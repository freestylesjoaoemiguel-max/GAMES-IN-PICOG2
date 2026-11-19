package com.joaolimz.pegapega.game;

import com.joaolimz.pegapega.game.math.Mat4;
import com.joaolimz.pegapega.game.math.MathUtil;
import com.joaolimz.pegapega.game.math.Quat;
import com.joaolimz.pegapega.game.math.Vec3;

/** Pooled spark particles (infection sparks, tag bursts). */
public final class Particles {
    private static final int MAX = 128;
    private final Vec3[] p = new Vec3[MAX], v = new Vec3[MAX];
    private final float[] life = new float[MAX], r = new float[MAX], g = new float[MAX], b = new float[MAX];
    public int limit = 32, active;
    public final MathUtil.Rng rng = new MathUtil.Rng(9);
    private final Quat id = new Quat();

    public Particles() { for (int i = 0; i < MAX; i++) { p[i] = new Vec3(); v[i] = new Vec3(); } }

    public void burst(Vec3 at, float cr, float cg, float cb) {
        int slot = -1;
        float oldest = Float.MAX_VALUE;
        for (int i = 0; i < limit; i++) {
            if (life[i] <= 0f) { slot = i; break; }
            if (life[i] < oldest) { oldest = life[i]; slot = i; }
        }
        if (slot < 0) return;
        p[slot].set(at).add(rng.range(-0.25f, 0.25f), rng.range(-0.4f, 0.1f), rng.range(-0.25f, 0.25f));
        v[slot].set(rng.range(-1f, 1f), rng.range(0.5f, 2.2f), rng.range(-1f, 1f));
        life[slot] = rng.range(0.3f, 0.7f);
        r[slot] = cr; g[slot] = cg; b[slot] = cb;
    }

    public void update(float dt) {
        active = 0;
        for (int i = 0; i < limit; i++) {
            if (life[i] <= 0f) continue;
            life[i] -= dt;
            v[i].y -= 3f * dt;
            p[i].addScaled(v[i], dt);
            active++;
        }
    }

    public void render(RenderQueue q, int mesh) {
        for (int i = 0; i < limit; i++) {
            if (life[i] <= 0f) continue;
            int k = q.add(mesh, RenderQueue.UNLIT, RenderQueue.F_NO_FOG, r[i] * 1.5f, g[i] * 1.5f, b[i] * 1.5f, 1f);
            if (k >= 0) Mat4.setTRS(q.model[k], p[i], id, 0.6f + life[i]);
        }
    }
}
