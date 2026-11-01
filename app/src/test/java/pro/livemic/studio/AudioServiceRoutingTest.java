package pro.livemic.studio;

import android.content.*;
import android.media.*;
import android.os.SystemClock;
import java.lang.reflect.Field;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.*;
import org.robolectric.shadows.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,instrumentedPackages={"pro.livemic.studio"},shadows={StudioScreenshotTest.Audio.class,StudioScreenshotTest.Composer.class,StudioScreenshotTest.Harmony.class})
@LooperMode(LooperMode.Mode.PAUSED)
public class AudioServiceRoutingTest {
 ServiceController<AudioService> controller;AudioService service;RecordingCountdown countdown;
 void action(String action){service.onStartCommand(new Intent(service,AudioService.class).setAction(action),0,1);}
 @Before public void setup()throws Exception{
  Context context=RuntimeEnvironment.getApplication();AudioRouting.prefs(context).edit().clear().commit();context.getSharedPreferences("MainActivity",0).edit().clear().commit();AudioService.active=false;AudioService.playbackActive=false;AudioService.composerRequested=false;AudioService.harmonyRequested=false;RecordingManager.active=false;AudioService.error="";StudioScreenshotTest.Audio.supplied=new float[22];StudioScreenshotTest.Audio.changes.clear();StudioScreenshotTest.Audio.rerouteMic.clear();StudioScreenshotTest.Audio.rerouteResults.clear();StudioScreenshotTest.Audio.microphoneStarts=StudioScreenshotTest.Audio.playbackStarts=StudioScreenshotTest.Audio.inputSessions=0;StudioScreenshotTest.Audio.burstCalls.clear();StudioScreenshotTest.Composer.monitorCalls.clear();Field f=AudioService.class.getDeclaredField("countdown");f.setAccessible(true);countdown=(RecordingCountdown)f.get(null);countdown.cancel();controller=Robolectric.buildService(AudioService.class).create();service=controller.get();StudioScreenshotTest.Harmony.current=new double[]{0,0,0,0,-1,-1,0,0,-1,-1,-1,0,0,-1};HarmonySession.get(service).ready=true;HarmonySession.get(service).state=StudioScreenshotTest.Harmony.current.clone();ComposerSession.get(service).capturing=false;
 }
 @After public void cleanup(){ComposerSession.get(service).capturing=false;StudioScreenshotTest.Harmony.current[4]=StudioScreenshotTest.Harmony.current[5]=-1;countdown.cancel();RecordingManager.active=false;if(AudioService.practice!=null)AudioService.practice.active=false;controller.destroy();StudioScreenshotTest.Audio.supplied=null;StudioScreenshotTest.Audio.rerouteResults.clear();AudioService.active=false;AudioService.playbackActive=false;}
 void wired(){AudioManager manager=(AudioManager)service.getSystemService(Context.AUDIO_SERVICE);AudioDeviceInfo headset=AudioDeviceInfoBuilder.newBuilder().setType(AudioDeviceInfo.TYPE_WIRED_HEADPHONES).build();Shadows.shadowOf(manager).setOutputDevices(Collections.singletonList(headset));}
 void clearSpies(){StudioScreenshotTest.Audio.changes.clear();StudioScreenshotTest.Audio.rerouteMic.clear();StudioScreenshotTest.Composer.monitorCalls.clear();}
 @Test public void playbackRouteNeverStartsMicrophone(){action(AudioService.PLAYBACK);assertTrue(AudioService.playbackActive);assertEquals(1,StudioScreenshotTest.Audio.playbackStarts);assertEquals(0,StudioScreenshotTest.Audio.microphoneStarts);clearSpies();action(AudioService.ROUTE);assertEquals(Collections.singletonList(false),StudioScreenshotTest.Audio.rerouteMic);assertEquals(0,StudioScreenshotTest.Audio.microphoneStarts);assertFalse(AudioService.active);assertTrue(AudioService.playbackActive);}
 @Test public void activeRouteRestoresOnlyActualSafeMonitoring(){
  wired();action("start mic");assertTrue(AudioService.active);float[] stats=StudioScreenshotTest.Audio.supplied;stats[21]=1;stats[17]=0;clearSpies();action(AudioService.ROUTE);assertEquals(Collections.singletonList(true),StudioScreenshotTest.Audio.rerouteMic);assertEquals(Arrays.asList(false,true),StudioScreenshotTest.Composer.monitorCalls);assertEquals(1,StudioScreenshotTest.Audio.changes.get(StudioScreenshotTest.Audio.changes.size()-1)[1],0);
  stats[17]=1;clearSpies();action(AudioService.ROUTE);assertEquals(Arrays.asList(false,false),StudioScreenshotTest.Composer.monitorCalls);assertEquals(0,StudioScreenshotTest.Audio.changes.get(StudioScreenshotTest.Audio.changes.size()-1)[1],0);
  stats[17]=0;stats[21]=0;clearSpies();action(AudioService.ROUTE);assertEquals(Arrays.asList(false,false),StudioScreenshotTest.Composer.monitorCalls);
 }
 @Test public void failedRouteRollsBackAndKeepsBacking()throws Exception{
  action(AudioService.PLAYBACK);assertTrue(AudioService.playbackActive);AudioRouting.prefs(service).edit().putInt("inputDevice",123).commit();StudioScreenshotTest.Audio.rerouteResults.add(false);StudioScreenshotTest.Audio.rerouteResults.add(true);clearSpies();action(AudioService.ROUTE);assertEquals(Arrays.asList(false,false),StudioScreenshotTest.Audio.rerouteMic);assertEquals(0,AudioRouting.selectedInput(service));assertTrue(AudioService.playbackActive);assertFalse(AudioService.active);assertEquals(0,StudioScreenshotTest.Audio.microphoneStarts);assertFalse(AudioService.error.isEmpty());
 }
 @Test public void recordingCountdownAndPracticeBlockReroute(){
  action(AudioService.PLAYBACK);for(int i=0;i<3;i++){clearSpies();if(i==0)RecordingManager.active=true;else if(i==1)countdown.arm(5,SystemClock.elapsedRealtime());else AudioService.practice.active=true;action(AudioService.ROUTE);assertTrue(StudioScreenshotTest.Audio.rerouteMic.isEmpty());assertTrue(AudioService.playbackActive);RecordingManager.active=false;countdown.cancel();AudioService.practice.active=false;}
 }
 @Test public void composerAndHarmonyCaptureBlockReroute(){
  action(AudioService.PLAYBACK);ComposerSession composer=ComposerSession.get(service);composer.capturing=true;clearSpies();action(AudioService.ROUTE);assertTrue(StudioScreenshotTest.Audio.rerouteMic.isEmpty());assertTrue(AudioService.playbackActive);composer.capturing=false;
  for(int index:new int[]{4,5}){StudioScreenshotTest.Harmony.current[index]=0;clearSpies();action(AudioService.ROUTE);assertTrue("harmony state "+index,StudioScreenshotTest.Audio.rerouteMic.isEmpty());assertTrue(AudioService.playbackActive);StudioScreenshotTest.Harmony.current[index]=-1;}
 }
 @Test public void effectsActionReadsServicePreferencesIncludingGainAndNoiseReduction(){
  service.getSharedPreferences("MainActivity",0).edit().putFloat("p1",.36f).putFloat("p19",0f).commit();StudioScreenshotTest.Audio.changes.clear();action(AudioService.EFFECTS);Map<Integer,Float> applied=new HashMap<>();for(float[] change:StudioScreenshotTest.Audio.changes)applied.put((int)change[0],change[1]);assertEquals(.36f,applied.get(1),.001);assertEquals(0f,applied.get(19),0);assertFalse(applied.containsKey(11));assertEquals(0,StudioScreenshotTest.Audio.microphoneStarts);
 }
}
