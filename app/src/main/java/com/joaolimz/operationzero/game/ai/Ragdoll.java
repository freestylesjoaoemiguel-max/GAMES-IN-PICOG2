package com.joaolimz.operationzero.game.ai;

import com.joaolimz.operationzero.game.math.Aabb;
import com.joaolimz.operationzero.game.math.Vec3;
import com.joaolimz.operationzero.game.physics.Layers;
import com.joaolimz.operationzero.game.physics.PhysicsWorld;
import com.joaolimz.operationzero.game.physics.StaticCollider;

import java.util.ArrayList;

/**
 * Simplified Verlet ragdoll: 11 point masses connected by distance constraints. Very cheap (no
 * rigid body solver), sleeps when settled and is recycled from a small pool after a timeout.
 */
public final class Ragdoll {
    public static final int HEAD = 0, CHEST = 1, PELVIS = 2, L_ELBOW = 3, L_HAND = 4, R_ELBOW = 5, R_HAND = 6;
    public static final int L_KNEE = 7, L_FOOT = 8, R_KNEE = 9, R_FOOT = 10, COUNT = 11;

    public final Vec3[] p = new Vec3[COUNT];
    public final Vec3[] prev = new Vec3[COUNT];
    private static final int[][] STICKS = {
            {HEAD, CHEST}, {CHEST, PELVIS}, {CHEST, L_ELBOW}, {L_ELBOW, L_HAND}, {CHEST, R_ELBOW}, {R_ELBOW, R_HAND},
            {PELVIS, L_KNEE}, {L_KNEE, L_FOOT}, {PELVIS, R_KNEE}, {R_KNEE, R_FOOT},
            // braces (keep the torso from folding unnaturally)
            {HEAD, PELVIS}, {L_KNEE, R_KNEE}, {L_ELBOW, R_ELBOW}, {CHEST, L_KNEE}, {CHEST, R_KNEE}
    };
    private final float[] rest = new float[STICKS.length];
    private final float[] minOnly = new float[STICKS.length];

    public boolean active;
    public boolean sleeping;
    public float age;
    public float sink;
    public int type;
    public int sector;
    private float stillTime;
    private final Aabb probe = new Aabb();

    public Ragdoll() {
        for (int i = 0; i < COUNT; i++) { p[i] = new Vec3(); prev[i] = new Vec3(); }
    }

    /** Starts from the given joint positions with an initial velocity (per second). */
    public void start(Vec3[] joints, Vec3 velocity, float dt, int type, int sector) {
        for (int i = 0; i < COUNT; i++) {
            p[i].set(joints[i]);
            prev[i].set(joints[i]).addScaled(velocity, -dt);
        }
        for (int s = 0; s < STICKS.length; s++) {
            rest[s] = p[STICKS[s][0]].distance(p[STICKS[s][1]]);
            minOnly[s] = s >= 11 ? 1f : 0f; // knee-knee / elbow-elbow braces only push apart
        }
        active = true;
        sleeping = false;
        age = 0f;
        sink = 0f;
        stillTime = 0f;
        this.type = type;
        this.sector = sector;
    }

    /** Adds an impulse (velocity change in m/s) to the particle closest to a point. */
    public void impulse(Vec3 at, Vec3 dv, float dt) {
        int best = 0;
        float bd = Float.MAX_VALUE;
        for (int i = 0; i < COUNT; i++) {
            float d = p[i].distanceSq(at);
            if (d < bd) { bd = d; best = i; }
        }
        prev[best].addScaled(dv, -dt);
        sleeping = false;
        stillTime = 0f;
    }

    public void update(PhysicsWorld world, float dt, float lifetime) {
        if (!active) return;
        age += dt;
        if (age > lifetime) {
            sink += dt * 0.4f;
            for (int i = 0; i < COUNT; i++) p[i].y -= dt * 0.4f;
            if (sink > 0.6f) active = false;
            return;
        }
        if (sleeping) return;
        float maxMove = 0f;
        float g = PhysicsWorld.GRAVITY * dt * dt;
        for (int i = 0; i < COUNT; i++) {
            Vec3 c = p[i], o = prev[i];
            float vx = (c.x - o.x) * 0.99f, vy = (c.y - o.y) * 0.99f, vz = (c.z - o.z) * 0.99f;
            o.set(c);
            c.x += vx; c.y += vy - g; c.z += vz;
        }
        for (int iter = 0; iter < 4; iter++) {
            for (int s = 0; s < STICKS.length; s++) {
                Vec3 a = p[STICKS[s][0]], b = p[STICKS[s][1]];
                float dx = b.x - a.x, dy = b.y - a.y, dz = b.z - a.z;
                float d = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (d < 1e-5f) continue;
                if (minOnly[s] > 0f && d >= rest[s] * 0.6f) continue;
                float target = minOnly[s] > 0f ? rest[s] * 0.6f : rest[s];
                float diff = (d - target) / d * 0.5f;
                a.x += dx * diff; a.y += dy * diff; a.z += dz * diff;
                b.x -= dx * diff; b.y -= dy * diff; b.z -= dz * diff;
            }
            collide(world);
        }
        for (int i = 0; i < COUNT; i++) maxMove = Math.max(maxMove, p[i].distanceSq(prev[i]));
        if (maxMove < 0.0002f * 0.0002f * 100f) {
            stillTime += dt;
            if (stillTime > 0.6f) sleeping = true;
        } else {
            stillTime = 0f;
        }
    }

    private void collide(PhysicsWorld world) {
        float r = 0.07f;
        for (int i = 0; i < COUNT; i++) {
            Vec3 c = p[i];
            ArrayList<StaticCollider> l = world.gatherColliders(c.x - r, c.y - r, c.z - r, c.x + r, c.y + r, c.z + r, Layers.STATIC);
            for (int k = 0; k < l.size(); k++) {
                Aabb b = l.get(k).box;
                probe.set(b.minX - r, b.minY - r, b.minZ - r, b.maxX + r, b.maxY + r, b.maxZ + r);
                if (!probe.contains(c.x, c.y, c.z)) continue;
                float dx0 = c.x - probe.minX, dx1 = probe.maxX - c.x;
                float dy0 = c.y - probe.minY, dy1 = probe.maxY - c.y;
                float dz0 = c.z - probe.minZ, dz1 = probe.maxZ - c.z;
                float m = Math.min(Math.min(Math.min(dx0, dx1), Math.min(dy0, dy1)), Math.min(dz0, dz1));
                Vec3 o = prev[i];
                if (m == dy1) {
                    c.y = probe.maxY;
                    // ground friction
                    o.x += (c.x - o.x) * 0.4f; o.z += (c.z - o.z) * 0.4f;
                } else if (m == dy0) c.y = probe.minY;
                else if (m == dx0) c.x = probe.minX;
                else if (m == dx1) c.x = probe.maxX;
                else if (m == dz0) c.z = probe.minZ;
                else c.z = probe.maxZ;
            }
        }
    }

    public Vec3 center(Vec3 out) { return out.set(p[CHEST]).lerp(p[PELVIS], 0.5f); }
}
