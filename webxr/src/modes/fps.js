import * as THREE from 'three';
import { pushOutSphere } from '../loco.js';

// OPERATION ZERO (versão WebXR): arena com ondas de robôs. Mire com o controle (raio), gatilho atira,
// touchpad/analógico anda, botão extra recarrega. Funciona com 1 controle 3DoF.

const box = (x, y, z, w, h, d) => ({ min: [x - w / 2, y, z - d / 2], max: [x + w / 2, y + h, z + d / 2] });

/** interseção raio x AABB (slab). Devolve distância ou -1. */
export function rayBox(o, d, b, max = 1e9) {
  let t0 = 0, t1 = max;
  for (let a = 0; a < 3; a++) {
    const inv = 1 / (d[a] === 0 ? 1e-12 : d[a]);
    let ta = (b.min[a] - o[a]) * inv, tb = (b.max[a] - o[a]) * inv;
    if (ta > tb) { const t = ta; ta = tb; tb = t; }
    t0 = Math.max(t0, ta); t1 = Math.min(t1, tb);
    if (t0 > t1) return -1;
  }
  return t0;
}
/** raio x esfera: distância ou -1 */
export function raySphere(o, d, c, r) {
  const ox = o[0] - c[0], oy = o[1] - c[1], oz = o[2] - c[2];
  const bq = ox * d[0] + oy * d[1] + oz * d[2], cq = ox * ox + oy * oy + oz * oz - r * r;
  const disc = bq * bq - cq; if (disc < 0) return -1;
  const t = -bq - Math.sqrt(disc); return t >= 0 ? t : (-bq + Math.sqrt(disc) >= 0 ? 0 : -1);
}

export function waveSize(n) { return Math.min(14, 3 + n * 2); }

export class FpsMode {
  constructor(app) {
    this.app = app; this.group = new THREE.Group(); this.pausable = true;
    this.boxes = []; this.enemies = []; this.shots = []; this.tracers = [];
    this.hudCanvas = document.createElement('canvas'); this.hudCanvas.width = 768; this.hudCanvas.height = 96;
    this.hudTex = new THREE.CanvasTexture(this.hudCanvas);
    this.hud = new THREE.Mesh(new THREE.PlaneGeometry(0.62, 0.0775), new THREE.MeshBasicMaterial({ map: this.hudTex, transparent: true, depthTest: false }));
    this.hud.renderOrder = 900; this.hud.position.set(0, -0.3, -0.9);
    this.flash = new THREE.Mesh(new THREE.PlaneGeometry(4, 4), new THREE.MeshBasicMaterial({ color: 0xff0000, transparent: true, opacity: 0, depthTest: false }));
    this.flash.renderOrder = 899; this.flash.position.set(0, 0, -0.3);
    this.gun = new THREE.Group();
    const gm = new THREE.MeshLambertMaterial({ color: 0x2b2f3a });
    const body = new THREE.Mesh(new THREE.BoxGeometry(0.05, 0.08, 0.22), gm); body.position.set(0, 0, -0.08);
    const barrel = new THREE.Mesh(new THREE.BoxGeometry(0.025, 0.025, 0.18), gm); barrel.position.set(0, 0.025, -0.25);
    const grip = new THREE.Mesh(new THREE.BoxGeometry(0.04, 0.1, 0.05), gm); grip.position.set(0, -0.08, 0);
    this.gun.add(body, barrel, grip);
    this.muzzle = new THREE.PointLight(0xffc766, 0, 8, 2); this.muzzle.position.set(0, 0.03, -0.36); this.gun.add(this.muzzle);
  }

