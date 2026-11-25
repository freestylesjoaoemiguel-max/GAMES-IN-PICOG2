package com.joaolimz.vrweb.render;

import android.graphics.Bitmap;
import android.opengl.GLES30;
import android.opengl.GLUtils;
import android.opengl.GLSurfaceView;
import android.os.SystemClock;
import android.util.Log;

import com.joaolimz.vrweb.game.Game;
import com.joaolimz.vrweb.game.RenderQueue;
import com.joaolimz.vrweb.game.input.InputFrame;
import com.joaolimz.vrweb.game.math.Mat4;
import com.joaolimz.vrweb.game.math.MathUtil;
import com.joaolimz.vrweb.game.math.Quat;
import com.joaolimz.vrweb.game.math.Vec3;
import com.joaolimz.vrweb.game.mesh.MeshData;
import com.joaolimz.vrweb.game.save.Settings;

import java.nio.ByteBuffer;
import java.util.Random;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Stereo renderer for mobile VR (Pico G2 / G2 4K): renders the RenderQueue twice into a side-by-side
 * eye buffer (resolution depends on the quality preset), then applies lens pre-distortion, vignette and
 * fades in a single cheap full-screen pass. Frustum + distance culling, alpha sorted after opaque.
 */
public final class VrRenderer implements GLSurfaceView.Renderer {
    /** Provides the latest rendered browser frame (triple buffered, GL thread only calls acquire/release). */
    public interface WebFrames {
        /** Returns a new frame, or null if there is nothing new. Must be released after uploading. */
        Bitmap acquire();
        void release();
    }

    private WebFrames webFrames;
    private int progWeb, uWebVP, uWebModel, uWebPage, webTex, webVao, webVbo, webW, webH;
    private boolean webHasFrame;

    public void setWebFrames(WebFrames f) { webFrames = f; }

    public interface FrameSource {
        /** Copies the latest input into dst (thread-safe). */
        void readInput(InputFrame dst);
    }

    private final Game game;
    private final FrameSource source;
    private final RenderQueue queue = new RenderQueue();
    private final InputFrame input = new InputFrame();
    private GpuMeshes meshes;
    private final TextRenderer text = new TextRenderer();
    private final Distortion distortion = new Distortion();

    private int progLit, progBaked, progUnlit;
    private int[] uLit = new int[12], uBaked = new int[8], uUnlit = new int[8];
    private int detailTex;
    private int fbo, colorTex, depthRb, fbW, fbH;
    private int screenW = 1, screenH = 1;
    private long lastNanos;
    private boolean ready;

    private final float[] proj = new float[16], view = new float[16], vp = new float[16], tmpM = new float[16];
    private final float[] skyModel = new float[16];
    private final float[] cullVP = new float[16];
    private final float[][] planes = new float[6][4];
    private final Vec3 eyePos = new Vec3(), right = new Vec3(), tv = new Vec3();
    private final Quat ident = new Quat();
    private int drawCalls, triangles;
    private final int[] alphaIdx = new int[RenderQueue.MAX_ITEMS];
    private final float[] alphaDist = new float[RenderQueue.MAX_ITEMS];

    public VrRenderer(Game game, FrameSource source) {
        this.game = game;
        this.source = source;
    }

    @Override public void onSurfaceCreated(GL10 unused, EGLConfig config) {
        meshes = new GpuMeshes(game.meshes);
        progLit = GlUtil.program(Shaders.LIT_VS, Shaders.COLOR_FS);
        progBaked = GlUtil.program(Shaders.BAKED_VS, Shaders.BAKED_FS);
        progUnlit = GlUtil.program(Shaders.UNLIT_VS, Shaders.COLOR_FS);
        String[] litNames = {"uVP", "uModel", "uLightDir", "uLightColor", "uAmbient", "uTint", "uEye", "uFog", "uFogColor"};
        for (int i = 0; i < litNames.length; i++) uLit[i] = GLES30.glGetUniformLocation(progLit, litNames[i]);
        String[] bakedNames = {"uVP", "uEye", "uFog", "uFogColor", "uDetail", "uDetailAmount"};
        for (int i = 0; i < bakedNames.length; i++) uBaked[i] = GLES30.glGetUniformLocation(progBaked, bakedNames[i]);
        String[] unlitNames = {"uVP", "uModel", "uTint", "uEye", "uFog", "uFogColor"};
        for (int i = 0; i < unlitNames.length; i++) uUnlit[i] = GLES30.glGetUniformLocation(progUnlit, unlitNames[i]);
        detailTex = makeDetailTexture();
        initWeb();
        text.init();
        distortion.init();
        fbo = 0;
        lastNanos = 0;
        ready = true;
        GlUtil.check("surfaceCreated");
    }

