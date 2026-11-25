'use strict';
/* SandLab VR — sandbox de mundo aberto para Pico G2 (WebXR / giroscópio / tela) */
const V3 = THREE.Vector3, Q4 = THREE.Quaternion, EYE = 1.65, UP = new V3(0, 1, 0);
const $ = s => document.querySelector(s);
const SET = Object.assign({ swap: false, laser: true, combat: false, slow: false, sound: true, height: 1.65 }, (() => { try { return JSON.parse(localStorage.getItem('sl_set') || '{}'); } catch (e) { return {}; } })());
const saveSet = () => { try { localStorage.setItem('sl_set', JSON.stringify(SET)); } catch (e) {} };

/* ---------- cena ---------- */
const canvas = $('#view');
const renderer = new THREE.WebGLRenderer({ canvas, antialias: true, powerPreference: 'high-performance' });
renderer.setPixelRatio(Math.min(devicePixelRatio || 1, 2)); renderer.xr.enabled = true; renderer.toneMapping = THREE.ACESFilmicToneMapping; renderer.toneMappingExposure = 1.25;
try { renderer.xr.setReferenceSpaceType('local'); renderer.xr.setFoveation(0.7); renderer.xr.setFramebufferScaleFactor(0.9); } catch (e) {}
const scene = new THREE.Scene(); scene.background = new THREE.Color(0xc9dbe8); scene.fog = new THREE.Fog(0xc9dbe8, 70, 260);
const camera = new THREE.PerspectiveCamera(75, 1, 0.03, 700), rig = new THREE.Group(); rig.add(camera); scene.add(rig);
scene.add(new THREE.HemisphereLight(0xe2ecff, 0x5d6a48, 1.35)); const sun = new THREE.DirectionalLight(0xfff0d4, 3.1); sun.position.set(-60, 90, 35); scene.add(sun);
const fill = new THREE.DirectionalLight(0xbcd2ff, 0.5); fill.position.set(50, 30, -40); scene.add(fill);
World.build(scene); const skyM = World.sky(); scene.add(skyM); FX.init(scene);
const SHN = 64, shadows = new THREE.InstancedMesh(new THREE.PlaneGeometry(1, 1).rotateX(-PI / 2), new THREE.MeshBasicMaterial({ map: TEX.shadow(), transparent: true, depthWrite: false, polygonOffset: true, polygonOffsetFactor: -1 }), SHN); shadows.frustumCulled = false; scene.add(shadows);
const _sm = new THREE.Matrix4(), _sq = new Q4(), _ss = new V3(), _sp = new V3();
function updateShadows() { let n = 0; const put = (x, y, z, s) => { if (n >= SHN) return; const fl = World.floorAt(x, z, y); const h = Math.max(0, y - fl); const k = s * (1 + h * 0.4); _sm.compose(_sp.set(x, fl + 0.012, z), _sq, _ss.set(k, 1, k)); shadows.setMatrixAt(n++, _sm); }; items.forEach(it => put(it.pos.x, it.pos.y, it.pos.z, it.def.shape === 'gun' ? 0.35 : it.def.r * 2.6)); dummies.forEach(d => { put(d.p[3].x, d.p[3].y, d.p[3].z, 0.75); put(d.p[12].x, d.p[12].y, d.p[12].z, 0.28); put(d.p[15].x, d.p[15].y, d.p[15].z, 0.28); }); _sm.makeScale(0, 0, 0); for (let i = n; i < SHN; i++) shadows.setMatrixAt(i, _sm); shadows.instanceMatrix.needsUpdate = true; }
const stereo = new THREE.StereoCamera();

/* ---------- objetos do mundo ---------- */
const items = [], dummies = [];
function spawnItem(type, x, y, z) { const it = new Item(scene, type, x, y, z); items.push(it); return it; }
function removeItem(it) { const i = items.indexOf(it); if (i >= 0) items.splice(i, 1); scene.remove(it.mesh); it.alive = false; if (hold.item === it) hold.item = null; }
function spawnDummy(x, z, yaw, kind) { const d = new DUMMY.Dummy(scene, x, z, yaw === undefined ? Math.random() * 6 : yaw, dummies.length, kind || 'dummy'); dummies.push(d); return d; }
function populate() {
  [['pistol', -0.7, 0.97, 4], ['rifle', 0, 0.97, 4], ['shotgun', 0.75, 0.97, 4]].forEach(a => { const it = spawnItem(a[0], a[1], a[2], a[3]); it.quat.setFromEuler(new THREE.Euler(0, PI / 2 + 0.1, PI / 2)); });
  for (let i = 0; i < 3; i++) for (let k = 0; k < 3 - i; k++) spawnItem('crate', -1 + k * 0.52 + i * 0.26, 0.25 + i * 0.5, 30.0); // pirâmide de caixas
  [[3, 26], [-3.5, 26.5], [5, 22]].forEach(p => spawnItem('barrel', p[0], 0.45, p[1]));
  [[2, 12], [-6, 9]].forEach(p => spawnItem('ball', p[0], 0.2, p[1]));
  [[-16.5, 1.12, 4], [-16.5, 1.12, 8.5], [-16.5, 1.12, 13]].forEach(p => spawnItem('melon', p[0], p[1], p[2]));
  for (let i = 0; i < 6; i++) spawnItem('cone', -4 + i * 1.6, 0.25, -21);
  [[-6, -6, 0.5], [6, -5, -0.4], [0, -12, 3], [-9, 2, 1.6], [10, 6, -1.5], [-12, 14, 2], [8, 16, -2]].forEach(p => spawnDummy(p[0], p[1], p[2], 'human'));
  [[4, 26, 3]].forEach(p => spawnDummy(p[0], p[1], p[2], 'dummy'));
  [[-35, 8], [-45, 15], [-30, 14]].forEach(p => spawnDummy(p[0], p[1], PI / 2));
}

