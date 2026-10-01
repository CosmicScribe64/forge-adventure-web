package org.teavm.classlib.java.io;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;

/**
 * Object serialization for the web build. Not the JDK's wire format (these saves only live in
 * the browser): a tagged stream of values, see TObjectInputStream for the reading side.
 *
 * Handles null, shared references and cycles, strings, boxed primitives, enums, Class, Date,
 * arrays (any dimension), collections and maps (written as their contents, since JDK and TeaVM
 * keep them in different private fields; rebuilt by class or kind, and a container already in the
 * field being read is refilled so its comparator survives; EnumMap keeps its key type),
 * and other Serializable objects field by field through reflection, honouring private
 * writeObject/readObject hooks (defaultWriteObject/defaultReadObject) and readResolve.
 * Every class written this way needs reflection metadata (build.gradle.kts reflection()).
 */
public class TObjectOutputStream extends OutputStream implements java.io.ObjectOutput {
    static final int MAGIC = 0x46575331; // "FWS1"

    static final int T_NULL = 0;
    static final int T_REF = 1;
    static final int T_STRING = 2;
    static final int T_INT = 3;
    static final int T_LONG = 4;
    static final int T_FLOAT = 5;
    static final int T_DOUBLE = 6;
    static final int T_BOOLEAN = 7;
    static final int T_BYTE = 8;
    static final int T_SHORT = 9;
    static final int T_CHAR = 10;
    static final int T_ENUM = 11;
    static final int T_CLASS = 12;
    static final int T_DATE = 13;
    static final int T_ARRAY = 14;
    static final int T_COLLECTION = 15;
    static final int T_MAP = 16;
    static final int T_ENUM_MAP = 17;
    static final int T_OBJECT = 18;
    static final int T_UUID = 19;

    // Collection / map kinds, so the reader can rebuild a compatible container.
    static final int K_LIST = 0;
    static final int K_SET = 1;
    static final int K_SORTED_SET = 2;
    static final int K_QUEUE = 3;
    static final int K_MAP = 4;
    static final int K_SORTED_MAP = 5;

    private final DataOutputStream data;
    private final IdentityHashMap<Object, Integer> handles = new IdentityHashMap<>();
    private Object currentObject;
    private Class<?> currentClass;

    public TObjectOutputStream(OutputStream out) throws IOException {
        data = new DataOutputStream(out);
        data.writeInt(MAGIC);
    }

    protected TObjectOutputStream() throws IOException {
        data = null;
    }

    @Override
    public void writeObject(Object obj) throws IOException {
        if (data == null) {
            writeObjectOverride(obj);
            return;
        }
        writeValue(obj);
    }

    protected void writeObjectOverride(Object obj) throws IOException {
        throw new IOException("writeObjectOverride not implemented");
    }

    public void writeUnshared(Object obj) throws IOException {
        writeObject(obj);
    }

    /** From a private writeObject hook: writes the current class's fields. */
    public void defaultWriteObject() throws IOException {
        if (currentObject == null) {
            throw new java.io.NotActiveException("defaultWriteObject outside writeObject");
        }
        writeFields(currentObject, currentClass);
    }

    public void reset() throws IOException {
        handles.clear();
    }

    // --- values ---

