package pro.livemic.studio;

import android.content.Context;
import java.io.*;
import java.nio.file.Files;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,instrumentedPackages={"pro.livemic.studio"},shadows={TakeStorageTest.Audio.class})
public class TakeStorageTest {
    @Implements(value=NativeAudio.class,isInAndroidSdk=false)
    public static class Audio extends StudioScreenshotTest.Audio {
        static int mixes,stems;static boolean valid=true;
        @Implementation protected static boolean beginRecord(String path){mixes++;try{TakeSourcesTest.wav(new File(path),false,2);return true;}catch(Exception e){return false;}}
        @Implementation protected static boolean beginRecordStems(String mix,String raw,String fixed,String vocal){stems++;try{TakeSourcesTest.wav(new File(mix),false,2);TakeSourcesTest.wav(new File(raw),true,1);TakeSourcesTest.wav(new File(fixed),true,2);TakeSourcesTest.wav(new File(vocal),true,2);return true;}catch(Exception e){return false;}}
        @Implementation protected static boolean recordSourcesValid(){return valid;}
        @Implementation protected static boolean recordVocalStemValid(){return valid;}
        @Implementation protected static boolean recordSourcesEditable(){return true;}
        @Implementation protected static void endRecord(){}
        @Implementation protected static double recordClock(){return 1;}
        @Implementation protected static boolean exportMidi(String path,int bpm,boolean record){return true;}
    }
    Context context;TakeStore store;
    @Before public void setup(){context=RuntimeEnvironment.getApplication();AppLanguage.set(context,true);context.getSharedPreferences("MainActivity",0).edit().remove(TakeStorage.PREFERENCE).commit();store=new TakeStore(context,false);Audio.mixes=Audio.stems=0;Audio.valid=true;RecordingManager.active=false;RecordingManager.paused=false;org.robolectric.util.ReflectionHelpers.<RecordingCountdown>getStaticField(AudioService.class,"countdown").cancel();StudioScreenshotTest.Audio.supplied=new float[22];StudioScreenshotTest.Audio.supplied[6]=48000;StudioScreenshotTest.Audio.supplied[8]=1;StudioScreenshotTest.Audio.supplied[10]=1;ExitCoordinator.resetForLaunch();}
    @After public void cleanup(){RecordingManager.active=false;RecordingManager.micOnly=false;StudioScreenshotTest.Audio.supplied=null;AppLanguage.set(context,false);}
    void take(String id)throws Exception{
        TakeSourcesTest.wav(store.file(id,".wav"),false,2);TakeSourcesTest.wav(store.file(id,".raw.wav"),true,1);TakeSourcesTest.wav(store.file(id,".fixed.wav"),true,2);TakeSourcesTest.wav(store.file(id,".vocal.wav"),true,2);
        Files.write(store.file(id,".mp4").toPath(),new byte[]{1,2,3});Files.write(store.file(id,".mid").toPath(),new byte[]{4,5});
        store.save(id,id,1,true,"",new JSONObject().put("cleanSources",true).put("sourceEditable",true).put("vocalStemEditable",true).put("sourceVocalRetained",true));
    }
    @Test public void defaultRetainsAllStemsAndOptOutIsNotASourceFailure()throws Exception{
        assertTrue(TakeStorage.retainSources(context));assertTrue(RecordingManager.start(context,78,false));String retained=RecordingManager.id;RecordingManager.stop();assertEquals(1,Audio.stems);assertTrue(store.read(retained).getBoolean("vocalStemEditable"));
        byte[] original=Files.readAllBytes(store.file(retained,".raw.wav").toPath());assertTrue(TakeStorage.setRetainSources(context,false));assertFalse(TakeStorage.retainSources(context));
        assertTrue(RecordingManager.start(context,78,false));String mixOnly=RecordingManager.id;assertFalse(TakeStorage.setRetainSources(context,true));
        RecordingManager.Result result=RecordingManager.stop();assertEquals(1,Audio.mixes);assertEquals("",result.warning);JSONObject meta=store.read(mixOnly);assertEquals("not-requested",meta.getString("sourceRetention"));assertFalse(meta.getBoolean("sourceEditable"));assertFalse(meta.getBoolean("vocalStemEditable"));assertFalse(store.file(mixOnly,".raw.wav").exists());
        assertArrayEquals(original,Files.readAllBytes(store.file(retained,".raw.wav").toPath()));
    }
    @Test public void estimatedSpaceIncludesFiveFloatChannelsAndVideoReserve(){
        assertEquals(69_120_000L,TakeStorage.minuteBytes(true,48000,false));assertEquals(11_520_000L,TakeStorage.minuteBytes(false,48000,false));
        long required=TakeStorage.requiredBytes(true,48000,false);assertFalse(TakeStorage.startProblem(context,required-1,true,48000,false).isEmpty());assertEquals("",TakeStorage.startProblem(context,required,true,48000,false));
        assertFalse(TakeStorage.startProblem(context,required,true,48000,true).isEmpty());assertEquals("",TakeStorage.startProblem(context,required-1,false,48000,false));
        AppLanguage.set(context,false);assertTrue(TakeStorage.startProblem(context,0,true,48000,false).contains("저장 공간"));
    }
    @Test public void deleteOnlyChosenSourcesKeepsMixVideoMidiAndOtherCopies()throws Exception{
        take("original");take("copy");byte[] mix=Files.readAllBytes(store.file("original",".wav").toPath()),video=Files.readAllBytes(store.file("original",".mp4").toPath()),copy=Files.readAllBytes(store.file("copy",".raw.wav").toPath());
        long used=TakeStorage.usage(store.directory),sources=TakeStorage.sourceBytes(store,"original"),metadataBefore=store.file("original",".json").length();store.deleteSources("original");
        assertFalse(TakeStorage.hasSources(store,"original"));assertEquals(used-sources-metadataBefore+store.file("original",".json").length(),TakeStorage.usage(store.directory));
        assertArrayEquals(mix,Files.readAllBytes(store.file("original",".wav").toPath()));assertArrayEquals(video,Files.readAllBytes(store.file("original",".mp4").toPath()));assertTrue(store.file("original",".mid").exists());assertArrayEquals(copy,Files.readAllBytes(store.file("copy",".raw.wav").toPath()));
        JSONObject meta=store.read("original");assertEquals("deleted-by-user",meta.getString("sourceRetention"));assertFalse(meta.getBoolean("sourceEditable"));assertFalse(meta.getBoolean("vocalStemEditable"));
    }
    @Test public void recordingLockAndMetadataFailureKeepSourceBytes()throws Exception{
        take("locked");byte[] raw=Files.readAllBytes(store.file("locked",".raw.wav").toPath());RecordingManager.active=true;
        try{store.deleteSources("locked");fail();}catch(IOException expected){}assertArrayEquals(raw,Files.readAllBytes(store.file("locked",".raw.wav").toPath()));RecordingManager.active=false;
        TakeStore failing=new TakeStore(context,false){@Override public void update(String id,JSONObject metadata)throws Exception{throw new IOException("metadata failure");}};
        String before=store.read("locked").toString();try{failing.deleteSources("locked");fail();}catch(IOException expected){}
        assertEquals(before,store.read("locked").toString());assertArrayEquals(raw,Files.readAllBytes(store.file("locked",".raw.wav").toPath()));assertFalse(store.file("locked",".raw.wav.delete-pending").exists());
    }
    @Test public void copyEstimateIncludesSourcesMixAndVideoTemporaryAudio()throws Exception{
        take("estimate");JSONObject take=store.read("estimate");long audio=TakeStorage.RESERVE_BYTES+TakeStorage.sourceBytes(store,"estimate")+store.file("estimate",".wav").length();
        assertEquals(audio,TakeStorage.estimatedCopyBytes(store,take,false));long video=audio+store.file("estimate",".mp4").length()+48_000;
        assertEquals(video,TakeStorage.estimatedCopyBytes(store,take,true));assertFalse(TakeStorage.copyProblem(context,video-1,video).isEmpty());assertEquals("",TakeStorage.copyProblem(context,video,video));
    }
    @Test public void actualSourceFailureKeepsAllFilesAndVocalSidecarIsNotRecoveredAsATake()throws Exception{
        Audio.valid=false;assertTrue(RecordingManager.start(context,78,false));String id=RecordingManager.id;RecordingManager.Result result=RecordingManager.stop();assertFalse(result.warning.isEmpty());assertTrue(store.file(id,".wav").exists());assertTrue(store.file(id,".vocal.wav").exists());
        TakeSourcesTest.wav(store.file("orphan",".vocal.wav"),true,2);new TakeStore(context,true);assertFalse(store.file("orphan.vocal",".json").exists());
    }
}
