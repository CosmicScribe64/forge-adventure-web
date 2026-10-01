package forgeweb.worker;

import com.badlogic.gdx.graphics.Color;
import forge.adventure.world.BiomeStructure;
import forge.adventure.world.ColorMap;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;
import org.teavm.jso.typedarrays.Int32Array;
import org.teavm.jso.typedarrays.Int8Array;

/**
 * Web Worker program (its own small TeaVM build, wfc-worker.js): solves world-generation
 * wave-function-collapse chunks with Forge's own code, so the page's main thread stays free and
 * several structures and chunks are solved in parallel. See forgeweb.compat.WfcPool for the other side.
 *
 * Request: {id, width, height, pixels: Int32Array (RGBA8888, row-major), n, periodicInput,
 * periodicOutput, symmetry, ground, seed: string, chunks: Int32Array [x, y, w, h]*}.
 * Reply: {id, status: Int8Array (1 solved, 0 failed or not run), pixels: Int32Array (RGBA8888 of
 * the solved chunks, in order, each row-major)} or {id, error}.
 */
public final class WfcWorker {
    private WfcWorker() {
    }

    @JSFunctor
    interface Handler extends JSObject {
        void handle(JSObject message);
    }

    public static void main(String[] args) {
        listen(WfcWorker::handle);
    }

    private static void handle(JSObject msg) {
        int id = getInt(msg, "id");
        try {
            int width = getInt(msg, "width"), height = getInt(msg, "height");
            int[] pixels = ((Int32Array) get(msg, "pixels")).copyToJavaArray();
            ColorMap source = new ColorMap(width, height);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    source.setColor(x, y, new Color(pixels[x + y * width]));
                }
            }
            int[] chunks = ((Int32Array) get(msg, "chunks")).copyToJavaArray();
            ColorMap[] solved = BiomeStructure.solveChunks(source, getInt(msg, "n"), getBool(msg, "periodicInput"),
                    getBool(msg, "periodicOutput"), getInt(msg, "symmetry"), getInt(msg, "ground"),
                    Long.parseLong(getString(msg, "seed")), chunks);

            byte[] status = new byte[solved.length];
            int total = 0;
            for (int c = 0; c < solved.length; c++) {
                if (solved[c] != null) {
                    status[c] = 1;
                    total += solved[c].getWidth() * solved[c].getHeight();
                }
            }
            int[] out = new int[total];
            int k = 0;
            for (ColorMap image : solved) {
                if (image == null) continue;
                for (int y = 0; y < image.getHeight(); y++) {
                    for (int x = 0; x < image.getWidth(); x++) {
                        out[k++] = Color.rgba8888(image.getColor(x, y));
                    }
                }
            }
            reply(id, Int8Array.copyFromJavaArray(status), Int32Array.copyFromJavaArray(out));
        } catch (Throwable t) {
            replyError(id, String.valueOf(t));
        }
    }

    @JSBody(params = "handler", script = "self.onmessage = function(e) { handler(e.data); };")
    private static native void listen(Handler handler);

    @JSBody(params = {"o", "k"}, script = "return o[k];")
    private static native JSObject get(JSObject o, String k);

    @JSBody(params = {"o", "k"}, script = "return o[k] | 0;")
    private static native int getInt(JSObject o, String k);

    @JSBody(params = {"o", "k"}, script = "return !!o[k];")
    private static native boolean getBool(JSObject o, String k);

    @JSBody(params = {"o", "k"}, script = "return String(o[k]);")
    private static native String getString(JSObject o, String k);

    @JSBody(params = {"id", "status", "pixels"},
            script = "self.postMessage({id: id, status: status, pixels: pixels}, [status.buffer, pixels.buffer]);")
    private static native void reply(int id, Int8Array status, Int32Array pixels);

    @JSBody(params = {"id", "error"}, script = "self.postMessage({id: id, error: error});")
    private static native void replyError(int id, String error);
}
