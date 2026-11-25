package com.joaolimz.operationzero.game.save;

/** All user configurable options. LOW quality is the default for the Pico G2. */
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

    // gameplay / controls
    public boolean rightHanded = true;
    /** 0 = trigger grabs with empty hand (Pico G2 controller has no grip button), 1 = dedicated grip button. */
    public int grabMode = 0;
    public boolean reloadAssist = false;
    public float playerHeight = 1.70f;
    public boolean aimFromHead = true;

    // comfort
    /** 0 snap, 1 smooth */
    public int turnMode = 0;
    public int snapAngle = 45;
    public float smoothTurnSpeed = 90f;
    /** 0 smooth locomotion, 1 teleport */
    public int moveMode = 0;
    public float moveSpeed = 2.0f;
    /** 0 off, 1 light, 2 strong */
    public int vignette = 1;
    /** Aim smoothing / sensitivity multiplier (0.5 .. 2). */
    public float sensitivity = 1f;
    public boolean cameraRecoil = false;

    // audio
    public float master = 0.9f, sfx = 1f, ambient = 0.6f;

    // Pico G2 controller (filled by the calibration wizard)
    /** 0 = aim with the controller when connected (default), 1 = always aim with the head. */
    public int aimSource = 0;
    /** 0 = walk where the head looks, 1 = walk where the controller points. */
    public int moveDirection = 0;
    public boolean ctrlCalibrated = false;
    public int[] ctrlPw = {1, 0, 0, 0, 1, 0, 0, 0, -1};
    public int[] ctrlPb = {1, 0, 0, 0, 1, 0, 0, 0, -1};
    public boolean ctrlInverse = false;
    /** Touchpad: center and the "forward"/"right" reference points (raw units). */
    public float touchCX = 128f, touchCY = 128f;
    public float touchFX = 128f, touchFY = 30f;
    public float touchRX = 226f, touchRY = 128f;

    // input bindings (Android key codes); several codes per action
    public static final int A_TRIGGER = 0, A_GRIP = 1, A_ACTION = 2, A_SWITCH = 3, A_MENU = 4, A_UP = 5, A_DOWN = 6, A_LEFT = 7, A_RIGHT = 8;
    public static final int ACTIONS = 9;
    public static final String[] ACTION_NAMES = {"GATILHO", "PEGAR (GRIP)", "ACAO (TOUCHPAD)", "TROCAR MAO (APP)", "MENU", "FRENTE", "TRAS", "ESQUERDA", "DIREITA"};
    public static final String[] ACTION_KEYS = {"key_trigger", "key_grip", "key_action", "key_switch", "key_menu", "key_up", "key_down", "key_left", "key_right"};
    public static final int[][] DEFAULT_KEYS = {
            {105, 96, 62},     // R2, BUTTON_A, SPACE
            {103, 35},         // R1, G
            {23, 66, 97, 46},  // DPAD_CENTER, ENTER, BUTTON_B, R
            {4, 99, 61},       // BACK (Pico app button), BUTTON_X, TAB
            {108, 82, 111},    // START, MENU, ESC
            {19, 51},          // DPAD_UP, W
            {20, 47},          // DPAD_DOWN, S
            {21, 29},          // DPAD_LEFT, A
            {22, 32}           // DPAD_RIGHT, D
    };
    public int[][] keys = copyKeys(DEFAULT_KEYS);

    public static int[][] copyKeys(int[][] src) {
        int[][] out = new int[src.length][];
        for (int i = 0; i < src.length; i++) out[i] = src[i].clone();
        return out;
    }

    // ---- quality derived values
    public int eyeWidth() { return quality == LOW ? 1024 : quality == MEDIUM ? 1280 : 1536; }
    public int eyeHeight() { return quality == LOW ? 1152 : quality == MEDIUM ? 1440 : 1728; }
    public int maxNpcs() { return quality == LOW ? 4 : quality == MEDIUM ? 6 : 8; }
    public int maxBodies() { return quality == LOW ? 12 : quality == MEDIUM ? 20 : 30; }
    public int maxRagdolls() { return quality == LOW ? 3 : quality == MEDIUM ? 5 : 7; }
    public float corpseTime() { return quality == LOW ? 8f : quality == MEDIUM ? 14f : 25f; }
    public float drawDistance() { return quality == LOW ? 38f : quality == MEDIUM ? 55f : 75f; }
    public boolean detailTexture() { return quality != LOW; }
    public int maxParticles() { return quality == LOW ? 24 : quality == MEDIUM ? 48 : 96; }

    public String serialize() {
        KeyValue kv = new KeyValue();
        kv.put("quality", quality).put("showPerf", showPerf).put("lens", lensDistortion).put("k1", lensK1).put("k2", lensK2)
                .put("fov", fovDeg).put("ipd", ipd).put("flip", flipScreen)
                .put("rightHanded", rightHanded).put("grabMode", grabMode).put("reloadAssist", reloadAssist)
                .put("height", playerHeight).put("aimFromHead", aimFromHead)
                .put("turnMode", turnMode).put("snapAngle", snapAngle).put("smoothTurn", smoothTurnSpeed)
                .put("moveMode", moveMode).put("moveSpeed", moveSpeed).put("vignette", vignette)
                .put("sensitivity", sensitivity).put("cameraRecoil", cameraRecoil)
                .put("master", master).put("sfx", sfx).put("ambient", ambient);
        for (int i = 0; i < ACTIONS; i++) kv.putIntArray(ACTION_KEYS[i], keys[i]);
        kv.put("aimSource", aimSource).put("moveDirection", moveDirection).put("ctrlCalibrated", ctrlCalibrated)
                .putIntArray("ctrlPw", ctrlPw).putIntArray("ctrlPb", ctrlPb).put("ctrlInverse", ctrlInverse)
                .put("touchCX", touchCX).put("touchCY", touchCY).put("touchFX", touchFX).put("touchFY", touchFY)
                .put("touchRX", touchRX).put("touchRY", touchRY);
        return kv.serialize();
    }

    public void load(String text) {
        if (text == null) return;
        KeyValue kv = KeyValue.parse(text);
        quality = clampI(kv.getInt("quality", quality), 0, 2);
        showPerf = kv.getBool("showPerf", showPerf);
        lensDistortion = kv.getBool("lens", lensDistortion);
        lensK1 = kv.getFloat("k1", lensK1);
        lensK2 = kv.getFloat("k2", lensK2);
        fovDeg = kv.getFloat("fov", fovDeg);
        ipd = kv.getFloat("ipd", ipd);
        flipScreen = kv.getBool("flip", flipScreen);
        rightHanded = kv.getBool("rightHanded", rightHanded);
        grabMode = clampI(kv.getInt("grabMode", grabMode), 0, 1);
        reloadAssist = kv.getBool("reloadAssist", reloadAssist);
        playerHeight = kv.getFloat("height", playerHeight);
        aimFromHead = kv.getBool("aimFromHead", aimFromHead);
        turnMode = clampI(kv.getInt("turnMode", turnMode), 0, 1);
        snapAngle = kv.getInt("snapAngle", snapAngle);
        smoothTurnSpeed = kv.getFloat("smoothTurn", smoothTurnSpeed);
        moveMode = clampI(kv.getInt("moveMode", moveMode), 0, 1);
        moveSpeed = kv.getFloat("moveSpeed", moveSpeed);
        vignette = clampI(kv.getInt("vignette", vignette), 0, 2);
        sensitivity = kv.getFloat("sensitivity", sensitivity);
        cameraRecoil = kv.getBool("cameraRecoil", cameraRecoil);
        master = kv.getFloat("master", master);
        sfx = kv.getFloat("sfx", sfx);
        ambient = kv.getFloat("ambient", ambient);
        for (int i = 0; i < ACTIONS; i++) keys[i] = kv.getIntArray(ACTION_KEYS[i], keys[i]);
        aimSource = clampI(kv.getInt("aimSource", aimSource), 0, 1);
        moveDirection = clampI(kv.getInt("moveDirection", moveDirection), 0, 1);
        ctrlCalibrated = kv.getBool("ctrlCalibrated", ctrlCalibrated);
        int[] a = kv.getIntArray("ctrlPw", ctrlPw);
        if (a.length == 9) ctrlPw = a;
        a = kv.getIntArray("ctrlPb", ctrlPb);
        if (a.length == 9) ctrlPb = a;
        ctrlInverse = kv.getBool("ctrlInverse", ctrlInverse);
        touchCX = kv.getFloat("touchCX", touchCX); touchCY = kv.getFloat("touchCY", touchCY);
        touchFX = kv.getFloat("touchFX", touchFX); touchFY = kv.getFloat("touchFY", touchFY);
        touchRX = kv.getFloat("touchRX", touchRX); touchRY = kv.getFloat("touchRY", touchRY);
    }

    private static int clampI(int v, int lo, int hi) { return v < lo ? lo : (v > hi ? hi : v); }

    public boolean isBound(int action, int keyCode) {
        int[] k = keys[action];
        for (int i = 0; i < k.length; i++) if (k[i] == keyCode) return true;
        return false;
    }
}
