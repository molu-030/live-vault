package pro.livemic.studio;

import android.app.AlertDialog;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowLooper;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28, instrumentedPackages={"pro.livemic.studio"}, shadows={ExitFlowTest.Audio.class,ExitFlowTest.VideoMux.class, StudioScreenshotTest.Composer.class, StudioScreenshotTest.Harmony.class, YouTubePlayerTest.OfflineWeb.class})
@LooperMode(LooperMode.Mode.PAUSED)
public class ExitFlowTest {
    @org.robolectric.annotation.internal.DoNotInstrument
    @Implements(value=NativeAudio.class,isInAndroidSdk=false)
    public static class Audio extends StudioScreenshotTest.Audio {
        @Implementation protected static void endRecord() {}
        @Implementation protected static double recordClock() { return 0; }
        @Implementation protected static boolean exportMidi(String path,int bpm,boolean recorded) { return true; }
    }
    @org.robolectric.annotation.internal.DoNotInstrument
    @Implements(value=MediaTools.class,isInAndroidSdk=false)
    public static class VideoMux {
        static boolean fail;
        @Implementation protected static void makeVideo(java.io.File source,java.io.File wav,java.io.File target,long offset,long delay) throws Exception {
            if(fail)throw new java.io.IOException("test mux failure");
            java.nio.file.Files.write(target.toPath(),new byte[]{1,2,3});
        }
    }

