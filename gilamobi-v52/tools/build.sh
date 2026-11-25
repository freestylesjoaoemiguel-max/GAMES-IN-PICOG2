#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
GODOT="${GODOT:-godot}"
mkdir -p "$ROOT/build" "$ROOT/web"
"$GODOT" --headless --editor --path "$ROOT/source" --import 2>&1 | tee "$ROOT/build/import.log"
if grep -E 'SCRIPT ERROR:|Parse Error:|Failed to load script' "$ROOT/build/import.log"; then exit 1; fi
timeout 120 "$GODOT" --headless --path "$ROOT/source" --script "$ROOT/tests/g2_tests.gd" 2>&1 | tee "$ROOT/build/tests.log"
if grep -E 'SCRIPT ERROR:|Parse Error:' "$ROOT/build/tests.log"; then exit 1; fi
"$GODOT" --headless --path "$ROOT/source" --export-release Web "$ROOT/web/game.html" 2>&1 | tee "$ROOT/build/export.log"
if grep -E 'SCRIPT ERROR:|Parse Error:|Export failed' "$ROOT/build/export.log"; then exit 1; fi
python3 "$ROOT/tools/finalize.py"
