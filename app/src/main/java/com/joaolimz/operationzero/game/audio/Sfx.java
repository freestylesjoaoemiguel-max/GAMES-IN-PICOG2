package com.joaolimz.operationzero.game.audio;

/** Sound effect identifiers. Names map to optional override files assets/audio/NAME.ogg. */
public final class Sfx {
    private Sfx() { }

    public static final int PISTOL = 0, SMG = 1, SHOTGUN = 2, RIFLE = 3, NPC_SHOT = 4, DRY_FIRE = 5;
    public static final int MAG_OUT = 6, MAG_IN = 7, SLIDE = 8, PUMP = 9, SHELL_IN = 10;
    public static final int STEP = 11, DOOR_OPEN = 12, DOOR_SLIDE = 13, BUTTON = 14, LEVER = 15;
    public static final int BEEP = 16, CARD_OK = 17, CARD_DENIED = 18;
    public static final int IMPACT_METAL = 19, IMPACT_WOOD = 20, IMPACT_SOFT = 21, BREAK_WOOD = 22, BREAK_GLASS = 23;
    public static final int BULLET_WALL = 24, NPC_ALERT = 25, NPC_PAIN = 26, NPC_DEATH = 27, PLAYER_HURT = 28;
    public static final int ALARM = 29, AMB_WIND = 30, AMB_HUM = 31, PICKUP = 32, OBJECTIVE = 33, WHIZ = 34;
    public static final int UI_HOVER = 35, UI_CLICK = 36, TELEPORT = 37, HOLSTER = 38, VICTORY = 39, HEAL = 40, GRAB = 41;
    public static final int COUNT = 42;

    public static final String[] NAMES = {
            "pistol", "smg", "shotgun", "rifle", "npc_shot", "dry_fire",
            "mag_out", "mag_in", "slide", "pump", "shell_in",
            "step", "door_open", "door_slide", "button", "lever",
            "beep", "card_ok", "card_denied",
            "impact_metal", "impact_wood", "impact_soft", "break_wood", "break_glass",
            "bullet_wall", "npc_alert", "npc_pain", "npc_death", "player_hurt",
            "alarm", "amb_wind", "amb_hum", "pickup", "objective", "whiz",
            "ui_hover", "ui_click", "teleport", "holster", "victory", "heal", "grab"
    };
}
