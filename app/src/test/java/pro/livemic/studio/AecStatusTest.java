package pro.livemic.studio;

import android.content.Context;
import android.content.Intent;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.AudioEffect;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.LooperMode;
import org.robolectric.annotation.RealObject;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35, instrumentedPackages={"pro.livemic.studio"}, shadows={
        AecStatusTest.Audio.class, AecStatusTest.Echo.class, AecStatusTest.Effect.class,
        StudioScreenshotTest.Composer.class, StudioScreenshotTest.Harmony.class})
@LooperMode(LooperMode.Mode.PAUSED)
public class AecStatusTest {
    private Context context;
    private AudioRouting routing;
    private ServiceController<AudioService> controller;

    @Implements(value=NativeAudio.class, isInAndroidSdk=false)
    public static class Audio extends StudioScreenshotTest.Audio {
        static int session;
        @Implementation protected static int inputSession() { return session; }
        @Implementation protected static void microphoneOff() { session = 0; }
    }

    @Implements(AcousticEchoCanceler.class)
    public static class Echo extends Effect {
        static boolean available, nullCreate;
        static int creates, lastSession;
        @Implementation protected void __constructor__(int session) { }
        @Implementation protected static boolean isAvailable() { return available; }
        @Implementation protected static AcousticEchoCanceler create(int session) {
            creates++;
            lastSession = session;
            return nullCreate ? null : ReflectionHelpers.callConstructor(
                    AcousticEchoCanceler.class, ClassParameter.from(int.class, session));
        }
    }

    @Implements(AudioEffect.class)
    public static class Effect {
        static boolean enabled, control, readThrows, enableThrows;
        static int enableResult, releases;
        @RealObject AudioEffect effect;
        @Implementation protected boolean hasControl() { return control; }
        @Implementation protected int setEnabled(boolean value) {
            if (enableThrows) throw new IllegalStateException("effect service unavailable");
            if (enableResult == AudioEffect.SUCCESS) enabled = value;
            return enableResult;
        }
        @Implementation protected boolean getEnabled() {
            if (readThrows) throw new IllegalStateException("effect service unavailable");
            return enabled;
        }
        @Implementation protected void release() {
            if (effect instanceof AcousticEchoCanceler) releases++;
        }
    }

    @Before public void setup() throws Exception {
        context = RuntimeEnvironment.getApplication();
        AudioRouting.prefs(context).edit().clear().commit();
        context.getSharedPreferences("MainActivity", 0).edit().clear().commit();
        Echo.available = true; Echo.nullCreate = false; Echo.creates = Echo.lastSession = 0;
        Effect.enabled = Effect.readThrows = Effect.enableThrows = false;
        Effect.control = true; Effect.enableResult = AudioEffect.SUCCESS; Effect.releases = 0;
        Audio.session = 73;
        AudioService.active = AudioService.playbackActive = false;
        AudioService.composerRequested = AudioService.harmonyRequested = false;
        StudioScreenshotTest.Composer.current = new double[]{0,0,0,0,32,-1,-1,0,0,0};
        StudioScreenshotTest.Harmony.current = new double[]{0,0,0,0,-1,-1,0,0,-1,-1,-1,0,0,-1};
        StudioScreenshotTest.Audio.rerouteResults.clear();
        ExitCoordinator.resetForLaunch();
        routing = new AudioRouting(context, 0);
    }

    @After public void cleanup() {
        if (controller != null) controller.destroy();
        routing.close();
        Audio.session = 0;
        AudioService.active = AudioService.playbackActive = false;
        StudioScreenshotTest.Audio.supplied = null;
        ExitCoordinator.resetForLaunch();
    }

    @Test public void activeRequiresAnEnabledControlledEffectOnCurrentSession() {
        assertEquals(AudioRouting.EchoStatus.UNAVAILABLE, routing.echoStatus());
        routing.attachEffects();
        assertEquals(73, Echo.lastSession);
        assertEquals(AudioRouting.EchoStatus.ACTIVE, routing.echoStatus());
        assertTrue(routing.echoEnabled());
        Effect.control = false;
        assertEquals(AudioRouting.EchoStatus.ERROR, routing.echoStatus());
        Effect.control = true;
        Audio.session = 74;
        assertEquals(AudioRouting.EchoStatus.UNAVAILABLE, routing.echoStatus());
        Audio.session = 0;
        assertFalse(routing.echoEnabled());
    }

