package pro.livemic.studio;

import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowLooper;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,instrumentedPackages={"pro.livemic.studio"},shadows={StudioScreenshotTest.Audio.class,StudioScreenshotTest.Composer.class,StudioScreenshotTest.Harmony.class})
@LooperMode(LooperMode.Mode.PAUSED)
public class ComposerCaptureTargetTest {
 MainActivity a;ComposerSession s;
 @Before public void setup()throws Exception{
  a=Robolectric.buildActivity(MainActivity.class).setup().get();a.handler.removeCallbacks(a.tick);s=a.composer;
  s.worker.submit(()->{}).get();ShadowLooper.idleMainLooper();a.handler.removeCallbacks(a.tick);
  s.project=new ComposerProject();s.selectedPart=0;s.store.undo.clear();s.store.redo.clear();s.ready=true;s.voiceOn=true;AudioService.active=true;
  StudioScreenshotTest.Composer.performance=new double[0];StudioScreenshotTest.Composer.current=new double[]{2,1,0,0,32,60,60,.1,0,0};s.poll();
 }
 @After public void cleanup(){if(s.capturing)s.cancelPhrase();s.voiceOn=false;AudioService.active=false;a.handler.removeCallbacks(a.tick);a.io.shutdown();}
 TrackConfig phrase(String source,int midi){TrackConfig t=s.part().tracks.get(0);t.voice=true;t.phraseSource=source;addPhrase(s.part(),t,midi);return t;}
 void addPhrase(ComposerProject.Part part,TrackConfig t,int midi){VoicePhrase p=new VoicePhrase();p.trackId=t.id;VoicePhrase.Note n=new VoicePhrase.Note();n.midi=midi;n.beat=1;n.length=1;n.velocity=90;p.notes.add(n);part.phrases.add(p);}
 void complete(){StudioScreenshotTest.Composer.performance=new double[]{4,1,72,90,0,.8,0,0,5,0,72,0,0,.8,0,0};StudioScreenshotTest.Composer.current[8]=3;s.poll();assertFalse(s.capturing);}
 VoicePhrase find(ComposerProject.Part p,String id){for(VoicePhrase ph:p.phrases)if(ph.trackId.equals(id))return ph;return null;}
 @Test public void keyboardAndUnknownLegacyPhrasesSurviveMicrophoneCapture()throws Exception{
  for(String source:new String[]{"keyboard",""}){
   s.project=new ComposerProject();TrackConfig old=phrase(source,67);String original=find(s.part(),old.id).json().toString();
   StudioScreenshotTest.Composer.current[8]=0;s.poll();s.recordPhrase();assertTrue(s.capturing);complete();
   assertEquals(5,s.part().tracks.size());assertEquals(original,find(s.part(),old.id).json().toString());
   TrackConfig microphone=s.part().tracks.get(4);assertEquals("microphone",microphone.phraseSource);assertEquals(72,find(s.part(),microphone.id).notes.get(0).midi);
  }
 }
 @Test public void knownMicrophoneRerecordAtCapacityIsTransactional()throws Exception{
  TrackConfig mic=phrase("microphone",67);while(s.part().tracks.size()<8)s.project.addTrack(new TrackConfig("extra",0,false));String before=s.project.json().toString();
  s.recordPhrase();assertTrue(s.capturing);s.cancelPhrase();assertEquals(before,s.project.json().toString());
  s.recordPhrase();assertTrue(s.capturing);assertEquals(before,s.project.json().toString());complete();assertEquals(8,s.part().tracks.size());assertEquals(72,find(s.part(),mic.id).notes.get(0).midi);
  s.history(false);assertEquals(before,s.project.json().toString());s.history(true);assertEquals("microphone",s.part().tracks.get(0).phraseSource);assertEquals(72,find(s.part(),mic.id).notes.get(0).midi);
 }
 @Test public void capacityAndTargetFollowPlayingPartInsteadOfSelectedPart()throws Exception{
  TrackConfig mic=phrase("microphone",67);while(s.part().tracks.size()<8)s.project.addTrack(new TrackConfig("extra",0,false));ComposerProject.Part other=s.project.duplicate(s.part());other.tracks.get(0).phraseSource="keyboard";s.selectedPart=1;
  s.recordPhrase();assertTrue(s.capturing);complete();assertEquals(72,find(s.project.parts.get(0),mic.id).notes.get(0).midi);assertEquals(67,find(s.project.parts.get(1),mic.id).notes.get(0).midi);
  // Now the selected part has a microphone, but the actual playing part has none.
  s.project.parts.get(0).tracks.get(0).phraseSource="keyboard";s.project.parts.get(1).tracks.get(0).phraseSource="microphone";StudioScreenshotTest.Composer.current[8]=0;s.poll();String before=s.project.json().toString();s.recordPhrase();assertFalse(s.capturing);assertEquals(before,s.project.json().toString());assertFalse(s.error.isEmpty());
 }
 @Test public void recordingRefreshesNativePartBeforeCapacityAndTargetSelection()throws Exception{
  TrackConfig mic=phrase("microphone",67);while(s.part().tracks.size()<8)s.project.addTrack(new TrackConfig("extra",0,false));
  ComposerProject.Part other=s.project.duplicate(s.part());other.tracks.get(0).phraseSource="keyboard";
  assertEquals(0,(int)s.state()[2]);StudioScreenshotTest.Composer.current[2]=1;
  // The service has not polled the part boundary: native is in the full keyboard part.
  String before=s.project.json().toString();StudioScreenshotTest.Composer.recordCalls.clear();s.recordPhrase();
  assertFalse(s.capturing);assertFalse(StudioScreenshotTest.Composer.recordCalls.contains(true));assertEquals(before,s.project.json().toString());assertEquals(1,(int)s.state()[2]);
  // Native returns to the microphone part while both selection and cached state stay in B.
  s.selectedPart=1;StudioScreenshotTest.Composer.current[2]=0;s.recordPhrase();assertTrue(s.capturing);complete();
  assertEquals(72,find(s.project.parts.get(0),mic.id).notes.get(0).midi);assertEquals(67,find(s.project.parts.get(1),mic.id).notes.get(0).midi);
 }
 @Test public void globalUnionLimitRejectsBeforeNativeCaptureEvenWithLocalSpace()throws Exception{
  ComposerProject.Part other=s.project.duplicate(s.part());for(int i=0;i<4;i++)other.tracks.add(new TrackConfig("other",i,false));assertEquals(4,s.part().tracks.size());String before=s.project.json().toString();StudioScreenshotTest.Composer.recordCalls.clear();s.recordPhrase();assertFalse(s.capturing);assertFalse(StudioScreenshotTest.Composer.recordCalls.contains(true));assertEquals(before,s.project.json().toString());
 }
 @Test public void drumsNeverBecomeMicrophoneReplacement()throws Exception{
  TrackConfig legacy=phrase("microphone",67);legacy.bank=128;s.recordPhrase();assertTrue(s.capturing);complete();assertEquals(67,find(s.part(),legacy.id).notes.get(0).midi);assertEquals(5,s.part().tracks.size());assertFalse(s.part().tracks.get(4).drums());
 }
 @Test public void changedProjectCannotReceiveOrBeReplacedByStaleCapture()throws Exception{
  phrase("microphone",67);s.recordPhrase();assertTrue(s.capturing);ComposerProject replacement=new ComposerProject();replacement.name="New project";s.project=replacement;String before=replacement.json().toString();complete();assertSame(replacement,s.project);assertEquals(before,s.project.json().toString());assertTrue(s.store.undo.isEmpty());
 }
 @Test public void savingDuringCaptureDoesNotInvalidateUnchangedProject()throws Exception{
  TrackConfig keyboard=phrase("keyboard",67);String original=find(s.part(),keyboard.id).json().toString();s.recordPhrase();assertTrue(s.capturing);
  int captureRevision=s.revision;s.save();s.worker.submit(()->{}).get();ShadowLooper.idleMainLooper();assertTrue(s.revision>captureRevision);assertTrue(s.capturing);
  complete();assertEquals(original,find(s.part(),keyboard.id).json().toString());assertEquals(5,s.part().tracks.size());
  TrackConfig mic=s.part().tracks.get(4);assertEquals("microphone",mic.phraseSource);assertEquals(72,find(s.part(),mic.id).notes.get(0).midi);
  s.worker.submit(()->{}).get();ComposerProject saved=s.store.draft();assertNotNull(saved);assertEquals(original,find(saved.parts.get(0),keyboard.id).json().toString());assertEquals(72,find(saved.parts.get(0),mic.id).notes.get(0).midi);
 }
 @Test public void sameProjectContentMutationRejectsStaleCaptureWithoutLosingChanges()throws Exception{
  TrackConfig mic=phrase("microphone",67);s.recordPhrase();assertTrue(s.capturing);s.project.name="Changed during capture";String changed=s.project.json().toString();
  complete();assertEquals(changed,s.project.json().toString());assertEquals(67,find(s.part(),mic.id).notes.get(0).midi);assertTrue(s.store.undo.isEmpty());assertFalse(s.error.isEmpty());
 }
 @Test public void phraseSourceIsOptionalAndStrictAiRoundTripPreservesIt()throws Exception{
  TrackConfig legacy=s.part().tracks.get(0);assertFalse(legacy.json().has("phraseSource"));assertEquals("",TrackConfig.read(legacy.json()).phraseSource);
  for(String source:new String[]{"microphone","keyboard",""}){legacy.phraseSource=source;ComposerProject imported=ComposerLlmImport.parse(s.project.json().toString());assertEquals(source,imported.parts.get(0).tracks.get(0).phraseSource);}
  JSONObject json=s.project.json();JSONObject track=json.getJSONArray("parts").getJSONObject(0).getJSONArray("tracks").getJSONObject(0);track.put("phraseSource","untrusted");try{ComposerLlmImport.parse(json.toString());fail();}catch(ComposerLlmImport.Invalid expected){assertTrue(expected.getMessage().contains("phraseSource"));}
 }
}
