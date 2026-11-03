package pro.livemic.studio;

import android.webkit.WebView;
import android.webkit.ValueCallback;
import android.widget.LinearLayout;
import java.util.ArrayList;
import java.util.List;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowWebView;
import static org.junit.Assert.*;

/** No YouTube network or playback occurs: the WebView boundary records HTML and commands. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=28, instrumentedPackages={"pro.livemic.studio"}, shadows={StudioScreenshotTest.Audio.class, StudioScreenshotTest.Composer.class, StudioScreenshotTest.Harmony.class, YouTubePlayerTest.OfflineWeb.class})
@LooperMode(LooperMode.Mode.PAUSED)
public class YouTubePlayerTest {
    @Implements(WebView.class)
    public static class OfflineWeb extends ShadowWebView {
        @RealObject WebView realWeb;
        // Chromium is absent offline. Give its boundary normal View geometry; the
        // actual attached parent tree still determines all scrolling and clipping.
        @Implementation protected boolean setFrame(int left,int top,int right,int bottom) {
            return org.robolectric.shadow.api.Shadow.directlyOn(realWeb,android.view.View.class,"setFrame",
                    org.robolectric.util.ReflectionHelpers.ClassParameter.from(int.class,left),
                    org.robolectric.util.ReflectionHelpers.ClassParameter.from(int.class,top),
                    org.robolectric.util.ReflectionHelpers.ClassParameter.from(int.class,right),
                    org.robolectric.util.ReflectionHelpers.ClassParameter.from(int.class,bottom));
        }
        static final List<String> pages = new ArrayList<>(), scripts = new ArrayList<>();
        @Implementation protected void loadDataWithBaseURL(String base, String data, String mime, String encoding, String history) { pages.add(data); }
        @Implementation protected void evaluateJavascript(String script, ValueCallback<String> callback) { scripts.add(script); }
    }
    MainActivity a;
    @Before public void setup() {
        RuntimeEnvironment.getApplication().getSharedPreferences("MainActivity",0).edit().clear().commit();
        OfflineWeb.pages.clear(); OfflineWeb.scripts.clear();
        AudioService.active=false; AudioService.playbackActive=false; AudioService.youtubeSelected=false; RecordingManager.active=false;
        StudioScreenshotTest.Harmony.current=new double[]{0,0,0,0,-1,-1,0,0,-1,-1,-1,0,0,-1};
        a=Robolectric.buildActivity(MainActivity.class).setup().get(); a.handler.removeCallbacks(a.tick);
    }
    @After public void cleanup() { a.handler.removeCallbacks(a.tick); a.io.shutdown(); AudioService.youtubeSelected=false; RecordingManager.active=false; RecordingManager.micOnly=false; AudioService.active=false; StudioScreenshotTest.Audio.supplied=null; }
    @Test public void startupNeverLoadsYouTube() { assertTrue(OfflineWeb.pages.isEmpty()); }
    @Test public void rejectingConsentOrInvalidInputNeverCreatesPlayerOrLoadsNetwork() {
        a.selectYouTube("not a video"); assertNull(a.youtube.web); assertTrue(OfflineWeb.pages.isEmpty());
        a.selectYouTube("dQw4w9WgXcQ");
        android.app.AlertDialog consent=(android.app.AlertDialog)org.robolectric.shadows.ShadowDialog.getLatestDialog();
        assertNotNull(consent);assertTrue(consent.isShowing());assertNull(a.youtube.web);
        consent.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).performClick();
        assertFalse(a.youtube.selected);assertFalse(a.getPreferences(0).getBoolean("youtubeConsent",false));assertTrue(OfflineWeb.pages.isEmpty());
    }
    @Test public void acceptedConsentLoadsVisiblePlayerAndTabOrBackgroundPausesIt() {
        a.selectYouTube("dQw4w9WgXcQ");
        android.app.AlertDialog consent=(android.app.AlertDialog)org.robolectric.shadows.ShadowDialog.getLatestDialog();
        consent.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();
        org.robolectric.shadows.ShadowLooper.idleMainLooper();a.handler.removeCallbacks(a.tick);
        assertTrue(a.youtube.selected);assertEquals(0,a.tab);assertNotNull(a.youtube.web.getParent());assertEquals(1,OfflineWeb.pages.size());
        float mic=a.values[0];StudioScreenshotTest.Audio.changes.clear();OfflineWeb.scripts.clear();a.set(1,.42f);
        assertEquals("volume(42)",OfflineWeb.scripts.get(0));assertEquals(mic,a.values[0],0);
        for(float[] change:StudioScreenshotTest.Audio.changes)assertEquals(1,(int)change[0]);
        OfflineWeb.scripts.clear();a.chooseTab(4);assertTrue(OfflineWeb.scripts.contains("pause()"));
        a.chooseTab(0);assertEquals(1,OfflineWeb.pages.size());
        OfflineWeb.scripts.clear();a.onPause();assertTrue(OfflineWeb.scripts.contains("pause()"));
    }
    @Test public void consentAcceptanceRechecksRecordingStartedWhileDialogWasOpen() {
        a.selectYouTube("dQw4w9WgXcQ");
        android.app.AlertDialog consent=(android.app.AlertDialog)org.robolectric.shadows.ShadowDialog.getLatestDialog();
        assertTrue(consent.isShowing());RecordingManager.active=true;
        consent.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();
        org.robolectric.shadows.ShadowLooper.idleMainLooper();a.handler.removeCallbacks(a.tick);
        assertFalse(a.youtube.selected);assertNull(a.youtube.web);assertTrue(OfflineWeb.pages.isEmpty());assertTrue(RecordingManager.active);
    }
    @Test public void focusLossPausesAndFocusReturnNeverStartsPlayback() {
        a.getPreferences(0).edit().putBoolean("youtubeConsent",true).commit();a.selectYouTube("dQw4w9WgXcQ");
        assertTrue(a.youtube.selected);OfflineWeb.scripts.clear();
        a.onWindowFocusChanged(false);assertTrue(OfflineWeb.scripts.contains("pause()"));
        OfflineWeb.scripts.clear();a.onWindowFocusChanged(true);
        for(String script:OfflineWeb.scripts)assertFalse(script.contains("playVideo"));
        assertEquals(1,OfflineWeb.pages.size());
    }
    @Test public void mainReverbAndEchoStayAdjustableDuringYouTubeMicrophoneOnlyRecording() {
        a.getPreferences(0).edit().putBoolean("youtubeConsent",true).commit();a.selectYouTube("dQw4w9WgXcQ");
        assertTrue(a.youtube.selected);AudioService.active=true;RecordingManager.active=true;RecordingManager.micOnly=true;
        float mic=a.values[0],backing=a.values[1],monitor=a.values[11],local=a.values[13];
        android.media.AudioManager manager=(android.media.AudioManager)a.getSystemService(android.content.Context.AUDIO_SERVICE);
        int music=manager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC),voice=manager.getStreamVolume(android.media.AudioManager.STREAM_VOICE_CALL);
        StudioKnob[] sliders={a.liveScreen.reverbVolume,a.liveScreen.echoVolume};int[] keys={2,4};
        assertEquals("All four main gains remain available",4,LiveConsoleTest.countSliders(a.page));
        for(int i=0;i<keys.length;i++) {
            assertNotNull(sliders[i]);assertTrue(sliders[i].isEnabled());
            StudioScreenshotTest.Audio.changes.clear();OfflineWeb.scripts.clear();
            LiveConsoleTest.adjust(sliders[i],35);assertFalse(StudioScreenshotTest.Audio.changes.isEmpty());
            for(float[] change:StudioScreenshotTest.Audio.changes)assertEquals(keys[i],(int)change[0]);
            assertEquals(a.values[keys[i]],a.getPreferences(0).getFloat("p"+keys[i],-1),.001);
            assertTrue(sliders[i].getContentDescription().toString().contains(Math.round(a.values[keys[i]]*100)+"%"));
            assertTrue("FX amount never changes YouTube gain or transport",OfflineWeb.scripts.isEmpty());
            assertTrue(RecordingManager.active);assertTrue(RecordingManager.micOnly);assertTrue(AudioService.active);assertTrue(a.youtube.selected);
        }
        StudioScreenshotTest.Audio.changes.clear();a.liveScreen.sync();assertTrue(StudioScreenshotTest.Audio.changes.isEmpty());
        assertEquals(mic,a.values[0],0);assertEquals(backing,a.values[1],0);assertEquals(monitor,a.values[11],0);assertEquals(local,a.values[13],0);
        assertEquals(music,manager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC));assertEquals(voice,manager.getStreamVolume(android.media.AudioManager.STREAM_VOICE_CALL));
        float reverb=a.values[2],echo=a.values[4];a.chooseTab(4);a.chooseTab(0);
        assertEquals(reverb,LiveConsoleTest.knobValue(a.liveScreen.reverbVolume),.001);assertEquals(echo,LiveConsoleTest.knobValue(a.liveScreen.echoVolume),.001);
        assertTrue(RecordingManager.active);assertTrue(RecordingManager.micOnly);
    }
    @Test public void youtubeRecordingUsesMicOnlyAndNativeSourcesKeepNormalMixAndSync() {
        StudioScreenshotTest.Audio.supplied=new float[22];StudioScreenshotTest.Audio.supplied[10]=1;
        RecordingManager.syncMs=85;
        for(boolean youtube:new boolean[]{false,true,false}) {
            RecordingManager.active=false;AudioService.youtubeSelected=youtube;
            StudioScreenshotTest.Audio.micOnlyCalls.clear();StudioScreenshotTest.Audio.syncCalls.clear();
            assertTrue(RecordingManager.start(a,78));
            assertEquals(java.util.Collections.singletonList(youtube),StudioScreenshotTest.Audio.micOnlyCalls);
            assertEquals(java.util.Collections.singletonList(youtube?0:85),StudioScreenshotTest.Audio.syncCalls);
        }
    }
    @Test public void frozenMicrophoneOnlyRequestSurvivesPlayerDeselectionBeforeRecordingStarts() {
        StudioScreenshotTest.Audio.supplied=new float[22];StudioScreenshotTest.Audio.supplied[10]=1;
        AudioService.youtubeSelected=false;StudioScreenshotTest.Audio.micOnlyCalls.clear();
        assertTrue(RecordingManager.start(a,78,true));assertTrue(RecordingManager.micOnly);
        assertEquals(java.util.Collections.singletonList(true),StudioScreenshotTest.Audio.micOnlyCalls);
        assertFalse("Native backing cannot enter a microphone-only take",YouTubePlayer.beforeNativePlayback());
    }
    @Test public void acceptsOnlyVideoIdsAndSupportedHttpsVideoLinks() {
        String id="dQw4w9WgXcQ";
        for(String value:new String[]{id," https://youtu.be/"+id+"?t=4 ","https://www.youtube.com/watch?v="+id+"&t=4","https://m.youtube.com/shorts/"+id,"https://youtube.com/embed/"+id,"https://youtube.com/live/"+id}) assertEquals(value,id,YouTubePlayer.videoId(value));
        for(String value:new String[]{"", "cats singing", "http://youtu.be/"+id,"javascript:alert(1)","file:///youtube.com/watch?v="+id,"https://youtube.com.evil.test/watch?v="+id,"https://youtube.com@evil.test/watch?v="+id,"https://evil.test@youtube.com/watch?v="+id,"https://youtube.com:443/watch?v="+id,"https://youtu.be/short","https://youtu.be/"+id+"/extra","https://youtube.com/results?search_query=karaoke","https://youtube.com/watch?v=<script>"}) assertNull(value,YouTubePlayer.videoId(value));
        assertNull(YouTubePlayer.videoId(null)); assertTrue(OfflineWeb.pages.isEmpty());
    }
    @Test public void selectedVideoRequiresUserGestureAndReattachmentNeverStartsPlayback() {
        YouTubePlayer player=new YouTubePlayer(a);player.selected=true;player.id="dQw4w9WgXcQ";
        LinearLayout slot=new LinearLayout(a); a.page.addView(slot);player.attach(slot);
        assertEquals(1,OfflineWeb.pages.size());
        assertTrue(OfflineWeb.pages.get(0).contains("autoplay:0"));
        String html=OfflineWeb.pages.get(0);
        assertTrue("Iframe CSS follows its measured native viewport",html.contains("height:100%"));
        assertFalse("No fixed iframe height can crop a wider native player",html.contains("height:220px"));
        assertTrue(player.web.getSettings().getMediaPlaybackRequiresUserGesture());
        OfflineWeb.scripts.clear();player.detach();assertTrue(OfflineWeb.scripts.contains("pause()"));
        player.attach(slot); assertEquals("Reuse paused player without loading another video",1,OfflineWeb.pages.size());
        for(String script:OfflineWeb.scripts) assertFalse(script.contains("playVideo"));
        a.activityVisible=false;player.checkVisibility();
        assertTrue("Hidden player has a pause or hidden command",OfflineWeb.scripts.contains("pause()") || OfflineWeb.scripts.contains("visible(false)"));
        for(String script:OfflineWeb.scripts) assertFalse(script.contains("playVideo"));
        player.destroy();
    }
    @Test public void backingVolumeReachesOfficialPlayerWithoutChangingMicrophoneOrSystemVolume() {
        YouTubePlayer player=new YouTubePlayer(a);player.selected=true;player.id="dQw4w9WgXcQ";player.attach(new LinearLayout(a));
        android.media.AudioManager manager=(android.media.AudioManager)a.getSystemService(android.content.Context.AUDIO_SERVICE);
        int music=manager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC),voice=manager.getStreamVolume(android.media.AudioManager.STREAM_VOICE_CALL);float mic=a.values[0];
        OfflineWeb.scripts.clear();player.volume(.37f);assertEquals("volume(37)",OfflineWeb.scripts.get(0));
        player.volume(0);assertEquals("volume(0)",OfflineWeb.scripts.get(1));
        assertEquals(mic,a.values[0],0);assertEquals(music,manager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC));assertEquals(voice,manager.getStreamVolume(android.media.AudioManager.STREAM_VOICE_CALL));player.destroy();
    }
    /** Window focus is supplied by the test; player clipping uses the real native view tree. */
    public static class FocusedActivity extends MainActivity {
        @Override public boolean hasWindowFocus() { return true; }
    }
    @Test public void scrollingPartOfActualVideoOutOfViewportDisallowsPlaybackUntilFullyRevealed() {
        RuntimeEnvironment.setQualifiers("w360dp-h720dp-mdpi");RuntimeEnvironment.setFontScale(1f);
        MainActivity focused=Robolectric.buildActivity(FocusedActivity.class).setup().get();
        try {
            focused.handler.removeCallbacks(focused.tick);focused.activityVisible=true;
            focused.getPreferences(0).edit().putBoolean("youtubeConsent",true).commit();
            focused.selectYouTube("dQw4w9WgXcQ");
            android.view.View decor=focused.getWindow().getDecorView();
            StudioScreenshotTest.redraw(decor);
            decor.measure(android.view.View.MeasureSpec.makeMeasureSpec(360,android.view.View.MeasureSpec.EXACTLY),
                    android.view.View.MeasureSpec.makeMeasureSpec(720,android.view.View.MeasureSpec.EXACTLY));
            decor.layout(0,0,360,720);
            YouTubeScreenshotTest.reveal(focused,focused.youtube.web);
            android.graphics.Rect rect=new android.graphics.Rect();
            assertTrue("Attached video has a visible native rect; root="+focused.root.getWidth()+"x"+focused.root.getHeight()+" video="+StudioScreenshotTest.bounds(focused,focused.youtube.web)+" viewport="+focused.youtube.viewport.getWidth()+"x"+focused.youtube.viewport.getHeight()+" minimum="+focused.youtube.viewport.minimum+" visibility="+focused.youtube.web.getVisibility(),focused.youtube.web.getGlobalVisibleRect(rect));
            assertEquals(focused.youtube.web.getWidth(),rect.width());assertEquals(focused.youtube.web.getHeight(),rect.height());
            focused.youtube.lastVisible=null;OfflineWeb.scripts.clear();focused.youtube.checkVisibility();
            assertTrue("Fully revealed native player can accept a user play gesture",OfflineWeb.scripts.contains("visible(true)"));
            assertTrue("Revealing the lower backing video requires body scrolling",focused.pageScroll.getScrollY()>0);
            OfflineWeb.scripts.clear();focused.pageScroll.scrollTo(0,0);
            assertTrue("Fixture actually clips part of the video",!focused.youtube.web.getGlobalVisibleRect(rect)||rect.height()<focused.youtube.web.getHeight());
            focused.youtube.checkVisibility();
            assertTrue("Partially clipped video is disallowed",OfflineWeb.scripts.contains("visible(false)"));
            OfflineWeb.scripts.clear();YouTubeScreenshotTest.reveal(focused,focused.youtube.web);
            focused.youtube.checkVisibility();
            assertTrue(OfflineWeb.scripts.contains("visible(true)"));
            for(String script:OfflineWeb.scripts)assertFalse("Revealing video never autoplays",script.contains("playVideo"));
        } finally {focused.handler.removeCallbacks(focused.tick);focused.youtube.destroy();focused.io.shutdown();}
    }
    @Test public void nativePlayerAdaptsToWidePortraitAndBelowMinimumWindows() {
        YouTubePlayer player=new YouTubePlayer(a);player.selected=true;player.id="dQw4w9WgXcQ";
        LinearLayout slot=new LinearLayout(a);player.attach(slot);
        for(int width:new int[]{160,200,312,360,412,600}) {
            int pixels=a.dp(width);
            slot.measure(android.view.View.MeasureSpec.makeMeasureSpec(pixels,android.view.View.MeasureSpec.EXACTLY),
                    android.view.View.MeasureSpec.makeMeasureSpec(0,android.view.View.MeasureSpec.UNSPECIFIED));
            slot.layout(0,0,pixels,slot.getMeasuredHeight());
            assertEquals(pixels,player.viewport.getWidth());
            assertEquals(Math.max(a.dp(200),(int)Math.ceil(pixels*9d/16d)),player.viewport.getHeight());
            assertEquals("WebView has the exact native viewport width",player.viewport.getWidth(),player.web.getMeasuredWidth());
            assertEquals("WebView has the exact native viewport height",player.viewport.getHeight(),player.web.getMeasuredHeight());
            assertEquals("Below minimum size uses a native fallback",width<200?android.view.View.INVISIBLE:android.view.View.VISIBLE,player.web.getVisibility());
        }
        player.destroy();
    }

}
