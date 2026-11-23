#!/usr/bin/env python3
"""Offline verification of the ready-to-publish export; no original ZIP needed."""
import hashlib
import json
from pathlib import Path
import re
from repack import ROOT, unpack

manifest = json.loads((ROOT / 'docs/BUILD.json').read_text())
pck = (ROOT / 'index.pck').read_bytes()
assert len(pck) == manifest['pck_bytes']
assert hashlib.sha256(pck).hexdigest() == manifest['pck_sha256']
assert hashlib.sha256((ROOT / 'index.wasm').read_bytes()).hexdigest() == manifest['wasm_sha256']
assert (ROOT / 'index.wasm').read_bytes()[:8] == b'\0asm\1\0\0\0'
files = unpack(pck)
assert len(files) == manifest['output_entries']
assert manifest['original_verified_entries'] == 388
assert not manifest['engine_rebuilt'] and not manifest['physical_pico_g2_tested']
assert files['res://.godot/extension_list.cfg'] == b''
assert not any('fixture' in name for name in files)
for name in manifest['patches']:
    assert files['res://' + name] == (ROOT / 'patches' / name).read_bytes(), name
    assert 'res://' + name + '.remap' not in files, name
for path in (ROOT / 'training').iterdir():
    if path.is_file(): assert files['res://g2/training/' + path.name] == path.read_bytes()
info = json.loads((ROOT / 'training/Info.dat').read_text())
assert (ROOT / 'training' / info['_songFilename']).read_bytes().startswith(b'OggS')
assert (ROOT / 'training' / info['_coverImageFilename']).read_bytes().startswith(b'\x89PNG')
for i, diff in enumerate(info['_difficultyBeatmapSets'][0]['_difficultyBeatmaps']):
    chart = json.loads((ROOT / 'training' / diff['_beatmapFilename']).read_text())
    assert len(chart['_notes']) == 37
    assert not chart['_obstacles']
    assert all(n['_cutDirection'] == 8 and n['_lineIndex'] in (1, 2) for n in chart['_notes'])
    colors = {n['_type'] for n in chart['_notes']}
    assert colors == ({1} if i == 0 else {0} if i == 1 else {0, 1})
html = (ROOT / 'index.html').read_text()
assert int(re.search(r'"index.pck":(\d+)', html)[1]) == len(pck)
assert '404.html' not in html
assert 'threads:false' in html and 'ensureCrossOriginIsolationHeaders":false' in html
for name in re.findall(r'(?:src|href)="([^"]+)"', html):
    assert (ROOT / name).is_file(), name
webxr = (ROOT / 'patches/game/scripts/webxr/webxr_initializer.gd').read_text()
assert 'webxr_interface.required_features = ""' in webxr
assert '"local-floor,local"' in webxr
assert 'session_mode = "immersive-vr"' in webxr
print(f'PASS: {len(files)} PCK entries and MD5 checksums; SHA256 manifest; unchanged supplied WASM; {len(manifest["patches"])} patches; 3 charts/111 notes; complete local assets; relative URLs; no fixture shipped.')
