package pro.livemic.studio;

import org.json.*;
import java.util.*;

/** Durable session model. All timeline positions use the fixed 48 kHz project timebase. */
final class SessionProject {
    static final int VERSION=1,RATE=48000,MAX_AUDIO_TRACKS=8,MAX_CLIPS=32;
    static final long MAX_FRAMES=RATE*60L*60*12;
    String id=UUID.randomUUID().toString(),name="Session";
    long created=System.currentTimeMillis(),modified=created;
    ComposerProject composer;
    JSONObject harmony=new JSONObject(),pendingTarget;
    final List<Media> media=new ArrayList<>();
    final List<Track> tracks=new ArrayList<>();
    final List<Clip> clips=new ArrayList<>();

    static final class Media {
        String id=UUID.randomUUID().toString(),file,sourceKind="import",sourceId="";
        int sampleRate,channels,format,bits;
        long frames;
        JSONObject json()throws JSONException{return new JSONObject().put("id",id).put("file",file).put("sampleRate",sampleRate).put("channels",channels).put("format",format).put("bits",bits).put("frames",frames).put("sourceKind",sourceKind).put("sourceId",sourceId);}
    }
    static final class Track {
        String id=UUID.randomUUID().toString(),name="Audio",kind="AUDIO";
        float gain=1,pan=0;boolean muted;
        JSONObject json()throws JSONException{return new JSONObject().put("id",id).put("name",name).put("kind",kind).put("gain",gain).put("pan",pan).put("muted",muted);}
    }
    static final class Clip {
        String id=UUID.randomUUID().toString(),trackId,mediaId;
        long startFrame,sourceOffsetFrames,lengthFrames,loopLengthFrames;
        boolean loop;
        JSONObject json()throws JSONException{return new JSONObject().put("id",id).put("trackId",trackId).put("mediaId",mediaId).put("startFrame",startFrame).put("sourceOffsetFrames",sourceOffsetFrames).put("lengthFrames",lengthFrames).put("loop",loop).put("loopLengthFrames",loopLengthFrames);}
    }
    static boolean safeId(String s){return s!=null&&s.matches("[A-Za-z0-9_-]{1,96}");}
    static void require(boolean ok,String why){if(!ok)throw new IllegalArgumentException(why);}
    static long convert(long frames,int from,int to){
        require(frames>=0&&from>=8000&&from<=192000&&to>=8000&&to<=192000,"Invalid frame conversion");
        try{return Math.addExact(Math.multiplyExact(frames/from,(long)to),Math.multiplyExact(frames%from,(long)to)/from);}
        catch(ArithmeticException e){throw new IllegalArgumentException("Frame conversion overflow",e);}
    }
    static long sourceToProject(long frames,int rate){return convert(frames,rate,RATE);}
    static long projectToSource(long frames,int rate){return convert(frames,RATE,rate);}
    Track track(String id){for(Track t:tracks)if(t.id.equals(id))return t;return null;}
    Media media(String id){for(Media m:media)if(m.id.equals(id))return m;return null;}
    long durationFrames(){long duration=0;for(Clip c:clips)duration=Math.max(duration,c.startFrame+c.lengthFrames);if(composer!=null)duration=Math.max(duration,Math.round(composer.totalBars()*MusicTheory.barBeats(composer.meter)*60*RATE/MusicTheory.quarterBpm(composer.bpm,composer.meter)));return duration;}
    void validate(){
        require(safeId(id),"Invalid session ID");require(name!=null&&!name.trim().isEmpty()&&name.length()<=160,"Invalid session name");require(created>=0&&modified>=0,"Invalid timestamp");
        require(tracks.size()<=MAX_AUDIO_TRACKS&&clips.size()<=MAX_CLIPS&&media.size()<=128,"Session limit exceeded");
        Set<String> ids=new HashSet<>(),files=new HashSet<>();
        for(Media m:media){require(safeId(m.id)&&ids.add(m.id),"Duplicate or invalid media ID");require(m.file!=null&&m.file.matches("media/[A-Za-z0-9_-]{1,96}\\.wav")&&files.add(m.file),"Invalid media path");require(m.sampleRate>=8000&&m.sampleRate<=192000&&(m.channels==1||m.channels==2)&&((m.format==1&&m.bits==16)||(m.format==3&&m.bits==32)),"Unsupported WAV format");require(m.frames>0&&sourceToProject(m.frames,m.sampleRate)<=MAX_FRAMES,"Invalid media duration");}
        ids.clear();for(Track t:tracks){require(safeId(t.id)&&ids.add(t.id),"Duplicate or invalid track ID");require(t.name!=null&&!t.name.isEmpty()&&t.name.length()<=160,"Invalid track name");require(Arrays.asList("AUDIO","VOCAL","HARMONY").contains(t.kind),"Invalid track kind");require(Float.isFinite(t.gain)&&t.gain>=0&&t.gain<=2&&Float.isFinite(t.pan)&&t.pan>=-1&&t.pan<=1,"Invalid track mix");}
        ids.clear();for(Clip c:clips){require(safeId(c.id)&&ids.add(c.id)&&track(c.trackId)!=null,"Invalid clip track");Media m=media(c.mediaId);require(m!=null,"Missing clip media");require(c.startFrame>=0&&c.startFrame<=MAX_FRAMES&&c.lengthFrames>0&&c.lengthFrames<=MAX_FRAMES-c.startFrame&&c.sourceOffsetFrames>=0,"Invalid clip position");long available=sourceToProject(m.frames,m.sampleRate);require(c.sourceOffsetFrames<available,"Invalid source offset");if(c.loop)require(c.loopLengthFrames>0&&c.loopLengthFrames<=available-c.sourceOffsetFrames,"Invalid loop length");else require(c.lengthFrames<=available-c.sourceOffsetFrames,"Clip exceeds media");}
        if(composer!=null)composer.validate();
        if(pendingTarget!=null){require(safeId(pendingTarget.optString("token"))&&id.equals(pendingTarget.optString("sessionId")),"Invalid recording target");require(track(pendingTarget.optString("trackId"))!=null,"Missing recording track");Object frame=pendingTarget.opt("startFrame");require(frame instanceof Integer||frame instanceof Long,"Invalid recording start");long start=((Number)frame).longValue();require(start>=0&&start<=MAX_FRAMES,"Invalid recording start");}
    }
    JSONObject json()throws JSONException{validate();JSONArray m=new JSONArray(),t=new JSONArray(),c=new JSONArray();for(Media x:media)m.put(x.json());for(Track x:tracks)t.put(x.json());for(Clip x:clips)c.put(x.json());return new JSONObject().put("version",VERSION).put("rate",RATE).put("id",id).put("name",name).put("created",created).put("modified",modified).put("composer",composer==null?JSONObject.NULL:composer.json()).put("harmony",harmony).put("media",m).put("tracks",t).put("clips",c).put("pendingTarget",pendingTarget==null?JSONObject.NULL:pendingTarget);}
    static SessionProject read(JSONObject j)throws JSONException{
        if(integer(j,"version")!=VERSION||integer(j,"rate")!=RATE)throw new JSONException("Unsupported session format");SessionProject p=new SessionProject();p.id=j.getString("id");p.name=j.getString("name");p.created=integer(j,"created");p.modified=integer(j,"modified");if(!j.isNull("composer"))p.composer=ComposerProject.read(j.getJSONObject("composer"));p.harmony=j.getJSONObject("harmony");if(!j.isNull("pendingTarget"))p.pendingTarget=j.getJSONObject("pendingTarget");
        JSONArray ma=j.getJSONArray("media"),ta=j.getJSONArray("tracks"),ca=j.getJSONArray("clips");if(ma.length()>128||ta.length()>MAX_AUDIO_TRACKS||ca.length()>MAX_CLIPS)throw new JSONException("Session limit exceeded");
        for(int i=0;i<ma.length();i++){JSONObject x=ma.getJSONObject(i);Media m=new Media();m.id=x.getString("id");m.file=x.getString("file");m.sampleRate=smallInteger(x,"sampleRate");m.channels=smallInteger(x,"channels");m.format=smallInteger(x,"format");m.bits=smallInteger(x,"bits");m.frames=integer(x,"frames");m.sourceKind=x.optString("sourceKind","import");m.sourceId=x.optString("sourceId","");p.media.add(m);}
        for(int i=0;i<ta.length();i++){JSONObject x=ta.getJSONObject(i);Track t=new Track();t.id=x.getString("id");t.name=x.getString("name");t.kind=x.getString("kind");t.gain=(float)x.getDouble("gain");t.pan=(float)x.getDouble("pan");t.muted=x.getBoolean("muted");p.tracks.add(t);}
        for(int i=0;i<ca.length();i++){JSONObject x=ca.getJSONObject(i);Clip c=new Clip();c.id=x.getString("id");c.trackId=x.getString("trackId");c.mediaId=x.getString("mediaId");c.startFrame=integer(x,"startFrame");c.sourceOffsetFrames=integer(x,"sourceOffsetFrames");c.lengthFrames=integer(x,"lengthFrames");c.loop=x.getBoolean("loop");c.loopLengthFrames=integer(x,"loopLengthFrames");p.clips.add(c);}
        try{p.validate();}catch(IllegalArgumentException e){throw new JSONException(e.getMessage());}return p;
    }
    private static int smallInteger(JSONObject j,String key)throws JSONException{long value=integer(j,key);if(value<Integer.MIN_VALUE||value>Integer.MAX_VALUE)throw new JSONException("Integer out of range: "+key);return (int)value;}
    private static long integer(JSONObject j,String key)throws JSONException{Object v=j.get(key);if(!(v instanceof Integer)&&!(v instanceof Long))throw new JSONException("Expected integer: "+key);return ((Number)v).longValue();}
}
