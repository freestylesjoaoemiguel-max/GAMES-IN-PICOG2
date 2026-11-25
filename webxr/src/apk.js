// Leitor de APK/ZIP por fatias (Blob.slice): nunca carrega o arquivo inteiro na memória.
// Os assets do APK estão "stored" (sem compressão); entradas deflate pequenas são suportadas via DecompressionStream.

const SIG_EOCD = 0x06054b50, SIG_CEN = 0x02014b50, SIG_LOC = 0x04034b50;

export class ApkReader {
  constructor(blob) { this.blob = blob; this.entries = new Map(); }

  static async open(blob) {
    const r = new ApkReader(blob);
    await r._readDirectory();
    return r;
  }

  async _slice(start, end) { return new DataView(await this.blob.slice(start, end).arrayBuffer()); }

  async _readDirectory() {
    const size = this.blob.size;
    if (size < 22) throw new Error('Arquivo muito pequeno para ser um ZIP/APK.');
    const tailLen = Math.min(size, 65557);
    const tail = await this._slice(size - tailLen, size);
    let eocd = -1;
    for (let i = tailLen - 22; i >= 0; i--) {
      if (tail.getUint32(i, true) === SIG_EOCD) { eocd = i; break; }
    }
    if (eocd < 0) throw new Error('Não é um ZIP/APK válido (fim do diretório não encontrado).');
    const count = tail.getUint16(eocd + 10, true);
    const cdSize = tail.getUint32(eocd + 12, true);
    const cdOff = tail.getUint32(eocd + 16, true);
    if (cdOff === 0xffffffff || count === 0xffff) throw new Error('ZIP64 não suportado.');
    const cd = await this._slice(cdOff, cdOff + cdSize);
    const dec = new TextDecoder('utf-8');
    let p = 0;
    for (let i = 0; i < count; i++) {
      if (cd.getUint32(p, true) !== SIG_CEN) throw new Error('Diretório central corrompido.');
      const method = cd.getUint16(p + 10, true);
      const csize = cd.getUint32(p + 20, true);
      const usize = cd.getUint32(p + 24, true);
      const nlen = cd.getUint16(p + 28, true), xlen = cd.getUint16(p + 30, true), clen = cd.getUint16(p + 32, true);
      const lho = cd.getUint32(p + 42, true);
      const name = dec.decode(new Uint8Array(cd.buffer, cd.byteOffset + p + 46, nlen));
      this.entries.set(name, { name, method, csize, usize, lho });
      p += 46 + nlen + xlen + clen;
    }
  }

  has(name) { return this.entries.has(name); }

  list(prefix = '') {
    const out = [];
    for (const n of this.entries.keys()) if (n.startsWith(prefix)) out.push(n);
    return out;
  }

  async _dataStart(e) {
    const h = await this._slice(e.lho, e.lho + 30);
    if (h.getUint32(0, true) !== SIG_LOC) throw new Error('Cabeçalho local inválido: ' + e.name);
    return e.lho + 30 + h.getUint16(26, true) + h.getUint16(28, true);
  }

  /** Blob da entrada (sem copiar quando stored). */
  async blob_(name) {
    const e = this.entries.get(name);
    if (!e) throw new Error('Arquivo não encontrado no APK: ' + name);
    const start = await this._dataStart(e);
    if (e.method === 0) return this.blob.slice(start, start + e.usize);
    if (e.method === 8) {
      if (typeof DecompressionStream === 'undefined') throw new Error('Este navegador não descompacta deflate: ' + name);
      const stream = this.blob.slice(start, start + e.csize).stream().pipeThrough(new DecompressionStream('deflate-raw'));
      return await new Response(stream).blob();
    }
    throw new Error('Método de compressão não suportado (' + e.method + '): ' + name);
  }

  async arrayBuffer(name) { return (await this.blob_(name)).arrayBuffer(); }
}

/** Lista os ambientes disponíveis: pastas assets/env/<nome>/ que contêm scene.bin. */
export function listEnvironments(apk) {
  const envs = new Map();
  for (const n of apk.entries.keys()) {
    const m = /^assets\/env\/([^/]+)\/(.+)$/.exec(n);
    if (!m) continue;
    if (!envs.has(m[1])) envs.set(m[1], []);
    envs.get(m[1]).push(m[2]);
  }
  return [...envs.entries()].filter(([, files]) => files.includes('scene.bin')).map(([name, files]) => ({ name, files: files.sort() }));
}
