"""Verify the actual CI export; no emulation fixtures may ship to Pages."""
import hashlib,json
from pathlib import Path
root=Path(__file__).resolve().parents[1]
report=json.loads((root/'docs/BUILD.json').read_text())
for name,meta in report['files'].items():
    p=root/'web'/name
    assert p.is_file(),name
    assert p.stat().st_size==meta['bytes'],name
    assert hashlib.sha256(p.read_bytes()).hexdigest()==meta['sha256'],name
for name,digest in report['source_hashes'].items():
    assert hashlib.sha256((root/name).read_bytes()).hexdigest()==digest,'Source changed without export: '+name
assert (root/'web/game.pck').read_bytes()[:4]==b'GDPC'
assert (root/'web/game.wasm').read_bytes()[:4]==b'\0asm'
for path in (root/'web').rglob('*'):
    if path.is_file():
        assert path.relative_to(root/'web').as_posix() in report['files'],path
        if path.suffix in ['.html','.js']:
            data=path.read_text()
            assert 'IWER' not in data and 'installRuntime' not in data and 'XRDevice(' not in data,path
assert not report['physical_pico_g2_tested']
print('GilaMobi: export/source hashes, runtime-only files and WASM/PCK verified')
