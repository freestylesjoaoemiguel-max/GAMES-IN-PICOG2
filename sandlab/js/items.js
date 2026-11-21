'use strict';
/* Som sintetizado, efeitos, armas e objetos com física */
const SND = (() => {
  let ctx = null, out = null, enabled = true, amb = null;
  const init = () => { if (ctx) { if (ctx.state === 'suspended') ctx.resume(); return; } const AC = window.AudioContext || window.webkitAudioContext; if (!AC) return; ctx = new AC(); out = ctx.createGain(); out.gain.value = 0.8; out.connect(ctx.destination); };
  const nbuf = d => { const n = Math.max(1, ctx.sampleRate * d | 0), b = ctx.createBuffer(1, n, ctx.sampleRate), a = b.getChannelData(0); for (let i = 0; i < n; i++) a[i] = Math.random() * 2 - 1; return b; };
  function noise(d, f, q, v, type, at, fend) { if (!ctx || !enabled) return; const t = ctx.currentTime + (at || 0), s = ctx.createBufferSource(); s.buffer = nbuf(d); const fl = ctx.createBiquadFilter(); fl.type = type || 'bandpass'; fl.frequency.setValueAtTime(f, t); if (fend) fl.frequency.exponentialRampToValueAtTime(fend, t + d); fl.Q.value = q || 1; const g = ctx.createGain(); g.gain.setValueAtTime(v, t); g.gain.exponentialRampToValueAtTime(0.0008, t + d); s.connect(fl); fl.connect(g); g.connect(out); s.start(t); }
  function tone(f, d, type, v, slide, at) { if (!ctx || !enabled) return; const t = ctx.currentTime + (at || 0), o = ctx.createOscillator(), g = ctx.createGain(); o.type = type || 'sine'; o.frequency.setValueAtTime(f, t); if (slide) o.frequency.exponentialRampToValueAtTime(Math.max(20, f * slide), t + d); g.gain.setValueAtTime(v, t); g.gain.exponentialRampToValueAtTime(0.0008, t + d); o.connect(g); g.connect(out); o.start(t); o.stop(t + d + 0.03); }
  const S = {
    pistol() { noise(0.18, 1400, 0.7, 0.9); noise(0.35, 400, 0.5, 0.5, 'lowpass'); tone(110, 0.16, 'sine', 0.7, 0.4); noise(0.5, 3000, 0.4, 0.06, 'highpass', 0.05); },
    rifle() { noise(0.12, 1700, 0.8, 0.75); noise(0.25, 500, 0.6, 0.45, 'lowpass'); tone(130, 0.1, 'sine', 0.55, 0.45); },
    shotgun() { noise(0.4, 900, 0.5, 1.0); noise(0.6, 250, 0.6, 0.7, 'lowpass'); tone(80, 0.3, 'sine', 0.9, 0.4); },
    pump() { noise(0.06, 2500, 2, 0.35, 'bandpass', 0.25); noise(0.07, 1800, 2, 0.35, 'bandpass', 0.42); },
    empty() { tone(1800, 0.03, 'square', 0.15); noise(0.03, 3000, 3, 0.2); },
    reload() { noise(0.05, 2200, 3, 0.35); noise(0.05, 1600, 3, 0.4, 'bandpass', 0.45); tone(900, 0.04, 'square', 0.12, 1, 0.85); noise(0.06, 2500, 3, 0.4, 'bandpass', 0.9); },
    concrete() { noise(0.08, 2600, 1.2, 0.25); }, ground() { noise(0.1, 700, 0.8, 0.22, 'lowpass'); },
    metal() { tone(1650 + Math.random() * 400, 0.5, 'sine', 0.18); tone(2710, 0.35, 'sine', 0.08); noise(0.04, 4000, 2, 0.15); },
    steel() { tone(980, 1.1, 'sine', 0.3); tone(1530, 0.8, 'sine', 0.15); tone(2400, 0.5, 'sine', 0.06); },
    wood(v) { tone(260 + Math.random() * 80, 0.09, 'triangle', 0.3 * (v || 1)); noise(0.06, 900, 1, 0.25 * (v || 1)); },
    paper() { noise(0.05, 3500, 1, 0.12); },
    dummy(v) { tone(140, 0.12, 'sine', 0.45 * (v || 1), 0.6); noise(0.08, 600, 1, 0.3 * (v || 1)); },
    head() { tone(500, 0.12, 'square', 0.12, 0.5); this.dummy(1.2); },
    splat() { noise(0.35, 600, 0.6, 0.6, 'lowpass', 0, 200); noise(0.2, 1800, 1, 0.25); },
    grab() { noise(0.18, 600, 2, 0.2, 'bandpass', 0, 2400); }, drop() { noise(0.12, 1200, 2, 0.12, 'bandpass', 0, 400); },
    throw() { noise(0.25, 500, 1.5, 0.25, 'bandpass', 0, 2200); },
    step() { noise(0.07, 500 + Math.random() * 300, 1, 0.12, 'lowpass'); },
    punch() { tone(90, 0.2, 'sine', 0.8, 0.5); noise(0.1, 800, 1, 0.4); },
    hitPunch(v) { tone(70 + Math.random() * 30, 0.16, 'sine', 0.9 * (v || 1), 0.45); noise(0.07, 1500, 0.8, 0.5 * (v || 1)); noise(0.12, 300, 0.7, 0.4 * (v || 1), 'lowpass'); },
    flesh(v) { tone(110, 0.1, 'sine', 0.5 * (v || 1), 0.5); noise(0.09, 900, 0.7, 0.35 * (v || 1), 'bandpass', 0, 300); },
    swing() { noise(0.14, 900, 1.2, 0.12, 'bandpass', 0, 2600); },
    grunt(p) { tone(150 * (p || 1), 0.18, 'sawtooth', 0.06, 0.7); noise(0.15, 500 * (p || 1), 2, 0.08); },
    click() { tone(800, 0.05, 'triangle', 0.2, 1.4); }, ui() { tone(1300, 0.03, 'sine', 0.06); },
    spawn() { tone(500, 0.2, 'sine', 0.2, 2); tone(900, 0.2, 'sine', 0.1, 1.5, 0.08); }
  };
  function ambience() {
    if (!ctx || amb) return; const s = ctx.createBufferSource(); s.buffer = nbuf(4); s.loop = true; const f = ctx.createBiquadFilter(); f.type = 'lowpass'; f.frequency.value = 450; const g = ctx.createGain(); g.gain.value = 0.05; const l = ctx.createOscillator(), lg = ctx.createGain(); l.frequency.value = 0.08; lg.gain.value = 0.03; l.connect(lg); lg.connect(g.gain); l.start(); s.connect(f); f.connect(g); g.connect(out); s.start(); amb = s;
    const bird = () => { if (enabled) { const b = 2400 + Math.random() * 1500; for (let i = 0; i < 2 + Math.random() * 3; i++) tone(b, 0.07, 'sine', 0.04, 1.35, i * 0.1); } setTimeout(bird, 3000 + Math.random() * 7000); }; setTimeout(bird, 2000);
  }
  return { init, play(n, a) { if (ctx && enabled && S[n]) S[n](a); }, ambience, set(v) { enabled = v; if (out) out.gain.value = v ? 0.8 : 0; }, get on() { return enabled; } };
})();

