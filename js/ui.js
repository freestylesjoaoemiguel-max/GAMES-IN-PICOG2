'use strict';
/* UI 3D: painéis curvos flutuantes (estilo aba de navegador), teclado virtual, HUD e telas */
const FONT = 'Roboto,"Segoe UI",system-ui,-apple-system,"Noto Sans","Noto Color Emoji",sans-serif';
const LIGHT = { bg: '#ffffff', chrome: '#dee1e6', surface: '#f1f3f4', text: '#202124', sub: '#5f6368', accent: '#1a73e8', line: '#dadce0', card: '#ffffff', hover: '#e8eaed', key: '#ffffff', keyBg: '#d2d5da' };
const DARK = { bg: '#202124', chrome: '#111214', surface: '#303134', text: '#e8eaed', sub: '#9aa0a6', accent: '#8ab4f8', line: '#3c4043', card: '#2b2c2f', hover: '#3c4043', key: '#3c4043', keyBg: '#17181a' };
const T = () => (Game.settings.dark ? DARK : LIGHT);
const GCOL = ['#4285f4', '#ea4335', '#fbbc05', '#4285f4', '#34a853', '#ea4335', '#fbbc05', '#4285f4', '#34a853'];

function rr(c, x, y, w, h, r) {
  r = Math.min(r, w / 2, h / 2); c.beginPath(); c.moveTo(x + r, y); c.arcTo(x + w, y, x + w, y + h, r); c.arcTo(x + w, y + h, x, y + h, r); c.arcTo(x, y + h, x, y, r); c.arcTo(x, y, x + w, y, r); c.closePath();
}
function txt(c, s, x, y, o) {
  o = o || {}; c.font = (o.weight || 400) + ' ' + (o.size || 24) + 'px ' + FONT; c.fillStyle = o.color || '#000';
  c.textAlign = o.align || 'left'; c.textBaseline = o.base || 'middle'; c.fillText(s, x, y);
}
function wrapLines(c, s, maxW, font) {
  c.font = font; const words = s.split(' '), lines = []; let cur = '';
  for (const w of words) { const t = cur ? cur + ' ' + w : w; if (c.measureText(t).width > maxW && cur) { lines.push(cur); cur = w; } else cur = t; }
  if (cur) lines.push(cur); return lines;
}
const ease = x => 1 - Math.pow(1 - x, 3);
const easeBack = x => { const c1 = 1.4, c3 = c1 + 1; return 1 + c3 * Math.pow(x - 1, 3) + c1 * Math.pow(x - 1, 2); };

function panelGeo(w, h, R) {
  const span = w / R, N = Math.max(2, Math.ceil(span * 28)), pos = [], uv = [], idx = [];
  for (let i = 0; i <= N; i++) {
    const a = (i / N - 0.5) * span, x = R * Math.sin(a), z = -R * Math.cos(a);
    pos.push(x, h / 2, z, x, -h / 2, z); uv.push(i / N, 1, i / N, 0);
    if (i < N) { const k = i * 2; idx.push(k, k + 1, k + 2, k + 1, k + 3, k + 2); }
  }
  const g = new THREE.BufferGeometry();
  g.setAttribute('position', new THREE.Float32BufferAttribute(pos, 3)); g.setAttribute('uv', new THREE.Float32BufferAttribute(uv, 2)); g.setIndex(idx);
  g.computeBoundingSphere(); return g;
}

