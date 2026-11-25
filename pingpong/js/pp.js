'use strict';
/* PING-PONG VR — WebXR para Pico G2 */
const V = THREE.Vector3, Q = THREE.Quaternion, EYE = 1.6, PI = Math.PI;
const $ = s => document.querySelector(s);
const SET = Object.assign({ diff: 1, hand: 'auto', height: 1.6, invert: false, sound: true, win: 11 }, (() => { try { return JSON.parse(localStorage.getItem('pp_set') || '{}'); } catch (e) { return {}; } })());
const saveSet = () => { try { localStorage.setItem('pp_set', JSON.stringify(SET)); } catch (e) {} };

/* ---------- Som (sintetizado) ---------- */
const Snd = (() => {
  let ctx = null, g = null, murm = null, murmG = null;
  function init() { if (ctx) { if (ctx.state === 'suspended') ctx.resume(); return; } const AC = window.AudioContext || window.webkitAudioContext; if (!AC) return; ctx = new AC(); g = ctx.createGain(); g.gain.value = 0.7; g.connect(ctx.destination); }
  function tone(f, d, type, v, slide, at) { if (!ctx || !SET.sound) return; const t = ctx.currentTime + (at || 0), o = ctx.createOscillator(), gg = ctx.createGain(); o.type = type || 'sine'; o.frequency.setValueAtTime(f, t); if (slide) o.frequency.exponentialRampToValueAtTime(Math.max(30, f * slide), t + d); gg.gain.setValueAtTime(v, t); gg.gain.exponentialRampToValueAtTime(0.0001, t + d); o.connect(gg); gg.connect(g); o.start(t); o.stop(t + d + 0.02); }
  function noise(d, f, v) { if (!ctx || !SET.sound) return; const n = ctx.sampleRate * d | 0, b = ctx.createBuffer(1, n, ctx.sampleRate), a = b.getChannelData(0); for (let i = 0; i < n; i++) a[i] = (Math.random() * 2 - 1) * (1 - i / n); const s = ctx.createBufferSource(); s.buffer = b; const fl = ctx.createBiquadFilter(); fl.type = 'bandpass'; fl.frequency.value = f; const gg = ctx.createGain(); gg.gain.value = v; s.connect(fl); fl.connect(gg); gg.connect(g); s.start(); }
  return {
    init,
    paddle(sp) { const k = clamp(sp / 12, 0.2, 1); noise(0.05, 2400, 0.5 * k); tone(1100 + 500 * k, 0.07, 'triangle', 0.35 * k + 0.1, 0.6); },
    table(sp) { const k = clamp(sp / 8, 0.15, 1); tone(520, 0.06, 'sine', 0.3 * k + 0.05, 0.7); noise(0.03, 1600, 0.2 * k); },
    net() { tone(180, 0.09, 'sine', 0.2, 0.7); noise(0.06, 700, 0.15); },
    floor() { tone(300, 0.05, 'sine', 0.12, 0.6); },
    point(win) { [523, 659, 784].forEach((f, i) => tone(f * (win ? 1 : 0.6), 0.18, 'triangle', 0.22, 1, i * 0.09)); },
    fanfare() { [523, 659, 784, 1047, 784, 1047, 1319].forEach((f, i) => tone(f, 0.25, 'square', 0.12, 1, i * 0.12)); },
    click() { tone(700, 0.06, 'triangle', 0.25, 1.4); },
    murmur() { if (!ctx || murm) return; const n = ctx.sampleRate * 3 | 0, b = ctx.createBuffer(1, n, ctx.sampleRate), a = b.getChannelData(0); let l = 0; for (let i = 0; i < n; i++) { l = l * 0.97 + (Math.random() * 2 - 1) * 0.03; a[i] = l * 3; } murm = ctx.createBufferSource(); murm.buffer = b; murm.loop = true; const f = ctx.createBiquadFilter(); f.type = 'bandpass'; f.frequency.value = 650; f.Q.value = 0.6; murmG = ctx.createGain(); murmG.gain.value = SET.sound ? 0.16 : 0; murm.connect(f); f.connect(murmG); murmG.connect(g); murm.start(); },
    cheer(big) { if (!ctx || !SET.sound) return; const d = big ? 3.2 : 1.8, n = ctx.sampleRate * d | 0, b = ctx.createBuffer(1, n, ctx.sampleRate), a = b.getChannelData(0); for (let i = 0; i < n; i++) { const e = Math.sin(Math.PI * i / n); a[i] = (Math.random() * 2 - 1) * e; } const s = ctx.createBufferSource(); s.buffer = b; const f = ctx.createBiquadFilter(); f.type = 'bandpass'; f.frequency.value = 1500; f.Q.value = 0.5; const gg = ctx.createGain(); gg.gain.value = big ? 0.55 : 0.4; s.connect(f); f.connect(gg); gg.connect(g); s.start(); },
    clap() { if (!ctx || !SET.sound) return; for (let i = 0; i < 26; i++) setTimeout(() => noise(0.03, 2500 + Math.random() * 1500, 0.12), i * 55 + Math.random() * 25); },
    serve() { tone(400, 0.12, 'sine', 0.2, 2.2); }
  };
})();

/* ---------- Cena ---------- */
const canvas = $('#view');
const renderer = new THREE.WebGLRenderer({ canvas, antialias: true, powerPreference: 'high-performance' });
renderer.setPixelRatio(Math.min(devicePixelRatio || 1, 2)); renderer.xr.enabled = true; try { renderer.xr.setReferenceSpaceType('local'); renderer.xr.setFoveation(0.6); } catch (e) {}
const scene = new THREE.Scene(); scene.background = new THREE.Color(0x0d1224);
const camera = new THREE.PerspectiveCamera(75, 1, 0.03, 60), rig = new THREE.Group(); rig.add(camera); scene.add(rig);
const stereo = new THREE.StereoCamera();
scene.add(new THREE.HemisphereLight(0xdfe9ff, 0x2a2f45, 1.35)); const sun = new THREE.DirectionalLight(0xfff4e0, 2.2); sun.position.set(1.5, 6, 2); scene.add(sun);
const mat = (c, o) => new THREE.MeshLambertMaterial(Object.assign({ color: c }, o)), basic = (c, o) => new THREE.MeshBasicMaterial(Object.assign({ color: c }, o));
const BOX = new THREE.BoxGeometry(1, 1, 1), CYL = new THREE.CylinderGeometry(1, 1, 1, 20), SPH = new THREE.SphereGeometry(1, 20, 14);
function mesh(g, m, x, y, z, sx, sy, sz, parent) { const o = new THREE.Mesh(g, m); o.position.set(x, y, z); o.scale.set(sx, sy, sz); (parent || scene).add(o); return o; }
function ctex(w, h, draw, rep) { const c = document.createElement('canvas'); c.width = w; c.height = h; draw(c.getContext('2d'), w, h); const t = new THREE.CanvasTexture(c); t.colorSpace = THREE.SRGBColorSpace; t.anisotropy = 4; if (rep) { t.wrapS = t.wrapT = THREE.RepeatWrapping; t.repeat.set(rep[0], rep[1]); } return t; }

scene.fog = new THREE.Fog(0x070a14, 16, 34); scene.background = new THREE.Color(0x070a14);

// mesa
const table = new THREE.Group(); scene.add(table);
(function buildTable() {
  const top = ctex(1024, 560, (x, w, h) => { x.fillStyle = '#17508a'; x.fillRect(0, 0, w, h); x.strokeStyle = '#fff'; x.lineWidth = 7; x.strokeRect(4, 4, w - 8, h - 8); x.lineWidth = 4; x.beginPath(); x.moveTo(w / 2, 4); x.lineTo(w / 2, h - 4); x.stroke(); x.moveTo(4, h / 2); x.lineTo(w, h / 2); x.lineWidth = 2; x.stroke(); });
  const t = mesh(BOX, [mat(0x0f3a66), mat(0x0f3a66), mat(0xffffff, { map: top }), mat(0x0f3a66), mat(0x0f3a66), mat(0x0f3a66)], 0, TBL.H - 0.0075, 0, TBL.W, 0.03, TBL.L, table);
  // textura só no topo: BoxGeometry face 2 = +Y; aponta uv correto (largura x, comprimento z)
  t.material[2].map.rotation = PI / 2; t.material[2].map.center.set(0.5, 0.5);
  mesh(BOX, mat(0xdde3ea), 0, TBL.H - 0.04, 0, TBL.W - 0.1, 0.05, TBL.L - 0.1, table);
  [[-1, -1], [1, -1], [-1, 1], [1, 1]].forEach(p => mesh(BOX, mat(0x2b2f3a), p[0] * (TBL.W / 2 - 0.1), TBL.H / 2 - 0.03, p[1] * (TBL.L / 2 - 0.2), 0.06, TBL.H - 0.06, 0.06, table));
  [-1, 1].forEach(s => mesh(BOX, mat(0x2b2f3a), 0, 0.12, s * (TBL.L / 2 - 0.2), TBL.W - 0.1, 0.04, 0.05, table));
  // rede
  const netT = ctex(256, 64, (x, w, h) => { x.clearRect(0, 0, w, h); x.strokeStyle = 'rgba(30,30,30,.85)'; x.lineWidth = 1.2; for (let i = 0; i <= w; i += 8) { x.beginPath(); x.moveTo(i, 0); x.lineTo(i, h); x.stroke(); } for (let j = 0; j <= h; j += 8) { x.beginPath(); x.moveTo(0, j); x.lineTo(w, j); x.stroke(); } });
  const net = new THREE.Mesh(new THREE.PlaneGeometry(TBL.W + 0.3, TBL.NET), new THREE.MeshBasicMaterial({ map: netT, transparent: true, side: THREE.DoubleSide, depthWrite: false })); net.position.set(0, TBL.H + TBL.NET / 2, 0); table.add(net);
  mesh(BOX, basic(0xffffff), 0, TBL.H + TBL.NET - 0.01, 0, TBL.W + 0.3, 0.02, 0.004, table);
  [-1, 1].forEach(s => mesh(CYL, mat(0x222222), s * (TBL.W / 2 + 0.15), TBL.H + TBL.NET / 2, 0, 0.012, TBL.NET + 0.02, 0.012, table));
})();

