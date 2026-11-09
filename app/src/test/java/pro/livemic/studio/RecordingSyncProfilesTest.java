package pro.livemic.studio;

import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.AudioDeviceInfoBuilder;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={28,35}, instrumentedPackages={"pro.livemic.studio"}, shadows={RecordingSyncProfilesTest.Audio.class})
public class RecordingSyncProfilesTest {
    @Implements(NativeAudio.class) public static class Audio {
        static int input, output;
        @Implementation public static int inputDevice() { return input; }
        @Implementation public static int outputDevice() { return output; }
    }
    Context context;
    AudioManager manager;
    AudioDeviceInfo mic, outputA, outputB;

    @Before public void setup() {
        context = RuntimeEnvironment.getApplication();
        manager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        AudioRouting.prefs(context).edit().clear().commit();
        Audio.input = Audio.output = 0;
        AppLanguage.set(context, true);
        mic = device(11, AudioDeviceInfo.TYPE_BUILTIN_MIC, "Phone", "builtin");
        outputA = device(21, AudioDeviceInfo.TYPE_USB_HEADSET, "Headset", "usb-private-A");
        outputB = device(22, AudioDeviceInfo.TYPE_USB_HEADSET, "Headset", "usb-private-B");
        devices(mic, outputA, outputB);
    }

    static AudioDeviceInfo device(int id, int type, String name, String address) {
        AudioDeviceInfo device = AudioDeviceInfoBuilder.newBuilder().setType(type).build();
        Object port = ReflectionHelpers.getField(device, "mPort");
        Object handle = ReflectionHelpers.getField(port, "mHandle");
        ReflectionHelpers.setField(handle, "mId", id);
        ReflectionHelpers.setField(port, "mName", name);
        ReflectionHelpers.setField(port, "mAddress", address);
        return device;
    }
    void devices(AudioDeviceInfo input, AudioDeviceInfo... outputs) {
        Shadows.shadowOf(manager).setInputDevices(Collections.singletonList(input));
        Shadows.shadowOf(manager).setOutputDevices(Arrays.asList(outputs));
    }
    RecordingSyncProfiles.Profile select(int input, int output) {
        AudioRouting.chooseInput(context, input); AudioRouting.chooseOutput(context, output);
        return RecordingSyncProfiles.current(context);
    }

    @Test public void twoPairsKeepOffsetsIndependentAndNeverStoreIdentifyingDetails() {
        RecordingSyncProfiles.Profile a = select(11, 21);
        assertTrue(RecordingSyncProfiles.save(context, a, 125));
        RecordingSyncProfiles.Profile b = select(11, 22);
        assertNotEquals(a.key, b.key);
        assertTrue(RecordingSyncProfiles.save(context, b, -40));
        assertEquals(125, select(11, 21).offsetMs);
        assertEquals(-40, select(11, 22).offsetMs);
        assertFalse(a.key.contains("Headset")); assertFalse(a.label.contains("usb-private"));
        for (String key : AudioRouting.prefs(context).getAll().keySet()) assertFalse(key.contains("usb-private"));
    }

