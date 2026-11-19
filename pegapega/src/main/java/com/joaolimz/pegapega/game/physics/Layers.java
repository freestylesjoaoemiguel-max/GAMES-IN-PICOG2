package com.joaolimz.pegapega.game.physics;

/** Collision / query layers (bit masks). */
public final class Layers {
    private Layers() { }

    public static final int STATIC = 1;
    public static final int PROP = 2;
    public static final int ITEM = 4;
    public static final int NPC = 8;
    public static final int PLAYER = 16;
    public static final int DEBRIS = 32;
    public static final int DOOR = 64;

    /** Everything that blocks bullets and line of sight. */
    public static final int BLOCK_SIGHT = STATIC | DOOR;
    /** Everything a hitscan weapon can hit. */
    public static final int SHOOTABLE = STATIC | DOOR | PROP | ITEM | NPC;
    /** Things the player capsule collides with. */
    public static final int PLAYER_COLLIDE = STATIC | DOOR;
}