// bola + sombras falsas (percepção de profundidade)
const ball = newBall(), ballMesh = mesh(SPH, mat(0xfff4e0, { emissive: 0x332a1a }), 0, 1, 1.3, BALL_R, BALL_R, BALL_R);
const shadowTex = ctex(64, 64, (x, w, h) => { const g = x.createRadialGradient(32, 32, 0, 32, 32, 32); g.addColorStop(0, 'rgba(0,0,0,.55)'); g.addColorStop(1, 'rgba(0,0,0,0)'); x.fillStyle = g; x.fillRect(0, 0, w, h); });
const ballShadow = new THREE.Mesh(new THREE.PlaneGeometry(0.07, 0.07), new THREE.MeshBasicMaterial({ map: shadowTex, transparent: true, depthWrite: false })); ballShadow.rotation.x = -PI / 2; scene.add(ballShadow);
const trail = []; for (let i = 0; i < 6; i++) { const m = mesh(SPH, basic(0xffffff, { transparent: true, opacity: 0.12 - i * 0.018 }), 0, -5, 0, BALL_R * (0.9 - i * 0.08), BALL_R * (0.9 - i * 0.08), BALL_R * (0.9 - i * 0.08)); trail.push(m); }

// raquete (modelo 3D) — origem no punho, +Y aponta para a lâmina, +Z é a face (borracha vermelha)
const PAD = { R: 0.087, TH: 0.012, CENTER: 0.115 };
function makePaddle(red, black) {
  const g = new THREE.Group();
  const blade = new THREE.Group(); blade.position.y = PAD.CENTER; g.add(blade);
  const wood = mesh(CYL, mat(0xc9a06a), 0, 0, 0, PAD.R, PAD.TH, PAD.R, blade); wood.rotation.x = PI / 2; wood.scale.set(PAD.R, PAD.TH, PAD.R);
  const f = mesh(CYL, mat(red), 0, 0, PAD.TH / 2 + 0.0008, PAD.R * 0.98, 0.003, PAD.R * 0.98, blade); f.rotation.x = PI / 2;
  const b = mesh(CYL, mat(black), 0, 0, -PAD.TH / 2 - 0.0008, PAD.R * 0.98, 0.003, PAD.R * 0.98, blade); b.rotation.x = PI / 2;
  mesh(BOX, mat(0xb98a55), 0, -0.07, 0, 0.026, 0.09, 0.012, g); mesh(BOX, mat(0x7a5530), 0, -0.07 + 0.0, 0, 0.03, 0.05, 0.016, g);
  return g;
}
const myPaddle = makePaddle(0xd62828, 0x151515); scene.add(myPaddle);
const aiPaddle = makePaddle(0x1d3557, 0xd62828); scene.add(aiPaddle);
// mão do jogador
const handM = mesh(SPH, mat(0xf1c27d), 0, 0, 0, 0.045, 0.05, 0.05, myPaddle); handM.position.set(0, -0.045, -0.012);
const armU = mesh(CYL, mat(0x3b82f6), 0, 0, 0, 1, 1, 1), armF = mesh(CYL, mat(0x3b82f6), 0, 0, 0, 1, 1, 1), armH = mesh(SPH, mat(0xf1c27d), 0, 0, 0, 0.04, 0.04, 0.04);
const _s = new V(); function seg(m, a, b, r) { const d = _s.subVectors(b, a), L = d.length() || 1e-3; m.position.copy(a).addScaledVector(d, 0.5); m.quaternion.setFromUnitVectors(new V(0, 1, 0), d.multiplyScalar(1 / L)); m.scale.set(r, L, r); }

// ---------- Personagens 3D articulados (rosto, pescoço, tronco, braços, pernas com joelhos, tênis) ----------
const SKINS = [0xe0ac84, 0xc68642, 0x8d5524, 0xf1c27d, 0xffdbac, 0xb5835a], HAIRS = [0x2b1b12, 0x0f0f12, 0x6b4423, 0xc9a24a, 0x8a8a90, 0x4a2a1a];
function limb(m, len, r, parent) { return mesh(CYL, m, 0, -len / 2, 0, r, len, r, parent); }
function buildHuman(o) {
  const g = new THREE.Group(), M = { skin: mat(o.skin), shirt: mat(o.shirt), shorts: mat(o.shorts), shoe: mat(0xf4f4f4), sole: mat(0x1b1b1b), hair: mat(o.hair), band: mat(o.band || 0xffffff), dark: basic(0x14141a), white: basic(0xffffff), mouth: basic(0x8a3b3b) };
  const J = {}, hips = new THREE.Group(); hips.position.y = 0.95; g.add(hips); J.hips = hips;
  mesh(CYL, M.shorts, 0, -0.03, 0, 0.165, 0.2, 0.105, hips);
  const spine = new THREE.Group(); spine.position.y = 0.06; hips.add(spine); J.spine = spine;
  mesh(CYL, M.shirt, 0, 0.2, 0, 0.145, 0.4, 0.095, spine); mesh(SPH, M.shirt, 0, 0.42, 0, 0.185, 0.12, 0.105, spine); mesh(CYL, M.band, 0, 0.5, 0, 0.07, 0.03, 0.06, spine); // gola
  const neck = new THREE.Group(); neck.position.y = 0.5; spine.add(neck); J.neck = neck; mesh(CYL, M.skin, 0, 0.03, 0, 0.042, 0.1, 0.042, neck);
  const head = new THREE.Group(); head.position.y = 0.08; neck.add(head); J.head = head;
  mesh(SPH, M.skin, 0, 0.115, 0, 0.092, 0.115, 0.1, head); mesh(SPH, M.hair, 0, 0.165, -0.014, 0.099, 0.085, 0.108, head);
  if (o.hairStyle === 'long') mesh(BOX, M.hair, 0, 0.07, -0.085, 0.19, 0.2, 0.05, head);
  if (o.hairStyle === 'band') mesh(CYL, M.band, 0, 0.15, 0, 0.1, 0.022, 0.108, head);
  [-1, 1].forEach(sx => { mesh(SPH, M.skin, sx * 0.093, 0.11, 0, 0.018, 0.03, 0.02, head); mesh(SPH, M.white, sx * 0.036, 0.13, 0.087, 0.019, 0.014, 0.01, head); mesh(SPH, M.dark, sx * 0.036, 0.13, 0.095, 0.0095, 0.0095, 0.006, head); mesh(BOX, M.hair, sx * 0.036, 0.157, 0.094, 0.032, 0.005, 0.006, head); });
  mesh(SPH, M.skin, 0, 0.098, 0.098, 0.016, 0.02, 0.02, head); mesh(BOX, M.mouth, 0, 0.058, 0.093, 0.032, 0.005, 0.006, head);
  const arms = [], legs = [];
  [-1, 1].forEach(s => { // braços: s=-1 direito do personagem (lado -x), s=+1 esquerdo
    const sh = new THREE.Group(); sh.position.set(s * 0.205, 0.45, 0); spine.add(sh); limb(M.shirt, 0.28, 0.05, sh);
    const el = new THREE.Group(); el.position.y = -0.28; sh.add(el); limb(M.skin, 0.25, 0.04, el); mesh(CYL, M.band, 0, -0.21, 0, 0.044, 0.035, 0.044, el); mesh(SPH, M.skin, 0, -0.275, 0, 0.042, 0.052, 0.036, el);
    arms.push({ sh, el, s });
    const hp = new THREE.Group(); hp.position.set(s * 0.09, -0.06, 0); hips.add(hp); limb(M.shorts, 0.24, 0.085, hp); mesh(CYL, M.skin, 0, -0.34, 0, 0.068, 0.2, 0.068, hp);
    const kn = new THREE.Group(); kn.position.y = -0.45; hp.add(kn); limb(M.skin, 0.4, 0.05, kn); mesh(CYL, M.white, 0, -0.33, 0, 0.056, 0.12, 0.056, kn);
    mesh(BOX, M.shoe, 0, -0.43, 0.04, 0.088, 0.07, 0.2, kn); mesh(BOX, M.sole, 0, -0.468, 0.04, 0.092, 0.018, 0.205, kn);
    legs.push({ hip: hp, knee: kn, s });
  });
  g.scale.setScalar(o.scale || 1); return { root: g, J, M, arms, legs, t: Math.random() * 6, ph: 0, cr: 0.5 };
}
function animHuman(h, dt, a) {
  const J = h.J; h.t += dt; const t = h.t, vx = a.vx || 0, moving = Math.abs(vx) > 0.12, k = Math.min(1, dt * 10), side = Math.sign(vx), swing = a.swing || 0; h.ph += dt * (moving ? 9 : 0);
  h.cr += ((a.crouch === undefined ? 0.5 : a.crouch) - h.cr) * k;
  J.hips.position.y = 0.95 - 0.13 * h.cr + (a.jump || 0) + Math.sin(t * 2.2) * 0.004; J.hips.position.x = Math.sin(t * 1.4) * 0.012;
  h.legs.forEach((L, i) => { const lift = moving ? Math.max(0, Math.sin(h.ph + (i ? PI : 0))) : 0; L.hip.rotation.x = -0.45 * h.cr - 0.3 * lift; L.knee.rotation.x = 0.9 * h.cr + 0.8 * lift; L.hip.rotation.z = L.s * 0.05 + side * 0.28 * lift; });
  const sad = a.mood === 'sad', cheer = a.mood === 'cheer';
  J.spine.rotation.x = 0.08 + 0.14 * h.cr + 0.16 * swing + (sad ? 0.16 : 0) + Math.sin(t * 2) * 0.006;
  J.spine.rotation.y += (((a.look || 0) * 0.45) - 0.5 * swing - J.spine.rotation.y) * k; J.spine.scale.y = 1 + Math.sin(t * 2) * 0.006;
  J.neck.rotation.y += (((a.look || 0) * 0.35) - J.neck.rotation.y) * k; J.neck.rotation.x = (sad ? 0.5 : 0.04) + Math.sin(t * 1.7) * 0.01; J.head.rotation.z = sad ? Math.sin(t * 7) * 0.05 : 0;
  const free = h.arms[1];
  if (cheer) { free.sh.rotation.z = 2.6 + Math.sin(t * 12) * 0.15; free.sh.rotation.x = 0; free.el.rotation.x = -0.25; }
  else { free.sh.rotation.z = 0.22 + Math.sin(t * 1.6) * 0.03 + swing * 0.2; free.sh.rotation.x = -0.28 + (sad ? 0.28 : 0) - swing * 0.3; free.el.rotation.x = -0.55 + (sad ? 0.4 : 0); }
}
// raquete do adversário: braço direito é desenhado por IK em coordenadas do mundo
const opp = { x: 0, hand: new V(0, 1, -1.8), target: new V(0, 0.95, -1.7), swing: 0, cool: 0, mood: 'neutral', moodT: 0, vx: 0 };
opp.h = buildHuman({ skin: SKINS[0], shirt: 0xe63946, shorts: 0x1d2a4a, hair: HAIRS[0], band: 0xffffff, hairStyle: 'band' }); scene.add(opp.h.root);
opp.h.arms[0].sh.visible = false;
opp.armU = mesh(CYL, opp.h.M.shirt, 0, 0, 0, 1, 1, 1); opp.armF = mesh(CYL, opp.h.M.skin, 0, 0, 0, 1, 1, 1); opp.armH = mesh(SPH, opp.h.M.skin, 0, 0, 0, 0.043, 0.052, 0.04);

