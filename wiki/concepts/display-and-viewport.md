---
type: concept
sources: [web/html/index.html, web/src/main/java/forge/web/DensityGraphics.java, web/src/main/java/forge/web/WebLauncher.java, web/src/main/java/com/github/xpenatan/gdx/teavm/backends/web/WebInput.java, patches/forge-web.patch, web/tools/webtest.py, scripts/e2e-common]
updated: 2026-10-06
tags: [display, phones, viewport, ios, audio]
---

# Display, viewport and pixel ratio

The canvas follows the visible viewport and is drawn at the device pixel ratio, capped at 2, while the game keeps laying out in CSS pixels.
Before 2026-10-06 it was drawn at CSS pixels and sized with `100vh`, which looked soft and was clipped under a phone browser's bars.

## What the owner saw on an iPhone (2026-10-06)
The live site in Chrome on an iPhone (WebKit underneath), portrait, device pixel ratio 3:
1. The picture was blurry: the backing store was 390x844 and the phone scaled it by 3.
2. The top and bottom were cut: the title screen's New Game button, the overworld HUD with the quest text, the VS screen's names and the reward screen's gold and button.

## Causes
- **Resolution.** gdx-teavm sizes the canvas from the window's client size, and only draws at the device ratio with `config.usePhysicalPixels`. That option also reports the physical size as `Gdx.graphics.getWidth()`,
  so Forge would lay out a 1170x2532 screen and every UI would shrink by 3. It was left off, which is why the earlier notes said the canvas is drawn at CSS pixels.
- **Height.** `index.html` gave the canvas `height: 100vh`. On iOS `100vh` is the height with the browser's bars hidden, so a game sized to it is taller than the visible area and its top and bottom sit under the bars.
  There was no handling of `visualViewport`, and the page could be dragged.
  The clipping can't be reproduced in Playwright's phone modes (they have no bars); the check there is that the canvas equals the visible viewport at every size, see [[e2e-tests]].

## How it works now
- `web/html/index.html` computes the visible size (`window.visualViewport` width and height, else `innerWidth` and `innerHeight`), sets the canvas's CSS size to it in pixels, and keeps it current: on `resize`, on `visualViewport` resize
  (it then fires a window `resize` too, which is the event gdx-teavm listens to), after `orientationchange`, and from a 500 ms timer for anything missed. While the page is pinch-zoomed (`visualViewport.scale` above 1.01) it keeps the old size.
  `window.forgeViewSize()` is that size and `window.forgePixelRatio()` the ratio.
- The page is pinned: `html` and `body` are `position: fixed`, `overflow: hidden`, `overscroll-behavior: none`, `touch-action: none`, and the viewport meta says `maximum-scale=1, user-scalable=no`.
  There is no `viewport-fit=cover`, so on a notched phone the browser keeps the page inside the safe area and the game never runs under the notch or the home indicator (the page background is black).
- **The pixel ratio** is `max(1, min(devicePixelRatio, 2))`. `?pixelratio=N` forces a value, for measuring.
- `forge.web.DensityGraphics` (a `WebGLGraphics` subclass that `WebLauncher` installs by overriding `WebApplication.createGraphics`) sets the backing store to the CSS size times the ratio and keeps `getWidth()` and `getHeight()` at the CSS size.
  `getBackBufferWidth()` and `getBackBufferHeight()` are the real pixels, and `resize` sets the GL viewport to them. libGDX's `HdpiUtils` (used by every `Viewport` and `ScissorStack`) converts between the two, so Forge's layout, which reads `getWidth()`, is unchanged.
- gdx-teavm's `WebInput` divides by the backing store when it maps a touch or click to a game coordinate. A shadow of it (`com/github/xpenatan/gdx/teavm/backends/web/WebInput.java`, four lines changed) divides by `Gdx.graphics.getWidth()` instead, so
  input stays in CSS pixels. See [[web-layer-mechanisms]].
