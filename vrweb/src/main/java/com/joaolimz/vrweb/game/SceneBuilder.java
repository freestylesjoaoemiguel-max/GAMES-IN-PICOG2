package com.joaolimz.vrweb.game;

import com.joaolimz.vrweb.game.math.Mat4;
import com.joaolimz.vrweb.game.math.Quat;
import com.joaolimz.vrweb.game.math.Vec3;
import com.joaolimz.vrweb.game.mesh.Assets;
import com.joaolimz.vrweb.game.ui.Surface;
import com.joaolimz.vrweb.game.ui.VrKeyboard;

/** Builds the render queue: environment, browser screen frame, toolbar, keyboard, pointer, messages, menus. */
public final class SceneBuilder {
    private final Game g;
    private final Vec3 t0 = new Vec3(), t1 = new Vec3(), up = new Vec3(0, 1, 0);
    private final Quat q0 = new Quat();
    private final StringBuilder sb = new StringBuilder();
    private static final int F = RenderQueue.F_ALPHA | RenderQueue.F_NO_FOG | RenderQueue.F_OVERLAY;

    public SceneBuilder(Game g) { this.g = g; }

    public void build(RenderQueue q) {
        Assets a = g.assets;
        q.clear();
        q.headPos.set(g.eye);
        q.headRot.set(g.headWorld);
        q.drawSky = true;
        q.clearColor[0] = 0.06f; q.clearColor[1] = 0.07f; q.clearColor[2] = 0.1f;
        q.fogColor[0] = 0.08f; q.fogColor[1] = 0.09f; q.fogColor[2] = 0.13f;
        q.fogStart = 40f; q.fogEnd = 80f;
        q.addStatic(a.floor);

        boolean browse = g.state == Game.State.BROWSE;
        // browser screen (textured quad) + dark frame behind it
        q.webVisible = true;
        Mat4.setTRS(q.webModel, g.panel.center.x, g.panel.center.y, g.panel.center.z, g.panel.rot, g.panel.w, g.panel.h, 1f);
        int k = q.add(a.uiQuad, RenderQueue.UNLIT, RenderQueue.F_NO_FOG, 0.02f, 0.02f, 0.03f, 1f);
        if (k >= 0) Mat4.setTRS(q.model[k], g.panel.center.x, g.panel.center.y, g.panel.center.z - 0.02f, g.panel.rot, g.panel.w + 0.08f, g.panel.h + 0.08f, 1f);

        if (browse) {
            toolbar(q, a);
            if (g.keyboardVisible) keyboard(q, a);
            if (g.urlMode) urlBar(q, a);
            pointer(q, a);
        } else {
            g.menu.render(q, a);
            menuPointer(q, a);
        }
        messages(q);
        if (g.settings.showPerf) perf(q);
        q.vignette = 0f;
        q.fadeBlack = 0f;
    }

    private void quad(RenderQueue q, Assets a, Surface s, float lx, float ly, float w, float h, float r, float gg, float b, float al) {
        s.toWorld(lx, ly, 0.002f, t0);
        int k = q.add(a.uiQuad, RenderQueue.UNLIT, F, r, gg, b, al);
        if (k >= 0) Mat4.setTRS(q.model[k], t0, s.rot, w, h, 1f);
    }

    private void label(RenderQueue q, Surface s, String text, float lx, float ly, float size, int align, float r, float gg, float b) {
        s.toWorld(lx, ly - size * 0.3f, 0.005f, t0);
        q.addText(text, t0, s.rot, size, align, r, gg, b, 1f, RenderQueue.F_OVERLAY);
    }

    private void toolbar(RenderQueue q, Assets a) {
        Surface s = g.toolbar;
        quad(q, a, s, 0f, 0f, s.w, s.h, 0.07f, 0.08f, 0.11f, 0.92f);
        String title = g.host != null ? g.host.title() : "";
        String url = g.host != null ? g.host.url() : "";
        int prog = g.host != null ? g.host.progress() : 100;
        sb.setLength(0);
        if (g.urlMode) sb.append("Endereço: ").append(g.urlBuffer).append('_');
        else {
            if (title != null && title.length() > 0) sb.append(trim(title, 40)).append("   -   ");
            sb.append(trim(url == null ? "" : url, 60));
            if (prog > 0 && prog < 100) sb.append("   (").append(prog).append("%)");
        }
        label(q, s, sb.toString(), 0f, 0.085f, 0.055f, 1, 0.85f, 0.9f, 1f);
        if (prog > 0 && prog < 100) quad(q, a, s, -s.w / 2 + s.w * prog / 200f, 0.145f, s.w * prog / 100f, 0.01f, 0.35f, 0.4f, 0.95f, 1f);
        for (int i = 0; i < g.buttons.size(); i++) {
            Game.Btn b = g.buttons.get(i);
            boolean hov = b == g.hoverBtn;
            boolean dim = (b.id.equals("back") && g.host != null && !g.host.canGoBack()) || (b.id.equals("fwd") && g.host != null && !g.host.canGoForward());
            float v = hov ? 0.9f : dim ? 0.12f : 0.2f;
            quad(q, a, s, b.x, b.y, b.w, b.h, hov ? 0.35f : v, hov ? 0.4f : v + 0.02f, hov ? 0.95f : v + 0.08f, 0.95f);
            label(q, s, b.label, b.x, b.y, 0.045f, 1, dim ? 0.5f : 1f, dim ? 0.5f : 1f, dim ? 0.5f : 1f);
        }
    }