    @Override public void onSurfaceChanged(GL10 unused, int width, int height) {
        screenW = width;
        screenH = height;
    }

    private void initWeb() {
        progWeb = GlUtil.program(Shaders.WEB_VS, Shaders.WEB_FS);
        uWebVP = GLES30.glGetUniformLocation(progWeb, "uVP");
        uWebModel = GLES30.glGetUniformLocation(progWeb, "uModel");
        uWebPage = GLES30.glGetUniformLocation(progWeb, "uPage");
        int[] h = new int[1];
        GLES30.glGenVertexArrays(1, h, 0);
        webVao = h[0];
        GLES30.glBindVertexArray(webVao);
        GLES30.glGenBuffers(1, h, 0);
        webVbo = h[0];
        float[] v = {-0.5f, -0.5f, 0f, 1f, 0.5f, -0.5f, 1f, 1f, 0.5f, 0.5f, 1f, 0f, -0.5f, -0.5f, 0f, 1f, 0.5f, 0.5f, 1f, 0f, -0.5f, 0.5f, 0f, 0f};
        java.nio.FloatBuffer fb = java.nio.ByteBuffer.allocateDirect(v.length * 4).order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer();
        fb.put(v).position(0);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, webVbo);
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, v.length * 4, fb, GLES30.GL_STATIC_DRAW);
        GLES30.glEnableVertexAttribArray(0);
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 16, 0);
        GLES30.glEnableVertexAttribArray(1);
        GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, 16, 8);
        GLES30.glBindVertexArray(0);
        GLES30.glGenTextures(1, h, 0);
        webTex = h[0];
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, webTex);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE);
        webW = 0; webH = 0; webHasFrame = false;
    }

    /** Uploads the newest browser frame (once per rendered frame, shared by both eyes). */
    private void uploadWeb() {
        if (webFrames == null) return;
        Bitmap b = webFrames.acquire();
        if (b == null) return;
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, webTex);
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 4);
        if (b.getWidth() != webW || b.getHeight() != webH) {
            webW = b.getWidth(); webH = b.getHeight();
            GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, b, 0);
        } else {
            GLUtils.texSubImage2D(GLES30.GL_TEXTURE_2D, 0, 0, 0, b);
        }
        webFrames.release();
        webHasFrame = true;
    }

    private void drawWeb() {
        if (!queue.webVisible) return;
        GLES30.glDisable(GLES30.GL_CULL_FACE);
        GLES30.glDisable(GLES30.GL_BLEND);
        GLES30.glUseProgram(progWeb);
        GLES30.glUniformMatrix4fv(uWebVP, 1, false, vp, 0);
        GLES30.glUniformMatrix4fv(uWebModel, 1, false, queue.webModel, 0);
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, webTex);
        GLES30.glUniform1i(uWebPage, 0);
        GLES30.glBindVertexArray(webVao);
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 6);
        GLES30.glBindVertexArray(0);
        GLES30.glEnable(GLES30.GL_CULL_FACE);
        drawCalls++;
        triangles += 2;
    }

    /** 256x256 grayscale value-noise texture used as a subtle material detail (MEDIUM/HIGH only). */
    private int makeDetailTexture() {
        int n = 256;
        ByteBuffer b = ByteBuffer.allocateDirect(n * n);
        Random r = new Random(7);
        float[] coarse = new float[17 * 17];
        for (int i = 0; i < coarse.length; i++) coarse[i] = r.nextFloat();
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                float fx = x / 16f, fy = y / 16f;
                int ix = (int) fx, iy = (int) fy;
                float tx = fx - ix, ty = fy - iy;
                float a = coarse[iy * 17 + ix], bb = coarse[iy * 17 + ix + 1], c = coarse[(iy + 1) * 17 + ix], d = coarse[(iy + 1) * 17 + ix + 1];
                float v = (a + (bb - a) * tx) + ((c + (d - c) * tx) - (a + (bb - a) * tx)) * ty;
                v = 0.5f + (v - 0.5f) * 0.35f + (r.nextFloat() - 0.5f) * 0.08f;
                b.put((byte) Math.max(0, Math.min(255, (int) (v * 255))));
            }
        }
        b.position(0);
        int[] h = new int[1];
        GLES30.glGenTextures(1, h, 0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, h[0]);
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 1);
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_R8, n, n, 0, GLES30.GL_RED, GLES30.GL_UNSIGNED_BYTE, b);
        GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_REPEAT);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_REPEAT);
        return h[0];
    }

    private void ensureEyeBuffer(int w, int h) {
        if (fbo != 0 && fbW == w && fbH == h) return;
        int[] t = new int[1];
        if (fbo != 0) {
            t[0] = fbo; GLES30.glDeleteFramebuffers(1, t, 0);
            t[0] = colorTex; GLES30.glDeleteTextures(1, t, 0);
            t[0] = depthRb; GLES30.glDeleteRenderbuffers(1, t, 0);
        }
        fbW = w; fbH = h;
        GLES30.glGenTextures(1, t, 0);
        colorTex = t[0];
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, colorTex);
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA, w, h, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glGenRenderbuffers(1, t, 0);
        depthRb = t[0];
        GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, depthRb);
        GLES30.glRenderbufferStorage(GLES30.GL_RENDERBUFFER, GLES30.GL_DEPTH_COMPONENT16, w, h);
        GLES30.glGenFramebuffers(1, t, 0);
        fbo = t[0];
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo);
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, colorTex, 0);
        GLES30.glFramebufferRenderbuffer(GLES30.GL_FRAMEBUFFER, GLES30.GL_DEPTH_ATTACHMENT, GLES30.GL_RENDERBUFFER, depthRb);
        int st = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER);
        if (st != GLES30.GL_FRAMEBUFFER_COMPLETE) Log.e(GlUtil.TAG, "Eye framebuffer incomplete: 0x" + Integer.toHexString(st));
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);
    }

    @Override public void onDrawFrame(GL10 unused) {
        if (!ready) return;
        long now = System.nanoTime();
        float dt = lastNanos == 0 ? 1f / 72f : (now - lastNanos) / 1e9f;
        lastNanos = now;
        source.readInput(input);
        long t0 = SystemClock.elapsedRealtimeNanos();
        synchronized (game) {
            game.update(dt, input);
            game.render(queue);
        }
        game.perf.updateMs = (SystemClock.elapsedRealtimeNanos() - t0) / 1e6f;
        game.perf.frame(dt);

        Settings s = game.settings;
        int ew = s.eyeWidth(), eh = s.eyeHeight();
        ensureEyeBuffer(ew * 2, eh);
        meshes.uploadsThisFrame = 0;
        meshes.collect();
        text.build(queue);
        uploadWeb();
        drawCalls = 0;
        triangles = 0;

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo);
        GLES30.glViewport(0, 0, ew * 2, eh);
        GLES30.glClearColor(queue.clearColor[0], queue.clearColor[1], queue.clearColor[2], 1f);
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT | GLES30.GL_DEPTH_BUFFER_BIT);

        // projection (symmetric per eye) and a slightly wider culling frustum shared by both eyes
        float aspect = ew / (float) eh;
        float fovY = MathUtil.clamp(s.fovDeg, 70f, 120f) * MathUtil.DEG / Math.max(1f, aspect);
        Mat4.perspective(proj, Math.max(fovY, 1.2f), aspect, 0.05f, 200f);
        buildCullFrustum(s, aspect);

        queue.headRot.right(right);
        for (int eye = 0; eye < 2; eye++) {
            float side = eye == 0 ? -0.5f : 0.5f;
            eyePos.set(queue.headPos).addScaled(right, s.ipd * side);
            Mat4.setViewFromPose(view, eyePos, queue.headRot);
            Mat4.multiply(vp, proj, view);
            GLES30.glViewport(eye * ew, 0, ew, eh);
            drawEye();
        }
        game.perf.drawCalls = drawCalls;
        game.perf.triangles = triangles;
        distortion.draw(colorTex, screenW, screenH, s.lensDistortion, s.lensK1, s.lensK2, s.flipScreen,
                queue.vignette, queue.damageFlash, queue.fadeBlack);
    }

    private void buildCullFrustum(Settings s, float aspect) {
        float fovY = (MathUtil.clamp(s.fovDeg, 70f, 120f) + 10f) * MathUtil.DEG / Math.max(1f, aspect);
        Mat4.perspective(tmpM, Math.max(fovY, 1.3f), aspect * 1.15f, 0.05f, s.drawDistance() + 5f);
        Mat4.setViewFromPose(view, queue.headPos, queue.headRot);
        Mat4.multiply(cullVP, tmpM, view);
        float[] m = cullVP;
        for (int i = 0; i < 6; i++) {
            int row = i / 2;
            float sign = (i % 2 == 0) ? 1f : -1f;
            planes[i][0] = m[3] + sign * m[row];
            planes[i][1] = m[7] + sign * m[4 + row];
            planes[i][2] = m[11] + sign * m[8 + row];
            planes[i][3] = m[15] + sign * m[12 + row];
            float l = (float) Math.sqrt(planes[i][0] * planes[i][0] + planes[i][1] * planes[i][1] + planes[i][2] * planes[i][2]);
            for (int k = 0; k < 4; k++) planes[i][k] /= l;
        }
    }

    private boolean visible(MeshData m, float[] model) {
        float cx = model[0] * m.cx + model[4] * m.cy + model[8] * m.cz + model[12];
        float cy = model[1] * m.cx + model[5] * m.cy + model[9] * m.cz + model[13];
        float cz = model[2] * m.cx + model[6] * m.cy + model[10] * m.cz + model[14];
        float sx = model[0] * model[0] + model[1] * model[1] + model[2] * model[2];
        float sy = model[4] * model[4] + model[5] * model[5] + model[6] * model[6];
        float sz = model[8] * model[8] + model[9] * model[9] + model[10] * model[10];
        float r = m.radius * (float) Math.sqrt(Math.max(sx, Math.max(sy, sz)));
        for (int i = 0; i < 6; i++) {
            float[] p = planes[i];
            if (p[0] * cx + p[1] * cy + p[2] * cz + p[3] < -r) return false;
        }
        return true;
    }

    private void drawEye() {
        RenderQueue q = queue;
        GLES30.glEnable(GLES30.GL_DEPTH_TEST);
        GLES30.glDepthFunc(GLES30.GL_LEQUAL);
        GLES30.glDepthMask(true);
        GLES30.glEnable(GLES30.GL_CULL_FACE);
        GLES30.glCullFace(GLES30.GL_BACK);
        GLES30.glDisable(GLES30.GL_BLEND);

        // sky dome (no depth write, centered on the eye)
        if (q.drawSky) {
            GLES30.glDepthMask(false);
            GLES30.glDisable(GLES30.GL_CULL_FACE);
            Mat4.setTRS(skyModel, eyePos.x, eyePos.y, eyePos.z, ident, 1f, 1f, 1f);
            useUnlit(true); // the dome is 80 m away: never fog it
            drawMesh(game.assets.sky, skyModel, uUnlit, 1, 1, 1, 1);
            GLES30.glDepthMask(true);
            GLES30.glEnable(GLES30.GL_CULL_FACE);
        }

        // static baked sector geometry
        GLES30.glUseProgram(progBaked);
        GLES30.glUniformMatrix4fv(uBaked[0], 1, false, vp, 0);
        GLES30.glUniform3f(uBaked[1], eyePos.x, eyePos.y, eyePos.z);
        GLES30.glUniform2f(uBaked[2], q.fogStart, q.fogEnd);
        GLES30.glUniform3f(uBaked[3], q.fogColor[0], q.fogColor[1], q.fogColor[2]);
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, detailTex);
        GLES30.glUniform1i(uBaked[4], 0);
        GLES30.glUniform1f(uBaked[5], game.settings.detailTexture() ? 0.35f : 0f);
        for (int i = 0; i < q.staticCount; i++) {
            int n = meshes.bind(q.staticMeshes[i]);
            if (n == 0) continue;
            GLES30.glDrawElements(GLES30.GL_TRIANGLES, n, GLES30.GL_UNSIGNED_INT, 0);
            drawCalls++;
            triangles += n / 3;
        }

        drawWeb();

        // opaque dynamic objects
        int alphaCount = 0;
        int currentProg = -1;
        for (int i = 0; i < q.count; i++) {
            int f = q.flags[i];
            if ((f & (RenderQueue.F_ALPHA | RenderQueue.F_OVERLAY)) != 0) {
                float[] m = q.model[i];
                float dx = m[12] - eyePos.x, dy = m[13] - eyePos.y, dz = m[14] - eyePos.z;
                alphaIdx[alphaCount] = i;
                alphaDist[alphaCount] = dx * dx + dy * dy + dz * dz;
                alphaCount++;
                continue;
            }
            MeshData md = game.meshes.get(q.mesh[i]);
            if (md == null || !visible(md, q.model[i])) continue;
            int prog = q.shader[i] == RenderQueue.UNLIT ? progUnlit : progLit;
            if (prog != currentProg) {
                if (prog == progLit) useLit(); else useUnlit((f & RenderQueue.F_NO_FOG) != 0);
                currentProg = prog;
            }
            if ((f & RenderQueue.F_NO_CULL) != 0) GLES30.glDisable(GLES30.GL_CULL_FACE);
            float[] t = q.tint[i];
            drawMesh(q.mesh[i], q.model[i], prog == progLit ? uLit : uUnlit, t[0], t[1], t[2], t[3]);
            if ((f & RenderQueue.F_NO_CULL) != 0) GLES30.glEnable(GLES30.GL_CULL_FACE);
        }

        // world text (depth tested, no depth writes)
        GLES30.glEnable(GLES30.GL_BLEND);
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA);
        GLES30.glDepthMask(false);
        GLES30.glDisable(GLES30.GL_CULL_FACE);
        drawCalls += text.draw(vp, false);

        // transparent objects back-to-front (insertion sort, few items)
        for (int a = 1; a < alphaCount; a++) {
            int ki = alphaIdx[a];
            float kd = alphaDist[a];
            int b = a - 1;
            while (b >= 0 && alphaDist[b] < kd) { alphaIdx[b + 1] = alphaIdx[b]; alphaDist[b + 1] = alphaDist[b]; b--; }
            alphaIdx[b + 1] = ki;
            alphaDist[b + 1] = kd;
        }
        currentProg = -1;
        for (int pass = 0; pass < 2; pass++) {
            boolean overlayPass = pass == 1;
            if (overlayPass) GLES30.glDisable(GLES30.GL_DEPTH_TEST);
            for (int k = 0; k < alphaCount; k++) {
                int i = alphaIdx[k];
                int f = q.flags[i];
                if (((f & RenderQueue.F_OVERLAY) != 0) != overlayPass) continue;
                MeshData md = game.meshes.get(q.mesh[i]);
                if (md == null || !visible(md, q.model[i])) continue;
                int prog = q.shader[i] == RenderQueue.UNLIT ? progUnlit : progLit;
                if (prog != currentProg) {
                    if (prog == progLit) useLit(); else useUnlit(true);
                    currentProg = prog;
                }
                float[] t = q.tint[i];
                drawMesh(q.mesh[i], q.model[i], prog == progLit ? uLit : uUnlit, t[0], t[1], t[2], t[3]);
            }
            if (overlayPass) {
                drawCalls += text.draw(vp, true);
                GLES30.glEnable(GLES30.GL_DEPTH_TEST);
            }
        }
        GLES30.glDepthMask(true);
        GLES30.glDisable(GLES30.GL_BLEND);
    }

    private void useLit() {
        RenderQueue q = queue;
        GLES30.glUseProgram(progLit);
        GLES30.glUniformMatrix4fv(uLit[0], 1, false, vp, 0);
        float lx = q.lightDir[0], ly = q.lightDir[1], lz = q.lightDir[2];
        float l = (float) Math.sqrt(lx * lx + ly * ly + lz * lz);
        GLES30.glUniform3f(uLit[2], lx / l, ly / l, lz / l);
        GLES30.glUniform3f(uLit[3], q.lightColor[0], q.lightColor[1], q.lightColor[2]);
        GLES30.glUniform3f(uLit[4], q.ambient[0], q.ambient[1], q.ambient[2]);
        GLES30.glUniform3f(uLit[6], eyePos.x, eyePos.y, eyePos.z);
        GLES30.glUniform2f(uLit[7], q.fogStart, q.fogEnd);
        GLES30.glUniform3f(uLit[8], q.fogColor[0], q.fogColor[1], q.fogColor[2]);
    }

    private void useUnlit(boolean noFog) {
        RenderQueue q = queue;
        GLES30.glUseProgram(progUnlit);
        GLES30.glUniformMatrix4fv(uUnlit[0], 1, false, vp, 0);
        GLES30.glUniform3f(uUnlit[3], eyePos.x, eyePos.y, eyePos.z);
        if (noFog) GLES30.glUniform2f(uUnlit[4], 1000f, 2000f); else GLES30.glUniform2f(uUnlit[4], q.fogStart, q.fogEnd);
        GLES30.glUniform3f(uUnlit[5], q.fogColor[0], q.fogColor[1], q.fogColor[2]);
    }

    private void drawMesh(int meshId, float[] model, int[] u, float r, float g, float b, float a) {
        int n = meshes.bind(meshId);
        if (n == 0) return;
        GLES30.glUniformMatrix4fv(u[1], 1, false, model, 0);
        int tintLoc = u == uLit ? uLit[5] : uUnlit[2];
        GLES30.glUniform4f(tintLoc, r, g, b, a);
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, n, GLES30.GL_UNSIGNED_INT, 0);
        drawCalls++;
        triangles += n / 3;
    }

    /** GL context lost (activity paused): GPU objects must be recreated. */
    public void onContextLost() {
        ready = false;
        if (meshes != null) meshes.invalidate();
        fbo = 0;
    }
}
