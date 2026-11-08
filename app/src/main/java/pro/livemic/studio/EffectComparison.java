package pro.livemic.studio;

/** Two in-memory listening snapshots. Transport and microphone monitoring are never restored. */
final class EffectComparison {
    static final int[] KEYS = {2,3,4,5,6,7,8,9,10,18};
    static final class Snapshot {
        final float[] values;
        final int preset;
        Snapshot(MainActivity a) {
            values = new float[KEYS.length];
            for (int i = 0; i < KEYS.length; i++) values[i] = a.values[KEYS[i]];
            preset = a.selectedPreset;
        }
        void apply(MainActivity a) {
            for (int i = 0; i < KEYS.length; i++) a.set(KEYS[i], values[i]);
            a.selectedPreset = preset;
            a.getPreferences(0).edit().putInt("preset", preset).apply();
            if (a.presetChip != null) a.presetChip.setText(a.presetName() + " ▾");
        }
    }
    private final Snapshot original;
    private Snapshot a, b;
    String listening = "";
    EffectComparison(MainActivity activity) { original = a = new Snapshot(activity); }
    void save(MainActivity activity, boolean slotB) {
        if (slotB) b = new Snapshot(activity); else a = new Snapshot(activity);
    }
    boolean hasB() { return b != null; }
    void listen(MainActivity activity, boolean slotB) {
        Snapshot target = slotB ? b : a;
        if (target == null) return;
        target.apply(activity);
        listening = slotB ? "B" : "A";
    }
    void cancel(MainActivity activity) { original.apply(activity); }
}
