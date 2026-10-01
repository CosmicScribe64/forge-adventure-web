package org.teavm.classlib.java.util.concurrent;

import java.util.concurrent.TimeUnit;

/** CountDownLatch on TeaVM green threads (Object.wait/notifyAll). */
public class TCountDownLatch {
    private long count;

    public TCountDownLatch(int count) {
        if (count < 0) throw new IllegalArgumentException("count < 0");
        this.count = count;
    }

    public synchronized void await() throws InterruptedException {
        while (count > 0) {
            wait();
        }
    }

    public synchronized boolean await(long timeout, TimeUnit unit) throws InterruptedException {
        long deadline = System.currentTimeMillis() + unit.toMillis(timeout);
        while (count > 0) {
            long left = deadline - System.currentTimeMillis();
            if (left <= 0) return false;
            wait(left);
        }
        return true;
    }

    public synchronized void countDown() {
        if (count > 0 && --count == 0) {
            notifyAll();
        }
    }

    public synchronized long getCount() {
        return count;
    }

    @Override
    public String toString() {
        return super.toString() + "[Count = " + getCount() + "]";
    }
}
