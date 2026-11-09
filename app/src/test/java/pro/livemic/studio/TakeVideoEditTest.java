package pro.livemic.studio;

import android.content.Context;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntConsumer;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,instrumentedPackages={"pro.livemic.studio"},shadows={TakeVideoEditTest.Audio.class,TakeVideoEditTest.Mux.class})
public class TakeVideoEditTest {
    @Implements(value=NativeAudio.class,isInAndroidSdk=false)
    public static class Audio extends TakeSourcesTest.Audio {
        static int offset,renders;static String rawPath,fixedPath;
        @Implementation protected static boolean renderVocalStem(String raw,String fixed,String output,float[] values,int sync){
            offset=sync;renders++;rawPath=raw;fixedPath=fixed;
            try{TakeSourcesTest.wav(new File(output),false,2);return true;}catch(Exception e){return false;}
        }
    }
    @Implements(value=TakeVideoMux.class,isInAndroidSdk=false)
    public static class Mux {
        static boolean fail,cancel;static int calls;static long offset;static File original;
        @Implementation protected static void render(File video,File sourceWav,File editedWav,File output,long audioOffset,AtomicBoolean cancelled,IntConsumer progress)throws Exception{
            calls++;offset=audioOffset;original=video;TakeVideoMux.validateWavs(sourceWav,editedWav);
            progress.accept(1);Files.write(new File(output.getParentFile(),output.getName()+".aac.m4a").toPath(),new byte[]{9});
            Files.write(output.toPath(),new byte[]{7,8,9});
            if(fail)throw new IOException("injected mux failure");if(cancel)cancelled.set(true);
            progress.accept(2);progress.accept(3);progress.accept(4);
        }
    }
    Context c;TakeStore store;JSONObject original;float[] settings;
    @Before public void setup()throws Exception{
        c=RuntimeEnvironment.getApplication();AppLanguage.set(c,true);ExitCoordinator.resetForLaunch();store=new TakeStore(c,false);
        for(JSONObject take:store.list())store.delete(take.optString("id"));
        String id="video-source";TakeSourcesTest.wav(store.file(id,".wav"),false,2);TakeSourcesTest.wav(store.file(id,".raw.wav"),true,1);
        TakeSourcesTest.wav(store.file(id,".fixed.wav"),true,2);TakeSourcesTest.wav(store.file(id,".vocal.wav"),true,2);Files.write(store.file(id,".mp4").toPath(),new byte[]{1,2,3});
        JSONObject extras=new JSONObject().put("cleanSources",true).put("sourceEditable",true).put("sourceFixed",true)
                .put("sourceVocalRetained",true).put("vocalStemEditable",true).put("sourceRetention","retained").put("syncOffsetMs",120).put("videoAudioOffsetUs",-85000L);
        store.save(id,"Original",.1,true,"",extras);original=store.read(id);settings=TakeAudioFiles.effects(original,new float[20],true);
        Mux.fail=Mux.cancel=false;Mux.calls=0;Audio.offset=Audio.renders=0;TakeSourcesTest.Audio.renderFails=false;
    }
    @After public void cleanup(){for(JSONObject take:store.list())store.delete(take.optString("id"));AppLanguage.set(c,false);ExitCoordinator.resetForLaunch();}
    @Test public void videoCopyUsesSignedCameraOffsetOnceAndKeepsIndependentSourcesForReediting()throws Exception{
        byte[] video=Files.readAllBytes(store.file("video-source",".mp4").toPath());List<Integer> phases=new ArrayList<>();
        TakeAudioFiles.Saved result=TakeAudioFiles.copyVideo(c,original,settings,new AtomicBoolean(),true,phases::add);
        assertTrue(result.video);assertEquals("",result.warning);assertEquals(120,Audio.offset);assertEquals(1,Audio.renders);
        assertEquals(-85000L,Mux.offset);assertEquals(store.file("video-source",".mp4"),Mux.original);assertEquals(java.util.Arrays.asList(0,1,2,3,4),phases);
        JSONObject copy=store.read(result.id);assertTrue(copy.getBoolean("video"));assertEquals("vocal",copy.getString("effectsMode"));assertEquals(-85000,copy.getLong("videoAudioOffsetUs"));
        for(String suffix:new String[]{".raw.wav",".fixed.wav",".vocal.wav"})assertArrayEquals(Files.readAllBytes(store.file("video-source",suffix).toPath()),Files.readAllBytes(store.file(result.id,suffix).toPath()));
        assertArrayEquals(video,Files.readAllBytes(store.file("video-source",".mp4").toPath()));
        store.delete("video-source");assertTrue(TakeAudioFiles.stemEditable(store,copy));
        TakeAudioFiles.Saved next=TakeAudioFiles.copyVideo(c,copy,settings,new AtomicBoolean(),true,phase->{});assertTrue(next.video);
        assertEquals(store.file(result.id,".vocal.wav").getAbsolutePath(),Audio.rawPath);assertEquals(store.file(result.id,".fixed.wav").getAbsolutePath(),Audio.fixedPath);
        assertNoWorkDirectories();
    }
    @Test public void muxFailureOrCancelPreservesEditedWavAndAllOriginalFilesAndCleansOnlyTemps()throws Exception{
        for(boolean cancel:new boolean[]{false,true}){
            Mux.fail=!cancel;Mux.cancel=cancel;byte[] video=Files.readAllBytes(store.file("video-source",".mp4").toPath());
            TakeAudioFiles.Saved result=TakeAudioFiles.copyVideo(c,original,settings,new AtomicBoolean(),true,phase->{});
            assertFalse(result.video);assertFalse(result.warning.isEmpty());assertTrue(store.file(result.id,".wav").length()>44);
            assertTrue(TakeAudioFiles.stemEditable(store,store.read(result.id)));assertFalse(store.file(result.id,".mp4").exists());
            assertArrayEquals(video,Files.readAllBytes(store.file("video-source",".mp4").toPath()));assertTrue(store.file("video-source",".raw.wav").length()>44);
            assertNoWorkDirectories();
        }
    }
    @Test public void legacyVideoNeedsExplicitTimingMetadataButStillSupportsWavEdits()throws Exception{
        original.remove("videoAudioOffsetUs");assertFalse(TakeAudioFiles.videoEditable(store,original));
        try{TakeAudioFiles.copyVideo(c,original,settings,new AtomicBoolean(),true,phase->{});fail();}catch(IOException expected){assertTrue(expected.getMessage().contains("timing"));}
        assertEquals(0,Mux.calls);assertEquals(0,Audio.renders);assertNotNull(TakeAudioFiles.copy(c,original,settings,new AtomicBoolean(),true));
    }
    @Test public void preCancelledWorkCreatesNoCopyAndNeutralStemDefaultsDoNotStackInitialEffects()throws Exception{
        float[] live=new float[20];java.util.Arrays.fill(live,.5f);float[] neutral=TakeAudioFiles.effects(original,live,true);
        assertEquals(1,neutral[0],0);for(int i:new int[]{2,4,6,7,8,9,10,19})assertEquals(0,neutral[i],0);
        try{TakeAudioFiles.copyVideo(c,original,neutral,new AtomicBoolean(true),true,phase->{});fail();}catch(IOException expected){}
        assertEquals(1,store.list().size());assertEquals(0,Mux.calls);assertEquals(0,Audio.renders);
    }
    @Test public void timelineDoesNotReapplyVocalSyncAndValidationRejectsLostSamplesOrWrongWavDuration()throws Exception{
        assertEquals(-85000,TakeVideoMux.audioTime(10000,10000,-85000));assertEquals(15000,TakeVideoMux.audioTime(110000,10000,-85000));
        TakeVideoMux.Samples expected=new TakeVideoMux.Samples(),actual=new TakeVideoMux.Samples();expected.add(70000);expected.add(103333);actual.add(70001);actual.add(103332);
        TakeVideoMux.verifySamples(expected,actual);actual.add(130000);try{TakeVideoMux.verifySamples(expected,actual);fail();}catch(IOException good){}
        File edited=new File(c.getCacheDir(),"bad-edit.wav");TakeSourcesTest.wav(edited,false,2);try(java.io.RandomAccessFile f=new java.io.RandomAccessFile(edited,"rw")){f.setLength(f.length()-4);}
        try{TakeVideoMux.validateWavs(store.file("video-source",".wav"),edited);fail();}catch(IOException good){}edited.delete();
    }
    @Test public void encoderHardBudgetUsesAudioDurationAndRejectsBrokenSignatures()throws Exception{
        assertEquals(30_000_000_000L,MediaTools.aacTimeBudgetNanos(0,2,48000));
        assertEquals(40_000_000_000L,MediaTools.aacTimeBudgetNanos(48000L*4,2,48000));
        assertEquals(330_000_000_000L,MediaTools.aacTimeBudgetNanos(44100L*2*30,1,44100));
        assertTrue(MediaTools.aacTimeBudgetNanos(Long.MAX_VALUE-3,2,8000)<=Long.MAX_VALUE/4);
        try{MediaTools.aacTimeBudgetNanos(3,2,48000);fail();}catch(IOException expected){}
        try(java.io.RandomAccessFile wav=new java.io.RandomAccessFile(store.file("video-source",".wav"),"rw")){
            MediaTools.validateAacWav(wav,2,48000);wav.seek(0);wav.writeInt(0);
            try{MediaTools.validateAacWav(wav,2,48000);fail();}catch(IOException expected){}
        }
    }
    private void assertNoWorkDirectories(){File[] work=c.getCacheDir().listFiles((d,n)->n.startsWith("take-video-edit-"));assertNotNull(work);assertEquals(0,work.length);}
}
