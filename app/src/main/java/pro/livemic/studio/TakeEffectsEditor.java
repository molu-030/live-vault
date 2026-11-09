package pro.livemic.studio;

import android.app.Dialog;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import java.io.File;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONObject;

/** A local-effects sheet. Preview/render never changes the live microphone or original take. */
final class TakeEffectsEditor {
    private final MainActivity a;
    private final JSONObject take;
    private final float[] values;
    private final AtomicBoolean cancelled=new AtomicBoolean();
    private final File preview;
    private boolean working;
    private android.app.AlertDialog dialog;
    TakeEffectsEditor(MainActivity activity,JSONObject source){
        a=activity;take=source;values=TakeAudioFiles.effects(source,a.values);
        preview=new File(a.getCacheDir(),"vocal-preview-"+System.nanoTime()+".wav");
    }
    void show(){
        if(!TakeAudioFiles.editable(a.store,take)){a.toast(a.tr("이 테이크는 보컬 효과 후편집을 지원하지 않습니다.","This take does not support vocal effect editing."));return;}
        LinearLayout panel=a.column();panel.setPadding(a.dp(16),a.dp(8),a.dp(16),0);
        panel.addView(a.text(a.tr("원본은 그대로 두고 새 WAV 복사본을 만듭니다. 원음은 기기 AEC·소음 처리 후이며 제거된 소리는 복구할 수 없습니다. 기본 보컬 효과를 전체에 일정하게 적용하므로 녹음 중 효과 변경과 다를 수 있습니다. 고정 믹스의 반주·보컬 루프는 유지됩니다. 일시정지 구간은 제외된 채로 유지되며 잔향은 다시 계산합니다. 비교 중 다른 앱 내 반주는 일시정지하며 자동 재개하지 않습니다.","Creates a new WAV copy and keeps the original. Sources follow device AEC/noise processing; removed sound cannot be recovered. Basic vocal effects apply uniformly, so live effect changes may differ. Backing and vocal loops in the fixed mix are retained. Paused sections remain omitted; effect tails are recalculated. Comparison pauses other in-app backing; it does not resume automatically."),12,MainActivity.MUTED));
        fader(panel,a.tr("마이크 볼륨","Microphone volume"),0,0,2);
        fader(panel,a.tr("리버브 양","Reverb amount"),2,0,1);
        fader(panel,a.tr("리버브 잔향","Reverb decay"),3,.3f,6);
        fader(panel,a.tr("에코 양","Echo level"),4,0,.8f);
        fader(panel,a.tr("에코 간격","Echo time"),5,70,700);
        fader(panel,a.tr("에코 반복","Echo feedback"),6,0,.7f);
        fader(panel,"Low EQ",7,-12,12);fader(panel,"Mid EQ",8,-12,12);fader(panel,"High EQ",9,-12,12);
        android.widget.Switch compressor=new android.widget.Switch(a);compressor.setText(a.tr("컴프레서","Compressor"));compressor.setTextColor(MainActivity.TEXT);compressor.setChecked(values[10]>.5f);compressor.setOnCheckedChangeListener((v,on)->values[10]=on?1:0);panel.addView(compressor);
        android.widget.Switch noise=new android.widget.Switch(a);noise.setText(a.tr("앱 소음 완화","App noise reduction"));noise.setTextColor(MainActivity.TEXT);noise.setChecked(values[19]>.5f);noise.setOnCheckedChangeListener((v,on)->values[19]=on?1:0);panel.addView(noise);
        panel.addView(a.button(a.tr("리버브 공간 선택","Reverb space"),()->StudioDialogs.choose(a,a.tr("리버브 공간","Reverb space"),new String[]{"Hall","Room","Plate","Cathedral"},Math.round(values[18]),which->values[18]=which)));
        LinearLayout compare=a.row();
        a.weighted(compare,a.button(a.tr("A · 원본 듣기","A · Listen to original"),()->{if(!working&&prepareListening()){a.play(a.store.file(take.optString("id"),".wav"));}}),48);
        a.weighted(compare,a.button(a.tr("B · 편집 미리듣기","B · Preview edit"),()->render(false)),48);panel.addView(compare);
        panel.addView(a.button(a.tr("새 복사본으로 저장","Save as a new copy"),()->render(true)));
        ScrollView scroll=new ScrollView(a);scroll.addView(panel);
        dialog=StudioDialogs.alert(a).setTitle(a.tr("녹음 후 보컬 효과","Recorded vocal effects")).setView(scroll)
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
        if(a.composer.playing())a.composer.pause();
        if(a.harmony.state[0]>.5)a.harmony.pause();
        return true;
    }
    private void render(boolean save){
        if(working||cancelled.get())return;
        if(!save&&!prepareListening())return;
        working=true;float[] settings=values.clone();
        if(!save){a.stopPlayer();preview.delete();}
        a.toast(a.tr("오디오를 처리하고 있습니다…","Rendering audio…"));
        a.io.execute(()->{
            String error="";String saved=null;boolean ready=false;
            try{
                if(save)saved=TakeAudioFiles.copy(a,take,settings,cancelled);
                else ready=NativeAudio.renderTakeSources(a.store.file(take.optString("id"),".raw.wav").getAbsolutePath(),take.optBoolean("sourceFixed",true)?a.store.file(take.optString("id"),".fixed.wav").getAbsolutePath():"",preview.getAbsolutePath(),settings,take.optInt("syncOffsetMs"));
            }catch(Exception e){error=e.getMessage();}
            final String failure=error;final String result=saved;final boolean playable=ready;
            a.runOnUiThread(()->{working=false;if(cancelled.get()||a.isDestroyed()){preview.delete();return;}
                if(result!=null){dialog.dismiss();a.toast(a.tr("새 효과 복사본을 저장했습니다.","Saved a new effects copy."));a.showTab();}
                else if(playable)a.play(preview);
                else a.toast(failure.isEmpty()?a.tr("처리하지 못했습니다. 원본은 유지됩니다.","Could not render. The original is retained."):failure);
            });
        });
    }
}
