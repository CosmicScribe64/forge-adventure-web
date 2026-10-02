---
type: overview
sources: [NOTES.md, PLAN.md, web/build.gradle.kts]
updated: 2026-10-01
tags: [overview]
---

# Overview of Forge Adventure in the browser

**Forge Adventure Web** runs [[forge|Forge]]'s Adventure mode (a libGDX game, `forge-gui-mobile`) in a
browser. It compiles Forge's Java, changed as little as possible, to JavaScript with
[[gdx-teavm]], which is built on [[teavm|TeaVM]] 0.15. The project goal (see [[plan-phases]]) is for Adventure to run
well on phones.

## Where it stands (as of 2026-09-29)

- **Hosted** at <https://cosmicscribe64.github.io/forge-adventure-web/> since 2026-10-01, rebuilt from
  source for each release ([[build-pipeline]]).
- **Playable end to end.** It goes from the tutorial through world generation and the overworld,
  then a town and shop purchase, then a duel won with rewards. Saves persist across reloads in
  IndexedDB. All of this is driven by the [[webtest-harness]] and was also watched live in a
  real browser.
- **Backend:** JavaScript. The WebAssembly GC spike failed on a TeaVM compiler bug (see
  [[stay-on-js-backend]]).
- **Numbers:** app.js is 76 MB raw and 7.1 MB gzipped. World generation went from 44-70 s to
  6.6 s, and the renderer from 1.46 GB to about 1.1 GB at the world. See [[metrics]].
- **Startup (Round 11):** the title screen appears after about 18 s headless, down from about
  33 s. The `[ttg]` console lines break this down in any browser. See [[startup-and-loading]].
- **Next:** phone memory first, because phones crash at about 1.3 GB (an iPhone running Chrome,
  2026-10-01). The ordered steps are in [[memory-budget]]. After that: Scryfall request timing,
  splitting the startup pack (async fetch), phone rotation, and the harness gaps. See
  [[open-issues]].

## How it fits together

```
forge/ (pinned commit, + patches/forge-web.patch)
   │  scripts/build-forge-libs  (Maven in Docker)
   ▼
web/libs/*.jar ──► TeaVM whole-program compile (≈5 GB heap, ~5 min) ──► app.js
                      ▲  our plug-ins: classlib shadows, SubstitutionPolicy,
                      │  CallRedirector, Unsupported, WebReflection
web/src (forgeweb.*) ─┘
forge/forge-gui/res ─► scripts/build-webdata ─► manifest + cardsfolder.zip + packs (all .gz)
                                                  │  fetched by the VFS at runtime
browser: index.html (loader, prefetch, IndexedDB) ─► WebLauncher ─► Forge on one green UI thread
```

The build is covered in [[build-pipeline]]. How our code plugs into the compiler is covered in
[[web-layer-mechanisms]], and where each piece lives in [[code-map]].

## The hard problems, and the page for each

| Problem | Page |
|---|---|
| Forge blocks threads, and the browser can't | [[green-threads]], [[one-ui-green-thread]] |
| Forge reads 466 MB of files synchronously | [[virtual-file-system]], [[startup-and-loading]] |
| TeaVM keeps little reflection metadata, and asking for more exhausts the build heap | [[reflection-on-teavm]], [[reflection-budget]] |
| Java serialization for saves | [[saves]] |
| World generation took a minute and froze the page | [[world-generation]] |
| Memory on phones | [[memory-budget]] |
| Fixed 480x270 layout on arbitrary screens | [[screen-layout]] |
| Classic-mode code bloating app.js | [[classic-code-pruning]] |
| Bugs in TeaVM, gdx-teavm and Forge | [[bug-catalog]], [[teavm-gotchas]] |

## Working principles

- Fix the root cause. Label any stopgap as a stepping stone (PLAN.md).
- Change Forge only through `patches/forge-web.patch`, and keep those changes upstreamable
  (see [[forge-patches-not-fork]]).
- Reproduce every runtime bug as a [[selftest]] check first, then do the full build.
- Measure before and after with `scripts/measure` (see [[metrics]]).
