---
type: concept
sources: [scripts/e2e-boot, web/tools/loader-watch.js, NOTES.md#round-8, NOTES.md#round-11, NOTES.md#debugging-a-stuck-run, PLAN.md#phase-7, PLAN.md#next, web/tools/webtest.py, web/src/main/java/forgeweb/test/WebTest.java, scripts/play-start, scripts/play, scripts/api]
updated: 2026-10-05
tags: [testing, harness, playwright]
---

# webtest and the game harness

There are two layers for driving the real game.

## 1. `scripts/webtest` and `web/tools/webtest.py` (outside the game)
It runs Playwright with headless Chromium in the `mcr.microsoft.com/playwright/python` container
against `scripts/serve-web`. Steps: `wait`, `click`, `key`, `hold <key> <s>`, `type`, `shot`,
`api`, `js`, `reload`, `resize <w> <h>`, `heap` (memory accounting, see [[memory-budget]]),
`profile`, `stacks` (sampling for hangs), `exceptions [n] [file] [match] [..]`, `until`.
`--interactive <cmdfile>` keeps a session running. `--init-script <file>` runs a JavaScript file in the page before its own scripts (used for a WebGL memory hook, [[memory-budget]]). Steps are split at semicolons, so a `js` step can't contain one.
Round 11 changes: `exceptions` matches the exception message as well as function names (for
example `TypeError`). The debugger is attached in interactive sessions too, so `exceptions` and
`stacks` work there. `--latency MS` and `--mbps N` apply Chrome's network emulation, sync XHR
included, to show what a hosted page feels like. `web/tools/find-seams.py` scans screenshots for one-pixel
bright lines (texture bleeding, [[screen-layout]]). `scripts/record-startup` records the files
a new game reads ([[virtual-file-system]]). The debugging aids (`[hb]` heartbeats, exit code
4 on a tab crash, `--max-time`) are covered in [[debug-stuck-run]].

### Assertions and the boot smoke test (2026-10-05)
Three steps fail the run with a clear `FAIL:` line and exit code 1 (a plain `until` that times out
still exits with 2): `expect <js expression>` (truthy in the page), `until-state <condition> <seconds>`
(polls `api state` once a second; the condition is JavaScript over the parsed `state`, for example
`state.scene=='StartScene'`) and `no-errors`. Every console `error` line and uncaught page error is
collected. Lines matching `ALLOWED_ERRORS` in `web/tools/webtest.py` are ignored; the rest are listed
at the end of any run, and `--strict` turns them into exit code 1. Without `--strict` nothing
changes for `scripts/measure` and the other users.

`until-state` does not poll until the loading screen is gone. A harness command queued on the UI
thread while Forge is still starting killed the UI thread in a trial (`Cannot read properties of
null (reading '$player9')` in the monitor code), so a command during startup is unsafe; this is
not fixed.

`webtest.py` can keep browser storage between runs: `--save-state FILE` writes IndexedDB and
localStorage when the run ends (or on `quit` in an interactive session), and `--load-state FILE` starts
with it. That is how a save made by one build was loaded by another ([[saves]]).

`scripts/e2e-boot` is the boot smoke test that CI and `pages.yml` run. It starts `scripts/serve-web`,
loads `?test=1&seed=1`, waits for `state.scene == 'StartScene'`, waits 3 s, takes a screenshot
(`out/e2e-boot.png`), expects the loading bar to have reached 100% (`web/tools/loader-watch.js`, a
`MutationObserver` that keeps the highest width of `#fill`, because the bar is removed from the page
soon after the first frame) and expects no unexpected console errors. It takes about 30 s on top of
a build. Known harmless errors, as of 2026-10-05 (each has a cause in the code comment):
- `Failed to load: fallback_skin/title_bg_lq.png` and `transition.png`, a dummy texture is used
  until the real skin loads (see [[open-issues]]);
- `The card ... was not assigned to any set` (19 cards, same on desktop);
- `Upcoming set ... dated in the future` (Star Trek, dated 2026-11-13; goes away after that date);
- `[suppressed] java.io.IOException (while throwing java.io.IOException)` from `JdkCompat`, an
  optional file the game probes.
Checked by breaking the page on purpose: a startup exception gives a `[pageerror]` and a timeout in
`until-state` (exit 1), and a late `console.error` fails `no-errors` (exit 1).

### Memory and download assertions, `scripts/e2e-newgame` (2026-10-05)
Three more steps: `measure <name>` (garbage-collects, then records renderer RSS, JS heap, GPU
process RSS and, with `--init-script web/tools/glhook.js`, live WebGL texture memory), `assert-max
<name>.<rss|heap|tex|gpu> <limit>` and `no-repeat-downloads [<min MB>]` (fails if a URL with the same
Range header was requested twice and the decoded body is at least 1 MB; it uses Playwright's
`requestfinished` events, so cache hits count too). `web/tools/glhook.js` wraps the WebGL calls to
count texture, buffer and renderbuffer bytes.

`scripts/e2e-newgame` starts a seed-1 new game: title screen, `measure menu`, New Game, Start, wait
for "Generating world took", 10 s (the workers end after 5 s and the minimap copy after 5 s), `measure world`,
the limits, the repeat-download check and `no-errors`. It takes 74 s on top of a build and is not
in CI yet. The limits are in one commented block at the top of the script, with the date and the
conditions (desktop 1280x720, headless, software GL, seed 1): menu 763 MB RSS, 321 MB heap, 95 MB
textures, overworld 895, 349 and 155, each 1.25 times the measured value. 41 distinct requests, none repeated.
Checked by hand: `assert-max` fails above its limit, and a file fetched twice from a throwaway server
is reported. In the overworld the state scene is `TileMapScene` (the start cave), not `GameScene`.

## 2. `forgeweb.test.WebTest` (inside the game)
A harness compiled into the game, **only active with `?test`** (PLAN Phase 7). It is driven by
`api` commands and returns JSON. Commands include `state`, `moveto`, `goto` and `interact <POI>`, `stop`, `click`, `dismiss`,
`layout`, `duel`, `ok`, `cancel`, `play` and `select <card>`, `player`, and `attackall`.
`WebTestAccess` and `WebTestStageAccess` are in Forge packages so they can reach package-private
state.

## Session scripts
| Script | What it does |
|---|---|
| `scripts/play-start` | headless session to the tutorial map (new character, world from `SEED`, default 1). Log `out/play.log`, output `out/play.txt` |
| `scripts/play-overworld` | plays the tutorial to the overworld: mage, rewards, exit cave, intro dialogs. Screenshot `out/overworld.png` |
| `scripts/play '<steps>'` | send webtest steps to the running session |
| `scripts/api [-p] '<cmd>'` | send one harness command, print trimmed JSON |
| `scripts/play quit` | stops the session (**do this before building**, see [[build-pipeline]]) |

Milestones reached through the harness (as of 2026-09-29): the tutorial, a town and a shop
purchase, the overworld, and the first duel won (10 cards, 96 gold and an achievement).

## Caveats
- The browser pauses `requestAnimationFrame` in hidden tabs or panes, so the game, and any walking
  the harness drives, stops there. Use the headless session for measurements.
- Headless runs use SwiftShader (software WebGL), so the GPU process uses a lot of CPU and the
  frame rate isn't representative.

## Gaps (PLAN "Next", [[open-issues]])
- `dismiss` can press a choice before all choices have appeared. It should wait until the list
  of buttons stops changing.
- `state` needs mana cost and colours for hand cards, descriptions of Forge overlays (damage
  assignment, choosers, ordering), and non-basic lands.
- Turn the browser-session duel autopilot into a harness command: `autoplay [turns]`.
- Still needed: exit-only POIs, towns, shops and duel commands, and completing the "travel to
  town" quest.

## See also
[[selftest]] · [[play-session]]
