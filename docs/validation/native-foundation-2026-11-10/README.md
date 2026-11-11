Native streaming and recording validation

Commands:

- `bash scripts/test-audio-buses.sh`
- `SANITIZE=1 bash scripts/test-audio-foundation.sh`
- `bash scripts/test-input-engine-drift.sh` (separate adaptive input integration suite)

These are synthetic host tests using production engine code and hardware stubs. They do not establish physical USB clock accuracy, round-trip latency, removable-device performance, or Android battery consumption.

The source cache holds 32,768 stereo float frames (256 KiB), independent of file duration. A replacement candidate can temporarily double this allocation. One cancelled reader may be retained until kernel I/O returns; additional replacements are refused until it can be reclaimed. Existing PCM16 import rates/channels and the 20-minute limit remain supported.

Pause/EOF only change atomic state. The file reader parks on a condition variable while paused; the callback never joins or waits on that mutex. Seek generations reject stale pages. Missing file frames produce silence while the output clock advances, with recording warnings and counters. Explicit new playback primes on the control thread, with a two-second read-ahead deadline. Opening a regular file and final cleanup cannot impose a portable hard deadline on blocked kernel filesystem operations; these remain outside the audio callback.

Record journals contain independently flushed and synchronized stream watermarks. Tests include abrupt process termination while WAV headers are still provisional, final header synchronization, and bounded tail admission if the writer has exited. Journaling does not promise persistence beyond the filesystem/device's synchronization guarantees.

Diagnostic field 4 counts unfilled input read requests, including read-ahead requests. It does not establish lost audio; FIFO underflow frames exclude intentional initial priming silence. Target buffer fields 17/18 use output-frame units and output sample rate. UI diagnostic reads use cached atomics rather than stream pointers.
