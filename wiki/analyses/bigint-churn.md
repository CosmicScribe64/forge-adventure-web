---
type: analysis
sources: [web/tools/bigint-hook.js, web/tools/bigint-decode.py, web/build.gradle.kts, web/src/main/java/forgeweb/shim/FibHash.java, web/src/main/java/com/badlogic/gdx/utils/IntMap.java, web/src/main/java/com/badlogic/gdx/utils/IntFloatMap.java, web/src/main/java/com/badlogic/gdx/utils/ObjectIntMap.java, web/src/unit/java/forgeweb/shim/FibHashTest.java, web/src/main/java/forgeweb/selftest/SelfTest.java, web/tools/webkit-memory.py]
updated: 2026-10-06
tags: [memory, phones, performance, teavm]
---

# BigInt churn in the render loop: where it came from and what was removed

TeaVM 0.15 stores every Java `long` as a JavaScript `BigInt`, and every `long` operation allocates one. [[webkit-memory]] found about 9000 of them a frame at the title screen. This page lists the call sites
and records the two changes that removed three quarters of them, with the measurements before and after (2026-10-06).

Counted by wrapping `BigInt.asIntN`, `BigInt.asUintN` and the `BigInt()` call in Chromium (desktop, 1280x720, minified build with its source map, seed 1) and sampling the call stack at every 20th call
(`web/tools/bigint-hook.js`, run through `scripts/webtest --init-script`, and `web/tools/bigint-decode.py`, which maps the stacks to Java source lines with the source map). Operations a frame, 10 s at the title screen and 10 s at the overworld, with the build before and after the changes below.
Software GL makes the overworld slow (4 frames a second with the counting hook before, 17 after), so the frame counts are not game speed; the per-frame counts are what matters.

| Source, before (share of the operations) | Title screen, per frame | Overworld, per frame |
|---|---|---|
| `Font` glyph arithmetic: `calculateSize` (called by `TextraLabel.draw` and `TypingLabel.act` every frame) and `drawGlyph`, each glyph a `long` | 53 percent, about 4800 | 54 percent, about 38,000 |
| libGDX `IntMap.place`, the glyph table lookup in `Font` (`mapping.get`) | 15 percent, about 1300 | 18 percent, about 12,700 |
| libGDX `IntFloatMap.place`, the kerning lookup in `Font`, `TextraLabel` and `TypingLabel` | 13 percent, about 1200 | 17 percent, about 11,700 |
| libGDX `ObjectIntMap.place`, `ShaderProgram` uniform locations (once per `setUniform`) | 6 percent, about 560 | 3 percent, about 2200 |
| `TextraLabel` and `TypingLabel` per-glyph loops, `GradientEffect` | 13 percent, about 1200 | 9 percent, about 6400 |
| All | 9040 (4690 `asIntN`, 1050 `asUintN`, 3300 `BigInt()`) | 70,700 |

Two changes, both without touching TextraTypist:

1. **Long literals as BigInt literals** (`web/build.gradle.kts`, the post-build patch of `app.js`, next to the double-to-long fix). TeaVM 0.15 writes every `long` literal as a call that allocates, `Long_fromInt(15)` or
   `Long_create(lo, hi)` (two to four BigInt allocations each time it runs), and every shift builds `BigInt(count)`. The patch rewrites the 4546 literal calls in the build as `15n` (made once, when the script is parsed)
   and takes the 64 shift counts from a table. Names are found by body, so it works for readable and minified builds. A third of all operations were these constants, mostly in `Font`.
2. **Integer hashing in three libGDX maps**: `IntMap`, `IntFloatMap` and `ObjectIntMap` are shadowed (`web/src/main/java/com/badlogic/gdx/utils`) and their `place` uses `forgeweb.shim.FibHash`, which computes the high word of the 64-bit
   Fibonacci multiplication with 32-bit arithmetic. The slot is the same as libGDX's for every key and table size (`FibHashTest` and a SelfTest check compare them against the `long` formula), so iteration order does not change. Other maps
   (`ObjectMap`, `ObjectSet`, `IntSet`, `IntIntMap`, `ObjectFloatMap`, `LongMap`) were not hot in either profile and were left alone. This belongs upstream: libGDX could use the integer form for targets with slow `long`.

