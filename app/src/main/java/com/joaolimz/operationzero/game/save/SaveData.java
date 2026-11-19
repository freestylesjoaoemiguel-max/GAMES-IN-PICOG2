package com.joaolimz.operationzero.game.save;

/** Campaign checkpoint (written on every mission completion). */
public final class SaveData {
    public static final String FILE = "savegame.dat";

    public int mission;
    public int health = 100;
    public final int[] reserve = new int[4];
    /** Weapon type per inventory slot (-1 none): 0 = hip, 1 = back. */
    public final int[] slotWeapon = {-1, -1};
    public final int[] slotRounds = {0, 0};
    public final boolean[] slotChambered = {false, false};
    public boolean hasKeycard, hasDevice;
    public int completedMask;
    public float playTime;
    public int kills;

    public String serialize() {
        KeyValue kv = new KeyValue();
        kv.put("version", 1).put("mission", mission).put("health", health)
                .putIntArray("reserve", reserve)
                .putIntArray("slotWeapon", slotWeapon)
                .putIntArray("slotRounds", slotRounds)
                .put("chambered0", slotChambered[0]).put("chambered1", slotChambered[1])
                .put("keycard", hasKeycard).put("device", hasDevice)
                .put("completed", completedMask).put("time", playTime).put("kills", kills);
        return kv.serialize();
    }

    public static SaveData parse(String text) {
        if (text == null || text.length() == 0) return null;
        KeyValue kv = KeyValue.parse(text);
        if (!kv.has("mission")) return null;
        SaveData d = new SaveData();
        d.mission = kv.getInt("mission", 0);
        d.health = kv.getInt("health", 100);
        copy(kv.getIntArray("reserve", d.reserve), d.reserve);
        copy(kv.getIntArray("slotWeapon", d.slotWeapon), d.slotWeapon);
        copy(kv.getIntArray("slotRounds", d.slotRounds), d.slotRounds);
        d.slotChambered[0] = kv.getBool("chambered0", false);
        d.slotChambered[1] = kv.getBool("chambered1", false);
        d.hasKeycard = kv.getBool("keycard", false);
        d.hasDevice = kv.getBool("device", false);
        d.completedMask = kv.getInt("completed", 0);
        d.playTime = kv.getFloat("time", 0f);
        d.kills = kv.getInt("kills", 0);
        return d;
    }

    private static void copy(int[] src, int[] dst) {
        for (int i = 0; i < dst.length && i < src.length; i++) dst[i] = src[i];
    }
}
