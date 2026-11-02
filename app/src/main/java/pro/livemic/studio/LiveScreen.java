package pro.livemic.studio;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import java.util.Locale;

/**
 * Redesigned Live Studio console matching the dark charcoal + neon lime reference UI:
 * 1. Live Session LCD display + Mic/Monitor/Route toolbar + Mic Volume slider
 * 2. Accompaniment (반주) panel + Backing Volume slider
 * 3. Vocal Effects 3x2 Mini-Dial Grid + Fine-Control Slider Box
 * 4. Fixed bottom recording dock
 */
final class LiveScreen {
    static final int IVORY = StudioTheme.TEXT;
    static final int INK = StudioTheme.ACCENT_INK;
    static final int PANEL = StudioTheme.SURFACE;
    static final int SCREEN = StudioTheme.LCD_BG;

    final MainActivity a;
    final int[] ids = {2, 3, 0, 4, 5, 6};
    final float[] mins = {0, .3f, 0, 0, 70, 0};
    final float[] maxs = {1, 6, 2, .8f, 700, .7f};
    final Button[] selectors = new Button[6];
    final MainActivity.Formatter[] format = {
        v -> Math.round(v * 100) + "%",
        v -> String.format(Locale.US, "%.1f s", v),
        v -> Math.round(v * 100) + "%",
        v -> Math.round(v * 100) + "%",
        v -> Math.round(v) + " ms",
        v -> Math.round(v * 100) + "%"
    };

    SharedEffectDial dial;
    TextView mic, route, clock, backing, pitch, level, effectName, effectValue, volumeLabel, micVolumeLabel, sessionStatus;
    ProgressBar meter;
    Button video, inputButton, filesButton, onlineButton, composerButton, monitorToggle, backingPlayButton;
    SeekBar backingVolume, micVolume;
    boolean volumeDragging, micVolumeDragging;

    LiveScreen(MainActivity activity) {
        a = activity;
    }

    String tr(String ko, String en) {
        return a.tr(ko, en);
    }

    String name(int n) {
        return new String[]{
            tr("리버브", "Reverb"),
            tr("잔향 길이", "Decay"),
            tr("목소리 크기", "Voice level"),
            tr("에코 양", "Echo"),
            tr("에코 간격", "Delay"),
            tr("에코 반복", "Repeat")
        }[n];
    }

    Button button(String text, Runnable action) {
        Button b = new Button(a);
        b.setAllCaps(false);
        b.setStateListAnimator(null);
        b.setElevation(0);
        b.setTypeface(StudioTheme.bold(a));
        b.setTextSize(11.5f);
        b.setGravity(Gravity.CENTER);
        b.setIncludeFontPadding(false);
        b.setPadding(a.dp(6), a.dp(2), a.dp(6), a.dp(2));
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinHeight(a.dp(48));
        b.setMinimumHeight(a.dp(48));
        b.setMaxLines(2);
        b.setEllipsize(TextUtils.TruncateAt.END);
        b.setText(text);
        b.setOnClickListener(v -> action.run());
        paint(b, false);
        return b;
    }

    void paint(Button b, boolean selected) {
        b.setBackgroundTintList(null);
        if (b instanceof EffectCardButton) {
            b.setBackground(StudioTheme.background(
                    a,
                    selected ? StudioTheme.CARD_SELECTED_BG : StudioTheme.CARD_BG,
                    8,
                    selected ? StudioTheme.CARD_SELECTED_BORDER : StudioTheme.CARD_BORDER
            ));
            b.setTextColor(selected ? StudioTheme.ACCENT : StudioTheme.TEXT);
        } else {
            b.setBackground(StudioTheme.background(
                    a,
                    selected ? StudioTheme.ACCENT : StudioTheme.RAISED,
                    7,
                    selected ? StudioTheme.ACCENT : 0xff35383e
            ));
            b.setTextColor(selected ? StudioTheme.ACCENT_INK : StudioTheme.TEXT);
        }
        b.setSelected(selected);
    }

    void paintDanger(Button b) {
        b.setBackgroundTintList(null);
        b.setBackground(StudioTheme.background(a, StudioTheme.DANGER, 7, StudioTheme.DANGER));
        b.setTextColor(0xff190b0c);
        b.setSelected(true);
    }

