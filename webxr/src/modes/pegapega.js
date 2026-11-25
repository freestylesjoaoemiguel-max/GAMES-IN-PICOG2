import * as THREE from 'three';
import { ArmLoco, pushOutSphere } from '../loco.js';
import { handFromOrientation, mirrorHand } from '../armmodel.js';

// Pega-pega estilo "Gorilla Tag": locomoção com os braços (empurre chão/paredes/troncos com as mãos), bots que correm e
// pegam. Controle 3DoF (Pico G2): a posição da mão vem do modelo de braço; com um só controle a outra mão espelha.

function rng(seed) { let a = seed >>> 0; return () => { a |= 0; a = (a + 0x6d2b79f5) | 0; let t = Math.imul(a ^ (a >>> 15), 1 | a); t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t; return ((t ^ (t >>> 14)) >>> 0) / 4294967296; }; }
const box = (x, y, z, w, h, d) => ({ min: [x - w / 2, y, z - d / 2], max: [x + w / 2, y + h, z + d / 2] });

export const BOT_COLORS = [0x4cc9f0, 0xf9c74f, 0x90be6d, 0xf72585, 0xb5179e];
export const STEP_UP = 3.2;

export function buildWorld(seed = 7) {
  const r = rng(seed), boxes = [], vis = [];
  const add = (b, color, kind = 'box') => { boxes.push(b); vis.push({ b, color, kind }); };
  add({ min: [-70, -3, -70], max: [70, 0, 70] }, 0x4f7d3a, 'ground');
  for (const [x, z, w, d] of [[0, -70, 142, 2], [0, 70, 142, 2], [-70, 0, 2, 142], [70, 0, 2, 142]]) add(box(x, 0, z, w, 10, d), 0x6b5b4b);
  // torre central com degraus
  for (let i = 0; i < 6; i++) add(box(0, 0, 0, 12 - i * 2, 0.9 * (i + 1), 12 - i * 2), 0x9a8c7a + i * 0x070707);
  // plataformas e blocos
  for (let i = 0; i < 16; i++) {
    const a = r() * Math.PI * 2, rad = 10 + r() * 48, w = 2 + r() * 5, h = 0.6 + r() * 3, d = 2 + r() * 5;
    add(box(Math.cos(a) * rad, 0, Math.sin(a) * rad, w, h, d), [0xc1692c, 0x8d99ae, 0xb08968, 0x7f5539][i % 4]);
  }
  // árvores: tronco + copa-plataforma
  for (let i = 0; i < 18; i++) {
    const a = r() * Math.PI * 2, rad = 14 + r() * 50, x = Math.cos(a) * rad, z = Math.sin(a) * rad, th = 4 + r() * 3;
    add(box(x, 0, z, 0.7, th, 0.7), 0x5e3b1e, 'trunk');
    add(box(x, th, z, 3.4, 0.5, 3.4), 0x2d6a2d, 'canopy');
  }
  // barras horizontais ("monkey bars") baixas
  for (let i = 0; i < 6; i++) add(box(-20 + i * 2.2, 2.2, -12, 0.25, 0.25, 6), 0xdddddd);
  return { boxes, vis };
}

/** altura do chão sob (x,z) para um bot em altura y: topo mais alto das caixas alcançáveis (degrau máx. STEP_UP). */
export function groundAt(boxes, x, z, y, r = 0.3) {
  let best = -1e9;
  for (const b of boxes) {
    if (x < b.min[0] - r || x > b.max[0] + r || z < b.min[2] - r || z > b.max[2] + r) continue;
    if (b.max[1] <= y + STEP_UP && b.max[1] > best) best = b.max[1];
  }
  return best === -1e9 ? 0 : best;
}
/** bloqueado se alguma caixa mais alta que o degrau interceptar o círculo do bot. */
export function blocked(boxes, x, z, y, r = 0.35) {
  for (const b of boxes) {
    if (b.max[1] <= y + STEP_UP || b.min[1] > y + 1.8) continue;
    const cx = Math.max(b.min[0], Math.min(x, b.max[0])), cz = Math.max(b.min[2], Math.min(z, b.max[2]));
    if ((x - cx) ** 2 + (z - cz) ** 2 < r * r) return true;
  }
  return false;
}

