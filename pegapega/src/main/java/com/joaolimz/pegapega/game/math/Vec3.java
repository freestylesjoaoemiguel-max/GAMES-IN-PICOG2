package com.joaolimz.pegapega.game.math;

/** Mutable 3D vector. Methods mutate and return {@code this} to avoid per-frame allocations. */
public final class Vec3 {
    public float x, y, z;

    public Vec3() { }

    public Vec3(float x, float y, float z) { this.x = x; this.y = y; this.z = z; }

    public Vec3(Vec3 o) { this.x = o.x; this.y = o.y; this.z = o.z; }

    public Vec3 set(float x, float y, float z) { this.x = x; this.y = y; this.z = z; return this; }

    public Vec3 set(Vec3 o) { x = o.x; y = o.y; z = o.z; return this; }

    public Vec3 add(Vec3 o) { x += o.x; y += o.y; z += o.z; return this; }

    public Vec3 add(float ax, float ay, float az) { x += ax; y += ay; z += az; return this; }

    public Vec3 sub(Vec3 o) { x -= o.x; y -= o.y; z -= o.z; return this; }

    public Vec3 sub(float ax, float ay, float az) { x -= ax; y -= ay; z -= az; return this; }

    public Vec3 scale(float s) { x *= s; y *= s; z *= s; return this; }

    public Vec3 addScaled(Vec3 o, float s) { x += o.x * s; y += o.y * s; z += o.z * s; return this; }

    /** this = a - b */
    public Vec3 setSub(Vec3 a, Vec3 b) { x = a.x - b.x; y = a.y - b.y; z = a.z - b.z; return this; }

    /** this = a + b */
    public Vec3 setAdd(Vec3 a, Vec3 b) { x = a.x + b.x; y = a.y + b.y; z = a.z + b.z; return this; }

    public float dot(Vec3 o) { return x * o.x + y * o.y + z * o.z; }

    public float length() { return (float) Math.sqrt(x * x + y * y + z * z); }

    public float lengthSq() { return x * x + y * y + z * z; }

    public float lengthXZ() { return (float) Math.sqrt(x * x + z * z); }

    public Vec3 normalize() {
        float l = length();
        if (l > 1e-6f) { x /= l; y /= l; z /= l; } else { x = 0; y = 0; z = -1; }
        return this;
    }

    /** this = a x b (a and b may alias this). */
    public Vec3 setCross(Vec3 a, Vec3 b) {
        float cx = a.y * b.z - a.z * b.y;
        float cy = a.z * b.x - a.x * b.z;
        float cz = a.x * b.y - a.y * b.x;
        x = cx; y = cy; z = cz;
        return this;
    }

    public Vec3 lerp(Vec3 target, float t) {
        x += (target.x - x) * t; y += (target.y - y) * t; z += (target.z - z) * t;
        return this;
    }

    public float distance(Vec3 o) { return (float) Math.sqrt(distanceSq(o)); }

    public float distanceSq(Vec3 o) {
        float dx = x - o.x, dy = y - o.y, dz = z - o.z;
        return dx * dx + dy * dy + dz * dz;
    }

    public float distanceXZ(Vec3 o) {
        float dx = x - o.x, dz = z - o.z;
        return (float) Math.sqrt(dx * dx + dz * dz);
    }

    public Vec3 clampLength(float max) {
        float l2 = lengthSq();
        if (l2 > max * max) scale(max / (float) Math.sqrt(l2));
        return this;
    }

    @Override public String toString() { return "(" + x + ", " + y + ", " + z + ")"; }
}
