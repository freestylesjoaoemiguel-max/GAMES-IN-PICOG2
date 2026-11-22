// A small, self-contained WebXR example of how Lights Out: Bear Facility hooks gmotion.js into a three.js scene (taken from its main.js, with the game's own state - monster effects,
// energy drink, VR menu and so on - removed). It shows the whole loop; copy what you need.
//
//   const g = await createGorillaMovement({ renderer, scene, camera, rig, mapGeometry });   // once, after the map is loaded
//   renderer.setAnimationLoop((t) => { g.update(dt); renderer.render(scene, camera); });
//
// rig        - the THREE.Group ("dolly") that holds the camera AND the two controllers; the headset moves inside it, and gorilla movement moves the rig.
// mapGeometry - a THREE.BufferGeometry of the walkable/solid world (all triangles, in world space). Call g.setMap(newGeometry) when the level changes.
import * as THREE from 'three';
import { GMotion, HEAD_RADIUS } from './gmotion.js';

export async function createGorillaMovement({ renderer, scene, camera, rig, mapGeometry, start = new THREE.Vector3(0, 2, 0) }) {
  const gm = new GMotion();
  await gm.load();                                              // starts the Rapier WASM physics engine
  gm.setWorld(mapGeometry);                                     // (pass 0 as the 2nd argument for a very large map to skip the extra wall thickness)
  gm.teleport(start.x, start.y, start.z);

  // Two small spheres that show where each hand really is: they stop at walls and the floor, and go red while a hand is touching something.
  const hands = [0, 1].map(() => {
    const m = new THREE.Mesh(new THREE.SphereGeometry(0.045, 12, 8), new THREE.MeshBasicMaterial({ color: 0xffffff, transparent: true, opacity: 0.85 }));
    m.frustumCulled = false; scene.add(m); return m;
  });
  const controllers = [renderer.xr.getController(0), renderer.xr.getController(1)];       // (they must be children of `rig`)
  const L = new THREE.Vector3(), Rr = new THREE.Vector3(), cw = new THREE.Vector3();

  /** Slave the headset to a point: the rig is placed so the camera sits exactly there (your head is at the centre of the physics ball). */
  function placeRigHead(x, y, z) {
    cw.copy(camera.position).multiplyScalar(rig.scale.x).applyQuaternion(rig.quaternion);
    rig.position.set(x - cw.x, y - cw.y, z - cw.z);
    rig.updateMatrixWorld(true);
  }

  let safe = null, safeT = 0, lostT = 0;
  /** If the ball ever ends up far outside the level (fell through, thrown out), put it back at the last good spot. */
  function rescue(dt, bounds) {
    const p = gm.pos, lost = p.y < bounds.min.y - 3 || p.y > bounds.max.y + 15 || p.x < bounds.min.x - 2 || p.x > bounds.max.x + 2 || p.z < bounds.min.z - 2 || p.z > bounds.max.z + 2;
    if (!lost) { lostT = 0; safeT -= dt; if (safeT <= 0) { safeT = 0.3; safe = p.clone(); } return; }
    lostT += dt;
    if (lostT > 0.3 && safe) { lostT = 0; gm.teleport(safe.x, safe.y + 0.05, safe.z); }
  }

  return {
    gm,
    /** Call once per frame while in VR. dt in seconds. mod is optional: { sprint: true } holds the left-trigger sprint; { gravity } changes the world gravity (default 5). */
    update(dt, mod = {}, bounds = null) {
      if (!renderer.xr.isPresenting) return;
      placeRigHead(gm.pos.x, gm.pos.y, gm.pos.z);               // the controllers' world positions below are relative to this rig position
      controllers[0].getWorldPosition(L); controllers[1].getWorldPosition(Rr);
      gm.update(dt, L, Rr, mod);                                // moves the body from what the hands pushed against
      if (bounds) rescue(dt, bounds);
      placeRigHead(gm.pos.x, gm.pos.y, gm.pos.z);               // and the headset follows the body for this frame's render
      hands[0].position.copy(gm.fol[0]); hands[1].position.copy(gm.fol[1]);
      for (const s of [0, 1]) hands[s].material.color.setHex(gm.hit[s] ? 0xff3a2a : 0xffffff);
    },
    /** The level changed: give the physics a new world (and put the player at the start). */
    setMap(geometry, at = start) { gm.setWorld(geometry); gm.teleport(at.x, at.y, at.z); },
    /** Where the player's feet are (the physics ball's centre is HEAD_RADIUS above the floor you stand on). */
    get feet() { return new THREE.Vector3(gm.pos.x, gm.pos.y - HEAD_RADIUS, gm.pos.z); },
    teleport(x, y, z) { gm.teleport(x, y, z); },
  };
}
