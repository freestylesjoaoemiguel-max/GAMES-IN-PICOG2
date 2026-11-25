package com.joaolimz.operationzero.game.save;

import java.util.LinkedHashMap;
import java.util.Map;

/** Tiny dependency-free key=value serializer used for settings and save games. */
public final class KeyValue {
    private final LinkedHashMap<String, String> map = new LinkedHashMap<String, String>();

    public static KeyValue parse(String text) {
        KeyValue kv = new KeyValue();
        if (text == null) return kv;
        String[] lines = text.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.length() == 0 || line.startsWith("#")) continue;
            int eq = line.indexOf('=');
            if (eq <= 0) continue;
            kv.map.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
        }
        return kv;
    }

    public KeyValue put(String k, String v) { map.put(k, v); return this; }
    public KeyValue put(String k, int v) { map.put(k, Integer.toString(v)); return this; }
    public KeyValue put(String k, float v) { map.put(k, Float.toString(v)); return this; }
    public KeyValue put(String k, boolean v) { map.put(k, v ? "1" : "0"); return this; }

    public boolean has(String k) { return map.containsKey(k); }

    public String getString(String k, String def) { String v = map.get(k); return v != null ? v : def; }

    public int getInt(String k, int def) {
        String v = map.get(k);
        if (v == null) return def;
        try { return Integer.parseInt(v); } catch (NumberFormatException e) { return def; }
    }

    public float getFloat(String k, float def) {
        String v = map.get(k);
        if (v == null) return def;
        try { return Float.parseFloat(v); } catch (NumberFormatException e) { return def; }
    }

    public boolean getBool(String k, boolean def) {
        String v = map.get(k);
        if (v == null) return def;
        return "1".equals(v) || "true".equalsIgnoreCase(v);
    }

    public int[] getIntArray(String k, int[] def) {
        String v = map.get(k);
        if (v == null || v.length() == 0) return def;
        String[] p = v.split(",");
        int[] out = new int[p.length];
        try {
            for (int i = 0; i < p.length; i++) out[i] = Integer.parseInt(p[i].trim());
        } catch (NumberFormatException e) { return def; }
        return out;
    }

    public KeyValue putIntArray(String k, int[] a) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < a.length; i++) { if (i > 0) sb.append(','); sb.append(a[i]); }
        map.put(k, sb.toString());
        return this;
    }

    public String serialize() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : map.entrySet()) sb.append(e.getKey()).append('=').append(e.getValue()).append('\n');
        return sb.toString();
    }
}