- Forge patch, `ScreenUtil`: the screenshot texture and the thumbnail read are the size of the framebuffer, not the screen's (`Gdx.graphics.getBackBufferWidth()`), because `glCopyTexSubImage2D` and `glReadPixels` copy the buffer from its corner.
- Not changed: Forge's `Utils` takes its scale from `Gdx.graphics.getPpcX()`, which gdx-teavm derives from the full device ratio (3 on an iPhone), so UI sizes did not move.
  `glLineWidth` values are in buffer pixels, so the few lines Forge draws with a thickness are half as thick relative to the layout at ratio 2 (WebGL clamps wide lines anyway).

## Why a cap of 2 (measured 2026-10-06)
Chromium phone mode (390x844, scale 3), minified build, seed 1, software GL, new game to the overworld, GPU process RSS (the buffers live there) and renderer RSS:

| Pixel ratio | Backing store | GPU RSS, title | GPU RSS, overworld | Renderer RSS, title | Renderer RSS, overworld |
|---|---|---|---|---|---|
| 1 (before) | 390x844 | 212 MB | 364 MB | 544 MB | 642 MB |
| 2 (chosen) | 780x1688 | 259 MB | 371 MB | 532 MB | 627 MB |
| 3 | 1170x2532 | 313 MB | 436 MB | 528 MB | 622 MB |

The renderer numbers and the JS heap, textures and pixmap heap did not move beyond the run spread (texture memory +1.6 MB at 2, +5 MB at 3, from the screenshot texture, which is now the buffer's size).
Ratio 2 costs 47 MB of GPU process memory at the title and 7 MB at the overworld; ratio 3 costs 101 and 72 MB, and 3 shades nine times the pixels of ratio 1 where 2 shades four times. The game's art is pixel art drawn at integer scales of a 16 pixel grid and text from FreeType,
so at 2 the text is rasterised at twice the size and is sharp; 3 adds little a person could see. WebKit (Playwright's Linux WPE build, iPhone 13 descriptor) renderer RSS is
1890 MB before, 1829 MB at ratio 2 and 1810 MB at 3 at the title, and 2280, 1886 and 2246 MB at the overworld, which is the run-to-run spread of that engine ([[webkit-memory]]), not a trend.
The backbuffer at 2 is 5.3 MB per buffer (780x1688x4), and the game allocates no other framebuffers at screen size.

## Audio is off by default on touch devices
Music used to start at the first tap (`HowlMusic` retries when Howler unlocks). `WebLauncher` now writes `UI_VOL_MUSIC=0` and `UI_VOL_SOUNDS=0` into `forge/data/preferences/forge.preferences` before Forge reads it, when the device is a touch device
(`(pointer: coarse)` or `navigator.maxTouchPoints > 0`) and the file has neither volume. Forge's libGDX port plays by volume (`SoundSystem`: a volume below 1 means no music and no effects), and Forge's own save writes every preference,
so a player who has ever saved settings has both keys and keeps them; one who raises a slider in Settings (Adventure settings, "Adjust Music Volume" and "Adjust Sound Volume", far down the list) has it saved as usual. Desktop is unchanged.
A touch laptop is treated as a touch device, so its player starts muted as well. Checked on 2026-10-06: a fresh phone and iPhone profile has no `Howl` objects after the taps, tapping the music slider starts a track, and it plays again after a reload; desktop plays music from the title screen on.

## Open (needs a real iPhone, [[open-issues]])
- Whether the visual viewport follows the bars as it does in the code: `visualViewport` on iOS Chrome and Safari, bars collapsing on scroll (the page can't scroll, so they should stay), rotation, and the keyboard.
- Whether the backing store at 780x1688 plus WebKit's own memory stays inside iOS's tab limit.
- Touch input at ratio 2 on a real screen (the emulation maps taps correctly, [[e2e-tests]]).
- Landscape: nothing there was changed except the sizing.

## See also
[[screen-layout]], [[memory-budget]], [[web-layer-mechanisms]], [[gdx-teavm]], [[webtest-harness]]