/* ---------- entrada ---------- */
const Input = { move: new THREE.Vector2(), trig: false, trigDown: false, trigUp: false, grab: false, grabDown: false, menuDown: false, run: false, keys: {}, mouseL: false, mouseR: false, xrSel: false, xrSq: false, prev: {}, grabHeldT: 0, look: { yaw: 0, pitch: 0 }, pose: null, raw: '' };
let mode = 'flat', xrSession = null;
const ctrls = [0, 1].map(i => { const c = renderer.xr.getController(i); rig.add(c); c.addEventListener('connected', e => { c.userData.on = true; c.userData.hand = e.data.handedness; }); c.addEventListener('disconnected', () => c.userData.on = false); c.addEventListener('selectstart', () => Input.xrSel = true); c.addEventListener('selectend', () => Input.xrSel = false); c.addEventListener('squeezestart', () => Input.xrSq = true); c.addEventListener('squeezeend', () => Input.xrSq = false); return c; });
const activeCtrl = () => { const c = ctrls.filter(x => x.userData.on); return c.find(x => x.userData.hand === 'right') || c[0] || null; };
function poll(dt) {
  let list = []; try { for (const p of (navigator.getGamepads ? navigator.getGamepads() : [])) if (p && p.connected) list.push(p); } catch (e) {}
  if (xrSession) try { for (const s of xrSession.inputSources) if (s.gamepad && !list.includes(s.gamepad)) list.push(s.gamepad); } catch (e) {}
  let trig = false, grab = false, menu = false, ax = 0, ay = 0; Input.pose = null;
  for (const p of list) {
    const b = i => !!(p.buttons[i] && p.buttons[i].pressed), std = p.mapping === 'xr-standard';
    trig = trig || b(0); grab = grab || b(2) || b(3) || (!std && b(1)); menu = menu || b(4) || b(5) || (std && b(1) && false);
    if (p.pose && p.pose.orientation) Input.pose = p.pose.orientation;
    for (const pr of [[p.axes[0] || 0, p.axes[1] || 0], [p.axes[2] || 0, p.axes[3] || 0]]) if (Math.hypot(pr[0], pr[1]) > Math.hypot(ax, ay)) { ax = pr[0]; ay = pr[1]; }
    Input.raw = p.id + ' ' + p.buttons.map((x, i) => x.pressed ? i : '').join('');
  }
  trig = trig || Input.xrSel || Input.mouseL || Input.keys[' ']; grab = grab || Input.xrSq || Input.mouseR || Input.keys.e;
  if (SET.swap) { const t = trig; trig = grab; grab = t; }
  menu = menu || Input.keys.q || Input.keys.tab;
  const K = Input.keys; const kx = (K.d ? 1 : 0) - (K.a ? 1 : 0), ky = (K.w ? 1 : 0) - (K.s ? 1 : 0);
  if (grab) { ax = 0; ay = 0; } // clicar no touch não anda
  const dz = 0.22; Input.move.set(Math.abs(ax) > dz ? ax : 0, Math.abs(ay) > dz ? -ay : 0); if (kx || ky) Input.move.set(kx, ky);
  Input.run = !!K.shift || Input.move.length() > 0.92;
  const P = Input.prev; Input.trigDown = trig && !P.trig; Input.trigUp = !trig && P.trig; Input.grabDown = grab && !P.grab; Input.grabUp = !grab && P.grab; Input.menuDown = menu && !P.menu; Input.trig = trig; Input.grab = grab;
  P.trig = trig; P.grab = grab; P.menu = menu;
  Input.grabHeldT = grab ? Input.grabHeldT + dt : 0;
}
function haptic(v, ms) { try { if (!xrSession) return; for (const s of xrSession.inputSources) { const a = s.gamepad && s.gamepad.hapticActuators && s.gamepad.hapticActuators[0]; if (a && a.pulse) a.pulse(v, ms || 40); } } catch (e) {} }

