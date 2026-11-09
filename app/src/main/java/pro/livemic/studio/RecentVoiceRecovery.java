package pro.livemic.studio;

import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import java.util.concurrent.atomic.AtomicBoolean;

/** Explicit, session-only opt-in. Native storage is microphone-only and bounded to ten seconds. */
final class RecentVoiceRecovery {
    private RecentVoiceRecovery(){}
    static void draw(MainActivity a){
        LinearLayout card=a.beginCard();
        Switch toggle=new Switch(a);StudioTheme.applyTypography(toggle,StudioTheme.TYPE_BODY);toggle.setMinHeight(a.dp(StudioTheme.TOUCH_TARGET));toggle.setPadding(0,a.dp(StudioTheme.SPACE_12),0,a.dp(StudioTheme.SPACE_12));toggle.setText(a.tr("직전 마이크 소리 10초 보관","Keep the last 10 seconds of mic audio"));toggle.setTextColor(MainActivity.TEXT);
        toggle.setChecked(NativeAudio.recentVoiceSeconds()>=0);
        toggle.setOnCheckedChangeListener((v,on)->{NativeAudio.recentVoiceEnabled(on);a.showTab();});a.add(toggle);
        a.note(a.tr("기본 꺼짐. 마이크는 직접 켜야 하며, 마이크나 보관을 끄면 메모리의 목소리를 지웁니다.","Off by default. Turn on the mic yourself; switching off the mic or history clears its memory."));
        a.add(StudioTheme.styled(a,a.tr("보관 안내","History details"),StudioButton.QUIET,()->{
            android.app.AlertDialog details=StudioDialogs.darkAlert(a).setTitle(a.tr("직전 목소리 보관 안내","Recent voice history"))
                    .setMessage(a.tr("기본 꺼짐. 마이크를 직접 켠 동안만 기기 AEC·소음 처리 후 원음을 메모리에 보관합니다. 마이크 끄기·앱 종료·비활성화 시 즉시 지웁니다. 디지털 반주·YouTube를 직접 합치지는 않습니다. 스피커 소리가 마이크에 들어오면 함께 남을 수 있습니다. 복구 저장이 성공하면 보관 버퍼를 새로 시작합니다. 이미 원음을 가져온 복구 저장은 메모리 삭제 후에도 완료될 수 있습니다.","Off by default. Keeps only microphone audio after device AEC/noise processing in memory while you turn the mic on yourself. Mic off, app exit or disabling clears it. Digital backing and YouTube are not mixed in directly; speaker sound entering the mic can remain. Successful recovery restarts the history buffer. A recovery that has already obtained its source can finish saving after memory is cleared."))
                    .setPositiveButton(a.tr("닫기","Close"),null).create();
            StudioDialogs.showDark(a,details);
        }));
        TextView status=a.text("",StudioTheme.TYPE_BODY,MainActivity.LIME);a.add(status);
        android.widget.Button recover=a.button(a.tr("직전 목소리 복구","Recover recent voice"),()->StudioDialogs.choose(a,a.tr("새 테이크로 복구","Recover as a new take"),new String[]{a.tr("원음으로 복구","Recover dry voice"),a.tr("현재 보컬 효과로 복구","Recover with current vocal effects")},-1,which->recover(a,which==1)));
        a.add(recover);a.endCard(card);
        Runnable update=new Runnable(){public void run(){if(!status.isAttachedToWindow()||a.tab!=4)return;int seconds=NativeAudio.recentVoiceSeconds();status.setText(seconds<0?a.tr("보관 꺼짐","History off"):!AudioService.active?a.tr("마이크 꺼짐 · 보관된 목소리 없음","Mic off · no voice retained"):seconds+a.tr("초 보관됨 · 최대 10초"," seconds retained · up to 10 seconds"));recover.setEnabled(AudioService.active&&seconds>0);status.postDelayed(this,500);}};
        status.post(update);
    }
    private static void recover(MainActivity a,boolean wet){
        float[] settings=a.values.clone();if(!wet){settings[0]=1;settings[2]=settings[4]=settings[7]=settings[8]=settings[9]=settings[10]=settings[19]=0;}
        AtomicBoolean cancelled=new AtomicBoolean();a.toast(a.tr("직전 목소리를 저장하고 있습니다…","Saving recent voice…"));
        a.io.execute(()->{String error="";try{TakeAudioFiles.recover(a,settings,cancelled);}catch(Exception e){error=e.getMessage();}final String failure=error;
            a.runOnUiThread(()->{if(a.isDestroyed())return;a.toast(failure.isEmpty()?a.tr("목소리를 새 테이크로 복구했습니다.","Recovered voice as a new take."):failure);a.showTab();});});
    }
}
