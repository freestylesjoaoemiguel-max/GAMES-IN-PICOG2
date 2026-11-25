package com.joaolimz.pegapega.game.player;

import com.joaolimz.pegapega.game.math.MathUtil;
import com.joaolimz.pegapega.game.math.Quat;
import com.joaolimz.pegapega.game.math.Vec3;
import com.joaolimz.pegapega.game.physics.Layers;
import com.joaolimz.pegapega.game.physics.PhysicsWorld;
import com.joaolimz.pegapega.game.physics.RayHit;
import com.joaolimz.pegapega.game.physics.SphereCast;
import com.joaolimz.pegapega.game.physics.SphereMover;

/**
 * Arm locomotion - a Java port of the algorithm of Another Axiom's open source "GorillaLocomotion"
 * (MIT License, see THIRD_PARTY_NOTICES.md), adapted to the Pico G2:
 *  - each hand sweeps from its last position toward the tracked position; touching a surface makes it
 *    stick, and the body moves by (stuck point - tracked point): push the floor to move, push down to jump,
 *    pull on walls to climb;
 *  - with both hands touching the movements are averaged, otherwise added;
 *  - while touching, the average body velocity of the last frames x jumpMultiplier (capped by
 *    maxJumpSpeed) becomes the velocity you keep when you let go;
 *  - a hand unsticks when it gets more than unStickDistance away from its stuck point;
 *  - gravity always acts on the body (a vertical capsule standing on its "feet").
 * Pico G2 adaptation: the tracked hand position comes from a 3DoF arm (shoulder + controller direction
 * x arm length) plus real arm motion from the accelerometer (ImuHand). Optional climb assist lets a hand
 * slide up a wall when you raise the arm, because a fixed-length 3DoF arm cannot pull away from a wall.
 */
public final class ArmRig {
    public static final class Hand {
        public final boolean right;
        /** Where the hand is drawn ("follower" = last valid position). */
        public final Vec3 pos = new Vec3();
        /** Tracked (desired) position. */
        public final Vec3 target = new Vec3();
        public final Vec3 lastTarget = new Vec3();
        public final Vec3 shoulder = new Vec3();
        public final Vec3 normal = new Vec3(0, 1, 0);
        public boolean anchored;
        public boolean active = true;
        public boolean tapped;
        public float tapStrength;
        public int tapMaterial;
        final Vec3 firstIteration = new Vec3();
        boolean colliding;
        Hand(boolean right) { this.right = right; }
        public float side() { return right ? 1f : -1f; }
    }

    // ---- tuning (same defaults as the reference implementation)
    public float maxArmLength = 1.5f;
    public float unStickDistance = 1f;
    public float velocityLimit = 0.4f;
    public float maxJumpSpeed = 6.5f;
    public float jumpMultiplier = 1.1f;
    public float handRadius = 0.05f;
    public float precision = 0.995f;
    public int velocityHistorySize = 6;
    /** 3DoF adaptation: arm reach from the shoulder along the controller direction. */
    public float armLength = 1.0f;
    public float standHeight = 1.0f;
    public boolean climbAssist = true;

    public final Vec3 head = new Vec3();
    public final Vec3 vel = new Vec3();
    public final Quat headRot = new Quat();
    public final Hand[] hands = {new Hand(false), new Hand(true)};
    public float headRadius = 0.18f;
    public boolean grounded;
    public boolean airborne;

    private final Vec3[] velocityHistory;
    private int velocityIndex;
    private final Vec3 velocityAverage = new Vec3();
    private final Vec3 lastPosition = new Vec3();

    private final Vec3 t0 = new Vec3(), t1 = new Vec3(), dir = new Vec3(), move = new Vec3(), hitP = new Vec3(), hitN = new Vec3();
    private final Vec3 imuL = new Vec3(), imuR = new Vec3();
    private final Quat yawQ = new Quat();
    private final RayHit hit = new RayHit();

    public ArmRig() {
        velocityHistory = new Vec3[16];
        for (int i = 0; i < velocityHistory.length; i++) velocityHistory[i] = new Vec3();
    }

    public void spawn(Vec3 eye) {
        head.set(eye);
        vel.set(0, 0, 0);
        lastPosition.set(eye);
        velocityAverage.set(0, 0, 0);
        for (Vec3 v : velocityHistory) v.set(0, 0, 0);
        for (Hand h : hands) { h.anchored = false; h.colliding = false; h.pos.set(eye); h.target.set(eye); h.lastTarget.set(eye); }
        fresh = true;
    }

    /** First frame after a spawn/teleport: hands appear at their targets without launching the body. */
    private boolean fresh = true;

    public Vec3 bodyCenter(Vec3 out) { return out.set(head.x, head.y - 0.3f, head.z); }

