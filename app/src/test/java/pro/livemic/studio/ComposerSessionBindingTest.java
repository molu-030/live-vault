package pro.livemic.studio;

import java.nio.file.Files;
import java.io.File;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowLooper;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,instrumentedPackages={"pro.livemic.studio"},shadows={StudioScreenshotTest.Audio.class,StudioScreenshotTest.Composer.class,StudioScreenshotTest.Harmony.class})
@LooperMode(LooperMode.Mode.PAUSED)
public class ComposerSessionBindingTest {
    MainActivity activity;ComposerSession composer;
    final List<ComposerProject> captured=new ArrayList<>();
    int play,pause,stop;boolean locked,playing;
    @Before public void setup() throws Exception {
        activity=Robolectric.buildActivity(MainActivity.class).setup().get();composer=activity.composer;
        composer.unbindSession();drain();composer.project=new ComposerProject();composer.selectedPart=0;
        composer.capturing=false;composer.voiceOn=false;composer.versionBusy=false;composer.versionRestoreLock=false;
        composer.store.undo.clear();composer.store.redo.clear();
        assertTrue("Composer initialization failed: "+composer.error,composer.ready);composer.error="";
        StudioScreenshotTest.Composer.current=new double[]{0,0,0,0,32,-1,-1,0,0,0};composer.poll();
    }
    void drain()throws Exception {
        activity.handler.removeCallbacks(activity.tick);
        for(int i=0;i<3;i++){composer.worker.submit(()->{}).get();ShadowLooper.idleMainLooper();activity.handler.removeCallbacks(activity.tick);}
    }
    void bind(ComposerProject value){
        boolean bound=composer.bindSession("session-fixture",value,captured::add,()->play++,()->pause++,()->stop++,()->playing,()->locked);
        assertTrue("Binding rejected: error="+composer.error+", ready="+composer.ready+", capture="+composer.capturing+", versionBusy="+composer.versionBusy+", restoreLock="+composer.versionRestoreLock+", exit="+ExitCoordinator.isExitRequested(),bound);
    }
    @After public void cleanup()throws Exception {composer.unbindSession();drain();activity.io.shutdown();}
    @Test public void sessionEditsUndoAndSaveKeepLegacyDraftAndHistory()throws Exception {
        composer.project.duplicate(composer.part());composer.selectedPart=1;
        composer.edit(p->p.name="Legacy unsaved",false);composer.persist();drain();
        ComposerProject legacy=composer.project;String legacyJson=legacy.json().toString();
        List<String> legacyUndo=new ArrayList<>(composer.store.undo);
        File draft=new File(composer.store.directory,"draft.json");byte[] before=Files.readAllBytes(draft.toPath());
        ComposerProject source=new ComposerProject();String initial=source.json().toString();bind(source);
        composer.edit(p->p.name="Session edit",false);composer.history(false);composer.history(true);composer.save();drain();
        assertEquals("Session edit",composer.project.name);assertEquals(initial,source.json().toString());
        assertEquals(4,captured.size());assertEquals("Session edit",captured.get(0).name);
        assertEquals(source.name,captured.get(1).name);assertArrayEquals(before,Files.readAllBytes(draft.toPath()));
        composer.unbindSession();assertSame(legacy,composer.project);assertEquals(legacyJson,composer.project.json().toString());
        assertEquals(1,composer.selectedPart);assertEquals(legacyUndo,new ArrayList<>(composer.store.undo));
        composer.history(false);assertNotEquals("Legacy unsaved",composer.project.name);
    }
    @Test public void sharedTransportDoesNotStartIndependentComposerAndCaptureIsExplicitlyUnavailable()throws Exception {
        bind(new ComposerProject());StudioScreenshotTest.Composer.transportCalls.clear();
        composer.play();composer.pause();composer.stop();composer.recordPhrase();drain();
        assertEquals(1,play);assertEquals(1,pause);assertEquals(1,stop);
        assertFalse(composer.capturing);assertTrue(composer.error.contains("session")||composer.error.contains("세션"));
        assertFalse(StudioScreenshotTest.Composer.transportCalls.contains(1));assertFalse(StudioScreenshotTest.Composer.transportCalls.contains(3));
    }
    @Test public void lockedEditCannotDivergeAndLibraryOpenCannotReplaceBoundProject()throws Exception {
        bind(new ComposerProject());ComposerProject bound=composer.project;String before=bound.json().toString();
        locked=true;composer.edit(p->p.name="Rejected",false);assertEquals(before,composer.project.json().toString());assertTrue(captured.isEmpty());
        composer.open(new ComposerProject());assertSame(bound,composer.project);assertTrue(composer.isSessionBound());
        locked=false;composer.edit(p->p.name="Accepted",false);composer.unbindSession();int count=captured.size();
        composer.edit(p->p.name="Legacy only",false);assertEquals(count,captured.size());
    }
    @Test public void queuedLegacyPublishIsInvalidatedWhenBindingStarts()throws Exception {
        java.util.concurrent.CountDownLatch entered=new java.util.concurrent.CountDownLatch(1),release=new java.util.concurrent.CountDownLatch(1);
        composer.worker.execute(()->{entered.countDown();try{release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}});
        entered.await();composer.publish(-1);int published=StudioScreenshotTest.Composer.publishCalls;
        try{bind(new ComposerProject());}finally{release.countDown();}drain();assertEquals(published,StudioScreenshotTest.Composer.publishCalls);
    }
}
