// Locomoção por braços ("estilo Gorilla Tag"): as mãos empurram o mundo, e soltar dá o impulso.
// Port simplificado da lógica GorillaLocomotion (MIT, Another-Axiom): média das duas mãos quando ambas tocam,
// velocidade média ao soltar x1.1 limitada a 6.5 m/s. Módulo puro (sem three.js) para testes em Node.

const add = (a, b) => [a[0] + b[0], a[1] + b[1], a[2] + b[2]];
const sub = (a, b) => [a[0] - b[0], a[1] - b[1], a[2] - b[2]];
const mulS = (a, k) => [a[0] * k, a[1] * k, a[2] * k];
const len = (a) => Math.hypot(a[0], a[1], a[2]);

/** Empurra uma esfera para fora de caixas AABB {min,max}. Retorna o vetor de correção (0,0,0 se livre). */
export function pushOutSphere(c, r, boxes) {
  let total = [0, 0, 0];
  for (let pass = 0; pass < 3; pass++) {
    let moved = false;
    const p = add(c, total);
    for (const b of boxes) {
      const q = [Math.max(b.min[0], Math.min(p[0], b.max[0])), Math.max(b.min[1], Math.min(p[1], b.max[1])), Math.max(b.min[2], Math.min(p[2], b.max[2]))];
      const d = sub(p, q), dist = len(d);
      if (dist >= r) continue;
      let push;
      if (dist > 1e-6) push = mulS(d, (r - dist) / dist);
      else { // centro dentro da caixa: sai pela face mais próxima
        let best = 1e9, axis = 0, sign = 1;
        for (let a = 0; a < 3; a++) {
          const lo = p[a] - b.min[a], hi = b.max[a] - p[a];
          if (lo < best) { best = lo; axis = a; sign = -1; }
          if (hi < best) { best = hi; axis = a; sign = 1; }
        }
        push = [0, 0, 0]; push[axis] = sign * (best + r);
      }
      total = add(total, push); moved = true;
      p[0] = c[0] + total[0]; p[1] = c[1] + total[1]; p[2] = c[2] + total[2];
    }
    if (!moved) break;
  }
  return total;
}

export class ArmLoco {
  constructor(boxes, opts = {}) {
    this.boxes = boxes;
    this.handR = opts.handRadius ?? 0.07;
    this.margin = opts.contactMargin ?? 0.02;
    this.bodyR = opts.bodyRadius ?? 0.3;
    this.bodyDrop = opts.bodyDrop ?? 0.1;
    this.gravity = opts.gravity ?? 9.8;
    this.jumpMul = opts.jumpMultiplier ?? 1.1;
    this.maxJump = opts.maxJump ?? 6.5;
    this.pos = [0, 0, 0];      // origem do rig (chão real do jogador) no mundo
    this.vel = [0, 0, 0];
    this.last = [null, null];  // posição local anterior de cada mão
    this.was = [false, false];
    this.touching = [false, false];
    this.handWorld = [[0, 0, 0], [0, 0, 0]];
    this.grounded = false;
    this.hist = [];
    this.stuckFor = 0;
  }

  teleport(p) { this.pos = [...p]; this.vel = [0, 0, 0]; this.last = [null, null]; this.was = [false, false]; this.hist = []; }

  /** headLocal/handLocal: posições relativas à origem do rig (como vêm do rastreamento). */
  update(dt, headLocal, handLocal) {
    dt = Math.min(Math.max(dt, 1e-4), 0.05);
    const moves = []; let allFirst = true;
    const touchingNow = [false, false];
    for (let i = 0; i < 2; i++) {
      const hl = handLocal[i];
      const hw = add(this.pos, hl);
      const cont = this.was[i] && this.last[i];
      // mantém o contato por uma pequena margem (histerese) para a mão não "soltar" ao encostar de leve
      const near = cont && len(pushOutSphere(hw, this.handR + this.margin, this.boxes)) > 1e-5;
      const m0 = cont ? sub(this.last[i], hl) : [0, 0, 0];
      const pen = pushOutSphere(add(hw, m0), this.handR, this.boxes);
      if (len(pen) > 1e-5 || near) {
        touchingNow[i] = true;
        if (cont) allFirst = false;
        moves.push(add(m0, pen));
      }
      this.last[i] = hl;
    }
    let move = [0, 0, 0];
    if (moves.length === 1) move = moves[0];
    else if (moves.length === 2) move = mulS(add(moves[0], moves[1]), 0.5);
    const ml = len(move);
    if (ml > 0.6) move = mulS(move, 0.6 / ml);   // evita saltos de teletransporte por falhas de rastreio
    const anyTouch = touchingNow[0] || touchingNow[1];
    const wasAny = this.was[0] || this.was[1];
    this.pos = add(this.pos, move);

    if (anyTouch) {
      // o 1º toque só afasta a mão da superfície: não conta como impulso de pulo
      if (!allFirst) { this.hist.push(mulS(move, 1 / dt)); if (this.hist.length > 6) this.hist.shift(); }
      this.vel = [0, 0, 0];
    } else {
      if (wasAny && this.hist.length) {
        const s = this.hist.reduce((a, v) => add(a, v), [0, 0, 0]);
        let v = mulS(s, this.jumpMul / this.hist.length);
        const l = len(v); if (l > this.maxJump) v = mulS(v, this.maxJump / l);
        this.vel = v; this.hist = [];
      }
      this.vel[1] -= this.gravity * dt;
      this.pos = add(this.pos, mulS(this.vel, dt));
    }
    this.was = touchingNow; this.touching = touchingNow;

    // corpo: três esferas (pés, tronco, cabeça) empurradas para fora do cenário (chão, paredes, plataformas, tetos)
    this.grounded = false;
    const hx = headLocal[0], hz = headLocal[2], hy = Math.max(0.5, headLocal[1]);
    const spheres = [[hx, 0.25, hz, 0.25, true], [hx, hy * 0.6, hz, this.bodyR * 0.85, false], [hx, hy - this.bodyDrop, hz, this.bodyR * 0.7, false]];
    for (let it = 0; it < 2; it++) {
      let any = false;
      for (const sp of spheres) {
        const bc = add(this.pos, [sp[0], sp[1], sp[2]]);
        const pen = pushOutSphere(bc, sp[3], this.boxes);
        const pl = len(pen);
        if (pl < 1e-6) continue;
        any = true;
        this.pos = add(this.pos, pen);
        const n = mulS(pen, 1 / pl);
        const vn = this.vel[0] * n[0] + this.vel[1] * n[1] + this.vel[2] * n[2];
        if (vn < 0) this.vel = sub(this.vel, mulS(n, vn));
        if (sp[4] && n[1] > 0.5) this.grounded = true;
      }
      if (!any) break;
    }
    if (this.grounded && !anyTouch) { const k = Math.max(0, 1 - 6 * dt); this.vel[0] *= k; this.vel[2] *= k; }
    for (let i = 0; i < 2; i++) this.handWorld[i] = add(this.pos, handLocal[i]);
    return { moved: ml, touching: touchingNow, grounded: this.grounded };
  }
}
