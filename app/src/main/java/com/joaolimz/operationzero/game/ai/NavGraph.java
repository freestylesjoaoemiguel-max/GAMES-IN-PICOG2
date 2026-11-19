package com.joaolimz.operationzero.game.ai;

import com.joaolimz.operationzero.game.math.Vec3;
import com.joaolimz.operationzero.game.physics.Layers;
import com.joaolimz.operationzero.game.physics.PhysicsWorld;
import com.joaolimz.operationzero.game.physics.RayHit;
import com.joaolimz.operationzero.game.world.Door;

import java.util.ArrayList;

/**
 * Waypoint navigation graph. Nodes are hand placed in the level; edges are generated once at load
 * time with clearance raycasts (pre-computation keeps runtime AI cheap). A* with a small binary heap.
 */
public final class NavGraph {
    public final ArrayList<Vec3> nodes = new ArrayList<Vec3>();
    public final ArrayList<Integer> nodeSector = new ArrayList<Integer>();
    private int[][] adj = new int[0][];
    private float[][] cost = new float[0][];
    /** Extra links forced by the level (stairs, door gaps) regardless of raycasts. */
    private final ArrayList<int[]> forced = new ArrayList<int[]>();
    private final ArrayList<Door> forcedDoor = new ArrayList<Door>();
    private Door[][] edgeDoor = new Door[0][];

    // A* scratch
    private float[] g = new float[0], f = new float[0];
    private int[] parent = new int[0], heap = new int[0], state = new int[0];
    private int heapSize;
    private int searchId = 1;

    public int add(float x, float y, float z, int sector) {
        nodes.add(new Vec3(x, y, z));
        nodeSector.add(sector);
        return nodes.size() - 1;
    }

    public void link(int a, int b) { link(a, b, null); }

    /** Forced link that is only traversable while the given door is open (null = always). */
    public void link(int a, int b, Door door) { forced.add(new int[]{a, b}); forcedDoor.add(door); }

    public int size() { return nodes.size(); }

    public Vec3 node(int i) { return nodes.get(i); }

