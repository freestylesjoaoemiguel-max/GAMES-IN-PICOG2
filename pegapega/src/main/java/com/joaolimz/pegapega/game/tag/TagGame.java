package com.joaolimz.pegapega.game.tag;

import com.joaolimz.pegapega.game.math.MathUtil;
import com.joaolimz.pegapega.game.math.Vec3;

import java.util.ArrayList;

/**
 * Round rules. INFECTION: whoever gets tagged joins the infected; infected win when everybody is caught,
 * survivors win when the timer ends. CLASSIC: one "pegador"; tagging passes it on (no tag-backs for a
 * moment); whoever spent the least time as pegador wins. FREE: no tagging, just explore.
 */
public final class TagGame {
    public static final int INFECTION = 0, CLASSIC = 1, FREE = 2;
    public static final String[] MODE_NAMES = {"INFECÇÃO", "PEGA-PEGA", "TREINO LIVRE"};
    public static final int COUNTDOWN = 0, PLAYING = 1, OVER = 2;

    public interface Listener {
        void onTag(Avatar chaser, Avatar victim);
        void onRoundStart();
        void onRoundOver(String result);
    }

    public final ArrayList<Avatar> avatars = new ArrayList<Avatar>();
    public int mode = INFECTION;
    public int phase = COUNTDOWN;
    public float phaseTime;
    public float roundLength = 150f;
    public float timeLeft;
    public int round;
    public String result = "";
    public Listener listener;
    private final MathUtil.Rng rng = new MathUtil.Rng(77);
    private final Vec3 a = new Vec3(), b = new Vec3();

    public void startRound() {
        phase = COUNTDOWN;
        phaseTime = 3f;
        round++;
        result = "";
        roundLength = mode == CLASSIC ? 120f : 150f;
        timeLeft = roundLength;
        for (Avatar v : avatars) {
            v.it = false; v.immunity = 0f; v.timeIt = 0f; v.survived = 0f; v.tags = 0; v.taggedThisRound = false; v.tagFlash = 0f;
        }
        if (mode != FREE && avatars.size() > 1) {
            Avatar first = avatars.get(rng.nextInt(avatars.size()));
            first.it = true;
            first.immunity = 3f;
        }
    }

    public int countIt() {
        int c = 0;
        for (Avatar v : avatars) if (v.it) c++;
        return c;
    }

    public void update(float dt) {
        phaseTime -= dt;
        for (Avatar v : avatars) {
            if (v.immunity > 0f) v.immunity -= dt;
            if (v.tagFlash > 0f) v.tagFlash -= dt;
        }
        if (phase == COUNTDOWN) {
            if (phaseTime <= 0f) {
                phase = PLAYING;
                if (listener != null) listener.onRoundStart();
            }
            return;
        }
        if (phase == OVER) {
            if (phaseTime <= 0f) startRound();
            return;
        }
        if (mode == FREE) return;
        timeLeft -= dt;
        for (Avatar v : avatars) {
            if (v.it) v.timeIt += dt; else v.survived += dt;
        }
        if (mode == INFECTION) {
            int it = countIt();
            if (it == avatars.size()) endRound("Todos foram infectados! Os infectados venceram.");
            else if (timeLeft <= 0f) endRound(survivorsText());
        } else if (timeLeft <= 0f) {
            Avatar best = null;
            for (Avatar v : avatars) if (best == null || v.timeIt < best.timeIt) best = v;
            endRound("Fim! Menos tempo como pegador: " + (best != null ? best.name : "-"));
        }
    }

    private String survivorsText() {
        StringBuilder sb = new StringBuilder("Tempo esgotado! Sobreviventes: ");
        boolean first = true;
        for (Avatar v : avatars) if (!v.it) { if (!first) sb.append(", "); sb.append(v.name); first = false; }
        return sb.toString();
    }

    private void endRound(String text) {
        phase = OVER;
        phaseTime = 7f;
        result = text;
        if (listener != null) listener.onRoundOver(text);
    }

    /** Attempts a tag between two avatars; returns true if it happened. */
    public boolean tryTag(Avatar chaser, Avatar victim) {
        if (phase != PLAYING || mode == FREE) return false;
        if (!chaser.it || victim.it || chaser == victim) return false;
        if (mode == CLASSIC && (victim.immunity > 0f || chaser.immunity > 0f)) return false;
        if (mode == INFECTION && chaser.immunity > 2.5f) return false;
        victim.it = true;
        victim.tagFlash = 1f;
        victim.taggedThisRound = true;
        chaser.tags++;
        if (mode == CLASSIC) {
            chaser.it = false;
            chaser.immunity = 3f; // no tag-backs
            victim.immunity = 1.5f;
        }
        if (listener != null) listener.onTag(chaser, victim);
        return true;
    }

    /** Checks contacts between every chaser and runner (bodies and hands). */
    public void checkContacts(float bodyReach, float handReach) {
        if (phase != PLAYING || mode == FREE) return;
        for (int i = 0; i < avatars.size(); i++) {
            Avatar c = avatars.get(i);
            if (!c.it) continue;
            for (int j = 0; j < avatars.size(); j++) {
                Avatar v = avatars.get(j);
                if (v.it || v == c) continue;
                c.body(a);
                v.body(b);
                boolean touch = a.distance(b) < bodyReach
                        || c.handL.distance(b) < handReach || c.handR.distance(b) < handReach;
                if (touch && tryTag(c, v)) return; // one tag per frame keeps classic mode fair
            }
        }
    }
}
