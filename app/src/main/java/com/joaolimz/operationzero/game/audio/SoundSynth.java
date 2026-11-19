package com.joaolimz.operationzero.game.audio;

import com.joaolimz.operationzero.game.math.MathUtil;

/**
 * Procedural sound design: every effect is synthesized at startup (original audio, no licensing
 * issues, zero APK size). Output: 16-bit mono PCM at {@link #RATE} Hz (small memory footprint).
 */
public final class SoundSynth {
    public static final int RATE = 22050;

    private SoundSynth() { }

    public static short[] generate(int id) {
        MathUtil.Rng rng = new MathUtil.Rng(1234 + id * 7919L);
        switch (id) {
            case Sfx.PISTOL: return gunshot(rng, 0.32f, 1400f, 160f, 0.9f);
            case Sfx.SMG: return gunshot(rng, 0.22f, 1900f, 200f, 0.75f);
            case Sfx.SHOTGUN: return gunshot(rng, 0.6f, 900f, 90f, 1.0f);
            case Sfx.RIFLE: return gunshot(rng, 0.5f, 2200f, 120f, 1.0f);
            case Sfx.NPC_SHOT: return gunshot(rng, 0.3f, 1200f, 140f, 0.7f);
            case Sfx.DRY_FIRE: return click(rng, 0.05f, 3000f, 0.5f);
            case Sfx.MAG_OUT: return twoClicks(rng, 0.18f, 1800f, 900f);
            case Sfx.MAG_IN: return twoClicks(rng, 0.16f, 1200f, 2600f);
            case Sfx.SLIDE: return slide(rng, 0.25f);
            case Sfx.PUMP: return slide(rng, 0.35f);
            case Sfx.SHELL_IN: return click(rng, 0.09f, 1500f, 0.6f);
            case Sfx.STEP: return thump(rng, 0.12f, 90f, 0.35f, 0.6f);
            case Sfx.DOOR_OPEN: return creak(rng, 0.7f);
            case Sfx.DOOR_SLIDE: return hiss(rng, 0.8f, 0.35f);
            case Sfx.BUTTON: return beepTone(0.06f, 1200f, 0.4f);
            case Sfx.LEVER: return twoClicks(rng, 0.3f, 700f, 400f);
            case Sfx.BEEP: return beepTone(0.12f, 880f, 0.35f);
            case Sfx.CARD_OK: return twoTone(0.25f, 990f, 1320f);
            case Sfx.CARD_DENIED: return twoTone(0.3f, 330f, 220f);
            case Sfx.IMPACT_METAL: return metal(rng, 0.35f);
            case Sfx.IMPACT_WOOD: return thump(rng, 0.15f, 220f, 0.6f, 0.5f);
            case Sfx.IMPACT_SOFT: return thump(rng, 0.1f, 120f, 0.4f, 0.4f);
            case Sfx.BREAK_WOOD: return crack(rng, 0.4f, 0.5f);
            case Sfx.BREAK_GLASS: return glass(rng, 0.5f);
            case Sfx.BULLET_WALL: return click(rng, 0.08f, 4000f, 0.5f);
            case Sfx.NPC_ALERT: return radioVoice(rng, 0.55f, 520f);
            case Sfx.NPC_PAIN: return radioVoice(rng, 0.25f, 380f);
            case Sfx.NPC_DEATH: return radioVoice(rng, 0.6f, 260f);
            case Sfx.PLAYER_HURT: return thump(rng, 0.25f, 70f, 0.8f, 0.9f);
            case Sfx.ALARM: return alarm(1.2f);
            case Sfx.AMB_WIND: return wind(rng, 3.0f);
            case Sfx.AMB_HUM: return hum(rng, 2.0f);
            case Sfx.PICKUP: return twoTone(0.12f, 660f, 990f);
            case Sfx.OBJECTIVE: return arpeggio(0.6f);
            case Sfx.WHIZ: return whiz(rng, 0.18f);
            case Sfx.UI_HOVER: return beepTone(0.03f, 1600f, 0.15f);
            case Sfx.UI_CLICK: return beepTone(0.05f, 1000f, 0.35f);
            case Sfx.TELEPORT: return sweep(0.25f, 300f, 1200f, 0.3f);
            case Sfx.HOLSTER: return slide(rng, 0.15f);
            case Sfx.VICTORY: return arpeggio(1.4f);
            case Sfx.HEAL: return sweep(0.4f, 400f, 900f, 0.3f);
            case Sfx.GRAB: return thump(rng, 0.06f, 300f, 0.3f, 0.3f);
            default: return beepTone(0.05f, 440f, 0.2f);
        }
    }

