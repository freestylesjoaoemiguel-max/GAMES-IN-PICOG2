package com.joaolimz.pegapega.game.world;

import com.joaolimz.pegapega.game.math.Vec3;
import com.joaolimz.pegapega.game.mesh.LightBaker;
import com.joaolimz.pegapega.game.mesh.MeshBuilder;
import com.joaolimz.pegapega.game.mesh.MeshLibrary;
import com.joaolimz.pegapega.game.physics.PhysicsWorld;
import com.joaolimz.pegapega.game.physics.StaticCollider;

import java.util.ArrayList;

/**
 * "Patio Ferro-Velho": an original playground built from scrap - container walls, a scaffold tower,
 * tire towers, a pipe tunnel, a bridge and lots of ledges to climb. Everything is boxes so collisions stay
 * cheap; lighting is baked into vertex colors.
 */
public final class Arena {
    public static final float HALF = 24f;

    public final PhysicsWorld physics = new PhysicsWorld();
    public final ArrayList<Vec3> spawns = new ArrayList<Vec3>();
    /** High places bots like to escape to. */
    public final ArrayList<Vec3> perches = new ArrayList<Vec3>();
    public final Vec3 board = new Vec3();
    public float boardYaw;
    public int mesh = -1;

    private final MeshBuilder mb = new MeshBuilder();
    private final LightBaker bk = new LightBaker();

    // playful palette
    private static final int C_GROUND = 0x7FA65A, C_SAND = 0xC9B27A, C_PATH = 0xA89B83;
    private static final int[] C_CONT = {0xD9534F, 0x3E7CC9, 0xF0AD4E, 0x5CB85C, 0x8E6CC9, 0x2BB0B0};
    private static final int C_STEEL = 0xF2C230, C_DARK = 0x3B3F46, C_WOOD = 0xA87444, C_TIRE = 0x2A2B2E, C_PIPE = 0x9AA3AD;

