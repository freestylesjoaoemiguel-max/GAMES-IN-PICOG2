package com.joaolimz.operationzero.game;

import com.joaolimz.operationzero.game.math.Mat4;
import com.joaolimz.operationzero.game.math.MathUtil;
import com.joaolimz.operationzero.game.math.Quat;
import com.joaolimz.operationzero.game.math.Vec3;
import com.joaolimz.operationzero.game.mesh.Assets;
import com.joaolimz.operationzero.game.player.Hand;
import com.joaolimz.operationzero.game.player.Player;
import com.joaolimz.operationzero.game.save.Settings;
import com.joaolimz.operationzero.game.weapons.Magazine;
import com.joaolimz.operationzero.game.weapons.Weapon;
import com.joaolimz.operationzero.game.weapons.WeaponType;
import com.joaolimz.operationzero.game.world.Door;
import com.joaolimz.operationzero.game.world.Interactive;
import com.joaolimz.operationzero.game.world.Level;
import com.joaolimz.operationzero.game.world.Prop;
import com.joaolimz.operationzero.game.world.Sector;

/** Converts the game state into a {@link RenderQueue} (culling by sector + distance, LOD, HUD, menus). */
public final class SceneBuilder {
    private final Game g;
    private final Vec3 t0 = new Vec3(), t1 = new Vec3(), t2 = new Vec3(), up = new Vec3(0, 1, 0);
    private final Quat q0 = new Quat(), q1 = new Quat();
    private final StringBuilder sb = new StringBuilder();

    public SceneBuilder(Game g) { this.g = g; }

    public void build(RenderQueue q) {
        Player p = g.player;
        Assets a = g.assets;
        Level L = g.level;
        Settings s = g.settings;
        q.clear();
        q.headPos.set(p.eye);
        q.headRot.set(p.head);
        environment(q, s);

        float dd = s.drawDistance();
        // static geometry of active sectors (sector PVS = occlusion culling)
        for (int i = 0; i < L.sectors.size(); i++) {
            Sector sec = L.sectors.get(i);
            if (!sec.active) continue;
            for (int k = 0; k < sec.staticMeshes.size(); k++) q.addStatic(sec.staticMeshes.get(k));
        }
        props(q, a, L, dd);
        doors(q, a, L);
        interactives(q, a, L);
        signsAndTargets(q, a, L, dd);
        g.npcs.render(q, a, p.eye, dd);
        g.fx.render(q, a);
        if (g.state != Game.State.MENU) body(q, a, p);
        if (g.state == Game.State.PLAYING) {
            teleport(q, a);
            hud(q, a, p, s);
        } else {
            g.menu.render(q, a);
            pointerLaser(q, a);
            toast(q);
        }
        if (s.showPerf) perfOverlay(q);
        q.vignette = g.vignette;
        q.damageFlash = MathUtil.clamp(p.hurtTimer / 0.6f, 0f, 1f) * 0.6f + (p.alive && p.health < 30f ? 0.15f : 0f);
        q.fadeBlack = Math.max(g.fade, g.state == Game.State.DEAD ? 0.45f : 0f);
    }

    private void environment(RenderQueue q, Settings s) {
        Sector cur = g.sectors.currentSector();
        boolean sky = false;
        for (int i = 0; i < g.level.sectors.size(); i++) {
            Sector sec = g.level.sectors.get(i);
            if (sec.active && sec.outdoor) sky = true;
        }
        q.drawSky = sky;
        q.fogColor[0] = cur.fogR; q.fogColor[1] = cur.fogG; q.fogColor[2] = cur.fogB;
        q.clearColor[0] = cur.fogR; q.clearColor[1] = cur.fogG; q.clearColor[2] = cur.fogB;
        q.fogEnd = s.drawDistance();
        q.fogStart = q.fogEnd * (cur.outdoor ? 0.5f : 0.4f);
        q.ambient[0] = cur.ambR; q.ambient[1] = cur.ambG; q.ambient[2] = cur.ambB;
        float l = cur.outdoor ? 0.75f : 0.45f;
        q.lightColor[0] = l; q.lightColor[1] = l * 0.97f; q.lightColor[2] = l * 0.92f;
        if (g.flags.contains("alarm") && !cur.outdoor) {
            float pulse = 0.5f + 0.5f * (float) Math.sin(g.time * 6f);
            q.ambient[0] += 0.25f * pulse;
        }
    }

