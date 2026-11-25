// GORILLA MOVEMENT ("gmotion") as used in Lights Out: Bear Facility's classic mode - standard speed, WITHOUT the game's later speed boost.
// Taken from gmotion_alpha_2.html and kept as it is there (Rapier physics, Gorilla-Tag style):
//   * The head is a dynamic physics ball and the headset is slaved to it.
//   * Each hand has a "follower" that chases the controller but cannot pass through the world (swept ball cast).
//   * While a follower is blocked, how far it stopped short of the hand becomes your velocity (gravity off).
//   * Let go and you are thrown with the average of your last few frames of velocity.
// Differences from the original (all requested for this game):
//   1. the physics timestep follows the frame time (the file steps at a fixed 1/60 s);
//   2. hooks for this game's monster effects and the sprint button (mod.inv / mod.scale / mod.sprint / mod.pull);
//   3. walls are thicker: every triangle of the collision mesh gets a copy pushed WALL_THICKNESS behind it, so a hand can't slip through a thin wall;
//   4. a controller pushed through the floor lifts the player so the hand ends up where the controller is;
//   5. a hand that ends up on the far side of a surface from the head (under the floor, behind a wall) is put back at the head and sweeps out again;
//   6. a hand resting closer to a surface than its own radius is pushed out of it (that is what left it red and unable to leave).
import * as THREE from 'three';
import RAPIER from '@dimforge/rapier3d-compat';

// Configuration (same values as the original)
const VELOCITY_HISTORY_SIZE = 8;
const MAX_JUMP_SPEED = 4.0;
const JUMP_MULTIPLIER = 1.1;
const VELOCITY_LIMIT = 0.4;
const SPRINT_MULT = 1.7;                 // this game: hold the left trigger (only if you pass mod.sprint = true; leave it out for plain gorilla speed)
const WALL_THICKNESS = 0.2;              // this game: how far behind every surface the collision mesh is thickened
const MAX_LIFT = 0.25;                   // this game: most the player is lifted in one frame when a controller is through the floor

// Collision groups
const GROUP_PLAYER_MEM = 0x0001;
const GROUP_ENV_MEM = 0x0002;
const GROUP_PLAYER = (GROUP_PLAYER_MEM << 16) | GROUP_ENV_MEM;
const GROUP_ENV = (GROUP_ENV_MEM << 16) | GROUP_PLAYER_MEM;
const GROUP_HAND_RAY = (GROUP_PLAYER_MEM << 16) | GROUP_ENV_MEM;

export const HEAD_RADIUS = 0.25;
const AXES = [{ x: 1, y: 0, z: 0 }, { x: -1, y: 0, z: 0 }, { x: 0, y: 1, z: 0 }, { x: 0, y: -1, z: 0 }, { x: 0, y: 0, z: 1 }, { x: 0, y: 0, z: -1 }];

export class GMotion {
  constructor() {
    this.ready = false;
    this.world = null;
    this.body = null;
    this.gravity = 5;                                       // the original world gravity is -5
    this.velocityHistory = Array.from({ length: VELOCITY_HISTORY_SIZE }, () => new THREE.Vector3());
    this.velocityIndex = 0;
    this.velocityAverage = new THREE.Vector3();
    this.previousPosition = new THREE.Vector3();
    this.isMoving = false;
    this.fol = [new THREE.Vector3(), new THREE.Vector3()];  // leftFollowerPos, rightFollowerPos
    this.hit = [false, false];                              // isLeftColliding, isRightColliding
    this.pos = new THREE.Vector3();
    this.vel = new THREE.Vector3();
    this.grounded = false;
    this.wrongT = [0, 0];
  }

  async load() {
    if (this.ready) return;
    await RAPIER.init();
    this.ready = true;
  }

