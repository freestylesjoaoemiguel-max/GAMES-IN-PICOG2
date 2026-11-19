package com.joaolimz.operationzero.game.physics;

import com.joaolimz.operationzero.game.math.Aabb;
import com.joaolimz.operationzero.game.math.MathUtil;
import com.joaolimz.operationzero.game.math.Quat;
import com.joaolimz.operationzero.game.math.Vec3;

import java.util.ArrayList;

/**
 * Lightweight physics tuned for mobile VR: static boxes in a uniform XZ grid, a handful of
 * simplified rigid bodies with sleeping, an active body budget and velocity limits.
 */
public final class PhysicsWorld {
    public static final float GRAVITY = 9.81f;
    public static final float MAX_SPEED = 20f;
    private static final float CELL = 4f;

    public final ArrayList<StaticCollider> statics = new ArrayList<StaticCollider>();
    public final ArrayList<StaticCollider> movables = new ArrayList<StaticCollider>();
    public final ArrayList<HingePanel> hinges = new ArrayList<HingePanel>();
    public final ArrayList<RigidBody> bodies = new ArrayList<RigidBody>();

    /** Index by sector id; bodies in inactive sectors are frozen. */
    public boolean[] sectorActive = new boolean[0];
    public int maxActiveBodies = 12;
    public int activeBodyCount;

    private ArrayList<StaticCollider>[] cells;
    private float gridMinX, gridMinZ;
    private int cols, rows;
    private int stamp = 1;

    private final Aabb tmpBox = new Aabb();
    private final Aabb tmpBox2 = new Aabb();
    private final Vec3 tmpN = new Vec3();
    private final Quat tmpQ = new Quat();
    private final Quat tmpQ2 = new Quat();
    private final Vec3 tmpV = new Vec3();
    private RigidBody[] sortBuf = new RigidBody[32];
    private float[] sortDist = new float[32];

    public StaticCollider addStatic(float minX, float minY, float minZ, float maxX, float maxY, float maxZ, int sector) {
        StaticCollider c = new StaticCollider(minX, minY, minZ, maxX, maxY, maxZ, sector, false);
        statics.add(c);
        return c;
    }

    public StaticCollider addMovable(float minX, float minY, float minZ, float maxX, float maxY, float maxZ, int sector) {
        StaticCollider c = new StaticCollider(minX, minY, minZ, maxX, maxY, maxZ, sector, true);
        movables.add(c);
        return c;
    }

    public void addHinge(HingePanel h) { hinges.add(h); }

    public void addBody(RigidBody b) { bodies.add(b); }

    public void removeBody(RigidBody b) { bodies.remove(b); }

    /** Builds the spatial grid. Call after all static colliders were added. */
    @SuppressWarnings("unchecked")
    public void buildGrid() {
        float minX = Float.MAX_VALUE, minZ = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        for (int i = 0; i < statics.size(); i++) {
            Aabb b = statics.get(i).box;
            minX = Math.min(minX, b.minX); minZ = Math.min(minZ, b.minZ);
            maxX = Math.max(maxX, b.maxX); maxZ = Math.max(maxZ, b.maxZ);
        }
        if (statics.isEmpty()) { minX = minZ = -1; maxX = maxZ = 1; }
        gridMinX = minX - 1; gridMinZ = minZ - 1;
        cols = (int) Math.ceil((maxX - gridMinX + 1) / CELL) + 1;
        rows = (int) Math.ceil((maxZ - gridMinZ + 1) / CELL) + 1;
        cells = new ArrayList[cols * rows];
        for (int i = 0; i < statics.size(); i++) {
            StaticCollider c = statics.get(i);
            int x0 = cellX(c.box.minX), x1 = cellX(c.box.maxX), z0 = cellZ(c.box.minZ), z1 = cellZ(c.box.maxZ);
            for (int z = z0; z <= z1; z++) {
                for (int x = x0; x <= x1; x++) {
                    int k = z * cols + x;
                    if (cells[k] == null) cells[k] = new ArrayList<StaticCollider>(4);
                    cells[k].add(c);
                }
            }
        }
    }

    private int cellX(float x) { return MathUtil.clamp((int) ((x - gridMinX) / CELL), 0, cols - 1); }

