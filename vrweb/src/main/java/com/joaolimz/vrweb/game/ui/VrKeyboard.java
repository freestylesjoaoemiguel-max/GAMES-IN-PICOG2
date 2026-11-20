package com.joaolimz.vrweb.game.ui;

import java.util.ArrayList;

/**
 * On-screen keyboard (Portuguese layout) with shift / caps lock, numbers and symbols, and an accents layer.
 * Pure logic: it produces text / key events through {@link Output}; drawing is done by the scene builder.
 */
public final class VrKeyboard {
    public interface Output {
        void text(String s);
        void backspace();
        void enter();
    }

    public static final int CHAR = 0, BACKSPACE = 1, ENTER = 2, SHIFT = 3, SYMBOLS = 4, ACCENTS = 5, SPACE = 6, LETTERS = 7;
    public static final int L_LOWER = 0, L_UPPER = 1, L_CAPS = 2, L_SYM = 3, L_ACC = 4;

    public static final class Key {
        public final String label;
        public final String text;
        public final int kind;
        public final float x, y, w, h;
        public boolean active;
        Key(String label, String text, int kind, float x, float y, float w, float h) {
            this.label = label; this.text = text; this.kind = kind; this.x = x; this.y = y; this.w = w; this.h = h;
        }
        public boolean contains(float lx, float ly) { return Math.abs(lx - x) <= w * 0.5f && Math.abs(ly - y) <= h * 0.5f; }
    }

    public final Surface surface = new Surface(1.9f, 0.62f);
    public final ArrayList<Key> keys = new ArrayList<Key>();
    public int layer = L_LOWER;
    public Output output;
    private float lastShiftTap = -10f;
    private float clock;

    public VrKeyboard() { build(); }

    public void tick(float dt) { clock += dt; }

    private static final String[] LOWER = {"1234567890", "qwertyuiop", "asdfghjklç", "zxcvbnm"};
    private static final String[] SYM = {"1234567890", "@#$%&*-+()", "!\"':;/?_=\\", "[]{}<>~"};
    private static final String[] ACC = {"1234567890", "áàâãéêíóôõ", "úüçÁÉÍÓÚÇ°", "ÀÂÃÊÔÕÜ"};

    private void build() {
        keys.clear();
        float W = surface.w, H = surface.h;
        float unit = W / 10f, rowH = H / 5f, gap = 0.008f;
        String[] rows = layer == L_SYM ? SYM : layer == L_ACC ? ACC : LOWER;
        boolean upper = layer == L_UPPER || layer == L_CAPS;
        float top = H * 0.5f - rowH * 0.5f;
        for (int r = 0; r < 3; r++) {
            String row = rows[r];
            for (int i = 0; i < row.length(); i++) {
                String ch = String.valueOf(row.charAt(i));
                if (upper && layer != L_SYM && layer != L_ACC && r > 0) ch = ch.toUpperCase();
                keys.add(new Key(ch, ch, CHAR, -W / 2 + unit * (i + 0.5f), top - rowH * r, unit - gap, rowH - gap));
            }
        }
        // row 3: [left special 1.5] 7 chars [backspace 1.5]
        float y3 = top - rowH * 3;
        String row3 = rows[3];
        boolean plain = layer == L_LOWER || layer == L_UPPER || layer == L_CAPS;
        if (plain) {
            Key sh = new Key(layer == L_CAPS ? "CAPS" : "Shift", "", SHIFT, -W / 2 + unit * 0.75f, y3, unit * 1.5f - gap, rowH - gap);
            sh.active = layer != L_LOWER;
            keys.add(sh);
        } else {
            keys.add(new Key("ABC", "", LETTERS, -W / 2 + unit * 0.75f, y3, unit * 1.5f - gap, rowH - gap));
        }
        for (int i = 0; i < row3.length(); i++) {
            String ch = String.valueOf(row3.charAt(i));
            if (upper && plain) ch = ch.toUpperCase();
            keys.add(new Key(ch, ch, CHAR, -W / 2 + unit * (1.5f + i + 0.5f), y3, unit - gap, rowH - gap));
        }
        keys.add(new Key("Apagar", "", BACKSPACE, -W / 2 + unit * 9.25f, y3, unit * 1.5f - gap, rowH - gap));
        // row 4: ?123 / ABC, acentos, virgula, espaco, ponto, enter
        float y4 = top - rowH * 4;
        keys.add(new Key(layer == L_SYM ? "ABC" : "123#", "", layer == L_SYM ? LETTERS : SYMBOLS, -W / 2 + unit * 0.75f, y4, unit * 1.5f - gap, rowH - gap));
        Key acc = new Key(layer == L_ACC ? "ABC" : "ã é ç", "", layer == L_ACC ? LETTERS : ACCENTS, -W / 2 + unit * 2.25f, y4, unit * 1.5f - gap, rowH - gap);
        keys.add(acc);
        keys.add(new Key(",", ",", CHAR, -W / 2 + unit * 3.5f, y4, unit - gap, rowH - gap));
        keys.add(new Key("Espaço", " ", SPACE, -W / 2 + unit * 6.25f, y4, unit * 3.5f - gap, rowH - gap));
        keys.add(new Key(".", ".", CHAR, -W / 2 + unit * 8.5f, y4, unit - gap, rowH - gap));
        keys.add(new Key("Enter", "", ENTER, -W / 2 + unit * 9.25f, y4, unit * 1.5f - gap, rowH - gap));
    }

    public Key keyAt(float lx, float ly) {
        for (int i = 0; i < keys.size(); i++) if (keys.get(i).contains(lx, ly)) return keys.get(i);
        return null;
    }

    /** Presses a key: emits output and updates the layer (shift is one-shot, double tap = caps lock). */
    public void press(Key k) {
        if (k == null) return;
        switch (k.kind) {
            case CHAR:
            case SPACE:
                if (output != null) output.text(k.text);
                if (layer == L_UPPER) { layer = L_LOWER; build(); }
                break;
            case BACKSPACE:
                if (output != null) output.backspace();
                break;
            case ENTER:
                if (output != null) output.enter();
                break;
            case SHIFT:
                if (layer == L_LOWER) { layer = L_UPPER; lastShiftTap = clock; }
                else if (layer == L_UPPER) { layer = clock - lastShiftTap < 0.45f ? L_CAPS : L_LOWER; lastShiftTap = clock; }
                else layer = L_LOWER;
                build();
                break;
            case SYMBOLS: layer = L_SYM; build(); break;
            case ACCENTS: layer = L_ACC; build(); break;
            case LETTERS: layer = L_LOWER; build(); break;
            default: break;
        }
    }

    public boolean repeats(Key k) { return k != null && k.kind == BACKSPACE; }
}
