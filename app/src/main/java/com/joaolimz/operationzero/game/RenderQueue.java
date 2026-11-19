package com.joaolimz.operationzero.game;

import com.joaolimz.operationzero.game.math.Mat4;
import com.joaolimz.operationzero.game.math.Quat;
import com.joaolimz.operationzero.game.math.Vec3;

/**
 * Platform independent list of draw commands produced by the game each frame and consumed by the
 * OpenGL renderer once per eye. All storage is preallocated (no garbage per frame).
 */
public final class RenderQueue {
    public static final int LIT = 0, UNLIT = 1, BAKED = 2;
    /** Flags */
    public static final int F_ALPHA = 1, F_NO_DEPTH = 2, F_VIEW_SPACE = 4, F_NO_CULL = 8, F_NO_FOG = 16, F_OVERLAY = 32;

    public static final int MAX_ITEMS = 768;
    public static final int MAX_TEXT = 96;
    public static final int MAX_STATIC = 64;

    public final int[] mesh = new int[MAX_ITEMS];
    public final int[] shader = new int[MAX_ITEMS];
    public final int[] flags = new int[MAX_ITEMS];
    public final float[][] model = new float[MAX_ITEMS][16];
    public final float[][] tint = new float[MAX_ITEMS][4];
    public int count;

    public final String[] text = new String[MAX_TEXT];
    public final float[][] textModel = new float[MAX_TEXT][16];
    public final float[][] textColor = new float[MAX_TEXT][4];
    /** 0 = left, 1 = center, 2 = right */
    public final int[] textAlign = new int[MAX_TEXT];
    public final int[] textFlags = new int[MAX_TEXT];
    public int textCount;

    public final int[] staticMeshes = new int[MAX_STATIC];
    public int staticCount;

    // environment
    public final float[] clearColor = {0.05f, 0.06f, 0.07f};
    public final float[] fogColor = {0.1f, 0.11f, 0.12f};
    public float fogStart = 20f, fogEnd = 45f;
    public boolean drawSky;
    public final float[] lightDir = {0.35f, 0.85f, 0.4f};
    public final float[] ambient = {0.35f, 0.36f, 0.4f};
    public final float[] lightColor = {0.75f, 0.72f, 0.68f};
    /** Comfort vignette strength 0..1 and damage flash 0..1 drawn by the renderer in view space. */
    public float vignette, damageFlash, fadeBlack;

    // eye-independent camera: head pose
    public final Vec3 headPos = new Vec3();
    public final Quat headRot = new Quat();

    public void clear() {
        count = 0; textCount = 0; staticCount = 0;
        vignette = 0; damageFlash = 0; fadeBlack = 0; drawSky = false;
    }

    /** Adds a mesh draw and returns its index (or -1 if the queue is full). Caller fills model matrix. */
    public int add(int meshId, int shaderType, int f, float r, float g, float b, float a) {
        if (count >= MAX_ITEMS || meshId < 0) return -1;
        int i = count++;
        mesh[i] = meshId; shader[i] = shaderType; flags[i] = f;
        float[] t = tint[i]; t[0] = r; t[1] = g; t[2] = b; t[3] = a;
        return i;
    }

    public int add(int meshId, Vec3 pos, Quat rot, float scale) {
        int i = add(meshId, LIT, 0, 1, 1, 1, 1);
        if (i >= 0) Mat4.setTRS(model[i], pos, rot, scale);
        return i;
    }

    public int add(int meshId, Vec3 pos, Quat rot, float sx, float sy, float sz, int shaderType, int f, float r, float g, float b, float a) {
        int i = add(meshId, shaderType, f, r, g, b, a);
        if (i >= 0) Mat4.setTRS(model[i], pos.x, pos.y, pos.z, rot, sx, sy, sz);
        return i;
    }

    public int addText(String s, Vec3 pos, Quat rot, float size, int align, float r, float g, float b, float a, int f) {
        if (textCount >= MAX_TEXT || s == null) return -1;
        int i = textCount++;
        text[i] = s;
        Mat4.setTRS(textModel[i], pos.x, pos.y, pos.z, rot, size, size, size);
        float[] c = textColor[i]; c[0] = r; c[1] = g; c[2] = b; c[3] = a;
        textAlign[i] = align;
        textFlags[i] = f;
        return i;
    }

    public void addStatic(int meshId) {
        if (staticCount < MAX_STATIC && meshId >= 0) staticMeshes[staticCount++] = meshId;
    }
}
