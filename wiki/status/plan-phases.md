---
type: status
sources: [PLAN.md, NOTES.md#round-9, NOTES.md#round-10, NOTES.md#round-11, patches/forge-web.patch]
updated: 2026-10-01
tags: [plan, status]
---

# Plan phases

Tracks PLAN.md's phases against their exit criteria. PLAN.md is the plan, and this page is the
compiled status, including things found in code that PLAN's Status section doesn't say yet.

| # | Phase | Exit criterion | Status (2026-09-29) |
|---|---|---|---|
| 0 | Baseline | `scripts/measure` prints the numbers | **Done.** `?seed=N`, build peak memory, heap and pixmap accounting ([[metrics]]) |
| 1 | Backend: wasm GC spike | decision recorded | **Done.** Stay on JS ([[stay-on-js-backend]]) |
| 2 | Compile only what Adventure uses | app.js and build heap measurably smaller; tests pass | **Started.** `ReachReport` and `Unsupported` stubs exist ([[classic-code-pruning]]); size not re-measured |
| 3 | Systemic correctness | SelfTest passes without the isGameOver patch; audit reports zero uncovered reflective sites | **Half met.** Borrowed monitors landed, and the isGameOver patch is gone (verified in the patch file). `ReflectAudit` exists; the diff against registries, the unified declaration and the EventBus warning are open ([[reflection-on-teavm]], [[green-threads]]) |
| 4 | World generation | under about 10 s on desktop; page never unresponsive for more than 100 ms | **Time met** (6.6 s). The responsiveness check is left ([[world-generation]]) |
| 5 | Memory for phones | overworld under 1 GB (JS heap and GPU) at a phone viewport | **Started, and now the top priority.** AsyncResult leak, lazy pixmap mirrors and FreeType leak fixed. Phones crash: the main menu costs about 1.3 GB (2026-10-01), and the target for iPhone is under about 700 MB. Next steps are in [[memory-budget]] |
| 6 | I/O and persistence | no sync XHR on the normal path; cached second load; old saves load | **Started (R11).** The startup pack removes the sync XHRs of a new game up to the first map; game data gzipped. Async fetch, service worker and save versioning not started ([[virtual-file-system]], [[saves]]) |
| 7 | Test infrastructure, then continue the play-through | harness driven by commands; win a duel, visit a shop, finish "travel to town" | **Largely done.** `?test` only, duel won, shop purchase; quest and harness gaps remain ([[webtest-harness]]) |

## Open question
Phase 3 lists "a TeaVM transformer drops `synchronized` from methods that cannot suspend". The
borrowed-monitor `TObject` already makes contended non-suspending entries safe. Is the
transformer still wanted (for example for clarity or speed), or is it superseded? The answer
isn't recorded.

## See also
[[open-issues]] · [[src-plan-md]]
