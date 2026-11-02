package pro.livemic.studio;

import android.content.Context;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
public class AudioRoutingTest {
    private Context context;

    @Before
    public void reset() {
        context = RuntimeEnvironment.getApplication();
        AudioRouting.prefs(context).edit().clear().commit();
    }

    @Test
    public void selectionsPersistIndependently() {
        AudioRouting.chooseInput(context, 123456);
        AudioRouting.chooseOutput(context, 654321);
        assertEquals(123456, AudioRouting.selectedInput(context));
        assertEquals(654321, AudioRouting.selectedOutput(context));
        AudioRouting.chooseOutput(context, 0);
        assertEquals(123456, AudioRouting.selectedInput(context));
    }

    @Test
    public void builtInDeviceTypesHaveDistinctLabels() {
        android.media.AudioDeviceInfo mic = org.robolectric.shadows.AudioDeviceInfoBuilder.newBuilder()
                .setType(android.media.AudioDeviceInfo.TYPE_BUILTIN_MIC).build();
        android.media.AudioDeviceInfo speaker = org.robolectric.shadows.AudioDeviceInfoBuilder.newBuilder()
                .setType(android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER).build();
        android.media.AudioDeviceInfo earpiece = org.robolectric.shadows.AudioDeviceInfoBuilder.newBuilder()
                .setType(android.media.AudioDeviceInfo.TYPE_BUILTIN_EARPIECE).build();
        assertNotEquals(AudioRouting.deviceLabel(context, mic), AudioRouting.deviceLabel(context, speaker));
        assertNotEquals(AudioRouting.deviceLabel(context, speaker), AudioRouting.deviceLabel(context, earpiece));
    }

    @Test
    public void prepareCapturesModeAfterOldRouteCloses() throws Exception {
        android.media.AudioManager manager = (android.media.AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        manager.setMode(android.media.AudioManager.MODE_IN_COMMUNICATION);
        AudioRouting next = new AudioRouting(context, 0);
        manager.setMode(android.media.AudioManager.MODE_NORMAL);
        next.prepare();
        java.lang.reflect.Field field = AudioRouting.class.getDeclaredField("previousMode");
        field.setAccessible(true);
        assertEquals(android.media.AudioManager.MODE_NORMAL, field.getInt(next));
        next.close();
    }

    @Test
    public void missingExplicitDevicesFailClearly() throws Exception {
        AudioRouting.chooseInput(context, 123456);
        try {
            new AudioRouting(context, 0);
            fail("missing input must fail");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().length() > 0);
        }
        new AudioRouting(context, 0, false);
        AudioRouting.chooseOutput(context, 654321);
        try {
            new AudioRouting(context, 0, false);
            fail("missing output must fail");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().length() > 0);
        }
    }

    @Test
    public void echoAndAutoGainPreferencesPersist() {
        assertTrue(AudioRouting.echoCancellationPreferred(context));
        assertFalse(AudioRouting.autoGainPreferred(context));
        AudioRouting.setEchoCancellationPreferred(context, false);
        AudioRouting.setAutoGainPreferred(context, true);
        assertFalse(AudioRouting.echoCancellationPreferred(context));
        assertTrue(AudioRouting.autoGainPreferred(context));
    }
}
