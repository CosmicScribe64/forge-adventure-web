---
type: concept
sources: [web/tools/webkit-memory.py, web/src/main/java/forgeweb/fs/Http.java, web/tools/heap-owners.js, NOTES.md#baseline, NOTES.md#round-9, NOTES.md#review, PLAN.md#phase-5, scripts/build-web, web/tools/latin1-js.py, web/html/index.html, web/src/main/java/forgeweb/fs/WebFileSystem.java, web/tools/webtest.py, scripts/e2e-newgame, web/src/main/java/forgeweb/fs/FileStore.java, patches/forge-web.patch]
updated: 2026-10-06
tags: [memory, phones, performance]
---

# Memory budget

The phone target (PLAN Phase 5) is the **overworld under 1 GB in total (JS heap and GPU)**. The
baseline showed that the Java heap is *not* the main cost. Decoded images in libGDX's wasm
heap and other ArrayBuffers are, and wasm memory never shrinks.

## Current totals (2026-10-05, minified release build)

Measured on the minified build (`TEAVM_OBFUSCATED=true scripts/build-web`, the build releases ship) with
`measure` in the webtest harness: headless Chromium with software GL, seed 1, default 700x700 world,
at the overworld after the tutorial's first map load and 10 s of waiting (so the WFC workers and the
minimap copy are gone). Desktop is 1280x720; the phone is `--width 390 --height 844 --scale 3 --mobile`.
Runs differ by a few MB, up to about 10 MB for RSS, so the table gives the range of two desktop runs and one phone run.

| Measure | Menu, desktop | Menu, phone | Overworld, desktop | Overworld, phone |
|---|---|---|---|---|
| Renderer RSS | 538 MB | 539 MB | 650 MB | 651 MB |
| JS heap used | 246 to 247 MB | 246 MB | 268 to 269 MB | 268 MB |
| Live WebGL textures | 75.4 MB | 73.8 MB | 124.0 MB | 122.4 MB |
| Wasm pixmap heap (live) | 7 MB | 7 MB | 40 MB | 40 MB |
| GPU process RSS (software GL) | 228 to 231 MB | 209 MB | 342 to 378 MB | 297 MB |

`scripts/e2e-newgame` takes its limits from these numbers ([[webtest-harness]]).

> [!note] Superseded 2026-10-06
> The renderer RSS row was 574 to 575 MB (menu, desktop), 581 MB (menu, phone), 684 to 690 MB (overworld, desktop) and 687 MB (overworld, phone) before the prefetch fix
> (the prefetched files were never released, see "WebKit attribution" below). The e2e-newgame limits were set from the old numbers and keep their room.

## Phone emulation and WebKit (2026-10-05)

The scenarios of [[e2e-tests]] measure the release build in three modes, from the assembled site served with
compression under a subpath, seed 1, headless, software GL, in the Playwright container (4 cores, no memory limit). One run each;
the WebKit RSS varies by a few hundred MB between runs, so it shows as a range of two runs.

| Measure | Menu, Chromium desktop | Menu, Chromium phone | Menu, WebKit iPhone 13 | Overworld, Chromium desktop | Overworld, Chromium phone | Overworld, WebKit iPhone 13 |
|---|---|---|---|---|---|---|
| Renderer or web process RSS | 574 MB | 571 MB | 1880 to 1899 MB | 691 MB | 683 MB | 2296 to 2519 MB |
| JS heap used | 246.6 MB | 245.8 MB | not reported | 268.2 MB | 268.9 MB | not reported |
| Live WebGL textures | 75.4 MB | 73.5 MB | 72.4 MB | 124.0 MB | 122.2 MB | 121.3 MB |
| Wasm pixmap heap | 7 MB | 7 MB | 7 MB | 40 MB | 40 MB | 40 MB |
| GPU process RSS (software GL) | 228 to 242 MB | 225 MB | in the web process | 384 MB | 319 MB | in the web process |

- The Chromium phone numbers equal the desktop ones within a few MB (RSS rise from menu to overworld: 112 MB on the phone,
  117 MB on desktop in the same run). The game draws into a canvas sized in CSS pixels, 390x844 on the phone, whatever the device scale
  factor (3), so a phone costs no extra framebuffer or texture memory. `scripts/e2e-newgame phone` has its own limit block
  set 5 to 8 percent above the heap, texture and pixmap numbers and 110 MB above RSS, as the desktop one is ([[webtest-harness]]).
