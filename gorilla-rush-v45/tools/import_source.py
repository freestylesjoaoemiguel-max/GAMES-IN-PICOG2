#!/usr/bin/env python3
"""Reproducible unpacking of the supplied HTML-in-TXT. Does not execute source code.
Output is the unpatched JS/HTML baseline; run only in a separate output directory.
"""
from pathlib import Path
import sys,re,base64,hashlib,json,gzip
src=Path(sys.argv[1]);out=Path(sys.argv[2]);out.mkdir(parents=True,exist_ok=True)
b=src.read_bytes();assert hashlib.md5(b).hexdigest()=='66e690a8491beedaaa194a72675f8d9c','Unexpected input revision'
s=b.decode('utf-8');records={};raw=set()
def save(txt,kind):
 data=base64.b64decode(txt,validate=True);sha=hashlib.sha256(data).hexdigest();ext='bin'
 for magic,e in [(b'glTF','glb'),(b'\x89PNG','png'),(b'\xff\xd8','jpg'),(b'OggS','ogg'),(b'OTTO','otf'),(b'ID3','mp3')]:
  if data.startswith(magic):ext=e;break
 if kind.startswith('audio/mpeg') or data[:1]==b'\xff' and ext=='bin':ext='mp3'
 path='assets/'+sha[:20]+'.'+ext
 compressed=data.startswith(b'\x1f\x8b');stored=gzip.decompress(data) if compressed else data
 (out/'assets').mkdir(exist_ok=True);(out/path).write_bytes(stored)
 records[path]={'path':path,'originalSha256':sha,'sha256':hashlib.sha256(stored).hexdigest(),'bytes':len(stored),'gzipExpanded':compressed,'kind':kind}
 if kind=='raw':raw.add(path)
 return path
# First data URLs (CSS / HTML / JS), then raw base64 literals, preserving all decoded payloads.
s=re.sub(r'data:([^;\s]+);base64,([A-Za-z0-9+/=]{500,})',lambda m:save(m[2],m[1]),s)
s=re.sub(r'([\"\'])([A-Za-z0-9+/=]{500,})\1',lambda m:m[1]+save(m[2],'raw')+m[1],s)
s=s.replace("'data:image/png;base64,'+b",'b')
s=re.sub(r'Uint8Array.from\(atob\(([^\n]+?)\),c=>c.charCodeAt\(0\)\)',r'V45.bytes(\1)',s)
s=s.replace('atob(','V45.binary(')
s=s.replace("await new Response(new Blob([by]).stream().pipeThrough(new DecompressionStream('gzip'))).arrayBuffer()",'by.buffer')
# Split inline scripts; defer flags are replaced by an explicit sequential boot loader.
scripts=[]
def split(m):
 attrs,body=m[1],m[2]
 if 'src=' in attrs:return m[0]
 name='js/source-%02d.js'%len(scripts);scripts.append(name);(out/'js').mkdir(exist_ok=True);(out/name).write_text(body)
 return ''
s=re.sub(r'<script([^>]*)>([\s\S]*?)</script>',split,s)
(out/'baseline.html').write_text(s)
(out/'docs').mkdir(exist_ok=True)
(out/'docs/INPUT_PROVENANCE.json').write_text(json.dumps({'sourceName':src.name,'sourceBytes':len(b),'sourceSha256':hashlib.sha256(b).hexdigest(),'sourceMD5':hashlib.md5(b).hexdigest(),'sourceLines':len(b.splitlines()),'assets':list(records.values()),'rawAssets':sorted(raw),'scripts':scripts},indent=2)+'\n')
print(len(records),'distinct assets;',len(scripts),'inline scripts;',sum(r['bytes'] for r in records.values()),'decoded bytes')
