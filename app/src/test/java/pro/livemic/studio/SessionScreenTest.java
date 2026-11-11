package pro.livemic.studio;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowLooper;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,instrumentedPackages={"pro.livemic.studio"},shadows={StudioScreenshotTest.Audio.class,StudioScreenshotTest.Composer.class,StudioScreenshotTest.Harmony.class,SessionScreenTest.Session.class,YouTubePlayerTest.OfflineWeb.class})
@LooperMode(LooperMode.Mode.PAUSED)
public class SessionScreenTest {
    @org.robolectric.annotation.internal.DoNotInstrument
    @Implements(NativeSession.class)
    public static class Session {
        static long[] current={0,0,0,0,48000,0,0,0,-1,0,48000};
        @Implementation public static void __staticInitializer__(){}
        @Implementation public static long[] state(){return current.clone();}
        @Implementation public static boolean transport(int command){current[1]=command;return true;}
        @Implementation public static void setEnabled(boolean enabled){current[0]=enabled?1:0;}
        @Implementation public static boolean seek(long frame){current[2]=frame;return true;}
        @Implementation public static void recordBus(boolean enabled){}
        @Implementation public static void finishCapture(){}
        @Implementation public static boolean armCapture(long token){return true;}
        @Implementation public static boolean prepare(String[] paths,long[] clips,double[] gains,long duration,double bpm){current[3]=duration;return true;}
    }
    MainActivity a;
    @Before public void setup()throws Exception{
        ExitCoordinator.beginLaunch();
        AppLanguage.set(RuntimeEnvironment.getApplication(),true);
        AudioService.active=false;AudioService.playbackActive=false;AudioService.youtubeSelected=false;RecordingManager.active=false;
        Session.current=new long[]{0,0,0,0,48000,0,0,0,-1,0,48000};
        a=Robolectric.buildActivity(MainActivity.class).setup().get();a.handler.removeCallbacks(a.tick);
        a.session.worker.submit(()->{}).get();a.composer.worker.submit(()->{}).get();a.harmony.worker.submit(()->{}).get();
        ShadowLooper.idleMainLooper();a.handler.removeCallbacks(a.tick);
        a.session.project=new SessionProject();a.session.ready=true;a.session.busy=false;a.session.error="";a.session.message="";
    }
    @After public void cleanup(){a.handler.removeCallbacksAndMessages(null);a.youtube.destroy();a.io.shutdown();a.session.worker.shutdownNow();AppLanguage.set(RuntimeEnvironment.getApplication(),false);}
    @Test public void sessionsAreReachableWithoutAddingABottomTab(){
        assertEquals(3,a.nav.getChildCount());a.chooseTab(10);assertNotNull(a.page.findViewWithTag("session-lcd"));assertEquals(3,a.nav.getChildCount());
        a.chooseLibraryFilter(2);assertEquals(4,a.tab);assertEquals(2,a.libraryFilter);a.onBackPressed();assertEquals(10,a.tab);
    }
    @Test public void sessionDestinationAndLibraryFilterSurviveSavedNavigation(){
        a.chooseLibraryFilter(2);a.chooseTab(10);Bundle saved=new Bundle();a.navigation.save(saved,new BackNavigation.Page(10,1,2,48));
        BackNavigation restored=new BackNavigation();BackNavigation.Page page=restored.restore(saved);assertEquals(10,page.tab);assertEquals(2,page.filter);assertEquals(48,page.scroll);assertEquals(2,restored.previous().filter);
    }
    @Test public void pendingRecoveryRemainsVisibleWithoutStartingMicrophone()throws Exception{
        SessionProject p=a.session.project;SessionProject.Track track=new SessionProject.Track();p.tracks.add(track);p.pendingTarget=new org.json.JSONObject().put("takeId","recovered");
        a.chooseTab(10);assertTrue(contains(a.page,"Unfinished recording recovery"));assertTrue(contains(a.page,"Attach at saved session position"));assertTrue(contains(a.page,"Discard target and keep original"));assertFalse(a.sessionScreen.record.isEnabled());assertFalse(AudioService.active);assertFalse(a.session.arming);
    }
    @Test public void captureHasExplicitFinishAndPositionEditingLocks(){
        a.session.capturing=true;a.chooseTab(10);assertEquals("Finish recording",a.sessionScreen.record.getText().toString());assertFalse(a.sessionScreen.play.isEnabled());assertFalse(a.sessionScreen.position.isEnabled());a.session.capturing=false;
    }
    @Test public void takeImportOffersVocalOnlyWhenRetainedAndNamesMixedBacking()throws Exception{
        a.sessionScreen.takeSource(new org.json.JSONObject().put("id","fixture").put("sourceVocalRetained",false));
        android.app.Dialog dialog=org.robolectric.shadows.ShadowDialog.getLatestDialog();
        assertTrue(contains(dialog.getWindow().getDecorView(),"Original mix · may include backing"));assertFalse(contains(dialog.getWindow().getDecorView(),"Vocal only"));dialog.dismiss();
        a.sessionScreen.takeSource(new org.json.JSONObject().put("id","fixture").put("sourceVocalRetained",true));
        dialog=org.robolectric.shadows.ShadowDialog.getLatestDialog();assertTrue(contains(dialog.getWindow().getDecorView(),"Vocal only"));assertTrue(contains(dialog.getWindow().getDecorView(),"Original mix · may include backing"));dialog.dismiss();
        AppLanguage.set(a,false);assertEquals("보컬",a.sessionScreen.kind("VOCAL"));assertEquals("화음",a.sessionScreen.kind("HARMONY"));assertEquals("오디오",a.sessionScreen.kind("AUDIO"));
    }
    @Test public void canonicalTimeIsIndependentOfOutputRate(){assertEquals("01:30",SessionScreen.time(48000L*90));Session.current[10]=44100;assertEquals("01:30",SessionScreen.time(48000L*90));}
    static boolean contains(View view,String value){if(view instanceof TextView&&((TextView)view).getText().toString().contains(value))return true;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)if(contains(group.getChildAt(i),value))return true;}return false;}
}
