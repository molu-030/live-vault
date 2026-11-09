#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
TASK_AUDIO_BUS_DIR="$(mktemp -d /tmp/livemic-audio-buses.XXXXXX)"
trap 'rm -rf "$TASK_AUDIO_BUS_DIR"' EXIT
TASK_AUDIO_BUS_JDK="${JAVA_HOME:-/workspace/toolchain/jdk}"
if [[ ! -f "$TASK_AUDIO_BUS_JDK/include/jni.h" ]]; then
    TASK_AUDIO_BUS_JDK="$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")"
fi
TASK_AUDIO_BUS_FLAGS=(-std=c++17 -O2 -pthread -Itests/audio_host_stubs -I"$TASK_AUDIO_BUS_JDK/include" -I"$TASK_AUDIO_BUS_JDK/include/linux")
if [[ "${SANITIZE:-0}" == 1 ]]; then
    TASK_AUDIO_BUS_FLAGS+=(-O1 -g -fno-omit-frame-pointer -fsanitize=address,undefined -fno-sanitize-recover=all)
fi
"${CXX:-c++}" "${TASK_AUDIO_BUS_FLAGS[@]}" tests/echo_input_profile_test.cpp -o "$TASK_AUDIO_BUS_DIR/profile-test"
"$TASK_AUDIO_BUS_DIR/profile-test"
"${CXX:-c++}" "${TASK_AUDIO_BUS_FLAGS[@]}" tests/take_sources_test.cpp -o "$TASK_AUDIO_BUS_DIR/source-test"
"$TASK_AUDIO_BUS_DIR/source-test" "$TASK_AUDIO_BUS_DIR"
"${CXX:-c++}" "${TASK_AUDIO_BUS_FLAGS[@]}" tests/audio_bus_render_test.cpp -o "$TASK_AUDIO_BUS_DIR/test"
"$TASK_AUDIO_BUS_DIR/test" "$TASK_AUDIO_BUS_DIR" app/src/main/assets/soundfonts/generaluser-gs-1.44.sf2
