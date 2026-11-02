package pro.livemic.studio;

import android.os.Bundle;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowLooper;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, instrumentedPackages = {"pro.livemic.studio"}, shadows = {StudioScreenshotTest.Audio.class, StudioScreenshotTest.Composer.class, StudioScreenshotTest.Harmony.class})
@LooperMode(LooperMode.Mode.PAUSED)
public class LiveConsoleTest {
    MainActivity a;

    @Before
    public void setup() throws Exception {
        AppLanguage.set(RuntimeEnvironment.getApplication(), true);
        AudioService.active = false;
        AudioService.playbackActive = false;
        RecordingManager.active = false;
        RecordingManager.paused = false;
        AudioService.composerRequested = false;
        StudioScreenshotTest.Audio.supplied = null;
        StudioScreenshotTest.Audio.changes.clear();
        a = Robolectric.buildActivity(MainActivity.class).setup().get();
        a.handler.removeCallbacks(a.tick);
        a.composer.worker.submit(() -> {}).get();
        ShadowLooper.idleMainLooper();
        a.handler.removeCallbacks(a.tick);
        a.chooseTab(0);
    }

    @After
    public void cleanup() {
        a.handler.removeCallbacks(a.tick);
        a.io.shutdown();
        AudioService.composerRequested = false;
        AudioService.active = false;
        RecordingManager.active = false;
        RecordingManager.paused = false;
        StudioScreenshotTest.Audio.supplied = null;
        AppLanguage.set(RuntimeEnvironment.getApplication(), false);
    }

