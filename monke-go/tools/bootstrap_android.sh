#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
CACHE="$ROOT/.cache/android"
if [[ ! -f "$CACHE/minapk/package/tools/ecj-3.45.0.jar" || ! -f "$CACHE/aaptjs/package/bin/x64/linux/aapt2" || ! -x "$ROOT/.venv/bin/python" ]]; then
  mkdir -p "$CACHE/minapk" "$CACHE/aaptjs"
  python3 -m venv "$ROOT/.venv"
  "$ROOT/.venv/bin/pip" install 'jdk4py==21.0.8.2'
  (cd "$CACHE" && npm pack --ignore-scripts '@drxiaozhi/minapk@0.5.1' --silent && npm pack --ignore-scripts 'aaptjs3@2.0.2' --silent)
  tar xf "$CACHE/drxiaozhi-minapk-0.5.1.tgz" -C "$CACHE/minapk"
  tar xf "$CACHE/aaptjs3-2.0.2.tgz" -C "$CACHE/aaptjs"
fi
chmod +x "$CACHE/aaptjs/package/bin/x64/linux/aapt2"
# Hashes verified before execution in build_android.py. No SDK/runtime from minapk bundled.
env -u JAVA_HOME "$ROOT/.venv/bin/python" "$ROOT/monke-go/tools/build_android.py"
