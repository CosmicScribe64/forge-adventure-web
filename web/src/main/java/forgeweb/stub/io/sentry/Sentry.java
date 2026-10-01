package forgeweb.stub.io.sentry;

import io.sentry.Breadcrumb;
import io.sentry.Hint;
import io.sentry.ScopeCallback;
import io.sentry.ScopeType;
import io.sentry.SentryLevel;
import io.sentry.protocol.SentryId;

/** No crash reporting on web: every io.sentry.Sentry entry point Forge uses is a no-op. */
public final class Sentry {
    public static boolean isEnabled() { return false; }
    public static void init() { }
    public static void init(String dsn) { }
    public static void close() { }
    public static void addBreadcrumb(Breadcrumb b) { }
    public static void addBreadcrumb(Breadcrumb b, Hint h) { }
    public static void addBreadcrumb(String message) { }
    public static void addBreadcrumb(String message, String category) { }
    public static void setExtra(String key, String value) { }
    public static void removeExtra(String key) { }
    public static void configureScope(ScopeCallback cb) { }
    public static void configureScope(ScopeType type, ScopeCallback cb) { }

    public static SentryId captureMessage(String message) {
        System.out.println("[sentry] " + message);
        return null;
    }

    public static SentryId captureMessage(String message, SentryLevel level) { return captureMessage(message); }
    public static SentryId captureMessage(String message, ScopeCallback cb) { return captureMessage(message); }
    public static SentryId captureMessage(String message, SentryLevel level, ScopeCallback cb) { return captureMessage(message); }

    public static SentryId captureException(Throwable t) {
        System.out.println("[sentry] exception: " + t);
        return null;
    }

    public static SentryId captureException(Throwable t, Hint h) { return captureException(t); }
    public static SentryId captureException(Throwable t, ScopeCallback cb) { return captureException(t); }
    public static SentryId captureException(Throwable t, Hint h, ScopeCallback cb) { return captureException(t); }
}
