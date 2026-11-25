#!/usr/bin/env python3
"""Build a separate test-only site in .cache. Never alter the published PCK."""
from pathlib import Path
import re
from repack import ROOT, unpack, pack
out = ROOT.parent / '.cache/opensaber-fixture'
out.mkdir(parents=True, exist_ok=True)
files = unpack((ROOT / 'index.pck').read_bytes())
files['res://g2/controller_fixture.gd'] = (ROOT / 'tests/controller_fixture.gd').read_bytes()
name = 'res://game/scripts/g2_perf.gd'
s = files[name].decode().replace('    print("[G2] OpenSaber web patch', '    get_tree().root.add_child(load("res://g2/controller_fixture.gd").new())\n    print("[G2] OpenSaber web patch')
files[name] = s.encode()
data = pack(files)
(out / 'index.pck').write_bytes(data)
html = re.sub(r'("index.pck":)\d+', r'\g<1>'+str(len(data)), (ROOT / 'index.html').read_text())
(out / 'index.html').write_text(html)
for name in ['index.js','index.wasm','index.audio.worklet.js','index.icon.png','web.js','web.css']:
    target = out / name
    if not target.exists(): target.symlink_to(ROOT / name)
print(out)
