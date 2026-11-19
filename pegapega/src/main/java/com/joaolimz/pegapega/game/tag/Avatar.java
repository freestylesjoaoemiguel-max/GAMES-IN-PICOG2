package com.joaolimz.pegapega.game.tag;

import com.joaolimz.pegapega.game.math.Vec3;

/** A participant of the round (the local player or a bot). */
public final class Avatar {
    public static final int[] COLORS = {0xE5483F, 0x3F7FE5, 0xF2C230, 0x46B85A, 0x9B5FE0, 0xF08A24, 0x2BC4C4, 0xF06CB0, 0xE8E8E8, 0x8A5A3A};
    public static final String[] COLOR_NAMES = {"Vermelho", "Azul", "Amarelo", "Verde", "Roxo", "Laranja", "Ciano", "Rosa", "Branco", "Marrom"};
    public static final String[] BOT_NAMES = {"Pipoca", "Ferrugem", "Bolacha", "Parafuso", "Tico", "Zumbi", "Faísca", "Mola", "Chiclete", "Biscoito"};

    public final int id;
    public final boolean isPlayer;
    public String name;
    public int color;
    /** Eye/head position (top of the body). */
    public final Vec3 head = new Vec3();
    public final Vec3 vel = new Vec3();
    public float yaw;
    public final Vec3 handL = new Vec3(), handR = new Vec3();
    public final Vec3 shoulderL = new Vec3(), shoulderR = new Vec3();
    public boolean it;
    public float immunity;
    public float timeIt;
    public float survived;
    public int tags;
    public boolean taggedThisRound;
    /** Flash timer when just tagged (visual). */
    public float tagFlash;

    public Avatar(int id, boolean isPlayer) { this.id = id; this.isPlayer = isPlayer; }

    public Vec3 body(Vec3 out) { return out.set(head.x, head.y - 0.3f, head.z); }

    public int colorRgb() { return COLORS[color % COLORS.length]; }
}
