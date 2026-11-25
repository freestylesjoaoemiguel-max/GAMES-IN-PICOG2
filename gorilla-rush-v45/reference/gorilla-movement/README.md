# Gorilla Movement (standard speed)

Gorilla-Tag style VR locomotion, extracted from **Lights Out: Bear Facility** (the version the classic mode uses), **without the game's later speed boost**.
You move by pushing your hands off the floor and walls: the head is a physics ball, each hand is a "follower" that chases its controller but cannot pass through the world,
and while a hand is blocked, how far it stopped short of the controller becomes your velocity. Let go and you are thrown with the average of your last few frames.
It is based on `gmotion_alpha_2.html`, with Rapier physics and three.js.

## Files
| File | What it is |
|---|---|
| `gmotion.js` | The locomotion class (`GMotion`) - the one file you need, plus `three` and Rapier |
| `HOW-TO-USE-IN-YOUR-GAME.md` | **Step-by-step guide to implementing it in your own three.js WebXR game** (rig, collision world, the frame loop, level changes, hooks, troubleshooting) |
| `vr-example.js` | A short WebXR example of the per-frame loop (rig placement, controllers, hand spheres, rescue) taken from the game's `main.js` with the game's own state removed. Not run in a headset - read it as a guide |
| `test.mjs` | A headless check of the physics (`npm install`, then `npm test`) |
| `package.json` | Dependencies: `three` 0.160.0 and `@dimforge/rapier3d-compat` 0.11.2 (the versions the game ships; Rapier 0.14+ changed the `castShape` call, so keep 0.11-0.13) |

## Use
```js
import { GMotion, HEAD_RADIUS } from './gmotion.js';
const gm = new GMotion();
await gm.load();                       // starts Rapier
gm.setWorld(mapGeometry);              // one BufferGeometry of every solid triangle (world space). 2nd argument 0 = no extra wall thickness (huge maps)
gm.teleport(x, y, z);                  // the ball's centre; the feet are HEAD_RADIUS (0.25 m) lower

// every frame in VR (dt in seconds):
gm.update(dt, leftControllerWorldPos, rightControllerWorldPos, {});
// then read gm.pos (the head ball), gm.vel, gm.fol[0|1] (where the hands really are), gm.hit[0|1] (is that hand touching something), gm.grounded
// and put the headset at gm.pos by moving the rig (see placeRigHead in vr-example.js)
```
Other calls: `gm.begin(vel, leftPos, rightPos)` (hand the player back to the physics with their momentum, after something else moved them), `gm.sync(x, y, z)` / `gm.syncIfFar(...)`
(follow an external position given as feet), `gm.setBoxes([{x0,x1,y0,y1,z0,z1}])` (extra solid boxes, e.g. lockers), `gm.dispose()`.

The last argument of `update` is optional (`{}` is plain gorilla speed): `{ gravity }` (world gravity, default 5), `{ sprint: true }` (x1.7 sprint, the game bound it to the left trigger),
`{ scale }` (a speed multiplier, default 1), `{ inv: true }` (reversed controls), `{ pull: {x, z} }` (a gentle pull towards a point).

## What differs from the original `gmotion_alpha_2.html` (all in the game's classic version)
1. The physics timestep follows the frame time (the original steps at a fixed 1/60 s).
2. The optional hooks above.
3. Walls are thicker: every triangle of the collision mesh gets a copy pushed 0.2 m behind it, so a hand cannot slip through a thin wall.
4. A controller pushed through the floor lifts the player so the hand ends up where the controller is.
5. A hand that ends up on the far side of a surface from the head is put back at the head and sweeps out again.
6. A hand resting closer to a surface than its own radius is pushed out of it (that is what left it red and unable to leave).

## What was left out on purpose
The game later added a speed boost on top of this (a gain on every push, a boosted throw on release, a compensation step to keep boosted pushes from jittering, a top-speed cap and
continuous collision on the ball). None of that is here: this is the standard-speed version.
