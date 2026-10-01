package forgeweb.compat;

import org.teavm.jso.JSBody;

/**
 * Shows progress of long work on the page (window.forgeProgress in web/html/index.html): Forge's
 * startup (the loading screen's bar) and world generation (a small bar over the game).
 *
 * Forge reports startup progress to its splash screen (CardStorageReader.ProgressObserver,
 * FProgressBar); CallRedirector routes those calls through here as well. Loading runs on a green
 * thread that never waits, so nothing on the page could repaint until it finished; each report
 * also pauses that thread briefly (at most every 100 ms) so the page and the splash can draw.
 * The same goes for work handed to the UI thread from other threads (see invokeInEdtLater).
 */
public final class Progress {
    private static String label = "Starting Forge…";
    private static float fraction;
    private static long lastYield;

    private Progress() {
    }

    // --- CallRedirector targets (receiver first) ---

    public static void setOperationName(forge.CardStorageReader.ProgressObserver observer, String name,
                                        boolean usePercents) {
        observer.setOperationName(name, usePercents);
        if (name != null && !name.isEmpty()) label = name;
        mark(name);
        fraction = 0;
        if (LoadingScreen.isShowing()) show(label, 0);
        yieldToPage();
    }

    public static void report(forge.CardStorageReader.ProgressObserver observer, int current, int total) {
        observer.report(current, total);
        fraction = total > 0 ? (float) current / total : 0;
        if (LoadingScreen.isShowing()) show(label, fraction);
        yieldToPage();
    }

    public static void setDescription(forge.toolbox.FProgressBar bar, String description) {
        bar.setDescription(description);
        mark(description);
        if (description != null && !description.isEmpty() && LoadingScreen.isShowing()) {
            label = description;
            show(label, fraction);
        }
    }

    /**
     * FThreads.invokeInEdtLater / invokeInEdtNowOrLater from another green thread: on desktop the
     * UI thread runs such work while the caller continues; here it only runs once the caller
     * waits. Loops that hand work to the UI thread (FSkinFont.preloadAll disposing each font
     * generator there) otherwise pile it all up, so let the UI thread catch up now and then.
     *
     * The pause comes BEFORE posting, never after: callers like WaitCallback.invokeAndWait post
     * and then wait for the task's notify(); a suspension in between would let a task that
     * finishes at once notify before anyone waits, and the caller would wait forever.
     */
    public static void invokeInEdtLater(Runnable proc) {
        if (!UiThread.isUiThread()) yieldToPage();
        forge.gui.FThreads.invokeInEdtLater(proc);
    }

    public static void invokeInEdtNowOrLater(Runnable proc) {
        if (!UiThread.isUiThread()) yieldToPage();
        forge.gui.FThreads.invokeInEdtNowOrLater(proc);
    }

    // --- direct use ---

    /** Shows {@code text} with {@code fraction} (0..1) done; null text hides the in-game bar. */
    public static void show(String text, float fraction) {
        showImpl(text, fraction);
    }

    public static void hide() {
        showImpl(null, 0);
    }

    /** Lets the page paint if the current green thread has run for 100 ms without waiting. */
    public static void yieldToPage() {
        long now = System.currentTimeMillis();
        if (now - lastYield < 100 || !MainThread.canSuspend()) return;
        lastYield = now;
        try {
            Thread.sleep(1); // a real suspension: this class isn't rewritten by CallRedirector
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Notes that a startup stage begins now (the page's time-to-gameplay report). */
    @JSBody(params = "stage", script = "if (window.forgeMark) window.forgeMark(stage);")
    public static native void mark(String stage);

    /** Logs how long each stage took, up to now ("[ttg] ..." in the console). */
    @JSBody(params = "until", script = "if (window.forgeTimingReport) window.forgeTimingReport(until);")
    public static native void timingReport(String until);

    @JSBody(params = {"text", "fraction"}, script = "if (window.forgeProgress) window.forgeProgress(text, fraction);")
    private static native void showImpl(String text, float fraction);
}
