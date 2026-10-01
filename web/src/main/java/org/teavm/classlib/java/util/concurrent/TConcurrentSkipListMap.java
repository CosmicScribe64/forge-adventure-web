package org.teavm.classlib.java.util.concurrent;

import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentMap;

/** A sorted map; TreeMap is enough without preemptive threads. */
public class TConcurrentSkipListMap<K, V> extends TreeMap<K, V> implements ConcurrentMap<K, V> {
    public TConcurrentSkipListMap() { }
    public TConcurrentSkipListMap(Comparator<? super K> comparator) { super(comparator); }
    public TConcurrentSkipListMap(Map<? extends K, ? extends V> m) { super(m); }

    @Override
    public V putIfAbsent(K key, V value) {
        V old = get(key);
        return old != null ? old : put(key, value);
    }

    @Override
    public boolean remove(Object key, Object value) {
        if (value == null || !value.equals(get(key))) return false;
        remove(key);
        return true;
    }

    @Override
    public boolean replace(K key, V oldValue, V newValue) {
        if (oldValue == null || !oldValue.equals(get(key))) return false;
        put(key, newValue);
        return true;
    }

    @Override
    public V replace(K key, V value) {
        return containsKey(key) ? put(key, value) : null;
    }
}
