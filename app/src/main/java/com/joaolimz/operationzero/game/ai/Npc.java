package com.joaolimz.operationzero.game.ai;

import com.joaolimz.operationzero.game.math.Vec3;

/** Enemy unit of the (fictional) Vantor security force. */
public final class Npc {
    public enum State { IDLE, PATROL, INVESTIGATE, ALERT, CHASE, ATTACK, COVER, SEARCH, DEAD }

    public static final int GUARD = 0, SOLDIER = 1, HEAVY = 2, MARKSMAN = 3;
    public static final String[] TYPE_NAMES = {"Vigia", "Soldado", "Pesado", "Atirador"};
    public static final float[] MAX_HP = {80f, 120f, 220f, 90f};
    public static final float[] DAMAGE = {8f, 6f, 20f, 18f};
    public static final float[] FIRE_INTERVAL = {0.55f, 0.12f, 1.1f, 1.7f};
    public static final int[] BURST = {2, 5, 1, 1};
    public static final float[] ACCURACY = {0.5f, 0.38f, 0.65f, 0.62f};
    public static final float[] RANGE = {25f, 20f, 12f, 45f};
    public static final float[] RUN = {3.2f, 3.5f, 2.6f, 3.0f};
    public static final float[] SIGHT = {22f, 24f, 20f, 34f};
    public static final float[] SCALE = {1f, 1f, 1.12f, 1f};

    public int type;
    public boolean alive;
    public boolean spawned;
    public float health;
    public final Vec3 pos = new Vec3();
    public float yaw;
    public final Vec3 vel = new Vec3();
    public final float[] vy = new float[1];
    public State state = State.IDLE;
    public float stateTime;
    public int sector;
    public String tag = "";
    public int[] patrol;
    public int patrolIdx;

    // navigation
    public final int[] path = new int[64];
    public int pathLen, pathIdx;
    public final Vec3 moveTarget = new Vec3();
    public boolean hasMoveTarget;
    public float repathTimer;
    public float stuckTimer;
    public final Vec3 lastPos = new Vec3();
    public float speed;

    // perception / memory
    public boolean canSee;
    public float seeTimer;
    public float perceptionTimer;
    public final Vec3 lastKnown = new Vec3();
    public float lastSeenTime = -100f;
    public final Vec3 investigate = new Vec3();
    public float suspicion;

    // combat
    public CoverPoint cover;
    public float crouch;
    public float peekTimer;
    public boolean peeking;
    public int burstLeft;
    public float fireCooldown;
    public float reaction;
    public float aimYaw, aimPitch;
    public float lastDamageTime = -100f;
    public float strafeDir = 1f;
    public float strafeTimer;
    public boolean retreating;
    public float searchTimer;
    public int searchCount;

    // animation
    public float walkPhase;
    public float hitFlash;
    public float flinch;
    public float muzzle;
    public float econAccum;
    public boolean economy;
    public float deadTime;

    public float maxHealth() { return MAX_HP[type]; }
}
