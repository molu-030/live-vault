package pro.livemic.studio;

import android.app.Dialog;
import android.media.MediaPlayer;
import android.view.View;
import android.view.MotionEvent;
import android.os.Bundle;
import android.graphics.Rect;
import android.view.accessibility.AccessibilityNodeInfo;
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
        AppLanguage.set(a,false);RuntimeEnvironment.setFontScale(1f);StudioScreenshotTest.Audio.supplied=null;
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
    StudioPlaybackLine seekLine() {
        StudioPlaybackLine line=a.takePlaybackLines.get(wav.getAbsolutePath());assertNotNull(line);return line;
    }
    void touch(StudioPlaybackLine line,int action,float fraction) {
        line.layout(0,0,400,48);
        MotionEvent event=MotionEvent.obtain(0,0,action,7+386*fraction,24,0);
        assertTrue(line.onTouchEvent(event));event.recycle();
    }
    @Test public void tapAndDragSeekPlayingAudioAndTickDoesNotFightTheGesture() {
        MediaPlayer preview=startPreview();StudioPlaybackLine line=seekLine();assertEquals(View.VISIBLE,line.getVisibility());
        touch(line,MotionEvent.ACTION_DOWN,.25f);ShadowLooper.idleMainLooper();
        assertEquals(2500,preview.getCurrentPosition());assertTrue(preview.isPlaying());
        touch(line,MotionEvent.ACTION_MOVE,.73f);ShadowLooper.idleMainLooper();
        assertEquals(7300,preview.getCurrentPosition());assertTrue(line.dragging);
        preview.seekTo(1000);ShadowLooper.idleMainLooper();a.updateTakePlaybackProgress();
        assertEquals(.73f,line.progress,.001f);
        touch(line,MotionEvent.ACTION_UP,.81f);ShadowLooper.idleMainLooper();a.updateTakePlaybackProgress();assertFalse(line.dragging);
        assertEquals(8100,preview.getCurrentPosition());assertEquals(.81f,line.progress,.001f);
    }
    @Test public void pausedAccessibilitySeekUpdatesTimeImmediatelyAndRefreshRebinds() {
        MediaPlayer preview=startPreview();a.play(wav);StudioPlaybackLine line=seekLine();
        Bundle args=new Bundle();args.putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE,.6f);
        assertTrue(line.performAccessibilityAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.getId(),args));
        assertEquals("Paused · 00:06 / 00:10",a.takePlaybackStatus.get(wav.getAbsolutePath()).getText().toString());
        ShadowLooper.idleMainLooper();assertEquals(6000,preview.getCurrentPosition());assertFalse(preview.isPlaying());
        a.showTab();assertNotSame(line,seekLine());assertEquals(.6f,seekLine().progress,.001f);
        assertEquals(View.VISIBLE,seekLine().getVisibility());assertFalse(preview.isPlaying());
        line.change(.2f);ShadowLooper.idleMainLooper();assertEquals(6000,preview.getCurrentPosition());
    }
    @Test public void seekClampsBoundsRejectsInvalidFractionsAndNeverStartsStoppedAudio() {
        MediaPlayer preview=startPreview();a.play(wav);int generation=a.takePlaybackViewGeneration;
        a.seekTake(wav,generation,-2);ShadowLooper.idleMainLooper();assertEquals(0,preview.getCurrentPosition());
        a.seekTake(wav,generation,2);ShadowLooper.idleMainLooper();assertEquals(10000,preview.getCurrentPosition());assertFalse(preview.isPlaying());
        a.seekTake(wav,generation,Float.NaN);a.seekTake(wav,generation,Float.POSITIVE_INFINITY);
        ShadowLooper.idleMainLooper();assertEquals(10000,preview.getCurrentPosition());
        StudioPlaybackLine line=seekLine();a.stopPlayer();line.change(.5f);assertNull(a.player);assertEquals(View.GONE,line.getVisibility());
    }
    @Test public void seekIsBlockedDuringRecordingCountdownAndAfterChangingFileOrPage()throws Exception {
        MediaPlayer preview=startPreview();a.play(wav);int generation=a.takePlaybackViewGeneration;StudioPlaybackLine stale=seekLine();
        RecordingManager.active=true;a.updateTakePlaybackProgress();assertFalse(stale.isEnabled());
        a.seekTake(wav,generation,.5f);ShadowLooper.idleMainLooper();assertEquals(0,preview.getCurrentPosition());
        RecordingManager.active=false;
        java.lang.reflect.Field field=AudioService.class.getDeclaredField("countdown");field.setAccessible(true);
        RecordingCountdown countdown=(RecordingCountdown)field.get(null);countdown.arm(3,0);
        a.seekTake(wav,generation,.5f);a.updateTakePlaybackProgress();assertFalse(stale.isEnabled());
        ShadowLooper.idleMainLooper();assertEquals(0,preview.getCurrentPosition());countdown.cancel();a.updateTakePlaybackProgress();
        File other=a.store.file("other-seek-preview",".wav");TakeSourcesTest.wav(other,false,2);
        ShadowMediaPlayer.addMediaInfo(DataSource.toDataSource(other.getAbsolutePath()),new ShadowMediaPlayer.MediaInfo(10000,0));
        a.play(other);MediaPlayer current=a.player;stale.change(.5f);ShadowLooper.idleMainLooper();assertEquals(0,current.getCurrentPosition());
        a.chooseTab(0);cleared(current);stale.change(.8f);assertNull(a.player);
    }
    @Test @Config(sdk=35,qualifiers="w412dp-h893dp-mdpi") @GraphicsMode(GraphicsMode.Mode.NATIVE)
    public void renderPhoneKoreanSeekAtLargeFont()throws Exception { renderSeekCapture(false,412,893,"phone-ko-large.png"); }
    @Test @Config(sdk=35,qualifiers="w1280dp-h800dp-land-mdpi") @GraphicsMode(GraphicsMode.Mode.NATIVE)
    public void renderTabletEnglishSeekAtLargeFont()throws Exception { renderSeekCapture(true,1280,800,"tablet-en-large.png"); }
    void renderSeekCapture(boolean english,int width,int height,String name)throws Exception {
        RuntimeEnvironment.setFontScale(1.3f);AppLanguage.set(a,english);a.showTab();startPreview();a.play(wav);
        seekLine().change(.6f);ShadowLooper.idleMainLooper();a.updateTakePlaybackProgress();
        a.root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));
        a.root.layout(0,0,width,height);
        Rect bounds=StudioScreenshotTest.bounds(a,seekLine());
        if(bounds.bottom>a.pageScroll.getBottom())a.pageScroll.scrollBy(0,bounds.bottom-a.pageScroll.getBottom()+16);
        File out=new File(System.getProperty("user.dir"),"build/studio-screenshots/library-seek");out.mkdirs();
        StudioScreenshotTest.capture(a,width,height,new File(out,name),false);
        bounds=StudioScreenshotTest.bounds(a,seekLine());assertEquals(48,seekLine().getHeight());
        assertTrue("seek stays within scroll viewport",bounds.top>=a.pageScroll.getTop()&&bounds.bottom<=a.pageScroll.getBottom());
        assertTrue("seek leaves gesture edge space",bounds.left>=16&&bounds.right<=width-16);
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
