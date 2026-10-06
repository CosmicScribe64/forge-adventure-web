// Shadows gdx-teavm backend-web 1.6.1's HowlMusic (project classes come first on the TeaVM
// classpath). Changes, marked "forgeweb": music streams through an HTML5 Audio element
// (Howler's html5: true) instead of being decoded whole by Web Audio, which cost about 70 MB per
// 185 s track; play() retries after the first user gesture; the music bytes become a data: URI, see createStreaming.
// See wiki/concepts/memory-budget.md.
package com.github.xpenatan.gdx.teavm.backends.web.webaudio.howler;

import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.files.FileHandle;
import com.github.xpenatan.gdx.teavm.backends.web.dom.typedarray.TypedArrays;
import org.teavm.jso.JSBody;
import org.teavm.jso.typedarrays.ArrayBufferView;

public class HowlMusic implements Music {

    private Howl howl;

    public HowlMusic(FileHandle fileHandle) {
        byte[] bytes = fileHandle.readBytes();
        ArrayBufferView data = TypedArrays.getInt8Array(bytes);
        howl = createStreaming(data);
    }

    // forgeweb: html5 streams from a data: URI. A Blob URL looked simpler (and cheaper) but WebKit sometimes fails
    // the first request of a fresh audio element for a blob: or http: URL (MEDIA_ERR_SRC_NOT_SUPPORTED a few
    // milliseconds after loadstart, with "Failed to load resource" on the console for blob: URLs; the URL is still
    // valid afterwards). About one load in 100 in a create/dispose loop and about one boot in 15 on the title
    // screen, whether or not the URL was revoked. A data: URI is decoded inside the page and never goes to the
    // network process: 0 failures in 1800 loads. The cost is a base64 string of 1.33 times the track (5 MB for the
    // largest), which is gone when the Howl is unloaded and collected.
    // The format comes from the file's first bytes: Howler takes the first entry of an explicit format list for a
    // single src, and "ogg" for an mp3 would be refused on browsers without Vorbis (Safari).
    @JSBody(params = { "arrayBufferView" }, script = "" +
            "var bytes = new Uint8Array(arrayBufferView.buffer, arrayBufferView.byteOffset, arrayBufferView.byteLength);" +
            "var fmt = 'mp3', mime = 'audio/mpeg';" +
            "if (bytes[0] == 0x4F && bytes[1] == 0x67 && bytes[2] == 0x67 && bytes[3] == 0x53) { fmt = 'ogg'; mime = 'audio/ogg'; }" +
            "else if (bytes[0] == 0x52 && bytes[1] == 0x49 && bytes[2] == 0x46 && bytes[3] == 0x46) { fmt = 'wav'; mime = 'audio/wav'; }" +
            "else if (bytes[0] == 0x66 && bytes[1] == 0x4C && bytes[2] == 0x61 && bytes[3] == 0x43) { fmt = 'flac'; mime = 'audio/flac'; }" +
            "var binary = '';" +
            "for (var offset = 0; offset < bytes.length; offset += 0x8000) binary += String.fromCharCode.apply(null, bytes.subarray(offset, offset + 0x8000));" +
            "var howl = new Howl({ src: ['data:' + mime + ';base64,' + btoa(binary)], html5: true, format: [fmt] });" +
            "howl._forgeAlive = true;" +
            // Web Audio Howls wait for the first user gesture by themselves; an HTML5 Audio element
            // instead rejects play() ("playerror") and Howler drops it. Retry once Howler unlocks.
            "howl.on('playerror', function() {" +
            "  if (howl._forgeWait) return;" +
            "  howl._forgeWait = true;" +
            "  howl.once('unlock', function() { howl._forgeWait = false; if (howl._forgeWant && howl._forgeAlive) howl.play(); });" +
            "});" +
            "return howl;")
    private static native Howl createStreaming(ArrayBufferView arrayBufferView);

    // Called after unload(): lets go of the data URI string at once instead of with the next collection.
    @JSBody(params = { "howl" }, script = "howl._forgeAlive = false; howl._src = '';")
    private static native void release(Howl h);

    @JSBody(params = { "h", "want" }, script = "h._forgeWant = want;")
    private static native void setWanted(Howl h, boolean want);

    @Override
    public void play() {
        setWanted(howl, true);
        if(!isPlaying()) {
            howl.play();
        }
    }

    @Override
    public void pause() {
        setWanted(howl, false);
        howl.pause();
    }

    @Override
    public void stop() {
        setWanted(howl, false);
        howl.stop();
    }

    @Override
    public boolean isPlaying() {
        return howl.isPlaying();
    }

    @Override
    public void setLooping(boolean isLooping) {
        howl.setLoop(isLooping);
    }

    @Override
    public boolean isLooping() {
        return howl.getLoop();
    }

    @Override
    public void setVolume(float volume) {
        howl.setVolume(volume);
    }

    @Override
    public float getVolume() {
        return howl.getVolume();
    }

    @Override
    public void setPan(float pan, float volume) {
        howl.setStereo(pan);
        howl.setVolume(volume);
    }

    @Override
    public void setPosition(float position) {
        howl.setSeek(position);
    }

    @Override
    public float getPosition() {
        return howl.getSeek();
    }

    @Override
    public void dispose() {
        howl.stop();
        howl.unload();
        release(howl); // forgeweb
        howl = null;
    }

    @Override
    public void setOnCompletionListener(OnCompletionListener listener) {
        howl.on("end", () -> listener.onCompletion(HowlMusic.this));
    }
}
