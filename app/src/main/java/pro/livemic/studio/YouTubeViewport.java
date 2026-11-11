package pro.livemic.studio;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

/** Measures the official player's viewport; the iframe owns video letterboxing. */
final class YouTubeViewport extends FrameLayout {
    final int minimum;
    private final TextView fallback;

    YouTubeViewport(Context context) {
        super(context);
        minimum = Math.round(200 * context.getResources().getDisplayMetrics().density);
        setBackgroundColor(Color.BLACK);
        setClipChildren(true);
        fallback = new TextView(context);
        fallback.setText(AppLanguage.text(context, "동영상을 표시하려면 창을 넓히세요.", "Widen the window to display the video."));
        fallback.setTextColor(Color.WHITE);
        fallback.setGravity(Gravity.CENTER);
        addView(fallback, new FrameLayout.LayoutParams(-1, -1));
    }

    static int heightForWidth(int width, int minimum) {
        return Math.max(minimum, (int) Math.ceil(width * 9d / 16d));
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getMode(widthSpec) == MeasureSpec.UNSPECIFIED
                ? minimum : MeasureSpec.getSize(widthSpec);
        boolean fits = width >= minimum;
        fallback.setVisibility(fits ? View.GONE : View.VISIBLE);
        // A sub-policy-size window gets a native fallback, never an overflowing player.
        for (int i = 1; i < getChildCount(); i++) {
            getChildAt(i).setVisibility(fits ? View.VISIBLE : View.INVISIBLE);
        }
        super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(heightForWidth(width, minimum), MeasureSpec.EXACTLY));
    }
}