  async enter() {
    const app = this.app;
    app.scene.add(this.group);
    app.scene.background = new THREE.Color(0x0b0f1a); app.scene.fog = new THREE.Fog(0x0b0f1a, 18, 55);
    this.group.add(new THREE.HemisphereLight(0xbcd0ff, 0x4a5066, 2.6));
    const sun = new THREE.DirectionalLight(0xffe3b0, 1.8); sun.position.set(8, 20, 6); this.group.add(sun);
    this.boxes = [{ min: [-60, -3, -60], max: [60, 0, 60] }];
    const vis = [{ b: this.boxes[0], c: 0x333b4d }];
    const walls = [[0, -20, 40, 1], [0, 20, 40, 1], [-20, 0, 1, 40], [20, 0, 1, 40]];
    for (const [x, z, w, d] of walls) { const b = box(x, 0, z, w, 7, d); this.boxes.push(b); vis.push({ b, c: 0x4a5670 }); }
    const crates = [[-6, -4, 2, 1.2], [6, 5, 2.4, 1.2], [0, 0, 1.6, 2.4], [-10, 8, 3, 1.4], [10, -9, 3, 1.4], [-13, -12, 2, 2.2], [13, 12, 2, 2.2], [3, -12, 4, 1.0], [-4, 12, 4, 1.0]];
    for (const [x, z, s, h] of crates) { const b = box(x, 0, z, s, h, s); this.boxes.push(b); vis.push({ b, c: 0x6b4f2a }); }
    for (const [x, z] of [[-14, 0], [14, 0], [0, 14], [0, -14]]) { const b = box(x, 0, z, 1.2, 6, 1.2); this.boxes.push(b); vis.push({ b, c: 0x7a8aa8 }); }
    for (const v of vis) {
      const b = v.b, w = b.max[0] - b.min[0], h = b.max[1] - b.min[1], d = b.max[2] - b.min[2];
      const m = new THREE.Mesh(new THREE.BoxGeometry(w, h, d), new THREE.MeshLambertMaterial({ color: v.c }));
      m.position.set((b.min[0] + b.max[0]) / 2, (b.min[1] + b.max[1]) / 2, (b.min[2] + b.max[2]) / 2); this.group.add(m);
    }
    app.camera.add(this.hud); app.camera.add(this.flash);
    app.laserAlways = true;
    this.group.add(this.tracerGroup = new THREE.Group());
    this.attachGun(); this.reset();
    app.ui.open(() => ({ title: 'OPERATION ZERO', lines: ['Mire com o controle e use o gatilho para atirar.', 'Touchpad/analógico: andar · botão extra: recarregar.', 'Sobreviva às ondas de robôs.'], buttons: [{ label: 'COMEÇAR', cb: () => app.ui.close(), wide: true }, { label: 'Menu principal', cb: () => app.openMenu(), wide: true }] }));
  }

  attachGun() {
    const app = this.app;
    this.gunHolder = null;
    this.gunParent = (i) => (i >= 0 && app.controllers[i]) || app.camera;
  }

  exit() {
    const app = this.app;
    app.camera.remove(this.hud); app.camera.remove(this.flash); this.gun.removeFromParent(); app.scene.remove(this.group); app.laserAlways = false;
    this.group.traverse((o) => { if (o.geometry) o.geometry.dispose(); if (o.material && o.material.dispose) o.material.dispose(); });
    while (this.group.children.length) this.group.remove(this.group.children[0]);
    this.enemies = []; this.shots = [];
  }

  reset() {
    for (const e of this.enemies) this.group.remove(e.g);
    for (const s of this.shots) this.group.remove(s.m);
    this.enemies = []; this.shots = []; this.wave = 0; this.score = 0; this.hp = 100; this.ammo = 30; this.mag = 30; this.reload = 0; this.cool = 0; this.dead = false; this.sinceHit = 99; this.nextWave = 1;
    this.app.root.position.set(0, 0, 17.5); this.kills = 0; this.waveBanner = 0;
  }

