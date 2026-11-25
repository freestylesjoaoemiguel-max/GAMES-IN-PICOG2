package com.joaolimz.operationzero.game.ai;

import com.joaolimz.operationzero.game.RenderQueue;
import com.joaolimz.operationzero.game.math.Mat4;
import com.joaolimz.operationzero.game.math.MathUtil;
import com.joaolimz.operationzero.game.math.Quat;
import com.joaolimz.operationzero.game.math.Vec3;
import com.joaolimz.operationzero.game.mesh.Assets;
import com.joaolimz.operationzero.game.physics.CharacterMover;
import com.joaolimz.operationzero.game.physics.Layers;
import com.joaolimz.operationzero.game.physics.PhysicsWorld;
import com.joaolimz.operationzero.game.physics.RayHit;
import com.joaolimz.operationzero.game.world.Level;

import java.util.ArrayList;

/**
 * Enemy AI: finite state machine (IDLE, PATROL, INVESTIGATE, ALERT, CHASE, ATTACK, COVER, SEARCH, DEAD),
 * vision cone + line of sight, hearing, pre-computed cover points, waypoint pathfinding, active-NPC budget,
 * economy updates for distant NPCs, smart spawning out of the player's view and pooled ragdolls.
 */
public final class NpcSystem {
    public interface Listener {
        void onNpcShoot(Npc n, Vec3 muzzle, Vec3 impact, boolean hitPlayer, float damage);
        void onNpcKilled(Npc n, Vec3 at);
        void onNpcAlert(Npc n);
        void onNpcHurt(Npc n);
    }

    public static final int PART_HEAD = 0, PART_TORSO = 1, PART_LEGS = 2;

    private static final class Queued {
        String group, patrol, tag;
        int type;
    }

    private final Level level;
    private final PhysicsWorld world;
    private final NavGraph nav;
    private Listener listener;
    public final Npc[] npcs = new Npc[16];
    public final Ragdoll[] ragdolls = new Ragdoll[8];
    private final ArrayList<Queued> queue = new ArrayList<Queued>();
    public int maxActive = 4;
    public int maxRagdolls = 3;
    public float corpseTime = 8f;
    public float time;
    private float spawnTimer;
    private final MathUtil.Rng rng = new MathUtil.Rng(4242);

    // player info (set each frame by the game)
    public final Vec3 playerEye = new Vec3();
    public final Vec3 playerFeet = new Vec3();
    public final Vec3 playerForward = new Vec3(0, 0, -1);
    public float playerSpeed;
    public boolean playerAlive = true;

    // noise events
    private final Vec3[] noisePos = new Vec3[16];
    private final float[] noiseRadius = new float[16];
    private final boolean[] noiseGun = new boolean[16];
    private int noiseCount;

    // scratch
    private final RayHit hit = new RayHit();
    private final Vec3 t0 = new Vec3(), t1 = new Vec3(), t2 = new Vec3(), eye = new Vec3();
    private final Vec3 up = new Vec3(0, 1, 0);
    private final Quat q = new Quat();
    private final Vec3[] joints = new Vec3[Ragdoll.COUNT];
    private final Vec3 shoulderL = new Vec3(), shoulderR = new Vec3();
    public int activeCount, aliveCount;
    public int killedCount;

    public NpcSystem(Level level) {
        this.level = level;
        this.world = level.physics;
        this.nav = level.nav;
        for (int i = 0; i < npcs.length; i++) npcs[i] = new Npc();
        for (int i = 0; i < ragdolls.length; i++) ragdolls[i] = new Ragdoll();
        for (int i = 0; i < noisePos.length; i++) noisePos[i] = new Vec3();
        for (int i = 0; i < joints.length; i++) joints[i] = new Vec3();
    }

    public void setListener(Listener l) { listener = l; }

    // ======================================================================== spawning
    public void queue(String group, int type, String patrol, String tag) {
        Queued qd = new Queued();
        qd.group = group; qd.type = type; qd.patrol = patrol; qd.tag = tag == null ? "" : tag;
        queue.add(qd);
    }

    public int pending(String tag) {
        int c = 0;
        for (int i = 0; i < queue.size(); i++) if (queue.get(i).tag.equals(tag)) c++;
        return c;
    }

    public int alive(String tag) {
        int c = 0;
        for (Npc n : npcs) if (n.alive && n.tag.equals(tag)) c++;
        return c;
    }

    /** Re-tags every living NPC and sends it after the player (alarm). */
    public void huntPlayer(String tag, Vec3 playerPos) {
        for (Npc n : npcs) {
            if (!n.alive) continue;
            n.tag = tag;
            n.lastKnown.set(playerPos);
            if (!isCombat(n.state)) { setState(n, Npc.State.CHASE); n.hasMoveTarget = false; }
        }
    }

    public void clearAll() {
        queue.clear();
        for (Npc n : npcs) { n.alive = false; n.spawned = false; }
        for (Ragdoll r : ragdolls) r.active = false;
        noiseCount = 0;
    }

    private void processSpawns(float dt) {
        spawnTimer -= dt;
        if (spawnTimer > 0f || queue.isEmpty()) return;
        spawnTimer = 0.5f;
        int alive = 0;
        for (Npc n : npcs) if (n.alive) alive++;
        for (int qi = 0; qi < queue.size() && alive < maxActive; qi++) {
            Queued qd = queue.get(qi);
            SpawnPoint sp = pickSpawn(qd.group);
            if (sp == null) continue;
            Npc n = freeNpc();
            if (n == null) return;
            spawnAt(n, sp, qd);
            queue.remove(qi);
            qi--;
            alive++;
        }
    }

    private Npc freeNpc() {
        for (Npc n : npcs) if (!n.alive) return n;
        return null;
    }

