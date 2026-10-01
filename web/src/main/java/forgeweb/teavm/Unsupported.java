package forgeweb.teavm;

import org.teavm.model.BasicBlock;
import org.teavm.model.ClassHolder;
import org.teavm.model.ClassHolderTransformer;
import org.teavm.model.ClassHolderTransformerContext;
import org.teavm.model.MethodHolder;
import org.teavm.model.MethodReference;
import org.teavm.model.Program;
import org.teavm.model.ValueType;
import org.teavm.model.Variable;
import org.teavm.model.instructions.ConstructInstruction;
import org.teavm.model.instructions.ExitInstruction;
import org.teavm.model.instructions.InvocationType;
import org.teavm.model.instructions.InvokeInstruction;
import org.teavm.model.instructions.RaiseInstruction;
import org.teavm.model.instructions.StringConstantInstruction;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Replaces the bodies of Forge methods that only Classic modes use with
 * {@code throw new UnsupportedOperationException(...)}. Whatever only those methods reach
 * (whole libraries, in XStream's case) then drops out of app.js, and a call that does happen
 * fails loudly instead of doing something half-working. Find candidates in out/reach-game.txt
 * (the constructor/static calls into each package).
 */
public class Unsupported implements ClassHolderTransformer {
    /** class -> method names (all overloads) whose body becomes a throw. */
    private static final Map<String, Set<String>> METHODS = new HashMap<>();
    /** class -> void methods whose body becomes an empty return (called on shared paths). */
    private static final Map<String, Set<String>> NOOP = new HashMap<>();

    static {
        // Classic save data (quest, gauntlet, tournament) is read/written with XStream, a
        // reflection-driven serializer: its Class.newInstance calls made TeaVM keep the no-arg
        // constructor of nearly every class. These are the only places that create one.
        METHODS.put("forge.gamemodes.quest.io.QuestDataIO", Set.of("getSerializer"));
        METHODS.put("forge.gamemodes.gauntlet.GauntletIO", Set.of("getSerializer"));
        METHODS.put("forge.gamemodes.tournament.TournamentIO", Set.of("getSerializer"));
        METHODS.put("forge.gamemodes.quest.bazaar.QuestBazaarManager", Set.of("load"));
        METHODS.put("forge.gamemodes.quest.bazaar.QuestPetStorage", Set.of("<init>"));
        // Quest save data internals set through reflection.
        METHODS.put("forge.gamemodes.quest.data.QuestAssets", Set.of("setItemLevel"));

        // Classic's home menus build each of their screens by reflection (Class.getConstructor
        // on every screen class): quest, planar conquest, gauntlet, online, puzzle, ...
        // Adventure never opens them.
        METHODS.put("forge.screens.home.NewGameMenu$NewGameScreen", Set.of("initializeScreen"));
        METHODS.put("forge.screens.home.LoadGameMenu$LoadGameScreen", Set.of("initializeScreen"));
        METHODS.put("forge.screens.online.OnlineMenu$OnlineScreen", Set.of("initializeScreen"));
        // Classic's home screen menus (openHomeScreen is only a fallback path): no menu opens.
        NOOP.put("forge.screens.home.HomeScreen", Set.of("openMenu"));
        // Offer to bulk-download card images (desktop CDN); the web build fetches art on demand.
        NOOP.put("forge.Forge", Set.of("maybePromptForBulkCdnSync"));
    }

    @Override
    public void transformClass(ClassHolder cls, ClassHolderTransformerContext context) {
        Set<String> names = METHODS.get(cls.getName());
        Set<String> noops = NOOP.get(cls.getName());
        if (names == null && noops == null) return;
        for (MethodHolder method : cls.getMethods()) {
            if (method.getProgram() == null) continue;
            if (names != null && names.contains(method.getName())) {
                method.setProgram(throwing(method, cls.getName() + "." + method.getName()
                        + " is not available in the web build (Adventure only)"));
            } else if (noops != null && noops.contains(method.getName())
                    && method.getResultType() == ValueType.VOID) {
                method.setProgram(empty(method));
            }
        }
    }

    private static Program empty(MethodHolder method) {
        Program program = new Program();
        for (int i = 0; i <= method.parameterCount(); i++) {
            program.createVariable();
        }
        program.createBasicBlock().add(new ExitInstruction());
        return program;
    }

    private static Program throwing(MethodHolder method, String message) {
        Program program = new Program();
        // Variable 0 is `this` (unused for static methods), then one per parameter.
        for (int i = 0; i <= method.parameterCount(); i++) {
            program.createVariable();
        }
        BasicBlock block = program.createBasicBlock();

        Variable text = program.createVariable();
        StringConstantInstruction constant = new StringConstantInstruction();
        constant.setConstant(message);
        constant.setReceiver(text);
        block.add(constant);

        Variable exception = program.createVariable();
        ConstructInstruction construct = new ConstructInstruction();
        construct.setType("java.lang.UnsupportedOperationException");
        construct.setReceiver(exception);
        block.add(construct);

        InvokeInstruction init = new InvokeInstruction();
        init.setType(InvocationType.SPECIAL);
        init.setInstance(exception);
        init.setMethod(new MethodReference("java.lang.UnsupportedOperationException", "<init>",
                ValueType.object("java.lang.String"), ValueType.VOID));
        init.setArguments(text);
        block.add(init);

        RaiseInstruction raise = new RaiseInstruction();
        raise.setException(exception);
        block.add(raise);
        return program;
    }
}
