package com.joaolimz.pegapega.game.physics;

/**
 * Collision representation of a hinged door: an oriented box in the XZ plane with a vertical extent.
 * The panel extends from the hinge along its local +X axis rotated by {@code angle} (radians, around Y).
 */
public final class HingePanel {
    public float hingeX, hingeZ, y0, y1;
    public float length, halfThickness = 0.05f;
    /** Closed rest yaw of the door. */
    public float baseYaw;
    public float angle;
    public boolean enabled = true;
    /** Torque accumulated from collisions this frame; consumed by the owning door. */
    public float torqueAccum;
    public int sector;
    public Object owner;

    public float dirX() { return (float) Math.cos(baseYaw + angle); }

    public float dirZ() { return -(float) Math.sin(baseYaw + angle); }

    public float centerX() { return hingeX + dirX() * length * 0.5f; }

    public float centerZ() { return hingeZ + dirZ() * length * 0.5f; }
}
