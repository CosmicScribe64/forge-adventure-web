---
type: concept
sources: [NOTES.md#round-5, NOTES.md#round-7, NOTES.md#round-8, NOTES.md#round-11, web/src/main/java/forgeweb/teavm/WebReflection.java, web/src/main/java/forgeweb/compat/ByName.java, web/src/main/java/forgeweb/compat/SerialHooks.java, web/build.gradle.kts]
updated: 2026-10-01
tags: [reflection, teavm]
---

# Reflection on TeaVM

[[teavm|TeaVM]] compiles the whole program ahead of time. It keeps a class, method, field or
constructor only if static analysis sees it used, and it keeps reflection metadata only when
asked. [[forge|Forge]] and its libraries reflect in many places. Each place has to be declared,
and declaring too much exhausts the build's memory (see [[reflection-budget]]).

## Three ways metadata is provided

1. **gdx-teavm `reflection("pattern")`** in `web/build.gradle.kts`. This exposes *every field
   and method* of matching classes. It is used only where libGDX's own registry is required:
   `forge.adventure.data.**` and `BiomeSprites*` (libGDX `Json` and FieldGen), `com.ray3k.tenpatch.**`
   (named in `ui_skin.json`), textratypist `effects.**` (TypingLabel builds `{EFFECT}` tags by
   reflected constructors), and gdx-controllers' `ControllerManagerStub`.
2. **`forgeweb.teavm.WebReflection`** (a `ReflectionSupplier`, registered in META-INF/services).
   It gives *just* what is needed: fields only for save classes, constructors only for the
   rules engine's reflective factories (`TriggerType`, `ReplacementType`, `Keyword`, `ApiType`,
   `SpellApiToAi`, 202 APIs), the serializer hook methods for `SerialHooks` classes, and the
   `receive*` methods of EventBus listener classes.
3. **Code shape.** For a class loaded **by name** (`forgeweb.compat.ByName.keep()`), TeaVM needs
   four things: a `reflection()` pattern (fields), a class literal (so it isn't dropped),
   `getName()` called on it (otherwise `Class.forName` can't find it), and its `Class` flowing into
   `newInstance()` (otherwise its no-arg constructor is dropped).

## Things that silently broke

- **Annotations aren't kept.** Guava's `EventBus` finds handlers via `@Subscribe`, so every
  game event was dropped. The duel ran, but the hand, library and log never updated. The fix is
  a stub `EventBus` (`forgeweb/stub/com/google/common/eventbus`) that dispatches to public
  one-argument `receive*` or `recieve*` methods (all of Forge's handlers are named that way).
  **Add new listener classes to WebReflection.** (PLAN Phase 3: warn on listeners with no
  visible handlers.)
- **No null checks.** A reflective lookup that returns null becomes a JS `TypeError`, which Java
  `catch` doesn't see. See [[teavm-gotchas]].

## Serializer hooks (`SerialHooks`)
The web object streams ([[saves]]) call private `writeObject`/`readObject`/`readResolve` only
for classes listed by **class literal** in `SerialHooks`, and the same classes must be in
WebReflection's `SERIAL_CLASSES`. Calling `getDeclaredMethods()` and `invoke()` on any class made
nearly all of Forge reachable through reflection, and builds took over 20 minutes and then ran out
of memory.

`getDeclaredMethods()` on a class literal only returns the methods TeaVM was told to keep. Until
Round 11 WebReflection didn't list the hooks at all, so every lookup returned nothing and the
streams quietly used plain fields instead. That gave empty save headers and cards without rules
([[saves]]). Exposing the four hook signatures on *every* class TeaVM asks about is too broad.
Guava's collections have hooks that need JDK classes TeaVM lacks (`InvalidObjectException`), and
that failed the build. That's why there is an exact list, a startup warning in `SerialHooks` when a listed class
has no callable hooks, and a [[selftest]] check.

## Auditing
`forgeweb.teavm.ReflectAudit` (build with `REACH=1`) writes `out/reach-*-reflect.txt`, every
reachable reflective call site. The PLAN Phase 3 goal is to compare that list with the
registries, fail on gaps, and merge WebReflection, ByName and SerialHooks into one declaration.

## Verified by
[[selftest]]: adventure `config.json` via Json, every `ui_skin.json` class, textratypist
effects, rules-engine construction, save hooks callable, gdx-controllers stub by name.

## See also
[[web-layer-mechanisms]] · [[code-map]]
