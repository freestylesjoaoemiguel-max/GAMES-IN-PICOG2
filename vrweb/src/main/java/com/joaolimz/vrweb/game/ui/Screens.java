package com.joaolimz.vrweb.game.ui;

import com.joaolimz.vrweb.game.Game;
import com.joaolimz.vrweb.game.input.InputFrame;
import com.joaolimz.vrweb.game.save.Settings;

/** In-VR settings screens. */
public final class Screens {
    private final Game g;
    private static final float L = -0.36f, R = 0.36f, BW = 0.66f, BH = 0.1f;
    private int lastKey;

    public Screens(Game g) { this.g = g; }

    public void place() { g.menu.place(g.eye, 0f, 1.1f); }

    public void open(String id) {
        Menu m = g.menu;
        Settings s = g.settings;
        if (id.equals("main")) {
            m.clear("main", "MENU");
            m.height = 1.4f;
            m.lines.add("Navegador VR (Discord web oficial). Gatilho = clicar.");
            m.lines.add("Motor: " + (g.host != null ? g.host.engineInfo() : "?"));
            m.lines.add("Dica: se a verificação (captcha) falhar, entre pelo QR code do Discord com o app do celular.");
            float y = 0.3f, st = 0.125f;
            opt(m, "close", "VOLTAR AO NAVEGADOR", L, y);
            opt(m, "reload", "Recarregar página", L, y - st);
            opt(m, "home", "Ir para o Discord / início", L, y - 2 * st);
            opt(m, "url", "Abrir endereço / buscar", L, y - 3 * st);
            opt(m, "controls", "Controle Pico (calibrar)", L, y - 4 * st);
            opt(m, "graphics", "Gráficos", L, y - 5 * st);
            opt(m, "size", "Tamanho da tela: " + String.format("%.1f m", s.panelWidth), R, y);
            opt(m, "dist", "Distância: " + String.format("%.1f m", s.panelDistance), R, y - st);
            opt(m, "zoom", "Zoom da página: " + Math.round(s.zoom * 100) + "%", R, y - 2 * st);
            opt(m, "ua", "Modo do site: " + (s.desktopUa ? "COMPUTADOR" : "PADRÃO"), R, y - 3 * st);
            opt(m, "scroll", "Rolagem: " + String.format("%.1fx", s.scrollSpeed), R, y - 4 * st);
            opt(m, "quality", "Qualidade da tela: " + Settings.QUALITY_NAMES[s.quality], R, y - 5 * st);
            boolean mic = g.host != null && g.host.micGranted();
            m.button("mic", mic ? "Microfone: PERMITIDO" : "Microfone: PERMITIR", -0.5f, -0.5f, 0.46f, 0.09f).small = true;
            m.button("logout", "Limpar login e dados", 0f, -0.5f, 0.46f, 0.09f).small = true;
            m.button("quit", "SAIR", 0.5f, -0.5f, 0.46f, 0.09f).small = true;
        } else if (id.equals("graphics")) {
            m.clear("graphics", "GRÁFICOS");
            m.lines.add("LOW é o recomendado para o Pico G2.");
            opt(m, "lens", "Correção de lente: " + (s.lensDistortion ? "LIGADA" : "DESLIGADA"), L, 0.24f);
            opt(m, "k1", "Curvatura lente: " + String.format("%.2f", s.lensK1), L, 0.1f);
            opt(m, "fov", "Campo de visão: " + (int) s.fovDeg + "°", L, -0.04f);
            opt(m, "ipd", "IPD: " + Math.round(s.ipd * 1000) + " mm", R, 0.24f);
            opt(m, "flip", "Inverter tela 180°: " + (s.flipScreen ? "SIM" : "NÃO"), R, 0.1f);
            opt(m, "perf", "Mostrar FPS: " + (s.showPerf ? "SIM" : "NÃO"), R, -0.04f);
            m.button("back", "VOLTAR", 0f, -0.42f, 0.6f, BH);
        } else if (id.equals("controls")) {
            m.clear("controls", "CONTROLE PICO G2");
            m.lines.add("Status: " + g.in.ctrlStatus + (s.ctrlCalibrated ? " (calibrado)" : " (não calibrado)"));
            m.lines.add("GATILHO: clicar | Touchpad (encostar e deslizar): rolar a página");
            m.lines.add("APP: teclado | APP segurado: menu | HOME segurado: recentralizar");
            m.button("calibrate", "CALIBRAR CONTROLE", L, 0.0f, BW, BH);
            m.button("diag", "DIAGNÓSTICO", R, 0.0f, BW, BH);
            m.button("recenter", "RECENTRALIZAR", L, -0.14f, BW, BH);
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
            m.lines.set(6, "O laser deve seguir o controle. Se não, use CALIBRAR.");
        }
        if (in.lastKeyCode != 0) lastKey = in.lastKeyCode;
    }

