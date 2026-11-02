package pro.livemic.studio;

import android.view.*;
import android.widget.*;
import java.util.Locale;

/** Independent recorded vocal layers, separate from MIDI phrases. */
final class HarmonyScreen {
    final MainActivity a;
    final HarmonySession s;
    TextView readout;
    TextView status;
    Button play;
    Button record;
    ProgressBar progress;
    int selected = 0;

    HarmonyScreen(MainActivity a, HarmonySession s) {
        this.a = a;
        this.s = s;
    }

    void refresh() {
        int tab = a.tab, y = a.pageScroll.getScrollY();
        a.showTab();
        a.pageScroll.post(() -> {
            if (a.tab == tab) a.pageScroll.scrollTo(0, y);
        });
    }

    boolean selectionLocked() {
        return s.busy || s.state[4] >= 0 || s.state[5] >= 0 || a.pendingAction == 9;
    }

    String[] scales() {
        return AppLanguage.english(a)
                ? new String[]{"Major", "Natural minor", "Harmonic minor", "Dorian", "Mixolydian"}
                : MusicTheory.SCALES;
    }

    boolean select(int slot) {
        if (selectionLocked()) {
            a.toast(AppLanguage.text(a, "녹음을 마친 뒤 성부를 선택하세요.", "Choose a part after recording."));
            return false;
        }
        selected = slot;
        return true;
    }

    void draw() {
        a.add(a.title(AppLanguage.text(a, "보컬 화음 쌓기", "Build vocal harmonies")));
        a.note(AppLanguage.text(a,
                "실제 목소리로 자동 화음을 만들고 성부를 겹쳐 녹음합니다. 작곡기와 독립된 루프입니다.",
                "Create automatic harmonies with your voice and layer recordings in a loop independent of the composer."));

        LinearLayout card = a.beginCard();
        readout = a.text(AppLanguage.text(a, "입력 — → 화음 —", "Input — → Harmony —"), 22, MainActivity.LIME);
        readout.setTypeface(StudioTheme.mono(a));
        readout.setGravity(Gravity.CENTER);
        readout.setMinHeight(a.dp(72));
        a.add(readout);
        a.note(AppLanguage.text(a, "입력 음 → 자동 화음 · 입력 레벨", "Input note → automatic harmony · input level"));
        a.endCard(card);

        if (!s.ready) {
            a.note(s.busy
                    ? AppLanguage.text(a, "성부를 불러오는 중입니다…", "Loading parts…")
                    : AppLanguage.text(a, "성부를 준비하지 못했습니다.", "Could not prepare parts."));
            return;
        }

        Button mic = StudioTheme.styled(a,
                s.liveOn ? AppLanguage.text(a, "마이크 끄기", "Turn mic off") : AppLanguage.text(a, "마이크 켜기", "Turn mic on"),
                StudioButton.STANDARD,
                () -> {
                    if (s.liveOn) a.requestMicToggle();
                    else a.ensureMic(8);
                });
        ((StudioButton) mic).glyph("mic");
        StudioTheme.selected(mic, s.liveOn);
        a.add(mic);

        Button harmony = StudioTheme.styled(a,
                s.auto ? AppLanguage.text(a, "자동 화음 · 켜짐", "Auto harmony · on") : AppLanguage.text(a, "자동 화음 · 꺼짐", "Auto harmony · off"),
                StudioButton.CHIP,
                () -> {
                    s.auto = !s.auto;
                    s.changed();
                    refresh();
                });
        StudioTheme.selected(harmony, s.auto);
        a.add(harmony);

        a.add(a.button(MusicTheory.ROOTS[s.key] + " · " + scales()[s.scale] + " ▾", () ->
                StudioDialogs.choose(a, AppLanguage.text(a, "키", "Key"), MusicTheory.ROOTS, s.key, k -> {
                    s.key = k;
                    s.changed();
                    StudioDialogs.choose(a, AppLanguage.text(a, "스케일", "Scale"), scales(), s.scale, n -> {
                        s.scale = n;
                        s.changed();
                        refresh();
                    });
                })));

        String[] presets = {
                AppLanguage.text(a, "위 3도", "Third above"),
                AppLanguage.text(a, "위 3도 + 5도", "Third + fifth above"),
                AppLanguage.text(a, "아래 3도 + 위 3도", "Third below + third above"),
                AppLanguage.text(a, "위아래 옥타브", "Octaves above and below")
        };
        a.add(a.button(AppLanguage.text(a, "화음: ", "Harmony: ") + presets[s.preset] + " ▾", () ->
                StudioDialogs.choose(a, AppLanguage.text(a, "자동 화음", "Auto harmony"), presets, s.preset, n -> {
                    s.preset = n;
                    s.changed();
                    refresh();
                })));

        a.add(a.button(AppLanguage.text(a, "목소리 · 화음 믹스 ▾", "Voice · harmony mix ▾"), this::mix));
        a.add(a.button(AppLanguage.text(a, "루프 ", "Loop ") + s.bpm + " BPM · "
                + new String[]{"4/4", "3/4", AppLanguage.text(a, "6/8 · 큰박", "6/8 · dotted beat")}[s.meter]
                + " · " + s.bars + AppLanguage.text(a, "마디 ▾", " bars ▾"), this::loop));

        a.add(a.text(AppLanguage.text(a, "성부 · 4", "Parts · 4"), 18, MainActivity.TEXT));
        for (int i = 0; i < 4; i++) {
            int slot = i;
            LinearLayout old = a.beginCard();
            String state = s.state[5] == i
                    ? AppLanguage.text(a, "녹음 중", "Recording")
                    : s.state[4] == i
                    ? AppLanguage.text(a, "대기 중", "Waiting")
                    : s.files[i] == null
                    ? AppLanguage.text(a, "비어 있음", "Empty")
                    : AppLanguage.text(a, "저장됨", "Saved");
            Button select = a.button(s.names[i] + " · " + state + (s.muted[i] ? AppLanguage.text(a, " · 뮤트", " · muted") : ""), () -> {
                if (select(slot)) refresh();
            });
            select.setMaxLines(2);
            select.setEllipsize(android.text.TextUtils.TruncateAt.END);
            ((StudioButton) select).role(StudioButton.CHIP);
            StudioTheme.selected(select, selected == i);
            a.add(select);

            LinearLayout actions = a.row();
            a.weighted(actions, a.button(s.muted[i] ? AppLanguage.text(a, "듣기", "Unmute") : AppLanguage.text(a, "뮤트", "Mute"), () -> {
                s.muted[slot] = !s.muted[slot];
                s.levels(slot);
                refresh();
            }), 48);
            a.weighted(actions, a.button(AppLanguage.text(a, "볼륨 · 팬", "Volume · pan"), () -> levels(slot)), 48);
            a.weighted(actions, a.button("⋯", () -> menu(slot)), 48);
            a.add(actions);
            a.endCard(old);
        }

        a.add(a.button(AppLanguage.text(a, "전체 믹스 녹음", "Record full mix"), a::toggleRecord));
        a.add(a.button(AppLanguage.text(a, "목소리 악기로 돌아가기 ›", "Back to voice instrument ›"), () -> a.chooseTab(8)));
        dock();
        poll();
    }

