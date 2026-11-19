package com.joaolimz.pegapega.game.audio;

import com.joaolimz.pegapega.game.math.MathUtil;

/** Procedural, original sound design (16-bit mono, 22.05 kHz). */
public final class SoundSynth {
    public static final int RATE = 22050;

    private SoundSynth() { }

    public static short[] generate(int id) {
        MathUtil.Rng rng = new MathUtil.Rng(4321 + id * 977L);
        switch (id) {
            case Sfx.TAP_SOFT: return thump(rng, 0.12f, 140f, 0.55f, 0.8f);
            case Sfx.TAP_METAL: return metal(rng, 0.28f);
            case Sfx.TAP_WOOD: return knock(rng, 0.14f);
            case Sfx.TAG: return zap(0.35f);
            case Sfx.INFECTED: return sting(0.7f);
            case Sfx.WHISTLE: return whistle(0.6f);
            case Sfx.ROUND_END: return arpeggio(new float[]{784f, 659f, 523f, 392f}, 0.9f);
            case Sfx.WIN: return arpeggio(new float[]{523f, 659f, 784f, 1046f}, 1.0f);
            case Sfx.WIND: return wind(rng, 3f);
            case Sfx.AMBIENT: return ambient(rng, 4f);
            case Sfx.UI_HOVER: return tone(0.03f, 1600f, 0.15f);
            case Sfx.UI_CLICK: return tone(0.05f, 1000f, 0.35f);
            case Sfx.JUMP: return sweep(0.25f, 250f, 700f, 0.25f, rng);
            case Sfx.LAND: return thump(rng, 0.18f, 90f, 0.7f, 0.9f);
            case Sfx.CHIRP: return chirp(0.18f);
            case Sfx.COUNTDOWN: return tone(0.12f, 880f, 0.4f);
            case Sfx.STEP: return thump(rng, 0.08f, 120f, 0.4f, 0.4f);
            case Sfx.ALARM: return tone(0.5f, 660f, 0.3f);
            default: return tone(0.05f, 440f, 0.2f);
        }
    }

    private static short[] alloc(float s) { return new short[Math.max(1, (int) (s * RATE))]; }

    private static short s(float v) { return (short) (Math.max(-1f, Math.min(1f, v)) * 32000f); }

    private static short[] thump(MathUtil.Rng rng, float len, float f, float noise, float gain) {
        short[] o = alloc(len);
        float lp = 0;
        for (int i = 0; i < o.length; i++) {
            float t = i / (float) RATE;
            float env = (float) Math.exp(-t * 28f);
            lp += (rng.range(-1f, 1f) - lp) * 0.25f;
            o[i] = s(gain * env * ((float) Math.sin(2 * Math.PI * f * t * (1.4f - t)) + lp * noise));
        }
        return o;
    }

    private static short[] metal(MathUtil.Rng rng, float len) {
        short[] o = alloc(len);
        float[] fr = {640f, 1410f, 2320f, 3100f};
        for (int i = 0; i < o.length; i++) {
            float t = i / (float) RATE;
            float v = 0;
            for (int k = 0; k < fr.length; k++) v += (float) Math.sin(2 * Math.PI * fr[k] * t) * (float) Math.exp(-t * (10f + k * 7f));
            v += rng.range(-1f, 1f) * (float) Math.exp(-t * 90f);
            o[i] = s(0.25f * v);
        }
        return o;
    }

    private static short[] knock(MathUtil.Rng rng, float len) {
        short[] o = alloc(len);
        for (int i = 0; i < o.length; i++) {
            float t = i / (float) RATE;
            float v = (float) Math.sin(2 * Math.PI * 420 * t) * (float) Math.exp(-t * 35f) + 0.5f * (float) Math.sin(2 * Math.PI * 910 * t) * (float) Math.exp(-t * 60f);
            v += rng.range(-0.3f, 0.3f) * (float) Math.exp(-t * 120f);
            o[i] = s(0.5f * v);
        }
        return o;
    }

    private static short[] zap(float len) {
        short[] o = alloc(len);
        float phase = 0;
        for (int i = 0; i < o.length; i++) {
            float k = i / (float) o.length;
            float f = 1800f * (1f - k) + 200f;
            phase += f / RATE;
            float sq = (phase - (float) Math.floor(phase)) < 0.5f ? 1f : -1f;
            o[i] = s(0.3f * sq * (1f - k));
        }
        return o;
    }

    private static short[] sting(float len) {
        short[] o = alloc(len);
        float[] notes = {220f, 233f, 247f};
        for (int i = 0; i < o.length; i++) {
            float t = i / (float) RATE, k = t / len;
            float v = 0;
            for (float n : notes) v += (float) Math.sin(2 * Math.PI * n * t * (1f + 0.02f * (float) Math.sin(t * 30f)));
            o[i] = s(0.22f * v * (1f - k) * Math.min(1f, k * 20f));
        }
        return o;
    }

