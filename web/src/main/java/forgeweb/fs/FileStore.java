package forgeweb.fs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
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
