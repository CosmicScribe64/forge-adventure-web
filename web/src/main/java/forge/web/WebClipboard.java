package forge.web;

import com.badlogic.gdx.utils.Clipboard;

/** In-memory clipboard; the browser clipboard API is async and permission-gated. */
public class WebClipboard implements Clipboard {
    private String contents = "";

    @Override public boolean hasContents() { return !contents.isEmpty(); }
    @Override public String getContents() { return contents; }
    @Override public void setContents(String content) { contents = content == null ? "" : content; }
}
