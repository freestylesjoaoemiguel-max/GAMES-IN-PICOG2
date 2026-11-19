package com.joaolimz.operationzero.game.perf;

/** Frame statistics shown in the optional performance overlay. */
public final class PerfMonitor {
    public float fps;
    public float frameMs;
    public float updateMs;
    public int drawCalls;
    public int triangles;
    public int activeBodies;
    public int activeNpcs;
    public int aliveNpcs;
    public int activeSectors;
    public int effects;
    public int ragdolls;
    public int props;
    private float accum;
    private int frames;

    /** Call once per rendered frame with the frame delta. */
    public void frame(float dt) {
        accum += dt;
        frames++;
        if (accum >= 0.5f) {
            fps = frames / accum;
            frameMs = accum * 1000f / frames;
            accum = 0f;
            frames = 0;
        }
    }
}
