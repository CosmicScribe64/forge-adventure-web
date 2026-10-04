---
type: entity
sources: [NOTES.md#round-8, forge/forge-gui/src/main/java/forge/util/ScryfallRateLimiter.java, forge/forge-gui-mobile/src/forge/util/LibGDXImageFetcher.java, web/src/main/java/org/teavm/classlib/java/lang/TObject.java, web/tools/scryfall-requests.js]
updated: 2026-10-04
tags: [external, card-art, rate-limit]
---

# Scryfall

Scryfall is the public Magic card database and API. The web build gets card art from Scryfall's API
through Forge's `LibGDXImageFetcher`. CORS is allowed, so it works from the browser directly.

- Art is cached **in memory only**, with no cap yet (an LRU cap is planned; see [[memory-budget]]).
- Scryfall asks clients for at most about 10 requests per second. The build is public, so this
  matters; see the next section.
- The desktop bulk CDN download prompt is disabled on the web ([[classic-code-pruning]]).

## Where art is fetched

Art is fetched from `api.scryfall.com/cards/<set>/<number>/en?format=image&version=normal`. The
browser follows Scryfall's redirect to `cards.scryfall.io` inside the same request, so the
Performance API shows no separate CDN entries and `cards.scryfall.io` is not throttled anyway.
`UI_ENABLE_ONLINE_IMAGE_FETCHER` is on by default, so no setting needs changing. Two screens
fetch art in the headless harness: a duel (the hand and the cards the AI plays) and the
Adventure deck editor (the art crop for each row that scrolls into view). The
`scryfall-requests.js` helper lists only finished requests, so it cannot see a request that
never completes.

## Rate limiter check (2026-10-04)

`ScryfallRateLimiter.acquire` (`forge-gui`) sleeps inside a `synchronized` block so that
requests to `api.scryfall.com` are at least 100 ms apart. It is called by `LibGDXImageFetcher`
just before each download. Measured in headless Chromium on the local build, new character,
seed 1, with a hook on `XMLHttpRequest.send`:

| Run | Requests sent | Gaps between sends | Peak in any 1 s window | 429 responses |
|---|---|---|---|---|
| First duel, Blue Tower, hand of 7 | 7 to `api.scryfall.com` | minimum 112 ms, median 201 ms | 6 | 0 |
| Deck editor, 11 rows needing art | 3 sent of 11 started | 111 ms and 117 ms | 3 | 0 |

No request went to any other Scryfall host that the Performance API could see. Neither log
contained a line with "429" or "cooldown", so the limiter's backoff was never exercised. An
earlier run reported two such matches with no requests behind them; they did not reproduce, and
the likely cause is a loose text match on something else, such as a number in a heartbeat line.

**Verdict.** The spacing itself works: every measured gap was at least 100 ms, and the request
rate stayed under the 10 per second Scryfall asks for. But the limiter exposes a separate bug
that loses requests. When 11 downloads started in the same millisecond (the deck editor),
only the first 3 were ever sent, and the other 8 never completed, even after several minutes.
The duel did not show it because its fetches started about 100 to 300 ms apart.

**Root cause.** TeaVM 0.15's `TObject.waitForOtherThreads` takes one waiting thread off the
monitor's queue and then sets the whole queue to `null`. Every other thread waiting for the
lock is dropped and never woken. `acquire` is the first code here where several green threads
queue on one lock while its owner sleeps, so it hits this. The shadow `TObject` in
`web/src/main/java` keeps the stock behaviour (the stock source is in the
`teavm-classlib-0.15.0-sources.jar`). See [[green-threads]] and [[bug-catalog]].

**Proposed fix, not yet made.** In the shadow `TObject.waitForOtherThreads`, remove one waiter
and set the queue to `null` only when it is empty afterwards. Add a [[selftest]] check with
three or more green threads contending for one lock while the owner sleeps. Then repeat the
deck editor measurement and expect 11 sends at least 100 ms apart. Report the bug to TeaVM too
([[open-issues]]).

## See also
[[memory-budget]] · [[webtest-harness]] · [[green-threads]]
