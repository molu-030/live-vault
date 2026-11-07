package pro.livemic.studio;

import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.Locale;

/** Compact performance surface; advanced editing lives in the Details dialog. */
final class LiveScreen {
    final MainActivity a;
    LiveSessionDisplay display;
    Button sourceButton, inputButton, micButton, monitorToggle, backingPlayButton, detailsButton, video;
    TextView backing, backingTime, route, volumeLabel, micVolumeLabel;
    SeekBar backingVolume, micVolume;
    StudioKnob reverbVolume, echoVolume;
    boolean volumeDragging, micVolumeDragging;
    private final boolean[] dragging = new boolean[20];

    LiveScreen(MainActivity activity) { a = activity; }
    String tr(String ko, String en) { return a.tr(ko, en); }

    Button button(String label, Runnable action) {
        Button b = new Button(a);
        b.setAllCaps(false);
        b.setStateListAnimator(null);
        b.setElevation(0);
        b.setTypeface(StudioTheme.bold(a));
        b.setTextSize(a.tabletLandscape() ? 14f : 10f);
        b.setLineSpacing(0, 0.90f);
        b.setGravity(Gravity.CENTER);
        b.setIncludeFontPadding(false);
        b.setPadding(a.dp(3), 0, a.dp(3), 0);
        b.setMinWidth(a.dp(48));
        b.setMinimumWidth(a.dp(48));
        b.setMinHeight(a.dp(48));
        b.setMinimumHeight(a.dp(48));
        b.setMaxLines(2);
        b.setEllipsize(TextUtils.TruncateAt.END);
        b.setText(label);
        b.setOnClickListener(v -> action.run());
        paint(b, false);
        return b;
    }

    void paint(Button b, boolean on) {
        if (b == null) return;
        b.setBackgroundTintList(null);
        b.setBackground(StudioTheme.background(a, on ? StudioTheme.ACCENT : StudioTheme.RAISED, 8, on ? StudioTheme.ACCENT : StudioTheme.BORDER));
        b.setTextColor(on ? StudioTheme.ACCENT_INK : StudioTheme.TEXT);
        b.setSelected(on);
    }

    void paintDanger(Button b) {
        if (b == null) return;
        b.setBackgroundTintList(null);
        b.setBackground(StudioTheme.background(a, StudioTheme.DANGER, 8, StudioTheme.DANGER));
        b.setTextColor(0xff190b0c);
        b.setSelected(true);
    }

    TextView text(String s, int size, int color) { return a.text(s, size, color); }
    TextView text(String s, float size, int color) {
        TextView t = new TextView(a);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setTypeface(StudioTheme.body(a));
        t.setIncludeFontPadding(false);
        return t;
    }

    LinearLayout panel() {
        LinearLayout p = a.column();
        p.setPadding(a.dp(12), a.dp(4), a.dp(12), a.dp(4));
        p.setBackground(StudioTheme.background(a, StudioTheme.SURFACE, 12, StudioTheme.BORDER));
        return p;
    }

