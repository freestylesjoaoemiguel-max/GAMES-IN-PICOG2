package com.joaolimz.pegapega.game.mesh;

import com.joaolimz.pegapega.game.math.Mat4;
import com.joaolimz.pegapega.game.math.Quat;
import com.joaolimz.pegapega.game.math.Vec3;

/** Procedural low-poly mesh builder with a transform stack and current vertex color. */
public final class MeshBuilder {
    private float[] v = new float[MeshData.STRIDE * 256];
    private int[] idx = new int[512];
    private int vCount, iCount;
    private float r = 1, g = 1, b = 1, a = 1;

    private final float[][] stack = new float[16][16];
    private int depth = 0;
    private final float[] tmpM = new float[16];
    private final Vec3 tp = new Vec3(), tn = new Vec3();
    private final Quat tq = new Quat();

    public MeshBuilder() { Mat4.identity(stack[0]); }

    public MeshBuilder color(float r, float g, float b) { this.r = r; this.g = g; this.b = b; this.a = 1f; return this; }

    public MeshBuilder color(float r, float g, float b, float a) { this.r = r; this.g = g; this.b = b; this.a = a; return this; }

    /** Hex RGB like 0x3A4F5C. */
    public MeshBuilder color(int rgb) {
        return color(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f);
    }

    public MeshBuilder push() {
        System.arraycopy(stack[depth], 0, stack[depth + 1], 0, 16);
        depth++;
        return this;
    }

    public MeshBuilder pop() { if (depth > 0) depth--; return this; }

    public MeshBuilder translate(float x, float y, float z) {
        Mat4.identity(tmpM);
        tmpM[12] = x; tmpM[13] = y; tmpM[14] = z;
        Mat4.multiply(stack[depth], stack[depth], tmpM);
        return this;
    }

    public MeshBuilder rotate(float ax, float ay, float az, float radians) {
        tq.setAxisAngle(ax, ay, az, radians);
        Mat4.setTRS(tmpM, 0, 0, 0, tq, 1, 1, 1);
        Mat4.multiply(stack[depth], stack[depth], tmpM);
        return this;
    }

    public MeshBuilder scale(float sx, float sy, float sz) {
        Mat4.identity(tmpM);
        tmpM[0] = sx; tmpM[5] = sy; tmpM[10] = sz;
        Mat4.multiply(stack[depth], stack[depth], tmpM);
        return this;
    }

    public int vertexCount() { return vCount; }

    private void ensure(int addV, int addI) {
        if ((vCount + addV) * MeshData.STRIDE > v.length) {
            float[] n = new float[Math.max(v.length * 2, (vCount + addV) * MeshData.STRIDE)];
            System.arraycopy(v, 0, n, 0, vCount * MeshData.STRIDE);
            v = n;
        }
        if (iCount + addI > idx.length) {
            int[] n = new int[Math.max(idx.length * 2, iCount + addI)];
            System.arraycopy(idx, 0, n, 0, iCount);
            idx = n;
        }
    }

    private int vertex(float x, float y, float z, float nx, float ny, float nz) {
        float[] m = stack[depth];
        Mat4.transformPoint(m, x, y, z, tp);
        Mat4.transformDir(m, nx, ny, nz, tn).normalize();
        int o = vCount * MeshData.STRIDE;
        v[o] = tp.x; v[o + 1] = tp.y; v[o + 2] = tp.z;
        v[o + 3] = tn.x; v[o + 4] = tn.y; v[o + 5] = tn.z;
        v[o + 6] = r; v[o + 7] = g; v[o + 8] = b; v[o + 9] = a;
        return vCount++;
    }

    /** Quad with counter-clockwise winding p0,p1,p2,p3 seen from the front. */
    public MeshBuilder quad(float x0, float y0, float z0, float x1, float y1, float z1,
                            float x2, float y2, float z2, float x3, float y3, float z3) {
        float ux = x1 - x0, uy = y1 - y0, uz = z1 - z0;
        float wx = x3 - x0, wy = y3 - y0, wz = z3 - z0;
        float nx = uy * wz - uz * wy, ny = uz * wx - ux * wz, nz = ux * wy - uy * wx;
        ensure(4, 6);
        int i0 = vertex(x0, y0, z0, nx, ny, nz);
        int i1 = vertex(x1, y1, z1, nx, ny, nz);
        int i2 = vertex(x2, y2, z2, nx, ny, nz);
        int i3 = vertex(x3, y3, z3, nx, ny, nz);
        idx[iCount++] = i0; idx[iCount++] = i1; idx[iCount++] = i2;
        idx[iCount++] = i0; idx[iCount++] = i2; idx[iCount++] = i3;
        return this;
    }

