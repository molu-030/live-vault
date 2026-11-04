package pro.livemic.studio;

import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.view.TextureView;
import org.robolectric.android.controller.ActivityController;
import android.view.View;
import android.widget.Button;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowLooper;
import static org.junit.Assert.*;

/** Page visits, modal priority and both platform Back entry points. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=28, instrumentedPackages={"pro.livemic.studio"}, shadows={StudioScreenshotTest.Audio.class, StudioScreenshotTest.Composer.class, StudioScreenshotTest.Harmony.class, YouTubePlayerTest.OfflineWeb.class})
@LooperMode(LooperMode.Mode.PAUSED)
public class BackNavigationTest {
    MainActivity a;
    @Before public void setup() throws Exception {
        RuntimeEnvironment.getApplication().getSharedPreferences("MainActivity",0).edit().clear().commit();
        AppLanguage.set(RuntimeEnvironment.getApplication(),true);
        AudioService.active=false;AudioService.playbackActive=false;AudioService.youtubeSelected=false;AudioService.composerRequested=false;
        RecordingManager.active=false;RecordingManager.paused=false;
        StudioScreenshotTest.Audio.supplied=null;
        StudioScreenshotTest.Composer.current=new double[]{0,0,0,0,32,-1,-1,0,0,0};
        StudioScreenshotTest.Harmony.current=new double[]{0,0,0,0,-1,-1,0,0,-1,-1,-1,0,0,-1};
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            mirrorManifestBackOptIn();
            org.robolectric.util.ReflectionHelpers.callStaticMethod(org.robolectric.shadows.ShadowWindowOnBackInvokedDispatcher.class,
                    "setEnablePredictiveBack", org.robolectric.util.ReflectionHelpers.ClassParameter.from(boolean.class, true));
        }
        a=Robolectric.buildActivity(MainActivity.class).setup().get();a.handler.removeCallbacks(a.tick);
        a.composer.worker.submit(()->{}).get();ShadowLooper.idleMainLooper();a.handler.removeCallbacks(a.tick);
        Shadows.shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(android.Manifest.permission.RECORD_AUDIO);
        a.chooseTab(0);
    }
    @After public void cleanup() {
        a.handler.removeCallbacks(a.tick);a.youtube.destroy();a.io.shutdown();
        AudioService.active=false;AudioService.playbackActive=false;AudioService.youtubeSelected=false;AudioService.composerRequested=false;
        RecordingManager.active=false;RecordingManager.paused=false;StudioScreenshotTest.Audio.supplied=null;
        AppLanguage.set(RuntimeEnvironment.getApplication(),false);
    }
    @Test public void visitsReturnThroughPreviousPagesAndRootBackConfirmsExit() {
        for (int page : new int[]{1, 7, 8, 9, 4, 6}) a.chooseTab(page);
        for (int page : new int[]{4, 9, 8, 7, 1, 0}) {
            a.onBackPressed(); assertEquals(page, a.tab);
        }
        assertTrue(a.navigation.history.isEmpty());
        a.onBackPressed();
        assertFalse(a.isFinishing());
        assertTrue(ShadowDialog.getLatestDialog().isShowing());
        assertFalse(ExitCoordinator.isExitRequested());
        a.onBackPressed();
        assertFalse(ShadowDialog.getLatestDialog().isShowing());
        assertTrue(a.navigation.history.isEmpty());
    }

    @Test public void redrawsAndSelectingCurrentPageDoNotCreateHistory() {
        a.chooseTab(1);
        int visits = a.navigation.history.size();
        a.showTab(); a.showTab(); a.chooseTab(1); a.chooseSource(a.sourceTab);
        assertEquals(visits, a.navigation.history.size());
        a.onBackPressed(); assertEquals(0, a.tab);
    }

    @Test public void sourceAndLibraryFilterVisitsRestorePreviousSelection() {
        a.chooseTab(1); a.chooseSource(0); a.chooseSource(2);
        a.onBackPressed(); assertEquals(1, a.tab); assertEquals(0, a.sourceTab);
        a.onBackPressed(); assertEquals(1, a.tab); assertEquals(1, a.sourceTab);
        a.onBackPressed(); assertEquals(0, a.tab);
        a.chooseTab(4); a.chooseLibraryFilter(1); a.chooseTab(7);
        a.onBackPressed(); assertEquals(4, a.tab); assertEquals(1, a.libraryFilter);
        a.onBackPressed(); assertEquals(4, a.tab); assertEquals(0, a.libraryFilter);
        a.onBackPressed(); assertEquals(0, a.tab);
    }

    @Test public void outgoingRenderedSelectionSurvivesCallerChangingFilterBeforeVisit() {
        a.chooseTab(1); a.chooseSource(2);
        a.libraryFilter = 1; a.chooseTab(4);
        a.onBackPressed(); assertEquals(1, a.tab); assertEquals(2, a.sourceTab);
        a.onBackPressed(); assertEquals(1, a.sourceTab);
    }

    @Test public void backClosesTopDialogThenDrawerBeforeChangingPage() {
        a.chooseTab(1); a.drawer();
        android.widget.PopupWindow drawer = a.drawerPopup;
        final int[] canceled = {0}, dismissed = {0};
        StudioDialogs.form(a, "Preview", new android.widget.TextView(a), "Save", () -> {}, () -> canceled[0]++);
        Dialog dialog = ShadowDialog.getLatestDialog();
        dialog.setOnDismissListener(ignored -> dismissed[0]++);
        a.onBackPressed();
        ShadowLooper.idleMainLooper(); a.handler.removeCallbacks(a.tick);
        assertFalse(dialog.isShowing()); assertEquals(1, canceled[0]); assertEquals(1, dismissed[0]);
        assertTrue(drawer.isShowing()); assertEquals(1, a.tab);
        a.onBackPressed(); assertFalse(drawer.isShowing()); assertEquals(1, a.tab);
        a.onBackPressed(); assertEquals(0, a.tab);
    }

    @Test public void plainAlertAndNativeCancelabilityRetainWindowPriority() {
        a.chooseTab(1);
        AlertDialog dialog = StudioDialogs.alert(a).setTitle("Stay").setCancelable(false).show();
        a.onBackPressed(); assertTrue(dialog.isShowing()); assertEquals(1, a.tab);
        dialog.setCancelable(true);
        a.onBackPressed(); assertFalse(dialog.isShowing()); assertEquals(1, a.tab);
        a.onBackPressed(); assertEquals(0, a.tab);
    }

    void layoutPage() {
        a.root.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY));
        a.root.layout(0, 0, 1080, 1000);
    }

    @Test public void backRestoresScrollAfterDestinationLayout() {
        a.chooseTab(6); layoutPage();
        a.pageScroll.scrollTo(0, 400);
        int scroll = a.pageScroll.getScrollY(); assertTrue("Settings page scrolls", scroll > 0);
        a.chooseTab(4); layoutPage();
        a.onBackPressed(); layoutPage();
        assertEquals(6, a.tab); assertEquals(scroll, a.pageScroll.getScrollY());
    }

    @Test public void returningThroughOnlineSourceClearsYouTubeOnlyWhenFilesRestored() {
        a.chooseTab(1); a.chooseSource(2);
        a.getPreferences(0).edit().putBoolean("youtubeConsent", true).commit();
        a.selectYouTube("dQw4w9WgXcQ"); assertEquals(1, a.tab); assertTrue(a.youtube.selected);
        StudioScreenshotTest.findButtonContaining(a.page,"To Live").performClick(); assertEquals(0,a.tab);
        a.onBackPressed(); assertEquals(1, a.tab); assertEquals(2, a.sourceTab); assertTrue(a.youtube.selected);
        a.onBackPressed(); assertEquals(1, a.sourceTab); assertFalse(a.youtube.selected);
        assertFalse(AudioService.youtubeSelected);
    }

    @Test public void sourceHistoryRespectsRecordingAndCountdownLocksWithoutConsumingVisit() throws Exception {
        java.lang.reflect.Field field = AudioService.class.getDeclaredField("countdown"); field.setAccessible(true);
        RecordingCountdown countdown = (RecordingCountdown) field.get(null);
        try {
            for (boolean recording : new boolean[]{true, false}) {
                a.navigation.history.clear(); a.chooseTab(0); a.chooseTab(1); a.chooseSource(2);
                a.getPreferences(0).edit().putBoolean("youtubeConsent", true).commit();
                a.selectYouTube("dQw4w9WgXcQ");
                assertEquals(1,a.tab); StudioScreenshotTest.findButtonContaining(a.page,"To Live").performClick();
                assertEquals(0, a.tab); assertTrue(a.youtube.selected);
                RecordingManager.active = recording;
                if (!recording) countdown.arm(10, android.os.SystemClock.elapsedRealtime());
                a.onBackPressed(); assertEquals("Normal page Back still works", 1, a.tab);
                int visits = a.navigation.history.size();
                a.onBackPressed();
                assertEquals(1, a.tab); assertEquals(2, a.sourceTab);
                assertTrue(a.youtube.selected); assertTrue(AudioService.youtubeSelected);
                assertEquals(visits, a.navigation.history.size());
                assertEquals(recording, RecordingManager.active); assertEquals(!recording, AudioService.waiting());
                RecordingManager.active = false; countdown.cancel();
                a.onBackPressed(); assertEquals(1, a.sourceTab); assertFalse(a.youtube.selected);
            }
        } finally { RecordingManager.active = false; countdown.cancel(); }
    }

    @Test public void savedStateRestoresPageSelectionsScrollAndHistory() {
        a.chooseTab(1); a.chooseSource(2); a.chooseTab(4); a.chooseLibraryFilter(1);
        Bundle saved = new Bundle(); a.onSaveInstanceState(saved);
        BackNavigation restored = new BackNavigation();
        BackNavigation.Page current = restored.restore(saved);
        assertEquals(4, current.tab); assertEquals(1, current.filter);
        assertEquals(4, restored.history.size());
        assertEquals(0, restored.previous().filter);
        BackNavigation.Page longPage = new BackNavigation.Page(1, 2, 0, 432);
        restored.save(saved, longPage);
        assertEquals(432, new BackNavigation().restore(saved).scroll);
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).create(saved).start().resume().visible();
        MainActivity recreated = controller.get(); recreated.handler.removeCallbacks(recreated.tick);
        assertEquals(1, recreated.tab); assertEquals(2, recreated.sourceTab);
        recreated.onBackPressed(); assertEquals(4, recreated.tab); assertEquals(0, recreated.libraryFilter);
        controller.pause().stop().destroy();
    }

    @Test public void backLeavesBackgroundAudioAndPendingPermissionIntact() {
        a.chooseTab(4); AudioService.active = true; RecordingManager.active = true;
        a.awaitingMicPermission = true; a.pendingMonitor = true;
        a.onBackPressed();
        assertEquals(0, a.tab); assertTrue(AudioService.active); assertTrue(RecordingManager.active);
        assertTrue(a.awaitingMicPermission); assertTrue(a.pendingMonitor);
    }

    @Test public void cameraPreparationDoesNotConsumeHistory() {
        a.chooseTab(4); a.videoPreparing = true;
        a.onBackPressed(); assertEquals(4, a.tab); assertEquals(1, a.navigation.history.size());
        a.videoPreparing = false;
        a.onBackPressed(); assertEquals(0, a.tab);
    }

    @Test public void cameraConfirmationOnlyPopsAfterAcceptance() {
        a.chooseTab(4);
        CameraController camera = new CameraController(a, new TextureView(a), new CameraController.Listener() {
            public void ready() {} public void error(String message) {}
        });
        a.camera = camera; camera.recording = true;
        a.onBackPressed();
        AlertDialog confirmation = (AlertDialog) ShadowDialog.getLatestDialog();
        assertEquals(4, a.tab); assertEquals(1, a.navigation.history.size()); assertTrue(camera.recording);
        a.onBackPressed(); // Cancel through the same path as native window Back.
        assertFalse(confirmation.isShowing()); assertEquals(1, a.navigation.history.size()); assertTrue(camera.recording);
        a.onBackPressed(); confirmation = (AlertDialog) ShadowDialog.getLatestDialog();
        // A real stop has asynchronous export; emulate the camera's completed stop before confirming.
        camera.recording = false;
        confirmation.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        ShadowLooper.idleMainLooper(); a.handler.removeCallbacks(a.tick);
        assertEquals(0, a.tab); assertTrue(a.navigation.history.isEmpty());
    }

    void mirrorManifestBackOptIn() throws Exception {
        java.nio.file.Path path = java.nio.file.Paths.get("src/main/AndroidManifest.xml");
        if (!java.nio.file.Files.exists(path)) path = java.nio.file.Paths.get("app/src/main/AndroidManifest.xml");
        javax.xml.parsers.DocumentBuilderFactory factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        org.w3c.dom.Element application = (org.w3c.dom.Element) factory.newDocumentBuilder()
                .parse(path.toFile()).getElementsByTagName("application").item(0);
        assertEquals("Production manifest opts in on API 33 as well as 35", "true",
                application.getAttributeNS("http://schemas.android.com/apk/res/android", "enableOnBackInvokedCallback"));
        // Robolectric 4.14.1 omits this application flag when parsing the manifest.
        // Mirror only the declared opt-in; actual platform callback registration is tested below.
        org.robolectric.util.ReflectionHelpers.callInstanceMethod(RuntimeEnvironment.getApplication().getApplicationInfo(),
                "setEnableOnBackInvokedCallback", org.robolectric.util.ReflectionHelpers.ClassParameter.from(boolean.class, true));
    }

    android.window.OnBackInvokedCallback registeredTop(android.window.OnBackInvokedDispatcher dispatcher) {
        // Activity exposes a stable proxy even after attachment; inspect its real window dispatcher.
        if (dispatcher.getClass().getName().equals("android.window.ProxyOnBackInvokedDispatcher")) {
            dispatcher = org.robolectric.util.ReflectionHelpers.getField(dispatcher, "mActualDispatcher");
            assertNotNull("Back dispatcher proxy is attached to a real window", dispatcher);
        }
        return org.robolectric.util.ReflectionHelpers.callInstanceMethod(dispatcher, "getTopCallback");
    }

    @Test @Config(sdk = {33, 35}) public void modernCallbackRegisteredAndRoutesThroughModalAndPageBack() throws Exception {
        java.lang.reflect.Field registration = MainActivity.class.getDeclaredField("modernBack");
        registration.setAccessible(true);
        Object modern = registration.get(a); assertNotNull(modern);
        java.lang.reflect.Field callbackField = modern.getClass().getDeclaredField("callback");
        callbackField.setAccessible(true);
        android.window.OnBackInvokedCallback callback = (android.window.OnBackInvokedCallback) callbackField.get(modern);
        android.window.OnBackInvokedCallback registered = registeredTop(a.getOnBackInvokedDispatcher());
        assertSame("Callback is actually registered with the platform", callback, registered);
        a.chooseTab(1);
        AlertDialog dialog = StudioDialogs.alert(a).setTitle("Menu").show();
        ShadowLooper.idleMainLooper(); a.handler.removeCallbacks(a.tick);
        android.window.OnBackInvokedCallback dialogBack = registeredTop(dialog.getOnBackInvokedDispatcher());
        assertNotNull("Native dialog owns its window Back callback", dialogBack);
        dialogBack.onBackInvoked(); assertFalse(dialog.isShowing()); assertEquals(1, a.tab);
        a.drawer();
        ShadowLooper.idleMainLooper(); a.handler.removeCallbacks(a.tick);
        android.widget.PopupWindow drawer = a.drawerPopup;
        int visits = a.navigation.history.size();
        android.window.OnBackInvokedDispatcher drawerDispatcher = drawer.getContentView().findOnBackInvokedDispatcher();
        assertNotNull("Attached drawer owns a native window dispatcher", drawerDispatcher);
        android.window.OnBackInvokedCallback drawerBack = registeredTop(drawerDispatcher);
        assertNotNull("Native popup registers its own Back callback", drawerBack);
        assertNotSame("Popup callback has priority over activity page Back", callback, drawerBack);
        drawerBack.onBackInvoked();
        assertFalse(drawer.isShowing()); assertEquals(1, a.tab); assertEquals(visits, a.navigation.history.size());
        registered.onBackInvoked(); assertEquals(0, a.tab);
        java.lang.reflect.Field dispatcherField = modern.getClass().getDeclaredField("dispatcher");
        dispatcherField.setAccessible(true);
        assertSame(a.getOnBackInvokedDispatcher(), dispatcherField.get(modern));
    }
}