// torcedores (corpo fundido em 1 malha + braços animados: poucos draw calls)
const eu2 = new THREE.Euler();
function bake(list) {
  const P = [], N = [], C = [], m4 = new THREE.Matrix4(), q = new Q(), sc = new V(), ps = new V(), col = new THREE.Color();
  for (const it of list) { if (it.ry || it.rx || it.rz) q.setFromEuler(eu2.set(it.rx || 0, it.ry || 0, it.rz || 0, 'YXZ')); else q.identity(); m4.compose(ps.set(it.x, it.y, it.z), q, sc.set(it.sx, it.sy, it.sz)); const g = it.g.index ? it.g.toNonIndexed() : it.g.clone(); g.applyMatrix4(m4); col.set(it.c); const pa = g.attributes.position.array, na = g.attributes.normal.array; for (let i = 0; i < pa.length; i++) { P.push(pa[i]); N.push(na[i]); } for (let i = 0; i < pa.length / 3; i++) C.push(col.r, col.g, col.b); g.dispose(); }
  const g = new THREE.BufferGeometry(); g.setAttribute('position', new THREE.Float32BufferAttribute(P, 3)); g.setAttribute('normal', new THREE.Float32BufferAttribute(N, 3)); g.setAttribute('color', new THREE.Float32BufferAttribute(C, 3)); return new THREE.Mesh(g, new THREE.MeshLambertMaterial({ vertexColors: true }));
}
function buildSpectator(o) {
  const g = new THREE.Group(); g.position.set(o.x, 0, o.z); g.rotation.y = o.face; scene.add(g); const it = (geo, c, x, y, z, sx, sy, sz) => ({ g: geo, c, x, y, z, sx, sy, sz });
  g.add(bake([it(CYL, o.shorts, 0, 0.92, 0, 0.165, 0.2, 0.105), it(CYL, o.shirt, 0, 1.21, 0, 0.145, 0.4, 0.095), it(SPH, o.shirt, 0, 1.43, 0, 0.185, 0.12, 0.105), it(CYL, o.skin, 0, 1.55, 0, 0.042, 0.1, 0.042), it(SPH, o.skin, 0, 1.725, 0, 0.092, 0.115, 0.1), it(SPH, o.hair, 0, 1.775, -0.014, 0.099, 0.085, 0.108), it(SPH, 0x111111, -0.036, 1.74, 0.093, 0.011, 0.011, 0.008), it(SPH, 0x111111, 0.036, 1.74, 0.093, 0.011, 0.011, 0.008)].concat(o.legs ? [it(CYL, o.shorts, -0.09, 0.45, 0, 0.075, 0.9, 0.075), it(CYL, o.shorts, 0.09, 0.45, 0, 0.075, 0.9, 0.075), it(BOX, 0xf4f4f4, -0.09, 0.04, 0.04, 0.09, 0.08, 0.2), it(BOX, 0xf4f4f4, 0.09, 0.04, 0.04, 0.09, 0.08, 0.2)] : [])));
  const arms = []; [-1, 1].forEach(s => { const sh = new THREE.Group(); sh.position.set(s * 0.205, 1.46, 0); g.add(sh); sh.add(bake([it(CYL, o.shirt, 0, -0.14, 0, 0.05, 0.28, 0.05)])); const el = new THREE.Group(); el.position.y = -0.28; sh.add(el); el.add(bake([it(CYL, o.skin, 0, -0.125, 0, 0.04, 0.25, 0.04), it(SPH, o.skin, 0, -0.275, 0, 0.042, 0.052, 0.036)])); arms.push({ sh, el, s }); });
  return { g, arms, t: 0 };
}
const crowd = { mood: 'idle', t: 0, secs: [], wave: 0 };
const CYL8 = new THREE.CylinderGeometry(1, 1, 1, 8, 1), SPH6 = new THREE.SphereGeometry(1, 8, 6);
const Lm = (c) => ({ c });
function buildArena() {
  const rnd = (() => { let a = 12345; return () => { a = (a * 1664525 + 1013904223) | 0; return ((a >>> 0) % 100000) / 100000; }; })();
  const pick = arr => arr[(rnd() * arr.length) | 0], ST = [], it = (g, c, x, y, z, sx, sy, sz, o) => ST.push(Object.assign({ g, c, x, y, z, sx, sy, sz }, o || {}));
  // piso e quadra de campeonato
  it(BOX, 0x1a1d27, 0, -0.05, -0.4, 40, 0.1, 40);
  // casca do estádio
  it(BOX, 0x0d1019, 0, 6, -0.4, 36, 0.2, 36); // teto
  [[0, 3, -19, 36, 6, 0.3], [0, 3, 18, 36, 6, 0.3], [-18, 3, -0.4, 0.3, 6, 36], [18, 3, -0.4, 0.3, 6, 36]].forEach(w => it(BOX, 0x10131c, w[0], w[1] + 3, w[2], w[3], w[4] + 6, w[5]));
  // treliça e refletores no teto
  for (let i = -2; i <= 2; i++) { it(BOX, 0x2a2f3d, i * 3, 5.7, -0.4, 0.15, 0.15, 30); it(BOX, 0x2a2f3d, 0, 5.7, -0.4 + i * 6, 24, 0.15, 0.15); }
  for (let i = 0; i < 4; i++) for (let j = 0; j < 4; j++) it(BOX, 0xfff3d0, -4.5 + i * 3, 5.55, -4.5 + j * 3, 0.9, 0.08, 0.9, { u: 1 });
  // arquibancadas: laterais (x), fundo do adversário (z-) e atrás do jogador (z+)
  const SEATS = [], spec = [];
  const tiers = 6, stepH = 0.55, depth = 0.95;
  const seatColors = [0xd62828, 0x1d3557, 0xf1c40f, 0xffffff];
  const sides = [
    { n: 'L', pos: k => [-(4.05 + k * depth), 0], yaw: PI / 2, across: [-5.1, 4.3], axis: 'z' },
    { n: 'R', pos: k => [4.05 + k * depth, 0], yaw: -PI / 2, across: [-5.1, 4.3], axis: 'z' },
    { n: 'F', pos: k => [0, -(5.75 + k * depth)], yaw: 0, across: [-3.4, 3.4], axis: 'x' },
    { n: 'B', pos: k => [0, 4.95 + k * depth], yaw: PI, across: [-3.4, 3.4], axis: 'x' }
  ];
  sides.forEach(sd => {
    for (let k = 0; k < tiers; k++) {
      const top = 0.45 + stepH * (k + 1), [cx, cz] = sd.pos(k), len = sd.across[1] - sd.across[0] + 1.4, mid = (sd.across[0] + sd.across[1]) / 2;
      if (sd.axis === 'z') it(BOX, 0x2b2f3c, cx, top / 2, mid, depth, top, len); else it(BOX, 0x2b2f3c, mid, top / 2, cz, len, top, depth);
      for (let q = sd.across[0]; q <= sd.across[1]; q += 0.62) {
        const x = sd.axis === 'z' ? cx : q + (rnd() - 0.5) * 0.06, z = sd.axis === 'z' ? q + (rnd() - 0.5) * 0.06 : cz;
        it(BOX, seatColors[((q * 1.6) | 0 + k) & 3 ? (Math.abs(((q * 1.6) | 0) + k) % 4) : 0], x, top + 0.05, z, sd.axis === 'z' ? 0.5 : 0.5, 0.1, 0.5);
        spec.push({ x, y: top + 0.1, z, yaw: sd.yaw + (rnd() - 0.5) * 0.25, side: sd.n, k, q });
      }
    }
  });
  scene.add(bake(ST));
  // LED de patrocinadores em volta da quadra
  const ledT = ctex(2048, 64, (c, w, h) => { c.fillStyle = '#0b1a3a'; c.fillRect(0, 0, w, h); c.font = '700 40px sans-serif'; c.textBaseline = 'middle'; const names = ['PICOVERSO', 'WORLD TABLE TENNIS VR', 'PICO G2', 'CAMPEONATO MUNDIAL', 'ULTRA SPIN', 'PING-PONG VR']; let x = 20, i = 0; while (x < w) { c.fillStyle = ['#ffd23f', '#4be08a', '#7fb0ff', '#ff6b6b'][i % 4]; c.fillText(names[i % names.length], x, h / 2); x += c.measureText(names[i % names.length]).width + 70; i++; } }, [2.5, 1]);
  const led = (w, h, x, y, z, ry) => { const m = new THREE.Mesh(new THREE.BoxGeometry(w, h, 0.08), [basic(0x111111), basic(0x111111), basic(0x111111), basic(0x111111), basic(0xffffff, { map: ledT }), basic(0x111111)]); m.position.set(x, y, z); m.rotation.y = ry; scene.add(m); };
  led(9.6, 0.9, -3.62, 0.45, -0.4, PI / 2); led(9.6, 0.9, 3.62, 0.45, -0.4, -PI / 2); led(7.3, 0.9, 0, 0.45, -5.18, 0); led(7.3, 0.9, 0, 0.45, 4.38, PI);
  const courtT = ctex(1024, 1280, (c, w, h) => { c.fillStyle = '#1f5f9a'; c.fillRect(0, 0, w, h); c.strokeStyle = '#fff'; c.lineWidth = 10; c.strokeRect(24, 24, w - 48, h - 48); c.fillStyle = 'rgba(255,255,255,.16)'; c.font = '700 120px sans-serif'; c.textAlign = 'center'; c.save(); c.translate(w / 2, h * 0.16); c.fillText('WORLD', 0, 0); c.restore(); c.save(); c.translate(w / 2, h * 0.86); c.rotate(PI); c.fillText('WORLD', 0, 0); c.restore(); c.beginPath(); c.arc(w / 2, h / 2, 150, 0, 7); c.lineWidth = 6; c.stroke(); });
  const court = new THREE.Mesh(new THREE.PlaneGeometry(7.2, 9.0), mat(0xffffff, { map: courtT })); court.rotation.x = -PI / 2; court.position.set(0, 0.004, -0.4); scene.add(court);
  // banners suspensos
  [[-8, 5.0, -3], [8, 5.0, -3], [-8, 5.0, 2.5], [8, 5.0, 2.5], [0, 5.0, -11]].forEach((b, i) => { const t = ctex(256, 512, (c, w, h) => { c.fillStyle = ['#e63946', '#1d3557', '#f4a261', '#2a9d8f', '#8e44ad'][i]; c.fillRect(0, 0, w, h); c.fillStyle = '#fff'; c.font = '700 54px sans-serif'; c.textAlign = 'center'; ['PING', 'PONG', 'VR', '★'].forEach((l, k) => c.fillText(l, w / 2, 110 + k * 100)); }); const m = new THREE.Mesh(new THREE.PlaneGeometry(1.2, 2.4), basic(0xffffff, { map: t, side: THREE.DoubleSide })); m.position.set(b[0], b[1], b[2]); m.rotation.y = b[0] === 0 ? 0 : (b[0] < 0 ? PI / 2 : -PI / 2); scene.add(m); });
  // cones de luz dos refletores
  for (let i = 0; i < 4; i++) { const cone = new THREE.Mesh(new THREE.CylinderGeometry(0.15, 1.6, 5.2, 16, 1, true), new THREE.MeshBasicMaterial({ color: 0xcfe4ff, transparent: true, opacity: 0.07, depthWrite: false, blending: THREE.AdditiveBlending, side: THREE.DoubleSide })); const a = i * PI / 2 + PI / 4; cone.position.set(Math.cos(a) * 2.2, 3.1, -0.4 + Math.sin(a) * 2.2); cone.rotation.z = Math.cos(a) * 0.35; cone.rotation.x = Math.sin(a) * 0.35; scene.add(cone); }
  // plateia: 8 setores (corpos, braços abaixados, braços levantados)
  const secs = {}, shirts = [0xd62828, 0x1d3557, 0xf1c40f, 0xffffff, 0x2a9d8f, 0xf4a261, 0x8e44ad, 0x3a86ff, 0xff6b6b, 0x222222];
  spec.forEach(sp => {
    const key = sp.side + (sp.q < (sp.side === 'L' || sp.side === 'R' ? -0.4 : 0) ? 0 : 1); const S = secs[key] || (secs[key] = { body: [], down: [], up: [] });
    const shirt = pick(shirts), skin = pick(SKINS), hair = pick(HAIRS), pants = pick([0x2b2f3a, 0x3a4a6a, 0x55402a, 0x222222]);
    const c = Math.cos(sp.yaw), sn = Math.sin(sp.yaw), P = (ox, oy, oz, g, col, sx, sy, sz, ex) => { const x = sp.x + ox * c + oz * sn, z = sp.z - ox * sn + oz * c; return Object.assign({ g, c: col, x, y: sp.y + oy, z, sx, sy, sz, ry: sp.yaw }, ex || {}); };
    S.body.push(P(0, 0.75, 0, CYL8, shirt, 0.15, 0.55, 0.1), P(0, 1.17, 0, SPH6, skin, 0.1, 0.12, 0.1), P(0, 1.24, -0.01, SPH6, hair, 0.105, 0.09, 0.108));
    [-1, 1].forEach(sd => { S.body.push(P(sd * 0.09, 0.5, 0.18, BOX, pants, 0.13, 0.13, 0.36), P(sd * 0.09, 0.24, 0.36, BOX, pants, 0.11, 0.44, 0.11)); S.down.push(P(sd * 0.2, 0.68, 0, CYL8, shirt, 0.04, 0.46, 0.04)); S.up.push(P(sd * 0.25, 1.22, 0.02, CYL8, shirt, 0.04, 0.5, 0.04, { rz: -sd * 0.35 })); });
    if (rnd() < 0.18) S.up.push(P(0.28, 1.58, 0.02, BOX, pick([0xe63946, 0x1d3557, 0xf4a261]), 0.26, 0.17, 0.01, { rz: 0.1 })); // bandeirinha
  });
  Object.keys(secs).forEach((k, i) => { const S = secs[k], g = new THREE.Group(); scene.add(g); const m = { g, body: bake(S.body), down: bake(S.down), up: bake(S.up), ph: i * 0.9, base: 0 }; g.add(m.body, m.down, m.up); m.up.visible = false; crowd.secs.push(m); });
  // flashes de câmera na plateia
  const fl = makePointsFlash(spec); scene.add(fl.p); crowd.flash = fl;
}
const flashTex = ctex(64, 64, (x, w, h) => { const g = x.createRadialGradient(32, 32, 0, 32, 32, 32); g.addColorStop(0, 'rgba(255,255,255,1)'); g.addColorStop(0.3, 'rgba(255,255,255,.5)'); g.addColorStop(1, 'rgba(255,255,255,0)'); x.fillStyle = g; x.fillRect(0, 0, w, h); });
function makePointsFlash(spec) {
  const n = 7, g = new THREE.BufferGeometry(); g.setAttribute('position', new THREE.Float32BufferAttribute(new Float32Array(n * 3).fill(-100), 3));
  const p = new THREE.Points(g, new THREE.PointsMaterial({ size: 0.45, color: 0xffffff, transparent: true, depthWrite: false, blending: THREE.AdditiveBlending, map: flashTex, sizeAttenuation: true })); p.frustumCulled = false;
  return { p, spec, n, t: 0 };
}
buildArena();
// árbitro em pé ao lado da mesa (corpo fundido + braços animados)
const umpire = (function () { const sp = buildSpectator({ x: -1.95, z: -0.15, face: PI / 2, skin: SKINS[3], hair: HAIRS[4], shirt: 0xf5f5f5, shorts: 0x15151a, legs: true }); sp.raise = 0; return sp; })();
function crowdMood(m, sec) { crowd.mood = m; crowd.t = sec; if (m === 'cheer') { umpire.raise = 1.4; umpire.side = 1; } }
function crowdUpdate(dt) {
  crowd.t -= dt; if (crowd.t <= 0) crowd.mood = 'idle'; time2 += dt; const t = time2, m = crowd.mood;
  if (st.rally >= 8 && crowd.wave <= 0 && st.phase === 'rally') crowd.wave = 5; if (crowd.wave > 0) crowd.wave -= dt;
  crowd.secs.forEach((S, i) => {
    let up = false, bob = 0, rz = Math.sin(t * 1.1 + S.ph) * 0.004;
    if (m === 'cheer') { up = Math.sin(t * 8 + S.ph) > -0.35; bob = Math.abs(Math.sin(t * 9 + S.ph)) * 0.09; }
    else if (m === 'clap') { up = Math.sin(t * 13 + S.ph) > 0; bob = 0.012 + Math.sin(t * 6 + S.ph) * 0.01; }
    else if (crowd.wave > 0) { const w = (t * 2.2 - i * 0.55) % 4.4; up = w > 0 && w < 1.1; bob = up ? 0.06 : 0; }
    S.up.visible = up; S.down.visible = !up; S.g.position.y = bob; S.g.rotation.z = rz;
  });
  // flashes
  const F = crowd.flash; F.t -= dt; if (F.t <= 0) { F.t = m === 'cheer' ? 0.05 : 0.35 + Math.random() * 0.5; const pos = F.p.geometry.attributes.position, tgt = F.n; for (let i = 0; i < tgt; i++) { if (Math.random() < (m === 'cheer' ? 0.8 : 0.25)) { const sp = F.spec[(Math.random() * F.spec.length) | 0]; pos.setXYZ(i, sp.x, sp.y + 1.2, sp.z); } else pos.setXYZ(i, 0, -100, 0); } pos.needsUpdate = true; }
  // árbitro: levanta o braço ao marcar ponto
  if (umpire.raise > 0) { umpire.raise -= dt; const A = umpire.arms[1]; A.sh.rotation.z = 2.4 * Math.min(1, umpire.raise * 3); A.sh.rotation.x = 0; } else umpire.arms.forEach(A => { A.sh.rotation.z = A.s * 0.1; A.sh.rotation.x = -0.1; A.el.rotation.x = -0.3; });
}
let time2 = 0;

