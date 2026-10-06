---
type: concept
sources: [web/tools/device-run.py, web/tools/device-probe.js, scripts/devices-android, .github/workflows/devices.yml, scripts/e2e-boot, scripts/e2e-newgame, scripts/e2e-cycle, scripts/e2e-common, scripts/e2e-release, scripts/serve-site, web/tools/webtest.py, .github/workflows/ci.yml, .github/workflows/pages.yml, web/html/index.html]
updated: 2026-10-06
tags: [testing, phones, webkit, releases]
---

# End-to-end tests: devices, CI and the release rehearsal

The end-to-end scenarios drive the built game in a real browser. Each one runs in three modes, desktop,
Chromium emulating a phone and WebKit with an iPhone descriptor, and the release workflow runs all of them on the assembled site
before it deploys and again on the live site afterwards. The harness itself is in [[webtest-harness]] and the numbers they
check are in [[memory-budget]].

## Scenarios

| Script | What it checks | Run time on top of a build |
|---|---|---|
| `scripts/e2e-boot` | the loading bar reaches 100 percent, the title screen comes up, no unexpected console errors, and on touch devices nothing but the game is under the top centre of the page; the display checks below, a resize to a smaller viewport and a tap on New Game after it; and the audio check | 28 to 34 s |
| `scripts/e2e-newgame` | boot, then a new game (seed 1) played to the overworld with real taps or clicks on New Game and Start, no unexpected errors, no file of 1 MB or more downloaded twice, and memory limits at the menu and the overworld (not in WebKit); the display checks at the menu (also after a resize to a smaller viewport and back) and the overworld; the audio check | 58 to 68 s |
| `scripts/e2e-cycle` | three new games in one session; pixmap heap, textures, JS heap and RSS do not grow ([[webtest-harness]]) | 218 to 224 s on desktop |

## Devices

The first argument, or `DEVICE`, picks the mode (`scripts/e2e-common`). Logs and screenshots get `-phone` or `-iphone` in their names.

| Mode | Engine and context | Asserts |
|---|---|---|
| `desktop` | Chromium, 1280x720 | everything, with the limits in `scripts/e2e-newgame` |
| `phone` | Chromium, 390x844, scale 3, mobile, touch, Android user agent | everything, with a limit block of its own (the numbers equal desktop within a few MB, see [[memory-budget]]) |
| `iphone` | Playwright's WebKit, descriptor "iPhone 13" (`IPHONE_DEVICE` changes it) | function: the title screen, no unexpected console errors, a new game to the overworld, no repeat downloads. Texture, pixmap and wasm numbers are recorded and RSS is recorded from the web process; none are asserted, because WebKit cannot report a JS heap and its RSS is not comparable with Chromium's |

`URL=<site root>` loads a site that is already running instead of starting `scripts/serve-web`, for the rehearsal and for the live
site. `E2E_CACHE_BUST=<text>` adds a query parameter so a CDN that caches by URL serves the current `index.html`.

The WebKit that Playwright ships on Linux is the WPE port, not the Apple build on an iPhone. It tests the engine's JavaScript
and WebGL behaviour, its console, and its memory growth, but not iOS limits, the iOS touch stack, audio unlocking or the
address bar. See [[open-issues]].

## Where they run

- **CI**, on every push: `scripts/e2e-boot` on desktop and `scripts/e2e-boot phone`. The phone run adds about 30 s (a second boot, plus starting the server again).
- **`pages.yml`, before deploying**: `scripts/build-site`, then `scripts/e2e-release site`, which starts `scripts/serve-site` (the
  site under `/forge-adventure-web/`, compressed by `web/tools/serve-compressed.py`) and runs boot and newgame in all three modes and
  e2e-cycle on desktop (`CYCLE=all` adds the phone modes). It keeps going after a failure and prints a result line per run. About 8 minutes in total (486 s), against about 1.5
  minutes for the boot and new game scenarios it replaces.
- **`pages.yml`, after deploying**: the `verify-live` job runs `scripts/e2e-release live` (e2e-boot in the three modes, about 100 s) against the
  page URL the deployment reports, three attempts two minutes apart.

