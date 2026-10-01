package forgeweb.compat;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Constructor;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Properties;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * JDK methods missing from TeaVM's class library. forgeweb.teavm.CallRedirector rewrites
 * each call site to the matching static method here (receiver first for instance methods).
 */
public final class JdkCompat {
    private JdkCompat() {
    }

    // java.lang.Runtime
    public static long maxMemory(Runtime rt) { return 2L * 1024 * 1024 * 1024; }
    /**
     * Green threads never run in parallel, so report one CPU: Forge then runs work like card
     * loading inline instead of in a pool, where a failing task hangs a CountDownLatch forever
     * and several tasks share one ZipFile.
     */
    public static int availableProcessors(Runtime rt) { return 1; }
    public static void addShutdownHook(Runtime rt, Thread hook) { }
    public static boolean removeShutdownHook(Runtime rt, Thread hook) { return false; }

    // java.lang.Throwable: TeaVM 0.15 never initializes the suppressed list, so the real
    // addSuppressed throws a null error and hides the primary exception of any
    // try-with-resources whose close() also fails. Log the suppressed one and move on.
    public static void addSuppressed(Throwable primary, Throwable suppressed) {
        if (suppressed != null && suppressed != primary) {
            System.err.println("[suppressed] " + suppressed + " (while throwing " + primary + ")");
        }
    }

    // java.lang.System
    public static Map<String, String> getenv() { return Collections.emptyMap(); }

    // java.lang.Thread.sleep: a browser callback can't pause, so skip the delay there (Forge's
    // sleeps on the UI thread are short pacing delays, e.g. 30 ms before playing a sound).
    public static void sleep(long millis) throws InterruptedException {
        if (MainThread.canSuspend()) Thread.sleep(millis);
    }

    public static void sleep(long millis, int nanos) throws InterruptedException {
        if (MainThread.canSuspend()) Thread.sleep(millis, nanos);
    }

    // java.lang.Thread.getStackTrace: TeaVM returns (almost) nothing, and code like Forge's
    // FThreads.assertExecutedByEdt reads trace[2] (no bounds checks in TeaVM's output, so that was a
    // JS TypeError instead of the IllegalStateException Forge catches). Placeholder frames instead.
    public static StackTraceElement[] getStackTrace(Thread t) {
        StackTraceElement[] frames = new StackTraceElement[8];
        for (int i = 0; i < frames.length; i++) {
            frames[i] = new StackTraceElement("web", "unknown", null, -1);
        }
        return frames;
    }

    // java.lang.Thread.stop can't be emulated on green threads, so interrupt the thread instead.
    public static void stop(Thread t) { t.interrupt(); }

    // java.lang.reflect / java.lang.Package / java.lang.ClassLoader
    public static boolean canAccess(Constructor<?> c, Object obj) { return true; }
    /** Forge falls back to its own version string when this is null. */
    public static String getImplementationVersion(Package p) { return null; }
    public static Enumeration<URL> getResources(ClassLoader cl, String name) { return Collections.emptyEnumeration(); }

    // java.io.File
    public static Path toPath(File f) { return Paths.get(f.getPath()); }

    // java.util.Collections
    public static <K, V> NavigableMap<K, V> emptyNavigableMap() { return new TreeMap<>(); }
    public static <T> SortedSet<T> unmodifiableSortedSet(SortedSet<T> s) { return new ReadOnlySortedSet<>(s); }

    // java.util.Collection: no parallelism on web.
    public static <E> Stream<E> parallelStream(Collection<E> c) { return c.stream(); }

    // java.util.Date
    public static Date from(Instant instant) { return new Date(instant.toEpochMilli()); }
    public static Instant toInstant(Date d) { return Instant.ofEpochMilli(d.getTime()); }

    // java.util.UUID.randomUUID: TeaVM calls crypto.randomUUID, which browsers only offer on
    // secure pages (https, localhost). crypto.getRandomValues works everywhere.
    public static UUID randomUUID() {
        byte[] b = randomBytes16();
        b[6] = (byte) ((b[6] & 0x0f) | 0x40); // version 4
        b[8] = (byte) ((b[8] & 0x3f) | 0x80); // IETF variant
        // TeaVM's UUID has no (long, long) constructor; go through the canonical string.
        StringBuilder sb = new StringBuilder(36);
        for (int i = 0; i < 16; i++) {
            if (i == 4 || i == 6 || i == 8 || i == 10) sb.append('-');
            sb.append(Character.forDigit((b[i] >> 4) & 0xf, 16)).append(Character.forDigit(b[i] & 0xf, 16));
        }
        return UUID.fromString(sb.toString());
    }

    private static byte[] randomBytes16() {
        return randomInt8Array16().copyToJavaArray();
    }

    @org.teavm.jso.JSBody(script = "return crypto.getRandomValues(new Int8Array(16));")
    private static native org.teavm.jso.typedarrays.Int8Array randomInt8Array16();

    // java.util.UUID: parsed back from the canonical string, so it matches the JDK exactly.
    public static long getMostSignificantBits(UUID u) {
        return parseHex64(u.toString().replace("-", "").substring(0, 16));
    }

    public static long getLeastSignificantBits(UUID u) {
        return parseHex64(u.toString().replace("-", "").substring(16));
    }

    /** 16 hex digits to a (possibly negative) long; Long.parseUnsignedLong is also missing. */
    private static long parseHex64(String hex) {
        long v = 0;
        for (int i = 0; i < hex.length(); i++) {
            v = (v << 4) | Character.digit(hex.charAt(i), 16);
        }
        return v;
    }

    // java.util.Properties
    public static void store(Properties props, Writer writer, String comments) throws IOException {
        if (comments != null) {
            writer.write("#" + comments + "\n");
        }
        for (Map.Entry<Object, Object> e : props.entrySet()) {
            writer.write(escapeProperty(String.valueOf(e.getKey()), true) + "="
                    + escapeProperty(String.valueOf(e.getValue()), false) + "\n");
        }
        writer.flush();
    }

    private static String escapeProperty(String s, boolean key) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                case '=': case ':': case '#': case '!': sb.append('\\').append(c); break;
                case ' ':
                    if (key || i == 0) sb.append('\\');
                    sb.append(c);
                    break;
                default: sb.append(c);
            }
        }
        return sb.toString();
    }

    // Formatting: TeaVM's Formatter has no %n (platform line separator).
    private static String fixFormat(String format) {
        return format == null ? null : format.replace("%n", "\n");
    }

    public static java.io.PrintStream printf(java.io.PrintStream ps, String format, Object[] args) {
        return ps.printf(fixFormat(format), args);
    }

    public static java.io.PrintStream printf(java.io.PrintStream ps, java.util.Locale l, String format, Object[] args) {
        return ps.printf(l, fixFormat(format), args);
    }

    public static java.io.PrintStream format(java.io.PrintStream ps, String format, Object[] args) {
        return ps.format(fixFormat(format), args);
    }

    public static String format(String format, Object[] args) {
        return String.format(fixFormat(format), args);
    }

    public static String format(java.util.Locale l, String format, Object[] args) {
        return String.format(l, fixFormat(format), args);
    }

    public static String formatted(String format, Object[] args) {
        return String.format(fixFormat(format), args);
    }

    // java.net: the browser picks the proxy.
    public static URLConnection openConnection(URL url, Proxy proxy) throws IOException { return url.openConnection(); }
    public static long getContentLengthLong(HttpURLConnection c) { return c.getContentLength(); }
    public static long getContentLengthLong(URLConnection c) { return c.getContentLength(); }
}
