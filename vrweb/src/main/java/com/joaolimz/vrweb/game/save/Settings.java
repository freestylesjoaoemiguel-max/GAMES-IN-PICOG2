package com.joaolimz.vrweb.game.save;

/** User options for the VR web browser (Discord on the Pico G2). */
public final class Settings {
    public static final String FILE = "settings.cfg";
    public static final int LOW = 0, MEDIUM = 1, HIGH = 2;
    public static final String[] QUALITY_NAMES = {"LOW", "MEDIUM", "HIGH"};
    public static final String DISCORD = "https://discord.com/app";

    // graphics
    public int quality = LOW;
    public boolean showPerf = false;
    public boolean lensDistortion = true;
    public float lensK1 = 0.22f, lensK2 = 0.20f;
    public float fovDeg = 96f;
    public float ipd = 0.064f;
    public boolean flipScreen = false;

    // browser
    public String homeUrl = DISCORD;
    public String lastUrl = DISCORD;
    public boolean desktopUa = true;
    /** Texture pixels per CSS pixel (1.0 = page is laid out as wide as the texture). */
    public float zoom = 1.0f;
    public float panelWidth = 2.6f;
    public float panelDistance = 1.9f;
    public float panelHeight = 0.2f;
    public boolean keyboardAuto = true;
    public float scrollSpeed = 1.0f;

    // comfort
    public int vignette = 0;

    // Pico controller calibration
    public boolean ctrlCalibrated = false;
    public int[] ctrlPw = {1, 0, 0, 0, 1, 0, 0, 0, -1};
    public int[] ctrlPb = {1, 0, 0, 0, 1, 0, 0, 0, -1};
    public boolean ctrlInverse = false;
    public float touchCX = 128f, touchCY = 128f, touchFX = 128f, touchFY = 30f, touchRX = 226f, touchRY = 128f;

    public static final int A_TRIGGER = 0, A_GRIP = 1, A_ACTION = 2, A_SWITCH = 3, A_MENU = 4, A_UP = 5, A_DOWN = 6, A_LEFT = 7, A_RIGHT = 8;
    public static final int ACTIONS = 9;
    public static final String[] ACTION_KEYS = {"key_trigger", "key_grip", "key_action", "key_switch", "key_menu", "key_up", "key_down", "key_left", "key_right"};
    public static final int[][] DEFAULT_KEYS = {
            {105, 96, 62}, {103, 35}, {23, 66, 97, 46}, {4, 99, 61}, {108, 82, 111}, {19, 51}, {20, 47}, {21, 29}, {22, 32}
    };
    public int[][] keys = copyKeys(DEFAULT_KEYS);

    public static int[][] copyKeys(int[][] src) {
        int[][] out = new int[src.length][];
        for (int i = 0; i < src.length; i++) out[i] = src[i].clone();
        return out;
    }

    public int eyeWidth() { return quality == LOW ? 1024 : quality == MEDIUM ? 1280 : 1536; }
    public int eyeHeight() { return quality == LOW ? 1152 : quality == MEDIUM ? 1440 : 1728; }
    /** Browser texture size (16:10). */
    public int texW() { return quality == LOW ? 1024 : quality == MEDIUM ? 1280 : 1600; }
    public int texH() { return texW() * 5 / 8; }
    public float drawDistance() { return 60f; }
    public boolean detailTexture() { return false; }

    public boolean isBound(int action, int keyCode) {
        for (int k : keys[action]) if (k == keyCode) return true;
        return false;
    }

    public String serialize() {
        KeyValue kv = new KeyValue();
        kv.put("quality", quality).put("showPerf", showPerf).put("lens", lensDistortion).put("k1", lensK1).put("k2", lensK2)
                .put("fov", fovDeg).put("ipd", ipd).put("flip", flipScreen)
                .put("home", homeUrl).put("last", lastUrl).put("desktopUa", desktopUa).put("zoom", zoom)
                .put("panelW", panelWidth).put("panelD", panelDistance).put("keyboardAuto", keyboardAuto)
                .put("scroll", scrollSpeed).put("vignette", vignette)
                .put("ctrlCalibrated", ctrlCalibrated).putIntArray("ctrlPw", ctrlPw).putIntArray("ctrlPb", ctrlPb)
                .put("ctrlInverse", ctrlInverse)
                .put("touchCX", touchCX).put("touchCY", touchCY).put("touchFX", touchFX).put("touchFY", touchFY)
                .put("touchRX", touchRX).put("touchRY", touchRY);
        for (int i = 0; i < ACTIONS; i++) kv.putIntArray(ACTION_KEYS[i], keys[i]);
        return kv.serialize();
    }

    public void load(String text) {
        if (text == null) return;
        KeyValue kv = KeyValue.parse(text);
        quality = clamp(kv.getInt("quality", quality), 0, 2);
        showPerf = kv.getBool("showPerf", showPerf);
        lensDistortion = kv.getBool("lens", lensDistortion);
        lensK1 = kv.getFloat("k1", lensK1);
        lensK2 = kv.getFloat("k2", lensK2);
        fovDeg = kv.getFloat("fov", fovDeg);
        ipd = kv.getFloat("ipd", ipd);
        flipScreen = kv.getBool("flip", flipScreen);
        homeUrl = kv.getString("home", homeUrl);
        lastUrl = kv.getString("last", lastUrl);
        desktopUa = kv.getBool("desktopUa", desktopUa);
        zoom = Math.max(0.6f, Math.min(2f, kv.getFloat("zoom", zoom)));
        panelWidth = Math.max(1.2f, Math.min(4.5f, kv.getFloat("panelW", panelWidth)));
        panelDistance = Math.max(1.0f, Math.min(4f, kv.getFloat("panelD", panelDistance)));
        keyboardAuto = kv.getBool("keyboardAuto", keyboardAuto);
        scrollSpeed = Math.max(0.3f, Math.min(3f, kv.getFloat("scroll", scrollSpeed)));
        vignette = clamp(kv.getInt("vignette", vignette), 0, 2);
        ctrlCalibrated = kv.getBool("ctrlCalibrated", ctrlCalibrated);
        int[] a = kv.getIntArray("ctrlPw", ctrlPw);
        if (a.length == 9) ctrlPw = a;
        a = kv.getIntArray("ctrlPb", ctrlPb);
        if (a.length == 9) ctrlPb = a;
        ctrlInverse = kv.getBool("ctrlInverse", ctrlInverse);
        touchCX = kv.getFloat("touchCX", touchCX); touchCY = kv.getFloat("touchCY", touchCY);
        touchFX = kv.getFloat("touchFX", touchFX); touchFY = kv.getFloat("touchFY", touchFY);
        touchRX = kv.getFloat("touchRX", touchRX); touchRY = kv.getFloat("touchRY", touchRY);
        for (int i = 0; i < ACTIONS; i++) keys[i] = kv.getIntArray(ACTION_KEYS[i], keys[i]);
    }

    private static int clamp(int v, int lo, int hi) { return v < lo ? lo : (v > hi ? hi : v); }
}
