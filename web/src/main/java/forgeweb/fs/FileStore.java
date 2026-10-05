package forgeweb.fs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The part of {@link WebFileSystem} that has no browser in it: the tree of nodes, the manifest,
 * packs, lazy downloads and the trim policy. Everything it needs from the outside (downloads,
 * the clock, timers) comes from a {@link Host}, so JUnit tests run it with fakes.
 */
class FileStore {
    /** What the file store needs from the browser. */
    interface Host {
        byte[] fetch(String url) throws IOException;

        long now();

        /** Runs the task once, after at least delayMs milliseconds. */
        void schedule(Runnable task, long delayMs);

        /** Notes that a game file is being read (window.forgeFetched). */
        void recordFetch(String path);
    }

    private final Host host;
    final Node root = new Node("", true);
    /** Downloaded packs by URL (see scripts/build-webdata). Small, and read again and again. */
    final Map<String, byte[]> packs = new HashMap<>();
    /** Files of each pack that have not been read yet; a pack is forgotten when this reaches 0. */
    private final Map<String, int[]> packUnread = new HashMap<>();
    /** Remote files of at least {@link #BIG} bytes whose contents are in memory (see {@link #trim}). */
    private final List<Node> bigLoaded = new ArrayList<>();
    private long lastBigUse;
    private boolean trimScheduled;
    private int remoteFetches;
    private long remoteBytes;
    /** See {@link #capFolder}. In access order, so the eldest entry is the least recently used file. */
    private final LinkedHashMap<Node, Integer> capped = new LinkedHashMap<>(64, 0.75f, true);
    private String cappedPrefix;
    private long cappedMax;
    private long cappedBytes;
    private int cappedDropped;


    FileStore(Host host) {
        this.host = host;
    }

