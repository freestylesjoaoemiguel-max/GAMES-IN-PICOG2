package com.joaolimz.operationzero.game.mesh;

/**
 * CPU side mesh. Interleaved vertex layout (10 floats): position xyz, normal xyz, color rgba.
 * Colors of static level geometry already contain baked lighting.
 */
public final class MeshData {
    public static final int STRIDE = 10;

    public final String name;
    public final float[] vertices;
    public final int[] indices;
    public final int vertexCount;
    public final int indexCount;
    /** Bounding sphere in mesh space (used for frustum culling). */
    public final float cx, cy, cz, radius;

    public MeshData(String name, float[] vertices, int vertexCount, int[] indices, int indexCount) {
        this.name = name;
        this.vertices = vertices;
        this.indices = indices;
        this.vertexCount = vertexCount;
        this.indexCount = indexCount;
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        for (int i = 0; i < vertexCount; i++) {
            int o = i * STRIDE;
            float x = vertices[o], y = vertices[o + 1], z = vertices[o + 2];
            if (x < minX) minX = x; if (x > maxX) maxX = x;
            if (y < minY) minY = y; if (y > maxY) maxY = y;
            if (z < minZ) minZ = z; if (z > maxZ) maxZ = z;
        }
        if (vertexCount == 0) { minX = minY = minZ = maxX = maxY = maxZ = 0; }
        cx = (minX + maxX) * 0.5f; cy = (minY + maxY) * 0.5f; cz = (minZ + maxZ) * 0.5f;
        float r = 0f;
        for (int i = 0; i < vertexCount; i++) {
            int o = i * STRIDE;
            float dx = vertices[o] - cx, dy = vertices[o + 1] - cy, dz = vertices[o + 2] - cz;
            float d = dx * dx + dy * dy + dz * dz;
            if (d > r) r = d;
        }
        radius = (float) Math.sqrt(r);
    }

    public int triangleCount() { return indexCount / 3; }
}
