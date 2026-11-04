package pro.livemic.studio;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.EditText;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.json.JSONObject;

/** Save a detached composer snapshot through Android's document provider. */
final class ComposerExchange {
    private static final int SAVE_EXPORT=40;
    private static final String STATE="composer.export.pending";
    private final MainActivity activity;
    private String pendingId;
    private boolean importPending;
    private static final Object EXPORT_LOCK=new Object();
    private static int pendingExports;
    private static String exportFailures="", exitExportFailures="";
    private static long exitFailureGeneration=-1;
    private static final java.util.ArrayList<java.util.function.Consumer<String>> exportWaiters=new java.util.ArrayList<>();
    private static void beginExport(){synchronized(EXPORT_LOCK){if(pendingExports++==0)exportFailures="";}}
    private static void endExport(String error){
        java.util.ArrayList<java.util.function.Consumer<String>> ready=null;String failures="";
        synchronized(EXPORT_LOCK){
            if(error!=null&&!error.isEmpty()){
                exportFailures+=error+"\n";
                if(ExitCoordinator.isExitRequested()){
                    if(exitFailureGeneration!=ExitCoordinator.generation()){exitFailureGeneration=ExitCoordinator.generation();exitExportFailures="";}
                    exitExportFailures+=error+"\n";
                }
            }
            if(--pendingExports==0){ready=new java.util.ArrayList<>(exportWaiters);exportWaiters.clear();failures=exportFailures;}
        }
        if(ready!=null){final String result=failures;final java.util.ArrayList<java.util.function.Consumer<String>> callbacks=ready;
            new android.os.Handler(android.os.Looper.getMainLooper()).post(()->{for(java.util.function.Consumer<String> callback:callbacks)callback.accept(result);});}
    }
    static void afterPendingExports(java.util.function.Consumer<String> done){
        final String failure;
        synchronized(EXPORT_LOCK){if(pendingExports!=0){exportWaiters.add(done);return;}
            failure=exitFailureGeneration==ExitCoordinator.generation()?exitExportFailures:"";}
        done.accept(failure);
    }

