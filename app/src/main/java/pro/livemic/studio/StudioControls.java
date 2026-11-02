package pro.livemic.studio;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.SeekBar;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Shared, native-drawn studio controls. All interaction stays connected to the audio engine. */
final class StudioGlyph extends Drawable {
    final String kind;
    final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    int color;

    StudioGlyph(String k, int c) {
        kind = k;
        color = c;
    }

    @Override
    public void draw(Canvas canvas) {
        canvas.save();
        canvas.translate(getBounds().left, getBounds().top);
        canvas.scale(getBounds().width() / 24f, getBounds().height() / 24f);
        draw(canvas, kind, color, p);
        canvas.restore();
    }

    static void draw(Canvas c, String k, int color, Paint p) {
        p.setColor(color);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.8f);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        Path a = new Path();
        switch (k) {
            case "menu":
                for (int y = 6; y <= 18; y += 6) c.drawLine(3, y, 21, y, p);
                break;
            case "close":
                c.drawLine(5, 5, 19, 19, p);
                c.drawLine(19, 5, 5, 19, p);
                break;
            case "mic":
            case "mute":
                c.drawRoundRect(9, 2, 15, 14, 3, 3, p);
                a.moveTo(6, 10);
                a.lineTo(6, 12);
                a.cubicTo(6, 20, 18, 20, 18, 12);
                a.lineTo(18, 10);
                c.drawPath(a, p);
                c.drawLine(12, 18, 12, 22, p);
                c.drawLine(9, 22, 15, 22, p);
                if (k.equals("mute")) c.drawLine(2, 2, 22, 22, p);
                break;
            case "music":
                a.moveTo(9, 17);
                a.lineTo(9, 5);
                a.lineTo(20, 3);
                a.lineTo(20, 15);
                c.drawPath(a, p);
                c.drawLine(9, 8, 20, 6, p);
                p.setStyle(Paint.Style.FILL);
                c.drawOval(3, 16, 9, 21, p);
                c.drawOval(14, 14, 20, 19, p);
                break;
            case "folder":
                a.moveTo(2, 7);
                a.lineTo(2, 5);
                a.quadTo(2, 3, 4, 3);
                a.lineTo(10, 3);
                a.lineTo(12, 6);
                a.lineTo(20, 6);
                a.quadTo(22, 6, 22, 8);
                a.lineTo(22, 19);
                a.quadTo(22, 21, 20, 21);
                a.lineTo(4, 21);
                a.quadTo(2, 21, 2, 19);
                a.close();
                c.drawPath(a, p);
                break;
            case "camera":
                c.drawRoundRect(2, 6, 18, 20, 3, 3, p);
                a.moveTo(18, 10);
                a.lineTo(23, 7);
                a.lineTo(23, 19);
                a.lineTo(18, 16);
                c.drawPath(a, p);
                break;
            case "play":
                p.setStyle(Paint.Style.FILL);
                a.moveTo(8, 5);
                a.lineTo(20, 12);
                a.lineTo(8, 19);
                a.close();
                c.drawPath(a, p);
                break;
            case "pause":
                p.setStyle(Paint.Style.FILL);
                c.drawRoundRect(6, 5, 10, 19, 1, 1, p);
                c.drawRoundRect(14, 5, 18, 19, 1, 1, p);
                break;
            case "more":
                p.setStyle(Paint.Style.FILL);
                for (int y = 5; y <= 19; y += 7) c.drawCircle(12, y, 1.6f, p);
                break;
            case "down":
                a.moveTo(6, 9);
                a.lineTo(12, 15);
                a.lineTo(18, 9);
                c.drawPath(a, p);
                break;
            case "arrow":
                a.moveTo(9, 5);
                a.lineTo(16, 12);
                a.lineTo(9, 19);
                c.drawPath(a, p);
                break;
            case "headphones":
                a.moveTo(3, 15);
                a.lineTo(3, 11);
                a.cubicTo(3, 0, 21, 0, 21, 11);
                a.lineTo(21, 15);
                c.drawPath(a, p);
                c.drawRoundRect(2, 11, 6, 21, 2, 2, p);
                c.drawRoundRect(18, 11, 22, 21, 2, 2, p);
                break;
            case "settings":
                c.drawCircle(12, 12, 6, p);
                c.drawCircle(12, 12, 2, p);
                for (int i = 0; i < 8; i++) {
                    double r = i * Math.PI / 4;
                    c.drawLine(
                            12 + (float) Math.cos(r) * 7,
                            12 + (float) Math.sin(r) * 7,
                            12 + (float) Math.cos(r) * 10,
                            12 + (float) Math.sin(r) * 10,
                            p
                    );
                }
                break;
            case "record":
                c.drawCircle(12, 12, 9, p);
                p.setStyle(Paint.Style.FILL);
                c.drawCircle(12, 12, 5, p);
                break;
            case "piano":
                c.drawRoundRect(2, 3, 22, 21, 2, 2, p);
                for (int i = 7; i < 22; i += 5) c.drawLine(i, 3, i, 21, p);
                p.setStyle(Paint.Style.FILL);
                for (int i = 5; i < 20; i += 5) c.drawRect(i, 3, i + 3, 13, p);
                break;
            default:
                c.drawCircle(12, 12, 9, p);
                p.setStyle(Paint.Style.FILL);
                p.setTextSize(15);
                p.setTypeface(Typeface.DEFAULT_BOLD);
                p.setTextAlign(Paint.Align.CENTER);
                c.drawText("?", 12, 17, p);
                p.setTextAlign(Paint.Align.LEFT);
        }
        p.setStyle(Paint.Style.FILL);
    }

    @Override
    public void setAlpha(int a) {
        p.setAlpha(a);
    }

    @Override
    public void setColorFilter(ColorFilter f) {
        p.setColorFilter(f);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}