    /** Chooses a spawn point of the group that is in an active sector and out of the player's sight. */
    private SpawnPoint pickSpawn(String group) {
        SpawnPoint best = null;
        float bestScore = -1e9f;
        for (int i = 0; i < level.spawns.size(); i++) {
            SpawnPoint sp = level.spawns.get(i);
            if (!sp.group.equals(group)) continue;
            if (!isSectorActive(sp.sector)) continue;
            if (time - sp.lastUsed < 3f) continue;
            float d = sp.pos.distance(playerFeet);
            if (d < 6f) continue;
            t0.setSub(sp.pos, playerFeet).normalize();
            float facing = t0.x * playerForward.x + t0.z * playerForward.z;
            eye.set(sp.pos.x, sp.pos.y + 1.5f, sp.pos.z);
            boolean visible = facing > 0.35f && world.lineOfSight(playerEye, eye, hit);
            if (visible && sp.door == null) continue;
            float score = -Math.abs(d - 14f) + rng.range(0f, 3f);
            if (score > bestScore) { bestScore = score; best = sp; }
        }
        return best;
    }

    private void spawnAt(Npc n, SpawnPoint sp, Queued qd) {
        n.type = qd.type;
        n.alive = true;
        n.spawned = true;
        n.health = n.maxHealth();
        n.pos.set(sp.pos);
        n.lastPos.set(sp.pos);
        n.yaw = sp.yaw;
        n.vel.set(0, 0, 0);
        n.vy[0] = 0f;
        n.sector = sp.sector;
        n.tag = qd.tag;
        n.patrol = qd.patrol != null ? level.patrol(qd.patrol) : null;
        n.patrolIdx = rng.nextInt(4);
        n.pathLen = 0; n.pathIdx = 0; n.hasMoveTarget = false;
        n.cover = null; n.crouch = 0f; n.peeking = false;
        n.seeTimer = 0f; n.canSee = false; n.suspicion = 0f;
        n.burstLeft = 0; n.fireCooldown = 1f; n.reaction = 0f;
        n.hitFlash = 0f; n.flinch = 0f; n.deadTime = 0f;
        n.lastDamageTime = -100f; n.lastSeenTime = -100f;
        n.retreating = false;
        n.perceptionTimer = rng.range(0f, 0.2f);
        sp.lastUsed = time;
        if (sp.door != null) sp.door.openDoor();
        // enemies spawned during alarms already hunt the player
        if (n.tag.equals("alarm") || n.tag.equals("garage")) {
            n.lastKnown.set(playerFeet);
            setState(n, Npc.State.CHASE);
        } else {
            setState(n, n.patrol != null ? Npc.State.PATROL : Npc.State.IDLE);
        }
    }

    private boolean isSectorActive(int s) {
        return s < 0 || s >= world.sectorActive.length || world.sectorActive[s];
    }

    // ======================================================================== noise
    /** Gunshots, impacts, footsteps... NPCs inside the radius may hear it. */
    public void noise(Vec3 p, float radius, boolean gunshot) {
        if (noiseCount >= noisePos.length) return;
        noisePos[noiseCount].set(p);
        noiseRadius[noiseCount] = radius;
        noiseGun[noiseCount] = gunshot;
        noiseCount++;
    }

    // ======================================================================== update
    public void update(float dt) {
        time += dt;
        processSpawns(dt);
        activeCount = 0;
        aliveCount = 0;
        for (Npc n : npcs) {
            if (!n.alive) continue;
            aliveCount++;
            if (!isSectorActive(n.sector)) continue; // frozen in unloaded sectors
            activeCount++;
            float d = n.pos.distance(playerFeet);
            n.economy = d > 28f;
            float step = dt;
            if (n.economy) {
                n.econAccum += dt;
                if (n.econAccum < 0.25f) continue;
                step = n.econAccum;
                n.econAccum = 0f;
            }
            hearing(n);
            perceive(n, step, d);
            think(n, step, d);
            moveNpc(n, step);
            animate(n, step);
        }
        separate();
        for (Ragdoll r : ragdolls) if (r.active && isSectorActive(r.sector)) r.update(world, dt, corpseTime);
        noiseCount = 0;
    }

    private void setState(Npc n, Npc.State s) {
        if (n.state == s) return;
        n.state = s;
        n.stateTime = 0f;
        if (s != Npc.State.COVER && n.cover != null) { n.cover.occupant = null; n.cover = null; }
        if (s == Npc.State.ALERT) n.reaction = rng.range(0.45f, 0.85f);
        if (s == Npc.State.SEARCH) { n.searchCount = 3; n.searchTimer = 0f; }
        n.peeking = false;
    }

    private void hearing(Npc n) {
        for (int i = 0; i < noiseCount; i++) {
            float d = n.pos.distance(noisePos[i]);
            if (d > noiseRadius[i]) continue;
            boolean combat = isCombat(n.state);
            if (!combat) {
                n.investigate.set(noisePos[i]);
                if (noiseGun[i] && d < noiseRadius[i] * 0.45f) {
                    n.lastKnown.set(noisePos[i]);
                    setState(n, Npc.State.ALERT);
                } else {
                    n.suspicion = Math.min(1f, n.suspicion + 0.5f);
                    setState(n, Npc.State.INVESTIGATE);
                    n.hasMoveTarget = false;
                }
            } else if (!n.canSee && noiseGun[i]) {
                n.lastKnown.set(noisePos[i]);
            }
        }
    }

    private static boolean isCombat(Npc.State s) {
        return s == Npc.State.ALERT || s == Npc.State.CHASE || s == Npc.State.ATTACK || s == Npc.State.COVER;
    }

    private void npcEye(Npc n, Vec3 out) {
        float s = Npc.SCALE[n.type];
        out.set(n.pos.x, n.pos.y + (1.62f - 0.5f * n.crouch) * s, n.pos.z);
    }

    private void perceive(Npc n, float dt, float dist) {
        n.perceptionTimer -= dt;
        if (n.perceptionTimer <= 0f) {
            n.perceptionTimer = n.economy ? 1f : 0.2f;
            n.canSee = false;
            if (playerAlive && dist < Npc.SIGHT[n.type]) {
                npcEye(n, eye);
                t0.setSub(playerEye, eye);
                float fx = -(float) Math.sin(n.yaw), fz = -(float) Math.cos(n.yaw);
                float lxz = (float) Math.sqrt(t0.x * t0.x + t0.z * t0.z);
                float cos = lxz > 1e-4f ? (t0.x * fx + t0.z * fz) / lxz : 1f;
                float fov = isCombat(n.state) ? -0.2f : 0.55f;
                if (cos > fov || dist < 3f) n.canSee = world.lineOfSight(eye, playerEye, hit);
            }
        }
        if (n.canSee) {
            n.lastKnown.set(playerFeet);
            n.lastSeenTime = time;
            if (!isCombat(n.state)) {
                n.seeTimer += dt * (dist < 8f ? 3f : dist < 15f ? 1.6f : 1f);
                if (n.seeTimer > 0.6f) {
                    setState(n, Npc.State.ALERT);
                    alertAllies(n);
                    if (listener != null) listener.onNpcAlert(n);
                }
            }
        } else {
            n.seeTimer = Math.max(0f, n.seeTimer - dt * 0.5f);
        }
    }

