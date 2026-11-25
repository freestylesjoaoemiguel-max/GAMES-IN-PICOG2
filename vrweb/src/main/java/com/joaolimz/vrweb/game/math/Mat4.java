package com.joaolimz.vrweb.game.math;

/** Column-major 4x4 matrix helpers operating on float[16] (OpenGL layout). */
public final class Mat4 {
    private Mat4() { }

    private static final float[] TMP = new float[16];

    public static void identity(float[] m) {
        for (int i = 0; i < 16; i++) m[i] = 0f;
        m[0] = m[5] = m[10] = m[15] = 1f;
    }

    public static void copy(float[] src, float[] dst) { System.arraycopy(src, 0, dst, 0, 16); }

    /** out = a * b. out may alias a or b. Not thread safe (uses shared temp). */
    public static void multiply(float[] out, float[] a, float[] b) {
        float[] r = TMP;
        for (int c = 0; c < 4; c++) {
            for (int row = 0; row < 4; row++) {
                r[c * 4 + row] = a[row] * b[c * 4] + a[4 + row] * b[c * 4 + 1]
                        + a[8 + row] * b[c * 4 + 2] + a[12 + row] * b[c * 4 + 3];
            }
        }
        System.arraycopy(r, 0, out, 0, 16);
    }

    /** Translation * Rotation * Scale. */
    public static void setTRS(float[] m, float px, float py, float pz, Quat q, float sx, float sy, float sz) {
        float x = q.x, y = q.y, z = q.z, w = q.w;
        float xx = x * x, yy = y * y, zz = z * z, xy = x * y, xz = x * z, yz = y * z, wx = w * x, wy = w * y, wz = w * z;
        m[0] = (1 - 2 * (yy + zz)) * sx; m[1] = 2 * (xy + wz) * sx; m[2] = 2 * (xz - wy) * sx; m[3] = 0;
        m[4] = 2 * (xy - wz) * sy; m[5] = (1 - 2 * (xx + zz)) * sy; m[6] = 2 * (yz + wx) * sy; m[7] = 0;
        m[8] = 2 * (xz + wy) * sz; m[9] = 2 * (yz - wx) * sz; m[10] = (1 - 2 * (xx + yy)) * sz; m[11] = 0;
        m[12] = px; m[13] = py; m[14] = pz; m[15] = 1;
    }

    public static void setTRS(float[] m, Vec3 p, Quat q, float s) { setTRS(m, p.x, p.y, p.z, q, s, s, s); }

    public static void setTRS(float[] m, float px, float py, float pz, Quat q, float s) { setTRS(m, px, py, pz, q, s, s, s); }

    public static void setTRS(float[] m, Vec3 p, Quat q, float sx, float sy, float sz) { setTRS(m, p.x, p.y, p.z, q, sx, sy, sz); }

    /** Builds a matrix that maps a unit-length +Z segment to the segment a->b with given thickness scale. */
    public static void setSegment(float[] m, Vec3 a, Vec3 b, float sx, float sy, Quat tmpQ, Vec3 upHint) {
        float dx = b.x - a.x, dy = b.y - a.y, dz = b.z - a.z;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-5f) { len = 1e-5f; dz = -1e-5f; }
        float ux = upHint.x, uy = upHint.y, uz = upHint.z;
        if (Math.abs(dy) / len > 0.95f) { ux = 0; uy = 0; uz = 1; }
        tmpQ.setLookRotation(-dx, -dy, -dz, ux, uy, uz); // +Z maps to direction a->b
        setTRS(m, (a.x + b.x) * 0.5f, (a.y + b.y) * 0.5f, (a.z + b.z) * 0.5f, tmpQ, sx, sy, len);
    }

    public static void frustum(float[] m, float l, float r, float b, float t, float n, float f) {
        for (int i = 0; i < 16; i++) m[i] = 0f;
        m[0] = 2 * n / (r - l);
        m[5] = 2 * n / (t - b);
        m[8] = (r + l) / (r - l);
        m[9] = (t + b) / (t - b);
        m[10] = -(f + n) / (f - n);
        m[11] = -1f;
        m[14] = -2 * f * n / (f - n);
    }

    public static void perspective(float[] m, float fovYRad, float aspect, float n, float f) {
        float t = n * (float) Math.tan(fovYRad * 0.5f);
        float r = t * aspect;
        frustum(m, -r, r, -t, t, n, f);
    }

    public static void orthographic(float[] m, float l, float r, float b, float t, float n, float f) {
        for (int i = 0; i < 16; i++) m[i] = 0f;
        m[0] = 2f / (r - l); m[5] = 2f / (t - b); m[10] = -2f / (f - n);
        m[12] = -(r + l) / (r - l); m[13] = -(t + b) / (t - b); m[14] = -(f + n) / (f - n); m[15] = 1f;
    }

    /** View matrix = inverse of rigid transform (position p, rotation q). */
    public static void setViewFromPose(float[] m, Vec3 p, Quat q) {
        // inverse rotation (conjugate) as rows
        float x = -q.x, y = -q.y, z = -q.z, w = q.w;
        float xx = x * x, yy = y * y, zz = z * z, xy = x * y, xz = x * z, yz = y * z, wx = w * x, wy = w * y, wz = w * z;
        m[0] = 1 - 2 * (yy + zz); m[1] = 2 * (xy + wz); m[2] = 2 * (xz - wy); m[3] = 0;
        m[4] = 2 * (xy - wz); m[5] = 1 - 2 * (xx + zz); m[6] = 2 * (yz + wx); m[7] = 0;
        m[8] = 2 * (xz + wy); m[9] = 2 * (yz - wx); m[10] = 1 - 2 * (xx + yy); m[11] = 0;
        m[12] = -(m[0] * p.x + m[4] * p.y + m[8] * p.z);
        m[13] = -(m[1] * p.x + m[5] * p.y + m[9] * p.z);
        m[14] = -(m[2] * p.x + m[6] * p.y + m[10] * p.z);
        m[15] = 1;
    }

    public static Vec3 transformPoint(float[] m, float x, float y, float z, Vec3 out) {
        float nx = m[0] * x + m[4] * y + m[8] * z + m[12];
        float ny = m[1] * x + m[5] * y + m[9] * z + m[13];
        float nz = m[2] * x + m[6] * y + m[10] * z + m[14];
        out.x = nx; out.y = ny; out.z = nz;
        return out;
    }

    public static Vec3 transformDir(float[] m, float x, float y, float z, Vec3 out) {
        float nx = m[0] * x + m[4] * y + m[8] * z;
        float ny = m[1] * x + m[5] * y + m[9] * z;
        float nz = m[2] * x + m[6] * y + m[10] * z;
        out.x = nx; out.y = ny; out.z = nz;
        return out;
    }
}
