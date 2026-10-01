# Architecture plan (2026-09-29)

Goal: fix the structural problems that the architecture review found, instead of working around
them, so that Adventure runs well on phones. Fix root causes. Use a stopgap only as a stepping
stone toward the real fix, and label it as one.

Each phase has a measurable exit criterion. NOTES.md records the numbers as the work goes.

## Phase 0: Baseline (measure before changing)
- Make world generation take a fixed seed (`?seed=N` or a test property), so that every run
  makes the same world.
- Record the size of app.js (raw and gzipped), the build time and peak TeaVM heap, the
  world-generation time, JS heap and GPU memory on the overworld (a Chrome heap snapshot through
  Playwright and CDP), and the time from page load to the title screen.
- Exit: a `scripts/measure` that prints these numbers.

## Phase 1: WebAssembly GC spike (backend decision)
- Build `SelfTest` with gdx-teavm's wasm target (TeaVM 0.15 wasm-gc).
- Unknowns: green threads (Thread, wait, notify, sleep), our classlib shadows,
  ReflectionSupplier, JSO code (Http, UserDataStore, LoadingScreen), and how complete
  gdx-teavm's wasm backend is.
- Add a SelfTest check that times WFC world-generation work, to compare JS and wasm directly.
- Exit: a decision recorded in NOTES.md, either to move to wasm (and what that takes) or to stay
  on JS (and why).

## Phase 2: Compile only what Adventure uses
- Get TeaVM's dependency report, and find the entry points that pull in code only Classic mode
  uses (deck editors, quest, planar conquest, online play, downloaders, Classic screens).
- Stub those entry points through ForgeWebSubstitutionPolicy. The stubs throw if anything ever
  reaches them.
- Exit: app.js and the build heap are measurably smaller, and SelfTest and the play-through
  still pass.

## Phase 3: Systemic correctness
- Synchronized methods. A TeaVM transformer drops `synchronized` from methods that cannot
  suspend, since they are atomic under green threads, and leaves the rest alone. Then remove
  the isGameOver patch.
- Reflection audit. A build-time bytecode scan of the Forge jars finds reflective use
  (forName with strings, getMethod and getField, newInstance, annotations), compares it with our
  registries, and fails on any gap. WebReflection, ByName and SerialHooks become one
  declaration.
- The EventBus stand-in warns about listeners that have no visible handlers.
- Exit: SelfTest passes without the isGameOver patch, and the audit reports no uncovered call
  sites.

## Phase 4: World generation, the real fix
- Profile Model.propagate and the rest of World.generateNew on the chosen backend.
- Fix the hot paths (boxing, long arithmetic, collections) as Forge patches that could go
  upstream.
- Run generation outside the UI thread's frame loop (on its own green thread, or in a worker if
  wasm makes that cheap), so the page keeps drawing progress.
- Exit: generation takes under about 10 s on desktop, and the page is never unresponsive for
  more than 100 ms.

## Phase 5: Memory for phones
- Act on the Phase 0 snapshot: load card scripts lazily (LOAD_CARD_SCRIPTS_LAZILY), release the
  minimap pixmap after uploading it, cap the card image cache with an LRU policy, and fix
  anything else the snapshot shows.
- Exit: the overworld uses under 1 GB in total (JS heap and GPU), tested at a phone viewport.

## Phase 6: I/O and persistence
- Fetch asynchronously, suspending with @Async, instead of using synchronous XHR. Keep sync XHR
  only as a fallback outside threads that can suspend. Prefetching becomes optional.
- Cache app.js, the packs and res files in a service worker, for fast repeat loads and offline
  play.
- Save format: put a version and the Forge commit in the header, and tolerate added or removed
  fields.
- Exit: no sync XHR on the normal path, the second load comes from the cache, and old saves
  still load.

## Phase 7: Test infrastructure, then continue the play-through
- Include WebTest only in test builds, and drive play-start with harness commands rather than
  pixel coordinates.
- Finish the harness (exit-only POIs, towns, shops, duel commands), then win a duel, visit a
  shop, and complete the "travel to town" quest.

## Status

- Phase 0 is done: `scripts/measure`, `?seed=N`, build peak memory, and heap and pixmap
  accounting in webtest's `heap` step. The baseline is in NOTES.md.
- Phase 1 is done. WebAssembly GC fails in TeaVM's coroutine transformation (a compiler bug), so
  the project stays on JS. NOTES.md has the details.
- Phase 2 has started. `forgeweb.teavm.ReachReport` writes out/reach-*.txt, with the code size of
  each package and the call chain that pulls it in.
- Phase 3 has started, with borrowed monitors (a shadowed TObject) and a SelfTest check for them.
  `ReflectAudit` writes out/reach-*-reflect.txt.
- Phase 4: world generation went from 44-70 s to 6.6 s, first through model reuse and flat arrays
  (as Forge patches), then through Web Workers. The output is identical (checked with
  scripts/wfc-golden and the World hash). The page responsiveness check is still to do.
- Phase 5 has started. The gdx-teavm texture leak is fixed (AsyncResult), and pixmap mirrors are
  created lazily. The renderer went from 1.45 GB to 1.1 GB at the world.
- Phase 7 has started. The harness is only installed with `?test`.

## Next (from the watched play-through, 2026-09-29)

User-reported:
- The loading bar reads 100% while "Starting Forge" still takes 20-40 s (cards, skin). It should
  show the real startup stages. The same happens during "Generating world", where the bar is
  full. It should report how many structures are done out of the total, which is now possible
  because the page is free while the workers run.
- Full screen should fill the screen. Forge should lay out again at the new size instead of
  letterboxing, including after rotation on phones and whenever the window is resized.

Harness gaps found while playing (forgeweb.test.WebTest):
- `dismiss` can press a choice before all the choices have appeared. It should wait until the
  list of buttons stops changing.
- `state` should report the mana cost and colours of cards in hand, and lands other than basics
  (for example Timber Gorge). It should describe Forge's overlays (damage assignment, card
  choosers, ordering) instead of listing only their buttons.
- The duel autopilot from the browser session (playing a land, colour-aware casting, targets,
  attacking, and Auto for damage, ordering and choosers) should become a harness command
  (`autoplay [turns]`).
- Check the hand layout in landscape, where the hand is drawn as a column on the right, against
  Forge.
- Pre-existing: fallback_skin/title_bg_lq.png fails to load at startup, and a dummy texture is
  used instead.

Milestones reached: the tutorial, a town and a shop purchase, the overworld, and the first duel
won (rewards were 10 cards, 96 gold and an achievement). All of it was driven through the
harness and watched live in the browser.
