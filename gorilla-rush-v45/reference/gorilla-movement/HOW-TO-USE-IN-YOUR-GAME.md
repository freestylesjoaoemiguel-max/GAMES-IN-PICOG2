# How to put Gorilla Movement into your game

This is a step-by-step guide for a **three.js + WebXR** game (it is exactly how Lights Out: Bear Facility does it). You need `gmotion.js`; `vr-example.js` is a working loop you can copy from.

## 0. What you need
- three.js **r160** and **@dimforge/rapier3d-compat 0.11.2** (the versions this was built with; Rapier 0.14+ changed `castShape`, so stay on 0.11 - 0.13).
- A WebXR session with two tracked controllers (hand tracking works too, as long as you can get a world position for each hand).
- Your level as triangles you can merge into **one `THREE.BufferGeometry` in world space**.

### Install
```bash
npm install three@0.160.0 @dimforge/rapier3d-compat@0.11.2
```
No bundler? Use an import map:
```html
<script type="importmap">{ "imports": {
  "three": "./vendor/three.module.js",
  "@dimforge/rapier3d-compat": "./vendor/rapier3d-compat.js"
} }</script>
```
Copy `gmotion.js` into your project.

## 1. Set up the rig ("dolly")
Gorilla movement moves **the rig**, not the camera. Put the camera and both controllers inside one `THREE.Group` and add that to the scene:
```js
const rig = new THREE.Group();
rig.add(camera);
const c0 = renderer.xr.getController(0), c1 = renderer.xr.getController(1);
rig.add(c0, c1);
scene.add(rig);
renderer.xr.enabled = true;
```
(If you already use a dolly for teleport or stick movement, reuse it.)

## 2. Build the collision world
Merge every **solid, static** mesh (floors, walls, props you can push off) into one geometry, with all transforms baked in:
```js
import { mergeGeometries } from 'three/addons/utils/BufferGeometryUtils.js';
const parts = [];
level.traverse((o) => {
  if (!o.isMesh || !o.userData.solid) return;
  const g = o.geometry.clone().applyMatrix4(o.matrixWorld);
  for (const k of Object.keys(g.attributes)) if (k !== 'position') g.deleteAttribute(k);     // mergeGeometries needs every piece to have the same attributes
  parts.push(g.index ? g.toNonIndexed() : g);
});
const worldGeometry = mergeGeometries(parts);
```
- Only the `position` attribute is used. Indexed or non-indexed geometry is fine.
- Don't include things that move (doors, platforms): see step 6.
- The physics world is only for **hands and head**. Keep your own collision for everything else if you have it.

## 3. Create it once
```js
import { GMotion, HEAD_RADIUS } from './gmotion.js';
const gm = new GMotion();
await gm.load();                       // starts Rapier (WASM) - await it
gm.setWorld(worldGeometry);            // for a huge level (150,000+ triangles) use gm.setWorld(worldGeometry, 0)
gm.teleport(spawn.x, spawn.y + HEAD_RADIUS, spawn.z);    // the ball's CENTRE is HEAD_RADIUS (0.25 m) above the floor
```
Load it lazily (`import('./gmotion.js')`) if you like, so a failed download never blocks the game, and keep your normal movement as a fallback until `gm.ready` is true.

## 4. Run it every frame - the order matters
```js
const L = new THREE.Vector3(), R = new THREE.Vector3(), cw = new THREE.Vector3();

function placeRigHead(x, y, z) {                       // put the headset exactly at a point
  cw.copy(camera.position).multiplyScalar(rig.scale.x).applyQuaternion(rig.quaternion);
  rig.position.set(x - cw.x, y - cw.y, z - cw.z);
  rig.updateMatrixWorld(true);
}

renderer.setAnimationLoop((time) => {
  const dt = Math.min(0.05, (time - last) / 1000); last = time;
  if (renderer.xr.isPresenting) {
    placeRigHead(gm.pos.x, gm.pos.y, gm.pos.z);        // 1. headset at the physics ball (controller positions below depend on this)
    c0.getWorldPosition(L); c1.getWorldPosition(R);    // 2. where the controllers are in the world right now
    gm.update(dt, L, R, {});                           // 3. physics: moves the ball from what the hands pushed against
    placeRigHead(gm.pos.x, gm.pos.y, gm.pos.z);        // 4. headset follows the ball for this frame's render
  }
  renderer.render(scene, camera);
});
```
Why twice? Step 1 makes the controller positions you read in step 2 line up with where the body already is; step 4 moves the view to where the body ended up this frame.
`vr-example.js` wraps all of this (plus hand spheres and a rescue) in `createGorillaMovement(...)`.