class Panel {
  constructor(o) {
    Object.assign(this, { w: o.w, h: o.h, pw: o.pw, ph: o.ph, R: o.R, name: o.name, drawFn: o.draw, order: o.order || 10, bob: o.bob === undefined ? 0.012 : o.bob, fps: o.fps || 30 });
    this.canvas = document.createElement('canvas'); this.canvas.width = o.pw; this.canvas.height = o.ph; this.ctx = this.canvas.getContext('2d');
    this.tex = new THREE.CanvasTexture(this.canvas); this.tex.colorSpace = THREE.SRGBColorSpace; this.tex.generateMipmaps = false; this.tex.minFilter = THREE.LinearFilter;
    this.mat = new THREE.MeshBasicMaterial({ map: this.tex, transparent: true, side: THREE.DoubleSide, depthWrite: false, toneMapped: false, fog: false });
    this.mesh = new THREE.Mesh(panelGeo(o.w, o.h, o.R), this.mat); this.mesh.renderOrder = this.order; this.mesh.userData.panel = this;
    this.root = new THREE.Group(); this.root.add(this.mesh); this.root.visible = false;
    this.open = false; this.a = 0; this.dirty = true; this.hoverId = null; this.btns = []; this.next = []; this.hvm = {}; this.smv = {}; this.ripples = [];
    this.ptr = null; this.lastDraw = 0; this.phase = Math.random() * 6; this.pressId = null; this.animating = false;
  }
  place(pos, yaw, pitch) { this.root.position.copy(pos); this.root.rotation.set(pitch || 0, yaw || 0, 0, 'YXZ'); }
  show(v) { if (v && !this.open) { this.a = Math.min(this.a, 0.02); this.dirty = true; } this.open = v; }
  hv(id) { return this.hvm[id] || 0; }
  sm(id, target, k) { const v = this.smv[id] === undefined ? target : this.smv[id]; const n = v + (target - v) * Math.min(1, (k || 0.35)); this.smv[id] = n; if (Math.abs(n - target) > 0.01) this.animating = true; return n; }
  hit(id, x, y, w, h, fn, o) { this.next.push({ id, x, y, w, h, fn, o: o || {} }); return this.hv(id); }
  setPointer(px, py) {
    this.ptr = px === null ? null : { x: px, y: py };
    let id = null;
    if (this.ptr) for (let i = this.btns.length - 1; i >= 0; i--) { const b = this.btns[i]; if (px >= b.x && px <= b.x + b.w && py >= b.y && py <= b.y + b.h) { id = b.id; break; } }
    if (id !== this.hoverId) { this.hoverId = id; if (id) Sfx.play('hover'); this.dirty = true; }
    return id;
  }
  pressAt() { // retorna o botão sob o ponteiro
    if (!this.ptr) return null;
    for (let i = this.btns.length - 1; i >= 0; i--) { const b = this.btns[i]; if (this.ptr.x >= b.x && this.ptr.x <= b.x + b.w && this.ptr.y >= b.y && this.ptr.y <= b.y + b.h) return b; }
    return null;
  }
  click() {
    const b = this.pressAt(); if (!b) return false;
    this.ripples.push({ x: this.ptr.x, y: this.ptr.y, t: 0, b }); this.pressId = b.id; this.dirty = true;
    if (!b.o.silent) Sfx.play(b.o.sfx || 'click');
    if (b.fn) b.fn(b);
    return true;
  }
  update(dt, t) {
    const target = this.open ? 1 : 0;
    this.a += (target - this.a) * Math.min(1, dt * 9);
    if (!this.open && this.a < 0.02) { this.a = 0; this.root.visible = false; return; }
    this.root.visible = true;
    const s = 0.82 + 0.18 * easeBack(clamp(this.a, 0, 1)); this.mesh.scale.set(s, s, 1);
    this.mat.opacity = clamp(this.a * 1.4, 0, 1);
    this.mesh.position.y = Math.sin(t * 1.2 + this.phase) * this.bob;
    let anim = this.animating || this.ripples.length > 0 || this.a < 0.98;
    for (const id in this.hvm) { const tg = id === this.hoverId ? 1 : 0, v = this.hvm[id], n = v + (tg - v) * Math.min(1, dt * 14); this.hvm[id] = n; if (Math.abs(n - tg) > 0.01) anim = true; }
    if (this.hoverId && !(this.hoverId in this.hvm)) { this.hvm[this.hoverId] = 0.01; anim = true; }
    for (const r of this.ripples) r.t += dt * 2.2; this.ripples = this.ripples.filter(r => r.t < 1);
    if (this.live) anim = true;
    if ((this.dirty || anim) && t - this.lastDraw >= 1 / this.fps) this.redraw(t);
  }
  redraw(t) {
    this.lastDraw = t; this.dirty = false; this.animating = false; this.next = [];
    const c = this.ctx; c.setTransform(1, 0, 0, 1, 0, 0); c.clearRect(0, 0, this.pw, this.ph);
    this.drawFn(this, c, t);
    this.btns = this.next; this.tex.needsUpdate = true;
    if (this.ptr) { const id = this.setPointer(this.ptr.x, this.ptr.y); void id; }
  }
  drawRipples(c) {
    for (const r of this.ripples) {
      const b = r.b; c.save(); rr(c, b.x, b.y, b.w, b.h, b.o.r || 16); c.clip();
      c.globalAlpha = (1 - r.t) * 0.35; c.fillStyle = '#4285f4'; c.beginPath(); c.arc(r.x, r.y, ease(r.t) * Math.max(b.w, b.h), 0, TAU); c.fill(); c.restore();
    }
  }
}

/* ---------- Componentes ---------- */
function pill(p, c, id, x, y, w, h, label, fn, o) {
  o = o || {}; const t = T(), hv = p.hit(id, x, y, w, h, fn, { r: h / 2, sfx: o.sfx });
  c.save(); const sc = 1 + hv * 0.04; c.translate(x + w / 2, y + h / 2); c.scale(sc, sc); c.translate(-w / 2, -h / 2);
  c.shadowColor = 'rgba(0,0,0,' + (0.12 + hv * 0.18) + ')'; c.shadowBlur = 10 + hv * 12; c.shadowOffsetY = 3 + hv * 3;
  rr(c, 0, 0, w, h, h / 2); c.fillStyle = o.fill ? o.fill : (hv > 0.02 ? t.hover : t.surface); c.fill(); c.shadowColor = 'transparent';
  if (o.stroke || !o.fill) { c.strokeStyle = hv > 0.02 ? t.accent : t.line; c.lineWidth = 2; c.stroke(); }
  txt(c, label, w / 2, h / 2 + 1, { size: o.size || 24, weight: o.weight || 500, color: o.color || t.text, align: 'center' });
  c.restore();
}
function iconBtn(p, c, id, cx, cy, r, glyph, fn, color) {
  const t = T(), hv = p.hit(id, cx - r, cy - r, r * 2, r * 2, fn, { r });
  if (hv > 0.02) { c.fillStyle = t.hover; c.globalAlpha = hv; c.beginPath(); c.arc(cx, cy, r, 0, TAU); c.fill(); c.globalAlpha = 1; }
  txt(c, glyph, cx, cy + 1, { size: r * 1.05, color: color || t.sub, align: 'center' });
}
function toggle(p, c, id, x, y, on, fn) {
  const t = T(), w = 84, h = 44, hv = p.hit(id, x, y, w, h, fn, { r: 22 }), k = p.sm('sw_' + id, on ? 1 : 0, 0.3);
  c.save(); rr(c, x, y, w, h, 22); c.fillStyle = on ? t.accent : (Game.settings.dark ? '#5f6368' : '#bdc1c6'); c.globalAlpha = 0.55 + k * 0.45; c.fill(); c.globalAlpha = 1;
  c.shadowColor = 'rgba(0,0,0,.35)'; c.shadowBlur = 6 + hv * 6; c.shadowOffsetY = 2; c.fillStyle = '#fff'; c.beginPath(); c.arc(x + 22 + k * 40, y + 22, 16 + hv * 2, 0, TAU); c.fill(); c.restore();
}

