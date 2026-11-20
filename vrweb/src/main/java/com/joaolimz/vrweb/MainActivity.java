package com.joaolimz.vrweb;

import android.app.Activity;
import android.content.Context;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;

import com.joaolimz.vrweb.game.Game;
import com.joaolimz.vrweb.game.input.InputFrame;
import com.joaolimz.vrweb.game.input.Platform;
import com.joaolimz.vrweb.game.save.Settings;
import com.joaolimz.vrweb.platform.HeadTracker;
import com.joaolimz.vrweb.platform.InputMapper;
import com.joaolimz.vrweb.platform.PicoControllerClient;
import com.joaolimz.vrweb.platform.WebPanel;
import com.joaolimz.vrweb.render.VrRenderer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/** PICO VR: Navegador (Discord web) - fullscreen landscape VR activity for the Pico G2 / G2 4K. */
public final class MainActivity extends Activity implements Platform, VrRenderer.FrameSource {
    private GLSurfaceView view;
    private VrRenderer renderer;
    private Game game;
    private HeadTracker head;
    private InputMapper inputMapper;
    private PicoControllerClient picoController;
    private WebPanel web;
    private Vibrator vibrator;
    private Settings settings;
    private int appliedQuality = -1;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_FULLSCREEN);
        settings = new Settings();
        settings.load(readFile(Settings.FILE));
        head = new HeadTracker(this);
        head.setFlip(settings.flipScreen);
        inputMapper = new InputMapper(settings);
        picoController = new PicoControllerClient(this);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);
        web = new WebPanel(this, root);
        web.create();
        game = new Game(settings, this);
        game.setHost(web);

        view = new GLSurfaceView(this);
        view.setEGLContextClientVersion(3);
        view.setPreserveEGLContextOnPause(true);
        renderer = new VrRenderer(game, this);
        renderer.setWebFrames(web);
        view.setRenderer(renderer);
        view.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        view.setFocusable(true);
        view.setFocusableInTouchMode(true);
        view.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View v, MotionEvent event) { return inputMapper.onTouch(event); }
        });
        root.addView(view, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);
        applySurfaceSize();
        hideSystemUi();
    }

    /** Lower window resolution on LOW/MEDIUM: the hardware scaler upsamples for free. */
    private void applySurfaceSize() {
        if (appliedQuality == settings.quality) return;
        appliedQuality = settings.quality;
        android.util.DisplayMetrics dm = new android.util.DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(dm);
        int w = Math.max(dm.widthPixels, dm.heightPixels), h = Math.min(dm.widthPixels, dm.heightPixels);
        float scale = settings.quality == Settings.LOW ? 0.667f : settings.quality == Settings.MEDIUM ? 0.833f : 1f;
        if (w > 2600 && scale < 1f) view.getHolder().setFixedSize((int) (w * scale) & ~1, (int) (h * scale) & ~1);
        else view.getHolder().setSizeFromLayout();
    }

    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUi();
    }

    @Override protected void onResume() {
        super.onResume();
        head.setDisplayRotation(getWindowManager().getDefaultDisplay().getRotation());
        head.start();
        picoController.start();
        web.start();
        view.onResume();
        hideSystemUi();
    }

    @Override protected void onPause() {
        synchronized (game) { game.rememberUrl(); }
        head.stop();
        picoController.stop();
        web.stop();
        view.onPause();
        super.onPause();
    }

    @Override protected void onDestroy() {
        web.destroy();
        super.onDestroy();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        boolean ok = grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED;
        web.onPermissionResult(ok);
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (inputMapper.onKey(event.getKeyCode(), event)) return true;
        return super.dispatchKeyEvent(event);
    }

    @Override public boolean dispatchGenericMotionEvent(MotionEvent ev) {
        if (inputMapper.onGenericMotion(ev)) return true;
        return super.dispatchGenericMotionEvent(ev);
    }

    @Override public void readInput(InputFrame dst) {
        head.read(dst.head);
        inputMapper.fill(dst);
        picoController.fill(dst);
        dst.hasControllerPose = false;
        head.setFlip(settings.flipScreen);
        if (appliedQuality != settings.quality) {
            runOnUiThread(new Runnable() { @Override public void run() { applySurfaceSize(); } });
        }
    }

    // ------------------------------------------------------------------ Platform
    @Override public void vibrate(int millis, float strength) {
        if (vibrator == null || !vibrator.hasVibrator() || millis <= 0) return;
        try {
            if (Build.VERSION.SDK_INT >= 26) vibrator.vibrate(VibrationEffect.createOneShot(millis, Math.max(1, Math.min(255, (int) (strength * 255)))));
            else vibrator.vibrate(millis);
        } catch (RuntimeException e) {
            // some headsets expose no vibrator service
        }
    }

    @Override public String readFile(String name) {
        File f = new File(getFilesDir(), name);
        if (!f.exists()) return null;
        try {
            FileInputStream in = new FileInputStream(f);
            byte[] data = new byte[(int) f.length()];
            int off = 0;
            while (off < data.length) { int r = in.read(data, off, data.length - off); if (r < 0) break; off += r; }
            in.close();
            return new String(data, 0, off, "UTF-8");
        } catch (IOException e) {
            Log.w("VrWeb", "read " + name + ": " + e);
            return null;
        }
    }

    @Override public boolean writeFile(String name, String content) {
        File tmp = new File(getFilesDir(), name + ".tmp");
        try {
            FileOutputStream out = new FileOutputStream(tmp);
            out.write(content.getBytes("UTF-8"));
            out.getFD().sync();
            out.close();
            return tmp.renameTo(new File(getFilesDir(), name));
        } catch (IOException e) {
            Log.w("VrWeb", "write " + name + ": " + e);
            return false;
        }
    }

    @Override public boolean deleteFile(String name) { return new File(getFilesDir(), name).delete(); }

    @Override public void quit() { runOnUiThread(new Runnable() { @Override public void run() { finishAffinity(); } }); }

    @Override public void recenter() { head.requestRecenter(); }

    @Override public String trackingStatus() { return head.status(); }
}
