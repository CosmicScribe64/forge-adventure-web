package emu.com.badlogic.gdx.utils.async;

import emu.com.badlogic.gdx.assets.AssetLoadingTask;
import org.teavm.jso.browser.TimerHandler;
import org.teavm.jso.browser.Window;

// Shadows gdx-teavm 1.6.1's own copy (project classes come first on the classpath).
// Fix: the task ran more than once. The timer ran it, and isDone() ran it again on every poll
// after the second (finishLoading/finishLoadingAsset poll in a loop). For TextureLoader that
// decoded the image again after the texture had been uploaded. The extra Pixmap overwrote the
// loader's field and was never disposed, which leaked one decoded image (in the wasm heap) per
// texture and doubled the decoding work. Now the task runs exactly once, whichever comes first, and a failure is
// kept and rethrown from get() (before, it was thrown into a timer callback and lost, or out of
// isDone(), depending on timing).
public class AsyncResult<T> implements TimerHandler {
    private AssetLoadingTask loadingTask;
    private boolean isDone;
    private int count;
    private Exception failure;

    AsyncResult(AsyncTask<T> task) {
        loadingTask = (AssetLoadingTask)task;
        Window.setTimeout(this, 0);
    }

    public boolean isDone() {
        count++;
        if(count > 2) {
            tick(); // Polled by finishLoading(): run now instead of waiting for the timer.
        }
        return isDone;
    }

    /** Rethrows what the task threw (whether the timer or a poll ran it), like Future.get. */
    public T get() {
        if (failure != null) {
            throw new com.badlogic.gdx.utils.GdxRuntimeException(failure);
        }
        return null;
    }

    @Override
    public void onTimer() {
        tick();
    }

    private void tick() {
        if(isDone) {
            return;
        }
        isDone = true;
        try {
            loadingTask.call();
        } catch(Exception e) {
            failure = e;
        }
    }
}
