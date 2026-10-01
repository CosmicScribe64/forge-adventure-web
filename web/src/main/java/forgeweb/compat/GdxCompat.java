package forgeweb.compat;

import com.badlogic.gdx.Files;
import com.badlogic.gdx.Files.FileType;
import com.badlogic.gdx.files.FileHandle;

import java.io.File;
import java.nio.Buffer;
import java.nio.ByteBuffer;

/** libGDX methods missing from gdx-teavm's emulation (see CallRedirector). */
public final class GdxCompat {
    private GdxCompat() {
    }

    // Time since the previous frame the game rendered. gdx-teavm measures it between browser
    // animation frames, but frames arriving while the UI thread is busy are dropped (UiThread), so
    // the next rendered frame saw about 16 ms instead of the real gap and animations crawled while the
    // game worked (the splash logo's zoom, transitions). Capped so a long freeze doesn't jump.
    private static double lastFrameAt = -1;
    private static float frameDelta = 1 / 60f;

    static void frameStarted(double nowMs) {
        if (lastFrameAt >= 0) {
            frameDelta = (float) Math.min(0.25, Math.max(0, (nowMs - lastFrameAt) / 1000.0));
        }
        lastFrameAt = nowMs;
    }

    public static float getDeltaTime(com.badlogic.gdx.Graphics graphics) {
        return frameDelta;
    }

    public static float getRawDeltaTime(com.badlogic.gdx.Graphics graphics) {
        return frameDelta;
    }

    /** Gdx.app.postRunnable: run on the UI thread (the browser's frame callback can't block). */
    public static void postRunnable(com.badlogic.gdx.Application app, Runnable r) {
        UiThread.post(r);
    }

    /** Wraps the game's input processor so input callbacks run as the main thread. */
    public static void setInputProcessor(com.badlogic.gdx.Input input, com.badlogic.gdx.InputProcessor processor) {
        input.setInputProcessor(processor == null || processor instanceof MainThreadInputProcessor
                ? processor : new MainThreadInputProcessor(processor));
    }

    public static com.badlogic.gdx.InputProcessor getInputProcessor(com.badlogic.gdx.Input input) {
        com.badlogic.gdx.InputProcessor p = input.getInputProcessor();
        return p instanceof MainThreadInputProcessor ? ((MainThreadInputProcessor) p).p : p;
    }

    /** There is no native memory on web, so no buffer is an "unsafe" one. */
    public static boolean isUnsafeByteBuffer(ByteBuffer buffer) { return false; }
    public static long getUnsafeBufferAddress(Buffer buffer) { return 0; }

    /**
     * gdx-teavm's WebFiles has no absolute or external files. Ours read and write through
     * java.io, which is backed by forgeweb.fs.WebFileSystem.
     */
    public static FileHandle absolute(Files files, String path) {
        return new FileHandle(new File(path));
    }

    public static FileHandle external(Files files, String path) {
        return new FileHandle(new File("/forge/external", path));
    }

    /**
     * gdx-teavm's AssetManager downloads every asset into its own store before loading it.
     * Absolute/external files come from our java.io file system instead, so they're "loaded".
     */
    public static boolean isAssetLoaded(com.github.xpenatan.gdx.teavm.backends.web.assetloader.AssetLoader loader,
                                        FileType type, String path) {
        if (type == FileType.Absolute || type == FileType.External) return true;
        return loader.isAssetLoaded(type, path);
    }

    public static FileHandle getFileHandle(Files files, String path, FileType type) {
        if (type == FileType.Absolute) return absolute(files, path);
        if (type == FileType.External) return external(files, path);
        return files.getFileHandle(path, type);
    }
}