// placares
function makeBoard(w, h, pw, ph) { const c = document.createElement('canvas'); c.width = pw; c.height = ph; const t = new THREE.CanvasTexture(c); t.colorSpace = THREE.SRGBColorSpace; const m = new THREE.Mesh(new THREE.PlaneGeometry(w, h), basic(0xffffff, { map: t })); return { m, c, ctx: c.getContext('2d'), t }; }
const boardFar = makeBoard(3.2, 0.9, 1024, 288); boardFar.m.position.set(0, 4.3, -12.5); scene.add(boardFar.m); boardFar.m.scale.setScalar(2.0);
const boardSide = makeBoard(2.0, 0.5625, 1024, 288); boardSide.m.position.set(-12.5, 4.3, -0.4); boardSide.m.rotation.y = PI / 2; boardSide.m.scale.setScalar(1.8); scene.add(boardSide.m);
const boardSide2 = makeBoard(2.0, 0.5625, 1024, 288); boardSide2.m.position.set(12.5, 4.3, -0.4); boardSide2.m.rotation.y = -PI / 2; boardSide2.m.scale.setScalar(1.8); scene.add(boardSide2.m);
let msg = { text: '', t: 0 };
const st = { phase: 'menu', server: 'player', lastHitter: null, bounceRecv: 0, serving: false, srvOwn: false, p: 0, a: 0, resolveT: 0, deadT: 0, over: false, rally: 0, best: 0 };
function drawBoard(b) {
  const c = b.ctx, w = b.c.width, h = b.c.height; c.fillStyle = '#0b1020'; c.fillRect(0, 0, w, h); c.strokeStyle = '#4285f4'; c.lineWidth = 10; c.strokeRect(5, 5, w - 10, h - 10);
  c.font = '700 120px sans-serif'; c.textBaseline = 'middle'; c.textAlign = 'center';
  c.fillStyle = '#ffd23f'; c.fillText(String(st.p), w * 0.32, h * 0.42); c.fillStyle = '#fff'; c.fillText(':', w / 2, h * 0.4); c.fillStyle = '#ff6b6b'; c.fillText(String(st.a), w * 0.68, h * 0.42);
  c.font = '600 38px sans-serif'; c.fillStyle = '#ffd23f'; c.fillText('VOCÊ', w * 0.32, h * 0.8); c.fillStyle = '#ff6b6b'; c.fillText('PICO', w * 0.68, h * 0.8);
  c.font = '600 34px sans-serif'; c.fillStyle = '#9ec5ff'; c.fillText(msg.t > 0 ? msg.text : '', w / 2, h * 0.9 > 0 ? h * 0.93 : 0); b.t.needsUpdate = true;
}
function setMsg(t, sec) { msg.text = t; msg.t = sec || 2; refreshBoards(); }
function refreshBoards() { drawBoard(boardFar); drawBoard(boardSide); drawBoard(boardSide2); }