    ComposerExchange(MainActivity activity){this.activity=activity;}
    void saveState(Bundle out){if(pendingId!=null)out.putString(STATE,pendingId);out.putBoolean(STATE+".import",importPending);}
    void restoreState(Bundle in){if(in!=null){importPending=in.getBoolean(STATE+".import",false);String id=in.getString(STATE);if(id!=null&&id.matches("[0-9a-f-]{36}")&&file(id).isFile())pendingId=id;}}
    private File file(String id){return new File(activity.getCacheDir(),"composer-export-"+id+".json");}
    void showExport(ComposerProject project){
        if(!ownerAlive())return;
        if(pendingId!=null||importPending){activity.toast(activity.tr("파일 선택을 먼저 완료해 주세요.","Finish choosing a file first."));return;}
        final String projectJson,projectName;
        try{projectJson=project.json().toString();projectName=project.name;}catch(Exception e){activity.toast(activity.tr("프로젝트를 내보낼 수 없습니다.","Could not prepare the project."));return;}
        EditText goal=new EditText(activity);
        goal.setHint(activity.tr("예: 더 따뜻한 재즈 느낌 (선택)","e.g. a warmer jazz feel (optional)"));
        goal.setMinLines(3);goal.setMaxLines(6);goal.setSingleLine(false);goal.setVerticalScrollBarEnabled(true);goal.setGravity(android.view.Gravity.TOP|android.view.Gravity.START);
        goal.setTypeface(StudioTheme.body(activity));goal.setTextColor(MainActivity.TEXT);goal.setHintTextColor(MainActivity.MUTED);goal.setBackground(StudioTheme.background(activity,StudioTheme.RAISED,12));int pad=activity.dp(12);goal.setPadding(pad,pad,pad,pad);
        StudioDialogs.alert(activity).setTitle(activity.tr("AI용 내보내기","Export for AI"))
            .setMessage(activity.tr("ChatGPT 등 AI에 ZIP을 첨부하고 원하는 수정을 요청하세요. 오디오는 포함되지 않습니다.","Attach the ZIP to ChatGPT or another AI and request the changes you want. Audio is not included."))
            .setView(goal).setNegativeButton(activity.tr("취소","Cancel"),null)
            .setPositiveButton(activity.tr("파일로 저장","Save to files"),(dialog,which)->{
                if(!ownerAlive()||pendingId!=null)return;
                String id=UUID.randomUUID().toString();File snapshot=file(id);
                try(FileOutputStream out=new FileOutputStream(snapshot)) {out.write(new JSONObject().put("project",new JSONObject(projectJson)).put("request",goal.getText().toString()).toString().getBytes(StandardCharsets.UTF_8));}
                catch(Exception e){snapshot.delete();activity.toast(activity.tr("프로젝트를 준비하지 못했습니다.","Could not prepare the project."));return;}
                pendingId=id;
                Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/zip").putExtra(Intent.EXTRA_TITLE,filename(projectName));
                try{activity.startActivityForResult(intent,SAVE_EXPORT);}catch(Exception e){pendingId=null;snapshot.delete();activity.toast(activity.tr("파일 저장 앱을 열 수 없습니다.","Could not open the file picker."));}
            }).show();
    }
    static String filename(String name){String cleaned=name==null?"":name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]","_").trim();if(cleaned.isEmpty()||cleaned.equals(".")||cleaned.equals(".."))cleaned="LiveVault";if(cleaned.length()>80)cleaned=cleaned.substring(0,80);return cleaned+"-livevault.zip";}
    private boolean ownerAlive(){return !activity.exiting()&&!activity.isFinishing()&&!activity.isDestroyed();}
    private boolean importBlocked(){return activity.exiting()||!activity.composer.ready||activity.composer.capturing||activity.settingsLocked()||activity.awaitingMicPermission||activity.recordPermissionPending||activity.pendingCapture||activity.pendingAction!=0||activity.starting||activity.busy||(activity.camera!=null&&activity.camera.recording);}
    void showImport(){
        if(!ownerAlive())return;
        if(importPending||pendingId!=null)return;
        if(importBlocked()){activity.toast(activity.tr("녹음이나 예약을 마친 뒤 가져오세요.","Finish recording or scheduled actions before importing."));return;}
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json");importPending=true;
        try{activity.startActivityForResult(intent,41);}catch(Exception e){importPending=false;activity.toast(activity.tr("파일 선택 앱을 열 수 없습니다.","Could not open the file picker."));}
    }
    private void importFile(Uri uri){
        new Thread(()->{try{
            byte[] bytes;try(InputStream in=activity.getContentResolver().openInputStream(uri);ByteArrayOutputStream out=new ByteArrayOutputStream()){if(in==null)throw new IOException("No input stream");byte[] buf=new byte[8192];int n,total=0;while((n=in.read(buf))!=-1){if(activity.exiting())return;total+=n;if(total>ComposerLlmImport.MAX_BYTES)throw new ComposerLlmImport.Invalid("8 MiB limit");out.write(buf,0,n);}bytes=out.toByteArray();}
            if(activity.exiting())return;
            int start=bytes.length>=3&&(bytes[0]&255)==239&&(bytes[1]&255)==187&&(bytes[2]&255)==191?3:0;
            String json=StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes,start,bytes.length-start)).toString();
            ComposerProject imported=ComposerLlmImport.parse(json);activity.runOnUiThread(()->{if(ownerAlive())preview(imported);});
        }catch(Exception e){String detail=e instanceof ComposerLlmImport.Invalid?e.getMessage():"JSON";activity.runOnUiThread(()->{if(ownerAlive())StudioDialogs.alert(activity).setTitle(activity.tr("가져올 수 없습니다","Cannot import")).setMessage(activity.tr("8 MiB 이하의 일반 프로젝트 JSON을 선택하세요. 오류 위치: ","Choose plain project JSON up to 8 MiB. Invalid field: ")+detail).setPositiveButton(activity.tr("확인","OK"),null).show();});}},"composer-import").start();
    }
    private void preview(ComposerProject p){
        if(!ownerAlive())return;
        int tracks=0,notes=0;java.util.Set<String> ids=new java.util.HashSet<>();for(ComposerProject.Part part:p.parts){for(TrackConfig t:part.tracks)ids.add(t.id);for(VoicePhrase ph:part.phrases)notes+=ph.notes.size();}tracks=ids.size();
        String[] scales=AppLanguage.english(activity)?new String[]{"Major","Natural minor","Harmonic minor","Dorian","Mixolydian"}:MusicTheory.SCALES,meters={"4/4","3/4","6/8"};String message=p.name+"\n"+MusicTheory.ROOTS[p.key]+" "+scales[p.scale]+" · "+p.bpm+" BPM · "+meters[p.meter]+"\n"+activity.tr("마디: ","Bars: ")+p.totalBars()+" · "+activity.tr("파트: ","Parts: ")+p.parts.size()+" · "+activity.tr("트랙: ","Tracks: ")+tracks+" · "+activity.tr("음표: ","Notes: ")+notes+"\n\n"+activity.tr("현재 프로젝트를 보관함에 저장한 뒤 새 복사본으로 엽니다.","The current project will be saved to your library, then this will open as a new copy.");
        StudioDialogs.alert(activity).setTitle(activity.tr("가져오기 미리보기","Import preview")).setMessage(message).setNegativeButton(activity.tr("취소","Cancel"),null).setPositiveButton(activity.tr("새 복사본 열기","Open new copy"),(d,w)->applyImport(p)).show();
    }
    private void applyImport(ComposerProject imported){
        if(!ownerAlive())return;
        if(importBlocked()){activity.toast(activity.tr("녹음이나 예약을 마친 뒤 가져오세요.","Finish recording or scheduled actions before importing."));return;}
        final String current;final ComposerProject backup;try{current=activity.composer.project.json().toString();backup=ComposerProject.read(new JSONObject(current));backup.id=UUID.randomUUID().toString();}catch(Exception e){activity.toast(activity.tr("현재 프로젝트를 저장하지 못했습니다.","Could not save the current project."));return;}
        activity.composer.worker.execute(()->{try{activity.composer.store.write(backup,false);activity.runOnUiThread(()->{try{if(!ownerAlive())return;if(importBlocked()||!current.equals(activity.composer.project.json().toString())){activity.toast(activity.tr("프로젝트가 변경되어 가져오기를 취소했습니다.","Import cancelled because the current project changed."));return;}imported.id=UUID.randomUUID().toString();activity.composer.open(imported);activity.toast(activity.tr("새 프로젝트를 열었습니다.","Opened the new project."));}catch(Exception e){activity.toast(activity.tr("가져오기에 실패했습니다.","Import failed."));}});}catch(Exception e){activity.runOnUiThread(()->{if(ownerAlive())activity.toast(activity.tr("현재 프로젝트 저장에 실패했습니다.","Could not save the current project."));});}});
    }
    boolean onActivityResult(int code,int result,Intent data){
        if(activity.exiting())return code==41||code==SAVE_EXPORT;
        if(code==41){boolean expected=importPending;importPending=false;if(expected&&result==Activity.RESULT_OK&&data!=null&&data.getData()!=null)importFile(data.getData());return true;}
        if(code!=SAVE_EXPORT)return false;
        final String id=pendingId;pendingId=null;
        if(id==null){if(result==Activity.RESULT_OK)activity.toast(activity.tr("내보내기를 다시 시작해 주세요.","Please start the export again."));return true;}
        final File snapshot=file(id);
        if(result!=Activity.RESULT_OK||data==null||data.getData()==null){snapshot.delete();return true;}
        final Uri uri=data.getData();
        // A dedicated thread survives Activity executor shutdown during recreation.
        beginExport();
        new Thread(()->{String failure="";try{byte[] bytes;try(FileInputStream input=new FileInputStream(snapshot);ByteArrayOutputStream buffer=new ByteArrayOutputStream()){byte[] block=new byte[8192];int n;while((n=input.read(block))!=-1)buffer.write(block,0,n);bytes=buffer.toByteArray();}JSONObject saved=new JSONObject(new String(bytes,StandardCharsets.UTF_8));ComposerProject detached=ComposerProject.read(saved.getJSONObject("project"));try(OutputStream stream=activity.getContentResolver().openOutputStream(uri,"wt")){if(stream==null)throw new IOException("Document provider returned no output stream");ComposerLlmExport.write(detached,saved.optString("request"),stream);}activity.runOnUiThread(()->{if(ownerAlive())activity.toast(activity.tr("프로젝트 ZIP을 저장했습니다.","Project ZIP saved."));});}catch(Exception e){failure=activity.tr("내보내기에 실패했습니다: ","Export failed: ")+(e.getMessage()==null?e.toString():e.getMessage());android.util.Log.e("ComposerExchange","Export failed",e);activity.runOnUiThread(()->{if(ownerAlive())activity.toast(activity.tr("내보내기에 실패했습니다.","Export failed."));});}finally{snapshot.delete();endExport(failure);}},"composer-export").start();
        return true;
    }
}
