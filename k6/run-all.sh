#!/usr/bin/env bash
# Quester — run every k6 journey and record per-journey PASS/FAIL.
# Usage:
#   K6_BASE_URL=http://localhost:8080 bash k6/run-all.sh
#   K6_OUT_DIR=k6-results bash k6/run-all.sh
# Writes one NDJSON file per journey into $K6_OUT_DIR (<name>.json) plus
# $K6_OUT_DIR/status.txt with lines "<name> PASS|FAIL".
# Exits non-zero if any journey failed its thresholds — the perf workflow
# calls this in a step that records the exit code instead of failing
# immediately, so the report step still runs and commits the numbers.
# Locally: start the stack first (docker compose up -d postgres rustfs
# backend agent-brain gateway), then run this script. k6 must be installed
# (https://k6.io/docs/get-started/installation/); it is NOT installed by
# this script.
set -u

BASE_URL="${K6_BASE_URL:-http://localhost:8080}"
OUT_DIR="${K6_OUT_DIR:-k6-results}"
mkdir -p "$OUT_DIR"

SCRIPTS="login profile jobs matches tracker coach"
FAILED=()
: > "$OUT_DIR/status.txt"

for name in $SCRIPTS; do
  echo "=== k6 $name ==="
  if k6 run --out "json=$OUT_DIR/$name.json" -e "K6_BASE_URL=$BASE_URL" "k6/$name.js"; then
    echo "$name PASS" >> "$OUT_DIR/status.txt"
    echo "$name: PASS"
  else
    echo "$name FAIL" >> "$OUT_DIR/status.txt"
    echo "$name: FAIL (thresholds or checks)"
    FAILED+=("$name")
  fi
done

if [ "${#FAILED[@]}" -gt 0 ]; then
  echo "FAILED journeys: ${FAILED[*]}"
  exit 1
fi
echo "All k6 journeys passed."
