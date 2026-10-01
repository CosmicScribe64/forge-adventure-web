package forgeweb.teavm;

import org.teavm.callgraph.CallGraph;
import org.teavm.callgraph.CallGraphNode;
import org.teavm.callgraph.CallSite;
import org.teavm.dependency.AbstractDependencyListener;
import org.teavm.dependency.DependencyAgent;
import org.teavm.model.BasicBlockReader;
import org.teavm.model.ClassReader;
import org.teavm.model.MethodReader;
import org.teavm.model.MethodReference;
import org.teavm.model.ProgramReader;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes what the compiler found reachable: methods and instructions per package group, and for
 * the biggest groups the call chain from the entry point that first reaches them. It's used to find
 * the few calls that pull whole unused features into app.js. The output path is REACH_REPORT, which
 * scripts/build-web sets only with REACH=1, because the report makes the build several times
 * slower.
 */
public class ReachReport extends AbstractDependencyListener {
    private static final int GROUPS = 80;

    @Override
    public void completing(DependencyAgent agent) {
        String out = System.getenv("REACH_REPORT");
        if (out == null || out.isEmpty()) return;
        try {
            write(agent, new File(out));
        } catch (Exception e) {
            System.err.println("ReachReport failed: " + e);
            e.printStackTrace();
        }
        try {
            ReflectAudit.write(agent, new File(out.replaceAll("\\.txt$", "") + "-reflect.txt"));
        } catch (Exception e) {
            System.err.println("ReflectAudit failed: " + e);
        }
    }

    private static String group(String className) {
        String pkg = className.contains(".") ? className.substring(0, className.lastIndexOf('.')) : "";
        String[] parts = pkg.split("\\.");
        int depth = parts[0].equals("forge") ? 3 : parts[0].equals("org") || parts[0].equals("com") ? 3 : 2;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(depth, parts.length); i++) {
            if (i > 0) sb.append('.');
            sb.append(parts[i]);
        }
        return sb.toString();
    }

    private static int size(DependencyAgent agent, MethodReference ref) {
        ProgramReader p;
        try {
            ClassReader cls = agent.getClassSource().get(ref.getClassName());
            MethodReader m = cls == null ? null : cls.getMethod(ref.getDescriptor());
            p = m == null ? null : m.getProgram();
        } catch (IllegalStateException e) {
            // Lazily parsed programs (and classes generated on demand) can't be materialised
            // during the completion phase; count them as 0.
            return 0;
        }
        if (p == null) return 0;
        int n = 0;
        for (int i = 0; i < p.basicBlockCount(); i++) {
            BasicBlockReader b = p.basicBlockAt(i);
            n += b.instructionCount();
        }
        return n;
    }

    private static boolean isInstantiationOrStatic(DependencyAgent agent, MethodReference ref,
                                                   Map<MethodReference, Boolean> cache) {
        if (ref.getName().equals("<init>") || ref.getName().equals("<clinit>")) return true;
        Boolean known = cache.get(ref);
        if (known != null) return known;
        boolean result;
        try {
            ClassReader cls = agent.getClassSource().get(ref.getClassName());
            MethodReader m = cls == null ? null : cls.getMethod(ref.getDescriptor());
            result = m != null && m.hasModifier(org.teavm.model.ElementModifier.STATIC);
        } catch (IllegalStateException e) {
            result = false;
        }
        cache.put(ref, result);
        return result;
    }

    private static void write(DependencyAgent agent, File file) throws Exception {
        Map<String, int[]> groups = new HashMap<>(); // group -> {methods, instructions}
        long totalInsns = 0;
        int totalMethods = 0;
        for (MethodReference ref : agent.getReachableMethods()) {
            int n = size(agent, ref);
            int[] g = groups.computeIfAbsent(group(ref.getClassName()), k -> new int[2]);
            g[0]++;
            g[1] += n;
            totalMethods++;
            totalInsns += n;
        }

        // Entry edges: calls from outside a group into it, by the calling method. Callers in
        // java.* (generic dispatch like Class.newInstance or String.valueOf, which the call graph
        // merges across all call sites) are only counted.
        // Slow on the whole game (millions of call edges), so only with REACH_DETAIL=1.
        boolean detail = "1".equals(System.getenv("REACH_DETAIL"));
        CallGraph graph = agent.getCallGraph();
        Map<String, Map<String, Integer>> entries = new HashMap<>();
        Map<String, Integer> jdkEntries = new HashMap<>();
        Map<MethodReference, Boolean> staticCache = new HashMap<>();
        for (MethodReference ref : detail ? agent.getReachableMethods() : java.util.Collections.<MethodReference>emptyList()) {
            CallGraphNode node = graph.getNode(ref);
            if (node == null) continue;
            String from = group(ref.getClassName());
            for (CallSite site : node.getCallSites()) {
                for (CallGraphNode callee : site.getCalledMethods()) {
                    String to = group(callee.getMethod().getClassName());
                    if (to.equals(from)) continue;
                    // Only edges that bring a class in: construction and static calls. Virtual
                    // calls (toString, run, ...) reach whatever was constructed somewhere else.
                    if (!isInstantiationOrStatic(agent, callee.getMethod(), staticCache)) continue;
                    if (ref.getClassName().startsWith("java.")) {
                        jdkEntries.merge(to, 1, Integer::sum);
                        continue;
                    }
                    entries.computeIfAbsent(to, k -> new HashMap<>())
                            .merge(ref + "  ->  " + callee.getMethod(), 1, Integer::sum);
                }
            }
        }

        List<Map.Entry<String, int[]>> sorted = new ArrayList<>(groups.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getValue()[1], a.getValue()[1]));
        file.getParentFile().mkdirs();
        try (PrintWriter w = new PrintWriter(file, "UTF-8")) {
            w.printf("entry %s: %d methods, %d instructions reachable%n%n", agent.getEntryPoint(), totalMethods, totalInsns);
            w.println("# groups by instructions (share, methods, instructions)");
            for (int i = 0; i < Math.min(GROUPS, sorted.size()); i++) {
                Map.Entry<String, int[]> e = sorted.get(i);
                w.printf("%5.1f%%  %6d  %8d  %s%n", 100.0 * e.getValue()[1] / Math.max(1, totalInsns),
                        e.getValue()[0], e.getValue()[1], e.getKey());
            }
            w.println();
            w.println("# entry edges into each group: constructor and static calls from other groups (JDK callers only counted)");
            if (!detail) w.println("(build with REACH_DETAIL=1 to list them)");
            for (int i = 0; detail && i < Math.min(GROUPS, sorted.size()); i++) {
                String g = sorted.get(i).getKey();
                Map<String, Integer> in = entries.getOrDefault(g, Collections.emptyMap());
                w.println();
                w.printf("## %s  (%d entry edges, %d from JDK dispatch)%n", g, in.size(), jdkEntries.getOrDefault(g, 0));
                List<String> edges = new ArrayList<>(in.keySet());
                Collections.sort(edges);
                for (int k = 0; k < Math.min(40, edges.size()); k++) w.println("   " + edges.get(k));
                if (edges.size() > 40) w.println("   ... " + (edges.size() - 40) + " more");
            }
        }
        System.err.println("ReachReport: wrote " + file);
    }
}
