package pro.livemic.studio;
import android.app.Dialog;
import android.graphics.Rect;
import android.view.*;
import android.widget.*;
import java.io.File;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=35,instrumentedPackages={"pro.livemic.studio"},shadows={StudioScreenshotTest.Audio.class,StudioScreenshotTest.Composer.class,StudioScreenshotTest.Harmony.class}) @GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
public class ComposerRedesignScreenshotTest {
 static View text(View v,String needle){if(v instanceof TextView&&((TextView)v).getText().toString().contains(needle))return v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){View x=text(g.getChildAt(i),needle);if(x!=null)return x;}}return null;}
 static void scroll(MainActivity a,View v){assertNotNull(v);Rect r=new Rect();v.getDrawingRect(r);a.page.offsetDescendantRectToMyCoords(v,r);a.pageScroll.scrollTo(0,r.top);}
 @Test public void composerAndLanguageScreens()throws Exception{
  File out=new File(System.getProperty("user.dir"),"build/studio-screenshots");out.mkdirs();
  for(boolean english:new boolean[]{false,true})for(float scale:new float[]{1f,1.3f}){
   RuntimeEnvironment.setQualifiers("w412dp-h893dp-mdpi");RuntimeEnvironment.setFontScale(scale);AppLanguage.set(RuntimeEnvironment.getApplication(),english);
   MainActivity a=Robolectric.buildActivity(MainActivity.class).setup().get();a.handler.removeCallbacks(a.tick);a.composer.worker.submit(()->{}).get();a.harmony.worker.submit(()->{}).get();ShadowLooper.idleMainLooper();a.handler.removeCallbacks(a.tick);a.composer.ready=true;a.composer.project=new ComposerProject();a.composer.selectedPart=0;StudioScreenshotTest.Composer.current=new double[]{0,0,0,0,32,-1,-1,0,0,0};a.root.dispatchApplyWindowInsets(new WindowInsets.Builder().setInsets(WindowInsets.Type.systemBars(),android.graphics.Insets.of(0,36,0,24)).build());
   String prefix="v33-"+(english?"en":"ko")+(scale==1?"-default":"-large");a.sourceTab=0;a.chooseTab(1);StudioScreenshotTest.capture(a,412,893,new File(out,prefix+"-top.png"),false);
   StudioScreenshotTest.Composer.current=new double[]{4.2,1,0,0,32,-1,-1,0,0,0};a.composer.poll();a.composerScreen.poll();assertEquals(1,a.composerScreen.highlightedBar);scroll(a,a.composerScreen.chordCards.get(1));StudioScreenshotTest.capture(a,412,893,new File(out,prefix+"-chords-playing.png"),false);
   scroll(a,text(a.page,english?"Tracks":"악기 트랙"));StudioScreenshotTest.capture(a,412,893,new File(out,prefix+"-tracks.png"),false);
   scroll(a,text(a.page,english?"Song order":"곡 순서"));StudioScreenshotTest.capture(a,412,893,new File(out,prefix+"-order.png"),false);
   a.composerScreen.exportProject();Dialog dialog=ShadowDialog.getLatestDialog();View content=dialog.getWindow().getDecorView().findViewById(android.R.id.content);RecordingSourceTest.captureDialog(a,dialog.getWindow().getDecorView(),new File(out,prefix+"-export.png"));dialog.dismiss();
   ComposerImportTest.Provider.source=new File(a.getCacheDir(),"review-import.json");java.nio.file.Files.write(ComposerImportTest.Provider.source.toPath(),ComposerImportTest.fixture().json().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));ComposerImportTest.Provider provider=new ComposerImportTest.Provider();android.content.pm.ProviderInfo info=new android.content.pm.ProviderInfo();info.authority="import.fixture";provider.attachInfo(a,info);ShadowContentResolver.registerProviderInternal("import.fixture",provider);ComposerImportTest.begin(a);Dialog preview=ShadowDialog.getLatestDialog();RecordingSourceTest.captureDialog(a,preview.getWindow().getDecorView(),new File(out,prefix+"-import-preview.png"));preview.dismiss();
   a.chooseTab(0);StudioScreenshotTest.capture(a,412,893,new File(out,prefix+"-live.png"),false);a.drawer();StudioScreenshotTest.capture(a,412,893,new File(out,prefix+"-language-settings.png"),true);Button language=StudioScreenshotTest.findButtonContaining(a.drawerPopup.getContentView(),english?"Language":"언어");assertNotNull(language);language.performClick();Dialog picker=ShadowDialog.getLatestDialog();RecordingSourceTest.captureDialog(a,picker.getWindow().getDecorView(),new File(out,prefix+"-language-picker.png"));picker.dismiss();a.chooseTab(9);StudioScreenshotTest.capture(a,412,893,new File(out,prefix+"-harmony.png"),false);a.io.shutdown();
  }
  RuntimeEnvironment.setQualifiers("w360dp-h800dp-mdpi");RuntimeEnvironment.setFontScale(1.3f);AppLanguage.set(RuntimeEnvironment.getApplication(),true);MainActivity small=Robolectric.buildActivity(MainActivity.class).setup().get();small.handler.removeCallbacks(small.tick);small.composer.worker.submit(()->{}).get();ShadowLooper.idleMainLooper();small.handler.removeCallbacks(small.tick);small.composer.project=new ComposerProject();small.composer.selectedPart=0;small.composer.project.scale(1);ComposerProject.Chord chord=new ComposerProject.Chord();chord.automatic=false;chord.root=1;chord.quality=9;chord.bass=6;small.composer.part().chords.get(0).add(chord);small.sourceTab=0;small.chooseTab(1);StudioScreenshotTest.capture(small,360,800,new File(out,"v33-en-small-large-top.png"),false);scroll(small,small.composerScreen.chordCards.get(0));StudioScreenshotTest.capture(small,360,800,new File(out,"v33-en-small-large-chords.png"),false);small.io.shutdown();AppLanguage.set(RuntimeEnvironment.getApplication(),false);RuntimeEnvironment.setFontScale(1f);
 }
}
