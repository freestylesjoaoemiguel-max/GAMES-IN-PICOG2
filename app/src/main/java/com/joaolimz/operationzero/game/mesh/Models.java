package com.joaolimz.operationzero.game.mesh;

/**
 * Original procedural low-poly models (no external assets). Every model is authored here in code so the
 * project has zero licensing risk. External OBJ files can replace any model by name (see {@link ObjLoader}).
 *
 * Weapon convention: origin = grip (where the hand holds), barrel along -Z, +Y up.
 * Magazine convention: origin = top of the magazine (insertion point), body extends to -Y.
 * Segment convention ("limb"): unit length along Z centered at origin (scaled at runtime).
 */
public final class Models {
    private Models() { }

    private static final MeshBuilder B = new MeshBuilder();

    // --------------------------------------------------------------- hands
    public static final int POSE_OPEN = 0, POSE_FIST = 1, POSE_POINT = 2;

    public static MeshData hand(boolean right, int pose) {
        MeshBuilder b = B.clear();
        float s = right ? 1f : -1f;
        int glove = 0x2B2F36, knuckle = 0x3A4048, accent = 0xE07A1F;
        // cuff
        b.color(accent).box(0.0f * s, 0f, 0.075f, 0.042f, 0.042f, 0.02f);
        // palm (palm faces -X for right hand when gripping a vertical handle)
        b.color(glove).box(0.018f * s, 0.0f, 0.02f, 0.022f, 0.045f, 0.05f);
        if (pose == POSE_OPEN) {
            for (int f = 0; f < 4; f++) {
                float y = 0.032f - f * 0.021f;
                b.color(f % 2 == 0 ? glove : knuckle).box(0.016f * s, y, -0.055f, 0.016f, 0.009f, 0.035f);
            }
            b.color(glove).box(-0.012f * s, 0.035f, -0.005f, 0.012f, 0.01f, 0.03f); // thumb
        } else {
            boolean point = pose == POSE_POINT;
            for (int f = 0; f < 4; f++) {
                float y = 0.032f - f * 0.021f;
                if (point && f == 0) {
                    b.color(glove).box(0.012f * s, y, -0.06f, 0.011f, 0.009f, 0.04f);
                } else {
                    // curled: wrap around the grip (front then inner segment)
                    b.color(knuckle).box(0.0f * s, y, -0.035f, 0.022f, 0.0095f, 0.014f);
                    b.color(glove).box(-0.02f * s, y, -0.012f, 0.009f, 0.0095f, 0.02f);
                }
            }
            b.color(glove).box(-0.016f * s, 0.038f, -0.02f, 0.011f, 0.011f, 0.026f); // thumb over grip
        }
        return b.build("hand_" + (right ? "r" : "l") + pose);
    }

    /** Unit limb segment (length 1 along Z) used for arms / ragdoll / NPC limbs. */
    public static MeshData limb(String name, int rgb, int accentRgb) {
        MeshBuilder b = B.clear();
        b.color(rgb).box(0, 0, 0, 0.5f, 0.5f, 0.5f);
        b.color(accentRgb).box(0, 0.42f, -0.15f, 0.35f, 0.12f, 0.2f);
        return b.build(name);
    }

    public static MeshData playerTorso() {
        MeshBuilder b = B.clear();
        b.color(0x30363F).box(0, -0.25f, 0.02f, 0.17f, 0.25f, 0.1f);       // chest
        b.color(0x3D4652).box(0, -0.12f, -0.085f, 0.15f, 0.1f, 0.02f);     // plate carrier
        b.color(0xE07A1F).box(0, -0.06f, -0.107f, 0.05f, 0.012f, 0.003f);  // stripe
        b.color(0x262A30).box(0, -0.55f, 0.02f, 0.16f, 0.07f, 0.1f);       // belt
        b.color(0x2E3A2F).box(-0.09f, -0.95f, 0.02f, 0.07f, 0.33f, 0.08f);  // legs
        b.color(0x2E3A2F).box(0.09f, -0.95f, 0.02f, 0.07f, 0.33f, 0.08f);
        b.color(0x1C1E22).box(-0.09f, -1.3f, -0.03f, 0.06f, 0.03f, 0.12f);  // boots
        b.color(0x1C1E22).box(0.09f, -1.3f, -0.03f, 0.06f, 0.03f, 0.12f);
        return b.build("player_torso");
    }