## Release rehearsal, 2026-10-05

Commit 49cfe5e plus the changes of this session, minified with a source map (`TEAVM_OBFUSCATED=true TEAVM_SOURCE_MAP=true scripts/build-web`,
5 min 42 s), `scripts/build-site`, `scripts/e2e-release site`, in the sandbox (4 cores, headless, software GL, a proxy in front of the browser):

| Run | Result | Time | Key numbers |
|---|---|---|---|
| boot, desktop | pass | 26 s | |
| boot, phone | pass | 26 s | top centre of the page is not the full screen button |
| boot, iPhone | pass | 32 s | same check |
| newgame, desktop | pass | 58 s | menu RSS 574, overworld 691 MB, rise 117 MB, 41 distinct requests |
| newgame, phone | pass | 58 s | menu RSS 571, overworld 683 MB, rise 112 MB, 47 distinct requests |
| newgame, iPhone | pass | 68 s | web process RSS 1880 and 2519 MB (not asserted), 49 distinct requests |
| cycle, desktop | pass | 218 s | pixmap +2 MB, textures +0.9 MB, RSS +17 MB (game 3 over game 2), heap +2.3 MB over games 1 to 3 |

`TEAVM_OBFUSCATED=true scripts/selftest` passes 39 of 39 (384 s, with its compile) and the readable `scripts/selftest` 39 of 39 (309 s), run again after the last code change, with the unit tests (29 JVM, 15 Python) passing.

## The live site, version 0.1.1, in WebKit (baseline, 2026-10-05)

Loaded from https://cosmicscribe64.github.io/forge-adventure-web/ with the same scripts:

| Run | Result | Time |
|---|---|---|
| boot, desktop (Chromium) | pass | 30 s |
| boot, phone (Chromium) | pass | 31 s |
| boot, iPhone (WebKit) | **fail**: WebGL `INVALID_ENUM` console errors | 36 s |
| newgame, iPhone (WebKit; `NEWGAME_INPUT=api` because 0.1.1 has no `api where`) | **fail** on 78 console errors; it did reach the overworld | 76 s |

The WebKit web process of 0.1.1 is 2701 MB at the title screen and 3454 MB at the overworld (textures 295 and 347.5 MB), against 1.9 GB and
2.3 to 2.5 GB now. This machine has no memory limit, so neither crashes here; a phone has one.