    @Test public void unsupportedOrInvalidSessionNeverReportsPreferredAsActive() {
        Echo.available = false;
        routing.attachEffects();
        assertEquals(AudioRouting.EchoStatus.UNAVAILABLE, routing.echoStatus());
        assertFalse(routing.echoEnabled());
        Echo.available = true; Audio.session = 0;
        routing.attachEffects();
        assertEquals(0, Echo.creates);
        assertEquals(AudioRouting.EchoStatus.UNAVAILABLE, routing.echoStatus());
    }

    @Test public void failedCreationOrEnableIsReportedAsError() {
        Echo.nullCreate = true;
        routing.attachEffects();
        assertEquals(AudioRouting.EchoStatus.ERROR, routing.echoStatus());
        Echo.nullCreate = false; Effect.enableResult = AudioEffect.ERROR_INVALID_OPERATION;
        routing.attachEffects();
        assertEquals(AudioRouting.EchoStatus.ERROR, routing.echoStatus());
        assertFalse(routing.echoEnabled());
        Effect.enableResult = AudioEffect.SUCCESS; Effect.enableThrows = true;
        routing.attachEffects();
        assertEquals(AudioRouting.EchoStatus.ERROR, routing.echoStatus());
    }

    @Test public void stateReadFailureIsContainedAndSyncCanRecover() {
        routing.attachEffects();
        Effect.readThrows = true;
        assertEquals(AudioRouting.EchoStatus.ERROR, routing.echoStatus());
        assertFalse(routing.echoEnabled());
        Effect.readThrows = false;
        routing.syncNoiseReduction();
        assertTrue(routing.echoEnabled());
        Effect.enabled = false;
        assertEquals(AudioRouting.EchoStatus.ERROR, routing.echoStatus());
    }

    @Test public void preferenceSyncDisablesAndReenablesExistingEffect() {
        routing.attachEffects();
        AudioRouting.setEchoCancellationPreferred(context, false);
        routing.syncNoiseReduction();
        assertFalse(Effect.enabled);
        assertEquals(AudioRouting.EchoStatus.OFF, routing.echoStatus());
        AudioRouting.setEchoCancellationPreferred(context, true);
        routing.syncNoiseReduction();
        assertTrue(routing.echoEnabled());
        assertFalse(AudioRouting.autoGainPreferred(context));
    }

    @Test public void reattachmentAndCloseReleaseOldEffect() {
        routing.attachEffects();
        Audio.session = 74;
        routing.attachEffects();
        assertEquals(1, Effect.releases);
        assertEquals(74, Echo.lastSession);
        routing.close();
        assertEquals(2, Effect.releases);
        assertEquals(AudioRouting.EchoStatus.UNAVAILABLE, routing.echoStatus());
        routing.close();
        assertEquals(2, Effect.releases);
    }

    @Test public void serviceRefreshesActualStatusOnEffectsRouteAndMicStop() {
        StudioScreenshotTest.Audio.supplied = new float[22];
        StudioScreenshotTest.Audio.supplied[10] = 1;
        controller = Robolectric.buildService(AudioService.class).create();
        AudioService service = controller.get();
        action(service, "start mic");
        assertEquals(AudioRouting.EchoStatus.ACTIVE, AudioService.echoStatus);
        AudioRouting.setEchoCancellationPreferred(context, false);
        action(service, AudioService.EFFECTS);
        assertEquals(AudioRouting.EchoStatus.OFF, AudioService.echoStatus);
        assertFalse(AudioService.echoCancellation);
        AudioRouting.setEchoCancellationPreferred(context, true);
        action(service, AudioService.EFFECTS);
        assertTrue(AudioService.echoCancellation);
        Audio.session = 74;
        action(service, AudioService.ROUTE);
        assertEquals(74, Echo.lastSession);
        assertEquals(AudioRouting.EchoStatus.ACTIVE, AudioService.echoStatus);
        action(service, AudioService.STOP);
        assertFalse(AudioService.echoCancellation);
        assertEquals(AudioRouting.EchoStatus.UNAVAILABLE, AudioService.echoStatus);
        action(service, AudioService.EFFECTS);
        assertFalse(AudioService.echoCancellation);
    }

    private void action(AudioService service, String action) {
        service.onStartCommand(new Intent(service, AudioService.class).setAction(action), 0, 1);
    }
}
