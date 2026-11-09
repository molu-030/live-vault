package pro.livemic.studio;

import android.content.Context;
import android.media.AudioManager;
import java.util.Collections;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowLooper;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28, instrumentedPackages={"pro.livemic.studio"}, shadows={StudioScreenshotTest.Audio.class,StudioScreenshotTest.Composer.class,StudioScreenshotTest.Harmony.class})
@LooperMode(LooperMode.Mode.PAUSED)
public class RecordingSyncUiTest {
    MainActivity activity;
    @Before public void setup() throws Exception {
        ExitCoordinator.resetForLaunch();
        Context context = RuntimeEnvironment.getApplication();
        AppLanguage.set(context, true);
        AudioRouting.prefs(context).edit().clear().commit();
        AudioService.active = AudioService.playbackActive = false;
        RecordingManager.active = false;
        StudioScreenshotTest.Audio.supplied = new float[22];
        StudioScreenshotTest.Audio.actualOutputDevice = 0;
        AudioManager manager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        Shadows.shadowOf(manager).setInputDevices(Collections.emptyList());
        Shadows.shadowOf(manager).setOutputDevices(Collections.emptyList());
        activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        activity.composer.worker.submit(() -> {}).get();
        ShadowLooper.idleMainLooper(); activity.handler.removeCallbacks(activity.tick);
    }
    @After public void cleanup() {
        activity.handler.removeCallbacksAndMessages(null);
        activity.youtube.destroy(); activity.io.shutdown();
        AudioRouting.prefs(activity).edit().clear().commit();
        StudioScreenshotTest.Audio.supplied = null;
        AppLanguage.set(activity, false);
    }
    @Test public void unconfirmedProfileExplainsItsSeparateStorageAndShowsSavedManualOffset() {
        activity.chooseTab(6);
        RecordingSyncProfiles.Profile profile = RecordingSyncProfiles.current(activity);
        assertFalse(profile.confirmed);
        String text = AudioSetupGuideTest.text(activity.page);
        assertTrue(text.contains(profile.label));
        assertTrue(text.contains("Adjustments save only to this unconfirmed profile"));
        assertTrue(text.contains("identifying the devices uses a separate device-pair profile"));
        assertTrue(text.contains("does not automatically measure"));
        assertTrue(RecordingSyncProfiles.save(activity, profile, 35));
        activity.showTab();
        String saved = AudioSetupGuideTest.text(activity.page);
        assertTrue(saved.contains("Using the manual offset saved for this route."));
        assertTrue(saved.contains("Vocal offset: 35 ms"));
    }
}
