package org.teavm.classlib.java.util.concurrent;

import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.CopyOnWriteArrayList;

public class TCopyOnWriteArraySet<E> extends AbstractSet<E> implements java.io.Serializable {
    private final CopyOnWriteArrayList<E> al = new CopyOnWriteArrayList<>();

    public TCopyOnWriteArraySet() { }

    public TCopyOnWriteArraySet(Collection<? extends E> c) {
        addAll(c);
    }

    @Override public int size() { return al.size(); }
    @Override public boolean isEmpty() { return al.isEmpty(); }
    @Override public boolean contains(Object o) { return al.contains(o); }
    @Override public boolean add(E e) { return !al.contains(e) && al.add(e); }
    @Override public boolean remove(Object o) { return al.remove(o); }
    @Override public void clear() { al.clear(); }
    @Override public Iterator<E> iterator() { return al.iterator(); }
    @Override public Object[] toArray() { return al.toArray(); }
    @Override public <T> T[] toArray(T[] a) { return al.toArray(a); }
}
