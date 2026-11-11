package pro.livemic.studio;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioDeviceInfo;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Locale;

/** Manual recording offsets scoped to a stable input/output pair, never a runtime device ID. */
public final class RecordingSyncProfiles {
    private static final String PREFIX = "syncPair.v1.";
    private RecordingSyncProfiles() {}

    /** Immutable snapshot: retain this instance for the duration of a recording. */
    public static final class Profile {
        public final String key, label;
        public final int offsetMs, mode;
        public final boolean inherited, confirmed, saved;
        private Profile(String key, String label, int offsetMs, int mode, boolean inherited, boolean confirmed, boolean saved) {
            this.key = key; this.label = label; this.offsetMs = offsetMs; this.mode = mode;
            this.inherited = inherited; this.confirmed = confirmed; this.saved = saved;
        }
    }

    public static Profile current(Context context) {
        int input = 0, output = 0;
        try { input = NativeAudio.inputDevice(); } catch (LinkageError ignored) { }
        try { output = NativeAudio.outputDevice(); } catch (LinkageError ignored) { }
        return forRoute(context, input, output);
    }

    /** Positive stream route IDs take precedence; zero never proves a system-default device. */
    public static Profile forRoute(Context context, int actualInput, int actualOutput) {
        return resolve(context, AudioRouting.selected(context), actualInput, actualOutput,
                AudioRouting.selectedInput(context), AudioRouting.selectedOutput(context),
                AudioRouting.listInputs(context), AudioRouting.listOutputs(context));
    }

    static Profile resolve(Context context, int mode, int actualInput, int actualOutput,
            int selectedInput, int selectedOutput, List<AudioDeviceInfo> inputs, List<AudioDeviceInfo> outputs) {
        Endpoint input = endpoint(context, true, actualInput, selectedInput, inputs);
        Endpoint output = endpoint(context, false, actualOutput, selectedOutput, outputs);
        // Keep the output-mode legacy fallback and separate unknown routes by that mode.
        int resolvedMode = mode;
        if (actualOutput > 0 && output.device != null) {
            int type = output.device.getType();
            resolvedMode = type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER ? 1
                    : (type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || type == AudioDeviceInfo.TYPE_BLE_SPEAKER ? 2 : 0);
        }
        String digest = hash("v1|" + resolvedMode + "|in:" + input.identity + "|out:" + output.identity);
        String key = PREFIX + digest;
        SharedPreferences prefs = AudioRouting.prefs(context);
        boolean saved = prefs.contains(key);
        // Legacy values were keyed by the selected output mode, including system default (0).
        // Identifying its actual speaker must not silently switch that legacy value to sync1.
        boolean inherited = !saved && prefs.contains("sync" + mode);
        int offset = clamp(prefs.getInt(key, prefs.getInt("sync" + mode, 0)));
        String label = input.label + " → " + output.label + " · " + digest.substring(0, 8);
        return new Profile(key, label, offset, resolvedMode, inherited, input.device != null && output.device != null, saved);
    }

    /** Reject a stale slider after unplugging or rerouting instead of modifying another pair. */
    public static boolean save(Context context, Profile shown, int offsetMs) {
        if (shown == null || !current(context).key.equals(shown.key)) return false;
        saveResolved(context, shown, offsetMs);
        return true;
    }

    static void saveResolved(Context context, Profile profile, int offsetMs) {
        AudioRouting.prefs(context).edit().putInt(profile.key, clamp(offsetMs)).apply();
    }

    private static int clamp(int value) { return Math.max(-500, Math.min(500, value)); }

    private static final class Endpoint {
        final AudioDeviceInfo device;
        final String identity, label;
        Endpoint(AudioDeviceInfo device, String identity, String label) {
            this.device = device; this.identity = identity; this.label = label;
        }
    }

    private static Endpoint endpoint(Context context, boolean input, int actual, int selected, List<AudioDeviceInfo> devices) {
        int id = actual > 0 ? actual : selected;
        for (AudioDeviceInfo device : devices) {
            if (id > 0 && device.getId() == id) {
                String name = device.getProductName() == null ? "" : device.getProductName().toString().trim();
                String address = "";
                try { address = device.getAddress(); } catch (SecurityException ignored) { }
                if (address == null) address = "";
                // Delimited lengths prevent ambiguity, and only the digest leaves this method.
                String identity = device.getType() + ":" + name.length() + ":" + name + ":" + address.length() + ":" + address;
                return new Endpoint(device, identity, typeName(context, input, device.getType()));
            }
        }
        String status = actual > 0 ? "unlisted-stream" : selected > 0 ? "disconnected-selection" : "system-default";
        String label = AppLanguage.text(context, input ? "입력 미확정" : "출력 미확정", input ? "Input unconfirmed" : "Output unconfirmed");
        return new Endpoint(null, "unknown:" + status, label);
    }

    private static String typeName(Context context, boolean input, int type) {
        switch (type) {
            case AudioDeviceInfo.TYPE_BUILTIN_MIC: return AppLanguage.text(context, "내장 마이크", "Built-in mic");
            case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER: return AppLanguage.text(context, "휴대폰 스피커", "Phone speaker");
            case AudioDeviceInfo.TYPE_BUILTIN_EARPIECE: return AppLanguage.text(context, "수화기", "Earpiece");
            case AudioDeviceInfo.TYPE_USB_DEVICE:
            case AudioDeviceInfo.TYPE_USB_ACCESSORY:
            case AudioDeviceInfo.TYPE_USB_HEADSET: return AppLanguage.text(context, "USB 오디오", "USB audio");
            case AudioDeviceInfo.TYPE_WIRED_HEADSET:
            case AudioDeviceInfo.TYPE_WIRED_HEADPHONES: return AppLanguage.text(context, "유선 오디오", "Wired audio");
            case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP:
            case AudioDeviceInfo.TYPE_BLUETOOTH_SCO:
            case AudioDeviceInfo.TYPE_BLE_HEADSET:
            case AudioDeviceInfo.TYPE_BLE_SPEAKER: return AppLanguage.text(context, "블루투스 오디오", "Bluetooth audio");
            default: return AppLanguage.text(context, input ? "입력 장치" : "출력 장치", input ? "Input device" : "Output device");
        }
    }

    private static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(64);
            for (byte b : digest) out.append(String.format(Locale.US, "%02x", b & 255));
            return out.toString();
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