## Display and audio checks (2026-10-06)
The webtest step `display-check [cap]` ([[webtest-harness]]) asserts, for the page as it is at that moment:
the canvas's CSS box is the visible viewport (`visualViewport`, else the window) and sits at the origin;
its backing store is that size times `max(1, min(devicePixelRatio, cap))` (cap 2 unless given), which is computed in the test, not read from the page; the game's own size (`api display`, the new
harness command) is the CSS size and its framebuffer is the backing store; the page cannot scroll (`scrollTo(0, 100)` leaves `scrollY` at 0, and the document is no larger than the window); and nothing but the canvas
is at the four corners. It prints one line, for example `display: css 390x844 backing 780x1688 ratio 2.0 dpr 3 game 390x844 (buffer 780x1688)`.
`scripts/e2e-common` gives each device a viewport and a smaller one (`SMALL_W`, `SMALL_H`: desktop 1280x720 and 1000x600, phone 390x844 and 390x700, iPhone 390x664 and 390x560; 844 to 700 is a phone's browser bars appearing).
- `e2e-boot` checks at the title screen, resizes to the small viewport, checks again, and taps New Game on the resized screen (the tap maps the game's own button position to the page, so reaching the next screen proves taps land after a resize).
- `e2e-newgame` checks at the menu, after resizing to the small viewport, after resizing back, and at the overworld.
- `E2E_QUERY=pixelratio=3 DISPLAY_CAP=3` runs a scenario with another pixel ratio, for measuring ([[display-and-viewport]]).
- The step `audio-check none|playing` counts the Howler sounds that are playing (`Howler._howls`). On `phone` and `iphone` the scenarios assert none after the first tap and at the overworld (a fresh profile has audio off, [[display-and-viewport]]),
  and on desktop `playing` once after the first tap (headless Chromium allows autoplay, so music starts at the title).
Run on 2026-10-06 against the minified build, all three devices pass; the display lines were 1280x720 and 1000x600 (ratio 1), 390x844 and 390x700 at 780x1688 and 780x1400 (phone), 390x664 and 390x560 at 780x1328 and 780x1120 (iPhone).
What these cannot show: the clipping itself. Playwright's phone modes have no browser bars, so the visible viewport is the whole window; the resize check is the stand-in for the bars appearing.

## Devices: real Mobile Safari and real Chrome (2026-10-06)
`.github/workflows/devices.yml` runs the game on device simulators, on GitHub's free runners, so a phone isn't needed to see what Safari and Android Chrome do.
It runs on demand (`gh workflow run devices.yml --ref main [-f site_url=...]`, the live site by default) and `pages.yml` calls it twice:
`devices-built` beside the deploy, on the `github-pages` artifact the build job uploaded, and `devices-live` after `verify-live`, on the page URL.
The first is not a gate (the deploy does not wait for it; it can only turn the run red) because its built-site mode has not run on a runner yet; the second turns the run red after the deploy. Against the live site both jobs passed (run 37544083281: iOS 18 min, Android 12 min; earlier runs failed on the old site, on Chrome dying and on a Scryfall flake, all handled since).

| Job | Runner | Device | Browser | Time (live site) |
|---|---|---|---|---|
| `ios` | `macos-15` | newest "iPhone NN" of the newest iOS runtime (393x852 at 3x; its user agent says iPhone OS 18_7, which Safari freezes, and Safari reports 26.2) booted with `xcrun simctl` | Mobile Safari 26.2 (the real build, in the Simulator) | 14 to 24 min per job, 8 to 13 min of it the probe; the rest is runner start, simulator boot and `footprint` readings |
| `android` | `ubuntu-latest` with KVM, `reactivecircus/android-emulator-runner` | Pixel 6 profile, API 34 Google APIs x86_64, 6 GB, swiftshader GL, 1080x2400 at 420 dpi | Chrome 113 (the image's own) | 12 to 32 min per job (a boot, then the probe 10 min; a crash costs a 5 minute wait) |

The two run in parallel, so `devices-built` adds no time to the deploy and `devices-live` about 20 minutes after it.

**How it works.** `safaridriver` drives desktop Safari and real iPhones, not the Simulator (that takes Appium and WebDriverAgent), and Android Chrome's chromedriver needs more set-up than a page that already has a test API needs.
So `web/tools/device-run.py` serves the game on `localhost:8123` (`--site` a built folder, with `serve-compressed.py`'s gzip rules, or `--upstream` a proxy to a running site such as the live one; `adb reverse` makes `localhost` reach it from the emulator),
puts `web/tools/device-probe.js` into `index.html`, opens the page with `xcrun simctl openurl` or `adb shell am start`, and waits for the probe's verdict.
The probe runs inside the page and uses the game's own test API (`window.forgeTest.cmd`: `state`, `where`, `display`, `dismiss`, `goto`, `console spawn enemy`, `duel`).
It asks the server for what only the host can do: `xcrun simctl io screenshot` or `adb exec-out screencap` (`/__shot`), and the browser's memory (`/__mem`). The harmless-error list is read from `web/tools/webtest.py` (`ALLOWED_ERRORS`), so the two stay in step.
The server restarts the page if the probe goes quiet for 5 minutes (an Android tab that was killed). Logs, `summary.json` (checks, memory, warnings) and the screenshots are uploaded as the artifacts `devices-ios` and `devices-android` (`-built` or `-live` added when `pages.yml` calls them); the job summary shows the table.

**Checks** (the same ones as `e2e-boot` and `e2e-newgame`, in the page, with taps made as touch events on the canvas): boot to the title screen; Scryfall's API and an image reachable; `display` at the title, the overworld, the VS screen and the duel (the canvas is the visible viewport between the browser's bars, the backing store is css size times the capped ratio, the game agrees, nothing else at the corners); a script cannot scroll the page (`scrollTo(0, 100)` leaves `scrollY` at 0); audio off after the taps; no unexpected console errors at the title, overworld and duel; a new game to the overworld by taps on New Game and start; the VS screen and a duel against a spawned Clay Golem. Screenshots: title, create, overworld, vs-a, duel (device screenshots, with the browser's bars) and `canvas-*.png` (the game's own canvas, read from the page).
A Scryfall image fetch that fails with HTTP code -1 is a warning, not a failure (it happened in 2 of 5 iOS duels, and is Scryfall refusing a burst from a shared CI address or a real Safari problem; not told apart, see [[open-issues]]).

**Memory** (live site, 2026-10-06, the browser's own process, one run each):

| | Title | Overworld | Duel |
|---|---|---|---|
| iOS Simulator, Safari WebContent process, `footprint` | 950 MB | 1086 MB | 1182 MB |
| Android emulator, Chrome renderer, PSS (all of Chrome's processes) | 471 (636) MB | 621 (803) MB | 628 (824) MB |

For comparison the Playwright WebKit RSS was 1.9 GB at the title and 2.3 to 2.5 GB at the overworld, and Chromium's renderer 530 to 630 MB ([[memory-budget]]): real Safari needs half of what Linux WebKit needs, and Android Chrome is where Chromium on the desktop is. The `footprint` figure is what iOS counts against a tab; `ps` RSS is noisy (it ranged 322 to 1051 MB for the same process).

**What this covers that the others don't:** the real iOS WebKit and Safari (bars, bottom address bar, safe areas, `visualViewport`, touch events, `webkit` WebGL), and Android Chrome's layout with its toolbar (412x786 visible of 412x915). **What it doesn't:**
- Real hardware: both run on software GL and a virtual CPU, so frame rates and memory limits are not the phone's. Neither simulator kills a tab at a memory limit as a phone would (the Android emulator lost its Chrome tab or never loaded the page in 4 of 6 runs, unexplained; `logcat-kills.txt` is in the artifact).
- Real input: the taps are touch events made by the page. `simctl` has no tap command and the Android taps were not wired to `adb shell input`. The browsers' own gesture handling (bar collapse, pull to refresh, double-tap zoom) is therefore not exercised.
- iOS: Safari's first-run tip ("View Bookmarks, Share Menu, and Open Tabs") covers the bottom fifth of every device screenshot and can't be dismissed without a tap; use `canvas-*.png` for the game's own picture. The `defaults write` switches tried did not hide it.
- Android: the emulator's screenshot of a WebGL page is black (swiftshader), so only `canvas-*.png` shows the game. Chrome is version 113, not the current one.
- Resizing and rotation, audio unlocking (audio is only checked to be off), the live site's service worker (there is none), and the built-site mode (`--site`) on a runner: it is tested only locally, and `pages.yml` has not run `devices-built` yet.
- The probe takes `localhost` as the origin; the live site's real origin is `cosmicscribe64.github.io`, so anything depending on it (cookies, IndexedDB per origin, CORS to the host) isn't seen. The proxy forwards bytes and headers unchanged.

**First findings:** the live 0.1.1-era page of the morning had the canvas taller than the visible viewport in both browsers (iOS: top at -100 to -120 px, "page scrolls"; Android: -28 px, 842 px high in 786), which is what the display work fixed; the same checks pass on the new build in both. Before a fix is believed on a phone, run `gh workflow run devices.yml --ref main`.
Run it by hand locally (no device) against a build with `scripts/webtest` as the browser: `python3 web/tools/device-run.py --platform none --out out/dev --site web/build/site --launch "<command that opens {url}>"`.

## Findings from the WebKit and phone runs

- **`GL_LINE_SMOOTH` is not a WebGL capability** ([[bug-catalog]]). Forge's `Graphics` enables it around every line and outline.
  Chromium swallows the `INVALID_ENUM`; WebKit logs a console error for each call, 42 to 78 lines on the way to the overworld, and
  `--strict` fails on them. Fixed in the patch (`Graphics.setLineSmoothing`). Desktop Chromium never showed it.
- **WebKit failing a blob media load by itself** (`HowlMusic`). In about one WebKit iPhone boot in 15 the title screen's music
  element got `MEDIA_ERR_SRC_NOT_SUPPORTED` (code 4) a few milliseconds after `loadstart`, and WebKit logged `Failed to load resource`,
  a message with no URL; webtest's `[netfail]` line named a `blob:` URL (failure `None`). We first blamed `dispose()` revoking the
  Blob URL (the title screen swaps its track within half a second) and delayed the revoke by 5 s, but the failure came back (2 in 12
  boots with the delay), and the log showed that nothing had been revoked when it happened, and that fetching the same URL right
  afterwards worked. An isolated page (real Howler, 300 create-and-dispose cycles of mp3 tracks) failed 1 to 5 times a run with
  every variant tried: Howler's `src` swap kept, replaced or left out, a gap of 400 ms between tracks, a MIME type on the Blob, fetching the URL
  before use, holding the Blob and the audio elements so nothing is collected, and a plain `http:` URL (which fails the same way, silently). Only a `data:`
  URI, which WebKit decodes in the page and never sends to the network process, had no failure (6 runs, 1800 loads). `HowlMusic` now
  builds a `data:` URI (a 3.7 MB track becomes 5 MB of string, freed at `dispose()`), so there is nothing to revoke. After the change 20 iPhone boots
  in a row passed. The cause inside WebKit (probably its GStreamer media loader cancelling the request) was not found.
- **Music check, run by hand** (2026-10-06, not a script in the repo). The page's own state is the evidence, because headless browsers
  can't be heard: an init script hooks `Howl.prototype` (`init`, `play`, `pause`, `stop`, `unload`, `volume`) and keeps every HTML5 Howl, and
  webtest `expect` steps read them. On desktop, phone and iPhone mode (minified site build, no console error in any run) the title
  music is a `data:` element that is running and whose `seek()` advances by 3 s in 4 s after the first tap; seeking to 1.5 s before the end
  makes the game dispose that track (stopped, unloaded, its data string dropped) and start the next one, which advances; a looping Howl on
  the same data URI wraps from the end back to the start; moving the music volume slider in the settings (desktop only, the slider is
  found by scrolling) reaches the Howl as `volume(0.5)`; starting a new game unloads the menu track and plays the cave track alone; leaving
  the cave through the portal pauses the cave track (shelved, not playing), unloads the older shelved one and plays the overworld track.
  Not covered: a town or a duel (the harness has no command to enter one), real iOS Safari's gesture rule (the unlock path ran only
  where WebKit let the first play through), and anything audible. Every one of the 79 audio files the game ships (`forge/res`, 37 in music
  folders) is an mp3 (56 with an ID3 tag, 23 starting at a frame header), and the first-bytes check names `mp3` for all of them.
- **WebKit's memory** is about three times Chromium's ([[open-issues]]).
- **The page's full screen button covered the top of New Game on a phone.** On a touch screen the button was shown for the first
  seconds after loading at the top centre, 34x28 pixels from 4 pixels down, and the title screen's New Game button starts about
  25 pixels down at 390 pixels wide. While it was shown `document.elementFromPoint(195, 28)` was the button, so a tap there
  toggled full screen and not New Game. It is not shown unasked on touch screens now, and `e2e-boot` checks the top centre on phones.
  This is the one place found where a phone-size click could be lost.
- **Menu clicks at phone size, root cause:** the earlier report that menu clicks did not work at phone size could not be reproduced on this build. A plain mouse
  click at a button's coordinates, a touchscreen tap and the harness's `api click` all reach New Game and Start in
  Chromium's phone mode. What was missing is that no test sent real input at phone size: `api click` calls the stage directly. The
  overlapping full screen button above is the one defect that would lose a tap, and the portrait layout puts buttons at
  different coordinates than the desktop one (New Game at about 195, 56 in CSS pixels), so a script written with desktop
  coordinates clicks empty space. `scripts/e2e-newgame` now uses `tap`, which asks the game where the button is.
- The phone layout logs one more known harmless error, `Failed to load: fallback_skin/title_bg_lq_portrait.png`, the portrait twin of the
  desktop one ([[webtest-harness]]).

## See also
[[selftest]] · [[unit-tests]] · [[build-pipeline]] · [[pick-up-work]]
