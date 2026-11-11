# Native session validation — 2026-11-10

Reproduce from repository root:

```sh
SANITIZE=1 scripts/test-session.sh
SANITIZE=1 scripts/test-audio-foundation.sh
SESSION_BENCHMARK=1 scripts/test-session.sh
scripts/test-composer.sh
scripts/test-audio-buses.sh
scripts/test-oboe-engine.sh
```

`session.txt` and `audio-foundation.txt` contain the final ASan/UBSan run output. `cache-and-callback.txt` describes the cache parking assertions and one optional host timing diagnostic. The composer, audio bus, and Oboe regression scripts also passed during this implementation; their verbose output is not duplicated here.

These host tests execute production transport, mixer, composer, cache, and writer code with stubbed audio hardware. They verify sample clocks, routing, lifecycle, bounded cache behavior, and callback operations. They do not establish device latency, Samsung S9 battery/CPU use, physical route behavior, or real microphone timing.
