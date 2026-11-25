package com.joaolimz.pegapega.game;

import com.joaolimz.pegapega.game.math.Mat4;
import com.joaolimz.pegapega.game.math.MathUtil;
import com.joaolimz.pegapega.game.math.Quat;
import com.joaolimz.pegapega.game.math.Vec3;
import com.joaolimz.pegapega.game.mesh.Assets;
import com.joaolimz.pegapega.game.player.ArmRig;
import com.joaolimz.pegapega.game.tag.Avatar;
import com.joaolimz.pegapega.game.tag.TagGame;

/** Builds the render queue: arena, avatars (LOD), player arms, particles, scoreboard, HUD and menus. */
public final class SceneBuilder {
    private final Game g;
    private final Vec3 t0 = new Vec3(), t1 = new Vec3(), up = new Vec3(0, 1, 0);
    private final Quat q0 = new Quat(), q1 = new Quat();
    private final StringBuilder sb = new StringBuilder();
    private static final float INF_R = 0.62f, INF_G = 0.25f, INF_B = 0.95f;

    public SceneBuilder(Game g) { this.g = g; }

    public void build(RenderQueue q) {
        Assets a = g.assets;
        q.clear();
        q.headPos.set(g.rig.head);
        q.headRot.set(g.headWorld);
        q.drawSky = true;
        q.clearColor[0] = 0.62f; q.clearColor[1] = 0.78f; q.clearColor[2] = 0.95f;
        q.fogColor[0] = 0.7f; q.fogColor[1] = 0.82f; q.fogColor[2] = 0.95f;
        q.fogEnd = g.settings.drawDistance();
        q.fogStart = q.fogEnd * 0.55f;
        q.ambient[0] = 0.55f; q.ambient[1] = 0.56f; q.ambient[2] = 0.62f;
        q.lightColor[0] = 0.7f; q.lightColor[1] = 0.68f; q.lightColor[2] = 0.62f;
        q.addStatic(g.arena.mesh);

        for (int i = 0; i < g.tag.avatars.size(); i++) {
            Avatar av = g.tag.avatars.get(i);
            if (av == g.me) continue;
            avatar(q, a, av);
        }
        playerArms(q, a);
        g.particles.render(q, a.spark);
        scoreboard(q);
        if (g.state == Game.State.PLAYING) hud(q);
        else {
            g.menu.render(q, a);
            laser(q, a);
            messages(q);
        }
        if (g.settings.showPerf) perf(q);
        q.vignette = g.vignette;
        q.damageFlash = g.hurt * 0.5f;
        q.fadeBlack = g.fade;
    }

    private float[] tint(Avatar av, float[] out) {
        int c = av.colorRgb();
        float r = ((c >> 16) & 255) / 255f, gg = ((c >> 8) & 255) / 255f, b = (c & 255) / 255f;
        if (av.it && g.tag.mode == TagGame.INFECTION) {
            float pulse = 0.85f + 0.15f * (float) Math.sin(g.time * 8f + av.id);
            r = INF_R * pulse; gg = INF_G * pulse; b = INF_B * pulse;
        }
        if (av.tagFlash > 0f) { r += av.tagFlash; gg += av.tagFlash; b += av.tagFlash; }
        out[0] = r; out[1] = gg; out[2] = b;
        return out;
    }

    private final float[] col = new float[3];

