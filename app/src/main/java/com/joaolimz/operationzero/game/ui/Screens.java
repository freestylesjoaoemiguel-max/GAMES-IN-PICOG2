package com.joaolimz.operationzero.game.ui;

import com.joaolimz.operationzero.game.Game;
import com.joaolimz.operationzero.game.mission.MissionSystem;
import com.joaolimz.operationzero.game.save.SaveData;
import com.joaolimz.operationzero.game.save.Settings;

/** Builds every menu screen (main menu, missions, settings, controls, comfort, graphics, audio...). */
public final class Screens {
    private final Game g;
    private String parent = "main";

    public Screens(Game g) { this.g = g; }

    private static final float COL_L = -0.36f, COL_R = 0.36f, BW = 0.66f, BH = 0.105f;

    public void open(String id) {
        Menu m = g.menu;
        String prevScreen = m.screen;
        Settings s = g.settings;
        if (id.equals("main") || id.equals("pause")) parent = id;
        if (id.equals("main")) {
            m.clear("main", "PICO VR: OPERATION ZERO");
            m.lines.add("Complexo Vantor-7  |  campanha single-player offline");
            float y = 0.24f;
            m.button("play", "JOGAR", COL_L, y, BW, BH);
            m.button("continue", "CONTINUAR", COL_L, y - 0.14f, BW, BH).enabled = g.save != null && g.save.mission < MissionSystem.COUNT;
            m.button("missions", "MISSÕES", COL_L, y - 0.28f, BW, BH);
            m.button("settings", "CONFIGURAÇÕES", COL_L, y - 0.42f, BW, BH);
            m.button("controls", "CONTROLES", COL_L, y - 0.56f, BW, BH);
            m.button("comfort", "CONFORTO VR", COL_R, y, BW, BH);
            m.button("graphics", "GRÁFICOS", COL_R, y - 0.14f, BW, BH);
            m.button("audio", "ÁUDIO", COL_R, y - 0.28f, BW, BH);
            m.button("quit", "SAIR", COL_R, y - 0.56f, BW, BH);
            m.lines.add("Mire com a cabeça/controle. GATILHO ou olhar fixo seleciona.");
        } else if (id.equals("pause")) {
            m.clear("pause", "PAUSA");
            m.lines.add("Objetivo: " + g.missions.objective());
            m.lines.add(g.missions.hint().length() > 70 ? g.missions.hint().substring(0, 70) + "..." : g.missions.hint());
            float y = 0.12f;
            m.button("resume", "CONTINUAR", COL_L, y, BW, BH);
            m.button("restart", "RECOMEÇAR CHECKPOINT", COL_L, y - 0.14f, BW, BH);
            m.button("settings", "CONFIGURAÇÕES", COL_L, y - 0.28f, BW, BH);
            m.button("controls", "CONTROLES", COL_L, y - 0.42f, BW, BH);
            m.button("comfort", "CONFORTO VR", COL_R, y, BW, BH);
            m.button("graphics", "GRÁFICOS", COL_R, y - 0.14f, BW, BH);
            m.button("audio", "ÁUDIO", COL_R, y - 0.28f, BW, BH);
            m.button("mainmenu", "MENU PRINCIPAL", COL_R, y - 0.42f, BW, BH);
        } else if (id.equals("dead")) {
            m.clear("dead", "VOCÊ MORREU");
            m.lines.add("Os agentes da Vantor te derrubaram.");
            m.lines.add("Objetivo atual: " + g.missions.objective());
            m.button("restart", "RECOMEÇAR DO CHECKPOINT", 0f, 0.0f, 0.9f, BH);
            m.button("mainmenu", "MENU PRINCIPAL", 0f, -0.16f, 0.9f, BH);
        } else if (id.equals("victory")) {
            m.clear("victory", "MISSÃO CUMPRIDA");
            int sec = (int) g.playTime;
            m.lines.add("Você escapou do Complexo Vantor-7 com o dispositivo ZERO.");
            m.lines.add("Tempo: " + (sec / 60) + " min " + (sec % 60) + " s   |   Abates: " + g.player.kills);
            m.lines.add("Obrigado por jogar PICO VR: OPERATION ZERO!");
            m.button("play", "JOGAR NOVAMENTE", 0f, -0.08f, 0.9f, BH);
            m.button("mainmenu", "MENU PRINCIPAL", 0f, -0.24f, 0.9f, BH);
        } else if (id.equals("missions")) {
            m.clear("missions", "MISSÕES");
            int reached = g.save != null ? g.save.mission : 0;
            int mask = g.save != null ? g.save.completedMask : 0;
            for (int i = 0; i < MissionSystem.COUNT; i++) {
                boolean done = (mask & (1 << i)) != 0;
                String mark = done ? "  [OK]" : (i == reached ? "  [ATUAL]" : "");
                Menu.Button b = m.button("mission" + i, (i + 1) + ". " + MissionSystem.TITLES[i] + mark, 0f, 0.3f - i * 0.12f, 1.3f, 0.095f);
                b.small = true;
                b.enabled = i <= reached || done;
            }
            m.button("back", "VOLTAR", 0f, -0.46f, 0.6f, BH);
        } else if (id.equals("settings")) {
            m.clear("settings", "CONFIGURAÇÕES");
            m.lines.add("Rastreamento: " + g.platform.trackingStatus());
            float y = 0.26f;
            opt(m, "hand", "Mão dominante: " + (s.rightHanded ? "DIREITA" : "ESQUERDA"), COL_L, y);
            opt(m, "grab", "Pegar com: " + (s.grabMode == 0 ? "GATILHO" : "BOTÃO GRIP"), COL_L, y - 0.13f);
            opt(m, "assist", "Recarga assistida: " + onOff(s.reloadAssist), COL_L, y - 0.26f);
            opt(m, "height", "Altura: " + String.format("%.2f m", s.playerHeight), COL_L, y - 0.39f);
            opt(m, "aim", "Mirar com: " + (s.aimSource == 0 ? "CONTROLE" : "CABEÇA"), COL_R, y - 0.39f);
            opt(m, "movedir", "Andar para: " + (s.moveDirection == 0 ? "ONDE OLHA" : "ONDE APONTA"), COL_L, y - 0.52f);
            opt(m, "calibrate", "Calibrar controle Pico", COL_R, y - 0.52f);
            m.height = 1.3f;
            opt(m, "perf", "Mostrar desempenho: " + onOff(s.showPerf), COL_R, y);
            opt(m, "recenter", "Recentralizar visão", COL_R, y - 0.13f);
            opt(m, "erase", "Apagar progresso", COL_R, y - 0.26f).enabled = g.save != null;
            m.button("back", "VOLTAR", 0f, -0.56f, 0.6f, 0.09f);
        } else if (id.equals("controls")) {
            m.clear("controls", "CONTROLES");
            m.lines.add("Pico G2: mire com o CONTROLE | GATILHO pega/atira | clique no TOUCHPAD: ejetar/engatilhar");
            m.lines.add("Polegar no touchpad: cima/baixo anda, esq/dir gira | APP: trocar mão | HOME segurar: recentralizar");
            m.lines.add("Controle: " + g.in.ctrlStatus + (g.settings.ctrlCalibrated ? " (calibrado)" : " (não calibrado)"));
            float y = 0.2f;
            for (int i = 0; i < Settings.ACTIONS; i++) {
                float x = i < 5 ? COL_L : COL_R;
                float yy = y - (i < 5 ? i : i - 5) * 0.115f;
                Menu.Button b = m.button("remap" + i, Settings.ACTION_NAMES[i] + ": " + keys(s.keys[i]), x, yy, BW, 0.095f);
                b.small = true;
            }
            m.button("resetkeys", "RESTAURAR PADRÃO", COL_R, y - 4 * 0.115f, BW, 0.095f).small = true;
            m.height = 1.32f;
            m.button("calibrate", "CALIBRAR CONTROLE PICO", COL_L, -0.42f, BW, 0.1f).small = true;
            m.button("diag", "DIAGNÓSTICO DO CONTROLE", COL_R, -0.42f, BW, 0.1f).small = true;
            m.button("back", "VOLTAR", 0f, -0.56f, 0.6f, 0.09f);
        } else if (id.equals("remap")) {
            m.clear("remap", "REMAPEAR: " + Settings.ACTION_NAMES[g.remapAction]);
            m.lines.add("Pressione agora o botão desejado no controle ou gamepad.");
            m.lines.add("(aguardando tecla...)");
        } else if (id.equals("comfort")) {
            m.clear("comfort", "CONFORTO VR");
            float y = 0.3f;
            opt(m, "turn", "Rotação: " + (s.turnMode == 0 ? "SNAP TURN" : "SMOOTH TURN"), COL_L, y);
            opt(m, "snap", "Ângulo snap: " + s.snapAngle + "°", COL_L, y - 0.13f);
            opt(m, "turnspeed", "Rotação suave: " + (int) s.smoothTurnSpeed + "°/s", COL_L, y - 0.26f);
            opt(m, "move", "Locomoção: " + (s.moveMode == 0 ? "SMOOTH" : "TELEPORTE"), COL_L, y - 0.39f);
            opt(m, "speed", "Velocidade: " + String.format("%.1f m/s", s.moveSpeed), COL_R, y);
            opt(m, "vignette", "Vinheta: " + (s.vignette == 0 ? "DESLIGADA" : s.vignette == 1 ? "FRACA" : "FORTE"), COL_R, y - 0.13f);
            opt(m, "sens", "Sensibilidade: " + String.format("%.2f", s.sensitivity), COL_R, y - 0.26f);
            opt(m, "camrecoil", "Recoil de câmera: " + onOff(s.cameraRecoil), COL_R, y - 0.39f);
            m.button("back", "VOLTAR", 0f, -0.42f, 0.6f, BH);
        } else if (id.equals("graphics")) {
            m.clear("graphics", "GRÁFICOS");
            m.lines.add("LOW é o recomendado para o Pico G2.");
            float y = 0.24f;
            opt(m, "quality", "Qualidade: " + Settings.QUALITY_NAMES[s.quality], COL_L, y);
            opt(m, "lens", "Correção de lente: " + onOff(s.lensDistortion), COL_L, y - 0.13f);
            opt(m, "k1", "Curvatura lente: " + String.format("%.2f", s.lensK1), COL_L, y - 0.26f);
            opt(m, "fov", "Campo de visão: " + (int) s.fovDeg + "°", COL_L, y - 0.39f);
            opt(m, "ipd", "IPD: " + Math.round(s.ipd * 1000f) + " mm", COL_R, y);
            opt(m, "flip", "Inverter tela 180°: " + onOff(s.flipScreen), COL_R, y - 0.13f);
            opt(m, "perf", "Mostrar FPS: " + onOff(s.showPerf), COL_R, y - 0.26f);
            m.button("back", "VOLTAR", 0f, -0.42f, 0.6f, BH);
        } else if (id.equals("audio")) {
            m.clear("audio", "ÁUDIO");
            float y = 0.2f;
            opt(m, "vmaster", "Volume geral: " + Math.round(s.master * 100) + "%", 0f, y);
            opt(m, "vsfx", "Efeitos: " + Math.round(s.sfx * 100) + "%", 0f, y - 0.14f);
            opt(m, "vamb", "Ambiente: " + Math.round(s.ambient * 100) + "%", 0f, y - 0.28f);
            m.button("back", "VOLTAR", 0f, -0.42f, 0.6f, BH);
        } else if (id.equals("calib")) {
            m.clear("calib", "CALIBRAR CONTROLE PICO G2");
            m.height = 1.2f;
            for (int i = 0; i < 6; i++) m.lines.add("");
            m.button("calib_cancel", "CANCELAR", COL_L, -0.42f, BW, BH);
            m.button("calib_skip", "PULAR TOUCHPAD", COL_R, -0.42f, BW, BH);
            live();
        } else if (id.equals("diag")) {
            m.clear("diag", "DIAGNÓSTICO DO CONTROLE");
            m.height = 1.2f;
            for (int i = 0; i < 9; i++) m.lines.add("");
            m.button("calibrate", "CALIBRAR", COL_L, -0.45f, BW, BH);
            m.button("back", "VOLTAR", COL_R, -0.45f, BW, BH);
            live();
        } else if (id.equals("confirm_erase")) {
            m.clear("confirm_erase", "APAGAR PROGRESSO?");
            m.lines.add("O checkpoint salvo será removido.");
            m.button("erase_yes", "SIM, APAGAR", COL_L, -0.1f, BW, BH);
            m.button("settings", "CANCELAR", COL_R, -0.1f, BW, BH);
        }
        boolean subScreen = !id.equals("main") && !id.equals("pause") && !id.equals("dead") && !id.equals("victory");
        if (!subScreen || !prevScreen.equals(id) && prevScreen.length() == 0) place();
    }

