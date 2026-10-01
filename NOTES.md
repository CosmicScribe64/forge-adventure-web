# Forge Adventure in the browser (lab notebook)

Goal: run Forge's Adventure mode (libGDX, `forge-gui-mobile`) in a browser by compiling it to
JavaScript with [gdx-teavm](https://github.com/xpenatan/gdx-teavm).

## Layout

| Path | What |
|---|---|
| `forge/` | shallow clone of Card-Forge/forge (gitignored) |
| `docker/Dockerfile` | build image with JDK 17, Maven and Gradle |
| `scripts/dock <cmd>` | runs a command in the build container; caches live in the Docker volumes `shandalar-m2` and `shandalar-gradle` |
| `scripts/build-forge-libs` | builds the Forge modules and copies them and their dependencies into `web/libs` |
| `web/` | Gradle project with the gdx-teavm plugin and `forge.web.WebLauncher` |

## Reproduce

These were the commands for the first compile. README.md has the current build steps.

```bash
scripts/build-forge-libs
DOCK_CWD=web scripts/dock 'GRADLE_OPTS="-Xmx1500m" gradle --console=plain gdx_teavm_web_js_build'
```

## Findings (2026-09-28, first compile)

- The versions match. Forge uses libGDX 1.14.2, which is exactly what gdx-teavm 1.6.1 targets.
- Forge has a clean entry point, `Forge.getApp(...)`, and the web launcher is about 15 lines.
- The launcher compiles against Forge, and TeaVM needs about 5 GB of heap to analyse it.
- TeaVM then reports **85 distinct missing JDK APIs** at 331 call sites. Grouped by cause:

| Cause | Examples | Fix |
|---|---|---|
| Crash reporting, logging | Sentry, tinylog, `ExceptionHandler` file locks | stub or skip on web |
| Networking, downloads, multiplayer | `FServerManager`, `GuiDownloadService`, jupnp, `java.net.*` | stub on web; card images later through `fetch` |
| Simple concurrent collections | `CopyOnWriteArraySet`, `ConcurrentLinkedQueue`, `ConcurrentSkipListMap`, `StringJoiner` | single-threaded emulations |
| **Game-thread and input sync** | `CountDownLatch`, `BlockingDeque`, `FutureTask`, `ExecutorService`, `CompletableFuture` in `InputSyncronizedBase`, `InputQueue`, `AiController`, `World` | **the real risk**; emulate on TeaVM green threads (`Thread` with `wait`/`notify`) |
| Saves | `ObjectInputStream`/`ObjectOutputStream` in `WorldSave`, `SaveLoadScene`, LDA deck data | replace with a JSON or custom format stored in IndexedDB |
| XML DOM | `DocumentBuilderFactory` in prefs, `ItemManagerConfig`, quest bazaar | switch to libGDX `XmlReader` or emulate |
| Text | `BreakIterator`, `Collator`, `Normalizer` | simple emulations |

This project now handles all of these without forking TeaVM (see below).

## Status (2026-09-28, round 2)

- **It compiles.** `gdx_teavm_web_js_build` succeeds with no missing APIs, and app.js is about
  80 MB unoptimised.
- **It runs until it touches files.** In the browser, Forge starts (`APP: Forge v.web-spike`) and
  gets into `Forge.create`. It then fails to write preferences (`FileOutputStream`) and to read
  the skin, because gdx-teavm's WebFiles doesn't support `Gdx.files.absolute`. The screen stays
  black.

Next is a browser file system for Forge's `res/` folder (466 MB) and its user data. The plan is to
fetch each `res/` file over HTTP the first time Forge reads it, using an index of what exists,
and cache it. User data (preferences and saves) goes in IndexedDB, and `Gdx.files.absolute` and
`java.io.File` are routed to the new file system.

## Status (round 3)

- The browser file system is done (`forgeweb/fs`). It reads `res/` over HTTP using a manifest
  from `scripts/build-webdata`, gets all card scripts as one 15 MB `cardsfolder.zip`, and keeps
  writes in memory.
- Startup now gets past preferences, the skin, language files and `%n` formatting.
- Fixed a TeaVM thread bug. The "current thread" goes stale after a green thread suspends, so
  Forge thought frames weren't running on the UI thread (`forgeweb/compat/MainThread*`).
- **Next blocker:** `FSkinFont.getCharacterSet` throws a JS null error inside a
  try-with-resources (`Throwable.addSuppressed` is in the stack), so fonts never generate and the
  splash screen can't draw.
- The workflow is `scripts/build-web` (with TEAVM_MEMORY_MB and TEAVM_OPT), then
  `scripts/serve-web` (port 8090), then `scripts/webtest`. That runs headless Chromium and records
  the console log, screenshots, and `stacks` samples for hangs.
- Not done yet: persisting user data to IndexedDB, Adventure saves (which use Java
  serialization), and a GitHub Pages workflow (a release build, where app.js must be under
  100 MB).

## Status (round 4)

- **The splash screen draws** (logo, fonts, progress bar). That needed fixes for two TeaVM 0.15
  classlib bugs.
  - `Throwable.addSuppressed` crashes because the suppressed list is never initialized. That
    hides the real exception of any try-with-resources whose `close()` also throws. Calls now go
    to `JdkCompat.addSuppressed`, which logs `[suppressed] ...` instead.
  - `Inflater.inflate` throws on `Z_BUF_ERROR` (-5) where the JDK returns 0, so every read of a
    deflated zip entry failed. The fix shadows the class with
    `org/teavm/classlib/java/util/zip/TInflater.java`. Our `SubstitutionPolicy` can't replace
    classes that TeaVM's classlib already has, but shadowing the `T` class by name works, because
    project classes come first on the classpath.
- `FSkinFont.getCharacterSet` still hits an `IOException`, but Forge catches it and falls back.
- `webtest`'s `exceptions` step takes an optional filter on the function name, for example
  `exceptions 3 out/x.txt getCharacterSet 150`.

## Status (round 5)

- **All 33,980 cards load**, in about 10 s. Fixed along the way:
  - Card loading hung at 0%. A task threw before `CountDownLatch.countDown()`, so `await()` never
    returned. `Runtime.availableProcessors()` now reports 1 (`JdkCompat`), so Forge loads cards
    inline and a failing task throws where it can be seen. Green threads gain nothing from a
    pool anyway.
  - Reading `ZipFile` entries hit `EOFException`, because TeaVM's `ZipFile` doesn't feed the
    Inflater the dummy byte that the JDK adds after raw deflate data. The fix shadows
    `org/teavm/classlib/java/util/zip/TZipFile.java`.
  - LDA archetype data is Java-serialized, so it can't load. A Forge patch makes
    `CardArchetypeLDAGenerator` skip a format that has no data. TeaVM has no null checks, so a
    null access is a JS `TypeError` that Java `catch` blocks don't see. NullPointerExceptions
    that are harmless on desktop become fatal here.
  - Relative `java.nio` paths (`Files.exists(Paths.get("./res"))`, Adventure's `Config.resPath`)
    reached our virtual file system unresolved. `WebFileSystem.getFile` now resolves them against
    the user directory.
  - libGDX `Json` needs reflection metadata, so build.gradle.kts has
    `reflection("forge.adventure.**")`.
- Dead ends: `fastGlobalAnalysis` and the gdx-teavm dev server (`gdx_teavm_web_js_run`, which
  seems to use fast analysis) both hit about 311 missing APIs. The less precise analysis reaches
  far more code, such as Netty through online chat. They're usable only after stubbing those.

## Status (round 6)

- **Forge reaches its start menu** ("Classic Mode" and "Adventure Mode") after about 2 minutes,
  headless.
- The tab crashed right after `ui/vs.png`. `GifDecoder.getAnimation` turns `effects/demo.gif`
  (248 frames of 718x405) into one 11488x6480 texture. `build-webdata` now leaves the file out
  (`SKIP_FILES`), and Forge skips the animation when the file is missing.
- Open: after the fonts, the page sits **idle for about 60 s** (renderer near 0% CPU, no console
  output) before the second card load. Something is waiting, probably a network request that
  times out.
- Open: `webtest` shows heavy CPU use in the GPU process on the menu. Headless Chromium renders
  WebGL in software (SwiftShader), so judge the frame rate in a real browser.

## Status (round 7)

- **Adventure mode's start screen loads** (New Game, Load, Settings and so on). The menu appears
  after about 16 s headless, down from 90-225 s.
- Startup speed: `build-webdata` packs the folders Forge reads whole (`PACK_DIRS`, such as
  editions, formats and tokenscripts) into `webdata/packs/*.pack`. The virtual file system
  downloads a pack once and slices files out of it. To find more candidates, count the requests
  per folder in `docker logs --since 5m forge-web-serve`.
- `web/html/index.html` replaces gdx-teavm's page. It shows a loading screen at once, with real
  download progress. It downloads app.js, the manifest, cardsfolder.zip and all the packs in
  parallel. `forgeweb.fs.Http` takes those files from `window.forgePrefetch` instead of fetching
  them with blocking sync XHRs. `forgeweb.compat.LoadingScreen` hides the loading screen on the
  first rendered frame.
- Adventure crash fixes: gdx-controllers now uses its no-gamepad stub (`WebLauncher`), and
  classes created by name get the extra care TeaVM needs (see `forgeweb.compat.ByName`). Each one
  needs a `reflection()` pattern and a class literal. `getName()` must be called on the literal,
  or `Class.forName` can't find the class, and the Class must flow into `newInstance()`, or its
  no-arg constructor is dropped. The patterns so far are `forge.adventure.**`,
  `com.ray3k.tenpatch.**` and `com.github.tommyettinger.textra.effects.**`.
- Next: start a new game, reach the world map and fight a battle. After that, a pass on startup
  speed (app.js size, lazy card loading, caching for return visits).

## Status (round 8, overnight)

Verified end to end in headless Chromium with scripts/play-start and scripts/play. The game
starts straight in Adventure and goes through New Game, character creation, world generation,
the tutorial cave, walking to the mage, the dialogue, the reward screen (card backs, items) and
the portal, to the overworld. It runs at 60 fps on an Apple M4 (real GPU) and headless.

Fixed along the way:
- **All game code runs on one green thread** (`forgeweb.compat.UiThread`). Browser callbacks
  (animation frames, input, Gdx.app.postRunnable) only queue work. Forge blocks on its UI thread
  (World.generateNew joins CompletableFutures, and AudioClip sleeps 30 ms in click handlers), and
  TeaVM can't block inside a browser callback. While a task blocks, frames are dropped.
  `Thread.sleep` is also redirected (`JdkCompat.sleep`), so it skips the pause where it can't
  suspend.
- `UUID.randomUUID` is redirected, because TeaVM calls crypto.randomUUID, which only exists on
  https and localhost pages.
- World generation never finished. gdx-teavm copied the whole pixmap out of wasm memory after
  every draw, and generation makes 490,000 draws into a 2800x2800 minimap. The shadowed
  `emu/.../Gdx2DPixmapNative` copies lazily. World generation now takes about 35-45 s, almost all
  of it in wave function collapse, which is the next speed target.
- Black screen on a real GPU. Forge fixes its screen size in create(), and a page started hidden
  had a size of 0x0, so scissoring cut everything away. Now the canvas is drawn at the window's
  size at startup and scaled with CSS, keeping its aspect ratio (index.html `--game-aspect`), and
  create() waits for a real size. Portrait windows get Forge's portrait layout. There is a
  full-screen button, which also locks the orientation on Android.
- The game starts straight in Adventure (the `UI_SELECTOR_MODE=Adventure` default preference). A
  patch to StartScene hides the start screen's Classic and Exit buttons when `forge.web` is set.
- Card backs drew black, because WebGL 1 can't mipmap textures whose sizes aren't powers of two.
  The fix is `config.useGL30`, which uses WebGL 2.
- More classes created by name: textratypist effects and tenpatch (see `ByName`).
- **Saves.** The web build has its own `ObjectOutputStream` and `ObjectInputStream` (classlib
  shadows) with their own tagged format. They handle references and cycles, arrays, collections
  by kind, EnumMap, reflection over fields, and private writeObject/readObject and readResolve.
  Objects are allocated without running constructors, through the class's JS constructor. Saved
  files under /forge/data/ persist in IndexedDB (`UserDataStore`, index.html).

Later the same night:
- **Saving, reloading the page and loading works** (IndexedDB). `java.util.UUID` isn't
  Serializable in TeaVM, so it has its own stream tag. The patch also fixes a Forge bug in which
  AdventurePlayer saved colorIdentity as a byte and read it back as a string, so every loaded
  character became colorless on desktop.
- **Reflection budget.** TeaVM needs about 5 GB, and Docker's VM kills it above that. gdx-teavm's
  `reflection()` exposes every field and method, and the first save serializer called
  getDeclaredMethods() and invoke() on any class. Together they made nearly all of Forge
  reachable through reflection, so builds ran for over 20 minutes and then ran out of memory.
  Now gdx `reflection()` covers only the libGDX Json data classes (its FieldGen needs its
  registry) and small UI libraries. `forgeweb.teavm.WebReflection`, a TeaVM ReflectionSupplier,
  gives *only fields* for save classes and *only constructors* for the rules engine's reflective
  factories. Serializer hooks work only for classes registered with literals
  (`forgeweb.compat.SerialHooks`). The game build is back to about 5 min, and the self-test takes
  about 1 min.
- The rules engine's TriggerType, ReplacementType, Keyword, ApiType and SpellApiToAi construct
  objects by reflection, and SelfTest checks all 202 APIs. `Thread.getStackTrace` returns
  placeholder frames, because Forge indexes trace[2] and TeaVM has no bounds checks.
  `LinkedBlockingDeque` is shadowed with a real BlockingDeque. TeaVM's is a plain LinkedList, so
  Forge's InputQueue.push was never compiled.
- A duel starts and runs to the prompt for the starting player. Next is playing one through.
- **Game events were silently dropped.** Guava's EventBus finds handlers by their @Subscribe
  annotation, which TeaVM doesn't keep. The duel ran, but the screen and log never updated, and
  the hand, library and log stayed empty. `forgeweb.stub.com.google.common.eventbus.EventBus`
  dispatches to public one-argument methods named receive* or recieve*, which is how every Forge
  handler is named. WebReflection exposes only those methods of the listener classes, so add new
  listener classes there.
- **The full loop works in the browser.** From the overworld, the player walks into an enemy,
  sees the VS screen and plays a duel (hand with card art, land drops, the AI casting creatures,
  combat damage, prompts, the Players and Log tabs). After conceding, the result screen shows the
  game log, and the game returns to Adventure with the loss penalty applied (life from 12 to 9,
  gold from 250 to 225). Card art comes from Scryfall's API through Forge's LibGDXImageFetcher
  (Scryfall allows CORS) and is cached in memory only. A public site should keep to Scryfall's
  guidance of about 10 requests per second.
- Conceding crashed. `Game.isGameOver()` was synchronized, and TeaVM compiles small synchronized
  methods with a lock that throws when another green thread holds it. setGameOver holds that lock
  while it fires events. A Forge patch makes isGameOver a lock-free volatile read, and SelfTest
  reproduces the contention. Other small synchronized methods can hit the same TeaVM limitation
  if their lock is held across a suspension. The SelfTest pattern, where the thread holding the
  lock sleeps inside synchronized, reproduces it.
- SelfTest now loads the real card database the way FModel does (Lang, ImageKeys, Localizer,
  tokens) and plays two full **AI-vs-AI matches** with Adventure starter decks, one with plain
  rules and one with Adventure's GameType and forVariants. It checks that each game ends and that
  the log is written. This takes about 2 min.

New testing tools: the `webtest` steps `hold <key> <s>`, `resize <w> <h>` and `reload`, the
`--interactive` option, `scripts/play-start` (a session at the tutorial map) and
`scripts/play '<steps>'`. Stop the play session before building (`scripts/play quit`). Chromium
and TeaVM's 5 GB heap together exceed Docker's memory, and the TeaVM process dies with an RMI
EOF.

## Baseline (2026-09-29, before the PLAN.md work)

`scripts/measure` with a fixed world seed (`?seed=1`), in headless Chromium with software GL,
while other containers were busy:

| Measure | Value |
|---|---|
| app.js | 76 MB raw, 7.1 MB gzip |
| game build | 5m21s, 6.7 GB peak container memory (the Docker VM has 7.7 GB) |
| page load to title screen | 28-40 s |
| world generation | 44-70 s (UI frozen throughout) |
| JS heap at world | 313 MB (the card database is most of it) |
| ArrayBuffers at world | 627 MB, of which libGDX's wasm heap is 331 MB (already 331 MB at the title) |
| renderer process | 1.46 GB; GPU process 743 MB (software GL, so textures count here) |

So the Java heap is not the main cost. The wasm heap (decoded images, in wasm memory that never
shrinks) and other ArrayBuffers are.

## Backend decision to stay on JavaScript (2026-09-29)

The spike: `TARGET=wasm scripts/selftest` builds SelfTest with gdx-teavm's WebAssembly GC target
(`gdx_teavm_web_wasm_build`, TeaVM 0.15). Whole-program analysis passes, but code generation
fails inside TeaVM. `CoroutineTransformation$ListSplitter.createSaveInstructions` and
`WasmTypeInference.pop` crash on methods that can suspend for green threads, including TeaVM's
own `Integer.parseIntImpl` and `Long.parseLongImpl`, libGDX's `BitmapFont.<init>` and
regexodus. Only 8 methods fail, but the cause is a compiler bug in the wasm coroutine splitter,
and our code can't avoid it, because it needs green threads. Our JS-only pieces
(`TObjectInputStream.allocateImpl` through @JSBody) would also need wasm variants.

Decision: stay on the JS backend and fix speed and memory there (PLAN.md phases 2-6). Rerun the
spike when gdx-teavm moves to a newer TeaVM. The `wasm {}` block in build.gradle.kts and
`TARGET=wasm` stay for that.

## Round 9: architecture plan (PLAN.md), 2026-09-29

Memory, measured with `scripts/measure`, whose heap step reports the JS heap, ArrayBuffers, wasm
memories, and live pixmaps with allocation stacks for the large ones:
- **gdx-teavm leaked a decoded image per texture.** Its `AsyncResult` ran the loading task from
  its timer and again on every `isDone()` poll after the second, and `finishLoadingAsset` polls
  like that. So `TextureLoader` decoded each image again after uploading it, and the second
  Pixmap was never disposed. The shadowed `emu/.../utils/async/AsyncResult` runs the task once.
  Live pixmap memory at the title went from 246 MB to 7 MB, the libGDX wasm heap from 331 to
  133 MB, and the renderer from 1.45 GB to 1.1 GB at the world. This is worth reporting upstream
  to gdx-teavm.
- Pixmap mirrors in JS (`Gdx2DPixmapNative.buffer`) are now created only by getBuffer(). A stale
  flag that could hand out old pixels is fixed.

World generation, which took 35-70 s with the UI frozen:
- Forge built a new `OverlappingModel` (patterns and propagator, boxed and O(T^2)) for every
  10x10 chunk. A Forge patch builds one per chunk size instead, and a second Forge patch
  flattens `Model.compatible` into one int[]. Both give exactly upstream's output.
  `scripts/wfc-golden` compiles the upstream and patched sources on the JVM and compares hashes
  over every structure model and every Shandalar structure (`BiomeStructure.initialize`, full
  size, three seeds).
- The chunk solver can be swapped out (`BiomeStructure.chunkSolver`), and the web build solves
  chunks in Web Workers. That's `forgeweb.compat.WfcPool` and `forgeweb.worker.WfcWorker`, a
  separate 212 KB TeaVM build made by `scripts/build-worker` and copied as `wfc-worker.js`.
  `?wfc=local` solves on the page instead, for comparison. The log prints a `World hash:` for the
  generated world.
- With seed 1 on the same machine, generation went from 44-70 s at the baseline to 16.1 s with
  both Forge patches on the page, and to 6.6 s with workers. The world hash was
  cf3e72545c906f8e in every variant.

Correctness:
- Small synchronized methods no longer throw under contention. The shadowed `TObject` "borrows" a
  monitor whose owner is suspended (see its comment, and the SelfTest check).
- Two build-time reports, `forgeweb.teavm.ReachReport` and `ReflectAudit`, write out/reach-*.txt
  (code size per package, and the constructor and static calls that pull each package in) and
  out/reach-*-reflect.txt (every reachable reflective call).

## Round 10: loading feedback, duel-start lag (2026-09-29)

- Loading bar: downloads fill the first 40%, and Forge's own startup fills the rest. In
  `forgeweb.compat.Progress`, CallRedirector routes CardStorageReader.ProgressObserver and
  FProgressBar.setDescription to window.forgeProgress. Progress reports also pause the loading
  green thread, at most once every 100 ms, so the page and Forge's splash screen can draw. World
  generation shows a floating bar of chunks solved out of chunks sent.
- Screen shape: Adventure's viewports stretched a fixed 480x270 screen (270x480 in portrait) onto
  any shape. A Forge patch now makes Scene.getViewWidth/Height extend that design size to the
  screen's shape. Map and world stages show more of the map. The HUD and menus keep their
  layouts, and ViewLayout moves each element, or group of touching elements, with its nearer edge
  and stretches the full-size ones. The canvas fills the window and follows resizes, because
  gdx-teavm's automatic sizing calls Forge.resize, which now updates its screen size and lays the
  scenes out again. The orientation stays as it was at startup.
- The start of a duel froze for 1.7-2 s. The causes and fixes:
  - Autosave, which runs before every duel, town and dungeon, re-encoded the world map PNG and
    two 700x700 arrays each time. A Forge patch makes World encode them once, at the end of
    generateNew, and a loaded world keeps the bytes it read.
  - TeaVM's Deflater is jzlib, in pure Java. The shadowed DeflaterOutputStream compresses with the
    browser's CompressionStream (deflate or deflate-raw) when the caller can wait, and otherwise
    takes the old path. It compresses 3 MB in 57 ms. TDeflater is shadowed only to expose
    nowrap. SelfTest covers round trips with the default Deflater, a custom Deflater and GZIP.
  - Enemy deck generation: CardUtil.CardPredicate now runs its regex and printings checks last,
    which gives the same result because every check fails the same way, and computes the
    minimum-date edition set once.
  - ImageUtil.getPaperCardFromImageKey is memoized. It ran for every card on every frame.

  The worst frame went from 1.7 s to 0.83 s headless. What's left is Match.startGame and the
  first frame of the duel.
- The browser pauses requestAnimationFrame while a tab or pane is hidden, so the game stops, and
  so does any walking the harness drives. Use the headless session for measurements.

## Review (2026-09-29, fresh-context adversarial review) and fixes

- Lost wake-up: yielding right after FThreads.invokeInEdtLater let a quick EDT task call
  notify() before WaitCallback.invokeAndWait started waiting, and the game hung silently.
  Progress now yields *before* posting.
- Scenes are laid out again on any change of screen size, not only of shape, and on first use,
  for a scene or the shared GameHUD created after a resize. ViewLayout keeps sizes that are set
  later, and looks through container groups of zero size (GameHUD's map, hud, menu and avatar
  groups).
- DeflaterOutputStream finishes once, so close() no longer writes a second GZIP trailer, and it
  falls back to the Java Deflater if the browser's compression fails. World doesn't cache a
  failed terrain encoding. AsyncResult keeps task failures and rethrows them. WfcPool marks itself
  broken after a worker error or a 2-minute timeout, and solves on the page after that. Borrowed
  monitors count as held for notify() and holdsLock().
- FreeType: gdx-freetype-web leaked each font's data, because it returned the address through a
  copied int[]. The shadowed emu FreeType.java allocates in Java. Forge's font preloading used up
  the fixed FreeType heap at some screen sizes.
- Known and not fixed yet:
  - Native deflate buffers the whole payload. At the end of generation that's the world map PNG,
    about 31 MB raw, which is a short spike on phones.
  - The native path ignores the caller's Deflater level and state.
  - Extended views much taller or wider than 960 units can show world chunks that aren't loaded.
  - The ImageUtil memo is unbounded.

## Round 11: Load screen, time to gameplay (2026-09-29)

**The Load screen froze whenever a save existed.** SaveLoadScene crashed on
`header.name.contains(...)`, because the header read back with every field null. The narrowed
reflection list (WebReflection) never made the private serialization hooks callable, so
`SerialHooks` found no `writeObject`, `readObject` or `readResolve`, and the object streams
silently fell back to plain fields. WorldSaveHeader has no visible plain fields, so nothing was
written and nothing was read. The same happened to `PaperCard.readObject`, which restores the
transient `rules` field (loaded booster decks had cards without rules), and to
`Deck.readResolve`. Now WebReflection.SERIAL_CLASSES exposes exactly those hooks. Exposing them
on every class pulled in Guava's hooks, which need JDK classes TeaVM lacks. SerialHooks owns the
class list and warns at startup when a class has no hooks. SelfTest checks the hooks, a header
round trip (through Deflater, the way WorldSave writes it), and that a saved deck's cards keep
their rules (36 of 36). A Forge patch makes SaveLoadScene skip an unreadable save instead of
breaking the list. Verified: quicksave, reload, the Load screen lists the save (date, location),
and loading it restores the map and the player. Save preview thumbnails are black, which hasn't
been investigated yet.

**Time to gameplay.** The page now logs `[ttg]` lines with the time each startup stage began
(page, app.js, Forge's own splash labels, scenes), and totals at the title screen and at
gameplay, meaning the first map the player walks on. This works in any browser; look for `[ttg]`
in the console. Findings, headless with software GL and a local server, where the title screen
used to appear at about 33 s:
- Card scripts: each entry in `cardsfolder.zip` was deflated separately, so Forge inflated 34,000
  entries with JZlib compiled by TeaVM, about 10% of startup. Now the entries are stored
  uncompressed and the whole file is gzipped, and the page un-gzips it natively while it
  downloads (DecompressionStream). Every file in forge-data/ is handled the same way
  (`<name>.gz`). The game data download went from 23 MB to 7.5 MB, with the card zip going from
  15.8 to 5.8 MB and the manifest from 1.3 MB to 0.2 MB. The page checks for the gzip magic
  number, so it also works with a host that sends Content-Encoding: gzip.
- `String.CASE_INSENSITIVE_ORDER`, which the card database's TreeMaps use, went through
  TCharacter.toLowerCase's Unicode table for every character (about 7%). CallRedirector now sends
  compareToIgnoreCase and equalsIgnoreCase, including the call inside TeaVM's comparator, to
  StringCompat, which has a fast path for ASCII.
- Fonts: Forge pre-generates 65 font sizes, then writes each atlas as a PNG and loads it back. On
  phones that cache persists, but ours is in memory. The UI-thread half of that work queued up
  behind startup ("Preparing database", 4.6 s), and generating took another 3 s, about 7.6 s in
  all. Keeping the cache in IndexedDB was tried and dropped, because loading 65 cached atlases
  took as long as generating them. A Forge patch makes FSkinFont keep the generated font on the
  web, with no PNG round trip. That also fixes upstream leaving the first set of page textures
  unused. Fonts now take about 1.7 s, and "Preparing database" 0 s.
- deckgendecks/*.dat (7 MB) was downloaded at every start, only to fail to parse, because it uses
  JDK serialization. It's now left out of the web data, and Forge falls back as it did before.
- There were 144 blocking requests (sync XHR, one round trip each) before the title screen, and
  about 220 before the world. scripts/record-startup now records the files a new game reads
  (web/startup-files.txt), and build-webdata packs them into packs/startup.pack, which is
  prefetched with app.js. webtest's `--latency MS` and `--mbps N` options emulate a hosted page.
- The dev server uses HTTP/1.1 keep-alive. With a new connection for each request, Docker's port
  forwarding sometimes stalled a request for a minute in headless runs.
- Results, headless while another project's containers were busy, so compare phases rather than
  totals. The title screen went from about 33 s to 17.7-18.3 s when the machine was quiet. At
  `--latency 50`, "Finishing startup" went from 10.7 s to 3.2 s with the startup pack (2.9 s with
  no latency), and world generation from 19.7 to 15.9 s, about 11 s less on a hosted page. In
  SelfTest, card scripts went from 4.3 to 1.6 s, and the card database from 12 to 5.6 s.
- Next: the startup pack is 17.6 MB gzipped, and 5.9 MB of it (77 files) is only needed after the
  title screen. Splitting it needs Java to wait for a download that's already in flight (the
  Phase 6 async fetch). "Loading cards from archive" (about 6.5 s) and "Finishing startup" (about
  3 s) are the biggest phases left.

**White lines in the cave (texture bleeding).** With a 480-unit view on a 1155 px screen, a
16-unit tile is 38.5 px wide. With the camera on whole units, every other tile edge lands exactly
on a pixel centre. Which tile gets that pixel depends on the GPU's sub-pixel vertex snapping
(1/16 px in SwiftShader), and nearest sampling then reads the neighbouring tile in the tileset
image. The line's beige is the column just left of tile 2215 in main.png. Upstream rarely shows
this, because 1080p is exactly 4 px per unit. A Forge patch to TemplateTmxMapLoader insets every
tile region, and the copies that tile objects carry, by 0.1 texel (0.01 was not enough).
web/tools/find-seams.py scans screenshots for bright lines one pixel wide. It found 7 long lines
in 32 cave frames before the fix and none after, at 1155x898 and 1710x898. The white line across
the duel background is Forge's own field separator, which MatchScreen draws in every layout, and
not a rendering bug.
Seen while checking: where the view is wider than a small map (the cave is 480 units, the view
514), the area past the map shows the clear colour (0,0,0) next to the map's own (6,6,8), which
makes a faint band edge.

**Seams in the UI too.** At 1155 px wide, dark lines crossed some buttons in the Load list.
TenPatch (the Adventure skin's `...10patch` drawables) and libGDX's NinePatch keep texture
coordinates off patch edges only for linear filtering, and the skin uses nearest filtering. Two
shadow classes fix this, since project classes come first on the classpath:
`com/ray3k/tenpatch/TenPatchDrawable.java` (5.2.3, inset in `drawToBatch`) and
`com/badlogic/gdx/graphics/g2d/NinePatch.java` (1.14.2, inset in `add`). Each changes only the
marked block, adding a 0.1 texel inset for nearest filtering. The sources came from the pinned
versions' source jars (`mvn dependency:get ...:sources`, and TenPatch from jitpack).

**Resize crash ("The number NaN cannot be converted to a BigInt").** TeaVM 0.15 keeps Java longs
as BigInt and converts a double or float to long with `BigInt(Math.floor(x))`. NaN and infinities
throw and large values wrap, where Java gives 0 for NaN and clamps to Long.MIN_VALUE and
Long.MAX_VALUE. Any `(long)` cast of a NaN, such as a 0/0 while laying out at an odd size,
crashed the game. TeaVM reads the snippet (`long.js`) through its own class loader, so a resource
shadow doesn't work. Instead, build.gradle.kts patches `Long_fromNumber` in the generated app.js
after every JS build (game, selftest and worker). It streams the file, and fails the build if
TeaVM's code changes. The SelfTest check is "(long) casts ...". Resizing through 1710x898, 1x1,
700x1000 and 1155x898 gave no errors.

**The full-screen button** sat over End Turn and Mulligan, and every corner has game UI. It's now
at the top centre and hides itself. It shows for 4 s after loading, then whenever the pointer
rests near the top centre or there's a touch there. While hidden, it lets clicks through.

**VS screen names were clipped** in portrait. The font scale depends only on the screen's shape,
so in a smaller window the names were huge and the bottom one ran off the screen. A Forge patch
to TransitionScreen shrinks them only as far as needed, so both names fit across (90%) and, in
portrait, in the strip below the avatar. scripts/play-start takes `WIDTH` and `HEIGHT`, and the
scripts now wait for `ui/new_game`, because portrait loads `new_game_portrait.json`.

**Save thumbnails were black.** `ScreenUtil.getThumbnailPreview` reads the framebuffer on a key
press or autosave, outside of drawing a frame, and WebGL clears the framebuffer once a frame is
shown. WebLauncher now sets `preserveDrawingBuffer = true`, with no measured change in frame
rate. A Forge patch makes ScreenUtil follow the screen size. It was fixed at first use, so
captures after a resize read the old size.

## Debugging a stuck run

`webtest` prints `[hb]` heartbeat lines with the time since the last console line, and the
renderer and GPU memory and CPU read from /proc. It stops with exit code 4 on `[tab crashed]`,
and gives up after `--max-time`. Idle CPU with no console output means the page is waiting on
something. Rising memory and high CPU followed by a crash means runaway allocation.
`PW_DEBUG=pw:browser scripts/webtest ...` adds Chromium's own stderr.

## Fast checks with `scripts/selftest`

A full game build and run takes about 8 minutes. `scripts/selftest` builds
`forgeweb.selftest.SelfTest` instead, with the same shims, redirects, virtual file system and
reflection configuration but without the game, and runs it in headless Chromium. That takes
**about 25 s**, and the exit code is 0 only if every check passes.

For a new runtime crash, reproduce it as a check in `SelfTest`, which can use Forge and libGDX
classes directly. Fix it until the check passes, then do the full game build. Every check there
corresponds to a real bug found in the game. `SKIP_BUILD=1 scripts/selftest` reruns the checks
without building.

## How the web layer plugs in

| Mechanism | Where | Used for |
|---|---|---|
| TeaVM classlib naming: `java.X.Foo` → `org.teavm.classlib.java.X.TFoo` | `web/src/main/java/org/teavm/classlib/java/...` | missing JDK classes: concurrency, locks, text, sql, net, io |
| Our `SubstitutionPolicy` (`forgeweb.ForgeWebSubstitutionPolicy`, registered in `META-INF/services`) | `forgeweb/shim/...` (T-prefixed), `forgeweb/stub/<original package>/<Name>` | XML (`org.w3c.dom`, `javax.xml`, `org.xml.sax`, `javax.swing`), and whole-class web stand-ins: Sentry, tinylog, `sun.misc.Unsafe`, Forge's `ExceptionHandler`, `AssetsDownloader`, `FServerManager` |
| gdx-teavm's policy: `com.badlogic.gdx.X` → `emu.com.badlogic.gdx.X` | `emu/com/badlogic/gdx/physics/box2d` | pure-Java Box2D subset (static boxes, rayCast, testPoint) |
| Our TeaVM plugin (`forgeweb.teavm.CallRedirector`) | `forgeweb/teavm`, helpers in `forgeweb/compat` | single missing methods (`File.toPath`, `Runtime.maxMemory`, ...): call sites rewritten to static helpers |
| Forge patches | `patches/forge-web.patch` (`git -C forge diff`; new files need `git -C forge add -N` first) | web fixes (FileUtil, CdnUuidCache, Localizer, ...) plus performance and layout changes worth upstreaming (world generation, saves, card lookups, Scene view and ViewLayout) |

Rules learned the hard way:
- Inside a substituted class (anything under `forgeweb/shim`, `forgeweb/stub` or
  `org/teavm/classlib`), references to shim types are renamed back to the original types. So
  shims must only call other shims, never a plain helper class whose signatures mention shim
  types. That's why the XML parser lives in `forgeweb/shim/javax/xml/parsers` as
  `TMiniDom`/`TMiniXml`.
- gdx-teavm's `@Emulate` annotation does not pick up classes from this project, so don't use it.
- `web/tools/gen_tinylog_stubs.py` generates the tinylog stand-ins.

## Updating Forge

Forge is pinned to the commit in `FORGE_COMMIT`. To move to a newer Forge:

```bash
scripts/update-forge master          # or a tag/commit; rewrites FORGE_COMMIT, re-applies patches
scripts/build-forge-libs
scripts/build-web
```

What can break, and where to fix it:
1. **A patch no longer applies.** `update-forge` stops. Redo the change by hand in `forge/`, then
   run `git -C forge diff > patches/forge-web.patch`.
2. **The compile lists new "was not found" APIs.** Forge started using something new. Add a shim,
   a redirect in `CallRedirector`, or a stub (see the table above). The build log's `at ...`
   lines show which Forge code reached it.
3. **A stubbed class changed its API** (`FServerManager`, `AssetsDownloader`,
   `ExceptionHandler`). The compile reports a missing method on that class, so add it to the
   stub.
4. **Runtime behaviour changed.** Only a run in the browser shows this.

To keep updates small, update often, and try to upstream `patches/` to Card-Forge.
