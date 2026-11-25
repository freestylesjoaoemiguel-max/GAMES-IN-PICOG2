'use strict';
/* Mundo 3D: céus 360°, personagens, casas, mapas, partículas, colisão */
const PI = Math.PI, TAU = PI * 2;
const lerp = (a, b, t) => a + (b - a) * t;
const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
function mulberry(a) { return function () { a |= 0; a = a + 0x6D2B79F5 | 0; let t = Math.imul(a ^ a >>> 15, 1 | a); t = t + Math.imul(t ^ t >>> 7, 61 | t) ^ t; return ((t ^ t >>> 14) >>> 0) / 4294967296; }; }

/* ---------- Materiais e geometrias ---------- */
const MAT = {
  lit: new THREE.MeshLambertMaterial({ vertexColors: true }),
  unlit: new THREE.MeshBasicMaterial({ vertexColors: true })
};
const lamCache = {};
function lam(hex, unlit) {
  const k = hex + (unlit ? 'u' : '');
  return lamCache[k] || (lamCache[k] = unlit ? new THREE.MeshBasicMaterial({ color: hex }) : new THREE.MeshLambertMaterial({ color: hex, flatShading: true }));
}
const GEO = {
  box: new THREE.BoxGeometry(1, 1, 1),
  ico0: new THREE.IcosahedronGeometry(1, 0), ico1: new THREE.IcosahedronGeometry(1, 1),
  oct: new THREE.OctahedronGeometry(1, 0),
  cone4: new THREE.CylinderGeometry(0, 1, 1, 4, 1).rotateY(PI / 4),
  cyl: {}, cone: {}, tap: {},
  cylG(s) { return this.cyl[s] || (this.cyl[s] = new THREE.CylinderGeometry(1, 1, 1, s, 1)); },
  coneG(s) { return this.cone[s] || (this.cone[s] = new THREE.CylinderGeometry(0, 1, 1, s, 1)); },
  tapG(rt, rb, s) { const k = rt + '_' + rb + '_' + s; return this.tap[k] || (this.tap[k] = new THREE.CylinderGeometry(rt, rb, 1, s, 1)); },
  sph: new THREE.SphereGeometry(1, 14, 10)
};

/* ---------- Baker: junta toda a cenário estática em 2 malhas (rápido no Pico G2) ---------- */
const _c = new THREE.Color();
class Baker {
  constructor(rnd) {
    this.rnd = rnd || Math.random;
    this.L = { p: [], n: [], c: [] }; this.U = { p: [], n: [], c: [] };
    this.base = new THREE.Matrix4(); this._m = new THREE.Matrix4(); this._q = new THREE.Quaternion();
    this._e = new THREE.Euler(); this._s = new THREE.Vector3(); this._p = new THREE.Vector3();
  }
  setBase(x, y, z, ry) { this.base.makeRotationY(ry || 0); this.base.setPosition(x, y, z); return this; }
  clearBase() { this.base.identity(); return this; }
  _add(geo, x, y, z, sx, sy, sz, rx, ry, rz, hex, o) {
    o = o || {};
    this._e.set(rx || 0, ry || 0, rz || 0, 'YXZ'); this._q.setFromEuler(this._e);
    this._m.compose(this._p.set(x, y, z), this._q, this._s.set(sx, sy, sz));
    this._m.premultiply(this.base);
    const g = geo.index ? geo.toNonIndexed() : geo.clone();
    g.applyMatrix4(this._m);
    const b = o.unlit ? this.U : this.L;
    _c.set(hex);
    const j = 1 + (this.rnd() - 0.5) * (o.j === undefined ? 0.14 : o.j);
    const pa = g.attributes.position.array, na = new Float32Array(pa.length);
    for (let i = 0; i < pa.length; i += 9) { // normais planas por triângulo (visual facetado sem depender de flatShading)
      const ux = pa[i + 3] - pa[i], uy = pa[i + 4] - pa[i + 1], uz = pa[i + 5] - pa[i + 2], vx = pa[i + 6] - pa[i], vy = pa[i + 7] - pa[i + 1], vz = pa[i + 8] - pa[i + 2];
      let nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx; const l = Math.hypot(nx, ny, nz);
      if (l < 1e-9) { nx = 0; ny = 1; nz = 0; } else { nx /= l; ny /= l; nz /= l; }
      for (let k = 0; k < 3; k++) { na[i + k * 3] = nx; na[i + k * 3 + 1] = ny; na[i + k * 3 + 2] = nz; }
    }
    for (let i = 0; i < pa.length; i++) { b.p.push(pa[i]); b.n.push(na[i]); }
    const cnt = pa.length / 3;
    for (let i = 0; i < cnt; i++) b.c.push(_c.r * j, _c.g * j, _c.b * j);
    g.dispose();
  }
  box(x, y, z, sx, sy, sz, hex, o) { o = o || {}; this._add(GEO.box, x, y, z, sx, sy, sz, o.rx, o.ry, o.rz, hex, o); }
  cyl(x, y, z, r, h, hex, o) { o = o || {}; this._add(GEO.cylG(o.seg || 8), x, y, z, r * (o.sx || 1), h, r * (o.sz || 1), o.rx, o.ry, o.rz, hex, o); }
  tcyl(x, y, z, rt, rb, h, hex, o) { o = o || {}; this._add(GEO.tapG(rt, rb, o.seg || 8), x, y, z, 1, h, 1, o.rx, o.ry, o.rz, hex, o); }
  cone(x, y, z, r, h, hex, o) { o = o || {}; this._add(GEO.coneG(o.seg || 8), x, y, z, r * (o.sx || 1), h, r * (o.sz || 1), o.rx, o.ry, o.rz, hex, o); }
  pyr(x, y, z, hw, hd, h, hex, o) { o = o || {}; this._add(GEO.cone4, x, y, z, hw / 0.7071, h, hd / 0.7071, o.rx, o.ry, o.rz, hex, o); }
  sph(x, y, z, r, hex, o) { o = o || {}; this._add(o.d === 0 ? GEO.ico0 : GEO.ico1, x, y, z, r * (o.sx || 1), r * (o.sy || 1), r * (o.sz || 1), o.rx, o.ry, o.rz, hex, o); }
  oct(x, y, z, r, hex, o) { o = o || {}; this._add(GEO.oct, x, y, z, r * (o.sx || 1), r * (o.sy || 1), r * (o.sz || 1), o.rx, o.ry, o.rz, hex, o); }
  build() {
    const grp = new THREE.Group();
    const mk = (b, mat) => {
      if (!b.p.length) return;
      const g = new THREE.BufferGeometry();
      g.setAttribute('position', new THREE.Float32BufferAttribute(b.p, 3));
      g.setAttribute('normal', new THREE.Float32BufferAttribute(b.n, 3));
      g.setAttribute('color', new THREE.Float32BufferAttribute(b.c, 3));
      g.computeBoundingSphere();
      grp.add(new THREE.Mesh(g, mat));
    };
    mk(this.L, MAT.lit); mk(this.U, MAT.unlit);
    return grp;
  }
}

