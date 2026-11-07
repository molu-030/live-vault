package pro.livemic.studio;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.DisplayCutout;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.TextureView;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Space;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONObject;

/** Live Vault native UI and studio controls. */
public class MainActivity extends Activity {
    static final int BG = StudioTheme.BG,
            PANEL = StudioTheme.SURFACE,
            RAISED = StudioTheme.RAISED,
            TEXT = StudioTheme.TEXT,
            MUTED = StudioTheme.MUTED,
            LIME = StudioTheme.ACCENT,
            RED = StudioTheme.DANGER;

    final Handler handler = new Handler(Looper.getMainLooper());
    final ExecutorService io = Executors.newSingleThreadExecutor();
    final float[] values = {1, .55f, .27f, 2.2f, .14f, 280, .25f, 0, 0, 1, 1, 0, 0, 0, 0, 78, 0, 0, 0, 1};

    StudioRootLayout root;
    private boolean expandedLandscape;
    private LinearLayout headerNavigation;
    private final Runnable adaptWindow = this::adaptToWindow;
    StudioPageLayout page;
    StudioScrollView pageScroll;
    LinearLayout actionDock;
    LinearLayout content, nav;
    Button practiceButton, micButton, monitorButton, recButton, videoButton, pauseButton, flipButton, recordOptions;
    TextView status, pitch, timer, cameraTimer, levelText;
    StudioVisual visual;

    int sourceTab = 1, genre = 0, selectedPreset = -1, pendingAction = 0;
    boolean effectsExpanded = false, videoPaused = false, flipResumeAudio = false, cameraFailed = false, awaitingMicPermission = false;
    TextView pitchDetails;
    PitchMeter pitchMeter;

    LiveScreen liveScreen;
    YouTubePlayer youtube;
    ComposerSession composer;
    ComposerScreen composerScreen;
    ComposerExchange exchange;
    HarmonySession harmony;
    HarmonyScreen harmonyScreen;
    int lastHarmonyRevision = -1;
    int libraryFilter = 0, lastComposerRevision = -1;

    RecentBackings recent;
    String selectedFile = "";
    long actionDeadline = 0;
    int recordDelay = 0, lastRecordingRevision = -1, lastRouteRevision = -1;
    boolean captureOtherApps = false, recordPermissionPending = false, pendingCapture = false;
    int recordingRequestStage = 0;
    float captureGain = .7f;
    String videoTakeId = null;
    boolean activityVisible = false;

    Button presetChip, outputButton, globalRecord, globalOptions, headerMute;
    TextView globalTimer, liveState, playerTime, recordCaption;
    StudioPlaybackLine playerLine;

    final BackNavigation navigation = new BackNavigation();
    BackNavigation.Page renderedPage;
    final ArrayList<Object> backWindows = new ArrayList<>();
    private ModernBack modernBack;
    boolean exitInProgress = false, exitCompleted = false, exitResourcesStopped = false;
    volatile String exitSaveError = "";
    AlertDialog exitDialog, exitProgress;
    String composerErrorBeforeExit = "", harmonyErrorBeforeExit = "";

    PopupWindow drawerPopup;
    int drawerLeft, drawerTop;
    LinearLayout transport;
    TextView trainingReadout;
    PracticePlot practicePlot;
    boolean practiceOwnsMic = false;
    boolean practiceRecord = false;
    int practiceRoot = 48, practiceBpm = 60, practiceMode = 0, practicePattern = 0;

    int selectedLiveEffect = 0;
    int tab = 0, inputDevice = 0;
    boolean starting = false, busy = false, wasRunning = false, headphones = false, pendingMonitor = false, videoPreparing = false;

    CameraController camera;
    TextureView texture;
    File rawVideo, exportFile, playingFile;
    TakeStore store;
    MediaPlayer player;
    final Map<String, StudioButton> takePlayButtons = new HashMap<>();
    final Map<String, TextView> takePlaybackStatus = new HashMap<>();
    boolean testActive = false;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        if (!ExitCoordinator.beginLaunch()) {
            exitCompleted = true;
            finish(); // Another activity is still saving; do not remove its task.
            return;
        }
        if (state != null) {
            selectedLiveEffect = Math.max(0, Math.min(5, state.getInt("selectedLiveEffect", 0)));
        }
        youtube = new YouTubePlayer(this);
        exchange = new ComposerExchange(this);
        exchange.restoreState(state);
        composer = ComposerSession.get(this);
        composerScreen = new ComposerScreen(this, composer);
        harmony = HarmonySession.get(this);
        harmonyScreen = new HarmonyScreen(this, harmony);
        store = new TakeStore(this);
        recent = new RecentBackings(this);
        updateScreenAwake(false);

