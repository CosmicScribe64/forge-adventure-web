package forge.web;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.github.xpenatan.gdx.teavm.backends.web.WebApplicationConfiguration;
import com.github.xpenatan.gdx.teavm.backends.web.WebGLGraphics;
import org.teavm.jso.JSBody;

/**
 * The canvas backing store at the device pixel ratio (capped, see index.html's forgePixelRatio), while the
 * game keeps seeing the CSS size: {@code getWidth()} and {@code getHeight()} are the logical size that
 * Forge lays out in and that input coordinates use, and {@code getBackBufferWidth()} and
 * {@code getBackBufferHeight()} are the real pixels. libGDX's viewports and scissors convert between the
 * two through HdpiUtils. gdx-teavm's own {@code usePhysicalPixels} would report the physical size as the
 * width, so every UI would shrink by the ratio.
 *
 * <p>The CSS size of the canvas, and the logical size, come from index.html (forgeViewSize), which follows
 * the visible viewport; gdx-teavm's resize handler calls {@link #setCanvasSize} on every window resize.
 * wiki/concepts/display-and-viewport.md.
 */
public class DensityGraphics extends WebGLGraphics {
    // No initializers: the superclass constructor calls setCanvasSize before these would run.
    private int logicalWidth;
    private int logicalHeight;

    public DensityGraphics(WebApplicationConfiguration config) {
        super(config);
    }

    @Override
    protected void setCanvasSize(int width, int height, boolean usePhysicalPixels) {
        int w = viewWidth(width);
        int h = viewHeight(height);
        if (w <= 0 || h <= 0) return;
        double ratio = pixelRatio();
        logicalWidth = w;
        logicalHeight = h;
        int bw = (int) Math.round(w * ratio);
        int bh = (int) Math.round(h * ratio);
        // Setting the size clears the buffer and reallocates it, so only when it changed.
        if (canvas.getWidth() != bw) canvas.setWidth(bw);
        if (canvas.getHeight() != bh) canvas.setHeight(bh);
    }

    @Override
    public int getWidth() {
        return logicalWidth > 0 ? logicalWidth : super.getWidth();
    }

    @Override
    public int getHeight() {
        return logicalHeight > 0 ? logicalHeight : super.getHeight();
    }

    @Override
    public void resize(ApplicationListener appListener, int width, int height) {
        Gdx.gl.glViewport(0, 0, getBackBufferWidth(), getBackBufferHeight());
        appListener.resize(width, height);
    }

    /** The CSS size index.html gave the canvas, or the size asked for when the page has none. */
    @JSBody(params = "fallback", script = "return window.forgeViewSize ? window.forgeViewSize().w : fallback;")
    private static native int viewWidth(int fallback);

    @JSBody(params = "fallback", script = "return window.forgeViewSize ? window.forgeViewSize().h : fallback;")
    private static native int viewHeight(int fallback);

    @JSBody(script = "return window.forgePixelRatio ? window.forgePixelRatio() : 1;")
    private static native double pixelRatio();
}
