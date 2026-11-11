#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
TASK_TEST_BINARY="$(mktemp /tmp/livemic-bounded-idle.XXXXXX)"
trap 'rm -f "$TASK_TEST_BINARY"' EXIT
c++ -std=c++17 -O2 -Wall -Wextra tests/bounded_dsp_idle_test.cpp -o "$TASK_TEST_BINARY"
"$TASK_TEST_BINARY"
