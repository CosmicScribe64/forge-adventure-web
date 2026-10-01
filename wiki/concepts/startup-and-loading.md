---
type: concept
sources: [NOTES.md#round-6, NOTES.md#round-7, NOTES.md#round-8, NOTES.md#round-10, NOTES.md#round-11, PLAN.md#next, web/html/index.html, web/src/main/java/forgeweb/compat/Progress.java, web/src/main/java/forgeweb/compat/LoadingScreen.java]
updated: 2026-10-01
tags: [startup, loading, ux]
---

# Startup and loading

This page follows the path from opening the page to the Adventure title screen, and what the
user sees on the way. It took 28-40 s at the baseline (headless). In Round 7 it took about 16 s to
reach the menu when nothing else was loading the machine. See [[metrics]].

## Sequence
1. **`web/html/index.html`** replaces gdx-teavm's page. It shows a loading screen instantly
   with real download progress, and downloads app.js, the manifest, `cardsfolder.zip` and all
   packs (including the startup pack) **in parallel**, un-gzipping game data natively as it
   arrives ([[virtual-file-system]]). The downloaded files go into `window.forgePrefetch`, and
   `forgeweb.fs.Http` takes them from there instead of making blocking sync XHRs. It also reads
   IndexedDB user data before the game starts (`UserDataStore`).
2. **`forge.web.WebLauncher`** starts [[forge|Forge]] (`Forge.getApp(...)`), starts
   `UiThread` ([[green-threads]]), selects gdx-controllers' no-gamepad stub, and sets the WFC
   chunk solver.
3. Forge startup: preferences, skin, fonts (FreeType), language files, then **all 33,980
   cards** (about 10 s, and about 6.5 s since Round 11), then the Adventure start screen.
   `UI_SELECTOR_MODE=Adventure` is the default preference, so it goes straight into Adventure. The Classic and Exit buttons are hidden when
   `forge.web` is set (StartScene patch).
4. `forgeweb.compat.LoadingScreen` hides the page's loader on the first rendered frame.

## Progress reporting (Round 10)
- Downloads fill the bar to 40%, and Forge's own startup fills the rest. The page downloads
  `app.js.gz` and the `forge-data/*.gz` files and un-gzips them itself, so it counts wire bytes
  against `Content-Length`. A file the server compresses on the fly (`Content-Encoding`) counts
  only once it has arrived, because the browser hands the page decoded bytes (2026-10-01; see
  [[bug-catalog]]).
- In `forgeweb.compat.Progress`, CallRedirector routes `CardStorageReader.ProgressObserver` and
  `FProgressBar.setDescription` to `window.forgeProgress`.
- Loading runs on a green thread that never waits, so nothing could repaint. Each report
  **pauses the thread** (at most once every 100 ms), so the page and the splash screen can draw. It must yield
  *before* posting to the EDT (the lost wake-up hazard in [[green-threads]]).
- World generation shows a small floating bar of chunks solved out of chunks sent.

> [!note] Superseded
> PLAN "Next" (2026-09-29) reported the bar reading 100% through "Starting Forge" and
> "Generating world". Round 10's progress reporting above fixed both (Forge's stages fill the
> bar; world generation has its own bar), per the watched session.

## Time to gameplay (Round 11)
The console shows `[ttg]` lines with the time each stage began (page, app.js, Forge's splash
labels, scenes), and totals at the title screen and at **gameplay**. Gameplay means the first map
the player walks on, which for a new game is the tutorial cave, a `TileMapScene`. The marks come
from `window.forgeMark` (index.html) and from `Progress.mark` and `MainThreadListener`. This
works in any browser, so a user's console gives the same breakdown as a headless run.

What the first breakdown showed, and what was done (numbers in [[metrics]]):
| Phase | Cause | Fix |
|---|---|---|
| Loading cards from archive ~10 s | 34,000 zip entries inflated by TeaVM-compiled JZlib | stored entries, gzip over the whole file, native un-gzip |
| Card DB maps | `String.CASE_INSENSITIVE_ORDER` looks up `TCharacter.toLowerCase`'s table for every character | `StringCompat` (ASCII fast path) via CallRedirector |
| Loading fonts 3 s + "Preparing database" 4.6 s | 65 FreeType sizes, each written as PNG and loaded back; the UI-thread half queued behind startup | Forge patch: on the web `FSkinFont` keeps the generated font |
| downloads | `deckgendecks/*.dat`, 7 MB of unreadable JDK serialization | left out of web data |
| Finishing startup, world generation | 144 blocking sync XHRs before the title, about 220 before the first map | startup pack |

Keeping Forge's font cache in IndexedDB was tried and dropped, because loading 65 cached atlases
took as long as generating them. What's left is the card archive (about 6.5 s), "Finishing
startup" (about 3 s), and splitting the startup pack so the title screen doesn't wait for 5.9 MB
that only the first map needs. That needs async fetch (Phase 6).
`webtest --latency MS` and `--mbps N` emulate a hosted page ([[webtest-harness]]).

## History
- Round 6: after the fonts, the page sat idle for about 60 s (0% CPU, no console output) before
  the second card load, probably because a request timed out. Round 7's packs and prefetching
  brought the menu to about 16 s. Whether the idle gap was ever explained isn't recorded.
- Round 6: the tab crashed on `effects/demo.gif` (see [[memory-budget]]).

## Card art
Card art is fetched from [[scryfall]] on demand through Forge's `LibGDXImageFetcher`, and
cached in memory only. `Forge.maybePromptForBulkCdnSync` is a no-op
([[classic-code-pruning]]).

## Planned
A service worker cache for repeat loads and async fetch (PLAN Phase 6, [[plan-phases]]), and a
smaller app.js ([[classic-code-pruning]]).
