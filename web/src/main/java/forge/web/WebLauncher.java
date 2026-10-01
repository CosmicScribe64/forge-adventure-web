package forge.web;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.controllers.Controllers;
import com.github.xpenatan.gdx.teavm.backends.web.WebApplication;
import com.github.xpenatan.gdx.teavm.backends.web.WebApplicationConfiguration;
import forge.Forge;
import forgeweb.compat.MainThreadListener;
import forgeweb.compat.UiThread;
import forgeweb.fs.WebFileSystem;

/** Browser entry point: the web counterpart of forge-gui-mobile-dev's GameLauncher. */
public class WebLauncher {
    /** Forge's assets folder: res/ is served from the web, data/ and cache/ are local. */
    static final String FORGE_ROOT = "/forge/";
    /** Loaded by name, so it is in build.gradle.kts's reflection list. */
    public static final String CONTROLLER_MANAGER = "com.badlogic.gdx.controllers.ControllerManagerStub";

    public static void main(String[] args) throws Exception {
        forgeweb.compat.ByName.keep();
        UiThread.start();
        forgeweb.test.WebTest.install(); // window.forgeTest.cmd(...), see scripts/play
        // World generation's wave-function collapse runs in Web Workers (wfc-worker.js).
        forge.adventure.world.BiomeStructure.chunkSolver = new forgeweb.compat.WfcPool();
        WebFileSystem fs = WebFileSystem.install(FORGE_ROOT, "forge-data/manifest.txt", "forge/");
        // Folders a desktop install already has; Forge writes into them before creating them.
        for (String dir : new String[] {"data/preferences", "data/decks", "data/adventure", "cache/pics/cards", "cache/skins", "cache/fonts"}) {
            fs.ensureDirectory(FORGE_ROOT + dir);
        }

        // gdx-controllers would load its GWT backend by name; use its no-gamepad stub instead.
        Controllers.preferredManager = CONTROLLER_MANAGER;

        // Lets patched Forge code tell it runs in a browser (patches/forge-web.patch).
        System.setProperty("forge.web", "true");
        // The web build is Adventure mode, so start in it and skip Forge's Classic/Adventure chooser.
        // Written only when there are no preferences yet, so a player's own choice wins.
        java.io.File prefs = new java.io.File(FORGE_ROOT + "data/preferences/forge.preferences");
        if (!prefs.exists()) {
            try (java.io.Writer w = new java.io.OutputStreamWriter(new java.io.FileOutputStream(prefs),
                    java.nio.charset.StandardCharsets.UTF_8)) {
                w.write("UI_SELECTOR_MODE=Adventure\n");
            }
        }

        WebApplicationConfiguration config = new WebApplicationConfiguration("canvas");
        // Fill the window, and follow it (with gdx-teavm's auto size, resize events resize the canvas
        // and call Forge.resize). Adventure shows more of the map where the window isn't 16:9 and keeps
        // its panels at the edges (Forge patch: Scene.getViewWidth/Height, ViewLayout).
        config.width = 0;
        config.height = 0;
        // Forge reads the framebuffer outside drawing a frame (save thumbnails, from a key press or
        // an autosave); WebGL clears it once a frame is shown, so those read back black.
        config.preserveDrawingBuffer = true;
        config.showDownloadLogs = true;
        // WebGL 2, falling back to 1. WebGL 1 can't mipmap textures whose sides aren't powers of two,
        // and Forge's sprite sheets (sleeves 1800x2000 and others) are loaded with mipmaps, so they
        // drew black (card backs in the reward screen).
        config.useGL30 = true;
        config.preloadListener = assetLoader -> assetLoader.loadScript("freetype.js");

        ApplicationListener app = Forge.getApp(null, new WebClipboard(), new WebDeviceAdapter(),
                FORGE_ROOT, false, false, 0);
        new WebApplication(new MainThreadListener(app), config);
    }
}
