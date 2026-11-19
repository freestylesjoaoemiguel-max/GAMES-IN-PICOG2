package com.joaolimz.operationzero.game.player;

import com.joaolimz.operationzero.game.math.Quat;
import com.joaolimz.operationzero.game.math.Vec3;
import com.joaolimz.operationzero.game.weapons.Weapon;
import com.joaolimz.operationzero.game.weapons.WeaponType;
import com.joaolimz.operationzero.game.world.ItemKind;
import com.joaolimz.operationzero.game.world.Prop;

/**
 * Body slots: HIP holster (pistol/SMG), BACK (long gun), two chest pockets (small items) and the
 * ammo pouch on the off-hand hip which hands out magazines from the ammo reserve.
 */
public final class Inventory {
    public static final int HIP = 0, BACK = 1, CHEST_L = 2, CHEST_R = 3, POUCH = 4;
    public static final int SLOTS = 4;
    public static final String[] NAMES = {"Coldre (cintura)", "Costas", "Bolso esquerdo", "Bolso direito", "Bolsa de municao"};

    public final Prop[] items = new Prop[SLOTS];
    /** Reserve ammo by class (rounds). */
    public final int[] reserve = new int[4];

    public boolean accepts(int slot, Prop p) {
        if (slot == POUCH) return p.kind == ItemKind.MAGAZINE || p.kind == ItemKind.SHELL || p.kind == ItemKind.AMMO_BOX;
        if (items[slot] != null) return false;
        if (slot == HIP) return p instanceof Weapon && (((Weapon) p).type == WeaponType.VX9 || ((Weapon) p).type == WeaponType.RAPTOR);
        if (slot == BACK) return p instanceof Weapon;
        return ItemKind.isSmall(p.kind) || p.kind == ItemKind.TOOL || p.kind == ItemKind.BOTTLE;
    }

    /**
     * Slot grab zone in world space. The body frame is the torso (eye position + torso yaw).
     * Positions were chosen from measured arm-model reach (see tests) so that none of them overlaps the
     * natural aiming pose: hip = point the hand straight down, back = point up and outward over the
     * shoulder of that hand, pockets = point down and inward. rightHanded mirrors HIP/POUCH.
     */
    public static Vec3 zone(int slot, Vec3 eye, Quat torso, boolean rightHanded, float handSide, Vec3 out) {
        float s = rightHanded ? 1f : -1f;
        switch (slot) {
            case HIP: torso.rotate(0.2f * s, -0.8f, 0.15f, out); break;
            case BACK: torso.rotate(0.33f * handSide, -0.2f, -0.14f, out); break;
            case CHEST_L: torso.rotate(-0.07f, -0.7f, -0.06f, out); break;
            case CHEST_R: torso.rotate(0.07f, -0.7f, -0.06f, out); break;
            default: torso.rotate(-0.2f * s, -0.8f, 0.15f, out); break;
        }
        return out.add(eye);
    }

    /** Where the stored item is drawn (may differ from the grab zone, e.g. the back). */
    public static Vec3 rest(int slot, Vec3 eye, Quat torso, boolean rightHanded, Vec3 out) {
        float s = rightHanded ? 1f : -1f;
        switch (slot) {
            case HIP: torso.rotate(0.22f * s, -0.86f, 0.06f, out); break;
            case BACK: torso.rotate(0.05f * s, -0.45f, 0.2f, out); break;
            case CHEST_L: torso.rotate(-0.1f, -0.5f, -0.1f, out); break;
            case CHEST_R: torso.rotate(0.1f, -0.5f, -0.1f, out); break;
            default: torso.rotate(-0.22f * s, -0.86f, 0.06f, out); break;
        }
        return out.add(eye);
    }

    public static float radius(int slot) { return slot == BACK ? 0.17f : slot == POUCH || slot == HIP ? 0.19f : 0.12f; }

    public boolean hasItemKind(int kind) {
        for (int i = 0; i < SLOTS; i++) if (items[i] != null && items[i].kind == kind) return true;
        return false;
    }

    public int indexOf(Prop p) {
        for (int i = 0; i < SLOTS; i++) if (items[i] == p) return i;
        return -1;
    }

    public void clear() {
        for (int i = 0; i < SLOTS; i++) items[i] = null;
        for (int i = 0; i < reserve.length; i++) reserve[i] = 0;
    }
}