    @Test public void reconnectWithNewRuntimeIdsAndFreshPreferencesHandleRestoresSameProfile() {
        RecordingSyncProfiles.Profile original = select(11, 21);
        assertTrue(RecordingSyncProfiles.save(context, original, 95));
        devices(device(111, AudioDeviceInfo.TYPE_BUILTIN_MIC, "Phone", "builtin"),
                device(221, AudioDeviceInfo.TYPE_USB_HEADSET, "Headset", "usb-private-A"));
        RecordingSyncProfiles.Profile reconnected = select(111, 221);
        assertEquals(original.key, reconnected.key); assertEquals(95, reconnected.offsetMs); assertTrue(reconnected.saved);
        assertTrue(AudioRouting.prefs(context).edit().commit()); // Flush the earlier apply before reopening preferences.
        Context reopened = context.createConfigurationContext(context.getResources().getConfiguration());
        assertEquals("Persisted pair entry is available through the new context", 95,
                AudioRouting.prefs(reopened).getInt(original.key, 0));
        // ShadowAudioManager stores device inventories per manager instance, unlike Android's system service.
        // Give the new context the same connected hardware before testing route resolution after reopening.
        AudioManager reopenedManager = (AudioManager) reopened.getSystemService(Context.AUDIO_SERVICE);
        Shadows.shadowOf(reopenedManager).setInputDevices(Arrays.asList(manager.getDevices(AudioManager.GET_DEVICES_INPUTS)));
        Shadows.shadowOf(reopenedManager).setOutputDevices(Arrays.asList(manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)));
        RecordingSyncProfiles.Profile restored = RecordingSyncProfiles.current(reopened);
        assertEquals(reconnected.key, restored.key);
        assertEquals(95, restored.offsetMs); assertTrue(restored.saved);
    }

    @Test public void actualStreamRouteWinsOverRequestedDeviceAndUnlistedActualDoesNotPretendToMatch() {
        select(11, 21);
        Audio.input = 11; Audio.output = 22;
        RecordingSyncProfiles.Profile actual = RecordingSyncProfiles.current(context);
        Audio.output = 0;
        RecordingSyncProfiles.Profile requested = RecordingSyncProfiles.current(context);
        assertNotEquals(requested.key, actual.key);
        Audio.output = 999;
        RecordingSyncProfiles.Profile unlisted = RecordingSyncProfiles.current(context);
        assertFalse(unlisted.confirmed); assertNotEquals(requested.key, unlisted.key);
    }

    @Test public void defaultZeroUsesSeparateUnconfirmedProfileUntilStreamIsKnown() {
        RecordingSyncProfiles.Profile unknown = select(0, 0);
        assertFalse(unknown.confirmed); assertTrue(unknown.label.contains("unconfirmed"));
        assertTrue(RecordingSyncProfiles.save(context, unknown, 30));
        Audio.input = 11; Audio.output = 21;
        RecordingSyncProfiles.Profile actual = RecordingSyncProfiles.current(context);
        assertTrue(actual.confirmed); assertNotEquals(unknown.key, actual.key); assertEquals(0, actual.offsetMs);
        Audio.input = Audio.output = 0;
        assertEquals(30, RecordingSyncProfiles.current(context).offsetMs);
    }

    @Test public void legacyOutputModeIsReadOnlyFallbackUntilEachPairIsAdjusted() {
        AudioRouting.prefs(context).edit().putInt("sync0", 70).putInt("sync1", -25).commit();
        RecordingSyncProfiles.Profile first = select(11, 21);
        assertEquals(70, first.offsetMs); assertTrue(first.inherited); assertFalse(first.saved);
        assertTrue(RecordingSyncProfiles.save(context, first, 90));
        assertFalse(RecordingSyncProfiles.current(context).inherited);
        RecordingSyncProfiles.Profile second = select(11, 22);
        assertEquals(70, second.offsetMs); assertTrue(second.inherited);
        assertEquals(70, AudioRouting.prefs(context).getInt("sync0", 0));
        devices(mic, device(31, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, "Phone", "speaker"));
        Audio.input = 11; Audio.output = 31;
        AudioRouting.chooseOutput(context, 0);
        RecordingSyncProfiles.Profile defaultSpeaker = RecordingSyncProfiles.current(context);
        assertEquals("Default mode keeps its historical sync0 even when the speaker is identified", 70, defaultSpeaker.offsetMs);
        assertTrue(defaultSpeaker.inherited);
        AudioRouting.chooseOutput(context, 31);
        RecordingSyncProfiles.Profile explicitSpeaker = RecordingSyncProfiles.current(context);
        assertEquals("Explicit speaker mode keeps its historical sync1", -25, explicitSpeaker.offsetMs);
        assertEquals("The actual device pair shares one profile regardless of how it was selected", defaultSpeaker.key, explicitSpeaker.key);
        assertTrue(RecordingSyncProfiles.save(context, explicitSpeaker, 110));
        AudioRouting.chooseOutput(context, 0);
        assertEquals("A saved pair value supersedes either legacy fallback", 110, RecordingSyncProfiles.current(context).offsetMs);
    }

    @Test public void disconnectOrModeChangeRejectsStaleSliderAndKeepsOtherPairUntouched() {
        RecordingSyncProfiles.Profile first = select(11, 21);
        assertTrue(RecordingSyncProfiles.save(context, first, 60));
        select(11, 22);
        assertFalse(RecordingSyncProfiles.save(context, first, 300));
        assertEquals(0, RecordingSyncProfiles.current(context).offsetMs);
        assertEquals(60, select(11, 21).offsetMs);
        Shadows.shadowOf(manager).setOutputDevices(Collections.emptyList());
        assertFalse(RecordingSyncProfiles.save(context, first, 300));
        RecordingSyncProfiles.Profile disconnected = RecordingSyncProfiles.current(context);
        assertFalse(disconnected.confirmed); assertNotEquals(first.key, disconnected.key);
        AudioRouting.chooseOutput(context, 0);
        RecordingSyncProfiles.Profile unknownMode0 = RecordingSyncProfiles.current(context);
        AudioRouting.prefs(context).edit().putInt("mode", 1).commit();
        assertNotEquals(unknownMode0.key, RecordingSyncProfiles.current(context).key);
    }

    @Test public void recordingSnapshotKeepsOffsetWhileNextReadUsesNextRecordingAdjustment() {
        RecordingSyncProfiles.Profile recording = select(11, 21);
        assertTrue(RecordingSyncProfiles.save(context, recording, 80));
        RecordingSyncProfiles.Profile started = RecordingSyncProfiles.current(context);
        assertTrue(RecordingSyncProfiles.save(context, started, -15));
        assertEquals("Retained take snapshot never changes", 80, started.offsetMs);
        assertEquals("Next recording sees latest manual adjustment", -15, RecordingSyncProfiles.current(context).offsetMs);
    }
}
