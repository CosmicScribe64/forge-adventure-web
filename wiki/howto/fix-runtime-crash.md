---
type: howto
sources: [NOTES.md#fast-checks, NOTES.md#round-4]
updated: 2026-10-01
tags: [debugging, howto]
---

# How to fix a runtime crash

1. **Get the real exception.** `scripts/webtest ... exceptions 3 out/x.txt <functionFilter> 150`
   captures thrown exceptions, optionally filtered by function name. Watch for:
   - `[suppressed] ...` lines (try-with-resources hid something, see [[bug-catalog]])
   - a JS `TypeError`, which is a null dereference Java couldn't catch ([[teavm-gotchas]])
   - "Suspension point reached from non-threading context" ([[green-threads]])
   - `ClassNotFoundException` or a missing constructor, which point to reflection
     ([[reflection-on-teavm]])
2. **Reproduce it in [[selftest]]** as a new check. SelfTest can use Forge and libGDX classes
   directly, and its checks run in about 35 s.
3. Fix it (choose the mechanism with [[add-missing-api]]) until the check passes.
4. Do the full game build (`scripts/build-web`, after stopping any play session), then play to
   the point of the crash (`scripts/play-start`, [[play-session]]).
5. Add the bug to [[bug-catalog]] if it's in someone else's code.

If it's a hang rather than a crash, see [[debug-stuck-run]].
