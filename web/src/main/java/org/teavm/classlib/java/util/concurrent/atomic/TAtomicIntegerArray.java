package org.teavm.classlib.java.util.concurrent.atomic;

import java.util.Arrays;

public class TAtomicIntegerArray implements java.io.Serializable {
    private final int[] array;

    public TAtomicIntegerArray(int length) { array = new int[length]; }
    public TAtomicIntegerArray(int[] values) { array = values.clone(); }

    public final int length() { return array.length; }
    public final int get(int i) { return array[i]; }
    public final void set(int i, int v) { array[i] = v; }
    public final void lazySet(int i, int v) { array[i] = v; }

    public final int getAndSet(int i, int v) {
        int old = array[i];
        array[i] = v;
        return old;
    }

    public final boolean compareAndSet(int i, int expect, int update) {
        if (array[i] != expect) return false;
        array[i] = update;
        return true;
    }

    public final int getAndIncrement(int i) { return array[i]++; }
    public final int getAndDecrement(int i) { return array[i]--; }
    public final int getAndAdd(int i, int d) {
        int old = array[i];
        array[i] += d;
        return old;
    }
    public final int incrementAndGet(int i) { return ++array[i]; }
    public final int decrementAndGet(int i) { return --array[i]; }
    public final int addAndGet(int i, int d) { return array[i] += d; }

    @Override
    public String toString() {
        return Arrays.toString(array);
    }
}
