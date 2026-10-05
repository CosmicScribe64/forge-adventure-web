// Shadows gdx-teavm backend-web 1.6.1's HowlMusic (project classes come first on the TeaVM
// classpath). Changes, marked "forgeweb": music streams through an HTML5 Audio element
// (Howler's html5: true) instead of being decoded whole by Web Audio, which cost about 70 MB per
// 185 s track; play() retries after the first user gesture; dispose() revokes the Blob URL (after a delay, see revokeUrl).
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

    // forgeweb: html5 streams from the Blob URL; the URL is kept on the Howl so dispose() can revoke it.
    @JSBody(params = { "arrayBufferView" }, script = "" +
            "var blob = new Blob([arrayBufferView]);" +
            "var url = URL.createObjectURL(blob);" +
            "var h = new Howl({ src: [url], html5: true, format: ['ogg', 'webm', 'mp3', 'wav'] });" +
            "h._forgeBlobUrl = url;" +
            // Web Audio Howls wait for the first user gesture by themselves; an HTML5 Audio element
            // instead rejects play() ("playerror") and Howler drops it. Retry once Howler unlocks.
            "h.on('playerror', function() {" +
            "  if (h._forgeWait) return;" +
            "  h._forgeWait = true;" +
            "  h.once('unlock', function() { h._forgeWait = false; if (h._forgeWant && h._forgeBlobUrl) h.play(); });" +
            "});" +
            "return h;")
    private static native Howl createStreaming(ArrayBufferView arrayBufferView);

    // The audio element starts fetching its Blob URL asynchronously, and a track is often disposed within half a
    // second of being created (the title screen swaps its music right away). Revoking the URL before WebKit has
    // started that fetch makes it log "Failed to load resource" (about one run in three on the title screen), so
    // the revoke waits a few seconds; a Blob of one track is a few MB.
    @JSBody(params = { "h" }, script = "if (h._forgeBlobUrl) { var u = h._forgeBlobUrl; h._forgeBlobUrl = null; setTimeout(function () { URL.revokeObjectURL(u); }, 5000); }")
    private static native void revokeUrl(Howl h);

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
        revokeUrl(howl); // forgeweb
        howl = null;
    }

    @Override
    public void setOnCompletionListener(OnCompletionListener listener) {
        howl.on("end", () -> listener.onCompletion(HowlMusic.this));
    }
}
