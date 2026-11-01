package pro.livemic.studio;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.util.Locale;

/** Live controls share one dial; status values come from the audio service. */
final class LiveScreen {
    static final int IVORY=0xffe5e0d4,INK=0xff141713, PANEL=0xff191c16,SCREEN=0xff10140f;
    final MainActivity a;
    final int[] ids={2,3,0,4,5,6};
    final float[] mins={0,.3f,0,0,70,0},maxs={1,6,2,.8f,700,.7f};
    final Button[] selectors=new Button[6];
    final MainActivity.Formatter[] format={v->Math.round(v*100)+"%",v->String.format(Locale.US,"%.1f s",v),v->Math.round(v*100)+"%",v->Math.round(v*100)+"%",v->Math.round(v)+" ms",v->Math.round(v*100)+"%"};
    SharedEffectDial dial;
    TextView mic,route,clock,backing,pitch,level,effectName,effectValue,volumeLabel;
    ProgressBar meter;Button video,inputButton,filesButton,onlineButton;SeekBar backingVolume;boolean volumeDragging;
    LiveScreen(MainActivity activity){a=activity;}
    String tr(String ko,String en){return a.tr(ko,en);}
    String name(int n){return new String[]{tr("리버브","Reverb"),tr("잔향 길이","Decay"),tr("목소리 크기","Voice level"),tr("에코 양","Echo"),tr("에코 간격","Delay"),tr("에코 반복","Repeat")}[n];}
    Button button(String text,Runnable action){Button b=new Button(a);b.setAllCaps(false);b.setStateListAnimator(null);b.setElevation(0);b.setTypeface(StudioTheme.bold(a));b.setTextSize(12);b.setGravity(Gravity.CENTER);b.setIncludeFontPadding(false);b.setPadding(a.dp(4),a.dp(2),a.dp(4),a.dp(2));b.setMinWidth(0);b.setMinimumWidth(0);b.setMinHeight(a.dp(48));b.setMinimumHeight(a.dp(48));b.setMaxLines(2);b.setText(text);b.setOnClickListener(v->action.run());paint(b,false);return b;}
    void paint(Button b,boolean selected){b.setBackgroundTintList(null);b.setBackground(StudioTheme.background(a,selected?MainActivity.LIME:IVORY,7));b.setTextColor(INK);b.setSelected(selected);}
    TextView text(String t,int size,int color){return a.text(t,size,color);}
    void draw(){
        LinearLayout lcd=a.column();lcd.setPadding(a.dp(10),a.dp(4),a.dp(10),a.dp(4));lcd.setBackground(StudioTheme.background(a,SCREEN,8));
        LinearLayout first=a.row();mic=text("",12,MainActivity.LIME);mic.setTypeface(StudioTheme.bold(a));first.addView(mic,new LinearLayout.LayoutParams(0,-2,1));clock=text("",18,IVORY);clock.setTypeface(StudioTheme.mono(a));first.addView(clock);lcd.addView(first);
        route=text("",11,0xffbdc0b2);route.setMaxLines(1);route.setEllipsize(android.text.TextUtils.TruncateAt.END);route.setOnClickListener(v->a.monitoringOptions());route.setFocusable(true);lcd.addView(route);backing=text("",11,0xffbdc0b2);backing.setMaxLines(1);backing.setEllipsize(android.text.TextUtils.TruncateAt.END);
        View divider=new View(a);divider.setBackgroundColor(0xff394135);LinearLayout.LayoutParams line=new LinearLayout.LayoutParams(-1,a.dp(1));line.topMargin=a.dp(1);line.bottomMargin=a.dp(1);lcd.addView(divider,line);
        LinearLayout selected=a.row();effectName=text("",14,IVORY);effectName.setTypeface(StudioTheme.bold(a));selected.addView(effectName,new LinearLayout.LayoutParams(0,-2,1));effectValue=text("",22,MainActivity.LIME);effectValue.setTypeface(StudioTheme.bold(a));selected.addView(effectValue);lcd.addView(selected);
        LinearLayout input=a.row();pitch=text("",11,0xffbdc0b2);input.addView(pitch,new LinearLayout.LayoutParams(0,-2,1));level=text("",11,0xffbdc0b2);input.addView(level);lcd.addView(input);lcd.addView(backing);
        meter=new ProgressBar(a,null,android.R.attr.progressBarStyleHorizontal);meter.setMax(1000);meter.setContentDescription(tr("입력 레벨","Input level"));meter.setProgressTintList(ColorStateList.valueOf(MainActivity.LIME));meter.setProgressBackgroundTintList(ColorStateList.valueOf(0xff384235));LinearLayout.LayoutParams m=new LinearLayout.LayoutParams(-1,a.dp(5));m.topMargin=a.dp(4);lcd.addView(meter,m);a.add(lcd);
        LinearLayout sources=a.row();filesButton=button(tr("내 파일","My files"),()->openSource(1));onlineButton=button(tr("온라인","Online"),()->openSource(2));sources.addView(filesButton,new LinearLayout.LayoutParams(0,a.dp(48),1));LinearLayout.LayoutParams online=new LinearLayout.LayoutParams(0,a.dp(48),1);online.leftMargin=a.dp(6);sources.addView(onlineButton,online);a.add(sources);
        LinearLayout controls=a.row();a.micButton=button(tr("마이크 켜기","Turn mic on"),a::requestMicToggle);controls.addView(a.micButton,new LinearLayout.LayoutParams(0,a.dp(48),1));inputButton=button(tr("입력 ▾","Input ▾"),a::selectInput);LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(0,a.dp(48),1);ip.leftMargin=a.dp(6);controls.addView(inputButton,ip);inputButton.setTextSize(11);inputButton.setEllipsize(android.text.TextUtils.TruncateAt.END);a.outputButton=button(tr("출력 ▾","Output ▾"),a::outputMode);LinearLayout.LayoutParams op=new LinearLayout.LayoutParams(0,a.dp(48),1);op.leftMargin=a.dp(6);controls.addView(a.outputButton,op);a.outputButton.setTextSize(11);a.outputButton.setEllipsize(android.text.TextUtils.TruncateAt.END);a.add(controls);
        LinearLayout volume=a.row();volumeLabel=text("",11,IVORY);volume.addView(volumeLabel,new LinearLayout.LayoutParams(-2,a.dp(48)));volumeLabel.setGravity(Gravity.CENTER_VERTICAL);backingVolume=new SeekBar(a);backingVolume.setMax(100);backingVolume.setProgress(Math.round(a.values[1]*100));backingVolume.setContentDescription(tr("반주 음량","Backing volume"));backingVolume.setProgressTintList(ColorStateList.valueOf(MainActivity.LIME));backingVolume.setThumbTintList(ColorStateList.valueOf(MainActivity.LIME));backingVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar b){volumeDragging=true;}public void onStopTrackingTouch(SeekBar b){volumeDragging=false;}public void onProgressChanged(SeekBar b,int value,boolean user){if(user){a.set(1,value/100f);updateVolumeLabel(value);}}});volume.addView(backingVolume,new LinearLayout.LayoutParams(0,a.dp(48),1));a.add(volume);
        LinearLayout effects=a.column();effects.setPadding(a.dp(7),a.dp(5),a.dp(7),a.dp(5));effects.setBackground(StudioTheme.background(a,PANEL,8));
        LinearLayout section=a.row();Button reset=button(tr("초기화","Reset"),this::reset);section.addView(reset,new LinearLayout.LayoutParams(a.dp(70),a.dp(48)));TextView heading=text(tr("보컬 효과","VOCAL FX"),13,IVORY);heading.setTypeface(StudioTheme.bold(a));heading.setGravity(Gravity.CENTER);section.addView(heading,new LinearLayout.LayoutParams(0,a.dp(48),1));a.presetChip=button(a.presetName()+" ▾",()->{dial.cancelGesture();a.presetPicker();});a.presetChip.setTextSize(11);section.addView(a.presetChip,new LinearLayout.LayoutParams(a.dp(112),a.dp(48)));effects.addView(section);
        LinearLayout row=a.row();row.setGravity(Gravity.CENTER);LinearLayout left=new SelectorColumn(),right=new SelectorColumn();for(int i=0;i<3;i++)addSelector(left,i);for(int i=3;i<6;i++)addSelector(right,i);
        row.addView(left,new LinearLayout.LayoutParams(0,-1,1));dial=createDial();LinearLayout.LayoutParams center=new LinearLayout.LayoutParams(0,-1,1.7f);center.leftMargin=a.dp(3);center.rightMargin=a.dp(3);row.addView(dial,center);row.addView(right,new LinearLayout.LayoutParams(0,-1,1));effects.addView(row,new LinearLayout.LayoutParams(-1,0,1));
        TextView hint=text(tr("값 선택 → 노브 조절","Select a value → turn the dial"),10,0xffa5aa9d);hint.setGravity(Gravity.CENTER);effects.addView(hint,new LinearLayout.LayoutParams(-1,-2));a.page.addView(effects,new LinearLayout.LayoutParams(-1,a.dp(220)));a.page.flexible(effects,0,1000);
        a.content=a.actionDock;LinearLayout actions=a.row();a.recButton=button(tr("● 녹음 시작","● Record"),a::toggleRecord);actions.addView(a.recButton,new LinearLayout.LayoutParams(0,a.dp(52),1));video=button(tr("영상","Video"),()->a.chooseTab(3));LinearLayout.LayoutParams vp=new LinearLayout.LayoutParams(0,a.dp(52),1);vp.leftMargin=a.dp(6);actions.addView(video,vp);a.recordOptions=button("⋯",a::recordingOptions);a.recordOptions.setTextSize(21);a.recordOptions.setContentDescription(tr("녹음 설정 · 시작 시간 및 다른 앱 소리","Recording settings · start time and other app audio"));LinearLayout.LayoutParams more=new LinearLayout.LayoutParams(a.dp(48),a.dp(52));more.leftMargin=a.dp(6);actions.addView(a.recordOptions,more);a.actionDock.addView(actions);a.content=a.page;
        select(a.selectedLiveEffect);poll(NativeAudio.stats(),AudioService.active);
    }
    final class SelectorColumn extends LinearLayout {
        SelectorColumn(){super(a);setOrientation(VERTICAL);setGravity(Gravity.CENTER_VERTICAL);}
        @Override protected void onMeasure(int widthSpec,int heightSpec){
            int available=MeasureSpec.getSize(heightSpec)-a.dp(8);
            int height=Math.max(a.dp(48),Math.min(a.dp(72),available/3));
            for(int i=0;i<getChildCount();i++){android.view.ViewGroup.LayoutParams lp=getChildAt(i).getLayoutParams();if(lp.height!=height)lp.height=height;}
            super.onMeasure(widthSpec,heightSpec);
        }
    }
    void updateVolumeLabel(int value){volumeLabel.setText(tr("반주 ","Backing ")+value+"%");}
    String compactDevice(String value){return value.equals(tr("시스템 기본","System default"))?tr("기본","Default"):value;}
    void openSource(int source){a.sourceTab=source;a.getPreferences(0).edit().putInt("backingSource",source).apply();a.chooseTab(1);}
    void addSelector(LinearLayout parent,int index){Button b=button("",()->select(index));selectors[index]=b;LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,a.dp(72));if(index%3>0)lp.topMargin=a.dp(4);parent.addView(b,lp);}
    void select(int index){if(dial.isTracking())return;a.selectedLiveEffect=Math.max(0,Math.min(5,index));bindDial();sync();}
    void reset(){dial.cancelGesture();int[] keys={0,2,3,4,5,6};float[] defaults={1,.27f,2.2f,.14f,280,.25f};for(int i=0;i<keys.length;i++)a.set(keys[i],defaults[i]);sync();}
    SharedEffectDial createDial(){return new SharedEffectDial(a,value->{int selected=a.selectedLiveEffect;a.set(ids[selected],value);sync();});}
    void bindDial(){int selected=a.selectedLiveEffect;dial.cancelGesture();dial.bind(name(selected),mins[selected],maxs[selected],a.values[ids[selected]],format[selected]);}
    void syncDial(){dial.sync(a.values[ids[a.selectedLiveEffect]]);}
    void sync(){int selected=a.selectedLiveEffect;for(int i=0;i<6;i++){String value=(i==2?tr("목소리 크기","Voice"):name(i))+"\n"+format[i].value(a.values[ids[i]]);if(!value.contentEquals(selectors[i].getText()))selectors[i].setText(value);if(selectors[i].isSelected()!=(i==selected))paint(selectors[i],i==selected);}effectName.setText(name(selected));effectValue.setText(format[selected].value(a.values[ids[selected]]));syncDial();}
    void poll(float[] s,boolean running){if(mic==null)return;mic.setText(running?tr("● 마이크 켜짐","● MIC ON"):tr("● 마이크 대기","● MIC READY"));if(a.micButton.isSelected()!=running)paint(a.micButton,running);route.setText(s[17]>.5?tr("내 목소리 듣기 · 보호 음소거","Voice monitoring · safety mute"):running&&s[21]>.5?tr("내 목소리 듣기 · 켜짐","Voice monitoring · on"):tr("내 목소리 듣기 · 꺼짐","Voice monitoring · off"));if(!volumeDragging)backingVolume.setProgress(Math.round(a.values[1]*100));updateVolumeLabel(backingVolume.getProgress());inputButton.setText(tr("입력","Input")+"\n"+compactDevice(a.inputName()));inputButton.setContentDescription(tr("입력: ","Input: ")+a.inputName());a.outputButton.setText(tr("출력","Output")+"\n"+compactDevice(a.outputName()));a.outputButton.setContentDescription(tr("출력: ","Output: ")+a.outputName());clock.setText(RecordingManager.active?(RecordingManager.paused?"Ⅱ ":"● ")+a.formatTime(s[8]):AudioService.waiting()?tr("예약 ","IN ")+AudioService.remaining()+tr("초","s"):"00:00");double[] cs=a.composer.state();if(a.composer.playing())backing.setText(tr("작곡기 · ","Composer · ")+tr("마디 ","bar ")+(1+(int)(Math.max(0,cs[0])/MusicTheory.barBeats(a.composer.project.meter))));else if(s[16]>.5){double[] local=NativeAudio.localPlaybackInfo();backing.setText(tr("반주 · ","Backing · ")+a.formatTime(local[0])+" / "+a.formatTime(local[1]));}else backing.setText(tr("반주 대기","Backing idle"));pitch.setText(tr("입력 음 ","INPUT ")+(s[0]>0?a.noteName((int)s[2]):"—"));float rms=Math.max(0,s[3]);level.setText(rms>.00001?String.format(Locale.US,"%.0f dB",20*Math.log10(rms)):"— dB");meter.setProgress((int)(1000*Math.max(0,Math.min(1,(20*Math.log10(Math.max(.00001,rms))+60)/60))));sync();}
    void transport(boolean busy,boolean waiting,boolean recording){if(a.recButton.isSelected()!=!busy)paint(a.recButton,!busy);video.setEnabled(!busy&&!waiting&&!recording&&!a.recordPermissionPending&&!a.starting);}
}
