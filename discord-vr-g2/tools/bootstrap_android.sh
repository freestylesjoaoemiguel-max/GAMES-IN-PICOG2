#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
CACHE="$ROOT/.cache/android"
mkdir -p "$CACHE/minapk" "$CACHE/aaptjs"
python3 -m venv "$ROOT/.venv"
"$ROOT/.venv/bin/pip" install 'jdk4py==21.0.8.2'
(cd "$CACHE" && npm pack --ignore-scripts '@drxiaozhi/minapk@0.5.1' --silent && npm pack --ignore-scripts 'aaptjs3@2.0.2' --silent)
tar xf "$CACHE/drxiaozhi-minapk-0.5.1.tgz" -C "$CACHE/minapk"
tar xf "$CACHE/aaptjs3-2.0.2.tgz" -C "$CACHE/aaptjs"
chmod +x "$CACHE/aaptjs/package/bin/x64/linux/aapt2"
# Only the compiler/build tools are used, never minapk's WebView/runtime/template.
# Use the pinned Java runtime, not the runner's preinstalled JAVA_HOME.
env -u JAVA_HOME "$ROOT/.venv/bin/python" "$ROOT/discord-vr-g2/tools/build_android.py"
