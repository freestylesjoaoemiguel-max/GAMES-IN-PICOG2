package com.joaolimz.vrweb.game;

import com.joaolimz.vrweb.game.input.ControllerMapping;
import com.joaolimz.vrweb.game.input.InputFrame;
import com.joaolimz.vrweb.game.input.Platform;
import com.joaolimz.vrweb.game.math.MathUtil;
import com.joaolimz.vrweb.game.math.Quat;
import com.joaolimz.vrweb.game.math.Vec3;
import com.joaolimz.vrweb.game.mesh.Assets;
import com.joaolimz.vrweb.game.mesh.MeshLibrary;
import com.joaolimz.vrweb.game.perf.PerfMonitor;
import com.joaolimz.vrweb.game.save.Settings;
import com.joaolimz.vrweb.game.ui.Menu;
import com.joaolimz.vrweb.game.ui.Screens;
import com.joaolimz.vrweb.game.ui.Surface;
import com.joaolimz.vrweb.game.ui.VrKeyboard;
import com.joaolimz.vrweb.game.web.WebHost;

import java.net.URLEncoder;
import java.util.ArrayList;

/**
 * VR web browser for the Pico G2: a floating browser screen (the official Discord web app by default), a laser
 * pointer driven by the 3DoF controller, touchpad scrolling, a navigation bar and a full on-screen keyboard.
 * Platform independent (the Android WebView is behind {@link WebHost}).
 */
public final class Game implements VrKeyboard.Output {
    public enum State { BROWSE, MENU }

    public static final int KEYCODE_DEL = 67, KEYCODE_ENTER = 66;
    public static final int HOVER_NONE = 0, HOVER_PANEL = 1, HOVER_KEY = 2, HOVER_TOOL = 3;

    /** A navigation bar button. */
    public static final class Btn {
        public final String id;
        public String label;
        public float x, y, w, h;
        Btn(String id, String label) { this.id = id; this.label = label; }
        public boolean contains(float lx, float ly) { return Math.abs(lx - x) <= w * 0.5f && Math.abs(ly - y) <= h * 0.5f; }
    }

    public final Settings settings;
    public final Platform platform;
    public WebHost host;
    public final MeshLibrary meshes = new MeshLibrary();
    public final Assets assets;
    public final PerfMonitor perf = new PerfMonitor();
    public final Menu menu = new Menu();
    public final Screens screens;
    public final SceneBuilder scene;

    // world: the user sits at the origin; surfaces are placed in front (-Z)
    public final Surface panel = new Surface(2.6f, 1.625f);
    public final Surface toolbar = new Surface(2.6f, 0.3f);
    public final VrKeyboard keyboard = new VrKeyboard();
    public final ArrayList<Btn> buttons = new ArrayList<Btn>();
    public boolean keyboardVisible = true;
    public boolean urlMode;
    public final StringBuilder urlBuffer = new StringBuilder();

    public State state = State.BROWSE;
    public final InputFrame in = new InputFrame();
    public final InputFrame prev = new InputFrame();
    public final Vec3 eye = new Vec3();
    public final Quat headWorld = new Quat();
    public final Quat aim = new Quat();
    public final Vec3 rayOrigin = new Vec3();
    public final Vec3 rayDir = new Vec3(0, 0, -1);

    // pointer state
    public int hoverKind;
    public VrKeyboard.Key hoverKey;
    public Btn hoverBtn;
    public final Vec3 cursor = new Vec3();
    public boolean cursorValid;
    public float panelPx, panelPy;
    private boolean touching;
    private float lastSentX = -1, lastSentY = -1;
    private VrKeyboard.Key pressedKey;
    private float keyHeld, keyRepeat;
    public float flash;
    public String hint;
    public float hintTimer;
    public float time;

    // touchpad scrolling
    private boolean padWasTouching;
    private float padPrevFwd;

    // buttons
    private float appHeld;
    private boolean appConsumed;
    private float homeHeld;

