---
type: howto
sources: [NOTES.md#updating-forge, scripts/update-forge]
updated: 2026-10-01
tags: [forge, maintenance]
---

# How to update Forge

Forge is pinned to `FORGE_COMMIT`.

```bash
scripts/update-forge master          # or a tag/commit; rewrites FORGE_COMMIT, re-applies patches
scripts/build-forge-libs
scripts/build-web
```
(`scripts/update-forge` with no argument just re-syncs `forge/` to the pin.)

## What can break, and where to fix it
1. **A patch no longer applies**: `update-forge` stops. Redo the change by hand in `forge/`,
   then run `git -C forge diff > patches/forge-web.patch` (after `git -C forge add -N` for new
   files).
2. **New "was not found" APIs in the compile**: Forge uses something new. Follow
   [[add-missing-api]]. The build log's `at ...` lines show which Forge code reached it.
3. **A stubbed class changed its API** (`FServerManager`, `AssetsDownloader`,
   `ExceptionHandler`). The compile reports a missing method on the stub, so add it.
4. **Runtime behaviour changed**: only a run in the browser shows this. Run [[selftest]], then the
   game (`scripts/play-overworld`).

Also after an update:
- Run `scripts/wfc-golden` if world-generation files changed upstream ([[world-generation]]).
- New EventBus listener classes, reflective factories or save classes need registry entries
  ([[reflection-on-teavm]]).
- Saves from before the update may not load (no versioning yet, see [[saves]]).

Update often to keep each update small ([[forge-patches-not-fork]]).
