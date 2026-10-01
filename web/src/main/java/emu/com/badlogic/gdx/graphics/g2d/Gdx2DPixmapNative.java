package emu.com.badlogic.gdx.graphics.g2d;

import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.github.xpenatan.gdx.teavm.backends.web.dom.typedarray.TypedArrays;
import java.nio.ByteBuffer;

import java.nio.ByteOrder;
import org.teavm.classlib.impl.nio.Buffers;
import org.teavm.jso.JSBody;
import org.teavm.jso.typedarrays.Int32Array;
import org.teavm.jso.typedarrays.Int8Array;

// Shadows gdx-teavm 1.6.1's own copy (project classes come first on the classpath).
// Change: every drawing call used to copy the whole pixmap from the wasm heap into `buffer`
// (copyHeapToBuffer). Forge's World.generateNew draws 490,000 tiles into a 2800x2800 minimap,
// so that was about 31 MB copied per tile and world creation never finished. Now drawing only marks
// `buffer` stale, and getBuffer()/copyToHeap() bring it up to date when it is actually used.
// (Code that keeps the ByteBuffer from an earlier getBuffer() must call it again after drawing.)
// Also: the JS-side mirror `buffer` is only created by getBuffer() (texture upload, pixel access),
// so pixmaps that are only drawn into don't keep a second copy of their pixels.
// Mirror states: with `buffer` null there is no mirror and the heap is the truth; with bufferStale
// set, the heap is newer; otherwise the two are in sync.
public class Gdx2DPixmapNative implements Disposable {

    int basePtr;
    int width;
    int height;
    int format;
    int heapStartIndex;
    int heapEndIndex;

    private Int32Array nativeData;
    private ByteBuffer buffer;
    private boolean bufferStale;

    // Memory accounting, published as window.forgePixmaps (see webtest's heap step).
    private static int liveCount, peakCount;
    private static double heapBytes, peakHeapBytes, mirrorBytes, peakMirrorBytes;
    private int countedHeap, countedMirror;

    private void account(int heapDelta, int mirrorDelta) {
        heapBytes += heapDelta;
        mirrorBytes += mirrorDelta;
        countedHeap += heapDelta;
        countedMirror += mirrorDelta;
        peakHeapBytes = Math.max(peakHeapBytes, heapBytes);
        peakMirrorBytes = Math.max(peakMirrorBytes, mirrorBytes);
        peakCount = Math.max(peakCount, liveCount);
        publish(liveCount, peakCount, heapBytes / 1048576, peakHeapBytes / 1048576, mirrorBytes / 1048576,
                peakMirrorBytes / 1048576);
    }

    @JSBody(params = {"live", "peakLive", "heap", "peakHeap", "mirror", "peakMirror"},
            script = "window.forgePixmaps = {live: live, peakLive: peakLive, heapMB: Math.round(heap),"
                    + " peakHeapMB: Math.round(peakHeap), mirrorMB: Math.round(mirror), peakMirrorMB: Math.round(peakMirror)};")
    private static native void publish(int live, int peakLive, double heap, double peakHeap, double mirror, double peakMirror);

    public Gdx2DPixmapNative(byte[] encodedData, int offset, int len, int requestedFormat) {
        nativeData = loadNative(encodedData, offset, len);
        updateNativeData();

        if(requestedFormat != 0 && requestedFormat != format) {
            convert(requestedFormat);
        }
    }

    /**
     * @throws GdxRuntimeException if allocation failed.
     */
    public Gdx2DPixmapNative(int width, int height, int format) throws GdxRuntimeException {
        nativeData = newPixmapNative(width, height, format);
        updateNativeData();
    }

    private void updateNativeData() {
        this.basePtr = nativeData.get(0);
        this.width = nativeData.get(1);
        this.height = nativeData.get(2);
        this.format = nativeData.get(3);
        this.heapStartIndex = nativeData.get(4);
        this.heapEndIndex = nativeData.get(5);
        liveCount++;
        account(heapEndIndex - heapStartIndex, 0);
        if (heapEndIndex - heapStartIndex >= 256 * 1024) {
            trackLarge(basePtr, width, height, heapEndIndex - heapStartIndex);
        }
    }