export class PegaPegaMode {
  constructor(app) {
    this.app = app; this.group = new THREE.Group(); this.pausable = true;
    this.world = null; this.loco = null; this.bots = []; this.sticky = false;
    this.armHand = [new THREE.Mesh(new THREE.SphereGeometry(0.07, 12, 8), new THREE.MeshLambertMaterial({ color: 0xffd166 })), new THREE.Mesh(new THREE.SphereGeometry(0.07, 12, 8), new THREE.MeshLambertMaterial({ color: 0xffd166 }))];
    this.armCyl = [0, 1].map(() => { const m = new THREE.Mesh(new THREE.CylinderGeometry(0.035, 0.05, 1, 8), new THREE.MeshLambertMaterial({ color: 0x3a2a1a })); return m; });
    this.hudCanvas = document.createElement('canvas'); this.hudCanvas.width = 512; this.hudCanvas.height = 128;
    this.hudTex = new THREE.CanvasTexture(this.hudCanvas);
    this.hud = new THREE.Mesh(new THREE.PlaneGeometry(0.5, 0.125), new THREE.MeshBasicMaterial({ map: this.hudTex, transparent: true, depthTest: false }));
    this.hud.renderOrder = 900; this.hud.position.set(0, -0.3, -0.9);
  }

  async enter() {
    const app = this.app;
    app.scene.add(this.group);
    app.scene.background = new THREE.Color(0x8fd3ff); app.scene.fog = new THREE.Fog(0x8fd3ff, 40, 140);
    this.group.add(new THREE.HemisphereLight(0xffffff, 0x8a9a7a, 2.7));
    const sun = new THREE.DirectionalLight(0xfff2d0, 2.4); sun.position.set(30, 60, 20); this.group.add(sun);
    this.world = buildWorld();
    for (const v of this.world.vis) {
      const b = v.b, w = b.max[0] - b.min[0], h = b.max[1] - b.min[1], d = b.max[2] - b.min[2];
      const m = new THREE.Mesh(new THREE.BoxGeometry(w, h, d), new THREE.MeshLambertMaterial({ color: v.color }));
      m.position.set((b.min[0] + b.max[0]) / 2, (b.min[1] + b.max[1]) / 2, (b.min[2] + b.max[2]) / 2); this.group.add(m);
    }
    this.loco = new ArmLoco(this.world.boxes);
    this.loco.teleport([0, 7, 14]);
    this.makeBots();
    for (let i = 0; i < 2; i++) { this.group.add(this.armHand[i]); this.group.add(this.armCyl[i]); }
    app.camera.add(this.hud);
    if (!app.camera.parent) app.offset.add(app.camera);
    this.score = 0; this.isIt = false; this.immune = 3; this.tagCount = 0; this.time = 0; this.msg = ''; this.msgT = 0;
    this.lastHand = [[0, 1, 0], [0, 1, 0]]; this.firstFrame = true;
    app.padAlways = !app.inSession;
    app.laserAlways = false;
    app.ui.open(() => ({ title: 'PEGA-PEGA', lines: ['Balance os braços para empurrar chão, paredes e árvores.', 'Solte para lançar o corpo. Fuja do bot vermelho!', 'Com 1 controle, a outra mão espelha a primeira.'], buttons: [{ label: 'JOGAR', cb: () => app.ui.close(), wide: true }, { label: 'Menu principal', cb: () => app.openMenu(), wide: true }] }));
  }

