package com.joaolimz.pegapega.game.physics;

import com.joaolimz.pegapega.game.math.Aabb;
import com.joaolimz.pegapega.game.math.Vec3;

import java.util.ArrayList;

/**
 * Sphere sweep against the static boxes (ray vs. box expanded by the radius). Used by the arm
 * locomotion exactly where the original algorithm uses Physics.SphereCast.
 */
public final class SphereCast {
    private SphereCast() { }

    private static final Aabb grown = new Aabb();
    private static final Vec3 n = new Vec3();

    /**
     * Sweeps a sphere from origin along dir (normalized) up to maxDist.
     * @return distance to the first contact, or -1. hitPoint = contact point on the surface, hitNormal = surface normal.
     */
    public static float cast(PhysicsWorld w, Vec3 origin, Vec3 dir, float maxDist, float radius, Vec3 hitPoint, Vec3 hitNormal) {
        float ex = dir.x * maxDist, ey = dir.y * maxDist, ez = dir.z * maxDist;
        ArrayList<StaticCollider> l = w.gatherColliders(
                Math.min(origin.x, origin.x + ex) - radius, Math.min(origin.y, origin.y + ey) - radius, Math.min(origin.z, origin.z + ez) - radius,
                Math.max(origin.x, origin.x + ex) + radius, Math.max(origin.y, origin.y + ey) + radius, Math.max(origin.z, origin.z + ez) + radius,
                Layers.STATIC);
        float best = -1f;
        for (int i = 0; i < l.size(); i++) {
            Aabb b = l.get(i).box;
            grown.set(b.minX - radius, b.minY - radius, b.minZ - radius, b.maxX + radius, b.maxY + radius, b.maxZ + radius);
            float t;
            if (grown.contains(origin.x, origin.y, origin.z)) {
                // already touching / overlapping: report a zero-distance hit with the closest-face normal
                t = 0f;
                float[] pen = {origin.x - grown.minX, grown.maxX - origin.x, origin.y - grown.minY, grown.maxY - origin.y,
                        origin.z - grown.minZ, grown.maxZ - origin.z};
                int k = 0;
                for (int j = 1; j < 6; j++) if (pen[j] < pen[k]) k = j;
                n.set(k == 0 ? -1 : k == 1 ? 1 : 0, k == 2 ? -1 : k == 3 ? 1 : 0, k == 4 ? -1 : k == 5 ? 1 : 0);
                // only counts if we are moving into the surface
                if (dir.dot(n) >= 0f) continue;
            } else {
                t = grown.raycast(origin.x, origin.y, origin.z, dir.x, dir.y, dir.z, maxDist, n);
                if (t < 0f) continue;
            }
            if (best < 0f || t < best) {
                best = t;
                hitNormal.set(n);
                hitPoint.set(origin).addScaled(dir, t).addScaled(n, -radius);
            }
        }
        return best;
    }
}
