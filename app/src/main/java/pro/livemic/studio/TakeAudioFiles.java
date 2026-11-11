package pro.livemic.studio;

import android.content.Context;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONArray;
import org.json.JSONObject;

/** Worker-only transactions. Existing takes and their sources are never overwritten. */
final class TakeAudioFiles {
    static String newId(){return "edit_"+System.currentTimeMillis()+"_"+UUID.randomUUID().toString().substring(0,8);}
    static boolean rawEditable(TakeStore store,JSONObject take){
        String id=take.optString("id");return id.matches("[A-Za-z0-9_-]+")&&!store.isRecovering(id)&&!take.optBoolean("integrityRepairPending")&& take.optBoolean("cleanSources")&&take.optBoolean("sourceEditable")
                &&store.file(id,".raw.wav").length()>44&&(!take.optBoolean("sourceFixed",true)||store.file(id,".fixed.wav").length()>44);
    }
    static boolean stemEditable(TakeStore store,JSONObject take){
        String id=take.optString("id");return id.matches("[A-Za-z0-9_-]+")&&!store.isRecovering(id)&&!take.optBoolean("integrityRepairPending")&&take.optBoolean("sourceVocalRetained")&&take.optBoolean("vocalStemEditable")
                &&store.file(id,".vocal.wav").length()>44&&(!take.optBoolean("sourceFixed",true)||store.file(id,".fixed.wav").length()>44);
    }
    static boolean editable(TakeStore store,JSONObject take){return rawEditable(store,take)||stemEditable(store,take);}
    static boolean vocalMode(TakeStore store,JSONObject take){return stemEditable(store,take)&&(!rawEditable(store,take)||"vocal".equals(take.optString("effectsMode")));}
    static boolean videoEditable(TakeStore store,JSONObject take){
        return take.optString("id").matches("[A-Za-z0-9_-]+")&&take.optBoolean("video")&&take.opt("videoAudioOffsetUs") instanceof Number
                &&Double.isFinite(take.optDouble("videoAudioOffsetUs"))&&store.file(take.optString("id"),".mp4").length()>0;
    }
    static float[] effects(JSONObject take,float[] fallback,boolean vocal){
        if(!vocal)return effects(take,fallback);
        float[] result=fallback.clone();result[0]=1;for(int key:new int[]{2,4,6,7,8,9,10,19})result[key]=0;
        JSONArray saved=take.optJSONArray("vocalEffects");
        if(saved!=null&&saved.length()==20)for(int i=0;i<20;i++)result[i]=(float)saved.optDouble(i,result[i]);
        return result;
    }
    static boolean render(TakeStore store,JSONObject take,float[] settings,boolean vocal,File output){
        String id=take.optString("id"),fixed=take.optBoolean("sourceFixed",true)?store.file(id,".fixed.wav").getAbsolutePath():"";
        try(TakeRecovery.Lease lease=store.lease(id)){
        if(store.read(id).optBoolean("integrityRepairPending"))return false;
        return vocal?NativeAudio.renderVocalStem(store.file(id,".vocal.wav").getAbsolutePath(),fixed,output.getAbsolutePath(),settings,take.optInt("syncOffsetMs"))
                :NativeAudio.renderTakeSources(store.file(id,".raw.wav").getAbsolutePath(),fixed,output.getAbsolutePath(),settings,take.optInt("syncOffsetMs"));
        }catch(Exception busy){return false;}
    }
    static float[] effects(JSONObject take,float[] fallback){
        float[] result=fallback.clone();JSONArray saved=take.optJSONArray("initialEffects");
        if(saved!=null&&saved.length()==20)for(int i=0;i<20;i++)result[i]=(float)saved.optDouble(i,result[i]);
        return result;
    }
    static String copy(Context context,JSONObject take,float[] effects,AtomicBoolean cancelled)throws Exception{
        return copy(context,take,effects,cancelled,vocalMode(new TakeStore(context,false),take));
    }
    static String copy(Context context,JSONObject take,float[] effects,AtomicBoolean cancelled,boolean vocal)throws Exception{
        TakeStore store=new TakeStore(context,false);
        try(TakeRecovery.Lease lease=store.lease(take.getString("id"))){return copyLocked(context,store.read(take.getString("id")),effects,cancelled,vocal);}
    }
    private static String copyLocked(Context context,JSONObject take,float[] effects,AtomicBoolean cancelled,boolean vocal)throws Exception{
        TakeStore store=new TakeStore(context,false);String source=take.getString("id");
        if(!(vocal?stemEditable(store,take):rawEditable(store,take)))throw new IOException("This take has no supported editable sources.");
        String storageProblem=TakeStorage.copyProblem(context,store,take,false);
        if(!storageProblem.isEmpty())throw new IOException(storageProblem);
        String id=newId();File tmp=store.file(id,".render.pending"),raw=store.file(id,".raw.pending"),fixed=store.file(id,".fixed.pending"),voice=store.file(id,".vocal.pending");
        boolean published=false;
        try{
            if(cancelled.get()||ExitCoordinator.isExitRequested())throw new IOException("Cancelled");
            if(!render(store,take,effects,vocal,tmp))throw new IOException("Could not render vocal effects.");
            if(cancelled.get()||ExitCoordinator.isExitRequested())throw new IOException("Cancelled");
            boolean keepRaw=store.file(source,".raw.wav").length()>44;
            boolean keepVoice=store.file(source,".vocal.wav").length()>44;
            if(keepRaw)copySource(store.file(source,".raw.wav"),raw,cancelled);
            if(keepVoice)copySource(store.file(source,".vocal.wav"),voice,cancelled);
            if(take.optBoolean("sourceFixed",true))copySource(store.file(source,".fixed.wav"),fixed,cancelled);
            if(cancelled.get()||ExitCoordinator.isExitRequested())throw new IOException("Cancelled");
            if(keepRaw)Files.move(raw.toPath(),store.file(id,".raw.wav").toPath());
            if(keepVoice)Files.move(voice.toPath(),store.file(id,".vocal.wav").toPath());
            if(take.optBoolean("sourceFixed",true))Files.move(fixed.toPath(),store.file(id,".fixed.wav").toPath());
            Files.move(tmp.toPath(),store.file(id,".wav").toPath());published=true;
            JSONObject extras=new JSONObject(take.toString());
            for(String key:new String[]{"id","name","created","seconds","video","warning"})extras.remove(key);
            JSONArray params=new JSONArray();for(float value:effects)params.put((double)value);
            extras.put(vocal?"vocalEffects":"initialEffects",params).put("effectsMode",vocal?"vocal":"raw").put("editedFrom",source);
            store.save(id,take.optString("name")+AppLanguage.text(context," · 효과 편집"," · effects edit"),take.optDouble("seconds"),false,"",extras);
            return id;
        }finally{
            tmp.delete();raw.delete();fixed.delete();voice.delete();
            if(!published){store.file(id,".raw.wav").delete();store.file(id,".fixed.wav").delete();store.file(id,".vocal.wav").delete();}
        }
    }
    static final class Saved {
        final String id,warning;final boolean video;
        Saved(String id,boolean video,String warning){this.id=id;this.video=video;this.warning=warning;}
    }
    static Saved copyVideo(Context context,JSONObject take,float[] effects,AtomicBoolean cancelled,boolean vocal,java.util.function.IntConsumer progress)throws Exception{
        TakeStore store=new TakeStore(context,false);
        try(TakeRecovery.Lease lease=store.lease(take.getString("id"))){return copyVideoLocked(context,store.read(take.getString("id")),effects,cancelled,vocal,progress);}
    }
    private static Saved copyVideoLocked(Context context,JSONObject take,float[] effects,AtomicBoolean cancelled,boolean vocal,java.util.function.IntConsumer progress)throws Exception{
        TakeStore store=new TakeStore(context,false);
        if(!videoEditable(store,take))throw new IOException(AppLanguage.text(context,"영상 싱크 정보가 없어 WAV 복사본만 저장할 수 있습니다.","Video timing metadata is unavailable. Save a WAV copy instead."));
        String storageProblem=TakeStorage.copyProblem(context,store,take,true);
        if(!storageProblem.isEmpty())throw new IOException(storageProblem);
        progress.accept(0);String id=copy(context,take,effects,cancelled,vocal);
        File work=null;String warning="";boolean video=false;
        try{
            TakeVideoMux.check(cancelled);work=Files.createTempDirectory(context.getCacheDir().toPath(),"take-video-edit-").toFile();
            File pending=new File(work,"edited.mp4");String source=take.getString("id");
            TakeVideoMux.render(store.file(source,".mp4"),store.file(source,".wav"),store.file(id,".wav"),pending,take.getLong("videoAudioOffsetUs"),cancelled,progress);
            TakeVideoMux.check(cancelled);
            MediaTools.publishVideo(pending,store.file(id,".mp4"));video=true;
            JSONObject meta=store.read(id);meta.put("video",true).put("videoEditedFrom",source);store.update(id,meta);
        }catch(Exception e){
            warning=cancelled.get()||ExitCoordinator.isExitRequested()
                    ?AppLanguage.text(context,"영상 처리를 취소했습니다. 저장된 편집 WAV 복사본은 유지됩니다.","Video processing cancelled. The saved edited WAV copy is retained.")
                    :AppLanguage.text(context,video?"MP4와 WAV를 저장했지만 영상 정보 갱신에 실패했습니다: ":"영상 결합 실패. 편집 WAV 복사본은 저장되어 있습니다: ",video?"MP4 and WAV saved, but video metadata could not be updated: ":"Video merge failed. The edited WAV copy is saved: ")+(e.getMessage()==null?e.toString():e.getMessage());
            try{JSONObject meta=store.read(id);meta.put("warning",warning);if(video)meta.put("video",true);store.update(id,meta);}catch(Exception ignored){}
        }finally{
            if(work!=null){new File(work,"edited.mp4").delete();new File(work,"edited.mp4.aac.m4a").delete();work.delete();}
        }
        return new Saved(id,video,warning);
    }
    private static void copySource(File from,File to,AtomicBoolean cancelled)throws IOException {
        try(java.io.FileInputStream input=new java.io.FileInputStream(from);java.io.FileOutputStream output=new java.io.FileOutputStream(to)){
            byte[] buffer=new byte[65536];int count;
            while((count=input.read(buffer))!=-1){
                if(cancelled.get()||ExitCoordinator.isExitRequested())throw new IOException("Cancelled");
                output.write(buffer,0,count);
            }
            output.getFD().sync();
        }
    }
    static String recover(Context context,float[] effects,AtomicBoolean cancelled)throws Exception{
        TakeStore store=new TakeStore(context,false);String id=newId();File raw=store.file(id,".raw.pending"),mix=store.file(id,".render.pending");
        boolean published=false;
        try{
            if(cancelled.get()||ExitCoordinator.isExitRequested()||!AudioService.active)throw new IOException("No recent microphone audio is available.");
            long snapshot=NativeAudio.saveRecentVoice(raw.getAbsolutePath());if(snapshot<0)throw new IOException("No recent microphone audio is available.");
            if(cancelled.get()||ExitCoordinator.isExitRequested()||!NativeAudio.renderTakeSources(raw.getAbsolutePath(),"",mix.getAbsolutePath(),effects,0))throw new IOException("Could not recover the voice.");
            if(cancelled.get()||ExitCoordinator.isExitRequested())throw new IOException("Cancelled");
            Files.move(raw.toPath(),store.file(id,".raw.wav").toPath());Files.move(mix.toPath(),store.file(id,".wav").toPath());published=true;
            int rate;try(java.io.RandomAccessFile wav=new java.io.RandomAccessFile(store.file(id,".raw.wav"),"r")){wav.seek(24);rate=Integer.reverseBytes(wav.readInt());}
            double seconds=(store.file(id,".raw.wav").length()-44)/(4.0*rate);JSONArray params=new JSONArray();for(float value:effects)params.put((double)value);
            store.save(id,AppLanguage.text(context,"복구한 직전 목소리","Recovered recent voice"),seconds,false,"",new JSONObject()
                    .put("cleanSources",true).put("sourceEditable",true).put("sourceFixed",false).put("sourceFloat32",true)
                    .put("recentVoiceRecovery",true).put("microphoneOnly",true).put("syncOffsetMs",0).put("initialEffects",params));
            NativeAudio.commitRecentVoice(snapshot);
            return id;
        }finally{raw.delete();mix.delete();if(!published)store.file(id,".raw.wav").delete();}
    }
}