    private static short[] whistle(float len) {
        short[] o = alloc(len);
        float phase = 0;
        for (int i = 0; i < o.length; i++) {
            float t = i / (float) RATE, k = t / len;
            float f = 2100f + 120f * (float) Math.sin(t * 2 * Math.PI * 28f);
            phase += f / RATE;
            float env = Math.min(1f, k * 15f) * Math.min(1f, (1f - k) * 6f);
            o[i] = s(0.3f * env * (float) Math.sin(2 * Math.PI * phase));
        }
        return o;
    }

    private static short[] arpeggio(float[] notes, float len) {
        short[] o = alloc(len);
        int per = o.length / notes.length;
        for (int i = 0; i < o.length; i++) {
            int n = Math.min(notes.length - 1, i / per);
            float t = (i - n * per) / (float) RATE;
            float env = (float) Math.exp(-t * 5f);
            o[i] = s(0.3f * env * ((float) Math.sin(2 * Math.PI * notes[n] * t) + 0.3f * (float) Math.sin(4 * Math.PI * notes[n] * t)));
        }
        return o;
    }

    private static short[] wind(MathUtil.Rng rng, float len) {
        short[] o = alloc(len);
        float lp = 0, lp2 = 0;
        for (int i = 0; i < o.length; i++) {
            lp += (rng.range(-1f, 1f) - lp) * 0.08f;
            lp2 += (lp - lp2) * 0.15f;
            o[i] = s(lp2 * 3f);
        }
        crossfade(o);
        return o;
    }

    private static short[] ambient(MathUtil.Rng rng, float len) {
        short[] o = alloc(len);
        float lp = 0;
        for (int i = 0; i < o.length; i++) {
            float t = i / (float) RATE;
            lp += (rng.range(-1f, 1f) - lp) * 0.03f;
            float v = lp * 1.2f;
            // distant birds-like chirps
            float cyc = t % 1.3f;
            if (cyc < 0.08f) v += 0.12f * (float) Math.sin(2 * Math.PI * (2600 + 900 * cyc / 0.08f) * t) * (1f - cyc / 0.08f);
            o[i] = s(v * 0.6f);
        }
        crossfade(o);
        return o;
    }

    private static void crossfade(short[] o) {
        int f = Math.min(o.length / 4, RATE / 4);
        for (int i = 0; i < f; i++) {
            float k = i / (float) f;
            int j = o.length - f + i;
            o[i] = (short) (o[i] * k + o[j] * (1f - k));
        }
    }

    private static short[] tone(float len, float f, float gain) {
        short[] o = alloc(len);
        for (int i = 0; i < o.length; i++) {
            float t = i / (float) RATE, k = t / len;
            float env = Math.min(1f, k * 20f) * Math.min(1f, (1f - k) * 10f);
            o[i] = s(gain * env * (float) Math.sin(2 * Math.PI * f * t));
        }
        return o;
    }

    private static short[] sweep(float len, float f0, float f1, float gain, MathUtil.Rng rng) {
        short[] o = alloc(len);
        float phase = 0, lp = 0;
        for (int i = 0; i < o.length; i++) {
            float k = i / (float) o.length;
            phase += (f0 + (f1 - f0) * k) / RATE;
            lp += (rng.range(-1f, 1f) - lp) * 0.4f;
            o[i] = s(gain * ((float) Math.sin(2 * Math.PI * phase) * 0.4f + lp) * (float) Math.sin(Math.PI * k));
        }
        return o;
    }

    private static short[] chirp(float len) {
        short[] o = alloc(len);
        float phase = 0;
        for (int i = 0; i < o.length; i++) {
            float k = i / (float) o.length;
            float f = 900f + 1400f * (float) Math.sin(Math.PI * k * 2f);
            phase += f / RATE;
            o[i] = s(0.25f * (float) Math.sin(2 * Math.PI * phase) * (float) Math.sin(Math.PI * k));
        }
        return o;
    }

    public static byte[] toWav(short[] pcm) {
        int dataLen = pcm.length * 2;
        byte[] b = new byte[44 + dataLen];
        put(b, 0, "RIFF"); putInt(b, 4, 36 + dataLen); put(b, 8, "WAVE"); put(b, 12, "fmt ");
        putInt(b, 16, 16); putShort(b, 20, 1); putShort(b, 22, 1); putInt(b, 24, RATE); putInt(b, 28, RATE * 2);
        putShort(b, 32, 2); putShort(b, 34, 16); put(b, 36, "data"); putInt(b, 40, dataLen);
        for (int i = 0; i < pcm.length; i++) { b[44 + i * 2] = (byte) pcm[i]; b[45 + i * 2] = (byte) (pcm[i] >> 8); }
        return b;
    }

    private static void put(byte[] b, int o, String s) { for (int i = 0; i < 4; i++) b[o + i] = (byte) s.charAt(i); }

    private static void putInt(byte[] b, int o, int v) { b[o] = (byte) v; b[o + 1] = (byte) (v >> 8); b[o + 2] = (byte) (v >> 16); b[o + 3] = (byte) (v >> 24); }

    private static void putShort(byte[] b, int o, int v) { b[o] = (byte) v; b[o + 1] = (byte) (v >> 8); }
}
