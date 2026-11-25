package com.joaolimz.operationzero.game.world;

/** Categories of grabbable objects. */
public final class ItemKind {
    private ItemKind() { }

    public static final int WEAPON = 0, MAGAZINE = 1, SHELL = 2, CRATE = 3, BARREL = 4, BOTTLE = 5, CAN = 6, TOOL = 7;
    public static final int KEYCARD = 8, DEVICE = 9, MEDKIT = 10, AMMO_BOX = 11, DEBRIS = 12;

    public static final String[] NAMES = {"Arma", "Carregador", "Cartucho", "Caixa", "Barril", "Garrafa", "Lata", "Ferramenta",
            "Cartao de acesso", "Dispositivo", "Kit medico", "Municao", "Destrocos"};

    /** Small items fit in chest pockets. */
    public static boolean isSmall(int kind) {
        return kind == KEYCARD || kind == DEVICE || kind == MEDKIT || kind == CAN || kind == SHELL || kind == MAGAZINE;
    }
}
