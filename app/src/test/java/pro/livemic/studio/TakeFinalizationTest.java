package pro.livemic.studio;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.TextureView;
import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowContentResolver;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.shadows.ShadowToast;
import static org.junit.Assert.*;

/** Real filesystem/provider regressions; only native audio and codec work are replaced. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=28, instrumentedPackages={"pro.livemic.studio"}, shadows={ExitFlowTest.Audio.class, ExitFlowTest.VideoMux.class, StudioScreenshotTest.Composer.class, StudioScreenshotTest.Harmony.class, YouTubePlayerTest.OfflineWeb.class})
@LooperMode(LooperMode.Mode.PAUSED)
public class TakeFinalizationTest {
    MainActivity a;
    ActivityController<MainActivity> controller;

    @Before public void setup() throws Exception {
        ExitCoordinator.resetForLaunch();
        MainActivity.finalizingVideoTakes.clear();
        ExitFlowTest.VideoMux.fail=false;ExitFlowTest.VideoMux.corruptMetadataAfterMux=null;
        AudioService.active=false;AudioService.playbackActive=false;
        AudioService.composerRequested=false;AudioService.harmonyRequested=false;
        RecordingManager.active=false;RecordingManager.lastResult=null;AudioService.practice=null;
        StudioScreenshotTest.Audio.supplied=null;
        StudioScreenshotTest.Composer.current=new double[]{0,0,0,0,32,-1,-1,0,0,0};
        StudioScreenshotTest.Harmony.current=new double[]{0,0,0,0,-1,-1,0,0,-1,-1,-1,0,0,-1};
        AppLanguage.set(RuntimeEnvironment.getApplication(),true);
        controller=Robolectric.buildActivity(MainActivity.class).setup();a=controller.get();
        settle();
    }
    void settle() throws Exception {
        a.handler.removeCallbacks(a.tick);
        a.composer.worker.submit(()->{}).get(5,TimeUnit.SECONDS);
        a.harmony.worker.submit(()->{}).get(5,TimeUnit.SECONDS);
        ShadowLooper.idleMainLooper();a.handler.removeCallbacks(a.tick);
    }
    @After public void cleanup() {
        if(a.camera!=null){a.camera.release();a.camera=null;}
        a.handler.removeCallbacksAndMessages(null);a.youtube.destroy();a.io.shutdown();
        MainActivity.finalizingVideoTakes.clear();RecordingManager.lastResult=null;ExitFlowTest.VideoMux.corruptMetadataAfterMux=null;
        AudioService.active=false;AudioService.playbackActive=false;RecordingManager.active=false;
        AudioService.practice=null;ExitCoordinator.resetForLaunch();AppLanguage.set(RuntimeEnvironment.getApplication(),false);
    }
    void video(String id) throws Exception {
        a.store.save(id,"My chosen name",2,false,"");
        Files.write(a.store.file(id,".wav").toPath(),new byte[]{1,2,3});
        a.rawVideo=new File(a.store.directory,"pending-"+id+".video.mp4");
        Files.write(a.rawVideo.toPath(),new byte[]{4,5,6});
        a.videoTakeId=id;RecordingManager.lastResult=new RecordingManager.Result(id,2,0,"");
        a.camera=new CameraController(a,new TextureView(a),new CameraController.Listener(){public void ready(){}public void error(String text){}});
        a.camera.recording=true;
    }
    @Test public void metadataFailureAfterMuxRetainsBothVideoCopies() throws Exception {
        video("metadata-failure");File raw=a.rawVideo;
        // Fail the metadata read only after the mux has produced its finished copy.
        ExitFlowTest.VideoMux.corruptMetadataAfterMux=a.store.file(a.videoTakeId,".json");
        a.stopVideo();a.io.submit(()->{}).get(5,TimeUnit.SECONDS);ShadowLooper.idleMainLooper();
        assertTrue("Original survives metadata failure",raw.exists());
        assertArrayEquals(new byte[]{1,2,3},Files.readAllBytes(a.store.file("metadata-failure",".mp4").toPath()));
        assertFalse(a.busy);assertFalse(MainActivity.finalizingVideoTakes.contains("metadata-failure"));
    }
    @Test public void queuedFinalizationBlocksMenuAndPreservesName() throws Exception {
        video("pending-menu");File raw=a.rawVideo;
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        a.io.execute(()->{entered.countDown();try{release.await(5,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}});
        assertTrue(entered.await(5,TimeUnit.SECONDS));
        try {
            a.stopVideo();
            assertTrue(MainActivity.finalizingVideoTakes.contains("pending-menu"));
            android.app.Dialog before=ShadowDialog.getLatestDialog();
            a.takeMenu(a.store.read("pending-menu"));
            assertSame("No rename/delete/share menu while finalizing",before,ShadowDialog.getLatestDialog());
            assertTrue(ShadowToast.getTextOfLatestToast().contains("finishes saving"));
        } finally {release.countDown();}
        a.io.submit(()->{}).get(5,TimeUnit.SECONDS);ShadowLooper.idleMainLooper();
        assertEquals("My chosen name",a.store.read("pending-menu").getString("name"));
        assertTrue(a.store.read("pending-menu").getBoolean("video"));
        assertFalse(raw.exists());assertFalse(a.busy);
    }
    @Test public void takeExportSurvivesActivityRecreationAndCopiesProviderBytes() throws Exception {
        File source=new File(a.getCacheDir(),"recreated-export.wav");
        byte[] bytes=new byte[]{9,8,7,6};Files.write(source.toPath(),bytes);a.export(source);
        Bundle state=new Bundle();controller.saveInstanceState(state).pause().stop().destroy();
        controller=Robolectric.buildActivity(MainActivity.class).create(state).start().resume().visible();a=controller.get();settle();
        assertEquals(source,a.exportFile);
        File destination=new File(a.getCacheDir(),"recreated-destination.wav");
        ComposerExportTest.TestProvider.destination=destination;
        ComposerExportTest.TestProvider provider=new ComposerExportTest.TestProvider();
        android.content.pm.ProviderInfo info=new android.content.pm.ProviderInfo();info.authority="take.export";provider.attachInfo(a,info);
        ShadowContentResolver.registerProviderInternal("take.export",provider);
        a.onActivityResult(21,MainActivity.RESULT_OK,new Intent().setData(Uri.parse("content://take.export/output")));
        a.io.submit(()->{}).get(5,TimeUnit.SECONDS);ShadowLooper.idleMainLooper();
        assertArrayEquals(bytes,Files.readAllBytes(destination.toPath()));assertNull(a.exportFile);
    }
    @Test public void missingSourceAndCancellationAreReportedAndClearPendingExport() throws Exception {
        File source=new File(a.getCacheDir(),"missing-export.wav");Files.write(source.toPath(),new byte[]{1});a.export(source);assertTrue(source.delete());
        a.onActivityResult(21,MainActivity.RESULT_OK,new Intent().setData(Uri.parse("content://take.export/output")));
        assertNull(a.exportFile);assertTrue(ShadowToast.getTextOfLatestToast().contains("source file is missing"));
        Files.write(source.toPath(),new byte[]{1});a.export(source);a.onActivityResult(21,MainActivity.RESULT_CANCELED,null);
        assertNull(a.exportFile);assertEquals("File save canceled.",ShadowToast.getTextOfLatestToast());
    }
    @Test public void automaticPracticeCompletionRefreshesControlsAndResultsOnlyOnce() throws Exception {
        AudioService.practice=new PracticeSession(a);PracticeSession practice=AudioService.practice;
        // Navigation intentionally stops active practice. The real start action runs
        // on the practice page and redraws it after starting the service session.
        a.chooseTab(5);practice.active=true;a.showTab();
        assertTrue("Practice remains active on its own page",practice.active);
        android.widget.SeekBar tempo=find(a.content,android.widget.SeekBar.class);
        android.widget.Switch capture=find(a.content,android.widget.Switch.class);
        assertNotNull(tempo);assertNotNull(capture);
        assertFalse("Tempo is locked during practice",tempo.isEnabled());
        assertFalse("Capture is locked during practice",capture.isEnabled());
        a.root.measure(android.view.View.MeasureSpec.makeMeasureSpec(412,android.view.View.MeasureSpec.EXACTLY),android.view.View.MeasureSpec.makeMeasureSpec(700,android.view.View.MeasureSpec.EXACTLY));
        a.root.layout(0,0,412,700);a.pageScroll.scrollTo(0,100);int scroll=a.pageScroll.getScrollY();
        practice.stop(true);int starts=StudioScreenshotTest.Audio.microphoneStarts;
        a.tick.run();a.handler.removeCallbacks(a.tick);
        assertTrue(find(a.content,android.widget.SeekBar.class).isEnabled());
        assertTrue(find(a.content,android.widget.Switch.class).isEnabled());
        assertNotNull(practice.result);assertTrue(containsText(a.content,"Not enough valid vocal input"));
        assertEquals(scroll,a.pageScroll.pendingScrollY);
        android.widget.TextView readout=a.trainingReadout;
        a.tick.run();a.handler.removeCallbacks(a.tick);assertSame("No repeated redraw",readout,a.trainingReadout);
        assertEquals("Refresh does not restart the microphone",starts,StudioScreenshotTest.Audio.microphoneStarts);
    }
    static <T extends android.view.View> T find(android.view.View view,Class<T> type) {
        if(type.isInstance(view))return type.cast(view);
        if(view instanceof android.view.ViewGroup)for(int i=0;i<((android.view.ViewGroup)view).getChildCount();i++) {
            T found=find(((android.view.ViewGroup)view).getChildAt(i),type);if(found!=null)return found;
        }
        return null;
    }
    static boolean containsText(android.view.View view,String text) {
        if(view instanceof android.widget.TextView&&((android.widget.TextView)view).getText().toString().contains(text))return true;
        if(view instanceof android.view.ViewGroup)for(int i=0;i<((android.view.ViewGroup)view).getChildCount();i++)if(containsText(((android.view.ViewGroup)view).getChildAt(i),text))return true;
        return false;
    }

}