/* ---------- Efeitos (pools com poucos draw calls) ---------- */
const FX = (() => {
  let scene; const blood = { n: 90, i: 0, pools: [] }; const decals = { n: 140, i: 0 }, sparks = { n: 160, i: 0 }, chunks = { n: 160, i: 0 }, casings = { n: 30, i: 0, list: [] }, tracers = [];
  const m4 = new THREE.Matrix4(), q = new THREE.Quaternion(), s3 = new THREE.Vector3(), Z = new THREE.Vector3(0, 0, 1);
  function init(sc) {
    scene = sc;
    blood.mesh = new THREE.InstancedMesh(new THREE.PlaneGeometry(1, 1), new THREE.MeshLambertMaterial({ map: TEX.blood(), transparent: true, depthWrite: false, polygonOffset: true, polygonOffsetFactor: -3 }), blood.n); blood.mesh.frustumCulled = false;
    m4.makeScale(0, 0, 0); for (let i = 0; i < blood.n; i++) blood.mesh.setMatrixAt(i, m4); scene.add(blood.mesh);
    decals.mesh = new THREE.InstancedMesh(new THREE.PlaneGeometry(1, 1), new THREE.MeshBasicMaterial({ map: TEX.hole(), transparent: true, depthWrite: false, polygonOffset: true, polygonOffsetFactor: -2 }), decals.n); decals.mesh.frustumCulled = false;
    m4.makeScale(0, 0, 0); for (let i = 0; i < decals.n; i++) decals.mesh.setMatrixAt(i, m4); scene.add(decals.mesh);
    const pts = (P, n, size, color, add) => { P.pos = new Float32Array(n * 3).fill(-999); P.vel = new Float32Array(n * 3); P.life = new Float32Array(n); const g = new THREE.BufferGeometry(); g.setAttribute('position', new THREE.BufferAttribute(P.pos, 3)); if (!add) { P.col = new Float32Array(n * 3); g.setAttribute('color', new THREE.BufferAttribute(P.col, 3)); } P.mesh = new THREE.Points(g, new THREE.PointsMaterial({ size, map: TEX.dot(), color, transparent: true, depthWrite: false, blending: add ? THREE.AdditiveBlending : THREE.NormalBlending, vertexColors: !add })); P.mesh.frustumCulled = false; scene.add(P.mesh); };
    pts(sparks, sparks.n, 0.05, 0xffd27a, true); pts(chunks, chunks.n, 0.07, 0xffffff, false);
    casings.mesh = new THREE.InstancedMesh(new THREE.CylinderGeometry(0.006, 0.006, 0.022, 6), new THREE.MeshPhongMaterial({ color: 0xc9a13b, shininess: 80, specular: 0xffffff }), casings.n); casings.mesh.frustumCulled = false; for (let i = 0; i < casings.n; i++) { casings.list.push({ p: new THREE.Vector3(0, -99, 0), v: new THREE.Vector3(), r: new THREE.Quaternion(), w: Math.random() * 20, life: 0 }); } scene.add(casings.mesh);
    for (let i = 0; i < 8; i++) { const m = new THREE.Mesh(new THREE.CylinderGeometry(1, 1, 1, 5, 1, true), new THREE.MeshBasicMaterial({ color: 0xffe6a0, transparent: true, opacity: 0.7, blending: THREE.AdditiveBlending, depthWrite: false })); m.visible = false; scene.add(m); tracers.push({ m, life: 0 }); }
  }
  function decal(p, n, size) { q.setFromUnitVectors(Z, n); m4.compose(s3.copy(p).addScaledVector(n, 0.004), q, new THREE.Vector3(size, size, size)); decals.mesh.setMatrixAt(decals.i, m4); decals.mesh.instanceMatrix.needsUpdate = true; decals.i = (decals.i + 1) % decals.n; }
  function burst(P, p, n, speed, life, col, up) { for (let k = 0; k < n; k++) { const i = P.i; P.i = (P.i + 1) % P.n; P.pos[i * 3] = p.x; P.pos[i * 3 + 1] = p.y; P.pos[i * 3 + 2] = p.z; P.vel[i * 3] = (Math.random() - 0.5) * speed; P.vel[i * 3 + 1] = Math.random() * speed * (up || 0.8); P.vel[i * 3 + 2] = (Math.random() - 0.5) * speed; P.life[i] = life * (0.5 + Math.random() * 0.5); if (P.col && col) { const c = col[(Math.random() * col.length) | 0]; P.col[i * 3] = c.r; P.col[i * 3 + 1] = c.g; P.col[i * 3 + 2] = c.b; } } if (P.col) P.mesh.geometry.attributes.color.needsUpdate = true; }
  const sparkAt = (p, n) => burst(sparks, p, n || 10, 4, 0.35);
  const C = h => new THREE.Color(h);
  const dustAt = (p, kind) => burst(chunks, p, 8, 1.6, 0.6, kind === 'wood' ? [C(0x8a5a2b), C(0xb98a55)] : kind === 'ground' ? [C(0x6b5a3a), C(0x4f7a34)] : kind === 'dummy' ? [C(0xf2c31b), C(0x222222)] : [C(0xb5b0a8), C(0x8c8a84)], 0.7);
  const splat = p => burst(chunks, p, 50, 5, 1.2, [C(0xd61f2b), C(0xff4757), C(0x2e7d32), C(0x1b5e20), C(0xf5e6c8)], 0.9);
  const BLOODC = [C(0x8a0a10), C(0xb3121b), C(0x5c0508), C(0xcc1f26)];
  function bloodDecal(p, n, size, rot) { q.setFromUnitVectors(Z, n); q.multiply(new THREE.Quaternion().setFromAxisAngle(Z, rot === undefined ? Math.random() * 6.28 : rot)); m4.compose(s3.copy(p).addScaledVector(n, 0.006), q, new THREE.Vector3(size, size, size)); const i = blood.i; blood.mesh.setMatrixAt(i, m4); blood.mesh.instanceMatrix.needsUpdate = true; blood.i = (blood.i + 1) % blood.n; blood.pools = blood.pools.filter(pl => pl.i !== i); return i; }
  function bleed(p, dir, amount) { // respingo: partículas + mancha na parede atrás + mancha no chão
    burst(chunks, p, Math.round(26 * amount), 3.2, 0.9, BLOODC, 0.5);
    for (let k = 0; k < 6 * amount; k++) { const i = chunks.i - 1 - k; if (i >= 0) { chunks.vel[i * 3] += dir.x * 3; chunks.vel[i * 3 + 1] += dir.y * 2; chunks.vel[i * 3 + 2] += dir.z * 3; } }
    const back = World.raycast(p.clone().addScaledVector(dir, 0.05), dir, 3.5); if (back && back.t < 3.5) bloodDecal(p.clone().addScaledVector(dir, 0.05 + back.t), back.n, 0.35 + Math.random() * 0.4 * amount);
    const fl = World.floorAt(p.x, p.z, p.y); bloodDecal(new THREE.Vector3(p.x + dir.x * 0.6 + (Math.random() - 0.5) * 0.4, fl, p.z + dir.z * 0.6 + (Math.random() - 0.5) * 0.4), new THREE.Vector3(0, 1, 0), 0.25 + Math.random() * 0.35 * amount);
  }
  function pool(p) { const fl = World.floorAt(p.x, p.z, p.y + 0.3); const pp = new THREE.Vector3(p.x, fl, p.z); const i = bloodDecal(pp, new THREE.Vector3(0, 1, 0), 0.05); blood.pools.push({ i, p: pp, s: 0.05, max: 1.0 + Math.random() * 0.4, rot: Math.random() * 6.28 }); }
  function casing(p, v) { const c = casings.list[casings.i]; casings.i = (casings.i + 1) % casings.n; c.p.copy(p); c.v.copy(v); c.life = 4; }
  function tracer(a, b) { const t = tracers.find(x => x.life <= 0) || tracers[0]; const d = s3.subVectors(b, a), L = d.length(); t.m.position.copy(a).addScaledVector(d, 0.5); t.m.quaternion.setFromUnitVectors(new THREE.Vector3(0, 1, 0), d.normalize()); t.m.scale.set(0.006, L, 0.006); t.m.visible = true; t.life = 0.05; }
  function update(dt) {
    if (blood.pools.length) { blood.pools.forEach(pl => { if (pl.s < pl.max) { pl.s += dt * 0.28; q.setFromUnitVectors(Z, new THREE.Vector3(0, 1, 0)); q.multiply(new THREE.Quaternion().setFromAxisAngle(Z, pl.rot)); s3.copy(pl.p); s3.y += 0.007; m4.compose(s3, q, new THREE.Vector3(pl.s, pl.s, pl.s)); blood.mesh.setMatrixAt(pl.i, m4); } }); blood.mesh.instanceMatrix.needsUpdate = true; blood.pools = blood.pools.filter(pl => pl.s < pl.max); }
    for (const P of [sparks, chunks]) { for (let i = 0; i < P.n; i++) { if (P.life[i] <= 0) continue; P.life[i] -= dt; P.vel[i * 3 + 1] -= 9.8 * dt; P.pos[i * 3] += P.vel[i * 3] * dt; P.pos[i * 3 + 1] += P.vel[i * 3 + 1] * dt; P.pos[i * 3 + 2] += P.vel[i * 3 + 2] * dt; if (P.pos[i * 3 + 1] < 0.02) { P.pos[i * 3 + 1] = 0.02; P.vel[i * 3] *= 0.5; P.vel[i * 3 + 2] *= 0.5; P.vel[i * 3 + 1] = 0; } if (P.life[i] <= 0) P.pos[i * 3 + 1] = -999; } P.mesh.geometry.attributes.position.needsUpdate = true; }
    casings.list.forEach((c, i) => { if (c.life > 0) { c.life -= dt; c.v.y -= 9.8 * dt; c.p.addScaledVector(c.v, dt); if (c.p.y < 0.006) { c.p.y = 0.006; c.v.y = Math.abs(c.v.y) * 0.4; c.v.x *= 0.6; c.v.z *= 0.6; if (Math.abs(c.v.y) > 0.4) SND.play('metal'); } c.w *= 0.98; q.setFromAxisAngle(new THREE.Vector3(1, 0, 0.3).normalize(), c.life * c.w); } m4.compose(c.life > 0 ? c.p : s3.set(0, -99, 0), q, new THREE.Vector3(1, 1, 1)); casings.mesh.setMatrixAt(i, m4); }); casings.mesh.instanceMatrix.needsUpdate = true;
    tracers.forEach(t => { if (t.life > 0) { t.life -= dt; if (t.life <= 0) t.m.visible = false; } });
  }
  return { init, decal, sparkAt, dustAt, splat, casing, tracer, update, bleed, pool };
})();