    public Arena build(MeshLibrary lib, int reuseId) {
        bk.ambient(0.55f, 0.55f, 0.6f);
        bk.sun = true;
        bk.sunR = 0.75f; bk.sunG = 0.7f; bk.sunB = 0.6f;
        // ground with paths
        block(-HALF - 2, -1f, -HALF - 2, HALF + 2, 0f, HALF + 2, C_GROUND, StaticCollider.MAT_CONCRETE);
        deco(-2.5f, 0f, -HALF, 2.5f, 0.01f, HALF, C_PATH);
        deco(-HALF, 0f, -2.5f, HALF, 0.01f, 2.5f, C_PATH);
        deco(-8f, 0f, -8f, 8f, 0.012f, 8f, C_SAND);
        // boundary: stacked containers (climbable) and a tall invisible wall beyond
        int ci = 0;
        for (float x = -HALF; x < HALF; x += 6f) {
            container(x + 3f, -HALF - 1.2f, 6f, 2.4f, 2 + (ci % 2), C_CONT[ci++ % C_CONT.length]);
            container(x + 3f, HALF + 1.2f, 6f, 2.4f, 2 + (ci % 2), C_CONT[ci++ % C_CONT.length]);
            container(-HALF - 1.2f, x + 3f, 2.4f, 6f, 2 + (ci % 2), C_CONT[ci++ % C_CONT.length]);
            container(HALF + 1.2f, x + 3f, 2.4f, 6f, 2 + (ci % 2), C_CONT[ci++ % C_CONT.length]);
        }
        solid(-HALF - 3f, 0, -HALF - 3f, HALF + 3f, 30f, -HALF - 2.4f);
        solid(-HALF - 3f, 0, HALF + 2.4f, HALF + 3f, 30f, HALF + 3f);
        solid(-HALF - 3f, 0, -HALF - 3f, -HALF - 2.4f, 30f, HALF + 3f);
        solid(HALF + 2.4f, 0, -HALF - 3f, HALF + 3f, 30f, HALF + 3f);

        // central scaffold tower: 4 legs, 3 decks, crate steps on one side
        float[] deckY = {3f, 6f, 9.5f};
        for (int i = -1; i <= 1; i += 2) for (int j = -1; j <= 1; j += 2) {
            block(i * 3f - 0.25f, 0f, j * 3f - 0.25f, i * 3f + 0.25f, deckY[2] + 0.3f, j * 3f + 0.25f, C_STEEL, StaticCollider.MAT_METAL);
        }
        for (int k = 0; k < 3; k++) {
            float y = deckY[k];
            float s = k == 2 ? 2.4f : 3.4f;
            // decks have a hole on alternating sides so you can climb through
            if (k == 1) {
                block(-s, y - 0.25f, -s, s, y, s - 2.2f, C_WOOD, StaticCollider.MAT_WOOD);
            } else {
                block(-s, y - 0.25f, -s, s, y, s, C_WOOD, StaticCollider.MAT_WOOD);
            }
            // railings (low, climbable)
            deco(-s, y, -s - 0.05f, s, y + 0.5f, -s + 0.05f, C_STEEL);
            perches.add(new Vec3(0, y, 0));
        }
        steps(3.6f, -1.5f, 1.2f, 4, 0.75f, true);
        // flag on top
        deco(-0.06f, deckY[2], -0.06f, 0.06f, deckY[2] + 3f, 0.06f, C_DARK);
        deco(0.06f, deckY[2] + 2.2f, -0.03f, 1.2f, deckY[2] + 2.9f, 0.03f, 0xE07A1F);

        // container stacks
        container(-13f, -12f, 6f, 2.4f, 2, C_CONT[1]);
        container(-13f, -9.2f, 6f, 2.4f, 1, C_CONT[2]);
        perches.add(new Vec3(-13f, 5.2f, -12f));
        container(12f, 12f, 2.4f, 6f, 3, C_CONT[0]);
        container(9.2f, 12f, 2.4f, 6f, 1, C_CONT[3]);
        perches.add(new Vec3(12f, 7.8f, 12f));
        container(14f, -13f, 6f, 2.4f, 1, C_CONT[4]);
        container(14f, -10.6f, 6f, 2.4f, 2, C_CONT[5]);
        perches.add(new Vec3(14f, 5.2f, -10.6f));

        // bridge from the tower's middle deck to a west lookout platform on a steel column
        block(-10f, 5.75f, -2.8f, -3.4f, 6f, -1.6f, C_WOOD, StaticCollider.MAT_WOOD);
        deco(-10f, 6f, -2.85f, -3.4f, 6.45f, -2.75f, C_STEEL);
        block(-13f, 5.75f, -4f, -10f, 6f, -0.4f, C_WOOD, StaticCollider.MAT_WOOD);
        block(-11.8f, 0f, -2.5f, -11.2f, 5.75f, -1.9f, C_STEEL, StaticCollider.MAT_METAL);
        for (float y = 1f; y < 5.7f; y += 1f) deco(-12f, y, -2.28f, -11f, y + 0.1f, -2.12f, C_DARK);
        perches.add(new Vec3(-11.5f, 6f, -2.2f));

        // tire towers (cylinders drawn, boxes collide)
        tires(-15f, 13f, 6);
        tires(16f, 1f, 4);
        tires(-4f, 17f, 3);
        tires(-18f, -1f, 5);
        perches.add(new Vec3(-15f, 6f * 0.55f, 13f));

        // pipe tunnel
        pipe(5f, -18f, 9f);
        // crate piles (steps for climbing)
        crate(-7f, 9f, 1.2f, 1f);
        crate(-6f, 10.2f, 1f, 2.2f);
        crate(-7.6f, 11.2f, 1.2f, 3.2f);
        perches.add(new Vec3(-7.6f, 3.2f, 11.2f));
        crate(8f, -6f, 1.4f, 1.4f);
        crate(9.2f, -4.6f, 1f, 0.8f);
        crate(-10f, 3f, 1.2f, 1.2f);
        crate(19f, -19f, 1.4f, 2.0f);
        crate(-19f, 19f, 1.4f, 2.0f);
        // tall poles with a small top platform (classic climbing spots)
        pole(18f, 18f, 7f);
        pole(-19f, -18f, 7f);
        pole(-2f, -15f, 5f);

        // scoreboard on the south side
        board.set(0f, 3.6f, HALF - 0.2f);
        boardYaw = 0f;
        deco(-3.2f, 1.8f, HALF - 0.1f, 3.2f, 5.4f, HALF + 0.1f, C_DARK);
        deco(-0.15f, 0f, HALF - 0.1f, 0.15f, 1.8f, HALF + 0.1f, C_DARK);

        // spawn ring
        for (int i = 0; i < 10; i++) {
            double a = i * Math.PI * 2 / 10;
            spawns.add(new Vec3((float) Math.cos(a) * 11f, 0f, (float) Math.sin(a) * 11f));
        }

        physics.buildGrid();
        // drop spawn points that ended up inside props
        com.joaolimz.pegapega.game.math.Aabb probe = new com.joaolimz.pegapega.game.math.Aabb();
        for (int i = spawns.size() - 1; i >= 0; i--) {
            Vec3 p = spawns.get(i);
            probe.setCenter(p.x, 0.8f, p.z, 0.5f, 0.6f, 0.5f);
            if (physics.overlapsSolid(probe, com.joaolimz.pegapega.game.physics.Layers.STATIC)) spawns.remove(i);
        }
        mb.bake(0, bk);
        if (reuseId >= 0) { lib.replace(reuseId, mb.build("arena")); mesh = reuseId; }
        else mesh = lib.register(mb.build("arena"));
        return this;
    }

