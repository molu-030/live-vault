package pro.livemic.studio;

import android.app.Dialog;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.HashSet;
import java.util.Arrays;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.*;
import static org.junit.Assert.*;

/** Exercises measured app windows, rather than assuming that a tablet device is always expanded. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,instrumentedPackages={"pro.livemic.studio"},shadows={StudioScreenshotTest.Audio.class,StudioScreenshotTest.Composer.class,StudioScreenshotTest.Harmony.class,YouTubePlayerTest.OfflineWeb.class})
@LooperMode(LooperMode.Mode.PAUSED)
public class TabletLayoutTest {
    @Before public void reset() throws Exception { new StudioScreenshotTest().resetComposer(); }
    @After public void restore() { RuntimeEnvironment.setFontScale(1); AppLanguage.set(RuntimeEnvironment.getApplication(),false); AudioService.active=false; AudioService.playbackActive=false; RecordingManager.active=false; AudioService.youtubeSelected=false; }

    static MainActivity open(int width,int height,float scale,boolean english) throws Exception {
        RuntimeEnvironment.setQualifiers("w"+width+"dp-h"+height+"dp-mdpi");
        RuntimeEnvironment.setFontScale(scale);
        AppLanguage.set(RuntimeEnvironment.getApplication(),english);
        displaySize(width,height);
        MainActivity a=Robolectric.buildActivity(MainActivity.class).setup().get();
        a.handler.removeCallbacks(a.tick);
        a.composer.worker.submit(()->{}).get(); a.harmony.worker.submit(()->{}).get();
        ShadowLooper.idleMainLooper(); a.handler.removeCallbacks(a.tick);
        a.composer.ready=true; a.composer.project=new ComposerProject(); a.composer.selectedPart=0;
        // Explicit test data: no library download, microphone or transport is started.
        if(english) {
            a.composer.project.name="New backing";
            String[] names={"Guitar","Bass","Drums","Keyboard"};
            for(int i=0;i<4;i++)a.composer.part().tracks.get(i).name=names[i];
        }
        a.selectedFile=english?"Local rehearsal.wav":"로컬 연습.wav"; a.sourceTab=1;
        a.chooseTab(0);
        if(width>=840 && height>=600 && width>height) {
            // Representative One UI status/taskbar space, not a claim of measured device insets.
            a.root.dispatchApplyWindowInsets(new android.view.WindowInsets.Builder()
                    .setInsets(android.view.WindowInsets.Type.systemBars(),android.graphics.Insets.of(0,24,0,48)).build());
        }
        measure(a,width,height); return a;
    }
    static void displaySize(int width,int height) {
        android.hardware.display.DisplayManager manager=(android.hardware.display.DisplayManager)RuntimeEnvironment.getApplication().getSystemService(android.content.Context.DISPLAY_SERVICE);
        ShadowDisplay display=Shadows.shadowOf(manager.getDisplay(android.view.Display.DEFAULT_DISPLAY));
        display.setWidth(width);display.setHeight(height);display.setRealWidth(width);display.setRealHeight(height);
    }
    static void resize(MainActivity a,int w,int h) {
        RuntimeEnvironment.setQualifiers("w"+w+"dp-h"+h+"dp-mdpi");displaySize(w,h);
        a.onConfigurationChanged(new android.content.res.Configuration(a.getResources().getConfiguration()));
        measure(a,w,h);
    }
    static void measure(MainActivity a,int w,int h) {
        for(int pass=0;pass<3;pass++) {
            StudioScreenshotTest.redraw(a.root);
            a.root.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));
            a.root.layout(0,0,w,h);
            ShadowLooper.idleMainLooper(); a.handler.removeCallbacks(a.tick);
        }
    }
    static void close(MainActivity a) { a.handler.removeCallbacks(a.tick); a.io.shutdown(); }
    static ScrollView pane(MainActivity a,String name) {
        View view=a.root.findViewWithTag(name); assertNotNull(name,view);
        assertTrue(name+" must own its scroll",view instanceof ScrollView); return (ScrollView)view;
    }
    static Rect bounds(MainActivity a,View v) { return StudioScreenshotTest.bounds(a,v); }
    static void distinct(MainActivity a,View left,View right) {
        Rect l=bounds(a,left),r=bounds(a,right);
        assertTrue("Both panes must have useful width",l.width()>=280 && r.width()>=280);
        assertTrue("Panes must be side by side",l.right<=r.left);
        assertTrue("Pane heights must be positive",l.height()>0 && r.height()>0);
    }
    static void touchTarget(MainActivity a,View v) {
        assertNotNull(v); assertTrue("Positive 48dp control: "+v.getContentDescription(),v.getWidth()>=a.dp(48) && v.getHeight()>=a.dp(48));
    }
    static void fixedDock(MainActivity a,View control) {
        Rect dock=bounds(a,control);
        assertTrue("Dock below scrolling body",dock.top>=bounds(a,a.pageScroll).bottom);
        assertTrue("Dock within safe window",dock.bottom<=a.root.getHeight()-a.root.getPaddingBottom());
        a.pageScroll.scrollTo(0,100000); assertEquals("Outer scroll must not move dock",dock,bounds(a,control));
    }
    static TextView text(View view,String needle) {
        if(view instanceof TextView && ((TextView)view).getText().toString().contains(needle))return (TextView)view;
        if(view instanceof ViewGroup) { ViewGroup group=(ViewGroup)view; for(int i=0;i<group.getChildCount();i++){TextView found=text(group.getChildAt(i),needle);if(found!=null)return found;} }
        return null;
    }
    static String childHeights(ViewGroup parent) {
        StringBuilder sizes=new StringBuilder();
        for(int i=0;i<parent.getChildCount();i++) {
            View child=parent.getChildAt(i);
            if(i>0)sizes.append(",");
            sizes.append(child.getClass().getSimpleName()).append("=").append(child.getHeight());
        }
        return sizes.toString();
    }
    static void largeProject(MainActivity a,boolean english) {
        a.composer.project.resize(a.composer.part(),16);
        while(a.composer.part().tracks.size()<8) {
            int n=a.composer.part().tracks.size(); a.composer.project.addTrack(new TrackConfig((english?"Layer ":"추가 악기 ")+(n+1),40+n,false));
        }
    }

    @Test public void liveLocalBackingFitsExpandedWindowsAndKeepsRealMeterValues() throws Exception {
        for(int[] size:new int[][]{{1480,924},{1280,800},{960,600}})for(boolean english:new boolean[]{false,true}) {
            MainActivity a=open(size[0],size[1],english?1.3f:1,english);
            try {
                assertTrue(a.tabletLandscape()); assertEquals(View.VISIBLE,a.nav.getVisibility());
                View left=a.root.findViewWithTag("tablet-live-left");assertNotNull(left);
                ScrollView right=pane(a,"tablet-live-right"); distinct(a,left,right);
                assertFalse("Expanded live has no outer vertical scroll",a.pageScroll.canScrollVertically(1));
                for(View v:new View[]{a.liveScreen.inputButton,a.outputButton,a.liveScreen.micButton,a.liveScreen.monitorToggle,a.liveScreen.backingPlayButton,a.recButton})touchTarget(a,v);
                for(View v:new View[]{a.liveScreen.inputButton,a.outputButton,a.liveScreen.display,a.liveScreen.backingPlayButton,a.liveScreen.detailsButton}) {
                    Rect visible=new Rect(); assertTrue("Essential live control visible",v.getGlobalVisibleRect(visible));
                    assertEquals("Essential control is not clipped",v.getHeight(),visible.height());
                    assertTrue(bounds(a,v).bottom<=bounds(a,a.actionDock).top);
                }
                TextView gainName=(TextView)((ViewGroup)a.liveScreen.backingVolume.getParent()).getChildAt(0);
                assertEquals(english?"Backing":"반주",gainName.getText().toString());
                assertTrue("Backing gain label fits at configured font scale",gainName.getPaint().measureText(gainName.getText().toString())<=gainName.getWidth()-gainName.getPaddingLeft()-gainName.getPaddingRight());
                assertTrue(bounds(a,a.liveScreen.inputButton).right<=bounds(a,a.outputButton).left);
                assertTrue(bounds(a,a.liveScreen.reverbVolume).right<=bounds(a,a.liveScreen.echoVolume).left);
                float[] stats=new float[22];stats[0]=440;stats[1]=12;stats[2]=69;stats[3]=.25f;stats[4]=.125f;stats[21]=1;
                a.liveScreen.poll(stats,true);
                assertEquals(440,a.liveScreen.display.frequency,0);assertEquals(a.noteName(69),a.liveScreen.display.note);
                assertEquals(12,a.liveScreen.display.cents,0);
                assertEquals(LiveSessionDisplay.decibels(.25f),a.liveScreen.display.inputDb,.001);
                assertEquals(LiveSessionDisplay.decibels(.125f),a.liveScreen.display.outputDb,.001);
                fixedDock(a,a.recButton);
            } finally { close(a); }
        }
    }

    @Test public void composerHasTwoChordColumnsVisibleTracksAndIndependentLongPaneScrolling() throws Exception {
        for(int[] size:new int[][]{{1480,924},{1280,800},{960,600}}) {
            MainActivity a=open(size[0],size[1],1.3f,true);
            try {
                largeProject(a,true);a.chooseTab(7);measure(a,size[0],size[1]);
                ScrollView left=pane(a,"tablet-composer-left"),right=pane(a,"tablet-composer-right");distinct(a,left,right);
                View hero=a.page.getChildAt(0);
                View paneRow=(View)left.getParent();
                assertFalse("Composer outer body must fit "+size[0]+"x"+size[1]
                        +" fontScale="+a.getResources().getConfiguration().fontScale
                        +" pageHeight="+a.page.getHeight()+" viewportHeight="+a.pageScroll.getHeight()
                        +" pageViewport="+a.page.viewport+" heroHeight="+(hero==null?-1:hero.getHeight())
                        +" paneRowHeight="+paneRow.getHeight()+" leftHeight="+left.getHeight()
                        +" rightHeight="+right.getHeight()+" dockHeight="+a.actionDock.getHeight()
                        +" pageChildren="+childHeights(a.page),a.pageScroll.canScrollVertically(1));
                assertEquals(16,a.composerScreen.chordCards.size());
                Rect first=bounds(a,a.composerScreen.chordCards.get(0)),second=bounds(a,a.composerScreen.chordCards.get(1));
                assertEquals(first.top,second.top);assertTrue(first.right<=second.left);
                assertNotNull(text(right,"Tracks"));assertNotNull(text(right,"Guitar"));
                View guitar=text(right,"Guitar");Rect visible=new Rect();assertTrue("First track is visible immediately",guitar.getGlobalVisibleRect(visible));
                assertTrue(visible.height()>0);
                View previous=StudioScreenshotTest.findDescription(left,"Previous part"),next=StudioScreenshotTest.findDescription(left,"Next part");
                touchTarget(a,previous);touchTarget(a,next);assertFalse(previous.isEnabled());assertFalse(next.isEnabled());
                Rect play=bounds(a,a.composerScreen.play);fixedDock(a,a.composerScreen.play);
                left.scrollTo(0,100000);assertTrue("16 bars overflow their pane",left.getScrollY()>0);assertEquals(0,right.getScrollY());assertEquals(play,bounds(a,a.composerScreen.play));
                int scroll=left.getScrollY();right.scrollTo(0,100000);assertTrue("Eight tracks overflow their pane",right.getScrollY()>0);assertEquals(scroll,left.getScrollY());assertEquals(play,bounds(a,a.composerScreen.play));
                assertNotNull(text(right,"Song order"));
            } finally { close(a); }
        }
    }

    @Test public void voicePanesKeepPhraseAndTransportInFixedFooter() throws Exception {
        MainActivity a=open(1480,924,1.3f,true);
        try {
            a.chooseTab(8);measure(a,1480,924);
            ScrollView left=pane(a,"tablet-voice-left"),right=pane(a,"tablet-voice-right");distinct(a,left,right);
            assertNotNull(a.composerScreen.voicePower);assertNotNull(a.composerScreen.phrase);
            touchTarget(a,a.composerScreen.play);touchTarget(a,a.composerScreen.phrase);
            fixedDock(a,a.composerScreen.play);fixedDock(a,a.composerScreen.phrase);
            Rect phrase=bounds(a,a.composerScreen.phrase);left.scrollTo(0,100000);right.scrollTo(0,100000);
            assertEquals(phrase,bounds(a,a.composerScreen.phrase));
        } finally { close(a); }
    }

    @Test public void phoneKeepsCompactLayoutAtBothFontScalesAndLanguages() throws Exception {
        for(float scale:new float[]{1,1.3f})for(boolean english:new boolean[]{false,true}) {
            MainActivity a=open(412,893,scale,english);
            try {
                assertFalse(a.tabletLandscape());assertEquals(View.GONE,a.nav.getVisibility());assertNull(a.root.findViewWithTag("tablet-live-left"));
                touchTarget(a,a.recButton);fixedDock(a,a.recButton);
                for(int tab:new int[]{7,8}) { a.chooseTab(tab);measure(a,412,893);assertNull(a.root.findViewWithTag("tablet-composer-left"));fixedDock(a,a.composerScreen.play); }
            } finally { close(a); }
        }
    }

    @Test public void tabletInstrumentCategoriesKeepDrumsAndFavorites() throws Exception {
        MainActivity a=open(1480,924,1,true);
        try {
            a.chooseTab(7);InstrumentFavorites.save(a.getPreferences(0),new HashSet<>(Arrays.asList("0:0","128:0")));
            a.composerScreen.instrument(-1,false);Dialog picker=ShadowDialog.getLatestDialog();
            View root=picker.getWindow().getDecorView();assertNotNull(StudioScreenshotTest.findButtonContaining(root,"0 · Standard"));
            StudioScreenshotTest.findButtonContaining(root,"Category:").performClick();Dialog categories=ShadowDialog.getLatestDialog();
            assertNotNull(StudioScreenshotTest.findButtonContaining(categories.getWindow().getDecorView(),"Drum kits"));
            StudioScreenshotTest.findButtonContaining(categories.getWindow().getDecorView(),"Favorites").performClick();
            assertNotNull(StudioScreenshotTest.findDescription(root,"Remove favorite: 0 · Standard"));picker.dismiss();
            a.composerScreen.instrument(-1,true);Dialog voice=ShadowDialog.getLatestDialog();root=voice.getWindow().getDecorView();
            StudioScreenshotTest.findButtonContaining(root,"Category:").performClick();categories=ShadowDialog.getLatestDialog();
            assertNull(StudioScreenshotTest.findButtonContaining(categories.getWindow().getDecorView(),"Drum kits"));
            StudioScreenshotTest.findButtonContaining(categories.getWindow().getDecorView(),"Favorites").performClick();
            assertNull(StudioScreenshotTest.findButtonContaining(root,"0 · Standard"));assertNotNull(StudioScreenshotTest.findDescription(root,"Remove favorite:"));voice.dismiss();
        } finally { close(a); }
    }
    @Test public void rotationAndMultiwindowPreserveProjectNavigationAndAudioState() throws Exception {
        MainActivity a=open(1480,924,1,true);
        try {
            a.chooseTab(7);a.composer.project.duplicate(a.composer.part());a.composer.selectedPart=1;
            a.composer.part().label="Retained part";a.composer.part().tracks.get(0).volume=.37f;
            a.set(0,.82f);a.set(1,.63f);a.showTab();measure(a,1480,924);
            String project=a.composer.project.json().toString();float[] levels=a.values.clone();
            int visits=a.navigation.history.size();BackNavigation.Page previous=a.navigation.previous();
            AudioService.active=true;AudioService.playbackActive=true;RecordingManager.active=true;
            int mic=StudioScreenshotTest.Audio.microphoneStarts,playback=StudioScreenshotTest.Audio.playbackStarts;
            int stop=StudioScreenshotTest.Audio.stopCalls,suspend=StudioScreenshotTest.Audio.suspendCalls;
            StudioScreenshotTest.Composer.transportCalls.clear();StudioScreenshotTest.Audio.localPlayCalls.clear();
            for(int[] size:new int[][]{{924,1480},{700,924},{1280,800},{960,600},{1480,924}}) {
                resize(a,size[0],size[1]);
                boolean expanded=size[0]>=840 && size[0]>size[1] && Math.min(size[0],size[1])>=600;
                assertEquals(expanded,a.tabletLandscape());assertEquals(expanded?View.VISIBLE:View.GONE,a.nav.getVisibility());
                assertEquals(7,a.tab);assertEquals(1,a.composer.selectedPart);assertEquals(project,a.composer.project.json().toString());
                assertArrayEquals(levels,a.values,0);assertEquals(visits,a.navigation.history.size());assertSame(previous,a.navigation.previous());
                assertTrue(AudioService.active);assertTrue(AudioService.playbackActive);assertTrue(RecordingManager.active);
            }
            assertEquals(mic,StudioScreenshotTest.Audio.microphoneStarts);assertEquals(playback,StudioScreenshotTest.Audio.playbackStarts);
            assertEquals(stop,StudioScreenshotTest.Audio.stopCalls);assertEquals(suspend,StudioScreenshotTest.Audio.suspendCalls);
            assertTrue(StudioScreenshotTest.Composer.transportCalls.isEmpty());assertTrue(StudioScreenshotTest.Audio.localPlayCalls.isEmpty());
        } finally { close(a); }
    }

    static void selectOfflineYouTube(MainActivity a) {
        a.getPreferences(0).edit().putBoolean("youtubeConsent",true).commit();
        a.selectYouTube("M7lc1UVf-VE");ShadowLooper.idleMainLooper();a.handler.removeCallbacks(a.tick);
        assertTrue(a.youtube.selected);assertNotNull(a.youtube.web);assertNotNull(a.youtube.viewport);
    }
    static void revealInPane(MainActivity a,ScrollView pane,View control) {
        Rect area=new Rect();control.getDrawingRect(area);
        ViewGroup content=(ViewGroup)pane.getChildAt(0);
        content.offsetDescendantRectToMyCoords(control,area);
        pane.requestChildRectangleOnScreen(content,area,true);
        assertTrue("Right pane must fully reveal "+control.getClass().getSimpleName()
                +" bounds="+bounds(a,control)+" pane="+bounds(a,pane),bounds(a,pane).contains(bounds(a,control)));
    }

    @Test public void officialYouTubeViewportAndEffectsRemainReachableInRightPane() throws Exception {
        MainActivity a=open(1480,924,1.3f,true);
        try {
            selectOfflineYouTube(a);measure(a,1480,924);
            View left=a.root.findViewWithTag("tablet-live-left");assertNotNull(left);
            ScrollView right=pane(a,"tablet-live-right");
            YouTubeViewport player=a.youtube.viewport;
            assertTrue("Official viewport is attached below the right pane",isDescendant(right,player));
            assertTrue(player.getWidth()>=a.dp(200));assertTrue(player.getHeight()>=a.dp(200));
            assertEquals("Official viewport keeps complete 16:9 frame",Math.ceil(player.getWidth()*9d/16d),player.getHeight(),1);
            assertEquals(player.getWidth(),a.youtube.web.getWidth());assertEquals(player.getHeight(),a.youtube.web.getHeight());
            Rect leftPosition=bounds(a,left),footer=bounds(a,a.recButton);
            assertTrue("Player plus effects need independent right scrolling",right.canScrollVertically(1));
            revealInPane(a,right,player);assertEquals(leftPosition,bounds(a,left));assertEquals(footer,bounds(a,a.recButton));
            revealInPane(a,right,a.liveScreen.reverbVolume);revealInPane(a,right,a.liveScreen.echoVolume);
            assertTrue("Effects are reached by scrolling right pane",right.getScrollY()>0);
            assertEquals(leftPosition,bounds(a,left));assertEquals(footer,bounds(a,a.recButton));
            float mic=a.values[0];YouTubePlayerTest.OfflineWeb.scripts.clear();StudioScreenshotTest.Audio.changes.clear();
            a.set(1,.42f);
            assertTrue(YouTubePlayerTest.OfflineWeb.scripts.contains("volume(42)"));assertEquals(mic,a.values[0],0);
            assertFalse(StudioScreenshotTest.Audio.changes.isEmpty());
            for(float[] change:StudioScreenshotTest.Audio.changes)assertEquals("Backing gain does not adjust microphone",1,(int)change[0]);
        } finally { a.youtube.destroy();close(a); }
    }
    static boolean isDescendant(ViewGroup parent,View view) {
        android.view.ViewParent ancestor=view.getParent();
        while(ancestor!=null) { if(ancestor==parent)return true;ancestor=ancestor.getParent(); }
        return false;
    }

    @Test public void cameraPreviewRetainsTextureAndControllerAcrossRotation() throws Exception {
        MainActivity a=open(1480,924,1,true);
        try {
            Shadows.shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(android.Manifest.permission.CAMERA);
            a.chooseTab(3);measure(a,1480,924);
            android.view.TextureView texture=a.texture;CameraController camera=a.camera;
            assertNotNull("Permission-granted preview creates TextureView",texture);assertNotNull(camera);
            assertFalse("Fixture does not record camera hardware",camera.recording);
            int mic=StudioScreenshotTest.Audio.microphoneStarts,playback=StudioScreenshotTest.Audio.playbackStarts;
            int stop=StudioScreenshotTest.Audio.stopCalls,suspend=StudioScreenshotTest.Audio.suspendCalls;
            int visits=a.navigation.history.size();
            for(int[] size:new int[][]{{924,1480},{1480,924}}) {
                resize(a,size[0],size[1]);
                assertEquals(3,a.tab);assertSame("Resize retains attached preview texture",texture,a.texture);
                assertSame("Resize retains camera session owner",camera,a.camera);assertNotNull(texture.getParent());
                assertFalse(camera.recording);assertEquals(visits,a.navigation.history.size());
                assertEquals(mic,StudioScreenshotTest.Audio.microphoneStarts);assertEquals(playback,StudioScreenshotTest.Audio.playbackStarts);
                assertEquals(stop,StudioScreenshotTest.Audio.stopCalls);assertEquals(suspend,StudioScreenshotTest.Audio.suspendCalls);
            }
        } finally {
            if(a.camera!=null){a.camera.release();a.camera=null;}
            Shadows.shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(android.Manifest.permission.CAMERA);
            close(a);
        }
    }

}
