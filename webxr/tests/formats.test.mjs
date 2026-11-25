import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import zlib from 'node:zlib';
import { ApkReader, listEnvironments } from '../src/apk.js';
import { parseScene, readMesh, worldMatrices } from '../src/scene.js';
import { parseKtx, limitMips } from '../src/ktx.js';
import { sceneUnitScale } from '../src/modes/viewer.js';
import { rayBox, raySphere, waveSize } from '../src/modes/fps.js';
import { buildWorld, groundAt, blocked, STEP_UP } from '../src/modes/pegapega.js';

// ---------- ZIP sintético (stored + deflate) ----------
function crc32(b) { let c, crc = 0xffffffff; for (let i = 0; i < b.length; i++) { c = (crc ^ b[i]) & 0xff; for (let k = 0; k < 8; k++) c = c & 1 ? (c >>> 1) ^ 0xedb88320 : c >>> 1; crc = (crc >>> 8) ^ c; } return (crc ^ 0xffffffff) >>> 0; }
function makeZip(files) {
  const parts = [], cds = []; let off = 0;
  for (const [name, data, deflate] of files) {
    const nb = Buffer.from(name), raw = Buffer.from(data), body = deflate ? zlib.deflateRawSync(raw) : raw;
    const h = Buffer.alloc(30); h.writeUInt32LE(0x04034b50, 0); h.writeUInt16LE(20, 4); h.writeUInt16LE(deflate ? 8 : 0, 8); h.writeUInt32LE(crc32(raw), 14); h.writeUInt32LE(body.length, 18); h.writeUInt32LE(raw.length, 22); h.writeUInt16LE(nb.length, 26);
    const c = Buffer.alloc(46); c.writeUInt32LE(0x02014b50, 0); c.writeUInt16LE(20, 4); c.writeUInt16LE(20, 6); c.writeUInt16LE(deflate ? 8 : 0, 10); c.writeUInt32LE(crc32(raw), 16); c.writeUInt32LE(body.length, 20); c.writeUInt32LE(raw.length, 24); c.writeUInt16LE(nb.length, 28); c.writeUInt32LE(off, 42);
    cds.push(Buffer.concat([c, nb])); parts.push(h, nb, body); off += 30 + nb.length + body.length;
  }
  const cd = Buffer.concat(cds), e = Buffer.alloc(22); e.writeUInt32LE(0x06054b50, 0); e.writeUInt16LE(files.length, 8); e.writeUInt16LE(files.length, 10); e.writeUInt32LE(cd.length, 12); e.writeUInt32LE(off, 16);
  return new Blob([...parts, cd, e]);
}

test('ApkReader lê entradas stored e deflate por fatias e lista ambientes', async () => {
  const zip = makeZip([['assets/env/foo/scene.bin', 'SCENE'], ['assets/env/foo/a.ktx', 'KTX'], ['assets/env/foo/ambient.ogg', 'OGG'], ['assets/env/vazio/x.ktx', 'x'], ['res/texto.txt', 'ola ola ola ola ola ola', true]]);
  const apk = await ApkReader.open(zip);
  assert.equal(Buffer.from(await apk.arrayBuffer('assets/env/foo/scene.bin')).toString(), 'SCENE');
  assert.equal(Buffer.from(await apk.arrayBuffer('res/texto.txt')).toString(), 'ola ola ola ola ola ola');
  const envs = listEnvironments(apk);
  assert.deepEqual(envs.map((e) => e.name), ['foo']);                // 'vazio' não tem scene.bin
  assert.deepEqual(envs[0].files, ['a.ktx', 'ambient.ogg', 'scene.bin']);
  await assert.rejects(() => apk.arrayBuffer('nao/existe'), /não encontrado/);
  await assert.rejects(() => ApkReader.open(new Blob(['isto nao e zip, de jeito nenhum, mesmo'])), /ZIP|pequeno/);
});

