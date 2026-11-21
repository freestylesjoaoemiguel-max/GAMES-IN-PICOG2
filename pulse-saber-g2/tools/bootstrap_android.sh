#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
# Reuse the audited pinned build-tool bootstrap; it also validates the existing Discord APK.
bash "$ROOT/discord-vr-g2/tools/bootstrap_android.sh"
env -u JAVA_HOME "$ROOT/.venv/bin/python" "$ROOT/pulse-saber-g2/tools/build_android.py"
