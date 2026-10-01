package forgeweb.compat;

import java.util.AbstractSet;
import java.util.Comparator;
import java.util.Iterator;
import java.util.SortedSet;

/** Live read-only view, as returned by Collections.unmodifiableSortedSet. */
final class ReadOnlySortedSet<T> extends AbstractSet<T> implements SortedSet<T> {
    private final SortedSet<T> s;

    ReadOnlySortedSet(SortedSet<T> s) {
        this.s = s;
    }

    @Override public int size() { return s.size(); }
    @Override public boolean contains(Object o) { return s.contains(o); }
    @Override public Comparator<? super T> comparator() { return s.comparator(); }
    @Override public SortedSet<T> subSet(T from, T to) { return new ReadOnlySortedSet<>(s.subSet(from, to)); }
    @Override public SortedSet<T> headSet(T to) { return new ReadOnlySortedSet<>(s.headSet(to)); }
    @Override public SortedSet<T> tailSet(T from) { return new ReadOnlySortedSet<>(s.tailSet(from)); }
    @Override public T first() { return s.first(); }
    @Override public T last() { return s.last(); }
    @Override public boolean add(T t) { throw new UnsupportedOperationException(); }
    @Override public boolean remove(Object o) { throw new UnsupportedOperationException(); }
    @Override public void clear() { throw new UnsupportedOperationException(); }

    @Override
    public Iterator<T> iterator() {
        Iterator<T> it = s.iterator();
        return new Iterator<T>() {
            @Override public boolean hasNext() { return it.hasNext(); }
            @Override public T next() { return it.next(); }
        };
    }
}
