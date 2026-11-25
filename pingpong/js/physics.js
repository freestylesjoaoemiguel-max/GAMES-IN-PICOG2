'use strict';
/* Física do ping-pong: bola com arrasto, efeito (Magnus), quique na mesa/rede/chão. Medidas reais (mesa 2,74 x 1,525 m, altura 0,76 m). */
const TBL = { L: 2.74, W: 1.525, H: 0.76, NET: 0.1525 }, BALL_R = 0.02, G = 9.81;
const NET_HALF = TBL.W / 2 + 0.1525;
const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
const rand = (a, b) => a + Math.random() * (b - a);

function newBall() { return { x: 0, y: 1, z: 1.3, vx: 0, vy: 0, vz: 0, wx: 0, wy: 0, wz: 0 }; }

/* Avança a bola dt segundos. Preenche ev: ev.table = +1 (lado do jogador, z>0) / -1 (lado do adversário), ev.net, ev.floor */
function stepBall(b, dt, ev) {
  const sp = Math.sqrt(b.vx * b.vx + b.vy * b.vy + b.vz * b.vz), kd = 0.126 * sp, km = 0.0056;
  const ax = -kd * b.vx + km * (b.wy * b.vz - b.wz * b.vy);
  const ay = -G - kd * b.vy + km * (b.wz * b.vx - b.wx * b.vz);
  const az = -kd * b.vz + km * (b.wx * b.vy - b.wy * b.vx);
  b.vx += ax * dt; b.vy += ay * dt; b.vz += az * dt;
  const px = b.x, py = b.y, pz = b.z;
  b.x += b.vx * dt; b.y += b.vy * dt; b.z += b.vz * dt;
  const dec = Math.exp(-0.35 * dt); b.wx *= dec; b.wy *= dec; b.wz *= dec;
  ev.table = 0; ev.net = false; ev.floor = false;
  // rede
  if ((pz > 0) !== (b.z > 0) && Math.abs(b.x) < NET_HALF && b.y < TBL.H + TBL.NET + BALL_R && b.y > TBL.H - 0.03) {
    b.z = pz > 0 ? BALL_R + 0.002 : -BALL_R - 0.002; b.vz = -b.vz * 0.2; b.vx *= 0.5; b.vy *= 0.4; ev.net = true;
  }
  // mesa
  if (b.vy < 0 && b.y - BALL_R <= TBL.H && py - BALL_R >= TBL.H - 0.03 && Math.abs(b.x) <= TBL.W / 2 && Math.abs(b.z) <= TBL.L / 2) {
    b.y = TBL.H + BALL_R;
    // velocidade do ponto de contato: v + w x (0,-R,0) = v + (wz*R, 0, -wx*R)
    const cx = b.vx + b.wz * BALL_R, cz = b.vz - b.wx * BALL_R;
    const ut = Math.hypot(cx, cz) || 1e-6, jn = (1 + 0.93) * Math.abs(b.vy);
    const jt = Math.min(0.25 * jn, 0.4 * ut), dvx = -jt * cx / ut, dvz = -jt * cz / ut;
    b.vy = -b.vy * 0.93; b.vx += dvx; b.vz += dvz;
    // torque do atrito: dw = -(3/(2R)) (y x dv), com y x dv = (dvz, 0, -dvx)
    b.wx += -(3 / (2 * BALL_R)) * dvz; b.wz += -(3 / (2 * BALL_R)) * (-dvx);
    ev.table = b.z > 0 ? 1 : -1;
  }
  // chão e paredes
  if (b.y <= BALL_R) { b.y = BALL_R; if (b.vy < 0) { b.vy = -b.vy * 0.7; b.vx *= 0.85; b.vz *= 0.85; } ev.floor = true; }
  if (b.x > 3.4) { b.x = 3.4; b.vx = -Math.abs(b.vx) * 0.5; } else if (b.x < -3.4) { b.x = -3.4; b.vx = Math.abs(b.vx) * 0.5; }
  if (b.z < -4.9) { b.z = -4.9; b.vz = Math.abs(b.vz) * 0.5; } else if (b.z > 4.0) { b.z = 4.0; b.vz = -Math.abs(b.vz) * 0.5; }
  if (b.y > 3.4) { b.y = 3.4; b.vy = -Math.abs(b.vy) * 0.5; }
}

/* Simula sem efeitos colaterais; devolve a sequência de quiques na mesa (+1/-1), se bateu na rede e onde caiu o 1º quique */
const _sb = newBall(), _ev = { table: 0, net: false, floor: false };
function simulate(b0, maxT, stopZ) {
  Object.assign(_sb, b0); const out = { bounces: [], net: false, first: null, hitPlane: null }; let t = 0, prevZ = _sb.z;
  while (t < maxT) {
    stepBall(_sb, 1 / 120, _ev); t += 1 / 120;
    if (_ev.net) out.net = true;
    if (_ev.table) { out.bounces.push(_ev.table); if (!out.first) out.first = { x: _sb.x, z: _sb.z, t }; }
    if (stopZ !== undefined && !out.hitPlane && prevZ !== _sb.z && (prevZ - stopZ) * (_sb.z - stopZ) <= 0) out.hitPlane = { x: _sb.x, y: _sb.y, z: _sb.z, t };
    prevZ = _sb.z;
    if (_ev.floor) break;
  }
  return out;
}