    private void avatar(RenderQueue q, Assets a, Avatar av) {
        float d = av.head.distance(g.rig.head);
        if (d > g.settings.drawDistance()) return;
        tint(av, col);
        av.body(t0);
        q0.setYaw(av.yaw);
        boolean lod = d > 16f;
        int k = q.add(lod ? a.bodyLod : a.body, RenderQueue.LIT, 0, col[0], col[1], col[2], 1f);
        if (k >= 0) Mat4.setTRS(q.model[k], t0, q0, 1f);
        if (!lod) {
            k = q.add(a.visor, RenderQueue.LIT, 0, 1, 1, 1, 1);
            if (k >= 0) Mat4.setTRS(q.model[k], t0, q0, 1f);
            boolean angry = av.it;
            for (int s = -1; s <= 1; s += 2) {
                q0.rotate(s * 0.075f, 0.07f, -0.31f, t1).add(t0);
                float er = angry ? 1f : 0.6f, eg = angry ? 0.3f : 1f, eb = angry ? 0.3f : 0.95f;
                k = q.add(a.eye, RenderQueue.UNLIT, RenderQueue.F_NO_FOG, er, eg, eb, 1f);
                if (k >= 0) Mat4.setTRS(q.model[k], t1, q0, 1f);
            }
            q0.rotate(0f, 0.24f, 0.05f, t1).add(t0);
            k = q.add(a.antenna, RenderQueue.LIT, 0, col[0], col[1], col[2], 1f);
            if (k >= 0) Mat4.setTRS(q.model[k], t1, q0, 1f);
        }
        // long arms
        arm(q, a, av.shoulderL, av.handL, col, lod);
        arm(q, a, av.shoulderR, av.handR, col, lod);
        // "it" markers
        if (av.it) {
            if (g.tag.mode == TagGame.CLASSIC) {
                t1.set(av.head).add(0, 0.25f + 0.04f * (float) Math.sin(g.time * 4f), 0);
                q1.setYaw(g.time * 1.5f);
                k = q.add(a.crown, RenderQueue.LIT, 0, 1, 1, 1, 1);
                if (k >= 0) Mat4.setTRS(q.model[k], t1, q1, 1.2f);
            } else {
                q1.identity();
                k = q.add(a.halo, RenderQueue.UNLIT, RenderQueue.F_ALPHA | RenderQueue.F_NO_CULL, INF_R, INF_G, INF_B, 0.8f);
                if (k >= 0) Mat4.setTRS(q.model[k], av.head.x, av.head.y - 0.3f, av.head.z, q1, 1.3f, 1f, 1.3f);
            }
        }
        // blob shadow
        float ground = g.arena.physics.groundHeight(av.head.x, av.head.z, 0.1f, av.head.y, 1);
        if (ground > -100f && d < 20f) {
            q1.identity();
            k = q.add(a.blob, RenderQueue.UNLIT, RenderQueue.F_ALPHA, 0, 0, 0, 1);
            float h = av.head.y - ground;
            float sc = MathUtil.clamp(0.5f - h * 0.08f, 0.2f, 0.5f);
            if (k >= 0) Mat4.setTRS(q.model[k], av.head.x, ground + 0.02f, av.head.z, q1, sc, 1f, sc);
        }
        // name tag
        if (d < 14f) {
            t1.set(av.head).add(0, av.it && g.tag.mode == TagGame.CLASSIC ? 0.55f : 0.42f, 0);
            q1.setLookRotation(g.rig.head.x - t1.x, 0, g.rig.head.z - t1.z, 0, 1, 0);
            q1.mul(q0.setYaw(MathUtil.PI));
            q.addText(av.name, t1, q1, 0.09f, 1, 1f, 1f, 1f, 0.9f, 0);
        }
    }

    private void arm(RenderQueue q, Assets a, Vec3 shoulder, Vec3 hand, float[] c, boolean lod) {
        int k = q.add(a.arm, RenderQueue.LIT, 0, c[0] * 0.85f, c[1] * 0.85f, c[2] * 0.85f, 1f);
        if (k >= 0) Mat4.setSegment(q.model[k], shoulder, hand, 0.07f, 0.07f, q1, up);
        if (lod) return;
        t1.setSub(hand, shoulder);
        q1.setLookRotation(t1.x, t1.y, t1.z, 0, 1, 0);
        k = q.add(a.hand, RenderQueue.LIT, 0, c[0], c[1], c[2], 1f);
        if (k >= 0) Mat4.setTRS(q.model[k], hand, q1, 1.2f);
    }

    private void playerArms(RenderQueue q, Assets a) {
        if (g.state == Game.State.MENU) return;
        tint(g.me, col);
        for (ArmRig.Hand h : g.rig.hands) {
            int k = q.add(a.arm, RenderQueue.LIT, 0, col[0] * 0.85f, col[1] * 0.85f, col[2] * 0.85f, 1f);
            if (k >= 0) Mat4.setSegment(q.model[k], h.shoulder, h.pos, 0.06f, 0.06f, q1, up);
            t1.setSub(h.pos, h.shoulder);
            q1.setLookRotation(t1.x, t1.y, t1.z, 0, 1, 0);
            float hl = h.anchored ? 1.25f : 1f;
            k = q.add(h.anchored ? a.handFist : a.hand, RenderQueue.LIT, 0, col[0] * hl, col[1] * hl, col[2] * hl, 1f);
            if (k >= 0) Mat4.setTRS(q.model[k], h.pos, q1, 1.2f);
        }
    }

