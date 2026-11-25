#!/usr/bin/env python3
"""Só para QA: decodifica as texturas ASTC de um ambiente (pasta extraída do SEU APK) em RGBA reduzido e grava um JSON
que o teste injeta em window.__QA_TEXTURES (o Chromium headless não tem ASTC). Uso: qa_decode.py <assets/env/NOME> <saida.json> [max_px]"""
import sys, os, json, base64, struct
import texture2ddecoder as t2d
BLK = {0x93b0:(4,4),0x93b1:(5,4),0x93b2:(5,5),0x93b3:(6,5),0x93b4:(6,6),0x93b5:(8,5),0x93b6:(8,6),0x93b7:(8,8),0x93b8:(10,5),0x93b9:(10,6),0x93ba:(10,8),0x93bb:(10,10),0x93bc:(12,10),0x93bd:(12,12)}
def decode(path, maxpx):
    b = open(path,'rb').read()
    fmt, = struct.unpack_from('<I', b, 28); w,h = struct.unpack_from('<II', b, 36); lv, kv = struct.unpack_from('<II', b, 56)[0], struct.unpack_from('<I', b, 60)[0]
    bx,by = BLK[fmt]; off = 64+kv; best = None
    for l in range(max(1,lv)):
        size, = struct.unpack_from('<I', b, off); off += 4
        lw, lh = max(1,w>>l), max(1,h>>l)
        if max(lw,lh) <= maxpx:
            raw = t2d.decode_astc(b[off:off+size], lw, lh, bx, by)   # BGRA
            ba = bytearray(raw); ba[0::4], ba[2::4] = raw[2::4], raw[0::4]
            return lw, lh, bytes(ba)
        off += size + ((4-size%4)%4)
    raise RuntimeError('sem nível pequeno: '+path)
def main():
    env, out = sys.argv[1], sys.argv[2]; mx = int(sys.argv[3]) if len(sys.argv) > 3 else 256
    res = {}
    for f in sorted(os.listdir(env)):
        if f.endswith('.ktx'):
            w,h,rgba = decode(os.path.join(env,f), mx); res[f] = {'w':w,'h':h,'rgba':base64.b64encode(rgba).decode()}
    json.dump(res, open(out,'w')); print(env, len(res), 'texturas ->', out)
main()