/* ---------- Entrada ---------- */
const Input = { strafe: 0, depth: 0, click: false, clickHeld: false, prev: false, menu: false, prevMenu: false, xrSelect: false, xrMenu: false, keys: {}, mouse: { x: 0, y: 0, has: false }, pose: null, raw: [] };
const ctrls = [0, 1].map(i => { const c = renderer.xr.getController(i); rig.add(c); c.addEventListener('connected', e => { c.userData.connected = true; c.userData.hand = e.data.handedness; c.userData.src = e.data; }); c.addEventListener('disconnected', () => { c.userData.connected = false; }); c.addEventListener('selectstart', () => { Input.xrSelect = true; }); c.addEventListener('selectend', () => { Input.xrSelect = false; }); c.addEventListener('squeezestart', () => { Input.xrMenu = true; }); return c; });
let mode = 'flat', xrSession = null;
function activeCtrl() { const c = ctrls.filter(x => x.userData.connected); if (SET.hand !== 'auto') { const m = c.find(x => x.userData.hand === SET.hand); if (m) return m; } return c.find(x => x.userData.hand === 'right') || c[0] || null; }
function pollInput() {
  let list = []; try { const g = navigator.getGamepads ? navigator.getGamepads() : []; for (const p of g) if (p && p.connected) list.push(p); } catch (e) {}
  if (xrSession) try { for (const s of xrSession.inputSources) if (s.gamepad && !list.includes(s.gamepad)) list.push(s.gamepad); } catch (e) {}
  let held = false, menu = false, ax = 0, ay = 0; Input.pose = null;
  for (const p of list) {
    Input.raw = Array.from(p.axes); if (p.pose && p.pose.orientation) Input.pose = p.pose.orientation;
    // touch traseiro / analógico: usa o par de eixos com mais movimento
    const pr = [[p.axes[0] || 0, p.axes[1] || 0], [p.axes[2] || 0, p.axes[3] || 0]];
    for (const q of pr) if (Math.hypot(q[0], q[1]) > Math.hypot(ax, ay)) { ax = q[0]; ay = q[1]; }
    p.buttons.forEach((b, i) => { if (b.pressed) { if (i === 1) menu = true; else held = true; } });
  }
  const dz = 0.25; ax = Math.abs(ax) > dz ? ax : 0; ay = Math.abs(ay) > dz ? ay : 0; const K = Input.keys;
  Input.strafe = clamp(ax * (SET.invert ? -1 : 1) + (K.d || K.arrowright ? 1 : 0) - (K.a || K.arrowleft ? 1 : 0), -1, 1);
  Input.depth = clamp(-ay + (K.s || K.arrowdown ? -1 : 0) + (K.w || K.arrowup ? 1 : 0), -1, 1);
  held = held || Input.xrSelect || K[' '] || K.enter; menu = menu || Input.xrMenu || K.m || K.escape;
  Input.click = (held && !Input.prev) || Input.tap; Input.clickHeld = held; Input.prev = held; Input.tap = false;
  Input.menu = menu && !Input.prevMenu; Input.prevMenu = menu; Input.xrMenu = false;
}
function haptic(strength) { try { if (!xrSession) return; for (const s of xrSession.inputSources) { const a = s.gamepad && s.gamepad.hapticActuators && s.gamepad.hapticActuators[0]; if (a && a.pulse) a.pulse(strength, 35); } } catch (e) {} }

/* ---------- Raquete rastreada pelo controle ---------- */
const TILT = 0.55; // rad: face da raquete inclinada p/ cima em relação à direção do controle (pegada natural)
const qOff = new Q().setFromAxisAngle(new V(1, 0, 0), TILT).multiply(new Q().setFromAxisAngle(new V(0, 1, 0), PI));
const pad = { pos: new V(), quat: new Q(), c0: new V(), c1: new V(), n0: new V(0, 0, -1), n1: new V(0, 0, -1), vel: new V(), side: 1, using6: false, init: false, F: new V(0, 0, -1), sh: new V() };
const REACH = 0.72;
function shoulder(side, out) { return out.set(rig.position.x + side * 0.2, rig.position.y - 0.25, rig.position.z); }
let handPoseYaw = null;
function updatePaddle(dt) {
  const ctrl = mode === 'xr' ? activeCtrl() : null; let q = new Q(), F = new V(0, 0, -1), side = 1, realPos = null;
  if (ctrl) {
    ctrl.getWorldQuaternion(q); side = ctrl.userData.hand === 'left' ? -1 : 1; F.set(0, 0, -1).applyQuaternion(q);
    if (ctrl.position.lengthSq() > 0.0009) { pad.using6 = true; } if (pad.using6) realPos = ctrl.getWorldPosition(new V());
  } else if (Input.pose && mode !== 'xr') {
    const qp = new Q().fromArray(Input.pose); F.set(0, 0, -1).applyQuaternion(qp); if (handPoseYaw === null) handPoseYaw = Math.atan2(-F.x, -F.z); F.applyAxisAngle(new V(0, 1, 0), handPoseYaw); q.setFromUnitVectors(new V(0, 0, -1), F);
  } else if (mode === 'flat' && Input.mouse.has) {
    const ray = new THREE.Raycaster(); ray.setFromCamera(mouseNdc, camera); const tgt = new V(); const plane = new THREE.Plane(new V(0, 0, 1), -(rig.position.z - 0.7)); ray.ray.intersectPlane(plane, tgt);
    if (tgt) { shoulder(1, pad.sh); tgt.y = clamp(tgt.y, 0.7, 1.6); tgt.x = clamp(tgt.x, rig.position.x - 1.2, rig.position.x + 1.2); F.subVectors(tgt, pad.sh).normalize(); q.setFromUnitVectors(new V(0, 0, -1), F); }
  } else { camera.getWorldDirection(F); q.copy(camera.getWorldQuaternion(new Q())); }
  pad.F.copy(F); pad.side = side; shoulder(side, pad.sh);
  const quat = q.clone().multiply(qOff);
  const pos = realPos ? realPos.clone() : pad.sh.clone().addScaledVector(F, REACH);
  const up = new V(0, 1, 0).applyQuaternion(quat), nrm = new V(0, 0, 1).applyQuaternion(quat), c = pos.clone().addScaledVector(up, PAD.CENTER);
  if (!pad.init) { pad.c0.copy(c); pad.n0.copy(nrm); pad.init = true; } else { pad.c0.copy(pad.c1); pad.n0.copy(pad.n1); }
  pad.c1.copy(c); pad.n1.copy(nrm); pad.pos.copy(pos); pad.quat.copy(quat);
  const inst = pad.c1.clone().sub(pad.c0).multiplyScalar(1 / Math.max(dt, 1e-3)); if (inst.length() > 14) inst.setLength(14);
  pad.vel.lerp(inst, 0.55);
  myPaddle.position.copy(pos); myPaddle.quaternion.copy(quat); myPaddle.visible = true;
  // braço
  const hand = pos.clone(), mid = shoulder(side, new V()).add(hand).multiplyScalar(0.5), S = shoulder(side, new V()), d = Math.min(S.distanceTo(hand), 0.84), hh = Math.sqrt(Math.max(0, 0.42 * 0.42 - (d / 2) * (d / 2)));
  const dir = hand.clone().sub(S).normalize(), bend = new V(0, -1, 0).addScaledVector(new V(1, 0, 0), side * 0.6); bend.addScaledVector(dir, -bend.dot(dir)).normalize();
  const E = mid.addScaledVector(bend, hh); seg(armU, S, E, 0.032); seg(armF, E, hand.clone().addScaledVector(up, -0.02), 0.027); armH.position.copy(hand).addScaledVector(up, -0.02); armH.visible = false;
  handM.visible = true;
}

