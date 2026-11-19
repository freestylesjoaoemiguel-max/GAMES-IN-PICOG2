package com.joaolimz.pegapega.game.save;

/** User options for PICO VR: PEGA-PEGA. LOW quality is the default for the Pico G2. */
public final class Settings {
    public static final String FILE = "settings.cfg";
    public static final int LOW = 0, MEDIUM = 1, HIGH = 2;
    public static final String[] QUALITY_NAMES = {"LOW", "MEDIUM", "HIGH"};

    // graphics
    public int quality = LOW;
    public boolean showPerf = false;
    public boolean lensDistortion = true;
    public float lensK1 = 0.22f, lensK2 = 0.20f;
    public float fovDeg = 96f;
    public float ipd = 0.064f;
    public boolean flipScreen = false;

    // movement (arm locomotion)
    /** Arm length in meters (longer arms = bigger, faster strides). */
    public float armLength = 1.0f;
    /** Eye height above the ground while standing. */
    public float standHeight = 1.0f;
    /** 0 = both arms follow the controller (recommended on the G2), 1 = only the active arm. */
    public int armMode = 0;
    /** Extra push multiplier applied when letting go (jump strength). */
    public float flingBoost = 1.15f;
    /** Optional touchpad walking (off by default: you move with your arms). */
    public boolean touchpadWalk = false;
    public float walkSpeed = 2.2f;

    // comfort
    public int turnMode = 0;
    public int snapAngle = 45;
    public float smoothTurnSpeed = 90f;
    public int vignette = 1;

    // audio
    public float master = 0.9f, sfx = 1f, ambient = 0.5f;

    // game
    /** 0 = infection, 1 = classic tag. */
    public int mode = 0;
    public int botCount = 7;
    /** 0 easy, 1 normal, 2 hard */
    public int difficulty = 1;
    public String playerName = "VOCÊ";
    public int playerColor = 0;

    // Pico controller calibration (same wizard as Operation Zero)
    public boolean ctrlCalibrated = false;
    public int[] ctrlPw = {1, 0, 0, 0, 1, 0, 0, 0, -1};
    public int[] ctrlPb = {1, 0, 0, 0, 1, 0, 0, 0, -1};
    public boolean ctrlInverse = false;
    public float touchCX = 128f, touchCY = 128f, touchFX = 128f, touchFY = 30f, touchRX = 226f, touchRY = 128f;

    // key bindings
    public static final int A_TRIGGER = 0, A_GRIP = 1, A_ACTION = 2, A_SWITCH = 3, A_MENU = 4, A_UP = 5, A_DOWN = 6, A_LEFT = 7, A_RIGHT = 8;
    public static final int ACTIONS = 9;
    public static final String[] ACTION_NAMES = {"GATILHO", "GRIP", "ACAO (TOUCHPAD)", "APP", "MENU", "FRENTE", "TRAS", "ESQUERDA", "DIREITA"};
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
    public float drawDistance() { return quality == LOW ? 60f : quality == MEDIUM ? 80f : 100f; }
    public boolean detailTexture() { return quality != LOW; }
    public int maxParticles() { return quality == LOW ? 32 : quality == MEDIUM ? 64 : 96; }

    public boolean isBound(int action, int keyCode) {
        for (int k : keys[action]) if (k == keyCode) return true;
        return false;
    }

    public String serialize() {
        KeyValue kv = new KeyValue();
        kv.put("quality", quality).put("showPerf", showPerf).put("lens", lensDistortion).put("k1", lensK1).put("k2", lensK2)
                .put("fov", fovDeg).put("ipd", ipd).put("flip", flipScreen)
                .put("armLength", armLength).put("standHeight", standHeight).put("armMode", armMode).put("fling", flingBoost)
                .put("touchpadWalk", touchpadWalk).put("walkSpeed", walkSpeed)
                .put("turnMode", turnMode).put("snapAngle", snapAngle).put("smoothTurn", smoothTurnSpeed).put("vignette", vignette)
                .put("master", master).put("sfx", sfx).put("ambient", ambient)
                .put("mode", mode).put("bots", botCount).put("difficulty", difficulty).put("color", playerColor)
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
        armLength = kv.getFloat("armLength", armLength);
        standHeight = kv.getFloat("standHeight", standHeight);
        armMode = clamp(kv.getInt("armMode", armMode), 0, 1);
        flingBoost = kv.getFloat("fling", flingBoost);
        touchpadWalk = kv.getBool("touchpadWalk", touchpadWalk);
        walkSpeed = kv.getFloat("walkSpeed", walkSpeed);
        turnMode = clamp(kv.getInt("turnMode", turnMode), 0, 1);
        snapAngle = kv.getInt("snapAngle", snapAngle);
        smoothTurnSpeed = kv.getFloat("smoothTurn", smoothTurnSpeed);
        vignette = clamp(kv.getInt("vignette", vignette), 0, 2);
        master = kv.getFloat("master", master);
        sfx = kv.getFloat("sfx", sfx);
        ambient = kv.getFloat("ambient", ambient);
        mode = clamp(kv.getInt("mode", mode), 0, 1);
        botCount = clamp(kv.getInt("bots", botCount), 1, 9);
        difficulty = clamp(kv.getInt("difficulty", difficulty), 0, 2);
        playerColor = clamp(kv.getInt("color", playerColor), 0, 9);
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
