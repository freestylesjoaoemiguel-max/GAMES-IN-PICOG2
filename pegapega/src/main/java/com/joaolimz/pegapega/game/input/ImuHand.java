package com.joaolimz.pegapega.game.input;

import com.joaolimz.pegapega.game.math.Quat;
import com.joaolimz.pegapega.game.math.Vec3;

/**
 * Uses the controller's accelerometer to add short, real arm movements (pushes, pulls, swings) to the
 * 3DoF arm. The acceleration (controller axes) is rotated to the world with the controller orientation,
 * gravity is removed, and the result is integrated with strong leaks so it never drifts: quick motions
 * move the hand, holding still brings it back to the arm-model position.
 */
public final class ImuHand {
    /** Signed permutation from the vendor accelerometer axes to the game controller axes (row major). */
    public final int[] pa = {1, 0, 0, 0, 1, 0, 0, 0, -1};
    /** True when the sensor reports gravity (specific force); false if it already reports linear acceleration. */
    public boolean gravityIncluded = true;
    /** Magnitude of gravity in sensor units (9.81 for m/s^2, ~1 for g). */
    public float gravity = 9.81f;
    public float gain = 1.4f;
    public float maxOffset = 0.45f;

    public final Vec3 offset = new Vec3();
    public final Vec3 velocity = new Vec3();
    /** Linear world acceleration of the last frame (m/s^2), for diagnostics. */
    public final Vec3 linear = new Vec3();
    private final Vec3 a = new Vec3();
    private boolean autoGravity = true;

    public void reset() { offset.set(0, 0, 0); velocity.set(0, 0, 0); }

    /**
     * @param ax raw accelerometer sample (vendor axes)
     * @param rot controller rotation in the game world
     * @param gyroMag angular speed magnitude (rad/s) - used to learn gravity while the controller is still
     */
    public void update(float ax, float ay, float az, Quat rot, float gyroMag, float dt) {
        if (dt <= 0f) return;
        float mag = (float) Math.sqrt(ax * ax + ay * ay + az * az);
        if (mag < 1e-6f) { decay(dt); return; }
        // learn the gravity magnitude (units) while nearly still
        if (autoGravity && gyroMag < 0.3f && gravityIncluded) gravity += (mag - gravity) * Math.min(1f, dt * 0.5f);
        // vendor axes -> controller axes -> world
        a.set(pa[0] * ax + pa[1] * ay + pa[2] * az, pa[3] * ax + pa[4] * ay + pa[5] * az, pa[6] * ax + pa[7] * ay + pa[8] * az);
        rot.rotate(a, a);
        float toMs2 = gravityIncluded ? 9.81f / Math.max(0.2f, gravity) : (gravity < 3f ? 9.81f : 1f);
        if (gravityIncluded) a.y -= gravity;
        a.scale(toMs2);
        linear.set(a);
        // dead zone against sensor noise and residual tilt errors
        float l = a.length();
        if (l < 0.6f) a.set(0, 0, 0); else a.scale((l - 0.6f) / l);
        velocity.addScaled(a, dt * gain);
        float kv = (float) Math.exp(-5f * dt), kp = (float) Math.exp(-3.5f * dt);
        velocity.scale(kv);
        offset.addScaled(velocity, dt);
        offset.scale(kp);
        offset.clampLength(maxOffset);
    }

    private void decay(float dt) {
        velocity.scale((float) Math.exp(-5f * dt));
        offset.scale((float) Math.exp(-3.5f * dt));
    }

    /**
     * Calibration: given the controller rotations (game frame) and averaged raw accelerometer readings of
     * the still poses, picks the axis permutation that makes gravity point up in every pose.
     * Returns the residual (0 = perfect).
     */
    public float solve(Quat[] rots, float[][] acc) {
        float meanMag = 0f;
        for (float[] s : acc) meanMag += (float) Math.sqrt(s[0] * s[0] + s[1] * s[1] + s[2] * s[2]);
        meanMag /= acc.length;
        if (meanMag < 0.3f) {
            // the service already removes gravity: cannot infer axes from still poses, keep defaults
            gravityIncluded = false;
            gravity = 9.81f;
            return 0f;
        }
        gravityIncluded = true;
        gravity = meanMag;
        autoGravity = false;
        int[][] P = ControllerMapping.permutations();
        float[] errs = new float[P.length];
        float best = Float.MAX_VALUE;
        int bi = 0;
        Vec3 v = new Vec3();
        for (int k = 0; k < P.length; k++) {
            int[] m = P[k];
            float err = 0f;
            for (int i = 0; i < rots.length; i++) {
                float[] s = acc[i];
                v.set(m[0] * s[0] + m[1] * s[1] + m[2] * s[2], m[3] * s[0] + m[4] * s[1] + m[5] * s[2], m[6] * s[0] + m[7] * s[1] + m[8] * s[2]);
                rots[i].rotate(v, v);
                float len = v.length();
                if (len < 1e-6f) { err += 2f; continue; }
                err += (float) Math.sqrt(v.x * v.x + (v.y - len) * (v.y - len) + v.z * v.z) / len;
            }
            errs[k] = err;
            if (err < best) { best = err; bi = k; }
        }
        // the calibration poses have no roll, so the lateral axis is ambiguous: among equally good
        // candidates keep the handedness (determinant) of the orientation frame and the one closest to it
        int detRef = ControllerMapping.det(pa);
        int pick = -1, bestScore = -1;
        for (int k = 0; k < P.length; k++) {
            if (errs[k] > best + 0.06f * rots.length || ControllerMapping.det(P[k]) != detRef) continue;
            int score = 0;
            for (int j = 0; j < 9; j++) if (P[k][j] == pa[j]) score++;
            if (score > bestScore) { bestScore = score; pick = k; }
        }
        if (pick < 0) pick = bi;
        System.arraycopy(P[pick], 0, pa, 0, 9);
        return errs[pick] / rots.length;
    }

    public void setDefaultFromBody(int[] pb) {
        // vendor body axes -> game body axes is Pb^T for the orientation; use the same for the accelerometer
        for (int r = 0; r < 3; r++) for (int c = 0; c < 3; c++) pa[r * 3 + c] = pb[c * 3 + r];
    }
}
