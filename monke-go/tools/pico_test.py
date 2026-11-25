#!/usr/bin/env python3
"""Compatibility entry point. Current test uses npm Chromium, not hard-coded Chrome paths.
Simulated WebVR only; not a physical PICO or Android test. Start npm start first.
"""
from pathlib import Path
import subprocess
subprocess.run(['npm','run','test:browser'],cwd=Path(__file__).resolve().parents[1],check=True)
