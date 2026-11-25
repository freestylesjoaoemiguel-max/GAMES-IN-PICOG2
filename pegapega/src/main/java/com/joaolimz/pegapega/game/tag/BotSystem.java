package com.joaolimz.pegapega.game.tag;

import com.joaolimz.pegapega.game.math.MathUtil;
import com.joaolimz.pegapega.game.math.Vec3;
import com.joaolimz.pegapega.game.physics.Layers;
import com.joaolimz.pegapega.game.physics.PhysicsWorld;
import com.joaolimz.pegapega.game.physics.RayHit;
import com.joaolimz.pegapega.game.physics.SphereMover;
import com.joaolimz.pegapega.game.world.Arena;

import java.util.ArrayList;

/**
 * Bot players. They move like the player: hopping with their arms on the ground, wall-climbing by
 * pushing up walls, and launching off ledges. Chasers hunt the nearest free player (with lead), runners
 * flee from nearby chasers, steer around obstacles and escape to high perches.
 */
public final class BotSystem {
    public static final class Bot {
        public final Avatar a;
        float hopTimer, thinkTimer, wallTimer;
        final Vec3 desired = new Vec3();
        final Vec3 goal = new Vec3();
        boolean hasGoal;
        boolean grounded, touchingWall;
        float phase;
        float stuck;
        final Vec3 lastPos = new Vec3();
        public boolean climbing;
        Bot(Avatar a) { this.a = a; }
    }

    public final ArrayList<Bot> bots = new ArrayList<Bot>();
    private final Arena arena;
    private final PhysicsWorld world;
    private final MathUtil.Rng rng = new MathUtil.Rng(2024);
    private final RayHit hit = new RayHit();
    private final Vec3 t0 = new Vec3(), t1 = new Vec3(), body = new Vec3(), probe = new Vec3();
    public float speedScale = 1f;
    public float reaction = 0.2f;
    /** Count of hand taps this frame (for sounds): positions appended to tapPositions. */
    public final ArrayList<Vec3> taps = new ArrayList<Vec3>();
    private final Vec3[] tapPool = new Vec3[32];
    private int tapCount;

    public BotSystem(Arena arena) {
        this.arena = arena;
        this.world = arena.physics;
        for (int i = 0; i < tapPool.length; i++) tapPool[i] = new Vec3();
    }

    public Bot add(Avatar a) {
        Bot b = new Bot(a);
        bots.add(b);
        return b;
    }

    public void setDifficulty(int d) {
        speedScale = d == 0 ? 0.82f : d == 1 ? 1f : 1.15f;
        reaction = d == 0 ? 0.35f : d == 1 ? 0.2f : 0.1f;
    }

    private void tap(Vec3 p) {
        if (tapCount < tapPool.length) {
            Vec3 v = tapPool[tapCount++].set(p);
            taps.add(v);
        }
    }

    public void update(ArrayList<Avatar> all, float dt, boolean frozen) {
        taps.clear();
        tapCount = 0;
        for (int i = 0; i < bots.size(); i++) {
            Bot b = bots.get(i);
            if (!frozen) think(b, all, dt);
            physics(b, dt, frozen);
            animate(b, dt);
        }
    }

    private Avatar nearest(Avatar self, ArrayList<Avatar> all, boolean wantIt) {
        Avatar best = null;
        float bd = Float.MAX_VALUE;
        for (int i = 0; i < all.size(); i++) {
            Avatar o = all.get(i);
            if (o == self || o.it != wantIt) continue;
            float d = o.head.distanceSq(self.head);
            if (d < bd) { bd = d; best = o; }
        }
        return best;
    }

