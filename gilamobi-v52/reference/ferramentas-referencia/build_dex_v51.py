import pathlib,zipfile,subprocess,os
root=pathlib.Path('/home/user/tools/hand');base=set((root/'base_classes.txt').read_text().splitlines());base={x for x in base if not x.startswith('io/godot/camera/')};seen=set();merged=pathlib.Path('/home/user/tools/v51/merged-v51.jar')
jars=[pathlib.Path('/home/user/tools/v51/camera-hand-v51.jar')]+sorted((root/'unpacked').glob('*.jar'))+sorted((root/'resolved').glob('*.jar'))
with zipfile.ZipFile(merged,'w',zipfile.ZIP_DEFLATED) as out:
 for jar in jars:
  if jar.name.startswith('auto-value-1.') or jar.name.startswith('annotation-1.'):continue
  n=d=0
  with zipfile.ZipFile(jar) as z:
   for name in z.namelist():
    if not name.endswith('.class') or name.startswith('META-INF/'):continue
    if name in base or name in seen:d+=1;continue
    out.writestr(name,z.read(name));seen.add(name);n+=1
  print(jar.name, n,'added',d,'skipped')
print('classes',len(seen))
out=pathlib.Path('/home/user/tools/v51/dex-v51');out.mkdir(exist_ok=True)
env=os.environ.copy();env['JAVA_HOME']='/home/user/tools/jdk17';env['PATH']=env['JAVA_HOME']+'/bin:'+env['PATH']
cmd=['/home/user/tools/android/build-tools/35.0.0/d8','--min-api','21','--lib',str(root/'libs/android.jar'),'--classpath',str(root/'libs/godot.jar'),'--output',str(out),str(merged)]
subprocess.run(cmd,check=True,env=env)
