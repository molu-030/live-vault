package pro.livemic.studio;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.AutomaticGainControl;
import android.media.audiofx.NoiseSuppressor;
import android.os.Build;
import java.util.ArrayList;
import java.util.List;

/** Resolves independently selected input and output devices and manages route effects. */
public final class AudioRouting implements AutoCloseable {
    public final int mode, input, output;
    public final String label;
    private final Context context;
    private final AudioManager manager;
    private int previousMode;
    private final int savedInput, savedOutput, savedMode, savedBluetooth;
    private boolean changedMode = false, communicationDevice = false;
    private AcousticEchoCanceler echo;
    private NoiseSuppressor noise;
    private AutomaticGainControl automaticGain;

    public static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("routing", 0);
    }

    public static boolean echoCancellationPreferred(Context c) {
        return prefs(c).getBoolean("echoCancellation", true);
    }

    public static void setEchoCancellationPreferred(Context c, boolean enabled) {
        prefs(c).edit().putBoolean("echoCancellation", enabled).apply();
    }

    public static boolean autoGainPreferred(Context c) {
        return prefs(c).getBoolean("autoGain", false);
    }

    public static void setAutoGainPreferred(Context c, boolean enabled) {
        prefs(c).edit().putBoolean("autoGain", enabled).apply();
    }

    public static int selected(Context c) {
        int id = selectedOutput(c);
        if (id != 0) {
            for (AudioDeviceInfo d : listOutputs(c)) {
                if (d.getId() == id) {
                    int t = d.getType();
                    return t == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                            ? 1
                            : (t == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || t == AudioDeviceInfo.TYPE_BLE_SPEAKER ? 2 : 0);
                }
            }
            return 0;
        }
        return prefs(c).getInt("mode", 0);
    }

    // A connected device is not necessarily the stream's current output. Only
    // positively identified personal listening devices bypass feedback control.
    static boolean feedbackRiskForType(int type) {
        return type != AudioDeviceInfo.TYPE_WIRED_HEADPHONES
                && type != AudioDeviceInfo.TYPE_WIRED_HEADSET
                && type != AudioDeviceInfo.TYPE_USB_HEADSET
                && type != AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                && type != AudioDeviceInfo.TYPE_BLE_HEADSET
                && type != AudioDeviceInfo.TYPE_HEARING_AID;
    }

    public static void refreshFeedbackProtection(Context c) {
        int actual = NativeAudio.outputDevice();
        boolean risky = true;
        if (actual > 0) {
            for (AudioDeviceInfo device : listOutputs(c)) {
                if (device.getId() == actual) {
                    risky = feedbackRiskForType(device.getType());
                    break;
                }
            }
        }
        NativeAudio.feedbackProtection(risky);
    }

    public static int selectedInput(Context c) {
        return prefs(c).getInt("inputDevice", 0);
    }

    public static int selectedOutput(Context c) {
        return prefs(c).getInt("outputDevice", 0);
    }

    public static void chooseInput(Context c, int id) {
        prefs(c).edit().putInt("inputDevice", id).apply();
    }

    public static void chooseOutput(Context c, int id) {
        prefs(c).edit().putInt("outputDevice", id).putInt("mode", 0).apply();
    }

    private static boolean isRoutableDevice(AudioDeviceInfo d) {
        int t = d.getType();
        return t != AudioDeviceInfo.TYPE_TELEPHONY
                && t != AudioDeviceInfo.TYPE_FM
                && t != AudioDeviceInfo.TYPE_FM_TUNER
                && t != AudioDeviceInfo.TYPE_TV_TUNER
                && t != AudioDeviceInfo.TYPE_REMOTE_SUBMIX
                && t != AudioDeviceInfo.TYPE_IP
                && t != AudioDeviceInfo.TYPE_BUS;
    }

    public static List<AudioDeviceInfo> listInputs(Context c) {
        AudioDeviceInfo[] raw = ((AudioManager) c.getSystemService(Context.AUDIO_SERVICE))
                .getDevices(AudioManager.GET_DEVICES_INPUTS);
        List<AudioDeviceInfo> out = new ArrayList<>();
        if (raw != null) {
            for (AudioDeviceInfo d : raw) {
                if (d != null && isRoutableDevice(d)) out.add(d);
            }
        }
        return out;
    }

    public static List<AudioDeviceInfo> listOutputs(Context c) {
        AudioDeviceInfo[] raw = ((AudioManager) c.getSystemService(Context.AUDIO_SERVICE))
                .getDevices(AudioManager.GET_DEVICES_OUTPUTS);
        List<AudioDeviceInfo> out = new ArrayList<>();
        if (raw != null) {
            for (AudioDeviceInfo d : raw) {
                if (d != null && isRoutableDevice(d)) out.add(d);
            }
        }
        return out;
    }

    public static String deviceLabel(Context c, AudioDeviceInfo d) {
        String type;
        switch (d.getType()) {
            case AudioDeviceInfo.TYPE_BUILTIN_MIC:
                type = AppLanguage.text(c, "내장 마이크", "Built-in microphone");
                break;
            case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER:
                type = AppLanguage.text(c, "휴대폰 스피커", "Phone speaker");
                break;
            case AudioDeviceInfo.TYPE_BUILTIN_EARPIECE:
                type = AppLanguage.text(c, "휴대폰 수화기", "Phone earpiece");
                break;
            case AudioDeviceInfo.TYPE_WIRED_HEADSET:
                type = AppLanguage.text(c, "유선 헤드셋", "Wired headset");
                break;
            case AudioDeviceInfo.TYPE_WIRED_HEADPHONES:
                type = AppLanguage.text(c, "유선 헤드폰", "Wired headphones");
                break;
            case AudioDeviceInfo.TYPE_USB_HEADSET:
                type = AppLanguage.text(c, "USB 헤드셋", "USB headset");
                break;
            case AudioDeviceInfo.TYPE_USB_DEVICE:
            case AudioDeviceInfo.TYPE_USB_ACCESSORY:
                type = AppLanguage.text(c, "USB 오디오", "USB audio");
                break;
            case AudioDeviceInfo.TYPE_BLUETOOTH_SCO:
            case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP:
            case AudioDeviceInfo.TYPE_BLE_HEADSET:
            case AudioDeviceInfo.TYPE_BLE_SPEAKER:
                type = AppLanguage.text(c, "블루투스 오디오", "Bluetooth audio");
                break;
            default:
                type = AppLanguage.text(c, "오디오 장치", "Audio device");
        }
        CharSequence product = d.getProductName();
        String name = product == null ? "" : product.toString().trim();
        return type + (name.isEmpty() || name.equalsIgnoreCase(type) ? "" : " · " + name) + " #" + d.getId();
    }

    private static String selectedLabel(Context c, int id, List<AudioDeviceInfo> devices) {
        if (id == 0) return AppLanguage.text(c, "시스템 기본값", "System default");
        for (AudioDeviceInfo d : devices) {
            if (d.getId() == id) return deviceLabel(c, d);
        }
        return AppLanguage.text(c, "연결되지 않은 장치", "Disconnected device") + " #" + id;
    }

    public static String inputLabel(Context c) {
        return selectedLabel(c, selectedInput(c), listInputs(c));
    }

    public static String outputLabel(Context c) {
        int id = selectedOutput(c);
        if (id != 0) return selectedLabel(c, id, listOutputs(c));
        int mode = selected(c);
        if (mode == 1) return AppLanguage.text(c, "휴대폰 스피커", "Phone speaker");
        if (mode == 2) return AppLanguage.text(c, "블루투스 스피커", "Bluetooth speaker");
        return selectedLabel(c, 0, listOutputs(c));
    }

    public static List<AudioDeviceInfo> bluetooth(Context c) {
        List<AudioDeviceInfo> list = new ArrayList<>();
        for (AudioDeviceInfo d : listOutputs(c)) {
            int type = d.getType();
            if (type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || type == AudioDeviceInfo.TYPE_BLE_SPEAKER) {
                list.add(d);
            }
        }
        return list;
    }

    public AudioRouting(Context c, int requestedInput) throws Exception {
        this(c, requestedInput, true);
    }

    public AudioRouting(Context c, int requestedInput, boolean withMic) throws Exception {
        context = c.getApplicationContext();
        manager = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        previousMode = manager.getMode();
        savedInput = selectedInput(c);
        savedOutput = selectedOutput(c);
        savedMode = prefs(c).getInt("mode", 0);
        savedBluetooth = prefs(c).getInt("bluetoothDevice", 0);

        int in = selectedInput(c);
        if (in == 0 && requestedInput > 0 && !prefs(c).contains("inputDevice")) {
            in = requestedInput;
        }
        if (withMic && in > 0) {
            boolean found = false;
            for (AudioDeviceInfo d : listInputs(c)) {
                if (d.getId() == in) found = true;
            }
            if (!found) {
                throw new IllegalStateException(AppLanguage.text(c, "선택한 입력 장치가 연결되지 않았습니다.", "The selected input device is disconnected."));
            }
        }

        int out = selectedOutput(c), resolvedMode = selected(c);
        String name = outputLabel(c);
        if (out > 0) {
            AudioDeviceInfo chosen = null;
            for (AudioDeviceInfo d : listOutputs(c)) {
                if (d.getId() == out) chosen = d;
            }
            if (chosen == null) {
                throw new IllegalStateException(AppLanguage.text(c, "선택한 출력 장치가 연결되지 않았습니다.", "The selected output device is disconnected."));
            }
            int type = chosen.getType();
            resolvedMode = type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                    ? 1
                    : (type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || type == AudioDeviceInfo.TYPE_BLE_SPEAKER ? 2 : 0);
            name = deviceLabel(c, chosen);
        } else if (resolvedMode > 0) {
            if (resolvedMode == 1) {
                for (AudioDeviceInfo d : listOutputs(c)) {
                    if (d.getType() == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
                        out = d.getId();
                        break;
                    }
                }
            } else {
                int chosen = prefs(c).getInt("bluetoothDevice", 0);
                for (AudioDeviceInfo d : bluetooth(c)) {
                    if (chosen == 0 || chosen == d.getId()) {
                        out = d.getId();
                        name = deviceLabel(c, d);
                        break;
                    }
                }
            }
            if (out == 0) {
                throw new IllegalStateException(AppLanguage.text(c, "출력 장치를 찾지 못했습니다.", "Could not find the selected output device."));
            }
        }
        if (in == 0 && resolvedMode > 0) {
            for (AudioDeviceInfo d : listInputs(c)) {
                if (d.getType() == AudioDeviceInfo.TYPE_BUILTIN_MIC) {
                    in = d.getId();
                    break;
                }
            }
        }
        mode = resolvedMode;
        input = in;
        output = out;
        label = name;
    }

    public void restoreSelection() {
        prefs(context)
                .edit()
                .putInt("inputDevice", savedInput)
                .putInt("outputDevice", savedOutput)
                .putInt("mode", savedMode)
                .putInt("bluetoothDevice", savedBluetooth)
                .apply();
    }

    public void prepare() throws Exception {
        previousMode = manager.getMode();
        if (mode > 0 && (previousMode == AudioManager.MODE_IN_CALL || previousMode == AudioManager.MODE_IN_COMMUNICATION)) {
            throw new IllegalStateException(AppLanguage.text(context, "통화 중에는 스피커 모드를 시작할 수 없습니다.", "Speaker mode cannot start during a call."));
        }
        if (mode == 1) {
            manager.setMode(AudioManager.MODE_IN_COMMUNICATION);
            changedMode = true;
            if (Build.VERSION.SDK_INT >= 31) {
                AudioDeviceInfo speaker = null;
                try {
                    for (AudioDeviceInfo d : manager.getAvailableCommunicationDevices()) {
                        if (d.getType() == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
                            speaker = d;
                            break;
                        }
                    }
                    if (speaker != null && manager.setCommunicationDevice(speaker)) {
                        communicationDevice = true;
                    } else {
                        manager.setSpeakerphoneOn(true);
                    }
                } catch (Exception ignored) {
                    manager.setSpeakerphoneOn(true);
                }
            } else {
                manager.setSpeakerphoneOn(true);
            }
        }
    }

    public void attachEffects() {
        refreshFeedbackProtection(context);
        int session = NativeAudio.inputSession();
        if (session <= 0 && mode == 0) return;
        if (session > 0) {
            try {
                if (AcousticEchoCanceler.isAvailable()) {
                    echo = AcousticEchoCanceler.create(session);
                    if (echo != null) echo.setEnabled(echoCancellationPreferred(context));
                }
            } catch (Exception ignored) {
            }
            try {
                if (NoiseSuppressor.isAvailable()) {
                    noise = NoiseSuppressor.create(session);
                    if (noise != null) noise.setEnabled(noiseReductionEnabled());
                }
            } catch (Exception ignored) {
            }
            try {
                if (AutomaticGainControl.isAvailable()) {
                    automaticGain = AutomaticGainControl.create(session);
                    if (automaticGain != null) automaticGain.setEnabled(autoGainPreferred(context));
                }
            } catch (Exception ignored) {
            }
        }
    }

    private boolean noiseReductionEnabled() {
        return context.getSharedPreferences("MainActivity", 0).getFloat("p19", 1f) > .5f;
    }

    public void syncNoiseReduction() {
        if (noise != null) {
            try {
                noise.setEnabled(noiseReductionEnabled());
            } catch (Exception ignored) {
            }
        }
        if (echo != null) {
            try {
                echo.setEnabled(echoCancellationPreferred(context));
            } catch (Exception ignored) {
            }
        }
        if (automaticGain != null) {
            try {
                automaticGain.setEnabled(autoGainPreferred(context));
            } catch (Exception ignored) {
            }
        }
    }

    public boolean echoEnabled() {
        return (echo != null && echo.getEnabled()) || echoCancellationPreferred(context);
    }

    @Override
    public void close() {
        if (echo != null) {
            echo.release();
            echo = null;
        }
        if (noise != null) {
            noise.release();
            noise = null;
        }
        if (automaticGain != null) {
            automaticGain.release();
            automaticGain = null;
        }
        if (communicationDevice && Build.VERSION.SDK_INT >= 31) {
            try {
                manager.clearCommunicationDevice();
            } catch (Exception ignored) {
            }
            communicationDevice = false;
        }
        if (changedMode) {
            try {
                manager.setSpeakerphoneOn(false);
            } catch (Exception ignored) {
            }
            if (manager.getMode() == AudioManager.MODE_IN_COMMUNICATION) {
                manager.setMode(previousMode);
            }
            changedMode = false;
        }
    }
}
