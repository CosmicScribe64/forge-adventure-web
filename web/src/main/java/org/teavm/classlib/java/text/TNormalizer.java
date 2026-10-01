package org.teavm.classlib.java.text;

/**
 * Forge uses NFD/NFKD only to strip accents ("Lim-Dûl" -> "Lim-Dul"). We decompose the
 * Latin letters that appear in card names into a base letter and a combining mark, which gives
 * the same result once the caller removes \p{M}.
 */
public final class TNormalizer {
    public enum Form { NFD, NFC, NFKD, NFKC }

    private static final String ACCENTED =
            "ÀÁÂÃÄÅàáâãäåÇçÈÉÊËèéêëÌÍÎÏìíîïÑñÒÓÔÕÖòóôõöÙÚÛÜùúûüÝýÿ";
    private static final String BASE =
            "AAAAAAaaaaaaCcEEEEeeeeIIIIiiiiNnOOOOOoooooUUUUuuuuYyy";
    private static final String MARKS =
            "̧̧̀́̂̃̈̊̀́̂̃̈̊"
            + "̀́̂̈̀́̂̈̀́̂̈̀́̂̈"
            + "̃̃̀́̂̃̈̀́̂̃̈"
            + "̀́̂̈̀́̂̈́́̈";

    private TNormalizer() { }

    public static String normalize(CharSequence src, Form form) {
        String s = src.toString();
        if (form == Form.NFC || form == Form.NFKC) return s;
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            int k = ACCENTED.indexOf(c);
            if (k >= 0) {
                sb.append(BASE.charAt(k)).append(MARKS.charAt(k));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public static boolean isNormalized(CharSequence src, Form form) {
        return normalize(src, form).contentEquals(src);
    }

    static String stripAccents(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            int k = ACCENTED.indexOf(c);
            sb.append(k >= 0 ? BASE.charAt(k) : c);
        }
        return sb.toString();
    }
}
