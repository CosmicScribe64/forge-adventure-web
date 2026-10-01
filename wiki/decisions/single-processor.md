---
type: decision
status: active
sources: [NOTES.md#round-5]
updated: 2026-10-01
tags: [threads, startup]
---

# Decision: report one processor

**Round 5.** `Runtime.availableProcessors()` returns **1** (`JdkCompat`, through CallRedirector).

## Why
Card loading hung at 0%. A pool task threw before `CountDownLatch.countDown()`, so `await()`
never returned. With green threads, a pool gains nothing (there is no parallelism). With 1
processor, Forge loads inline, and a failing task throws visibly instead of hanging.

## Consequences
Any Forge code that sizes pools from the processor count gets one worker. Real parallelism only
comes from Web Workers, used explicitly ([[world-generation]]). A [[selftest]] check pins the
value.

## See also
[[one-ui-green-thread]] · [[green-threads]]