    // --------------------------------------------------------------- weapons
    public static MeshData pistol() {
        MeshBuilder b = B.clear();
        b.color(0x23262B).box(0, -0.045f, 0.005f, 0.015f, 0.05f, 0.022f);    // grip
        b.push().rotate(1, 0, 0, -0.25f);
        b.color(0x2C3036).box(0, -0.04f, 0.0f, 0.016f, 0.045f, 0.02f);
        b.pop();
        b.color(0x30343A).box(0, 0.025f, -0.06f, 0.014f, 0.02f, 0.09f);      // frame
        b.color(0x1E2024).box(0, 0.0f, -0.035f, 0.004f, 0.015f, 0.016f);     // trigger guard
        b.color(0xE07A1F).box(0, 0.047f, 0.022f, 0.005f, 0.004f, 0.004f);     // rear sight
        b.color(0xE07A1F).box(0, 0.047f, -0.142f, 0.003f, 0.004f, 0.003f);   // front sight
        return b.build("pistol");
    }

    public static MeshData pistolSlide() {
        MeshBuilder b = B.clear();
        b.color(0x4A5058).box(0, 0.032f, -0.06f, 0.0145f, 0.012f, 0.088f);
        b.color(0x2A2D33).box(0, 0.032f, 0.01f, 0.015f, 0.01f, 0.004f);
        return b.build("pistol_slide");
    }

    public static MeshData smg() {
        MeshBuilder b = B.clear();
        b.color(0x23262B).box(0, -0.05f, 0.0f, 0.016f, 0.055f, 0.022f);       // grip
        b.color(0x343A40).box(0, 0.03f, -0.1f, 0.022f, 0.035f, 0.17f);       // receiver
        b.color(0x1D1F22).box(0, 0.035f, -0.32f, 0.01f, 0.01f, 0.06f);       // barrel
        b.color(0x2A2F35).box(0, -0.02f, -0.24f, 0.018f, 0.045f, 0.03f);     // foregrip
        b.color(0x23262B).box(0, 0.03f, 0.13f, 0.012f, 0.03f, 0.08f);        // stock
        b.color(0xE07A1F).box(0, 0.072f, -0.1f, 0.006f, 0.008f, 0.03f);      // sight
        return b.build("smg");
    }

    public static MeshData shotgun() {
        MeshBuilder b = B.clear();
        b.color(0x23262B).box(0, -0.05f, 0.0f, 0.017f, 0.05f, 0.025f);
        b.color(0x3A3F45).box(0, 0.03f, -0.12f, 0.022f, 0.035f, 0.15f);
        b.color(0x1A1C1F).box(0, 0.045f, -0.42f, 0.014f, 0.014f, 0.18f);    // barrel
        b.color(0x26292D).box(0, 0.015f, -0.42f, 0.012f, 0.012f, 0.16f);    // tube
        b.color(0x5A3E26).box(0, 0.02f, 0.16f, 0.016f, 0.04f, 0.13f);       // stock (wood-like)
        b.color(0xE07A1F).box(0, 0.064f, -0.6f, 0.004f, 0.006f, 0.004f);
        return b.build("shotgun");
    }

    public static MeshData shotgunPump() {
        MeshBuilder b = B.clear();
        b.color(0x6A4A2E).box(0, 0.015f, -0.36f, 0.02f, 0.022f, 0.07f);
        return b.build("shotgun_pump");
    }

