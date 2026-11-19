package com.joaolimz.operationzero.game.world;

import com.joaolimz.operationzero.game.ai.CoverPoint;
import com.joaolimz.operationzero.game.ai.SpawnPoint;
import com.joaolimz.operationzero.game.audio.Sfx;
import com.joaolimz.operationzero.game.math.MathUtil;
import com.joaolimz.operationzero.game.math.Vec3;
import com.joaolimz.operationzero.game.mesh.Assets;
import com.joaolimz.operationzero.game.mesh.LightBaker;
import com.joaolimz.operationzero.game.mesh.MeshBuilder;
import com.joaolimz.operationzero.game.mesh.MeshLibrary;
import com.joaolimz.operationzero.game.physics.StaticCollider;
import com.joaolimz.operationzero.game.weapons.WeaponType;

import java.util.ArrayList;

/**
 * Builds "Complexo Vantor-7", an abandoned industrial facility split into 7 streaming sectors:
 * 0 Exterior (estacionamento, entrada, area de treinamento), 1 Administracao (recepcao, escritorio,
 * sala de seguranca), 2 Nucleo (corredor, deposito), 3 Laboratorio, 4 Subsolo (manutencao,
 * laboratorio secreto, tunel), 5 Garagem, 6 Patio de saida.
 */
public final class LevelBuilder {
    public static final int S_EXT = 0, S_ADMIN = 1, S_CORE = 2, S_LAB = 3, S_UNDER = 4, S_GARAGE = 5, S_YARD = 6;
    public static final int SECTOR_COUNT = 7;

    private static final float PI = MathUtil.PI;
    private static final float WT = 0.15f; // half wall thickness
    private static final float CELL = 2.0f;

    private final Level L;
    private final MeshLibrary lib;
    private final Assets A;
    private final PropFactory factory;
    private final MeshBuilder[] mb = new MeshBuilder[SECTOR_COUNT];
    private final LightBaker[] bk = new LightBaker[SECTOR_COUNT];
    private final ArrayList<float[]> fixtures = new ArrayList<float[]>();

    // palette
    private static final int C_ASPHALT = 0x3E4144, C_DIRT = 0x5D5A45, C_CONCRETE = 0x6B6E70, C_WALL = 0x8A8F94, C_OFFICE = 0xB8B2A6;
    private static final int C_LABW = 0xD5DADF, C_LABF = 0x9EA6AD, C_UNDER = 0x5E6460, C_WARE = 0x5C5F5A, C_STRIPE = 0xD8B32A;
    private static final int C_CEIL = 0x55585C, C_FENCE = 0x6F757A, C_WOOD = 0x8A6A45, C_METAL = 0x5A626B, C_DARK = 0x2C3036;

    /** Sector mesh ids from a previous build to reuse (level reload), or null. */
    private final int[] reuseIds;

    public LevelBuilder(Level level, MeshLibrary lib, Assets assets, int[] reuseSectorMeshIds) {
        this.reuseIds = reuseSectorMeshIds;
        this.L = level;
        this.lib = lib;
        this.A = assets;
        this.factory = new PropFactory(assets, level);
        for (int i = 0; i < SECTOR_COUNT; i++) { mb[i] = new MeshBuilder(); bk[i] = new LightBaker(); }
    }

    public PropFactory factory() { return factory; }

    /** Static mesh id per sector (for reuse on the next reload). */
    public int[] sectorMeshIds() {
        int[] ids = new int[SECTOR_COUNT];
        for (int i = 0; i < SECTOR_COUNT; i++) ids[i] = L.sectors.get(i).staticMeshes.get(0);
        return ids;
    }

    public Level build() {
        sectors();
        exterior();
        admin();
        core();
        lab();
        underground();
        garage();
        yard();
        L.physics.buildGrid();
        navigation();
        finishMeshes();
        checkpoints();
        return L;
    }

    // ===================================================================================== sectors
    private void sectors() {
        Sector s0 = new Sector(S_EXT, "Exterior").volume(-34, -2, 0, 36, 14, 42);
        s0.outdoor = true; s0.ambientSound = Sfx.AMB_WIND; s0.neighbors = new int[]{S_ADMIN};
        s0.ambR = 0.55f; s0.ambG = 0.55f; s0.ambB = 0.6f; s0.fogR = 0.58f; s0.fogG = 0.58f; s0.fogB = 0.57f;
        Sector s1 = new Sector(S_ADMIN, "Administracao").volume(-6, -1, -12, 6, 5, 0).volume(-24, -1, -24, -6, 5, 0).volume(6, -1, -12, 16, 5, 0);
        s1.ambientSound = Sfx.AMB_HUM; s1.neighbors = new int[]{S_EXT, S_CORE};
        Sector s2 = new Sector(S_CORE, "Deposito").volume(-2, -1, -40, 2, 5, -12).volume(2, -1, -40, 24, 8, -12);
        s2.ambientSound = Sfx.AMB_HUM; s2.neighbors = new int[]{S_ADMIN, S_LAB, S_GARAGE};
        Sector s3 = new Sector(S_LAB, "Laboratorio").volume(-24, -1, -40, -2, 5, -24);
        s3.ambientSound = Sfx.AMB_HUM; s3.neighbors = new int[]{S_CORE, S_UNDER};
        s3.ambR = 0.45f; s3.ambG = 0.5f; s3.ambB = 0.56f;
        Sector s4 = new Sector(S_UNDER, "Subsolo").volume(-24, -7, -56, -2, -1.5f, -28).volume(-24, -7, -76, -2, -0.5f, -56)
                .volume(-2, -7, -50, 18, -2.5f, -46);
        s4.ambientSound = Sfx.AMB_HUM; s4.neighbors = new int[]{S_LAB, S_GARAGE};
        s4.ambR = 0.3f; s4.ambG = 0.36f; s4.ambB = 0.32f; s4.fogR = 0.04f; s4.fogG = 0.05f; s4.fogB = 0.045f;
        Sector s5 = new Sector(S_GARAGE, "Garagem").volume(-2, -2.5f, -60, 24, 8, -40);
        s5.ambientSound = Sfx.AMB_HUM; s5.neighbors = new int[]{S_CORE, S_UNDER, S_YARD};
        Sector s6 = new Sector(S_YARD, "Patio de saida").volume(-6, -2, -86, 30, 14, -60);
        s6.outdoor = true; s6.ambientSound = Sfx.AMB_WIND; s6.neighbors = new int[]{S_GARAGE};
        s6.ambR = 0.55f; s6.ambG = 0.55f; s6.ambB = 0.6f; s6.fogR = 0.58f; s6.fogG = 0.58f; s6.fogB = 0.57f;
        L.sectors.add(s0); L.sectors.add(s1); L.sectors.add(s2); L.sectors.add(s3); L.sectors.add(s4); L.sectors.add(s5); L.sectors.add(s6);
        for (int i = 0; i < SECTOR_COUNT; i++) {
            Sector s = L.sectors.get(i);
            bk[i].ambient(s.outdoor ? 0.5f : 0.2f, s.outdoor ? 0.5f : 0.21f, s.outdoor ? 0.55f : 0.24f);
            bk[i].sun = s.outdoor;
        }
        bk[S_UNDER].floorY = -6f;
    }

    // ===================================================================================== helpers
    private StaticCollider solid(int s, float x0, float y0, float z0, float x1, float y1, float z1, int mat) {
        StaticCollider c = L.physics.addStatic(Math.min(x0, x1), Math.min(y0, y1), Math.min(z0, z1), Math.max(x0, x1), Math.max(y0, y1), Math.max(z0, z1), s);
        c.material = mat;
        return c;
    }

    /** Visual + collision box. */
    private void block(int s, float x0, float y0, float z0, float x1, float y1, float z1, int color, int mat) {
        solid(s, x0, y0, z0, x1, y1, z1, mat);
        mb[s].color(color).boxMinMax(Math.min(x0, x1), Math.min(y0, y1), Math.min(z0, z1), Math.max(x0, x1), Math.max(y0, y1), Math.max(z0, z1),
                MeshBuilder.FACE_ALL, CELL);
    }

    /** Visual only box (decoration, no collision). */
    private void deco(int s, float x0, float y0, float z0, float x1, float y1, float z1, int color) {
        mb[s].color(color).boxMinMax(Math.min(x0, x1), Math.min(y0, y1), Math.min(z0, z1), Math.max(x0, x1), Math.max(y0, y1), Math.max(z0, z1),
                MeshBuilder.FACE_ALL, 0f);
    }

