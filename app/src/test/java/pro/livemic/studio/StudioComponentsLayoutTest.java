package pro.livemic.studio;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Bundle;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;

/** Text must remain complete when common controls grow at the system font scale. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class StudioComponentsLayoutTest {
    @After public void reset(){RuntimeEnvironment.setFontScale(1f);}
    private void layout(View view,int width){
        view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
        view.layout(0,0,view.getMeasuredWidth(),view.getMeasuredHeight());
        Bitmap bitmap=Bitmap.createBitmap(view.getWidth(),view.getHeight(),Bitmap.Config.ARGB_8888);view.draw(new Canvas(bitmap));bitmap.recycle();
    }
    @Test public void longButtonLabelsGrowInsteadOfClippingAtLargeFontScale(){
        RuntimeEnvironment.setQualifiers("w320dp-h480dp-mdpi");RuntimeEnvironment.setFontScale(1.3f);
        try(ActivityController<Activity> controller=Robolectric.buildActivity(Activity.class).setup()){
            Activity context=controller.get();
            for(String label:new String[]{"Apply effects and save a new copy","효과를 적용하고 새 복사본으로 저장"})for(int width:new int[]{112,160,240}){
                int[] clicks={0};StudioButton button=(StudioButton)StudioTheme.button(context,label,()->clicks[0]++);layout(button,width);
                assertTrue(button.getHeight()>=StudioTheme.dp(context,48));assertTrue(button.getLayout().getLineCount()>1);
                int last=button.getLayout().getLineCount()-1;
                assertEquals(label.length(),button.getLayout().getLineEnd(last));assertEquals(0,button.getLayout().getEllipsisCount(last));
                assertTrue("All button lines fit between the 12dp vertical insets",button.getHeight()-button.getPaddingTop()-button.getPaddingBottom()>=button.getLayout().getHeight());
                assertEquals(StudioTheme.sp(context,14),button.getTextSize(),.01f);button.performClick();assertEquals(1,clicks[0]);
            }
        }
    }
    @Test public void narrowFaderKeepsWholeLabelAndValueAndStillUpdatesTheParameter(){
        RuntimeEnvironment.setQualifiers("w320dp-h480dp-mdpi");RuntimeEnvironment.setFontScale(1.3f);
        try(ActivityController<Activity> controller=Robolectric.buildActivity(Activity.class).setup()){
            Activity context=controller.get();
            for(String label:new String[]{"Natural performance timing","연주의 자연스러운 타이밍"})for(int width:new int[]{96,144,288}){
                float[] received={-1};StudioFader fader=new StudioFader(context,label,0,1,.35f,v->Math.round(v*100)+"%",v->received[0]=v);layout(fader,width);
                int last=fader.labelLayout.getLineCount()-1;
                assertEquals("Full control label is rendered",label.length(),fader.labelLayout.getLineEnd(last));assertEquals(0,fader.labelLayout.getEllipsisCount(last));
                assertEquals("35%",fader.valueLayout.getText().toString());assertTrue(fader.getHeight()>=StudioTheme.dp(context,48));
                if(width==96)assertTrue("Narrow labels wrap naturally",fader.labelLayout.getLineCount()>1);
                Bundle progress=new Bundle();progress.putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE,.9f);
                assertTrue(fader.performAccessibilityAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.getId(),progress));
                layout(fader,width);assertEquals(.9f,received[0],.0001f);assertEquals("90%",fader.valueLayout.getText().toString());
                assertTrue(fader.getContentDescription().toString().contains("90%"));
            }
        }
    }
}