    /** Quad subdivided into a grid so baked per-vertex lighting has enough resolution. */
    public MeshBuilder gridQuad(float x0, float y0, float z0, float x1, float y1, float z1,
                                float x3, float y3, float z3, float maxCell) {
        float ux = x1 - x0, uy = y1 - y0, uz = z1 - z0;
        float wx = x3 - x0, wy = y3 - y0, wz = z3 - z0;
        float lu = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
        float lw = (float) Math.sqrt(wx * wx + wy * wy + wz * wz);
        int nu = Math.max(1, Math.min(24, (int) Math.ceil(lu / maxCell)));
        int nw = Math.max(1, Math.min(24, (int) Math.ceil(lw / maxCell)));
        float nx = uy * wz - uz * wy, ny = uz * wx - ux * wz, nz = ux * wy - uy * wx;
        ensure((nu + 1) * (nw + 1), nu * nw * 6);
        int base = vCount;
        for (int j = 0; j <= nw; j++) {
            float tw = j / (float) nw;
            for (int i = 0; i <= nu; i++) {
                float tu = i / (float) nu;
                vertex(x0 + ux * tu + wx * tw, y0 + uy * tu + wy * tw, z0 + uz * tu + wz * tw, nx, ny, nz);
            }
        }
        for (int j = 0; j < nw; j++) {
            for (int i = 0; i < nu; i++) {
                int a0 = base + j * (nu + 1) + i;
                int a1 = a0 + 1;
                int a3 = a0 + (nu + 1);
                int a2 = a3 + 1;
                idx[iCount++] = a0; idx[iCount++] = a1; idx[iCount++] = a2;
                idx[iCount++] = a0; idx[iCount++] = a2; idx[iCount++] = a3;
            }
        }
        return this;
    }

    public static final int FACE_PX = 1, FACE_NX = 2, FACE_PY = 4, FACE_NY = 8, FACE_PZ = 16, FACE_NZ = 32, FACE_ALL = 63;

    public MeshBuilder box(float cx, float cy, float cz, float hx, float hy, float hz) {
        return boxMinMax(cx - hx, cy - hy, cz - hz, cx + hx, cy + hy, cz + hz, FACE_ALL, 0f);
    }

