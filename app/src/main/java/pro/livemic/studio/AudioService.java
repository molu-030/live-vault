package pro.livemic.studio;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import java.util.Locale;

/** Owns audio and scheduled recording even while the Activity is backgrounded or recreated. */
public class AudioService extends Service {
    public static final String RECORD = "pro.livemic.RECORD",
            CANCEL = "pro.livemic.CANCEL",
            FINISH = "pro.livemic.FINISH",
            STOP = "STOP",
            ROUTE = "pro.livemic.ROUTE";
    public static final String EFFECTS = "pro.livemic.EFFECTS";
    public static final String PLAYBACK = "pro.livemic.PLAYBACK",
            STOP_PLAYBACK = "pro.livemic.STOP_PLAYBACK",
            COMPOSER_STOP = "pro.livemic.COMPOSER_STOP",
            HARMONY_STOP = "pro.livemic.HARMONY_STOP";

    public static volatile boolean composerRequested = false, harmonyRequested = false;
    public static PracticeSession practice;
    public static volatile boolean playbackActive = false;
    private boolean micForeground = false;
    private ComposerSession composer;
    private HarmonySession harmony;
    public static volatile boolean active = false, captureActive = false;
    public static volatile boolean youtubeSelected = false, pendingMicOnly = false;
    public static volatile String error = "", message = "";
    public static float captureLevel = 0;
    public static String routeLabel = "이어폰";
    public static boolean echoCancellation = false;
    public static int routeRevision = 0;
    public static int revision = 0;
    private static final RecordingCountdown countdown = new RecordingCountdown();

    public static boolean waiting() {
        return countdown.armed();
    }

    public static int remaining() {
        return countdown.remaining(SystemClock.elapsedRealtime());
    }

    private final Handler main = new Handler(Looper.getMainLooper());
    private AudioManager manager;
    private PowerManager.WakeLock wakeLock;
    private ExternalCapture capture;
    private int pendingTempo = 78, lastRemaining = -1;
    private long lastNotification = 0;
    private boolean destroying = false;
    private boolean cleanedUp = false;
    private boolean suspendedOutput;
    private static AudioService instance;
    private long playbackRequestedAt;
    private long lastRouteCheck = Long.MIN_VALUE;
    // Device callbacks protect immediately; this catches stream changes without a device event.
    private static final long ROUTE_CHECK_MS = 500;
    private static final long STATUS_PULSE_MS = 250;
    private static final long PLAYBACK_START_GRACE_MS = 1500;

    public static void requestAppExit(Context context, java.util.function.Consumer<String> done) {
        ExitCoordinator.requestExit();
        Runnable shutdown = () -> {
            AudioService service = instance;
            String failure = "";
            if (service != null) {
                failure = service.cleanupAudio();
                service.stopSelf();
            } else {
                try { RecordingManager.stop(); }
                catch (Exception e) { failure = e.getMessage() == null ? e.toString() : e.getMessage(); }
                NativeComposer.transport(0);
                NativeHarmony.transport(0);
                NativeAudio.stop();
                active = playbackActive = captureActive = composerRequested = harmonyRequested = false;
                pendingMicOnly = youtubeSelected = false;
                countdown.cancel();
            }
            done.accept(failure);
        };
        if (Looper.myLooper() == Looper.getMainLooper()) shutdown.run();
        else new Handler(Looper.getMainLooper()).post(shutdown);
    }
    private AudioRouting routing;
    private boolean callbacksRegistered;