    // controller + calibration
    public final ControllerMapping ctrlMap = new ControllerMapping();
    private boolean ctrlAligned, calibOffered, ctrlWasConnected;
    private float realignTimer = -1f;
    public int calibStep = -1;
    public String calibMessage = "";
    private final float[][] calibRaw = new float[3][4];
    private float calibHeadYaw;
    private final float[][] calibTouch = new float[3][2];
    private String calibReturn = "main";
    private final Quat tq = new Quat(), yawQ = new Quat();
    private final Vec3 t0 = new Vec3(), t1 = new Vec3();
    private final float[] hit = new float[2];
    private int lastTexW, lastTexH;

    public Game(Settings settings, Platform platform) {
        this.settings = settings;
        this.platform = platform;
        this.assets = new Assets(meshes);
        this.screens = new Screens(this);
        this.scene = new SceneBuilder(this);
        keyboard.output = this;
        loadMapping();
        layout();
        showHint("Aponte o controle e aperte o GATILHO para clicar. Touchpad: rolar. APP: teclado.", 6f);
    }

    public void setHost(WebHost h) {
        host = h;
        applyBrowserSettings();
        h.loadUrl(settings.lastUrl != null && settings.lastUrl.length() > 0 ? settings.lastUrl : settings.homeUrl);
    }

    public void applyBrowserSettings() {
        lastTexW = settings.texW();
        lastTexH = settings.texH();
        if (host != null) host.configure(lastTexW, lastTexH, settings.zoom, settings.desktopUa);
        layout();
    }

    public void saveSettings() { platform.writeFile(Settings.FILE, settings.serialize()); }

    public void rememberUrl() {
        if (host != null && host.url() != null && host.url().startsWith("http")) {
            settings.lastUrl = host.url();
            saveSettings();
        }
    }

    private void loadMapping() {
        System.arraycopy(settings.ctrlPw, 0, ctrlMap.pw, 0, 9);
        System.arraycopy(settings.ctrlPb, 0, ctrlMap.pb, 0, 9);
        ctrlMap.inverse = settings.ctrlInverse;
    }

    public void showHint(String t, float s) { hint = t; hintTimer = s; }

    // ================================================================== layout
    public float panelHeight() { return settings.panelWidth * settings.texH() / (float) settings.texW(); }

    /** Positions the screen, toolbar and keyboard in front of the user. */
    public void layout() {
        float w = settings.panelWidth, h = panelHeight();
        float d = settings.panelDistance;
        panel.w = w; panel.h = h;
        panel.center.set(0f, 0.05f, -d);
        panel.rot.identity();
        toolbar.w = w; toolbar.h = 0.3f;
        toolbar.center.set(0f, 0.05f + h / 2 + 0.2f, -d);
        toolbar.rot.identity();
        // keyboard: a desk-like tilted plane below the screen, closer to the hands
        keyboard.surface.w = Math.min(1.9f, w * 0.85f + 0.4f);
        float ks = keyboard.surface.w / 1.9f;
        keyboard.surface.h = 0.62f * ks;
        keyboard.surface.center.set(0f, -0.62f, -1.25f);
        keyboard.surface.rot.setAxisAngle(1, 0, 0, -0.95f);
        buildButtons();
    }

    private void buildButtons() {
        buttons.clear();
        String[][] defs = {{"back", "< Voltar"}, {"fwd", "Frente >"}, {"reload", "Recarregar"}, {"home", "Início"},
                {"zoomm", "A-"}, {"zoomp", "A+"}, {"kbd", keyboardVisible ? "Teclado: SIM" : "Teclado: NÃO"}, {"menu", "Menu"}};
        float bw = (toolbar.w - 0.12f) / defs.length;
        for (int i = 0; i < defs.length; i++) {
            Btn b = new Btn(defs[i][0], defs[i][1]);
            b.w = bw - 0.015f; b.h = 0.12f;
            b.x = -toolbar.w / 2 + 0.06f + bw * (i + 0.5f);
            b.y = -0.07f;
            buttons.add(b);
        }
    }

