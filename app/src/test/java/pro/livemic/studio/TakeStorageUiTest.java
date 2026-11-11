package pro.livemic.studio;

import android.app.*;
import android.view.*;
import android.widget.*;
import java.nio.file.Files;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,instrumentedPackages={"pro.livemic.studio"},shadows={StudioScreenshotTest.Audio.class,StudioScreenshotTest.Composer.class,StudioScreenshotTest.Harmony.class})
@LooperMode(LooperMode.Mode.PAUSED)
public class TakeStorageUiTest {
    MainActivity a;
    @Before public void setup()throws Exception{
        ExitCoordinator.resetForLaunch();RecordingManager.active=false;AudioService.active=AudioService.playbackActive=false;
        java.lang.reflect.Field countdown=AudioService.class.getDeclaredField("countdown");countdown.setAccessible(true);((RecordingCountdown)countdown.get(null)).cancel();
        AppLanguage.set(RuntimeEnvironment.getApplication(),true);StudioScreenshotTest.Audio.supplied=new float[22];
        RuntimeEnvironment.getApplication().getSharedPreferences("MainActivity",0).edit().remove(TakeStorage.PREFERENCE).commit();
        a=Robolectric.buildActivity(MainActivity.class).setup().get();a.handler.removeCallbacks(a.tick);a.composer.worker.submit(()->{}).get();ShadowLooper.idleMainLooper();a.handler.removeCallbacks(a.tick);
    }
    @After public void cleanup()throws Exception{RecordingManager.active=false;a.io.submit(()->{}).get();ShadowLooper.idleMainLooper();a.handler.removeCallbacksAndMessages(null);a.io.shutdown();AppLanguage.set(a,false);StudioScreenshotTest.Audio.supplied=null;}
    Dialog dialog(){return ShadowDialog.getLatestDialog();}
    Button button(String label){Button button=StudioScreenshotTest.findButtonContaining(dialog().getWindow().getDecorView(),label);assertNotNull(label,button);return button;}
    void click(String label){button(label).performClick();ShadowLooper.idleMainLooper();a.handler.removeCallbacks(a.tick);}
    CheckBox retention(View view){View found=view.findViewWithTag("retain-take-sources");assertNotNull(found);return (CheckBox)found;}
    JSONObject take()throws Exception{
        String id="source-management-ui";TakeSourcesTest.wav(a.store.file(id,".wav"),false,2);TakeSourcesTest.wav(a.store.file(id,".raw.wav"),true,1);TakeSourcesTest.wav(a.store.file(id,".fixed.wav"),true,2);TakeSourcesTest.wav(a.store.file(id,".vocal.wav"),true,2);
        a.store.save(id,"Storage take",1,false,"",new JSONObject().put("cleanSources",true).put("sourceEditable",true));return a.store.read(id);
    }
    @Test @Config(shadows={TakeStorageTest.Audio.class,StudioScreenshotTest.Composer.class,StudioScreenshotTest.Harmony.class})
    public void serviceRecordingSnapshotsTheActualActivityEffectsPreferences()throws Exception{
        float old=a.values[2];boolean previousPreference=a.getPreferences(0).contains("p2");
        android.content.SharedPreferences legacy=a.getSharedPreferences("MainActivity",0);
        boolean hadLegacy=legacy.contains("p2");float oldLegacy=legacy.getFloat("p2",0);
        TakeStorageTest.Audio.mixes=TakeStorageTest.Audio.stems=0;TakeStorageTest.Audio.valid=true;
        StudioScreenshotTest.Audio.supplied[6]=48000;StudioScreenshotTest.Audio.supplied[10]=1;StudioScreenshotTest.Audio.supplied[8]=1;
        try{
            a.set(2,.63f);assertEquals(.63f,a.getPreferences(0).getFloat("p2",-1),.0001f);
            if(!"MainActivity".equals(a.getLocalClassName()))legacy.edit().putFloat("p2",.11f).commit();
            assertTrue(RecordingManager.start(a.getApplicationContext(),78,false));String id=RecordingManager.id;RecordingManager.stop();
            assertEquals(.63,a.store.read(id).getJSONArray("initialEffects").getDouble(2),.0001);a.store.delete(id);
        }finally{
            if(RecordingManager.active)RecordingManager.stop();a.set(2,old);
            if(!previousPreference)a.getPreferences(0).edit().remove("p2").commit();
            if(hadLegacy)legacy.edit().putFloat("p2",oldLegacy).commit();else legacy.edit().remove("p2").commit();
            TakeStorageTest.Audio.mixes=TakeStorageTest.Audio.stems=0;TakeStorageTest.Audio.valid=true;StudioScreenshotTest.Audio.changes.clear();
        }
    }
    @Test public void retentionChoiceRequiresSaveAndRecordingLocksAnAlreadyOpenSheet(){
        a.recordingOptions();CheckBox choice=retention(dialog().getWindow().getDecorView());assertTrue(choice.isChecked());assertTrue(choice.getMinHeight()>=a.dp(48));choice.performClick();click("Cancel");assertTrue(TakeStorage.retainSources(a));
        a.recordingOptions();retention(dialog().getWindow().getDecorView()).performClick();click("Save settings");assertFalse(TakeStorage.retainSources(a));
        a.recordingOptions();assertFalse(retention(dialog().getWindow().getDecorView()).isChecked());retention(dialog().getWindow().getDecorView()).performClick();RecordingManager.active=true;click("Save settings");assertFalse(TakeStorage.retainSources(a));RecordingManager.active=false;
        AppLanguage.set(a,false);a.recordingOptions();assertTrue(retention(dialog().getWindow().getDecorView()).getText().toString().contains("원음 보관"));
    }
    @Test public void sourceDeleteCancelKeepsBytesAndConfirmedMenuActionKeepsMix()throws Exception{
        JSONObject take=take();String id=take.getString("id");byte[] raw=Files.readAllBytes(a.store.file(id,".raw.wav").toPath()),mix=Files.readAllBytes(a.store.file(id,".wav").toPath());
        a.takeMenu(take);click("Delete only this take's sources");assertNotNull(ComposerRedesignScreenshotTest.text(dialog().getWindow().getDecorView(),"other edited copies are kept"));click("Cancel");assertArrayEquals(raw,Files.readAllBytes(a.store.file(id,".raw.wav").toPath()));
        a.takeMenu(take);click("Delete only this take's sources");click("Delete sources only");a.io.submit(()->{}).get();ShadowLooper.idleMainLooper();a.handler.removeCallbacks(a.tick);
        assertFalse(TakeStorage.hasSources(a.store,id));assertArrayEquals(mix,Files.readAllBytes(a.store.file(id,".wav").toPath()));assertEquals("deleted-by-user",a.store.read(id).getString("sourceRetention"));
    }
    @Test public void recordingStartedAfterConfirmationBlocksDeletionAndOptOutIsClearlyLabelled()throws Exception{
        JSONObject take=take();String id=take.getString("id");a.deleteTakeSources(take);RecordingManager.active=true;click("Delete sources only");assertTrue(a.store.file(id,".raw.wav").exists());RecordingManager.active=false;
        a.store.save("mix-only","Mix only",1,false,"",new JSONObject().put("sourceRetention","not-requested"));a.libraryFilter=0;a.chooseTab(4);
        assertNotNull(ComposerRedesignScreenshotTest.text(a.page,"Source retention off · Final mix kept"));
    }
}