/* ---------- Céu 360° (panorama equirretangular desenhado em canvas) ---------- */
function skyCanvas(kind, W) {
  W = W || 2048; const H = W / 2;
  const c = document.createElement('canvas'); c.width = W; c.height = H;
  const x = c.getContext('2d'); const rnd = mulberry(kind.length * 977 + 13);
  const G = {
    village: [[0, '#2468c8'], [.25, '#4f9be6'], [.42, '#9ad4f6'], [.5, '#e6f6ff'], [.5001, '#cfe9d5'], [1, '#9fd0a0']],
    desert: [[0, '#1d1450'], [.22, '#5b2a7a'], [.36, '#c2487a'], [.45, '#ff8c42'], [.5, '#ffd38a'], [.5001, '#e6b878'], [1, '#c9966a']],
    snow: [[0, '#050d26'], [.25, '#14305e'], [.42, '#3d6e9f'], [.5, '#9cc3dd'], [.5001, '#dfeaf2'], [1, '#c9d9e6']],
    lobby: [[0, '#02010a'], [.3, '#0d0630'], [.5, '#3a1a6e'], [.5001, '#1b0d3d'], [1, '#07031a']]
  }[kind];
  const gr = x.createLinearGradient(0, 0, 0, H); G.forEach(s => gr.addColorStop(s[0], s[1]));
  x.fillStyle = gr; x.fillRect(0, 0, W, H);
  const wrap = fn => { for (const dx of [-W, 0, W]) fn(dx); };
  const glow = (cx, cy, r, rgb, a) => wrap(dx => {
    const g = x.createRadialGradient(cx + dx, cy, 0, cx + dx, cy, r);
    g.addColorStop(0, `rgba(${rgb},${a})`); g.addColorStop(1, `rgba(${rgb},0)`);
    x.fillStyle = g; x.fillRect(cx + dx - r, cy - r, r * 2, r * 2);
  });
  const stars = (n, maxY, bright) => {
    for (let i = 0; i < n; i++) {
      const sx = rnd() * W, sy = Math.pow(rnd(), 0.8) * H * maxY, r = (0.4 + rnd() * 1.4) * (W / 2048);
      x.fillStyle = `rgba(255,255,${200 + rnd() * 55 | 0},${(0.3 + rnd() * 0.7) * bright})`;
      x.beginPath(); x.ellipse(sx, sy, r * (1 + sy / H * 0.8), r, 0, 0, TAU); x.fill();
    }
  };
  const ridge = (base, amp, color, seeds, tipColor) => {
    x.fillStyle = color; x.beginPath(); x.moveTo(0, H * base + 2);
    const phase = seeds.map(() => rnd() * TAU);
    for (let i = 0; i <= W; i += 4) {
      let h = 0; seeds.forEach((f, k) => { h += Math.sin(i / W * TAU * f + phase[k]) * (1 / (1 + k * 0.6)); });
      x.lineTo(i, H * base - Math.abs(h) * amp * (W / 2048));
    }
    x.lineTo(W, H * base + 2); x.closePath(); x.fill();
  };
  const clouds = (n, col, yMin, yMax, sz) => {
    for (let i = 0; i < n; i++) {
      const cx = rnd() * W, cy = H * (yMin + rnd() * (yMax - yMin)), s = sz * (0.6 + rnd());
      for (let k = 0; k < 7; k++) glow(cx + (rnd() - 0.5) * s * 1.6, cy + (rnd() - 0.5) * s * 0.18, s * (0.35 + rnd() * 0.35), col, 0.5);
    }
  };
  if (kind === 'village') {
    glow(W * 0.3, H * 0.3, H * 0.18, '255,255,220', 0.9); glow(W * 0.3, H * 0.3, H * 0.05, '255,255,255', 1);
    clouds(36, '255,255,255', 0.14, 0.44, W / 14);
    ridge(0.5, 46, '#8fb4d9', [3, 5, 9, 14]); ridge(0.5, 24, '#6fa070', [4, 7, 13, 19]);
  } else if (kind === 'desert') {
    glow(W * 0.62, H * 0.455, H * 0.28, '255,160,70', 0.85); glow(W * 0.62, H * 0.46, H * 0.07, '255,240,200', 1);
    stars(500, 0.25, 0.8); clouds(10, '255,150,160', 0.25, 0.42, W / 11);
    ridge(0.5, 30, '#9a4a5e', [2, 4, 7, 11]); ridge(0.5, 14, '#c9703a', [3, 6, 10, 17]);
  } else if (kind === 'snow') {
    stars(900, 0.46, 1); glow(W * 0.72, H * 0.24, H * 0.1, '220,235,255', 0.55); glow(W * 0.72, H * 0.24, H * 0.03, '255,255,255', 1);
    for (let i = 0; i < W; i += 3) {
      const yb = H * 0.14 + Math.sin(i / W * TAU * 3) * H * 0.04 + Math.sin(i / W * TAU * 7) * H * 0.015;
      const a = (0.5 + 0.5 * Math.sin(i / W * TAU * 5 + 1)) * 0.55;
      const g = x.createLinearGradient(0, yb, 0, yb + H * 0.2);
      g.addColorStop(0, `rgba(90,255,170,0)`); g.addColorStop(0.25, `rgba(90,255,170,${a})`); g.addColorStop(0.7, `rgba(150,90,255,${a * 0.6})`); g.addColorStop(1, 'rgba(150,90,255,0)');
      x.fillStyle = g; x.fillRect(i, yb, 3, H * 0.2);
    }
    ridge(0.5, 44, '#6b8cae', [3, 5, 8, 13]); ridge(0.5, 22, '#cfe0ee', [4, 7, 12, 19]);
  } else {
    glow(W * 0.2, H * 0.28, H * 0.35, '120,60,255', 0.28); glow(W * 0.62, H * 0.18, H * 0.4, '40,120,255', 0.22);
    glow(W * 0.85, H * 0.35, H * 0.3, '255,60,160', 0.2); glow(W * 0.45, H * 0.4, H * 0.25, '60,255,220', 0.12);
    x.save(); x.globalAlpha = 0.35; x.fillStyle = '#b090ff';
    for (let i = 0; i < 220; i++) { const px = rnd() * W, py = H * (0.25 + rnd() * 0.12 + Math.sin(px / W * TAU * 2) * 0.05); x.fillRect(px, py, 2, 2); }
    x.restore();
    stars(2200, 0.5, 1);
    const pg = x.createRadialGradient(W * 0.7, H * 0.3, 0, W * 0.7, H * 0.3, H * 0.09);
    pg.addColorStop(0, '#ffd1f0'); pg.addColorStop(1, '#6a2fa8'); x.fillStyle = pg; x.beginPath(); x.arc(W * 0.7, H * 0.3, H * 0.09, 0, TAU); x.fill();
    x.strokeStyle = 'rgba(255,220,255,.55)'; x.lineWidth = H * 0.006; x.beginPath(); x.ellipse(W * 0.7, H * 0.3, H * 0.17, H * 0.03, -0.35, 0, TAU); x.stroke();
  }
  return c;
}
function skyTexture(kind) {
  const t = new THREE.CanvasTexture(skyCanvas(kind, 2048));
  t.colorSpace = THREE.SRGBColorSpace; t.wrapS = THREE.RepeatWrapping; t.repeat.x = -1; t.generateMipmaps = false; t.minFilter = THREE.LinearFilter;
  return t;
}

/* ---------- Partículas ---------- */
let _dot = null;
function dotTex() {
  if (_dot) return _dot;
  const c = document.createElement('canvas'); c.width = c.height = 64; const x = c.getContext('2d');
  const g = x.createRadialGradient(32, 32, 0, 32, 32, 32); g.addColorStop(0, 'rgba(255,255,255,1)'); g.addColorStop(.4, 'rgba(255,255,255,.6)'); g.addColorStop(1, 'rgba(255,255,255,0)');
  x.fillStyle = g; x.fillRect(0, 0, 64, 64); _dot = new THREE.CanvasTexture(c); return _dot;
}
function makePoints(n, size, color, additive) {
  const g = new THREE.BufferGeometry(); g.setAttribute('position', new THREE.Float32BufferAttribute(new Float32Array(n * 3), 3));
  const m = new THREE.PointsMaterial({ size, map: dotTex(), color, transparent: true, depthWrite: false, blending: additive ? THREE.AdditiveBlending : THREE.NormalBlending, sizeAttenuation: true });
  const p = new THREE.Points(g, m); p.frustumCulled = false; return p;
}

/* ---------- Colisão ---------- */
function collide(M, pos, r) {
  if (!M) return;
  for (let it = 0; it < 2; it++) {
    for (const b of M.boxes) {
      if (b.active === false) continue;
      const cx = clamp(pos.x, b.x0, b.x1), cz = clamp(pos.z, b.z0, b.z1);
      let dx = pos.x - cx, dz = pos.z - cz; const d2 = dx * dx + dz * dz;
      if (d2 < r * r) {
        if (d2 > 1e-6) { const d = Math.sqrt(d2), k = (r - d) / d; pos.x += dx * k; pos.z += dz * k; }
        else { const l = pos.x - b.x0, rr = b.x1 - pos.x, t = pos.z - b.z0, bt = b.z1 - pos.z, m = Math.min(l, rr, t, bt);
          if (m === l) pos.x = b.x0 - r; else if (m === rr) pos.x = b.x1 + r; else if (m === t) pos.z = b.z0 - r; else pos.z = b.z1 + r; }
      }
    }
    for (const c of M.circles) {
      const dx = pos.x - c.x, dz = pos.z - c.z, d2 = dx * dx + dz * dz, rr = r + c.r;
      if (d2 < rr * rr && d2 > 1e-6) { const d = Math.sqrt(d2), k = (rr - d) / d; pos.x += dx * k; pos.z += dz * k; }
    }
  }
  const R = M.radius || 62, d = Math.hypot(pos.x, pos.z);
  if (d > R) { pos.x *= R / d; pos.z *= R / d; }
}

