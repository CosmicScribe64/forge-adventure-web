---
type: analysis
sources: [web/tools/webkit-memory.py, web/build.gradle.kts, web/src/main/java/forgeweb/shim/FibHash.java, web/src/main/java/com/badlogic/gdx/utils/IntMap.java, web/src/main/java/forgeweb/fs/Http.java, web/src/main/java/forgeweb/fs/UserDataStore.java, web/tools/test_jsbody_names.py, web/tools/webtest.py, scripts/e2e-newgame]
updated: 2026-10-06
tags: [memory, phones, webkit]
---

# Where the WebKit web process memory goes

Playwright's Linux WebKit needs 1.9 GB at the title screen and 2.3 GB at the overworld for the same build that needs 0.57 and
0.68 GB in Chromium's renderer. This page records how that was taken apart on 2026-10-06, what is known, and what is not.
The short answer: the live data is about the same size in both engines (about 0.55 GB), and JavaScriptCore lets the
garbage-collected heap grow to about three times that before it collects. The excess is allocation headroom, filled by
two kinds of churn that the game produces: BigInt arithmetic for every `long`, and the card loader's strings and arrays. (Removing three quarters of the BigInt operations later did not lower the idle maximum measurably, see "BigInt work removed" below.)

All numbers: Playwright 1.55 WebKit 26.0 (WPE port), iPhone 13 profile, headless, in the Playwright container (4 cores, 15 GB
RAM, no memory limit), software GL inside the web process, minified build served from the assembled site, seed 1. RSS is the
`WPEWebProcess` from `/proc`. A single reading varies by up to 300 MB because of the sawtooth below, so use `VmHWM` and
the minimum, median and maximum of an idle minute. The tool is `web/tools/webkit-memory.py`.

## The pieces, in order of size

| Piece | Size | How it was found |
|---|---|---|
| Empty page with one WebGL canvas | 371 MB | blank page: the web process, including software GL |
| Fetching and decompressing `app.js` (20.5 MB) | +33 to +48 MB | the same page, keeping the bytes |
| Parsing `app.js` without running it (wrapped in `if(0){}`) | +58 MB on top of the fetch | so the source, bytecode and the "21 MB string" are not the problem |
| Live JavaScriptCore heap after a full collection | 550 to 600 MB | `JSC_logGC=basic` (with `DEBUG=pw:browser`) |
| Heap size before that collection at the title screen | up to 1.68 GB | the same log: the allocation allowed between collections grew with the live heap: 271, 373, 462 and 562 MB as the cards loaded |
| Executable memory (JIT code) | 33 MB | `smaps`, anonymous `rwx` mappings |
| Mapped files (WebKit, LLVM, Mesa, fonts) | 180 MB | `smaps` |

The heap log shows the mechanism. A full collection leaves 550 to 600 MB, and the collector allows an allocation of about the
same amount before the next one (it scales the allowance with the live size and, in its source, with the RAM size it sees; see the `forceRAMSize` tests below). Collections of the young part free only part of it, so the heap climbs through several of them before a full
collection returns it to the live size. Freed pages are kept by the allocator for a while, so RSS follows the top of the
heap, not the live size. The excess over Chromium (about 1.1 GB) is therefore headroom, not a leak. At 600 MB of live data
the headroom is 600 MB or more, and the blank-page base of 0.37 GB comes on top.

## Startup, stage by stage

RSS when each loading stage began (one run, `web/tools/webkit-memory.py`): page 296 MB, app.js parse 586, gdx preload 807,
Forge create 829, "Loading game resources" 1641 (before the fix below; 826 to 859 after), start of card loading 1294 (after a
collection), end of card loading 1875, title screen 1924.

- **The 800 MB step before "Loading game resources" was a bug of ours** (fixed 2026-10-06, see below). One 683 MB anonymous
  mapping appeared in about a second. Its content was a pool of tiny strings holding decimal numbers ("91", "10", "123"), the bytes of a
  JSON file turned into text one number at a time.
