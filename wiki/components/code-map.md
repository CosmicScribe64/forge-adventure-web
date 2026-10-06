---
type: component
sources: [web/src/main/java, web/html, web/tools, scripts]
updated: 2026-10-06
tags: [code, reference]
---

# Code map

This page shows where the project's own code lives and which concept each part serves. Paths are relative to
`web/src/main/java/` unless noted.

| Package / path | Contents | Concept |
|---|---|---|
| `forge/web/` | `WebLauncher` (entry point, counterpart of desktop `GameLauncher`), `WebDeviceAdapter`, `WebClipboard` | [[startup-and-loading]] |
| `forgeweb/compat/` | runtime helpers: `UiThread`, `MainThread*`, `FrameStats` | [[green-threads]] |
| | `JdkCompat`, `GdxCompat`, `GameCompat` (CallRedirector targets) | [[web-layer-mechanisms]] |
| | `Progress` (bar, and `[ttg]` stage marks), `LoadingScreen` | [[startup-and-loading]] |
| | `WfcPool` | [[world-generation]] |
| | `ByName`, `SerialHooks` (owns the hook class list; warns if a class has none) | [[reflection-on-teavm]] |
| | `StringCompat` (case-insensitive compares, R11) | [[startup-and-loading]] |
| | `ReadOnlySortedSet` | |
| `forgeweb/fs/` | `WebFileSystem`, `FileStore`, `WebVirtualFile`, `Node`, `Http`, `UserDataStore` | [[virtual-file-system]] |
| `forgeweb/teavm/` | compiler side: `ForgeWebPlugin` (registers the rest), `CallRedirector`, `Unsupported`, `WebReflection`, `ReachReport`, `ReflectAudit` | [[web-layer-mechanisms]], [[classic-code-pruning]] |
| `forgeweb/ForgeWebSubstitutionPolicy` | class substitution | [[web-layer-mechanisms]] |
| `forgeweb/shim/` | T-prefixed XML DOM/transform/SAX (`TMiniDom`, `TMiniXml`), `TFontUIResource` | [[web-layer-mechanisms]] |
| `forgeweb/stub/` | stand-ins: Guava `EventBus`, Sentry, tinylog, `Unsafe`, `ExceptionHandler`, `AssetsDownloader`, `FServerManager` | [[web-layer-mechanisms]], [[reflection-on-teavm]] |
| `forgeweb/selftest/` | `SelfTest` | [[selftest]] |
| `forgeweb/test/` | `WebTest` harness | [[webtest-harness]] |
| `forgeweb/worker/` | `WfcWorker` (separate build) | [[world-generation]] |
| `org/teavm/classlib/java/...` | classlib shadows: `lang.TObject` (borrowed monitors), `util.concurrent.*`, `util.concurrent.locks.*`, `util.zip.{TInflater,TDeflater,TDeflaterOutputStream,TZipFile}`, `io.TObject{Input,Output}Stream`+, `text.*`, `sql.*`, `net.*`, `awt.TFont` | [[web-layer-mechanisms]], [[green-threads]], [[saves]] |
| `com/badlogic/gdx/graphics/g2d/NinePatch`, `com/ray3k/tenpatch/TenPatchDrawable` | library class shadows with the nearest-filtering inset (R11) | [[screen-layout]] |
| `com/badlogic/gdx/utils/{IntMap,IntFloatMap,ObjectIntMap}`, `forgeweb/shim/FibHash` | libGDX map shadows whose `place` uses 32-bit arithmetic instead of a BigInt multiplication (2026-10-06) | [[bigint-churn]] |
| `com/github/xpenatan/gdx/teavm/backends/web/webaudio/howler/HowlMusic` | gdx-teavm class shadow: streams music with `html5: true`, retries after the first gesture, revokes the Blob URL (2026-10-04) | [[memory-budget]] |
| `emu/com/badlogic/gdx/...` | gdx-teavm emu shadows: `Gdx2DPixmapNative`, `freetype/FreeType`, `utils/async/AsyncResult`, Box2D subset | [[memory-budget]] |
| `forge/.../SelfTestAccess`, `WebTestAccess`, `WebTestStageAccess`, `com/github/.../textra/SelfTestAccess` | package-private access for tests | [[selftest]], [[webtest-harness]] |
| `web/html/index.html` | loader page, prefetch, IndexedDB bridge, canvas sizing | [[startup-and-loading]], [[screen-layout]] |
| `web/tools/` | `webtest.py`, `serve.py`, `find-seams.py`, `gen_tinylog_stubs.py`, `wfc-golden/` | [[webtest-harness]], [[world-generation]] |
| `web/build.gradle.kts` | gdx-teavm config, reflection patterns, entry points | [[build-pipeline]] |
| `scripts/` | build, serve, test, measure, play; `record-startup` (startup pack list, R11) | [[build-pipeline]], [[webtest-harness]], [[virtual-file-system]] |
| `patches/forge-web.patch` | all Forge changes | [[src-forge-web-patch]] |
| `out/` | screenshots, logs, reach reports, measurements (scratch; gitignored) | |
