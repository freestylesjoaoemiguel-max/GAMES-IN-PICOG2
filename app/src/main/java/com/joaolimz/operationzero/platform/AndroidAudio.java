package com.joaolimz.operationzero.platform;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.util.Log;

import com.joaolimz.operationzero.game.audio.AudioSystem;
import com.joaolimz.operationzero.game.audio.Sfx;
import com.joaolimz.operationzero.game.audio.SoundSynth;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * SoundPool backend. Every effect is synthesized procedurally at first launch and cached as a small
 * mono 22 kHz WAV. Optional compressed overrides: put assets/audio/NAME.ogg in the APK and it is used
 * instead (see README).
 */
public final class AndroidAudio implements AudioSystem.Sink {
    private final SoundPool pool;
    private final int[] ids = new int[Sfx.COUNT];

    public AndroidAudio(Context ctx) {
        AudioAttributes attr = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        pool = new SoundPool.Builder().setMaxStreams(16).setAudioAttributes(attr).build();
        File dir = new File(ctx.getCacheDir(), "sfx_v1");
        if (!dir.exists()) dir.mkdirs();
        for (int i = 0; i < Sfx.COUNT; i++) {
            ids[i] = 0;
            String name = Sfx.NAMES[i];
            try {
                AssetFileDescriptor afd = ctx.getAssets().openFd("audio/" + name + ".ogg");
                ids[i] = pool.load(afd, 1);
                afd.close();
                continue;
            } catch (IOException ignored) {
                // no override: use the synthesized sound
            }
            try {
                File f = new File(dir, name + ".wav");
                if (!f.exists() || f.length() < 64) {
                    FileOutputStream fos = new FileOutputStream(f);
                    fos.write(SoundSynth.toWav(SoundSynth.generate(i)));
                    fos.close();
                }
                ids[i] = pool.load(f.getAbsolutePath(), 1);
            } catch (IOException e) {
                Log.w("OperationZero", "sound " + name + " failed: " + e);
            }
        }
    }

    @Override public int play(int sfx, float left, float right, float rate, boolean loop) {
        if (sfx < 0 || sfx >= ids.length || ids[sfx] == 0) return 0;
        float r = Math.max(0.5f, Math.min(2f, rate));
        return pool.play(ids[sfx], clamp(left), clamp(right), loop ? 2 : 1, loop ? -1 : 0, r);
    }

    @Override public void stop(int stream) { if (stream != 0) pool.stop(stream); }

    @Override public void setVolume(int stream, float left, float right) { if (stream != 0) pool.setVolume(stream, clamp(left), clamp(right)); }

    private static float clamp(float v) { return v < 0f ? 0f : (v > 1f ? 1f : v); }

    public void pauseAll() { pool.autoPause(); }

    public void resumeAll() { pool.autoResume(); }

    public void release() { pool.release(); }
}