    // ================================================================== keyboard output
    @Override public void text(String s) {
        if (urlMode) urlBuffer.append(s);
        else if (host != null) host.typeText(s);
    }

    @Override public void backspace() {
        if (urlMode) { if (urlBuffer.length() > 0) urlBuffer.setLength(urlBuffer.length() - 1); }
        else if (host != null) host.key(KEYCODE_DEL);
    }

    @Override public void enter() {
        if (urlMode) {
            urlMode = false;
            String u = normalizeUrl(urlBuffer.toString());
            urlBuffer.setLength(0);
            if (u != null && host != null) host.loadUrl(u);
        } else if (host != null) {
            host.key(KEYCODE_ENTER);
        }
    }

    public static String normalizeUrl(String in) {
        String s = in.trim();
        if (s.length() == 0) return null;
        if (s.startsWith("http://") || s.startsWith("https://")) return s;
        if (s.indexOf(' ') < 0 && s.indexOf('.') > 0) return "https://" + s;
        try {
            return "https://www.google.com/search?q=" + URLEncoder.encode(s, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            return null;
        }
    }

    public void startUrlInput() {
        urlMode = true;
        urlBuffer.setLength(0);
        keyboardVisible = true;
        state = State.BROWSE;
        buildButtons();
        showHint("Digite o endereço (ou uma busca) e aperte Enter", 5f);
    }

    // ================================================================== controller
    public void recenterAll() {
        platform.recenter();
        realignTimer = 0.15f;
        showHint("Visão e controle recentralizados", 2.5f);
    }

    private void processController(float dt) {
        if (!in.ctrlConnected) {
            ctrlWasConnected = false;
            return;
        }
        if (!ctrlWasConnected) {
            ctrlWasConnected = true;
            ctrlAligned = false;
            if (!settings.ctrlCalibrated && !calibOffered) { calibOffered = true; startCalibration(); }
        }
        if (realignTimer >= 0f) {
            realignTimer -= dt;
            if (realignTimer < 0f) ctrlAligned = false;
        }
        if (!ctrlAligned) {
            ctrlMap.alignYaw(in.ctrlX, in.ctrlY, in.ctrlZ, in.ctrlW, in.head.yaw());
            ctrlAligned = true;
        }
        ctrlMap.apply(in.ctrlX, in.ctrlY, in.ctrlZ, in.ctrlW, in.controller);
        in.hasControllerPose = true;
        in.trigger |= in.ctrlTrigger;
        in.action |= in.ctrlClick;
        in.switchHand |= in.ctrlApp;
        if (in.ctrlHome) {
            homeHeld += dt;
            if (homeHeld > 0.9f && homeHeld - dt <= 0.9f) recenterAll();
        } else homeHeld = 0f;
    }

    public static final String[] CALIB_TEXT = {
            "1/6  Aponte o controle para FRENTE (para onde você olha) e aperte o GATILHO",
            "2/6  Aponte o controle para CIMA (para o teto) e aperte o GATILHO",
            "3/6  Aponte o controle para a sua DIREITA e aperte o GATILHO",
            "4/6  Encoste o polegar no CENTRO do touchpad (sem clicar) e aperte o GATILHO",
            "5/6  Encoste o polegar na parte de CIMA do touchpad e aperte o GATILHO",
            "6/6  Encoste o polegar na parte DIREITA do touchpad e aperte o GATILHO"
    };

    public void startCalibration() {
        calibReturn = state == State.MENU ? "main" : "none";
        calibStep = 0;
        calibMessage = "";
        state = State.MENU;
        screens.open("calib");
    }

    public void endCalibration() {
        calibStep = -1;
        if (calibReturn.equals("none")) state = State.BROWSE; else screens.open(calibReturn);
    }

    private void updateCalibration() {
        boolean press = in.ctrlTrigger && !prev.ctrlTrigger;
        if (!in.ctrlConnected) { calibMessage = "Controle não conectado: " + in.ctrlStatus; return; }
        if (!press) return;
        if (calibStep <= 2) {
            float[] r = calibRaw[calibStep];
            r[0] = in.ctrlX; r[1] = in.ctrlY; r[2] = in.ctrlZ; r[3] = in.ctrlW;
            if (calibStep == 0) calibHeadYaw = in.head.yaw();
            if (calibStep == 2) {
                float err = ctrlMap.solve(calibRaw[0], calibRaw[1], calibRaw[2]);
                if (err > 1.4f) {
                    loadMapping();
                    calibStep = 0;
                    calibMessage = "Não reconheci os movimentos. Vamos repetir.";
                    return;
                }
                ctrlMap.alignYaw(calibRaw[0][0], calibRaw[0][1], calibRaw[0][2], calibRaw[0][3], calibHeadYaw);
                ctrlAligned = true;
                System.arraycopy(ctrlMap.pw, 0, settings.ctrlPw, 0, 9);
                System.arraycopy(ctrlMap.pb, 0, settings.ctrlPb, 0, 9);
                settings.ctrlInverse = ctrlMap.inverse;
                settings.ctrlCalibrated = true;
                saveSettings();
                calibMessage = "Orientação calibrada! Agora o touchpad.";
            }
            calibStep++;
            return;
        }
        if (!in.ctrlTouching) { calibMessage = "Encoste o polegar no touchpad (sem clicar) enquanto aperta o gatilho."; return; }
        float[] t = calibTouch[calibStep - 3];
        t[0] = in.ctrlTouchX; t[1] = in.ctrlTouchY;
        calibStep++;
        calibMessage = "";
        if (calibStep == 6) {
            float cx = calibTouch[0][0], cy = calibTouch[0][1];
            boolean ok = d2(calibTouch[1], cx, cy) > 100f && d2(calibTouch[2], cx, cy) > 100f;
            if (ok) {
                settings.touchCX = cx; settings.touchCY = cy;
                settings.touchFX = calibTouch[1][0]; settings.touchFY = calibTouch[1][1];
                settings.touchRX = calibTouch[2][0]; settings.touchRY = calibTouch[2][1];
                saveSettings();
                showHint("Controle calibrado!", 3f);
                endCalibration();
            } else {
                calibStep = 3;
                calibMessage = "Os toques ficaram muito próximos. Repita o touchpad.";
            }
        }
    }

    private static float d2(float[] p, float cx, float cy) { return (p[0] - cx) * (p[0] - cx) + (p[1] - cy) * (p[1] - cy); }

    // ================================================================== update
    public void update(float dt, InputFrame input) {
        dt = MathUtil.clamp(dt, 0f, 0.05f);
        time += dt;
        prev.copyFrom(in);
        in.copyFrom(input);
        processController(dt);
        if (in.recenter && !prev.recenter) recenterAll();
        if (hintTimer > 0f) hintTimer -= dt;
        if (flash > 0f) flash -= dt;
        headWorld.set(in.head);
        eye.set(0f, 0f, 0f);
        keyboard.tick(dt);
        if (settings.texW() != lastTexW) applyBrowserSettings();

        buttons(dt);
        computeRay(dt);
        if (state == State.MENU) updateMenu(dt); else updateBrowse(dt);
        perf.activeNpcs = 0;
    }

    private void buttons(float dt) {
        if (in.switchHand) {
            appHeld += dt;
            if (appHeld > 0.8f && !appConsumed) {
                appConsumed = true;
                toggleMenu();
            }
        } else {
            if (prev.switchHand && !appConsumed) toggleKeyboard();
            appHeld = 0f;
            appConsumed = false;
        }
        if (in.menu && !prev.menu) toggleMenu();
    }

    public void toggleKeyboard() {
        keyboardVisible = !keyboardVisible;
        if (!keyboardVisible) urlMode = false;
        buildButtons();
    }

    public void toggleMenu() {
        if (state == State.MENU) {
            if (calibStep >= 0) { endCalibration(); return; }
            state = State.BROWSE;
        } else {
            state = State.MENU;
            screens.open("main");
            screens.place();
        }
    }

    private void computeRay(float dt) {
        if (in.hasControllerPose) {
            // slight smoothing removes sensor jitter without adding noticeable lag
            if (aimInit) aim.slerp(in.controller, MathUtil.damp(45f, dt)); else { aim.set(in.controller); aimInit = true; }
            aim.forward(rayDir);
            rayOrigin.set(0.2f, -0.3f, -0.1f);
        } else {
            headWorld.forward(rayDir);
            rayOrigin.set(0f, 0f, 0f);
            aimInit = false;
        }
    }

    private boolean aimInit;

    // ---------------------------------------------------------------- browsing
    private void updateBrowse(float dt) {
        hoverKind = HOVER_NONE; hoverKey = null; hoverBtn = null; cursorValid = false;
        float bestT = Float.MAX_VALUE;
        float margin = touching ? 10f : 0.02f;
        float t;
        // keyboard
        if (keyboardVisible && !touching) {
            t = keyboard.surface.hit(rayOrigin, rayDir, 0.03f, hit);
            if (t > 0f && t < bestT) {
                VrKeyboard.Key k = keyboard.keyAt(hit[0], hit[1]);
                if (k != null) {
                    bestT = t; hoverKind = HOVER_KEY; hoverKey = k;
                    keyboard.surface.toWorld(hit[0], hit[1], 0.004f, cursor);
                    cursorValid = true;
                }
            }
        }
        // toolbar
        if (!touching) {
            t = toolbar.hit(rayOrigin, rayDir, 0.02f, hit);
            if (t > 0f && t < bestT) {
                Btn hb = null;
                for (int i = 0; i < buttons.size(); i++) if (buttons.get(i).contains(hit[0], hit[1])) hb = buttons.get(i);
                if (hb != null) {
                    bestT = t; hoverKind = HOVER_TOOL; hoverBtn = hb;
                    toolbar.toWorld(hit[0], hit[1], 0.004f, cursor);
                    cursorValid = true;
                }
            }
        }
        // page
        t = panel.hit(rayOrigin, rayDir, margin, hit);
        if (t > 0f && (t < bestT || touching)) {
            hoverKind = HOVER_PANEL; hoverKey = null; hoverBtn = null;
            float tw = settings.texW(), th = settings.texH();
            panelPx = MathUtil.clamp((hit[0] / panel.w + 0.5f) * tw, 0f, tw - 1f);
            panelPy = MathUtil.clamp((0.5f - hit[1] / panel.h) * th, 0f, th - 1f);
            panel.toWorld(MathUtil.clamp(hit[0], -panel.w / 2, panel.w / 2), MathUtil.clamp(hit[1], -panel.h / 2, panel.h / 2), 0.004f, cursor);
            cursorValid = true;
        }

        boolean down = in.trigger && !prev.trigger;
        boolean up = !in.trigger && prev.trigger;
        if (down) onTriggerDown();
        if (touching && in.trigger && host != null && hoverKind == HOVER_PANEL
                && (Math.abs(panelPx - lastSentX) > 1.5f || Math.abs(panelPy - lastSentY) > 1.5f)) {
            host.pointer(WebHost.MOVE, panelPx, panelPy);
            lastSentX = panelPx; lastSentY = panelPy;
        }
        if (up) onTriggerUp();
        keyRepeatUpdate(dt);
        scroll(dt);
    }

    private void onTriggerDown() {
        flash = 0.12f;
        if (hoverKind == HOVER_KEY && hoverKey != null) {
            pressedKey = hoverKey;
            keyboard.press(hoverKey);
            keyHeld = 0f; keyRepeat = 0f;
            platform.vibrate(8, 0.3f);
        } else if (hoverKind == HOVER_TOOL && hoverBtn != null) {
            toolAction(hoverBtn.id);
            platform.vibrate(10, 0.35f);
        } else if (hoverKind == HOVER_PANEL && host != null) {
            touching = true;
            lastSentX = panelPx; lastSentY = panelPy;
            host.pointer(WebHost.DOWN, panelPx, panelPy);
        }
    }

    private void onTriggerUp() {
        pressedKey = null;
        if (touching && host != null) {
            host.pointer(WebHost.UP, panelPx, panelPy);
        }
        touching = false;
    }

    private void keyRepeatUpdate(float dt) {
        if (pressedKey == null || !in.trigger || !keyboard.repeats(pressedKey)) return;
        keyHeld += dt;
        if (keyHeld > 0.45f) {
            keyRepeat += dt;
            while (keyRepeat > 0.06f) { keyRepeat -= 0.06f; keyboard.press(pressedKey); }
        }
    }

    public void toolAction(String id) {
        if (host == null) return;
        if (id.equals("back")) host.goBack();
        else if (id.equals("fwd")) host.goForward();
        else if (id.equals("reload")) host.reload();
        else if (id.equals("home")) host.loadUrl(settings.homeUrl);
        else if (id.equals("zoomm")) { settings.zoom = Math.max(0.6f, Math.round((settings.zoom - 0.1f) * 10f) / 10f); applyBrowserSettings(); saveSettings(); }
        else if (id.equals("zoomp")) { settings.zoom = Math.min(2f, Math.round((settings.zoom + 0.1f) * 10f) / 10f); applyBrowserSettings(); saveSettings(); }
        else if (id.equals("kbd")) toggleKeyboard();
        else if (id.equals("menu")) toggleMenu();
    }

    private void scroll(float dt) {
        if (host == null) return;
        float th = settings.texH();
        float cx = hoverKind == HOVER_PANEL ? panelPx : settings.texW() / 2f;
        float cy = hoverKind == HOVER_PANEL ? panelPy : th / 2f;
        boolean touchNow = in.ctrlTouching && !in.ctrlClick && in.ctrlConnected;
        if (touchNow) {
            float dx = in.ctrlTouchX - settings.touchCX, dy = in.ctrlTouchY - settings.touchCY;
            float fx = settings.touchFX - settings.touchCX, fy = settings.touchFY - settings.touchCY;
            float fwd = MathUtil.clamp((dx * fx + dy * fy) / Math.max(1f, fx * fx + fy * fy), -1.5f, 1.5f);
            if (padWasTouching) {
                float d = fwd - padPrevFwd;
                if (Math.abs(d) > 0.002f) host.scroll(cx, cy, d * th * 1.3f * settings.scrollSpeed);
            }
            padPrevFwd = fwd;
        }
        padWasTouching = touchNow;
        // gamepad stick / D-pad as a fallback
        float stick = in.up ? 1f : in.down ? -1f : in.moveY;
        if (Math.abs(stick) > 0.2f) host.scroll(cx, cy, -stick * th * 0.9f * dt * settings.scrollSpeed);
    }

    // ---------------------------------------------------------------- menu
    private void updateMenu(float dt) {
        hoverKind = HOVER_NONE; cursorValid = false;
        if (menu.center.distance(eye) > 3.5f) screens.place();
        Vec3 origin = rayOrigin, dir = rayDir;
        if (calibStep >= 0 && menu.screen.equals("calib")) {
            updateCalibration();
            screens.live();
            // the wizard buttons are selected with the head (the controller is not calibrated yet)
            headWorld.forward(t1);
            String c = menu.pointer(eye, t1, dt, true);
            if (c != null) screens.click(c);
            return;
        }
        if (menu.screen.equals("diag")) screens.live();
        String clicked = menu.pointer(origin, dir, dt, !in.hasControllerPose);
        boolean click = in.trigger && !prev.trigger;
        if (clicked == null && click && menu.hovered != null) clicked = menu.hovered.id;
        if (clicked != null) { platform.vibrate(8, 0.3f); screens.click(clicked); }
    }

    public void render(RenderQueue q) { scene.build(q); }
}
