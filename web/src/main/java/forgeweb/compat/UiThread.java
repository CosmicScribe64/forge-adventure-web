package forgeweb.compat;

import java.util.ArrayDeque;

/**
 * The one green thread that runs all game code: lifecycle calls, input events and
 * Gdx.app.postRunnable tasks, in the order the browser delivered them.
 *
 * Browser callbacks (animation frames, input events) have no green thread, so any wait, sleep
 * or contended lock there throws "Suspension point reached from non-threading context", and
 * Forge blocks on its UI thread in places (World.generateNew joins CompletableFutures). So the
 * callbacks only queue work here and return. While a task blocks, new frames are dropped
 * (at most one waits in the queue) instead of piling up.
 */
public final class UiThread {
    private static final Object lock = new Object();
    private static final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
    private static Thread thread;
    private static boolean frameQueued;

    private UiThread() {
    }

    public static void start() {
        if (thread != null) return;
        thread = new Thread(UiThread::loop, "Forge UI");
        thread.start();
    }

    public static boolean isUiThread() {
        return thread != null && Thread.currentThread() == thread;
    }

    /** Runs {@code r} on the UI thread after everything queued before it. */
    public static void post(Runnable r) {
        synchronized (lock) {
            tasks.add(r);
            lock.notifyAll();
        }
    }

    /** Like {@link #post}, but dropped if a frame is already waiting (the game is busy). */
    public static void postFrame(Runnable r) {
        synchronized (lock) {
            if (frameQueued) {
                FrameStats.dropped();
                return;
            }
            frameQueued = true;
            tasks.add(() -> {
                synchronized (lock) {
                    frameQueued = false;
                }
                r.run();
            });
            lock.notifyAll();
        }
    }

    private static void loop() {
        while (true) {
            Runnable r;
            synchronized (lock) {
                while (tasks.isEmpty()) {
                    try {
                        lock.wait();
                    } catch (InterruptedException e) {
                        return;
                    }
                }
                r = tasks.poll();
            }
            try {
                r.run();
            } catch (Throwable t) {
                // Keep the game loop alive; webtest greps for "Fatal Error".
                System.err.println("Fatal Error on the Forge UI thread: " + t);
                t.printStackTrace();
            }
        }
    }
}