    private void floor(int s, float x0, float z0, float x1, float z1, float y, int color) {
        solid(s, x0, y - 0.4f, z0, x1, y, z1, StaticCollider.MAT_CONCRETE);
        mb[s].color(color).gridQuad(x0, y, z1, x1, y, z1, x0, y, z0, CELL);
    }

    private void floorWithHole(int s, float x0, float z0, float x1, float z1, float y, float hx0, float hz0, float hx1, float hz1, int color) {
        if (hz0 > z0) floor(s, x0, z0, x1, hz0, y, color);
        if (hz1 < z1) floor(s, x0, hz1, x1, z1, y, color);
        if (hx0 > x0) floor(s, x0, hz0, hx0, hz1, y, color);
        if (hx1 < x1) floor(s, hx1, hz0, x1, hz1, y, color);
    }

    private void ceiling(int s, float x0, float z0, float x1, float z1, float y, int color) {
        solid(s, x0, y, z0, x1, y + 0.3f, z1, StaticCollider.MAT_CONCRETE);
        mb[s].color(color).gridQuad(x0, y, z0, x1, y, z0, x0, y, z1, CELL * 1.5f);
    }

    private void ceilingWithHole(int s, float x0, float z0, float x1, float z1, float y, float hx0, float hz0, float hx1, float hz1, int color) {
        if (hz0 > z0) ceiling(s, x0, z0, x1, hz0, y, color);
        if (hz1 < z1) ceiling(s, x0, hz1, x1, z1, y, color);
        if (hx0 > x0) ceiling(s, x0, hz0, hx0, hz1, y, color);
        if (hx1 < x1) ceiling(s, hx1, hz0, x1, hz1, y, color);
    }

    /**
     * Wall along X at depth z from x0..x1. openings = {from, to, height, from, to, height...}.
     */
    private void wallX(int s, float z, float x0, float x1, float y, float h, int color, float... openings) {
        float cur = x0;
        for (int i = 0; i < openings.length; i += 3) {
            float a = openings[i], b = openings[i + 1], oh = openings[i + 2];
            if (a > cur) wallBox(s, cur, y, z - WT, a, y + h, z + WT, color, h);
            if (oh < h) wallBox(s, a, y + oh, z - WT, b, y + h, z + WT, color, h);
            cur = b;
        }
        if (cur < x1) wallBox(s, cur, y, z - WT, x1, y + h, z + WT, color, h);
    }

    private void wallZ(int s, float x, float z0, float z1, float y, float h, int color, float... openings) {
        float cur = z0;
        for (int i = 0; i < openings.length; i += 3) {
            float a = openings[i], b = openings[i + 1], oh = openings[i + 2];
            if (a > cur) wallBox(s, x - WT, y, cur, x + WT, y + h, a, color, h);
            if (oh < h) wallBox(s, x - WT, y + oh, a, x + WT, y + h, b, color, h);
            cur = b;
        }
        if (cur < z1) wallBox(s, x - WT, y, cur, x + WT, y + h, z1, color, h);
    }

    private void wallBox(int s, float x0, float y0, float z0, float x1, float y1, float z1, int color, float fullH) {
        solid(s, x0, y0, z0, x1, y1, z1, StaticCollider.MAT_CONCRETE);
        int faces = MeshBuilder.FACE_PX | MeshBuilder.FACE_NX | MeshBuilder.FACE_PZ | MeshBuilder.FACE_NZ;
        if (fullH < 3.2f) faces |= MeshBuilder.FACE_PY;
        mb[s].color(color).boxMinMax(x0, y0, z0, x1, y1, z1, faces | MeshBuilder.FACE_NY, CELL);
        // dark baseboard stripe
        mb[s].color(Models_darken(color)).boxMinMax(x0 - 0.01f, y0, z0 - 0.01f, x1 + 0.01f, Math.min(y1, y0 + 0.12f), z1 + 0.01f,
                MeshBuilder.FACE_PX | MeshBuilder.FACE_NX | MeshBuilder.FACE_PZ | MeshBuilder.FACE_NZ, 0f);
    }

    private static int Models_darken(int c) { return com.joaolimz.operationzero.game.mesh.Models.darken(c, 0.6f); }

    /** Room shell: floor + optional ceiling (walls built separately so shared walls are single). */
    private void slab(int s, float x0, float z0, float x1, float z1, float y, float h, int floorColor, boolean ceil) {
        floor(s, x0, z0, x1, z1, y, floorColor);
        if (ceil) ceiling(s, x0, z0, x1, z1, y + h, C_CEIL);
    }

    private void light(int s, float x, float y, float z, float radius, float r, float g, float b) {
        bk[s].addLight(x, y - 0.3f, z, radius, r, g, b);
        fixtures.add(new float[]{s, x, y, z, r, g, b});
    }

    /** Stairs descending along Z from (zTop at yTop) to (zBottom at yBottom). */
    private void stairsZ(int s, float x0, float x1, float zTop, float zBottom, float yTop, float yBottom, int steps, int color) {
        float dz = (zBottom - zTop) / steps, rise = (yTop - yBottom) / steps;
        for (int i = 0; i < steps; i++) {
            float za = zTop + dz * i, zb = zTop + dz * (i + 1);
            float top = yTop - rise * (i + 1);
            if (i == steps - 1) top = yBottom + 0.01f;
            block(s, x0, yBottom - 0.2f, Math.min(za, zb), x1, top, Math.max(za, zb), (i % 2 == 0) ? color : Models_darken(color) | 0x202020, StaticCollider.MAT_CONCRETE);
        }
    }

    /** Stairs ascending along +X from (xBottom at yBottom) to (xTop at yTop). */
    private void stairsX(int s, float z0, float z1, float xBottom, float xTop, float yBottom, float yTop, int steps, int color) {
        float dx = (xTop - xBottom) / steps, rise = (yTop - yBottom) / steps;
        for (int i = 0; i < steps; i++) {
            float xa = xBottom + dx * i, xb = xBottom + dx * (i + 1);
            float top = yBottom + rise * (i + 1);
            block(s, Math.min(xa, xb), yBottom - 0.2f, z0, Math.max(xa, xb), top, z1, (i % 2 == 0) ? color : Models_darken(color) | 0x202020, StaticCollider.MAT_CONCRETE);
        }
    }

    private void cover(int s, float x, float y, float z, float nx, float nz, boolean low) {
        L.covers.add(new CoverPoint(x, y, z, nx, nz, low, s));
    }

    /** Adds 4 cover points around an obstacle box (center cx,cz, half extents hx,hz). */
    private void coverAround(int s, float cx, float y, float cz, float hx, float hz, boolean low) {
        float d = 0.55f;
        cover(s, cx + hx + d, y, cz, -1, 0, low);
        cover(s, cx - hx - d, y, cz, 1, 0, low);
        cover(s, cx, y, cz + hz + d, 0, -1, low);
        cover(s, cx, y, cz - hz - d, 0, 1, low);
    }

    private void crateStack(int s, float x, float y, float z, float hx, float h, float hz, int color) {
        block(s, x - hx, y, z - hz, x + hx, y + h, z + hz, color, StaticCollider.MAT_WOOD);
        deco(s, x - hx - 0.01f, y + h * 0.45f, z - hz - 0.01f, x + hx + 0.01f, y + h * 0.55f, z + hz + 0.01f, Models_darken(color));
        coverAround(s, x, y, z, hx, hz, h < 1.4f);
    }

    private void car(int s, float cx, float cz, boolean alongX, int color) {
        float hx = alongX ? 2.1f : 0.9f, hz = alongX ? 0.9f : 2.1f;
        block(s, cx - hx, 0, cz - hz, cx + hx, 1.0f, cz + hz, color, StaticCollider.MAT_METAL);
        float cx2 = alongX ? cx + 0.2f : cx, cz2 = alongX ? cz : cz + 0.2f;
        float chx = alongX ? 1.1f : 0.8f, chz = alongX ? 0.8f : 1.1f;
        block(s, cx2 - chx, 1.0f, cz2 - chz, cx2 + chx, 1.55f, cz2 + chz, Models_darken(color), StaticCollider.MAT_METAL);
        // windows + wheels (decor)
        deco(s, cx2 - chx - 0.01f, 1.12f, cz2 - chz - 0.01f, cx2 + chx + 0.01f, 1.45f, cz2 + chz + 0.01f, 0x23313D);
        float wx = alongX ? 1.3f : 0.95f, wz = alongX ? 0.95f : 1.3f;
        for (int i = -1; i <= 1; i += 2) for (int j = -1; j <= 1; j += 2) {
            deco(s, cx + i * wx - 0.3f, 0.0f, cz + j * wz - 0.3f, cx + i * wx + 0.3f, 0.6f, cz + j * wz + 0.3f, 0x151618);
        }
        coverAround(s, cx, 0, cz, hx, hz, true);
    }