        selectedPreset = getPreferences(0).getInt("preset", -1);
        recordDelay = getPreferences(0).getInt("recordDelay", 0);
        getPreferences(0).edit().remove("captureOtherApps").apply();
        if (state != null) {
            recordingRequestStage = state.getInt("recordingRequestStage", 0);
            if (recordingRequestStage < 1 || recordingRequestStage > 2) recordingRequestStage = 0;
            pendingCapture = recordingRequestStage != 0 && state.getBoolean("pendingCapture", false);
            recordPermissionPending = recordingRequestStage != 0;
        }
        captureGain = getPreferences(0).getFloat("captureGain", .7f);
        for (int i = 0; i < values.length; i++) {
            if (i != 11 && i != 13 && i != 12) {
                values[i] = getPreferences(0).getFloat("p" + i, values[i]);
            }
        }
        inputDevice = AudioRouting.selectedInput(this);
        sourceTab = getPreferences(0).getInt("backingSource", 1);
        if (!AudioService.active) {
            for (int i = 0; i < values.length; i++) {
                NativeAudio.set(i, values[i]);
            }
        }
        BackNavigation.Page restored = navigation.restore(state);
        if (restored != null) applyPage(restored);
        build();
        if (restored != null) restorePageScroll(restored);
        if (Build.VERSION.SDK_INT >= 33) modernBack = new ModernBack(this);
        handler.post(tick);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("selectedLiveEffect", selectedLiveEffect);
        navigation.save(out, currentPage());
        if (exchange == null) return;
        exchange.saveState(out);
        if (recordingRequestStage != 0) {
            out.putInt("recordingRequestStage", recordingRequestStage);
            out.putBoolean("pendingCapture", pendingCapture);
        }
    }

    String tr(String ko, String en) {
        return AppLanguage.text(this, ko, en);
    }

    int dp(float n) {
        return (int) (getResources().getDisplayMetrics().density * n + .5f);
    }

    GradientDrawable bg(int c, int r) {
        return StudioTheme.background(this, c, r);
    }

    TextView text(String t, int size, int color) {
        return StudioTheme.text(this, t, size, color);
    }

    TextView title(String t) {
        TextView v = text(t, 22, TEXT);
        v.setTypeface(StudioTheme.bold(this));
        v.setPadding(0, dp(4), 0, dp(10));
        return v;
    }

    LinearLayout column() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    LinearLayout row() {
        LinearLayout l = new LinearLayout(this);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    Button button(String t, Runnable r) {
        return StudioTheme.button(this, t, r);
    }

    void add(View v) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(8);
        content.addView(v, lp);
    }

    void gap() {
        Space s = new Space(this);
        content.addView(s, new LinearLayout.LayoutParams(1, dp(4)));
    }

    void note(String t) {
        TextView v = text(t, 12, MUTED);
        v.setPadding(0, dp(4), 0, dp(4));
        add(v);
    }

    LinearLayout beginCard() {
        LinearLayout previous = content;
        LinearLayout card = StudioTheme.card(this);
        add(card);
        content = card;
        return previous;
    }

    void endCard(LinearLayout previous) {
        content = previous;
    }

    void weighted(LinearLayout line, View view, int height) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(height), 1);
        if (line.getChildCount() > 0) lp.leftMargin = dp(8);
        line.addView(view, lp);
    }

    /** Uses app-window space, never physical display size or keyboard-reduced height. */
    boolean tabletLandscape() { return expandedLandscape; }

    private boolean expandedWindow() {
        float density = getResources().getDisplayMetrics().density;
        float width = getResources().getConfiguration().screenWidthDp;
        float height = getResources().getConfiguration().screenHeightDp;
        if (Build.VERSION.SDK_INT >= 30) {
            android.view.WindowMetrics metrics = getWindowManager().getCurrentWindowMetrics();
            android.graphics.Rect bounds = metrics.getBounds();
            android.graphics.Insets safe = metrics.getWindowInsets().getInsetsIgnoringVisibility(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            if (bounds.width() > 0 && bounds.height() > 0) {
                width = (bounds.width() - safe.left - safe.right) / density;
                height = (bounds.height() - safe.top - safe.bottom) / density;
            }
        }
        return width >= 840 && Math.min(width, height) >= 600 && width > height;
    }

    private void scheduleWindowAdaptation() {
        handler.removeCallbacks(adaptWindow);
        handler.post(adaptWindow);
    }

    private void adaptToWindow() {
        if (root == null || exiting()) return;
        boolean next = expandedWindow();
        if (next == expandedLandscape) return;
        BackNavigation.Page current = currentPage();
        expandedLandscape = next;
        updateHeaderNavigation();
        root.requestLayout();
        // A recording or preview owns its attached TextureView until normal navigation.
        // Resizing that view is safe; rebuilding it would close the camera session.
        if (tab == 3 || videoPreparing) return;
        showTab();
        restorePageScroll(current);
    }

    @Override public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        scheduleWindowAdaptation();
    }

    private void updateHeaderNavigation() {
        if (nav == null || headerNavigation == null) return;
        android.view.ViewGroup parent = (android.view.ViewGroup) nav.getParent();
        if (parent != null) parent.removeView(nav);
        if (tabletLandscape()) {
            nav.setVisibility(View.VISIBLE);
            headerNavigation.addView(nav, new LinearLayout.LayoutParams(dp(330), dp(48)));
        } else {
            nav.setVisibility(View.GONE);
            root.addView(nav, 1, new LinearLayout.LayoutParams(-1, 0));
        }
    }

    void build() {
        expandedLandscape = expandedWindow();
        root = new StudioRootLayout(this);
        root.setBackgroundColor(BG);
        root.addOnLayoutChangeListener((v, l, t, r, b, oldL, oldT, oldR, oldB) -> {
            if (r - l != oldR - oldL || b - t != oldB - oldT) scheduleWindowAdaptation();
        });
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            );
        }
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets i = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                int bottom = Math.max(i.bottom, insets.getInsets(WindowInsets.Type.ime()).bottom);
                root.safeInsets(i.left, i.top, i.right, bottom);
            } else {
                int left = insets.getSystemWindowInsetLeft(),
                        top = insets.getSystemWindowInsetTop(),
                        right = insets.getSystemWindowInsetRight(),
                        bottom = insets.getSystemWindowInsetBottom();
                DisplayCutout cutout = insets.getDisplayCutout();
                if (cutout != null) {
                    left = Math.max(left, cutout.getSafeInsetLeft());
                    top = Math.max(top, cutout.getSafeInsetTop());
                    right = Math.max(right, cutout.getSafeInsetRight());
                    bottom = Math.max(bottom, cutout.getSafeInsetBottom());
                }
                root.safeInsets(left, top, right, bottom);
            }
            return insets;
        });
        setContentView(root);
        root.requestApplyInsets();

        // Top Bar (#0e1013 concept)
        LinearLayout header = row();
        header.setMinimumHeight(dp(48));
        header.addView(StudioTheme.icon(this, "menu", tr("전체 메뉴", "Menu"), this::drawer), new LinearLayout.LayoutParams(dp(48), dp(48)));

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.live_vault_mark);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        logo.setContentDescription(tr("승인된 Live Vault 원호 마이크 로고", "Live Vault microphone logo"));
        logo.setClickable(true);
        logo.setFocusable(true);
        logo.setOnClickListener(v -> chooseTab(0));
        header.addView(logo, new LinearLayout.LayoutParams(dp(28), dp(28)));

        TextView brand = text("Live Vault", 18, TEXT);
        brand.setTypeface(StudioTheme.bold(this));
        brand.setSingleLine();
        brand.setEllipsize(TextUtils.TruncateAt.END);
        brand.setPadding(dp(8), 0, 0, 0);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        brand.setClickable(true);
        brand.setFocusable(true);
        brand.setOnClickListener(v -> chooseTab(0));
        header.addView(brand, new LinearLayout.LayoutParams(0, dp(48), 1));

        headerNavigation = row();
        header.addView(headerNavigation, new LinearLayout.LayoutParams(-2, dp(48)));

        StudioButton mute = StudioTheme.styled(this, tr("출력 끄기", "Mute output"), StudioButton.QUIET, () -> {
            set(11, 0);
            toast(tr("내 목소리 출력만 껐습니다. 녹음은 계속됩니다.", "Voice output muted. Recording continues."));
        });
        headerMute = mute;
        mute.glyph("mute");
        mute.setTextSize(11);
        mute.setMaxLines(1);
        mute.setEllipsize(TextUtils.TruncateAt.END);
        mute.setPadding(dp(8), 0, dp(8), 0);
        header.addView(mute, new LinearLayout.LayoutParams(-2, dp(48)));
        root.addView(header);

        // Hidden nav container (0 height, visually removed per user request while keeping programmatic nav references intact)
        nav = row();
        nav.setVisibility(View.GONE);
        String[] navLabels = {tr("라이브", "Live"), tr("작곡기", "Composer"), tr("보관함", "Library")};
        for (int i = 0; i < navLabels.length; i++) {
            final int target = new int[]{0, 7, 4}[i];
            Button b = new Button(this);
            b.setAllCaps(false);
            b.setStateListAnimator(null);
            b.setElevation(0);
            b.setTypeface(StudioTheme.bold(this));
            b.setTextSize(12);
            b.setPadding(dp(4), 0, dp(4), 0);
            b.setMinWidth(0);
            b.setMinimumWidth(0);
            b.setText(navLabels[i]);
            b.setOnClickListener(v -> chooseTab(target));
            LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(0, dp(48), 1);
            if (i > 0) np.leftMargin = dp(6);
            nav.addView(b, np);
        }
        root.addView(nav, new LinearLayout.LayoutParams(-1, 0));
        updateHeaderNavigation();

        pageScroll = new StudioScrollView(this);
        page = new StudioPageLayout(this);
        page.setPadding(0, dp(4), 0, dp(4));
        content = page;
        pageScroll.addView(page);
        root.addView(pageScroll, new LinearLayout.LayoutParams(-1, 0, 1));

        actionDock = column();
        actionDock.setPadding(0, dp(6), 0, dp(6));
        actionDock.setVisibility(View.GONE);
        root.addView(actionDock, new LinearLayout.LayoutParams(-1, -2));

        transport = row();
        transport.setPadding(dp(12), dp(10), dp(12), dp(10));
        transport.setBackground(bg(PANEL, 12));
        recButton = button(tr("●  오디오 녹음", "● Audio recording"), this::toggleRecord);
        transport.addView(recButton, new LinearLayout.LayoutParams(0, dp(52), 1));
        recordOptions = button("⋯", this::recordingOptions);
        recordOptions.setTextSize(22);
        recordOptions.setContentDescription(tr("녹음 설정 · 시작 시간 및 다른 앱 소리", "Recording settings · start time and other app audio"));
        transport.addView(recordOptions, new LinearLayout.LayoutParams(dp(48), dp(52)));
        LinearLayout clock = column();
        clock.setPadding(dp(12), 0, 0, 0);
        timer = text("00:00.0", 16, TEXT);
        timer.setTypeface(StudioTheme.mono(this));
        clock.addView(timer);
        clock.addView(text("WAV + MIDI", 10, MUTED));
        transport.addView(clock);
        transport.setVisibility(View.GONE);
        root.addView(transport, new LinearLayout.LayoutParams(-1, dp(74)));

        globalRecord = recButton;
        globalOptions = recordOptions;
        globalTimer = timer;
        showTab();
    }

    BackNavigation.Page currentPage() {
        BackNavigation.Page shown = renderedPage;
        return new BackNavigation.Page(shown == null ? tab : shown.tab,
                shown == null ? sourceTab : shown.source, shown == null ? libraryFilter : shown.filter,
                pageScroll == null ? 0 : pageScroll.getScrollY());
    }

    private void applyPage(BackNavigation.Page destination) {
        tab = destination.tab;
        sourceTab = destination.source;
        libraryFilter = destination.filter;
        if (tab == 1 && sourceTab != 2) leaveYouTube();
        getPreferences(0).edit().putInt("backingSource", sourceTab).apply();
    }

    private void restorePageScroll(BackNavigation.Page destination) {
        pageScroll.preserveScroll(destination.scroll);
        pageScroll.requestLayout();
    }

    void chooseTab(int k) {
        navigate(new BackNavigation.Page(k, sourceTab, libraryFilter, 0), false);
    }

    void chooseSource(int source) {
        if (settingsLocked()) { toast(tr("녹음 또는 예약을 마친 뒤 소스를 바꾸세요.", "Finish recording or the countdown before changing sources.")); return; }
        navigate(new BackNavigation.Page(1, source, libraryFilter, 0), false);
    }

    void chooseLibraryFilter(int filter) {
        navigate(new BackNavigation.Page(4, sourceTab, filter, 0), false);
    }

    private void navigate(BackNavigation.Page destination, boolean back) {
        if (exiting()) return;
        BackNavigation.Page origin = currentPage();
        if (destination.tab == 1 && destination.source != sourceTab && settingsLocked()) {
            toast(tr("녹음 또는 예약을 마친 뒤 소스를 바꾸세요.", "Finish recording or the countdown before changing sources."));
            return;
        }
        if (origin.sameDestination(destination)) {
            applyPage(destination);
            showTab();
            restorePageScroll(new BackNavigation.Page(tab, sourceTab, libraryFilter, origin.scroll));
            return;
        }
        if (videoPreparing) {
            toast(tr("카메라를 준비하고 있습니다.", "Preparing camera."));
            return;
        }
        Runnable accepted = () -> {
            if (testActive) finishSyncTest(false);
            if (AudioService.practice != null && AudioService.practice.active) AudioService.practice.stop(false);
            pendingAction = 0;
            if (back) navigation.pop(); else navigation.visit(origin, destination);
            applyPage(destination);
            showTab();
            restorePageScroll(destination);
        };
        if (camera != null && camera.recording) {
            StudioDialogs.alert(this)
                    .setTitle(tr("녹화를 마칠까요?", "Finish recording video?"))
                    .setMessage(tr("영상을 저장한 뒤 화면을 이동합니다.", "Save the video before leaving this screen."))
                    .setPositiveButton(tr("저장 후 이동", "Save and leave"), (d, w) -> {
                        stopVideo();
                        accepted.run();
                    })
                    .setNegativeButton(tr("계속 녹화", "Keep recording"), null)
                    .show();
            return;
        }
        accepted.run();
    }

    void trackBackWindow(Object window) {
        backWindows.removeIf(item -> item == window
                || (item instanceof Dialog && !((Dialog) item).isShowing())
                || (item instanceof PopupWindow && !((PopupWindow) item).isShowing()));
        backWindows.add(window);
    }

    /** Window callbacks retain their own cancel/dismiss listeners and preview rollback. */
    boolean closeTopBackWindow() {
        for (int i = backWindows.size() - 1; i >= 0; i--) {
            Object window = backWindows.get(i);
            if (window instanceof Dialog && ((Dialog) window).isShowing()) {
                ((Dialog) window).onBackPressed();
                return true;
            }
            if (window instanceof PopupWindow && ((PopupWindow) window).isShowing()) {
                ((PopupWindow) window).dismiss();
                return true;
            }
            backWindows.remove(i);
        }
        return false;
    }

    @Override public void onBackPressed() {
        if (exitInProgress) return;
        if (closeTopBackWindow()) return;
        BackNavigation.Page previous = navigation.previous();
        if (previous != null) navigate(previous, true);
        else if (tab != 0) navigate(new BackNavigation.Page(0, sourceTab, libraryFilter, 0), true);
        else confirmExit();
    }

    void confirmExit() {
        if (exitInProgress || (exitDialog != null && exitDialog.isShowing())) return;
        exitDialog = StudioDialogs.alert(this)
                .setTitle(tr("앱을 종료할까요?", "Exit Live Vault?"))
                .setMessage(tr("녹음 중인 오디오와 영상을 저장한 뒤 마이크와 재생을 모두 끄고 종료합니다. 미완성 프레이즈와 화음은 이전 녹음을 유지합니다.",
                        "Save active audio and video, then turn off the microphone and all playback and exit. Unfinished phrases and harmony parts keep the previous recording."))
                .setNegativeButton(tr("취소", "Cancel"), null)
                .setPositiveButton(tr("종료", "Exit"), (dialog, which) -> exitApp())
                .show();
    }

    boolean exiting() { return exitInProgress || exitCompleted || ExitCoordinator.isExitRequested(); }

    void exitApp() {
        if (exitInProgress || exitCompleted) return;
        if (exitResourcesStopped) { finishExitTask(); return; }
        exitInProgress = true;
        ExitCoordinator.requestExit();
        exitSaveError = "";
        composerErrorBeforeExit = composer.error;
        harmonyErrorBeforeExit = harmony.error;
        exitProgress = StudioDialogs.alert(this)
                .setTitle(tr("저장 후 종료 중", "Saving and exiting"))
                .setMessage(tr("저장이 끝날 때까지 기다려 주세요.", "Please wait until saving finishes."))
                .setCancelable(false).show();
        pendingAction = 0;
        clearRecordingRequest();
        awaitingMicPermission = pendingMonitor = starting = false;
        handler.removeCallbacksAndMessages(null);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (drawerPopup != null) drawerPopup.dismiss();
        youtube.destroy();
        AudioService.youtubeSelected = false;
        stopPlayer();
        if (testActive) finishSyncTest(false);
        if (camera != null && camera.recording) stopVideo();
        videoPreparing = false;
        if (camera != null) { camera.release(); camera = null; }
        if (composer.capturing) composer.poll();
        if (composer.capturing) composer.cancelPhrase();
        harmony.poll(); // Queue a layer that completed just before capture stopped.
        // Close recording writers and release live streams immediately. Disk export/mux
        // can take time, and must not keep the microphone or countdown running.
        AudioService.requestAppExit(this, error -> {
            if (error != null && !error.isEmpty()) exitSaveError += error + "\n";
            // Document-provider exports survive recreation; finish their authorized writes too.
            ComposerExchange.afterPendingExports(exportError -> {
                if (!exportError.isEmpty()) exitSaveError += exportError;
                // This marker follows queued decode/export/mux jobs and their UI completions.
                io.execute(() -> handler.post(() -> composer.drainForExit(() ->
                    harmony.drainForExit(() -> {
                        exitResourcesStopped = true;
                        if (!composer.error.isEmpty() && !composer.error.equals(composerErrorBeforeExit)) exitSaveError += composer.error + "\n";
                        if (!harmony.error.isEmpty() && !harmony.error.equals(harmonyErrorBeforeExit)) exitSaveError += harmony.error + "\n";
                        completeExit();
                    }))));
            });
        });
    }

    void completeExit() {
        if (exitProgress != null) exitProgress.dismiss();
        if (!exitSaveError.isEmpty()) {
            exitInProgress = false;
            StudioDialogs.alert(this)
                    .setTitle(tr("저장 결과를 확인해 주세요", "Check the saved files"))
                    .setMessage(exitSaveError + tr("\n저장 가능한 파일은 앱 보관함에 남아 있으며, 결합하지 못한 원본 영상은 앱 저장 폴더에 보관됩니다. 오류를 확인한 뒤 종료하세요.",
                            "\nAvailable saved files remain in the library; unmerged original video files remain in app storage. Review the error before exiting."))
                    .setPositiveButton(tr("확인 후 종료", "Close after review"), (d, w) -> finishExitTask())
                    .setCancelable(false).show();
            return;
        }
        finishExitTask();
    }

    void finishExitTask() {
        ExitCoordinator.completeExit();
        exitCompleted = true;
        exitInProgress = false;
        io.shutdown();
        finishAndRemoveTask();
    }

    /** Kept behind the SDK guard so API 28 never resolves android.window classes. */
    @android.annotation.TargetApi(33)
    private static final class ModernBack {
        final android.window.OnBackInvokedDispatcher dispatcher;
        final android.window.OnBackInvokedCallback callback;
        ModernBack(MainActivity activity) {
            dispatcher = activity.getOnBackInvokedDispatcher();
            callback = activity::onBackPressed;
            dispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback);
        }
        void unregister() { dispatcher.unregisterOnBackInvokedCallback(callback); }
    }

    void showTab() {
        setVolumeControlStream(AudioRouting.selected(this) == 1 ? AudioManager.STREAM_VOICE_CALL : AudioManager.STREAM_MUSIC);
        if (camera != null && camera.recording) return;
        youtube.detach();
        if (camera != null) {
            camera.release();
            camera = null;
        }
        liveScreen = null;
        recButton = globalRecord;
        recordOptions = globalOptions;
        timer = globalTimer;
        playerTime = null;
        playerLine = null;
        liveState = null;
        recordCaption = null;
        presetChip = null;
        outputButton = null;
        takePlayButtons.clear();
        takePlaybackStatus.clear();
        content = page;
        page.maxContentWidthDp = tabletLandscape() && (tab == 1 || tab == 2 || tab == 4 || tab == 5 || tab == 6 || tab == 9) ? 880 : 0;
        page.flexible(null, 0, 0);
        content.removeAllViews();
        actionDock.removeAllViews();
        actionDock.setVisibility(tab == 0 || tab == 3 || tab == 5 || tab == 7 || tab == 8 || tab == 9 ? View.VISIBLE : View.GONE);
        pageScroll.scrollTo(0, 0);
        visual = null;
        pitch = null;
        pitchDetails = null;
        pitchMeter = null;
        texture = null;
        micButton = null;
        monitorButton = null;
        status = null;
        cameraTimer = null;
        videoButton = null;
        pauseButton = null;
        flipButton = null;
        levelText = null;

        int selected = tab == 7 || tab == 8 ? 1 : tab == 4 ? 2 : tab == 0 ? 0 : -1;
        for (int i = 0; i < nav.getChildCount(); i++) {
            Button b = (Button) nav.getChildAt(i);
            boolean active = i == selected;
            b.setSelected(active);
            b.setBackgroundTintList(null);
            b.setBackground(StudioTheme.background(this, active ? LIME : RAISED, 7));
            b.setTextColor(active ? StudioTheme.ACCENT_INK : TEXT);
        }

        if (tab == 0) studio();
        else if (tab == 1) backing();
        else if (tab == 2) midi();
        else if (tab == 3) cameraPage();
        else if (tab == 5) practicePage();
        else if (tab == 6) audioSettings();
        else if (tab == 7) composerScreen.draw(false);
        else if (tab == 8) composerScreen.draw(true);
        else if (tab == 9) harmonyScreen.draw();
        else library();
        renderedPage = new BackNavigation.Page(tab, sourceTab, libraryFilter, 0);
        updateTransportVisibility();
    }

    void set(int k, float v) {
        if ((k >= 2 && k <= 10) || k == 18) {
            selectedPreset = -1;
            getPreferences(0).edit().putInt("preset", -1).apply();
            if (presetChip != null) presetChip.setText(tr("사용자 설정 ▾", "Custom ▾"));
        }
        values[k] = v;
        NativeAudio.set(k, v);
        if (k == 1 && youtube != null) youtube.volume(v);
        if (k == 11) {
            NativeComposer.monitor(v > 0);
            harmony.monitor(v > 0);
        }
        if (k != 11 && k != 12 && k != 13) {
            getPreferences(0).edit().putFloat("p" + k, v).apply();
        }
        if (k == 19 && AudioService.active) {
            startService(new Intent(this, AudioService.class).putExtra("exitGeneration", ExitCoordinator.generation()).setAction(AudioService.EFFECTS));
        }
    }

    interface Formatter {
        String value(float v);
    }

    void slider(String label, int k, float min, float max, Formatter f) {
        StudioFader control = new StudioFader(this, label, min, max, values[k], f, v -> set(k, v));
        content.addView(control, new LinearLayout.LayoutParams(-1, -2));
    }

    void toggle(String label, int k) {
        Switch s = new Switch(this);
        s.setText(label);
        s.setTextColor(TEXT);
        s.setTextSize(14);
        s.setPadding(0, dp(10), 0, dp(10));
        s.setChecked(values[k] > .5);
        s.setOnCheckedChangeListener((b, on) -> {
            if (k == 11 && on && !AudioService.canMonitor(this)) {
                NativeAudio.monitorLevel(.22f);
            }
            set(k, on ? 1 : 0);
        });
        add(s);
    }

    String presetName() {
        return selectedPreset >= 0
                ? new String[]{
                        tr("노래방", "Karaoke"),
                        tr("발라드", "Ballad"),
                        tr("콘서트 홀", "Concert hall"),
                        tr("팝 보컬", "Pop vocal"),
                        tr("대성당", "Cathedral"),
                        tr("생목소리", "Dry voice")
                }[selectedPreset]
                : tr("사용자 설정", "Custom");
    }

    void presetPicker() {
        StudioDialogs.choose(
                this,
                tr("보컬 프리셋", "Vocal preset"),
                new String[]{
                        tr("노래방", "Karaoke"),
                        tr("발라드", "Ballad"),
                        tr("콘서트 홀", "Concert hall"),
                        tr("팝 보컬", "Pop vocal"),
                        tr("대성당", "Cathedral"),
                        tr("생목소리", "Dry voice")
                },
                selectedPreset,
                this::preset
        );
    }

    void studio() {
        liveScreen = new LiveScreen(this);
        liveScreen.draw();
    }

    void addModeSelector(boolean video) {
        LinearLayout media = row();
        media.setPadding(dp(3), dp(3), dp(3), dp(3));
        media.setBackground(bg(RAISED, 10));
        StudioButton audio = StudioTheme.styled(this, tr("오디오", "Audio"), video ? StudioButton.QUIET : StudioButton.STANDARD, () -> chooseTab(0));
        audio.glyph("music");
        audio.setTextColor(video ? MUTED : LIME);
        StudioButton film = StudioTheme.styled(this, tr("영상", "Video"), video ? StudioButton.STANDARD : StudioButton.QUIET, () -> chooseTab(3));
        film.glyph("camera");
        film.setTextColor(video ? LIME : MUTED);
        media.addView(audio, new LinearLayout.LayoutParams(0, dp(48), 1));
        media.addView(film, new LinearLayout.LayoutParams(0, dp(48), 1));
        add(media);
    }

    void drawer() {
        LinearLayout panel = column();
        panel.setPadding(dp(18), dp(18), dp(18), dp(22));
        panel.setBackground(bg(PANEL, 16));
        int availableHeight = root.getHeight() - root.getPaddingTop() - root.getPaddingBottom();
        int h = Math.max(dp(160), availableHeight - dp(64));
        ScrollView menuScroll = new ScrollView(this);
        menuScroll.setFillViewport(true);
        menuScroll.setVerticalScrollBarEnabled(false);
        menuScroll.addView(panel);
        PopupWindow popup = new PopupWindow(
                menuScroll,
                Math.min(dp(332), root.getWidth() - root.getPaddingLeft() - root.getPaddingRight()),
                h,
                true
        );
        popup.setBackgroundDrawable(bg(PANEL, 16));
        popup.setElevation(dp(24));
        popup.setOutsideTouchable(true);
        drawerPopup = popup;
        trackBackWindow(popup);
        root.setForeground(new ColorDrawable(0x88000000));
        popup.setOnDismissListener(() -> {
            root.setForeground(null);
            drawerPopup = null;
        });

        LinearLayout close = row();
        close.setGravity(Gravity.END);
        close.addView(new Space(this), new LinearLayout.LayoutParams(0, 1, 1));
        close.addView(StudioTheme.icon(this, "close", tr("메뉴 닫기", "Close menu"), popup::dismiss), new LinearLayout.LayoutParams(dp(48), dp(48)));
        panel.addView(close);

        ImageView mark = new ImageView(this);
        mark.setImageResource(R.drawable.live_vault_mark);
        mark.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams markParams = new LinearLayout.LayoutParams(dp(72), dp(72));
        markParams.gravity = Gravity.CENTER;
        markParams.topMargin = dp(2);
        panel.addView(mark, markParams);

        TextView name = text("Live Vault", 22, TEXT);
        name.setTypeface(StudioTheme.bold(this));
        name.setGravity(Gravity.CENTER);
        panel.addView(name, new LinearLayout.LayoutParams(-1, dp(40)));

        // Hamburger Menu with Live, Backing, Composer, Library, and Studio Tools
        String[] names = {
                tr("라이브", "Live"),
                tr("반주", "Backing"),
                tr("작곡기", "Composer"),
                tr("보관함", "Library"),
                tr("오디오 설정", "Audio settings"),
                tr("녹음 설정", "Recording settings"),
                tr("보컬 연습", "Vocal practice"),
                tr("보컬 화음", "Vocal harmony"),
                tr("도움말", "Help"),
                tr("종료", "Exit")
        };
        String[] subtitles = {
                tr("보컬 효과 · 실시간 모니터링 · 녹음", "Vocal FX · live monitoring · recording"),
                tr("내 파일 · 작곡기 · 온라인", "My files · Composer · Online"),
                tr("코드 진행 · AI 반주 제작", "Chord progression · AI backing"),
                tr("녹음 테이크 · 내 반주 프로젝트", "Recorded takes · saved projects"),
                tr("출력 · 레이턴시 · 상세 효과", "Output · latency · effects"),
                tr("예약 녹음 · 저장 옵션", "Recording timer · storage"),
                tr("스케일 · 피치 · MIDI", "Scales · pitch · MIDI"),
                tr("목소리 화음과 루프", "Vocal harmonies and loops"),
                tr("사용 방법 · 앱 정보", "Guide · about"),
                tr("저장 후 마이크와 재생 모두 끄기", "Save and turn off microphone and playback")
        };
        String[] glyphs = {"mic", "music", "piano", "folder", "settings", "record", "piano", "music", "help", "close"};

        for (int i = 0; i < names.length; i++) {
            final int k = i;
            LinearLayout item = row();
            item.setPadding(dp(8), dp(11), dp(8), dp(11));
            boolean currentItem = (k == 0 && tab == 0)
                    || (k == 1 && tab == 1)
                    || (k == 2 && (tab == 7 || tab == 8))
                    || (k == 3 && tab == 4)
                    || (k == 4 && tab == 6)
                    || (k == 6 && tab == 5)
                    || (k == 7 && tab == 9);
            ImageView icon = new ImageView(this);
            icon.setImageDrawable(new StudioGlyph(glyphs[i], currentItem ? LIME : (i == 6 ? RED : 0xffbcc5d4)));
            item.addView(icon, new LinearLayout.LayoutParams(dp(28), dp(28)));

            LinearLayout copy = column();
            copy.setPadding(dp(14), 0, dp(8), 0);
            TextView itemTitle = text(names[i], 16, currentItem ? LIME : TEXT);
            itemTitle.setTypeface(StudioTheme.bold(this));
            copy.addView(itemTitle);
            TextView detail = text(subtitles[i], 11, MUTED);
            detail.setPadding(0, dp(3), 0, 0);
            copy.addView(detail);
            item.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));

            ImageView arrow = new ImageView(this);
            arrow.setImageDrawable(new StudioGlyph("arrow", currentItem ? LIME : MUTED));
            item.addView(arrow, new LinearLayout.LayoutParams(dp(16), dp(16)));
            item.setFocusable(true);
            item.setContentDescription(names[i] + ", " + subtitles[i]);
            item.setOnClickListener(v -> {
                popup.dismiss();
                if (k == 0) chooseTab(0);
                else if (k == 1) chooseBacking();
                else if (k == 2) chooseTab(7);
                else if (k == 3) chooseTab(4);
                else if (k == 4) chooseTab(6);
                else if (k == 5) recordingOptions();
                else if (k == 6) chooseTab(5);
                else if (k == 7) chooseTab(9);
                else if (k == 9) confirmExit();
                else {
                    AlertDialog dialog = StudioDialogs.darkAlert(this)
                            .setTitle("Live Vault")
                            .setMessage(tr(
                                    "효과와 함께 듣고, 녹음하고, 음정을 연습하세요.\n\n스피커는 낮은 출력에서 시작하세요. 블루투스 전송 지연은 기기마다 다릅니다.\nNoto Sans KR · Roboto Mono / SIL OFL",
                                    "Listen with effects, record, and practice pitch.\n\nStart speakers at low volume. Bluetooth delay varies by device.\nNoto Sans KR · Roboto Mono / SIL OFL"
                            ))
                            .setPositiveButton(tr("닫기", "Close"), null)
                            .create();
                    StudioDialogs.showDark(this, dialog);
                }
            });
            item.setMinimumHeight(dp(68));
            panel.addView(item, new LinearLayout.LayoutParams(-1, -2));

            View separator = new View(this);
            separator.setBackgroundColor(0x193b4657);
            panel.addView(separator, new LinearLayout.LayoutParams(-1, dp(1)));
        }

        Button language = StudioTheme.styled(
                this,
                tr("언어 · 한국어 / English", "Language · English / 한국어"),
                StudioButton.STANDARD,
                () -> {
                    popup.dismiss();
                    StudioDialogs.choose(
                            this,
                            tr("언어", "Language"),
                            new String[]{"한국어", "English"},
                            AppLanguage.english(this) ? 1 : 0,
                            n -> {
                                boolean next = n == 1;
                                if (next == AppLanguage.english(this)) return;
                                AppLanguage.set(this, next);
                                for (int i = 0; i < nav.getChildCount(); i++) {
                                    Button b = (Button) nav.getChildAt(i);
                                    String label = new String[]{tr("라이브", "Live"), tr("작곡기", "Composer"), tr("보관함", "Library")}[i];
                                    b.setText(label);
                                    b.setContentDescription(label);
                                }
                                headerMute.setText(tr("출력 끄기", "Mute output"));
                                globalOptions.setContentDescription(tr("녹음 설정 · 시작 시간 및 다른 앱 소리", "Recording settings · start time and other app audio"));
                                int y = pageScroll.getScrollY(), current = tab;
                                if (camera != null && camera.recording) {
                                    toast(tr("영상 촬영을 마친 뒤 화면에 언어가 적용됩니다.", "The screen language will update after video recording ends."));
                                    return;
                                }
                                showTab();
                                pageScroll.post(() -> {
                                    if (tab == current) pageScroll.scrollTo(0, y);
                                });
                            }
                    );
                }
        );
        LinearLayout.LayoutParams langLp = new LinearLayout.LayoutParams(-1, dp(50));
        langLp.topMargin = dp(8);
        panel.addView(language, langLp);

        drawerLeft = Math.max(dp(8), root.getPaddingLeft() - dp(12));
        drawerTop = root.getPaddingTop() + dp(52);
        popup.showAtLocation(root, Gravity.TOP | Gravity.START, drawerLeft, drawerTop);
    }

    boolean settingsLocked() {
        return testActive
                || RecordingManager.active
                || AudioService.waiting()
                || videoPreparing
                || (AudioService.practice != null && AudioService.practice.active)
                || (composer != null && composer.capturing)
                || (harmony != null && (harmony.state[4] >= 0 || harmony.state[5] >= 0));
    }

    void audioSettings() {
        LinearLayout topRow = row();
        topRow.addView(title(tr("오디오 설정", "Audio settings")), new LinearLayout.LayoutParams(0, -2, 1));
        StudioButton backBtn = StudioTheme.styled(this, tr("라이브로 ›", "To Live ›"), StudioButton.QUIET, () -> chooseTab(0));
        backBtn.setTextColor(LIME);
        topRow.addView(backBtn, new LinearLayout.LayoutParams(-2, dp(48)));
        add(topRow);

        // Device & Route Card
        LinearLayout routeCard = beginCard();
        add(text(tr("입출력 경로", "Input & Output Route"), 16, TEXT));
        add(button(tr("마이크 소스: ", "Input device: ") + inputName() + " ▾", this::selectInput));
        add(button(tr("출력 소스: ", "Output device: ") + outputName() + " ▾", this::outputMode));
        add(button(tr("출력 및 하울링 보호 설정 ›", "Output & feedback protection ›"), this::monitoringOptions));
        endCard(routeCard);

        // 4 Core Switches from Reference Design
        LinearLayout switchCard = beginCard();
        add(text(tr("음성 및 입력 처리", "Voice & Input Processing"), 16, TEXT));
        toggle(tr("내 목소리 듣기", "Voice monitoring"), 11);
        toggle(tr("소음 완화", "Noise reduction"), 19);

        Switch echoSw = new Switch(this);
        echoSw.setText(tr("에코 제거", "Echo cancellation"));
        echoSw.setTextColor(TEXT);
        echoSw.setTextSize(14);
        echoSw.setPadding(0, dp(10), 0, dp(10));
        echoSw.setChecked(AudioRouting.echoCancellationPreferred(this));
        echoSw.setOnCheckedChangeListener((b, on) -> {
            AudioRouting.setEchoCancellationPreferred(this, on);
            if (AudioService.active) {
                startService(new Intent(this, AudioService.class).putExtra("exitGeneration", ExitCoordinator.generation()).setAction(AudioService.EFFECTS));
            }
        });
        add(echoSw);

        Switch agcSw = new Switch(this);
        agcSw.setText(tr("자동 입력 레벨", "Auto gain control"));
        agcSw.setTextColor(TEXT);
        agcSw.setTextSize(14);
        agcSw.setPadding(0, dp(10), 0, dp(10));
        agcSw.setChecked(AudioRouting.autoGainPreferred(this));
        agcSw.setOnCheckedChangeListener((b, on) -> {
            AudioRouting.setAutoGainPreferred(this, on);
            if (AudioService.active) {
                startService(new Intent(this, AudioService.class).putExtra("exitGeneration", ExitCoordinator.generation()).setAction(AudioService.EFFECTS));
            }
        });
        add(agcSw);
        endCard(switchCard);

        // Latency & Sync Card
        LinearLayout latencyCard = beginCard();
        add(text(tr("실시간 레이턴시", "Live latency"), 16, TEXT));
        note(tr("낮추면 반응이 빨라지고, 높이면 끊김이 줄어듭니다. 블루투스 전송 지연은 남습니다.", "Lower values respond faster; higher values can reduce dropouts. Bluetooth delay remains."));
        int mode = AudioRouting.selected(this);
        int count = AudioRouting.prefs(this).getInt("bursts" + mode, 2);
        TextView latency = text("", 13, LIME);
        add(latency);
        updateLatency(latency);

        LinearLayout presets = row();
        String[] names = {tr("반응 우선", "Low latency"), tr("균형", "Balanced"), tr("안정", "Stable")};
        int[] bs = {1, 2, 4};
        for (int i = 0; i < 3; i++) {
            final int n = bs[i];
            weighted(presets, button(names[i], () -> applyLatency(mode, n, latency)), 48);
        }
        add(presets);

        SeekBar buffer = new SeekBar(this);
        buffer.setMax(7);
        buffer.setProgress(count - 1);
        buffer.setEnabled(!settingsLocked());
        buffer.setContentDescription(tr("출력 버퍼 단계 1에서 8", "Output buffer level 1 to 8"));
        buffer.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onStartTrackingTouch(SeekBar b) {}

            @Override
            public void onProgressChanged(SeekBar b, int p, boolean u) {}

            @Override
            public void onStopTrackingTouch(SeekBar b) {
                applyLatency(mode, b.getProgress() + 1, latency);
            }
        });
        add(buffer);
        note(tr("표시는 실제 적용된 출력 버퍼입니다. 장치가 요청값을 제한할 수 있습니다.", "The display shows the applied output buffer. The device may limit the requested value."));

        add(text(tr("녹음 싱크 보정", "Recording sync adjustment"), 16, TEXT));
        int offset = AudioRouting.prefs(this).getInt("sync" + mode, 0);
        TextView sync = text(tr("보컬 보정: ", "Vocal offset: ") + offset + " ms", 13, TEXT);
        add(sync);
        SeekBar shift = new SeekBar(this);
        shift.setMax(200);
        shift.setProgress((offset + 500) / 5);
        shift.setEnabled(!settingsLocked());
        shift.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onStartTrackingTouch(SeekBar b) {}

            @Override
            public void onStopTrackingTouch(SeekBar b) {}

            @Override
            public void onProgressChanged(SeekBar b, int v, boolean u) {
                if (u && !settingsLocked()) {
                    int ms = v * 5 - 500;
                    AudioRouting.prefs(MainActivity.this).edit().putInt("sync" + mode, ms).apply();
                    sync.setText(tr("보컬 보정: ", "Vocal offset: ") + ms + " ms");
                }
            }
        });
        add(shift);
        note(tr("− 보컬 앞당김 / + 보컬 늦춤. 다음 녹음에 적용하며 실시간 목소리에는 지연을 추가하지 않습니다.", "− shifts vocals earlier / + shifts them later in the next recording; live monitoring is unchanged."));
        add(button(tr("싱크 테스트 · 5초", "Sync test · 5 seconds"), this::syncTest));
        endCard(latencyCard);

        // Detailed Effects & EQ Card
        LinearLayout fxCard = beginCard();
        add(text(tr("상세 효과 및 EQ", "Detailed Effects & EQ"), 16, TEXT));
        slider(tr("마이크 볼륨", "Microphone volume"), 0, 0, 2, v -> Math.round(v * 100) + "%");
        slider(tr("반주 볼륨", "Backing volume"), 1, 0, 1, v -> Math.round(v * 100) + "%");
        slider(tr("리버브 양", "Reverb amount"), 2, 0, 1, v -> Math.round(v * 100) + "%");
        slider(tr("리버브 잔향", "Reverb decay"), 3, .3f, 6, v -> String.format(Locale.US, "%.1f s", v));
        slider(tr("에코 양", "Echo level"), 4, 0, .8f, v -> Math.round(v * 100) + "%");
        slider(tr("에코 간격", "Echo time"), 5, 70, 700, v -> Math.round(v) + " ms");
        slider(tr("에코 반복", "Echo feedback"), 6, 0, .7f, v -> Math.round(v * 100) + "%");
        slider("Low EQ", 7, -12, 12, v -> Math.round(v) + " dB");
        slider("Mid EQ", 8, -12, 12, v -> Math.round(v) + " dB");
        slider("High EQ", 9, -12, 12, v -> Math.round(v) + " dB");
        toggle(tr("컴프레서", "Compressor"), 10);
        add(button(
                tr("리버브 공간", "Reverb space"),
                () -> StudioDialogs.choose(
                        this,
                        tr("리버브 공간", "Reverb space"),
                        new String[]{tr("홀", "Hall"), tr("룸", "Room"), tr("플레이트", "Plate"), tr("대성당", "Cathedral")},
                        Math.round(values[18]),
                        w -> set(18, w)
                )
        ));
        endCard(fxCard);
    }

    void updateLatency(TextView label) {
        int[] a = NativeAudio.latencyInfo();
        label.setText(
                a[0] > 0
                        ? String.format(
                                Locale.US,
                                tr("출력 버퍼 %.1f ms · %d 프레임 · 끊김 %s", "Output buffer %.1f ms · %d frames · dropouts %s"),
                                1000.0 * a[2] / a[3],
                                a[2],
                                a[4] >= 0 ? String.valueOf(a[4]) : tr("측정 불가", "Unavailable")
                        )
                        : tr("마이크를 켜면 적용 버퍼를 확인할 수 있습니다.", "Turn on the microphone to see the applied buffer.")
        );
    }

    void applyLatency(int mode, int n, TextView label) {
        if (settingsLocked()) {
            toast(tr("녹음·예약·연습을 마친 뒤 변경하세요.", "Finish recording, scheduling or practice before changing this."));
            return;
        }
        if (NativeAudio.bufferBursts(n)) {
            AudioRouting.prefs(this).edit().putInt("bursts" + mode, n).apply();
            updateLatency(label);
        } else {
            toast(tr("기기에서 버퍼 변경을 허용하지 않았습니다.", "The device did not allow the buffer change."));
        }
    }

    void practicePage() {
        add(title(tr("보컬 연습", "Vocal practice")));
        note(tr("한 음씩 천천히 따라 불러보세요.", "Sing each note slowly."));
        trainingReadout = null;
        LinearLayout practiceCard = beginCard();
        trainingReadout = text(tr("목표음 · 내 피치\n연습을 시작하세요", "Target · my pitch\nStart practice"), 22, LIME);
        trainingReadout.setMinHeight(dp(96));
        trainingReadout.setGravity(Gravity.CENTER);
        add(trainingReadout);
        practicePlot = new PracticePlot(this);
        practicePlot.root = practiceRoot;
        content.addView(practicePlot, new LinearLayout.LayoutParams(-1, dp(128)));
        endCard(practiceCard);

        practiceButton = button(
                AudioService.practice != null && AudioService.practice.active
                        ? tr("연습 종료 · 결과 저장", "End practice · save results")
                        : tr("연습 시작", "Start practice"),
                () -> {
                    if (AudioService.practice != null && AudioService.practice.active) {
                        AudioService.practice.stop(false);
                        showTab();
                    } else if (!settingsLocked()) {
                        ensureMic(5);
                    } else {
                        toast(tr("현재 녹음 또는 예약을 마친 뒤 시작하세요.", "Finish the current recording or scheduled recording first."));
                    }
                }
        );
        practiceButton.setTextColor(StudioTheme.ACCENT_INK);
        practiceButton.setBackgroundTintList(ColorStateList.valueOf(LIME));
        practiceButton.setMinHeight(dp(54));
        practiceButton.setPadding(dp(16), dp(12), dp(16), dp(12));
        actionDock.addView(practiceButton, new LinearLayout.LayoutParams(-1, -2));

        add(text(tr("연습 설정", "Practice settings"), 17, TEXT));
        add(button(
                practiceMode == 0
                        ? tr("방식: 듣고 따라 부르기 ▾", "Mode: listen and repeat ▾")
                        : tr("방식: 함께 부르기 ▾", "Mode: sing along ▾"),
                () -> StudioDialogs.choose(
                        this,
                        tr("연습 방식", "Practice mode"),
                        new String[]{tr("듣고 따라 부르기", "Listen and repeat"), tr("함께 부르기", "Sing along")},
                        practiceMode,
                        w -> {
                            if (!settingsLocked()) {
                                practiceMode = w;
                                showTab();
                            }
                        }
                )
        ));
        add(button(
                practicePattern == 0
                        ? tr("5음 상하행 ▾", "Five-note scale ▾")
                        : tr("장음계 1옥타브 ▾", "Major scale · one octave ▾"),
                () -> StudioDialogs.choose(
                        this,
                        tr("연습 음계", "Practice scale"),
                        new String[]{tr("5음 상하행", "Five-note scale"), tr("장음계 1옥타브", "Major scale · one octave")},
                        practicePattern,
                        w -> {
                            if (!settingsLocked()) {
                                practicePattern = w;
                                showTab();
                            }
                        }
                )
        ));
        add(button(
                tr("시작음: ", "Starting note: ") + noteName(practiceRoot) + " ▾",
                () -> {
                    String[] notes = new String[28];
                    for (int i = 0; i < 28; i++) notes[i] = noteName(i + 45);
                    StudioDialogs.choose(this, tr("시작음", "Starting note"), notes, practiceRoot - 45, w -> {
                        if (!settingsLocked()) {
                            practiceRoot = 45 + w;
                            showTab();
                        }
                    });
                }
        ));

        TextView tempo = text(tr("템포 ", "Tempo ") + practiceBpm + " BPM", 14, TEXT);
        add(tempo);
        SeekBar bpm = new SeekBar(this);
        bpm.setMax(16);
        bpm.setProgress((practiceBpm - 40) / 5);
        bpm.setEnabled(!settingsLocked());
        bpm.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onStartTrackingTouch(SeekBar b) {}

            @Override
            public void onStopTrackingTouch(SeekBar b) {}

            @Override
            public void onProgressChanged(SeekBar b, int p, boolean u) {
                if (u) {
                    practiceBpm = 40 + p * 5;
                    tempo.setText(tr("템포 ", "Tempo ") + practiceBpm + " BPM");
                }
            }
        });
        add(bpm);

        Switch capture = new Switch(this);
        capture.setText(tr("연습 녹음 · WAV + MIDI + 결과", "Record practice · WAV + MIDI + results"));
        capture.setTextColor(TEXT);
        capture.setChecked(practiceRecord);
        capture.setEnabled(!settingsLocked());
        capture.setOnCheckedChangeListener((b, on) -> practiceRecord = on);
        add(capture);

        add(button(tr("보컬 화음 쌓기 ›", "Layer vocal harmonies ›"), () -> chooseTab(9)));
        add(button(tr("목소리 악기 ›", "Voice instrument ›"), () -> chooseTab(8)));
        add(button(tr("자유 음정 · MIDI ›", "Free pitch · MIDI ›"), () -> chooseTab(2)));
        add(button(tr("연습 결과 보기", "View practice results"), () -> showPracticeResult(AudioService.practice == null ? null : AudioService.practice.result)));
        if (AudioService.practice != null && !AudioService.practice.error.isEmpty()) {
            note(AudioService.practice.error);
        }
        if (AudioService.practice != null && AudioService.practice.result != null) {
            JSONObject result = AudioService.practice.result;
            note(result.has("accuracy")
                    ? String.format(
                            Locale.US,
                            tr("정확도 %.1f%% · 평균 오차 %.1f¢ · 발성 %.1f%%", "Accuracy %.1f%% · mean error %.1f¢ · voiced %.1f%%"),
                            result.optDouble("accuracy"),
                            result.optDouble("meanAbsoluteCents"),
                            result.optDouble("coverage")
                    )
                    : tr("유효 발성이 부족해 정확도를 계산하지 않았습니다.", "Not enough valid vocal input to calculate accuracy."));
            add(button(tr("보관함 ›", "Library ›"), () -> chooseTab(4)));
        }
        note(tr("목표음 대비 ±25센트 기준. 스피커·블루투스의 함께 부르기는 화면 가이드로 진행합니다.", "Scoring uses ±25 cents from the target. When using speakers or Bluetooth, follow the on-screen guide."));
    }

    String noteName(int n) {
        return new String[]{"C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B"}[n % 12] + (n / 12 - 1);
    }

    void showPracticeResult(JSONObject result) {
        if (result == null) {
            toast(tr("연습을 마치면 결과를 확인할 수 있습니다.", "Results appear after practice."));
            return;
        }
        List<String> lines = new ArrayList<>();
        lines.add(result.has("accuracy")
                ? String.format(
                        Locale.US,
                        tr("정확도 %.1f%% · 평균 오차 %.1f¢", "Accuracy %.1f%% · mean error %.1f¢"),
                        result.optDouble("accuracy"),
                        result.optDouble("meanAbsoluteCents")
                )
                : tr("유효 발성이 부족합니다.", "Not enough valid vocal input."));
        lines.add(String.format(Locale.US, tr("발성 비율 %.1f%%", "Voiced %.1f%%"), result.optDouble("coverage")));
        JSONArray notes = result.optJSONArray("notes");
        if (notes != null) {
            for (int i = 0; i < notes.length(); i++) {
                JSONObject n = notes.optJSONObject(i);
                if (n != null) {
                    lines.add(noteName(n.optInt("targetMidi")) + " · "
                            + (n.has("accuracy")
                            ? String.format(Locale.US, "%.1f%% · %.1f¢", n.optDouble("accuracy"), n.optDouble("meanAbsoluteCents"))
                            : tr("발성 부족", "Not enough vocal input")));
                }
            }
        }
        StudioDialogs.alert(this)
                .setTitle(result.optBoolean("complete") ? tr("연습 완료", "Practice complete") : tr("부분 연습 결과", "Partial practice results"))
                .setMessage(TextUtils.join("\n", lines))
                .setPositiveButton(tr("닫기", "Close"), null)
                .show();
    }

    void syncTest() {
        if (settingsLocked()) return;
        ensureMic(6);
    }

    String inputName() {
        return AudioRouting.inputLabel(this);
    }

    String outputName() {
        return AudioRouting.outputLabel(this);
    }

    void monitoringOptions() {
        LinearLayout panel = column();
        panel.setPadding(dp(16), dp(8), dp(16), 0);
        Button route = button(tr("출력 모드: ", "Output mode: ") + outputName() + "  ›", () -> {});
        panel.addView(route);

        Button monitor = button("", () -> {});
        monitor.setText(NativeAudio.stats()[21] > .5 ? tr("내 목소리 듣기 · 켜짐", "Voice monitoring · on") : tr("내 목소리 듣기 · 꺼짐", "Voice monitoring · off"));
        monitor.setOnClickListener(v -> {
            if (NativeAudio.stats()[17] > .5) {
                NativeAudio.resetFeedback();
                NativeComposer.monitor(true);
                harmony.monitor(true);
                set(11, 1);
                monitor.setText(tr("내 목소리 듣기 · 켜짐", "Voice monitoring · on"));
                toast(tr("보호 음소거를 해제하고 출력을 재시작했습니다.", "Safety mute reset and live output restarted."));
                return;
            }
            if (AudioService.active) {
                boolean nextOn = !(NativeAudio.stats()[21] > .5);
                if (nextOn && !AudioService.canMonitor(this)) {
                    NativeAudio.monitorLevel(.22f);
                }
                set(11, nextOn ? 1 : 0);
                monitor.setText(nextOn ? tr("내 목소리 듣기 · 켜짐", "Voice monitoring · on") : tr("내 목소리 듣기 · 꺼짐", "Voice monitoring · off"));
                toast(values[11] > .5 ? tr("실시간 출력 켜짐", "Live output on") : tr("실시간 출력 꺼짐", "Live output off"));
            } else {
                toast(tr("마이크와 출력 장치를 먼저 켜세요.", "Turn on the microphone and output first."));
            }
        });
        panel.addView(monitor);

        TextView help = text(
                AudioRouting.selected(this) == 0
                        ? tr("유선 / USB / 블루투스 이어폰을 연결하면 더 선명하게 내 목소리를 실시간으로 들을 수 있습니다.", "Connect wired, USB, or Bluetooth headphones for clearer live voice monitoring.")
                        : tr("선택한 마이크로 입력합니다. 시스템 스피커 볼륨도 낮게 시작하고 마이크와 스피커를 떨어뜨리세요. 블루투스는 전송 지연이 있습니다.", "Uses the selected microphone. Start with low system volume and keep the microphone away from the speaker. Bluetooth adds delay."),
                13,
                MUTED
        );
        help.setPadding(0, dp(12), 0, dp(12));
        panel.addView(help);

        AlertDialog dialog = StudioDialogs.darkAlert(this)
                .setTitle(tr("출력 설정", "Output settings"))
                .setView(StudioDialogs.scrollable(this, panel))
                .setPositiveButton(tr("닫기", "Close"), null)
                .create();
        route.setEnabled(!settingsLocked());
        route.setOnClickListener(v -> {
            dialog.dismiss();
            outputMode();
        });
        panel.addView(button(tr("상세 효과 설정", "Detailed effects"), () -> {
            dialog.dismiss();
            chooseTab(6);
        }));

        if (AudioRouting.selected(this) > 0 || !AudioService.hasWiredOutput(this)) {
            int gain = AudioService.active ? Math.round(NativeAudio.stats()[20] * 100) : 18;
            TextView label = text(tr("스피커 마이크 출력 · ", "Speaker microphone output · ") + gain + "%", 14, TEXT);
            panel.addView(label);
            SeekBar level = new SeekBar(this);
            level.setMax(65);
            level.setProgress(gain);
            level.setEnabled(AudioService.active);
            level.setContentDescription(tr("스피커 마이크 출력, 최대 65퍼센트", "Speaker microphone output, maximum 65 percent"));
            level.setProgressTintList(ColorStateList.valueOf(LIME));
            level.setThumbTintList(ColorStateList.valueOf(LIME));
            level.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onStartTrackingTouch(SeekBar b) {}

                @Override
                public void onStopTrackingTouch(SeekBar b) {}

                @Override
                public void onProgressChanged(SeekBar b, int value, boolean user) {
                    if (user) {
                        NativeAudio.monitorLevel(value / 100f);
                        label.setText(tr("스피커 마이크 출력 · ", "Speaker microphone output · ") + value + "%");
                    }
                }
            });
            panel.addView(level, new LinearLayout.LayoutParams(-1, dp(48)));

            Button reset = button(tr("보호 음소거 해제 · 18%로 재시작", "Reset safety mute · restart at 18%"), () -> {
                NativeAudio.resetFeedback();
                NativeComposer.monitor(true);
                harmony.monitor(true);
                set(11, 1);
                dialog.dismiss();
                toast(tr("18% 출력으로 재시작합니다. 시스템 볼륨도 낮게 유지하세요.", "Restarting at 18% output. Keep the system volume low."));
            });
            reset.setEnabled(AudioService.active);
            panel.addView(reset);

            TextView protection = text(
                    tr("피드백을 감지하면 필터를 적용하고 필요 시 마이크 출력만 음소거합니다. 녹음은 계속됩니다.\n기기 에코 제거: ", "Feedback triggers filters and, if needed, mutes microphone output only. Recording continues.\nDevice echo cancellation: ")
                            + (AudioService.active
                            ? (AudioService.echoCancellation ? tr("활성", "Active") : tr("미지원", "Unsupported"))
                            : tr("마이크를 켜면 확인", "Available when microphone is on")),
                    12,
                    MUTED
            );
            protection.setPadding(0, dp(12), 0, dp(8));
            panel.addView(protection);
        }
        StudioDialogs.showDark(this, dialog);
    }

    void outputMode() {
        chooseDevice(false);
    }

    void chooseDevice(boolean input) {
        if (settingsLocked()) {
            toast(tr("예약 또는 녹음을 마친 뒤 장치를 바꾸세요.", "Finish recording or the countdown before changing devices."));
            return;
        }
        List<AudioDeviceInfo> devices = input ? AudioRouting.listInputs(this) : AudioRouting.listOutputs(this);
        String[] names = new String[devices.size() + 1];
        names[0] = tr("시스템 기본", "System default");
        int current = input ? AudioRouting.selectedInput(this) : AudioRouting.selectedOutput(this);
        int selected = current == 0 ? 0 : -1;
        for (int i = 0; i < devices.size(); i++) {
            AudioDeviceInfo device = devices.get(i);
            names[i + 1] = AudioRouting.deviceLabel(this, device);
            if (device.getId() == current) selected = i + 1;
        }
        StudioDialogs.choose(
                this,
                input ? tr("입력 장치", "Input device") : tr("출력 장치", "Output device"),
                names,
                selected,
                index -> {
                    if (settingsLocked()) return;
                    int id = index == 0 ? 0 : devices.get(index - 1).getId();
                    if (input) {
                        AudioRouting.chooseInput(this, id);
                        inputDevice = id;
                    } else {
                        AudioRouting.chooseOutput(this, id);
                    }
                    if (AudioService.active || AudioService.playbackActive) {
                        startService(new Intent(this, AudioService.class).putExtra("exitGeneration", ExitCoordinator.generation()).setAction(AudioService.ROUTE).putExtra("device", inputDevice));
                    }
                    showTab();
                }
        );
    }

    void recordingOptions() {
        if (settingsLocked()) {
            toast(tr("예약 또는 녹음을 마친 뒤 설정하세요.", "Finish recording or the countdown before changing settings."));
            return;
        }
        LinearLayout panel = column();
        panel.setPadding(dp(16), dp(12), dp(16), 0);
        panel.addView(text(tr("녹음 시작", "Start recording"), 15, TEXT));
        LinearLayout delays = row();
        int[] choice = {recordDelay};
        int[] options = {0, 3, 5, 10};
        for (int value : options) {
            StudioButton b = StudioTheme.styled(this, value == 0 ? tr("즉시", "Now") : value + tr("초", "seconds"), StudioButton.STANDARD, () -> {});
            b.setTextSize(13);
            StudioTheme.selected(b, recordDelay == value);
            b.setOnClickListener(v -> {
                choice[0] = value;
                for (int i = 0; i < delays.getChildCount(); i++) {
                    StudioTheme.selected((Button) delays.getChildAt(i), delays.getChildAt(i) == b);
                }
            });
            weighted(delays, b, 48);
        }
        panel.addView(delays);

        Switch source = new Switch(this);
        source.setText(tr("이번 녹음에 다른 앱 소리 포함", "Include other app audio for this recording"));
        source.setTextColor(TEXT);
        source.setChecked(captureOtherApps && !youtube.selected);
        source.setEnabled(!youtube.selected);
        source.setPadding(0, dp(12), 0, dp(8));
        if (CaptureSupport.available()) panel.addView(source);

        TextView details = text(
                youtube.selected
                        ? tr("YouTube 선택 시 마이크만 녹음합니다. YouTube 소리와 앱 내 악기·반주는 저장하지 않습니다. 스피커 소리는 마이크에 들어갈 수 있으므로 이어폰을 사용하세요.", "With YouTube selected, recordings contain only the microphone; YouTube audio and in-app instruments/backing are excluded. Use headphones to avoid speaker audio entering the microphone.")
                        : CaptureSupport.available()
                        ? tr("기본 녹음은 마이크와 앱 내 반주가 있으면 함께 담으며 Android 공유 권한이 필요하지 않습니다. 다른 앱 소리는 이번 녹음에만 선택하며 시작할 때 공유 권한을 요청합니다.", "Normal recording includes the microphone and any in-app backing without Android sharing permission. Select other app audio for this recording only; permission is requested when recording starts.")
                        : tr("마이크와 앱 내 반주가 있으면 함께 녹음하며 Android 공유 권한이 필요하지 않습니다.", "Records the microphone and any in-app backing without Android sharing permission."),
                12,
                MUTED
        );
        panel.addView(details);

        TextView gain = text(tr("녹음에 담을 다른 앱 소리: ", "Other app audio in recording: ") + Math.round(captureGain * 100) + "%", 12, TEXT);
        SeekBar slider = new SeekBar(this);
        slider.setMax(150);
        slider.setProgress(Math.round(captureGain * 100));
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onStartTrackingTouch(SeekBar b) {}

            @Override
            public void onStopTrackingTouch(SeekBar b) {}

            @Override
            public void onProgressChanged(SeekBar b, int p, boolean user) {
                gain.setText(tr("녹음에 담을 다른 앱 소리: ", "Other app audio in recording: ") + p + "%");
            }
        });
        if (CaptureSupport.available()) {
            panel.addView(gain);
            panel.addView(slider);
        }
        panel.addView(text(
                tr("알림에서 예약 취소·녹음 저장이 가능합니다. 다른 앱 소리를 선택한 경우 카운트다운 중 해당 앱에서 재생하세요.", "Cancel or save from the notification. If other app audio is selected, start playback in that app during the countdown."),
                12,
                MUTED
        ));

        AlertDialog dialog = StudioDialogs.darkAlert(this)
                .setTitle(tr("백그라운드 녹음", "Background recording"))
                .setView(StudioDialogs.scrollable(this, panel))
                .setPositiveButton(tr("설정 저장", "Save settings"), (d, w) -> {
                    recordDelay = choice[0];
                    captureOtherApps = !youtube.selected && CaptureSupport.available() && source.isChecked();
                    captureGain = slider.getProgress() / 100f;
                    getPreferences(0)
                            .edit()
                            .putInt("recordDelay", recordDelay)
                            .remove("captureOtherApps")
                            .putFloat("captureGain", captureGain)
                            .apply();
                })
                .setNegativeButton(tr("취소", "Cancel"), null)
                .create();
        StudioDialogs.showDark(this, dialog);
    }

    void requestRecording() {
        if (exiting()) return;
        pendingCapture = !youtube.selected && captureOtherApps && CaptureSupport.available();
        captureOtherApps = false;
        continueRecording();
    }

    void clearRecordingRequest() {
        recordPermissionPending = false;
        recordingRequestStage = 0;
        pendingCapture = false;
    }

    void continueRecording() {
        if (exiting()) return;
        if (youtube.selected) pendingCapture = false;
        try {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                recordPermissionPending = true;
                recordingRequestStage = 1;
                requestPermissions(
                        Build.VERSION.SDK_INT >= 33
                                ? new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS}
                                : new String[]{Manifest.permission.RECORD_AUDIO},
                        12
                );
                return;
            }
            if (pendingCapture && CaptureSupport.available()) {
                recordPermissionPending = true;
                recordingRequestStage = 2;
                CaptureSupport.request(this, 30);
            } else {
                scheduleRecording(null);
            }
        } catch (Exception e) {
            clearRecordingRequest();
            toast(tr("녹음 권한 요청 실패: ", "Recording permission request failed: ") + e.getMessage());
        }
    }

    void scheduleRecording(Intent projection) {
        if (exiting()) return;
        if (youtube.selected) projection = null;
        clearRecordingRequest();
        pendingMonitor = AudioService.canMonitor(this) || values[11] > .5f;
        Intent intent = new Intent(this, AudioService.class).putExtra("exitGeneration", ExitCoordinator.generation())
                .setAction(AudioService.RECORD)
                .putExtra("device", inputDevice)
                .putExtra("delay", recordDelay)
                .putExtra("micOnly", youtube.selected)
                .putExtra("bpm", Math.round(values[15]))
                .putExtra("monitor", pendingMonitor)
                .putExtra("capture", projection != null && BuildConfig.PLAYBACK_CAPTURE)
                .putExtra("captureGain", captureGain);
        if (projection != null) intent.putExtra("projection", projection);
        try {
            startForegroundService(intent);
        } catch (Exception e) {
            toast(tr("녹음 예약 실패: ", "Could not schedule recording: ") + e.getMessage());
        }
    }

    void preset(int p) {
        selectedPreset = p;
        float[][] options = {
                {.2f, 1.5f, .34f, 300, .38f, 0, 0, 2},
                {.3f, 2.4f, .14f, 280, .25f, 0, -1, 2},
                {.42f, 3.7f, .1f, 340, .25f, 1, -1, 1},
                {.12f, .9f, .12f, 140, .18f, -1, 1, 3},
                {.58f, 5.5f, .15f, 410, .35f, 1, -2, 0},
                {0, .3f, 0, 150, 0, 0, 0, 0}
        };
        int[] keys = {2, 3, 4, 5, 6, 7, 8, 9};
        for (int i = 0; i < keys.length; i++) set(keys[i], options[p][i]);
        set(10, p == 5 ? 0 : 1);
        set(18, p == 4 ? 3 : p == 3 ? 2 : 0);
        selectedPreset = p;
        getPreferences(0).edit().putInt("preset", p).apply();
        showTab();
        toast(tr("프리셋을 적용했습니다.", "Preset applied."));
    }

    void chooseBacking() {
        if (settingsLocked()) {
            toast(tr("녹음 또는 예약을 마친 뒤 반주를 선택하세요.", "Finish recording or the countdown before choosing backing."));
            return;
        }
        if (sourceTab < 0 || sourceTab > 2) sourceTab = 1;
        chooseTab(1);
    }

    void backing() {
        LinearLayout topRow = row();
        topRow.addView(title(tr("반주", "Backing")), new LinearLayout.LayoutParams(0, -2, 1));
        StudioButton backBtn = StudioTheme.styled(this, tr("라이브로 ›", "To Live ›"), StudioButton.QUIET, () -> chooseTab(0));
        backBtn.setTextColor(LIME);
        topRow.addView(backBtn, new LinearLayout.LayoutParams(-2, dp(48)));
        add(topRow);

        LinearLayout tabs = row();
        String[] labels = {tr("내 파일", "My files"), tr("작곡기", "Composer"), tr("온라인", "Online")};
        int[] targets = {1, 0, 2};
        for (int i = 0; i < 3; i++) {
            final int src = targets[i];
            StudioButton b = StudioTheme.styled(this, labels[i], StudioButton.CHIP, () -> {
                chooseSource(src);
            });
            StudioTheme.selected(b, sourceTab == src);
            weighted(tabs, b, 48);
        }
        add(tabs);

        if (sourceTab == 1) {
            fileBacking();
            return;
        }
        if (sourceTab == 2) {
            youtubeBacking();
            return;
        }
        composerScreen.draw(false);
    }

    void fileBacking() {
        StudioButton importButton = StudioTheme.styled(this, tr("＋ 반주 가져오기", "＋ Import backing"), StudioButton.OUTLINE, this::pickBacking);
        importButton.setTextColor(LIME);
        add(importButton);
        TextView format = text("WAV · MP3 · M4A · FLAC", 11, MUTED);
        format.setGravity(Gravity.CENTER);
        add(format);

        TextView heading = text(tr("최근 반주", "Recent backing"), 17, TEXT);
        heading.setTypeface(StudioTheme.bold(this));
        heading.setPadding(0, dp(10), 0, dp(4));
        add(heading);

        List<JSONObject> items = recent.list();
        if (items.isEmpty()) {
            note(tr("연습할 반주 파일을 가져오세요. 불러온 파일이 여기에 모입니다.", "Import backing files for practice. Imported files appear here."));
        }
        for (JSONObject j : items) {
            Uri uri = Uri.parse(j.optString("uri"));
            String filename = j.optString("name"), ext = extension(filename);
            LinearLayout card = row();
            card.setPadding(dp(12), dp(10), dp(8), dp(10));
            card.setBackground(bg(PANEL, 12));
            ImageView icon = new ImageView(this);
            icon.setPadding(dp(9), dp(9), dp(9), dp(9));
            icon.setImageDrawable(new StudioGlyph("music", selectedFile.equals(filename) ? LIME : 0xffb8c5da));
            icon.setBackground(bg(RAISED, 8));
            card.addView(icon, new LinearLayout.LayoutParams(dp(42), dp(48)));

            LinearLayout copy = column();
            copy.setPadding(dp(12), 0, dp(4), 0);
            TextView name = text(filename, 15, selectedFile.equals(filename) ? LIME : TEXT);
            name.setTypeface(StudioTheme.medium(this));
            name.setSingleLine();
            name.setEllipsize(TextUtils.TruncateAt.END);
            copy.addView(name);
            TextView detail = text(
                    ext + " · " + new SimpleDateFormat("MM.dd", Locale.KOREA).format(new Date(j.optLong("time")))
                            + (selectedFile.equals(filename) ? tr(" · 선택됨", " · selected") : ""),
                    11,
                    MUTED
            );
            detail.setPadding(0, dp(4), 0, 0);
            copy.addView(detail);
            card.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
            card.setOnClickListener(v -> loadBacking(uri));

            Button play = StudioTheme.icon(
                    this,
                    selectedFile.equals(filename) && NativeAudio.stats()[16] > .5 ? "pause" : "play",
                    filename + tr(" 재생 / 일시정지", " play / pause"),
                    () -> {
                        if (selectedFile.equals(filename)) {
                            if (NativeAudio.stats()[16] > .5) NativeAudio.playLocal(false);
                            else ensureMic(4);
                            showTab();
                        } else {
                            loadBacking(uri, true);
                        }
                    }
            );
            card.addView(play, new LinearLayout.LayoutParams(dp(48), dp(48)));

            Button more = StudioTheme.icon(
                    this,
                    "more",
                    tr("최근 반주 메뉴", "Recent backing menu"),
                    () -> StudioDialogs.choose(
                            this,
                            filename,
                            new String[]{tr("반주 불러오기", "Load backing"), tr("최근 목록에서 삭제", "Remove from recents")},
                            -1,
                            w -> {
                                if (w == 0) loadBacking(uri);
                                else {
                                    recent.remove(uri);
                                    showTab();
                                }
                            }
                    )
            );
            card.addView(more, new LinearLayout.LayoutParams(dp(48), dp(48)));
            add(card);
        }

        if (!selectedFile.isEmpty()) {
            gap();
            LinearLayout prev = beginCard();
            LinearLayout info = row();
            LinearLayout copy = column();
            TextView name = text(selectedFile, 15, TEXT);
            name.setSingleLine();
            name.setEllipsize(TextUtils.TruncateAt.END);
            copy.addView(name);
            playerTime = text("", 11, MUTED);
            playerTime.setTypeface(StudioTheme.mono(this));
            playerTime.setPadding(0, dp(6), 0, 0);
            copy.addView(playerTime);
            info.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
            info.addView(
                    StudioTheme.icon(
                            this,
                            NativeAudio.stats()[16] > .5 ? "pause" : "play",
                            tr("선택한 반주 재생 / 일시정지", "Play / pause selected backing"),
                            () -> {
                                if (NativeAudio.stats()[16] > .5) NativeAudio.playLocal(false);
                                else ensureMic(4);
                                showTab();
                            }
                    ),
                    new LinearLayout.LayoutParams(dp(48), dp(48))
            );
            add(info);
            playerLine = new StudioPlaybackLine(this, fraction -> {
                if (!RecordingManager.active && !AudioService.waiting()) {
                    double[] infoNow = NativeAudio.localPlaybackInfo();
                    NativeAudio.seekLocal(fraction * infoNow[1]);
                }
            });
            content.addView(playerLine, new LinearLayout.LayoutParams(-1, dp(48)));
            slider(tr("반주 볼륨", "Backing volume"), 1, 0, 1, v -> Math.round(v * 100) + "%");
            endCard(prev);
        }
    }

    String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot >= 0 && dot < name.length() - 1 ? name.substring(dot + 1).toUpperCase(Locale.ROOT) : "AUDIO";
    }

    void pickBacking() {
        if (RecordingManager.active || AudioService.waiting() || busy || videoPreparing) {
            toast(tr("녹음과 파일 준비를 마친 뒤 선택하세요.", "Wait for recording and file preparation to finish."));
            return;
        }
        startActivityForResult(
                new Intent(Intent.ACTION_OPEN_DOCUMENT)
                        .setType("audio/*")
                        .addCategory(Intent.CATEGORY_OPENABLE)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION),
                20
        );
    }

    void loadBacking(Uri uri) {
        loadBacking(uri, false);
    }

    void loadBacking(Uri uri, boolean autoplay) {
        if (exiting()) return;
        if (RecordingManager.active || AudioService.waiting() || busy || videoPreparing) {
            toast(tr("녹음을 마친 뒤 반주를 불러오세요.", "Finish recording before loading a backing."));
            return;
        }
        busy = true;
        String name = recent.name(uri);
        toast(tr("반주 파일을 준비하고 있습니다…", "Preparing backing file…"));
        io.execute(() -> {
            File decodedFile = null;
            try {
                MediaTools.Decoded decoded = MediaTools.decode(this, uri);
                decodedFile = decoded.file;
                if (exiting()) return;
                if (!NativeAudio.loadLocalFile(decoded.file.getAbsolutePath(), decoded.rate, decoded.channels)) {
                    throw new IOException(tr("반주를 불러오지 못했습니다.", "Could not load backing."));
                }
                recent.remember(uri, name);
                runOnUiThread(() -> {
                    if (exiting()) return;
                    selectedFile = name;
                    toast(tr("반주를 불러왔습니다.", "Backing loaded."));
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (exiting()) return;
                    selectedFile = "";
                    StudioDialogs.alert(this)
                            .setTitle(tr("파일을 열 수 없어요", "Cannot open file"))
                            .setMessage(tr("파일이 이동·삭제되었거나 접근권한이 만료되었을 수 있습니다. 다시 가져와 주세요.", "The file may have moved, been deleted, or lost access. Import it again."))
                            .setPositiveButton(tr("확인", "OK"), null)
                            .setNegativeButton(tr("목록에서 삭제", "Remove from list"), (d, w) -> {
                                recent.remove(uri);
                                showTab();
                            })
                            .show();
                });
            } finally {
                if (decodedFile != null) decodedFile.delete();
                runOnUiThread(() -> {
                    busy = false;
                    if (exiting()) return;
                    if (autoplay && !selectedFile.isEmpty()) ensureMic(4);
                    if (tab == 1 || tab == 0) showTab();
                });
            }
        });
    }

    void youtubeBacking() {
        EditText link = new EditText(this);
        link.setSingleLine();
        link.setTextSize(14);
        link.setTextColor(TEXT);
        link.setHintTextColor(MUTED);
        link.setPadding(dp(14), dp(12), dp(14), dp(12));
        link.setBackground(bg(RAISED, 10));
        link.setHint(tr("YouTube 주소 / 동영상 ID / 검색어", "YouTube URL / video ID / search"));
        if (!youtube.id.isEmpty()) link.setText(youtube.id);
        add(link);
        add(button(tr("앱에서 동영상 선택", "Select video in app"), () -> selectYouTube(link.getText().toString())));
        youtube.attach(content);
        add(button(tr("YouTube에서 열기 ↗", "Open in YouTube ↗"), () -> {
            String value = link.getText().toString().trim();
            if (value.isEmpty()) value = tr("노래방 MR", "Karaoke backing");
            Uri uri = value.startsWith("https://youtu.be/") || value.startsWith("https://www.youtube.com/")
                    ? Uri.parse(value)
                    : Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(value));
            open(uri, "text/html");
        }));
        add(button(tr("SoundCloud에서 열기 ↗", "Open in SoundCloud ↗"), () -> open(Uri.parse("https://soundcloud.com/"), "text/html")));
        note(tr("YouTube는 화면에 표시될 때만 재생합니다. 탭 이동·백그라운드·플레이어가 가려지면 일시정지합니다. 녹음은 마이크만 저장하며 YouTube 소리는 포함하지 않습니다. 이어폰을 권장합니다.", "YouTube plays only while its video is visible. Changing tabs, backgrounding, or scrolling it out of view pauses playback. Recordings save the microphone only, excluding YouTube audio. Headphones recommended."));
        add(button("YouTube Terms of Service ↗", () -> open(Uri.parse("https://www.youtube.com/t/terms"), "text/html")));
        add(button("Google Privacy Policy ↗", () -> open(Uri.parse("https://policies.google.com/privacy"), "text/html")));
    }

    void selectYouTube(String value) {
        if (exiting()) return;
        String id = YouTubePlayer.videoId(value);
        if (id == null) { toast(tr("올바른 HTTPS YouTube 주소 또는 11자리 동영상 ID를 입력하세요.", "Enter a valid HTTPS YouTube URL or 11-character video ID.")); return; }
        if (settingsLocked() || recordPermissionPending || pendingAction != 0 || AudioService.captureActive || composer.capturing || testActive || starting || busy || awaitingMicPermission || (camera != null && camera.recording)) {
            toast(tr("녹음·예약·다른 앱 소리 캡처를 마친 뒤 YouTube를 선택하세요.", "Finish recording, countdown, or other-app audio capture before selecting YouTube.")); return;
        }
        Runnable select = () -> {
            if (settingsLocked() || recordPermissionPending || pendingAction != 0 || starting || busy || awaitingMicPermission || AudioService.captureActive || (camera != null && camera.recording)) return;
            // Accepted links leave editing mode so the preview gets the full window height.
            View focused = getCurrentFocus();
            android.view.inputmethod.InputMethodManager inputMethod = getSystemService(android.view.inputmethod.InputMethodManager.class);
            if (inputMethod != null) inputMethod.hideSoftInputFromWindow(
                    focused != null ? focused.getWindowToken() : getWindow().getDecorView().getWindowToken(), 0);
            if (focused instanceof EditText) focused.clearFocus();
            // Stop native owners before exposing a third-party player.
            stopPlayer(); composer.stop(); composer.voice(false); harmony.stop(); harmony.live(false);
            NativeAudio.playLocal(false); NativeAudio.set(13, 0); NativeAudio.set(12, 0);
            captureOtherApps = pendingCapture = false;
            NativeAudio.enablePlaybackCapture(false, 0);
            youtube.pause(); youtube.id = id; youtube.selected = true; AudioService.youtubeSelected = true;
            if (youtube.web != null) youtube.load();
            // Refresh the current preview without changing its destination or Back history.
            BackNavigation.Page preview = currentPage();
            navigate(new BackNavigation.Page(preview.tab, 2, preview.filter, preview.scroll), false);
        };
        if (getPreferences(0).getBoolean("youtubeConsent", false)) { select.run(); return; }
        StudioDialogs.alert(this).setTitle(tr("YouTube 플레이어 사용", "Use the YouTube player"))
                .setMessage(tr("YouTube 내장 플레이어를 불러오면 Google/YouTube에 IP·기기 정보가 전송되며 쿠키/저장소를 사용할 수 있습니다. 사용 시 YouTube 서비스 약관(https://www.youtube.com/t/terms)에 동의합니다. Google 개인정보처리방침: https://policies.google.com/privacy. 녹음은 마이크만 저장합니다. 이어폰을 사용하면 스피커 소리 유입을 줄일 수 있습니다.", "Loading the embedded player connects to Google/YouTube, shares your IP and device information, and may use cookies/storage. By using it you agree to YouTube Terms of Service (https://www.youtube.com/t/terms). Google Privacy Policy: https://policies.google.com/privacy. Recordings save only the microphone. Use headphones to reduce speaker audio entering the microphone."))
                .setPositiveButton(tr("동의하고 불러오기", "Agree and load"), (d, w) -> {
                    getPreferences(0).edit().putBoolean("youtubeConsent", true).apply(); select.run();
                }).setNegativeButton(tr("취소", "Cancel"), null).show();
    }

    void leaveYouTube() {
        if (youtube == null) return;
        youtube.pause(); youtube.selected = false; AudioService.youtubeSelected = false;
    }


    void midi() {
        add(title(tr("실시간 MIDI", "Live MIDI")));
        LinearLayout prev = beginCard();
        TextView caption = text(tr("현재 음정", "Current pitch"), 14, MUTED);
        caption.setGravity(Gravity.CENTER);
        add(caption);
        pitch = text("—", 72, LIME);
        pitch.setTypeface(Typeface.create(StudioTheme.mono(this), 700, false));
        pitch.setGravity(Gravity.CENTER);
        add(pitch);
        pitchDetails = text(tr("음정 감지 대기", "Waiting for pitch"), 16, MUTED);
        pitchDetails.setTypeface(StudioTheme.mono(this));
        pitchDetails.setGravity(Gravity.CENTER);
        add(pitchDetails);
        pitchMeter = new PitchMeter(this);
        content.addView(pitchMeter, new LinearLayout.LayoutParams(-1, dp(28)));
        TextView scale = text("−50 ───────── 0 ───────── +50", 14, MUTED);
        scale.setGravity(Gravity.CENTER);
        add(scale);
        endCard(prev);

        prev = beginCard();
        add(text(tr("최근 12초", "Last 12 seconds"), 12, MUTED));
        visual = new StudioVisual(this, true);
        content.addView(visual, new LinearLayout.LayoutParams(-1, dp(170)));
        endCard(prev);

        toggle(tr("신스 더블링", "Synth doubling"), 12);
        add(button(tr("MIDI 저장", "Save MIDI"), () -> {
            File f = store.file("melody_" + System.currentTimeMillis(), ".mid");
            if (NativeAudio.exportMidi(f.getAbsolutePath(), Math.round(values[15]), false)) export(f);
            else toast(tr("MIDI 저장 실패", "Could not save MIDI"));
        }));
        Button clear = button(tr("채보 지우기", "Clear transcription"), () -> {
            if (RecordingManager.active || AudioService.waiting()) {
                toast(tr("예약 또는 녹음 중에는 채보를 지울 수 없습니다.", "Transcription cannot be cleared during recording or a countdown."));
                return;
            }
            StudioDialogs.confirmDanger(
                    this,
                    tr("채보를 지울까요?", "Clear the transcription?"),
                    tr("현재 음정 기록을 지웁니다. 저장한 MIDI 파일은 유지됩니다.", "Clear current pitch history. Saved MIDI files are kept."),
                    tr("지우기", "Clear"),
                    NativeAudio::clearNotes
            );
        });
        add(clear);
        note(tr("단선율 보컬 감지 · 잡음에 따라 오차가 생길 수 있어요.", "Monophonic vocal detection · noise can affect accuracy."));
    }

    void cameraPage() {
        cameraFailed = false;
        addModeSelector(true);
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            LinearLayout prev = beginCard();
            add(text(tr("카메라로 담는 나의 무대", "Capture your performance"), 20, TEXT));
            note(tr("보컬 효과와 반주를 영상에도 그대로", "Include vocal effects and backing in video"));
            actionDock.setVisibility(View.GONE);
            add(button(tr("카메라 권한 허용", "Allow camera permission"), () -> requestPermissions(new String[]{Manifest.permission.CAMERA}, 11)));
            endCard(prev);
            return;
        }
        FrameLayout stage = new FrameLayout(this);
        stage.setBackground(bg(PANEL, 20));
        stage.setClipToOutline(true);
        content.addView(stage, new LinearLayout.LayoutParams(-1, dp(220)));
        page.flexible(stage, 180, 520);

        texture = new TextureView(this);
        stage.addView(texture, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout hud = row();
        hud.setPadding(dp(12), dp(12), dp(12), 0);
        hud.setBackgroundColor(0x55000000);
        cameraTimer = text(tr("영상 촬영", "Video recording"), 16, TEXT);
        cameraTimer.setTypeface(StudioTheme.mono(this));
        hud.addView(cameraTimer, new LinearLayout.LayoutParams(0, -2, 1));
        flipButton = button(tr("전·후면 ↻", "Switch camera ↻"), this::flipCamera);
        hud.addView(flipButton, new LinearLayout.LayoutParams(dp(112), dp(48)));
        stage.addView(hud, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));

        gap();
        LinearLayout controls = row();
        pauseButton = button(tr("Ⅱ 일시정지", "Ⅱ Pause"), this::pauseVideo);
        weighted(controls, pauseButton, 56);
        videoButton = button(tr("● 영상 촬영", "● Record video"), this::toggleVideo);
        videoButton.setTextColor(BG);
        videoButton.setBackgroundTintList(ColorStateList.valueOf(RED));
        weighted(controls, videoButton, 56);
        actionDock.addView(controls, new LinearLayout.LayoutParams(-1, -2));
        note(tr("영상 촬영을 시작하면 효과 오디오도 함께 녹음돼요.", "Video recordings include the processed audio."));

        camera = new CameraController(this, texture, new CameraController.Listener() {
            @Override
            public void ready() {
                if (exiting()) return;
                if (flipResumeAudio) {
                    RecordingManager.pause(false);
                    videoPaused = false;
                    flipResumeAudio = false;
                }
                if (pendingAction == 2 && AudioService.active) tryPendingAction();
            }

            @Override
            public void error(String t) {
                cameraFailed = true;
                videoPreparing = false;
                pendingAction = 0;
                flipResumeAudio = false;
                if (camera != null && camera.recording) stopVideo();
                toast(t);
                if (cameraTimer != null) cameraTimer.setText(tr("카메라 오류 · 재시도 필요", "Camera error · retry"));
            }
        });
    }

    void flipCamera() {
        if (camera == null || videoPreparing || busy) return;
        boolean resume = camera.recording && !videoPaused;
        if (resume) RecordingManager.pause(true);
        if (camera.flip()) {
            flipResumeAudio = resume;
        } else {
            if (resume) RecordingManager.pause(false);
            toast(tr("카메라가 준비된 뒤 다시 시도해 주세요.", "Try again when the camera is ready."));
        }
    }

    void pauseVideo() {
        if (camera == null || !camera.recording || camera.switching) return;
        boolean next = !videoPaused;
        if (next) {
            RecordingManager.pause(true);
            if (!camera.pause(true)) {
                RecordingManager.pause(false);
                return;
            }
        } else {
            if (!camera.pause(false)) return;
            RecordingManager.pause(false);
        }
        videoPaused = next;
    }

    void library() {
        LinearLayout filters = row();
        StudioButton recFilter = StudioTheme.styled(this, tr("녹음", "Recording"), StudioButton.CHIP, () -> {
            chooseLibraryFilter(0);
        });
        StudioTheme.selected(recFilter, libraryFilter == 0);
        StudioButton backFilter = StudioTheme.styled(this, tr("내 반주", "My backing"), StudioButton.CHIP, () -> {
            chooseLibraryFilter(1);
        });
        StudioTheme.selected(backFilter, libraryFilter == 1);
        weighted(filters, recFilter, 48);
        weighted(filters, backFilter, 48);
        add(filters);

        if (libraryFilter == 1) {
            composerScreen.projects();
            return;
        }
        add(title(tr("보관함", "Library")));
        note(tr("녹음과 연습을 한곳에", "Recordings and practice in one place"));
        if (busy) note(tr("영상에 보컬 오디오를 합치고 있습니다…", "Merging vocal audio into video…"));

        List<JSONObject> takes = store.list();
        if (takes.isEmpty()) {
            LinearLayout prev = beginCard();
            add(text(tr("첫 번째 목소리를 담아보세요", "Make your first recording"), 19, TEXT));
            note(tr("녹음한 오디오·영상·MIDI가 한 테이크로 모입니다.", "Audio, video and MIDI are saved together in a take."));
            StudioButton first = StudioTheme.styled(this, tr("첫 녹음 시작", "Start your first recording"), StudioButton.OUTLINE, () -> chooseTab(0));
            first.setTextColor(LIME);
            add(first);
            endCard(prev);
            return;
        }

        for (JSONObject j : takes) {
            String id = j.optString("id");
            File audio = store.file(id, ".wav"), video = store.file(id, ".mp4");
            LinearLayout card = column();
            card.setPadding(dp(14), dp(13), dp(12), dp(12));
            card.setBackground(bg(PANEL, 12));

            LinearLayout heading = row();
            LinearLayout copy = column();
            TextView name = text(j.optString("name"), 17, TEXT);
            name.setTypeface(StudioTheme.bold(this));
            name.setSingleLine();
            name.setEllipsize(TextUtils.TruncateAt.END);
            copy.addView(name);
            TextView meta = text(
                    new SimpleDateFormat("yyyy.MM.dd", Locale.KOREA).format(new Date(j.optLong("created")))
                            + " · " + formatTime(j.optDouble("seconds")),
                    11,
                    MUTED
            );
            meta.setPadding(0, dp(5), 0, 0);
            copy.addView(meta);
            heading.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));

            for (String type : new String[]{"MP4", "WAV", "MID"}) {
                if (!store.file(id, "." + type.toLowerCase(Locale.ROOT)).exists()) continue;
                TextView badge = text(type, 10, StudioTheme.OLIVE);
                badge.setPadding(dp(5), dp(4), dp(5), dp(4));
                badge.setBackground(bg(RAISED, 5));
                LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-2, -2);
                bp.leftMargin = dp(4);
                heading.addView(badge, bp);
            }
            heading.addView(StudioTheme.icon(this, "more", tr("테이크 메뉴", "Take menu"), () -> takeMenu(j)), new LinearLayout.LayoutParams(dp(48), dp(48)));
            card.addView(heading);

            LinearLayout stage = row();
            LinearLayout.LayoutParams stageParams = new LinearLayout.LayoutParams(-1, dp(78));
            stageParams.topMargin = dp(10);
            if (video.exists()) {
                ImageView thumbnail = new ImageView(this);
                thumbnail.setScaleType(ImageView.ScaleType.CENTER_CROP);
                thumbnail.setBackground(bg(BG, 9));
                thumbnail.setClipToOutline(true);
                stage.addView(thumbnail, new LinearLayout.LayoutParams(0, -1, 1));
                io.execute(() -> {
                    MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                    try {
                        retriever.setDataSource(video.getAbsolutePath());
                        android.graphics.Bitmap frame = retriever.getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 320, 160);
                        runOnUiThread(() -> {
                            if (!isDestroyed()) thumbnail.setImageBitmap(frame);
                        });
                    } catch (Exception ignored) {
                    } finally {
                        try {
                            retriever.release();
                        } catch (Exception ignored) {
                        }
                    }
                });
            } else if (j.has("practice") && store.file(id, ".mid").exists()) {
                StudioMidiPreview midi = new StudioMidiPreview(this);
                stage.addView(midi, new LinearLayout.LayoutParams(0, -1, 1));
                io.execute(() -> {
                    float[] notes = StudioMidiPreview.read(store.file(id, ".mid"));
                    runOnUiThread(() -> {
                        if (!isDestroyed()) {
                            midi.notes = notes;
                            midi.invalidate();
                        }
                    });
                });
            } else {
                StudioTakeWave waveform = new StudioTakeWave(this);
                stage.addView(waveform, new LinearLayout.LayoutParams(0, -1, 1));
                io.execute(() -> {
                    float[] levels = readWaveform(audio);
                    runOnUiThread(() -> {
                        if (!isDestroyed()) {
                            waveform.levels = levels;
                            waveform.invalidate();
                        }
                    });
                });
            }
            StudioButton play = (StudioButton) StudioTheme.icon(this, "play", tr("테이크 재생", "Play take"), () -> {
                if (video.exists()) openFile(video);
                else play(audio);
            });
            if (!video.exists()) takePlayButtons.put(audio.getAbsolutePath(), play);
            LinearLayout.LayoutParams playParams = new LinearLayout.LayoutParams(dp(48), dp(48));
            playParams.leftMargin = dp(6);
            stage.addView(play, playParams);
            card.addView(stage, stageParams);

            if (!video.exists()) {
                TextView playback = text("", 12, LIME);
                playback.setPadding(0, dp(8), 0, 0);
                playback.setVisibility(View.GONE);
                takePlaybackStatus.put(audio.getAbsolutePath(), playback);
                card.addView(playback);
            }
            if (j.has("practice")) {
                JSONObject pr = j.optJSONObject("practice");
                TextView score = text(
                        pr != null && pr.has("accuracy")
                                ? String.format(Locale.US, tr("정확도 %.1f%% · 연습 결과 ›", "Accuracy %.1f%% · practice results ›"), pr.optDouble("accuracy"))
                                : tr("보컬 연습 결과 ›", "Vocal practice results ›"),
                        12,
                        LIME
                );
                score.setMinHeight(dp(48));
                score.setGravity(Gravity.CENTER_VERTICAL);
                score.setOnClickListener(v -> showPracticeResult(pr));
                card.addView(score);
            }
            if (!j.optString("warning").isEmpty()) {
                card.addView(text(j.optString("warning"), 11, RED));
            }
            add(card);
        }
        updateTakePlaybackButtons();
    }

    static float[] readWaveform(File file) {
        float[] levels = new float[64];
        try (RandomAccessFile wav = new RandomAccessFile(file, "r")) {
            long data = Math.max(0, wav.length() - 44);
            for (int i = 0; i < 64; i++) {
                long offset = (data * i / 64) / 4 * 4;
                wav.seek(44 + offset);
                double energy = 0;
                int samples = 0;
                for (int n = 0; n < 64 && wav.getFilePointer() + 2 <= wav.length(); n++) {
                    int lo = wav.readUnsignedByte(), hi = wav.readByte();
                    float v = (short) ((hi << 8) | lo) / 32768f;
                    energy += v * v;
                    samples++;
                }
                levels[i] = samples > 0 ? (float) Math.min(1, Math.sqrt(energy / samples) * 3) : 0;
            }
        } catch (Exception ignored) {
        }
        return levels;
    }

    void takeMenu(JSONObject j) {
        String id = j.optString("id");
        File wav = store.file(id, ".wav"), midi = store.file(id, ".mid"), video = store.file(id, ".mp4");
        List<String> names = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        if (wav.exists()) {
            names.add(tr("오디오 재생 / 일시정지", "Play / pause audio"));
            actions.add(() -> play(wav));
            names.add(tr("WAV 저장", "Save WAV"));
            actions.add(() -> export(wav));
        }
        if (midi.exists()) {
            names.add(tr("MIDI 저장", "Save MIDI"));
            actions.add(() -> export(midi));
        }
        if (video.exists()) {
            names.add(tr("영상 재생", "Play video"));
            actions.add(() -> openFile(video));
            names.add(tr("MP4 저장", "Save MP4"));
            actions.add(() -> export(video));
        }
        if (video.exists() || wav.exists()) {
            names.add(tr("공유", "Share"));
            actions.add(() -> share(video.exists() ? video : wav));
        }
        names.add(tr("이름 변경", "Rename"));
        actions.add(() -> {
            EditText input = new EditText(this);
            input.setText(j.optString("name"));
            StudioDialogs.alert(this)
                    .setTitle(tr("테이크 이름", "Take name"))
                    .setView(input)
                    .setPositiveButton(tr("저장", "Save"), (a, b) -> {
                        try {
                            String name = input.getText().toString().trim();
                            if (name.isEmpty()) {
                                toast(tr("이름을 입력해 주세요.", "Enter a name."));
                                return;
                            }
                            store.rename(j, name);
                            showTab();
                        } catch (Exception e) {
                            toast(e.getMessage());
                        }
                    })
                    .setNegativeButton(tr("취소", "Cancel"), null)
                    .show();
        });
        names.add(tr("테이크 삭제", "Delete take"));
        actions.add(() -> StudioDialogs.confirmDanger(
                this,
                tr("이 테이크를 삭제할까요?", "Delete this take?"),
                tr("오디오·MIDI·영상이 기기에서 삭제됩니다.", "Audio, MIDI and video will be deleted from this device."),
                tr("삭제", "Delete"),
                () -> {
                    stopPlayer();
                    store.delete(id);
                    showTab();
                }
        ));
        StudioDialogs.choose(this, j.optString("name"), names.toArray(new String[0]), -1, w -> actions.get(w).run());
    }

    void requestMicToggle() {
        if (AudioService.active && (RecordingManager.active || AudioService.waiting())) {
            boolean waiting = AudioService.waiting();
            StudioDialogs.alert(this)
                    .setTitle(tr("마이크를 끌까요?", "Turn off the microphone?"))
                    .setMessage(waiting
                            ? tr("예약을 취소하고 마이크를 끕니다.", "Cancel the countdown and turn off the microphone.")
                            : tr("진행 중인 녹음을 저장하고 마이크를 끕니다.", "Save the recording and turn off the microphone."))
                    .setPositiveButton(
                            waiting ? tr("예약 취소 후 끄기", "Cancel countdown and turn off") : tr("저장 후 끄기", "Save and turn off"),
                            (d, w) -> toggleMic()
                    )
                    .setNegativeButton(tr("계속 사용", "Keep using"), null)
                    .show();
            return;
        }
        toggleMic();
    }

    void toggleMic() {
        if (exiting()) return;
        if (starting || busy || testActive) return;
        pendingAction = 0;
        if (AudioService.active) {
            if (camera != null && camera.recording) stopVideo();
            else finishAudio();
            startService(new Intent(this, AudioService.class).putExtra("exitGeneration", ExitCoordinator.generation()).setAction(AudioService.STOP));
            set(11, 0);
            set(13, 0);
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            awaitingMicPermission = true;
            requestPermissions(
                    Build.VERSION.SDK_INT >= 33
                            ? new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS}
                            : new String[]{Manifest.permission.RECORD_AUDIO},
                    10
            );
            return;
        }
        starting = true;
        pendingMonitor = AudioService.canMonitor(this);
        startForegroundService(new Intent(this, AudioService.class).putExtra("exitGeneration", ExitCoordinator.generation()).putExtra("device", inputDevice).putExtra("monitor", pendingMonitor));
        handler.postDelayed(() -> starting = false, 1500);
    }

    boolean requireMic() {
        if (!AudioService.active || NativeAudio.stats()[10] < .5) {
            toast(tr("스튜디오에서 마이크를 켜주세요.", "Turn on the microphone in the studio."));
            return false;
        }
        return true;
    }

    void ensureMic(int action) {
        if (exiting()) return;
        if (busy || videoPreparing) return;
        if (action == 4) {
            if (!YouTubePlayer.beforeNativePlayback()) return;
            composer.pause();
            AudioService.composerRequested = false;
            NativeAudio.playLocal(true);
            startForegroundService(new Intent(this, AudioService.class).putExtra("exitGeneration", ExitCoordinator.generation()).setAction(AudioService.PLAYBACK).putExtra("playLocal", true));
            set(13, 0);
            showTab();
            return;
        }
        if (AudioService.active && NativeAudio.stats()[10] > .5) {
            if (action == 5) practiceOwnsMic = false;
            pendingAction = action;
            actionDeadline = SystemClock.elapsedRealtime() + 10000;
            tryPendingAction();
            return;
        }
        if (action == 5) practiceOwnsMic = true;
        if (!starting) toggleMic();
        pendingAction = action;
        actionDeadline = SystemClock.elapsedRealtime() + 10000;
    }

    void tryPendingAction() {
        if (exiting()) return;
        if (pendingAction == 0 || !AudioService.active || NativeAudio.stats()[10] < .5 || busy || videoPreparing) return;
        int action = pendingAction;
        if (action == 2 && (camera == null || !camera.ready)) return;
        pendingAction = 0;
        if (action == 1) {
            stopPlayer();
            requestRecording();
        } else if (action == 2) {
            startVideo();
        } else if (action == 3) {
            NativeAudio.playLocal(false);
            set(14, BackingCatalog.SYNTH_STYLES[genre]);
            set(13, 1);
            showTab();
        } else if (action == 5) {
            composer.pause();
            set(13, 0);
            set(17, 0);
            set(12, 0);
            AudioService.practice.ownedMic = practiceOwnsMic;
            practiceOwnsMic = false;
            AudioService.practice.start(practiceRoot, practiceBpm, practiceMode, practicePattern, practiceRecord);
            showTab();
        } else if (action == 7) {
            if (!AudioService.canMonitor(this)) {
                NativeAudio.monitorLevel(.22f);
            }
            harmony.live(false);
            composer.voice(true);
            showTab();
        } else if (action == 6) {
            runSyncTest();
        } else if (action == 8 || action == 9) {
            if (!AudioService.canMonitor(this)) {
                NativeAudio.monitorLevel(.22f);
            }
            if (NativeAudio.stats()[17] > .5f) {
                toast(tr("하울링 보호를 출력 설정에서 해제한 뒤 다시 켜세요.", "Reset feedback protection in output settings before restarting."));
                monitoringOptions();
                return;
            }
            set(11, 1);
            harmony.live(true);
            if (action == 9) harmony.arm(harmonyScreen.selected);
            if (tab == 9) harmonyScreen.refresh();
            else showTab();
        } else if (action == 4) {
            leaveYouTube();
            if (selectedFile.isEmpty()) {
                toast(tr("반주 파일을 먼저 선택하세요.", "Choose a backing file first."));
                return;
            }
            set(13, 0);
            NativeAudio.playLocal(true);
            showTab();
        }
    }

    void toggleRecord() {
        if (exiting()) return;
        if (testActive || videoPreparing || busy || starting || recordPermissionPending) return;
        pendingAction = 0;
        if (camera != null && camera.recording) {
            stopVideo();
            return;
        }
        if (AudioService.waiting()) {
            startService(new Intent(this, AudioService.class).putExtra("exitGeneration", ExitCoordinator.generation()).setAction(AudioService.CANCEL));
            return;
        }
        if (RecordingManager.active) {
            finishAudio();
            return;
        }
        stopPlayer();
        requestRecording();
    }

    void finishAudio() {
        if (AudioService.active) {
            startService(new Intent(this, AudioService.class).putExtra("exitGeneration", ExitCoordinator.generation()).setAction(AudioService.FINISH));
            return;
        }
        try {
            RecordingManager.Result r = RecordingManager.stop();
            if (r != null) toast(tr("오디오와 MIDI를 저장했습니다.", "Audio and MIDI saved."));
        } catch (Exception e) {
            toast(tr("보관함 저장 실패: ", "Could not save to library: ") + e.getMessage());
        }
    }

    void toggleVideo() {
        if (exiting()) return;
        if (videoPreparing || busy) return;
        if (cameraFailed) {
            showTab();
            return;
        }
        if (camera != null && camera.recording) {
            stopVideo();
            return;
        }
        if (RecordingManager.active || AudioService.waiting()) {
            toast(tr("예약 또는 오디오 녹음을 마친 뒤 영상을 시작하세요.", "Finish the scheduled or audio recording before starting video."));
            return;
        }
        ensureMic(2);
    }

    void startVideo() {
        if (exiting()) return;
        youtube.pause();
        if (camera == null || !requireMic()) return;
        stopPlayer();
        rawVideo = new File(store.directory, "pending_" + System.currentTimeMillis() + ".video.mp4");
        videoPreparing = true;
        videoPaused = false;
        final CameraController target = camera;
        if (!camera.start(rawVideo, () -> {
            videoPreparing = false;
            if (exiting() || camera != target || !target.recording) { target.stop(); return; }
            RecordingManager.syncMs = AudioRouting.prefs(this).getInt("sync" + AudioRouting.selected(this), 0);
            if (!RecordingManager.start(this, Math.round(values[15]))) {
                target.stop();
                toast(tr("오디오를 시작하지 못해 영상 녹화를 중지했습니다.", "Video recording stopped because audio could not start."));
            } else {
                videoTakeId = RecordingManager.id;
            }
        })) {
            videoPreparing = false;
            toast(tr("카메라 미리보기가 준비된 뒤 다시 눌러주세요.", "Try again once the camera preview is ready."));
        }
    }

    void stopVideo() {
        if (camera == null || !camera.recording) return;
        pendingAction = 0;
        videoPaused = false;
        flipResumeAudio = false;
        boolean videoOk = camera.stop();
        try {
            RecordingManager.Result r = RecordingManager.stop();
            if (r == null && RecordingManager.lastResult != null && RecordingManager.lastResult.id.equals(videoTakeId)) {
                r = RecordingManager.lastResult;
            }
            if (r == null) {
                if (exiting()) exitSaveError += "Video audio could not be finalized; original video retained.\n";
                return;
            }
            final RecordingManager.Result saved = r;
            final long voiceDelayUs = Math.max(0, RecordingManager.syncMs) * 1000L;
            File source = rawVideo;
            if (!videoOk) {
                if (exiting()) exitSaveError += "Video was interrupted; saved audio and original video retained.\n";
                toast(tr("영상이 너무 짧거나 중단되었습니다. 오디오는 보관함에서 확인하세요.", "The video was too short or interrupted. Find the audio in the library."));
                return;
            }
            busy = true;
            long offset = (long) (r.clock * 1e6 - camera.videoStartNanos / 1000.);
            toast(tr("영상을 저장하고 있습니다…", "Saving video…"));
            io.execute(() -> {
                try {
                    MediaTools.makeVideo(source, store.file(saved.id, ".wav"), store.file(saved.id, ".mp4"), offset, voiceDelayUs);
                    JSONObject meta = store.read(saved.id);
                    meta.put("id", saved.id)
                            .put("name", "Video Take " + saved.id)
                            .put("seconds", saved.seconds)
                            .put("video", true)
                            .put("warning", saved.warning);
                    store.update(saved.id, meta);
                    runOnUiThread(() -> toast(tr("MP4 · WAV · MIDI를 저장했습니다.", "MP4 · WAV · MIDI saved.")));
                } catch (Exception e) {
                    store.file(saved.id, ".mp4").delete();
                    if (exiting()) exitSaveError += "Video merge failed: " + e.getMessage() + "\n";
                    runOnUiThread(() -> toast(tr("영상 결합 실패. WAV와 MIDI는 보관되어 있습니다: ", "Video merge failed. WAV and MIDI remain in the library: ") + e.getMessage()));
                } finally {
                    runOnUiThread(() -> {
                        busy = false;
                        if (!exiting() && tab == 4) showTab();
                    });
                }
            });
        } catch (Exception e) {
            if (exiting()) exitSaveError += "Could not save video: " + e.getMessage() + "\n";
            toast(tr("녹화 저장 실패: ", "Could not save video: ") + e.getMessage());
        } finally {
            if (!exiting() && tab == 3 && !isFinishing()) showTab();
        }
    }

    void selectInput() {
        chooseDevice(true);
    }

    void updateTransportVisibility() {
        boolean practiceRecording = tab == 5 && AudioService.practice != null && AudioService.practice.active;
        transport.setVisibility(((RecordingManager.active || AudioService.waiting()) && tab != 0 && tab != 3 && !practiceRecording) ? View.VISIBLE : View.GONE);
    }

    String formatTime(double seconds) {
        int s = (int) Math.max(0, seconds);
        return String.format(Locale.US, "%02d:%02d", s / 60, s % 60);
    }

    long uiTickDelay(boolean running) {
        return running || (camera != null) || videoPreparing || testActive || pendingAction != 0
                || starting || (player != null && player.isPlaying()) || AudioService.playbackActive
                ? 80L : 1000L;
    }

    void updateScreenAwake(boolean running) {
        boolean awake = activityVisible && !exiting()
                && (camera != null || videoPreparing || (running && (tab == 0 || tab == 5 || tab == 9)));
        if (awake) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (exiting() || !activityVisible) return;
            float[] s = NativeAudio.stats();
            boolean running = AudioService.active && s[10] > .5;
            values[11] = s[21];
            boolean wired = AudioService.hasWiredOutput(MainActivity.this);
            if (headphones && !wired && AudioRouting.selected(MainActivity.this) == 0) {
                set(11, 0);
            }
            headphones = wired;
            if (running && !wasRunning) {
                starting = false;
                values[11] = s[21];
                pendingMonitor = false;
            }
            if (!running && wasRunning) {
                if (camera != null && camera.recording) stopVideo();
                finishAudio();
                set(11, 0);
                set(13, 0);
                if (AudioService.active) stopService(new Intent(MainActivity.this, AudioService.class).putExtra("exitGeneration", ExitCoordinator.generation()));
            }
            wasRunning = running;

            if (micButton != null) {
                micButton.setText(liveScreen != null
                        ? (starting ? tr("연결 중…", "Connecting…") : running ? tr("마이크 켜짐", "Mic on") : tr("마이크 꺼짐", "Mic off"))
                        : running ? tr("마이크 끄기", "Turn mic off") : starting ? tr("연결 중…", "Connecting…") : tr("마이크 켜기", "Turn mic on"));
                if (liveScreen == null) StudioTheme.selected(micButton, running);
            }
            if (monitorButton != null) {
                monitorButton.setText(
                        s[17] > .5
                                ? tr("내 목소리 듣기 · 보호 음소거 ›", "Voice monitoring · safety mute ›")
                                : values[11] > .5
                                ? tr("내 목소리 듣기 · 켜짐", "Voice monitoring · on")
                                : tr("내 목소리 듣기 · 꺼짐", "Voice monitoring · off")
                );
                StudioTheme.selected(monitorButton, values[11] > .5 && s[17] < .5);
                monitorButton.setEnabled(running);
            }
            if (outputButton != null) {
                String route = outputName();
                if (liveScreen == null) outputButton.setText(route + " ▾");
                outputButton.setContentDescription(tr("출력: ", "Output: ") + route);
            }
            if (playerTime != null) {
                double[] position = NativeAudio.localPlaybackInfo();
                playerTime.setText(formatTime(position[0]) + " / " + formatTime(position[1]));
                if (playerLine != null) {
                    boolean canSeek = !RecordingManager.active && !AudioService.waiting();
                    if (playerLine.isEnabled() != canSeek) {
                        playerLine.setContentDescription(canSeek
                                ? tr("반주 재생 위치", "Backing playback position")
                                : tr("녹음 중에는 반주 위치를 바꿀 수 없습니다", "Cannot seek backing while recording"));
                    }
                    playerLine.setEnabled(canSeek);
                    playerLine.setAlpha(canSeek ? 1f : .45f);
                    if (!playerLine.dragging) {
                        playerLine.progress = position[1] > 0 ? (float) (position[0] / position[1]) : 0;
                    }
                    playerLine.invalidate();
                }
            }
            if (player != null && playingFile != null) {
                TextView playback = takePlaybackStatus.get(playingFile.getAbsolutePath());
                if (playback != null) {
                    try {
                        playback.setText(
                                (player.isPlaying() ? tr("재생 중 · ", "Playing · ") : tr("일시정지 · ", "Paused · "))
                                        + formatTime(player.getCurrentPosition() / 1000.0)
                                        + " / " + formatTime(player.getDuration() / 1000.0)
                        );
                    } catch (IllegalStateException ignored) {
                    }
                }
            }
            if (levelText != null) {
                float db = 20 * (float) Math.log10(Math.max(.00001f, s[3]));
                levelText.setText(s[3] > .00001 ? String.format(Locale.US, "%.0f dB", db) : "— dB");
                levelText.setTextColor(s[3] > .8 ? RED : LIME);
            }
            if (tab == 5 && trainingReadout != null && AudioService.practice != null) {
                PracticeSession pr = AudioService.practice;
                if (practiceButton != null) {
                    practiceButton.setText(pr.active ? tr("연습 종료 · 결과 저장", "End practice · save results") : tr("연습 시작", "Start practice"));
                    practiceButton.setBackgroundTintList(ColorStateList.valueOf(pr.active ? RED : LIME));
                }
                if (pr.active) {
                    if (practicePlot != null) {
                        practicePlot.sample(s[15], pr.target, s[0], !Double.isNaN(pr.score.cents));
                    }
                    trainingReadout.setText(
                            pr.phase + tr("\n목표 ", "\nTarget ") + noteName(pr.target) + " · " + (s[0] > 0 ? noteName((int) s[2]) : "—")
                                    + "\n" + (Double.isNaN(pr.score.cents)
                                    ? tr("입력 대기", "Waiting for input")
                                    : String.format(Locale.US, "%+.0f¢", pr.score.cents)
                                            + (Double.isNaN(pr.score.accuracy()) ? "" : " · " + String.format(Locale.US, "%.1f%%", pr.score.accuracy())))
                    );
                } else if (pr.index >= 0) {
                    trainingReadout.setText(
                            pr.phase + "\n" + (Double.isNaN(pr.score.accuracy())
                                    ? tr("발성 부족", "Not enough vocal input")
                                    : String.format(Locale.US, tr("정확도 %.1f%% · 발성 %.1f%%", "Accuracy %.1f%% · voiced %.1f%%"), pr.score.accuracy(), pr.score.coverage()))
                    );
                }
            }
            if (!AudioService.active && !AudioService.playbackActive) harmony.poll();
            harmonyScreen.poll();
            if (lastHarmonyRevision != harmony.revision) {
                lastHarmonyRevision = harmony.revision;
                if (tab == 9) harmonyScreen.refresh();
            }
            if (!harmony.error.isEmpty()) {
                toast(harmony.error);
                harmony.error = "";
            }
            if (!harmony.message.isEmpty()) {
                toast(harmony.message);
                harmony.message = "";
            }
            if (composer.ready) {
                if (!AudioService.active && !AudioService.playbackActive) composer.poll();
                composerScreen.poll();
            }
            if (lastComposerRevision != composer.revision) {
                lastComposerRevision = composer.revision;
                if (tab == 7 || tab == 8 || (tab == 1 && sourceTab == 0) || (tab == 4 && libraryFilter == 1)) {
                    composerScreen.refresh();
                }
            }
            if (!composer.error.isEmpty()) {
                toast(composer.error);
                composer.error = "";
            }
            if (!composer.message.isEmpty()) {
                toast(composer.message);
                composer.message = "";
            }
            updateTransportVisibility();
            if (pendingAction != 0) {
                if (!awaitingMicPermission && SystemClock.elapsedRealtime() > actionDeadline) {
                    pendingAction = 0;
                    toast(tr("장치를 시작하지 못했습니다. 권한과 연결을 확인해 주세요.", "Could not start the device. Check permissions and connections."));
                } else {
                    tryPendingAction();
                }
            }
            if (liveState != null) {
                liveState.setText(running ? tr("● 마이크 켜짐", "● Microphone on") : tr("● 마이크 대기", "● Microphone ready"));
                liveState.setTextColor(running ? LIME : MUTED);
            }
            if (status != null) {
                status.setText(running ? tr("실시간 모니터링 · ", "Live monitoring · ") + outputName() : tr("마이크를 켜고 목소리를 들려주세요", "Turn on the microphone and sing"));
            }
            if (!AudioService.error.isEmpty()) {
                toast(AudioService.error);
                AudioService.error = "";
            }
            if (tab == 0 && liveScreen != null) {
                liveScreen.poll(s, running);
            }
            boolean waiting = AudioService.waiting();
            timer.setVisibility(tab != 0 || RecordingManager.active || waiting ? View.VISIBLE : View.GONE);
            String elapsed = formatTime(RecordingManager.active ? s[8] : 0) + String.format(Locale.US, ".%d", (int) ((RecordingManager.active ? s[8] : 0) * 10) % 10);
            timer.setText(waiting ? AudioService.remaining() + tr("초", "seconds") : elapsed);
            if (recordCaption != null) {
                recordCaption.setText(
                        waiting
                                ? tr("예약 녹음", "Scheduled recording")
                                : RecordingManager.active
                                ? tr("● 녹음 중", "● Recording")
                                : captureOtherApps
                                ? tr("이번 녹음 · 다른 앱 소리 포함", "This recording · includes other app audio")
                                : tr("마이크 녹음", "Microphone recording")
                );
                recordCaption.setTextColor(RecordingManager.active || waiting ? RED : MUTED);
            }
            recButton.setText(
                    liveScreen != null
                            ? (busy
                            ? tr("준비 중…", "Preparing…")
                            : waiting
                            ? tr("예약 취소", "Cancel")
                            : RecordingManager.active
                            ? tr("■ 녹음 종료", "■ Stop")
                            : recordDelay == 0
                            ? tr("● 녹음 시작", "● Record")
                            : "● " + recordDelay + tr("초 후 녹음", "s to record"))
                            : (busy
                            ? tr("저장 / 준비 중…", "Saving / preparing…")
                            : waiting
                            ? tr("예약 취소", "Cancel scheduled recording")
                            : RecordingManager.active
                            ? tr("■ 녹음 종료 · 저장", "■ Stop and save")
                            : recordDelay == 0
                            ? tr("● 녹음 시작", "● Start recording")
                            : "● " + recordDelay + tr("초 후 녹음", "s until recording"))
            );
            if (liveScreen == null) {
                recButton.setTextColor(StudioTheme.ACCENT_INK);
                recButton.setBackgroundTintList(ColorStateList.valueOf(RecordingManager.active || waiting ? RED : LIME));
            } else {
                liveScreen.transport(busy, waiting, RecordingManager.active);
            }
            recButton.setEnabled(!busy && !videoPreparing && !recordPermissionPending && !starting);
            recordOptions.setEnabled(!busy && !waiting && !RecordingManager.active && !recordPermissionPending);

            if (status != null) {
                status.setVisibility(waiting || s[17] > .5f || AudioService.captureActive ? View.VISIBLE : View.GONE);
                if (waiting) {
                    status.setText(AudioService.remaining() + tr("초 후 녹음 · ", "s until recording · ")
                            + (AudioService.captureActive
                            ? tr("다른 앱으로 이동해 재생하세요", "Switch to the other app and play audio")
                            : tr("잠시 후 녹음 시작", "Recording will start shortly")));
                } else if (RecordingManager.active && AudioService.captureActive) {
                    status.setText(AudioService.captureLevel > .001f
                            ? tr("● 마이크 + 다른 앱 소리 입력 중", "● Microphone + other app audio input")
                            : tr("● 다른 앱 소리 신호 없음 / 캡처 허용 확인", "● No other app audio · check capture permission"));
                } else if (running && s[19] > 0) {
                    status.setText(tr("출력: ", "Output: ") + AudioService.routeLabel + tr(" · 보호 필터 ", " · safety filters ") + (int) s[18] + tr("개 · 출력 설정 ›", " · output settings ›"));
                }
                if (running && s[17] > .5f) {
                    status.setText(tr("하울링 보호 음소거 · 출력 설정에서 재시작 ›", "Safety mute · restart in output settings ›"));
                }
            }
            if (lastRouteRevision != AudioService.routeRevision) {
                lastRouteRevision = AudioService.routeRevision;
                if (tab == 0) showTab();
            }
            if (lastRecordingRevision != AudioService.revision) {
                lastRecordingRevision = AudioService.revision;
                if (tab == 4) showTab();
            }
            if (camera != null && camera.recording && !RecordingManager.active && !videoPreparing) {
                stopVideo();
            }
            if (cameraTimer != null) {
                boolean active = camera != null && camera.recording;
                cameraTimer.setText(
                        cameraFailed
                                ? tr("카메라 오류 · 재시도", "Camera error · retry")
                                : videoPreparing
                                ? tr("카메라 준비 중…", "Preparing camera…")
                                : active
                                ? (videoPaused ? "Ⅱ PAUSED " : "●REC ") + formatTime(s[8])
                                : busy
                                ? tr("영상 저장 중…", "Saving video…")
                                : tr("영상 촬영", "Video recording")
                );
                cameraTimer.setTextColor(active ? RED : TEXT);
            }
            if (videoButton != null) {
                videoButton.setText(
                        cameraFailed
                                ? tr("카메라 다시 연결", "Reconnect camera")
                                : camera != null && camera.recording
                                ? tr("■ 중단 · 저장", "■ Stop · save")
                                : tr("● 영상 촬영", "● Record video")
                );
                videoButton.setEnabled(!busy && !videoPreparing && camera != null && !camera.switching);
            }
            if (pauseButton != null) {
                pauseButton.setText(videoPaused ? tr("▶ 이어서 촬영", "▶ Resume filming") : tr("Ⅱ 일시정지", "Ⅱ Pause"));
                pauseButton.setEnabled(camera != null && camera.recording && !camera.switching && !videoPreparing);
            }
            if (flipButton != null) {
                flipButton.setEnabled(camera != null && camera.ready && !camera.switching && !videoPreparing && !busy);
            }
            if (pitch != null) {
                int n = (int) s[2];
                String[] notes = {"C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B"};
                pitch.setText(n >= 0 ? notes[n % 12] + (n / 12 - 1) : "—");
                pitchDetails.setText(n >= 0 ? String.format(Locale.US, "%.1f Hz    %+.0f cent", s[0], s[1]) : tr("목소리를 들려주세요", "Sing into the microphone"));
                pitchMeter.tracking = n >= 0;
                pitchMeter.cents = s[1];
                pitchMeter.invalidate();
            }
            if (visual != null) {
                visual.stats = s;
                if (!visual.piano) visual.sampleInput(s[3]);
                if (visual.piano) visual.notes = NativeAudio.roll();
                visual.invalidate();
            }
            updateScreenAwake(running);
            if (activityVisible && !exiting()) handler.postDelayed(this, uiTickDelay(running));
        }
    };

    void play(File f) {
        if (exiting()) return;
        if (RecordingManager.active || AudioService.waiting()) {
            toast(tr("예약 또는 녹음을 마친 뒤 재생하세요.", "Finish recording or the countdown before playback."));
            return;
        }
        if (!YouTubePlayer.beforeNativePlayback()) return;
        if (player != null && f.equals(playingFile)) {
            try {
                if (player.isPlaying()) player.pause();
                else player.start();
                updateTakePlaybackButtons();
            } catch (IllegalStateException e) {
                stopPlayer();
                toast(tr("재생 실패", "Playback failed"));
            }
            return;
        }
        stopPlayer();
        set(11, 0);
        set(13, 0);
        NativeAudio.playLocal(false);
        try {
            player = new MediaPlayer();
            player.setDataSource(f.getAbsolutePath());
            player.setOnCompletionListener(p -> {
                if (player == p) stopPlayer();
            });
            player.prepare();
            playingFile = f;
            player.start();
            updateTakePlaybackButtons();
        } catch (Exception e) {
            stopPlayer();
            toast(tr("재생 실패", "Playback failed"));
        }
    }

    void stopPlayer() {
        if (player != null) {
            player.release();
            player = null;
        }
        playingFile = null;
        updateTakePlaybackButtons();
    }

    void updateTakePlaybackButtons() {
        for (Map.Entry<String, StudioButton> entry : takePlayButtons.entrySet()) {
            boolean current = playingFile != null && entry.getKey().equals(playingFile.getAbsolutePath());
            boolean active = current && player != null && player.isPlaying();
            entry.getValue().glyph(active ? "pause" : "play");
            entry.getValue().setContentDescription(
                    active
                            ? tr("테이크 재생 중 · 일시정지", "Take playing · pause")
                            : current
                            ? tr("일시정지됨 · 이어서 재생", "Paused · resume playback")
                            : tr("테이크 재생", "Play take")
            );
            TextView st = takePlaybackStatus.get(entry.getKey());
            if (st != null) {
                st.setVisibility(current ? View.VISIBLE : View.GONE);
                if (current) st.setText(active ? tr("재생 중", "Playing") : tr("일시정지", "Paused"));
            }
        }
    }

    Uri fileUri(File f) {
        return Uri.parse("content://" + getPackageName() + ".files/" + Uri.encode(f.getName()));
    }

    String mime(File f) {
        return f.getName().endsWith(".mp4") ? "video/mp4" : f.getName().endsWith(".mid") ? "audio/midi" : "audio/wav";
    }

    void openFile(File f) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(fileUri(f), mime(f)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
        } catch (Exception e) {
            toast(tr("이 파일을 재생할 앱이 없습니다. 파일 저장을 사용하세요.", "No app can play this file. Save the file instead."));
        }
    }

    void share(File f) {
        Intent i = new Intent(Intent.ACTION_SEND)
                .setType(mime(f))
                .putExtra(Intent.EXTRA_STREAM, fileUri(f))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(i, tr("테이크 공유", "Share take")));
    }

    void open(Uri u, String type) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, u));
        } catch (Exception e) {
            toast(tr("링크를 열 앱이 없습니다.", "No app can open this link."));
        }
    }

    void export(File f) {
        if (!f.exists()) {
            toast(tr("저장할 파일이 없습니다.", "There is no file to save."));
            return;
        }
        exportFile = f;
        startActivityForResult(
                new Intent(Intent.ACTION_CREATE_DOCUMENT)
                        .setType(mime(f))
                        .putExtra(Intent.EXTRA_TITLE, f.getName())
                        .addCategory(Intent.CATEGORY_OPENABLE),
                21
        );
    }

    @Override
    public void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (exiting()) return;
        if (exchange.onActivityResult(request, result, data)) return;
        if (request == 30) {
            if (youtube.selected || recordingRequestStage != 2) {
                clearRecordingRequest();
                return;
            }
            if (result == RESULT_OK && data != null && pendingCapture) {
                scheduleRecording(data);
            } else {
                clearRecordingRequest();
                toast(tr("다른 앱 소리 권한을 허용하지 않아 녹음을 시작하지 않았습니다.", "Recording did not start because other app audio permission was denied."));
            }
            return;
        }
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (request == 20) {
            try {
                if ((data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0) {
                    getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                }
            } catch (SecurityException ignored) {
            }
            sourceTab = 1;
            loadBacking(uri);
        } else if (request == 21 && exportFile != null) {
            File f = exportFile;
            io.execute(() -> {
                try (InputStream in = new FileInputStream(f);
                        OutputStream out = getContentResolver().openOutputStream(uri)) {
                    if (out == null) {
                        throw new IOException(tr("저장 위치를 열지 못했습니다.", "Could not open the save location."));
                    }
                    byte[] b = new byte[65536];
                    int n;
                    while ((n = in.read(b)) != -1) out.write(b, 0, n);
                    runOnUiThread(() -> toast(tr("파일을 저장했습니다.", "File saved.")));
                } catch (Exception e) {
                    runOnUiThread(() -> toast(tr("파일 저장 실패: ", "Could not save file: ") + e.getMessage()));
                }
            });
        }
    }

    @Override
    public void onRequestPermissionsResult(int request, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(request, permissions, grants);
        if (exiting()) { pendingAction = 0; clearRecordingRequest(); return; }
        if (request == 12) {
            if (recordingRequestStage != 1) {
                clearRecordingRequest();
                return;
            }
            recordPermissionPending = false;
            recordingRequestStage = 0;
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                continueRecording();
            } else {
                clearRecordingRequest();
                toast(tr("마이크 권한이 필요합니다.", "Microphone permission is required."));
            }
            return;
        }
        if (request == 10) awaitingMicPermission = false;
        if (request == 10 && checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            int next = pendingAction;
            toggleMic();
            pendingAction = next;
            actionDeadline = SystemClock.elapsedRealtime() + 10000;
        } else if (request == 11) {
            showTab();
        } else {
            pendingAction = 0;
            toast(tr("설정에서 마이크 권한을 허용해주세요.", "Allow microphone permission in settings."));
        }
    }

    void runSyncTest() {
        if (exiting()) return;
        try {
            File f = new File(getCacheDir(), "sync-test.wav");
            int sync = AudioRouting.prefs(this).getInt("sync" + AudioRouting.selected(this), 0);
            NativeAudio.recordSync(sync);
            set(13, 0);
            NativeAudio.playLocal(false);
            set(15, 60);
            set(17, 1);
            if (!NativeAudio.beginRecord(f.getAbsolutePath())) {
                set(17, 0);
                return;
            }
            testActive = true;
            toast(tr("클릭에 맞춰 소리 내세요. 5초 후 테스트를 재생합니다.", "Sing with the click. The test plays after 5 seconds."));
            handler.postDelayed(() -> {
                if (testActive) finishSyncTest(true);
            }, 5000);
        } catch (Exception e) {
            set(17, 0);
            toast(e.getMessage());
        }
    }

    void finishSyncTest(boolean playback) {
        NativeAudio.endRecord();
        testActive = false;
        set(17, 0);
        set(11, 0);
        if (playback) {
            play(new File(getCacheDir(), "sync-test.wav"));
            toast(tr("테스트를 듣고 보정값을 조절하세요.", "Listen to the test and adjust the offset."));
        }
    }

    void toast(String t) {
        Toast.makeText(this, t == null ? tr("오류가 발생했습니다.", "An error occurred.") : t, Toast.LENGTH_LONG).show();
    }

    @Override
    public boolean onKeyDown(int k, KeyEvent e) {
        if (k == KeyEvent.KEYCODE_ESCAPE) {
            set(11, 0);
            toast(tr("마이크 모니터링 음소거", "Mute microphone monitoring"));
            return true;
        }
        return super.onKeyDown(k, e);
    }

    final Runnable pendingSource = new Runnable() {
        @Override
        public void run() {
            if (exiting() || activityVisible || pendingAction == 0) return;
            if (awaitingMicPermission) {
                handler.postDelayed(this, 100);
                return;
            }
            if (SystemClock.elapsedRealtime() > actionDeadline) {
                pendingAction = 0;
                return;
            }
            tryPendingAction();
            if (pendingAction != 0) handler.postDelayed(this, 100);
        }
    };

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (youtube != null && !exiting()) {
            if (!hasFocus) youtube.pause();
            else youtube.checkVisibility();
        }
    }

    @Override
    protected void onPause() {
        if (youtube == null) { super.onPause(); return; }
        youtube.pause();
        if (youtube.web != null) youtube.web.onPause();
        if (testActive) finishSyncTest(false);
        if (AudioService.practice != null && AudioService.practice.active) {
            AudioService.practice.stop(false);
        }
        activityVisible = false;
        updateScreenAwake(false);
        handler.removeCallbacks(tick);
        if (!exiting() && (pendingAction == 3 || pendingAction == 4)) handler.post(pendingSource);
        videoPreparing = false;
        if (camera != null) {
            if (camera.recording) stopVideo();
            camera.release();
            camera = null;
        }
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (exiting()) return;
        activityVisible = true;
        if (youtube.web != null) { youtube.web.onResume(); youtube.checkVisibility(); }
        handler.removeCallbacks(pendingSource);
        handler.removeCallbacks(tick);
        handler.post(tick);
        if (tab == 3 && content != null && camera == null) showTab();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(adaptWindow);
        if (modernBack != null) modernBack.unregister();
        backWindows.clear();
        if (youtube == null) {
            handler.removeCallbacksAndMessages(null);
            io.shutdown();
            super.onDestroy();
            return;
        }
        boolean ownsYouTube = YouTubePlayer.current == youtube;
        youtube.destroy();
        if (ownsYouTube) AudioService.youtubeSelected = false;
        if (drawerPopup != null) drawerPopup.dismiss();
        handler.removeCallbacks(tick);
        handler.removeCallbacks(pendingSource);
        if (camera != null) camera.release();
        stopPlayer();
        io.shutdown();
        super.onDestroy();
    }
}