  exit() {
    const app = this.app;
    app.camera.remove(this.hud); app.scene.remove(this.group); app.padAlways = false;
    this.group.traverse((o) => { if (o.geometry) o.geometry.dispose(); if (o.material && o.material.dispose) o.material.dispose(); });
    while (this.group.children.length) this.group.remove(this.group.children[0]);
    this.bots = [];
  }

  makeBots() {
    this.bots = [];
    const r = rng(11);
    for (let i = 0; i < 5; i++) {
      const g = new THREE.Group(), col = BOT_COLORS[i];
      const mat = new THREE.MeshLambertMaterial({ color: col });
      const body = new THREE.Mesh(new THREE.BoxGeometry(0.55, 0.6, 0.32), mat); body.position.y = 0.85;
      const head = new THREE.Mesh(new THREE.SphereGeometry(0.2, 12, 10), mat); head.position.y = 1.32;
      const armL = new THREE.Mesh(new THREE.BoxGeometry(0.14, 0.7, 0.14), mat); armL.position.set(-0.38, 0.85, 0);
      const armR = armL.clone(); armR.position.x = 0.38;
      const legs = new THREE.Mesh(new THREE.BoxGeometry(0.4, 0.55, 0.25), mat); legs.position.y = 0.28;
      g.add(body, head, armL, armR, legs); this.group.add(g);
      const a = r() * Math.PI * 2;
      this.bots.push({ g, mat, color: col, x: Math.cos(a) * 30, z: Math.sin(a) * 30, y: 0, vy: 0, it: false, frozen: 0, dir: a, wander: 0 });
    }
    this.bots[0].it = true;
  }

  setMsg(t) { this.msg = t; this.msgT = 3; }

  /** Posição local (relativa ao chão do rig) da cabeça e das duas mãos neste frame. */
  readBody(fi) {
    const app = this.app, cam = app.camera;
    const head = [cam.position.x, cam.position.y + app.eyeOffset, cam.position.z];
    const hq = cam.quaternion.toArray();
    const hands = [null, null];
    if (fi.xr) {
      const conn = [0, 1].filter((i) => fi.rays[i]);
      for (const i of conn) {
        const r = fi.rays[i], h = r.hand;
        // posição do controle no espaço do rig
        const c = app.controllers[i];
        const p = [c.position.x, c.position.y + app.eyeOffset, c.position.z];
        const use3dof = app.settings.forceArm || h.emulated || !h.hasPosition;
        let side = h.handedness === 'left' ? -1 : 1;
        if (conn.length === 2 && h.handedness === 'none') side = i === 0 ? -1 : 1;
        const pos = use3dof ? handFromOrientation(head, hq, c.quaternion.toArray(), side, { reach: app.settings.armReach }) : p;
        hands[side < 0 ? 0 : 1] = pos;
      }
      if (hands[0] && !hands[1]) hands[1] = mirrorHand(head, hq, hands[0]);
      else if (hands[1] && !hands[0]) hands[0] = mirrorHand(head, hq, hands[1]);
    }
    if (!hands[0]) {
      // modo teste 2D / sem controle: braços "pendurados" com leve balanço pela mira
      const q = cam.quaternion.toArray(); const c = [0, 0, 0, 1];
      const lift = 0; void q; void c; void lift;
      const base = handFromOrientation(head, hq, [-0.38, 0, 0, 0.925], 1, { reach: app.settings.armReach });
      hands[1] = base; hands[0] = mirrorHand(head, hq, base);
    }
    return { head, hq, hands };
  }

