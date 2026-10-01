package org.teavm.classlib.java.sql;

/** Only what XStream's SqlDateConverter needs. */
public class TDate extends java.util.Date {
    public TDate(long millis) { super(millis); }

    @SuppressWarnings("deprecation")
    public static TDate valueOf(String s) {
        String[] p = s.split("-");
        return new TDate(new java.util.Date(Integer.parseInt(p[0]) - 1900, Integer.parseInt(p[1]) - 1,
                Integer.parseInt(p[2])).getTime());
    }

    @Override
    @SuppressWarnings("deprecation")
    public String toString() {
        return String.format("%04d-%02d-%02d", getYear() + 1900, getMonth() + 1, getDate());
    }
}
