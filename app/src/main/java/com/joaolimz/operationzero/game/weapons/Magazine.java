package com.joaolimz.operationzero.game.weapons;

import com.joaolimz.operationzero.game.world.ItemKind;
import com.joaolimz.operationzero.game.world.Prop;

/** Physical magazine (or a single shotgun shell when capacity == 1). */
public final class Magazine extends Prop {
    public int ammo;
    public int rounds;
    public int capacity;
    /** Weapon this magazine is inserted into, or null. */
    public Weapon attachedTo;

    public Magazine setup(int ammoClass, int capacity, int rounds) {
        this.ammo = ammoClass;
        this.capacity = capacity;
        this.rounds = rounds;
        this.snapGrip = true;
        this.name = capacity == 1 ? "Cartucho 12" : "Carregador " + WeaponType.AMMO_NAMES[ammoClass];
        return this;
    }

    public boolean isShell() { return kind == ItemKind.SHELL; }
}
