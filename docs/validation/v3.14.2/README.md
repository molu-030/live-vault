# Live Vault 3.14.2 — library preview lifecycle

Library recording previews now stop and release their MediaPlayer when the recordings page is left through navigation, Back, or a library filter change, and when the activity goes into the background. Returning does not resume a stopped preview. Paused previews are released too.

Same-page library refreshes retain playback. Opening or dismissing the navigation drawer without choosing another destination is not a page departure. Service-owned accompaniment, composer/session transport and microphone recording keep their existing lifecycle.

Vocal-effect preview renders carry a playback generation so a render completing after departure cannot start audio late, including after returning to the library. Saving a rendered take remains separate from automatic preview playback.

Focused synthetic media-player/lifecycle regressions and the final Android build results are recorded alongside this report. Physical device playback/navigation checks remain pending.

Final result: **428 Java/UI tests across 63 classes passed**, no failures/errors/skips; personalDebug APK build succeeded. Original certificate and 82 JNI exports in each of three ABIs verified. The nine new lifecycle/media regressions are included in this total.