    private static short[] alloc(float seconds) { return new short[Math.max(1, (int) (seconds * RATE))]; }

    private static short s(float v) {
        if (v > 1f) v = 1f; else if (v < -1f) v = -1f;
        return (short) (v * 32000f);
    }

    private static short[] gunshot(MathUtil.Rng rng, float len, float cutoff, float boomHz, float gain) {
        short[] o = alloc(len);
        float lp = 0f, lp2 = 0f;
        float a = cutoff / (cutoff + RATE / 6.28f);
        for (int i = 0; i < o.length; i++) {
            float t = i / (float) RATE;
            float env = (float) Math.exp(-t * 18f);
            float n = rng.range(-1f, 1f);
            lp += (n - lp) * a;
            lp2 += (lp - lp2) * 0.15f;
            float crack = i < RATE * 0.004f ? rng.range(-1f, 1f) : 0f;
            float boom = (float) Math.sin(2 * Math.PI * boomHz * t * (1f - t)) * (float) Math.exp(-t * 10f);
            float tail = lp2 * (float) Math.exp(-t * 5f) * 0.6f;
            o[i] = s(gain * (crack * 0.9f + lp * env * 1.2f + boom * 0.8f + tail));
        }
        return o;
    }

    private static short[] click(MathUtil.Rng rng, float len, float freq, float gain) {
        short[] o = alloc(len);
        for (int i = 0; i < o.length; i++) {
            float t = i / (float) RATE;
            float env = (float) Math.exp(-t * 60f);
            o[i] = s(gain * env * ((float) Math.sin(2 * Math.PI * freq * t) * 0.6f + rng.range(-0.5f, 0.5f)));
        }
        return o;
    }

    private static short[] twoClicks(MathUtil.Rng rng, float len, float f1, float f2) {
        short[] o = alloc(len);
        int mid = o.length / 2;
        for (int i = 0; i < o.length; i++) {
            int k = i < mid ? i : i - mid;
            float t = k / (float) RATE;
            float f = i < mid ? f1 : f2;
            float env = (float) Math.exp(-t * 70f);
            o[i] = s(0.6f * env * ((float) Math.sin(2 * Math.PI * f * t) * 0.5f + rng.range(-0.5f, 0.5f)));
        }
        return o;
    }

    private static short[] slide(MathUtil.Rng rng, float len) {
        short[] o = alloc(len);
        float lp = 0;
        for (int i = 0; i < o.length; i++) {
            float t = i / (float) RATE, k = t / len;
            float env = k < 0.6f ? 0.3f : (float) Math.exp(-(k - 0.6f) * 30f);
            lp += (rng.range(-1f, 1f) - lp) * 0.3f;
            float snap = Math.abs(k - 0.62f) < 0.02f ? rng.range(-1f, 1f) : 0f;
            o[i] = s(0.6f * (lp * env + snap));
        }
        return o;
    }

    private static short[] thump(MathUtil.Rng rng, float len, float freq, float noise, float gain) {
        short[] o = alloc(len);
        float lp = 0;
        for (int i = 0; i < o.length; i++) {
            float t = i / (float) RATE;
            float env = (float) Math.exp(-t * 25f);
            lp += (rng.range(-1f, 1f) - lp) * 0.2f;
            o[i] = s(gain * env * ((float) Math.sin(2 * Math.PI * freq * t) + lp * noise));
        }
        return o;
    }

    private static short[] creak(MathUtil.Rng rng, float len) {
        short[] o = alloc(len);
        float phase = 0;
        for (int i = 0; i < o.length; i++) {
            float t = i / (float) RATE, k = t / len;
            float f = 180f + 90f * (float) Math.sin(k * 7f) + 40f * k;
            phase += f / RATE;
            float saw = 2f * (phase - (float) Math.floor(phase)) - 1f;
            float env = (float) Math.sin(Math.PI * k) * 0.35f;
            o[i] = s(env * (saw * 0.6f + rng.range(-0.2f, 0.2f)));
        }
        return o;
    }

