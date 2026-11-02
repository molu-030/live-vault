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
  StudioScreenshotTest.Audio.actualOutputDevice=0;StudioScreenshotTest.Audio.feedbackProtectionCalls.clear();
  Context context=RuntimeEnvironment.getApplication();AudioRouting.prefs(context).edit().clear().commit();context.getSharedPreferences("MainActivity",0).edit().clear().commit();AudioService.active=false;AudioService.playbackActive=false;AudioService.composerRequested=false;AudioService.harmonyRequested=false;RecordingManager.active=false;AudioService.error="";StudioScreenshotTest.Audio.supplied=new float[22];StudioScreenshotTest.Audio.changes.clear();StudioScreenshotTest.Audio.rerouteMic.clear();StudioScreenshotTest.Audio.rerouteResults.clear();StudioScreenshotTest.Audio.microphoneStarts=StudioScreenshotTest.Audio.playbackStarts=StudioScreenshotTest.Audio.inputSessions=0;StudioScreenshotTest.Audio.burstCalls.clear();StudioScreenshotTest.Composer.monitorCalls.clear();Field f=AudioService.class.getDeclaredField("countdown");f.setAccessible(true);countdown=(RecordingCountdown)f.get(null);countdown.cancel();controller=Robolectric.buildService(AudioService.class).create();service=controller.get();StudioScreenshotTest.Harmony.current=new double[]{0,0,0,0,-1,-1,0,0,-1,-1,-1,0,0,-1};HarmonySession.get(service).ready=true;HarmonySession.get(service).state=StudioScreenshotTest.Harmony.current.clone();ComposerSession.get(service).capturing=false;
 }
 @After public void cleanup(){ComposerSession.get(service).capturing=false;StudioScreenshotTest.Harmony.current[4]=StudioScreenshotTest.Harmony.current[5]=-1;countdown.cancel();RecordingManager.active=false;RecordingManager.micOnly=false;AudioService.pendingMicOnly=false;AudioService.youtubeSelected=false;if(AudioService.practice!=null)AudioService.practice.active=false;controller.destroy();StudioScreenshotTest.Audio.supplied=null;StudioScreenshotTest.Audio.rerouteResults.clear();AudioService.active=false;AudioService.playbackActive=false;}
 void wired(){AudioManager manager=(AudioManager)service.getSystemService(Context.AUDIO_SERVICE);AudioDeviceInfo headset=AudioDeviceInfoBuilder.newBuilder().setType(AudioDeviceInfo.TYPE_WIRED_HEADPHONES).build();Shadows.shadowOf(manager).setOutputDevices(Collections.singletonList(headset));}
 void clearSpies(){StudioScreenshotTest.Audio.changes.clear();StudioScreenshotTest.Audio.rerouteMic.clear();StudioScreenshotTest.Composer.monitorCalls.clear();}
 @Test public void delayedYouTubeRecordingKeepsFrozenMicOnlyModeAfterPlayerDeselection()throws Exception{
  StudioScreenshotTest.Audio.supplied[10]=1;StudioScreenshotTest.Audio.micOnlyCalls.clear();StudioScreenshotTest.Audio.syncCalls.clear();
  AudioService.youtubeSelected=true;RecordingManager.syncMs=85;
  Intent request=new Intent(service,AudioService.class).setAction(AudioService.RECORD).putExtra("micOnly",true).putExtra("delay",3);
  service.onStartCommand(request,0,1);assertTrue(AudioService.waiting());assertTrue(AudioService.pendingMicOnly);assertFalse(RecordingManager.active);
  AudioService.youtubeSelected=false;org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(3));
  java.lang.reflect.Method start=AudioService.class.getDeclaredMethod("checkStart");start.setAccessible(true);start.invoke(service);
  assertFalse(AudioService.waiting());assertTrue(RecordingManager.active);assertTrue(RecordingManager.micOnly);
  assertEquals(Collections.singletonList(true),StudioScreenshotTest.Audio.micOnlyCalls);assertEquals(Collections.singletonList(0),StudioScreenshotTest.Audio.syncCalls);
  RecordingManager.active=false;RecordingManager.micOnly=false;AudioService.pendingMicOnly=false;AudioService.youtubeSelected=false;
 }
 @Test public void stalePlaybackRequestCannotOpenNativeBackingDuringYouTubeOrFrozenMicOnlyRecording(){
  for(int state=0;state<3;state++){
   AudioService.youtubeSelected=state==0;RecordingManager.active=state==1;RecordingManager.micOnly=state==1;
   if(state==2){countdown.arm(3,SystemClock.elapsedRealtime());AudioService.pendingMicOnly=true;}
   int playbackStarts=StudioScreenshotTest.Audio.playbackStarts,micStarts=StudioScreenshotTest.Audio.microphoneStarts;
   action(AudioService.PLAYBACK);
   assertEquals(playbackStarts,StudioScreenshotTest.Audio.playbackStarts);assertEquals(micStarts,StudioScreenshotTest.Audio.microphoneStarts);assertFalse(AudioService.playbackActive);
   assertNotNull("Foreground-service deadline is satisfied",Shadows.shadowOf(service).getLastForegroundNotification());
   if(state==1)assertTrue(RecordingManager.active);if(state==2)assertTrue(AudioService.waiting());
   countdown.cancel();RecordingManager.active=false;RecordingManager.micOnly=false;AudioService.pendingMicOnly=false;AudioService.youtubeSelected=false;
  }
 }
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
 @Test public void playbackThenMicrophoneJoinRefreshesActualOutputProtection(){
  AudioManager manager=(AudioManager)service.getSystemService(Context.AUDIO_SERVICE);
  AudioDeviceInfo speaker=FeedbackRoutingTest.device(41,AudioDeviceInfo.TYPE_BUILTIN_SPEAKER);
  AudioDeviceInfo headphones=FeedbackRoutingTest.device(42,AudioDeviceInfo.TYPE_WIRED_HEADPHONES);
  Shadows.shadowOf(manager).setOutputDevices(Arrays.asList(speaker,headphones));
  StudioScreenshotTest.Audio.actualOutputDevice=41;action(AudioService.PLAYBACK);
  assertTrue(StudioScreenshotTest.Audio.feedbackProtectionCalls.contains(true));
  StudioScreenshotTest.Audio.feedbackProtectionCalls.clear();
  StudioScreenshotTest.Audio.actualOutputDevice=42;action("start mic");
  assertTrue(AudioService.active);assertTrue(StudioScreenshotTest.Audio.feedbackProtectionCalls.contains(false));
 }
 @Test public void pulseRefreshesProtectionWithoutChangingRecordingOrEffectGains()throws Exception{
  AudioManager manager=(AudioManager)service.getSystemService(Context.AUDIO_SERVICE);
  AudioDeviceInfo headphones=FeedbackRoutingTest.device(42,AudioDeviceInfo.TYPE_WIRED_HEADPHONES);
  Shadows.shadowOf(manager).setOutputDevices(Collections.singletonList(headphones));
  StudioScreenshotTest.Audio.actualOutputDevice=42;action("start mic");
  StudioScreenshotTest.Audio.supplied[10]=1;RecordingManager.active=true;RecordingManager.micOnly=true;
  StudioScreenshotTest.Audio.supplied[17]=1; // A safety mute must not end the recording.
  StudioScreenshotTest.Audio.actualOutputDevice=0;StudioScreenshotTest.Audio.feedbackProtectionCalls.clear();clearSpies();
  Field f=AudioService.class.getDeclaredField("pulse");f.setAccessible(true);((Runnable)f.get(service)).run();
  assertEquals(Collections.singletonList(true),StudioScreenshotTest.Audio.feedbackProtectionCalls);
  assertTrue(RecordingManager.active);assertTrue(RecordingManager.micOnly);assertTrue(AudioService.active);
  assertTrue("Route safety must not overwrite microphone/effect gains",StudioScreenshotTest.Audio.changes.isEmpty());
 }
 @Test public void rollbackRefreshesProtectionForRestoredActualRoute(){
  action(AudioService.PLAYBACK);StudioScreenshotTest.Audio.rerouteResults.add(false);StudioScreenshotTest.Audio.rerouteResults.add(true);
  StudioScreenshotTest.Audio.feedbackProtectionCalls.clear();action(AudioService.ROUTE);
  assertTrue(AudioService.playbackActive);assertEquals(Arrays.asList(false,false),StudioScreenshotTest.Audio.rerouteMic);
  assertTrue(StudioScreenshotTest.Audio.feedbackProtectionCalls.contains(true));
 }

}
