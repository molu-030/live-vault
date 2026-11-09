package pro.livemic.studio;

import android.content.Context;
import org.json.JSONObject;
import java.io.*;
import java.util.*;

/** Explicit source retention and deletion. Existing sources are never removed automatically. */
final class TakeStorage {
    static final long RESERVE_BYTES=64L*1024*1024;
    static final String PREFERENCE="retainTakeSources";
    static final String[] SOURCES={".raw.wav",".fixed.wav",".vocal.wav"};
    static boolean retainSources(Context context){return context.getSharedPreferences("MainActivity",0).getBoolean(PREFERENCE,true);}
    static boolean setRetainSources(Context context,boolean retain){
        synchronized(RecordingManager.class){
            if(RecordingManager.active||AudioService.waiting())return false;
            context.getSharedPreferences("MainActivity",0).edit().putBoolean(PREFERENCE,retain).apply();return true;
        }
    }
    static int sampleRate(float[] stats){int rate=stats.length>6?(int)stats[6]:48000;return rate>=8000&&rate<=192000?rate:48000;}
    static long minuteBytes(boolean sources,int rate,boolean video){
        int actualRate=rate>=8000&&rate<=192000?rate:48000;
        return actualRate*60L*(4+(sources?20:0))+(video?60_000_000L:0);
    }
    static long requiredBytes(boolean sources,int rate,boolean video){return RESERVE_BYTES+minuteBytes(sources,rate,video);}
    static long available(Context context){return Math.max(0,context.getFilesDir().getUsableSpace());}
    static String startProblem(Context context,boolean video){return startProblem(context,available(context),retainSources(context),sampleRate(NativeAudio.stats()),video);}
    static String startProblem(Context context,long available,boolean sources,int rate,boolean video){
        long required=requiredBytes(sources,rate,video);
        if(available>=required)return "";
        return String.format(Locale.US,AppLanguage.text(context,
                "저장 공간이 부족해 녹음을 시작하지 않았습니다. 여유 %.0f MB / 필요 %.0f MB. 기존 원음을 직접 정리하거나 녹음 설정에서 다음 녹음의 원음 보관을 끄세요.",
                "Recording did not start: insufficient storage. %.0f MB available / %.0f MB needed. Manage existing sources, or turn off source retention for future recordings in Recording settings."),available/1e6,required/1e6);
    }
    static long estimatedCopyBytes(TakeStore store,JSONObject take,boolean video){
        String id=take.optString("id");double estimate=(double)sourceBytes(store,id)+store.file(id,".wav").length();
        if(video){double seconds=take.optDouble("seconds",0);if(!Double.isFinite(seconds)||seconds<0)seconds=0;
            estimate+=store.file(id,".mp4").length()+seconds*24_000*2;
        }
        return estimate>=Long.MAX_VALUE-RESERVE_BYTES?Long.MAX_VALUE:(long)Math.ceil(estimate)+RESERVE_BYTES;
    }
    static String copyProblem(Context context,TakeStore store,JSONObject take,boolean video){return copyProblem(context,available(context),estimatedCopyBytes(store,take,video));}
    static String copyProblem(Context context,long available,long estimate){
        if(available>=estimate)return "";
        return String.format(Locale.US,AppLanguage.text(context,"편집 사본을 저장할 공간이 부족합니다. 여유 %.0f MB / 예상 필요 %.0f MB. 기존 파일은 그대로 유지됩니다. 보관함에서 필요 없는 원음을 직접 정리하세요.",
                "Not enough space for an edited copy. %.0f MB available / approximately %.0f MB needed. Existing files are kept. Manage unwanted sources in Library."),available/1e6,estimate/1e6);
    }
    static long usage(File file){if(file.isFile())return file.length();long total=0;File[] files=file.listFiles();if(files!=null)for(File child:files)total+=usage(child);return total;}
    static long sourceBytes(TakeStore store,String id){long bytes=0;for(String suffix:SOURCES){bytes+=store.file(id,suffix).length();bytes+=store.file(id,suffix+".delete-pending").length();}return bytes;}
    static boolean hasSources(TakeStore store,String id){for(String suffix:SOURCES)if(store.file(id,suffix).exists()||store.file(id,suffix+".delete-pending").exists())return true;return false;}
    static String spaceLabel(Context context,long used,long available){return String.format(Locale.US,AppLanguage.text(context,"녹음 보관함 %.1f MB · 기기 여유 %.1f GB","Recording library %.1f MB · Device free %.1f GB"),used/1e6,available/1e9);}

    static void deleteSources(TakeStore store,String id)throws Exception{
        if(!id.matches("[A-Za-z0-9_-]+"))throw new IOException("Invalid take ID");
        synchronized(RecordingManager.class){
            if(RecordingManager.active||AudioService.waiting())throw new IOException("Finish recording or the countdown before deleting sources.");
            JSONObject original=store.read(id),updated=new JSONObject(original.toString());List<File[]> moved=new ArrayList<>();
            try{
                for(String suffix:SOURCES){File source=store.file(id,suffix),pending=store.file(id,suffix+".delete-pending");
                    if(source.exists()){if(pending.exists()||!source.renameTo(pending))throw new IOException("Could not prepare source deletion");moved.add(new File[]{source,pending});}
                }
                updated.put("sourceRetention","deleted-by-user").put("sourceDeletionPending",true).put("sourcesDeletedAt",System.currentTimeMillis())
                        .put("sourceRawRetained",false).put("sourceFixedRetained",false).put("sourceVocalRetained",false)
                        .put("sourceEditable",false).put("vocalStemEditable",false).put("cleanSources",false).put("sourceFixed",false)
                        .put("sourceEditReason","deleted-by-user");
                store.update(id,updated);
            }catch(Exception e){for(File[] pair:moved)pair[1].renameTo(pair[0]);throw e;}
            boolean removed=true;for(String suffix:SOURCES){File pending=store.file(id,suffix+".delete-pending");if(pending.exists()&&!pending.delete())removed=false;}
            updated.put("sourceDeletionPending",!removed);store.update(id,updated);
            if(!removed)throw new IOException("Some source files could not be deleted. Try deleting sources again.");
        }
    }
}
