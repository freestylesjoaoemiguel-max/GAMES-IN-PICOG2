package com.joaolimz.pegapega.game.audio;

/** Sound effect identifiers (all synthesized by {@link SoundSynth}; optional overrides assets/audio/NAME.ogg). */
public final class Sfx {
    private Sfx() { }

    public static final int TAP_SOFT = 0, TAP_METAL = 1, TAP_WOOD = 2, TAG = 3, INFECTED = 4, WHISTLE = 5, ROUND_END = 6;
    public static final int WIND = 7, AMBIENT = 8, UI_HOVER = 9, UI_CLICK = 10, JUMP = 11, LAND = 12, CHIRP = 13;
    public static final int COUNTDOWN = 14, STEP = 15, ALARM = 16, WIN = 17;
    public static final int COUNT = 18;

    public static final String[] NAMES = {
            "tap_soft", "tap_metal", "tap_wood", "tag", "infected", "whistle", "round_end",
            "wind", "ambient", "ui_hover", "ui_click", "jump", "land", "chirp", "countdown", "step", "alarm", "win"
    };
}