    private void props(RenderQueue q, Assets a, Level L, float dd) {
        Player p = g.player;
        for (int i = 0; i < L.props.size(); i++) {
            Prop pr = L.props.get(i);
            if (!pr.active || pr.mesh < 0) continue;
            boolean attached = pr instanceof Magazine && ((Magazine) pr).attachedTo != null;
            if (!pr.body.enabled && !attached) continue;
            if (!pr.isHeld() && !pr.isStored() && !g.sectors.isActive(pr.body.sector)) continue;
            float d = pr.body.pos.distance(p.eye);
            if (d > dd) continue;
            float hl = pr.highlight > 0f ? 1.6f : 1f;
            int idx = q.add(pr.mesh, RenderQueue.LIT, 0, hl, hl, hl, 1f);
            if (idx < 0) return;
            Mat4.setTRS(q.model[idx], pr.body.pos, pr.body.rot, pr.meshScale);
            if (pr instanceof Weapon) weaponParts(q, a, (Weapon) pr);
            // blob shadow for loose objects nearby
            if (d < 6f && !pr.isHeld() && !pr.isStored() && pr.body.hy > 0.1f) {
                q0.identity();
                int k = q.add(a.blob, RenderQueue.UNLIT, RenderQueue.F_ALPHA, 0, 0, 0, 1);
                if (k >= 0) Mat4.setTRS(q.model[k], pr.body.pos.x, pr.body.pos.y - pr.body.hy + 0.01f, pr.body.pos.z, q0,
                        pr.body.radiusXZ() * 1.4f, 1f, pr.body.radiusXZ() * 1.4f);
            }
        }
    }

    private void weaponParts(RenderQueue q, Assets a, Weapon w) {
        float back = 0f;
        int mesh = -1;
        if (w.type == WeaponType.VX9) {
            mesh = a.pistolSlide;
            back = (w.slideLocked ? 1f : w.actionAnim) * 0.03f;
        } else if (w.type == WeaponType.BRUTUS) {
            mesh = a.shotgunPump;
            back = w.actionAnim * 0.08f;
        }
        if (mesh >= 0) {
            w.body.rot.rotate(0f, 0f, back, t0).add(w.body.pos);
            int idx = q.add(mesh, RenderQueue.LIT, 0, 1, 1, 1, 1);
            if (idx >= 0) Mat4.setTRS(q.model[idx], t0, w.body.rot, 1f);
        }
        if (w.isHeld()) {
            // digital ammo counter on the back of the weapon
            sb.setLength(0);
            if (w.type.usesMagazine()) {
                if (w.mag == null) sb.append("--"); else sb.append(w.mag.rounds);
                if (w.chambered) sb.append("+1");
            } else {
                sb.append(w.tubeRounds);
                if (w.chambered) sb.append("+1");
            }
            float y = w.type == WeaponType.VX9 ? 0.06f : 0.09f;
            w.body.rot.rotate(0f, y, 0.04f, t0).add(w.body.pos);
            q0.setAxisAngle(1, 0, 0, -0.5f);
            q1.setMul(w.body.rot, q0);
            boolean empty = w.totalLoaded() == 0;
            q.addText(sb.toString(), t0, q1, 0.02f, 1, empty ? 1f : 0.5f, empty ? 0.3f : 1f, empty ? 0.25f : 0.7f, 1f, 0);
        }
    }

