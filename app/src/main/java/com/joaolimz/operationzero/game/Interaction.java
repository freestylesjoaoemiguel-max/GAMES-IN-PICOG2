package com.joaolimz.operationzero.game;

import com.joaolimz.operationzero.game.ai.Npc;
import com.joaolimz.operationzero.game.ai.NpcSystem;
import com.joaolimz.operationzero.game.audio.Sfx;
import com.joaolimz.operationzero.game.input.InputFrame;
import com.joaolimz.operationzero.game.math.MathUtil;
import com.joaolimz.operationzero.game.math.Quat;
import com.joaolimz.operationzero.game.math.Vec3;
import com.joaolimz.operationzero.game.physics.Layers;
import com.joaolimz.operationzero.game.physics.RayHit;
import com.joaolimz.operationzero.game.physics.RigidBody;
import com.joaolimz.operationzero.game.physics.StaticCollider;
import com.joaolimz.operationzero.game.player.Hand;
import com.joaolimz.operationzero.game.player.Inventory;
import com.joaolimz.operationzero.game.player.Player;
import com.joaolimz.operationzero.game.mesh.Models;
import com.joaolimz.operationzero.game.save.Settings;
import com.joaolimz.operationzero.game.weapons.Magazine;
import com.joaolimz.operationzero.game.weapons.Weapon;
import com.joaolimz.operationzero.game.weapons.WeaponType;
import com.joaolimz.operationzero.game.world.Door;
import com.joaolimz.operationzero.game.world.Interactive;
import com.joaolimz.operationzero.game.world.ItemKind;
import com.joaolimz.operationzero.game.world.Level;
import com.joaolimz.operationzero.game.world.Prop;
import com.joaolimz.operationzero.game.world.PropFactory;

/**
 * Physical VR interaction: locomotion, hands, grabbing / throwing, distance grab, holsters, physical
 * reloading (eject, take magazine from pouch, insert, rack), shooting (hitscan), recoil, doors, buttons,
 * levers, terminals, keycards, breakables.
 */
public final class Interaction {
    private final Game g;
    private final RayHit hit = new RayHit();
    private final RayHit hit2 = new RayHit();
    private final Vec3 t0 = new Vec3(), t1 = new Vec3(), t2 = new Vec3(), t3 = new Vec3(), dir = new Vec3();
    private final Quat q0 = new Quat(), q1 = new Quat();
    private final MathUtil.Rng rng = new MathUtil.Rng(99);

    private boolean gripPrev;
    /** In trigger-grab mode a trigger press that grabbed a weapon must be released before firing. */
    private boolean suppressFire;
    private float snapCooldown;
    private boolean turnLatch;

    // teleport
    public boolean teleportAiming, teleportValid;
    public final Vec3 teleportTarget = new Vec3();
    public final Vec3[] arc = new Vec3[24];
    public int arcCount;

    // assisted reload
    private float assistTimer;
    private Weapon assistWeapon;
    private float stepAccum;
    public String prompt;

    public Interaction(Game g) {
        this.g = g;
        for (int i = 0; i < arc.length; i++) arc[i] = new Vec3();
    }

    private Level level() { return g.level; }

    // ===================================================================== main update
    public void update(float dt) {
        Player p = g.player;
        Settings s = g.settings;
        InputFrame in = g.in, pv = g.prev;
        prompt = null;

        turning(dt, in, pv, s);
        locomotion(dt, in, pv, s);

        // hands: the controller drives the active hand, the other one parks / supports
        Hand ah = p.activeHand(), oh = p.otherHand();
        ah.controlled = true;
        oh.controlled = false;
        p.driveHand(ah, in.hasControllerPose, in.controller, dt, s.sensitivity);
        p.parkHand(oh, ah, dt);
        ah.trackVelocity(dt);
        oh.trackVelocity(dt);

        pointing(ah);
        boolean gripDown = gripInput(ah, in);
        if (gripDown && !gripPrev) onGrabPressed(ah);
        if (!gripDown && gripPrev) onGrabReleased(ah);
        gripPrev = gripDown;
        if (!in.trigger) suppressFire = false;

        // trigger with an empty hand in grip-button mode uses the pointed object
        if (s.grabMode == 1 && in.trigger && !pv.trigger && ah.isEmpty()) useByPointing(ah);

        // hand pose for rendering
        if (ah.held != null || ah.door != null || ah.lever != null) ah.pose = Models.POSE_FIST;
        else if (ah.pointInteractive != null || ah.pointDoor != null) ah.pose = Models.POSE_POINT;
        else ah.pose = (s.grabMode == 0 ? in.trigger : in.grip) ? Models.POSE_FIST : Models.POSE_OPEN;
        oh.pose = (oh.held != null || oh.supporting) ? Models.POSE_FIST : Models.POSE_OPEN;

        updatePulling(ah, dt);
        updateHeldPoses(dt);
        updateDoorAndLever(ah, dt);
        weapons(ah, dt, in);
        reloadChecks(ah, oh);
        consumables(ah);
        keycardReaders(ah);
        pushProps();
        updateInteractives(dt);
        if (p.feet.y < -30f) p.damage(1000f, null);
    }

    // ===================================================================== locomotion
    private void turning(float dt, InputFrame in, InputFrame pv, Settings s) {
        float turn = in.turnX;
        if (in.left) turn = -1f;
        if (in.right) turn = 1f;
        // touchpad left/right are turn keys only when teleport isn't using them
        if (s.turnMode == 0) {
            snapCooldown -= dt;
            if (Math.abs(turn) > 0.6f) {
                if (!turnLatch && snapCooldown <= 0f) {
                    g.player.yaw -= Math.signum(turn) * s.snapAngle * MathUtil.DEG;
                    g.fade = Math.max(g.fade, 0.35f);
                    snapCooldown = 0.25f;
                    turnLatch = true;
                    g.player.turnSpeedNow = 0f;
                }
            } else {
                turnLatch = false;
            }
        } else {
            float rate = s.smoothTurnSpeed * MathUtil.DEG * turn;
            g.player.yaw -= rate * dt;
            g.player.turnSpeedNow = Math.abs(s.smoothTurnSpeed * turn);
        }
        if (s.turnMode == 0) g.player.turnSpeedNow = 0f;
    }

