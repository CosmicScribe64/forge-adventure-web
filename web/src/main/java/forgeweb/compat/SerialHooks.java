package forgeweb.compat;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Classes whose private writeObject/readObject/readResolve the web object streams call
 * (org.teavm.classlib.java.io.TObjectOutputStream). Listed with class literals on purpose:
 * if the streams called getDeclaredMethods() and invoke() on whatever class they meet, TeaVM would
 * have to keep every method of every reflection-enabled class reachable (builds took 3x longer
 * and ran out of memory). forgeweb.teavm.WebReflection makes the hook methods of these classes
 * callable (its SERIAL_CLASSES list must name the same classes). Add each class that defines
 * such hooks and ends up in a save, in both places.
 */
public final class SerialHooks {
    private static final Map<Class<?>, Method[]> METHODS = new HashMap<>();

    static {
        register(forge.adventure.world.WorldSaveHeader.class, forge.adventure.world.WorldSaveHeader.class.getDeclaredMethods());
        register(forge.item.PaperCard.class, forge.item.PaperCard.class.getDeclaredMethods());
        register(forge.deck.Deck.class, forge.deck.Deck.class.getDeclaredMethods());
    }

    private SerialHooks() {
    }

    private static void register(Class<?> cls, Method[] declaredMethods) {
        METHODS.put(cls, declaredMethods);
        if (hookCount(cls) == 0) {
            // The streams would quietly save and load plain fields instead (see WebReflection).
            System.err.println("SerialHooks: no serialization hooks visible on " + cls.getName()
                    + "; saves of it will be wrong");
        }
    }

    /** How many of writeObject/readObject/readResolve/writeReplace are callable on {@code cls}. */
    public static int hookCount(Class<?> cls) {
        int n = 0;
        if (find(cls, "writeObject", java.io.ObjectOutputStream.class) != null) n++;
        if (find(cls, "readObject", java.io.ObjectInputStream.class) != null) n++;
        if (find(cls, "readResolve") != null) n++;
        if (find(cls, "writeReplace") != null) n++;
        return n;
    }

    public static Method find(Class<?> cls, String name, Class<?>... params) {
        Method[] methods = METHODS.get(cls);
        if (methods == null) return null;
        for (Method m : methods) {
            if (m.getName().equals(name) && Arrays.equals(m.getParameterTypes(), params)) {
                m.setAccessible(true);
                return m;
            }
        }
        return null;
    }
}
