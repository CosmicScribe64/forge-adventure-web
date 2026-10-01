package org.teavm.classlib.java.util.concurrent;

import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

public interface TDelayed extends Comparable<Delayed> {
    long getDelay(TimeUnit unit);
}
