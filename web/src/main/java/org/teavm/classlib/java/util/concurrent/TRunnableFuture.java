package org.teavm.classlib.java.util.concurrent;

import java.util.concurrent.Future;

public interface TRunnableFuture<V> extends Runnable, Future<V> {
    @Override
    void run();
}