- WebKit (Playwright's WPE build 26.0 on Linux, the iPhone 13 descriptor) offers no JS heap number. Its web process is
  three times Chromium's renderer, and in this port the software GL runs inside it, so a blank page with one WebGL canvas
  already costs 0.38 GB. The same page of the 0.1.1 release is 2.7 GB at the title and 3.5 GB at the overworld
  (textures 295 and 347.5 MB). Attribution is in the section below and in [[webkit-memory]]; the WebKit RSS is recorded
  by the run and not asserted.
- `window.forgePixmaps`, the texture count of `web/tools/glhook.js` and the wasm memory sizes (160 MB at the menu, 173 MB at the
  overworld) are identical in all three engines, so those three are the numbers to compare across them.

## WebKit attribution (2026-10-06)

Full detail, method and numbers are in [[webkit-memory]]. Summary, from the same minified build in Playwright's WebKit with the iPhone 13 profile:

- Live data is about the same as Chromium's (JavaScriptCore's heap after a full collection: 550 to 600 MB). The excess is the garbage-collected heap growing to
  about three times that before a collection (1.2 GB at the card loading, up to 1.68 GB at the idle title screen), on top of 0.37 GB for an empty WebGL page
  (software GL runs in the web process here). RSS follows the top of the heap, so it saws between 1.65 and 2.27 GB at the idle title screen and `VmHWM` is 2.0 to 2.3 GB.
- Two sources of garbage fill the headroom: the card loader (4 million `char[]`, 143,000 `byte[]`, 1.9 million `int[]` in 12 s) and the render loop, which
  makes about 9000 BigInt operations a frame at the title screen because TeaVM 0.15 stores every `long` as a `BigInt` (TextraTypist keeps each glyph in a `long`).
- A bug of ours cost a transient 700 MB in WebKit (and 33 MB for good in every engine): in a minified build a `@JSBody` parameter renamed to `b` was replaced by the
  script's own `var b`, so `Http.takePrefetched` made a string of 22 million numbers and never released the prefetched files. Fixed on 2026-10-06 (long variable names, and
  `web/tools/test_jsbody_names.py`). Chromium after the fix, one run each: menu 538 MB on desktop and 539 MB on a phone (570 and 573 before), overworld 650 and 651 MB (683 and 684 before);
  JS heap, textures and pixmaps did not change. WebKit's title `VmHWM` and idle maximum did not change.
- Later the same day three quarters of the BigInt operations were removed (9040 to 2145 a frame at the title screen, 70,700 to 16,500 at the overworld) by writing `long` literals as BigInt literals in the
  `app.js` patch and by integer hashing in three libGDX maps ([[bigint-churn]]). Chromium RSS did not change (535 and 640 MB), the overworld is 5 percent faster in software GL, and WebKit's
  idle sawtooth and `VmHWM` stayed within the run-to-run spread (overworld idle median 70 to 180 MB lower, title maximum 2.25 to 2.32 GB before and after). BigInt was not the main garbage that fills the headroom.

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

