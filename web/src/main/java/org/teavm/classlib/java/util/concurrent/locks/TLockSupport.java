package org.teavm.classlib.java.util.concurrent.locks;

import java.util.HashSet;
import java.util.Set;

/** park/unpark on green threads: one shared monitor plus a per-thread permit. */
public final class TLockSupport {
    private static final Object MONITOR = new Object();
    private static final Set<Thread> PERMITS = new HashSet<>();

    private TLockSupport() {
    }

    public static void unpark(Thread thread) {
        if (thread == null) return;
        synchronized (MONITOR) {
            PERMITS.add(thread);
            MONITOR.notifyAll();
        }
    }

    public static void park() {
        parkMillis(0);
    }

    public static void park(Object blocker) {
        parkMillis(0);
    }

    public static void parkNanos(long nanos) {
        if (nanos > 0) parkMillis(Math.max(1, nanos / 1_000_000));
    }

    public static void parkNanos(Object blocker, long nanos) {
        parkNanos(nanos);
    }

    public static void parkUntil(long deadline) {
        long ms = deadline - System.currentTimeMillis();
        if (ms > 0) parkMillis(ms);
    }

    public static void parkUntil(Object blocker, long deadline) {
        parkUntil(deadline);
    }

    public static Object getBlocker(Thread t) {
        return null;
    }

    /** May return early, which park() permits ("spurious wakeup"). */
    private static void parkMillis(long ms) {
        Thread me = Thread.currentThread();
        synchronized (MONITOR) {
            if (PERMITS.remove(me)) return;
            try {
                if (ms > 0) MONITOR.wait(ms);
                else MONITOR.wait();
            } catch (InterruptedException e) {
                me.interrupt();
            }
            PERMITS.remove(me);
        }
    }
}
