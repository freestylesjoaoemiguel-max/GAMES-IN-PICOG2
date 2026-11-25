package com.joaolimz.operationzero.game.player;

import com.joaolimz.operationzero.game.math.MathUtil;
import com.joaolimz.operationzero.game.math.Quat;
import com.joaolimz.operationzero.game.math.Vec3;
import com.joaolimz.operationzero.game.physics.CharacterMover;
import com.joaolimz.operationzero.game.physics.Layers;
import com.joaolimz.operationzero.game.physics.PhysicsWorld;
import com.joaolimz.operationzero.game.weapons.Weapon;

/**
 * Player body: head (3DoF IMU + neck model), torso, two arms driven by an arm model, hands,
 * locomotion and health. The Pico G2 has a single 3DoF controller (and no positional tracking), so the
 * controller drives one hand at a time; the other hand parks in a reachable pose or supports the
 * weapon's foregrip.
 */
public final class Player {
    public static final int MAX_HEALTH = 100;

    public final Vec3 feet = new Vec3();
    public float yaw;
    public final Quat yawQ = new Quat();
    public final Quat head = new Quat();
    public final Vec3 eye = new Vec3();
    public float torsoYaw;
    public final Quat torso = new Quat();
    public final Hand[] hands = {new Hand(false), new Hand(true)};
    public int active = 1;
    public final Inventory inventory = new Inventory();
    public float health = MAX_HEALTH;
    public boolean alive = true;
    public float hurtTimer;
    public final Vec3 hurtDir = new Vec3();
    public float height = 1.7f;
    public final float radius = 0.28f;
    public boolean grounded = true;
    private final float[] vy = new float[1];
    public float moveSpeedNow;
    public float turnSpeedNow;
    public int kills;
    /** Recoil camera offset (only when enabled in comfort settings). */
    public float cameraPunch;

    private final Vec3 t0 = new Vec3(), t1 = new Vec3();
    private final Quat q0 = new Quat(), q1 = new Quat();

    public Hand activeHand() { return hands[active]; }

    public Hand otherHand() { return hands[1 - active]; }

    public Hand dominant(boolean rightHanded) { return hands[rightHanded ? 1 : 0]; }

    public void spawn(Vec3 p, float yawRad) {
        feet.set(p);
        yaw = yawRad;
        torsoYaw = yawRad;
        vy[0] = 0f;
        health = MAX_HEALTH;
        alive = true;
        hurtTimer = 0f;
        for (int i = 0; i < 2; i++) {
            Hand h = hands[i];
            h.held = null; h.door = null; h.lever = null; h.pulling = null; h.aimInit = false; h.resetTracking();
        }
    }

    /** Applies head orientation from the IMU and computes eye position with a neck model. */
    public void updateHead(Quat imu, float cameraPunchRad) {
        yawQ.setYaw(yaw);
        head.setMul(yawQ, imu);
        if (cameraPunchRad != 0f) {
            q0.setAxisAngle(1, 0, 0, cameraPunchRad);
            head.mul(q0);
        }
        // neck pivot then eyes forward/up
        float neckY = feet.y + height - 0.12f;
        head.rotate(0f, 0.12f, -0.08f, t0);
        eye.set(feet.x + t0.x, neckY + t0.y, feet.z + t0.z);
        // torso follows head yaw with a dead zone
        float hy = head.yaw();
        float diff = MathUtil.wrapAngle(hy - torsoYaw);
        if (Math.abs(diff) > 0.6f) torsoYaw = MathUtil.wrapAngle(torsoYaw + diff - Math.signum(diff) * 0.6f);
        torso.setYaw(torsoYaw);
    }

    /** Neck base position (torso frame origin). */
    public Vec3 neck(Vec3 out) { return out.set(feet.x, feet.y + height - 0.12f, feet.z); }

    /**
     * 3DoF arm model: shoulder fixed to the torso, elbow follows a fraction of the hand rotation, the
     * forearm points along the controller.
     */
    public void armModel(Hand h, Quat handRot, Vec3 outHand) {
        float s = h.side();
        neck(t0);
        torso.rotate(0.19f * s, -0.15f, 0.04f, t1);
        h.shoulder.set(t0).add(t1);
        q1.set(torso).slerp(handRot, 0.42f);
        q1.rotate(0f, -0.26f, -0.07f, t1);
        h.elbow.set(h.shoulder).add(t1);
        handRot.rotate(0f, 0f, -0.29f, t1);
        outHand.set(h.elbow).add(t1);
    }

