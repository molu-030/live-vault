package pro.livemic.studio;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class ComposerReliabilityTest {
 @Test public void addingTrackRejectsUnionLimitWithoutChangingAnyPart() throws Exception {
  ComposerProject p=new ComposerProject();
  ComposerProject.Part a=p.parts.get(0), b=p.duplicate(a);
  for(int i=0;i<4;i++) b.tracks.add(new TrackConfig("extra",i,false));
  p=ComposerProject.read(p.json());
  String before=p.json().toString();
  try { p.addTrack(new TrackConfig("ninth",0,false)); fail(); }
  catch(IllegalArgumentException expected) { }
  assertEquals(before,p.json().toString());
 }
 @Test public void addingTrackRejectsFullLaterPartEvenWhenIdAlreadyExists() throws Exception {
  ComposerProject p=new ComposerProject();
  ComposerProject.Part a=p.parts.get(0), b=p.duplicate(a);
  for(int i=0;i<4;i++) b.tracks.add(new TrackConfig("extra",i,false));
  TrackConfig existing=b.tracks.get(4);
  String before=p.json().toString();
  try { p.addTrack(existing); fail(); }
  catch(IllegalArgumentException expected) { }
  assertEquals(before,p.json().toString());
 }
 @Test public void highRecordedChordReleasesExactlyTheNotesItStarts() throws Exception {
  ComposerProject p=new ComposerProject();
  TrackConfig t=new TrackConfig("voice",0,false); t.voice=true; p.addTrack(t);
  VoicePhrase ph=new VoicePhrase(); ph.trackId=t.id; ph.chordMode=true;
  VoicePhrase.Note n=new VoicePhrase.Note(); n.midi=127; n.length=1; n.velocity=100; n.quality=0; ph.notes.add(n);
  p.parts.get(0).phrases.add(ph);
  double[] events=new ComposerTransport().compile(p,-1).events;
  int ons=0,offs=0;
  for(int i=0;i<events.length;i+=9) if((int)events[i+2]==4) {
   if((int)events[i+1]==1) { assertEquals(127,(int)events[i+3]); ons++; }
   if((int)events[i+1]==0) { assertEquals(127,(int)events[i+3]); offs++; }
  }
  assertTrue(ons>0); assertEquals(ons,offs);
 }
}
