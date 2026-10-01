package org.teavm.classlib.java.text;

import java.text.CharacterIterator;
import java.util.Locale;

/**
 * No ICU data on web. The factory methods throw, and Forge's TextRenderer
 * already falls back to its own word breaking when they do (as on iOS).
 */
public abstract class TBreakIterator implements Cloneable {
    public static final int DONE = -1;

    protected TBreakIterator() { }

    public abstract int first();
    public abstract int last();
    public abstract int next(int n);
    public abstract int next();
    public abstract int previous();
    public abstract int following(int offset);
    public abstract int current();
    public abstract CharacterIterator getText();
    public abstract void setText(CharacterIterator newText);

    public int preceding(int offset) {
        int pos = following(offset);
        while (pos >= offset && pos != DONE) pos = previous();
        return pos;
    }

    public boolean isBoundary(int offset) {
        return offset == 0 || following(offset - 1) == offset;
    }

    public void setText(String newText) {
        throw new UnsupportedOperationException();
    }

    public static TBreakIterator getLineInstance() { throw new UnsupportedOperationException("BreakIterator unavailable on web"); }
    public static TBreakIterator getLineInstance(Locale l) { return getLineInstance(); }
    public static TBreakIterator getWordInstance() { return getLineInstance(); }
    public static TBreakIterator getWordInstance(Locale l) { return getLineInstance(); }
    public static TBreakIterator getCharacterInstance() { return getLineInstance(); }
    public static TBreakIterator getCharacterInstance(Locale l) { return getLineInstance(); }
    public static TBreakIterator getSentenceInstance() { return getLineInstance(); }
    public static TBreakIterator getSentenceInstance(Locale l) { return getLineInstance(); }
}