  update(fi) {
    const app = this.app, dt = fi.dt;
    const body = this.readBody(fi);
    // locomoção
    if (fi.xr) {
      const res = this.loco.update(dt, body.head, body.hands);
      app.root.position.set(this.loco.pos[0], this.loco.pos[1], this.loco.pos[2]);
      this.res = res;
      for (let i = 0; i < 2; i++) if (res.touching[i] && !this.touchPrev?.[i]) { app.sound.thud(); app._pulse(i === 0 ? 0 : 1); }
      this.touchPrev = res.touching;
    } else {
      // teste 2D: caminhada simples com WASD sobre o mesmo mundo
      const k = app.keys, f = new THREE.Vector3(-Math.sin(app.deskYaw), 0, -Math.cos(app.deskYaw)), rr = new THREE.Vector3(f.z * -1, 0, f.x);
      const sp = 6 * dt;
      this.loco.pos[0] += f.x * sp * ((k.has('KeyW') ? 1 : 0) - (k.has('KeyS') ? 1 : 0)) + rr.x * sp * ((k.has('KeyD') ? 1 : 0) - (k.has('KeyA') ? 1 : 0));
      this.loco.pos[2] += f.z * sp * ((k.has('KeyW') ? 1 : 0) - (k.has('KeyS') ? 1 : 0)) + rr.z * sp * ((k.has('KeyD') ? 1 : 0) - (k.has('KeyA') ? 1 : 0));
      this.loco.update(dt, body.head, body.hands);
      app.root.position.set(this.loco.pos[0], this.loco.pos[1], this.loco.pos[2]);
    }
    const P = this.loco.pos;
    const hw = [0, 1].map((i) => [P[0] + body.hands[i][0], P[1] + body.hands[i][1], P[2] + body.hands[i][2]]);
    const headW = [P[0] + body.head[0], P[1] + body.head[1], P[2] + body.head[2]];
    // mãos e braços visíveis
    for (let i = 0; i < 2; i++) {
      this.armHand[i].position.set(hw[i][0], hw[i][1], hw[i][2]);
      const sh = new THREE.Vector3(headW[0] + (i === 0 ? -0.2 : 0.2), headW[1] - 0.25, headW[2]), hv = new THREE.Vector3(...hw[i]);
      const mid = sh.clone().add(hv).multiplyScalar(0.5), dir = hv.clone().sub(sh), L = dir.length();
      this.armCyl[i].position.copy(mid); this.armCyl[i].scale.set(1, Math.max(0.01, L), 1);
      this.armCyl[i].quaternion.setFromUnitVectors(new THREE.Vector3(0, 1, 0), dir.normalize());
    }
    this.updateBots(dt, headW, hw);
    this.time += dt; if (!this.isIt) this.score += dt;
    if (this.msgT > 0) this.msgT -= dt;
    this.drawHud();
    // se cair do mapa
    if (P[1] < -20) { this.loco.teleport([0, 7, 14]); }
  }