/* ---------- Moldura de navegador ---------- */
function chrome(p, c, url, title) {
  const t = T(), W = p.pw;
  c.save(); rr(c, 0, 0, W, p.ph, 30); c.clip();
  c.fillStyle = t.bg; c.fillRect(0, 0, W, p.ph);
  c.fillStyle = t.chrome; c.fillRect(0, 0, W, 56);
  // aba ativa
  rr(c, 16, 10, 330, 46, 14); c.fillStyle = t.bg; c.fill(); c.fillRect(16, 40, 330, 16);
  const g = c.createLinearGradient(30, 20, 56, 46); g.addColorStop(0, '#4285f4'); g.addColorStop(0.5, '#ea4335'); g.addColorStop(1, '#fbbc05');
  c.fillStyle = g; c.beginPath(); c.arc(44, 33, 12, 0, TAU); c.fill(); txt(c, 'P', 44, 34, { size: 15, weight: 700, color: '#fff', align: 'center' });
  txt(c, title, 66, 34, { size: 20, color: t.text }); iconBtn(p, c, 'tab_close', 322, 34, 16, '✕', () => Game.closeMenuBtn());
  iconBtn(p, c, 'tab_new', 376, 34, 18, '+', () => Game.go('home'));
  ['—', '▢', '✕'].forEach((g2, i) => txt(c, g2, W - 110 + i * 40, 30, { size: 20, color: t.sub, align: 'center' }));
  // barra de endereço
  c.fillStyle = t.bg; c.fillRect(0, 56, W, 56);
  iconBtn(p, c, 'nav_back', 40, 84, 22, '←', () => Game.back(), Game.history.length ? t.text : t.line);
  iconBtn(p, c, 'nav_fwd', 88, 84, 22, '→', null, t.line);
  iconBtn(p, c, 'nav_reload', 136, 84, 22, '⟳', () => { p.show(false); setTimeout(() => p.show(true), 160); Sfx.play('open'); });
  iconBtn(p, c, 'nav_home', 184, 84, 22, '⌂', () => Game.go('home'));
  rr(c, 222, 62, W - 222 - 170, 44, 22); c.fillStyle = t.surface; c.fill();
  txt(c, '🔒', 248, 85, { size: 20 }); txt(c, url, 280, 85, { size: 22, color: t.text });
  txt(c, '☆', W - 214, 85, { size: 26, color: t.sub, align: 'center' });
  const nm = (Game.playerName() || 'P')[0].toUpperCase();
  c.fillStyle = '#34a853'; c.beginPath(); c.arc(W - 100, 84, 20, 0, TAU); c.fill(); txt(c, nm, W - 100, 85, { size: 22, weight: 600, color: '#fff', align: 'center' });
  iconBtn(p, c, 'nav_more', W - 50, 84, 22, '⋮', () => Game.go('set'));
  c.fillStyle = t.line; c.fillRect(0, 111, W, 2);
  c.restore();
  return 113;
}

function caretOn() { return Math.floor(performance.now() / 500) % 2 === 0; }

/* ---------- Telas ---------- */
function drawHome(p, c, y0) {
  const t = T(), W = p.pw;
  const word = 'PicoVerso'; c.font = '500 96px ' + FONT; const tw = c.measureText(word).width; let x = (W - tw) / 2;
  const bob = Math.sin(performance.now() / 900) * 4;
  for (let i = 0; i < word.length; i++) { const ch = word[i]; c.fillStyle = GCOL[i]; c.textAlign = 'left'; c.textBaseline = 'middle'; c.font = '500 96px ' + FONT; c.fillText(ch, x, y0 + 80 + Math.sin(performance.now() / 700 + i * 0.6) * 3); x += c.measureText(ch).width; }
  void bob; p.animating = true;
  // caixa de pesquisa
  const sx = 230, sw = 820, sy = y0 + 150, sh = 70, hv = p.hit('search', sx, sy, sw, sh, () => Game.focusText('search'), { r: 35 });
  c.save(); c.shadowColor = 'rgba(32,33,36,' + (0.18 + hv * 0.2) + ')'; c.shadowBlur = 8 + hv * 14; c.shadowOffsetY = 2;
  rr(c, sx, sy, sw, sh, 35); c.fillStyle = Game.settings.dark ? '#303134' : '#fff'; c.fill(); c.shadowColor = 'transparent';
  c.strokeStyle = Game.focus === 'search' ? t.accent : t.line; c.lineWidth = Game.focus === 'search' ? 3 : 2; c.stroke(); c.restore();
  txt(c, '🔍', sx + 40, sy + sh / 2 + 1, { size: 26 });
  const shown = Game.focus === 'search' ? Game.text + (caretOn() ? '|' : '') : (Game.text || '');
  if (shown) txt(c, shown, sx + 80, sy + sh / 2 + 1, { size: 28, color: t.text }); else txt(c, 'Pergunte algo ao Pico (IA) ou toque para digitar…', sx + 80, sy + sh / 2 + 1, { size: 26, color: t.sub });
  pill(p, c, 'ask', sx + sw - 150, sy + 10, 130, sh - 20, '✨ Enviar', () => Game.submit(), { fill: '#1a73e8', color: '#fff', stroke: false, size: 22, weight: 600 });
  // botões
  pill(p, c, 'b_ai', W / 2 - 250, y0 + 250, 230, 54, 'Falar com a IA', () => Game.openChat('pico'), { size: 22 });
  pill(p, c, 'b_lucky', W / 2 + 20, y0 + 250, 230, 54, 'Estou com sorte 🍀', () => Game.play(['village', 'desert', 'snow'][Math.floor(Math.random() * 3)]), { size: 22 });
  // atalhos
  const tiles = [
    { id: 'play', icon: '▶', label: Game.mode === 'play' ? 'Continuar' : 'Jogar', col: '#34a853', fn: () => (Game.mode === 'play' ? Game.closeMenu() : Game.go('maps')) },
    { id: 'maps', icon: '🗺', label: 'Mapas', col: '#4285f4', fn: () => Game.go('maps') },
    { id: 'chat', icon: '💬', label: 'Conversar', col: '#ea4335', fn: () => Game.openChat('pico') },
    { id: 'ctrl', icon: '🎮', label: 'Controle', col: '#fbbc05', fn: () => Game.go('ctrl') },
    { id: 'set', icon: '⚙', label: 'Ajustes', col: '#8e4ec6', fn: () => Game.go('set') },
    { id: 'help', icon: '?', label: 'Ajuda', col: '#00acc1', fn: () => Game.go('help') }
  ];
  const tw2 = 150, gap = 26, tx0 = (W - (tiles.length * tw2 + (tiles.length - 1) * gap)) / 2, ty = y0 + 345;
  tiles.forEach((tl, i) => {
    const x2 = tx0 + i * (tw2 + gap), h2 = p.hit('t_' + tl.id, x2, ty, tw2, 178, tl.fn, { r: 24 });
    c.save(); c.translate(x2 + tw2 / 2, ty + 89); const s = 1 + h2 * 0.07; c.scale(s, s); c.translate(-tw2 / 2, -89 - h2 * 6);
    c.shadowColor = 'rgba(0,0,0,' + (0.1 + h2 * 0.22) + ')'; c.shadowBlur = 10 + h2 * 18; c.shadowOffsetY = 4 + h2 * 4;
    rr(c, 0, 0, tw2, 178, 24); c.fillStyle = t.card; c.fill(); c.shadowColor = 'transparent'; c.strokeStyle = h2 > 0.05 ? tl.col : t.line; c.lineWidth = 2; c.stroke();
    c.fillStyle = tl.col; c.beginPath(); c.arc(tw2 / 2, 64, 40, 0, TAU); c.fill();
    txt(c, tl.icon, tw2 / 2, 66, { size: 40, color: '#fff', align: 'center', weight: 700 });
    txt(c, tl.label, tw2 / 2, 138, { size: 24, weight: 500, color: t.text, align: 'center' });
    c.restore();
  });
  txt(c, 'Dica: deslize o touch de trás para andar  ·  clique para escolher  ·  botão lateral abre o menu', W / 2, y0 + 560, { size: 20, color: t.sub, align: 'center' });
  if (Game.mode === 'play') pill(p, c, 'b_lobby', W - 300, y0 + 540, 260, 44, '↩ Voltar ao saguão', () => Game.toLobby(), { size: 20 });
}

