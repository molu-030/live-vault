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
    static boolean editable(TakeStore store,JSONObject take){
        String id=take.optString("id");return id.matches("[A-Za-z0-9_-]+")&& take.optBoolean("cleanSources")&&take.optBoolean("sourceEditable")
                &&store.file(id,".raw.wav").length()>44&&(!take.optBoolean("sourceFixed",true)||store.file(id,".fixed.wav").length()>44);
    }
    static float[] effects(JSONObject take,float[] fallback){
        float[] result=fallback.clone();JSONArray saved=take.optJSONArray("initialEffects");
        if(saved!=null&&saved.length()==20)for(int i=0;i<20;i++)result[i]=(float)saved.optDouble(i,result[i]);
        return result;
    }
    static String copy(Context context,JSONObject take,float[] effects,AtomicBoolean cancelled)throws Exception{
        TakeStore store=new TakeStore(context,false);String source=take.getString("id");
        if(!editable(store,take))throw new IOException("This take has no supported editable sources.");
        String id=newId();File tmp=store.file(id,".render.pending"),raw=store.file(id,".raw.pending"),fixed=store.file(id,".fixed.pending");
        boolean published=false;
        try{
            if(cancelled.get()||ExitCoordinator.isExitRequested())throw new IOException("Cancelled");
            if(!NativeAudio.renderTakeSources(store.file(source,".raw.wav").getAbsolutePath(),take.optBoolean("sourceFixed",true)?store.file(source,".fixed.wav").getAbsolutePath():"",tmp.getAbsolutePath(),effects,take.optInt("syncOffsetMs")))throw new IOException("Could not render vocal effects.");
            if(cancelled.get()||ExitCoordinator.isExitRequested())throw new IOException("Cancelled");
            copySource(store.file(source,".raw.wav"),raw,cancelled);
            if(take.optBoolean("sourceFixed",true))copySource(store.file(source,".fixed.wav"),fixed,cancelled);
            if(cancelled.get()||ExitCoordinator.isExitRequested())throw new IOException("Cancelled");
            Files.move(raw.toPath(),store.file(id,".raw.wav").toPath());
            if(take.optBoolean("sourceFixed",true))Files.move(fixed.toPath(),store.file(id,".fixed.wav").toPath());
            Files.move(tmp.toPath(),store.file(id,".wav").toPath());published=true;
            JSONObject extras=new JSONObject(take.toString());
            for(String key:new String[]{"id","name","created","seconds","video","warning"})extras.remove(key);
            JSONArray params=new JSONArray();for(float value:effects)params.put((double)value);
            extras.put("initialEffects",params).put("editedFrom",source);
            store.save(id,take.optString("name")+AppLanguage.text(context," · 효과 편집"," · effects edit"),take.optDouble("seconds"),false,"",extras);
            return id;
        }finally{
            tmp.delete();raw.delete();fixed.delete();
            if(!published){store.file(id,".raw.wav").delete();store.file(id,".fixed.wav").delete();}
        }
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