    // Large live pixmaps with the stack that allocated them: window.forgeLargePixmaps (Map).
    @JSBody(params = {"ptr", "w", "h", "bytes"}, script = "var m = window.forgeLargePixmaps"
            + " || (window.forgeLargePixmaps = new Map());"
            + " Error.stackTraceLimit = 30; m.set(ptr, {w: w, h: h, mb: Math.round(bytes / 10485.76) / 100, stack: new Error().stack});")
    private static native void trackLarge(int ptr, int w, int h, int bytes);

    @JSBody(params = "ptr", script = "if (window.forgeLargePixmaps) window.forgeLargePixmaps.delete(ptr);")
    private static native void untrackLarge(int ptr);

    private void copyHeapToBuffer() {
        Int8Array heapData = getHeapData(false);
        if(buffer == null) {
            int length = heapData.getLength();
            buffer = ByteBuffer.allocateDirect(length);
            buffer.order(ByteOrder.BIG_ENDIAN);
            account(0, length);
        }
        TypedArrays.copy(heapData, buffer);
    }

    public void copyToHeap() {
        // No mirror, or the heap is newer (copying the mirror back would undo drawing).
        if (buffer == null || bufferStale) {
            return;
        }
        // Need to update emscripten heap. TODO implement a way make all native calls use buffer and not emscripten heap
        Int8Array heapData = getHeapData(false);
        Int8Array typedArray = TypedArrays.getInt8Array(buffer);
        heapData.set(typedArray);
    }

    private void convert(int requestedFormat) {
        Gdx2DPixmapNative pixmap = new Gdx2DPixmapNative(width, height, requestedFormat);
        pixmap.setBlend(Gdx2DPixmap.GDX2D_BLEND_NONE);
        pixmap.drawPixmap(basePtr, 0, 0, 0, 0, width, height);
        dispose();
        this.basePtr = pixmap.basePtr;
        this.format = pixmap.format;
        this.width = pixmap.width;
        this.height = pixmap.height;
        this.nativeData = pixmap.nativeData;
        this.heapStartIndex = pixmap.heapStartIndex;
        this.heapEndIndex = pixmap.heapEndIndex;
        // Take over the converted pixmap's mirror and its accounting (this one's was released).
        this.buffer = pixmap.buffer;
        this.bufferStale = pixmap.bufferStale;
        this.countedHeap = pixmap.countedHeap;
        this.countedMirror = pixmap.countedMirror;
    }

    @Override
    public void dispose() {
        untrackLarge(basePtr);
        if (countedHeap != 0 || countedMirror != 0) {
            liveCount--;
            account(-countedHeap, -countedMirror);
        }
        free(basePtr);
        if (buffer != null) {
            Buffers.free(buffer);
            buffer = null;
        }
        nativeData = null;
    }

    public void clear(int color) {
        clear(basePtr, color);
        bufferStale = true;
    }

    public void setPixel(int x, int y, int color) {
        setPixel(basePtr, x, y, color);
        bufferStale = true;
    }

    public int getPixel(int x, int y) {
        return getPixel(basePtr, x, y);
    }

    public void drawLine(int x, int y, int x2, int y2, int color) {
        drawLine(basePtr, x, y, x2, y2, color);
        bufferStale = true;
    }

    public void drawRect(int x, int y, int width, int height, int color) {
        drawRect(basePtr, x, y, width, height, color);
        bufferStale = true;
    }

    public void drawCircle(int x, int y, int radius, int color) {
        drawCircle(basePtr, x, y, radius, color);
        bufferStale = true;
    }

    public void fillRect(int x, int y, int width, int height, int color) {
        fillRect(basePtr, x, y, width, height, color);
        bufferStale = true;
    }

