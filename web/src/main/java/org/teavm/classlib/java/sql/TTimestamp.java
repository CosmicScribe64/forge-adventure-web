package org.teavm.classlib.java.sql;

/** Only what XStream's SqlTimestampConverter needs. */
public class TTimestamp extends java.util.Date {
    private int nanos;

    public TTimestamp(long millis) {
        super((millis / 1000) * 1000);
        nanos = (int) ((millis % 1000) * 1_000_000);
        if (nanos < 0) {
            nanos += 1_000_000_000;
            setTime(((millis / 1000) - 1) * 1000);
        }
    }

    @Override
    public long getTime() {
        return super.getTime() + nanos / 1_000_000;
    }

    public int getNanos() { return nanos; }
    public void setNanos(int n) { nanos = n; }
}