    private void table(int s, float x, float z, float hx, float hz, float h, int color) {
        block(s, x - hx, h - 0.06f, z - hz, x + hx, h, z + hz, color, StaticCollider.MAT_WOOD);
        // legs (visual only so props can't get stuck)
        deco(s, x - hx + 0.03f, 0, z - hz + 0.03f, x - hx + 0.09f, h - 0.06f, z - hz + 0.09f, Models_darken(color));
        deco(s, x + hx - 0.09f, 0, z - hz + 0.03f, x + hx - 0.03f, h - 0.06f, z - hz + 0.09f, Models_darken(color));
        deco(s, x - hx + 0.03f, 0, z + hz - 0.09f, x - hx + 0.09f, h - 0.06f, z + hz - 0.03f, Models_darken(color));
        deco(s, x + hx - 0.09f, 0, z + hz - 0.09f, x + hx - 0.03f, h - 0.06f, z + hz - 0.03f, Models_darken(color));
    }

    /** Desk at floor level y (generic). */
    private void desk(int s, float x, float y, float z, float hx, float hz) {
        block(s, x - hx, y, z - hz, x + hx, y + 0.78f, z + hz, 0x6E5A44, StaticCollider.MAT_WOOD);
        coverAround(s, x, y, z, hx, hz, true);
    }

    private void shelf(int s, float x, float y, float z, float hx, float hz, float h) {
        block(s, x - hx, y, z - hz, x + hx, y + h, z + hz, 0x4C5560, StaticCollider.MAT_METAL);
        for (float sy = 0.5f; sy < h; sy += 0.9f) deco(s, x - hx - 0.02f, y + sy, z - hz - 0.02f, x + hx + 0.02f, y + sy + 0.05f, z + hz + 0.02f, C_STRIPE);
        coverAround(s, x, y, z, hx, hz, false);
    }

    private void pillar(int s, float x, float y, float z, float half, float h, int color) {
        block(s, x - half, y, z - half, x + half, y + h, z + half, color, StaticCollider.MAT_CONCRETE);
        coverAround(s, x, y, z, half, half, false);
    }

    private void sign(int s, String text, float x, float y, float z, float yaw, float size, int color) {
        L.signs.add(new Level.Sign(text, x, y, z, yaw, size, color, s));
    }

    private Door hingedDoor(String id, int s, float hingeX, float hingeZ, float y, float baseYaw, float width, boolean metal) {
        Door d = Door.hinged(id, L.physics, hingeX, hingeZ, y, baseYaw, width, 2.3f, s);
        d.mesh = metal ? A.doorMetal : A.doorWood;
        L.doors.add(d);
        return d;
    }

    private Door slidingDoor(String id, int s, float x0, float y0, float z0, float x1, float y1, float z1, float tx, float ty, float tz, int mesh) {
        Door d = Door.sliding(id, L.physics, Math.min(x0, x1), Math.min(y0, y1), Math.min(z0, z1), Math.max(x0, x1), Math.max(y0, y1), Math.max(z0, z1), tx, ty, tz, s);
        d.mesh = mesh;
        L.doors.add(d);
        return d;
    }

    private Interactive interactive(String id, int type, int s, float x, float y, float z, float yaw, String event) {
        Interactive it = new Interactive(id, type, x, y, z, yaw, s);
        it.event = event;
        switch (type) {
            case Interactive.BUTTON: it.mesh = A.buttonGreen; break;
            case Interactive.LEVER: it.mesh = A.leverBase; it.mesh2 = A.leverArm; break;
            case Interactive.TERMINAL: it.mesh = A.terminal; break;
            default: it.mesh = A.cardReader; break;
        }
        L.interactives.add(it);
        return it;
    }

    private void prop(int kind, int mesh, float hx, float hy, float hz, float mass, int s, float x, float y, float z) {
        factory.prop(kind, mesh, hx, hy, hz, mass, s, x, y + hy, z);
    }

    private void crateProp(int s, float x, float y, float z, boolean breakable) {
        if (breakable) {
            Prop p = factory.prop(ItemKind.CRATE, A.crateBreakable, 0.25f, 0.25f, 0.25f, 6f, s, x, y + 0.25f, z);
            p.health = 40f;
            p.name = "Caixa fragil";
        } else {
            factory.prop(ItemKind.CRATE, A.crateSmall, 0.2f, 0.2f, 0.2f, 4f, s, x, y + 0.2f, z);
        }
    }

    private void bottle(int s, float x, float y, float z) {
        Prop p = factory.prop(ItemKind.BOTTLE, A.bottle, 0.035f, 0.11f, 0.035f, 0.4f, s, x, y + 0.08f, z);
        p.health = 5f;
    }

    private void ammo(int s, int ammoClass, int amount, float x, float y, float z) {
        int mesh = ammoClass == WeaponType.AMMO_9MM ? A.ammoPistol : ammoClass == WeaponType.AMMO_SMG ? A.ammoSmg
                : ammoClass == WeaponType.AMMO_SHELL ? A.ammoShells : A.ammoRifle;
        Prop p = factory.prop(ItemKind.AMMO_BOX, mesh, 0.09f, 0.055f, 0.05f, 1f, s, x, y + 0.055f, z);
        p.value = ammoClass;
        p.value2 = amount;
        p.name = "Municao " + WeaponType.AMMO_NAMES[ammoClass];
    }

    private void medkit(int s, float x, float y, float z) {
        Prop p = factory.prop(ItemKind.MEDKIT, A.medkit, 0.09f, 0.05f, 0.06f, 0.8f, s, x, y + 0.05f, z);
        p.value = 50;
    }

    private void spawn(String group, int s, float x, float y, float z, float yaw) {
        L.spawns.add(new SpawnPoint(group, x, y, z, yaw, s));
    }

