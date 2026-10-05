---
type: overview
updated: 2026-10-01
---

# Log

Append-only. Newest at the bottom. `grep "^## \[" wiki/log.md | tail -5`

## [2026-09-29] schema | Wiki created
- Created SCHEMA.md (layout, page conventions, ingest/query/lint workflows), index, overview.
- Root CLAUDE.md points sessions to the wiki.
- Added `tools/lint`.

## [2026-09-29] ingest | NOTES.md, PLAN.md, forge-web.patch, web layer code
- Ingest point: **NOTES.md through "Updating Forge"** (463 lines, including "Round 10" and
  "Review (2026-09-29)"); **PLAN.md through "Next (from the watched play-through, 2026-09-29)"**.
  Next ingest of either: start after these sections.
- Read code for: CallRedirector, Unsupported, WebReflection, ByName, SerialHooks, UiThread,
  TObject, WfcPool, Progress, fs/*, SelfTest check list, WebTest commands, build.gradle.kts,
  script headers.
- Pages: 13 concepts, 1 component, 4 entities, 2 bugs, 5 decisions, 3 status, 5 how-to, 4 sources.
- Found while cross-checking:
  - The `Game.isGameOver` Forge patch is **gone** from `patches/forge-web.patch`, superseded by
    borrowed monitors, so the first half of Phase 3's exit criterion is met. PLAN.md still lists it as
    a to-do.
  - PLAN Phase 2 names ForgeWebSubstitutionPolicy for stubs; the code uses `forgeweb.teavm.Unsupported`.
  - SelfTest duration conflict (~25 s vs ~1 min / AI matches ~2 min), noted in [[metrics]].

## [2026-09-29] update | Round 11: Load screen, time to gameplay, cave seams
- Read: NOTES.md "Round 11" (new section after "Review"), the session's diffs (WebReflection,
  SerialHooks, StringCompat, Http, WebFileSystem, index.html, build-webdata, record-startup,
  webtest.py, find-seams.py, Forge patch: SaveLoadScene, FSkinFont, TemplateTmxMapLoader).
  Next NOTES ingest: start after "Round 11".
- Pages: saves (hooks bug), reflection-on-teavm, startup-and-loading (new "Time to gameplay"
  section; superseded the loading-bar warning, fixed in Round 10), virtual-file-system (gzip,
  startup pack, SKIP_DIRS, keep-alive), screen-layout (seams), webtest-harness, metrics
  (title 33 → 18 s, download 23 → 7.5 MB, latency table), open-issues (removed fixed items,
  added later user reports), bug-catalog (StringCompat, FSkinFont, tile edges, two lessons),
  plan-phases (Phase 6 started), selftest (36 checks, log name), code-map, teavm, overview,
  src-notes-md.
- Resolved from the ingest's open items: selftest duration note (checks ~35 s in page, R11).

## [2026-09-29] update | Round 11, part 2: resize crash, UI seams, VS names, thumbnails, button
- Read: NOTES.md Round 11 (sections from "Seams in the UI too" to "Save thumbnails black"); diffs:
  build.gradle.kts (Long_fromNumber patch), NinePatch/TenPatchDrawable shadows, TransitionScreen,
  ScreenUtil, WebLauncher (preserveDrawingBuffer), index.html (button), play-start (WIDTH/HEIGHT).
- Pages: web-layer-mechanisms (two new mechanisms + rules), bug-catalog (TeaVM long cast,
  NinePatch, TenPatch, VS font, ScreenUtil), screen-layout, saves, open-issues (4 items fixed).

## [2026-10-01] update | Public release preparation and a writing pass
- Prepared the repository for release on GitHub: `git init`, README.md, LICENSE (GPL-3.0, like
  Forge), `docs/overworld.png`, and a `.gitignore` that excludes heap dumps (`*.hprof`) and
  per-machine files. Anchored its patterns (`/forge/`), because the unanchored `forge/` had also
  excluded `web/src/main/java/forge/` (10 source files; [[bug-catalog]]).
- Script fixes: `scripts/build-web` no longer uses macOS-only `sed -i ''`; `play-start` and
  `record-startup` create `out/` first.
- Built the tracked files from a clean checkout and loaded the result to the title screen
  ([[build-pipeline]]).
- Writing pass with the `deslop` skill over README, CLAUDE.md, PLAN.md, NOTES.md, every wiki page
  except this log and `raw/`, script and source comments, and the comments in
  `patches/forge-web.patch` (regenerated; code unchanged). The wording changed, not the facts:
  NOTES.md keeps its sections and headings, so the ingest points above still hold (NOTES.md
  through "Updating Forge", including all of Round 11). Its "Updating Forge" recipe now uses
  `scripts/build-web`.
- Corrections found on the way: overview's "Next" listed Round 11 fixes as open;
  src-forge-web-patch was missing four Round 11 files (FSkinFont, ScreenUtil,
  TemplateTmxMapLoader, TransitionScreen); code-map was missing the NinePatch and TenPatch shadows;
  build-pipeline's SelfTest time disagreed with selftest; reflection-budget counted "three"
  registries for four; `scripts/selftest` named the old log file; index.html said
  `scripts/measure` greps the `[ttg]` lines, which it doesn't.
- open-issues: "Before a public release" became "Before hosting a public build" (Scryfall rate,
  GitHub Pages), since those apply to a hosted site, not to the source release.

## [2026-10-01] update | Hosting on GitHub Pages
- Added `scripts/build-site` (the static site in `web/build/site`, 310 MB) and
  `.github/workflows/pages.yml`, which builds from scratch and deploys on each published release
  or by hand. Before pushing, the site was served from a `/shandalar/` subpath and played to the
  tutorial cave: title at 27 s, world generated in 13.6 s, World hash `cf3e72545c906f8e`, no
  errors.
- `scripts/dock` hands files back to the caller on Linux (the container's root owned them, which
  broke `build-web` on a Linux runner).
- Pages: build-pipeline, overview, open-issues (Hosting section).

## [2026-10-01] update | Renamed the project to Forge Adventure Web
- The user chose "forge-adventure-web": "Shandalar" is the name of Forge's Adventure world, not of
  this project. The GitHub repository is now CosmicScribe64/forge-adventure-web, and the hosted
  build is at https://cosmicscribe64.github.io/forge-adventure-web/.
- Changed the project name in README.md, CLAUDE.md and overview, and the Docker names: the image is
  `forge-adventure-web-build`, and the cache volumes are `forge-adventure-web-m2`, `-gradle` and
  `-pip`. "Shandalar" stays wherever it means the game world.
- The first Pages run failed because services.gradle.org returned HTTP 500 while building the
  Docker image. The Dockerfile now retries that download.

## [2026-10-01] update | Live on GitHub Pages, release v0.1.0
- The Pages workflow's second run passed in 7.5 minutes on GitHub's runner (the first failed on
  an HTTP 500 from services.gradle.org). On Linux, `scripts/dock` handing files back to the caller
  let `build-web` finish.
- The live site, played headless from this machine: title at 65 s (24 s of it downloading), world
  generated in 12.1 s, World hash `cf3e72545c906f8e`, no console errors. Pages gzips app.js to
  7.5 MB, so a first visit downloads about 32 MB in total. Pages sends `Cache-Control:
  max-age=600` for every file and ignores the long caching `serve.py` gives versioned URLs.
- Published release v0.1.0 (Forge Adventure Web 0.1.0); its notes and the repository's About
  sidebar link to https://cosmicscribe64.github.io/forge-adventure-web/.
- The release's own deploy job was first rejected: GitHub created the `github-pages` environment
  allowing only the `main` branch, and a release runs from its tag. Added a `v*` tag rule to the
  environment and reran the deploy, which passed.

## [2026-10-01] update | Loader progress on GitHub Pages, local folder renamed
- User report from the live site: "Downloading game data… 62 / 32 MB". Pages gzips app.js on the
  fly, so its Content-Length was 7.5 MB while the page counted 76 MB of decoded bytes. The page now
  downloads `app.js.gz` (6.8 MB, written by `scripts/build-web`) and un-gzips it like the game
  data, and counts any file the browser decoded only once it completes. Checked headless against
  `serve.py` and a test server that compresses like Pages: the text never passed its total, and
  both ended at "31 / 31 MB". The site is 245 MB now, since it no longer ships the raw app.js.
- The local project folder was renamed to `~/Documents/Claude/forge-adventure-web`, and the
  Obsidian vault entry with it.
- Scryfall: Forge's own `ScryfallRateLimiter` (100 ms between `api.scryfall.com` requests, 500 ms
  for search, backoff on 429; the `cards.scryfall.io` CDN is unthrottled) runs on green threads,
  where `Thread.sleep` really waits. Not yet measured in a session: the test reached no card art.
- Released v0.1.1 with the loader fix. On the live site, `app.js.gz` arrives as
  `application/gzip` with no Content-Encoding, and the loader text never passed its total and
  ended at "31 / 31 MB".
- Added `.github/workflows/ci.yml`: on every push to main and every pull request it runs the wiki
  lint, `update-forge`, the Forge build, `build-webdata`, `scripts/selftest` and `build-web`. Its
  first run passed, which was also the first SelfTest run on Linux.

## [2026-10-01] update | Saves on the live site, the phone crash, an honest README
- Saves work on the live site: a quicksave on the world map survived a page reload, the Load
  screen showed its date, thumbnail and location, and loading it restored position, life, gold
  and shards. No console errors.
- User report: phones crash after the main menu appears. Memory at the menu is about 1.3 GB
  (renderer plus GPU) whatever the screen size; numbers in [[memory-budget]], issue in
  [[open-issues]].
- `webtest` gained `--scale` (device pixel ratio) and `--mobile` (mobile viewport and touch).
- README: a Status section that says plainly what works, that phones don't, and that a lot still
  breaks.

## [2026-10-01] update | Current state and next steps
State at the end of the day:
- Live at https://cosmicscribe64.github.io/forge-adventure-web/ (release v0.1.1). CI runs the
  patch, the Forge build, SelfTest and the game compile on every push; it and the Pages deploys
  all pass. Saves work on the live site. The README says plainly what works and what doesn't.
- Desktop Chrome plays through the tutorial, world generation, overworld, towns, duels and saves.
  Much else still breaks, and other browsers are untested.
- Phones crash (an iPhone running Chrome): about 1.3 GB at the main menu.

Next steps:
1. Phone memory (PLAN Phase 5, top priority), in the order listed in [[memory-budget]]: stream
   music with Howler's `html5: true` (about 62 MB per decoded track today), revoke the app.js and
   audio Blob URLs, drop the startup pack and card zip after startup, take a heap snapshot of the
   310 MB of ArrayBuffers, then shrink the card database. Measure each step at the main menu and
   the overworld, and test on WebKit.
2. Measure Scryfall request timing in a duel, to confirm Forge's rate limiter works in the
   browser ([[open-issues]]).
3. The other items in [[open-issues]]: phone rotation, splitting the startup pack (async fetch),
   and the harness gaps.

## [2026-10-04] update | Filling gaps in the to-do list
- Checked the future work against the wiki and added what was missing: the Howler music bug in
  [[bug-catalog]], an Upstream section in [[open-issues]] (report the TeaVM and gdx-teavm bugs,
  offer the Forge patches), the non-reproducible card zip, and the startup-share discrepancy in
  `build-webdata`'s docstring.

## [2026-10-04] update | Everything needed to continue is in the repo
- New [[pick-up-work]] page: what to read first, CI and releases (including the Pages environment
  rule), test helpers, and the conventions that had lived only in a session's private notes (the
  project name, plain writing and its checker, root-cause fixes, honesty about the state, noreply
  commits). README.md and CLAUDE.md point to it.
- Moved the test helpers from a session scratch folder into the repo: `web/tools/serve-compressed.py`,
  `web/tools/loader-check.js` and `web/tools/scryfall-requests.js`.

## [2026-10-04] query | Does the Scryfall rate limiter space requests in the browser?
- Drove a headless session to a duel in the Blue Tower and to the Adventure deck editor, and
  recorded `XMLHttpRequest.send` timing plus the console log. Read `ScryfallRateLimiter`,
  `LibGDXImageFetcher`, `TGreenThreadExecutor` and the shadow `TObject`.
- Result: gaps between sends were at least 111 ms (duel: 7 requests, median 201 ms, peak 6 in one
  second; deck editor: 3 requests), no 429s, no "429" or "cooldown" log lines. Spacing works.
- Found that a burst of 11 downloads loses 8 of them, because TeaVM 0.15's `waitForOtherThreads`
  discards queued monitor waiters. Fix proposed, not made.
- Pages touched: [[scryfall]], [[open-issues]], [[bug-catalog]], [[green-threads]].

## [2026-10-04] fix | TeaVM monitor queue dropped waiting threads
- Fixed the shadow `web/src/main/java/org/teavm/classlib/java/lang/TObject.java`:
  `waitForOtherThreads` now sets the queue to `null` only when it is empty after removing one
  waiter. The `notify`, `notifyAll` and `monitorEnter` paths were read and have no similar flaw.
- The [[selftest]] check "every thread queued on a held monitor eventually enters it" already
  existed. Without the fix it failed (1 of 4 contenders entered); with the fix the suite passes,
  38 of 38.
- Rebuilt the game and repeated the deck editor burst in headless Chromium: 22 sends to
  `api.scryfall.com`, gaps 103 to 148 ms, busiest second held 9 sends. Sandbox proxy errors made
  the downloads themselves fail, so only the sends and spacing were verified.
- Pages touched: [[bug-catalog]], [[green-threads]], [[scryfall]], [[open-issues]], [[selftest]].

## [2026-10-04] fix | Stream music with Howler html5 (memory plan step 1)
- Read [[memory-budget]], [[metrics]], [[open-issues]] and the Howler row of [[bug-catalog]]. Added a
  shadow `HowlMusic` that creates music Howls with `html5: true`, retries `play()` on Howler's
  `unlock` event (a refused HTML5 play is dropped, unlike a Web Audio one), and revokes the Blob
  URL in `dispose()`.
- Measured headless, software GL, seed 1, same build with the flag patched in `app.js`: renderer
  at the menu 830 to 776 MB (desktop) and 835 to 777 MB (phone size 390x844 at 3x), at the
  overworld 1117 to 1031 MB; GPU unchanged. A first run looked like only 5 MB saved because the
  dist used as baseline was already a streaming build, so the flag was toggled in one build to
  compare.
- Without the retry the menu music never started in the test; with it, it starts at the first
  click. Track changes dispose the old Howl. Not tested on a real iPhone.
- Pages touched: [[memory-budget]], [[open-issues]], [[bug-catalog]], [[metrics]], [[code-map]].

## [2026-10-05] fix | Heap snapshot, and app.js source stored as one-byte text (memory plan steps 2 to 4)
- Read [[memory-budget]], [[metrics]] and [[open-issues]]. Step 2 (revoke the app.js Blob URL) was
  measured and saved nothing (renderer 773 to 776 MB at the desktop menu), so no code was kept.
- Added a `snapshot` step to `web/tools/webtest.py` and took a V8 heap snapshot at the menu.
  Of 310 MB of backing stores, 145 MB was the `app.js` source held as a two-byte string because
  2468 characters are above U+00FF; the wasm heaps are 80 MB, the card zip 27.5 MB, the startup
  pack 21 MB.
- Reordered the plan on that evidence: added `web/tools/latin1-js.py`, run by `scripts/build-web`,
  which escapes those characters. Renderer at the menu 773 to 701 MB (desktop), 779 to 705 MB
  (phone size); overworld 1034 to 954 MB; GPU unchanged. Selftest 38 of 38. A gameplay save and
  load was not exercised (Save is greyed in the starting cave).
- Pages touched: [[memory-budget]], [[metrics]], [[open-issues]], [[build-pipeline]].

## [2026-10-05] fix | Packs and card zip dropped after startup; GPU memory measured (memory plan steps 3 and GPU)
- Read [[memory-budget]], [[open-issues]], [[metrics]] and [[virtual-file-system]]. Found the readers
  of the pack map and the zip data (`ensureLoaded`, the accessor, Forge's open `ZipFile`) before
  changing anything.
- `WebFileSystem` now drops big read-only remote files (the card zip) after 5 idle seconds and a
  pack once all its files are read; `Accessor.read` loads dropped files again on demand. A
  network hook showed the first version (packs on the idle timer) downloaded the startup pack
  twice, so packs are dropped only when fully read; no pack or zip refetch in a new game, town,
  two duels or the deck editor. Menu: renderer 699 to 669 MB (desktop), 703 to 667 MB (phone
  size); overworld 957 to 916 MB; GPU unchanged. New selftest check; 39 of 39 pass.
  A new game, town, and duel start were played on the trimmed build.
- Added `--init-script` to `web/tools/webtest.py` and used a WebGL hook to total live GPU
  memory: 296 MB of textures at the menu (125 MB font pages, about 119 MB desktop skin sprite
  sheets), 349 MB at the overworld, buffers negligible. Wrote down reductions with estimates and
  why the software GL figure is only partly a fair stand-in for a phone.
- Pages touched: [[memory-budget]], [[metrics]], [[open-issues]], [[webtest-harness]].

## [2026-10-05] fix | Font sizes made on first use (memory plan, GPU step 1)
- Read [[memory-budget]] (GPU memory) and the phone item in [[open-issues]], then
  `FSkinFont` and its callers. `preloadAll` only avoids a pause at first use: `_get` already makes
  a missing size, so `patches/forge-web.patch` makes it return early when `forge.web` is set
  (desktop Forge keeps the old behaviour).
- Logged the sizes created in play (menu, overworld, town, duel; desktop and phone size):
  17 sizes, 8 MB. At the menu, live textures went from 296 MB in 129 to 173.5 MB in 73; GPU
  process 456 to 334 MB (desktop), 435 to 306 MB (phone size); renderer 669 to 659 and 667 to 663
  MB; the "Loading fonts" startup stage 3.2 to 0 s.
- Pages touched: [[memory-budget]], [[metrics]], [[open-issues]].

## [2026-10-05] fix | Duel-only sprite sheets read on first use (memory plan, GPU step 2)
- Read `FSkin`, `FSkinImageImpl`, `Assets` and every caller of the avatar, sleeve, crack, border
  and deck box maps before changing anything. They are all reached through five `FSkin` getters,
  so `patches/forge-web.patch` now makes each read its own sheet on first call (guarded by
  `forge.web`), adds `FSkin.getDefaultSleeve()` so adventure rewards load one sleeve sheet instead
  of two, and makes foil, watermark, set logo, planar conquest and border images read their sheet
  when first drawn. The map tilesets no longer build mipmaps (their filters are Nearest).
- Live textures: menu 173.5 to 75.4 MB (desktop), 73.8 MB at phone size; overworld 256.7 to
  169.1 MB; duel 257.2 to 224.6 MB. GPU process: menu 334 to 230 MB, overworld 478 to 377 MB,
  phone menu 306 to 217 MB. Renderer menu 659 to 633 MB. Skin sheet mipmaps left on, with the
  reason. Selftest 39 of 39. A duel, the deck editor (list and image view) and the tutorial were
  played and looked the same; pickers, foils and custom skins were not exercised.
- Pages touched: [[memory-budget]], [[metrics]], [[open-issues]].

## [2026-10-05] analysis | JS heap by owner and why lazy card loading is off (memory plan, card database)
- Took heap snapshots at the menu and on the overworld, wrote `web/tools/heap-owners.js` (dominator
  tree with `StaticData`, `CardDb` and `FModel` cut), and read `FModel`, `StaticData`,
  `CardStorageReader`, `RewardData` and `CardUtil`. The top retainers are `CardFace` (90.6 MB),
  `CardEdition` (26.2), `CardRules` (22.1), `PaperCard` (17.6) and shared strings (17.4); a `CardType`
  alone is 32 MB over 35,900 faces, mostly empty sets. Duplicate strings waste only 12.9 MB.
- Forced lazy loading on for `forge.web` in a throwaway build: menu JS heap 277.7 to 129.0 MB,
  renderer 629 to 473 MB (desktop), 632 to 499 MB (phone size), overworld 902 to 770 MB. The reason
  Forge turns it off on mobile still holds: the adventure reward code enumerates the whole database.
  The experiment was reverted; no game code was changed.
- Pages touched: [[memory-budget]], [[metrics]], [[open-issues]].

## [2026-10-05] ingest | Lazy CardType sets and specializedParts (patch, memory plan)
- `patches/forge-web.patch` now makes `CardType` create its supertype, subtype and excluded-subtype
  sets on first add, and `CardRules` create `specializedParts` only for Specialize cards. Menu JS
  heap 277.4 to 256.7 MB, overworld 304.4 to 283.5 MB, phone-size menu 276.8 to 256.4 MB. Mutators
  and getter callers were checked by search; a throwaway program and a browser run (new game, duel,
  deck editor) passed. Forge has no `forge-core` tests.
- Pages touched: [[memory-budget]], [[metrics]], [[open-issues]], [[src-forge-web-patch]].

## [2026-10-05] update | Boot smoke test in CI, webtest assertions, minified SelfTest
- Read `web/tools/webtest.py`, `scripts/webtest`, both workflows and `web/build.gradle.kts`.
  Added `expect`, `until-state`, `no-errors`, `--strict` and an error allowlist to webtest,
  `scripts/e2e-boot` and `web/tools/loader-watch.js`, boot smoke steps in `ci.yml` and `pages.yml`,
  and `TEAVM_OBFUSCATED` for SelfTest builds. The smoke test passes on the current build (about 30 s)
  and fails on a startup exception and on a late console error.
- Minified SelfTest: build fails in the `Long_fromNumber` patch, 35 of 39 checks pass on the unpatched
  `app.js`; the other failures are three save and deck checks.
- Pages touched: [[webtest-harness]], [[selftest]], [[build-pipeline]], [[open-issues]].

## [2026-10-05] update | Overworld memory: workers, minimap copy, chunk arrays, e2e-newgame
- Read `$S/overworld-memory.md` findings against the code, then measured each change at the overworld
  (seed 1, headless, software GL; desktop and phone size). WFC workers cost 133 MB (889 against 756 MB
  with `?wfc=local`); the pool now ends 5 s after the last reply. Big pixmap mirrors (16 MB and more) are
  dropped 5 s after their last read (backing stores 253.5 to 224.5 MB). WorldBackground arrays are per
  chunk (JS heap 283.2 to 277.5 MB). Overworld renderer 889 to 724 MB desktop, 753 to 715 MB phone size.
- Added `scripts/e2e-newgame` with the webtest steps `measure`, `assert-max`, `no-repeat-downloads` and
  `web/tools/glhook.js`; 74 s, not in CI. Final checks passed: startup, new game, town, duel, deck
  editor, map screen, save, load, second new game, `scripts/selftest` 39/39, `scripts/e2e-boot`.
- A Forge-side release hook failed (no `getNative()` on `Pixmap` at run time) and was dropped.
- Pages touched: [[memory-budget]], [[metrics]], [[open-issues]], [[webtest-harness]], [[world-generation]], [[src-forge-web-patch]].


## [2026-10-05] update | Minified release build, source map, fixes for the four minified SelfTest failures
- Read `web/build.gradle.kts`, `scripts/build-web`, `scripts/build-site`, `web/tools/latin1-js.py`,
  `TObjectInputStream`, both workflows and the TeaVM 0.15 sources (`TClass`, `ClassInfo`). Not read: NOTES.md.
- Root causes: the long-cast patch matched `Long_fromNumber` by name (now by body, with a regular
  expression), and `TObjectInputStream.allocate` read the class object's `$classInfo` field, which a
  minified build renames (now `ClassInfo.newInstance()`). The class-name theory was wrong; the save format
  is unchanged. SelfTest 39 of 39 minified and readable.
- Release builds are minified (`TEAVM_OBFUSCATED=true`) with `app.js.map` (`TEAVM_SOURCE_MAP=true`).
  `app.js` 76.1 to 20.5 MB, gzip 6.8 to 3.8 MB, JS source string at the menu 73.9 to 20.9 MB.
  `latin1-js.py` shifts map columns. `pages.yml` runs SelfTest, e2e-boot and e2e-newgame on it; CI stays readable.
- Checked by hand on the minified build: startup, new game, tutorial, town, deck editor, a duel to turn 2,
  loading saves made by the live readable build (slot 1 and autosave) and the reverse. `webtest` got
  `--save-state` and `--load-state`.
- Pages touched: [[build-pipeline]], [[selftest]], [[saves]], [[metrics]], [[memory-budget]], [[open-issues]],
  [[webtest-harness]], [[pick-up-work]], [[stay-on-js-backend]].

## [2026-10-05] fix | Loading bar ends full
- CI's boot smoke test failed on `9b23f4b` with the loading bar at less than 100%, although the
  game reached its title screen. Forge's last progress report can arrive after the loading screen
  has started to fade, and from then on reports don't move the bar, so whether it reached 100%
  depended on timing. `forgeLoadingDone` in `web/html/index.html` now sets the bar to 100% before
  the fade. The earlier passing runs were timing luck.

## [2026-10-05] fix | New games leaked the previous world
- Each new game kept the old `World.biomeImage` (31 MB of wasm pixmap heap) and `TileMapScene` never
  disposed its previous `TiledMap` (11.7 MB of textures per new game; also on load). Two patches in
  `patches/forge-web.patch`, without a web guard. Before: pixmap heap 7, 40, 71, 102 MB and textures
  124.0, 135.7, 147.3 MB over the menu and three games. After (overworld, after the tutorial): 40, 41,
  42 MB and 157.5, 157.9, 158.4 MB. Minimap and map screen checked after each game and after a load; towns,
  a dungeon and a duel checked after the fix. Added `scripts/e2e-cycle` (232 s). e2e-boot, e2e-newgame and
  SelfTest (39/39) pass.
- Pages touched: [[memory-budget]], [[bug-catalog]], [[open-issues]], [[webtest-harness]].