    private void locomotion(float dt, InputFrame in, InputFrame pv, Settings s) {
        Player p = g.player;
        float fwd = in.moveY, strafe = in.moveX;
        if (in.up) fwd = 1f;
        if (in.down) fwd = -1f;
        if (s.moveMode == 1) {
            // teleport: hold forward to aim, release to jump
            boolean aiming = fwd > 0.5f;
            if (aiming) {
                teleportAiming = true;
                computeArc(p.activeHand());
            } else if (teleportAiming) {
                teleportAiming = false;
                if (teleportValid) {
                    p.teleport(teleportTarget);
                    g.fade = 1f;
                    g.audio.play2D(Sfx.TELEPORT, 0.6f);
                }
            }
            // allow small back-steps with smooth movement
            if (fwd < -0.5f) p.move(level().physics, -1f, 0f, s.moveSpeed * 0.5f, dt);
            else p.move(level().physics, 0f, 0f, 0f, dt);
        } else {
            teleportAiming = false;
            p.move(level().physics, fwd, strafe, s.moveSpeed, dt);
        }
        // footsteps
        stepAccum += p.moveSpeedNow * dt;
        if (stepAccum > 0.75f && p.grounded) {
            stepAccum = 0f;
            g.audio.play(Sfx.STEP, t0.set(p.feet), 0.35f, 0.9f + rng.range(0f, 0.2f), 10f);
            if (p.moveSpeedNow > 2.6f) g.npcs.noise(p.feet, 5f, false);
        }
        // comfort vignette
        float strength = s.vignette == 0 ? 0f : s.vignette == 1 ? 0.55f : 0.9f;
        float motion = MathUtil.clamp(p.moveSpeedNow / 2.5f + p.turnSpeedNow / 120f, 0f, 1f);
        g.vignette += (strength * motion - g.vignette) * MathUtil.damp(6f, dt);
    }

    private void computeArc(Hand h) {
        h.rot.forward(dir);
        t0.set(h.pos);
        t1.set(dir).scale(7f);
        arcCount = 0;
        teleportValid = false;
        float step = 0.05f;
        for (int i = 0; i < arc.length; i++) {
            arc[i].set(t0);
            arcCount = i + 1;
            t2.set(t1).scale(step);
            float len = t2.length();
            if (len < 1e-4f) break;
            t3.set(t2).scale(1f / len);
            if (level().physics.raycast(t0, t3, len, Layers.STATIC | Layers.DOOR, hit, null)) {
                arc[i].set(hit.point);
                arcCount = i + 1;
                if (hit.normal.y > 0.7f) {
                    teleportTarget.set(hit.point);
                    // the destination must fit the player capsule and be close to the current floor level
                    com.joaolimz.operationzero.game.math.Aabb box = new com.joaolimz.operationzero.game.math.Aabb();
                    box.setCenter(hit.point.x, hit.point.y + 1.0f, hit.point.z, 0.25f, 0.8f, 0.25f);
                    teleportValid = !level().physics.overlapsSolid(box, Layers.STATIC) && Math.abs(hit.point.y - g.player.feet.y) < 2.5f
                            && hit.point.distance(g.player.feet) < 9f;
                }
                return;
            }
            t0.add(t2);
            t1.y -= 9.8f * step;
        }
    }

    // ===================================================================== hands
    private boolean gripInput(Hand h, InputFrame in) {
        if (g.settings.grabMode == 1) return in.grip;
        // trigger-grab mode: weapons are "sticky" (trigger fires them)
        if (h.held instanceof Weapon) return true;
        if (h.pulling != null) return true;
        return in.trigger;
    }

    public void switchHands() {
        Player p = g.player;
        Hand cur = p.activeHand();
        // release transient grabs on the old hand
        if (cur.door != null) { cur.door.grabbedBy = null; cur.door = null; }
        if (cur.lever != null) { cur.lever.grabbedBy = null; cur.lever = null; }
        if (cur.pulling != null) { cur.pulling.body.kinematic = false; cur.pulling.heldBy = null; cur.pulling = null; }
        p.active = 1 - p.active;
        Hand nh = p.activeHand();
        nh.aim.set(cur.aim);
        nh.aimInit = cur.aimInit;
        gripPrev = nh.held != null;
        suppressFire = true;
        g.audio.play2D(Sfx.GRAB, 0.3f);
    }

    private void pointing(Hand h) {
        h.pointProp = null;
        h.pointInteractive = null;
        h.pointDoor = null;
        if (!h.isEmpty()) return;
        h.rot.forward(dir);
        Level L = level();
        // interactives
        float bestI = 3.5f;
        for (int i = 0; i < L.interactives.size(); i++) {
            Interactive it = L.interactives.get(i);
            if (!g.sectors.isActive(it.sector)) continue;
            t0.setSub(it.pos, h.pos);
            if (it.type == Interactive.TERMINAL) t0.y += 0.95f;
            float along = t0.dot(dir);
            if (along < 0.05f || along > bestI) continue;
            t1.set(h.pos).addScaled(dir, along);
            t2.set(it.pos);
            if (it.type == Interactive.TERMINAL) t2.y += 0.95f;
            if (t1.distance(t2) < it.radius() * 1.4f + along * 0.04f) { bestI = along; h.pointInteractive = it; }
        }
        // props (distance grab), cone of ~9 degrees
        float bestCos = 0.988f;
        for (int i = 0; i < L.props.size(); i++) {
            Prop pr = L.props.get(i);
            if (!pr.active || pr.isHeld() || pr.isStored() || pr.kind == ItemKind.DEBRIS) continue;
            if (pr instanceof Magazine && ((Magazine) pr).attachedTo != null) continue;
            if (!g.sectors.isActive(pr.body.sector)) continue;
            t0.setSub(pr.body.pos, h.pos);
            float d = t0.length();
            if (d > 4.5f || d < 0.05f) continue;
            float c = t0.dot(dir) / d;
            if (c > bestCos) {
                if (!level().physics.lineOfSight(h.pos, pr.body.pos, hit)) continue;
                bestCos = c;
                h.pointProp = pr;
            }
        }
        if (h.pointProp != null) {
            h.pointProp.highlight = 0.1f;
            if (h.pointInteractive != null && h.pointProp.body.pos.distance(h.pos) > bestI) h.pointProp = null;
            else h.pointInteractive = null;
        }
        if (h.pointProp == null && h.pointInteractive == null) {
            if (L.physics.raycast(h.pos, dir, 3f, Layers.DOOR | Layers.STATIC, hit, null) && hit.hinge != null) {
                h.pointDoor = (Door) hit.hinge.owner;
            }
        }
        if (h.pointProp != null) prompt = "GATILHO: pegar " + h.pointProp.name;
        else if (h.pointInteractive != null) prompt = "GATILHO: usar " + (h.pointInteractive.label != null ? h.pointInteractive.label : "");
    }