    private void alertAllies(Npc src) {
        for (Npc o : npcs) {
            if (o == src || !o.alive || isCombat(o.state)) continue;
            if (o.pos.distance(src.pos) < 16f) {
                o.lastKnown.set(src.lastKnown);
                setState(o, Npc.State.CHASE);
                o.hasMoveTarget = false;
            }
        }
    }

    private void think(Npc n, float dt, float dist) {
        n.stateTime += dt;
        if (n.hitFlash > 0f) n.hitFlash -= dt;
        if (n.flinch > 0f) n.flinch -= dt * 3f;
        if (n.muzzle > 0f) n.muzzle -= dt;
        boolean wantCrouch = false;
        switch (n.state) {
            case IDLE: {
                n.speed = 0f;
                n.yaw += (float) Math.sin(n.stateTime * 0.6f) * 0.3f * dt;
                if (n.patrol != null && n.stateTime > 3f) setState(n, Npc.State.PATROL);
                break;
            }
            case PATROL: {
                if (n.patrol == null) { setState(n, Npc.State.IDLE); break; }
                if (!n.hasMoveTarget) {
                    // short pause at each waypoint, looking around
                    n.searchTimer += dt;
                    if (n.searchTimer < 1.5f) { n.speed = 0f; n.yaw += 0.4f * dt; break; }
                    n.searchTimer = 0f;
                    n.patrolIdx = (n.patrolIdx + 1) % n.patrol.length;
                    destination(n, nav.node(n.patrol[n.patrolIdx]));
                }
                n.speed = 1.4f;
                break;
            }
            case INVESTIGATE: {
                if (!n.hasMoveTarget && n.stateTime < 0.3f) destination(n, n.investigate);
                n.speed = 2.0f;
                if (!n.hasMoveTarget && n.stateTime > 0.3f) {
                    n.speed = 0f;
                    n.yaw += 1.2f * dt;
                    if (n.stateTime > 4f) {
                        if (n.suspicion > 0.6f) { n.lastKnown.set(n.investigate); setState(n, Npc.State.SEARCH); }
                        else setState(n, n.patrol != null ? Npc.State.PATROL : Npc.State.IDLE);
                    }
                }
                break;
            }
            case ALERT: {
                n.speed = 0f;
                faceToward(n, playerFeet, dt, 8f);
                n.reaction -= dt;
                if (n.reaction <= 0f) decideCombat(n, dist);
                break;
            }
            case ATTACK: {
                if (n.canSee) {
                    faceToward(n, playerFeet, dt, 7f);
                    shoot(n, dt, dist);
                    // strafe sideways instead of standing still or running straight
                    n.strafeTimer -= dt;
                    if (n.strafeTimer <= 0f) { n.strafeTimer = rng.range(0.8f, 2f); n.strafeDir = rng.nextFloat() < 0.5f ? -1f : 1f; }
                    float rx = (float) Math.cos(n.yaw), rz = -(float) Math.sin(n.yaw);
                    if (n.type == Npc.HEAVY && dist > 5f) {
                        n.moveTarget.set(playerFeet); n.hasMoveTarget = true; n.pathLen = 0; n.speed = 1.6f;
                    } else {
                        n.moveTarget.set(n.pos.x + rx * n.strafeDir * 1.5f, n.pos.y, n.pos.z + rz * n.strafeDir * 1.5f);
                        n.hasMoveTarget = true; n.pathLen = 0; n.speed = 1.1f;
                    }
                } else {
                    n.speed = 0f;
                    n.hasMoveTarget = false;
                    if (time - n.lastSeenTime > 1.2f) { setState(n, Npc.State.CHASE); break; }
                }
                if (dist > Npc.RANGE[n.type] * 1.1f) { setState(n, Npc.State.CHASE); break; }
                if (n.health < n.maxHealth() * 0.35f && !n.retreating) {
                    if (findCover(n, true)) { n.retreating = true; setState(n, Npc.State.COVER); break; }
                }
                if (n.type != Npc.HEAVY && time - n.lastDamageTime < 0.3f && rng.nextFloat() < 0.5f) {
                    if (findCover(n, false)) { setState(n, Npc.State.COVER); break; }
                }
                if (n.stateTime > 7f && n.type != Npc.HEAVY) {
                    if (findCover(n, false)) setState(n, Npc.State.COVER); else n.stateTime = 0f;
                }
                break;
            }
            case CHASE: {
                n.speed = Npc.RUN[n.type];
                if (n.canSee && dist < Npc.RANGE[n.type] * 0.9f) { decideCombat(n, dist); break; }
                n.repathTimer -= dt;
                if (!n.hasMoveTarget || n.repathTimer <= 0f) {
                    if (n.hasMoveTarget && n.moveTarget.distance(n.lastKnown) < 1f && n.repathTimer > -5f) {
                        n.repathTimer = 2f;
                    } else {
                        destination(n, n.lastKnown);
                        n.repathTimer = 2f;
                    }
                }
                if (!n.hasMoveTarget && n.pos.distance(n.lastKnown) < 1.5f) setState(n, Npc.State.SEARCH);
                if (n.stateTime > 20f) setState(n, Npc.State.SEARCH);
                break;
            }
            case COVER: {
                CoverPoint c = n.cover;
                if (c == null) { setState(n, Npc.State.ATTACK); break; }
                float dc = n.pos.distanceXZ(c.pos);
                if (dc > 0.6f) {
                    n.speed = Npc.RUN[n.type];
                    if (!n.hasMoveTarget) destination(n, c.pos);
                    if (n.canSee && rng.nextFloat() < 0.3f * dt) shoot(n, dt, dist);
                    if (n.stateTime > 8f) { setState(n, Npc.State.ATTACK); }
                    break;
                }
                // in cover: hide / peek cycle
                n.speed = 0f;
                n.hasMoveTarget = false;
                faceToward(n, n.lastKnown, dt, 5f);
                n.peekTimer -= dt;
                if (n.peekTimer <= 0f) {
                    n.peeking = !n.peeking;
                    n.peekTimer = n.peeking ? rng.range(1.2f, 2.2f) : rng.range(1.0f, 2.4f);
                    if (!n.peeking) {
                        // re-evaluate: cover compromised (flanked) or too close -> reposition or attack
                        t0.set(c.pos.x, c.pos.y + 0.9f, c.pos.z);
                        boolean exposed = playerAlive && world.lineOfSight(playerEye, t0, hit) && c.low;
                        t1.setSub(playerFeet, c.pos);
                        t1.y = 0;
                        float l = t1.length();
                        boolean flanked = l > 0.1f && (t1.x * c.normal.x + t1.z * c.normal.z) / l < 0.2f;
                        if (dist < 3f) { setState(n, Npc.State.ATTACK); break; }
                        if (flanked || exposed || n.stateTime > 14f) {
                            if (findCover(n, n.retreating)) { n.stateTime = 0f; n.hasMoveTarget = false; }
                            else { setState(n, Npc.State.ATTACK); break; }
                        }
                    }
                }
                if (c.low) wantCrouch = !n.peeking;
                if (n.peeking) {
                    if (!c.low) {
                        // high cover: lean out sideways
                        float rx = (float) Math.cos(n.yaw), rz = -(float) Math.sin(n.yaw);
                        n.moveTarget.set(c.pos.x + rx * 0.8f, c.pos.y, c.pos.z + rz * 0.8f);
                    }
                    if (n.canSee) shoot(n, dt, dist);
                }
                if (time - n.lastSeenTime > 10f) setState(n, Npc.State.SEARCH);
                break;
            }
            case SEARCH: {
                n.speed = 2.2f;
                if (!n.hasMoveTarget) {
                    n.searchTimer += dt;
                    n.yaw += 1.5f * dt;
                    if (n.searchTimer > 1.6f) {
                        n.searchTimer = 0f;
                        if (n.searchCount-- <= 0) {
                            setState(n, n.patrol != null ? Npc.State.PATROL : Npc.State.IDLE);
                            break;
                        }
                        int start = nav.nearest(n.pos, null, false, null);
                        if (start >= 0) {
                            int[] nb = nav.neighbors(start);
                            if (nb.length > 0) destination(n, nav.node(nb[rng.nextInt(nb.length)]));
                        }
                    }
                }
                if (n.stateTime > 20f) setState(n, n.patrol != null ? Npc.State.PATROL : Npc.State.IDLE);
                break;
            }
            default:
                break;
        }
        n.crouch = MathUtil.approach(n.crouch, wantCrouch ? 1f : 0f, dt * 4f);
    }

