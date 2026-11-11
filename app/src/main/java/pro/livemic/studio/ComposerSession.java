package pro.livemic.studio;

import android.content.*;
import android.os.*;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;

/** Service-owned project and transactional performance capture. No UI timer triggers audio. */
final class ComposerSession {
    private static ComposerSession instance;
    static synchronized ComposerSession get(Context c){if(instance==null||instance.worker.isShutdown())instance=new ComposerSession(c.getApplicationContext());return instance;}
    final Context context;final ComposerStore store;final ExecutorService worker=Executors.newSingleThreadExecutor();final Handler main=new Handler(Looper.getMainLooper());
    ComposerProject project=new ComposerProject();int selectedPart=0,revision=0;volatile boolean ready=false;boolean voiceOn=false,capturing=false;String error="",message="";
    // A session edit is an isolated workspace; the unrelated legacy draft and history stay intact.
    private SessionBinding binding;
    private volatile long publishGeneration;
    private static final class SessionBinding {
        final String id;final ComposerProject legacy;final int selected;
        final ArrayDeque<String> undo,redo;final PhraseComparison comparison;
        final Consumer<ComposerProject> sink;final Runnable play,pause,stop;
        final BooleanSupplier playing,editingLocked;
        final String saveId;final boolean dirty;final SaveState draft,library;
        SessionBinding(ComposerSession owner,String id,Consumer<ComposerProject> sink,Runnable play,Runnable pause,Runnable stop,BooleanSupplier playing,BooleanSupplier editingLocked){
            this.id=id;legacy=owner.project;selected=owner.selectedPart;undo=new ArrayDeque<>(owner.store.undo);redo=new ArrayDeque<>(owner.store.redo);comparison=owner.comparison;
            this.sink=sink;this.play=play;this.pause=pause;this.stop=stop;this.playing=playing;this.editingLocked=editingLocked;
            saveId=owner.saveProjectId;dirty=owner.libraryDirty;draft=copySave(owner.draftSave);library=copySave(owner.librarySave);
        }
    }
    private static SaveState copySave(SaveState value){SaveState result=new SaveState();restoreSave(result,value);return result;}
    private static void restoreSave(SaveState to,SaveState from){to.phase=from.phase;to.request=from.request;to.savedAt=from.savedAt;to.failure=from.failure;to.formattedAt=-1;}
    boolean isSessionBound(){return binding!=null;}
    synchronized boolean bindSession(String id,ComposerProject value,Consumer<ComposerProject> sink,Runnable play,Runnable pause,Runnable stop,BooleanSupplier playing,BooleanSupplier editingLocked){
        if(!ready||capturing||versionBusy||versionRestoreLock||ExitCoordinator.isExitRequested())return false;
        try{
            ComposerProject isolated=ComposerProject.read(value.json());isolated.validate();
            if(binding!=null)unbindSession();
            stop();voice(false);publishGeneration++;playbackRequest++;
            binding=new SessionBinding(this,id,sink,play,pause,stop,playing,editingLocked);
            project=isolated;selectedPart=0;comparison=null;store.undo.clear();store.redo.clear();
            saveEpoch++;saveProjectId="";ensureSaveProject();revision++;error="";return true;
        }catch(Exception e){error=displayError(e.getMessage());return false;}
    }
    synchronized void unbindSession(){
        SessionBinding previous=binding;if(previous==null)return;
        publishGeneration++;playbackRequest++;binding=null;project=previous.legacy;selectedPart=previous.selected;
        store.undo.clear();store.undo.addAll(previous.undo);store.redo.clear();store.redo.addAll(previous.redo);
        comparison=previous.comparison;saveEpoch++;saveProjectId=previous.saveId;libraryDirty=previous.dirty;
        restoreSave(draftSave,previous.draft);restoreSave(librarySave,previous.library);
        // A queued legacy save may have completed while its UI was suspended.
        if(draftSave.phase==SaveState.SAVING){draftSave.phase=SaveState.IDLE;draftSave.request++;}
        if(librarySave.phase==SaveState.SAVING){librarySave.phase=SaveState.IDLE;librarySave.request++;}
        revision++;if(comparison!=null)comparison.revision=revision;
    }
    private boolean sessionEditingLocked(){
        if(binding==null||!binding.editingLocked.getAsBoolean())return false;
        error=AppLanguage.text(context,"세션을 일시정지하고 작업을 마친 뒤 편집하세요.","Pause the session and finish the current operation before editing.");return true;
    }
    private boolean sessionLibraryBlocked(){
        if(binding==null)return false;
        error=AppLanguage.text(context,"세션으로 돌아간 뒤 다른 프로젝트를 여세요.","Return to the session before opening or managing another backing project.");return true;
    }
    static final class SaveState {
        static final int IDLE=0,SAVING=1,SAVED=2,FAILED=3;
        int phase=IDLE;long request,savedAt;String failure="";
        long formattedAt=-1;boolean formattedEnglish;String formattedTime="";
        void reset(){phase=IDLE;request++;savedAt=0;failure="";}
    }
    private static final class PhraseComparison {
        final ComposerProject project;final String partId,trackId;final List<String> previous,current;
        int revision;boolean currentSelected=true;
        PhraseComparison(ComposerProject p,String part,String track,List<String> old,List<String> next,int revision){project=p;partId=part;trackId=track;previous=old;current=next;this.revision=revision;}
    }
    private PhraseComparison comparison;
    private static List<String> phraseCopies(ComposerProject.Part part,String trackId)throws JSONException {
        List<String> copies=new ArrayList<>();for(VoicePhrase phrase:part.phrases)if(phrase.trackId.equals(trackId))copies.add(phrase.json().toString());return copies;
    }
    boolean canComparePhrase(){
        if(capturing||comparison==null||comparison.project!=project||comparison.revision!=revision)return false;
        for(ComposerProject.Part part:project.parts)if(part.id.equals(comparison.partId))for(TrackConfig track:part.tracks)if(track.id.equals(comparison.trackId))return true;
        return false;
    }
    private void nonEditingRevision(){
        boolean preserve=canComparePhrase();revision++;
        if(preserve)comparison.revision=revision;
    }
    boolean currentTakeSelected(){return canComparePhrase()&&comparison.currentSelected;}
    boolean previousTakeEmpty(){return !canComparePhrase()||comparison.previous.isEmpty();}
    void selectPhraseTake(boolean current,boolean finish){
        if(sessionEditingLocked()||restoreLocked()||!canComparePhrase())return;PhraseComparison saved=comparison;
        ComposerProject.Part target=project.part(saved.partId);List<VoicePhrase> before=new ArrayList<>(target.phrases);
        try{
            List<VoicePhrase> replacements=new ArrayList<>();for(String json:current?saved.current:saved.previous)replacements.add(VoicePhrase.read(new JSONObject(json)));
            target.phrases.removeIf(ph->ph.trackId.equals(saved.trackId));target.phrases.addAll(replacements);project.validate();
            // Capture already owns one undo checkpoint; repeated auditions must keep earlier edits.
            revision++;persist();publish(-1);saved.revision=revision;saved.currentSelected=current;
            comparison=finish?null:saved;
        }catch(Exception e){target.phrases.clear();target.phrases.addAll(before);comparison=null;error=displayError(e.getMessage());}
    }
    final SaveState draftSave=new SaveState(),librarySave=new SaveState();
    private String saveProjectId="",recordingTargetLabel="";private long saveEpoch;
    private boolean libraryDirty=true;
    private void ensureSaveProject(){if(!project.id.equals(saveProjectId)){saveProjectId=project.id;saveEpoch++;draftSave.reset();librarySave.reset();libraryDirty=true;}}
    private void projectChanged(){ensureSaveProject();libraryDirty=true;if(librarySave.phase==SaveState.SAVING){librarySave.request++;librarySave.phase=SaveState.IDLE;}}
    boolean canUndo(){return !capturing&&!versionRestoreLock&&!store.undo.isEmpty();}
    boolean canRedo(){return !capturing&&!versionRestoreLock&&!store.redo.isEmpty();}
    String saveStatus(boolean draft){
        if(binding!=null)return AppLanguage.text(context,"세션 · 편집 시 자동 저장","Session · Edits save automatically");
        ensureSaveProject();SaveState status=draft?draftSave:librarySave;
        String label=AppLanguage.text(context,draft?"초안":"내 반주",draft?"Draft":"My Backings");
        if(status.phase==SaveState.SAVING)return label+AppLanguage.text(context," · 저장 중…"," · Saving…");
        if(status.phase==SaveState.FAILED)return label+AppLanguage.text(context," · 저장 실패: "," · Save failed: ")+status.failure;
        boolean english=AppLanguage.english(context);
        if(status.formattedAt!=status.savedAt||status.formattedEnglish!=english){
            status.formattedTime=status.savedAt==0?"":android.text.format.DateFormat.getTimeFormat(context).format(new Date(status.savedAt));
            status.formattedAt=status.savedAt;status.formattedEnglish=english;
        }
        String time=status.formattedTime;
        if(!draft&&libraryDirty)return label+AppLanguage.text(context," · 저장하지 않은 변경사항"," · Unsaved changes")+(time.isEmpty()?"":AppLanguage.text(context," · 마지막 저장 "," · Last saved ")+time);
        if(status.phase==SaveState.SAVED)return label+AppLanguage.text(context," · 저장됨 "," · Saved ")+time;
        return label+AppLanguage.text(context," · 편집 시 자동 저장"," · Edits save automatically");
    }
    boolean saveFailed(boolean draft){ensureSaveProject();return (draft?draftSave:librarySave).phase==SaveState.FAILED;}
    void retrySave(boolean draft){if(draft)queueSave(true);else save();}
    private int playbackRequest=0;private VoicePhrase pendingPhrase;private VoicePhrase.Note pendingNote;private String recordingPartId,recordingTrackId,recordingBefore;private TrackConfig recordingNewTrack;private ComposerProject recordingProject;private double[] state={0,0,0,0,32,-1,-1,0,0,0};
    private ComposerSession(Context c){context=c;store=new ComposerStore(c);worker.execute(()->{try{
        ComposerProject draft=store.draft();if(draft==null){draft=new ComposerProject();if(AppLanguage.english(c)){draft.name="New backing";String[] names={"Guitar","Bass","Drums","Keyboard"};for(int i=0;i<names.length;i++)draft.parts.get(0).tracks.get(i).name=names[i];}android.content.SharedPreferences old=c.getSharedPreferences("MainActivity",0);if(old.contains("p15")){draft.bpm=Math.max(40,Math.min(240,Math.round(old.getFloat("p15",90))));draft.transpose(MusicTheory.pc(Math.round(old.getFloat("p16",0))));}}
        File sf=new File(c.getFilesDir(),"generaluser-gs-1.44.sf2");if(sf.length()!=31279314L){android.util.AtomicFile atomic=new android.util.AtomicFile(sf);FileOutputStream out=atomic.startWrite();try(InputStream in=c.getAssets().open("soundfonts/generaluser-gs-1.44.sf2")){byte[] b=new byte[65536];int n;while((n=in.read(b))>0)out.write(b,0,n);atomic.finishWrite(out);}catch(Exception e){atomic.failWrite(out);throw e;}}
        if(!NativeComposer.loadSoundFont(sf.getAbsolutePath()))throw new IOException(AppLanguage.text(context,"음원 초기화 실패","Sound initialization failed"));ComposerProject restored=draft;main.post(()->{project=restored;ensureSaveProject();long stamp=store.savedAt(restored.id,true);if(stamp>0){draftSave.phase=SaveState.SAVED;draftSave.savedAt=stamp;}ready=true;revision++;publish(-1);});
    }catch(Exception e){main.post(()->{error=AppLanguage.text(context,"작곡기 준비 실패: ","Composer setup failed: ")+displayError(e.getMessage());revision++;});}});}
    void drainForExit(Runnable done) {
        unbindSession();
        playbackRequest++;
        if (capturing) poll(); // Keep a phrase already completed by native audio.
        if (capturing) cancelPhrase(); // Unfinished capture keeps the previous phrase.
        NativeComposer.record(false);
        NativeComposer.transport(0);
        voiceOn = false;
        applyVoice();
        AudioService.composerRequested = false;
        // First marker lets earlier main-thread completions enqueue their final writes.
        worker.execute(() -> main.post(() -> worker.execute(() -> main.post(() -> {
            NativeComposer.transport(0);
            voiceOn = false;
            applyVoice();
            worker.shutdown();
            done.run();
        }))));
    }
    double[] state(){return state;}
    boolean playing(){return binding!=null?binding.playing.getAsBoolean():state[1]>.5;}
    ComposerProject.Part part(){selectedPart=Math.max(0,Math.min(selectedPart,project.parts.size()-1));return project.parts.get(selectedPart);}
    String displayError(String value){
        if(!AppLanguage.english(context)||value==null)return value;
        switch(value){
            case "없는 파트": return "Part not found.";
            case "파트 최대 8개 · 곡 최대 256마디": return "Maximum 8 parts and 256 bars per song.";
            case "중복 파트 / 트랙 한도": return "Duplicate part or track limit exceeded.";
            case "악기는 최대 8트랙입니다.": return "A maximum of 8 instrument tracks is allowed.";
            case "반복 횟수": return "Invalid repeat count.";
            case "캐논은 8·16마디에서 선택하세요.": return "Choose Canon for an 8- or 16-bar part.";
            case "파트 길이": return "Invalid part length.";
            case "곡 최대 256마디": return "A song can have at most 256 bars.";
            case "파트 최대 8개": return "A maximum of 8 parts is allowed.";
            case "파트 데이터": return "Invalid part data.";
            case "마디 코드 수": return "Invalid number of chords in a bar.";
            case "지원하지 않는 프로젝트 버전": return "Unsupported project version.";
            case "프로젝트 크기": return "Project size exceeds the limit.";
            default: return value;
        }
    }
    void edit(Consumer<ComposerProject> change,boolean structure){if(sessionEditingLocked()||restoreLocked())return;if(capturing){error=AppLanguage.text(context,"프레이즈 기록을 마친 뒤 편집하세요.","Finish recording the phrase before editing.");return;}if(structure&&playing()){error=AppLanguage.text(context,"일시정지 후 파트 구성을 편집하세요.","Pause playback before editing parts.");return;}
        comparison=null;
        try{String before=project.json().toString();try{change.accept(project);project.validate();}catch(Exception e){project=ComposerProject.read(new JSONObject(before));throw e;}store.checkpoint(ComposerProject.read(new JSONObject(before)));revision++;persist();publish(-1);if(voiceOn)applyVoice();}catch(Exception e){error=displayError(e.getMessage());}}
    void persist(){projectChanged();queueSave(true);}
    private void queueSave(boolean draft){
        if(binding!=null){
            try{binding.sink.accept(ComposerProject.read(project.json()));draftSave.phase=SaveState.SAVED;draftSave.savedAt=System.currentTimeMillis();}
            catch(Exception e){draftSave.phase=SaveState.FAILED;draftSave.failure=displayError(e.getMessage());error=draftSave.failure;}
            return;
        }
        ensureSaveProject();SaveState status=draft?draftSave:librarySave;
        long epoch=saveEpoch,request=++status.request;String id=project.id;
        status.phase=SaveState.SAVING;status.failure="";
        try{
            ComposerProject snapshot=ComposerProject.read(project.json());
            worker.execute(()->{String failure="";try{store.write(snapshot,draft);}catch(Exception e){failure=displayError(e.getMessage()==null?e.toString():e.getMessage());}
                final String result=failure;final long completedAt=System.currentTimeMillis();
                main.post(()->completeSave(draft,id,epoch,request,completedAt,result));
            });
        }catch(Exception e){completeSave(draft,id,epoch,request,System.currentTimeMillis(),displayError(e.getMessage()==null?e.toString():e.getMessage()));}
    }
    private void completeSave(boolean draft,String id,long epoch,long request,long at,String failure){
        ensureSaveProject();SaveState status=draft?draftSave:librarySave;
        if(!project.id.equals(id)||saveEpoch!=epoch||status.request!=request)return;
        if(!failure.isEmpty()){status.phase=SaveState.FAILED;status.failure=failure;error=AppLanguage.text(context,draft?"초안 저장 실패: ":"저장 실패: ",draft?"Could not save draft: ":"Save failed: ")+failure;return;}
        status.phase=SaveState.SAVED;status.savedAt=at;status.failure="";
        if(!draft){libraryDirty=false;message=AppLanguage.text(context,"내 반주에 저장했습니다.","Saved to My Backings.");nonEditingRevision();}
    }
    void publish(int selected){if(binding!=null)return;final long generation=publishGeneration;if(ExitCoordinator.isExitRequested()||worker.isShutdown()||!ready)return;try{ComposerProject snapshot=ComposerProject.read(project.json());worker.execute(()->{if(generation!=publishGeneration)return;try{ComposerTransport.Plan plan=new ComposerTransport().compile(snapshot,selected);synchronized(this){if(generation!=publishGeneration)return;if(!NativeComposer.publish(plan.events,plan.beats,plan.bar,plan.bpm,plan.key,plan.scale))throw new IOException(AppLanguage.text(context,"재생 계획 큐가 가득 찼습니다.","Playback plan queue is full."));}}catch(Exception e){main.post(()->error=AppLanguage.text(context,"반주 준비 실패: ","Could not prepare backing: ")+displayError(e.getMessage()));}});}catch(Exception e){error=displayError(e.getMessage());}}
    void play(){if(binding!=null){binding.play.run();return;}if(ExitCoordinator.isExitRequested())return;if(!YouTubePlayer.beforeNativePlayback())return;if(!ready){error=AppLanguage.text(context,"음원을 준비하고 있습니다.","Preparing sounds.");return;}int request=++playbackRequest;publish(-1);worker.execute(()->main.post(()->{if(ExitCoordinator.isExitRequested()||request!=playbackRequest)return;AudioService.composerRequested=true;context.startForegroundService(new Intent(context,AudioService.class).putExtra("exitGeneration", ExitCoordinator.generation()).setAction(AudioService.PLAYBACK));NativeAudio.playLocal(false);NativeComposer.transport(1);}));}
    void pause(){if(binding!=null){binding.pause.run();return;}playbackRequest++;NativeComposer.transport(2);AudioService.composerRequested=false;if(!ExitCoordinator.isExitRequested()&&AudioService.playbackActive)context.startService(new Intent(context,AudioService.class).putExtra("exitGeneration", ExitCoordinator.generation()).setAction(AudioService.COMPOSER_STOP));}
    void stop(){if(binding!=null){binding.stop.run();return;}playbackRequest++;if(capturing)cancelPhrase();NativeComposer.transport(0);AudioService.composerRequested=false;if(!ExitCoordinator.isExitRequested()&&AudioService.playbackActive)context.startService(new Intent(context,AudioService.class).putExtra("exitGeneration", ExitCoordinator.generation()).setAction(AudioService.COMPOSER_STOP));}
    void voice(boolean on){if(binding!=null){error=AppLanguage.text(context,"세션 화면에서 목소리를 녹음하세요.","Record voice from the session screen.");return;}if(restoreLocked())return;if(on&&ExitCoordinator.isExitRequested())return;if(on&&!YouTubePlayer.beforeNativePlayback())return;voiceOn=on;applyVoice();nonEditingRevision();}
    void applyVoice(){VoiceInstrumentSettings s=project.voice;NativeComposer.voice(voiceOn,s.program,s.mode,s.octave,s.autoRange,s.snap,s.response,s.quality,s.rhythm,s.mixVoice,s.confidence,s.gate);}
    void history(boolean redo){if(sessionEditingLocked()||restoreLocked())return;if(capturing){error=AppLanguage.text(context,"기록을 마친 뒤 실행 취소하세요.","Finish recording before undoing.");return;}if((redo?store.redo:store.undo).isEmpty())return;comparison=null;try{project=store.history(project,redo);revision++;persist();publish(-1);if(voiceOn)applyVoice();}catch(Exception e){error=displayError(e.getMessage());}}
    void save(){queueSave(false);}
    void open(ComposerProject p){if(sessionLibraryBlocked()||restoreLocked())return;if(capturing){error=AppLanguage.text(context,"기록을 마친 뒤 프로젝트를 여세요.","Finish recording before opening a project.");return;}comparison=null;stop();voice(false);project=p;saveProjectId="";ensureSaveProject();selectedPart=0;store.undo.clear();store.redo.clear();persist();publish(-1);revision++;}
    void duplicate(){duplicate(project.name+AppLanguage.text(context," 복제"," Copy"));}
    void duplicate(String name){if(sessionLibraryBlocked())return;int before=revision;edit(p->{p.id=UUID.randomUUID().toString();p.name=name;},true);if(revision!=before)save();}
    boolean versionBusy,versionRestoreLock;
    private boolean restoreLocked(){if(!versionRestoreLock)return false;error=AppLanguage.text(context,"버전 복원을 마칠 때까지 잠시 기다려 주세요.","Please wait for the version restore to finish.");return true;}
    private <T> void versionTask(Callable<T> task,Consumer<T> success,Runnable completed) {
        if(sessionLibraryBlocked()){if(completed!=null)completed.run();return;}
        if(ExitCoordinator.isExitRequested()||worker.isShutdown())return;
        if(capturing||versionBusy){error=AppLanguage.text(context,"기록 또는 버전 작업을 마친 뒤 다시 시도하세요.","Finish recording or the version operation first.");return;}
        ComposerProject expected=project;int expectedRevision=revision;versionBusy=true;
        worker.execute(()->{
            T value=null;String failure="";
            try{value=task.call();}catch(Exception e){failure=displayError(e.getMessage()==null?e.toString():e.getMessage());}
            final T result=value;final String problem=failure;
            main.post(()->{
                versionBusy=false;
                if(ExitCoordinator.isExitRequested()||worker.isShutdown())return;
                if(project!=expected||revision!=expectedRevision||capturing){error=AppLanguage.text(context,"프로젝트가 변경되어 버전 작업을 적용하지 않았습니다.","The project changed. The version operation was not applied.");if(completed!=null)completed.run();return;}
                if(!problem.isEmpty())error=AppLanguage.text(context,"버전 작업 실패: ","Version operation failed: ")+problem;
                else success.accept(result);
                if(completed!=null)completed.run();
            });
        });
    }
    void listVersions(Consumer<List<ProjectVersions.Version>> result,Runnable completed){
        String id=project.id;versionTask(()->store.versions.list(id),result,completed);
    }
    void createVersion(String name,Runnable completed){
        if(capturing){error=AppLanguage.text(context,"기록을 마친 뒤 버전을 만드세요.","Finish recording before creating a version.");return;}
        try{ComposerProject snapshot=ComposerProject.read(project.json());
            versionTask(()->store.versions.create(snapshot,name),version->{message=AppLanguage.text(context,"버전을 저장했습니다: ","Version saved: ")+version.name;},completed);
        }catch(Exception e){error=displayError(e.getMessage());if(completed!=null)completed.run();}
    }
    void renameVersion(ProjectVersions.Version version,String name,Runnable completed){
        if(!project.id.equals(version.projectId)){error=AppLanguage.text(context,"다른 프로젝트의 버전입니다.","This version belongs to another project.");return;}
        versionTask(()->store.versions.rename(version,name),renamed->{message=AppLanguage.text(context,"버전 이름을 변경했습니다.","Version renamed.");},completed);
    }
    void previewVersion(ProjectVersions.Version version,Consumer<ComposerProject> result,Runnable completed){
        if(!project.id.equals(version.projectId)){error=AppLanguage.text(context,"다른 프로젝트의 버전입니다.","This version belongs to another project.");return;}
        versionTask(()->store.versions.readProject(version.projectId,version.id),result,completed);
    }
    private static final class PreparedRestore {
        final ComposerProject project;final ProjectVersions.Version backup;
        PreparedRestore(ComposerProject project,ProjectVersions.Version backup){this.project=project;this.backup=backup;}
    }
    void restoreVersion(ProjectVersions.Version version,boolean asCopy,Runnable completed){
        if(sessionLibraryBlocked()){if(completed!=null)completed.run();return;}
        if(capturing||versionBusy||!project.id.equals(version.projectId)){error=AppLanguage.text(context,"기록 또는 버전 작업 중이거나 다른 프로젝트의 버전입니다.","Recording or a version operation is active, or this version belongs to another project.");return;}
        if(ExitCoordinator.isExitRequested()||worker.isShutdown())return;
        try{
            ComposerProject expected=project,before=ComposerProject.read(project.json());int expectedRevision=revision;
            String beforeJson=before.json().toString();versionBusy=true;
            String backupName=AppLanguage.text(context,"복원 전 자동 백업","Automatic backup before restore");
            String copySuffix=AppLanguage.text(context," 복원 사본"," Restored copy");
            worker.execute(()->{
                PreparedRestore prepared=null;String failure="";
                try{
                    ComposerProject restored=store.versions.readProject(version.projectId,version.id);
                    if(asCopy){restored.id=UUID.randomUUID().toString();restored.name+=copySuffix;}restored.validate();
                    store.write(before,false);
                    // Staging is durable but invisible, and never evicts an existing named version.
                    prepared=new PreparedRestore(restored,store.versions.stage(before,backupName));
                }catch(Exception e){failure=displayError(e.getMessage()==null?e.toString():e.getMessage());}
                final PreparedRestore result=prepared;final String problem=failure;
                main.post(()->{
                    boolean valid=false;try{valid=result!=null&&project==expected&&revision==expectedRevision&&!capturing&&!ExitCoordinator.isExitRequested()&&project.json().toString().equals(beforeJson);}catch(Exception ignored){}
                    if(!problem.isEmpty()||!valid){
                        versionBusy=false;
                        if(result!=null)worker.execute(()->{try{store.versions.discardStaged(result.backup);}catch(Exception ignored){}});
                        error=problem.isEmpty()?AppLanguage.text(context,"프로젝트가 변경되어 버전 작업을 적용하지 않았습니다.","The project changed. The version operation was not applied.")
                                :AppLanguage.text(context,"버전 작업 실패: ","Version operation failed: ")+problem;
                        if(completed!=null&&!ExitCoordinator.isExitRequested())completed.run();return;
                    }
                    // All project mutations are blocked only for this final, short disk commit.
                    versionRestoreLock=true;
                    worker.execute(()->{
                        String commitFailure="";
                        try{store.versions.commitStaged(result.backup);}catch(Exception e){commitFailure=displayError(e.getMessage()==null?e.toString():e.getMessage());try{store.versions.discardStaged(result.backup);}catch(Exception ignored){}}
                        final String commitProblem=commitFailure;
                        main.post(()->{
                            versionRestoreLock=false;versionBusy=false;
                            if(!commitProblem.isEmpty())error=AppLanguage.text(context,"버전 작업 실패: ","Version operation failed: ")+commitProblem;
                            else try{
                                store.checkpoint(before);comparison=null;stop();voice(false);project=result.project;selectedPart=0;
                                revision++;persist();publish(-1);save();
                                message=asCopy?AppLanguage.text(context,"복원 사본을 열었습니다. 이전 상태는 원본 프로젝트의 자동 백업에 있습니다.","Opened a restored copy. The previous state is backed up in the original project's history.")
                                        :AppLanguage.text(context,"버전을 복원했습니다. 이전 상태는 자동 백업에 있습니다.","Version restored. The previous state is in the automatic backup.");
                            }catch(Exception e){error=displayError(e.getMessage());}
                            if(completed!=null&&!ExitCoordinator.isExitRequested())completed.run();
                        });
                    });
                });
            });
        }catch(Exception e){versionBusy=false;error=displayError(e.getMessage());if(completed!=null)completed.run();}
    }

