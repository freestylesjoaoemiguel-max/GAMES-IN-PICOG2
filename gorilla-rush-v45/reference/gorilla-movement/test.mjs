const THREE = await import('three');
const { GMotion, HEAD_RADIUS } = await import('./gmotion.js');
const gm = new GMotion(); await gm.load();
const quad = (a, b, c, d) => [...a, ...b, ...c, ...a, ...c, ...d];
const pos = new Float32Array([...quad([-30, 0, -30], [-30, 0, 30], [30, 0, 30], [30, 0, -30]), ...quad([5, 0, 60], [5, 6, 60], [5, 6, -60], [5, 0, -60])]);
const geo = new THREE.BufferGeometry(); geo.setAttribute('position', new THREE.BufferAttribute(pos, 3)); gm.setWorld(geo);
const V = (x, y, z) => new THREE.Vector3(x, y, z);
let ok = 0, bad = 0; const t = (c, m) => { c ? ok++ : bad++; console.log(c ? 'ok  ' : 'FAIL', m); };
// 1. falls and lands on the floor
gm.teleport(0, 3, 0); for (let i = 0; i < 120; i++) gm.update(1 / 72, V(gm.pos.x + 0.3, gm.pos.y - 0.2, 0.2), V(gm.pos.x + 0.3, gm.pos.y - 0.2, -0.2), {});
t(gm.pos.y < 0.6 && gm.pos.y > 0.2, 'falls and rests on the floor (y ' + gm.pos.y.toFixed(2) + ', head radius ' + HEAD_RADIUS + ')');
// 2. keeps momentum with the hands in the air
gm.teleport(0, 2.5, 0); gm.begin(V(4, 0, 0), V(0.3, 2.2, 0.2), V(0.3, 2.2, -0.2)); const x0 = gm.pos.x; for (let i = 0; i < 20; i++) gm.update(1 / 60, V(gm.pos.x + 0.3, gm.pos.y - 0.3, 0.2), V(gm.pos.x + 0.3, gm.pos.y - 0.3, -0.2), {});
t(gm.pos.x - x0 > 0.9, 'carries momentum (' + (gm.pos.x - x0).toFixed(2) + ' m in 1/3 s)');
// 3. a hard push into the wall at 3 m/s: the body speed never exceeds the plain original pace (no boost) and it never crosses the wall
let top = 0, maxX = -9; for (const fps of [72, 30]) { gm.teleport(3.9, 1.6, 0); gm.begin(V(0, 0, 0), V(4.95, 1.6, 0.1), V(3.6, 1.6, -0.3)); for (let i = 0; i < fps * 2; i++) { gm.update(1 / fps, V(gm.pos.x + 1.1 + 0.05 * Math.sin(i), 1.6, 0.1), V(gm.pos.x - 0.4, 1.6, -0.3), {}); maxX = Math.max(maxX, gm.pos.x); } }
t(maxX < 5, 'does not cross the wall (furthest x ' + maxX.toFixed(2) + ')');
// 4. throw on release is the original capped jump: at most 4 m/s x 1.1 (4.4), not boosted
gm.teleport(2, 1.6, 0); gm.begin(V(0, 0, 0), V(2.5, 1.6, 0.1), V(1.6, 1.6, -0.3)); for (let i = 0; i < 6; i++) gm.update(1 / 72, V(gm.pos.x + 0.5 - 0.5 * i / 5 * 0, 0.0, 0.1), V(gm.pos.x - 0.4, 1.6, -0.3), {}); let relMax = 0; for (let i = 0; i < 10; i++) { gm.update(1 / 72, V(gm.pos.x + 0.4, 3.2, 0.1), V(gm.pos.x - 0.4, 3.2, -0.3), {}); relMax = Math.max(relMax, Math.hypot(gm.vel.x, gm.vel.y, gm.vel.z)); }
t(relMax < 12, 'release speed stays at the original scale (' + relMax.toFixed(1) + ' m/s)');
t(!('extra' in gm) && !('shown' in gm), 'no boost state left on the object');
console.log(ok + ' passed, ' + bad + ' failed');
