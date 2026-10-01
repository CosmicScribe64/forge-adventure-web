---
type: decision
status: active
sources: [NOTES.md#round-8, web/src/main/java/forgeweb/compat/UiThread.java]
updated: 2026-10-01
tags: [threads]
---

# Decision: all game code on one green thread

**Round 8 (2026-09-28/29).**

## Context
Forge blocks its UI thread (joins futures during world generation, sleeps in click handlers).
Browser callbacks can't block on [[teavm|TeaVM]] ([[green-threads]]).

## Options
1. Patch every blocking site in Forge. There are many sites, and the patch would drift from
   upstream.
2. Run each browser callback on its own green thread. This breaks ordering and Forge's
   assumption of a single EDT.
3. **One long-lived green thread ("Forge UI") runs everything in delivery order, and callbacks
   only enqueue.**

## Choice
Option 3 (`forgeweb.compat.UiThread`). The trade-off is that while a task blocks, frames are
dropped (at most one queued), so long work must yield or move off-thread (WFC went to Web
Workers, and `Progress` yields during loading).

## See also
[[single-processor]]