    private int cellZ(float z) { return MathUtil.clamp((int) ((z - gridMinZ) / CELL), 0, rows - 1); }

    private int[] queryBuf = new int[64];

    /** Collects grid cells overlapped by an XZ rectangle. Returns count of cell indices written into queryBuf. */
    private int cellsFor(float minX, float minZ, float maxX, float maxZ) {
        int x0 = cellX(minX), x1 = cellX(maxX), z0 = cellZ(minZ), z1 = cellZ(maxZ);
        int n = (x1 - x0 + 1) * (z1 - z0 + 1);
        if (n > queryBuf.length) queryBuf = new int[n * 2];
        int k = 0;
        for (int z = z0; z <= z1; z++) for (int x = x0; x <= x1; x++) queryBuf[k++] = z * cols + x;
        return k;
    }

    private final ArrayList<StaticCollider> gather = new ArrayList<StaticCollider>();

    /** Returns enabled colliders whose box intersects the given region (statics + movables). Shared list! */
    public ArrayList<StaticCollider> gatherColliders(float minX, float minY, float minZ, float maxX, float maxY, float maxZ, int mask) {
        gather.clear();
        if (cells == null) buildGrid();
        stamp++;
        int n = cellsFor(minX, minZ, maxX, maxZ);
        for (int i = 0; i < n; i++) {
            ArrayList<StaticCollider> list = cells[queryBuf[i]];
            if (list == null) continue;
            for (int j = 0, m = list.size(); j < m; j++) {
                StaticCollider c = list.get(j);
                if (c.stamp == stamp || !c.enabled || (c.layer & mask) == 0) continue;
                c.stamp = stamp;
                Aabb b = c.box;
                if (b.maxX < minX || b.minX > maxX || b.maxY < minY || b.minY > maxY || b.maxZ < minZ || b.minZ > maxZ) continue;
                gather.add(c);
            }
        }
        for (int j = 0, m = movables.size(); j < m; j++) {
            StaticCollider c = movables.get(j);
            if (!c.enabled || (c.layer & mask) == 0) continue;
            Aabb b = c.box;
            if (b.maxX < minX || b.minX > maxX || b.maxY < minY || b.minY > maxY || b.maxZ < minZ || b.minZ > maxZ) continue;
            gather.add(c);
        }
        return gather;
    }

    public boolean overlapsSolid(Aabb box, int mask) {
        ArrayList<StaticCollider> l = gatherColliders(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, mask);
        for (int i = 0; i < l.size(); i++) if (l.get(i).box.intersects(box)) return true;
        return false;
    }

    // ------------------------------------------------------------------------------------------
    // Raycasts
    // ------------------------------------------------------------------------------------------

    /**
     * Raycast against static geometry, movables, hinged doors and rigid bodies.
     * dir must be normalized. Returns true if something was hit within maxDist.
     */
    public boolean raycast(Vec3 o, Vec3 dir, float maxDist, int mask, RayHit hit, RigidBody ignore) {
        hit.clear();
        if (cells == null) buildGrid();
        if ((mask & (Layers.STATIC)) != 0) raycastGrid(o, dir, maxDist, mask, hit);
        float best = hit.distance;
        for (int j = 0, m = movables.size(); j < m; j++) {
            StaticCollider c = movables.get(j);
            if (!c.enabled || (c.layer & mask) == 0) continue;
            float t = c.box.raycast(o.x, o.y, o.z, dir.x, dir.y, dir.z, Math.min(best, maxDist), tmpN);
            if (t >= 0f && t < best) {
                best = t; hit.distance = t; hit.normal.set(tmpN); hit.collider = c; hit.layer = c.layer; hit.owner = c.owner;
                hit.body = null; hit.hinge = null;
            }
        }
        if ((mask & Layers.DOOR) != 0) {
            for (int j = 0, m = hinges.size(); j < m; j++) {
                HingePanel h = hinges.get(j);
                if (!h.enabled) continue;
                float t = rayHinge(h, o, dir, Math.min(best, maxDist), tmpN);
                if (t >= 0f && t < best) {
                    best = t; hit.distance = t; hit.normal.set(tmpN); hit.hinge = h; hit.layer = Layers.DOOR; hit.owner = h.owner;
                    hit.collider = null; hit.body = null;
                }
            }
        }
        for (int j = 0, m = bodies.size(); j < m; j++) {
            RigidBody b = bodies.get(j);
            if (b == ignore || !b.enabled || (b.layer & mask) == 0) continue;
            tmpBox.setCenter(b.pos.x, b.pos.y, b.pos.z, b.hx, b.hy, b.hz);
            float t = tmpBox.raycast(o.x, o.y, o.z, dir.x, dir.y, dir.z, Math.min(best, maxDist), tmpN);
            if (t >= 0f && t < best) {
                best = t; hit.distance = t; hit.normal.set(tmpN); hit.body = b; hit.layer = b.layer; hit.owner = b.owner;
                hit.collider = null; hit.hinge = null;
            }
        }
        if (hit.distance <= maxDist && hit.distance < Float.MAX_VALUE) {
            hit.point.set(o).addScaled(dir, hit.distance);
            return true;
        }
        hit.clear();
        return false;
    }

