package com.joaolimz.operationzero.game.world;

import com.joaolimz.operationzero.game.math.Aabb;

/** Invisible volume that fires a script event when the player enters. */
public final class TriggerZone {
    public final Aabb box;
    public final String event;
    public boolean enabled = true;
    public boolean once = true;
    public boolean inside;

    public TriggerZone(String event, float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        this.event = event;
        this.box = new Aabb(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
