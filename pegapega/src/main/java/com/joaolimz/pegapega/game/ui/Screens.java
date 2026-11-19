package com.joaolimz.pegapega.game.ui;

import com.joaolimz.pegapega.game.Game;
import com.joaolimz.pegapega.game.input.InputFrame;
import com.joaolimz.pegapega.game.save.Settings;
import com.joaolimz.pegapega.game.tag.Avatar;
import com.joaolimz.pegapega.game.tag.TagGame;

/** All menu screens of PEGA-PEGA. */
public final class Screens {
    private final Game g;
    private static final float L = -0.36f, R = 0.36f, BW = 0.66f, BH = 0.105f;
    private int lastKey;

    public Screens(Game g) { this.g = g; }

    public void place() { g.menu.place(g.rig.head, g.headWorld.yaw(), 1.6f); }

    public void open(String id) {
        Menu m = g.menu;
        String prevScreen = m.screen;
        Settings s = g.settings;
        if (id.equals("main")) {
            m.clear("main", "PICO VR: PEGA-PEGA");
            m.lines.add("Braços longos, pega-pega e infecção no Pátio Ferro-Velho");
            m.button("play_inf", "JOGAR: INFECÇÃO", L, 0.24f, BW, BH);
            m.button("play_tag", "JOGAR: PEGA-PEGA", L, 0.1f, BW, BH);
            m.button("play_free", "TREINO LIVRE", L, -0.04f, BW, BH);
            m.button("howto", "COMO JOGAR", L, -0.18f, BW, BH);
            m.button("controls", "CONTROLE PICO", L, -0.32f, BW, BH);
            m.button("game", "PARTIDA (BOTS/COR)", R, 0.24f, BW, BH);
            m.button("move", "MOVIMENTO / CONFORTO", R, 0.1f, BW, BH);
            m.button("graphics", "GRÁFICOS", R, -0.04f, BW, BH);
            m.button("audio", "ÁUDIO", R, -0.18f, BW, BH);
            m.button("quit", "SAIR", R, -0.32f, BW, BH);
        } else if (id.equals("pause")) {
            m.clear("pause", "PAUSA");
            m.lines.add("Modo: " + TagGame.MODE_NAMES[g.tag.mode] + "   Rodada " + g.tag.round);
            m.button("resume", "CONTINUAR", L, 0.18f, BW, BH);
            m.button("restart", "REINICIAR RODADA", L, 0.04f, BW, BH);
            m.button("menu", "MENU PRINCIPAL", L, -0.1f, BW, BH);
            m.button("move", "MOVIMENTO / CONFORTO", R, 0.18f, BW, BH);
            m.button("controls", "CONTROLE PICO", R, 0.04f, BW, BH);
            m.button("graphics", "GRÁFICOS", R, -0.1f, BW, BH);
            m.button("audio", "ÁUDIO", R, -0.24f, BW, BH);
        } else if (id.equals("howto")) {
            m.clear("howto", "COMO JOGAR");
            m.height = 1.3f;
            m.lines.add("Você não anda: você se move com os BRAÇOS, como um bicho de braços longos.");
            m.lines.add("Aponte o controle para baixo até a mão tocar o chão: ela GRUDA.");
            m.lines.add("Puxe o controle para trás/baixo: seu corpo vai para frente.");
            m.lines.add("Empurre forte para baixo e solte: você PULA. Bata nas paredes e puxe: ESCALA.");
            m.lines.add("Os dois braços seguem o controle e o sensor de movimento: empurrões reais do braço contam.");
            m.lines.add("INFECÇÃO: quem é pego vira infectado (roxo). Sobreviva até o tempo acabar.");
            m.lines.add("PEGA-PEGA: o pegador (coroa) passa a vez ao encostar em alguém.");
            m.lines.add("Polegar nos lados do touchpad: girar. Segurar HOME: recentralizar.");
            m.lines.add("Segurar APP: pausa.");
            m.button("back", "VOLTAR", 0f, -0.55f, 0.6f, 0.09f);
        } else if (id.equals("game")) {
            m.clear("game", "PARTIDA");
            opt(m, "bots", "Bots: " + s.botCount, L, 0.24f);
            opt(m, "diff", "Dificuldade: " + (s.difficulty == 0 ? "FÁCIL" : s.difficulty == 1 ? "NORMAL" : "DIFÍCIL"), L, 0.1f);
            opt(m, "color", "Sua cor: " + Avatar.COLOR_NAMES[s.playerColor], R, 0.24f);
            m.button("back", "VOLTAR", 0f, -0.42f, 0.6f, BH);
        } else if (id.equals("move")) {
            m.clear("move", "MOVIMENTO / CONFORTO");
            m.height = 1.3f;
            opt(m, "arms", "Braços: " + (s.armMode == 0 ? "OS DOIS SEGUEM O CONTROLE" : "UM BRAÇO (APP troca)"), L, 0.3f);
            opt(m, "armlen", "Comprimento do braço: " + String.format("%.2f m", s.armLength), L, 0.17f);
            opt(m, "height", "Altura: " + String.format("%.2f m", s.standHeight), L, 0.04f);
            opt(m, "fling", "Multiplicador de pulo: " + String.format("%.2fx", s.flingBoost), L, -0.09f);
            opt(m, "walk", "Andar pelo touchpad: " + (s.touchpadWalk ? "LIGADO" : "DESLIGADO"), L, -0.22f);
            opt(m, "imu", "Sensor de movimento: " + (s.imuEnabled ? "LIGADO" : "DESLIGADO"), L, -0.35f);
            opt(m, "imugain", "Força do sensor: " + String.format("%.1fx", s.imuGain), R, -0.35f);
            opt(m, "climb", "Ajuda na escalada: " + (s.climbAssist ? "LIGADA" : "DESLIGADA"), 0f, -0.48f);
            opt(m, "turn", "Rotação: " + (s.turnMode == 0 ? "SNAP" : "SUAVE"), R, 0.3f);
            opt(m, "snap", "Ângulo snap: " + s.snapAngle + "°", R, 0.17f);
            opt(m, "turnspeed", "Rotação suave: " + (int) s.smoothTurnSpeed + "°/s", R, 0.04f);
            opt(m, "vignette", "Vinheta: " + (s.vignette == 0 ? "DESLIGADA" : s.vignette == 1 ? "FRACA" : "FORTE"), R, -0.09f);
            opt(m, "recenter", "Recentralizar visão", R, -0.22f);
            m.height = 1.45f;
            m.button("back", "VOLTAR", 0f, -0.62f, 0.6f, 0.08f);
        } else if (id.equals("graphics")) {
            m.clear("graphics", "GRÁFICOS");
            m.lines.add("LOW é o recomendado para o Pico G2.");
            opt(m, "quality", "Qualidade: " + Settings.QUALITY_NAMES[s.quality], L, 0.24f);
            opt(m, "lens", "Correção de lente: " + (s.lensDistortion ? "LIGADA" : "DESLIGADA"), L, 0.1f);
            opt(m, "k1", "Curvatura lente: " + String.format("%.2f", s.lensK1), L, -0.04f);
            opt(m, "fov", "Campo de visão: " + (int) s.fovDeg + "°", L, -0.18f);
            opt(m, "ipd", "IPD: " + Math.round(s.ipd * 1000) + " mm", R, 0.24f);
            opt(m, "flip", "Inverter tela 180°: " + (s.flipScreen ? "SIM" : "NÃO"), R, 0.1f);
            opt(m, "perf", "Mostrar FPS: " + (s.showPerf ? "SIM" : "NÃO"), R, -0.04f);
            m.button("back", "VOLTAR", 0f, -0.42f, 0.6f, BH);
        } else if (id.equals("audio")) {
            m.clear("audio", "ÁUDIO");
            opt(m, "vmaster", "Volume geral: " + Math.round(s.master * 100) + "%", 0f, 0.2f);
            opt(m, "vsfx", "Efeitos: " + Math.round(s.sfx * 100) + "%", 0f, 0.06f);
            opt(m, "vamb", "Ambiente e vento: " + Math.round(s.ambient * 100) + "%", 0f, -0.08f);
            m.button("back", "VOLTAR", 0f, -0.42f, 0.6f, BH);
        } else if (id.equals("controls")) {
            m.clear("controls", "CONTROLE PICO G2");
            m.lines.add("Status: " + g.in.ctrlStatus + (s.ctrlCalibrated ? " (calibrado)" : " (não calibrado)"));
            m.lines.add("GATILHO: selecionar | Polegar no touchpad: girar | APP: braço / segurar = pausa");
            m.button("calibrate", "CALIBRAR CONTROLE", L, 0.0f, BW, BH);
            m.button("diag", "DIAGNÓSTICO", R, 0.0f, BW, BH);
            m.button("back", "VOLTAR", 0f, -0.42f, 0.6f, BH);
        } else if (id.equals("calib")) {
            m.clear("calib", "CALIBRAR CONTROLE PICO G2");
            m.height = 1.2f;
            for (int i = 0; i < 5; i++) m.lines.add("");
            m.button("calib_cancel", "CANCELAR", L, -0.42f, BW, BH);
            m.button("calib_skip", "PULAR TOUCHPAD", R, -0.42f, BW, BH);
            live();
        } else if (id.equals("diag")) {
            m.clear("diag", "DIAGNÓSTICO DO CONTROLE");
            m.height = 1.2f;
            for (int i = 0; i < 7; i++) m.lines.add("");
            m.button("calibrate", "CALIBRAR", L, -0.45f, BW, BH);
            m.button("back", "VOLTAR", R, -0.45f, BW, BH);
            live();
        }
        boolean top = id.equals("main") || id.equals("pause");
        if (top || prevScreen.length() == 0) place();
    }

