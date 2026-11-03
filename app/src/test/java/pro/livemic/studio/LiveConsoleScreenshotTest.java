package pro.livemic.studio;

import android.app.Dialog;
import android.graphics.Rect;
import android.view.*;
import android.widget.*;
import java.io.File;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowLooper;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35, instrumentedPackages={"pro.livemic.studio"}, shadows={StudioScreenshotTest.Audio.class, StudioScreenshotTest.Composer.class, StudioScreenshotTest.Harmony.class})
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class LiveConsoleScreenshotTest {
    static void checkControls(MainActivity a, View view) {
        if (view.getVisibility() != View.VISIBLE) return;
        if (view instanceof Button || view instanceof SeekBar || view instanceof StudioKnob) {
            Rect bounds = StudioScreenshotTest.bounds(a, view);
            assertTrue("Control width: " + view.getContentDescription() + " " + bounds, bounds.width() >= 48);
            assertTrue("Control height: " + view.getContentDescription() + " " + bounds, bounds.height() >= 48);
            assertTrue("Control stays inside safe area: " + bounds, bounds.bottom <= a.root.getHeight() - a.root.getPaddingBottom());
        }
        if (view instanceof TextView && !(view instanceof EditText)) {
            TextView text = (TextView)view;
            android.text.Layout layout = text.getLayout();
            if (layout != null && text.length() > 0) {
                int last = layout.getLineCount() - 1;
                assertEquals("Full value visible: " + text.getText(), text.length(), layout.getLineEnd(last));
                assertEquals("No ellipsis: " + text.getText(), 0, layout.getEllipsisCount(last));
                assertTrue("Text height: " + text.getText(), layout.getHeight() <= text.getHeight()-text.getCompoundPaddingTop()-text.getCompoundPaddingBottom());
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group=(ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++) checkControls(a,group.getChildAt(i));
        }
    }

    static void tick(MainActivity a) {
        a.tick.run();
        a.handler.removeCallbacks(a.tick);
    }

    @Test public void liveDashboardInBothLanguagesSizesAndTextScales() throws Exception {
        File out=new File(System.getProperty("user.dir"),"build/outputs/studio-previews");
        out.mkdirs();
        java.lang.reflect.Field field=AudioService.class.getDeclaredField("countdown");
        field.setAccessible(true);
        RecordingCountdown countdown=(RecordingCountdown)field.get(null);
        try {
            for(boolean english:new boolean[]{false,true}) for(float scale:new float[]{1f,1.3f}) for(int width:new int[]{412,360}) {
                int height=width==412?893:800;
                RuntimeEnvironment.setQualifiers("w"+width+"dp-h"+height+"dp-mdpi");
                RuntimeEnvironment.setFontScale(scale);
                AppLanguage.set(RuntimeEnvironment.getApplication(),english);
                StudioScreenshotTest.Composer.current=new double[]{0,0,0,0,32,-1,-1,0,0,0};
                AudioService.active=false;
                AudioService.playbackActive=false;
                AudioService.composerRequested=false;
                RecordingManager.active=false;
                RecordingManager.paused=false;
                countdown.cancel();
                MainActivity a=Robolectric.buildActivity(MainActivity.class).setup().get();
                a.handler.removeCallbacks(a.tick);
                a.composer.worker.submit(()->{}).get();
                ShadowLooper.idleMainLooper();
                a.handler.removeCallbacks(a.tick);
                a.root.dispatchApplyWindowInsets(new WindowInsets.Builder().setInsets(WindowInsets.Type.systemBars(),android.graphics.Insets.of(0,36,0,24)).build());
                a.chooseTab(0);
                String prefix="v386-knobs-"+(english?"en":"ko")+"-"+width+(scale==1?"-default":"-large");
                float[] stats=new float[22];
                stats[2]=-1;
                StudioScreenshotTest.Audio.supplied=stats;
                tick(a);
                StudioScreenshotTest.capture(a,width,height,new File(out,prefix+"-idle.png"),false);
                if ((!english && width==360 && scale==1.3f) || (english && width==412 && scale==1f)) {
                    a.drawer();
                    assertNotNull(a.drawerPopup);
                    StudioScreenshotTest.capture(a,width,height,new File(out,prefix+"-menu.png"),true);
                    assertNotNull(StudioScreenshotTest.findDescription(a.drawerPopup.getContentView(),english?"Backing,":"반주,"));
                    a.drawerPopup.dismiss();
                }
                Rect rec=StudioScreenshotTest.bounds(a,a.recButton);
                assertEquals(3,a.nav.getChildCount());
                assertTrue("Footer follows dashboard viewport",rec.top>=StudioScreenshotTest.bounds(a,a.pageScroll).bottom);
                assertTrue("Footer clears system navigation",rec.bottom<=height-24);
                assertTrue("Dashboard fits viewport",a.page.getHeight()<=a.pageScroll.getHeight());
                a.pageScroll.scrollTo(0,a.page.getHeight());
                assertEquals("No dashboard scrolling",0,a.pageScroll.getScrollY());
                assertEquals(rec,StudioScreenshotTest.bounds(a,a.recButton));
                checkControls(a,a.page);
                checkControls(a,a.transport);
                Rect reverb=StudioScreenshotTest.bounds(a,a.liveScreen.reverbVolume);
                Rect echo=StudioScreenshotTest.bounds(a,a.liveScreen.echoVolume);
                assertEquals("Compact main knobs share a row",reverb.top,echo.top);
                assertTrue("Separate main knob touch targets",reverb.right<=echo.left);
                assertTrue("Reverb exposes its visible amount",a.liveScreen.reverbVolume.getContentDescription().toString().contains("%"));
                assertTrue("Echo exposes its visible amount",a.liveScreen.echoVolume.getContentDescription().toString().contains("%"));
                ViewGroup micRow = (ViewGroup) a.liveScreen.micButton.getParent();
                ViewGroup inputRow = (ViewGroup) a.liveScreen.inputButton.getParent();
                assertEquals(2, micRow.getChildCount());
                assertEquals(2, inputRow.getChildCount());
                assertTrue(StudioScreenshotTest.bounds(a, a.liveScreen.micButton).top < StudioScreenshotTest.bounds(a, a.liveScreen.inputButton).top);
                assertTrue(StudioScreenshotTest.bounds(a, a.liveScreen.monitorToggle).top < StudioScreenshotTest.bounds(a, a.outputButton).top);

                float[] original=a.values.clone();
                a.values[0]=2;a.values[1]=1;a.values[2]=1;a.values[4]=.8f;
                a.liveScreen.sync();
                StudioScreenshotTest.capture(a,width,height,new File(out,prefix+"-max-values.png"),false);
                checkControls(a,a.page);
                for(int key:new int[]{0,1,2,4})a.values[key]=original[key];
                a.liveScreen.sync();

                AudioService.active=true;
                stats[0]=440;stats[2]=69;stats[3]=.1f;stats[4]=.01f;stats[10]=1;stats[16]=1;stats[21]=1;
                a.selectedFile="Vocal practice backing.wav";
                tick(a);
                StudioScreenshotTest.capture(a,width,height,new File(out,prefix+"-live-backing.png"),false);
                assertEquals(-20,a.liveScreen.display.inputDb,.01);
                assertEquals(-40,a.liveScreen.display.outputDb,.01);
                assertEquals("A4",a.liveScreen.display.note);

                RecordingManager.active=true;stats[8]=12;
                tick(a);
                StudioScreenshotTest.capture(a,width,height,new File(out,prefix+"-recording.png"),false);
                assertTrue(a.liveScreen.display.recordClock.contains("00:12"));
                assertFalse(a.liveScreen.video.isEnabled());
                RecordingManager.paused=true;
                tick(a);
                StudioScreenshotTest.capture(a,width,height,new File(out,prefix+"-paused.png"),false);
                assertTrue(a.liveScreen.display.recordClock.contains("00:12"));
                RecordingManager.paused=false;RecordingManager.active=false;stats[17]=1;
                tick(a);
                StudioScreenshotTest.capture(a,width,height,new File(out,prefix+"-safety.png"),false);
                assertTrue(a.liveScreen.display.routeStatus.toLowerCase(java.util.Locale.US).contains(english?"safety":"보호"));
                stats[17]=0;
                countdown.arm(5,android.os.SystemClock.elapsedRealtime());
                tick(a);
                StudioScreenshotTest.capture(a,width,height,new File(out,prefix+"-countdown.png"),false);
                assertEquals(english?"COUNTDOWN":"예약",a.liveScreen.display.recordStatus);
                assertFalse(a.liveScreen.video.isEnabled());
                countdown.cancel();tick(a);
                a.liveScreen.sourceButton.performClick();
                Dialog source=ShadowDialog.getLatestDialog();assertNotNull(source);
                StudioScreenshotTest.captureSheet(a,source.getWindow().getDecorView().findViewById(android.R.id.content),new File(out,prefix+"-source-sheet.png"));source.dismiss();
                a.liveScreen.detailsButton.performClick();
                Dialog details=ShadowDialog.getLatestDialog();assertNotNull(details);
                StudioScreenshotTest.captureSheet(a,details.getWindow().getDecorView().findViewById(android.R.id.content),new File(out,prefix+"-details-sheet.png"));details.dismiss();
                a.handler.removeCallbacks(a.tick);a.io.shutdown();StudioScreenshotTest.Audio.supplied=null;AudioService.active=false;
            }
        } finally {
            countdown.cancel();RecordingManager.active=false;RecordingManager.paused=false;AudioService.active=false;
            AudioService.composerRequested=false;StudioScreenshotTest.Audio.supplied=null;AppLanguage.set(RuntimeEnvironment.getApplication(),false);RuntimeEnvironment.setFontScale(1f);
        }
    }
}