    public void fillCircle(int x, int y, int radius, int color) {
        fillCircle(basePtr, x, y, radius, color);
        bufferStale = true;
    }

    public void fillTriangle(int x1, int y1, int x2, int y2, int x3, int y3, int color) {
        fillTriangle(basePtr, x1, y1, x2, y2, x3, y3, color);
        bufferStale = true;
    }

    public void drawPixmap(int basePtr, int srcX, int srcY, int dstX, int dstY, int width, int height) {
        drawPixmap(basePtr, this.basePtr, srcX, srcY, width, height, dstX, dstY, width, height);
        bufferStale = true;
    }

    public void drawPixmap(int basePtr, int srcX, int srcY, int srcWidth, int srcHeight, int dstX, int dstY, int dstWidth, int dstHeight) {
        drawPixmap(basePtr, this.basePtr, srcX, srcY, srcWidth, srcHeight, dstX, dstY, dstWidth, dstHeight);
        bufferStale = true;
    }

    public void setBlend(int blend) {
        setBlend(basePtr, blend);
        bufferStale = true;
    }

    public void setScale(int scale) {
        setScale(basePtr, scale);
        bufferStale = true;
    }

    public ByteBuffer getBuffer() {
        syncBuffer();
        return buffer;
    }

    private void syncBuffer() {
        if (buffer == null || bufferStale) {
            bufferStale = false;
            copyHeapToBuffer();
        }
    }

    public int getHeight() {
        return height;
    }

    public int getWidth() {
        return width;
    }

    public int getFormat() {
        return format;
    }

    public Int8Array getHeapData(boolean shouldCopy) {
        if(heapStartIndex == 0 && heapEndIndex == 0) {
            return null;
        }
        return getHeapDataNative(shouldCopy, heapStartIndex, heapEndIndex);
    }

    @JSBody(params = {"shouldCopy", "heapStartIndex", "heapEndIndex"}, script = "" +
            "var heapArray = Gdx.HEAP8.subarray(heapStartIndex, heapEndIndex);" +
            "if(shouldCopy) {" +
            "   var newArray = new Int8Array(heapArray);" +
            "   return newArray;" +
            "}" +
            "return heapArray;"
    )
    public static native Int8Array getHeapDataNative(boolean shouldCopy, int heapStartIndex, int heapEndIndex);

    // @off
    /*JNI
    #include <gdx2d/gdx2d.h>
    #include <stdlib.h>
     */

    @JSBody(params = {"buffer", "offset", "len"}, script = "" +
            "var cBufferSize = buffer.length * Uint8Array.BYTES_PER_ELEMENT;" +
            "var cBuffer = Gdx._malloc(cBufferSize);" +
            "Gdx.writeArrayToMemory(buffer, cBuffer);" +
            "var pixmap = Gdx.Gdx.prototype.g2d_load(cBuffer, offset, len);" +
            "Gdx._free(cBuffer);" +
            "var pixels = Gdx.Gdx.prototype.g2d_get_pixels(pixmap);" +
            "var pixmapAddr = Gdx.getPointer(pixmap);" +
            "var format = pixmap.get_format();" +
            "var width = pixmap.get_width();" +
            "var height = pixmap.get_height();" +
            "var bytesPerPixel = Gdx.Gdx.prototype.g2d_bytes_per_pixel(format);" +
            "var bytesSize = width * height * bytesPerPixel;" +
            "var startIndex = pixels;" +
            "var endIndex = startIndex + bytesSize;" +
            "var nativeData = new Int32Array(6);" +
            "nativeData[0] = pixmapAddr;" +
            "nativeData[1] = width;" +
            "nativeData[2] = height;" +
            "nativeData[3] = format;" +
            "nativeData[4] = startIndex;" +
            "nativeData[5] = endIndex;" +
            "return nativeData;"
    )
    public static native Int32Array loadNative(byte[] buffer, int offset, int len); /*MANUAL
        const unsigned char* p_buffer = (const unsigned char*)env->GetPrimitiveArrayCritical(buffer, 0);
        gdx2d_pixmap* pixmap = gdx2d_load(p_buffer + offset, len);
        env->ReleasePrimitiveArrayCritical(buffer, (char*)p_buffer, 0);

        if(pixmap==0)
            return 0;

        jobject pixel_buffer = env->NewDirectByteBuffer((void*)pixmap->pixels, pixmap->width * pixmap->height * gdx2d_bytes_per_pixel(pixmap->format));
        jlong* p_native_data = (jlong*)env->GetPrimitiveArrayCritical(nativeData, 0);
        p_native_data[0] = (jlong)pixmap;
        p_native_data[1] = pixmap->width;
        p_native_data[2] = pixmap->height;
        p_native_data[3] = pixmap->format;
        env->ReleasePrimitiveArrayCritical(nativeData, p_native_data, 0);

        return pixel_buffer;
     */

