package com.joaolimz.operationzero.game.world;

import com.joaolimz.operationzero.game.mesh.Assets;
import com.joaolimz.operationzero.game.weapons.Magazine;
import com.joaolimz.operationzero.game.weapons.Weapon;
import com.joaolimz.operationzero.game.weapons.WeaponType;

import java.util.ArrayList;

/** Creates props/weapons/magazines and recycles pooled ones (dropped magazines, debris). */
public final class PropFactory {
    private final Assets a;
    private final Level level;
    private final ArrayList<Magazine> magPool = new ArrayList<Magazine>();
    private final ArrayList<Prop> debrisPool = new ArrayList<Prop>();

    public PropFactory(Assets a, Level level) {
        this.a = a;
        this.level = level;
    }

    public Prop prop(int kind, int mesh, float hx, float hy, float hz, float mass, int sector, float x, float y, float z) {
        Prop p = new Prop().init(kind, mesh, hx, hy, hz, mass, sector).at(x, y, z);
        add(p);
        return p;
    }

    private void add(Prop p) {
        level.props.add(p);
        level.physics.addBody(p.body);
    }

    public Weapon weapon(WeaponType t, int sector, float x, float y, float z, int magRounds, boolean chambered) {
        Weapon w = new Weapon().setup(t);
        int mesh = t == WeaponType.VX9 ? a.pistol : t == WeaponType.RAPTOR ? a.smg : t == WeaponType.BRUTUS ? a.shotgun : a.rifle;
        float hz = t == WeaponType.VX9 ? 0.1f : t == WeaponType.RAPTOR ? 0.24f : t == WeaponType.BRUTUS ? 0.38f : 0.42f;
        w.init(ItemKind.WEAPON, mesh, 0.035f, 0.06f, hz, t.mass, sector).at(x, y, z);
        if (t.usesMagazine()) {
            if (magRounds >= 0) {
                Magazine m = magazine(t.ammo, magRounds, sector, x, y, z);
                w.insert(m);
                m.body.enabled = false;
            }
        } else {
            w.tubeRounds = Math.max(0, Math.min(t.capacity, magRounds));
        }
        w.chambered = chambered;
        add(w);
        return w;
    }

    public static float magLength(int ammo) {
        return ammo == WeaponType.AMMO_9MM ? 0.1f : ammo == WeaponType.AMMO_SMG ? 0.17f : 0.15f;
    }

    public int magMesh(int ammo) {
        return ammo == WeaponType.AMMO_9MM ? a.magPistol : ammo == WeaponType.AMMO_SMG ? a.magSmg : ammo == WeaponType.AMMO_RIFLE ? a.magRifle : a.shell;
    }

    public static int capacityFor(int ammo) {
        for (WeaponType t : WeaponType.ALL) if (t.ammo == ammo) return t.capacity;
        return 1;
    }

    /** Magazine (or shell for AMMO_SHELL). Reuses pooled inactive magazines. */
    public Magazine magazine(int ammo, int rounds, int sector, float x, float y, float z) {
        boolean shell = ammo == WeaponType.AMMO_SHELL;
        Magazine m = null;
        for (int i = 0; i < magPool.size(); i++) {
            Magazine c = magPool.get(i);
            if (!c.active && c.ammo == ammo) { m = c; break; }
        }
        float len = shell ? 0.03f : magLength(ammo) * 0.5f;
        if (m == null) {
            m = new Magazine();
            m.init(shell ? ItemKind.SHELL : ItemKind.MAGAZINE, magMesh(ammo), shell ? 0.03f : 0.015f, shell ? 0.015f : len, shell ? 0.015f : 0.03f,
                    shell ? 0.05f : 0.25f, sector);
            magPool.add(m);
            add(m);
        }
        m.setup(ammo, shell ? 1 : capacityFor(ammo), Math.min(rounds, shell ? 1 : capacityFor(ammo)));
        m.active = true;
        m.body.enabled = true;
        m.body.kinematic = false;
        m.body.sector = sector;
        m.heldBy = null; m.slot = -1; m.attachedTo = null;
        m.despawnTimer = -1f;
        m.at(x, y, z);
        m.body.rot.identity();
        return m;
    }

    public Prop debris(int sector, float x, float y, float z, boolean glass) {
        Prop p = null;
        int mesh = glass ? a.shard : a.debris;
        for (int i = 0; i < debrisPool.size(); i++) {
            Prop c = debrisPool.get(i);
            if (!c.active && c.mesh == mesh) { p = c; break; }
        }
        if (p == null) {
            p = new Prop().init(ItemKind.DEBRIS, mesh, glass ? 0.02f : 0.06f, glass ? 0.01f : 0.02f, glass ? 0.03f : 0.09f, 0.2f, sector);
            debrisPool.add(p);
            add(p);
        }
        p.active = true;
        p.body.enabled = true;
        p.body.sector = sector;
        p.at(x, y, z);
        p.body.sleeping = false;
        p.despawnTimer = 6f;
        return p;
    }

    public void release(Prop p) {
        p.active = false;
        p.body.enabled = false;
        p.heldBy = null;
        p.slot = -1;
    }
}