let _thumbs = {};
function drawMaps(p, c, y0) {
  const t = T(), W = p.pw;
  txt(c, 'Escolha um mundo 360°', W / 2, y0 + 44, { size: 40, weight: 500, color: t.text, align: 'center' });
  const ids = ['village', 'desert', 'snow'], desc = {
    village: 'Fonte, moinho, flores e casas coloridas. Dia ensolarado com passarinhos.',
    desert: 'Oásis com palmeiras, feira, fogueira e pirâmides ao pôr do sol.',
    snow: 'Cabanas aquecidas, boneco de neve, fogueira e aurora boreal.'
  };
  const cw = 360, gap = 34, x0 = (W - (cw * 3 + gap * 2)) / 2;
  ids.forEach((id, i) => {
    if (!_thumbs[id]) _thumbs[id] = skyCanvas(MAPS[id].sky, 512);
    const x = x0 + i * (cw + gap), y = y0 + 90, h = 480, hv = p.hit('map_' + id, x, y, cw, h, () => Game.play(id), { r: 26 });
    c.save(); c.translate(x + cw / 2, y + h / 2); const s = 1 + hv * 0.04; c.scale(s, s); c.translate(-cw / 2, -h / 2 - hv * 6);
    c.shadowColor = 'rgba(0,0,0,' + (0.15 + hv * 0.25) + ')'; c.shadowBlur = 14 + hv * 20; c.shadowOffsetY = 6; rr(c, 0, 0, cw, h, 26); c.fillStyle = t.card; c.fill(); c.shadowColor = 'transparent';
    c.save(); rr(c, 0, 0, cw, 210, 26); c.clip(); const th = _thumbs[id], off = (Math.sin(performance.now() / 4000 + i) * 0.5 + 0.5) * 0.25;
    c.drawImage(th, th.width * (0.1 + off), th.height * 0.12, th.width * 0.4, th.height * 0.5, 0, 0, cw, 210);
    const gr = c.createLinearGradient(0, 120, 0, 210); gr.addColorStop(0, 'rgba(0,0,0,0)'); gr.addColorStop(1, 'rgba(0,0,0,.45)'); c.fillStyle = gr; c.fillRect(0, 0, cw, 210); c.restore();
    txt(c, MAPS[id].emoji, 36, 176, { size: 44 });
    txt(c, MAPS[id].name, 24, 244, { size: 31, weight: 600, color: t.text });
    wrapLines(c, desc[id], cw - 48, '400 21px ' + FONT).slice(0, 3).forEach((l, k) => txt(c, l, 24, 288 + k * 28, { size: 21, color: t.sub }));
    const got = Game.progress[id] || 0;
    txt(c, '⭐ ' + got + ' / 8 estrelas', 24, 398, { size: 22, color: got >= 8 ? '#34a853' : t.text, weight: 500 });
    c.restore();
    pill(p, c, 'play_' + id, x + 40, y + 420, cw - 80, 48, Game.map && Game.map.id === id ? 'Recomeçar' : '▶ Jogar', () => Game.play(id), { fill: '#1a73e8', color: '#fff', stroke: false, size: 22, weight: 600 });
  });
}

