---
type: bug
sources: [NOTES.md, NOTES.md#round-11, web/src/main/java]
updated: 2026-10-01
tags: [bugs, upstream]
---

# Bug catalog

This page lists every defect found in someone else's code, where it's fixed here, and its
upstream status. This is
the list to work from when upstreaming. Our own bugs are listed only when the lesson is
general (bottom section). `upstream` is `not-reported` unless noted. Update it when a report
is filed.

## TeaVM 0.15 ([[teavm]])

| Bug | Symptom here | Fix here | Found |
|---|---|---|---|
| `Throwable.addSuppressed` crashes (suppressed list never initialized) | the real exception of any try-with-resources whose `close()` also throws was hidden | redirected to `JdkCompat.addSuppressed` (logs `[suppressed] ...`) | R4 |
| `Inflater.inflate` throws on `Z_BUF_ERROR` (-5), where the JDK returns 0 | every deflated zip entry read failed | shadow `TInflater` | R4 |
| `ZipFile` doesn't feed the Inflater the JDK's dummy byte after raw deflate data | `EOFException` on entries | shadow `TZipFile` | R5 |
| `Thread.currentThread()` stale after a green thread suspends | Forge thought frames weren't on the UI thread | `MainThread*` | R3 |
| `LinkedBlockingDeque` is a plain LinkedList | Forge's `InputQueue.push` never compiled | shadow `TLinkedBlockingDeque` | R8 |
| Small synchronized method throws when the lock's owner is suspended | conceding a duel crashed (`Game.isGameOver`) | shadow `TObject`, borrowed monitors ([[green-threads]]) | R8/R9 |
| `UUID.randomUUID` uses `crypto.randomUUID` (https and localhost only) | crash on plain-http hosts | redirect | R8 |
| `UUID` not `Serializable` | saves | own stream tag ([[saves]]) | R8 |
| `Deflater` is jzlib (pure Java), slow | duel-start lag on autosave | shadow `DeflaterOutputStream` to use the browser's `CompressionStream` | R10 |
| casting double or float to long uses `BigInt(Math.floor(x))`, so NaN and infinities throw and large values wrap | crash when resizing the window (`NaN cannot be converted to a BigInt`) | build.gradle.kts patches `Long_fromNumber` in app.js (long.js can't be shadowed, because TeaVM's own class loader reads it) | R11 |
| `String.CASE_INSENSITIVE_ORDER` and `compareToIgnoreCase` map every character through `TCharacter.toLowerCase`'s table | about 7% of startup (card database TreeMaps) | redirected to `StringCompat` (ASCII fast path) | R11 |
| wasm-gc: `CoroutineTransformation$ListSplitter.createSaveInstructions` and `WasmTypeInference.pop` crash on suspendable methods | no wasm build | none possible, so the project stays on JS ([[stay-on-js-backend]]) | 2026-09-29 |

## gdx-teavm 1.6.1 ([[gdx-teavm]])

| Bug | Symptom | Fix here | Found |
|---|---|---|---|
| `AsyncResult` runs the task more than once | a decoded Pixmap leaked per texture (246 MB at the title) | emu shadow ([[memory-budget]]) | R9 |
| `Gdx2DPixmapNative` copies the whole pixmap out of wasm after every draw | world generation never finished (490k draws on 2800x2800) | emu shadow, lazy copy | R8 |
| (libGDX 1.14.2) `NinePatch` keeps UVs off patch edges only for linear filtering | lines across nine-patches at fractional scales | shadow `NinePatch` (0.1-texel inset for nearest filtering) | R11 |
| (TenPatch 5.2.3) `TenPatchDrawable` has no inset at all | dark lines across Adventure buttons | shadow `TenPatchDrawable` | R11 |
| `gdx-freetype-web` leaks font data (address returned through a copied `int[]`) | FreeType heap exhausted at some screen sizes | emu `FreeType.java` | review |

## Forge ([[forge]])

| Bug | Symptom | Fix here | Desktop too? |
|---|---|---|---|
| `AdventurePlayer` writes `colorIdentity` as a byte, reads it as a string | every loaded character becomes colorless | patch | **yes** |
| `OverlappingModel` rebuilt for every 10x10 chunk, with a boxed O(T²) propagator | world generation slow | patch ([[world-generation]]) | yes (perf) |
| Autosave re-encodes the world map PNG and arrays each time | duel-start freeze | patch | yes (perf) |
| `ImageUtil.getPaperCardFromImageKey` runs for every card on every frame, and `CardPredicate` checks in a slow order | duel-start freeze | patch (memo, predicate order) | yes (perf) |
| `CardArchetypeLDAGenerator` fails when a format has no LDA data | crash (data is Java-serialized, unavailable on web) | patch skips the format | edge case |
| A `CountDownLatch` task throwing before `countDown()` | card loading hung at 0% | `availableProcessors()` returns 1, so loading runs inline and fails visibly ([[single-processor]]) | latent |
| `FSkinFont` writes each of 65 generated font atlases as PNG and loads it back; the first set of page textures is never used or freed | about 7.6 s of startup here (no lasting cache), and GPU memory for unused textures | patch keeps the generated font on the web | leak: **yes** |
| Tile regions sampled up to their exact edge (`OrthogonalTiledMapRenderer`, tile objects) | one-pixel lines at fractional scales ([[screen-layout]]) | patch adds a 0.1-texel inset in `TemplateTmxMapLoader` | yes, at non-integer scales |
| VS screen font scale depends only on aspect ratio | names too big or off screen in smaller windows | patch shrinks the names to fit | yes, small screens |
| `ScreenUtil` sizes captures once, and the thumbnail is read outside a frame | black save thumbnails (WebGL clears the buffer), and a stale size after a resize | `preserveDrawingBuffer`, and a patch makes `ScreenUtil` follow the screen size | the size bug, after a desktop resize |
| Fixed screen size taken in `create()` | black screen when started hidden | canvas sizing ([[screen-layout]]) | n/a |

## Our own, with general lessons

| Bug | Lesson |
|---|---|
| The serializer called `getDeclaredMethods()` and `invoke()` on any class, so builds took over 20 min and ran out of memory | declare reflective needs narrowly ([[reflection-budget]]) |
| EventBus finds handlers through annotations, so all game events were dropped | annotations are gone on TeaVM ([[reflection-on-teavm]]) |
| `Progress` yielded after posting, which lost a wake-up and hung silently | yield before posting ([[green-threads]]) |
| `DeflaterOutputStream` wrote a second GZIP trailer on close | finish once; keep the Java fallback |
| `effects/demo.gif` became an 11488x6480 texture and crashed the tab | skip huge assets in webdata |
| Serializer hooks were never made callable, so the streams silently used plain fields, giving empty save headers and cards without rules (R11) | a reflective lookup that finds nothing must fail loudly; test the round trip, not just the write ([[saves]]) |
| The loader counted decoded bytes against `Content-Length`, so on GitHub Pages, which gzips app.js on the fly, it showed "62 / 32 MB" (user report, 2026-10-01) | count bytes before un-gzipping, ship app.js as `app.js.gz`, and count a file the browser decoded only once it completes; tested against a server that compresses like Pages |
| An unanchored `forge/` in `.gitignore` also matched `web/src/main/java/forge/`, so 10 source files (including `WebLauncher`) were left out of the repository (found 2026-10-01, before the first commit) | anchor ignore patterns (`/forge/`), and check a release by building from a clean checkout, not the working tree |
| Card zip deflated per entry, inflated by TeaVM's JZlib (R11) | let the browser decompress (gzip with `DecompressionStream`), and never use compiled Java zlib on a hot path |

See also [[teavm-gotchas]], [[open-issues]].