class StudioButton extends Button {
    static final int STANDARD = 0, QUIET = 1, CHIP = 2, OUTLINE = 3, RECORD = 4, CIRCLE = 5, OPTION = 6;
    int role = STANDARD;
    final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    final RectF box = new RectF();

    StudioButton(Context c) {
        super(c);
        setBackgroundColor(Color.TRANSPARENT);
        setStateListAnimator(null);
        setElevation(0);
        setMinWidth(0);
        setMinimumWidth(0);
        setMinHeight(StudioTheme.dp(c, 48));
        setMinimumHeight(StudioTheme.dp(c, 48));
        setAllCaps(false);
        setIncludeFontPadding(false);
        setTypeface(StudioTheme.bold(c));
        setTextSize(13);
        setLetterSpacing(0);
        setGravity(Gravity.CENTER);
        setPadding(StudioTheme.dp(c, 12), 0, StudioTheme.dp(c, 12), 0);
        setTextColor(StudioTheme.TEXT);
    }

    StudioButton role(int v) {
        role = v;
        invalidate();
        return this;
    }

    StudioButton glyph(String k) {
        StudioGlyph d = new StudioGlyph(k, role == RECORD ? StudioTheme.DANGER : StudioTheme.MUTED);
        int size = StudioTheme.dp(getContext(), 17);
        d.setBounds(0, 0, size, size);
        setCompoundDrawables(d, null, null, null);
        setCompoundDrawablePadding(StudioTheme.dp(getContext(), 7));
        return this;
    }

    StudioButton trailing(String k) {
        StudioGlyph d = new StudioGlyph(k, StudioTheme.MUTED);
        int size = StudioTheme.dp(getContext(), 16);
        d.setBounds(0, 0, size, size);
        Drawable[] old = getCompoundDrawables();
        setCompoundDrawables(old[0], old[1], d, old[3]);
        return this;
    }

