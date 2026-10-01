package forgeweb.teavm;

import org.teavm.classlib.ReflectionContext;
import org.teavm.classlib.ReflectionSupplier;
import org.teavm.model.ClassReader;
import org.teavm.model.FieldReader;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodReader;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Just the reflection metadata Forge needs, and no more. gdx-teavm's reflection() patterns in
 * build.gradle.kts expose every field and method of a class. Used for all of Forge's reflective
 * code, they pushed TeaVM past the memory Docker can give it. So only libGDX Json's data classes
 * use those patterns (its generic-field lookup needs them); everything else is here.
 * Registered in META-INF/services/org.teavm.classlib.ReflectionSupplier; SelfTest checks both uses.
 */
public class WebReflection implements ReflectionSupplier {
    /** Constructors: the rules engine builds these by reflection (TriggerType, ReplacementType,
     *  Keyword, ApiType through ReflectionUtil, SpellApiToAi). */
    private static final String[] CONSTRUCTORS = {
        "forge.game.trigger.",
        "forge.game.replacement.",
        "forge.game.keyword.",
        "forge.game.ability.effects.",
        "forge.ai.ability.",
    };

    /** Event handler methods (receive.../recieve..., one parameter) of the classes that subscribe
     *  to Forge's game and UI events (see forgeweb.stub.com.google.common.eventbus.EventBus). */
    private static final String[] EVENT_LISTENERS = {
        "forge.game.GameLogFormatter",
        "forge.gamemodes.match.HostedMatch",   // and its anonymous listeners
        "forge.gamemodes.quest.QuestController",
        "forge.gui.control.FControlGamePlayback",
        "forge.gui.control.FControlGameEventHandler",
        "forge.gui.control.GameEventForwarder",
        "forge.haptic.HapticEngine",
        "forge.sound.SoundSystem",
        "forgeweb.selftest.",
    };

    /** Fields: non-Json classes that Adventure saves serialize field by field (TObjectOutputStream). */
    private static final String[] FIELDS = {
        "forge.deck.Deck",
        "forge.deck.DeckBase",
        "forge.deck.CardPool",
        "forge.util.ItemPool",
        "forge.item.PaperCard",       // and PaperCard$PaperCardFlags
        "org.apache.commons.lang3.tuple.",
        "com.badlogic.gdx.math.Vector2",
        "com.badlogic.gdx.math.Rectangle",
    };

    /** Classes whose Java serialization hooks the web object streams call (the classes listed in
     *  forgeweb.compat.SerialHooks, which warns at startup, and SelfTest fails, if one's hooks
     *  aren't callable). Without them the streams silently fall back to plain fields, so a save header
     *  came back empty and loaded cards had no rules (PaperCard.readObject restores its transient
     *  fields). Only these, because TeaVM asks about many more classes, and Guava's collections have
     *  hooks that need JDK classes TeaVM lacks. */
    private static final String[] SERIAL_CLASSES = {
        "forge.adventure.world.WorldSaveHeader",
        "forge.item.PaperCard",
        "forge.deck.Deck",
    };

    private static final String[] SERIAL_HOOKS = {
        "writeObject(Ljava/io/ObjectOutputStream;)V",
        "readObject(Ljava/io/ObjectInputStream;)V",
        "readResolve()Ljava/lang/Object;",
        "writeReplace()Ljava/lang/Object;",
    };

    @Override
    public Collection<String> getAccessibleFields(ReflectionContext context, String className) {
        if (!matches(className, FIELDS)) return Collections.emptyList();
        ClassReader cls = context.getClassSource().get(className);
        if (cls == null) return Collections.emptyList();
        List<String> names = new ArrayList<>();
        for (FieldReader f : cls.getFields()) names.add(f.getName());
        return names;
    }

    @Override
    public Collection<MethodDescriptor> getAccessibleMethods(ReflectionContext context, String className) {
        boolean ctors = matches(className, CONSTRUCTORS);
        boolean handlers = matches(className, EVENT_LISTENERS);
        boolean serial = java.util.Arrays.asList(SERIAL_CLASSES).contains(className);
        if (!ctors && !handlers && !serial) return Collections.emptyList();
        ClassReader cls = context.getClassSource().get(className);
        if (cls == null) return Collections.emptyList();
        List<MethodDescriptor> out = new ArrayList<>();
        for (MethodReader m : cls.getMethods()) {
            String name = m.getName();
            if (serial && java.util.Arrays.asList(SERIAL_HOOKS).contains(m.getDescriptor().toString())) {
                out.add(m.getDescriptor());
            }
            if (ctors && name.equals("<init>")) out.add(m.getDescriptor());
            if (handlers && m.parameterCount() == 1 && (name.startsWith("receive") || name.startsWith("recieve"))) {
                out.add(m.getDescriptor());
            }
        }
        return out;
    }

    private static boolean matches(String className, String[] prefixes) {
        for (String p : prefixes) {
            if (className.equals(p) || className.startsWith(p.endsWith(".") ? p : p + "$")) return true;
        }
        return false;
    }
}
