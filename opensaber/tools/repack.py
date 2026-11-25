#!/usr/bin/env python3
"""Repackage the user-supplied Godot 4.3 PCK, verifying each original file.
Usage: python3 opensaber/tools/repack.py path/to/original.zip
No engine rebuild: patched GDScript is parsed by the supplied Godot runtime.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import struct
import zipfile

ROOT = Path(__file__).resolve().parents[1]
SOURCE_MD5 = '850a54660599b7b482f3557b328e5e8e'


def unpack(data):
    assert data[:4] == b'GDPC', 'Not a Godot PCK'
    version, major, minor, patch, flags = struct.unpack_from('<5I', data, 4)
    assert (version, major, minor, flags) == (2, 4, 3, 0), 'Unexpected/encrypted pack'
    base = struct.unpack_from('<Q', data, 24)[0]
    count = struct.unpack_from('<I', data, 96)[0]
    pos = 100
    files = {}
    for _ in range(count):
        n = struct.unpack_from('<I', data, pos)[0]; pos += 4
        name = data[pos:pos+n].rstrip(b'\0').decode(); pos += n
        offset, size = struct.unpack_from('<QQ', data, pos); pos += 16
        digest = data[pos:pos+16]; pos += 16
        entry_flags = struct.unpack_from('<I', data, pos)[0]; pos += 4
        assert entry_flags == 0
        payload = data[base+offset:base+offset+size]
        assert len(payload) == size and hashlib.md5(payload).digest() == digest, name
        assert name.startswith('res://') and '..' not in name.split('/'), name
        assert name not in files, name
        files[name] = payload
    return files


def pack(files):
    table, payload = bytearray(), bytearray()
    for name, content in sorted(files.items()):
        encoded = name.encode()
        encoded += b'\0' * ((-len(encoded)) % 4)
        table.extend(struct.pack('<I', len(encoded)) + encoded)
        table.extend(struct.pack('<QQ', len(payload), len(content)))
        table.extend(hashlib.md5(content).digest() + struct.pack('<I', 0))
        payload.extend(content)
        payload.extend(b'\0' * ((-len(payload)) % 16))
    base = (100 + len(table) + 15) // 16 * 16
    header = b'GDPC' + struct.pack('<5IQ', 2, 4, 3, 0, 0, base) + bytes(64) + struct.pack('<I', len(files))
    return header + table + bytes(base-100-len(table)) + payload


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source', type=Path)
    args = parser.parse_args()
    archive_bytes = args.source.read_bytes()
    assert hashlib.md5(archive_bytes).hexdigest() == SOURCE_MD5, 'Use the original supplied ZIP'
    with zipfile.ZipFile(args.source) as archive:
        name = next(n for n in archive.namelist() if n.endswith('index.pck'))
        original = archive.read(name)
    files = unpack(original)
    original_count = len(files)
    # Native OpenXR extensions and TCP/UDP servers are not usable on the web.
    files['res://.godot/extension_list.cfg'] = b''
    # Keep its cover (referenced by the main scene), but don't advertise a song
    # with no audio/chart. MainMenu skips incomplete directories.
    files['res://game/data/maps/Playlists.json'] = b'[]'
    patches = []
    for path in sorted((ROOT / 'patches').rglob('*.gd')):
        name = path.relative_to(ROOT / 'patches').as_posix()
        # Keep the exact class-cache path; remapping source to another .gd path
        # makes Godot report duplicate global classes.
        files['res://' + name] = path.read_bytes()
        files.pop('res://' + name + '.remap', None)
        patches.append(name)
    for path in sorted((ROOT / 'training').iterdir()):
        if path.is_file(): files['res://g2/training/' + path.name] = path.read_bytes()
    output = pack(files)
    assert unpack(output) == files
    (ROOT / 'index.pck').write_bytes(output)
    html = (ROOT / 'index.html').read_text()
    html = re.sub(r'("index.pck":)\d+', r'\g<1>' + str(len(output)), html)
    (ROOT / 'index.html').write_text(html)
    manifest = {
        'source_zip_md5': SOURCE_MD5,
        'source_pck_sha256': hashlib.sha256(original).hexdigest(),
        'original_verified_entries': original_count,
        'output_entries': len(files),
        'pck_bytes': len(output),
        'pck_sha256': hashlib.sha256(output).hexdigest(),
        'wasm_sha256': hashlib.sha256((ROOT / 'index.wasm').read_bytes()).hexdigest(),
        'patches': patches,
        'engine_rebuilt': False,
        'physical_pico_g2_tested': False,
    }
    (ROOT / 'docs/BUILD.json').write_text(json.dumps(manifest, indent=2) + '\n')
    print(json.dumps(manifest, indent=2))

if __name__ == '__main__': main()
