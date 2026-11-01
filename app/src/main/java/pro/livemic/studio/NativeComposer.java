package pro.livemic.studio;
/** Dedicated composer JNI; never uses the vocal effects parameter array. */
final class NativeComposer {
    static { System.loadLibrary("livemic"); }
    static native boolean loadSoundFont(String path);
    static native String[] drumKits();
    static native boolean publish(double[] events,double beats,double bar,double bpm,int key,int scale);
    static native void transport(int command); // 0 stop, 1 play, 2 pause, 3 count-in
    static native void clearSolo();
    static native void track(int channel,float volume,float pan,boolean mute,boolean solo);
    static native void voice(boolean enabled,int program,int mode,int octave,boolean autoRange,boolean snap,int response,int quality,boolean rhythm,boolean mix,float confidence,float gate);
    static native void monitor(boolean enabled);
    static native double[] state(); // beat, playing, part, partStart, partEnd, input, output, level, recordState, overflow
    static native void record(boolean arm); // cancellation leaves project untouched
    static native double[] drain(); // performance events: beat, kind, midi, velocity, bend, expression, degree, quality
}
