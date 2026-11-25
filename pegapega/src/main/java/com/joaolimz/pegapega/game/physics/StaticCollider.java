package com.joaolimz.pegapega.game.physics;

import com.joaolimz.pegapega.game.math.Aabb;

/** Immovable box collider (walls, floors, furniture). Moving variants (sliding doors) set {@code movable}. */
public final class StaticCollider {
    public final Aabb box = new Aabb();
    public int layer = Layers.STATIC;
    public int sector;
    public boolean enabled = true;
    /** Movable colliders are kept out of the spatial grid and tested linearly. */
    public final boolean movable;
    /** Optional gameplay owner (door, breakable...). */
    public Object owner;
    /** Surface material for impact sounds/effects. */
    public int material;
    int stamp;

    public static final int MAT_CONCRETE = 0, MAT_METAL = 1, MAT_WOOD = 2, MAT_GLASS = 3;

    public StaticCollider(float minX, float minY, float minZ, float maxX, float maxY, float maxZ, int sector, boolean movable) {
        box.set(minX, minY, minZ, maxX, maxY, maxZ);
        this.sector = sector;
        this.movable = movable;
    }
}
