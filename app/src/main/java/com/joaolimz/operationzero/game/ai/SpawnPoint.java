package com.joaolimz.operationzero.game.ai;

import com.joaolimz.operationzero.game.math.Vec3;
import com.joaolimz.operationzero.game.world.Door;

/** Pre-defined enemy spawn position (rooms, behind doors). */
public final class SpawnPoint {
    public final Vec3 pos = new Vec3();
    public final float yaw;
    public final String group;
    public final int sector;
    /** Optional door that opens when an enemy spawns behind it. */
    public Door door;
    public float lastUsed = -100f;

    public SpawnPoint(String group, float x, float y, float z, float yaw, int sector) {
        this.group = group;
        this.pos.set(x, y, z);
        this.yaw = yaw;
        this.sector = sector;
    }
}
