package forgeweb.fs;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FileStoreTest {
    private static final int MB = 1 << 20;

    private FakeHost host;
    private FileStore fs;

    @BeforeEach
    void setUp() {
        host = new FakeHost();
        fs = new FileStore(host);
    }

    private void mount(String manifest) {
        fs.mountManifest("/forge/", manifest, "base/");
    }

    private Node file(String path) {
        Node n = fs.find(path);
        assertNotNull(n, path);
        return n;
    }

    // --- paths ---

    @Test
    void segmentsDropEmptyAndDotParts() {
        assertEquals(List.of("a", "b"), FileStore.segments("//a/./b/"));
        assertEquals(List.of(), FileStore.segments("/"));
    }

    @Test
    void dotDotRemovesTheParentAndStopsAtTheRoot() {
        assertEquals(List.of("a", "c"), FileStore.segments("/a/b/../c"));
        assertEquals(List.of("x"), FileStore.segments("/../../x"));
    }

    @Test
    void canonicalizeGivesAnAbsolutePath() {
        assertEquals("/", fs.canonicalize(""));
        assertEquals("/", fs.canonicalize("/a/.."));
        assertEquals("/forge/res/x.txt", fs.canonicalize("/forge//res/./adventure/../x.txt"));
        assertEquals("/a/b", fs.canonicalize("a/b/"));
    }

    @Test
    void mkdirsReusesExistingDirectories() {
        Node a = fs.mkdirs("/x/y");
        assertSame(a, fs.mkdirs("/x/./y/"));
        assertSame(a.parent, fs.find("/x"));
        assertNull(fs.find("/x/z"));
    }

    // --- manifest ---

    @Test
    void manifestBuildsDirectoriesAndFilesWithoutDownloading() {
        mount("# comment\n\nres/a.txt\t10\nres/deep/b.txt\t20\t https://cdn/b.txt \nc.txt\t5\n");
        assertTrue(file("/forge/res/deep").directory);
        assertEquals(10, file("/forge/res/a.txt").size);
        assertEquals("base/res/a.txt", file("/forge/res/a.txt").remoteUrl);
        assertEquals("https://cdn/b.txt", file("/forge/res/deep/b.txt").remoteUrl);
        assertEquals(-1, file("/forge/res/a.txt").packOffset);
        assertTrue(file("/forge/c.txt").readOnly);
        assertNull(file("/forge/c.txt").data);
        assertTrue(host.fetches.isEmpty());
    }

    @Test
    void manifestReadsPackOffsets() {
        mount("a\t3\tpack\t0\nb\t4\tpack\t3\n");
        assertEquals(0, file("/forge/a").packOffset);
        assertEquals(3, file("/forge/b").packOffset);
        assertEquals("pack", file("/forge/b").remoteUrl);
    }

    // --- plain remote files ---

    @Test
    void remoteFileIsDownloadedOnFirstUseOnly() throws IOException {
        host.serve("base/a", 100);
        mount("a\t100\n");
        Node a = file("/forge/a");
        fs.ensureLoaded(a);
        fs.ensureLoaded(a);
        fs.touch(a);
        assertEquals(1, host.fetchCount("base/a"));
        assertEquals(1, fs.remoteFetches());
        assertArrayEquals(host.files.get("base/a"), a.data);
        assertEquals(List.of("/forge/a"), host.recorded.subList(0, 1));
    }

    @Test
    void sizeIsCorrectedToWhatWasDownloaded() throws IOException {
        host.serve("base/a", 7);
        mount("a\t100\n");
        fs.ensureLoaded(file("/forge/a"));
        assertEquals(7, file("/forge/a").size);
    }

    @Test
    void statsCountFilesAndKilobytes() throws IOException {
        host.serve("base/a", 3 * 1024).serve("base/b", 2 * 1024);
        mount("a\t1\nb\t1\n");
        fs.ensureLoaded(file("/forge/a"));
        fs.ensureLoaded(file("/forge/b"));
        assertEquals(2, fs.remoteFetches());
        assertEquals("2 files / 5 KB downloaded", fs.stats());
    }

    @Test
    void failedDownloadIsNotCountedAndCanBeRetried() throws IOException {
        mount("a\t1\n");
        assertThrows(IOException.class, () -> fs.ensureLoaded(file("/forge/a")));
        assertEquals(0, fs.remoteFetches());
        assertNull(file("/forge/a").data);
        host.serve("base/a", 1);
        fs.ensureLoaded(file("/forge/a"));
        assertEquals(1, fs.remoteFetches());
    }

    @Test
    void filesWithoutAUrlAreNeverFetched() throws IOException {
        fs.restoreFile("/forge/data/save.bin", new byte[] {1, 2, 3});
        Node n = file("/forge/data/save.bin");
        fs.ensureLoaded(n);
        fs.touch(n);
        assertTrue(host.fetches.isEmpty());
        assertEquals(3, n.size);
        assertFalse(n.readOnly);
    }

    @Test
    void restoreFileReplacesAShippedFile() throws IOException {
        host.serve("base/a", 5);
        mount("a\t5\n");
        fs.restoreFile("/forge/a", new byte[] {9});
        Node n = file("/forge/a");
        assertNull(n.remoteUrl);
        assertEquals(-1, n.packOffset);
        assertArrayEquals(new byte[] {9}, n.data);
        fs.touch(n);
        assertTrue(host.fetches.isEmpty());
    }

    // --- packs ---

    @Test
    void aPackIsDownloadedOnceForAllItsFiles() throws IOException {
        host.serve("pack", 7);
        mount("a\t3\tpack\t0\nb\t4\tpack\t3\n");
        fs.ensureLoaded(file("/forge/a"));
        fs.ensureLoaded(file("/forge/b"));
        assertEquals(1, host.fetchCount("pack"));
        assertArrayEquals(Arrays.copyOfRange(host.files.get("pack"), 0, 3), file("/forge/a").data);
        assertArrayEquals(Arrays.copyOfRange(host.files.get("pack"), 3, 7), file("/forge/b").data);
        assertEquals(2, host.recorded.size());
    }

    @Test
    void packIsKeptWhileFilesInItAreUnread() throws IOException {
        host.serve("pack", 9);
        mount("a\t3\tpack\t0\nb\t3\tpack\t3\nc\t3\tpack\t6\n");
        fs.ensureLoaded(file("/forge/a"));
        fs.ensureLoaded(file("/forge/b"));
        assertTrue(fs.packs.containsKey("pack"));
    }

    @Test
    void packIsDroppedOnceEveryFileIsRead() throws IOException {
        host.serve("pack", 9);
        mount("a\t3\tpack\t0\nb\t3\tpack\t3\nc\t3\tpack\t6\n");
        for (String name : new String[] {"a", "b", "c"}) fs.ensureLoaded(file("/forge/" + name));
        assertFalse(fs.packs.containsKey("pack"));
        assertEquals(1, host.fetchCount("pack"));
    }

    @Test
    void readingTheSameFileTwiceDoesNotCountItTwice() throws IOException {
        host.serve("pack", 6);
        mount("a\t3\tpack\t0\nb\t3\tpack\t3\n");
        Node a = file("/forge/a");
        fs.ensureLoaded(a);
        a.data = null; // as if it were dropped; it must not count as the last unread file
        fs.ensureLoaded(a);
        assertTrue(fs.packs.containsKey("pack"));
        assertEquals(1, host.fetchCount("pack"));
    }

    /** The bug: the startup pack was forgotten when idle, so the new game downloaded it again. */
    @Test
    void packIsNotRedownloadedAfterALongPauseWhileFilesAreUnread() throws IOException {
        host.serve("pack", 3 * MB);
        mount("a\t" + MB + "\tpack\t0\nb\t" + MB + "\tpack\t" + MB + "\nc\t" + MB + "\tpack\t" + 2 * MB + "\n");
        fs.ensureLoaded(file("/forge/a"));
        fs.ensureLoaded(file("/forge/b"));
        host.advance(10 * 60_000); // sits at the menu
        fs.ensureLoaded(file("/forge/c"));
        assertEquals(1, host.fetchCount("pack"));
        assertEquals(1, fs.remoteFetches());
    }

    @Test
    void bigPackedFilesAreDroppedWhenIdleAndComeBackFromANewDownload() throws IOException {
        host.serve("pack", 2 * MB);
        mount("a\t" + MB + "\tpack\t0\nb\t" + MB + "\tpack\t" + MB + "\n");
        fs.ensureLoaded(file("/forge/a"));
        fs.ensureLoaded(file("/forge/b"));
        host.advance(6000);
        assertNull(file("/forge/a").data);
        assertNull(file("/forge/b").data);
        fs.touch(file("/forge/a"));
        assertEquals(MB, file("/forge/a").data.length);
        assertEquals(2, host.fetchCount("pack")); // every file had been read, so the pack was gone
    }

    // --- trim ---

    @Test
    void smallFilesAreNeverTrimmed() throws IOException {
        host.serve("base/s", MB - 1);
        mount("s\t" + (MB - 1) + "\n");
        fs.ensureLoaded(file("/forge/s"));
        assertEquals(0, host.pendingTimers());
        host.advance(60_000);
        assertNotNull(file("/forge/s").data);
    }

    @Test
    void bigFileIsDroppedAfterTheIdleTime() throws IOException {
        host.serve("base/zip", 2 * MB);
        mount("zip\t" + 2 * MB + "\n");
        fs.ensureLoaded(file("/forge/zip"));
        host.advance(4900);
        assertNotNull(file("/forge/zip").data);
        host.advance(300);
        assertNull(file("/forge/zip").data);
        assertEquals(2 * MB, file("/forge/zip").size); // the size stays for listings and seeks
    }

    @Test
    void touchKeepsABigFileAliveAndTheTrimTimerWaits() throws IOException {
        host.serve("base/zip", 2 * MB);
        mount("zip\t" + 2 * MB + "\n");
        Node zip = file("/forge/zip");
        fs.ensureLoaded(zip);
        host.advance(4000);
        fs.touch(zip);
        host.advance(2000); // the first timer fires 5 s in, but the file was used 1 s before
        assertNotNull(zip.data);
        assertEquals(1, host.pendingTimers());
        host.advance(4200);
        assertNull(zip.data);
        assertEquals(0, host.pendingTimers());
    }

    @Test
    void onlyOneTrimTimerIsPendingNoMatterHowManyBigFilesLoad() throws IOException {
        host.serve("base/a", MB).serve("base/b", MB);
        mount("a\t" + MB + "\nb\t" + MB + "\n");
        fs.ensureLoaded(file("/forge/a"));
        fs.ensureLoaded(file("/forge/b"));
        assertEquals(1, host.pendingTimers());
    }

    /** The bug: the card zip was read after the trim; it must reload once, not on every read. */
    @Test
    void readAfterTrimReloadsOnceAndThenStaysLoaded() throws IOException {
        host.serve("base/cards.zip", 3 * MB);
        mount("cards.zip\t" + 3 * MB + "\n");
        Node zip = file("/forge/cards.zip");
        fs.ensureLoaded(zip);
        host.advance(30_000);
        assertNull(zip.data);
        for (int i = 0; i < 50; i++) {
            fs.touch(zip);
            host.advance(10);
        }
        assertNotNull(zip.data);
        assertEquals(2, host.fetchCount("base/cards.zip"));
        assertEquals(2, fs.remoteFetches());
    }

    @Test
    void reloadedBigFileIsTrimmedAgainWhenIdle() throws IOException {
        host.serve("base/zip", 2 * MB);
        mount("zip\t" + 2 * MB + "\n");
        Node zip = file("/forge/zip");
        fs.ensureLoaded(zip);
        host.advance(10_000);
        fs.touch(zip);
        assertNotNull(zip.data);
        host.advance(10_000);
        assertNull(zip.data);
        assertEquals(2, host.fetchCount("base/zip"));
    }

    @Test
    void trimNowDropsEveryBigFileAtOnce() throws IOException {
        host.serve("base/zip", 2 * MB);
        mount("zip\t" + 2 * MB + "\n");
        fs.ensureLoaded(file("/forge/zip"));
        fs.trimNow();
        assertNull(file("/forge/zip").data);
    }

    @Test
    void aBigFileThatWasWrittenIsNeverDropped() throws IOException {
        host.serve("base/big", 2 * MB);
        mount("big\t" + 2 * MB + "\n");
        Node big = file("/forge/big");
        fs.ensureLoaded(big);
        // what WebVirtualFile.createAccessor does when the file is opened for writing
        big.remoteUrl = null;
        big.packOffset = -1;
        big.readOnly = false;
        host.advance(60_000);
        assertNotNull(big.data);
        assertEquals(1, host.fetches.size());
    }

    @Test
    void touchOnALoadedSmallFileSchedulesNothing() throws IOException {
        host.serve("base/s", 10);
        mount("s\t10\n");
        fs.ensureLoaded(file("/forge/s"));
        fs.touch(file("/forge/s"));
        assertEquals(1, fs.remoteFetches());
        assertEquals(0, host.pendingTimers());
    }
}