    public static MeshData rifle() {
        MeshBuilder b = B.clear();
        b.color(0x23262B).box(0, -0.05f, 0.0f, 0.016f, 0.055f, 0.022f);
        b.color(0x4B4F3E).box(0, 0.035f, -0.13f, 0.022f, 0.04f, 0.2f);       // receiver
        b.color(0x3D402F).box(0, 0.035f, -0.42f, 0.02f, 0.03f, 0.1f);        // handguard
        b.color(0x1A1C1F).box(0, 0.04f, -0.6f, 0.008f, 0.008f, 0.09f);       // barrel
        b.color(0x30332A).box(0, 0.03f, 0.2f, 0.018f, 0.045f, 0.12f);        // stock
        b.color(0x1E2023).box(0, 0.1f, -0.15f, 0.014f, 0.016f, 0.07f);       // optic
        b.color(0x8EC9FF).box(0, 0.1f, -0.221f, 0.01f, 0.01f, 0.002f);       // lens
        b.color(0xE07A1F).box(0, 0.075f, 0.03f, 0.012f, 0.004f, 0.01f);      // charging handle
        return b.build("rifle");
    }

    public static MeshData magazine(String name, float halfDepth, float length, int rgb) {
        MeshBuilder b = B.clear();
        b.color(rgb).box(0, -length * 0.5f, 0, 0.012f, length * 0.5f, halfDepth);
        b.color(0xE07A1F).box(0, -length + 0.006f, 0, 0.013f, 0.006f, halfDepth + 0.002f);
        b.color(0xC9A13B).box(0, -0.004f, 0, 0.006f, 0.004f, halfDepth * 0.6f); // visible round
        return b.build(name);
    }

    public static MeshData shell() {
        MeshBuilder b = B.clear();
        b.push().rotate(1, 0, 0, MathUtilHalfPi());
        b.color(0xB8322B).cylinder(0.011f, 0.03f, 6, true);
        b.color(0xC9A13B).push().translate(0, 0.03f, 0).cylinder(0.012f, 0.007f, 6, true).pop();
        b.pop();
        return b.build("shell");
    }

    private static float MathUtilHalfPi() { return (float) (Math.PI / 2); }

    // --------------------------------------------------------------- props
    public static MeshData crate(String name, float hx, float hy, float hz, int rgb) {
        MeshBuilder b = B.clear();
        b.color(rgb).box(0, 0, 0, hx, hy, hz);
        int dark = darken(rgb, 0.7f);
        float t = Math.min(hx, Math.min(hy, hz)) * 0.12f;
        b.color(dark);
        b.box(0, hy - t * 0.5f, hz + 0.002f, hx, t * 0.5f, 0.004f);
        b.box(0, -hy + t * 0.5f, hz + 0.002f, hx, t * 0.5f, 0.004f);
        b.box(0, hy - t * 0.5f, -hz - 0.002f, hx, t * 0.5f, 0.004f);
        b.box(0, -hy + t * 0.5f, -hz - 0.002f, hx, t * 0.5f, 0.004f);
        b.box(hx + 0.002f, 0, 0, 0.004f, hy, t * 0.5f);
        b.box(-hx - 0.002f, 0, 0, 0.004f, hy, t * 0.5f);
        return b.build(name);
    }

    public static MeshData barrel() {
        MeshBuilder b = B.clear();
        b.color(0x2F5D8A).cylinder(0.28f, 0.42f, 10, true);
        b.color(0x23466A).push().translate(0, 0.2f, 0).cylinder(0.29f, 0.02f, 10, false).pop();
        b.color(0x23466A).push().translate(0, -0.2f, 0).cylinder(0.29f, 0.02f, 10, false).pop();
        return b.build("barrel");
    }

    public static MeshData bottle() {
        MeshBuilder b = B.clear();
        b.color(0x2F7A4A).cylinder(0.035f, 0.08f, 7, true);
        b.color(0x2F7A4A).push().translate(0, 0.11f, 0).cylinder(0.014f, 0.035f, 6, true).pop();
        b.color(0xD9D2B8).push().translate(0, 0.0f, 0).cylinder(0.036f, 0.03f, 7, false).pop();
        return b.build("bottle");
    }

