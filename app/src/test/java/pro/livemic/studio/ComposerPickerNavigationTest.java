package pro.livemic.studio;

import android.app.Dialog;
import android.view.*;
import android.widget.*;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,instrumentedPackages={"pro.livemic.studio"},shadows={StudioScreenshotTest.Audio.class,StudioScreenshotTest.Composer.class,StudioScreenshotTest.Harmony.class})
@LooperMode(LooperMode.Mode.PAUSED)
public class ComposerPickerNavigationTest {
    MainActivity a; ComposerSession s; ComposerScreen screen;
    @Before public void setup() throws Exception {
        AppLanguage.set(RuntimeEnvironment.getApplication(),true);
        a=Robolectric.buildActivity(MainActivity.class).setup().get();
        a.handler.removeCallbacks(a.tick);a.composer.worker.submit(()->{}).get();ShadowLooper.idleMainLooper();a.handler.removeCallbacks(a.tick);
        a.chooseTab(7);s=a.composer;screen=a.composerScreen;
        a.getPreferences(0).edit().remove("gmFavorites").remove("instrumentFavorites").remove("instrumentFavoritesMigrated").commit();
    }
    @After public void cleanup(){a.handler.removeCallbacks(a.tick);a.io.shutdown();AppLanguage.set(RuntimeEnvironment.getApplication(),false);}
    View root(){
        List<Dialog> shown=ShadowDialog.getShownDialogs();
        for(int i=shown.size()-1;i>=0;i--)if(shown.get(i).isShowing())return shown.get(i).getWindow().getDecorView();
        throw new AssertionError("No showing dialog");
    }
    TextView clickableText(View v,String label){
        if(v instanceof TextView&&v.isClickable()&&((TextView)v).getText().toString().contains(label))return (TextView)v;
        if(v instanceof ViewGroup){ViewGroup group=(ViewGroup)v;for(int i=0;i<group.getChildCount();i++){TextView found=clickableText(group.getChildAt(i),label);if(found!=null)return found;}}
        return null;
    }
    void clickText(View root,String label){TextView view=clickableText(root,label);assertNotNull(label,view);assertTrue(view.performClick());}
    Button button(View root,String label){Button b=StudioScreenshotTest.findButtonContaining(root,label);assertNotNull(label,b);return b;}
    View description(View v,String prefix){
        if(v.getContentDescription()!=null&&v.getContentDescription().toString().startsWith(prefix))return v;
        if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){View found=description(g.getChildAt(i),prefix);if(found!=null)return found;}}return null;
    }
    EditText search(View v){if(v instanceof EditText)return (EditText)v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){EditText f=search(g.getChildAt(i));if(f!=null)return f;}}return null;}
    void category(String name){button(root(),"Category:").performClick();button(root(),name).performClick();}
    @Test public void featuredContainsSpecifiedMelodic24AndAvailableDefaultDrum(){
        assertArrayEquals(new int[]{24,25,26,27,28,29,30,31,32,33,34,35,36,37,38,39,0,4,16,40,42,65,73,89},InstrumentCatalog.FEATURED);
        screen.instrument(-1,false);assertNotNull(button(root(),"Featured 24 + Drums"));assertNotNull(button(root(),"0 · Standard"));
        assertNull(StudioScreenshotTest.findButtonContaining(root(),"8 · Room"));
        View star=description(root(),"Add favorite: 0 · Standard");assertNotNull(star);assertTrue(star.getLayoutParams().width>=a.dp(48));
    }
    @Test public void drumFavoritePersistsAndDoesNotSelectOrDismissAndLegacyGmZeroStaysDistinct(){
        a.getPreferences(0).edit().putStringSet("gmFavorites",new HashSet<>(Arrays.asList("0","24"))).commit();
        screen.instrument(-1,false);Dialog picker=ShadowDialog.getLatestDialog();int tracks=s.part().tracks.size();
        description(root(),"Add favorite: 0 · Standard").performClick();
        assertTrue(picker.isShowing());assertEquals(tracks,s.part().tracks.size());
        assertEquals(new HashSet<>(Arrays.asList("0:0","0:24","128:0")),InstrumentFavorites.read(a.getPreferences(0)));
        picker.dismiss();screen.instrument(-1,false);category("Favorites");assertNotNull(button(root(),"0 · Standard"));
        View remove=description(root(),"Remove favorite: 0 · Standard");assertNotNull(remove);remove.performClick();
        assertTrue(InstrumentFavorites.read(a.getPreferences(0)).contains("0:0"));assertFalse(InstrumentFavorites.read(a.getPreferences(0)).contains("128:0"));
        assertNull(StudioScreenshotTest.findButtonContaining(root(),"0 · Standard"));
    }
    @Test public void drumSearchAndSelectionUseActualKitAndFavoritesFilterStaysMeaningful(){
        screen.instrument(-1,false);category("Drum kits");search(root()).setText("room");
        assertNull(StudioScreenshotTest.findButtonContaining(root(),"0 · Standard"));button(root(),"8 · Room").performClick();
        TrackConfig drum=s.part().tracks.get(s.part().tracks.size()-1);assertEquals(8,drum.program);assertTrue(drum.drums());
        screen.instrument(-1,false);category("Favorites");search(root()).setText("piano");assertNull(description(root(),"Add favorite:"));
    }
    @Test public void voiceOmitsDrumsAndCategoryMappingStillFindsFavorites(){
        InstrumentFavorites.save(a.getPreferences(0),new HashSet<>(Arrays.asList("0:0","128:0")));
        screen.instrument(-1,true);button(root(),"Category:").performClick();assertNull(StudioScreenshotTest.findButtonContaining(root(),"Drum kits"));
        button(root(),"Favorites").performClick();assertNull(StudioScreenshotTest.findButtonContaining(root(),"0 · Standard"));assertNotNull(description(root(),"Remove favorite:"));
    }
    @Test public void composerLinksOpenVoiceAndSavedProjectsWithProjectFilter() {
        clickText(a.page,"Play with voice");assertEquals(8,a.tab);
        a.chooseTab(7);a.libraryFilter=0;clickText(a.page,"Saved projects");assertEquals(4,a.tab);assertEquals(1,a.libraryFilter);
    }
    @Test public void stalePartChooserCannotJumpInNewProject() throws Exception {
        s.project.duplicate(s.part());screen.refresh();screen.choosePart();Dialog chooser=ShadowDialog.getLatestDialog();
        s.open(new ComposerProject());button(chooser.getWindow().getDecorView(),"B ·").performClick();assertEquals(0,s.selectedPart);
    }
    @Test public void eightPartsHaveBoundedArrowsAndDirectJumpLocksDuringCapture() throws Exception {
        while(s.project.parts.size()<8)s.project.duplicate(s.part());screen.refresh();
        assertFalse(button(a.page,"‹").isEnabled());assertTrue(button(a.page,"›").isEnabled());
        screen.solo=0;StudioScreenshotTest.Composer.trackCalls.clear();
        for(int i=1;i<8;i++){button(a.page,"›").performClick();assertEquals(i,s.selectedPart);}
        assertFalse(button(a.page,"›").isEnabled());assertEquals(-1,screen.solo);assertTrue(StudioScreenshotTest.Composer.trackCalls.isEmpty());
        button(a.page,"‹").performClick();assertEquals(6,s.selectedPart);
        button(a.page,"All parts").performClick();Dialog chooser=ShadowDialog.getLatestDialog();
        s.capturing=true;button(chooser.getWindow().getDecorView(),"A ·").performClick();assertEquals(6,s.selectedPart);
        screen.selectPart(0);assertEquals(6,s.selectedPart);screen.refresh();
        assertFalse(button(a.page,"‹").isEnabled());assertFalse(button(a.page,"›").isEnabled());assertFalse(button(a.page,"All parts").isEnabled());
        s.capturing=false;screen.refresh();
        button(a.page,"All parts").performClick();button(root(),"A ·").performClick();assertEquals(0,s.selectedPart);
    }
}
