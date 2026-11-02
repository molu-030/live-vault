package pro.livemic.studio;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Native primitives and dark studio palette from the Live Vault UI reference. */
final class StudioTheme {
    static final int BG = 0xff0c0e11;
    static final int TOPBAR = 0xff0e1013;
    static final int SURFACE = 0xff14171b;
    static final int RAISED = 0xff1c1f24;
    static final int LCD_BG = 0xff0c100e;
    static final int LCD_BORDER = 0xff2b3428;
    static final int FINE_BG = 0xff101318;
    static final int FINE_BORDER = 0xff282e32;
    static final int CARD_BG = 0xff191d22;
    static final int CARD_BORDER = 0xff2d3138;
    static final int CARD_SELECTED_BG = 0xff22261a;
    static final int CARD_SELECTED_BORDER = 0xff68742b;
    static final int ACCENT = 0xffe7f500;
    static final int ACCENT_INK = 0xff171b08;
    static final int OLIVE = 0xffc4d181;
    static final int TEXT = 0xfff3f5f6;
    static final int MUTED = 0xff8a9098;
    static final int DANGER = 0xffea6b70;
    static final int BORDER = 0xff2b2e33;

    private static Typeface body, medium, bold, mono;

    static Typeface body(Context c) {
        if (body == null) body = c.getResources().getFont(R.font.studio_sans);
        return body;
    }

    static Typeface medium(Context c) {
        if (medium == null) medium = Typeface.create(body(c), 500, false);
        return medium;
    }

    static Typeface bold(Context c) {
        if (bold == null) bold = Typeface.create(body(c), 700, false);
        return bold;
    }

    static Typeface mono(Context c) {
        if (mono == null) mono = c.getResources().getFont(R.font.studio_numbers);
        return mono;
    }

    static float sp(Context c, float n) {
        return android.util.TypedValue.applyDimension(
                android.util.TypedValue.COMPLEX_UNIT_SP,
                n,
                c.getResources().getDisplayMetrics()
        );
    }

    static int dp(Context c, float n) {
        return Math.round(c.getResources().getDisplayMetrics().density * n);
    }

    static GradientDrawable background(Context c, int color, int radius) {
        int stroke = 0;
        if (color == RAISED) stroke = 0xff35383e;
        else if (color == SURFACE) stroke = BORDER;
        else if (color == LCD_BG) stroke = LCD_BORDER;
        else if (color == FINE_BG) stroke = FINE_BORDER;
        else if (color == CARD_BG) stroke = CARD_BORDER;
        else if (color == CARD_SELECTED_BG) stroke = CARD_SELECTED_BORDER;
        return background(c, color, radius, stroke);
    }

    static GradientDrawable background(Context c, int color, int radius, int strokeColor) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(c, radius));
        if (strokeColor != 0) {
            d.setStroke(dp(c, 1), strokeColor);
        }
        return d;
    }

    static TextView text(Context c, String value, int size, int color) {
        TextView t = new TextView(c);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setTypeface(body(c));
        t.setIncludeFontPadding(false);
        t.setFontFeatureSettings("tnum");
        t.setLineSpacing(dp(c, 3), 1);
        return t;
    }

    static Button button(Context c, String value, Runnable action) {
        StudioButton b = new StudioButton(c);
        b.setText(value);
        b.setOnClickListener(v -> action.run());
        return b;
    }

    static StudioButton styled(Context c, String value, int role, Runnable action) {
        StudioButton b = (StudioButton) button(c, value, action);
        b.role(role);
        return b;
    }

    static Button icon(Context c, String glyph, String description, Runnable action) {
        StudioButton b = styled(
                c,
                "",
                glyph.equals("play") || glyph.equals("pause") ? StudioButton.CIRCLE : StudioButton.QUIET,
                action
        );
        b.glyph(glyph);
        b.setContentDescription(description);
        b.setPadding(dp(c, 12), 0, dp(c, 12), 0);
        return b;
    }

    static void selected(Button b, boolean selected) {
        b.setBackgroundTintList(ColorStateList.valueOf(selected ? ACCENT : RAISED));
        b.setTextColor(selected ? ACCENT_INK : TEXT);
        b.setSelected(selected);
    }

    static LinearLayout card(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(c, 12);
        l.setPadding(pad, pad, pad, pad);
        l.setBackground(background(c, SURFACE, 10, BORDER));
        return l;
    }
}
