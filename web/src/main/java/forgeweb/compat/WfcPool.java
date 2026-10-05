package forgeweb.compat;

import com.badlogic.gdx.graphics.Color;
import forge.adventure.world.BiomeStructure;
import forge.adventure.world.ColorMap;
import org.teavm.interop.Async;
import org.teavm.interop.AsyncCallback;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;
import org.teavm.jso.typedarrays.Int32Array;
import org.teavm.jso.typedarrays.Int8Array;

/**
 * World generation's wave-function collapse in Web Workers (BiomeStructure.chunkSolver, set in
 * WebLauncher). A structure's chunks are split across a pool of workers running
 * forgeweb.worker.WfcWorker (Forge's own solver, wfc-worker.js); the calling green thread waits
 * without blocking the page. Chunks are independent, so the result is exactly the local one.
 * Falls back to solving locally where the caller can't wait or a worker fails.
 */
public final class WfcPool implements BiomeStructure.ChunkSolver {
    // Chunks sent to workers and solved so far, across the structures solved concurrently (for
    // the progress bar; reset when all are done).
    private static int chunksSent, chunksSolved;

    @Override
    public ColorMap[] solve(ColorMap source, int n, boolean periodicInput, boolean periodicOutput, int symmetry,
                            int ground, long seed, int[] chunks) {
        int count = chunks.length / 4;
        if (count == 0 || !MainThread.canSuspend() || forcedLocal()) {
            return BiomeStructure.solveChunks(source, n, periodicInput, periodicOutput, symmetry, ground, seed, chunks);
        }
        try {
            if (poolSize() == 0) {
                return BiomeStructure.solveChunks(source, n, periodicInput, periodicOutput, symmetry, ground, seed, chunks);
            }
            return solveInWorkers(source, n, periodicInput, periodicOutput, symmetry, ground, seed, chunks, count);
        } catch (RuntimeException e) {
            chunksSent = chunksSolved = 0;
            System.err.println("WFC workers failed (" + e.getMessage() + "); solving on the main thread");
            return BiomeStructure.solveChunks(source, n, periodicInput, periodicOutput, symmetry, ground, seed, chunks);
        }
    }

