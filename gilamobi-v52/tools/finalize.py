import json,hashlib
from pathlib import Path
root=Path(__file__).resolve().parents[1]
html=(root/'web/game.html').read_text()
assert '<body>' in html
html=html.replace('<body>','<body>\n<script src="controls.js"></script>',1)
(root/'web/index.html').write_text(html)
files={p.name:{'bytes':p.stat().st_size,'sha256':hashlib.sha256(p.read_bytes()).hexdigest()} for p in sorted((root/'web').iterdir()) if p.is_file()}
(root/'docs/BUILD.json').write_text(json.dumps({'godot':'4.4.1.stable','version':'v52-g2web-1.0.0','files':files,'physical_pico_g2_tested':False,'native_android_vr_apk':False},indent=2)+'\n')