// ---------- V40S sintético ----------
function makeScene({ headerWords = 5, extra = false } = {}) {
  const w32 = (n) => { const b = Buffer.alloc(4); b.writeUInt32LE(n >>> 0); return b; }, f32 = (n) => { const b = Buffer.alloc(4); b.writeFloatLE(n); return b; }, i32 = (n) => { const b = Buffer.alloc(4); b.writeInt32LE(n); return b; };
  const node = (parent, pos, mesh, scale = [1, 1, 1]) => Buffer.concat([i32(parent), ...pos.map(f32), f32(0), f32(0), f32(0), f32(1), ...scale.map(f32), i32(mesh), i32(-1)]);
  const mesh = (mat) => {
    const verts = [[0, 0, 0, 0, 0], [1, 0, 0, 1, 0], [0, 1, 0, 0, 1]];
    const hdr = headerWords === 5 ? [1, mat, 3, 3, 0] : [1, 3, 3, 0];
    return Buffer.concat([...hdr.map(w32), ...verts.flat().map(f32), w32(0), w32(1), w32(2)]);
  };
  const name = Buffer.from('tex_a.ktx');
  return Buffer.concat([Buffer.from('V40S'), w32(2), f32(100), w32(2),
    node(-1, [1, 2, 3], 0, [2, 2, 2]), node(0, [0, 1, 0], -1),
    w32(1 + (extra ? 1 : 0)), mesh(0), ...(extra ? [mesh(0)] : []),
    w32(1), w32(0), w32(1), f32(1), f32(0.5), f32(0.25), f32(1),
    w32(1), w32(name.length), name, Buffer.from('resto-desconhecido')]);
}
for (const [label, opts] of [['cabeçalho de 5 palavras', {}], ['variante de 4 palavras', { headerWords: 4 }], ['malha extra antes dos materiais', { extra: true }]]) {
  test('parseScene: ' + label, () => {
    const b = makeScene(opts); const s = parseScene(b.buffer.slice(b.byteOffset, b.byteOffset + b.byteLength));
    assert.equal(s.nodes.length, 2); assert.equal(s.meshes.length, opts.extra ? 2 : 1);
    assert.deepEqual(s.textureNames, ['tex_a.ktx']); assert.equal(s.materials[0].flags, 1);
    assert.ok(Math.abs(s.materials[0].color[0] - 1) < 1e-6 && Math.abs(s.materials[0].color[1] - 0.5) < 1e-6);
    const d = readMesh(s, s.meshes[0]); assert.deepEqual([...d.idx], [0, 1, 2]); assert.deepEqual([...d.pos.slice(3, 6)], [1, 0, 0]);
    const W = worldMatrices(s.nodes);
    assert.deepEqual([...W[0].slice(12, 15)], [1, 2, 3]); assert.equal(W[0][0], 2);
    assert.deepEqual([...W[1].slice(12, 15)], [1, 2 + 2, 3]);      // filho herda posição e escala do pai
  });
}
test('parseScene rejeita lixo e versões desconhecidas', () => {
  assert.throws(() => parseScene(new ArrayBuffer(64)), /V40S/);
  const b = makeScene(); b.writeUInt32LE(9, 4); assert.throws(() => parseScene(b.buffer.slice(b.byteOffset, b.byteOffset + b.byteLength)), /versão/);
});

// ---------- KTX sintético ----------
function makeKtx(w, h, levels, fmt = 0x93b7) {
  const hd = Buffer.alloc(64); Buffer.from([0xab, 0x4b, 0x54, 0x58, 0x20, 0x31, 0x31, 0xbb, 0x0d, 0x0a, 0x1a, 0x0a]).copy(hd);
  hd.writeUInt32LE(0x04030201, 12); hd.writeUInt32LE(fmt, 28); hd.writeUInt32LE(w, 36); hd.writeUInt32LE(h, 40); hd.writeUInt32LE(1, 52); hd.writeUInt32LE(levels, 56);
  const parts = [hd];
  for (let l = 0; l < levels; l++) { const lw = Math.max(1, w >> l), lh = Math.max(1, h >> l), size = Math.ceil(lw / 8) * Math.ceil(lh / 8) * 16; const s = Buffer.alloc(4); s.writeUInt32LE(size); parts.push(s, Buffer.alloc(size, l + 1)); }
  return Buffer.concat(parts);
}
test('parseKtx lê níveis ASTC 8x8 e limitMips descarta os maiores', () => {
  const b = makeKtx(256, 128, 4); const k = parseKtx(b.buffer.slice(b.byteOffset, b.byteOffset + b.byteLength));
  assert.deepEqual(k.block, [8, 8]); assert.equal(k.mips.length, 4); assert.equal(k.mips[0].width, 256); assert.equal(k.mips[0].data.length, 32 * 16 * 16);
  const m = limitMips(k, 64); assert.equal(m[0].width, 64); assert.equal(m.length, 2);
  assert.equal(limitMips(k, 1).length, 1); assert.equal(limitMips(k, 1)[0].width, 32);   // nunca descarta o último nível
  assert.throws(() => parseKtx(new ArrayBuffer(128)), /assinatura/);
  const bad = makeKtx(64, 64, 1, 0x8058); assert.throws(() => parseKtx(bad.buffer.slice(bad.byteOffset, bad.byteOffset + bad.byteLength)), /ASTC/);
});

