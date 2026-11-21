'use strict';
/* Mundo aberto: geometria estática fundida por material (poucos draw calls), UV projetada no mundo, colisores AABB */
const PI = Math.PI, TAU = PI * 2;
const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
const World = (() => {
  const MATS = {
    grass: { tex: 'grass', scale: 5 }, asphalt: { tex: 'asphalt', scale: 7 }, concrete: { tex: 'concrete', scale: 3 }, brick: { tex: 'brick', scale: 3 },
    facade: { tex: 'facade', scale: 12 }, tower: { tex: 'tower', scale: 16 }, brickwin: { tex: 'brickwin', scale: 12 }, glass: { tex: 'glass', scale: 4 }, metal: { tex: 'metal', scale: 3 }, container: { tex: 'container', scale: 2.4 },
    wood: { tex: 'wood', scale: 1.5 }, crate: { tex: 'crate', own: true }, target: { tex: 'target', own: true }, dirt: { tex: 'dirt', scale: 6 },
    bark: { tex: 'bark', scale: 1.5 }, leaves: { tex: 'leaves', scale: 2.5 }, rock: { tex: 'rock', scale: 5 }, paint: { tex: null }, light: { tex: null, unlit: true }
  };
  const buckets = {}, colliders = [], plates = [], slabs = [];
  const G = { box: new THREE.BoxGeometry(1, 1, 1), cyl: {}, ico: new THREE.IcosahedronGeometry(1, 1), ico0: new THREE.IcosahedronGeometry(1, 0), cone: {}, sph: new THREE.SphereGeometry(1, 12, 8) };
  const cylG = (s, t) => { const k = s + '_' + (t || 1); return G.cyl[k] || (G.cyl[k] = new THREE.CylinderGeometry(t || 1, 1, 1, s, 1)); };
  const m4 = new THREE.Matrix4(), q = new THREE.Quaternion(), e = new THREE.Euler(), vs = new THREE.Vector3(), vp = new THREE.Vector3(), col = new THREE.Color();
  function add(mat, geo, x, y, z, sx, sy, sz, o) {
    o = o || {}; const B = buckets[mat] || (buckets[mat] = { p: [], n: [], u: [], c: [] }), M = MATS[mat];
    e.set(o.rx || 0, o.ry || 0, o.rz || 0, 'YXZ'); q.setFromEuler(e); m4.compose(vp.set(x, y, z), q, vs.set(sx, sy, sz));
    const g = geo.index ? geo.toNonIndexed() : geo.clone(), ownUV = g.attributes.uv ? g.attributes.uv.array : null; g.applyMatrix4(m4);
    const pa = g.attributes.position.array, na = g.attributes.normal.array; col.set(o.c === undefined ? 0xffffff : o.c);
    const sc = M.scale || 1;
    for (let i = 0; i < pa.length; i += 9) {
      // normal plana por triângulo + UV projetada pelo eixo dominante
      const ax = pa[i + 3] - pa[i], ay = pa[i + 4] - pa[i + 1], az = pa[i + 5] - pa[i + 2], bx = pa[i + 6] - pa[i], by = pa[i + 7] - pa[i + 1], bz = pa[i + 8] - pa[i + 2];
      let nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx; const l = Math.hypot(nx, ny, nz) || 1; nx /= l; ny /= l; nz /= l;
      const smooth = o.smooth, j = 1 + ((Math.sin(pa[i] * 12.9 + pa[i + 2] * 78.2) * 43758.5) % 1) * (o.j === undefined ? 0.08 : o.j);
      for (let k = 0; k < 3; k++) {
        const px = pa[i + k * 3], py = pa[i + k * 3 + 1], pz = pa[i + k * 3 + 2]; B.p.push(px, py, pz);
        if (smooth) B.n.push(na[i + k * 3], na[i + k * 3 + 1], na[i + k * 3 + 2]); else B.n.push(nx, ny, nz);
        if (M.own && ownUV) { const vi = (i / 3 + k) * 2; B.u.push(ownUV[vi], ownUV[vi + 1]); }
        else { const anx = Math.abs(nx), any = Math.abs(ny), anz = Math.abs(nz); if (any >= anx && any >= anz) B.u.push(px / sc, pz / sc); else if (anx >= anz) B.u.push(pz / sc, py / sc); else B.u.push(px / sc, py / sc); }
        B.c.push(col.r * j, col.g * j, col.b * j);
      }
    }
    g.dispose();
  }
  const box = (mat, x, y, z, sx, sy, sz, o) => { add(mat, G.box, x, y, z, sx, sy, sz, o); if (!(o && (o.ry || o.rx || o.rz)) && sy <= 0.25 && y - sy / 2 <= 0.02 && y + sy / 2 < 0.3 && sx * sz > 4 && mat !== 'light') slabs.push({ x0: x - sx / 2, x1: x + sx / 2, z0: z - sz / 2, z1: z + sz / 2, top: y + sy / 2 }); if (o && o.col) colliders.push({ x0: x - sx / 2, x1: x + sx / 2, y0: y - sy / 2, y1: y + sy / 2, z0: z - sz / 2, z1: z + sz / 2, kind: o.kind || mat }); };
  const cyl = (mat, x, y, z, r, h, o) => { o = o || {}; add(mat, cylG(o.seg || 10, o.top), x, y, z, r, h, r, o); if (o.col) colliders.push({ x0: x - r, x1: x + r, y0: y - h / 2, y1: y + h / 2, z0: z - r, z1: z + r, kind: mat }); };
  const blob = (mat, x, y, z, sx, sy, sz, o) => add(mat, (o && o.d0) ? G.ico0 : G.ico, x, y, z, sx, sy, sz, Object.assign({ smooth: false }, o));

  let rs = 9; const R = () => { rs = (rs * 16807) % 2147483647; return rs / 2147483647; };

  /* ---------- construção do mapa ---------- */
  function building(x, z, w, d, h, tint, m) {
    box(m || 'facade', x, h / 2, z, w, h, d, { col: true, c: tint, kind: 'concrete' });
    box('concrete', x, h + 0.25, z, w + 0.4, 0.5, d + 0.4, { c: 0xbdb8ae });
    box('glass', x, 1.6, z + d / 2 + 0.03, w - 1, 2.8, 0.06, { c: 0xffffff }); box('glass', x, 1.6, z - d / 2 - 0.03, w - 1, 2.8, 0.06);
    box('concrete', x, 3.15, z + d / 2 + 0.6, w - 0.5, 0.18, 1.2, { c: 0x8f8c85 }); // marquise
    box('metal', x + w * 0.25, h + 1.2, z, 2.4, 1.9, 2.4, { c: 0x9aa0a6 }); // ar-condicionado no telhado
  }
  function car(x, z, ry, c) {
    const L = 4.3, W = 1.8; const rot = { ry };
    const cs = Math.cos(ry), sn = Math.sin(ry), off = (a, b) => [x + a * cs + b * sn, z - a * sn + b * cs];
    box('paint', x, 0.62, z, W, 0.65, L, Object.assign({ c }, rot)); const [cx, cz] = off(0, -0.2); box('glass', cx, 1.18, cz, W - 0.2, 0.55, L * 0.5, Object.assign({ c: 0x8fb0c8 }, rot));
    box('paint', cx, 1.47, cz, W - 0.24, 0.06, L * 0.5 - 0.1, Object.assign({ c }, rot));
    [[-1, 1.3], [1, 1.3], [-1, -1.3], [1, -1.3]].forEach(p => { const [wx, wz] = off(p[0] * (W / 2 - 0.05), p[1]); add('paint', cylG(12), wx, 0.34, wz, 0.34, 0.24, 0.34, { rz: PI / 2, ry, c: 0x1b1b1b, j: 0 }); add('metal', cylG(8), wx, 0.34, wz, 0.2, 0.26, 0.2, { rz: PI / 2, ry }); });
    const [fx, fz] = off(0, L / 2); box('light', fx, 0.75, fz, W - 0.4, 0.12, 0.05, Object.assign({ c: 0xfff6d0 }, rot)); const [bx, bz] = off(0, -L / 2); box('light', bx, 0.8, bz, W - 0.3, 0.1, 0.05, Object.assign({ c: 0xb81d1d }, rot));
    const hw = Math.abs(cs) > 0.5 ? W / 2 : L / 2, hd = Math.abs(cs) > 0.5 ? L / 2 : W / 2; colliders.push({ x0: x - hw, x1: x + hw, y0: 0, y1: 1.5, z0: z - hd, z1: z + hd, kind: 'metal' });
  }
  function tree(x, z, s) {
    cyl('bark', x, 1.6 * s, z, 0.2 * s, 3.2 * s, { seg: 7, top: 0.7 }); colliders.push({ x0: x - 0.25 * s, x1: x + 0.25 * s, y0: 0, y1: 3 * s, z0: z - 0.25 * s, z1: z + 0.25 * s, kind: 'wood' });
    const tints = [0xffffff, 0xd9ecc0, 0xbfd9a0];
    blob('leaves', x, 4.1 * s, z, 1.9 * s, 1.6 * s, 1.9 * s, { c: tints[(R() * 3) | 0] }); blob('leaves', x + 1.1 * s, 3.6 * s, z + 0.4 * s, 1.3 * s, 1.1 * s, 1.3 * s, { c: tints[(R() * 3) | 0] });
    blob('leaves', x - 0.9 * s, 3.7 * s, z - 0.5 * s, 1.4 * s, 1.2 * s, 1.4 * s, { c: tints[(R() * 3) | 0] }); blob('leaves', x, 5.1 * s, z, 1.2 * s, 1.0 * s, 1.2 * s);
  }
  function lamp(x, z) { cyl('metal', x, 3, z, 0.08, 6, { seg: 8, c: 0x3a3f46, col: true }); box('metal', x + 0.6, 5.95, z, 1.4, 0.12, 0.25, { c: 0x3a3f46 }); box('light', x + 1.1, 5.85, z, 0.5, 0.06, 0.2, { c: 0xfff1c8 }); }
  function container(x, y, z, ry, c) { const along = Math.abs(Math.cos(ry)) < 0.5; box('container', x, y + 1.3, z, along ? 6.1 : 2.45, 2.6, along ? 2.45 : 6.1, { c, col: true, kind: 'metal' }); }

  function build(scene) {
    // chão
    const ground = new THREE.Mesh(new THREE.PlaneGeometry(600, 600, 1, 1).rotateX(-PI / 2), new THREE.MeshLambertMaterial({ map: TEX.grass() })); ground.material.map.repeat.set(120, 120); scene.add(ground);
    // praça central e ruas
    box('concrete', 0, 0.03, 0, 34, 0.06, 34, { c: 0xe2ded6 });
    box('asphalt', 0, 0.02, -28, 200, 0.05, 11); box('asphalt', 28, 0.021, 0, 11, 0.05, 200);
    for (let i = -100; i < 100; i += 6) { box('light', i, 0.05, -28, 3, 0.01, 0.18, { c: 0xf2d24b }); box('light', 28, 0.051, i, 0.18, 0.01, 3, { c: 0xf2d24b }); }
    [[0, -21.6, 200, 2.2], [0, -34.4, 200, 2.2]].forEach(s => box('concrete', s[0], 0.09, s[1], s[2], 0.18, s[3], { c: 0xcfcac0 }));
    [[21.6, 0, 2.2, 200], [34.4, 0, 2.2, 200]].forEach(s => box('concrete', s[0], 0.091, s[1], s[2], 0.18, s[3], { c: 0xcfcac0 }));
    // faixas de pedestre
    for (let i = 0; i < 8; i++) box('light', -3.5 + i, 0.06, -28, 0.55, 0.01, 8, { c: 0xf4f4f4 });
    // prédios (cidade ao norte, depois da rua)
    const tints = [0xffffff, 0xf2e3cc, 0xd9e2ea, 0xe8d0c0, 0xcdd6c7];
    [[-50, -50, 18, 20, 26], [-26, -48, 14, 16, 16], [-6, -52, 16, 22, 34], [14, -47, 10, 12, 12], [52, -50, 20, 18, 22], [-72, -46, 14, 14, 18], [74, -48, 14, 16, 30], [-50, -78, 24, 18, 40], [10, -80, 22, 20, 48]].forEach((b, i) => building(b[0], b[1], b[2], b[3], b[4], tints[i % 5], ['facade', 'brickwin', 'tower', 'facade', 'brickwin', 'facade', 'tower', 'facade', 'tower'][i]));
    // casa de tijolo e muro
    box('brick', -46, 3, 26, 14, 6, 10, { col: true }); box('concrete', -46, 6.3, 26, 15, 0.6, 11, { c: 0x6b6b6b }); box('wood', -46, 1.2, 31.05, 1.4, 2.4, 0.1, { c: 0x6a4a2a });
    // armazém e pátio de contêineres (leste)
    box('metal', 52, 5, 18, 22, 10, 26, { col: true, c: 0xd0d4d8 }); box('metal', 52, 10.3, 18, 23, 0.6, 27, { c: 0x8c9196 }); box('metal', 40.9, 3, 18, 0.2, 6, 8, { c: 0x5b6067 });
    const cc = [0xc0392b, 0x2471a3, 0x27ae60, 0xd68910, 0x7d3c98, 0x1f618d];
    [[45, 0, -2, 0], [52, 0, -2, 0], [59, 0, -2, 0], [45, 2.6, -2, 0], [52, 2.6, -2, 0], [48, 0, -8, PI / 2], [44, 0, 34, 0], [51, 0, 34, 0], [44, 2.6, 34, 0]].forEach((c, i) => container(c[0], c[1], c[2], c[3], cc[i % cc.length]));
    // carros estacionados
    const carC = [0xb71c1c, 0x1e3a5f, 0xe0e0e0, 0x2e7d32, 0x212121, 0xf9a825, 0x6d4c41];
    [-40, -28, -12, 6, 44, 62].forEach((x, i) => car(x, -24.2, PI / 2, carC[i % carC.length])); [-34, -4, 50].forEach((x, i) => car(x, -31.8, -PI / 2, carC[(i + 3) % carC.length]));
    [12, -14].forEach((z, i) => car(24.3, z, 0, carC[(i + 5) % carC.length]));
    // postes, bancos e árvores
    for (let i = -90; i <= 90; i += 15) { lamp(i, -21.8); lamp(21.8, i + 7); }
    [[-12, 12], [12, 12], [-12, -12], [12, -12]].forEach(p => { box('wood', p[0], 0.45, p[1], 2, 0.08, 0.5, { c: 0xffffff }); box('wood', p[0], 0.8, p[1] + 0.25 * Math.sign(p[1]), 2, 0.5, 0.08); box('metal', p[0], 0.22, p[1], 1.8, 0.44, 0.08, { c: 0x333333 }); });
    for (let i = 0; i < 80; i++) { const a = R() * TAU, d = 30 + R() * 75, x = Math.cos(a) * d, z = Math.sin(a) * d; if (z < -15 && z > -40) continue; if (x > 15 && x < 70 && z > -15 && z < 45) continue; if (Math.abs(x) < 20 && Math.abs(z) < 20) continue; if (x < -10 && x > -64 && z > 0 && z < 22) continue; tree(x, z, 0.85 + R() * 0.5); }
    [[-16, 16], [16, 16], [-16, -16], [16, -16]].forEach(p => tree(p[0], p[1], 0.9));
    // estande de tiro (oeste): mesas, divisórias, alvos e paredão
    box('concrete', -37, 0.05, 11, 46, 0.1, 18, { c: 0xb8b4ac });
    for (let i = 0; i < 4; i++) { const z = 4 + i * 4.5; box('wood', -16.5, 0.95, z, 0.9, 0.08, 3.2, { c: 0xffffff }); box('metal', -16.5, 0.47, z, 0.8, 0.9, 0.08, { c: 0x444 }); box('wood', -16.3, 1.4, z + 2.25, 1.6, 2.8, 0.1, { c: 0xd8c4a0 }); }
    colliders.push({ x0: -17, x1: -16, y0: 0, y1: 1, z0: 2, z1: 19, kind: 'wood' });
    [-28, -38, -50].forEach((x, i) => [5, 11, 17].forEach((z, k) => { box('wood', x, 0.7, z - 0.45, 0.08, 1.4, 0.08); box('wood', x, 0.7, z + 0.45, 0.08, 1.4, 0.08); add('target', G.box, x, 1.55, z, 0.04, 1.1, 0.8, { ry: PI / 2 }); plates.push({ x, y: 1.55, z, w: 0.8, h: 1.1, kind: 'paper' }); }));
    [-33, -44].forEach(x => [8, 14].forEach(z => { cyl('metal', x, 1.05, z, 0.04, 2.1, { seg: 6, c: 0x333333 }); add('paint', cylG(16), x, 1.25, z, 0.26, 0.03, 0.26, { rz: PI / 2, c: 0xe8e8e8, j: 0 }); plates.push({ x, y: 1.25, z, r: 0.26, kind: 'steel' }); }));
    box('concrete', -60, 3, 11, 2, 6, 20, { col: true, c: 0x8f8a80 }); for (let i = 0; i < 20; i++) blob('dirt', -58.6, 0.4 + (i % 4) * 0.55, 1.5 + (i >> 2) * 4.6 + (i % 2), 0.7, 0.35, 1.2, { d0: true });
    // muros laterais do estande
    box('concrete', -38, 1.5, 1.4, 44, 3, 0.4, { col: true, c: 0xa8a39a }); box('concrete', -38, 1.5, 20.6, 44, 3, 0.4, { col: true, c: 0xa8a39a });
    // parquinho de física (rampa e plataformas) — sul
    box('concrete', 0, 0.6, 30, 8, 1.2, 4, { col: true, c: 0xc7c2b8 }); box('wood', 0, 1.25, 30, 8, 0.1, 4, { c: 0xffffff });
    box('metal', -9, 1.5, 30, 0.2, 3, 6, { col: true, c: 0x5f646b }); box('metal', 9, 1.5, 30, 0.2, 3, 6, { col: true, c: 0x5f646b });
    // pedras e montanhas
    for (let i = 0; i < 24; i++) { const a = R() * TAU, d = 40 + R() * 60; blob('rock', Math.cos(a) * d, 0.3, Math.sin(a) * d, 0.8 + R(), 0.6 + R() * 0.6, 0.8 + R(), { d0: true, c: 0xffffff }); }
    for (let i = 0; i < 34; i++) { const a = i / 34 * TAU + R() * 0.1, d = 280 + R() * 60, s = 26 + R() * 34; add('rock', G.ico0, Math.cos(a) * d, s * 0.15, Math.sin(a) * d, s * 1.7, s * (0.55 + R() * 0.45), s * 1.7, { c: i % 3 ? 0xa9b8a0 : 0xc2c6c4, j: 0.12 }); }
    // cerca/limite do mapa
    for (let i = 0; i < 64; i++) { const a = i / 64 * TAU; cyl('wood', Math.cos(a) * 95, 0.6, Math.sin(a) * 95, 0.07, 1.2, { seg: 5 }); }
    // mesa de armas no spawn
    box('wood', 0, 0.9, 4, 2.4, 0.08, 1.0, { c: 0xffffff }); [[-1.1, -0.42], [1.1, -0.42], [-1.1, 0.42], [1.1, 0.42]].forEach(p => box('metal', p[0], 0.45, 4 + p[1], 0.06, 0.9, 0.06, { c: 0x333 })); colliders.push({ x0: -1.2, x1: 1.2, y0: 0, y1: 0.94, z0: 3.5, z1: 4.5, kind: 'wood' });
    // monta as malhas
    const group = new THREE.Group();
    for (const k in buckets) {
      const B = buckets[k], M = MATS[k], g = new THREE.BufferGeometry();
      g.setAttribute('position', new THREE.Float32BufferAttribute(B.p, 3)); g.setAttribute('normal', new THREE.Float32BufferAttribute(B.n, 3)); g.setAttribute('uv', new THREE.Float32BufferAttribute(B.u, 2)); g.setAttribute('color', new THREE.Float32BufferAttribute(B.c, 3)); g.computeBoundingSphere();
      const map = M.tex ? TEX[M.tex]() : null; if (map) map.repeat.set(1, 1);
      const mat = M.unlit ? new THREE.MeshBasicMaterial({ vertexColors: true }) : new THREE.MeshLambertMaterial({ map, vertexColors: true });
      group.add(new THREE.Mesh(g, mat));
    }
    scene.add(group);
    return group;
  }

  /* ---------- céu 360° ---------- */
  function sky() {
    const W = 2048, H = 1024, c = document.createElement('canvas'); c.width = W; c.height = H; const x = c.getContext('2d');
    const g = x.createLinearGradient(0, 0, 0, H); [[0, '#2f6fb8'], [.28, '#5d9ad6'], [.45, '#a9cfee'], [.5, '#e9f2f6'], [.5001, '#c8d2c4'], [1, '#8a9a80']].forEach(s => g.addColorStop(s[0], s[1])); x.fillStyle = g; x.fillRect(0, 0, W, H);
    const glow = (cx, cy, r, rgb, a) => { for (const dx of [-W, 0, W]) { const gg = x.createRadialGradient(cx + dx, cy, 0, cx + dx, cy, r); gg.addColorStop(0, `rgba(${rgb},${a})`); gg.addColorStop(1, `rgba(${rgb},0)`); x.fillStyle = gg; x.fillRect(cx + dx - r, cy - r, r * 2, r * 2); } };
    glow(W * 0.32, H * 0.27, H * 0.22, '255,248,220', 0.8); glow(W * 0.32, H * 0.27, H * 0.04, '255,255,255', 1);
    for (let i = 0; i < 45; i++) { const cx = R() * W, cy = H * (0.12 + R() * 0.33), s = 60 + R() * 140; for (let k = 0; k < 8; k++) glow(cx + (R() - .5) * s * 1.8, cy + (R() - .5) * s * 0.2, s * (0.3 + R() * 0.35), k % 3 ? '255,255,255' : '225,232,240', 0.45); }
    const t = new THREE.CanvasTexture(c); t.colorSpace = THREE.SRGBColorSpace; t.wrapS = THREE.RepeatWrapping; t.repeat.x = -1;
    const m = new THREE.Mesh(new THREE.SphereGeometry(400, 40, 20), new THREE.MeshBasicMaterial({ map: t, side: THREE.BackSide, fog: false, depthWrite: false, toneMapped: false })); m.renderOrder = -10; m.frustumCulled = false; return m;
  }

  /* ---------- consultas ---------- */
  function rayAABB(o, d, b, maxT) {
    let t0 = 0, t1 = maxT; const ax = ['x', 'y', 'z'], lo = [b.x0, b.y0, b.z0], hi = [b.x1, b.y1, b.z1]; let nAx = 0, nSign = 1;
    for (let i = 0; i < 3; i++) {
      const oi = o[ax[i]], di = d[ax[i]];
      if (Math.abs(di) < 1e-9) { if (oi < lo[i] || oi > hi[i]) return null; continue; }
      let ta = (lo[i] - oi) / di, tb = (hi[i] - oi) / di, s = -1; if (ta > tb) { const tt = ta; ta = tb; tb = tt; s = 1; }
      if (ta > t0) { t0 = ta; nAx = i; nSign = s; } if (tb < t1) t1 = tb; if (t0 > t1) return null;
    }
    const n = new THREE.Vector3(); n.setComponent(nAx, nSign * (d.getComponent(nAx) > 0 ? 1 : 1)); if (d.getComponent(nAx) > 0) n.setComponent(nAx, -1); else n.setComponent(nAx, 1);
    return { t: t0, n, kind: b.kind };
  }
  function raycast(o, d, maxT) {
    let best = null;
    if (d.y < 0) { const t = -o.y / d.y; if (t < maxT) best = { t, n: new THREE.Vector3(0, 1, 0), kind: Math.abs(o.x + d.x * t) < 17 && Math.abs(o.z + d.z * t) < 17 ? 'concrete' : 'ground' }; }
    for (const b of colliders) { const h = rayAABB(o, d, b, best ? best.t : maxT); if (h && (!best || h.t < best.t)) best = h; }
    for (const p of plates) {
      if (p.kind === 'paper' || p.kind === 'steel') { if (Math.abs(d.x) < 1e-6) continue; const t = (p.x - o.x) / d.x; if (t <= 0 || (best && t >= best.t) || t > maxT) continue; const y = o.y + d.y * t, z = o.z + d.z * t;
        const hit = p.kind === 'paper' ? (Math.abs(y - p.y) < p.h / 2 && Math.abs(z - p.z) < p.w / 2) : Math.hypot(y - p.y, z - p.z) < p.r; if (hit) best = { t, n: new THREE.Vector3(d.x > 0 ? -1 : 1, 0, 0), kind: p.kind, plate: p }; }
    }
    return best;
  }
  function collide(pos, r, h) { // empurra um cilindro (raio r, altura h acima de pos.y) para fora dos colisores
    for (let it = 0; it < 2; it++) for (const b of colliders) {
      if (pos.y + (h || 1.7) < b.y0 || pos.y > b.y1 - 0.25) continue;
      const cx = clamp(pos.x, b.x0, b.x1), cz = clamp(pos.z, b.z0, b.z1); let dx = pos.x - cx, dz = pos.z - cz; const d2 = dx * dx + dz * dz;
      if (d2 < r * r) { if (d2 > 1e-8) { const d = Math.sqrt(d2), k = (r - d) / d; pos.x += dx * k; pos.z += dz * k; } else { const l = pos.x - b.x0, rr = b.x1 - pos.x, t = pos.z - b.z0, bt = b.z1 - pos.z, m = Math.min(l, rr, t, bt); if (m === l) pos.x = b.x0 - r; else if (m === rr) pos.x = b.x1 + r; else if (m === t) pos.z = b.z0 - r; else pos.z = b.z1 + r; } }
    }
    const d = Math.hypot(pos.x, pos.z); if (d > 92) { pos.x *= 92 / d; pos.z *= 92 / d; }
  }
  function floorAt(x, z, y) { let f = 0; for (const b of slabs) if (x > b.x0 && x < b.x1 && z > b.z0 && z < b.z1 && b.top > f) f = b.top; for (const b of colliders) if (x > b.x0 && x < b.x1 && z > b.z0 && z < b.z1 && b.y1 <= y + 0.05 && b.y1 > f) f = b.y1; return f; }
  return { build, sky, raycast, collide, floorAt, colliders, plates, TAU };
})();