    private void writeValue(Object o) throws IOException {
        if (o == null) {
            data.writeByte(T_NULL);
            return;
        }
        if (o instanceof String) {
            data.writeByte(T_STRING);
            writeString((String) o);
            return;
        }
        if (o instanceof Integer) { data.writeByte(T_INT); data.writeInt((Integer) o); return; }
        if (o instanceof Long) { data.writeByte(T_LONG); data.writeLong((Long) o); return; }
        if (o instanceof Float) { data.writeByte(T_FLOAT); data.writeFloat((Float) o); return; }
        if (o instanceof Double) { data.writeByte(T_DOUBLE); data.writeDouble((Double) o); return; }
        if (o instanceof Boolean) { data.writeByte(T_BOOLEAN); data.writeBoolean((Boolean) o); return; }
        if (o instanceof Byte) { data.writeByte(T_BYTE); data.writeByte((Byte) o); return; }
        if (o instanceof Short) { data.writeByte(T_SHORT); data.writeShort((Short) o); return; }
        if (o instanceof Character) { data.writeByte(T_CHAR); data.writeChar((Character) o); return; }
        if (o instanceof Enum) {
            data.writeByte(T_ENUM);
            writeString(((Enum<?>) o).getDeclaringClass().getName());
            writeString(((Enum<?>) o).name());
            return;
        }
        if (o instanceof java.util.UUID) { // not Serializable in TeaVM's classlib
            data.writeByte(T_UUID);
            writeString(o.toString());
            return;
        }
        if (o instanceof Class) {
            data.writeByte(T_CLASS);
            writeString(((Class<?>) o).getName());
            return;
        }

        Integer handle = handles.get(o);
        if (handle != null) {
            data.writeByte(T_REF);
            data.writeInt(handle);
            return;
        }
        handles.put(o, handles.size());

        Class<?> c = o.getClass();
        if (o instanceof Date) {
            data.writeByte(T_DATE);
            data.writeLong(((Date) o).getTime());
        } else if (c.isArray()) {
            writeArray(o, c);
        } else if (o instanceof EnumMap) {
            EnumMap<?, ?> m = (EnumMap<?, ?>) o;
            data.writeByte(T_ENUM_MAP);
            Class<?> keyType = m.isEmpty() ? null : ((Enum<?>) m.keySet().iterator().next()).getDeclaringClass();
            writeString(keyType == null ? "" : keyType.getName());
            data.writeInt(m.size());
            for (Map.Entry<?, ?> e : m.entrySet()) {
                writeValue(e.getKey());
                writeValue(e.getValue());
            }
        } else if (o instanceof Map) {
            Map<?, ?> m = (Map<?, ?>) o;
            data.writeByte(T_MAP);
            writeString(c.getName());
            data.writeByte(o instanceof SortedMap ? K_SORTED_MAP : K_MAP);
            data.writeInt(m.size());
            for (Map.Entry<?, ?> e : m.entrySet()) {
                writeValue(e.getKey());
                writeValue(e.getValue());
            }
        } else if (o instanceof Collection) {
            Collection<?> col = (Collection<?>) o;
            data.writeByte(T_COLLECTION);
            writeString(c.getName());
            int kind = o instanceof SortedSet ? K_SORTED_SET : o instanceof Set ? K_SET
                    : o instanceof List ? K_LIST : o instanceof Queue ? K_QUEUE : K_LIST;
            data.writeByte(kind);
            data.writeInt(col.size());
            for (Object e : col) {
                writeValue(e);
            }
        } else {
            if (!(o instanceof Serializable)) {
                throw new java.io.NotSerializableException(c.getName());
            }
            data.writeByte(T_OBJECT);
            writeString(c.getName());
            writeClassData(o, c);
        }
    }

    private void writeArray(Object array, Class<?> c) throws IOException {
        data.writeByte(T_ARRAY);
        Class<?> component = c.getComponentType();
        writeString(component.getName());
        int n = Array.getLength(array);
        data.writeInt(n);
        if (component == int.class) {
            for (int v : (int[]) array) data.writeInt(v);
        } else if (component == long.class) {
            for (long v : (long[]) array) data.writeLong(v);
        } else if (component == float.class) {
            for (float v : (float[]) array) data.writeFloat(v);
        } else if (component == double.class) {
            for (double v : (double[]) array) data.writeDouble(v);
        } else if (component == boolean.class) {
            for (boolean v : (boolean[]) array) data.writeBoolean(v);
        } else if (component == byte.class) {
            data.write((byte[]) array);
        } else if (component == short.class) {
            for (short v : (short[]) array) data.writeShort(v);
        } else if (component == char.class) {
            for (char v : (char[]) array) data.writeChar(v);
        } else {
            for (Object v : (Object[]) array) writeValue(v);
        }
    }

    private void writeClassData(Object o, Class<?> c) throws IOException {
        for (Class<?> cls : serializableHierarchy(c)) {
            Method hook = privateHook(cls, "writeObject", java.io.ObjectOutputStream.class);
            if (hook != null) {
                Object savedObject = currentObject;
                Class<?> savedClass = currentClass;
                currentObject = o;
                currentClass = cls;
                try {
                    hook.invoke(o, this);
                } catch (Exception e) {
                    throw wrap("writeObject of " + cls.getName(), e);
                } finally {
                    currentObject = savedObject;
                    currentClass = savedClass;
                }
            } else {
                writeFields(o, cls);
            }
        }
    }