    /** Sets the accelerometer offsets added to the tracked hand positions (world space). */
    public void setImuOffsets(Vec3 left, Vec3 right) { imuL.set(left); imuR.set(right); }

    private void computeTargets(Quat ctrl, boolean bothArms, int activeArm) {
        yawQ.setYaw(headRot.yaw());
        ctrl.forward(dir);
        for (int i = 0; i < 2; i++) {
            Hand h = hands[i];
            h.active = bothArms || i == activeArm;
            yawQ.rotate(h.side() * 0.2f, -0.32f, 0.04f, t0);
            h.shoulder.set(head).add(t0);
            if (h.active) {
                h.target.set(h.shoulder).addScaled(dir, armLength).add(i == 0 ? imuL : imuR);
            } else {
                yawQ.rotate(h.side() * 0.08f, -0.55f, -0.3f, t0);
                h.target.set(h.shoulder).add(t0);
            }
            // clamp to maxArmLength from the head (as the reference implementation)
            t0.setSub(h.target, head);
            float d = t0.length();
            if (d > maxArmLength) h.target.set(head).addScaled(t0, maxArmLength / d);
        }
    }

    /** Sweeps the hand sphere from 'from' by 'delta'. Returns true on contact; end = resting position. */
    private boolean sweep(PhysicsWorld world, Vec3 from, Vec3 delta, Vec3 end, Hand h) {
        float len = delta.length();
        if (len < 1e-6f) {
            // not moving: still touching if a surface is right under the sphere
            t1.set(0, -1, 0);
            float t = SphereCast.cast(world, from, t1, handRadius * 0.5f, handRadius * precision, hitP, hitN);
            if (t >= 0f) { end.set(from); h.normal.set(hitN); return true; }
            return false;
        }
        t1.set(delta).scale(1f / len);
        float t = SphereCast.cast(world, from, t1, len + handRadius * (1f - precision), handRadius * precision, hitP, hitN);
        if (t < 0f) return false;
        end.set(hitP).addScaled(hitN, handRadius);
        h.normal.set(hitN);
        return true;
    }

    public void update(PhysicsWorld world, Quat ctrl, boolean bothArms, int activeArm, float walk, float dt) {
        if (dt <= 0f) return;
        for (Hand h : hands) h.tapped = false;

        // ---- rigid body step: gravity + collisions of the standing capsule
        vel.y -= PhysicsWorld.GRAVITY * dt;
        if (walk != 0f && grounded) {
            float yaw = headRot.yaw();
            vel.x = -(float) Math.sin(yaw) * walk * 2.2f;
            vel.z = -(float) Math.cos(yaw) * walk * 2.2f;
        }
        moveBody(world, vel.x * dt, vel.y * dt, vel.z * dt, true);

        computeTargets(ctrl, bothArms, activeArm);

        // ---- first iteration: where would each hand go, and how must the body move to keep stuck hands?
        boolean anyColliding = false;
        for (Hand h : hands) {
            h.colliding = false;
            h.firstIteration.set(0, 0, 0);
            if (!h.active) { h.anchored = false; continue; }
            if (climbAssist && h.anchored && Math.abs(h.normal.y) < 0.6f) {
                float dy = h.target.y - h.lastTarget.y;
                if (dy > 0f) h.pos.y += dy; // raising the arm slides the hand up the wall
            }
            t0.setSub(h.target, h.pos).add(0f, -2f * PhysicsWorld.GRAVITY * dt * dt, 0f);
            wallGrip(h, t0);
            if (sweep(world, h.pos, t0, move, h)) {
                if (h.anchored) h.firstIteration.setSub(h.pos, h.target);
                else h.firstIteration.setSub(move, h.target);
                vel.set(0, 0, 0);
                h.colliding = true;
                anyColliding = true;
            }
        }
        boolean bothTouching = (hands[0].colliding || hands[0].anchored) && (hands[1].colliding || hands[1].anchored);
        move.set(hands[0].firstIteration).add(hands[1].firstIteration);
        if (bothTouching) move.scale(0.5f);

        // ---- move the body (the head collision check of the reference is done by the capsule move)
        if (move.lengthSq() > 0f) {
            moveBody(world, move.x, move.y, move.z, false);
            computeTargets(ctrl, bothArms, activeArm);
        }

        // ---- final hand positions
        for (Hand h : hands) {
            if (!h.active) { h.pos.set(h.target); h.anchored = false; continue; }
            boolean wasAnchored = h.anchored;
            t0.setSub(h.target, h.pos);
            wallGrip(h, t0);
            if (sweep(world, h.pos, t0, move, h)) {
                h.pos.set(move);
                h.colliding = true;
            } else {
                h.pos.set(h.target);
            }
            if (h.colliding && !wasAnchored) {
                h.tapped = true;
                float sp = h.target.distance(h.lastTarget) / dt;
                h.tapStrength = MathUtil.clamp(sp / 4f + vel.length() / 6f, 0.15f, 1f);
                h.tapMaterial = materialAt(world, h.pos, h.normal);
            }
        }

        // ---- velocity history and the jump
        storeVelocity(dt);
        anyColliding = hands[0].colliding || hands[1].colliding;
        if (anyColliding) {
            float m = velocityAverage.length();
            if (m > velocityLimit) {
                if (m * jumpMultiplier > maxJumpSpeed) vel.set(velocityAverage).scale(maxJumpSpeed / m);
                else vel.set(velocityAverage).scale(jumpMultiplier);
                airborne = vel.y > 1f;
            }
        }

        if (fresh) {
            fresh = false;
            vel.set(0, 0, 0);
            velocityAverage.set(0, 0, 0);
            for (Vec3 v : velocityHistory) v.set(0, 0, 0);
            lastPosition.set(head);
        }

        // ---- unstick hands that were pulled far from their stuck point
        for (Hand h : hands) {
            if (h.colliding && h.target.distance(h.pos) > unStickDistance) {
                t0.setSub(h.target, head);
                float d = t0.length();
                boolean blocked = d > 1e-4f && world.raycast(head, t0.scale(1f / d), Math.max(0f, d - handRadius), Layers.STATIC, hit, null);
                if (!blocked) { h.pos.set(h.target); h.colliding = false; }
            }
            h.anchored = h.colliding;
            h.lastTarget.set(h.target);
        }
    }