    public static MeshData can() {
        MeshBuilder b = B.clear();
        b.color(0xC23B32).cylinder(0.033f, 0.06f, 8, true);
        b.color(0xBFC4C9).push().translate(0, 0.058f, 0).cylinder(0.031f, 0.004f, 8, true).pop();
        return b.build("can");
    }

    public static MeshData wrench() {
        MeshBuilder b = B.clear();
        b.color(0x9AA3AD).box(0, 0, -0.1f, 0.012f, 0.006f, 0.14f);
        b.color(0x9AA3AD).box(0, 0, -0.25f, 0.03f, 0.008f, 0.018f);
        b.color(0x9AA3AD).box(-0.02f, 0, -0.275f, 0.008f, 0.008f, 0.02f);
        b.color(0x9AA3AD).box(0.02f, 0, -0.275f, 0.008f, 0.008f, 0.02f);
        b.color(0xE07A1F).box(0, 0, 0.02f, 0.016f, 0.012f, 0.05f);
        return b.build("wrench");
    }

    public static MeshData keycard() {
        MeshBuilder b = B.clear();
        b.color(0xE8E8E8).box(0, 0, -0.03f, 0.027f, 0.002f, 0.043f);
        b.color(0xE07A1F).box(0, 0.0025f, -0.05f, 0.025f, 0.0008f, 0.015f);
        b.color(0x2D6CDF).box(0.012f, 0.0025f, -0.015f, 0.008f, 0.0008f, 0.008f);
        return b.build("keycard");
    }

    public static MeshData device() {
        MeshBuilder b = B.clear();
        b.color(0x2A2E35).box(0, 0, -0.02f, 0.05f, 0.04f, 0.07f);
        b.color(0x40E0FF).box(0, 0.042f, -0.02f, 0.035f, 0.004f, 0.05f);
        b.color(0xE07A1F).box(0, 0, -0.095f, 0.02f, 0.02f, 0.006f);
        b.color(0x40E0FF).push().translate(0, 0.0f, 0.06f).sphere(0.03f, 6, 4).pop();
        return b.build("device");
    }

    public static MeshData medkit() {
        MeshBuilder b = B.clear();
        b.color(0xE6E6E6).box(0, 0, 0, 0.09f, 0.05f, 0.06f);
        b.color(0x2FB36A).box(0, 0.051f, 0, 0.04f, 0.002f, 0.012f);
        b.color(0x2FB36A).box(0, 0.051f, 0, 0.012f, 0.002f, 0.04f);
        return b.build("medkit");
    }

    public static MeshData ammoBox(int stripe) {
        MeshBuilder b = B.clear();
        b.color(0x4B5320).box(0, 0, 0, 0.09f, 0.055f, 0.05f);
        b.color(stripe).box(0, 0.02f, 0.051f, 0.08f, 0.012f, 0.002f);
        b.color(0x2C2F1A).box(0, 0.06f, 0, 0.03f, 0.006f, 0.01f);
        return b.build("ammo_box_" + Integer.toHexString(stripe));
    }

    public static MeshData debris() {
        MeshBuilder b = B.clear();
        b.color(0x8A6A45).box(0, 0, 0, 0.06f, 0.02f, 0.09f);
        return b.build("debris");
    }

    public static MeshData glassShard() {
        MeshBuilder b = B.clear();
        b.color(0x3F8F5E).box(0, 0, 0, 0.02f, 0.01f, 0.03f);
        return b.build("shard");
    }

    // --------------------------------------------------------------- interactables
    /** Unit door panel: hinge at origin, extends +X 1m, height 1m from 0, thickness 1 (scaled). */
    public static MeshData doorPanel(int rgb) {
        MeshBuilder b = B.clear();
        b.color(rgb).boxMinMax(0, 0, -0.5f, 1, 1, 0.5f, MeshBuilder.FACE_ALL, 0f);
        b.color(darken(rgb, 0.75f)).boxMinMax(0.1f, 0.55f, -0.52f, 0.9f, 0.9f, 0.52f, MeshBuilder.FACE_PZ | MeshBuilder.FACE_NZ, 0f);
        return b.build("door_" + Integer.toHexString(rgb));
    }

