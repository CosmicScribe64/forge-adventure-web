---
type: overview
updated: 2026-10-01
---

# Index

Every page, one line each. Read this first when answering a question, and start with
[[overview]]. The conventions are in [[SCHEMA]], and the history is in [[log]].

## Concepts (how it works)
- [[web-layer-mechanisms]]: the six ways our code plugs into TeaVM (shadows, policy, emu, CallRedirector, Unsupported, patch) and the rules learned the hard way
- [[green-threads]]: green threads, the single UI thread, borrowed monitors, lost wake-ups
- [[virtual-file-system]]: read-through `res/` from a manifest, packs, sync XHR, IndexedDB user data
- [[reflection-on-teavm]]: the three ways to provide metadata, by-name classes, the EventBus stub, SerialHooks
- [[saves]]: own object-stream format, IndexedDB, the colorIdentity bug, autosave cost
- [[world-generation]]: WFC, from 44-70 s down to 6.6 s, with Forge patches, Web Workers and the wfc-golden check
- [[memory-budget]]: where memory goes, leaks fixed, the phone target of under 1 GB, the WebKit attribution
- [[startup-and-loading]]: from index.html's prefetch through WebLauncher and card loading to the title screen, and progress reporting
- [[screen-layout]]: canvas sizing, extended design size, ViewLayout, resizing and rotation
- [[build-pipeline]]: Docker, scripts, TeaVM environment variables, the 7.7 GB memory ceiling
- [[classic-code-pruning]]: ReachReport and Unsupported, to cut Classic code from app.js
- [[selftest]]: the 25 s check suite; one check per real bug
- [[unit-tests]]: JVM and Python tests (file store, latin1-js, JSBody names, Forge's own), run by scripts/unit-test and CI
- [[e2e-tests]]: the boot, new game and cycle scenarios in desktop, Chromium phone and WebKit iPhone modes, CI and release wiring, the release rehearsal and the live 0.1.1 baseline
- [[webtest-harness]]: webtest.py steps, the in-game WebTest harness, play scripts, gaps

## Components (our code)
- [[code-map]]: each package, its purpose, and the concept page it serves

## Entities (external projects)
- [[teavm]]: the compiler; properties, classlib bugs, what to re-check on upgrade
- [[gdx-teavm]]: the libGDX backend; bugs fixed via emu shadows
- [[forge]]: the game; relevant modules, upstream relationship
- [[scryfall]]: card art source; caching and rate notes

## Bugs
- [[bug-catalog]]: every defect found in TeaVM, gdx-teavm and Forge (and general lessons from ours), with fix and upstream status
- [[teavm-gotchas]]: JVM assumptions TeaVM breaks (no null or bounds checks, annotations, suspension)

## Decisions
- [[stay-on-js-backend]]: wasm GC is blocked by a TeaVM coroutine bug (revisit when TeaVM is upgraded)
- [[one-ui-green-thread]]: all game code on one green thread; callbacks only enqueue
- [[single-processor]]: `availableProcessors()` returns 1
- [[reflection-budget]]: narrow reflection metadata to keep the build under 5 GB
- [[forge-patches-not-fork]]: one upstreamable patch file; no TeaVM fork

## Status
- [[plan-phases]]: PLAN.md's phases against their exit criteria
- [[metrics]]: numbers over time, with conditions
- [[open-issues]]: known, unfixed, user-reported, unexplained

## How-to
- [[pick-up-work]]: start here to continue the project: what to read, CI and releases, test helpers, conventions
- [[update-forge]]: move the Forge pin; what breaks and where
- [[add-missing-api]]: pick the right mechanism for a missing or broken API
- [[fix-runtime-crash]]: capture the exception, add a SelfTest check, fix it, then do the full build
- [[debug-stuck-run]]: reading heartbeats, and telling a hang from runaway allocation
- [[play-session]]: start, drive, and stop a headless game session

## Sources
- [[src-notes-md]]: NOTES.md, the lab notebook, and which page covers each section
- [[src-plan-md]]: PLAN.md, the architecture plan
- [[src-forge-web-patch]]: patches/forge-web.patch, files by purpose
- [[src-web-layer-code]]: the project's own code, and what it says beyond NOTES

## Analyses
- [[webkit-memory]]: where Linux WebKit's 1.9 to 2.3 GB goes (JavaScriptCore headroom, card-loading and BigInt garbage, a minified-build bug), what did not matter, what would apply on an iPhone