    private void decideCombat(Npc n, float dist) {
        if (n.health < n.maxHealth() * 0.35f && !n.retreating && findCover(n, true)) {
            n.retreating = true;
            setState(n, Npc.State.COVER);
            return;
        }
        if (n.type != Npc.HEAVY && dist > 5f && findCover(n, false)) {
            setState(n, Npc.State.COVER);
            return;
        }
        if (n.canSee) setState(n, Npc.State.ATTACK); else setState(n, Npc.State.CHASE);
    }

    /** Picks a free pre-computed cover point that blocks the line from the player. */
    private boolean findCover(Npc n, boolean retreat) {
        CoverPoint best = null;
        float bestScore = -1e9f;
        int tested = 0;
        for (int pass = 0; pass < 2 && best == null; pass++) {
            for (int i = 0; i < level.covers.size(); i++) {
                CoverPoint c = level.covers.get(i);
                if (c.occupant != null && c.occupant != n) continue;
                if (!isSectorActive(c.sector)) continue;
                if (Math.abs(c.pos.y - n.pos.y) > 1.5f) continue;
                float dn = c.pos.distance(n.pos);
                if (dn > (retreat ? 20f : 14f)) continue;
                float dp = c.pos.distance(playerFeet);
                if (dp < 4f) continue;
                if (!retreat && dp > Npc.RANGE[n.type]) continue;
                // obstacle must lie between cover and player
                t1.setSub(playerFeet, c.pos);
                t1.y = 0;
                float l = t1.length();
                if (l < 0.1f) continue;
                float protect = (t1.x * c.normal.x + t1.z * c.normal.z) / l;
                if (protect < 0.5f) continue;
                float score = protect * 4f - dn * 0.4f + (retreat ? dp * 0.5f : -Math.abs(dp - 10f) * 0.2f);
                if (pass == 0 && score <= bestScore) continue;
                // verify with one raycast (cap the number of tests to stay cheap)
                if (tested++ > 5) break;
                t0.set(c.pos.x, c.pos.y + (c.low ? 0.8f : 1.4f), c.pos.z);
                if (world.lineOfSight(playerEye, t0, hit)) continue;
                bestScore = score;
                best = c;
            }
        }
        if (best == null) return false;
        if (n.cover != null) n.cover.occupant = null;
        n.cover = best;
        best.occupant = n;
        n.peekTimer = rng.range(0.8f, 1.6f);
        n.peeking = false;
        destination(n, best.pos);
        return true;
    }

    private void faceToward(Npc n, Vec3 target, float dt, float rate) {
        float dx = target.x - n.pos.x, dz = target.z - n.pos.z;
        if (dx * dx + dz * dz < 1e-4f) return;
        float want = (float) Math.atan2(-dx, -dz);
        float diff = MathUtil.wrapAngle(want - n.yaw);
        n.yaw = MathUtil.wrapAngle(n.yaw + MathUtil.clamp(diff, -rate * dt, rate * dt));
        float dy = playerEye.y - (n.pos.y + 1.4f);
        n.aimPitch = (float) Math.atan2(dy, (float) Math.sqrt(dx * dx + dz * dz));
    }

