package com.joaolimz.operationzero.game.fx;

import com.joaolimz.operationzero.game.RenderQueue;
import com.joaolimz.operationzero.game.math.Mat4;
import com.joaolimz.operationzero.game.math.MathUtil;
import com.joaolimz.operationzero.game.math.Quat;
import com.joaolimz.operationzero.game.math.Vec3;
import com.joaolimz.operationzero.game.mesh.Assets;

/** Pooled lightweight visual effects: sparks, muzzle flashes, tracers, impact puffs. */
public final class Effects {
    private static final int MAX = 96;
    private static final int SPARK = 0, FLASH = 1, TRACER = 2, PUFF = 3;

    private final int[] type = new int[MAX];
    private final float[] life = new float[MAX], maxLife = new float[MAX];
    private final Vec3[] pos = new Vec3[MAX], vel = new Vec3[MAX], end = new Vec3[MAX];
    private final float[] r = new float[MAX], g = new float[MAX], b = new float[MAX], size = new float[MAX];
    private final Quat[] rot = new Quat[MAX];
    private int limit = 24;
    private final MathUtil.Rng rng = new MathUtil.Rng(77);
    private final Quat tq = new Quat();
    private final Vec3 up = new Vec3(0, 1, 0);
    public int activeCount;

    public Effects() {
        for (int i = 0; i < MAX; i++) { pos[i] = new Vec3(); vel[i] = new Vec3(); end[i] = new Vec3(); rot[i] = new Quat(); }
    }

    public void setLimit(int l) { limit = Math.max(8, Math.min(MAX, l)); }

    private int alloc() {
        int oldest = 0;
        float ol = Float.MAX_VALUE;
        for (int i = 0; i < limit; i++) {
            if (life[i] <= 0f) return i;
            if (life[i] < ol) { ol = life[i]; oldest = i; }
        }
        return oldest;
    }

    public void sparks(Vec3 p, Vec3 normal, int count, float cr, float cg, float cb) {
        for (int k = 0; k < count; k++) {
            int i = alloc();
            type[i] = SPARK;
            life[i] = maxLife[i] = rng.range(0.15f, 0.35f);
            pos[i].set(p);
            vel[i].set(normal).scale(rng.range(1f, 3f)).add(rng.range(-1.5f, 1.5f), rng.range(0f, 2f), rng.range(-1.5f, 1.5f));
            r[i] = cr; g[i] = cg; b[i] = cb;
            size[i] = rng.range(0.6f, 1.3f);
        }
    }

    public void puff(Vec3 p, float cr, float cg, float cb, float s) {
        int i = alloc();
        type[i] = PUFF;
        life[i] = maxLife[i] = 0.35f;
        pos[i].set(p);
        vel[i].set(0, 0.4f, 0);
        r[i] = cr; g[i] = cg; b[i] = cb;
        size[i] = s;
    }

    public void flash(Vec3 p, Quat q, float s) {
        int i = alloc();
        type[i] = FLASH;
        life[i] = maxLife[i] = 0.05f;
        pos[i].set(p);
        rot[i].set(q);
        tq.setAxisAngle(0, 0, 1, rng.range(0f, 3.14f));
        rot[i].mul(tq);
        size[i] = s;
        r[i] = 1; g[i] = 0.85f; b[i] = 0.5f;
    }

    public void tracer(Vec3 from, Vec3 to, float cr, float cg, float cb) {
        int i = alloc();
        type[i] = TRACER;
        life[i] = maxLife[i] = 0.06f;
        pos[i].set(from);
        end[i].set(to);
        r[i] = cr; g[i] = cg; b[i] = cb;
    }

    public void update(float dt) {
        activeCount = 0;
        for (int i = 0; i < limit; i++) {
            if (life[i] <= 0f) continue;
            life[i] -= dt;
            activeCount++;
            if (type[i] == SPARK) {
                vel[i].y -= 9.8f * dt;
                pos[i].addScaled(vel[i], dt);
            } else if (type[i] == PUFF) {
                pos[i].addScaled(vel[i], dt);
            }
        }
    }

    public void render(RenderQueue q, Assets a) {
        for (int i = 0; i < limit; i++) {
            if (life[i] <= 0f) continue;
            float k = life[i] / maxLife[i];
            int idx;
            switch (type[i]) {
                case SPARK:
                    idx = q.add(a.spark, RenderQueue.UNLIT, RenderQueue.F_NO_FOG, r[i], g[i], b[i], 1f);
                    if (idx >= 0) { tq.identity(); Mat4.setTRS(q.model[idx], pos[i], tq, size[i] * (0.4f + 0.6f * k)); }
                    break;
                case PUFF:
                    idx = q.add(a.uiQuad, RenderQueue.UNLIT, RenderQueue.F_ALPHA | RenderQueue.F_NO_CULL, r[i], g[i], b[i], 0.5f * k);
                    if (idx >= 0) {
                        tq.set(q.headRot);
                        float s = size[i] * (1.5f - k * 0.5f);
                        Mat4.setTRS(q.model[idx], pos[i].x, pos[i].y, pos[i].z, tq, s, s, s);
                    }
                    break;
                case FLASH:
                    idx = q.add(a.flash, RenderQueue.UNLIT, RenderQueue.F_ALPHA | RenderQueue.F_NO_CULL | RenderQueue.F_NO_FOG, 1f, 1f, 1f, 1f);
                    if (idx >= 0) Mat4.setTRS(q.model[idx], pos[i], rot[i], size[i]);
                    break;
                default:
                    idx = q.add(a.tracer, RenderQueue.UNLIT, RenderQueue.F_ALPHA | RenderQueue.F_NO_FOG, r[i], g[i], b[i], 0.7f * k);
                    if (idx >= 0) Mat4.setSegment(q.model[idx], pos[i], end[i], 0.008f, 0.008f, tq, up);
                    break;
            }
        }
    }

    public void clear() { for (int i = 0; i < MAX; i++) life[i] = 0f; }
}
