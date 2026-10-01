package org.teavm.classlib.java.io;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidClassException;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSObject;

import static org.teavm.classlib.java.io.TObjectOutputStream.*;

/** Reads what TObjectOutputStream writes. JDK-format streams (for example, bundled .dat files) fail. */
public class TObjectInputStream extends InputStream implements java.io.ObjectInput {
    private final DataInputStream data;
    private final List<Object> handles = new ArrayList<>();
    private Object currentObject;
    private Class<?> currentClass;

    public TObjectInputStream(InputStream in) throws IOException {
        data = new DataInputStream(in);
        int magic = data.readInt();
        if (magic != MAGIC) {
            throw new java.io.StreamCorruptedException((magic >>> 16) == 0xACED
                    ? "JDK object serialization format is not supported on web"
                    : "not a web object stream");
        }
    }

    protected TObjectInputStream() throws IOException {
        data = null;
    }

    @Override
    public Object readObject() throws ClassNotFoundException, IOException {
        if (data == null) return readObjectOverride();
        return readValue(null);
    }

    protected Object readObjectOverride() throws ClassNotFoundException, IOException {
        throw new IOException("readObjectOverride not implemented");
    }

    public Object readUnshared() throws IOException, ClassNotFoundException {
        return readObject();
    }

    /** From a private readObject hook: reads the current class's fields. */
    public void defaultReadObject() throws IOException, ClassNotFoundException {
        if (currentObject == null) {
            throw new IOException("defaultReadObject outside readObject");
        }
        readFields(currentObject, currentClass);
    }

    /** Forge's DecompressibleInputStream overrides this; our stream has no class descriptors. */
    protected java.io.ObjectStreamClass readClassDescriptor() throws IOException, ClassNotFoundException {
        throw new IOException("no class descriptors in web object streams");
    }

    // --- values ---

    /** @param existing the value currently in the field being filled, reused for containers */
    private Object readValue(Object existing) throws IOException, ClassNotFoundException {
        int tag = data.readUnsignedByte();
        switch (tag) {
            case T_NULL: return null;
            case T_REF: {
                int h = data.readInt();
                if (h < 0 || h >= handles.size()) throw new java.io.StreamCorruptedException("bad handle " + h);
                return handles.get(h);
            }
            case T_STRING: return readString();
            case T_INT: return data.readInt();
            case T_LONG: return data.readLong();
            case T_FLOAT: return data.readFloat();
            case T_DOUBLE: return data.readDouble();
            case T_BOOLEAN: return data.readBoolean();
            case T_BYTE: return data.readByte();
            case T_SHORT: return data.readShort();
            case T_CHAR: return data.readChar();
            case T_ENUM: {
                Class<?> type = classForName(readString());
                String name = readString();
                Object[] constants = type.getEnumConstants();
                if (constants != null) {
                    for (Object constant : constants) {
                        if (((Enum<?>) constant).name().equals(name)) return constant;
                    }
                }
                // TeaVM keeps getEnumConstants() only for enums it saw flow there; a constant is
                // also a static field of the same name.
                try {
                    Field f = type.getDeclaredField(name);
                    f.setAccessible(true);
                    return f.get(null);
                } catch (Exception e) {
                    throw new InvalidClassException(type.getName(), "no enum constant " + name);
                }
            }
            case T_CLASS: return classForName(readString());
            case T_UUID: return java.util.UUID.fromString(readString());
            case T_DATE: {
                Date d = new Date(data.readLong());
                handles.add(d);
                return d;
            }
            case T_ARRAY: return readArray();
            case T_ENUM_MAP: return readEnumMap(existing);
            case T_MAP: return readMap(existing);
            case T_COLLECTION: return readCollection(existing);
            case T_OBJECT: return readPlainObject();
            default: throw new java.io.StreamCorruptedException("bad tag " + tag);
        }
    }