**Turn off your other locomotion while this is on**: no stick walking, no gravity or collision code moving the same rig. Snap/smooth turning is fine: rotate the rig around the head (`rig.rotation.y`); `placeRigHead` already accounts for `rig.quaternion`.

## 5. What you can read each frame
| Property | Meaning |
|---|---|
| `gm.pos` | the head ball's centre (world). Feet = `gm.pos.y - HEAD_RADIUS` |
| `gm.vel` | its velocity (m/s) |
| `gm.fol[0]`, `gm.fol[1]` | where the left/right hands **really** are (they stop at walls and the floor) |
| `gm.hit[0]`, `gm.hit[1]` | is that hand touching something (pushing right now) |
| `gm.grounded` | standing on something (use for footsteps and noise) |

Show the hands: two small spheres at `gm.fol[i]`, tinted red while `gm.hit[i]` is true, and hide your normal controller models. Rumble the controller on the frame `gm.hit` turns true (`update` returns `{ newLeft, newRight, grounded }` for exactly that).

## 6. When the world changes
- **Level change / new map:** `gm.setWorld(newGeometry); gm.teleport(...)`.
- **A wall is broken or a hole is cut:** rebuild the merged geometry without those triangles and call `gm.setWorld(...)` again (the game waits ~0.4 s after the change, so several edits cause one rebuild).
- **Movable solid blocks (lockers, crates, doors):** `gm.setBoxes([{ x0, x1, y0, y1, z0, z1 }, ...])`. Call it again with the new list when they move or are removed. Boxes survive a later `setWorld`.

## 7. When something else moves the player
Respawn, warp, being carried, a cutscene: keep the physics ball in step with the player.
```js
gm.teleport(x, y, z);                        // jump to a point (ball centre), momentum cleared
gm.sync(feetX, feetY, feetZ);                // same, but given as FEET
gm.syncIfFar(feetX, feetY, feetZ);           // call every frame: re-syncs only if the ball is >0.9 m from where the player should be
gm.begin(velocity, leftHandPos, rightHandPos); // hand the player BACK to gorilla movement with their momentum (call once, the frame control returns)
```
`begin` matters: without it, the hands start at the head and the first frame sweeps them out to the controllers, which can fling the player.

## 8. Optional hooks (the 4th argument of `update`)
`gm.update(dt, L, R, {})` is plain gorilla speed. Add any of these when you need them:
- `gravity: 5` - world gravity (default 5; larger = heavier).
- `sprint: true` - x1.7 push speed (the game bound it to holding the left trigger).
- `scale: 0.5` - multiply the speed (slow effects, freezes: `0` stops pushes).
- `inv: true` - reversed controls. `pull: { x, z }` - a gentle drag towards a point.

## 9. Multiplayer
Send `gm.pos` (or the feet position) as the player's position like any other movement; the physics is local to each player. In Lights Out the host trusts the player's reported position.

## 10. Common problems
| Symptom | Cause / fix |
|---|---|
| You fall through the floor | the floor is missing from `worldGeometry` (only merged geometry is solid), or `setWorld` hasn't run yet |
| You can't move at all | `gm.update` isn't being called (not presenting, or `gm.ready` false), or your other locomotion keeps overwriting the rig position |
| A jerk when you start | you didn't call `gm.begin(...)` / `gm.sync(...)` when handing the player back to it |
| Hands stuck red in a corner | use this version of `gmotion.js` (it pushes the hand out of a surface) - older copies of the original file could stick |
| Slow / hitching on a huge level | `gm.setWorld(geometry, 0)` skips the extra wall copies (halves the triangle count); also merge geometry rather than adding thousands of meshes |
| Walls feel thin / hands slip through | keep the default thickness (0.2 m); don't pass 0 on small levels |
| Controls feel too slow or fast | pass `scale` in the hooks (the game's classic speed is 1) |

## 11. Quick checklist
1. rig with camera + both controllers, XR enabled
2. one merged world geometry, `await gm.load()`, `gm.setWorld(...)`, `gm.teleport(...)`
3. per frame: `placeRigHead` -> read controllers -> `gm.update` -> `placeRigHead`
4. your other locomotion off while it runs
5. `setWorld` / `setBoxes` when the level changes; `begin` / `sync` when something else moves the player
