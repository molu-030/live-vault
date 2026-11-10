# Live Vault 3.14.0 validation

Final combined tasks `:app:testPersonalDebugUnitTest :app:assemblePersonalDebug` completed successfully. The final full run contains **410 tests across 62 classes**, with zero failures, errors or skipped tests. `java-tests.json` records per-class counts. Earlier target results are not added to this total.

## Scope and artifact verification

- Shared48k session project/clock, copied media, vocal-only overdub, callback capture anchor, manual-offset placement, pending recovery, isolated composer editing and service lifecycle are implemented.
- `apk-checks.json`: personal3.14.0-preview/code37, package `pro.livemic.studio.personal`, original certificate, APK SHA-256, arm64-v8a/armeabi-v7a/x86_64 libraries and all82 Java-declared JNI methods verified. The existing standard APK is unchanged.
- `session/`:12 actual Android View/native Skia renders with synthetic project data at130% text, Korean/English,412×893 and1480×924. They exercise8 audio tracks, separate composer,32 clips,160-character names, shared time axis, scrolling and fixed48dp controls. These are test renders, not physical tablet captures.
- Native production transport/cache/mixer/writer/composer tests and ASan/UBSan: `../session-native-2026-11-10/`. They check exact44.1/48k conversion and rate changes, pause/seek/stop/EOF, source formats, overlapping audio, isolated recording bus, exact native anchors, callback allocation/lock/fileIO counters, semaphore cache parking, and concurrent recording shutdown. An optional host timing comparison is diagnostic only.

## Review corrections and intermediate evidence

An early target build caught a duplicate catch variable. A35-test run then had14 shadow setup failures: native static initializer shadows were incorrectly instrumented; explicit DoNotInstrument corrected the fixture. An early39-test run had4 composer-binding setup refusals. Guard-state/initialization diagnostics were added; all4 passed in the subsequent40-test run and final full410-test run without weakening their assertions.

The first full410-test run had one LiveConsoleScreenshot failure: an added standalone Session button row made the360×800 Live dashboard scroll. The row was removed, preserving the original no-scroll Live contract; Session remains available through the drawer and Library filter. The final complete run passed.

Root review also corrected queued exit saves and exact capture-target persistence, writer/stream teardown races, stale playback requests, legacy playback handoff, unresolved-target overwrite, source leases, explicit vocal-versus-mix imports, long timeline labels, and recording-retention policy. Session recording respects the user's source-retention setting; the essential processed vocal WAV remains usable when optional dry/float stems are disabled. Existing media bytes are retained when clip/track associations or pending targets are discarded.

## Deliberate limits

Clip positioning and track gain/pan/mute edits require paused or stopped playback. Recorded clips keep their original speed/pitch; BPM/key changes affect the composer. Voice-instrument phrase capture remains available in the independent composer, not while bound to a session editor. There is no automatic unreferenced-media cleanup, detailed audio-trim editor or time stretching.

Actual Galaxy Tab S9 Ultra USB routing/reconnection, acoustic feedback, physical recording/round-trip latency, long-duration hardware stability, battery and thermal testing remain deferred by the user. Host tests, UI fixtures and APK linkage checks do not establish those measurements.