    @Override
    protected void onDraw(Canvas c) {
        Drawable glyph = getCompoundDrawables()[0];
        if (glyph instanceof StudioGlyph) {
            ((StudioGlyph) glyph).color = getCurrentTextColor();
        }
        float d = getResources().getDisplayMetrics().density;
        box.set(.7f * d, .7f * d, getWidth() - .7f * d, getHeight() - .7f * d);
        float radius = role == CHIP || role == OUTLINE || role == CIRCLE ? getHeight() / 2f : 8 * d;
        if (role == CIRCLE) {
            float diameter = Math.min(getWidth(), getHeight()) - 2 * d;
            box.set(
                    (getWidth() - diameter) / 2,
                    (getHeight() - diameter) / 2,
                    (getWidth() + diameter) / 2,
                    (getHeight() + diameter) / 2
            );
        }
        boolean selected = isSelected();
        ColorStateList tint = getBackgroundTintList();
        int custom = tint == null ? 0 : tint.getDefaultColor();
        boolean colored = !selected && (custom == StudioTheme.ACCENT || custom == StudioTheme.DANGER);
        int fill = selected
                ? (role == OPTION ? StudioTheme.CARD_SELECTED_BG : StudioTheme.ACCENT)
                : colored
                ? custom
                : role == RECORD
                ? 0xff2b161b
                : role == OPTION
                ? StudioTheme.RAISED
                : StudioTheme.RAISED;
        if (role != QUIET) {
            p.setShader(null);
            p.setColor(fill);
            p.setAlpha(isEnabled() ? 255 : 125);
            c.drawRoundRect(box, radius, radius, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(.9f * d);
            int stroke = role == RECORD
                    ? 0xffa8424d
                    : role == OUTLINE
                    ? StudioTheme.ACCENT
                    : selected
                    ? (role == OPTION ? StudioTheme.CARD_SELECTED_BORDER : StudioTheme.ACCENT)
                    : 0xff35383e;
            p.setColor(stroke);
            c.drawRoundRect(box, radius, radius, p);
            p.setStyle(Paint.Style.FILL);
        }
        if (isPressed()) {
            p.setColor(0x18ffffff);
            c.drawRoundRect(box, radius, radius, p);
        }
        c.saveLayerAlpha(0, 0, getWidth(), getHeight(), isEnabled() ? 255 : 110);
        super.onDraw(c);
        c.restore();
    }
}

final class StudioNavButton extends StudioButton {
    final String icon;
    final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    StudioNavButton(Context c, String label, String icon, Runnable action) {
        super(c);
        this.icon = icon;
        setText(label);
        setOnClickListener(v -> action.run());
        setContentDescription(label);
    }

    @Override
    protected void onDraw(Canvas c) {
        float d = getResources().getDisplayMetrics().density;
        int color = isSelected() ? StudioTheme.ACCENT : StudioTheme.MUTED;
        c.save();
        c.translate(getWidth() / 2f - 11 * d, 8 * d);
        c.scale(22 * d / 24, 22 * d / 24);
        StudioGlyph.draw(c, icon, color, paint);
        c.restore();
        paint.setTypeface(StudioTheme.medium(getContext()));
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(StudioTheme.sp(getContext(), 12));
        paint.setColor(color);
        c.drawText(getText().toString(), getWidth() / 2f, 49 * d, paint);
        paint.setTextAlign(Paint.Align.LEFT);
    }
}

final class StudioFader extends View {
    interface Changed {
        void changed(float value);
    }

    final android.text.TextPaint p = new android.text.TextPaint(Paint.ANTI_ALIAS_FLAG);
    final String label;
    final MainActivity.Formatter formatter;
    final float min, max;
    float value;
    final Changed listener;

    StudioFader(Context c, String label, float min, float max, float value, MainActivity.Formatter formatter, Changed listener) {
        super(c);
        this.label = label;
        this.min = min;
        this.max = max;
        this.value = value;
        this.formatter = formatter;
        this.listener = listener;
        setFocusable(true);
        setContentDescription(label);
    }

    private float captionHeight() {
        p.setTypeface(StudioTheme.medium(getContext()));
        p.setTextSize(StudioTheme.sp(getContext(), 13));
        Paint.FontMetrics metrics = p.getFontMetrics();
        float height = metrics.descent - metrics.ascent;
        p.setTypeface(StudioTheme.mono(getContext()));
        p.setTextSize(StudioTheme.sp(getContext(), 12));
        metrics = p.getFontMetrics();
        return Math.max(height, metrics.descent - metrics.ascent);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int height = (int) Math.ceil(34 * getResources().getDisplayMetrics().density + captionHeight());
        setMeasuredDimension(resolveSize(StudioTheme.dp(getContext(), 160), widthSpec), resolveSize(height, heightSpec));
    }

