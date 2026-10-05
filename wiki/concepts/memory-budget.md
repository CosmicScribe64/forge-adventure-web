---
type: concept
sources: [NOTES.md#baseline, NOTES.md#round-9, NOTES.md#review, PLAN.md#phase-5, scripts/build-web, web/tools/latin1-js.py, web/html/index.html]
updated: 2026-10-05
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

## Music streaming (2026-10-04)

Step 1 of the plan is done. A shadow `HowlMusic`
(`web/src/main/java/com/github/xpenatan/gdx/teavm/backends/web/webaudio/howler/HowlMusic.java`)
creates each music Howl with `html5: true`, so the browser streams the track through an audio
element and nothing is decoded into samples. Sound effects (`HowlSound`) stay on Web Audio.

Conditions: headless Chromium through `scripts/webtest`, software GL, local server, world seed 1,
the same build with only the flag changed (the compiled `app.js` was patched between runs).
RSS is the renderer or GPU process from `/proc`, taken by the `heap` step.

| Measure | Web Audio (before) | Streaming (after) | Saved |
|---|---|---|---|
| Main menu, desktop 1280x720: renderer | 830 MB | 776 MB | 54 MB |
| Main menu, desktop: GPU | 460 MB | 461 MB | none |
| Main menu, phone 390x844 at 3x: renderer | 835 MB | 777 MB | 58 MB |
| Main menu, phone: GPU | 436 MB | 439 MB | none |
| Overworld (new game, 10 s after world generation), desktop: renderer | 1117 MB | 1031 MB | 86 MB |
| Overworld, desktop: GPU | 600 MB | 601 MB | none |

JS heap (281 MB) and ArrayBuffer backing stores (310 MB at the menu, 372 MB at the overworld)
did not change, because decoded audio is not counted there. A decoded track costs about 55 MB of
renderer memory, a little less than the 62 MB of samples, and the overworld run had decoded two
tracks. This is not enough alone: the menu is still about 780 MB in the renderer plus 440 to
460 MB of GPU memory, against a target under about 700 MB in total for the renderer.

Behaviour checked in the same setup:
- Music is an HTML5 Howl (`_html5` true, duration 185 s for `menus/menu2.mp3`), and it plays.
- Browsers refuse `play()` on an audio element before the first click. Web Audio Howls wait and
  start by themselves at the first gesture, but an HTML5 Howl emits `playerror` and Howler
  drops the request, so the menu music stayed silent. `HowlMusic` now retries on Howler's
  `unlock` event if the game still wants the track (a flag cleared by `pause` and `stop`). After
  a click the menu track plays from the start.
- Track changes work: seeking to the last two seconds of the menu track made the game dispose it
  and start the next one (164 s), and only one Howl stayed alive.
- `dispose()` now revokes the Blob URL, which fixes the leak noted in [[bug-catalog]].
- Volume goes through `setVolume` unchanged; the game's music volume preference was 100 in the
  test, so only the pass-through was seen, not other levels.
- Not tested on a real iPhone. WebKit may need a user gesture for every new audio element, so
  a track change on iOS could stay silent until the next tap. Test it at step 6.

## Heap snapshot and the script source (2026-10-05)

Steps 2 to 4 of the plan, in the order the evidence called for.

**Heap snapshot at the main menu** (`webtest` step `snapshot`, headless, software GL, desktop
1280x720, seed 1, build c588afb). V8 reported 310 MB of backing stores, and the snapshot
splits them like this:

| Part | Size | What it is |
|---|---|---|
| External string data | 146 MB | The source text of `app.js`: 145 MB, one string. Chrome kept it as two bytes per character (76 million characters) because 2468 characters are above U+00FF. Howler (0.03 MB), the Gdx and Module glue (1.2 MB) are the rest |
| libGDX wasm heap (`Gdx` memory) | 64 MB | Mostly the FreeType heap and live pixmaps |
| The card script zip held by the virtual file system | 27.5 MB | `Node.data` of `cardsfolder.zip` in `WebFileSystem` |
| The startup pack held in `WebFileSystem.packs` | 21.3 MB | Kept after startup for later reads |
| The window wasm memory | 16 MB | Fixed size |
| The editions pack in `packs` | 3.9 MB | Same map |
| Everything else (7000 small buffers) | about 30 MB | Files read so far, character tables, `forgePrefetch` leftovers (the 0.26 MB `blockdata.pack` that nothing takes) |

The snapshot's total was 591 MB of self size: 310 native, 120 objects, 50 strings, 35 code,
26 arrays. The 280 MB JS heap is 970,000 `String` objects (26 MB), 500,000 element arrays,
345,000 `TreeMap` nodes, 98,000 `PaperCard`s and 102,000 edition entries, so the card
database really is spread thin over small objects and no single structure dominates.

**Step 2, revoking the app.js Blob URL: no measurable change.** The page now revokes the URL as
soon as the script has run (`web/html/index.html`, `loadApp`). Measured with the same build,
before and after: renderer 773 and 776 MB at the desktop menu, 1034 and 1028 MB at the
overworld, 779 and 782 MB at the phone-size menu; backing stores 310 MB both times. Chrome
doesn't count the Blob in the renderer, so the change was not committed. The 76 MB Blob never
was the cost; the cost is the source text V8 keeps.

**The two-byte source (a new step, ahead of dropping the packs because it saves more).**
`web/tools/latin1-js.py` rewrites the 2468 characters above U+00FF (curly quotes in quest text,
accents, dashes) as `\uXXXX` escapes, and `scripts/build-web` runs it before hashing and
compressing `app.js`. It refuses to run if such a character follows a backslash. The file grows
by 9 KB. V8 then holds the source as one byte per character.

| Measure (headless, software GL, seed 1, same build, only `app.js` changed) | Before | After | Saved |
|---|---|---|---|
| Menu, desktop: renderer | 773 MB | 701 MB | 72 MB |
| Menu, desktop: ArrayBuffer backing stores | 310 MB | 237.5 MB | 72.5 MB |
| Menu, desktop: GPU | 461 MB | 450 MB | none (noise) |
| Overworld, desktop: renderer | 1034 MB | 954 MB | 80 MB |
| Overworld, desktop: backing stores | 373 MB | 300 MB | 73 MB |
| Menu, phone 390x844 at 3x: renderer | 779 MB | 705 MB | 74 MB |
| Menu, phone: GPU | 435 MB | 435 MB | none |

JS heap used stayed at 281 MB. Checked: startup, a new game to the overworld (world generated,
screenshot taken) and the [[selftest]] suite (38 of 38; it uses its own build, so it did not run
the escaped file). A gameplay save and load was not exercised: the Save button is greyed in the
starting cave and no autosave exists there, so only the selftest save checks cover saving.

Another option, not taken: minifying `app.js` (TeaVM's obfuscation) would cut the source further,
but it changes every stack trace the test tools print. That is the owner's choice.

## Findings for phones (2026-10-01)

The user's phone is an iPhone running Chrome. Every iOS browser uses WebKit, which has some of
the strictest per-tab memory limits, so the target is under about 700 MB, half of today's 1.3 GB.

Allocation sampling at the main menu (live JS memory 275 MB):
- Most of the JS heap is the card database: card-rule parsing (`CardRules$Reader`),
  `CardDb.addSetCard`, strings (`fromCharCode`, `substring`, regex groups), lists and maps.
- **Music was fully decoded** (fixed on 2026-10-04, see above). gdx-teavm's `Howl.create` (`webaudio/howler/Howl.java` in
  backend-web 1.6.1) makes `new Howl({src: [blobUrl]})` without `html5: true`, so Howler decodes
  the whole file with Web Audio. The menu track (`menu2.mp3`, 1.5 MB, 185 s) becomes about 62 MB
  of samples, and Howler caches decoded tracks, so each new track (overworld, towns, battles) can
  add as much again. The Blob URL is never revoked either.
- `forge-data/packs/blockdata.pack` is prefetched and never taken (270 KB, minor).

Lazy card scripts alone won't help Adventure: `FModel` turns them off on mobile
(`GuiBase.isMobile() ? false : ...`), because rewards, shops and enemy decks draw from the whole
card pool and `StaticData.ensureAllCardsLoaded` would load everything anyway.

## Still to do (PLAN Phase 5, [[open-issues]])
Next steps, in order:
1. Done 2026-10-04: music streams with `html5: true` and its Blob URLs are revoked (see
   "Music streaming" above). Saved 54 to 58 MB at the menu and 86 MB at the overworld.
2. Done 2026-10-05, no gain: revoking the app.js Blob URL (see "Heap snapshot" above).
3. Next, about 55 MB expected from the snapshot: drop the startup pack (21 MB) and editions
   pack (4 MB) from `WebFileSystem.packs`, and the card zip (27.5 MB) from its `Node.data`, after
   startup, if nothing reads them again. Both can be fetched again on demand.
4. Done 2026-10-05: heap snapshot. It found the two-byte `app.js` source (145 MB), fixed by
   escaping non-Latin-1 characters, which saved 72 MB at the menu.
5. Shrink the card database itself (shared strings, smaller per-card structures), since lazy
   loading can't be used.
6. Test on a real iPhone, or in WebKit through Playwright, at each step.
- Release the minimap pixmap after upload.
- An LRU-capped card image cache (art from [[scryfall]] is cached in memory, unbounded).
- The `ImageUtil` memo is unbounded.
- Native deflate buffers the whole payload (the ~31 MB world map at the end of generation).
- Test at a phone viewport, and on a real GPU (headless GPU numbers are software GL).

## How to measure
`scripts/measure` runs the webtest `heap` step, which reports the JS heap, ArrayBuffers, wasm memories, and
live pixmaps with allocation stacks of the large ones. Build peak memory is tracked as well
(see [[metrics]], [[build-pipeline]]).
