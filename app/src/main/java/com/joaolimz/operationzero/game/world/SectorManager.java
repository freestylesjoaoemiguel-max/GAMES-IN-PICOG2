package com.joaolimz.operationzero.game.world;

import com.joaolimz.operationzero.game.mesh.MeshLibrary;
import com.joaolimz.operationzero.game.physics.PhysicsWorld;

import java.util.ArrayList;

/**
 * Activates the player's sector plus neighbours, flags GPU residency of sector meshes (streaming) and
 * informs physics which sectors simulate.
 */
public final class SectorManager {
    public final ArrayList<Sector> sectors;
    private final MeshLibrary lib;
    public int current = 0;
    public int activeCount;

    public SectorManager(ArrayList<Sector> sectors, MeshLibrary lib) {
        this.sectors = sectors;
        this.lib = lib;
    }

    public int locate(float x, float y, float z, int hint) {
        if (hint >= 0 && hint < sectors.size() && sectors.get(hint).contains(x, y, z)) return hint;
        // check neighbours of hint first, then all
        if (hint >= 0 && hint < sectors.size()) {
            for (int n : sectors.get(hint).neighbors) if (sectors.get(n).contains(x, y, z)) return n;
        }
        for (int i = 0; i < sectors.size(); i++) if (sectors.get(i).contains(x, y, z)) return i;
        return hint;
    }

    /** Updates the active set. Returns true if the current sector changed. */
    public boolean update(float x, float y, float z, PhysicsWorld world) {
        int s = locate(x, y, z, current);
        boolean force = activeCount == 0;
        boolean changed = s != current || force;
        current = s;
        if (!changed) return false;
        if (world.sectorActive.length != sectors.size()) world.sectorActive = new boolean[sectors.size()];
        activeCount = 0;
        for (int i = 0; i < sectors.size(); i++) {
            Sector sec = sectors.get(i);
            boolean act = i == current || sectors.get(current).isNeighbor(i);
            sec.active = act;
            world.sectorActive[i] = act;
            if (act) activeCount++;
            // GPU streaming: keep active sectors resident, release far ones
            if (sec.resident != act || force) {
                sec.resident = act;
                for (int k = 0; k < sec.staticMeshes.size(); k++) lib.setResident(sec.staticMeshes.get(k), act);
            }
        }
        return true;
    }

    public boolean isActive(int sector) {
        return sector < 0 || sector >= sectors.size() || sectors.get(sector).active;
    }

    public Sector currentSector() { return sectors.get(current); }
}
