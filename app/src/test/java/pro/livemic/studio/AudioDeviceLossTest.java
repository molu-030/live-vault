package pro.livemic.studio;

import android.content.*;
import android.media.*;
import java.io.File;
import java.time.Duration;
import java.util.*;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.*;
import org.robolectric.shadows.*;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,instrumentedPackages={"pro.livemic.studio"},shadows={AudioDeviceLossTest.Audio.class,StudioScreenshotTest.Composer.class,StudioScreenshotTest.Harmony.class})
@LooperMode(LooperMode.Mode.PAUSED)
public class AudioDeviceLossTest {
    @Implements(value=NativeAudio.class,isInAndroidSdk=false)
    public static class Audio extends TakeStorageTest.Audio {
        static int ends;static double[] writer=new double[21];
        @Implementation protected static void endRecord(){ends++;}
        @Implementation protected static double[] diagnostics(){return writer.clone();}
        @Implementation protected static boolean beginRecordStems(String mix,String raw,String fixed,String vocal){writer[14]=writer[15]=0;return TakeStorageTest.Audio.beginRecordStems(mix,raw,fixed,vocal);}
        @Implementation protected static boolean beginRecord(String mix){writer[14]=writer[15]=0;return TakeStorageTest.Audio.beginRecord(mix);}
    }
    ServiceController<AudioService> controller;AudioService service;AudioDeviceCallback devices;RecordingCountdown countdown;TakeStore store;
    @Before public void setup()throws Exception{
        ExitCoordinator.resetForLaunch();Context context=RuntimeEnvironment.getApplication();AppLanguage.set(context,true);AudioRouting.prefs(context).edit().clear().commit();AudioRouting.studioPreferences(context).edit().clear().commit();context.getSharedPreferences("MainActivity",0).edit().remove(TakeStorage.PREFERENCE).commit();
        RecordingManager.active=false;AudioService.active=AudioService.playbackActive=AudioService.composerRequested=AudioService.harmonyRequested=AudioService.youtubeSelected=false;
        StudioScreenshotTest.Audio.supplied=new float[22];StudioScreenshotTest.Audio.supplied[6]=48000;StudioScreenshotTest.Audio.supplied[10]=1;StudioScreenshotTest.Audio.supplied[8]=1;
        StudioScreenshotTest.Audio.actualInputDevice=11;StudioScreenshotTest.Audio.actualOutputDevice=21;StudioScreenshotTest.Audio.microphoneStarts=StudioScreenshotTest.Audio.playbackStarts=StudioScreenshotTest.Audio.suspendCalls=0;StudioScreenshotTest.Audio.changes.clear();StudioScreenshotTest.Audio.rerouteResults.clear();Audio.ends=0;Audio.writer=new double[21];TakeStorageTest.Audio.valid=true;
        StudioScreenshotTest.Composer.current=new double[]{0,0,0,0,32,-1,-1,0,0,0};StudioScreenshotTest.Harmony.current=new double[]{0,0,0,0,-1,-1,0,0,-1,-1,-1,0,0,-1};
        countdown=ReflectionHelpers.getStaticField(AudioService.class,"countdown");countdown.cancel();controller=Robolectric.buildService(AudioService.class).create();service=controller.get();devices=ReflectionHelpers.getField(service,"devices");store=new TakeStore(service,false);
        ComposerSession.get(service).capturing=false;HarmonySession harmony=HarmonySession.get(service);harmony.ready=true;harmony.state=StudioScreenshotTest.Harmony.current.clone();
    }
    @After public void cleanup()throws Exception{if(RecordingManager.active)RecordingManager.stop();store.awaitRecovery();countdown.cancel();ComposerSession.get(service).capturing=false;controller.destroy();AudioService.active=AudioService.playbackActive=AudioService.youtubeSelected=false;StudioScreenshotTest.Audio.supplied=null;StudioScreenshotTest.Audio.actualInputDevice=StudioScreenshotTest.Audio.actualOutputDevice=0;StudioScreenshotTest.Audio.rerouteResults.clear();ExitCoordinator.resetForLaunch();AppLanguage.set(service,false);}
    void action(String action){service.onStartCommand(new Intent(service,AudioService.class).setAction(action),0,1);}
    void pulse(){ReflectionHelpers.<Runnable>getField(service,"pulse").run();}
    void startTake(){action(AudioService.RECORD);ReflectionHelpers.callInstanceMethod(service,"checkStart");assertTrue(RecordingManager.active);}
    void backing(){StudioScreenshotTest.Audio.supplied[16]=1;action(AudioService.PLAYBACK);}
    static AudioDeviceInfo usbMic(){return FeedbackRoutingTest.device(11,AudioDeviceInfo.TYPE_USB_DEVICE);}
    static AudioDeviceInfo headphones(){return FeedbackRoutingTest.device(21,AudioDeviceInfo.TYPE_WIRED_HEADPHONES);}
    @Test public void defaultSelectedUsbRemovalMatchesRememberedRouteAfterNativeFallbackAndFinalizesOnce()throws Exception{
        backing();startTake();String id=RecordingManager.id;assertEquals(0,AudioRouting.selectedInput(service));int starts=StudioScreenshotTest.Audio.microphoneStarts;
        StudioScreenshotTest.Audio.actualInputDevice=5;devices.onAudioDevicesRemoved(new AudioDeviceInfo[]{usbMic()});
        assertEquals(1,Audio.ends);assertFalse(RecordingManager.active);assertFalse(AudioService.active);assertTrue(AudioService.playbackActive);assertEquals(0,StudioScreenshotTest.Audio.suspendCalls);assertTrue(store.file(id,".wav").isFile());assertTrue(store.read(id).getDouble("seconds")>0);
        assertTrue(StudioScreenshotTest.Audio.changes.stream().anyMatch(change->change[0]==11&&change[1]==0));
        devices.onAudioDevicesRemoved(new AudioDeviceInfo[]{usbMic()});devices.onAudioDevicesAdded(new AudioDeviceInfo[]{usbMic()});assertEquals(1,Audio.ends);assertEquals(starts,StudioScreenshotTest.Audio.microphoneStarts);assertFalse(AudioService.active);
        StudioScreenshotTest.Audio.actualInputDevice=11;action("start mic");assertTrue(AudioService.active);assertEquals(starts+1,StudioScreenshotTest.Audio.microphoneStarts);
    }
    @Test public void outputLossAfterInputLossPausesBackingWithoutResettingPositions()throws Exception{
        backing();startTake();StudioScreenshotTest.Composer.current[0]=12.5;StudioScreenshotTest.Harmony.current[1]=3.5;
        StudioScreenshotTest.Composer.transportCalls.clear();StudioScreenshotTest.Harmony.transportCalls.clear();StudioScreenshotTest.Audio.actualInputDevice=5;devices.onAudioDevicesRemoved(new AudioDeviceInfo[]{usbMic()});
        StudioScreenshotTest.Audio.actualOutputDevice=3;devices.onAudioDevicesRemoved(new AudioDeviceInfo[]{headphones()});
        assertEquals(1,Audio.ends);assertEquals(1,StudioScreenshotTest.Audio.suspendCalls);assertTrue(StudioScreenshotTest.Composer.transportCalls.contains(2));assertFalse(StudioScreenshotTest.Composer.transportCalls.contains(0));assertEquals(12.5,StudioScreenshotTest.Composer.current[0],0);assertEquals(3.5,StudioScreenshotTest.Harmony.current[1],0);
        int starts=StudioScreenshotTest.Audio.microphoneStarts;devices.onAudioDevicesAdded(new AudioDeviceInfo[]{headphones(),usbMic()});assertEquals(starts,StudioScreenshotTest.Audio.microphoneStarts);assertFalse(AudioService.active);
    }
    @Test public void queuedComposerPlayCannotRestartAfterOutputRemoval()throws Exception{
        backing();action("start mic");ComposerSession composer=ComposerSession.get(service);composer.ready=true;StudioScreenshotTest.Composer.transportCalls.clear();composer.play();composer.worker.submit(()->{}).get();
        devices.onAudioDevicesRemoved(new AudioDeviceInfo[]{headphones()});ShadowLooper.idleMainLooper();
        assertFalse(StudioScreenshotTest.Composer.transportCalls.contains(1));assertTrue(StudioScreenshotTest.Composer.transportCalls.contains(2));assertFalse(AudioService.active);
    }
    @Test public void silentInputZeroAndUnrelatedDeviceRemovalAreHandledSeparately()throws Exception{
        backing();startTake();devices.onAudioDevicesRemoved(new AudioDeviceInfo[]{FeedbackRoutingTest.device(99,AudioDeviceInfo.TYPE_USB_DEVICE)});assertTrue(RecordingManager.active);assertEquals(0,Audio.ends);
        StudioScreenshotTest.Audio.actualInputDevice=0;pulse();assertEquals(1,Audio.ends);assertFalse(AudioService.active);assertTrue(AudioService.playbackActive);
    }
    @Test public void cacheMissesWarnButKeepRecordingAndRealWriterIoFaultFinalizesOnce()throws Exception{
        startTake();StudioScreenshotTest.Audio.supplied[13]=1;Audio.writer[16]=10;pulse();assertTrue(RecordingManager.active);assertEquals(0,Audio.ends);
        Audio.writer[15]=1;ShadowSystemClock.advanceBy(Duration.ofMillis(1100));pulse();assertFalse(RecordingManager.active);assertEquals(1,Audio.ends);assertTrue(RecordingManager.lastResult.warning.contains("error"));pulse();assertEquals(1,Audio.ends);
    }
    @Test public void actualDroppedWriterFramesFinalizeInsteadOfBeingConfusedWithCacheMisses()throws Exception{
        startTake();Audio.writer[14]=24;pulse();assertFalse(RecordingManager.active);assertEquals(1,Audio.ends);
    }
    @Test public void explicitStartupAndRouteRollbackKeepTheStoredEffectSnapshot()throws Exception{
        AudioRouting.studioPreferences(service).edit().putFloat("p2",.63f).commit();action("start mic");assertEquals(.63f,lastEffect(2),.0001);
        StudioScreenshotTest.Audio.rerouteResults.add(false);StudioScreenshotTest.Audio.rerouteResults.add(true);action(AudioService.ROUTE);assertTrue(AudioService.active);assertEquals(.63f,lastEffect(2),.0001);
    }
    float lastEffect(int key){float value=Float.NaN;for(float[] change:StudioScreenshotTest.Audio.changes)if(change[0]==key)value=change[1];return value;}
}
