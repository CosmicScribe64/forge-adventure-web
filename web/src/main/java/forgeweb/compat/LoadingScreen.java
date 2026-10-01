package forgeweb.compat;

import org.teavm.jso.JSBody;

/** The HTML loading screen in web/html/index.html. */
public final class LoadingScreen {
    private static boolean hidden;

    private LoadingScreen() {
    }

    public static boolean isShowing() {
        return !hidden;
    }

    /** Hides it (once); called when the game draws its first frame. */
    public static void hide() {
        if (!hidden) {
            hidden = true;
            hideImpl();
        }
    }

    @JSBody(script = "if (window.forgeLoadingDone) window.forgeLoadingDone();")
    private static native void hideImpl();
}
