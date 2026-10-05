---
type: concept
sources: [NOTES.md#baseline, NOTES.md#round-9, NOTES.md#review, PLAN.md#phase-5, scripts/build-web, web/tools/latin1-js.py, web/html/index.html, web/src/main/java/forgeweb/fs/WebFileSystem.java, web/tools/webtest.py]
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

## Dropping the packs and the card zip (2026-10-05)

Step 3 of the plan. `WebFileSystem` kept every downloaded pack in its `packs` map (startup pack
21.3 MB, editions pack 3.9 MB) and the card script zip in its `Node.data` (27.5 MB). Readers
found before the change: `ensureLoaded` (every file inside a pack, read for the first time),
`WebVirtualFile.Accessor.read` (reads `Node.data` directly), and Forge's `CardStorageReader`,
which keeps its `ZipFile` and so an open accessor on the zip for the whole session. Lazy card
loading is off on mobile, so after startup the zip is read only if something loads a card
script by name later, but that path exists.

**The change.** Packs and big (1 MB or more) read-only remote files are now dropped once nothing
has used them for 5 seconds: `WebFileSystem.trim` clears `packs` and sets `Node.data` to null for
the big files. Files already copied out of a pack keep their own bytes. `touch` runs on every
read, so an accessor that was open across a trim (the `ZipFile`) downloads the file again on its
next read, and a file in a pack that was not read yet downloads the pack again (the browser's
HTTP cache usually answers). A timer (`setTimeout`) schedules the trim, so nothing in Forge
changed. The selftest has a new check, "big files and packs read again after the idle trim",
which trims, reads a zip entry through the open `ZipFile`, and reads an unread file from the
formats pack (39 of 39 pass).

| Measure (headless, software GL, seed 1, same machine, dist built from the same source) | Before | After | Saved |
|---|---|---|---|
| Menu, desktop: renderer | 699 MB | 672 MB | 27 MB |
| Menu, desktop: ArrayBuffer backing stores | 237.5 MB | 206 MB | 31.5 MB |
| Menu, desktop: GPU | 458 MB | 449 MB | noise |
| Overworld, desktop: renderer | 957 MB | 886 MB | 71 MB |
| Overworld, desktop: backing stores | 300 MB | 236 MB | 64 MB |
| Menu, phone 390x844 at 3x: renderer | 703 MB | 678 MB | 25 MB |
| Menu, phone: GPU | 437 MB | 433 MB | noise |

A heap snapshot after the change shows the three buffers gone (ArrayBuffer data 164 MB to
102 MB), so the logic works. The renderer fell by less than the snapshot suggests at the menu
(27 of about 53 MB), probably because freed pages are not all returned to the operating system.
The overworld gain is larger because that run reads more of the startup pack before the trim.
JS heap used stayed at 281 MB. Checked on the trimmed build: startup, a new game through the
tutorial to the overworld, entering a town (Secluded Encampment), starting a duel (coin toss and
a seven-card hand) and the selftest.

## GPU memory (2026-10-05)

Measured with a WebGL hook (`web/tools/webtest.py --init-script`, the hook script wraps
`texImage2D`, `texStorage2D`, `generateMipmap`, `bufferData`, `renderbufferStorage` and the
deletes, and records a JavaScript stack when each texture is created). Same conditions as above.
It is a diagnostic, not part of the build.

| Live GPU objects | Menu, desktop | Menu, phone size | Overworld, desktop |
|---|---|---|---|
| Textures (including mip levels) | 296 MB in 129 | 295 MB in 129 | 349 MB in 150 |
| Buffers | 0.2 MB | 0.2 MB | 0.2 MB |
| Renderbuffers and framebuffer textures | none | none | none |
| Canvas backbuffer (estimated, double buffered plus depth) | 10.5 MB (1280x720) | 3.8 MB (390x844) | 10.5 MB |
| GPU process RSS | 449 MB | 433 MB | 597 MB |

The canvas is drawn at CSS pixels, so a phone at a device pixel ratio of 3 costs less than the
desktop backbuffer. Drawing at native resolution would be 1170x2532 and about 36 MB.

Where the texture bytes come from (menu, desktop; creation stacks plus matching image sizes to
files in `forge/forge-gui/res`):

| Source | Size | Notes |
|---|---|---|
| `FSkinFont` glyph pages | 125 MB in 65 textures | `FSkinFont.preloadAll` makes every size from 8 to 72: 23 pages of 1024x1024 (92 MB, sizes 50 to 72), 30 of 512x512 (30 MB, sizes 20 to 49), 12 of 256x256 (3 MB) |
| Desktop skin sprite sheets, `skins/default/sprite_*.png` | about 119 MB | Loaded through the asset manager at startup, with mipmaps: `sprite_sleeves` and `sprite_sleeves2` (1800x2000, 18.3 MB each), `sprite_foils` and `sprite_old_foils` (800x2850, 11.6 MB each), watermark 7.7, avatars 6.7, icons 6.6, border 6.5, setlogo 6.0, planar conquest 5.1, deckbox 5.1, buttons 5.0, and `bg_splash_hd` 5.5 |
| Other asset manager textures | about 35 MB | Adventure UI images, card frames and the like |
| `LanaPixel` skin atlas (2948x2048, 16 bit) | 11.5 MB | Read by `Skin` |
| Other | about 5 MB | Splash images, screenshot buffer, one-pixel textures |
| Minimap (`Assets.getNewMiniMapTexture`, 2800x2800) | 29.9 MB | Made in `GameHUD.enter` when the world opens |
| Tilesets of the world map and town (2528x1024, 448x1024) | 15.5 MB | `TemplateTmxMapLoader` |

Mipmaps cost 28.5 MB of the 296 (the 14 mipped sheets).

**Is the headless number a fair stand-in for a phone?** The texture total is. A phone GPU stores
the same RGBA8 bytes, and on iOS and most Android phones GPU memory is shared with the system
and counted against the app. The GPU process figure is not: an empty page with one WebGL context
costs 69 MB here, and uploading 100 MB of textures raised it by 114 MB, so the software renderer
adds about 70 MB fixed and about 10 to 25 percent on top of the textures (the menu's 449 MB is
296 MB of textures, 10 MB of backbuffer and about 140 MB of overhead). A real phone would
probably show 300 to 330 MB for the same content, but it would also hold compositor surfaces
and driver copies that this setup doesn't show. Treat 300 MB as the number to reduce.

**Recommended reductions, largest first (estimated texture savings, desktop menu):**
1. Preload only the font sizes Adventure uses, or stop at 36 as `MAX_FONT_SIZE_MANY_GLYPHS` does,
   and generate the larger ones on first use. This drops the 23 pages of 1024x1024 and 13 of
   the 512x512 pages: about 105 MB, with a small stall at the first use of a large size. Which
   sizes Adventure asks for needs a count first.
2. Do not load the duel-only sprite sheets (sleeves, foils, watermark, avatars, planar
   conquest, deckbox, setlogo, border, bg_splash) until a duel or the deck editor opens: up to
   about 100 MB. They are Forge desktop skin files, so this is a patch in how `FSkin` or
   `Assets` loads them, and icons and buttons may be needed earlier.
3. Load the sprite sheets without mipmaps: 28 MB, and less if (2) is done. Check the look when
   they are drawn smaller than their size.
4. Make the minimap a 16-bit or half-size texture: 15 to 22 MB at the overworld. It is drawn
   small, but check the map view that uses the same texture.
5. Later: compressed textures (ASTC or ETC2 made at build time) would take the artwork to a
   quarter or an eighth, but that needs an asset pipeline and loader work.

Together, (1) to (3) could take the menu's textures from 296 MB to about 100 MB.

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
3. Done 2026-10-05: the packs and the card zip are dropped after 5 idle seconds and downloaded
   again on demand (see "Dropping the packs" above). Saved 25 to 27 MB at the menu and 71 MB at
   the overworld.
4. Done 2026-10-05: heap snapshot. It found the two-byte `app.js` source (145 MB), fixed by
   escaping non-Latin-1 characters, which saved 72 MB at the menu.
5. Reduce GPU textures (see "GPU memory" above): lazy large fonts, duel-only sprite sheets,
   no mipmaps. Up to about 200 MB of the 296 MB at the menu.
6. Shrink the card database itself (shared strings, smaller per-card structures), since lazy
   loading can't be used.
7. Test on a real iPhone, or in WebKit through Playwright, at each step.
- Release the minimap pixmap after upload.
- An LRU-capped card image cache (art from [[scryfall]] is cached in memory, unbounded).
- The `ImageUtil` memo is unbounded.
- Native deflate buffers the whole payload (the ~31 MB world map at the end of generation).
- Test at a phone viewport, and on a real GPU (headless GPU numbers are software GL).

## How to measure
`scripts/measure` runs the webtest `heap` step, which reports the JS heap, ArrayBuffers, wasm memories, and
live pixmaps with allocation stacks of the large ones. Build peak memory is tracked as well
(see [[metrics]], [[build-pipeline]]).