/* ---------- Personagens 3D (com braços, pernas, rosto, cabelo, chapéus) ---------- */
const CG = { sph: new THREE.SphereGeometry(1, 14, 10), box: new THREE.BoxGeometry(1, 1, 1), cyl: new THREE.CylinderGeometry(1, 1, 1, 12, 1), cone: new THREE.CylinderGeometry(0, 1, 1, 12, 1), torus: new THREE.TorusGeometry(1, 0.28, 8, 18) };
function part(g, mat, x, y, z, sx, sy, sz, parent) {
  const m = new THREE.Mesh(g, mat); m.position.set(x, y, z); m.scale.set(sx, sy, sz); if (parent) parent.add(m); return m;
}
function createCharacter(o) {
  const root = new THREE.Group(), S = o.scale || 1;
  const body = new THREE.Group(); root.add(body);
  const skin = lam(o.skin || 0xe8b88a), shirt = lam(o.shirt || 0x4285f4), pants = lam(o.pants || 0x333a55), hairM = lam(o.hair || 0x3b2314), dark = lam(0x1b1b22), white = lam(0xffffff);
  const legs = [], arms = [], fores = [];
  [-1, 1].forEach(sx => {
    const p = new THREE.Group(); p.position.set(sx * 0.1, 0.82, 0); root.add(p);
    part(CG.cyl, pants, 0, -0.38, 0, 0.082, 0.76, 0.082, p);
    part(CG.box, dark, 0, -0.8, 0.04, 0.1, 0.07, 0.17, p);
    legs.push(p);
  });
  const torso = part(CG.cyl, shirt, 0, 1.13, 0, 0.2, 0.62, 0.135, body);
  part(CG.sph, shirt, 0, 1.43, 0, 0.215, 0.1, 0.14, body);
  part(CG.box, lam(0x2a2a35), 0, 0.84, 0, 0.4, 0.07, 0.25, body);
  [-1, 1].forEach(sx => {
    const sh = new THREE.Group(); sh.position.set(sx * 0.265, 1.4, 0); body.add(sh);
    part(CG.cyl, shirt, 0, -0.15, 0, 0.058, 0.3, 0.058, sh);
    const el = new THREE.Group(); el.position.set(0, -0.3, 0); sh.add(el);
    part(CG.cyl, shirt, 0, -0.13, 0, 0.05, 0.27, 0.05, el);
    part(CG.sph, skin, 0, -0.29, 0, 0.062, 0.062, 0.062, el);
    arms.push(sh); fores.push(el);
  });
  const head = new THREE.Group(); head.position.set(0, 1.6, 0); body.add(head);
  part(CG.sph, skin, 0, 0, 0, 0.19, 0.2, 0.19, head);
  [-1, 1].forEach(sx => {
    part(CG.sph, white, sx * 0.07, 0.025, 0.165, 0.045, 0.05, 0.03, head);
    part(CG.sph, dark, sx * 0.07, 0.025, 0.188, 0.024, 0.028, 0.015, head);
    part(CG.sph, lam(0xf2a0a0), sx * 0.11, -0.035, 0.15, 0.04, 0.025, 0.02, head);
  });
  part(CG.sph, lam(0xd9a07f), 0, -0.01, 0.19, 0.028, 0.028, 0.03, head);
  const mouth = part(CG.box, lam(0x7a2b2b), 0, -0.07, 0.178, 0.06, 0.013, 0.01, head);
  const hs = o.hairStyle || 'short';
  if (hs !== 'none') {
    part(CG.sph, hairM, 0, 0.055, -0.02, 0.2, 0.17, 0.2, head);
    if (hs === 'long') part(CG.box, hairM, 0, -0.1, -0.11, 0.2, 0.3, 0.07, head);
    if (hs === 'bun') part(CG.sph, hairM, 0, 0.2, -0.06, 0.08, 0.08, 0.08, head);
  }
  if (o.accessory === 'beard') part(CG.sph, hairM.clone ? lam(o.hair && o.hair > 0xaaaaaa ? o.hair : 0x6b6b6b) : hairM, 0, -0.1, 0.1, 0.12, 0.08, 0.1, head);
  if (o.accessory === 'glasses') [-1, 1].forEach(sx => { const t = part(CG.torus, dark, sx * 0.07, 0.03, 0.19, 0.045, 0.045, 0.02, head); });
  if (o.accessory === 'scarf') { const t = part(CG.torus, lam(o.scarfColor || 0xffd166), 0, 1.45, 0.0, 0.17, 0.17, 0.17, body); t.rotation.x = PI / 2; }
  if (o.accessory === 'apron') part(CG.box, lam(0xfaf6ee), 0, 1.05, 0.125, 0.3, 0.5, 0.02, body);
  const hc = o.hatColor || 0xcc4444;
  if (o.hat === 'straw') { part(CG.cyl, lam(hc), 0, 0.16, 0, 0.3, 0.02, 0.3, head); part(CG.cyl, lam(hc), 0, 0.22, 0, 0.15, 0.1, 0.15, head); }
  if (o.hat === 'beanie') { part(CG.sph, lam(hc), 0, 0.09, 0, 0.205, 0.17, 0.205, head); part(CG.sph, white, 0, 0.27, 0, 0.055, 0.055, 0.055, head); }
  if (o.hat === 'turban') { part(CG.sph, lam(hc), 0, 0.1, 0, 0.22, 0.15, 0.22, head); part(CG.sph, lam(hc), 0.02, 0.22, 0.02, 0.1, 0.07, 0.1, head); }
  if (o.hat === 'guard') { part(CG.sph, lam(hc), 0, 0.07, 0, 0.21, 0.16, 0.21, head); part(CG.cyl, lam(0x555a66), 0, 0.08, 0.17, 0.12, 0.015, 0.06, head); }
  root.scale.setScalar(S);
  const ch = { root, head, mouth, arms, fores, legs, torso, ph: Math.random() * 6, sw: 0, t: 0 };
  ch.height = 1.9 * S;
  ch.update = function (dt, walking, talking, waving) {
    this.t += dt; this.ph += dt * (walking ? 7.5 : 0);
    this.sw += ((walking ? 1 : 0) - this.sw) * Math.min(1, dt * 8);
    const sw = this.sw, ph = this.ph, t = this.t;
    legs[0].rotation.x = Math.sin(ph) * 0.7 * sw; legs[1].rotation.x = -Math.sin(ph) * 0.7 * sw;
    body.position.y = Math.abs(Math.sin(ph)) * 0.04 * sw + Math.sin(t * 2) * 0.004;
    torso.scale.y = 0.62 + Math.sin(t * 2) * 0.006;
    arms[1].rotation.x = -Math.sin(ph) * 0.6 * sw; arms[1].rotation.z = -0.08;
    arms[0].rotation.x = Math.sin(ph) * 0.6 * sw; arms[0].rotation.z = 0.08;
    fores[0].rotation.x = fores[1].rotation.x = -0.15 - sw * 0.2;
    head.rotation.y = Math.sin(t * 0.7) * 0.12; head.rotation.x = 0;
    mouth.scale.y = 0.013;
    if (talking) {
      mouth.scale.y = 0.013 + Math.abs(Math.sin(t * 14)) * 0.035; head.rotation.x = Math.sin(t * 5) * 0.06;
      arms[1].rotation.x = -0.9 + Math.sin(t * 3.1) * 0.3; arms[1].rotation.z = -0.2; fores[1].rotation.x = -0.9 + Math.sin(t * 4.4) * 0.3;
    }
    if (waving) {
      arms[0].rotation.x = 0; arms[0].rotation.z = 2.7 + Math.sin(t * 3) * 0.1; fores[0].rotation.z = Math.sin(t * 11) * 0.45; fores[0].rotation.x = 0;
    } else fores[0].rotation.z = 0;
  };
  return ch;
}

function createRobot(o) {
  const root = new THREE.Group(), body = new THREE.Group(); root.add(body);
  const blue = lam(o.color || 0x4285f4), white = lam(0xf4f7ff), dark = lam(0x20243a), glow = lam(0x7ff0ff, true), amber = lam(0xffc83d, true);
  part(CG.sph, blue, 0, 0.95, 0, 0.3, 0.34, 0.26, body);
  part(CG.cyl, white, 0, 0.66, 0, 0.16, 0.06, 0.16, body);
  part(CG.sph, lam(0x2a2e4a), 0, 0.35, 0, 0.14, 0.22, 0.14, body);
  part(CG.sph, glow, 0, 0.1, 0, 0.17, 0.05, 0.17, body);
  const head = new THREE.Group(); head.position.set(0, 1.5, 0); body.add(head);
  part(CG.box, white, 0, 0, 0, 0.44, 0.34, 0.36, head);
  part(CG.box, dark, 0, 0.02, 0.17, 0.36, 0.2, 0.05, head);
  const eyes = [-1, 1].map(sx => part(CG.box, glow, sx * 0.09, 0.03, 0.2, 0.07, 0.07, 0.02, head));
  const mouth = part(CG.box, glow, 0, -0.06, 0.2, 0.1, 0.012, 0.02, head);
  part(CG.cyl, dark, 0, 0.22, 0, 0.015, 0.14, 0.015, head);
  const bulb = part(CG.sph, amber, 0, 0.31, 0, 0.04, 0.04, 0.04, head);
  [-1, 1].forEach(sx => part(CG.cyl, blue, sx * 0.24, 0, 0, 0.04, 0.12, 0.08, head).rotation.z = PI / 2);
  const arms = [], fores = [];
  [-1, 1].forEach(sx => {
    const sh = new THREE.Group(); sh.position.set(sx * 0.36, 1.05, 0); body.add(sh);
    part(CG.sph, white, 0, 0, 0, 0.07, 0.07, 0.07, sh);
    part(CG.cyl, lam(0xb7c4e8), 0, -0.14, 0, 0.035, 0.28, 0.035, sh);
    const el = new THREE.Group(); el.position.set(0, -0.28, 0); sh.add(el);
    part(CG.cyl, lam(0xb7c4e8), 0, -0.12, 0, 0.03, 0.24, 0.03, el);
    part(CG.sph, blue, 0, -0.26, 0, 0.06, 0.06, 0.06, el);
    arms.push(sh); fores.push(el);
  });
  const ch = { root, head, mouth, arms, fores, legs: [], t: 0, height: 1.9, robot: true };
  ch.update = function (dt, walking, talking, waving) {
    this.t += dt; const t = this.t;
    body.position.y = 0.25 + Math.sin(t * 2) * 0.06; head.rotation.y = Math.sin(t * 0.8) * 0.3;
    head.rotation.z = Math.sin(t * 1.3) * 0.05; bulb.material = Math.sin(t * 4) > 0 ? amber : lam(0x886622, true);
    arms[0].rotation.z = 0.35 + Math.sin(t * 1.7) * 0.1; arms[1].rotation.z = -0.35 - Math.sin(t * 1.7) * 0.1;
    arms[0].rotation.x = arms[1].rotation.x = 0; fores[0].rotation.x = fores[1].rotation.x = -0.4;
    mouth.scale.y = 0.012; mouth.scale.x = 0.1;
    if (talking) { mouth.scale.y = 0.012 + Math.abs(Math.sin(t * 15)) * 0.04; arms[1].rotation.z = -0.5; arms[1].rotation.x = -0.8 + Math.sin(t * 3) * 0.3; }
    if (waving) { arms[0].rotation.z = 2.5 + Math.sin(t * 3) * 0.1; fores[0].rotation.z = Math.sin(t * 11) * 0.5; } else fores[0].rotation.z = 0;
  };
  return ch;
}

