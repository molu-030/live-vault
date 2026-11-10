package pro.livemic.studio;

import android.content.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.util.ReflectionHelpers;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,instrumentedPackages={"pro.livemic.studio"},shadows={SessionControllerIntegrationTest.Session.class,SessionControllerIntegrationTest.Audio.class,StudioScreenshotTest.Composer.class,SessionControllerIntegrationTest.Harmony.class})
@LooperMode(LooperMode.Mode.PAUSED)
public class SessionControllerIntegrationTest {
    @org.robolectric.annotation.internal.DoNotInstrument
    @Implements(NativeSession.class) public static class Session {
        static int calls,arms;static boolean armAllowed=true;
        static long[] current;
        @Implementation public static void __staticInitializer__(){}
        @Implementation public static long[] state(){calls++;return current.clone();}
        @Implementation public static boolean prepare(String[] paths,long[] clips,double[] gains,long duration,double bpm){calls++;current[3]=duration;return true;}
        @Implementation public static void setEnabled(boolean enabled){calls++;current[0]=enabled?1:0;}
        @Implementation public static boolean transport(int command){calls++;current[1]=command;if(command==1&&current[7]>0)current[8]=current[2];return true;}
        @Implementation public static boolean seek(long frame){calls++;current[2]=frame;return true;}
        @Implementation public static void recordBus(boolean enabled){calls++;}
        @Implementation public static boolean armCapture(long token){calls++;arms++;current[7]=token;return armAllowed;}
        @Implementation public static void finishCapture(){calls++;}
    }
    @org.robolectric.annotation.internal.DoNotInstrument
    @Implements(NativeAudio.class) public static class Audio extends TakeStorageTest.Audio {
        static int ends;static CountDownLatch endEntered,endRelease;
        @Implementation protected static void endRecord(){ends++;if(endEntered!=null){endEntered.countDown();try{if(!endRelease.await(5,TimeUnit.SECONDS))throw new AssertionError("finalizer not released");}catch(InterruptedException e){Thread.currentThread().interrupt();}}}
    }
    @org.robolectric.annotation.internal.DoNotInstrument
    @Implements(NativeHarmony.class) public static class Harmony extends StudioScreenshotTest.Harmony {
        static java.util.List<Integer> presets=java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        @Implementation protected static void configure(boolean enabled,int key,int scale,int preset,float wet,float dry,float spread){presets.add(preset);}
    }
    Context context;SessionController session;
    @Before public void setup()throws Exception{
        context=RuntimeEnvironment.getApplication();ExitCoordinator.resetForLaunch();AppLanguage.set(context,true);
        ReflectionHelpers.setStaticField(SessionController.class,"instance",null);
        AudioService.active=AudioService.playbackActive=AudioService.youtubeSelected=false;RecordingManager.active=false;RecordingManager.paused=false;
        ReflectionHelpers.<RecordingCountdown>getStaticField(AudioService.class,"countdown").cancel();
        Session.calls=Session.arms=0;Session.armAllowed=true;Session.current=new long[]{0,0,0,0,48000,0,0,0,-1,0,48000};
        Audio.ends=0;Audio.endEntered=Audio.endRelease=null;TakeStorageTest.Audio.valid=true;TakeStorageTest.Audio.stems=0;TakeStorageTest.Audio.mixes=0;StudioScreenshotTest.Audio.syncCalls.clear();StudioScreenshotTest.Audio.micOnlyCalls.clear();
        StudioScreenshotTest.Audio.stopCalls=StudioScreenshotTest.Audio.suspendCalls=StudioScreenshotTest.Audio.microphoneStarts=0;
        StudioScreenshotTest.Audio.supplied=new float[22];StudioScreenshotTest.Audio.supplied[6]=48000;StudioScreenshotTest.Audio.supplied[8]=1;StudioScreenshotTest.Audio.supplied[10]=1;
        session=SessionController.get(context);settle();assertTrue(session.ready);
    }
    @After public void cleanup()throws Exception{
        if(Audio.endRelease!=null)Audio.endRelease.countDown();if(!session.worker.isShutdown()){settle();session.worker.shutdownNow();}
        RecordingManager.active=false;StudioScreenshotTest.Audio.supplied=null;AppLanguage.set(context,false);ExitCoordinator.resetForLaunch();
    }
    void settle()throws Exception{session.worker.submit(()->{}).get(10,TimeUnit.SECONDS);}
    void recordArmed()throws Exception{session.record(null);settle();assertTrue(session.arming);}
    void recordStarted()throws Exception{recordArmed();session.beginServiceRecording(session.project.pendingTarget.getString("token"),78);settle();assertTrue(session.capturing);assertTrue(RecordingManager.active);}
    @Test public void coldLoadSaveAndOpenNeverTouchNativeOrStartMicrophone()throws Exception{
        session.save();settle();String id=session.project.id;session.open(id);settle();session.poll();assertEquals(0,Session.calls);assertEquals(0,StudioScreenshotTest.Audio.microphoneStarts);assertFalse(session.ownsAudio());
    }
    @Test public void queuedPlaybackStartCannotEscapeLaterStop()throws Exception{
        session.playPause();settle();assertEquals(1,Session.current[1]);session.stop();settle();ShadowLooper.idleMainLooper();assertEquals(0,Session.current[1]);assertNull(Shadows.shadowOf((android.app.Application)context).getNextStartedService());assertFalse(session.ownsAudio());
    }
    @Test public void stopAndNaturalEndReleaseLegacyPlaybackOwnership()throws Exception{
        session.playPause();settle();assertTrue(SessionController.legacyBlocked(context));session.stop();settle();assertFalse(SessionController.legacyBlocked(context));assertEquals(0,Session.current[0]);
        session.playPause();settle();Session.current[1]=3;session.poll();settle();assertFalse(SessionController.legacyBlocked(context));assertEquals(0,Session.current[0]);assertEquals(0,StudioScreenshotTest.Audio.microphoneStarts);
    }
    @Test public void queuedComposerEditsKeepLatestSnapshot()throws Exception{
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);session.worker.execute(()->{entered.countDown();try{release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}});assertTrue(entered.await(5,TimeUnit.SECONDS));
        ComposerProject first=new ComposerProject();first.name="First";session.updateComposer(session.project.id,first);ComposerProject latest=new ComposerProject();latest.name="Latest";session.updateComposer(session.project.id,latest);latest.name="Mutated caller";release.countDown();settle();assertEquals("Latest",session.project.composer.name);assertEquals("Latest",session.store.open(session.project.id).composer.name);
    }
    @Test public void exitDrainsAlreadyAcceptedLatestComposerEdit()throws Exception{
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);session.worker.execute(()->{entered.countDown();try{release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}});assertTrue(entered.await(5,TimeUnit.SECONDS));
        ComposerProject latest=new ComposerProject();latest.name="Last accepted chords";session.updateComposer(session.project.id,latest);String id=session.project.id;
        ExitCoordinator.requestExit();AtomicBoolean done=new AtomicBoolean();session.drainForExit(()->done.set(true));release.countDown();assertTrue(session.worker.awaitTermination(10,TimeUnit.SECONDS));ShadowLooper.idleMainLooper();
        assertTrue(done.get());assertEquals("",session.exitFailure);assertEquals("Last accepted chords",session.store.open(id).composer.name);
    }
    @Test public void exitClosesTakeAndPersistsExactPendingAssociationWithoutBulkImport()throws Exception{
        recordStarted();String id=session.project.id,take=RecordingManager.id;ExitCoordinator.requestExit();AtomicBoolean done=new AtomicBoolean();session.drainForExit(()->done.set(true));assertTrue(session.worker.awaitTermination(10,TimeUnit.SECONDS));ShadowLooper.idleMainLooper();
        assertTrue(done.get());assertEquals("",session.exitFailure);assertFalse(RecordingManager.active);assertEquals(1,Audio.ends);SessionProject reopened=session.store.open(id);assertNotNull(reopened.pendingTarget);assertEquals(take,reopened.pendingTarget.getString("takeId"));assertTrue(reopened.pendingTarget.getBoolean("anchored"));assertEquals(0,reopened.pendingTarget.getLong("startFrame"));assertEquals(0,reopened.clips.size());assertNotNull(new TakeStore(context,false).read(take));assertEquals(0,StudioScreenshotTest.Audio.microphoneStarts);
    }
    @Test public void failedNativeArmKeepsDurableTargetAndClearsCaptureOwnership()throws Exception{
        recordArmed();Session.armAllowed=false;session.beginServiceRecording(session.project.pendingTarget.getString("token"),78);settle();assertFalse(session.arming);assertFalse(session.capturing);assertFalse(RecordingManager.active);assertNotNull(session.store.open(session.project.id).pendingTarget);assertFalse(session.error.isEmpty());
    }
    @Test public void sessionRecordingRespectsSourceRetentionOffAndStillStoresEssentialVocalClip()throws Exception{
        context.getSharedPreferences("MainActivity",0).edit().putBoolean(TakeStorage.PREFERENCE,false).commit();recordStarted();
        assertFalse(TakeStorage.retainSources(context));assertEquals(0,TakeStorageTest.Audio.stems);assertEquals(1,TakeStorageTest.Audio.mixes);assertFalse(new TakeStore(context,false).file(RecordingManager.id,".raw.wav").exists());assertFalse(new TakeStore(context,false).file(RecordingManager.id,".fixed.wav").exists());assertFalse(new TakeStore(context,false).file(RecordingManager.id,".vocal.wav").exists());assertEquals(Integer.valueOf(0),StudioScreenshotTest.Audio.syncCalls.get(StudioScreenshotTest.Audio.syncCalls.size()-1));assertFalse(RecordingManager.micOnly);
        session.finishServiceRecording("");settle();assertFalse(TakeStorage.retainSources(context));assertEquals(1,session.project.clips.size());assertEquals("VOCAL",session.project.tracks.get(0).kind);
    }
    @Test public void cancellingArmingRejectsLateServiceStart()throws Exception{
        recordArmed();String token=session.project.pendingTarget.getString("token");AtomicBoolean done=new AtomicBoolean();session.finishAndThen(()->done.set(true));session.beginServiceRecording(token,78);settle();ShadowLooper.idleMainLooper();assertTrue(done.get());assertEquals(0,Session.arms);assertFalse(RecordingManager.active);assertFalse(session.arming);
    }
    @Test public void oldPendingSessionCannotClaimOrStopOrdinaryLiveTakeDuringExit()throws Exception{
        session.worker.submit(()->{SessionProject p=SessionProject.read(session.project.json());SessionProject.Track track=new SessionProject.Track();p.tracks.add(track);org.json.JSONObject target=new org.json.JSONObject().put("sessionId",p.id).put("token","old_capture").put("trackId",track.id).put("startFrame",960).put("takeId","old_take").put("anchored",true);session.store.writePendingTarget(p,target);session.project=p;return null;}).get();
        assertTrue(RecordingManager.start(context,78,false));String liveTake=RecordingManager.id;assertFalse(RecordingManager.belongsToSession(session.project.id,"old_capture"));
        ExitCoordinator.requestExit();AtomicBoolean done=new AtomicBoolean();session.drainForExit(()->done.set(true));assertTrue(session.worker.awaitTermination(10,TimeUnit.SECONDS));ShadowLooper.idleMainLooper();
        assertTrue(done.get());assertTrue(RecordingManager.active);assertEquals(0,Audio.ends);assertEquals(0,Session.calls);SessionProject reopened=session.store.open(session.project.id);assertEquals("old_capture",reopened.pendingTarget.getString("token"));assertEquals("old_take",reopened.pendingTarget.getString("takeId"));assertEquals(960,reopened.pendingTarget.getLong("startFrame"));
        ServiceController<AudioService> owner=Robolectric.buildService(AudioService.class).create();ReflectionHelpers.callInstanceMethod(owner.get(),"cleanupAudio");owner.destroy();assertFalse(RecordingManager.active);assertEquals(1,Audio.ends);org.json.JSONObject live=new TakeStore(context,false).read(liveTake);assertTrue(live.isNull("sessionTarget"));assertEquals("old_take",session.store.open(session.project.id).pendingTarget.getString("takeId"));assertEquals(0,Session.calls);
    }
    @Test public void cancellingSessionArmingCannotCloseAnUnrelatedOrdinaryTake()throws Exception{
        recordArmed();String token=session.project.pendingTarget.getString("token");assertTrue(RecordingManager.start(context,78,false));String foreign=RecordingManager.id;session.finishAndThen(()->{});settle();assertTrue(RecordingManager.active);assertEquals(foreign,RecordingManager.id);assertEquals(0,Audio.ends);assertEquals(token,session.project.pendingTarget.getString("token"));assertFalse(session.project.pendingTarget.has("takeId"));RecordingManager.stop();
    }
    @Test public void foreignCaptureCannotChangeSessionOrBackingTransport()throws Exception{
        RecordingManager.active=true;StudioScreenshotTest.Composer.transportCalls.clear();StudioScreenshotTest.Harmony.transportCalls.clear();session.playPause();settle();session.record(null);settle();
        assertEquals(0,Session.calls);assertTrue(StudioScreenshotTest.Composer.transportCalls.isEmpty());assertTrue(StudioScreenshotTest.Harmony.transportCalls.isEmpty());assertFalse(session.arming);assertNull(session.project.pendingTarget);RecordingManager.active=false;
    }
    @Test public void pendingAssociationCannotBeOverwrittenAndDiscardKeepsTakeBytes()throws Exception{
        recordArmed();String token=session.project.pendingTarget.getString("token");Session.armAllowed=false;session.beginServiceRecording(token,78);settle();
        TakeStore takes=new TakeStore(context,false);java.io.File original=takes.file("kept",".wav");TakeSourcesTest.wav(original,false,2);byte[] before=java.nio.file.Files.readAllBytes(original.toPath());
        session.worker.submit(()->{SessionProject p=SessionProject.read(session.project.json());org.json.JSONObject target=new org.json.JSONObject(p.pendingTarget.toString()).put("takeId","kept");session.store.writePendingTarget(p,target);session.project=p;return null;}).get();
        session.record(null);settle();assertEquals(token,session.project.pendingTarget.getString("token"));session.removeTrack(session.project.tracks.get(0).id);settle();assertEquals(1,session.project.tracks.size());
        session.discardPendingRecording();settle();assertNull(session.project.pendingTarget);assertEquals(0,session.project.tracks.size());assertArrayEquals(before,java.nio.file.Files.readAllBytes(original.toPath()));
    }
    @Test public void removingClipsAndTracksKeepsCopiedMediaAndOtherTracks()throws Exception{
        session.worker.submit(()->{SessionProject p=SessionProject.read(session.project.json());java.io.File source=new java.io.File(context.getCacheDir(),"remove-source.wav");TakeSourcesTest.wav(source,false,2);SessionProject.Media m=session.store.importWav(p,source,"take","legacy");session.store.addAudioClip(p,m,"Voice","VOCAL",0);SessionProject.Track other=new SessionProject.Track();other.name="Keep";p.tracks.add(other);session.store.save(p);session.project=p;return null;}).get();
        String track=session.project.tracks.get(0).id;java.io.File media=session.store.mediaFile(session.project,session.project.media.get(0));session.removeClip(session.project.clips.get(0).id);settle();assertTrue(media.isFile());assertEquals(2,session.project.tracks.size());session.removeTrack(track);settle();assertEquals("Keep",session.project.tracks.get(0).name);assertTrue(media.isFile());
    }
    @Test public void explicitVocalImportFailsRatherThanAddingOriginalBackingMix()throws Exception{
        TakeStore takes=new TakeStore(context,false);TakeSourcesTest.wav(takes.file("mixed",".wav"),false,2);takes.save("mixed","Mixed",1,false,"",new org.json.JSONObject().put("sourceVocalRetained",false));
        session.importTake("mixed",true);settle();assertEquals(0,session.project.clips.size());assertFalse(session.error.isEmpty());session.importTake("mixed",false);settle();assertEquals(1,session.project.clips.size());assertEquals("AUDIO",session.project.tracks.get(0).kind);
    }
    @Test public void reopenedSessionAppliesItsHarmonyOnlyForExplicitRecordAndRestoresStandalone()throws Exception{
        HarmonySession global=HarmonySession.get(context);global.worker.submit(()->{}).get();ShadowLooper.idleMainLooper();global.ready=true;global.preset=0;global.auto=false;global.configure();
        org.json.JSONObject h=new org.json.JSONObject().put("preset",3).put("key",5).put("scale",0).put("auto",true).put("wet",.7).put("dry",.9).put("spread",.4);session.importHarmony(h);settle();String id=session.project.id;session.open(id);settle();Harmony.presets.clear();recordStarted();
        assertEquals(Integer.valueOf(3),Harmony.presets.get(Harmony.presets.size()-1));assertEquals(0,global.preset);assertFalse(global.auto);session.finishServiceRecording("");settle();ShadowLooper.idleMainLooper();assertEquals(Integer.valueOf(0),Harmony.presets.get(Harmony.presets.size()-1));assertEquals(0,global.preset);
    }
    @Test public void destroyWaitsForWriterAndClosesNativeExactlyOnce()throws Exception{
        recordStarted();Audio.endEntered=new CountDownLatch(1);Audio.endRelease=new CountDownLatch(1);
        ServiceController<AudioService> owner=Robolectric.buildService(AudioService.class).create();AudioService service=owner.get();ReflectionHelpers.callInstanceMethod(service,"cleanupAudio");assertTrue(Audio.endEntered.await(5,TimeUnit.SECONDS));owner.destroy();
        assertEquals(0,StudioScreenshotTest.Audio.stopCalls);assertEquals(0,StudioScreenshotTest.Audio.suspendCalls);assertTrue(session.finalizing);
        Audio.endRelease.countDown();settle();ShadowLooper.idleMainLooper();assertEquals(1,Audio.ends);assertEquals(1,StudioScreenshotTest.Audio.stopCalls);assertFalse(RecordingManager.active);assertFalse(session.finalizing);
    }    @Test public void outputDeviceLossAndDestroyShareOneFinalizerBeforeSuspend()throws Exception{
        recordStarted();Audio.endEntered=new CountDownLatch(1);Audio.endRelease=new CountDownLatch(1);
        ServiceController<AudioService> owner=Robolectric.buildService(AudioService.class).create();AudioService service=owner.get();
        ReflectionHelpers.callInstanceMethod(service,"deviceLost",ReflectionHelpers.ClassParameter.from(boolean.class,true));assertTrue(Audio.endEntered.await(5,TimeUnit.SECONDS));owner.destroy();assertEquals(0,StudioScreenshotTest.Audio.suspendCalls);assertEquals(0,StudioScreenshotTest.Audio.stopCalls);
        Audio.endRelease.countDown();settle();ShadowLooper.idleMainLooper();assertEquals(1,Audio.ends);assertEquals(1,StudioScreenshotTest.Audio.suspendCalls);assertEquals(0,StudioScreenshotTest.Audio.stopCalls);assertFalse(AudioService.active);
    }

}
