package pro.livemic.studio;

import android.content.Context;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class TakeStore {
    public final File directory;
    private final Context context;

    public TakeStore(Context c) {
        this(c, true);
    }

    public TakeStore(Context c, boolean recoverFiles) {
        context = c.getApplicationContext();
        directory = new File(c.getFilesDir(), "takes");
        directory.mkdirs();
        if(recoverFiles)recoverAsync(Runnable::run,null);
    }

    public boolean recovering(){return TakeRecovery.recovering(this);}
    public String recoveryFailure(){return TakeRecovery.failure(this);}
    public boolean isRecovering(String id){return TakeRecovery.busy(this,id);}
    public void recoverAsync(java.util.concurrent.Executor completionExecutor,Runnable completed){TakeRecovery.request(this,null,completionExecutor,completed);}
    void queueRecovery(String id){TakeRecovery.request(this,id,Runnable::run,null);}
    public void awaitRecovery()throws Exception{TakeRecovery.await(this);}
    TakeRecovery.Lease lease(String id)throws IOException{return TakeRecovery.lease(this,id);}
    void recoverOne(String id)throws Exception{
        File metadata=file(id,".json");boolean existing=metadata.exists()||file(id,".json.bak").exists();if(existing&&!RecordingIntegrity.pending(this,id)){read(id);return;}
        JSONObject recovered=existing?read(id):RecordingIntegrity.readState(this,id);
        if("deleted-by-user".equals(recovered.optString("sourceRetention"))&&!recovered.optBoolean("integrityRepairPending")){RecordingIntegrity.cleanup(this,id);return;}
        if(!existing){
            recovered.put("id",id).put("name",AppLanguage.text(context,"복구된 테이크","Recovered take")).put("created",System.currentTimeMillis()).put("video",false)
                    .put("warning",AppLanguage.text(context,"중단된 녹음을 복구했습니다.","Recovered an interrupted recording."));
            if(!recovered.has("syncOffsetMs"))recovered.put("sourceEditable",false).put("vocalStemEditable",false).put("sourceEditReason","unknown-interrupted-routing");
        }
        RecordingIntegrity.finish(context,this,id,recovered,()->write(id,recovered),true);
    }

    public void save(String id, String name, double seconds, boolean video, String warning) throws Exception {
        save(id, name, seconds, video, warning, null);
    }

    public void save(String id, String name, double seconds, boolean video, String warning, JSONObject extras) throws Exception {
        JSONObject j = new JSONObject()
                .put("id", id)
                .put("name", name)
                .put("seconds", seconds)
                .put("created", System.currentTimeMillis())
                .put("video", video)
                .put("warning", warning);
        if (extras != null) {
            Iterator<String> keys = extras.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                j.put(k, extras.get(k));
            }
        }
        try(TakeRecovery.Lease ignored=lease(id)){
            if(RecordingIntegrity.pending(this,id))RecordingIntegrity.finish(context,this,id,j,()->write(id,j),false);
            else write(id,j);
        }
    }

    public JSONObject read(String id) throws Exception {
        return new JSONObject(new String(new android.util.AtomicFile(file(id,".json")).readFully(),StandardCharsets.UTF_8));
    }

    public void finishVideo(String id, double seconds, String warning, File original) throws Exception {
        JSONObject meta = read(id);
        meta.put("videoSeconds",seconds).put("seconds",meta.has("audioSeconds")?meta.optDouble("audioSeconds"):seconds).put("video", true).put("warning", warning);
        update(id, meta);
        // A metadata failure must leave both the original and the finished MP4 intact.
        original.delete();
    }

    public void update(String id, JSONObject j) throws Exception {
        try(TakeRecovery.Lease ignored=lease(id)){write(id,j);}
    }

    private void write(String id, JSONObject j) throws Exception {
        directory.mkdirs();File target=file(id,".json");if(target.isDirectory())throw new IOException("Metadata path is not a file");
        android.util.AtomicFile atomic=new android.util.AtomicFile(target);FileOutputStream out=atomic.startWrite();
        try{out.write(j.toString().getBytes(StandardCharsets.UTF_8));out.getFD().sync();atomic.finishWrite(out);}catch(Exception e){atomic.failWrite(out);throw e;}
    }

    public List<JSONObject> list() {
        List<JSONObject> list = new ArrayList<>();
        File[] fs = directory.listFiles((d, n) -> n.endsWith(".json"));
        if (fs != null) {
            for (File f : fs) {
                try {
                    list.add(new JSONObject(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8)));
                } catch (Exception ignored) {
                }
            }
        }
        list.sort((a, b) -> Long.compare(b.optLong("created"), a.optLong("created")));
        return list;
    }

    public void rename(JSONObject j, String name) throws Exception {
        j.put("name", name);
        update(j.getString("id"), j);
    }

    public boolean delete(String id) {
        if(RecordingManager.isWritingId(id))return false;
        try(TakeRecovery.Lease ignored=lease(id)){for (String ext : new String[]{".wav", ".mid", ".mp4", ".json", ".video.mp4", ".aac.m4a", ".mp4.pending.mp4", ".raw.wav", ".fixed.wav", ".vocal.wav", ".raw.wav.delete-pending", ".fixed.wav.delete-pending", ".vocal.wav.delete-pending"}) {
            new File(directory, id + ext).delete();
        }
        RecordingIntegrity.cleanup(this,id);new android.util.AtomicFile(file(id,".json")).delete();file(id,".tmp").delete();
        for(String suffix:RecordingIntegrity.SUFFIXES){file(id,suffix+".recovery-original").delete();file(id,suffix+".recovery-new").delete();}
        return true;}catch(IOException busy){return false;}
    }

    public void deleteSources(String id)throws Exception {
        boolean repair;
        // Consistent ordering with stop(): RecordingManager monitor, then per-take lease.
        synchronized(RecordingManager.class){try(TakeRecovery.Lease ignored=lease(id)){
            TakeStorage.deleteSources(this,id);repair=read(id).optBoolean("integrityRepairPending");
            if(!repair)RecordingIntegrity.cleanup(this,id);
            for(String suffix:new String[]{".raw.wav",".fixed.wav",".vocal.wav"}){file(id,suffix+".recovery-original").delete();file(id,suffix+".recovery-new").delete();}
        }}
        if(repair)queueRecovery(id);
    }

    public File file(String id, String ext) {
        return new File(directory, id + ext);
    }
}