function makeTag(def) {
  const c = document.createElement('canvas'); c.width = 384; c.height = 112; const x = c.getContext('2d');
  x.fillStyle = 'rgba(20,24,40,.82)'; x.beginPath();
  const w = 384, h = 100, r = 28; x.moveTo(r, 0); x.arcTo(w, 0, w, h, r); x.arcTo(w, h, 0, h, r); x.arcTo(0, h, 0, 0, r); x.arcTo(0, 0, w, 0, r); x.fill();
  x.strokeStyle = 'rgba(255,255,255,.35)'; x.lineWidth = 3; x.stroke();
  x.fillStyle = '#fff'; x.font = '700 38px Roboto,system-ui,sans-serif'; x.textAlign = 'center'; x.fillText(def.name, 192, 44);
  x.fillStyle = '#9ec5ff'; x.font = '400 26px Roboto,system-ui,sans-serif'; x.fillText('💬 ' + def.role, 192, 80);
  const t = new THREE.CanvasTexture(c); t.colorSpace = THREE.SRGBColorSpace;
  const s = new THREE.Sprite(new THREE.SpriteMaterial({ map: t, transparent: true, depthWrite: false }));
  s.scale.set(0.96, 0.28, 1); return s;
}

class NPC {
  constructor(def, M) {
    this.def = def; this.M = M;
    this.char = def.robot ? createRobot(def) : createCharacter(def);
    this.root = this.char.root;
    this.root.position.set(def.home[0], 0, def.home[1]);
    this.yaw = Math.random() * TAU; this.root.rotation.y = this.yaw;
    this.state = 'idle'; this.timer = 1 + Math.random() * 3; this.target = new THREE.Vector3();
    this.tag = makeTag(def); this.tag.position.y = (def.scale || 1) * 2.12 + (def.robot ? 0.15 : 0); this.root.add(this.tag);
    this.tag.scale.multiplyScalar(1 / (def.scale || 1));
    this.talking = false; this.talkTimer = 0; this.waveT = 0; this.focus = false; this.hover = 0;
  }
  get pos() { return this.root.position; }
  update(dt, player) {
    const p = this.root.position, toP = new THREE.Vector3(player.x - p.x, 0, player.z - p.z), dist = toP.length();
    let walking = false;
    if (this.talking || this.focus) {
      this.faceYaw(Math.atan2(toP.x, toP.z), dt * 6);
    } else if (this.def.wander === false) {
      if (dist < 9) this.faceYaw(Math.atan2(toP.x, toP.z), dt * 3);
    } else {
      this.timer -= dt;
      if (this.state === 'idle') {
        if (dist < 7 && dist > 0.1) this.faceYaw(Math.atan2(toP.x, toP.z), dt * 2);
        if (this.timer <= 0) {
          const a = Math.random() * TAU, r = 1 + Math.random() * 5;
          this.target.set(this.def.home[0] + Math.cos(a) * r, 0, this.def.home[1] + Math.sin(a) * r);
          this.state = 'walk'; this.timer = 9;
        }
      } else {
        const d = new THREE.Vector3().subVectors(this.target, p); d.y = 0; const l = d.length();
        if (l < 0.3 || this.timer <= 0) { this.state = 'idle'; this.timer = 2 + Math.random() * 5; }
        else {
          this.faceYaw(Math.atan2(d.x, d.z), dt * 5); walking = true;
          const s = (this.def.scale || 1) > 0.9 ? 0.95 : 0.8;
          p.x += d.x / l * s * dt; p.z += d.z / l * s * dt; collide(this.M, p, 0.35);
        }
      }
    }
    if (this.waveT > 0) this.waveT -= dt;
    else if (!this.talking && dist < 10 && dist > 2 && Math.random() < dt * 0.04) this.waveT = 2.2;
    this.root.rotation.y = this.yaw;
    this.char.update(dt, walking, this.talking, this.waveT > 0 && !walking);
    this.tag.material.opacity = clamp(1.4 - dist / 14, 0, 1) * (this.hover > 0 ? 1 : 0.85);
    this.tag.visible = this.tag.material.opacity > 0.05;
    this.hover = 0;
  }
  faceYaw(target, k) {
    let d = target - this.yaw; while (d > PI) d -= TAU; while (d < -PI) d += TAU;
    this.yaw += d * Math.min(1, k);
  }
}

/* ---------- Casas ---------- */
const HOUSE_SLOTS = [[0, -19, 0], [-17, -9, PI / 2], [17, -9, -PI / 2], [-17, 11, PI / 2], [17, 11, -PI / 2], [0, 21, PI]];

function makeHouse(B, M, idx, st, rnd) {
  const [hx, hz, ry] = HOUSE_SLOTS[idx];
  const W = 6.4, D = 5.2, H = 2.7, t = 0.22;
  const wall = st.wall[idx % st.wall.length], trim = st.trim, floor = st.floor;
  const cs = Math.cos(ry), sn = Math.sin(ry);
  const toW = (lx, lz) => [hx + lx * cs + lz * sn, hz - lx * sn + lz * cs];
  const addBox = (lx, lz, sx, sz, active) => {
    const [wx, wz] = toW(lx, lz), sw = Math.abs(sn) > 0.5 ? sz : sx, sd = Math.abs(sn) > 0.5 ? sx : sz;
    const b = { x0: wx - sw / 2, x1: wx + sw / 2, z0: wz - sd / 2, z1: wz + sd / 2, active: active !== false };
    M.boxes.push(b); return b;
  };
  B.setBase(hx, 0, hz, ry);
  B.box(0, 0.05, 0, W + 0.6, 0.1, D + 0.6, st.base || 0x9a9a9a);
  B.box(0, 0.12, 0, W - 0.1, 0.06, D - 0.1, floor);
  B.box(0, H / 2, -D / 2 + t / 2, W, H, t, wall); addBox(0, -D / 2 + t / 2, W, t);
  B.box(-W / 2 + t / 2, H / 2, 0, t, H, D, wall); addBox(-W / 2 + t / 2, 0, t, D);
  B.box(W / 2 - t / 2, H / 2, 0, t, H, D, wall); addBox(W / 2 - t / 2, 0, t, D);
  const segW = W / 2 - 0.6, segX = 0.6 + segW / 2;
  B.box(-segX, H / 2, D / 2 - t / 2, segW, H, t, wall); addBox(-segX, D / 2 - t / 2, segW, t);
  B.box(segX, H / 2, D / 2 - t / 2, segW, H, t, wall); addBox(segX, D / 2 - t / 2, segW, t);
  B.box(0, (2.2 + H) / 2, D / 2 - t / 2, 1.2, H - 2.2, t, wall);
  B.box(-0.66, 1.1, D / 2 + 0.02, 0.1, 2.2, 0.3, trim); B.box(0.66, 1.1, D / 2 + 0.02, 0.1, 2.2, 0.3, trim); B.box(0, 2.25, D / 2 + 0.02, 1.42, 0.1, 0.3, trim);
  B.box(0, 0.16, D / 2 + 0.35, 1.6, 0.1, 0.9, st.base || 0x9a9a9a);
  // telhado
  if (st.roofType === 'flat') {
    B.box(0, H + 0.12, 0, W + 0.5, 0.26, D + 0.5, st.roof);
    [[0, D / 2 + 0.2, W + 0.5, 0.18], [0, -D / 2 - 0.2, W + 0.5, 0.18]].forEach(p => B.box(p[0], H + 0.4, p[1], p[2], 0.3, p[3], wall));
    [[W / 2 + 0.2, 0], [-W / 2 - 0.2, 0]].forEach(p => B.box(p[0], H + 0.4, p[1], 0.18, 0.3, D + 0.5, wall));
    for (let i = -2; i <= 2; i++) B.cyl(i * 1.2, H - 0.2, D / 2 + 0.28, 0.1, 0.5, 0x8a5a2b, { rx: PI / 2, seg: 6 });
    B.box(0, H + 0.45, -1, 0.9, 0.5, 0.9, wall); // cúpula/caixa
    B.sph(-1.9, H + 0.4, 1.0, 0.6, st.accent || 0xd1385f, { sy: 0.55 });
    // toldo listrado
    for (let i = 0; i < 5; i++) B.box(-1.2 + i * 0.6, 2.3, D / 2 + 0.7, 0.6, 0.06, 1.2, i % 2 ? 0xffffff : (st.accent || 0xd1385f), { rx: 0.18 });
  } else {
    const rh = 1.9;
    B.box(0, H + 0.08, 0, W + 0.5, 0.16, D + 0.5, trim);
    B.pyr(0, H + 0.16 + rh / 2, 0, W / 2 + 0.55, D / 2 + 0.55, rh, st.roof);
    if (st.snowRoof) B.pyr(0, H + 0.3 + rh * 0.46, 0, W / 2 + 0.5, D / 2 + 0.5, rh * 0.92, 0xf4f8ff, { j: 0.04 });
    B.box(1.7, H + 1.2, -1, 0.55, 1.6, 0.55, 0x8d8f99); B.box(1.7, H + 2.05, -1, 0.7, 0.14, 0.7, 0x6d6f7a);
    if (st.snowRoof) B.box(1.7, H + 2.16, -1, 0.66, 0.1, 0.66, 0xffffff);
    M.smoke.push(toW(1.7, -1));
  }
  // janelas
  const winC = st.window;
  const win = (lx, lz, rot) => {
    B.box(lx, 1.55, lz, 0.98, 0.88, 0.1, trim, { ry: rot });
    B.box(lx, 1.55, lz + (rot ? 0 : 0.0), 0.8, 0.7, 0.12, winC, { ry: rot, unlit: true, j: 0 });
    B.box(lx, 1.55, lz, 0.06, 0.74, 0.14, trim, { ry: rot }); B.box(lx, 1.55, lz, 0.84, 0.06, 0.14, trim, { ry: rot });
  };
  win(-2.0, D / 2 - 0.02, 0); win(2.0, D / 2 - 0.02, 0);
  win(-W / 2 + 0.02, 0.5, PI / 2); win(W / 2 - 0.02, 0.5, PI / 2); win(0, -D / 2 + 0.02, 0);
  // interior
  B.cyl(0.2, 0.17, 0.4, 1.4, 0.03, st.rug || 0xc0463a, { seg: 14, j: 0 });
  B.box(1.7, 0.82, -0.5, 1.3, 0.08, 0.8, 0x8a5a2b); [[-0.55, -0.3], [0.55, -0.3], [-0.55, 0.3], [0.55, 0.3]].forEach(p => B.box(1.7 + p[0], 0.42, -0.5 + p[1], 0.07, 0.8, 0.07, 0x6b4423));
  B.cyl(1.7, 1.0, -0.5, 0.1, 0.22, 0xffe9a8, { unlit: true, seg: 8 }); B.sph(1.7, 1.2, -0.5, 0.14, 0xfff2b8, { unlit: true, d: 0 });
  [[-0.2, -0.5, 0], [3.2, -0.5, PI]].forEach(p => { B.box(p[0] + 1.3 * (p[2] ? 0 : 0), 0.45, p[1], 0.45, 0.06, 0.45, 0xb5793a); B.box(p[0], 0.24, p[1], 0.06, 0.4, 0.06, 0x6b4423); });
  B.box(1.7 - 1.15, 0.45, -0.5, 0.45, 0.06, 0.45, 0xb5793a); B.box(1.7 - 1.15, 0.25, -0.5, 0.06, 0.4, 0.06, 0x6b4423);
  B.box(-2.1, 0.35, -1.4, 1.1, 0.3, 2.0, 0x8a5a2b); B.box(-2.1, 0.58, -1.3, 1.0, 0.18, 1.8, st.blanket || 0x4a7dd0); B.box(-2.1, 0.7, -2.2, 0.8, 0.14, 0.3, 0xffffff);
  B.box(-2.8, 1.2, 1.2, 0.3, 1.6, 0.9, 0x8a5a2b); [0.5, 1.0, 1.5].forEach((y, i) => B.box(-2.76, y, 1.2, 0.2, 0.08, 0.8, [0xc0463a, 0x3fa34d, 0x4a7dd0][i], { j: 0.2 }));
  B.cyl(2.6, 0.3, 1.8, 0.22, 0.4, 0xb5793a, { seg: 8 }); B.sph(2.6, 0.7, 1.8, 0.3, 0x3fa34d, { sy: 0.9 });
  // porta (mesh à parte, anima)
  const [dwx, dwz] = toW(-0.6, D / 2 - t / 2);
  const piv = new THREE.Group(); piv.position.set(dwx, 0, dwz); piv.rotation.y = ry; M.group.add(piv);
  const leaf = part(CG.box, lam(st.door), 0.6, 1.05, 0, 1.2, 2.1, 0.08, piv);
  part(CG.box, lam(st.trim), 0.6, 1.5, 0.045, 0.8, 0.5, 0.02, piv); part(CG.box, lam(st.trim), 0.6, 0.55, 0.045, 0.8, 0.5, 0.02, piv);
  part(CG.sph, lam(0xffd04a, true), 1.0, 1.0, 0.08, 0.05, 0.05, 0.05, piv);
  const dc = addBox(0, D / 2 - t / 2, 1.2, 0.3, true);
  const [cwx, cwz] = toW(0, D / 2 + 0.3);
  M.doors.push({ pivot: piv, leaf, open: false, a: 0, collider: dc, pos: new THREE.Vector3(cwx, 1.1, cwz), mesh: leaf, idx });
  M.houses.push({ x: hx, z: hz, ry, toW });
  // estrela escondida dentro de algumas casas
  if (idx === 1 || idx === 4) { const [sx, sz] = toW(-2.1, -0.1); M.starSpots.push([sx, 1.0, sz]); }
  B.clearBase();
  // caminho da praça até a porta
  const [px, pz] = toW(0, D / 2 + 0.8), len = Math.hypot(px, pz) - 8.5, ang = Math.atan2(px, pz);
  if (len > 0) B.box(px - Math.sin(ang) * len / 2, 0.025, pz - Math.cos(ang) * len / 2, 1.9, 0.05, len, st.path || 0xd9cfc0, { ry: ang, j: 0.08 });
}