    private void scoreboard(RenderQueue q) {
        Vec3 b = g.arena.board;
        q0.setYaw(MathUtil.PI); // faces -Z (toward the arena)
        TagGame t = g.tag;
        t1.set(b.x, b.y + 1.4f, b.z - 0.12f);
        sb.setLength(0);
        sb.append(TagGame.MODE_NAMES[t.mode]);
        if (t.mode != TagGame.FREE && g.state != Game.State.MENU) sb.append("  -  ").append(time(t.timeLeft));
        q.addText(sb.toString(), t1, q0, 0.32f, 1, 1f, 0.85f, 0.3f, 1f, 0);
        int row = 0;
        for (int i = 0; i < t.avatars.size() && row < 10; i++) {
            Avatar av = t.avatars.get(i);
            sb.setLength(0);
            sb.append(av.name);
            if (t.mode == TagGame.CLASSIC) sb.append("  ").append(time(av.timeIt));
            else if (t.mode == TagGame.INFECTION) sb.append(av.it ? "  INFECTADO" : "  livre");
            int c = av.colorRgb();
            t1.set(b.x + 2.8f - (row % 2) * 3f, b.y + 0.9f - (row / 2) * 0.32f, b.z - 0.12f);
            q.addText(sb.toString(), t1, q0, 0.2f, 0, ((c >> 16) & 255) / 255f, ((c >> 8) & 255) / 255f, (c & 255) / 255f, 1f, 0);
            row++;
        }
    }

    private static String time(float s) {
        int sec = Math.max(0, (int) Math.ceil(s));
        return (sec / 60) + ":" + (sec % 60 < 10 ? "0" : "") + (sec % 60);
    }

    private void hud(RenderQueue q) {
        int f = RenderQueue.F_OVERLAY;
        TagGame t = g.tag;
        sb.setLength(0);
        if (t.phase == TagGame.COUNTDOWN && t.mode != TagGame.FREE) sb.append("Começa em ").append((int) Math.ceil(t.phaseTime));
        else if (t.mode == TagGame.FREE) sb.append("TREINO LIVRE");
        else if (t.phase == TagGame.OVER) sb.append("Próxima rodada em ").append((int) Math.ceil(t.phaseTime));
        else {
            sb.append(time(t.timeLeft)).append("   ");
            if (t.mode == TagGame.INFECTION) sb.append(t.avatars.size() - t.countIt()).append(" livres");
            sb.append(g.me.it ? "   VOCÊ É PEGADOR" : "   FUJA!");
        }
        g.headWorld.rotate(0f, -0.36f, -1.0f, t1).add(g.rig.head);
        float r = g.me.it ? 1f : 0.8f, gg = g.me.it ? 0.45f : 1f, b = g.me.it ? 0.9f : 0.8f;
        q.addText(sb.toString(), t1, g.headWorld, 0.035f, 1, r, gg, b, 0.95f, f);
        messages(q);
    }

    private void messages(RenderQueue q) {
        int f = RenderQueue.F_OVERLAY;
        if (g.bannerTimer > 0f && g.banner != null) {
            g.headWorld.rotate(0f, 0.18f, -1.0f, t1).add(g.rig.head);
            q.addText(g.banner, t1, g.headWorld, 0.045f, 1, 1f, 0.85f, 0.35f, Math.min(1f, g.bannerTimer), f);
        }
        if (g.hintTimer > 0f && g.hint != null) {
            g.headWorld.rotate(0f, -0.24f, -1.0f, t1).add(g.rig.head);
            q.addText(g.hint, t1, g.headWorld, 0.03f, 1, 1f, 1f, 1f, Math.min(1f, g.hintTimer), f);
        }
    }

    private void laser(RenderQueue q, Assets a) {
        if (!g.in.hasControllerPose || !g.menu.cursorValid) return;
        int k = q.add(a.laser, RenderQueue.UNLIT, RenderQueue.F_ALPHA | RenderQueue.F_NO_FOG, 0.3f, 0.85f, 1f, 0.5f);
        Vec3 from = g.rig.hands[g.activeArm].shoulder;
        if (k >= 0) Mat4.setSegment(q.model[k], from, g.menu.cursor, 0.005f, 0.005f, q1, up);
    }

    private void perf(RenderQueue q) {
        sb.setLength(0);
        sb.append("FPS ").append((int) g.perf.fps).append("  ").append((int) g.perf.frameMs).append(" ms  draw ").append(g.perf.drawCalls)
                .append("  tri ").append(g.perf.triangles).append("  bots ").append(g.perf.activeNpcs);
        g.headWorld.rotate(-0.3f, 0.3f, -1.0f, t1).add(g.rig.head);
        q.addText(sb.toString(), t1, g.headWorld, 0.022f, 0, 0.5f, 1f, 0.5f, 1f, RenderQueue.F_OVERLAY);
    }
}
