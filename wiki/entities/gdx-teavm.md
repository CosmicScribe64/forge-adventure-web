---
type: entity
sources: [NOTES.md, web/build.gradle.kts]
updated: 2026-10-06
tags: [gdx-teavm, upstream]
---

# gdx-teavm

The libGDX backend for [[teavm|TeaVM]] (github.com/xpenatan/gdx-teavm). The project uses
**1.6.1**, through the Gradle plugin `com.github.xpenatan.gdx-teavm`, and `gdx-freetype-web`
(FreeType compiled to wasm). Version 1.6.1 targets libGDX 1.14.2, exactly the version
[[forge|Forge]] uses.

## What it provides
The WebGL backend, `WebFiles`, asset loading, a `reflection()` config, an `emu.` package
substitution policy (used here for fixes, see [[web-layer-mechanisms]]), and `js {}` and `wasm {}`
targets. Native libGDX code (Gdx2D pixmaps, FreeType) runs in a wasm heap that **never
shrinks** ([[memory-budget]]).

## Bugs found (all fixed here with emu shadows, and worth reporting upstream)
- `AsyncResult` runs loading tasks more than once, leaking a decoded Pixmap per texture.
- `Gdx2DPixmapNative` copies the whole pixmap out of wasm memory after every draw.
- `gdx-freetype-web` leaks each font's data.
- `WebFiles` doesn't support `absolute`. That's by design, and the calls are redirected to the
  virtual file system.

Details and status are in [[bug-catalog]].

## Limitations
- `usePhysicalPixels` draws at the device ratio but then reports the physical size as `Gdx.graphics.getWidth()` (and its input divides by the backing store), so a layout that reads the width shrinks by the ratio.
  `DensityGraphics` and a `WebInput` shadow keep the game in CSS pixels instead ([[display-and-viewport]]). Worth reporting upstream.
- `@Emulate` doesn't pick up classes from this project.
- The dev server (`gdx_teavm_web_js_run`) seems to use fast analysis, so it is unusable here
  ([[build-pipeline]]).
- Its wasm target is blocked on TeaVM ([[stay-on-js-backend]]).