    private void think(Bot b, ArrayList<Avatar> all, float dt) {
        b.thinkTimer -= dt;
        if (b.thinkTimer > 0f) return;
        b.thinkTimer = reaction + rng.range(0f, 0.1f);
        Avatar a = b.a;
        b.climbing = false;
        if (a.it) {
            Avatar t = nearest(a, all, false);
            if (t == null) { b.desired.set(0, 0, 0); return; }
            // lead the target a little
            t0.set(t.head).addScaled(t.vel, 0.35f);
            b.desired.setSub(t0, a.head);
            float dy = t0.y - a.head.y;
            b.desired.y = 0;
            if (dy > 1.2f && b.desired.length() < 5f) b.climbing = true;
            b.desired.normalize();
        } else {
            // flee from chasers within range
            t1.set(0, 0, 0);
            float danger = 0f;
            for (int i = 0; i < all.size(); i++) {
                Avatar o = all.get(i);
                if (!o.it) continue;
                t0.setSub(a.head, o.head);
                t0.y = 0;
                float d = t0.length();
                if (d > 14f || d < 1e-3f) continue;
                float w = 1f / (d * d);
                t1.addScaled(t0.scale(1f / d), w * 10f);
                danger = Math.max(danger, 1f - d / 14f);
            }
            if (danger > 0f) {
                b.desired.set(t1);
                b.desired.y = 0;
                // keep away from the arena border
                float edge = Arena.HALF - 4f;
                if (a.head.x > edge) b.desired.x -= 2f; if (a.head.x < -edge) b.desired.x += 2f;
                if (a.head.z > edge) b.desired.z -= 2f; if (a.head.z < -edge) b.desired.z += 2f;
                b.desired.normalize();
                // sometimes run for a perch when the chaser is close
                if (danger > 0.6f && !b.hasGoal && rng.nextFloat() < 0.25f) pickPerch(b);
                if (b.hasGoal) {
                    t0.setSub(b.goal, a.head);
                    t0.y = 0;
                    if (t0.dot(b.desired) > 0f || t0.length() < 3f) {
                        b.desired.set(t0).normalize();
                        if (b.goal.y > a.head.y - 0.5f) b.climbing = t0.length() < 3.5f;
                    } else {
                        b.hasGoal = false;
                    }
                }
            } else {
                // wander
                if (!b.hasGoal || a.head.distanceXZ(b.goal) < 1.5f) {
                    if (rng.nextFloat() < 0.3f) pickPerch(b);
                    else {
                        b.goal.set(rng.range(-18f, 18f), 0f, rng.range(-18f, 18f));
                        b.hasGoal = true;
                    }
                }
                b.desired.setSub(b.goal, a.head);
                b.desired.y = 0;
                if (b.goal.y > a.head.y - 0.5f && b.desired.length() < 3.5f) b.climbing = true;
                b.desired.normalize().scale(0.75f);
            }
        }
        avoid(b);
    }

    private void pickPerch(Bot b) {
        if (arena.perches.isEmpty()) return;
        Vec3 p = arena.perches.get(rng.nextInt(arena.perches.size()));
        b.goal.set(p);
        b.hasGoal = true;
    }

    /** Steers around obstacles that are too tall to hop (unless climbing on purpose). */
    private void avoid(Bot b) {
        if (b.desired.lengthSq() < 1e-4f || b.climbing) return;
        float len = b.desired.length();
        t0.set(b.desired).scale(1f / len);
        probe.set(b.a.head.x, b.a.head.y - 0.4f, b.a.head.z);
        if (!world.raycast(probe, t0, 1.8f, Layers.STATIC, hit, null)) return;
        float[] tries = {0.6f, -0.6f, 1.2f, -1.2f, 1.9f, -1.9f};
        for (float ang : tries) {
            float c = (float) Math.cos(ang), s = (float) Math.sin(ang);
            t1.set(t0.x * c - t0.z * s, 0, t0.x * s + t0.z * c);
            if (!world.raycast(probe, t1, 1.8f, Layers.STATIC, hit, null)) {
                b.desired.set(t1).scale(len);
                return;
            }
        }
        b.climbing = true; // boxed in: climb over
    }

