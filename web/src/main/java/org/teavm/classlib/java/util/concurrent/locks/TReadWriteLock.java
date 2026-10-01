package org.teavm.classlib.java.util.concurrent.locks;

import java.util.concurrent.locks.Lock;

public interface TReadWriteLock {
    Lock readLock();
    Lock writeLock();
}