    private void onGrabPressed(Hand h) {
        Player p = g.player;
        Hand other = p.otherHand();
        if (!h.isEmpty()) return;
        // 1. inventory slots
        for (int sIdx = 0; sIdx <= Inventory.POUCH; sIdx++) {
            Inventory.zone(sIdx, p.eye, p.torso, g.settings.rightHanded, h.side(), t0);
            if (h.pos.distance(t0) > Inventory.radius(sIdx)) continue;
            if (sIdx == Inventory.POUCH) {
                if (takeFromPouch(h, other)) return;
                continue;
            }
            Prop item = p.inventory.items[sIdx];
            if (item != null) {
                p.inventory.items[sIdx] = null;
                item.slot = -1;
                attach(h, item);
                g.audio.play2D(Sfx.HOLSTER, 0.6f);
                g.platform.vibrate(15, 0.3f);
                return;
            }
        }
        // 2. rack / pump the weapon held by the other hand
        if (other.held instanceof Weapon) {
            Weapon w = (Weapon) other.held;
            w.body.rot.rotate(0f, w.type.action == WeaponType.ACTION_PUMP ? w.type.foreY : 0.035f,
                    w.type.action == WeaponType.ACTION_PUMP ? w.type.foreZ : -0.06f, t0).add(w.body.pos);
            boolean wantsRack = w.needsAction() || (w.slideLocked && w.mag != null && w.mag.rounds > 0);
            if (wantsRack && h.pointProp == null && h.pos.distance(t0) < 0.12f) {
                cycle(w);
                return;
            }
            // pull the inserted magazine out of the other hand's weapon
            if (w.mag != null) {
                Magazine m = w.mag;
                if (h.pos.distance(m.body.pos) < 0.1f) {
                    w.ejectMagazine();
                    m.body.enabled = true;
                    attach(h, m);
                    g.audio.play(Sfx.MAG_OUT, m.body.pos, 0.8f, 1f);
                    return;
                }
            }
        }
        Level L = level();
        // 3. touch interactives / levers
        for (int i = 0; i < L.interactives.size(); i++) {
            Interactive it = L.interactives.get(i);
            if (!g.sectors.isActive(it.sector)) continue;
            t0.set(it.pos);
            if (it.type == Interactive.TERMINAL) t0.y += 0.85f;
            if (h.pos.distance(t0) < it.radius() + 0.08f) {
                if (it.type == Interactive.LEVER && it.enabled) { h.lever = it; it.grabbedBy = h; g.audio.play2D(Sfx.GRAB, 0.3f); }
                else activate(it, h);
                return;
            }
        }
        // 4. door handles
        for (int i = 0; i < L.doors.size(); i++) {
            Door d = L.doors.get(i);
            if (d.type != Door.HINGED || !g.sectors.isActive(d.sector)) continue;
            for (int side = -1; side <= 1; side += 2) {
                d.handlePos(t0, side);
                if (h.pos.distance(t0) < 0.25f) {
                    if (d.locked) { lockedFeedback(d); return; }
                    h.door = d;
                    d.grabbedBy = h;
                    g.audio.play2D(Sfx.GRAB, 0.3f);
                    return;
                }
            }
        }
        // 5. nearest prop within reach
        Prop best = null;
        float bd = 0.22f;
        for (int i = 0; i < L.props.size(); i++) {
            Prop pr = L.props.get(i);
            if (!pr.active || pr.isStored() || pr.kind == ItemKind.DEBRIS) continue;
            if (pr instanceof Magazine && ((Magazine) pr).attachedTo != null) continue;
            if (pr.heldBy != null) continue; // never steal from the other hand by accident
            float d = pr.body.pos.distance(h.pos) - Math.min(0.15f, pr.body.radiusXZ());
            if (d < bd) { bd = d; best = pr; }
        }
        if (best != null) {
            attach(h, best);
            return;
        }
        // 6. distance grab, interactives and doors by pointing
        useByPointing(h);
    }

    private void useByPointing(Hand h) {
        if (h.pointProp != null) {
            Prop pr = h.pointProp;
            if (pr.heldBy != null) return;
            pr.body.kinematic = true;
            pr.heldBy = h;
            h.pulling = pr;
            h.pullTime = 0f;
            g.audio.play2D(Sfx.GRAB, 0.4f);
            return;
        }
        if (h.pointInteractive != null) {
            Interactive it = h.pointInteractive;
            if (it.type == Interactive.LEVER) toggleLever(it);
            else activate(it, h);
            return;
        }
        if (h.pointDoor != null) {
            Door d = h.pointDoor;
            if (d.locked) lockedFeedback(d);
            else {
                // push the door away from the player
                t0.set(g.player.feet).sub(d.hinge.hingeX, 0, d.hinge.hingeZ);
                float nx = -d.hinge.dirZ(), nz = d.hinge.dirX();
                float side = t0.x * nx + t0.z * nz >= 0 ? 1f : -1f;
                d.angVel += side * 2.2f;
                g.audio.play(Sfx.DOOR_OPEN, t0.set(d.hinge.centerX(), d.hinge.y0 + 1f, d.hinge.centerZ()), 0.6f, 1f);
            }
        }
    }

    private void lockedFeedback(Door d) {
        g.showHint(d.lockedHint);
        g.audio.play2D(Sfx.CARD_DENIED, 0.5f);
    }

    private boolean takeFromPouch(Hand h, Hand other) {
        Player p = g.player;
        int ammo = -1;
        if (other.held instanceof Weapon) ammo = ((Weapon) other.held).type.ammo;
        else if (h.held instanceof Weapon) ammo = ((Weapon) h.held).type.ammo;
        else {
            for (int s = 0; s < 2 && ammo < 0; s++) if (p.inventory.items[s] instanceof Weapon) ammo = ((Weapon) p.inventory.items[s]).type.ammo;
        }
        if (ammo < 0) { g.showHint("Nenhuma arma para buscar munição"); return true; }
        int reserve = p.inventory.reserve[ammo];
        if (reserve <= 0) {
            g.showHint("Sem munição " + WeaponType.AMMO_NAMES[ammo]);
            g.audio.play2D(Sfx.DRY_FIRE, 0.5f);
            return true;
        }
        int cap = ammo == WeaponType.AMMO_SHELL ? 1 : PropFactory.capacityFor(ammo);
        int rounds = Math.min(cap, reserve);
        p.inventory.reserve[ammo] -= rounds;
        Magazine m = g.factory.magazine(ammo, rounds, g.sectors.current, h.pos.x, h.pos.y, h.pos.z);
        attach(h, m);
        g.audio.play2D(Sfx.HOLSTER, 0.5f);
        return true;
    }

