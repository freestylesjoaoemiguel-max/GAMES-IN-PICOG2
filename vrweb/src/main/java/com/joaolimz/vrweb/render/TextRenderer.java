package com.joaolimz.vrweb.render;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.opengl.GLES30;
import android.opengl.GLUtils;

import com.joaolimz.vrweb.game.RenderQueue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Bitmap font generated at runtime from the system typeface (no font files shipped) and a CPU text
 * batcher that turns every text command into world-space quads (2 draw calls per eye: normal + overlay).
 */
final class TextRenderer {
    private static final String CHARS =
            " !\"#$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~"
                    + "ÁÀÂÃÉÊÍÓÔÕÚÜÇáàâãéêíóôõúüç°";
    private static final int ATLAS = 512, CELL_W = 32, CELL_H = 40, COLS = ATLAS / CELL_W;
    private static final int FLOATS_PER_VERTEX = 9; // pos3 uv2 color4
    private static final int MAX_GLYPHS = 4096;

    private final float[] advance = new float[CHARS.length()];
    private final float[] u0 = new float[CHARS.length()], v0 = new float[CHARS.length()];
    private final char[] lookupChars = CHARS.toCharArray();
    private final int[] asciiIndex = new int[128];
    private int texture;
    private int program, uVP, uFont;
    private int vao, vbo;
    private final float[] verts = new float[MAX_GLYPHS * 6 * FLOATS_PER_VERTEX];
    private final FloatBuffer buffer = ByteBuffer.allocateDirect(MAX_GLYPHS * 6 * FLOATS_PER_VERTEX * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
    private int normalStart, normalCount, overlayStart, overlayCount;

    void init() {
        Bitmap bmp = Bitmap.createBitmap(ATLAS, ATLAS, Bitmap.Config.ARGB_8888);
        bmp.eraseColor(Color.TRANSPARENT);
        Canvas c = new Canvas(bmp);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.WHITE);
        p.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        p.setTextSize(30f);
        for (int i = 0; i < 128; i++) asciiIndex[i] = -1;
        for (int i = 0; i < lookupChars.length; i++) {
            char ch = lookupChars[i];
            if (ch < 128) asciiIndex[ch] = i;
            int col = i % COLS, row = i / COLS;
            float x = col * CELL_W + 2, y = row * CELL_H + 31;
            c.drawText(String.valueOf(ch), x, y, p);
            advance[i] = (p.measureText(String.valueOf(ch)) + 1f) / CELL_H;
            u0[i] = col * CELL_W / (float) ATLAS;
            v0[i] = row * CELL_H / (float) ATLAS;
        }
        int[] h = new int[1];
        GLES30.glGenTextures(1, h, 0);
        texture = h[0];
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE);
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bmp, 0);
        GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D);
        bmp.recycle();

        program = GlUtil.program(Shaders.TEXT_VS, Shaders.TEXT_FS);
        uVP = GLES30.glGetUniformLocation(program, "uVP");
        uFont = GLES30.glGetUniformLocation(program, "uFont");
        GLES30.glGenVertexArrays(1, h, 0);
        vao = h[0];
        GLES30.glBindVertexArray(vao);
        GLES30.glGenBuffers(1, h, 0);
        vbo = h[0];
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo);
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, verts.length * 4, null, GLES30.GL_DYNAMIC_DRAW);
        int stride = FLOATS_PER_VERTEX * 4;
        GLES30.glEnableVertexAttribArray(0);
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, stride, 0);
        GLES30.glEnableVertexAttribArray(1);
        GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, stride, 12);
        GLES30.glEnableVertexAttribArray(2);
        GLES30.glVertexAttribPointer(2, 4, GLES30.GL_FLOAT, false, stride, 20);
        GLES30.glBindVertexArray(0);
    }

    private int glyph(char ch) {
        if (ch < 128) return asciiIndex[ch];
        for (int i = 95; i < lookupChars.length; i++) if (lookupChars[i] == ch) return i;
        return asciiIndex['?'];
    }

    private float width(String s) {
        float w = 0f;
        for (int i = 0; i < s.length(); i++) {
            int gi = glyph(s.charAt(i));
            if (gi >= 0) w += advance[gi];
        }
        return w;
    }

    /** Builds the vertex data for all text in the queue (once per frame, shared by both eyes). */
    void build(RenderQueue q) {
        int n = 0;
        for (int pass = 0; pass < 2; pass++) {
            int start = n;
            for (int t = 0; t < q.textCount; t++) {
                boolean overlay = (q.textFlags[t] & RenderQueue.F_OVERLAY) != 0;
                if (overlay != (pass == 1)) continue;
                String s = q.text[t];
                float[] m = q.textModel[t];
                float[] col = q.textColor[t];
                float w = width(s);
                float x = q.textAlign[t] == 1 ? -w * 0.5f : q.textAlign[t] == 2 ? -w : 0f;
                for (int i = 0; i < s.length(); i++) {
                    int gi = glyph(s.charAt(i));
                    if (gi < 0) continue;
                    float adv = advance[gi];
                    if (s.charAt(i) != ' ' && n + 6 <= MAX_GLYPHS * 6) {
                        float gw = CELL_W / (float) CELL_H;
                        float uu0 = u0[gi], vv0 = v0[gi], uu1 = uu0 + CELL_W / (float) ATLAS, vv1 = vv0 + CELL_H / (float) ATLAS;
                        // glyph box in text space: y from -0.25 (descender) to 0.75
                        n = vertex(n, m, x, -0.25f, uu0, vv1, col);
                        n = vertex(n, m, x + gw, -0.25f, uu1, vv1, col);
                        n = vertex(n, m, x + gw, 0.75f, uu1, vv0, col);
                        n = vertex(n, m, x, -0.25f, uu0, vv1, col);
                        n = vertex(n, m, x + gw, 0.75f, uu1, vv0, col);
                        n = vertex(n, m, x, 0.75f, uu0, vv0, col);
                    }
                    x += adv;
                }
            }
            if (pass == 0) { normalStart = start; normalCount = n - start; }
            else { overlayStart = start; overlayCount = n - start; }
        }
        if (n > 0) {
            buffer.position(0);
            buffer.put(verts, 0, n * FLOATS_PER_VERTEX).position(0);
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo);
            GLES30.glBufferSubData(GLES30.GL_ARRAY_BUFFER, 0, n * FLOATS_PER_VERTEX * 4, buffer);
        }
    }

    private int vertex(int n, float[] m, float x, float y, float u, float v, float[] col) {
        int o = n * FLOATS_PER_VERTEX;
        verts[o] = m[0] * x + m[4] * y + m[12];
        verts[o + 1] = m[1] * x + m[5] * y + m[13];
        verts[o + 2] = m[2] * x + m[6] * y + m[14];
        verts[o + 3] = u; verts[o + 4] = v;
        verts[o + 5] = col[0]; verts[o + 6] = col[1]; verts[o + 7] = col[2]; verts[o + 8] = col[3];
        return n + 1;
    }

    /** Draws either the depth-tested batch or the overlay batch. Returns draw calls issued. */
    int draw(float[] vp, boolean overlay) {
        int count = overlay ? overlayCount : normalCount;
        if (count == 0) return 0;
        GLES30.glUseProgram(program);
        GLES30.glUniformMatrix4fv(uVP, 1, false, vp, 0);
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture);
        GLES30.glUniform1i(uFont, 0);
        GLES30.glBindVertexArray(vao);
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, overlay ? overlayStart : normalStart, count);
        GLES30.glBindVertexArray(0);
        return 1;
    }
}
