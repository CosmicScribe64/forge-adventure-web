package forgeweb.fs;

import org.teavm.runtime.fs.VirtualFile;
import org.teavm.runtime.fs.VirtualFileAccessor;

import java.io.IOException;
import java.util.Arrays;

/** A path in {@link WebFileSystem}; resolved on every call, like TeaVM's in-memory VFS. */
final class WebVirtualFile implements VirtualFile {
    private final WebFileSystem fs;
    private final String path;

    WebVirtualFile(WebFileSystem fs, String path) {
        this.fs = fs;
        this.path = path;
    }

    private Node node() {
        return fs.find(path);
    }

    @Override
    public String getName() {
        return path.substring(path.lastIndexOf('/') + 1);
    }

    @Override
    public boolean isDirectory() {
        Node n = node();
        return n != null && n.directory;
    }

    @Override
    public boolean isFile() {
        Node n = node();
        return n != null && !n.directory;
    }

    @Override
    public String[] listFiles() {
        Node n = node();
        return n != null && n.directory ? n.children.keySet().toArray(new String[0]) : null;
    }

    @Override
    public VirtualFileAccessor createAccessor(boolean readable, boolean writable, boolean append) {
        Node n = node();
        if (n == null || n.directory) return null;
        try {
            // Writing to a shipped file makes a local copy (copy-on-write), so load it first.
            fs.ensureLoaded(n);
        } catch (IOException e) {
            System.out.println("[fs] " + e.getMessage());
            return null;
        }
        if (writable) {
            n.remoteUrl = null;
            n.packOffset = -1;
            n.readOnly = false;
        }
        return new Accessor(fs, n, writable, append);
    }

    @Override
    public boolean createFile(String fileName) throws IOException {
        Node n = node();
        if (n == null || !n.directory) throw new IOException("Directory does not exist: " + path);
        if (n.child(fileName) != null) return false;
        Node f = new Node(fileName, false);
        f.data = new byte[0];
        n.addChild(f);
        UserDataStore.changed(f);
        return true;
    }

    @Override
    public boolean createDirectory(String fileName) {
        Node n = node();
        if (n == null || !n.directory || n.child(fileName) != null) return false;
        n.addChild(new Node(fileName, true));
        return true;
    }

    @Override
    public boolean delete() {
        Node n = node();
        if (n == null || n.parent == null || (n.directory && !n.children.isEmpty())) return false;
        n.parent.children.remove(n.name);
        fs.forget(n);
        UserDataStore.deleted(n);
        n.parent = null;
        return true;
    }

    @Override
    public boolean adopt(VirtualFile file, String fileName) {
        Node dir = node();
        Node moved = ((WebVirtualFile) file).node();
        if (dir == null || !dir.directory || moved == null || moved.parent == null) return false;
        UserDataStore.deleted(moved);
        fs.forget(moved);
        moved.parent.children.remove(moved.name);
        Node renamed = new Node(fileName, moved.directory);
        if (moved.directory) {
            for (Node c : moved.children.values()) renamed.addChild(c);
        } else {
            renamed.data = moved.data;
            renamed.size = moved.size;
            renamed.remoteUrl = moved.remoteUrl;
            renamed.packOffset = moved.packOffset;
        }
        dir.addChild(renamed);
        UserDataStore.changed(renamed);
        if (!renamed.directory) fs.written(renamed);
        return true;
    }

    @Override public boolean canRead() { return node() != null; }

    @Override
    public boolean canWrite() {
        Node n = node();
        return n != null && !n.readOnly;
    }

    @Override
    public long lastModified() {
        Node n = node();
        return n != null ? n.lastModified : 0;
    }

    @Override
    public boolean setLastModified(long lastModified) {
        Node n = node();
        if (n == null) return false;
        n.lastModified = lastModified;
        return true;
    }

    @Override
    public boolean setReadOnly(boolean readOnly) {
        Node n = node();
        if (n == null) return false;
        n.readOnly = readOnly;
        return true;
    }

    @Override
    public int length() {
        Node n = node();
        return n != null && !n.directory ? n.size : 0;
    }

    private static final class Accessor implements VirtualFileAccessor {
        private final WebFileSystem fs;
        private final Node n;
        private final boolean writable;
        private int pos;
        private boolean modified;

        Accessor(WebFileSystem fs, Node n, boolean writable, boolean append) {
            this.fs = fs;
            this.n = n;
            this.writable = writable;
            if (append) {
                pos = n.size;
            } else if (writable) {
                n.size = 0;
                modified = true;
            }
        }

        @Override
        public int read(byte[] buffer, int offset, int limit) {
            limit = Math.max(0, Math.min(n.size - pos, limit));
            if (limit > 0) {
                try {
                    fs.touch(n);
                } catch (IOException e) {
                    System.out.println("[fs] " + e.getMessage());
                    return 0;
                }
                System.arraycopy(n.data, pos, buffer, offset, limit);
                pos += limit;
                fs.used(n);
            }
            return limit;
        }

        @Override
        public void write(byte[] buffer, int offset, int limit) {
            expand(pos + limit);
            System.arraycopy(buffer, offset, n.data, pos, limit);
            pos += limit;
            if (pos > n.size) n.size = pos;
            n.lastModified = System.currentTimeMillis();
            modified = true;
        }

        private void expand(int needed) {
            if (n.data == null) n.data = new byte[0];
            if (needed > n.data.length) {
                n.data = Arrays.copyOf(n.data, Math.max(needed, n.data.length * 3 / 2 + 16));
            }
        }

        @Override public int tell() { return pos; }
        @Override public void seek(int target) { pos = target; }
        @Override public void skip(int amount) { pos += amount; }
        @Override public int size() { return n.size; }

        @Override
        public void resize(int size) {
            expand(size);
            n.size = size;
            modified = true;
        }

        @Override
        public void close() {
            if (writable && modified) {
                UserDataStore.changed(n);
                fs.written(n);
            }
        }

        @Override
        public void flush() {
        }
    }
}
