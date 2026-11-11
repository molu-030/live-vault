package pro.livemic.studio;

/** Process-wide admission gate; generation invalidates callbacks from a prior launch. */
public final class ExitCoordinator {
    private static volatile boolean requested;
    private static volatile long generation;
    private static volatile boolean completed;
    private ExitCoordinator() {}
    public static boolean isExitRequested() { return requested; }
    public static long generation() { return generation; }
    public static synchronized boolean requestExit() {
        if (requested) return false;
        requested = true;
        completed = false;
        generation++;
        return true;
    }
    public static synchronized boolean beginLaunch() {
        if (requested && !completed) return false;
        resetForLaunch();
        return true;
    }
    public static synchronized void completeExit() { if (requested) completed = true; }
    public static synchronized void resetForLaunch() {
        if (requested) { requested = false; completed = false; generation++; }
    }
}