    private void shoot(Npc n, float dt, float dist) {
        if (!playerAlive) return;
        n.fireCooldown -= dt;
        if (n.fireCooldown > 0f) return;
        // must roughly face the player
        float dx = playerFeet.x - n.pos.x, dz = playerFeet.z - n.pos.z;
        float want = (float) Math.atan2(-dx, -dz);
        if (Math.abs(MathUtil.wrapAngle(want - n.yaw)) > 0.35f) return;
        if (n.burstLeft <= 0) n.burstLeft = Npc.BURST[n.type];
        n.burstLeft--;
        n.fireCooldown = n.burstLeft > 0 ? Npc.FIRE_INTERVAL[n.type] : Npc.FIRE_INTERVAL[n.type] + rng.range(0.7f, 1.4f);
        n.muzzle = 0.06f;
        // muzzle position
        float fx = -(float) Math.sin(n.yaw), fz = -(float) Math.cos(n.yaw);
        float s = Npc.SCALE[n.type];
        t2.set(n.pos.x + fx * 0.55f + (float) Math.cos(n.yaw) * 0.1f, n.pos.y + (1.35f - 0.45f * n.crouch) * s, n.pos.z + fz * 0.55f - (float) Math.sin(n.yaw) * 0.1f);
        float range = Npc.RANGE[n.type];
        float acc = Npc.ACCURACY[n.type] * MathUtil.clamp(1.15f - dist / range * 0.6f, 0.2f, 1f);
        if (playerSpeed > 1f) acc *= 0.7f;
        if (time - n.lastSeenTime > 0.5f) acc *= 0.5f;
        if (n.stateTime < 1f && n.state == Npc.State.ATTACK) acc *= 0.6f;
        float dmg = Npc.DAMAGE[n.type];
        if (n.type == Npc.HEAVY) dmg *= MathUtil.clamp(1.3f - dist / range, 0.3f, 1.2f);
        boolean hitPlayer = rng.nextFloat() < acc && world.lineOfSight(t2, playerEye, hit);
        if (hitPlayer) {
            t0.set(playerEye).add(0, -0.35f, 0);
        } else {
            // miss: aim near the player and find what the bullet hits
            t1.set(playerEye).add(rng.range(-0.7f, 0.7f), rng.range(-0.6f, 0.4f), rng.range(-0.7f, 0.7f)).sub(t2).normalize();
            if (world.raycast(t2, t1, 60f, Layers.BLOCK_SIGHT | Layers.PROP, hit, null)) t0.set(hit.point);
            else t0.set(t2).addScaled(t1, 40f);
        }
        if (listener != null) listener.onNpcShoot(n, t2, t0, hitPlayer, dmg);
        noise(t2, 30f, true);
    }

    // ======================================================================== movement
    private void destination(Npc n, Vec3 target) {
        n.moveTarget.set(target);
        n.hasMoveTarget = true;
        int start = nav.nearest(n.pos, world, true, hit);
        int goal = nav.nearest(target, world, true, hit);
        n.pathLen = (start >= 0 && goal >= 0) ? nav.findPath(start, goal, n.path) : 0;
        n.pathIdx = 0;
        // skip the first node if we are already closer to the second
        if (n.pathLen >= 2) {
            Vec3 a = nav.node(n.path[0]), b = nav.node(n.path[1]);
            if (n.pos.distanceXZ(b) < a.distanceXZ(b)) n.pathIdx = 1;
        }
        n.stuckTimer = 0f;
    }

    private void moveNpc(Npc n, float dt) {
        float dx = 0f, dz = 0f;
        if (n.hasMoveTarget && n.speed > 0f) {
            Vec3 wp = n.pathIdx < n.pathLen ? nav.node(n.path[n.pathIdx]) : n.moveTarget;
            float tx = wp.x - n.pos.x, tz = wp.z - n.pos.z;
            float d = (float) Math.sqrt(tx * tx + tz * tz);
            if (d < 0.45f) {
                if (n.pathIdx < n.pathLen) n.pathIdx++;
                else n.hasMoveTarget = false;
            } else {
                float sp = Math.min(n.speed, d / Math.max(dt, 1e-3f));
                dx = tx / d * sp * dt;
                dz = tz / d * sp * dt;
                boolean combatFacing = n.state == Npc.State.ATTACK || (n.state == Npc.State.COVER && n.canSee);
                if (!combatFacing) {
                    float want = (float) Math.atan2(-tx, -tz);
                    n.yaw = MathUtil.wrapAngle(n.yaw + MathUtil.clamp(MathUtil.wrapAngle(want - n.yaw), -6f * dt, 6f * dt));
                }
            }
        }
        float bx = n.pos.x, bz = n.pos.z;
        CharacterMover.move(world, n.pos, n.vy, dx, dz, 0.3f, 1.7f, dt, Layers.STATIC | Layers.DOOR, 0.5f);
        float moved = (float) Math.sqrt((n.pos.x - bx) * (n.pos.x - bx) + (n.pos.z - bz) * (n.pos.z - bz));
        n.vel.set((n.pos.x - bx) / Math.max(dt, 1e-3f), 0, (n.pos.z - bz) / Math.max(dt, 1e-3f));
        // stuck detection -> repath or give up the target
        if (n.hasMoveTarget && n.speed > 0f && moved < n.speed * dt * 0.2f) {
            n.stuckTimer += dt;
            if (n.stuckTimer > 1.2f) {
                n.stuckTimer = 0f;
                if (n.pathIdx < n.pathLen) n.pathIdx++;
                else n.hasMoveTarget = false;
            }
        } else {
            n.stuckTimer = 0f;
        }
        n.sector = locateSector(n);
        if (n.pos.y < -40f) kill(n, null, null);
    }

    private int locateSector(Npc n) {
        if (n.sector >= 0 && n.sector < level.sectors.size() && level.sectors.get(n.sector).contains(n.pos.x, n.pos.y + 0.5f, n.pos.z)) return n.sector;
        for (int i = 0; i < level.sectors.size(); i++) if (level.sectors.get(i).contains(n.pos.x, n.pos.y + 0.5f, n.pos.z)) return i;
        return n.sector;
    }

