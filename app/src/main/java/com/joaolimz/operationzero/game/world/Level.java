package com.joaolimz.operationzero.game.world;

import com.joaolimz.operationzero.game.ai.CoverPoint;
import com.joaolimz.operationzero.game.ai.NavGraph;
import com.joaolimz.operationzero.game.ai.SpawnPoint;
import com.joaolimz.operationzero.game.math.Vec3;
import com.joaolimz.operationzero.game.physics.PhysicsWorld;

import java.util.ArrayList;

/** Runtime contents of the facility map. */
public final class Level {
    public final PhysicsWorld physics = new PhysicsWorld();
    public final ArrayList<Sector> sectors = new ArrayList<Sector>();
    public final ArrayList<Prop> props = new ArrayList<Prop>();
    public final ArrayList<Door> doors = new ArrayList<Door>();
    public final ArrayList<Interactive> interactives = new ArrayList<Interactive>();
    public final ArrayList<TriggerZone> triggers = new ArrayList<TriggerZone>();
    public final ArrayList<CoverPoint> covers = new ArrayList<CoverPoint>();
    public final ArrayList<SpawnPoint> spawns = new ArrayList<SpawnPoint>();
    public final ArrayList<Sign> signs = new ArrayList<Sign>();
    public final ArrayList<Target> targets = new ArrayList<Target>();
    public final NavGraph nav = new NavGraph();
    /** Patrol routes (node index lists) by name. */
    public final ArrayList<String> patrolNames = new ArrayList<String>();
    public final ArrayList<int[]> patrolRoutes = new ArrayList<int[]>();
    /** Checkpoint spawn per mission index (position + yaw). */
    public final Vec3[] checkpoints = new Vec3[7];
    public final float[] checkpointYaw = new float[7];
    public final Vec3 extraction = new Vec3();

    /** World-space text sign (painted signage). */
    public static final class Sign {
        public final String text;
        public final Vec3 pos = new Vec3();
        public final float yaw, size;
        public final int sector;
        public final int color;
        public Sign(String text, float x, float y, float z, float yaw, float size, int color, int sector) {
            this.text = text; pos.set(x, y, z); this.yaw = yaw; this.size = size; this.color = color; this.sector = sector;
        }
    }

    /** Pop-up training target (training range). */
    public static final class Target {
        public final Vec3 pos = new Vec3();
        public final float yaw;
        public final int sector;
        public float down; // 0 = up, 1 = knocked down
        public float resetTimer;
        public int hits;
        public Target(float x, float y, float z, float yaw, int sector) { pos.set(x, y, z); this.yaw = yaw; this.sector = sector; }
    }

    public Door door(String id) {
        for (int i = 0; i < doors.size(); i++) if (doors.get(i).id.equals(id)) return doors.get(i);
        return null;
    }

    public Interactive interactive(String id) {
        for (int i = 0; i < interactives.size(); i++) if (interactives.get(i).id.equals(id)) return interactives.get(i);
        return null;
    }

    public int[] patrol(String name) {
        int i = patrolNames.indexOf(name);
        return i >= 0 ? patrolRoutes.get(i) : null;
    }

    public void addPatrol(String name, int[] route) { patrolNames.add(name); patrolRoutes.add(route); }
}
