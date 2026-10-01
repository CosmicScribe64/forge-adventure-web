---
type: decision
status: revisit
sources: [NOTES.md#backend-decision, PLAN.md#phase-1]
updated: 2026-10-01
tags: [wasm, teavm, backend]
---

# Decision: stay on the JavaScript backend

**Decided 2026-09-29 (PLAN Phase 1).** Revisit when [[gdx-teavm]] moves to a newer [[teavm|TeaVM]].

## Context
WebAssembly GC might have given faster code (world generation) and a smaller heap. The
unknowns were green threads, our classlib shadows, `ReflectionSupplier`, JSO code (`Http`,
`UserDataStore`, `LoadingScreen`), and how complete gdx-teavm's wasm backend is.

## What happened
`TARGET=wasm scripts/selftest` builds SelfTest with `gdx_teavm_web_wasm_build`. Whole-program
analysis passes, but **code generation crashes inside TeaVM**:
`CoroutineTransformation$ListSplitter.createSaveInstructions` and `WasmTypeInference.pop`, on
methods that can suspend. Only 8 methods fail, including TeaVM's own `Integer.parseIntImpl` and
`Long.parseLongImpl`, libGDX's `BitmapFont.<init>` and regexodus. This is a compiler bug in the
wasm coroutine splitter, and we can't work around it, because we need green threads. Even if it
were fixed, JS-only pieces such as `TObjectInputStream.allocateImpl` (`@JSBody`) would need wasm
variants.

## Decision
Stay on JS, and fix speed and memory directly (PLAN phases 2-6). The `wasm {}` block and
`TARGET=wasm` stay, for re-running the spike.

## Outcome since
World generation on the JS path got down to 6.6 s with Web Workers ([[world-generation]]), which
removed much of the reason to move for speed.

## Revisit when
gdx-teavm updates TeaVM, or the TeaVM coroutine bug is fixed. Then re-run
`TARGET=wasm scripts/selftest`.
