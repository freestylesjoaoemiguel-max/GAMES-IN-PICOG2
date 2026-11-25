#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
GODOT="${GODOT:-godot}"
mkdir -p "$ROOT/build" "$ROOT/web"
"$GODOT" --headless --editor --path "$ROOT/source" --import 2>&1 | tee "$ROOT/build/import.log"
if grep -E 'SCRIPT ERROR:|Parse Error:|Failed to load script' "$ROOT/build/import.log"; then exit 1; fi
# Dummy rendering cannot read imported morph buffers; test the real GL mesh path.
# CI has Xvfb/Mesa. On a workstation without Xvfb, use the normal display.
DISPLAY_RUN=()
if command -v xvfb-run >/dev/null 2>&1; then DISPLAY_RUN=(xvfb-run -a); fi
timeout 120 "${DISPLAY_RUN[@]}" "$GODOT" --path "$ROOT/source" --rendering-method gl_compatibility --audio-driver Dummy --script "$ROOT/tests/g2_tests.gd" 2>&1 | tee "$ROOT/build/tests.log"
if grep -E 'SCRIPT ERROR:|Parse Error:' "$ROOT/build/tests.log"; then exit 1; fi
"$GODOT" --headless --path "$ROOT/source" --export-release Web "$ROOT/web/game.html" 2>&1 | tee "$ROOT/build/export.log"
if grep -E 'SCRIPT ERROR:|Parse Error:|Export failed' "$ROOT/build/export.log"; then exit 1; fi
python3 "$ROOT/tools/finalize.py"
