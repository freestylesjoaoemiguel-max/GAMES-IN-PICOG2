"""Fast local source regression preview using the last CI-imported assets.
Does NOT replace release export: final release must still go through Godot CI.
"""
import hashlib,struct,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT.parent/'.cache/gila-preview'

def unpack(data):
    assert data[:4]==b'GDPC'
    assert struct.unpack_from('<I',data,4)[0]==2
    base=struct.unpack_from('<Q',data,24)[0]
    count=struct.unpack_from('<I',data,96)[0]
    files={};pos=100
    for _ in range(count):
        n=struct.unpack_from('<I',data,pos)[0];pos+=4
        name=data[pos:pos+n].rstrip(b'\0').decode();pos+=n
        off,size=struct.unpack_from('<QQ',data,pos);pos+=16
        digest=data[pos:pos+16];pos+=16
        flags=struct.unpack_from('<I',data,pos)[0];pos+=4
        assert flags==0
        contents=data[base+off:base+off+size]
        assert hashlib.md5(contents).digest()==digest,name
        files[name]=contents
    return files

def pack(files):
    table=bytearray();payload=bytearray()
    for name,data in sorted(files.items()):
        path=name.encode();path+=bytes((-len(path))%4)
        table+=struct.pack('<I',len(path))+path+struct.pack('<QQ',len(payload),len(data))+hashlib.md5(data).digest()+struct.pack('<I',0)
        payload+=data;payload+=bytes((-len(payload))%16)
    base=(100+len(table)+15)//16*16
    return b'GDPC'+struct.pack('<5IQ',2,4,4,1,0,base)+bytes(64)+struct.pack('<I',len(files))+table+bytes(base-100-len(table))+payload

if __name__=='__main__':
    OUT.mkdir(parents=True,exist_ok=True)
    files=unpack((ROOT/'web/game.pck').read_bytes())
    for source in (ROOT/'source').rglob('*.gd'):
        name=source.relative_to(ROOT/'source').as_posix()
        files[name]=source.read_bytes();files.pop(name+'.remap',None)
    result=pack(files);assert unpack(result)==files
    (OUT/'game.pck').write_bytes(result)
    import shutil
    for file in (ROOT/'web').rglob('*'):
        if not file.is_file() or file.name=='game.pck':continue
        dest=OUT/file.relative_to(ROOT/'web');dest.parent.mkdir(parents=True,exist_ok=True)
        shutil.copyfile(file,dest)
    import re
    html=(ROOT/'web/game.html').read_text().replace('<body>','<body>\n<script src="vendor/webxr-layers-polyfill.js"></script>\n<script src="controls.js"></script>')
    html=re.sub(r'("game.pck":)\d+',r'\g<1>'+str(len(result)),html)
    (OUT/'index.html').write_text(html)
    print(OUT)
