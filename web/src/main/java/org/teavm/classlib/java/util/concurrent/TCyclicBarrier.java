package org.teavm.classlib.java.util.concurrent;

public class TCyclicBarrier {
    private final int parties;
    private final Runnable action;
    private int waiting;
    private int generation;

    public TCyclicBarrier(int parties) {
        this(parties, null);
    }

    public TCyclicBarrier(int parties, Runnable action) {
        this.parties = parties;
        this.action = action;
    }

    public synchronized int await() throws InterruptedException {
        int gen = generation;
        int index = parties - 1 - waiting;
        if (++waiting == parties) {
            waiting = 0;
            generation++;
            if (action != null) action.run();
            notifyAll();
            return 0;
        }
        while (gen == generation) {
            wait();
        }
        return index;
    }

    public int getParties() {
        return parties;
    }

    public synchronized int getNumberWaiting() {
        return waiting;
    }

    public synchronized void reset() {
        waiting = 0;
        generation++;
        notifyAll();
    }
}