    private static short[] hiss(MathUtil.Rng rng, float len, float gain) {
        short[] o = alloc(len);
        float lp = 0;
        for (int i = 0; i < o.length; i++) {
            float k = i / (float) o.length;
            lp += (rng.range(-1f, 1f) - lp) * 0.5f;
            float env = (float) Math.sin(Math.PI * k);
            float motor = (float) Math.sin(2 * Math.PI * 110 * i / RATE) * 0.3f;
            o[i] = s(gain * env * (lp + motor));
        }
        return o;
    }

    private static short[] beepTone(float len, float f, float gain) {
        short[] o = alloc(len);
        for (int i = 0; i < o.length; i++) {
            float t = i / (float) RATE, k = t / len;
            float env = Math.min(1f, k * 20f) * Math.min(1f, (1f - k) * 10f);
            o[i] = s(gain * env * (float) Math.sin(2 * Math.PI * f * t));
        }
        return o;
    }

    private static short[] twoTone(float len, float f1, float f2) {
        short[] o = alloc(len);
        for (int i = 0; i < o.length; i++) {
            float t = i / (float) RATE, k = t / len;
            float f = k < 0.5f ? f1 : f2;
            float env = Math.min(1f, (1f - k) * 8f);
            o[i] = s(0.35f * env * (float) Math.sin(2 * Math.PI * f * t));
        }
        return o;
    }

    private static short[] metal(MathUtil.Rng rng, float len) {
        short[] o = alloc(len);
        float[] fr = {523f, 1187f, 1960f, 2810f};
        for (int i = 0; i < o.length; i++) {
            float t = i / (float) RATE;
            float v = 0;
            for (int k = 0; k < fr.length; k++) v += (float) Math.sin(2 * Math.PI * fr[k] * t) * (float) Math.exp(-t * (8f + k * 6f));
            v += rng.range(-1f, 1f) * (float) Math.exp(-t * 80f);
            o[i] = s(0.3f * v);
        }
        return o;
    }

    private static short[] crack(MathUtil.Rng rng, float len, float gain) {
        short[] o = alloc(len);
        float lp = 0;
        for (int i = 0; i < o.length; i++) {
            float t = i / (float) RATE;
            float burst = ((i / 400) % 3 == 0) ? 1f : 0.3f;
            lp += (rng.range(-1f, 1f) - lp) * 0.4f;
            o[i] = s(gain * burst * lp * (float) Math.exp(-t * 7f));
        }
        return o;
    }

    private static short[] glass(MathUtil.Rng rng, float len) {
        short[] o = alloc(len);
        for (int i = 0; i < o.length; i++) {
            float t = i / (float) RATE;
            float v = rng.range(-1f, 1f) * (float) Math.exp(-t * 12f) * 0.5f;
            v += (float) Math.sin(2 * Math.PI * (3100 + 400 * Math.sin(t * 40)) * t) * (float) Math.exp(-t * 9f) * 0.3f;
            o[i] = s(v);
        }
        return o;
    }

    /** Filtered, band-limited "radio voice" burble so enemies sound like they talk over comms. */
    private static short[] radioVoice(MathUtil.Rng rng, float len, float base) {
        short[] o = alloc(len);
        float phase = 0, lp = 0;
        for (int i = 0; i < o.length; i++) {
            float t = i / (float) RATE, k = t / len;
            float f = base * (1f + 0.25f * (float) Math.sin(t * 23f) + 0.1f * (float) Math.sin(t * 61f));
            phase += f / RATE;
            float saw = 2f * (phase - (float) Math.floor(phase)) - 1f;
            float syll = 0.5f + 0.5f * (float) Math.sin(t * 2 * Math.PI * 7f);
            lp += (saw * syll - lp) * 0.25f;
            float squelch = (k < 0.04f || k > 0.96f) ? rng.range(-0.6f, 0.6f) : 0f;
            o[i] = s(0.45f * (lp + rng.range(-0.08f, 0.08f) + squelch));
        }
        return o;
    }

    private static short[] alarm(float len) {
        short[] o = alloc(len);
        for (int i = 0; i < o.length; i++) {
            float t = i / (float) RATE, k = t / len;
            float f = k < 0.5f ? 620f + 300f * k * 2f : 920f - 300f * (k - 0.5f) * 2f;
            o[i] = s(0.3f * (float) Math.signum(Math.sin(2 * Math.PI * f * t)) * 0.6f);
        }
        return o;
    }

