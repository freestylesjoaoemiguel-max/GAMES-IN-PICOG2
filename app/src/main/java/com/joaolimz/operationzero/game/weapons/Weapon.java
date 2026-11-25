package com.joaolimz.operationzero.game.weapons;

import com.joaolimz.operationzero.game.math.MathUtil;
import com.joaolimz.operationzero.game.world.Prop;

/**
 * Physical firearm state machine: magazine / tube, chamber, slide or pump, cadence and recoil spring.
 * Firing itself (raycasts, effects) is executed by {@code CombatSystem}; this class only owns the
 * mechanical state so it can be unit tested.
 */
public final class Weapon extends Prop {
    public WeaponType type;
    public Magazine mag;
    public boolean chambered;
    public int tubeRounds;
    /** Slide locked back after the last round (pistol/SMG/rifle). */
    public boolean slideLocked;
    public float cooldown;
    /** Recoil spring state (meters back / degrees pitch), decays to zero. */
    public float recoilBack, recoilPitch;
    public float flashTimer;
    /** 0..1 animation of slide / pump travel. */
    public float actionAnim;
    public boolean triggerWasDown;
    public int shotsFired;

    public static final int FIRE_NONE = 0, FIRE_SHOT = 1, FIRE_DRY = 2;

    public Weapon setup(WeaponType t) {
        type = t;
        snapGrip = true;
        name = t.name;
        return this;
    }

    public int roundsInMagazine() {
        if (!type.usesMagazine()) return tubeRounds;
        return mag != null ? mag.rounds : 0;
    }

    public int totalLoaded() { return roundsInMagazine() + (chambered ? 1 : 0); }

    public void update(float dt) {
        if (cooldown > 0f) cooldown -= dt;
        float k = MathUtil.damp(18f, dt);
        recoilBack -= recoilBack * k;
        recoilPitch -= recoilPitch * k;
        if (flashTimer > 0f) flashTimer -= dt;
        if (actionAnim > 0f) actionAnim = Math.max(0f, actionAnim - dt * 6f);
    }

    /**
     * Applies trigger input. Returns FIRE_SHOT when a round was discharged (caller performs the
     * hitscan), FIRE_DRY for an empty click, FIRE_NONE otherwise.
     */
    public int pullTrigger(boolean down) {
        boolean pressed = down && !triggerWasDown;
        triggerWasDown = down;
        if (!down) return FIRE_NONE;
        if (!pressed && !type.automatic) return FIRE_NONE;
        if (cooldown > 0f) return FIRE_NONE;
        if (!chambered) {
            if (pressed) { cooldown = 0.2f; return FIRE_DRY; }
            return FIRE_NONE;
        }
        // discharge
        chambered = false;
        cooldown = type.fireInterval;
        shotsFired++;
        flashTimer = 0.05f;
        recoilBack = Math.min(recoilBack + type.recoilBack, type.recoilBack * 2.2f);
        recoilPitch = Math.min(recoilPitch + type.recoilPitchDeg, type.recoilPitchDeg * 2.2f);
        if (type.action == WeaponType.ACTION_SLIDE) {
            actionAnim = 1f;
            // self-loading: chamber the next round from the magazine or lock open
            if (mag != null && mag.rounds > 0) {
                mag.rounds--;
                chambered = true;
            } else {
                slideLocked = true;
            }
        }
        return FIRE_SHOT;
    }

    /** Detaches the magazine (the caller turns it into a free physical object). */
    public Magazine ejectMagazine() {
        if (mag == null) return null;
        Magazine m = mag;
        mag = null;
        m.attachedTo = null;
        return m;
    }

    public boolean canAccept(Magazine m) {
        if (m == null || m.attachedTo != null) return false;
        if (type.usesMagazine()) return mag == null && m.ammo == type.ammo && !m.isShell();
        return m.isShell() && m.ammo == type.ammo && tubeRounds < type.capacity;
    }

    /** Inserts a magazine (or loads a shell into the tube). Returns true on success. */
    public boolean insert(Magazine m) {
        if (!canAccept(m)) return false;
        if (type.usesMagazine()) {
            mag = m;
            m.attachedTo = this;
            if (slideLocked && m.rounds > 0) {
                // slide release
                slideLocked = false;
                m.rounds--;
                chambered = true;
                actionAnim = 1f;
            }
        } else {
            tubeRounds++;
            m.rounds = 0;
        }
        return true;
    }

    /**
     * Manually cycles the action (rack slide / pump). Ejects a chambered round if present.
     * Returns true when the action was performed.
     */
    public boolean cycleAction() {
        actionAnim = 1f;
        if (type.action == WeaponType.ACTION_PUMP) {
            chambered = false;
            if (tubeRounds > 0) { tubeRounds--; chambered = true; }
            return true;
        }
        chambered = false;
        if (mag != null && mag.rounds > 0) {
            mag.rounds--;
            chambered = true;
            slideLocked = false;
        } else {
            slideLocked = mag == null || mag.rounds == 0;
        }
        return true;
    }

    /** True when an action (rack/pump) is needed before the weapon can fire again. */
    public boolean needsAction() {
        if (chambered) return false;
        if (type.action == WeaponType.ACTION_PUMP) return tubeRounds > 0;
        return mag != null && mag.rounds > 0;
    }
}