/* ---------- jogador, braço e mão ---------- */
const player = { vel: new V3(), hp: 100, hurtT: 0, stepAcc: 0, bodyYaw: 0, knock: new V3() };
const hand = { pos: new V3(), prev: new V3(), vel: new V3(), q: new Q4(), F: new V3(0, 0, -1), side: 1, six: false, aimDist: 8, pmin: new V3(9, 9, 9), pmax: new V3(-9, -9, -9), jab: 0 };
const handSkin = new THREE.MeshPhongMaterial({ color: 0xd9a27c, shininess: 12, specular: 0x2a1a10 }), sleeveM = new THREE.MeshLambertMaterial({ color: 0x2f3a48 });
function gloveMesh(fist) { // mão direita: dedos para -z, palma para -x, polegar para cima
  const g = new THREE.Group(), C = new THREE.CylinderGeometry(1, 1, 1, 8), S = new THREE.SphereGeometry(1, 8, 6), skinM = handSkin;
  const lim = (a, b, r, m) => { const A = new V3(...a), B = new V3(...b), d = B.clone().sub(A), L = d.length(); const o = new THREE.Mesh(C, m || skinM); o.position.copy(A).addScaledVector(d, 0.5); o.quaternion.setFromUnitVectors(UP, d.normalize()); o.scale.set(r, L, r); g.add(o); const j = new THREE.Mesh(S, m || skinM); j.position.copy(B); j.scale.setScalar(r * 1.02); g.add(j); };
  const palm = new THREE.Mesh(S, skinM); palm.scale.set(0.022, 0.046, 0.052); palm.position.set(0, 0, 0.005); g.add(palm);
  const back = new THREE.Mesh(new THREE.BoxGeometry(1, 1, 1), skinM); back.scale.set(0.03, 0.075, 0.07); back.position.set(0.004, 0, 0.01); g.add(back);
  const fy = [0.026, 0.009, -0.008, -0.024], len = [1, 1.1, 1.0, 0.82];
  fy.forEach((y, i) => { const k = len[i], r = i === 3 ? 0.0075 : 0.0088;
    if (fist) { lim([0.002, y, -0.03], [-0.004, y, -0.062 * k], r); lim([-0.004, y, -0.062 * k], [-0.03, y, -0.058 * k], r * 0.95); lim([-0.03, y, -0.058 * k], [-0.034, y, -0.032], r * 0.9); }
    else { lim([0.002, y, -0.03], [0, y, -0.068 * k], r); lim([0, y, -0.068 * k], [-0.008, y, -0.094 * k], r * 0.95); lim([-0.008, y, -0.094 * k], [-0.016, y, -0.112 * k], r * 0.88); } });
  if (fist) { lim([-0.008, 0.036, 0.005], [-0.026, 0.04, -0.03], 0.0105); lim([-0.026, 0.04, -0.03], [-0.036, 0.022, -0.05], 0.0095); }
  else { lim([-0.008, 0.036, 0.005], [-0.024, 0.052, -0.03], 0.0105); lim([-0.024, 0.052, -0.03], [-0.03, 0.06, -0.058], 0.0095); }
  const cuff = new THREE.Mesh(C, sleeveM); cuff.rotation.x = PI / 2; cuff.scale.set(0.044, 0.12, 0.044); cuff.position.set(0.002, 0, 0.1); g.add(cuff);
  const wrist = new THREE.Mesh(C, skinM); wrist.rotation.x = PI / 2; wrist.scale.set(0.028, 0.05, 0.026); wrist.position.set(0.002, 0, 0.045); g.add(wrist);
  mergeGroup(g); scene.add(g); return g;
}
const gloveOpen = gloveMesh(false), gloveFist = gloveMesh(true); gloveFist.visible = false;
const armU = new THREE.Mesh(new THREE.CylinderGeometry(1, 1, 1, 8), sleeveM), armF = new THREE.Mesh(new THREE.CylinderGeometry(1, 1, 1, 8), sleeveM); scene.add(armU, armF);
const _s = new V3(); function seg(m, a, b, r) { const d = _s.subVectors(b, a), L = d.length() || 1e-3; m.position.copy(a).addScaledVector(d, 0.5); m.quaternion.setFromUnitVectors(UP, d.multiplyScalar(1 / L)); m.scale.set(r, L, r); }
const laser = new THREE.Mesh(new THREE.CylinderGeometry(1, 1, 1, 5, 1, true), new THREE.MeshBasicMaterial({ color: 0x7fd3ff, transparent: true, opacity: 0.55, depthWrite: false })); scene.add(laser);
const dotM = new THREE.Mesh(new THREE.SphereGeometry(1, 8, 6), new THREE.MeshBasicMaterial({ color: 0xffffff, depthTest: false })); dotM.renderOrder = 40; scene.add(dotM);
const ring = new THREE.Mesh(new THREE.RingGeometry(0.85, 1, 32), new THREE.MeshBasicMaterial({ color: 0x7dffb0, transparent: true, opacity: 0.8, depthTest: false, side: THREE.DoubleSide })); ring.renderOrder = 41; scene.add(ring);
const hurtM = new THREE.Mesh(new THREE.PlaneGeometry(4, 4), new THREE.MeshBasicMaterial({ color: 0xb00000, transparent: true, opacity: 0, depthTest: false })); hurtM.position.z = -0.2; hurtM.renderOrder = 99; camera.add(hurtM);
let poseYaw = null, nearFoe = false;
const gazeAim = () => mode === 'flat' || (mode !== 'xr' && !Input.pose) || (mode === 'xr' && !activeCtrl()); // sem controle rastreado: mira pelo olhar
function headYaw() { const d = new V3(); camera.getWorldDirection(d); return Math.atan2(-d.x, -d.z); }
function updateHand(dt) {
  const hp = new V3(); camera.getWorldPosition(hp); const hy = headYaw(); let dy = hy - player.bodyYaw; while (dy > PI) dy -= 2 * PI; while (dy < -PI) dy += 2 * PI; player.bodyYaw += dy * Math.min(1, dt * (Math.abs(dy) > 0.8 ? 8 : 2.5));
  const c = mode === 'xr' ? activeCtrl() : null, q = new Q4();
  if (c) { c.getWorldQuaternion(q); hand.side = c.userData.hand === 'left' ? -1 : 1; hand.pmin.min(c.position); hand.pmax.max(c.position); if (hand.pmax.clone().sub(hand.pmin).length() > 0.25 && c.position.lengthSq() > 0.0009) hand.six = true; }
  else if (Input.pose && mode !== 'xr') { q.fromArray(Input.pose); const f = new V3(0, 0, -1).applyQuaternion(q); if (poseYaw === null) poseYaw = hy - Math.atan2(-f.x, -f.z); q.premultiply(new Q4().setFromAxisAngle(UP, poseYaw)); }
  else { camera.getWorldQuaternion(q); }
  if (gazeAim()) { // sem controle rastreado: mão/arma convergem para o ponto no centro da mira
    const co = new V3(), cd = new V3(); camera.getWorldPosition(co); camera.getWorldDirection(cd); const w = World.raycast(co, cd, 40); let dist = w ? Math.max(1.2, w.t) : 25;
    for (const dm of dummies) { const h = dm.rayHit(co, cd, dist); if (h) dist = Math.max(1.2, h.t); }
    for (const it of items) { if (it.held) continue; const oc = it.pos.clone().sub(co), t = oc.dot(cd); if (t > 0 && t < dist && oc.lengthSq() - t * t < it.def.r * it.def.r) dist = Math.max(1.2, t); }
    hand.aimDist += (dist - hand.aimDist) * Math.min(1, dt * (dist < hand.aimDist ? 14 : 5)); const hpos = co.clone().add(new V3(0.2, -0.22, -0.45).applyQuaternion(camera.getWorldQuaternion(new Q4()))), aimP = co.addScaledVector(cd, hand.aimDist), f = aimP.sub(hpos).normalize();
    const m = new THREE.Matrix4().lookAt(new V3(), f, UP); q.setFromRotationMatrix(m);
  }
  hand.q.slerp(q, mode === 'xr' ? 1 : Math.min(1, dt * 18)); hand.F.set(0, 0, -1).applyQuaternion(hand.q);
  const y = player.bodyYaw, sh = new V3(hp.x + Math.cos(y) * 0.2 * hand.side, hp.y - 0.28, hp.z - Math.sin(y) * 0.2 * hand.side);
  hand.prev.copy(hand.pos);
  if (c && hand.six) c.getWorldPosition(hand.pos);
  else if (gazeAim()) hand.pos.copy(hp).add(new V3(0.2, -0.22, -0.45).applyQuaternion(camera.getWorldQuaternion(new Q4())));
  else hand.pos.copy(sh).addScaledVector(hand.F, 0.52);
  if (hand.jab > 0) { hand.jab -= dt; const k = Math.sin((1 - hand.jab / 0.24) * PI); hand.pos.addScaledVector(hand.F, k * 0.32); }
  const inst = hand.pos.clone().sub(hand.prev).multiplyScalar(1 / Math.max(dt, 1e-3)); if (inst.length() > 20) inst.setLength(20); hand.vel.lerp(inst, 0.5);
  // braço (IK 2 ossos)
  const S = sh, H = hand.pos.clone().addScaledVector(new V3(0, 0, 0.08).applyQuaternion(hand.q), 1), d = Math.min(S.distanceTo(H), 0.6), L = 0.3, hh = Math.sqrt(Math.max(0, L * L - d * d / 4)), dir = H.clone().sub(S).normalize();
  const bend = new V3(0, -1, 0).addScaledVector(new V3(Math.cos(y), 0, -Math.sin(y)), 0.6 * hand.side); bend.addScaledVector(dir, -bend.dot(dir)).normalize(); const E = S.clone().addScaledVector(dir, d / 2).addScaledVector(bend, hh);
  const vis = !gazeAim(); armU.visible = armF.visible = vis; if (vis) { seg(armU, S, E, 0.042); seg(armF, E, H, 0.036); }
  const fist = !!hold.item || !!hold.dummy || Input.trig || hand.jab > 0 || nearFoe; gloveOpen.visible = !fist; gloveFist.visible = fist; const gl = fist ? gloveFist : gloveOpen; gl.position.copy(hand.pos); gl.quaternion.copy(hand.q);
}