    private static short[] wind(MathUtil.Rng rng, float len) {
        short[] o = alloc(len);
        float lp = 0, lp2 = 0;
        int n = o.length;
        for (int i = 0; i < n; i++) {
            float k = i / (float) n;
            lp += (rng.range(-1f, 1f) - lp) * 0.05f;
            lp2 += (lp - lp2) * 0.1f;
            float gust = 0.6f + 0.4f * (float) Math.sin(k * Math.PI * 2 * 2);
            o[i] = s(lp2 * 2.5f * gust);
        }
        crossfadeLoop(o);
        return o;
    }

    private static short[] hum(MathUtil.Rng rng, float len) {
        short[] o = alloc(len);
        for (int i = 0; i < o.length; i++) {
            float t = i / (float) RATE;
            float v = (float) Math.sin(2 * Math.PI * 60 * t) * 0.15f + (float) Math.sin(2 * Math.PI * 120 * t) * 0.08f
                    + rng.range(-0.03f, 0.03f);
            o[i] = s(v);
        }
        return o;
    }

    private static void crossfadeLoop(short[] o) {
        int f = Math.min(o.length / 4, RATE / 4);
        for (int i = 0; i < f; i++) {
            float k = i / (float) f;
            int j = o.length - f + i;
            o[i] = (short) (o[i] * k + o[j] * (1f - k));
        }
    }

    private static short[] arpeggio(float len) {
        short[] o = alloc(len);
        float[] notes = {523.25f, 659.25f, 783.99f, 1046.5f};
        int per = o.length / notes.length;
        for (int i = 0; i < o.length; i++) {
            int nIdx = Math.min(notes.length - 1, i / per);
            float t = (i - nIdx * per) / (float) RATE;
            float env = (float) Math.exp(-t * 6f);
            o[i] = s(0.3f * env * ((float) Math.sin(2 * Math.PI * notes[nIdx] * t) + 0.3f * (float) Math.sin(4 * Math.PI * notes[nIdx] * t)));
        }
        return o;
    }

    private static short[] whiz(MathUtil.Rng rng, float len) {
        short[] o = alloc(len);
        float lp = 0;
        for (int i = 0; i < o.length; i++) {
            float k = i / (float) o.length;
            lp += (rng.range(-1f, 1f) - lp) * (0.6f - 0.4f * k);
            o[i] = s(0.4f * lp * (float) Math.sin(Math.PI * k));
        }
        return o;
    }

    private static short[] sweep(float len, float f0, float f1, float gain) {
        short[] o = alloc(len);
        float phase = 0;
        for (int i = 0; i < o.length; i++) {
            float k = i / (float) o.length;
            phase += (f0 + (f1 - f0) * k) / RATE;
            o[i] = s(gain * (float) Math.sin(2 * Math.PI * phase) * (float) Math.sin(Math.PI * k));
        }
        return o;
    }

    /** Encodes PCM as a WAV byte array (used by the Android SoundPool loader). */
    public static byte[] toWav(short[] pcm) {
        int dataLen = pcm.length * 2;
        byte[] b = new byte[44 + dataLen];
        putStr(b, 0, "RIFF"); putInt(b, 4, 36 + dataLen); putStr(b, 8, "WAVE");
        putStr(b, 12, "fmt "); putInt(b, 16, 16); putShort(b, 20, 1); putShort(b, 22, 1);
        putInt(b, 24, RATE); putInt(b, 28, RATE * 2); putShort(b, 32, 2); putShort(b, 34, 16);
        putStr(b, 36, "data"); putInt(b, 40, dataLen);
        for (int i = 0; i < pcm.length; i++) { b[44 + i * 2] = (byte) (pcm[i] & 255); b[45 + i * 2] = (byte) ((pcm[i] >> 8) & 255); }
        return b;
    }

    private static void putStr(byte[] b, int o, String s) { for (int i = 0; i < 4; i++) b[o + i] = (byte) s.charAt(i); }

    private static void putInt(byte[] b, int o, int v) { b[o] = (byte) v; b[o + 1] = (byte) (v >> 8); b[o + 2] = (byte) (v >> 16); b[o + 3] = (byte) (v >> 24); }

    private static void putShort(byte[] b, int o, int v) { b[o] = (byte) v; b[o + 1] = (byte) (v >> 8); }
}
