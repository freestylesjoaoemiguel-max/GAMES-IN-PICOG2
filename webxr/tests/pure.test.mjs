import test from 'node:test';
import assert from 'node:assert/strict';
import { HandState, Bindings, describeHand } from '../src/inputmap.js';
import { handFromOrientation, mirrorHand, rotateByQuat, yawOf } from '../src/armmodel.js';
import { ArmLoco, pushOutSphere } from '../src/loco.js';

const store = () => { const m = new Map(); return { getItem: (k) => m.get(k) ?? null, setItem: (k, v) => m.set(k, v) }; };
const snap = (buttons, axes, extra = {}) => ({ handedness: 'right', profiles: ['pico-g2'], gamepad: { mapping: '', buttons: buttons.map((p) => ({ pressed: !!p, touched: false, value: p ? 1 : 0 })), axes }, ...extra });

test('detecta o par de eixos ativo (touchpad no par 0 ou analógico no par 1) e aplica deadzone', () => {
  const h = new HandState(0);
  h.update(snap([0, 0, 0], [0, 0, 0.9, -0.9]));
  assert.equal(h.pair, 1);
  assert.ok(h.stick.mag > 0.9 && h.stick.x > 0.5 && h.stick.y < -0.5);
  h.update(snap([0, 0, 0], [0, 0, 0.05, 0.05]));   // dentro da deadzone: mantém o par, stick zerado
  assert.equal(h.pair, 1); assert.equal(h.stick.mag < 0.18 ? h.stick.x : 1, 0);
  h.update(snap([0, 0, 0], [0.5, 0.5]));
  assert.equal(h.pair, 0);
});

test('bordas de botão: justPressed/justReleased e registro de índices usados', () => {
  const h = new HandState(0);
  h.update(snap([0, 0, 0, 0], []));
  h.update(snap([0, 0, 0, 1], []));
  assert.ok(h.justPressed(3)); assert.ok(!h.justPressed(0)); assert.equal(h.lastPressedIndex, 3);
  h.update(snap([0, 0, 0, 1], []));
  assert.ok(!h.justPressed(3)); assert.ok(h.down(3));
  h.update(snap([0, 0, 0, 0], []));
  assert.ok(h.justReleased(3)); assert.deepEqual([...h.everPressed], [3]);
});

test('bindings: gatilho dispara; select do WebXR também; "alt" = qualquer outro botão; remapeamento persiste', () => {
  const st = store(); const b = new Bindings(st); const h = new HandState(0);
  h.update(snap([1, 0, 0], []));
  assert.ok(b.isDown(h, 'fire'));
  h.update(snap([0, 0, 0], [], { selecting: true }));
  assert.ok(b.isDown(h, 'fire'));
  h.update(snap([0, 0, 0], []));
  h.update(snap([0, 0, 1], []));
  assert.ok(b.justPressed(h, 'alt')); assert.ok(!b.justPressed(h, 'fire'));
  b.bind('menu', 2);
  h.update(snap([0, 0, 0], [])); h.update(snap([0, 0, 1], []));
  assert.ok(b.justPressed(h, 'menu')); assert.ok(!b.justPressed(h, 'alt'));
  assert.deepEqual(new Bindings(st).map.menu, [2]);   // persistiu
});

test('movimento: frente = eixo Y negativo; inversões funcionam', () => {
  const b = new Bindings(store()); const h = new HandState(0);
  h.update(snap([0], [0, -1]));
  assert.ok(b.move(h).y > 0.9);
  b.invertY = true; assert.ok(b.move(h).y < -0.9);
});

test('modelo de braço: apontar para frente/baixo/cima move a mão em arco ao redor do ombro', () => {
  const id = [0, 0, 0, 1], head = [0, 1.6, 0];
  const fwd = handFromOrientation(head, id, id, 1, { reach: 0.8 });
  assert.ok(Math.abs(fwd[2] + 0.8) < 1e-9 && Math.abs(fwd[0] - 0.2) < 1e-9 && Math.abs(fwd[1] - 1.38) < 1e-9);
  const s = Math.SQRT1_2, down = handFromOrientation(head, id, [-s, 0, 0, s], 1, { reach: 0.8 }); // pitch -90°
  assert.ok(Math.abs(down[1] - (1.38 - 0.8)) < 1e-9);
  const up = handFromOrientation(head, id, [s, 0, 0, s], -1, { reach: 0.8 });
  assert.ok(Math.abs(up[1] - (1.38 + 0.8)) < 1e-9 && up[0] < 0);
  // o ombro acompanha o yaw da cabeça
  const yaw90 = [0, s, 0, s];
  assert.ok(Math.abs(yawOf(yaw90) - Math.PI / 2) < 1e-9);
  const m = mirrorHand(head, id, [0.3, 1.2, -0.5]); assert.ok(Math.abs(m[0] + 0.3) < 1e-9 && m[2] === -0.5 && m[1] === 1.2);
  assert.deepEqual(rotateByQuat(id, [1, 2, 3]), [1, 2, 3]);
});

