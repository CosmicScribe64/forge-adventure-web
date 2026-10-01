package forgeweb.teavm;

import org.teavm.model.BasicBlock;
import org.teavm.model.ClassHolder;
import org.teavm.model.ClassHolderTransformer;
import org.teavm.model.ClassHolderTransformerContext;
import org.teavm.model.Instruction;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodHolder;
import org.teavm.model.MethodReference;
import org.teavm.model.Program;
import org.teavm.model.ValueType;
import org.teavm.model.Variable;
import org.teavm.model.instructions.InvocationType;
import org.teavm.model.instructions.InvokeInstruction;

import java.util.HashMap;
import java.util.Map;

/**
 * Rewrites calls to JDK and libGDX methods that TeaVM's class library lacks into calls to
 * static helpers in {@code forgeweb.compat}. An instance call {@code file.toPath()}
 * becomes {@code JdkCompat.toPath(file)}: the receiver becomes the first argument.
 *
 * To add one: implement the helper (same name; receiver first for instance methods)
 * and add a line to the table below.
 */
public class CallRedirector implements ClassHolderTransformer {
    private static final String JDK = "forgeweb.compat.JdkCompat";
    private static final String GDX = "forgeweb.compat.GdxCompat";

    /** "owner.name(desc)" -> [helper class, receiver type for instance calls or null for static]. */
    private static final Map<String, String[]> REDIRECTS = new HashMap<>();
    /** "name(desc)" matched on any owner (for example List, Set and ArrayList.parallelStream). */
    private static final Map<String, String[]> ANY_OWNER = new HashMap<>();
    /** Constructors missing an overload: call the shorter one, dropping the last argument. */
    private static final Map<String, String> CTOR_DROP_LAST_ARG = new HashMap<>();
    /** Owner renames: invokeinterface on the first type is re-issued against the second. */
    private static final Map<String, String> OWNER_RENAMES = new HashMap<>();