    /** Re-centers the panel in front of the player's current view. */
    public void place() {
        g.menu.place(g.player.eye, g.player.head.yaw(), 1.6f);
    }

    /** Refreshes the live text of the calibration / diagnostics screens (called every frame). */
    public void live() {
        Menu m = g.menu;
        com.joaolimz.operationzero.game.input.InputFrame in = g.in;
        if (m.screen.equals("calib") && m.lines.size() >= 6) {
            int st = Math.max(0, g.calibStep);
            m.lines.set(0, st < 6 ? Game.CALIB_TEXT[st] : "Concluído");
            m.lines.set(1, g.calibMessage);
            m.lines.set(2, "Status: " + in.ctrlStatus);
            m.lines.set(3, "Gatilho: " + (in.ctrlTrigger ? "APERTADO" : "solto") + "   Touchpad: "
                    + (in.ctrlTouching ? in.ctrlTouchX + ", " + in.ctrlTouchY : "sem toque"));
            m.lines.set(4, "Botões CANCELAR/PULAR: olhe fixo por 1,6 s.");
            m.lines.set(5, "");
        } else if (m.screen.equals("diag") && m.lines.size() >= 9) {
            com.joaolimz.operationzero.game.math.Quat q = in.controller;
            m.lines.set(0, "Status: " + in.ctrlStatus);
            m.lines.set(1, "Conectado: " + (in.ctrlConnected ? "SIM" : "NÃO") + "   Bateria: " + in.ctrlBattery
                    + "   Calibrado: " + (g.settings.ctrlCalibrated ? "SIM" : "NÃO"));
            m.lines.set(2, String.format("Quaternion bruto: %.2f %.2f %.2f %.2f", in.ctrlX, in.ctrlY, in.ctrlZ, in.ctrlW));
            m.lines.set(3, String.format("Mira do controle: yaw %.0f°  pitch %.0f°   cabeça: yaw %.0f°",
                    q.yaw() * 57.3f, q.pitch() * 57.3f, in.head.yaw() * 57.3f));
            m.lines.set(4, "Gatilho " + (in.ctrlTrigger ? "SIM" : "não") + " | Clique " + (in.ctrlClick ? "SIM" : "não")
                    + " | APP " + (in.ctrlApp ? "SIM" : "não") + " | HOME " + (in.ctrlHome ? "SIM" : "não"));
            m.lines.set(5, "Touchpad: " + (in.ctrlTouching ? in.ctrlTouchX + ", " + in.ctrlTouchY : "sem toque")
                    + String.format("   andar %.2f  girar %.2f", in.moveY, in.turnX));
            m.lines.set(6, "Última tecla Android: " + lastKey + "   (gatilho/ação por tecla: "
                    + (in.trigger ? "G " : "") + (in.action ? "A " : "") + ")");
            m.lines.set(7, "Aponte o controle: o cursor branco deve seguir a mão.");
            m.lines.set(8, "Se a mira estiver errada, use CALIBRAR.");
        }
        if (in.lastKeyCode != 0) lastKey = in.lastKeyCode;
    }

