package pro.livemic.studio;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONObject;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Application-owned timeline. Disk operations and native preparation share one ordered worker. */
final class SessionController {
    private static SessionController instance;
    static synchronized SessionController get(Context c) {
        if(instance==null||instance.worker.isShutdown())instance=new SessionController(c.getApplicationContext());
        return instance;
    }
    final Context context; final SessionStore store;
    final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    volatile boolean ready,busy,arming,capturing,finalizing;
    private volatile boolean pendingPlayback,nativeActivated;
    private volatile long playbackRequest;
    volatile String error="",message="",exitFailure="";
    volatile SessionProject project;
    volatile int revision;
    volatile long[] state=new long[]{0,0,0,0,48000,0,0,0,-1,0,0};
    private volatile long recordToken;
    private volatile String commandToken="";
    private volatile boolean closing;
    private volatile long projectEpoch;
    private volatile Map<String,String> mediaProblems=Collections.emptyMap();
    static boolean legacyBlocked(){SessionController s=instance;return s!=null&&legacyBlocked(s.context);}
    static boolean legacyBlocked(Context c){SessionController s=instance;if(s==null)return false;boolean blocked=s.nativeActivated||s.arming||s.capturing||s.finalizing;if(blocked){s.error=AppLanguage.text(c,"다른 반주를 재생하려면 먼저 세션을 정지하세요.","Stop the session before playing other backing.");AudioService.error=s.error;s.revision++;}return blocked;}
    private interface Work { void run() throws Exception; }
    private SessionController(Context c){context=c;store=new SessionStore(c);submit(()->{List<SessionProject> all=store.list();project=all.isEmpty()?store.create("Session"):store.open(all.get(0).id);ready=true;try{recoverPending(false);}finally{refreshMediaProblems();}});}
    private void submit(Work work){submit(work,false);}
    private void submitMetadata(Work work){submit(work,true);}
    private synchronized void submit(Work work,boolean allowExit){
        if(closing||ExitCoordinator.isExitRequested())return;
        if(busy){error=AppLanguage.text(context,"세션 작업이 끝난 뒤 다시 시도하세요.","Session is busy. Try again when the current operation finishes.");revision++;return;}
        busy=true;error="";long generation=ExitCoordinator.generation();
        worker.execute(()->{try{if(allowExit||generation==ExitCoordinator.generation()&&!ExitCoordinator.isExitRequested())work.run();}catch(Exception e){error=e.getMessage()==null?e.toString():e.getMessage();if(allowExit&&(closing||ExitCoordinator.isExitRequested()))exitFailure=error;}finally{busy=false;revision++;}});
    }
    private void stopped() {if(arming||capturing||finalizing||state[1]==1)throw new IllegalStateException(AppLanguage.text(context,"세션을 정지한 뒤 편집하세요.","Stop the session before editing."));}
    private SessionProject copy()throws Exception{return SessionProject.read(project.json());}
    private void disableNative(){playbackRequest++;pendingPlayback=false;if(nativeActivated)NativeSession.setEnabled(false);nativeActivated=false;state=new long[]{0,0,0,0,48000,0,0,0,-1,0,0};}
    private void refreshNativeState(){if(nativeActivated)state=NativeSession.state();}
    private void commit(SessionProject p)throws Exception{store.saveFinalization(p);project=p;refreshMediaProblems();disableNative();refreshNativeState();}
    void rename(String id,String name){submitMetadata(()->{stopped();SessionProject p=id.equals(project.id)?copy():store.open(id);p.name=name;store.saveFinalization(p);if(id.equals(project.id))project=p;});}
    void duplicate(String id){submit(()->{stopped();SessionProject p=store.open(id);project=store.duplicate(p,p.name+" copy");projectEpoch++;playbackRequest++;pendingPlayback=false;refreshMediaProblems();disableNative();});}
    void delete(String id){submit(()->{stopped();store.delete(id);if(project.id.equals(id)){List<SessionProject> all=store.list();project=all.isEmpty()?store.create("Session"):all.get(0);projectEpoch++;playbackRequest++;pendingPlayback=false;refreshMediaProblems();disableNative();}});}
    void moveClip(String id,long frame){submitMetadata(()->{stopped();SessionProject p=copy();for(SessionProject.Clip c:p.clips)if(c.id.equals(id))c.startFrame=frame;commit(p);});}
    void setTrackMix(String id,float gain,float pan,boolean muted){submitMetadata(()->{stopped();SessionProject p=copy();SessionProject.Track t=p.track(id);if(t==null)throw new IllegalArgumentException("Missing track");t.gain=gain;t.pan=pan;t.muted=muted;commit(p);});}
    void discardPendingRecording(){submitMetadata(()->{stopped();SessionProject p=copy();JSONObject target=p.pendingTarget;if(target==null)return;String track=target.optString("trackId");if(target.optBoolean("createdTrack")){boolean used=false;for(SessionProject.Clip c:p.clips)if(track.equals(c.trackId))used=true;if(!used)p.tracks.removeIf(t->track.equals(t.id));}p.pendingTarget=null;commit(p);});}
    void removeClip(String id){submitMetadata(()->{stopped();SessionProject p=copy();p.clips.removeIf(c->id.equals(c.id));commit(p);});}
    void removeTrack(String id){submitMetadata(()->{stopped();SessionProject p=copy();if(p.pendingTarget!=null&&id.equals(p.pendingTarget.optString("trackId")))throw new IllegalStateException(AppLanguage.text(context,"먼저 미완료 녹음 연결 정보를 처리하세요.","Resolve the pending recording before removing its track."));p.clips.removeIf(c->id.equals(c.trackId));p.tracks.removeIf(t->id.equals(t.id));commit(p);});}
    void attachPendingRecording(){submit(()->{stopped();recoverPending(true);});}
    private void refreshMediaProblems(){Map<String,String> found=new HashMap<>();SessionProject p=project;if(p!=null)for(SessionProject.Clip c:p.clips)try{if(!store.mediaFile(p,p.media(c.mediaId)).isFile())found.put(c.id,"missing");}catch(Exception e){found.put(c.id,"invalid");}mediaProblems=Collections.unmodifiableMap(found);}
    String mediaProblem(String clipId){String value=mediaProblems.get(clipId);return value==null?"":"missing".equals(value)?AppLanguage.text(context,"오디오 파일 없음","Missing audio file"):AppLanguage.text(context,"잘못된 오디오 파일","Invalid audio file");}
    void updateComposer(String sessionId,ComposerProject value){
        final ComposerProject snapshot;try{snapshot=ComposerProject.read(value.json());}catch(Exception e){error=e.getMessage();revision++;return;}
        long epoch=projectEpoch;if(closing||ExitCoordinator.isExitRequested()||worker.isShutdown())return;
        worker.execute(()->{try{if(epoch!=projectEpoch||!project.id.equals(sessionId))return;stopped();SessionProject p=copy();p.composer=snapshot;store.saveFinalization(p);project=p;refreshMediaProblems();disableNative();}catch(Exception e){error=e.getMessage();if(closing||ExitCoordinator.isExitRequested())exitFailure=error;}finally{revision++;}});
    }
    void pause(){playbackRequest++;pendingPlayback=false;if(capturing||arming){finishRecording();return;}submit(()->{if(nativeActivated)NativeSession.transport(2);refreshNativeState();});}
    void newProject(String name){submit(()->{stopped();project=store.create(name);projectEpoch++;playbackRequest++;pendingPlayback=false;refreshMediaProblems();disableNative();});}
    void open(String id){submit(()->{stopped();SessionProject p=store.open(id);project=p;projectEpoch++;playbackRequest++;pendingPlayback=false;refreshMediaProblems();disableNative();recoverPending(false);});}
    void save(){submitMetadata(()->{stopped();SessionProject p=copy();store.saveFinalization(p);project=p;message=AppLanguage.text(context,"세션을 저장했습니다.","Session saved.");});}
    void listTakes(Consumer<List<JSONObject>> done){if(!worker.isShutdown())worker.execute(()->{TakeStore takes=new TakeStore(context,false);try{takes.awaitRecovery();List<JSONObject> items=takes.list();main.post(()->done.accept(items));}catch(Exception e){error=e.getMessage();revision++;}});}
    void list(Consumer<List<SessionProject>> done){if(!worker.isShutdown())worker.execute(()->{List<SessionProject> items=store.list();main.post(()->done.accept(items));});}
    void importComposer(ComposerProject source){submit(()->{stopped();if(AudioService.youtubeSelected)throw new IllegalStateException(AppLanguage.text(context,"반주를 가져오려면 YouTube 모드를 종료하세요.","Leave YouTube mode before importing backing."));SessionProject p=copy();store.importComposer(p,source);commit(p);});}
    void importHarmony(HarmonySession source){submit(()->{stopped();if(AudioService.youtubeSelected)throw new IllegalStateException(AppLanguage.text(context,"반주를 가져오려면 YouTube 모드를 종료하세요.","Leave YouTube mode before importing backing."));SessionProject p=copy();store.importHarmony(p,source);commit(p);});}
    void importHarmony(JSONObject source){submit(()->{stopped();if(AudioService.youtubeSelected)throw new IllegalStateException(AppLanguage.text(context,"반주를 가져오려면 YouTube 모드를 종료하세요.","Leave YouTube mode before importing backing."));SessionProject p=copy();p.harmony=new JSONObject(source.toString());commit(p);});}
    void importTake(String id,boolean vocal){submit(()->{stopped();if(AudioService.youtubeSelected)throw new IllegalStateException(AppLanguage.text(context,"반주를 가져오려면 YouTube 모드를 종료하세요.","Leave YouTube mode before importing backing."));TakeStore takes=new TakeStore(context,false);takes.recoverAsync(Runnable::run,null);takes.awaitRecovery();SessionProject p=copy();try(TakeRecovery.Lease ignored=takes.lease(id)){if(vocal){JSONObject meta=takes.read(id);if(!meta.optBoolean("sourceVocalRetained"))throw new IllegalStateException(AppLanguage.text(context,"보컬 원음이 없습니다. 원본 믹스를 직접 선택하세요.","No vocal stem is available. Choose the original mix explicitly."));SessionStore.inspectWav(takes.file(id,".vocal.wav"));}store.importTake(p,takes,id,0,vocal);}commit(p);});}
    void importTake(String id){importTake(id,true);}
    private void prepare()throws Exception{
        SessionProject p=project;p.validate();refreshMediaProblems();NativeComposer.clearSolo();
        if(p.composer!=null){if(!ComposerSession.get(context).ready)throw new IllegalStateException(AppLanguage.text(context,"반주 음원을 준비하고 있습니다. 잠시 후 다시 시도하세요.","Preparing composer sounds. Try again shortly."));ComposerTransport.Plan plan=new ComposerTransport().compile(p.composer,-1);if(!NativeComposer.publish(plan.events,plan.beats,plan.bar,plan.bpm,plan.key,plan.scale))throw new IllegalStateException("Composer playback plan is busy.");}
        else if(!NativeComposer.publish(new double[0],4,4,78,0,0))throw new IllegalStateException("Composer playback plan is busy.");
        int count=p.clips.size();String[] paths=new String[count];long[] clips=new long[count*7];double[] gains=new double[count*2];
        for(int i=0;i<count;i++){SessionProject.Clip c=p.clips.get(i);SessionProject.Media m=p.media(c.mediaId);SessionProject.Track t=p.track(c.trackId);paths[i]=store.mediaFile(p,m).getAbsolutePath();int k=i*7;clips[k]=p.tracks.indexOf(t);clips[k+1]=c.startFrame;clips[k+2]=c.sourceOffsetFrames;clips[k+3]=c.lengthFrames;clips[k+4]=c.loop?1:0;clips[k+5]=c.loopLengthFrames;clips[k+6]=m.sampleRate;gains[2*i]=t.muted?0:t.gain;gains[2*i+1]=t.pan;}
        double bpm=p.composer==null?78:MusicTheory.quarterBpm(p.composer.bpm,p.composer.meter);
        nativeActivated=true;if(!NativeSession.prepare(paths,clips,gains,p.durationFrames(),bpm))throw new IllegalStateException("Could not prepare session media.");NativeSession.setEnabled(true);
    }
    boolean ownsAudio(){return pendingPlayback||arming||capturing||finalizing||state[0]!=0&&state[1]==1;}
    void playPause(){if(capturing||arming){finishRecording();return;}submit(()->{requireNoForeignCapture();if(AudioService.youtubeSelected)throw new IllegalStateException(AppLanguage.text(context,"세션을 재생하려면 YouTube 모드를 종료하세요.","Leave YouTube mode before playing a session."));refreshNativeState();if(state[1]==1){NativeSession.transport(2);}else{if(state[0]==0)prepare();NativeHarmony.transport(0);NativeAudio.playLocal(false);if(!NativeSession.transport(1)){disableNative();throw new IllegalStateException("Session could not start.");}requestPlayback();}refreshNativeState();});}
    private void requireNoForeignCapture(){
        if(RecordingManager.active||AudioService.waiting()||AudioService.practice!=null&&AudioService.practice.active)throw new IllegalStateException(AppLanguage.text(context,"현재 녹음이나 연습을 먼저 마치세요.","Finish the current recording or practice first."));
        ComposerSession composer=ComposerSession.get(context);HarmonySession harmony=HarmonySession.get(context);
        if(composer.capturing||harmony.state[4]>=0||harmony.state[5]>=0)throw new IllegalStateException(AppLanguage.text(context,"프레이즈 또는 하모니 녹음을 먼저 마치세요.","Finish the phrase or harmony capture first."));
    }
    private void applyProjectHarmony()throws Exception{JSONObject h=new JSONObject(project.harmony.toString());if(h.length()==0)return;int key=h.optInt("key",0),scale=h.optInt("scale",0),preset=h.optInt("preset",0);float wet=(float)h.optDouble("wet",.45),dry=(float)h.optDouble("dry",1),spread=(float)h.optDouble("spread",.65);if(key<0||key>=MusicTheory.ROOTS.length||scale<0||scale>=MusicTheory.SCALES.length||preset<0||preset>3||!Float.isFinite(wet)||wet<0||wet>1||!Float.isFinite(dry)||dry<0||dry>1||!Float.isFinite(spread)||spread<0||spread>1)throw new IllegalStateException("Invalid session harmony configuration.");NativeHarmony.configure(h.optBoolean("auto",false),key,scale,preset,wet,dry,spread);}
    private void restoreStandaloneHarmony(){main.post(()->HarmonySession.get(context).configure());}
    private void requestPlayback(){long request=++playbackRequest,epoch=projectEpoch,generation=ExitCoordinator.generation();pendingPlayback=true;Intent i=new Intent(context,AudioService.class).setAction(AudioService.PLAYBACK).putExtra("exitGeneration",generation);main.post(()->{try{if(!closing&&!ExitCoordinator.isExitRequested()&&request==playbackRequest&&epoch==projectEpoch&&generation==ExitCoordinator.generation())context.startForegroundService(i);}catch(Exception e){error=e.getMessage();routeLost();}finally{if(request==playbackRequest)pendingPlayback=false;}});}
    void stop(){playbackRequest++;pendingPlayback=false;if(capturing||arming){finishRecording();return;}submit(()->{if(nativeActivated)NativeSession.transport(0);disableNative();});}
    void seek(long frame){if(capturing||arming){finishRecording();return;}submit(()->{if(!nativeActivated)return;if(!NativeSession.seek(Math.max(0,frame)))throw new IllegalStateException("Pause before seeking.");refreshNativeState();});}
    void record(String trackId){submit(()->{
        if(project.pendingTarget!=null)throw new IllegalStateException(AppLanguage.text(context,"기존 녹음을 연결하거나 연결 정보만 지운 뒤 녹음하세요.","Attach or discard the pending recording association before recording again."));requireNoForeignCapture();
        if(AudioService.youtubeSelected||RecordingManager.active||AudioService.waiting())throw new IllegalStateException(AppLanguage.text(context,"현재 녹음을 마치고 YouTube 모드를 종료하세요.","Finish the current recording and leave YouTube mode first."));
        if(arming||capturing||finalizing)throw new IllegalStateException(AppLanguage.text(context,"현재 세션 녹음을 먼저 마치세요.","Finish the current session recording first."));if(nativeActivated)NativeSession.transport(2);refreshNativeState();SessionProject p=copy();SessionProject.Track t=p.track(trackId);boolean createdTrack=t==null;if(t==null){if(p.tracks.size()>=SessionProject.MAX_AUDIO_TRACKS)throw new IllegalStateException(AppLanguage.text(context,"세션 트랙 한도에 도달했습니다.","Session track limit reached."));t=new SessionProject.Track();t.name="Vocal";t.kind="VOCAL";p.tracks.add(t);}
        commandToken=UUID.randomUUID().toString();recordToken=UUID.randomUUID().getMostSignificantBits()&Long.MAX_VALUE;if(recordToken==0)recordToken=1;
        JSONObject target=new JSONObject().put("token",commandToken).put("nativeToken",recordToken).put("sessionId",p.id).put("trackId",t.id).put("createdTrack",createdTrack).put("startFrame",state[2]).put("manualOffsetMs",RecordingSyncProfiles.current(context).offsetMs).put("placementPolicy","native-anchor-plus-manual-offset");
        store.writePendingTarget(p,target);project=p;arming=true;
        try{if(state[0]==0)prepare();}catch(Exception e){arming=false;throw e;}
        Intent i=new Intent(context,AudioService.class).setAction(AudioService.RECORD).putExtra("sessionToken",commandToken).putExtra("bpm",p.composer==null?78:p.composer.bpm).putExtra("exitGeneration",ExitCoordinator.generation());
        String token=commandToken;long generation=ExitCoordinator.generation();main.post(()->{try{if(!ExitCoordinator.isExitRequested()&&generation==ExitCoordinator.generation()&&acceptsRecord(token))context.startForegroundService(i);}catch(Exception e){recordStartFailed(e.getMessage());}});
    });}
    boolean acceptsRecord(String token){return arming&&!commandToken.isEmpty()&&commandToken.equals(token)&&!closing;}
    void beginServiceRecording(String token,int tempo){
        if(!acceptsRecord(token))return;
        worker.execute(()->{try{if(!acceptsRecord(token)||ExitCoordinator.isExitRequested())return;applyProjectHarmony();NativeSession.recordBus(true);if(!NativeSession.armCapture(recordToken))throw new IllegalStateException("Could not arm session capture.");
            if(!RecordingManager.startSession(context,tempo,project.pendingTarget))throw new IllegalStateException(RecordingManager.startError.isEmpty()?"Could not start session recording.":RecordingManager.startError);
            JSONObject target=new JSONObject(project.pendingTarget.toString()).put("takeId",RecordingManager.id);SessionProject candidate=copy();store.writePendingTarget(candidate,target);project=candidate;capturing=true;arming=false;if(!NativeSession.transport(1))throw new IllegalStateException("Session recording could not start.");refreshNativeState();message=AppLanguage.text(context,"세션 녹음을 시작했습니다.","Session recording started.");
        }catch(Exception e){if(RecordingManager.active){capturing=true;arming=false;error=e.getMessage();finishServiceRecording("Session target could not be updated; recording kept for recovery.");}else recordStartFailed(e.getMessage());}finally{revision++;}});
    }
    void recordStartFailed(String reason){restoreStandaloneHarmony();if(nativeActivated){NativeSession.finishCapture();NativeSession.recordBus(false);}arming=false;error=reason==null?"Could not start session recording.":reason;revision++;}
    void finishRecording(){long generation=ExitCoordinator.generation();String token=commandToken;main.post(()->{if(!ExitCoordinator.isExitRequested()&&generation==ExitCoordinator.generation()&&token.equals(commandToken))context.startService(new Intent(context,AudioService.class).setAction(AudioService.FINISH).putExtra("exitGeneration",generation));});}
    synchronized void finishServiceRecording(String warning){
        if(finalizing)return;finalizing=true;arming=false;capturing=false;
        worker.execute(()->{try{NativeSession.finishCapture();NativeSession.transport(2);refreshNativeState();RecordingManager.extraWarning=warning;RecordingManager.Result result=RecordingManager.stop();NativeSession.recordBus(false);
            if(result!=null){JSONObject target=new JSONObject(project.pendingTarget.toString()).put("takeId",result.id);if(state[8]>=0)target.put("startFrame",state[8]).put("anchored",true);SessionProject candidate=copy();store.writePendingTargetFinalization(candidate,target);project=candidate;if(!closing&&!ExitCoordinator.isExitRequested())recoverPending(true);message=AppLanguage.text(context,"세션 보컬을 저장했습니다.","Session vocal saved.");}
        }catch(Exception e){error=e.getMessage()==null?e.toString():e.getMessage();if(closing||ExitCoordinator.isExitRequested())exitFailure=error;}finally{NativeSession.recordBus(false);restoreStandaloneHarmony();capturing=false;arming=false;finalizing=false;revision++;}});
    }
    private void recoverPending(boolean attach)throws Exception{
        JSONObject target=project.pendingTarget;if(target==null)return;TakeStore takes=new TakeStore(context,false);takes.recoverAsync(Runnable::run,null);takes.awaitRecovery();
        if(!target.has("takeId")){for(JSONObject take:takes.list()){JSONObject saved=take.optJSONObject("sessionTarget");if(saved!=null&&target.optString("token").equals(saved.optString("token"))){target=new JSONObject(target.toString()).put("takeId",take.getString("id"));SessionProject candidate=copy();store.writePendingTarget(candidate,target);project=candidate;break;}}}
        if(!target.has("takeId"))return;String id=target.getString("takeId");
        if(!attach){message=AppLanguage.text(context,"복구된 세션 녹음이 있습니다. 확인한 뒤 직접 연결하세요.","A recovered session recording is available. Review and attach it explicitly.");return;}
        if(!target.optBoolean("anchored")) {message=AppLanguage.text(context,"녹음 위치를 확인할 수 없어 복구된 녹음을 보관함에 유지했습니다.","A recovered recording is kept in Library; its session position could not be confirmed.");return;}
        SessionProject p=copy();String originalTrack=target.getString("trackId");p.pendingTarget=null;SessionProject.Clip clip=store.importTakeToTrack(p,takes,id,originalTrack,0);placeRecording(clip,target.getLong("startFrame"),target.optLong("manualOffsetMs",0));store.save(p);project=p;refreshMediaProblems();disableNative();refreshNativeState();
    }
    static void placeRecording(SessionProject.Clip clip,long anchor,long offsetMs){
        long position=Math.addExact(anchor,Math.multiplyExact(offsetMs,48L));
        if(position<0){long crop=Math.negateExact(position);if(crop>=clip.lengthFrames)throw new IllegalStateException("Recording offset exceeds the captured audio.");clip.sourceOffsetFrames=Math.addExact(clip.sourceOffsetFrames,crop);clip.lengthFrames-=crop;clip.startFrame=0;}else clip.startFrame=position;
    }
    void poll(){if(!ready||!nativeActivated||state[0]==0&&!capturing&&!arming)return;refreshNativeState();if(state[1]==3&&!capturing&&!arming&&!finalizing&&!busy){submit(()->{if(nativeActivated)NativeSession.transport(0);disableNative();});return;}if(capturing&&state[8]>=0&&project.pendingTarget!=null&&!project.pendingTarget.optBoolean("anchored")&&!busy){submit(()->{if(project.pendingTarget!=null){JSONObject t=new JSONObject(project.pendingTarget.toString()).put("startFrame",state[8]).put("anchored",true);SessionProject candidate=copy();store.writePendingTarget(candidate,t);project=candidate;}});}}
    void routeLost(){pendingPlayback=false;playbackRequest++;if(capturing||arming)finishServiceRecording("Device disconnected; the session recording was finalized.");if(!worker.isShutdown())worker.execute(()->{if(nativeActivated)NativeSession.transport(2);refreshNativeState();});}
    void finishAndThen(Runnable done){if(capturing||arming)finishServiceRecording("");if(worker.isShutdown()){main.post(done);return;}worker.execute(()->main.post(done));}
    void drainForExit(Consumer<String> done){drainForExit(()->done.accept(exitFailure));}
    void drainForExit(Runnable done){if(!closing&&!ExitCoordinator.isExitRequested())exitFailure="";boolean pending=arming||capturing||finalizing||RecordingManager.active&&project!=null&&project.pendingTarget!=null;closing=true;pendingPlayback=false;playbackRequest++;if(pending)finishServiceRecording("");else arming=false;if(worker.isShutdown()){main.post(done);return;}worker.execute(()->{if(nativeActivated){NativeSession.transport(0);disableNative();NativeSession.recordBus(false);}worker.shutdown();main.post(done);});}
}
