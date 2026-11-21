"""Convert legally sourced CC0 GLBs to compact, static, vertex-colored stage props.
Usage: .venv/bin/python pulse-saber-g2/tools/prepare_models.py
Sources and exact hashes are recorded in docs/ASSETS.json. Requires numpy 2.2.6.
No textures, animation, skeleton or vendor runtime are shipped; originals stay in .cache.
"""
from pathlib import Path
import json,struct,hashlib
import numpy as np
ROOT=Path(__file__).resolve().parents[1]
CACHE=ROOT.parent/'.cache/pulse-models'
def convert(name):
 b=(CACHE/(name+'.glb')).read_bytes(); n=struct.unpack_from('<I',b,12)[0];d=json.loads(b[20:20+n]);off=20+n;length=struct.unpack_from('<I',b,off)[0];raw=b[off+8:off+8+length]
 def access(index):
  a=d['accessors'][index];v=d['bufferViews'][a['bufferView']];dtype={5126:'<f4',5125:'<u4',5123:'<u2',5121:'u1'}[a['componentType']];size={'SCALAR':1,'VEC2':2,'VEC3':3,'VEC4':4,'MAT4':16}[a['type']];dt=np.dtype(dtype);stride=v.get('byteStride',size*dt.itemsize);return np.ndarray((a['count'],size),dtype=dt,buffer=raw,offset=v.get('byteOffset',0)+a.get('byteOffset',0),strides=(stride,dt.itemsize)).copy()
 verts=[];colors=[]
 def node(i,parent):
  o=d['nodes'][i]
  if 'matrix' in o:m=np.array(o['matrix']).reshape(4,4).T
  else:
   x,y,z,w=o.get('rotation',[0,0,0,1]);m=np.eye(4);m[:3,:3]=[[1-2*y*y-2*z*z,2*x*y-2*z*w,2*x*z+2*y*w],[2*x*y+2*z*w,1-2*x*x-2*z*z,2*y*z-2*x*w],[2*x*z-2*y*w,2*y*z+2*x*w,1-2*x*x-2*y*y]];m[:3,:3]=m[:3,:3]@np.diag(o.get('scale',[1,1,1]));m[:3,3]=o.get('translation',[0,0,0])
  m=parent@m
  if 'mesh' in o:
   for p in d['meshes'][o['mesh']]['primitives']:
    if p.get('mode',4)!=4:continue
    pos=access(p['attributes']['POSITION']);pos=(np.c_[pos,np.ones(len(pos))]@m.T)[:,:3];ids=access(p['indices']).ravel() if 'indices' in p else np.arange(len(pos));pos=pos[ids];base=d.get('materials',[{}])[p.get('material',0)].get('pbrMetallicRoughness',{}).get('baseColorFactor',[.5,.6,.7,1])[:3]
    if name=='boombox':base=[.2,.33,.4]
    triangles=pos.reshape(-1,3,3);norm=np.cross(triangles[:,1]-triangles[:,0],triangles[:,2]-triangles[:,0]);norm/=np.maximum(np.linalg.norm(norm,axis=1,keepdims=True),1e-9);light=.55+.45*np.maximum(0,norm@np.array([.3,.8,.5]));col=np.repeat(light[:,None]*np.array(base),3,axis=0);verts.extend(pos.tolist());colors.extend(col.tolist())
  for c in o.get('children',[]):node(c,m)
 for i in d['scenes'][d.get('scene',0)]['nodes']:node(i,np.eye(4))
 v=np.array(verts);lo=v.min(axis=0);hi=v.max(axis=0);v-=[(lo[0]+hi[0])/2,lo[1],(lo[2]+hi[2])/2];v/=max(hi-lo)
 # Store interleaved triangles for a shared GLES/WebGL reader.
 data=np.c_[v,np.array(colors)].round(5).ravel().tolist();p=ROOT/'web/assets'/f'{name}.json';p.write_text(json.dumps({'format':'xyzrgb-triangles-v1','vertices':data},separators=(',',':')))
 return {'input_sha256':hashlib.sha256(b).hexdigest(),'output_sha256':hashlib.sha256(p.read_bytes()).hexdigest(),'triangles':len(v)//3}
result={name:convert(name) for name in ['robot','boombox']}
result['robot'].update(author='Tomás Laulhé (Quaternius), modifications Don McCurdy',license='CC0-1.0',source='https://github.com/mrdoob/three.js/tree/0245b6dad700ecc32f145dfa495c55e41fc75db7/examples/models/gltf/RobotExpressive')
result['boombox'].update(author='Microsoft',license='CC0-1.0',source='https://github.com/KhronosGroup/glTF-Sample-Assets/tree/e124e8d8d7330a5372bc2b4d00d39a4a9d7997ea/Models/BoomBox')
(ROOT/'docs/ASSETS.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
for name in result:(ROOT/'docs'/f'{name}-upstream-license.md').write_text((CACHE/f'{name}-license.md').read_text())
print(result)