    void menu(int i) {
        StudioDialogs.choose(a, s.names[i], new String[]{
                AppLanguage.text(a, "녹음 · 재녹음", "Record · re-record"),
                AppLanguage.text(a, "이전 성부 되돌리기", "Restore previous part"),
                AppLanguage.text(a, "이름 변경", "Rename"),
                AppLanguage.text(a, "성부 삭제", "Delete part")
        }, -1, n -> {
            if (n == 0) {
                if (select(i)) startRecord();
            } else if (n == 1) {
                s.undo(i);
                refresh();
            } else if (n == 2) {
                rename(i);
            } else {
                StudioDialogs.confirmDanger(a, AppLanguage.text(a, "성부 삭제", "Delete part"),
                        AppLanguage.text(a, "저장된 성부를 비울까요? 이전 성부로 되돌릴 수 있습니다.", "Clear this saved part? You can restore the previous part."),
                        AppLanguage.text(a, "삭제", "Delete"), () -> {
                            s.clear(i);
                            refresh();
                        });
            }
        });
    }

    void startRecord() {
        if (selectionLocked()) {
            a.toast(AppLanguage.text(a, "녹음 또는 저장이 끝날 때까지 기다려 주세요.", "Wait until recording or saving finishes."));
            return;
        }
        if (!s.liveOn) {
            a.ensureMic(9);
        } else {
            s.arm(selected);
            refresh();
        }
    }

