---
type: concept
sources: [NOTES.md#layout, NOTES.md#reproduce, NOTES.md#round-8, web/build.gradle.kts, scripts]
updated: 2026-10-01
tags: [build, docker, teavm]
---

# Build pipeline

Everything builds in Docker (`docker/Dockerfile`, with JDK 17, Maven and Gradle). `scripts/dock <cmd>`
runs a command in the container with the project at `/work`. Maven and Gradle caches live in
the Docker volumes `shandalar-m2` and `shandalar-gradle`. `README.md` walks a new user through the
steps below.

## Steps

| Step | Script | Output | Notes |
|---|---|---|---|
| Sync Forge | `scripts/update-forge [ref]` | `forge/` at `FORGE_COMMIT`, with the patch applied | see [[update-forge]] |
| Build Forge jars | `scripts/build-forge-libs` | `web/libs/*.jar` and dependencies | Forge's POMs use unresolved `${revision}`, so Gradle takes a fileTree of jars |
| Game data | `scripts/build-webdata` | `web/webdata/` manifest, `cardsfolder.zip`, packs | see [[virtual-file-system]] |
| WFC worker | `scripts/build-worker` (`WORKER=true`) | `web/build/dist/wfc-worker.js` (212 KB) | copied next to index.html by build-web and selftest |
| Game | `scripts/build-web` | `web/build/dist/js/webapp`, log `web/build/teavm.log` | about 5 min; it rewrites index.html without `sed -i`, so it runs on macOS and Linux (2026-10-01) |
| Self-test | `scripts/selftest` (`SELFTEST=true`) | `web/build/dist/selftest` | about 35 s of checks plus about 5 min to build; see [[selftest]] |
| Serve | `scripts/serve-web` | port 8090 | also the desktop preview config `forge-web` |

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
- `outOfProcess = true`, `obfuscated = false`. There is also a `wasm {}` block for
  [[stay-on-js-backend|the wasm spike]] (`TARGET=wasm scripts/selftest`).

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
