package com.joaolimz.pegapega.game.player;

import com.joaolimz.pegapega.game.math.MathUtil;
import com.joaolimz.pegapega.game.math.Quat;
import com.joaolimz.pegapega.game.math.Vec3;
import com.joaolimz.pegapega.game.physics.Layers;
import com.joaolimz.pegapega.game.physics.PhysicsWorld;
import com.joaolimz.pegapega.game.physics.RayHit;
import com.joaolimz.pegapega.game.physics.SphereMover;

/**
 * Arm locomotion: there is no walking. Long arms reach from the shoulders along the controller direction;
 * when a hand meets a surface it sticks there and, as the arm keeps moving, the body is pushed the opposite
 * way (push the floor backwards to go forward, push down to jump, pull down on a wall to climb). Letting go
 * keeps the body's recent velocity (fling). Adapted to the Pico G2: one 3DoF controller drives both long
 * arms (or just the active one) through a shoulder pivot, which is enough to reach the ground and walls.
 */
public final class ArmRig {
    public static final class Hand {
        public final boolean right;
        public final Vec3 pos = new Vec3();
        public final Vec3 target = new Vec3();
        /** Target at the end of the previous frame (to know how the controller moved). */
        public final Vec3 lastTarget = new Vec3();
        public final Vec3 shoulder = new Vec3();
        public final Vec3 anchor = new Vec3();
        public final Vec3 normal = new Vec3();
        public boolean anchored;
        public boolean active = true;
        /** Set for one frame when the hand touches a surface (for tap sounds / haptics). */
        public boolean tapped;
        public float tapStrength;
        public int tapMaterial;
        Hand(boolean right) { this.right = right; }
        public float side() { return right ? 1f : -1f; }
    }

    public final Vec3 head = new Vec3();
    public final Vec3 vel = new Vec3();
    public final Quat headRot = new Quat();
    public final Hand[] hands = {new Hand(false), new Hand(true)};
    public float bodyRadius = 0.27f;
    public float bodyDrop = 0.3f;
    public float handRadius = 0.06f;
    public float armLength = 1.0f;
    public float standHeight = 1.0f;
    public float flingBoost = 1.15f;
    public float maxSpeed = 7.5f;
    public boolean grounded;
    public boolean airborne;
    /** Velocity samples while pushing (for the fling). */
    private final float[] hx = new float[6], hy = new float[6], hz = new float[6];
    private int hi, samples;

    private final Vec3 t0 = new Vec3(), t1 = new Vec3(), dir = new Vec3(), body = new Vec3(), move = new Vec3();
    private final Quat yawQ = new Quat();
    private final RayHit hit = new RayHit();

    public void spawn(Vec3 eye) {
        head.set(eye);
        vel.set(0, 0, 0);
        for (Hand h : hands) { h.anchored = false; h.pos.set(eye); h.target.set(eye); h.lastTarget.set(eye); }
        samples = 0;
    }

    /** Center of the body sphere. */
    public Vec3 bodyCenter(Vec3 out) { return out.set(head.x, head.y - bodyDrop, head.z); }

    private void computeTargets(Quat ctrl, boolean bothArms, int activeArm) {
        float yaw = headRot.yaw();
        yawQ.setYaw(yaw);
        ctrl.forward(dir);
        for (int i = 0; i < 2; i++) {
            Hand h = hands[i];
            h.active = bothArms || i == activeArm;
            yawQ.rotate(h.side() * 0.2f, -0.32f, 0.04f, t0);
            h.shoulder.set(head).add(t0);
            if (h.active) {
                h.target.set(h.shoulder).addScaled(dir, armLength);
            } else {
                // resting arm hangs a bit forward
                yawQ.rotate(h.side() * 0.08f, -0.55f, -0.3f, t0);
                h.target.set(h.shoulder).add(t0);
            }
        }
    }

