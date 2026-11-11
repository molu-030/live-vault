package pro.livemic.studio;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.function.IntConsumer;

/** Shared, scrollable option sheet for app-owned choices. System pickers remain native. */
final class StudioDialogs {
    private static final int POPUP_BG = 0xff0d0f12;
    private static final int POPUP_BORDER = 0xff353b44;
    private static final float POPUP_DIM = .82f;
    private StudioDialogs() {}

    /** Decorative grip uses geometry so font scaling cannot clip a text glyph. */
    private static View handle(Activity activity) {
        FrameLayout container = new FrameLayout(activity);
        container.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        View line = new View(activity);
        line.setBackgroundColor(StudioTheme.MUTED);
        container.addView(line, new FrameLayout.LayoutParams(
                StudioTheme.dp(activity, 36), StudioTheme.dp(activity, 4), Gravity.CENTER));
        return container;
    }

    static void choose(Activity activity, String title, String[] items, int selected, IntConsumer onChoose) {
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        int pad = StudioTheme.dp(activity, StudioTheme.SPACE_16);
        FormSheet sheet = new FormSheet(activity);
        sheet.actionGap = StudioTheme.SPACE_8;
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(pad, StudioTheme.dp(activity, StudioTheme.SPACE_12), pad, StudioTheme.dp(activity, StudioTheme.SPACE_16));
        sheet.setBackground(StudioTheme.background(activity, POPUP_BG, StudioTheme.CARD_RADIUS, POPUP_BORDER));

        sheet.addView(handle(activity), new LinearLayout.LayoutParams(-1, StudioTheme.dp(activity, StudioTheme.SPACE_24)));
        TextView heading = StudioTheme.text(activity, title, StudioTheme.TYPE_HEADLINE, StudioTheme.TEXT);
        heading.setTypeface(StudioTheme.bold(activity));
        heading.setPadding(0, StudioTheme.dp(activity, StudioTheme.SPACE_8), 0, StudioTheme.dp(activity, StudioTheme.SPACE_16));
        sheet.addView(heading);

        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(false);
        LinearLayout choices = new LinearLayout(activity);
        choices.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < items.length; i++) {
            final int index = i;
            StudioButton option = StudioTheme.styled(
                    activity,
                    (i == selected ? "✓  " : "") + items[i],
                    StudioButton.OPTION,
                    () -> {
                        dialog.dismiss();
                        if (onChoose != null) onChoose.accept(index);
                    }
            );
            option.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
            option.setSingleLine(false);
            option.setContentDescription(items[i] + (i == selected ? AppLanguage.text(activity, ", 선택됨", ", selected") : ""));
            if (i == selected) {
                option.setSelected(true);
                option.setTextColor(StudioTheme.ACCENT);
            } else if (items[i].contains("삭제") || items[i].contains("지우기") || items[i].contains("Delete") || items[i].contains("Clear")) {
                option.setTextColor(StudioTheme.DANGER);
            }
            LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT);
            itemParams.bottomMargin = StudioTheme.dp(activity, 8);
            choices.addView(option, itemParams);
        }
        scroll.addView(choices);
        Rect visible = new Rect();
        activity.getWindow().getDecorView().getWindowVisibleDisplayFrame(visible);
        int availableWidth = visible.width() > 0 ? visible.width() : activity.getResources().getDisplayMetrics().widthPixels;
        StudioButton close = StudioTheme.styled(activity, AppLanguage.text(activity, "닫기", "Close"), StudioButton.QUIET, dialog::dismiss);
        sheet.content = scroll;
        sheet.addView(scroll, new LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(-1, -2);
        closeParams.topMargin = StudioTheme.dp(activity, StudioTheme.SPACE_8);
        sheet.addView(close, closeParams);
        dialog.setContentView(sheet);
        dialog.setCanceledOnTouchOutside(true);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setGravity(Gravity.BOTTOM);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams params = window.getAttributes();
            params.dimAmount = POPUP_DIM;
            window.setAttributes(params);
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        dialog.show();
        track(activity, dialog);
        if (window != null) {
            window.setLayout(Math.min(availableWidth, StudioTheme.dp(activity, 520)), ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    /** Themed form sheet; cancel restores any live preview. */
    static void form(Activity activity, String title, View content, String action, Runnable save, Runnable cancel) {
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        FormSheet sheet = new FormSheet(activity);
        sheet.setOrientation(LinearLayout.VERTICAL);
        int pad = StudioTheme.dp(activity, StudioTheme.SPACE_16);
        sheet.setPadding(pad, StudioTheme.dp(activity, StudioTheme.SPACE_12), pad, StudioTheme.dp(activity, StudioTheme.SPACE_16));
        sheet.setBackground(StudioTheme.background(activity, POPUP_BG, StudioTheme.CARD_RADIUS, POPUP_BORDER));
        sheet.addView(handle(activity), new LinearLayout.LayoutParams(-1, StudioTheme.dp(activity, StudioTheme.SPACE_24)));
        TextView heading = StudioTheme.text(activity, title, StudioTheme.TYPE_HEADLINE, StudioTheme.TEXT);
        heading.setTypeface(StudioTheme.bold(activity));
        heading.setPadding(0, StudioTheme.dp(activity, StudioTheme.SPACE_8), 0, StudioTheme.dp(activity, StudioTheme.SPACE_16));
        sheet.addView(heading);
        ScrollView scroll = scrollable(activity, content);
        sheet.content = scroll;
        sheet.addView(scroll, new LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout buttons = activity instanceof MainActivity ? ((MainActivity) activity).row() : new LinearLayout(activity);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams row = new LinearLayout.LayoutParams(-1, -2);
        row.topMargin = StudioTheme.dp(activity, StudioTheme.SPACE_24);
        sheet.addView(buttons, row);
        StudioButton close = StudioTheme.styled(activity, AppLanguage.text(activity, "취소", "Cancel"), StudioButton.QUIET, dialog::cancel);
        buttons.addView(close, new LinearLayout.LayoutParams(0, -2, 1));
        StudioButton apply = StudioTheme.styled(activity, action, StudioButton.STANDARD, () -> {
            dialog.dismiss();
            save.run();
        });
        StudioTheme.selected(apply, true);
        LinearLayout.LayoutParams applyParams = new LinearLayout.LayoutParams(0, -2, 1);
        applyParams.leftMargin = StudioTheme.dp(activity, StudioTheme.SPACE_8);
        buttons.addView(apply, applyParams);
        if (cancel != null) dialog.setOnCancelListener(d -> cancel.run());
        dialog.setContentView(sheet);
        dialog.setCanceledOnTouchOutside(true);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setGravity(Gravity.BOTTOM);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams params = window.getAttributes();
            params.dimAmount = POPUP_DIM;
            window.setAttributes(params);
        }
        dialog.show();
        track(activity, dialog);
        if (window != null) {
            Rect available = availableBounds(activity);
            window.setLayout(Math.min(available.width(), StudioTheme.dp(activity, 520)), ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    /** Keep form actions outside the scrollable body, including with the keyboard open. */
    private static final class FormSheet extends LinearLayout {
        final Activity activity;
        ScrollView content;
        int actionGap = StudioTheme.SPACE_24;
        FormSheet(Activity activity) { super(activity); this.activity = activity; }
        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            if (content == null) { super.onMeasure(widthSpec, heightSpec); return; }
            Rect available = availableBounds(activity);
            int limit = View.MeasureSpec.getMode(heightSpec) == View.MeasureSpec.UNSPECIFIED
                    ? Math.round(available.height() * .9f)
                    : Math.min(available.height(), View.MeasureSpec.getSize(heightSpec));
            View grip = getChildAt(0), heading = getChildAt(1), actions = getChildAt(getChildCount() - 1);
            LinearLayout.LayoutParams actionParams = (LinearLayout.LayoutParams) actions.getLayoutParams();
            grip.getLayoutParams().height = StudioTheme.dp(activity, StudioTheme.SPACE_24);
            heading.setPadding(0, StudioTheme.dp(activity, StudioTheme.SPACE_8), 0, StudioTheme.dp(activity, StudioTheme.SPACE_16));
            actionParams.topMargin = StudioTheme.dp(activity, actionGap);
            setPadding(getPaddingLeft(), StudioTheme.dp(activity, StudioTheme.SPACE_12), getPaddingRight(), StudioTheme.dp(activity, StudioTheme.SPACE_16));
            ViewGroup.LayoutParams body = content.getLayoutParams();
            int natural = 0, fixed = 0;
            // Reserve real action/text heights before assigning the body. When the IME leaves
            // little room, compact spacing in 4/8dp steps rather than compressing controls.
            for (int compact = 0; compact <= 4; compact++) {
                body.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                super.onMeasure(widthSpec, View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
                natural = content.getMeasuredHeight();
                fixed = getMeasuredHeight() - natural;
                int minimumBody = Math.min(natural, StudioTheme.dp(activity, StudioTheme.TOUCH_TARGET));
                if (content.getChildCount() > 0 && content.getChildAt(0) instanceof ViewGroup) {
                    ViewGroup controls = (ViewGroup) content.getChildAt(0);
                    if (controls.getChildCount() > 0)
                        minimumBody = Math.min(natural, Math.max(minimumBody, controls.getChildAt(controls.getChildCount() - 1).getMeasuredHeight()));
                }
                if (limit - fixed >= minimumBody || compact == 4) break;
                if (compact == 0) actionParams.topMargin = StudioTheme.dp(activity, StudioTheme.SPACE_8);
                else if (compact == 1) heading.setPadding(0, StudioTheme.dp(activity, StudioTheme.SPACE_8), 0, StudioTheme.dp(activity, StudioTheme.SPACE_8));
                else if (compact == 2) grip.getLayoutParams().height = StudioTheme.dp(activity, StudioTheme.SPACE_16);
                else setPadding(getPaddingLeft(), StudioTheme.dp(activity, StudioTheme.SPACE_8), getPaddingRight(), StudioTheme.dp(activity, StudioTheme.SPACE_8));
            }
            body.height = Math.min(natural, Math.max(0, limit - fixed));
            super.onMeasure(widthSpec, heightSpec);
        }
    }

    private static Rect availableBounds(Activity activity) {
        Rect available = new Rect();
        activity.getWindow().getDecorView().getWindowVisibleDisplayFrame(available);
        if (available.width() <= 0 || available.height() <= 0) {
            View decor = activity.getWindow().getDecorView();
            int width = decor.getWidth() > 0 ? decor.getWidth() : activity.getResources().getDisplayMetrics().widthPixels;
            int height = decor.getHeight() > 0 ? decor.getHeight() : activity.getResources().getDisplayMetrics().heightPixels;
            available.set(0, 0, width, height);
        }
        return available;
    }

    static ScrollView scrollable(Activity activity, View content) {
        ScrollView scroll = new ScrollView(activity);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(content);
        return scroll;
    }

    static void track(Activity activity, Dialog dialog) {
        if (activity instanceof MainActivity) ((MainActivity) activity).trackBackWindow(dialog);
    }

    static AlertDialog.Builder alert(Activity activity) {
        return trackedBuilder(activity, 0);
    }

    private static AlertDialog.Builder trackedBuilder(Activity activity, int theme) {
        return new AlertDialog.Builder(activity, theme) {
            @Override public AlertDialog show() {
                AlertDialog dialog = super.show();
                track(activity, dialog);
                return dialog;
            }
        };
    }

    static AlertDialog.Builder darkAlert(Activity activity) {
        return trackedBuilder(activity, android.R.style.Theme_DeviceDefault_Dialog_Alert);
    }

    static AlertDialog showDark(Activity activity, AlertDialog dialog) {
        dialog.show();
        track(activity, dialog);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(StudioTheme.background(activity, POPUP_BG, StudioTheme.CARD_RADIUS, POPUP_BORDER));
            View content = window.getDecorView().findViewById(android.R.id.content);
            if (content != null) {
                content.setBackground(StudioTheme.background(activity, POPUP_BG, StudioTheme.CARD_RADIUS, POPUP_BORDER));
            }
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams params = window.getAttributes();
            params.dimAmount = POPUP_DIM;
            window.setAttributes(params);
        }
        for (int which : new int[]{AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL}) {
            android.widget.Button action = dialog.getButton(which);
            if (action == null) continue;
            StudioTheme.applyTypography(action, StudioTheme.TYPE_BODY);
            action.setAllCaps(false);
            action.setSingleLine(false);
            action.setMinHeight(StudioTheme.dp(activity, StudioTheme.TOUCH_TARGET));
            action.setPadding(StudioTheme.dp(activity, StudioTheme.BUTTON_PADDING_HORIZONTAL), StudioTheme.dp(activity, StudioTheme.BUTTON_PADDING_VERTICAL),
                    StudioTheme.dp(activity, StudioTheme.BUTTON_PADDING_HORIZONTAL), StudioTheme.dp(activity, StudioTheme.BUTTON_PADDING_VERTICAL));
            action.setBackground(StudioTheme.background(activity, StudioTheme.RAISED, StudioTheme.BUTTON_RADIUS));
        }
        android.widget.Button pos = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (pos != null) pos.setTextColor(StudioTheme.ACCENT);
        android.widget.Button neg = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        if (neg != null) neg.setTextColor(StudioTheme.TEXT);
        android.widget.Button neu = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
        if (neu != null) neu.setTextColor(StudioTheme.MUTED);
        return dialog;
    }

    static void confirmDanger(Activity activity, String title, String message, String action, Runnable confirmed) {
        AlertDialog dialog = darkAlert(activity)
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton(AppLanguage.text(activity, "취소", "Cancel"), null)
                .setPositiveButton(action, (d, w) -> confirmed.run())
                .create();
        dialog.setOnShowListener(ignored -> {
            android.widget.Button pos = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (pos != null) pos.setTextColor(StudioTheme.DANGER);
        });
        showDark(activity, dialog);
        android.widget.Button pos = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (pos != null) pos.setTextColor(StudioTheme.DANGER);
    }
}
