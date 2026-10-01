package forgeweb.compat;

/**
 * Case-insensitive String comparison without TeaVM's per-character Unicode table lookup
 * (TCharacter.toLowerCase maps every char through a binary-searched table, even ASCII). Forge's
 * card database keys its TreeMaps by String.CASE_INSENSITIVE_ORDER, whose comparisons were
 * about 7% of startup. CallRedirector sends every compareToIgnoreCase and equalsIgnoreCase call here,
 * including the one inside TeaVM's own CASE_INSENSITIVE_ORDER. Same results as TeaVM's
 * versions: equal chars are equal, ASCII is lowered arithmetically, anything else as before.
 */
public final class StringCompat {
    private StringCompat() {
    }

    public static int compareToIgnoreCase(String a, String b) {
        if (a == b) return 0;
        int la = a.length(), lb = b.length(), n = Math.min(la, lb);
        for (int i = 0; i < n; i++) {
            char x = a.charAt(i), y = b.charAt(i);
            if (x == y) continue;
            x = lower(x);
            y = lower(y);
            if (x != y) return x - y;
        }
        return la - lb;
    }

    public static boolean equalsIgnoreCase(String a, String b) {
        if (a == b) return true;
        if (b == null || a.length() != b.length()) return false;
        for (int i = 0, n = a.length(); i < n; i++) {
            char x = a.charAt(i), y = b.charAt(i);
            if (x != y && lower(x) != lower(y)) return false;
        }
        return true;
    }

    private static char lower(char c) {
        if (c < 128) return c >= 'A' && c <= 'Z' ? (char) (c + 32) : c;
        return Character.toLowerCase(c);
    }
}
