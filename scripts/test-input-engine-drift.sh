#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
TASK_DRIFT_DIR="$(mktemp -d /tmp/livevault-engine-drift.XXXXXX)"
TASK_DRIFT_BINARY="$TASK_DRIFT_DIR/test"
trap 'rm -rf "$TASK_DRIFT_DIR"' EXIT
TASK_DRIFT_JDK="${JAVA_HOME:-/workspace/toolchain/jdk}"
if [[ ! -f "$TASK_DRIFT_JDK/include/jni.h" ]]; then
    TASK_DRIFT_JDK="$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")"
fi
TASK_DRIFT_FLAGS=(-std=c++17 -O2 -pthread -Wl,--wrap=malloc -Wl,--wrap=calloc -Wl,--wrap=realloc -Itests/audio_host_stubs -I"$TASK_DRIFT_JDK/include" -I"$TASK_DRIFT_JDK/include/linux")
if [[ "${SANITIZE:-0}" == 1 ]]; then
    TASK_DRIFT_FLAGS+=(-O1 -g -fno-omit-frame-pointer -fsanitize=address,undefined -fno-sanitize-recover=all)
fi
"${CXX:-c++}" "${TASK_DRIFT_FLAGS[@]}" tests/input_engine_drift_test.cpp -o "$TASK_DRIFT_BINARY"
"$TASK_DRIFT_BINARY" "$TASK_DRIFT_DIR/continuity.wav"