    /** Cheap line-of-sight: true when no sight-blocking geometry lies between a and b. */
    public boolean lineOfSight(Vec3 a, Vec3 b, RayHit scratch) {
        tmpV.setSub(b, a);
        float d = tmpV.length();
        if (d < 1e-4f) return true;
        tmpV.scale(1f / d);
        return !raycast(a, tmpV, d - 0.05f, Layers.BLOCK_SIGHT, scratch, null);
    }

    private void raycastGrid(Vec3 o, Vec3 d, float maxDist, int mask, RayHit hit) {
        stamp++;
        // 2D DDA over the XZ grid
        float x = (o.x - gridMinX) / CELL, z = (o.z - gridMinZ) / CELL;
        int cx = (int) Math.floor(x), cz = (int) Math.floor(z);
        int stepX = d.x > 0 ? 1 : -1, stepZ = d.z > 0 ? 1 : -1;
        float tDeltaX = Math.abs(d.x) < 1e-8f ? Float.MAX_VALUE : Math.abs(CELL / d.x);
        float tDeltaZ = Math.abs(d.z) < 1e-8f ? Float.MAX_VALUE : Math.abs(CELL / d.z);
        float tMaxX = Math.abs(d.x) < 1e-8f ? Float.MAX_VALUE : ((stepX > 0 ? (cx + 1 - x) : (x - cx)) * CELL) / Math.abs(d.x);
        float tMaxZ = Math.abs(d.z) < 1e-8f ? Float.MAX_VALUE : ((stepZ > 0 ? (cz + 1 - z) : (z - cz)) * CELL) / Math.abs(d.z);
        float best = Float.MAX_VALUE;
        float tCell = 0f;
        int guard = 0;
        while (tCell <= maxDist && guard++ < 256) {
            if (cx >= 0 && cz >= 0 && cx < cols && cz < rows) {
                ArrayList<StaticCollider> list = cells[cz * cols + cx];
                if (list != null) {
                    for (int j = 0, m = list.size(); j < m; j++) {
                        StaticCollider c = list.get(j);
                        if (c.stamp == stamp || !c.enabled || (c.layer & mask) == 0) continue;
                        c.stamp = stamp;
                        float t = c.box.raycast(o.x, o.y, o.z, d.x, d.y, d.z, Math.min(best, maxDist), tmpN);
                        if (t >= 0f && t < best) {
                            best = t;
                            hit.distance = t; hit.normal.set(tmpN); hit.collider = c; hit.layer = c.layer; hit.owner = c.owner;
                        }
                    }
                }
            } else if ((stepX > 0 && cx >= cols) || (stepX < 0 && cx < 0) || (stepZ > 0 && cz >= rows) || (stepZ < 0 && cz < 0)) {
                // left the grid moving away: but a ray starting outside could enter; only stop when moving away on an axis already outside
                if ((cx >= cols && stepX > 0) || (cx < 0 && stepX < 0) || (cz >= rows && stepZ > 0) || (cz < 0 && stepZ < 0)) break;
            }
            float next = Math.min(tMaxX, tMaxZ);
            if (best <= next) break; // nearest hit lies within the current cell span
            tCell = next;
            if (tMaxX < tMaxZ) { cx += stepX; tMaxX += tDeltaX; } else { cz += stepZ; tMaxZ += tDeltaZ; }
        }
    }