    private Object readArray() throws IOException, ClassNotFoundException {
        String componentName = readString();
        int n = data.readInt();
        Object array;
        switch (componentName) {
            case "int": { int[] a = new int[n]; handles.add(a); for (int i = 0; i < n; i++) a[i] = data.readInt(); return a; }
            case "long": { long[] a = new long[n]; handles.add(a); for (int i = 0; i < n; i++) a[i] = data.readLong(); return a; }
            case "float": { float[] a = new float[n]; handles.add(a); for (int i = 0; i < n; i++) a[i] = data.readFloat(); return a; }
            case "double": { double[] a = new double[n]; handles.add(a); for (int i = 0; i < n; i++) a[i] = data.readDouble(); return a; }
            case "boolean": { boolean[] a = new boolean[n]; handles.add(a); for (int i = 0; i < n; i++) a[i] = data.readBoolean(); return a; }
            case "byte": { byte[] a = new byte[n]; handles.add(a); data.readFully(a); return a; }
            case "short": { short[] a = new short[n]; handles.add(a); for (int i = 0; i < n; i++) a[i] = data.readShort(); return a; }
            case "char": { char[] a = new char[n]; handles.add(a); for (int i = 0; i < n; i++) a[i] = data.readChar(); return a; }
            default:
                array = Array.newInstance(classForName(componentName), n);
                handles.add(array);
                Object[] objects = (Object[]) array;
                for (int i = 0; i < n; i++) objects[i] = readValue(null);
                return array;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object readEnumMap(Object existing) throws IOException, ClassNotFoundException {
        String keyTypeName = readString();
        int n = data.readInt();
        Map map;
        if (existing instanceof EnumMap) {
            map = (Map) existing;
            map.clear();
        } else if (!keyTypeName.isEmpty()) {
            map = new EnumMap(classForName(keyTypeName));
        } else {
            map = new LinkedHashMap();
        }
        handles.add(map);
        for (int i = 0; i < n; i++) {
            Object k = readValue(null);
            map.put(k, readValue(null));
        }
        return map;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object readMap(Object existing) throws IOException, ClassNotFoundException {
        String className = readString();
        int kind = data.readUnsignedByte();
        int n = data.readInt();
        Map map;
        if (existing instanceof Map && existing.getClass().getName().equals(className)) {
            map = (Map) existing; // keeps e.g. a comparator set up by the field's initializer
            map.clear();
        } else {
            map = (Map) newContainer(className, kind);
        }
        handles.add(map);
        for (int i = 0; i < n; i++) {
            Object k = readValue(null);
            map.put(k, readValue(null));
        }
        return map;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object readCollection(Object existing) throws IOException, ClassNotFoundException {
        String className = readString();
        int kind = data.readUnsignedByte();
        int n = data.readInt();
        Collection col;
        if (existing instanceof Collection && existing.getClass().getName().equals(className)) {
            col = (Collection) existing;
            col.clear();
        } else {
            col = (Collection) newContainer(className, kind);
        }
        handles.add(col);
        for (int i = 0; i < n; i++) {
            col.add(readValue(null));
        }
        return col;
    }

    /** Known JDK containers directly; others by their no-arg constructor, else by kind. */
    private static Object newContainer(String className, int kind) {
        switch (className) {
            case "java.util.ArrayList": return new ArrayList<>();
            case "java.util.LinkedList": return new LinkedList<>();
            case "java.util.ArrayDeque": return new ArrayDeque<>();
            case "java.util.HashSet": return new HashSet<>();
            case "java.util.LinkedHashSet": return new LinkedHashSet<>();
            case "java.util.TreeSet": return new TreeSet<>();
            case "java.util.HashMap": return new HashMap<>();
            case "java.util.LinkedHashMap": return new LinkedHashMap<>();
            case "java.util.TreeMap": return new TreeMap<>();
            default:
                break;
        }
        try {
            return Class.forName(className).newInstance();
        } catch (Throwable e) {
            // Unmodifiable views, Arrays$ArrayList, ...: an ordinary container of the same kind.
            switch (kind) {
                case K_SET: return new LinkedHashSet<>();
                case K_SORTED_SET: return new TreeSet<>();
                case K_QUEUE: return new ArrayDeque<>();
                case K_MAP: return new LinkedHashMap<>();
                case K_SORTED_MAP: return new TreeMap<>();
                default: return new ArrayList<>();
            }
        }
    }

    private Object readPlainObject() throws IOException, ClassNotFoundException {
        Class<?> c = classForName(readString());
        Object o = allocate(c);
        int handle = handles.size();
        handles.add(o);
        for (Class<?> cls : serializableHierarchy(c)) {
            Method hook = privateHook(cls, "readObject", java.io.ObjectInputStream.class);
            if (hook != null) {
                Object savedObject = currentObject;
                Class<?> savedClass = currentClass;
                currentObject = o;
                currentClass = cls;
                try {
                    hook.invoke(o, this);
                } catch (Exception e) {
                    throw wrap("readObject of " + cls.getName(), e);
                } finally {
                    currentObject = savedObject;
                    currentClass = savedClass;
                }
            } else {
                readFields(o, cls);
            }
        }
        for (Class<?> k = c; k != null; k = k.getSuperclass()) {
            Method resolve = privateHook(k, "readResolve");
            if (resolve != null) {
                try {
                    Object replaced = resolve.invoke(o);
                    handles.set(handle, replaced);
                    return replaced;
                } catch (Exception e) {
                    throw wrap("readResolve of " + k.getName(), e);
                }
            }
        }
        return o;
    }

    private void readFields(Object o, Class<?> cls) throws IOException, ClassNotFoundException {
        for (Field f : serialFields(cls)) {
            try {
                // TeaVM's Field has no typed setters; set() unboxes.
                Class<?> t = f.getType();
                if (t == int.class) f.set(o, data.readInt());
                else if (t == long.class) f.set(o, data.readLong());
                else if (t == float.class) f.set(o, data.readFloat());
                else if (t == double.class) f.set(o, data.readDouble());
                else if (t == boolean.class) f.set(o, data.readBoolean());
                else if (t == byte.class) f.set(o, data.readByte());
                else if (t == short.class) f.set(o, data.readShort());
                else if (t == char.class) f.set(o, data.readChar());
                else {
                    Object existing = f.get(o);
                    Object value = readValue(existing);
                    if (value != existing) f.set(o, value);
                }
            } catch (IllegalAccessException e) {
                throw wrap("field " + cls.getName() + "." + f.getName(), e);
            }
        }
    }

    private static Class<?> classForName(String name) throws ClassNotFoundException {
        switch (name) {
            case "int": return int.class;
            case "long": return long.class;
            case "float": return float.class;
            case "double": return double.class;
            case "boolean": return boolean.class;
            case "byte": return byte.class;
            case "short": return short.class;
            case "char": return char.class;
            default:
                break;
        }
        if (name.startsWith("[")) {
            // Array descriptors ("[J", "[[I", "[Lforge.Foo;"): TeaVM's Class.forName doesn't take them.
            Class<?> element;
            String rest = name.substring(1);
            switch (rest.charAt(0)) {
                case 'I': element = int.class; break;
                case 'J': element = long.class; break;
                case 'F': element = float.class; break;
                case 'D': element = double.class; break;
                case 'Z': element = boolean.class; break;
                case 'B': element = byte.class; break;
                case 'S': element = short.class; break;
                case 'C': element = char.class; break;
                case '[': element = classForName(rest); break;
                case 'L': element = classForName(rest.substring(1, rest.length() - 1)); break;
                default: throw new ClassNotFoundException(name);
            }
            return Array.newInstance(element, 0).getClass();
        }
        return Class.forName(name);
    }

    /**
     * A new instance without running any constructor, like the JDK's deserialization: TeaVM's
     * class objects keep the JavaScript constructor in $classInfo, and calling it only sets
     * fields to their defaults. Then the class is initialized, as `new` would.
     */
    private static Object allocate(Class<?> c) throws InvalidClassException {
        Object o = allocateImpl((JSObject) (Object) c);
        if (o == null) throw new InvalidClassException(c.getName(), "cannot allocate");
        return o;
    }

    @JSBody(params = "cls", script = "var ctor = cls.$classInfo; if (!ctor) return null;"
            + " if (typeof jl_Class_initialize === 'function') jl_Class_initialize(cls);"
            + " return new ctor();")
    private static native Object allocateImpl(JSObject cls);

    private String readString() throws IOException {
        int n = data.readInt();
        byte[] bytes = new byte[n];
        data.readFully(bytes);
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }

    // --- DataInput ---

    @Override public int read() throws IOException { return data.read(); }
    @Override public int read(byte[] b, int off, int len) throws IOException { return data.read(b, off, len); }
    @Override public int available() throws IOException { return data == null ? 0 : data.available(); }
    @Override public void close() throws IOException { if (data != null) data.close(); }
    @Override public void readFully(byte[] b) throws IOException { data.readFully(b); }
    @Override public void readFully(byte[] b, int off, int len) throws IOException { data.readFully(b, off, len); }
    @Override public int skipBytes(int n) throws IOException { return data.skipBytes(n); }
    @Override public boolean readBoolean() throws IOException { return data.readBoolean(); }
    @Override public byte readByte() throws IOException { return data.readByte(); }
    @Override public int readUnsignedByte() throws IOException { return data.readUnsignedByte(); }
    @Override public short readShort() throws IOException { return data.readShort(); }
    @Override public int readUnsignedShort() throws IOException { return data.readUnsignedShort(); }
    @Override public char readChar() throws IOException { return data.readChar(); }
    @Override public int readInt() throws IOException { return data.readInt(); }
    @Override public long readLong() throws IOException { return data.readLong(); }
    @Override public float readFloat() throws IOException { return data.readFloat(); }
    @Override public double readDouble() throws IOException { return data.readDouble(); }
    @SuppressWarnings("deprecation")
    @Override public String readLine() throws IOException { return data.readLine(); }
    @Override public String readUTF() throws IOException { return readString(); }
}
