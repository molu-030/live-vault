package pro.livemic.studio;

import android.content.Context;
import android.util.AtomicFile;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Durable writer watermarks and rollback-safe repair. Large repairs run only in TakeRecovery. */
final class RecordingIntegrity {
    static final String[] NAMES={"mix","raw","fixed","vocal"};
    static final String[] SUFFIXES={".wav",".raw.wav",".fixed.wav",".vocal.wav"};
    interface Commit {void run()throws Exception;}
    static File journal(TakeStore store,String id){return store.file(id,".wav.journal");}
    static File state(TakeStore store,String id){return store.file(id,".recording-state");}
    static boolean pending(TakeStore store,String id){return journal(store,id).exists()||state(store,id).exists();}
    static void writeState(TakeStore store,String id,JSONObject metadata)throws Exception{
        AtomicFile file=new AtomicFile(state(store,id));FileOutputStream out=file.startWrite();
        try{out.write(metadata.toString().getBytes(StandardCharsets.UTF_8));out.getFD().sync();file.finishWrite(out);}catch(Exception e){file.failWrite(out);throw e;}
    }
    static JSONObject readState(TakeStore store,String id){try{return new JSONObject(new String(new AtomicFile(state(store,id)).readFully(),StandardCharsets.UTF_8));}catch(Exception e){return new JSONObject();}}
    static void cleanup(TakeStore store,String id){new AtomicFile(state(store,id)).delete();journal(store,id).delete();store.file(id,".wav.journal.tmp").delete();}
    static final class Wave {
        final int rate,channels,bits,format,frameBytes;final long frames,discarded;final boolean ahead,repair;
        Wave(int rate,int channels,int bits,int format,long frames,long discarded,boolean ahead,boolean repair){this.rate=rate;this.channels=channels;this.bits=bits;this.format=format;frameBytes=channels*(bits/8);this.frames=frames;this.discarded=discarded;this.ahead=ahead;this.repair=repair;}
        JSONObject json()throws Exception{return new JSONObject().put("sampleRate",rate).put("channels",channels).put("bitsPerSample",bits).put("format",format).put("confirmedFrames",frames).put("discardedBytes",discarded).put("watermarkAheadOfFile",ahead);}
    }
    private static long uint(RandomAccessFile in)throws IOException{return Integer.toUnsignedLong(Integer.reverseBytes(in.readInt()));}
    private static int ushort(RandomAccessFile in)throws IOException{return Short.toUnsignedInt(Short.reverseBytes(in.readShort()));}
    private static String tag(RandomAccessFile in)throws IOException{byte[] bytes=new byte[4];in.readFully(bytes);return new String(bytes,StandardCharsets.US_ASCII);}
    private static long integer(JSONObject value,String key,long maximum)throws IOException{
        Object number=value.opt(key);if(!(number instanceof Number))throw new IOException("Invalid journal field: "+key);
        double exact=((Number)number).doubleValue();long integral=((Number)number).longValue();
        if(!Double.isFinite(exact)||exact!=integral||integral<0||integral>maximum)throw new IOException("Invalid journal field: "+key);return integral;
    }
    static Wave inspect(File file,String name,JSONObject watermark)throws Exception{
        try(RandomAccessFile in=new RandomAccessFile(file,"r")){
            if(in.length()>0xffffffffL+8)throw new IOException("WAV exceeds RIFF size limit");
            if(in.length()<44||!"RIFF".equals(tag(in)))throw new IOException("Invalid RIFF header");long riff=uint(in);
            if(!"WAVE".equals(tag(in))||!"fmt ".equals(tag(in))||uint(in)!=16)throw new IOException("Unsupported WAV layout");
            int format=ushort(in),channels=ushort(in);long rate=uint(in),byteRate=uint(in);int align=ushort(in),bits=ushort(in);
            if(!"data".equals(tag(in)))throw new IOException("Missing WAV data chunk");long data=uint(in);
            int expectedChannels="raw".equals(name)?1:2,expectedBits="mix".equals(name)?16:32,expectedFormat="mix".equals(name)?1:3;
            if(rate<8000||rate>192000||channels!=expectedChannels||bits!=expectedBits||format!=expectedFormat||align!=channels*(bits/8)||byteRate!=rate*align)throw new IOException("Invalid WAV sample format");
            long physical=in.length()-44,physicalFrames=physical/align,frames=physicalFrames;boolean ahead=false;
            if(watermark!=null){
                if(integer(watermark,"rate",192000)!=rate||integer(watermark,"channels",2)!=channels||integer(watermark,"bits",32)!=bits||integer(watermark,"format",3)!=format||integer(watermark,"bytesPerFrame",8)!=align)throw new IOException("Journal and WAV formats differ");
                long committed=integer(watermark,"frames",(0xffffffffL-36)/align);if(committed<0||committed>(0xffffffffL-36)/align)throw new IOException("Invalid confirmed frame count");
                ahead=committed>physicalFrames;frames=Math.min(committed,physicalFrames);
            }
            if(frames>(0xffffffffL-36)/align)throw new IOException("WAV frame count exceeds RIFF limit");
            return new Wave((int)rate,channels,bits,format,frames,physical-frames*align,ahead,physical!=frames*align||data!=frames*align||riff!=frames*align+36);
        }
    }
    private static File staged(File original){return new File(original.getPath()+".recovery-new");}
    private static File backup(File original){return new File(original.getPath()+".recovery-original");}
    private static void checkCancelled()throws IOException{if(ExitCoordinator.isExitRequested()||Thread.currentThread().isInterrupted())throw new IOException("Recovery cancelled");}
    private static void repairCopy(File original,Wave wave)throws Exception{
        checkCancelled();long length=44+wave.frames*wave.frameBytes;
        try(InputStream in=new FileInputStream(original);FileOutputStream out=new FileOutputStream(staged(original))){
            byte[] buffer=new byte[65536];long remaining=length;
            while(remaining>0){checkCancelled();int n=in.read(buffer,0,(int)Math.min(buffer.length,remaining));if(n<0)throw new EOFException("WAV changed during repair");out.write(buffer,0,n);remaining-=n;}
            out.getFD().sync();
        }
        try(RandomAccessFile out=new RandomAccessFile(staged(original),"rw")){out.seek(4);out.writeInt(Integer.reverseBytes((int)(length-8)));out.seek(40);out.writeInt(Integer.reverseBytes((int)(length-44)));out.getFD().sync();}
    }
    static void restoreInterruptedRepair(TakeStore store,String id,boolean sourcesRemoved)throws Exception{
        for(int i=0;i<SUFFIXES.length;i++){File original=store.file(id,SUFFIXES[i]),old=backup(original);
            if(i>0&&sourcesRemoved){old.delete();staged(original).delete();continue;}
            if(old.exists())Files.move(old.toPath(),original.toPath(),StandardCopyOption.REPLACE_EXISTING);staged(original).delete();}
    }
    /** allowRepair=false is the synchronous stop path: inspect/commit only, never copy or truncate audio. */
    static void finish(Context context,TakeStore store,String id,JSONObject metadata,Commit commit,boolean allowRepair)throws Exception{
        boolean sourcesRemoved="deleted-by-user".equals(metadata.optString("sourceRetention"))||"not-requested".equals(metadata.optString("sourceRetention"));
        if(allowRepair){checkCancelled();restoreInterruptedRepair(store,id,sourcesRemoved);}
        JSONObject journal=null;
        if(journal(store,id).exists()){
            if(journal(store,id).length()>65536)throw new IOException("Invalid journal size");
            journal=new JSONObject(new String(Files.readAllBytes(journal(store,id).toPath()),StandardCharsets.UTF_8));
            if(integer(journal,"version",1)!=1||!journal.has("mix"))throw new IOException("Unsupported recording journal");if(!(journal.opt("done") instanceof Boolean))throw new IOException("Invalid journal completion flag");
        }
        Map<File,Wave> plans=new LinkedHashMap<>();Map<String,Wave> waves=new HashMap<>();JSONObject report=new JSONObject();boolean sourceFailure=false,trimmed=false,repair=false;
        for(int i=0;i<NAMES.length;i++){
            if(i>0&&sourcesRemoved)continue;File original=store.file(id,SUFFIXES[i]);
            if(!original.exists()){if(i==0)throw new IOException("Missing recorded mix");if(journal!=null&&journal.has(NAMES[i])){sourceFailure=true;report.put(NAMES[i],new JSONObject().put("error","Committed source is missing"));}continue;}
            try{
                JSONObject mark=journal==null?null:journal.optJSONObject(NAMES[i]);if(journal!=null&&mark==null)throw new IOException("No source watermark");
                Wave wave=inspect(original,NAMES[i],mark);if(i==0&&wave.frames==0)throw new IOException("No complete recorded frames");
                waves.put(NAMES[i],wave);plans.put(original,wave);report.put(NAMES[i],wave.json());trimmed|=wave.discarded>0||wave.ahead;repair|=wave.repair;
            }catch(Exception e){if(i==0)throw e;sourceFailure=true;report.put(NAMES[i],new JSONObject().put("error",e.getMessage()).put("originalPreserved",true));}
        }
        Wave mix=waves.get("mix"),raw=waves.get("raw"),fixed=waves.get("fixed"),vocal=waves.get("vocal");
        boolean rawOkay=compatible(raw,fixed),vocalOkay=compatible(vocal,fixed);
        boolean rawEditable=metadata.optBoolean("preRepairSourceEditable",metadata.optBoolean("sourceEditable"));
        boolean vocalEditable=metadata.optBoolean("preRepairVocalEditable",metadata.optBoolean("vocalStemEditable",true));
        metadata.put("seconds",mix.frames/(double)mix.rate).put("audioSeconds",mix.frames/(double)mix.rate).put("recordingIntegrity",report)
                .put("recoveryJournal",journal!=null).put("unconfirmedTailDiscarded",trimmed).put("cleanSources",!sourcesRemoved&&rawOkay)
                .put("sourceEditable",!sourcesRemoved&&rawOkay&&rawEditable).put("vocalStemEditable",!sourcesRemoved&&vocalOkay&&vocalEditable);
        for(int i=1;i<NAMES.length;i++)metadata.put("source"+NAMES[i].substring(0,1).toUpperCase(Locale.ROOT)+NAMES[i].substring(1)+"Retained",!sourcesRemoved&&store.file(id,SUFFIXES[i]).length()>44);
        if(fixed!=null)metadata.put("sourceFixed",true);
        if(trimmed||sourceFailure)appendWarning(metadata,AppLanguage.text(context,"확정된 오디오 프레임까지만 저장했습니다. MIDI·영상·전송 시간과 길이가 다를 수 있습니다. 손상된 원음은 그대로 보존했습니다.","Saved only confirmed audio frames. Duration can differ from MIDI, video or transport time. Damaged sources were kept unchanged."));
        if(!allowRepair&&repair){
            metadata.put("integrityRepairPending",true).put("preRepairSourceEditable",rawEditable).put("preRepairVocalEditable",vocalEditable).put("sourceEditable",false).put("vocalStemEditable",false);
            appendWarning(metadata,AppLanguage.text(context,"파일을 백그라운드에서 복구 중입니다. 완료 후 재생·편집하세요.","Files are being repaired in the background. Wait before playback or editing."));commit.run();return;
        }
        clearPendingWarning(metadata);
        metadata.put("integrityRepairPending",false);metadata.remove("preRepairSourceEditable");metadata.remove("preRepairVocalEditable");
        List<File> replaced=new ArrayList<>();
        try{
            for(Map.Entry<File,Wave> entry:plans.entrySet())if(entry.getValue().repair){if(!allowRepair)throw new IOException("Repair requires background worker");repairCopy(entry.getKey(),entry.getValue());}
            for(File original:plans.keySet())if(plans.get(original).repair){checkCancelled();Files.move(original.toPath(),backup(original).toPath());replaced.add(original);Files.move(staged(original).toPath(),original.toPath());}
            if(allowRepair)checkCancelled();commit.run();
        }catch(Exception e){for(int i=replaced.size()-1;i>=0;i--){File original=replaced.get(i);try{Files.move(backup(original).toPath(),original.toPath(),StandardCopyOption.REPLACE_EXISTING);}catch(Exception rollback){e.addSuppressed(rollback);}}throw e;}
        finally{for(File original:plans.keySet())staged(original).delete();}
        for(File original:plans.keySet())backup(original).delete();cleanup(store,id);
    }
    private static void clearPendingWarning(JSONObject metadata)throws Exception{
        String ko="파일을 백그라운드에서 복구 중입니다. 완료 후 재생·편집하세요.",en="Files are being repaired in the background. Wait before playback or editing.";
        StringBuilder retained=new StringBuilder();for(String line:metadata.optString("warning").split("\n"))if(!line.equals(ko)&&!line.equals(en)&&!line.isEmpty()){if(retained.length()>0)retained.append('\n');retained.append(line);}
        metadata.put("warning",retained.toString());
    }
    private static boolean compatible(Wave a,Wave b){return a!=null&&b!=null&&a.rate==b.rate&&a.frames==b.frames&&a.frames>0;}
    private static void appendWarning(JSONObject metadata,String warning)throws Exception{String previous=metadata.optString("warning");if(!previous.contains(warning))metadata.put("warning",previous+(previous.isEmpty()?"":"\n")+warning);}
}