function drawSettings(p, c, y0) {
  const t = T(), W = p.pw, S = Game.settings;
  txt(c, 'Ajustes', 60, y0 + 40, { size: 38, weight: 500, color: t.text });
  const rowsL = [['Música', 'music'], ['Efeitos sonoros', 'sfx'], ['Voz das pessoas (fala)', 'voice'], ['Tema escuro', 'dark'], ['Selecionar com o olhar (2s)', 'dwell']];
  const rowsR = [['Giro rápido (touch ← →)', 'snap'], ['Inverter andar do touch', 'invert']];
  const row = (x, y, label, key) => { txt(c, label, x, y + 22, { size: 26, color: t.text }); toggle(p, c, 'tg_' + key, x + 440, y, !!S[key], () => Game.setSetting(key, !S[key])); };
  rowsL.forEach((r, i) => row(60, y0 + 84 + i * 66, r[0], r[1]));
  rowsR.forEach((r, i) => row(670, y0 + 84 + i * 66, r[0], r[1]));
  const stepper = (x, y, label, val, dec, inc, id) => { txt(c, label, x, y + 22, { size: 26, color: t.text }); iconBtn(p, c, id + '_m', x + 480, y + 22, 24, '−', dec, t.accent); txt(c, val, x + 550, y + 23, { size: 26, weight: 600, color: t.text, align: 'center' }); iconBtn(p, c, id + '_p', x + 620, y + 22, 24, '+', inc, t.accent); };
  stepper(670, y0 + 84 + 2 * 66 - 66 + 132 - 66 + 0, 'Volume', Math.round(S.vol * 100) + '%', () => Game.setSetting('vol', clamp(Math.round((S.vol - 0.1) * 10) / 10, 0, 1)), () => Game.setSetting('vol', clamp(Math.round((S.vol + 0.1) * 10) / 10, 0, 1)), 'vol');
  stepper(670, y0 + 84 + 3 * 66, 'Velocidade de andar', S.speed.toFixed(1) + ' m/s', () => Game.setSetting('speed', clamp(+(S.speed - 0.4).toFixed(1), 1, 5)), () => Game.setSetting('speed', clamp(+(S.speed + 0.4).toFixed(1), 1, 5)), 'spd');
  txt(c, 'Andar na direção de', 670, y0 + 84 + 4 * 66 + 22, { size: 26, color: t.text });
  pill(p, c, 'wd', 1000, y0 + 84 + 4 * 66, 200, 46, S.walkDir === 'head' ? '👁 Olhar' : '🎮 Controle', () => Game.setSetting('walkDir', S.walkDir === 'head' ? 'hand' : 'head'), { size: 21 });
  txt(c, 'Seu nome:', 60, y0 + 84 + 5 * 66 + 36, { size: 26, color: t.text });
  const nameShown = Game.focus === 'name' ? Game.text + (caretOn() ? '|' : '') : (Game.playerName() || 'toque para definir');
  const hvn = p.hit('nameBox', 220, y0 + 84 + 5 * 66 + 10, 380, 52, () => Game.focusText('name'), { r: 26 });
  rr(c, 220, y0 + 84 + 5 * 66 + 10, 380, 52, 26); c.fillStyle = t.surface; c.fill(); c.strokeStyle = Game.focus === 'name' ? t.accent : (hvn > 0.05 ? t.accent : t.line); c.lineWidth = 2; c.stroke();
  txt(c, nameShown, 244, y0 + 84 + 5 * 66 + 37, { size: 26, color: t.text });
  pill(p, c, 'recenter', 640, y0 + 84 + 5 * 66 + 10, 260, 52, '⌖ Recentralizar visão', () => Game.recenter(), { size: 21 });
  pill(p, c, 'resetp', 920, y0 + 84 + 5 * 66 + 10, 280, 52, '↺ Zerar progresso', () => Game.resetProgress(), { size: 21 });
}

function drawControls(p, c, y0) {
  const t = T(), W = p.pw, I = Input;
  p.live = true;
  txt(c, 'Controle do Pico G2', 60, y0 + 40, { size: 38, weight: 500, color: t.text });
  const lines = ['👆 Deslize o touch de trás ↑↓ para andar', '↔ Deslize ← → para girar (giro rápido)', '🎯 Aponte o controle: o braço e o laser seguem você', '🖱 Clique (gatilho/touch) = selecionar botões, teclado, portas e pessoas', '☰ Botão lateral = abrir/fechar o menu', '👁 Sem controle? Ative “Selecionar com o olhar” nos ajustes'];
  lines.forEach((l, i) => txt(c, l, 60, y0 + 100 + i * 46, { size: 24, color: t.text }));
  // testador
  const bx = 760, by = y0 + 80;
  rr(c, bx - 20, by - 10, 500, 380, 24); c.fillStyle = t.surface; c.fill();
  txt(c, 'Teste ao vivo', bx, by + 20, { size: 24, weight: 600, color: t.text });
  txt(c, I.raw.id ? I.raw.id.slice(0, 40) : 'Nenhum controle detectado — mexa nele', bx, by + 54, { size: 18, color: t.sub });
  for (let i = 0; i < 4; i++) {
    const v = I.raw.axes[i] || 0, y = by + 92 + i * 32; txt(c, 'eixo ' + i, bx, y, { size: 18, color: t.sub });
    rr(c, bx + 80, y - 9, 280, 18, 9); c.fillStyle = t.line; c.fill(); const cx = bx + 220 + v * 140; c.fillStyle = Math.abs(v) > 0.3 ? '#34a853' : t.accent; c.beginPath(); c.arc(cx, y, 11, 0, TAU); c.fill();
    txt(c, v.toFixed(2), bx + 400, y, { size: 18, color: t.text });
  }
  for (let i = 0; i < 8; i++) { const x = bx + (i % 4) * 110, y = by + 238 + Math.floor(i / 4) * 52, on = (I.raw.buttons[i] || 0) > 0.5; rr(c, x, y, 96, 40, 12); c.fillStyle = on ? '#34a853' : t.line; c.fill(); txt(c, 'btn ' + i, x + 48, y + 21, { size: 18, color: on ? '#fff' : t.sub, align: 'center' }); }
  const st = I.calib ? '➜ ' + I.calib.msg : 'Clique=' + (Game.settings.clickBtns ? Game.settings.clickBtns.join(',') : 'qualquer') + ' · Menu=' + Game.settings.menuBtns.join(',') + ' · Andar=eixo ' + (Game.settings.walkAxis >= 0 ? Game.settings.walkAxis : 'auto');
  txt(c, st, 60, y0 + 400, { size: 20, color: I.calib ? '#ea4335' : t.sub, weight: I.calib ? 600 : 400 });
  pill(p, c, 'cal_click', 60, y0 + 430, 270, 52, 'Calibrar CLIQUE', () => Input.startCalib('click'), { size: 21 });
  pill(p, c, 'cal_menu', 350, y0 + 430, 270, 52, 'Calibrar MENU', () => Input.startCalib('menu'), { size: 21 });
  pill(p, c, 'cal_walk', 640, y0 + 430, 270, 52, 'Calibrar ANDAR', () => Input.startCalib('walk'), { size: 21 });
  pill(p, c, 'cal_reset', 930, y0 + 430, 270, 52, 'Restaurar padrão', () => Input.resetCalib(), { size: 21 });
  txt(c, 'Calibrar: toque no botão e depois aperte o botão do controle (ou deslize o touch p/ frente).', 60, y0 + 515, { size: 19, color: t.sub });
}