    // ===================================================================================== EXTERIOR
    private void exterior() {
        int s = S_EXT;
        floor(s, -34, 0, 36, 42, 0, C_ASPHALT);
        // parking stripes
        for (int i = 0; i < 6; i++) deco(s, -20 + i * 3.2f, 0.0f, 12, -19.9f + i * 3.2f, 0.01f, 17, 0xC8C8C0);
        for (int i = 0; i < 6; i++) deco(s, -20 + i * 3.2f, 0.0f, 22, -19.9f + i * 3.2f, 0.01f, 27, 0xC8C8C0);
        deco(s, -2.5f, 0.0f, 0.5f, 2.5f, 0.012f, 10, 0x4A4D50);
        // perimeter fence
        wallZ(s, -34, 0, 42, 0, 3f, C_FENCE);
        wallZ(s, 36, 0, 42, 0, 3f, C_FENCE);
        wallX(s, 42, -34, 36, 0, 3f, C_FENCE);
        wallX(s, 0, -34, -24, 0, 3f, C_FENCE);
        wallX(s, 0, 24, 36, 0, 3f, C_FENCE);
        // building facade (shared with lobby/office) - shutter opening x[-2,2]
        wallX(s, 0, -24, 24, 0, 6f, 0x7C8086, -2f, 2f, 3.2f);
        deco(s, -24, 6, -0.4f, 24, 6.4f, 0.4f, 0x4C5157); // roof edge
        deco(s, -6, 3.6f, 0.15f, 6, 4.4f, 0.3f, 0x2D3A4A); // facade banner
        sign(s, "COMPLEXO VANTOR-7", 0, 4.0f, 0.35f, PI, 0.55f, 0xFFB050);
        sign(s, "ENTRADA", 0, 3.5f, 0.35f, PI, 0.25f, 0xFFFFFF);
        // shutter (opened by power lever)
        Door shutter = slidingDoor("door_shutter", S_ADMIN, -2f, 0f, -0.1f, 2f, 3.2f, 0.1f, 0, 3.0f, 0, A.slabShutter);
        shutter.locked = true;
        shutter.lockedHint = "Sem energia: puxe a alavanca";
        shutter.speed = 0.5f;
        Interactive lever = interactive("lever_power", Interactive.LEVER, s, 3.6f, 1.2f, 0.32f, PI, "power_on");
        lever.label = "ENERGIA";
        lever.oneShot = true;
        deco(s, 3.2f, 0.6f, 0.15f, 4.0f, 1.8f, 0.3f, 0x3A3F46);
        // cars and barriers (cover)
        car(s, -14, 14.5f, false, 0x6A2E2E);
        car(s, -7.6f, 24.5f, false, 0x2E4A6A);
        car(s, 9, 20, true, 0x5B6B3A);
        car(s, 12, 9, true, 0x8C8C86);
        block(s, -5, 0, 5.5f, -2.5f, 0.9f, 6.1f, 0x9A9A92, StaticCollider.MAT_CONCRETE);
        block(s, 2.5f, 0, 5.5f, 5, 0.9f, 6.1f, 0x9A9A92, StaticCollider.MAT_CONCRETE);
        coverAround(s, -3.75f, 0, 5.8f, 1.25f, 0.3f, true);
        coverAround(s, 3.75f, 0, 5.8f, 1.25f, 0.3f, true);
        // guard booth
        block(s, -12, 0, 2, -9, 2.6f, 4.5f, 0x7A7F70, StaticCollider.MAT_CONCRETE);
        deco(s, -12.05f, 1.2f, 2.5f, -8.95f, 2.0f, 4.0f, 0x23313D);
        coverAround(s, -10.5f, 0, 3.25f, 1.5f, 1.25f, false);
        // start area: table with the VX-9 pistol
        table(s, 0, 27, 0.9f, 0.45f, 0.9f, C_WOOD);
        factory.weapon(WeaponType.VX9, s, -0.3f, 0.9f + 0.06f, 27.0f, 12, true);
        Prop m1 = factory.magazine(WeaponType.AMMO_9MM, 12, s, 0.25f, 0.9f + 0.06f, 26.9f);
        m1.body.sleeping = true;
        Prop m2 = factory.magazine(WeaponType.AMMO_9MM, 12, s, 0.45f, 0.9f + 0.06f, 26.9f);
        m2.body.sleeping = true;
        ammo(s, WeaponType.AMMO_9MM, 24, 0.7f, 0.9f, 27.15f);
        sign(s, "PEGUE A PISTOLA (GATILHO)", 0, 1.6f, 26.3f, PI, 0.12f, 0xFFFFFF);
        // physics playground: crates, barrels, bottles
        crateProp(s, 4, 0, 27, false);
        crateProp(s, 4.5f, 0, 27.6f, true);
        crateProp(s, 4.2f, 0.4f, 27.2f, false);
        prop(ItemKind.BARREL, A.barrel, 0.28f, 0.42f, 0.28f, 18f, s, -4, 0, 28);
        prop(ItemKind.BARREL, A.barrel, 0.28f, 0.42f, 0.28f, 18f, s, -4.7f, 0, 27.3f);
        table(s, -3, 23, 0.8f, 0.4f, 0.9f, C_WOOD);
        bottle(s, -3.4f, 0.9f, 23f);
        bottle(s, -3.0f, 0.9f, 23.1f);
        bottle(s, -2.6f, 0.9f, 22.9f);
        prop(ItemKind.CAN, A.can, 0.033f, 0.06f, 0.033f, 0.3f, s, -2.3f, 0.9f, 23.1f);
        prop(ItemKind.TOOL, A.wrench, 0.03f, 0.012f, 0.16f, 0.9f, s, 0.6f, 0.9f, 27.35f);
        // training area (fenced range)
        wallX(s, 34f, 18, 34, 0, 1.2f, C_FENCE, 18.0f, 20.5f, 1.2f);
        wallZ(s, 18, 4, 34, 0, 1.2f, C_FENCE, 24f, 27f, 1.2f);
        block(s, 19, 0, 23.8f, 33, 1.0f, 24.2f, 0x6B5D48, StaticCollider.MAT_WOOD); // firing line bench
        sign(s, "AREA DE TREINAMENTO", 26, 3.2f, 6.0f, PI, 0.3f, 0xFFD27A);
        for (int i = 0; i < 4; i++) L.targets.add(new Level.Target(21 + i * 3.5f, 1.2f, 9 + (i % 2) * 3f, PI, s));
        ammo(s, WeaponType.AMMO_9MM, 36, 20f, 1.0f, 24f);
        factory.magazine(WeaponType.AMMO_9MM, 12, s, 21f, 1.06f, 24f);
        // lighting
        light(s, -8, 5.5f, 1.2f, 10, 0.6f, 0.55f, 0.45f);
        light(s, 8, 5.5f, 1.2f, 10, 0.6f, 0.55f, 0.45f);
        // enemies outside
        spawn("ext", s, -18, 0, 8, 0);
        spawn("ext", s, 16, 0, 4, PI * 0.5f);
        spawn("ext", s, -24, 0, 30, 0);
        L.extraction.set(12, 0, -78);
        // trigger: entering the lobby completes mission 1
        L.triggers.add(new TriggerZone("enter_lobby", -6, -1, -10, 6, 4, -1.5f));
    }

