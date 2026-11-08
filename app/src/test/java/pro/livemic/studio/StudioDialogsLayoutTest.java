package pro.livemic.studio;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowLooper;
import static org.junit.Assert.*;

/** Native View measurement/drawing of forms in short windows and after IME resize. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class StudioDialogsLayoutTest {
    @After public void resetLanguage() {
        AppLanguage.set(RuntimeEnvironment.getApplication(), false);
        RuntimeEnvironment.setFontScale(1f);
    }

    @Test public void shortFormsKeepActionsReachableAndContentScrollable() {
        for (boolean english : new boolean[]{false, true}) {
            for (float scale : new float[]{1f, 1.3f}) {
                for (int[] size : new int[][]{{360, 320}, {960, 600}}) {
                    RuntimeEnvironment.setQualifiers("w" + size[0] + "dp-h" + size[1] + "dp-mdpi");
                    RuntimeEnvironment.setFontScale(scale);
                    AppLanguage.set(RuntimeEnvironment.getApplication(), english);
                    try (ActivityController<Activity> controller = Robolectric.buildActivity(Activity.class).setup()) {
                        Activity activity = controller.get();
                        LinearLayout body = new LinearLayout(activity);
                        body.setOrientation(LinearLayout.VERTICAL);
                        for (int i = 0; i < 14; i++) {
                            body.addView(StudioTheme.text(activity, english ? "Harmony mix setting " + i : "화음 믹스 설정 " + i, 18, StudioTheme.TEXT),
                                    new LinearLayout.LayoutParams(-1, 56));
                        }
                        int[] saves = {0}, cancels = {0};
                        StudioDialogs.form(activity, english ? "Voice · harmony mix" : "목소리 · 화음 믹스", body,
                                english ? "Save" : "저장", () -> saves[0]++, () -> cancels[0]++);
                        Dialog dialog = ShadowDialog.getLatestDialog();
                        LinearLayout sheet = (LinearLayout) body.getParent().getParent();
                        ScrollView scroll = (ScrollView) body.getParent();
                        View actions = sheet.getChildAt(sheet.getChildCount() - 1);
                        measureAndDraw(sheet, Math.min(520, size[0]), size[1]);
                        assertTrue("body should scroll", body.getHeight() > scroll.getHeight());
                        assertTrue("actions fit the measured window", actions.getBottom() <= sheet.getHeight() - sheet.getPaddingBottom());
                        assertTrue("actions retain touch height", actions.getHeight() >= 48);
                        int previousHeight = scroll.getHeight();
                        // Model the tighter height spec delivered when the keyboard opens.
                        measureAndDraw(sheet, Math.min(520, size[0]), size[1] - 80);
                        assertTrue(scroll.getHeight() < previousHeight);
                        assertTrue(actions.getBottom() <= sheet.getHeight() - sheet.getPaddingBottom());
                        scroll.scrollTo(0, body.getHeight());
                        Rect last = new Rect();
                        View lastSetting = body.getChildAt(body.getChildCount() - 1);
                        lastSetting.getDrawingRect(last);
                        scroll.offsetDescendantRectToMyCoords(lastSetting, last);
                        // Descendant coordinates are in the ScrollView's content space;
                        // convert to its visible viewport before testing reachability.
                        last.offset(-scroll.getScrollX(), -scroll.getScrollY());
                        assertEquals("scroll reaches the end of the body",
                                body.getHeight() - scroll.getHeight(), scroll.getScrollY());
                        assertTrue("last setting is fully visible: " + last + " in " + scroll.getHeight(),
                                last.top >= 0 && last.bottom <= scroll.getHeight());
                        assertTrue("save/cancel stay below the body", actions.getTop() >= scroll.getBottom());
                        assertTrue("save/cancel remain visible after scrolling",
                                actions.getBottom() <= sheet.getHeight() - sheet.getPaddingBottom());
                        measureAndDraw(sheet, Math.min(520, size[0]), size[1]);
                        assertEquals("body grows after keyboard closes", previousHeight, scroll.getHeight());
                        ((Button) ((ViewGroup) actions).getChildAt(1)).performClick();
                        ShadowLooper.idleMainLooper();
                        assertEquals(1, saves[0]);
                        assertEquals("save does not roll back preview", 0, cancels[0]);
                        assertFalse(dialog.isShowing());

                        StudioDialogs.form(activity, "Form", new View(activity), "Save", () -> saves[0]++, () -> cancels[0]++);
                        Dialog canceledDialog = ShadowDialog.getLatestDialog();
                        // MainActivity.closeTopBackWindow() invokes this same method.
                        // Dialog.cancel() posts OnCancelListener through a Handler, so
                        // process that message before verifying the preview rollback.
                        canceledDialog.onBackPressed();
                        ShadowLooper.idleMainLooper();
                        assertFalse("tracked-window Back closes the form", canceledDialog.isShowing());
                        assertEquals("Back invokes preview rollback exactly once", 1, cancels[0]);
                        assertEquals("Back does not apply the form", 1, saves[0]);
                        ShadowLooper.idleMainLooper();
                        assertEquals("no duplicate rollback remains queued", 1, cancels[0]);
                    }
                }
            }
        }
    }

    private void measureAndDraw(View view, int width, int height) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.AT_MOST));
        view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
        Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bitmap));
        bitmap.recycle();
    }
}
