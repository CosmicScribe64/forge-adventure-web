---
type: concept
sources: [NOTES.md#baseline, NOTES.md#round-9, NOTES.md#review, PLAN.md#phase-5]
updated: 2026-10-01
tags: [memory, phones, performance]
---

# Memory budget

The phone target (PLAN Phase 5) is the **overworld under 1 GB in total (JS heap and GPU)**. The
baseline showed that the Java heap is *not* the main cost. Decoded images in libGDX's wasm
heap and other ArrayBuffers are, and wasm memory never shrinks.

## Where memory goes (baseline, 2026-09-29, headless, software GL)

| Measure | At the world |
|---|---|
| JS heap | 313 MB (mostly the card database) |
| ArrayBuffers | 627 MB, of which libGDX wasm heap 331 MB (already 331 at the title screen) |
| Renderer process | 1.46 GB |
| GPU process | 743 MB (software GL, so textures count here) |

## At the main menu (2026-10-01, headless, software GL, local server)

| Measure | Desktop 1280x720 | Phone 390x844 at 3x |
|---|---|---|
| Renderer process | 817 MB | 818 MB (886 MB peak while loading) |
| GPU process | 472 MB | 482 MB |
| JS heap used | 280 MB | 280 MB |
| ArrayBuffer backing stores | 310 MB | 308 MB |

Screen size hardly matters: the canvas is drawn at CSS pixels (390x844 even at 3x), so the cost
is data, not pixels. Phones kill the tab at this size ([[open-issues]]). Candidates to check
first: the app.js Blob URL is never revoked (a 76 MB copy), the startup pack and card zip stay
in memory after startup, and card scripts load eagerly.

## Fixed

- **gdx-teavm `AsyncResult` ran loading tasks more than once** (on a timer, and again on every
  `isDone()` poll after the second, which `finishLoadingAsset` does). So `TextureLoader`
  decoded each image a second time after upload and never disposed the second Pixmap. The fix
  is a shadowed `emu/.../utils/async/AsyncResult` that runs the task once and keeps and rethrows
  failures. Live pixmaps at the title went from 246 MB to 7 MB, the wasm heap from 331 to
  133 MB, and the renderer from 1.45 to 1.1 GB at the world. **Report upstream** ([[bug-catalog]]).
- **Pixmap JS mirrors** (`Gdx2DPixmapNative.buffer`) are created only by `getBuffer()`, and a
  stale flag that could hand out old pixels was fixed.
- **FreeType font data leak**: gdx-freetype-web returned the address through a copied `int[]`
  and leaked each font's data. The shadowed `emu FreeType.java` allocates in Java. Forge's
  font preloading had exhausted the fixed FreeType heap at some screen sizes.
- `effects/demo.gif` (one 11488x6480 texture) was left out of web data.

## Still to do (PLAN Phase 5, [[open-issues]])
- Load card scripts lazily (`LOAD_CARD_SCRIPTS_LAZILY`), since the card database dominates the
  JS heap.
- Release the minimap pixmap after upload.
- An LRU-capped card image cache (art from [[scryfall]] is cached in memory, unbounded).
- The `ImageUtil` memo is unbounded.
- Native deflate buffers the whole payload (the ~31 MB world map at the end of generation).
- Test at a phone viewport, and on a real GPU (headless GPU numbers are software GL).

## How to measure
`scripts/measure` runs the webtest `heap` step, which reports the JS heap, ArrayBuffers, wasm memories, and
live pixmaps with allocation stacks of the large ones. Build peak memory is tracked as well
(see [[metrics]], [[build-pipeline]]).
