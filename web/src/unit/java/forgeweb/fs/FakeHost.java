package forgeweb.fs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** A fake browser for {@link FileStore}: canned downloads, a clock that only moves when told, and timers. */
final class FakeHost implements FileStore.Host {
    private static final class Timer {
        final long due;
        final Runnable task;

        Timer(long due, Runnable task) {
            this.due = due;
            this.task = task;
        }
    }

    final Map<String, byte[]> files = new HashMap<>();
    /** Every download, in order. */
    final List<String> fetches = new ArrayList<>();
    /** Every recordFetch call, in order. */
    final List<String> recorded = new ArrayList<>();
    long now = 1_000_000;
    private final List<Timer> timers = new ArrayList<>();

    FakeHost serve(String url, int size) {
        byte[] data = new byte[size];
        for (int i = 0; i < size; i++) data[i] = (byte) (i * 31 + url.length());
        files.put(url, data);
        return this;
    }

    int fetchCount(String url) {
        return (int) fetches.stream().filter(url::equals).count();
    }

    @Override
    public byte[] fetch(String url) throws IOException {
        byte[] data = files.get(url);
        if (data == null) throw new IOException("404 " + url);
        fetches.add(url);
        return data;
    }

    @Override
    public long now() {
        return now;
    }

    @Override
    public void schedule(Runnable task, long delayMs) {
        timers.add(new Timer(now + delayMs, task));
    }

    @Override
    public void recordFetch(String path) {
        recorded.add(path);
    }

    int pendingTimers() {
        return timers.size();
    }

    /** Moves the clock forward and runs the timers that come due, in order. */
    void advance(long ms) {
        long target = now + ms;
        while (true) {
            Timer next = null;
            for (Timer t : timers) {
                if (t.due <= target && (next == null || t.due < next.due)) next = t;
            }
            if (next == null) break;
            timers.remove(next);
            now = Math.max(now, next.due);
            next.task.run();
        }
        now = target;
    }
}