    private TrackConfig microphoneTrack(ComposerProject.Part target){for(TrackConfig t:target.tracks)if(t.voice&&!t.drums()&&"microphone".equals(t.phraseSource))return t;return null;}
    private String targetLabel(ComposerProject.Part target,TrackConfig microphone){
        return AppLanguage.text(context,"파트 ","Part ")+target.label+" → "+(microphone==null
            ?AppLanguage.text(context,"새 목소리 트랙","New voice track")
            :microphone.name+AppLanguage.text(context," · 기존 마이크 트랙"," · Existing microphone track"));
    }
    String recordingTarget(){
        if(capturing)return AppLanguage.text(context,"기록 대상 고정: ","Recording target (fixed): ")+recordingTargetLabel;
        double[] latest=NativeComposer.state();int index=latest[1]>.5?(int)latest[2]:selectedPart;
        if(index<0||index>=project.parts.size())return AppLanguage.text(context,"기록 대상: 파트 확인 필요","Recording target: part unavailable");
        ComposerProject.Part target=project.parts.get(index);
        return AppLanguage.text(context,"기록 대상: ","Recording target: ")+targetLabel(target,microphoneTrack(target));
    }
    void recordPhrase(){if(binding!=null){error=AppLanguage.text(context,"세션 화면에서 목소리를 녹음하세요. 세션 편집 중 프레이즈 기록은 지원되지 않습니다.","Record voice from the session screen. Phrase capture is unavailable while editing a session.");return;}if(restoreLocked())return;if(ExitCoordinator.isExitRequested())return;if(!YouTubePlayer.beforeNativePlayback())return;if(!voiceOn||!AudioService.active){error=AppLanguage.text(context,"목소리 악기를 먼저 켜세요.","Turn on the voice instrument first.");return;}if(capturing){cancelPhrase();return;}if(!ready){error=AppLanguage.text(context,"음원을 준비하고 있습니다.","Preparing sounds.");return;}try{
        comparison=null;state=NativeComposer.state();
        boolean wasPlaying=playing();int actualPart=wasPlaying?(int)state[2]:selectedPart;
        if(actualPart<0||actualPart>=project.parts.size())throw new IOException(AppLanguage.text(context,"기록할 파트를 찾을 수 없습니다.","The recording part is unavailable."));
        ComposerProject.Part target=project.parts.get(actualPart);TrackConfig microphone=microphoneTrack(target);
        recordingTargetLabel=targetLabel(target,microphone);
        TrackConfig newTrack=null;
        if(microphone==null){
            newTrack=new TrackConfig(AppLanguage.text(context,"목소리 ","Voice ")+InstrumentCatalog.GM[project.voice.program],project.voice.program,false);newTrack.voice=true;newTrack.phraseSource="microphone";
            // addTrack clones into every part, so preflight its global and per-part limits.
            try{ComposerProject trial=ComposerProject.read(project.json());trial.addTrack(newTrack);trial.validate();}
            catch(IllegalArgumentException e){throw new IOException(AppLanguage.text(context,"프레이즈를 기록할 트랙 공간이 없습니다. 악기 트랙을 줄여주세요.","No track space remains for the phrase. Reduce the number of instrument tracks."));}
            microphone=newTrack;
        }
        recordingProject=project;recordingPartId=target.id;recordingTrackId=microphone.id;recordingNewTrack=newTrack;recordingBefore=project.json().toString();pendingPhrase=new VoicePhrase();pendingPhrase.chordMode=project.voice.mode==1;pendingNote=null;capturing=true;NativeComposer.drain();
        if(!wasPlaying){int request=++playbackRequest;publish(selectedPart);worker.execute(()->main.post(()->{if(ExitCoordinator.isExitRequested()||request!=playbackRequest||!capturing)return;AudioService.composerRequested=true;context.startForegroundService(new Intent(context,AudioService.class).putExtra("exitGeneration", ExitCoordinator.generation()).setAction(AudioService.PLAYBACK));NativeComposer.transport(3);NativeComposer.record(true);}));}
        else NativeComposer.record(true);revision++;
    }catch(Exception e){error=displayError(e.getMessage());}}
    void cancelPhrase(){playbackRequest++;NativeComposer.record(false);NativeComposer.drain();pendingPhrase=null;pendingNote=null;recordingProject=null;recordingNewTrack=null;capturing=false;revision++;message=AppLanguage.text(context,"재기록을 취소했습니다. 이전 프레이즈를 유지합니다.","Recording cancelled. The previous phrase was kept.");}
    void poll(){if(!ready)return;state=NativeComposer.state();if(!capturing)return;double[] data=NativeComposer.drain();for(int i=0;i+7<data.length;i+=8){double time=data[i];int kind=(int)data[i+1];if(kind==1){closeNote(time);pendingNote=new VoicePhrase.Note();pendingNote.beat=time;pendingNote.midi=(int)data[i+2];pendingNote.velocity=(int)data[i+3];pendingNote.bend=(float)data[i+4];pendingNote.expression=(float)data[i+5];pendingNote.degree=(int)data[i+6];pendingNote.quality=(int)data[i+7];pendingNote.automatic=pendingPhrase.chordMode&&project.voice.quality<0;pendingNote.offset=pendingPhrase.chordMode&&project.voice.program/8==3?.012*MusicTheory.quarterBpm(project.bpm,project.meter)/60:0;}
            else if(kind==0)closeNote(time);else if(kind==2&&pendingNote!=null)pendingNote.curve.add(new double[]{time-pendingNote.beat,data[i+4],data[i+5]});}
        if(state[8]==3){closeNote(Math.max(0,state[4]-state[3]));finishPhrase();}}
    private void closeNote(double time){if(pendingNote!=null){pendingNote.length=Math.max(.01,time-pendingNote.beat);pendingPhrase.notes.add(pendingNote);pendingNote=null;}}
    private void finishPhrase(){
        NativeComposer.record(false);capturing=false;
        try{
            if(state[9]>.5)throw new IOException(AppLanguage.text(context,"기록 버퍼가 가득 차 이전 프레이즈를 유지합니다.","The recording buffer is full. The previous phrase was kept."));
            if(pendingPhrase.notes.isEmpty()){message=AppLanguage.text(context,"기록한 음이 없습니다. 이전 프레이즈를 유지합니다.","No notes were recorded. The previous phrase was kept.");return;}
            if(project!=recordingProject||!project.json().toString().equals(recordingBefore))throw new IOException(AppLanguage.text(context,"프로젝트가 변경되어 기록을 적용하지 않았습니다.","The project changed. The recording was not applied."));
            // Commit to a copy so a stale target or validation failure cannot alter old phrases.
            ComposerProject completed=ComposerProject.read(new JSONObject(recordingBefore));
            ComposerProject.Part target=null;for(ComposerProject.Part p:completed.parts)if(p.id.equals(recordingPartId))target=p;
            if(target==null)throw new IOException("Part not found.");
            if(recordingNewTrack!=null)completed.addTrack(recordingNewTrack);
            TrackConfig voice=null;for(TrackConfig t:target.tracks)if(t.id.equals(recordingTrackId))voice=t;
            if(voice==null||!voice.voice||voice.drums()||!"microphone".equals(voice.phraseSource))throw new IOException("Recording track is unavailable.");
            voice.program=completed.voice.program;voice.muted=false;pendingPhrase.trackId=voice.id;pendingPhrase.quantize(completed.voice.quantize);
            String id=voice.id;List<String> previous=phraseCopies(target,id);
            target.phrases.removeIf(p->p.trackId.equals(id));target.phrases.add(pendingPhrase);completed.validate();
            store.checkpoint(ComposerProject.read(new JSONObject(recordingBefore)));project=completed;persist();publish(-1);
            comparison=new PhraseComparison(completed,target.id,id,previous,phraseCopies(target,id),revision+1);
            message=AppLanguage.text(context,"프레이즈를 기록했습니다. 실행 취소로 이전 연주를 복구할 수 있습니다.","Phrase recorded. Undo to restore the previous performance.");
        }catch(Exception e){error=displayError(e.getMessage());}finally{pendingPhrase=null;pendingNote=null;recordingProject=null;recordingNewTrack=null;revision++;}
    }
}
