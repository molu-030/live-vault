package pro.livemic.studio;

import android.app.Dialog;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Switch;
import java.nio.file.Files;
import java.util.Arrays;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowLooper;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,instrumentedPackages={"pro.livemic.studio"},shadows={TakeAudioUiTest.Audio.class,StudioScreenshotTest.Composer.class,StudioScreenshotTest.Harmony.class})
@LooperMode(LooperMode.Mode.PAUSED)
public class TakeAudioUiTest {
    @Implements(value=NativeAudio.class,isInAndroidSdk=false)
    public static class Audio extends StudioScreenshotTest.Audio {
        static boolean history;static int seconds,cancels;
        @Implementation protected static int recentVoiceSeconds(){return history?seconds:-1;}
        @Implementation protected static void recentVoiceEnabled(boolean on){history=on;seconds=0;}
        @Implementation protected static void cancelTakeRender(){cancels++;}
    }
    MainActivity a;
    @Before public void setup()throws Exception{
        ExitCoordinator.resetForLaunch();RecordingManager.active=false;AudioService.active=AudioService.playbackActive=false;Audio.history=false;Audio.seconds=Audio.cancels=0;
        StudioScreenshotTest.Audio.supplied=new float[22];a=Robolectric.buildActivity(MainActivity.class).setup().get();AppLanguage.set(a,true);
        a.handler.removeCallbacks(a.tick);a.composer.worker.submit(()->{}).get();ShadowLooper.idleMainLooper();a.handler.removeCallbacks(a.tick);
    }
    @After public void cleanup(){Dialog dialog=ShadowDialog.getLatestDialog();if(dialog!=null)dialog.dismiss();a.handler.removeCallbacksAndMessages(null);a.io.shutdown();AudioService.active=false;AppLanguage.set(a,false);StudioScreenshotTest.Audio.supplied=null;}
    static Switch toggle(View view,String label){
        if(view instanceof Switch&&((Switch)view).getText().toString().contains(label))return (Switch)view;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){Switch found=toggle(((ViewGroup)view).getChildAt(i),label);if(found!=null)return found;}return null;
    }
    @Test public void historyRequiresOptInAndASeparateMicStartAndNeverOffersEmptyRecovery(){
        int starts=StudioScreenshotTest.Audio.microphoneStarts;a.chooseTab(4);ShadowLooper.idleMainLooper();a.handler.removeCallbacks(a.tick);
        Switch history=toggle(a.page,"Keep the last 10 seconds");assertNotNull(history);assertFalse(history.isChecked());
        Button recover=StudioScreenshotTest.findButtonContaining(a.page,"Recover recent voice");assertNotNull(recover);assertFalse(recover.isEnabled());
        history.performClick();ShadowLooper.idleMainLooper();a.handler.removeCallbacks(a.tick);assertTrue(Audio.history);assertFalse(AudioService.active);assertEquals(starts,StudioScreenshotTest.Audio.microphoneStarts);
        AudioService.active=true;Audio.seconds=5;a.showTab();ShadowLooper.idleMainLooper();a.handler.removeCallbacks(a.tick);
        assertTrue(StudioScreenshotTest.findButtonContaining(a.page,"Recover recent voice").isEnabled());
        a.chooseTab(0);a.liveScreen.poll(new float[22],true);assertTrue(a.liveScreen.display.recordStatus.contains("10s history"));
        a.chooseTab(4);toggle(a.page,"Keep the last 10 seconds").performClick();assertFalse(Audio.history);assertEquals(0,Audio.seconds);
    }
    @Test public void editorUsesLocalEffectsAndBackKeepsOriginalAndLiveSettings()throws Exception{
        String id="editable-ui";TakeSourcesTest.wav(a.store.file(id,".wav"),false,2);TakeSourcesTest.wav(a.store.file(id,".raw.wav"),true,1);TakeSourcesTest.wav(a.store.file(id,".fixed.wav"),true,2);
        JSONObject take=new JSONObject().put("id",id).put("cleanSources",true).put("sourceEditable",true).put("sourceFixed",true);
        byte[] original=Files.readAllBytes(a.store.file(id,".wav").toPath());float[] live=a.values.clone();StudioScreenshotTest.Audio.changes.clear();
        a.chooseTab(4);a.takeEffectsEditor=new TakeEffectsEditor(a,take);a.takeEffectsEditor.show();Dialog dialog=ShadowDialog.getLatestDialog();assertTrue(dialog.isShowing());
        Switch compressor=toggle(dialog.getWindow().getDecorView(),"Compressor");assertNotNull(compressor);compressor.performClick();assertArrayEquals(live,a.values,0);assertTrue(StudioScreenshotTest.Audio.changes.isEmpty());
        a.onBackPressed();ShadowLooper.idleMainLooper();assertFalse(dialog.isShowing());assertTrue("Dismiss cancels pending offline rendering",Audio.cancels>0);assertArrayEquals(original,Files.readAllBytes(a.store.file(id,".wav").toPath()));assertFalse(AudioService.active);
        a.store.delete(id);
    }
    @Test public void recordedVoiceModeHasExplicitVideoChoiceAndFixedSaveControls()throws Exception{
        String id="stem-video-ui";TakeSourcesTest.wav(a.store.file(id,".wav"),false,2);TakeSourcesTest.wav(a.store.file(id,".vocal.wav"),true,2);TakeSourcesTest.wav(a.store.file(id,".fixed.wav"),true,2);Files.write(a.store.file(id,".mp4").toPath(),new byte[]{1});
        JSONObject take=new JSONObject().put("id",id).put("sourceFixed",true).put("sourceVocalRetained",true).put("vocalStemEditable",true).put("video",true).put("videoAudioOffsetUs",-85000);
        assertTrue(TakeAudioFiles.editable(a.store,take));a.takeEffectsEditor=new TakeEffectsEditor(a,take);a.takeEffectsEditor.show();
        Dialog dialog=ShadowDialog.getLatestDialog();View root=dialog.getWindow().getDecorView();
        Button wav=StudioScreenshotTest.findButtonContaining(root,"Save WAV"),video=StudioScreenshotTest.findButtonContaining(root,"Save MP4");assertNotNull(wav);assertNotNull(video);
        for(android.view.ViewParent p=wav.getParent();p!=null;p=p.getParent())assertFalse("Save stays outside scrolling effects",p instanceof android.widget.ScrollView);
        Switch compressor=toggle(root,"Compressor");assertNotNull(compressor);assertTrue(compressor.getMinHeight()>=a.dp(48));
        dialog.dismiss();take.remove("videoAudioOffsetUs");a.takeEffectsEditor=new TakeEffectsEditor(a,take);a.takeEffectsEditor.show();
        root=ShadowDialog.getLatestDialog().getWindow().getDecorView();assertNotNull(StudioScreenshotTest.findButtonContaining(root,"Save WAV"));assertNull(StudioScreenshotTest.findButtonContaining(root,"Save MP4"));a.store.delete(id);
    }

}
