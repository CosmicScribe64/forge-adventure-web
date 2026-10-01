---
type: decision
status: active
sources: [NOTES.md#round-8, web/build.gradle.kts, web/src/main/java/forgeweb/teavm/WebReflection.java]
updated: 2026-10-01
tags: [reflection, build]
---

# Decision: a narrow reflection budget

**Round 8.**

## Context
TeaVM needs about 5 GB, and Docker's VM kills it above that. gdx-teavm's `reflection()` exposes
every field and method. The first save serializer called `getDeclaredMethods()` and `invoke()`
on any class. Together they made nearly all of Forge reachable through reflection, so builds took
over 20 minutes and then ran out of memory.

## Choice
- gdx `reflection()` patterns **only** where libGDX's registry needs them (Json data classes,
  skin and UI libs).
- Everything else goes through `WebReflection`, with the minimum per use: fields only for save
  classes, constructors only for rules-engine factories, named hook methods for `SerialHooks`
  classes, `receive*` for EventBus listeners.
- Serializer hooks only for classes registered with literals.

## Result
The game build went back to about 5 min, and the self-test to about 1 min (about 35 s of checks
as of Round 11).

## Cost and follow-up
There are four registries to keep in sync (`build.gradle.kts`, WebReflection, ByName and
SerialHooks), and gaps only show at runtime. PLAN Phase 3 plans a bytecode audit (`ReflectAudit`)
that fails on gaps, and a single unified declaration. See [[reflection-on-teavm]], [[plan-phases]].
