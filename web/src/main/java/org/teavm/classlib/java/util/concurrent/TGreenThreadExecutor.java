package org.teavm.classlib.java.util.concurrent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Every Executors.* pool on web: each task runs on its own TeaVM green thread.
 * Green threads are cooperative, so pool sizes have no effect on throughput.
 * (Not a JDK class name, so it is ours alone; the T prefix only keeps it beside its peers.)
 */
public class TGreenThreadExecutor implements ScheduledExecutorService {
    private final ThreadFactory factory;
    private boolean shutdown;
    private int running;

    public TGreenThreadExecutor(ThreadFactory factory) {
        this.factory = factory != null ? factory : Thread::new;
    }

    private void launch(Runnable task) {
        synchronized (this) {
            if (shutdown) throw new RejectedExecutionException("Executor has been shut down");
            running++;
        }
        Thread t = factory.newThread(() -> {
            try {
                task.run();
            } finally {
                synchronized (TGreenThreadExecutor.this) {
                    running--;
                    TGreenThreadExecutor.this.notifyAll();
                }
            }
        });
        t.start();
    }

    @Override public void execute(Runnable command) { launch(command); }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        FutureTask<T> f = new FutureTask<>(task);
        launch(f);
        return f;
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        FutureTask<T> f = new FutureTask<>(task, result);
        launch(f);
        return f;
    }

    @Override
    public Future<?> submit(Runnable task) {
        return submit(task, null);
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
        List<Future<T>> out = new ArrayList<>();
        for (Callable<T> c : tasks) out.add(submit(c));
        for (Future<T> f : out) {
            try {
                f.get();
            } catch (ExecutionException ignored) {
                // reported through the future
            }
        }
        return out;
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException {
        return invokeAll(tasks);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
        ExecutionException last = null;
        for (Callable<T> c : tasks) {
            try {
                return submit(c).get();
            } catch (ExecutionException e) {
                last = e;
            }
        }
        throw last != null ? last : new ExecutionException(new IllegalArgumentException("no tasks"));
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        return invokeAny(tasks);
    }

    @Override public synchronized void shutdown() { shutdown = true; }

    @Override
    public synchronized List<Runnable> shutdownNow() {
        shutdown = true;
        return new ArrayList<>();
    }

    @Override public synchronized boolean isShutdown() { return shutdown; }
    @Override public synchronized boolean isTerminated() { return shutdown && running == 0; }

    @Override
    public synchronized boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        long deadline = System.currentTimeMillis() + unit.toMillis(timeout);
        while (running > 0) {
            long left = deadline - System.currentTimeMillis();
            if (left <= 0) return false;
            wait(left);
        }
        return true;
    }

    // --- scheduling ---

    private final class Scheduled<V> extends FutureTask<V> implements ScheduledFuture<V> {
        private final long periodMs; // >0 fixed rate, <0 fixed delay, 0 one-shot
        private long nextRunAt;

        Scheduled(Callable<V> c, long delayMs, long periodMs) {
            super(c);
            this.periodMs = periodMs;
            this.nextRunAt = System.currentTimeMillis() + delayMs;
        }

        @Override
        public long getDelay(TimeUnit unit) {
            return unit.convert(nextRunAt - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
        }

        @Override
        public int compareTo(Delayed o) {
            return Long.compare(getDelay(TimeUnit.MILLISECONDS), o.getDelay(TimeUnit.MILLISECONDS));
        }

        void loop() {
            try {
                while (true) {
                    long wait = nextRunAt - System.currentTimeMillis();
                    if (wait > 0) Thread.sleep(wait);
                    if (isCancelled() || isShutdown()) return;
                    if (periodMs == 0) {
                        run();
                        return;
                    }
                    long started = System.currentTimeMillis();
                    if (!runAndReset()) return;
                    nextRunAt = periodMs > 0 ? nextRunAt + periodMs : System.currentTimeMillis() - periodMs;
                    if (nextRunAt < started) nextRunAt = started;
                }
            } catch (InterruptedException e) {
                cancel(false);
            }
        }
    }

    private <V> Scheduled<V> startScheduled(Scheduled<V> s) {
        launch(s::loop);
        return s;
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        return startScheduled(new Scheduled<>(() -> {
            command.run();
            return null;
        }, unit.toMillis(delay), 0));
    }

    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        return startScheduled(new Scheduled<>(callable, unit.toMillis(delay), 0));
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        return startScheduled(new Scheduled<>(() -> {
            command.run();
            return null;
        }, unit.toMillis(initialDelay), Math.max(1, unit.toMillis(period))));
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
        return startScheduled(new Scheduled<>(() -> {
            command.run();
            return null;
        }, unit.toMillis(initialDelay), -Math.max(1, unit.toMillis(delay))));
    }
}
