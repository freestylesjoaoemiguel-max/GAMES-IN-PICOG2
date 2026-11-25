package com.joaolimz.operationzero.game.ai;

import com.joaolimz.operationzero.game.math.Vec3;

/**
 * Pre-placed cover position next to an obstacle (crate, pillar, car, barricade). The normal points
 * from the NPC toward the obstacle, i.e. the direction it protects from.
 */
public final class CoverPoint {
    public final Vec3 pos = new Vec3();
    public final Vec3 normal = new Vec3();
    /** Low cover: crouch to hide, stand to peek. High cover: step sideways to peek. */
    public final boolean low;
    public final int sector;
    public Object occupant;
    public float reservedUntil;
    /** Nearest nav node (filled at level build). */
    public int node = -1;

    public CoverPoint(float x, float y, float z, float nx, float nz, boolean low, int sector) {
        pos.set(x, y, z);
        normal.set(nx, 0, nz).normalize();
        this.low = low;
        this.sector = sector;
    }
}