    @JSBody(params = {"width", "height", "format"}, script = "" +
            "var pixmap = Gdx.Gdx.prototype.g2d_new(width, height, format);" +
            "var pixels = Gdx.Gdx.prototype.g2d_get_pixels(pixmap);" +
            "var pixmapAddr = Gdx.getPointer(pixmap);" +
            "var format = pixmap.get_format();" +
            "var width = pixmap.get_width();" +
            "var height = pixmap.get_height();" +
            "var bytesPerPixel = Gdx.Gdx.prototype.g2d_bytes_per_pixel(format);" +
            "var bytesSize = width * height * bytesPerPixel;" +
            "var startIndex = pixels;" +
            "var endIndex = startIndex + bytesSize;" +
            "var nativeData = new Int32Array(6);" +
            "nativeData[0] = pixmapAddr;" +
            "nativeData[1] = width;" +
            "nativeData[2] = height;" +
            "nativeData[3] = format;" +
            "nativeData[4] = startIndex;" +
            "nativeData[5] = endIndex;" +
            "return nativeData;"
    )
    public static native Int32Array newPixmapNative(int width, int height, int format); /*MANUAL
        gdx2d_pixmap* pixmap = gdx2d_new(width, height, format);
        if(pixmap==0)
            return 0;

        jobject pixel_buffer = env->NewDirectByteBuffer((void*)pixmap->pixels, pixmap->width * pixmap->height * gdx2d_bytes_per_pixel(pixmap->format));
        jlong* p_native_data = (jlong*)env->GetPrimitiveArrayCritical(nativeData, 0);
        p_native_data[0] = (jlong)pixmap;
        p_native_data[1] = pixmap->width;
        p_native_data[2] = pixmap->height;
        p_native_data[3] = pixmap->format;
        env->ReleasePrimitiveArrayCritical(nativeData, p_native_data, 0);

        return pixel_buffer;
     */

    @JSBody(params = { "pixmap" }, script = "" +
            "Gdx.Gdx.prototype.g2d_free(pixmap);")
    public static native void free(int pixmap); /*
        gdx2d_free((gdx2d_pixmap*)pixmap);
     */

    @JSBody(params = { "pixmap", "color" }, script = "" +
            "Gdx.Gdx.prototype.g2d_clear(pixmap, color);")
    public static native void clear(int pixmap, int color); /*
        gdx2d_clear((gdx2d_pixmap*)pixmap, color);
     */

    @JSBody(params = { "pixmap", "x", "y", "color" }, script = "" +
            "Gdx.Gdx.prototype.g2d_set_pixel(pixmap, x, y, color);")
    public static native void setPixel(int pixmap, int x, int y, int color); /*
        gdx2d_set_pixel((gdx2d_pixmap*)pixmap, x, y, color);
     */

    @JSBody(params = { "pixmap", "x", "y" }, script = "" +
            "return Gdx.Gdx.prototype.g2d_get_pixel(pixmap, x, y);")
    public static native int getPixel(int pixmap, int x, int y); /*
        return gdx2d_get_pixel((gdx2d_pixmap*)pixmap, x, y);
     */

