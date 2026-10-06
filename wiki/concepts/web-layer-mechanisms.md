---
type: concept
sources: [NOTES.md#how-the-web-layer-plugs-in, NOTES.md#findings, web/src/main/java/forgeweb/teavm, web/src/main/resources/META-INF/services]
updated: 2026-10-06
tags: [teavm, architecture]
---

# Web layer mechanisms

Forge needs APIs that [[teavm|TeaVM]]'s class library lacks. The first compile found 85
distinct missing JDK APIs across 331 call sites. The project closes that gap without forking
TeaVM, using six mechanisms. Picking the right one is the first step of [[add-missing-api]].

| Mechanism | How it works | Where | Use it for |
|---|---|---|---|
| **Classlib shadow** | TeaVM maps `java.X.Foo` to `org.teavm.classlib.java.X.TFoo`. Project classes come first on the classpath, so a same-named class here wins, even over one TeaVM already has. | `web/src/main/java/org/teavm/classlib/java/...` | missing or broken JDK classes: concurrency, locks, zip, object streams, text, sql, net, `TObject` |
| **SubstitutionPolicy** | `forgeweb.ForgeWebSubstitutionPolicy` (registered in `META-INF/services`) swaps whole classes | `forgeweb/shim/...` (T-prefixed), `forgeweb/stub/<orig package>/<Name>` | non-`java.*` APIs (XML DOM, `javax.swing`), and whole-class web stand-ins (Sentry, tinylog, `sun.misc.Unsafe`, `ExceptionHandler`, `AssetsDownloader`, `FServerManager`, Guava `EventBus`) |
| **gdx-teavm emu** | [[gdx-teavm]]'s policy maps `com.badlogic.gdx.X` to `emu.com.badlogic.gdx.X` | `web/src/main/java/emu/...` | pure-Java Box2D subset; fixed `Gdx2DPixmapNative`, `AsyncResult`, `FreeType` |
| **CallRedirector** (TeaVM plugin) | rewrites individual call sites to static helpers; the receiver becomes the first argument. Also handles any-owner matches, constructor overloads that drop the last argument, and owner renames. | `forgeweb/teavm/CallRedirector.java`, helpers in `forgeweb/compat/JdkCompat`, `GdxCompat`, `GameCompat`, `Progress` | single missing or wrong methods: `Throwable.addSuppressed`, `Runtime.availableProcessors`, `Thread.sleep`, `UUID.randomUUID`, `String.format`, `Gdx.files.absolute`, ... |
| **Unsupported** (TeaVM plugin) | replaces the bodies of named Forge methods with a throw or a no-op | `forgeweb/teavm/Unsupported.java` | cutting Classic-only code out of app.js ([[classic-code-pruning]]) |
| **Library class shadow** (R11) | a copy of a third-party class under its own name in `web/src`; project classes come first on the TeaVM classpath. Change only a marked block, and note the pinned version | `web/src/main/java/com/badlogic/gdx/graphics/g2d/NinePatch.java` (libGDX 1.14.2), `com/ray3k/tenpatch/TenPatchDrawable.java` (TenPatch 5.2.3), `com/github/xpenatan/gdx/teavm/backends/web/WebInput.java` (gdx-teavm 1.6.1, input in logical pixels, [[display-and-viewport]]), `com/badlogic/gdx/utils/{IntMap,IntFloatMap,ObjectIntMap}.java` (libGDX 1.14.2, `place` hashes without a 64-bit multiply, [[bigint-churn]]) | bugs in libraries that aren't `java.*` and aren't in gdx-teavm's emu (texture seams, [[screen-layout]]) |
| **app.js post-build patch** (R11) | `web/build.gradle.kts` rewrites a snippet of the generated JavaScript after every JS build, streaming the file; the build fails if the snippet is missing | `web/build.gradle.kts` (`Long_fromNumber`, and `long` literals as BigInt literals, [[bigint-churn]]) | TeaVM runtime JS (`long.js`, `runtime.js`), which TeaVM reads through its own class loader and so can't be shadowed |
| **Forge patch** | edits to Forge source | `patches/forge-web.patch` | behaviour changes, performance, layout; upstreamable ([[forge-patches-not-fork]]) |

Plus **reflection metadata**: `forgeweb.teavm.WebReflection`, a `ReflectionSupplier`. See
[[reflection-on-teavm]].

## Rules learned the hard way

- **Shims only call shims.** Inside a substituted class (anything under `forgeweb/shim`,
  `forgeweb/stub` or `org/teavm/classlib`), the class's own shim types are renamed back to the
  originals. So a shim must never call a plain helper whose signatures mention shim types.
  That is why the XML parser lives in `forgeweb/shim/javax/xml/parsers` as
  `TMiniDom`/`TMiniXml`.
- **The SubstitutionPolicy can't replace a class TeaVM's classlib already has**, so classlib
  shadowing is used for those (`TInflater`, `TZipFile`, `TObject`, `TLinkedBlockingDeque`).
- gdx-teavm's `@Emulate` annotation does not pick up classes from this project, so don't use it.
- A shadowed TeaVM class is a fork, and `TObject.java` says "keep in sync with TeaVM on upgrades".
  See [[teavm]] for the list to re-check when TeaVM is upgraded.
- `web/tools/gen_tinylog_stubs.py` generates the tinylog stand-ins.
- **TeaVM's runtime JavaScript can't be shadowed.** A resource copy in `web/src/main/resources` is
  ignored, because `RuntimeRenderer` loads it through TeaVM's own class loader. Patch the output
  instead, and make the patch fail loudly when the snippet changes. TeaVM ends a definition with
  `;` or `,` depending on the output, so match the expression, not the punctuation (R11).
- Library shadows and post-build patches are forks too, so re-check them when upgrading libGDX,
  TenPatch or TeaVM.

## Original gap, by cause (first compile, 2026-09-28)

| Cause | Resolution |
|---|---|
| Crash reporting, logging (Sentry, tinylog) | stubs |
| Networking, downloads, multiplayer | stubs; card art via fetch ([[scryfall]]) |
| Simple concurrent collections | single-threaded classlib shadows |
| Game-thread and input sync (latches, futures, executors, blocking deques) | the real risk; emulated on green threads ([[green-threads]]) |
| Saves (Java serialization) | own object streams ([[saves]]) |
| XML DOM | `TMiniDom` shim |
| Text (`BreakIterator`, `Collator`, `Normalizer`) | simple shadows |

All of these were handled by Round 2, and the build compiles with no missing APIs.

## See also
[[code-map]] · [[teavm-gotchas]] · [[update-forge]]