  updateBots(dt, headW, hw) {
    const B = this.world.boxes;
    const itBot = this.bots.find((b) => b.it);
    if (this.immune > 0) this.immune -= dt;
    for (const b of this.bots) {
      if (b.frozen > 0) b.frozen -= dt;
      let tx, tz, sp;
      if (b.frozen > 0) { sp = 0; tx = b.x; tz = b.z; }
      else if (b.it) { // persegue quem estiver mais perto (jogador ou outro bot)
        let best = this.isIt ? 1e9 : (headW[0] - b.x) ** 2 + (headW[2] - b.z) ** 2; tx = headW[0]; tz = headW[2];
        if (this.isIt) best = 1e9;
        for (const o of this.bots) if (o !== b && !o.it) { const d = (o.x - b.x) ** 2 + (o.z - b.z) ** 2; if (d < best) { best = d; tx = o.x; tz = o.z; } }
        sp = 4.3;
      } else { // foge do pegador (ou do jogador, se ele é o pegador)
        const fx = this.isIt ? headW[0] : itBot ? itBot.x : b.x, fz = this.isIt ? headW[2] : itBot ? itBot.z : b.z;
        const dx = b.x - fx, dz = b.z - fz, d = Math.hypot(dx, dz) || 1;
        b.wander -= dt; if (b.wander <= 0) { b.wander = 0.8 + Math.random() * 1.5; b.dir = (Math.random() - 0.5) * 1.4; }
        const ang = Math.atan2(dz, dx) + b.dir * (d < 12 ? 0.4 : 1);
        tx = b.x + Math.cos(ang) * 10; tz = b.z + Math.sin(ang) * 10; sp = d < 20 ? 3.9 : 1.5;
      }
      const dx = tx - b.x, dz = tz - b.z, dl = Math.hypot(dx, dz) || 1;
      let mx = (dx / dl) * sp * dt, mz = (dz / dl) * sp * dt;
      if (sp > 0) {
        if (!blocked(B, b.x + mx, b.z + mz, b.y)) { b.x += mx; b.z += mz; }
        else if (!blocked(B, b.x + mx, b.z, b.y)) b.x += mx;
        else if (!blocked(B, b.x, b.z + mz, b.y)) b.z += mz;
        else { b.dir = Math.random() * 6; b.wander = 0.4; }
      }
      b.x = Math.max(-68, Math.min(68, b.x)); b.z = Math.max(-68, Math.min(68, b.z));
      const gy = groundAt(B, b.x, b.z, b.y);
      if (gy > b.y) b.y = Math.min(gy, b.y + 7 * dt); else b.y = Math.max(gy, b.y - 12 * dt);
      b.g.position.set(b.x, b.y, b.z); b.g.rotation.y = Math.atan2(dx, dz);
      b.mat.color.setHex(b.frozen > 0 ? 0x66ccff : b.it ? 0xe5152b : b.color);
      b.mat.emissive.setHex(b.it ? 0x550000 : 0x000000);
    }
    // marcação
    const near = (px, py, pz, b, r) => Math.hypot(px - b.x, pz - b.z) < r && py > b.y - 0.4 && py < b.y + 1.9;
    if (!this.isIt) {
      if (itBot && itBot.frozen <= 0 && this.immune <= 0) {
        const hit = near(headW[0], headW[1], headW[2], itBot, 0.65) || hw.some((h) => near(h[0], h[1], h[2], itBot, 0.5));
        if (hit) { this.isIt = true; itBot.it = false; itBot.frozen = 3; this.immune = 3; this.setMsg('VOCÊ FOI PEGO! Agora pegue alguém!'); this.app.sound.tag(); this.app._pulse(1); }
      }
    } else if (this.immune <= 0 || true) {
      for (const b of this.bots) {
        if (b.frozen > 0) continue;
        const hit = near(headW[0], headW[1], headW[2], b, 0.7) || hw.some((h) => near(h[0], h[1], h[2], b, 0.55));
        if (hit && this.immune <= 0) { this.isIt = false; b.it = true; b.frozen = 0; this.immune = 3; this.tagCount++; this.setMsg('Você pegou um bot! Corra!'); this.app.sound.tag(); break; }
      }
    }
    // se ninguém é pegador (jogador não é e nenhum bot é): sorteia
    if (!this.isIt && !this.bots.some((b) => b.it)) this.bots[Math.floor(Math.random() * this.bots.length)].it = true;
  }

  drawHud() {
    const c = this.hudCanvas.getContext('2d'); c.clearRect(0, 0, 512, 128);
    c.fillStyle = this.isIt ? 'rgba(160,10,20,0.85)' : 'rgba(10,30,60,0.8)'; c.fillRect(0, 0, 512, 128);
    c.fillStyle = '#fff'; c.font = 'bold 34px system-ui, sans-serif'; c.textBaseline = 'middle';
    c.fillText(this.isIt ? 'VOCÊ É O PEGADOR!' : 'FUJA DO VERMELHO!', 16, 36);
    c.font = '28px system-ui, sans-serif';
    c.fillText(`Tempo livre: ${this.score.toFixed(0)}s   Pegadas: ${this.tagCount}`, 16, 80);
    if (this.msgT > 0) { c.fillStyle = '#ffd166'; c.font = 'bold 22px system-ui, sans-serif'; c.fillText(this.msg, 16, 112); }
    this.hudTex.needsUpdate = true;
  }
}
