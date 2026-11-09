package pro.livemic.studio;

import android.app.Dialog;
import android.graphics.*;
import android.text.Layout;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.file.Files;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.*;
import static org.junit.Assert.*;

/** Actual Android Views drawn with native Skia. WAVs, backing names, retained-voice seconds and
 * project history and the non-decodable MP4 UI marker are synthetic fixtures. This checks layout/interactions, not device audio quality,
 * hardware recording, measured Galaxy system insets or the acoustic correctness of a rendered mix. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,instrumentedPackages={"pro.livemic.studio"},shadows={TakeAudioUiTest.Audio.class,StudioScreenshotTest.Composer.class,StudioScreenshotTest.Harmony.class,YouTubePlayerTest.OfflineWeb.class})
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class WorkflowScreenshotTest {
    File directory;
    @Before public void reset()throws Exception {
        new StudioScreenshotTest().resetComposer();ExitCoordinator.resetForLaunch();
        RecordingManager.active=false;TakeAudioUiTest.Audio.history=false;TakeAudioUiTest.Audio.seconds=0;
        directory=new File(System.getProperty("user.dir"),"build/studio-screenshots/v311");
        assertTrue(directory.isDirectory()||directory.mkdirs());
    }
    @After public void restore(){
        Dialog dialog=ShadowDialog.getLatestDialog();if(dialog!=null)dialog.dismiss();
        AudioService.active=AudioService.playbackActive=false;RecordingManager.active=false;
        RuntimeEnvironment.setFontScale(1);AppLanguage.set(RuntimeEnvironment.getApplication(),false);
        TakeAudioUiTest.Audio.history=false;TakeAudioUiTest.Audio.seconds=0;
    }
    void settle(MainActivity a)throws Exception {
        for(int i=0;i<4;i++){a.composer.worker.submit(()->{}).get();ShadowLooper.idleMainLooper();a.handler.removeCallbacks(a.tick);}
    }
    static Rect rect(View root,View child){Rect r=new Rect(0,0,child.getWidth(),child.getHeight());if(root!=child)((ViewGroup)root).offsetDescendantRectToMyCoords(child,r);return r;}
    static ScrollView scroll(View view){
        if(view.getVisibility()!=View.VISIBLE)return null;
        if(view instanceof ScrollView&&view.getHeight()>0)return (ScrollView)view;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){ScrollView found=scroll(((ViewGroup)view).getChildAt(i));if(found!=null)return found;}return null;
    }
    static ScrollView effectsScroll(View decor){
        View body=decor.findViewWithTag("take-effects-scroll");assertNotNull("Explicit effects body",body);
        assertTrue("Effects body is a visible measured scroll",body instanceof ScrollView&&body.getVisibility()==View.VISIBLE&&body.getHeight()>0);
        return (ScrollView)body;
    }
    static void textFits(View view){
        if(view.getVisibility()!=View.VISIBLE)return;
        if(view instanceof TextView){
            TextView t=(TextView)view;Layout layout=t.getLayout();
            if(t.getText().length()>0){
                String context=t.getClass().getSimpleName()+" text="+t.getText()+" size="+t.getWidth()+"x"+t.getHeight();
                assertNotNull(context,layout);int width=t.getWidth()-t.getCompoundPaddingLeft()-t.getCompoundPaddingRight();
                assertTrue(context+" full text height",layout.getHeight()<=t.getHeight()-t.getCompoundPaddingTop()-t.getCompoundPaddingBottom());
                for(int line=0;line<layout.getLineCount();line++){
                    assertEquals(context+" no ellipsis",0,layout.getEllipsisCount(line));
                    assertTrue(context+" visible line width="+layout.getLineMax(line)+" available="+width+" line="+line,layout.getLineMax(line)<=width+1);
                }
            }
        }
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)textFits(((ViewGroup)view).getChildAt(i));
    }
    static void touch(MainActivity a,View view){assertNotNull(view);assertTrue("48dp target: "+((TextView)view).getText()+" "+view.getWidth()+"x"+view.getHeight(),view.getWidth()>=a.dp(48)&&view.getHeight()>=a.dp(48));}
    static Button button(View root,String label){Button button=StudioScreenshotTest.findButtonContaining(root,label);assertNotNull(label,button);return button;}
    /** Uses the dialog's configured width and natural height capped to this window's safe area.
     * The composite explicitly draws a dialog over its host, because activity.root.draw omits windows. */
    View sheet(MainActivity a,Dialog dialog,int width,int height,String name)throws Exception {
        assertNotNull(dialog);assertTrue(dialog.isShowing());TabletLayoutTest.measure(a,width,height);
        View decor=dialog.getWindow().getDecorView();int requested=dialog.getWindow().getAttributes().width;
        int sheetWidth=requested>0?Math.min(requested,width):Math.min(width,a.dp(560));
        int available=height-a.root.getPaddingTop()-a.root.getPaddingBottom();
        for(int pass=0;pass<2;pass++){
            StudioScreenshotTest.redraw(decor);
            decor.measure(View.MeasureSpec.makeMeasureSpec(sheetWidth,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(available,View.MeasureSpec.AT_MOST));
            decor.layout(0,0,sheetWidth,decor.getMeasuredHeight());
        }
        Bitmap bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);
        a.root.draw(canvas);canvas.drawColor(0xd1000000);canvas.save();
        boolean bottom=(dialog.getWindow().getAttributes().gravity&Gravity.VERTICAL_GRAVITY_MASK)==Gravity.BOTTOM;
        canvas.translate((width-sheetWidth)/2f,bottom?height-a.root.getPaddingBottom()-decor.getHeight():a.root.getPaddingTop()+(available-decor.getHeight())/2f);
        decor.draw(canvas);canvas.restore();
        File output=new File(directory,name+".png");try(FileOutputStream file=new FileOutputStream(output)){bitmap.compress(Bitmap.CompressFormat.PNG,100,file);}finally{bitmap.recycle();}
        assertTrue("Native rendered image exists",output.length()>1000);return decor;
    }
    static void reveal(ScrollView scroll,View target){
        Rect r=new Rect();target.getDrawingRect(r);ViewGroup content=(ViewGroup)scroll.getChildAt(0);content.offsetDescendantRectToMyCoords(target,r);
        scroll.requestChildRectangleOnScreen(content,r,true);
        // offsetDescendantRectToMyCoords stops before applying the root ScrollView's own
        // scroll offset. Map from its content into the visible viewport explicitly.
        Rect visibleTarget=rect(content,target);
        visibleTarget.offset(content.getLeft()-scroll.getScrollX(),content.getTop()-scroll.getScrollY());
        Rect viewport=new Rect(scroll.getPaddingLeft(),scroll.getPaddingTop(),scroll.getWidth()-scroll.getPaddingRight(),scroll.getHeight()-scroll.getPaddingBottom());
        assertTrue("Control fully reachable by scroll: "+target+" target="+visibleTarget+" viewport="+viewport+" scrollY="+scroll.getScrollY(),viewport.contains(visibleTarget));
    }
    @Test public void effectsEditorAtBothLanguagesAndFontScales()throws Exception {matrix(0);}
    @Test public void recentVoiceAtBothLanguagesAndFontScales()throws Exception {matrix(1);}
    @Test public void projectVersionsAtBothLanguagesAndFontScales()throws Exception {matrix(2);}
    void matrix(int workflow)throws Exception {
        for(int[] size:new int[][]{{1480,924},{960,600}})for(boolean english:new boolean[]{false,true})for(float scale:new float[]{1,1.3f}) {
            MainActivity a=TabletLayoutTest.open(size[0],size[1],scale,english);
            String prefix=(size[0]==1480?"s9ultra":"short-window")+"-"+size[0]+"x"+size[1]+"-"+(english?"en":"ko")+"-"+(scale==1?"100":"130")+"-SYNTHETIC";
            try { if(workflow==0)editor(a,size[0],size[1],prefix,english);else if(workflow==1)recovery(a,size[0],size[1],prefix,english);else versions(a,size[0],size[1],prefix,english); }
            finally {Dialog dialog=ShadowDialog.getLatestDialog();if(dialog!=null)dialog.dismiss();a.handler.removeCallbacksAndMessages(null);TabletLayoutTest.close(a);}
        }
    }
    void editor(MainActivity a,int w,int h,String prefix,boolean en)throws Exception {
        String id="workflow_editor_fixture";TakeSourcesTest.wav(a.store.file(id,".wav"),false,2);TakeSourcesTest.wav(a.store.file(id,".raw.wav"),true,1);TakeSourcesTest.wav(a.store.file(id,".fixed.wav"),true,2);TakeSourcesTest.wav(a.store.file(id,".vocal.wav"),true,2);
        // A nonempty marker enables the timing-aware video UI; no video is decoded or exported.
        try(FileOutputStream marker=new FileOutputStream(a.store.file(id,".mp4"))){marker.write(new byte[]{0,0,0,8,102,116,121,112});}
        JSONObject source=new JSONObject().put("video",true).put("videoAudioOffsetUs",1000).put("id",id).put("name",en?"Synthetic local rehearsal":"합성 로컬 연습").put("cleanSources",true).put("sourceEditable",true).put("sourceFixed",true).put("sourceVocalRetained",true).put("vocalStemEditable",true);
        byte[] original=Files.readAllBytes(a.store.file(id,".wav").toPath()),originalVideo=Files.readAllBytes(a.store.file(id,".mp4").toPath());float[] live=a.values.clone();
        a.chooseTab(4);a.takeEffectsEditor=new TakeEffectsEditor(a,source);a.takeEffectsEditor.show();Dialog dialog=ShadowDialog.getLatestDialog();
        editorState(a,dialog,w,h,prefix+"-effects",en,live);
        View decor=dialog.getWindow().getDecorView();ScrollView body=effectsScroll(decor);
        Button mode=button(decor,en?"Effect source:":"편집 원음:");reveal(body,mode);touch(a,mode);
        assertTrue("Initial mode rebuilds microphone effects",mode.getText().toString().contains(en?"rebuild effects":"효과 재계산"));mode.performClick();
        Dialog choices=ShadowDialog.getLatestDialog();decor=sheet(a,choices,w,h,prefix+"-effect-source-options");textFits(decor);
        Button add=button(decor,en?"Recorded voice · add effects":"녹음된 보컬 · 추가 효과");touch(a,add);add.performClick();
        assertFalse("Source switch dismisses previous editor",dialog.isShowing());dialog=ShadowDialog.getLatestDialog();assertNotSame(choices,dialog);
        decor=sheet(a,dialog,w,h,prefix+"-additional-effects-top");
        assertNotNull("Additional mode explains existing effects cannot be removed",TabletLayoutTest.text(decor,en?"Existing effects and harmony cannot be removed":"기존 효과와 화음을 제거"));
        assertTrue("Mode reflects recorded stereo voice",button(decor,en?"Effect source:":"편집 원음:").getText().toString().contains(en?"Recorded voice · add effects":"녹음된 보컬 · 추가 효과"));
        editorState(a,dialog,w,h,prefix+"-additional-effects",en,live);
        a.onBackPressed();ShadowLooper.idleMainLooper();assertFalse(dialog.isShowing());
        assertArrayEquals("Cancel retains original WAV",original,Files.readAllBytes(a.store.file(id,".wav").toPath()));assertFalse(AudioService.active);assertArrayEquals("Cancel retains marker video",originalVideo,Files.readAllBytes(a.store.file(id,".mp4").toPath()));a.store.delete(id);
    }
    void editorState(MainActivity a,Dialog dialog,int w,int h,String prefix,boolean en,float[] live)throws Exception {
        View decor=sheet(a,dialog,w,h,prefix+"-top");textFits(decor);ScrollView body=effectsScroll(decor);
        Button close=button(decor,en?"keep original":"원본 유지"),save=button(decor,en?"Save WAV copy":"WAV 복사본 저장"),mp4=button(decor,en?"Save MP4 + WAV":"MP4 + WAV 저장");touch(a,close);touch(a,save);touch(a,mp4);
        assertFalse("WAV save stays outside scrolling body",TabletLayoutTest.isDescendant(body,save));assertFalse("MP4 save stays outside scrolling body",TabletLayoutTest.isDescendant(body,mp4));
        Rect fixedClose=rect(decor,close),fixedSave=rect(decor,save),fixedMp4=rect(decor,mp4);
        assertTrue("Close within dialog",rect(decor,decor).contains(fixedClose));assertTrue("Save within dialog",rect(decor,decor).contains(fixedSave));assertTrue("MP4 save within dialog",rect(decor,decor).contains(fixedMp4));
        assertTrue("Effect controls overflow into a real scroll area",body.canScrollVertically(1));body.scrollTo(0,100000);assertTrue(body.getScrollY()>0);
        sheet(a,dialog,w,h,prefix+"-bottom");assertEquals("Close stays fixed after scroll",fixedClose,rect(decor,close));assertEquals("WAV save stays fixed after scroll",fixedSave,rect(decor,save));assertEquals("MP4 save stays fixed after scroll",fixedMp4,rect(decor,mp4));
        Switch compressor=TakeAudioUiTest.toggle(decor,en?"Compressor":"컴프레서");assertNotNull(compressor);reveal(body,compressor);touch(a,compressor);compressor.performClick();
        assertArrayEquals("Local edit must not change live controls",live,a.values,0);
    }
    void recovery(MainActivity a,int w,int h,String prefix,boolean en)throws Exception {
        TakeAudioUiTest.Audio.history=true;TakeAudioUiTest.Audio.seconds=7;AudioService.active=true;a.chooseTab(4);ShadowLooper.idleMainLooper();a.handler.removeCallbacks(a.tick);TabletLayoutTest.measure(a,w,h);
        Button recover=button(a.page,en?"Recover recent voice":"직전 목소리 복구");assertTrue(recover.isEnabled());touch(a,recover);reveal(a.pageScroll,recover);
        Switch toggle=TakeAudioUiTest.toggle(a.page,en?"Keep the last 10 seconds":"직전 마이크 소리 10초 보관");assertNotNull(toggle);assertTrue(toggle.isChecked());touch(a,toggle);
        StudioScreenshotTest.capture(a,w,h,new File(directory,prefix+"-recent-voice.png"),false);
        recover.performClick();Dialog dialog=ShadowDialog.getLatestDialog();View decor=sheet(a,dialog,w,h,prefix+"-recovery-options");textFits(decor);
        touch(a,button(decor,en?"Recover dry voice":"원음으로 복구"));touch(a,button(decor,en?"Recover with current":"현재 보컬 효과"));
        button(decor,en?"Close":"닫기").performClick();assertFalse(dialog.isShowing());TakeAudioUiTest.Audio.history=false;TakeAudioUiTest.Audio.seconds=0;AudioService.active=false;
    }
    void versions(MainActivity a,int w,int h,String prefix,boolean en)throws Exception {
        a.chooseTab(7);ComposerSession session=a.composer;session.project=new ComposerProject();session.selectedPart=0;
        session.project.name=en?"Synthetic backing project":"합성 반주 프로젝트";
        ProjectVersions.Version version=null;
        for(int i=0;i<20;i++)version=session.store.versions.create(session.project,(en?"Before chorus ":"후렴 변경 전 ")+i);
        session.project.bpm=141;a.showTab();a.composerScreen.versionHistory();settle(a);Dialog list=ShadowDialog.getLatestDialog();
        View decor=sheet(a,list,w,h,prefix+"-version-history");textFits(decor);ScrollView body=scroll(decor);assertNotNull(body);
        assertTrue("Version history overflow is scrollable",body.canScrollVertically(1));body.scrollTo(0,100000);assertTrue(body.getScrollY()>0);sheet(a,list,w,h,prefix+"-version-history-bottom");
        button(decor,en?"Close":"닫기").performClick();a.composerScreen.versionOptions(version);settle(a);Dialog preview=ShadowDialog.getLatestDialog();
        decor=sheet(a,preview,w,h,prefix+"-version-preview");textFits(decor);Button restore=button(decor,en?"Restore this project":"이 프로젝트를 복원");touch(a,restore);restore.performClick();
        Dialog confirm=ShadowDialog.getLatestDialog();decor=sheet(a,confirm,w,h,prefix+"-version-restore-confirm");textFits(decor);
        Button cancel=button(decor,en?"Cancel":"취소"),apply=StudioScreenshotTest.findButton(decor,en?"Restore":"복원");assertNotNull(apply);touch(a,cancel);touch(a,apply);
        Rect cancelPosition=rect(decor,cancel);body=scroll(decor);assertNotNull(body);body.scrollTo(0,100000);assertEquals("Cancel fixed outside body",cancelPosition,rect(decor,cancel));
        assertTrue("Cancel fully in dialog",rect(decor,decor).contains(cancelPosition));assertTrue("Restore fully in dialog",rect(decor,decor).contains(rect(decor,apply)));
        cancel.performClick();assertFalse(confirm.isShowing());assertEquals("Cancel preserves edits",141,session.project.bpm);assertEquals(20,session.store.versions.list(session.project.id).size());
    }
}
