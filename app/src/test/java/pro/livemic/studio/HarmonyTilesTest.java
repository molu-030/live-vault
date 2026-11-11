package pro.livemic.studio;

import android.view.View;
import android.widget.Button;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowLooper;
import static org.junit.Assert.*;

/** Tile interaction, live native transitions and real layer persistence. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=28, instrumentedPackages={"pro.livemic.studio"}, shadows={StudioScreenshotTest.Audio.class, StudioScreenshotTest.Composer.class, StudioScreenshotTest.Harmony.class, YouTubePlayerTest.OfflineWeb.class})
@LooperMode(LooperMode.Mode.PAUSED)
public class HarmonyTilesTest {
    MainActivity a;
    HarmonySession s;
    HarmonyScreen screen;
    @Before public void setup() throws Exception {
        ExitCoordinator.resetForLaunch();
        Field instance=HarmonySession.class.getDeclaredField("instance");instance.setAccessible(true);
        HarmonySession old=(HarmonySession)instance.get(null);if(old!=null)old.worker.shutdownNow();instance.set(null,null);
        HarmonySessionTest.delete(new File(RuntimeEnvironment.getApplication().getFilesDir(),"vocal-harmony"));
        AudioService.active=false;AudioService.playbackActive=false;AudioService.harmonyRequested=false;AudioService.practice=null;
        RecordingManager.active=false;
        StudioScreenshotTest.Audio.supplied=null;
        StudioScreenshotTest.Harmony.current=new double[]{0,0,0,0,-1,-1,0,0,-1,-1,-1,0,0,-1};
        StudioScreenshotTest.Harmony.failSave=false;StudioScreenshotTest.Harmony.loadedWhilePlaying=false;
        StudioScreenshotTest.Composer.current=new double[]{0,0,0,0,32,-1,-1,0,0,0};
        AppLanguage.set(RuntimeEnvironment.getApplication(),true);
        a=Robolectric.buildActivity(MainActivity.class).setup().get();a.handler.removeCallbacks(a.tick);
        a.composer.worker.submit(()->{}).get(5,TimeUnit.SECONDS);
        s=a.harmony;drain();a.handler.removeCallbacks(a.tick);assertTrue(s.error,s.ready);
        a.chooseTab(9);screen=a.harmonyScreen;
    }
    void drain() throws Exception {s.worker.submit(()->{}).get(5,TimeUnit.SECONDS);ShadowLooper.idleMainLooper();if(a!=null)a.handler.removeCallbacks(a.tick);}
    @After public void cleanup() throws Exception {
        a.handler.removeCallbacksAndMessages(null);a.youtube.destroy();a.io.shutdown();s.worker.shutdown();
        AudioService.active=false;AudioService.playbackActive=false;AudioService.harmonyRequested=false;
        RuntimeEnvironment.setFontScale(1f);AppLanguage.set(RuntimeEnvironment.getApplication(),false);
    }
    @Test public void tileStatesAndRecordingTargetUpdateWithoutRedrawingAndCancelKeepsSavedPart() throws Exception {
        Files.write(new File(s.dir,"saved.wav").toPath(),new byte[]{1,2,3});s.files[1]="saved.wav";
        assertTrue(screen.select(1));screen.poll();Button selector=screen.selectors[1];
        assertTrue(selector.getText().toString().contains("selected"));
        assertTrue(screen.record.getText().toString().contains("Part 2"));
        assertTrue(screen.recordButtons[1].getText().toString().contains("replace"));
        StudioScreenshotTest.Harmony.current[4]=1;s.poll();screen.poll();
        assertSame(selector,screen.selectors[1]);assertTrue(screen.tileStates[1].getText().toString().contains("Waiting"));
        assertFalse(screen.selectors[0].isEnabled());assertFalse(screen.select(0));assertEquals(1,screen.selected);
        assertFalse(screen.restoreButtons[1].isEnabled());
        StudioScreenshotTest.Harmony.current[4]=-1;StudioScreenshotTest.Harmony.current[5]=1;s.poll();screen.poll();
        assertTrue(screen.tileStates[1].getText().toString().contains("Recording"));
        assertTrue(screen.record.getText().toString().contains("Cancel recording"));
        screen.record.performClick();
        assertEquals("saved.wav",s.files[1]);assertArrayEquals(new byte[]{1,2,3},Files.readAllBytes(new File(s.dir,"saved.wav").toPath()));
        assertEquals(-1,s.state[5],0);assertEquals(1,screen.selected);
        s.state[0]=1;screen.poll();assertEquals("Playing",screen.tileStates[1].getText().toString());
        screen.muteButtons[1].performClick();assertTrue(s.muted[1]);assertTrue(screen.tileStates[1].getText().toString().contains("Muted"));
        s.volume[1]=.4f;s.pan[1]=-.3f;screen.poll();assertTrue(screen.mixButtons[1].getText().toString().contains("40%"));assertTrue(screen.mixButtons[1].getText().toString().contains("L 30"));
    }
    @Test public void directRestoreUsesPreviousSavedFileAndSwapsUndoHistory() throws Exception {
        s.saveCompleted(0);drain();String first=s.files[0];
        s.saveCompleted(0);drain();String second=s.files[0];
        screen.poll();assertTrue(screen.restoreButtons[0].isEnabled());
        assertEquals("Restore previous part",screen.restoreButtons[0].getText().toString());
        screen.restoreButtons[0].performClick();drain();screen.poll();
        assertEquals(first,s.files[0]);assertEquals(second,s.previous[0]);assertTrue(new File(s.dir,first).exists());
        assertEquals(first,s.json().getJSONArray("layers").getJSONObject(0).getString("file"));
    }
    @Test public void failedSaveKeepsOldAudioAndPersistentFailureOnAffectedTile() throws Exception {
        s.saveCompleted(2);drain();String original=s.files[2];byte[] bytes=Files.readAllBytes(new File(s.dir,original).toPath());
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        s.worker.execute(()->{entered.countDown();try{release.await(5,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}});
        assertTrue(entered.await(5,TimeUnit.SECONDS));
        StudioScreenshotTest.Harmony.failSave=true;
        try{s.saveCompleted(2);screen.poll();assertTrue(screen.tileStates[2].getText().toString().contains("Saving"));assertFalse(screen.selectors[0].isEnabled());}
        finally{release.countDown();}
        drain();s.error="";screen.poll();
        assertTrue(screen.tileStates[2].getText().toString().contains("Could not save part"));
        assertEquals(original,s.files[2]);assertArrayEquals(bytes,Files.readAllBytes(new File(s.dir,original).toPath()));
        assertEquals(-1,s.savingSlot);assertTrue(screen.selectors[0].isEnabled());
        assertFalse(screen.tileStates[0].getText().toString().contains("Could not save"));
    }
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE) @Config(qualifiers="sw800dp-w1280dp-h800dp-mdpi") public void tabletHasTwoByTwoTilesAtBothFontScalesAndLanguages() throws Exception {checkLayout(1280,800,2);}
    @Test @Config(qualifiers="sw412dp-w412dp-h893dp-mdpi") public void phoneKeepsSingleColumnAtBothFontScalesAndLanguages() throws Exception {checkLayout(412,893,1);}
    void checkLayout(int width,int height,int columns) throws Exception {
        for(boolean english:new boolean[]{false,true})for(float scale:new float[]{1f,1.3f}) {
            AppLanguage.set(a,english);RuntimeEnvironment.setFontScale(scale);a.showTab();screen=a.harmonyScreen;
            a.root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));a.root.layout(0,0,width,height);
            assertEquals(4/columns,screen.partsGrid.getChildCount());
            for(int i=0;i<4;i++) {
                assertEquals(columns,((android.view.ViewGroup)screen.partsGrid.getChildAt(i/columns)).getChildCount());
                assertTrue(screen.tiles[i].getMeasuredWidth()>0);
                assertTrue(screen.selectors[i].getText().toString().contains(english?"Part "+(i+1):(i+1)+"성부"));
                for(Button button:new Button[]{screen.selectors[i],screen.muteButtons[i],screen.mixButtons[i],screen.recordButtons[i],screen.restoreButtons[i],screen.menuButtons[i]}) {
                    assertTrue("48dp touch target: "+button.getText(),button.getMeasuredHeight()>=a.dp(48));
                    assertTrue("Button stays inside tile",button.getMeasuredWidth()<=screen.tiles[i].getMeasuredWidth());
                    if(button!=screen.menuButtons[i]&&button!=screen.selectors[i]) {
                        android.text.Layout text=button.getLayout();assertNotNull(text);
                        assertEquals("Full action label remains readable: "+button.getText(),button.getText().length(),text.getLineEnd(text.getLineCount()-1));
                    }
                }
            }
            assertEquals(english?"Empty":"비어 있음",screen.tileStates[3].getText().toString());
            if(columns==2 && ((!english && scale==1f) || (english && scale==1.3f))) {
                // Visible state fixture only; no session metadata or audio is written.
                s.files[0]="fixture-main.wav";s.previous[0]="fixture-previous.wav";s.volume[0]=.68f;s.pan[0]=-.2f;
                s.files[1]="fixture-muted.wav";s.muted[1]=true;screen.selected=2;
                s.state[english?5:4]=2;screen.poll();
                a.pageScroll.scrollTo(0,screen.partsGrid.getTop());
                File root=new File(System.getProperty("user.dir"));if(root.getName().equals("app"))root=root.getParentFile();
                File output=new File(root,"private-artifacts/v394-screenshots");assertTrue(output.isDirectory()||output.mkdirs());
                StudioScreenshotTest.capture(a,width,height,new File(output,english?"harmony-tablet-en-130.png":"harmony-tablet-ko-100.png"),false);
                s.files[0]=null;s.previous[0]=null;s.volume[0]=1;s.pan[0]=0;s.files[1]=null;s.muted[1]=false;
                s.state[4]=-1;s.state[5]=-1;screen.selected=0;screen.poll();
            }
        }
    }
}