/* ---------- pegar / arremessar ---------- */
const hold = { item: null, dummy: null, joint: -1, mode: 'toggle', pull: 0, offQ: new Q4(), grabPoint: new V3(), from: new V3() };
let target = null;
function findTarget() {
  let o = hand.pos, d = hand.F, best = null;
  if (gazeAim()) { o = new V3(); d = new V3(); camera.getWorldPosition(o); camera.getWorldDirection(d); }
  for (const it of items) { if (it.held) continue; const r = it.def.r + 0.18, oc = it.pos.clone().sub(o), t = oc.dot(d); if (t < 0 || t > 16) continue; const dist2 = oc.lengthSq() - t * t; const tol = r + t * 0.025; if (dist2 < tol * tol && (!best || t < best.t)) best = { t, item: it, p: it.pos.clone(), r: it.def.r }; }
  for (const dm of dummies) { const h = dm.rayHit(o, d, 16); if (h && (!best || h.t < best.t)) best = { t: h.t, dummy: dm, j: h.j, p: dm.p[h.j].clone(), r: 0.2 }; }
  const w = World.raycast(o, d, best ? best.t : 16); if (w && best && w.t < best.t - 0.2) best = null;
  return best;
}
function grabTarget(t, how) {
  if (!t) return; SND.play('grab'); haptic(0.4, 40); hold.mode = how;
  if (t.item) { hold.item = t.item; t.item.held = true; hold.pull = 0.16; hold.from.copy(t.item.pos); hold.mode = t.item.gun ? 'toggle' : how; hold.offQ.copy(hand.q).invert().multiply(t.item.quat); if (t.item.gun) { t.item.ammo.visible = true; toast(t.item.gun.name + ' — R2 atira'); } }
  else { hold.dummy = t.dummy; hold.joint = t.j; t.dummy.grabbed = t.j; t.dummy.stab = 0; hold.grabPoint.copy(t.dummy.p[t.j]); hold.pull = 0.3; hold.from.copy(t.dummy.p[t.j]); }
}
function release(throwIt) {
  const v = hand.vel.clone().multiplyScalar(mode === 'xr' && !hand.six ? 1.7 : 1.2); if (throwIt) v.addScaledVector(hand.F, 6);
  if (hold.item) { const it = hold.item; it.held = false; it.vel.copy(v); it.ang.set(Math.random() - 0.5, Math.random() - 0.5, Math.random() - 0.5).multiplyScalar(v.length() * 1.5); if (it.gun) it.ammo.visible = false; hold.item = null; }
  if (hold.dummy) { const dm = hold.dummy; dm.grabbed = -1; const k = 1 / 60; dm.pp.forEach((pp, i) => { pp.copy(dm.p[i]).addScaledVector(v, -k * (i === hold.joint ? 1 : 0.7)); }); dm.stab = 0; dm.limp = 2.8; hold.dummy = null; }
  SND.play(v.length() > 3 ? 'throw' : 'drop');
}
function updateHold(dt) {
  if (hold.pull > 0) hold.pull -= dt;
  const k = hold.pull > 0 ? 1 - hold.pull / 0.16 : 1;
  if (hold.item) {
    const it = hold.item, q = hand.q;
    if (it.gun) { const grip = { pistol: [0, -0.035, 0.012], rifle: [0, -0.045, 0.05], shotgun: [0, -0.035, 0.085] }[it.type]; it.kick *= Math.exp(-dt * 14); const kq = new Q4().setFromAxisAngle(new V3(1, 0, 0), it.kick * 2.2); const tq = q.clone().multiply(kq); const tp = hand.pos.clone().sub(new V3(grip[0], grip[1], grip[2] - it.kick * 0.25).applyQuaternion(tq)); it.pos.lerpVectors(hold.from, tp, Math.min(1, k)); it.quat.copy(tq); }
    else { const tp = hand.pos.clone().addScaledVector(hand.F, 0.12 + it.def.r); it.pos.lerpVectors(hold.from, tp, Math.min(1, k)); it.quat.copy(q).multiply(hold.offQ); }
    it.vel.set(0, 0, 0);
  }
  if (hold.dummy) { const tp = hand.pos.clone().addScaledVector(hand.F, 0.15), kk = hold.pull > 0 ? 1 - hold.pull / 0.3 : 1; hold.grabPoint.lerpVectors(hold.from, tp, Math.min(1, kk)); }
}

/* ---------- soco ---------- */
const punch = { cool: 0 };
function punchHit(dm, j, speed, dir) {
  const dmg = 10 + Math.min(25, speed * 5), r = dm.hit(j, dir.clone().multiplyScalar(0.018 + speed * 0.006), dmg, 'player');
  SND.play('hitPunch', Math.min(1.2, 0.5 + speed * 0.15)); SND.play('grunt', dm.kind === 'human' ? 1 : 0.7); haptic(Math.min(1, 0.4 + speed * 0.12), 70);
  const p = dm.p[j].clone(); if (dm.kind === 'human') FX.bleed(p, dir, 0.45); else FX.dustAt(p, 'dummy');
  punch.cool = 0.35; if (r === 'kill') onKill(dm); else toast(dm.angry ? (dm.kind === 'human' ? '😡 Ele vai revidar!' : 'O boneco vai revidar!') : '👊 Soco!', 1.2);
}
function nearestJoint(pt, maxD) { let best = null; for (const dm of dummies) { for (let j = 0; j < 16; j++) { const d = dm.p[j].distanceTo(pt); if (d < maxD && (!best || d < best.d)) best = { dm, j, d }; } } return best; }
function updatePunch(dt) {
  punch.cool -= dt; nearFoe = false;
  if (hold.item || hold.dummy || menu.open) return;
  const fist = hand.pos.clone().addScaledVector(hand.F, 0.07), foe = nearestJoint(fist, 1.6); nearFoe = !!foe;
  // VR: soco balançando o controle
  const sp = hand.vel.length();
  if (punch.cool <= 0 && sp > 1.6) { const h = nearestJoint(fist, 0.3); if (h) { punchHit(h.dm, h.j, sp, hand.vel.clone().normalize()); return; } }
  // R2 com pessoa por perto (ou PC/giroscópio): jab
  if (Input.trigDown && punch.cool <= 0 && target && target.dummy && target.t < 1.9) {
    hand.jab = 0.24; SND.play('swing'); punch.cool = 0.4; const tg = target;
    setTimeout(() => { if (tg.dummy.p[tg.j]) punchHit(tg.dummy, tg.j, 4.5, hand.F.clone()); }, 110);
    return 'punched';
  }
}
function onKill(dm) { stats.kos++; toast(dm.kind === 'human' ? '💀 Pessoa nocauteada!' : '💥 Boneco derrubado!'); if (dm.kind === 'human') setTimeout(() => FX.pool(dm.p[3]), 900); }