    private void doors(RenderQueue q, Assets a, Level L) {
        for (int i = 0; i < L.doors.size(); i++) {
            Door d = L.doors.get(i);
            if (!g.sectors.isActive(d.sector)) continue;
            if (d.type == Door.HINGED) {
                q0.setYaw(d.hinge.baseYaw + d.hinge.angle);
                float tint = d.locked ? 0.8f : 1f;
                int idx = q.add(d.mesh, RenderQueue.LIT, 0, tint, tint, tint, 1);
                if (idx >= 0) Mat4.setTRS(q.model[idx], d.hinge.hingeX, d.hinge.y0, d.hinge.hingeZ, q0, d.hinge.length, d.height, 0.08f);
                for (int side = -1; side <= 1; side += 2) {
                    d.handlePos(t0, side);
                    idx = q.add(a.doorHandle, RenderQueue.LIT, 0, 1, 1, 1, 1);
                    if (idx >= 0) Mat4.setTRS(q.model[idx], t0, q0, 1f);
                }
                if (d.locked) {
                    // red lock light
                    d.handlePos(t0, 1f);
                    t0.y += 0.12f;
                    idx = q.add(a.spark, RenderQueue.UNLIT, 0, 1f, 0.2f, 0.15f, 1f);
                    if (idx >= 0) Mat4.setTRS(q.model[idx], t0, q0, 1.6f);
                }
            } else {
                if (!d.slab.enabled && d.open >= 0.999f && d.travel.y == 0f) continue;
                com.joaolimz.operationzero.game.math.Aabb b = d.slab.box;
                q0.identity();
                int idx = q.add(d.mesh, RenderQueue.LIT, 0, 1, 1, 1, 1);
                if (idx >= 0) Mat4.setTRS(q.model[idx], b.centerX(), b.centerY(), b.centerZ(), q0, b.maxX - b.minX, b.maxY - b.minY, Math.max(0.12f, b.maxZ - b.minZ));
            }
        }
    }

    private void interactives(RenderQueue q, Assets a, Level L) {
        for (int i = 0; i < L.interactives.size(); i++) {
            Interactive it = L.interactives.get(i);
            if (!g.sectors.isActive(it.sector)) continue;
            q0.setYaw(it.yaw);
            int mesh = it.mesh;
            if (it.type == Interactive.BUTTON) mesh = it.enabled ? a.buttonGreen : a.buttonRed;
            float hl = g.player.activeHand().pointInteractive == it ? 1.5f : 1f;
            int idx = q.add(mesh, RenderQueue.LIT, 0, hl, hl, hl, 1);
            if (idx < 0) continue;
            float push = it.type == Interactive.BUTTON ? Math.max(0f, it.pressAnim) * 0.012f : 0f;
            q0.rotate(0f, 0f, push, t0).add(it.pos);
            Mat4.setTRS(q.model[idx], t0, q0, 1f);
            if (it.type == Interactive.LEVER) {
                q1.setAxisAngle(1, 0, 0, -it.leverAngle);
                q1.preMul(q0);
                q0.rotate(0f, 0f, -0.06f, t0).add(it.pos);
                idx = q.add(it.mesh2, RenderQueue.LIT, 0, hl, hl, hl, 1);
                if (idx >= 0) Mat4.setTRS(q.model[idx], t0, q1, 1f);
                if (it.label != null) label(q, it, it.label, 0.3f);
            } else if (it.type == Interactive.TERMINAL && it.screen != null) {
                q1.setYaw(it.yaw + MathUtil.PI);
                for (int k = 0; k < it.screen.length; k++) {
                    q0.rotate(0f, 1.1f - k * 0.05f, -0.085f, t0).add(it.pos);
                    q.addText(it.screen[k], t0, q1, 0.03f, 1, 0.35f, 1f, 0.55f, 1f, 0);
                }
            } else if (it.type == Interactive.READER) {
                float r = 0.9f, gg = 0.6f, b = 0.1f;
                if (it.okFlash > 0f || !it.enabled) { r = 0.2f; gg = 1f; b = 0.3f; }
                if (it.deniedFlash > 0f) { r = 1f; gg = 0.15f; b = 0.1f; }
                q0.rotate(0f, 0.03f, -0.026f, t0).add(it.pos);
                idx = q.add(a.spark, RenderQueue.UNLIT, 0, r, gg, b, 1f);
                if (idx >= 0) Mat4.setTRS(q.model[idx], t0, q0, 2.2f);
                label(q, it, it.label, 0.14f);
            } else if (it.type == Interactive.BUTTON && it.label != null) {
                label(q, it, it.label, 0.12f);
            }
        }
    }

