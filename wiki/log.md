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
