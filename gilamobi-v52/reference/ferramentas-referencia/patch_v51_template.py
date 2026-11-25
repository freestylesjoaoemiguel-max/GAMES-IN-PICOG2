import zipfile,struct,pathlib
src='/home/user/.local/share/godot/export_templates/4.4.1.stable/android_debug.apk'
out='/home/user/tools/v51/android-hand-v51-template.apk'
U=lambda b,o:struct.unpack_from('<I',b,o)[0]
with zipfile.ZipFile(src) as z: d=z.read('AndroidManifest.xml')
assert U(d,0)==0x00080003 and U(d,8)==0x001c0001
p=8; oldsize=U(d,p+4);count=U(d,p+8);flags=U(d,p+16);start=U(d,p+20); assert flags==0 and U(d,p+12)==0
strings=[]
for i in range(count):
 at=p+start+U(d,p+28+4*i);length=struct.unpack_from('<H',d,at)[0];assert length<32768
 strings.append(d[at+2:at+2+length*2].decode('utf-16le'))
print('count',count,'template',[(i,x) for i,x in enumerate(strings) if 'godotengine' in x])
new=['org.godotengine.plugin.v2.CameraServerExtension','io.godot.camera.GodotCameraServer','org.godotengine.plugin.v2.HandTracking','io.godot.camera.GodotHandTracking']
olddata=bytearray(d[p+start:p+oldsize]);offsets=[U(d,p+28+4*i) for i in range(count)]
for st in new:
 offsets.append(len(olddata)); olddata+=struct.pack('<H',len(st))+st.encode('utf-16le')+b'\x00\x00'
while len(olddata)%4:olddata+=b'\0'
new_start=28+len(offsets)*4; newsize=new_start+len(olddata)
pool=struct.pack('<HHI',1,28,newsize)+struct.pack('<IIIII',len(offsets),0,0,new_start,0)+struct.pack('<%dI'%len(offsets),*offsets)+olddata
# Clone the Godot editor.version meta-data node under application and substitute values.
orig=d[0x136c:0x13d0];assert U(orig,20)==strings.index('meta-data')
node=bytearray(orig)
# attributes begin at byte 36, each of size 20: namespace/name/raw/typed size+type/typed data
for at,idx in [(36,count),(56,count+1)]:
 assert U(node,at+8)!=0xffffffff
 struct.pack_into('<I',node,at+8,idx);struct.pack_into('<I',node,at+16,idx)
node2=bytearray(orig)
for at,idx in [(36,count+2),(56,count+3)]:
 struct.pack_into('<I',node2,at+8,idx);struct.pack_into('<I',node2,at+16,idx)
rest=d[p+oldsize:]; insert=0x13d0-(p+oldsize);rest=rest[:insert]+node+node2+rest[insert:]
result=bytearray(d[:8]+pool+rest);struct.pack_into('<I',result,4,len(result))
with zipfile.ZipFile(src) as zi,zipfile.ZipFile(out,'w') as zo:
 for info in zi.infolist():
  if info.filename.startswith('META-INF/'):continue
  data=result if info.filename=='AndroidManifest.xml' else zi.read(info.filename)
  zo.writestr(info,data)
 zo.write('/home/user/tools/v51/dex-v51/classes.dex','classes5.dex',compress_type=zipfile.ZIP_DEFLATED)
 zo.write('/home/user/tools/hand/libmediapipe_tasks_vision_jni.so','lib/arm64-v8a/libmediapipe_tasks_vision_jni.so',compress_type=zipfile.ZIP_DEFLATED)
 zo.write('/home/user/tools/hand/download/hand_landmarker.task','assets/hand_landmarker.task',compress_type=zipfile.ZIP_STORED)
print(out)
