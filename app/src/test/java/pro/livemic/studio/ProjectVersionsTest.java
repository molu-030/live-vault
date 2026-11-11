package pro.livemic.studio;

import android.util.AtomicFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class ProjectVersionsTest {
    File directory;ProjectVersions versions;
    @Before public void setup()throws Exception{directory=Files.createTempDirectory("composer-history").toFile();versions=new ProjectVersions(directory);}
    @Test public void snapshotsRemainImmutableAcrossRenameRestartAndSourceEdits()throws Exception{
        ComposerProject project=new ComposerProject();project.name="First";project.bpm=96;
        ProjectVersions.Version version=versions.create(project,"Before bridge");
        File snapshot=new File(versions.directory,project.id+"/"+version.id+".json");byte[] bytes=Files.readAllBytes(snapshot.toPath());
        project.name="Edited";project.bpm=138;versions.rename(version,"Named arrangement");
        versions=new ProjectVersions(directory);ProjectVersions.Version reopened=versions.list(project.id).get(0);
        assertEquals("Named arrangement",reopened.name);assertEquals(version.createdAt,reopened.createdAt);
        assertArrayEquals(bytes,Files.readAllBytes(snapshot.toPath()));assertEquals("First",versions.readProject(project.id,version.id).name);
        assertEquals(96,versions.readProject(project.id,version.id).bpm);assertEquals(1,versions.readProject(project.id,version.id).json().getInt("version"));
    }
    @Test public void newestTwentyIncludesNamedVersionsAndProjectsStayIndependent()throws Exception{
        ComposerProject original=new ComposerProject();ProjectVersions.Version oldest=versions.create(original,"Keep named version");
        for(int n=1;n<=20;n++){original.bpm=80+n;versions.create(original,"Named "+n);}
        List<ProjectVersions.Version> retained=versions.list(original.id);assertEquals(20,retained.size());
        assertFalse(retained.stream().anyMatch(v->v.id.equals(oldest.id)));assertEquals("Named 20",retained.get(0).name);
        ComposerProject copy=ComposerProject.read(original.json());copy.id=UUID.randomUUID().toString();assertTrue(versions.list(copy.id).isEmpty());
        versions.create(copy,"Copy version");assertEquals(20,versions.list(original.id).size());assertEquals(1,versions.list(copy.id).size());
        try{versions.readProject(copy.id,retained.get(0).id);fail();}catch(IOException expected){}
    }
    @Test public void incompleteMetadataAndInterruptedSnapshotDoNotHideHealthyVersions()throws Exception{
        ComposerProject project=new ComposerProject();ProjectVersions.Version good=versions.create(project,"Healthy");
        File folder=new File(versions.directory,project.id);Files.write(new File(folder,"broken.meta.json").toPath(),"{broken".getBytes(StandardCharsets.UTF_8));
        File data=new File(folder,good.id+".json");AtomicFile atomic=new AtomicFile(data);FileOutputStream stream=atomic.startWrite();stream.write("broken".getBytes(StandardCharsets.UTF_8));atomic.failWrite(stream);
        assertEquals(1,versions.list(project.id).size());assertEquals(project.json().toString(),versions.readProject(project.id,good.id).json().toString());
    }
    @Test public void invalidNamesAndUnavailableStorageNeverProduceVisibleVersion()throws Exception{
        ComposerProject project=new ComposerProject();
        try{versions.create(project,"   ");fail();}catch(IllegalArgumentException expected){}assertTrue(versions.list(project.id).isEmpty());
        File blocked=Files.createTempDirectory("blocked-history").toFile();File file=new File(blocked,"versions");assertTrue(file.createNewFile());
        try{new ProjectVersions(blocked).create(project,"Fails");fail();}catch(IOException expected){}
    }
    @Test public void undoKeepsVersionsAndExplicitProjectDeleteRemovesOnlyItsHistory()throws Exception{
        android.content.Context context=org.robolectric.RuntimeEnvironment.getApplication();ComposerStore store=new ComposerStore(context);
        ComposerProject project=new ComposerProject();ProjectVersions.Version version=store.versions.create(project,"Permanent history");store.write(project,false);
        ComposerProject copy=ComposerProject.read(project.json());copy.id=UUID.randomUUID().toString();store.versions.create(copy,"Independent copy");
        store.checkpoint(project);project.bpm=140;store.history(project,false);
        assertEquals(1,store.versions.list(project.id).size());assertEquals(90,store.versions.readProject(project.id,version.id).bpm);
        store.delete(project.id);assertFalse(new File(store.versions.directory,project.id).exists());assertEquals(1,store.versions.list(copy.id).size());
    }
    @Test public void cancelledOrFailedStagedBackupAtLimitNeverPrunesNamedVersions()throws Exception{
        ComposerProject project=new ComposerProject();for(int i=0;i<20;i++)versions.create(project,"Named "+i);
        List<ProjectVersions.Version> before=versions.list(project.id);ProjectVersions.Version staged=versions.stage(project,"Automatic backup");
        assertEquals(20,versions.list(project.id).size());assertEquals(before.get(19).id,versions.list(project.id).get(19).id);
        versions.discardStaged(staged);assertEquals(20,versions.list(project.id).size());
        staged=versions.stage(project,"Will fail");File pending=new File(versions.directory,project.id+"/"+staged.id+".pending");assertTrue(pending.delete());
        try{versions.commitStaged(staged);fail();}catch(IOException expected){}versions.discardStaged(staged);
        assertEquals(before.get(19).id,versions.list(project.id).get(19).id);assertEquals(20,versions.list(project.id).size());
        ProjectVersions.Version committed=versions.stage(project,"Confirmed restore backup");versions.commitStaged(committed);
        assertEquals(20,versions.list(project.id).size());assertEquals(committed.id,versions.list(project.id).get(0).id);
        assertFalse(versions.list(project.id).stream().anyMatch(v->v.id.equals(before.get(19).id)));
    }
}