    void dock() {
        a.actionDock.setVisibility(View.VISIBLE);
        status = a.text("", 12, MainActivity.MUTED);
        status.setTypeface(StudioTheme.mono(a));
        status.setMaxLines(2);
        status.setEllipsize(android.text.TextUtils.TruncateAt.END);
        a.actionDock.addView(status);

        progress = new ProgressBar(a, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(1000);
        progress.setProgressTintList(android.content.res.ColorStateList.valueOf(MainActivity.LIME));
        progress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(StudioTheme.RAISED));
        a.actionDock.addView(progress, new LinearLayout.LayoutParams(-1, a.dp(6)));

        LinearLayout row = a.row();
        play = a.button(s.state[0] > .5 ? AppLanguage.text(a, "Ⅱ 일시정지", "Ⅱ Pause") : AppLanguage.text(a, "▶ 루프 재생", "▶ Play loop"), () -> {
            if (s.state[0] > .5) s.pause();
            else s.play();
            refresh();
        });
        StudioTheme.selected(play, true);
        a.weighted(row, play, 48);
        a.weighted(row, a.button(AppLanguage.text(a, "■ 정지", "■ Stop"), () -> {
            s.stop();
            refresh();
        }), 48);
        a.actionDock.addView(row);

        boolean pending = s.state[4] >= 0 || s.state[5] >= 0;
        record = StudioTheme.styled(a,
                pending ? AppLanguage.text(a, "녹음 취소 · 이전 성부 유지", "Cancel recording · keep previous part") : s.names[selected] + AppLanguage.text(a, " 녹음", " Record"),
                StudioButton.RECORD,
                () -> {
                    if (pending) {
                        s.cancel();
                        refresh();
                    } else {
                        startRecord();
                    }
                });
        record.setMaxLines(2);
        record.setEllipsize(android.text.TextUtils.TruncateAt.END);
        ((StudioButton) record).glyph("record");
        a.actionDock.addView(record, new LinearLayout.LayoutParams(-1, -2));
    }

    void poll() {
        if (play != null) {
            play.setText(s.state[0] > .5 ? AppLanguage.text(a, "Ⅱ 일시정지", "Ⅱ Pause") : AppLanguage.text(a, "▶ 루프 재생", "▶ Play loop"));
        }
        if (readout != null) {
            double[] st = s.state;
            readout.setText(AppLanguage.text(a, "입력 ", "Input ") + note(st[8]) + " → " + note(st[9]) + " / " + note(st[10])
                    + String.format(Locale.US, "\n%.0f dB", 20 * Math.log10(Math.max(.00001, st[11]))));
        }
        if (status != null) {
            double[] st = s.state;
            int beats = s.meter == 0 ? 4 : s.meter == 1 ? 3 : 2;
            double beat = st[1] * s.bpm / 60.0;
            int bar = Math.min(s.bars, 1 + (int) (beat / beats)), inBar = 1 + (int) beat % beats;
            String position = bar + " / " + s.bars + AppLanguage.text(a, "마디 · ", " bars · ") + inBar + AppLanguage.text(a, "박", " beat");
            if (s.busy) {
                status.setText(AppLanguage.text(a, "저장 중… · ", "Saving… · ") + position);
            } else if (st[5] >= 0 && st[5] < 4) {
                status.setText(AppLanguage.text(a, "녹음 중 · ", "Recording · ") + position + "\n" + s.names[(int) st[5]]);
            } else if (st[4] >= 0 && st[4] < 4) {
                status.setText((st[3] > 0
                        ? AppLanguage.text(a, "카운트인 ", "Count-in ") + (int) Math.ceil(st[3]) + AppLanguage.text(a, "박", " beat")
                        : AppLanguage.text(a, "다음 루프에서 녹음 · ", "Recording on next loop · ") + position) + "\n" + s.names[(int) st[4]]);
            } else {
                status.setText(position + (st[0] > .5 ? AppLanguage.text(a, " · 재생 중", " · Playing") : AppLanguage.text(a, " · 대기", " · Ready")));
            }
            if (progress != null) {
                progress.setProgress(st[2] > 0 ? (int) Math.max(0, Math.min(1000, st[1] / st[2] * 1000)) : 0);
            }
        }
    }