/* ---------- tiro ---------- */
const _o = new V3(), _d = new V3();
function fire(it) {
  const G = it.gun; it.cool = G.rate; it.ammoN--; it.kick = Math.min(0.25, it.kick + G.recoil); SND.play(G.snd); haptic(Math.min(1, G.recoil * 6), 50);
  it.flash.visible = true; it.flash.material.rotation = Math.random() * 6; it.flashT = 0.05;
  const m = it.mesh.localToWorld(it.muzzle.clone()), fwd = new V3(0, 0, -1).applyQuaternion(it.quat).normalize();
  for (let n = 0; n < G.pellets; n++) {
    _d.copy(fwd).add(new V3((Math.random() - 0.5) * 2, (Math.random() - 0.5) * 2, (Math.random() - 0.5) * 2).multiplyScalar(G.spread)).normalize(); _o.copy(m);
    let best = World.raycast(_o, _d, 160), what = 'world';
    for (const dm of dummies) { const h = dm.rayHit(_o, _d, best ? best.t : 160); if (h && (!best || h.t < best.t)) { best = h; what = dm; } }
    for (const x of items) { if (x === it || x.held) continue; const oc = x.pos.clone().sub(_o), t = oc.dot(_d); if (t <= 0) continue; const r = x.def.r * 0.85; if (oc.lengthSq() - t * t < r * r && (!best || t < best.t)) { best = { t, item: x }; what = x; } }
    const end = best ? _o.clone().addScaledVector(_d, best.t) : _o.clone().addScaledVector(_d, 80);
    if (n === 0 || n % 3 === 0) FX.tracer(m.clone().addScaledVector(_d, 0.1), end);
    if (!best) continue;
    if (what === 'world') {
      const kind = best.kind; if (kind === 'steel') { SND.play('steel'); FX.sparkAt(end, 14); } else { FX.decal(end, best.n, kind === 'paper' ? 0.035 : 0.06); if (kind === 'metal') { SND.play('metal'); FX.sparkAt(end, 8); } else { SND.play(kind === 'wood' ? 'wood' : kind === 'paper' ? 'paper' : kind === 'ground' ? 'ground' : 'concrete'); FX.dustAt(end, kind); } }
      if (best.plate && best.plate.kind === 'paper') stats.targets++;
    } else if (what instanceof Item) {
      const x = what; if (x.def.splat) { FX.splat(x.pos); SND.play('splat'); removeItem(x); stats.melons++; continue; }
      x.vel.addScaledVector(_d, G.force * 3 / x.def.mass); x.vel.y += 1.2 / x.def.mass; x.ang.add(new V3(Math.random() - 0.5, Math.random() - 0.5, Math.random() - 0.5).multiplyScalar(8 / x.def.mass)); SND.play(x.def.snd); if (x.def.snd === 'metal') FX.sparkAt(end, 8); else FX.dustAt(end, 'wood');
    } else {
      const r = what.hit(best.j, _d.clone().multiplyScalar(0.035 * G.force), G.dmg, 'player');
      if (what.kind === 'human') { FX.bleed(end, _d, best.j === 0 ? 1.4 : 1); SND.play('flesh'); if (Math.random() < 0.5) SND.play('grunt', 1.1); } else { FX.dustAt(end, 'dummy'); SND.play(r === 'head' || best.j === 0 ? 'head' : 'dummy'); }
      if (r === 'kill') onKill(what);
    }
  }
  FX.casing(it.mesh.localToWorld(it.eject.clone()), new V3(1.6, 1.4, 0.2).applyQuaternion(it.quat).add(new V3((Math.random() - 0.5) * 0.4, 0, 0)));
  if (it.type === 'shotgun') { setTimeout(() => SND.play('pump'), 120); it.pumpT = 0.55; }
  if (it.ammoN <= 0) it.reloadT = 1.3;
  it.drawAmmo();
}
function updateGun(it, dt) {
  it.cool -= dt; if (it.flashT > 0) { it.flashT -= dt; if (it.flashT <= 0) it.flash.visible = false; }
  if (it.pumpT > 0) { it.pumpT -= dt; if (it.pump) it.pump.position.z = -0.24 + Math.sin((1 - it.pumpT / 0.55) * PI) * 0.07; }
  if (it.reloadT > 0) { const was = it.reloadT; it.reloadT -= dt; if (was >= 1.3 - dt) SND.play('reload'); if (it.reloadT <= 0) { it.ammoN = it.gun.mag; it.drawAmmo(); } else if (Math.floor(was * 4) !== Math.floor(it.reloadT * 4)) it.drawAmmo(); return; }
  if (hold.item !== it) return;
  const want = it.gun.auto ? Input.trig : Input.trigDown;
  if (want && it.cool <= 0) { if (it.ammoN > 0) fire(it); else if (Input.trigDown) { SND.play('empty'); it.reloadT = 1.3; it.drawAmmo(); } }
  if (Input.keys.r && it.reloadT <= 0 && it.ammoN < it.gun.mag) { it.reloadT = 1.3; it.drawAmmo(); }
}

