package pro.livemic.studio;

import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowLooper;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28, instrumentedPackages={"pro.livemic.studio"}, shadows={StudioScreenshotTest.Audio.class,StudioScreenshotTest.Composer.class,StudioScreenshotTest.Harmony.class})
@LooperMode(LooperMode.Mode.PAUSED)
public class AudioSetupGuideTest {
    MainActivity a;
    @Before public void setup() throws Exception {
        ExitCoordinator.resetForLaunch();
        AudioService.active = AudioService.playbackActive = false;
        StudioScreenshotTest.Audio.supplied = new float[22];
        a = Robolectric.buildActivity(MainActivity.class).setup().get();
        AppLanguage.set(a, true);
        AudioRouting.prefs(a).edit().clear().commit();
        a.handler.removeCallbacks(a.tick);
        a.composer.worker.submit(() -> {}).get();
        ShadowLooper.idleMainLooper();
        a.handler.removeCallbacks(a.tick);
    }
    @After public void cleanup() {
        a.handler.removeCallbacksAndMessages(null);
        a.io.shutdown();
        AudioService.active = AudioService.playbackActive = false;
        RecordingManager.active = false;
        StudioScreenshotTest.Audio.supplied = null;
        AudioRouting.prefs(a).edit().clear().commit();
        AppLanguage.set(a, false);
    }
    static String text(View v) {
        String result = v instanceof TextView ? ((TextView)v).getText() + "\n" : "";
        if (v instanceof ViewGroup) for (int i=0;i<((ViewGroup)v).getChildCount();i++) result += text(((ViewGroup)v).getChildAt(i));
        return result;
    }
    static Button button(View v, String label) {
        if (v instanceof Button && ((Button)v).getText().toString().equals(label)) return (Button)v;
        if (v instanceof ViewGroup) for (int i=0;i<((ViewGroup)v).getChildCount();i++) {
            Button found = button(((ViewGroup)v).getChildAt(i), label);
            if (found != null) return found;
        }
        return null;
    }
    @Test public void setupOpensWithoutStartingMicAndDescribesListeningTestHonestly() {
        int starts = StudioScreenshotTest.Audio.microphoneStarts;
        while (Shadows.shadowOf(a.getApplication()).getNextStartedService() != null) { }
        a.chooseTab(6);
        String page = text(a.page);
        assertTrue(page.contains("1 · Choose devices"));
        assertTrue(page.contains("2 · Check input level"));
        assertTrue(page.contains("3 · Check sync and protection"));
        assertTrue(page.contains("does not automatically measure round-trip latency"));
        assertTrue(page.contains("not saved per device"));
        assertNotNull(button(a.page, "Turn microphone on to check level"));
        assertEquals(starts, StudioScreenshotTest.Audio.microphoneStarts);
        assertNull(Shadows.shadowOf(a.getApplication()).getNextStartedService());
        assertFalse(AudioService.active);
    }
    @Test public void stepButtonsMoveToTheirSectionsInPageCoordinates() {
        a.chooseTab(6);
        int width = a.dp(412), height = a.dp(893);
        a.root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        a.root.layout(0, 0, width, height);
        button(a.page, "3 Sync / safety").performClick();
        int safety = a.pageScroll.getScrollY();
        assertTrue("Safety section is below the header", safety > a.dp(150));
        button(a.page, "2 Input").performClick();
        int input = a.pageScroll.getScrollY();
        assertTrue(input > 0);
        assertTrue(input < safety);
        button(a.page, "1 Devices").performClick();
        assertTrue(a.pageScroll.getScrollY() < input);
    }
    @Test public void inputReadoutRespondsToLiveLevelAndDeviceChangesPromptRecheck() {
        a.chooseTab(6);
        float[] stats = new float[22]; stats[3] = .9f;
        a.audioSetupGuide.poll(stats, true);
        assertTrue(text(a.page).contains("Input is high"));
        stats[3] = .0001f;
        a.audioSetupGuide.poll(stats, true);
        assertTrue(text(a.page).contains("Input is quiet"));
        a.audioSetupGuide.poll(stats, false);
        assertTrue(text(a.page).contains("Microphone off"));
        AudioRouting.chooseInput(a, 1729);
        a.showTab();
        assertTrue(text(a.page).contains("Devices changed. Recheck input level and sync."));
    }
    @Test public void effectComparisonSwitchesCompleteEffectsAndCancelRestoresOriginalWithoutTransportChanges() {
        a.selectedPreset = 2;
        a.presetChip = new Button(a);
        float[] original = a.values.clone();
        EffectComparison comparison = new EffectComparison(a);
        assertFalse(comparison.hasB());
        for (int key : EffectComparison.KEYS) a.set(key, original[key] + .15f);
        float[] alternative = a.values.clone();
        comparison.save(a, true);
        assertTrue(comparison.hasB());
        AudioRouting.setEchoCancellationPreferred(a, false);
        AudioRouting.setAutoGainPreferred(a, true);
        a.set(0, .44f); a.set(1, .33f); a.set(19, 0);
        a.values[11] = 1; a.values[12] = .42f; a.values[13] = 1; a.values[17] = 1;
        comparison.listen(a, false);
        for (int key : EffectComparison.KEYS) assertEquals("A key " + key, original[key], a.values[key], 0);
        assertEquals(2, a.selectedPreset);
        assertEquals("Concert hall ▾", a.presetChip.getText().toString());
        comparison.listen(a, true);
        for (int key : EffectComparison.KEYS) assertEquals("B key " + key, alternative[key], a.values[key], 0);
        assertFalse(AudioRouting.echoCancellationPreferred(a));
        assertTrue(AudioRouting.autoGainPreferred(a));
        comparison.cancel(a);
        for (int key : EffectComparison.KEYS) {
            assertEquals(original[key], a.values[key], 0);
            assertEquals(original[key], a.getPreferences(0).getFloat("p" + key, -1), 0);
        }
        assertFalse(AudioRouting.echoCancellationPreferred(a));
        assertTrue(AudioRouting.autoGainPreferred(a));
        assertEquals(.44f, a.values[0], 0); assertEquals(.33f, a.values[1], 0);
        assertEquals(0, a.values[19], 0);
        assertEquals(2, a.getPreferences(0).getInt("preset", -1));
        assertEquals(1, a.values[11], 0); assertEquals(.42f, a.values[12], 0);
        assertEquals(1, a.values[13], 0); assertEquals(1, a.values[17], 0);
        assertFalse(AudioService.active);
    }
    @Test public void recordingFromGlobalButtonKeepsComparedEffectsWhenLeavingSetup() {
        Shadows.shadowOf(a.getApplication()).grantPermissions(android.Manifest.permission.RECORD_AUDIO);
        a.captureOtherApps = false;
        a.recordDelay = 3;
        a.chooseTab(6);
        button(a.page, "Start effect A/B comparison").performClick();
        a.set(2, .73f);
        button(a.page, "Save current → B").performClick();
        while (Shadows.shadowOf(a.getApplication()).getNextStartedService() != null) { }
        a.globalRecord.performClick();
        assertNull("Starting the countdown finalizes A/B", a.effectComparison);
        android.content.Intent request = Shadows.shadowOf(a.getApplication()).getNextStartedService();
        assertNotNull(request);
        assertEquals(AudioService.RECORD, request.getAction());
        assertEquals(3, request.getIntExtra("delay", -1));
        assertEquals(.73f, a.values[2], 0);
        RecordingManager.active = true;
        a.chooseTab(0);
        assertEquals(.73f, a.values[2], 0);
        assertEquals(.73f, a.getPreferences(0).getFloat("p2", -1), 0);
    }
    @Test public void externallyStartedRecordingPreventsExitFromRestoringComparisonEffects() {
        a.chooseTab(6);
        button(a.page, "Start effect A/B comparison").performClick();
        a.set(2, .67f);
        RecordingManager.active = true;
        a.chooseTab(0);
        assertNull(a.effectComparison);
        assertEquals(.67f, a.values[2], 0);
    }

    @Test public void comparisonButtonsCanSaveSwitchAndCancelEditedSettings() {
        a.chooseTab(6);
        float reverb = a.values[2];
        button(a.page, "Start effect A/B comparison").performClick();
        assertNotNull(a.effectComparison);
        a.set(2, .75f);
        RecordingManager.active = true;
        button(a.page, "Load A").performClick();
        assertEquals(.75f, a.values[2], 0);
        RecordingManager.active = false;
        button(a.page, "Save current → B").performClick();
        button(a.page, "Load A").performClick();
        assertEquals(reverb, a.values[2], 0);
        button(a.page, "Load B").performClick();
        assertEquals(.75f, a.values[2], 0);
        button(a.page, "Cancel comparison · restore original").performClick();
        assertEquals(reverb, a.values[2], 0);
        assertNull(a.effectComparison);
        button(a.page, "Start effect A/B comparison").performClick();
        a.set(2, .64f);
        a.drawer();
        a.onBackPressed();
        assertNotNull("Back closes drawer before ending comparison", a.effectComparison);
        a.onBackPressed();
        assertNull(a.effectComparison);
        assertEquals(reverb, a.values[2], 0);
    }
}
