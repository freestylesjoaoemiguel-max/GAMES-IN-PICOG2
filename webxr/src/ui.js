import * as THREE from 'three';

// Painéis 3D desenhados em <canvas> com botões selecionáveis por raio (laser) do controle.
// Funciona igual com controles 3DoF (só rotação) e no modo de teste 2D (mouse).

const W = 1024;

export class Panel {
  constructor(widthM = 1.5, fixedH = 1024) {
    this.widthM = widthM;
    this.canvas = document.createElement('canvas');
    this.canvas.width = W; this.canvas.height = fixedH;
    this.ctx = this.canvas.getContext('2d');
    this.texture = new THREE.CanvasTexture(this.canvas);
    this.texture.minFilter = THREE.LinearFilter; this.texture.generateMipmaps = false;
    this.mesh = new THREE.Mesh(new THREE.PlaneGeometry(1, 1),
      new THREE.MeshBasicMaterial({ map: this.texture, transparent: true, depthTest: false, side: THREE.DoubleSide }));
    this.mesh.renderOrder = 1000;
    this.mesh.frustumCulled = false;
    this.items = []; this.hover = -1; this.spec = null;
  }

  /** spec = { title, lines:[string|{t,color}], buttons:[{label, cb, active?, disabled?, wide?}], cols } */
  set(spec) {
    this.spec = spec;
    const ctx = this.ctx, cols = spec.cols || 2, H = this.canvas.height;
    const lines = (spec.lines || []).map((l) => (typeof l === 'string' ? { t: l } : l));
    const btns = spec.buttons || [];
    const lineH = 38, btnH = 84, pad = 36, gap = 16;
    // layout dos botões em linhas (um botão "wide" ocupa a linha toda)
    const bw = (W - pad * 2 - (cols - 1) * gap) / cols;
    const top = pad + 76 + lines.length * lineH + 18;
    this.items = []; let col = 0, row = 0;
    for (const b of btns) {
      if (b.wide) { if (col > 0) { row++; col = 0; } this.items.push({ x: pad, y: top + row * (btnH + gap), w: W - pad * 2, h: btnH, b }); row++; col = 0; continue; }
      this.items.push({ x: pad + col * (bw + gap), y: top + row * (btnH + gap), w: bw, h: btnH, b });
      if (++col >= cols) { col = 0; row++; }
    }
    if (col > 0) row++;
    const h = Math.min(H, Math.max(200, top + row * (btnH + gap) + pad - gap));
    this.usedH = h;
    // a textura tem tamanho fixo (WebGL2 não redimensiona textura imutável): o plano mostra só a parte de cima usada
    const g = new THREE.PlaneGeometry(this.widthM, this.widthM * h / W), uv = g.attributes.uv;
    for (let i = 0; i < uv.count; i++) uv.setY(i, 1 - h / H + uv.getY(i) * h / H);
    this.mesh.geometry.dispose(); this.mesh.geometry = g;
    ctx.clearRect(0, 0, W, H);
    ctx.fillStyle = 'rgba(8,12,24,0.93)'; round(ctx, 0, 0, W, h, 36); ctx.fill();
    ctx.strokeStyle = '#34d1ff'; ctx.lineWidth = 6; round(ctx, 3, 3, W - 6, h - 6, 34); ctx.stroke();
    ctx.fillStyle = '#34d1ff'; ctx.font = 'bold 50px system-ui, sans-serif'; ctx.textBaseline = 'middle'; ctx.textAlign = 'left';
    ctx.fillText(spec.title || '', pad, pad + 30);
    let y = pad + 76 + lineH / 2;
    ctx.font = '30px system-ui, sans-serif';
    for (const l of lines) { ctx.fillStyle = l.color || '#d6e2ff'; ctx.fillText(l.t, pad, y, W - pad * 2); y += lineH; }
    this.hover = -1;
    this._paint();
  }

  _paint() {
    const ctx = this.ctx;
    // repinta só os botões (mantém o fundo)
    this.items.forEach((it, i) => {
      const { x, y, w, h: bh, b } = it;
      ctx.fillStyle = b.disabled ? '#1a2133' : i === this.hover ? '#34d1ff' : b.active ? '#1f7a4d' : '#1d2b4a';
      round(ctx, x, y, w, bh, 20); ctx.fill();
      ctx.lineWidth = 3; ctx.strokeStyle = b.disabled ? '#2a3350' : '#5fa8ff'; round(ctx, x, y, w, bh, 20); ctx.stroke();
      ctx.fillStyle = b.disabled ? '#667' : i === this.hover ? '#04101f' : '#fff';
      ctx.font = 'bold 32px system-ui, sans-serif'; ctx.textBaseline = 'middle'; ctx.textAlign = 'center';
      ctx.fillText(b.label, x + w / 2, y + bh / 2, w - 20); ctx.textAlign = 'left';
    });
    this.texture.needsUpdate = true;
  }

  /** ponto uv (0..1, origem canto sup. esq.) -> índice do botão ou -1 */
  pick(u, v) {
    const px = u * W, py = v * this.canvas.height;
    return this.items.findIndex((it) => !it.b.disabled && px >= it.x && px <= it.x + it.w && py >= it.y && py <= it.y + it.h);
  }
  setHover(i) { if (i !== this.hover) { this.hover = i; this._paint(); } }
  click(i) { const it = this.items[i]; if (it && it.b.cb) it.b.cb(); }
}

function round(ctx, x, y, w, h, r) {
  ctx.beginPath(); ctx.moveTo(x + r, y); ctx.arcTo(x + w, y, x + w, y + h, r); ctx.arcTo(x + w, y + h, x, y + h, r);
  ctx.arcTo(x, y + h, x, y, r); ctx.arcTo(x, y, x + w, y, r); ctx.closePath();
}

