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
