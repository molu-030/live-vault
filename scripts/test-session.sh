#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
TASK_SESSION_DIR="$(mktemp -d /tmp/livevault-session.XXXXXX)"
trap 'rm -rf "$TASK_SESSION_DIR"' EXIT
TASK_SESSION_FLAGS=(-std=c++17 -O2 -pthread)
if [[ "${SANITIZE:-0}" == 1 ]]; then
 TASK_SESSION_FLAGS+=(-O1 -g -fno-omit-frame-pointer -fsanitize=address,undefined -fno-sanitize-recover=all)
fi
"${CXX:-c++}" "${TASK_SESSION_FLAGS[@]}" tests/session_native_test.cpp -o "$TASK_SESSION_DIR/test"
"$TASK_SESSION_DIR/test"
TASK_SESSION_JDK="${JAVA_HOME:-/workspace/toolchain/jdk}"
if [[ ! -f "$TASK_SESSION_JDK/include/jni.h" ]]; then
 TASK_SESSION_JDK="$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")"
fi
"${CXX:-c++}" "${TASK_SESSION_FLAGS[@]}" -Itests/audio_host_stubs -I"$TASK_SESSION_JDK/include" -I"$TASK_SESSION_JDK/include/linux" -Wl,--wrap=malloc -Wl,--wrap=calloc -Wl,--wrap=realloc -Wl,--wrap=pthread_mutex_lock -Wl,--wrap=pthread_mutex_trylock -Wl,--wrap=fread -Wl,--wrap=fwrite -Wl,--wrap=fseeko tests/session_engine_test.cpp -o "$TASK_SESSION_DIR/engine-test"
"$TASK_SESSION_DIR/engine-test" "$TASK_SESSION_DIR/capture.wav"
if [[ "${SESSION_BENCHMARK:-0}" == 1 ]]; then
 "${CXX:-c++}" "${TASK_SESSION_FLAGS[@]}" -Itests/audio_host_stubs -I"$TASK_SESSION_JDK/include" -I"$TASK_SESSION_JDK/include/linux" tests/session_callback_benchmark.cpp -o "$TASK_SESSION_DIR/benchmark"
 "$TASK_SESSION_DIR/benchmark" app/src/main/assets/soundfonts/generaluser-gs-1.44.sf2
fi
