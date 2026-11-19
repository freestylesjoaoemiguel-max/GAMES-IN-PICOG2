package com.joaolimz.operationzero.game.world;

import com.joaolimz.operationzero.game.math.Vec3;

/**
 * Wall mounted interactive device: push button, lever, computer terminal or keycard reader.
 * Activated by touching with the hand, by pointing + trigger (accessibility for 3DoF), or by
 * presenting a required item (keycard).
 */
public final class Interactive {
    public static final int BUTTON = 0, LEVER = 1, TERMINAL = 2, READER = 3;

    public final String id;
    public final int type;
    public final Vec3 pos = new Vec3();
    /** Facing yaw (direction the front looks toward). */
    public float yaw;
    public int sector;
    public int mesh, mesh2 = -1;
    public boolean enabled = true;
    public boolean on;
    public boolean oneShot;
    public String event;
    /** For readers: item kind that must be presented. */
    public int requiredItem = -1;
    /** Lever angle (radians), -0.7 (off) .. 0.7 (on). */
    public float leverAngle = -0.7f;
    public Object grabbedBy;
    public float cooldown;
    public float pressAnim;
    /** Terminal screen lines. */
    public String[] screen;
    public String label;
    /** Feedback blink (green ok / red denied). */
    public float okFlash, deniedFlash;

    public Interactive(String id, int type, float x, float y, float z, float yaw, int sector) {
        this.id = id; this.type = type; this.pos.set(x, y, z); this.yaw = yaw; this.sector = sector;
    }

    public float radius() { return type == TERMINAL ? 0.45f : type == LEVER ? 0.25f : 0.16f; }
}
