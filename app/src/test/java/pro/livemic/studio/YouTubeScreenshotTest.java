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
        WebView web=a.youtube.web;ViewGroup parent=a.youtube.viewport;assertNotNull(parent);
        View prior=StudioScreenshotTest.findDescription(parent,"Offline YouTube player fixture; no network playback");if(prior!=null)parent.removeView(prior);
        ViewGroup.LayoutParams size=web.getLayoutParams();if(web.getParent()!=null)((ViewGroup)web.getParent()).removeView(web);
        TextView fixture=a.text("OFFLINE PLAYER FIXTURE\nYouTube video is not loaded\n영상 재생 검증 아님",14,MainActivity.TEXT);
        fixture.setGravity(Gravity.CENTER);fixture.setBackgroundColor(0xff151515);fixture.setContentDescription("Offline YouTube player fixture; no network playback");
        parent.addView(fixture,size);return fixture;
    }
    static void reveal(MainActivity a,View control) {
        android.graphics.Rect rect=new android.graphics.Rect(0,0,control.getWidth(),control.getHeight());
        a.page.offsetDescendantRectToMyCoords(control,rect);
        a.pageScroll.requestChildRectangleOnScreen(a.page,rect,true);
    }
    static void mainEffectsVisible(MainActivity a) {
        android.graphics.Rect viewport=StudioScreenshotTest.bounds(a,a.pageScroll);
        android.graphics.Rect footer=StudioScreenshotTest.bounds(a,a.recButton);
        Button chooser=StudioScreenshotTest.findButtonContaining(a.page,AppLanguage.text(a,"반주 선택", "Choose backing"));
        assertNotNull("Selected YouTube keeps a backing chooser",chooser);
        View[] controls={a.liveScreen.micButton,a.liveScreen.monitorToggle,a.liveScreen.inputButton,a.outputButton,
                a.liveScreen.micVolume,a.liveScreen.backingPlayButton,a.liveScreen.backingVolume,
                chooser,a.liveScreen.detailsButton,a.liveScreen.reverbVolume,a.liveScreen.echoVolume};
        for(View control:controls) {
            assertNotNull(control);assertTrue("Main control remains shown",control.isShown());
            reveal(a,control);
            android.graphics.Rect bounds=StudioScreenshotTest.bounds(a,control);
            assertTrue("Main touch target >=48: "+bounds,bounds.width()>=48&&bounds.height()>=48);
            assertTrue("Main control can be fully revealed: "+bounds+" in "+viewport,viewport.contains(bounds));
            assertEquals("Record footer stays fixed while revealing controls",footer,StudioScreenshotTest.bounds(a,a.recButton));
        }
        for(StudioKnob knob:new StudioKnob[]{a.liveScreen.reverbVolume,a.liveScreen.echoVolume}) {
            assertTrue("Main FX exposes a percent value",knob.getContentDescription().toString().contains("%"));
            assertNotNull("Main FX has an accessible range",knob.createAccessibilityNodeInfo().getRangeInfo());
        }
        android.graphics.Rect reverb=StudioScreenshotTest.bounds(a,a.liveScreen.reverbVolume);
        android.graphics.Rect echo=StudioScreenshotTest.bounds(a,a.liveScreen.echoVolume);
        assertEquals("Main knobs keep their shared row",reverb.top,echo.top);
        assertTrue("Main knob targets do not overlap",reverb.right<=echo.left);
        assertEquals("Normal I/O row remains intact",2,((ViewGroup)a.liveScreen.micButton.getParent()).getChildCount());
        assertEquals("Normal route row remains intact",2,((ViewGroup)a.liveScreen.inputButton.getParent()).getChildCount());
        assertTrue("LCD keeps its normal readable minimum",a.liveScreen.display.getHeight()>=a.dp(151));
        assertTrue("Recording footer clears system navigation",footer.bottom<=a.root.getHeight()-a.root.getPaddingBottom());
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
        File out=new File(System.getProperty("user.dir"),"build/outputs/studio-previews");out.mkdirs();
        try {
            for(boolean english:new boolean[]{false,true}) for(int width:new int[]{412,360,600}) for(float scale:new float[]{1f,1.3f}) {
                int height=width==412?893:width==600?960:800;
                RuntimeEnvironment.setQualifiers("w"+width+"dp-h"+height+"dp-mdpi");RuntimeEnvironment.setFontScale(scale);
                AppLanguage.set(RuntimeEnvironment.getApplication(),english);
                RuntimeEnvironment.getApplication().getSharedPreferences("MainActivity",0).edit().clear().commit();
                StudioScreenshotTest.Composer.current=new double[]{0,0,0,0,32,-1,-1,0,0,0};
                AudioService.active=false;AudioService.playbackActive=false;AudioService.youtubeSelected=false;RecordingManager.active=false;
                StudioScreenshotTest.Harmony.current=new double[]{0,0,0,0,-1,-1,0,0,-1,-1,-1,0,0,-1};
                MainActivity a=Robolectric.buildActivity(MainActivity.class).setup().get();
                try {
                    a.getPreferences(0).edit().clear().commit();
                    a.handler.removeCallbacks(a.tick);a.composer.worker.submit(()->{}).get();org.robolectric.shadows.ShadowLooper.idleMainLooper();a.handler.removeCallbacks(a.tick);
                    a.root.dispatchApplyWindowInsets(new WindowInsets.Builder().setInsets(WindowInsets.Type.systemBars(),Insets.of(0,36,0,24)).build());
                    String prefix="v386-youtube-layout-"+(english?"en":"ko")+"-"+width+(scale==1?"-default":"-large");
                    StudioScreenshotTest.capture(a,width,height,new File(out,prefix+"-native-live.png"),false);
                    int normalPanels=a.page.getChildCount(),normalMicHeight=a.liveScreen.micButton.getHeight(),normalFxHeight=a.liveScreen.echoVolume.getHeight();
                    assertTrue("Native source uses the shared readable LCD minimum",a.liveScreen.display.getHeight()>=a.dp(151));
                    a.sourceTab=2;a.chooseTab(1);StudioScreenshotTest.capture(a,width,height,new File(out,prefix+"-source.png"),false);
                    assertNull(a.youtube.web);a.selectYouTube("M7lc1UVf-VE");AlertDialog consent=(AlertDialog)ShadowDialog.getLatestDialog();assertTrue(consent.isShowing());
                    consentCapture(a,consent.getWindow().getDecorView(),width,height,new File(out,prefix+"-consent.png"));
                    consent.getButton(AlertDialog.BUTTON_POSITIVE).performClick();org.robolectric.shadows.ShadowLooper.idleMainLooper();a.handler.removeCallbacks(a.tick);assertTrue(a.youtube.selected);assertEquals(1,a.tab);assertEquals(2,a.sourceTab);
                    TextView onlineFixture=offlinePlayer(a);StudioScreenshotTest.capture(a,width,height,new File(out,prefix+"-online-loaded-OFFLINE-fixture.png"),false);
                    reveal(a,onlineFixture);assertTrue("Loaded Online video can be fully revealed without navigating",StudioScreenshotTest.bounds(a,a.pageScroll).contains(StudioScreenshotTest.bounds(a,onlineFixture)));
                    Button toLive=StudioScreenshotTest.findButtonContaining(a.page,a.tr("라이브로", "To Live"));assertNotNull(toLive);toLive.performClick();assertEquals(0,a.tab);
                    java.util.List<String> pages=YouTubePlayerTest.OfflineWeb.pages;
                    assertFalse(pages.isEmpty());
                    java.nio.file.Files.write(new File(out,"v386-youtube-embed.html").toPath(),pages.get(pages.size()-1).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    TextView fixture=offlinePlayer(a);StudioScreenshotTest.capture(a,width,height,new File(out,prefix+"-live-OFFLINE-fixture.png"),false);
                    assertEquals("Selecting video keeps the same Live card hierarchy",normalPanels,a.page.getChildCount());
                    assertEquals("Selecting video retains full microphone control height",normalMicHeight,a.liveScreen.micButton.getHeight());
                    assertEquals("Selecting video retains full FX knob height",normalFxHeight,a.liveScreen.echoVolume.getHeight());
                    android.graphics.Rect playerBounds=StudioScreenshotTest.bounds(a,fixture);
                    assertTrue("Player meets visible minimum: "+playerBounds,playerBounds.width()>=200&&playerBounds.height()>=200);
                    assertEquals("Adaptive viewport preserves player aspect with native minimum",Math.max(a.dp(200),(int)Math.ceil(playerBounds.width()*9d/16d)),playerBounds.height());
                    reveal(a,fixture);playerBounds=StudioScreenshotTest.bounds(a,fixture);
                    assertTrue("Entire video can be revealed above footer: "+playerBounds,StudioScreenshotTest.bounds(a,a.pageScroll).contains(playerBounds));
                    assertTrue("Player below system top: "+playerBounds,playerBounds.top>=36);
                    mainEffectsVisible(a);
                    assertTrue("Video adds body content without crushing the Live controls",a.page.getHeight()>a.pageScroll.getHeight());
                    StudioScreenshotTest.capture(a,width,height,new File(out,prefix+"-fx-OFFLINE-fixture.png"),false);
                    float[] stats=new float[22];stats[0]=440;stats[2]=69;stats[3]=.1f;stats[4]=.01f;stats[8]=12;stats[10]=1;
                    StudioScreenshotTest.Audio.supplied=stats;AudioService.active=true;RecordingManager.active=true;RecordingManager.micOnly=true;
                    a.tick.run();a.handler.removeCallbacks(a.tick);
                    assertEquals("A4",a.liveScreen.display.note);assertEquals(440,a.liveScreen.display.frequency,.01);assertTrue(a.liveScreen.display.recordClock.contains("00:12"));
                    StudioScreenshotTest.capture(a,width,height,new File(out,prefix+"-recording-pitch-OFFLINE-fixture.png"),false);
                    reveal(a,fixture);assertTrue("Recording player can be fully revealed above footer",StudioScreenshotTest.bounds(a,a.pageScroll).contains(StudioScreenshotTest.bounds(a,fixture)));
                    mainEffectsVisible(a);
                    RecordingManager.active=false;RecordingManager.micOnly=false;AudioService.active=false;StudioScreenshotTest.Audio.supplied=null;
                    a.sourceTab=2;a.chooseTab(1);
                    Shadows.shadowOf(a.youtube.web).getWebViewClient().onReceivedError(a.youtube.web,request(),null);
                    assertTrue(a.youtube.status.getText().toString().contains(english?"Could not load":"불러오지"));
                    offlinePlayer(a);StudioScreenshotTest.capture(a,width,height,new File(out,prefix+"-error-OFFLINE-fixture.png"),false);
                    a.youtube.status.setText((english?"Could not load YouTube. Check the connection and select another video. ":"YouTube를 불러오지 못했습니다. 연결을 확인하고 다른 동영상을 선택하세요. ").repeat(4));
                    StudioScreenshotTest.capture(a,width,height,new File(out,prefix+"-long-error-OFFLINE-fixture.png"),false);
                    Button returnLive=StudioScreenshotTest.findButtonContaining(a.page,a.tr("라이브로", "To Live"));assertNotNull(returnLive);returnLive.performClick();
                    a.youtube.status.setText((english?"Could not load YouTube. Check the connection and select another video. ":"YouTube를 불러오지 못했습니다. 연결을 확인하고 다른 동영상을 선택하세요. ").repeat(4));
                    offlinePlayer(a);StudioScreenshotTest.capture(a,width,height,new File(out,prefix+"-long-error-live-OFFLINE-fixture.png"),false);
                    mainEffectsVisible(a);
                } finally { a.handler.removeCallbacks(a.tick);a.youtube.destroy();a.io.shutdown(); }
            }
        } finally { RuntimeEnvironment.setFontScale(1f);AppLanguage.set(RuntimeEnvironment.getApplication(),false);AudioService.youtubeSelected=false;RecordingManager.active=false;RecordingManager.micOnly=false;AudioService.active=false;StudioScreenshotTest.Audio.supplied=null; }
    }
    @Test public void restoredBackingEntryPointsAndSourcesNativePreview() throws Exception {
        File out=new File(System.getProperty("user.dir"),"build/outputs/studio-previews");out.mkdirs();
        try {
            for(boolean english:new boolean[]{true,false}) {
                int width=english?412:360,height=english?893:800;
                RuntimeEnvironment.setQualifiers("w"+width+"dp-h"+height+"dp-mdpi");RuntimeEnvironment.setFontScale(english?1f:1.3f);
                AppLanguage.set(RuntimeEnvironment.getApplication(),english);
                RuntimeEnvironment.getApplication().getSharedPreferences("MainActivity",0).edit().clear().commit();
                StudioScreenshotTest.Composer.current=new double[]{0,0,0,0,32,-1,-1,0,0,0};
                AudioService.active=false;AudioService.playbackActive=false;AudioService.youtubeSelected=false;RecordingManager.active=false;
                StudioScreenshotTest.Harmony.current=new double[]{0,0,0,0,-1,-1,0,0,-1,-1,-1,0,0,-1};
                MainActivity a=Robolectric.buildActivity(MainActivity.class).setup().get();
                try {
                    a.handler.removeCallbacks(a.tick);a.composer.worker.submit(()->{}).get();org.robolectric.shadows.ShadowLooper.idleMainLooper();a.handler.removeCallbacks(a.tick);
                    a.root.dispatchApplyWindowInsets(new WindowInsets.Builder().setInsets(WindowInsets.Type.systemBars(),Insets.of(0,36,0,24)).build());
                    String prefix="v386-backing-"+(english?"en-412-default":"ko-360-large");
                    StudioScreenshotTest.capture(a,width,height,new File(out,prefix+"-live.png"),false);
                    Button chooser=StudioScreenshotTest.findButtonContaining(a.page,english?"Choose backing":"반주 선택");assertNotNull(chooser);
                    assertTrue(StudioScreenshotTest.bounds(a,a.pageScroll).contains(StudioScreenshotTest.bounds(a,chooser)));
                    assertEquals(a.dp(48),chooser.getHeight());
                    a.drawer();StudioScreenshotTest.capture(a,width,height,new File(out,prefix+"-drawer.png"),true);
                    View row=StudioScreenshotTest.findDescription(a.drawerPopup.getContentView(),english?"Backing,":"반주,");assertNotNull(row);row.performClick();assertEquals(1,a.tab);
                    if(english) {
                        String[] labels={"My files","Composer","Online"};
                        for(String label:labels) {
                            Button source=StudioScreenshotTest.findButtonContaining(a.page,label);assertNotNull(source);source.performClick();
                            StudioScreenshotTest.capture(a,width,height,new File(out,prefix+"-"+label.replace(' ','-')+".png"),false);
                        }
                    }
                } finally {a.handler.removeCallbacks(a.tick);a.youtube.destroy();a.io.shutdown();}
            }
        } finally {RuntimeEnvironment.setFontScale(1f);AppLanguage.set(RuntimeEnvironment.getApplication(),false);}
    }

}
