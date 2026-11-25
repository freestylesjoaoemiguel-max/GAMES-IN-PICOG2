package com.joaolimz.pegapega.game.mesh;

import com.joaolimz.pegapega.game.math.Vec3;

import java.util.ArrayList;

/**
 * Offline-style lighting evaluated once at level build time and stored in vertex colors
 * ("baked lighting"). Zero runtime cost on the GPU.
 */
public final class LightBaker {
    public static final class PointLight {
        public final float x, y, z, radius, r, g, b;
        public PointLight(float x, float y, float z, float radius, float r, float g, float b) {
            this.x = x; this.y = y; this.z = z; this.radius = radius; this.r = r; this.g = g; this.b = b;
        }
    }

    public float ambR = 0.25f, ambG = 0.26f, ambB = 0.3f;
    public boolean sun = false;
    public float sunX = 0.35f, sunY = 0.85f, sunZ = 0.4f, sunR = 0.9f, sunG = 0.82f, sunB = 0.7f;
    /** Floor height used for contact darkening (fake ambient occlusion). */
    public float floorY = 0f;
    public final ArrayList<PointLight> lights = new ArrayList<PointLight>();

    public LightBaker ambient(float r, float g, float b) { ambR = r; ambG = g; ambB = b; return this; }

    public LightBaker addLight(float x, float y, float z, float radius, float r, float g, float b) {
        lights.add(new PointLight(x, y, z, radius, r, g, b));
        return this;
    }

    public void shade(float px, float py, float pz, float nx, float ny, float nz, Vec3 out) {
        float lr = ambR, lg = ambG, lb = ambB;
        // hemisphere: ceilings darker, floors a bit lighter from bounce
        float hemi = 0.85f + 0.15f * ny;
        lr *= hemi; lg *= hemi; lb *= hemi;
        if (sun) {
            float l = (float) Math.sqrt(sunX * sunX + sunY * sunY + sunZ * sunZ);
            float nd = (nx * sunX + ny * sunY + nz * sunZ) / l;
            if (nd > 0f) { lr += sunR * nd; lg += sunG * nd; lb += sunB * nd; }
        }
        for (int i = 0, n = lights.size(); i < n; i++) {
            PointLight L = lights.get(i);
            float dx = L.x - px, dy = L.y - py, dz = L.z - pz;
            float d2 = dx * dx + dy * dy + dz * dz;
            if (d2 > L.radius * L.radius) continue;
            float d = (float) Math.sqrt(d2) + 1e-4f;
            float nd = (nx * dx + ny * dy + nz * dz) / d;
            if (nd <= 0f) continue;
            float att = 1f - d / L.radius;
            att = att * (0.6f + 0.4f * att); // softer than quadratic: brighter rooms on dim mobile panels
            float k = nd * att;
            lr += L.r * k; lg += L.g * k; lb += L.b * k;
        }
        // contact shadow near the floor on vertical surfaces
        float h = py - floorY;
        if (Math.abs(ny) < 0.5f && h >= -0.05f && h < 0.6f) {
            float ao = 0.6f + 0.4f * Math.max(0f, h / 0.6f);
            lr *= ao; lg *= ao; lb *= ao;
        }
        out.set(lr, lg, lb);
    }
}
