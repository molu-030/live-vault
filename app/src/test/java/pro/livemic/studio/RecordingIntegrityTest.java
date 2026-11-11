package pro.livemic.studio;

import android.content.Context;
import org.json.JSONObject;
import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28,instrumentedPackages={"pro.livemic.studio"},shadows={StudioScreenshotTest.Audio.class})
public class RecordingIntegrityTest {
    Context context;TakeStore store;
    @Before public void setup(){context=RuntimeEnvironment.getApplication();store=new TakeStore(context,false);ExitCoordinator.resetForLaunch();RecordingManager.active=false;org.robolectric.util.ReflectionHelpers.<RecordingCountdown>getStaticField(AudioService.class,"countdown").cancel();AppLanguage.set(context,true);}
    @After public void cleanup()throws Exception{RecordingManager.active=false;ExitCoordinator.resetForLaunch();store.awaitRecovery();AppLanguage.set(context,false);}
    static String suffix(String name){return name.equals("mix")?".wav":"."+name+".wav";}
    static JSONObject mark(String name,int rate,long frames)throws Exception{int channels=name.equals("raw")?1:2,bits=name.equals("mix")?16:32;return new JSONObject().put("rate",rate).put("channels",channels).put("bits",bits).put("format",name.equals("mix")?1:3).put("bytesPerFrame",channels*bits/8).put("frames",frames);}
    void journal(String id,JSONObject values)throws Exception{values.put("version",1).put("done",false);Files.write(RecordingIntegrity.journal(store,id).toPath(),values.toString().getBytes(StandardCharsets.UTF_8));}
    static void wave(TakeStore store,String id,String name,int rate,int frames,int partial,boolean emptyHeader)throws Exception{
        int channels=name.equals("raw")?1:2,bits=name.equals("mix")?16:32,align=channels*bits/8,size=frames*align+partial;
        ByteBuffer bytes=ByteBuffer.allocate(44+size).order(ByteOrder.LITTLE_ENDIAN);
        bytes.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(emptyHeader?36:36+size).put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16).putShort((short)(bits==16?1:3)).putShort((short)channels).putInt(rate).putInt(rate*align).putShort((short)align).putShort((short)bits).put("data".getBytes(StandardCharsets.US_ASCII)).putInt(emptyHeader?0:size);
        while(bytes.hasRemaining())bytes.put((byte)(bytes.position()%71));Files.write(store.file(id,suffix(name)).toPath(),bytes.array());
    }
    void recover()throws Exception{store.recoverAsync(Runnable::run,null);store.awaitRecovery();}
    @Test public void zeroHeadersAndPartialFramesUseIndependentDurableWatermarks()throws Exception{
        String id="crash";wave(store,id,"mix",48000,12,3,true);wave(store,id,"raw",44100,15,2,true);wave(store,id,"fixed",48000,13,7,true);wave(store,id,"vocal",48000,14,5,true);
        byte[] original=Files.readAllBytes(store.file(id,".wav").toPath());journal(id,new JSONObject().put("mix",mark("mix",48000,8)).put("raw",mark("raw",44100,10)).put("fixed",mark("fixed",48000,9)).put("vocal",mark("vocal",48000,9)));
        RecordingIntegrity.writeState(store,id,new JSONObject().put("syncOffsetMs",85).put("sourceRetention","retained"));recover();assertEquals("",store.recoveryFailure());
        JSONObject meta=store.read(id);assertEquals(8/48000.,meta.getDouble("seconds"),1e-12);assertEquals(44+8*4,store.file(id,".wav").length());assertEquals(44+10*4,store.file(id,".raw.wav").length());assertEquals(44+9*8,store.file(id,".fixed.wav").length());
        assertArrayEquals(Arrays.copyOfRange(original,44,44+8*4),Arrays.copyOfRange(Files.readAllBytes(store.file(id,".wav").toPath()),44,44+8*4));assertEquals(85,meta.getInt("syncOffsetMs"));assertFalse(meta.getBoolean("cleanSources"));assertTrue(meta.getBoolean("vocalStemEditable"));
        JSONObject raw=meta.getJSONObject("recordingIntegrity").getJSONObject("raw");assertEquals(44100,raw.getInt("sampleRate"));assertEquals(10,raw.getLong("confirmedFrames"));assertFalse(RecordingIntegrity.journal(store,id).exists());
    }
    @Test public void normalStopDefersSparseLargeRepairWithoutTouchingAudio()throws Exception{
        String id="deferred";wave(store,id,"mix",48000,4,0,true);try(RandomAccessFile file=new RandomAccessFile(store.file(id,".wav"),"rw")){file.setLength(256L*1024*1024+44);}
        journal(id,new JSONObject().put("mix",mark("mix",48000,2)));long before=store.file(id,".wav").length();store.save(id,"Stopped",99,false,"");
        assertEquals(before,store.file(id,".wav").length());assertFalse(store.file(id,".wav.recovery-new").exists());assertTrue(store.read(id).getBoolean("integrityRepairPending"));assertEquals(2/48000.,store.read(id).getDouble("seconds"),1e-12);
        store.queueRecovery(id);store.awaitRecovery();assertEquals(52,store.file(id,".wav").length());assertFalse(store.read(id).getBoolean("integrityRepairPending"));assertFalse(store.read(id).getString("warning").contains("being repaired"));
    }
    @Test public void metadataFailureRollsBackEveryOriginalAndRetryIsIdempotent()throws Exception{
        String id="rollback";wave(store,id,"mix",48000,8,1,true);wave(store,id,"raw",48000,8,2,true);store.save(id,"Original name",1,false,"");
        String metadata=store.read(id).toString();byte[] mix=Files.readAllBytes(store.file(id,".wav").toPath()),raw=Files.readAllBytes(store.file(id,".raw.wav").toPath());journal(id,new JSONObject().put("mix",mark("mix",48000,4)).put("raw",mark("raw",48000,5)));
        try{RecordingIntegrity.finish(context,store,id,new JSONObject(metadata),()->{throw new IOException("metadata write failed");},true);fail();}catch(IOException expected){}
        assertArrayEquals(mix,Files.readAllBytes(store.file(id,".wav").toPath()));assertArrayEquals(raw,Files.readAllBytes(store.file(id,".raw.wav").toPath()));assertEquals(metadata,store.read(id).toString());assertTrue(RecordingIntegrity.journal(store,id).exists());
        recover();byte[] repaired=Files.readAllBytes(store.file(id,".wav").toPath());String committed=store.read(id).toString();recover();assertArrayEquals(repaired,Files.readAllBytes(store.file(id,".wav").toPath()));assertEquals(committed,store.read(id).toString());
    }
    @Test public void interruptedRenameIsRestoredBeforeRepair()throws Exception{
        String id="interrupted-repair";wave(store,id,"mix",48000,8,0,true);byte[] original=Files.readAllBytes(store.file(id,".wav").toPath());journal(id,new JSONObject().put("mix",mark("mix",48000,5)));
        Files.move(store.file(id,".wav").toPath(),store.file(id,".wav.recovery-original").toPath());Files.write(store.file(id,".wav.recovery-new").toPath(),new byte[]{1,2});recover();
        assertEquals(64,store.file(id,".wav").length());assertArrayEquals(Arrays.copyOfRange(original,44,64),Arrays.copyOfRange(Files.readAllBytes(store.file(id,".wav").toPath()),44,64));assertFalse(store.file(id,".wav.recovery-original").exists());
    }
    @Test public void corruptMixOrMalformedJournalPreservesBytesAndCorruptSourceIsKept()throws Exception{
        String id="malformed";wave(store,id,"mix",48000,8,0,true);byte[] original=Files.readAllBytes(store.file(id,".wav").toPath());journal(id,new JSONObject().put("mix",mark("mix",48000,4).put("frames",1.5)));recover();assertArrayEquals(original,Files.readAllBytes(store.file(id,".wav").toPath()));assertFalse(store.file(id,".json").exists());assertFalse(store.recoveryFailure().isEmpty());
        journal(id,new JSONObject().put("mix",mark("mix",48000,4)));try(RandomAccessFile mix=new RandomAccessFile(store.file(id,".wav"),"rw")){mix.seek(28);mix.writeInt(0);}byte[] corrupt=Files.readAllBytes(store.file(id,".wav").toPath());recover();assertArrayEquals(corrupt,Files.readAllBytes(store.file(id,".wav").toPath()));
        Files.write(store.file(id,".wav").toPath(),original);wave(store,id,"raw",48000,9,0,true);try(RandomAccessFile raw=new RandomAccessFile(store.file(id,".raw.wav"),"rw")){raw.writeInt(0);}byte[] badRaw=Files.readAllBytes(store.file(id,".raw.wav").toPath());journal(id,new JSONObject().put("mix",mark("mix",48000,4)).put("raw",mark("raw",48000,6)));recover();assertArrayEquals(badRaw,Files.readAllBytes(store.file(id,".raw.wav").toPath()));assertTrue(store.read(id).getJSONObject("recordingIntegrity").getJSONObject("raw").getBoolean("originalPreserved"));
    }
    @Test public void aheadWatermarkAndLegacyTailAreConservativeAndOversizeRejectedBeforeCopy()throws Exception{
        String id="ahead";wave(store,id,"mix",48000,3,2,true);journal(id,new JSONObject().put("mix",mark("mix",48000,10)));recover();assertEquals(56,store.file(id,".wav").length());assertTrue(store.read(id).getJSONObject("recordingIntegrity").getJSONObject("mix").getBoolean("watermarkAheadOfFile"));
        wave(store,"legacy","mix",44100,5,3,true);recover();assertEquals(64,store.file("legacy",".wav").length());assertFalse(store.read("legacy").getBoolean("recoveryJournal"));
        wave(store,"oversize","mix",48000,1,0,true);try(RandomAccessFile huge=new RandomAccessFile(store.file("oversize",".wav"),"rw")){huge.setLength(0xffffffffL+9);}try{RecordingIntegrity.inspect(store.file("oversize",".wav"),"mix",null);fail();}catch(IOException expected){}assertFalse(store.file("oversize",".wav.recovery-new").exists());store.file("oversize",".wav").delete();
    }
    @Test public void activeTakeAndExitCancellationKeepOriginalsForNextLaunch()throws Exception{
        String id="active";wave(store,id,"mix",48000,8,2,true);journal(id,new JSONObject().put("mix",mark("mix",48000,4)));byte[] before=Files.readAllBytes(store.file(id,".wav").toPath());RecordingManager.id=id;RecordingManager.active=true;recover();assertArrayEquals(before,Files.readAllBytes(store.file(id,".wav").toPath()));assertFalse(store.file(id,".json").exists());RecordingManager.active=false;
        ExitCoordinator.requestExit();try{RecordingIntegrity.finish(context,store,id,new JSONObject(),()->fail("No metadata on cancellation"),true);fail();}catch(IOException expected){}assertArrayEquals(before,Files.readAllBytes(store.file(id,".wav").toPath()));ExitCoordinator.resetForLaunch();recover();assertTrue(store.file(id,".json").exists());
    }
    @Test public void pendingMixWatermarkSurvivesSourceDeletionWithoutResurrectingSources()throws Exception{
        String id="source-cleanup";wave(store,id,"mix",48000,8,0,true);wave(store,id,"raw",48000,8,0,true);journal(id,new JSONObject().put("mix",mark("mix",48000,4)).put("raw",mark("raw",48000,4)));store.save(id,"Pending",10,false,"");
        store.deleteSources(id);store.awaitRecovery();assertFalse(store.file(id,".raw.wav").exists());JSONObject meta=store.read(id);assertEquals("deleted-by-user",meta.getString("sourceRetention"));assertFalse(meta.getBoolean("sourceEditable"));assertEquals(60,store.file(id,".wav").length());assertFalse(meta.getBoolean("integrityRepairPending"));
    }
    @Test public void sharedReservationRejectsSameTakeWithoutBlockingOtherTakeAndDeduplicates()throws Exception{
        String id="busy";wave(store,id,"mix",48000,8,0,true);journal(id,new JSONObject().put("mix",mark("mix",48000,4)));CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);java.util.concurrent.atomic.AtomicInteger calls=new java.util.concurrent.atomic.AtomicInteger();
        TakeStore blocking=new TakeStore(context,false){@Override void recoverOne(String target)throws Exception{if(target.equals(id)){calls.incrementAndGet();entered.countDown();if(!release.await(10,TimeUnit.SECONDS))throw new IOException("Test recovery barrier timeout");}super.recoverOne(target);}};
        blocking.queueRecovery(id);assertTrue(entered.await(5,TimeUnit.SECONDS));
        try{assertTrue(store.isRecovering(id));assertFalse(store.delete(id));try{store.deleteSources(id);fail();}catch(IOException expected){}
            store.save("other","Unrelated",1,false,"");assertEquals("Unrelated",store.read("other").getString("name"));
            store.recoverAsync(Runnable::run,null);
        }finally{release.countDown();}
        store.awaitRecovery();assertEquals(1,calls.get());assertFalse(store.isRecovering(id));assertTrue(store.file(id,".json").exists());
    }
}
