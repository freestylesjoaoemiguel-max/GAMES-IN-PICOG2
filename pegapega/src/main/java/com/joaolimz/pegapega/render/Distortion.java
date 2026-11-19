package com.joaolimz.pegapega.render;

import android.opengl.GLES30;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

/**
 * Barrel pre-distortion mesh for the headset lenses (radial k1/k2 model). Both eyes share one mesh and
 * are drawn with different viewports / texture rects. Disabling lens correction uses k = 0.
 */
final class Distortion {
    private static final int N = 32;
    private int program, uFlip, uTex, uRect, uVig, uDamage, uFade;
    private int vao, vbo, ibo, indexCount;
    private float builtK1 = -1f, builtK2 = -1f, builtAspect = -1f;

    void init() {
        program = GlUtil.program(Shaders.DISTORT_VS, Shaders.DISTORT_FS);
        uFlip = GLES30.glGetUniformLocation(program, "uFlip");
        uTex = GLES30.glGetUniformLocation(program, "uEyeTex");
        uRect = GLES30.glGetUniformLocation(program, "uUVRect");
        uVig = GLES30.glGetUniformLocation(program, "uVignette");
        uDamage = GLES30.glGetUniformLocation(program, "uDamage");
        uFade = GLES30.glGetUniformLocation(program, "uFade");
        int[] h = new int[3];
        GLES30.glGenVertexArrays(1, h, 0);
        vao = h[0];
        GLES30.glGenBuffers(2, h, 1);
        vbo = h[1];
        ibo = h[2];
        // indices are constant
        ShortBuffer ib = ByteBuffer.allocateDirect(N * N * 6 * 2).order(ByteOrder.nativeOrder()).asShortBuffer();
        for (int j = 0; j < N; j++) {
            for (int i = 0; i < N; i++) {
                short a = (short) (j * (N + 1) + i), b = (short) (a + 1), c = (short) (a + N + 1), d = (short) (c + 1);
                ib.put(a).put(b).put(d).put(a).put(d).put(c);
            }
        }
        ib.position(0);
        indexCount = N * N * 6;
        GLES30.glBindVertexArray(vao);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo);
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, (N + 1) * (N + 1) * 5 * 4, null, GLES30.GL_DYNAMIC_DRAW);
        GLES30.glEnableVertexAttribArray(0);
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 20, 0);
        GLES30.glEnableVertexAttribArray(1);
        GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, 20, 8);
        GLES30.glEnableVertexAttribArray(2);
        GLES30.glVertexAttribPointer(2, 1, GLES30.GL_FLOAT, false, 20, 16);
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ibo);
        GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, indexCount * 2, ib, GLES30.GL_STATIC_DRAW);
        GLES30.glBindVertexArray(0);
        builtK1 = -1f;
    }

    /** Rebuilds the mesh when lens parameters or the eye aspect ratio change. */
    private void rebuild(float k1, float k2, float aspect) {
        builtK1 = k1; builtK2 = k2; builtAspect = aspect;
        FloatBuffer vb = ByteBuffer.allocateDirect((N + 1) * (N + 1) * 5 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        // normalize so that the horizontal edge midpoint samples exactly the texture edge
        float edge = 1f + k1 + k2;
        float ax = Math.min(aspect, 1f), ay = Math.min(1f / aspect, 1f);
        for (int j = 0; j <= N; j++) {
            for (int i = 0; i <= N; i++) {
                float x = -1f + 2f * i / N, y = -1f + 2f * j / N;   // eye viewport NDC
                float px = x * ax, py = y * ay;                      // lens space (circular)
                float r2 = px * px + py * py;
                float f = (1f + k1 * r2 + k2 * r2 * r2) / edge;
                float u = 0.5f + 0.5f * x * f, v = 0.5f + 0.5f * y * f;
                vb.put(x).put(y).put(u).put(v).put((float) Math.sqrt(r2));
            }
        }
        vb.position(0);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo);
        GLES30.glBufferSubData(GLES30.GL_ARRAY_BUFFER, 0, (N + 1) * (N + 1) * 5 * 4, vb);
    }

    /**
     * Draws both eyes to the window. The eye texture contains left|right side by side.
     * flip180 rotates the final image (and swaps panel halves) for devices whose panel is mounted inverted.
     */
    void draw(int eyeTexture, int screenW, int screenH, boolean lens, float k1, float k2, boolean flip180,
              float vignette, float damage, float fade) {
        float useK1 = lens ? k1 : 0f, useK2 = lens ? k2 : 0f;
        float aspect = (screenW * 0.5f) / Math.max(1, screenH);
        if (useK1 != builtK1 || useK2 != builtK2 || Math.abs(aspect - builtAspect) > 1e-3f) rebuild(useK1, useK2, aspect);
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);
        GLES30.glViewport(0, 0, screenW, screenH);
        GLES30.glDisable(GLES30.GL_DEPTH_TEST);
        GLES30.glDisable(GLES30.GL_CULL_FACE);
        GLES30.glDisable(GLES30.GL_BLEND);
        GLES30.glClearColor(0f, 0f, 0f, 1f);
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT);
        GLES30.glUseProgram(program);
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, eyeTexture);
        GLES30.glUniform1i(uTex, 0);
        GLES30.glUniform2f(uFlip, flip180 ? -1f : 1f, flip180 ? -1f : 1f);
        GLES30.glUniform1f(uVig, vignette);
        GLES30.glUniform1f(uDamage, damage);
        GLES30.glUniform1f(uFade, fade);
        GLES30.glBindVertexArray(vao);
        for (int eye = 0; eye < 2; eye++) {
            // when flipped the left-eye image must land on the right half of the panel (the shader flips NDC
            // of the half viewport, so swap viewports explicitly)
            int half = screenW / 2;
            int vx = (flip180 ? 1 - eye : eye) * half;
            GLES30.glViewport(vx, 0, half, screenH);
            GLES30.glUniform4f(uRect, eye * 0.5f, 0f, 0.5f, 1f);
            GLES30.glDrawElements(GLES30.GL_TRIANGLES, indexCount, GLES30.GL_UNSIGNED_SHORT, 0);
        }
        GLES30.glBindVertexArray(0);
    }
}
