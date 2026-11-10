package pro.livemic.studio;
import android.content.Context;
import java.io.*;
import java.text.*;
import java.util.*;
public final class RecordingManager {
    public static volatile boolean active=false,paused=false,micOnly=false;public static volatile String id=null;public static int bpm=78;
    public static String extraWarning="";public static volatile Result lastResult=null;
    public static int syncMs=0;public static org.json.JSONObject practiceResult=null;
    private static Context context;
    private static volatile boolean preparing,finalizing;
    static boolean isWritingId(String takeId){return takeId.equals(id)&&(active||preparing||finalizing);}
    private static int savedSyncMs;
    private static boolean savedRetainSources;
    public static String startError="";
    private static RecordingSyncProfiles.Profile savedSyncProfile;
    private static org.json.JSONArray savedEffects;
    private static org.json.JSONObject sessionTarget,savedSessionTarget;
    public static synchronized boolean startSession(Context c,int tempo,org.json.JSONObject target){
        sessionTarget=target;
        try{return start(c,tempo,false);}finally{sessionTarget=null;}
    }
    public static synchronized boolean start(Context c,int tempo){return start(c,tempo,AudioService.youtubeSelected);}
    public static synchronized boolean start(Context c,int tempo,boolean microphoneOnly){
        startError="";
        if(active||NativeAudio.stats()[10]<.5f)return false;
        startError=TakeStorage.startProblem(c,false);if(!startError.isEmpty())return false;
        context=c.getApplicationContext();bpm=tempo;savedSessionTarget=sessionTarget;savedRetainSources=TakeStorage.retainSources(c);
        preparing=true;
        id=new SimpleDateFormat("yyyyMMdd_HHmmss_SSS",Locale.US).format(new Date())+"_"+UUID.randomUUID().toString().substring(0,8);TakeStore store=new TakeStore(context,false);
        savedSyncProfile=RecordingSyncProfiles.current(c);savedSyncMs=microphoneOnly||sessionTarget!=null?0:savedSyncProfile.offsetMs;syncMs=savedSyncMs;
        savedEffects=new org.json.JSONArray();float[] defaults={1,.55f,.27f,2.2f,.14f,280,.25f,0,0,1,1,0,0,0,0,78,0,0,0,1};
        android.content.SharedPreferences effectValues=AudioRouting.studioPreferences(c);
        for(int i=0;i<20;i++)savedEffects.put((Object)Double.valueOf(effectValues.getFloat("p"+i,defaults[i])));
        try{RecordingIntegrity.writeState(store,id,new org.json.JSONObject().put("syncOffsetMs",savedSyncMs).put("microphoneOnly",microphoneOnly)
                .put("syncProfileKey",savedSyncProfile.key).put("syncProfileConfirmed",savedSyncProfile.confirmed).put("syncProfileInherited",savedSyncProfile.inherited)
                .put("sourceRetention",savedRetainSources?"retained":"not-requested").put("initialEffects",savedEffects).put("sessionTarget",sessionTarget==null?org.json.JSONObject.NULL:sessionTarget));}
        catch(Exception e){preparing=false;startError=AppLanguage.text(c,"녹음 복구 정보를 저장하지 못했습니다: ","Could not save recording recovery information: ")+e.getMessage();return false;}
        try{
        micOnly=microphoneOnly;NativeAudio.recordMicOnly(micOnly);NativeAudio.recordSync(savedSyncMs);practiceResult=null;extraWarning="";lastResult=null;paused=false;active=savedRetainSources?NativeAudio.beginRecordStems(store.file(id,".wav").getAbsolutePath(),store.file(id,".raw.wav").getAbsolutePath(),store.file(id,".fixed.wav").getAbsolutePath(),store.file(id,".vocal.wav").getAbsolutePath()):NativeAudio.beginRecord(store.file(id,".wav").getAbsolutePath());if(!active){NativeAudio.recordMicOnly(false);micOnly=false;if(store.file(id,".wav").length()<=44){RecordingIntegrity.cleanup(store,id);for(String suffix:RecordingIntegrity.SUFFIXES)if(store.file(id,suffix).length()<=44)store.file(id,suffix).delete();}}return active;
        }finally{preparing=false;}
    }
    public static synchronized void pause(boolean value){if(active){NativeAudio.pauseRecord(value);paused=value;}}
    public static synchronized Result stop()throws Exception{
        if(!active)return null;finalizing=true;TakeStore finishingStore=null;boolean repairPending=false;String finishedId=id;
        try{android.util.Log.i("LiveMicRecord","Closing audio writer");boolean savedMicOnly=micOnly;NativeAudio.endRecord();NativeAudio.recordMicOnly(false);micOnly=false;android.util.Log.i("LiveMicRecord","Audio closed; finalizing take");active=false;paused=false;float[] s=NativeAudio.stats();double clock=NativeAudio.recordClock();
        TakeStore store=new TakeStore(context,false);finishingStore=store;boolean midi=NativeAudio.exportMidi(store.file(id,".mid").getAbsolutePath(),bpm,true);
        String warning=s[13]>.5?AppLanguage.text(context,"오디오 버퍼 또는 저장 오류가 감지되었습니다. 재생 상태를 확인하세요.","An audio buffer or save error was detected. Check playback."):!midi?AppLanguage.text(context,"MIDI 파일을 저장하지 못했습니다.","Could not save the MIDI file."):"";
        if(!extraWarning.isEmpty())warning+=(warning.isEmpty()?"":"\n")+extraWarning;
        boolean vocal=savedRetainSources&&NativeAudio.recordVocalStemValid()&&store.file(id,".vocal.wav").length()>44;
        boolean sources=savedRetainSources&&NativeAudio.recordSourcesValid()&&store.file(id,".raw.wav").length()>44&&store.file(id,".fixed.wav").length()>44;
        boolean editable=sources&&NativeAudio.recordSourcesEditable();
        if(savedRetainSources&&!sources){warning+=(warning.isEmpty()?"":"\n")+AppLanguage.text(context,"원음 분리 파일 저장에 문제가 있습니다. 원래 믹스와 남아 있는 원음 파일은 유지됩니다.","Source files could not be fully saved. The original mix and retained source files are kept.");}
        if(savedRetainSources&&!vocal)warning+=(warning.isEmpty()?"":"\n")+AppLanguage.text(context,"보컬 분리 파일을 저장하지 못했습니다. 최종 믹스와 남은 원음은 유지됩니다.","The vocal stem could not be saved. The final mix and remaining sources are retained.");
        org.json.JSONObject extras=new org.json.JSONObject().put("sourceRetention",savedRetainSources?"retained":"not-requested").put("sessionTarget",savedSessionTarget==null?org.json.JSONObject.NULL:savedSessionTarget)
                .put("sourceVocalRetained",store.file(id,".vocal.wav").length()>44).put("vocalStemEditable",vocal&&sources)
                .put("syncOffsetMs",savedSyncMs).put("microphoneOnly",savedMicOnly)
                .put("syncProfileKey",savedSyncProfile.key).put("syncProfileConfirmed",savedSyncProfile.confirmed).put("syncProfileInherited",savedSyncProfile.inherited)
                .put("sourceRawRetained",store.file(id,".raw.wav").length()>44).put("sourceFixedRetained",store.file(id,".fixed.wav").length()>44)
                .put("cleanSources",sources).put("sourceFloat32",sources).put("sourceEditable",editable).put("sourceFixed",sources)
                .put("sourceEditReason",sources&&!editable?"live-harmony-or-voice-routing-or-format":"").put("initialEffects",savedEffects);if(practiceResult!=null)extras.put("practice",practiceResult);
        store.save(id,"Take "+new SimpleDateFormat("MM.dd HH:mm",Locale.KOREA).format(new Date()),s[8],false,warning,extras);
        android.util.Log.i("LiveMicRecord","Take metadata saved");org.json.JSONObject committed=store.read(id);repairPending=committed.optBoolean("integrityRepairPending");lastResult=new Result(id,committed.optDouble("seconds",s[8]),clock,committed.optString("warning",warning),savedSyncMs);return lastResult;
        }finally{finalizing=false;if(repairPending&&finishingStore!=null)finishingStore.queueRecovery(finishedId);}
    }
    public static class Result {public final String id,warning;public final double seconds,clock;public final int offsetMs;Result(String i,double s,double c,String w){this(i,s,c,w,0);}Result(String i,double s,double c,String w,int offset){id=i;seconds=s;clock=c;warning=w;offsetMs=offset;}}
}
