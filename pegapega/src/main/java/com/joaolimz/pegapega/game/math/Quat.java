package com.joaolimz.pegapega.game.math;

/** Mutable unit quaternion (x, y, z, w). Convention: right-handed, -Z forward, +Y up. */
public final class Quat {
    public float x, y, z, w = 1f;

    public Quat() { }

    public Quat(float x, float y, float z, float w) { this.x = x; this.y = y; this.z = z; this.w = w; }

    public Quat(Quat o) { set(o); }

    public Quat set(float x, float y, float z, float w) { this.x = x; this.y = y; this.z = z; this.w = w; return this; }

    public Quat set(Quat o) { x = o.x; y = o.y; z = o.z; w = o.w; return this; }

    public Quat identity() { x = 0; y = 0; z = 0; w = 1; return this; }

    public Quat setAxisAngle(float ax, float ay, float az, float radians) {
        float len = (float) Math.sqrt(ax * ax + ay * ay + az * az);
        if (len < 1e-6f) return identity();
        float s = (float) Math.sin(radians * 0.5f) / len;
        x = ax * s; y = ay * s; z = az * s; w = (float) Math.cos(radians * 0.5f);
        return this;
    }

    public Quat setYaw(float radians) { return setAxisAngle(0, 1, 0, radians); }

    /** Yaw (Y), then pitch (X), then roll (Z), applied as q = yaw * pitch * roll. */
    public Quat setEuler(float yaw, float pitch, float roll) {
        float cy = (float) Math.cos(yaw * 0.5f), sy = (float) Math.sin(yaw * 0.5f);
        float cp = (float) Math.cos(pitch * 0.5f), sp = (float) Math.sin(pitch * 0.5f);
        float cr = (float) Math.cos(roll * 0.5f), sr = (float) Math.sin(roll * 0.5f);
        // yaw*pitch (expanded quaternion product)
        float ax = cy * sp, ay = sy * cp, az = -sy * sp, aw = cy * cp;
        // (yaw*pitch)*roll, roll quat = (0,0,sr,cr)
        x = ax * cr + ay * sr;
        y = ay * cr - ax * sr;
        z = aw * sr + az * cr;
        w = aw * cr - az * sr;
        return this;
    }

    /** this = a * b (aliasing safe). */
    public Quat setMul(Quat a, Quat b) {
        float nx = a.w * b.x + a.x * b.w + a.y * b.z - a.z * b.y;
        float ny = a.w * b.y - a.x * b.z + a.y * b.w + a.z * b.x;
        float nz = a.w * b.z + a.x * b.y - a.y * b.x + a.z * b.w;
        float nw = a.w * b.w - a.x * b.x - a.y * b.y - a.z * b.z;
        x = nx; y = ny; z = nz; w = nw;
        return this;
    }

    public Quat mul(Quat b) { return setMul(this, b); }

    public Quat preMul(Quat a) { return setMul(a, this); }

    public Quat conjugate() { x = -x; y = -y; z = -z; return this; }

    public Quat normalize() {
        float l = (float) Math.sqrt(x * x + y * y + z * z + w * w);
        if (l < 1e-6f) return identity();
        x /= l; y /= l; z /= l; w /= l;
        return this;
    }

    /** out = this * v (rotate vector). out may alias v. */
    public Vec3 rotate(Vec3 v, Vec3 out) {
        return rotate(v.x, v.y, v.z, out);
    }

    public Vec3 rotate(float vx, float vy, float vz, Vec3 out) {
        // t = 2 * cross(q.xyz, v)
        float tx = 2f * (y * vz - z * vy);
        float ty = 2f * (z * vx - x * vz);
        float tz = 2f * (x * vy - y * vx);
        out.x = vx + w * tx + (y * tz - z * ty);
        out.y = vy + w * ty + (z * tx - x * tz);
        out.z = vz + w * tz + (x * ty - y * tx);
        return out;
    }

    public Vec3 forward(Vec3 out) { return rotate(0, 0, -1, out); }