    MainActivity a;
    @Before public void setup() throws Exception {
        ExitCoordinator.resetForLaunch();
        VideoMux.fail=false;
        AudioService.active=false; AudioService.playbackActive=false;
        AudioService.composerRequested=false; AudioService.harmonyRequested=false;
        RecordingManager.active=false;
        AppLanguage.set(RuntimeEnvironment.getApplication(),true);
        StudioScreenshotTest.Audio.supplied=null;
        StudioScreenshotTest.Composer.current=new double[]{0,0,0,0,32,-1,-1,0,0,0};
        StudioScreenshotTest.Composer.performance=new double[0];
        StudioScreenshotTest.Harmony.current=new double[]{0,0,0,0,-1,-1,0,0,-1,-1,-1,0,0,-1};
        a=Robolectric.buildActivity(MainActivity.class).setup().get();
        a.handler.removeCallbacks(a.tick);
        a.composer.worker.submit(()->{}).get(5,TimeUnit.SECONDS);
        a.harmony.worker.submit(()->{}).get(5,TimeUnit.SECONDS);
        ShadowLooper.idleMainLooper(); a.handler.removeCallbacks(a.tick);
    }
    @After public void cleanup() {
        a.handler.removeCallbacksAndMessages(null); a.youtube.destroy(); a.io.shutdown();
        ExitCoordinator.resetForLaunch();
        AudioService.active=false;AudioService.playbackActive=false;RecordingManager.active=false;
        AppLanguage.set(RuntimeEnvironment.getApplication(),false);
    }
    @Test public void rootCancelAndDialogBackPreserveAudioAndPermission() {
        AudioService.active=true;RecordingManager.active=true;a.pendingAction=4;a.awaitingMicPermission=true;
        a.onBackPressed();
        AlertDialog dialog=(AlertDialog)ShadowDialog.getLatestDialog();
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
        ShadowLooper.idleMainLooper();a.handler.removeCallbacks(a.tick);
        assertFalse(ExitCoordinator.isExitRequested());assertTrue(RecordingManager.active);assertEquals(4,a.pendingAction);
        a.onBackPressed();ShadowLooper.idleMainLooper();a.handler.removeCallbacks(a.tick);
        a.onBackPressed();ShadowLooper.idleMainLooper();a.handler.removeCallbacks(a.tick);
        assertFalse(ShadowDialog.getLatestDialog().isShowing());
        assertTrue(AudioService.active);assertTrue(a.awaitingMicPermission);assertFalse(a.isFinishing());
    }
    @Test public void menuExitIsAccessibleAndKeepsExistingNavigation() {
        a.chooseTab(4);a.drawer();
        android.widget.PopupWindow drawer=a.drawerPopup;
        View exit=findDescription(drawer.getContentView(),"Exit, Save and turn off microphone and playback");
        assertNotNull(exit);assertTrue(exit.isFocusable());exit.performClick();
        assertFalse(drawer.isShowing());assertEquals(4,a.tab);
        assertTrue(ShadowDialog.getLatestDialog().isShowing());assertFalse(ExitCoordinator.isExitRequested());
    }
    @Test public void confirmationWaitsForQueuedSaveAndIgnoresStalePermissionAndPendingStart() throws Exception {
        CountDownLatch entered=new CountDownLatch(1), release=new CountDownLatch(1);
        a.io.execute(()->{entered.countDown();try{release.await(5,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}});
        assertTrue(entered.await(5,TimeUnit.SECONDS));
        beginAudio();String savedId=RecordingManager.id;
        AudioService.active=true;AudioService.playbackActive=true;
        int stopsBefore=StudioScreenshotTest.Audio.stopCalls;
        a.busy=true;a.pendingAction=4;a.awaitingMicPermission=true;
        a.onBackPressed();((AlertDialog)ShadowDialog.getLatestDialog()).getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        ShadowLooper.idleMainLooper();a.handler.removeCallbacks(a.tick);
        assertTrue(a.exitInProgress);assertTrue(ExitCoordinator.isExitRequested());assertFalse(a.isFinishing());
        assertFalse("Recording closes before pending export finishes",RecordingManager.active);
        assertFalse(AudioService.active);assertFalse(AudioService.playbackActive);
        assertTrue(StudioScreenshotTest.Audio.stopCalls>stopsBefore);
        assertTrue(a.store.file(savedId,".wav").exists());assertTrue(a.store.file(savedId,".json").exists());
        assertNotNull(a.exitProgress);assertTrue(a.exitProgress.isShowing());
        a.onBackPressed();assertTrue(a.exitProgress.isShowing());
        org.robolectric.android.controller.ActivityController<MainActivity> duplicateController=Robolectric.buildActivity(MainActivity.class).setup();
        MainActivity duplicate=duplicateController.get();
        assertTrue(duplicate.isFinishing());assertNull(duplicate.youtube);
        assertTrue(ExitCoordinator.isExitRequested());assertTrue(a.exitInProgress);
        duplicateController.pause().stop().destroy();
        a.chooseTab(7);assertEquals(0,a.tab);
        assertEquals(0,a.pendingAction);a.exitApp(); // Repeated requests cannot bypass the save barrier.
        int starts=StudioScreenshotTest.Audio.microphoneStarts;
        a.onRequestPermissionsResult(10,new String[]{android.Manifest.permission.RECORD_AUDIO},new int[]{0});
        a.onActivityResult(30,MainActivity.RESULT_OK,new Intent());a.pendingSource.run();a.tryPendingAction();
        assertEquals(starts,StudioScreenshotTest.Audio.microphoneStarts);assertFalse(a.isFinishing());
        release.countDown();awaitExit();assertTrue(a.isFinishing());assertTrue(a.exitCompleted);
        assertTrue(a.composer.worker.isShutdown());assertTrue(a.harmony.worker.isShutdown());
        MainActivity next=Robolectric.buildActivity(MainActivity.class).setup().get();next.handler.removeCallbacks(next.tick);
        assertFalse(ExitCoordinator.isExitRequested());assertFalse(AudioService.active);assertFalse(next.awaitingMicPermission);
        assertFalse(next.composer.worker.isShutdown());assertFalse(next.composer.voiceOn);
        a.onRequestPermissionsResult(10,new String[0],new int[0]);assertEquals(starts,StudioScreenshotTest.Audio.microphoneStarts);
        next.youtube.destroy();next.io.shutdown();next.handler.removeCallbacksAndMessages(null);
    }
    public static class BlockingExportProvider extends ComposerExportTest.TestProvider {
        static CountDownLatch entered,release;
        @Override public android.os.ParcelFileDescriptor openFile(android.net.Uri uri,String mode) throws java.io.FileNotFoundException {
            entered.countDown();try{if(!release.await(5,TimeUnit.SECONDS))throw new java.io.FileNotFoundException("Timed out");}
            catch(InterruptedException e){Thread.currentThread().interrupt();throw new java.io.FileNotFoundException("Interrupted");}
            return super.openFile(uri,mode);
        }
    }
    @Test public void confirmedExitWaitsForAuthorizedDocumentExportAndSuppressesLateUi() throws Exception {
        a.exchange.showExport(a.composer.project);
        ((AlertDialog)ShadowDialog.getLatestDialog()).getButton(AlertDialog.BUTTON_POSITIVE).performClick();ShadowLooper.idleMainLooper();a.handler.removeCallbacks(a.tick);
        Shadows.shadowOf(a).getNextStartedActivityForResult();
        ComposerExportTest.TestProvider.destination=new java.io.File(a.getCacheDir(),"exit-export.zip");
        BlockingExportProvider.entered=new CountDownLatch(1);BlockingExportProvider.release=new CountDownLatch(1);
        BlockingExportProvider provider=new BlockingExportProvider();android.content.pm.ProviderInfo info=new android.content.pm.ProviderInfo();info.authority="exit.export";provider.attachInfo(a,info);
        org.robolectric.shadows.ShadowContentResolver.registerProviderInternal("exit.export",provider);
        a.exchange.onActivityResult(40,MainActivity.RESULT_OK,new Intent().setData(android.net.Uri.parse("content://exit.export/output")));
        assertTrue(BlockingExportProvider.entered.await(5,TimeUnit.SECONDS));
        a.exitApp();assertFalse(a.isFinishing());assertFalse(AudioService.active);assertFalse(a.exitResourcesStopped);
        BlockingExportProvider.release.countDown();
        for(Thread thread:Thread.getAllStackTraces().keySet())if(thread.getName().equals("composer-export"))thread.join(5000);
        ShadowLooper.idleMainLooper();awaitExit();
        assertTrue(a.isFinishing());
        try(java.util.zip.ZipFile zip=new java.util.zip.ZipFile(ComposerExportTest.TestProvider.destination)){assertNotNull(zip.getEntry("project.json"));}
    }
    public static class BlockingImportProvider extends ComposerExportTest.TestProvider {
        static CountDownLatch entered,release;static java.io.File source;
        @Override public android.os.ParcelFileDescriptor openFile(android.net.Uri uri,String mode) throws java.io.FileNotFoundException {
            entered.countDown();try{if(!release.await(5,TimeUnit.SECONDS))throw new java.io.FileNotFoundException("Timed out");}
            catch(InterruptedException e){Thread.currentThread().interrupt();throw new java.io.FileNotFoundException("Interrupted");}
            return android.os.ParcelFileDescriptor.open(source,android.os.ParcelFileDescriptor.MODE_READ_ONLY);
        }
    }
    @Test public void importFinishingAfterExitCannotShowPreviewOrApplyProject() throws Exception {
        a.exchange.showImport();Shadows.shadowOf(a).getNextStartedActivityForResult();
        BlockingImportProvider.source=new java.io.File(a.getCacheDir(),"late-import.json");
        java.nio.file.Files.write(BlockingImportProvider.source.toPath(),a.composer.project.json().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        BlockingImportProvider.entered=new CountDownLatch(1);BlockingImportProvider.release=new CountDownLatch(1);
        BlockingImportProvider provider=new BlockingImportProvider();android.content.pm.ProviderInfo info=new android.content.pm.ProviderInfo();info.authority="exit.import";provider.attachInfo(a,info);
        org.robolectric.shadows.ShadowContentResolver.registerProviderInternal("exit.import",provider);
        a.exchange.onActivityResult(41,MainActivity.RESULT_OK,new Intent().setData(android.net.Uri.parse("content://exit.import/input")));
        assertTrue(BlockingImportProvider.entered.await(5,TimeUnit.SECONDS));
        a.exitApp();awaitExit();android.app.Dialog last=ShadowDialog.getLatestDialog();
        BlockingImportProvider.release.countDown();
        for(Thread thread:Thread.getAllStackTraces().keySet())if(thread.getName().equals("composer-import"))thread.join(5000);
        ShadowLooper.idleMainLooper();assertTrue(a.isFinishing());assertSame(last,ShadowDialog.getLatestDialog());
    }
    @Test public void exitSavesPhraseCompletedBeforeJavaPoll() throws Exception {
        preparePhraseCapture();
        StudioScreenshotTest.Composer.performance=new double[]{4,1,60,90,0,.8,0,0,5,0,60,0,0,.7,0,0};
        StudioScreenshotTest.Composer.current[8]=3;
        a.exitApp();
        assertFalse(a.composer.capturing);
        assertEquals(1,a.composer.part().phrases.size());assertEquals(60,a.composer.part().phrases.get(0).notes.get(0).midi);
        awaitExit();ComposerProject saved=a.composer.store.draft();
        assertNotNull(saved);assertEquals(1,saved.parts.get(0).phrases.size());assertEquals(60,saved.parts.get(0).phrases.get(0).notes.get(0).midi);
        String recordedTrackId=saved.parts.get(0).phrases.get(0).trackId;
        TrackConfig recordedTrack=null;for(TrackConfig track:saved.parts.get(0).tracks)if(track.id.equals(recordedTrackId))recordedTrack=track;
        assertNotNull(recordedTrack);assertEquals("microphone",recordedTrack.phraseSource);
    }
    @Test public void serviceDestructionPersistsAlreadyCompletedPhraseBeforeDisablingVoice() throws Exception {
        org.robolectric.android.controller.ServiceController<AudioService> service=Robolectric.buildService(AudioService.class).create();
        preparePhraseCapture();
        StudioScreenshotTest.Composer.performance=new double[]{4,1,60,90,0,.8,0,0,5,0,60,0,0,.7,0,0};
        StudioScreenshotTest.Composer.current[8]=3;
        service.destroy();
        assertFalse(a.composer.capturing);assertFalse(a.composer.voiceOn);
        assertEquals(1,a.composer.part().phrases.size());
        a.composer.worker.submit(()->{}).get(5,TimeUnit.SECONDS);
        ComposerProject saved=a.composer.store.draft();assertNotNull(saved);
        VoicePhrase phrase=saved.parts.get(0).phrases.get(0);assertEquals(60,phrase.notes.get(0).midi);
        TrackConfig target=null;for(TrackConfig track:saved.parts.get(0).tracks)if(track.id.equals(phrase.trackId))target=track;
        assertNotNull(target);assertEquals("microphone",target.phraseSource);
    }
    @Test public void exitCancelsIncompletePhraseAndKeepsPreviousProject() throws Exception {
        String before=a.composer.project.json().toString();
        preparePhraseCapture();
        StudioScreenshotTest.Composer.performance=new double[]{4,1,60,90,0,.8,0,0};
        StudioScreenshotTest.Composer.current[8]=1;
        a.exitApp();assertFalse(a.composer.capturing);
        assertEquals(before,a.composer.project.json().toString());awaitExit();
    }
    void preparePhraseCapture() {
        AudioService.active=true;a.composer.voiceOn=true;
        StudioScreenshotTest.Composer.current=new double[]{2,1,0,0,32,60,60,.1,0,0};
        a.composer.poll();a.composer.recordPhrase();assertTrue(a.composer.capturing);
    }
    @Test public void activeAudioIsFinalizedBeforeTaskRemoval() throws Exception {
        beginAudio();
        String id=RecordingManager.id;
        a.exitApp();awaitExit();
        assertFalse(RecordingManager.active);
        assertTrue(a.store.file(id,".wav").exists());
        assertTrue(a.store.file(id,".json").exists());
        assertTrue(a.isFinishing());
    }
    @Test public void activeVideoPublishesMp4BeforeRemovingTask() throws Exception {
        beginAudio();String id=RecordingManager.id;
        a.rawVideo=new java.io.File(a.store.directory,"pending-success.video.mp4");
        java.nio.file.Files.write(a.rawVideo.toPath(),new byte[]{1,2,3});a.videoTakeId=id;
        a.camera=new CameraController(a,new android.view.TextureView(a),new CameraController.Listener(){public void ready(){}public void error(String message){}});
        a.camera.recording=true;
        a.exitApp();awaitExit();
        assertTrue(a.isFinishing());assertTrue(a.store.file(id,".mp4").exists());
        assertTrue(a.store.file(id,".wav").exists());assertTrue(a.store.read(id).getBoolean("video"));
    }
    @Test public void activeVideoMuxFinishesAndFailuresRemainReviewable() throws Exception {
        beginAudio();String id=RecordingManager.id;
        a.rawVideo=new java.io.File(a.store.directory,"pending-test.video.mp4");
        java.nio.file.Files.write(a.rawVideo.toPath(),new byte[]{1,2,3});
        a.videoTakeId=id;
        a.camera=new CameraController(a,new android.view.TextureView(a),new CameraController.Listener(){public void ready(){}public void error(String message){}});
        a.camera.recording=true;
        VideoMux.fail=true;
        a.exitApp();
        for(int i=0;i<30&&!a.exitResourcesStopped;i++) {
            if(!a.io.isShutdown())a.io.submit(()->{}).get(5,TimeUnit.SECONDS);
            ShadowLooper.idleMainLooper();
            if(!a.composer.worker.isShutdown())a.composer.worker.submit(()->{}).get(5,TimeUnit.SECONDS);
            if(!a.harmony.worker.isShutdown())a.harmony.worker.submit(()->{}).get(5,TimeUnit.SECONDS);
            ShadowLooper.idleMainLooper();
        }
        assertTrue(a.exitResourcesStopped);assertFalse(a.isFinishing());
        assertTrue(a.exitSaveError.contains("test mux failure"));
        assertTrue(a.rawVideo.exists());assertTrue(a.store.file(id,".wav").exists());assertTrue(a.store.file(id,".json").exists());
        AlertDialog error=(AlertDialog)ShadowDialog.getLatestDialog();assertTrue(error.isShowing());
        org.robolectric.android.controller.ActivityController<MainActivity> duplicate=Robolectric.buildActivity(MainActivity.class).setup();
        assertTrue(duplicate.get().isFinishing());assertNull(duplicate.get().youtube);assertTrue(ExitCoordinator.isExitRequested());
        duplicate.pause().stop().destroy();
        error.getButton(AlertDialog.BUTTON_POSITIVE).performClick();ShadowLooper.idleMainLooper();assertTrue(a.isFinishing());
    }
    void beginAudio() throws Exception {
        float[] stats=new float[22];stats[10]=1;stats[8]=2;StudioScreenshotTest.Audio.supplied=stats;
        assertTrue(RecordingManager.start(a,90));
        java.nio.file.Files.write(a.store.file(RecordingManager.id,".wav").toPath(),new byte[128]);
    }
    @Test public void idleUiAndBackgroundReleaseScreenAndStopTicks() {
        assertEquals(1000,a.uiTickDelay(false));assertEquals(80,a.uiTickDelay(true));
        a.updateScreenAwake(false);assertEquals(0,a.getWindow().getAttributes().flags&WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        a.updateScreenAwake(true);assertTrue((a.getWindow().getAttributes().flags&WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)!=0);
        a.chooseTab(4);a.updateScreenAwake(true);assertEquals(0,a.getWindow().getAttributes().flags&WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        a.onPause();int reads=StudioScreenshotTest.Audio.statsCalls;a.tick.run();assertEquals(reads,StudioScreenshotTest.Audio.statsCalls);
    }
    void awaitExit() throws Exception {
        for(int i=0;i<30&&!a.exitResourcesStopped;i++) {
            if(!a.io.isShutdown())a.io.submit(()->{}).get(5,TimeUnit.SECONDS);
            ShadowLooper.idleMainLooper();
            if(!a.composer.worker.isShutdown())a.composer.worker.submit(()->{}).get(5,TimeUnit.SECONDS);
            if(!a.harmony.worker.isShutdown())a.harmony.worker.submit(()->{}).get(5,TimeUnit.SECONDS);
            ShadowLooper.idleMainLooper();
        }
        assertTrue(a.exitResourcesStopped);
        if(!a.isFinishing()) {((AlertDialog)ShadowDialog.getLatestDialog()).getButton(AlertDialog.BUTTON_POSITIVE).performClick();ShadowLooper.idleMainLooper();}
    }
    static View findDescription(View view,String description) {
        if(description.contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))return view;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){View found=findDescription(((ViewGroup)view).getChildAt(i),description);if(found!=null)return found;}
        return null;
    }
}
