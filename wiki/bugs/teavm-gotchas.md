---
type: bug
owner: teavm
upstream: n/a
sources: [NOTES.md]
updated: 2026-10-01
tags: [teavm, gotchas]
---

# TeaVM gotchas

These are behaviours that aren't bugs but break JVM assumptions. Check this list when a crash
"can't happen".

1. **No null checks.** A null dereference is a JS `TypeError`, not an NPE, and Java `catch`
   blocks don't see it. Forge code that relies on catching NPEs, harmless on desktop, becomes
   fatal here.
2. **No array bounds checks.** `trace[2]` on a short array reads `undefined` instead of
   throwing (so `Thread.getStackTrace` returns placeholder frames).
3. **Annotations aren't kept** at runtime (Guava EventBus `@Subscribe`).
4. **Reflection only on request.** A class loaded by name needs four things
   ([[reflection-on-teavm]]).
5. **No suspension outside a green thread.** Waiting, sleeping or a contended lock in a browser
   callback throws "Suspension point reached from non-threading context" ([[green-threads]]).
6. **Green threads switch only at suspension points.** Nothing runs in parallel, and a busy
   thread starves the page. Long work must yield (as `Progress` does).
7. **Whole-program analysis follows what's reachable.** One generic reflective call can make
   everything reachable ([[reflection-budget]]). Fast analysis reaches even more
   ([[build-pipeline]]).
8. **Shims only call shims** ([[web-layer-mechanisms]]).
9. **The classlib has gaps that compile fine** (for example, `LinkedBlockingDeque` without blocking).
   A missing *method* is reported at compile time, but wrong *behaviour* only shows at runtime.

See also [[bug-catalog]].