    @JSBody(params = { "pixmap", "x", "y", "x2", "y2", "color" }, script = "" +
            "Gdx.Gdx.prototype.g2d_draw_line(pixmap, x, y, x2, y2, color);")
    public static native void drawLine(int pixmap, int x, int y, int x2, int y2, int color); /*
        gdx2d_draw_line((gdx2d_pixmap*)pixmap, x, y, x2, y2, color);
     */

    @JSBody(params = { "pixmap", "x", "y", "width", "height", "color" }, script = "" +
            "Gdx.Gdx.prototype.g2d_draw_rect(pixmap, x, y, width, height, color);")
    public static native void drawRect(int pixmap, int x, int y, int width, int height, int color); /*
        gdx2d_draw_rect((gdx2d_pixmap*)pixmap, x, y, width, height, color);
     */

    @JSBody(params = { "pixmap", "x", "y", "radius", "color" }, script = "" +
            "Gdx.Gdx.prototype.g2d_draw_circle(pixmap, x, y, radius, color);")
    public static native void drawCircle(int pixmap, int x, int y, int radius, int color); /*
        gdx2d_draw_circle((gdx2d_pixmap*)pixmap, x, y, radius, color);
     */

    @JSBody(params = { "pixmap", "x", "y", "width", "height", "color" }, script = "" +
            "Gdx.Gdx.prototype.g2d_fill_rect(pixmap, x, y, width, height, color);")
    public static native void fillRect(int pixmap, int x, int y, int width, int height, int color); /*
        gdx2d_fill_rect((gdx2d_pixmap*)pixmap, x, y, width, height, color);
     */

    @JSBody(params = { "pixmap", "x", "y", "radius", "color" }, script = "" +
            "Gdx.Gdx.prototype.g2d_fill_circle(pixmap, x, y, radius, color);")
    public static native void fillCircle(int pixmap, int x, int y, int radius, int color); /*
        gdx2d_fill_circle((gdx2d_pixmap*)pixmap, x, y, radius, color);
     */

    @JSBody(params = { "pixmap", "x1", "y1", "x2", "y2", "x3", "y3", "color" }, script = "" +
            "Gdx.Gdx.prototype.g2d_fill_triangle(pixmap, x1, y1, x2, y2, x3, y3, color);")
    public static native void fillTriangle(int pixmap, int x1, int y1, int x2, int y2, int x3, int y3, int color); /*
        gdx2d_fill_triangle((gdx2d_pixmap*)pixmap, x1, y1, x2, y2, x3, y3, color);
     */

    @JSBody(params = { "src", "dst", "srcX", "srcY", "srcWidth", "srcHeight", "dstX", "dstY", "dstWidth", "dstHeight" }, script = "" +
            "Gdx.Gdx.prototype.g2d_draw_pixmap(src, dst, srcX, srcY, srcWidth, srcHeight, dstX, dstY, dstWidth, dstHeight);")
    public static native void drawPixmap(int src, int dst, int srcX, int srcY, int srcWidth, int srcHeight, int dstX, int dstY, int dstWidth, int dstHeight); /*
        gdx2d_draw_pixmap((gdx2d_pixmap*)src, (gdx2d_pixmap*)dst, srcX, srcY, srcWidth, srcHeight, dstX, dstY, dstWidth, dstHeight);
         */

    @JSBody(params = { "src", "blend" }, script = "" +
            "Gdx.Gdx.prototype.g2d_set_blend(src, blend);")
    public static native void setBlend(int src, int blend); /*
        gdx2d_set_blend((gdx2d_pixmap*)src, blend);
     */

    @JSBody(params = { "src", "scale" }, script = "" +
            "Gdx.Gdx.prototype.g2d_set_scale(src, scale);")
    public static native void setScale(int src, int scale); /*
        gdx2d_set_scale((gdx2d_pixmap*)src, scale);
     */

    public static native String getFailureReason(); /*
     return env->NewStringUTF(gdx2d_get_failure_reason());
     */
}