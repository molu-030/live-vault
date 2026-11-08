package pro.livemic.studio;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Real composer tracks only. Solo belongs to this visible mixer, never to the project. */
final class LiveMixerView extends LinearLayout {
    final MainActivity a;
    final ComposerSession session;
    ComposerProject project;
    ComposerProject.Part part;
    int revision = -1, solo = -1;
    final LinearLayout strips;
    final TextView heading;
    boolean dragging;
    Gain activeGain;

    LiveMixerView(MainActivity a) {
        super(a); this.a = a; session = a.composer;
        setOrientation(VERTICAL);
        heading = a.text("", 12, StudioTheme.MUTED);
        addView(heading);
        HorizontalScrollView scroll = new HorizontalScrollView(a);
        scroll.setHorizontalScrollBarEnabled(true);
        strips = a.row();
        scroll.addView(strips, new HorizontalScrollView.LayoutParams(-2, -2));
        addView(scroll, new LinearLayout.LayoutParams(-1, -2));
        poll();
    }

    ComposerProject.Part currentPart() {
        int index = (int) session.state()[2];
        return session.playing() && index >= 0 && index < session.project.parts.size()
                ? session.project.parts.get(index) : session.part();
    }
    boolean current() { return project == session.project && part == currentPart() && session.ready && !session.capturing; }
    boolean valid(int slot, TrackConfig track, int expectedRevision) {
        return current() && expectedRevision == session.revision && slot >= 0
                && slot < part.tracks.size() && part.tracks.get(slot) == track;
    }
    void clearSolo() { solo = -1; NativeComposer.clearSolo(); }
    void close() {
        if (dragging && part != null && part == currentPart())
            for (int i = 0; i < part.tracks.size(); i++) {
                TrackConfig track = part.tracks.get(i);
                NativeComposer.track(i, track.volume, track.pan, track.muted, false);
            }
        dragging = false; activeGain = null; clearSolo();
    }
    @Override protected void onDetachedFromWindow() { close(); super.onDetachedFromWindow(); }

    void poll() {
        if (dragging) {
            if (activeGain != null && valid(activeGain.slot, activeGain.track, activeGain.createdRevision)) return;
            close();
        }
        ComposerProject.Part next = currentPart();
        if (project != session.project || part != next) {
            clearSolo(); project = session.project; part = next; revision = -1;
        }
        if (revision == session.revision) return;
        revision = session.revision;
        drawTracks();
    }

    void drawTracks() {
        strips.removeAllViews();
        heading.setText(a.tr("파트 ", "Part ") + part.label + a.tr(" · 트랙 믹서 · 솔로는 임시", " · Track mixer · temporary solo"));
        int count = Math.min(8, part.tracks.size());
        if (count == 0) {
            strips.addView(a.text(a.tr("이 파트에는 트랙이 없습니다.", "This part has no tracks."), 14, StudioTheme.MUTED));
            return;
        }
        for (int i = 0; i < count; i++) {
            final int slot = i, createdRevision = session.revision;
            TrackConfig track = part.tracks.get(i);
            LinearLayout strip = a.column();
            strip.setPadding(a.dp(4), a.dp(4), a.dp(4), a.dp(4));
            TextView name = a.text(track.name, 10, StudioTheme.TEXT);
            name.setGravity(Gravity.CENTER); name.setMaxLines(1); name.setHorizontallyScrolling(false);
            name.setPadding(0,0,0,0); name.setContentDescription(track.name);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            strip.addView(name, new LinearLayout.LayoutParams(-1, a.dp(28)));
            String source = sourceLabel(a, track);
            TextView type = a.text(source, 9, StudioTheme.MUTED);
            type.setGravity(Gravity.CENTER); type.setMaxLines(1); type.setHorizontallyScrolling(false);
            type.setPadding(0,0,0,0);
            type.setEllipsize(android.text.TextUtils.TruncateAt.END); type.setContentDescription(source);
            strip.addView(type, new LinearLayout.LayoutParams(-1, a.dp(22)));
            Button mute = StudioTheme.styled(a, a.tr("뮤트", "Mute"), StudioButton.STANDARD, () -> {
                if (!valid(slot, track, createdRevision)) { poll(); return; }
                session.edit(p -> track.muted = !track.muted, false);
                apply(slot, track.volume); poll();
            });
            fitStripButton(mute);
            mute.setContentDescription(track.name + a.tr(" 뮤트", " mute"));
            StudioTheme.selected(mute, track.muted);
            strip.addView(mute, new LinearLayout.LayoutParams(-1, a.dp(48)));
            Button soloButton = StudioTheme.styled(a, a.tr("솔로", "Solo"), StudioButton.STANDARD, () -> {
                if (!valid(slot, track, createdRevision)) { poll(); return; }
                solo = solo == slot ? -1 : slot;
                for (int n = 0; n < part.tracks.size(); n++) apply(n, part.tracks.get(n).volume);
                drawTracks();
            });
            fitStripButton(soloButton);
            soloButton.setContentDescription(track.name + a.tr(" 임시 솔로", " temporary solo"));
            StudioTheme.selected(soloButton, solo == slot);
            strip.addView(soloButton, new LinearLayout.LayoutParams(-1, a.dp(48)));
            Gain gain = new Gain(slot, track);
            strip.addView(gain, new LinearLayout.LayoutParams(-1, a.dp(130)));
            strips.addView(strip, new LinearLayout.LayoutParams(a.dp(68), -2));
        }
    }