    static void adjust(SeekBar slider, int value) {
        slider.setProgress(value);
        assertTrue(slider.onKeyDown(KeyEvent.KEYCODE_DPAD_RIGHT,
                new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT)));
    }

    @Test
    public void mainEffectSlidersPersistAndWriteOnlyReverbAndEcho() {
        SeekBar[] sliders = {a.liveScreen.reverbVolume, a.liveScreen.echoVolume};
        int[] ids = {2, 4};
        for (int i = 0; i < sliders.length; i++) {
            StudioScreenshotTest.Audio.changes.clear();
            adjust(sliders[i], 35);
            assertFalse(StudioScreenshotTest.Audio.changes.isEmpty());
            for (float[] change : StudioScreenshotTest.Audio.changes) assertEquals(ids[i], (int) change[0]);
            assertEquals(a.values[ids[i]], a.getPreferences(0).getFloat("p" + ids[i], -1), .001);
        }
        float reverb = a.values[2], echo = a.values[4];
        a.chooseTab(4);
        a.chooseTab(0);
        assertEquals(Math.round(reverb * 100), a.liveScreen.reverbVolume.getProgress());
        assertEquals(Math.round(echo * 100), a.liveScreen.echoVolume.getProgress());
    }

    @Test
    public void synchronizationAndPollingNeverWriteAudioParameters() {
        StudioScreenshotTest.Audio.changes.clear();
        a.liveScreen.sync();
        a.liveScreen.poll(new float[22], false);
        assertTrue(StudioScreenshotTest.Audio.changes.isEmpty());
    }

    @Test
    public void resetDoesNotStopMicOrRecordingOrTouchOtherParameters() {
        AudioService.active = true;
        RecordingManager.active = true;
        a.values[11] = 1;
        a.values[13] = 1;
        a.values[0] = 1.4f;
        a.values[1] = .6f;
        StudioScreenshotTest.Audio.changes.clear();
        a.liveScreen.reset();
        assertTrue(AudioService.active);
        assertTrue(RecordingManager.active);
        assertEquals(1, a.values[11], 0);
        assertEquals(1, a.values[13], 0);
        assertEquals(1.4f, a.values[0], 0);
        assertEquals(.6f, a.values[1], 0);
        Set<Integer> keys = new HashSet<>();
        for (float[] v : StudioScreenshotTest.Audio.changes) keys.add((int) v[0]);
        assertTrue(keys.containsAll(Arrays.asList(2, 3, 4, 5, 6)));
        for (int key : new int[]{0, 1, 11, 13}) assertFalse(keys.contains(key));
    }

    @Test
    public void displayShowsIndependentActualLevelsPitchAndRecordingTime() {
        float[] stats = new float[22];
        stats[0] = 440;
        stats[1] = 17.5f;
        stats[2] = 69;
        stats[3] = .1f;
        stats[4] = .01f;
        stats[8] = 12;
        RecordingManager.active = true;
        a.liveScreen.poll(stats, true);
        assertTrue(a.liveScreen.display.note.contains("A4"));
        assertEquals(440, a.liveScreen.display.frequency, .01);
        assertEquals(17.5f, a.liveScreen.display.cents, .001);
        assertEquals(-20, a.liveScreen.display.inputDb, .01);
        assertEquals(-40, a.liveScreen.display.outputDb, .01);
        assertTrue(a.liveScreen.display.recordClock.contains("00:12"));
        stats[0] = 0;
        stats[3] = 0;
        stats[4] = Float.NaN;
        a.liveScreen.poll(stats, true);
        assertFalse(a.liveScreen.display.note.contains("A4"));
        assertEquals(0, a.liveScreen.display.frequency, 0);
        assertEquals(0, a.liveScreen.display.cents, 0);
        assertFalse(Float.isNaN(a.liveScreen.display.inputDb));
        assertFalse(Float.isNaN(a.liveScreen.display.outputDb));
        stats[3] = Float.POSITIVE_INFINITY;
        stats[4] = -.1f;
        a.liveScreen.poll(stats, false);
        assertFalse(Float.isNaN(a.liveScreen.display.inputDb));
        assertFalse(Float.isNaN(a.liveScreen.display.outputDb));
    }

    @Test
    public void idleAndBusyStatesDoNotDisplayStaleRecordingDuration() {
        float[] stats = new float[22];
        stats[8] = 999;
        stats[0] = Float.NaN;
        stats[2] = 69;
        a.liveScreen.poll(stats, false);
        assertEquals("00:00", a.liveScreen.display.recordClock);
        assertEquals("READY", a.liveScreen.display.recordStatus);
        assertEquals("—", a.liveScreen.display.note);
        assertEquals(0, a.liveScreen.display.cents, 0);
        a.busy = true;
        a.liveScreen.poll(stats, false);
        assertEquals("PREPARING / SAVING", a.liveScreen.display.recordStatus);
        assertEquals("00:00", a.liveScreen.display.recordClock);
        a.busy = false;
    }

    @Test
    public void presetAndSafetyMuteKeepRecordingAndMicrophoneActive() {
        AudioService.active = true;
        RecordingManager.active = true;
        float[] stats = new float[22];
        stats[10] = 1;
        stats[17] = 1;
        StudioScreenshotTest.Audio.supplied = stats;
        StudioScreenshotTest.Audio.changes.clear();
        a.monitoringOptions();
        assertTrue(AudioService.active);
        assertTrue(RecordingManager.active);
        for (float[] change : StudioScreenshotTest.Audio.changes) assertNotEquals(11, (int) change[0]);
        a.preset(1);
        assertTrue(AudioService.active);
        assertTrue(RecordingManager.active);
        assertEquals(.3, a.values[2], .001);
        assertTrue(a.liveScreen.reverbLabel.getText().toString().contains("30%"));
    }

    @Test
    public void videoOpensPreviewAndIsDisabledWhileBusy() {
        a.liveScreen.video.performClick();
        assertEquals(3, a.tab);
        assertFalse(RecordingManager.active);
        a.chooseTab(0);
        a.liveScreen.transport(true, false, false);
        assertFalse(a.liveScreen.video.isEnabled());
        a.liveScreen.transport(false, true, false);
        assertFalse(a.liveScreen.video.isEnabled());
        a.liveScreen.transport(false, false, false);
        assertTrue(a.liveScreen.video.isEnabled());
    }

    @Test
    public void pausedAndMonitorStatusComeFromStats() {
        float[] stats = new float[22];
        stats[8] = 12;
        stats[21] = 1;
        RecordingManager.active = true;
        RecordingManager.paused = true;
        a.liveScreen.poll(stats, true);
        assertTrue(a.liveScreen.display.recordClock.contains("00:12"));
        assertEquals("PAUSED", a.liveScreen.display.recordStatus);
        assertTrue(a.liveScreen.display.routeStatus.toLowerCase(java.util.Locale.US).contains("monitoring on"));
        RecordingManager.paused = false;
    }

    static Switch findSwitch(View v, String label) {
        if (v instanceof Switch && ((Switch) v).getText().toString().contains(label)) return (Switch) v;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                Switch found = findSwitch(g.getChildAt(i), label);
                if (found != null) return found;
            }
        }
        return null;
    }

    @Test
    public void liveBackingVolumePersistsAndWritesOnlyBackingGain() {
        AudioService.active = true;
        boolean composerPlaying = a.composer.playing();
        a.liveScreen.backingVolume.setProgress(40);
        StudioScreenshotTest.Audio.changes.clear();
        assertTrue(a.liveScreen.backingVolume.onKeyDown(KeyEvent.KEYCODE_DPAD_RIGHT, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT)));
        assertFalse(StudioScreenshotTest.Audio.changes.isEmpty());
        for (float[] change : StudioScreenshotTest.Audio.changes) assertEquals(1, (int) change[0]);
        float value = a.values[1];
        assertTrue(value > 0);
        assertEquals(value, a.getPreferences(0).getFloat("p1", -1), .001);
        assertTrue(AudioService.active);
        assertEquals(composerPlaying, a.composer.playing());
        a.chooseTab(4);
        a.chooseTab(0);
        assertEquals(Math.round(value * 100), a.liveScreen.backingVolume.getProgress());
    }

    @Test
    public void sourceChooserRetainsBackingSourcesAndDeviceAccess() {
        a.liveScreen.sourceButton.performClick();
        android.app.Dialog dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog();
        assertNotNull(dialog);
        View content = dialog.getWindow().getDecorView();
        for (String label : new String[]{"Input", "Output", "files", "Online", "Composer"})
            assertNotNull("Source chooser entry: " + label, StudioScreenshotTest.findButtonContaining(content, label));
        dialog.dismiss();
        for (String device : new String[]{"Input", "Output"}) {
            a.liveScreen.sourceButton.performClick();
            android.app.Dialog chooser = org.robolectric.shadows.ShadowDialog.getLatestDialog();
            StudioScreenshotTest.findButtonContaining(chooser.getWindow().getDecorView(), device).performClick();
            android.app.Dialog routes = org.robolectric.shadows.ShadowDialog.getLatestDialog();
            assertNotSame("Device selector opens from unified source chooser", chooser, routes);
            assertTrue(routes.isShowing());
            routes.dismiss();
        }
        a.liveScreen.openSource(1);
        assertEquals(1, a.tab);
        assertEquals(1, a.sourceTab);
        a.nav.getChildAt(1).performClick();
        assertEquals(7, a.tab);
        a.nav.getChildAt(2).performClick();
        assertEquals(4, a.tab);
        a.nav.getChildAt(0).performClick();
        assertEquals(0, a.tab);
        a.liveScreen.openSource(2);
        assertEquals(2, a.sourceTab);
    }

    @Test
    public void monitorStatusUsesStatsEvenWhenCachedSettingDisagrees() {
        float[] stats = new float[22];
        a.values[11] = 1;
        stats[21] = 0;
        a.liveScreen.poll(stats, true);
        assertTrue(a.liveScreen.display.routeStatus.toLowerCase(java.util.Locale.US).contains("monitoring off"));
        a.values[11] = 0;
        stats[21] = 1;
        a.liveScreen.poll(stats, true);
        assertTrue(a.liveScreen.display.routeStatus.toLowerCase(java.util.Locale.US).contains("monitoring on"));
        stats[17] = 1;
        a.liveScreen.poll(stats, true);
        assertTrue(a.liveScreen.display.routeStatus.toLowerCase(java.util.Locale.US).contains("safety mute"));
    }

    @Test
    public void noiseReductionInDetailedEffectsUpdatesParameter() {
        a.chooseTab(6);
        Switch noise = findSwitch(a.page, "Noise reduction");
        assertNotNull(noise);
        boolean next = !noise.isChecked();
        StudioScreenshotTest.Audio.changes.clear();
        noise.performClick();
        assertEquals(next ? 1 : 0, a.values[19], 0);
        assertEquals(a.values[19], a.getPreferences(0).getFloat("p19", -1), 0);
        assertEquals(19, (int) StudioScreenshotTest.Audio.changes.get(0)[0]);
        a.chooseTab(0);
        assertEquals(next ? 1 : 0, a.values[19], 0);
    }

    static int countSliders(View view) {
        int count = view.createAccessibilityNodeInfo().getRangeInfo() != null ? 1 : 0;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup)view;
            for (int i = 0; i < group.getChildCount(); i++) count += countSliders(group.getChildAt(i));
        }
        return count;
    }

    @Test
    public void detailsKeepAdvancedEffectsAndPresetResetActions() {
        assertEquals("Only mic, backing, reverb and echo gains on main", 4, countSliders(a.page));
        a.liveScreen.detailsButton.performClick();
        android.app.Dialog dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog();
        assertNotNull(dialog);
        View content = dialog.getWindow().getDecorView();
        assertTrue("Advanced effect controls remain available", countSliders(content) >= 5);
        View decay = StudioScreenshotTest.findDescription(content, "Reverb decay");
        assertNotNull(decay);
        Bundle value = new Bundle();
        value.putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, 3.4f);
        StudioScreenshotTest.Audio.changes.clear();
        assertTrue(decay.performAccessibilityAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.getId(), value));
        assertEquals(3.4f, a.values[3], .001);
        assertEquals(3.4f, a.getPreferences(0).getFloat("p3", -1), .001);
        assertFalse(StudioScreenshotTest.Audio.changes.isEmpty());
        for (float[] change : StudioScreenshotTest.Audio.changes) {
            assertEquals(3, (int) change[0]);
            assertEquals(3.4f, change[1], .001);
        }
        assertNotNull(StudioScreenshotTest.findButtonContaining(content, "Preset"));
        assertNotNull(StudioScreenshotTest.findButtonContaining(content, "Reset"));
        dialog.dismiss();
    }

    @Test
    public void playingSelectedFileDoesNotRequestMicrophonePermission() {
        org.robolectric.Shadows.shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(android.Manifest.permission.RECORD_AUDIO);
        a.sourceTab = 1;
        a.selectedFile = "local-backing.wav";
        a.awaitingMicPermission = false;
        StudioScreenshotTest.Audio.localPlayCalls.clear();
        StudioScreenshotTest.Audio.microphoneStarts = 0;
        a.liveScreen.backingPlayButton.performClick();
        assertFalse(a.awaitingMicPermission);
        assertFalse(AudioService.active);
        assertEquals(0, StudioScreenshotTest.Audio.microphoneStarts);
        assertTrue(StudioScreenshotTest.Audio.localPlayCalls.contains(true));
    }

    @Test
    public void pausedCurrentProjectResumesComposerInsteadOfStaleFile() throws Exception {
        a.selectedFile = "stale-file.wav";
        AudioService.composerRequested = true;
        StudioScreenshotTest.Audio.localPlayCalls.clear();
        StudioScreenshotTest.Composer.transportCalls.clear();
        a.liveScreen.backingPlayButton.performClick();
        a.composer.worker.submit(() -> {}).get();
        ShadowLooper.idleMainLooper();
        assertTrue(StudioScreenshotTest.Composer.transportCalls.contains(1));
        assertFalse(StudioScreenshotTest.Audio.localPlayCalls.contains(true));
        assertTrue(AudioService.composerRequested);
    }

    @Test
    public void captureLocksDeviceSettings() {
        a.composer.capturing = true;
        assertTrue(a.settingsLocked());
        a.composer.capturing = false;
        a.harmony.state[4] = 0;
        assertTrue(a.settingsLocked());
        a.harmony.state[4] = -1;
        a.harmony.state[5] = 0;
        assertTrue(a.settingsLocked());
        a.harmony.state[5] = -1;
        assertFalse(a.settingsLocked());
    }

    @Test
    public void monitorOffAndOnUpdatesComposerWithoutStoppingRecordingOrBacking() {
        AudioService.active = true;
        AudioService.playbackActive = true;
        RecordingManager.active = true;
        StudioScreenshotTest.Composer.monitorCalls.clear();
        StudioScreenshotTest.Composer.transportCalls.clear();
        StudioScreenshotTest.Audio.changes.clear();
        a.set(11, 0);
        a.set(11, 1);
        assertEquals(Arrays.asList(false, true), StudioScreenshotTest.Composer.monitorCalls);
        assertTrue(AudioService.active);
        assertTrue(AudioService.playbackActive);
        assertTrue(RecordingManager.active);
        assertTrue(StudioScreenshotTest.Composer.transportCalls.isEmpty());
        assertEquals(2, StudioScreenshotTest.Audio.changes.size());
        for (float[] change : StudioScreenshotTest.Audio.changes) assertEquals(11, (int) change[0]);
    }

    @Test
    public void hamburgerMenuNavigatesToLiveBackingComposerAndLibrary() {
        assertEquals(View.GONE, a.nav.getVisibility());
        a.drawer();
        View liveItem = StudioScreenshotTest.findDescription(a.drawerPopup.getContentView(), "Live,");
        assertNotNull(liveItem);
        liveItem.performClick();
        assertEquals(0, a.tab);

        a.drawer();
        View backingItem = StudioScreenshotTest.findDescription(a.drawerPopup.getContentView(), "Backing,");
        assertNotNull(backingItem);
        backingItem.performClick();
        assertEquals(1, a.tab);

        a.drawer();
        View composerItem = StudioScreenshotTest.findDescription(a.drawerPopup.getContentView(), "Composer,");
        assertNotNull(composerItem);
        composerItem.performClick();
        assertEquals(7, a.tab);

        a.drawer();
        View libraryItem = StudioScreenshotTest.findDescription(a.drawerPopup.getContentView(), "Library,");
        assertNotNull(libraryItem);
        libraryItem.performClick();
        assertEquals(4, a.tab);
    }

    @Test
    public void liveMicVolumeAndMonitorToggleSyncWithParameters() {
        a.chooseTab(0);
        StudioScreenshotTest.Audio.changes.clear();
        a.liveScreen.micVolume.setProgress(150);
        assertTrue(a.liveScreen.micVolume.onKeyDown(KeyEvent.KEYCODE_DPAD_RIGHT, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT)));
        assertFalse(StudioScreenshotTest.Audio.changes.isEmpty());
        assertEquals(0, (int) StudioScreenshotTest.Audio.changes.get(0)[0]);
        assertTrue(a.values[0] > 1.4f);
        assertTrue(a.liveScreen.micVolumeLabel.getText().toString().contains(Math.round(a.values[0] * 100) + "%"));
        assertEquals(a.values[0], a.getPreferences(0).getFloat("p0", -1), .001);

        useHeadphones();
        AudioService.active = true;
        float prevMonitor = a.values[11];
        float[] stats = new float[22];
        stats[21] = prevMonitor;
        StudioScreenshotTest.Audio.supplied = stats;
        a.liveScreen.monitorToggle.performClick();
        assertNotEquals(prevMonitor, a.values[11], .001f);
        stats[21] = a.values[11]; // Native set updates the engine state before the next click.
        a.liveScreen.monitorToggle.performClick();
        assertEquals(prevMonitor, a.values[11], .001f);
    }

    void useHeadphones() {
        android.media.AudioManager manager = (android.media.AudioManager)a.getSystemService(android.content.Context.AUDIO_SERVICE);
        android.media.AudioDeviceInfo headphones = org.robolectric.shadows.AudioDeviceInfoBuilder.newBuilder()
                .setType(android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES).build();
        org.robolectric.Shadows.shadowOf(manager).setOutputDevices(Collections.singletonList(headphones));
    }

    @Test
    public void activeMonitorClicksFollowActualStateWhenCachedSettingDisagrees() {
        useHeadphones();
        AudioService.active = true;
        AudioService.playbackActive = true;
        RecordingManager.active = true;
        float[] stats = new float[22];
        stats[16] = 1;
        StudioScreenshotTest.Audio.supplied = stats;
        for (boolean settingsDialog : new boolean[]{false, true}) {
            android.app.Dialog dialog = null;
            View control = a.liveScreen.monitorToggle;
            if (settingsDialog) {
                a.monitoringOptions();
                dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog();
                control = StudioScreenshotTest.findButtonContaining(dialog.getWindow().getDecorView(), "Voice monitoring");
                assertNotNull(control);
            }
            for (int actual : new int[]{0, 1}) {
                a.values[11] = 1 - actual;
                stats[21] = actual;
                StudioScreenshotTest.Audio.changes.clear();
                control.performClick();
                assertEquals(1 - actual, a.values[11], 0);
                assertFalse(StudioScreenshotTest.Audio.changes.isEmpty());
                for (float[] change : StudioScreenshotTest.Audio.changes) {
                    assertEquals(11, (int)change[0]);
                    assertEquals(1 - actual, change[1], 0);
                }
                assertTrue(AudioService.active);
                assertTrue(AudioService.playbackActive);
                assertTrue(RecordingManager.active);
                assertEquals(1, stats[16], 0);
            }
            if (dialog != null) dialog.dismiss();
        }
    }

    @Test
    public void audioSettingsExposesAllFourProcessingSwitches() {
        a.chooseTab(6);
        Switch monitor = findSwitch(a.page, "Voice monitoring");
        Switch noise = findSwitch(a.page, "Noise reduction");
        Switch echo = findSwitch(a.page, "Echo cancellation");
        Switch agc = findSwitch(a.page, "Auto gain control");
        assertNotNull(monitor);
        assertNotNull(noise);
        assertNotNull(echo);
        assertNotNull(agc);

        boolean nextEcho = !echo.isChecked();
        echo.performClick();
        assertEquals(nextEcho, AudioRouting.echoCancellationPreferred(a));

        boolean nextAgc = !agc.isChecked();
        agc.performClick();
        assertEquals(nextAgc, AudioRouting.autoGainPreferred(a));
    }
}