    TextView text(String t, int size, int color) {
        return a.text(t, size, color);
    }

    /** Custom 3x2 Effect Card Button with a native-drawn mini rotary dial in the upper half. */
    final class EffectCardButton extends Button {
        final int index;
        final Paint dialPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final RectF dialArc = new RectF();

        EffectCardButton(Context c, int index, Runnable action) {
            super(c);
            this.index = index;
            setAllCaps(false);
            setStateListAnimator(null);
            setElevation(0);
            setTypeface(StudioTheme.bold(c));
            setTextSize(10.5f);
            setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            setIncludeFontPadding(false);
            setLineSpacing(a.dp(1), 1f);
            setPadding(a.dp(3), a.dp(2), a.dp(3), a.dp(4));
            setMinWidth(a.dp(48));
            setMinimumWidth(a.dp(48));
            setMinHeight(a.dp(48));
            setMinimumHeight(a.dp(48));
            setMaxLines(2);
            setOnClickListener(v -> action.run());
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float d = getResources().getDisplayMetrics().density;
            float w = getWidth(), h = getHeight();
            int textHeight = getLayout() != null ? getLayout().getHeight() : Math.round(26 * d);
            float availableTop = Math.max(20 * d, h - textHeight - 6 * d);
            float cx = w / 2f;
            float cy = Math.max(12 * d, availableTop / 2f + d);
            float r = Math.min(12.5f * d, (availableTop - 4 * d) / 2f);

            if (r >= 6 * d) {
                float min = mins[index], max = maxs[index];
                float val = Math.max(min, Math.min(max, a.values[ids[index]]));
                float frac = (val - min) / (max - min);
                boolean sel = isSelected();

                dialArc.set(cx - r, cy - r, cx + r, cy + r);
                dialPaint.setStyle(Paint.Style.STROKE);
                dialPaint.setStrokeWidth(2.2f * d);
                dialPaint.setStrokeCap(Paint.Cap.ROUND);
                dialPaint.setColor(0xff2c323b);
                canvas.drawArc(dialArc, 135, 270, false, dialPaint);

                dialPaint.setColor(sel ? StudioTheme.ACCENT : StudioTheme.OLIVE);
                canvas.drawArc(dialArc, 135, 270 * frac, false, dialPaint);

                dialPaint.setStyle(Paint.Style.FILL);
                dialPaint.setColor(0xff121519);
                canvas.drawCircle(cx, cy, r * 0.74f, dialPaint);

                dialPaint.setStyle(Paint.Style.STROKE);
                dialPaint.setStrokeWidth(0.9f * d);
                dialPaint.setColor(sel ? 0xff5a6624 : 0xff3a404a);
                canvas.drawCircle(cx, cy, r * 0.74f, dialPaint);

                double rad = Math.toRadians(135 + 270 * frac);
                float nx = (float) Math.cos(rad), ny = (float) Math.sin(rad);
                dialPaint.setStrokeWidth(1.6f * d);
                dialPaint.setColor(StudioTheme.ACCENT);
                canvas.drawLine(cx + nx * r * 0.25f, cy + ny * r * 0.25f, cx + nx * r * 0.64f, cy + ny * r * 0.64f, dialPaint);
                dialPaint.setStyle(Paint.Style.FILL);
            }
            super.onDraw(canvas);
        }
    }