    private Menu.Button opt(Menu m, String id, String label, float x, float y) {
        Menu.Button b = m.button(id, label, x, y, BW, BH);
        b.small = true;
        return b;
    }

    public void live() {
        Menu m = g.menu;
        InputFrame in = g.in;
        if (m.screen.equals("calib") && m.lines.size() >= 5) {
            int st = Math.max(0, g.calibStep);
            m.lines.set(0, st < 6 ? Game.CALIB_TEXT[st] : "Concluído");
            m.lines.set(1, g.calibMessage);
            m.lines.set(2, "Status: " + in.ctrlStatus);
            m.lines.set(3, "Gatilho: " + (in.ctrlTrigger ? "APERTADO" : "solto") + "   Touchpad: " + (in.ctrlTouching ? in.ctrlTouchX + ", " + in.ctrlTouchY : "sem toque"));
            m.lines.set(4, "Botões CANCELAR/PULAR: olhe fixo por 1,6 s.");
        } else if (m.screen.equals("diag") && m.lines.size() >= 7) {
            m.lines.set(0, "Status: " + in.ctrlStatus + "   Bateria: " + in.ctrlBattery);
            m.lines.set(1, "Conectado: " + (in.ctrlConnected ? "SIM" : "NÃO") + "   Calibrado: " + (g.settings.ctrlCalibrated ? "SIM" : "NÃO"));
            m.lines.set(2, String.format("Quaternion: %.2f %.2f %.2f %.2f", in.ctrlX, in.ctrlY, in.ctrlZ, in.ctrlW));
            m.lines.set(3, String.format("Mira: yaw %.0f°  pitch %.0f°", in.controller.yaw() * 57.3f, in.controller.pitch() * 57.3f));
            m.lines.set(4, "Gatilho " + (in.ctrlTrigger ? "SIM" : "não") + " | Clique " + (in.ctrlClick ? "SIM" : "não") + " | APP "
                    + (in.ctrlApp ? "SIM" : "não") + " | HOME " + (in.ctrlHome ? "SIM" : "não"));
            m.lines.set(5, "Touchpad: " + (in.ctrlTouching ? in.ctrlTouchX + ", " + in.ctrlTouchY : "sem toque") + "   tecla Android: " + lastKey);
            m.lines.set(6, in.ctrlHasImu
                    ? String.format("Acelerômetro: %.1f %.1f %.1f   mão +%.0f cm", in.ctrlAccX, in.ctrlAccY, in.ctrlAccZ, g.imu.offset.length() * 100f)
                    : "Acelerômetro: indisponível (só rotação). O laser deve seguir o controle.");
        }
        if (in.lastKeyCode != 0) lastKey = in.lastKeyCode;
    }

