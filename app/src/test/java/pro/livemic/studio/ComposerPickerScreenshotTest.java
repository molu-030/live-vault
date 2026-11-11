package pro.livemic.studio;

import android.app.Dialog;
import android.graphics.*;
import android.view.*;
import java.io.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,instrumentedPackages={"pro.livemic.studio"},shadows={StudioScreenshotTest.Audio.class,StudioScreenshotTest.Composer.class,StudioScreenshotTest.Harmony.class})
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class ComposerPickerScreenshotTest {
    @Test public void pickerAndEightPartNavigationAtLargeTextInBothLanguages() throws Exception {
        File out=new File(System.getProperty("user.dir"),"build/studio-screenshots");out.mkdirs();
        try {
            for(boolean english:new boolean[]{false,true})for(int width:new int[]{360,412}) {
                int height=width==360?800:893;
                RuntimeEnvironment.setQualifiers("w"+width+"dp-h"+height+"dp-mdpi");RuntimeEnvironment.setFontScale(1.3f);
                AppLanguage.set(RuntimeEnvironment.getApplication(),english);
                MainActivity a=Robolectric.buildActivity(MainActivity.class).setup().get();
                a.handler.removeCallbacks(a.tick);a.composer.worker.submit(()->{}).get();ShadowLooper.idleMainLooper();a.handler.removeCallbacks(a.tick);
                a.composer.project=new ComposerProject();a.composer.selectedPart=0;
                while(a.composer.project.parts.size()<8)a.composer.project.duplicate(a.composer.part());
                while(a.composer.part().tracks.size()<8)a.composer.project.addTrack(new TrackConfig("Warm pad",89,false));
                a.chooseTab(7);a.composerScreen.selectPart(3);
                String prefix="v388-picker-"+(english?"en":"ko")+"-"+width+"-large";
                StudioScreenshotTest.capture(a,width,height,new File(out,prefix+"-parts.png"),false);
                ComposerRedesignScreenshotTest.scroll(a,ComposerRedesignScreenshotTest.text(a.page,english?"Tracks":"악기 트랙"));
                StudioScreenshotTest.capture(a,width,height,new File(out,prefix+"-eight-tracks.png"),false);
                a.composerScreen.instrument(0,false);Dialog picker=ShadowDialog.getLatestDialog();
                captureDialog(a,picker,width,height,new File(out,prefix+"-featured.png"));picker.dismiss();
                a.composerScreen.choosePart();Dialog parts=ShadowDialog.getLatestDialog();
                captureDialog(a,parts,width,height,new File(out,prefix+"-all-parts.png"));parts.dismiss();
                a.io.shutdown();
            }
        } finally {RuntimeEnvironment.setFontScale(1f);AppLanguage.set(RuntimeEnvironment.getApplication(),false);}
    }
    static void captureDialog(MainActivity a,Dialog dialog,int width,int height,File output) throws Exception {
        View view=dialog.getWindow().getDecorView();int panelWidth=width-24;
        view.measure(View.MeasureSpec.makeMeasureSpec(panelWidth,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height-48,View.MeasureSpec.AT_MOST));
        int panelHeight=view.getMeasuredHeight();view.layout(0,0,panelWidth,panelHeight);
        Bitmap bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);
        a.root.draw(canvas);canvas.drawColor(0x99000000);canvas.save();canvas.translate(12,(height-panelHeight)/2f);view.draw(canvas);canvas.restore();
        try(FileOutputStream stream=new FileOutputStream(output)){bitmap.compress(Bitmap.CompressFormat.PNG,100,stream);}bitmap.recycle();
    }
}
