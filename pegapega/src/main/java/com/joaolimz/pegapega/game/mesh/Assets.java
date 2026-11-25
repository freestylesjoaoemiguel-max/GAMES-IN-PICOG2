package com.joaolimz.pegapega.game.mesh;

/**
 * Original procedural models for PEGA-PEGA. Characters ("Rolos") are round little robots with long noodle
 * arms; they are built in light grey so the per-player color is applied as a tint by the shader.
 */
public final class Assets {
    public final MeshLibrary lib;
    public int body, bodyLod, visor, eye, antenna, hand, handFist, arm, crown, halo;
    public int sky, uiQuad, blob, marker, spark, laser, flag;

    private static final MeshBuilder B = new MeshBuilder();

    public Assets(MeshLibrary lib) {
        this.lib = lib;
        body = lib.register(body(true));
        bodyLod = lib.register(body(false));
        visor = lib.register(visor());
        eye = lib.register(eye());
        antenna = lib.register(antenna());
        hand = lib.register(hand(false));
        handFist = lib.register(hand(true));
        arm = lib.register(arm());
        crown = lib.register(crown());
        halo = lib.register(halo());
        sky = lib.register(sky());
        uiQuad = lib.register(quad());
        blob = lib.register(blob());
        marker = lib.register(marker());
        spark = lib.register(spark());
        laser = lib.register(unitBox(1f, 1f, 1f, 0.5f));
        flag = lib.register(flag());
    }

    /** Round body, radius ~0.3, centered at origin; white (tinted at runtime). */
    private static MeshData body(boolean detailed) {
        MeshBuilder b = B.clear();
        int sl = detailed ? 12 : 6, st = detailed ? 8 : 4;
        b.color(0.95f, 0.95f, 0.95f).push().scale(1f, 0.92f, 1f).sphere(0.3f, sl, st).pop();
        if (detailed) {
            // belly plate and little feet (no legs: they move with their arms)
            b.color(0.82f, 0.82f, 0.82f).box(0, -0.08f, -0.255f, 0.15f, 0.1f, 0.03f);
            b.color(0.35f, 0.35f, 0.38f).box(-0.12f, -0.29f, -0.02f, 0.07f, 0.03f, 0.1f);
            b.color(0.35f, 0.35f, 0.38f).box(0.12f, -0.29f, -0.02f, 0.07f, 0.03f, 0.1f);
        }
        return b.build(detailed ? "rolo_body" : "rolo_body_lod");
    }

    private static MeshData visor() {
        MeshBuilder b = B.clear();
        b.color(0.08f, 0.1f, 0.14f).box(0, 0.06f, -0.25f, 0.2f, 0.09f, 0.06f);
        return b.build("rolo_visor");
    }

    private static MeshData eye() {
        MeshBuilder b = B.clear();
        b.color(1f, 1f, 1f).box(0, 0, 0, 0.035f, 0.045f, 0.01f);
        return b.build("rolo_eye");
    }

    private static MeshData antenna() {
        MeshBuilder b = B.clear();
        b.color(0.4f, 0.4f, 0.45f).box(0, 0.1f, 0, 0.012f, 0.1f, 0.012f);
        b.color(1f, 1f, 1f).push().translate(0, 0.22f, 0).sphere(0.04f, 6, 4).pop();
        return b.build("rolo_antenna");
    }

    /** Hand: a mitten with three fat fingers pointing -Z. */
    private static MeshData hand(boolean fist) {
        MeshBuilder b = B.clear();
        b.color(0.9f, 0.9f, 0.9f).box(0, 0, 0, 0.055f, 0.03f, 0.06f);
        for (int f = -1; f <= 1; f++) {
            if (fist) b.color(0.8f, 0.8f, 0.8f).box(f * 0.035f, -0.01f, -0.07f, 0.016f, 0.03f, 0.02f);
            else b.color(0.8f, 0.8f, 0.8f).box(f * 0.035f, 0f, -0.1f, 0.016f, 0.018f, 0.045f);
        }
        b.color(0.8f, 0.8f, 0.8f).box(-0.07f, 0.01f, -0.01f, 0.02f, 0.018f, 0.035f);
        return b.build(fist ? "rolo_fist" : "rolo_hand");
    }

