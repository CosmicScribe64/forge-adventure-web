package org.teavm.classlib.java.io;

import java.io.ObjectStreamField;

/**
 * XStream asks for a class's serialPersistentFields. There is no reflection over
 * private fields on web, so report none (XStream then uses its reflection provider).
 */
public class TObjectStreamClass implements java.io.Serializable {
    private final Class<?> type;

    private TObjectStreamClass(Class<?> type) {
        this.type = type;
    }

    public static TObjectStreamClass lookup(Class<?> cl) {
        return cl == null ? null : new TObjectStreamClass(cl);
    }

    public String getName() { return type.getName(); }
    public Class<?> forClass() { return type; }
    public ObjectStreamField[] getFields() { return new ObjectStreamField[0]; }
    public ObjectStreamField getField(String name) { return null; }
    public long getSerialVersionUID() { return 0L; }
}