    public void click(String id) {
        Settings s = g.settings;
        String screen = g.menu.screen;
        boolean browserChanged = false, changed = true;
        if (id.equals("close")) { g.state = Game.State.BROWSE; return; }
        if (id.equals("back")) { open("main"); return; }
        if (id.equals("reload")) { if (g.host != null) g.host.reload(); g.state = Game.State.BROWSE; return; }
        if (id.equals("home")) { if (g.host != null) g.host.loadUrl(s.homeUrl); g.state = Game.State.BROWSE; return; }
        if (id.equals("url")) { g.startUrlInput(); return; }
        if (id.equals("controls") || id.equals("graphics") || id.equals("diag")) { open(id); return; }
        if (id.equals("calibrate")) { g.startCalibration(); return; }
        if (id.equals("calib_cancel")) { g.endCalibration(); return; }
        if (id.equals("calib_skip")) {
            if (g.calibStep >= 3 || s.ctrlCalibrated) g.endCalibration();
            else g.calibMessage = "Primeiro conclua os 3 passos de orientação.";
            return;
        }
        if (id.equals("recenter")) { g.recenterAll(); return; }
        if (id.equals("mic")) { if (g.host != null) g.host.requestMic(); open(screen); return; }
        if (id.equals("logout")) { if (g.host != null) { g.host.clearData(); g.host.loadUrl(s.homeUrl); } g.state = Game.State.BROWSE; g.showHint("Login e dados apagados", 3f); return; }
        if (id.equals("quit")) { g.rememberUrl(); g.platform.quit(); return; }
        if (id.equals("size")) { s.panelWidth = s.panelWidth >= 4.0f ? 1.6f : s.panelWidth + 0.3f; g.layout(); }
        else if (id.equals("dist")) { s.panelDistance = s.panelDistance >= 3.5f ? 1.2f : s.panelDistance + 0.3f; g.layout(); }
        else if (id.equals("zoom")) { s.zoom = s.zoom >= 1.5f ? 0.7f : Math.round((s.zoom + 0.1f) * 10f) / 10f; browserChanged = true; }
        else if (id.equals("ua")) { s.desktopUa = !s.desktopUa; browserChanged = true; }
        else if (id.equals("scroll")) s.scrollSpeed = s.scrollSpeed >= 2.5f ? 0.5f : s.scrollSpeed + 0.5f;
        else if (id.equals("quality")) { s.quality = (s.quality + 1) % 3; browserChanged = true; }
        else if (id.equals("lens")) s.lensDistortion = !s.lensDistortion;
        else if (id.equals("k1")) s.lensK1 = s.lensK1 >= 0.34f ? 0.1f : s.lensK1 + 0.04f;
        else if (id.equals("fov")) s.fovDeg = s.fovDeg >= 105f ? 85f : s.fovDeg + 5f;
        else if (id.equals("ipd")) s.ipd = s.ipd >= 0.0695f ? 0.056f : s.ipd + 0.002f;
        else if (id.equals("flip")) s.flipScreen = !s.flipScreen;
        else if (id.equals("perf")) s.showPerf = !s.showPerf;
        else changed = false;
        if (changed) {
            if (browserChanged) { g.applyBrowserSettings(); if (id.equals("ua") && g.host != null) g.host.reload(); }
            g.saveSettings();
        }
        open(screen);
    }
}
