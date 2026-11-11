#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
TASK_BINARY="$(mktemp /tmp/livevault-input-drift.XXXXXX)"
trap 'rm -f "$TASK_BINARY"' EXIT
c++ -std=c++17 -O2 -Wall -Wextra tests/input_drift_test.cpp -o "$TASK_BINARY"
"$TASK_BINARY"
c++ -std=c++17 -O2 -Wall -Wextra tests/input_resampler_test.cpp -o "$TASK_BINARY"
"$TASK_BINARY"
