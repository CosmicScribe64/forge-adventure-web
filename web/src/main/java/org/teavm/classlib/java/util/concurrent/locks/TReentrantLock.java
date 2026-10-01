package org.teavm.classlib.java.util.concurrent.locks;

import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

/**
 * A reentrant lock for TeaVM green threads. Blocking uses a private monitor,
 * so subclasses (e.g. Guava's LocalCache.Segment) can't interfere with it.
 */
public class TReentrantLock implements Lock, java.io.Serializable {
    private final Object monitor = new Object();
    private Thread owner;
    private int holds;

    public TReentrantLock() { }
    public TReentrantLock(boolean fair) { }

    @Override
    public void lock() {
        Thread me = Thread.currentThread();
        if (owner == me) {
            holds++;
            return;
        }
        synchronized (monitor) {
            boolean interrupted = false;
            while (owner != null) {
                try {
                    monitor.wait();
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
            owner = me;
            holds = 1;
            if (interrupted) me.interrupt();
        }
    }

    @Override
    public void lockInterruptibly() throws InterruptedException {
        Thread me = Thread.currentThread();
        if (owner == me) {
            holds++;
            return;
        }
        synchronized (monitor) {
            while (owner != null) {
                monitor.wait();
            }
            owner = me;
            holds = 1;
        }
    }

    @Override
    public boolean tryLock() {
        Thread me = Thread.currentThread();
        if (owner == me) {
            holds++;
            return true;
        }
        if (owner != null) return false;
        owner = me;
        holds = 1;
        return true;
    }

    @Override
    public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
        if (tryLock()) return true;
        long deadline = System.currentTimeMillis() + unit.toMillis(time);
        synchronized (monitor) {
            while (owner != null) {
                long left = deadline - System.currentTimeMillis();
                if (left <= 0) return false;
                monitor.wait(left);
            }
            owner = Thread.currentThread();
            holds = 1;
            return true;
        }
    }

    @Override
    public void unlock() {
        if (owner != Thread.currentThread()) throw new IllegalMonitorStateException();
        if (--holds == 0) {
            owner = null;
            synchronized (monitor) {
                monitor.notifyAll();
            }
        }
    }

    /** Releases every hold (for Condition.await); returns the count to restore. */
    int releaseAll() {
        if (owner != Thread.currentThread()) throw new IllegalMonitorStateException();
        int saved = holds;
        holds = 1;
        unlock();
        return saved;
    }

    void reacquire(int saved) {
        lock();
        holds = saved;
    }

    public boolean isHeldByCurrentThread() { return owner == Thread.currentThread(); }
    public int getHoldCount() { return owner == Thread.currentThread() ? holds : 0; }
    public boolean isLocked() { return owner != null; }
    public final boolean isFair() { return false; }
    protected Thread getOwner() { return owner; }
    public final boolean hasQueuedThreads() { return false; }

    @Override
    public Condition newCondition() {
        return new Cond(this);
    }

    @Override
    public String toString() {
        return super.toString() + (owner == null ? "[Unlocked]" : "[Locked by thread " + owner.getName() + "]");
    }

    static final class Cond implements Condition {
        private final TReentrantLock lock;
        private final Object signal = new Object();

        Cond(TReentrantLock lock) {
            this.lock = lock;
        }

        @Override
        public void await() throws InterruptedException {
            int saved;
            synchronized (signal) {
                saved = lock.releaseAll();
                try {
                    signal.wait();
                } finally {
                    // reacquired below, outside the signal monitor
                }
            }
            lock.reacquire(saved);
        }

        @Override
        public void awaitUninterruptibly() {
            try {
                await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        @Override
        public boolean await(long time, TimeUnit unit) throws InterruptedException {
            return awaitMillis(unit.toMillis(time));
        }

        @Override
        public long awaitNanos(long nanos) throws InterruptedException {
            long start = System.nanoTime();
            awaitMillis(Math.max(1, nanos / 1_000_000));
            return nanos - (System.nanoTime() - start);
        }

        @Override
        public boolean awaitUntil(Date deadline) throws InterruptedException {
            return awaitMillis(deadline.getTime() - System.currentTimeMillis());
        }

        private boolean awaitMillis(long ms) throws InterruptedException {
            if (ms <= 0) return false;
            long start = System.currentTimeMillis();
            int saved;
            synchronized (signal) {
                saved = lock.releaseAll();
                signal.wait(ms);
            }
            lock.reacquire(saved);
            return System.currentTimeMillis() - start < ms;
        }

        @Override
        public void signal() {
            synchronized (signal) {
                signal.notify();
            }
        }

        @Override
        public void signalAll() {
            synchronized (signal) {
                signal.notifyAll();
            }
        }
    }
}