test('escala do cenário: cenas enormes estão em centímetros', () => {
  assert.equal(sceneUnitScale(173), 1); assert.equal(sceneUnitScale(1727), 1); assert.equal(sceneUnitScale(13094), 0.01); assert.equal(sceneUnitScale(41524), 0.01);
});

// ---------- jogos: funções puras ----------
test('rayBox / raySphere', () => {
  const b = { min: [-1, 0, -5], max: [1, 2, -3] };
  assert.ok(Math.abs(rayBox([0, 1, 0], [0, 0, -1], b) - 3) < 1e-9);
  assert.equal(rayBox([5, 1, 0], [0, 0, -1], b), -1);
  assert.equal(rayBox([0, 1, 0], [0, 0, -1], b, 2), -1);
  assert.ok(Math.abs(raySphere([0, 0, 0], [0, 0, -1], [0, 0, -4], 1) - 3) < 1e-9);
  assert.equal(raySphere([0, 0, 0], [0, 0, -1], [3, 0, -4], 1), -1);
  assert.ok(waveSize(1) < waveSize(5) && waveSize(100) === 14);
});
test('Pega-Pega: mundo, degraus e bloqueio para os bots', () => {
  const w = buildWorld(7); assert.ok(w.boxes.length > 50);
  assert.equal(groundAt(w.boxes, 55, 55, 0) <= 0.01, true);
  const tower = groundAt(w.boxes, 0, 0, 5);        // topo da torre central visto de cima
  assert.ok(tower > 3 && tower <= 5.4 + 1e-9, 'torre ' + tower);
  assert.equal(groundAt(w.boxes, 0, 0, 0) <= STEP_UP, true);   // do chão só alcança degraus <= STEP_UP
  assert.equal(blocked([{ min: [0, 0, 0], max: [1, 10, 1] }], 0.5, 0.5, 0), true);
  assert.equal(blocked([{ min: [0, 0, 0], max: [1, 1, 1] }], 0.5, 0.5, 0), false);   // baixo: sobe
  assert.deepEqual(buildWorld(7).boxes, buildWorld(7).boxes);                         // determinístico
});

// ---------- dados reais (opcional): METULAS_ENV_DIR=.../assets/env ----------
const ENV = process.env.METULAS_ENV_DIR;
test('cenas reais do APK: as 7 abrem, índices válidos e texturas ASTC legíveis', { skip: !ENV || !fs.existsSync(ENV) }, () => {
  const names = fs.readdirSync(ENV); assert.equal(names.length, 7);
  for (const n of names) {
    const b = fs.readFileSync(`${ENV}/${n}/scene.bin`); const s = parseScene(b.buffer.slice(b.byteOffset, b.byteOffset + b.byteLength));
    assert.ok(s.meshes.length > 10 && s.nodes.length > 10, n);
    for (const t of s.textureNames) { const kb = fs.readFileSync(`${ENV}/${n}/${t}`); const k = parseKtx(kb.buffer.slice(kb.byteOffset, kb.byteOffset + kb.byteLength)); assert.ok(k.mips.length >= 5, t); }
    for (const mt of s.materials) assert.ok(mt.texture < s.textureNames.length);
  }
});
