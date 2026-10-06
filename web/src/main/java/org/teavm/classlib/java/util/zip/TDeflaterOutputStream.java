/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.teavm.classlib.java.util.zip;

import java.io.ByteArrayOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import org.teavm.interop.Async;
import org.teavm.interop.AsyncCallback;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;
import org.teavm.jso.typedarrays.Int8Array;

// Forge web: TeaVM 0.15's TDeflaterOutputStream plus a native path. TeaVM's Deflater is jzlib,
// pure Java, which is slow as JavaScript: Adventure's autosave (before every duel and every town
// or dungeon) froze the page for 1.4 s compressing the save and the world map PNG. A stream now
// buffers what is written and, in finish(), compresses it with the browser's own zlib
// (CompressionStream "deflate", or "deflate-raw" for a nowrap Deflater: the same formats) when the
// calling green thread can wait for it; otherwise it falls back to the original path through the
// Deflater. Keep in sync with TeaVM on upgrades (TDeflater is shadowed too, for its nowrap flag).
public class TDeflaterOutputStream extends FilterOutputStream {
    static final int BUF_SIZE = 512;
    protected byte[] buf;
    protected TDeflater def;
    boolean done;
    // Native path: bytes written so far (null for ZipOutputStream, and once finished). The caller's
    // Deflater isn't used on this path: its level is ignored and its finished()/totals aren't set.
    private ByteArrayOutputStream pending;

    public TDeflaterOutputStream(OutputStream os, TDeflater def) {
        this(os, def, BUF_SIZE);
    }

    public TDeflaterOutputStream(OutputStream os) {
        this(os, new TDeflater());
    }

    public TDeflaterOutputStream(OutputStream os, TDeflater def, int bsize) {
        super(os);
        if (os == null || def == null) {
            throw new NullPointerException();
        }
        if (bsize <= 0) {
            throw new IllegalArgumentException();
        }
        this.def = def;
        buf = new byte[bsize];
        // Native path for any Deflater: the browser writes the same zlib (or raw, nowrap) format.
        // The caller's compression level isn't applied (the browser uses its default).
        // Not for ZipOutputStream: it drives the Deflater itself for each entry.
        if (!(this instanceof TZipOutputStream)) {
            pending = new ByteArrayOutputStream();
        }
    }

    protected void deflate() throws IOException {
        int x;
        do {
            x = def.deflate(buf);
            out.write(buf, 0, x);
        } while (!def.needsInput());
    }

    @Override
    public void close() throws IOException {
        // Finish once: on the native path the Deflater never finishes, and a second finish() of a
        // GZIPOutputStream would append another trailer.
        if (!done && (pending != null || !def.finished())) {
            finish();
        }
        def.end();
        out.close();
    }

    public void finish() throws IOException {
        if (done) {
            return;
        }
        if (pending != null) {
            byte[] data = pending.toByteArray();
            pending = null;
            if (canSuspend() && hasCompressionStream()) {
                byte[] compressed = null;
                try {
                    compressed = compressNative(data, def.nowrap);
                } catch (IOException e) {
                    // Fall back to the Java Deflater below with the same data.
                }
                if (compressed != null) {
                    out.write(compressed, 0, compressed.length);
                    done = true;
                    return;
                }
            }
            // Can't wait here (or the browser failed): compress with the Java Deflater as before.
            def.setInput(data, 0, data.length);
            deflate();
        }
        def.finish();
        int x = 0;
        while (!def.finished()) {
            if (def.needsInput()) {
                def.setInput(buf, 0, 0);
            }
            x = def.deflate(buf);
            out.write(buf, 0, x);
        }
        done = true;
    }

    @Override
    public void write(int i) throws IOException {
        byte[] b = new byte[1];
        b[0] = (byte) i;
        write(b, 0, 1);
    }

    @Override
    public void write(byte[] buffer, int off, int nbytes) throws IOException {
        if (done) {
            throw new IOException();
        }
        // avoid int overflow, check null buf
        if (off <= buffer.length && nbytes >= 0 && off >= 0 && buffer.length - off >= nbytes) {
            if (pending != null) {
                pending.write(buffer, off, nbytes);
                return;
            }
            if (!def.needsInput()) {
                throw new IOException();
            }
            def.setInput(buffer, off, nbytes);
            deflate();
        } else {
            throw new ArrayIndexOutOfBoundsException();
        }
    }

    // --- native compression ---

    @JSBody(script = "return typeof $rt_nativeThread === 'function' && $rt_nativeThread() !== null;")
    private static native boolean canSuspend();

    @JSBody(script = "return typeof CompressionStream === 'function';")
    private static native boolean hasCompressionStream();

    @JSFunctor
    interface Done extends JSObject {
        void accept(Int8Array result);
    }

    @JSFunctor
    interface Failed extends JSObject {
        void accept(String error);
    }

    @Async
    private static native byte[] compressNative(byte[] data, boolean raw) throws IOException;

    private static void compressNative(byte[] data, boolean raw, AsyncCallback<byte[]> callback) {
        compressImpl(Int8Array.copyFromJavaArray(data), raw, result -> callback.complete(result.copyToJavaArray()),
                error -> callback.error(new IOException("CompressionStream failed: " + error)));
    }

    @JSBody(params = {"data", "raw", "done", "failed"}, script = ""
            + "new Response(new Blob([data]).stream().pipeThrough(new CompressionStream(raw ? 'deflate-raw' : 'deflate')))"
            // Long closure names: a minified build renames "failed" to "e", so a closure parameter "e" would hide it
            // (see web/tools/test_jsbody_names.py).
            + ".arrayBuffer().then(function(buffer) { done(new Int8Array(buffer)); }, function(reason) { failed(String(reason)); });")
    private static native void compressImpl(Int8Array data, boolean raw, Done done, Failed failed);
}