    void rename(int i) {
        EditText field = new EditText(a);
        field.setText(s.names[i]);
        field.setSingleLine();
        field.setTextColor(MainActivity.TEXT);
        field.setTypeface(StudioTheme.body(a));
        field.setPadding(a.dp(14), a.dp(12), a.dp(14), a.dp(12));
        field.setBackground(StudioTheme.background(a, StudioTheme.RAISED, 12));
        field.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(40)});
        field.setSelectAllOnFocus(true);
        StudioDialogs.form(a, AppLanguage.text(a, "성부 이름", "Part name"), field, AppLanguage.text(a, "저장", "Save"), () -> {
            String name = field.getText().toString().trim();
            if (!name.isEmpty()) {
                s.names[i] = name;
                s.changed();
                refresh();
            }
        }, null);
    }

    String note(double n) {
        return n < 0 ? "—" : MusicTheory.note((int) n);
    }

    void mix() {
        float[] before = {s.wet, s.dry, s.spread};
        LinearLayout p = a.column();
        TextView explanation = a.text(AppLanguage.text(a, "자동 화음이 켜져 있을 때 적용됩니다.", "Applies when automatic harmony is on."), 14, MainActivity.MUTED);
        explanation.setPadding(0, 0, 0, a.dp(12));
        p.addView(explanation);
        p.addView(new StudioFader(a, AppLanguage.text(a, "화음", "Harmony"), 0, 1, s.wet, v -> Math.round(v * 100) + "%", v -> {
            s.wet = v;
            s.configure();
        }));
        p.addView(new StudioFader(a, AppLanguage.text(a, "원래 목소리", "Original voice"), 0, 1, s.dry, v -> Math.round(v * 100) + "%", v -> {
            s.dry = v;
            s.configure();
        }));
        p.addView(new StudioFader(a, AppLanguage.text(a, "스테레오", "Stereo"), 0, 1, s.spread, v -> Math.round(v * 100) + "%", v -> {
            s.spread = v;
            s.configure();
        }));
        StudioDialogs.form(a, AppLanguage.text(a, "목소리 · 화음 믹스", "Voice · harmony mix"), p, AppLanguage.text(a, "저장", "Save"), () -> {
            s.changed();
            refresh();
        }, () -> {
            s.wet = before[0];
            s.dry = before[1];
            s.spread = before[2];
            s.configure();
            refresh();
        });
    }

    void levels(int i) {
        float[] before = {s.volume[i], s.pan[i]};
        LinearLayout p = a.column();
        p.addView(new StudioFader(a, AppLanguage.text(a, "볼륨", "Volume"), 0, 1, s.volume[i], v -> Math.round(v * 100) + "%", v -> {
            s.volume[i] = v;
            NativeHarmony.layer(i, s.volume[i], s.pan[i], s.muted[i]);
        }));
        p.addView(new StudioFader(a, AppLanguage.text(a, "팬", "Pan"), -1, 1, s.pan[i], v -> String.format(Locale.US, "%+.0f", v * 100), v -> {
            s.pan[i] = v;
            NativeHarmony.layer(i, s.volume[i], s.pan[i], s.muted[i]);
        }));
        StudioDialogs.form(a, s.names[i], p, AppLanguage.text(a, "저장", "Save"), () -> {
            s.levels(i);
            refresh();
        }, () -> {
            s.volume[i] = before[0];
            s.pan[i] = before[1];
            NativeHarmony.layer(i, s.volume[i], s.pan[i], s.muted[i]);
            refresh();
        });
    }

    void loop() {
        if (s.locked()) return;
        for (String f : s.files) {
            if (f != null) {
                a.toast(AppLanguage.text(a, "성부를 비운 뒤 변경하세요.", "Clear the parts before changing this."));
                return;
            }
        }
        int[] v = {s.bpm, s.meter, s.bars};
        LinearLayout p = a.column();
        p.addView(new StudioFader(a, AppLanguage.text(a, "템포", "Tempo"), 40, 240, v[0], n -> Math.round(n) + " BPM", n -> v[0] = Math.round(n)));
        Button meter = a.button(AppLanguage.text(a, "박자: ", "Meter: ")
                + new String[]{"4/4", "3/4", AppLanguage.text(a, "6/8 · 큰박", "6/8 · dotted beat")}[v[1]], () -> {});
        meter.setOnClickListener(x -> StudioDialogs.choose(a, AppLanguage.text(a, "박자", "Meter"),
                new String[]{"4/4", "3/4", AppLanguage.text(a, "6/8 · 큰박", "6/8 · dotted beat")}, v[1], n -> {
                    v[1] = n;
                    meter.setText(AppLanguage.text(a, "박자: ", "Meter: ")
                            + new String[]{"4/4", "3/4", AppLanguage.text(a, "6/8 · 큰박", "6/8 · dotted beat")}[n]);
                }));
        p.addView(meter);
        Button bars = a.button(v[2] + AppLanguage.text(a, "마디", " bars"), () -> {});
        bars.setOnClickListener(x -> {
            v[2] = v[2] == 4 ? 8 : 4;
            bars.setText(v[2] + AppLanguage.text(a, "마디", " bars"));
        });
        p.addView(bars);
        StudioDialogs.form(a, AppLanguage.text(a, "루프 설정", "Loop settings"), p, AppLanguage.text(a, "적용", "Apply"), () -> {
            s.loopSettings(v[0], v[1], v[2]);
            refresh();
        }, null);
    }
}
