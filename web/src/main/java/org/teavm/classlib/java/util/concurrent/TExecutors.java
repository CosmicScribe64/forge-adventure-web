package org.teavm.classlib.java.util.concurrent;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

public final class TExecutors {
    private static int threadNumber;

    private TExecutors() {
    }

    public static ExecutorService newCachedThreadPool() { return new TGreenThreadExecutor(null); }
    public static ExecutorService newCachedThreadPool(ThreadFactory f) { return new TGreenThreadExecutor(f); }
    public static ExecutorService newFixedThreadPool(int n) { return new TGreenThreadExecutor(null); }
    public static ExecutorService newFixedThreadPool(int n, ThreadFactory f) { return new TGreenThreadExecutor(f); }
    public static ExecutorService newSingleThreadExecutor() { return new TGreenThreadExecutor(null); }
    public static ExecutorService newSingleThreadExecutor(ThreadFactory f) { return new TGreenThreadExecutor(f); }
    public static ExecutorService newWorkStealingPool() { return new TGreenThreadExecutor(null); }
    public static ExecutorService newWorkStealingPool(int parallelism) { return new TGreenThreadExecutor(null); }
    public static ScheduledExecutorService newScheduledThreadPool(int n) { return new TGreenThreadExecutor(null); }
    public static ScheduledExecutorService newScheduledThreadPool(int n, ThreadFactory f) { return new TGreenThreadExecutor(f); }
    public static ScheduledExecutorService newSingleThreadScheduledExecutor() { return new TGreenThreadExecutor(null); }
    public static ScheduledExecutorService newSingleThreadScheduledExecutor(ThreadFactory f) { return new TGreenThreadExecutor(f); }

    public static ThreadFactory defaultThreadFactory() {
        return r -> {
            Thread t = new Thread(r, "pool-thread-" + (++threadNumber));
            t.setDaemon(true);
            return t;
        };
    }

    public static <T> Callable<T> callable(Runnable task, T result) {
        return () -> {
            task.run();
            return result;
        };
    }

    public static Callable<Object> callable(Runnable task) {
        return callable(task, null);
    }
}
