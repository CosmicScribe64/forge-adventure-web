---
type: status
sources: [NOTES.md#baseline, NOTES.md#round-11, NOTES.md#round-5, NOTES.md#round-7, NOTES.md#round-8, NOTES.md#round-9, NOTES.md#round-10, scripts/measure]
updated: 2026-10-05
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
| JS heap at the menu and world | 313 MB at the world | **256.7 MB at the menu, 283.5 MB at the world** (2026-10-05, desktop, after the lazy `CardType` sets; 277.4 and 304.4 MB before). The card database is about 170 MB of it; lazy loading forced on would give 129 and 156 MB but breaks rewards and enemy decks | [[memory-budget]] |
| ArrayBuffers at world | 627 MB (libGDX wasm heap 331 MB) | wasm heap 133 MB | [[memory-budget]] |
| Live pixmaps at title | 246 MB | 7 MB | [[memory-budget]] |
| Renderer process at world | 1.46 GB | about 0.90 GB (2026-10-05, fonts on first use, music streamed, one-byte `app.js` source and card zip and packs dropped after use; 0.96 GB before the last change) | [[memory-budget]] |
| GPU process at the main menu | 472 MB (2026-10-01) | 230 MB desktop, 217 MB phone size; 75 MB of that is live textures (2026-10-05, font sizes and duel-only sprite sheets read on first use; 456 and 435 MB, 296 MB of textures, before) | [[memory-budget]] |
| Renderer process at the main menu | 817 MB (2026-10-01) | 633 MB desktop, 631 MB phone size (2026-10-05, music streamed, fonts and sprite sheets on first use, `app.js` source as one-byte text and packs dropped after startup; 669 and 667 MB before the font change) | [[memory-budget]] |
| Overworld, renderer / JS heap / backing stores | 889 MB / 284.8 / 253.5 (2026-10-05 morning, 3 WFC workers) | **724 MB / 277.5 / 224.5** desktop, 715 / 278.4 / 222.2 phone size (2026-10-05, seed 1, headless, software GL; workers ended after generation, minimap copy dropped, chunk arrays per chunk) | [[memory-budget]] |
| `scripts/e2e-newgame` | n/a | 74 s; limits 763 / 321 / 95 MB (menu rss / heap / textures) and 895 / 349 / 155 MB (overworld), 1.25 times 608 to 610 / 257 / 75.4 and 714 to 716 / 278.4 to 278.7 / 124.0 | [[webtest-harness]] |
| `scripts/e2e-release site` (7 runs on the assembled site, 2026-10-05) | n/a | 486 s: boot 26, 26, 32 s; new game 58, 58, 68 s; cycle 218 s (desktop, Chromium phone, WebKit iPhone) | [[e2e-tests]] |
| GPU process | 743 MB (software GL) | not re-measured | |
| Worst frame at duel start | 1.7-2 s | 0.83 s (the rest is `Match.startGame` and the first duel frame) | [[bug-catalog]] |
| Card DB load | about 10 s for 33,980 cards (R5) | about 6.5 s in the game ("Loading cards from archive"); in SelfTest, scripts went from 4.3 to 1.6 s and the card database from 12 to 5.6 s (R11) | [[startup-and-loading]] |
| Fonts at startup | about 7.6 s (3 s generating and 4.6 s for the PNG round trip on the UI thread) | 0 s: sizes are made on first use (2026-10-05; 3.2 s in R11-era builds) | [[startup-and-loading]] |
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

## Minified release build (2026-10-05)
Same machine and method as [[memory-budget]]: desktop 1280x720, headless Chromium with software GL,
seed 1, the build before and after `TEAVM_OBFUSCATED=true` (same sources).
| Number | Readable | Minified |
|---|---|---|
| `app.js` after latin1 escaping | 76,123,885 bytes | 20,533,504 bytes |
| `app.js.gz` (gzip -9) | 6,771,249 bytes | 3,812,728 bytes |
| `app.js.map` | none | 4,275,179 bytes (1,252,509 gzipped) |
| Menu: JS source string (ExternalStringData in a heap snapshot) | 73.9 MB | 20.9 MB |
| Menu: renderer RSS | 603 MB | 573 MB |
| Menu: JS heap used | 257.0 MB | 246.4 MB |
| Menu: ArrayBuffer backing stores | 193.7 MB | 173.6 MB |
| Overworld (`scripts/e2e-newgame`): renderer RSS / JS heap / textures | 716 / 278.7 / 124 MB (limits comment) | 678 / 267.6 / 124.0 MB |
| Game compile | about 7 min | 7m11s with the source map |
| Startup to title (`scripts/e2e-boot`) | about 32 s | 37 s (one run each, noise is a few seconds) |
The readable column for the menu was measured on a copy of the live build, which does not have the
`allocate` fix; the fix does not touch these numbers.

## Final minified numbers and picture caches (2026-10-05)
Same machine and method as the section above, minified build with the image cache fixes. Menu and overworld
totals for desktop and phone emulation are in the table at the top of [[memory-budget]]
(desktop menu RSS 574 to 575 MB, overworld 684 to 690 MB). Other numbers from that day:
| Number | Value |
|---|---|
| gdx.wasm start size 1024 vs 256 pages, overworld desktop RSS | 673 and 680 vs 684 and 680 MB (no drop, dropped) |
| Deck editor, 608 pictures scrolled, no cache fixes: RSS / textures | 899 MB / 632 MB, still rising |
| Same with the texture cap (120) and the 64 MB picture cap | 861 to 872 MB / 337 to 354 MB, flat |
| One Scryfall picture in the in-memory file system | about 100 KB |
| One 488x680 card texture with mipmaps | about 1.7 MB |
| WFC worker regression, for the test limits | 133 MB RSS (3 workers) |

## Build and test loop times
A full game build takes about 5 min (4m20s-5m20s, with 6.5-6.6 GB peak container memory, R11).
In SelfTest, the checks take about 35 s in the page (36 checks in R11, with the AI matches taking
about 18 s of that), plus a build of about 5 min. With `REACH=1` the build takes 10-40 min.

> [!note] Superseded
> The earlier figures were "~25 s" for SelfTest (NOTES' Fast checks section) and "~1 min"
> (Round 8), and "~2 min" for the AI matches (R8). They were re-measured in Round 11 as above.
> The checks have grown, and got faster, since then.