    public static MeshData doorHandle() {
        MeshBuilder b = B.clear();
        b.color(0xB8BEC6).box(0, 0, 0.03f, 0.05f, 0.012f, 0.012f);
        b.color(0xB8BEC6).box(0, 0, -0.03f, 0.05f, 0.012f, 0.012f);
        return b.build("door_handle");
    }

    /** Unit slab (1x1x1 centered) for sliding doors / shutters. */
    public static MeshData slab(String name, int rgb, int stripe) {
        MeshBuilder b = B.clear();
        b.color(rgb).box(0, 0, 0, 0.5f, 0.5f, 0.5f);
        b.color(stripe).box(0, -0.3f, 0, 0.5f, 0.04f, 0.52f);
        b.color(stripe).box(0, 0.3f, 0, 0.5f, 0.04f, 0.52f);
        return b.build(name);
    }

    public static MeshData button(int rgb) {
        MeshBuilder b = B.clear();
        b.color(0x2A2D33).box(0, 0, 0.0f, 0.06f, 0.06f, 0.015f);
        b.color(rgb).push().translate(0, 0, -0.02f).rotate(1, 0, 0, MathUtilHalfPi()).cylinder(0.035f, 0.012f, 8, true).pop();
        return b.build("button_" + Integer.toHexString(rgb));
    }

    public static MeshData leverBase() {
        MeshBuilder b = B.clear();
        b.color(0x3A3F46).box(0, 0, 0, 0.1f, 0.16f, 0.05f);
        b.color(0xE0C21F).box(0, 0.12f, -0.051f, 0.08f, 0.012f, 0.002f);
        b.color(0x2FB36A).box(0, 0.13f, -0.053f, 0.012f, 0.012f, 0.002f);
        b.color(0xC23B32).box(0, -0.13f, -0.053f, 0.012f, 0.012f, 0.002f);
        return b.build("lever_base");
    }

    /** Lever arm: pivot at origin, handle pointing +Y. */
    public static MeshData leverArm() {
        MeshBuilder b = B.clear();
        b.color(0x8B9199).box(0, 0.11f, 0, 0.012f, 0.11f, 0.012f);
        b.color(0xC23B32).box(0, 0.23f, 0, 0.03f, 0.025f, 0.025f);
        return b.build("lever_arm");
    }

    public static MeshData terminal() {
        MeshBuilder b = B.clear();
        b.color(0x30353C).box(0, 0.35f, 0, 0.32f, 0.35f, 0.22f);      // desk block
        b.color(0x23272D).box(0, 0.95f, -0.05f, 0.28f, 0.2f, 0.03f);   // monitor
        b.color(0x23272D).box(0, 0.75f, -0.05f, 0.03f, 0.05f, 0.03f);
        b.color(0x1A1D21).box(0, 0.71f, -0.15f, 0.2f, 0.01f, 0.06f);   // keyboard
        return b.build("terminal");
    }

    public static MeshData cardReader() {
        MeshBuilder b = B.clear();
        b.color(0x2A2D33).box(0, 0, 0, 0.06f, 0.09f, 0.02f);
        b.color(0x111317).box(0, 0.03f, -0.021f, 0.04f, 0.025f, 0.002f);
        return b.build("card_reader");
    }

    /** Simple quad (1x1) facing +Z centered at origin. */
    public static MeshData quad(String name, int rgb, float alpha) {
        MeshBuilder b = B.clear();
        b.color(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f, alpha);
        b.quad(-0.5f, -0.5f, 0, 0.5f, -0.5f, 0, 0.5f, 0.5f, 0, -0.5f, 0.5f, 0);
        return b.build(name);
    }

