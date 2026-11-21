#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
npm test
npm run build
(cd build/webxr && zip -qr ../MonkeGo-WebXR.zip .)
echo "APK must be built first with tools/bootstrap_android.sh; export: build/MonkeGo-WebXR.zip"
