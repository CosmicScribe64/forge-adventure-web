package forgeweb.compat;

/**
 * Classes the game only loads by name (skin JSON, backend lookups). On TeaVM such a class needs:
 * a pattern in build.gradle.kts's reflection() list (fields), a reference so it isn't dropped,
 * getName() called on it so Class.forName can find it, and its Class object flowing into
 * newInstance() so its no-arg constructor is kept. keep() provides the last three.
 */
public final class ByName {
    private ByName() {
    }

    public static final Class<?>[] CLASSES = {
        com.ray3k.tenpatch.TenPatchDrawable.class,
        com.badlogic.gdx.controllers.ControllerManagerStub.class,
    };

    /** Call once at startup. */
    public static int keep() {
        int n = 0;
        for (Class<?> c : CLASSES) {
            n += c.getName().length();
            if (n < 0) {
                // Never runs; TeaVM's analysis doesn't evaluate conditions, so this keeps the
                // no-arg constructors that reflection (Skin, Json) calls.
                try {
                    c.newInstance();
                } catch (ReflectiveOperationException ignored) {
                    // unreachable
                }
            }
        }
        return n;
    }
}
