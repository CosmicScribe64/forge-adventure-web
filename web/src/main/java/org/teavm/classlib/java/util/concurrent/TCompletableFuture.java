package org.teavm.classlib.java.util.concurrent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The subset of CompletableFuture that Forge uses, on TeaVM green threads.
 * Dependent stages run on whichever thread completes this future.
 */
public class TCompletableFuture<T> implements Future<T> {
    private boolean done;
    private T value;
    private Throwable error;
    private List<Runnable> callbacks = new ArrayList<>();

    public TCompletableFuture() {
    }

    public static <U> TCompletableFuture<U> supplyAsync(Supplier<U> supplier) {
        TCompletableFuture<U> f = new TCompletableFuture<>();
        new Thread(() -> {
            try {
                f.complete(supplier.get());
            } catch (Throwable t) {
                f.completeExceptionally(t);
            }
        }).start();
        return f;
    }

    public static <U> TCompletableFuture<U> supplyAsync(Supplier<U> supplier, Executor executor) {
        TCompletableFuture<U> f = new TCompletableFuture<>();
        executor.execute(() -> {
            try {
                f.complete(supplier.get());
            } catch (Throwable t) {
                f.completeExceptionally(t);
            }
        });
        return f;
    }

    public static TCompletableFuture<Void> runAsync(Runnable r) {
        return supplyAsync(() -> {
            r.run();
            return null;
        });
    }

    public static TCompletableFuture<Void> runAsync(Runnable r, Executor executor) {
        return supplyAsync(() -> {
            r.run();
            return null;
        }, executor);
    }

    public static <U> TCompletableFuture<U> completedFuture(U value) {
        TCompletableFuture<U> f = new TCompletableFuture<>();
        f.complete(value);
        return f;
    }

    public static <U> TCompletableFuture<U> failedFuture(Throwable ex) {
        TCompletableFuture<U> f = new TCompletableFuture<>();
        f.completeExceptionally(ex);
        return f;
    }

    public static TCompletableFuture<Void> allOf(TCompletableFuture<?>... cfs) {
        TCompletableFuture<Void> all = new TCompletableFuture<>();
        if (cfs.length == 0) {
            all.complete(null);
            return all;
        }
        int[] remaining = {cfs.length};
        for (TCompletableFuture<?> cf : cfs) {
            cf.onDone(() -> {
                if (--remaining[0] == 0) {
                    Throwable firstError = null;
                    for (TCompletableFuture<?> c : cfs) {
                        if (c.error != null) {
                            firstError = c.error;
                            break;
                        }
                    }
                    if (firstError != null) all.completeExceptionally(firstError);
                    else all.complete(null);
                }
            });
        }
        return all;
    }

    public static TCompletableFuture<Object> anyOf(TCompletableFuture<?>... cfs) {
        TCompletableFuture<Object> any = new TCompletableFuture<>();
        for (TCompletableFuture<?> cf : cfs) {
            cf.onDone(() -> {
                if (cf.error != null) any.completeExceptionally(cf.error);
                else any.complete(cf.value);
            });
        }
        return any;
    }

    private void onDone(Runnable r) {
        boolean runNow;
        synchronized (this) {
            runNow = done;
            if (!runNow) callbacks.add(r);
        }
        if (runNow) r.run();
    }

    private boolean finish(T v, Throwable t) {
        List<Runnable> toRun;
        synchronized (this) {
            if (done) return false;
            done = true;
            value = v;
            error = t;
            toRun = callbacks;
            callbacks = null;
            notifyAll();
        }
        for (Runnable r : toRun) r.run();
        return true;
    }

    public boolean complete(T v) { return finish(v, null); }
    public boolean completeExceptionally(Throwable t) { return finish(null, t); }

    @Override public boolean cancel(boolean mayInterrupt) { return finish(null, new CancellationException()); }
    @Override public synchronized boolean isCancelled() { return done && error instanceof CancellationException; }
    @Override public synchronized boolean isDone() { return done; }
    public synchronized boolean isCompletedExceptionally() { return done && error != null; }

    private synchronized void waitDone() throws InterruptedException {
        while (!done) wait();
    }

    public T join() {
        try {
            waitDone();
        } catch (InterruptedException e) {
            throw new CompletionException(e);
        }
        if (error == null) return value;
        if (error instanceof CancellationException) throw (CancellationException) error;
        throw error instanceof CompletionException ? (CompletionException) error : new CompletionException(error);
    }

    @Override
    public T get() throws InterruptedException, ExecutionException {
        waitDone();
        if (error == null) return value;
        if (error instanceof CancellationException) throw (CancellationException) error;
        Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
        throw new ExecutionException(cause);
    }

    @Override
    public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        long deadline = System.currentTimeMillis() + unit.toMillis(timeout);
        synchronized (this) {
            while (!done) {
                long left = deadline - System.currentTimeMillis();
                if (left <= 0) throw new TimeoutException();
                wait(left);
            }
        }
        return get();
    }

    public T getNow(T valueIfAbsent) {
        synchronized (this) {
            if (!done) return valueIfAbsent;
        }
        return join();
    }

    public <U> TCompletableFuture<U> thenApply(Function<? super T, ? extends U> fn) {
        TCompletableFuture<U> next = new TCompletableFuture<>();
        onDone(() -> {
            if (error != null) {
                next.completeExceptionally(error);
                return;
            }
            try {
                next.complete(fn.apply(value));
            } catch (Throwable t) {
                next.completeExceptionally(t);
            }
        });
        return next;
    }

    public TCompletableFuture<Void> thenAccept(Consumer<? super T> action) {
        return thenApply(v -> {
            action.accept(v);
            return null;
        });
    }

    public TCompletableFuture<Void> thenRun(Runnable action) {
        return thenApply(v -> {
            action.run();
            return null;
        });
    }

    public <U> TCompletableFuture<U> handle(BiFunction<? super T, Throwable, ? extends U> fn) {
        TCompletableFuture<U> next = new TCompletableFuture<>();
        onDone(() -> {
            try {
                next.complete(fn.apply(value, error));
            } catch (Throwable t) {
                next.completeExceptionally(t);
            }
        });
        return next;
    }

    public TCompletableFuture<T> whenComplete(BiConsumer<? super T, ? super Throwable> action) {
        TCompletableFuture<T> next = new TCompletableFuture<>();
        onDone(() -> {
            try {
                action.accept(value, error);
            } catch (Throwable t) {
                if (error == null) {
                    next.completeExceptionally(t);
                    return;
                }
            }
            next.finish(value, error);
        });
        return next;
    }

    public TCompletableFuture<T> exceptionally(Function<Throwable, ? extends T> fn) {
        TCompletableFuture<T> next = new TCompletableFuture<>();
        onDone(() -> {
            if (error == null) {
                next.complete(value);
                return;
            }
            try {
                next.complete(fn.apply(error));
            } catch (Throwable t) {
                next.completeExceptionally(t);
            }
        });
        return next;
    }
}
