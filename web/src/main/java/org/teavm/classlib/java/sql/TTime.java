package org.teavm.classlib.java.sql;

/** Only what XStream's SqlTimeConverter needs. */
public class TTime extends java.util.Date {
    public TTime(long millis) { super(millis); }

    @SuppressWarnings("deprecation")
    public static TTime valueOf(String s) {
        String[] p = s.split(":");
        return new TTime(new java.util.Date(70, 0, 1, Integer.parseInt(p[0]), Integer.parseInt(p[1]),
                Integer.parseInt(p[2])).getTime());
    }

    @Override
    @SuppressWarnings("deprecation")
    public String toString() {
        return String.format("%02d:%02d:%02d", getHours(), getMinutes(), getSeconds());
    }
}