  spawnWave() {
    this.wave++; const n = waveSize(this.wave); this.waveBanner = 2.5;
    for (let i = 0; i < n; i++) {
      const a = (i / n) * Math.PI * 2 + Math.random(), r = 16;
      const g = new THREE.Group(), tough = this.wave >= 3 && i % 4 === 0;
      const mat = new THREE.MeshLambertMaterial({ color: tough ? 0xff7a1a : 0xd62839, emissive: 0x330000 });
      const torso = new THREE.Mesh(new THREE.BoxGeometry(0.6, 0.8, 0.4), mat); torso.position.y = 1.0;
      const head = new THREE.Mesh(new THREE.SphereGeometry(0.22, 10, 8), new THREE.MeshLambertMaterial({ color: 0x222831, emissive: 0x00ffee, emissiveIntensity: 0.4 })); head.position.y = 1.65;
      const legs = new THREE.Mesh(new THREE.BoxGeometry(0.5, 0.6, 0.3), mat); legs.position.y = 0.3;
      g.add(torso, head, legs); this.group.add(g);
      const e = { g, mat, x: Math.cos(a) * r, z: Math.sin(a) * r, hp: tough ? 90 : 45, speed: (tough ? 2.4 : 2.0) + this.wave * 0.12, fire: 1 + Math.random() * 2, hurt: 0, tough };
      g.position.set(e.x, 0, e.z); this.enemies.push(e);
    }
  }

  damage(n) {
    if (this.dead) return; this.hp -= n; this.sinceHit = 0; this.flash.material.opacity = 0.45; this.app.sound.hurt(); this.app._pulse(1);
    if (this.hp <= 0) { this.hp = 0; this.dead = true; this.gameOver(); }
  }

  gameOver() {
    const app = this.app;
    app.ui.open(() => ({ title: 'FIM DE JOGO', lines: [`Onda ${this.wave} · ${this.kills} abates · ${this.score} pontos`], buttons: [{ label: 'JOGAR DE NOVO', cb: () => { this.reset(); app.ui.close(); }, wide: true }, { label: 'Menu principal', cb: () => app.openMenu(), wide: true }] }));
  }

