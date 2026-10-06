---
type: concept
sources: [NOTES.md#layout, NOTES.md#reproduce, NOTES.md#round-8, web/build.gradle.kts, scripts]
updated: 2026-10-06
tags: [build, docker, teavm]
---

# Build pipeline

Everything builds in Docker (`docker/Dockerfile`, with JDK 17, Maven and Gradle). `scripts/dock <cmd>`
runs a command in the container with the project at `/work`. On Linux the container's root owns
what it writes there, so `dock` hands those files back to the caller when the command exits
(2026-10-01). Without that, `build-web`'s host-side steps fail on a Linux machine or runner. Maven and Gradle caches live in
the Docker volumes `forge-adventure-web-m2` and `forge-adventure-web-gradle`. `README.md` walks a new user through the
steps below.

## Steps

| Step | Script | Output | Notes |
|---|---|---|---|
| Sync Forge | `scripts/update-forge [ref]` | `forge/` at `FORGE_COMMIT`, with the patch applied | see [[update-forge]] |
| Build Forge jars | `scripts/build-forge-libs` | `web/libs/*.jar` and dependencies | Forge's POMs use unresolved `${revision}`, so Gradle takes a fileTree of jars |
| Game data | `scripts/build-webdata` | `web/webdata/` manifest, `cardsfolder.zip`, packs | see [[virtual-file-system]] |
| WFC worker | `scripts/build-worker` (`WORKER=true`) | `web/build/dist/wfc-worker.js` (212 KB) | copied next to index.html by build-web and selftest |
| Game | `scripts/build-web` | `web/build/dist/js/webapp`, log `web/build/teavm.log` | about 5 min; it rewrites index.html without `sed -i`, so it runs on macOS and Linux (2026-10-01); it escapes characters above U+00FF in `app.js` with `web/tools/latin1-js.py` so Chrome stores the source in one byte per character (2026-10-05, [[memory-budget]]) |
| Self-test | `scripts/selftest` (`SELFTEST=true`) | `web/build/dist/selftest` | about 35 s of checks plus about 5 min to build; see [[selftest]] |
| Serve | `scripts/serve-web` | port 8090 | also the desktop preview config `forge-web` |
| Static site | `scripts/build-site` | `web/build/site` (245 MB) | the game (app.js only as `app.js.gz`), `forge-data/`, and only the `res/` files the manifest fetches one by one; works from a subpath |
| CI | `.github/workflows/ci.yml` | pass or fail on each push and pull request | wiki lint, patch, Forge build, game data, SelfTest, game compile and the boot smoke test on desktop and in Chromium's phone mode (`scripts/e2e-boot`, about 1 min together, see [[e2e-tests]]); on failure the log and screenshot are uploaded |
| Publish | `.github/workflows/pages.yml` | GitHub Pages | runs every step above, including SelfTest against the minified build, then assembles the site and runs the end-to-end scenarios on it (boot and new game on desktop, in Chromium's phone mode and in WebKit with an iPhone descriptor, and the leak cycle on desktop; [[e2e-tests]]) before deploying, and a `verify-live` job loads the deployed site in the same three modes, on GitHub's runners for each published release, or by hand. The repository's `github-pages` environment must allow the `main` branch and `v*` tags, or a release's deploy job is rejected; see [[open-issues]] |

## TeaVM settings (environment variables read by `build.gradle.kts`)
- `TEAVM_MEMORY_MB` (default 5120). Forge is about 400k lines, and TeaVM needs about 5 GB to analyse it.
- `TEAVM_OPT` (default `BALANCED`). `NONE` compiles faster but runs far too slowly to load
  34k card scripts.
- `TEAVM_FAST_ANALYSIS=true`: less precise analysis. It reaches far more code (about 311 missing
  APIs, including Netty through online chat), so it is unusable until more is stubbed. The
  gdx-teavm dev server (`gdx_teavm_web_js_run`) seems to use it, so it is a dead end for now.
- `REACH=1` and `REACH_DETAIL=1` write `out/reach-game.txt` and `out/reach-game-reflect.txt`
  (see [[classic-code-pruning]], [[reflection-on-teavm]]). This is slow, taking 10-40 min.
- `REFLECTION_DEBUG=true` turns on gdx-teavm's reflection debugging.
- `TEAVM_OBFUSCATED=true`: minified names for the JS build, which `scripts/build-web` and `scripts/selftest` pass on. The release build (`pages.yml`) sets it. Local builds, CI and SelfTest stay readable. The wasm target and the world-generation worker (`scripts/build-worker`, 212 KB) are never minified. See "Minified build" below.
- `TEAVM_SOURCE_MAP=true`: TeaVM writes `app.js.map` (`sourceMap = true`, `sourceFilePolicy = DO_NOTHING`, so the map names Java files but does not embed or copy them). `pages.yml` sets it together with `TEAVM_OBFUSCATED`.
- `outOfProcess = true` for the game. There is also a `wasm {}` block for
  [[stay-on-js-backend|the wasm spike]] (`TARGET=wasm scripts/selftest`).

## Minified build (2026-10-05)
The release is minified, because it cuts `app.js` from 76.1 MB to 20.5 MB (gzip 6.8 to 3.8 MB) and
the JS source string Chrome holds at the menu from 73.9 MB to 20.9 MB ([[metrics]], [[memory-budget]]).
Things that make it work:
- The long-cast patch in `web/build.gradle.kts` finds `Long_fromNumber` by its body with a regular
  expression (minified output has another name and no spaces) and writes the fix under the name it found.
  The same `doLast` rewrites `Long_fromInt(15)` and `Long_create(lo, hi)` with literal arguments as BigInt literals (`15n`, 4546 in the game build)
  and builds the 64 shift counts once, because TeaVM allocates a BigInt each time it runs them ([[bigint-churn]]). It fails the build if a helper is not found or the table anchor is not unique.
- Code must not read TeaVM's generated JavaScript fields by name. `TObjectInputStream.allocate` did
  (`cls.$classInfo`); it now calls TeaVM's `ClassInfo.newInstance()` ([[saves]]). `$rt_nativeThread` and
  the other `$rt_` runtime functions keep their names.
- `scripts/build-web` removes TeaVM's `sourceMappingURL` comment from `app.js`, because the page runs the
  code from a blob: URL where a relative URL would not resolve. `web/html/index.html` appends an absolute
  `//# sourceMappingURL=` (with the map's content hash) to that blob when `app.js.map` exists. DevTools
  fetches the map only when open. `scripts/build-site` copies `app.js.map` into the site with the rest.
- `web/tools/latin1-js.py` takes the map as a second argument and moves each generated column by the
  extra length of the escapes before it (the lines of a minified file are long). On the release build,
  0 of 899,219 mapped columns lie past the end of their line.
- The map is 4.3 MB (1.3 MB gzipped), with 2,925 source files named.
Build time and peak memory were not different in a way that mattered (7m11s for the game, 2026-10-05).

## Building from a clean checkout (2026-10-01)
Before the first commit, a copy of exactly the tracked files was built from scratch: a fresh
clone of Forge from GitHub, `update-forge`, `build-forge-libs` (51 s, the same 85 jars),
`build-webdata` (identical manifest and packs; `cardsfolder.zip` differs only in the timestamps
stored in the zip), and `build-web`. The build succeeded with no missing APIs and a 5.9 GB peak,
in 16 min because another project's containers were using the CPUs. Served and loaded headless,
it reached the title screen in 26 s with no console errors. The first attempt found that the
`.gitignore` was leaving source files out ([[bug-catalog]]).

## Memory ceiling
The Docker VM has 7.7 GB. The game build peaks at **6.7 GB** container memory. Consequences:
- Stop the play session before building (`scripts/play quit`). Chromium and TeaVM's heap
  together exceed Docker's memory, and TeaVM dies with an RMI EOF.
- Broad reflection patterns push TeaVM over the limit ([[reflection-budget]]).
- `web/java_pid*.hprof` files are heap dumps from such OOMs. They can be several GB each, are
  gitignored (`*.hprof`, 2026-10-01), and are safe to delete.

## Output size
app.js is 76 MB raw and 7.1 MB gzipped (baseline). It was about 80 MB unoptimised at the first
compile.
GitHub Pages needs it under 100 MB. See [[metrics]].