    /** Ray vs hinged panel (oriented box). Returns distance or -1. */
    public float rayHinge(HingePanel h, Vec3 o, Vec3 d, float maxT, Vec3 normalOut) {
        float dx = h.dirX(), dz = h.dirZ();
        float nx = -dz, nz = dx; // panel normal in XZ
        float ox = o.x - h.hingeX, oz = o.z - h.hingeZ;
        float lox = ox * dx + oz * dz, loz = ox * nx + oz * nz;
        float ldx = d.x * dx + d.z * dz, ldz = d.x * nx + d.z * nz;
        tmpBox2.set(0f, h.y0, -h.halfThickness, h.length, h.y1, h.halfThickness);
        float t = tmpBox2.raycast(lox, o.y, loz, ldx, d.y, ldz, maxT, normalOut);
        if (t >= 0f && normalOut != null) {
            float lx = normalOut.x, ly = normalOut.y, lz = normalOut.z;
            normalOut.set(lx * dx + lz * nx, ly, lx * dz + lz * nz);
        }
        return t;
    }

    // ------------------------------------------------------------------------------------------
    // Character helpers (player + NPC)
    // ------------------------------------------------------------------------------------------

    /**
     * Pushes a vertical circle (XZ) out of colliders overlapping the vertical band [yMin, yMax].
     * Hinged doors receive torque so characters can push doors open.
     */
    public void resolveCircle(Vec3 p, float r, float yMin, float yMax, int mask, float doorPushStrength) {
        for (int iter = 0; iter < 2; iter++) {
            ArrayList<StaticCollider> l = gatherColliders(p.x - r, yMin, p.z - r, p.x + r, yMax, p.z + r, mask);
            for (int i = 0; i < l.size(); i++) {
                Aabb b = l.get(i).box;
                if (b.maxY <= yMin || b.minY >= yMax) continue;
                float cx = MathUtil.clamp(p.x, b.minX, b.maxX), cz = MathUtil.clamp(p.z, b.minZ, b.maxZ);
                float dx = p.x - cx, dz = p.z - cz;
                float d2 = dx * dx + dz * dz;
                if (d2 >= r * r) continue;
                if (d2 > 1e-8f) {
                    float d = (float) Math.sqrt(d2);
                    float push = r - d;
                    p.x += dx / d * push; p.z += dz / d * push;
                } else {
                    // center inside box: push out along smallest axis
                    float l1 = p.x - b.minX + r, r1 = b.maxX - p.x + r, f1 = p.z - b.minZ + r, k1 = b.maxZ - p.z + r;
                    float mn = Math.min(Math.min(l1, r1), Math.min(f1, k1));
                    if (mn == l1) p.x -= l1; else if (mn == r1) p.x += r1; else if (mn == f1) p.z -= f1; else p.z += k1;
                }
            }
            if ((mask & Layers.DOOR) != 0) {
                for (int i = 0; i < hinges.size(); i++) circleVsHinge(hinges.get(i), p, r, yMin, yMax, doorPushStrength);
            }
        }
    }

    /** Circle vs hinge panel; returns penetration applied. */
    private float circleVsHinge(HingePanel h, Vec3 p, float r, float yMin, float yMax, float pushStrength) {
        if (!h.enabled || h.y1 <= yMin || h.y0 >= yMax) return 0f;
        float dx = h.dirX(), dz = h.dirZ();
        float nx = -dz, nz = dx;
        float ox = p.x - h.hingeX, oz = p.z - h.hingeZ;
        float lx = ox * dx + oz * dz, lz = ox * nx + oz * nz;
        float cx = MathUtil.clamp(lx, 0f, h.length), cz = MathUtil.clamp(lz, -h.halfThickness, h.halfThickness);
        float ex = lx - cx, ez = lz - cz;
        float d2 = ex * ex + ez * ez;
        if (d2 >= r * r) return 0f;
        float pen;
        float side = lz >= 0 ? 1f : -1f;
        if (d2 > 1e-8f) {
            float d = (float) Math.sqrt(d2);
            pen = r - d;
            ex /= d; ez /= d;
        } else {
            pen = r + h.halfThickness - Math.abs(lz);
            ex = 0; ez = side;
        }
        float wx = ex * dx + ez * nx, wz = ex * dz + ez * nz;
        p.x += wx * pen; p.z += wz * pen;
        // object on +n side pushes the panel towards -n which increases the angle
        h.torqueAccum += side * pen * Math.max(0.2f, cx) * pushStrength;
        return pen;
    }

