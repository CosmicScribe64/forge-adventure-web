package org.teavm.classlib.java.awt;

import java.util.HashMap;
import java.util.Map;

/** Just enough for XStream's FontConverter to link; AWT fonts are never used on web. */
public class TFont implements java.io.Serializable {
    private final Map<Object, Object> attributes;

    public TFont(Map<?, ?> attributes) {
        this.attributes = new HashMap<>(attributes);
    }

    protected TFont(TFont font) {
        this.attributes = new HashMap<>(font.attributes);
    }

    public static TFont getFont(Map<?, ?> attributes) {
        return new TFont(attributes);
    }

    public Map<Object, Object> getAttributes() {
        return new HashMap<>(attributes);
    }
}
