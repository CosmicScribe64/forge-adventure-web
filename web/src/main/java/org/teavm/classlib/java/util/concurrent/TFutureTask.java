package org.teavm.classlib.java.util.concurrent;

import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class TFutureTask<V> implements RunnableFuture<V> {
    private static final int NEW = 0, RUNNING = 1, DONE = 2, FAILED = 3, CANCELLED = 4;

    private Callable<V> callable;
    private int state = NEW;
    private V result;
    private Throwable failure;
    private Thread runner;

    public TFutureTask(Callable<V> callable) {
        if (callable == null) throw new NullPointerException();
        this.callable = callable;
    }

    public TFutureTask(Runnable runnable, V result) {
        this(() -> {
            runnable.run();
            return result;
        });
    }

    @Override
    public void run() {
        synchronized (this) {
            if (state != NEW) return;
            state = RUNNING;
            runner = Thread.currentThread();
        }
        V value = null;
        Throwable error = null;
        try {
            value = callable.call();
        } catch (Throwable t) {
            error = t;
        }
        synchronized (this) {
            runner = null;
            if (state == RUNNING) {
                if (error != null) {
                    failure = error;
                    state = FAILED;
                } else {
                    result = value;
                    state = DONE;
                }
            }
            notifyAll();
        }
        done();
    }

    protected void done() {
    }

    /** Runs the task without setting a result, so it can run again (periodic tasks). */
    protected boolean runAndReset() {
        synchronized (this) {
            if (state != NEW) return false;
        }
        try {
            callable.call();
            return true;
        } catch (Throwable t) {
            setException(t);
            return false;
        }
    }

    protected void set(V v) {
        synchronized (this) {
            if (state > RUNNING) return;
            result = v;
            state = DONE;
            notifyAll();
        }
        done();
    }

    protected void setException(Throwable t) {
        synchronized (this) {
            if (state > RUNNING) return;
            failure = t;
            state = FAILED;
            notifyAll();
        }
        done();
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        Thread r;
        synchronized (this) {
            if (state > RUNNING) return false;
            state = CANCELLED;
            r = runner;
            notifyAll();
        }
        if (mayInterruptIfRunning && r != null) r.interrupt();
        done();
        return true;
    }

    @Override public synchronized boolean isCancelled() { return state == CANCELLED; }
    @Override public synchronized boolean isDone() { return state > RUNNING; }

    @Override
    public synchronized V get() throws InterruptedException, ExecutionException {
        while (state <= RUNNING) {
            wait();
        }
        return report();
    }

    @Override
    public synchronized V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        long deadline = System.currentTimeMillis() + unit.toMillis(timeout);
        while (state <= RUNNING) {
            long left = deadline - System.currentTimeMillis();
            if (left <= 0) throw new TimeoutException();
            wait(left);
        }
        return report();
    }

    private V report() throws ExecutionException {
        if (state == CANCELLED) throw new CancellationException();
        if (state == FAILED) throw new ExecutionException(failure);
        return result;
    }
}
