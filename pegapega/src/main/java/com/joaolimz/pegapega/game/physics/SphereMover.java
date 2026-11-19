package com.joaolimz.pegapega.game.physics;

import com.joaolimz.pegapega.game.math.Aabb;
import com.joaolimz.pegapega.game.math.MathUtil;
import com.joaolimz.pegapega.game.math.Vec3;

import java.util.ArrayList;

/** Moves a sphere through the static world with sliding, in small sub-steps (no tunneling). */
public final class SphereMover {
    private SphereMover() { }

    /**
     * Moves the sphere center by (dx,dy,dz). Velocity components pointing into touched surfaces are removed
     * from vel (if not null). Returns a bitmask: 1 = touched ground (normal up), 2 = touched wall, 4 = ceiling.
     */
    public static int move(PhysicsWorld w, Vec3 c, float r, float dx, float dy, float dz, Vec3 vel) {
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        int steps = Math.max(1, (int) Math.ceil(len / (r * 0.5f)));
        steps = Math.min(steps, 24);
        int contact = 0;
        for (int s = 0; s < steps; s++) {
            c.x += dx / steps; c.y += dy / steps; c.z += dz / steps;
            contact |= resolve(w, c, r, vel);
        }
        return contact;
    }

    private static final Vec3 n = new Vec3();

    /** Pushes the sphere out of overlapping static boxes. */
    public static int resolve(PhysicsWorld w, Vec3 c, float r, Vec3 vel) {
        int contact = 0;
        for (int iter = 0; iter < 3; iter++) {
            boolean any = false;
            ArrayList<StaticCollider> l = w.gatherColliders(c.x - r, c.y - r, c.z - r, c.x + r, c.y + r, c.z + r, Layers.STATIC);
            for (int i = 0; i < l.size(); i++) {
                Aabb b = l.get(i).box;
                float px = MathUtil.clamp(c.x, b.minX, b.maxX), py = MathUtil.clamp(c.y, b.minY, b.maxY), pz = MathUtil.clamp(c.z, b.minZ, b.maxZ);
                float dx = c.x - px, dy = c.y - py, dz = c.z - pz;
                float d2 = dx * dx + dy * dy + dz * dz;
                if (d2 >= r * r) continue;
                float push;
                if (d2 > 1e-8f) {
                    float d = (float) Math.sqrt(d2);
                    push = r - d;
                    n.set(dx / d, dy / d, dz / d);
                } else {
                    // center inside the box: leave through the closest face
                    float[] pen = {c.x - b.minX, b.maxX - c.x, c.y - b.minY, b.maxY - c.y, c.z - b.minZ, b.maxZ - c.z};
                    int k = 0;
                    for (int j = 1; j < 6; j++) if (pen[j] < pen[k]) k = j;
                    push = pen[k] + r;
                    n.set(k == 0 ? -1 : k == 1 ? 1 : 0, k == 2 ? -1 : k == 3 ? 1 : 0, k == 4 ? -1 : k == 5 ? 1 : 0);
                }
                c.addScaled(n, push);
                any = true;
                if (n.y > 0.6f) contact |= 1; else if (n.y < -0.6f) contact |= 4; else contact |= 2;
                if (vel != null) {
                    float vn = vel.dot(n);
                    if (vn < 0f) vel.addScaled(n, -vn);
                }
            }
            if (!any) break;
        }
        return contact;
    }
}
