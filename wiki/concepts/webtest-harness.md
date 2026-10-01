---
type: concept
sources: [NOTES.md#round-8, NOTES.md#round-11, NOTES.md#debugging-a-stuck-run, PLAN.md#phase-7, PLAN.md#next, web/tools/webtest.py, web/src/main/java/forgeweb/test/WebTest.java, scripts/play-start, scripts/play, scripts/api]
updated: 2026-10-01
tags: [testing, harness, playwright]
---

# webtest and the game harness

There are two layers for driving the real game.

## 1. `scripts/webtest` and `web/tools/webtest.py` (outside the game)
It runs Playwright with headless Chromium in the `mcr.microsoft.com/playwright/python` container
against `scripts/serve-web`. Steps: `wait`, `click`, `key`, `hold <key> <s>`, `type`, `shot`,
`api`, `js`, `reload`, `resize <w> <h>`, `heap` (memory accounting, see [[memory-budget]]),
`profile`, `stacks` (sampling for hangs), `exceptions [n] [file] [match] [..]`, `until`.
`--interactive <cmdfile>` keeps a session running.
Round 11 changes: `exceptions` matches the exception message as well as function names (for
example `TypeError`). The debugger is attached in interactive sessions too, so `exceptions` and
`stacks` work there. `--latency MS` and `--mbps N` apply Chrome's network emulation, sync XHR
included, to show what a hosted page feels like. `web/tools/find-seams.py` scans screenshots for one-pixel
bright lines (texture bleeding, [[screen-layout]]). `scripts/record-startup` records the files
a new game reads ([[virtual-file-system]]). The debugging aids (`[hb]` heartbeats, exit code
4 on a tab crash, `--max-time`) are covered in [[debug-stuck-run]].

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