/* ---------- HUD e menu ---------- */
const stats = { kos: 0, targets: 0, melons: 0 };
function makePanel(w, h, pw, ph) { const c = document.createElement('canvas'); c.width = pw; c.height = ph; const t = new THREE.CanvasTexture(c); t.colorSpace = THREE.SRGBColorSpace; const m = new THREE.Mesh(new THREE.PlaneGeometry(w, h), new THREE.MeshBasicMaterial({ map: t, transparent: true, depthTest: false, toneMapped: false })); m.renderOrder = 60; return { m, c, x: c.getContext('2d'), t }; }
const hud = makePanel(0.5, 0.11, 640, 140); hud.m.position.set(0, -0.3, -0.75); hud.m.rotation.x = -0.35; camera.add(hud.m);
let toastT = 0, toastMsg = '';
function toast(m, s) { toastMsg = m; toastT = s || 2.2; }
function rr(c, x, y, w, h, r) { c.beginPath(); c.moveTo(x + r, y); c.arcTo(x + w, y, x + w, y + h, r); c.arcTo(x + w, y + h, x, y + h, r); c.arcTo(x, y + h, x, y, r); c.arcTo(x, y, x + w, y, r); c.closePath(); }
function drawHud() {
  const c = hud.x, W = 640, H = 140; c.clearRect(0, 0, W, H); c.fillStyle = 'rgba(8,12,22,.55)'; rr(c, 0, 0, W, H, 26); c.fill();
  c.fillStyle = '#333a48'; rr(c, 24, 22, 260, 18, 9); c.fill(); c.fillStyle = player.hp > 40 ? '#4be08a' : '#ff5252'; rr(c, 24, 22, 260 * Math.max(0, player.hp) / 100, 18, 9); c.fill();
  c.font = '700 24px sans-serif'; c.fillStyle = '#fff'; c.textBaseline = 'middle'; c.fillText('VIDA', 300, 31);
  c.font = '600 22px sans-serif'; c.fillStyle = '#ffd23f'; c.fillText('💥 ' + stats.kos + '   🎯 ' + stats.targets + '   🍉 ' + stats.melons + (SET.slow ? '   ⏱ LENTO' : ''), 24, 70);
  c.fillStyle = '#a9c7ff'; c.font = '500 21px sans-serif'; c.fillText(toastT > 0 ? toastMsg : (hold.item ? (hold.item.gun ? 'R2 atira · clique do meio solta' : 'Solte o R2 / clique p/ arremessar') : target && target.dummy && target.t < 1.9 ? 'R2: soco! (ou balance o controle)' : target ? 'Clique do meio ou R2 para pegar' : 'Touch: andar · segure o clique: menu'), 24, 110);
  hud.t.needsUpdate = true;
}
const menu = makePanel(1.5, 1.05, 1100, 770); menu.m.visible = false; scene.add(menu.m); menu.open = false; menu.btns = []; menu.hover = null;
function drawMenu() {
  const c = menu.x, W = 1100, H = 770; menu.btns = []; c.clearRect(0, 0, W, H);
  c.fillStyle = 'rgba(10,14,26,.94)'; rr(c, 0, 0, W, H, 34); c.fill(); c.strokeStyle = '#f2c31b'; c.lineWidth = 6; c.stroke();
  c.fillStyle = '#f2c31b'; c.font = '800 54px sans-serif'; c.textAlign = 'left'; c.textBaseline = 'middle'; c.fillText('SANDLAB', 40, 56); c.fillStyle = '#fff'; c.font = '400 26px sans-serif'; c.fillText('menu de criação', 330, 60);
  const btn = (id, x, y, w, h, label, fn, on) => { menu.btns.push({ id, x, y, w, h, fn }); const hv = menu.hover === id; rr(c, x, y, w, h, 18); c.fillStyle = on ? '#1f7a4d' : hv ? '#3a4370' : '#222a42'; c.fill(); if (hv) { c.strokeStyle = '#f2c31b'; c.lineWidth = 4; c.stroke(); } c.fillStyle = '#fff'; c.font = '600 26px sans-serif'; c.textAlign = 'center'; c.fillText(label, x + w / 2, y + h / 2 + 2); c.textAlign = 'left'; };
  const row = (y, title) => { c.fillStyle = '#9aa3c0'; c.font = '600 22px sans-serif'; c.fillText(title, 40, y); };
  const sp = t => () => spawnInFront(t);
  row(118, 'ARMAS'); [['pistol', '🔫 Pistola'], ['rifle', '🔫 Fuzil'], ['shotgun', '🔫 Escopeta']].forEach((b, i) => btn('w' + i, 40 + i * 340, 135, 320, 70, b[1], sp(b[0])));
  row(240, 'OBJETOS'); [['crate', '📦 Caixa'], ['barrel', '🛢 Barril'], ['ball', '⚽ Bola'], ['melon', '🍉 Melancia'], ['cone', '🔺 Cone']].forEach((b, i) => btn('p' + i, 40 + i * 205, 257, 190, 70, b[1], sp(b[0])));
  row(362, 'PESSOAS E BONECOS'); btn('d0', 40, 379, 155, 70, '🧍 Pessoa', () => spawnInFront('human')); btn('d3', 205, 379, 155, 70, '🤖 Boneco', () => spawnInFront('dummy')); btn('d1', 380, 379, 320, 70, '♻ Reviver todos', () => dummies.forEach(d => d.revive())); btn('d2', 720, 379, 340, 70, SET.combat ? '⚔ Combate: LIGADO' : '⚔ Combate: desligado', () => { SET.combat = !SET.combat; saveSet(); toast(SET.combat ? 'Bonecos vêm te atacar!' : 'Bonecos pacíficos'); }, SET.combat);
  row(484, 'MUNDO'); btn('m0', 40, 501, 250, 70, SET.slow ? '⏱ Lento: SIM' : '⏱ Câmera lenta', () => { SET.slow = !SET.slow; saveSet(); }, SET.slow); btn('m1', 305, 501, 250, 70, '🧹 Limpar', () => { items.slice().forEach(i => { if (!i.gun && !i.held) removeItem(i); }); }); btn('m2', 570, 501, 230, 70, '🏠 Início', () => { rig.position.set(0, SET.height, 8); }); btn('m3', 815, 501, 245, 70, SET.sound ? '🔊 Som' : '🔇 Mudo', () => { SET.sound = !SET.sound; SND.set(SET.sound); saveSet(); });
  btn('s0', 40, 600, 330, 64, SET.laser ? '🔴 Mira laser: SIM' : 'Mira laser: não', () => { SET.laser = !SET.laser; saveSet(); }, SET.laser); btn('s1', 385, 600, 330, 64, SET.swap ? '⇄ Botões trocados' : '⇄ Trocar botões', () => { SET.swap = !SET.swap; saveSet(); }, SET.swap); btn('s2', 730, 600, 330, 64, '✕ Fechar menu', () => closeMenu());
  c.fillStyle = '#7f8aa8'; c.font = '400 20px sans-serif'; c.fillText('R2 (gatilho de trás): atirar / segurar objeto · Clique do meio: pegar e soltar · Touch: andar', 40, 712); c.fillText('Controle: ' + (Input.raw || 'nenhum detectado').slice(0, 60), 40, 742);
  menu.t.needsUpdate = true;
}
function openMenu() { menu.open = true; menu.m.visible = true; hud.m.visible = false; const f = new V3(); camera.getWorldDirection(f); f.y = 0; f.normalize(); const p = new V3(); camera.getWorldPosition(p); menu.m.position.set(p.x + f.x * 1.25, p.y - 0.1, p.z + f.z * 1.25); menu.m.rotation.set(0, Math.atan2(-f.x, -f.z), 0); drawMenu(); SND.play('click'); }
function closeMenu() { menu.open = false; menu.m.visible = false; hud.m.visible = true; SND.play('click'); }
function spawnInFront(t) {
  const f = new V3(); camera.getWorldDirection(f); f.y = 0; f.normalize(); const p = rig.position.clone().addScaledVector(f, 1.8);
  if (t === 'dummy' || t === 'human') spawnDummy(p.x, p.z, Math.atan2(-f.x, -f.z) + PI, t); else { const it = spawnItem(t, p.x, 1.4, p.z); it.vel.set(0, 0.5, 0); }
  SND.play('spawn'); FX.sparkAt(new V3(p.x, 1.2, p.z), 12);
}
function menuPointer() {
  const ray = new THREE.Raycaster(hand.pos.clone(), hand.F.clone()); if (gazeAim()) { const o = new V3(); camera.getWorldPosition(o); const d = new V3(); camera.getWorldDirection(d); ray.set(o, d); }
  const h = ray.intersectObject(menu.m, false)[0]; let id = null;
  if (h) { const px = h.uv.x * 1100, py = (1 - h.uv.y) * 770; for (const b of menu.btns) if (px >= b.x && px <= b.x + b.w && py >= b.y && py <= b.y + b.h) id = b.id; dotM.position.copy(h.point); dotM.scale.setScalar(0.008); dotM.visible = true; }
  if (id !== menu.hover) { menu.hover = id; drawMenu(); if (id) SND.play('ui'); }
  if ((Input.trigDown || Input.grabDown) && id) { SND.init(); SND.play('click'); menu.btns.find(b => b.id === id).fn(); drawMenu(); }
  return h ? h.point : null;
}

