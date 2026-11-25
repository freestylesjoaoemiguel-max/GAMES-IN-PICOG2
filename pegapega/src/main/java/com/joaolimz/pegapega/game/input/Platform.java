package com.joaolimz.pegapega.game.input;

/** Services the game needs from the host platform. Android implements it; tests use a stub. */
public interface Platform {
    /** Short vibration pulse (controller or device), strength 0..1. */
    void vibrate(int millis, float strength);

    /** Reads a stored text file or returns null. */
    String readFile(String name);

    /** Writes a text file atomically. Returns success. */
    boolean writeFile(String name, String content);

    boolean deleteFile(String name);

    /** Closes the application. */
    void quit();

    /** Requests head-tracking recenter on the platform side. */
    void recenter();

    /** Human-readable status of the head tracking source (shown in settings). */
    String trackingStatus();
}
