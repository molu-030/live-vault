package pro.livemic.studio;
import android.app.Dialog;
import android.view.View;
import android.widget.Button;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28,instrumentedPackages={"pro.livemic.studio"},shadows={StudioScreenshotTest.Audio.class,StudioScreenshotTest.Composer.class,StudioScreenshotTest.Harmony.class}) @LooperMode(LooperMode.Mode.PAUSED)
public class LanguageSwitchTest {
 @Before public void reset(){AppLanguage.set(RuntimeEnvironment.getApplication(),false);AudioService.active=false;AudioService.playbackActive=false;}
 @After public void cleanup(){AppLanguage.set(RuntimeEnvironment.getApplication(),false);AudioService.active=false;AudioService.playbackActive=false;}
 @Test public void languageChoicePersistsWithoutChangingProjectOrInterruptingState()throws Exception{
  MainActivity a=Robolectric.buildActivity(MainActivity.class).setup().get();a.handler.removeCallbacks(a.tick);a.composer.worker.submit(()->{}).get();ShadowLooper.idleMainLooper();a.handler.removeCallbacks(a.tick);a.composer.project.name="내 직접 이름";String project=a.composer.project.json().toString();AudioService.active=true;AudioService.playbackActive=true;a.pendingAction=9;a.recordPermissionPending=true;
  a.drawer();Button language=StudioScreenshotTest.findButtonContaining(a.drawerPopup.getContentView(),"언어");assertNotNull(language);language.performClick();Dialog choices=ShadowDialog.getLatestDialog();Button english=StudioScreenshotTest.findButton(choices.getWindow().getDecorView(),"English");assertNotNull(english);english.performClick();ShadowLooper.idleMainLooper();a.handler.removeCallbacks(a.tick);
  assertTrue(AppLanguage.english(a));assertEquals("내 직접 이름",a.composer.project.name);assertEquals(project,a.composer.project.json().toString());assertTrue(AudioService.active);assertTrue(AudioService.playbackActive);assertEquals(9,a.pendingAction);assertTrue(a.recordPermissionPending);
  assertEquals("Live",((android.widget.TextView)a.nav.getChildAt(0)).getText().toString());assertEquals("Composer",((android.widget.TextView)a.nav.getChildAt(1)).getText().toString());assertEquals("Library",((android.widget.TextView)a.nav.getChildAt(2)).getText().toString());
  a.chooseTab(7);assertNotNull(StudioScreenshotTest.findButtonContaining(a.page,"AI export"));assertNotNull(StudioScreenshotTest.findButtonContaining(a.page,"Import JSON"));a.chooseTab(9);assertEquals("English",AppLanguage.text(a,"한국어","English"));
  a.io.shutdown();MainActivity recreated=Robolectric.buildActivity(MainActivity.class).setup().get();recreated.handler.removeCallbacks(recreated.tick);assertTrue(AppLanguage.english(recreated));recreated.io.shutdown();
 }
}
