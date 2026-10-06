package forge.web;

import com.badlogic.gdx.Gdx;
import forge.interfaces.IDeviceAdapter;
import org.apache.commons.lang3.tuple.Pair;
import org.jupnp.UpnpServiceConfiguration;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Date;

/** Minimal browser implementation of Forge's platform hooks. */
public class WebDeviceAdapter implements IDeviceAdapter {
    @Override public boolean isConnectedToInternet() { return true; }
    @Override public boolean isConnectedToWifi() { return true; }
    @Override public boolean isTablet() { return false; }
    @Override public String getDownloadsDir() { return "/downloads/"; }
    @Override public String getVersionString() { return pageVersion(); }

    /** Set by web/html/index.html from `git describe` at build time; "dev" if the page wasn't built by build-web. */
    @org.teavm.jso.JSBody(script = "var version = window.forgeVersion;"
            + " return version && version.indexOf('GAME_') !== 0 ? version : 'dev';")
    private static native String pageVersion();
    @Override public String getLatestChanges(String commitsAtom, Date buildDateOriginal, Date maxDate) { return ""; }
    @Override public String getReleaseTag(String releaseAtom) { return ""; }
    @Override public boolean openFile(String filename) { return false; }
    @Override public void setLandscapeMode(boolean landscapeMode) { }
    @Override public void preventSystemSleep(boolean preventSleep) { }
    @Override public void restart() { }
    @Override public void exit() { Gdx.app.exit(); }
    @Override public void closeSplashScreen() { }

    @Override
    public void convertToJPEG(InputStream input, OutputStream output) throws IOException {
        input.transferTo(output);
    }

    @Override
    public void convertToPNG(InputStream input, OutputStream output) throws IOException {
        input.transferTo(output);
    }

    @Override
    public Pair<Integer, Integer> getRealScreenSize(boolean real) {
        return Pair.of(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    @Override public ArrayList<String> getGamepads() { return new ArrayList<>(); }
    @Override public UpnpServiceConfiguration getUpnpPlatformService() { return null; }
    @Override public boolean needFileAccess() { return false; }
    @Override public void requestFileAcces() { }
}