/* ---------- loop ---------- */
const ctxD = { player: rig.position, playerHead: new V3(), combat: false, autoRevive: true, grabPoint: null, onPunch, onThud: (d, v) => { if (v > 0.03) SND.play('dummy', Math.min(1, v * 8)); } };
function onPunch(d) { player.hp -= d.kind === 'human' ? 11 : 9; player.hurtT = 0.35; SND.play('hitPunch', 1); SND.play('grunt', 0.8); haptic(1, 120); const away = rig.position.clone().sub(d.root); away.y = 0; away.normalize(); player.knock.addScaledVector(away, 3.5); if (player.hp <= 0) { toast('Você foi nocauteado! Voltando ao início…', 3); player.hp = 100; rig.position.set(0, SET.height, 8); dummies.forEach(x => { x.angry = false; if (x.state === 'combat') x.state = 'idle'; }); } }
let time = 0, last = 0, footT = 0;
function update(rdt) {
  const dt = rdt * (SET.slow ? 0.35 : 1); time += dt; poll(rdt);
  if (Input.menuDown || (Input.grabHeldT > 0.75 && !hold.item && !hold.dummy && !menu.open && !Input._lp)) { Input._lp = true; menu.open ? closeMenu() : openMenu(); }
  if (!Input.grab) Input._lp = false;
  // locomoção (direção do olhar)
  const f = new V3(); camera.getWorldDirection(f); f.y = 0; f.normalize(); const r = new V3(-f.z, 0, f.x), sp = Input.run ? 4.3 : 2.5;
  const mv = f.multiplyScalar(Input.move.y).addScaledVector(r, Input.move.x); if (mv.lengthSq() > 1) mv.normalize();
  rig.position.addScaledVector(mv, sp * rdt).addScaledVector(player.knock, rdt); player.knock.multiplyScalar(Math.exp(-rdt * 6));
  const pp = { x: rig.position.x, y: 0, z: rig.position.z }; World.collide(pp, 0.3, 1.8); rig.position.x = pp.x; rig.position.z = pp.z; rig.position.y = SET.height + World.floorAt(pp.x, pp.z, 0.6);
  if (mv.lengthSq() > 0.01) { footT += rdt * sp; if (footT > 1.6) { footT = 0; SND.play('step'); } }
  if (mode === 'flat') { rig.rotation.y = Input.look.yaw; camera.rotation.set(Input.look.pitch, 0, 0, 'YXZ'); }
  rig.updateMatrixWorld(true); camera.getWorldPosition(ctxD.playerHead);
  updateHand(rdt);
  // pegar / soltar
  if (menu.open) { menuPointer(); ring.visible = false; }
  else {
    dotM.visible = false; target = hold.item || hold.dummy ? null : findTarget();
    if (Input.grabDown) { if (hold.item || hold.dummy) release(false); else if (target) grabTarget(target, 'toggle'); }
    const pr = updatePunch(rdt);
    if (Input.trigDown && pr !== 'punched' && !hold.item && !hold.dummy && target && !(target.dummy && target.t < 1.9)) grabTarget(target, 'trigger');
    else if (Input.trigUp && hold.mode === 'trigger' && (hold.dummy || (hold.item && !hold.item.gun))) release(true);
    ring.visible = !!target; if (target) { ring.position.copy(target.p); ring.lookAt(ctxD.playerHead); ring.scale.setScalar(target.r + 0.08 + Math.sin(time * 8) * 0.01); }
  }
  updateHold(rdt);
  // laser / mira
  const gun = hold.item && hold.item.gun ? hold.item : null; let lEnd = null;
  if (menu.open) lEnd = dotM.visible ? dotM.position.clone() : hand.pos.clone().addScaledVector(hand.F, 2);
  else if (gun && SET.laser) { const m = gun.mesh.localToWorld(gun.muzzle.clone()), d = new V3(0, 0, -1).applyQuaternion(gun.quat); const w = World.raycast(m, d, 60); lEnd = m.clone().addScaledVector(d, w ? w.t : 60); dotM.visible = true; dotM.position.copy(lEnd); dotM.scale.setScalar(0.012 + (w ? w.t : 60) * 0.0015); dotM.material.color.set(0xff2a2a); laser.material.color.set(0xff3030); laser.material.opacity = 0.35; }
  else if (!gun && !gazeAim()) { lEnd = target ? target.p.clone() : hand.pos.clone().addScaledVector(hand.F, 3); laser.material.color.set(target ? 0x7dffb0 : 0x7fd3ff); laser.material.opacity = 0.45; dotM.material.color.set(0xffffff); }
  if (lEnd) { const a = gun && !menu.open ? gun.mesh.localToWorld(gun.muzzle.clone()) : hand.pos.clone().addScaledVector(hand.F, 0.08); seg(laser, a, lEnd, 0.0025); laser.visible = true; } else laser.visible = false;
  // simulação
  ctxD.combat = SET.combat; ctxD.grabPoint = hold.dummy ? hold.grabPoint : null;
  items.forEach(it => { it.step(dt); if (it.gun) updateGun(it, dt); if (it.pos.y < -5) { it.pos.set(0, 2, 6); it.vel.set(0, 0, 0); } });
  for (let i = 0; i < items.length; i++) for (let j = i + 1; j < items.length; j++) { const a = items[i], b = items[j]; if (a.held || b.held) continue; const d = b.pos.clone().sub(a.pos), L = d.length(), rr2 = (a.def.shape === 'gun' ? 0.08 : a.def.r * 0.8) + (b.def.shape === 'gun' ? 0.08 : b.def.r * 0.8); if (L < rr2 && L > 1e-4) { d.multiplyScalar(1 / L); const push = (rr2 - L) / 2; a.pos.addScaledVector(d, -push); b.pos.addScaledVector(d, push); const rv = b.vel.clone().sub(a.vel).dot(d); if (rv < 0) { const ma = a.def.mass, mb = b.def.mass, imp = -1.3 * rv / (1 / ma + 1 / mb); a.vel.addScaledVector(d, -imp / ma); b.vel.addScaledVector(d, imp / mb); } } }
  dummies.forEach(dm => dm.step(dt, ctxD));
  // objetos arremessados atingem bonecos
  items.forEach(it => { if (it.held || it.vel.lengthSq() < 9) return; dummies.forEach(dm => { const j = dm.nearestJoint(it.pos); if (dm.p[j].distanceTo(it.pos) < it.def.r + 0.15) { const r = dm.hit(j, it.vel.clone().multiplyScalar(0.004 * it.def.mass), it.vel.length() * it.def.mass * 0.8, 'player'); if (dm.kind === 'human') FX.bleed(it.pos.clone(), it.vel.clone().normalize(), 0.5); else FX.dustAt(it.pos.clone(), 'dummy'); it.vel.multiplyScalar(-0.3); SND.play('dummy'); if (r === 'kill') onKill(dm); } }); });
  FX.update(dt); updateShadows();
  // vida e dano
  if (player.hurtT > 0) player.hurtT -= rdt; hurtM.material.opacity = Math.max(0, player.hurtT) * 1.3; player.hurtT = Math.max(-4, player.hurtT); if (player.hurtT <= 0) player.hurtT -= rdt * 0; player.regen = (player.regen || 0) + rdt; if (player.hurtT > 0) player.regen = 0; if (player.regen > 4) player.hp = Math.min(100, player.hp + rdt * 6);
  if (toastT > 0) toastT -= rdt;
  if (Math.floor(time * 6) !== Math.floor((time - dt) * 6)) drawHud();
  skyM.position.set(rig.position.x, 0, rig.position.z);
}
function frame(ms) { const rdt = Math.min(0.05, Math.max(0.001, (ms - last) / 1000)); last = ms; update(rdt); render(); }
function render() {
  if (mode === 'split') { const s = renderer.getSize(new THREE.Vector2()), hw = s.x >> 1; camera.aspect = hw / s.y; camera.updateProjectionMatrix(); camera.updateMatrixWorld(true); stereo.update(camera); renderer.setScissorTest(true); renderer.setViewport(0, 0, hw, s.y); renderer.setScissor(0, 0, hw, s.y); renderer.render(scene, stereo.cameraL); renderer.setViewport(hw, 0, hw, s.y); renderer.setScissor(hw, 0, hw, s.y); renderer.render(scene, stereo.cameraR); renderer.setScissorTest(false); }
  else renderer.render(scene, camera);
}
function resize() { if (renderer.xr.isPresenting) return; renderer.setSize(innerWidth, innerHeight, false); if (mode !== 'split') { camera.aspect = innerWidth / innerHeight; camera.updateProjectionMatrix(); } }
addEventListener('resize', resize); resize();

