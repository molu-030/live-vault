package pro.livemic.studio;

import android.graphics.*;
import android.app.Dialog;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.shadows.ShadowDialog;
import static org.junit.Assert.*;

/** Real Android layout and native Skia rendering. Audio hardware alone is shadowed.
 * Fixtures are isolated test data and are never created by the installed application. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=28, instrumentedPackages={"pro.livemic.studio"},shadows={StudioScreenshotTest.Audio.class,StudioScreenshotTest.Composer.class,StudioScreenshotTest.Harmony.class})
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class StudioScreenshotTest {
 @Before public void resetComposer()throws Exception{java.lang.reflect.Field f=ComposerSession.class.getDeclaredField("instance");f.setAccessible(true);ComposerSession old=(ComposerSession)f.get(null);if(old!=null)old.worker.shutdownNow();f.set(null,null);AudioService.active=false;AudioService.playbackActive=false;Composer.current=new double[]{0,0,0,0,32,-1,-1,0,0,0};Composer.performance=new double[0];Audio.supplied=null;Audio.changes.clear();}
 @org.robolectric.annotation.internal.DoNotInstrument
 @Implements(value=NativeAudio.class,isInAndroidSdk=false)
 public static class Audio {
  static double lastSeek=-1;static final java.util.List<Boolean> localPlayCalls=new java.util.ArrayList<>();static float[] supplied;static final java.util.List<float[]> changes=new java.util.ArrayList<>();
  static final java.util.List<Boolean> rerouteMic=new java.util.ArrayList<>();static final java.util.ArrayDeque<Boolean> rerouteResults=new java.util.ArrayDeque<>();static int microphoneStarts,playbackStarts,inputSessions,statsCalls,stopCalls,suspendCalls;static final java.util.List<Integer> burstCalls=new java.util.ArrayList<>();
  @Implementation protected static void __staticInitializer__(){}
  @Implementation protected static void set(int k,float v){changes.add(new float[]{k,v});}
  @Implementation protected static float[] stats(){statsCalls++;if(supplied!=null)return supplied.clone();float[] f=new float[22];f[2]=-1;f[6]=48000;return f;}
  @Implementation protected static float[] roll(){return new float[0];}
  @Implementation protected static double[] localPlaybackInfo(){return new double[]{0,6};}
  @Implementation protected static void seekLocal(double seconds){lastSeek=seconds;}
  @Implementation protected static void playLocal(boolean playing){localPlayCalls.add(playing);}
  @Implementation protected static boolean loadLocalFile(String p,int r,int c){return true;}
  @Implementation protected static void guideTone(float f){}
  @Implementation protected static void stop(){stopCalls++;}
  @Implementation protected static void suspend(){suspendCalls++;}
  @Implementation protected static boolean startPlayback(int out,int mode){playbackStarts++;return true;}
  @Implementation protected static boolean start(int input,int output,int mode){microphoneStarts++;return true;}
  @Implementation protected static boolean reroute(int input,int output,int mode,boolean microphone){rerouteMic.add(microphone);return rerouteResults.isEmpty()||rerouteResults.removeFirst();}
  @Implementation protected static boolean bufferBursts(int n){burstCalls.add(n);return true;}
  @Implementation protected static int[] latencyInfo(){return new int[]{0,0,0,48000,0};}
  @Implementation protected static int inputSession(){inputSessions++;return 0;}
  static int actualOutputDevice,feedbackResets;
  static final java.util.List<Boolean> feedbackProtectionCalls=new java.util.ArrayList<>();
  static final java.util.List<Float> monitorLevelCalls=new java.util.ArrayList<>();
  @Implementation protected static int outputDevice(){return actualOutputDevice;}
  @Implementation protected static void feedbackProtection(boolean risky){feedbackProtectionCalls.add(risky);}
  @Implementation protected static void monitorLevel(float level){monitorLevelCalls.add(level);}
  @Implementation protected static void resetFeedback(){feedbackResets++;}
  @Implementation protected static void microphoneOff(){}
  static final java.util.List<Boolean> micOnlyCalls=new java.util.ArrayList<>();
  static final java.util.List<Integer> syncCalls=new java.util.ArrayList<>();
  @Implementation protected static void enablePlaybackCapture(boolean enabled,float gain){}
  @Implementation protected static void recordMicOnly(boolean enabled){micOnlyCalls.add(enabled);}
  @Implementation protected static void recordSync(int milliseconds){syncCalls.add(milliseconds);}
  @Implementation protected static boolean beginRecord(String path){return true;}
 }
 @org.robolectric.annotation.internal.DoNotInstrument
 @Implements(value=NativeComposer.class,isInAndroidSdk=false)
 public static class Composer {
  static double[] current={0,0,0,0,32,-1,-1,0,0,0},performance=new double[0];
  static final java.util.List<Integer> trackCalls=new java.util.ArrayList<>();
  static int clearSoloCalls;
  static final java.util.List<Boolean> monitorCalls=new java.util.ArrayList<>();
  static final java.util.List<Integer> transportCalls=new java.util.ArrayList<>();
  static final java.util.List<Boolean> recordCalls=new java.util.ArrayList<>();
  static volatile int publishCalls;
  @Implementation protected static void __staticInitializer__(){}
  @Implementation protected static boolean loadSoundFont(String path){return true;}
  @Implementation protected static void monitor(boolean enabled){monitorCalls.add(enabled);}
  @Implementation protected static boolean publish(double[] e,double length,double bar,double bpm,int key,int scale){publishCalls++;return true;}
  @Implementation protected static void transport(int command){transportCalls.add(command);}
  @Implementation protected static void track(int c,float v,float p,boolean m,boolean s){trackCalls.add(c);}
  @Implementation protected static void clearSolo(){clearSoloCalls++;}
  @Implementation protected static void voice(boolean enabled,int program,int mode,int octave,boolean range,boolean snap,int response,int quality,boolean rhythm,boolean mix,float conf,float gate){}
  @Implementation protected static double[] state(){return current.clone();}
  @Implementation protected static double[] drain(){double[] p=performance;performance=new double[0];return p;}
  @Implementation protected static void record(boolean arm){recordCalls.add(arm);}
  @Implementation protected static String[] drumKits(){return new String[]{"0 · Standard","8 · Room"};}
 }
 @org.robolectric.annotation.internal.DoNotInstrument
 @Implements(value=NativeHarmony.class,isInAndroidSdk=false)
 public static class Harmony {
  static double[] current={0,0,0,0,-1,-1,0,0,-1,-1,-1,0,0,-1};
  static boolean failSave=false,loadedWhilePlaying=false;
  static final java.util.List<Integer> transportCalls=new java.util.ArrayList<>();
  @Implementation protected static void __staticInitializer__(){}
  @Implementation protected static boolean prepare(int bpm,int meter,int bars){current[2]=60.0/bpm*bars*(meter==1?3:4);return true;}
  @Implementation protected static void configure(boolean enabled,int key,int scale,int preset,float wet,float dry,float spread){}
  @Implementation protected static void monitor(boolean enabled){}
  @Implementation protected static void transport(int command){transportCalls.add(command);current[0]=command==1?1:0;if(command==0)current[1]=0;}
  @Implementation protected static boolean arm(int slot){current[4]=slot;return true;}
  @Implementation protected static void cancel(){current[4]=-1;current[5]=-1;}
  @Implementation protected static void layer(int slot,float volume,float pan,boolean muted){}
  @Implementation protected static void clear(int slot){}
  @Implementation protected static boolean saveLayer(int slot,String path){if(failSave)return false;try{wav(new File(path),new byte[8]);return true;}catch(Exception e){return false;}}
  @Implementation protected static boolean loadLayer(int slot,String path){if(current[0]!=0)loadedWhilePlaying=true;return new File(path).exists();}
  @Implementation protected static double[] state(){return current.clone();}
 }
 @Test @Config(sdk=35) public void composerAndVoiceFixedControls()throws Exception{
  File out=new File(System.getProperty("user.dir"),"build/studio-screenshots");out.mkdirs();
  for(float scale:new float[]{1f,1.3f}){
   RuntimeEnvironment.setQualifiers("w412dp-h893dp-mdpi");RuntimeEnvironment.setFontScale(scale);
   MainActivity a=Robolectric.buildActivity(MainActivity.class).setup().get();a.handler.removeCallbacks(a.tick);a.composer.worker.submit(()->{}).get();ShadowLooper.idleMainLooper();a.handler.removeCallbacks(a.tick);a.composer.ready=true;
   a.root.dispatchApplyWindowInsets(new WindowInsets.Builder().setInsets(WindowInsets.Type.systemBars(),android.graphics.Insets.of(0,36,0,24)).build());
   while(a.composer.project.parts.get(0).tracks.size()<8)a.composer.project.addTrack(new TrackConfig("추가 악기",40,false));
   for(int tab:new int[]{7,8}){a.tab=tab;a.showTab();capture(a,412,893,new File(out,"v3-"+(scale==1?"default":"large")+"-"+(tab==7?"composer":"voice")+".png"),false);assertEquals(3,a.nav.getChildCount());Rect play=bounds(a,a.composerScreen.play);assertTrue(play.top>=a.pageScroll.getBottom());assertTrue(play.bottom<=a.root.getHeight()-a.root.getPaddingBottom());a.pageScroll.scrollTo(0,a.page.getHeight());assertEquals(play,bounds(a,a.composerScreen.play));if(tab==7)capture(a,412,893,new File(out,"v3-"+(scale==1?"default":"large")+"-tracks.png"),false);if(tab==8){assertNotNull(a.composerScreen.phrase);assertTrue(bounds(a,a.composerScreen.phrase).bottom<=a.root.getHeight()-a.root.getPaddingBottom());}}
   a.composerScreen.instrument(-1,true);Dialog dialog=ShadowDialog.getLatestDialog();assertNotNull(dialog);assertTrue(dialog.isShowing());ViewGroup panel=(ViewGroup)dialog.getWindow().getDecorView().findViewById(android.R.id.content);EditText query=findInput(panel,"악기 · 카테고리 검색");assertNotNull(query);query.setText("bass");assertTrue(countButtonsContaining(panel,"Bass")>=8);Button category=findButtonContaining(panel,"카테고리:");assertNotNull(category);category.performClick();Dialog choices=ShadowDialog.getLatestDialog();assertNotNull(findButton(choices.getWindow().getDecorView(),"전체 GM 128"));choices.dismiss();panel.measure(View.MeasureSpec.makeMeasureSpec(412,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(700,View.MeasureSpec.EXACTLY));panel.layout(0,0,412,700);Bitmap bitmap=Bitmap.createBitmap(412,700,Bitmap.Config.ARGB_8888);panel.draw(new Canvas(bitmap));try(FileOutputStream file=new FileOutputStream(new File(out,"v3.1-"+(scale==1?"default":"large")+"-search.png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,file);}bitmap.recycle();dialog.dismiss();a.io.shutdown();
  }RuntimeEnvironment.setFontScale(1f);
 }
 @Test @Config(sdk=35) public void harmonyFourLayersAndFixedActions()throws Exception{
  File out=new File(System.getProperty("user.dir"),"build/studio-screenshots");out.mkdirs();
  for(float scale:new float[]{1f,1.3f}){
   RuntimeEnvironment.setQualifiers("w412dp-h893dp-mdpi");RuntimeEnvironment.setFontScale(scale);
   MainActivity a=Robolectric.buildActivity(MainActivity.class).setup().get();a.handler.removeCallbacks(a.tick);a.harmony.worker.submit(()->{}).get();ShadowLooper.idleMainLooper();a.handler.removeCallbacks(a.tick);
   assertTrue(a.harmony.error,a.harmony.ready);a.root.dispatchApplyWindowInsets(new WindowInsets.Builder().setInsets(WindowInsets.Type.systemBars(),android.graphics.Insets.of(0,36,0,24)).build());a.chooseTab(9);
   capture(a,412,893,new File(out,"v3.1-"+(scale==1?"default":"large")+"-harmony.png"),false);
   for(int i=1;i<=4;i++)assertNotNull(findButtonContaining(a.page,i+"성부 ·"));a.pageScroll.scrollTo(0,a.page.getHeight());capture(a,412,893,new File(out,"v3.1-"+(scale==1?"default":"large")+"-harmony-layers.png"),false);a.harmony.state[4]=0;a.showTab();capture(a,412,893,new File(out,"v3.1-"+(scale==1?"default":"large")+"-harmony-cancel.png"),false);assertNotNull(findButtonContaining(a.actionDock,"녹음 취소"));a.harmony.state[4]=-1;a.showTab();
   Rect play=bounds(a,a.harmonyScreen.play),record=bounds(a,a.harmonyScreen.record);assertTrue(play.top>=a.pageScroll.getBottom());assertTrue(record.bottom<=a.root.getHeight()-a.root.getPaddingBottom());a.pageScroll.scrollTo(0,a.page.getHeight());assertEquals(play,bounds(a,a.harmonyScreen.play));assertEquals(record,bounds(a,a.harmonyScreen.record));a.harmonyScreen.loop();Dialog form=ShadowDialog.getLatestDialog();View formView=form.getWindow().getDecorView().findViewById(android.R.id.content);captureView(formView,412,700,new File(out,"v3.1-"+(scale==1?"default":"large")+"-tempo.png"));form.dismiss();a.io.shutdown();
  }RuntimeEnvironment.setFontScale(1f);
 }
 @Test @Config(sdk=35) public void performanceAndHarmonyReviewCaptures() throws Exception {
  File out=new File(System.getProperty("user.dir"),"build/studio-screenshots");out.mkdirs();
  for(float scale:new float[]{1f,1.3f}) {
   RuntimeEnvironment.setQualifiers("w412dp-h893dp-mdpi");RuntimeEnvironment.setFontScale(scale);
   MainActivity a=Robolectric.buildActivity(MainActivity.class).setup().get();a.handler.removeCallbacks(a.tick);a.composer.worker.submit(()->{}).get();a.harmony.worker.submit(()->{}).get();ShadowLooper.idleMainLooper();a.handler.removeCallbacks(a.tick);a.composer.ready=true;
   a.root.dispatchApplyWindowInsets(new WindowInsets.Builder().setInsets(WindowInsets.Type.systemBars(),android.graphics.Insets.of(0,36,0,24)).build());
   String prefix="review-"+(scale==1?"default":"large");a.chooseTab(7);
   capture(a,412,893,new File(out,prefix+"-composer-top.png"),false);
   a.pageScroll.scrollTo(0,a.page.getHeight()/2);capture(a,412,893,new File(out,prefix+"-composer-scroll.png"),false);
   a.composerScreen.performance();Dialog form=ShadowDialog.getLatestDialog();View formView=form.getWindow().getDecorView().findViewById(android.R.id.content);captureSheet(a,formView,new File(out,prefix+"-performance-sheet.png"));assertNotNull(findButton(formView,"적용"));form.dismiss();
   a.chooseTab(9);capture(a,412,893,new File(out,prefix+"-harmony-empty.png"),false);
   a.harmony.names[1]="아주 긴 이름의 두 번째 화음 성부 녹음 테스트";a.harmony.files[1]="fixture.wav";a.harmonyScreen.selected=1;a.showTab();capture(a,412,893,new File(out,prefix+"-harmony-selected.png"),false);
   a.pageScroll.scrollTo(0,a.page.getHeight());int scroll=a.pageScroll.getScrollY();assertTrue("fixture must be scrolled",scroll>0);a.harmonyScreen.refresh();a.root.measure(View.MeasureSpec.makeMeasureSpec(412,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(893,View.MeasureSpec.EXACTLY));a.root.layout(0,0,412,893);ShadowLooper.idleMainLooper();assertEquals("refresh preserves layer scroll",scroll,a.pageScroll.getScrollY());capture(a,412,893,new File(out,prefix+"-harmony-layers.png"),false);
   a.harmony.liveOn=true;a.harmony.state[0]=1;a.harmony.state[1]=0;a.harmony.state[2]=10.667;a.harmony.state[4]=1;a.harmony.state[3]=3;assertTrue(a.harmonyScreen.selectionLocked());assertFalse(a.harmonyScreen.select(2));assertEquals(1,a.harmonyScreen.selected);a.showTab();a.harmonyScreen.poll();capture(a,412,893,new File(out,prefix+"-harmony-countin.png"),false);
   a.harmony.state[4]=-1;a.harmony.state[5]=1;a.harmony.state[1]=2.5;a.harmony.state[3]=0;assertTrue(a.harmonyScreen.selectionLocked());assertFalse(a.harmonyScreen.select(2));assertEquals(1,a.harmonyScreen.selected);a.showTab();a.harmonyScreen.poll();capture(a,412,893,new File(out,prefix+"-harmony-recording.png"),false);
   a.io.shutdown();
  }RuntimeEnvironment.setFontScale(1f);
 }
 @Test public void phraseCommitCancellationUndoAndKeyChange()throws Exception{
  MainActivity a=Robolectric.buildActivity(MainActivity.class).setup().get();a.handler.removeCallbacks(a.tick);ComposerSession session=a.composer;session.worker.submit(()->{}).get();ShadowLooper.idleMainLooper();a.handler.removeCallbacks(a.tick);session.ready=true;session.voiceOn=true;AudioService.active=true;
  Composer.current=new double[]{2,1,0,0,32,60,60,.1,0,0};session.poll();session.recordPhrase();assertTrue(session.capturing);assertEquals(4,session.part().tracks.size());
  Composer.performance=new double[]{4,1,60,90,0,.8,0,0,4.2,2,60,0,15,.7,0,0,5,0,60,0,0,.7,0,0};Composer.current[8]=3;session.poll();assertFalse(session.capturing);assertEquals(5,session.part().tracks.size());assertEquals(4,session.part().phrases.get(0).notes.get(0).beat,0);assertEquals(1,session.part().phrases.get(0).notes.get(0).curve.size());
  String saved=session.project.json().toString();Composer.current[8]=0;session.poll();session.recordPhrase();session.cancelPhrase();assertEquals(saved,session.project.json().toString());session.history(false);assertEquals(4,session.part().tracks.size());session.history(true);assertEquals(5,session.part().tracks.size());session.edit(p->p.transpose(2),false);assertEquals(62,session.part().phrases.get(0).notes.get(0).midi);AudioService.active=false;session.voiceOn=false;a.io.shutdown();
 }
 @Test public void renderStudioScreens()throws Exception {
  File out=new File(System.getProperty("user.dir"),"build/studio-screenshots");out.mkdirs();
  for(int width:new int[]{360,412}){
   int physicalHeight=width==360?800:915,height=physicalHeight-72;
   RuntimeEnvironment.setQualifiers("w"+width+"dp-h"+height+"dp-mdpi");
   MainActivity a=Robolectric.buildActivity(MainActivity.class).setup().get();a.handler.removeCallbacks(a.tick);a.activityVisible=true;
   assertEquals("Top navigation has three items",3,a.nav.getChildCount());
   seed(a);a.selectedFile="보컬 연습 01.wav";a.preset(1);a.set(0,.8f);a.handler.removeCallbacks(a.tick);
   a.getResources().getDisplayMetrics().density=1;a.getResources().getDisplayMetrics().scaledDensity=1;a.getResources().getDisplayMetrics().widthPixels=width;a.getResources().getDisplayMetrics().heightPixels=height;
   for(int page:new int[]{0,1,4,5}){
    a.tab=page;a.showTab();a.transport.setVisibility(View.GONE);a.tick.run();a.handler.removeCallbacks(a.tick);a.io.submit(()->{}).get();ShadowLooper.idleMainLooper();a.handler.removeCallbacks(a.tick);
    capture(a,width,height,new File(out,width+"-"+page+"-idle.png"),false);
    if(page==0){
     try{RecordingManager.active=true;a.tick.run();a.handler.removeCallbacks(a.tick);capture(a,width,height,new File(out,width+"-0-recording-fixture.png"),false);assertTrue(a.recButton.getText().toString().contains("종료"));assertFalse(a.recordOptions.isEnabled());}
     finally{RecordingManager.active=false;a.tick.run();a.handler.removeCallbacks(a.tick);}
     float[] fixture=new float[22];fixture[0]=440;fixture[2]=69;fixture[3]=.05f;fixture[10]=1;a.liveScreen.poll(fixture,true);capture(a,width,height,new File(out,width+"-0-fixture.png"),false);a.drawer();capture(a,width,height,new File(out,width+"-drawer.png"),true);a.drawerPopup.dismiss();}
   }
  // The main mic gain remains a real native parameter adjustment.
  a.tab=0;a.showTab();a.liveScreen.micVolume.setProgress(64);int gainStep=a.liveScreen.micVolume.getKeyProgressIncrement();Audio.changes.clear();assertTrue(a.liveScreen.micVolume.onKeyDown(KeyEvent.KEYCODE_DPAD_RIGHT,new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_RIGHT)));float expectedGain=(64+gainStep)/100f;assertEquals(expectedGain,a.values[0],.001f);assertFalse(Audio.changes.isEmpty());for(float[] change:Audio.changes){assertEquals(0,(int)change[0]);assertEquals(expectedGain,change[1],.001f);}
  assertEquals(18,StudioMidiPreview.read(a.store.file("render_fixture_1",".mid")).length);
  a.tab=1;a.showTab();assertNotNull(a.playerLine);Bundle seekArgs=new Bundle();seekArgs.putFloat(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE,.3f);assertTrue(a.playerLine.performAccessibilityAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.getId(),seekArgs));assertEquals(1.8,Audio.lastSeek,.001);a.playerLine.setEnabled(false);seekArgs.putFloat(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE,.8f);assertFalse(a.playerLine.performAccessibilityAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.getId(),seekArgs));assertEquals(1.8,Audio.lastSeek,.001);
  a.io.shutdown();
  }
 }
 @Test public void selectedOptionAndChoiceRemainAccessible(){
  MainActivity a=Robolectric.buildActivity(MainActivity.class).setup().get();a.handler.removeCallbacks(a.tick);
  int[] chosen={-1};StudioDialogs.choose(a,"보컬 프리셋",new String[]{"노래방","발라드"},1,i->chosen[0]=i);
  Dialog dialog=ShadowDialog.getLatestDialog();assertNotNull(dialog);Button selected=findButton(dialog.getWindow().getDecorView(),"✓  발라드");assertNotNull(selected);assertTrue(selected.isSelected());assertTrue(selected.getContentDescription().toString().contains("선택됨"));
  Button first=findButton(dialog.getWindow().getDecorView(),"노래방");assertNotNull(first);first.performClick();assertEquals(0,chosen[0]);assertFalse(dialog.isShowing());
  a.drawer();View practice=findDescription(a.drawerPopup.getContentView(),"보컬 연습,");assertNotNull(practice);practice.performClick();assertEquals(5,a.tab);assertNull(a.drawerPopup);assertEquals(3,a.nav.getChildCount());assertFalse(a.nav.getChildAt(0).isSelected());a.io.shutdown();
 }
 @Test @Config(sdk=35) public void galaxyWindowKeepsPrimaryActionsAboveSystemBars()throws Exception {
  File out=new File(System.getProperty("user.dir"),"build/studio-screenshots");out.mkdirs();
  for(float fontScale:new float[]{1f,1.3f})for(int navigationBottom:new int[]{24,48}){
   RuntimeEnvironment.setQualifiers("w412dp-h893dp-mdpi");RuntimeEnvironment.setFontScale(fontScale);
   MainActivity a=Robolectric.buildActivity(MainActivity.class).setup().get();a.handler.removeCallbacks(a.tick);a.activityVisible=false;
   android.view.WindowInsets insets=new android.view.WindowInsets.Builder().setInsets(android.view.WindowInsets.Type.statusBars(),android.graphics.Insets.of(0,36,0,0)).setInsets(android.view.WindowInsets.Type.navigationBars(),android.graphics.Insets.of(0,0,0,navigationBottom)).build();a.root.dispatchApplyWindowInsets(insets);
   a.tick.run();a.handler.removeCallbacks(a.tick);a.tick.run();a.handler.removeCallbacks(a.tick);
   String prefix="s26u-"+(fontScale==1?"default":"large")+(navigationBottom==48?"-buttons":"");
   capture(a,412,893,new File(out,prefix+"-live.png"),false);
   LinearLayout header=(LinearLayout)a.root.getChildAt(0);assertTrue("Details retains a tappable width",a.liveScreen.detailsButton.getWidth()>=48);assertTrue("Details retains a tappable height",a.liveScreen.detailsButton.getHeight()>=48);a.liveScreen.detailsButton.performClick();Dialog effects=ShadowDialog.getLatestDialog();assertNotNull(effects);assertNotNull("Preset remains accessible in Details",findButtonContaining(effects.getWindow().getDecorView(),a.tr("프리셋","Preset")));effects.dismiss();assertTrue("Emergency output control must retain a tappable width",header.getChildAt(header.getChildCount()-1).getWidth()>=48);
   Rect record=bounds(a,a.recButton);assertTrue(record.top>=a.pageScroll.getBottom());assertTrue(record.bottom<=a.root.getHeight()-a.root.getPaddingBottom());assertTrue(bounds(a,a.nav).bottom<=bounds(a,a.pageScroll).top);assertTrue(a.root.getPaddingTop()>=36);assertEquals(3,a.nav.getChildCount());
   a.pageScroll.scrollTo(0,a.page.getHeight());assertEquals(record,bounds(a,a.recButton));a.pageScroll.scrollTo(0,0);a.drawer();assertTrue(a.drawerTop+a.drawerPopup.getHeight()<=893-navigationBottom);capture(a,412,893,new File(out,prefix+"-drawer.png"),true);a.drawerPopup.dismiss();
   try{RecordingManager.active=true;a.tick.run();a.handler.removeCallbacks(a.tick);capture(a,412,893,new File(out,prefix+"-recording.png"),false);assertEquals(record,bounds(a,a.recButton));}
   finally{RecordingManager.active=false;a.tick.run();a.handler.removeCallbacks(a.tick);}
   a.chooseTab(5);capture(a,412,893,new File(out,prefix+"-practice.png"),false);Rect stop=bounds(a,a.practiceButton);a.pageScroll.scrollTo(0,a.page.getHeight());assertEquals(stop,bounds(a,a.practiceButton));assertTrue(stop.top>=a.pageScroll.getBottom());assertTrue(stop.bottom<=a.root.getHeight()-a.root.getPaddingBottom());
   a.chooseTab(0);android.view.WindowInsets keyboard=new android.view.WindowInsets.Builder(insets).setInsets(android.view.WindowInsets.Type.ime(),android.graphics.Insets.of(0,0,0,300)).build();a.root.dispatchApplyWindowInsets(keyboard);capture(a,412,893,new File(out,prefix+"-keyboard.png"),false);assertTrue(bounds(a,a.nav).bottom<=bounds(a,a.pageScroll).top);assertTrue(bounds(a,a.recButton).bottom<=a.root.getHeight()-a.root.getPaddingBottom());
   a.io.shutdown();
  }
  RuntimeEnvironment.setFontScale(1f);
 }
 @Test @Config(sdk=35) public void cameraControlsStayReachableWhenPreviewScrolls()throws Exception {
  RuntimeEnvironment.setQualifiers("w412dp-h893dp-mdpi");RuntimeEnvironment.setFontScale(1.3f);
  org.robolectric.Shadows.shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(android.Manifest.permission.CAMERA);
  MainActivity a=Robolectric.buildActivity(MainActivity.class).setup().get();a.handler.removeCallbacks(a.tick);a.activityVisible=false;
  a.root.dispatchApplyWindowInsets(new WindowInsets.Builder().setInsets(WindowInsets.Type.systemBars(),android.graphics.Insets.of(0,36,0,24)).build());
  a.chooseTab(3);
  try{
   assertNotNull(a.videoButton);File out=new File(System.getProperty("user.dir"),"build/studio-screenshots");capture(a,412,893,new File(out,"s26u-camera-layout.png"),false);
   Rect record=bounds(a,a.videoButton);a.pageScroll.scrollTo(0,a.page.getHeight());assertEquals(record,bounds(a,a.videoButton));assertTrue(record.top>=a.pageScroll.getBottom());assertTrue(record.bottom<=a.root.getHeight()-a.root.getPaddingBottom());
  }finally{a.chooseTab(0);a.io.shutdown();RuntimeEnvironment.setFontScale(1f);}
 }
 static EditText findInput(View v,String hint){if(v instanceof EditText&&hint.contentEquals(((EditText)v).getHint()))return (EditText)v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){EditText x=findInput(g.getChildAt(i),hint);if(x!=null)return x;}}return null;}
 static Button findButtonContaining(View v,String text){if(v instanceof Button&&((Button)v).getText().toString().contains(text))return (Button)v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){Button x=findButtonContaining(g.getChildAt(i),text);if(x!=null)return x;}}return null;}
 static int countButtonsContaining(View v,String text){int n=v instanceof Button&&((Button)v).getText().toString().toLowerCase().contains(text.toLowerCase())?1:0;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)n+=countButtonsContaining(g.getChildAt(i),text);}return n;}
 static Rect bounds(MainActivity a,View view){Rect b=new Rect();view.getDrawingRect(b);a.root.offsetDescendantRectToMyCoords(view,b);return b;}
 static View findDescription(View view,String prefix){if(view.getContentDescription()!=null&&view.getContentDescription().toString().startsWith(prefix))return view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View match=findDescription(group.getChildAt(i),prefix);if(match!=null)return match;}}return null;}
 static Button findButton(View view,String label){if(view instanceof Button&&label.contentEquals(((Button)view).getText()))return (Button)view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){Button found=findButton(group.getChildAt(i),label);if(found!=null)return found;}}return null;}
 static void capture(MainActivity a,int w,int h,File output,boolean drawer)throws Exception {
  redraw(a.root);a.root.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));a.root.layout(0,0,w,h);
  if(a.tab==0){Rect bounds=new Rect(0,0,a.recButton.getWidth(),a.recButton.getHeight());a.root.offsetDescendantRectToMyCoords(a.recButton,bounds);System.out.println("LAYOUT "+output.getName()+" record="+bounds.bottom+" safeBottom="+(a.root.getHeight()-a.root.getPaddingBottom()));assertTrue("Recording button must remain inside safe bottom",bounds.bottom<=a.root.getHeight()-a.root.getPaddingBottom());}
  Bitmap bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);a.root.draw(canvas);
  if(drawer){View panel=a.drawerPopup.getContentView();panel.measure(View.MeasureSpec.makeMeasureSpec(a.drawerPopup.getWidth(),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(a.drawerPopup.getHeight(),View.MeasureSpec.EXACTLY));panel.layout(0,0,panel.getMeasuredWidth(),panel.getMeasuredHeight());canvas.save();canvas.translate(a.drawerLeft,a.drawerTop);panel.draw(canvas);canvas.restore();}
  try(FileOutputStream file=new FileOutputStream(output)){bitmap.compress(Bitmap.CompressFormat.PNG,100,file);}bitmap.recycle();
 }
 static void captureSheet(MainActivity a,View view,File output)throws Exception {
  a.root.measure(View.MeasureSpec.makeMeasureSpec(412,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(893,View.MeasureSpec.EXACTLY));a.root.layout(0,0,412,893);
  view.measure(View.MeasureSpec.makeMeasureSpec(412,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(893,View.MeasureSpec.AT_MOST));int height=view.getMeasuredHeight();view.layout(0,0,412,height);
  Bitmap bitmap=Bitmap.createBitmap(412,893,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);a.root.draw(canvas);canvas.drawColor(0xAD000000);canvas.save();canvas.translate(0,893-height);view.draw(canvas);canvas.restore();try(FileOutputStream file=new FileOutputStream(output)){bitmap.compress(Bitmap.CompressFormat.PNG,100,file);}bitmap.recycle();
 }
 static void captureView(View view,int width,int height,File output)throws Exception{view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));view.layout(0,0,width,height);Bitmap bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);view.draw(new Canvas(bitmap));try(FileOutputStream file=new FileOutputStream(output)){bitmap.compress(Bitmap.CompressFormat.PNG,100,file);}bitmap.recycle();}
 static void redraw(View v){v.forceLayout();v.invalidate();if(v instanceof ViewGroup){ViewGroup group=(ViewGroup)v;for(int i=0;i<group.getChildCount();i++)redraw(group.getChildAt(i));}}
 static void seed(MainActivity a)throws Exception {
  File dir=new File(a.getCacheDir(),"render-fixtures");dir.mkdirs();String[] titles={"보컬 연습 01.wav","발라드 연습.wav","내 반주.wav"};byte[] pcm=pcm();
  for(int i=2;i>=0;i--){File f=new File(dir,titles[i]);wav(f,pcm);a.recent.remember(android.net.Uri.fromFile(f),titles[i]);}
  org.json.JSONArray history=new org.json.JSONArray();for(org.json.JSONObject item:a.recent.list()){item.put("time",1793355840000L);history.put(item);}a.getSharedPreferences("backing-history",0).edit().putString("items",history.toString()).commit();
  for(int i=0;i<3;i++){String id="render_fixture_"+i;wav(a.store.file(id,".wav"),pcm);a.store.save(id,new String[]{"오늘의 연습","멜로디 스케치","보컬 트레이닝"}[i],6,false,"");org.json.JSONObject info=a.store.read(id);info.put("created",1793355840000L-i*86400000L);if(i==1)info.put("practice",new JSONObject().put("accuracy",92).put("coverage",86).put("meanAbsoluteCents",12).put("complete",true));a.store.update(id,info);try(FileOutputStream mid=new FileOutputStream(a.store.file(id,".mid"))){mid.write(new byte[]{77,84,104,100,0,0,0,6,0,0,0,1,1,(byte)224,77,84,114,107,0,0,0,58,0,(byte)144,60,90,(byte)0x83,0x60,(byte)128,60,0,0,(byte)144,64,90,(byte)0x83,0x60,(byte)128,64,0,0,(byte)144,67,90,(byte)0x83,0x60,(byte)128,67,0,0,(byte)144,65,90,(byte)0x83,0x60,(byte)128,65,0,0,(byte)144,62,90,(byte)0x83,0x60,(byte)128,62,0,0,(byte)144,60,90,(byte)0x83,0x60,(byte)128,60,0,0,(byte)255,47,0});}}
 }
 static byte[] pcm(){ByteBuffer b=ByteBuffer.allocate(48000*6*4).order(ByteOrder.LITTLE_ENDIAN);for(int n=0;n<48000*6;n++){double x=(double)n/48000;short v=(short)(Math.sin(x*2*Math.PI*220)*(.06+.28*Math.pow(Math.sin(x*2.7),4))*32767);b.putShort(v);b.putShort(v);}return b.array();}
 static void wav(File f,byte[] pcm)throws Exception {ByteBuffer b=ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);b.put("RIFF".getBytes("US-ASCII")).putInt(pcm.length+36).put("WAVEfmt ".getBytes("US-ASCII")).putInt(16).putShort((short)1).putShort((short)2).putInt(48000).putInt(192000).putShort((short)4).putShort((short)16).put("data".getBytes("US-ASCII")).putInt(pcm.length);try(FileOutputStream out=new FileOutputStream(f)){out.write(b.array());out.write(pcm);}}
}
