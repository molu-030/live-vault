package pro.livemic.studio;

import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONObject;

/** Local edits use independent sources; closing cancels work without replacing a take. */
final class TakeEffectsEditor {
    private final MainActivity a;
    private final JSONObject take;
    private final float[] values;
    private final boolean vocal;
    private final AtomicBoolean cancelled=new AtomicBoolean();
    private final File preview;
    private boolean working;
    private android.app.AlertDialog dialog;
    private TextView status;
    private View progress;
    private Button wavSave,videoSave;
    TakeEffectsEditor(MainActivity activity,JSONObject source){this(activity,source,TakeAudioFiles.vocalMode(activity.store,source));}
    private TakeEffectsEditor(MainActivity activity,JSONObject source,boolean voice){
        a=activity;take=source;vocal=voice;values=TakeAudioFiles.effects(source,a.values,vocal);
        preview=new File(a.getCacheDir(),"vocal-preview-"+System.nanoTime()+".wav");
    }
    void show(){
        if(!(vocal?TakeAudioFiles.stemEditable(a.store,take):TakeAudioFiles.rawEditable(a.store,take))){a.toast(a.tr("이 테이크는 보컬 효과 후편집을 지원하지 않습니다.","This take does not support vocal effect editing."));return;}
        LinearLayout panel=a.formColumn();panel.setPadding(a.dp(StudioTheme.SPACE_16),a.dp(StudioTheme.SPACE_8),a.dp(StudioTheme.SPACE_16),a.dp(StudioTheme.SPACE_8));
        panel.addView(a.text(vocal?a.tr("녹음 당시의 화음·보컬 효과·목소리 악기가 포함된 보컬 트랙에 효과를 추가합니다. 기존 효과와 화음을 제거하거나 다른 화음으로 바꿀 수 없습니다. 반주·보컬 루프는 유지됩니다.","Adds effects to the recorded voice track, including its harmony, vocal effects and voice instruments. Existing effects and harmony cannot be removed or changed to another harmony. Backing and vocal loops are retained.")
                :a.tr("원본은 그대로 두고 새 복사본을 만듭니다. 원음은 기기 AEC·소음 처리 후이며 제거된 소리는 복구할 수 없습니다. 기본 보컬 효과를 전체에 일정하게 적용하므로 녹음 중 효과 변경과 다를 수 있습니다. 고정 믹스의 반주·보컬 루프는 유지됩니다.","Creates a new copy and keeps the original. Sources follow device AEC/noise processing; removed sound cannot be recovered. Basic vocal effects apply uniformly, so live effect changes may differ. Backing and vocal loops in the fixed mix are retained."),12,MainActivity.MUTED));
        panel.addView(a.text(a.tr("일시정지 구간은 제외된 채로 유지되며 잔향은 다시 계산합니다. 비교 중 다른 앱 내 반주는 일시정지하며 자동 재개하지 않습니다.","Paused sections remain omitted; effect tails are recalculated. Comparison pauses other in-app backing and does not resume it automatically."),12,MainActivity.MUTED));
        if(TakeAudioFiles.rawEditable(a.store,take)&&TakeAudioFiles.stemEditable(a.store,take)){
            panel.addView(a.text(a.tr("두 모드는 녹음 당시의 서로 다른 보관 원음을 사용합니다. 모드를 바꾸면 현재 사본의 소리나 다른 모드의 편집 설정을 이어서 처리하지 않습니다.","The two modes use different sources kept from the recording. Switching modes does not process the current copy's sound or carry over edits from the other mode."),12,MainActivity.MUTED));
            panel.addView(a.button(a.tr("편집 원음: ","Effect source: ")+(vocal?a.tr("녹음된 보컬 · 추가 효과","Recorded voice · add effects"):a.tr("마이크 원음 · 효과 재계산","Microphone source · rebuild effects")),()->{
                if(working)return;
                StudioDialogs.choose(a,a.tr("효과를 적용할 원음","Source for effects"),new String[]{a.tr("마이크 원음 · 효과 재계산","Microphone source · rebuild effects"),a.tr("녹음된 보컬 · 추가 효과","Recorded voice · add effects")},vocal?1:0,which->{
                    if((which==1)==vocal)return;dialog.dismiss();a.takeEffectsEditor=new TakeEffectsEditor(a,take,which==1);a.takeEffectsEditor.show();
                });
            }));
        }
        fader(panel,a.tr("보컬 볼륨","Vocal volume"),0,0,2);
        fader(panel,a.tr("리버브 양","Reverb amount"),2,0,1);
        fader(panel,a.tr("리버브 잔향","Reverb decay"),3,.3f,6);
        fader(panel,a.tr("에코 양","Echo level"),4,0,.8f);
        fader(panel,a.tr("에코 간격","Echo time"),5,70,700);
        fader(panel,a.tr("에코 반복","Echo feedback"),6,0,.7f);
        fader(panel,"Low EQ",7,-12,12);fader(panel,"Mid EQ",8,-12,12);fader(panel,"High EQ",9,-12,12);
        android.widget.Switch compressor=new android.widget.Switch(a);compressor.setText(a.tr("컴프레서","Compressor"));compressor.setTextColor(MainActivity.TEXT);StudioTheme.applyTypography(compressor,StudioTheme.TYPE_BODY);compressor.setMinHeight(a.dp(StudioTheme.TOUCH_TARGET));compressor.setPadding(0,a.dp(StudioTheme.SPACE_12),0,a.dp(StudioTheme.SPACE_12));compressor.setChecked(values[10]>.5f);compressor.setOnCheckedChangeListener((v,on)->values[10]=on?1:0);panel.addView(compressor);
        android.widget.Switch noise=new android.widget.Switch(a);noise.setText(a.tr("앱 소음 완화","App noise reduction"));noise.setTextColor(MainActivity.TEXT);StudioTheme.applyTypography(noise,StudioTheme.TYPE_BODY);noise.setMinHeight(a.dp(StudioTheme.TOUCH_TARGET));noise.setPadding(0,a.dp(StudioTheme.SPACE_12),0,a.dp(StudioTheme.SPACE_12));noise.setChecked(values[19]>.5f);noise.setOnCheckedChangeListener((v,on)->values[19]=on?1:0);panel.addView(noise);
        panel.addView(a.button(a.tr("리버브 공간 선택","Reverb space"),()->StudioDialogs.choose(a,a.tr("리버브 공간","Reverb space"),new String[]{"Hall","Room","Plate","Cathedral"},Math.round(values[18]),which->values[18]=which)));
        LinearLayout compare=a.row();
        a.weighted(compare,a.button(a.tr("A · 원본 듣기","A · Listen to original"),()->{if(!working&&prepareListening())a.play(a.store.file(take.optString("id"),".wav"));}),48);
        a.weighted(compare,a.button(a.tr("B · 편집 미리듣기","B · Preview edit"),()->render(false,false)),48);panel.addView(compare);
        boolean video=TakeAudioFiles.videoEditable(a.store,take);
        if(take.optBoolean("video"))panel.addView(a.text(video?a.tr("MP4 저장은 원본 영상과 편집 오디오를 새 복사본으로 결합합니다. WAV 복사본도 함께 유지합니다.","Save MP4 combines the original video with edited audio in a new copy and also keeps the WAV copy.")
                :a.tr("이 영상에는 정확한 싱크 정보가 없어 WAV 복사본만 저장할 수 있습니다. 원본 영상은 유지됩니다.","This video has no exact timing metadata. Save a WAV copy; the original video is retained."),12,MainActivity.MUTED));
        ScrollView scroll=new ScrollView(a);scroll.setTag("take-effects-scroll");scroll.addView(panel);
        LinearLayout outer=a.column();outer.setLayoutParams(new android.view.ViewGroup.LayoutParams(-1,Math.max(a.dp(96),Math.min(a.dp(600),a.getResources().getDisplayMetrics().heightPixels-a.dp(180)))));
        outer.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout footer=a.formColumn();footer.setPadding(a.dp(StudioTheme.SPACE_16),a.dp(StudioTheme.SPACE_8),a.dp(StudioTheme.SPACE_16),a.dp(StudioTheme.SPACE_8));
        progress=new android.widget.ProgressBar(a,null,android.R.attr.progressBarStyleHorizontal);((android.widget.ProgressBar)progress).setIndeterminate(true);progress.setVisibility(View.GONE);footer.addView(progress,new LinearLayout.LayoutParams(-1,a.dp(4)));
        status=a.text("",12,MainActivity.MUTED);status.setMaxLines(2);status.setVisibility(View.GONE);footer.addView(status);
        LinearLayout saves=a.row();wavSave=a.button(a.tr("WAV 복사본 저장","Save WAV copy"),()->render(true,false));a.weighted(saves,wavSave,48);
        if(video){videoSave=a.button(a.tr("MP4 + WAV 저장","Save MP4 + WAV"),()->render(true,true));a.weighted(saves,videoSave,48);}footer.addView(saves);outer.addView(footer);
        dialog=StudioDialogs.alert(a).setTitle(vocal?a.tr("녹음된 보컬에 추가 효과","Add effects to recorded voice"):a.tr("녹음 후 보컬 효과","Recorded vocal effects")).setView(outer)
                .setNegativeButton(a.tr("닫기 · 원본 유지","Close · keep original"),(d,w)->{}).create();
        dialog.setOnDismissListener(d->{cancelled.set(true);NativeAudio.cancelTakeRender();a.stopPlayer();preview.delete();});
        StudioDialogs.showDark(a,dialog);
    }
    void close(){cancelled.set(true);NativeAudio.cancelTakeRender();if(dialog!=null)dialog.dismiss();}
    private void fader(LinearLayout panel,String label,int key,float min,float max){
        panel.addView(new StudioFader(a,label,min,max,values[key],v->key==5?Math.round(v)+" ms":key==3?String.format(Locale.US,"%.1f s",v):key>=7?String.format(Locale.US,"%+.1f dB",v):Math.round(v*100)+"%",v->values[key]=v));
    }
    private boolean prepareListening(){
        if(a.settingsLocked()){a.toast(a.tr("녹음·예약·캡처를 마친 뒤 비교하세요.","Finish recording, countdown or capture before comparing."));return false;}
        if(a.composer.playing())a.composer.pause();if(a.harmony.state[0]>.5)a.harmony.pause();return true;
    }
    private void stage(int phase){
        a.runOnUiThread(()->{if(cancelled.get()||a.isDestroyed())return;
            String[] ko={"편집 WAV 처리 중…","영상용 오디오 인코딩 중…","영상 트랙 복사 중…","편집 오디오 결합 중…","영상·오디오·회전 확인 중…"};
            String[] en={"Rendering edited WAV…","Encoding video audio…","Copying video track…","Combining edited audio…","Checking video, audio and rotation…"};status.setText(a.tr(ko[phase],en[phase]));
        });
    }
    private void working(boolean on){
        working=on;wavSave.setEnabled(!on);if(videoSave!=null)videoSave.setEnabled(!on);progress.setVisibility(on?View.VISIBLE:View.GONE);status.setVisibility(on?View.VISIBLE:View.GONE);
        dialog.getButton(android.content.DialogInterface.BUTTON_NEGATIVE).setText(on?a.tr("취소 · 원본과 저장 파일 유지","Cancel · keep original and saved files"):a.tr("닫기 · 원본 유지","Close · keep original"));
    }
    private void render(boolean save,boolean video){
        if(working||cancelled.get()||ExitCoordinator.isExitRequested())return;if(!save&&!prepareListening())return;
        working(true);stage(0);float[] settings=values.clone();if(!save){a.stopPlayer();preview.delete();}
        try{a.io.execute(()->{
            String error="";TakeAudioFiles.Saved saved=null;boolean ready=false;
            try{
                if(video)saved=TakeAudioFiles.copyVideo(a,take,settings,cancelled,vocal,this::stage);
                else if(save)saved=new TakeAudioFiles.Saved(TakeAudioFiles.copy(a,take,settings,cancelled,vocal),false,"");
                else ready=TakeAudioFiles.render(a.store,take,settings,vocal,preview);
            }catch(Exception e){error=e.getMessage()==null?e.toString():e.getMessage();}
            final String failure=error;final TakeAudioFiles.Saved result=saved;final boolean playable=ready;
            a.runOnUiThread(()->{if(a.isDestroyed()||ExitCoordinator.isExitRequested()){preview.delete();return;}working(false);
                if(cancelled.get()){preview.delete();if(result!=null)a.showTab();return;}
                if(result!=null){dialog.dismiss();a.toast(result.warning.isEmpty()?(result.video?a.tr("새 MP4와 WAV 복사본을 저장했습니다.","Saved new MP4 and WAV copies."):a.tr("새 효과 WAV 복사본을 저장했습니다.","Saved a new effects WAV copy.")):result.warning);a.showTab();}
                else if(playable)a.play(preview);else a.toast(failure.isEmpty()?a.tr("처리하지 못했습니다. 원본은 유지됩니다.","Could not render. The original is retained."):failure);
            });
        });}catch(java.util.concurrent.RejectedExecutionException e){working(false);a.toast(a.tr("종료 중에는 편집을 시작할 수 없습니다.","Cannot start editing while closing."));}
    }
}
