package com.joaolimz.vrweb;

import com.joaolimz.vrweb.game.Game;
import com.joaolimz.vrweb.game.RenderQueue;
import com.joaolimz.vrweb.game.input.InputFrame;
import com.joaolimz.vrweb.game.math.Quat;
import com.joaolimz.vrweb.game.math.Vec3;
import com.joaolimz.vrweb.game.save.Settings;
import com.joaolimz.vrweb.game.ui.VrKeyboard;
import com.joaolimz.vrweb.game.web.WebHost;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class VrWebTest {
    private static final float DT = 1f / 72f;

    /** Records everything the VR layer asks the browser to do. */
    static final class FakeHost implements WebHost {
        final ArrayList<String> log = new ArrayList<String>();
        final StringBuilder typed = new StringBuilder();
        float lastX, lastY;
        @Override public void loadUrl(String url) { log.add("load " + url); }
        @Override public void reload() { log.add("reload"); }
        @Override public void goBack() { log.add("back"); }
        @Override public void goForward() { log.add("fwd"); }
        @Override public void pointer(int action, float x, float y) { log.add("ptr" + action); lastX = x; lastY = y; }
        @Override public void scroll(float x, float y, float dy) { log.add("scroll " + (dy > 0 ? "+" : "-")); }
        @Override public void typeText(String text) { typed.append(text); }
        @Override public void key(int code) {
            if (code == Game.KEYCODE_DEL && typed.length() > 0) typed.setLength(typed.length() - 1);
            log.add("key" + code);
        }
        @Override public void configure(int w, int h, float zoom, boolean desktop) { log.add("cfg " + w + "x" + h); }
        @Override public void clearData() { log.add("clear"); }
        @Override public String url() { return "https://discord.com/app"; }
        @Override public String title() { return "Discord"; }
        @Override public int progress() { return 100; }
        @Override public boolean canGoBack() { return true; }
        @Override public boolean canGoForward() { return false; }
        @Override public String engineInfo() { return "Chrome/test"; }
        @Override public boolean micGranted() { return true; }
        @Override public void requestMic() { }
        int count(String prefix) { int c = 0; for (String s : log) if (s.startsWith(prefix)) c++; return c; }
    }

    private Game game;
    private FakeHost host;
    private InputFrame in;

    @Before public void setUp() {
        game = new Game(new Settings(), new TestPlatform());
        host = new FakeHost();
        game.setHost(host);
        in = new InputFrame();
        step(2);
    }

    private void step(int n) { for (int i = 0; i < n; i++) game.update(DT, in); }

    /** Points the (gaze) ray from the origin at a world point. */
    private void aimAt(Vec3 p) {
        Quat q = new Quat().setLookRotation(p.x, p.y, p.z, 0, 1, 0);
        in.head.set(q);
    }

    private void clickAt(Vec3 p) {
        aimAt(p); step(2);
        in.trigger = true; step(2);
        in.trigger = false; step(2);
    }

    @Test public void startsOnDiscordWithBrowserConfigured() {
        assertEquals("load https://discord.com/app", host.log.get(1));
        assertTrue(host.log.get(0).startsWith("cfg 1024x640"));
    }

    @Test public void pointingAtThePageClicksTheRightPixel() {
        // dead center of the screen -> center of the texture
        Vec3 c = new Vec3(game.panel.center);
        aimAt(c); step(2);
        in.trigger = true; step(2);
        assertEquals(512f, host.lastX, 3f);
        assertEquals(320f, host.lastY, 3f);
        // drag to the upper-left quarter while the trigger is held
        Vec3 p = new Vec3(c.x - game.panel.w / 4f, c.y + game.panel.h / 4f, c.z);
        aimAt(p); step(3);
        in.trigger = false; step(2);
        assertEquals(256f, host.lastX, 4f);
        assertEquals(160f, host.lastY, 4f);
        assertEquals(1, host.count("ptr0"));
        assertTrue(host.count("ptr1") >= 1);
        assertEquals(1, host.count("ptr2"));
    }

    private void pressKey(String label) {
        VrKeyboard.Key target = null;
        for (VrKeyboard.Key k : game.keyboard.keys) if (k.label.equals(label)) target = k;
        assertNotNull("key " + label, target);
        float ks = game.keyboard.surface.w / 1.9f;
        Vec3 p = game.keyboard.surface.toWorld(target.x * ks, target.y * ks, 0f, new Vec3());
        clickAt(p);
    }

    @Test public void keyboardTypesWithShiftAccentsAndBackspace() {
        pressKey("Shift"); pressKey("O");           // keys show capitals after Shift
        pressKey("i");                              // i (shift was one-shot)
        pressKey("Espaço");
        pressKey("ã é ç"); pressKey("ç");
        pressKey("ABC"); pressKey("a");
        assertEquals("Oi ça", host.typed.toString());
        pressKey("Apagar");
        assertEquals("Oi ç", host.typed.toString());
        pressKey("123#"); pressKey("@"); pressKey("1");
        assertEquals("Oi ç@1", host.typed.toString());
        pressKey("Enter");
        assertEquals(1, host.count("key" + Game.KEYCODE_ENTER));
    }

    @Test public void capsLockOnDoubleTapShift() {
        // tap, then tap again within 0.45 s -> caps lock
        VrKeyboard kb = game.keyboard;
        VrKeyboard.Key sh = null;
        for (VrKeyboard.Key k : kb.keys) if (k.label.equals("Shift")) sh = k;
        kb.press(sh);
        assertEquals(VrKeyboard.L_UPPER, kb.layer);
        for (VrKeyboard.Key k : kb.keys) if (k.kind == VrKeyboard.SHIFT) sh = k;
        kb.press(sh);
        assertEquals(VrKeyboard.L_CAPS, kb.layer);
        kb.output = new VrKeyboard.Output() {
            public void text(String s) { host.typed.append(s); }
            public void backspace() { }
            public void enter() { }
        };
        for (VrKeyboard.Key k : kb.keys) if (k.label.equals("A")) { kb.press(k); kb.press(k); }
        assertEquals("AA", host.typed.toString());
        assertEquals(VrKeyboard.L_CAPS, kb.layer);
    }

    @Test public void urlBarNavigatesAndSearches() {
        game.startUrlInput();
        for (String c : new String[]{"e", "x", "a", "m", "p", "l", "e", ".", "c", "o", "m"}) pressKey(c);
        pressKey("Enter");
        assertTrue(host.log.contains("load https://example.com"));
        assertEquals(0, host.typed.length());
        assertEquals("https://www.google.com/search?q=ol%C3%A1+mundo", Game.normalizeUrl("olá mundo"));
        assertEquals("https://exemplo.com.br", Game.normalizeUrl("exemplo.com.br"));
    }

    @Test public void toolbarButtonsWork() {
        for (Game.Btn b : game.buttons) {
            if (!b.id.equals("back") && !b.id.equals("reload")) continue;
            Vec3 p = game.toolbar.toWorld(b.x, b.y, 0f, new Vec3());
            clickAt(p);
        }
        assertEquals(1, host.count("back"));
        assertEquals(1, host.count("reload"));
        assertEquals(0, host.count("ptr"));   // toolbar clicks never leak into the page
    }

    @Test public void firstControllerConnectionOpensTheCalibrationWizard() {
        in.ctrlConnected = true; in.ctrlStatus = "teste";
        step(3);
        assertEquals(Game.State.MENU, game.state);
        assertEquals("calib", game.menu.screen);
    }

    @Test public void touchpadSwipeScrollsThePage() {
        game.settings.ctrlCalibrated = true;
        in.ctrlConnected = true;
        in.ctrlStatus = "teste";
        in.ctrlTouching = true; in.ctrlTouchX = 128; in.ctrlTouchY = 128;
        step(2);
        for (int i = 0; i < 10; i++) { in.ctrlTouchY = 128 - i * 8; step(1); } // thumb moves up (calibration default: up = smaller Y)
        assertTrue("scrolled: " + host.log, host.count("scroll +") >= 5);
        in.ctrlTouching = false; step(1);
        int n = host.count("scroll");
        step(3);
        assertEquals(n, host.count("scroll"));
    }

    @Test public void appButtonTogglesKeyboardAndLongPressOpensMenu() {
        assertTrue(game.keyboardVisible);
        in.switchHand = true; step(3); in.switchHand = false; step(2);
        assertTrue(!game.keyboardVisible);
        in.switchHand = true; step((int) (1.0f / DT)); in.switchHand = false; step(2);
        assertEquals(Game.State.MENU, game.state);
        assertEquals("main", game.menu.screen);
    }

    @Test public void menuSettingsAndRenderQueue() {
        game.toggleMenu();
        game.screens.click("size");
        assertTrue(game.settings.panelWidth > 2.6f);
        game.screens.click("quality");
        assertEquals(Settings.MEDIUM, game.settings.quality);
        step(2);
        assertTrue(host.count("cfg 1280x800") >= 1);
        game.screens.click("close");
        RenderQueue q = new RenderQueue();
        game.render(q);
        assertTrue(q.webVisible);
        assertTrue(q.count > 40);
        assertTrue(q.textCount > 10);
    }

    @Test public void settingsPersist() {
        Settings s = new Settings();
        s.zoom = 1.3f; s.desktopUa = false; s.panelWidth = 3.1f; s.lastUrl = "https://example.com/x";
        Settings r = new Settings();
        r.load(s.serialize());
        assertEquals(1.3f, r.zoom, 0.001f);
        assertTrue(!r.desktopUa);
        assertEquals(3.1f, r.panelWidth, 0.001f);
        assertEquals("https://example.com/x", r.lastUrl);
    }
}