    private void physics(Bot b, float dt, boolean frozen) {
        Avatar a = b.a;
        b.hopTimer -= dt;
        b.wallTimer -= dt;
        float ground = world.groundHeight(a.head.x, a.head.z, 0.18f, a.head.y + 0.05f, Layers.STATIC);
        boolean near = ground > -999f && a.head.y - ground < 0.98f;
        if (near && a.vel.y <= 0.3f) {
            b.grounded = true;
            a.head.y += (ground + 0.95f - a.head.y) * MathUtil.damp(14f, dt);
            a.vel.y = 0f;
            float f = (float) Math.exp(-6f * dt);
            a.vel.x *= f; a.vel.z *= f;
            if (!frozen && b.hopTimer <= 0f && b.desired.lengthSq() > 0.01f) {
                float sp = (a.it ? 4.1f : 3.8f) * speedScale * Math.min(1f, b.desired.length());
                a.vel.x = b.desired.x / Math.max(0.01f, b.desired.length()) * sp;
                a.vel.z = b.desired.z / Math.max(0.01f, b.desired.length()) * sp;
                a.vel.y = b.climbing ? 4.6f : 2.2f + rng.range(0f, 0.8f);
                b.hopTimer = rng.range(0.32f, 0.5f);
                b.phase = 0f;
                b.grounded = false;
                tap(a.head);
            }
        } else {
            b.grounded = false;
            a.vel.y -= PhysicsWorld.GRAVITY * dt;
            // gentle air control
            if (!frozen && b.desired.lengthSq() > 0.01f) {
                a.vel.x += b.desired.x * 2f * dt;
                a.vel.z += b.desired.z * 2f * dt;
            }
        }
        // wall climbing: pushing against a wall with the arms launches upward
        if (!frozen && b.touchingWall && b.climbing && b.wallTimer <= 0f && a.vel.y < 2.5f) {
            a.vel.y = 4.4f;
            b.wallTimer = 0.32f;
            tap(a.head);
        }
        a.vel.clampLength(9f);
        a.body(body);
        int c = SphereMover.move(world, body, 0.28f, a.vel.x * dt, a.vel.y * dt, a.vel.z * dt, a.vel);
        a.head.set(body.x, body.y + 0.3f, body.z);
        b.touchingWall = (c & 2) != 0;
        if ((c & 1) != 0 && !b.grounded) tap(a.head);
        // face movement direction
        float hs = a.vel.lengthXZ();
        if (hs > 0.3f) {
            float want = (float) Math.atan2(-a.vel.x, -a.vel.z);
            a.yaw = MathUtil.wrapAngle(a.yaw + MathUtil.clamp(MathUtil.wrapAngle(want - a.yaw), -8f * dt, 8f * dt));
        }
        // unstick
        b.stuck = a.head.distanceXZ(b.lastPos) < 0.05f && b.desired.lengthSq() > 0.1f ? b.stuck + dt : 0f;
        b.lastPos.set(a.head);
        if (b.stuck > 1.5f) { b.hasGoal = false; b.climbing = true; b.stuck = 0f; }
        if (a.head.y < -10f) a.head.set(0f, 1f, 0f);
    }

    /** Procedural arm animation: hands plant on the ground when hopping and reach up when climbing. */
    private void animate(Bot b, float dt) {
        Avatar a = b.a;
        b.phase += dt * 6f;
        float fx = -(float) Math.sin(a.yaw), fz = -(float) Math.cos(a.yaw);
        float rx = (float) Math.cos(a.yaw), rz = -(float) Math.sin(a.yaw);
        a.shoulderL.set(a.head.x - rx * 0.2f, a.head.y - 0.32f, a.head.z - rz * 0.2f);
        a.shoulderR.set(a.head.x + rx * 0.2f, a.head.y - 0.32f, a.head.z + rz * 0.2f);
        float reach, down;
        if (b.touchingWall || b.climbing && !b.grounded) {
            reach = 0.6f; down = -0.6f - 0.3f * (float) Math.sin(b.phase * 2f); // reaching up
        } else if (b.grounded) {
            reach = 0.55f; down = 0.75f;
        } else {
            float k = MathUtil.clamp(b.phase / 3f, 0f, 1f);
            reach = 0.7f - 0.9f * k; down = 0.55f + 0.2f * k; // arms swing back while flying
        }
        float sw = 0.12f * (float) Math.sin(b.phase * 1.5f);
        a.handL.set(a.shoulderL.x + fx * (reach + sw) - rx * 0.15f, a.shoulderL.y - down, a.shoulderL.z + fz * (reach + sw) - rz * 0.15f);
        a.handR.set(a.shoulderR.x + fx * (reach - sw) + rx * 0.15f, a.shoulderR.y - down, a.shoulderR.z + fz * (reach - sw) + rz * 0.15f);
        float gy = a.head.y - 1.0f;
        if (b.grounded) {
            if (a.handL.y < gy) a.handL.y = gy;
            if (a.handR.y < gy) a.handR.y = gy;
        }
    }
}
