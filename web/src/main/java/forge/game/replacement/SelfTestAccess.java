package forge.game.replacement;

import java.util.ArrayList;
import java.util.List;

/** Lets forgeweb.selftest.SelfTest see each ReplacementType's class (package-private). */
public final class SelfTestAccess {
    private SelfTestAccess() {
    }

    public static List<Class<?>> replacementClasses() {
        List<Class<?>> out = new ArrayList<>();
        for (ReplacementType r : ReplacementType.values()) {
            if (r.clasz != null && !out.contains(r.clasz)) out.add(r.clasz);
        }
        return out;
    }
}
