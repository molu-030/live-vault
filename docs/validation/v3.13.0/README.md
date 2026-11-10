# Live Vault 3.13.0 validation

Final tasks `:app:testPersonalDebugUnitTest :app:assemblePersonalDebug` completed successfully. The final single full run contains 365 tests across 55 classes: zero failures, errors or skipped tests. `java-tests.json` records per-class counts. Private capture implementation and its test source are excluded from this public repository.

`apk-checks.json` records the personal package/version, SHA-256, existing signing certificate and native-library checks. All 74 Java-declared native methods were present in all three shipped ABIs. The real Android build links official Oboe 1.11.0 and `c++_shared`. The previously published standard APK is unchanged.

## Checks and limits

- Native streaming/journal/FX bus evidence and reproduction commands: `../native-foundation-2026-11-10/`.
- FIFO servo: `input-drift-recovery.txt` and `input-engine-drift*.txt`. These simulate clock differences, not physical USB measurements.
- `diagnostics/` contains twelve Android View/native Skia renders with synthetic counters at 130% text: Korean/English, 412×893, 1480×924 and 960×600. Top/bottom images demonstrate scrolling while title/actions remain accessible. Counters are fixtures, not measured device data.
- Actual USB attachment/reconnection, acoustic feedback, latency, power, thermal and Oboe A/B measurements are deferred by the user.

## Intermediate failures and corrections

The first full run had 365 tests with 9 failures. A focused run had 65 tests with 7 failures; after fixture corrections a 67-test run had one remaining new LCD assertion failure. The final complete 365-test run passed without aggregating earlier results.

- Exit-flow fixtures previously wrote 128 zero bytes as a WAV. Strict integrity checking correctly rejected it. Fixtures now write a valid PCM WAV and keep file, metadata, exit-order and failed-save assertions. The blocked-exit latch is released in `finally`, draining pending exit work to prevent a failure cascading into the next Harmony test.
- The safety-mute test now keeps a known connected output route while exercising a feedback mute. A positive actual device becoming absent is a separate device-loss scenario, covered by the new service tests.
- The post-mux metadata-failure fixture now corrupts metadata after mux completion, preserving assertions for both completed and original videos. Preflight correctly prevents processing a take whose metadata is already corrupt.
- The legacy-video fixture persists missing timing metadata before requesting an edit because editing now reloads authoritative stored metadata under the per-take lease.
- The responsive LCD has the existing 120–300dp allocation contract. Native playback can use spare height; added video can compact it to the 120dp minimum. Tests retain touch targets, hierarchy, microphone/FX control height, player aspect and fixed footer checks. A newly introduced equal-height assumption was corrected; product layout was not changed to satisfy it.
- The first Android Oboe configuration failed because the Prefab library requires shared STL. The CMake argument now explicitly selects `c++_shared`; final Android builds succeeded.

Root review also corrected actual Oboe Harmony microphone availability, input publication/reopen safety, failed mic-join preservation, fallback status/counter resets and the distinction between input request shortfall and real FIFO underflow. Temporary file-cache misses warn without automatically ending a recording; confirmed writer loss/I/O errors or low disk space safely finalize it.
