package com.github.tommyettinger.textra;

import java.util.ArrayList;
import java.util.List;

/** Lets forgeweb.selftest.SelfTest see textratypist's package-private effect registry. */
public final class SelfTestAccess {
    private SelfTestAccess() {
    }

    public static List<Class<? extends Effect>> effectClasses() {
        List<Class<? extends Effect>> out = new ArrayList<>();
        for (Class<? extends Effect> c : TypingConfig.EFFECT_START_TOKENS.values()) {
            if (!out.contains(c)) out.add(c);
        }
        return out;
    }
}
