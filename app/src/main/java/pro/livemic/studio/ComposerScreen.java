package pro.livemic.studio;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.function.*;

/** Uses the existing native theme, scroll body and fixed action dock. */
final class ComposerScreen {
    final MainActivity a;
    final ComposerSession s;
    TextView readout;
    TextView transportLabel;
    TextView lcdStatus;
    Button play;
    Button phrase;
    Button voicePower;
    int solo = -1;
    String soloPart = "";
    ComposerProject soloProject;
    final List<View> chordCards = new ArrayList<>();
    int highlightedBar = -2;
    ScrollView leftPane;
    ScrollView rightPane;

    /** Bounded panes keep long parts and track lists independently reachable. */
    LinearLayout tabletPanes(String screen) {
        LinearLayout row = a.row();
        row.setGravity(Gravity.TOP);
        leftPane = new ScrollView(a);
        rightPane = new ScrollView(a);
        leftPane.setTag("tablet-" + screen + "-left");
        rightPane.setTag("tablet-" + screen + "-right");
        leftPane.setFillViewport(true);
        rightPane.setFillViewport(true);
        leftPane.setClipToPadding(false);
        rightPane.setClipToPadding(false);
        leftPane.setPadding(0, 0, a.dp(4), a.dp(8));
        rightPane.setPadding(0, 0, a.dp(4), a.dp(8));
        LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, -1, 1);
        LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0, -1, 1);
        right.leftMargin = a.dp(24);
        row.addView(leftPane, left);
        row.addView(rightPane, right);
        a.add(row);
        a.page.flexible(row, 160, 1600);
        LinearLayout leftContent = a.column();
        leftPane.addView(leftContent, new ScrollView.LayoutParams(-1, -2));
        LinearLayout rightContent = a.column();
        rightPane.addView(rightContent, new ScrollView.LayoutParams(-1, -2));
        return leftContent;
    }

    void rightPane() {
        a.content = (LinearLayout) rightPane.getChildAt(0);
    }

    ComposerScreen(MainActivity a, ComposerSession s) {
        this.a = a;
        this.s = s;
    }

    void refresh() {
        int tab = a.tab, y = a.pageScroll.getScrollY();
        int leftY = leftPane == null ? 0 : leftPane.getScrollY();
        int rightY = rightPane == null ? 0 : rightPane.getScrollY();
        a.pageScroll.preserveScroll(y);
        a.showTab();
        a.pageScroll.post(() -> {
            if (a.tab == tab) {
                a.pageScroll.scrollTo(0, y);
                if (leftPane != null) leftPane.scrollTo(0, leftY);
                if (rightPane != null) rightPane.scrollTo(0, rightY);
            }
        });
    }

    void edit(Consumer<ComposerProject> f, boolean structural) {
        s.edit(f, structural);
        refresh();
    }

    boolean current(ComposerProject project, int revision, String part) {
        return s.project == project && s.revision == revision && s.part().id.equals(part);
    }

    void stale() {
        a.toast(tr("편집 내용이 변경되었습니다. 다시 시도해 주세요.", "The editing context changed. Please try again."));
    }

    void choose(String title, String[] items, int selected, IntConsumer action) {
        ComposerProject project = s.project;
        int revision = s.revision;
        String part = s.part().id;
        StudioDialogs.choose(a, title, items, selected, n -> {
            if (!current(project, revision, part)) {
                stale();
                return;
            }
            action.accept(n);
        });
    }

    void form(String title, View panel, String positive, Runnable apply, Runnable cancel) {
        ComposerProject project = s.project;
        int revision = s.revision;
        String part = s.part().id;
        StudioDialogs.form(a, title, panel, positive, () -> {
            if (!current(project, revision, part)) {
                stale();
                return;
            }
            apply.run();
        }, cancel == null ? null : () -> {
            if (current(project, revision, part)) cancel.run();
        });
    }

    void heading(String t) {
        a.add(a.text(t, 18, MainActivity.TEXT));
    }

    String tr(String ko, String en) {
        return a.tr(ko, en);
    }

    String[] scales() {
        return AppLanguage.english(a)
                ? new String[]{"Major", "Natural minor", "Harmonic minor", "Dorian", "Mixolydian"}
                : MusicTheory.SCALES;
    }

    String[] rhythms() {
        return AppLanguage.english(a)
                ? new String[]{"Pop 8-beat", "Pop 16-beat", "Shuffle", "R&B", "Rock", "Bossa nova", "Waltz", "6/8 ballad", "Hip-hop"}
                : MusicTheory.RHYTHMS;
    }

    String[] presets() {
        return AppLanguage.english(a)
                ? new String[]{"Pop I–V–vi–IV", "Ballad I–vi–IV–V", "Canon I–V–vi–iii–IV–I–IV–V", "Jazz ii7–V7–Imaj7", "Minor i–VI–III–VII", "Minor cadence i–VI–iv–V7"}
                : MusicTheory.PRESETS;
    }

    TextView caption(String text) {
        TextView v = a.text(text, 12, MainActivity.MUTED);
        v.setTypeface(StudioTheme.medium(a));
        return v;
    }

    void section(String name, String detail) {
        LinearLayout row = a.row();
        TextView title = a.text(name, 19, MainActivity.TEXT);
        title.setTypeface(StudioTheme.bold(a));
        row.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        if (detail != null) row.addView(caption(detail));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = a.dp(15);
        lp.bottomMargin = a.dp(8);
        a.content.addView(row, lp);
    }

    TextView action(String text, Runnable onClick) {
        TextView v = a.text(text, 13, MainActivity.LIME);
        v.setTypeface(StudioTheme.medium(a));
        v.setPadding(a.dp(4), a.dp(10), a.dp(4), a.dp(10));
        v.setOnClickListener(w -> onClick.run());
        v.setFocusable(true);
        v.setMinHeight(a.dp(48));
        v.setMinWidth(a.dp(48));
        v.setGravity(Gravity.CENTER_VERTICAL);
        return v;
    }

    void stat(LinearLayout row, String label, String value, Runnable click) {
        LinearLayout tile = a.column();
        tile.setPadding(a.dp(10), a.dp(8), a.dp(10), a.dp(8));
        tile.setBackground(StudioTheme.background(a, StudioTheme.RAISED, 10));
        tile.addView(caption(label));
        TextView text = a.text(value + " ▾", 13, MainActivity.TEXT);
        text.setTypeface(StudioTheme.bold(a));
        text.setSingleLine();
        text.setEllipsize(TextUtils.TruncateAt.END);
        tile.addView(text);
        tile.setMinimumHeight(a.dp(52));
        tile.setOnClickListener(v -> click.run());
        tile.setFocusable(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
        if (row.getChildCount() > 0) lp.leftMargin = a.dp(6);
        row.addView(tile, lp);
    }

    static String trackEmoji(TrackConfig t) {
        if (t.drums()) return "🥁";
        switch (t.family()) {
            case 0: return "🎹";
            case 1: return "🔔";
            case 2: return "🎹";
            case 3: return "🎸";
            case 4: return "🎻";
            case 5: case 6: return "🎻";
            case 7: return "🎺";
            case 8: case 9: return "🎷";
            case 10: case 11: case 12: return "🎛️";
            default: return "🎵";
        }
    }

    String chordName(ComposerProject.Chord c) {
        return MusicTheory.ROOTS[c.root] + MusicTheory.QUALITIES[c.quality] + (c.bass >= 0 ? "/" + MusicTheory.ROOTS[c.bass] : "");
    }

    String chordDegree(ComposerProject.Chord c, ComposerProject p) {
        String label = c.label(p);
        int split = label.indexOf(" · ");
        return split < 0 ? label : label.substring(split + 3).replace(" 직접", tr(" 직접", " manual"));
    }

    void draw(boolean voice) {
        leftPane = rightPane = null;
        readout = transportLabel = lcdStatus = null;
        play = phrase = voicePower = null;
        chordCards.clear();
        highlightedBar = -2;
        if (voice) {
            voice();
            return;
        }
        ComposerProject p = s.project;
        ComposerProject.Part part = s.part();
        if (soloProject != p || !soloPart.equals(part.id)) {
            clearSolo();
            soloProject = p;
            soloPart = part.id;
        }

        LinearLayout hero = StudioTheme.card(a);
        hero.setBackground(StudioTheme.background(a, Color.rgb(16, 20, 15), 14));
        hero.setPadding(a.dp(16), a.dp(14), a.dp(16), a.dp(14));
        LinearLayout top = a.row();
        LinearLayout name = a.column();
        name.addView(caption(tr("내 반주 프로젝트", "BACKING PROJECT")));
        TextView projectName = a.text(p.name, 21, MainActivity.TEXT);
        projectName.setTypeface(StudioTheme.bold(a));
        projectName.setSingleLine();
        projectName.setEllipsize(TextUtils.TruncateAt.END);
        name.addView(projectName);
        top.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        top.addView(action(tr("관리 ⋯", "Manage ⋯"), this::projectMenu));
        hero.addView(top);
        TextView summary = caption(tr("작곡 초안 · ", "Composition draft · ") + p.parts.size()
                + tr("개 파트 · ", " parts · ") + p.totalBars() + tr("마디", " bars")
                + " · " + new String[]{"4/4", "3/4", "6/8"}[p.meter]);
        summary.setPadding(0, a.dp(4), 0, a.dp(8));
        hero.addView(summary);

        lcdStatus = a.text("", 13, Color.rgb(225, 228, 196));
        lcdStatus.setTypeface(StudioTheme.bold(a));
        lcdStatus.setBackground(StudioTheme.background(a, Color.rgb(24, 30, 22), 8));
        lcdStatus.setPadding(a.dp(10), a.dp(6), a.dp(10), a.dp(6));
        LinearLayout.LayoutParams lcdLp = new LinearLayout.LayoutParams(-1, -2);
        lcdLp.bottomMargin = a.dp(10);
        hero.addView(lcdStatus, lcdLp);

        LinearLayout stats = a.row();
        stat(stats, tr("키", "KEY"), MusicTheory.ROOTS[p.key], () ->
                choose(tr("키 · 모든 코드와 프레이즈 전조", "Key · transpose chords and phrases"), MusicTheory.ROOTS, p.key, k ->
                        edit(x -> x.transpose(k), false)));
        stat(stats, tr("템포", "TEMPO"), p.bpm + " · " + new String[]{"4/4", "3/4", "6/8"}[p.meter], this::tempo);
        stat(stats, tr("스케일", "SCALE"), scales()[p.scale], () ->
                choose(tr("스케일", "Scale"), scales(), p.scale, k ->
                        edit(x -> x.scale(k), false)));
        hero.addView(stats);

        LinearLayout aiBar = a.row();
        LinearLayout.LayoutParams aiBarLp = new LinearLayout.LayoutParams(-1, -2);
        aiBarLp.topMargin = a.dp(10);
        StudioButton export = StudioTheme.styled(a, tr("AI 내보내기", "AI export"), StudioButton.CHIP, this::exportProject);
        export.setContentDescription(tr("AI 편집용 프로젝트 JSON 내보내기", "Export project JSON for AI editing"));
        StudioTheme.selected(export, true);
        StudioButton aiImport = StudioTheme.styled(a, tr("JSON 불러오기", "Import JSON"), StudioButton.CHIP, () -> a.exchange.showImport());
        aiImport.setContentDescription(tr("수정한 프로젝트 JSON 불러오기", "Import revised project JSON"));
        a.weighted(aiBar, export, 44);
        LinearLayout.LayoutParams expLp = (LinearLayout.LayoutParams) export.getLayoutParams();
        expLp.rightMargin = a.dp(8);
        a.weighted(aiBar, aiImport, 44);
        hero.addView(aiBar, aiBarLp);
        boolean tablet = a.tabletLandscape();
        if (tablet) {
            hero.removeAllViews();
            LinearLayout wideHero = a.row();
            wideHero.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout identity = a.column();
            identity.addView(top);
            identity.addView(summary);
            identity.addView(lcdStatus, lcdLp);
            LinearLayout controls = a.column();
            controls.addView(stats);
            controls.addView(aiBar, aiBarLp);
            wideHero.addView(identity, new LinearLayout.LayoutParams(0, -2, 1));
            LinearLayout.LayoutParams controlsLp = new LinearLayout.LayoutParams(0, -2, 1);
            controlsLp.leftMargin = a.dp(24);
            wideHero.addView(controls, controlsLp);
            hero.addView(wideHero);
        }
        a.add(hero);
        LinearLayout pageContent = a.content;
        if (tablet) a.content = tabletPanes("composer");

        if (!tablet) section(tr("파트", "Parts"), p.parts.size() + " / 8");
        LinearLayout parts = a.row();
        Button previous = a.button("‹", () -> selectPart(s.selectedPart - 1));
        previous.setTextSize(24);
        previous.setContentDescription(tr("이전 파트", "Previous part"));
        previous.setEnabled(!s.capturing && s.selectedPart > 0);
        parts.addView(previous, new LinearLayout.LayoutParams(a.dp(52), a.dp(64)));
        Button currentPart = a.button((tablet ? tr("파트 ", "Part ") : "") + part.label + " · " + part.bars + tr("마디", " bars")
                + "\n" + (s.selectedPart + 1) + " / " + p.parts.size() + tr(" · 전체 파트 ▾", " · All parts ▾"), this::choosePart);
        currentPart.setSingleLine(false);
        currentPart.setMaxLines(2);
        currentPart.setEnabled(!s.capturing);
        parts.addView(currentPart, new LinearLayout.LayoutParams(0, a.dp(64), 1));
        Button next = a.button("›", () -> selectPart(s.selectedPart + 1));
        next.setTextSize(24);
        next.setContentDescription(tr("다음 파트", "Next part"));
        next.setEnabled(!s.capturing && s.selectedPart < p.parts.size() - 1);
        parts.addView(next, new LinearLayout.LayoutParams(a.dp(52), a.dp(64)));
        a.add(parts);

        LinearLayout partActions = a.row();
        partActions.addView(action(tr("⚙️ 파트 편집", "⚙️ Edit part"), this::partMenu), new LinearLayout.LayoutParams(0, -2, 1));
        partActions.addView(action(tr("🎛️ 연주 느낌", "🎛️ Feel"), this::performance));
        a.add(partActions);

        section(tr("코드 진행", "Chord progression"), part.label + " · " + part.bars + tr("마디", " bars"));
        LinearLayout chordActions = a.row();
        chordActions.addView(caption(tr("카드를 눌러 코드를 편집하세요", "Tap a card to edit its chords")), new LinearLayout.LayoutParams(0, -2, 1));
        chordActions.addView(action(tr("프리셋 ▾", "Presets ▾"), () ->
                choose(tr("진행 · 파트 길이에 맞춰 반복", "Progression · repeats to fit the part"), presets(), -1, k ->
                        edit(x -> x.preset(s.part(), k), false))));
        a.add(chordActions);

        for (int b = 0; b < part.bars; b += 2) {
            if (b % 4 == 0 && part.bars > 4) {
                a.add(caption(tr("마디 ", "Bars ") + (b + 1) + "–" + Math.min(b + 4, part.bars)));
            }
            LinearLayout row = a.row();
            for (int c = 0; c < 2 && b + c < part.bars; c++) {
                int bar = b + c;
                LinearLayout card = a.column();
                card.setPadding(a.dp(12), a.dp(9), a.dp(12), a.dp(9));
                card.setBackground(StudioTheme.background(a, StudioTheme.SURFACE, 12));
                card.setMinimumHeight(a.dp(74));
                card.setOnClickListener(v -> chord(bar, 0));
                card.setFocusable(true);
                StringBuilder accessible = new StringBuilder(tr("마디 ", "Bar ") + (bar + 1));
                for (ComposerProject.Chord item : part.chords.get(bar)) {
                    accessible.append(" · ").append(chordName(item)).append(" · ").append(chordDegree(item, p));
                }
                card.setContentDescription(accessible.toString());

                LinearLayout cardHeader = a.row();
                cardHeader.setGravity(Gravity.CENTER_VERTICAL);
                cardHeader.addView(caption(String.format(Locale.US, "BAR %02d", bar + 1)), new LinearLayout.LayoutParams(0, -2, 1));
                if (part.chords.get(bar).size() == 1) {
                    ComposerProject.Chord ch0 = part.chords.get(bar).get(0);
                    TextView degreeBadge = a.text(chordDegree(ch0, p), 11, MainActivity.LIME);
                    degreeBadge.setTypeface(StudioTheme.bold(a));
                    degreeBadge.setPadding(a.dp(6), a.dp(1), a.dp(6), a.dp(1));
                    degreeBadge.setBackground(StudioTheme.background(a, 0x229dff00, 6));
                    cardHeader.addView(degreeBadge);
                }
                card.addView(cardHeader);

                if (part.chords.get(bar).size() == 1) {
                    ComposerProject.Chord ch = part.chords.get(bar).get(0);
                    TextView root = a.text(chordName(ch), 20, MainActivity.TEXT);
                    root.setTypeface(StudioTheme.bold(a));
                    root.setSingleLine();
                    root.setEllipsize(TextUtils.TruncateAt.END);
                    LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-2, -2);
                    rp.topMargin = a.dp(4);
                    card.addView(root, rp);
                } else {
                    LinearLayout splitRow = a.row();
                    splitRow.setGravity(Gravity.CENTER_VERTICAL);
                    LinearLayout.LayoutParams srp = new LinearLayout.LayoutParams(-1, -2);
                    srp.topMargin = a.dp(4);
                    for (int j = 0; j < part.chords.get(bar).size(); j++) {
                        if (j > 0) {
                            TextView slash = a.text(" · ", 14, MainActivity.MUTED);
                            slash.setPadding(a.dp(2), 0, a.dp(2), 0);
                            splitRow.addView(slash);
                        }
                        ComposerProject.Chord ch = part.chords.get(bar).get(j);
                        LinearLayout col = a.column();
                        TextView cName = a.text(chordName(ch), 16, MainActivity.TEXT);
                        cName.setTypeface(StudioTheme.bold(a));
                        cName.setSingleLine();
                        cName.setEllipsize(TextUtils.TruncateAt.END);
                        col.addView(cName);
                        TextView deg = a.text(chordDegree(ch, p), 11, MainActivity.MUTED);
                        col.addView(deg);
                        splitRow.addView(col, new LinearLayout.LayoutParams(0, -2, 1));
                    }
                    card.addView(splitRow, srp);
                }

                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
                if (c > 0) lp.leftMargin = a.dp(8);
                row.addView(card, lp);
                chordCards.add(card);
            }
            a.add(row);
        }

        if (tablet) rightPane();
        section(tr("악기 트랙", "Tracks"), part.tracks.size() + " / 8");
        LinearLayout rhythmRow = a.row();
        rhythmRow.addView(caption(tr("리듬", "RHYTHM")), new LinearLayout.LayoutParams(0, -2, 1));
        rhythmRow.addView(action(rhythms()[part.rhythm] + " ▾", () -> {
            List<Integer> ids = new ArrayList<>();
            List<String> labels = new ArrayList<>();
            for (int i = 0; i < rhythms().length; i++) {
                if (MusicTheory.rhythmFits(i, p.meter)) {
                    ids.add(i);
                    labels.add(rhythms()[i]);
                }
            }
            choose(tr("리듬 · BPM 유지", "Rhythm · keep tempo"), labels.toArray(new String[0]), ids.indexOf(part.rhythm), k ->
                    edit(x -> s.part().rhythm = ids.get(k), false));
        }));
        a.add(rhythmRow);

        for (int i = 0; i < part.tracks.size(); i++) {
            int n = i;
            TrackConfig t = part.tracks.get(i);
            LinearLayout row = a.row();
            row.setPadding(a.dp(10), a.dp(8), a.dp(10), a.dp(8));
            row.setBackground(StudioTheme.background(a, StudioTheme.SURFACE, 12));
            row.setGravity(Gravity.CENTER_VERTICAL);

            TextView icon = a.text(trackEmoji(t), 18, MainActivity.TEXT);
            icon.setGravity(Gravity.CENTER);
            icon.setBackground(StudioTheme.background(a, StudioTheme.RAISED, 10));
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(a.dp(38), a.dp(38));
            ip.rightMargin = a.dp(10);
            row.addView(icon, ip);

            LinearLayout details = a.column();
            TextView nameText = a.text(t.name, 15, t.muted ? MainActivity.MUTED : MainActivity.TEXT);
            nameText.setTypeface(StudioTheme.bold(a));
            nameText.setSingleLine();
            nameText.setEllipsize(TextUtils.TruncateAt.END);
            details.addView(nameText);
            String[] patterns = InstrumentCatalog.patternLabels(a, t);
            VoicePhrase phraseForTrack = null;
            for (VoicePhrase ph : s.part().phrases) {
                if (ph.trackId.equals(t.id) && !ph.notes.isEmpty()) { phraseForTrack = ph; break; }
            }
            String patLabel = (t.voice && phraseForTrack != null)
                    ? tr("🎹 건반 직접 입력 (", "🎹 Custom notes (") + phraseForTrack.notes.size() + tr("음)", " notes)")
                    : patterns[Math.min(t.pattern, patterns.length - 1)];
            details.addView(caption((t.drums() ? tr("드럼 키트 ", "Drum kit ") + t.program : InstrumentCatalog.GM[t.program])
                    + " · " + patLabel + " · " + Math.round(t.volume * 100) + "%"));
            details.setOnClickListener(v -> track(n));
            details.setFocusable(false);
            row.addView(details, new LinearLayout.LayoutParams(0, -2, 1));

            TextView mute = a.text("M", 13, t.muted ? Color.rgb(255, 110, 110) : MainActivity.MUTED);
            mute.setTypeface(StudioTheme.bold(a));
            mute.setGravity(Gravity.CENTER);
            mute.setBackground(StudioTheme.background(a, t.muted ? 0x33ff5555 : StudioTheme.RAISED, 8));
            mute.setOnClickListener(v -> {
                edit(x -> t.muted = !t.muted, false);
                liveTrack(n);
            });
            mute.setFocusable(true);
            mute.setContentDescription(t.muted ? tr("뮤트 해제", "Unmute") : tr("뮤트", "Mute"));
            LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(a.dp(36), a.dp(36));
            mp.rightMargin = a.dp(6);
            row.addView(mute, mp);

            boolean isSolo = (solo == n);
            TextView soloBtn = a.text("S", 13, isSolo ? MainActivity.LIME : MainActivity.MUTED);
            soloBtn.setTypeface(StudioTheme.bold(a));
            soloBtn.setGravity(Gravity.CENTER);
            soloBtn.setBackground(StudioTheme.background(a, isSolo ? 0x339dff00 : StudioTheme.RAISED, 8));
            soloBtn.setOnClickListener(v -> {
                if (s.playing() && liveChannel(t.id) < 0) {
                    a.toast(tr("재생 중인 파트에서 솔로를 사용해 주세요.", "Use solo on the currently playing part."));
                    return;
                }
                solo = solo == n ? -1 : n;
                for (int ch = 0; ch < s.part().tracks.size(); ch++) liveTrack(ch);
                refresh();
            });
            soloBtn.setFocusable(true);
            soloBtn.setContentDescription(isSolo ? tr("솔로 종료", "End solo") : tr("솔로", "Solo"));
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(a.dp(36), a.dp(36));
            sp.rightMargin = a.dp(6);
            row.addView(soloBtn, sp);

            TextView more = a.text("⋯", 16, MainActivity.MUTED);
            more.setGravity(Gravity.CENTER);
            more.setBackground(StudioTheme.background(a, StudioTheme.RAISED, 8));
            more.setOnClickListener(v -> track(n));
            more.setFocusable(true);
            more.setContentDescription(tr("트랙 설정", "Track options"));
            row.addView(more, new LinearLayout.LayoutParams(a.dp(36), a.dp(36)));

            a.add(row);
        }
        a.add(StudioTheme.styled(a, tr("＋ 악기 추가", "＋ Add instrument"), StudioButton.OUTLINE, () -> instrument(-1, false)));

        section(tr("곡 순서", "Song order"), p.totalBars() + " / 256 " + tr("마디", "bars"));
        for (int i = 0; i < p.order.size(); i++) {
            int n = i;
            ComposerProject.Order o = p.order.get(i);
            Button entry = a.button((i + 1) + "  ·  " + p.part(o.part).label + " ×" + o.repeats + "  ⋯", () -> order(n));
            entry.setContentDescription(tr("곡 순서 ", "Song order ") + (i + 1) + ": " + p.part(o.part).label
                    + " ×" + o.repeats + tr(" · 반복 및 순서 편집", " · Edit repeats and order"));
            a.add(entry);
        }
        a.add(StudioTheme.styled(a, tr("＋ 순서에 파트 추가", "＋ Add part to order"), StudioButton.OUTLINE, () -> {
            String[] labels = new String[p.parts.size()];
            for (int i = 0; i < labels.length; i++) labels[i] = p.parts.get(i).label;
            choose(tr("추가할 파트", "Add a part"), labels, -1, k ->
                    edit(x -> x.order.add(new ComposerProject.Order(x.parts.get(k).id, 1)), true));
        }));

        LinearLayout links = a.row();
        links.addView(action(tr("저장된 프로젝트 열기 ›", "Saved projects ›"), () -> {
            a.libraryFilter = 1;
            a.chooseTab(4);
        }), new LinearLayout.LayoutParams(0, -2, 1));
        ((LinearLayout.LayoutParams) links.getChildAt(0).getLayoutParams()).rightMargin = a.dp(8);
        links.addView(action(tr("목소리로 연주 ›", "Play with voice ›"), () -> a.chooseTab(8)),
                new LinearLayout.LayoutParams(0, -2, 1));
        a.add(links);
        a.content = pageContent;
        dock(false);
        if (!s.ready) a.note(tr("오프라인 샘플 음원을 준비하고 있습니다…", "Preparing offline sounds…"));
    }

    ComposerProject newProject() {
        ComposerProject p = new ComposerProject();
        if (AppLanguage.english(a)) {
            p.name = "New backing";
            String[] names = {"Guitar", "Bass", "Drums", "Keyboard"};
            for (ComposerProject.Part part : p.parts) {
                for (int i = 0; i < Math.min(names.length, part.tracks.size()); i++) {
                    part.tracks.get(i).name = names[i];
                }
            }
        }
        return p;
    }

    void exportProject() {
        a.exchange.showExport(s.project);
    }

    void projectMenu() {
        choose(tr("프로젝트", "Project"), new String[]{
                tr("저장", "Save"),
                tr("이름 변경", "Rename"),
                tr("복제", "Duplicate"),
                tr("새 반주", "New backing"),
                tr("실행 취소", "Undo"),
                tr("다시 실행", "Redo"),
                tr("내 반주 열기", "Open saved projects"),
                tr("AI 수정본 불러오기", "Import AI revision")
        }, -1, k -> {
            if (k == 0) s.save();
            else if (k == 1) input(tr("프로젝트 이름", "Project name"), s.project.name, name -> edit(p -> p.name = name, false));
            else if (k == 2) s.duplicate();
            else if (k == 3) s.open(newProject());
            else if (k == 4) s.history(false);
            else if (k == 5) s.history(true);
            else if (k == 6) {
                a.libraryFilter = 1;
                a.chooseTab(4);
            } else {
                a.exchange.showImport();
                return;
            }
            refresh();
        });
    }

    void tempo() {
        final int[] bpm = {s.project.bpm}, meter = {s.project.meter};
        LinearLayout panel = a.column();
        StudioFader fader = new StudioFader(a, tr("템포", "Tempo"), 40, 240, bpm[0], v -> Math.round(v) + " BPM", v -> bpm[0] = Math.round(v));
        panel.addView(fader, new LinearLayout.LayoutParams(-1, -2));
        TextView caption = a.text(tr("박자", "Meter"), 14, MainActivity.MUTED);
        caption.setPadding(0, a.dp(18), 0, a.dp(8));
        panel.addView(caption);
        LinearLayout choices = a.row();
        String[] names = {"4/4", "3/4", "6/8"};
        Button[] buttons = new Button[3];
        for (int i = 0; i < 3; i++) {
            final int n = i;
            buttons[i] = StudioTheme.styled(a, names[i], StudioButton.CHIP, () -> {
                meter[0] = n;
                for (int j = 0; j < 3; j++) StudioTheme.selected(buttons[j], j == n);
            });
            StudioTheme.selected(buttons[i], i == meter[0]);
            a.weighted(choices, buttons[i], 48);
        }
        panel.addView(choices);
        form(tr("템포 · 박자", "Tempo & meter"), panel, tr("적용", "Apply"), () -> edit(p -> {
            p.bpm = bpm[0];
            p.meter(meter[0]);
        }, false), null);
    }

    void performance() {
        ComposerProject.Part part = s.part();
        float[] values = {part.energy, part.humanize};
        boolean[] fill = {part.fill};
        LinearLayout panel = a.column();
        TextView explanation = a.text(part.label + tr("파트에만 적용됩니다.", " part only."), 14, MainActivity.MUTED);
        panel.addView(explanation);
        panel.addView(new StudioFader(a, tr("연주 강도", "Energy"), 0, 1, values[0], v -> Math.round(v * 100) + "%", v -> values[0] = v));
        StudioFader timing = new StudioFader(a, tr("자연스러운 타이밍", "Humanize timing"), 0, 1, values[1], v -> Math.round(v * 100) + "%", v -> values[1] = v);
        LinearLayout.LayoutParams spacing = new LinearLayout.LayoutParams(-1, -2);
        spacing.topMargin = a.dp(16);
        panel.addView(timing, spacing);
        Button toggle = StudioTheme.styled(a, tr("마무리 필인: ", "Ending fill: ") + (fill[0] ? tr("켜짐", "On") : tr("꺼짐", "Off")), StudioButton.CHIP, () -> {});
        StudioTheme.selected(toggle, fill[0]);
        toggle.setOnClickListener(v -> {
            fill[0] = !fill[0];
            toggle.setText(tr("마무리 필인: ", "Ending fill: ") + (fill[0] ? tr("켜짐", "On") : tr("꺼짐", "Off")));
            StudioTheme.selected(toggle, fill[0]);
        });
        LinearLayout.LayoutParams toggleSpacing = new LinearLayout.LayoutParams(-1, -2);
        toggleSpacing.topMargin = a.dp(16);
        panel.addView(toggle, toggleSpacing);
        form(part.label + tr("파트 · 연주 느낌", " · Performance feel"), panel, tr("적용", "Apply"), () -> edit(p -> {
            part.energy = values[0];
            part.humanize = values[1];
            part.fill = fill[0];
        }, false), null);
    }

    void selectPart(int index) {
        if (s.capturing || index < 0 || index >= s.project.parts.size() || index == s.selectedPart) return;
        clearSolo();
        s.selectedPart = index;
        refresh();
    }

    void choosePart() {
        if (s.capturing) return;
        String[] labels = new String[s.project.parts.size()];
        for (int i = 0; i < labels.length; i++) {
            ComposerProject.Part part = s.project.parts.get(i);
            labels[i] = part.label + " · " + part.bars + tr("마디", " bars") + " · " + (i + 1) + " / " + labels.length;
        }
        choose(tr("파트 선택", "Choose part"), labels, s.selectedPart, this::selectPart);
    }

    void partMenu() {
        String partId = s.part().id;
        choose(tr("파트 ", "Part ") + s.part().label, new String[]{
                tr("4마디", "4 bars"),
                tr("8마디", "8 bars"),
                tr("16마디", "16 bars"),
                tr("복제해 새 파트", "Duplicate part"),
                tr("파트 삭제", "Delete part")
        }, -1, k -> {
            int before = s.revision;
            edit(p -> {
                ComposerProject.Part target = p.part(partId);
                if (k < 3) {
                    p.resize(target, new int[]{4, 8, 16}[k]);
                } else if (k == 3) {
                    try {
                        p.duplicate(target);
                    } catch (org.json.JSONException e) {
                        throw new IllegalArgumentException(e);
                    }
                } else {
                    if (p.parts.size() == 1) throw new IllegalArgumentException(tr("파트는 하나 이상 필요합니다.", "At least one part is required."));
                    p.parts.remove(target);
                    p.order.removeIf(o -> o.part.equals(partId));
                    if (p.order.isEmpty()) p.order.add(new ComposerProject.Order(p.parts.get(0).id, 1));
                }
            }, true);
            if (s.revision != before) {
                if (k == 3) s.selectedPart = s.project.parts.size() - 1;
                else if (k == 4) s.selectedPart = 0;
                refresh();
            }
        });
    }

    void chord(int bar, int slot) {
        ComposerProject.Chord ch = s.part().chords.get(bar).get(slot);
        choose(tr("마디 ", "Bar ") + (bar + 1) + " · " + chordName(ch) + " · " + chordDegree(ch, s.project), new String[]{
                tr("근음 변경", "Change root"),
                tr("코드 종류", "Chord quality"),
                tr("분수코드 베이스", "Slash bass"),
                tr("스케일 자동 코드로 복원", "Restore scale chord"),
                s.part().chords.get(bar).size() == 1 ? tr("코드 2개로 분할", "Split into two chords") : tr("첫 코드 편집", "Edit first chord"),
                s.part().chords.get(bar).size() == 1 ? tr("닫기", "Close") : tr("두 번째 코드 편집", "Edit second chord"),
                s.part().chords.get(bar).size() == 1 ? tr("닫기", "Close") : tr("코드 1개로 합치기", "Merge into one chord")
        }, -1, k -> {
            if (k == 0) {
                choose(tr("근음", "Root"), MusicTheory.ROOTS, ch.root, n -> edit(p -> {
                    ch.root = n;
                    ch.automatic = false;
                }, false));
            } else if (k == 1) {
                String[] q = MusicTheory.QUALITIES.clone();
                q[0] = tr("메이저", "Major");
                choose(tr("코드 종류", "Chord quality"), q, ch.quality, n -> edit(p -> {
                    ch.quality = n;
                    ch.automatic = false;
                }, false));
            } else if (k == 2) {
                String[] bass = new String[13];
                bass[0] = tr("분수코드 해제", "Remove slash bass");
                System.arraycopy(MusicTheory.ROOTS, 0, bass, 1, 12);
                choose(tr("베이스음", "Bass note"), bass, ch.bass + 1, n -> edit(p -> {
                    ch.bass = n - 1;
                    if (n > 0) ch.automatic = false;
                }, false));
            } else if (k == 3) {
                choose(tr("스케일 도수", "Scale degree"), new String[]{"I / i", "II / ii", "III / iii", "IV / iv", "V / v", "VI / vi", "VII / vii"}, ch.degree, n -> edit(p -> {
                    ch.degree = n;
                    ch.automatic = true;
                    ch.dominant = false;
                    ch.resolve(p);
                }, false));
            } else if (k == 4) {
                if (s.part().chords.get(bar).size() == 2) {
                    chord(bar, 0);
                } else {
                    edit(p -> {
                        try {
                            s.part().chords.get(bar).add(ComposerProject.Chord.read(ch.json()));
                        } catch (Exception e) {
                            throw new IllegalArgumentException(e);
                        }
                    }, false);
                }
            } else if (k == 5 && s.part().chords.get(bar).size() == 2) {
                chord(bar, 1);
            } else if (k == 6 && s.part().chords.get(bar).size() == 2) {
                edit(p -> s.part().chords.get(bar).remove(1), false);
            }
        });
    }

    void track(int index) {
        TrackConfig t = s.part().tracks.get(index);
        choose(t.name, new String[]{
                tr("음색 선택", "Choose instrument"),
                tr("패턴", "Pattern"),
                tr("🎹 건반으로 직접 입력", "🎹 Keyboard step input"),
                t.muted ? tr("뮤트 해제", "Unmute") : tr("뮤트", "Mute"),
                tr("볼륨 · 팬", "Volume & pan"),
                solo == index ? tr("솔로 종료", "End solo") : tr("솔로 미리듣기", "Solo preview"),
                tr("트랙 이름", "Track name"),
                tr("트랙 삭제", "Delete track")
        }, -1, k -> {
            if (k == 0) {
                instrument(index, false);
            } else if (k == 1) {
                String[] basePatterns = InstrumentCatalog.patternLabels(a, t);
                List<String> pList = new ArrayList<>(Arrays.asList(basePatterns));
                pList.add(tr("🎹 건반으로 직접 입력", "🎹 Keyboard step input"));
                choose(tr("패턴", "Pattern"), pList.toArray(new String[0]), t.voice ? pList.size() - 1 : Math.min(t.pattern, basePatterns.length - 1), n -> {
                    if (n == pList.size() - 1) {
                        openKeyboardInput(index);
                    } else {
                        edit(p -> {
                            t.pattern = n;
                            t.voice = false;
                        }, false);
                    }
                });
            } else if (k == 2) {
                openKeyboardInput(index);
            } else if (k == 3) {
                edit(p -> t.muted = !t.muted, false);
                liveTrack(index);
            } else if (k == 4) {
                trackLevels(index);
            } else if (k == 5) {
                if (s.playing() && liveChannel(t.id) < 0) {
                    a.toast(tr("재생 중인 파트에서 솔로를 사용해 주세요.", "Use solo on the currently playing part."));
                    return;
                }
                solo = solo == index ? -1 : index;
                for (int ch = 0; ch < s.part().tracks.size(); ch++) liveTrack(ch);
            } else if (k == 6) {
                input(tr("트랙 이름", "Track name"), t.name, name -> edit(p -> t.name = name, false));
            } else {
                clearSolo();
                edit(p -> {
                    String id = t.id;
                    for (ComposerProject.Part part : p.parts) {
                        part.tracks.removeIf(tr -> tr.id.equals(id));
                        part.phrases.removeIf(ph -> ph.trackId.equals(id));
                    }
                }, true);
            }
        });
    }

    int liveChannel(String trackId) {
        ComposerProject.Part playing = s.part();
        double[] state = s.state();
        if (s.playing()) {
            int part = (int) state[2];
            if (part < 0 || part >= s.project.parts.size()) return -1;
            playing = s.project.parts.get(part);
            if (!playing.id.equals(s.part().id)) return -1;
        }
        for (int i = 0; i < playing.tracks.size(); i++) {
            if (playing.tracks.get(i).id.equals(trackId)) return i;
        }
        return -1;
    }

    void liveTrack(int index) {
        if (index < 0 || index >= s.part().tracks.size()) return;
        TrackConfig t = s.part().tracks.get(index);
        int channel = liveChannel(t.id);
        if (channel >= 0) NativeComposer.track(channel, t.volume, t.pan, t.muted, solo == index);
    }

    void clearSolo() {
        solo = -1;
        NativeComposer.clearSolo();
    }

    void trackLevels(int index) {
        TrackConfig t = s.part().tracks.get(index);
        float[] values = {t.volume, t.pan};
        LinearLayout panel = a.column();
        ComposerProject context = s.project;
        int revision = s.revision;
        String partId = s.part().id;
        Runnable preview = () -> {
            if (current(context, revision, partId)) {
                int channel = liveChannel(t.id);
                if (channel >= 0) NativeComposer.track(channel, values[0], values[1], t.muted, solo == index);
            }
        };
        StudioFader volume = new StudioFader(a, tr("볼륨", "Volume"), 0, 1, values[0], v -> Math.round(v * 100) + "%", v -> {
            values[0] = v;
            preview.run();
        });
        StudioFader pan = new StudioFader(a, tr("팬", "Pan"), -1, 1, values[1], v -> {
            int n = Math.round(v * 100);
            return n == 0 ? "CENTER" : n < 0 ? "L " + (-n) : "R " + n;
        }, v -> {
            values[1] = v;
            preview.run();
        });
        panel.addView(volume, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = a.dp(18);
        panel.addView(pan, lp);
        form(t.name + tr(" · 볼륨과 팬", " · Volume & pan"), panel, tr("저장", "Save"), () -> edit(x -> {
            t.volume = values[0];
            t.pan = values[1];
        }, false), () -> liveTrack(index));
    }

    void order(int index) {
        choose(tr("곡 순서", "Song order"), new String[]{
                tr("반복 횟수", "Repeats"),
                tr("앞으로", "Move earlier"),
                tr("뒤로", "Move later"),
                tr("삭제", "Delete")
        }, -1, k -> {
            if (k == 0) {
                String[] r = new String[16];
                for (int i = 0; i < 16; i++) r[i] = "×" + (i + 1);
                choose(tr("반복", "Repeats"), r, s.project.order.get(index).repeats - 1, n -> edit(p -> p.order.get(index).repeats = n + 1, true));
            } else {
                edit(p -> {
                    if (k == 1 && index > 0) Collections.swap(p.order, index, index - 1);
                    else if (k == 2 && index < p.order.size() - 1) Collections.swap(p.order, index, index + 1);
                    else if (k == 3) {
                        if (p.order.size() == 1) throw new IllegalArgumentException(tr("순서는 하나 이상 필요합니다.", "At least one order entry is required."));
                        p.order.remove(index);
                    }
                }, true);
            }
        });
    }

    void voice() {
        VoiceInstrumentSettings v = s.project.voice;
        a.add(a.title(tr("목소리로 연주", "Play with voice")));
        boolean tablet = a.tabletLandscape();
        LinearLayout pageContent = a.content;
        if (tablet) a.content = tabletPanes("voice");
        LinearLayout old = a.beginCard();
        readout = a.text(tr("목소리 — → 악기 —", "Voice — → Instrument —"), 24, MainActivity.LIME);
        readout.setGravity(Gravity.CENTER);
        readout.setMinHeight(a.dp(tablet ? 260 : 100));
        if (tablet) readout.setTextSize(32);
        a.add(readout);
        a.note(tr("입력 음 → 악기 출력 음", "Input note → instrument note"));
        a.endCard(old);
        if (tablet) rightPane();

        a.add(a.button(tr("악기: ", "Instrument: ") + InstrumentCatalog.GM[v.program] + " ▾", () -> instrument(-1, true)));
        a.add(a.button(tr("연주 방식: ", "Playing mode: ") + (v.mode == 0 ? tr("멜로디", "Melody") : tr("코드", "Chord")) + " ▾", () ->
                choose(tr("목소리 연주", "Voice performance"), new String[]{
                        tr("멜로디 · 한 음씩", "Melody · one note at a time"),
                        tr("코드 · 부른 근음으로 연주", "Chord · sing the root")
                }, v.mode, n -> edit(p -> p.voice.mode = n, false))));
        a.add(a.button(tr("파트 ", "Part ") + s.part().label + " · " + s.part().bars + tr("마디 ▾", " bars ▾"), () -> {
            String[] labels = new String[s.project.parts.size()];
            for (int i = 0; i < labels.length; i++) labels[i] = s.project.parts.get(i).label;
            choose(tr("기록할 파트", "Part to record"), labels, s.selectedPart, n -> {
                if (!s.capturing) {
                    clearSolo();
                    s.selectedPart = n;
                    refresh();
                }
            });
        }));

        voicePower = a.button(s.voiceOn ? tr("목소리 악기 끄기", "Turn voice instrument off") : tr("마이크로 연주 켜기", "Play with microphone"), () -> {
            if (s.voiceOn) {
                s.voice(false);
                refresh();
            } else if (s.ready) {
                a.ensureMic(7);
            } else {
                a.toast(tr("음원을 준비하고 있습니다.", "Preparing sounds."));
            }
        });
        ((StudioButton) voicePower).glyph("mic");
        StudioTheme.selected(voicePower, s.voiceOn);
        a.add(voicePower);

        a.add(a.button(tr("보컬 화음 쌓기 ›", "Build vocal harmonies ›"), () -> a.chooseTab(9)));
        a.add(a.button(tr("연주 세부 설정 ▾", "Performance settings ▾"), this::voiceSettings));
        a.add(a.button(tr("작곡기로 돌아가기 ›", "Back to composer ›"), () -> a.chooseTab(7)));
        a.note(tr("단선율 입력을 사용합니다. 스케일 맞춤과 원래 목소리 섞기는 기본 꺼짐입니다.", "Uses monophonic input. Scale snap and mixing the original voice are off by default."));
        a.content = pageContent;
        dock(true);
        poll();
    }

    void voiceSettings() {
        VoiceInstrumentSettings v = s.project.voice;
        String[] opts = {
                tr("반응: ", "Response: ") + (v.response == 0 ? tr("빠른 반응", "Fast") : tr("안정 우선", "Stable")),
                tr("자동 음역: ", "Auto range: ") + (v.autoRange ? tr("켜짐", "On") : tr("꺼짐", "Off")),
                tr("옥타브: ", "Octave: ") + v.octave,
                tr("스케일 맞춤: ", "Scale snap: ") + (v.snap ? tr("켜짐", "On") : tr("꺼짐", "Off")),
                tr("코드 종류: ", "Chord quality: ") + (v.quality < 0 ? tr("스케일 자동", "Auto from scale") : MusicTheory.QUALITIES[v.quality]),
                tr("코드 발음: ", "Chord articulation: ") + (v.rhythm ? tr("현재 리듬", "Current rhythm") : tr("한 번 연주", "Play once")),
                tr("원래 목소리 섞기: ", "Mix original voice: ") + (v.mixVoice ? tr("켜짐", "On") : tr("꺼짐", "Off")),
                tr("양자화: ", "Quantize: ") + (v.quantize == 0 ? "OFF" : "1/" + v.quantize),
                tr("입력 게이트 · 신뢰도", "Input gate & confidence")
        };
        choose(tr("연주 설정", "Performance settings"), opts, -1, k -> {
            if (k == 0) edit(p -> v.response = 1 - v.response, false);
            else if (k == 1) edit(p -> v.autoRange = !v.autoRange, false);
            else if (k == 2) choose(tr("수동 옥타브", "Manual octave"), new String[]{"−3", "−2", "−1", "0", "+1", "+2", "+3"}, v.octave + 3, n -> edit(p -> v.octave = n - 3, false));
            else if (k == 3) edit(p -> v.snap = !v.snap, false);
            else if (k == 4) {
                String[] q = new String[12];
                q[0] = tr("스케일 자동", "Auto from scale");
                System.arraycopy(MusicTheory.QUALITIES, 0, q, 1, 11);
                q[1] = tr("메이저", "Major");
                choose(tr("코드 종류", "Chord quality"), q, v.quality + 1, n -> edit(p -> v.quality = n - 1, false));
            } else if (k == 5) edit(p -> v.rhythm = !v.rhythm, false);
            else if (k == 6) edit(p -> v.mixVoice = !v.mixVoice, false);
            else if (k == 7) choose(tr("기록 양자화", "Recording quantization"), new String[]{"OFF", "1/8", "1/16"}, v.quantize == 0 ? 0 : v.quantize == 8 ? 1 : 2, n -> edit(p -> v.quantize = new int[]{0, 8, 16}[n], false));
            else choose(tr("입력 검출", "Input detection"), new String[]{
                    tr("기본 · 신뢰도 0.85 / 게이트 0.007", "Default · confidence 0.85 / gate 0.007"),
                    tr("작은 목소리 · 0.85 / 0.004", "Quiet voice · 0.85 / 0.004"),
                    tr("잡음 억제 · 0.90 / 0.012", "Noise reduction · 0.90 / 0.012")
            }, -1, n -> edit(p -> {
                v.confidence = n == 2 ? .9f : .85f;
                v.gate = n == 0 ? .007f : n == 1 ? .004f : .012f;
            }, false));
        });
    }

    void dock(boolean voice) {
        a.actionDock.setVisibility(View.VISIBLE);
        transportLabel = a.text("", 12, MainActivity.MUTED);
        a.actionDock.addView(transportLabel);
        LinearLayout row = a.row();
        play = a.button(s.playing() ? tr("일시정지", "Pause") : tr("재생", "Play"), () -> {
            if (s.playing()) s.pause();
            else s.play();
        });
        ((StudioButton) play).glyph(s.playing() ? "pause" : "play");
        StudioTheme.selected(play, true);
        a.weighted(row, play, 48);
        a.weighted(row, a.button(tr("■ 정지", "■ Stop"), s::stop), 48);
        a.actionDock.addView(row);
        if (voice) {
            phrase = a.button(s.capturing ? tr("취소 · 이전 프레이즈 유지", "Cancel · keep previous phrase") : tr("프레이즈 기록", "Record phrase"), () -> {
                s.recordPhrase();
                refresh();
            });
            ((StudioButton) phrase).role(StudioButton.RECORD).glyph("record");
            a.actionDock.addView(phrase, new LinearLayout.LayoutParams(-1, -2));
        }
        poll();
    }

    void poll() {
        double[] st = s.state();
        int active = s.playing() && st[0] >= st[3] && (int) st[2] == s.selectedPart
                ? (int) ((st[0] - st[3]) / MusicTheory.barBeats(s.project.meter))
                : -1;
        if (active < 0 || active >= chordCards.size()) active = -1;
        if (active != highlightedBar) {
            for (int i = 0; i < chordCards.size(); i++) {
                android.graphics.drawable.GradientDrawable bg = StudioTheme.background(a, StudioTheme.SURFACE, 12);
                if (i == active) bg.setStroke(a.dp(2), MainActivity.LIME);
                chordCards.get(i).setBackground(bg);
            }
            highlightedBar = active;
        }
        if (lcdStatus != null) {
            ComposerProject.Part displayed = s.part();
            int bar = 0, slot = 0;
            int playingPart = (int) st[2];
            if (s.playing() && playingPart >= 0 && playingPart < s.project.parts.size()) {
                displayed = s.project.parts.get(playingPart);
                double elapsed = Math.max(0, st[0] - st[3]);
                double beats = MusicTheory.barBeats(s.project.meter);
                bar = Math.min(displayed.bars - 1, (int) (elapsed / beats));
                if (elapsed % beats >= MusicTheory.split(s.project.meter)) slot = 1;
            }
            String chord = "—";
            if (bar >= 0 && bar < displayed.chords.size() && !displayed.chords.get(bar).isEmpty()) {
                List<ComposerProject.Chord> chords = displayed.chords.get(bar);
                chord = chordName(chords.get(Math.min(slot, chords.size() - 1)));
            }
            lcdStatus.setText(displayed.label + " · " + tr("마디 ", "Bar ") + (bar + 1) + " · " + chord
                    + (s.playing() ? tr(" · 재생 중", " · Playing") : tr(" · 정지", " · Stopped")));
        }
        if (transportLabel != null) {
            String status = s.capturing
                    ? (st[8] == 1 ? tr("카운트인 / 다음 마디 대기", "Count-in / waiting for next bar") : tr("프레이즈 기록 중 · 파트 끝에서 저장", "Recording phrase · saves at end of part"))
                    : st[0] < 0
                    ? tr("1마디 카운트인", "One-bar count-in")
                    : tr("마디 ", "Bar ") + (1 + (int) (Math.max(0, st[0]) / MusicTheory.barBeats(s.project.meter)))
                            + " · " + s.project.bpm + " BPM" + (s.playing() ? tr(" · 재생 중", " · Playing") : "");
            transportLabel.setText(status);
        }
        if (play != null) {
            play.setText(s.playing() ? tr("일시정지", "Pause") : tr("재생", "Play"));
            ((StudioButton) play).glyph(s.playing() ? "pause" : "play");
        }
        if (readout != null) {
            readout.setText(tr("목소리 ", "Voice ") + (st[5] >= 0 ? MusicTheory.note((int) st[5]) : "—")
                    + "\n→ " + InstrumentCatalog.GM[s.project.voice.program] + " " + (st[6] >= 0 ? MusicTheory.note((int) st[6]) : "—")
                    + String.format(Locale.US, tr("\n입력 %.0f dB", "\nInput %.0f dB"), 20 * Math.log10(Math.max(.00001, st[7]))));
        }
    }

    void instrumentRow(LinearLayout list, String label, int bank, int program,
                       Set<String> favorites, Runnable[] fill, Runnable select) {
        String id = InstrumentFavorites.key(bank, program);
        boolean favorite = favorites.contains(id);
        LinearLayout row = a.row();
        row.setGravity(Gravity.CENTER_VERTICAL);
        String primary, secondary;
        if (bank == 128) {
            int divider = label.lastIndexOf(" · ");
            primary = divider < 0 ? label : label.substring(0, divider);
            secondary = tr("드럼 키트", "Drum kit");
        } else {
            primary = AppLanguage.english(a) ? InstrumentCatalog.GM[program] : InstrumentCatalog.KO[program];
            secondary = AppLanguage.english(a) ? InstrumentCatalog.categories(a)[program / 8] : InstrumentCatalog.GM[program];
        }
        SpannableString styled = new SpannableString(primary + "\n" + secondary);
        styled.setSpan(new android.text.style.RelativeSizeSpan(.85f), primary.length() + 1, styled.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        styled.setSpan(new android.text.style.ForegroundColorSpan(MainActivity.MUTED), primary.length() + 1, styled.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        Button item = a.button(label, select);
        item.setText(styled);
        item.setContentDescription(label);
        item.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        item.setPadding(a.dp(12), a.dp(8), a.dp(8), a.dp(8));
        item.setMinHeight(a.dp(56));
        row.addView(item, new LinearLayout.LayoutParams(0, -2, 1));
        Button star = a.button(favorite ? "★" : "☆", () -> {
            if (favorites.contains(id)) favorites.remove(id);
            else favorites.add(id);
            InstrumentFavorites.save(a.getPreferences(0), favorites);
            fill[0].run();
        });
        star.setTextSize(20);
        star.setTextColor(favorite ? StudioTheme.ACCENT : MainActivity.MUTED);
        star.setContentDescription((favorite ? tr("즐겨찾기에서 제거: ", "Remove favorite: ")
                : tr("즐겨찾기에 추가: ", "Add favorite: ")) + label);
        row.addView(star, new LinearLayout.LayoutParams(a.dp(52), a.dp(52)));
        list.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }

    void instrument(int track, boolean voice) {
        ComposerProject context = s.project;
        int revision = s.revision;
        String partId = s.part().id;
        Dialog dialog = new Dialog(a);
        LinearLayout panel = a.column();
        panel.setPadding(a.dp(16), a.dp(12), a.dp(16), a.dp(12));
        panel.setBackground(StudioTheme.background(a, 0xff0d0f12, 20, 0xff353b44));
        EditText search = new EditText(a);
        search.setTextColor(MainActivity.TEXT);
        search.setHintTextColor(MainActivity.MUTED);
        search.setHint(tr("악기 · 카테고리 검색", "Search instruments or categories"));
        search.setSingleLine();
        search.setTypeface(StudioTheme.body(a));
        search.setPadding(a.dp(14), a.dp(12), a.dp(14), a.dp(12));
        search.setBackground(StudioTheme.background(a, StudioTheme.RAISED, 12));
        panel.addView(search);
        final int[] category = {0};
        List<String> cats = new ArrayList<>(Arrays.asList(
                tr("대표 24 + 드럼", "Featured 24 + Drums"),
                tr("🎹 건반 / 피아노", "🎹 Piano & Keyboards"),
                tr("🎸 기타 / 베이스", "🎸 Guitar & Bass"),
                tr("🥁 드럼 키트", "🥁 Drum kits"),
                tr("🎻 스트링 / 브라스", "🎻 Strings & Brass"),
                tr("🎛️ 신스 / FX", "🎛️ Synth & FX"),
                tr("전체 GM 128", "All GM 128"),
                tr("★ 즐겨찾기", "★ Favorites")));
        // Keep stable category IDs when the voice picker omits the drum category.
        List<Integer> categoryIds = new ArrayList<>(Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7));
        if (voice) {
            cats.remove(3);
            categoryIds.remove(Integer.valueOf(3));
            cats.set(0, tr("대표 24음색", "Featured 24"));
        }
        Button categoryButton = a.button(tr("카테고리: ", "Category: ") + cats.get(0) + " ▾", () ->
                choose(tr("악기 카테고리", "Instrument category"), cats.toArray(new String[0]), category[0], n -> category[0] = n));
        panel.addView(categoryButton);
        ScrollView scroll = new ScrollView(a);
        LinearLayout list = a.column();
        scroll.addView(list);
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        panel.addView(a.button(tr("닫기", "Close"), dialog::dismiss));
        Set<String> favorites = InstrumentFavorites.read(a.getPreferences(0));
        Runnable[] fill = new Runnable[1];
        fill[0] = () -> {
            list.removeAllViews();
            String query = search.getText().toString().toLowerCase(Locale.ROOT);
            int cat = categoryIds.get(category[0]);
            if (!voice && (cat == 0 || cat == 3 || cat == 7)) {
                String[] kits = NativeComposer.drumKits();
                String featuredKit = kits.length == 0 ? null : kits[0];
                for (String kit : kits) if (kit.startsWith("0 · ")) featuredKit = kit;
                for (String kit : kits) {
                    int program = Integer.parseInt(kit.split(" · ", 2)[0]);
                    if (cat == 0 && !kit.equals(featuredKit)) continue;
                    if (cat == 7 && !favorites.contains(InstrumentFavorites.key(128, program))) continue;
                    if (!query.isEmpty() && !(kit + " " + tr("드럼 키트", "Drum kits")).toLowerCase(Locale.ROOT).contains(query)) continue;
                    instrumentRow(list, kit + " · " + tr("드럼 키트", "Drum kit"), 128, program, favorites, fill, () -> {
                        if (current(context, revision, partId)) selectInstrument(track, false, program, 128);
                        else stale();
                        dialog.dismiss();
                    });
                }
            }
            if (cat == 3) return;
            for (int n = 0; n < 128; n++) {
                int program = n;
                if (voice && n >= 112) continue;
                boolean featured = false;
                for (int f : InstrumentCatalog.FEATURED) if (f == n) featured = true;
                int fam = n / 8;
                boolean matchCat = true;
                if (cat == 0) matchCat = featured;
                else if (cat == 1) matchCat = (fam == 0 || fam == 2);
                else if (cat == 2) matchCat = (fam == 3 || fam == 4);
                else if (cat == 4) matchCat = (fam >= 5 && fam <= 9);
                else if (cat == 5) matchCat = (fam >= 10);
                else if (cat == 6) matchCat = true;
                else if (cat == 7) matchCat = favorites.contains(InstrumentFavorites.key(0, n));
                if (!matchCat) continue;
                String label = InstrumentCatalog.displayName(a, n), family = InstrumentCatalog.categories(a)[n / 8];
                if (!query.isEmpty() && !(label + " " + family + " " + InstrumentCatalog.GM[n]).toLowerCase(Locale.ROOT).contains(query)) continue;

                instrumentRow(list, label + " · " + family, 0, program, favorites, fill, () -> {
                    if (current(context, revision, partId)) selectInstrument(track, voice, program, 0);
                    else stale();
                    dialog.dismiss();
                });
            }
        };
        categoryButton.setOnClickListener(v -> choose(tr("악기 카테고리", "Instrument category"), cats.toArray(new String[0]), category[0], n -> {
            category[0] = n;
            categoryButton.setText(tr("카테고리: ", "Category: ") + cats.get(n) + " ▾");
            if (fill[0] != null) fill[0].run();
        }));
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (fill[0] != null) fill[0].run();
            }
            public void afterTextChanged(Editable e) {}
        });
        if (fill[0] != null) fill[0].run();
        dialog.setContentView(panel);
        dialog.show();
        StudioDialogs.track(a, dialog);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams params = window.getAttributes();
            params.dimAmount = 0.82f;
            window.setAttributes(params);
            window.setLayout(Math.min(a.root.getWidth(), a.dp(a.tabletLandscape() ? 760 : 520)), Math.max(a.dp(250), (int) (a.root.getHeight() * .82f)));
        }
    }

    void selectInstrument(int index, boolean voice, int program, int bank) {
        edit(p -> {
            if (voice) {
                p.voice.program = program;
                p.voice.autoRange = program / 8 == 4;
            } else if (index < 0) {
                TrackConfig t = new TrackConfig(bank == 128 ? tr("드럼", "Drums") : InstrumentCatalog.GM[program], program, false);
                t.bank = bank;
                p.addTrack(t);
            } else {
                TrackConfig t = s.part().tracks.get(index);
                t.program = program;
                t.bank = bank;
                t.pattern = 0;
            }
        }, index < 0 && !voice);
    }

    void input(String title, String value, Consumer<String> action) {
        EditText field = new EditText(a);
        field.setText(value);
        field.setSingleLine();
        field.setTextColor(MainActivity.TEXT);
        field.setHintTextColor(MainActivity.MUTED);
        field.setTypeface(StudioTheme.body(a));
        field.setPadding(a.dp(14), a.dp(12), a.dp(14), a.dp(12));
        field.setBackground(StudioTheme.background(a, StudioTheme.RAISED, 12));
        field.setSelectAllOnFocus(true);
        form(title, field, tr("저장", "Save"), () -> {
            String name = field.getText().toString().trim();
            if (!name.isEmpty()) action.accept(name);
        }, null);
    }

    void projects() {
        a.add(a.title(tr("내 반주", "Saved projects")));
        a.add(a.button(tr("＋ 새 프로젝트", "＋ New project"), () -> {
            s.open(newProject());
            a.chooseTab(7);
        }));
        for (ComposerProject p : s.store.list()) {
            a.add(a.button(p.name + "\n" + MusicTheory.ROOTS[p.key] + " · " + p.bpm + " BPM · " + p.totalBars() + tr("마디", " bars"), () -> {
                s.open(p);
                a.chooseTab(7);
            }));
            a.add(a.button(tr("프로젝트 ⋯", "Project ⋯"), () ->
                    choose(p.name, new String[]{tr("이름 변경", "Rename"), tr("복제", "Duplicate"), tr("삭제", "Delete")}, -1, k -> {
                        if (k == 0) {
                            input(tr("이름 변경", "Rename"), p.name, name -> {
                                p.name = name;
                                saveProject(p);
                            });
                        } else if (k == 1) {
                            p.id = UUID.randomUUID().toString();
                            p.name += tr(" 복제", " copy");
                            saveProject(p);
                        } else {
                            StudioDialogs.confirmDanger(a, tr("반주 삭제", "Delete backing"), p.name + tr(" 프로젝트를 삭제할까요?", " · Delete this project?"), tr("삭제", "Delete"), () ->
                                    s.worker.execute(() -> {
                                        s.store.delete(p.id);
                                        a.runOnUiThread(this::refresh);
                                    }));
                        }
                    })));
        }
    }

    void saveProject(ComposerProject p) {
        s.worker.execute(() -> {
            try {
                s.store.write(p, false);
                a.runOnUiThread(this::refresh);
            } catch (Exception e) {
                a.runOnUiThread(() -> a.toast(e.getMessage()));
            }
        });
    }

    void openKeyboardInput(int trackIndex) {
        TrackConfig t = s.part().tracks.get(trackIndex);
        ComposerProject.Part part = s.part();
        ComposerProject project = s.project;
        int revision = s.revision;
        String partId = part.id;

        VoicePhrase existingPhrase = null;
        for (VoicePhrase ph : part.phrases) {
            if (ph.trackId.equals(t.id)) { existingPhrase = ph; break; }
        }
        VoicePhrase tempPhrase = null;
        if (existingPhrase != null) {
            try {
                tempPhrase = VoicePhrase.read(existingPhrase.json());
            } catch (Exception e) {
                tempPhrase = new VoicePhrase();
                tempPhrase.trackId = t.id;
            }
        } else {
            tempPhrase = new VoicePhrase();
            tempPhrase.trackId = t.id;
        }
        final VoicePhrase workingPhrase = tempPhrase;

        final int[] curBar = {0};
        final double[] curBeat = {0.0};
        final double[] stepLen = {1.0};
        final int[] baseOctave = {4};

        Dialog dialog = new Dialog(a);
        LinearLayout root = a.column();
        root.setPadding(a.dp(16), a.dp(12), a.dp(16), a.dp(12));
        root.setBackground(StudioTheme.background(a, 0xff0f1418, 16, 0xff354048));

        LinearLayout header = a.row();
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView titleText = a.text(t.name + tr(" · 건반 입력", " · Keyboard Rec"), 17, MainActivity.TEXT);
        titleText.setTypeface(StudioTheme.bold(a));
        header.addView(titleText, new LinearLayout.LayoutParams(0, -2, 1));
        header.addView(action(tr("✕ 닫기", "✕ Close"), dialog::dismiss));
        root.addView(header);

        LinearLayout hud = a.column();
        hud.setPadding(a.dp(12), a.dp(8), a.dp(12), a.dp(8));
        hud.setBackground(StudioTheme.background(a, 0xff162026, 10));
        
        TextView statusText = a.text("", 14, Color.rgb(225, 228, 196));
        statusText.setTypeface(StudioTheme.bold(a));
        hud.addView(statusText);

        TextView noteListText = a.text("", 12, MainActivity.MUTED);
        noteListText.setSingleLine();
        noteListText.setEllipsize(TextUtils.TruncateAt.START);
        hud.addView(noteListText);
        root.addView(hud);

        LinearLayout toolbar1 = a.row();
        toolbar1.setGravity(Gravity.CENTER_VERTICAL);
        toolbar1.setPadding(0, a.dp(8), 0, a.dp(6));

        Button prevBar = StudioTheme.styled(a, "◀", StudioButton.CHIP, () -> {});
        Button nextBar = StudioTheme.styled(a, "▶", StudioButton.CHIP, () -> {});
        toolbar1.addView(prevBar, new LinearLayout.LayoutParams(a.dp(44), a.dp(38)));
        toolbar1.addView(nextBar, new LinearLayout.LayoutParams(a.dp(44), a.dp(38)));

        Button octBtn = StudioTheme.styled(a, tr("옥타브: C", "Oct: C") + baseOctave[0], StudioButton.CHIP, () -> {});
        LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(-2, a.dp(38));
        olp.leftMargin = a.dp(6);
        toolbar1.addView(octBtn, olp);

        Button lenBtn = StudioTheme.styled(a, tr("길이: 1박", "Len: 1 beat"), StudioButton.CHIP, () -> {});
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(-2, a.dp(38));
        llp.leftMargin = a.dp(6);
        toolbar1.addView(lenBtn, llp);

        Button restBtn = StudioTheme.styled(a, tr("쉼표 ␣", "Rest ␣"), StudioButton.CHIP, () -> {});
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(0, a.dp(38), 1);
        rlp.leftMargin = a.dp(6);
        toolbar1.addView(restBtn, rlp);
        root.addView(toolbar1);

        LinearLayout toolbar2 = a.row();
        toolbar2.setPadding(0, 0, 0, a.dp(8));
        Button deleteLast = StudioTheme.styled(a, tr("⌫ 직전 음 삭제", "⌫ Delete note"), StudioButton.STANDARD, () -> {});
        Button clearAll = StudioTheme.styled(a, tr("전체 초기화", "Clear all"), StudioButton.STANDARD, () -> {});
        toolbar2.addView(deleteLast, new LinearLayout.LayoutParams(0, a.dp(40), 1));
        LinearLayout.LayoutParams clp2 = new LinearLayout.LayoutParams(0, a.dp(40), 1);
        clp2.leftMargin = a.dp(8);
        toolbar2.addView(clearAll, clp2);
        root.addView(toolbar2);

        HorizontalScrollView keyScroll = new HorizontalScrollView(a);
        keyScroll.setHorizontalScrollBarEnabled(false);
        RelativeLayout pianoBox = new RelativeLayout(a);

        Runnable updateUI = () -> {
            int beatsPerBar = (int) MusicTheory.barBeats(s.project.meter);
            statusText.setText(part.label + tr(" · 마디 ", " · Bar ") + (curBar[0] + 1) + " / " + part.bars
                    + tr(" · 박자 ", " · Beat ") + String.format(Locale.US, "%.1f", curBeat[0] + 1.0)
                    + tr(" (총 ", " (Total ") + workingPhrase.notes.size() + tr("음)", " notes)"));
            
            StringBuilder sb = new StringBuilder();
            double barStartBeat = curBar[0] * beatsPerBar;
            double barEndBeat = barStartBeat + beatsPerBar;
            for (VoicePhrase.Note n : workingPhrase.notes) {
                if (n.beat >= barStartBeat && n.beat < barEndBeat) {
                    sb.append("[").append(MusicTheory.note(n.midi)).append(" ").append(String.format(Locale.US, "%.1f", n.beat - barStartBeat + 1.0)).append("] ");
                }
            }
            noteListText.setText(sb.length() == 0 ? tr("(현재 마디에 입력된 음 없음)", "(No notes in this bar)") : sb.toString());
            octBtn.setText(tr("옥타브: C", "Oct: C") + baseOctave[0]);
            lenBtn.setText(stepLen[0] == 1.0 ? tr("길이: 1박", "Len: 1 beat") : stepLen[0] == 0.5 ? tr("길이: 반박", "Len: 1/2 beat") : tr("길이: 2박", "Len: 2 beats"));
        };

        IntConsumer addNote = midiPitch -> {
            int beatsPerBar = (int) MusicTheory.barBeats(s.project.meter);
            double absBeat = curBar[0] * beatsPerBar + curBeat[0];
            VoicePhrase.Note n = new VoicePhrase.Note();
            n.beat = absBeat;
            n.length = stepLen[0];
            n.midi = midiPitch;
            n.velocity = 100;
            workingPhrase.notes.removeIf(existing -> Math.abs(existing.beat - absBeat) < 0.001);
            workingPhrase.notes.add(n);
            workingPhrase.notes.sort((n1, n2) -> Double.compare(n1.beat, n2.beat));

            curBeat[0] += stepLen[0];
            if (curBeat[0] >= beatsPerBar) {
                curBeat[0] = 0;
                curBar[0] = (curBar[0] + 1) % part.bars;
            }
            updateUI.run();
        };

        prevBar.setOnClickListener(v -> {
            curBar[0] = Math.max(0, curBar[0] - 1);
            curBeat[0] = 0;
            updateUI.run();
        });
        nextBar.setOnClickListener(v -> {
            curBar[0] = Math.min(part.bars - 1, curBar[0] + 1);
            curBeat[0] = 0;
            updateUI.run();
        });
        octBtn.setOnClickListener(v -> choose(tr("옥타브", "Octave"), new String[]{"C2", "C3", "C4", "C5"}, baseOctave[0] - 2, n -> {
            baseOctave[0] = n + 2;
            updateUI.run();
        }));
        lenBtn.setOnClickListener(v -> choose(tr("음표 길이", "Note length"), new String[]{tr("1박 (4분음표)", "1 beat"), tr("반박 (8분음표)", "1/2 beat"), tr("2박 (2분음표)", "2 beats")}, stepLen[0] == 1.0 ? 0 : stepLen[0] == 0.5 ? 1 : 2, n -> {
            stepLen[0] = n == 0 ? 1.0 : n == 1 ? 0.5 : 2.0;
            updateUI.run();
        }));
        restBtn.setOnClickListener(v -> {
            int beatsPerBar = (int) MusicTheory.barBeats(s.project.meter);
            curBeat[0] += stepLen[0];
            if (curBeat[0] >= beatsPerBar) {
                curBeat[0] = 0;
                curBar[0] = (curBar[0] + 1) % part.bars;
            }
            updateUI.run();
        });
        deleteLast.setOnClickListener(v -> {
            if (!workingPhrase.notes.isEmpty()) {
                workingPhrase.notes.remove(workingPhrase.notes.size() - 1);
                updateUI.run();
            }
        });
        clearAll.setOnClickListener(v -> {
            workingPhrase.notes.clear();
            curBar[0] = 0;
            curBeat[0] = 0;
            updateUI.run();
        });

        int whiteKeyWidth = a.dp(44), whiteKeyHeight = a.dp(130);
        int blackKeyWidth = a.dp(28), blackKeyHeight = a.dp(80);
        int[] whitePitches = {0, 2, 4, 5, 7, 9, 11, 12, 14, 16};
        String[] whiteNames = {"C", "D", "E", "F", "G", "A", "B", "C", "D", "E"};

        for (int i = 0; i < 10; i++) {
            final int noteIndex = i;
            TextView whiteKey = new TextView(a);
            whiteKey.setText(whiteNames[i]);
            whiteKey.setTextColor(Color.rgb(80, 90, 100));
            whiteKey.setTextSize(11);
            whiteKey.setTypeface(StudioTheme.bold(a));
            whiteKey.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            whiteKey.setPadding(0, 0, 0, a.dp(8));
            whiteKey.setBackground(StudioTheme.background(a, 0xfff0f4f8, 6, 0xffb0c0cc));
            whiteKey.setOnClickListener(v -> {
                int midi = (baseOctave[0] + 1) * 12 + whitePitches[noteIndex];
                addNote.accept(midi);
            });
            RelativeLayout.LayoutParams wlp = new RelativeLayout.LayoutParams(whiteKeyWidth, whiteKeyHeight);
            wlp.leftMargin = i * (whiteKeyWidth + a.dp(2));
            pianoBox.addView(whiteKey, wlp);
        }

        int[] blackWhiteSlots = {0, 1, 3, 4, 5, 7, 8};
        int[] blackPitches = {1, 3, 6, 8, 10, 13, 15};
        String[] blackNames = {"C♯", "D♯", "F♯", "G♯", "A♯", "C♯", "D♯"};
        for (int i = 0; i < 7; i++) {
            final int noteIndex = i;
            TextView blackKey = new TextView(a);
            blackKey.setText(blackNames[i]);
            blackKey.setTextColor(Color.rgb(200, 210, 220));
            blackKey.setTextSize(9);
            blackKey.setTypeface(StudioTheme.bold(a));
            blackKey.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            blackKey.setPadding(0, 0, 0, a.dp(6));
            blackKey.setBackground(StudioTheme.background(a, 0xff1b242b, 4, 0xff000000));
            blackKey.setOnClickListener(v -> {
                int midi = (baseOctave[0] + 1) * 12 + blackPitches[noteIndex];
                addNote.accept(midi);
            });
            RelativeLayout.LayoutParams blp = new RelativeLayout.LayoutParams(blackKeyWidth, blackKeyHeight);
            int slot = blackWhiteSlots[i];
            blp.leftMargin = slot * (whiteKeyWidth + a.dp(2)) + whiteKeyWidth - (blackKeyWidth / 2);
            pianoBox.addView(blackKey, blp);
        }

        keyScroll.addView(pianoBox);
        root.addView(keyScroll);

        Button saveBtn = StudioTheme.styled(a, tr("저장 및 트랙에 적용", "Save and apply to track"), StudioButton.STANDARD, () -> {
            if (!current(project, revision, partId)) {
                stale();
                dialog.dismiss();
                return;
            }
            edit(p -> {
                ComposerProject.Part currentPart = p.part(partId);
                currentPart.phrases.removeIf(ph -> ph.trackId.equals(t.id));
                if (!workingPhrase.notes.isEmpty()) {
                    currentPart.phrases.add(workingPhrase);
                    t.voice = true;
                    t.phraseSource = "keyboard";
                } else {
                    t.voice = false;
                    t.phraseSource = "";
                }
            }, false);
            dialog.dismiss();
        });
        StudioTheme.selected(saveBtn, true);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, a.dp(48));
        slp.topMargin = a.dp(12);
        root.addView(saveBtn, slp);

        updateUI.run();
        dialog.setContentView(root);
        dialog.show();
        StudioDialogs.track(a, dialog);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setLayout(Math.min(a.root.getWidth(), a.dp(a.tabletLandscape() ? 720 : 560)), -2);
        }
    }

}
