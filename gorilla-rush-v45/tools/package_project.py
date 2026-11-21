#!/usr/bin/env python3
from pathlib import Path
import zipfile,hashlib,shutil
root=Path(__file__).resolve().parents[1];out=root/'downloads';out.mkdir(exist_ok=True)
file=out/'Gorilla-Rush-v45-projeto.zip'
with zipfile.ZipFile(file,'w',zipfile.ZIP_DEFLATED,compresslevel=6) as z:
 for p in sorted(root.rglob('*')):
  rel=p.relative_to(root)
  if p.is_file() and not any(c in {'build','node_modules','downloads','__pycache__','.cache'} for c in rel.parts):z.write(p,'gorilla-rush-v45/'+str(rel))
 for p in out.glob('*.apk*'):z.write(p,'gorilla-rush-v45/downloads/'+p.name)
with zipfile.ZipFile(file) as z:assert z.testzip() is None
(out/(file.name+'.sha256')).write_text(hashlib.sha256(file.read_bytes()).hexdigest()+'  '+file.name+'\n')
web=root/'build/webxr/downloads'
if web.exists():
 shutil.copy2(file,web/file.name);shutil.copy2(out/(file.name+'.sha256'),web/(file.name+'.sha256'))
print(file,file.stat().st_size,'bytes; source, all decoded supplied assets, pinned dependencies, tools, reports and signed screen APK')