    /**
     * Highest walkable surface below {@code maxY} under the point (x,z) sampling a small square.
     * Returns -1000 when there is none.
     */
    public float groundHeight(float x, float z, float halfSize, float maxY, int mask) {
        float best = -1000f;
        ArrayList<StaticCollider> l = gatherColliders(x - halfSize, -1000f, z - halfSize, x + halfSize, maxY, z + halfSize, mask);
        for (int i = 0; i < l.size(); i++) {
            Aabb b = l.get(i).box;
            if (b.maxY > maxY) continue;
            if (b.maxX < x - halfSize || b.minX > x + halfSize || b.maxZ < z - halfSize || b.minZ > z + halfSize) continue;
            if (b.maxY > best) best = b.maxY;
        }
        return best;
    }

    // ------------------------------------------------------------------------------------------
    // Simulation
    // ------------------------------------------------------------------------------------------

    private boolean inActiveSector(int s) { return s < 0 || s >= sectorActive.length || sectorActive[s]; }

    public void step(float dt, Vec3 focus) {
        if (cells == null) buildGrid();
        enforceBudget(focus);
        activeBodyCount = 0;
        for (int i = 0, n = bodies.size(); i < n; i++) {
            RigidBody b = bodies.get(i);
            b.impactFlag = false;
            if (!b.enabled || b.kinematic || b.sleeping || !inActiveSector(b.sector)) continue;
            activeBodyCount++;
            if (b.protectTimer > 0f) b.protectTimer -= dt;
            integrate(b, dt);
        }
        // body vs body
        for (int i = 0, n = bodies.size(); i < n; i++) {
            RigidBody a = bodies.get(i);
            if (!a.enabled || !inActiveSector(a.sector)) continue;
            for (int j = i + 1; j < n; j++) {
                RigidBody b = bodies.get(j);
                if (!b.enabled) continue;
                if ((a.sleeping || a.kinematic) && (b.sleeping || b.kinematic)) continue;
                if ((a.collideMask & b.layer) == 0 && (b.collideMask & a.layer) == 0) continue;
                resolvePair(a, b);
            }
        }
    }

    private void integrate(RigidBody b, float dt) {
        b.vel.y -= GRAVITY * dt;
        b.vel.clampLength(MAX_SPEED);
        float speed = b.vel.length();
        float minHalf = Math.max(0.03f, Math.min(b.hx, Math.min(b.hy, b.hz)));
        int sub = MathUtil.clamp((int) Math.ceil(speed * dt / (minHalf * 1.5f)), 1, 4);
        float sdt = dt / sub;
        boolean wasGrounded = b.grounded;
        b.grounded = false;
        float impact = 0f;
        for (int s = 0; s < sub; s++) {
            b.pos.addScaled(b.vel, sdt);
            impact = Math.max(impact, collideStatic(b, sdt));
        }
        if (impact > 1.5f && !wasGrounded || impact > 3f) {
            b.impactFlag = true;
            b.lastImpactSpeed = impact;
        }
        // rotation
        float w = b.angVel.length();
        if (w > 1e-4f) {
            tmpQ.setAxisAngle(b.angVel.x, b.angVel.y, b.angVel.z, w * dt);
            b.rot.preMul(tmpQ).normalize();
        }
        float angDamp = b.grounded ? 6f : 0.4f;
        b.angVel.scale(1f - MathUtil.clamp(angDamp * dt, 0f, 1f));
        if (b.grounded) {
            // settle toward a resting orientation (keep yaw)
            tmpQ2.setYaw(b.rot.yaw());
            b.rot.slerp(tmpQ2, MathUtil.damp(5f, dt));
            float f = MathUtil.clamp(b.friction * 10f * dt, 0f, 1f);
            b.vel.x *= 1f - f; b.vel.z *= 1f - f;
        }
        b.vel.x *= 1f - 0.05f * dt; b.vel.z *= 1f - 0.05f * dt;
        // sleeping
        if (b.grounded && b.vel.lengthSq() < 0.01f && b.angVel.lengthSq() < 0.05f) {
            b.sleepTimer += dt;
            if (b.sleepTimer > 0.5f) { b.sleeping = true; b.vel.set(0, 0, 0); b.angVel.set(0, 0, 0); }
        } else {
            b.sleepTimer = 0f;
        }
        if (b.pos.y < -60f) { b.enabled = false; }
    }

