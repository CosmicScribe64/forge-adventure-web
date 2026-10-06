package forgeweb.fs;

import java.util.Arrays;
import org.teavm.jso.JSBody;
import org.teavm.jso.typedarrays.Int8Array;

/**
 * Persists what the game writes under the user data folder (preferences, Adventure saves,
 * decks) in the browser's IndexedDB, through window.forgeUserStore in web/html/index.html.
 * The page reads the stored files before the game starts; {@link #restore} puts them back into
 * the file system, and every write or delete after that is mirrored in the background.
 */
final class UserDataStore {
    /** Only this subtree is persisted; cache/ and the read-only res/ are not. (Keeping Forge's
     *  font cache was tried: loading 65 cached font atlases took as long as generating them.) */
    private static String userRoot = "/forge/data/";

    private UserDataStore() {
    }

    static void setUserRoot(String root) {
        userRoot = root.endsWith("/") ? root : root + "/";
    }

    private static boolean persisted(String path) {
        return path.startsWith(userRoot);
    }

    static void changed(Node n) {
        String path = n.path();
        if (n.directory || !persisted(path)) return;
        byte[] bytes = n.data == null ? new byte[0] : Arrays.copyOf(n.data, n.size);
        put(path, Int8Array.copyFromJavaArray(bytes));
    }

    static void deleted(Node n) {
        String path = n.path();
        if (!persisted(path)) return;
        if (n.directory) {
            deletePrefix(path + "/");
        } else {
            delete(path);
        }
    }

    /** Files the page loaded from IndexedDB, as "path\n..." (see index.html). */
    static void restore(WebFileSystem fs) {
        String listing = storedPaths();
        if (listing == null || listing.isEmpty()) return;
        int count = 0;
        for (String path : listing.split("\n")) {
            if (!persisted(path)) continue;
            Int8Array bytes = takeStored(path);
            if (bytes == null) continue;
            fs.restoreFile(path, bytes.copyToJavaArray());
            count++;
        }
        System.out.println("[fs] restored " + count + " saved files");
    }

    @JSBody(params = {"path", "bytes"}, script = "if (window.forgeUserStore) window.forgeUserStore.put(path, bytes);")
    private static native void put(String path, Int8Array bytes);

    @JSBody(params = "path", script = "if (window.forgeUserStore) window.forgeUserStore.remove(path);")
    private static native void delete(String path);

    @JSBody(params = "prefix", script = "if (window.forgeUserStore) window.forgeUserStore.removePrefix(prefix);")
    private static native void deletePrefix(String prefix);

    @JSBody(script = "var f = window.forgeUserFiles; return f ? Object.keys(f).join('\\n') : null;")
    private static native String storedPaths();

    // Long variable names on purpose, see Http.takePrefetched.
    @JSBody(params = "path", script = "var storedTable = window.forgeUserFiles; var storedBody = storedTable && storedTable[path];"
            + " if (!storedBody) return null; delete storedTable[path];"
            + " return new Int8Array(storedBody.buffer, storedBody.byteOffset, storedBody.length);")
    private static native Int8Array takeStored(String path);
}