/* ---------- Árvores e props ---------- */
function tree(B, M, kind, x, z, s, rnd) {
  const R = rnd;
  if (kind === 'oak') {
    B.tcyl(x, 1.0 * s, z, 0.14 * s, 0.24 * s, 2.0 * s, 0x7a5230, { seg: 6 });
    const cols = [0x3f9a3a, 0x4cae42, 0x2f8532];
    B.sph(x, 2.9 * s, z, 1.25 * s, cols[(R() * 3) | 0], { sy: 0.9 });
    B.sph(x + 0.7 * s, 2.5 * s, z + 0.2 * s, 0.9 * s, cols[(R() * 3) | 0]); B.sph(x - 0.6 * s, 2.6 * s, z - 0.3 * s, 0.95 * s, cols[(R() * 3) | 0]);
    B.sph(x, 3.6 * s, z, 0.8 * s, cols[(R() * 3) | 0]);
    M.circles.push({ x, z, r: 0.35 * s });
  } else if (kind === 'pine') {
    B.tcyl(x, 0.5 * s, z, 0.12 * s, 0.2 * s, 1.0 * s, 0x6b4a2b, { seg: 6 });
    for (let i = 0; i < 4; i++) {
      const r = (1.5 - i * 0.28) * s, y = (1.2 + i * 0.85) * s;
      B.cone(x, y + 0.6 * s, z, r, 1.5 * s, i % 2 ? 0x1f6b43 : 0x2a7d4f, { seg: 7 });
      B.cone(x, y + 0.85 * s, z, r * 0.82, 0.8 * s, 0xf4f8ff, { seg: 7, j: 0.03 });
    }
    M.circles.push({ x, z, r: 0.35 * s });
  } else if (kind === 'palm') {
    const lean = (R() - 0.5) * 0.5, a = R() * TAU;
    for (let i = 0; i < 4; i++) {
      const yy = (0.7 + i * 1.05) * s;
      B.tcyl(x + Math.sin(a) * lean * i * 0.4 * s, yy, z + Math.cos(a) * lean * i * 0.4 * s, (0.13 - i * 0.01) * s, (0.17 - i * 0.01) * s, 1.1 * s, 0x9a7048, { seg: 6 });
    }
    const tx = x + Math.sin(a) * lean * 1.6 * s, tz = z + Math.cos(a) * lean * 1.6 * s;
    for (let i = 0; i < 7; i++) {
      const aa = i / 7 * TAU;
      B.cone(tx + Math.cos(aa) * 0.8 * s, 4.5 * s, tz + Math.sin(aa) * 0.8 * s, 0.22 * s, 2.1 * s, 0x3b9a4a, { seg: 4, rz: Math.cos(aa) * 1.25, rx: -Math.sin(aa) * 1.25 });
    }
    B.sph(tx, 4.35 * s, tz, 0.2 * s, 0x6b4a2b, { d: 0 }); B.sph(tx + 0.18 * s, 4.25 * s, tz, 0.17 * s, 0x6b4a2b, { d: 0 });
    M.circles.push({ x, z, r: 0.3 * s });
  } else if (kind === 'cactus') {
    B.cyl(x, 1.1 * s, z, 0.3 * s, 2.2 * s, 0x3f9a58, { seg: 7 }); B.sph(x, 2.2 * s, z, 0.3 * s, 0x3f9a58, { d: 0 });
    B.cyl(x + 0.5 * s, 1.4 * s, z, 0.14 * s, 0.9 * s, 0x3f9a58, { seg: 6 }); B.box(x + 0.35 * s, 1.0 * s, z, 0.4 * s, 0.2 * s, 0.2 * s, 0x3f9a58);
    B.cyl(x - 0.5 * s, 1.7 * s, z, 0.14 * s, 0.8 * s, 0x3f9a58, { seg: 6 }); B.box(x - 0.35 * s, 1.3 * s, z, 0.4 * s, 0.2 * s, 0.2 * s, 0x3f9a58);
    B.sph(x, 2.45 * s, z, 0.12 * s, 0xff6fa0, { d: 0 });
    M.circles.push({ x, z, r: 0.45 * s });
  }
}
function lamp(B, M, x, z, warm) {
  B.cyl(x, 1.5, z, 0.07, 3.0, 0x2b2f3a, { seg: 6 }); B.sph(x, 3.1, z, 0.28, warm || 0xffe29a, { unlit: true }); B.box(x, 3.34, z, 0.5, 0.06, 0.5, 0x2b2f3a);
  M.circles.push({ x, z, r: 0.15 });
}
function bench(B, x, z, ry) {
  B.box(x, 0.5, z, 1.7, 0.1, 0.5, 0x9a6a3a, { ry }); B.box(x, 0.85, z, 1.7, 0.4, 0.08, 0x9a6a3a, { ry });
  const c = Math.cos(ry), s = Math.sin(ry);
  [-0.7, 0.7].forEach(o => B.box(x + o * c, 0.25, z - o * s, 0.1, 0.5, 0.4, 0x4a3a2a, { ry }));
}
function fire(M, B, x, z, rnd) {
  B.cyl(x, 0.12, z, 1.0, 0.24, 0x6b6b72, { seg: 10 }); B.cyl(x, 0.25, z, 0.75, 0.1, 0x2a2018, { seg: 10 });
  for (let i = 0; i < 6; i++) B.cyl(x + Math.cos(i) * 0.4, 0.4, z + Math.sin(i) * 0.4, 0.07, 0.8, 0x5a3a20, { rz: Math.cos(i) * 0.6, rx: Math.sin(i) * 0.6, seg: 5 });
  const fl = new THREE.Group(); fl.position.set(x, 0.3, z); M.group.add(fl);
  const flames = [[0, 0, 0.5, 1.4, 0xff7a1a], [0.2, 0.1, 0.35, 1.0, 0xffb02e], [-0.2, -0.1, 0.35, 0.9, 0xff4f1a], [0, 0, 0.25, 0.9, 0xfff0a0]].map(f => {
    const m = new THREE.Mesh(GEO.coneG(6), lam(f[4], true)); m.position.set(f[0], f[3] / 2, f[1]); m.scale.set(f[2], f[3], f[2]); fl.add(m); return { m, h: f[3], b: f[2] };
  });
  M.updaters.push((dt, t) => flames.forEach((f, i) => { const k = 1 + Math.sin(t * (9 + i * 3) + i) * 0.2; f.m.scale.set(f.b * (2 - k), f.h * k, f.b * (2 - k)); f.m.position.y = f.h * k / 2; }));
  M.circles.push({ x, z, r: 1.0 });
  M.sparks.push([x, 0.8, z]);
}

