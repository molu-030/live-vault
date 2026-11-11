package pro.livemic.studio;

import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class SessionProjectTest {
    static SessionProject audio(){SessionProject p=new SessionProject();SessionProject.Media m=new SessionProject.Media();m.file="media/"+m.id+".wav";m.sampleRate=44100;m.channels=2;m.format=1;m.bits=16;m.frames=44100;p.media.add(m);SessionProject.Track t=new SessionProject.Track();p.tracks.add(t);SessionProject.Clip c=new SessionProject.Clip();c.trackId=t.id;c.mediaId=m.id;c.startFrame=48000;c.lengthFrames=48000;p.clips.add(c);return p;}
    private static void invalid(SessionProject p){try{p.validate();fail("Expected invalid session");}catch(IllegalArgumentException expected){}}
    @Test public void roundTripPreservesLongFramesAndEmbeddedComposer()throws Exception{
        SessionProject p=audio();p.composer=new ComposerProject();p.composer.bpm=111;p.composer.key=5;p.clips.get(0).startFrame=1_500_000_000L;p.clips.get(0).sourceOffsetFrames=12000;p.clips.get(0).lengthFrames=36000;p.pendingTarget=new JSONObject().put("token","capture_1").put("sessionId",p.id).put("trackId",p.tracks.get(0).id).put("startFrame",1_500_000_000L);
        SessionProject read=SessionProject.read(p.json());assertEquals(p.json().toString(),read.json().toString());assertEquals(1_500_036_000L,read.durationFrames());assertEquals(111,read.composer.bpm);assertEquals(5,read.composer.key);
    }
    @Test public void conversionUsesIntegralProjectTimebaseAndRejectsOverflow(){assertEquals(48000,SessionProject.sourceToProject(44100,44100));assertEquals(44100,SessionProject.projectToSource(48000,44100));assertEquals(1,SessionProject.sourceToProject(1,44100));try{SessionProject.sourceToProject(Long.MAX_VALUE,8000);fail();}catch(IllegalArgumentException expected){}try{SessionProject.sourceToProject(-1,44100);fail();}catch(IllegalArgumentException expected){}}
    @Test public void clipBoundsOffsetsLoopsAndDuplicateIdsAreValidated(){SessionProject p=audio();p.clips.get(0).sourceOffsetFrames=1;invalid(p);p.clips.get(0).loop=true;p.clips.get(0).loopLengthFrames=47999;p.validate();p.clips.get(0).lengthFrames=SessionProject.MAX_FRAMES;p.clips.get(0).startFrame=1;invalid(p);p=audio();p.media.add(p.media.get(0));invalid(p);p=audio();p.tracks.get(0).gain=Float.NaN;invalid(p);p=audio();p.media.get(0).file="../takes/old.wav";invalid(p);}
    @Test public void futureSchemaAndFractionalPositionsAreRejected()throws Exception{JSONObject j=audio().json();j.put("version",2);try{SessionProject.read(j);fail();}catch(JSONException expected){}j=audio().json();j.getJSONArray("clips").getJSONObject(0).put("startFrame",.5);try{SessionProject.read(j);fail();}catch(JSONException expected){}}
    @Test public void everyMediaIntegerFieldRejectsFractionalValues()throws Exception{for(String key:new String[]{"sampleRate","channels","format","bits","frames"}){JSONObject j=audio().json();JSONObject m=j.getJSONArray("media").getJSONObject(0);m.put(key,m.getDouble(key)+.5);try{SessionProject.read(j);fail(key);}catch(JSONException expected){}}JSONObject j=audio().json().put("version",1.5);try{SessionProject.read(j);fail();}catch(JSONException expected){}}
    @Test public void pendingCaptureRequiresExistingTrackAndSessionIdentity()throws Exception{SessionProject p=audio();p.pendingTarget=new JSONObject().put("token","capture").put("sessionId","other").put("trackId",p.tracks.get(0).id).put("startFrame",0);invalid(p);p.pendingTarget.put("sessionId",p.id);p.validate();p.pendingTarget.put("trackId","missing");invalid(p);}
}