/* ---------- Colisão contínua bola x raquete ---------- */
const _l = new V(), _n = new V(), _bc = new V(), _vp = new V(), _vr = new V(), _vt = new V(), _w = new V();
let hitCool = 0;
function paddleCollide(prev, u0, u1, subDt) {
  if (hitCool > 0 || st.phase === 'menu') return false;
  const bc0 = _bc.lerpVectors(pad.c0, pad.c1, u0), n0 = _n.lerpVectors(pad.n0, pad.n1, u0).normalize();
  const d0 = (prev.x - bc0.x) * n0.x + (prev.y - bc0.y) * n0.y + (prev.z - bc0.z) * n0.z;
  const bc1 = new V().lerpVectors(pad.c0, pad.c1, u1), n1 = new V().lerpVectors(pad.n0, pad.n1, u1).normalize();
  const rx = ball.x - bc1.x, ry = ball.y - bc1.y, rz = ball.z - bc1.z, d1 = rx * n1.x + ry * n1.y + rz * n1.z, th = PAD.TH / 2 + BALL_R;
  const crossed = (d0 > th && d1 <= th) || (d0 < -th && d1 >= -th) || Math.abs(d1) < th;
  if (!crossed) return false;
  const lat = Math.hypot(rx - n1.x * d1, ry - n1.y * d1, rz - n1.z * d1); if (lat > PAD.R + BALL_R * 0.7) return false;
  const s = (d0 >= 0 ? 1 : -1), nh = n1.clone().multiplyScalar(s);
  _vp.copy(pad.vel); _vr.set(ball.vx - _vp.x, ball.vy - _vp.y, ball.vz - _vp.z); const vn = _vr.dot(nh); if (vn >= 0) return false;
  ball.x = bc1.x + (rx - n1.x * d1) + nh.x * (th + 0.0006); ball.y = bc1.y + (ry - n1.y * d1) + nh.y * (th + 0.0006); ball.z = bc1.z + (rz - n1.z * d1) + nh.z * (th + 0.0006);
  _vt.copy(_vr).addScaledVector(nh, -vn); const mu = 0.22, e = 0.88;
  ball.vx = _vp.x + _vt.x * (1 - mu) - nh.x * e * vn; ball.vy = _vp.y + _vt.y * (1 - mu) - nh.y * e * vn; ball.vz = _vp.z + _vt.z * (1 - mu) - nh.z * e * vn;
  _w.crossVectors(nh, _vt).multiplyScalar(3 * mu / (2 * BALL_R)); ball.wx += _w.x; ball.wy += _w.y; ball.wz += _w.z;
  const sp = Math.hypot(ball.vx, ball.vy, ball.vz); if (sp > 22) { const k = 22 / sp; ball.vx *= k; ball.vy *= k; ball.vz *= k; }
  hitCool = 0.07; Snd.paddle(sp); haptic(clamp(sp / 12, 0.25, 1)); onHit('player'); return true;
}

/* ---------- Regras ---------- */
const other = s => (s === 'player' ? 'ai' : 'player');
function sideOf(z) { return z > 0 ? 'player' : 'ai'; }
function onHit(by) {
  if (st.phase === 'dead' || st.phase === 'menu') return;
  if (st.phase === 'serveWait') { st.phase = 'rally'; st.serving = true; st.srvOwn = false; st.lastHitter = by; st.bounceRecv = 0; st.rally = 0; return; }
  if (st.lastHitter === by) { point(other(by), 'Dupla batida'); return; }
  if (st.serving || st.bounceRecv === 0) { point(st.lastHitter, 'Bateu antes de quicar'); return; }
  st.lastHitter = by; st.bounceRecv = 0; st.rally++; st.best = Math.max(st.best, st.rally); opp.cool = 0.25;
}
function onTable(side) {
  if (st.phase !== 'rally') return; const H = st.lastHitter, R = other(H); if (!H) return;
  if (st.serving) {
    if (!st.srvOwn) { if (side === H) st.srvOwn = true; else point(R, 'Saque inválido'); }
    else if (side === R) { st.serving = false; st.bounceRecv = 1; predictAI(); } else point(R, 'Saque inválido');
    return;
  }
  if (side === H) { point(R, 'Bola no seu lado'); return; }
  st.bounceRecv++; if (st.bounceRecv >= 2) point(H, st.lastHitter === 'player' ? 'O Pico não alcançou' : 'Você não alcançou'); else if (side === 'ai') predictAI();
}
function point(winner, why) {
  if (st.phase === 'dead' || st.phase === 'over') return; st.phase = 'dead'; st.deadT = 1.6; st[winner === 'player' ? 'p' : 'a']++;
  const win = winner === 'player'; if (win) { crowdMood('cheer', 2.3); Snd.cheer(st.p >= SET.win - 1); opp.mood = 'sad'; opp.moodT = 2.0; } else { crowdMood('clap', 1.7); Snd.clap(); opp.mood = 'cheer'; opp.moodT = 1.9; } Snd.point(win); setMsg((win ? 'PONTO SEU! ' : 'Ponto do Pico. ') + why, 2.4);
  const w = SET.win; if ((st.p >= w || st.a >= w) && Math.abs(st.p - st.a) >= 2) { st.over = true; setTimeout(() => { st.phase = 'over'; Snd.fanfare(); setMsg(st.p > st.a ? '🏆 VOCÊ VENCEU!' : 'O Pico venceu!', 6); openMenu(); }, 1400); }
  refreshBoards();
}
function startServe() {
  const total = st.p + st.a, w = SET.win; st.server = (total >= (w - 1) * 2) ? ((total % 2) ? other(st.firstServer) : st.firstServer) : (Math.floor(total / 2) % 2 ? other(st.firstServer) : st.firstServer);
  st.phase = 'serveWait'; st.lastHitter = null; st.serving = false; st.bounceRecv = 0; st.srvOwn = false; st.serveT = 0; opp.swing = 0; opp.serveBall = false;
  ball.vx = ball.vy = ball.vz = ball.wx = ball.wy = ball.wz = 0;
  if (st.server === 'player') { setMsg('Seu saque: aperte o gatilho para lançar a bola', 3); } else { setMsg('Saque do Pico', 1.5); ball.x = opp.x; ball.y = 1.0; ball.z = -TBL.L / 2 - 0.25; st.serveT = 1.2; }
}

/* ---------- IA do adversário ---------- */
const DIFF = [{ name: 'Fácil', spd: 1.5, err: 0.1, react: 0.12, miss: 0.18 }, { name: 'Médio', spd: 2.6, err: 0.05, react: 0.07, miss: 0.07 }, { name: 'Difícil', spd: 4.2, err: 0.022, react: 0.03, miss: 0.0 }];
const AI_Z = -TBL.L / 2 - 0.3;
function predictAI() {
  const r = simulate({ x: ball.x, y: ball.y, z: ball.z, vx: ball.vx, vy: ball.vy, vz: ball.vz, wx: ball.wx, wy: ball.wy, wz: ball.wz }, 2, AI_Z);
  if (r.hitPlane) { const D = DIFF[SET.diff], e = D.err; opp.target.set(clamp(r.hitPlane.x + rand(-e, e), -1.4, 1.4), clamp(r.hitPlane.y + rand(-e, e) * 0.5, 0.82, 1.35), AI_Z + 0.1); opp.missRoll = Math.random() < D.miss; }
}
function aiChoose(serve) {
  const D = DIFF[SET.diff]; let best = null, bestS = -1e9; const px = rig.position.x;
  for (let i = 0; i < 170; i++) {
    const v = serve ? { vx: rand(-0.6, 0.6), vy: rand(-0.9, 0.5), vz: rand(2.6, 5.8) } : { vx: rand(-1.5, 1.5), vy: rand(0.3, 1.7), vz: rand(3.2, 6.8) };
    const r = simulate({ x: ball.x, y: ball.y, z: ball.z, vx: v.vx, vy: v.vy, vz: v.vz, wx: 0, wy: rand(-40, 40), wz: 0 }, 1.6);
    const ok = serve ? (r.bounces[0] === -1 && r.bounces[1] === 1 && !r.net) : (r.bounces[0] === 1 && !r.net); if (!ok) continue;
    const lx = serve ? r.first.x : r.first.x; let s = -Math.abs(lx - (px > 0 ? -0.5 : 0.5)) * (SET.diff + 0.3) * 2 + Math.random() * (2 - SET.diff * 0.7);
    if (!serve) s += Math.min(0, -Math.abs(v.vz - 4.5) * 0.1); if (s > bestS) { bestS = s; best = v; }
  }
  return best;
}
function aiUpdate(dt) {
  const D = DIFF[SET.diff]; opp.cool -= dt;
  if (st.phase === 'serveWait' && st.server === 'ai') {
    if (!opp.serveBall) { st.serveT -= dt; ball.x = opp.x; ball.z = -TBL.L / 2 - 0.25; ball.vx = ball.vy = ball.vz = 0; ball.y = 1.0 + Math.sin(time * 4) * 0.01; if (st.serveT <= 0.7) { opp.serveBall = true; ball.vy = 2.0; Snd.serve(); } }
  }
  if (st.phase === 'serveWait' && st.server === 'ai' && opp.serveBall) { stepBallLoose(dt); if (ball.vy < 0 && ball.y < 1.04) { const v = aiChoose(true) || { vx: 0.2, vy: -0.4, vz: 5.2 }; Object.assign(ball, v, { wx: 0, wy: 0, wz: 0 }); opp.swing = 0.3; Snd.paddle(5); onHit('ai'); } return; }
  // acompanha o alvo
  const tgt = opp.target; if (st.phase !== 'rally' || st.lastHitter !== 'player') { tgt.x += (clamp(ball.x * 0.4, -0.5, 0.5) - tgt.x) * Math.min(1, dt * 2); }
  const k = Math.min(1, D.spd * dt / Math.max(0.01, opp.hand.distanceTo(tgt))); opp.hand.lerp(tgt, k);
  // rebater
  if (st.phase === 'rally' && st.lastHitter === 'player' && st.bounceRecv >= 1 && ball.vz < 0 && opp.cool <= 0) {
    const dx = ball.x - opp.hand.x, dy = ball.y - opp.hand.y, dz = ball.z - (opp.hand.z + 0.02);
    if (Math.hypot(dx, dy, dz) < 0.13 && ball.y > TBL.H + 0.015) {
      if (opp.missRoll) { opp.missRoll = false; opp.cool = 0.6; return; }
      const v = aiChoose(false) || { vx: 0, vy: 1.0, vz: 4.8 }; if (v) { Object.assign(ball, v, { wx: rand(-60, 20), wy: rand(-40, 40), wz: 0 }); opp.swing = 0.3; Snd.paddle(6); haptic(0.15); onHit('ai'); }
    }
  }
  opp.swing = Math.max(0, opp.swing - dt);
}
const _pev = { table: 0, net: false, floor: false };
function stepBallLoose(dt) { const n = Math.ceil(dt / (1 / 240)), h = dt / n; for (let i = 0; i < n; i++) { stepBall(ball, h, _pev); } }

