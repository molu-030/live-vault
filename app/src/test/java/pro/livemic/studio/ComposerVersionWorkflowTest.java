package pro.livemic.studio;

import android.app.Dialog;
import android.view.*;
import android.widget.Button;
import java.io.File;
import java.util.*;
import java.util.concurrent.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,instrumentedPackages={"pro.livemic.studio"},shadows={StudioScreenshotTest.Audio.class,StudioScreenshotTest.Composer.class,StudioScreenshotTest.Harmony.class})
@LooperMode(LooperMode.Mode.PAUSED)
public class ComposerVersionWorkflowTest {
    MainActivity a;ComposerSession s;ComposerScreen screen;
    @Before public void setup()throws Exception{
        java.lang.reflect.Field instance=ComposerSession.class.getDeclaredField("instance");instance.setAccessible(true);
        ComposerSession previous=(ComposerSession)instance.get(null);
        if(previous!=null&&!previous.worker.isShutdown()){for(int i=0;i<3;i++){previous.worker.submit(()->{}).get();ShadowLooper.idleMainLooper();}previous.worker.shutdown();}
        instance.set(null,null);AppLanguage.set(RuntimeEnvironment.getApplication(),true);
        a=Robolectric.buildActivity(MainActivity.class).setup().get();s=a.composer;settle();
        s.project=new ComposerProject();s.selectedPart=0;s.capturing=false;s.voiceOn=false;s.error="";s.message="";s.store.undo.clear();s.store.redo.clear();
        AudioService.active=false;AudioService.playbackActive=false;AudioService.composerRequested=false;
        StudioScreenshotTest.Composer.current=new double[]{0,0,0,0,32,-1,-1,0,0,0};s.poll();a.chooseTab(7);screen=a.composerScreen;
    }
    void settle()throws Exception{a.handler.removeCallbacks(a.tick);for(int i=0;i<4;i++){s.worker.submit(()->{}).get();ShadowLooper.idleMainLooper();a.handler.removeCallbacks(a.tick);}}
    @After public void cleanup()throws Exception{s.capturing=false;settle();a.io.shutdown();AppLanguage.set(RuntimeEnvironment.getApplication(),false);}
    Button button(Dialog dialog,String label){Button button=StudioScreenshotTest.findButtonContaining(dialog.getWindow().getDecorView(),label);assertNotNull(label,button);return button;}
    @Test public void creatingVersionRunsBehindWorkerAndKeepsEditingIdentity()throws Exception{
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        s.worker.execute(()->{entered.countDown();try{release.await(5,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}});assertTrue(entered.await(5,TimeUnit.SECONDS));
        String id=s.project.id;
        try{s.createVersion("Before chorus",null);assertTrue(s.versionBusy);assertTrue(s.store.versions.list(id).isEmpty());}finally{release.countDown();}
        settle();assertFalse(s.versionBusy);assertEquals(id,s.project.id);assertEquals("Before chorus",new ProjectVersions(s.store.directory).list(id).get(0).name);
    }
    @Test public void restoringBacksUpCurrentStateAndUndoKeepsBothVersions()throws Exception{
        String id=s.project.id;ProjectVersions.Version version=s.store.versions.create(s.project,"Original tempo");
        s.edit(p->p.bpm=123,false);settle();s.restoreVersion(version,false,null);settle();assertEquals(90,s.project.bpm);assertEquals(id,s.project.id);
        List<ProjectVersions.Version> versions=new ProjectVersions(s.store.directory).list(id);assertEquals(2,versions.size());
        assertEquals(123,s.store.versions.readProject(id,versions.get(0).id).bpm);assertEquals(ComposerSession.SaveState.SAVED,s.librarySave.phase);
        s.history(false);settle();assertEquals(123,s.project.bpm);assertEquals(2,s.store.versions.list(id).size());
    }
    @Test public void restoringAsCopyKeepsOriginalHistoryAndLibraryEntry()throws Exception{
        String id=s.project.id;ProjectVersions.Version version=s.store.versions.create(s.project,"Start");s.project.bpm=132;
        s.restoreVersion(version,true,null);settle();assertNotEquals(id,s.project.id);assertEquals(90,s.project.bpm);assertTrue(s.store.versions.list(s.project.id).isEmpty());
        assertTrue(s.store.list().stream().anyMatch(p->p.id.equals(id)&&p.bpm==132));assertEquals(2,s.store.versions.list(id).size());
    }
    @Test public void unavailableStorageDuringRestoreLeavesCurrentProjectAndHistoryUntouched()throws Exception{
        ProjectVersions.Version version=s.store.versions.create(s.project,"Earlier");s.project.bpm=137;String before=s.project.json().toString();
        File directory=s.store.directory,backup=new File(directory.getParentFile(),"history-test-backup-"+UUID.randomUUID());
        assertTrue(directory.renameTo(backup));assertTrue(directory.createNewFile());
        try{s.restoreVersion(version,false,null);settle();assertEquals(before,s.project.json().toString());assertTrue(s.error,s.error.contains("Version operation failed"));}
        finally{assertTrue(directory.delete());assertTrue(backup.renameTo(directory));}
        assertEquals(1,s.store.versions.list(s.project.id).size());
    }
    @Test public void staleRestoreAndRecordingCannotReplaceNewerEdits()throws Exception{
        ProjectVersions.Version version=s.store.versions.create(s.project,"Old");
        for(int i=1;i<20;i++)s.store.versions.create(s.project,"Named "+i);
        Set<String> history=new HashSet<>();for(ProjectVersions.Version saved:s.store.versions.list(s.project.id))history.add(saved.id);
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        s.worker.execute(()->{entered.countDown();try{release.await(5,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}});assertTrue(entered.await(5,TimeUnit.SECONDS));
        try{s.restoreVersion(version,false,null);s.edit(p->p.bpm=147,false);}finally{release.countDown();}
        settle();assertEquals(147,s.project.bpm);assertTrue(s.error,s.error.contains("project changed"));
        Set<String> after=new HashSet<>();for(ProjectVersions.Version saved:s.store.versions.list(s.project.id))after.add(saved.id);assertEquals(history,after);
        int count=s.store.versions.list(s.project.id).size();s.capturing=true;s.restoreVersion(version,false,null);s.createVersion("During capture",null);assertFalse(s.versionBusy);s.capturing=false;settle();
        assertEquals(count,s.store.versions.list(s.project.id).size());assertEquals(147,s.project.bpm);
    }
    @Test public void previewCancelAndStaleConfirmationKeepOriginalAndUiUsesActualClicks()throws Exception{
        ProjectVersions.Version version=s.store.versions.create(s.project,"Before bridge");s.project.bpm=141;
        screen.versionHistory();settle();button(ShadowDialog.getLatestDialog(),"Before bridge").performClick();settle();Dialog preview=ShadowDialog.getLatestDialog();
        assertNotNull(StudioScreenshotTest.findButtonContaining(preview.getWindow().getDecorView(),"Restore this project"));
        button(preview,"Restore this project").performClick();Dialog confirmation=ShadowDialog.getLatestDialog();button(confirmation,"Cancel").performClick();assertEquals(141,s.project.bpm);
        screen.versionOptions(version);settle();button(ShadowDialog.getLatestDialog(),"Restore this project").performClick();confirmation=ShadowDialog.getLatestDialog();
        s.edit(p->p.bpm=155,false);settle();button(confirmation,"Restore").performClick();settle();assertEquals(155,s.project.bpm);assertEquals(1,s.store.versions.list(s.project.id).size());
    }
    @Test public void finalRestoreCommitLocksEditsUntilBackupIsDurable()throws Exception{
        ProjectVersions.Version version=s.store.versions.create(s.project,"Earlier");s.project.bpm=138;
        s.restoreVersion(version,false,null);s.worker.submit(()->{}).get();
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        s.worker.execute(()->{entered.countDown();try{release.await(5,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}});assertTrue(entered.await(5,TimeUnit.SECONDS));
        try{ShadowLooper.idleMainLooper();assertTrue(s.versionRestoreLock);assertEquals(1,s.store.versions.list(s.project.id).size());
            String before=s.project.json().toString();s.edit(p->p.bpm=170,false);s.open(new ComposerProject());s.history(false);s.recordPhrase();
            assertEquals(before,s.project.json().toString());assertFalse(s.capturing);
        }finally{release.countDown();}
        settle();assertFalse(s.versionRestoreLock);assertEquals(90,s.project.bpm);assertEquals(2,s.store.versions.list(s.project.id).size());
    }
    @Test public void explicitLibraryDeletionNamesVersionsAndCancelKeepsThem()throws Exception{
        String id=s.project.id;s.store.versions.create(s.project,"Named history");s.save();settle();
        ComposerProject copy=ComposerProject.read(s.project.json());copy.id=UUID.randomUUID().toString();s.store.versions.create(copy,"Independent copy");
        a.libraryFilter=1;a.chooseTab(4);Button menu=StudioScreenshotTest.findButtonContaining(a.page,"Project ⋯");assertNotNull(menu);
        menu.performClick();button(ShadowDialog.getLatestDialog(),"Delete").performClick();Dialog confirm=ShadowDialog.getLatestDialog();
        assertNotNull(ComposerRedesignScreenshotTest.text(confirm.getWindow().getDecorView(),"all of its versions"));button(confirm,"Cancel").performClick();assertEquals(1,s.store.versions.list(id).size());
        menu.performClick();button(ShadowDialog.getLatestDialog(),"Delete").performClick();button(ShadowDialog.getLatestDialog(),"Delete").performClick();settle();
        assertFalse(new File(s.store.versions.directory,id).exists());assertEquals(1,s.store.versions.list(copy.id).size());assertFalse(s.store.list().stream().anyMatch(project->project.id.equals(id)));
    }
    @Test public void asynchronousListDoesNotOpenAfterNavigation()throws Exception{
        screen.versionHistory();a.chooseTab(0);Dialog previous=ShadowDialog.getLatestDialog();settle();assertSame(previous,ShadowDialog.getLatestDialog());
    }
}
