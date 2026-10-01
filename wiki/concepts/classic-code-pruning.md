---
type: concept
sources: [PLAN.md#phase-2, NOTES.md#round-9, web/src/main/java/forgeweb/teavm/Unsupported.java, web/src/main/java/forgeweb/teavm/ReachReport.java]
updated: 2026-10-01
tags: [app-size, teavm, phase-2]
---

# Classic code pruning

TeaVM keeps everything reachable from the entry point. Forge's Adventure shares code with
Classic mode (deck editors, quest, gauntlet, planar conquest, online, downloaders, Classic
screens), so much of Classic ends up in app.js even though Adventure never runs it. The PLAN
Phase 2 goal is to compile only what Adventure uses, so that app.js and the build heap are
measurably smaller.

## Finding what pulls code in (`forgeweb.teavm.ReachReport`)
Build with `REACH=1` to get `out/reach-game.txt`, with the code size of each package and the
constructor and static calls that pull it in. `REACH_DETAIL=1` adds more detail. `ReflectAudit` writes
the matching `-reflect.txt` ([[reflection-on-teavm]]).

## Cutting it out (`forgeweb.teavm.Unsupported`)
This TeaVM class transformer replaces the bodies of named methods with one of two things:
- **throw** `UnsupportedOperationException("... is not available in the web build (Adventure
  only)")`, so a call that does happen fails loudly instead of half-working:
  - XStream serializers for quest, gauntlet and tournament saves (`QuestDataIO.getSerializer`,
    `GauntletIO`, `TournamentIO`, `QuestBazaarManager.load`, `QuestPetStorage.<init>`,
    `QuestAssets.setItemLevel`). XStream's `Class.newInstance` made TeaVM keep the no-arg
    constructor of nearly every class.
  - Classic home menus that build screens by reflection (`NewGameMenu$NewGameScreen`,
    `LoadGameMenu$LoadGameScreen`, `OnlineMenu$OnlineScreen` `initializeScreen`).
- **no-op**: `HomeScreen.openMenu`, `Forge.maybePromptForBulkCdnSync` (the bulk card-image
  download prompt).

> [!note]
> PLAN.md describes Phase 2 stubs as going "through ForgeWebSubstitutionPolicy". The actual
> mechanism is this transformer. The policy is for whole-class stand-ins
> ([[web-layer-mechanisms]]).

## Status
Phase 2 has started (as of 2026-09-29). The effect on app.js size hasn't been re-measured in NOTES yet
(baseline 76 MB raw). See [[plan-phases]], [[metrics]].
