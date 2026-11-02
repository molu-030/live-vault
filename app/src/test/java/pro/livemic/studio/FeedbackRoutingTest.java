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
import org.robolectric.shadows.AudioDeviceInfoBuilder;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,instrumentedPackages={"pro.livemic.studio"},shadows={StudioScreenshotTest.Audio.class})
public class FeedbackRoutingTest {
    Context context;
    AudioManager manager;
    @Before public void setup(){
        context=RuntimeEnvironment.getApplication();
        manager=(AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
        AudioRouting.prefs(context).edit().clear().commit();
        StudioScreenshotTest.Audio.actualOutputDevice=0;
        StudioScreenshotTest.Audio.feedbackProtectionCalls.clear();
    }
    static AudioDeviceInfo device(int id,int type){
        AudioDeviceInfo result=AudioDeviceInfoBuilder.newBuilder().setType(type).build();
        Object port=org.robolectric.util.ReflectionHelpers.getField(result,"mPort");
        Object handle=org.robolectric.util.ReflectionHelpers.getField(port,"mHandle");
        org.robolectric.util.ReflectionHelpers.setField(handle,"mId",id);
        return result;
    }
    void expectRisk(boolean risk){
        StudioScreenshotTest.Audio.feedbackProtectionCalls.clear();
        AudioRouting.refreshFeedbackProtection(context);
        assertEquals(Collections.singletonList(risk),StudioScreenshotTest.Audio.feedbackProtectionCalls);
    }
    @Test public void onlyKnownPersonalListeningDeviceTypesBypassProtection(){
        for(int type:new int[]{AudioDeviceInfo.TYPE_WIRED_HEADPHONES,AudioDeviceInfo.TYPE_WIRED_HEADSET,AudioDeviceInfo.TYPE_USB_HEADSET,AudioDeviceInfo.TYPE_BLUETOOTH_SCO,AudioDeviceInfo.TYPE_BLE_HEADSET,AudioDeviceInfo.TYPE_HEARING_AID})
            assertFalse("safe type "+type,AudioRouting.feedbackRiskForType(type));
        for(int type:new int[]{AudioDeviceInfo.TYPE_UNKNOWN,AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,AudioDeviceInfo.TYPE_BUILTIN_EARPIECE,AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,AudioDeviceInfo.TYPE_BLE_SPEAKER,AudioDeviceInfo.TYPE_USB_DEVICE,AudioDeviceInfo.TYPE_USB_ACCESSORY,AudioDeviceInfo.TYPE_HDMI,99999})
            assertTrue("risky or ambiguous type "+type,AudioRouting.feedbackRiskForType(type));
    }
    @Test public void connectedHeadphonesDoNotOverrideActualSpeaker(){
        Shadows.shadowOf(manager).setOutputDevices(Arrays.asList(device(41,AudioDeviceInfo.TYPE_BUILTIN_SPEAKER),device(42,AudioDeviceInfo.TYPE_WIRED_HEADPHONES)));
        AudioRouting.chooseOutput(context,42); // Requested route can lag the stream's actual route.
        StudioScreenshotTest.Audio.actualOutputDevice=41;expectRisk(true);
        StudioScreenshotTest.Audio.actualOutputDevice=42;expectRisk(false);
    }
    @Test public void defaultRouteUsesActualOutputAndProtectsUnknownOrRemovedRoutes(){
        Shadows.shadowOf(manager).setOutputDevices(Collections.singletonList(device(42,AudioDeviceInfo.TYPE_WIRED_HEADPHONES)));
        assertEquals(0,AudioRouting.selectedOutput(context));
        StudioScreenshotTest.Audio.actualOutputDevice=42;expectRisk(false);
        StudioScreenshotTest.Audio.actualOutputDevice=0;expectRisk(true);
        StudioScreenshotTest.Audio.actualOutputDevice=999;expectRisk(true);
        StudioScreenshotTest.Audio.actualOutputDevice=42;
        Shadows.shadowOf(manager).setOutputDevices(Collections.emptyList());expectRisk(true);
    }
}
