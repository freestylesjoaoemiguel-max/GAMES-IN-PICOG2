package com.joaolimz.operationzero.game.world;

import com.joaolimz.operationzero.game.math.Aabb;

import java.util.ArrayList;

/**
 * Streaming unit of the map. Only the sector containing the player and its neighbours are active
 * (simulated, GPU resident and drawn). Neighbour lists double as a precomputed potentially-visible
 * set, which is the occlusion culling scheme used on the Pico G2.
 */
public final class Sector {
    public final int id;
    public final String name;
    public final ArrayList<Aabb> volumes = new ArrayList<Aabb>();
    public int[] neighbors = new int[0];
    public final ArrayList<Integer> staticMeshes = new ArrayList<Integer>();
    public boolean outdoor;
    public int ambientSound = -1;
    public float ambR = 0.4f, ambG = 0.42f, ambB = 0.46f;
    public float fogR = 0.08f, fogG = 0.09f, fogB = 0.1f;
    public boolean active;
    public boolean resident;
    public int triangles;

    public Sector(int id, String name) { this.id = id; this.name = name; }

    public Sector volume(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        volumes.add(new Aabb(minX, minY, minZ, maxX, maxY, maxZ));
        return this;
    }

    public boolean contains(float x, float y, float z) {
        for (int i = 0; i < volumes.size(); i++) if (volumes.get(i).contains(x, y, z)) return true;
        return false;
    }

    public boolean isNeighbor(int other) {
        for (int n : neighbors) if (n == other) return true;
        return false;
    }
}
