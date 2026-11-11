package pro.livemic.studio;

import android.app.Dialog;
import android.media.MediaPlayer;
import android.view.View;
import android.widget.Button;
import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.*;
import org.robolectric.shadows.util.DataSource;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,instrumentedPackages={"pro.livemic.studio"},shadows={LibraryPlaybackNavigationTest.Audio.class,StudioScreenshotTest.Composer.class,StudioScreenshotTest.Harmony.class})
@LooperMode(LooperMode.Mode.PAUSED)
public class LibraryPlaybackNavigationTest {
    @Implements(value=NativeAudio.class,isInAndroidSdk=false)
    public static class Audio extends StudioScreenshotTest.Audio {
        static CountDownLatch rendering,finishRender;
        @Implementation protected static boolean renderTakeSources(String raw,String fixed,String output,float[] values,int offset) {
            rendering.countDown();
            try {
                if(!finishRender.await(5,TimeUnit.SECONDS))return false;
                TakeSourcesTest.wav(new File(output),false,2);
                return true;
            } catch(Exception e) { return false; }
        }
    }
    MainActivity a;
    File wav;
    JSONObject take;
    @Before public void setup()throws Exception {
        ExitCoordinator.resetForLaunch();RecordingManager.active=RecordingManager.paused=false;
        AudioService.active=AudioService.playbackActive=AudioService.composerRequested=AudioService.youtubeSelected=false;
        java.lang.reflect.Field countdown=AudioService.class.getDeclaredField("countdown");countdown.setAccessible(true);((RecordingCountdown)countdown.get(null)).cancel();
        AppLanguage.set(RuntimeEnvironment.getApplication(),true);StudioScreenshotTest.Audio.supplied=new float[22];
        StudioScreenshotTest.Composer.current=new double[]{0,0,0,0,32,-1,-1,0,0,0};
        StudioScreenshotTest.Harmony.current=new double[]{0,0,0,0,-1,-1,0,0,-1,-1,-1,0,0,-1};
        a=Robolectric.buildActivity(MainActivity.class).setup().get();a.handler.removeCallbacks(a.tick);
        a.composer.worker.submit(()->{}).get();ShadowLooper.idleMainLooper();a.handler.removeCallbacks(a.tick);
        String id="navigation-preview";wav=a.store.file(id,".wav");TakeSourcesTest.wav(wav,false,2);
        TakeSourcesTest.wav(a.store.file(id,".raw.wav"),true,1);TakeSourcesTest.wav(a.store.file(id,".fixed.wav"),true,2);
        a.store.save(id,"Navigation preview",1,false,"",new JSONObject().put("cleanSources",true).put("sourceEditable",true).put("sourceFixed",true));
        take=a.store.read(id);
        ShadowMediaPlayer.addMediaInfo(DataSource.toDataSource(wav.getAbsolutePath()),new ShadowMediaPlayer.MediaInfo(10000,0));
        Audio.rendering=new CountDownLatch(1);Audio.finishRender=new CountDownLatch(1);
        a.chooseLibraryFilter(0);
    }
    @After public void cleanup()throws Exception {
        Audio.finishRender.countDown();a.io.submit(()->{}).get();ShadowLooper.idleMainLooper();
        Dialog dialog=ShadowDialog.getLatestDialog();if(dialog!=null)dialog.dismiss();
        if(a.drawerPopup!=null)a.drawerPopup.dismiss();a.stopPlayer();a.handler.removeCallbacksAndMessages(null);
        a.youtube.destroy();a.io.shutdown();a.composer.worker.shutdownNow();
        AudioService.active=AudioService.playbackActive=AudioService.composerRequested=false;RecordingManager.active=false;
        AppLanguage.set(a,false);StudioScreenshotTest.Audio.supplied=null;
    }
    MediaPlayer startPreview() {
        Button play=a.takePlayButtons.get(wav.getAbsolutePath());assertNotNull(play);play.performClick();
        assertNotNull(a.player);assertTrue(a.player.isPlaying());assertEquals(wav,a.playingFile);return a.player;
    }
    void cleared(MediaPlayer previous) {
        assertNull(a.player);assertNull(a.playingFile);assertEquals("END",Shadows.shadowOf(previous).getState().name());
    }
    @Test public void acceptedTabAndFilterChangesReleasePlayingAndPausedPreviews() {
        MediaPlayer previous=startPreview();a.chooseTab(0);cleared(previous);
        a.onBackPressed();assertEquals(4,a.tab);assertNull(a.player);
        previous=startPreview();a.takePlayButtons.get(wav.getAbsolutePath()).performClick();assertFalse(previous.isPlaying());
        a.chooseLibraryFilter(1);cleared(previous);a.chooseLibraryFilter(0);assertNull(a.player);
    }
    @Test public void backAndActualDrawerDestinationStopPreviewButClosingDrawerKeepsIt() {
        MediaPlayer previous=startPreview();a.drawer();assertSame(previous,a.player);a.onBackPressed();assertSame(previous,a.player);
        a.drawer();View destination=StudioScreenshotTest.findDescription(a.drawerPopup.getContentView(),"Live,");assertNotNull(destination);destination.performClick();cleared(previous);
        a.chooseLibraryFilter(0);previous=startPreview();a.onBackPressed();assertEquals(0,a.tab);cleared(previous);
    }
    @Test public void samePageRefreshAndReselectionKeepPlaybackAndStatus() {
        MediaPlayer previous=startPreview();a.showTab();a.chooseLibraryFilter(0);assertSame(previous,a.player);assertTrue(previous.isPlaying());
        assertEquals("Take playing · pause",a.takePlayButtons.get(wav.getAbsolutePath()).getContentDescription());
    }
    @Test public void activityPauseReleasesPausedPreviewAndReturningDoesNotResume() {
        MediaPlayer previous=startPreview();a.play(wav);assertFalse(previous.isPlaying());a.onPause();cleared(previous);
        a.onResume();a.handler.removeCallbacks(a.tick);assertNull(a.player);assertNull(a.playingFile);
    }
    @Test public void activityPauseReleasesPlayingPreview() {
        MediaPlayer previous=startPreview();a.onPause();cleared(previous);
    }
    @Test public void stoppingPreviewLeavesServiceRecordingAndBackingUntouched() {
        MediaPlayer previous=startPreview();AudioService.active=AudioService.playbackActive=AudioService.composerRequested=true;RecordingManager.active=true;
        StudioScreenshotTest.Audio.localPlayCalls.clear();StudioScreenshotTest.Composer.transportCalls.clear();
        a.chooseTab(0);a.onPause();cleared(previous);
        assertTrue(AudioService.active);assertTrue(AudioService.playbackActive);assertTrue(AudioService.composerRequested);assertTrue(RecordingManager.active);
        assertTrue(StudioScreenshotTest.Audio.localPlayCalls.isEmpty());assertTrue(StudioScreenshotTest.Composer.transportCalls.isEmpty());
    }
    void startEditorRender(String label)throws Exception {
        a.takeEffectsEditor=new TakeEffectsEditor(a,take);a.takeEffectsEditor.show();
        Button button=StudioScreenshotTest.findButtonContaining(ShadowDialog.getLatestDialog().getWindow().getDecorView(),label);assertNotNull(button);button.performClick();
        assertTrue("Render started",Audio.rendering.await(5,TimeUnit.SECONDS));
    }
    void completeRender()throws Exception {
        Audio.finishRender.countDown();a.io.submit(()->{}).get();ShadowLooper.idleMainLooper();a.handler.removeCallbacks(a.tick);
    }
    @Test public void delayedEditedPreviewCannotAutoplayAfterLeavingAndReturning()throws Exception {
        startEditorRender("B · Preview edit");a.chooseTab(0);a.chooseLibraryFilter(0);completeRender();assertNull(a.player);assertNull(a.playingFile);
    }
    @Test public void delayedEditedPreviewCannotAutoplayAfterBackgroundAndResume()throws Exception {
        startEditorRender("B · Preview edit");a.onPause();a.onResume();completeRender();assertNull(a.player);assertNull(a.playingFile);
    }
    @Test public void backgroundingDoesNotCancelExplicitSaveRender()throws Exception {
        startEditorRender("Save WAV copy");a.onPause();completeRender();
        boolean saved=false;for(JSONObject item:a.store.list())if(take.getString("id").equals(item.optString("editedFrom")))saved=true;
        assertTrue("Requested copy still saved",saved);assertNull(a.player);
    }
}