    private void label(RenderQueue q, Interactive it, String text, float above) {
        q1.setYaw(it.yaw + MathUtil.PI);
        q0.setYaw(it.yaw);
        q0.rotate(0f, above, -0.06f, t0).add(it.pos);
        q.addText(text, t0, q1, 0.035f, 1, 1f, 0.85f, 0.4f, 1f, 0);
    }

    private void signsAndTargets(RenderQueue q, Assets a, Level L, float dd) {
        for (int i = 0; i < L.signs.size(); i++) {
            Level.Sign sg = L.signs.get(i);
            if (!g.sectors.isActive(sg.sector) || sg.pos.distance(g.player.eye) > dd * 0.8f) continue;
            q0.setYaw(sg.yaw + MathUtil.PI);
            int c = sg.color;
            q.addText(sg.text, sg.pos, q0, sg.size, 1, ((c >> 16) & 255) / 255f, ((c >> 8) & 255) / 255f, (c & 255) / 255f, 1f, 0);
        }
        for (int i = 0; i < L.targets.size(); i++) {
            Level.Target t = L.targets.get(i);
            if (!g.sectors.isActive(t.sector)) continue;
            q0.setYaw(t.yaw);
            q1.setAxisAngle(1, 0, 0, -t.down * 1.45f);
            q0.mul(q1);
            int idx = q.add(a.target, RenderQueue.LIT, 0, 1, 1, 1, 1);
            if (idx >= 0) Mat4.setTRS(q.model[idx], t.pos.x, t.pos.y - 1.2f, t.pos.z, q0, 1f);
        }
    }

    private void body(RenderQueue q, Assets a, Player p) {
        // torso / legs (visible when looking down)
        p.neck(t0);
        q0.setYaw(p.torsoYaw);
        q0.rotate(0f, 0f, 0.1f, t1);
        t0.add(t1);
        int idx = q.add(a.playerTorso, RenderQueue.LIT, 0, 1, 1, 1, 1);
        if (idx >= 0) Mat4.setTRS(q.model[idx], t0, q0, 1f);
        q1.identity();
        idx = q.add(a.blob, RenderQueue.UNLIT, RenderQueue.F_ALPHA, 0, 0, 0, 1);
        if (idx >= 0) Mat4.setTRS(q.model[idx], p.feet.x, p.feet.y + 0.01f, p.feet.z, q1, 0.4f, 1f, 0.4f);
        for (int i = 0; i < 2; i++) {
            Hand h = p.hands[i];
            // arm segments
            int m = q.add(a.armUpper, RenderQueue.LIT, 0, 1, 1, 1, 1);
            if (m >= 0) Mat4.setSegment(q.model[m], h.shoulder, h.elbow, 0.09f, 0.09f, q1, up);
            h.rot.rotate(0f, 0f, 0.07f, t1).add(h.pos);
            m = q.add(a.armFore, RenderQueue.LIT, 0, 1, 1, 1, 1);
            if (m >= 0) Mat4.setSegment(q.model[m], h.elbow, t1, 0.075f, 0.075f, q1, up);
            float hl = h.controlled ? 1f : 0.85f;
            m = q.add(a.handMesh(h.right, h.pose), RenderQueue.LIT, 0, hl, hl, hl, 1);
            if (m >= 0) Mat4.setTRS(q.model[m], h.pos, h.rot, 1f);
        }
        // inventory slot markers when the active hand is close (helps finding holsters)
        Hand ah = p.activeHand();
        for (int sIdx = 0; sIdx <= 4; sIdx++) {
            com.joaolimz.operationzero.game.player.Inventory.zone(sIdx, p.eye, p.torso, g.settings.rightHanded, t0);
            float d = ah.pos.distance(t0);
            float r = com.joaolimz.operationzero.game.player.Inventory.radius(sIdx);
            if (d > r * 2.2f) continue;
            boolean inside = d < r;
            q1.set(p.head);
            int k = q.add(a.marker, RenderQueue.UNLIT, RenderQueue.F_ALPHA | RenderQueue.F_NO_CULL, inside ? 0.4f : 1f, 1f, inside ? 0.4f : 1f, inside ? 0.9f : 0.4f);
            q0.setAxisAngle(1, 0, 0, MathUtil.PI * 0.5f);
            q1.mul(q0);
            if (k >= 0) Mat4.setTRS(q.model[k], t0, q1, 0.35f);
            if (inside) {
                q1.set(p.head);
                t0.y += 0.07f;
                q.addText(com.joaolimz.operationzero.game.player.Inventory.NAMES[sIdx], t0, q1, 0.022f, 1, 1f, 1f, 1f, 1f, RenderQueue.F_OVERLAY);
            }
        }
        pointerLaser(q, a);
    }

