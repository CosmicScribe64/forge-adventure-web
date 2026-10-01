package org.teavm.classlib.java.util.concurrent.atomic;

import java.util.Arrays;
import java.util.function.BinaryOperator;
import java.util.function.UnaryOperator;

public class TAtomicReferenceArray<E> implements java.io.Serializable {
    private final Object[] array;

    public TAtomicReferenceArray(int length) { array = new Object[length]; }
    public TAtomicReferenceArray(E[] values) { array = values.clone(); }

    public final int length() { return array.length; }

    @SuppressWarnings("unchecked")
    public final E get(int i) { return (E) array[i]; }

    public final void set(int i, E v) { array[i] = v; }
    public final void lazySet(int i, E v) { array[i] = v; }

    public final E getAndSet(int i, E v) {
        E old = get(i);
        array[i] = v;
        return old;
    }

    public final boolean compareAndSet(int i, E expect, E update) {
        if (array[i] != expect) return false;
        array[i] = update;
        return true;
    }

    public final boolean weakCompareAndSet(int i, E expect, E update) {
        return compareAndSet(i, expect, update);
    }

    public final E getAndUpdate(int i, UnaryOperator<E> fn) {
        E old = get(i);
        array[i] = fn.apply(old);
        return old;
    }

    public final E updateAndGet(int i, UnaryOperator<E> fn) {
        E v = fn.apply(get(i));
        array[i] = v;
        return v;
    }

    public final E accumulateAndGet(int i, E x, BinaryOperator<E> fn) {
        E v = fn.apply(get(i), x);
        array[i] = v;
        return v;
    }

    @Override
    public String toString() {
        return Arrays.toString(array);
    }
}
