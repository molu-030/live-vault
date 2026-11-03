package pro.livemic.studio;
import java.io.*;
import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import org.robolectric.*;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.*;
import java.nio.charset.StandardCharsets;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28,instrumentedPackages={"pro.livemic.studio"},shadows={StudioScreenshotTest.Audio.class,StudioScreenshotTest.Composer.class,StudioScreenshotTest.Harmony.class}) @LooperMode(LooperMode.Mode.PAUSED)
public class ComposerImportTest {
 static ComposerProject fixture()throws Exception{
  ComposerProject p=new ComposerProject();p.meter=2;p.parts.get(0).rhythm=7;p.name="내 원곡";p.seed=123;
  ComposerProject.Part part=p.parts.get(0);ComposerProject.Chord chord=new ComposerProject.Chord();chord.automatic=false;chord.root=7;chord.quality=6;chord.bass=11;part.chords.get(0).add(chord);
  TrackConfig t=new TrackConfig("내 목소리",24,false);t.voice=true;p.addTrack(t);VoicePhrase ph=new VoicePhrase();ph.trackId=t.id;VoicePhrase.Note n=new VoicePhrase.Note();n.midi=64;n.beat=.375;n.length=1.25;n.velocity=99;n.expression=.7f;n.curve.add(new double[]{.2,23,.6});ph.notes.add(n);part.phrases.add(ph);return p;
 }
 static JSONObject copy(JSONObject j)throws Exception{return new JSONObject(j.toString());}
 static void reject(JSONObject j)throws Exception{try{ComposerLlmImport.parse(j.toString());fail("invalid project was accepted");}catch(ComposerLlmImport.Invalid expected){}}
 @Test public void completeCanonicalProjectIsAcceptedWithoutChanges()throws Exception{
  ComposerProject p=fixture();ComposerProject restored=ComposerLlmImport.parse(p.json().toString());assertEquals(p.json().toString(),restored.json().toString());
  ByteArrayOutputStream out=new ByteArrayOutputStream();ComposerLlmExport.write(p,"",out);String json=ComposerExportTest.string(ComposerExportTest.unzip(out.toByteArray()).get("project.json"));assertEquals(p.json().toString(),ComposerLlmImport.parse(json).json().toString());
 }
 @Test public void hipHopAndExpandedPatternsSurviveSaveDuplicateExportAndImport()throws Exception {
  ComposerProject p=new ComposerProject();ComposerProject.Part part=p.parts.get(0);part.rhythm=8;part.tracks.get(0).pattern=6;part.tracks.get(1).pattern=7;
  String before=p.json().toString();assertEquals(before,ComposerProject.read(p.json()).json().toString());
  ComposerProject.Part duplicate=p.duplicate(part);assertEquals(8,duplicate.rhythm);assertEquals(6,duplicate.tracks.get(0).pattern);assertEquals(7,duplicate.tracks.get(1).pattern);
  ByteArrayOutputStream out=new ByteArrayOutputStream();ComposerLlmExport.write(p,"",out);java.util.Map<String,byte[]> files=ComposerExportTest.unzip(out.toByteArray());
  String json=ComposerExportTest.string(files.get("project.json"));assertEquals(p.json().toString(),ComposerLlmImport.parse(json).json().toString());
  assertTrue(ComposerExportTest.string(files.get("arrangement.md")).contains("Hip-hop"));
  JSONObject schema=new JSONObject(ComposerExportTest.string(files.get("schema.json")));assertEquals(8,schema.getJSONObject("$defs").getJSONObject("part").getJSONObject("properties").getJSONObject("rhythm").getInt("maximum"));
 }
 @Test public void importedRhythmsMustFitTheirMeter()throws Exception {
  for(int meter=0;meter<3;meter++)for(int rhythm=0;rhythm<MusicTheory.RHYTHMS.length;rhythm++){
   ComposerProject p=new ComposerProject();p.meter=meter;p.parts.get(0).rhythm=rhythm;
   boolean fits=meter==0?(rhythm<6||rhythm==8):meter==1?rhythm==6:rhythm==7;
   assertEquals(fits,MusicTheory.rhythmFits(rhythm,meter));
   if(fits)assertEquals(rhythm,ComposerLlmImport.parse(p.json().toString()).parts.get(0).rhythm);else reject(p.json());
  }
 }
 @Test public void legacyRecordedExpressionAndNotesBeyondResizedPartRoundTrip()throws Exception {
  ComposerProject p=fixture();ComposerProject.Part part=p.parts.get(0);VoicePhrase.Note n=part.phrases.get(0).notes.get(0);n.length=.5;n.bend=1200;n.curve.clear();n.curve.add(new double[]{.65,1200,.6});p.resize(part,4);n.beat=24;p.seed=Long.MAX_VALUE;
  ComposerProject parsed=ComposerLlmImport.parse(p.json().toString());assertEquals(p.json().toString(),parsed.json().toString());
  String overflow=p.json().toString().replace("\"seed\":9223372036854775807","\"seed\":9223372036854775808");try{ComposerLlmImport.parse(overflow);fail("overflow accepted");}catch(ComposerLlmImport.Invalid expected){}
 }
 @Test public void rejectsInvalidRangesReferencesDuplicatesAndTiming()throws Exception{
  JSONObject original=fixture().json(),j;JSONObject part;
  j=copy(original);j.put("version",2);reject(j);
  j=copy(original);j.getJSONArray("parts").getJSONObject(0).getJSONArray("chords").getJSONArray(0).getJSONObject(0).put("root",99);reject(j);
  j=copy(original);j.getJSONArray("order").getJSONObject(0).put("part","missing");reject(j);
  j=copy(original);part=j.getJSONArray("parts").getJSONObject(0);part.getJSONArray("tracks").getJSONObject(1).put("id",part.getJSONArray("tracks").getJSONObject(0).getString("id"));reject(j);
  j=copy(original);j.getJSONArray("parts").getJSONObject(0).getJSONArray("phrases").getJSONObject(0).put("trackId","missing");reject(j);
  j=copy(original);j.getJSONArray("parts").getJSONObject(0).getJSONArray("phrases").getJSONObject(0).getJSONArray("notes").getJSONObject(0).put("beat",1e10);reject(j);
  j=copy(original);j.getJSONArray("parts").getJSONObject(0).getJSONArray("phrases").getJSONObject(0).getJSONArray("notes").getJSONObject(0).put("length",-1);reject(j);
  j=copy(original);j.getJSONArray("parts").getJSONObject(0).getJSONArray("phrases").getJSONObject(0).getJSONArray("notes").getJSONObject(0).getJSONArray("curve").getJSONArray(0).put(0,1e10);reject(j);
  j=copy(original);j.put("extraUnknown",1);reject(j);
  j=copy(original);j.getJSONArray("order").getJSONObject(0).put("repeats",33);reject(j);
  for(String bad:new String[]{original.toString().replace("\"bpm\":90","\"bpm\":NaN"),"{\"version\":1,\"version\":1}"}){try{ComposerLlmImport.parse(bad);fail("invalid JSON accepted");}catch(ComposerLlmImport.Invalid expected){}}
 }
 public static class Provider extends ContentProvider {
  static File source;static java.util.concurrent.CountDownLatch entered,release;public boolean onCreate(){return true;}public String getType(Uri u){return "application/json";}public Cursor query(Uri u,String[] p,String s,String[] a,String o){return null;}public Uri insert(Uri u,ContentValues v){return null;}public int delete(Uri u,String s,String[] a){return 0;}public int update(Uri u,ContentValues v,String s,String[] a){return 0;}
  public ParcelFileDescriptor openFile(Uri u,String mode)throws FileNotFoundException{if(entered!=null){entered.countDown();try{release.await(10,java.util.concurrent.TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}}return ParcelFileDescriptor.open(source,ParcelFileDescriptor.MODE_READ_ONLY);}
 }
 static void awaitImport()throws Exception{for(Thread t:Thread.getAllStackTraces().keySet())if(t.getName().equals("composer-import"))t.join(10000);ShadowLooper.idleMainLooper();}
 static void begin(MainActivity a)throws Exception{a.exchange.showImport();Intent picker=org.robolectric.Shadows.shadowOf(a).getNextStartedActivityForResult().intent;assertEquals(Intent.ACTION_OPEN_DOCUMENT,picker.getAction());a.exchange.onActivityResult(41,Activity.RESULT_OK,new Intent().setData(Uri.parse("content://import.fixture/input")));awaitImport();}
 @Test public void previewCancelAndApplyKeepOriginalUntilExplicitCopy()throws Exception {
  MainActivity a=Robolectric.buildActivity(MainActivity.class).setup().get();a.handler.removeCallbacks(a.tick);a.composer.worker.submit(()->{}).get();ShadowLooper.idleMainLooper();a.handler.removeCallbacks(a.tick);a.composer.ready=true;
  ComposerProject imported=fixture();Provider.source=new File(a.getCacheDir(),"import.json");java.nio.file.Files.write(Provider.source.toPath(),imported.json().toString().getBytes(StandardCharsets.UTF_8));Provider provider=new Provider();android.content.pm.ProviderInfo info=new android.content.pm.ProviderInfo();info.authority="import.fixture";provider.attachInfo(a,info);ShadowContentResolver.registerProviderInternal("import.fixture",provider);
  String original=a.composer.project.json().toString(),oldId=a.composer.project.id;begin(a);AlertDialog preview=(AlertDialog)ShadowDialog.getLatestDialog();assertTrue(preview.isShowing());assertEquals(original,a.composer.project.json().toString());preview.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();ShadowLooper.idleMainLooper();assertEquals(original,a.composer.project.json().toString());
  begin(a);preview=(AlertDialog)ShadowDialog.getLatestDialog();preview.getButton(AlertDialog.BUTTON_POSITIVE).performClick();ShadowLooper.idleMainLooper();a.composer.worker.submit(()->{}).get();ShadowLooper.idleMainLooper();assertNotEquals(oldId,a.composer.project.id);assertNotEquals(imported.id,a.composer.project.id);assertEquals(imported.parts.get(0).id,a.composer.project.parts.get(0).id);assertEquals(imported.name,a.composer.project.name);String oldName=new JSONObject(original).getString("name");assertTrue(a.composer.store.list().stream().anyMatch(p->p.name.equals(oldName)));a.io.shutdown();
 }
 @Test public void recordingBlocksImportPicker()throws Exception{MainActivity a=Robolectric.buildActivity(MainActivity.class).setup().get();a.handler.removeCallbacks(a.tick);a.composer.ready=true;a.composer.capturing=true;a.exchange.showImport();assertNull(org.robolectric.Shadows.shadowOf(a).getNextStartedActivityForResult());a.composer.capturing=false;a.io.shutdown();}

