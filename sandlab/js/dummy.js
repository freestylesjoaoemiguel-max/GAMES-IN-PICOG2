'use strict';
/* Bonecos (manequins de teste de impacto) com ragdoll Verlet + "marionete ativa" para ficar de pé/andar */
const DUMMY = (() => {
  // índices das juntas
  const J = { head: 0, neck: 1, chest: 2, pelvis: 3, lSh: 4, lEl: 5, lHa: 6, rSh: 7, rEl: 8, rHa: 9, lHip: 10, lKn: 11, lFt: 12, rHip: 13, rKn: 14, rFt: 15 };
  const POSE = [[0, 1.66, 0], [0, 1.47, 0], [0, 1.27, 0], [0, 0.96, 0], [-0.2, 1.42, 0], [-0.24, 1.15, 0], [-0.26, 0.9, 0.04], [0.2, 1.42, 0], [0.24, 1.15, 0], [0.26, 0.9, 0.04], [-0.1, 0.92, 0], [-0.1, 0.5, 0.03], [-0.11, 0.07, 0.04], [0.1, 0.92, 0], [0.1, 0.5, 0.03], [0.11, 0.07, 0.04]];
  const RAD = [0.13, 0.06, 0.15, 0.13, 0.07, 0.055, 0.06, 0.07, 0.055, 0.06, 0.08, 0.07, 0.07, 0.08, 0.07, 0.07];
  const LINKS = [[0, 1], [1, 2], [2, 3], [1, 4], [4, 5], [5, 6], [1, 7], [7, 8], [8, 9], [3, 10], [10, 11], [11, 12], [3, 13], [13, 14], [14, 15],
    [4, 7], [10, 13], [4, 2], [7, 2], [4, 3], [7, 3], [10, 2], [13, 2], [0, 4], [0, 7], [4, 10], [7, 13], [0, 2]]; // + travamentos do tronco
  const YEL = new THREE.Color(0xf2c31b), BLK = new THREE.Color(0x1a1a1c), GRY = new THREE.Color(0x8a8f96);
  // partes desenhadas: [a, b, raio, tipo, cor, deslocamento(rel. cabeça), escala]
  const DUMMY_PARTS = [[1, 2, 0.16, 'tor', YEL], [2, 3, 0.15, 'tor', YEL], [3, 3, 0.15, 'pel', BLK], [4, 5, 0.055, 'cyl', YEL], [5, 6, 0.048, 'cyl', YEL], [7, 8, 0.055, 'cyl', YEL], [8, 9, 0.048, 'cyl', YEL],
    [10, 11, 0.075, 'cyl', YEL], [11, 12, 0.06, 'cyl', YEL], [13, 14, 0.075, 'cyl', YEL], [14, 15, 0.06, 'cyl', YEL],
    [5, 5, 0.06, 'sph', BLK], [8, 8, 0.06, 'sph', BLK], [11, 11, 0.07, 'sph', BLK], [14, 14, 0.07, 'sph', BLK], [6, 6, 0.055, 'sph', GRY], [9, 9, 0.055, 'sph', GRY],
    [12, 12, 0.08, 'foot', BLK], [15, 15, 0.08, 'foot', BLK], [0, 0, 0.12, 'head', YEL], [1, 1, 0.05, 'cyl0', BLK]];
  const SKINS = [0xf1c8a6, 0xe0ac84, 0xc68642, 0x9a6a44, 0x6e4a2e, 0xffdcc0], HAIRS = [0x1b1410, 0x3b2416, 0x6b4423, 0xb98a4a, 0x8a8a90, 0x101010];
  const SHIRTS = [0x2f6fb5, 0xc0392b, 0xf2f2f2, 0x27ae60, 0x34495e, 0xe67e22, 0x8e44ad, 0x1abc9c, 0xd35400, 0x2c3e50], PANTS = [0x2b3d5c, 0x1f2a44, 0x3b3b3b, 0x5c4a36, 0x203040, 0x6b6b6b], SHOES = [0x1a1a1a, 0xf0f0f0, 0x5a3a22, 0x2a2a2a];
  const pick = a => a[(Math.random() * a.length) | 0], C = h => new THREE.Color(h);
  function humanParts(look) {
    const sk = C(look.skin), sh = C(look.shirt), pa = C(look.pants), so = C(look.shoes), hr = C(look.hair), wh = C(0xf4f4f4), ey = C(0x1d1712), lip = C(look.skin).multiplyScalar(0.72);
    const arm = look.long ? sh : sk;
    return [[1, 2, 0.16, 'torc', sh], [2, 3, 0.15, 'torc', sh], [3, 3, 0.15, 'pelc', pa], [2, 2, 0.15, 'chest', sh], [4, 5, 0.058, 'cyl', sh], [5, 6, 0.046, 'cyl', arm], [7, 8, 0.058, 'cyl', sh], [8, 9, 0.046, 'cyl', arm],
      [10, 11, 0.082, 'cyl', pa], [11, 12, 0.064, 'cyl', pa], [13, 14, 0.082, 'cyl', pa], [14, 15, 0.064, 'cyl', pa],
      [5, 5, 0.05, 'sph', arm], [8, 8, 0.05, 'sph', arm], [11, 11, 0.075, 'sph', pa], [14, 14, 0.075, 'sph', pa], [6, 6, 0.05, 'hand', sk], [9, 9, 0.05, 'hand', sk], [4, 4, 0.07, 'sph', sh], [7, 7, 0.07, 'sph', sh],
      [12, 12, 0.08, 'foot', so], [15, 15, 0.08, 'foot', so], [1, 1, 0.05, 'cyl0', sk], [0, 0, 0.12, 'head', sk],
      [0, 0, 0, 'att', hr, [0, 0.045, -0.014], [0.122, look.longHair ? 0.115 : 0.1, 0.13]], [0, 0, 0, 'att', hr, [0, -0.02, -0.06], look.longHair ? [0.12, 0.13, 0.08] : [0.105, 0.07, 0.07]],
      [0, 0, 0, 'att', wh, [-0.04, 0.025, 0.098], [0.022, 0.014, 0.012]], [0, 0, 0, 'att', wh, [0.04, 0.025, 0.098], [0.022, 0.014, 0.012]],
      [0, 0, 0, 'att', ey, [-0.04, 0.025, 0.108], [0.011, 0.011, 0.006]], [0, 0, 0, 'att', ey, [0.04, 0.025, 0.108], [0.011, 0.011, 0.006]],
      [0, 0, 0, 'att', hr, [-0.04, 0.05, 0.105], [0.025, 0.006, 0.008]], [0, 0, 0, 'att', hr, [0.04, 0.05, 0.105], [0.025, 0.006, 0.008]],
      [0, 0, 0, 'attb', C(look.skin).multiplyScalar(0.9), [0, 0.0, 0.118], [0.022, 0.04, 0.03]], [0, 0, 0, 'attb', lip, [0, -0.05, 0.105], [0.042, 0.009, 0.012]],
      [0, 0, 0, 'att', sk, [-0.118, 0.01, 0], [0.018, 0.034, 0.026]], [0, 0, 0, 'att', sk, [0.118, 0.01, 0], [0.018, 0.034, 0.026]]];
  }
  function randomLook() { return { skin: pick(SKINS), hair: pick(HAIRS), shirt: pick(SHIRTS), pants: pick(PANTS), shoes: pick(SHOES), long: Math.random() < 0.35, longHair: Math.random() < 0.35 }; }
  // geometria base (unitária)
  function unit(type) {
    const g = type === 'sph' || type === 'head' ? new THREE.SphereGeometry(1, type === 'head' ? 14 : 8, type === 'head' ? 10 : 6) : type === 'tor' || type === 'pel' || type === 'foot' ? new THREE.BoxGeometry(1, 1, 1) : new THREE.CylinderGeometry(1, 1, 1, 8, 1);
    if (type === 'cyl' || type === 'cyl0') g.translate(0, 0.5, 0); if (type === 'tor') g.translate(0, 0.5, 0); if (type === 'foot') g.translate(0, 0, 0);
    const ng = g.toNonIndexed(); g.dispose(); return ng;
  }
  const BASE = {}; ['tor', 'pel', 'cyl', 'cyl0', 'sph', 'head', 'foot'].forEach(t => BASE[t] = unit(t)); { const g = new THREE.CylinderGeometry(1, 0.92, 1, 14, 1); g.translate(0, 0.5, 0); BASE.torc = g.toNonIndexed(); } { const g = new THREE.SphereGeometry(1, 12, 8); BASE.pelc = g.toNonIndexed(); } BASE.att = BASE.sph; BASE.attb = BASE.foot; BASE.hand = BASE.sph; BASE.chest = BASE.pelc;
  const _hx = new THREE.Vector3(), _hy = new THREE.Vector3(), _hz = new THREE.Vector3();
  const _a = new THREE.Vector3(), _b = new THREE.Vector3(), _x = new THREE.Vector3(), _y = new THREE.Vector3(), _z = new THREE.Vector3(), _t = new THREE.Vector3(), _up = new THREE.Vector3(0, 1, 0);

  class Dummy {
    constructor(scene, x, z, yaw, id, kind, look) {
      this.id = id; this.kind = kind || 'dummy'; this.look = look || randomLook(); this.parts = this.kind === 'human' ? humanParts(this.look) : DUMMY_PARTS; this.angry = false; this.bleed = 0; this.p = []; this.pp = []; this.alive = true; this.hp = 100; this.stab = 1; this.down = 0; this.grabbed = -1;
      this.root = new THREE.Vector3(x, 0, z); this.home = new THREE.Vector3(x, 0, z); this.yaw = yaw; this.phase = Math.random() * 6; this.state = 'idle'; this.timer = 1 + Math.random() * 3; this.goal = new THREE.Vector3(x, 0, z); this.punch = 0; this.speed = 0; this.flash = 0; this.limp = 0;
      for (let i = 0; i < 16; i++) { const v = this.local(i, new THREE.Vector3()); this.p.push(v.clone()); this.pp.push(v.clone()); }
      this.links = LINKS.map(l => [l[0], l[1], this.p[l[0]].distanceTo(this.p[l[1]])]);
      // malha dinâmica única
      let n = 0; this.parts.forEach(pt => n += BASE[pt[3]].attributes.position.count);
      const g = new THREE.BufferGeometry(); this.pos = new Float32Array(n * 3); this.nor = new Float32Array(n * 3); const colA = new Float32Array(n * 3);
      let o = 0; this.parts.forEach(pt => { const bg = BASE[pt[3]], pa = bg.attributes.position.array, cnt = bg.attributes.position.count; for (let i = 0; i < cnt; i++) { let c = pt[4]; if (pt[3] === 'head' && this.kind === 'dummy') { const lx = pa[i * 3], ly = pa[i * 3 + 1], lz = pa[i * 3 + 2]; if (lz > 0.25 && lx * ly > 0) c = BLK; if (lz > 0.25 && Math.abs(lx) < 0.12 && Math.abs(ly) < 0.12) c = BLK; } if (pt[3] === 'tor' && this.kind === 'dummy' && Math.abs(pa[i * 3]) > 0.47) c = BLK; colA[(o + i) * 3] = c.r; colA[(o + i) * 3 + 1] = c.g; colA[(o + i) * 3 + 2] = c.b; } o += cnt; });
      this.baseCol = colA.slice(); g.setAttribute('position', new THREE.BufferAttribute(this.pos, 3)); g.setAttribute('normal', new THREE.BufferAttribute(this.nor, 3)); g.setAttribute('color', new THREE.BufferAttribute(colA, 3));
      this.mesh = new THREE.Mesh(g, this.kind === 'human' ? new THREE.MeshPhongMaterial({ vertexColors: true, shininess: 8, specular: 0x111111 }) : new THREE.MeshLambertMaterial({ vertexColors: true })); this.mesh.frustumCulled = false; scene.add(this.mesh); this.updateMesh();
    }
    local(i, out) { // pose-alvo da junta i no mundo
      const P = POSE[i]; let x = P[0], y = P[1], z = P[2]; const ph = this.phase, w = Math.min(1, this.speed / 1.1);
      if (i === J.lKn || i === J.lFt) { z += Math.sin(ph) * (i === J.lFt ? 0.32 : 0.16) * w; if (i === J.lFt) y += Math.max(0, Math.cos(ph)) * 0.12 * w; }
      if (i === J.rKn || i === J.rFt) { z += Math.sin(ph + PI) * (i === J.rFt ? 0.32 : 0.16) * w; if (i === J.rFt) y += Math.max(0, Math.cos(ph + PI)) * 0.12 * w; }
      if (i === J.lEl || i === J.lHa) z += Math.sin(ph + PI) * (i === J.lHa ? 0.22 : 0.1) * w;
      if (i === J.rEl || i === J.rHa) z += Math.sin(ph) * (i === J.rHa ? 0.22 : 0.1) * w;
      if (this.punch > 0 && (i === J.rEl || i === J.rHa)) { const k = Math.sin((1 - this.punch / 0.45) * PI); if (i === J.rHa) { y = 1.4; z = 0.25 + 0.4 * k; x = 0.12; } else { y = 1.32; z = 0.1 + 0.2 * k; } }
      if (this.state === 'combat' && this.punch <= 0 && (i === J.lHa || i === J.rHa)) { y = 1.3; z = 0.25; x *= 0.7; }
      y += Math.abs(Math.sin(ph)) * 0.03 * w + (this.alive ? Math.sin(this.phase * 0.3) * 0.004 : 0);
      const c = Math.cos(this.yaw), s = Math.sin(this.yaw);
      return out.set(this.root.x + x * c + z * s, y, this.root.z - x * s + z * c);
    }
    hit(j, imp, dmg, src) {
      if (src === 'player' && this.alive) { this.angry = true; this.state = 'combat'; }
      const p = this.p[j], pp = this.pp[j]; pp.sub(imp); // impulso Verlet = desloca o "anterior"
      [this.p[J.chest], this.p[J.pelvis]].forEach(q => q !== p && q.addScaledVector(imp, 0.15));
      this.hp -= dmg * (j === J.head ? 3 : 1); this.flash = 0.12; this.stab = Math.min(this.stab, 0.08); this.limp = Math.max(this.limp, 0.35);
      if (this.hp <= 0 && this.alive) { this.alive = false; this.down = 0; return 'kill'; }
      return j === J.head ? 'head' : 'hit';
    }
    revive() { this.alive = true; this.hp = 100; this.down = 0; this.limp = 0; this.angry = false; }
    step(dt, ctx) {
      const pel = this.p[J.pelvis];
      // IA: anda / persegue / soco
      if (this.alive) {
        if (this.limp > 0 || this.grabbed >= 0) { this.limp -= dt; this.stab = 0; } else this.stab = Math.min(1, this.stab + dt * (this.p[J.head].y > 1.2 ? 0.9 : 0.35));
        if (this.grabbed < 0 && this.stab > 0.6) {
          const toP = _t.set(ctx.player.x - this.root.x, 0, ctx.player.z - this.root.z), dP = toP.length();
          if ((ctx.combat || this.angry) && dP < 40) { this.state = 'combat'; this.goal.set(ctx.player.x, 0, ctx.player.z); }
          else if (this.state === 'combat') this.state = 'idle';
          this.timer -= dt;
          if (this.state === 'idle' && this.timer <= 0) { const a = Math.random() * TAU, r = 2 + Math.random() * 7; this.goal.set(this.home.x + Math.cos(a) * r, 0, this.home.z + Math.sin(a) * r); this.state = 'walk'; this.timer = 8; }
          const to = _t.set(this.goal.x - this.root.x, 0, this.goal.z - this.root.z), d = to.length();
          let want = 0;
          if ((this.state === 'walk' && d > 0.4) || (this.state === 'combat' && d > 0.85)) { want = this.state === 'combat' ? 1.5 : 0.9; let dy = Math.atan2(to.x, to.z) - this.yaw; while (dy > PI) dy -= TAU; while (dy < -PI) dy += TAU; this.yaw += dy * Math.min(1, dt * 4); }
          else if (this.state === 'walk') { this.state = 'idle'; this.timer = 2 + Math.random() * 4; }
          if (this.state === 'combat' && d <= 0.95) { let dy = Math.atan2(to.x, to.z) - this.yaw; while (dy > PI) dy -= TAU; while (dy < -PI) dy += TAU; this.yaw += dy * Math.min(1, dt * 6); if (this.punch <= 0 && Math.random() < dt * (this.angry ? 2.6 : 1.6)) { this.punch = 0.45; this.punched = false; } }
          this.speed += (want - this.speed) * Math.min(1, dt * 4);
          if (this.detour > 0) this.detour -= dt; const mvYaw = this.yaw + (this.detour > 0 ? this.detourDir * 1.4 : 0);
          const ox = this.root.x, oz = this.root.z; this.root.x += Math.sin(mvYaw) * this.speed * dt; this.root.z += Math.cos(mvYaw) * this.speed * dt; this.phase += dt * this.speed * 5.5;
          const rp = { x: this.root.x, y: 0, z: this.root.z }; World.collide(rp, 0.3, 1.8); this.root.x = rp.x; this.root.z = rp.z;
          const moved = Math.hypot(this.root.x - ox, this.root.z - oz); this.stuck = want > 0.3 && moved < this.speed * dt * 0.35 ? (this.stuck || 0) + dt : 0;
          if (this.stuck > 0.35 && !(this.detour > 0)) { this.detour = 0.9; this.detourDir = Math.random() < 0.5 ? -1 : 1; this.stuck = 0; }
        } else { this.speed *= 0.9; }
        if (this.punch > 0) { this.punch -= dt; if (!this.punched && this.punch < 0.22) { this.punched = true; const hd = this.p[J.rHa].distanceTo(ctx.playerHead); if (hd < 0.95) ctx.onPunch(this); } }
        // raiz segue a pélvis quando desequilibrado (evita "teleporte")
        if (this.stab < 0.95) { this.root.x += (pel.x - this.root.x) * Math.min(1, dt * 6 * (1 - this.stab)); this.root.z += (pel.z - this.root.z) * Math.min(1, dt * 6 * (1 - this.stab)); }
      } else {
        this.down += dt; this.stab = 0; this.speed = 0; this.root.set(pel.x, 0, pel.z);
        if (ctx.autoRevive && this.down > 9) this.revive();
      }
      if (this.flash > 0) this.flash -= dt;
      // Verlet
      const g = -9.8 * dt * dt, k = this.alive ? 0.32 * this.stab * this.stab : 0;
      for (let i = 0; i < 16; i++) {
        const p = this.p[i], pp = this.pp[i], vx = (p.x - pp.x) * 0.992, vy = (p.y - pp.y) * 0.992, vz = (p.z - pp.z) * 0.992; pp.copy(p); p.x += vx; p.y += vy + g; p.z += vz;
        if (k > 0) { this.local(i, _a); p.x += (_a.x - p.x) * k; p.y += (_a.y - p.y) * k; p.z += (_a.z - p.z) * k; }
      }
      if (this.grabbed >= 0 && ctx.grabPoint) { this.p[this.grabbed].copy(ctx.grabPoint); }
      for (let it = 0; it < 5; it++) {
        for (const L of this.links) { const a = this.p[L[0]], b = this.p[L[1]]; _x.subVectors(b, a); const d = _x.length() || 1e-6, diff = (d - L[2]) / d * 0.5; a.addScaledVector(_x, diff); b.addScaledVector(_x, -diff); }
        if (this.grabbed >= 0 && ctx.grabPoint) this.p[this.grabbed].copy(ctx.grabPoint);
      }
      for (let i = 0; i < 16; i++) { // chão e paredes
        const p = this.p[i], pp = this.pp[i], r = RAD[i], fl = World.floorAt(p.x, p.z, p.y);
        if (p.y < fl + r) { p.y = fl + r; const fr = this.alive && k > 0.1 ? 0.3 : 0.75; pp.x += (p.x - pp.x) * fr; pp.z += (p.z - pp.z) * fr; if (pp.y < p.y - 0.06 && ctx.onThud && i === J.pelvis) ctx.onThud(this, p.y - pp.y); pp.y = p.y + (pp.y - p.y) * -0.2; }
        const q = { x: p.x, y: p.y - r, z: p.z }; World.collide(q, r, r * 2); if (q.x !== p.x || q.z !== p.z) { pp.x = q.x + (pp.x - p.x) * -0.3; pp.z = q.z + (pp.z - p.z) * -0.3; p.x = q.x; p.z = q.z; }
      }
      this.updateMesh();
    }
    updateMesh() {
      const P = this.p; let o = 0;
      const right = _z.subVectors(P[J.rSh], P[J.lSh]).normalize();
      for (const pt of this.parts) {
        const bg = BASE[pt[3]], pa = bg.attributes.position.array, na = bg.attributes.normal.array, cnt = bg.attributes.position.count, r = pt[2];
        const A = P[pt[0]], Bp = P[pt[1]]; let len = 1; _y.copy(_up);
        if (pt[0] !== pt[1]) { _y.subVectors(Bp, A); len = _y.length() || 1e-3; _y.multiplyScalar(1 / len); }
        if (pt[3] === 'tor' || pt[3] === 'torc' || pt[3] === 'pel' || pt[3] === 'pelc' || pt[3] === 'chest' || pt[3] === 'foot' || pt[3] === 'head') {
          if (pt[3] === 'pel' || pt[3] === 'pelc' || pt[3] === 'chest') _y.subVectors(P[J.chest], P[J.pelvis]).normalize(); if (pt[3] === 'head') _y.subVectors(P[J.head], P[J.neck]).normalize(); if (pt[3] === 'foot') _y.subVectors(P[pt[0] - 1], P[pt[0]]).normalize();
          _x.copy(right).addScaledVector(_y, -right.dot(_y)).normalize();
        } else { _x.set(1, 0, 0); if (Math.abs(_y.x) > 0.9) _x.set(0, 0, 1); _x.addScaledVector(_y, -_x.dot(_y)).normalize(); }
        _a.crossVectors(_x, _y); // eixo z
        if (pt[3] === 'head') { _hx.copy(_x); _hy.copy(_y); _hz.copy(_a); }
        if (pt[3] === 'att' || pt[3] === 'attb') { _x.copy(_hx); _y.copy(_hy); _a.copy(_hz); }
        let sx = r, sy = len, sz = r, ox = 0, oy = 0, oz = 0;
        if (pt[3] === 'tor') { sx = 0.36; sz = 0.2; sy = pt[0] === 1 ? len : len; }
        if (pt[3] === 'pel') { sx = 0.34; sy = 0.16; sz = 0.2; }
        if (pt[3] === 'torc') { sx = pt[0] === 1 ? 0.185 : 0.165; sz = pt[0] === 1 ? 0.115 : 0.105; }
        if (pt[3] === 'pelc') { sx = 0.165; sy = 0.1; sz = 0.11; }
        if (pt[3] === 'chest') { sx = 0.175; sy = 0.12; sz = 0.12; oy = 0.08; oz = 0.012; }
        if (pt[3] === 'foot') { sx = 0.1; sy = 0.07; sz = 0.24; oz = 0.06; }
        if (pt[3] === 'head') { sx = 0.11; sy = 0.13; sz = 0.12; oy = 0.02; }
        if (pt[3] === 'sph') { sy = r; }
        if (pt[3] === 'cyl0') { sy = 0.1; oy = 0.02; }
        if (pt[3] === 'hand') { sx = 0.042; sy = 0.055; sz = 0.075; }
        if (pt[3] === 'att' || pt[3] === 'attb') { sx = pt[6][0]; sy = pt[6][1]; sz = pt[6][2]; ox = pt[5][0]; oy = pt[5][1] + 0.02; oz = pt[5][2]; }
        for (let i = 0; i < cnt; i++) {
          const lx = pa[i * 3] * sx + ox, ly = pa[i * 3 + 1] * sy + oy, lz = pa[i * 3 + 2] * sz + oz, j = (o + i) * 3;
          this.pos[j] = A.x + _x.x * lx + _y.x * ly + _a.x * lz; this.pos[j + 1] = A.y + _x.y * lx + _y.y * ly + _a.y * lz; this.pos[j + 2] = A.z + _x.z * lx + _y.z * ly + _a.z * lz;
          const nx = na[i * 3], ny = na[i * 3 + 1], nz = na[i * 3 + 2]; this.nor[j] = _x.x * nx + _y.x * ny + _a.x * nz; this.nor[j + 1] = _x.y * nx + _y.y * ny + _a.y * nz; this.nor[j + 2] = _x.z * nx + _y.z * ny + _a.z * nz;
        }
        o += cnt;
      }
      const g = this.mesh.geometry; g.attributes.position.needsUpdate = true; g.attributes.normal.needsUpdate = true; g.computeBoundingSphere();
      const c = g.attributes.color; if (this.flash > 0 || this._fl) { const f = this.flash > 0, hum = this.kind === 'human'; for (let i = 0; i < c.array.length; i++) c.array[i] = f ? (hum ? (i % 3 === 0 ? Math.min(1, this.baseCol[i] + 0.35) : this.baseCol[i] * 0.55) : Math.min(1, this.baseCol[i] + 0.5)) : this.baseCol[i]; c.needsUpdate = true; this._fl = f; }
    }
    // tiro: distância do raio a cada segmento → junta atingida
    rayHit(o, d, maxT) {
      let best = null;
      for (const pt of this.parts) {
        if (pt[3] === 'att' || pt[3] === 'attb') continue;
        const A = this.p[pt[0]], Bp = this.p[pt[1]], r = pt[3] === 'tor' || pt[3] === 'pel' || pt[3] === 'torc' || pt[3] === 'pelc' || pt[3] === 'chest' ? 0.17 : pt[3] === 'head' ? 0.14 : pt[2] + 0.02;
        const t = segRay(o, d, A, Bp, r, maxT); if (t !== null && (!best || t < best.t)) best = { t, j: pt[3] === 'head' ? J.head : (A.distanceTo(o.clone().addScaledVector(d, t)) < Bp.distanceTo(o.clone().addScaledVector(d, t)) ? pt[0] : pt[1]) };
      }
      return best;
    }
    nearestJoint(pt) { let bi = 0, bd = 1e9; this.p.forEach((p, i) => { const d = p.distanceTo(pt); if (d < bd) { bd = d; bi = i; } }); return bi; }
  }
  // distância entre raio e segmento (cápsula): retorna t do raio ou null
  function segRay(o, d, A, B, r, maxT) {
    const u = _b.subVectors(B, A), w = _t.subVectors(o, A), a = 1, b = d.dot(u), c = u.dot(u) || 1e-6, dd = d.dot(w), e = u.dot(w), D = a * c - b * b;
    let sc, tc; if (D < 1e-8) { sc = 0; tc = b > c ? dd / b : e / c; } else { sc = (b * e - c * dd) / D; tc = (a * e - b * dd) / D; }
    tc = clamp(tc, 0, 1); sc = Math.max(0, (tc * b - dd)); if (sc > maxT) return null;
    const px = o.x + d.x * sc - (A.x + u.x * tc), py = o.y + d.y * sc - (A.y + u.y * tc), pz = o.z + d.z * sc - (A.z + u.z * tc);
    return px * px + py * py + pz * pz <= r * r ? Math.max(0, sc - r * 0.5) : null;
  }
  return { Dummy, J, randomLook };
})();
