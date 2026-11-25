// Parser do formato "V40S" (versão 2) dos ambientes. Formato deduzido dos dados (sem documentação):
//
//  header  : "V40S" | u32 versão=2 | f32 raio da cena | u32 nNós
//  nós     : nNós x 52 bytes = i32 pai, f32 pos[3], f32 quat[4](x,y,z,w), f32 escala[3], i32 malha, i32 (animação?)
//  malhas  : u32 nMalhas, depois cada uma:
//              cabeçalho 5 palavras u32 [fmt, material, nVert, nIdx, tipo]  (variante de 4 palavras sem material [fmt,nVert,nIdx,tipo])
//              vértices: tipo 0 -> 20 bytes (pos f32x3, uv f32x2); tipo 1 -> 52 bytes (pos, uv, 8 floats extras de sprite)
//              índices : nIdx x u32
//  materiais: u32 n, n x (u32 indiceTextura, u32 flags, f32 cor[4])
//  texturas : u32 n, n x (u32 tamanho, bytes do nome .ktx)
//  resto    : dados que não são usados aqui (provavelmente animações)

const rd = (dv, o) => dv.getUint32(o, true);

function tryMesh(dv, len, off, headerWords) {
  if (off + headerWords * 4 > len) return null;
  let fmt, material, nv, ni, type;
  if (headerWords === 5) { fmt = rd(dv, off); material = rd(dv, off + 4); nv = rd(dv, off + 8); ni = rd(dv, off + 12); type = rd(dv, off + 16); }
  else { fmt = rd(dv, off); material = -1; nv = rd(dv, off + 4); ni = rd(dv, off + 8); type = rd(dv, off + 12); }
  if ((fmt !== 1 && fmt !== 2) || (type !== 0 && type !== 1) || nv === 0 || ni === 0 || ni % 3 !== 0 || nv > 4e6 || ni > 12e6) return null;
  const stride = type === 1 ? 52 : 20;
  const vo = off + headerWords * 4, io = vo + nv * stride, end = io + ni * 4;
  if (end > len) return null;
  // valida índices (amostra dos extremos + varredura completa, é barato)
  let max = 0;
  for (let i = 0; i < ni; i++) { const v = dv.getUint32(io + i * 4, true); if (v > max) max = v; }
  if (max >= nv) return null;
  return { fmt, material, nv, ni, type, stride, vo, io, end, headerWords };
}

function tryTail(dv, len, off) {
  try {
    let o = off;
    const mc = rd(dv, o); o += 4;
    if (mc < 1 || mc > 512 || o + mc * 24 > len) return null;
    const materials = [];
    for (let i = 0; i < mc; i++) {
      materials.push({ texture: rd(dv, o), flags: rd(dv, o + 4), color: [dv.getFloat32(o + 8, true), dv.getFloat32(o + 12, true), dv.getFloat32(o + 16, true), dv.getFloat32(o + 20, true)] });
      o += 24;
    }
    const tc = rd(dv, o); o += 4;
    if (tc < 1 || tc > 512) return null;
    const names = [];
    const dec = new TextDecoder('latin1');
    for (let i = 0; i < tc; i++) {
      const l = rd(dv, o); o += 4;
      if (l < 1 || l > 255 || o + l > len) return null;
      const s = dec.decode(new Uint8Array(dv.buffer, dv.byteOffset + o, l));
      if (!/\.ktx$/i.test(s)) return null;
      names.push(s); o += l;
    }
    if (materials.some((m) => m.texture >= tc)) return null;
    return { materials, textureNames: names, end: o };
  } catch (e) { return null; }
}