    @Override
    protected void onDraw(Canvas c) {
        float d = getResources().getDisplayMetrics().density, w = getWidth(), left = 7 * d, right = w - 8 * d;
        float height = captionHeight(), y = 6 * d + height + 13 * d;
        float f = Math.max(0, Math.min(1, (value - min) / (max - min)));
        String formatted = formatter.value(value);
        p.setTypeface(StudioTheme.mono(getContext()));
        p.setTextSize(StudioTheme.sp(getContext(), 12));
        float valueWidth = p.measureText(formatted);
        Paint.FontMetrics metrics = p.getFontMetrics();
        float baseline = 6 * d - metrics.ascent;
        p.setTextAlign(Paint.Align.RIGHT);
        p.setColor(StudioTheme.ACCENT);
        c.drawText(formatted, w - 2 * d, baseline, p);
        p.setTextAlign(Paint.Align.LEFT);
        p.setTypeface(StudioTheme.medium(getContext()));
        p.setTextSize(StudioTheme.sp(getContext(), 13));
        p.setColor(StudioTheme.TEXT);
        metrics = p.getFontMetrics();
        baseline = 6 * d - metrics.ascent;
        c.drawText(
                android.text.TextUtils.ellipsize(label, p, Math.max(0, w - valueWidth - 16 * d), android.text.TextUtils.TruncateAt.END).toString(),
                0,
                baseline,
                p
        );
        p.setStrokeWidth(5 * d);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setColor(0xff272b31);
        c.drawLine(left, y, right, y, p);
        float x = left + (right - left) * f;
        p.setColor(StudioTheme.ACCENT);
        c.drawLine(left, y, x, y, p);
        p.setColor(StudioTheme.TEXT);
        c.drawCircle(x, y, 7 * d, p);
    }

    void change(float v) {
        value = Math.max(min, Math.min(max, v));
        listener.changed(value);
        setContentDescription(label + " " + formatter.value(value));
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (!isEnabled()) return false;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                getParent().requestDisallowInterceptTouchEvent(true);
                setPressed(true);
            case MotionEvent.ACTION_MOVE:
                change(min + Math.max(0, Math.min(1, (e.getX() - 7 * getResources().getDisplayMetrics().density) / Math.max(1, getWidth() - 15 * getResources().getDisplayMetrics().density))) * (max - min));
                return true;
            case MotionEvent.ACTION_UP:
                setPressed(false);
                getParent().requestDisallowInterceptTouchEvent(false);
                performClick();
                return true;
            case MotionEvent.ACTION_CANCEL:
                setPressed(false);
                getParent().requestDisallowInterceptTouchEvent(false);
                return true;
        }
        return false;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo i) {
        super.onInitializeAccessibilityNodeInfo(i);
        i.setClassName(SeekBar.class.getName());
        i.setRangeInfo(AccessibilityNodeInfo.RangeInfo.obtain(AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_FLOAT, min, max, value));
        i.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS);
        i.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);
        i.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
    }

    @Override
    public boolean performAccessibilityAction(int action, Bundle b) {
        if (!isEnabled()) return false;
        if (action == AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.getId() && b != null) {
            change(b.getFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE));
            return true;
        }
        if (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                || action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) {
            change(value + (max - min) * .05f * (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD ? 1 : -1));
            return true;
        }
        return super.performAccessibilityAction(action, b);
    }
}

final class StudioTakeWave extends View {
    float[] levels;
    final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

    StudioTakeWave(Context c) {
        super(c);
        setContentDescription(AppLanguage.text(c, "녹음 파형", "Recording waveform"));
    }