    /** Connects nodes within maxDist that have clear passage (two parallel knee-height rays + chest ray). */
    public void build(PhysicsWorld world, float maxDist) {
        int n = nodes.size();
        ArrayList<ArrayList<Integer>> lists = new ArrayList<ArrayList<Integer>>();
        for (int i = 0; i < n; i++) lists.add(new ArrayList<Integer>());
        RayHit hit = new RayHit();
        Vec3 a = new Vec3(), b = new Vec3(), d = new Vec3(), side = new Vec3();
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                Vec3 p = nodes.get(i), q = nodes.get(j);
                if (Math.abs(p.y - q.y) > 0.6f) continue;
                float dist = p.distance(q);
                if (dist > maxDist) continue;
                d.setSub(q, p);
                d.y = 0;
                float len = d.length();
                if (len < 0.01f) continue;
                d.scale(1f / len);
                side.set(-d.z, 0, d.x).scale(0.3f);
                boolean clear = true;
                for (int k = -1; k <= 1 && clear; k++) {
                    float h = k == 0 ? 1.3f : 0.5f;
                    a.set(p.x + side.x * k, p.y + h, p.z + side.z * k);
                    b.set(q.x + side.x * k, q.y + h, q.z + side.z * k);
                    Vec3 dir = new Vec3().setSub(b, a);
                    float l = dir.length();
                    dir.scale(1f / l);
                    if (world.raycast(a, dir, l, Layers.STATIC, hit, null)) clear = false;
                }
                if (clear) { lists.get(i).add(j); lists.get(j).add(i); }
            }
        }
        for (int[] e : forced) {
            if (!lists.get(e[0]).contains(e[1])) { lists.get(e[0]).add(e[1]); lists.get(e[1]).add(e[0]); }
        }
        adj = new int[n][];
        cost = new float[n][];
        edgeDoor = new Door[n][];
        for (int i = 0; i < n; i++) {
            ArrayList<Integer> l = lists.get(i);
            adj[i] = new int[l.size()];
            cost[i] = new float[l.size()];
            edgeDoor[i] = new Door[l.size()];
            for (int k = 0; k < l.size(); k++) {
                adj[i][k] = l.get(k);
                cost[i][k] = nodes.get(i).distance(nodes.get(l.get(k)));
                for (int e = 0; e < forced.size(); e++) {
                    int[] fe = forced.get(e);
                    if ((fe[0] == i && fe[1] == l.get(k)) || (fe[1] == i && fe[0] == l.get(k))) edgeDoor[i][k] = forcedDoor.get(e);
                }
            }
        }
        g = new float[n]; f = new float[n]; parent = new int[n]; heap = new int[n * 4 + 8]; state = new int[n];
    }

    public int[] neighbors(int i) { return adj[i]; }

    /** Nearest node in XZ with similar height. If requireSight, prefer visible nodes. */
    public int nearest(Vec3 p, PhysicsWorld world, boolean requireSight, RayHit scratch) {
        int best = -1;
        float bd = Float.MAX_VALUE;
        Vec3 eye = new Vec3();
        Vec3 np = new Vec3();
        for (int pass = 0; pass < 2 && best < 0; pass++) {
            for (int i = 0; i < nodes.size(); i++) {
                Vec3 q = nodes.get(i);
                if (Math.abs(q.y - p.y) > 1.5f) continue;
                if (i < adj.length && adj[i].length == 0) continue; // isolated (inside geometry)
                float dx = q.x - p.x, dz = q.z - p.z;
                float d2 = dx * dx + dz * dz;
                if (d2 >= bd) continue;
                if (pass == 0 && requireSight && world != null) {
                    eye.set(p.x, p.y + 1.0f, p.z);
                    np.set(q.x, q.y + 1.0f, q.z);
                    if (!world.lineOfSight(eye, np, scratch)) continue;
                }
                bd = d2; best = i;
            }
            if (!requireSight) break;
        }
        return best;
    }

    /** A* search. Writes the node path (start..goal) into out and returns its length (0 if none). */
    public int findPath(int start, int goal, int[] out) {
        int n = nodes.size();
        if (start < 0 || goal < 0 || start >= n || goal >= n) return 0;
        if (start == goal) { out[0] = start; return 1; }
        searchId += 2;
        int open = searchId, closed = searchId + 1;
        heapSize = 0;
        g[start] = 0; f[start] = h(start, goal); parent[start] = -1; state[start] = open;
        push(start);
        int iterations = 0;
        while (heapSize > 0 && iterations++ < 2000) {
            int cur = pop();
            if (state[cur] == closed) continue;
            if (cur == goal) {
                int len = 0;
                for (int k = cur; k != -1; k = parent[k]) len++;
                if (len > out.length) return 0;
                int k = cur;
                for (int i = len - 1; i >= 0; i--) { out[i] = k; k = parent[k]; }
                return len;
            }
            state[cur] = closed;
            int[] nb = adj[cur];
            for (int k = 0; k < nb.length; k++) {
                int m = nb[k];
                if (state[m] == closed) continue;
                Door door = edgeDoor[cur][k];
                if (door != null && (door.locked || (door.type == Door.SLIDING && !door.isOpen()))) continue;
                float ng = g[cur] + cost[cur][k];
                if (state[m] != open || ng < g[m]) {
                    g[m] = ng; f[m] = ng + h(m, goal); parent[m] = cur; state[m] = open;
                    push(m);
                }
            }
        }
        return 0;
    }

    private float h(int a, int b) { return nodes.get(a).distance(nodes.get(b)); }

    private void push(int node) {
        if (heapSize >= heap.length) { int[] nh = new int[heap.length * 2]; System.arraycopy(heap, 0, nh, 0, heapSize); heap = nh; }
        int i = heapSize++;
        heap[i] = node;
        while (i > 0) {
            int p = (i - 1) >> 1;
            if (f[heap[p]] <= f[heap[i]]) break;
            int t = heap[p]; heap[p] = heap[i]; heap[i] = t; i = p;
        }
    }

    private int pop() {
        int top = heap[0];
        heap[0] = heap[--heapSize];
        int i = 0;
        while (true) {
            int l = i * 2 + 1, r = l + 1, m = i;
            if (l < heapSize && f[heap[l]] < f[heap[m]]) m = l;
            if (r < heapSize && f[heap[r]] < f[heap[m]]) m = r;
            if (m == i) break;
            int t = heap[m]; heap[m] = heap[i]; heap[i] = t; i = m;
        }
        return top;
    }
}
