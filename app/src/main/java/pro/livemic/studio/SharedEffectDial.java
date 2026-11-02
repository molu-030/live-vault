package pro.livemic.studio;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.SeekBar;

/**
 * Bound effect control that supports both compact horizontal fine-slider layout
 * (used in the redesigned Live Studio panel) and full rotary dial geometry.
 */
final class SharedEffectDial extends View {
    interface Listener {
        void changed(float value);
    }

    private final Listener listener;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arc = new RectF();
    private final int slop;
    private String label = "";
    private MainActivity.Formatter formatter = v -> "";
    private float min = 0, max = 1, value = 0;
    private float cx, cy, radius, downX, downY, lastAngle, dragValue;
    private boolean tracking, dragging, angular, sliderMode, bound;
    private int pointerId = MotionEvent.INVALID_POINTER_ID;

    SharedEffectDial(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        slop = ViewConfiguration.get(context).getScaledTouchSlop();
        setFocusable(true);
        setClickable(true);
    }

    private float dp(float n) {
        return n * getResources().getDisplayMetrics().density;
    }

    private float sp(float n) {
        return n * getResources().getDisplayMetrics().scaledDensity;
    }

    private float clamp(float v) {
        return Math.max(min, Math.min(max, v));
    }

    boolean bind(String label, float min, float max, float value, MainActivity.Formatter formatter) {
        if (tracking) return false;
        if (label == null || formatter == null || !Float.isFinite(min) || !Float.isFinite(max) || max <= min || !Float.isFinite(value)) {
            return false;
        }
        this.label = label;
        this.min = min;
        this.max = max;
        this.value = clamp(value);
        this.formatter = formatter;
        bound = true;
        describe();
        invalidate();
        return true;
    }

    void sync(float value) {
        if (tracking || !bound || !Float.isFinite(value)) return;
        float next = clamp(value);
        if (next != this.value) {
            this.value = next;
            describe();
            invalidate();
        }
    }

    boolean isTracking() {
        return tracking;
    }

    void cancelGesture() {
        tracking = false;
        dragging = false;
        pointerId = MotionEvent.INVALID_POINTER_ID;
        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
    }

    private void describe() {
        if (bound) setContentDescription(label + ", " + formatter.value(value));
    }

    private boolean change(float next) {
        if (!bound || !Float.isFinite(next)) return false;
        next = clamp(next);
        if (Math.abs(value - next) <= Math.max(1e-7f, (max - min) * 1e-6f)) return false;
        value = next;
        describe();
        invalidate();
        if (listener != null) listener.changed(value);
        return true;
    }

    private float fraction() {
        return (value - min) / (max - min);
    }

    private boolean isCompactSlider() {
        return getHeight() > 0 && getHeight() < dp(105);
    }

    private void geometry() {
        cx = getWidth() / 2f;
        cy = getHeight() / 2f + dp(12);
        radius = Math.max(dp(22), Math.min(getWidth() - dp(12), getHeight() - dp(40)) * .43f);
    }

    @Override
    protected void onMeasure(int w, int h) {
        setMeasuredDimension(resolveSize(Math.round(dp(150)), w), resolveSize(Math.round(dp(54)), h));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int alpha = isEnabled() ? 255 : 95;
        if (isCompactSlider()) {
            float w = getWidth(), h = getHeight();
            float left = dp(10), right = Math.max(left + dp(20), w - dp(10));
            float trackY = h * 0.44f;
            float f = bound ? Math.max(0f, Math.min(1f, fraction())) : 0f;
            float thumbX = left + (right - left) * f;

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xff272d34);
            paint.setAlpha(alpha);
            arc.set(left, trackY - dp(3), right, trackY + dp(3));
            canvas.drawRoundRect(arc, dp(3), dp(3), paint);

            paint.setColor(StudioTheme.ACCENT);
            paint.setAlpha(alpha);
            arc.set(left, trackY - dp(3), thumbX, trackY + dp(3));
            canvas.drawRoundRect(arc, dp(3), dp(3), paint);

            paint.setColor(StudioTheme.TEXT);
            paint.setAlpha(alpha);
            canvas.drawCircle(thumbX, trackY, dp(7.5f), paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1.8f));
            paint.setColor(StudioTheme.SURFACE);
            canvas.drawCircle(thumbX, trackY, dp(7.5f), paint);