    private void attach(Hand h, Prop pr) {
        h.held = pr;
        pr.heldBy = h;
        pr.slot = -1;
        pr.body.kinematic = true;
        pr.body.enabled = true;
        pr.body.wake();
        if (!pr.snapGrip) {
            // keep the current relative offset (feels physical), but limit it
            q0.set(h.rot).conjugate();
            t0.setSub(pr.body.pos, h.pos);
            if (t0.length() > 0.15f) t0.normalize().scale(0.1f);
            q0.rotate(t0, pr.holdPos);
            pr.holdRot.setMul(q0, pr.body.rot);
        }
        if (pr instanceof Weapon) {
            Weapon w = (Weapon) pr;
            w.triggerWasDown = true;
            suppressFire = true;
            if (!g.flags.contains("armed")) {
                g.flags.add("armed");
                g.showHint("GATILHO atira. TOUCHPAD ejeta/carrega. APP troca de mão.");
            }
        }
        if (pr.kind == ItemKind.AMMO_BOX) {
            g.player.inventory.reserve[pr.value] += pr.value2;
            g.showHint("+" + pr.value2 + " munição " + WeaponType.AMMO_NAMES[pr.value]);
            g.audio.play2D(Sfx.PICKUP, 0.7f);
            h.held = null;
            g.factory.release(pr);
            return;
        }
        g.audio.play2D(Sfx.GRAB, 0.35f);
        g.platform.vibrate(12, 0.25f);
        if (!pr.pickedOnce) {
            pr.pickedOnce = true;
            if (pr.pickupEvent != null) g.onEvent(pr.pickupEvent);
        }
    }

    private void onGrabReleased(Hand h) {
        if (h.door != null) { h.door.grabbedBy = null; h.door = null; return; }
        if (h.lever != null) { h.lever.grabbedBy = null; h.lever = null; return; }
        if (h.pulling != null) return; // finishes into the hand
        if (h.held != null && !(h.held instanceof Weapon && g.settings.grabMode == 0)) release(h, true);
    }

    /** Releases the held object: holsters it when close to a slot, otherwise throws it. */
    public void release(Hand h, boolean allowStore) {
        Prop pr = h.held;
        if (pr == null) return;
        Player p = g.player;
        h.held = null;
        pr.heldBy = null;
        // fast-moving hands are throwing, never storing
        if (allowStore && h.velocity.length() < 1.5f) {
            for (int sIdx = 0; sIdx <= Inventory.POUCH; sIdx++) {
                Inventory.zone(sIdx, p.eye, p.torso, g.settings.rightHanded, h.side(), t0);
                if (h.pos.distance(t0) > Inventory.radius(sIdx)) continue;
                if (!p.inventory.accepts(sIdx, pr)) continue;
                if (sIdx == Inventory.POUCH) {
                    if (pr instanceof Magazine) {
                        Magazine m = (Magazine) pr;
                        p.inventory.reserve[m.ammo] += m.rounds;
                        g.factory.release(m);
                        g.audio.play2D(Sfx.HOLSTER, 0.5f);
                        return;
                    }
                    continue;
                }
                g.store(pr, sIdx);
                g.audio.play2D(Sfx.HOLSTER, 0.6f);
                g.platform.vibrate(15, 0.3f);
                return;
            }
        }
        // throw
        RigidBody b = pr.body;
        b.kinematic = false;
        b.enabled = true;
        b.wake();
        b.sector = g.sectors.current;
        h.throwVelocity(t0);
        t0.scale(1.25f).clampLength(14f);
        b.vel.set(t0);
        b.angVel.set(rng.range(-4f, 4f), rng.range(-4f, 4f), rng.range(-4f, 4f));
        b.protectTimer = 2f;
        if (pr instanceof Magazine && ((Magazine) pr).rounds == 0) pr.despawnTimer = 20f;
    }

    /** Long press of the action button: drop whatever the active hand holds. */
    public void dropHeld(Hand h) {
        if (h.held != null) {
            release(h, false);
            g.showHint("Objeto solto");
        }
    }

    public void releaseAll() {
        for (int i = 0; i < 2; i++) {
            Hand h = g.player.hands[i];
            if (h.door != null) { h.door.grabbedBy = null; h.door = null; }
            if (h.lever != null) { h.lever.grabbedBy = null; h.lever = null; }
            if (h.held != null) release(h, false);
            h.pulling = null;
        }
    }

    private void updatePulling(Hand h, float dt) {
        Prop pr = h.pulling;
        if (pr == null) return;
        h.pullTime += dt;
        float k = MathUtil.damp(12f, dt);
        pr.body.pos.lerp(h.pos, k);
        pr.body.vel.set(0, 0, 0);
        if (pr.body.pos.distance(h.pos) < 0.12f || h.pullTime > 0.6f) {
            h.pulling = null;
            pr.heldBy = null;
            pr.body.pos.set(h.pos);
            pr.holdPos.set(0f, 0f, -0.05f);
            pr.holdRot.identity();
            attach(h, pr);
        }
    }

