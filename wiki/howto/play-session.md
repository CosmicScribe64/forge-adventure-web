---
type: howto
sources: [scripts/play-start, scripts/play, scripts/api, scripts/play-overworld, NOTES.md#round-8]
updated: 2026-10-01
tags: [testing, howto]
---

# How to run a play session

```bash
scripts/serve-web                 # if not already running (port 8090)
scripts/play-start                # headless session at the tutorial map, SEED=1 by default
scripts/api -p 'state'            # send a harness command, print the JSON result
scripts/api 'goto Deep Silt'      # walk to a point of interest
scripts/play 'hold ArrowRight 0.5; shot out/a.png'   # raw webtest steps
scripts/play quit                 # always do this before building
```

- `scripts/play-overworld` gets you straight past the tutorial to the overworld.
- The harness is only active with `?test`. The commands are listed in [[webtest-harness]].
- To watch the game, open the served page in a real browser. A hidden tab pauses the game.
- For comparable numbers, use `scripts/measure` instead ([[metrics]]).
