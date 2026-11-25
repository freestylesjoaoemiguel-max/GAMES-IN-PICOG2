package com.joaolimz.operationzero.game.ui;

import com.joaolimz.operationzero.game.RenderQueue;
import com.joaolimz.operationzero.game.math.Mat4;
import com.joaolimz.operationzero.game.math.Quat;
import com.joaolimz.operationzero.game.math.Vec3;
import com.joaolimz.operationzero.game.mesh.Assets;

import java.util.ArrayList;

/**
 * World-space VR menu panel with laser pointer (controller) or gaze + dwell selection (works even when
 * no controller button is mapped yet).
 */
public final class Menu {
    public static final class Button {
        public final String id;
        public String label;
        public float x, y, w, h;
        public boolean enabled = true;
        public boolean small;
        Button(String id, String label, float x, float y, float w, float h) {
            this.id = id; this.label = label; this.x = x; this.y = y; this.w = w; this.h = h;
        }
    }

    public String screen = "";
    public String title = "";
    public final ArrayList<String> lines = new ArrayList<String>();
    public final ArrayList<Button> buttons = new ArrayList<Button>();
    public float width = 1.5f, height = 1.15f;
    public final Vec3 center = new Vec3();
    public final Quat rot = new Quat();
    public float yaw;
    public Button hovered;
    public final Vec3 cursor = new Vec3();
    public boolean cursorValid;
    public float dwell;
    public static final float DWELL_TIME = 1.6f;
    public boolean dwellEnabled = true;
    private final Vec3 t0 = new Vec3(), n = new Vec3(), r = new Vec3(), u = new Vec3();
    private final Quat tq = new Quat();

    public void clear(String screenId, String titleText) {
        screen = screenId;
        title = titleText;
        lines.clear();
        buttons.clear();
        hovered = null;
        dwell = 0f;
        height = 1.15f;
    }

    public Button button(String id, String label, float x, float y, float w, float h) {
        Button b = new Button(id, label, x, y, w, h);
        buttons.add(b);
        return b;
    }

    /** Places the panel in front of a viewer at the given yaw. */
    public void place(Vec3 eye, float viewerYaw, float distance) {
        yaw = viewerYaw;
        float fx = -(float) Math.sin(viewerYaw), fz = -(float) Math.cos(viewerYaw);
        center.set(eye.x + fx * distance, eye.y - 0.1f, eye.z + fz * distance);
        rot.setYaw(viewerYaw);
    }

    /** Updates hover state from a pointer ray. Returns the clicked button id (dwell) or null. */
    public String pointer(Vec3 origin, Vec3 dir, float dt, boolean useDwell) {
        rot.rotate(0, 0, 1, n);  // panel normal facing the viewer
        rot.rotate(1, 0, 0, r);
        rot.rotate(0, 1, 0, u);
        float denom = dir.dot(n);
        cursorValid = false;
        Button prev = hovered;
        hovered = null;
        if (denom < -1e-4f) {
            t0.setSub(center, origin);
            float t = t0.dot(n) / denom;
            if (t > 0f && t < 6f) {
                cursor.set(origin).addScaled(dir, t);
                t0.setSub(cursor, center);
                float lx = t0.dot(r), ly = t0.dot(u);
                if (Math.abs(lx) < width * 0.5f + 0.1f && Math.abs(ly) < height * 0.5f + 0.1f) {
                    cursorValid = true;
                    for (int i = 0; i < buttons.size(); i++) {
                        Button b = buttons.get(i);
                        if (!b.enabled) continue;
                        if (lx >= b.x - b.w * 0.5f && lx <= b.x + b.w * 0.5f && ly >= b.y - b.h * 0.5f && ly <= b.y + b.h * 0.5f) {
                            hovered = b;
                            break;
                        }
                    }
                }
            }
        }
        if (hovered != prev) dwell = 0f;
        if (hovered != null && useDwell && dwellEnabled) {
            dwell += dt;
            if (dwell >= DWELL_TIME) {
                dwell = -0.6f; // small cooldown before the next dwell click
                return hovered.id;
            }
        }
        return null;
    }

    public boolean hoverChanged;

    public void render(RenderQueue q, Assets a) {
        int f = RenderQueue.F_ALPHA | RenderQueue.F_NO_FOG | RenderQueue.F_OVERLAY;
        int i = q.add(a.uiQuad, RenderQueue.UNLIT, f, 0.05f, 0.07f, 0.09f, 0.88f);
        if (i >= 0) Mat4.setTRS(q.model[i], center.x, center.y, center.z, rot, width, height, 1f);
        // accent bar
        local(0, height * 0.5f - 0.012f, 0.002f, t0);
        i = q.add(a.uiQuad, RenderQueue.UNLIT, f, 0.88f, 0.48f, 0.12f, 1f);
        if (i >= 0) Mat4.setTRS(q.model[i], t0.x, t0.y, t0.z, rot, width, 0.024f, 1f);
        local(0, height * 0.5f - 0.1f, 0.004f, t0);
        q.addText(title, t0, rot, 0.065f, 1, 1f, 0.75f, 0.35f, 1f, RenderQueue.F_OVERLAY);
        float ly = height * 0.5f - 0.2f;
        for (int k = 0; k < lines.size(); k++) {
            local(-width * 0.5f + 0.06f, ly - k * 0.05f, 0.004f, t0);
            q.addText(lines.get(k), t0, rot, 0.034f, 0, 0.85f, 0.88f, 0.9f, 1f, RenderQueue.F_OVERLAY);
        }
        for (int k = 0; k < buttons.size(); k++) {
            Button b = buttons.get(k);
            boolean hov = b == hovered;
            local(b.x, b.y, 0.003f, t0);
            float cr = hov ? 0.88f : 0.16f, cg = hov ? 0.48f : 0.2f, cb = hov ? 0.12f : 0.25f;
            if (!b.enabled) { cr = 0.1f; cg = 0.11f; cb = 0.12f; }
            i = q.add(a.uiQuad, RenderQueue.UNLIT, f, cr, cg, cb, 0.95f);
            if (i >= 0) Mat4.setTRS(q.model[i], t0.x, t0.y, t0.z, rot, b.w, b.h, 1f);
            local(b.x, b.y - (b.small ? 0.012f : 0.018f), 0.006f, t0);
            float tc = b.enabled ? 1f : 0.45f;
            q.addText(b.label, t0, rot, b.small ? 0.032f : 0.045f, 1, tc, tc, tc, 1f, RenderQueue.F_OVERLAY);
            if (hov && dwell > 0f) {
                float k2 = Math.min(1f, dwell / DWELL_TIME);
                local(b.x - b.w * 0.5f + b.w * k2 * 0.5f, b.y - b.h * 0.5f + 0.006f, 0.007f, t0);
                i = q.add(a.uiQuad, RenderQueue.UNLIT, f, 1f, 1f, 1f, 0.9f);
                if (i >= 0) Mat4.setTRS(q.model[i], t0.x, t0.y, t0.z, rot, b.w * k2, 0.008f, 1f);
            }
        }
        if (cursorValid) {
            tq.set(rot);
            i = q.add(a.uiQuad, RenderQueue.UNLIT, f, 1f, 1f, 1f, 1f);
            if (i >= 0) Mat4.setTRS(q.model[i], cursor.x, cursor.y, cursor.z, tq, 0.016f, 0.016f, 1f);
        }
    }

    private Vec3 local(float x, float y, float z, Vec3 out) {
        rot.rotate(x, y, z, out);
        return out.add(center);
    }
}