    private void separate() {
        for (int i = 0; i < npcs.length; i++) {
            Npc a = npcs[i];
            if (!a.alive) continue;
            for (int j = i + 1; j < npcs.length; j++) {
                Npc b = npcs[j];
                if (!b.alive) continue;
                float dx = b.pos.x - a.pos.x, dz = b.pos.z - a.pos.z;
                float d2 = dx * dx + dz * dz;
                if (d2 < 0.49f && d2 > 1e-6f && Math.abs(a.pos.y - b.pos.y) < 1f) {
                    float d = (float) Math.sqrt(d2);
                    float push = (0.7f - d) * 0.5f;
                    a.pos.x -= dx / d * push; a.pos.z -= dz / d * push;
                    b.pos.x += dx / d * push; b.pos.z += dz / d * push;
                }
            }
            // keep distance from the player body
            float dx = a.pos.x - playerFeet.x, dz = a.pos.z - playerFeet.z;
            float d2 = dx * dx + dz * dz;
            if (d2 < 0.5f && d2 > 1e-6f && Math.abs(a.pos.y - playerFeet.y) < 1f) {
                float d = (float) Math.sqrt(d2);
                a.pos.x += dx / d * (0.71f - d); a.pos.z += dz / d * (0.71f - d);
            }
        }
    }

    private void animate(Npc n, float dt) {
        float sp = n.vel.lengthXZ();
        n.walkPhase += dt * (2.5f + sp * 2.6f) * (sp > 0.1f ? 1f : 0f);
    }

    // ======================================================================== damage
    /** Ray vs NPC hit volumes (head sphere, torso and legs cylinders). Sets hit.owner/part when closer. */
    public boolean raycast(Vec3 o, Vec3 d, float maxDist, RayHit out) {
        boolean any = false;
        for (Npc n : npcs) {
            if (!n.alive || !isSectorActive(n.sector)) continue;
            float s = Npc.SCALE[n.type];
            float c = n.crouch;
            float limit = Math.min(maxDist, out.distance);
            float th = MathUtil.raySphere(o, d, n.pos.x, n.pos.y + (1.62f - 0.5f * c) * s, n.pos.z, 0.15f * s, limit);
            float tt = MathUtil.rayCylinderY(o, d, n.pos.x, n.pos.z, 0.25f * s, n.pos.y + (0.9f - 0.4f * c) * s, n.pos.y + (1.5f - 0.48f * c) * s, limit);
            float tl = MathUtil.rayCylinderY(o, d, n.pos.x, n.pos.z, 0.2f * s, n.pos.y, n.pos.y + (0.9f - 0.4f * c) * s, limit);
            float best = -1f;
            int part = 0;
            if (th >= 0f) { best = th; part = PART_HEAD; }
            if (tt >= 0f && (best < 0f || tt < best)) { best = tt; part = PART_TORSO; }
            if (tl >= 0f && (best < 0f || tl < best)) { best = tl; part = PART_LEGS; }
            if (best >= 0f && best < out.distance) {
                out.distance = best;
                out.point.set(o).addScaled(d, best);
                out.normal.set(d).scale(-1f);
                out.owner = n;
                out.part = part;
                out.layer = Layers.NPC;
                out.collider = null; out.body = null; out.hinge = null;
                any = true;
            }
        }
        return any;
    }

    /** Applies damage. Returns true if the NPC died. */
    public boolean damage(Npc n, float amount, int part, Vec3 point, Vec3 dir) {
        if (!n.alive) return false;
        float mult = part == PART_HEAD ? 2.5f : part == PART_LEGS ? 0.7f : 1f;
        if (n.type == Npc.HEAVY && part == PART_TORSO) mult *= 0.75f; // armored vest
        n.health -= amount * mult;
        n.hitFlash = 0.12f;
        n.flinch = 1f;
        n.lastDamageTime = time;
        n.lastKnown.set(playerFeet);
        n.lastSeenTime = time;
        if (!isCombat(n.state)) {
            setState(n, Npc.State.ALERT);
            n.reaction = 0.3f;
            alertAllies(n);
        }
        if (n.health <= 0f) {
            kill(n, point, dir);
            return true;
        }
        if (listener != null) listener.onNpcHurt(n);
        return false;
    }

    private void kill(Npc n, Vec3 point, Vec3 dir) {
        computeJoints(n, joints);
        n.alive = false;
        n.state = Npc.State.DEAD;
        if (n.cover != null) { n.cover.occupant = null; n.cover = null; }
        killedCount++;
        Ragdoll r = allocRagdoll();
        t0.set(n.vel);
        if (dir != null) t0.addScaled(dir, 2.5f);
        r.start(joints, t0, 1f / 60f, n.type, n.sector);
        if (point != null && dir != null) { t1.set(dir).scale(4f); r.impulse(point, t1, 1f / 60f); }
        if (listener != null) listener.onNpcKilled(n, n.pos);
    }

    private Ragdoll allocRagdoll() {
        Ragdoll oldest = null;
        int activeR = 0;
        for (int i = 0; i < ragdolls.length; i++) {
            Ragdoll r = ragdolls[i];
            if (r.active) {
                activeR++;
                if (oldest == null || r.age > oldest.age) oldest = r;
            }
        }
        if (activeR < maxRagdolls) {
            for (Ragdoll r : ragdolls) if (!r.active) return r;
        }
        return oldest != null ? oldest : ragdolls[0];
    }

    public int activeRagdolls() {
        int c = 0;
        for (Ragdoll r : ragdolls) if (r.active) c++;
        return c;
    }

    /** Applies a bullet impulse to ragdolls hit by a ray (cheap proximity test). */
    public boolean shootRagdolls(Vec3 o, Vec3 d, float maxDist, Vec3 outPoint) {
        for (Ragdoll r : ragdolls) {
            if (!r.active) continue;
            for (int i = 0; i < Ragdoll.COUNT; i++) {
                float t = MathUtil.raySphere(o, d, r.p[i].x, r.p[i].y, r.p[i].z, 0.14f, maxDist);
                if (t >= 0f) {
                    outPoint.set(o).addScaled(d, t);
                    t1.set(d).scale(3f);
                    r.impulse(outPoint, t1, 1f / 60f);
                    return true;
                }
            }
        }
        return false;
    }

