#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
TASK_OBOE_DIR="$(mktemp -d /tmp/livevault-oboe-engine.XXXXXX)"
trap 'rm -rf "$TASK_OBOE_DIR"' EXIT
TASK_OBOE_JDK="${JAVA_HOME:-/workspace/toolchain/jdk}"
if [[ ! -f "$TASK_OBOE_JDK/include/jni.h" ]]; then
    TASK_OBOE_JDK="$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")"
fi
TASK_OBOE_FLAGS=(-std=c++17 -O2 -pthread -Wl,--wrap=malloc -Wl,--wrap=calloc -Wl,--wrap=realloc -Wl,--wrap=pthread_mutex_lock -Wl,--wrap=pthread_mutex_trylock -Itests/audio_host_stubs -I"$TASK_OBOE_JDK/include" -I"$TASK_OBOE_JDK/include/linux")
if [[ "${SANITIZE:-0}" == 1 ]]; then
    TASK_OBOE_FLAGS+=(-O1 -g -fno-omit-frame-pointer -fsanitize=address,undefined -fno-sanitize-recover=all)
fi
"${CXX:-c++}" "${TASK_OBOE_FLAGS[@]}" tests/oboe_engine_test.cpp -o "$TASK_OBOE_DIR/test"
"$TASK_OBOE_DIR/test" "$TASK_OBOE_DIR/continuity.wav"