    private int lastKey;

    private Menu.Button opt(Menu m, String id, String label, float x, float y) {
        Menu.Button b = m.button(id, label, x, y, BW, BH);
        b.small = true;
        return b;
    }

    private static String onOff(boolean v) { return v ? "LIGADO" : "DESLIGADO"; }

    private static String keys(int[] k) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < k.length && i < 3; i++) { if (i > 0) sb.append('/'); sb.append(k[i]); }
        return sb.toString();
    }

    public void click(String id) {
        Settings s = g.settings;
        String screen = g.menu.screen;
        boolean changed = true;
        if (id.equals("play")) { g.newGame(); return; }
        if (id.equals("continue")) { g.continueGame(); return; }
        if (id.equals("resume")) { g.resume(); return; }
        if (id.equals("restart")) { g.restartCheckpoint(); return; }
        if (id.equals("mainmenu")) { g.toMainMenu(); return; }
        if (id.equals("quit")) { g.platform.quit(); return; }
        if (id.equals("back")) { open(g.state == Game.State.PAUSED ? "pause" : "main"); return; }
        if (id.startsWith("mission")) { g.startMission(Integer.parseInt(id.substring(7))); return; }
        if (id.startsWith("remap")) { g.beginRemap(Integer.parseInt(id.substring(5))); open("remap"); return; }
        if (id.equals("calibrate")) { g.startCalibration(); return; }
        if (id.equals("calib_cancel")) { g.cancelCalibration(); return; }
        if (id.equals("calib_skip")) {
            if (g.calibStep >= 3 || g.settings.ctrlCalibrated) { g.calibStep = -1; open(g.state == Game.State.PAUSED ? "pause" : "main"); }
            else g.calibMessage = "Primeiro conclua os 3 passos de orientação.";
            return;
        }
        if (id.equals("diag")) { open("diag"); return; }
        if (id.equals("resetkeys")) { s.keys = Settings.copyKeys(Settings.DEFAULT_KEYS); }
        else if (id.equals("aim")) { s.aimSource = 1 - s.aimSource; }
        else if (id.equals("movedir")) { s.moveDirection = 1 - s.moveDirection; }
        else if (id.equals("hand")) { s.rightHanded = !s.rightHanded; }
        else if (id.equals("grab")) { s.grabMode = 1 - s.grabMode; }
        else if (id.equals("assist")) { s.reloadAssist = !s.reloadAssist; }
        else if (id.equals("height")) { s.playerHeight = s.playerHeight >= 1.9f ? 1.5f : s.playerHeight + 0.1f; }
        else if (id.equals("perf")) { s.showPerf = !s.showPerf; }
        else if (id.equals("recenter")) { g.recenterAll(); changed = false; }
        else if (id.equals("erase")) { open("confirm_erase"); return; }
        else if (id.equals("erase_yes")) {
            g.platform.deleteFile(SaveData.FILE);
            g.save = null;
            open(parent);
            return;
        }
        else if (id.equals("turn")) { s.turnMode = 1 - s.turnMode; }
        else if (id.equals("snap")) { s.snapAngle = s.snapAngle == 30 ? 45 : s.snapAngle == 45 ? 60 : s.snapAngle == 60 ? 90 : 30; }
        else if (id.equals("turnspeed")) { s.smoothTurnSpeed = s.smoothTurnSpeed >= 180f ? 60f : s.smoothTurnSpeed + 30f; }
        else if (id.equals("move")) { s.moveMode = 1 - s.moveMode; }
        else if (id.equals("speed")) { s.moveSpeed = s.moveSpeed >= 3.0f ? 1.5f : s.moveSpeed + 0.5f; }
        else if (id.equals("vignette")) { s.vignette = (s.vignette + 1) % 3; }
        else if (id.equals("sens")) { s.sensitivity = s.sensitivity >= 2f ? 0.5f : s.sensitivity < 1f ? s.sensitivity + 0.25f : s.sensitivity + 0.5f; }
        else if (id.equals("camrecoil")) { s.cameraRecoil = !s.cameraRecoil; }
        else if (id.equals("quality")) { s.quality = (s.quality + 1) % 3; }
        else if (id.equals("lens")) { s.lensDistortion = !s.lensDistortion; }
        else if (id.equals("k1")) { s.lensK1 = s.lensK1 >= 0.34f ? 0.1f : s.lensK1 + 0.04f; }
        else if (id.equals("fov")) { s.fovDeg = s.fovDeg >= 105f ? 85f : s.fovDeg + 5f; }
        else if (id.equals("ipd")) { s.ipd = s.ipd >= 0.0695f ? 0.056f : s.ipd + 0.002f; }
        else if (id.equals("flip")) { s.flipScreen = !s.flipScreen; }
        else if (id.equals("vmaster")) { s.master = cycleVol(s.master); }
        else if (id.equals("vsfx")) { s.sfx = cycleVol(s.sfx); }
        else if (id.equals("vamb")) { s.ambient = cycleVol(s.ambient); }
        else if (id.equals("settings") || id.equals("controls") || id.equals("comfort") || id.equals("graphics")
                || id.equals("audio") || id.equals("missions")) { open(id); return; }
        else changed = false;
        if (changed) {
            g.applySettings();
            g.saveSettings();
        }
        open(screen);
    }

    private static float cycleVol(float v) {
        float n = Math.round(v * 10f) / 10f + 0.1f;
        return n > 1.001f ? 0f : n;
    }
}