  /** The static world: one trimesh from the map's triangles, each also copied WALL_THICKNESS behind itself. */
  setWorld(geometry, thickness = WALL_THICKNESS) {
    if (!this.ready || !geometry) return;
    try { this.world?.free(); } catch { /* already freed */ }
    this.world = new RAPIER.World({ x: 0.0, y: -this.gravity, z: 0.0 });
    const posAttr = geometry.attributes.position;
    const src = posAttr.array instanceof Float32Array ? posAttr.array : Float32Array.from(posAttr.array);
    const idx = geometry.index ? geometry.index.array : null;
    const triCount = idx ? idx.length / 3 : posAttr.count / 3;
    let vertices, indices;
    if (thickness > 0) {
      vertices = new Float32Array(triCount * 6 * 3); indices = new Uint32Array(triCount * 6);
      const a = new THREE.Vector3(), b = new THREE.Vector3(), c = new THREE.Vector3(), n = new THREE.Vector3(), e = new THREE.Vector3();
      for (let t = 0; t < triCount; t++) {
        const i0 = idx ? idx[t * 3] : t * 3, i1 = idx ? idx[t * 3 + 1] : t * 3 + 1, i2 = idx ? idx[t * 3 + 2] : t * 3 + 2;
        a.fromArray(src, i0 * 3); b.fromArray(src, i1 * 3); c.fromArray(src, i2 * 3);
        n.crossVectors(e.subVectors(b, a), n.subVectors(c, a)).normalize();
        if (!Number.isFinite(n.x)) n.set(0, 0, 0);
        const o = t * 18, k = t * 6;
        a.toArray(vertices, o); b.toArray(vertices, o + 3); c.toArray(vertices, o + 6);                       // the surface itself
        a.addScaledVector(n, -thickness).toArray(vertices, o + 9);                                            // the same triangle, pushed behind it
        b.addScaledVector(n, -thickness).toArray(vertices, o + 12);
        c.addScaledVector(n, -thickness).toArray(vertices, o + 15);
        for (let j = 0; j < 6; j++) indices[k + j] = k + j;
      }
    } else {                                                          // huge maps: the surfaces as they are (the extra layer would double a big triangle count)
      vertices = src;
      if (idx) indices = Uint32Array.from(idx);
      else { indices = new Uint32Array(posAttr.count); for (let i = 0; i < indices.length; i++) indices[i] = i; }
    }
    const env = this.world.createRigidBody(RAPIER.RigidBodyDesc.fixed());
    this.world.createCollider(RAPIER.ColliderDesc.trimesh(vertices, indices).setCollisionGroups(GROUP_ENV).setFriction(1.5), env);
    // Player sphere
    const sphereBodyDesc = RAPIER.RigidBodyDesc.dynamic().setTranslation(0, 2, 0).setLinearDamping(0.0).lockRotations().setCanSleep(false);
    this.body = this.world.createRigidBody(sphereBodyDesc);
    const sphereColliderDesc = RAPIER.ColliderDesc.ball(HEAD_RADIUS).setCollisionGroups(GROUP_PLAYER).setFriction(0.0).setRestitution(0.0);
    this.world.createCollider(sphereColliderDesc, this.body);
    this.boxCols = [];
    this.setBoxes(this.lastBoxes || []);
    this.world.step();                                      // difference 5: scene queries (the hand casts) see nothing until the world has stepped once
    this.teleport(0, 2, 0);
  }

  /** Solid boxes (lockers) in the physics world: [{ x0, x1, y0, y1, z0, z1 }]. */
  setBoxes(list) {
    this.lastBoxes = list;
    if (!this.world) return;
    for (const c of this.boxCols || []) { try { this.world.removeCollider(c, true); } catch { /* already gone */ } }
    this.boxCols = [];
    for (const b of list) {
      const d = RAPIER.ColliderDesc.cuboid((b.x1 - b.x0) / 2, (b.y1 - b.y0) / 2, (b.z1 - b.z0) / 2).setTranslation((b.x0 + b.x1) / 2, (b.y0 + b.y1) / 2, (b.z0 + b.z1) / 2).setCollisionGroups(GROUP_ENV).setFriction(1.5);
      this.boxCols.push(this.world.createCollider(d));
    }
  }

