package pro.livemic.studio;
import android.os.Bundle;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowLooper;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28,instrumentedPackages={"pro.livemic.studio"},shadows={StudioScreenshotTest.Audio.class,StudioScreenshotTest.Composer.class,StudioScreenshotTest.Harmony.class}) @LooperMode(LooperMode.Mode.PAUSED)
public class LiveConsoleTest {
 MainActivity a;
 @Before public void setup()throws Exception{AppLanguage.set(RuntimeEnvironment.getApplication(),true);AudioService.active=false;AudioService.playbackActive=false;RecordingManager.active=false;StudioScreenshotTest.Audio.supplied=null;StudioScreenshotTest.Audio.changes.clear();a=Robolectric.buildActivity(MainActivity.class).setup().get();a.handler.removeCallbacks(a.tick);a.composer.worker.submit(()->{}).get();ShadowLooper.idleMainLooper();a.handler.removeCallbacks(a.tick);a.chooseTab(0);}
 @After public void cleanup(){a.handler.removeCallbacks(a.tick);a.io.shutdown();AudioService.active=false;RecordingManager.active=false;RecordingManager.paused=false;StudioScreenshotTest.Audio.supplied=null;AppLanguage.set(RuntimeEnvironment.getApplication(),false);}
 static void progress(SharedEffectDial k,float value){Bundle args=new Bundle();args.putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE,value);assertTrue(k.performAccessibilityAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.getId(),args));}
 @Test public void selectorsBindOneDialWithoutWritingAndExposeActualRanges(){
  int[] ids={2,3,0,4,5,6};float[] min={0,.3f,0,0,70,0},max={1,6,2,.8f,700,.7f},target={.4f,3f,1.2f,.3f,350,.4f};StudioScreenshotTest.Audio.changes.clear();
  for(int i=0;i<ids.length;i++){int count=StudioScreenshotTest.Audio.changes.size();a.liveScreen.selectors[i].performClick();assertEquals(count,StudioScreenshotTest.Audio.changes.size());AccessibilityNodeInfo.RangeInfo range=a.liveScreen.dial.createAccessibilityNodeInfo().getRangeInfo();assertEquals(min[i],range.getMin(),.001);assertEquals(max[i],range.getMax(),.001);progress(a.liveScreen.dial,target[i]);assertEquals(target[i],a.values[ids[i]],.001);assertEquals(target[i],a.getPreferences(0).getFloat("p"+ids[i],-1),.001);float[] change=StudioScreenshotTest.Audio.changes.get(StudioScreenshotTest.Audio.changes.size()-1);assertEquals(ids[i],(int)change[0]);assertEquals(target[i],change[1],.001);}
  int count=StudioScreenshotTest.Audio.changes.size();a.liveScreen.syncDial();a.liveScreen.poll(new float[22],false);assertEquals(count,StudioScreenshotTest.Audio.changes.size());
 }
 @Test public void dialTouchAndSelectionDuringGestureDoNotLeak(){
  a.liveScreen.select(0);SharedEffectDial dial=a.liveScreen.dial;dial.measure(View.MeasureSpec.makeMeasureSpec(160,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(180,View.MeasureSpec.EXACTLY));dial.layout(0,0,160,180);StudioScreenshotTest.Audio.changes.clear();
  dial.onTouchEvent(MotionEvent.obtain(0,0,MotionEvent.ACTION_DOWN,80,102,0));assertTrue(dial.isTracking());assertTrue(StudioScreenshotTest.Audio.changes.isEmpty());dial.sync(.9f);assertTrue(StudioScreenshotTest.Audio.changes.isEmpty());a.liveScreen.select(4);dial.onTouchEvent(MotionEvent.obtain(0,10,MotionEvent.ACTION_MOVE,80,50,0));for(float[] change:StudioScreenshotTest.Audio.changes)assertEquals(2,(int)change[0]);dial.onTouchEvent(MotionEvent.obtain(0,20,MotionEvent.ACTION_UP,80,50,0));assertFalse(dial.isTracking());
  a.liveScreen.select(4);progress(dial,900);assertEquals(700,a.values[5],0);int n=StudioScreenshotTest.Audio.changes.size();progress(dial,Float.NaN);assertEquals(n,StudioScreenshotTest.Audio.changes.size());dial.onKeyDown(KeyEvent.KEYCODE_DPAD_DOWN,new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_DOWN));assertTrue(a.values[5]<700);
 }
 @Test public void rotaryGestureChangesBoundValue(){
  a.liveScreen.select(0);SharedEffectDial dial=a.liveScreen.dial;dial.measure(View.MeasureSpec.makeMeasureSpec(160,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(180,View.MeasureSpec.EXACTLY));dial.layout(0,0,160,180);StudioScreenshotTest.Audio.changes.clear();dial.onTouchEvent(MotionEvent.obtain(0,0,MotionEvent.ACTION_DOWN,130,102,0));dial.onTouchEvent(MotionEvent.obtain(0,10,MotionEvent.ACTION_MOVE,80,152,0));dial.onTouchEvent(MotionEvent.obtain(0,20,MotionEvent.ACTION_UP,80,152,0));assertFalse(StudioScreenshotTest.Audio.changes.isEmpty());assertEquals(2,(int)StudioScreenshotTest.Audio.changes.get(0)[0]);
 }
 @Test public void resetDoesNotStopMicOrRecordingOrTouchOtherParameters(){
  AudioService.active=true;RecordingManager.active=true;a.values[11]=1;a.values[13]=1;a.values[7]=5;StudioScreenshotTest.Audio.changes.clear();a.liveScreen.reset();assertTrue(AudioService.active);assertTrue(RecordingManager.active);assertEquals(1,a.values[11],0);assertEquals(1,a.values[13],0);assertEquals(5,a.values[7],0);Set<Integer> keys=new HashSet<>();for(float[] v:StudioScreenshotTest.Audio.changes)keys.add((int)v[0]);assertEquals(new HashSet<>(Arrays.asList(0,2,3,4,5,6)),keys);
 }
 @Test public void lcdShowsRealInputBackingAndSafetyState(){
  float[] stats=new float[22];stats[0]=440;stats[2]=69;stats[3]=.1f;stats[8]=12;stats[16]=1;stats[17]=1;stats[21]=0;RecordingManager.active=true;a.liveScreen.poll(stats,true);assertTrue(a.liveScreen.mic.getText().toString().contains("MIC ON"));assertTrue(a.liveScreen.route.getText().toString().contains("safety mute"));assertTrue(a.liveScreen.pitch.getText().toString().contains("A"));assertTrue(a.liveScreen.backing.getText().toString().contains("Backing"));assertTrue(a.liveScreen.meter.getProgress()>0);assertTrue(a.liveScreen.clock.getText().toString().contains("00:12"));
  stats[0]=0;stats[3]=0;stats[16]=0;stats[17]=0;RecordingManager.active=false;a.liveScreen.poll(stats,false);assertTrue(a.liveScreen.mic.getText().toString().contains("READY"));assertEquals(0,a.liveScreen.meter.getProgress());assertTrue(a.liveScreen.backing.getText().toString().contains("idle"));
 }
 @Test public void presetAndSafetyMuteKeepRecordingAndMicrophoneActive(){
  AudioService.active=true;RecordingManager.active=true;float[] stats=new float[22];stats[10]=1;stats[17]=1;StudioScreenshotTest.Audio.supplied=stats;StudioScreenshotTest.Audio.changes.clear();a.monitoringOptions();assertTrue(AudioService.active);assertTrue(RecordingManager.active);for(float[] change:StudioScreenshotTest.Audio.changes)assertNotEquals(11,(int)change[0]);
  a.preset(1);assertTrue(AudioService.active);assertTrue(RecordingManager.active);assertEquals(.3,a.values[2],.001);assertTrue(a.liveScreen.selectors[0].getText().toString().contains("30%"));
 }

 @Test public void videoOpensPreviewAndIsDisabledWhileBusy(){
  a.liveScreen.video.performClick();assertEquals(3,a.tab);assertFalse(RecordingManager.active);a.chooseTab(0);a.liveScreen.transport(true,false,false);assertFalse(a.liveScreen.video.isEnabled());a.liveScreen.transport(false,true,false);assertFalse(a.liveScreen.video.isEnabled());a.liveScreen.transport(false,false,false);assertTrue(a.liveScreen.video.isEnabled());
 }
 @Test public void pausedAndMonitorStatusComeFromStats(){
  float[] stats=new float[22];stats[8]=12;stats[21]=1;RecordingManager.active=true;RecordingManager.paused=true;a.liveScreen.poll(stats,true);assertTrue(a.liveScreen.clock.getText().toString().contains("00:12"));assertTrue(a.liveScreen.clock.getText().toString().contains("Ⅱ"));assertTrue(a.liveScreen.route.getText().toString().contains("monitoring · on"));RecordingManager.paused=false;
 }

 static Switch findSwitch(View v,String label){if(v instanceof Switch&&((Switch)v).getText().toString().contains(label))return (Switch)v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){Switch found=findSwitch(g.getChildAt(i),label);if(found!=null)return found;}}return null;}
 @Test public void liveBackingVolumePersistsAndWritesOnlyBackingGain(){
  AudioService.active=true;boolean composerPlaying=a.composer.playing();a.liveScreen.backingVolume.setProgress(40);StudioScreenshotTest.Audio.changes.clear();assertTrue(a.liveScreen.backingVolume.onKeyDown(KeyEvent.KEYCODE_DPAD_RIGHT,new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_RIGHT)));assertFalse(StudioScreenshotTest.Audio.changes.isEmpty());for(float[] change:StudioScreenshotTest.Audio.changes)assertEquals(1,(int)change[0]);float value=a.values[1];assertTrue(value>0);assertEquals(value,a.getPreferences(0).getFloat("p1",-1),.001);assertTrue(AudioService.active);assertEquals(composerPlaying,a.composer.playing());a.chooseTab(4);a.chooseTab(0);assertEquals(Math.round(value*100),a.liveScreen.backingVolume.getProgress());
 }
 @Test public void sourceButtonsAndTopNavigationLeadToRequestedPages(){
  a.liveScreen.filesButton.performClick();assertEquals(1,a.tab);assertEquals(1,a.sourceTab);a.nav.getChildAt(1).performClick();assertEquals(7,a.tab);a.nav.getChildAt(2).performClick();assertEquals(4,a.tab);a.nav.getChildAt(0).performClick();assertEquals(0,a.tab);a.liveScreen.onlineButton.performClick();assertEquals(1,a.tab);assertEquals(2,a.sourceTab);a.nav.getChildAt(1).performClick();assertEquals(7,a.tab);a.nav.getChildAt(0).performClick();assertEquals(0,a.tab);
 }
 @Test public void monitorStatusUsesStatsEvenWhenCachedSettingDisagrees(){
  float[] stats=new float[22];a.values[11]=1;stats[21]=0;a.liveScreen.poll(stats,true);assertTrue(a.liveScreen.route.getText().toString().contains("monitoring · off"));a.values[11]=0;stats[21]=1;a.liveScreen.poll(stats,true);assertTrue(a.liveScreen.route.getText().toString().contains("monitoring · on"));stats[17]=1;a.liveScreen.poll(stats,true);assertTrue(a.liveScreen.route.getText().toString().contains("safety mute"));
 }
 @Test public void noiseReductionInDetailedEffectsUpdatesParameter(){
  a.chooseTab(6);Switch noise=findSwitch(a.page,"Noise reduction");assertNotNull(noise);boolean next=!noise.isChecked();StudioScreenshotTest.Audio.changes.clear();noise.performClick();assertEquals(next?1:0,a.values[19],0);assertEquals(a.values[19],a.getPreferences(0).getFloat("p19",-1),0);assertEquals(19,(int)StudioScreenshotTest.Audio.changes.get(0)[0]);a.chooseTab(0);assertEquals(next?1:0,a.values[19],0);
 }

 @Test public void captureLocksDeviceSettings(){
  a.composer.capturing=true;assertTrue(a.settingsLocked());a.composer.capturing=false;a.harmony.state[4]=0;assertTrue(a.settingsLocked());a.harmony.state[4]=-1;a.harmony.state[5]=0;assertTrue(a.settingsLocked());a.harmony.state[5]=-1;assertFalse(a.settingsLocked());
 }

 @Test public void monitorOffAndOnUpdatesComposerWithoutStoppingRecordingOrBacking(){
  AudioService.active=true;AudioService.playbackActive=true;RecordingManager.active=true;StudioScreenshotTest.Composer.monitorCalls.clear();StudioScreenshotTest.Composer.transportCalls.clear();StudioScreenshotTest.Audio.changes.clear();a.set(11,0);a.set(11,1);assertEquals(Arrays.asList(false,true),StudioScreenshotTest.Composer.monitorCalls);assertTrue(AudioService.active);assertTrue(AudioService.playbackActive);assertTrue(RecordingManager.active);assertTrue(StudioScreenshotTest.Composer.transportCalls.isEmpty());assertEquals(2,StudioScreenshotTest.Audio.changes.size());for(float[] change:StudioScreenshotTest.Audio.changes)assertEquals(11,(int)change[0]);
 }

}