- **Card loading** allocates, by its end, 4.0 million `char[]` (a `Uint16Array` each, 452 MB in all), 143,000 `byte[]` of 2.4 KB on average
  (340 MB) and 1.9 million `int[]` (107 MB), counted by wrapping the typed array constructors (`ALLOC` option of the old probe, not kept). Most of it is garbage at once, and
  it is what drives the heap from 0.4 to 1.2 GB. It needs 12 s of the 26 s start.
- At the title screen 13 `Uint8Array` hold the 76 MB of prefetched files (the card zip and packs) until they are consumed.

## Idle at the title screen: the sawtooth

Left alone at the title screen, RSS rises about 28 MB/s for 20 to 25 s, then drops by 400 to 600 MB, again and again
(minimum 1640 to 1790 MB, median 1915 to 1990, maximum 2250 to 2290, six runs, and `VmHWM` 2.3 GB). With `requestAnimationFrame` replaced
by a no-op the process stays flat at 1.9 GB, so the churn comes from the render loop. Stubbing every WebGL call changed nothing, so it is
JavaScript garbage, not the binding.

The garbage is BigInt. TeaVM 0.15 represents `long` as `BigInt` (hard-coded in its runtime, no option), so each long operation allocates. At the
title screen at about 50 frames a second the page makes 4700 `BigInt.asIntN`, 1050 `asUintN` and 3300 `BigInt()` calls per frame (counted by wrapping
them), about 500 KB of heap per frame. Sampling the stacks of one call in 50 (in Chromium, with the source map) puts about half of it in
TextraTypist, which keeps a glyph, its colour and its style bits in one `long`: `Font` (the source lines 3053 and 3412 of the jar's debug info) and the label drawing
(`TextraLabel`, `TypingLabel`), and about a third in libGDX's `IntMap`, `IntFloatMap` and `ObjectIntMap`, whose `place` multiplies the hash by a 64-bit constant (a first count that put it at a tenth was wrong: the sampled stacks, decoded again with the source map, say a third at the title screen and 38 percent at the overworld).

## BigInt work removed (2026-10-06)

The BigInt churn was attributed by call site and mostly removed (9040 to 2145 operations a frame at the title screen, 70,700 to 16,500 at the overworld) by writing `long` literals as BigInt literals in the build's patch of `app.js`
and by integer hashing in three libGDX maps. Chromium RSS did not change, the overworld is 5 percent faster in software GL, and WebKit's idle sawtooth and `VmHWM` did not move beyond the run-to-run spread (title maximum 2.25 to 2.32 GB
before and after; overworld idle median 70 to 180 MB lower). Details, call sites and the measurement table are in [[bigint-churn]]. So BigInt was not the main garbage that fills JavaScriptCore's headroom.

## What did not matter

Each was tried on the same build; none moved the idle maximum or `VmHWM` by more than the run-to-run spread.

