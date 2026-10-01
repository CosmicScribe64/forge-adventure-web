package org.teavm.classlib.java.util.concurrent;

import java.util.concurrent.Delayed;
import java.util.concurrent.Future;

public interface TScheduledFuture<V> extends Delayed, Future<V> {
}