    private void fitStripButton(Button button) {
        button.setPadding(0, 0, 0, 0);
        button.setTextSize(12);
        button.setMaxLines(1);
        button.setHorizontallyScrolling(false);
        button.setEllipsize(android.text.TextUtils.TruncateAt.END);
    }

    static String sourceLabel(MainActivity a, TrackConfig track) {
        if (!track.voice) return a.tr("생성", "Generated");
        if ("keyboard".equals(track.phraseSource)) return a.tr("건반", "Keyboard");
        if ("microphone".equals(track.phraseSource)) return a.tr("목소리", "Voice");
        return a.tr("기록", "Recorded");
    }

    void apply(int slot, float volume) {
        if (!current() || slot >= part.tracks.size()) return;
        TrackConfig track = part.tracks.get(slot);
        NativeComposer.track(slot, volume, track.pan, track.muted, solo == slot);
    }

    final class Gain extends View {
        final int slot, createdRevision;
        final TrackConfig track;
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        float value, downX, downY;
        boolean vertical;
        Gain(int slot, TrackConfig track) {
            super(a); this.slot = slot; this.track = track; value = track.volume; createdRevision = session.revision;
            setFocusable(true); setContentDescription(track.name + a.tr(" 게인", " gain"));
        }
        @Override protected void onDraw(Canvas canvas) {
            float x = getWidth() / 2f, top = a.dp(28), bottom = getHeight() - a.dp(12);
            paint.setTypeface(StudioTheme.mono(a)); paint.setTextSize(StudioTheme.sp(a, 11));
            paint.setTextAlign(Paint.Align.CENTER); paint.setColor(StudioTheme.TEXT);
            canvas.drawText(Math.round(value * 100) + "%", x, a.dp(17), paint);
            paint.setStrokeWidth(a.dp(5)); paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor(StudioTheme.BORDER); canvas.drawLine(x, top, x, bottom, paint);
            float y = bottom - value * (bottom - top);
            paint.setColor(StudioTheme.ACCENT); canvas.drawLine(x, bottom, x, y, paint);
            canvas.drawCircle(x, y, a.dp(8), paint);
        }
        void preview(float next) {
            value = Math.max(0, Math.min(1, next)); apply(slot, value); invalidate();
        }
        void commit() {
            if (valid(slot, track, createdRevision)) session.edit(p -> track.volume = value, false);
            dragging = false; activeGain = null; poll();
        }
        @Override public boolean onTouchEvent(MotionEvent event) {
            if (!valid(slot, track, createdRevision)) { close(); post(LiveMixerView.this::poll); return false; }
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = event.getX(); downY = event.getY(); vertical = false;
                    // Reserve a fader gesture before the surrounding vertical page can intercept it.
                    getParent().requestDisallowInterceptTouchEvent(true);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = Math.abs(event.getX() - downX), dy = Math.abs(event.getY() - downY);
                    if (!vertical && dx > ViewConfiguration.get(a).getScaledTouchSlop() && dx > dy) {
                        getParent().requestDisallowInterceptTouchEvent(false); return false;
                    }
                    if (!vertical && dy > ViewConfiguration.get(a).getScaledTouchSlop() && dy > dx) {
                        vertical = true; dragging = true; activeGain = this; getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    if (vertical) preview(1 - (event.getY() - a.dp(28)) / Math.max(1, getHeight() - a.dp(40)));
                    return true;
                case MotionEvent.ACTION_UP:
                    if (vertical) commit(); else performClick();
                    getParent().requestDisallowInterceptTouchEvent(false); return true;
                case MotionEvent.ACTION_CANCEL:
                    preview(track.volume); dragging = false; activeGain = null; vertical = false;
                    getParent().requestDisallowInterceptTouchEvent(false); return true;
                default: return true;
            }
        }
        @Override public boolean performClick() { super.performClick(); return true; }
        @Override public boolean onKeyDown(int key, KeyEvent event) {
            if ((key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN) && valid(slot, track, createdRevision)) {
                preview(value + (key == KeyEvent.KEYCODE_DPAD_UP ? .05f : -.05f)); commit(); return true;
            }
            return super.onKeyDown(key, event);
        }
        @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info); info.setClassName("android.widget.SeekBar");
            info.setRangeInfo(AccessibilityNodeInfo.RangeInfo.obtain(AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_FLOAT, 0, 1, value));
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS);
        }
        @Override public boolean performAccessibilityAction(int action, Bundle args) {
            if (action == AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.getId() && args != null && valid(slot, track, createdRevision)) {
                float next = args.getFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, Float.NaN);
                if (!Float.isFinite(next)) return false;
                preview(next); commit(); return true;
            }
            return super.performAccessibilityAction(action, args);
        }
    }
}
