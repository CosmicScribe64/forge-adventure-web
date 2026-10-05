---
type: source
sources: [patches/forge-web.patch, FORGE_COMMIT]
updated: 2026-10-01
tags: [source, forge, patches]
---

# Source: patches/forge-web.patch

Every change to [[forge|Forge]] source, as one `git diff` against the commit pinned in
`FORGE_COMMIT` (`ddda332dc4f17a2cb8d45424dc8763117c927bc8` as of 2026-09-29).
`scripts/update-forge` applies it (see [[update-forge]]). To regenerate it, run
`git -C forge diff > patches/forge-web.patch`, after `git -C forge add -N` for any new files.

## Files touched (33, as of 2026-10-05), grouped by purpose

| Purpose | Files | Page |
|---|---|---|
| Web-only fixes | `FileUtil`, `CdnUuidCache`, `Localizer`, `CardArchetypeLDAGenerator` (skip formats with no LDA data) | [[virtual-file-system]], [[bug-catalog]] |
| Adventure start screen and web mode | `StartScene` (hides Classic and Exit when `forge.web` is set), `Forge.java` | [[startup-and-loading]] |
| World generation speed (upstreamable, output-identical) | `Model`, `BiomeStructure` (pluggable `chunkSolver`), `World` (encode map once) | [[world-generation]] |
| Duel-start lag | `CardUtil` (predicate order, min-date set), `ImageUtil` (memoized image-key lookup) | [[metrics]] |
| Saves | `AdventurePlayer` (colorIdentity saved as a byte, read as a string), `SaveLoadScene` (skips an unreadable save), `ScreenUtil` (thumbnails follow the screen size, R11) | [[saves]] |
| Screen shapes | `Scene`, `UIScene`, `HudScene`, `TileMapScene`, `RewardScene`, `GameHUD`, `GameStage`, `RewardActor`, new `ViewLayout`, `TransitionScreen` (VS names fit, R11) | [[screen-layout]] |
| Texture seams (R11) | `TemplateTmxMapLoader` (0.1-texel inset on tile regions) | [[screen-layout]] |
| Card database memory (upstreamable, behaviour-neutral) | `CardType`, `CardRules`, `CardChangedType`, `WordChangedType` (collections created on first add) | [[memory-budget]] |
| Startup fonts (R11) | `FSkinFont` (keeps the generated font on the web, no PNG round trip) | [[startup-and-loading]] |

> [!note] Superseded
> In Round 8, `Game.isGameOver()` was patched to a lock-free volatile read (NOTES.md). As of
> 2026-09-29 that change is **no longer in the patch**. `forge-game` isn't touched at all.
> The borrowed-monitor `TObject` shadow replaced it (see [[green-threads]]). SelfTest's
> contention check calls the real `game.isGameOver()` while another thread holds the lock.
> This meets the first half of PLAN Phase 3's exit criterion.

See also [[forge-patches-not-fork]].

Update 2026-10-05: `forge-gui-mobile/src/forge/adventure/stage/WorldBackground.java` sizes `chunks`, `chunksSprites` and `chunksSpritesBackground` by chunk count instead of tile count ([[memory-budget]]).
