// Leitor de KTX 1.1 com texturas ASTC comprimidas (o que o APK usa). Devolve os níveis de mip crus para o WebGL.

// GL internalFormat -> bloco ASTC (largura, altura). Valores iguais aos enums do three.js (RGBA_ASTC_*_Format).
const ASTC = {
  0x93b0: [4, 4], 0x93b1: [5, 4], 0x93b2: [5, 5], 0x93b3: [6, 5], 0x93b4: [6, 6], 0x93b5: [8, 5], 0x93b6: [8, 6],
  0x93b7: [8, 8], 0x93b8: [10, 5], 0x93b9: [10, 6], 0x93ba: [10, 8], 0x93bb: [10, 10], 0x93bc: [12, 10], 0x93bd: [12, 12],
};
const MAGIC = [0xab, 0x4b, 0x54, 0x58, 0x20, 0x31, 0x31, 0xbb, 0x0d, 0x0a, 0x1a, 0x0a];

export function parseKtx(buffer) {
  const u8 = new Uint8Array(buffer);
  if (u8.length < 64) throw new Error('KTX: arquivo curto.');
  for (let i = 0; i < 12; i++) if (u8[i] !== MAGIC[i]) throw new Error('KTX: assinatura inválida (só KTX 1.1).');
  const dv = new DataView(buffer);
  if (dv.getUint32(12, true) !== 0x04030201) throw new Error('KTX: endianness não suportada.');
  const glInternalFormat = dv.getUint32(28, true);
  const width = dv.getUint32(36, true), height = dv.getUint32(40, true);
  const faces = dv.getUint32(52, true), levels = Math.max(1, dv.getUint32(56, true)), kv = dv.getUint32(60, true);
  if (faces !== 1) throw new Error('KTX: cubemaps não suportados.');
  const block = ASTC[glInternalFormat];
  if (!block) throw new Error('KTX: formato 0x' + glInternalFormat.toString(16) + ' não suportado (esperado ASTC).');
  let o = 64 + kv;
  const mips = [];
  for (let l = 0; l < levels; l++) {
    if (o + 4 > u8.length) throw new Error('KTX: dados truncados no nível ' + l);
    const size = dv.getUint32(o, true); o += 4;
    const w = Math.max(1, width >> l), h = Math.max(1, height >> l);
    const expect = Math.ceil(w / block[0]) * Math.ceil(h / block[1]) * 16;
    if (size !== expect) throw new Error('KTX: tamanho do nível ' + l + ' (' + size + ') != esperado (' + expect + ').');
    if (o + size > u8.length) throw new Error('KTX: dados truncados no nível ' + l);
    mips.push({ data: u8.subarray(o, o + size), width: w, height: h });
    o += size + ((4 - (size % 4)) % 4);
  }
  return { glInternalFormat, block, width, height, mips };
}

/** Descarta os níveis maiores que maxSize (economiza memória/banda de GPU no Pico G2). */
export function limitMips(ktx, maxSize) {
  let skip = 0;
  while (skip < ktx.mips.length - 1 && Math.max(ktx.mips[skip].width, ktx.mips[skip].height) > maxSize) skip++;
  return ktx.mips.slice(skip);
}
