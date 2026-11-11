# Live Vault 3.14.3 — seek within library recordings

The current audio recording card now shows a 48dp seek control and elapsed/total time. Tap or drag to choose a playback position; paused playback stays paused. Progress uses the existing UI tick, with no additional background timer. Inactive cards retain their compact layout. Videos retain their external player.

Seek requests verify the current file and view generation, reject non-finite values, clamp to the actual player duration, and are blocked during recording/countdown. Detached controls cannot seek a newly selected file. A list refresh rebinds to the existing playback position. The 3.14.2 stop-on-navigation/background behavior is preserved.

Four new seek regressions exercise gestures, accessibility, paused operation, bounds, stale controls and blocked recording/countdown states. Two native Android View renders cover a phone in Korean and landscape tablet in English at130% font scale. The renders use synthetic media and are not physical device captures. Final build/test/package checks are recorded alongside this report.

Final result: **434 Java/UI tests across 63 classes passed**, no failures/errors/skips; personalDebug APK build succeeded. Original certificate and 82 JNI exports in each of three ABIs verified. The four new seek regressions and two native render checks are included in this total.