  /** Put the head ball at (x, y, z) with no velocity (used when the game moves the player: spawns, warps). */
  teleport(x, y, z) {
    if (!this.body) return;
    this.body.setTranslation({ x, y, z }, true);
    this.body.setLinvel({ x: 0, y: 0, z: 0 }, true);
    this.body.setGravityScale(1, true);
    this.pos.set(x, y, z);
    this.previousPosition.set(x, y, z);
    this.fol[0].set(x, y, z); this.fol[1].set(x, y, z);
    this.hit[0] = this.hit[1] = false; this.wrongT[0] = this.wrongT[1] = 0;
    this.isMoving = false;
    this.velocityAverage.set(0, 0, 0);
    for (const h of this.velocityHistory) h.set(0, 0, 0);
  }

  /**
   * The player has just been handed (back) to this physics body - after a grab, a carry, a spawn. Keep their momentum, and put each hand follower where its controller is: with the
   * followers left at the head, the first frame sweeps each one out from the head to the controller, and any wall or floor in between reads as a huge hand displacement
   * (movement / dt) that flings the player for a moment.
   */
  begin(vel, leftPos, rightPos) {
    if (!this.body) return;
    this.body.setLinvel({ x: vel.x, y: vel.y, z: vel.z }, true);
    this.body.setGravityScale(1, true);
    this.fol[0].copy(leftPos); this.fol[1].copy(rightPos);
    this.hit[0] = this.hit[1] = false; this.wrongT[0] = this.wrongT[1] = 0; this.isMoving = false;
    this.velocityAverage.copy(vel); for (const h of this.velocityHistory) h.copy(vel);          // (letting go again straight away still throws as hard as they were already going)
  }

  /** Follow an external position given as feet x, y, z. */
  sync(px, py, pz) { this.teleport(px, py + HEAD_RADIUS, pz); }

  syncIfFar(px, py, pz) {
    if (!this.body) return false;
    const t = this.body.translation();
    if (Math.hypot(t.x - px, t.y - (py + HEAD_RADIUS), t.z - pz) > 0.9) { this.sync(px, py, pz); return true; }
    return false;
  }

  /**
   * Difference 6: a follower that rests closer to a surface than its own radius makes every later sweep report a hit at distance 0, so the
   * hand stays red and cannot leave. Push it out along the six axes (floor, ceiling, walls, corners).
   */
  _depenetrate(p) {
    const R = 0.03 + 0.002;
    for (const d of AXES) {
      const hit = this.world.castRay(new RAPIER.Ray({ x: p.x, y: p.y, z: p.z }, d), R, true, RAPIER.QueryFilterFlags.EXCLUDE_DYNAMIC, GROUP_HAND_RAY);
      if (hit && hit.toi < R) { p.x -= d.x * (R - hit.toi); p.y -= d.y * (R - hit.toi); p.z -= d.z * (R - hit.toi); }
    }
  }

  /** moveFollower() from the original. */
  _moveFollower(i, targetPos, dt) {
    dt = Math.max(0.001, Math.min(dt, 0.1));
    const followerPos = this.fol[i];
    this._depenetrate(followerPos);
    const start = followerPos.clone();
    const end = targetPos.clone();
    const direction = new THREE.Vector3().subVectors(end, start);
    const dist = direction.length();
    if (dist < 0.0001) { followerPos.copy(end); this._depenetrate(followerPos); return false; }
    direction.normalize();
    const castRadius = 0.03;
    const shape = new RAPIER.Ball(castRadius);
    const hit = this.world.castShape(start, { w: 1.0, x: 0.0, y: 0.0, z: 0.0 }, direction, shape, dist, true, RAPIER.QueryFilterFlags.EXCLUDE_DYNAMIC, GROUP_HAND_RAY);
    if (hit && hit.toi < dist) {
      const moveDist = Math.max(0, hit.toi - 0.002);        // a small buffer keeps the follower outside the wall
      followerPos.copy(start.add(direction.multiplyScalar(moveDist)));
      return true;
    }
    followerPos.copy(end);
    this._depenetrate(followerPos);
    return false;
  }