    /** Returns approximate impact speed along contact normals. */
    private float collideStatic(RigidBody b, float dt) {
        float impact = 0f;
        ArrayList<StaticCollider> l = gatherColliders(b.pos.x - b.hx, b.pos.y - b.hy, b.pos.z - b.hz,
                b.pos.x + b.hx, b.pos.y + b.hy, b.pos.z + b.hz, b.collideMask);
        for (int i = 0; i < l.size(); i++) {
            Aabb c = l.get(i).box;
            float px = Math.min(b.pos.x + b.hx - c.minX, c.maxX - (b.pos.x - b.hx));
            float py = Math.min(b.pos.y + b.hy - c.minY, c.maxY - (b.pos.y - b.hy));
            float pz = Math.min(b.pos.z + b.hz - c.minZ, c.maxZ - (b.pos.z - b.hz));
            if (px <= 0 || py <= 0 || pz <= 0) continue;
            if (py <= px && py <= pz) {
                float dir = b.pos.y > c.centerY() ? 1f : -1f;
                b.pos.y += py * dir;
                if (b.vel.y * dir < 0) {
                    impact = Math.max(impact, Math.abs(b.vel.y));
                    b.vel.y = Math.abs(b.vel.y) < 1.2f ? 0f : -b.vel.y * b.restitution;
                }
                if (dir > 0) b.grounded = true;
            } else if (px <= pz) {
                float dir = b.pos.x > c.centerX() ? 1f : -1f;
                b.pos.x += px * dir;
                if (b.vel.x * dir < 0) { impact = Math.max(impact, Math.abs(b.vel.x)); b.vel.x = -b.vel.x * b.restitution; b.angVel.y += b.vel.z * 2f; }
            } else {
                float dir = b.pos.z > c.centerZ() ? 1f : -1f;
                b.pos.z += pz * dir;
                if (b.vel.z * dir < 0) { impact = Math.max(impact, Math.abs(b.vel.z)); b.vel.z = -b.vel.z * b.restitution; b.angVel.y -= b.vel.x * 2f; }
            }
        }
        // hinged doors: treat body as a circle
        if ((b.collideMask & Layers.DOOR) != 0) {
            float r = b.radiusXZ();
            for (int i = 0; i < hinges.size(); i++) {
                HingePanel h = hinges.get(i);
                float before = b.pos.x + b.pos.z;
                float pen = circleVsHinge(h, b.pos, r, b.pos.y - b.hy, b.pos.y + b.hy, b.mass * 0.5f);
                if (pen > 0f && before != b.pos.x + b.pos.z) {
                    b.vel.x *= 0.5f; b.vel.z *= 0.5f;
                }
            }
        }
        return impact;
    }

