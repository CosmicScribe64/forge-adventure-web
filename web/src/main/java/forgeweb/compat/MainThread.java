package forgeweb.compat;

import org.teavm.jso.JSBody;

/** What TeaVM allows on the current call stack (see {@link UiThread}). */
public final class MainThread {
    private MainThread() {
    }

    /**
     * False inside a browser callback (input event, animation frame) with no green thread to
     * pause. There, waits, sleeps and contended locks throw "Suspension point reached from
     * non-threading context".
     */
    @JSBody(script = "return $rt_nativeThread() !== null;")
    public static native boolean canSuspend();
}