/** Gerenciador de painéis: abre/fecha, posiciona na frente do usuário e trata o laser. */
export class UI {
  constructor(app) {
    this.app = app;
    this.panel = new Panel(1.5);
    this.panel.mesh.visible = false;
    app.scene.add(this.panel.mesh);
    this.pad = new Panel(0.55, 300);   // botão "☰ MENU" que aparece quando se olha para baixo
    this.pad.set({ title: '☰ MENU', buttons: [] });
    this.pad.items = [{ x: 0, y: 0, w: W, h: 300, b: { label: '☰ MENU', cb: () => app.toggleMenu() } }];
    this.pad.mesh.geometry = new THREE.PlaneGeometry(0.55, 0.55 * 300 / W);
    this._padPaint();
    this.pad.mesh.visible = false;
    app.scene.add(this.pad.mesh);
    this.builder = null;
    this.lasers = [];
    this.hits = [null, null];
    this._ray = new THREE.Raycaster();
    this._refresh = 0;
  }

  _padPaint() {
    const c = this.pad.ctx; c.clearRect(0, 0, W, 300);
    c.fillStyle = 'rgba(8,12,24,0.9)'; round(c, 0, 0, W, 300, 60); c.fill();
    c.strokeStyle = '#34d1ff'; c.lineWidth = 10; round(c, 5, 5, W - 10, 290, 56); c.stroke();
    c.fillStyle = '#fff'; c.font = 'bold 120px system-ui, sans-serif'; c.textAlign = 'center'; c.textBaseline = 'middle';
    c.fillText('☰ MENU', W / 2, 160); c.textAlign = 'left';
    this.pad.texture.needsUpdate = true;
  }

  get isOpen() { return this.panel.mesh.visible; }

  /** builder() devolve o spec; é chamado de novo ao atualizar (diagnóstico ao vivo). */
  open(builder, live = 0) {
    this.builder = builder; this.live = live; this._refresh = 0;
    this.panel.set(builder());
    this._place(); this.panel.mesh.visible = true;
    this.placePending = 3;   // reposiciona nos primeiros quadros (a pose da cabeça pode ainda não ter chegado)
  }
  _place() {
    const cam = this.app.camera, p = new THREE.Vector3(), q = new THREE.Quaternion();
    this.app.root.updateMatrixWorld(true); cam.getWorldPosition(p); cam.getWorldQuaternion(q);
    const f = new THREE.Vector3(0, 0, -1).applyQuaternion(q); f.y = 0; if (f.lengthSq() < 1e-4) f.set(0, 0, -1); f.normalize();
    this.panel.mesh.position.copy(p).addScaledVector(f, 1.5); this.panel.mesh.position.y = p.y - 0.05;
    this.panel.mesh.lookAt(p.x, this.panel.mesh.position.y, p.z);
  }
  close() { this.panel.mesh.visible = false; this.builder = null; }

  update(dt, rays) {
    const cam = this.app.camera;
    if (this.placePending > 0 && this.isOpen) { this.placePending--; this._place(); }
    // botão de menu no chão: aparece quando se olha para baixo
    const q = new THREE.Quaternion(); cam.getWorldQuaternion(q);
    const f = new THREE.Vector3(0, 0, -1).applyQuaternion(q);
    const showPad = !this.isOpen && f.y < -0.62 && this.app.inSession;
    this.pad.mesh.visible = showPad || (this.app.padAlways && !this.isOpen);
    if (this.pad.mesh.visible) {
      const p = new THREE.Vector3(); cam.getWorldPosition(p);
      const fy = new THREE.Vector3(0, 0, -1).applyQuaternion(q); fy.y = 0; if (fy.lengthSq() < 1e-4) fy.set(0, 0, -1); fy.normalize();
      this.pad.mesh.position.copy(p).addScaledVector(fy, 0.75); this.pad.mesh.position.y = p.y - 0.85;
      this.pad.mesh.rotation.order = 'YXZ';
      this.pad.mesh.rotation.set(-1.0, Math.atan2(-fy.x, -fy.z), 0);
    }
    if (this.live && this.builder) { this._refresh += dt; if (this._refresh > this.live) { this._refresh = 0; this.panel.set(this.builder()); } }
    // raios -> painel
    const out = [];
    rays.forEach((r, idx) => {
      let hit = null;
      for (const pn of [this.panel, this.pad]) {
        if (!pn.mesh.visible) continue;
        this._ray.set(r.origin, r.dir); this._ray.far = 8;
        pn.mesh.updateMatrixWorld(true);
        const h = this._ray.intersectObject(pn.mesh, false)[0];
        if (h && h.uv && (!hit || h.distance < hit.distance)) hit = { panel: pn, dist: h.distance, u: h.uv.x, v: 1 - h.uv.y };
      }
      if (hit) { hit.index = hit.panel.pick(hit.u, hit.v); }
      out[idx] = hit;
    });
    for (const pn of [this.panel, this.pad]) {
      if (!pn.mesh.visible) continue;
      const mine = out.find((h) => h && h.panel === pn);
      pn.setHover(mine ? mine.index : -1);
    }
    this.hits = out;
    return out;
  }

  /** clique com o raio idx; devolve true se acertou algum botão. */
  click(idx) {
    const h = this.hits[idx];
    if (!h || h.index < 0) return !!h;
    h.panel.click(h.index);
    if (this.builder && this.panel.mesh.visible) this.panel.set(this.builder());
    return true;
  }
}
