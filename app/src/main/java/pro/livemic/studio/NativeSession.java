package pro.livemic.studio;
/** Native session: canonical timeline frames are always 48000 Hz. Blocking calls run on session worker. */
final class NativeSession {
 static { System.loadLibrary("livemic"); }
 // clipData stride 7: track,start,sourceOffsetCanonical,length,loop,loopLengthCanonical,sourceRate.
 static native boolean prepare(String[] paths,long[] clipData,double[] gains,long durationFrames,double bpm);
 static native boolean transport(int command);
 static native boolean seek(long frame);
 static native void setEnabled(boolean enabled);
 static native void recordBus(boolean enabled);
 static native boolean armCapture(long token);
 static native void finishCapture();
 // enabled,mode,frame,duration,48000,revision,underruns,token,anchor(-1 pending),captureFrames,outputRate
 static native long[] state();
 private NativeSession() {}
}