    // ------------------------------------------------------------------ helpers
    private StaticCollider solid(float x0, float y0, float z0, float x1, float y1, float z1) {
        return physics.addStatic(Math.min(x0, x1), Math.min(y0, y1), Math.min(z0, z1), Math.max(x0, x1), Math.max(y0, y1), Math.max(z0, z1), 0);
    }

    private void block(float x0, float y0, float z0, float x1, float y1, float z1, int color, int mat) {
        solid(x0, y0, z0, x1, y1, z1).material = mat;
        mb.color(color).boxMinMax(Math.min(x0, x1), Math.min(y0, y1), Math.min(z0, z1), Math.max(x0, x1), Math.max(y0, y1), Math.max(z0, z1),
                MeshBuilder.FACE_ALL, 2.5f);
    }

    private void deco(float x0, float y0, float z0, float x1, float y1, float z1, int color) {
        mb.color(color).boxMinMax(Math.min(x0, x1), Math.min(y0, y1), Math.min(z0, z1), Math.max(x0, x1), Math.max(y0, y1), Math.max(z0, z1),
                MeshBuilder.FACE_ALL, 0f);
    }

    private static int darken(int c, float k) {
        return (((int) (((c >> 16) & 255) * k)) << 16) | (((int) (((c >> 8) & 255) * k)) << 8) | (int) ((c & 255) * k);
    }

    /** Stacked shipping containers with ribs. */
    private void container(float cx, float cz, float sx, float sz, int levels, int color) {
        for (int l = 0; l < levels; l++) {
            int c = l % 2 == 0 ? color : C_CONT[(indexOf(color) + 2) % C_CONT.length];
            float y0 = l * 2.6f, y1 = y0 + 2.6f;
            block(cx - sx / 2, y0, cz - sz / 2, cx + sx / 2, y1, cz + sz / 2, c, StaticCollider.MAT_METAL);
            int rib = darken(c, 0.75f);
            boolean alongX = sx >= sz;
            for (float t = -0.4f; t <= 0.41f; t += 0.2f) {
                if (alongX) {
                    deco(cx + t * sx - 0.06f, y0 + 0.1f, cz - sz / 2 - 0.04f, cx + t * sx + 0.06f, y1 - 0.1f, cz + sz / 2 + 0.04f, rib);
                } else {
                    deco(cx - sx / 2 - 0.04f, y0 + 0.1f, cz + t * sz - 0.06f, cx + sx / 2 + 0.04f, y1 - 0.1f, cz + t * sz + 0.06f, rib);
                }
            }
        }
    }

