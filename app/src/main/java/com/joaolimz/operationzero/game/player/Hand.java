package com.joaolimz.operationzero.game.player;

import com.joaolimz.operationzero.game.math.Quat;
import com.joaolimz.operationzero.game.math.Vec3;
import com.joaolimz.operationzero.game.mesh.Models;
import com.joaolimz.operationzero.game.world.Door;
import com.joaolimz.operationzero.game.world.Interactive;
import com.joaolimz.operationzero.game.world.Prop;

/** Virtual hand: pose, grab state, velocity history used for throwing. */
public final class Hand {
    public final boolean right;
    public final Vec3 pos = new Vec3();
    public final Quat rot = new Quat();
    public final Vec3 velocity = new Vec3();
    public final Vec3 shoulder = new Vec3();
    public final Vec3 elbow = new Vec3();
    private final Vec3 prev = new Vec3();
    private boolean hasPrev;
    private final float[] hx = new float[6], hy = new float[6], hz = new float[6];
    private int hi;

    /** Driven by the controller this frame (otherwise parked / supporting). */
    public boolean controlled;
    public Prop held;
    public Door door;
    public Interactive lever;
    public int pose = Models.POSE_OPEN;
    /** Prop being pulled toward the hand (distance grab). */
    public Prop pulling;
    public float pullTime;
    /** Distance-grab / pointing candidate highlighted this frame. */
    public Prop pointProp;
    public Interactive pointInteractive;
    public Door pointDoor;
    /** Aim punch (radians) from recoil, recovers quickly. */
    public float punch;
    /** Supporting a two-handed weapon held by the other hand. */
    public boolean supporting;
    /** Smoothed aim orientation when aiming from the head (no controller pose). */
    public final Quat aim = new Quat();
    public boolean aimInit;

    public Hand(boolean right) { this.right = right; }

    public float side() { return right ? 1f : -1f; }

    public boolean isEmpty() { return held == null && door == null && lever == null && pulling == null; }

    /** Tracks velocity after the pose was updated for this frame. */
    public void trackVelocity(float dt) {
        if (dt <= 0f) return;
        if (!hasPrev) { prev.set(pos); hasPrev = true; }
        float vx = (pos.x - prev.x) / dt, vy = (pos.y - prev.y) / dt, vz = (pos.z - prev.z) / dt;
        hx[hi] = vx; hy[hi] = vy; hz[hi] = vz;
        hi = (hi + 1) % hx.length;
        float sx = 0, sy = 0, sz = 0;
        for (int i = 0; i < hx.length; i++) { sx += hx[i]; sy += hy[i]; sz += hz[i]; }
        velocity.set(sx / hx.length, sy / hx.length, sz / hx.length);
        prev.set(pos);
    }

    /** Peak recent velocity (better feeling throws). */
    public Vec3 throwVelocity(Vec3 out) {
        int best = 0;
        float bs = -1;
        for (int i = 0; i < hx.length; i++) {
            float s = hx[i] * hx[i] + hy[i] * hy[i] + hz[i] * hz[i];
            if (s > bs) { bs = s; best = i; }
        }
        out.set((hx[best] + velocity.x) * 0.5f, (hy[best] + velocity.y) * 0.5f, (hz[best] + velocity.z) * 0.5f);
        return out;
    }

    public void resetTracking() {
        hasPrev = false;
        for (int i = 0; i < hx.length; i++) { hx[i] = 0; hy[i] = 0; hz[i] = 0; }
        velocity.set(0, 0, 0);
    }
}