    /** Pose for the controlled hand. */
    public void driveHand(Hand h, boolean hasController, Quat controller, float dt, float sensitivity) {
        if (hasController) {
            h.rot.setMul(yawQ, controller);
        } else {
            // aim from the head: hand follows gaze with slight lag and a small downward offset
            q0.setAxisAngle(1, 0, 0, -0.10f);
            q1.setMul(head, q0);
            if (!h.aimInit) { h.aim.set(q1); h.aimInit = true; }
            h.aim.slerp(q1, MathUtil.damp(14f * sensitivity, dt));
            h.rot.set(h.aim);
        }
        if (h.punch > 0f) {
            q0.setAxisAngle(1, 0, 0, h.punch);
            h.rot.mul(q0);
            h.punch -= h.punch * MathUtil.damp(14f, dt);
        }
        armModel(h, h.rot, h.pos);
    }

    /**
     * Pose for the hand that is not driven by the controller. Holding a weapon: the weapon is presented in
     * front of the chest so the controlled hand can reach its magazine well. Otherwise it supports the
     * other hand's two-handed weapon or rests at the side.
     */
    public void parkHand(Hand h, Hand controlledHand, float dt) {
        h.supporting = false;
        if (h.held != null) {
            // evaluate where the controlled hand is when aiming toward the body center, slightly down
            q0.setEuler(torsoYaw + controlledHand.side() * 0.35f, -0.5f, 0f);
            armModel(controlledHand, q0, t0);
            // present the held item so that its insertion point sits at t0
            q1.setEuler(torsoYaw + h.side() * 0.12f, -0.12f, h.side() * -0.35f);
            float my = -0.1f, mz = 0f;
            if (h.held instanceof Weapon) {
                Weapon w = (Weapon) h.held;
                my = w.type.magY; mz = w.type.magZ;
            }
            q1.rotate(0f, my, mz, t1);
            h.pos.set(t0).sub(t1);
            torso.rotate(0f, 0f, -0.04f, t1);
            h.pos.add(t1);
            h.rot.set(q1);
            neck(t0);
            torso.rotate(0.19f * h.side(), -0.15f, 0.04f, t1);
            h.shoulder.set(t0).add(t1);
            h.elbow.set(h.shoulder).lerp(h.pos, 0.5f);
            h.elbow.y -= 0.12f;
            return;
        }
        Hand other = controlledHand;
        if (other.held instanceof Weapon && ((Weapon) other.held).type.twoHanded) {
            Weapon w = (Weapon) other.held;
            other.rot.rotate(0f, w.type.foreY, w.type.foreZ, t1);
            h.pos.set(other.pos).add(t1);
            h.rot.set(other.rot);
            neck(t0);
            torso.rotate(0.19f * h.side(), -0.15f, 0.04f, t1);
            h.shoulder.set(t0).add(t1);
            h.elbow.set(h.shoulder).lerp(h.pos, 0.5f);
            h.elbow.y -= 0.15f;
            h.supporting = true;
            return;
        }
        // rest at the side, slightly forward
        q0.setEuler(torsoYaw + h.side() * -0.15f, -1.2f, 0f);
        armModel(h, q0, h.pos);
        h.rot.set(q0);
    }

    /** Smooth locomotion relative to head yaw. Returns horizontal distance moved. */
    /** Locomotion heading override (radians); NaN = walk where the head looks. */
    public float moveYaw = Float.NaN;

    public float move(PhysicsWorld world, float forward, float strafe, float speed, float dt) {
        float hy = Float.isNaN(moveYaw) ? head.yaw() : moveYaw;
        float fx = -(float) Math.sin(hy), fz = -(float) Math.cos(hy);
        float rx = -fz, rz = fx;
        float len = (float) Math.sqrt(forward * forward + strafe * strafe);
        if (len > 1f) { forward /= len; strafe /= len; }
        float dx = (fx * forward + rx * strafe) * speed * dt;
        float dz = (fz * forward + rz * strafe) * speed * dt;
        float bx = feet.x, bz = feet.z;
        grounded = CharacterMover.move(world, feet, vy, dx, dz, radius, height, dt, Layers.PLAYER_COLLIDE, 0.35f);
        float moved = (float) Math.sqrt((feet.x - bx) * (feet.x - bx) + (feet.z - bz) * (feet.z - bz));
        moveSpeedNow = dt > 0 ? moved / dt : 0f;
        return moved;
    }

    public void teleport(Vec3 target) {
        feet.set(target);
        vy[0] = 0f;
        for (int i = 0; i < 2; i++) hands[i].resetTracking();
    }

    public void damage(float amount, Vec3 from) {
        if (!alive) return;
        health -= amount;
        hurtTimer = 0.6f;
        if (from != null) hurtDir.setSub(from, eye).normalize();
        if (health <= 0f) { health = 0f; alive = false; }
    }

    public void heal(float amount) { health = Math.min(MAX_HEALTH, health + amount); }
}
