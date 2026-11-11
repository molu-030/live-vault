# Live Vault 3.14.1 USB microphone correction

The user reported “device disconnected” immediately after pressing Record. No physical device is connected to this environment; the exact hardware failure has not been reproduced.

## Confirmed software defects and changes

- The Oboe engine read inputInfo before startInput. The production backend returns input metadata only after successful start/publishing, so the old order obtained zero device, session, rate and channel fields. Start now precedes metadata acquisition. Negotiated Float32, 1–2 channels and a positive rate are validated, then the resampler and device/session snapshots are initialized before enabling callback consumption. Invalid metadata closes only the new input and preserves an existing backing output.
- The native fixture previously exposed metadata before start, masking this defect. It now follows the production contract. Regression probes invoke the render callback during input start and metadata acquisition, verify no premature input reads, and exercise stereo 44.1 kHz conversion plus invalid metadata cleanup. Allocation/mutex callback counters remain zero.
- Android device IDs are transient. Input/output choices now retain type, product name and address, and re-resolve changed IDs conservatively. An existing matching port remains selectable when duplicate names exist. A missing port is rebound only to a unique identity match; differing nonempty addresses and ambiguous matches fail closed. Addressless matching cannot prove the same physical microphone. Preferences from older builds migrate identity only while their old port is present; otherwise users must reselect the device. Route rollback restores identity and ID together. Reconnection never resumes microphone capture automatically.
- An engine stop was always described as a physical device disconnection. Stream failures now keep safe recording finalization and monitor muting, while reporting stream interruption and the available native error code. Actual removal handling is retained.

## Verification

Final Android build and full Java/UI results are recorded in the adjacent machine-readable files after completion. Native validation evidence is included separately. These are host/synthetic tests, not USB hardware measurements.

## Device follow-up

Install the personal update, connect the USB microphone, explicitly select it under audio input, then start recording. Old unavailable selections require reselection once. If interrupted again, report the complete message/error code and microphone model. S9 Ultra USB routing, real latency and battery measurements remain pending; this release does not claim a verified hardware fix.

Final result: **419 Java/UI tests across 62 classes passed**, no failures/errors/skips; personalDebug APK build succeeded. Original certificate and 82 JNI exports in each of three ABIs verified. The earlier40-test target pass is not added to this total.