    public static MeshData target() {
        MeshBuilder b = B.clear();
        b.color(0x8A6A45).box(0, -0.6f, 0, 0.03f, 0.6f, 0.03f);
        b.color(0xE8E2D0).box(0, 0.25f, 0, 0.25f, 0.3f, 0.015f);
        b.color(0xC23B32).box(0, 0.32f, -0.017f, 0.08f, 0.08f, 0.002f);
        b.color(0x222222).box(0, 0.12f, -0.017f, 0.16f, 0.1f, 0.002f);
        return b.build("target");
    }

    // --------------------------------------------------------------- NPC (Vantor security units)
    public static MeshData npcHead(int armor, int visor) {
        MeshBuilder b = B.clear();
        b.color(armor).box(0, 0, 0, 0.11f, 0.12f, 0.12f);
        b.color(visor).box(0, 0.015f, -0.121f, 0.085f, 0.035f, 0.004f);
        b.color(darken(armor, 0.7f)).box(0, 0.125f, 0, 0.08f, 0.01f, 0.09f);
        return b.build("npc_head_" + Integer.toHexString(armor));
    }

    public static MeshData npcTorso(int armor, int accent) {
        MeshBuilder b = B.clear();
        b.color(armor).box(0, 0, 0, 0.2f, 0.26f, 0.12f);
        b.color(darken(armor, 0.8f)).box(0, 0.05f, -0.125f, 0.16f, 0.14f, 0.012f);
        b.color(accent).box(0, 0.13f, -0.139f, 0.06f, 0.015f, 0.002f);
        b.color(darken(armor, 0.6f)).box(0, -0.25f, 0, 0.19f, 0.04f, 0.11f); // belt
        return b.build("npc_torso_" + Integer.toHexString(armor));
    }

    public static MeshData npcLowBody(int armor, int visor) {
        // LOD1/LOD2 helper: full static humanoid in 1 mesh, origin at feet.
        MeshBuilder b = B.clear();
        b.color(armor).box(0, 1.25f, 0, 0.2f, 0.26f, 0.12f);
        b.color(armor).box(0, 1.62f, 0, 0.11f, 0.12f, 0.12f);
        b.color(visor).box(0, 1.635f, -0.121f, 0.085f, 0.035f, 0.004f);
        b.color(darken(armor, 0.8f)).box(-0.1f, 0.48f, 0, 0.075f, 0.48f, 0.08f);
        b.color(darken(armor, 0.8f)).box(0.1f, 0.48f, 0, 0.075f, 0.48f, 0.08f);
        b.color(armor).box(-0.26f, 1.2f, -0.05f, 0.05f, 0.27f, 0.05f);
        b.color(armor).box(0.26f, 1.2f, -0.05f, 0.05f, 0.27f, 0.05f);
        return b.build("npc_lod_" + Integer.toHexString(armor));
    }

    public static MeshData npcGun() {
        MeshBuilder b = B.clear();
        b.color(0x1E2024).box(0, 0.02f, -0.15f, 0.02f, 0.035f, 0.22f);
        b.color(0x1E2024).box(0, -0.04f, -0.02f, 0.014f, 0.04f, 0.02f);
        b.color(0x1E2024).box(0, -0.03f, -0.12f, 0.012f, 0.04f, 0.015f);
        return b.build("npc_gun");
    }

    // --------------------------------------------------------------- effects / UI
    public static MeshData flash() {
        MeshBuilder b = B.clear();
        b.color(1f, 0.85f, 0.4f, 0.9f);
        for (int i = 0; i < 3; i++) {
            b.push().rotate(0, 0, 1, i * 1.047f);
            b.quad(-0.05f, -0.012f, 0, 0.05f, -0.012f, 0, 0.05f, 0.012f, 0, -0.05f, 0.012f, 0);
            b.quad(0.05f, -0.012f, 0, -0.05f, -0.012f, 0, -0.05f, 0.012f, 0, 0.05f, 0.012f, 0);
            b.pop();
        }
        b.color(1f, 0.7f, 0.25f, 0.8f);
        b.quad(0, -0.02f, 0, 0, -0.02f, -0.12f, 0, 0.02f, -0.12f, 0, 0.02f, 0);
        b.quad(0, 0.02f, 0, 0, 0.02f, -0.12f, 0, -0.02f, -0.12f, 0, -0.02f, 0);
        return b.build("flash");
    }

