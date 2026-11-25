// Entrada genérica de controles WebXR. NÃO assume o layout do Pico G2: os índices reais de botões/eixos são
// detectados em tempo de execução e mostrados no diagnóstico. Módulo sem dependências (testável em Node).

export const DEADZONE = 0.18;

export class HandState {
  constructor(id) {
    this.id = id; this.handedness = 'none'; this.profiles = []; this.connected = false;
    this.buttons = []; this.prev = []; this.axes = [];
    this.pair = 0; this.stick = { x: 0, y: 0, mag: 0 };
    this.selecting = false; this.mapping = ''; this.emulated = true; this.hasPosition = false; this.targetRayMode = '';
    this.everPressed = new Set(); this.lastPressedIndex = -1; this.lastAxesMax = [];
  }

  /** snap = { handedness, profiles, gamepad:{buttons:[{pressed,touched,value}],axes,mapping}|null, selecting, emulated, hasPosition } */
  update(snap) {
    this.connected = true;
    this.handedness = snap.handedness || 'none';
    this.profiles = snap.profiles || [];
    this.selecting = !!snap.selecting;
    this.emulated = snap.emulated !== false;
    this.hasPosition = !!snap.hasPosition;
    this.targetRayMode = snap.targetRayMode || '';
    this.prev = this.buttons;
    const gp = snap.gamepad;
    this.mapping = gp ? gp.mapping || '' : '';
    this.buttons = gp ? gp.buttons.map((b) => ({ pressed: !!b.pressed, touched: !!b.touched, value: b.value || 0 })) : [];
    this.axes = gp ? Array.from(gp.axes) : [];
    for (let i = 0; i < this.buttons.length; i++) {
      if (this.buttons[i].pressed && !this.wasDown(i)) { this.everPressed.add(i); this.lastPressedIndex = i; }
    }
    // escolhe o par de eixos "ativo" (touchpad ou analógico) com histerese
    const pairs = Math.floor(this.axes.length / 2);
    let best = -1, bm = 0;
    for (let p = 0; p < pairs; p++) {
      const m = Math.hypot(this.axes[p * 2], this.axes[p * 2 + 1]);
      this.lastAxesMax[p] = Math.max(this.lastAxesMax[p] || 0, m);
      if (m > bm) { bm = m; best = p; }
    }
    if (best >= 0 && bm > 0.1) this.pair = best;
    if (pairs === 0) this.stick = { x: 0, y: 0, mag: 0 };
    else {
      const p = Math.min(this.pair, pairs - 1);
      let x = this.axes[p * 2], y = this.axes[p * 2 + 1];
      const mag = Math.hypot(x, y);
      if (mag < DEADZONE) { x = 0; y = 0; } else { const k = (mag - DEADZONE) / (1 - DEADZONE) / mag; x *= Math.min(1, k); y *= Math.min(1, k); }
      this.stick = { x, y, mag: Math.min(1, mag) };
    }
  }

  wasDown(i) { return !!(this.prev[i] && this.prev[i].pressed); }
  down(i) { return !!(this.buttons[i] && this.buttons[i].pressed); }
  justPressed(i) { return this.down(i) && !this.wasDown(i); }
  justReleased(i) { return !this.down(i) && this.wasDown(i); }
}

const DEFAULTS = { fire: [0], menu: [], alt: null /* null = qualquer outro botão */ };

export class Bindings {
  constructor(store) { this.store = store; this.map = JSON.parse(JSON.stringify(DEFAULTS)); this.invertX = false; this.invertY = false; this.load(); }
  load() {
    try {
      const raw = this.store && this.store.getItem('pico-webxr-bindings');
      if (raw) { const o = JSON.parse(raw); Object.assign(this.map, o.map || {}); this.invertX = !!o.invertX; this.invertY = !!o.invertY; }
    } catch (e) { /* sem storage */ }
  }
  save() { try { this.store && this.store.setItem('pico-webxr-bindings', JSON.stringify({ map: this.map, invertX: this.invertX, invertY: this.invertY })); } catch (e) { /* ok */ } }
  reset() { this.map = JSON.parse(JSON.stringify(DEFAULTS)); this.save(); }
  bind(action, idx) {
    for (const k of Object.keys(this.map)) if (Array.isArray(this.map[k])) this.map[k] = this.map[k].filter((i) => i !== idx);
    if (action === 'alt') this.map.alt = [idx]; else this.map[action] = [idx];
    this.save();
  }
  _list(hand, action) {
    if (action === 'alt' && this.map.alt === null) {
      const taken = new Set([...this.map.fire, ...this.map.menu]);
      const out = []; for (let i = 0; i < hand.buttons.length; i++) if (!taken.has(i)) out.push(i);
      return out;
    }
    return this.map[action] || [];
  }
  isDown(hand, action) {
    if (action === 'fire' && hand.selecting) return true;
    return this._list(hand, action).some((i) => hand.down(i));
  }
  justPressed(hand, action) { return this._list(hand, action).some((i) => hand.justPressed(i)); }
  /** Movimento já com inversões: x = direita(+), y = frente(+). Padrão xr-standard: frente = eixo Y negativo. */
  move(hand) {
    const s = hand.stick;
    return { x: this.invertX ? -s.x : s.x, y: this.invertY ? s.y : -s.y, mag: s.mag };
  }
}

/** Descrição legível do que o navegador reporta, para colar/colar no diagnóstico. */
export function describeHand(h) {
  const btn = h.buttons.map((b, i) => `${i}${b.pressed ? '●' : b.touched ? '○' : '·'}${b.value ? b.value.toFixed(2) : ''}`).join(' ');
  return `mão#${h.id} ${h.handedness} modo-raio=${h.targetRayMode} perfis=[${h.profiles.join(', ')}] mapping="${h.mapping}"\n` +
    `  posição ${h.hasPosition ? (h.emulated ? 'emulada (3DoF)' : 'rastreada (6DoF)') : 'ausente (só rotação)'}\n` +
    `  botões(${h.buttons.length}): ${btn}\n  eixos(${h.axes.length}): ${h.axes.map((a) => a.toFixed(2)).join(' ')} (par ativo ${h.pair})\n` +
    `  botões já pressionados: [${[...h.everPressed].join(', ')}]`;
}
