---
type: howto
sources: [NOTES.md#how-the-web-layer-plugs-in, web/src/main/java/forgeweb/teavm/CallRedirector.java]
updated: 2026-10-01
tags: [teavm, howto]
---

# How to handle a missing or broken API

When the compile reports `... was not found`, or something behaves wrongly at runtime, pick the
mechanism from this table. [[web-layer-mechanisms]] has the details.

| Situation | What to do |
|---|---|
| One JDK or libGDX **method** missing or wrong | add a static helper in `JdkCompat`, `GdxCompat` or `GameCompat` (same name, with the receiver as the first argument for instance methods) and one line in `CallRedirector`'s table |
| A whole **`java.*` class** missing, or TeaVM's version is broken | classlib shadow: `web/src/main/java/org/teavm/classlib/java/.../TName.java` |
| A **non-`java.*`** API (XML, swing) | shim in `forgeweb/shim/...` (T-prefixed) and a mapping in `ForgeWebSubstitutionPolicy`. Shims only call shims |
| A library or Forge class that **shouldn't run on the web** (telemetry, networking) | stand-in in `forgeweb/stub/<original package>/<Name>` and a policy entry |
| A Forge **method that only Classic uses** drags in code | `Unsupported` (throw or no-op) ([[classic-code-pruning]]) |
| A libGDX class **broken in gdx-teavm** | emu shadow in `web/src/main/java/emu/com/badlogic/gdx/...` |
| Forge **behaviour** must change | Forge patch ([[forge-patches-not-fork]]) |
| It's **reflective** (by name, Json, annotations) | see [[reflection-on-teavm]]; don't widen `reflection()` patterns ([[reflection-budget]]) |

Then add a [[selftest]] check that would have caught it.