/* ---------- Modelos de armas (peça por peça) ---------- */
const MATS = {};
const mat = (c, o) => { const k = c + JSON.stringify(o || {}); return MATS[k] || (MATS[k] = new THREE.MeshPhongMaterial(Object.assign({ color: c, shininess: 30, specular: 0x333333 }, o))); };
const STEEL = () => mat(0x2b2e33, { shininess: 90, specular: 0x9aa4b0 }), POLY = () => mat(0x1d1f22, { shininess: 15, specular: 0x222222 }), TAN = () => mat(0x8b7355, { shininess: 10 }), WOODM = () => new THREE.MeshPhongMaterial({ map: TEX.wood(), shininess: 25 });
function part(g, geo, m, x, y, z, sx, sy, sz, rx) { const o = new THREE.Mesh(geo, m); o.position.set(x, y, z); o.scale.set(sx, sy, sz); if (rx) o.rotation.x = rx; g.add(o); return o; }
const BX = new THREE.BoxGeometry(1, 1, 1), CY = new THREE.CylinderGeometry(1, 1, 1, 14, 1);
function mergeGroup(g) { // junta peças da mesma material em 1 malha (menos draw calls)
  const byMat = new Map(); g.updateMatrixWorld(true);
  g.children.slice().forEach(ch => { if (!ch.isMesh || ch.userData.keep) return; const geo = ch.geometry.clone(); geo.applyMatrix4(ch.matrix); const ng = geo.index ? geo.toNonIndexed() : geo; (byMat.get(ch.material) || byMat.set(ch.material, []).get(ch.material)).push(ng); g.remove(ch); });
  byMat.forEach((list, m) => { const P = [], N = [], U = []; list.forEach(ge => { P.push(...ge.attributes.position.array); N.push(...ge.attributes.normal.array); if (ge.attributes.uv) U.push(...ge.attributes.uv.array); else for (let i = 0; i < ge.attributes.position.count; i++) U.push(0, 0); }); const bg = new THREE.BufferGeometry(); bg.setAttribute('position', new THREE.Float32BufferAttribute(P, 3)); bg.setAttribute('normal', new THREE.Float32BufferAttribute(N, 3)); bg.setAttribute('uv', new THREE.Float32BufferAttribute(U, 2)); g.add(new THREE.Mesh(bg, m)); });
}
function buildGun(type) {
  const g = new THREE.Group(); let muzzle, eject;
  if (type === 'pistol') {
    part(g, BX, STEEL(), 0, 0.045, -0.06, 0.03, 0.032, 0.19); // ferrolho
    for (let i = 0; i < 6; i++) part(g, BX, POLY(), 0, 0.045, 0.015 - i * 0.006, 0.031, 0.026, 0.002);
    part(g, BX, POLY(), 0, 0.02, -0.055, 0.028, 0.022, 0.17); part(g, CY, STEEL(), 0, 0.048, -0.157, 0.006, 0.012, 0.006, PI / 2);
    const gr = part(g, BX, POLY(), 0, -0.035, 0.015, 0.03, 0.1, 0.045); gr.rotation.x = -0.25;
    part(g, BX, POLY(), 0, -0.002, -0.035, 0.008, 0.03, 0.04); part(g, BX, STEEL(), 0, 0.065, -0.14, 0.004, 0.008, 0.006); part(g, BX, STEEL(), 0, 0.065, 0.02, 0.018, 0.008, 0.006);
    muzzle = new THREE.Vector3(0, 0.048, -0.17); eject = new THREE.Vector3(0.02, 0.06, -0.04);
  } else if (type === 'rifle') {
    part(g, BX, POLY(), 0, 0.03, -0.05, 0.04, 0.06, 0.32); part(g, BX, STEEL(), 0, 0.068, -0.05, 0.026, 0.02, 0.3);
    part(g, CY, STEEL(), 0, 0.035, -0.37, 0.011, 0.34, 0.011, PI / 2); part(g, CY, POLY(), 0, 0.035, -0.29, 0.024, 0.2, 0.024, PI / 2);
    for (let i = 0; i < 5; i++) part(g, BX, STEEL(), 0, 0.06, -0.22 - i * 0.03, 0.05, 0.004, 0.012);
    part(g, CY, STEEL(), 0, 0.035, -0.555, 0.016, 0.05, 0.016, PI / 2); // quebra-chamas
    const mag = part(g, BX, POLY(), 0, -0.05, -0.08, 0.025, 0.12, 0.06); mag.rotation.x = 0.2;
    const gr = part(g, BX, POLY(), 0, -0.04, 0.05, 0.03, 0.09, 0.04); gr.rotation.x = -0.3;
    part(g, BX, POLY(), 0, 0.03, 0.2, 0.035, 0.07, 0.2); part(g, BX, POLY(), 0, 0.0, 0.29, 0.04, 0.12, 0.03);
    part(g, BX, mat(0x111111), 0, 0.098, -0.03, 0.03, 0.04, 0.09); part(g, CY, mat(0x3a7bd5, { shininess: 120, specular: 0xffffff, transparent: true, opacity: 0.6 }), 0, 0.1, -0.03, 0.012, 0.092, 0.012, PI / 2); // mira holográfica
    muzzle = new THREE.Vector3(0, 0.035, -0.585); eject = new THREE.Vector3(0.025, 0.05, -0.02);
  } else {
    part(g, CY, STEEL(), 0, 0.045, -0.3, 0.016, 0.6, 0.016, PI / 2); part(g, CY, STEEL(), 0, 0.015, -0.27, 0.013, 0.5, 0.013, PI / 2);
    const pump = part(g, CY, WOODM(), 0, 0.018, -0.24, 0.026, 0.16, 0.026, PI / 2); pump.userData.keep = true; pump.name = 'pump';
    part(g, BX, STEEL(), 0, 0.035, 0.03, 0.04, 0.06, 0.16);
    const st = part(g, BX, WOODM(), 0, 0.0, 0.22, 0.04, 0.08, 0.24); st.rotation.x = 0.12; const gr = part(g, BX, WOODM(), 0, -0.03, 0.08, 0.034, 0.08, 0.05); gr.rotation.x = -0.35;
    part(g, CY, STEEL(), 0, 0.063, -0.59, 0.003, 0.01, 0.003);
    muzzle = new THREE.Vector3(0, 0.045, -0.6); eject = new THREE.Vector3(0.03, 0.05, 0.02);
  }
  mergeGroup(g);
  const flash = new THREE.Sprite(new THREE.SpriteMaterial({ map: TEX.flash(), transparent: true, blending: THREE.AdditiveBlending, depthWrite: false })); flash.scale.set(0.22, 0.22, 1); flash.position.copy(muzzle).add(new THREE.Vector3(0, 0, -0.06)); flash.visible = false; g.add(flash);
  // munição (canvas)
  const ac = document.createElement('canvas'); ac.width = 128; ac.height = 48; const at = new THREE.CanvasTexture(ac); at.colorSpace = THREE.SRGBColorSpace;
  const ammo = new THREE.Sprite(new THREE.SpriteMaterial({ map: at, transparent: true, depthTest: false })); ammo.scale.set(0.09, 0.034, 1); ammo.position.set(0.05, 0.11, 0.02); ammo.renderOrder = 30; ammo.visible = false; g.add(ammo);
  return { group: g, muzzle, eject, flash, ammo, ammoCtx: ac.getContext('2d'), ammoTex: at, pump: g.getObjectByName('pump') };
}
const GUNS = {
  pistol: { name: 'Pistola', auto: false, rate: 0.13, mag: 15, dmg: 34, pellets: 1, spread: 0.004, recoil: 0.07, snd: 'pistol', r: 0.14, force: 3 },
  rifle: { name: 'Fuzil', auto: true, rate: 0.09, mag: 30, dmg: 26, pellets: 1, spread: 0.012, recoil: 0.04, snd: 'rifle', r: 0.3, force: 4 },
  shotgun: { name: 'Escopeta', auto: false, rate: 0.8, mag: 6, dmg: 14, pellets: 9, spread: 0.055, recoil: 0.16, snd: 'shotgun', r: 0.32, force: 2.5 }
};
const PROPS = {
  crate: { name: 'Caixa', r: 0.3, half: 0.25, mass: 6, bounce: 0.2, snd: 'wood', shape: 'box' },
  barrel: { name: 'Barril', r: 0.42, half: 0.44, mass: 14, bounce: 0.15, snd: 'metal', shape: 'cyl' },
  ball: { name: 'Bola', r: 0.16, half: 0.16, mass: 0.6, bounce: 0.72, snd: 'ground', shape: 'sph' },
  melon: { name: 'Melancia', r: 0.15, half: 0.13, mass: 3, bounce: 0.25, snd: 'dummy', shape: 'sph', splat: true },
  cone: { name: 'Cone', r: 0.2, half: 0.25, mass: 1, bounce: 0.3, snd: 'ground', shape: 'cone' }
};
function buildProp(type) {
  const g = new THREE.Group();
  if (type === 'crate') { const geo = new THREE.BoxGeometry(0.5, 0.5, 0.5); g.add(new THREE.Mesh(geo, new THREE.MeshLambertMaterial({ map: TEX.crate() }))); }
  if (type === 'barrel') { const m = new THREE.Mesh(new THREE.CylinderGeometry(0.3, 0.3, 0.88, 18), mat(0x1f5aa0, { shininess: 50 })); g.add(m); [-0.28, 0, 0.28].forEach(y => { const r = new THREE.Mesh(new THREE.TorusGeometry(0.305, 0.015, 6, 20), mat(0x183f70, { shininess: 60 })); r.rotation.x = PI / 2; r.position.y = y; g.add(r); }); const lid = new THREE.Mesh(new THREE.CircleGeometry(0.29, 18), mat(0x2a6ab4)); lid.rotation.x = -PI / 2; lid.position.y = 0.441; g.add(lid); mergeGroup(g); }
  if (type === 'ball') { const c = document.createElement('canvas'); c.width = 256; c.height = 128; const x = c.getContext('2d'); x.fillStyle = '#e8eaed'; x.fillRect(0, 0, 256, 128); x.fillStyle = '#d32f2f'; for (let i = 0; i < 8; i++) x.fillRect(i * 32, 0, 14, 128); const t = new THREE.CanvasTexture(c); t.colorSpace = THREE.SRGBColorSpace; g.add(new THREE.Mesh(new THREE.SphereGeometry(0.16, 20, 14), new THREE.MeshPhongMaterial({ map: t, shininess: 70 }))); }
  if (type === 'melon') { const c = document.createElement('canvas'); c.width = 256; c.height = 128; const x = c.getContext('2d'); x.fillStyle = '#2f7d32'; x.fillRect(0, 0, 256, 128); for (let i = 0; i < 16; i++) { x.fillStyle = i % 2 ? '#1b4d1e' : '#4c9a3c'; x.beginPath(); x.moveTo(i * 16, 0); x.quadraticCurveTo(i * 16 + 10, 64, i * 16, 128); x.lineTo(i * 16 + 8, 128); x.quadraticCurveTo(i * 16 + 18, 64, i * 16 + 8, 0); x.fill(); } const t = new THREE.CanvasTexture(c); t.colorSpace = THREE.SRGBColorSpace; const m = new THREE.Mesh(new THREE.SphereGeometry(0.15, 18, 12), new THREE.MeshPhongMaterial({ map: t, shininess: 60 })); m.scale.set(1.15, 0.9, 0.9); g.add(m); }
  if (type === 'cone') { g.add(new THREE.Mesh(new THREE.CylinderGeometry(0.03, 0.15, 0.48, 14), mat(0xff6d00, { shininess: 40 }))); const s = new THREE.Mesh(new THREE.CylinderGeometry(0.075, 0.105, 0.09, 14), mat(0xffffff)); s.position.y = 0.02; g.add(s); const b = new THREE.Mesh(new THREE.BoxGeometry(0.34, 0.03, 0.34), mat(0xe65100)); b.position.y = -0.235; g.add(b); mergeGroup(g); }
  return g;
}