    // ===================================================================================== ADMIN
    private void admin() {
        int s = S_ADMIN;
        // lobby x[-6,6] z[-12,0]
        slab(s, -6, -12, 6, 0, 0, 4, 0x7A776F, true);
        wallZ(s, -6, -12, 0, 0, 4, C_OFFICE, -5f, -3.8f, 2.3f);
        wallZ(s, 6, -12, 0, 0, 4, C_OFFICE, -5f, -3.8f, 2.3f);
        wallX(s, -12, -6, 6, 0, 4, C_OFFICE, -0.6f, 0.6f, 2.3f);
        hingedDoor("door_office", s, -6, -3.8f, 0, PI * 0.5f, 1.2f, false);
        hingedDoor("door_security", s, 6, -3.8f, 0, PI * 0.5f, 1.2f, true);
        Door corr = hingedDoor("door_corridor", s, -0.6f, -12, 0, 0, 1.2f, true);
        corr.locked = true;
        corr.lockedHint = "Trancada: libere no terminal da seguranca";
        // reception desk
        block(s, -2.5f, 0, -8.5f, 2.5f, 1.1f, -7.6f, 0x5E4B3A, StaticCollider.MAT_WOOD);
        coverAround(s, 0, 0, -8.05f, 2.5f, 0.45f, true);
        sign(s, "RECEPCAO", 0, 3.2f, -11.8f, PI, 0.3f, 0xFFFFFF);
        sign(s, "ESCRITORIO", -5.8f, 2.6f, -4.4f, -PI * 0.5f, 0.15f, 0xFFFFFF);
        sign(s, "SEGURANCA", 5.8f, 2.6f, -4.4f, PI * 0.5f, 0.15f, 0xFFFFFF);
        pillar(s, -4, 0, -2, 0.3f, 4, 0x9C9890);
        pillar(s, 4, 0, -2, 0.3f, 4, 0x9C9890);
        light(s, 0, 4, -4, 9, 0.9f, 0.85f, 0.75f);
        light(s, 0, 4, -10, 7, 0.8f, 0.75f, 0.65f);
        bottle(s, 1.6f, 1.1f, -8.05f);
        prop(ItemKind.CAN, A.can, 0.033f, 0.06f, 0.033f, 0.3f, s, -1.5f, 1.1f, -8.0f);

        // office x[-24,-6] z[-24,0]
        slab(s, -24, -24, -6, 0, 0, 3.6f, 0x8C8577, true);
        wallZ(s, -24, -24, 0, 0, 3.6f, C_OFFICE);
        wallX(s, -24, -24, -6, 0, 3.6f, C_OFFICE);
        wallZ(s, -6, -24, -12, 0, 3.6f, C_OFFICE);
        // manager room partition with door
        wallZ(s, -16, -24, -16, 0, 3.6f, C_OFFICE, -21f, -19.8f, 2.3f);
        wallX(s, -16, -24, -16, 0, 3.6f, C_OFFICE);
        hingedDoor("door_manager", s, -16, -19.8f, 0, PI * 0.5f, 1.2f, false);
        sign(s, "GERENCIA", -15.8f, 2.6f, -20.4f, -PI * 0.5f, 0.15f, 0xFFFFFF);
        for (int r = 0; r < 2; r++) for (int c = 0; c < 3; c++) {
            float x = -13.5f + c * 2.8f, z = -4f - r * 5f;
            desk(s, x, 0, z, 0.8f, 0.45f);
            deco(s, x - 0.25f, 0.78f, z - 0.1f, x + 0.25f, 1.12f, z - 0.05f, C_DARK); // monitor
        }
        desk(s, -20, 0, -20, 1.0f, 0.5f);
        Prop card = factory.prop(ItemKind.KEYCARD, A.keycard, 0.03f, 0.006f, 0.045f, 0.05f, s, -20.2f, 0.79f, -20.0f);
        card.pickupEvent = "pickup_keycard";
        card.name = "Cartao de acesso NIVEL 3";
        medkit(s, -19.4f, 0.78f, -19.9f);
        shelf(s, -23.4f, 0, -10, 0.4f, 1.5f, 2.2f);
        crateProp(s, -22.5f, 0, -3, false);
        crateProp(s, -22.5f, 0.4f, -3, false);
        bottle(s, -13.2f, 0.78f, -9.0f);
        light(s, -12, 3.6f, -5, 10, 0.85f, 0.8f, 0.7f);
        light(s, -12, 3.6f, -14, 10, 0.85f, 0.8f, 0.7f);
        light(s, -20, 3.6f, -20, 7, 0.9f, 0.75f, 0.5f);
        spawn("admin", s, -10, 0, -14, PI);
        spawn("admin", s, -20, 0, -6, 0);
        spawn("admin", s, -19, 0, -22, PI * 0.5f);

        // security room x[6,16] z[-12,0]
        slab(s, 6, -12, 16, 0, 0, 3.6f, 0x6F7378, true);
        wallZ(s, 16, -12, 0, 0, 3.6f, C_OFFICE);
        wallX(s, -12, 6, 16, 0, 3.6f, C_OFFICE);
        Interactive term = interactive("terminal_security", Interactive.TERMINAL, s, 11, 0, -10.6f, PI, "unlock_corridor");
        term.label = "TERMINAL DE SEGURANCA";
        term.screen = new String[]{"VANTOR-7 SEGURANCA", "Cameras: OFFLINE", "Porta corredor: TRAVADA", "[GATILHO] Liberar acesso"};
        term.oneShot = true;
        for (int i = 0; i < 3; i++) deco(s, 7.5f + i * 2.4f, 1.4f, -11.8f, 9.3f + i * 2.4f, 2.6f, -11.7f, 0x1A2630);
        desk(s, 13.5f, 0, -3, 1.0f, 0.45f);
        medkit(s, 13.3f, 0.78f, -3.1f);
        ammo(s, WeaponType.AMMO_9MM, 24, 14.0f, 0.78f, -2.9f);
        light(s, 11, 3.6f, -6, 9, 0.7f, 0.85f, 1.0f);
        sign(s, "SALA DE SEGURANCA", 11, 2.9f, -11.8f, PI, 0.2f, 0xFFFFFF);
        spawn("admin", s, 9, 0, -6, PI * 0.5f);
    }

    // ===================================================================================== CORE (corridor + warehouse)
    private void core() {
        int s = S_CORE;
        // corridor x[-2,2] z[-40,-12]
        slab(s, -2, -40, 2, -12, 0, 3.2f, 0x6B6E70, true);
        Door labDoor = slidingDoor("door_lab", s, -2.1f, 0, -33, -1.9f, 2.4f, -31, 0, 0, -2.0f, A.slabLab);
        labDoor.locked = true;
        labDoor.lockedHint = "Acesso restrito: cartao NIVEL 3";
        labDoor.openEvent = "lab_door_opened";
        wallZ(s, -2, -40, -12, 0, 3.2f, C_WALL, -33f, -31f, 2.4f);
        wallZ(s, 2, -40, -12, 0, 3.2f, C_WALL, -27f, -24.6f, 2.6f);
        // north wall (shared corridor/warehouse/garage) z=-40 from x=-2..24
        wallX(s, -40, -2, 24, 0, 7f, C_WALL, -0.6f, 0.6f, 2.3f);
        Door gin = hingedDoor("door_garage_in", s, -0.6f, -40, 0, 0, 1.2f, true);
        gin.locked = true;
        gin.lockedHint = "Trancada: sem energia na garagem";
        Interactive reader = interactive("reader_lab", Interactive.READER, s, -1.78f, 1.3f, -30.3f, -PI * 0.5f, "lab_card");
        reader.requiredItem = ItemKind.KEYCARD;
        reader.label = "LEITOR";
        sign(s, "LABORATORIO", -1.8f, 2.7f, -32f, -PI * 0.5f, 0.2f, 0x7FC8FF);
        sign(s, "DEPOSITO", 1.8f, 2.8f, -25.8f, PI * 0.5f, 0.2f, 0xFFFFFF);
        sign(s, "GARAGEM", 0, 2.7f, -39.8f, PI, 0.18f, 0xFFFFFF);
        light(s, 0, 3.2f, -16, 7, 0.8f, 0.8f, 0.75f);
        light(s, 0, 3.2f, -26, 7, 0.8f, 0.8f, 0.75f);
        light(s, 0, 3.2f, -36, 7, 0.8f, 0.8f, 0.75f);
        crateProp(s, 1.3f, 0, -18, true);
        prop(ItemKind.BARREL, A.barrel, 0.28f, 0.42f, 0.28f, 18f, s, -1.4f, 0, -21);

        // warehouse x[2,24] z[-40,-12] height 7
        slab(s, 2, -40, 24, -12, 0, 7, C_WARE, true);
        wallZ(s, 24, -40, -12, 0, 7, C_WALL);
        wallX(s, -12, 2, 24, 0, 7, C_WALL);
        deco(s, 4, 0.0f, -38, 4.1f, 0.012f, -14, C_STRIPE);
        deco(s, 22, 0.0f, -38, 22.1f, 0.012f, -14, C_STRIPE);
        shelf(s, 8, 0, -16, 3, 0.5f, 3.2f);
        shelf(s, 8, 0, -36, 3, 0.5f, 3.2f);
        shelf(s, 20, 0, -16, 2.5f, 0.5f, 3.2f);
        crateStack(s, 14, 0, -20, 1.0f, 1.2f, 1.0f, 0x6F7A52);
        crateStack(s, 17, 0, -28, 1.2f, 2.4f, 1.0f, 0x7A6A4E);
        crateStack(s, 10, 0, -28, 0.9f, 1.1f, 0.9f, 0x6F7A52);
        crateStack(s, 20, 0, -35, 1.0f, 1.2f, 1.6f, 0x7A6A4E);
        crateStack(s, 6, 0, -24, 0.7f, 1.0f, 0.7f, 0x6F7A52);
        // forklift
        block(s, 12.5f, 0, -33.5f, 14.5f, 1.6f, -32, 0xD8A02A, StaticCollider.MAT_METAL);
        deco(s, 12.6f, 1.6f, -33.4f, 14.4f, 2.4f, -32.8f, C_DARK);
        coverAround(s, 13.5f, 0, -32.75f, 1, 0.75f, false);
        // SMG on crate top
        factory.weapon(WeaponType.RAPTOR, s, 14, 1.2f + 0.06f, -20, 30, true);
        factory.magazine(WeaponType.AMMO_SMG, 30, s, 14.4f, 1.26f, -19.6f);
        ammo(s, WeaponType.AMMO_SMG, 60, 13.6f, 1.2f, -19.5f);
        medkit(s, 17.2f, 2.4f, -28f);
        crateProp(s, 11, 0, -24, true);
        crateProp(s, 11.5f, 0, -23.4f, false);
        crateProp(s, 19, 0, -22, true);
        prop(ItemKind.BARREL, A.barrel, 0.28f, 0.42f, 0.28f, 18f, s, 22.5f, 0, -24);
        prop(ItemKind.BARREL, A.barrel, 0.28f, 0.42f, 0.28f, 18f, s, 22.5f, 0, -24.7f);
        light(s, 8, 7, -20, 12, 0.85f, 0.8f, 0.65f);
        light(s, 18, 7, -20, 12, 0.85f, 0.8f, 0.65f);
        light(s, 8, 7, -32, 12, 0.85f, 0.8f, 0.65f);
        light(s, 18, 7, -32, 12, 0.85f, 0.8f, 0.65f);
        spawn("core", s, 20, 0, -30, PI * 0.5f);
        spawn("core", s, 8, 0, -30, 0);
        spawn("core", s, 21, 0, -19, PI);
        spawn("core", s, 0, 0, -36, 0);
    }