    private static int indexOf(int c) {
        for (int i = 0; i < C_CONT.length; i++) if (C_CONT[i] == c) return i;
        return 0;
    }

    private void steps(float x, float z, float w, int count, float rise, boolean alongZ) {
        for (int i = 0; i < count; i++) {
            float top = rise * (i + 1);
            float zz = z + i * 0.9f;
            block(x - w / 2 + i * 0.0f, 0f, zz, x + w / 2, top, zz + 0.9f, C_WOOD, StaticCollider.MAT_WOOD);
        }
    }

    private void tires(float cx, float cz, int count) {
        for (int i = 0; i < count; i++) {
            float y = i * 0.55f;
            mb.color(C_TIRE).push().translate(cx + (i % 2) * 0.12f, y + 0.275f, cz).cylinder(0.9f, 0.275f, 12, true).pop();
            mb.color(0x55585C).push().translate(cx + (i % 2) * 0.12f, y + 0.276f, cz).disc(0.45f, 10).pop();
        }
        solid(cx - 0.85f, 0f, cz - 0.85f, cx + 0.95f, count * 0.55f, cz + 0.85f).material = StaticCollider.MAT_WOOD;
    }

    private void pipe(float cx, float cz, float len) {
        float r = 1.4f, t = 0.25f;
        block(cx - len / 2, 0f, cz - r, cx + len / 2, t, cz + r, C_PIPE, StaticCollider.MAT_METAL);
        block(cx - len / 2, 2 * r - t, cz - r, cx + len / 2, 2 * r, cz + r, C_PIPE, StaticCollider.MAT_METAL);
        block(cx - len / 2, 0f, cz - r, cx + len / 2, 2 * r, cz - r + t, darken(C_PIPE, 0.85f), StaticCollider.MAT_METAL);
        block(cx - len / 2, 0f, cz + r - t, cx + len / 2, 2 * r, cz + r, darken(C_PIPE, 0.85f), StaticCollider.MAT_METAL);
        deco(cx - len / 2 - 0.1f, 0f, cz - r - 0.1f, cx - len / 2 + 0.1f, 2 * r + 0.1f, cz - r + t, 0xE07A1F);
        deco(cx - len / 2 - 0.1f, 0f, cz + r - t, cx - len / 2 + 0.1f, 2 * r + 0.1f, cz + r + 0.1f, 0xE07A1F);
        perches.add(new Vec3(cx, 2 * r, cz));
    }

    private void crate(float cx, float cz, float half, float h) {
        block(cx - half, 0f, cz - half, cx + half, h, cz + half, C_WOOD, StaticCollider.MAT_WOOD);
        deco(cx - half - 0.02f, h * 0.45f, cz - half - 0.02f, cx + half + 0.02f, h * 0.55f, cz + half + 0.02f, darken(C_WOOD, 0.7f));
    }

    private void pole(float cx, float cz, float h) {
        block(cx - 0.3f, 0f, cz - 0.3f, cx + 0.3f, h, cz + 0.3f, C_STEEL, StaticCollider.MAT_METAL);
        block(cx - 1.2f, h, cz - 1.2f, cx + 1.2f, h + 0.25f, cz + 1.2f, C_WOOD, StaticCollider.MAT_WOOD);
        // rungs to grab
        for (float y = 1f; y < h; y += 1f) deco(cx - 0.45f, y, cz - 0.08f, cx + 0.45f, y + 0.1f, cz + 0.08f, C_DARK);
        perches.add(new Vec3(cx, h + 0.25f, cz));
    }
}
