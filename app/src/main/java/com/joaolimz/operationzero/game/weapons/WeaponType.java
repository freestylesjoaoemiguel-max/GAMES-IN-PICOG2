package com.joaolimz.operationzero.game.weapons;

import com.joaolimz.operationzero.game.audio.Sfx;

/** Static weapon definitions. All names and designs are original. */
public final class WeaponType {
    public static final int AMMO_9MM = 0, AMMO_SMG = 1, AMMO_SHELL = 2, AMMO_RIFLE = 3;
    public static final String[] AMMO_NAMES = {"9mm", "SMG .45", "Cartucho 12", "7.62"};

    public static final int ACTION_SLIDE = 0, ACTION_PUMP = 1, ACTION_BOLT = 2;

    public final int id;
    public final String name;
    public final int ammo;
    public final int capacity;
    public final float damage;
    public final float fireInterval;
    public final boolean automatic;
    public final int pellets;
    public final float spreadDeg;
    public final float range;
    public final float recoilBack;
    public final float recoilPitchDeg;
    public final int hapticMs;
    public final boolean twoHanded;
    public final int action;
    public final int sound;
    public final float mass;
    public final float noiseRadius;
    /** Local points (weapon space, grip at origin). */
    public final float muzzleY, muzzleZ, magY, magZ, foreY, foreZ;
    public final float impulse;

    private WeaponType(int id, String name, int ammo, int capacity, float damage, float fireInterval, boolean automatic, int pellets,
                       float spreadDeg, float range, float recoilBack, float recoilPitchDeg, int hapticMs, boolean twoHanded, int action,
                       int sound, float mass, float noiseRadius, float muzzleY, float muzzleZ, float magY, float magZ,
                       float foreY, float foreZ, float impulse) {
        this.id = id; this.name = name; this.ammo = ammo; this.capacity = capacity; this.damage = damage;
        this.fireInterval = fireInterval; this.automatic = automatic; this.pellets = pellets; this.spreadDeg = spreadDeg;
        this.range = range; this.recoilBack = recoilBack; this.recoilPitchDeg = recoilPitchDeg; this.hapticMs = hapticMs;
        this.twoHanded = twoHanded; this.action = action; this.sound = sound; this.mass = mass; this.noiseRadius = noiseRadius;
        this.muzzleY = muzzleY; this.muzzleZ = muzzleZ; this.magY = magY; this.magZ = magZ; this.foreY = foreY; this.foreZ = foreZ;
        this.impulse = impulse;
    }

    public static final int PISTOL = 0, SMG = 1, SHOTGUN = 2, RIFLE = 3;

    /** VX-9: semi-automatic sidearm, light recoil. */
    public static final WeaponType VX9 = new WeaponType(PISTOL, "VX-9", AMMO_9MM, 12, 34f, 0.14f, false, 1, 0.6f, 60f,
            0.035f, 6f, 25, false, ACTION_SLIDE, Sfx.PISTOL, 0.9f, 28f, 0.035f, -0.15f, -0.095f, 0.004f, 0f, 0f, 2.5f);
    /** RAPTOR-45: compact automatic SMG, fast but inaccurate. */
    public static final WeaponType RAPTOR = new WeaponType(SMG, "RAPTOR-45", AMMO_SMG, 30, 21f, 0.085f, true, 1, 2.4f, 45f,
            0.02f, 2.8f, 18, true, ACTION_SLIDE, Sfx.SMG, 2.4f, 30f, 0.035f, -0.39f, -0.1f, -0.08f, -0.02f, -0.24f, 2f);
    /** BRUTUS-12: pump-action shotgun, 8 pellets, tube fed. */
    public static final WeaponType BRUTUS = new WeaponType(SHOTGUN, "BRUTUS-12", AMMO_SHELL, 6, 16f, 0.6f, false, 8, 6.5f, 28f,
            0.07f, 11f, 70, true, ACTION_PUMP, Sfx.SHOTGUN, 3.2f, 40f, 0.045f, -0.61f, 0.01f, -0.25f, 0.015f, -0.36f, 1.5f);
    /** LANCE-R7: semi-automatic marksman rifle, high damage, long range. */
    public static final WeaponType LANCE = new WeaponType(RIFLE, "LANCE-R7", AMMO_RIFLE, 20, 62f, 0.22f, false, 1, 0.25f, 120f,
            0.05f, 7f, 45, true, ACTION_SLIDE, Sfx.RIFLE, 3.8f, 45f, 0.04f, -0.7f, -0.085f, -0.1f, 0.035f, -0.42f, 4f);

    public static final WeaponType[] ALL = {VX9, RAPTOR, BRUTUS, LANCE};

    public static WeaponType get(int id) { return id >= 0 && id < ALL.length ? ALL[id] : null; }

    public boolean usesMagazine() { return action != ACTION_PUMP; }
}
