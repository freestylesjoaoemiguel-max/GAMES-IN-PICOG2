package com.joaolimz.vrweb.render;

import android.opengl.GLES30;
import android.util.Log;

/** Shader compilation helpers. */
public final class GlUtil {
    private GlUtil() { }

    public static final String TAG = "OperationZero";

    public static int compile(int type, String src) {
        int s = GLES30.glCreateShader(type);
        GLES30.glShaderSource(s, src);
        GLES30.glCompileShader(s);
        int[] ok = new int[1];
        GLES30.glGetShaderiv(s, GLES30.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) {
            String log = GLES30.glGetShaderInfoLog(s);
            GLES30.glDeleteShader(s);
            throw new RuntimeException("Shader compile error: " + log);
        }
        return s;
    }

    public static int program(String vs, String fs) {
        int v = compile(GLES30.GL_VERTEX_SHADER, vs);
        int f = compile(GLES30.GL_FRAGMENT_SHADER, fs);
        int p = GLES30.glCreateProgram();
        GLES30.glAttachShader(p, v);
        GLES30.glAttachShader(p, f);
        GLES30.glLinkProgram(p);
        int[] ok = new int[1];
        GLES30.glGetProgramiv(p, GLES30.GL_LINK_STATUS, ok, 0);
        GLES30.glDeleteShader(v);
        GLES30.glDeleteShader(f);
        if (ok[0] == 0) {
            String log = GLES30.glGetProgramInfoLog(p);
            GLES30.glDeleteProgram(p);
            throw new RuntimeException("Program link error: " + log);
        }
        return p;
    }

    public static void check(String where) {
        int e = GLES30.glGetError();
        if (e != GLES30.GL_NO_ERROR) Log.w(TAG, "GL error 0x" + Integer.toHexString(e) + " at " + where);
    }
}