  /**
   * One frame of the original animate() locomotion. leftTarget / rightTarget are the controllers in world space.
   * mod (this game only): { inv, scale, sprint, pull:{x,z}|null, gravity }.
   */
  update(dt, leftTarget, rightTarget, mod = {}) {
    dt = Math.max(0.001, Math.min(dt, 0.1));
    const { world, body } = this;
    if (mod.gravity != null && mod.gravity !== this.gravity) { this.gravity = mod.gravity; world.gravity = { x: 0, y: -this.gravity, z: 0 }; }
    const wasL = this.hit[0], wasR = this.hit[1];

    // 8. Move followers
    let leftColliding = this._moveFollower(0, leftTarget, dt);
    let rightColliding = this._moveFollower(1, rightTarget, dt);
    this.hit[0] = leftColliding; this.hit[1] = rightColliding;
    // Difference 5: with a surface between the head and a hand for more than a moment, that hand slipped through. Put it back at the head.
    for (let i = 0; i < 2; i++) {
      const f = this.fol[i], dx = f.x - this.pos.x, dy = f.y - this.pos.y, dz = f.z - this.pos.z, len = Math.hypot(dx, dy, dz);
      const bad = len > 0.3 && !!world.castRay(new RAPIER.Ray({ x: this.pos.x, y: this.pos.y, z: this.pos.z }, { x: dx / len, y: dy / len, z: dz / len }), len - 0.06, true, RAPIER.QueryFilterFlags.EXCLUDE_DYNAMIC, GROUP_HAND_RAY);
      this.wrongT[i] = bad ? this.wrongT[i] + dt : 0;
      if (this.wrongT[i] > 0.05) {
        this.wrongT[i] = 0; f.set(this.pos.x, this.pos.y, this.pos.z);
        this._moveFollower(i, i ? rightTarget : leftTarget, dt);
        this.hit[i] = i ? (rightColliding = this.fol[i].distanceTo(i ? rightTarget : leftTarget) > 0.001) : (leftColliding = this.fol[i].distanceTo(leftTarget) > 0.001);
      }
    }

    // 9. Movement from hand displacement
    const leftHandDiff = new THREE.Vector3().subVectors(this.fol[0], leftTarget);
    const rightHandDiff = new THREE.Vector3().subVectors(this.fol[1], rightTarget);
    const movement = new THREE.Vector3();
    if (leftColliding && rightColliding) movement.addVectors(leftHandDiff, rightHandDiff).multiplyScalar(0.5);
    else {
      if (leftColliding) movement.add(leftHandDiff);
      if (rightColliding) movement.add(rightHandDiff);
    }

    // Difference 4: a controller that is through the floor lifts the player straight up by that much, so the hand ends up where the controller is.
    let lift = 0;
    const tg = [leftTarget, rightTarget], coll = [leftColliding, rightColliding];
    for (let i = 0; i < 2; i++) {
      if (!coll[i]) continue;
      const below = this.fol[i].y - tg[i].y;                // how far under the hand's resting place the controller is
      if (below > 0.02 && this.fol[i].y < this.pos.y - 0.05) lift = Math.max(lift, below);       // (a hand on something at head height or above is a wall grab, not a floor)
    }
    if (lift > 0) {
      lift = Math.min(lift, MAX_LIFT);
      const p = body.translation();
      const up = world.castRay(new RAPIER.Ray({ x: p.x, y: p.y, z: p.z }, { x: 0, y: 1, z: 0 }), lift + HEAD_RADIUS + 0.05, true, RAPIER.QueryFilterFlags.EXCLUDE_DYNAMIC, GROUP_HAND_RAY);
      if (up) lift = Math.max(0, Math.min(lift, up.toi - HEAD_RADIUS - 0.05));                  // never into a ceiling
      lift = Math.max(0, lift - 0.012);                                      // stop just short so the hand stays in contact (no bounce)
      if (lift > 0) { body.setTranslation({ x: p.x, y: p.y + lift, z: p.z }, true); if (movement.y > 0) movement.y = 0; }
    }

    let pull = null;
    if (mod.pull) {
      const p = body.translation(), dx = mod.pull.x - p.x, dz = mod.pull.z - p.z, dl = Math.hypot(dx, dz) || 1;
      pull = { x: dx / dl * 2.2, z: dz / dl * 2.2 };
    }

    // 10. Apply to player
    const finalVelocity = new THREE.Vector3();
    if (leftColliding || rightColliding) {
      finalVelocity.copy(movement).divideScalar(dt);
      if (finalVelocity.lengthSq() < 0.02) finalVelocity.set(0, 0, 0);       // deadzone: no shaking while holding still against a surface
      if (mod.inv) { finalVelocity.x = -finalVelocity.x; finalVelocity.z = -finalVelocity.z; }      // The Jester
      finalVelocity.multiplyScalar((mod.scale == null ? 1 : mod.scale) * (mod.sprint ? SPRINT_MULT : 1));
      if (pull) { finalVelocity.x += pull.x; finalVelocity.z += pull.z; }
      this.isMoving = true;
      body.setLinvel({ x: finalVelocity.x, y: finalVelocity.y, z: finalVelocity.z }, true);
      body.setGravityScale(0.0, true);                                       // no gravity while climbing
    } else {
      if (this.isMoving) {                                                   // just released: apply the throw
        const len = this.velocityAverage.length();
        if (len > VELOCITY_LIMIT) {
          const throwVel = this.velocityAverage.clone();
          const cap = MAX_JUMP_SPEED * (mod.sprint ? 1.5 : 1);
          if (len * JUMP_MULTIPLIER > cap) throwVel.normalize().multiplyScalar(cap);
          else throwVel.multiplyScalar(JUMP_MULTIPLIER);
          body.setLinvel({ x: throwVel.x, y: throwVel.y, z: throwVel.z }, true);
        } else body.setLinvel({ x: 0, y: 0, z: 0 }, true);
        this.isMoving = false;
      }
      body.setGravityScale(1.0, true);
      if (pull) { const lv = body.linvel(); body.setLinvel({ x: lv.x + pull.x * dt * 4, y: lv.y, z: lv.z + pull.z * dt * 4 }, true); }
    }

    // 4. Physics step
    body.wakeUp();
    world.timestep = dt;
    world.step();

    const t = body.translation(), lv = body.linvel();
    this.pos.set(t.x, t.y, t.z);
    this.vel.set(lv.x, lv.y, lv.z);

    // 11. Velocity history from the real displacement
    const effectiveVel = new THREE.Vector3().subVectors(this.pos, this.previousPosition).divideScalar(dt);
    this.velocityIndex = (this.velocityIndex + 1) % VELOCITY_HISTORY_SIZE;
    const oldestVel = this.velocityHistory[this.velocityIndex].clone();
    this.velocityAverage.add(effectiveVel.clone().sub(oldestVel).divideScalar(VELOCITY_HISTORY_SIZE));
    this.velocityHistory[this.velocityIndex].copy(effectiveVel);
    this.previousPosition.copy(this.pos);

    // Standing on something? (only used for footsteps and the noise level)
    const ray = new RAPIER.Ray({ x: t.x, y: t.y, z: t.z }, { x: 0, y: -1, z: 0 });
    this.grounded = !!world.castRay(ray, HEAD_RADIUS + 0.12, true, RAPIER.QueryFilterFlags.EXCLUDE_DYNAMIC, GROUP_HAND_RAY);
    return { grounded: this.grounded, left: leftColliding, right: rightColliding, newLeft: leftColliding && !wasL, newRight: rightColliding && !wasR };
  }

  dispose() { try { this.world?.free(); } catch { /* ignore */ } this.world = null; this.body = null; }
}
