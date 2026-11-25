package com.joaolimz.vrweb.game.math;

/** Axis aligned bounding box. */
public final class Aabb {
    public float minX, minY, minZ, maxX, maxY, maxZ;

    public Aabb() { }

    public Aabb(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        set(minX, minY, minZ, maxX, maxY, maxZ);
    }

    public Aabb set(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        this.minX = Math.min(minX, maxX); this.maxX = Math.max(minX, maxX);
        this.minY = Math.min(minY, maxY); this.maxY = Math.max(minY, maxY);
        this.minZ = Math.min(minZ, maxZ); this.maxZ = Math.max(minZ, maxZ);
        return this;
    }

    public Aabb setCenter(float cx, float cy, float cz, float hx, float hy, float hz) {
        minX = cx - hx; maxX = cx + hx; minY = cy - hy; maxY = cy + hy; minZ = cz - hz; maxZ = cz + hz;
        return this;
    }

    public Aabb set(Aabb o) { return set(o.minX, o.minY, o.minZ, o.maxX, o.maxY, o.maxZ); }

    public float centerX() { return (minX + maxX) * 0.5f; }
    public float centerY() { return (minY + maxY) * 0.5f; }
    public float centerZ() { return (minZ + maxZ) * 0.5f; }

    public boolean contains(float x, float y, float z) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    public boolean containsXZ(float x, float z) { return x >= minX && x <= maxX && z >= minZ && z <= maxZ; }

    public boolean intersects(Aabb o) {
        return minX < o.maxX && maxX > o.minX && minY < o.maxY && maxY > o.minY && minZ < o.maxZ && maxZ > o.minZ;
    }

    /**
     * Ray/box slab test. Returns entry distance in [0,maxT] or -1. Writes the hit face normal axis into normalOut
     * when not null.
     */
    public float raycast(float ox, float oy, float oz, float dx, float dy, float dz, float maxT, Vec3 normalOut) {
        float tmin = 0f, tmax = maxT;
        int axis = -1; float sign = 0f;
        // X
        if (Math.abs(dx) < 1e-8f) {
            if (ox < minX || ox > maxX) return -1f;
        } else {
            float inv = 1f / dx;
            float t1 = (minX - ox) * inv, t2 = (maxX - ox) * inv;
            float s = -1f;
            if (t1 > t2) { float t = t1; t1 = t2; t2 = t; s = 1f; }
            if (t1 > tmin) { tmin = t1; axis = 0; sign = s; }
            if (t2 < tmax) tmax = t2;
            if (tmin > tmax) return -1f;
        }
        if (Math.abs(dy) < 1e-8f) {
            if (oy < minY || oy > maxY) return -1f;
        } else {
            float inv = 1f / dy;
            float t1 = (minY - oy) * inv, t2 = (maxY - oy) * inv;
            float s = -1f;
            if (t1 > t2) { float t = t1; t1 = t2; t2 = t; s = 1f; }
            if (t1 > tmin) { tmin = t1; axis = 1; sign = s; }
            if (t2 < tmax) tmax = t2;
            if (tmin > tmax) return -1f;
        }
        if (Math.abs(dz) < 1e-8f) {
            if (oz < minZ || oz > maxZ) return -1f;
        } else {
            float inv = 1f / dz;
            float t1 = (minZ - oz) * inv, t2 = (maxZ - oz) * inv;
            float s = -1f;
            if (t1 > t2) { float t = t1; t1 = t2; t2 = t; s = 1f; }
            if (t1 > tmin) { tmin = t1; axis = 2; sign = s; }
            if (t2 < tmax) tmax = t2;
            if (tmin > tmax) return -1f;
        }
        if (normalOut != null) {
            normalOut.set(0, 0, 0);
            if (axis == 0) normalOut.x = sign; else if (axis == 1) normalOut.y = sign; else if (axis == 2) normalOut.z = sign;
            else normalOut.set(-dx, -dy, -dz).normalize();
        }
        return tmin;
    }
}
