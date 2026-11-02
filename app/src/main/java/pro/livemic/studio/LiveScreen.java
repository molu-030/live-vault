package pro.livemic.studio;

import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import java.util.Locale;

/** Compact performance surface; advanced editing lives in the Details dialog. */
final class LiveScreen {
    final MainActivity a;
    LiveSessionDisplay display;
    Button sourceButton, inputButton, monitorToggle, backingPlayButton, detailsButton, video;
    TextView backing, backingTime, route, volumeLabel, micVolumeLabel, reverbLabel, echoLabel;
    SeekBar backingVolume, micVolume, reverbVolume, echoVolume;
    boolean volumeDragging, micVolumeDragging;
    private final boolean[] dragging = new boolean[20];
    LiveScreen(MainActivity activity) { a=activity; }
    String tr(String ko,String en) { return a.tr(ko,en); }
    Button button(String label,Runnable action) {
        Button b=new Button(a); b.setAllCaps(false); b.setStateListAnimator(null); b.setElevation(0);
        b.setTypeface(StudioTheme.bold(a)); b.setTextSize(12); b.setGravity(Gravity.CENTER);
        b.setIncludeFontPadding(false); b.setPadding(a.dp(5),0,a.dp(5),0);
        b.setMinWidth(0); b.setMinimumWidth(0); b.setMinHeight(a.dp(48)); b.setMinimumHeight(a.dp(48));
        b.setMaxLines(2); b.setEllipsize(TextUtils.TruncateAt.END); b.setText(label);
        b.setOnClickListener(v->action.run()); paint(b,false); return b;
    }
    void paint(Button b,boolean on) {
        b.setBackgroundTintList(null);
        b.setBackground(StudioTheme.background(a,on?StudioTheme.ACCENT:StudioTheme.RAISED,8,on?StudioTheme.ACCENT:StudioTheme.BORDER));
        b.setTextColor(on?StudioTheme.ACCENT_INK:StudioTheme.TEXT); b.setSelected(on);
    }
    void paintDanger(Button b) {
        b.setBackgroundTintList(null); b.setBackground(StudioTheme.background(a,StudioTheme.DANGER,8,StudioTheme.DANGER));
        b.setTextColor(0xff190b0c); b.setSelected(true);
    }
    TextView text(String s,int size,int color) { return a.text(s,size,color); }
    LinearLayout panel() {
        LinearLayout p=a.column(); p.setPadding(a.dp(12),a.dp(5),a.dp(12),a.dp(5));
        p.setBackground(StudioTheme.background(a,StudioTheme.SURFACE,12,StudioTheme.BORDER)); return p;
    }
    void addPanel(View p) {
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.topMargin=a.dp(8); a.page.addView(p,lp);
    }
    TextView heading(String s) {
        TextView t=text(s,11,StudioTheme.MUTED); t.setTypeface(StudioTheme.bold(a)); t.setGravity(Gravity.CENTER_VERTICAL); return t;
    }
    SeekBar gain(LinearLayout parent,String label,int key,int max,TextView value) {
        LinearLayout row=a.row(); value.setTextSize(12); value.setTypeface(StudioTheme.medium(a));
        value.setMaxLines(1); value.setGravity(Gravity.CENTER_VERTICAL);
        TextView name=text(label,12,StudioTheme.MUTED);name.setMaxLines(1);name.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(name,new LinearLayout.LayoutParams(a.dp(64),a.dp(48)));
        value.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);value.setTypeface(StudioTheme.mono(a));
        SeekBar bar=new SeekBar(a); bar.setMax(max); bar.setProgress(Math.round(a.values[key]*100));
        bar.setContentDescription(label); bar.setMinimumHeight(a.dp(48));
        bar.setProgressTintList(ColorStateList.valueOf(StudioTheme.ACCENT));
        bar.setThumbTintList(ColorStateList.valueOf(StudioTheme.ACCENT));
        bar.setProgressBackgroundTintList(ColorStateList.valueOf(0xff30352b));
        bar.setPadding(a.dp(8),0,a.dp(8),0);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onStartTrackingTouch(SeekBar b) { dragging[key]=true; if(key==0)micVolumeDragging=true;if(key==1)volumeDragging=true; }
            public void onStopTrackingTouch(SeekBar b) { dragging[key]=false;if(key==0)micVolumeDragging=false;if(key==1)volumeDragging=false; }
            public void onProgressChanged(SeekBar b,int v,boolean user) { if(user) { a.set(key,v/100f);value.setText(v+"%"); } }
        });
        row.addView(bar,new LinearLayout.LayoutParams(0,a.dp(48),1));
        row.addView(value,new LinearLayout.LayoutParams(a.dp(44),a.dp(48))); parent.addView(row); return bar;
    }
    void draw() {
        LinearLayout session=panel();
        display=new LiveSessionDisplay(a);
        session.addView(display,new LinearLayout.LayoutParams(-1,a.dp(170)));
        a.page.flexible(display,151,300);
        LinearLayout controls=a.row();
        sourceButton=button(tr("소스 ▾","Source ▾"),this::sourceSheet); inputButton=sourceButton;
        controls.addView(sourceButton,new LinearLayout.LayoutParams(0,a.dp(48),1));
        a.micButton=button(tr("마이크 켜기","Turn mic on"),a::requestMicToggle);
        LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(0,a.dp(48),1);mp.leftMargin=a.dp(6);controls.addView(a.micButton,mp);
        monitorToggle=button(tr("모니터","Monitor"),this::toggleMonitor);
        LinearLayout.LayoutParams op=new LinearLayout.LayoutParams(0,a.dp(48),1);op.leftMargin=a.dp(6);controls.addView(monitorToggle,op);
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,a.dp(48));cp.topMargin=a.dp(8);session.addView(controls,cp);
        // Kept for routing accessibility; main has exactly three controls.
        a.outputButton=null;
        route=text("",10,StudioTheme.MUTED); route.setVisibility(View.GONE);
        micVolumeLabel=text("",11,StudioTheme.MUTED);micVolume=gain(session,tr("마이크","Mic"),0,200,micVolumeLabel);
        a.page.addView(session,new LinearLayout.LayoutParams(-1,-2));

        LinearLayout accompaniment=panel();
        accompaniment.addView(heading(tr("반주","ACCOMPANIMENT")),new LinearLayout.LayoutParams(-1,a.dp(24)));
        LinearLayout player=a.row(); backingPlayButton=button("▶",this::toggleBackingPlay);
        backingPlayButton.setTextSize(18); player.addView(backingPlayButton,new LinearLayout.LayoutParams(a.dp(48),a.dp(48)));
        LinearLayout track=a.column();track.setGravity(Gravity.CENTER_VERTICAL);track.setPadding(a.dp(10),0,0,0);
        backing=text("",12,StudioTheme.TEXT);backing.setTypeface(StudioTheme.medium(a));backing.setMaxLines(1);backing.setEllipsize(TextUtils.TruncateAt.END);
        backingTime=text("00:00 / 00:00",10,StudioTheme.OLIVE);backingTime.setTypeface(StudioTheme.mono(a));track.addView(backing);track.addView(backingTime);
        track.setOnClickListener(v->playbackSources());track.setFocusable(true);track.setContentDescription(tr("반주 소스 선택","Choose accompaniment source"));
        player.addView(track,new LinearLayout.LayoutParams(0,a.dp(48),1)); accompaniment.addView(player);
        volumeLabel=text("",11,StudioTheme.MUTED);backingVolume=gain(accompaniment,tr("반주","Backing"),1,100,volumeLabel);addPanel(accompaniment);

        LinearLayout fx=panel();LinearLayout fh=a.row();
        fh.addView(heading(tr("보컬 효과","VOCAL FX")),new LinearLayout.LayoutParams(0,a.dp(48),1));
        detailsButton=button(tr("상세 ▾","Details ▾"),this::effectsDetails);fh.addView(detailsButton,new LinearLayout.LayoutParams(a.dp(90),a.dp(48)));fx.addView(fh);
        reverbLabel=text("",11,StudioTheme.MUTED);reverbVolume=gain(fx,tr("리버브","Reverb"),2,100,reverbLabel);
        echoLabel=text("",11,StudioTheme.MUTED);echoVolume=gain(fx,tr("에코","Echo"),4,80,echoLabel);addPanel(fx);

        LinearLayout dock=a.row();a.recButton=button(tr("● 녹음 시작","● Record"),a::toggleRecord);dock.addView(a.recButton,new LinearLayout.LayoutParams(0,a.dp(52),1));
        video=button(tr("영상","Video"),()->a.chooseTab(3));LinearLayout.LayoutParams vp=new LinearLayout.LayoutParams(a.dp(90),a.dp(52));vp.leftMargin=a.dp(8);dock.addView(video,vp);a.actionDock.addView(dock);
        a.recordOptions=a.globalOptions;
        poll(NativeAudio.stats(),AudioService.active);
    }
    void sourceSheet() {
        LinearLayout p=panel();
        AlertDialog dialog=new AlertDialog.Builder(a).setTitle(tr("소스","Sources")).setView(StudioDialogs.scrollable(a,p)).setPositiveButton(tr("닫기","Close"),null).create();
        Button in=button(tr("입력 장치: ","Input device: ")+a.inputName(),()->{dialog.dismiss();a.selectInput();});
        Button out=button(tr("출력 장치: ","Output device: ")+a.outputName(),()->{dialog.dismiss();a.outputMode();});
        in.setEnabled(!a.settingsLocked());out.setEnabled(!a.settingsLocked());
        in.setContentDescription(tr("입력 장치: ","Input device: ")+a.inputName());out.setContentDescription(tr("출력 장치: ","Output device: ")+a.outputName());
        p.addView(in);p.addView(out);
        p.addView(button(tr("모니터 설정","Monitoring settings"),()->{dialog.dismiss();a.monitoringOptions();}));
        sourceChoices(p,dialog);
        p.addView(button(tr("오디오 설정","Audio settings"),()->{dialog.dismiss();a.chooseTab(6);}));dialog.show();
    }
    void playbackSources() {
        LinearLayout p=panel();AlertDialog d=new AlertDialog.Builder(a).setTitle(tr("반주 소스","Accompaniment source")).setView(p).setPositiveButton(tr("닫기","Close"),null).create();sourceChoices(p,d);d.show();
    }
    void sourceChoices(LinearLayout p,AlertDialog dialog) {
        String[] names={tr("작곡기","Composer"),tr("내 파일","My files"),tr("온라인","Online")};
        for(int source:new int[]{1,2,0}) {
            Button choice=button(names[source],()->{dialog.dismiss();openSource(source);});
            choice.setEnabled(!a.settingsLocked());p.addView(choice);
        }
    }
    void openSource(int source) {
        if(a.settingsLocked()) {a.toast(tr("녹음 또는 예약을 마친 뒤 소스를 바꾸세요.","Finish recording or the countdown before changing sources."));return;}
        a.sourceTab=source;a.getPreferences(0).edit().putInt("backingSource",source).apply();a.chooseTab(1);
    }
    void effectsDetails() {
        LinearLayout p=panel();
        LinearLayout previous=a.content;a.content=p;
        Button preset=button(tr("프리셋 · ","Preset · ")+a.presetName()+" ▾",()->{});
        p.addView(preset);
        a.slider(tr("리버브 양","Reverb amount"),2,0,1,v->Math.round(v*100)+"%");
        a.slider(tr("리버브 잔향","Reverb decay"),3,.3f,6,v->String.format(Locale.US,"%.1f s",v));
        a.slider(tr("에코 양","Echo amount"),4,0,.8f,v->Math.round(v*100)+"%");
        a.slider(tr("에코 간격","Echo delay"),5,70,700,v->Math.round(v)+" ms");
        a.slider(tr("에코 반복","Echo feedback"),6,0,.7f,v->Math.round(v*100)+"%");
        for(int key=7;key<=9;key++)a.slider(new String[]{"Low EQ","Mid EQ","High EQ"}[key-7],key,-12,12,v->Math.round(v)+" dB");
        a.toggle(tr("컴프레서","Compressor"),10);a.toggle(tr("소음 완화","Noise reduction"),19);
        p.addView(button(tr("리버브 공간 ▾","Reverb space ▾"),()->StudioDialogs.choose(a,tr("리버브 공간","Reverb space"),new String[]{tr("홀","Hall"),tr("룸","Room"),tr("플레이트","Plate"),tr("대성당","Cathedral")},Math.round(a.values[18]),v->a.set(18,v))));
        a.content=previous;
        AlertDialog dialog=new AlertDialog.Builder(a).setTitle(tr("상세 보컬 효과","Vocal FX details")).setView(StudioDialogs.scrollable(a,p)).setPositiveButton(tr("닫기","Close"),(d,w)->sync()).create();
        preset.setOnClickListener(v->{dialog.dismiss();a.presetPicker();});
        p.addView(button(tr("효과 초기화","Reset effects"),()->{reset();dialog.dismiss();effectsDetails();}));dialog.show();
    }
    void reset() {
        int[] keys={2,3,4,5,6,7,8,9,10,18};float[] values={.27f,2.2f,.14f,280,.25f,0,0,1,1,0};
        for(int i=0;i<keys.length;i++)a.set(keys[i],values[i]);sync();
    }
    void toggleMonitor() {
        float[] s=NativeAudio.stats();
        if(s[17]>.5f) {NativeAudio.resetFeedback();NativeComposer.monitor(true);a.harmony.monitor(true);a.set(11,1);a.toast(tr("보호 음소거를 해제하고 실시간 출력을 켰습니다.","Safety mute reset; live monitoring is on."));return;}
        boolean on=AudioService.active?s[21]>.5f:a.values[11]>.5f;
        if(on) {a.set(11,0);a.toast(tr("내 목소리 듣기를 껐습니다.","Voice monitoring turned off."));}
        else {if(!AudioService.canMonitor(a))NativeAudio.monitorLevel(.22f);a.set(11,1);if(!AudioService.active)a.requestMicToggle();else a.toast(tr("내 목소리 듣기를 켰습니다.","Voice monitoring turned on."));}
        poll(NativeAudio.stats(),AudioService.active);
    }
    void toggleBackingPlay() {
        if(a.settingsLocked()) {a.toast(tr("녹음 또는 예약 중에는 반주를 바꿀 수 없습니다.","Accompaniment is locked during recording or countdown."));return;}
        float[] s=NativeAudio.stats();
        if(a.composer.playing())a.composer.pause();
        else if(s[16]>.5f)NativeAudio.playLocal(false);
        else if(AudioService.composerRequested)a.composer.play();
        else if(!a.selectedFile.isEmpty())a.ensureMic(4);
        else playbackSources();
        poll(NativeAudio.stats(),AudioService.active);
    }
    String compactDevice(String name) {
        if(name.equals(tr("시스템 기본","System default"))||name.equals(tr("시스템 기본값","System default")))return tr("기본","Default");
        int dot=name.indexOf(" · ");if(dot>0)return name.substring(0,dot);int hash=name.indexOf(" #");return hash>0?name.substring(0,hash):name;
    }
    void syncGain(SeekBar b,TextView label,int key,String name) {
        if(b==null)return;if(!dragging[key])b.setProgress(Math.round(a.values[key]*100));label.setText(b.getProgress()+"%");
    }
    void updateVolumeLabel(int value) {if(volumeLabel!=null)volumeLabel.setText(value+"%");}
    void updateMicVolumeLabel(int value) {if(micVolumeLabel!=null)micVolumeLabel.setText(value+"%");}
    void sync() {
        syncGain(micVolume,micVolumeLabel,0,tr("마이크","Mic"));syncGain(backingVolume,volumeLabel,1,tr("반주","Backing"));
        syncGain(reverbVolume,reverbLabel,2,tr("리버브","Reverb"));syncGain(echoVolume,echoLabel,4,tr("에코","Echo"));
    }
    void poll(float[] s,boolean running) {
        if(display==null)return;
        float[] safe=new float[22];
        if(s!=null)for(int i=0;i<Math.min(s.length,safe.length);i++)safe[i]=Float.isFinite(s[i])?s[i]:0;
        s=safe;
        if(a.micButton.isSelected()!=running)paint(a.micButton,running);
        boolean muted=s[17]>.5f,on=!muted&&running&&s[21]>.5f;
        String monitoring=muted?tr("보호 음소거","Safety mute"):on?tr("모니터 켜짐","Monitoring on"):tr("모니터 꺼짐","Monitoring off");route.setText(monitoring);
        monitorToggle.setText(muted?tr("보호 해제","Reset safety"):on?tr("모니터 켜짐","Monitor on"):tr("모니터 꺼짐","Monitor off"));monitorToggle.setContentDescription(monitoring);paint(monitorToggle,on);
        sourceButton.setText(tr("소스 ▾","Source ▾")+"\n"+compactDevice(a.outputName()));
        sourceButton.setContentDescription(tr("입력: ","Input: ")+a.inputName()+tr(". 출력: ",". Output: ")+a.outputName());
        boolean rec=RecordingManager.active,wait=AudioService.waiting();
        String state=a.busy?tr("준비 / 저장 중","PREPARING / SAVING"):rec?(RecordingManager.paused?tr("일시정지","PAUSED"):"REC"):wait?tr("예약","COUNTDOWN"):running?tr("마이크 켜짐","MIC ON"):tr("대기","READY");
        String time=rec?a.formatTime(s[8]):wait?String.valueOf(AudioService.remaining())+"s":"00:00";
        float hz=running&&Float.isFinite(s[0])&&s[0]>0?s[0]:0;
        display.cents=hz>0?Math.max(-50,Math.min(50,s[1])):0;
        display.update(running?s[3]:0,s[4],hz,hz>0&&Float.isFinite(s[2])?a.noteName((int)s[2]):"—",state,time,rec&&!RecordingManager.paused,monitoring);
        boolean composer=s[16]<=.5f&&(a.composer.playing()||AudioService.composerRequested);
        boolean playing=a.composer.playing()||s[16]>.5f;
        if(composer) {double[] cs=a.composer.state();backing.setText(a.composer.project.name);backingTime.setText(tr("작곡기 · 마디 ","Composer · bar ")+(1+(int)(Math.max(0,cs[0])/MusicTheory.barBeats(a.composer.project.meter)))+(!playing?tr(" · 일시정지"," · paused"):""));}
        else {double[] local=NativeAudio.localPlaybackInfo();backing.setText(a.selectedFile.isEmpty()?tr("반주 선택 ▾","Choose backing ▾"):a.selectedFile);backingTime.setText(a.formatTime(local[0])+" / "+a.formatTime(local[1]));}
        backing.setContentDescription(backing.getText());backingPlayButton.setText(playing?"Ⅱ":"▶");backingPlayButton.setContentDescription(playing?tr("반주 일시정지","Pause accompaniment"):tr("반주 재생","Play accompaniment"));paint(backingPlayButton,playing);
        backingPlayButton.setEnabled(!a.settingsLocked());sync();
    }
    void transport(boolean busy,boolean waiting,boolean recording) {
        if(waiting||recording)paintDanger(a.recButton);else paint(a.recButton,!busy);
        video.setEnabled(!busy&&!waiting&&!recording&&!a.recordPermissionPending&&!a.starting);
    }
}