    /**
     * 3DoF adaptation: a hand stuck on a wall keeps "pressing" against it. A real player pushes the hand into
     * the wall while pulling down; a fixed-length 3DoF arm drifts slightly away from the wall when it pitches,
     * so small outward motions (up to 30 cm) are ignored instead of releasing the hand.
     */
    private void wallGrip(Hand h, Vec3 delta) {
        if (!climbAssist || !h.anchored || Math.abs(h.normal.y) >= 0.6f) return;
        float out = delta.dot(h.normal);
        float away = h.target.distance(h.pos);
        if (out > -0.001f && away < 0.3f + Math.abs(out)) delta.addScaled(h.normal, -out - 0.002f);
    }

    /** Vertical capsule (head sphere, chest sphere, feet sphere) moved with sliding collisions. */
    private void moveBody(PhysicsWorld world, float dx, float dy, float dz, boolean physicsStep) {
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        int steps = Math.min(24, Math.max(1, (int) Math.ceil(len / 0.08f)));
        int contact = 0;
        float feetDrop = standHeight - 0.2f;
        for (int s = 0; s < steps; s++) {
            head.x += dx / steps; head.y += dy / steps; head.z += dz / steps;
            for (int k = 0; k < 3; k++) {
                float drop = k == 0 ? 0f : k == 1 ? feetDrop * 0.5f : feetDrop;
                float r = k == 0 ? headRadius : 0.2f;
                t0.set(head.x, head.y - drop, head.z);
                float bx = t0.x, by = t0.y, bz = t0.z;
                int c = SphereMover.resolve(world, t0, r, physicsStep ? vel : null);
                if (k == 2) contact |= c;
                head.x += t0.x - bx; head.y += t0.y - by; head.z += t0.z - bz;
            }
        }
        if (physicsStep) {
            grounded = (contact & 1) != 0;
            if (grounded) {
                airborne = false;
                if (!anyHandTouching()) {
                    float f = 0.82f; // ground friction while standing
                    vel.x *= f; vel.z *= f;
                }
            }
        }
    }

    private boolean anyHandTouching() { return hands[0].anchored || hands[1].anchored; }

    private void storeVelocity(float dt) {
        int size = Math.max(1, Math.min(velocityHistory.length, velocityHistorySize));
        velocityIndex = (velocityIndex + 1) % size;
        Vec3 oldest = velocityHistory[velocityIndex];
        t0.setSub(head, lastPosition).scale(1f / dt);
        velocityAverage.addScaled(t0, 1f / size).addScaled(oldest, -1f / size);
        oldest.set(t0);
        lastPosition.set(head);
    }

    private int materialAt(PhysicsWorld world, Vec3 p, Vec3 normal) {
        t0.set(-normal.x, -normal.y, -normal.z);
        t1.set(p);
        if (world.raycast(t1, t0.normalize(), 0.3f, Layers.STATIC, hit, null) && hit.collider != null) return hit.collider.material;
        return 0;
    }

    public boolean anyAnchored() { return hands[0].anchored || hands[1].anchored; }
}