    static {
        instance("java.lang.Throwable", "addSuppressed(Ljava/lang/Throwable;)V");
        instance("java.lang.Runtime", "maxMemory()J");
        instance("java.lang.Runtime", "availableProcessors()I");
        instance("java.lang.Runtime", "addShutdownHook(Ljava/lang/Thread;)V");
        instance("java.lang.Runtime", "removeShutdownHook(Ljava/lang/Thread;)Z");
        instance("java.io.File", "toPath()Ljava/nio/file/Path;");
        instance("java.util.Date", "toInstant()Ljava/time/Instant;");
        instance("java.util.UUID", "getMostSignificantBits()J");
        instance("java.util.UUID", "getLeastSignificantBits()J");
        instance("java.util.Properties", "store(Ljava/io/Writer;Ljava/lang/String;)V");
        instance("java.lang.Thread", "stop()V");
        instance("java.lang.Thread", "getStackTrace()[Ljava/lang/StackTraceElement;");
        statik("java.util.UUID", "randomUUID()Ljava/util/UUID;");
        statik("java.lang.Thread", "sleep(J)V");
        statik("java.lang.Thread", "sleep(JI)V");
        instance("java.lang.reflect.Constructor", "canAccess(Ljava/lang/Object;)Z");
        instance("java.lang.Package", "getImplementationVersion()Ljava/lang/String;");
        instance("java.lang.ClassLoader", "getResources(Ljava/lang/String;)Ljava/util/Enumeration;");
        instance("java.net.URL", "openConnection(Ljava/net/Proxy;)Ljava/net/URLConnection;");
        instance("java.net.HttpURLConnection", "getContentLengthLong()J");
        instance("java.net.URLConnection", "getContentLengthLong()J");
        statik("java.lang.System", "getenv()Ljava/util/Map;");
        instance("java.io.PrintStream", "printf(Ljava/lang/String;[Ljava/lang/Object;)Ljava/io/PrintStream;");
        instance("java.io.PrintStream", "printf(Ljava/util/Locale;Ljava/lang/String;[Ljava/lang/Object;)Ljava/io/PrintStream;");
        instance("java.io.PrintStream", "format(Ljava/lang/String;[Ljava/lang/Object;)Ljava/io/PrintStream;");
        statik("java.lang.String", "format(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;");
        statik("java.lang.String", "format(Ljava/util/Locale;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;");
        instance("java.lang.String", "formatted([Ljava/lang/Object;)Ljava/lang/String;");
        statik("java.util.Collections", "emptyNavigableMap()Ljava/util/NavigableMap;");
        statik("java.util.Collections", "unmodifiableSortedSet(Ljava/util/SortedSet;)Ljava/util/SortedSet;");
        statik("java.util.Date", "from(Ljava/time/Instant;)Ljava/util/Date;");
        ANY_OWNER.put("parallelStream()Ljava/util/stream/Stream;", new String[] {JDK, "java.util.Collection"});

        for (String owner : new String[] {"com.badlogic.gdx.Files",
                "com.github.xpenatan.gdx.teavm.backends.web.WebFiles"}) {
            REDIRECTS.put(owner + ".absolute(Ljava/lang/String;)Lcom/badlogic/gdx/files/FileHandle;", new String[] {GDX, "com.badlogic.gdx.Files"});
            REDIRECTS.put(owner + ".external(Ljava/lang/String;)Lcom/badlogic/gdx/files/FileHandle;", new String[] {GDX, "com.badlogic.gdx.Files"});
            REDIRECTS.put(owner + ".getFileHandle(Ljava/lang/String;Lcom/badlogic/gdx/Files$FileType;)Lcom/badlogic/gdx/files/FileHandle;",
                    new String[] {GDX, "com.badlogic.gdx.Files"});
        }
        for (String owner : new String[] {"com.badlogic.gdx.Application",
                "com.github.xpenatan.gdx.teavm.backends.web.WebApplication"}) {
            REDIRECTS.put(owner + ".postRunnable(Ljava/lang/Runnable;)V", new String[] {GDX, "com.badlogic.gdx.Application"});
        }
        for (String owner : new String[] {"com.badlogic.gdx.Graphics",
                "com.github.xpenatan.gdx.teavm.backends.web.WebGraphics"}) {
            REDIRECTS.put(owner + ".getDeltaTime()F", new String[] {GDX, "com.badlogic.gdx.Graphics"});
            REDIRECTS.put(owner + ".getRawDeltaTime()F", new String[] {GDX, "com.badlogic.gdx.Graphics"});
        }
        REDIRECTS.put("com.badlogic.gdx.Input.setInputProcessor(Lcom/badlogic/gdx/InputProcessor;)V",
                new String[] {GDX, "com.badlogic.gdx.Input"});
        REDIRECTS.put("com.badlogic.gdx.Input.getInputProcessor()Lcom/badlogic/gdx/InputProcessor;",
                new String[] {GDX, "com.badlogic.gdx.Input"});
        REDIRECTS.put("com.github.xpenatan.gdx.teavm.backends.web.assetloader.AssetLoader.isAssetLoaded(Lcom/badlogic/gdx/Files$FileType;Ljava/lang/String;)Z",
                new String[] {GDX, "com.github.xpenatan.gdx.teavm.backends.web.assetloader.AssetLoader"});
        REDIRECTS.put("com.badlogic.gdx.utils.BufferUtils.isUnsafeByteBuffer(Ljava/nio/ByteBuffer;)Z", new String[] {GDX, null});
        REDIRECTS.put("com.badlogic.gdx.utils.BufferUtils.getUnsafeBufferAddress(Ljava/nio/Buffer;)J", new String[] {GDX, null});

        // Startup progress onto the page (forgeweb.compat.Progress).
        String progress = "forgeweb.compat.Progress";
        REDIRECTS.put("forge.CardStorageReader$ProgressObserver.setOperationName(Ljava/lang/String;Z)V",
                new String[] {progress, "forge.CardStorageReader$ProgressObserver"});
        REDIRECTS.put("forge.CardStorageReader$ProgressObserver.report(II)V",
                new String[] {progress, "forge.CardStorageReader$ProgressObserver"});
        REDIRECTS.put("forge.toolbox.FProgressBar.setDescription(Ljava/lang/String;)V",
                new String[] {progress, "forge.toolbox.FProgressBar"});
        REDIRECTS.put("forge.gui.FThreads.invokeInEdtLater(Ljava/lang/Runnable;)V", new String[] {progress, null});
        REDIRECTS.put("forge.gui.FThreads.invokeInEdtNowOrLater(Ljava/lang/Runnable;)V", new String[] {progress, null});

        // Case-insensitive compares without a Unicode table lookup per char (card database maps).
        // Also rewrites the call inside TeaVM's String.CASE_INSENSITIVE_ORDER.
        String strings = "forgeweb.compat.StringCompat";
        REDIRECTS.put("java.lang.String.compareToIgnoreCase(Ljava/lang/String;)I", new String[] {strings, "java.lang.String"});
        REDIRECTS.put("java.lang.String.equalsIgnoreCase(Ljava/lang/String;)Z", new String[] {strings, "java.lang.String"});

        REDIRECTS.put("forge.adventure.world.World.generateNew(J)Z",
                new String[] {"forgeweb.compat.GameCompat", "forge.adventure.world.World"});

        // new ZipFile(name, charset) -> new ZipFile(name); Forge already falls back to this.
        CTOR_DROP_LAST_ARG.put("java.util.zip.ZipFile.<init>(Ljava/lang/String;Ljava/nio/charset/Charset;)V",
                "<init>(Ljava/lang/String;)V");

        // (BlockingDeque calls used to be renamed to Deque here; LinkedBlockingDeque is now shadowed
        // with a real BlockingDeque, org/teavm/classlib/java/util/concurrent/TLinkedBlockingDeque.)
    }