function drawHelp(p, c, y0) {
  const t = T(), W = p.pw;
  txt(c, 'Como jogar', 60, y0 + 40, { size: 38, weight: 500, color: t.text });
  const L = ['⭐ Encontre as 8 estrelas douradas de cada mundo (algumas estão dentro das casas!).', '🚪 Aponte para a porta e clique: ela abre. Portas fechadas bloqueiam o caminho.', '💬 Clique em uma pessoa para conversar. Digite no teclado flutuante ou toque nas respostas rápidas.', '🤖 O Pico é a IA do saguão: pergunte o que quiser (piadas, dicas, o clima…).', '🗺 Abra o menu a qualquer hora para trocar de mapa — são 3 mundos em 360°.', '🥽 Modos: VR (WebXR), óculos com tela dividida e tela normal.', '🔊 Todos os sons e a música são gerados na hora, e as pessoas falam em voz alta (ajustes).'];
  L.forEach((l, i) => { const lines = wrapLines(c, l, W - 140, '400 25px ' + FONT); lines.forEach((ln, k) => txt(c, ln, 70, y0 + 100 + i * 62 + k * 28, { size: 25, color: t.text })); });
  txt(c, 'IA real (opcional): defina localStorage.pico_ai_url para o seu servidor e as pessoas passam a usar um LLM.', 70, y0 + 548, { size: 19, color: t.sub });
}

/* ---------- Chat ---------- */
function drawChatUI(p, c, x, y, w, h) {
  const t = T(), ch = Game.chat; if (!ch) return;
  const npc = ch.npc, side = 250;
  rr(c, x, y, side, h, 24); c.fillStyle = t.surface; c.fill();
  c.fillStyle = npc.robot ? '#4285f4' : ['#ea4335', '#34a853', '#fbbc05', '#8e4ec6'][npc.name.length % 4]; c.beginPath(); c.arc(x + side / 2, y + 84, 56 + Math.sin(performance.now() / 500) * (ch.thinking || ch.typing ? 3 : 0), 0, TAU); c.fill();
  txt(c, npc.robot ? '🤖' : (npc.scale && npc.scale < 0.9 ? '🧒' : '🧑'), x + side / 2, y + 88, { size: 60, align: 'center' });
  txt(c, npc.name, x + side / 2, y + 170, { size: 28, weight: 600, color: t.text, align: 'center' });
  txt(c, npc.role, x + side / 2, y + 202, { size: 20, color: t.sub, align: 'center' });
  txt(c, ch.thinking ? 'pensando…' : (ch.typing ? 'falando…' : 'online'), x + side / 2, y + 232, { size: 18, color: ch.thinking ? '#fbbc05' : '#34a853', align: 'center' });
  pill(p, c, 'ch_voice', x + 25, y + h - 140, side - 50, 44, Game.settings.voice ? '🔊 Voz ligada' : '🔇 Voz mudo', () => Game.setSetting('voice', !Game.settings.voice), { size: 19 });
  pill(p, c, 'ch_exit', x + 25, y + h - 84, side - 50, 52, '✕ Sair da conversa', () => Game.closeChat(), { size: 20, fill: Game.settings.dark ? '#5a2a2a' : '#fde7e9', color: '#c5221f', stroke: false });
  // mensagens
  const mx = x + side + 20, mw = w - side - 20, my = y, mh = h - 190;
  c.save(); rr(c, mx, my, mw, mh, 20); c.clip();
  const font = '400 25px ' + FONT; let by = my + mh - 14;
  for (let i = ch.msgs.length - 1; i >= 0 && by > my; i--) {
    const m = ch.msgs[i], shown = m.me ? m.text : m.text.slice(0, Math.floor(m.shown));
    const lines = wrapLines(c, shown || ' ', mw * 0.68, font), bh = lines.length * 32 + 26, bw = Math.min(mw * 0.72, Math.max(...lines.map(l => c.measureText(l).width)) + 44);
    by -= bh + 12; const bx = m.me ? mx + mw - bw - 14 : mx + 14;
    c.save(); c.globalAlpha = Math.min(1, m.age * 5); c.translate(0, (1 - Math.min(1, m.age * 5)) * 14);
    rr(c, bx, by, bw, bh, 22); c.fillStyle = m.me ? '#1a73e8' : t.surface; c.fill();
    lines.forEach((l, k) => txt(c, l, bx + 22, by + 26 + k * 32, { size: 25, color: m.me ? '#fff' : t.text }));
    c.restore();
  }
  if (ch.thinking) { const tx = mx + 14, ty = my + mh - 50; rr(c, tx, ty, 100, 40, 20); c.fillStyle = t.surface; c.fill(); for (let k = 0; k < 3; k++) { c.fillStyle = t.sub; c.beginPath(); c.arc(tx + 28 + k * 22, ty + 20 - Math.abs(Math.sin(performance.now() / 220 + k)) * 6, 6, 0, TAU); c.fill(); } }
  c.restore();
  // chips
  let cx = mx;
  Game.chips().forEach((chip, i) => { c.font = '400 21px ' + FONT; const cw = c.measureText(chip).width + 36; if (cx + cw > mx + mw) return; pill(p, c, 'chip' + i, cx, y + h - 176, cw, 40, chip, () => Game.sendChat(chip), { size: 21 }); cx += cw + 10; });
  // entrada
  const iy = y + h - 120, iw = mw - 160, hv = p.hit('ch_input', mx, iy, iw, 62, () => Game.focusText('chat'), { r: 31 });
  rr(c, mx, iy, iw, 62, 31); c.fillStyle = Game.settings.dark ? '#303134' : '#fff'; c.fill(); c.strokeStyle = Game.focus === 'chat' ? t.accent : (hv > 0.05 ? t.accent : t.line); c.lineWidth = Game.focus === 'chat' ? 3 : 2; c.stroke();
  const sh = Game.text; c.font = '400 26px ' + FONT; let disp = sh; while (c.measureText(disp).width > iw - 60 && disp.length > 1) disp = disp.slice(1);
  if (Game.focus === 'chat') txt(c, disp + (caretOn() ? '|' : ''), mx + 28, iy + 32, { size: 26, color: t.text }); else if (sh) txt(c, disp, mx + 28, iy + 32, { size: 26, color: t.text }); else txt(c, 'Toque aqui e digite…', mx + 28, iy + 32, { size: 26, color: t.sub });
  pill(p, c, 'ch_send', mx + iw + 14, iy, 140, 62, 'Enviar ➤', () => Game.submit(), { fill: '#1a73e8', color: '#fff', stroke: false, size: 22, weight: 600 });
}