    public void click(String id) {
        Settings s = g.settings;
        String screen = g.menu.screen;
        boolean changed = true;
        if (id.equals("play_inf")) { g.startGame(TagGame.INFECTION); return; }
        if (id.equals("play_tag")) { g.startGame(TagGame.CLASSIC); return; }
        if (id.equals("play_free")) { g.startGame(TagGame.FREE); return; }
        if (id.equals("resume")) { g.resume(); return; }
        if (id.equals("restart")) { g.restartRound(); return; }
        if (id.equals("menu")) { g.toMenu(); return; }
        if (id.equals("quit")) { g.platform.quit(); return; }
        if (id.equals("back")) { open(g.state == Game.State.PAUSED ? "pause" : "main"); return; }
        if (id.equals("calibrate")) { g.startCalibration(); return; }
        if (id.equals("calib_cancel")) { g.endCalibration(); return; }
        if (id.equals("calib_skip")) {
            if (g.calibStep >= 3 || s.ctrlCalibrated) g.endCalibration();
            else g.calibMessage = "Primeiro conclua os 3 passos de orientação.";
            return;
        }
        if (id.equals("howto") || id.equals("game") || id.equals("move") || id.equals("graphics") || id.equals("audio")
                || id.equals("controls") || id.equals("diag")) { open(id); return; }
        if (id.equals("bots")) s.botCount = s.botCount >= 9 ? 1 : s.botCount + 1;
        else if (id.equals("diff")) s.difficulty = (s.difficulty + 1) % 3;
        else if (id.equals("color")) s.playerColor = (s.playerColor + 1) % Avatar.COLORS.length;
        else if (id.equals("arms")) s.armMode = 1 - s.armMode;
        else if (id.equals("armlen")) s.armLength = s.armLength >= 1.3f ? 0.8f : s.armLength + 0.1f;
        else if (id.equals("height")) s.standHeight = s.standHeight >= 1.3f ? 0.8f : s.standHeight + 0.1f;
        else if (id.equals("fling")) s.flingBoost = s.flingBoost >= 1.5f ? 1.0f : s.flingBoost + 0.1f;
        else if (id.equals("walk")) s.touchpadWalk = !s.touchpadWalk;
        else if (id.equals("imu")) s.imuEnabled = !s.imuEnabled;
        else if (id.equals("imugain")) s.imuGain = s.imuGain >= 2.5f ? 0.6f : s.imuGain + 0.4f;
        else if (id.equals("climb")) s.climbAssist = !s.climbAssist;
        else if (id.equals("turn")) s.turnMode = 1 - s.turnMode;
        else if (id.equals("snap")) s.snapAngle = s.snapAngle == 30 ? 45 : s.snapAngle == 45 ? 60 : s.snapAngle == 60 ? 90 : 30;
        else if (id.equals("turnspeed")) s.smoothTurnSpeed = s.smoothTurnSpeed >= 180f ? 60f : s.smoothTurnSpeed + 30f;
        else if (id.equals("vignette")) s.vignette = (s.vignette + 1) % 3;
        else if (id.equals("recenter")) { g.recenterAll(); changed = false; }
        else if (id.equals("quality")) s.quality = (s.quality + 1) % 3;
        else if (id.equals("lens")) s.lensDistortion = !s.lensDistortion;
        else if (id.equals("k1")) s.lensK1 = s.lensK1 >= 0.34f ? 0.1f : s.lensK1 + 0.04f;
        else if (id.equals("fov")) s.fovDeg = s.fovDeg >= 105f ? 85f : s.fovDeg + 5f;
        else if (id.equals("ipd")) s.ipd = s.ipd >= 0.0695f ? 0.056f : s.ipd + 0.002f;
        else if (id.equals("flip")) s.flipScreen = !s.flipScreen;
        else if (id.equals("perf")) s.showPerf = !s.showPerf;
        else if (id.equals("vmaster")) s.master = vol(s.master);
        else if (id.equals("vsfx")) s.sfx = vol(s.sfx);
        else if (id.equals("vamb")) s.ambient = vol(s.ambient);
        else changed = false;
        if (changed) {
            g.applySettings();
            g.saveSettings();
            if (id.equals("color")) g.me.color = s.playerColor;
        }
        open(screen);
    }

    private static float vol(float v) {
        float n = Math.round(v * 10f) / 10f + 0.1f;
        return n > 1.001f ? 0f : n;
    }
}
