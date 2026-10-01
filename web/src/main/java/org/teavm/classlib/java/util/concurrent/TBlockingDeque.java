package org.teavm.classlib.java.util.concurrent;

import java.util.Deque;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/** java.util.concurrent.BlockingDeque (missing from TeaVM); implemented by TLinkedBlockingDeque. */
public interface TBlockingDeque<E> extends BlockingQueue<E>, Deque<E> {
    void putFirst(E e) throws InterruptedException;

    void putLast(E e) throws InterruptedException;

    boolean offerFirst(E e, long timeout, TimeUnit unit) throws InterruptedException;

    boolean offerLast(E e, long timeout, TimeUnit unit) throws InterruptedException;

    E takeFirst() throws InterruptedException;

    E takeLast() throws InterruptedException;

    E pollFirst(long timeout, TimeUnit unit) throws InterruptedException;

    E pollLast(long timeout, TimeUnit unit) throws InterruptedException;
}
