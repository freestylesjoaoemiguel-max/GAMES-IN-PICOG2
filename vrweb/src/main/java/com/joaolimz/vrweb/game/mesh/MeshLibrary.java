package com.joaolimz.vrweb.game.mesh;

import java.util.ArrayList;

/**
 * Registry that maps integer ids to CPU meshes. The renderer uploads meshes to the GPU lazily,
 * re-uploads them when their version changes and frees GPU memory for meshes flagged as not resident
 * (sector streaming).
 */
public final class MeshLibrary {
    private final ArrayList<MeshData> meshes = new ArrayList<MeshData>();
    private final ArrayList<Boolean> resident = new ArrayList<Boolean>();
    private final ArrayList<Integer> versions = new ArrayList<Integer>();

    public synchronized int register(MeshData data) {
        meshes.add(data);
        resident.add(Boolean.TRUE);
        versions.add(0);
        return meshes.size() - 1;
    }

    /** Replaces the mesh behind an id (level reload) and bumps its version. */
    public synchronized void replace(int id, MeshData data) {
        meshes.set(id, data);
        versions.set(id, versions.get(id) + 1);
    }

    public synchronized MeshData get(int id) { return id >= 0 && id < meshes.size() ? meshes.get(id) : null; }

    public synchronized int size() { return meshes.size(); }

    public synchronized int version(int id) { return id >= 0 && id < versions.size() ? versions.get(id) : 0; }

    /** Marks whether the GPU copy should be kept. Used by sector streaming. */
    public synchronized void setResident(int id, boolean keep) {
        if (id >= 0 && id < resident.size()) resident.set(id, keep ? Boolean.TRUE : Boolean.FALSE);
    }

    public synchronized boolean isResident(int id) { return id >= 0 && id < resident.size() && resident.get(id).booleanValue(); }
}