    @Override
    protected void onDraw(Canvas c) {
        float d = getResources().getDisplayMetrics().density, w = getWidth(), h = getHeight();
        p.setColor(StudioTheme.LCD_BG);
        c.drawRoundRect(0, 0, w, h, 8 * d, 8 * d, p);
        p.setColor(0x168b9b49);
        p.setStrokeWidth(.7f * d);
        for (int i = 1; i < 12; i++) c.drawLine(w * i / 12, 0, w * i / 12, h, p);
        for (int i = 1; i < 4; i++) c.drawLine(0, h * i / 4, w, h * i / 4, p);
        if (levels == null) return;
        float gap = 2 * d, bw = (w - 20 * d) / levels.length;
        for (int i = 0; i < levels.length; i++) {
            float v = Math.max(.025f, Math.min(1, levels[i]));
            p.setColor(StudioTheme.ACCENT);
            float bh = (h - 16 * d) * v;
            c.drawRoundRect(10 * d + i * bw, (h - bh) / 2, 10 * d + i * bw + Math.max(d, bw - gap), (h + bh) / 2, 1 * d, 1 * d, p);
        }
    }
}

final class StudioPlaybackLine extends View {
    interface Seek {
        void seek(float fraction);
    }

    float progress;
    boolean dragging;
    final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Seek listener;

    StudioPlaybackLine(Context c, Seek listener) {
        super(c);
        this.listener = listener;
        setFocusable(true);
        setContentDescription(AppLanguage.text(c, "반주 재생 위치", "Backing playback position"));
    }

    @Override
    protected void onDraw(Canvas c) {
        float d = getResources().getDisplayMetrics().density;
        float y = getHeight() / 2f, left = 7 * d, right = getWidth() - 7 * d;
        float x = left + Math.max(0, Math.min(1, progress)) * (right - left);
        p.setColor(0xff272b31);
        c.drawRoundRect(left, y - 2.5f * d, right, y + 2.5f * d, 2.5f * d, 2.5f * d, p);
        p.setColor(StudioTheme.ACCENT);
        c.drawRoundRect(left, y - 2.5f * d, x, y + 2.5f * d, 2.5f * d, 2.5f * d, p);
        p.setColor(StudioTheme.TEXT);
        c.drawCircle(x, y, 6 * d, p);
    }

    void change(float value) {
        progress = Math.max(0, Math.min(1, value));
        setContentDescription(AppLanguage.text(getContext(), "반주 재생 위치 ", "Backing playback position ") + Math.round(progress * 100) + "%");
        listener.seek(progress);
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) {
            if (dragging) {
                dragging = false;
                getParent().requestDisallowInterceptTouchEvent(false);
            }
            return false;
        }
        float d = getResources().getDisplayMetrics().density;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragging = true;
                getParent().requestDisallowInterceptTouchEvent(true);
            case MotionEvent.ACTION_MOVE:
                change((event.getX() - 7 * d) / Math.max(1, getWidth() - 14 * d));
                return true;
            case MotionEvent.ACTION_UP:
                dragging = false;
                getParent().requestDisallowInterceptTouchEvent(false);
                performClick();
                return true;
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                getParent().requestDisallowInterceptTouchEvent(false);
                return true;
        }
        return false;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName(SeekBar.class.getName());
        info.setRangeInfo(AccessibilityNodeInfo.RangeInfo.obtain(AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_FLOAT, 0, 1, progress));
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS);
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
    }

    @Override
    public boolean performAccessibilityAction(int action, Bundle args) {
        if (!isEnabled()) return false;
        if (action == AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.getId() && args != null) {
            change(args.getFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE));
            return true;
        }
        if (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                || action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) {
            change(progress + (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD ? .05f : -.05f));
            return true;
        }
        return super.performAccessibilityAction(action, args);
    }
}

/** Compact preview of notes actually present in this app's saved Standard MIDI File. */
final class StudioMidiPreview extends View {
    float[] notes;
    final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

    StudioMidiPreview(Context c) {
        super(c);
        setContentDescription(AppLanguage.text(c, "저장된 MIDI 피아노 롤", "Saved MIDI piano roll"));
    }

