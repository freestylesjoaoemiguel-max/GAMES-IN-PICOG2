package com.joaolimz.operationzero.game.input;

import com.joaolimz.operationzero.game.math.Quat;
import com.joaolimz.operationzero.game.math.Vec3;

/**
 * Converts the raw controller quaternion reported by the Pico system service into the game frame
 * (right handed, +Y up, -Z forward). The vendor frame is not documented consistently (the official
 * samples apply different conversions), so the conversion is G = Pw * R * Pb (optionally using R^T),
 * where Pw/Pb are signed axis permutations. {@link #solve} finds them from three poses captured by the
 * calibration wizard (pointing forward, up and right). A yaw offset aligns the controller with the head.
 */
public final class ControllerMapping {
    /** World-side and body-side signed permutation matrices (row major 3x3, entries -1/0/1). */
    public final int[] pw = {1, 0, 0, 0, 1, 0, 0, 0, -1};
    public final int[] pb = {1, 0, 0, 0, 1, 0, 0, 0, -1};
    public boolean inverse;
    /** Yaw (radians) added after conversion so that the controller points where the head looks. */
    public float yawOffset;

    private final float[] r = new float[9], g = new float[9], t = new float[9];
    private final Quat qy = new Quat();
    private final Vec3 v = new Vec3();

    private static int[][] perms;

    /** All 48 signed 3x3 permutation matrices. */
    static int[][] permutations() {
        if (perms != null) return perms;
        int[][] order = {{0, 1, 2}, {0, 2, 1}, {1, 0, 2}, {1, 2, 0}, {2, 0, 1}, {2, 1, 0}};
        int[][] out = new int[48][];
        int k = 0;
        for (int[] o : order) {
            for (int s = 0; s < 8; s++) {
                int[] m = new int[9];
                for (int row = 0; row < 3; row++) m[row * 3 + o[row]] = ((s >> row) & 1) == 0 ? 1 : -1;
                out[k++] = m;
            }
        }
        perms = out;
        return out;
    }

    static int det(int[] m) {
        return m[0] * (m[4] * m[8] - m[5] * m[7]) - m[1] * (m[3] * m[8] - m[5] * m[6]) + m[2] * (m[3] * m[7] - m[4] * m[6]);
    }

    /** Raw quaternion (x,y,z,w as the vendor Orientation class) -> game rotation (without yaw offset). */
    public Quat convert(float x, float y, float z, float w, Quat out) {
        return convert(x, y, z, w, pw, pb, inverse, out);
    }

    /** Converts and applies the yaw offset. */
    public Quat apply(float x, float y, float z, float w, Quat out) {
        convert(x, y, z, w, out);
        qy.setYaw(yawOffset);
        return out.preMul(qy);
    }

    private Quat convert(float x, float y, float z, float w, int[] a, int[] b, boolean inv, Quat out) {
        float l = (float) Math.sqrt(x * x + y * y + z * z + w * w);
        if (l < 1e-6f) return out.identity();
        x /= l; y /= l; z /= l; w /= l;
        // rotation matrix (row major)
        float xx = x * x, yy = y * y, zz = z * z, xy = x * y, xz = x * z, yz = y * z, wx = w * x, wy = w * y, wz = w * z;
        r[0] = 1 - 2 * (yy + zz); r[1] = 2 * (xy - wz); r[2] = 2 * (xz + wy);
        r[3] = 2 * (xy + wz); r[4] = 1 - 2 * (xx + zz); r[5] = 2 * (yz - wx);
        r[6] = 2 * (xz - wy); r[7] = 2 * (yz + wx); r[8] = 1 - 2 * (xx + yy);
        if (inv) {
            float s;
            s = r[1]; r[1] = r[3]; r[3] = s;
            s = r[2]; r[2] = r[6]; r[6] = s;
            s = r[5]; r[5] = r[7]; r[7] = s;
        }
        // t = a * r ; g = t * b
        for (int i = 0; i < 3; i++) for (int j = 0; j < 3; j++) {
            t[i * 3 + j] = a[i * 3] * r[j] + a[i * 3 + 1] * r[3 + j] + a[i * 3 + 2] * r[6 + j];
        }
        for (int i = 0; i < 3; i++) for (int j = 0; j < 3; j++) {
            g[i * 3 + j] = t[i * 3] * b[j] + t[i * 3 + 1] * b[3 + j] + t[i * 3 + 2] * b[6 + j];
        }
        // columns of g
        return out.setFromBasis(g[0], g[3], g[6], g[1], g[4], g[7], g[2], g[5], g[8]);
    }

    /**
     * Finds the conversion from three raw poses: pointing forward (level), up and to the right.
     * Each pose is {x,y,z,w}. Returns the residual error (radians-ish; below ~0.6 is a good fit).
     */
    public float solve(float[] fwd, float[] up, float[] right) {
        int[][] P = permutations();
        Quat q = new Quat();
        Vec3 f = new Vec3(), u = new Vec3();
        float best = Float.MAX_VALUE;
        int bw = 0, bb = 0;
        boolean binv = false;
        for (int iw = 0; iw < P.length; iw++) {
            for (int ib = 0; ib < P.length; ib++) {
                if (det(P[iw]) * det(P[ib]) != 1) continue;
                for (int inv = 0; inv < 2; inv++) {
                    boolean bi = inv == 1;
                    convert(fwd[0], fwd[1], fwd[2], fwd[3], P[iw], P[ib], bi, q);
                    q.forward(f);
                    float yaw0 = (float) Math.atan2(-f.x, -f.z);
                    Quat unyaw = new Quat().setYaw(-yaw0);
                    float err = 0f;
                    // forward pose: level forward, controller top facing up
                    Quat g0 = new Quat().setMul(unyaw, q);
                    g0.forward(f);
                    err += dist(f, 0, 0, -1);
                    g0.up(u);
                    err += 0.5f * dist(u, 0, 1, 0);
                    // up pose
                    convert(up[0], up[1], up[2], up[3], P[iw], P[ib], bi, q);
                    q.preMul(unyaw).forward(f);
                    err += dist(f, 0, 1, 0);
                    // right pose
                    convert(right[0], right[1], right[2], right[3], P[iw], P[ib], bi, q);
                    q.preMul(unyaw).forward(f);
                    err += dist(f, 1, 0, 0);
                    if (err < best) { best = err; bw = iw; bb = ib; binv = bi; }
                }
            }
        }
        System.arraycopy(P[bw], 0, pw, 0, 9);
        System.arraycopy(P[bb], 0, pb, 0, 9);
        inverse = binv;
        return best;
    }

    private static float dist(Vec3 a, float x, float y, float z) {
        float dx = a.x - x, dy = a.y - y, dz = a.z - z;
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Sets the yaw offset so the converted raw orientation points toward the given yaw. */
    public void alignYaw(float x, float y, float z, float w, float targetYaw) {
        Quat q = new Quat();
        convert(x, y, z, w, q);
        q.forward(v);
        float yaw = (float) Math.atan2(-v.x, -v.z);
        yawOffset = targetYaw - yaw;
    }

    public void resetDefault() {
        int[] d = {1, 0, 0, 0, 1, 0, 0, 0, -1};
        System.arraycopy(d, 0, pw, 0, 9);
        System.arraycopy(d, 0, pb, 0, 9);
        inverse = false;
    }
}