    // ===================================================================================== LAB
    private void lab() {
        int s = S_LAB;
        // floor with stairwell hole x[-23,-19] z[-38,-30]
        floorWithHole(s, -24, -40, -2, -24, 0, -23, -38, -19, -30, C_LABF);
        ceiling(s, -24, -40, -2, -24, 3.6f, 0xC9CED3);
        wallZ(s, -24, -40, -24, 0, 3.6f, C_LABW);
        wallX(s, -40, -24, -2, 0, 3.6f, C_LABW);
        wallX(s, -24, -24, -2, 0, 3.6f, C_LABW);
        // stairwell rails
        block(s, -23.1f, 0, -38.1f, -22.9f, 1.0f, -30, C_STRIPE, StaticCollider.MAT_METAL);
        block(s, -19.1f, 0, -38.1f, -18.9f, 1.0f, -30, C_STRIPE, StaticCollider.MAT_METAL);
        block(s, -23, 0, -38.1f, -19, 1.0f, -37.9f, C_STRIPE, StaticCollider.MAT_METAL);
        sign(s, "SUBSOLO", -21, 2.4f, -29.9f, PI, 0.2f, 0xFFD27A);
        stairsZ(s, -23, -19, -30, -38, 0, -6, 16, 0x7E868C);
        // benches
        for (int i = 0; i < 3; i++) {
            float z = -27.5f - i * 4.0f;
            block(s, -15, 0, z - 0.5f, -7, 0.92f, z + 0.5f, 0xE3E6E8, StaticCollider.MAT_METAL);
            coverAround(s, -11, 0, z, 4, 0.5f, true);
            deco(s, -14, 0.92f, z - 0.2f, -13.4f, 1.3f, z + 0.2f, 0x4A90C2);
        }
        Interactive t = interactive("terminal_lab", Interactive.TERMINAL, s, -4.5f, 0, -38.8f, 0, "lab_terminal");
        t.label = "REGISTRO";
        t.screen = new String[]{"PROJETO ZERO", "Nucleo de dados movido", "para o LAB SECRETO (subsolo)", "Energia: alavanca norte"};
        bottle(s, -12, 0.92f, -27.5f);
        bottle(s, -9, 0.92f, -31.5f);
        prop(ItemKind.CAN, A.can, 0.033f, 0.06f, 0.033f, 0.3f, s, -8, 0.92f, -35.5f);
        ammo(s, WeaponType.AMMO_SMG, 30, -10, 0.92f, -35.6f);
        medkit(s, -14, 0.92f, -31.6f);
        light(s, -8, 3.6f, -28, 9, 0.75f, 0.85f, 1.0f);
        light(s, -8, 3.6f, -36, 9, 0.75f, 0.85f, 1.0f);
        light(s, -18, 3.6f, -27, 8, 0.75f, 0.85f, 1.0f);
        spawn("lab", s, -6, 0, -27, PI * 0.5f);
        spawn("lab", s, -16, 0, -36, 0);
        spawn("lab", s, -20, 0, -26, -PI * 0.5f);
    }

    // ===================================================================================== UNDERGROUND
    private void underground() {
        int s = S_UNDER;
        float y = -6f;
        // maintenance x[-24,-2] z[-56,-28]
        floor(s, -24, -56, -2, -28, y, C_UNDER);
        ceilingWithHole(s, -24, -56, -2, -28, -2f, -23, -38, -19, -30, 0x404542);
        wallZ(s, -24, -56, -28, y, 4, 0x6A706B);
        wallX(s, -28, -24, -2, y, 4, 0x6A706B);
        wallZ(s, -2, -56, -28, y, 4, 0x6A706B, -50f, -46f, 3f);
        Door tunnel = slidingDoor("door_tunnel", s, -2.1f, y, -50, -1.9f, y + 3, -46, 0, 2.9f, 0, A.slabShutter);
        tunnel.locked = true;
        tunnel.lockedHint = "Bloqueio de seguranca ativo";
        wallX(s, -56, -24, -2, y, 5, 0x6A706B, -14f, -11f, 3f);
        Door secret = slidingDoor("door_secret", s, -14, y, -56.1f, -11, y + 3, -55.9f, 3.0f, 0, 0, A.slabLab);
        secret.locked = true;
        secret.lockedHint = "Sem energia: use a alavanca";
        Interactive lever = interactive("lever_secret", Interactive.LEVER, s, -17.5f, y + 1.2f, -55.8f, PI, "secret_power");
        lever.label = "ENERGIA LAB";
        lever.oneShot = true;
        deco(s, -17.9f, y + 0.6f, -55.85f, -17.1f, y + 1.8f, -55.75f, 0x3A3F46);
        sign(s, "LAB SECRETO", -12.5f, y + 3.4f, -55.8f, PI, 0.22f, 0x7FE0FF);
        // pipes and pillars (cover)
        for (int i = 0; i < 3; i++) pillar(s, -14, y, -32 - i * 7, 0.4f, 4, 0x707670);
        for (int i = 0; i < 2; i++) pillar(s, -7, y, -36 - i * 8, 0.4f, 4, 0x707670);
        deco(s, -24, y + 3.3f, -29, -2, y + 3.6f, -28.6f, 0x8C5A3C);
        deco(s, -24, y + 3.0f, -29.6f, -2, y + 3.25f, -29.3f, 0x4E6A80);
        crateStack(s, -20, y, -48, 1.0f, 1.1f, 1.0f, 0x55603F);
        crateStack(s, -9, y, -52, 1.2f, 1.2f, 0.8f, 0x55603F);
        // armory with the shotgun
        block(s, -3.2f, y, -34.5f, -2.2f, y + 1.0f, -30.5f, C_METAL, StaticCollider.MAT_METAL);
        factory.weapon(WeaponType.BRUTUS, s, -2.7f, y + 1.06f, -32.5f, 4, true);
        for (int i = 0; i < 6; i++) factory.magazine(WeaponType.AMMO_SHELL, 1, s, -2.9f + (i % 2) * 0.12f, y + 1.03f, -31.2f - (i / 2) * 0.1f);
        ammo(s, WeaponType.AMMO_SHELL, 12, -2.7f, y + 1.0f, -33.9f);
        medkit(s, -2.7f, y + 1.0f, -30.9f);
        sign(s, "ARSENAL", -2.2f, y + 2.4f, -32.5f, PI * 0.5f, 0.18f, 0xFFD27A);
        prop(ItemKind.TOOL, A.wrench, 0.03f, 0.012f, 0.16f, 0.9f, s, -20, y + 1.1f, -47.8f);
        crateProp(s, -21.5f, y, -44, true);
        crateProp(s, -16, y, -42, true);
        bottle(s, -20.3f, y + 1.1f, -48.2f);
        light(s, -12, -2f, -34, 9, 0.45f, 0.6f, 0.45f);
        light(s, -12, -2f, -46, 9, 0.45f, 0.6f, 0.45f);
        light(s, -4, -2f, -52, 6, 0.9f, 0.2f, 0.15f);
        spawn("under", s, -10, y, -44, 0);
        spawn("under", s, -20, y, -40, PI * 0.5f);
        spawn("alarm", s, -21, y, -53, 0);
        spawn("alarm", s, -5, y, -54, 0);
        spawn("alarm", s, -22, y, -30, PI);

        // secret lab x[-24,-2] z[-76,-56] height 5
        floor(s, -24, -76, -2, -56, y, 0x4B5257);
        ceiling(s, -24, -76, -2, -56, -1f, 0x30353A);
        wallZ(s, -24, -76, -56, y, 5, 0x50585F);
        wallZ(s, -2, -76, -56, y, 5, 0x50585F);
        wallX(s, -76, -24, -2, y, 5, 0x50585F);
        // pedestal with the device
        block(s, -14, y, -73, -12, y + 1.0f, -71, 0x2A2E35, StaticCollider.MAT_METAL);
        deco(s, -13.8f, y + 1.0f, -72.8f, -12.2f, y + 1.04f, -71.2f, 0x40E0FF);
        Prop dev = factory.prop(ItemKind.DEVICE, A.device, 0.05f, 0.045f, 0.09f, 1.5f, s, -13, y + 1.05f, -72);
        dev.pickupEvent = "pickup_device";
        dev.name = "Dispositivo ZERO";
        for (int i = 0; i < 2; i++) {
            block(s, -21 + i * 13, y, -66, -17 + i * 13, y + 1.1f, -64, 0x3A4048, StaticCollider.MAT_METAL);
            coverAround(s, -19 + i * 13, y, -65, 2, 1, true);
        }
        pillar(s, -13, y, -61, 0.5f, 5, 0x45505A);
        // rifle rack
        block(s, -23.6f, y, -62, -22.8f, y + 1.1f, -58, C_METAL, StaticCollider.MAT_METAL);
        factory.weapon(WeaponType.LANCE, s, -23.2f, y + 1.16f, -60, 20, true);
        factory.magazine(WeaponType.AMMO_RIFLE, 20, s, -23.2f, y + 1.16f, -58.6f);
        ammo(s, WeaponType.AMMO_RIFLE, 20, -23.2f, y + 1.1f, -61.4f);
        medkit(s, -9, y + 1.1f, -65.2f);
        light(s, -13, -1f, -60, 10, 0.3f, 0.75f, 0.9f);
        light(s, -13, -1f, -70, 10, 0.3f, 0.75f, 0.9f);
        spawn("secret", s, -20, y, -70, 0);
        spawn("secret", s, -6, y, -70, 0);
        spawn("alarm", s, -22, y, -74, 0);
        spawn("alarm", s, -4, y, -74, 0);

        // escape tunnel x[-2,10] z[-50,-46] + stairs up x[10,18]
        floor(s, -2, -50, 10, -46, y, C_UNDER);
        ceiling(s, -2, -50, 10, -46, -2f, 0x404542);
        wallX(s, -50, -2, 18, y, 6, 0x6A706B);
        wallX(s, -46, -2, 18, y, 6, 0x6A706B);
        stairsX(s, -50, -46, 10, 18, y, 0, 16, 0x7E868C);
        light(s, 4, -2f, -48, 7, 0.9f, 0.3f, 0.2f);
        light(s, 14, -1f, -48, 6, 0.9f, 0.3f, 0.2f);
        sign(s, "SAIDA DE EMERGENCIA", -2.2f, y + 3.4f, -48, PI * 0.5f, 0.15f, 0x7CFF9A);
    }