    void draw() {
        // 1. LIVE SESSION PANEL
        LinearLayout sessionPanel = a.column();
        sessionPanel.setPadding(a.dp(8), a.dp(6), a.dp(8), a.dp(6));
        sessionPanel.setBackground(StudioTheme.background(a, StudioTheme.SURFACE, 10, StudioTheme.BORDER));

        LinearLayout lcd = a.column();
        lcd.setPadding(a.dp(9), a.dp(5), a.dp(9), a.dp(6));
        lcd.setBackground(StudioTheme.background(a, StudioTheme.LCD_BG, 7, StudioTheme.LCD_BORDER));

        LinearLayout lcdTop = a.row();
        mic = text("", 11, StudioTheme.ACCENT);
        mic.setTypeface(StudioTheme.bold(a));
        lcdTop.addView(mic, new LinearLayout.LayoutParams(0, -2, 1));
        route = text("", 10, StudioTheme.OLIVE);
        route.setMaxLines(1);
        route.setEllipsize(TextUtils.TruncateAt.END);
        route.setOnClickListener(v -> a.monitoringOptions());
        route.setFocusable(true);
        lcdTop.addView(route);
        lcd.addView(lcdTop);

        LinearLayout lcdMid = a.row();
        LinearLayout.LayoutParams midLp = new LinearLayout.LayoutParams(-1, -2);
        midLp.topMargin = a.dp(2);
        clock = text("00:00", 17, StudioTheme.TEXT);
        clock.setTypeface(StudioTheme.mono(a));
        lcdMid.addView(clock, new LinearLayout.LayoutParams(0, -2, 1));
        pitch = text("", 11, StudioTheme.OLIVE);
        pitch.setTypeface(StudioTheme.mono(a));
        pitch.setPadding(0, 0, a.dp(8), 0);
        lcdMid.addView(pitch);
        level = text("", 11, StudioTheme.MUTED);
        level.setTypeface(StudioTheme.mono(a));
        lcdMid.addView(level);
        lcd.addView(lcdMid, midLp);

        meter = new ProgressBar(a, null, android.R.attr.progressBarStyleHorizontal);
        meter.setMax(1000);
        meter.setContentDescription(tr("입력 레벨", "Input level"));
        meter.setProgressTintList(ColorStateList.valueOf(StudioTheme.ACCENT));
        meter.setProgressBackgroundTintList(ColorStateList.valueOf(0xff1e261f));
        LinearLayout.LayoutParams meterLp = new LinearLayout.LayoutParams(-1, a.dp(5));
        meterLp.topMargin = a.dp(4);
        lcd.addView(meter, meterLp);
        sessionPanel.addView(lcd);

        // Mic & Route Controls Row
        LinearLayout controls = a.row();
        LinearLayout.LayoutParams controlsLp = new LinearLayout.LayoutParams(-1, a.dp(48));
        controlsLp.topMargin = a.dp(5);
        a.micButton = button(tr("마이크 켜기", "Turn mic on"), a::requestMicToggle);
        controls.addView(a.micButton, new LinearLayout.LayoutParams(0, a.dp(48), 1.15f));

        monitorToggle = button(tr("내 목소리 듣기", "Monitor"), this::toggleMonitor);
        monitorToggle.setTextSize(10.5f);
        LinearLayout.LayoutParams monLp = new LinearLayout.LayoutParams(0, a.dp(48), 1.15f);
        monLp.leftMargin = a.dp(5);
        controls.addView(monitorToggle, monLp);

        inputButton = button(tr("입력 ▾", "Input ▾"), a::selectInput);
        inputButton.setTextSize(10.5f);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(0, a.dp(48), 0.9f);
        ip.leftMargin = a.dp(5);
        controls.addView(inputButton, ip);

        a.outputButton = button(tr("출력 ▾", "Output ▾"), a::outputMode);
        a.outputButton.setTextSize(10.5f);
        LinearLayout.LayoutParams op = new LinearLayout.LayoutParams(0, a.dp(48), 0.9f);
        op.leftMargin = a.dp(5);
        controls.addView(a.outputButton, op);
        sessionPanel.addView(controls, controlsLp);

        // Microphone Volume Slider Row (param 0)
        LinearLayout micVolRow = a.row();
        LinearLayout.LayoutParams micVolLp = new LinearLayout.LayoutParams(-1, a.dp(32));
        micVolLp.topMargin = a.dp(3);
        micVolumeLabel = text("", 11, StudioTheme.MUTED);
        micVolumeLabel.setGravity(Gravity.CENTER_VERTICAL);
        micVolRow.addView(micVolumeLabel, new LinearLayout.LayoutParams(-2, a.dp(32)));
        micVolume = new SeekBar(a);
        micVolume.setMax(200);
        micVolume.setProgress(Math.round(a.values[0] * 100));
        micVolume.setContentDescription(tr("마이크 볼륨", "Microphone volume"));
        micVolume.setProgressTintList(ColorStateList.valueOf(StudioTheme.ACCENT));
        micVolume.setThumbTintList(ColorStateList.valueOf(StudioTheme.TEXT));
        micVolume.setProgressBackgroundTintList(ColorStateList.valueOf(0xff272b31));
        micVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onStartTrackingTouch(SeekBar b) {
                micVolumeDragging = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar b) {
                micVolumeDragging = false;
            }

            @Override
            public void onProgressChanged(SeekBar b, int value, boolean user) {
                if (user) {
                    a.set(0, value / 100f);
                    updateMicVolumeLabel(value);
                    sync();
                }
            }
        });
        micVolRow.addView(micVolume, new LinearLayout.LayoutParams(0, a.dp(32), 1));
        sessionPanel.addView(micVolRow, micVolLp);

        LinearLayout.LayoutParams sessionPanelLp = new LinearLayout.LayoutParams(-1, -2);
        sessionPanelLp.bottomMargin = a.dp(5);
        a.page.addView(sessionPanel, sessionPanelLp);

        // 2. ACCOMPANIMENT (반주) PANEL
        LinearLayout backingPanel = a.column();
        backingPanel.setPadding(a.dp(8), a.dp(5), a.dp(8), a.dp(5));
        backingPanel.setBackground(StudioTheme.background(a, StudioTheme.SURFACE, 10, StudioTheme.BORDER));

        LinearLayout sources = a.row();
        backing = text("", 11, StudioTheme.OLIVE);
        backing.setTypeface(StudioTheme.medium(a));
        backing.setMaxLines(1);
        backing.setEllipsize(TextUtils.TruncateAt.END);
        sources.addView(backing, new LinearLayout.LayoutParams(0, -2, 1.15f));

        backingPlayButton = button("▶", this::toggleBackingPlay);
        LinearLayout.LayoutParams playLp = new LinearLayout.LayoutParams(a.dp(48), a.dp(48));
        playLp.leftMargin = a.dp(4);
        sources.addView(backingPlayButton, playLp);

        filesButton = button(tr("내 파일", "My files"), () -> openSource(1));
        LinearLayout.LayoutParams filesLp = new LinearLayout.LayoutParams(0, a.dp(48), 0.9f);
        filesLp.leftMargin = a.dp(4);
        sources.addView(filesButton, filesLp);

        onlineButton = button(tr("온라인", "Online"), () -> openSource(2));
        LinearLayout.LayoutParams onlineLp = new LinearLayout.LayoutParams(0, a.dp(48), 0.9f);
        onlineLp.leftMargin = a.dp(4);
        sources.addView(onlineButton, onlineLp);
        backingPanel.addView(sources);

        LinearLayout volume = a.row();
        LinearLayout.LayoutParams volLp = new LinearLayout.LayoutParams(-1, a.dp(32));
        volLp.topMargin = a.dp(2);
        volumeLabel = text("", 11, StudioTheme.MUTED);
        volumeLabel.setGravity(Gravity.CENTER_VERTICAL);
        volume.addView(volumeLabel, new LinearLayout.LayoutParams(-2, a.dp(32)));
        backingVolume = new SeekBar(a);
        backingVolume.setMax(100);
        backingVolume.setProgress(Math.round(a.values[1] * 100));
        backingVolume.setContentDescription(tr("반주 음량", "Backing volume"));
        backingVolume.setProgressTintList(ColorStateList.valueOf(StudioTheme.ACCENT));
        backingVolume.setThumbTintList(ColorStateList.valueOf(StudioTheme.TEXT));
        backingVolume.setProgressBackgroundTintList(ColorStateList.valueOf(0xff272b31));
        backingVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onStartTrackingTouch(SeekBar b) {
                volumeDragging = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar b) {
                volumeDragging = false;
            }

            @Override
            public void onProgressChanged(SeekBar b, int value, boolean user) {
                if (user) {
                    a.set(1, value / 100f);
                    updateVolumeLabel(value);
                }
            }
        });
        volume.addView(backingVolume, new LinearLayout.LayoutParams(0, a.dp(32), 1));
        backingPanel.addView(volume, volLp);

        LinearLayout.LayoutParams backingPanelLp = new LinearLayout.LayoutParams(-1, -2);
        backingPanelLp.bottomMargin = a.dp(5);
        a.page.addView(backingPanel, backingPanelLp);

        // 3. VOCAL EFFECTS (보컬 효과) PANEL — 3x2 Mini-Dial Grid + Fine Control Slider Box
        LinearLayout effects = a.column();
        effects.setPadding(a.dp(8), a.dp(6), a.dp(8), a.dp(6));
        effects.setBackground(StudioTheme.background(a, StudioTheme.SURFACE, 10, StudioTheme.BORDER));

        LinearLayout section = a.row();
        TextView heading = text(tr("보컬 효과", "VOCAL FX"), 13, StudioTheme.TEXT);
        heading.setTypeface(StudioTheme.bold(a));
        heading.setGravity(Gravity.CENTER_VERTICAL);
        section.addView(heading, new LinearLayout.LayoutParams(0, a.dp(48), 1));

        Button reset = button(tr("초기화", "Reset"), this::reset);
        section.addView(reset, new LinearLayout.LayoutParams(a.dp(68), a.dp(48)));

        a.presetChip = button(a.presetName() + " ▾", () -> {
            dial.cancelGesture();
            a.presetPicker();
        });
        a.presetChip.setTextSize(11);
        LinearLayout.LayoutParams presetLp = new LinearLayout.LayoutParams(a.dp(108), a.dp(48));
        presetLp.leftMargin = a.dp(5);
        section.addView(a.presetChip, presetLp);
        effects.addView(section);

        // 3x2 Grid of 6 Mini-Dial Effect Cards
        LinearLayout grid = a.column();
        LinearLayout.LayoutParams gridLp = new LinearLayout.LayoutParams(-1, 0, 1);
        gridLp.topMargin = a.dp(4);
        for (int r = 0; r < 2; r++) {
            LinearLayout gridRow = a.row();
            for (int c = 0; c < 3; c++) {
                int index = r * 3 + c;
                EffectCardButton card = new EffectCardButton(a, index, () -> select(index));
                selectors[index] = card;
                paint(card, index == a.selectedLiveEffect);
                LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(0, -1, 1);
                if (c > 0) cardLp.leftMargin = a.dp(5);
                gridRow.addView(card, cardLp);
            }
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, 0, 1);
            if (r > 0) rowLp.topMargin = a.dp(5);
            grid.addView(gridRow, rowLp);
        }
        effects.addView(grid, gridLp);

        // Fine Control Slider Box (#101318)
        LinearLayout fineBox = a.column();
        fineBox.setPadding(a.dp(9), a.dp(5), a.dp(9), a.dp(4));
        fineBox.setBackground(StudioTheme.background(a, StudioTheme.FINE_BG, 8, StudioTheme.FINE_BORDER));
        LinearLayout fineHeader = a.row();
        effectName = text("", 12, StudioTheme.TEXT);
        effectName.setTypeface(StudioTheme.bold(a));
        fineHeader.addView(effectName, new LinearLayout.LayoutParams(0, -2, 1));
        effectValue = text("", 14, StudioTheme.ACCENT);
        effectValue.setTypeface(StudioTheme.mono(a));
        fineHeader.addView(effectValue);
        fineBox.addView(fineHeader);

        dial = createDial();
        fineBox.addView(dial, new LinearLayout.LayoutParams(-1, a.dp(38)));
        LinearLayout.LayoutParams fineLp = new LinearLayout.LayoutParams(-1, -2);
        fineLp.topMargin = a.dp(5);
        effects.addView(fineBox, fineLp);

        a.page.addView(effects, new LinearLayout.LayoutParams(-1, a.dp(266)));
        a.page.flexible(effects, 250, 1000);

        // 4. FIXED BOTTOM ACTION DOCK
        a.content = a.actionDock;
        LinearLayout actions = a.row();
        a.recButton = button(tr("● 녹음 시작", "● Record"), a::toggleRecord);
        actions.addView(a.recButton, new LinearLayout.LayoutParams(0, a.dp(50), 1.3f));
        video = button(tr("영상", "Video"), () -> a.chooseTab(3));
        LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(0, a.dp(50), 0.9f);
        vp.leftMargin = a.dp(6);
        actions.addView(video, vp);
        a.recordOptions = button("⋯", a::recordingOptions);
        a.recordOptions.setTextSize(20);
        a.recordOptions.setContentDescription(tr("녹음 설정 · 시작 시간 및 다른 앱 소리", "Recording settings · start time and other app audio"));
        LinearLayout.LayoutParams more = new LinearLayout.LayoutParams(a.dp(48), a.dp(50));
        more.leftMargin = a.dp(6);
        actions.addView(a.recordOptions, more);
        a.actionDock.addView(actions);
        a.content = a.page;

        select(a.selectedLiveEffect);
        poll(NativeAudio.stats(), AudioService.active);
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
        boolean currentlyOn = s[21] > .5f || a.values[11] > .5f;
        if (currentlyOn) {
            a.set(11, 0);
            a.toast(tr("내 목소리 듣기를 껐습니다.", "Voice monitoring turned off."));
        } else {
            if (!AudioService.canMonitor(a)) {
                NativeAudio.monitorLevel(.22f);
            }
            a.set(11, 1);
            if (!AudioService.active) {
                a.requestMicToggle();
            } else {
                a.toast(tr("내 목소리 듣기를 켰습니다.", "Voice monitoring turned on."));
            }
        }
        poll(NativeAudio.stats(), AudioService.active);
    }

    void toggleBackingPlay() {
        float[] s = NativeAudio.stats();
        if (a.composer.playing()) {
            a.composer.pause();
            poll(NativeAudio.stats(), AudioService.active);
            return;
        }
        if (s[16] > .5f) {
            NativeAudio.playLocal(false);
            poll(NativeAudio.stats(), AudioService.active);
            return;
        }
        if (!a.selectedFile.isEmpty()) {
            a.ensureMic(4);
            return;
        }
        openSource(1);
    }

    void updateVolumeLabel(int value) {
        if (volumeLabel != null) {
            volumeLabel.setText(tr("반주 ", "Backing ") + value + "%");
        }
    }

    void updateMicVolumeLabel(int value) {
        if (micVolumeLabel != null) {
            micVolumeLabel.setText(tr("마이크 ", "Mic ") + value + "%");
        }
    }

    String compactDevice(String value) {
        if (value.equals(tr("시스템 기본", "System default")) || value.equals(tr("시스템 기본값", "System default"))) {
            return tr("기본", "Default");
        }
        int dot = value.indexOf(" · ");
        if (dot > 0) return value.substring(0, dot);
        int hash = value.indexOf(" #");
        if (hash > 0) return value.substring(0, hash);
        return value;
    }

    void openSource(int source) {
        a.sourceTab = source;
        a.getPreferences(0).edit().putInt("backingSource", source).apply();
        a.chooseTab(1);
    }

    void select(int index) {
        if (dial != null && dial.isTracking()) return;
        a.selectedLiveEffect = Math.max(0, Math.min(5, index));
        bindDial();
        sync();
    }

    void reset() {
        if (dial != null) dial.cancelGesture();
        int[] keys = {0, 2, 3, 4, 5, 6};
        float[] defaults = {1, .27f, 2.2f, .14f, 280, .25f};
        for (int i = 0; i < keys.length; i++) a.set(keys[i], defaults[i]);
        sync();
    }

    SharedEffectDial createDial() {
        return new SharedEffectDial(a, value -> {
            int selected = a.selectedLiveEffect;
            a.set(ids[selected], value);
            sync();
        });
    }

    void bindDial() {
        int selected = a.selectedLiveEffect;
        dial.cancelGesture();
        dial.bind(name(selected), mins[selected], maxs[selected], a.values[ids[selected]], format[selected]);
    }

    void syncDial() {
        if (dial != null) {
            dial.sync(a.values[ids[a.selectedLiveEffect]]);
        }
    }

    void sync() {
        int selected = a.selectedLiveEffect;
        for (int i = 0; i < 6; i++) {
            String label = (i == 2 ? tr("목소리 크기", "Voice") : name(i));
            String value = label + "\n" + format[i].value(a.values[ids[i]]);
            if (!value.contentEquals(selectors[i].getText())) {
                selectors[i].setText(value);
            }
            if (selectors[i].isSelected() != (i == selected)) {
                paint(selectors[i], i == selected);
            }
            selectors[i].invalidate();
        }
        if (effectName != null) effectName.setText(name(selected));
        if (effectValue != null) effectValue.setText(format[selected].value(a.values[ids[selected]]));
        if (!micVolumeDragging && micVolume != null) {
            int mv = Math.round(a.values[0] * 100);
            if (micVolume.getProgress() != mv) micVolume.setProgress(mv);
            updateMicVolumeLabel(mv);
        }
        syncDial();
    }

    void poll(float[] s, boolean running) {
        if (mic == null) return;
        mic.setText(running ? tr("● 마이크 켜짐", "● MIC ON") : tr("● 마이크 대기", "● MIC READY"));
        mic.setTextColor(running ? StudioTheme.ACCENT : StudioTheme.OLIVE);
        if (a.micButton.isSelected() != running) paint(a.micButton, running);

        boolean safetyMuted = s[17] > .5f;
        boolean monitoringOn = !safetyMuted && running && s[21] > .5f;
        route.setText(
                safetyMuted
                        ? tr("내 목소리 듣기 · 보호 음소거", "Voice monitoring · safety mute")
                        : monitoringOn
                        ? tr("내 목소리 듣기 · 켜짐", "Voice monitoring · on")
                        : tr("내 목소리 듣기 · 꺼짐", "Voice monitoring · off")
        );
        route.setTextColor(safetyMuted ? StudioTheme.DANGER : monitoringOn ? StudioTheme.ACCENT : StudioTheme.OLIVE);
        if (monitorToggle != null && monitorToggle.isSelected() != monitoringOn) {
            paint(monitorToggle, monitoringOn);
        }

        if (!volumeDragging) backingVolume.setProgress(Math.round(a.values[1] * 100));
        updateVolumeLabel(backingVolume.getProgress());
        if (!micVolumeDragging && micVolume != null) {
            micVolume.setProgress(Math.round(a.values[0] * 100));
            updateMicVolumeLabel(micVolume.getProgress());
        }

        inputButton.setText(tr("입력", "Input") + "\n" + compactDevice(a.inputName()));
        inputButton.setContentDescription(tr("입력: ", "Input: ") + a.inputName());
        a.outputButton.setText(tr("출력", "Output") + "\n" + compactDevice(a.outputName()));
        a.outputButton.setContentDescription(tr("출력: ", "Output: ") + a.outputName());

        clock.setText(
                RecordingManager.active
                        ? (RecordingManager.paused ? "Ⅱ " : "● ") + a.formatTime(s[8])
                        : AudioService.waiting()
                        ? tr("예약 ", "IN ") + AudioService.remaining() + tr("초", "s")
                        : "00:00"
        );

        double[] cs = a.composer.state();
        boolean backingPlaying = a.composer.playing() || s[16] > .5f;
        if (a.composer.playing()) {
            backing.setText(tr("작곡기 · ", "Composer · ") + tr("마디 ", "bar ") + (1 + (int) (Math.max(0, cs[0]) / MusicTheory.barBeats(a.composer.project.meter))));
        } else if (s[16] > .5f) {
            double[] local = NativeAudio.localPlaybackInfo();
            backing.setText(tr("반주 · ", "Backing · ") + a.formatTime(local[0]) + " / " + a.formatTime(local[1]));
        } else if (!a.selectedFile.isEmpty()) {
            double[] local = NativeAudio.localPlaybackInfo();
            backing.setText(a.selectedFile + (local[1] > 0 ? " · " + a.formatTime(local[0]) + " / " + a.formatTime(local[1]) : ""));
        } else {
            backing.setText(tr("반주 대기", "Backing idle"));
        }
        if (backingPlayButton != null) {
            backingPlayButton.setText(backingPlaying ? "Ⅱ" : "▶");
            if (backingPlayButton.isSelected() != backingPlaying) {
                paint(backingPlayButton, backingPlaying);
            }
        }

        pitch.setText(tr("입력 음 ", "INPUT ") + (s[0] > 0 ? a.noteName((int) s[2]) : "—"));
        float rms = Math.max(0, s[3]);
        level.setText(rms > .00001f ? String.format(Locale.US, "%.0f dB", 20 * Math.log10(rms)) : "— dB");
        meter.setProgress((int) (1000 * Math.max(0, Math.min(1, (20 * Math.log10(Math.max(.00001, rms)) + 60) / 60))));
        sync();
    }

    void transport(boolean busy, boolean waiting, boolean recording) {
        if (waiting || recording) {
            paintDanger(a.recButton);
        } else if (a.recButton.isSelected() != !busy) {
            paint(a.recButton, !busy);
        }
        video.setEnabled(!busy && !waiting && !recording && !a.recordPermissionPending && !a.starting);
    }
}