    private void pointerLaser(RenderQueue q, Assets a) {
        Hand h = g.player.activeHand();
        boolean menu = g.state != Game.State.PLAYING;
        Vec3 target = null;
        if (menu) {
            if (!g.in.hasControllerPose || !g.menu.cursorValid) return;
            target = g.menu.cursor;
        } else if (h.pointProp != null) target = h.pointProp.body.pos;
        else if (h.pointInteractive != null) target = h.pointInteractive.pos;
        if (target == null) return;
        int k = q.add(a.laser, RenderQueue.UNLIT, RenderQueue.F_ALPHA | RenderQueue.F_NO_FOG, 0.3f, 0.85f, 1f, 0.45f);
        if (k >= 0) Mat4.setSegment(q.model[k], h.pos, target, 0.004f, 0.004f, q1, up);
    }

    private void teleport(RenderQueue q, Assets a) {
        Interaction it = g.interaction;
        if (!it.teleportAiming) return;
        float r = it.teleportValid ? 0.3f : 1f, gg = it.teleportValid ? 0.85f : 0.3f, b = it.teleportValid ? 1f : 0.3f;
        q0.identity();
        for (int i = 0; i < it.arcCount; i++) {
            int k = q.add(a.arcDot, RenderQueue.UNLIT, RenderQueue.F_NO_FOG, r, gg, b, 1f);
            if (k >= 0) Mat4.setTRS(q.model[k], it.arc[i], q0, 0.025f);
        }
        if (it.teleportValid) {
            int k = q.add(a.marker, RenderQueue.UNLIT, RenderQueue.F_ALPHA | RenderQueue.F_NO_CULL, 1, 1, 1, 1);
            if (k >= 0) Mat4.setTRS(q.model[k], it.teleportTarget.x, it.teleportTarget.y + 0.02f, it.teleportTarget.z, q0, 1f);
        }
    }

    /** Minimal HUD: belt display (health, weapon, ammo, objective) + short head-locked messages. */
    private void hud(RenderQueue q, Assets a, Player p, Settings s) {
        q0.setYaw(p.torsoYaw);
        q0.rotate(0f, -0.52f, -0.42f, t0).add(p.eye);
        q1.setAxisAngle(1, 0, 0, -0.88f);
        q1.preMul(q0);
        int f = RenderQueue.F_OVERLAY;
        int bg = q.add(a.uiQuad, RenderQueue.UNLIT, RenderQueue.F_ALPHA | f, 0.03f, 0.05f, 0.07f, 0.65f);
        if (bg >= 0) Mat4.setTRS(q.model[bg], t0, q1, 0.4f, 0.13f, 1f);
        float hp = p.health;
        sb.setLength(0);
        sb.append("VIDA ").append((int) hp);
        q1.rotate(-0.18f, 0.035f, 0.002f, t1).add(t0);
        q.addText(sb.toString(), t1, q1, 0.026f, 0, hp < 30 ? 1f : 0.6f, hp < 30 ? 0.3f : 1f, hp < 30 ? 0.25f : 0.6f, 1f, f);
        // health bar
        q1.rotate(-0.18f + 0.09f * hp / 100f, 0.015f, 0.002f, t1).add(t0);
        int hb = q.add(a.uiQuad, RenderQueue.UNLIT, RenderQueue.F_ALPHA | f, hp < 30 ? 1f : 0.3f, hp < 30 ? 0.2f : 0.85f, 0.3f, 0.9f);
        if (hb >= 0) Mat4.setTRS(q.model[hb], t1, q1, 0.18f * hp / 100f, 0.008f, 1f);
        // weapon + ammo
        Hand ah = p.activeHand(), oh = p.otherHand();
        Weapon w = ah.held instanceof Weapon ? (Weapon) ah.held : oh.held instanceof Weapon ? (Weapon) oh.held : null;
        sb.setLength(0);
        if (w != null) {
            sb.append(w.type.name).append("  ").append(w.totalLoaded()).append(" | ").append(p.inventory.reserve[w.type.ammo]);
        } else {
            sb.append("SEM ARMA");
        }
        q1.rotate(0.02f, 0.035f, 0.002f, t1).add(t0);
        q.addText(sb.toString(), t1, q1, 0.024f, 0, 1f, 0.85f, 0.5f, 1f, f);
        sb.setLength(0);
        sb.append("OBJ: ").append(g.missions.objective());
        q1.rotate(-0.18f, -0.035f, 0.002f, t1).add(t0);
        q.addText(sb.toString(), t1, q1, 0.021f, 0, 0.85f, 0.9f, 1f, 1f, f);
        sb.setLength(0);
        sb.append("MAO ATIVA: ").append(ah.right ? "DIREITA" : "ESQUERDA");
        q1.rotate(-0.18f, -0.06f, 0.002f, t1).add(t0);
        q.addText(sb.toString(), t1, q1, 0.016f, 0, 0.6f, 0.65f, 0.7f, 1f, f);
        toast(q);
        if (g.interaction.prompt != null) {
            p.head.rotate(0f, -0.3f, -1.0f, t1).add(p.eye);
            q.addText(g.interaction.prompt, t1, p.head, 0.026f, 1, 0.8f, 0.95f, 1f, 0.9f, f);
        }
    }

