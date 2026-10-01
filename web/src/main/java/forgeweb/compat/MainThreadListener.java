package forgeweb.compat;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;

/** Runs every lifecycle callback of the wrapped app on the {@link UiThread}. */
public final class MainThreadListener implements ApplicationListener {
    private final ApplicationListener app;

    public MainThreadListener(ApplicationListener app) {
        this.app = app;
    }

    /**
     * Forge reads the screen size once in create() and keeps it (a phone's screen doesn't
     * change). A page opened in a hidden tab or pane has a 0x0 canvas then, and every draw was
     * scissored away for good, so wait for a real size first.
     */
    @Override
    public void create() {
        UiThread.post(() -> {
            while (Gdx.graphics.getWidth() <= 0 || Gdx.graphics.getHeight() <= 0) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    return;
                }
            }
            Progress.mark("Forge create");
            app.create();
        });
    }
    @Override public void resize(int width, int height) { UiThread.post(() -> app.resize(width, height)); }
    @Override public void pause() { UiThread.post(app::pause); }
    @Override public void resume() { UiThread.post(app::resume); }
    @Override public void dispose() { UiThread.post(app::dispose); }

    @Override
    public void render() {
        UiThread.postFrame(() -> {
            double start = FrameStats.now();
            GdxCompat.frameStarted(start);
            forgeweb.test.WebTest.tick(); // harness walking (moveto/goto)
            app.render();
            FrameStats.rendered(FrameStats.now() - start);
            LoadingScreen.hide();
            reportMilestones();
        });
    }

    private boolean titleReported, gameplayReported;

    /** Time to gameplay (see Progress.timingReport): the title screen, then the first map the
     *  player walks on (the overworld, or a place such as the new game's tutorial cave). */
    private void reportMilestones() {
        if (gameplayReported) return;
        forge.adventure.scene.Scene scene = forge.Forge.getCurrentScene();
        if (!titleReported && scene instanceof forge.adventure.scene.StartScene) {
            titleReported = true;
            Progress.timingReport("title screen");
        } else if (titleReported && (scene instanceof forge.adventure.scene.GameScene
                || scene instanceof forge.adventure.scene.TileMapScene)) {
            gameplayReported = true;
            Progress.timingReport("gameplay");
        } else if (titleReported && scene != null) {
            Progress.mark(scene.getClass().getSimpleName());
        }
    }
}