    void mountManifest(String mountPoint, String manifest, String baseUrl) {
        Node mount = mkdirs(mountPoint);
        int files = 0;
        long total = 0;
        for (String line : manifest.split("\n")) {
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] cols = line.split("\t");
            String rel = cols[0];
            int size = Integer.parseInt(cols[1].trim());
            String url = cols.length > 2 ? cols[2].trim() : baseUrl + rel;
            int slash = rel.lastIndexOf('/');
            Node dir = slash < 0 ? mount : mkdirs(mount, rel.substring(0, slash));
            Node file = new Node(rel.substring(slash + 1), false);
            file.size = size;
            file.remoteUrl = url;
            if (cols.length > 3) {
                file.packOffset = Integer.parseInt(cols[3].trim());
                packUnread.computeIfAbsent(url, k -> new int[1])[0]++;
            }
            file.readOnly = true;
            dir.addChild(file);
            files++;
            total += size;
        }
        System.out.println("[fs] mounted " + files + " files (" + (total / (1024 * 1024)) + " MB) at " + mountPoint);
    }

    // --- path handling ---

    static List<String> segments(String path) {
        List<String> out = new ArrayList<>();
        for (String s : path.split("/")) {
            if (s.isEmpty() || s.equals(".")) continue;
            if (s.equals("..")) {
                if (!out.isEmpty()) out.remove(out.size() - 1);
            } else {
                out.add(s);
            }
        }
        return out;
    }

    Node find(String path) {
        Node n = root;
        for (String s : segments(path)) {
            n = n.child(s);
            if (n == null) return null;
        }
        return n;
    }

    Node mkdirs(String path) {
        return mkdirs(root, path);
    }

    /** Puts back a file saved in an earlier session (UserDataStore). */
    void restoreFile(String path, byte[] data) {
        int slash = path.lastIndexOf('/');
        Node dir = mkdirs(path.substring(0, slash));
        String name = path.substring(slash + 1);
        Node file = dir.child(name);
        if (file == null || file.directory) {
            file = dir.addChild(new Node(name, false));
        }
        file.data = data;
        file.size = data.length;
        file.remoteUrl = null;
        file.packOffset = -1;
        file.readOnly = false;
    }

    /** Creates a directory and its parents (for folders the game expects to exist). */
    public void ensureDirectory(String path) {
        mkdirs(path);
    }

    private static Node mkdirs(Node from, String path) {
        Node n = from;
        for (String s : segments(path)) {
            Node c = n.child(s);
            if (c == null) c = n.addChild(new Node(s, true));
            n = c;
        }
        return n;
    }

    /**
     * Bounds the files under a folder that the game writes at runtime and could download again
     * (card pictures from Scryfall in cache/pics/: every picture ever shown stayed in memory, about
     * 100 KB each). Over {@code maxBytes}, the least recently read or written files are deleted
     * from the tree. Forge downloads a missing picture again when it next looks for it as a new card
     * image (a list item that already looked keeps showing its placeholder). A reader that already
     * has the file open keeps reading it.
     */
    public void capFolder(String folder, long maxBytes) {
        cappedPrefix = folder.endsWith("/") ? folder : folder + "/";
        cappedMax = maxBytes;
    }

    private boolean inCappedFolder(Node n) {
        return cappedPrefix != null && !n.directory && (n.path() + "/").startsWith(cappedPrefix);
    }

    /** Called when a file's contents were written or moved into place (not while it is being written). */
    void written(Node n) {
        if (!inCappedFolder(n)) return;
        // Writing grows the buffer by half each time it fills; the finished file needs no spare room.
        if (n.data != null && n.data.length > n.size) n.data = Arrays.copyOf(n.data, n.size);
        Integer old = capped.put(n, n.size);
        cappedBytes += n.size - (old == null ? 0 : old);
        while (cappedBytes > cappedMax && capped.size() > 1) {
            Node eldest = capped.keySet().iterator().next();
            if (eldest == n) break;
            forget(eldest);
            // Once, then every 100th: a long deck editor session would otherwise log one line per picture.
            if (cappedDropped++ % 100 == 0) {
                System.out.println("[fs] picture cache over " + (cappedMax >> 20) + " MB: dropped " + cappedDropped
                        + " pictures so far, " + (cappedBytes >> 10) + " KB kept in " + capped.size() + " files");
            }
            if (eldest.parent != null) {
                eldest.parent.children.remove(eldest.name);
                eldest.parent = null;
            }
        }
    }

    /** Called when a capped file is read: it is the most recently used now. */
    void used(Node n) {
        if (cappedPrefix != null) capped.get(n);
    }

    /** Called when a file leaves the tree by deletion or by being moved. */
    void forget(Node n) {
        Integer old = capped.remove(n);
        if (old != null) cappedBytes -= old;
    }

    /** Bytes of the capped folder's files that are in memory. */
    public long cappedBytes() {
        return cappedBytes;
    }

    /** JSON for the test harness: files and KB held in the capped folder, and files dropped so far. */
    public String cappedStats() {
        return "{\"files\":" + capped.size() + ",\"kb\":" + (cappedBytes >> 10) + ",\"dropped\":" + cappedDropped + "}";
    }

    /** Files and packs from this size up are dropped again once idle, see {@link #trim}. */
    private static final int BIG = 1 << 20;
    private static final int IDLE_MS = 5000;

    /** Downloads a remote file's contents on first use (a whole pack for packed files). */
    void ensureLoaded(Node n) throws IOException {
        if (n.data != null || n.remoteUrl == null) return;
        host.recordFetch(n.path());
        if (n.packOffset >= 0) {
            byte[] pack = packs.get(n.remoteUrl);
            if (pack == null) {
                pack = fetch(n.remoteUrl);
                packs.put(n.remoteUrl, pack);
            }
            n.data = Arrays.copyOfRange(pack, n.packOffset, n.packOffset + n.size);
            if (n.size >= BIG) {
                bigLoaded.add(n);
                lastBigUse = host.now();
                scheduleTrim(IDLE_MS);
            }
            int[] unread = packUnread.get(n.remoteUrl);
            if (!n.packCounted && unread != null) {
                n.packCounted = true;
                if (--unread[0] <= 0) packs.remove(n.remoteUrl);
            }
            return;
        }
        byte[] bytes = fetch(n.remoteUrl);
        n.data = bytes;
        n.size = bytes.length;
        if (n.size >= BIG && n.readOnly) {
            bigLoaded.add(n);
            lastBigUse = host.now();
            scheduleTrim(IDLE_MS);
        }
    }

    /** Called on every read of a file: a big file dropped by {@link #trim} comes back, and its
     *  idle time starts again. */
    void touch(Node n) throws IOException {
        if (n.data == null) ensureLoaded(n);
        else if (n.size >= BIG && n.remoteUrl != null) lastBigUse = host.now();
    }

    /**
     * A pack is forgotten as soon as every file in it has been read (files copied out of it keep
     * their own data), so a pack with files still unread stays: dropping it earlier made the
     * startup pack download again between the menu and the world. Big read-only files (the 27 MB
     * card script zip, read once at startup because lazy card loading is off on mobile) drop
     * their contents once nothing has used them for {@link #IDLE_MS}; they are downloaded again
     * if something reads them later (an open file keeps its Node, and {@link #touch} reloads it).
     */
    private void trim() {
        trimScheduled = false;
        long idle = host.now() - lastBigUse;
        if (idle < IDLE_MS) {
            scheduleTrim(IDLE_MS - idle);
            return;
        }
        trimNow();
    }

    /** Drops the big files' contents now (the idle timer calls this; the self test too). */
    public void trimNow() {
        for (Node n : bigLoaded) {
            if (n.remoteUrl != null && n.readOnly) n.data = null;
        }
        bigLoaded.clear();
    }

    private void scheduleTrim(long delayMs) {
        if (trimScheduled) return;
        trimScheduled = true;
        host.schedule(this::trim, delayMs + 50);
    }

    private byte[] fetch(String url) throws IOException {
        byte[] bytes = host.fetch(url);
        remoteFetches++;
        remoteBytes += bytes.length;
        return bytes;
    }

    public int remoteFetches() {
        return remoteFetches;
    }

    public String stats() {
        return remoteFetches + " files / " + (remoteBytes / 1024) + " KB downloaded";
    }

    /** The canonical absolute form of a path: no empty, "." or ".." segments. */
    public String canonicalize(String path) {
        return "/" + String.join("/", segments(path));
    }
}
