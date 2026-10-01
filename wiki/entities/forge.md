---
type: entity
sources: [NOTES.md, FORGE_COMMIT, patches/forge-web.patch]
updated: 2026-10-01
tags: [forge, upstream]
---

# Forge

Card-Forge/forge is an open-source Magic: The Gathering rules engine and game, with about 400k
lines of Java. **Adventure mode** is a Shandalar-style overworld RPG in `forge-gui-mobile`, built on
libGDX 1.14.2. The project keeps a shallow clone in `forge/` (gitignored), pinned to
`FORGE_COMMIT`, with `patches/forge-web.patch` applied.

## Parts that matter here
- `Forge.getApp(...)`, a clean entry point. The web launcher is about 15 lines.
- `forge-gui-mobile/src/forge/adventure/...`: scenes, stages, world generation (WFC),
  `WorldSave`.
- `forge-game`: the rules engine, which builds triggers, replacements, keywords and effects by
  reflection ([[reflection-on-teavm]]).
- `forge-gui/res`: 466 MB of data, including 33,980 card scripts ([[virtual-file-system]]).
- Forge uses Guava `EventBus` for game events, `CountDownLatch`, `CompletableFuture` and
  `BlockingDeque` to sync the game with the UI, and Java serialization for saves.

## Relationship
Changes go through the patch and aim to be upstreamable ([[forge-patches-not-fork]]). Upstream
candidates include the WFC speedups, the `colorIdentity` save bug, the duel-start fixes and
ViewLayout. [[update-forge]] describes how to move the pin.
