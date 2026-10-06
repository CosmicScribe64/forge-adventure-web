---
type: howto
sources: [scripts/e2e-release, scripts/serve-site, README.md, PLAN.md, .github/workflows/ci.yml, .github/workflows/pages.yml, web/tools]
updated: 2026-10-05
tags: [onboarding, process]
---

# How to pick up the work

Everything needed to continue the project is in this repository. This page says what to read
first, how the project is run, and the conventions it follows.

## Read first
1. `README.md`, for what the project is and how to build it.
2. [[overview]], for how the pieces fit and where things stand.
3. [[open-issues]], the to-do list. Phone memory is the top priority; its ordered plan is in
   [[memory-budget]].
4. The last entries of [[log]], which end with the current state and the next steps.
5. [[SCHEMA]] before editing the wiki. `NOTES.md` is the lab notebook and `PLAN.md` the plan.

## Day to day
- Build and run locally with the steps in `README.md`. `scripts/selftest` is the quick check
  ([[selftest]]); a full game build takes about 5 minutes.
- Reproduce a runtime bug as a SelfTest check before fixing it ([[fix-runtime-crash]]).
- Drive the game headless with `scripts/webtest`, or play a session with `scripts/play-start`
  ([[webtest-harness]], [[play-session]]). `webtest --phone` emulates a phone in Chromium and
  `webtest --browser webkit --device "iPhone 13"` runs WebKit with an iPhone's descriptor.
- Measure before and after a change: `scripts/measure`, or webtest's `heap` step
  ([[metrics]], [[memory-budget]]).

## Checks and releases
- **CI** (`.github/workflows/ci.yml`) runs on every push to `main` and every pull request: the
  wiki lint, the unit tests, `update-forge`, the Forge build, `build-webdata`, `scripts/selftest`,
  `build-web`, then `scripts/e2e-boot` on desktop and `scripts/e2e-boot phone` (Chromium emulating a
  phone, about 30 s more). Keep it green.
- **Releases** publish the game. `gh release create vX.Y.Z --generate-notes` triggers
  `.github/workflows/pages.yml`, which builds everything from scratch, minified and with a source
  map, runs SelfTest against the minified build, assembles the site (`scripts/build-site`) and runs
  `scripts/e2e-release site` on it: e2e-boot and e2e-newgame on desktop, in Chromium's phone mode and in
  WebKit with the iPhone 13 descriptor, plus e2e-cycle on desktop ([[e2e-tests]]). The site is served by
  `scripts/serve-site`, from the subpath `/forge-adventure-web/` and with compression, as GitHub Pages serves
  it. Then it deploys to https://cosmicscribe64.github.io/forge-adventure-web/. A last job, `verify-live`,
  loads the live site in the same three modes (`scripts/e2e-release live`, e2e-boot) and turns the run red if a
  release is broken, which can't undo the deployment. The checks add about 9 minutes to a release
  run and the live check about 2. The repository's `github-pages` environment must allow the `main` branch
  and `v*` tags, or the deploy is rejected ([[build-pipeline]]).
- **Devices** (`.github/workflows/devices.yml`): the game in real Mobile Safari (iOS Simulator, macOS runner) and real Chrome
  (Android emulator, Linux runner). Run `gh workflow run devices.yml --ref main`, wait 15 to 30 minutes, download the artifacts
  (`gh run download <id> -D out/devices`) and look at `summary.json` and the screenshots ([[e2e-tests]]). `pages.yml` calls it beside the
  deploy (not a gate) and after it. Look here when a phone-only report comes in before asking the owner to try it on an iPhone.
- **Rehearse a release locally** with the same commands, after
  `TEAVM_OBFUSCATED=true TEAVM_SOURCE_MAP=true scripts/build-web` and `scripts/build-site`:
  `scripts/e2e-release site` (about 9 minutes; `ONLY="boot-iphone"` runs one), and
  `TEAVM_OBFUSCATED=true scripts/selftest`. `scripts/e2e-release live` checks the deployed site.
  A single run is `scripts/e2e-boot [desktop|phone|iphone]`, and `URL=<site root>` points any scenario at a
  site that is already running.

## Test helpers
- `scripts/serve-site` serves `web/build/site` under `/forge-adventure-web/` with `serve-compressed.py`, on port 8097.
- `serve-compressed.py` is a static server that compresses like GitHub Pages, and also sends
  `.gz` files with `Content-Encoding: gzip`. Use it to test the loader against hosts that
  compress, for example:
  `docker run --rm -p 8097:8080 -v "$PWD/web/build/site":/srv/forge-adventure-web:ro -v "$PWD/web/tools/serve-compressed.py":/serve.py:ro -w /srv forge-adventure-web-build python3 /serve.py 8080`
- `loader-check.js` records every download count the loading screen shows. Pass it to webtest
  as `js $(tr -d '\n' < web/tools/loader-check.js)`, then read `window.__p`.
- `scripts/build-site` makes the static site in `web/build/site`, the same as the Pages
  workflow publishes.
- `scryfall-requests.js` lists the Scryfall requests made so far, with their timing, for checking
  the rate limiter ([[scryfall]]).

## Conventions
- **The project is Forge Adventure Web** (`forge-adventure-web`). "Shandalar" is the name of
  Forge's Adventure world, the Magic plane, so use it only for the world, never for the project.
- **Fix the root cause.** A stopgap is a labelled stepping stone toward the real fix
  (`PLAN.md`). Change Forge only through `patches/forge-web.patch`, in a way that could go
  upstream ([[forge-patches-not-fork]]).
- **Write plainly.** Docs, comments and the wiki use whole sentences and plain words: no em
  dashes, no arrows or "+" and "/" shorthand in prose, colons only before lists or examples,
  sentence-case headings, and numbers with their conditions. The `deslop` skill from
  https://github.com/CosmicScribe64/dotfiles (`.copilot/skills/deslop`) has the full rules, and
  its `scripts/find_tells.py` flags likely problems for review.
- **Be honest about the state.** The README says what works and what doesn't; keep it true as
  things change.
- **Commits** go out under the author's GitHub noreply address, never a personal email.

## See also
[[build-pipeline]] · [[add-missing-api]] · [[update-forge]] · [[debug-stuck-run]]
