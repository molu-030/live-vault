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
    private android.media.AudioDeviceInfo usb(int id, String name, String address) {
        android.media.AudioDeviceInfo device = FeedbackRoutingTest.device(id, android.media.AudioDeviceInfo.TYPE_USB_DEVICE);
        Object port = org.robolectric.util.ReflectionHelpers.getField(device, "mPort");
        org.robolectric.util.ReflectionHelpers.setField(port, "mName", name);
        org.robolectric.util.ReflectionHelpers.setField(port, "mAddress", address);
        return device;
    }

    private void inputs(android.media.AudioDeviceInfo... devices) {
        android.media.AudioManager manager = (android.media.AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        org.robolectric.Shadows.shadowOf(manager).setInputDevices(java.util.Arrays.asList(devices));
    }

    private void expectMissingInput() throws Exception {
        try { new AudioRouting(context, 0); fail("unresolved explicit input must fail"); }
        catch (IllegalStateException expected) { assertTrue(expected.getMessage().length() > 0); }
    }

    @Test public void usbSelectionSurvivesNewPortIdWithoutChangingOutput() throws Exception {
        inputs(usb(41, "USB microphone", "usb:one"));
        AudioRouting.chooseInput(context, 41);
        AudioRouting.chooseOutput(context, 0);
        inputs();
        expectMissingInput();
        inputs(usb(77, "USB microphone", "usb:one"));
        assertEquals(77, AudioRouting.selectedInput(context));
        assertEquals(77, new AudioRouting(context, 41).input);
        assertEquals(0, AudioRouting.selectedOutput(context));
    }

    @Test public void recycledIdAndDifferentUsbAddressCannotReplaceSelection() throws Exception {
        inputs(usb(41, "USB microphone", "usb:one"));
        AudioRouting.chooseInput(context, 41);
        inputs(usb(41, "Other microphone", "usb:two"));
        expectMissingInput();
        inputs(usb(77, "USB microphone", "usb:two"));
        expectMissingInput();
        assertEquals(41, AudioRouting.selectedInput(context));
    }

    @Test public void addresslessReconnectRequiresUniqueNameAndType() throws Exception {
        inputs(usb(41, "USB microphone", ""));
        AudioRouting.chooseInput(context, 41);
        inputs(usb(77, "USB microphone", ""), usb(78, "USB microphone", ""));
        expectMissingInput();
        inputs(usb(77, "USB microphone", ""));
        assertEquals(77, new AudioRouting(context, 0).input);
    }

    @Test public void rollbackRestoresIdentityTogetherWithInputId() throws Exception {
        inputs(usb(41, "First microphone", "usb:one"), usb(51, "Second microphone", "usb:two"));
        AudioRouting.chooseInput(context, 41);
        AudioRouting old = new AudioRouting(context, 0);
        AudioRouting.chooseInput(context, 51);
        old.restoreSelection();
        inputs(usb(77, "First microphone", "usb:one"), usb(51, "Second microphone", "usb:two"));
        assertEquals(77, new AudioRouting(context, 0).input);
    }

    @Test public void explicitCurrentPortStillWorksWithIdenticalAddresslessDevices() throws Exception {
        inputs(usb(41, "USB microphone", ""), usb(51, "USB microphone", ""));
        AudioRouting.chooseInput(context, 51);
        assertEquals(51, new AudioRouting(context, 0).input);
    }

    @Test public void recycledInputPortIsLabelledDisconnected() {
        inputs(usb(41, "USB microphone", "usb:one"));
        AudioRouting.chooseInput(context, 41);
        inputs(usb(41, "Other microphone", "usb:two"));
        assertFalse(AudioRouting.inputLabel(context).contains("Other microphone"));
    }

}