  update(fi) {
    const app = this.app, dt = fi.dt;
    const pi = app.primary(fi.rays);
    const ray = fi.xr ? (pi >= 0 ? fi.rays[pi] : null) : fi.rays[0];
    const hand = ray && ray.hand;
    // arma no controle (ou na câmera no modo 2D)
    const holder = fi.xr ? (pi >= 0 ? app.controllers[pi] : app.camera) : app.camera;
    if (this.gun.parent !== holder) { this.gun.removeFromParent(); holder.add(this.gun); if (holder === app.camera) this.gun.position.set(0.18, -0.2, -0.3); else this.gun.position.set(0, 0, 0); }
    // andar
    let mv = { x: 0, y: 0, mag: 0 };
    if (fi.xr && hand) mv = app.bindings.move(hand);
    else if (!fi.xr) { const k = app.keys; mv = { x: (k.has('KeyD') ? 1 : 0) - (k.has('KeyA') ? 1 : 0), y: (k.has('KeyW') ? 1 : 0) - (k.has('KeyS') ? 1 : 0), mag: 1 }; }
    if (mv.mag > 0.05 && !this.dead) {
      const q = app.settings.steer === 'head' || !ray ? app.camera.getWorldQuaternion(new THREE.Quaternion()) : ray.quat;
      const f = new THREE.Vector3(0, 0, -1).applyQuaternion(q); f.y = 0;
      if (f.lengthSq() < 1e-4) f.set(0, 0, -1).applyQuaternion(app.camera.getWorldQuaternion(new THREE.Quaternion())); f.y = 0; f.normalize();
      const r = new THREE.Vector3().crossVectors(f, new THREE.Vector3(0, 1, 0));
      const sp = app.settings.speed;
      app.root.position.addScaledVector(f, mv.y * sp * dt).addScaledVector(r, mv.x * sp * dt);
    }
    // colisão do corpo (cilindro ~ esferas)
    const P = app.root.position, head = app.camera.getWorldPosition(new THREE.Vector3());
    for (let it = 0; it < 2; it++) {
      for (const c of [[head.x, 0.4, head.z, 0.35], [head.x, 1.0, head.z, 0.35]]) {
        const pen = pushOutSphere([c[0], c[1], c[2]], c[3], this.boxes.slice(1));
        if (pen[0] || pen[2]) { P.x += pen[0]; P.z += pen[2]; head.x += pen[0]; head.z += pen[2]; }
      }
    }
    P.y = 0;
    // atirar / recarregar
    this.cool -= dt; if (this.reload > 0) { this.reload -= dt; if (this.reload <= 0) { this.mag = 30; } }
    const wantFire = fi.xr ? (hand && app.bindings.isDown(hand, 'fire')) : app.pointer.down;
    if (hand && app.bindings.justPressed(hand, 'alt') && this.reload <= 0 && this.mag < 30) this.startReload();
    if (!fi.xr && app.keys.has('KeyR') && this.reload <= 0 && this.mag < 30) this.startReload();
    if (!this.dead && wantFire && this.cool <= 0 && this.reload <= 0) {
      if (this.mag > 0) { this.shoot(ray); } else this.startReload();
    }
    // inimigos
    if (!this.dead && this.enemies.length === 0) { this.nextWave -= dt; if (this.nextWave <= 0) { this.spawnWave(); this.nextWave = 3; } }
    const hp3 = [head.x, head.y, head.z];
    for (let i = this.enemies.length - 1; i >= 0; i--) {
      const e = this.enemies[i];
      if (e.hurt > 0) { e.hurt -= dt; e.mat.emissive.setHex(0xaa2200); } else e.mat.emissive.setHex(0x330000);
      const dx = head.x - e.x, dz = head.z - e.z, d = Math.hypot(dx, dz) || 1;
      if (d > 7) { // aproxima até a distância de tiro, desviando das caixas
        let mx = (dx / d) * e.speed * dt, mz = (dz / d) * e.speed * dt;
        const test = (x, z) => pushOutSphere([x, 0.6, z], 0.4, this.boxes.slice(1));
        const p1 = test(e.x + mx, e.z + mz);
        if (!p1[0] && !p1[2]) { e.x += mx; e.z += mz; } else { // desliza lateralmente
          const sx = e.x + mz * 1.5, sz = e.z - mx * 1.5, p2 = test(sx, sz);
          if (!p2[0] && !p2[2]) { e.x += mz * 1.5 * 0.5; e.z -= mx * 1.5 * 0.5; }
        }
      }
      e.g.position.set(e.x, 0, e.z); e.g.rotation.y = Math.atan2(dx, dz);
      e.fire -= dt;
      if (e.fire <= 0 && d < 28 && !this.dead) {
        e.fire = (e.tough ? 1.3 : 2.0) + Math.random() * 1.3;
        const o = [e.x, 1.4, e.z]; let dir = [hp3[0] - o[0], hp3[1] - 0.2 - o[1], hp3[2] - o[2]]; const L = Math.hypot(...dir); dir = dir.map((v) => v / L);
        let blockedLos = false; for (const b of this.boxes.slice(1)) { const t = rayBox(o, dir, b, L); if (t >= 0 && t < L) { blockedLos = true; break; } }
        if (!blockedLos) {
          const m = new THREE.Mesh(new THREE.SphereGeometry(0.09, 8, 6), new THREE.MeshBasicMaterial({ color: 0xff4d4d })); m.position.set(...o); this.group.add(m);
          this.shots.push({ m, p: o, v: dir.map((v) => v * 9), life: 6 });
        }
      }
    }
    for (let i = this.shots.length - 1; i >= 0; i--) {
      const s = this.shots[i]; s.life -= dt; s.p = s.p.map((v, k) => v + s.v[k] * dt); s.m.position.set(...s.p);
      const hitPlayer = Math.hypot(s.p[0] - head.x, s.p[1] - head.y, s.p[2] - head.z) < 0.45 || Math.hypot(s.p[0] - head.x, s.p[2] - head.z) < 0.35 && s.p[1] < head.y && s.p[1] > 0.2;
      const hitWall = this.boxes.some((b) => s.p[0] > b.min[0] && s.p[0] < b.max[0] && s.p[1] > b.min[1] && s.p[1] < b.max[1] && s.p[2] > b.min[2] && s.p[2] < b.max[2]);
      if (hitPlayer) this.damage(8);
      if (hitPlayer || hitWall || s.life <= 0) { this.group.remove(s.m); s.m.geometry.dispose(); s.m.material.dispose(); this.shots.splice(i, 1); }
    }
    // tracers e efeitos
    for (let i = this.tracers.length - 1; i >= 0; i--) { const t = this.tracers[i]; t.life -= dt; t.l.material.opacity = Math.max(0, t.life / 0.08); if (t.life <= 0) { this.tracerGroup.remove(t.l); t.l.geometry.dispose(); t.l.material.dispose(); this.tracers.splice(i, 1); } }
    this.muzzle.intensity = Math.max(0, this.muzzle.intensity - dt * 600);
    this.flash.material.opacity = Math.max(0, this.flash.material.opacity - dt * 1.4);
    this.sinceHit += dt; if (this.sinceHit > 5 && !this.dead) this.hp = Math.min(100, this.hp + 6 * dt);
    if (this.waveBanner > 0) this.waveBanner -= dt;
    this.drawHud();
  }

