package org.teavm.classlib.java.io;

/** Referenced by XStream's static initialisers; serialization itself is unsupported on web. */
public class TObjectStreamField implements Comparable<Object> {
    private final String name;
    private final Class<?> type;

    public TObjectStreamField(String name, Class<?> type) {
        this.name = name;
        this.type = type;
    }

    public TObjectStreamField(String name, Class<?> type, boolean unshared) {
        this(name, type);
    }

    public String getName() { return name; }
    public Class<?> getType() { return type; }

    @Override
    public int compareTo(Object o) {
        return name.compareTo(((TObjectStreamField) o).name);
    }
}
