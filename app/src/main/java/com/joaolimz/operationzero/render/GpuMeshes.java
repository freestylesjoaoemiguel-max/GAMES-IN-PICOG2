package com.joaolimz.operationzero.render;

import android.opengl.GLES30;

import com.joaolimz.operationzero.game.mesh.MeshData;
import com.joaolimz.operationzero.game.mesh.MeshLibrary;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * GPU cache of {@link MeshData}: one VAO + VBO + IBO per mesh, uploaded on first use, re-uploaded when the
 * library version changes and released when the mesh is not resident (sector streaming).
 */
final class GpuMeshes {
    private int[] vao = new int[256], vbo = new int[256], ibo = new int[256], count = new int[256], version = new int[256];
    private boolean[] loaded = new boolean[256];
    private final MeshLibrary lib;
    int uploadsThisFrame;
    int residentMeshes;

    GpuMeshes(MeshLibrary lib) { this.lib = lib; }

    private void ensure(int id) {
        if (id < vao.length) return;
        int n = Math.max(id + 1, vao.length * 2);
        vao = grow(vao, n); vbo = grow(vbo, n); ibo = grow(ibo, n); count = grow(count, n); version = grow(version, n);
        boolean[] l = new boolean[n];
        System.arraycopy(loaded, 0, l, 0, loaded.length);
        loaded = l;
    }

    private static int[] grow(int[] a, int n) { int[] r = new int[n]; System.arraycopy(a, 0, r, 0, a.length); return r; }

    /** Binds the VAO of a mesh (uploading if necessary) and returns its index count, or 0. */
    int bind(int id) {
        if (id < 0) return 0;
        ensure(id);
        int ver = lib.version(id);
        if (!loaded[id] || version[id] != ver) {
            if (loaded[id]) release(id);
            MeshData m = lib.get(id);
            if (m == null || m.indexCount == 0) return 0;
            upload(id, m);
            version[id] = ver;
        }
        GLES30.glBindVertexArray(vao[id]);
        return count[id];
    }

    private void upload(int id, MeshData m) {
        int[] h = new int[1];
        GLES30.glGenVertexArrays(1, h, 0);
        vao[id] = h[0];
        GLES30.glBindVertexArray(vao[id]);
        GLES30.glGenBuffers(1, h, 0);
        vbo[id] = h[0];
        FloatBuffer fb = ByteBuffer.allocateDirect(m.vertexCount * MeshData.STRIDE * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        fb.put(m.vertices, 0, m.vertexCount * MeshData.STRIDE).position(0);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo[id]);
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, m.vertexCount * MeshData.STRIDE * 4, fb, GLES30.GL_STATIC_DRAW);
        int stride = MeshData.STRIDE * 4;
        GLES30.glEnableVertexAttribArray(0);
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, stride, 0);
        GLES30.glEnableVertexAttribArray(1);
        GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, stride, 12);
        GLES30.glEnableVertexAttribArray(2);
        GLES30.glVertexAttribPointer(2, 4, GLES30.GL_FLOAT, false, stride, 24);
        GLES30.glGenBuffers(1, h, 0);
        ibo[id] = h[0];
        IntBuffer ib = ByteBuffer.allocateDirect(m.indexCount * 4).order(ByteOrder.nativeOrder()).asIntBuffer();
        ib.put(m.indices, 0, m.indexCount).position(0);
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ibo[id]);
        GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, m.indexCount * 4, ib, GLES30.GL_STATIC_DRAW);
        GLES30.glBindVertexArray(0);
        count[id] = m.indexCount;
        loaded[id] = true;
        uploadsThisFrame++;
    }

    private void release(int id) {
        int[] h = {vao[id]};
        GLES30.glDeleteVertexArrays(1, h, 0);
        h[0] = vbo[id];
        GLES30.glDeleteBuffers(1, h, 0);
        h[0] = ibo[id];
        GLES30.glDeleteBuffers(1, h, 0);
        loaded[id] = false;
    }

    /** Frees GPU memory for meshes the streaming system marked as not resident. */
    void collect() {
        residentMeshes = 0;
        for (int i = 0; i < loaded.length; i++) {
            if (!loaded[i]) continue;
            if (!lib.isResident(i)) release(i); else residentMeshes++;
        }
    }

    /** Called after the GL context was lost: handles are invalid, forget them. */
    void invalidate() {
        for (int i = 0; i < loaded.length; i++) loaded[i] = false;
    }
}