  startReload() { this.reload = 1.6; this.app.sound.reload(); }

  shoot(ray) {
    const app = this.app;
    this.mag--; this.cool = 0.11; app.sound.shot(); app._pulse(app.primary(app.frameInfo ? app.frameInfo.rays : []));
    this.muzzle.intensity = 40;
    const o = ray.origin.toArray(), d = ray.dir.toArray();
    let tBest = 80, hit = null, headshot = false;
    for (const b of this.boxes) { const t = rayBox(o, d, b, tBest); if (t >= 0 && t < tBest) { tBest = t; hit = null; } }
    for (const e of this.enemies) {
      const th = raySphere(o, d, [e.x, 1.65, e.z], 0.26);
      const tb = rayBox(o, d, { min: [e.x - 0.32, 0, e.z - 0.25], max: [e.x + 0.32, 1.45, e.z + 0.25] }, tBest);
      if (th >= 0 && th < tBest) { tBest = th; hit = e; headshot = true; }
      else if (tb >= 0 && tb < tBest) { tBest = tb; hit = e; headshot = false; }
    }
    const end = new THREE.Vector3(...o).addScaledVector(ray.dir, tBest);
    const geo = new THREE.BufferGeometry().setFromPoints([new THREE.Vector3(...o).addScaledVector(ray.dir, 0.3), end]);
    const l = new THREE.Line(geo, new THREE.LineBasicMaterial({ color: 0xffe08a, transparent: true, opacity: 1 }));
    this.tracerGroup.add(l); this.tracers.push({ l, life: 0.08 });
    if (hit) {
      hit.hp -= headshot ? 45 : 18; hit.hurt = 0.12; app.sound.hit();
      if (hit.hp <= 0) {
        this.group.remove(hit.g); this.enemies.splice(this.enemies.indexOf(hit), 1); this.kills++; this.score += headshot ? 150 : 100; app.sound.kill();
      } else this.score += 10;
    }
  }

  drawHud() {
    const c = this.hudCanvas.getContext('2d'); c.clearRect(0, 0, 768, 96);
    c.fillStyle = 'rgba(8,12,24,0.8)'; c.fillRect(0, 0, 768, 96);
    c.fillStyle = this.hp > 35 ? '#6ee7a0' : '#ff5c5c'; c.fillRect(16, 60, 220 * this.hp / 100, 22);
    c.strokeStyle = '#fff'; c.strokeRect(16, 60, 220, 22);
    c.fillStyle = '#fff'; c.font = 'bold 30px system-ui, sans-serif'; c.textBaseline = 'middle';
    c.fillText(`❤ ${Math.ceil(this.hp)}`, 16, 28);
    c.fillText(this.reload > 0 ? 'RECARREGANDO…' : `Munição ${this.mag}/30`, 150, 28);
    c.fillText(`Onda ${this.wave}  Inimigos ${this.enemies.length}`, 420, 28);
    c.font = '26px system-ui, sans-serif'; c.fillText(`${this.score} pts`, 420, 70);
    if (this.waveBanner > 0) { c.fillStyle = '#ffd166'; c.fillText(`ONDA ${this.wave}!`, 600, 70); }
    this.hudTex.needsUpdate = true;
  }
}