    void addPanel(View p) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = a.dp(6);
        a.page.addView(p, lp);
    }

    TextView heading(String s) {
        TextView t = new TextView(a);
        t.setText(s);
        t.setTextSize(a.tabletLandscape() ? 12f : 10f);
        t.setTextColor(StudioTheme.MUTED);
        t.setTypeface(StudioTheme.bold(a));
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setSingleLine(true);
        t.setIncludeFontPadding(false);
        t.setLineSpacing(0, 1f);
        return t;
    }

    SeekBar gain(LinearLayout parent, String label, int key, int max, TextView value) {
        LinearLayout row = a.row();
        value.setTextSize(a.tabletLandscape() ? 14f : 11f);
        value.setTypeface(StudioTheme.mono(a));
        value.setMaxLines(1);
        value.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        value.setIncludeFontPadding(false);
        value.setLineSpacing(0, 1f);
        TextView name = text(label, a.tabletLandscape() ? 14f : 11f, StudioTheme.MUTED);
        name.setMaxLines(1);
        name.setGravity(Gravity.CENTER_VERTICAL);
        name.setTypeface(StudioTheme.medium(a));
        name.setIncludeFontPadding(false);
        name.setLineSpacing(0, 1f);
        row.addView(name, new LinearLayout.LayoutParams(a.dp(a.tabletLandscape() ? 90 : 62), a.dp(48)));
        SeekBar bar = gainBar(label, key, max, value);
        row.addView(bar, new LinearLayout.LayoutParams(0, a.dp(48), 1));
        row.addView(value, new LinearLayout.LayoutParams(a.dp(a.tabletLandscape() ? 64 : 46), a.dp(48)));
        parent.addView(row);
        return bar;
    }

    SeekBar compactGain(LinearLayout parent, String label, int key, int max, TextView value) {
        LinearLayout row = a.row();
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = text(label, 10f, StudioTheme.MUTED);
        name.setMaxLines(1);
        name.setGravity(Gravity.CENTER_VERTICAL);
        name.setTypeface(StudioTheme.medium(a));
        name.setIncludeFontPadding(false);
        name.setLineSpacing(0, 1f);
        row.addView(name, new LinearLayout.LayoutParams(a.dp(48), a.dp(48)));
        SeekBar bar = gainBar(label, key, max, value);
        bar.setPadding(a.dp(4), 0, a.dp(4), 0);
        row.addView(bar, new LinearLayout.LayoutParams(0, a.dp(48), 1));
        value.setTextSize(10f);
        value.setTypeface(StudioTheme.mono(a));
        value.setMaxLines(1);
        value.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        value.setIncludeFontPadding(false);
        value.setLineSpacing(0, 1f);
        row.addView(value, new LinearLayout.LayoutParams(a.dp(40), a.dp(48)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, a.dp(48), 1);
        if (parent.getChildCount() > 0) lp.leftMargin = a.dp(4);
        parent.addView(row, lp);
        return bar;
    }

    SeekBar gainBar(String label, int key, int max, TextView value) {
        SeekBar bar = new SeekBar(a);
        bar.setMax(max);
        bar.setProgress(Math.round(a.values[key] * 100));
        value.setText(bar.getProgress() + "%");
        bar.setContentDescription(label);
        bar.setMinimumHeight(a.dp(48));
        bar.setProgressTintList(ColorStateList.valueOf(StudioTheme.ACCENT));
        bar.setThumbTintList(ColorStateList.valueOf(StudioTheme.ACCENT));
        bar.setProgressBackgroundTintList(ColorStateList.valueOf(0xff30352b));
        bar.setPadding(a.dp(8), 0, a.dp(8), 0);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onStartTrackingTouch(SeekBar b) {
                dragging[key] = true;
                if (key == 0) micVolumeDragging = true;
                if (key == 1) volumeDragging = true;
            }
            public void onStopTrackingTouch(SeekBar b) {
                dragging[key] = false;
                if (key == 0) micVolumeDragging = false;
                if (key == 1) volumeDragging = false;
            }
            public void onProgressChanged(SeekBar b, int v, boolean user) {
                if (user) {
                    a.set(key, v / 100f);
                    value.setText(v + "%");
                }
            }
        });
        return bar;
    }

    void draw() {
        boolean tablet = a.tabletLandscape();
        LinearLayout tabletRow = tablet ? a.row() : null;
        LinearLayout tabletRight = tablet ? a.column() : null;
        // 1. LIVE SESSION LCD DISPLAY & 2x2 I/O CONTROLS
        LinearLayout session = panel();
        display = new LiveSessionDisplay(a);
        if (tablet) {
            session.setTag("tablet-live-left");
            session.setPadding(a.dp(16), a.dp(14), a.dp(16), a.dp(12));
            display.setTag("tablet-live-session-display");
            session.addView(display, new LinearLayout.LayoutParams(-1, 0, 1));
        } else {
            session.addView(display, new LinearLayout.LayoutParams(-1, a.dp(170)));
            a.page.flexible(display, 151, 300);
        }

        // Section label "입출력" & 2x2 Input / Output Controls Grid
        session.addView(heading(tr("입출력", "I/O")), new LinearLayout.LayoutParams(-1, -2));
        LinearLayout grid = a.column();

        // Row 1: Mic Toggle (left) | Monitor Toggle (right)
        LinearLayout row1 = a.row();
        micButton = a.micButton = button(tr("마이크 켜기", "Turn mic on"), a::requestMicToggle);
        row1.addView(a.micButton, new LinearLayout.LayoutParams(0, a.dp(48), 1));

        monitorToggle = button(tr("목소리 듣기", "Monitor"), this::toggleMonitor);
        LinearLayout.LayoutParams monLp = new LinearLayout.LayoutParams(0, a.dp(48), 1);
        monLp.leftMargin = a.dp(6);
        row1.addView(monitorToggle, monLp);
        grid.addView(row1, new LinearLayout.LayoutParams(-1, a.dp(48)));

        // Row 2: Input Device (left) | Output Device (right)
        LinearLayout row2 = a.row();
        LinearLayout.LayoutParams row2Lp = new LinearLayout.LayoutParams(-1, a.dp(48));
        row2Lp.topMargin = a.dp(4);

        inputButton = button(tr("입력", "Input") + " ▾\n" + compactDevice(a.inputName()), a::selectInput);
        sourceButton = inputButton; // Keep reference for test compatibility
        row2.addView(inputButton, new LinearLayout.LayoutParams(0, a.dp(48), 1));

        a.outputButton = button(tr("출력", "Output") + " ▾\n" + compactDevice(a.outputName()), a::outputMode);
        LinearLayout.LayoutParams outLp = new LinearLayout.LayoutParams(0, a.dp(48), 1);
        outLp.leftMargin = a.dp(6);
        row2.addView(a.outputButton, outLp);
        grid.addView(row2, row2Lp);

        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
        cp.topMargin = a.dp(4);
        session.addView(grid, cp);

        route = text("", 10, StudioTheme.MUTED);
        route.setVisibility(View.GONE);
        micVolumeLabel = text("", 11, StudioTheme.MUTED);
        volumeLabel = text("", 11, StudioTheme.MUTED);

        micVolume = gain(session, tr("마이크", "Mic"), 0, 200, micVolumeLabel);
        if (tablet) {
            tabletRow.setTag("tablet-live-workspace");
            tabletRow.addView(session, new LinearLayout.LayoutParams(0, -1, 1.12f));
            ScrollView rightScroll = new ScrollView(a);
            rightScroll.setTag("tablet-live-right");
            rightScroll.setFillViewport(true);
            rightScroll.setClipToPadding(false);
            rightScroll.addView(tabletRight, new ScrollView.LayoutParams(-1, -2));
            LinearLayout.LayoutParams rightLp = new LinearLayout.LayoutParams(0, -1, 1);
            rightLp.leftMargin = a.dp(14);
            tabletRow.addView(rightScroll, rightLp);
            a.page.addView(tabletRow, new LinearLayout.LayoutParams(-1, a.dp(420)));
            a.page.flexible(tabletRow, 400, 1600);
        } else {
            a.page.addView(session, new LinearLayout.LayoutParams(-1, -2));
        }

        // 2. ACCOMPANIMENT PANEL (Clean Backing Surface)
        LinearLayout accompaniment = panel();
        if (tablet) accompaniment.setPadding(a.dp(16), a.dp(14), a.dp(16), a.dp(12));
        accompaniment.addView(heading(tablet ? tr("반주", "ACCOMPANIMENT") : tr("🎵 반주", "🎵 ACCOMPANIMENT")), new LinearLayout.LayoutParams(-1, -2));

        LinearLayout player = a.row();
        backingPlayButton = button("▶", this::toggleBackingPlay);
        backingPlayButton.setTextSize(18);
        player.addView(backingPlayButton, new LinearLayout.LayoutParams(a.dp(48), a.dp(48)));

        LinearLayout track = a.column();
        track.setGravity(Gravity.CENTER_VERTICAL);
        track.setPadding(a.dp(10), 0, 0, 0);
        backing = text("", tablet ? 16 : 12, StudioTheme.TEXT);
        backing.setTypeface(StudioTheme.medium(a));
        backing.setMaxLines(1);
        backing.setEllipsize(TextUtils.TruncateAt.END);
        backingTime = text("00:00 / 00:00", tablet ? 12 : 10, StudioTheme.OLIVE);
        backingTime.setTypeface(StudioTheme.mono(a));
        track.addView(backing);
        track.addView(backingTime);
        track.setOnClickListener(v -> a.chooseBacking());
        track.setFocusable(true);
        track.setContentDescription(tr("반주 선택 또는 변경", "Choose or change backing"));
        player.addView(track, new LinearLayout.LayoutParams(0, a.dp(48), 1));

        Button chooseBacking = button(tr("반주 선택 ▾", "Choose backing ▾"), a::chooseBacking);
        player.addView(chooseBacking, new LinearLayout.LayoutParams(a.dp(tablet ? 126 : 80), a.dp(48)));

        accompaniment.addView(player);
        backingVolume = gain(accompaniment, tr("반주", "Backing"), 1, 100, volumeLabel);
        if (a.youtube.selected) a.youtube.attach(accompaniment);
        if (tablet) tabletRight.addView(accompaniment, new LinearLayout.LayoutParams(-1, -2));
        else addPanel(accompaniment);

        // Compact rotary controls keep horizontal system Back gestures available.
        LinearLayout fx = panel();
        LinearLayout knobs = a.row();
        reverbVolume = effectKnob(knobs, tr("리버브", "Reverb"), 2, 1);
        echoVolume = effectKnob(knobs, tr("에코", "Echo"), 4, .8f);
        detailsButton = button(tr("상세 ▾", "Details ▾"), this::effectsDetails);
        detailsButton.setTextSize(tablet ? 14f : 10.5f);
        detailsButton.setPadding(a.dp(2), 0, a.dp(2), 0);

        LinearLayout fh = a.row();
        fh.addView(heading(tablet ? tr("보컬 효과", "VOCAL FX") : tr("✨ 보컬 효과", "✨ VOCAL FX")), new LinearLayout.LayoutParams(0, a.dp(48), 1));
        fh.addView(detailsButton, new LinearLayout.LayoutParams(a.dp(tablet ? 112 : 80), a.dp(48)));
        fx.addView(fh);
        if (tablet) {
            // A real base height survives ScrollView's initial unspecified measurement;
            // weights consume spare viewport height only when fillViewport remeasures.
            knobs.setMinimumHeight(a.dp(170));
            fx.addView(knobs, new LinearLayout.LayoutParams(-1, a.dp(170), 1));
            fx.setMinimumHeight(a.dp(240));
            fx.setPadding(a.dp(16), a.dp(8), a.dp(16), a.dp(12));
            LinearLayout.LayoutParams fxLp = new LinearLayout.LayoutParams(-1, -2, 1);
            fxLp.topMargin = a.dp(12);
            tabletRight.addView(fx, fxLp);
        } else {
            fx.addView(knobs, new LinearLayout.LayoutParams(-1, a.dp(96)));
            addPanel(fx);
        }

        // 4. ACTION DOCK
        LinearLayout dock = a.row();
        a.recButton = button(tr("● 녹음 시작", "● Record"), a::toggleRecord);
        dock.addView(a.recButton, new LinearLayout.LayoutParams(0, a.dp(48), 1));
        video = button(tr("영상", "Video"), () -> a.chooseTab(3));
        LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(a.dp(90), a.dp(48));
        vp.leftMargin = a.dp(8);
        dock.addView(video, vp);
        a.actionDock.addView(dock);
        a.recordOptions = a.globalOptions;

        poll(NativeAudio.stats(), AudioService.active);
    }

    StudioKnob effectKnob(LinearLayout parent, String label, int key, float max) {
        StudioKnob knob = new StudioKnob(a, label, 0, max, a.values[key],
                v -> Math.round(v * 100) + "%", v -> a.set(key, v));
        if (!a.tabletLandscape()) knob.compact();
        knob.setOnClickListener(v -> adjustEffectDialog(label, key, max, knob));
        parent.addView(knob, new LinearLayout.LayoutParams(0, a.tabletLandscape() ? -1 : a.dp(96), 1));
        return knob;
    }

    void adjustEffectDialog(String title, int key, float max, StudioKnob knob) {
        float[] val = { a.values[key] };
        LinearLayout p = a.column();
        p.setPadding(a.dp(16), a.dp(12), a.dp(16), a.dp(12));

        TextView display = a.text(Math.round(val[0] * 100) + "%", 26, MainActivity.TEXT);
        display.setTypeface(StudioTheme.bold(a));
        display.setGravity(Gravity.CENTER);
        display.setPadding(0, 0, 0, a.dp(8));
        p.addView(display);

        int maxPercent = Math.round(max * 100);
        SeekBar bar = new SeekBar(a);
        bar.setMax(maxPercent);
        bar.setProgress(Math.round(val[0] * 100));
        bar.setPadding(a.dp(14), 0, a.dp(14), 0);
        java.util.function.IntConsumer applyPercent = percent -> {
            int bounded = Math.max(0, Math.min(maxPercent, percent));
            val[0] = bounded / 100f;
            a.set(key, val[0]);
            knob.sync(val[0]);
            display.setText(bounded + "%");
            bar.setProgress(bounded);
        };
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (fromUser) applyPercent.accept(progress);
            }
            public void onStartTrackingTouch(SeekBar sb) {}
            public void onStopTrackingTouch(SeekBar sb) {}
        });

        LinearLayout presets = a.row();
        presets.setGravity(Gravity.CENTER);
        for (int i = 0; i <= 4; i++) {
            final int percent = Math.round(maxPercent * i / 4f);
            Button chip = StudioTheme.styled(a, percent + "%", StudioButton.CHIP,
                    () -> applyPercent.accept(percent));
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, a.dp(40), 1);
            if (i > 0) clp.leftMargin = a.dp(4);
            presets.addView(chip, clp);
        }
        LinearLayout.LayoutParams prLp = new LinearLayout.LayoutParams(-1, -2);
        prLp.bottomMargin = a.dp(14);
        p.addView(presets, prLp);

        LinearLayout stepRow = a.row();
        stepRow.setGravity(Gravity.CENTER_VERTICAL);
        Button minus = StudioTheme.styled(a, "−5%", StudioButton.STANDARD,
                () -> applyPercent.accept(Math.round(val[0] * 100) - 5));
        stepRow.addView(minus, new LinearLayout.LayoutParams(a.dp(56), a.dp(48)));
        stepRow.addView(bar, new LinearLayout.LayoutParams(0, -2, 1));

        Button plus = StudioTheme.styled(a, "+5%", StudioButton.STANDARD,
                () -> applyPercent.accept(Math.round(val[0] * 100) + 5));
        stepRow.addView(plus, new LinearLayout.LayoutParams(a.dp(56), a.dp(48)));
        p.addView(stepRow);

        StudioDialogs.form(a, title + tr(" 조절", " Adjust"), p, tr("완료", "Done"), () -> {
            a.set(key, val[0]);
            knob.sync(val[0]);
        }, null);
    }

    void openSource(int source) {
        if (a.settingsLocked()) {
            a.toast(tr("녹음 또는 예약을 마친 뒤 소스를 바꾸세요.", "Finish recording or the countdown before changing sources."));
            return;
        }
        if (source != 2) a.leaveYouTube();
        a.sourceTab = source;
        a.getPreferences(0).edit().putInt("backingSource", source).apply();
        a.chooseTab(1);
    }

    void effectsDetails() {
        LinearLayout p = panel();
        p.setBackground(null);
        LinearLayout previous = a.content;
        a.content = p;
        Button preset = button(tr("프리셋 · ", "Preset · ") + a.presetName() + " ▾", () -> {});
        p.addView(preset);
        a.slider(tr("리버브 잔향", "Reverb decay"), 3, .3f, 6, v -> String.format(Locale.US, "%.1f s", v));
        a.slider(tr("에코 간격", "Echo delay"), 5, 70, 700, v -> Math.round(v) + " ms");
        a.slider(tr("에코 반복", "Echo feedback"), 6, 0, .7f, v -> Math.round(v * 100) + "%");
        for (int key = 7; key <= 9; key++) a.slider(new String[]{"Low EQ", "Mid EQ", "High EQ"}[key - 7], key, -12, 12, v -> Math.round(v) + " dB");
        a.toggle(tr("컴프레서", "Compressor"), 10);
        a.toggle(tr("소음 완화", "Noise reduction"), 19);
        p.addView(button(tr("리버브 공간 ▾", "Reverb space ▾"), () -> StudioDialogs.choose(a, tr("리버브 공간", "Reverb space"), new String[]{tr("홀", "Hall"), tr("룸", "Room"), tr("플레이트", "Plate"), tr("대성당", "Cathedral")}, Math.round(a.values[18]), v -> a.set(18, v))));
        a.content = previous;
        AlertDialog dialog = StudioDialogs.darkAlert(a)
                .setTitle(tr("상세 보컬 효과", "Vocal FX details"))
                .setView(StudioDialogs.scrollable(a, p))
                .setPositiveButton(tr("닫기", "Close"), (d, w) -> sync())
                .create();
        preset.setOnClickListener(v -> { dialog.dismiss(); a.presetPicker(); });
        p.addView(button(tr("효과 초기화", "Reset effects"), () -> { reset(); dialog.dismiss(); effectsDetails(); }));
        StudioDialogs.showDark(a, dialog);
    }

    void reset() {
        int[] keys = {2, 3, 4, 5, 6, 7, 8, 9, 10, 18};
        float[] values = {.27f, 2.2f, .14f, 280, .25f, 0, 0, 1, 1, 0};
        for (int i = 0; i < keys.length; i++) a.set(keys[i], values[i]);
        sync();
    }

    void toggleMonitor() {
        float[] s = NativeAudio.stats();
        if (s[17] > .5f) {
            NativeAudio.resetFeedback();
            NativeComposer.monitor(true);
            a.harmony.monitor(true);
            a.set(11, 1);
            a.toast(tr("보호 음소거를 해제하고 실시간 출력을 켰습니다.", "Safety mute reset; live monitoring is on."));
            return;
        }
        boolean on = AudioService.active ? s[21] > .5f : a.values[11] > .5f;
        if (on) {
            a.set(11, 0);
            a.toast(tr("내 목소리 듣기를 껐습니다.", "Voice monitoring turned off."));
        } else {
            if (!AudioService.canMonitor(a)) NativeAudio.monitorLevel(.22f);
            a.set(11, 1);
            if (!AudioService.active) a.requestMicToggle();
            else a.toast(tr("내 목소리 듣기를 켰습니다.", "Voice monitoring turned on."));
        }
        poll(NativeAudio.stats(), AudioService.active);
    }

    void toggleBackingPlay() {
        if (a.youtube.selected) {
            a.youtube.pause();
            a.toast(tr("동영상의 YouTube 재생 버튼을 사용하세요.", "Use the YouTube player controls to play the video."));
            return;
        }
        if (a.settingsLocked()) {
            a.toast(tr("녹음 또는 예약 중에는 반주를 바꿀 수 없습니다.", "Accompaniment is locked during recording or countdown."));
            return;
        }
        float[] s = NativeAudio.stats();
        if (a.composer.playing()) a.composer.pause();
        else if (s[16] > .5f) NativeAudio.playLocal(false);
        else if (AudioService.composerRequested) a.composer.play();
        else if (!a.selectedFile.isEmpty()) a.ensureMic(4);
        else {
            a.chooseBacking();
            return;
        }
        poll(NativeAudio.stats(), AudioService.active);
    }

    String compactDevice(String name) {
        if (name == null || name.isEmpty()) return tr("기본", "Default");
        if (name.equals(tr("시스템 기본", "System default")) || name.equals(tr("시스템 기본값", "System default"))) return tr("기본", "Default");
        int dot = name.indexOf(" · ");
        if (dot > 0) name = name.substring(0, dot);
        int hash = name.indexOf(" #");
        if (hash > 0) name = name.substring(0, hash);
        name = name.trim();
        if (name.length() > 14) name = name.substring(0, 14).trim();
        return name;
    }

    void syncGain(SeekBar b, TextView label, int key, String name) {
        if (b == null) return;
        if (!dragging[key]) b.setProgress(Math.round(a.values[key] * 100));
        label.setText(b.getProgress() + "%");
    }

    void updateVolumeLabel(int value) { if (volumeLabel != null) volumeLabel.setText(value + "%"); }
    void updateMicVolumeLabel(int value) { if (micVolumeLabel != null) micVolumeLabel.setText(value + "%"); }

    void sync() {
        syncGain(micVolume, micVolumeLabel, 0, tr("마이크", "Mic"));
        syncGain(backingVolume, volumeLabel, 1, tr("반주", "Backing"));
        if (reverbVolume != null) reverbVolume.sync(a.values[2]);
        if (echoVolume != null) echoVolume.sync(a.values[4]);
    }

    void poll(float[] s, boolean running) {
        if (display == null) return;
        float[] safe = new float[22];
        if (s != null) for (int i = 0; i < Math.min(s.length, safe.length); i++) safe[i] = Float.isFinite(s[i]) ? s[i] : 0;
        s = safe;

        a.micButton.setText(running ? tr("마이크 끄기", "Turn mic off") : tr("마이크 켜기", "Turn mic on"));
        paint(a.micButton, running);

        boolean muted = s[17] > .5f, on = !muted && running && s[21] > .5f;
        String monitoring = muted ? tr("보호 음소거", "Safety mute") : on ? tr("모니터 켜짐", "Monitoring on") : tr("모니터 꺼짐", "Monitoring off");
        route.setText(monitoring);
        monitorToggle.setText(muted ? tr("보호 해제", "Reset safety") : on ? tr("목소리 듣기 켬", "Monitor ON") : tr("목소리 듣기", "Monitor"));
        monitorToggle.setContentDescription(monitoring);
        paint(monitorToggle, on);

        inputButton.setText(tr("입력", "Input") + " ▾\n" + compactDevice(a.inputName()));
        inputButton.setContentDescription(tr("입력 장치: ", "Input device: ") + a.inputName());

        if (a.outputButton != null) {
            a.outputButton.setText(tr("출력", "Output") + " ▾\n" + compactDevice(a.outputName()));
            a.outputButton.setContentDescription(tr("출력 장치: ", "Output device: ") + a.outputName());
        }

        boolean rec = RecordingManager.active, wait = AudioService.waiting();
        String state = a.busy ? tr("준비 / 저장 중", "PREPARING / SAVING") : rec ? (RecordingManager.paused ? tr("일시정지", "PAUSED") : "REC") : wait ? tr("예약", "COUNTDOWN") : running ? tr("마이크 켜짐", "MIC ON") : tr("대기", "READY");
        String time = rec ? a.formatTime(s[8]) : wait ? String.valueOf(AudioService.remaining()) + "s" : "00:00";
        float hz = running && Float.isFinite(s[0]) && s[0] > 0 ? s[0] : 0;
        display.cents = hz > 0 ? Math.max(-50, Math.min(50, s[1])) : 0;
        display.update(running ? s[3] : 0, s[4], hz, hz > 0 && Float.isFinite(s[2]) ? a.noteName((int) s[2]) : "—", state, time, rec && !RecordingManager.paused, monitoring);

        boolean composer = s[16] <= .5f && (a.composer.playing() || AudioService.composerRequested);
        boolean playing = a.composer.playing() || s[16] > .5f;
        if (composer) {
            double[] cs = a.composer.state();
            backing.setText(a.composer.project.name + " ▾");
            backingTime.setText(tr("작곡기 · 마디 ", "Composer · bar ") + (1 + (int) (Math.max(0, cs[0]) / MusicTheory.barBeats(a.composer.project.meter))) + (!playing ? tr(" · 일시정지", " · paused") : ""));
        } else {
            double[] local = NativeAudio.localPlaybackInfo();
            backing.setText(a.selectedFile.isEmpty() ? tr("반주 선택 ▾", "Choose backing ▾") : a.selectedFile + " ▾");
            backingTime.setText(a.formatTime(local[0]) + " / " + a.formatTime(local[1]));
        }
        if (a.youtube.selected) {
            backing.setText("YouTube · " + a.youtube.id);
            backingTime.setText(tr("마이크만 녹음 · 동영상 제어 사용", "Mic-only recording · use video controls"));
            playing = false;
        }
        backing.setContentDescription(tr("반주 선택: ", "Choose backing: ") + backing.getText().toString().replace(" ▾", ""));
        backingPlayButton.setText(playing ? "Ⅱ" : "▶");
        backingPlayButton.setContentDescription(playing ? tr("반주 일시정지", "Pause accompaniment") : tr("반주 재생", "Play accompaniment"));
        paint(backingPlayButton, playing);
        if (a.youtube.selected) {
            backingPlayButton.setText("Ⅱ");
            backingPlayButton.setContentDescription(tr("YouTube 일시정지 · 재생은 동영상 제어 사용", "Pause YouTube · use video controls to play"));
        }
        backingPlayButton.setEnabled(a.youtube.selected || !a.settingsLocked());
        sync();
    }

    void transport(boolean busy, boolean waiting, boolean recording) {
        if (waiting || recording) paintDanger(a.recButton);
        else paint(a.recButton, !busy);
        video.setEnabled(!busy && !waiting && !recording && !a.recordPermissionPending && !a.starting);
    }
}
