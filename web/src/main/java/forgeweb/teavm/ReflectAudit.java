package forgeweb.teavm;

import org.teavm.dependency.DependencyAgent;
import org.teavm.model.BasicBlockReader;
import org.teavm.model.ClassReader;
import org.teavm.model.MethodReader;
import org.teavm.model.MethodReference;
import org.teavm.model.ProgramReader;
import org.teavm.model.ValueType;
import org.teavm.model.VariableReader;
import org.teavm.model.instructions.AbstractInstructionReader;
import org.teavm.model.instructions.InvocationType;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Lists every reachable call into reflection (Class.forName, getMethod, newInstance, annotations,
 * libGDX ClassReflection, ServiceLoader, ...) with its constant argument when there is one.
 * TeaVM keeps reflection metadata only for what we declare, and a missing declaration fails
 * silently at runtime (null, empty list, dropped event), so every site here must be either
 * covered by a declaration or known to be harmless. Written next to the reach report.
 */
final class ReflectAudit {
    private ReflectAudit() {
    }

    private static final Set<String> CLASS_METHODS = new HashSet<>(Arrays.asList(
            "forName", "newInstance", "getMethod", "getMethods", "getDeclaredMethod", "getDeclaredMethods",
            "getField", "getFields", "getDeclaredField", "getDeclaredFields", "getConstructor",
            "getConstructors", "getDeclaredConstructor", "getDeclaredConstructors", "getAnnotation",
            "getAnnotations", "getDeclaredAnnotations", "isAnnotationPresent", "getAnnotationsByType",
            "getRecordComponents", "getEnclosingMethod"));

    private static String api(MethodReference m) {
        String owner = m.getClassName();
        String name = m.getName();
        if (owner.equals("java.lang.Class") && CLASS_METHODS.contains(name)) return "Class." + name;
        if (owner.startsWith("java.lang.reflect.")) {
            String simple = owner.substring("java.lang.reflect.".length());
            if (name.equals("invoke") || name.equals("newInstance") || name.startsWith("getAnnotation")
                    || name.equals("isAnnotationPresent") || name.equals("newProxyInstance")
                    || (simple.equals("Field") && (name.startsWith("get") || name.startsWith("set"))
                        && !name.equals("getName") && !name.equals("getType") && !name.equals("getModifiers")
                        && !name.equals("getGenericType") && !name.equals("getDeclaringClass"))) {
                return simple + "." + name;
            }
            return null;
        }
        if (owner.equals("com.badlogic.gdx.utils.reflect.ClassReflection")) {
            // Type tests and names need no metadata.
            if (name.equals("getSimpleName") || name.startsWith("is") || name.equals("getSuperclass")
                    || name.equals("getComponentType")) return null;
            return "ClassReflection." + name;
        }
        if (owner.equals("java.util.ServiceLoader") && name.startsWith("load")) return "ServiceLoader." + name;
        return null;
    }

    private static boolean skipCaller(String cls) {
        return cls.startsWith("forgeweb.") || cls.startsWith("org.teavm.") || cls.startsWith("com.github.xpenatan.")
                || cls.startsWith("java.") || cls.startsWith("javax.") || cls.startsWith("sun.");
    }

    static void write(DependencyAgent agent, File file) throws Exception {
        // caller class -> lines
        Map<String, List<String>> byClass = new TreeMap<>();
        Map<String, Integer> byApi = new TreeMap<>();
        for (MethodReference ref : agent.getReachableMethods()) {
            if (skipCaller(ref.getClassName())) continue;
            ClassReader cls;
            try {
                cls = agent.getClassSource().get(ref.getClassName());
            } catch (IllegalStateException e) {
                continue;
            }
            MethodReader m = cls == null ? null : cls.getMethod(ref.getDescriptor());
            ProgramReader p;
            try {
                p = m == null ? null : m.getProgram();
            } catch (IllegalStateException e) {
                continue;
            }
            if (p == null) continue;
            Map<Integer, String> constants = new HashMap<>();
            List<String> sites = new ArrayList<>();
            AbstractInstructionReader constantsReader = new AbstractInstructionReader() {
                @Override
                public void stringConstant(VariableReader receiver, String cst) {
                    constants.put(receiver.getIndex(), "\"" + cst + "\"");
                }

                @Override
                public void classConstant(VariableReader receiver, ValueType cst) {
                    constants.put(receiver.getIndex(), cst + ".class");
                }
            };
            AbstractInstructionReader invokeReader = new AbstractInstructionReader() {
                @Override
                public void invoke(VariableReader receiver, VariableReader instance, MethodReference method,
                                   List<? extends VariableReader> arguments, InvocationType type) {
                    String a = api(method);
                    if (a == null) return;
                    StringBuilder sb = new StringBuilder(a);
                    if (instance != null && constants.containsKey(instance.getIndex())) {
                        sb.append(" on ").append(constants.get(instance.getIndex()));
                    }
                    for (VariableReader arg : arguments) {
                        String c = constants.get(arg.getIndex());
                        if (c != null) sb.append(' ').append(c);
                    }
                    sites.add(sb.toString());
                    byApi.merge(a, 1, Integer::sum);
                }
            };
            for (int i = 0; i < p.basicBlockCount(); i++) p.basicBlockAt(i).readAllInstructions(constantsReader);
            for (int i = 0; i < p.basicBlockCount(); i++) p.basicBlockAt(i).readAllInstructions(invokeReader);
            for (String s : sites) {
                byClass.computeIfAbsent(ref.getClassName(), k -> new ArrayList<>())
                        .add(ref.getName() + ": " + s);
            }
        }
        int total = 0;
        for (List<String> l : byClass.values()) total += l.size();
        try (PrintWriter w = new PrintWriter(file, "UTF-8")) {
            w.printf("%d reachable reflective call sites in %d classes%n%n# by API%n", total, byClass.size());
            for (Map.Entry<String, Integer> e : byApi.entrySet()) w.printf("%6d  %s%n", e.getValue(), e.getKey());
            w.println();
            w.println("# by calling class");
            for (Map.Entry<String, List<String>> e : byClass.entrySet()) {
                w.println(e.getKey());
                for (String s : new java.util.TreeSet<>(e.getValue())) w.println("    " + s);
            }
        }
        System.err.println("ReflectAudit: wrote " + file + " (" + total + " sites)");
    }
}
