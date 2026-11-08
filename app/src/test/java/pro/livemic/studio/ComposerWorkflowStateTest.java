package pro.livemic.studio;

import android.view.View;
import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,instrumentedPackages={"pro.livemic.studio"},shadows={StudioScreenshotTest.Audio.class,StudioScreenshotTest.Composer.class,StudioScreenshotTest.Harmony.class})
@LooperMode(LooperMode.Mode.PAUSED)
public class ComposerWorkflowStateTest {
 MainActivity a;ComposerSession s;ComposerScreen screen;
 @Before public void setup()throws Exception{
  // Robolectric replaces Application/preferences between tests, but this app singleton survives.
  java.lang.reflect.Field instance=ComposerSession.class.getDeclaredField("instance");instance.setAccessible(true);
  ComposerSession previous=(ComposerSession)instance.get(null);
  if(previous!=null&&!previous.worker.isShutdown()){
   for(int i=0;i<2;i++){previous.worker.submit(()->{}).get();ShadowLooper.idleMainLooper();}
   previous.worker.shutdown();
  }
  instance.set(null,null);
  AppLanguage.set(RuntimeEnvironment.getApplication(),true);a=Robolectric.buildActivity(MainActivity.class).setup().get();s=a.composer;settle();
  assertSame("Session must use this test's Application and preferences",a.getApplicationContext(),s.context);
  assertTrue("Session status language must match the English UI",AppLanguage.english(s.context));
  s.project=new ComposerProject();s.selectedPart=0;s.capturing=false;s.voiceOn=false;s.error="";s.message="";s.store.undo.clear();s.store.redo.clear();
  AudioService.active=false;AudioService.playbackActive=false;AudioService.composerRequested=false;
  StudioScreenshotTest.Composer.current=new double[]{0,0,0,0,32,-1,-1,0,0,0};StudioScreenshotTest.Composer.performance=new double[0];s.poll();
  a.chooseTab(7);screen=a.composerScreen;
 }
 void settle()throws Exception{a.handler.removeCallbacks(a.tick);for(int i=0;i<2;i++){s.worker.submit(()->{}).get();ShadowLooper.idleMainLooper();a.handler.removeCallbacks(a.tick);}}
 @After public void cleanup()throws Exception{if(s.capturing)s.cancelPhrase();settle();s.voiceOn=false;AudioService.active=false;a.io.shutdown();AppLanguage.set(RuntimeEnvironment.getApplication(),false);}
 @Test public void olderDraftCompletionCannotReplaceNewerSavingState()throws Exception{
  s.persist();s.worker.submit(()->{}).get();
  CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
  s.worker.execute(()->{entered.countDown();try{release.await(10,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}});
  assertTrue(entered.await(5,TimeUnit.SECONDS));
  try{s.edit(p->p.name="Latest edit",false);screen.poll();assertTrue(screen.draftStatus.getText().toString(),screen.draftStatus.getText().toString().contains("Saving"));
   ShadowLooper.idleMainLooper();assertEquals(ComposerSession.SaveState.SAVING,s.draftSave.phase);
  }finally{release.countDown();}
  settle();assertEquals(ComposerSession.SaveState.SAVED,s.draftSave.phase);assertTrue(s.draftSave.savedAt>0);assertEquals("Latest edit",s.store.draft().name);
  assertTrue(s.saveStatus(false),s.saveStatus(false).contains("Unsaved changes"));
 }
 @Test public void reopenedSameIdAndLaterEditsRejectStaleLibraryCompletions()throws Exception{
  s.save();s.worker.submit(()->{}).get();ComposerProject reopened=ComposerProject.read(s.project.json());s.open(reopened);settle();
  assertEquals(0,s.librarySave.savedAt);assertTrue(s.saveStatus(false),s.saveStatus(false).contains("Unsaved changes"));assertTrue(s.message.isEmpty());
  s.save();s.worker.submit(()->{}).get();s.edit(p->p.name="After save snapshot",false);settle();
  assertEquals(0,s.librarySave.savedAt);assertTrue(s.saveStatus(false),s.saveStatus(false).contains("Unsaved changes"));
  s.save();settle();assertEquals(ComposerSession.SaveState.SAVED,s.librarySave.phase);assertTrue(s.saveStatus(false).contains("Saved"));
 }
 @Test public void saveFailuresStayVisibleAndRetriesSaveCurrentProject()throws Exception{
  settle();File directory=s.store.directory,backup=new File(directory.getParentFile(),"composer-test-backup-"+java.util.UUID.randomUUID());
  assertTrue(directory.renameTo(backup));assertTrue(directory.createNewFile());
  try{s.persist();s.save();settle();screen.poll();assertEquals(ComposerSession.SaveState.FAILED,s.draftSave.phase);assertEquals(ComposerSession.SaveState.FAILED,s.librarySave.phase);assertEquals(View.VISIBLE,screen.retryDraft.getVisibility());assertEquals(View.VISIBLE,screen.retryLibrary.getVisibility());assertTrue(screen.draftStatus.getText().toString(),screen.draftStatus.getText().toString().contains("Save failed"));assertTrue(screen.libraryStatus.getText().toString(),screen.libraryStatus.getText().toString().contains("Save failed"));}
  finally{assertTrue(directory.delete());assertTrue(backup.renameTo(directory));}
  screen.retryDraft.performClick();settle();screen.poll();assertEquals(ComposerSession.SaveState.SAVED,s.draftSave.phase);assertEquals(View.GONE,screen.retryDraft.getVisibility());assertEquals(s.project.json().toString(),s.store.draft().json().toString());
  screen.retryLibrary.performClick();settle();screen.poll();assertEquals(ComposerSession.SaveState.SAVED,s.librarySave.phase);assertEquals(View.GONE,screen.retryLibrary.getVisibility());
 }
 @Test public void undoRedoButtonsExposeHistoryAndLockWhileRecording()throws Exception{
  assertFalse(screen.undoButton.isEnabled());assertFalse(screen.redoButton.isEnabled());
  assertEquals(a.dp(48),screen.undoButton.getLayoutParams().height);assertEquals(a.dp(48),screen.redoButton.getLayoutParams().height);
  s.edit(p->p.name="Changed",false);screen.poll();assertTrue(screen.undoButton.isEnabled());screen.undoButton.performClick();assertTrue(screen.redoButton.isEnabled());
  screen.redoButton.performClick();assertEquals("Changed",s.project.name);s.capturing=true;screen.poll();assertFalse(screen.undoButton.isEnabled());assertFalse(screen.redoButton.isEnabled());s.capturing=false;
 }
 TrackConfig microphone(int midi){TrackConfig t=s.part().tracks.get(0);t.voice=true;t.phraseSource="microphone";t.name="Lead take";VoicePhrase ph=new VoicePhrase();ph.trackId=t.id;VoicePhrase.Note n=new VoicePhrase.Note();n.beat=1;n.length=1;n.midi=midi;n.velocity=90;ph.notes.add(n);s.part().phrases.add(ph);return t;}
 void complete(){StudioScreenshotTest.Composer.performance=new double[]{4,1,72,90,0,.8,0,0,5,0,72,0,0,.8,0,0};StudioScreenshotTest.Composer.current[8]=3;s.poll();}
 @Test public void recordingReadoutUsesActualPartAndStaysFixedDuringCapture()throws Exception{
  microphone(67);ComposerProject.Part first=s.part(),second=s.project.duplicate(first);second.label="B";s.selectedPart=1;
  StudioScreenshotTest.Composer.current=new double[]{2,1,0,0,32,60,60,.1,0,0};s.voiceOn=true;AudioService.active=true;
  a.chooseTab(8);assertTrue(screen.recordingTarget.getText().toString(),screen.recordingTarget.getText().toString().contains("Part "+first.label+" → Lead take"));
  s.recordPhrase();screen.refresh();String fixed=screen.recordingTarget.getText().toString();assertTrue(fixed.contains("fixed"));
  StudioScreenshotTest.Composer.current[2]=1;screen.poll();assertEquals(fixed,screen.recordingTarget.getText().toString());s.cancelPhrase();
  second.tracks.get(0).phraseSource="keyboard";screen.poll();assertTrue(screen.recordingTarget.getText().toString().contains("Part B → New voice track"));
 }
 @Test public void previousProjectSaveCannotMarkNewProjectAsSaved()throws Exception{
  s.save();s.worker.submit(()->{}).get();ComposerProject next=new ComposerProject();next.name="Different project";s.open(next);settle();
  assertEquals("Different project",s.project.name);assertEquals(0,s.librarySave.savedAt);assertEquals(ComposerSession.SaveState.IDLE,s.librarySave.phase);assertTrue(s.message.isEmpty());
 }
 @Test public void namedCopyUsesNewIdAndKeepsOriginalLibraryEntry()throws Exception{
  s.project.name="Original";s.save();settle();String original=s.project.id;s.duplicate("Named snapshot");settle();
  assertNotEquals(original,s.project.id);assertEquals("Named snapshot",s.project.name);assertTrue(s.store.list().stream().anyMatch(p->p.id.equals(original)&&p.name.equals("Original")));assertTrue(s.store.list().stream().anyMatch(p->p.id.equals(s.project.id)&&p.name.equals("Named snapshot")));
 }
 @Test public void turningVoiceOffAndSavingPreserveOnlyAnAlreadyValidComparison()throws Exception{
  microphone(67);StudioScreenshotTest.Composer.current=new double[]{2,1,0,0,32,60,60,.1,0,0};s.voiceOn=true;AudioService.active=true;s.recordPhrase();complete();
  StudioScreenshotTest.Composer.transportCalls.clear();s.voice(false);assertFalse(s.voiceOn);assertTrue(s.canComparePhrase());
  s.selectPhraseTake(false,false);assertEquals(67,s.part().phrases.get(0).notes.get(0).midi);
  s.save();settle();assertTrue(s.canComparePhrase());assertFalse(s.voiceOn);
  s.selectPhraseTake(true,false);assertEquals(72,s.part().phrases.get(0).notes.get(0).midi);assertTrue(StudioScreenshotTest.Composer.transportCalls.isEmpty());
  s.edit(p->p.name="An actual edit",false);assertFalse(s.canComparePhrase());String changed=s.project.json().toString();
  s.voice(false);s.save();settle();assertFalse(s.canComparePhrase());s.selectPhraseTake(false,false);assertEquals(changed,s.project.json().toString());
 }
 @Test public void comparisonCannotSurviveTransposeReopenOrAnotherRecording()throws Exception{
  for(int scenario=0;scenario<3;scenario++){
   s.project=new ComposerProject();s.selectedPart=0;microphone(67);
   StudioScreenshotTest.Composer.current=new double[]{2,1,0,0,32,60,60,.1,0,0};s.voiceOn=true;AudioService.active=true;s.recordPhrase();complete();assertTrue(s.canComparePhrase());
   if(scenario==0)s.edit(p->p.transpose(1),false);
   else if(scenario==1)s.open(ComposerProject.read(s.project.json()));
   else{StudioScreenshotTest.Composer.current[8]=0;s.recordPhrase();assertTrue(s.capturing);assertFalse(s.canComparePhrase());s.cancelPhrase();}
   assertFalse(s.canComparePhrase());String after=s.project.json().toString();s.selectPhraseTake(false,false);assertEquals(after,s.project.json().toString());
  }
 }
 @Test public void phraseComparisonChangesOnlyTargetAndInvalidatesOnOtherEdits()throws Exception{
  TrackConfig target=microphone(67);s.part().phrases.get(0).notes.get(0).curve.add(new double[]{.2,25,.7});String previousPhrase=s.part().phrases.get(0).json().toString();ComposerProject.Part other=s.project.duplicate(s.part());String otherBefore=other.json().toString();
  StudioScreenshotTest.Composer.current=new double[]{2,1,0,0,32,60,60,.1,0,0};s.voiceOn=true;AudioService.active=true;s.recordPhrase();complete();assertTrue(s.canComparePhrase());
  int history=s.store.undo.size();
  for(int i=0;i<4;i++){s.selectPhraseTake(false,false);s.selectPhraseTake(true,false);}
  assertEquals("Take auditions keep the capture checkpoint and earlier edits",history,s.store.undo.size());
  StudioScreenshotTest.Composer.transportCalls.clear();s.voiceOn=false;s.selectPhraseTake(false,false);
  assertEquals(previousPhrase,s.part().phrases.get(0).json().toString());assertTrue(s.canComparePhrase());assertEquals(otherBefore,s.project.parts.get(1).json().toString());assertFalse(s.voiceOn);assertTrue(StudioScreenshotTest.Composer.transportCalls.isEmpty());
  s.selectPhraseTake(true,false);assertEquals(72,s.part().phrases.get(0).notes.get(0).midi);s.selectPhraseTake(false,true);assertEquals(67,s.part().phrases.get(0).notes.get(0).midi);assertFalse(s.canComparePhrase());
  s.voiceOn=true;StudioScreenshotTest.Composer.current[8]=0;s.recordPhrase();complete();assertTrue(s.canComparePhrase());
  a.chooseTab(8);android.widget.Button oldButton=screen.comparePhrase;assertEquals(View.VISIBLE,oldButton.getVisibility());
  s.edit(p->p.parts.get(0).tracks.get(1).volume=.2f,false);assertFalse(s.canComparePhrase());String after=s.project.json().toString();
  oldButton.performClick();s.selectPhraseTake(false,false);assertEquals(after,s.project.json().toString());
 }
}
