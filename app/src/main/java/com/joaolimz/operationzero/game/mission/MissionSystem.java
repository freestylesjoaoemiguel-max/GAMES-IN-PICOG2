package com.joaolimz.operationzero.game.mission;

/** Linear campaign objectives of "Operation Zero". */
public final class MissionSystem {
    public static final int COUNT = 6;
    public static final String[] TITLES = {
            "Entre na instalação.",
            "Encontre o cartão de acesso.",
            "Abra o laboratório.",
            "Recupere o dispositivo.",
            "Elimine os inimigos.",
            "Escape da instalação."
    };
    public static final String[] HINTS = {
            "Pegue a pistola na mesa e puxe a alavanca de energia ao lado do portão.",
            "O cartão está na sala da gerência, no escritório (oeste da recepção).",
            "Libere o corredor no terminal da segurança e use o cartão no leitor do laboratório.",
            "Desça ao subsolo, ligue a energia do laboratório secreto e pegue o dispositivo.",
            "Alarme ativado! Elimine os reforços no subsolo.",
            "Siga pelo túnel de emergência até a garagem, abra o portão e vá ao ponto de extração."
    };
    public static final String[] EVENTS = {"enter_lobby", "pickup_keycard", "lab_door_opened", "pickup_device", "alarm_cleared", "exit_reached"};

    public int current;
    public int completedMask;
    public String toast;
    public float toastTimer;

    public void reset(int startMission) {
        current = Math.max(0, Math.min(COUNT, startMission));
        completedMask = 0;
        for (int i = 0; i < current; i++) completedMask |= 1 << i;
        toast = current < COUNT ? "MISSÃO " + (current + 1) + ": " + TITLES[current] : null;
        toastTimer = 5f;
    }

    /** Returns true when the event completed the current objective. */
    public boolean onEvent(String event) {
        if (current >= COUNT || event == null) return false;
        if (!EVENTS[current].equals(event)) return false;
        completedMask |= 1 << current;
        current++;
        if (current < COUNT) {
            toast = "MISSÃO " + (current + 1) + ": " + TITLES[current];
        } else {
            toast = "CAMPANHA CONCLUÍDA";
        }
        toastTimer = 6f;
        return true;
    }

    public boolean finished() { return current >= COUNT; }

    public String objective() { return current < COUNT ? TITLES[current] : "Missão cumprida"; }

    public String hint() { return current < COUNT ? HINTS[current] : ""; }

    public void update(float dt) {
        if (toastTimer > 0f) toastTimer -= dt;
    }

    public void showToast(String text, float seconds) {
        toast = text;
        toastTimer = seconds;
    }
}