/* ---------- Mapas ---------- */
const MAPS = {
  village: { name: 'Vila Verde', emoji: '🏡', sky: 'village', seed: 11, surface: 'grass', spawn: [0, 7.5, 0], music: 'village', ground: [0x6cc24a, 0x5fb441], fog: [0xcfe9f7, 140], hemi: [0xd5ecff, 0x6a8f4a, 2.4], sun: [0xfff1d0, 2.6, [30, 50, 20]], tint: 0x8fb4d9,
    house: { wall: [0xf6d9a8, 0xf3b6b6, 0xb7d8f0, 0xd2e8b0, 0xf7e1c0, 0xe8c6e8], roof: 0xc4503a, trim: 0xfaf3e6, floor: 0xb98a5a, door: 0x8a5a2b, window: 0x9fdcff, rug: 0xc0463a, blanket: 0x4a7dd0, path: 0xd9cfc0, base: 0xa0a0a0 } },
  desert: { name: 'Oásis do Pôr do Sol', emoji: '🏜️', sky: 'desert', seed: 23, surface: 'sand', spawn: [8.5, 8.5, PI / 4], music: 'desert', ground: [0xe3b96e, 0xd9ac5f], fog: [0xf0b98a, 130], hemi: [0xffd7a8, 0xb07a4a, 2.2], sun: [0xffa860, 2.4, [-40, 14, -30]], tint: 0xc9703a,
    house: { wall: [0xe8c79a, 0xdfae7c, 0xf0d4a8, 0xd9a06b, 0xe6bf8a, 0xf2dcb4], roof: 0xcfa070, roofType: 'flat', trim: 0x7a4a2a, floor: 0xcaa073, door: 0x2a7da0, window: 0xffcf6a, rug: 0x7b2d8a, blanket: 0xd1385f, path: 0xe9cf9a, base: 0xc9a56e, accent: 0xd1385f } },
  snow: { name: 'Vale Nevado', emoji: '❄️', sky: 'snow', seed: 37, surface: 'snow', spawn: [0, 7.5, 0], music: 'snow', ground: [0xf3f8ff, 0xe3edf8], fog: [0xb8d0e6, 110], hemi: [0xbfd8ff, 0x8aa0c0, 2.3], sun: [0xcfe0ff, 1.8, [-25, 40, 30]], tint: 0xcfe0ee,
    house: { wall: [0x9a6a3e, 0xa8764a, 0x8a5a33, 0xb0804f, 0x946238, 0xa06f43], roof: 0x5a3a28, snowRoof: true, trim: 0x4a2f20, floor: 0x7a5636, door: 0x4a8a6b, window: 0xffd070, rug: 0x2a7d4f, blanket: 0xb83a4b, path: 0xdce8f4, base: 0xaab4c0 } }
};

