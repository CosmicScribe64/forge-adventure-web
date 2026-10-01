---
type: entity
sources: [NOTES.md, web/build.gradle.kts]
updated: 2026-10-01
tags: [teavm, upstream]
---

# TeaVM

TeaVM is an ahead-of-time compiler from JVM bytecode to JavaScript and WebAssembly, written by
Alexey Andreev. The project uses **0.15.0** through [[gdx-teavm]]. It does whole-program analysis, has its own
class library (`org.teavm.classlib`, `T`-prefixed classes), and emulates threads as
[[green-threads]].

## Properties that shape this project
- It needs about 5 GB of heap to analyse Forge ([[build-pipeline]]).
- It keeps reflection metadata only when asked, and **doesn't keep annotations**
  ([[reflection-on-teavm]]).
- **It has no null checks or array bounds checks.** A null access becomes a JS `TypeError` that
  Java `catch` doesn't see ([[teavm-gotchas]]).
- Small synchronized methods get non-blocking monitor entry ([[green-threads]]).
- Extension points: `SubstitutionPolicy`, `ClassHolderTransformer` plugins, `ReflectionSupplier`
  ([[web-layer-mechanisms]]).
- The wasm-gc target's coroutine transformation crashes on suspendable methods
  ([[stay-on-js-backend]]).

## Classlib bugs found
`Throwable.addSuppressed`, `Inflater` on `Z_BUF_ERROR`, `ZipFile` dummy byte,
`LinkedBlockingDeque` not blocking, the stale current thread, `crypto.randomUUID` on plain http,
contended small synchronized methods throwing, the jzlib `Deflater` being slow, `UUID` not
being `Serializable`, case-insensitive compares doing a Unicode table lookup for every character, and double-to-long
casts throwing on NaN and infinity (R11). Details in [[bug-catalog]].

## What to re-check on a TeaVM upgrade
- `TObject.java` is a fork of 0.15's ("keep in sync with TeaVM on upgrades").
- Every shadow in `org/teavm/classlib` (it may now exist upstream, or be fixed).
- The `Long_fromNumber` patch in `web/build.gradle.kts` (casting NaN and infinity to long). The
  build fails if TeaVM's snippet changed, and SelfTest's "(long) casts" check tells whether
  upstream fixed it.
- Re-run the wasm spike (`TARGET=wasm scripts/selftest`).
- Upstream candidates: the classlib bugs above.