> [!note] Superseded
> The release is now minified (2026-10-05, the owner's decision), which cut the JS source string
> from 73.9 MB to 20.9 MB at the menu, and renderer RSS from 603 to 573 MB. The cost is that stack
> traces from the release show short names; the source map (`app.js.map`) in the site maps them
> back in DevTools. See [[build-pipeline]] and [[metrics]].

## Dropping the packs and the card zip (2026-10-05)

Step 3 of the plan. `WebFileSystem` kept every downloaded pack in its `packs` map (startup pack
21.3 MB, editions pack 3.9 MB) and the card script zip in its `Node.data` (27.5 MB). Readers
found before the change: `ensureLoaded` (every file inside a pack, read for the first time),
`WebVirtualFile.Accessor.read` (reads `Node.data` directly), and Forge's `CardStorageReader`,
which keeps its `ZipFile` and so an open accessor on the zip for the whole session. Lazy card
loading is off on mobile, so after startup the zip is read only if something loads a card
script by name later, but that path exists.

**The change.** Big (1 MB or more) read-only remote files, which means the card zip, drop their
contents once nothing has used them for 5 seconds (`WebFileSystem.trim`); files already copied
out of a pack keep their own bytes. `touch` runs on every read, so an accessor that was open
across a trim (the `ZipFile`) downloads the file again on its next read. A pack is forgotten as
soon as every file in it has been read (`packUnread` counts them). A timer (`setTimeout`)
schedules the trim, so nothing in Forge changed. The selftest has a new check, "big files and
packs read again after the idle trim", which trims and reads a zip entry through the open
`ZipFile` (39 of 39 pass).

**Refetches in play (checked 2026-10-05).** The first version also dropped packs after 5 idle
seconds, and the startup pack (17.2 MB downloaded) was fetched again between the menu and the new
game. That is why packs now go only when fully read. Forge loads card scripts eagerly in our
build (`FModel` turns lazy loading off on mobile, and the log shows "Read cards: 33980 archived
files" at startup), and `StaticData.attemptToLoadCard` returns early when loading is eager, so
nothing reads the zip after startup. Downloads of 1 MB or more or of any pack, from an XHR and
fetch hook, on the final build: startup 8 (app.js 6.5, startup pack 17.2, card zip 5.7,
editions 1.3 MB, the other packs under 0.2 MB, all downloaded sizes); new game to the world 0
packs or zips (one music file); the tutorial, a town, two duels played over several turns, a
concede and the deck editor: no pack or zip downloads, only music tracks of 1.3 to 2.2 MB. The
town shop could not be entered through the harness (the walk stalled), so the shop was not
exercised; it draws on the card database already in memory.

| Measure (headless, software GL, seed 1, same machine, dist built from the same source) | Before | After | Saved |
|---|---|---|---|
| Menu, desktop: renderer | 699 MB | 669 MB | 30 MB |
| Menu, desktop: ArrayBuffer backing stores | 237.5 MB | 206 MB | 31.5 MB |
| Menu, desktop: GPU | 458 MB | 456 MB | noise |
| Overworld, desktop: renderer | 957 MB | 916 MB | 41 MB |
| Overworld, desktop: backing stores | 300 MB | 259 MB | 41 MB |
| Menu, phone 390x844 at 3x: renderer | 703 MB | 667 MB | 36 MB |
| Menu, phone: GPU | 437 MB | 435 MB | noise |

The startup pack is not read in full by a new game, so it stays in memory (the version that
dropped it early saved 71 MB at the overworld, at the cost of the refetch). A heap snapshot of the first version shows the three buffers gone (ArrayBuffer data 164 MB to
102 MB), so the logic works. The renderer fell by less than the snapshot suggests at the menu
(27 of about 53 MB), probably because freed pages are not all returned to the operating system.
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
1. (Done 2026-10-05, see "Lazy font sizes" below.) Preload only the font sizes Adventure uses, or stop at 36 as `MAX_FONT_SIZE_MANY_GLYPHS` does,
   and generate the larger ones on first use. This drops the 23 pages of 1024x1024 and 13 of
   the 512x512 pages: about 105 MB, with a small stall at the first use of a large size. Which
   sizes Adventure asks for needs a count first.
2. (Done 2026-10-05, see "Lazy sprite sheets" below.) Do not load the duel-only sprite sheets (sleeves, foils, watermark, avatars, planar
   conquest, deckbox, setlogo, border, bg_splash) until a duel or the deck editor opens: up to
   about 100 MB. They are Forge desktop skin files, so this is a patch in how `FSkin` or
   `Assets` loads them, and icons and buttons may be needed earlier.
3. (Partly done: the map tilesets only, see below.) Load the sprite sheets without mipmaps: 28 MB, and less if (2) is done. Check the look when
   they are drawn smaller than their size.
4. Make the minimap a 16-bit or half-size texture: 15 to 22 MB at the overworld. It is drawn
   small, but check the map view that uses the same texture.
5. Later: compressed textures (ASTC or ETC2 made at build time) would take the artwork to a
   quarter or an eighth, but that needs an asset pipeline and loader work.

Together, (1) to (3) could take the menu's textures from 296 MB to about 100 MB.

## Lazy font sizes (2026-10-05)

`FSkinFont.preloadAll` (called from `Forge.java` once the database is loaded, with the loading
bar text "Loading fonts") made every size from 8 to 72 up front. Upstream does this so that no
screen has to generate a font while it is drawing: `_get(size)` already generates a missing
size on first use, so the preload is only about avoiding a pause, and the generation is split
between the calling thread (FreeType) and the UI thread (the textures), which is why
`Progress.invokeInEdtNowOrLater` lets the page catch up while a loop does it. Nothing depends on
the sizes existing: `shrink()` and `increase()` call `_get` too. On the web the preload now
returns early (`patches/forge-web.patch`, guarded by `forge.web`, so desktop and phone builds
of Forge keep the old behaviour).

Sizes created by play on the lazy build (a log line in `_get` on a debug build): desktop
1280x720 menu 9 to 17, overworld 24, a duel 8, 18 to 21, 27 and 33; phone 390x844 menu 9, 10,
11, 16, 18, 19, and 28 later. The sizes depend on the screen size (`Utils.scale`), so a fixed
preload list would be brittle and first-use generation is the robust choice. Seventeen sizes
(8 to 21, 24, 27, 33) cost 8 MB of textures after the menu, the map, a town and a duel. A size
takes about 0.1 s to generate on the UI thread (log timestamps; sizes 27 and 33 at the duel
start were created within 0.2 s of each other), and it happens while a screen is being built.

| Measure (headless, software GL, seed 1, local server, no hook unless noted) | Before | After |
|---|---|---|
| Menu, desktop: live textures (WebGL hook) | 296 MB in 129 | 173.5 MB in 73 |
| Menu, desktop: renderer / GPU process | 669 / 456 MB | 659 / 334 MB |
| Menu, desktop: backing stores | 206 MB | 204 MB |
| Overworld, desktop: renderer / GPU process | 916 / 596 MB | 902 / 478 MB |
| Menu, phone 390x844 at 3x: renderer / GPU process | 667 / 435 MB | 663 / 306 MB |
| Startup stage "Loading fonts" (`[ttg]` line) | 3.2 s | 0.0 s |

Played on the lazy build: a new game through the tutorial, a town, the overworld, a duel
started with the coin toss, a seven-card hand and several turns.

## Lazy sprite sheets (2026-10-05)

`FSkin.loadFull` read every desktop skin sheet at startup, then cut the avatar, sleeve, crack,
border and deck box regions out of them, and `FSkinImage.load` (through `FSkinImageImpl`) made the
region of every skin image, which loads that image's sheet. On the web (`forge.web`, so desktop
Forge keeps its behaviour) both are now lazy, in `forge-gui-mobile/src/forge/assets/FSkin.java`
and `FSkinImageImpl.java`:
- `FSkin.getAvatars()`, `getSleeves()`, `getCracks()`, `getBorders()` and `getDeckbox()` each read
  only their own sheet the first time they are called (on the UI thread, using
  `FThreads.invokeInEdtAndWait` when called from another one). The sleeves come in two sheets
  and adventure rewards need only sleeve 0, so a new `FSkin.getDefaultSleeve()` (used by
  `RewardActor` and `CardSleeveImage`) reads the first sheet alone; the full `getSleeves()` map
  is still complete because the second sheet's sleeves are numbered after the first's.
- `FSkinImageImpl` keeps foils, old foils, watermarks, set logos, planar conquest and the border
  props unread until `getTextureRegion()` or `draw()` is first called on one of their images.
  Icons, abilities, mana icons, buttons and adventure sheets stay eager, because loading them
  needs the icons pixmap or they are used at once.

Nothing draws differently: the same textures with the same filters, read later. Played on the
lazy build: a new game through the tutorial, a town, the overworld, a duel (coin toss, hand,
casting a spell with its mana cost prompt, the players tab), then the deck editor in list and
image view, where the watermarks (a Dimir watermark behind a card's text), frames and set
symbols draw. Mipmaps of the skin sheets stay on: the icons, buttons and mana icon sheets are
drawn at many sizes, I did not find a sheet that is certainly drawn at its native size, and
turning mipmaps off would make any minified icon alias.
Mipmaps cost 7.8 MB of the 75 MB at the menu and 12 MB at the overworld, so there is little
left to win. The map tilesets are the exception: `TemplateTmxMapLoader` asked for mipmaps but
sets both filters to `Nearest`, so the levels were never sampled, and it no longer creates them
(a quarter of a tileset: 15.5 to 11.6 MB for the town and 31 to 23.3 MB for the world and town
together).

| Live textures (WebGL hook, headless, software GL, desktop 1280x720, seed 1) | Fonts lazy only (before) | Sheets lazy too (after) |
|---|---|---|
| Menu | 173.5 MB in 73 | 75.4 MB in 62 |
| Town (new game, tutorial, Secluded Encampment) | 241.2 MB in 132 | 157.5 MB in 122 |
| Overworld | 256.7 MB in 172 | 169.1 MB in 163 |
| Duel (turn 1, separate hooked session) | 257.2 MB in 173 | 224.6 MB in 182 |
| Deck editor (list view, from the overworld) | 272.7 MB in 183 | 185.0 MB in 174 |
| Menu, phone 390x844 at 3x | not measured (296 MB at the start) | 73.8 MB in 60 |

The "before" rows are the build after the font change; against the start of this session (296 MB
at the menu, 349 MB at the overworld) the menu is down 220 MB and the overworld 180 MB. The deck
editor row of the "before" run came after a duel, the "after" run went straight there, so that
row overstates the saving a little. A duel loads most of the sheets again (55 MB more than the
overworld), so it costs 225 MB of textures, 33 MB less than before.

| Process memory (headless, software GL, seed 1, no hook) | Before (fonts lazy) | After |
|---|---|---|
| Menu, desktop: renderer / GPU process | 659 / 334 MB | 633 / 230 MB |
| Overworld, desktop: renderer / GPU process | 902 / 478 MB | 905 / 377 MB |
| Menu, phone 390x844 at 3x: renderer / GPU process | 663 / 306 MB | 631 / 217 MB |
| Title screen reached at | 31.2 s | 22.4 s (machine load differs, indicative only) |

From the start of the session (669 / 456, 916 / 596 and 667 / 435 MB) the menu GPU process is down
226 MB on the desktop and 218 MB at phone size, and the overworld GPU process down 219 MB. The
renderer moved little (36 MB at the desktop menu), because textures are uploaded to the GPU and
the CPU copies are freed.

What was not exercised: the sleeve and avatar pickers in the new-game or lobby screens, the
quest and planar conquest screens (set logos and planar conquest sheets), a custom skin (these
paths need a non-default skin directory, which the web build doesn't have), and foil cards.
The first duel now reads about 33 MB of sheets while the transition screen is showing; its
cost was not timed.

## JS heap by owner and lazy card loading (2026-10-05)

**Method.** `webtest` step `snapshot` at the main menu (headless, software GL, desktop 1280x720,
seed 1, build bf3b5ad), then `web/tools/heap-owners.js`, which builds the dominator tree and cuts
`StaticData`, `CardDb`, `FModel` and `CardStorageReader` so that data shared by several maps is
attributed to its own class instead of to one global root. The snapshot reaches 471 MB, of which
the JS heap is 277 MB.

| Owner (top-level retained size) | Size | Notes |
|---|---|---|
| `CardFace` (35,661 faces) | 90.6 MB | `CardType` 32.3, SVar `TreeMap`s 16.5, oracle text 9.1, mana cost 7.6, triggers 7.2, abilities 6.7, keywords 2.9, static abilities 2.5 |
| `CardEdition` (683) | 26.2 MB | The per-set lookup multimap 10.5, 82,000 strings 6.1, 99,000 `EditionEntry` 5.3 |
| `CardRules` (34,048) | 22.1 MB | Empty `specializedParts` maps 5.7, `CardAiHints` 4.7, face lists 4.0, normalized names 3.0 |
| `PaperCard` (97,786) | 17.6 MB | Sortable names 6.4, object properties 6.3 |
| Strings shared between the above | 17.4 MB | 273,000 `String` objects |
| Two `CardDb` multimaps and 557 `TreeMap`s | about 16 MB | `allCardsByName`, `allCardsByRules`, and others |

Not JS objects, but in the same snapshot: the `app.js` source 72.6 MB, the libGDX and window wasm
memories 80 MB, the `WebFileSystem` startup pack 21.3 MB (still held at the menu, because not all of
its files have been read) and the virtual file tree (`Node`, 15.6 MB, one entry per resource file).
The card database in total is about 190 MB of the 277 MB.

Two facts about the waste. First, a `CardType` costs about 900 bytes whatever it holds: an empty
`excludedCreatureSubtypes` set (7.5 MB in all), mostly empty supertypes (6.0 MB), and a subtypes set
(11.7 MB) even for lands and spells. Creating those sets on first add is a small change inside
`CardType` that could go upstream; it was made on 2026-10-05 (see "Lazy `CardType` sets and
`specializedParts`" below).
Second, identical strings are not the problem: 479,000 duplicate copies waste only 12.9 MB
(`SVar:DBCleanup` lines, "Human", "Flying" and the like), so interning would save under 5 percent.

**Lazy `CardType` sets and `specializedParts` (done 2026-10-05).** In `patches/forge-web.patch`,
`CardType` now starts its supertypes, subtypes and excluded creature subtypes as the shared
`Collections.emptySet()` and creates the real set in `writableSupertypes()`, `writableSubtypes()` and
`writableExcludedCreatureSubtypes()` on the first add. `CardRules.specializedParts` is likewise a shared
empty map until a Specialize card fills it. The change does not depend on `forge.web` and behaves the
same on the desktop, so it is written to go upstream as it is. The core types stay an `EnumSet`, as
they are present on nearly every card.

Measured on a clean tree, desktop, seed 1, JS heap used: menu 277.4 to 256.7 MB, overworld 304.4 to
283.5 MB, phone-size menu 276.8 to 256.4 MB, about 21 MB each (the estimate from the snapshot was
38 MB; the rest of the 900 bytes per `CardType` is the object and its `coreTypes` set, which stay).
Renderer memory fell by 14 MB at the desktop menu (627 to 613 MB) and 12 MB on the overworld (892 to
881 MB); the GPU figures moved within run-to-run noise.

How the mutators were checked: every writer of the three fields goes through a `writable` method
(`add`, both `addAll` overloads, `setCreatureTypes`, `combine`, the copy constructor,
`CardChangedType.applyChanges` and `WordChangedType.applyChanges`). Remove-style calls (`remove`,
`removeAll`, `removeIf`, `clear`) are left alone, because they are no-ops on an empty set. No caller
outside `CardType` mutates what `getSupertypes()`, `getSubtypes()` or `getExcludedCreatureSubTypes()`
returns: a search over all modules found only reads (`forEach(types::add)` copies out of it, and the
two `(Set<String>)` casts in `AdvancedSearch` are only read). The one caller of
`CardRules.getSpecializeParts()` in each of `CardUtil` and `CardFactory` only iterates it. Forge has
no `forge-core` unit tests, and the `forge-gui-desktop` tests could not be resolved offline here, so a
throwaway program covered the empty-set cases (parse, copy, add after clear, `combine`,
`removeAll`, `CardChangedType`); it passed. In the browser a new game, an Adept wizard dialog, a duel
to turn 2 and the deck editor list with its colour filter and Advanced Search dialog ran with no new
errors, and the selftest passed.

**Why lazy card loading is off on mobile, and whether it applies here.** `FModel` sets
`loadCardsLazily` to false when `GuiBase.isMobile()`, with the comment "unless proven to work on
mobile". In lazy mode `CardDb` starts empty and a card is parsed only when something asks for it by
name (`attemptToLoadCard`); only seven game effects and `GameFormat.getAllCards` call
`ensureAllCardsLoaded`. The adventure code enumerates the whole database at startup instead:
`Config` calls `RewardData.getAllCards()`, which filters `CardUtil.getFullCardPool` by rules (keywords,
AI hints, editions). Shop stock, loot, enemy decks, the Spell Smith and `FModel.getAllCards` (the
deck editor catalog) all work from lists built that way. So the reason still holds in the browser.
The web build is `isMobile()` and has the same code.

**Measured upper bound** (a throwaway build with lazy loading forced on for `forge.web`, nothing
reverted in the repository): the database is still indexed at startup, which parses all 33,980
scripts once ("indexed 35240 card names from 33980 files in 5.7 s", the same cost as the eager
load), but keeps none.

| Measure | Eager (bf3b5ad) | Lazy forced on |
|---|---|---|
| Menu, desktop: JS heap, renderer, GPU | 277.7 MB, 629 MB, 236 MB | 129.0 MB, 473 MB, 263 MB |
| Menu, phone size: JS heap, renderer, GPU | 277.3 MB, 632 MB, 213 MB | 128.8 MB, 499 MB, 212 MB |
| Overworld, desktop: JS heap, renderer | 304.5 MB, 902 MB | 156.4 MB, 770 MB |

Phone-size backing stores grew from 191 to 222 MB because the card zip is read again after the idle
trim. The starter deck loads and the deck editor listed the 40 cards. Rewards and enemy decks were not
checked in play (the duel run found no encounter), but the code above says they draw from the cards
loaded at startup, which is almost none. So lazy loading as Forge has it saves about 150 MB and breaks
rewards, shops and enemy decks.

**What a working version needs.** A slim index that is complete for enumeration: every card keeps
name, type, colors, mana cost, power and toughness, oracle text (the text filter needs it), rarity and
the AI hints (`RemAIDecks` and similar flags used by the reward filters), and `hasKeyword` for the
ante filter. Everything the game engine alone reads (abilities, triggers, static abilities, replacement
effects, SVars, keywords) would be parsed on first use from the zip. Those are about 36 MB of the
277 MB heap, and the zip held back costs about 34 MB (27.5 MB data and 6.5 MB entries), so the net saving
is small. The big gain of the experiment came from not keeping `CardType`, SVar maps and mana costs for
cards that are never played, and a slim index would keep type and cost. Estimated net: 20 to 40 MB for a
medium-sized change to `CardFace` and `CardRules`. Cheaper first: the `CardType` change, now done.

## Overworld: workers, minimap copy, chunk arrays (2026-10-05)

Measured at the overworld (new game, seed 1, headless Chromium, software GL, desktop 1280x720, 10 s
after "Generating world took"), renderer RSS / JS heap / backing stores:

| Build | Renderer | JS heap | Backing stores | Notes |
|---|---|---|---|---|
| Start of the day (3 workers alive) | 889 MB | 284.8 MB | 253.5 MB | GPU process 370 MB |
| `?wfc=local`, no workers | 756 MB | 283.4 MB | 254.4 MB | same build |
| Workers terminated when idle | 747 MB | 282.3 MB | 253.5 MB | phone size 753 MB |
| Minimap copy dropped | 728 MB | 283.2 MB | 224.5 MB | phone size 722 MB |
| WorldBackground arrays per chunk | 724 MB | 277.5 MB | 224.5 MB | phone size 715 MB, 278.4 MB heap |

- **WFC workers cost real memory**: 133 MB of renderer RSS for 3 workers on this 4-core box, about
  44 MB each (an isolate, a TeaVM heap and retained garbage; none of it is in the main JS heap, so
  the `heap` step doesn't show it). `WfcPool` now terminates the pool 5 s after the last reply and
  starts it again for the next generation (`window.forgeWfc` is deleted). A second new game
  respawned 3 workers and finished; a worker redirected to a missing URL logged "WFC workers failed",
  fell back to the page and finished the world in 37.8 s (against about 20 s with workers). The
  worker count is 1 to 7 by core count, so a phone with more cores saved more.
- **The minimap's JS copy**: `World.biomeImage` (2800x2800, 31 MB in the wasm heap) got a 31 MB JS
  mirror `ByteBuffer` whenever it was read (texture upload, PNG encode) and kept it all session.
  `Gdx2DPixmapNative` now drops the mirror of any pixmap of 16 MB or more 5 s after its last
  `getBuffer()`. The next read (the map screen, a later save, a loaded world) copies from the wasm
  heap again. The live pixmap counter showed mirrors 31 MB to 1 MB. Checked in one session: HUD minimap,
  map screen (the HUD zoom button opens it), save to a slot, second new game, load of the slot.
  Wrong for a big pixmap written through its buffer without `copyToHeap()`; none exists.
- **Tried and dropped**: an explicit release hook called by Forge's `Assets.getNewMiniMapTexture`
  and `World.generateNew` (a Forge patch). At run time `Pixmap` has no `getNative()`, so the call threw
  `$getNative is not a function` inside `generateNew`, the exception was swallowed and generation
  ended half done (`Cannot read properties of null (reading 'data')` in `WorldBackground.loadChunk`
  later). A registry of mirrors in `Gdx2DPixmapNative` found no owner for reasons not understood.
  The timer needs no Forge change.
- **WorldBackground**: `chunks`, `chunksSprites` and `chunksSpritesBackground` are indexed by chunk but
  were sized by tile (700x700 each). They are now `ceil(tiles / chunkSize)` per side
  (`patches/forge-web.patch`). Saved 5.7 MB of JS heap (not the 12 MB estimated; the arrays hold
  4-byte references). Walked across two chunk borders in the overworld without errors; the world
  edge was not walked.

## New games no longer leak the old world (2026-10-05)

Measured in one session (seed 1, desktop 1280x720, headless software GL), at the overworld after the
tutorial, before the fix: the wasm pixmap heap was 7 MB at the menu, then 40, 71 and 102 MB after
games 1, 2 and 3 (31 MB per new game, the previous `biomeImage`), and live texture memory went
124.0, 135.7, 147.3 MB (11.7 MB per new game, the previous `TiledMap`, which is a 2528x1024 main
tileset). Renderer RSS went 677, 725, 756 MB. Loading a save added another pixmap and 9 MB of textures.

Two Forge patches fix it, with no `forge.web` guard because they are bugs on desktop too.
`World.generateNew` disposes the old `biomeImage` when it stores the new one, and
`TileMapScene.load` (both overloads) disposes the previous `TiledMap` after the renderer has
switched to the new one. After the fix, games 1, 2 and 3 measured 40, 41, 42 MB of pixmap heap and
157.5, 157.9, 158.4 MB of textures (the overworld numbers are higher than the table above because
they are taken after the tutorial, which loads the start cave and its textures). Growth per new game
is now about 1 MB of pixmap heap and 0.4 MB of textures; a save, then a load, added 1 MB and 1.3 MB.
Renderer RSS still rises between games 1 and 2 (739 to 768 MB), because the old image is freed only
after the new one exists, which raises the wasm memory high-water mark once; games 2 to 3 added 15 to
20 MB (JS heap 283.0, 284.6, 285.4 MB). Visiting towns and dungeons raises the texture total by about
1.8 MB per distinct map (sprite atlases that stay cached), not 10 MB. `scripts/e2e-cycle` asserts all
of this ([[webtest-harness]]).

## The gdx.wasm start size does not matter (2026-10-05, not changed)

The memory section of the embedded gdx.wasm module asks for 1024 pages (64 MB) at start, growable up
to 32768 pages. Its stack and data are under 80 KB, and emscripten's resize hook calls `memory.grow`, so it
can start much smaller. A small build-time script rewrote the memory minimum in the base64 payload of
`gdx.wasm.js` (parse the sections, re-encode the one memory section, not a byte search) and the game ran
with 256 pages (16 MB). The wasm pixmap heap grew past 16 MB without trouble (40 MB at the overworld).
Renderer RSS did not drop, because the untouched pages of a wasm memory are never resident:

| Minified build, same dist, seed 1 | 1024 pages | 256 pages |
|---|---|---|
| Menu desktop RSS (two runs) | 563, 570 MB | 573, 567 MB |
| Overworld desktop RSS (two runs) | 673, 680 MB | 684, 680 MB |
| Menu phone RSS | 580 MB | 575 MB |
| Overworld phone RSS | 687 MB | 688 MB |

JS heap (246 and 267 MB), textures and pixmap heap were identical. The change was dropped and no code
was committed. The 64 MB that a heap snapshot shows for this memory at the menu is address space; only pages that
are written count towards RSS.

## Image caches (2026-10-05)

There are three places where card pictures can pile up.

1. **Card textures** (`ImageCache`, Forge). `Forge.cacheSize` (300, or 400 and 600 on devices that
   report more RAM) is meant to cap them, but nothing enforced it. A texture that has finished loading is
   handed out by `getAsset`, which returns before the code that records it for eviction, and the old
   eviction pass dropped everything except the last few loads. In the deck editor, scrolling through a
   collection of 600 cards (`api addcards 600`, then the wheel) left every texture alive: about 1.7 MB each
   with mipmaps (488x680), 632 MB of textures after 600 pictures and still rising. Fixed in
   `patches/forge-web.patch`: `noteUsed` keeps the most recently drawn textures in a `LinkedHashSet` in
   least-recently-used order, drops a tenth when the cap is passed, and also counts a texture when its
   load is requested (a card that scrolls away while it loads is never drawn again). The legacy pass in
   `loadAsset` now calls it. With `forge.web` set the cap is at most 120, as on iOS, because a phone tab
   has far less memory than a desktop. Desktop behaviour changes only by honouring the existing cap.
2. **Downloaded picture files.** The web file system keeps everything written under `cache/` in memory
   (a Scryfall picture is about 100 KB, measured with `api fsstats`), and none of it was ever dropped.
   `FileStore.capFolder` now bounds `cache/pics/` at 64 MB (about 640 pictures): over the cap the least
   recently read or written files are deleted, and a finished download no longer keeps the spare half of
   its growing buffer. `FileStoreTest` covers the order and the sizes. Limitation: Forge only requests a
   missing picture when a card image object is created, so a list item whose file was dropped keeps its
   placeholder until the list is built again. 64 MB makes that rare; a 24 MB cap showed it when scrolling
   back to the top of a 600-card list (369 pictures dropped, 239 files and 24.5 MB kept).
3. **Card art crops** (`Assets.cardArtCache`) are an LRU of 100 entries already, and the `ImageUtil`
   memo maps image keys to cards, so it is bounded by the card database. No change.

Test: a new game, 600 cards added to the collection, the deck editor opened on the inventory page, and
the wheel scrolled 60 rows at a time with 25 s for the downloads, then back up; `measure` after each step
(desktop, minified build). Without either fix, after 608 pictures: renderer RSS 899 MB, textures 632 MB
(still growing, 21 to 30 MB per 60 pictures), GPU process RSS 904 MB. With both: RSS 861 to 872 MB, textures
337 to 354 MB and flat from the fourth step on (the cap), GPU process RSS 614 MB, and all three stay flat
when scrolling the list again. JS heap rose from 286 to 306 MB in both runs and stayed there. Open:
renderer RSS still rises by about 145 MB while the first 600 pictures load, with or without the fixes, and
JS heap by 20 MB; this is not the textures or the picture files, and it stops when the list ends. See [[open-issues]].

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
3. Done 2026-10-05: the card zip is dropped after 5 idle seconds and packs once fully read, and
   downloaded again on demand (see "Dropping the packs" above). Saved 30 to 36 MB at the menu and 41 MB at
   the overworld.
4. Done 2026-10-05: heap snapshot. It found the two-byte `app.js` source (145 MB), fixed by
   escaping non-Latin-1 characters, which saved 72 MB at the menu.
5. Reduce GPU textures (see "GPU memory" above): lazy large fonts, duel-only sprite sheets,
   no mipmaps. Up to about 200 MB of the 296 MB at the menu.
6. Shrink the card database itself: the `CardType` empty sets and the empty `specializedParts`
   maps are done (about 21 MB); next a slim card index with script details read on first use (see "JS heap by owner" above).
   Forge's lazy loading can't be used as it is.
7. Test on a real iPhone, or in WebKit through Playwright, at each step.
- Done 2026-10-05: workers terminated when idle, minimap copy dropped, WorldBackground arrays per chunk (see "Overworld" above). Open: the minimap as a half-size or RGB565 texture (15 to 22 MB GPU), disposing the pixmap and rebuilding it from the cached PNG (about 31 MB of wasm heap that never shrinks).
- Done 2026-10-05: the card texture cap works (120 in the browser) and the picture files in memory are capped at 64 MB (see "Image caches" above). The `ImageUtil` memo is bounded by the card database. Open: what raises renderer RSS by about 145 MB over the first 600 pictures.
- Done 2026-10-05, no gain: a 16 MB start size for gdx.wasm (see "The gdx.wasm start size" above).
- Native deflate buffers the whole payload (the ~31 MB world map at the end of generation).
- Test at a phone viewport, and on a real GPU (headless GPU numbers are software GL).

## How to measure
`scripts/e2e-cycle` asserts that a second and third new game do not grow the pixmap heap or textures. `scripts/e2e-newgame` asserts renderer RSS, JS heap and texture memory at the menu and the overworld (see [[webtest-harness]]). `scripts/measure` runs the webtest `heap` step, which reports the JS heap, ArrayBuffers, wasm memories, and
live pixmaps with allocation stacks of the large ones. Build peak memory is tracked as well
(see [[metrics]], [[build-pipeline]]).
