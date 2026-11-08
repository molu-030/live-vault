package pro.livemic.studio;

import android.app.Dialog;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Switch;
import android.widget.TextView;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowLooper;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28, instrumentedPackages={"pro.livemic.studio"}, shadows={StudioScreenshotTest.Audio.class,StudioScreenshotTest.Composer.class,StudioScreenshotTest.Harmony.class})
@LooperMode(LooperMode.Mode.PAUSED)
public class SpeakerProtectionUiTest {
    MainActivity a;

    @Before public void setup() throws Exception {
        AudioService.active=false;
        AudioService.playbackActive=false;
        AudioService.echoStatus=AudioRouting.EchoStatus.OFF;
        StudioScreenshotTest.Audio.feedbackReductionDb=0;
        StudioScreenshotTest.Audio.supplied=new float[22];
        a=Robolectric.buildActivity(MainActivity.class).setup().get();
        a.handler.removeCallbacks(a.tick);
        a.composer.worker.submit(()->{}).get();
        ShadowLooper.idleMainLooper();
        a.handler.removeCallbacks(a.tick);
    }

    @After public void cleanup() {
        Dialog dialog=ShadowDialog.getLatestDialog();
        if(dialog!=null)dialog.dismiss();
        a.handler.removeCallbacksAndMessages(null);
        a.io.shutdown();
        AudioService.active=false;
        AudioService.echoStatus=AudioRouting.EchoStatus.OFF;
        StudioScreenshotTest.Audio.feedbackReductionDb=0;
        StudioScreenshotTest.Audio.supplied=null;
        AppLanguage.set(a,false);
    }

    static String text(View v) {
        String result=v instanceof TextView ? ((TextView)v).getText().toString()+"\n" : "";
        if(v instanceof ViewGroup) for(int i=0;i<((ViewGroup)v).getChildCount();i++)result+=text(((ViewGroup)v).getChildAt(i));
        return result;
    }

    static Switch echoSwitch(View v) {
        if(v instanceof Switch && ((Switch)v).getText().toString().contains("AEC"))return (Switch)v;
        if(v instanceof ViewGroup) for(int i=0;i<((ViewGroup)v).getChildCount();i++){
            Switch found=echoSwitch(((ViewGroup)v).getChildAt(i));if(found!=null)return found;
        }
        return null;
    }

    @Test public void actualAecStateIsIndependentOfPreferenceInBothLanguages() {
        for(boolean english:new boolean[]{false,true}) {
            AppLanguage.set(a,english);
            AudioService.active=true;
            for(AudioRouting.EchoStatus status:AudioRouting.EchoStatus.values()) {
                AudioService.echoStatus=status;
                // An enabled preference is not evidence that the device effect is active.
                AudioRouting.setEchoCancellationPreferred(a,true);
                String expected=status==AudioRouting.EchoStatus.ACTIVE ? (english?"Active":"활성")
                        : status==AudioRouting.EchoStatus.OFF ? (english?"Off":"꺼짐")
                        : status==AudioRouting.EchoStatus.ERROR ? (english?"Error":"오류")
                        : (english?"Unavailable":"사용 불가");
                a.chooseTab(6);
                Switch control=echoSwitch(a.page);
                assertNotNull(control);
                assertTrue(control.getText().toString(),control.getText().toString().endsWith(expected));
                assertEquals(AudioRouting.echoCancellationPreferred(a),control.isChecked());
                a.monitoringOptions();
                Dialog dialog=ShadowDialog.getLatestDialog();
                assertTrue(text(dialog.getWindow().getDecorView()).contains(": "+expected));
                dialog.dismiss();
            }
            AudioRouting.setEchoCancellationPreferred(a,false);
            AudioService.echoStatus=AudioRouting.EchoStatus.ACTIVE;
            assertEquals(english?"Off":"꺼짐",a.deviceEchoStatus());
            AudioService.active=false;
            assertEquals(english?"Off":"꺼짐",a.deviceEchoStatus());
            AudioRouting.setEchoCancellationPreferred(a,true);
            AudioService.echoStatus=AudioRouting.EchoStatus.ACTIVE;
            assertEquals(english?"Check when mic is on":"마이크 켜면 확인",a.deviceEchoStatus());
        }
    }

    @Test public void existingTickRefreshesOpenSettingsAfterEffectResult() {
        AppLanguage.set(a,true);
        AudioRouting.setEchoCancellationPreferred(a,true);
        AudioService.active=true;
        AudioService.echoStatus=AudioRouting.EchoStatus.OFF;
        a.chooseTab(6);
        Switch control=echoSwitch(a.page);
        a.monitoringOptions();
        Dialog dialog=ShadowDialog.getLatestDialog();
        assertTrue(control.getText().toString().endsWith("Off"));
        AudioService.echoStatus=AudioRouting.EchoStatus.ERROR;
        a.tick.run();
        a.handler.removeCallbacks(a.tick);
        assertTrue(control.getText().toString().endsWith("Error"));
        assertTrue(text(dialog.getWindow().getDecorView()).contains(": Error"));
        dialog.dismiss();
        AudioService.echoStatus=AudioRouting.EchoStatus.ACTIVE;
        a.tick.run();
        a.handler.removeCallbacks(a.tick);
        assertTrue(control.getText().toString().endsWith("Active"));
    }

    @Test public void reductionRetainsMonitorStateAndRejectsInvalidReadings() {
        AudioService.active=true;
        a.chooseTab(0);
        float[] stats=new float[22];stats[10]=1;stats[21]=1;
        for(boolean english:new boolean[]{false,true}) {
            AppLanguage.set(a,english);
            for(float db:new float[]{4,8}) {
                StudioScreenshotTest.Audio.feedbackReductionDb=db;
                a.liveScreen.poll(stats,true);
                String route=a.liveScreen.display.routeStatus;
                assertTrue(route,route.contains(english?"Mon on":"모니터 켜짐"));
                assertTrue(route,route.contains((english?"Protection -":"보호감쇠 -")+(int)db+"dB"));
                a.monitoringOptions();
                Dialog dialog=ShadowDialog.getLatestDialog();
                assertTrue(text(dialog.getWindow().getDecorView()).contains("-"+(int)db+"dB"));
                dialog.dismiss();
                stats[21]=0;
                a.liveScreen.poll(stats,true);
                assertTrue(a.liveScreen.display.routeStatus.contains(english?"Mon off":"모니터 꺼짐"));
                stats[21]=1;
            }
        }
        for(float db:new float[]{0,-4,Float.NaN,Float.POSITIVE_INFINITY}) {
            StudioScreenshotTest.Audio.feedbackReductionDb=db;
            assertEquals("",a.feedbackReductionLabel());
        }
        StudioScreenshotTest.Audio.feedbackReductionDb=8;
        stats[17]=1;
        a.liveScreen.poll(stats,true);
        assertFalse(a.liveScreen.display.routeStatus.contains("-8dB"));
        assertTrue(a.liveScreen.display.routeStatus.contains("Safety mute"));
        stats[17]=0;
        a.liveScreen.poll(stats,false);
        assertFalse(a.liveScreen.display.routeStatus.contains("-8dB"));
    }
}
