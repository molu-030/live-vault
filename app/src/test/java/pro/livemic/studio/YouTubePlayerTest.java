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
}