    /** Positions held, stored and attached objects. */
    private void updateHeldPoses(float dt) {
        Player p = g.player;
        for (int i = 0; i < 2; i++) {
            Hand h = p.hands[i];
            Prop pr = h.held;
            if (pr == null) continue;
            RigidBody b = pr.body;
            if (pr instanceof Weapon) {
                Weapon w = (Weapon) pr;
                q0.setAxisAngle(1, 0, 0, w.recoilPitch * MathUtil.DEG);
                b.rot.setMul(h.rot, q0);
                h.rot.rotate(0f, 0f, w.recoilBack, t0);
                b.pos.set(h.pos).add(t0);
            } else if (pr instanceof Magazine) {
                Magazine m = (Magazine) pr;
                float len = m.isShell() ? 0.03f : PropFactory.magLength(m.ammo);
                b.rot.set(h.rot);
                h.rot.rotate(0f, len - 0.02f, -0.02f, t0);
                b.pos.set(h.pos).add(t0);
            } else {
                b.rot.setMul(h.rot, pr.holdRot);
                h.rot.rotate(pr.holdPos, t0);
                b.pos.set(h.pos).add(t0);
            }
            b.vel.set(h.velocity);
            b.sector = g.sectors.current;
        }
        // stored items follow the body
        for (int sIdx = 0; sIdx < Inventory.SLOTS; sIdx++) {
            Prop pr = p.inventory.items[sIdx];
            if (pr == null) continue;
            Inventory.rest(sIdx, p.eye, p.torso, g.settings.rightHanded, pr.body.pos);
            if (sIdx == Inventory.BACK) q0.setEuler(p.torsoYaw + MathUtil.PI * 0.5f, 0.9f, 0f);
            else if (sIdx == Inventory.HIP) q0.setEuler(p.torsoYaw, -1.45f, 0f);
            else q0.setEuler(p.torsoYaw, -0.3f, 0f);
            pr.body.rot.set(q0);
            pr.body.vel.set(0, 0, 0);
            pr.body.kinematic = true;
            pr.body.sector = g.sectors.current;
        }
        // magazines inserted in weapons
        for (int i = 0; i < level().props.size(); i++) {
            Prop pr = level().props.get(i);
            if (!(pr instanceof Weapon) || !pr.active) continue;
            Weapon w = (Weapon) pr;
            if (w.mag == null) continue;
            float len = PropFactory.magLength(w.mag.ammo);
            w.body.rot.rotate(0f, w.type.magY + len - 0.005f, w.type.magZ, t0);
            w.mag.body.pos.set(w.body.pos).add(t0);
            w.mag.body.rot.set(w.body.rot);
            w.mag.body.enabled = false;
            w.mag.active = true;
        }
    }

    // ===================================================================== doors / levers / interactives
    private void updateDoorAndLever(Hand h, float dt) {
        if (h.lever != null) {
            Interactive it = h.lever;
            // lever angle follows the hand height relative to the pivot
            float dy = h.pos.y - it.pos.y;
            it.leverAngle = MathUtil.clamp(dy / 0.25f, -0.7f, 0.7f);
            if (it.leverAngle > 0.5f && !it.on) {
                it.on = true;
                g.audio.play(Sfx.LEVER, it.pos, 1f, 1f);
                g.platform.vibrate(40, 0.6f);
                if (it.oneShot) { it.enabled = false; it.grabbedBy = null; h.lever = null; it.leverAngle = 0.7f; }
                g.onEvent(it.event);
            }
            if (h.lever != null && h.pos.distance(it.pos) > 0.6f) { it.grabbedBy = null; h.lever = null; }
        }
        if (h.door != null && h.pos.distance(h.door.handlePos(t0, 1f)) > 0.9f) {
            h.door.grabbedBy = null;
            h.door = null;
        }
    }

    private void toggleLever(Interactive it) {
        if (!it.enabled) { g.audio.play2D(Sfx.CARD_DENIED, 0.4f); return; }
        if (it.on) return;
        it.on = true;
        it.leverAngle = 0.7f;
        g.audio.play(Sfx.LEVER, it.pos, 1f, 1f);
        if (it.oneShot) it.enabled = false;
        g.onEvent(it.event);
    }

    /** Activates a button / terminal / reader (touch or pointing). */
    public void activate(Interactive it, Hand h) {
        if (it.cooldown > 0f) return;
        it.cooldown = 0.6f;
        switch (it.type) {
            case Interactive.BUTTON:
                it.pressAnim = 1f;
                if (!it.enabled) {
                    g.audio.play(Sfx.CARD_DENIED, it.pos, 0.7f, 1f);
                    g.showHint("Sem energia");
                    return;
                }
                g.audio.play(Sfx.BUTTON, it.pos, 1f, 1f);
                if (it.oneShot) it.enabled = false;
                it.on = true;
                g.onEvent(it.event);
                break;
            case Interactive.LEVER:
                toggleLever(it);
                break;
            case Interactive.TERMINAL:
                g.audio.play(Sfx.BEEP, it.pos, 0.8f, 1f);
                if (!it.enabled) return;
                it.on = true;
                if (it.oneShot) {
                    it.enabled = false;
                    it.screen = new String[]{"ACESSO CONCEDIDO", "Porta do corredor: LIVRE", "", "Bom trabalho, agente."};
                }
                g.onEvent(it.event);
                break;
            case Interactive.READER:
                if (!it.enabled) return;
                if (playerHasItem(it.requiredItem)) {
                    it.okFlash = 1f;
                    it.enabled = false;
                    g.audio.play(Sfx.CARD_OK, it.pos, 1f, 1f);
                    g.onEvent(it.event);
                } else {
                    it.deniedFlash = 1f;
                    g.audio.play(Sfx.CARD_DENIED, it.pos, 1f, 1f);
                    g.showHint("Cartão de acesso necessário");
                }
                break;
            default:
                break;
        }
    }

    private boolean playerHasItem(int kind) {
        Player p = g.player;
        if (p.inventory.hasItemKind(kind)) return true;
        for (int i = 0; i < 2; i++) if (p.hands[i].held != null && p.hands[i].held.kind == kind) return true;
        return false;
    }

    private void updateInteractives(float dt) {
        Level L = level();
        for (int i = 0; i < L.interactives.size(); i++) {
            Interactive it = L.interactives.get(i);
            if (it.cooldown > 0f) it.cooldown -= dt;
            if (it.pressAnim > 0f) it.pressAnim -= dt * 4f;
            if (it.okFlash > 0f) it.okFlash -= dt;
            if (it.deniedFlash > 0f) it.deniedFlash -= dt * 2f;
            if (it.type == Interactive.LEVER && it.grabbedBy == null && !it.on) it.leverAngle = MathUtil.approach(it.leverAngle, -0.7f, dt * 2f);
        }
        // training targets reset
        for (int i = 0; i < L.targets.size(); i++) {
            Level.Target t = L.targets.get(i);
            if (t.down > 0f) {
                t.resetTimer -= dt;
                if (t.resetTimer <= 0f) t.down = Math.max(0f, t.down - dt * 2f);
            }
        }
    }

    private void keycardReaders(Hand h) {
        Prop pr = h.held;
        if (pr == null || pr.kind != ItemKind.KEYCARD) return;
        Level L = level();
        for (int i = 0; i < L.interactives.size(); i++) {
            Interactive it = L.interactives.get(i);
            if (it.type != Interactive.READER || !it.enabled) continue;
            if (pr.body.pos.distance(it.pos) < 0.2f) activate(it, h);
        }
    }

