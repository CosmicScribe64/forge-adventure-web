---
type: concept
sources: [NOTES.md#round-8, NOTES.md#baseline, NOTES.md#round-9, PLAN.md#phase-4, web/src/main/java/forgeweb/compat/WfcPool.java, scripts/wfc-golden]
updated: 2026-10-01
tags: [performance, worldgen, wfc, workers]
---

# World generation

A new Adventure game generates the Shandalar overworld (`World.generateNew`). Nearly all the
time goes to **wave-function collapse** (WFC, `Model.propagate`, `OverlappingModel`) over
10x10 chunks per biome structure. On the web this took 35-70 s and froze the UI. It now takes
**6.6 s** (seed 1), with the page free.

## What changed

| Step | Where | Seed-1 time |
|---|---|---|
| Baseline (under load) | | 44-70 s |
| gdx-teavm copied the whole 2800x2800 minimap pixmap out of wasm memory after each of ~490,000 draws; shadowed `Gdx2DPixmapNative` copies lazily (Round 8, earlier) | emu shadow | made it *finish* (then ~35-45 s) |
| Reuse one `OverlappingModel` per chunk size instead of one per chunk (patterns and propagator, boxed, O(T²)) | Forge patch `Model`/`BiomeStructure` | |
| `Model.compatible` flattened to one `int[]` | Forge patch | 16.1 s on the page |
| Chunks solved in **Web Workers** | `WfcPool` and `WfcWorker` | **6.6 s** |

World hash `cf3e72545c906f8e` is the same in every variant. See [[metrics]].

## Workers
- `BiomeStructure.chunkSolver` can be swapped out (Forge patch). `WebLauncher` sets it to
  `forgeweb.compat.WfcPool`.
- The pool runs `forgeweb.worker.WfcWorker`, Forge's own solver compiled as a separate 212 KB
  TeaVM build (`scripts/build-worker`, `WORKER=true`), shipped as `wfc-worker.js`.
- Chunks are independent, so the result is identical to local solving. The calling green thread
  waits without blocking the page ([[green-threads]]).
- **Fallbacks:** chunks are solved locally where the caller can't wait. After a worker error or
  a 2-minute timeout, the pool marks itself broken and solves on the page from then on.
- `?wfc=local` forces on-page solving for comparison, and `?seed=N` fixes the world.
- Progress: a floating bar shows chunks solved out of chunks sent ([[startup-and-loading]]).

## Proving the output is unchanged (`scripts/wfc-golden`)
It compiles upstream (forge HEAD) and patched sources on the JVM and compares hashes over every
structure model (N=2 and N=3, two chunk sizes, 25 seeds each) and `BiomeStructure.initialize` for
every Shandalar structure (full size, three seeds). The log prints `World hash:` per generated
world. Output goes to `out/wfc-golden/result.txt`.

## Remaining (PLAN Phase 4)
- Check that the page is never unresponsive for more than 100 ms (exit criterion; left open as
  of 2026-09-29).
- At the end of generation, the world map PNG (~31 MB raw) is deflated with native buffering,
  which causes a short memory spike on phones ([[open-issues]]).

## See also
[[forge-patches-not-fork]] (these patches are the best candidates for upstreaming) · [[memory-budget]]
