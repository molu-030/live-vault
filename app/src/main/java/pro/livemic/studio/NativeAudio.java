package pro.livemic.studio;
public final class NativeAudio {
    static { System.loadLibrary("livemic"); }
    public static native boolean start(int inputDevice,int outputDevice,int monitorMode);
    public static native void stop();
    /** Close streams and analyzers while retaining paused transport positions. */
    public static native void suspend();
    public static native boolean reroute(int inputDevice,int outputDevice,int monitorMode,boolean microphone);
    public static native boolean startPlayback(int outputDevice,int monitorMode);
    public static native void microphoneOff();
    public static native int[] latencyInfo();
    public static native boolean bufferBursts(int bursts);
    public static native void recordSync(int ms);
    public static native void recordMicOnly(boolean enabled);
    public static native void guideTone(float frequency);
    public static native float[] pitchFrame();
    public static native int inputSession();
    public static native int outputDevice();
    public static native void feedbackProtection(boolean riskyOutput);
    public static native void monitorLevel(float level);
    public static native void resetFeedback();
    public static native void set(int key,float value);
    public static native float[] stats();
    public static native float[] roll();
    public static native boolean beginRecord(String path);
    public static native void pauseRecord(boolean pause);
    public static native void endRecord();
    public static native boolean exportMidi(String path,int bpm,boolean recordingOnly);
    public static native void clearNotes();
    public static native double recordClock();
    public static native void loadLocal(float[] pcm,int sampleRate);
    public static native boolean loadLocalFile(String path,int sampleRate,int channels);
    public static native double[] localPlaybackInfo();
    public static native void seekLocal(double seconds);
    public static native void playLocal(boolean play);
    public static native void enablePlaybackCapture(boolean enable,float gain);
    public static native int pushPlayback(float[] stereo,int count);
}