function buildMap(id) {
  const cfg = MAPS[id], rnd = mulberry(cfg.seed), B = new Baker(rnd), group = new THREE.Group();
  const M = { id, cfg, group, boxes: [], circles: [], doors: [], houses: [], starSpots: [], updaters: [], smoke: [], sparks: [], radius: 62, surface: cfg.surface, particles: [] };
  // chão
  const gg = new THREE.PlaneGeometry(320, 320, 64, 64); gg.rotateX(-PI / 2);
  const cols = []; const c1 = new THREE.Color(cfg.ground[0]), c2 = new THREE.Color(cfg.ground[1]), tmp = new THREE.Color();
  const pa = gg.attributes.position;
  for (let i = 0; i < pa.count; i++) { tmp.copy(c1).lerp(c2, rnd()); const k = 0.94 + rnd() * 0.1; cols.push(tmp.r * k, tmp.g * k, tmp.b * k); }
  gg.setAttribute('color', new THREE.Float32BufferAttribute(cols, 3));
  const ground = new THREE.Mesh(gg, MAT.lit); group.add(ground);
  // praça
  const plaza = { village: 0xd9cfc0, desert: 0xe0c08a, snow: 0xe8f1fa }[id];
  B.cyl(0, 0.03, 0, 9, 0.06, plaza, { seg: 28, j: 0.04 }); B.cyl(0, 0.05, 0, 7.2, 0.04, plaza, { seg: 28, j: 0.1 });
  for (let i = 0; i < HOUSE_SLOTS.length; i++) makeHouse(B, M, i, cfg.house, rnd);
  const free = (x, z, r) => {
    if (Math.hypot(x, z) < 11) return false;
    for (const h of M.houses) if (Math.hypot(x - h.x, z - h.z) < 6 + r) return false;
    for (const c of M.circles) if (Math.hypot(x - c.x, z - c.z) < c.r + r) return false;
    return true;
  };
  const scatter = (n, rmin, rmax, r, fn) => { let t = 0; while (n > 0 && t++ < 1500) { const a = rnd() * TAU, d = rmin + rnd() * (rmax - rmin), x = Math.cos(a) * d, z = Math.sin(a) * d; if (free(x, z, r)) { fn(x, z); n--; } } };

  if (id === 'village') {
    B.cyl(0, 0.3, 0, 2.5, 0.6, 0x9aa3ab, { seg: 16 }); B.cyl(0, 0.6, 0, 2.1, 0.06, 0x4aa8e0, { seg: 16, unlit: true, j: 0 });
    B.cyl(0, 1.2, 0, 0.35, 1.2, 0x8a939b, { seg: 8 }); B.cyl(0, 1.9, 0, 0.95, 0.2, 0x9aa3ab, { seg: 12 }); B.cyl(0, 2.02, 0, 0.8, 0.05, 0x4aa8e0, { seg: 12, unlit: true, j: 0 }); B.sph(0, 2.4, 0, 0.3, 0xf4d35e, { unlit: true });
    M.circles.push({ x: 0, z: 0, r: 2.6 });
    for (let i = 0; i < 6; i++) { const a = i / 6 * TAU + 0.5; lamp(B, M, Math.cos(a) * 8.2, Math.sin(a) * 8.2); }
    for (let i = 0; i < 4; i++) { const a = i / 4 * TAU + 0.78; bench(B, Math.cos(a) * 6.4, Math.sin(a) * 6.4, a + PI / 2); }
    scatter(64, 24, 60, 1.5, (x, z) => tree(B, M, 'oak', x, z, 0.8 + rnd() * 0.6, rnd));
    scatter(24, 10, 40, 1, (x, z) => tree(B, M, 'oak', x, z, 0.6 + rnd() * 0.3, rnd));
    scatter(60, 10, 55, 0.5, (x, z) => { const col = [0xff6b6b, 0xffd93d, 0xffffff, 0xc77dff, 0xff9ecd][(rnd() * 5) | 0]; for (let k = 0; k < 4; k++) { const fx = x + (rnd() - 0.5) * 1.2, fz = z + (rnd() - 0.5) * 1.2; B.cone(fx, 0.14, fz, 0.03, 0.28, 0x3a9a3a, { seg: 4 }); B.sph(fx, 0.3, fz, 0.11, col, { d: 0, j: 0.2 }); } });
    scatter(14, 14, 58, 1, (x, z) => { B.sph(x, 0.25, z, 0.5 + rnd() * 0.5, 0x9a9aa0, { sy: 0.6, d: 0 }); });
    // moinho
    const mx = -34, mz = -28;
    B.tcyl(mx, 3.0, mz, 1.6, 2.6, 6, 0xf0e4d0, { seg: 8 }); B.cone(mx, 6.9, mz, 2.4, 2.2, 0xc4503a, { seg: 8 }); B.box(mx, 0.9, mz + 2.4, 1.0, 1.8, 0.2, 0x8a5a2b);
    M.circles.push({ x: mx, z: mz, r: 2.7 });
    const bl = new THREE.Group(); bl.position.set(mx, 5.4, mz + 1.9); M.group.add(bl);
    for (let i = 0; i < 4; i++) { const arm = new THREE.Group(); arm.rotation.z = i * PI / 2; bl.add(arm); part(CG.box, lam(0x8a5a2b), 0, 2.2, 0, 0.12, 4.4, 0.08, arm); part(CG.box, lam(0xfaf3e6), 0.35, 2.7, 0.02, 0.6, 2.0, 0.04, arm); }
    part(CG.sph, lam(0x4a3a2a), 0, 0, 0, 0.3, 0.3, 0.3, bl);
    M.updaters.push((dt) => { bl.rotation.z += dt * 0.5; });
    // partículas: fonte + vaga-lumes
    const sp = makePoints(110, 0.16, 0xbfe9ff, true), sd = [];
    for (let i = 0; i < 110; i++) sd.push({ a: rnd() * TAU, v: 2.2 + rnd() * 1.2, t: rnd() * 2, s: 0.8 + rnd() * 0.8 });
    M.group.add(sp);
    M.updaters.push((dt) => { const p = sp.geometry.attributes.position; sd.forEach((q, i) => { q.t += dt; if (q.t > 1.7) { q.t = 0; q.a = rnd() * TAU; } const tt = q.t, r = q.s * tt; p.setXYZ(i, Math.cos(q.a) * r, 2.2 + q.v * tt - 4.2 * tt * tt, Math.sin(q.a) * r); }); p.needsUpdate = true; });
    const ff = makePoints(70, 0.3, 0xe8ff7a, true), fd = []; for (let i = 0; i < 70; i++) fd.push({ x: (rnd() - 0.5) * 70, z: (rnd() - 0.5) * 70, y: 0.5 + rnd() * 2, p: rnd() * 6, s: 0.3 + rnd() * 0.5 });
    M.group.add(ff);
    M.updaters.push((dt, t) => { const p = ff.geometry.attributes.position; fd.forEach((q, i) => p.setXYZ(i, q.x + Math.sin(t * q.s + q.p) * 1.5, q.y + Math.sin(t * q.s * 1.7 + q.p) * 0.5, q.z + Math.cos(t * q.s + q.p) * 1.5)); p.needsUpdate = true; ff.material.opacity = 0.5 + Math.sin(t * 2) * 0.2; });
  } else if (id === 'desert') {
    B.cyl(0, 0.14, 0, 3.4, 0.28, 0xb9a07a, { seg: 18 }); B.cyl(0, 0.3, 0, 3.0, 0.04, 0x37b6c9, { seg: 18, unlit: true, j: 0 });
    M.circles.push({ x: 0, z: 0, r: 3.4 });
    for (let i = 0; i < 5; i++) { const a = i / 5 * TAU + 0.3; tree(B, M, 'palm', Math.cos(a) * 4.6, Math.sin(a) * 4.6, 1.0 + rnd() * 0.3, rnd); }
    fire(M, B, 6.5, -2.2, rnd); lamp(B, M, -8, 2, 0xffc86a); lamp(B, M, 8, 7, 0xffc86a); lamp(B, M, -3, -8, 0xffc86a);
    // tenda
    B.pyr(-6.5, 1.5, -2.6, 2.2, 2.2, 2.4, 0xd1385f); B.box(-6.5, 0.9, -2.6 + 2.18, 1.0, 1.7, 0.1, 0x3a2a20); M.circles.push({ x: -6.5, z: -2.6, r: 2.0 });
    // barracas do mercado
    [[-3.5, 7.5, 0], [3.5, 7.8, 0], [0, -7.2, PI]].forEach((s, i) => {
      B.setBase(s[0], 0, s[1], s[2]);
      B.box(0, 0.5, 0, 2.2, 1.0, 0.8, 0x8a5a2b); [-1, 1].forEach(sx => B.box(sx * 1.05, 1.2, -0.3, 0.1, 2.4, 0.1, 0x6b4423));
      for (let k = 0; k < 6; k++) B.box(-1.1 + k * 0.44, 2.35, 0.1, 0.44, 0.06, 1.3, k % 2 ? 0xffffff : [0xd1385f, 0x2a7da0, 0xf2c14e][i], { rx: 0.15 });
      B.sph(-0.5, 1.15, 0, 0.18, 0xd9534f, { d: 0 }); B.sph(0, 1.15, 0, 0.18, 0xf2c14e, { d: 0 }); B.sph(0.5, 1.15, 0, 0.18, 0x6dbf4b, { d: 0 });
      B.clearBase(); M.boxes.push({ x0: s[0] - 1.2, x1: s[0] + 1.2, z0: s[1] - 0.5, z1: s[1] + 0.5 });
    });
    scatter(40, 24, 60, 1, (x, z) => tree(B, M, 'cactus', x, z, 0.7 + rnd() * 0.8, rnd));
    scatter(12, 25, 60, 1.5, (x, z) => tree(B, M, 'palm', x, z, 0.8 + rnd() * 0.4, rnd));
    scatter(26, 10, 58, 1, (x, z) => { B.sph(x, 0.3, z, 0.45 + rnd() * 0.5, 0xb89868, { sy: 0.6, d: 0 }); });
    // pirâmides ao longe
    [[-95, -70, 38], [-70, -110, 56], [110, -50, 44]].forEach(p => B.pyr(p[0], p[2] / 2, p[1], p[2] * 1.0, p[2] * 1.0, p[2], 0xd9ab6a, { j: 0.06 }));
    const dust = makePoints(160, 0.18, 0xf3d9a6, false), dd = []; for (let i = 0; i < 160; i++) dd.push({ x: (rnd() - 0.5) * 60, y: rnd() * 3, z: (rnd() - 0.5) * 60 });
    M.group.add(dust);
    M.updaters.push((dt, t, pl) => { const p = dust.geometry.attributes.position; dd.forEach((q, i) => { q.x += dt * 2.2; if (q.x > 30) q.x -= 60; p.setXYZ(i, pl.x + q.x, q.y + Math.sin(t + i) * 0.3, pl.z + q.z); }); p.needsUpdate = true; });
  } else {
    fire(M, B, 0, 0, rnd);
    for (let i = 0; i < 5; i++) { const a = i / 5 * TAU; B.cyl(Math.cos(a) * 2.2, 0.2, Math.sin(a) * 2.2, 0.35, 0.4, 0x7a5636, { seg: 8, rx: PI / 2, ry: a }); }
    for (let i = 0; i < 6; i++) { const a = i / 6 * TAU + 0.5; lamp(B, M, Math.cos(a) * 8, Math.sin(a) * 8, 0xffd48a); }
    // boneco de neve
    B.sph(-6.5, 0.6, 3.2, 0.7, 0xffffff, { j: 0.03 }); B.sph(-6.5, 1.5, 3.2, 0.5, 0xffffff, { j: 0.03 }); B.sph(-6.5, 2.2, 3.2, 0.38, 0xffffff, { j: 0.03 });
    B.cone(-6.5, 2.2, 3.62, 0.07, 0.4, 0xff7a1a, { seg: 5, rx: PI / 2 }); B.sph(-6.58, 2.3, 3.55, 0.04, 0x111111, { d: 0 }); B.sph(-6.42, 2.3, 3.55, 0.04, 0x111111, { d: 0 });
    B.cyl(-6.5, 2.62, 3.2, 0.3, 0.05, 0x222222, { seg: 10 }); B.cyl(-6.5, 2.8, 3.2, 0.2, 0.35, 0x222222, { seg: 10 }); M.circles.push({ x: -6.5, z: 3.2, r: 0.9 });
    // iglu
    B.sph(-27, 0, 20, 3.4, 0xeaf3fb, { sy: 0.85, j: 0.03 }); B.box(-27, 0.9, 23.3, 1.4, 1.8, 1.2, 0xdce8f4); B.box(-27, 0.8, 23.9, 0.9, 1.5, 0.2, 0x1a2540);
    M.circles.push({ x: -27, z: 20, r: 3.4 });
    // lago congelado
    B.cyl(28, 0.04, -8, 7, 0.08, 0x9ed3ee, { seg: 20, unlit: false, j: 0.03 });
    // trenó
    B.box(5.5, 0.35, -7.5, 1.0, 0.1, 2.0, 0xb5342b); B.box(5.0, 0.15, -7.5, 0.06, 0.12, 2.3, 0x555555); B.box(6.0, 0.15, -7.5, 0.06, 0.12, 2.3, 0x555555); M.circles.push({ x: 5.5, z: -7.5, r: 1.0 });
    scatter(95, 22, 60, 1.3, (x, z) => tree(B, M, 'pine', x, z, 0.8 + rnd() * 0.8, rnd));
    scatter(20, 10, 40, 1.2, (x, z) => tree(B, M, 'pine', x, z, 0.6 + rnd() * 0.4, rnd));
    scatter(30, 10, 58, 1, (x, z) => { B.sph(x, 0.1, z, 0.9 + rnd() * 0.9, 0xffffff, { sy: 0.35, j: 0.05 }); });
    const sn = makePoints(520, 0.12, 0xffffff, false), sd = []; for (let i = 0; i < 520; i++) sd.push({ x: (rnd() - 0.5) * 50, y: rnd() * 14, z: (rnd() - 0.5) * 50, v: 0.8 + rnd() * 1.1, p: rnd() * 6 });
    M.group.add(sn);
    M.updaters.push((dt, t, pl) => { const p = sn.geometry.attributes.position; sd.forEach((q, i) => { q.y -= q.v * dt; if (q.y < 0) q.y += 14; p.setXYZ(i, pl.x + q.x + Math.sin(t * 0.7 + q.p) * 0.6, q.y, pl.z + q.z); }); p.needsUpdate = true; });
  }

  // colinas ao redor (profundidade 360°)
  const hills = { village: [0x7fc56a, 0x5aa85a], desert: [0xe0a86a, 0xc98c52], snow: [0xf1f6fc, 0xdbe6f2] }[id];
  for (let i = 0; i < 26; i++) { const a = i / 26 * TAU + rnd() * 0.2, d = 78 + rnd() * 40, s = 14 + rnd() * 24; B.sph(Math.cos(a) * d, -s * 0.15, Math.sin(a) * d, s, hills[i % 2], { sy: 0.55 + rnd() * 0.4, d: 1 }); }
  if (id === 'snow') for (let i = 0; i < 14; i++) { const a = rnd() * TAU, d = 130 + rnd() * 40, s = 22 + rnd() * 20; B.cone(Math.cos(a) * d, s * 0.7, Math.sin(a) * d, s * 0.9, s * 1.5, 0xd8e4f2, { seg: 6 }); }
  group.add(B.build());

  // nuvens baked (rotacionam devagar)
  const CB = new Baker(rnd); const cc = { village: 0xffffff, desert: 0xffc2a8, snow: 0x9fb4d0 }[id];
  if (id !== 'snow') for (let i = 0; i < 16; i++) { const a = rnd() * TAU, d = 120 + rnd() * 90, y = 55 + rnd() * 45; for (let k = 0; k < 5; k++) CB.sph(Math.cos(a) * d + (k - 2) * 9 + rnd() * 4, y + rnd() * 3, Math.sin(a) * d + rnd() * 6, 8 + rnd() * 6, cc, { unlit: true, sy: 0.5, j: 0.06 }); }
  M.clouds = CB.build(); group.add(M.clouds);
  M.updaters.push(dt => { M.clouds.rotation.y += dt * 0.004; });

  // fumaça das chaminés
  if (M.smoke.length) {
    const sm = makePoints(M.smoke.length * 10, 0.9, id === 'snow' ? 0xe8eef6 : 0xdddddd, false); sm.material.opacity = 0.55; M.group.add(sm);
    const sdat = []; M.smoke.forEach(s => { for (let i = 0; i < 10; i++) sdat.push({ x: s[0], z: s[1], t: i * 0.35, o: rnd() * 6 }); });
    M.updaters.push((dt, t) => { const p = sm.geometry.attributes.position; sdat.forEach((q, i) => { q.t += dt; if (q.t > 3.5) q.t = 0; p.setXYZ(i, q.x + q.t * 0.3 + Math.sin(q.t * 2 + q.o) * 0.15, 3.9 + q.t * 0.9, q.z); }); p.needsUpdate = true; });
  }
  if (M.sparks.length) {
    const sp = makePoints(40, 0.14, 0xffb347, true); M.group.add(sp); const sd = []; for (let i = 0; i < 40; i++) sd.push({ t: rnd() * 2, a: rnd() * TAU, r: rnd() * 0.5 });
    const o = M.sparks[0];
    M.updaters.push(dt => { const p = sp.geometry.attributes.position; sd.forEach((q, i) => { q.t += dt; if (q.t > 2) { q.t = 0; q.a = rnd() * TAU; q.r = rnd() * 0.5; } p.setXYZ(i, o[0] + Math.cos(q.a) * q.r * (1 + q.t), o[1] + q.t * 1.6, o[2] + Math.sin(q.a) * q.r * (1 + q.t)); }); p.needsUpdate = true; });
  }

  // estrelas coletáveis
  const spots = M.starSpots.slice(); let tries = 0;
  while (spots.length < 8 && tries++ < 500) {
    const a = rnd() * TAU, d = 9 + rnd() * 44, x = Math.cos(a) * d, z = Math.sin(a) * d;
    let ok = true; for (const h of M.houses) if (Math.hypot(x - h.x, z - h.z) < 5.5) ok = false;
    for (const c of M.circles) if (Math.hypot(x - c.x, z - c.z) < c.r + 1) ok = false;
    for (const s of spots) if (Math.hypot(x - s[0], z - s[2]) < 9) ok = false;
    if (ok) spots.push([x, 1.0, z]);
  }
  M.stars = spots.map(s => makeStar(s, M));
  return M;
}

