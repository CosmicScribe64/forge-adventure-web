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

    /** A touch screen: the pointer is coarse, or the device reports touch points. */
    @org.teavm.jso.JSBody(script = "return matchMedia('(pointer: coarse)').matches || navigator.maxTouchPoints > 0;")
    private static native boolean isTouchDevice();

    private static String readText(java.io.File file) throws java.io.IOException {
        try (java.io.InputStream in = new java.io.FileInputStream(file)) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            for (int n; (n = in.read(buffer)) > 0; ) out.write(buffer, 0, n);
            return new String(out.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private static boolean hasVolumePreference(java.io.File prefs) throws java.io.IOException {
        if (!prefs.exists()) return false;
        String text = readText(prefs);
        return text.contains("UI_VOL_MUSIC=") || text.contains("UI_VOL_SOUNDS=");
    }

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
        StringBuilder first = new StringBuilder();
        if (!prefs.exists()) first.append("UI_SELECTOR_MODE=Adventure\n");
        // Audio stays off until the player asks for it on a touch screen (a phone or tablet): music that starts at
        // the first tap is not wanted there. Only a player with no saved volumes gets this: Forge's save writes
        // every preference, so a saved file names both volumes, and one who raises them in Settings keeps that.
        // Forge's libGDX port plays music and sounds by volume (SoundSystem), so 0 is off.
        if (isTouchDevice() && !hasVolumePreference(prefs)) first.append("UI_VOL_MUSIC=0\nUI_VOL_SOUNDS=0\n");
        if (first.length() > 0) {
            String old = prefs.exists() ? readText(prefs) : "";
            try (java.io.Writer w = new java.io.OutputStreamWriter(new java.io.FileOutputStream(prefs),
                    java.nio.charset.StandardCharsets.UTF_8)) {
                w.write(old + (old.isEmpty() || old.endsWith("\n") ? "" : "\n") + first);
            }
        }

        WebApplicationConfiguration config = new WebApplicationConfiguration("canvas");
        // Fill the visible viewport, and follow it (with gdx-teavm's auto size, resize events resize the canvas
        // and call Forge.resize; index.html says how big, DensityGraphics sets the backing store). Adventure shows more of the map where the window isn't 16:9 and keeps
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
        // The canvas is drawn at the device pixel ratio while the game keeps its CSS-pixel layout (DensityGraphics).
        new WebApplication(new MainThreadListener(app), config) {
            @Override
            protected com.github.xpenatan.gdx.teavm.backends.web.WebGraphics createGraphics(WebApplicationConfiguration c) {
                return new DensityGraphics(c);
            }
        };
    }
}
