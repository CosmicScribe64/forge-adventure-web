package forgeweb.compat;

import com.badlogic.gdx.InputProcessor;

/**
 * Runs input callbacks of the wrapped processor on the {@link UiThread}. The browser needs an
 * answer now, so every event reports "handled"; the game sees it a moment later, in order.
 */
public final class MainThreadInputProcessor implements InputProcessor {
    final InputProcessor p;

    MainThreadInputProcessor(InputProcessor p) {
        this.p = p;
    }

    private static boolean post(Runnable r) {
        UiThread.post(r);
        return true;
    }

    @Override public boolean keyDown(int keycode) { return post(() -> p.keyDown(keycode)); }
    @Override public boolean keyUp(int keycode) { return post(() -> p.keyUp(keycode)); }
    @Override public boolean keyTyped(char c) { return post(() -> p.keyTyped(c)); }
    @Override public boolean touchDown(int x, int y, int pointer, int button) { return post(() -> p.touchDown(x, y, pointer, button)); }
    @Override public boolean touchUp(int x, int y, int pointer, int button) { return post(() -> p.touchUp(x, y, pointer, button)); }
    @Override public boolean touchCancelled(int x, int y, int pointer, int button) { return post(() -> p.touchCancelled(x, y, pointer, button)); }
    @Override public boolean touchDragged(int x, int y, int pointer) { return post(() -> p.touchDragged(x, y, pointer)); }
    @Override public boolean mouseMoved(int x, int y) { return post(() -> p.mouseMoved(x, y)); }
    @Override public boolean scrolled(float amountX, float amountY) { return post(() -> p.scrolled(amountX, amountY)); }
}