let _starGeo = null;
function makeStar(p, M) {
  if (!_starGeo) {
    const sh = new THREE.Shape(); for (let i = 0; i < 10; i++) { const r = i % 2 ? 0.17 : 0.4, a = i / 10 * TAU + PI / 2; i ? sh.lineTo(Math.cos(a) * r, Math.sin(a) * r) : sh.moveTo(Math.cos(a) * r, Math.sin(a) * r); }
    _starGeo = new THREE.ExtrudeGeometry(sh, { depth: 0.1, bevelEnabled: true, bevelSize: 0.03, bevelThickness: 0.03, bevelSegments: 1 }); _starGeo.translate(0, 0, -0.05);
  }
  const g = new THREE.Group(); g.position.set(p[0], p[1], p[2]);
  g.add(new THREE.Mesh(_starGeo, new THREE.MeshBasicMaterial({ color: 0xffd23f })));
  const halo = new THREE.Sprite(new THREE.SpriteMaterial({ map: dotTex(), color: 0xffe27a, transparent: true, blending: THREE.AdditiveBlending, depthWrite: false })); halo.scale.set(1.6, 1.6, 1); g.add(halo);
  const beam = new THREE.Mesh(new THREE.CylinderGeometry(0.05, 0.05, 6, 6, 1, true), new THREE.MeshBasicMaterial({ color: 0xffe27a, transparent: true, opacity: 0.25, depthWrite: false, blending: THREE.AdditiveBlending })); beam.position.y = 3; g.add(beam);
  M.group.add(g);
  return { g, taken: false, base: p[1], pos: g.position, r: 0.7, t: Math.random() * 6 };
}

/* ---------- Saguão (lobby) ---------- */
function buildLobby() {
  const rnd = mulberry(5), B = new Baker(rnd), group = new THREE.Group();
  const M = { id: 'lobby', group, boxes: [], circles: [], doors: [], houses: [], stars: [], updaters: [], radius: 6.2, surface: 'stone', cfg: { fog: [0x120a30, 120], hemi: [0xb59cff, 0x2a1a5a, 1.7], sun: [0xbda3ff, 1.4, [20, 30, 10]] } };
  B.cyl(0, -0.3, 0, 7, 0.6, 0x241a4a, { seg: 40, j: 0.03 }); B.cyl(0, 0.01, 0, 6.5, 0.04, 0x3a2a7a, { seg: 40, j: 0 });
  B.cyl(0, 0.04, 0, 3.0, 0.02, 0x241a4a, { seg: 40, j: 0 });
  const cols = [0x4285f4, 0xea4335, 0xfbbc05, 0x34a853, 0xc77dff, 0x2ee6d6];
  for (let i = 0; i < 70; i++) { const a = rnd() * TAU, d = 9 + rnd() * 26, y = 0.5 + rnd() * 14, s = 0.25 + rnd() * 0.9; (rnd() < 0.5 ? B.oct : B.sph).call(B, Math.cos(a) * d, y, Math.sin(a) * d, s, cols[(rnd() * 6) | 0], { unlit: true, d: 0, j: 0.1 }); }
  const floating = B.build(); group.add(floating);
  M.updaters.push(dt => { floating.rotation.y += dt * 0.03; });
  // anel de luz
  const ring = new THREE.Mesh(new THREE.TorusGeometry(6.6, 0.07, 6, 80), new THREE.MeshBasicMaterial({ color: 0x4dd0ff })); ring.rotation.x = PI / 2; ring.position.y = 0.06; group.add(ring);
  const ring2 = new THREE.Mesh(new THREE.TorusGeometry(3.0, 0.04, 6, 60), new THREE.MeshBasicMaterial({ color: 0xff5ca8 })); ring2.rotation.x = PI / 2; ring2.position.y = 0.07; group.add(ring2);
  M.updaters.push((dt, t) => { ring.material.color.setHSL((t * 0.05) % 1, 0.9, 0.6); ring2.material.color.setHSL((t * 0.05 + 0.5) % 1, 0.9, 0.65); });
  // grade de luz no infinito
  const gc = document.createElement('canvas'); gc.width = gc.height = 256; const gx = gc.getContext('2d'); gx.fillStyle = '#0a0620'; gx.fillRect(0, 0, 256, 256); gx.strokeStyle = '#6a3dff'; gx.lineWidth = 3; gx.strokeRect(0, 0, 256, 256);
  const gt = new THREE.CanvasTexture(gc); gt.wrapS = gt.wrapT = THREE.RepeatWrapping; gt.repeat.set(60, 60); gt.colorSpace = THREE.SRGBColorSpace;
  const floor = new THREE.Mesh(new THREE.CircleGeometry(150, 48), new THREE.MeshBasicMaterial({ map: gt })); floor.rotation.x = -PI / 2; floor.position.y = -0.62; group.add(floor);
  // faíscas
  const sp = makePoints(220, 0.12, 0xb9a6ff, true), sd = []; for (let i = 0; i < 220; i++) sd.push({ a: rnd() * TAU, d: 2 + rnd() * 18, y: rnd() * 10, v: 0.2 + rnd() * 0.4 }); group.add(sp);
  M.updaters.push(dt => { const p = sp.geometry.attributes.position; sd.forEach((q, i) => { q.y += q.v * dt; if (q.y > 10) q.y = 0; p.setXYZ(i, Math.cos(q.a) * q.d, q.y, Math.sin(q.a) * q.d); }); p.needsUpdate = true; });
  return M;
}
