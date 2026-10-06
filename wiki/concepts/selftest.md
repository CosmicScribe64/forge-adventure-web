---
type: concept
sources: [NOTES.md#fast-checks, NOTES.md#round-11, web/src/main/java/forgeweb/selftest/SelfTest.java, scripts/selftest]
updated: 2026-10-06
tags: [testing]
---

# SelfTest

A full game build and run takes about 8 minutes. `scripts/selftest` instead builds
`forgeweb.selftest.SelfTest`, which uses the same shims, redirects, virtual file system and
reflection configuration, but not the game. The script serves it with the game data on port 8092
and runs it in headless Chromium. The checks take about 35 s in the page (R11, 36 checks; 39 on 2026-10-05, with the idle-trim check), and a
build adds about 5 min. The exit code is 0 only if every check passes. The log is
`out/selftest-js.log`. `out/selftest.log` is an older name, so don't read results from it. `SKIP_BUILD=1` reruns without building, and `TARGET=wasm` builds the wasm
target ([[stay-on-js-backend]]).

## The rule
**Every check corresponds to a real bug found in the game.** To fix a new runtime crash,
reproduce it as a check (it can use Forge and libGDX classes directly), fix it until the check
passes, then do the full build. See [[fix-runtime-crash]].

## Checks (as of Round 11, 36)
Round 11 added three: the save hooks are callable (WorldSaveHeader, PaperCard, Deck), a save
header survives a round trip through Deflater (the Load list), and a saved booster deck's cards
keep their rules (`PaperCard.readObject`). SelfTest's page has no prefetch, so it also exercises `Http`'s
gzip fallback for `forge-data/` files ([[virtual-file-system]]).

Grouped by concept:
- **JDK and TeaVM behaviour**: try-with-resources keeps the primary exception, inflate with tiny
  buffers, native `DeflaterOutputStream` round trip, `availableProcessors` is 1, UUID on plain
  http, `getStackTrace` has frames, deck-list regex. ([[bug-catalog]])
- **Threads**: an executor with a CountDownLatch, a failing task through `Future.get`, sleep in a browser
  callback, UI-thread join on CompletableFuture, BlockingDeque, **small synchronized method
  while another thread is suspended holding the lock** (using the real `Game.isGameOver`),
  **every thread queued on a held monitor eventually enters it** (four contenders, an owner
  that sleeps 150 ms, a 3 s timeout; added 2026-10-04, 1 of 4 entered before the `TObject` fix).
  ([[green-threads]])
- **Files**: every card script in `cardsfolder.zip`, packs read whole in one download,
  `File.list(filter)`, `./res` resolution, starter deck file sections, demo.gif absent.
  ([[virtual-file-system]])
- **Reflection**: adventure `config.json` through Json, `ui_skin.json` classes, textratypist
  effects, rules-engine construction (all triggers, replacements, keywords, effects, AI),
  gdx-controllers stub. ([[reflection-on-teavm]])
- **Saves**: value round-trip, save hooks, header round-trip, JDK-format rejected, EnumMap,
  deck round-trip. ([[saves]])
- **Rendering**: `Pixmap.drawPixmap` stays cheap on a big pixmap.
- **Long arithmetic**: literals, shifts and `FibHash` slots against the `long` formula, plus lookups in the shadowed libGDX maps (the build patches `long` literals into BigInt literals, [[bigint-churn]]).
- **End to end**: loads the real card DB (as FModel does) and plays **two AI-vs-AI matches**
  with Adventure starter decks, one with plain rules and one with `GameType.Adventure` and
  `forVariants`. This takes about 2 min on its own.
- **Benchmark**: WFC world generation work, as `World.generateNew` does it ([[world-generation]]).

## Minified build (2026-10-05)
`TEAVM_OBFUSCATED=true scripts/selftest` compiles SelfTest with TeaVM `obfuscated = true`. All 39 checks
pass minified and readable (2026-10-05; 40 of 40 on 2026-10-06 after the check for long literals and hash slots, readable and minified; the minified run takes 68 s in the page after a 5 min build).
The first minified run (before the fixes) passed 35 and failed four:
- The build failed at the end, because the `doLast` in `web/build.gradle.kts` matched `Long_fromNumber`
  by name and the name was gone. With the unpatched `app.js`, the (long) cast check failed too
  (`The number NaN cannot be converted to a BigInt`). The patch now matches by body, in both modes.
- The adventure save round trip, the save header round trip and the saved deck read back failed in
  `TObjectInputStream.allocate`, which created objects through the class object's JavaScript field
  `$classInfo`. A minified build gives that field another name, so `allocate` got null: `InvalidClassException:
  cannot allocate` for the header, and a `TypeError` on a null object for the other two. It now uses
  TeaVM's `ClassInfo.newInstance()`. The suspicion that class names in the save format were the cause was
  wrong: `Class.getName` and `Class.forName` return the real names minified, so the format is unchanged
  ([[saves]]).
- Reflection (Json, ui_skin classes, textratypist effects, rules engine) passed all along, because the
  `reflection(...)` patterns keep those names.
`pages.yml` runs this check before a release. `ci.yml` does not, because the extra compile costs 5 min.

## See also
[[webtest-harness]] (the in-game counterpart) · [[unit-tests]] (the JVM tests that need no browser)