    // ======================================================================== animation / rendering
    /** Procedural pose: fills 11 joint positions (same layout as the ragdoll). */
    public void computeJoints(Npc n, Vec3[] j) {
        float s = Npc.SCALE[n.type];
        float c = n.crouch;
        float fx = -(float) Math.sin(n.yaw), fz = -(float) Math.cos(n.yaw);
        float rx = (float) Math.cos(n.yaw), rz = -(float) Math.sin(n.yaw);
        float pelvisH = (0.95f - 0.38f * c) * s;
        float lean = -n.flinch * 0.12f + (isCombat(n.state) ? 0.06f : 0f);
        float px = n.pos.x, py = n.pos.y, pz = n.pos.z;
        j[Ragdoll.PELVIS].set(px, py + pelvisH, pz);
        j[Ragdoll.CHEST].set(px + fx * lean, py + pelvisH + 0.45f * s, pz + fz * lean);
        j[Ragdoll.HEAD].set(px + fx * lean * 1.3f, py + pelvisH + 0.76f * s, pz + fz * lean * 1.3f);
        float sp = n.vel.lengthXZ();
        float stride = MathUtil.clamp(sp / 3f, 0f, 1f) * 0.32f * s;
        for (int side = 0; side < 2; side++) {
            float sg = side == 0 ? -1f : 1f;
            float swing = (float) Math.sin(n.walkPhase + side * Math.PI) * stride;
            float lift = Math.max(0f, (float) Math.cos(n.walkPhase + side * Math.PI)) * stride * 0.35f;
            float hx = px + rx * sg * 0.11f * s, hz = pz + rz * sg * 0.11f * s;
            Vec3 foot = j[side == 0 ? Ragdoll.L_FOOT : Ragdoll.R_FOOT];
            Vec3 knee = j[side == 0 ? Ragdoll.L_KNEE : Ragdoll.R_KNEE];
            foot.set(hx + fx * swing, py + 0.05f + lift, hz + fz * swing);
            float kneeFwd = (0.08f + 0.3f * c) * s;
            knee.set((hx + foot.x) * 0.5f + fx * kneeFwd, (py + pelvisH + foot.y) * 0.5f, (hz + foot.z) * 0.5f + fz * kneeFwd);
        }
        Vec3 chest = j[Ragdoll.CHEST];
        shoulderL.set(chest.x - rx * 0.2f * s, chest.y + 0.12f * s, chest.z - rz * 0.2f * s);
        shoulderR.set(chest.x + rx * 0.2f * s, chest.y + 0.12f * s, chest.z + rz * 0.2f * s);
        boolean aiming = isCombat(n.state) && n.state != Npc.State.CHASE;
        float pitch = aiming ? MathUtil.clamp(n.aimPitch, -0.6f, 0.6f) : 0f;
        float py2 = (float) Math.sin(pitch);
        Vec3 rh = j[Ragdoll.R_HAND], lh = j[Ragdoll.L_HAND];
        if (aiming) {
            rh.set(chest.x + fx * 0.4f * s + rx * 0.06f, chest.y + 0.08f * s + py2 * 0.3f, chest.z + fz * 0.4f * s + rz * 0.06f);
            lh.set(chest.x + fx * 0.62f * s - rx * 0.0f, chest.y + 0.1f * s + py2 * 0.5f, chest.z + fz * 0.62f * s - rz * 0.0f);
        } else {
            rh.set(chest.x + fx * 0.28f * s + rx * 0.16f, chest.y - 0.22f * s, chest.z + fz * 0.28f * s + rz * 0.16f);
            lh.set(chest.x + fx * 0.46f * s - rx * 0.04f, chest.y - 0.12f * s, chest.z + fz * 0.46f * s - rz * 0.04f);
        }
        j[Ragdoll.R_ELBOW].set(shoulderR).lerp(rh, 0.5f).add(0, -0.12f * s, 0).add(rx * 0.08f, 0, rz * 0.08f);
        j[Ragdoll.L_ELBOW].set(shoulderL).lerp(lh, 0.5f).add(0, -0.12f * s, 0).add(-rx * 0.08f, 0, -rz * 0.08f);
    }

    private void limb(RenderQueue rq, int mesh, Vec3 a, Vec3 b, float w, float h, float r, float g, float bl) {
        int i = rq.add(mesh, RenderQueue.LIT, 0, r, g, bl, 1f);
        if (i >= 0) Mat4.setSegment(rq.model[i], a, b, w, h, q, up);
    }