/* ---------- Atualização do jogo ---------- */
let time = 0, last = 0, accum = 0;
function physicsFrame(dt) {
  hitCool -= dt; const n = Math.max(1, Math.ceil(dt / (1 / 240))), h = dt / n;
  const toss = (st.phase === 'serveWait' && st.server === 'player' && !st.tossed);
  if (st.phase === 'menu' || toss) return;
  if (st.phase === 'serveWait' && st.server === 'ai') return; // movido pela IA
  for (let i = 0; i < n; i++) {
    const prev = { x: ball.x, y: ball.y, z: ball.z };
    stepBall(ball, h, _pev);
    paddleCollide(prev, i / n, (i + 1) / n, h);
    if (_pev.table) { Snd.table(Math.abs(ball.vy) + 2); onTable(_pev.table > 0 ? 'player' : 'ai'); }
    if (_pev.net) { Snd.net(); }
    if (_pev.floor && st.phase === 'rally') { Snd.floor(); resolveFloor(); }
    if (_pev.floor && st.phase === 'serveWait') { if (st.server === 'player') { st.tossed = false; ball.vx = ball.vy = ball.vz = 0; setMsg('Lance a bola e bata nela no ar', 2); } }
  }
}
function resolveFloor() {
  if (!st.lastHitter) { st.phase = 'serveWait'; st.tossed = false; return; }
  const H = st.lastHitter; if (st.serving) point(other(H), 'Saque errado'); else if (st.bounceRecv >= 1) point(H, H === 'player' ? 'O Pico não alcançou' : 'Você não alcançou'); else point(other(H), H === 'player' ? 'Bola fora' : 'O Pico errou');
}
function holdServe(dt) {
  if (st.phase === 'serveWait' && st.server === 'player' && !st.tossed) {
    const bx = pad.pos.x + 0.0, hover = Math.sin(time * 3) * 0.012; ball.x = rig.position.x + pad.side * 0.1; ball.y = 1.0 + hover; ball.z = rig.position.z - 0.55; ball.vx = ball.vy = ball.vz = 0; void bx;
    if (Input.click) { st.tossed = true; ball.vy = 2.05; ball.vx = rand(-0.05, 0.05); Snd.serve(); }
  }
}
function opponentVisual(dt) {
  const h = opp.h, g = h.root, bz = -TBL.L / 2 - 0.62, oldX = opp.x;
  opp.x += (opp.hand.x * 0.85 - opp.x) * Math.min(1, dt * 5); opp.vx = (opp.x - oldX) / Math.max(dt, 1e-3); g.position.set(opp.x, 0, bz);
  opp.moodT -= dt; if (opp.moodT <= 0) opp.mood = 'neutral';
  const phase = opp.swing > 0 ? 1 - opp.swing / 0.3 : 0, sw = opp.swing > 0 ? Math.sin(phase * PI) : 0, look = clamp(Math.atan2(ball.x - opp.x, ball.z - bz), -0.9, 0.9);
  const cheer = opp.mood === 'cheer', sad = opp.mood === 'sad', jump = cheer ? Math.abs(Math.sin(time * 9)) * 0.1 : 0;
  animHuman(h, dt, { crouch: cheer ? 0.15 : sad ? 0.3 : 0.62, vx: opp.vx, look, swing: sw, mood: opp.mood, jump });
  g.updateMatrixWorld(true);
  const hp = new V(opp.hand.x, opp.hand.y, opp.hand.z + sw * 0.28);
  if (cheer) hp.set(opp.x - 0.22, 1.95 + Math.sin(time * 10) * 0.05 + jump, bz + 0.12); else if (sad) hp.set(opp.x - 0.2, 0.75, bz + 0.2);
  const n = new V(0, 0.28, 1).normalize(); aiPaddle.quaternion.setFromUnitVectors(new V(0, 0, 1), n); aiPaddle.position.copy(hp); aiPaddle.position.y -= 0.1;
  const S = h.J.spine.localToWorld(new V(-0.205, 0.45, 0)), hand = hp.clone().addScaledVector(new V(0, -1, 0), 0.03), d = Math.min(S.distanceTo(hand), 0.585), L = 0.295, hh = Math.sqrt(Math.max(0, L * L - (d / 2) * (d / 2)));
  const dir = hand.clone().sub(S).normalize(), mid = S.clone().addScaledVector(dir, d / 2), bend = new V(-0.4, -1, -0.5); bend.addScaledVector(dir, -bend.dot(dir)).normalize();
  const E = mid.addScaledVector(bend, hh), H = S.clone().addScaledVector(dir, d);
  seg(opp.armU, S, E, 0.05); seg(opp.armF, E, H, 0.04); opp.armH.position.copy(H);
}

/* ---------- Menu em 3D ---------- */
const menu = { c: document.createElement('canvas'), open: true, hover: null, btns: [], dirty: true };
menu.c.width = 1024; menu.c.height = 700; menu.t = new THREE.CanvasTexture(menu.c); menu.t.colorSpace = THREE.SRGBColorSpace;
menu.m = new THREE.Mesh(new THREE.PlaneGeometry(1.6, 1.094), new THREE.MeshBasicMaterial({ map: menu.t, transparent: true, depthTest: false })); menu.m.renderOrder = 50; scene.add(menu.m);
const cursor = new THREE.Mesh(new THREE.RingGeometry(0.008, 0.013, 20), new THREE.MeshBasicMaterial({ color: 0xffffff, depthTest: false })); cursor.renderOrder = 60; scene.add(cursor);
const laser = new THREE.Mesh(new THREE.CylinderGeometry(1, 1, 1, 6, 1, true), new THREE.MeshBasicMaterial({ color: 0x7fd3ff, transparent: true, opacity: 0.7, depthTest: false })); laser.renderOrder = 59; scene.add(laser);
function rr(c, x, y, w, h, r) { c.beginPath(); c.moveTo(x + r, y); c.arcTo(x + w, y, x + w, y + h, r); c.arcTo(x + w, y + h, x, y + h, r); c.arcTo(x, y + h, x, y, r); c.arcTo(x, y, x + w, y, r); c.closePath(); }
function drawMenu() {
  const c = menu.c.getContext('2d'), W = 1024, H = 700; menu.btns = []; c.clearRect(0, 0, W, H);
  c.fillStyle = 'rgba(12,16,34,.94)'; rr(c, 0, 0, W, H, 36); c.fill(); c.strokeStyle = '#4285f4'; c.lineWidth = 6; c.stroke();
  c.textAlign = 'center'; c.textBaseline = 'middle'; c.fillStyle = '#fff'; c.font = '700 64px sans-serif'; c.fillText('🏓 PING-PONG VR', W / 2, 70);
  c.font = '400 26px sans-serif'; c.fillStyle = '#9ec5ff'; c.fillText(st.over ? (st.p > st.a ? '🏆 Você venceu!' : 'O Pico venceu!') + '  ' + st.p + ' x ' + st.a : 'Aponte o controle e clique · primeiro a ' + SET.win + ' pontos', W / 2, 125);
  const btn = (id, x, y, w, h, label, fn, on) => { const hv = menu.hover === id; menu.btns.push({ id, x, y, w, h, fn }); rr(c, x, y, w, h, h / 2); c.fillStyle = on ? '#1a73e8' : (hv ? '#3a4370' : '#252b45'); c.fill(); if (hv) { c.strokeStyle = '#7fb0ff'; c.lineWidth = 4; c.stroke(); } c.fillStyle = '#fff'; c.font = '600 30px sans-serif'; c.fillText(label, x + w / 2, y + h / 2 + 2); };
  btn('play', 262, 160, 500, 96, st.over || st.p + st.a > 0 ? '▶ Nova partida' : '▶ Jogar', () => newGame(), false);
  c.fillStyle = '#cfd8ff'; c.font = '600 26px sans-serif'; c.textAlign = 'left'; c.fillText('Dificuldade', 60, 318);
  DIFF.forEach((d, i) => btn('d' + i, 300 + i * 220, 288, 200, 62, d.name, () => { SET.diff = i; saveSet(); }, SET.diff === i));
  c.fillStyle = '#cfd8ff'; c.fillText('Mão da raquete', 60, 398); [['auto', 'Auto'], ['right', 'Direita'], ['left', 'Esquerda']].forEach((h, i) => btn('h' + i, 300 + i * 220, 368, 200, 62, h[1], () => { SET.hand = h[0]; saveSet(); }, SET.hand === h[0]));
  c.fillStyle = '#cfd8ff'; c.fillText('Sua altura: ' + SET.height.toFixed(2) + ' m', 60, 478); btn('hm', 300, 448, 90, 62, '−', () => { SET.height = clamp(+(SET.height - 0.05).toFixed(2), 1.0, 2.1); saveSet(); applyHeight(); }, false); btn('hp', 410, 448, 90, 62, '+', () => { SET.height = clamp(+(SET.height + 0.05).toFixed(2), 1.0, 2.1); saveSet(); applyHeight(); }, false);
  btn('snd', 540, 448, 200, 62, SET.sound ? '🔊 Som' : '🔇 Mudo', () => { SET.sound = !SET.sound; saveSet(); }, false); btn('inv', 760, 448, 200, 62, SET.invert ? '⇄ Invertido' : '⇄ Normal', () => { SET.invert = !SET.invert; saveSet(); }, false);
  c.textAlign = 'center'; c.fillStyle = '#9aa3c0'; c.font = '400 23px sans-serif'; c.fillText('Mova o controle como uma raquete · deslize o touch de trás ← → para andar para os lados', W / 2, 568); c.fillText('Gatilho: lançar a bola no saque · botão lateral: pausar/menu', W / 2, 604); c.fillText('Melhor sequência de trocas: ' + st.best, W / 2, 648);
  menu.t.needsUpdate = true; menu.dirty = false;
}
function openMenu() { menu.open = true; menu.dirty = true; menu.m.visible = true; const f = new V(); camera.getWorldDirection(f); f.y = 0; f.normalize(); const p = new V(); camera.getWorldPosition(p); menu.m.position.set(p.x + f.x * 1.3, p.y - 0.05, p.z + f.z * 1.3); menu.m.rotation.set(0, Math.atan2(-f.x, -f.z), 0); if (st.phase !== 'over') st.phase = 'menu'; }
function closeMenu() { menu.open = false; menu.m.visible = false; cursor.visible = false; laser.visible = false; }
function applyHeight() { rig.position.y = SET.height; }
function newGame() { Snd.init(); Snd.click(); st.p = st.a = 0; st.over = false; st.best = 0; st.firstServer = Math.random() < 0.5 ? 'player' : 'ai'; closeMenu(); refreshBoards(); startServe(); }
function menuPointer() {
  if (!menu.open) return; const ctrl = mode === 'xr' ? activeCtrl() : null, ray = new THREE.Raycaster(); let o = new V(), d = new V();
  if (ctrl) { ctrl.getWorldQuaternion(new Q()); o.copy(pad.pos); d.copy(pad.F); } else if (mode === 'flat' && Input.mouse.has) { ray.setFromCamera(mouseNdc, camera); o.copy(ray.ray.origin); d.copy(ray.ray.direction); } else { camera.getWorldPosition(o); camera.getWorldDirection(d); }
  ray.set(o, d); const h = ray.intersectObject(menu.m, false)[0]; let id = null;
  if (h) { const px = h.uv.x * 1024, py = (1 - h.uv.y) * 700; for (const b of menu.btns) if (px >= b.x && px <= b.x + b.w && py >= b.y && py <= b.y + b.h) id = b.id; cursor.position.copy(h.point); cursor.lookAt(camera.getWorldPosition(new V())); cursor.visible = true; laser.visible = true; const L = o.distanceTo(h.point); laser.position.copy(o).addScaledVector(d, L / 2); laser.quaternion.setFromUnitVectors(new V(0, 1, 0), d); laser.scale.set(0.003, L, 0.003); } else { cursor.visible = false; laser.visible = !!ctrl; if (ctrl) { laser.position.copy(o).addScaledVector(d, 1); laser.quaternion.setFromUnitVectors(new V(0, 1, 0), d); laser.scale.set(0.003, 2, 0.003); } }
  if (id !== menu.hover) { menu.hover = id; menu.dirty = true; }
  if (Input.click && id) { const b = menu.btns.find(x => x.id === id); Snd.init(); Snd.click(); b.fn(); menu.dirty = true; }
}