    // ===================================================================================== GARAGE
    private void garage() {
        int s = S_GARAGE;
        floorWithHole(s, -2, -60, 24, -40, 0, 10, -50, 18, -46, 0x55585B);
        ceiling(s, -2, -60, 24, -40, 6, C_CEIL);
        wallZ(s, -2, -60, -40, 0, 6, C_WALL);
        wallZ(s, 24, -60, -40, 0, 6, C_WALL);
        wallX(s, -60, -2, 24, 0, 6, C_WALL, 8f, 16f, 4f);
        Door gout = slidingDoor("door_garage_out", s, 8, 0, -60.1f, 16, 4, -59.9f, 0, 3.8f, 0, A.slabGarage);
        gout.locked = true;
        gout.lockedHint = "Use o botao do portao";
        gout.speed = 0.35f;
        Interactive btn = interactive("btn_garage", Interactive.BUTTON, s, 6.4f, 1.3f, -59.82f, PI, "garage_open");
        btn.label = "PORTAO";
        btn.enabled = false;
        // stair rails around the hole (entry side x=10 open toward the tunnel stairs top at x=18)
        block(s, 10, 0, -50.15f, 18, 1.0f, -50.0f, C_STRIPE, StaticCollider.MAT_METAL);
        block(s, 10, 0, -46.0f, 18, 1.0f, -45.85f, C_STRIPE, StaticCollider.MAT_METAL);
        block(s, 9.85f, 0, -50.15f, 10.0f, 1.0f, -45.85f, C_STRIPE, StaticCollider.MAT_METAL);
        car(s, 4, -45, false, 0x2E4A6A);
        car(s, 20, -54, false, 0x6A2E2E);
        block(s, 16, 0, -44, 22, 2.8f, -41.5f, 0xB8B4A8, StaticCollider.MAT_METAL); // truck box
        block(s, 22, 0, -44, 23.6f, 2.0f, -41.5f, 0x2E4A6A, StaticCollider.MAT_METAL);
        coverAround(s, 19, 0, -42.75f, 3, 1.25f, false);
        crateStack(s, 2, 0, -56, 1, 1.1f, 0.8f, 0x55603F);
        pillar(s, 11, 0, -55, 0.4f, 6, 0x8A8F94);
        sign(s, "SAIDA", 12, 4.6f, -59.8f, PI, 0.4f, 0x7CFF9A);
        medkit(s, 2, 1.1f, -56);
        ammo(s, WeaponType.AMMO_SMG, 30, 2.4f, 1.1f, -55.8f);
        ammo(s, WeaponType.AMMO_RIFLE, 10, 1.6f, 1.1f, -55.8f);
        prop(ItemKind.BARREL, A.barrel, 0.28f, 0.42f, 0.28f, 18f, s, 22.6f, 0, -58.6f);
        light(s, 6, 6, -50, 12, 0.85f, 0.8f, 0.65f);
        light(s, 18, 6, -50, 12, 0.85f, 0.8f, 0.65f);
        spawn("garage", s, 20, 0, -58, 0);
        spawn("garage", s, 2, 0, -52, -PI * 0.5f);
        spawn("garage", s, 14, 0, -42, PI);
        L.triggers.add(new TriggerZone("enter_garage", -2, -1, -60, 24, 4, -40));
    }

    // ===================================================================================== YARD
    private void yard() {
        int s = S_YARD;
        floor(s, -6, -86, 30, -60, 0, C_DIRT);
        wallZ(s, -6, -86, -60, 0, 3, C_FENCE);
        wallZ(s, 30, -86, -60, 0, 3, C_FENCE);
        wallX(s, -86, -6, 30, 0, 3, C_FENCE);
        wallX(s, -60, -6, -2, 0, 6, C_WALL);
        // helipad / extraction
        deco(s, 7, 0.0f, -82, 17, 0.02f, -72, 0x2F3236);
        deco(s, 11.5f, 0.0f, -80, 12.5f, 0.03f, -74, 0xE8E8E8);
        deco(s, 9.5f, 0.0f, -77.5f, 14.5f, 0.03f, -76.5f, 0xE8E8E8);
        sign(s, "PONTO DE EXTRACAO", 12, 2.5f, -82, PI, 0.35f, 0x7CFF9A);
        car(s, 0, -70, true, 0x3A3A3A);
        crateStack(s, 24, 0, -70, 1, 1.1f, 1, 0x55603F);
        L.triggers.add(new TriggerZone("exit_reached", 6, -1, -84, 18, 4, -72));
    }

    // ===================================================================================== NAVIGATION
    private int n(float x, float y, float z, int s) { return L.nav.add(x, y, z, s); }

    private int node(float x, float y, float z) {
        return L.nav.nearest(new Vec3(x, y, z), L.physics, true, new com.joaolimz.operationzero.game.physics.RayHit());
    }

    private final com.joaolimz.operationzero.game.math.Aabb probe = new com.joaolimz.operationzero.game.math.Aabb();

    /** Adds nav nodes on a grid inside a room, skipping points blocked by static geometry. */
    private void grid(int s, float x0, float z0, float x1, float z1, float y, float spacing) {
        int nx = Math.max(1, Math.round((x1 - x0) / spacing)), nz = Math.max(1, Math.round((z1 - z0) / spacing));
        float sx = (x1 - x0) / nx, sz = (z1 - z0) / nz;
        for (int i = 0; i < nx; i++) {
            for (int j = 0; j < nz; j++) {
                float x = x0 + sx * (i + 0.5f), z = z0 + sz * (j + 0.5f);
                probe.set(x - 0.4f, y + 0.15f, z - 0.4f, x + 0.4f, y + 1.7f, z + 0.4f);
                if (L.physics.overlapsSolid(probe, com.joaolimz.operationzero.game.physics.Layers.STATIC)) continue;
                n(x, y, z, s);
            }
        }
    }

