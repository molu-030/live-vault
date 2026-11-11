package pro.livemic.studio;

import android.content.Context;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,instrumentedPackages={"pro.livemic.studio"},shadows={TakeSourcesTest.Audio.class})
public class TakeSourcesTest {
    @Implements(value=NativeAudio.class,isInAndroidSdk=false)
    public static class Audio extends StudioScreenshotTest.Audio {
        static boolean sourceValid,editable,renderFails,fixedFails;
        @Implementation protected static boolean beginRecordSources(String mix,String raw,String fixed){
            try{wav(new File(mix),false,2);wav(new File(raw),true,1);if(fixedFails){new FileOutputStream(fixed).close();}else wav(new File(fixed),true,2);return true;}catch(Exception e){return false;}
        }
        @Implementation protected static boolean recordSourcesValid(){return sourceValid;}
        @Implementation protected static boolean recordSourcesEditable(){return editable;}
        @Implementation protected static void endRecord(){}
        @Implementation protected static double recordClock(){return 42;}
        @Implementation protected static boolean exportMidi(String path,int bpm,boolean onlyRecord){return true;}
        @Implementation protected static boolean renderTakeSources(String raw,String fixed,String output,float[] values,int offset){
            if(renderFails)return false;try{wav(new File(output),false,2);return true;}catch(Exception e){return false;}
        }
    }
    Context context;TakeStore store;
    @Before public void setup(){
        context=RuntimeEnvironment.getApplication();store=new TakeStore(context,false);for(JSONObject j:store.list())store.delete(j.optString("id"));
        AudioRouting.prefs(context).edit().clear().putInt("sync0",80).commit();
        StudioScreenshotTest.Audio.supplied=new float[22];StudioScreenshotTest.Audio.supplied[10]=1;StudioScreenshotTest.Audio.supplied[8]=1;
        StudioScreenshotTest.Audio.actualInputDevice=StudioScreenshotTest.Audio.actualOutputDevice=0;StudioScreenshotTest.Audio.syncCalls.clear();
        Audio.sourceValid=Audio.editable=true;Audio.renderFails=Audio.fixedFails=false;RecordingManager.active=false;ExitCoordinator.resetForLaunch();
    }
    @After public void cleanup(){RecordingManager.active=false;RecordingManager.micOnly=false;StudioScreenshotTest.Audio.supplied=null;AudioRouting.prefs(context).edit().clear().commit();ExitCoordinator.resetForLaunch();}
    static void wav(File path,boolean floating,int channels)throws Exception{
        int size=4*channels*(floating?4:2);ByteBuffer h=ByteBuffer.allocate(44+size).order(ByteOrder.LITTLE_ENDIAN);
        h.put("RIFF".getBytes()).putInt(36+size).put("WAVEfmt ".getBytes()).putInt(16).putShort((short)(floating?3:1)).putShort((short)channels).putInt(48000).putInt(48000*channels*(floating?4:2)).putShort((short)(channels*(floating?4:2))).putShort((short)(floating?32:16)).put("data".getBytes()).putInt(size);
        while(h.hasRemaining())h.put((byte)0);try(FileOutputStream out=new FileOutputStream(path)){out.write(h.array());}
    }
    @Test public void beginSnapshotsPairOffsetForNativeMetadataAndVideoResult()throws Exception{
        assertTrue(RecordingManager.start(context,78,false));String id=RecordingManager.id;
        assertEquals(Integer.valueOf(80),StudioScreenshotTest.Audio.syncCalls.get(0));
        AudioRouting.prefs(context).edit().putInt("sync0",150).commit();RecordingManager.syncMs=-200;
        RecordingManager.Result result=RecordingManager.stop();assertEquals(80,result.offsetMs);
        JSONObject meta=store.read(id);assertEquals(80,meta.getInt("syncOffsetMs"));assertTrue(meta.getBoolean("cleanSources"));assertTrue(meta.getBoolean("sourceFloat32"));assertTrue(TakeAudioFiles.editable(store,meta));assertTrue(meta.has("syncProfileKey"));
        assertTrue(RecordingManager.start(context,78,false));assertEquals(Integer.valueOf(150),StudioScreenshotTest.Audio.syncCalls.get(StudioScreenshotTest.Audio.syncCalls.size()-1));RecordingManager.stop();
    }
    @Test public void micOnlyIgnoresOffsetAndSourceFailureNeverRemovesOriginalMix()throws Exception{
        Audio.sourceValid=false;Audio.fixedFails=true;assertTrue(RecordingManager.start(context,78,true));String id=RecordingManager.id;byte[] original=Files.readAllBytes(store.file(id,".wav").toPath()),retainedRaw=Files.readAllBytes(store.file(id,".raw.wav").toPath());
        RecordingManager.Result result=RecordingManager.stop();assertEquals(0,result.offsetMs);assertEquals(0,store.read(id).getInt("syncOffsetMs"));assertFalse(store.read(id).getBoolean("cleanSources"));assertArrayEquals(original,Files.readAllBytes(store.file(id,".wav").toPath()));assertTrue(store.file(id,".raw.wav").length()>44);assertArrayEquals(retainedRaw,Files.readAllBytes(store.file(id,".raw.wav").toPath()));assertTrue(store.read(id).getBoolean("sourceRawRetained"));assertFalse(store.read(id).getBoolean("sourceFixedRetained"));assertFalse(TakeAudioFiles.editable(store,store.read(id)));
    }
    @Test public void unsupportedVoiceRoutingRetainsSourcesWithoutClaimingEditSupport()throws Exception{
        Audio.editable=false;assertTrue(RecordingManager.start(context,78,false));String id=RecordingManager.id;RecordingManager.stop();JSONObject meta=store.read(id);
        assertTrue(meta.getBoolean("cleanSources"));assertFalse(TakeAudioFiles.editable(store,meta));assertTrue(store.file(id,".raw.wav").length()>44);assertTrue(meta.getString("sourceEditReason").contains("harmony"));
    }
    @Test public void copyPublishesNewTakeAndCancelOrRenderFailureLeavesSourcesUntouched()throws Exception{
        assertTrue(RecordingManager.start(context,78,false));String id=RecordingManager.id;RecordingManager.stop();JSONObject meta=store.read(id);
        byte[] mix=Files.readAllBytes(store.file(id,".wav").toPath()),raw=Files.readAllBytes(store.file(id,".raw.wav").toPath()),fixed=Files.readAllBytes(store.file(id,".fixed.wav").toPath());
        float[] fx=new float[20];String copy=TakeAudioFiles.copy(context,meta,fx,new AtomicBoolean());assertNotEquals(id,copy);assertEquals(id,store.read(copy).getString("editedFrom"));assertTrue(TakeAudioFiles.editable(store,store.read(copy)));
        int count=store.list().size();try{TakeAudioFiles.copy(context,meta,fx,new AtomicBoolean(true));fail();}catch(java.io.IOException expected){}
        Audio.renderFails=true;try{TakeAudioFiles.copy(context,meta,fx,new AtomicBoolean());fail();}catch(java.io.IOException expected){}
        assertEquals(count,store.list().size());assertArrayEquals(mix,Files.readAllBytes(store.file(id,".wav").toPath()));assertArrayEquals(raw,Files.readAllBytes(store.file(id,".raw.wav").toPath()));assertArrayEquals(fixed,Files.readAllBytes(store.file(id,".fixed.wav").toPath()));
        store.delete(id);assertTrue(TakeAudioFiles.editable(store,store.read(copy)));Audio.renderFails=false;assertNotNull(TakeAudioFiles.copy(context,store.read(copy),fx,new AtomicBoolean()));
    }
    @Test public void recoverySkipsSourceSidecarsAndDeletionRemovesAllTakeFiles()throws Exception{
        wav(store.file("orphan",".raw.wav"),true,1);wav(store.file("orphan",".fixed.wav"),true,2);new TakeStore(context,true).awaitRecovery();assertFalse(store.file("orphan.raw",".json").exists());assertFalse(store.file("orphan.fixed",".json").exists());
        assertTrue(RecordingManager.start(context,78,false));String id=RecordingManager.id;RecordingManager.stop();store.delete(id);assertFalse(store.file(id,".wav").exists());assertFalse(store.file(id,".raw.wav").exists());assertFalse(store.file(id,".fixed.wav").exists());
    }
}