    public void render(RenderQueue rq, Assets a, Vec3 viewer, float drawDistance) {
        for (Npc n : npcs) {
            if (!n.alive || !isSectorActive(n.sector)) continue;
            float d = n.pos.distance(viewer);
            if (d > drawDistance) continue;
            int t = n.type;
            float s = Npc.SCALE[t];
            float tr = 1f, tg = 1f, tb = 1f;
            if (n.hitFlash > 0f) { tr = 2.2f; tg = 0.6f; tb = 0.6f; }
            if (d > 25f) {
                q.setYaw(n.yaw);
                int i = rq.add(a.npcLod[t], RenderQueue.LIT, 0, tr, tg, tb, 1f);
                if (i >= 0) Mat4.setTRS(rq.model[i], n.pos.x, n.pos.y, n.pos.z, q, s, s * (1f - 0.25f * n.crouch), s);
                continue;
            }
            computeJoints(n, joints);
            Vec3 chest = joints[Ragdoll.CHEST], pelvis = joints[Ragdoll.PELVIS], head = joints[Ragdoll.HEAD];
            // head and torso oriented by yaw (+ lean)
            q.setYaw(n.yaw);
            int i = rq.add(a.npcHead[t], RenderQueue.LIT, 0, tr, tg, tb, 1f);
            if (i >= 0) Mat4.setTRS(rq.model[i], head.x, head.y, head.z, q, s, s, s);
            t0.set(chest).lerp(pelvis, 0.35f);
            i = rq.add(a.npcTorso[t], RenderQueue.LIT, 0, tr, tg, tb, 1f);
            if (i >= 0) Mat4.setTRS(rq.model[i], t0.x, t0.y + 0.05f * s, t0.z, q, s, s, s);
            boolean lod1 = d > 12f;
            if (lod1) {
                limb(rq, a.npcLimb[t], shoulderL, joints[Ragdoll.L_HAND], 0.1f * s, 0.1f * s, tr, tg, tb);
                limb(rq, a.npcLimb[t], shoulderR, joints[Ragdoll.R_HAND], 0.1f * s, 0.1f * s, tr, tg, tb);
                t1.set(pelvis.x, pelvis.y, pelvis.z);
                limb(rq, a.npcLimbDark[t], t1, joints[Ragdoll.L_FOOT], 0.15f * s, 0.15f * s, tr, tg, tb);
                limb(rq, a.npcLimbDark[t], t1, joints[Ragdoll.R_FOOT], 0.15f * s, 0.15f * s, tr, tg, tb);
            } else {
                limb(rq, a.npcLimb[t], shoulderL, joints[Ragdoll.L_ELBOW], 0.1f * s, 0.1f * s, tr, tg, tb);
                limb(rq, a.npcLimb[t], joints[Ragdoll.L_ELBOW], joints[Ragdoll.L_HAND], 0.085f * s, 0.085f * s, tr, tg, tb);
                limb(rq, a.npcLimb[t], shoulderR, joints[Ragdoll.R_ELBOW], 0.1f * s, 0.1f * s, tr, tg, tb);
                limb(rq, a.npcLimb[t], joints[Ragdoll.R_ELBOW], joints[Ragdoll.R_HAND], 0.085f * s, 0.085f * s, tr, tg, tb);
                for (int side = 0; side < 2; side++) {
                    float sg = side == 0 ? -1f : 1f;
                    float rx = (float) Math.cos(n.yaw), rz = -(float) Math.sin(n.yaw);
                    t1.set(pelvis.x + rx * sg * 0.11f * s, pelvis.y, pelvis.z + rz * sg * 0.11f * s);
                    Vec3 knee = joints[side == 0 ? Ragdoll.L_KNEE : Ragdoll.R_KNEE];
                    Vec3 foot = joints[side == 0 ? Ragdoll.L_FOOT : Ragdoll.R_FOOT];
                    limb(rq, a.npcLimbDark[t], t1, knee, 0.14f * s, 0.14f * s, tr, tg, tb);
                    limb(rq, a.npcLimbDark[t], knee, foot, 0.12f * s, 0.12f * s, tr, tg, tb);
                }
            }
            // weapon from right hand toward left hand
            Vec3 rh = joints[Ragdoll.R_HAND], lh = joints[Ragdoll.L_HAND];
            t1.setSub(lh, rh);
            q.setLookRotation(t1.x, t1.y, t1.z, 0, 1, 0);
            i = rq.add(a.npcGun, RenderQueue.LIT, 0, 1, 1, 1, 1f);
            if (i >= 0) Mat4.setTRS(rq.model[i], rh.x, rh.y, rh.z, q, s, s, s);
            if (n.muzzle > 0f) {
                t2.set(rh).addScaled(t1.normalize(), 0.42f * s);
                i = rq.add(a.flash, RenderQueue.UNLIT, RenderQueue.F_ALPHA | RenderQueue.F_NO_CULL | RenderQueue.F_NO_FOG, 1, 1, 1, 1);
                if (i >= 0) Mat4.setTRS(rq.model[i], t2, q, 1.4f);
            }
            if (d < 15f) {
                q.identity();
                i = rq.add(a.blob, RenderQueue.UNLIT, RenderQueue.F_ALPHA, 0, 0, 0, 1);
                if (i >= 0) Mat4.setTRS(rq.model[i], n.pos.x, n.pos.y + 0.02f, n.pos.z, q, 0.45f, 1f, 0.45f);
            }
        }
        for (Ragdoll r : ragdolls) {
            if (!r.active || !isSectorActive(r.sector)) continue;
            Vec3 c = r.p[Ragdoll.CHEST];
            if (c.distance(viewer) > drawDistance) continue;
            int t = r.type;
            float s = Npc.SCALE[t];
            Vec3[] p = r.p;
            t0.setSub(p[Ragdoll.HEAD], p[Ragdoll.CHEST]);
            q.setLookRotation(t0.x, t0.y, t0.z, 0, 1, 0);
            // head box: model faces -Z, so orient up along the neck by rotating the look rotation
            int i = rq.add(a.npcHead[t], RenderQueue.LIT, 0, 0.8f, 0.8f, 0.8f, 1f);
            if (i >= 0) Mat4.setTRS(rq.model[i], p[Ragdoll.HEAD], q, s);
            limb(rq, a.npcLimb[t], p[Ragdoll.CHEST], p[Ragdoll.PELVIS], 0.38f * s, 0.24f * s, 0.8f, 0.8f, 0.8f);
            limb(rq, a.npcLimb[t], p[Ragdoll.CHEST], p[Ragdoll.L_ELBOW], 0.1f * s, 0.1f * s, 0.8f, 0.8f, 0.8f);
            limb(rq, a.npcLimb[t], p[Ragdoll.L_ELBOW], p[Ragdoll.L_HAND], 0.085f * s, 0.085f * s, 0.8f, 0.8f, 0.8f);
            limb(rq, a.npcLimb[t], p[Ragdoll.CHEST], p[Ragdoll.R_ELBOW], 0.1f * s, 0.1f * s, 0.8f, 0.8f, 0.8f);
            limb(rq, a.npcLimb[t], p[Ragdoll.R_ELBOW], p[Ragdoll.R_HAND], 0.085f * s, 0.085f * s, 0.8f, 0.8f, 0.8f);
            limb(rq, a.npcLimbDark[t], p[Ragdoll.PELVIS], p[Ragdoll.L_KNEE], 0.14f * s, 0.14f * s, 0.8f, 0.8f, 0.8f);
            limb(rq, a.npcLimbDark[t], p[Ragdoll.L_KNEE], p[Ragdoll.L_FOOT], 0.12f * s, 0.12f * s, 0.8f, 0.8f, 0.8f);
            limb(rq, a.npcLimbDark[t], p[Ragdoll.PELVIS], p[Ragdoll.R_KNEE], 0.14f * s, 0.14f * s, 0.8f, 0.8f, 0.8f);
            limb(rq, a.npcLimbDark[t], p[Ragdoll.R_KNEE], p[Ragdoll.R_FOOT], 0.12f * s, 0.12f * s, 0.8f, 0.8f, 0.8f);
        }
    }

    public Npc nearestAlive(Vec3 p) {
        Npc best = null;
        float bd = Float.MAX_VALUE;
        for (Npc n : npcs) {
            if (!n.alive) continue;
            float d = n.pos.distanceSq(p);
            if (d < bd) { bd = d; best = n; }
        }
        return best;
    }
}
