#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
TASK_FOUNDATION_DIR="$(mktemp -d /tmp/livevault-foundation.XXXXXX)"
trap 'rm -rf "$TASK_FOUNDATION_DIR"' EXIT
TASK_FOUNDATION_JDK="${JAVA_HOME:-/workspace/toolchain/jdk}"
if [[ ! -f "$TASK_FOUNDATION_JDK/include/jni.h" ]]; then
    TASK_FOUNDATION_JDK="$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")"
fi
TASK_FOUNDATION_FLAGS=(-std=c++17 -O2 -pthread -Itests/audio_host_stubs -I"$TASK_FOUNDATION_JDK/include" -I"$TASK_FOUNDATION_JDK/include/linux")
if [[ "${SANITIZE:-0}" == 1 ]]; then
    TASK_FOUNDATION_FLAGS+=(-O1 -g -fno-omit-frame-pointer -fsanitize=address,undefined -fno-sanitize-recover=all)
fi
"${CXX:-c++}" "${TASK_FOUNDATION_FLAGS[@]}" -Wl,--wrap=fread -Wl,--wrap=fwrite -Wl,--wrap=fseeko tests/local_stream_test.cpp -o "$TASK_FOUNDATION_DIR/local-test"
"$TASK_FOUNDATION_DIR/local-test" "$TASK_FOUNDATION_DIR"
"${CXX:-c++}" "${TASK_FOUNDATION_FLAGS[@]}" tests/record_journal_native_test.cpp -o "$TASK_FOUNDATION_DIR/journal-test"
"$TASK_FOUNDATION_DIR/journal-test" "$TASK_FOUNDATION_DIR"
