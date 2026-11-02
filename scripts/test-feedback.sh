#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
TASK_FEEDBACK_BINARY="$(mktemp /tmp/livemic-feedback.XXXXXX)"
trap 'rm -f "$TASK_FEEDBACK_BINARY"' EXIT
TASK_FEEDBACK_FLAGS=(-std=c++17 -O2 -Wall -Wextra)
if [[ "${SANITIZE:-0}" == 1 ]]; then
    TASK_FEEDBACK_FLAGS+=(-O1 -g -fno-omit-frame-pointer -fsanitize=address,undefined)
fi
"${CXX:-c++}" "${TASK_FEEDBACK_FLAGS[@]}" tests/feedback_guard_test.cpp -o "$TASK_FEEDBACK_BINARY"
"$TASK_FEEDBACK_BINARY"
