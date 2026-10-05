---
type: status
sources: [PLAN.md#next, NOTES.md#review, NOTES.md#round-6, NOTES.md#round-11]
updated: 2026-10-05
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
  the phone-size menu) and dropping the card zip and the packs after use (2026-10-05, 36 MB at the
  phone-size menu, 41 MB at the overworld). The phone-size
  menu is now 631 MB in the renderer plus 217 MB of GPU memory, against a target under about
  700 MB in total. Revoking the Blob URL saved nothing. Font sizes (2026-10-05, 123 MB of
  textures) and the duel-only sprite sheets (2026-10-05, 98 MB at the menu) are now read on first use. Live textures at
  the phone-size menu are 74 MB (295 MB at the start of the day); a duel costs 225 MB.
  Skin sheet mipmaps are still on ([[memory-budget]], "Lazy sprite sheets"). Then the card
  database (JS heap 277 MB, of which about 190 MB is cards). Forge's lazy card loading would save
  about 150 MB but leaves the database empty for the adventure reward and enemy code, which
  enumerates it, so it needs a slim card index first ([[memory-budget]], "JS heap by owner").
  The cheap first step, `CardType` and `CardRules` creating their collections on first add, is done
  (21 MB, 2026-10-05).
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
  instead. This predates the port's changes. The boot smoke test allows this line.

## Testing (2026-10-05)
- [x] SelfTest passed only 35 of 39 checks minified. Fixed 2026-10-05: the long-cast patch matches by
  body and `TObjectInputStream.allocate` no longer reads a renamed JavaScript field. 39 of 39 pass in
  both modes. See [[selftest]].
- [ ] Stack traces from the minified release use short names. The source map is published next to
  `app.js` but is only used by DevTools; `webtest` prints raw traces. Nothing translates them yet.
- [ ] CI does not run the minified SelfTest (5 min extra compile); only releases do. A change that
  breaks minifying shows up at the release. Run `TEAVM_OBFUSCATED=true scripts/selftest` by hand
  for changes that touch reflection, serialisation or `@JSBody` code.
- [ ] Village names in the world are random per run, not seeded, even with `seed=1` (the layout is the
  same). Found while comparing builds; not a minification effect.
- [ ] The harness must not be called while the game is starting up; a queued `api state` killed
  the UI thread in a trial (`until-state` waits for the loading screen to go). Find out why.
- [ ] The boot smoke test's allowlist hides four known error lines; remove an entry when its cause
  is fixed ([[webtest-harness]]).
- [ ] Other test-plan steps (JVM unit tests, game scenarios, memory thresholds) are not started.

## Found 2026-10-05, not fixed
- [x] The wasm pixmap heap grew by 31 MB per new game and textures by 11.7 MB (the old `biomeImage`
  and `TiledMap` were never disposed). Fixed 2026-10-05 in `patches/forge-web.patch`; see
  [[memory-budget]] and [[bug-catalog]].
- [ ] A small leak remains: about 1 MB of wasm pixmap heap and 0.4 MB of textures per new game
  (one live pixmap more each time, found by comparing `forgePixmaps.live` across games). Its owner
  is not known; `scripts/e2e-cycle` allows 5 MB of growth over two extra games.
- [ ] The `scripts/e2e-newgame` limits are 1.25 times the measured values, so the 889 MB overworld
  RSS from before the worker change would still pass the 895 MB limit. Tighten them when the numbers settle.
- [ ] `scripts/e2e-newgame` is not in CI (74 s on top of a build); see [[webtest-harness]].

## Plan items not started
- [ ] Phase 3: compare the reflection audit with the registries, unify the declarations, and warn
  about EventBus listeners with no handlers.
- [ ] Phase 4: check that the page never stays unresponsive for more than 100 ms.
- [ ] Phase 5 (top priority, phones crash): streaming music, the one-byte `app.js` source, dropping startup data and lazy font sizes and sprite sheets are done;
  next shrink the card database. Ordered list in [[memory-budget]]. Releasing the
  minimap's JS copy is done; disposing it, an LRU image cache, and a test at
  a phone viewport remain.
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
