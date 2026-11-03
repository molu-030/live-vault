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
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.function.IntConsumer;

/** Shared, scrollable option sheet for app-owned choices. System pickers remain native. */
final class StudioDialogs {
    private static final int POPUP_BG = 0xff0d0f12;
    private static final int POPUP_BORDER = 0xff353b44;
    private static final float POPUP_DIM = .82f;
    private StudioDialogs() {}

    static void choose(Activity activity, String title, String[] items, int selected, IntConsumer onChoose) {
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        int pad = StudioTheme.dp(activity, 20);
        LinearLayout sheet = new LinearLayout(activity);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(pad, StudioTheme.dp(activity, 12), pad, StudioTheme.dp(activity, 18));
        sheet.setBackground(StudioTheme.background(activity, POPUP_BG, 18, POPUP_BORDER));

        TextView handle = StudioTheme.text(activity, "━━━━", 16, StudioTheme.MUTED);
        handle.setGravity(Gravity.CENTER);
        sheet.addView(handle, new LinearLayout.LayoutParams(-1, StudioTheme.dp(activity, 20)));
        TextView heading = StudioTheme.text(activity, title, 18, StudioTheme.TEXT);
        heading.setTypeface(StudioTheme.bold(activity));
        heading.setPadding(0, StudioTheme.dp(activity, 10), 0, StudioTheme.dp(activity, 12));
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
        int availableHeight = visible.height() > 0 ? visible.height() : activity.getResources().getDisplayMetrics().heightPixels;
        int availableWidth = visible.width() > 0 ? visible.width() : activity.getResources().getDisplayMetrics().widthPixels;
        int widthSpec = View.MeasureSpec.makeMeasureSpec(
                Math.max(1, Math.min(availableWidth, StudioTheme.dp(activity, 520)) - 2 * pad),
                View.MeasureSpec.EXACTLY
        );
        heading.measure(widthSpec, View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        choices.measure(widthSpec, View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int fixed = sheet.getPaddingTop() + sheet.getPaddingBottom() + heading.getMeasuredHeight() + StudioTheme.dp(activity, 68);
        int maxList = Math.max(StudioTheme.dp(activity, 48), (int) (availableHeight * .9f) - fixed);
        int listHeight = Math.min(maxList, choices.getMeasuredHeight());
        sheet.addView(scroll, new LinearLayout.LayoutParams(-1, listHeight));
        StudioButton close = StudioTheme.styled(activity, AppLanguage.text(activity, "닫기", "Close"), StudioButton.QUIET, dialog::dismiss);
        sheet.addView(close, new LinearLayout.LayoutParams(-1, StudioTheme.dp(activity, 48)));
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
        LinearLayout sheet = new LinearLayout(activity);
        sheet.setOrientation(LinearLayout.VERTICAL);
        int pad = StudioTheme.dp(activity, 20);
        sheet.setPadding(pad, StudioTheme.dp(activity, 12), pad, StudioTheme.dp(activity, 18));
        sheet.setBackground(StudioTheme.background(activity, POPUP_BG, 18, POPUP_BORDER));
        TextView handle = StudioTheme.text(activity, "━━━━", 16, StudioTheme.MUTED);
        handle.setGravity(Gravity.CENTER);
        sheet.addView(handle, new LinearLayout.LayoutParams(-1, StudioTheme.dp(activity, 20)));
        TextView heading = StudioTheme.text(activity, title, 18, StudioTheme.TEXT);
        heading.setTypeface(StudioTheme.bold(activity));
        heading.setPadding(0, StudioTheme.dp(activity, 10), 0, StudioTheme.dp(activity, 16));
        sheet.addView(heading);
        sheet.addView(content, new LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout buttons = new LinearLayout(activity);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams row = new LinearLayout.LayoutParams(-1, StudioTheme.dp(activity, 48));
        row.topMargin = StudioTheme.dp(activity, 20);
        sheet.addView(buttons, row);
        StudioButton close = StudioTheme.styled(activity, AppLanguage.text(activity, "취소", "Cancel"), StudioButton.QUIET, dialog::cancel);
        buttons.addView(close, new LinearLayout.LayoutParams(0, -1, 1));
        StudioButton apply = StudioTheme.styled(activity, action, StudioButton.STANDARD, () -> {
            dialog.dismiss();
            save.run();
        });
        StudioTheme.selected(apply, true);
        buttons.addView(apply, new LinearLayout.LayoutParams(0, -1, 1));
        if (cancel != null) dialog.setOnCancelListener(d -> cancel.run());
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
        }
        dialog.show();
        track(activity, dialog);
        if (window != null) {
            window.setLayout(Math.min(activity.getResources().getDisplayMetrics().widthPixels, StudioTheme.dp(activity, 520)), ViewGroup.LayoutParams.WRAP_CONTENT);
        }
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
            window.setBackgroundDrawable(StudioTheme.background(activity, POPUP_BG, 18, POPUP_BORDER));
            View content = window.getDecorView().findViewById(android.R.id.content);
            if (content != null) {
                content.setBackground(StudioTheme.background(activity, POPUP_BG, 18, POPUP_BORDER));
            }
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams params = window.getAttributes();
            params.dimAmount = POPUP_DIM;
            window.setAttributes(params);
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