/* ---------- giroscópio / mouse / teclado ---------- */
let orient = null, alpha0 = null; const eu = new THREE.Euler(), q1 = new Q4(-Math.SQRT1_2, 0, 0, Math.SQRT1_2), q0 = new Q4(), zee = new V3(0, 0, 1);
addEventListener('deviceorientation', e => { orient = e; if ((mode === 'gyro' || mode === 'split') && e.alpha != null) { if (alpha0 === null) alpha0 = e.alpha; const a = (e.alpha - alpha0) * PI / 180, b = (e.beta || 0) * PI / 180, g = (e.gamma || 0) * PI / 180, o = ((screen.orientation && screen.orientation.angle) || window.orientation || 90) * PI / 180; eu.set(b, a, -g, 'YXZ'); camera.quaternion.setFromEuler(eu); camera.quaternion.multiply(q1); camera.quaternion.multiply(q0.setFromAxisAngle(zee, -o)); } });
canvas.addEventListener('mousedown', e => { if (mode !== 'flat' && mode !== 'gyro') return; if (document.pointerLockElement !== canvas && mode === 'flat') { try { canvas.requestPointerLock(); } catch (er) {} } if (e.button === 0) Input.mouseL = true; if (e.button === 2) Input.mouseR = true; SND.init(); });
addEventListener('mouseup', e => { if (e.button === 0) Input.mouseL = false; if (e.button === 2) Input.mouseR = false; });
canvas.addEventListener('contextmenu', e => e.preventDefault());
addEventListener('mousemove', e => { if (mode === 'flat' && document.pointerLockElement === canvas) { Input.look.yaw -= e.movementX * 0.0024; Input.look.pitch = clamp(Input.look.pitch - e.movementY * 0.0024, -1.45, 1.45); } });
canvas.addEventListener('touchstart', e => { if (mode === 'gyro' || mode === 'split') { Input.mouseL = true; setTimeout(() => Input.mouseL = false, 90); } }, { passive: true });
addEventListener('keydown', e => { Input.keys[e.key.toLowerCase()] = true; if (['tab', ' '].includes(e.key.toLowerCase())) e.preventDefault(); });
addEventListener('keyup', e => { Input.keys[e.key.toLowerCase()] = false; });

/* ---------- início ---------- */
function begin(m) {
  SND.init(); SND.set(SET.sound); SND.ambience(); mode = m; $('#overlay').classList.add('hide'); camera.fov = m === 'split' ? 90 : m === 'gyro' ? 80 : 75; resize(); camera.updateProjectionMatrix();
  rig.position.set(0, SET.height, 8); toast('Bem-vindo ao SandLab! Pegue uma arma na mesa à frente.', 4);
}
async function startXR() { try { const s = await navigator.xr.requestSession('immersive-vr', { optionalFeatures: ['local', 'local-floor'] }); renderer.xr.setReferenceSpaceType('local'); await renderer.xr.setSession(s); xrSession = s; s.addEventListener('end', () => { xrSession = null; mode = 'flat'; resize(); $('#overlay').classList.remove('hide'); }); begin('xr'); } catch (e) { $('#msg').textContent = 'Não foi possível iniciar o VR: ' + (e && e.message ? e.message : e) + ' — use o modo giroscópio.'; } }
async function startMotion(m) { try { if (typeof DeviceOrientationEvent !== 'undefined' && DeviceOrientationEvent.requestPermission) await DeviceOrientationEvent.requestPermission(); } catch (e) {} try { await document.documentElement.requestFullscreen(); } catch (e) {} try { await screen.orientation.lock('landscape'); } catch (e) {} alpha0 = null; begin(m); }
$('#btnXR').onclick = startXR; $('#btnGyro').onclick = () => startMotion('gyro'); $('#btnSplit').onclick = () => startMotion('split'); $('#btnFlat').onclick = () => begin('flat');
if (navigator.xr && navigator.xr.isSessionSupported) navigator.xr.isSessionSupported('immersive-vr').then(ok => { $('#btnXR').disabled = !ok; $('#xrNote').textContent = ok ? 'Recomendado · mão e armas seguem o controle' : 'Este navegador não liberou WebXR — use o giroscópio'; }).catch(() => $('#xrNote').textContent = 'WebXR indisponível'); else $('#xrNote').textContent = 'WebXR não encontrado — use o modo giroscópio';
if (/[?&]app=1/.test(location.search)) { $('#btnXR').style.display = 'none'; setTimeout(() => $('#btnGyro').click(), 60); }
populate(); rig.position.set(0, SET.height, 8); drawHud(); renderer.setAnimationLoop(frame);
window.__sl = { scene, camera, rig, items, dummies, hold, hand, Input, SET, update, render, renderer, spawnItem, spawnDummy, grabTarget, release, findTarget, fire, openMenu, closeMenu, setMode: m => mode = m, stats, player, FX, World };
