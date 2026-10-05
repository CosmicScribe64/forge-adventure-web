---
type: howto
sources: [README.md, PLAN.md, .github/workflows/ci.yml, .github/workflows/pages.yml, web/tools]
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
  ([[webtest-harness]], [[play-session]]). `webtest --width 390 --height 844 --scale 3 --mobile`
  emulates a phone.
- Measure before and after a change: `scripts/measure`, or webtest's `heap` step
  ([[metrics]], [[memory-budget]]).

## Checks and releases
- **CI** (`.github/workflows/ci.yml`) runs on every push to `main` and every pull request: the
  wiki lint, `update-forge`, the Forge build, `build-webdata`, `scripts/selftest` and
  `build-web`. Keep it green.
- **Releases** publish the game. `gh release create vX.Y.Z --generate-notes` triggers
  `.github/workflows/pages.yml`, which builds everything from scratch, minified and with a source
  map, runs SelfTest, `scripts/e2e-boot` and `scripts/e2e-newgame` against the minified build, and
  deploys it to https://cosmicscribe64.github.io/forge-adventure-web/ (the run is about 8
  minutes longer than before: a SelfTest compile and the two scenarios). The repository's
  `github-pages` environment must allow the `main` branch and `v*` tags, or the deploy is
  rejected ([[build-pipeline]]).
- After a release, load the live site headless and check it reaches the title screen with no
  console errors.

## Test helpers
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