    private void consumables(Hand h) {
        Prop pr = h.held;
        if (pr == null) return;
        if (pr.kind == ItemKind.MEDKIT && pr.body.pos.distance(g.player.eye) < 0.35f) useMedkit(h);
    }

    private void useMedkit(Hand h) {
        Prop pr = h.held;
        if (g.player.health >= Player.MAX_HEALTH) { g.showHint("Vida cheia"); return; }
        g.player.heal(pr.value > 0 ? pr.value : 50);
        g.audio.play2D(Sfx.HEAL, 0.8f);
        g.showHint("Vida restaurada");
        h.held = null;
        g.factory.release(pr);
    }

    // ===================================================================== weapons
    /** Short press of the action button (touchpad click). */
    public void contextAction() {
        Player p = g.player;
        Hand h = p.activeHand();
        Prop pr = h.held;
        if (pr instanceof Weapon) {
            Weapon w = (Weapon) pr;
            // holster when the hand is over a compatible slot
            for (int sIdx = 0; sIdx < 2; sIdx++) {
                Inventory.zone(sIdx, p.eye, p.torso, g.settings.rightHanded, h.side(), t0);
                if (h.pos.distance(t0) < Inventory.radius(sIdx) && p.inventory.accepts(sIdx, w)) {
                    h.held = null;
                    w.heldBy = null;
                    g.store(w, sIdx);
                    g.audio.play2D(Sfx.HOLSTER, 0.6f);
                    return;
                }
            }
            if (g.settings.reloadAssist && assistWeapon == null && w.totalLoaded() == 0) {
                startAssistedReload(w);
                return;
            }
            if (w.type.usesMagazine() && w.mag != null) {
                Magazine m = w.ejectMagazine();
                m.body.enabled = true;
                m.body.kinematic = false;
                m.body.wake();
                m.body.sector = g.sectors.current;
                m.body.vel.set(h.velocity).add(0, -1.2f, 0);
                m.body.protectTimer = 1f;
                if (m.rounds == 0) m.despawnTimer = 20f;
                g.audio.play(Sfx.MAG_OUT, m.body.pos, 0.9f, 1f);
                g.platform.vibrate(20, 0.3f);
                return;
            }
            cycle(w);
            return;
        }
        if (pr != null && pr.kind == ItemKind.MEDKIT) { useMedkit(h); return; }
        if (pr == null && h.pointInteractive != null) activate(h.pointInteractive, h);
    }

    private void cycle(Weapon w) {
        boolean wasChambered = w.chambered;
        w.cycleAction();
        g.audio.play(w.type.action == WeaponType.ACTION_PUMP ? Sfx.PUMP : Sfx.SLIDE, w.body.pos, 0.9f, 1f);
        g.platform.vibrate(25, 0.4f);
        if (wasChambered) {
            // ejected live round lands in the world as a small physical object
            if (w.type.ammo == WeaponType.AMMO_SHELL) {
                Magazine s = g.factory.magazine(WeaponType.AMMO_SHELL, 1, g.sectors.current, w.body.pos.x, w.body.pos.y + 0.05f, w.body.pos.z);
                s.body.sleeping = false;
                s.body.vel.set(rng.range(-1f, 1f), 1.5f, rng.range(-1f, 1f));
                s.despawnTimer = 25f;
            }
        }
    }

    private void startAssistedReload(Weapon w) {
        int ammo = w.type.ammo;
        if (g.player.inventory.reserve[ammo] <= 0) { g.showHint("Sem munição de reserva"); return; }
        assistWeapon = w;
        assistTimer = w.type.usesMagazine() ? 1.1f : 0.5f;
        if (w.type.usesMagazine() && w.mag != null) contextActionEject(w);
    }

    private void contextActionEject(Weapon w) {
        Magazine m = w.ejectMagazine();
        if (m == null) return;
        m.body.enabled = true;
        m.body.kinematic = false;
        m.body.wake();
        m.body.sector = g.sectors.current;
        m.despawnTimer = 20f;
        g.audio.play(Sfx.MAG_OUT, m.body.pos, 0.9f, 1f);
    }

    private void weapons(Hand h, float dt, InputFrame in) {
        // update all weapons' mechanical timers
        for (int i = 0; i < 2; i++) {
            Prop pr = g.player.hands[i].held;
            if (pr instanceof Weapon) ((Weapon) pr).update(dt);
        }
        if (assistWeapon != null) {
            assistTimer -= dt;
            if (assistTimer <= 0f) finishAssistedReload();
        }
        if (!(h.held instanceof Weapon)) return;
        Weapon w = (Weapon) h.held;
        boolean triggerDown = in.trigger && !suppressFire;
        int r = w.pullTrigger(triggerDown);
        if (r == Weapon.FIRE_SHOT) fire(h, w);
        else if (r == Weapon.FIRE_DRY) {
            g.audio.play(Sfx.DRY_FIRE, w.body.pos, 0.8f, 1f);
            if (w.needsAction()) g.showHint("Toque no touchpad para engatilhar");
            else if (w.type.usesMagazine() && w.mag == null) g.showHint("Sem carregador: pegue um na bolsa (quadril)");
            else if (w.type.usesMagazine()) g.showHint("Carregador vazio: touchpad ejeta");
            else g.showHint("Sem cartuchos: carregue pela bolsa de munição");
        }
        if (w.flashTimer > 0f) prompt = null;
    }

    private void finishAssistedReload() {
        Weapon w = assistWeapon;
        assistWeapon = null;
        int ammo = w.type.ammo;
        int[] reserve = g.player.inventory.reserve;
        if (reserve[ammo] <= 0) return;
        if (w.type.usesMagazine()) {
            int rounds = Math.min(w.type.capacity, reserve[ammo]);
            reserve[ammo] -= rounds;
            Magazine m = g.factory.magazine(ammo, rounds, g.sectors.current, w.body.pos.x, w.body.pos.y, w.body.pos.z);
            w.slideLocked = true;
            w.insert(m);
            m.body.enabled = false;
            if (!w.chambered) w.cycleAction();
        } else {
            while (w.tubeRounds < w.type.capacity && reserve[ammo] > 0) { w.tubeRounds++; reserve[ammo]--; }
            if (!w.chambered) w.cycleAction();
        }
        g.audio.play(Sfx.MAG_IN, w.body.pos, 0.9f, 1f);
    }

