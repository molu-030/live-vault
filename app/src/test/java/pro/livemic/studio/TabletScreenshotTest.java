package pro.livemic.studio;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.View;
import android.widget.ScrollView;
import java.io.File;
import java.util.HashSet;
import java.util.Set;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;

/** Native Skia screenshots of actual Android Views. Tablet fixtures reserve representative 24dp status
 * and 48dp taskbar insets; these are not measured Galaxy defaults. No audio or network media starts. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,instrumentedPackages={"pro.livemic.studio"},shadows={StudioScreenshotTest.Audio.class,StudioScreenshotTest.Composer.class,StudioScreenshotTest.Harmony.class,YouTubePlayerTest.OfflineWeb.class})
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class TabletScreenshotTest {
    @Before public void reset() throws Exception { new StudioScreenshotTest().resetComposer(); }
    @After public void restore() { RuntimeEnvironment.setFontScale(1);AppLanguage.set(RuntimeEnvironment.getApplication(),false);AudioService.active=false;AudioService.playbackActive=false;RecordingManager.active=false;AudioService.youtubeSelected=false; }

    static void capture(MainActivity a,int width,int height,String name) throws Exception {
        TabletLayoutTest.measure(a,width,height);
        File directory=new File(System.getProperty("user.dir"),"build/studio-screenshots/tablet");
        assertTrue(directory.isDirectory() || directory.mkdirs());
        File output=new File(directory,name+".png");
        StudioScreenshotTest.capture(a,width,height,output,false);
        if(a.tab==0) {
            assertGainLabel(a,a.liveScreen.micVolume,AppLanguage.text(a,"마이크","Mic"),name);
            assertGainLabel(a,a.liveScreen.backingVolume,AppLanguage.text(a,"반주","Backing"),name);
        }
        assertTrue("Screenshot was written",output.length()>1000);
        Bitmap actual=BitmapFactory.decodeFile(output.getAbsolutePath());assertNotNull(actual);
        try {
            assertEquals(width,actual.getWidth());assertEquals(height,actual.getHeight());
            Set<Integer> colors=new HashSet<>();
            for(int y=0;y<height;y+=11)for(int x=0;x<width;x+=11)colors.add(actual.getPixel(x,y));
            assertTrue("Actual Views must render beyond a blank background",colors.size()>20);
        } finally { actual.recycle(); }
    }

    static void assertGainLabel(MainActivity a,View slider,String expected,String fixture) {
        android.widget.TextView label=(android.widget.TextView)((android.view.ViewGroup)slider.getParent()).getChildAt(0);
        assertEquals(fixture+" gain label",expected,label.getText().toString());
        android.text.Layout layout=label.getLayout();assertNotNull(fixture+" native gain text layout",layout);
        String context=fixture+" full gain label "+expected+" column="+label.getWidth();
        assertEquals(context+" uses one line",1,layout.getLineCount());
        assertEquals(context+" includes every character",expected.length(),layout.getLineEnd(0));
        assertEquals(context+" is not ellipsized",0,layout.getEllipsisCount(0));
        int availableWidth=label.getWidth()-label.getCompoundPaddingLeft()-label.getCompoundPaddingRight();
        int availableHeight=label.getHeight()-label.getCompoundPaddingTop()-label.getCompoundPaddingBottom();
        assertTrue(context+" native line width fits",layout.getLineWidth(0)<=availableWidth+.5f);
        assertTrue(context+" native painted text fits",label.getPaint().measureText(expected)<=availableWidth+.5f);
        assertTrue(context+" visible line height fits",layout.getLineBottom(0)-layout.getLineTop(0)<=availableHeight);
    }

    @Test public void representativeTabletAndPhoneViews() throws Exception {
        // Twelve baseline images plus one explicitly labeled offline YouTube fixture below.
        for(boolean english:new boolean[]{false,true}) {
            float scale=english?1.3f:1;
            MainActivity a=TabletLayoutTest.open(1480,924,scale,english);
            try {
                String prefix="s9ultra-1480x924-"+(english?"en-130":"ko-100");
                a.chooseTab(0);float[] stats=new float[22];stats[0]=440;stats[1]=12;stats[2]=69;stats[3]=.12f;stats[4]=.08f;
                a.liveScreen.poll(stats,true);capture(a,1480,924,prefix+"-live");
                a.chooseTab(7);capture(a,1480,924,prefix+"-composer");
                a.chooseTab(8);capture(a,1480,924,prefix+"-voice");
            } finally { TabletLayoutTest.close(a); }
        }
        MainActivity medium=TabletLayoutTest.open(1280,800,1,true);
        try {
            capture(medium,1280,800,"tablet-1280x800-en-100-live");
            medium.chooseTab(7);capture(medium,1280,800,"tablet-1280x800-en-100-composer");
        } finally { TabletLayoutTest.close(medium); }
        MainActivity narrow=TabletLayoutTest.open(960,600,1.3f,false);
        try {
            capture(narrow,960,600,"multiwindow-960x600-ko-130-live");
            TabletLayoutTest.largeProject(narrow,false);narrow.chooseTab(7);TabletLayoutTest.measure(narrow,960,600);
            ScrollView left=TabletLayoutTest.pane(narrow,"tablet-composer-left"),right=TabletLayoutTest.pane(narrow,"tablet-composer-right");
            left.scrollTo(0,100000);right.scrollTo(0,100000);
            assertTrue(left.getScrollY()>0);assertTrue(right.getScrollY()>0);
            capture(narrow,960,600,"multiwindow-960x600-ko-130-16bars-8tracks-bottom");
        } finally { TabletLayoutTest.close(narrow); }
        MainActivity phone=TabletLayoutTest.open(412,893,1.3f,true);
        try {
            capture(phone,412,893,"phone-412x893-en-130-live");
            phone.chooseTab(7);capture(phone,412,893,"phone-412x893-en-130-composer");
        } finally { TabletLayoutTest.close(phone); }
    }
    @Test public void tabletOfficialViewportWithClearlyLabeledOfflineContent() throws Exception {
        MainActivity a=TabletLayoutTest.open(1480,924,1.3f,true);
        try {
            TabletLayoutTest.selectOfflineYouTube(a);TabletLayoutTest.measure(a,1480,924);
            YouTubeScreenshotTest.offlinePlayer(a);
            TabletLayoutTest.measure(a,1480,924);
            TabletLayoutTest.revealInPane(a,TabletLayoutTest.pane(a,"tablet-live-right"),a.youtube.viewport);
            capture(a,1480,924,"s9ultra-1480x924-en-130-youtube-OFFLINE-fixture");
        } finally { a.youtube.destroy();TabletLayoutTest.close(a); }
    }

}