    /** Box from min/max corners. faces = bitmask, cell > 0 tessellates faces for baking. */
    public MeshBuilder boxMinMax(float x0, float y0, float z0, float x1, float y1, float z1, int faces, float cell) {
        if (cell <= 0f) {
            if ((faces & FACE_PZ) != 0) quad(x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
            if ((faces & FACE_NZ) != 0) quad(x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0);
            if ((faces & FACE_PX) != 0) quad(x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1);
            if ((faces & FACE_NX) != 0) quad(x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
            if ((faces & FACE_PY) != 0) quad(x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0);
            if ((faces & FACE_NY) != 0) quad(x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
        } else {
            if ((faces & FACE_PZ) != 0) gridQuad(x0, y0, z1, x1, y0, z1, x0, y1, z1, cell);
            if ((faces & FACE_NZ) != 0) gridQuad(x1, y0, z0, x0, y0, z0, x1, y1, z0, cell);
            if ((faces & FACE_PX) != 0) gridQuad(x1, y0, z1, x1, y0, z0, x1, y1, z1, cell);
            if ((faces & FACE_NX) != 0) gridQuad(x0, y0, z0, x0, y0, z1, x0, y1, z0, cell);
            if ((faces & FACE_PY) != 0) gridQuad(x0, y1, z1, x1, y1, z1, x0, y1, z0, cell);
            if ((faces & FACE_NY) != 0) gridQuad(x0, y0, z0, x1, y0, z0, x0, y0, z1, cell);
        }
        return this;
    }

    /** Cylinder along Y centered at origin of current transform. */
    public MeshBuilder cylinder(float radius, float halfHeight, int segments, boolean caps) {
        ensure(segments * 4 + 2 * (segments + 1), segments * 12);
        for (int i = 0; i < segments; i++) {
            double a0 = i * Math.PI * 2 / segments, a1 = (i + 1) * Math.PI * 2 / segments;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0);
            float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            float cm = (float) Math.cos((a0 + a1) * 0.5), sm = (float) Math.sin((a0 + a1) * 0.5);
            int i0 = vertex(c0 * radius, -halfHeight, s0 * radius, cm, 0, sm);
            int i1 = vertex(c0 * radius, halfHeight, s0 * radius, cm, 0, sm);
            int i2 = vertex(c1 * radius, halfHeight, s1 * radius, cm, 0, sm);
            int i3 = vertex(c1 * radius, -halfHeight, s1 * radius, cm, 0, sm);
            idx[iCount++] = i0; idx[iCount++] = i1; idx[iCount++] = i2;
            idx[iCount++] = i0; idx[iCount++] = i2; idx[iCount++] = i3;
        }
        if (caps) {
            for (int side = 0; side < 2; side++) {
                float y = side == 0 ? halfHeight : -halfHeight;
                float ny = side == 0 ? 1 : -1;
                ensure(segments + 1, segments * 3);
                int center = vertex(0, y, 0, 0, ny, 0);
                int first = vCount;
                for (int i = 0; i < segments; i++) {
                    double ang = i * Math.PI * 2 / segments;
                    vertex((float) Math.cos(ang) * radius, y, (float) Math.sin(ang) * radius, 0, ny, 0);
                }
                for (int i = 0; i < segments; i++) {
                    int p = first + i, q = first + (i + 1) % segments;
                    idx[iCount++] = center;
                    if (side == 0) { idx[iCount++] = q; idx[iCount++] = p; } else { idx[iCount++] = p; idx[iCount++] = q; }
                }
            }
        }
        return this;
    }

    /** Low-poly sphere (lat/long). */
    public MeshBuilder sphere(float radius, int slices, int stacks) {
        ensure((slices + 1) * (stacks + 1), slices * stacks * 6);
        int base = vCount;
        for (int j = 0; j <= stacks; j++) {
            double phi = Math.PI * j / stacks;
            float sy = (float) Math.cos(phi), sr = (float) Math.sin(phi);
            for (int i = 0; i <= slices; i++) {
                double th = 2 * Math.PI * i / slices;
                float nx = (float) Math.cos(th) * sr, nz = (float) Math.sin(th) * sr;
                vertex(nx * radius, sy * radius, nz * radius, nx, sy, nz);
            }
        }
        for (int j = 0; j < stacks; j++) {
            for (int i = 0; i < slices; i++) {
                int a0 = base + j * (slices + 1) + i, a1 = a0 + 1, a2 = a0 + slices + 1, a3 = a2 + 1;
                idx[iCount++] = a0; idx[iCount++] = a1; idx[iCount++] = a3;
                idx[iCount++] = a0; idx[iCount++] = a3; idx[iCount++] = a2;
            }
        }
        return this;
    }

    /** Flat disc facing +Y (used for blob shadows, markers). */
    public MeshBuilder disc(float radius, int segments) {
        ensure(segments + 1, segments * 3);
        int center = vertex(0, 0, 0, 0, 1, 0);
        int first = vCount;
        for (int i = 0; i < segments; i++) {
            double ang = i * Math.PI * 2 / segments;
            vertex((float) Math.cos(ang) * radius, 0, (float) Math.sin(ang) * radius, 0, 1, 0);
        }
        for (int i = 0; i < segments; i++) {
            idx[iCount++] = center; idx[iCount++] = first + (i + 1) % segments; idx[iCount++] = first + i;
        }
        return this;
    }

    /** Flat ring facing +Y with per-vertex alpha (inner alpha, outer alpha). */
    public MeshBuilder ring(float inner, float outer, int segments, float innerAlpha, float outerAlpha) {
        ensure(segments * 2, segments * 6);
        int first = vCount;
        float saveA = a;
        for (int i = 0; i < segments; i++) {
            double ang = i * Math.PI * 2 / segments;
            float c = (float) Math.cos(ang), s = (float) Math.sin(ang);
            a = innerAlpha; vertex(c * inner, 0, s * inner, 0, 1, 0);
            a = outerAlpha; vertex(c * outer, 0, s * outer, 0, 1, 0);
        }
        a = saveA;
        for (int i = 0; i < segments; i++) {
            int i0 = first + i * 2, i1 = i0 + 1, j0 = first + ((i + 1) % segments) * 2, j1 = j0 + 1;
            idx[iCount++] = i0; idx[iCount++] = j0; idx[iCount++] = j1;
            idx[iCount++] = i0; idx[iCount++] = j1; idx[iCount++] = i1;
        }
        return this;
    }

    /** Applies baked lighting to vertices added since {@code fromVertex}. */
    public void bake(int fromVertex, LightBaker baker) {
        for (int i = fromVertex; i < vCount; i++) {
            int o = i * MeshData.STRIDE;
            float l0 = v[o + 6], l1 = v[o + 7], l2 = v[o + 8];
            baker.shade(v[o], v[o + 1], v[o + 2], v[o + 3], v[o + 4], v[o + 5], tp);
            v[o + 6] = Math.min(1f, l0 * tp.x);
            v[o + 7] = Math.min(1f, l1 * tp.y);
            v[o + 8] = Math.min(1f, l2 * tp.z);
        }
    }

    public MeshData build(String name) {
        float[] vv = new float[vCount * MeshData.STRIDE];
        System.arraycopy(v, 0, vv, 0, vv.length);
        int[] ii = new int[iCount];
        System.arraycopy(idx, 0, ii, 0, iCount);
        return new MeshData(name, vv, vCount, ii, iCount);
    }

    public MeshBuilder clear() {
        vCount = 0; iCount = 0; depth = 0; Mat4.identity(stack[0]);
        r = g = b = a = 1f;
        return this;
    }
}
