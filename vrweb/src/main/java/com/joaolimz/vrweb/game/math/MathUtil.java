package com.joaolimz.vrweb.game.math;

/** Small numeric helpers shared by gameplay code. */
public final class MathUtil {
    private MathUtil() { }

    public static final float PI = (float) Math.PI;
    public static final float DEG = PI / 180f;

    public static float clamp(float v, float lo, float hi) { return v < lo ? lo : (v > hi ? hi : v); }

    public static int clamp(int v, int lo, int hi) { return v < lo ? lo : (v > hi ? hi : v); }

    public static float lerp(float a, float b, float t) { return a + (b - a) * t; }

    /** Frame-rate independent exponential smoothing factor. */
    public static float damp(float rate, float dt) { return 1f - (float) Math.exp(-rate * dt); }

    public static float wrapAngle(float a) {
        while (a > PI) a -= 2 * PI;
        while (a < -PI) a += 2 * PI;
        return a;
    }

    public static float approach(float v, float target, float step) {
        if (v < target) return Math.min(v + step, target);
        return Math.max(v - step, target);
    }

    /** Ray vs sphere. Returns distance or -1. Direction must be normalized. */
    public static float raySphere(Vec3 o, Vec3 d, float cx, float cy, float cz, float r, float maxT) {
        float ox = o.x - cx, oy = o.y - cy, oz = o.z - cz;
        float b = ox * d.x + oy * d.y + oz * d.z;
        float c = ox * ox + oy * oy + oz * oz - r * r;
        if (c > 0f && b > 0f) return -1f;
        float disc = b * b - c;
        if (disc < 0f) return -1f;
        float t = -b - (float) Math.sqrt(disc);
        if (t < 0f) t = 0f;
        return t <= maxT ? t : -1f;
    }

    /** Ray vs finite vertical cylinder (axis Y) between y0 and y1. Direction normalized. */
    public static float rayCylinderY(Vec3 o, Vec3 d, float cx, float cz, float r, float y0, float y1, float maxT) {
        float ox = o.x - cx, oz = o.z - cz;
        float a = d.x * d.x + d.z * d.z;
        float best = -1f;
        if (a > 1e-8f) {
            float b = ox * d.x + oz * d.z;
            float c = ox * ox + oz * oz - r * r;
            float disc = b * b - a * c;
            if (disc >= 0f) {
                float t = (-b - (float) Math.sqrt(disc)) / a;
                if (t < 0f && c <= 0f) t = 0f; // origin inside the infinite cylinder
                if (t >= 0f && t <= maxT) {
                    float y = o.y + d.y * t;
                    if (y >= y0 && y <= y1) best = t;
                }
            }
        }
        // caps
        if (Math.abs(d.y) > 1e-8f) {
            float[] caps = CAPS;
            caps[0] = y0; caps[1] = y1;
            for (int i = 0; i < 2; i++) {
                float t = (caps[i] - o.y) / d.y;
                if (t >= 0f && t <= maxT && (best < 0f || t < best)) {
                    float px = ox + d.x * t, pz = oz + d.z * t;
                    if (px * px + pz * pz <= r * r) best = t;
                }
            }
        }
        return best;
    }

    private static final float[] CAPS = new float[2];

    /** Deterministic xorshift random used by gameplay so tests are reproducible. */
    public static final class Rng {
        private long s;
        public Rng(long seed) { s = seed == 0 ? 0x9E3779B97F4A7C15L : seed; }
        public long nextLong() { s ^= s << 13; s ^= s >>> 7; s ^= s << 17; return s; }
        public float nextFloat() { return (nextLong() >>> 40) / (float) (1L << 24); }
        public float range(float a, float b) { return a + (b - a) * nextFloat(); }
        public int nextInt(int n) { return (int) ((nextLong() >>> 33) % n); }
    }
}