    @Override
    protected void onDraw(Canvas c) {
        float d = getResources().getDisplayMetrics().density, w = getWidth(), h = getHeight();
        p.setColor(StudioTheme.LCD_BG);
        c.drawRoundRect(0, 0, w, h, 8 * d, 8 * d, p);
        p.setColor(0xff232a24);
        p.setStrokeWidth(.6f * d);
        for (int i = 1; i < 16; i++) c.drawLine(i * w / 16, 0, i * w / 16, h, p);
        for (int i = 1; i < 8; i++) c.drawLine(0, i * h / 8, w, i * h / 8, p);
        if (notes == null || notes.length == 0) return;
        float end = 1, lo = 127, hi = 0;
        for (int i = 0; i < notes.length; i += 3) {
            end = Math.max(end, notes[i] + notes[i + 1]);
            lo = Math.min(lo, notes[i + 2]);
            hi = Math.max(hi, notes[i + 2]);
        }
        lo -= 2;
        hi += 2;
        p.setColor(StudioTheme.ACCENT);
        for (int i = 0; i < notes.length; i += 3) {
            float left = 6 * d + notes[i] / end * (w - 12 * d);
            float right = 6 * d + (notes[i] + notes[i + 1]) / end * (w - 12 * d);
            float y = h - 6 * d - (notes[i + 2] - lo) / (hi - lo) * (h - 12 * d);
            c.drawRoundRect(left, y - 2 * d, Math.max(left + 3 * d, right - 1 * d), y + 2 * d, d, d, p);
        }
    }

    static float[] read(File file) {
        List<Float> out = new ArrayList<>();
        try {
            if (file.length() > 2 * 1024 * 1024) return new float[0];
            byte[] bytes = Files.readAllBytes(file.toPath());
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
            if (in.readInt() != 0x4d546864) return new float[0];
            int header = in.readInt();
            if (header < 6 || header > 1000) return new float[0];
            in.readUnsignedShort();
            int tracks = in.readUnsignedShort();
            in.readUnsignedShort();
            in.skipBytes(header - 6);
            for (int track = 0; track < tracks && in.available() > 8; track++) {
                if (in.readInt() != 0x4d54726b) break;
                int len = in.readInt();
                if (len < 0 || len > in.available()) break;
                byte[] data = new byte[len];
                in.readFully(data);
                DataInputStream t = new DataInputStream(new ByteArrayInputStream(data));
                int tick = 0, running = 0;
                int[] starts = new int[2048];
                Arrays.fill(starts, -1);
                while (t.available() > 0 && out.size() < 6000) {
                    tick += vlq(t);
                    int status = t.readUnsignedByte(), first = -1;
                    if (status < 128) {
                        first = status;
                        status = running;
                    } else if (status < 240) {
                        running = status;
                    }
                    if (status == 255) {
                        t.readUnsignedByte();
                        int n = vlq(t);
                        if (t.skipBytes(n) != n) break;
                        continue;
                    }
                    if (status == 240 || status == 247) {
                        int n = vlq(t);
                        if (t.skipBytes(n) != n) break;
                        continue;
                    }
                    if (status < 128 || status >= 240) break;
                    int key = first >= 0 ? first : t.readUnsignedByte();
                    int kind = status & 240;
                    int velocity = kind == 192 || kind == 208 ? 0 : t.readUnsignedByte();
                    int slot = (status & 15) * 128 + key;
                    if (key >= 128) break;
                    if (kind == 144 && velocity > 0) {
                        starts[slot] = tick;
                    } else if ((kind == 128 || kind == 144) && starts[slot] >= 0) {
                        out.add((float) starts[slot]);
                        out.add((float) Math.max(1, tick - starts[slot]));
                        out.add((float) key);
                        starts[slot] = -1;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        float[] result = new float[out.size()];
        for (int i = 0; i < result.length; i++) result[i] = out.get(i);
        return result;
    }

    static int vlq(DataInputStream in) throws IOException {
        int value = 0;
        for (int i = 0; i < 4; i++) {
            int b = in.readUnsignedByte();
            value = (value << 7) | (b & 127);
            if ((b & 128) == 0) return value;
        }
        throw new IOException("MIDI variable length overflow");
    }
}