    private static void instance(String owner, String desc) {
        REDIRECTS.put(owner + "." + desc, new String[] {JDK, owner});
    }

    private static void statik(String owner, String desc) {
        REDIRECTS.put(owner + "." + desc, new String[] {JDK, null});
    }

    @Override
    public void transformClass(ClassHolder cls, ClassHolderTransformerContext context) {
        if (cls.getName().startsWith("forgeweb.compat.")) {
            return;
        }
        for (MethodHolder method : cls.getMethods()) {
            Program program = method.getProgram();
            if (program == null) continue;
            for (int i = 0; i < program.basicBlockCount(); i++) {
                BasicBlock block = program.basicBlockAt(i);
                for (Instruction insn : block) {
                    if (insn instanceof InvokeInstruction) {
                        redirect((InvokeInstruction) insn);
                    }
                }
            }
        }
    }

    private static void redirect(InvokeInstruction inv) {
        MethodReference ref = inv.getMethod();
        String desc = ref.getDescriptor().toString();

        String shorter = CTOR_DROP_LAST_ARG.get(ref.getClassName() + "." + desc);
        if (shorter != null) {
            inv.setMethod(new MethodReference(ref.getClassName(), MethodDescriptor.parse(shorter)));
            java.util.List<? extends Variable> a = inv.getArguments();
            inv.setArguments(a.subList(0, a.size() - 1).toArray(new Variable[0]));
            return;
        }

        String renamedOwner = OWNER_RENAMES.get(ref.getClassName());
        if (renamedOwner != null) {
            inv.setMethod(new MethodReference(renamedOwner, ref.getDescriptor()));
            return;
        }

        String[] target = REDIRECTS.get(ref.getClassName() + "." + desc);
        if (target == null) {
            target = ANY_OWNER.get(desc);
            if (target == null) return;
        }
        ValueType[] params = ref.getParameterTypes();
        ValueType[] signature;
        Variable[] args;
        if (inv.getInstance() != null) {
            signature = new ValueType[params.length + 2];
            signature[0] = ValueType.object(target[1]);
            System.arraycopy(params, 0, signature, 1, params.length);
            args = new Variable[inv.getArguments().size() + 1];
            args[0] = inv.getInstance();
            for (int k = 0; k < inv.getArguments().size(); k++) {
                args[k + 1] = inv.getArguments().get(k);
            }
        } else {
            signature = new ValueType[params.length + 1];
            System.arraycopy(params, 0, signature, 0, params.length);
            args = inv.getArguments().toArray(new Variable[0]);
        }
        signature[signature.length - 1] = ref.getReturnType();
        inv.setInstance(null);
        inv.setType(InvocationType.SPECIAL);
        inv.setMethod(new MethodReference(target[0], ref.getName(), signature));
        inv.setArguments(args);
    }
}