    private void toast(RenderQueue q) {
        Player p = g.player;
        int f = RenderQueue.F_OVERLAY;
        if (g.missions.toastTimer > 0f && g.missions.toast != null) {
            p.head.rotate(0f, 0.16f, -1.0f, t1).add(p.eye);
            float al = Math.min(1f, g.missions.toastTimer);
            q.addText(g.missions.toast, t1, p.head, 0.04f, 1, 1f, 0.75f, 0.3f, al, f);
        }
        if (g.hintTimer > 0f && g.hint != null) {
            p.head.rotate(0f, -0.2f, -1.0f, t1).add(p.eye);
            float al = Math.min(1f, g.hintTimer);
            q.addText(g.hint, t1, p.head, 0.03f, 1, 1f, 1f, 1f, al, f);
        }
    }

    private void perfOverlay(RenderQueue q) {
        Player p = g.player;
        int f = RenderQueue.F_OVERLAY;
        com.joaolimz.operationzero.game.perf.PerfMonitor pm = g.perf;
        sb.setLength(0);
        sb.append("FPS ").append((int) pm.fps).append("  ").append((int) pm.frameMs).append(" ms  upd ").append((int) (pm.updateMs * 10) / 10f).append(" ms");
        p.head.rotate(-0.3f, 0.3f, -1.0f, t1).add(p.eye);
        q.addText(sb.toString(), t1, p.head, 0.022f, 0, 0.5f, 1f, 0.5f, 1f, f);
        sb.setLength(0);
        sb.append("OBJ ").append(pm.props).append(" fis ").append(pm.activeBodies).append("  NPC ").append(pm.activeNpcs).append('/').append(pm.aliveNpcs)
                .append("  rag ").append(pm.ragdolls).append("  setores ").append(pm.activeSectors);
        p.head.rotate(-0.3f, 0.27f, -1.0f, t1).add(p.eye);
        q.addText(sb.toString(), t1, p.head, 0.02f, 0, 0.5f, 1f, 0.5f, 1f, f);
        sb.setLength(0);
        sb.append("draw ").append(pm.drawCalls).append("  tri ").append(pm.triangles).append("  ").append(Settings.QUALITY_NAMES[g.settings.quality]);
        p.head.rotate(-0.3f, 0.24f, -1.0f, t1).add(p.eye);
        q.addText(sb.toString(), t1, p.head, 0.02f, 0, 0.5f, 1f, 0.5f, 1f, f);
    }
}