    private void writeFields(Object o, Class<?> cls) throws IOException {
        for (Field f : serialFields(cls)) {
            try {
                // TeaVM's Field has no typed getters; get() boxes primitives.
                Class<?> t = f.getType();
                Object v = f.get(o);
                if (t == int.class) data.writeInt((Integer) v);
                else if (t == long.class) data.writeLong((Long) v);
                else if (t == float.class) data.writeFloat((Float) v);
                else if (t == double.class) data.writeDouble((Double) v);
                else if (t == boolean.class) data.writeBoolean((Boolean) v);
                else if (t == byte.class) data.writeByte((Byte) v);
                else if (t == short.class) data.writeShort((Short) v);
                else if (t == char.class) data.writeChar((Character) v);
                else writeValue(v);
            } catch (IllegalAccessException e) {
                throw wrap("field " + cls.getName() + "." + f.getName(), e);
            }
        }
    }

    // --- reflection helpers, shared with TObjectInputStream ---

    /** Serializable classes from the topmost one down to {@code c}. */
    static List<Class<?>> serializableHierarchy(Class<?> c) {
        List<Class<?>> chain = new ArrayList<>();
        for (Class<?> k = c; k != null && Serializable.class.isAssignableFrom(k); k = k.getSuperclass()) {
            chain.add(0, k);
        }
        return chain;
    }

    /** Non-static, non-transient fields declared by {@code cls}, in name order. */
    static Field[] serialFields(Class<?> cls) {
        List<Field> out = new ArrayList<>();
        for (Field f : cls.getDeclaredFields()) {
            int m = f.getModifiers();
            if (Modifier.isStatic(m) || Modifier.isTransient(m)) continue;
            f.setAccessible(true);
            out.add(f);
        }
        Field[] fields = out.toArray(new Field[0]);
        Arrays.sort(fields, (a, b) -> a.getName().compareTo(b.getName()));
        return fields;
    }

    /** Only classes registered in forgeweb.compat.SerialHooks (see there for why). */
    static Method privateHook(Class<?> cls, String name, Class<?>... params) {
        return forgeweb.compat.SerialHooks.find(cls, name, params);
    }

    static IOException wrap(String what, Exception e) {
        Throwable cause = e instanceof java.lang.reflect.InvocationTargetException && e.getCause() != null ? e.getCause() : e;
        if (cause instanceof IOException) return (IOException) cause;
        IOException io = new IOException(what + ": " + cause);
        io.initCause(cause);
        return io;
    }

    private void writeString(String s) throws IOException {
        byte[] bytes = s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        data.writeInt(bytes.length);
        data.write(bytes);
    }

    // --- DataOutput ---

    @Override public void write(int b) throws IOException { data.write(b); }
    @Override public void write(byte[] b) throws IOException { data.write(b); }
    @Override public void write(byte[] b, int off, int len) throws IOException { data.write(b, off, len); }
    @Override public void flush() throws IOException { if (data != null) data.flush(); }
    @Override public void close() throws IOException { if (data != null) data.close(); }
    @Override public void writeBoolean(boolean v) throws IOException { data.writeBoolean(v); }
    @Override public void writeByte(int v) throws IOException { data.writeByte(v); }
    @Override public void writeShort(int v) throws IOException { data.writeShort(v); }
    @Override public void writeChar(int v) throws IOException { data.writeChar(v); }
    @Override public void writeInt(int v) throws IOException { data.writeInt(v); }
    @Override public void writeLong(long v) throws IOException { data.writeLong(v); }
    @Override public void writeFloat(float v) throws IOException { data.writeFloat(v); }
    @Override public void writeDouble(double v) throws IOException { data.writeDouble(v); }
    @Override public void writeBytes(String s) throws IOException { data.writeBytes(s); }
    @Override public void writeChars(String s) throws IOException { data.writeChars(s); }
    @Override public void writeUTF(String s) throws IOException { writeString(s); }


}