/* ---------- Loop ---------- */
const mouseNdc = new THREE.Vector2(); let tickN = 0;
function movePlayer(dt) {
  if (menu.open) return; const lim = 1.45; rig.position.x = clamp(rig.position.x + Input.strafe * 2.0 * dt, -lim, lim); rig.position.z = clamp(rig.position.z - Input.depth * 1.2 * dt, 1.5, 2.5);
}
const eu = new THREE.Euler(), q1 = new Q(-Math.SQRT1_2, 0, 0, Math.SQRT1_2), q0 = new Q(), zee = new V(0, 0, 1); let orient = null, alpha0 = null, pitch = 0;
function updateOrientation() {
  if (mode === 'xr') return;
  if ((mode === 'split' || mode === 'gyro') && orient && orient.alpha != null) { if (alpha0 === null) alpha0 = orient.alpha; const a = (orient.alpha - alpha0) * PI / 180, b = (orient.beta || 0) * PI / 180, g = (orient.gamma || 0) * PI / 180, o = ((screen.orientation && screen.orientation.angle) || window.orientation || 90) * PI / 180; eu.set(b, a, -g, 'YXZ'); camera.quaternion.setFromEuler(eu); camera.quaternion.multiply(q1); camera.quaternion.multiply(q0.setFromAxisAngle(zee, -o)); }
  else camera.rotation.set(pitch, 0, 0, 'YXZ');
}
function frame(ms) { const dt = Math.min(0.05, Math.max(0.001, (ms - last) / 1000)); last = ms; update(dt); render(); }
function update(dt) {
  time += dt; pollInput();
  if (Input.menu) { if (menu.open && st.phase !== 'menu' && st.phase !== 'over') closeMenu(); else if (!menu.open) openMenu(); }
  updateOrientation(); movePlayer(dt); rig.updateMatrixWorld(true);
  updatePaddle(dt); holdServe(dt);
  if (!menu.open) { if (st.phase === 'serveWait' && st.server === 'ai') aiUpdate(dt); else { aiUpdate(dt); physicsFrame(dt); } }
  if (st.phase === 'dead') { st.deadT -= dt; if (st.deadT <= 0 && !st.over) { st.tossed = false; startServe(); } }
  if (msg.t > 0) { msg.t -= dt; if (msg.t <= 0) refreshBoards(); }
  // visual
  ballMesh.position.set(ball.x, ball.y, ball.z); ballMesh.rotation.x += ball.wx * dt * 0.1; ballMesh.rotation.z += ball.wz * dt * 0.1;
  const onT = Math.abs(ball.x) <= TBL.W / 2 + 0.05 && Math.abs(ball.z) <= TBL.L / 2 + 0.05, gy = onT ? TBL.H + 0.002 : 0.006, hgt = Math.max(0, ball.y - gy);
  ballShadow.position.set(ball.x, gy, ball.z); const ss = 1 + hgt * 0.8; ballShadow.scale.set(ss, ss, ss); ballShadow.material.opacity = clamp(1 - hgt * 0.6, 0.2, 1);
  const sp = Math.hypot(ball.vx, ball.vy, ball.vz); trail.forEach((t, i) => { const k = (i + 1) * 0.012 * Math.min(1, sp / 8); t.position.set(ball.x - ball.vx * k, ball.y - ball.vy * k, ball.z - ball.vz * k); t.visible = sp > 3; });
  crowdUpdate(dt); opponentVisual(dt); if (menu.open) { if (menu.dirty) drawMenu(); menuPointer(); }
}
function render() {
  if (mode === 'split') { const s = renderer.getSize(new THREE.Vector2()), hw = s.x >> 1; camera.aspect = hw / s.y; camera.updateProjectionMatrix(); camera.updateMatrixWorld(true); stereo.update(camera); renderer.setScissorTest(true); renderer.setViewport(0, 0, hw, s.y); renderer.setScissor(0, 0, hw, s.y); renderer.render(scene, stereo.cameraL); renderer.setViewport(hw, 0, hw, s.y); renderer.setScissor(hw, 0, hw, s.y); renderer.render(scene, stereo.cameraR); renderer.setScissorTest(false); }
  else renderer.render(scene, camera);
}
function resize() { if (renderer.xr.isPresenting) return; const w = innerWidth, h = innerHeight; renderer.setSize(w, h, false); if (mode !== 'split') { camera.aspect = w / h; camera.updateProjectionMatrix(); } }
addEventListener('resize', resize); resize();

/* ---------- Navegador: mouse, toque, teclado, giroscópio ---------- */
function setMouse(e) { const r = canvas.getBoundingClientRect(); mouseNdc.set((e.clientX - r.left) / r.width * 2 - 1, -((e.clientY - r.top) / r.height) * 2 + 1); Input.mouse.has = true; }
canvas.addEventListener('pointermove', e => { if (mode === 'xr') return; setMouse(e); });
canvas.addEventListener('pointerdown', e => { if (mode === 'xr') return; setMouse(e); Input.tap = true; });
addEventListener('keydown', e => { Input.keys[e.key.toLowerCase()] = true; if ([' ', 'arrowup', 'arrowdown'].includes(e.key.toLowerCase())) e.preventDefault(); });
addEventListener('keyup', e => { Input.keys[e.key.toLowerCase()] = false; });
addEventListener('deviceorientation', e => { orient = e; });

/* ---------- Início ---------- */
let started = false;
function begin(m) {
  Snd.init(); Snd.murmur(); mode = m; $('#overlay').classList.add('hide'); started = true; camera.fov = m === 'split' ? 90 : m === 'gyro' ? 80 : 75; resize(); camera.updateProjectionMatrix();
  rig.position.set(0, SET.height, 1.95); st.firstServer = 'player'; refreshBoards(); openMenu(); renderer.setAnimationLoop(frame);
}
async function startXR() {
  try { const s = await navigator.xr.requestSession('immersive-vr', { optionalFeatures: ['local', 'local-floor'] }); renderer.xr.setReferenceSpaceType('local'); await renderer.xr.setSession(s); xrSession = s; s.addEventListener('end', () => { xrSession = null; mode = 'flat'; resize(); $('#overlay').classList.remove('hide'); $('#msg').textContent = 'Sessão VR encerrada.'; });
    begin('xr'); } catch (e) { $('#msg').textContent = 'Não foi possível iniciar o VR: ' + (e && e.message ? e.message : e) + ' — tente o modo giroscópio.'; }
}
async function startMotion(m) { try { if (typeof DeviceOrientationEvent !== 'undefined' && DeviceOrientationEvent.requestPermission) await DeviceOrientationEvent.requestPermission(); } catch (e) {} try { await document.documentElement.requestFullscreen(); } catch (e) {} try { await screen.orientation.lock('landscape'); } catch (e) {} alpha0 = null; begin(m); }
$('#btnFlat').onclick = () => begin('flat'); $('#btnGyro').onclick = () => startMotion('gyro'); $('#btnSplit').onclick = () => startMotion('split'); $('#btnXR').onclick = startXR;
if (navigator.xr && navigator.xr.isSessionSupported) navigator.xr.isSessionSupported('immersive-vr').then(ok => { $('#btnXR').disabled = !ok; $('#xrNote').textContent = ok ? 'Recomendado · raquete segue o controle' : 'Seu navegador não liberou WebXR — use o modo giroscópio'; }).catch(() => { $('#xrNote').textContent = 'WebXR indisponível — use o modo giroscópio'; });
else $('#xrNote').textContent = 'WebXR não encontrado neste navegador — use o modo giroscópio';
if (/[?&]app=1/.test(location.search)) { $('#btnXR').style.display = 'none'; setTimeout(() => $('#btnGyro').click(), 50); }
rig.position.set(0, SET.height, 1.95); refreshBoards(); renderer.setAnimationLoop(frame); closeMenu(); menu.m.visible = false;
window.__pp = { st, ball, pad, opp, rig, camera, scene, renderer, SET, begin, newGame, startServe, physicsFrame, aiUpdate, openMenu, closeMenu, Input, mouseNdc, setMode: m => { mode = m; }, DIFF, point, TBL };
