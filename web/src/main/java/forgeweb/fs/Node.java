package forgeweb.fs;

import java.util.Map;
import java.util.TreeMap;

/** A file or directory in {@link WebFileSystem}. */
final class Node {
    final String name;
    Node parent;
    final boolean directory;
    final Map<String, Node> children;

    // Files only. A remote file has remoteUrl set and data == null until first read.
    byte[] data;
    int size;
    String remoteUrl;
    /** If >= 0, remoteUrl is a pack and this file is size bytes at this offset in it. */
    int packOffset = -1;
    /** Set once this file has been counted as read in its pack (see FileStore.packUnread). */
    boolean packCounted;
    long lastModified = System.currentTimeMillis();
    boolean readOnly;

    Node(String name, boolean directory) {
        this.name = name;
        this.directory = directory;
        this.children = directory ? new TreeMap<>() : null;
    }

    String path() {
        if (parent == null) return "";
        return parent.path() + "/" + name;
    }

    Node child(String childName) {
        return directory ? children.get(childName) : null;
    }

    Node addChild(Node child) {
        child.parent = this;
        children.put(child.name, child);
        lastModified = System.currentTimeMillis();
        return child;
    }
}
