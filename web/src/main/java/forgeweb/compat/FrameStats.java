package forgeweb.compat;

import org.teavm.jso.JSBody;

/**
 * Frame timing: every 5 s logs "[perf] ..." to the console and updates window.forgeStats.
 * fps counts the frames the game rendered, "dropped" counts browser frames skipped because the
 * game was busy (see UiThread.postFrame), and frame ms is the time spent inside the game's render().
 */
public final class FrameStats {
    private static long windowStart;
    private static int frames;
    private static int dropped;
    private static double renderMs;
    private static double worstMs;

    private FrameStats() {
    }

    static void dropped() {
        dropped++;
    }

    static void rendered(double ms) {
        frames++;
        renderMs += ms;
        worstMs = Math.max(worstMs, ms);
        double now = now();
        if (windowStart == 0) {
            windowStart = (long) now;
            return;
        }
        double elapsed = now - windowStart;
        if (elapsed >= 5000) {
            double fps = frames * 1000.0 / elapsed;
            double avg = renderMs / frames;
            System.out.println("[perf] fps " + round(fps) + ", frame " + round(avg) + " ms avg / "
                    + round(worstMs) + " ms worst, dropped " + dropped);
            publish(fps, avg, worstMs, dropped);
            windowStart = (long) now;
            frames = 0;
            dropped = 0;
            renderMs = 0;
            worstMs = 0;
        }
    }

    private static double round(double v) {
        return Math.round(v * 10) / 10.0;
    }

    @JSBody(script = "return performance.now();")
    static native double now();

    @JSBody(params = {"fps", "avg", "worst", "dropped"},
            script = "window.forgeStats = {fps: fps, frameMs: avg, worstMs: worst, dropped: dropped, at: Date.now()};")
    private static native void publish(double fps, double avg, double worst, int dropped);
}
