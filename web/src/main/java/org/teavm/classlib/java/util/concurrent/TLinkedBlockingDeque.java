package org.teavm.classlib.java.util.concurrent;

import java.util.Collection;
import java.util.LinkedList;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.TimeUnit;

/**
 * Shadows TeaVM 0.15's LinkedBlockingDeque, which is a plain LinkedList that doesn't implement
 * BlockingDeque. Forge keeps its input stack in a field typed BlockingDeque (InputQueue), and
 * TeaVM's type-filtered analysis then saw no implementation for calls on it: push() was never
 * compiled and the first in-game prompt crashed. Blocking operations wait on this object's
 * monitor (green threads); unbounded, like the default JDK constructor.
 */
public class TLinkedBlockingDeque<E> extends LinkedList<E> implements BlockingDeque<E> {
    public TLinkedBlockingDeque() {
    }

    public TLinkedBlockingDeque(Collection<? extends E> c) {
        super(c);
    }

    public TLinkedBlockingDeque(int capacity) {
    }

    // Additions wake up waiting takers.
    @Override public synchronized void addFirst(E e) { super.addFirst(e); notifyAll(); }
    @Override public synchronized void addLast(E e) { super.addLast(e); notifyAll(); }
    @Override public synchronized boolean add(E e) { boolean r = super.add(e); notifyAll(); return r; }
    @Override public synchronized boolean offer(E e) { boolean r = super.offer(e); notifyAll(); return r; }
    @Override public synchronized boolean offerFirst(E e) { boolean r = super.offerFirst(e); notifyAll(); return r; }
    @Override public synchronized boolean offerLast(E e) { boolean r = super.offerLast(e); notifyAll(); return r; }
    @Override public synchronized void push(E e) { super.push(e); notifyAll(); }

    @Override public void put(E e) { addLast(e); }
    @Override public void putFirst(E e) { addFirst(e); }
    @Override public void putLast(E e) { addLast(e); }
    @Override public boolean offer(E e, long timeout, TimeUnit unit) { return offerLast(e); }
    @Override public boolean offerFirst(E e, long timeout, TimeUnit unit) { return offerFirst(e); }
    @Override public boolean offerLast(E e, long timeout, TimeUnit unit) { return offerLast(e); }

    @Override public E take() throws InterruptedException { return takeFirst(); }

    @Override
    public synchronized E takeFirst() throws InterruptedException {
        while (isEmpty()) wait();
        return removeFirst();
    }

    @Override
    public synchronized E takeLast() throws InterruptedException {
        while (isEmpty()) wait();
        return removeLast();
    }

    @Override public E poll(long timeout, TimeUnit unit) throws InterruptedException { return pollFirst(timeout, unit); }

    @Override
    public synchronized E pollFirst(long timeout, TimeUnit unit) throws InterruptedException {
        long deadline = System.currentTimeMillis() + unit.toMillis(timeout);
        while (isEmpty()) {
            long left = deadline - System.currentTimeMillis();
            if (left <= 0) return null;
            wait(left);
        }
        return removeFirst();
    }

    @Override
    public synchronized E pollLast(long timeout, TimeUnit unit) throws InterruptedException {
        long deadline = System.currentTimeMillis() + unit.toMillis(timeout);
        while (isEmpty()) {
            long left = deadline - System.currentTimeMillis();
            if (left <= 0) return null;
            wait(left);
        }
        return removeLast();
    }

    @Override public int remainingCapacity() { return Integer.MAX_VALUE; }

    @Override public int drainTo(Collection<? super E> c) { return drainTo(c, Integer.MAX_VALUE); }

    @Override
    public synchronized int drainTo(Collection<? super E> c, int maxElements) {
        int n = 0;
        while (n < maxElements && !isEmpty()) {
            c.add(removeFirst());
            n++;
        }
        return n;
    }
}