    /** Unit-length arm segment along Z (scaled at runtime). */
    private static MeshData arm() {
        MeshBuilder b = B.clear();
        b.color(0.75f, 0.75f, 0.75f).push().rotate(1, 0, 0, (float) (Math.PI / 2)).cylinder(0.5f, 0.5f, 6, false).pop();
        return b.build("rolo_arm");
    }

    private static MeshData crown() {
        MeshBuilder b = B.clear();
        b.color(1f, 0.8f, 0.15f);
        for (int i = 0; i < 5; i++) {
            double a = i * Math.PI * 2 / 5;
            b.box((float) Math.cos(a) * 0.12f, 0.04f, (float) Math.sin(a) * 0.12f, 0.03f, 0.06f, 0.03f);
        }
        b.color(1f, 0.7f, 0.1f).cylinder(0.13f, 0.02f, 10, false);
        return b.build("crown");
    }

    private static MeshData halo() {
        MeshBuilder b = B.clear();
        b.color(1f, 1f, 1f, 1f).ring(0.36f, 0.46f, 18, 0.9f, 0f);
        return b.build("halo");
    }

    private static MeshData sky() {
        MeshBuilder b = B.clear();
        int slices = 16, stacks = 6;
        for (int j = 0; j < stacks; j++) {
            float p0 = (float) (Math.PI * 0.5 * j / stacks), p1 = (float) (Math.PI * 0.5 * (j + 1) / stacks);
            for (int i = 0; i < slices; i++) {
                float t0 = (float) (Math.PI * 2 * i / slices), t1 = (float) (Math.PI * 2 * (i + 1) / slices);
                float y0 = (float) Math.sin(p0), y1 = (float) Math.sin(p1), r0 = (float) Math.cos(p0), r1 = (float) Math.cos(p1);
                float k = j / (float) stacks;
                b.color(0.72f - 0.3f * k, 0.85f - 0.25f * k, 0.98f - 0.08f * k);
                b.quad((float) Math.cos(t0) * r0 * 90, y0 * 60 - 8, (float) Math.sin(t0) * r0 * 90,
                        (float) Math.cos(t0) * r1 * 90, y1 * 60 - 8, (float) Math.sin(t0) * r1 * 90,
                        (float) Math.cos(t1) * r1 * 90, y1 * 60 - 8, (float) Math.sin(t1) * r1 * 90,
                        (float) Math.cos(t1) * r0 * 90, y0 * 60 - 8, (float) Math.sin(t1) * r0 * 90);
            }
        }
        // a couple of flat low-poly clouds
        b.color(1f, 1f, 1f);
        float[][] clouds = {{30, 32, -40}, {-45, 30, -10}, {10, 34, 45}, {-20, 36, 40}, {50, 31, 20}};
        for (float[] c : clouds) b.box(c[0], c[1], c[2], 7f, 1.2f, 4f);
        return b.build("sky");
    }

    private static MeshData quad() {
        MeshBuilder b = B.clear();
        b.color(1f, 1f, 1f, 1f).quad(-0.5f, -0.5f, 0, 0.5f, -0.5f, 0, 0.5f, 0.5f, 0, -0.5f, 0.5f, 0);
        return b.build("ui_quad");
    }

    private static MeshData blob() {
        MeshBuilder b = B.clear();
        b.color(0f, 0f, 0f, 0.4f).ring(0f, 1f, 12, 0.4f, 0f);
        return b.build("blob");
    }

    private static MeshData marker() {
        MeshBuilder b = B.clear();
        b.color(1f, 1f, 1f, 1f).ring(0.22f, 0.3f, 16, 0.9f, 0.9f);
        return b.build("marker");
    }

    private static MeshData spark() {
        MeshBuilder b = B.clear();
        b.color(1f, 1f, 1f, 1f).box(0, 0, 0, 0.02f, 0.02f, 0.02f);
        return b.build("spark");
    }

    private static MeshData unitBox(float r, float g, float bl, float a) {
        MeshBuilder b = B.clear();
        b.color(r, g, bl, a).box(0, 0, 0, 0.5f, 0.5f, 0.5f);
        return b.build("unit_box");
    }

    private static MeshData flag() {
        MeshBuilder b = B.clear();
        b.color(0.5f, 0.5f, 0.5f).box(0, 1.5f, 0, 0.04f, 1.5f, 0.04f);
        b.color(1f, 1f, 1f).box(0.4f, 2.7f, 0, 0.4f, 0.25f, 0.02f);
        return b.build("flag");
    }
}
