package org.teavm.classlib.java.util;

public final class TStringJoiner {
    private final String prefix;
    private final String delimiter;
    private final String suffix;
    private StringBuilder value;
    private String emptyValue;

    public TStringJoiner(CharSequence delimiter) {
        this(delimiter, "", "");
    }

    public TStringJoiner(CharSequence delimiter, CharSequence prefix, CharSequence suffix) {
        this.delimiter = delimiter.toString();
        this.prefix = prefix.toString();
        this.suffix = suffix.toString();
        this.emptyValue = this.prefix + this.suffix;
    }

    public TStringJoiner setEmptyValue(CharSequence emptyValue) {
        this.emptyValue = emptyValue.toString();
        return this;
    }

    public TStringJoiner add(CharSequence element) {
        if (value == null) {
            value = new StringBuilder().append(prefix);
        } else {
            value.append(delimiter);
        }
        value.append(element);
        return this;
    }

    public TStringJoiner merge(TStringJoiner other) {
        if (other.value != null) {
            add(other.value.substring(other.prefix.length()));
        }
        return this;
    }

    public int length() {
        return value != null ? value.length() + suffix.length() : emptyValue.length();
    }

    @Override
    public String toString() {
        return value == null ? emptyValue : value + suffix;
    }
}
