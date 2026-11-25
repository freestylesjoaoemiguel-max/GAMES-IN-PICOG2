package com.joaolimz.vrweb.game.ui;

import com.joaolimz.vrweb.game.math.Quat;
import com.joaolimz.vrweb.game.math.Vec3;

/** A flat rectangle floating in the world (browser screen, toolbar, keyboard) that a ray can hit. */
public final class Surface {
    public final Vec3 center = new Vec3();
    public final Quat rot = new Quat();
    public float w, h;
    private final Vec3 n = new Vec3(), r = new Vec3(), u = new Vec3(), p = new Vec3();

    public Surface(float w, float h) { this.w = w; this.h = h; }

    /**
     * Ray test. Returns the distance (>0) or -1; out[0], out[1] receive the hit position in the surface plane
     * (meters from the center, x right, y up).
     */
    public float hit(Vec3 origin, Vec3 dir, float margin, float[] out) {
        rot.rotate(0, 0, 1, n);
        float denom = dir.dot(n);
        if (denom > -1e-4f) return -1f;
        p.setSub(center, origin);
        float t = p.dot(n) / denom;
        if (t <= 0f) return -1f;
        p.set(origin).addScaled(dir, t).sub(center);
        rot.rotate(1, 0, 0, r);
        rot.rotate(0, 1, 0, u);
        float lx = p.dot(r), ly = p.dot(u);
        if (Math.abs(lx) > w * 0.5f + margin || Math.abs(ly) > h * 0.5f + margin) return -1f;
        out[0] = lx;
        out[1] = ly;
        return t;
    }

    /** Local point (x right, y up, z toward the viewer) to world space. */
    public Vec3 toWorld(float lx, float ly, float lz, Vec3 out) {
        rot.rotate(lx, ly, lz, out);
        return out.add(center);
    }
}
