package pro.livemic.studio;

import android.app.AlertDialog;
import android.view.View;
import android.widget.Button;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowLooper;
import static org.junit.Assert.*;

/** Exercises the user-visible entry points without microphone permission or YouTube networking. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=28, instrumentedPackages={"pro.livemic.studio"}, shadows={StudioScreenshotTest.Audio.class, StudioScreenshotTest.Composer.class, StudioScreenshotTest.Harmony.class, YouTubePlayerTest.OfflineWeb.class})
@LooperMode(LooperMode.Mode.PAUSED)
public class BackingNavigationTest {
    MainActivity a;
    @Before public void setup() throws Exception {
        RuntimeEnvironment.getApplication().getSharedPreferences("MainActivity",0).edit().clear().commit();
        AppLanguage.set(RuntimeEnvironment.getApplication(),true);
        AudioService.active=false;AudioService.playbackActive=false;AudioService.youtubeSelected=false;AudioService.composerRequested=false;
        RecordingManager.active=false;RecordingManager.paused=false;
        StudioScreenshotTest.Audio.supplied=null;
        StudioScreenshotTest.Composer.current=new double[]{0,0,0,0,32,-1,-1,0,0,0};
        StudioScreenshotTest.Harmony.current=new double[]{0,0,0,0,-1,-1,0,0,-1,-1,-1,0,0,-1};
        a=Robolectric.buildActivity(MainActivity.class).setup().get();a.handler.removeCallbacks(a.tick);
        a.composer.worker.submit(()->{}).get();ShadowLooper.idleMainLooper();a.handler.removeCallbacks(a.tick);
        Shadows.shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(android.Manifest.permission.RECORD_AUDIO);
        a.chooseTab(0);
    }
    @After public void cleanup() {
        a.handler.removeCallbacks(a.tick);a.youtube.destroy();a.io.shutdown();
        AudioService.active=false;AudioService.playbackActive=false;AudioService.youtubeSelected=false;AudioService.composerRequested=false;
        RecordingManager.active=false;RecordingManager.paused=false;StudioScreenshotTest.Audio.supplied=null;
        AppLanguage.set(RuntimeEnvironment.getApplication(),false);
    }
    static Button chooser(MainActivity a) {
        Button result=StudioScreenshotTest.findButtonContaining(a.page,"Choose backing");
        assertNotNull("Live has an explicit backing chooser",result);return result;
    }
    void click(String label) {
        Button b=StudioScreenshotTest.findButtonContaining(a.page,label);assertNotNull(label,b);assertTrue(b.performClick());
    }
    void drawer(String label) {
        a.drawer();View row=StudioScreenshotTest.findDescription(a.drawerPopup.getContentView(),label+",");
        assertNotNull("Actual clickable drawer row: "+label,row);assertTrue(row.performClick());assertNull(a.drawerPopup);
    }
    @Test public void drawerBackingOffersAllThreeSourcesAndReturnsToLive() {
        drawer("Backing");assertEquals(1,a.tab);
        for(String source:new String[]{"My files","Composer","Online"}) {
            click(source);assertEquals(1,a.tab);
            assertEquals(source,source.equals("My files")?1:source.equals("Composer")?0:2,a.sourceTab);
            assertNotNull(StudioScreenshotTest.findButtonContaining(a.page,"To Live"));
        }
        click("My files");assertNotNull(StudioScreenshotTest.findButtonContaining(a.page,"Import backing"));
        click("To Live");assertEquals(0,a.tab);assertNotNull(chooser(a));
        drawer("Backing");assertEquals("Last selected source retained",1,a.sourceTab);
    }
    @Test public void everyDrawerDestinationKeepsItsMeaningAfterBackingInsertion() {
        String[] labels={"Composer","Library","Audio settings","Vocal practice","Vocal harmony","Live"};
        int[] tabs={7,4,6,5,9,0};
        for(int i=0;i<labels.length;i++){drawer(labels[i]);assertEquals(labels[i],tabs[i],a.tab);}
        drawer("Recording settings");AlertDialog options=(AlertDialog)ShadowDialog.getLatestDialog();assertNotNull(options);assertTrue(options.isShowing());options.dismiss();
        drawer("Help");AlertDialog help=(AlertDialog)ShadowDialog.getLatestDialog();assertTrue(help.isShowing());
        assertEquals("Live Vault",Shadows.shadowOf(help).getTitle().toString());help.dismiss();assertEquals(0,a.tab);
    }
    @Test public void chooserAndTrackNameNavigateFromEmptyFileAndPausedComposerWithoutStartingTransport() throws Exception {
        for(int state=0;state<3;state++) {
            a.selectedFile=state==0?"":"loaded-backing.wav";AudioService.composerRequested=state==2;a.sourceTab=state;
            a.chooseTab(0);
            StudioScreenshotTest.Audio.localPlayCalls.clear();StudioScreenshotTest.Composer.transportCalls.clear();
            chooser(a).performClick();assertEquals(1,a.tab);assertEquals(state,a.sourceTab);
            click("To Live");
            ((View)a.liveScreen.backing.getParent()).performClick();assertEquals(1,a.tab);assertEquals(state,a.sourceTab);
            a.composer.worker.submit(()->{}).get();ShadowLooper.idleMainLooper();a.handler.removeCallbacks(a.tick);
            assertFalse(StudioScreenshotTest.Audio.localPlayCalls.contains(true));assertFalse(StudioScreenshotTest.Composer.transportCalls.contains(1));
            assertFalse(a.awaitingMicPermission);assertFalse(AudioService.active);
        }
    }
    @Test public void emptyPlayOpensChooserButSelectedFilePlayRemainsTransport() {
        a.selectedFile="";a.liveScreen.backingPlayButton.performClick();assertEquals(1,a.tab);
        click("To Live");a.selectedFile="loaded-backing.wav";StudioScreenshotTest.Audio.localPlayCalls.clear();
        a.liveScreen.backingPlayButton.performClick();assertEquals(0,a.tab);assertTrue(StudioScreenshotTest.Audio.localPlayCalls.contains(true));assertFalse(a.awaitingMicPermission);
    }
    @Test public void selectedYouTubeHasChooserAndFilesTabNaturallyLeavesVideo() {
        a.getPreferences(0).edit().putBoolean("youtubeConsent",true).commit();a.selectYouTube("dQw4w9WgXcQ");
        assertTrue(a.youtube.selected);YouTubePlayerTest.OfflineWeb.scripts.clear();
        chooser(a).performClick();assertEquals(1,a.tab);assertTrue(a.youtube.selected);
        click("My files");assertEquals(1,a.sourceTab);assertFalse(a.youtube.selected);assertFalse(AudioService.youtubeSelected);
        click("To Live");assertEquals(0,a.tab);assertNotNull(chooser(a));
        for(String script:YouTubePlayerTest.OfflineWeb.scripts)assertFalse(script.contains("playVideo"));
    }
    @Test public void recordingAndCountdownLockAllChooserEntrancesWithoutChangingSelection() throws Exception {
        java.lang.reflect.Field field=AudioService.class.getDeclaredField("countdown");field.setAccessible(true);
        RecordingCountdown countdown=(RecordingCountdown)field.get(null);
        try {
            for(boolean recording:new boolean[]{true,false}) {
                RecordingManager.active=recording;if(!recording)countdown.arm(10,android.os.SystemClock.elapsedRealtime());
                a.sourceTab=0;a.selectedFile="preserved.wav";AudioService.composerRequested=true;a.chooseTab(0);
                chooser(a).performClick();assertEquals(0,a.tab);
                ((View)a.liveScreen.backing.getParent()).performClick();assertEquals(0,a.tab);
                drawer("Backing");assertEquals(0,a.tab);assertEquals(0,a.sourceTab);
                assertEquals("preserved.wav",a.selectedFile);assertTrue(AudioService.composerRequested);
                assertEquals(recording,RecordingManager.active);assertEquals(!recording,AudioService.waiting());
                assertFalse(a.awaitingMicPermission);
            }
        } finally {countdown.cancel();RecordingManager.active=false;}
    }
}