    public static MeshData spark() {
        MeshBuilder b = B.clear();
        b.color(1f, 0.8f, 0.4f, 1f).box(0, 0, 0, 0.012f, 0.012f, 0.012f);
        return b.build("spark");
    }

    public static MeshData unitBox(String name, int rgb, float alpha) {
        MeshBuilder b = B.clear();
        b.color(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f, alpha);
        b.box(0, 0, 0, 0.5f, 0.5f, 0.5f);
        return b.build(name);
    }

    public static MeshData blobShadow() {
        MeshBuilder b = B.clear();
        b.color(0f, 0f, 0f, 0.45f);
        b.ring(0f, 1f, 12, 0.45f, 0f);
        return b.build("blob_shadow");
    }

    public static MeshData marker() {
        MeshBuilder b = B.clear();
        b.color(0.3f, 0.85f, 1f, 1f);
        b.ring(0.22f, 0.3f, 16, 0.9f, 0.9f);
        b.color(0.3f, 0.85f, 1f, 0.25f);
        b.disc(0.22f, 16);
        return b.build("marker");
    }

    /** Head-locked vignette ring in view space (z = -1). Inner radius 1 normalized, scaled at runtime. */
    public static MeshData vignette() {
        MeshBuilder b = B.clear();
        b.color(0f, 0f, 0f, 1f);
        b.push().rotate(1, 0, 0, MathUtilHalfPi());
        b.ring(0.55f, 0.95f, 24, 0f, 1f);
        b.ring(0.95f, 3.0f, 24, 1f, 1f);
        b.pop();
        return b.build("vignette");
    }

    public static MeshData skyDome() {
        MeshBuilder b = B.clear();
        int slices = 16, stacks = 6;
        // inverted dome, gradient colors (unlit)
        for (int j = 0; j < stacks; j++) {
            float p0 = (float) (Math.PI * 0.5 * j / stacks), p1 = (float) (Math.PI * 0.5 * (j + 1) / stacks);
            for (int i = 0; i < slices; i++) {
                float t0 = (float) (Math.PI * 2 * i / slices), t1 = (float) (Math.PI * 2 * (i + 1) / slices);
                float y0 = (float) Math.sin(p0), y1 = (float) Math.sin(p1);
                float r0 = (float) Math.cos(p0), r1 = (float) Math.cos(p1);
                float k = j / (float) stacks;
                b.color(MathLerp(0.62f, 0.25f, k), MathLerp(0.6f, 0.36f, k), MathLerp(0.58f, 0.52f, k));
                b.quad((float) Math.cos(t0) * r0 * 80, y0 * 50 - 5, (float) Math.sin(t0) * r0 * 80,
                        (float) Math.cos(t0) * r1 * 80, y1 * 50 - 5, (float) Math.sin(t0) * r1 * 80,
                        (float) Math.cos(t1) * r1 * 80, y1 * 50 - 5, (float) Math.sin(t1) * r1 * 80,
                        (float) Math.cos(t1) * r0 * 80, y0 * 50 - 5, (float) Math.sin(t1) * r0 * 80);
            }
        }
        return b.build("sky");
    }

    private static float MathLerp(float a, float b, float t) { return a + (b - a) * t; }

    public static int darken(int rgb, float k) {
        int r = (int) (((rgb >> 16) & 255) * k), g = (int) (((rgb >> 8) & 255) * k), bl = (int) ((rgb & 255) * k);
        return (r << 16) | (g << 8) | bl;
    }
}
