---
type: status
sources: [PLAN.md#next, NOTES.md#review, NOTES.md#round-6, NOTES.md#round-11]
updated: 2026-10-04
tags: [issues, todo]
---

# Open issues

Known problems that aren't fixed yet, as of 2026-09-29. Remove an item when it's fixed, and log
the change.

## User-reported (watched play-through and later testing)
- [ ] **Phones crash** (user report, 2026-10-01): on the live site the game reached the main
  menu, the page reloaded, loaded again, and then the tab crashed. Measured headless the same
  day, the main menu costs about 820 MB in the renderer plus about 480 MB of GPU memory, at a
  desktop size and at a phone size (390x844 at 3x) alike, which is past what phone browsers allow a
  tab. This is PLAN Phase 5. See [[memory-budget]].
  The phone was an iPhone running Chrome (WebKit). Done so far: streaming music (2026-10-04,
  about 55 MB at the menu) and storing the `app.js` source as one-byte text (2026-10-05, 74 MB at
  the phone-size menu). The phone-size menu is now 705 MB in the renderer plus 435 MB of GPU
  memory, against a target under about 700 MB in total. Revoking the Blob URL saved nothing. Next
  is dropping the startup pack and card zip after startup (about 55 MB expected), then the card
  database (JS heap 281 MB). See [[memory-budget]].
- [ ] **Phone rotation** after startup is untested. Filling the page and live resizes were done
  in Round 10. See [[screen-layout]].
- [ ] **The battle UI layout** (portrait or landscape match screen) is chosen at load and doesn't
  switch on resize. That's upstream behaviour, and the user isn't sure it should switch.

## Startup (Round 11)
See [[startup-and-loading]].
- [ ] Split the startup pack. The title screen waits for 5.9 MB (77 files) that only the first
  map needs. Splitting needs Java to wait for a download that's already in flight (the Phase 6
  async fetch).
- [ ] The biggest phases left are "Loading cards from archive" (about 6.5 s) and "Finishing
  startup" (about 3 s).
- [ ] Views wider than a small map show the clear colour past the map, next to the map's
  near-black, which makes a faint band edge. See [[screen-layout]].

## Harness gaps
See [[webtest-harness]].
- [ ] `dismiss` can press a choice before all the choices appear.
- [ ] `state` should report the mana cost and colours of cards in hand, Forge's overlays (damage
  assignment, choosers, ordering), and non-basic lands (for example Timber Gorge).
- [ ] Add an `autoplay [turns]` harness command, based on the autopilot from the browser session.
- [ ] Check the landscape hand layout (a column on the right) against Forge.
- [ ] Support exit-only POIs, towns and shops, and complete the "travel to town" quest.

## Known technical debt (2026-09-29 review)
- [ ] Native deflate buffers the whole payload. At the end of generation that's the world map
  PNG, about 31 MB raw, which is a short memory spike on phones. See [[memory-budget]].
- [ ] The native deflate path ignores the caller's Deflater level and state. See [[saves]].
- [ ] Extended views much taller or wider than 960 units can show world chunks that aren't
  loaded. See [[screen-layout]].
- [ ] The `ImageUtil` memo and the card art cache are both unbounded. See [[memory-budget]].
- [ ] `scripts/build-webdata` output isn't reproducible: `cardsfolder.zip` stores each card file's
  timestamp, so a fresh checkout gives a different file with the same contents. Writing fixed
  timestamps would fix it. See [[build-pipeline]].
- [ ] `scripts/build-webdata`'s docstring says inflating the card zip took "a quarter of startup",
  while NOTES.md (Round 11) says about 10%. Check which is right.
- [ ] `fallback_skin/title_bg_lq.png` fails to load at startup, and a dummy texture is used
  instead. This predates the port's changes.

## Plan items not started
- [ ] Phase 3: compare the reflection audit with the registries, unify the declarations, and warn
  about EventBus listeners with no handlers.
- [ ] Phase 4: check that the page never stays unresponsive for more than 100 ms.
- [ ] Phase 5 (top priority, phones crash): streaming music and the one-byte `app.js` source are done;
  drop startup data after use, then shrink the card database. Ordered list in [[memory-budget]]. Also releasing the
  minimap pixmap, an LRU image cache, and a test at
  a phone viewport.
- [ ] Phase 6: async fetch, a service worker, and save versioning.
- [ ] Re-measure app.js after the Phase 2 stubs. See [[metrics]].

## Fixed since the last ingest (Rounds 10-11)
Loading bar stages and the world-generation bar (R10). Full screen fills the page and follows
resizes (R10). The Load screen freeze and silently skipped save hooks (R11, [[saves]]). White
lines in the cave and across TenPatch and NinePatch buttons (R11, [[screen-layout]]). The resize
crash, from TeaVM's long casts of NaN (R11, [[bug-catalog]]). The full-screen button moved and
now hides itself. VS screen names fit (R11). Black save thumbnails (R11, [[saves]]). The white
line across the duel background is Forge's own field separator, not a bug.

## Upstream
- [ ] Report the TeaVM and gdx-teavm bugs to their projects. None has been reported yet; the list,
  with each one's status, is in [[bug-catalog]]. The gdx-teavm ones (AsyncResult, the pixmap
  copy, the FreeType leak, Howler decoding music, which is fixed here as of 2026-10-04) have fixes here to offer. Add the lost monitor
  waiters bug in `TObject` ([[scryfall]]) once it is fixed here.
- [ ] Offer the upstreamable Forge patches to Card-Forge (world generation speed, the
  colorIdentity save bug, the duel-start fixes, ViewLayout, the texture seams). See
  [[forge-patches-not-fork]].

## Unexplained
- [ ] Round 6's idle gap of about 60 s after the fonts was never recorded as explained. Round 7's
  prefetching may have fixed it. See [[startup-and-loading]].
- [ ] Is the Phase 3 transformer that drops `synchronized` still needed? See [[plan-phases]].

## Hosting
The build has been public on GitHub Pages since 2026-10-01 ([[build-pipeline]]).
- [ ] Card art requests go from each player's browser to Scryfall, which asks for at most about
  10 requests per second. Measured 2026-10-04: the limiter does space requests to
  `api.scryfall.com` at least 100 ms apart (minimum gap 111 ms, peak 6 requests in one second,
  no 429s), so the rate is safe. A burst of 11 downloads in the deck editor had sent only 3
  requests and silently lost the other 8, because of a TeaVM 0.15 bug in `TObject`'s monitor
  queue. Fixed in the shadow `TObject` on 2026-10-04: the same scenario now sends all 22
  requests, at least 103 ms apart ([[scryfall]]).
- [ ] Report the `TObject.waitForOtherThreads` bug to TeaVM, with the fix and the failing check
  (four threads waiting on a lock whose owner sleeps; only the first ever enters).
- [x] GitHub Pages workflow (`.github/workflows/pages.yml`, 2026-10-01).