    /** Hitscan firing with pellets, impacts, NPC damage, prop impulses, effects, recoil and noise. */
    private void fire(Hand h, Weapon w) {
        WeaponType t = w.type;
        RigidBody b = w.body;
        b.rot.rotate(0f, t.muzzleY, t.muzzleZ, t0).add(b.pos); // muzzle
        Vec3 muzzle = t1.set(t0);
        // aim along the hand (not the recoiled weapon) so recoil never steals the shot
        h.rot.forward(dir);
        for (int pellet = 0; pellet < t.pellets; pellet++) {
            t2.set(dir);
            float spread = t.spreadDeg * MathUtil.DEG;
            if (spread > 0f) {
                h.rot.right(t3).scale(rng.range(-spread, spread));
                t2.add(t3);
                h.rot.up(t3).scale(rng.range(-spread, spread));
                t2.add(t3).normalize();
            }
            shootRay(muzzle, t2, t, w, pellet == 0);
        }
        // feedback
        g.fx.flash(muzzle, b.rot, t == WeaponType.BRUTUS ? 1.5f : 1f);
        g.audio.play(t.sound, muzzle, 1f, 0.95f + rng.range(0f, 0.1f), 60f);
        g.platform.vibrate(t.hapticMs, t == WeaponType.BRUTUS ? 1f : 0.6f);
        h.punch += t.recoilPitchDeg * MathUtil.DEG * 0.35f;
        g.player.cameraPunch += t.recoilPitchDeg * MathUtil.DEG * 0.08f;
        g.npcs.noise(muzzle, t.noiseRadius, true);
    }

    private void shootRay(Vec3 o, Vec3 d, WeaponType t, Weapon w, boolean tracer) {
        Level L = level();
        L.physics.raycast(o, d, t.range, Layers.SHOOTABLE, hit, w.body);
        // skip props held by the player
        if (hit.body != null && hit.body.owner instanceof Prop && (((Prop) hit.body.owner).isHeld() || ((Prop) hit.body.owner).isStored())) {
            hit.clear();
            L.physics.raycast(o, d, t.range, Layers.STATIC | Layers.DOOR, hit, null);
        }
        if (!hit.hasHit()) hit.distance = t.range;
        g.npcs.raycast(o, d, t.range, hit);
        float dist = Math.min(hit.distance, t.range);
        Vec3 end = t3.set(o).addScaled(d, dist);
        // training targets
        for (int i = 0; i < L.targets.size(); i++) {
            Level.Target tg = L.targets.get(i);
            if (tg.down > 0.5f) continue;
            float tt = MathUtil.raySphere(o, d, tg.pos.x, tg.pos.y + 0.25f, tg.pos.z, 0.3f, dist);
            if (tt >= 0f) {
                tg.down = 1f;
                tg.resetTimer = 3f;
                tg.hits++;
                g.audio.play(Sfx.IMPACT_METAL, tg.pos, 0.8f, 1.2f);
                end.set(o).addScaled(d, tt);
                g.fx.sparks(end, t0.set(d).scale(-1f), 4, 1f, 0.9f, 0.6f);
                if (tracer && t != WeaponType.VX9) g.fx.tracer(o, end, 1f, 0.85f, 0.5f);
                return;
            }
        }
        if (tracer && t != WeaponType.VX9) g.fx.tracer(o, end, 1f, 0.85f, 0.5f);
        if (!hit.hasHit() || hit.distance >= t.range) {
            // maybe a ragdoll
            g.npcs.shootRagdolls(o, d, t.range, t0);
            return;
        }
        if (hit.owner instanceof Npc) {
            Npc n = (Npc) hit.owner;
            boolean killed = g.npcs.damage(n, t.damage, hit.part, end, d);
            // non-gory feedback: armor sparks
            g.fx.sparks(end, t0.set(d).scale(-1f), 5, 1f, 0.55f, 0.15f);
            g.audio.play(hit.part == NpcSystem.PART_HEAD ? Sfx.IMPACT_METAL : Sfx.IMPACT_SOFT, end, 0.8f, 1f);
            if (killed) g.platform.vibrate(30, 0.5f);
            return;
        }
        if (hit.body != null) {
            RigidBody rb = hit.body;
            rb.wake();
            rb.vel.addScaled(d, t.impulse / Math.max(0.3f, rb.mass) * 2f);
            rb.angVel.add(rng.range(-6f, 6f), rng.range(-6f, 6f), rng.range(-6f, 6f));
            rb.protectTimer = 1f;
            if (rb.owner instanceof Prop) {
                Prop pr = (Prop) rb.owner;
                if (pr.health > 0f) damageProp(pr, t.damage, d);
                else g.audio.play(pr.kind == ItemKind.BARREL ? Sfx.IMPACT_METAL : Sfx.IMPACT_WOOD, end, 0.8f, 1f);
            }
            g.fx.sparks(end, hit.normal, 3, 0.9f, 0.8f, 0.6f);
            return;
        }
        if (hit.hinge != null) {
            float nx = -hit.hinge.dirZ(), nz = hit.hinge.dirX();
            hit.hinge.torqueAccum += -(d.x * nx + d.z * nz) * 0.2f;
        }
        int mat = hit.collider != null ? hit.collider.material : StaticCollider.MAT_METAL;
        g.fx.sparks(end, hit.normal, 4, 1f, 0.85f, 0.5f);
        g.fx.puff(end, mat == StaticCollider.MAT_WOOD ? 0.55f : 0.6f, mat == StaticCollider.MAT_WOOD ? 0.45f : 0.6f, 0.4f, 0.12f);
        g.audio.play(Sfx.BULLET_WALL, end, 0.6f, 0.9f + rng.range(0f, 0.3f), 20f);
        g.npcs.noise(end, 6f, false);
    }

    /** Damages a breakable prop; breaks it into pooled debris. */
    public void damageProp(Prop pr, float dmg, Vec3 d) {
        pr.health -= dmg;
        if (pr.health > 0f) {
            g.audio.play(Sfx.IMPACT_WOOD, pr.body.pos, 0.8f, 1f);
            return;
        }
        boolean glass = pr.kind == ItemKind.BOTTLE;
        g.audio.play(glass ? Sfx.BREAK_GLASS : Sfx.BREAK_WOOD, pr.body.pos, 1f, 1f);
        int pieces = glass ? 4 : 5;
        for (int i = 0; i < pieces; i++) {
            Prop deb = g.factory.debris(pr.body.sector, pr.body.pos.x + rng.range(-0.1f, 0.1f), pr.body.pos.y + rng.range(0f, 0.15f),
                    pr.body.pos.z + rng.range(-0.1f, 0.1f), glass);
            deb.body.vel.set(rng.range(-2f, 2f), rng.range(1f, 3f), rng.range(-2f, 2f));
            if (d != null) deb.body.vel.addScaled(d, 2f);
            deb.body.angVel.set(rng.range(-8f, 8f), rng.range(-8f, 8f), rng.range(-8f, 8f));
        }
        if (pr.heldBy != null) ((Hand) pr.heldBy).held = null;
        g.factory.release(pr);
        g.npcs.noise(pr.body.pos, 10f, false);
    }

