package com.joaolimz.pegapega.game.audio;

import com.joaolimz.pegapega.game.math.Quat;
import com.joaolimz.pegapega.game.math.Vec3;

/**
 * Simple positional audio: distance attenuation and stereo panning relative to the listener head.
 * The platform backend only needs to play a sample with left/right volume and playback rate.
 */
public final class AudioSystem {
    public interface Sink {
        /** Returns a stream handle (or 0). */
        int play(int sfx, float left, float right, float rate, boolean loop);
        void stop(int stream);
        void setVolume(int stream, float left, float right);
    }

    private Sink sink;
    public float master = 0.9f, sfxVolume = 1f, ambientVolume = 0.6f;
    private final Vec3 listener = new Vec3();
    private final Quat listenerRot = new Quat();
    private final Vec3 tmp = new Vec3();
    private final Quat inv = new Quat();
    private int ambientStream, ambientId = -1, alarmStream;
    private float stepCooldown;
    /** Counters for tests / perf overlay. */
    public int playedCount;
    public int lastPlayed = -1;

    public void setSink(Sink s) { sink = s; }

    public void setListener(Vec3 pos, Quat rot) {
        listener.set(pos);
        listenerRot.set(rot);
    }

    public void play2D(int sfx, float volume) {
        playedCount++; lastPlayed = sfx;
        if (sink == null) return;
        float v = volume * master * sfxVolume;
        sink.play(sfx, v, v, 1f, false);
    }

    public void play(int sfx, Vec3 pos, float volume, float pitch) { play(sfx, pos, volume, pitch, 25f); }

    public void play(int sfx, Vec3 pos, float volume, float pitch, float maxDistance) {
        playedCount++; lastPlayed = sfx;
        if (sink == null) return;
        tmp.setSub(pos, listener);
        float d = tmp.length();
        if (d > maxDistance) return;
        float att = 1f / (1f + d * d * 0.04f);
        att *= 1f - d / maxDistance;
        // listener space
        inv.set(listenerRot).conjugate();
        inv.rotate(tmp, tmp);
        float pan = d > 0.01f ? tmp.x / d : 0f; // -1 left .. 1 right
        float l = Math.min(1f, 1f - pan * 0.7f), r = Math.min(1f, 1f + pan * 0.7f);
        float v = volume * att * master * sfxVolume;
        sink.play(sfx, v * l, v * r, pitch, false);
    }

    public void footstep(Vec3 pos, float dt, boolean moving, float speed) {
        stepCooldown -= dt;
        if (moving && stepCooldown <= 0f) {
            stepCooldown = Math.max(0.3f, 0.65f - speed * 0.08f);
            play(Sfx.STEP, pos, 0.35f, 0.9f + (playedCount % 5) * 0.05f, 10f);
        }
    }

    public void setAmbient(int sfx) {
        if (sink == null) { ambientId = sfx; return; }
        if (sfx == ambientId) return;
        if (ambientStream != 0) sink.stop(ambientStream);
        ambientId = sfx;
        float v = master * ambientVolume * 0.5f;
        ambientStream = sfx >= 0 ? sink.play(sfx, v, v, 1f, true) : 0;
    }

    public void setAlarm(boolean on) {
        if (sink == null) return;
        if (on && alarmStream == 0) {
            float v = master * sfxVolume * 0.35f;
            alarmStream = sink.play(Sfx.ALARM, v, v, 1f, true);
        } else if (!on && alarmStream != 0) {
            sink.stop(alarmStream);
            alarmStream = 0;
        }
    }

    public void refreshVolumes() {
        if (sink == null) return;
        if (ambientStream != 0) { float v = master * ambientVolume * 0.5f; sink.setVolume(ambientStream, v, v); }
        if (alarmStream != 0) { float v = master * sfxVolume * 0.35f; sink.setVolume(alarmStream, v, v); }
    }

    public void stopAll() {
        if (sink == null) return;
        if (ambientStream != 0) sink.stop(ambientStream);
        if (alarmStream != 0) sink.stop(alarmStream);
        ambientStream = 0; alarmStream = 0; ambientId = -1;
    }
}
