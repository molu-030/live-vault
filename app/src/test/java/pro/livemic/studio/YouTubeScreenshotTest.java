package pro.livemic.studio;

import android.app.AlertDialog;
import android.graphics.Insets;
import android.net.Uri;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import java.io.File;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowDialog;
import static org.junit.Assert.*;

/** Native Skia chrome screenshots. The labeled player is an OFFLINE fixture, never a real video. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35, instrumentedPackages={"pro.livemic.studio"}, shadows={StudioScreenshotTest.Audio.class,StudioScreenshotTest.Composer.class,StudioScreenshotTest.Harmony.class,YouTubePlayerTest.OfflineWeb.class})
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class YouTubeScreenshotTest {
    static TextView offlinePlayer(MainActivity a) {
        WebView web=a.youtube.web;ViewGroup parent=(ViewGroup)web.getParent();assertNotNull(parent);int index=parent.indexOfChild(web);
        ViewGroup.LayoutParams size=web.getLayoutParams();parent.removeView(web);
        TextView fixture=a.text("OFFLINE PLAYER FIXTURE\nYouTube video is not loaded\n영상 재생 검증 아님",14,MainActivity.TEXT);
        fixture.setGravity(Gravity.CENTER);fixture.setBackgroundColor(0xff151515);fixture.setContentDescription("Offline YouTube player fixture; no network playback");
        parent.addView(fixture,index,size);return fixture;
    }
    static void consentCapture(MainActivity a,View content,int width,int height,File output)throws Exception {
        content.measure(View.MeasureSpec.makeMeasureSpec(width-32,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height-72,View.MeasureSpec.AT_MOST));
        int panelHeight=content.getMeasuredHeight();content.layout(0,0,width-32,panelHeight);
        android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(width,height,android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas=new android.graphics.Canvas(bitmap);a.root.draw(canvas);canvas.drawColor(0xad000000);canvas.save();canvas.translate(16,(height-panelHeight)/2f);content.draw(canvas);canvas.restore();
        try(java.io.FileOutputStream stream=new java.io.FileOutputStream(output)){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,stream);}bitmap.recycle();
    }
    static WebResourceRequest request() { return new WebResourceRequest() {
        public Uri getUrl(){return Uri.parse("https://www.youtube.com/iframe_api");}
        public boolean isForMainFrame(){return true;}public boolean isRedirect(){return false;}public boolean hasGesture(){return false;}
        public String getMethod(){return "GET";}public java.util.Map<String,String> getRequestHeaders(){return java.util.Collections.emptyMap();}
    }; }
    @Test public void youtubeSourceConsentErrorAndLiveOfflineFixtureInBothLanguages() throws Exception {
        File out=new File(System.getProperty("user.dir"),"build/studio-screenshots");out.mkdirs();
        try {
            for(boolean english:new boolean[]{false,true}) for(int width:new int[]{412,360}) {
                int height=width==412?893:800;float scale=width==412?1f:1.3f;
                RuntimeEnvironment.setQualifiers("w"+width+"dp-h"+height+"dp-mdpi");RuntimeEnvironment.setFontScale(scale);
                AppLanguage.set(RuntimeEnvironment.getApplication(),english);
                RuntimeEnvironment.getApplication().getSharedPreferences("MainActivity",0).edit().clear().commit();
                AudioService.active=false;AudioService.playbackActive=false;AudioService.youtubeSelected=false;RecordingManager.active=false;
                StudioScreenshotTest.Harmony.current=new double[]{0,0,0,0,-1,-1,0,0,-1,-1,-1,0,0,-1};
                MainActivity a=Robolectric.buildActivity(MainActivity.class).setup().get();
                try {
                    a.getPreferences(0).edit().clear().commit();
                    a.handler.removeCallbacks(a.tick);a.composer.worker.submit(()->{}).get();org.robolectric.shadows.ShadowLooper.idleMainLooper();a.handler.removeCallbacks(a.tick);
                    a.root.dispatchApplyWindowInsets(new WindowInsets.Builder().setInsets(WindowInsets.Type.systemBars(),Insets.of(0,36,0,24)).build());
                    String prefix="youtube-"+(english?"en":"ko")+"-"+width+(scale==1?"-default":"-large");
                    a.sourceTab=2;a.chooseTab(1);StudioScreenshotTest.capture(a,width,height,new File(out,prefix+"-source.png"),false);
                    assertNull(a.youtube.web);a.selectYouTube("M7lc1UVf-VE");AlertDialog consent=(AlertDialog)ShadowDialog.getLatestDialog();assertTrue(consent.isShowing());
                    consentCapture(a,consent.getWindow().getDecorView(),width,height,new File(out,prefix+"-consent.png"));
                    consent.getButton(AlertDialog.BUTTON_POSITIVE).performClick();org.robolectric.shadows.ShadowLooper.idleMainLooper();a.handler.removeCallbacks(a.tick);assertTrue(a.youtube.selected);assertEquals(0,a.tab);
                    java.util.List<String> pages=YouTubePlayerTest.OfflineWeb.pages;
                    assertFalse(pages.isEmpty());
                    java.nio.file.Files.write(new File(out,"v38-youtube-player.html").toPath(),pages.get(pages.size()-1).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    TextView fixture=offlinePlayer(a);StudioScreenshotTest.capture(a,width,height,new File(out,prefix+"-live-OFFLINE-fixture.png"),false);
                    android.graphics.Rect playerBounds=StudioScreenshotTest.bounds(a,fixture);
                    assertTrue("Player meets visible minimum: "+playerBounds,playerBounds.width()>=200&&playerBounds.height()>=200);
                    assertTrue("Player above footer: "+playerBounds,playerBounds.bottom<=StudioScreenshotTest.bounds(a,a.pageScroll).bottom);
                    assertTrue("Player below system top: "+playerBounds,playerBounds.top>=36);
                    assertTrue("Live FX details remain above footer",StudioScreenshotTest.bounds(a,a.liveScreen.detailsButton).bottom<=StudioScreenshotTest.bounds(a,a.pageScroll).bottom);
                    float[] stats=new float[22];stats[0]=440;stats[2]=69;stats[3]=.1f;stats[4]=.01f;stats[8]=12;stats[10]=1;
                    StudioScreenshotTest.Audio.supplied=stats;AudioService.active=true;RecordingManager.active=true;RecordingManager.micOnly=true;
                    a.tick.run();a.handler.removeCallbacks(a.tick);
                    assertEquals("A4",a.liveScreen.display.note);assertEquals(440,a.liveScreen.display.frequency,.01);assertTrue(a.liveScreen.display.recordClock.contains("00:12"));
                    StudioScreenshotTest.capture(a,width,height,new File(out,prefix+"-recording-pitch-OFFLINE-fixture.png"),false);
                    assertTrue("Recording player clears footer",StudioScreenshotTest.bounds(a,fixture).bottom<=StudioScreenshotTest.bounds(a,a.pageScroll).bottom);
                    assertTrue("Recording FX details remain above footer",StudioScreenshotTest.bounds(a,a.liveScreen.detailsButton).bottom<=StudioScreenshotTest.bounds(a,a.pageScroll).bottom);
                    RecordingManager.active=false;RecordingManager.micOnly=false;AudioService.active=false;StudioScreenshotTest.Audio.supplied=null;
                    a.sourceTab=2;a.chooseTab(1);
                    Shadows.shadowOf(a.youtube.web).getWebViewClient().onReceivedError(a.youtube.web,request(),null);
                    assertTrue(a.youtube.status.getText().toString().contains(english?"Could not load":"불러오지"));
                    offlinePlayer(a);StudioScreenshotTest.capture(a,width,height,new File(out,prefix+"-error-OFFLINE-fixture.png"),false);
                } finally { a.handler.removeCallbacks(a.tick);a.youtube.destroy();a.io.shutdown(); }
            }
        } finally { RuntimeEnvironment.setFontScale(1f);AppLanguage.set(RuntimeEnvironment.getApplication(),false);AudioService.youtubeSelected=false;RecordingManager.active=false;RecordingManager.micOnly=false;AudioService.active=false;StudioScreenshotTest.Audio.supplied=null; }
    }
}
