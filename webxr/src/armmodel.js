// Modelo de braço para controles 3DoF (só orientação): estima a posição da mão a partir da cabeça + rotação do controle.
// Mão = ombro + (quaternion do controle) * (0,0,-comprimento). Sem dependências (arrays [x,y,z], quaternion [x,y,z,w]).

export function rotateByQuat(q, v) {
  const [x, y, z, w] = q, [vx, vy, vz] = v;
  const tx = 2 * (y * vz - z * vy), ty = 2 * (z * vx - x * vz), tz = 2 * (x * vy - y * vx);
  return [vx + w * tx + (y * tz - z * ty), vy + w * ty + (z * tx - x * tz), vz + w * tz + (x * ty - y * tx)];
}

/** yaw (rad) da direção para a qual a cabeça aponta (em torno de Y). */
export function yawOf(q) {
  const f = rotateByQuat(q, [0, 0, -1]);
  return Math.atan2(-f[0], -f[2]);
}

/**
 * @param head  posição da cabeça (relativa ao chão) [x,y,z]
 * @param headQ quaternion da cabeça
 * @param ctrlQ quaternion do controle
 * @param side  -1 esquerda, +1 direita
 * @param opts  { reach: comprimento do braço (m), shoulderDrop, shoulderWidth }
 */
export function handFromOrientation(head, headQ, ctrlQ, side, opts = {}) {
  const reach = opts.reach ?? 0.8, drop = opts.shoulderDrop ?? 0.22, width = opts.shoulderWidth ?? 0.2;
  const yaw = yawOf(headQ);
  const c = Math.cos(yaw), s = Math.sin(yaw);
  // ombro: deslocamento lateral no referencial do corpo (yaw da cabeça)
  const sx = side * width, sz = 0.0;
  const shoulder = [head[0] + c * sx + s * sz, head[1] - drop, head[2] - s * sx + c * sz];
  const d = rotateByQuat(ctrlQ, [0, 0, -1]);
  return [shoulder[0] + d[0] * reach, shoulder[1] + d[1] * reach, shoulder[2] + d[2] * reach];
}

/** Segunda mão espelhada (quando só há um controle, como no G2): espelha em relação ao plano sagital da cabeça. */
export function mirrorHand(head, headQ, hand) {
  const yaw = yawOf(headQ), c = Math.cos(yaw), s = Math.sin(yaw);
  const dx = hand[0] - head[0], dz = hand[2] - head[2];
  const lx = c * dx - s * dz;          // componente lateral (direita +) no corpo
  const lz = s * dx + c * dz;
  const mx = -lx;
  return [head[0] + c * mx + s * lz, hand[1], head[2] - s * mx + c * lz];
}
