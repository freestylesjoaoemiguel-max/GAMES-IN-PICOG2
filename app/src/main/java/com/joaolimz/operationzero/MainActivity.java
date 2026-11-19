package com.joaolimz.operationzero;

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

import com.joaolimz.operationzero.game.Game;
import com.joaolimz.operationzero.game.input.InputFrame;
import com.joaolimz.operationzero.game.input.Platform;
import com.joaolimz.operationzero.game.save.Settings;
import com.joaolimz.operationzero.platform.AndroidAudio;
import com.joaolimz.operationzero.platform.HeadTracker;
import com.joaolimz.operationzero.platform.InputMapper;
import com.joaolimz.operationzero.platform.ObjModelOverride;
import com.joaolimz.operationzero.render.VrRenderer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Entry point: fullscreen landscape VR activity. Works fully offline (no network permission, no
 * accounts, no store services).
 */
public final class MainActivity extends Activity implements Platform, VrRenderer.FrameSource {
    private GLSurfaceView view;
    private VrRenderer renderer;
    private Game game;
    private HeadTracker head;
    private InputMapper inputMapper;
    private AndroidAudio audio;
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
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        audio = new AndroidAudio(this);
        game = new Game(settings, this, new ObjModelOverride(getAssets()));
        game.audio.setSink(audio);

        view = new GLSurfaceView(this);
        view.setEGLContextClientVersion(3);
        // default config chooser (RGB888 + depth): rendering happens in our own eye framebuffer
        view.setPreserveEGLContextOnPause(true);
        renderer = new VrRenderer(game, this);
        view.setRenderer(renderer);
        view.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        view.setFocusable(true);
        view.setFocusableInTouchMode(true);
        view.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View v, MotionEvent event) { return inputMapper.onTouch(event); }
        });
        setContentView(view);
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
        View decor = getWindow().getDecorView();
        decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN
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
        view.onResume();
        audio.resumeAll();
        hideSystemUi();
    }

    @Override protected void onPause() {
        synchronized (game) {
            if (game.isPlaying()) game.pause();
        }
        head.stop();
        audio.pauseAll();
        view.onPause();
        super.onPause();
    }

    @Override protected void onDestroy() {
        audio.release();
        super.onDestroy();
    }

    // ------------------------------------------------------------------ input
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
        dst.hasControllerPose = false; // Pico G2 controller orientation needs the vendor SDK (see README)
        head.setFlip(settings.flipScreen);
        if (appliedQuality != settings.quality) {
            runOnUiThread(new Runnable() { @Override public void run() { applySurfaceSize(); } });
        }
    }

    // ------------------------------------------------------------------ Platform
    @Override public void vibrate(int millis, float strength) {
        if (vibrator == null || !vibrator.hasVibrator() || millis <= 0) return;
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                int amp = Math.max(1, Math.min(255, (int) (strength * 255)));
                vibrator.vibrate(VibrationEffect.createOneShot(millis, amp));
            } else {
                vibrator.vibrate(millis);
            }
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
            while (off < data.length) {
                int r = in.read(data, off, data.length - off);
                if (r < 0) break;
                off += r;
            }
            in.close();
            return new String(data, 0, off, "UTF-8");
        } catch (IOException e) {
            Log.w("OperationZero", "read " + name + ": " + e);
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
            Log.w("OperationZero", "write " + name + ": " + e);
            return false;
        }
    }

    @Override public boolean deleteFile(String name) {
        return new File(getFilesDir(), name).delete();
    }

    @Override public void quit() {
        runOnUiThread(new Runnable() {
            @Override public void run() {
                finishAffinity();
            }
        });
    }

    @Override public void recenter() { head.requestRecenter(); }

    @Override public String trackingStatus() { return head.status(); }
}
