package com.joaolimz.operationzero;

import com.joaolimz.operationzero.game.input.Platform;

import java.util.HashMap;

/** In-memory platform used by JVM tests (no Android needed). */
public final class TestPlatform implements Platform {
    public final HashMap<String, String> files = new HashMap<String, String>();
    public int vibrations;
    public boolean quitCalled;
    public int recenters;

    @Override public void vibrate(int millis, float strength) { vibrations++; }
    @Override public String readFile(String name) { return files.get(name); }
    @Override public boolean writeFile(String name, String content) { files.put(name, content); return true; }
    @Override public boolean deleteFile(String name) { return files.remove(name) != null; }
    @Override public void quit() { quitCalled = true; }
    @Override public void recenter() { recenters++; }
    @Override public String trackingStatus() { return "teste"; }
}
