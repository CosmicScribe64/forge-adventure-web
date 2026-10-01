package org.teavm.classlib.java.text;

import java.util.Comparator;
import java.util.Locale;

/** Locale-independent approximation: accent-insensitive primary compare, then case, then exact. */
public class TCollator implements Comparator<Object>, Cloneable {
    public static final int PRIMARY = 0;
    public static final int SECONDARY = 1;
    public static final int TERTIARY = 2;
    public static final int IDENTICAL = 3;

    private int strength = TERTIARY;

    protected TCollator() { }

    public static TCollator getInstance() { return new TCollator(); }
    public static TCollator getInstance(Locale locale) { return new TCollator(); }

    public int getStrength() { return strength; }
    public void setStrength(int s) { strength = s; }
    public void setDecomposition(int d) { }

    public int compare(String a, String b) {
        int c = TNormalizer.stripAccents(a).compareToIgnoreCase(TNormalizer.stripAccents(b));
        if (c != 0 || strength == PRIMARY) return c;
        c = a.compareToIgnoreCase(b);
        if (c != 0 || strength == SECONDARY) return c;
        return a.compareTo(b);
    }

    @Override
    public int compare(Object a, Object b) {
        return compare((String) a, (String) b);
    }

    public boolean equals(String a, String b) {
        return compare(a, b) == 0;
    }
}