    private static String trim(String s, int n) { return s.length() <= n ? s : s.substring(0, n - 1) + "~"; }

    private void keyboard(RenderQueue q, Assets a) {
        Surface s = g.keyboard.surface;
        float ks = s.w / 1.9f;
        quad(q, a, s, 0f, 0f, s.w + 0.04f, s.h + 0.04f, 0.05f, 0.06f, 0.09f, 0.94f);
        for (int i = 0; i < g.keyboard.keys.size(); i++) {
            VrKeyboard.Key key = g.keyboard.keys.get(i);
            boolean hov = key == g.hoverKey;
            boolean special = key.kind != VrKeyboard.CHAR && key.kind != VrKeyboard.SPACE;
            float v = special ? 0.16f : 0.24f;
            float r = hov ? 0.35f : v, gg = hov ? 0.4f : v + 0.01f, b = hov ? 0.95f : v + 0.07f;
            if (key.active) { r = 0.3f; gg = 0.35f; b = 0.8f; }
            quad(q, a, s, key.x * ks, key.y * ks, key.w * ks, key.h * ks, r, gg, b, 0.96f);
            label(q, s, key.label, key.x * ks, key.y * ks, special && key.label.length() > 3 ? 0.04f * ks : 0.062f * ks, 1, 1f, 1f, 1f);
        }
    }

    private void urlBar(RenderQueue q, Assets a) {
        // big readable echo of what is being typed, floating above the keyboard
        Surface s = g.keyboard.surface;
        t0.set(0f, -0.05f, -1.15f);
        q0.identity();
        sb.setLength(0);
        sb.append(g.urlBuffer).append('_');
        q.addText(sb.toString(), t0, q0, 0.06f, 1, 1f, 0.95f, 0.5f, 1f, RenderQueue.F_OVERLAY);
    }

    private void pointer(RenderQueue q, Assets a) {
        t1.set(g.rayOrigin);
        Vec3 end = g.cursorValid ? g.cursor : t0.set(g.rayOrigin).addScaled(g.rayDir, 3f);
        int k = q.add(a.laser, RenderQueue.UNLIT, RenderQueue.F_ALPHA | RenderQueue.F_NO_FOG, g.in.trigger ? 1f : 0.35f, g.in.trigger ? 0.8f : 0.85f, 1f, g.cursorValid ? 0.55f : 0.2f);
        if (k >= 0) Mat4.setSegment(q.model[k], t1, end, 0.004f, 0.004f, q0, up);
        if (g.cursorValid) {
            float sc = g.in.trigger ? 0.035f : 0.05f;
            Surface s = g.hoverKind == Game.HOVER_PANEL ? g.panel : g.hoverKind == Game.HOVER_TOOL ? g.toolbar : g.keyboard.surface;
            k = q.add(a.marker, RenderQueue.UNLIT, F, 1f, 1f, 1f, 1f);
            if (k >= 0) Mat4.setTRS(q.model[k], g.cursor, s.rot, sc, sc, 1f);
        }
    }

    private void menuPointer(RenderQueue q, Assets a) {
        if (!g.menu.cursorValid) return;
        t1.set(g.rayOrigin);
        int k = q.add(a.laser, RenderQueue.UNLIT, RenderQueue.F_ALPHA | RenderQueue.F_NO_FOG, 0.3f, 0.85f, 1f, 0.5f);
        if (k >= 0) Mat4.setSegment(q.model[k], t1, g.menu.cursor, 0.004f, 0.004f, q0, up);
    }

    private void messages(RenderQueue q) {
        if (g.hintTimer > 0f && g.hint != null) {
            g.headWorld.rotate(0f, -0.28f, -1.0f, t1);
            q.addText(g.hint, t1, g.headWorld, 0.03f, 1, 1f, 1f, 0.8f, Math.min(1f, g.hintTimer), RenderQueue.F_OVERLAY);
        }
    }

    private void perf(RenderQueue q) {
        sb.setLength(0);
        sb.append("FPS ").append((int) g.perf.fps).append("  ").append((int) g.perf.frameMs).append(" ms  draw ").append(g.perf.drawCalls);
        g.headWorld.rotate(-0.3f, 0.3f, -1.0f, t1);
        q.addText(sb.toString(), t1, g.headWorld, 0.022f, 0, 0.5f, 1f, 0.5f, 1f, RenderQueue.F_OVERLAY);
    }
}