            paint.setStyle(Paint.Style.FILL);
            paint.setTypeface(StudioTheme.body(getContext()));
            paint.setTextSize(sp(10));
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setColor(StudioTheme.MUTED);
            paint.setAlpha(alpha);
            String caption = AppLanguage.text(getContext(), "효과 선택 후 슬라이더로 세밀하게 조절하세요", "Select an effect and fine-tune with the slider");
            canvas.drawText(caption, w / 2f, h - dp(4), paint);
            paint.setTextAlign(Paint.Align.LEFT);
            paint.setAlpha(255);
            return;
        }

        geometry();
        float r = radius;
        paint.setTypeface(StudioTheme.medium(getContext()));
        paint.setTextSize(sp(12));
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setColor(0xffe4e5dd);
        paint.setAlpha(alpha);
        String title = bound ? label + " · " + formatter.value(value) : "";
        String fitted = TextUtils.ellipsize(title, new android.text.TextPaint(paint), Math.max(0, getWidth() - dp(8)), TextUtils.TruncateAt.END).toString();
        canvas.drawText(fitted, cx, Math.max(sp(14), cy - r - dp(9)), paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xff151a17);
        paint.setAlpha(alpha);
        canvas.drawCircle(cx, cy, r * .88f, paint);
        paint.setStrokeCap(Paint.Cap.ROUND);
        for (int i = 0; i <= 30; i++) {
            double angle = Math.toRadians(135 + i * 9);
            float co = (float) Math.cos(angle), si = (float) Math.sin(angle);
            paint.setColor(i % 5 == 0 ? 0xffb0b5a5 : 0xff59625a);
            paint.setAlpha(alpha);
            paint.setStrokeWidth(dp(i % 5 == 0 ? 1.4f : .7f));
            float inner = r * (i % 5 == 0 ? .91f : .94f);
            canvas.drawLine(cx + co * inner, cy + si * inner, cx + co * r, cy + si * r, paint);
        }
        arc.set(cx - r * .81f, cy - r * .81f, cx + r * .81f, cy + r * .81f);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(3.5f));
        paint.setColor(0xff46513c);
        paint.setAlpha(alpha);
        canvas.drawArc(arc, 135, 270, false, paint);
        paint.setColor(StudioTheme.ACCENT);
        paint.setAlpha(alpha);
        canvas.drawArc(arc, 135, bound ? 270 * fraction() : 0, false, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xff101511);
        paint.setAlpha(alpha);
        canvas.drawCircle(cx, cy, r * .80f, paint);
        for (int i = 0; i < 64; i++) {
            double angle = 2 * Math.PI * i / 64;
            float x = (float) Math.cos(angle), y = (float) Math.sin(angle);
            paint.setColor(i % 2 == 0 ? 0xff343b32 : 0xff202720);
            paint.setAlpha(alpha);
            paint.setStrokeWidth(dp(.8f));
            canvas.drawLine(cx + x * r * .76f, cy + y * r * .76f, cx + x * r * .80f, cy + y * r * .80f, paint);
        }
        paint.setColor(0xff202720);
        paint.setAlpha(alpha);
        canvas.drawCircle(cx, cy, r * .74f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1));
        paint.setColor(0xff414b3b);
        paint.setAlpha(alpha);
        canvas.drawCircle(cx, cy, r * .74f, paint);
        double angle = Math.toRadians(135 + 270 * fraction());
        float x = (float) Math.cos(angle), y = (float) Math.sin(angle);
        paint.setStrokeWidth(dp(2));
        paint.setColor(StudioTheme.ACCENT);
        paint.setAlpha(alpha);
        canvas.drawLine(cx + x * r * .60f, cy + y * r * .60f, cx + x * r * .70f, cy + y * r * .70f, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setAlpha(255);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private float angle(float x, float y) {
        return (float) Math.toDegrees(Math.atan2(y - cy, x - cx));
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled() || !bound) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                sliderMode = isCompactSlider();
                if (!sliderMode) {
                    geometry();
                    float dx = event.getX() - cx, dy = event.getY() - cy;
                    float distance = (float) Math.hypot(dx, dy);
                    if (distance > radius + dp(5)) return false;
                    angular = distance > radius * .38f;
                    lastAngle = angle(event.getX(), event.getY());
                } else {
                    angular = false;
                }
                pointerId = event.getPointerId(0);
                tracking = true;
                dragging = false;
                downX = event.getX();
                downY = event.getY();
                dragValue = value;
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                if (sliderMode) {
                    float left = dp(10), span = Math.max(1f, getWidth() - dp(20));
                    float frac = Math.max(0f, Math.min(1f, (event.getX() - left) / span));
                    change(min + frac * (max - min));
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!tracking) return false;
                int pointer = event.findPointerIndex(pointerId);
                if (pointer < 0) {
                    cancelGesture();
                    return false;
                }
                float x = event.getX(pointer), y = event.getY(pointer);
                if (!dragging && Math.hypot(x - downX, y - downY) > slop) dragging = true;
                if (sliderMode) {
                    dragging = true;
                    float left = dp(10), span = Math.max(1f, getWidth() - dp(20));
                    float frac = Math.max(0f, Math.min(1f, (x - left) / span));
                    change(min + frac * (max - min));
                } else if (dragging) {
                    if (angular) {
                        float now = angle(x, y), delta = now - lastAngle;
                        if (delta > 180) delta -= 360;
                        else if (delta < -180) delta += 360;
                        lastAngle = now;
                        dragValue = clamp(dragValue + delta / 270f * (max - min));
                    } else {
                        dragValue = clamp(value + (downY - y) / Math.max(dp(140), getHeight()) * (max - min));
                    }
                    change(dragValue);
                    if (!angular) downY = y;
                }
                return true;
            case MotionEvent.ACTION_POINTER_UP:
                if (event.getPointerId(event.getActionIndex()) == pointerId) cancelGesture();
                return true;
            case MotionEvent.ACTION_UP:
                if (!tracking) return false;
                boolean moved = dragging;
                cancelGesture();
                if (!moved) performClick();
                return true;
            case MotionEvent.ACTION_CANCEL:
                cancelGesture();
                return true;
            default:
                return tracking;
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (!isEnabled() || !bound) return super.onKeyDown(keyCode, event);
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP
                || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                || keyCode == KeyEvent.KEYCODE_PLUS
                || keyCode == KeyEvent.KEYCODE_EQUALS) {
            change(value + (max - min) * .05f);
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                || keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                || keyCode == KeyEvent.KEYCODE_MINUS) {
            change(value - (max - min) * .05f);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName(SeekBar.class.getName());
        if (bound) {
            info.setRangeInfo(AccessibilityNodeInfo.RangeInfo.obtain(AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_FLOAT, min, max, value));
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS);
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
        }
    }

    @Override
    public boolean performAccessibilityAction(int action, Bundle args) {
        if (!isEnabled() || !bound) return false;
        if (action == AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.getId() && args != null) {
            change(args.getFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE));
            return true;
        }
        if (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                || action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) {
            change(value + (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD ? 1 : -1) * (max - min) * .05f);
            return true;
        }
        return super.performAccessibilityAction(action, args);
    }
}