    /**
     * @param ctrl world rotation of the controller (aim)
     * @param walk optional touchpad walking input (forward, -1..1) relative to head yaw (0 when disabled)
     */
    public void update(PhysicsWorld world, Quat ctrl, boolean bothArms, int activeArm, float walk, float dt) {
        if (dt <= 0f) return;
        for (Hand h : hands) h.tapped = false;
        computeTargets(ctrl, bothArms, activeArm);

        // 1) anchored hands: keep them on their anchor, body moves by the correction
        move.set(0, 0, 0);
        int anchoredCount = 0;
        for (Hand h : hands) {
            if (!h.anchored) continue;
            t0.setSub(h.target, h.anchor);
            boolean pulledOut = t0.dot(h.normal) > 0.015f;
            boolean overstretch = t0.length() > armLength * 0.75f;
            if (!h.active || pulledOut || overstretch) {
                h.anchored = false;
                continue;
            }
            // walls: raising the arm slides the hand up the wall (re-grab higher) instead of pulling the
            // body down, so climbing is "pull down, reach up, pull down" even with fixed-length 3DoF arms
            if (Math.abs(h.normal.y) < 0.6f) {
                float dy = h.target.y - h.lastTarget.y;
                if (dy > 0f) h.anchor.y += dy;
            }
            move.add(h.anchor.x - h.target.x, h.anchor.y - h.target.y, h.anchor.z - h.target.z);
            anchoredCount++;
        }
        if (anchoredCount > 0) {
            move.scale(1f / anchoredCount);
            move.clampLength(maxSpeed * dt * 1.5f);
            bodyCenter(body);
            SphereMover.move(world, body, bodyRadius, move.x, move.y, move.z, null);
            float mx = body.x - head.x, my = body.y + bodyDrop - head.y, mz = body.z - head.z;
            head.set(body.x, body.y + bodyDrop, body.z);
            // velocity history for the fling
            hx[hi] = mx / dt; hy[hi] = my / dt; hz[hi] = mz / dt;
            hi = (hi + 1) % hx.length;
            samples = Math.min(samples + 1, hx.length);
            vel.set(0, 0, 0);
            airborne = false;
            computeTargets(ctrl, bothArms, activeArm);
            boolean any = false;
            for (Hand h : hands) if (h.anchored) { h.pos.set(h.anchor); any = true; }
            if (!any) release();
        } else {
            // 2) free body: gravity, standing support, optional walking
            if (samples > 0) release();
            freeMotion(world, walk, dt);
            computeTargets(ctrl, bothArms, activeArm);
        }

        // 3) free hands: move toward the target; touching a surface anchors them
        for (Hand h : hands) {
            if (h.anchored) continue;
            t0.setSub(h.target, h.pos);
            float d = t0.length();
            if (d > 1e-5f && h.active) {
                t0.scale(1f / d);
                if (world.raycast(h.pos, t0, d + handRadius, Layers.STATIC, hit, null)) {
                    h.anchored = true;
                    h.normal.set(hit.normal);
                    h.anchor.set(hit.point).addScaled(hit.normal, handRadius);
                    h.pos.set(h.anchor);
                    h.tapped = true;
                    h.tapStrength = MathUtil.clamp(d / dt / 4f + vel.length() / 6f, 0.15f, 1f);
                    h.tapMaterial = hit.collider != null ? hit.collider.material : 0;
                    continue;
                }
            }
            // keep the hand out of geometry it may already be in (e.g. after a teleport)
            h.pos.set(h.target);
        }
        for (Hand h : hands) h.lastTarget.set(h.target);
    }

    private void release() {
        if (samples > 0) {
            float sx = 0, sy = 0, sz = 0;
            int n = Math.min(samples, 4);
            for (int k = 1; k <= n; k++) {
                int i = (hi - k + hx.length) % hx.length;
                sx += hx[i]; sy += hy[i]; sz += hz[i];
            }
            vel.set(sx / n, sy / n, sz / n).scale(flingBoost).clampLength(maxSpeed);
            if (vel.y > 0.5f || vel.lengthXZ() > 1.5f) airborne = true;
        }
        samples = 0;
    }

    private void freeMotion(PhysicsWorld world, float walk, float dt) {
        bodyCenter(body);
        float ground = world.groundHeight(head.x, head.z, 0.18f, head.y + 0.05f, Layers.STATIC);
        float heightAbove = head.y - ground;
        boolean nearGround = ground > -999f && heightAbove < standHeight + 0.06f;
        if (nearGround && vel.y <= 0.5f && !(airborne && vel.y > 0f)) {
            // legs: a soft spring keeps the eyes at standing height; strong ground friction
            grounded = true;
            airborne = false;
            float want = ground + standHeight;
            head.y += (want - head.y) * MathUtil.damp(14f, dt);
            vel.y = 0f;
            float f = (float) Math.exp(-7f * dt);
            vel.x *= f; vel.z *= f;
            if (walk != 0f) {
                float yaw = headRot.yaw();
                vel.x = -(float) Math.sin(yaw) * walk * 2.2f;
                vel.z = -(float) Math.cos(yaw) * walk * 2.2f;
            }
        } else {
            grounded = false;
            vel.y -= PhysicsWorld.GRAVITY * dt;
            float drag = (float) Math.exp(-0.15f * dt);
            vel.x *= drag; vel.z *= drag;
        }
        bodyCenter(body);
        int c = SphereMover.move(world, body, bodyRadius, vel.x * dt, vel.y * dt, vel.z * dt, vel);
        head.set(body.x, body.y + bodyDrop, body.z);
        if ((c & 1) != 0) {
            airborne = false;
            float f = (float) Math.exp(-10f * dt);
            vel.x *= f; vel.z *= f;
        }
    }

    public boolean anyAnchored() { return hands[0].anchored || hands[1].anchored; }
}