export function parseScene(buffer) {
  const dv = new DataView(buffer), len = buffer.byteLength;
  if (len < 16 || rd(dv, 0) !== 0x53303456) throw new Error('scene.bin: assinatura "V40S" não encontrada.');
  const version = rd(dv, 4);
  if (version !== 2) throw new Error('scene.bin: versão ' + version + ' não suportada (só 2).');
  const radius = dv.getFloat32(8, true);
  const nNodes = rd(dv, 12);
  if (nNodes < 1 || 16 + nNodes * 52 + 4 > len) throw new Error('scene.bin: tabela de nós inválida.');
  const nodes = [];
  for (let i = 0; i < nNodes; i++) {
    const o = 16 + i * 52;
    nodes.push({
      parent: dv.getInt32(o, true),
      pos: [dv.getFloat32(o + 4, true), dv.getFloat32(o + 8, true), dv.getFloat32(o + 12, true)],
      quat: [dv.getFloat32(o + 16, true), dv.getFloat32(o + 20, true), dv.getFloat32(o + 24, true), dv.getFloat32(o + 28, true)],
      scale: [dv.getFloat32(o + 32, true), dv.getFloat32(o + 36, true), dv.getFloat32(o + 40, true)],
      mesh: dv.getInt32(o + 44, true),
      anim: dv.getInt32(o + 48, true),
    });
  }
  let off = 16 + nNodes * 52;
  const nMeshes = rd(dv, off); off += 4;
  const meshes = [];
  for (let m = 0; m < nMeshes; m++) {
    const r = tryMesh(dv, len, off, 5) || tryMesh(dv, len, off, 4);
    if (!r) throw new Error('scene.bin: malha ' + m + ' inválida (offset ' + off + ').');
    meshes.push(r); off = r.end;
  }
  // a tabela de materiais vem a seguir; algumas cenas têm malhas extras não referenciadas antes dela
  let tail = null, extra = 0;
  for (; extra < 6 && !tail; extra++) {
    tail = tryTail(dv, len, off);
    if (tail) break;
    const r = tryMesh(dv, len, off, 5) || tryMesh(dv, len, off, 4);
    if (!r) break;
    off = r.end;
  }
  if (!tail) throw new Error('scene.bin: tabela de materiais não encontrada.');
  return { version, radius, nodes, meshes, materials: tail.materials, textureNames: tail.textureNames, extraMeshes: extra, dataEnd: tail.end, view: dv };
}

/** Lê os vértices de uma malha: positions (Float32Array xyz), uvs (Float32Array uv), indices (Uint32Array). */
export function readMesh(scene, mesh) {
  const dv = scene.view;
  const pos = new Float32Array(mesh.nv * 3), uv = new Float32Array(mesh.nv * 2);
  for (let i = 0; i < mesh.nv; i++) {
    const o = mesh.vo + i * mesh.stride;
    pos[i * 3] = dv.getFloat32(o, true); pos[i * 3 + 1] = dv.getFloat32(o + 4, true); pos[i * 3 + 2] = dv.getFloat32(o + 8, true);
    uv[i * 2] = dv.getFloat32(o + 12, true); uv[i * 2 + 1] = dv.getFloat32(o + 16, true);
  }
  const idx = new Uint32Array(mesh.ni);
  for (let i = 0; i < mesh.ni; i++) idx[i] = dv.getUint32(mesh.io + i * 4, true);
  return { pos, uv, idx };
}

/** Matrizes mundo (4x4 coluna-maior, Float32Array(16)) de todos os nós, resolvendo a hierarquia em qualquer ordem. */
export function worldMatrices(nodes) {
  const mats = new Array(nodes.length);
  const local = (n) => {
    const [x, y, z, w] = n.quat, [sx, sy, sz] = n.scale;
    const xx = x * x, yy = y * y, zz = z * z, xy = x * y, xz = x * z, yz = y * z, wx = w * x, wy = w * y, wz = w * z;
    return new Float64Array([
      (1 - 2 * (yy + zz)) * sx, 2 * (xy + wz) * sx, 2 * (xz - wy) * sx, 0,
      2 * (xy - wz) * sy, (1 - 2 * (xx + zz)) * sy, 2 * (yz + wx) * sy, 0,
      2 * (xz + wy) * sz, 2 * (yz - wx) * sz, (1 - 2 * (xx + yy)) * sz, 0,
      n.pos[0], n.pos[1], n.pos[2], 1,
    ]);
  };
  const mul = (a, b) => {
    const o = new Float64Array(16);
    for (let c = 0; c < 4; c++) for (let r = 0; r < 4; r++) {
      o[c * 4 + r] = a[r] * b[c * 4] + a[4 + r] * b[c * 4 + 1] + a[8 + r] * b[c * 4 + 2] + a[12 + r] * b[c * 4 + 3];
    }
    return o;
  };
  const state = new Uint8Array(nodes.length);
  const get = (i) => {
    if (mats[i]) return mats[i];
    if (state[i] === 1) throw new Error('scene.bin: ciclo na hierarquia de nós.');
    state[i] = 1;
    const l = local(nodes[i]);
    const p = nodes[i].parent;
    mats[i] = p >= 0 && p < nodes.length ? mul(get(p), l) : l;
    state[i] = 2;
    return mats[i];
  };
  for (let i = 0; i < nodes.length; i++) get(i);
  return mats.map((m) => Float32Array.from(m));
}