- JIT tiers: DFG and FTL off, baseline only, wasm tiers off, concurrent GC off. All JIT off (`JSC_useJIT=0`) lowers the title RSS to 1.3 GB and `VmHWM` to 1.8 GB but takes 190 s to start.
- `JSC_forceRAMSize` 4 GB and 6 GB (what an iPhone 13 and an iPhone 15 Pro would report): same sawtooth. 1.5 GB lowers the trough by about 220 MB.
- All WebGL calls stubbed out (draws, uploads, state): same sawtooth.
- `bufferData` was uploading the whole 120 KB vertex array for every sprite batch flush, about 1.3 MB a frame at the title screen (gdx-teavm's `WebGL20.glBufferData`
  ignores the buffer's limit). A scratch shadow that uploads only the limit cut it to 23 KB a frame. RSS did not change. It would save bandwidth to the GPU process on a phone, but it was not kept.
- `System.arraycopy` of typed arrays creates a `subarray` view per call, 1190 a frame at the title screen. Short copies done in a loop (a build-time patch of `app.js`) removed them. RSS did not change. Not kept.

## What was fixed

The step before "Loading game resources" and a small part of the retained memory came from one bug class, found by hooking `Uint8Array.prototype.toString`:
a minified build renames a `@JSBody` method's parameters to `b`, `c`, and so on, and `Http.takePrefetched` declared its own `var b`, so
`delete p[url]` became `delete p[b]` on the 22 MB array. JavaScript turned the array into a key, a string of 22 million numbers (and in JavaScriptCore a string object for each), and the entry was never deleted, so the prefetched files stayed in memory. Same bug in
`UserDataStore.takeStored`, and in the error path of `TDeflaterOutputStream.compressImpl` (the closure parameter `e` hid the callback `failed`, which a minified build also calls `e`). The variables got long names, and
`web/tools/test_jsbody_names.py` fails when a `@JSBody` script declares a single letter that a minified build may give to one of its parameters.

| Measure (one run each) | Before | After |
|---|---|---|
| Chromium desktop, menu, renderer RSS | 570 MB | 538 MB |
| Chromium desktop, overworld, renderer RSS | 683 MB | 650 MB |
| Chromium phone, menu, renderer RSS | 573 MB | 539 MB |
| Chromium phone, overworld, renderer RSS | 684 MB | 651 MB |
| WebKit iPhone, RSS when "Loading game resources" starts | 1588 and 1609 MB | 826 and 859 MB |
| WebKit iPhone, `VmHWM` at the title screen | 2089 and 2124 MB | 2145 and 1981 MB |
| WebKit iPhone, idle minimum, median, maximum | 1783, 1980, 2278 MB | 1789, 1992, 2284 MB |
| Prefetched files still held at the title screen (`window.forgePrefetch` entries) | 8 | 1 (not identified) |

So Chromium gained about 33 MB, and WebKit lost the transient but not the peak, because card loading and the render loop reach the same top.

## Which parts would also hit a real iPhone

- High confidence, same on an iPhone: the live data (0.55 GB), the card loader's churn, the BigInt churn, the 76 MB of prefetched arrays and everything in
  JavaScript, because JavaScriptCore and our code are the same. The collector's growth rule depends on a RAM size and, on iOS, on memory warnings that this container never sends.
- Medium confidence: that the heap climbs to 1.2 to 1.7 GB. On iOS the collector also reacts to jetsam pressure, which may keep the headroom smaller, and the `forceRAMSize` tests show no change for 4 and 6 GB.
- Linux only: the 0.37 GB base of the blank page and the textures (72 to 121 MB) are software GL in the web process. An iPhone renders in a separate GPU process whose memory is counted differently.
  `JIT` code is 33 MB here and similar there. Allocator behaviour (libpas decommit timing, glibc) differs, so the RSS follows the heap top more slowly here than footprint does on iOS.
- Unknown until a real device is measured: the exact footprint iOS charges the tab. Safari's Web Inspector memory timeline on an iPhone 13 would settle it.

## Options that remain, by expected saving

1. **Slim the card database** (the live part): the live heap is 0.55 GB and the collector's headroom scales with it, so each MB removed saves about two. A build-time binary index of cards, or lazy `CardRules`, is the redesign already
   described under "JS heap by owner" in [[memory-budget]]. It also removes most of the card loader's churn and about 10 s of start.
2. **Remove the rest of the BigInt churn** from the render loop (done in part, see "BigInt work removed" below): what is left is TextraTypist's `Font.calculateSize` and `drawGlyph`, about 1700 operations a frame at the title screen.
   Caching the laid-out size of a label (upstream calls `calculateSize` from `TextraLabel.draw` every frame, with a TODO about it) or a fork of `Font` with the glyph as two `int`s would remove most of them (a fork of 5900 lines, so not done).
3. **The two scratch fixes above** (`bufferData` size, `arraycopy` loop) for CPU and bandwidth, not memory.
4. **Measure on a real iPhone** before choosing between 1 and 2.

## See also
[[bigint-churn]], [[memory-budget]], [[open-issues]], [[e2e-tests]], [[webtest-harness]], [[bug-catalog]]