/* ---------- Painéis ---------- */
function makeMainPanel() {
  return new Panel({
    name: 'main', w: 3.4, h: 1.9125, pw: 1280, ph: 720, R: 1.9, order: 10, fps: 30,
    draw(p, c) {
      const t = T(), titles = { home: 'PicoVerso — Início', maps: 'PicoVerso — Mapas', set: 'PicoVerso — Ajustes', ctrl: 'PicoVerso — Controle', help: 'PicoVerso — Ajuda', chat: 'PicoVerso — Conversa' };
      const urls = { home: 'picoverso://inicio', maps: 'picoverso://mapas', set: 'picoverso://ajustes', ctrl: 'picoverso://controle', help: 'picoverso://ajuda', chat: 'picoverso://ia/' + (Game.chat ? Game.chat.npc.id : 'pico') };
      p.live = false;
      const y0 = chrome(p, c, urls[Game.screen], titles[Game.screen]);
      c.save(); rr(c, 0, 0, p.pw, p.ph, 30); c.clip();
      if (Game.screen === 'home') drawHome(p, c, y0 + 10);
      else if (Game.screen === 'maps') drawMaps(p, c, y0);
      else if (Game.screen === 'set') drawSettings(p, c, y0);
      else if (Game.screen === 'ctrl') drawControls(p, c, y0);
      else if (Game.screen === 'help') drawHelp(p, c, y0);
      else if (Game.screen === 'chat') drawChatUI(p, c, 30, y0 + 20, p.pw - 60, p.ph - y0 - 40);
      p.drawRipples(c); c.restore();
      c.strokeStyle = 'rgba(128,128,128,.5)'; c.lineWidth = 3; rr(c, 1.5, 1.5, p.pw - 3, p.ph - 3, 30); c.stroke();
    }
  });
}
function makeChatPanel() {
  return new Panel({
    name: 'chat', w: 2.0, h: 1.12, pw: 1250, ph: 700, R: 1.6, order: 11, fps: 30,
    draw(p, c) {
      const t = T();
      c.save(); rr(c, 0, 0, p.pw, p.ph, 34); c.clip(); c.fillStyle = t.bg; c.fillRect(0, 0, p.pw, p.ph);
      c.fillStyle = t.chrome; c.fillRect(0, 0, p.pw, 50); const g = c.createLinearGradient(14, 8, 40, 40); g.addColorStop(0, '#4285f4'); g.addColorStop(1, '#ea4335'); c.fillStyle = g; c.beginPath(); c.arc(34, 25, 12, 0, TAU); c.fill();
      txt(c, 'Conversa — ' + (Game.chat ? Game.chat.npc.name : ''), 58, 26, { size: 22, weight: 500, color: t.text });
      iconBtn(p, c, 'chat_close', p.pw - 34, 25, 18, '✕', () => Game.closeChat());
      drawChatUI(p, c, 24, 64, p.pw - 48, p.ph - 84); p.drawRipples(c); c.restore();
      c.strokeStyle = 'rgba(128,128,128,.5)'; c.lineWidth = 3; rr(c, 1.5, 1.5, p.pw - 3, p.ph - 3, 34); c.stroke();
    }
  });
}

