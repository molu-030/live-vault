package pro.livemic.studio;

import android.Manifest;
import android.content.pm.PackageManager;
import android.text.InputType;
import android.text.TextPaint;
import android.view.Gravity;
import android.view.View;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.widget.*;
import org.json.JSONObject;
import java.util.*;

/** One saved project and one canonical 48 kHz timeline. Views never perform file work. */
final class SessionScreen {
    final MainActivity a;
    final SessionController s;
    TextView lcd, status;
    Button play, record, stop;
    SeekBar position;
    Timeline timeline;
    int drawnRevision=-1;
    String targetTrack;
    List<SessionProject> library=Collections.emptyList();
    boolean loadingLibrary, drawnLocked;
    SessionScreen(MainActivity a,SessionController s){this.a=a;this.s=s;}
    String tr(String ko,String en){return AppLanguage.text(a,ko,en);}
    static String time(long frame){long seconds=Math.max(0,frame)/SessionProject.RATE;return String.format(Locale.US,"%02d:%02d",seconds/60,seconds%60);}
    static long barFrames(SessionProject p){return p.composer==null?SessionProject.RATE*60L*4/78:Math.round(MusicTheory.barBeats(p.composer.meter)*60*SessionProject.RATE/MusicTheory.quarterBpm(p.composer.bpm,p.composer.meter));}
    boolean locked(){return s.busy||s.arming||s.capturing||s.finalizing||s.state[1]==1;}
    void refresh(){if(a.tab!=10&&!(a.tab==4&&a.libraryFilter==2))return;int y=a.pageScroll.getScrollY();a.showTab();a.pageScroll.post(()->a.pageScroll.scrollTo(0,y));}
    Button action(String label,Runnable action){Button b=a.button(label,action);b.setEnabled(!locked()&&s.ready);return b;}
    void pair(Button one,Button two){LinearLayout row=a.row();a.weighted(row,one,48);a.weighted(row,two,48);a.add(row);}
    void name(String title,String value,java.util.function.Consumer<String> save){EditText input=new EditText(a);input.setText(value);input.setSingleLine(true);input.setSelectAllOnFocus(true);input.setTextColor(MainActivity.TEXT);StudioDialogs.form(a,title,input,tr("저장","Save"),()->{String n=input.getText().toString().trim();if(!n.isEmpty())save.accept(n);},null);}
    void draw(){
        drawnRevision=s.revision;drawnLocked=locked();
        SessionProject p=s.project;
        a.add(a.title(tr("세션","Session")));
        if(!s.ready||p==null){a.note(tr("세션을 불러오는 중입니다…","Loading session…"));if(!s.error.isEmpty())a.add(a.text(s.error,StudioTheme.TYPE_LABEL,MainActivity.RED));return;}
        a.note(p.name);
        LinearLayout before=a.beginCard();
        lcd=a.text("",24,MainActivity.LIME);lcd.setTypeface(StudioTheme.mono(a));lcd.setGravity(Gravity.CENTER);lcd.setMinHeight(a.dp(72));lcd.setTag("session-lcd");a.add(lcd);
        status=a.text("",StudioTheme.TYPE_LABEL,MainActivity.MUTED);a.add(status);
        position=new SeekBar(a);position.setMax(1000);position.setContentDescription(tr("세션 재생 위치","Session playback position"));position.setMinimumHeight(a.dp(48));position.setEnabled(!locked());
        position.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar v,int n,boolean user){}public void onStartTrackingTouch(SeekBar v){}public void onStopTrackingTouch(SeekBar v){s.seek(p.durationFrames()*v.getProgress()/1000);}});a.add(position);a.endCard(before);
        pair(action(tr("새 세션","New session"),()->name(tr("새 세션","New session"),tr("내 세션","My session"),n->{a.composer.unbindSession();targetTrack=null;s.newProject(n);})),action(tr("열기","Open"),this::open));
        pair(action(tr("저장","Save"),s::save),action(tr("가져오기","Import"),this::imports));
        if(a.composer.isSessionBound())a.add(action(tr("작곡기 편집 종료","Finish composer edit"),()->{a.composer.unbindSession();refresh();}));
        if(!s.error.isEmpty())a.add(a.text(s.error,StudioTheme.TYPE_LABEL,MainActivity.RED));
        if(!s.message.isEmpty())a.note(s.message);
        if(p.pendingTarget!=null&&!s.capturing&&!s.arming&&!s.finalizing){
            before=a.beginCard();a.note(tr("미완료 녹음 복구 · 저장된 원본을 확인하세요.","Unfinished recording recovery · the original is retained."));
            if(p.pendingTarget.has("takeId"))a.add(action(tr("이 세션 위치에 연결","Attach at saved session position"),s::attachPendingRecording));
            else a.note(tr("라이브러리에 복구된 테이크가 있으면 가져오기로 연결하세요.","Import the recovered take from Library when available."));
            a.add(action(tr("연결 정보만 지우고 원본 보관","Discard target and keep original"),()->confirm(tr("미완료 녹음 연결 정보 삭제","Discard unfinished recording target"),tr("세션의 연결 정보만 지웁니다. 녹음 원본은 보관함에 유지됩니다.","Only the session target is cleared. The original recording remains in Library."),s::discardPendingRecording)));
            a.endCard(before);
        }
        a.add(a.title(tr("공유 타임라인","Shared timeline")));
        if(p.composer!=null&&!p.tracks.isEmpty())a.note(tr("BPM·키 변경은 작곡기에 적용됩니다. 녹음된 클립의 속도와 음정은 유지됩니다.","BPM and key changes apply to the composer. Recorded clips keep their original speed and pitch."));
        timeline=new Timeline(p);a.add(timeline);
        if(p.composer!=null){before=a.beginCard();a.note(tr("작곡기 · ","Composer · ")+p.composer.totalBars()+tr(" 마디"," bars"));a.add(action(tr("세션 작곡기 편집","Edit session composer"),this::editComposer));a.endCard(before);}
        else a.add(action(tr("세션 작곡기 만들기","Create session composer"),this::editComposer));
        if(p.tracks.isEmpty())a.note(tr("반주나 테이크를 가져온 뒤 현재 위치에서 보컬을 겹쳐 녹음하세요.","Import backing or a take, then overdub your vocal at the current position."));
        for(SessionProject.Track t:p.tracks){
            before=a.beginCard();
            a.add(a.text(t.name+" · "+kind(t.kind),18,MainActivity.TEXT));
            pair(action(t.muted?tr("음소거 해제","Unmute"):tr("음소거","Mute"),()->s.setTrackMix(t.id,t.gain,t.pan,!t.muted)),action(tr("트랙 옵션","Track options"),()->trackOptions(t)));
            Button select=action(Objects.equals(targetTrack,t.id)?tr("녹음 대상 · 선택됨","Record target · selected"):tr("이 트랙에 보컬 녹음","Record vocal on this track"),()->{targetTrack=t.id;refresh();});a.add(select);
            for(SessionProject.Clip c:p.clips)if(c.trackId.equals(t.id)){
                a.note(tr("시작 ","Start ")+time(c.startFrame)+" · "+tr("길이 ","Length ")+time(c.lengthFrames)+" · "+tr("마디 ","Bar ")+(1+c.startFrame/barFrames(p)));
                String problem=s.mediaProblem(c.id);if(!problem.isEmpty())a.add(a.text(problem,StudioTheme.TYPE_LABEL,MainActivity.RED));
                a.add(action(tr("클립 위치 이동","Move clip"),()->move(p,c)));
            }
            a.endCard(before);
        }
        Button fresh=action(tr("새 보컬 트랙에 녹음","Record on a new vocal track"),()->{targetTrack=null;record();});fresh.setEnabled(!locked()&&p.pendingTarget==null);a.add(fresh);
        LinearLayout controls=a.row();
        play=a.button(tr("재생","Play"),s::playPause);stop=a.button(tr("정지","Stop"),s::stop);record=a.button(tr("보컬 녹음","Record vocal"),this::record);
        record.setContentDescription(tr("일시정지한 위치에서 보컬 녹음 · 녹음 중 다시 누르면 마침","Record vocal at the paused position · press again to finish"));
        a.weighted(controls,play,48);a.weighted(controls,stop,48);a.weighted(controls,record,48);a.actionDock.addView(controls,new LinearLayout.LayoutParams(-1,-2));poll();
    }
    /** Read-only overview; precise clip changes use the paused position sheet. */
    final class Timeline extends View {
        final SessionProject project;
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        final TextPaint label=new TextPaint(Paint.ANTI_ALIAS_FLAG);
        final int rows;
        Timeline(SessionProject p){
            super(a);project=p;rows=Math.max(1,p.tracks.size()+(p.composer==null?0:1));
            label.setTypeface(StudioTheme.body(a));label.setTextSize(12*a.getResources().getDisplayMetrics().scaledDensity);
            setMinimumHeight(a.dp(rows*48+56));
            StringBuilder summary=new StringBuilder(tr("공유 타임라인", "Shared timeline"));
            if(p.composer!=null)summary.append(". ").append(tr("작곡기", "Composer"));
            for(SessionProject.Track track:p.tracks){summary.append(". ").append(track.name);for(SessionProject.Clip clip:p.clips)if(clip.trackId.equals(track.id))summary.append(": ").append(tr("시작 ","start ")).append(time(clip.startFrame)).append(", ").append(tr("길이 ","length ")).append(time(clip.lengthFrames));}
            setContentDescription(summary.toString());
        }
        float graphLeft(){return Math.min(a.dp(144),Math.max(a.dp(88),getWidth()*.28f));}
        @Override protected void onMeasure(int width,int height){setMeasuredDimension(View.MeasureSpec.getSize(width),a.dp(rows*48+56));}
        String fitName(String text){
            float available=graphLeft()-a.dp(16);
            if(label.measureText(text)<=available)return text;
            int end=label.breakText(text,true,Math.max(0,available-label.measureText("…")),null);
            return text.substring(0,end)+"…";
        }
        void name(Canvas canvas,String text,float y){
            label.setColor(MainActivity.TEXT);label.setTextAlign(Paint.Align.LEFT);
            String fit=fitName(text);
            canvas.save();canvas.clipRect(0,y,graphLeft()-a.dp(8),y+a.dp(48));
            canvas.drawText(fit,a.dp(4),y+a.dp(24)-(label.ascent()+label.descent())/2,label);canvas.restore();
        }

        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);float left=graphLeft(),right=getWidth()-a.dp(8),top=a.dp(48),row=a.dp(48),width=right-left;
            long duration=Math.max(barFrames(project),project.durationFrames());
            paint.setColor(MainActivity.RAISED);canvas.drawRoundRect(left,top,right,top+rows*row,a.dp(12),a.dp(12),paint);
            int ticks=width<a.dp(400)?2:4;
            for(int i=0;i<=ticks;i++){
                float x=left+width*i/ticks;long frame=duration*i/ticks;
                paint.setColor(MainActivity.MUTED);paint.setAlpha(55);canvas.drawLine(x,top,x,top+rows*row,paint);paint.setAlpha(255);
                label.setColor(MainActivity.MUTED);label.setTextAlign(i==0?Paint.Align.LEFT:i==ticks?Paint.Align.RIGHT:Paint.Align.CENTER);
                canvas.drawText(time(frame),x,a.dp(16),label);
                canvas.drawText(tr("마디 ","Bar ")+(1+frame/barFrames(project)),x,a.dp(36),label);
            }
            int index=0;
            if(project.composer!=null){
                name(canvas,tr("작곡기","Composer"),top);
                long length=Math.round(project.composer.totalBars()*MusicTheory.barBeats(project.composer.meter)*60*SessionProject.RATE/MusicTheory.quarterBpm(project.composer.bpm,project.composer.meter));
                paint.setColor(MainActivity.LIME);paint.setAlpha(110);canvas.drawRoundRect(left+2,top+a.dp(8),left+width*length/duration,top+row-a.dp(8),a.dp(4),a.dp(4),paint);paint.setAlpha(255);index++;
            }
            for(SessionProject.Track track:project.tracks){
                float y=top+index++*row;name(canvas,track.name,y);paint.setColor("VOCAL".equals(track.kind)?MainActivity.LIME:MainActivity.MUTED);paint.setAlpha(track.muted?50:180);
                for(SessionProject.Clip clip:project.clips)if(clip.trackId.equals(track.id)){float x=left+width*clip.startFrame/duration,end=left+width*(clip.startFrame+clip.lengthFrames)/duration;canvas.drawRoundRect(x,y+a.dp(8),Math.max(x+a.dp(2),end),y+row-a.dp(8),a.dp(4),a.dp(4),paint);}
                paint.setAlpha(255);
            }
            canvas.save();canvas.clipRect(left,top,right,top+rows*row);paint.setColor(MainActivity.TEXT);paint.setStrokeWidth(a.dp(2));float head=left+width*Math.min(s.state[2],duration)/duration;canvas.drawLine(head,top,head,top+rows*row,paint);canvas.restore();
        }
    }
    void editComposer(){SessionProject p=s.project;String id=p.id;if(a.composer.bindSession(id,p.composer==null?new ComposerProject():p.composer,snapshot->s.updateComposer(id,snapshot),s::playPause,s::pause,s::stop,()->s.state[1]==1,this::locked))a.chooseTab(7);}
    void record(){if(s.arming||s.capturing){s.finishRecording();return;}if(locked())return;if(s.project!=null&&s.project.pendingTarget!=null){a.toast(tr("미완료 녹음을 연결하거나 연결 정보를 먼저 지우세요.","Attach or discard the unfinished recording target first."));return;}if(a.checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){a.requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},14);return;}s.record(targetTrack);}
    void poll(){if(lcd==null||s.project==null||a.tab!=10)return;if(drawnLocked!=locked()){refresh();return;}long[] state=s.state;boolean recording=s.arming||s.capturing||s.finalizing;
        lcd.setText(time(state[2])+" / "+time(s.project.durationFrames())+"\n"+tr("마디 ","Bar ")+(1+state[2]/barFrames(s.project)));
        status.setText(s.finalizing?tr("녹음 저장 중…","Saving recording…"):s.capturing?tr("보컬 오버더빙 중","Recording vocal overdub"):s.arming?tr("녹음 준비 중…","Arming recording…"):s.busy?tr("처리 중…","Working…"):state[1]==1?tr("재생 중 · 일시정지 후 보컬 녹음","Playing · pause to record vocal"):tr("일시정지 · 위치 편집 가능","Paused · position editing available"));
        if(timeline!=null)timeline.invalidate();
        if(position!=null){position.setEnabled(!locked());position.setProgress((int)(1000*Math.min(state[2],s.project.durationFrames())/Math.max(1,s.project.durationFrames())));}
        if(play!=null){play.setText(state[1]==1?tr("일시정지","Pause"):tr("재생","Play"));play.setEnabled(!s.busy&&!recording);stop.setEnabled(!s.busy&&!s.finalizing);record.setText(recording?tr("녹음 마침","Finish recording"):tr("보컬 녹음","Record vocal"));record.setEnabled(!s.busy&&!s.finalizing&&(recording||state[1]!=1&&s.project.pendingTarget==null));}
    }
    void imports(){StudioDialogs.choose(a,tr("세션에 가져오기","Import into session"),new String[]{tr("현재 작곡기 복사","Copy current composer"),tr("현재 보컬 화음 복사","Copy current vocal harmony"),tr("저장된 테이크","Saved take")},-1,i->{if(i==0)s.importComposer(a.composer.project);else if(i==1)s.importHarmony(a.harmony);else takes();});}
    String kind(String value){return "VOCAL".equals(value)?tr("보컬","Vocal"):"HARMONY".equals(value)?tr("화음","Harmony"):tr("오디오","Audio");}
    void takes(){s.listTakes(items->{
        if(a.isDestroyed()||a.exiting())return;
        String[] labels=new String[items.size()];for(int i=0;i<labels.length;i++){JSONObject t=items.get(i);labels[i]=t.optString("name",t.optString("id"));}
        if(labels.length==0){a.toast(tr("저장된 테이크가 없습니다.","No saved takes."));return;}
        StudioDialogs.choose(a,tr("테이크 가져오기","Import take"),labels,-1,i->takeSource(items.get(i)));
    });}
    void takeSource(JSONObject take){
        boolean vocal=take.optBoolean("sourceVocalRetained");
        String[] options=vocal?new String[]{tr("보컬만","Vocal only"),tr("원본 믹스 · 반주 포함 가능","Original mix · may include backing")}:new String[]{tr("원본 믹스 · 반주 포함 가능","Original mix · may include backing")};
        StudioDialogs.choose(a,tr("가져올 오디오","Audio to import"),options,-1,i->{
            String id=take.optString("id");
            if(vocal&&i==0){s.importTake(id,true);return;}
            StudioDialogs.alert(a).setTitle(tr("원본 믹스 가져오기","Import original mix"))
                    .setMessage(tr("원본에 반주가 포함되어 있으면 작곡기나 다른 반주와 겹쳐 들릴 수 있습니다.","If the original includes backing, it may overlap the composer or other backing tracks."))
                    .setNegativeButton(tr("취소","Cancel"),null).setPositiveButton(tr("가져오기","Import"),(d,w)->s.importTake(id,false)).show();
        });
    }
    void open(){s.list(items->{if(a.isDestroyed()||a.exiting())return;String[] names=new String[items.size()];for(int i=0;i<names.length;i++)names[i]=items.get(i).name;StudioDialogs.choose(a,tr("세션 열기","Open session"),names,-1,i->{a.composer.unbindSession();s.open(items.get(i).id);a.chooseTab(10);});});}
    void move(SessionProject p,SessionProject.Clip c){long bar=barFrames(p);StudioDialogs.choose(a,tr("클립 위치 · 일시정지 상태에서 이동","Clip position · move while paused"),new String[]{tr("한 마디 앞으로","One bar earlier"),tr("한 마디 뒤로","One bar later"),tr("현재 재생 위치로","To playhead"),tr("시작 시간 입력 (초)","Enter start time (seconds)"),tr("클립 연결 삭제","Remove clip from session")},-1,i->{if(i==4){confirm(tr("클립 연결 삭제","Remove clip from session"),tr("이 클립의 연결만 삭제합니다. 저장된 오디오 원본은 유지됩니다.","Remove this clip reference. Saved original audio is retained."),()->s.removeClip(c.id));return;}if(i==3){EditText n=new EditText(a);n.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);n.setText(String.format(Locale.US,"%.3f",c.startFrame/(double)SessionProject.RATE));StudioDialogs.form(a,tr("시작 시간 (초)","Start time (seconds)"),n,tr("적용","Apply"),()->{try{double seconds=Double.parseDouble(n.getText().toString());if(!Double.isFinite(seconds)||seconds<0)throw new NumberFormatException();s.moveClip(c.id,Math.round(seconds*SessionProject.RATE));}catch(NumberFormatException e){a.toast(tr("올바른 시간을 입력하세요.","Enter a valid time."));}},null);}else s.moveClip(c.id,i==0?Math.max(0,c.startFrame-bar):i==1?c.startFrame+bar:s.state[2]);});}
    void confirm(String title,String detail,Runnable done){StudioDialogs.alert(a).setTitle(title).setMessage(detail).setNegativeButton(tr("취소","Cancel"),null).setPositiveButton(tr("삭제","Remove"),(d,w)->done.run()).show();}
    void trackOptions(SessionProject.Track t){StudioDialogs.choose(a,t.name,new String[]{tr("믹스","Mix"),tr("트랙 연결 삭제","Remove track from session")},-1,i->{if(i==0)mix(t);else confirm(tr("트랙 연결 삭제","Remove track from session"),tr("이 트랙과 클립 연결만 삭제합니다. 저장된 오디오 원본은 유지됩니다.","Remove this track and its clip references. Saved original audio is retained."),()->{if(Objects.equals(targetTrack,t.id))targetTrack=null;s.removeTrack(t.id);});});}
    void mix(SessionProject.Track t){LinearLayout form=a.column();TextView gain=a.text(tr("게인","Gain")+" "+Math.round(t.gain*100)+"%",16,MainActivity.TEXT);form.addView(gain);SeekBar level=new SeekBar(a);level.setMax(200);level.setProgress(Math.round(t.gain*100));form.addView(level,new LinearLayout.LayoutParams(-1,a.dp(48)));TextView pan=a.text(tr("팬 · 왼쪽 / 중앙 / 오른쪽","Pan · left / center / right"),16,MainActivity.TEXT);form.addView(pan);SeekBar balance=new SeekBar(a);balance.setMax(200);balance.setProgress(Math.round(t.pan*100)+100);form.addView(balance,new LinearLayout.LayoutParams(-1,a.dp(48)));StudioDialogs.form(a,tr("트랙 믹스","Track mix"),form,tr("적용","Apply"),()->s.setTrackMix(t.id,level.getProgress()/100f,(balance.getProgress()-100)/100f,t.muted),null);}
    void projects(){projectList=a.column();a.add(a.title(tr("세션","Sessions")));a.add(a.button(tr("현재 세션 열기","Open current session"),()->a.chooseTab(10)));a.add(projectList);if(!loadingLibrary){loadingLibrary=true;s.list(items->{library=items;loadingLibrary=false;if(a.tab==4&&a.libraryFilter==2)renderProjects();});}renderProjects();}
    LinearLayout projectList;
    void renderProjects(){if(projectList==null||a.tab!=4||a.libraryFilter!=2)return;projectList.removeAllViews();for(SessionProject p:library){Button b=a.button(p.name,()->StudioDialogs.choose(a,p.name,new String[]{tr("열기","Open"),tr("이름 변경","Rename"),tr("복제","Duplicate"),tr("삭제","Delete")},-1,i->{if(i==0){a.composer.unbindSession();s.open(p.id);a.chooseTab(10);}else if(i==1)name(tr("세션 이름","Session name"),p.name,n->s.rename(p.id,n));else if(i==2){a.composer.unbindSession();s.duplicate(p.id);}else StudioDialogs.alert(a).setTitle(tr("세션 삭제","Delete session")).setMessage(p.name).setNegativeButton(tr("취소","Cancel"),null).setPositiveButton(tr("삭제","Delete"),(d,w)->{if(s.project!=null&&s.project.id.equals(p.id))a.composer.unbindSession();s.delete(p.id);}).show();}));projectList.addView(b,new LinearLayout.LayoutParams(-1,-2));}}
}