const ground = { min: [-50, -5, -50], max: [50, 0, 50] };

test('pushOutSphere: empurra para fora do chão e das paredes; centro dentro da caixa sai pela face mais próxima', () => {
  const p = pushOutSphere([0, 0.02, 0], 0.07, [ground]);
  assert.ok(Math.abs(p[1] - 0.05) < 1e-6 && p[0] === 0);
  assert.deepEqual(pushOutSphere([0, 1, 0], 0.07, [ground]), [0, 0, 0]);
  const inside = pushOutSphere([0, -0.1, 0], 0.07, [ground]);
  assert.ok(inside[1] > 0.1);
});

test('locomoção: gravidade leva o corpo ao chão e o mantém em pé (cabeça a ~1.6 m)', () => {
  const l = new ArmLoco([ground]); l.teleport([0, 3, 0]);
  const head = [0, 1.6, 0], hands = [[-0.2, 1.3, -0.3], [0.2, 1.3, -0.3]];
  for (let i = 0; i < 300; i++) l.update(1 / 72, head, hands);
  assert.ok(l.grounded); assert.ok(l.pos[1] < 0.5 && l.pos[1] > -0.01, 'y=' + l.pos[1]);
});

test('locomoção: empurrar o chão com a mão move o corpo no sentido oposto; soltar lança', () => {
  const l = new ArmLoco([ground]); l.teleport([0, 0, 0]);
  const head = [0, 1.6, 0];
  for (let i = 0; i < 30; i++) l.update(1 / 72, head, [[-0.2, 1.3, -0.3], [0.2, 1.3, -0.3]]);
  const z0 = l.pos[2];
  // mão direita encosta no chão à frente e puxa para trás (z cresce no referencial local) => corpo vai para frente (-z)
  let hz = -0.5;
  for (let i = 0; i < 20; i++) { hz += 0.025; l.update(1 / 72, head, [[-0.2, 1.3, -0.3], [0.2, 0.03, hz]]); }
  assert.ok(l.touching[1] || l.was[1]);
  assert.ok(l.pos[2] < z0 - 0.15, 'moveu ' + (l.pos[2] - z0));
  // soltar com velocidade => ganha velocidade para frente e sobe/sai do chão
  l.update(1 / 72, head, [[-0.2, 1.3, -0.3], [0.2, 0.9, hz]]);
  assert.ok(l.vel[2] < -0.5, 'vel ' + l.vel);
  assert.ok(Math.hypot(...l.vel) <= 6.5 + 1e-6);
});

test('locomoção: não atravessa paredes e ignora saltos absurdos de rastreio', () => {
  const wall = { min: [-0.5, 0, -3], max: [0.5, 6, -2] };
  const l = new ArmLoco([ground, wall]); l.teleport([0, 0, 0]);
  for (let i = 0; i < 20; i++) l.update(1 / 72, [0, 1.6, 0], [[-0.2, 1.3, -0.3], [0.2, 1.3, -0.3]]);
  l.vel = [0, 0, -8];
  for (let i = 0; i < 120; i++) l.update(1 / 72, [0, 1.6, 0], [[-0.2, 1.3, -0.3], [0.2, 1.3, -0.3]]);
  assert.ok(l.pos[2] > -2 - 0.3 - 1e-3, 'z=' + l.pos[2]);
  const before = [...l.pos];
  l.update(1 / 72, [0, 1.6, 0], [[-0.2, 1.3, -0.3], [0.2, -3, 3]]);
  assert.ok(Math.hypot(l.pos[0] - before[0], l.pos[2] - before[2]) < 0.7);
});

test('describeHand lista botões e eixos reais', () => {
  const h = new HandState(1); h.update(snap([1, 0, 1], [0.1, -0.9]));
  const s = describeHand(h);
  assert.match(s, /botões\(3\): 0●1\.00 1· 2●1\.00/); assert.match(s, /eixos\(2\)/); assert.match(s, /pico-g2/);
});