    private void resolvePair(RigidBody a, RigidBody b) {
        float px = Math.min(a.pos.x + a.hx - (b.pos.x - b.hx), b.pos.x + b.hx - (a.pos.x - a.hx));
        if (px <= 0) return;
        float py = Math.min(a.pos.y + a.hy - (b.pos.y - b.hy), b.pos.y + b.hy - (a.pos.y - a.hy));
        if (py <= 0) return;
        float pz = Math.min(a.pos.z + a.hz - (b.pos.z - b.hz), b.pos.z + b.hz - (a.pos.z - a.hz));
        if (pz <= 0) return;
        float ia = a.sleeping ? 0f : a.invMass(), ib = b.sleeping ? 0f : b.invMass();
        // a sleeping body hit by a moving one wakes up
        if (a.sleeping && !a.kinematic && (b.kinematic || b.vel.lengthSq() > 0.25f)) { a.wake(); ia = a.invMass(); }
        if (b.sleeping && !b.kinematic && (a.kinematic || a.vel.lengthSq() > 0.25f)) { b.wake(); ib = b.invMass(); }
        float sum = ia + ib;
        if (sum <= 0f) return;
        float ka = ia / sum, kb = ib / sum;
        float rvx = a.vel.x - b.vel.x, rvy = a.vel.y - b.vel.y, rvz = a.vel.z - b.vel.z;
        if (py <= px && py <= pz) {
            float dir = a.pos.y > b.pos.y ? 1f : -1f;
            a.pos.y += py * dir * ka; b.pos.y -= py * dir * kb;
            if (rvy * dir < 0) exchange(a, b, 1, rvy, ka, kb);
            if (dir > 0) a.grounded = true; else b.grounded = true;
        } else if (px <= pz) {
            float dir = a.pos.x > b.pos.x ? 1f : -1f;
            a.pos.x += px * dir * ka; b.pos.x -= px * dir * kb;
            if (rvx * dir < 0) exchange(a, b, 0, rvx, ka, kb);
        } else {
            float dir = a.pos.z > b.pos.z ? 1f : -1f;
            a.pos.z += pz * dir * ka; b.pos.z -= pz * dir * kb;
            if (rvz * dir < 0) exchange(a, b, 2, rvz, ka, kb);
        }
    }

    private void exchange(RigidBody a, RigidBody b, int axis, float rel, float ka, float kb) {
        float e = Math.min(a.restitution, b.restitution);
        float j = -(1f + e) * rel;
        float da = j * ka, db = -j * kb;
        if (axis == 0) { a.vel.x += da; b.vel.x += db; }
        else if (axis == 1) { a.vel.y += da; b.vel.y += db; }
        else { a.vel.z += da; b.vel.z += db; }
        if (Math.abs(rel) > 2f) {
            if (!a.kinematic) { a.impactFlag = true; a.lastImpactSpeed = Math.abs(rel); }
            if (!b.kinematic) { b.impactFlag = true; b.lastImpactSpeed = Math.abs(rel); }
        }
    }

    /** Puts the farthest awake bodies to sleep when the active count exceeds the budget. */
    private void enforceBudget(Vec3 focus) {
        int n = 0;
        for (int i = 0; i < bodies.size(); i++) {
            RigidBody b = bodies.get(i);
            if (!b.enabled || b.kinematic || b.sleeping || !inActiveSector(b.sector)) continue;
            if (n >= sortBuf.length) {
                RigidBody[] nb = new RigidBody[n * 2]; System.arraycopy(sortBuf, 0, nb, 0, n); sortBuf = nb;
                float[] nd = new float[n * 2]; System.arraycopy(sortDist, 0, nd, 0, n); sortDist = nd;
            }
            sortBuf[n] = b;
            sortDist[n] = focus == null ? 0f : b.pos.distanceSq(focus);
            n++;
        }
        if (n <= maxActiveBodies) return;
        // simple selection: repeatedly freeze the farthest eligible body
        int excess = n - maxActiveBodies;
        for (int k = 0; k < excess; k++) {
            int far = -1; float fd = -1f;
            for (int i = 0; i < n; i++) {
                RigidBody b = sortBuf[i];
                if (b == null || b.protectTimer > 0f) continue;
                if (!b.grounded && b.vel.lengthSq() > 1f) continue; // never freeze objects mid-flight
                if (sortDist[i] > fd) { fd = sortDist[i]; far = i; }
            }
            if (far < 0) break;
            RigidBody b = sortBuf[far];
            b.sleeping = true; b.vel.set(0, 0, 0); b.angVel.set(0, 0, 0);
            sortBuf[far] = null;
        }
        for (int i = 0; i < n; i++) sortBuf[i] = null;
    }

    public int awakeBodies() { return activeBodyCount; }
}