| Measure (minified build, desktop Chromium unless noted) | Before | After |
|---|---|---|
| BigInt operations a frame at the title screen | 9040 | 2145 (1807 `asIntN`, 323 `asUintN`, 15 `BigInt()`) |
| BigInt operations a frame at the overworld | 70,700 | 16,500 |
| Renderer RSS at the title screen and at the overworld (after a collection) | 535 and 642 MB | 535 and 640 MB |
| Frames a second at the title screen and at the overworld (software GL, 10 s) | 59.8 and 32.8 | 60.0 and 34.3 |
| WebKit iPhone, title idle minimum, median, maximum over 60 s (two runs) | 1793, 1998, 2287 and 1748, 1998, 2246 MB | 1734, 1972, 2263 and 1605, 1950, 2318 MB |
| WebKit iPhone, overworld idle minimum, median, maximum over 60 s (two runs) | 2056, 2330, 2699 and 1969, 2257, 2555 MB | 1935, 2262, 2574 and 1753, 2078, 2534 MB |
| WebKit iPhone, `VmHWM` after the new game (two runs) | 2935 and 3048 MB | 2928 and 2743 MB |

**The memory effect is small.** Three quarters of the BigInt operations are gone, but the WebKit sawtooth is still 1.6 to 2.3 GB at the title screen: the title maximum did not move, the overworld idle median is lower by 70 to 180 MB,
and `VmHWM` is set while the overworld loads (RSS 2.7 to 3.0 GB at the "gameplay" stage in all four runs), not at idle. The runs vary by 150 to 300 MB, so only the overworld median is a hint of a gain. So BigInt was not the main
garbage that fills JavaScriptCore's headroom; the headroom follows the live heap (see the top of this page), and the card database remains the lever (option 1). What the change does buy is CPU: the counting hook ran the overworld
four times faster after it, and without the hook the overworld is 5 percent faster in software GL. On a phone that is less heat and a steadier frame rate.

Text drawing is unchanged: the title screen is identical pixel for pixel before and after (no pixel differs by more than 16 of 255), and the new-game screen, town, overworld HUD and a duel (at the coin toss) show the same fonts, colours and positions.
Those other screens differ only in what the game randomises (the character's name, race and colours, the opponent), which the seed does not fix.

## What is left, and what belongs upstream

- Remaining at the title screen (2145 a frame): `Font.calculateSize` and `Font.drawGlyph` (79 percent), the per-glyph loops of `TextraLabel` (14) and `TypingLabel` (4), `GradientEffect` (3). Every glyph is read from a `LongArray` (one BigInt)
  and split with `long` masks. Two ways to remove it, neither done because both mean a fork of TextraTypist's 5900-line `Font` (and `TextraLabel`, `TypingLabel`): keep the glyph as two `int`s, or skip `calculateSize`
  when a label's layout has not changed (a cache keyed by a change counter in `Layout`). A fork like that would remove most of the 2145 and 16,500.
- **Upstream, TextraTypist:** `TextraLabel.draw` calls `font.calculateSize(layout)` every frame (its own TODO says to limit it), and the same layout is measured again for every label. On a target with slow `long` this dominates.
- **Upstream, libGDX:** `IntMap.place` and its relatives can use the integer form in `FibHash` with the same result.
- **Upstream, TeaVM:** `long` literals should be constants (`15n`), shift counts should not allocate, and an option for `long` as a pair of `int`s would remove the whole class of problem. The patch in `web/build.gradle.kts` is the workaround.
- Tests, all passed on 2026-10-06 with the change: `scripts/unit-test` (31 JVM tests and 17 Python), SelfTest 40 of 40 readable and minified, and the release rehearsal `scripts/e2e-release site` (boot 26, 24 and 33 s, new game 58, 58 and 67 s on desktop, phone and WebKit iPhone, cycle 221 s on desktop). `FibHashTest` (JVM, every shift, 20,000 random keys each), the SelfTest check "long literals, shifts and libGDX hash slots are as in Java" (in the browser, readable and minified), and the build fails if TeaVM's long helpers
  change shape, so the patch cannot silently stop applying.

## See also
[[webkit-memory]], [[memory-budget]], [[build-pipeline]], [[selftest]], [[web-layer-mechanisms]], [[open-issues]]
