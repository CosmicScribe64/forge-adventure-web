package org.teavm.classlib.java.util.concurrent;

import java.util.AbstractQueue;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;

/** Green threads only switch at blocking calls, so a plain list is safe here. */
public class TConcurrentLinkedQueue<E> extends AbstractQueue<E> implements java.io.Serializable {
    private final LinkedList<E> items = new LinkedList<>();

    public TConcurrentLinkedQueue() { }

    public TConcurrentLinkedQueue(Collection<? extends E> c) {
        items.addAll(c);
    }

    @Override
    public boolean offer(E e) {
        if (e == null) throw new NullPointerException();
        return items.add(e);
    }

    @Override public E poll() { return items.poll(); }
    @Override public E peek() { return items.peek(); }
    @Override public int size() { return items.size(); }
    @Override public boolean isEmpty() { return items.isEmpty(); }
    @Override public boolean contains(Object o) { return items.contains(o); }
    @Override public boolean remove(Object o) { return items.remove(o); }

    /** Weakly consistent, like the JDK's: iterates a snapshot. */
    @Override
    public Iterator<E> iterator() {
        Iterator<E> snap = new ArrayList<>(items).iterator();
        return new Iterator<E>() {
            E last;

            @Override public boolean hasNext() { return snap.hasNext(); }

            @Override
            public E next() {
                last = snap.next();
                return last;
            }

            @Override
            public void remove() {
                items.remove(last);
            }
        };
    }
}
