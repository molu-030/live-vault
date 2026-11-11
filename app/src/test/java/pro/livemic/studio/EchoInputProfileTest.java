package pro.livemic.studio;

import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.audiofx.AcousticEchoCanceler;
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
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,instrumentedPackages={"pro.livemic.studio"},shadows={StudioScreenshotTest.Audio.class,EchoInputProfileTest.Aec.class})
public class EchoInputProfileTest {
    @Implements(AcousticEchoCanceler.class)
    public static class Aec {
        static boolean available;
        @Implementation protected static boolean isAvailable(){return available;}
    }
    private static AudioDeviceInfo inputDevice(int id, int type) {
        AudioDeviceInfo device = FeedbackRoutingTest.device(id, type);
        // Robolectric's builder uses EXT_TO_INT_DEVICE_MAPPING (outputs only).
        // Give the fixture a real input port type and source role before testing policy.
        android.util.SparseIntArray inputTypes = org.robolectric.util.ReflectionHelpers.getStaticField(
                AudioDeviceInfo.class, "EXT_TO_INT_INPUT_DEVICE_MAPPING");
        Object port = org.robolectric.util.ReflectionHelpers.getField(device, "mPort");
        org.robolectric.util.ReflectionHelpers.setField(port, "mType", inputTypes.get(type, 0));
        org.robolectric.util.ReflectionHelpers.setField(port, "mRole", 1); // AudioPort.ROLE_SOURCE
        assertEquals("fixture input type", type, device.getType());
        assertEquals("fixture input id", id, device.getId());
        assertTrue("fixture source port", device.isSource());
        assertFalse("fixture is not an output", device.isSink());
        return device;
    }
    Context context;
    AudioManager manager;
    @Before public void setup(){
        context=RuntimeEnvironment.getApplication();
        manager=(AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
        AudioRouting.prefs(context).edit().clear().commit();
        Aec.available=true;
        Shadows.shadowOf(manager).setOutputDevices(Arrays.asList(
                FeedbackRoutingTest.device(20,AudioDeviceInfo.TYPE_BUILTIN_SPEAKER),
                FeedbackRoutingTest.device(21,AudioDeviceInfo.TYPE_WIRED_HEADPHONES),
                FeedbackRoutingTest.device(22,AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)));
        Shadows.shadowOf(manager).setInputDevices(Collections.singletonList(
                inputDevice(10,AudioDeviceInfo.TYPE_BUILTIN_MIC)));
    }
    @Test public void onlyBuiltInIdsAreProvidedAndVirtualDevicesDoNotBlockSafeDefault(){
        Shadows.shadowOf(manager).setInputDevices(Arrays.asList(
                inputDevice(10,AudioDeviceInfo.TYPE_BUILTIN_MIC),
                inputDevice(90,AudioDeviceInfo.TYPE_TELEPHONY),
                inputDevice(91,AudioDeviceInfo.TYPE_REMOTE_SUBMIX)));
        EchoInputProfile.configure(context);
        assertTrue(StudioScreenshotTest.Audio.echoInputSupported);
        assertTrue(StudioScreenshotTest.Audio.echoDefaultSafe);
        assertArrayEquals(new int[]{20},StudioScreenshotTest.Audio.echoSpeakers);
        assertArrayEquals(new int[]{10},StudioScreenshotTest.Audio.echoMicrophones);
    }
    @Test public void externalAndUnknownInputsPreventAssumingTheDefaultMicrophone(){
        for(int type:new int[]{AudioDeviceInfo.TYPE_USB_DEVICE,AudioDeviceInfo.TYPE_WIRED_HEADSET,AudioDeviceInfo.TYPE_BLUETOOTH_SCO,AudioDeviceInfo.TYPE_UNKNOWN}){
            Shadows.shadowOf(manager).setInputDevices(Arrays.asList(
                    inputDevice(10,AudioDeviceInfo.TYPE_BUILTIN_MIC),inputDevice(99,type)));
            EchoInputProfile.configure(context);
            assertFalse("ambiguous input type "+type,StudioScreenshotTest.Audio.echoDefaultSafe);
            assertArrayEquals(new int[]{10},StudioScreenshotTest.Audio.echoMicrophones);
        }
    }
    @Test public void preferenceAndPlatformSupportBothGateTheProfile(){
        AudioRouting.setEchoCancellationPreferred(context,false);
        EchoInputProfile.configure(context);
        assertFalse(StudioScreenshotTest.Audio.echoInputSupported);
        AudioRouting.setEchoCancellationPreferred(context,true);Aec.available=false;
        EchoInputProfile.configure(context);
        assertFalse(StudioScreenshotTest.Audio.echoInputSupported);
    }
}
