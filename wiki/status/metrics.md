---
type: status
sources: [NOTES.md#baseline, NOTES.md#round-11, NOTES.md#round-5, NOTES.md#round-7, NOTES.md#round-8, NOTES.md#round-9, NOTES.md#round-10, scripts/measure]
updated: 2026-10-04
tags: [metrics, performance, memory]
---

# Metrics

This page tracks the numbers over time, with their conditions. Unless noted, they come from
headless Chromium with software GL (SwiftShader) and world seed 1. Measure with `scripts/measure`
(output in `out/measure/`), which needs `scripts/build-web` and `scripts/serve-web`.

## Current numbers against the baseline

| Metric | Baseline (2026-09-29) | Latest | Where |
|---|---|---|---|
| app.js | 76 MB raw, 7.1 MB gzip | same (Phase 2 not re-measured) | [[classic-code-pruning]] |
| Game build | 5m21s, 6.7 GB peak container (VM 7.7 GB) | not re-measured | [[build-pipeline]] |
| Page load to title | 28-40 s (machine busy); about 33 s before R11 | **17.7-18.3 s** (R11, quiet machine; `[ttg]` lines) | [[startup-and-loading]] |
| Game data download | 23 MB (card zip 15.8, manifest 1.3) | **7.5 MB** gzipped (R11), before the startup pack | [[virtual-file-system]] |
| Blocking requests before title | 144 sync XHRs | 0 from `res/` in the startup set (startup pack, R11) | [[virtual-file-system]] |
| World generation | 44-70 s, UI frozen | **6.6 s**, page free (workers) | [[world-generation]] |
| JS heap at world | 313 MB | not re-measured | [[memory-budget]] |
| ArrayBuffers at world | 627 MB (libGDX wasm heap 331 MB) | wasm heap 133 MB | [[memory-budget]] |
| Live pixmaps at title | 246 MB | 7 MB | [[memory-budget]] |
| Renderer process at world | 1.46 GB | about 0.89 GB (2026-10-05, music streamed, one-byte `app.js` source and packs dropped after startup; 0.96 GB before the last change) | [[memory-budget]] |
| GPU process at the main menu | 472 MB (2026-10-01) | 449 MB desktop, 433 MB phone size; 296 MB of that is live textures (2026-10-05) | [[memory-budget]] |
| Renderer process at the main menu | 817 MB (2026-10-01) | 672 MB desktop, 678 MB phone size (2026-10-05, music streamed, `app.js` source as one-byte text and packs dropped after startup; 701 and 705 MB before the last change) | [[memory-budget]] |
| GPU process | 743 MB (software GL) | not re-measured | |
| Worst frame at duel start | 1.7-2 s | 0.83 s (the rest is `Match.startGame` and the first duel frame) | [[bug-catalog]] |
| Card DB load | about 10 s for 33,980 cards (R5) | about 6.5 s in the game ("Loading cards from archive"); in SelfTest, scripts went from 4.3 to 1.6 s and the card database from 12 to 5.6 s (R11) | [[startup-and-loading]] |
| Fonts at startup | about 7.6 s (3 s generating and 4.6 s for the PNG round trip on the UI thread) | about 1.7 s (R11) | [[startup-and-loading]] |
| Autosave deflate | jzlib | 3 MB in 57 ms (CompressionStream) | [[saves]] |
| Frame rate | 60 fps on Apple M4 (real GPU) and headless, at the overworld (R8) | not re-measured | |

## Hosted-page emulation (R11, `webtest --latency 50`, machine busy, so compare phases)
| Phase | No startup pack | Startup pack | Pack, no latency |
|---|---|---|---|
| Finishing startup | 10.7 s | 3.2 s | 2.9 s |
| World generation (new game) | 19.7 s | 15.9 s | 15.4 s |
| Title / gameplay | 32.8 / 59.0 s | 27.9 / 50.1 s | 29.9 / 51.4 s |

## World generation history (seed 1)
| When | Time | Note |
|---|---|---|
| R8 | never finished, then 35-45 s | pixmap copy fix |
| Baseline | 44-70 s | busy machine |
| R9 patches, on page | 16.1 s | model reuse and flat arrays |
| R9 workers | 6.6 s | hash `cf3e72545c906f8e` unchanged |

## Build and test loop times
A full game build takes about 5 min (4m20s-5m20s, with 6.5-6.6 GB peak container memory, R11).
In SelfTest, the checks take about 35 s in the page (36 checks in R11, with the AI matches taking
about 18 s of that), plus a build of about 5 min. With `REACH=1` the build takes 10-40 min.

> [!note] Superseded
> The earlier figures were "~25 s" for SelfTest (NOTES' Fast checks section) and "~1 min"
> (Round 8), and "~2 min" for the AI matches (R8). They were re-measured in Round 11 as above.
> The checks have grown, and got faster, since then.
