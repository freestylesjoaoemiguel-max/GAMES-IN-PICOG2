#!/usr/bin/env bash
# Preserve failures and surface the error in Checks when raw runner logs are unavailable.
set -uo pipefail
log=$(mktemp)
trap 'rm -f "$log"' EXIT
"$@" 2>&1 | tee "$log"
status=${PIPESTATUS[0]}
if (( status != 0 )); then
  detail=$(tail -n 40 "$log")
  detail=${detail//%/%25}
  detail=${detail//$'\r'/%0D}
  detail=${detail//$'\n'/%0A}
  echo "::error title=Browser regression failed::$detail"
fi
exit "$status"
