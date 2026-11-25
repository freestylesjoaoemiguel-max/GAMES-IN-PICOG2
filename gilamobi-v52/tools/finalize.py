import json,hashlib
from pathlib import Path
root=Path(__file__).resolve().parents[1]
html=(root/'web/game.html').read_text()
assert '<body>' in html
html=html.replace('<body>','<body>\n<script src="vendor/webxr-layers-polyfill.js"></script>\n<script src="controls.js?v=1.0.2"></script>',1)
# Bust PCK cache too: a versioned page alone does not refresh cached assets.
html=html.replace("engine.startGame({", "engine.startGame({'mainPack':'game.pck?v=1.0.2',")
(root/'web/index.html').write_text(html)
import shutil
(root/'web/licenses').mkdir(exist_ok=True)
for name in ['LICENSE.txt','CREDITOS-MODELO.txt','THIRD_PARTY_HAND_TRACKING.txt']:
    shutil.copyfile(root/'source'/name,root/'web/licenses'/name)
shutil.copyfile(root.parent/'opensaber/docs/GODOT_LICENSE.txt',root/'web/licenses/GODOT_LICENSE.txt')
files={p.relative_to(root/'web').as_posix():{'bytes':p.stat().st_size,'sha256':hashlib.sha256(p.read_bytes()).hexdigest()} for p in sorted((root/'web').rglob('*')) if p.is_file()}
sources={p.relative_to(root).as_posix():hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted((root/'source').rglob('*')) if p.is_file() and '.godot' not in p.parts and p.suffix in ['.gd','.tscn','.godot','.cfg']}
(root/'docs/BUILD.json').write_text(json.dumps({'godot':'4.4.1.stable','version':'v52-g2web-1.0.2','files':files,'source_hashes':sources,'physical_pico_g2_tested':False,'native_android_vr_apk':False},indent=2)+'\n')
