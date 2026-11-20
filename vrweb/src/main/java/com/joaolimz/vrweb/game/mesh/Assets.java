package com.joaolimz.vrweb.game.mesh;

/** Original procedural models: a calm dark "lounge" environment. */
public final class Assets {
    public final MeshLibrary lib;
    public int sky, uiQuad, marker, laser, floor;
    private static final MeshBuilder B = new MeshBuilder();

    public Assets(MeshLibrary lib) {
        this.lib = lib;
        sky = lib.register(sky());
        uiQuad = lib.register(quad());
        marker = lib.register(marker());
        laser = lib.register(unitBox());
        floor = lib.register(floor());
    }

    private static MeshData sky() {
        MeshBuilder b = B.clear();
        int slices = 16, stacks = 8;
        for (int j = 0; j < stacks; j++) {
            float p0 = (float) (Math.PI * 0.5 * j / stacks), p1 = (float) (Math.PI * 0.5 * (j + 1) / stacks);
            float k = j / (float) stacks;
            // night gradient: warm dark horizon to deep blue zenith
            b.color(0.16f - 0.09f * k, 0.17f - 0.10f * k, 0.24f - 0.10f * k);
            for (int i = 0; i < slices; i++) {
                float t0 = (float) (Math.PI * 2 * i / slices), t1 = (float) (Math.PI * 2 * (i + 1) / slices);
                float y0 = (float) Math.sin(p0), y1 = (float) Math.sin(p1), r0 = (float) Math.cos(p0), r1 = (float) Math.cos(p1);
                b.quad((float) Math.cos(t0) * r0 * 90, y0 * 60 - 8, (float) Math.sin(t0) * r0 * 90,
                        (float) Math.cos(t0) * r1 * 90, y1 * 60 - 8, (float) Math.sin(t0) * r1 * 90,
                        (float) Math.cos(t1) * r1 * 90, y1 * 60 - 8, (float) Math.sin(t1) * r1 * 90,
                        (float) Math.cos(t1) * r0 * 90, y0 * 60 - 8, (float) Math.sin(t1) * r0 * 90);
            }
        }
        // a few stars
        b.color(1f, 1f, 1f);
        java.util.Random r = new java.util.Random(5);
        for (int i = 0; i < 60; i++) {
            double az = r.nextDouble() * Math.PI * 2, el = 0.15 + r.nextDouble() * 1.2;
            float x = (float) (Math.cos(az) * Math.cos(el) * 85), y = (float) (Math.sin(el) * 55 - 8), z = (float) (Math.sin(az) * Math.cos(el) * 85);
            b.box(x, y, z, 0.25f, 0.25f, 0.25f);
        }
        return b.build("sky");
    }

    private static MeshData quad() {
        MeshBuilder b = B.clear();
        b.color(1f, 1f, 1f, 1f).quad(-0.5f, -0.5f, 0, 0.5f, -0.5f, 0, 0.5f, 0.5f, 0, -0.5f, 0.5f, 0);
        return b.build("ui_quad");
    }

    private static MeshData marker() {
        MeshBuilder b = B.clear();
        b.color(1f, 1f, 1f, 1f).ring(0.2f, 0.32f, 16, 0.95f, 0.95f);
        return b.build("marker");
    }

    private static MeshData unitBox() {
        MeshBuilder b = B.clear();
        b.color(1f, 1f, 1f, 0.5f).box(0, 0, 0, 0.5f, 0.5f, 0.5f);
        return b.build("laser");
    }

    /** Dark floor disc with a soft glowing ring, so you feel seated in a room. */
    private static MeshData floor() {
        MeshBuilder b = B.clear();
        b.color(0.09f, 0.1f, 0.13f).push().translate(0, -1.7f, 0).scale(1f, 1f, 1f).disc(9f, 32).pop();
        b.color(0.35f, 0.4f, 0.95f, 1f).push().translate(0, -1.69f, 0).ring(2.4f, 2.5f, 48, 0.6f, 0.6f).pop();
        b.color(0.2f, 0.22f, 0.5f, 1f).push().translate(0, -1.69f, 0).ring(4.2f, 4.26f, 48, 0.35f, 0.35f).pop();
        return b.build("floor");
    }
}