    private void reloadChecks(Hand ah, Hand oh) {
        // magazine / shell in one hand near the weapon of the other hand
        for (int k = 0; k < 2; k++) {
            Hand mh = k == 0 ? ah : oh, wh = k == 0 ? oh : ah;
            if (!(mh.held instanceof Magazine) || !(wh.held instanceof Weapon)) continue;
            Magazine m = (Magazine) mh.held;
            Weapon w = (Weapon) wh.held;
            if (!w.canAccept(m)) continue;
            w.body.rot.rotate(0f, w.type.magY, w.type.magZ, t0).add(w.body.pos);
            if (m.body.pos.distance(t0) < 0.14f) {
                mh.held = null;
                m.heldBy = null;
                boolean shell = m.isShell();
                w.insert(m);
                if (shell) g.factory.release(m); else m.body.enabled = false;
                g.audio.play(shell ? Sfx.SHELL_IN : Sfx.MAG_IN, w.body.pos, 1f, 1f);
                g.platform.vibrate(30, 0.5f);
                if (shell && w.tubeRounds < w.type.capacity && g.player.inventory.reserve[WeaponType.AMMO_SHELL] > 0) {
                    g.showHint("Cartucho inserido (" + w.tubeRounds + "/" + w.type.capacity + ")");
                } else if (!w.chambered && w.needsAction()) {
                    g.showHint("Agora engatilhe: touchpad com a arma na mão");
                }
            }
        }
    }

    // ===================================================================== physics interaction
    private void pushProps() {
        Player p = g.player;
        Level L = level();
        for (int i = 0; i < L.props.size(); i++) {
            Prop pr = L.props.get(i);
            if (!pr.active || pr.isHeld() || pr.isStored()) continue;
            RigidBody b = pr.body;
            if (!b.enabled || b.kinematic) continue;
            float dx = b.pos.x - p.feet.x, dz = b.pos.z - p.feet.z;
            float r = p.radius + b.radiusXZ();
            float d2 = dx * dx + dz * dz;
            if (d2 >= r * r || b.pos.y - b.hy > p.feet.y + 1.5f || b.pos.y + b.hy < p.feet.y + 0.05f) continue;
            float d = (float) Math.sqrt(d2) + 1e-4f;
            float push = (r - d);
            float k = Math.min(1f, 6f / Math.max(1f, b.mass));
            b.wake();
            b.pos.x += dx / d * push * k;
            b.pos.z += dz / d * push * k;
            b.vel.x += dx / d * 1.5f * k;
            b.vel.z += dz / d * 1.5f * k;
            if (k < 1f) { p.feet.x -= dx / d * push * (1f - k); p.feet.z -= dz / d * push * (1f - k); }
        }
    }

    /** After the physics step: impact sounds, breakage on impact, thrown hits, despawning. */
    public void postPhysics(float dt) {
        Level L = level();
        for (int i = 0; i < L.props.size(); i++) {
            Prop pr = L.props.get(i);
            if (!pr.active) continue;
            RigidBody b = pr.body;
            if (pr.highlight > 0f) pr.highlight -= dt;
            if (pr.despawnTimer > 0f && !pr.isHeld()) {
                pr.despawnTimer -= dt;
                if (pr.despawnTimer <= 0f) { g.factory.release(pr); continue; }
            }
            if (!b.enabled) {
                if (b.pos.y < -50f) recover(pr);
                continue;
            }
            if (b.impactFlag) {
                float v = b.lastImpactSpeed;
                int sfx = pr.kind == ItemKind.CRATE ? Sfx.IMPACT_WOOD : (pr.kind == ItemKind.BOTTLE || pr.kind == ItemKind.CAN
                        || pr.kind == ItemKind.DEBRIS) ? Sfx.IMPACT_SOFT : Sfx.IMPACT_METAL;
                g.audio.play(sfx, b.pos, MathUtil.clamp(v / 6f, 0.15f, 1f), 0.9f + rng.range(0f, 0.2f), 18f);
                if (v > 4f) g.npcs.noise(b.pos, 9f, false); // distractions
                if (pr.kind == ItemKind.BOTTLE && v > 5f) { damageProp(pr, 100f, null); continue; }
                if (pr.health > 0f && v > 9f) damageProp(pr, v * 2f, null);
            }
            // thrown objects hurt NPCs a little
            float sp = b.vel.length();
            if (sp > 6f && b.protectTimer > 0f) {
                Npc n = g.npcs.nearestAlive(b.pos);
                if (n != null && Math.abs(n.pos.x - b.pos.x) < 0.4f && Math.abs(n.pos.z - b.pos.z) < 0.4f
                        && b.pos.y > n.pos.y && b.pos.y < n.pos.y + 1.9f) {
                    t0.set(b.vel).normalize();
                    g.npcs.damage(n, 6f + b.mass * sp * 0.6f, b.pos.y > n.pos.y + 1.45f ? NpcSystem.PART_HEAD : NpcSystem.PART_TORSO, b.pos, t0);
                    b.vel.scale(-0.3f);
                    g.audio.play(Sfx.IMPACT_SOFT, b.pos, 1f, 0.8f);
                }
            }
            if (!b.enabled || b.pos.y < -50f) recover(pr);
        }
    }

    /** Objects that fell out of the world: mission items return to the player, others vanish. */
    private void recover(Prop pr) {
        if (pr.kind == ItemKind.KEYCARD || pr.kind == ItemKind.DEVICE || pr.kind == ItemKind.WEAPON) {
            Player p = g.player;
            pr.body.enabled = true;
            pr.body.kinematic = false;
            pr.at(p.feet.x + 0.5f, p.feet.y + 1.2f, p.feet.z);
            pr.body.sleeping = false;
            pr.body.sector = g.sectors.current;
        } else {
            g.factory.release(pr);
        }
    }
}