    private void navigation() {
        L.physics.buildGrid();
        float y = -6f;
        // automatic room grids (obstacles are skipped)
        grid(S_EXT, -33, 1, 35, 41, 0, 5.5f);
        grid(S_ADMIN, -6, -12, 6, 0, 0, 2.6f);
        grid(S_ADMIN, -24, -16, -6, 0, 0, 3.0f);
        grid(S_ADMIN, -16, -24, -6, -16, 0, 3.0f);
        grid(S_ADMIN, -24, -24, -16, -16, 0, 2.6f);
        grid(S_ADMIN, 6, -12, 16, 0, 0, 2.6f);
        grid(S_CORE, -2, -40, 2, -12, 0, 4.0f);
        grid(S_CORE, 2, -40, 24, -12, 0, 3.2f);
        grid(S_LAB, -24, -40, -2, -24, 0, 3.0f);
        grid(S_UNDER, -24, -56, -2, -28, y, 3.2f);
        grid(S_UNDER, -24, -76, -2, -56, y, 3.2f);
        grid(S_UNDER, -2, -50, 10, -46, y, 3.0f);
        grid(S_GARAGE, -2, -60, 24, -40, 0, 3.0f);
        grid(S_YARD, -6, -86, 30, -60, 0, 5.0f);
        // explicit door / stair nodes
        int extDoor = n(0, 0, 1.6f, S_EXT), lobbyIn = n(0, 0, -1.6f, S_ADMIN);
        int lobbyW = n(-4.8f, 0, -4.4f, S_ADMIN), offE = n(-7.3f, 0, -4.4f, S_ADMIN);
        int lobbyE = n(4.8f, 0, -4.4f, S_ADMIN), sec1 = n(7.3f, 0, -4.4f, S_ADMIN);
        int lobbyN = n(0, 0, -10.8f, S_ADMIN), c1 = n(0, 0, -13.3f, S_CORE);
        int mgrOut = n(-14.6f, 0, -20.4f, S_ADMIN), mgrIn = n(-17.4f, 0, -20.4f, S_ADMIN);
        int c3 = n(0, 0, -25.8f, S_CORE), w0 = n(3.4f, 0, -25.8f, S_CORE);
        int c4 = n(0, 0, -32f, S_CORE), labIn = n(-3.4f, 0, -32f, S_LAB);
        int c5 = n(0, 0, -38.7f, S_CORE), gIn = n(0, 0, -41.3f, S_GARAGE);
        int labStair = n(-21, 0, -28.8f, S_LAB), stairMid = n(-21, -3, -34, S_UNDER), stairBot = n(-21, y, -39.2f, S_UNDER);
        int u10 = n(-12.5f, y, -54.6f, S_UNDER), sl0 = n(-12.5f, y, -57.4f, S_UNDER);
        int u8 = n(-3.4f, y, -48, S_UNDER), t0n = n(-0.6f, y, -48, S_UNDER);
        int t2 = n(9.4f, y, -48, S_UNDER), t3 = n(14, -3, -48, S_UNDER), gTop = n(19.2f, 0, -48, S_GARAGE);
        int g7 = n(12, 0, -58.6f, S_GARAGE), y0 = n(12, 0, -61.4f, S_YARD);

        Door shutter = L.door("door_shutter");
        Door labDoor = L.door("door_lab");
        Door secret = L.door("door_secret");
        Door tunnel = L.door("door_tunnel");
        Door gout = L.door("door_garage_out");
        L.nav.link(extDoor, lobbyIn, shutter);
        L.nav.link(lobbyW, offE); L.nav.link(lobbyE, sec1); L.nav.link(lobbyN, c1);
        L.nav.link(mgrOut, mgrIn);
        L.nav.link(c3, w0);
        L.nav.link(c4, labIn, labDoor);
        L.nav.link(c5, gIn);
        L.nav.link(labStair, stairMid); L.nav.link(stairMid, stairBot);
        L.nav.link(u10, sl0, secret);
        L.nav.link(u8, t0n, tunnel);
        L.nav.link(t2, t3); L.nav.link(t3, gTop);
        L.nav.link(g7, y0, gout);
        L.nav.build(L.physics, 7.5f);
        // patrols
        L.addPatrol("ext", new int[]{node(-18, 0, 8), node(-20, 0, 20), node(0, 0, 17), node(16, 0, 15), node(16, 0, 4), node(0, 0, 9)});
        L.addPatrol("admin", new int[]{node(-11, 0, -2), node(-11, 0, -14), node(-20, 0, -12), node(-20, 0, -4), node(-3, 0, -5), node(3, 0, -5), node(11, 0, -6)});
        L.addPatrol("core", new int[]{node(0, 0, -20), node(0, 0, -34), node(5, 0, -27), node(12, 0, -14), node(22, 0, -20), node(21, 0, -38), node(9, 0, -31)});
        L.addPatrol("lab", new int[]{node(-5, 0, -26), node(-18, 0, -26), node(-17, 0, -39), node(-5, 0, -38), node(-11, 0, -29.5f), node(-11, 0, -33.5f)});
        L.addPatrol("under", new int[]{node(-17, y, -30), node(-4, y, -30), node(-4, y, -40), node(-4, y, -48), node(-10, y, -49), node(-17, y, -44), node(-10, y, -40)});
        L.addPatrol("secret", new int[]{node(-19, y, -60), node(-19, y, -69), node(-13, y, -68), node(-6, y, -69), node(-6, y, -60)});
        L.addPatrol("garage", new int[]{node(7, 0, -44.8f), node(13, 0, -44.8f), node(22.5f, 0, -48), node(15, 0, -53), node(7, 0, -53), node(1, 0, -48)});
        L.addPatrol("yard", new int[]{node(12, 0, -64), node(4, 0, -72), node(12, 0, -78), node(22, 0, -76), node(20, 0, -64)});
        // cover nodes
        com.joaolimz.operationzero.game.physics.RayHit rh = new com.joaolimz.operationzero.game.physics.RayHit();
        for (int i = 0; i < L.covers.size(); i++) {
            CoverPoint c = L.covers.get(i);
            c.node = L.nav.nearest(c.pos, L.physics, true, rh);
        }
        // remove cover points that ended inside geometry
        for (int i = L.covers.size() - 1; i >= 0; i--) {
            CoverPoint c = L.covers.get(i);
            probe.setCenter(c.pos.x, c.pos.y + 0.9f, c.pos.z, 0.25f, 0.7f, 0.25f);
            if (c.node < 0 || L.physics.overlapsSolid(probe, com.joaolimz.operationzero.game.physics.Layers.STATIC)) L.covers.remove(i);
        }
    }

    // ===================================================================================== MESHES
    private void finishMeshes() {
        for (int i = 0; i < SECTOR_COUNT; i++) {
            mb[i].bake(0, bk[i]);
        }
        for (int i = 0; i < fixtures.size(); i++) {
            float[] f = fixtures.get(i);
            int s = (int) f[0];
            mb[s].color(Math.min(1f, 0.6f + f[4]), Math.min(1f, 0.6f + f[5]), Math.min(1f, 0.6f + f[6]));
            mb[s].boxMinMax(f[1] - 0.5f, f[2] - 0.08f, f[3] - 0.15f, f[1] + 0.5f, f[2] - 0.01f, f[3] + 0.15f, MeshBuilder.FACE_ALL, 0f);
        }
        for (int i = 0; i < SECTOR_COUNT; i++) {
            Sector sec = L.sectors.get(i);
            int id;
            if (reuseIds != null && i < reuseIds.length && reuseIds[i] >= 0) {
                id = reuseIds[i];
                lib.replace(id, mb[i].build("sector_" + i));
            } else {
                id = lib.register(mb[i].build("sector_" + i));
            }
            sec.staticMeshes.add(id);
            sec.triangles = lib.get(id).triangleCount();
            lib.setResident(id, false);
        }
    }

    private void checkpoints() {
        set(0, 0, 0, 30.5f, 0);
        set(1, 0, 0, -3, 0);
        set(2, -12, 0, -18, -PI * 0.5f);
        set(3, 0, 0, -29, PI * 0.5f);
        set(4, -13, -6, -66, PI);
        set(5, -6, -6, -48, -PI * 0.5f);
        set(6, 12, 0, -70, 0);
    }

    private void set(int i, float x, float y, float z, float yaw) {
        L.checkpoints[i] = new Vec3(x, y, z);
        L.checkpointYaw[i] = yaw;
    }
}
