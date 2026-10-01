---
type: decision
status: active
sources: [NOTES.md#updating-forge, NOTES.md#how-the-web-layer-plugs-in, scripts/update-forge]
updated: 2026-10-01
tags: [forge, maintenance]
---

# Decision: patch Forge, don't fork it; don't fork TeaVM

## Choice
- [[forge|Forge]] stays a pinned upstream checkout plus **one patch file**
  (`patches/forge-web.patch`). Prefer the web layer (shims, redirects, stubs) over patching,
  and patch only for real behaviour, performance or layout changes, written to be upstreamable.
- [[teavm|TeaVM]] and [[gdx-teavm]] are consumed as released. Fixes go in classlib or emu
  shadows inside this project.

## Why
Updating Forge often keeps each update small ([[update-forge]]). Upstreaming the patch shrinks
it over time. Classlib shadows are small, targeted forks that are easy to re-check on upgrade.

## Costs
- Shadowed classes (especially `TObject`) must be re-synced on TeaVM upgrades.
- Output-affecting patches need proof of equivalence (`scripts/wfc-golden` for world generation).

## See also
[[src-forge-web-patch]] · [[web-layer-mechanisms]] · [[bug-catalog]] (upstream candidates)
