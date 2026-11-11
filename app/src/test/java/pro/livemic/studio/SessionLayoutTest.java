package pro.livemic.studio;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.view.View;
import java.io.File;
import java.util.HashSet;
import java.util.Set;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowLooper;
import static org.junit.Assert.*;

/** Actual Android View/Skia renders of maximum track/clip fixtures, without starting audio. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,instrumentedPackages={"pro.livemic.studio"},shadows={StudioScreenshotTest.Audio.class,StudioScreenshotTest.Composer.class,StudioScreenshotTest.Harmony.class,SessionScreenTest.Session.class,YouTubePlayerTest.OfflineWeb.class})
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class SessionLayoutTest {
    @Before public void reset()throws Exception{new StudioScreenshotTest().resetComposer();}
    @After public void restore(){RuntimeEnvironment.setFontScale(1);AppLanguage.set(RuntimeEnvironment.getApplication(),false);AudioService.active=false;AudioService.playbackActive=false;RecordingManager.active=false;AudioService.youtubeSelected=false;}
    static SessionProject fixture(boolean english){
        SessionProject p=new SessionProject();p.name=english?"Shared rehearsal session with eight long vocal track names":"여덟 개의 긴 보컬 트랙 이름이 있는 합주 세션";p.composer=new ComposerProject();
        for(int i=0;i<8;i++){
            SessionProject.Track t=new SessionProject.Track();t.kind=i==0?"HARMONY":"VOCAL";
            StringBuilder longName=new StringBuilder(english?"Vocal harmony layer ":"보컬 화음 레이어 ");longName.append(i+1).append(" ");while(longName.length()<160)longName.append(english?"extended ":"긴 이름 ");t.name=longName.substring(0,160);p.tracks.add(t);
            SessionProject.Media media=new SessionProject.Media();media.file="media/fixture"+i+".wav";media.frames=48000L*120;media.sampleRate=48000;media.channels=1;media.format=1;media.bits=16;p.media.add(media);
            for(int j=0;j<4;j++){SessionProject.Clip c=new SessionProject.Clip();c.trackId=t.id;c.mediaId=media.id;c.startFrame=48000L*(j*28+i);c.lengthFrames=48000L*20;p.clips.add(c);}
        }
        p.validate();return p;
    }
    static void render(MainActivity a,int width,int height,String name)throws Exception{
        TabletLayoutTest.measure(a,width,height);
        File directory=new File(System.getProperty("user.dir"),"build/studio-screenshots/session");assertTrue(directory.isDirectory()||directory.mkdirs());File output=new File(directory,name+".png");
        StudioScreenshotTest.capture(a,width,height,output,false);assertTrue(output.length()>1000);
        Bitmap bitmap=BitmapFactory.decodeFile(output.getAbsolutePath());assertNotNull(bitmap);try{assertEquals(width,bitmap.getWidth());assertEquals(height,bitmap.getHeight());Set<Integer> colors=new HashSet<>();for(int y=0;y<height;y+=11)for(int x=0;x<width;x+=11)colors.add(bitmap.getPixel(x,y));assertTrue("Actual view must have content",colors.size()>20);}finally{bitmap.recycle();}
    }
    @Test public void phoneAndS9UltraAt130PercentBothLanguagesRenderAndKeepDockReachable()throws Exception{
        for(int[] size:new int[][]{{412,893},{1480,924}})for(boolean english:new boolean[]{false,true}){
            MainActivity a=TabletLayoutTest.open(size[0],size[1],1.3f,english);
            try{
                a.session.worker.submit(()->{}).get();ShadowLooper.idleMainLooper();a.handler.removeCallbacks(a.tick);
                a.session.project=fixture(english);a.session.ready=true;a.session.busy=false;a.session.error="";a.session.message="";a.session.state=new long[]{0,0,48000L*42,48000L*112,48000,0,0,0,-1,0,48000};a.chooseTab(10);
                String prefix=size[0]+"x"+size[1]+"-"+(english?"en":"ko")+"-130-max";
                render(a,size[0],size[1],prefix+"-top");
                assertEquals(3,a.nav.getChildCount());assertEquals(9,a.sessionScreen.timeline.rows);
                assertTrue(a.sessionScreen.timeline.getContentDescription().toString().contains(a.session.project.tracks.get(7).name));
                for(View v:new View[]{a.sessionScreen.play,a.sessionScreen.stop,a.sessionScreen.record}){TabletLayoutTest.touchTarget(a,v);TabletLayoutTest.fixedDock(a,v);}
                assertTrue("Long content scrolls",a.pageScroll.getScrollY()>0);
                Rect dock=TabletLayoutTest.bounds(a,a.actionDock);assertTrue("Body cannot overlay fixed controls",TabletLayoutTest.bounds(a,a.pageScroll).bottom<=dock.top);
                a.pageScroll.scrollTo(0,a.sessionScreen.timeline.getTop());render(a,size[0],size[1],prefix+"-timeline");
                assertTrue("Label area is reserved",a.sessionScreen.timeline.graphLeft()>=a.dp(88));
                for(SessionProject.Track track:a.session.project.tracks){String fit=a.sessionScreen.timeline.fitName(track.name);assertTrue("Long names visibly ellipsize",fit.endsWith("…"));assertTrue("Name fits reserved column",a.sessionScreen.timeline.label.measureText(fit)<=a.sessionScreen.timeline.graphLeft()-a.dp(16)+1);}
                a.pageScroll.scrollTo(0,100000);render(a,size[0],size[1],prefix+"-bottom");assertTrue(a.pageScroll.getScrollY()>0);
            }finally{a.session.worker.shutdownNow();TabletLayoutTest.close(a);}
        }
    }
}