class Item {
  constructor(scene, type, x, y, z) {
    this.type = type; this.gun = GUNS[type] || null; this.def = this.gun ? { r: this.gun.r, half: 0.04, mass: 1.5, bounce: 0.25, snd: 'metal', shape: 'gun' } : PROPS[type];
    if (this.gun) { const b = buildGun(type); Object.assign(this, b); this.mesh = b.group; this.ammoN = this.gun.mag; this.cool = 0; this.reloadT = 0; this.kick = 0; this.drawAmmo(); }
    else this.mesh = buildProp(type);
    this.pos = this.mesh.position.set(x, y, z); this.vel = new THREE.Vector3(); this.ang = new THREE.Vector3(); this.quat = this.mesh.quaternion; this.held = false; this.rest = 0; this.alive = true;
    if (this.gun) this.quat.setFromEuler(new THREE.Euler(0, Math.random() * 0.4, PI / 2));
    scene.add(this.mesh);
  }
  drawAmmo() { const c = this.ammoCtx; c.clearRect(0, 0, 128, 48); c.fillStyle = 'rgba(10,14,24,.75)'; c.fillRect(0, 0, 128, 48); c.fillStyle = this.reloadT > 0 ? '#ffd23f' : this.ammoN ? '#7dffb0' : '#ff6b6b'; c.font = '700 34px monospace'; c.textAlign = 'center'; c.textBaseline = 'middle'; c.fillText(this.reloadT > 0 ? 'RECARR.' : this.ammoN + '/' + this.gun.mag, 64, 26); this.ammoTex.needsUpdate = true; }
  floorH() { const d = this.def; if (d.shape === 'cyl') { const up = new THREE.Vector3(0, 1, 0).applyQuaternion(this.quat); return Math.abs(up.y) > 0.7 ? 0.44 : 0.3; } if (d.shape === 'gun') return 0.03; return d.half; }
  step(dt) {
    if (this.held) return;
    const d = this.def, v = this.vel; v.y -= 9.8 * dt; this.pos.addScaledVector(v, dt);
    const fl = World.floorAt(this.pos.x, this.pos.z, this.pos.y), h = this.floorH();
    let onG = false;
    if (this.pos.y - h <= fl) { this.pos.y = fl + h; onG = true; if (v.y < -1.6) { SND.play(d.snd, Math.min(1, -v.y / 6)); } if (v.y < 0) v.y = -v.y * d.bounce; if (Math.abs(v.y) < 0.4) v.y = 0; const fr = d.shape === 'sph' ? 0.995 : 0.86; v.x *= fr; v.z *= fr; }
    const q = { x: this.pos.x, y: this.pos.y - h, z: this.pos.z }; World.collide(q, d.r * 0.85, h * 2); if (q.x !== this.pos.x) { v.x = -v.x * 0.4; this.pos.x = q.x; } if (q.z !== this.pos.z) { v.z = -v.z * 0.4; this.pos.z = q.z; }
    // rotação
    if (d.shape === 'sph' && onG) { this.ang.set(v.z, 0, -v.x).multiplyScalar(1 / d.r); }
    else if (onG) { this.ang.multiplyScalar(0.8); }
    const w = this.ang.length(); if (w > 1e-4) { const dq = new THREE.Quaternion().setFromAxisAngle(this.ang.clone().multiplyScalar(1 / w), w * dt); this.quat.premultiply(dq); }
    if (onG && d.shape !== 'sph' && v.lengthSq() < 0.5) { // assenta numa face
      const axes = [new THREE.Vector3(1, 0, 0), new THREE.Vector3(0, 1, 0), new THREE.Vector3(0, 0, 1)].map(a => a.applyQuaternion(this.quat)); let best = axes[0], bd = 0;
      const cand = d.shape === 'gun' ? [axes[0]] : d.shape === 'cyl' ? [axes[1], axes[0], axes[2]] : d.shape === 'cone' ? [axes[1]] : axes;
      cand.forEach(a => { if (Math.abs(a.y) > bd) { bd = Math.abs(a.y); best = a; } }); const tq = new THREE.Quaternion().setFromUnitVectors(best.clone().multiplyScalar(Math.sign(best.y) || 1), new THREE.Vector3(0, 1, 0)).multiply(this.quat);
      if (d.shape === 'cyl' && bd < 0.7) { /* deitado: ok */ } this.quat.slerp(tq, Math.min(1, dt * 6));
    }
    this.rest = onG && v.lengthSq() < 0.01 ? this.rest + dt : 0;
  }
}
