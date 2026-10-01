package forgeweb.stub.forge.assets;

import forge.Forge;
import forge.gui.FThreads;

/**
 * forge.assets.AssetsDownloader checks for new builds and downloads resource zips.
 * The web build ships its assets with the page, so skip straight to loading
 * (the same path AssetsDownloader takes when there is nothing to download).
 */
public class AssetsDownloader {
    public static void checkForUpdates(boolean exited, Runnable runnable) {
        if (runnable != null) {
            Forge.getSplashScreen().getProgressBar().setDescription("Loading game resources...");
            FThreads.invokeInBackgroundThread(runnable);
            return;
        }
        Forge.isMobileAdventureMode = Forge.advStartup;
        Forge.exitAnimation(false);
    }
}
