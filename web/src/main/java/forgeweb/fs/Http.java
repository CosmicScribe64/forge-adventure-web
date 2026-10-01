package forgeweb.fs;

import org.teavm.jso.JSBody;
import org.teavm.jso.ajax.XMLHttpRequest;
import org.teavm.jso.typedarrays.Int8Array;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Synchronous HTTP GET. Forge reads files synchronously, often from the UI thread,
 * which can't be suspended; a synchronous XHR is the only way to block there.
 * Binary bodies come through the "x-user-defined" charset trick: one char per byte.
 */
final class Http {
    private Http() {
    }

    static byte[] getBytes(String url) throws IOException {
        Int8Array prefetched = takePrefetched(url);
        if (prefetched != null) {
            return prefetched.copyToJavaArray();
        }
        if (url.startsWith("forge-data/")) {
            // Stored gzipped (scripts/build-webdata); the page normally un-gzips it natively.
            // Already un-gzipped if the server sent it with Content-Encoding: gzip.
            byte[] raw = getRaw(url + ".gz");
            if (raw.length < 2 || raw[0] != (byte) 0x1f || raw[1] != (byte) 0x8b) return raw;
            try (java.util.zip.GZIPInputStream in = new java.util.zip.GZIPInputStream(
                    new java.io.ByteArrayInputStream(raw))) {
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                byte[] buffer = new byte[65536];
                for (int n; (n = in.read(buffer)) > 0; ) out.write(buffer, 0, n);
                return out.toByteArray();
            }
        }
        return getRaw(url);
    }

    private static byte[] getRaw(String url) throws IOException {
        XMLHttpRequest xhr = XMLHttpRequest.create();
        xhr.open("GET", url, false);
        xhr.overrideMimeType("text/plain; charset=x-user-defined");
        xhr.send();
        int status = xhr.getStatus();
        if (status != 200 && status != 0) {
            throw new IOException("HTTP " + status + " for " + url);
        }
        String body = xhr.getResponseText();
        byte[] bytes = new byte[body.length()];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) body.charAt(i);
        }
        return bytes;
    }

    static String getText(String url) throws IOException {
        return new String(getBytes(url), StandardCharsets.UTF_8);
    }

    /** Downloaded by web/html/index.html while app.js loaded; each is handed over once. */
    @JSBody(params = "url", script = "var p = window.forgePrefetch; var b = p && p[url];"
            + " if (!b) return null; delete p[url]; return new Int8Array(b.buffer, b.byteOffset, b.length);")
    private static native Int8Array takePrefetched(String url);
}
