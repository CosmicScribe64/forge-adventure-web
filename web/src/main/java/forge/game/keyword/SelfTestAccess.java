package forge.game.keyword;

import java.util.ArrayList;
import java.util.List;

/** Lets forgeweb.selftest.SelfTest see each Keyword's implementation class (package-private). */
public final class SelfTestAccess {
    private SelfTestAccess() {
    }

    public static List<Class<?>> keywordClasses() {
        List<Class<?>> out = new ArrayList<>();
        for (Keyword k : Keyword.values()) {
            if (k.type != null && !out.contains(k.type)) out.add(k.type);
        }
        return out;
    }
}