    public Vec3 up(Vec3 out) { return rotate(0, 1, 0, out); }

    public Vec3 right(Vec3 out) { return rotate(1, 0, 0, out); }

    /** Heading angle (radians) of the forward vector projected on XZ. 0 = -Z, positive = turning left. */
    public float yaw() {
        float fx = -2f * (x * z + w * y);
        float fz = -(1f - 2f * (x * x + y * y));
        return (float) Math.atan2(-fx, -fz);
    }

    /** Pitch of the forward vector (positive = looking up). */
    public float pitch() {
        float fy = -2f * (y * z - w * x);
        if (fy > 1f) fy = 1f; else if (fy < -1f) fy = -1f;
        return (float) Math.asin(fy);
    }

    public Quat slerp(Quat target, float t) {
        float bx = target.x, by = target.y, bz = target.z, bw = target.w;
        float cos = x * bx + y * by + z * bz + w * bw;
        if (cos < 0f) { cos = -cos; bx = -bx; by = -by; bz = -bz; bw = -bw; }
        float s0, s1;
        if (cos > 0.9995f) {
            s0 = 1f - t; s1 = t;
        } else {
            float ang = (float) Math.acos(cos);
            float sin = (float) Math.sin(ang);
            s0 = (float) Math.sin((1f - t) * ang) / sin;
            s1 = (float) Math.sin(t * ang) / sin;
        }
        x = s0 * x + s1 * bx; y = s0 * y + s1 * by; z = s0 * z + s1 * bz; w = s0 * w + s1 * bw;
        return normalize();
    }

    /** Rotation whose -Z axis points along dir, with up hint. */
    public Quat setLookRotation(float dx, float dy, float dz, float ux, float uy, float uz) {
        float fl = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (fl < 1e-6f) return identity();
        // back axis (z) = -dir
        float zx = -dx / fl, zy = -dy / fl, zz = -dz / fl;
        // x = up cross z
        float xx = uy * zz - uz * zy, xy = uz * zx - ux * zz, xz = ux * zy - uy * zx;
        float xl = (float) Math.sqrt(xx * xx + xy * xy + xz * xz);
        if (xl < 1e-5f) { // dir parallel to up
            xx = 1; xy = 0; xz = 0; xl = 1;
        }
        xx /= xl; xy /= xl; xz /= xl;
        float yx = zy * xz - zz * xy, yy = zz * xx - zx * xz, yz = zx * xy - zy * xx;
        return setFromBasis(xx, xy, xz, yx, yy, yz, zx, zy, zz);
    }

    /** Columns X, Y, Z of a rotation matrix. */
    public Quat setFromBasis(float m00, float m10, float m20, float m01, float m11, float m21, float m02, float m12, float m22) {
        float tr = m00 + m11 + m22;
        if (tr > 0) {
            float s = (float) Math.sqrt(tr + 1.0f) * 2f;
            w = 0.25f * s; x = (m21 - m12) / s; y = (m02 - m20) / s; z = (m10 - m01) / s;
        } else if (m00 > m11 && m00 > m22) {
            float s = (float) Math.sqrt(1.0f + m00 - m11 - m22) * 2f;
            w = (m21 - m12) / s; x = 0.25f * s; y = (m01 + m10) / s; z = (m02 + m20) / s;
        } else if (m11 > m22) {
            float s = (float) Math.sqrt(1.0f + m11 - m00 - m22) * 2f;
            w = (m02 - m20) / s; x = (m01 + m10) / s; y = 0.25f * s; z = (m12 + m21) / s;
        } else {
            float s = (float) Math.sqrt(1.0f + m22 - m00 - m11) * 2f;
            w = (m10 - m01) / s; x = (m02 + m20) / s; y = (m12 + m21) / s; z = 0.25f * s;
        }
        return normalize();
    }

    @Override public String toString() { return "Q(" + x + ", " + y + ", " + z + ", " + w + ")"; }
}