const KB_ACC = ['á', 'é', 'í', 'ó', 'ú', 'ã', 'õ', 'â', 'ê', 'ô'];
const KB_ROWS = [
  '1234567890'.split(''), 'qwertyuiop'.split(''), 'asdfghjklç'.split(''), 'zxcvbnm,.?'.split('')
];
function makeKeyboard() {
  return new Panel({
    name: 'kb', w: 1.8, h: 0.81, pw: 1100, ph: 495, R: 1.3, order: 12, bob: 0.008, fps: 30,
    draw(p, c) {
      const t = T();
      c.save(); rr(c, 0, 0, p.pw, p.ph, 30); c.clip();
      c.fillStyle = Game.settings.dark ? 'rgba(20,21,24,.95)' : 'rgba(205,209,216,.96)'; c.fillRect(0, 0, p.pw, p.ph);
      const kw = 86, kh = 66, gap = 8, pad = 12;
      const key = (id, x, y, w, label, fn, o) => {
        o = o || {}; const hv = p.hit(id, x, y, w, kh, fn, { r: 14, sfx: 'key', silent: false });
        c.save(); const dy = hv * 3; c.translate(x + w / 2, y + kh / 2); const s = 1 + hv * 0.06; c.scale(s, s); c.translate(-w / 2, -kh / 2 - dy);
        c.shadowColor = 'rgba(0,0,0,.35)'; c.shadowBlur = 5; c.shadowOffsetY = 3; rr(c, 0, 0, w, kh, 14);
        c.fillStyle = o.fill || (hv > 0.05 ? (Game.settings.dark ? '#5a5d63' : '#eaf1fe') : t.key); c.fill(); c.shadowColor = 'transparent';
        if (hv > 0.05) { c.strokeStyle = t.accent; c.lineWidth = 3; c.stroke(); }
        txt(c, label, w / 2, kh / 2 + 2, { size: o.size || 32, weight: 500, color: o.color || t.text, align: 'center' }); c.restore();
      };
      const up = Game.shift;
      // acentos
      let x = pad + 20; KB_ACC.forEach((a, i) => { key('ac' + i, x, 10, kw - 10, up ? a.toUpperCase() : a, () => Game.typeChar(up ? a.toUpperCase() : a), { size: 28 }); x += kw - 10 + gap; });
      KB_ROWS.forEach((row, ri) => {
        const y = 10 + (ri + 1) * (kh + gap), n = row.length + (ri === 0 ? 1 : 0) + (ri === 3 ? 0 : 0), total = n * kw + (n - 1) * gap;
        let x0 = (p.pw - total) / 2 + (ri === 2 ? -0 : 0);
        if (ri === 3) { total; }
        let xx = x0;
        row.forEach(ch => { const L = (up || (Game.text.length === 0 && ri > 0 && Game.autoShift)) && /[a-zç]/.test(ch) ? ch.toUpperCase() : ch; key('k' + ch, xx, y, kw, L, () => Game.typeChar(L)); xx += kw + gap; });
        if (ri === 0) key('bk', xx, y, kw + 30, '⌫', () => Game.backspace(), { fill: Game.settings.dark ? '#4a4d54' : '#b7bcc6', size: 34 });
      });
      const y5 = 10 + 5 * (kh + gap);
      key('shift', pad + 10, y5, 150, up ? '⇧ ABC' : '⇧ abc', () => Game.toggleShift(), { fill: up ? '#1a73e8' : (Game.settings.dark ? '#4a4d54' : '#b7bcc6'), color: up ? '#fff' : t.text, size: 26 });
      key('space', pad + 10 + 160, y5, 520, 'espaço', () => Game.typeChar(' '), { size: 26 });
      key('excl', pad + 10 + 690, y5, kw - 10, '!', () => Game.typeChar('!'), { size: 30 });
      key('hide', pad + 10 + 690 + kw - 2, y5, 100, '⌨▾', () => Game.hideKeyboard(), { fill: Game.settings.dark ? '#4a4d54' : '#b7bcc6', size: 26 });
      key('enter', pad + 10 + 690 + kw + 108, y5, 200, 'Enviar ➤', () => Game.submit(), { fill: '#1a73e8', color: '#fff', size: 26 });
      p.drawRipples(c); c.restore();
      c.strokeStyle = 'rgba(128,128,128,.55)'; c.lineWidth = 3; rr(c, 1.5, 1.5, p.pw - 3, p.ph - 3, 30); c.stroke();
    }
  });
}

function makeHud() {
  return new Panel({
    name: 'hud', w: 0.62, h: 0.2, pw: 700, ph: 226, R: 1.15, order: 50, bob: 0, fps: 20,
    draw(p, c) {
      const t = T(); c.save(); rr(c, 0, 0, p.pw, p.ph, 40); c.fillStyle = 'rgba(16,18,30,.78)'; c.fill(); c.strokeStyle = 'rgba(255,255,255,.28)'; c.lineWidth = 3; c.stroke();
      const mp = Game.map ? MAPS[Game.map.id] : null;
      txt(c, mp ? mp.emoji + ' ' + mp.name : '✨ Saguão PicoVerso', 36, 44, { size: 34, weight: 600, color: '#fff' });
      for (let i = 0; i < 8; i++) { const got = Game.mode === 'play' && i < Game.starsFound; txt(c, '★', 44 + i * 44, 104, { size: 42, color: got ? '#ffd23f' : 'rgba(255,255,255,.22)', align: 'center' }); }
      const hint = Game.hint || ''; txt(c, hint.length > 44 ? hint.slice(0, 43) + '…' : hint, 36, 170, { size: 26, color: '#a9c7ff' });
      const hv = p.hit('hud_menu', p.pw - 190, 24, 160, 78, () => Game.toggleMenu(), { r: 39 });
      rr(c, p.pw - 190, 24, 160, 78, 39); c.fillStyle = hv > 0.05 ? '#5b9bff' : '#4285f4'; c.fill(); txt(c, '☰ Menu', p.pw - 110, 64, { size: 30, weight: 600, color: '#fff', align: 'center' });
      const hv2 = p.hit('hud_help', p.pw - 190, 114, 160, 70, () => Game.openChatNearest(), { r: 35 });
      rr(c, p.pw - 190, 114, 160, 70, 35); c.fillStyle = hv2 > 0.05 ? '#4be08a' : '#34a853'; c.fill(); txt(c, '💬 Falar', p.pw - 110, 150, { size: 28, weight: 600, color: '#fff', align: 'center' });
      p.drawRipples(c); c.restore();
    }
  });
}
function makeToast() {
  return new Panel({
    name: 'toast', w: 0.9, h: 0.16, pw: 900, ph: 160, R: 1.15, order: 51, bob: 0.004, fps: 30,
    draw(p, c) {
      c.save(); rr(c, 0, 0, p.pw, p.ph, 50); const g = c.createLinearGradient(0, 0, p.pw, 0); g.addColorStop(0, 'rgba(66,133,244,.95)'); g.addColorStop(1, 'rgba(142,78,198,.95)'); c.fillStyle = g; c.fill();
      c.strokeStyle = 'rgba(255,255,255,.5)'; c.lineWidth = 3; c.stroke();
      const lines = wrapLines(c, Game.toastText || '', p.pw - 100, '600 38px ' + FONT);
      lines.slice(0, 2).forEach((l, i) => txt(c, l, p.pw / 2, p.ph / 2 + (i - (Math.min(lines.length, 2) - 1) / 2) * 46, { size: 38, weight: 600, color: '#fff', align: 'center' }));
      c.restore();
    }
  });
}