 @Test public void unsolicitedResultAndDestroyedActivityCannotShowImportPreview()throws Exception {
  org.robolectric.android.controller.ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup();MainActivity a=controller.get();a.handler.removeCallbacks(a.tick);a.composer.worker.submit(()->{}).get();ShadowLooper.idleMainLooper();a.handler.removeCallbacks(a.tick);a.composer.ready=true;
  Provider.source=new File(a.getCacheDir(),"delayed-import.json");java.nio.file.Files.write(Provider.source.toPath(),fixture().json().toString().getBytes(StandardCharsets.UTF_8));Provider provider=new Provider();android.content.pm.ProviderInfo info=new android.content.pm.ProviderInfo();info.authority="import.fixture";provider.attachInfo(a,info);ShadowContentResolver.registerProviderInternal("import.fixture",provider);
  String before=a.composer.project.json().toString();Intent result=new Intent().setData(Uri.parse("content://import.fixture/input"));Dialog earlier=ShadowDialog.getLatestDialog();a.exchange.onActivityResult(41,Activity.RESULT_OK,result);awaitImport();assertEquals(before,a.composer.project.json().toString());assertSame(earlier,ShadowDialog.getLatestDialog());
  Provider.entered=new java.util.concurrent.CountDownLatch(1);Provider.release=new java.util.concurrent.CountDownLatch(1);a.exchange.showImport();assertNotNull(org.robolectric.Shadows.shadowOf(a).getNextStartedActivityForResult());a.exchange.onActivityResult(41,Activity.RESULT_OK,result);assertTrue(Provider.entered.await(5,java.util.concurrent.TimeUnit.SECONDS));controller.pause().stop().destroy();Provider.release.countDown();awaitImport();assertSame(earlier,ShadowDialog.getLatestDialog());assertEquals(before,a.composer.project.json().toString());Provider.entered=Provider.release=null;
 }

}
