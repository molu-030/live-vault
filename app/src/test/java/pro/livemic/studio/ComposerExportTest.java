package pro.livemic.studio;

import java.io.*;
import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowLooper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28, instrumentedPackages={"pro.livemic.studio"}, shadows={StudioScreenshotTest.Audio.class,StudioScreenshotTest.Composer.class,StudioScreenshotTest.Harmony.class}) @LooperMode(LooperMode.Mode.PAUSED)
public class ComposerExportTest {
 static Map<String,byte[]> unzip(byte[] data)throws Exception{Map<String,byte[]> files=new LinkedHashMap<>();try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(data))){ZipEntry e;while((e=zip.getNextEntry())!=null){assertFalse(e.isDirectory());assertFalse(e.getName().contains("/"));assertNull("duplicate entry",files.put(e.getName(),zip.readAllBytes()));}}return files;}
 static String string(byte[] bytes){return new String(bytes,StandardCharsets.UTF_8);}
 static String hash(byte[] bytes)throws Exception{StringBuilder out=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(bytes))out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();}
 @Test public void portableArchivePreservesCompleteProjectAndDescribesCompoundTime()throws Exception{
  ComposerProject p=new ComposerProject();p.name="../../내 이름";p.meter=2;p.bpm=90;p.seed=1234567;
  ComposerProject.Part part=p.parts.get(0);part.rhythm=7;ComposerProject.Chord chord=new ComposerProject.Chord();chord.automatic=false;chord.root=7;chord.quality=6;chord.bass=11;part.chords.get(0).add(chord);
  TrackConfig voice=new TrackConfig("내 목소리",24,false);voice.voice=true;p.addTrack(voice);VoicePhrase phrase=new VoicePhrase();phrase.trackId=voice.id;VoicePhrase.Note note=new VoicePhrase.Note();note.midi=64;note.beat=.375;note.length=1.25;note.velocity=99;note.expression=.7f;note.bend=12;note.curve.add(new double[]{.2,23,.6});phrase.notes.add(note);part.phrases.add(phrase);
  ComposerProject.Part other=p.duplicate(part);p.order.add(new ComposerProject.Order(other.id,2));String before=p.json().toString();ByteArrayOutputStream out=new ByteArrayOutputStream();ComposerLlmExport.write(p,"Keep the melody",out);assertEquals(before,p.json().toString());
  Map<String,byte[]> files=unzip(out.toByteArray());assertEquals(new HashSet<>(Arrays.asList("project.json","arrangement.md","llm-prompt.md","schema.json","manifest.json","README.md")),files.keySet());
  JSONObject project=new JSONObject(string(files.get("project.json")));ComposerProject restored=ComposerProject.read(project);assertEquals(p.json().toString(),restored.json().toString());assertEquals(1234567,restored.seed);assertEquals(11,restored.parts.get(0).chords.get(0).get(1).bass);assertEquals(23,restored.parts.get(0).phrases.get(0).notes.get(0).curve.get(0)[1],0);
  String arrangement=string(files.get("arrangement.md"));assertTrue(arrangement.contains("6/8"));assertTrue(arrangement.contains("1.5"));assertTrue(arrangement.contains("quarter"));assertTrue(string(files.get("llm-prompt.md")).contains("Keep the melody"));assertNotNull(new JSONObject(string(files.get("schema.json"))));
  JSONObject manifest=new JSONObject(string(files.get("manifest.json")));assertEquals(p.id,manifest.getString("projectId"));JSONObject hashes=manifest.getJSONObject("sha256");for(String name:Arrays.asList("project.json","arrangement.md","llm-prompt.md","schema.json","README.md"))assertEquals(hash(files.get(name)),hashes.getString(name));
 }
 @Test public void arrangementDistinguishesActivePhraseSourcesAndPreservesInactiveRoles()throws Exception{
  ComposerProject p=new ComposerProject();ComposerProject.Part part=p.parts.get(0);TrackConfig track=part.tracks.get(0);
  VoicePhrase phrase=new VoicePhrase();phrase.trackId=track.id;VoicePhrase.Note note=new VoicePhrase.Note();note.midi=60;note.length=1;note.velocity=90;phrase.notes.add(note);part.phrases.add(phrase);
  String prefix="- "+track.name+" ["+track.id+"]: ";String[] sources={"microphone","keyboard",""},roles={"recorded voice phrase","keyboard phrase","recorded phrase"};
  for(int i=0;i<sources.length;i++){
   track.phraseSource=sources[i];track.voice=true;String before=p.json().toString();
   assertTrue(ComposerLlmExport.arrangement(p).contains(prefix+roles[i]+", GM program "));assertEquals(before,p.json().toString());
   track.voice=false;before=p.json().toString();String summary=ComposerLlmExport.arrangement(p);
   assertTrue(summary.contains(prefix+"instrument, GM program "));assertTrue(summary.contains("1 phrase notes"));assertEquals(before,p.json().toString());
  }
  track.bank=128;assertTrue(ComposerLlmExport.arrangement(p).contains(prefix+"drums, GM program "));
 }
 public static class TestProvider extends ContentProvider {
  static File destination;public boolean onCreate(){return true;}public String getType(Uri u){return "application/zip";}public Cursor query(Uri u,String[] p,String s,String[] a,String o){return null;}public Uri insert(Uri u,ContentValues v){return null;}public int delete(Uri u,String s,String[] a){return 0;}public int update(Uri u,ContentValues v,String s,String[] a){return 0;}
  @Override public ParcelFileDescriptor openFile(Uri u,String mode)throws FileNotFoundException{return ParcelFileDescriptor.open(destination,ParcelFileDescriptor.MODE_CREATE|ParcelFileDescriptor.MODE_TRUNCATE|ParcelFileDescriptor.MODE_WRITE_ONLY);}
 }
 @Test public void documentPickerCancellationAndProviderWriteUseDetachedSnapshot()throws Exception{
  MainActivity a=Robolectric.buildActivity(MainActivity.class).setup().get();a.handler.removeCallbacks(a.tick);a.composer.worker.submit(()->{}).get();ShadowLooper.idleMainLooper();a.handler.removeCallbacks(a.tick);
  ComposerProject p=a.composer.project;p.name="../../evil/name";String original=p.json().toString();ComposerExchange exchange=new ComposerExchange(a);
  exchange.showExport(p);AlertDialog d=(AlertDialog)ShadowDialog.getLatestDialog();d.getButton(AlertDialog.BUTTON_POSITIVE).performClick();ShadowLooper.idleMainLooper();Intent picker=org.robolectric.Shadows.shadowOf(a).getNextStartedActivityForResult().intent;assertEquals(Intent.ACTION_CREATE_DOCUMENT,picker.getAction());String title=picker.getStringExtra(Intent.EXTRA_TITLE);assertFalse(title.contains("/"));assertFalse(title.contains("\\"));assertTrue(title.endsWith(".zip"));assertTrue(exchange.onActivityResult(40,Activity.RESULT_CANCELED,null));assertEquals(original,p.json().toString());
  exchange.showExport(p);d=(AlertDialog)ShadowDialog.getLatestDialog();d.getButton(AlertDialog.BUTTON_POSITIVE).performClick();ShadowLooper.idleMainLooper();org.robolectric.Shadows.shadowOf(a).getNextStartedActivityForResult();p.name="Changed after picker opened";android.os.Bundle state=new android.os.Bundle();exchange.saveState(state);ComposerExchange restoredExchange=new ComposerExchange(a);restoredExchange.restoreState(state);
  TestProvider.destination=new File(a.getCacheDir(),"export-provider.zip");TestProvider provider=new TestProvider();android.content.pm.ProviderInfo info=new android.content.pm.ProviderInfo();info.authority="export.fixture";provider.attachInfo(a,info);org.robolectric.shadows.ShadowContentResolver.registerProviderInternal("export.fixture",provider);assertTrue(restoredExchange.onActivityResult(40,Activity.RESULT_OK,new Intent().setData(Uri.parse("content://export.fixture/output"))));for(Thread t:Thread.getAllStackTraces().keySet())if(t.getName().equals("composer-export"))t.join(10000);ShadowLooper.idleMainLooper();assertTrue("Provider write failed: "+org.robolectric.shadows.ShadowToast.getTextOfLatestToast(),TestProvider.destination.exists());Map<String,byte[]> files=unzip(java.nio.file.Files.readAllBytes(TestProvider.destination.toPath()));assertEquals("../../evil/name",new JSONObject(string(files.get("project.json"))).getString("name"));assertEquals("Changed after picker opened",p.name);a.io.shutdown();
 }

}
