package pro.livemic.studio;

import android.os.Bundle;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.widget.TextView;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import java.io.File;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowLooper;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,instrumentedPackages={"pro.livemic.studio"},shadows={StudioScreenshotTest.Audio.class,LiveMixerViewTest.Composer.class,StudioScreenshotTest.Harmony.class,YouTubePlayerTest.OfflineWeb.class})
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class LiveMixerViewTest {
    MainActivity a;
    ActivityController<MainActivity> controller;
    @Before public void reset() throws Exception {
        java.lang.reflect.Field instance = ComposerSession.class.getDeclaredField("instance");
        instance.setAccessible(true);
        ComposerSession old = (ComposerSession) instance.get(null);
        if (old != null && !old.worker.isShutdown()) { old.worker.submit(() -> {}).get(); ShadowLooper.idleMainLooper(); old.worker.shutdown(); }
        instance.set(null, null);
        ExitCoordinator.resetForLaunch();
        AudioService.active = AudioService.playbackActive = AudioService.youtubeSelected = AudioService.composerRequested = AudioService.harmonyRequested = AudioService.captureActive = false;
        AudioService.practice = null;
        RecordingManager.active = RecordingManager.paused = RecordingManager.micOnly = false;
        StudioScreenshotTest.Audio.supplied = null;
        Composer.current = new double[]{0,0,0,0,32,-1,-1,0,0,0};
        Composer.channels = new float[8][4]; Composer.clears = 0;
        StudioScreenshotTest.Harmony.current = new double[]{0,0,0,0,-1,-1,0,0,-1,-1,-1,0,0,-1};
    }
    void open(int width, int height, boolean english, float scale) throws Exception {
        RuntimeEnvironment.setQualifiers("w" + width + "dp-h" + height + "dp-mdpi");
        RuntimeEnvironment.setFontScale(scale); AppLanguage.set(RuntimeEnvironment.getApplication(), english);
        TabletLayoutTest.displaySize(width, height);
        controller = Robolectric.buildActivity(MainActivity.class).setup(); a = controller.get();
        drain();
        a.composer.project = new ComposerProject(); a.composer.selectedPart = 0;
        a.composer.capturing = a.composer.voiceOn = false; a.composer.ready = true;
        a.composer.poll(); a.composer.revision++;
        ComposerProject.Part part = a.composer.part();
        while (part.tracks.size() < 8) part.tracks.add(new TrackConfig("Track " + (part.tracks.size() + 1), 0, false));
        String[] names = english ? new String[]{"Guitar","Bass","Drums","Keyboard","Piano phrase","Voice melody","Strings","Synth"}
                : new String[]{"기타","베이스","드럼","키보드","건반 프레이즈","목소리 멜로디","스트링","신스"};
        for (int i = 0; i < 8; i++) { part.tracks.get(i).name = names[i]; part.tracks.get(i).volume = .3f + i * .08f; }
        part.tracks.get(4).voice = true; part.tracks.get(4).phraseSource = "keyboard";
        part.tracks.get(5).voice = true; part.tracks.get(5).phraseSource = "microphone";
        a.chooseTab(0); measure(width, height);
    }
    void drain() throws Exception {
        if (a == null) return;
        a.handler.removeCallbacks(a.tick);
        for (int i = 0; i < 2; i++) {
            a.composer.worker.submit(() -> {}).get(); a.harmony.worker.submit(() -> {}).get();
            ShadowLooper.idleMainLooper(); a.handler.removeCallbacks(a.tick);
        }
    }
    @After public void cleanup() throws Exception {
        if (a != null) {
            if (a.liveScreen != null && a.liveScreen.mixer != null) a.liveScreen.mixer.close();
            drain(); controller.pause().stop().destroy(); a.io.shutdown();
        }
        RuntimeEnvironment.setFontScale(1f); AppLanguage.set(RuntimeEnvironment.getApplication(), false);
        ExitCoordinator.resetForLaunch();
        AudioService.active = AudioService.playbackActive = AudioService.youtubeSelected = AudioService.composerRequested = AudioService.harmonyRequested = AudioService.captureActive = false;
        AudioService.practice = null;
        RecordingManager.active = RecordingManager.paused = RecordingManager.micOnly = false;
        Composer.current = new double[]{0,0,0,0,32,-1,-1,0,0,0};
    }
    void measure(int width, int height) {
        a.root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        a.root.layout(0,0,width,height);
    }
    LiveMixerView showMixer() {
        Button mode = StudioScreenshotTest.findButton(a.root, a.tr("트랙 믹서", "Track mixer"));
        assertNotNull(mode); mode.performClick(); return a.liveScreen.mixer;
    }
    ViewGroup strip(LiveMixerView mixer, int slot) { return (ViewGroup) mixer.strips.getChildAt(slot); }
    @Test public void sourcesRespectActiveRecordedMode() throws Exception {
        open(1480,924,true,1);
        TrackConfig track = new TrackConfig();
        for (String source : new String[]{"keyboard","microphone",""}) {
            track.phraseSource = source; track.voice = false;
            assertEquals("Generated", LiveMixerView.sourceLabel(a, track));
            track.voice = true;
            assertEquals(source.equals("keyboard") ? "Keyboard" : source.equals("microphone") ? "Voice" : "Recorded", LiveMixerView.sourceLabel(a, track));
        }
    }
    @Test public void soloIsTemporaryAndPreservesNativeGainPanMute() throws Exception {
        open(1480,924,true,1); LiveMixerView mixer = showMixer();
        TrackConfig first = a.composer.part().tracks.get(0); first.pan = -.4f; first.muted = true;
        a.composer.revision++; mixer.poll();
        String before = a.composer.project.json().toString();
        ((Button) strip(mixer,1).getChildAt(3)).performClick();
        assertEquals(1, mixer.solo); assertEquals(1, Composer.channels[1][3], 0);
        assertEquals(before, a.composer.project.json().toString());
        int clears = Composer.clears;
        StudioScreenshotTest.findButton(a.root,"Vocal FX").performClick();
        assertTrue(Composer.clears > clears); assertEquals(-1,mixer.solo);
        assertEquals(first.volume, Composer.channels[0][0], 0);
        assertEquals(first.pan, Composer.channels[0][1], 0);
        assertEquals(1, Composer.channels[0][2], 0);
        for (float[] channel : Composer.channels) assertEquals(0,channel[3],0);
        showMixer(); ((Button) strip(mixer,2).getChildAt(3)).performClick();
        a.chooseTab(4); assertEquals(-1,mixer.solo);
        assertEquals(before,a.composer.project.json().toString());
    }
    @Test public void gainEditsCommitOnceAndStaleGesturesRestoreTheCurrentModel() throws Exception {
        open(1480,924,true,1); LiveMixerView mixer = showMixer(); measure(1480,924);
        LiveMixerView.Gain gain = (LiveMixerView.Gain) strip(mixer,0).getChildAt(4);
        TrackConfig track = a.composer.part().tracks.get(0); float before = track.volume;
        int revision = a.composer.revision;
        touch(gain,MotionEvent.ACTION_DOWN,20,80);
        assertFalse(touch(gain,MotionEvent.ACTION_MOVE,55,82));
        touch(gain,MotionEvent.ACTION_CANCEL,55,82);
        assertEquals(before,track.volume,0); assertFalse(mixer.dragging);
        touch(gain,MotionEvent.ACTION_DOWN,30,100); touch(gain,MotionEvent.ACTION_MOVE,30,40);
        assertEquals(before,track.volume,0); // Native preview does not mutate the saved project.
        touch(gain,MotionEvent.ACTION_UP,30,40);
        assertTrue(track.volume > before); assertEquals(revision + 1,a.composer.revision); drain();
        gain = (LiveMixerView.Gain) strip(mixer,0).getChildAt(4);
        measure(1480,924);
        touch(gain,MotionEvent.ACTION_DOWN,30,100); touch(gain,MotionEvent.ACTION_MOVE,30,35);
        track.volume = .22f; a.composer.revision++; mixer.poll();
        assertFalse(mixer.dragging); assertEquals(.22f,Composer.channels[0][0],.0001f);
        assertFalse(touch(gain,MotionEvent.ACTION_UP,30,35)); assertEquals(.22f,track.volume,0);
        assertFalse(gain.onKeyDown(KeyEvent.KEYCODE_DPAD_UP,new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_UP)));
        LiveMixerView.Gain old = (LiveMixerView.Gain) strip(mixer,0).getChildAt(4);
        TrackConfig replacement = TrackConfig.read(track.json()); a.composer.part().tracks.set(0,replacement);
        assertFalse(old.onKeyDown(KeyEvent.KEYCODE_DPAD_UP,new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_UP)));
        assertEquals(.22f,replacement.volume,0);
    }
    boolean touch(View view,int action,float x,float y) {
        MotionEvent event = MotionEvent.obtain(0,0,action,x,y,0);
        try { return view.onTouchEvent(event); } finally { event.recycle(); }
    }
    @Test public void readoutAndMixerFollowThePlayingPartAndArrangementRepeat() throws Exception {
        open(1480,924,true,1);
        ComposerProject project = a.composer.project;
        ComposerProject.Part first = project.parts.get(0);
        ComposerProject.Part second = project.duplicate(first); second.label = "B";
        project.order.clear(); project.order.add(new ComposerProject.Order(first.id,2));
        project.order.add(new ComposerProject.Order(second.id,1));
        a.composer.revision++;
        LiveMixerView mixer = showMixer();
        double length = first.bars * MusicTheory.barBeats(project.meter);
        Composer.current = new double[]{4,1,0,0,length,-1,-1,0,0,0}; a.composer.poll();
        assertEquals("Now A · 2/8 bars · Next A",a.liveScreen.arrangementText());
        Composer.current = new double[]{length + 4,1,0,length,2 * length,-1,-1,0,0,0}; a.composer.poll();
        assertEquals("Now A · 2/8 bars · Next B",a.liveScreen.arrangementText());
        ((Button) strip(mixer,0).getChildAt(3)).performClick();
        Composer.current = new double[]{2 * length,1,1,2 * length,3 * length,-1,-1,0,0,0}; a.composer.poll(); mixer.poll();
        assertSame(second,mixer.part); assertEquals(-1,mixer.solo);
        assertEquals("Now B · 1/8 bars · Next A",a.liveScreen.arrangementText());
    }
    @Test public void compactExpandedWorkspaceKeepsLcdAndIoInsideItsBounds() throws Exception {
        for (boolean english : new boolean[]{false,true}) {
            open(960,600,english,english?1.3f:1);
            measure(960,600);
            View workspace = a.root.findViewWithTag("tablet-live-workspace");
            Rect bounds = StudioScreenshotTest.bounds(a,workspace);
            assertTrue("actual viewport retains readable LCD",a.liveScreen.display.getHeight()>=a.dp(110));
            for (View control : new View[]{a.liveScreen.display,a.liveScreen.inputButton,a.outputButton,a.liveScreen.micButton,a.liveScreen.monitorToggle}) {
                if (control != a.liveScreen.display) {
                    assertTrue("I/O retains 48dp touch height",control.getHeight()>=a.dp(48));
                    assertTrue("I/O retains 48dp touch width",control.getWidth()>=a.dp(48));
                }
                Rect controlBounds = StudioScreenshotTest.bounds(a,control);
                assertTrue("control inside workspace: " + controlBounds + " in " + bounds,
                        bounds.contains(controlBounds));
                Rect visible = new Rect(); assertTrue(control.getGlobalVisibleRect(visible));
                assertEquals("control is fully visible",control.getHeight(),visible.height());
            }
            assertFalse("workspace fits after fixed siblings",a.pageScroll.canScrollVertically(1));
            assertEquals("fixed recording target stays one line",1,a.liveScreen.recordingTarget.getLineCount());
            cleanup(); a=null; controller=null; reset();
        }
    }
    @Test public void liveScreensAndMixerNativeCaptures() throws Exception {
        captureCase(false,1,"s9ultra-ko100"); cleanup(); a = null; controller = null; reset();
        captureCase(true,1.3f,"s9ultra-en130"); cleanup(); a = null; controller = null; reset();
        open(412,893,false,1);
        assertNull(a.liveScreen.mixer); assertNotNull(a.liveScreen.reverbVolume); assertNotNull(a.liveScreen.echoVolume);
        capture(412,893,"phone-ko100-effects");
    }
    void captureCase(boolean english,float scale,String name) throws Exception {
        open(1480,924,english,scale);
        capture(1480,924,name + "-effects");
        LiveMixerView mixer = showMixer(); measure(1480,924);
        capture(1480,924,name + "-mixer");
        assertEquals(8,mixer.strips.getChildCount());
        for(int i=0;i<8;i++) {
            assertTrue(strip(mixer,i).getChildAt(2).getHeight() >= a.dp(48));
            assertTrue(strip(mixer,i).getChildAt(3).getHeight() >= a.dp(48));
            for (int textIndex : new int[]{0,1,2,3}) assertVisibleText((TextView) strip(mixer,i).getChildAt(textIndex));
            for (int buttonIndex : new int[]{2,3}) {
                Button control = (Button) strip(mixer,i).getChildAt(buttonIndex);
                assertNotNull(control.getLayout());
                assertEquals("mute/solo do not wrap",1,control.getLayout().getLineCount());
                assertEquals("mute/solo label fits",0,control.getLayout().getEllipsisCount(0));
                assertEquals(StudioButton.STANDARD,((StudioButton)control).role);
            }
        }
        assertTrue(a.liveScreen.recordingTarget.getText().toString().contains(english?"full mix":"전체 믹스"));
        a.youtube.selected = true; a.liveScreen.poll(NativeAudio.stats(),false);
        assertTrue(a.liveScreen.recordingTarget.getText().toString().contains(english?"microphone only":"마이크만"));
        a.youtube.selected = false;
        RecordingManager.active = RecordingManager.micOnly = true;
        a.liveScreen.poll(NativeAudio.stats(),false);
        assertTrue(a.liveScreen.recordingTarget.getText().toString().contains(english?"microphone only":"마이크만"));
        RecordingManager.active = RecordingManager.micOnly = false;
    }
    void assertVisibleText(TextView text) {
        assertNotNull("text has a layout: " + text.getText(),text.getLayout());
        int width = text.getWidth() - text.getCompoundPaddingLeft() - text.getCompoundPaddingRight();
        int height = text.getHeight() - text.getCompoundPaddingTop() - text.getCompoundPaddingBottom();
        assertTrue("text layout fits width: " + text.getText(),text.getLayout().getWidth() <= width);
        assertTrue("text layout fits height: " + text.getText(),text.getLayout().getHeight() <= height);
        Bitmap pixels = Bitmap.createBitmap(text.getWidth(),text.getHeight(),Bitmap.Config.ARGB_8888);
        text.draw(new Canvas(pixels));
        // Compare with the same view's background, rather than requiring small,
        // antialiased glyphs to contain nearly opaque pixels of their exact text color.
        android.content.res.ColorStateList foreground = text.getTextColors();
        Bitmap background = Bitmap.createBitmap(text.getWidth(),text.getHeight(),Bitmap.Config.ARGB_8888);
        text.setTextColor(Color.TRANSPARENT);
        text.draw(new Canvas(background));
        text.setTextColor(foreground);
        int glyphPixels = 0;
        for (int y=0;y<pixels.getHeight();y++) for (int x=0;x<pixels.getWidth();x++)
            if (pixels.getPixel(x,y) != background.getPixel(x,y)) glyphPixels++;
        pixels.recycle(); background.recycle();
        assertTrue("visible glyphs are painted: " + text.getText(),glyphPixels>3);
    }
    void capture(int width,int height,String name) throws Exception {
        File folder = new File(System.getProperty("user.dir"),"build/studio-screenshots/v394"); assertTrue(folder.isDirectory() || folder.mkdirs());
        StudioScreenshotTest.capture(a,width,height,new File(folder,name + ".png"),false);
    }

    @org.robolectric.annotation.internal.DoNotInstrument
    @Implements(value=NativeComposer.class,isInAndroidSdk=false)
    public static class Composer {
        static double[] current = {0,0,0,0,32,-1,-1,0,0,0};
        static float[][] channels = new float[8][4]; static int clears;
        @Implementation protected static void __staticInitializer__() {}
        @Implementation protected static boolean loadSoundFont(String path) { return true; }
        @Implementation protected static String[] drumKits() { return new String[]{"Standard"}; }
        @Implementation protected static boolean publish(double[] events,double beats,double bar,double bpm,int key,int scale) { return true; }
        @Implementation protected static void transport(int command) {}
        @Implementation protected static void clearSolo() { clears++; for(float[] channel:channels) channel[3]=0; }
        @Implementation protected static void track(int channel,float volume,float pan,boolean mute,boolean solo) { channels[channel] = new float[]{volume,pan,mute?1:0,solo?1:0}; }
        @Implementation protected static void voice(boolean enabled,int program,int mode,int octave,boolean range,boolean snap,int response,int quality,boolean rhythm,boolean mix,float confidence,float gate) {}
        @Implementation protected static void monitor(boolean enabled) {}
        @Implementation protected static double[] state() { return current.clone(); }
        @Implementation protected static void record(boolean arm) {}
        @Implementation protected static double[] drain() { return new double[0]; }
    }
}
