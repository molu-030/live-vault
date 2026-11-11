package pro.livemic.studio;

import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.audiofx.AcousticEchoCanceler;
import java.util.ArrayList;
import java.util.List;

/** Supplies startup policy; native code checks the opened output before choosing an input preset. */
final class EchoInputProfile {
    static void configure(Context context) {
        List<Integer> speakers = new ArrayList<>(), microphones = new ArrayList<>();
        for (AudioDeviceInfo device : AudioRouting.listOutputs(context)) {
            if (device.getType() == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) speakers.add(device.getId());
        }
        boolean defaultSafe = true;
        for (AudioDeviceInfo device : AudioRouting.listInputs(context)) {
            if (device.getType() == AudioDeviceInfo.TYPE_BUILTIN_MIC) microphones.add(device.getId());
            else defaultSafe = false;
        }
        boolean supported = false;
        try { supported = AcousticEchoCanceler.isAvailable(); } catch (RuntimeException ignored) { }
        NativeAudio.configureEchoInput(AudioRouting.echoCancellationPreferred(context) && supported,
                ids(speakers), ids(microphones), defaultSafe);
    }
    private static int[] ids(List<Integer> source) {
        int[] result = new int[source.size()];
        for (int i = 0; i < result.length; i++) result[i] = source.get(i);
        return result;
    }
}
