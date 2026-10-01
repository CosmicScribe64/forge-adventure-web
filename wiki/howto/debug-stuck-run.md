---
type: howto
sources: [NOTES.md#debugging-a-stuck-run, NOTES.md#round-3]
updated: 2026-10-01
tags: [debugging, howto]
---

# How to debug a stuck run

`scripts/webtest` prints `[hb]` heartbeat lines with the time since the last console line,
and the renderer and GPU memory and CPU read from `/proc`. It stops with **exit code 4** on
`[tab crashed]`, and gives up after `--max-time`.

| Pattern | Likely cause |
|---|---|
| idle CPU and no console output | waiting on something: a network request, a lost wake-up, or a latch that was never counted down ([[green-threads]], [[single-processor]]) |
| rising memory and high CPU, then a crash | runaway allocation (for example the huge demo.gif texture or pixmap copies; see [[memory-budget]]) |
| high GPU-process CPU on a static menu | headless software GL; judge frame rate in a real browser |
| game stops in a visible browser | the tab or pane is hidden, and `requestAnimationFrame` is paused |

Tools:
- `stacks` step: samples stacks during a hang.
- `PW_DEBUG=pw:browser scripts/webtest ...` adds Chromium's own stderr (crash reasons, OOM).
- `docker logs --since 5m forge-web-serve` shows which files are being requested.
- `[monitor] entered a lock held by a suspended thread` in the console is informational
  (borrowed monitor), not a hang.