    private final AudioDeviceCallback devices = new AudioDeviceCallback() {
        @Override
        public void onAudioDevicesAdded(AudioDeviceInfo[] added) {
            AudioRouting.refreshFeedbackProtection(AudioService.this);
        }

        @Override
        public void onAudioDevicesRemoved(AudioDeviceInfo[] removed) {
            // Protect a possible fallback to the phone speaker immediately; the
            // next pulse resolves the stream's actual replacement route.
            NativeAudio.feedbackProtection(true);
            if (AudioRouting.selected(AudioService.this) == 0 && !hasWiredOutput(AudioService.this)) {
                NativeAudio.set(11, 0);
                NativeComposer.monitor(false);
                NativeHarmony.monitor(false);
            }
            if (routing != null && routing.mode == 2) {
                for (AudioDeviceInfo d : removed) {
                    if (d.getId() == routing.output) {
                        NativeAudio.set(11, 0);
                        message = error = AppLanguage.text(
                                AudioService.this,
                                "블루투스 스피커 연결이 해제되어 마이크를 껐습니다.",
                                "Bluetooth speaker disconnected; the mic was turned off."
                        );
                        stopSelf();
                        break;
                    }
                }
            }
        }
    };

    private final BroadcastReceiver noisy = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            NativeAudio.feedbackProtection(true);
            NativeAudio.set(11, 0);
            NativeComposer.monitor(false);
            NativeHarmony.monitor(false);
        }
    };

    public static boolean hasWiredOutput(Context c) {
        AudioManager m = (AudioManager) c.getSystemService(AUDIO_SERVICE);
        for (AudioDeviceInfo d : m.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            int t = d.getType();
            if (t == AudioDeviceInfo.TYPE_WIRED_HEADPHONES
                    || t == AudioDeviceInfo.TYPE_WIRED_HEADSET
                    || t == AudioDeviceInfo.TYPE_USB_HEADSET
                    || t == AudioDeviceInfo.TYPE_USB_DEVICE
                    || t == AudioDeviceInfo.TYPE_USB_ACCESSORY
                    || t == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                    || t == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                    || t == AudioDeviceInfo.TYPE_BLE_HEADSET
                    || t == AudioDeviceInfo.TYPE_HEARING_AID) {
                return true;
            }
        }
        return false;
    }

    public static boolean canMonitor(Context c) {
        return AudioRouting.selected(c) > 0 || hasWiredOutput(c);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        manager = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (!ExitCoordinator.isExitRequested()) {
            composer = ComposerSession.get(this);
            harmony = HarmonySession.get(this);
            practice = new PracticeSession(this);
            manager.registerAudioDeviceCallback(devices, main);
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(noisy, new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(noisy, new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));
            }
            callbacksRegistered = true;
        }
        NotificationChannel channel = new NotificationChannel(
                "live_audio",
                AppLanguage.text(AudioService.this, "마이크 스튜디오", "Mic Studio"),
                NotificationManager.IMPORTANCE_LOW
        );
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
        wakeLock = ((PowerManager) getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LiveMic:Recording");
        wakeLock.setReferenceCounted(false);
    }

    private PendingIntent action(String action, int request) {
        return PendingIntent.getService(
                this,
                request,
                new Intent(this, AudioService.class).setAction(action).putExtra("exitGeneration", ExitCoordinator.generation()),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );
    }

    private Notification notification() {
        PendingIntent open = PendingIntent.getActivity(
                this,
                0,
                new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );
        String title = getString(R.string.app_name)
                + (active
                ? AppLanguage.text(AudioService.this, " · 마이크 사용 중", " · Mic active")
                : AppLanguage.text(AudioService.this, " · 반주 재생", " · Backing playing"));
        String detail = AppLanguage.text(AudioService.this, "효과 처리 중 · 탭하여 스튜디오 열기", "Processing effects · tap to open studio");
        if (waiting()) {
            title = remaining() + AppLanguage.text(AudioService.this, "초 후 녹음 시작", " seconds until recording");
            detail = captureActive
                    ? AppLanguage.text(AudioService.this, "다른 앱으로 이동해 재생하세요 · 마이크 + 허용된 앱 소리", "Switch to another app and play audio · mic + permitted app audio")
                    : AppLanguage.text(AudioService.this, "잠시 후 녹음 시작 · 마이크와 앱 내 반주", "Recording starts soon · mic and in-app backing");
        } else if (RecordingManager.active) {
            float seconds = NativeAudio.stats()[8];
            title = String.format(
                    Locale.US,
                    (RecordingManager.paused
                            ? AppLanguage.text(AudioService.this, "일시정지", "Paused")
                            : AppLanguage.text(AudioService.this, "녹음 중", "Recording")) + " · %02d:%02d",
                    (int) seconds / 60,
                    (int) seconds % 60
            );
            detail = captureActive
                    ? (captureLevel > .001f
                    ? AppLanguage.text(AudioService.this, "내 목소리 + 다른 앱 소리 입력 중", "Capturing voice + other app audio")
                    : AppLanguage.text(AudioService.this, "다른 앱 소리 신호 없음 · 재생/캡처 허용 확인", "No app audio signal · check playback and capture permission"))
                    : AppLanguage.text(AudioService.this, "내 목소리를 녹음하고 있습니다.", "Recording your voice.");
        } else if (!message.isEmpty()) {
            detail = message;
        } else {
            detail = AppLanguage.text(AudioService.this, "출력: ", "Output: ")
                    + (routing == null || routing.mode == 0
                    ? AppLanguage.text(AudioService.this, "이어폰", "Headphones")
                    : routing.mode == 1
                    ? AppLanguage.text(AudioService.this, "휴대폰 스피커", "Phone speaker")
                    : routeLabel)
                    + AppLanguage.text(AudioService.this, " · 탭하여 스튜디오 열기", " · tap to open studio");
        }
        if (active && NativeAudio.stats()[17] > .5f) {
            detail = AppLanguage.text(AudioService.this, "하울링 보호로 마이크 출력만 음소거했습니다.", "Mic output muted by feedback protection.");
        }
        Notification.Builder builder = new Notification.Builder(this, "live_audio")
                .setSmallIcon(R.drawable.ic_mic_status)
                .setContentTitle(title)
                .setContentText(detail)
                .setStyle(new Notification.BigTextStyle().bigText(detail))
                .setOngoing(true)
                .setContentIntent(open)
                .setOnlyAlertOnce(true);
        if (waiting()) {
            builder.addAction(new Notification.Action.Builder(null, AppLanguage.text(AudioService.this, "예약 취소", "Cancel scheduled recording"), action(CANCEL, 2)).build());
        } else if (RecordingManager.active) {
            builder.addAction(new Notification.Action.Builder(null, AppLanguage.text(AudioService.this, "녹음 저장", "Save recording"), action(FINISH, 3)).build());
        }
        return builder
                .addAction(new Notification.Action.Builder(
                        null,
                        active
                                ? AppLanguage.text(AudioService.this, "마이크 끄기", "Turn mic off")
                                : AppLanguage.text(AudioService.this, "반주 정지", "Stop backing"),
                        action(active ? STOP : STOP_PLAYBACK, 1)
                ).build())
                .build();
    }

    private void foreground(boolean external) {
        if (Build.VERSION.SDK_INT >= 30 || (Build.VERSION.SDK_INT >= 29 && external)) {
            startForeground(
                    41,
                    notification(),
                    micForeground ? CaptureSupport.serviceTypes(external) : ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            );
        } else {
            startForeground(41, notification());
        }
        lastNotification = SystemClock.elapsedRealtime();
    }

    private void notifyState() {
        getSystemService(NotificationManager.class).notify(41, notification());
        lastNotification = SystemClock.elapsedRealtime();
    }

    private void keepAwake() {
        if (!wakeLock.isHeld()) wakeLock.acquire(32 * 60 * 1000L);
    }

    private void releaseAwake() {
        if (wakeLock.isHeld()) wakeLock.release();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int id) {
        String action = intent == null ? "" : intent.getAction();
        boolean staleGeneration = intent != null && intent.hasExtra("exitGeneration")
                && intent.getLongExtra("exitGeneration", -1) != ExitCoordinator.generation();
        if (staleGeneration) {
            // Do not let a queued command from the previous launch stop current audio either.
            if (!active && !playbackActive && !waiting() && !RecordingManager.active) {
                if (PLAYBACK.equals(action) || RECORD.equals(action) || action == null || "".equals(action)) {
                    micForeground = false;
                    foreground(false);
                }
                cleanupAudio();
                stopForeground(STOP_FOREGROUND_REMOVE);
                stopSelf(id);
            }
            return START_NOT_STICKY;
        }
        if (destroying || ExitCoordinator.isExitRequested()) {
            // A queued foreground-service start still has Android's notification deadline.
            if (PLAYBACK.equals(action) || RECORD.equals(action) || action == null || "".equals(action)) {
                micForeground = false;
                foreground(false);
            }
            cleanupAudio();
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf(id);
            return START_NOT_STICKY;
        }
        // Creation may precede delivery of the first command; start ownership checks only now.
        main.removeCallbacks(pulse);
        main.post(pulse);
        if (EFFECTS.equals(action)) {
            syncEffects();
            if (!active && !playbackActive) stopSelf(id);
            return START_NOT_STICKY;
        }
        if (PLAYBACK.equals(action)) {
            if (youtubeSelected || (RecordingManager.active && RecordingManager.micOnly) || (waiting() && pendingMicOnly)) {
                // A stale startForegroundService playback request must still satisfy Android's deadline.
                micForeground = active;
                foreground(captureActive);
                if (!active && !waiting() && !RecordingManager.active && !playbackActive
                        && !composerRequested && !harmonyRequested && !composer.capturing
                        && (practice == null || !practice.active)) {
                    stopSelf(id);
                }
                return START_NOT_STICKY;
            }
            try {
                syncEffects();
                micForeground = active;
                foreground(false);
                if (!playbackActive && !active) {
                    if (routing == null) {
                        routing = new AudioRouting(this, 0, false);
                        routing.prepare();
                    }
                    if (!NativeAudio.startPlayback(routing.output, routing.mode)) {
                        throw new IllegalStateException(AppLanguage.text(AudioService.this, "반주 출력 장치를 열지 못했습니다.", "Could not open the backing output device."));
                    }
                    AudioRouting.refreshFeedbackProtection(this);
                }
                if (intent != null && intent.getBooleanExtra("playLocal", false)) {
                    NativeAudio.playLocal(true);
                }
                playbackActive = true;
                playbackRequestedAt = SystemClock.elapsedRealtime();
                keepAwake();
            } catch (Exception e) {
                error = e.getMessage();
                if (!active) stopSelf();
            }
            return START_NOT_STICKY;
        }
        if (STOP_PLAYBACK.equals(action)) {
            composerRequested = false;
            harmony.stopNative();
            NativeComposer.transport(0);
            NativeAudio.playLocal(false);
            playbackActive = false;
            if (!active) stopSelf();
            return START_NOT_STICKY;
        }
        if (COMPOSER_STOP.equals(action) || HARMONY_STOP.equals(action)) {
            schedulePlaybackRelease();
            return START_NOT_STICKY;
        }
        if (STOP.equals(action)) {
            harmony.live(false);
            if (practice != null) practice.stop(false);
            if (composer.capturing) composer.cancelPhrase();
            composer.voice(false);
            NativeAudio.set(11, 0);
            countdown.cancel();
            finishRecording("");
            NativeAudio.microphoneOff();
            active = false;
            echoCancellation = false;
            micForeground = false;
            float[] stats = NativeAudio.stats();
            if (stats != null && stats.length > 16 && stats[16] > .5f) {
                playbackActive = true;
            }
            if (playbackActive) {
                foreground(false);
            } else {
                stopSelf();
            }
            return START_NOT_STICKY;
        }
        if (CANCEL.equals(action)) {
            if (waiting()) {
                cancelSchedule();
                if (active) foreground(false);
            } else if (active) {
                notifyState();
            } else {
                stopSelf();
            }
            return START_NOT_STICKY;
        }
        if (FINISH.equals(action)) {
            if (practice != null && practice.active) {
                practice.stop(false);
                foreground(false);
                return START_NOT_STICKY;
            }
            android.util.Log.i("LiveMicRecord", "Save requested");
            finishRecording("");
            if (active) foreground(false);
            else stopSelf();
            return START_NOT_STICKY;
        }
        if (ROUTE.equals(action)) {
            harmony.poll();
            if (waiting()
                    || RecordingManager.active
                    || (practice != null && practice.active)
                    || composer.capturing
                    || harmony.state[4] >= 0
                    || harmony.state[5] >= 0) {
                error = AppLanguage.text(this, "예약, 녹음 또는 연습을 마친 뒤 장치를 바꾸세요.", "Finish the countdown, recording, phrase capture, or practice before changing devices.");
                return START_NOT_STICKY;
            }
            if (!active && !playbackActive) { stopSelf(id); return START_NOT_STICKY; }
            boolean wasActive = active;
            boolean monitor = wasActive && NativeAudio.stats()[21] > .5f && NativeAudio.stats()[17] < .5f;
            AudioRouting previous = routing, next;
            try {
                next = new AudioRouting(this, 0, wasActive);
            } catch (Exception e) {
                if (previous != null) previous.restoreSelection();
                error = message = e.getMessage();
                routeRevision++;
                return START_NOT_STICKY;
            }
            try {
                NativeAudio.set(11, 0);
                NativeComposer.monitor(false);
                NativeHarmony.monitor(false);
                if (previous != null) previous.close();
                routing = next;
                routing.prepare();
                NativeAudio.bufferBursts(AudioRouting.prefs(this).getInt("bursts" + routing.mode, 2));
                if (!NativeAudio.reroute(routing.input, routing.output, routing.mode, wasActive)) {
                    throw new IllegalStateException(AppLanguage.text(this, "선택한 오디오 장치를 열지 못했습니다.", "Could not open the selected audio device."));
                }
                AudioRouting.refreshFeedbackProtection(this);
                syncEffects();
                if (wasActive) {
                    routing.attachEffects();
                    boolean safe = monitor && canMonitor(this);
                    NativeAudio.set(11, safe ? 1 : 0);
                    NativeComposer.monitor(safe);
                    NativeHarmony.monitor(safe);
                }
                routeLabel = routing.label;
                echoCancellation = wasActive && routing.echoEnabled();
                routeRevision++;
                foreground(false);
            } catch (Exception e) {
                error = message = e.getMessage();
                if (routing != null) routing.close();
                routing = previous;
                boolean restored = false;
                if (previous != null) {
                    previous.restoreSelection();
                    try {
                        previous.prepare();
                        NativeAudio.bufferBursts(AudioRouting.prefs(this).getInt("bursts" + previous.mode, 2));
                        restored = NativeAudio.reroute(previous.input, previous.output, previous.mode, wasActive);
                        if (restored) {
                            AudioRouting.refreshFeedbackProtection(this);
                            syncEffects();
                            if (wasActive) {
                                previous.attachEffects();
                                boolean safe = monitor && canMonitor(this);
                                NativeAudio.set(11, safe ? 1 : 0);
                                NativeComposer.monitor(safe);
                                NativeHarmony.monitor(safe);
                            }
                            routeLabel = previous.label;
                            echoCancellation = wasActive && previous.echoEnabled();
                        }
                    } catch (Exception ignored) {
                        restored = false;
                    }
                }
                routeRevision++;
                if (restored) {
                    foreground(false);
                } else {
                    active = false;
                    playbackActive = false;
                    echoCancellation = false;
                    stopSelf();
                }
            }
            return START_NOT_STICKY;
        }
        boolean requested = RECORD.equals(action), external = requested && intent.getBooleanExtra("capture", false);
        try {
            if (requested && (waiting() || RecordingManager.active)) {
                message = AppLanguage.text(AudioService.this, "이미 예약 또는 녹음 중입니다.", "A recording is already scheduled or in progress.");
                return START_NOT_STICKY;
            }
            if (external && (youtubeSelected || intent.getBooleanExtra("micOnly", false))) throw new IllegalStateException("YouTube selected: microphone-only recording.");
            if (external && (!BuildConfig.PLAYBACK_CAPTURE || !CaptureSupport.available())) {
                throw new IllegalStateException(AppLanguage.text(AudioService.this, "이 버전은 마이크 녹음 전용입니다.", "This version supports microphone recording only."));
            }
            syncEffects();
            micForeground = true;
            foreground(external);
            if (!active) {
                int device = intent == null ? 0 : intent.getIntExtra("device", 0);
                if (routing != null && !active) {
                    routing.close();
                    routing = null;
                }
                if (routing == null) {
                    routing = new AudioRouting(this, device);
                    routing.prepare();
                }
                NativeAudio.bufferBursts(AudioRouting.prefs(this).getInt("bursts" + routing.mode, 2));
                active = NativeAudio.start(routing.input, routing.output, routing.mode);
                if (!active) {
                    throw new IllegalStateException(AppLanguage.text(
                            AudioService.this,
                            "선택한 오디오 경로를 열지 못했습니다. 장치 연결과 다른 녹음 앱을 확인하세요.",
                            "Could not open the selected audio route. Check the device connection and other recording apps."
                    ));
                }
                routing.attachEffects();
                boolean monitor = intent != null && intent.getBooleanExtra("monitor", false) && canMonitor(this);
                NativeAudio.set(11, monitor ? 1 : 0);
                NativeHarmony.monitor(monitor);
                routeLabel = routing.label;
                echoCancellation = routing.echoEnabled();
                routeRevision++;
            }
            if (requested) {
                message = "";
                RecordingManager.syncMs = AudioRouting.prefs(this).getInt("sync" + AudioRouting.selected(this), 0);
                pendingMicOnly = youtubeSelected || intent.getBooleanExtra("micOnly", false);
                pendingTempo = Math.max(40, Math.min(240, intent.getIntExtra("bpm", 78)));
                if (external) {
                    Intent consent = Build.VERSION.SDK_INT >= 33
                            ? intent.getParcelableExtra("projection", Intent.class)
                            : intent.getParcelableExtra("projection");
                    NativeAudio.enablePlaybackCapture(true, intent.getFloatExtra("captureGain", .7f));
                    capture = CaptureSupport.start(this, consent, (int) NativeAudio.stats()[6], () -> captureStopped());
                    captureActive = true;
                }
                countdown.arm(intent.getIntExtra("delay", 0), SystemClock.elapsedRealtime());
                keepAwake();
                lastRemaining = -1;
                revision++;
                checkStart();
                notifyState();
            }
        } catch (Exception e) {
            error = AppLanguage.text(AudioService.this, "녹음 준비 실패: ", "Could not prepare recording: ") + e.getMessage();
            message = error;
            cancelSchedule();
            if (!active) {
                if (routing != null) {
                    routing.close();
                    routing = null;
                }
                stopSelf();
            } else {
                foreground(false);
            }
        }
        return START_NOT_STICKY;
    }

    private void syncEffects() {
        float[] defaults = {1, .55f, .27f, 2.2f, .14f, 280, .25f, 0, 0, 1, 1, 0, 0, 0, 0, 78, 0, 0, 0, 1};
        SharedPreferences p = getSharedPreferences("MainActivity", 0);
        for (int i = 0; i < defaults.length; i++) {
            if (i != 11 && i != 12 && i != 13) {
                NativeAudio.set(i, p.getFloat("p" + i, defaults[i]));
            }
        }
        if (routing != null) routing.syncNoiseReduction();
    }

    private void schedulePlaybackRelease() {
        // The owner pulse keeps checking until playback or a save really finishes.
        playbackRequestedAt = 0;
        main.removeCallbacks(pulse);
        main.post(pulse);
    }

    private boolean hasPlaybackOwner(float[] stats) {
        return stats[16] > .5f || composer.playing() || composer.capturing
                || harmony.state[0] > .5 || harmony.state[4] >= 0 || harmony.state[5] >= 0
                || harmony.busy
                || ((composerRequested || harmonyRequested)
                    && SystemClock.elapsedRealtime() - playbackRequestedAt < PLAYBACK_START_GRACE_MS);
    }

    private void checkStart() {
        if (countdown.consumeIfDue(SystemClock.elapsedRealtime())) {
            if (!RecordingManager.start(this, pendingTempo, pendingMicOnly)) {
                message = error = AppLanguage.text(
                        AudioService.this,
                        "녹음을 시작하지 못했습니다. 저장 공간과 마이크 상태를 확인하세요.",
                        "Could not start recording. Check storage and microphone availability."
                );
                closeCapture();
                releaseAwake();
            } else {
                message = AppLanguage.text(AudioService.this, "녹음을 시작했습니다.", "Recording started.");
            }
            revision++;
            notifyState();
        }
    }

    private void cancelSchedule() {
        boolean had = waiting();
        countdown.cancel();
        pendingMicOnly = false;
        closeCapture();
        if (!RecordingManager.active) releaseAwake();
        if (had) {
            message = AppLanguage.text(AudioService.this, "예약 녹음을 취소했습니다.", "Scheduled recording cancelled.");
            revision++;
        }
    }

    private void closeCapture() {
        if (capture != null) {
            capture.close();
            capture = null;
        }
        captureActive = false;
        captureLevel = 0;
        NativeAudio.enablePlaybackCapture(false, 0);
    }

    private void captureStopped() {
        if (destroying) return;
        String reason = AppLanguage.text(AudioService.this, "다른 앱 소리 캡처가 중단되어 녹음을 마쳤습니다.", "Recording ended because app audio capture stopped.");
        if (waiting()) {
            cancelSchedule();
            message = error = AppLanguage.text(
                    AudioService.this,
                    "다른 앱 소리 캡처 권한이 해제되어 예약을 취소했습니다.",
                    "Scheduled recording cancelled because app audio capture permission was revoked."
            );
        } else {
            finishRecording(reason);
        }
        if (active) foreground(false);
    }

    private String recordingStopFailure = "";

    private void finishRecording(String warning) {
        recordingStopFailure = "";
        countdown.cancel();
        if (capture != null && !capture.receivedAudioDuringRecording()) {
            warning += (warning.isEmpty() ? "" : "\n")
                    + AppLanguage.text(
                            AudioService.this,
                            "다른 앱 소리가 감지되지 않았습니다. 재생되지 않았거나 해당 앱이 캡처를 허용하지 않았을 수 있습니다.",
                            "No app audio was detected. Playback may have stopped or the app may not allow capture."
                    );
        }
        RecordingManager.extraWarning = warning;
        try {
            RecordingManager.Result result = RecordingManager.stop();
            if (result != null) {
                message = result.warning.isEmpty()
                        ? AppLanguage.text(AudioService.this, "오디오와 MIDI를 저장했습니다.", "Audio and MIDI saved.")
                        : result.warning;
                revision++;
            }
        } catch (Exception e) {
            recordingStopFailure = e.getMessage() == null ? e.toString() : e.getMessage();
            android.util.Log.e("LiveMicRecord", "Save failed", e);
            message = error = AppLanguage.text(AudioService.this, "보관함 저장 실패: ", "Could not save to library: ") + e.getMessage();
        } finally {
            closeCapture();
            releaseAwake();
            if (active && !destroying) foreground(false);
        }
    }

    private final Runnable pulse = new Runnable() {
        @Override
        public void run() {
            if (destroying) return;
            if (!active && !playbackActive && !waiting() && !RecordingManager.active) {
                cleanupAudio();
                stopSelf();
                return;
            }
            long now = SystemClock.elapsedRealtime();
            if (lastRouteCheck == Long.MIN_VALUE || now - lastRouteCheck >= ROUTE_CHECK_MS) {
                AudioRouting.refreshFeedbackProtection(AudioService.this);
                lastRouteCheck = now;
            }
            composer.poll();
            harmony.poll();
            float[] stats = NativeAudio.stats();
            if (stats[10] < .5f) {
                message = error = AppLanguage.text(AudioService.this, "오디오 장치 연결이 중단되어 녹음을 마쳤습니다.", "Recording ended because the audio device disconnected.");
                cleanupAudio();
                stopSelf();
                return;
            }
            checkStart();
            if (capture != null) captureLevel = capture.level();
            boolean playbackOwner = hasPlaybackOwner(stats);
            if (!active && !waiting() && !RecordingManager.active && !playbackOwner) {
                composerRequested = harmonyRequested = false;
                cleanupAudio(true);
                stopSelf();
                return;
            }
            if (RecordingManager.active && stats[8] >= 1800) {
                finishRecording(AppLanguage.text(AudioService.this, "30분 녹음 한도에 도달해 저장했습니다.", "Saved after reaching the 30-minute recording limit."));
            }
            if (RecordingManager.active || waiting() || captureActive || playbackOwner || composer.capturing) keepAwake();
            else releaseAwake();
            int next = remaining();
            if (next != lastRemaining || now - lastNotification >= 1000) {
                lastRemaining = next;
                notifyState();
            }
            main.postDelayed(this, composer.capturing ? 20 : STATUS_PULSE_MS);
        }
    };

    /** Synchronous and idempotent: completion means native streams and CPU ownership are gone. */
    private String cleanupAudio() { return cleanupAudio(false); }

    private String cleanupAudio(boolean preservePausedTransport) {
        if (cleanedUp) {
            if (suspendedOutput && !preservePausedTransport && ExitCoordinator.isExitRequested()) {
                NativeComposer.transport(0);
                NativeHarmony.transport(0);
                NativeAudio.stop();
                suspendedOutput = false;
            }
            return recordingStopFailure;
        }
        cleanedUp = true;
        destroying = true;
        main.removeCallbacksAndMessages(null);
        if (practice != null) practice.stop(false);
        countdown.cancel();
        finishRecording("");
        if (routing != null) {
            routing.close();
            routing = null;
        }
        if (harmony != null) {
            harmony.live(false);
            if (!preservePausedTransport) harmony.stopNative();
        }
        if (ExitCoordinator.isExitRequested()) NativeComposer.transport(0);
        composerRequested = harmonyRequested = false;
        if (composer != null) {
            if (composer.capturing && !composer.worker.isShutdown()) composer.poll();
            if (composer.capturing) composer.cancelPhrase();
            composer.voice(false);
        }
        suspendedOutput = preservePausedTransport;
        if (preservePausedTransport) NativeAudio.suspend();
        else NativeAudio.stop();
        active = playbackActive = captureActive = false;
        pendingMicOnly = false;
        if (ExitCoordinator.isExitRequested()) youtubeSelected = false;
        echoCancellation = false;
        releaseAwake();
        stopForeground(STOP_FOREGROUND_REMOVE);
        return recordingStopFailure;
    }

    @Override
    public void onDestroy() {
        cleanupAudio();
        if (instance == this) instance = null;
        if (callbacksRegistered) {
            manager.unregisterAudioDeviceCallback(devices);
            unregisterReceiver(noisy);
            callbacksRegistered = false;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