    private static ColorMap[] solveInWorkers(ColorMap source, int n, boolean periodicInput, boolean periodicOutput,
                                             int symmetry, int ground, long seed, int[] chunks, int count) {
        int[] pixels = new int[source.getWidth() * source.getHeight()];
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                pixels[x + y * source.getWidth()] = Color.rgba8888(source.getColor(x, y));
            }
        }
        // One contiguous range of chunks per worker (each builds its models once per range).
        int jobs = Math.min(count, poolSize());
        int[] starts = new int[jobs + 1];
        for (int j = 0; j <= jobs; j++) starts[j] = (int) ((long) count * j / jobs);
        JSObject[] pending = new JSObject[jobs];
        for (int j = 0; j < jobs; j++) {
            int[] range = new int[(starts[j + 1] - starts[j]) * 4];
            System.arraycopy(chunks, starts[j] * 4, range, 0, range.length);
            pending[j] = post(source.getWidth(), source.getHeight(), Int32Array.copyFromJavaArray(pixels), n,
                    periodicInput, periodicOutput, symmetry, ground, Long.toString(seed),
                    Int32Array.copyFromJavaArray(range));
        }
        chunksSent += count;
        ColorMap[] result = new ColorMap[count];
        for (int j = 0; j < jobs; j++) {
            JSObject reply = await(pending[j]);
            chunksSolved += starts[j + 1] - starts[j];
            Progress.show("", (float) chunksSolved / chunksSent);
            if (chunksSolved >= chunksSent) chunksSolved = chunksSent = 0;
            byte[] status = ((Int8Array) field(reply, "status")).copyToJavaArray();
            int[] out = ((Int32Array) field(reply, "pixels")).copyToJavaArray();
            int k = 0;
            for (int i = 0; i < status.length; i++) {
                if (status[i] == 0) break; // a failed chunk ends the structure (see BiomeStructure)
                int c = starts[j] + i;
                int w = chunks[c * 4 + 2], h = chunks[c * 4 + 3];
                ColorMap image = new ColorMap(w, h);
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        image.setColor(x, y, new Color(out[k++]));
                    }
                }
                result[c] = image;
            }
        }
        return result;
    }

    @JSFunctor
    interface Resolve extends JSObject {
        void accept(JSObject value);
    }

    @JSFunctor
    interface Reject extends JSObject {
        void accept(String error);
    }

    @Async
    private static native JSObject await(JSObject promise);

    private static void await(JSObject promise, AsyncCallback<JSObject> callback) {
        then(promise, callback::complete, error -> callback.error(new IllegalStateException(error)));
    }

    @JSBody(params = {"promise", "resolve", "reject"},
            script = "promise.then(function(v) { resolve(v); }, function(e) { reject(String(e)); });")
    private static native void then(JSObject promise, Resolve resolve, Reject reject);

    /** {@code ?wfc=local}: solve on the main thread (to compare with the workers' result). */
    @JSBody(script = "return new URLSearchParams(location.search).get('wfc') === 'local';")
    private static native boolean forcedLocal();

    @JSBody(params = {"o", "k"}, script = "return o[k];")
    private static native JSObject field(JSObject o, String k);

    /**
     * Starts the workers on first use and returns how many there are: one per core, leaving one
     * for the page, from 1 to 8. Returns 0 once a worker has failed (script missing, crash, or no
     * reply in 2 minutes). Everything is then solved on the page, because waiting on a dead worker
     * would hang world generation. Each worker costs about 45 MB of renderer memory, so the pool is
     * terminated 5 seconds after the last reply and started again by the next generation.
     */
    @JSBody(script = ""
            + "var p = window.forgeWfc;"
            + "if (p && p.broken) return 0;"
            + "if (!p) {"
            + "  p = window.forgeWfc = {workers: [], next: 0, seq: 0, pending: new Map(), broken: false};"
            + "  var n = Math.max(1, Math.min(8, (navigator.hardwareConcurrency || 4) - 1));"
            + "  var fail = function(why) {"
            + "    p.broken = true;"
            + "    clearTimeout(p.idleTimer);"
            + "    p.workers.forEach(function(w) { w.terminate(); });"
            + "    p.pending.forEach(function(r) { r.reject(why); });"
            + "    p.pending.clear();"
            + "  };"
            + "  p.fail = fail;"
            + "  p.idle = function() {"
            + "    clearTimeout(p.idleTimer);"
            + "    p.idleTimer = setTimeout(function() {"
            + "      if (p.broken || p.pending.size > 0 || window.forgeWfc !== p) return;"
            + "      p.workers.forEach(function(w) { w.terminate(); });"
            + "      delete window.forgeWfc;"
            + "    }, 5000);"
            + "  };"
            + "  for (var i = 0; i < n; i++) {"
            + "    var w = new Worker(window.forgeWfcUrl || 'wfc-worker.js');"
            + "    w.onmessage = function(e) {"
            + "      var r = p.pending.get(e.data.id);"
            + "      if (!r) return;"
            + "      p.pending.delete(e.data.id);"
            + "      clearTimeout(r.timer);"
            + "      if (p.pending.size === 0) p.idle();"
            + "      if (e.data.error) r.reject(e.data.error); else r.resolve(e.data);"
            + "    };"
            + "    w.onerror = function(e) { fail('worker error: ' + (e.message || e)); };"
            + "    p.workers.push(w);"
            + "  }"
            + "}"
            + "return p.workers.length;")
    private static native int poolSize();

    // The arrays are copied, not transferred: TeaVM may hand out the Java array's own storage,
    // which a transfer would detach.
    @JSBody(params = {"width", "height", "pixels", "n", "periodicInput", "periodicOutput", "symmetry", "ground",
            "seed", "chunks"}, script = ""
            + "var p = window.forgeWfc, id = ++p.seq;"
            + "clearTimeout(p.idleTimer);"
            + "var w = p.workers[p.next++ % p.workers.length];"
            + "return new Promise(function(resolve, reject) {"
            + "  if (p.broken) return reject('workers unavailable');"
            + "  var timer = setTimeout(function() { p.fail('worker timed out'); }, 120000);"
            + "  p.pending.set(id, {resolve: resolve, reject: reject, timer: timer});"
            + "  w.postMessage({id: id, width: width, height: height, pixels: pixels, n: n,"
            + "      periodicInput: periodicInput, periodicOutput: periodicOutput, symmetry: symmetry,"
            + "      ground: ground, seed: seed, chunks: chunks});"
            + "});")
    private static native JSObject post(int width, int height, Int32Array pixels, int n, boolean periodicInput,
                                        boolean periodicOutput, int symmetry, int ground, String seed,
                                        Int32Array chunks);
}
