package pro.livemic.studio;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Bundle;
import java.io.File;
import org.robolectric.android.controller.ActivityController;
import android.view.*;
import android.widget.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowDialog;
import static org.junit.Assert.*;

/** Exercises actual activity recording requests; projection and audio hardware are shadowed. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=28, instrumentedPackages={"pro.livemic.studio"}, shadows={StudioScreenshotTest.Audio.class,StudioScreenshotTest.Composer.class,StudioScreenshotTest.Harmony.class,RecordingSourceTest.Projection.class})
@LooperMode(LooperMode.Mode.PAUSED)
public class RecordingSourceTest {
 @org.robolectric.annotation.internal.DoNotInstrument
 @Implements(value=CaptureSupport.class,isInAndroidSdk=false)
 public static class Projection {
  static int requests;static boolean fail;
  @Implementation protected static boolean available(){return BuildConfig.PLAYBACK_CAPTURE;}
  @Implementation protected static void request(Activity a,int code){requests++;if(fail)throw new IllegalStateException("projection unavailable");}
 }
 MainActivity a;
 @Before public void setup(){Context c=RuntimeEnvironment.getApplication();c.getSharedPreferences("MainActivity",0).edit().clear().commit();org.robolectric.Shadows.shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.RECORD_AUDIO);Projection.requests=0;Projection.fail=false;AudioService.active=false;AudioService.playbackActive=false;AudioService.youtubeSelected=false;RecordingManager.active=false;}
 @After public void cleanup(){if(a!=null){a.handler.removeCallbacks(a.tick);a.io.shutdown();}AudioService.active=false;AudioService.playbackActive=false;}
 void launch(){a=Robolectric.buildActivity(MainActivity.class).setup().get();a.handler.removeCallbacks(a.tick);while(org.robolectric.Shadows.shadowOf(RuntimeEnvironment.getApplication()).getNextStartedService()!=null){}}
 Intent next(){return org.robolectric.Shadows.shadowOf(RuntimeEnvironment.getApplication()).getNextStartedService();}
 void selectExternal(){a.recordingOptions();AlertDialog d=(AlertDialog)ShadowDialog.getLatestDialog();Switch s=findSwitch(d.getWindow().getDecorView());assertNotNull(s);s.setChecked(true);d.getButton(AlertDialog.BUTTON_POSITIVE).performClick();org.robolectric.shadows.ShadowLooper.idleMainLooper();assertTrue("opt-in applied",a.captureOtherApps);}
 static Switch findSwitch(View v){if(v instanceof Switch)return (Switch)v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){Switch s=findSwitch(g.getChildAt(i));if(s!=null)return s;}}return null;}
 void assertMicIntent(Intent i){assertNotNull(i);assertEquals(AudioService.RECORD,i.getAction());assertFalse(i.getBooleanExtra("capture",true));assertFalse(i.hasExtra("projection"));}
 @Test public void youtubeDisarmsExternalCaptureAndLateProjectionCannotStartAnotherRecording(){
  launch();a.youtube.selected=true;AudioService.youtubeSelected=true;a.captureOtherApps=true;a.pendingCapture=true;
  a.requestRecording();Intent recording=next();assertMicIntent(recording);assertTrue(recording.getBooleanExtra("micOnly",false));assertEquals(0,Projection.requests);assertFalse(a.captureOtherApps);assertFalse(a.pendingCapture);assertFalse(a.recordPermissionPending);
  a.onActivityResult(30,Activity.RESULT_OK,new Intent("late.projection"));assertNull(next());assertFalse(a.pendingCapture);AudioService.youtubeSelected=false;
 }
 @Test public void pendingProjectionPreventsYouTubeSelectionUntilCancelled(){
  org.junit.Assume.assumeTrue(BuildConfig.PLAYBACK_CAPTURE);launch();selectExternal();a.requestRecording();
  a.getPreferences(0).edit().putBoolean("youtubeConsent",true).commit();a.selectYouTube("dQw4w9WgXcQ");
  assertFalse(a.youtube.selected);assertTrue(a.recordPermissionPending);assertEquals(1,Projection.requests);assertNull(next());
  a.onActivityResult(30,Activity.RESULT_CANCELED,null);assertFalse(a.recordPermissionPending);assertNull(next());
 }
 @Test public void legacyPreferenceCannotTriggerProjectionForOrdinaryRecord(){
  RuntimeEnvironment.getApplication().getSharedPreferences("MainActivity",0).edit().putBoolean("captureOtherApps",true).commit();launch();
  assertFalse(a.captureOtherApps);assertFalse(a.getPreferences(0).getBoolean("captureOtherApps",false));
  for(int source=0;source<3;source++){a.sourceTab=source;a.selectedFile=source==1?"backing.wav":"";AudioService.playbackActive=source==0;a.requestRecording();assertMicIntent(next());assertEquals(0,Projection.requests);}
 }
 @Test public void externalCaptureIsOneShotAndCancellationClearsIt(){
  org.junit.Assume.assumeTrue(BuildConfig.PLAYBACK_CAPTURE);launch();selectExternal();a.requestRecording();assertEquals(1,Projection.requests);assertTrue(a.recordPermissionPending);assertNull(next());
  a.onActivityResult(30,Activity.RESULT_CANCELED,null);assertFalse(a.recordPermissionPending);a.requestRecording();assertMicIntent(next());assertEquals(1,Projection.requests);
 }
 @Test public void approvedConsentSchedulesExactlyOneExternalCapture(){
  org.junit.Assume.assumeTrue(BuildConfig.PLAYBACK_CAPTURE);launch();selectExternal();a.requestRecording();Intent token=new Intent("test.projection.token");a.onActivityResult(30,Activity.RESULT_OK,token);Intent record=next();assertNotNull(record);assertEquals(AudioService.RECORD,record.getAction());assertTrue(record.getBooleanExtra("capture",false));assertEquals("test.projection.token",((Intent)record.getParcelableExtra("projection")).getAction());assertNull(next());
  a.requestRecording();assertMicIntent(next());assertEquals(1,Projection.requests);
 }
 @Test public void projectionLaunchFailureClearsPendingAndNextRecordIsMic(){
  org.junit.Assume.assumeTrue(BuildConfig.PLAYBACK_CAPTURE);launch();selectExternal();Projection.fail=true;a.requestRecording();assertFalse(a.recordPermissionPending);assertNull(next());Projection.fail=false;a.requestRecording();assertMicIntent(next());assertEquals(1,Projection.requests);
 }
 @Test public void deniedMicrophoneClearsOneShot(){
  org.junit.Assume.assumeTrue(BuildConfig.PLAYBACK_CAPTURE);launch();selectExternal();org.robolectric.Shadows.shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(Manifest.permission.RECORD_AUDIO);a.requestRecording();a.onRequestPermissionsResult(12,new String[]{Manifest.permission.RECORD_AUDIO},new int[]{PackageManager.PERMISSION_DENIED});assertFalse(a.recordPermissionPending);assertEquals(0,Projection.requests);org.robolectric.Shadows.shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.RECORD_AUDIO);a.requestRecording();assertMicIntent(next());assertEquals(0,Projection.requests);
 }
 @Test public void microphoneGrantContinuesOnlyTheArmedRequest(){
  org.junit.Assume.assumeTrue(BuildConfig.PLAYBACK_CAPTURE);launch();selectExternal();org.robolectric.Shadows.shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(Manifest.permission.RECORD_AUDIO);a.requestRecording();assertEquals(1,a.recordingRequestStage);assertEquals(0,Projection.requests);
  org.robolectric.Shadows.shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.RECORD_AUDIO);a.onRequestPermissionsResult(12,new String[]{Manifest.permission.RECORD_AUDIO},new int[]{PackageManager.PERMISSION_GRANTED});assertEquals(1,Projection.requests);assertEquals(2,a.recordingRequestStage);a.onActivityResult(30,Activity.RESULT_CANCELED,null);a.requestRecording();assertMicIntent(next());
 }
 @Test public void recreationPreservesInFlightConsentButDoesNotRearm(){
  org.junit.Assume.assumeTrue(BuildConfig.PLAYBACK_CAPTURE);launch();selectExternal();a.requestRecording();assertEquals(1,Projection.requests);Bundle saved=new Bundle();a.onSaveInstanceState(saved);a.handler.removeCallbacks(a.tick);a.io.shutdown();ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).create(saved).start().resume().visible();a=controller.get();a.handler.removeCallbacks(a.tick);assertEquals(2,a.recordingRequestStage);assertTrue(a.pendingCapture);assertFalse(a.captureOtherApps);a.onActivityResult(30,Activity.RESULT_OK,new Intent("token"));assertTrue(next().getBooleanExtra("capture",false));a.requestRecording();assertMicIntent(next());assertEquals(1,Projection.requests);
 }
 @Test public void unexpectedConsentDoesNotStartRecording(){launch();a.onActivityResult(30,Activity.RESULT_OK,new Intent("unexpected"));assertNull(next());assertFalse(a.recordPermissionPending);a.requestRecording();assertMicIntent(next());assertEquals(0,Projection.requests);}
 @Test @Config(sdk=35) @GraphicsMode(GraphicsMode.Mode.NATIVE) public void recordingOptionsScreenshots()throws Exception {
  File out=new File(System.getProperty("user.dir"),"build/studio-screenshots");out.mkdirs();for(float scale:new float[]{1f,1.3f}){RuntimeEnvironment.setQualifiers("w412dp-h893dp-mdpi");RuntimeEnvironment.setFontScale(scale);launch();a.tick.run();a.handler.removeCallbacks(a.tick);String prefix="recording-source-"+(BuildConfig.PLAYBACK_CAPTURE?"personal":"standard")+(scale==1?"-default":"-large");StudioScreenshotTest.capture(a,412,893,new File(out,prefix+"-mic.png"),false);a.recordingOptions();AlertDialog dialog=(AlertDialog)ShadowDialog.getLatestDialog();View panel=dialog.getWindow().getDecorView().findViewById(android.R.id.content);captureDialog(a,dialog.getWindow().getDecorView(),new File(out,prefix+"-options.png"));if(BuildConfig.PLAYBACK_CAPTURE){Switch option=findSwitch(panel);assertNotNull(option);option.setChecked(true);captureDialog(a,dialog.getWindow().getDecorView(),new File(out,prefix+"-options-on.png"));dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();org.robolectric.shadows.ShadowLooper.idleMainLooper();assertTrue(a.captureOtherApps);a.tick.run();a.handler.removeCallbacks(a.tick);StudioScreenshotTest.capture(a,412,893,new File(out,prefix+"-armed.png"),false);}else {assertNull(findSwitch(panel));dialog.dismiss();}a.io.shutdown();}RuntimeEnvironment.setFontScale(1f);
 }

 static void captureDialog(MainActivity a,View view,File output)throws Exception {
  a.root.measure(View.MeasureSpec.makeMeasureSpec(412,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(893,View.MeasureSpec.EXACTLY));a.root.layout(0,0,412,893);view.measure(View.MeasureSpec.makeMeasureSpec(380,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(800,View.MeasureSpec.AT_MOST));int height=view.getMeasuredHeight();view.layout(0,0,380,height);android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(412,893,android.graphics.Bitmap.Config.ARGB_8888);android.graphics.Canvas canvas=new android.graphics.Canvas(bitmap);a.root.draw(canvas);canvas.drawColor(0x99000000);canvas.save();canvas.translate(16,(893-height)/2f);view.draw(canvas);canvas.restore();try(java.io.FileOutputStream out=new java.io.FileOutputStream(output)){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();
 }

}
