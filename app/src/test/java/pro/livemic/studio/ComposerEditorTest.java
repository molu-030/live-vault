package pro.livemic.studio;

import android.app.Dialog;
import android.view.View;
import android.view.ViewGroup;
import android.os.Bundle;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowLooper;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,instrumentedPackages={"pro.livemic.studio"},shadows={StudioScreenshotTest.Audio.class,StudioScreenshotTest.Composer.class,StudioScreenshotTest.Harmony.class})
@LooperMode(LooperMode.Mode.PAUSED)
public class ComposerEditorTest {
 MainActivity a; ComposerSession s; ComposerScreen screen;
 @Before public void setup() throws Exception {AppLanguage.set(RuntimeEnvironment.getApplication(),true);a=Robolectric.buildActivity(MainActivity.class).setup().get();a.handler.removeCallbacks(a.tick);a.composer.worker.submit(()->{}).get();ShadowLooper.idleMainLooper();a.handler.removeCallbacks(a.tick);a.chooseTab(7);s=a.composer;screen=a.composerScreen;}
 @After public void cleanup(){a.handler.removeCallbacks(a.tick);a.io.shutdown();AppLanguage.set(RuntimeEnvironment.getApplication(),false);}
 Button option(Dialog d,String text){Button b=StudioScreenshotTest.findButtonContaining(d.getWindow().getDecorView(),text);assertNotNull(text,b);return b;}
 android.widget.TextView key(View v,String label){
  if(v instanceof android.widget.TextView&&label.contentEquals(((android.widget.TextView)v).getText()))return (android.widget.TextView)v;
  if(v instanceof ViewGroup){ViewGroup group=(ViewGroup)v;for(int i=0;i<group.getChildCount();i++){android.widget.TextView found=key(group.getChildAt(i),label);if(found!=null)return found;}}return null;
 }
 @Test public void keyboardCancelKeepsPhraseAndSaveCanBeUndone()throws Exception {
  TrackConfig track=s.part().tracks.get(0);track.voice=true;VoicePhrase original=new VoicePhrase();original.trackId=track.id;
  VoicePhrase.Note note=new VoicePhrase.Note();note.beat=2;note.length=1;note.midi=67;note.velocity=90;original.notes.add(note);s.part().phrases.add(original);
  String before=s.project.json().toString();int undo=s.store.undo.size();
  screen.openKeyboardInput(0);Dialog dialog=ShadowDialog.getLatestDialog();key(dialog.getWindow().getDecorView(),"C").performClick();dialog.cancel();
  assertEquals(before,s.project.json().toString());assertEquals(undo,s.store.undo.size());
  screen.openKeyboardInput(0);dialog=ShadowDialog.getLatestDialog();key(dialog.getWindow().getDecorView(),"C").performClick();option(dialog,"Save and apply").performClick();
  VoicePhrase saved=s.part().phrases.get(0);assertNotSame(original,saved);assertEquals(1,original.notes.size());assertEquals(2,saved.notes.size());assertEquals(60,saved.notes.get(0).midi);assertTrue(s.part().tracks.get(0).voice);assertEquals(undo+1,s.store.undo.size());
  s.history(false);assertEquals(before,s.project.json().toString());
 }
 @Test public void staleKeyboardSaveCannotReplaceNewProject()throws Exception {
  screen.openKeyboardInput(0);Dialog dialog=ShadowDialog.getLatestDialog();key(dialog.getWindow().getDecorView(),"C").performClick();s.open(new ComposerProject());String before=s.project.json().toString();option(dialog,"Save and apply").performClick();assertEquals(before,s.project.json().toString());
 }
 @Test public void failedEditInvalidatesChordDialogAndRetryEditsCurrentProject(){
  screen.chord(0,0);Dialog menu=ShadowDialog.getLatestDialog();option(menu,"Change root").performClick();Dialog root=ShadowDialog.getLatestDialog();ComposerProject old=s.project;int undo=s.store.undo.size();s.edit(p->{throw new IllegalArgumentException("fixture");},false);assertNotSame(old,s.project);int initial=s.part().chords.get(0).get(0).root;StudioScreenshotTest.findButton(root.getWindow().getDecorView(),MusicTheory.ROOTS[(initial+1)%12]).performClick();assertEquals(initial,s.part().chords.get(0).get(0).root);assertEquals(undo,s.store.undo.size());
  screen.chord(0,0);option(ShadowDialog.getLatestDialog(),"Change root").performClick();StudioScreenshotTest.findButton(ShadowDialog.getLatestDialog().getWindow().getDecorView(),MusicTheory.ROOTS[(initial+1)%12]).performClick();assertEquals((initial+1)%12,s.part().chords.get(0).get(0).root);assertEquals(undo+1,s.store.undo.size());
 }
 @Test public void staleLevelCancelCannotWriteToNewProject(){
  screen.trackLevels(0);Dialog levels=ShadowDialog.getLatestDialog();s.open(new ComposerProject());StudioScreenshotTest.Composer.trackCalls.clear();levels.cancel();assertTrue(StudioScreenshotTest.Composer.trackCalls.isEmpty());
 }
 @Test public void deletingSoloTrackClearsSoloIndex(){
  screen.solo=0;screen.track(0);option(ShadowDialog.getLatestDialog(),"Delete track").performClick();assertEquals(-1,screen.solo);
 }
 StudioFader fader(View v){if(v instanceof StudioFader)return (StudioFader)v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){StudioFader f=fader(g.getChildAt(i));if(f!=null)return f;}}return null;}
 void setFader(StudioFader f,float value){assertNotNull(f);Bundle args=new Bundle();args.putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE,value);assertTrue(f.performAccessibilityAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.getId(),args));}
 @Test public void playingPartAloneReceivesTrackPreviewAndMute() throws Exception {
  ComposerProject.Part first=s.part();ComposerProject.Part second=s.project.duplicate(first);second.tracks.get(0).volume=.2f;second.tracks.get(0).muted=true;assertEquals(first.tracks.get(0).id,second.tracks.get(0).id);double[] st=s.state();st[1]=1;st[2]=0;s.selectedPart=1;screen.refresh();StudioScreenshotTest.Composer.trackCalls.clear();screen.trackLevels(0);Dialog levels=ShadowDialog.getLatestDialog();setFader(fader(levels.getWindow().getDecorView()),.7f);levels.cancel();screen.liveTrack(0);assertTrue(StudioScreenshotTest.Composer.trackCalls.isEmpty());
  s.selectedPart=0;screen.refresh();StudioScreenshotTest.Composer.trackCalls.clear();screen.trackLevels(0);levels=ShadowDialog.getLatestDialog();setFader(fader(levels.getWindow().getDecorView()),.35f);assertEquals(java.util.Arrays.asList(0),StudioScreenshotTest.Composer.trackCalls);levels.cancel();st[1]=0;
 }
 @Test public void switchingPartAndProjectClearsSoloWithoutMixWrites() throws Exception {
  s.project.duplicate(s.part());screen.refresh();screen.solo=0;StudioScreenshotTest.Composer.trackCalls.clear();StudioScreenshotTest.Composer.clearSoloCalls=0;Button part=StudioScreenshotTest.findButton(a.page,"›");assertNotNull(part);part.performClick();assertEquals(-1,screen.solo);assertTrue(StudioScreenshotTest.Composer.clearSoloCalls>0);assertTrue(StudioScreenshotTest.Composer.trackCalls.isEmpty());
  screen.solo=0;StudioScreenshotTest.Composer.clearSoloCalls=0;s.open(new ComposerProject());screen.refresh();assertEquals(-1,screen.solo);assertTrue(StudioScreenshotTest.Composer.clearSoloCalls>0);assertTrue(StudioScreenshotTest.Composer.trackCalls.isEmpty());
 }
 @Test public void lcdShowsPlayingPartAndSecondSplitChord() throws Exception {
  ComposerProject.Part first=s.part();ComposerProject.Chord second=ComposerProject.Chord.read(first.chords.get(0).get(0).json());second.root=(second.root+5)%12;second.automatic=false;first.chords.get(0).add(second);s.project.duplicate(first);s.selectedPart=1;screen.refresh();double[] st=s.state();st[0]=MusicTheory.split(s.project.meter)+.1;st[1]=1;st[2]=0;st[3]=0;screen.poll();String lcd=screen.lcdStatus.getText().toString();assertTrue(lcd,lcd.contains(first.label+" · Bar 1"));assertTrue(lcd,lcd.contains(screen.chordName(second)));st[1]=0;
 }

 @Test public void queuedPlayCannotRestartAfterStop() throws Exception {
  s.stop();s.worker.submit(()->{}).get();ShadowLooper.idleMainLooper();assertTrue(s.ready);AudioService.composerRequested=false;StudioScreenshotTest.Composer.transportCalls.clear();int published=StudioScreenshotTest.Composer.publishCalls;
  s.play();s.worker.submit(()->{}).get();assertTrue("worker compiled and posted start",StudioScreenshotTest.Composer.publishCalls>published);assertFalse(AudioService.composerRequested);s.stop();ShadowLooper.idleMainLooper();assertFalse(AudioService.composerRequested);assertFalse(StudioScreenshotTest.Composer.transportCalls.contains(1));assertTrue(StudioScreenshotTest.Composer.transportCalls.contains(0));
 }
 @Test public void queuedCountInCannotRestartAfterCancel() throws Exception {
  s.stop();s.worker.submit(()->{}).get();ShadowLooper.idleMainLooper();StudioScreenshotTest.Composer.current[1]=0;s.state()[1]=0;s.voiceOn=true;AudioService.active=true;AudioService.composerRequested=false;StudioScreenshotTest.Composer.transportCalls.clear();StudioScreenshotTest.Composer.recordCalls.clear();int published=StudioScreenshotTest.Composer.publishCalls;
  s.recordPhrase();assertTrue(s.capturing);s.worker.submit(()->{}).get();assertTrue("worker compiled and posted count-in",StudioScreenshotTest.Composer.publishCalls>published);s.cancelPhrase();ShadowLooper.idleMainLooper();assertFalse(s.capturing);assertFalse(AudioService.composerRequested);assertFalse(StudioScreenshotTest.Composer.transportCalls.contains(3));assertFalse(StudioScreenshotTest.Composer.recordCalls.contains(true));assertTrue(StudioScreenshotTest.Composer.recordCalls.contains(false));s.voiceOn=false;AudioService.active=false;
 }
 @Test public void corruptHistoryPreservesBothStacksAndCurrentProject() throws Exception {
  for(boolean redo:new boolean[]{false,true}){ComposerStore store=new ComposerStore(a);ComposerProject current=new ComposerProject();String before=current.json().toString();store.undo.add("undo marker");store.redo.add("redo marker");java.util.ArrayDeque<String> source=redo?store.redo:store.undo;source.add("{broken");java.util.List<String> undo=new java.util.ArrayList<>(store.undo),other=new java.util.ArrayList<>(store.redo);try{store.history(current,redo);fail("invalid history should fail");}catch(org.json.JSONException expected){}assertEquals(before,current.json().toString());assertEquals(undo,new java.util.ArrayList<>(store.undo));assertEquals(other,new java.util.ArrayList<>(store.redo));}
 }

}
