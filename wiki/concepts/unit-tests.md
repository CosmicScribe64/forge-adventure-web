---
type: concept
sources: [web/src/unit/java/forgeweb/shim/FibHashTest.java, web/tools/test_jsbody_names.py, scripts/unit-test, web/build.gradle.kts, web/src/unit/java/forgeweb/fs/FileStoreTest.java, web/tools/test_latin1_js.py, .github/workflows/ci.yml]
updated: 2026-10-06
tags: [testing, ci]
---

# Unit tests

Fast tests that run on the plain JVM and in Python, with no browser and no TeaVM compile. They
cover logic that was otherwise exercised only through slow browser runs ([[selftest]], [[webtest-harness]]).
`scripts/unit-test` runs them locally and in CI. As of 2026-10-05 there are 27 JUnit tests, 15
Python tests and 11 Forge tests (17 Python tests from 2026-10-06, with `test_jsbody_names.py`).

## Running them
- `scripts/unit-test` runs the Python tests and the web module's JUnit tests (Gradle task
  `unitTest`, in the build container). The first run downloads JUnit, later runs take seconds.
- `scripts/unit-test forge` runs Forge's own TestNG tests (needs `scripts/update-forge` first).
  `all` runs both groups.
- CI runs the first group right after the wiki lint, before any long step, and the Forge tests
  after `scripts/build-forge-libs`. Locally the whole set takes about 30 s with warm caches.

## The file system tests
`FileStore` holds the part of the [[virtual-file-system]] with no browser in it: the node tree,
manifest parsing, packs, lazy downloads, path canonicalisation and the trim policy.
`WebFileSystem` extends it and adds the `VirtualFileSystem` methods, the XHR download and the
`setTimeout` timer. `FileStore.Host` is the whole interface to the browser (`fetch`, `now`,
`schedule`, `recordFetch`). In the tests `FakeHost` serves canned bytes, counts downloads, and
runs timers only when `advance(ms)` moves its clock. Behaviour is unchanged.

The Gradle `unit` source set compiles only `FileStore`, `Node`, `FibHash` and the tests from `web/src`, so it
needs neither Forge's jars nor TeaVM. `WebVirtualFile` implements TeaVM's `VirtualFile` and is
not covered; `touch` and `ensureLoaded`, which it calls, are.

`FibHashTest` (2026-10-06) checks that `forgeweb.shim.FibHash.place` equals libGDX's `(int)(item * 0x9E3779B97F4A7C15L >>> shift)` for every shift from 33 to 63 and 20,000 random keys each ([[bigint-churn]]).

The tests include the two bugs of early October 2026:
- A pack was dropped while idle, before all its files had been read, so a new game downloaded it
  again. The test reads two of three files, waits ten minutes, reads the third, and expects one
  download.
- The 27 MB card zip was read after the trim. It must reload once and then stay loaded, however
  often it is read. The test touches it 50 times after a trim and expects two downloads in total.

Others: a pack is dropped exactly when its last file is read (and a file read twice counts once),
big read-only files go after 5 s idle, `touch` postpones that, one timer is pending at most, big
files that were written are never dropped, a failed download is not counted and can be retried,
and the manifest forms (default URL, own URL, pack offset).

## The latin1-js.py tests
`web/tools/test_latin1_js.py` uses `unittest`. It checks escapes of characters above U+00FF,
astral characters as surrogate pairs, Latin-1 left alone, refusal of a character after a
backslash (and no change to the file), and source map columns: they shift by the extra length of
the escapes before them, an astral character counts as two UTF-16 units, and other lines and maps
without escapes stay unchanged.

## The test_jsbody_names.py tests
`web/tools/test_jsbody_names.py` reads every `@JSBody` in `web/src` and fails if its script declares (var, let, const, function or arrow parameter) a single letter among the
first parameter-count-plus-one letters of the alphabet. A minified build renames the parameters of a `@JSBody` to those letters and leaves the script alone, so such a variable hides a
parameter. This was a real bug in `Http.takePrefetched`, `UserDataStore.takeStored` and `TDeflaterOutputStream` ([[bug-catalog]], [[webkit-memory]]).

## Forge's tests
Forge has three TestNG classes outside the desktop module: `ManaCostBeingPaidTest` and
`AbilityKeyTest` in forge-game (3 tests) and `PlayerControllerHumanShould` in forge-gui (8
tests). They run offline once Maven has fetched surefire and its TestNG provider, in about 25 s
when the modules are compiled, and they pass on the patched tree. They are relevant because
the patch touches game and GUI code.

## What stays in SelfTest
Monitors, executors and threads on the TeaVM scheduler, Inflater and Deflater natives, long casts,
reflection metadata, Pixmap, XHR and IndexedDB behaviour, anything that needs TeaVM itself.

## See also
[[open-issues]] · [[build-pipeline]]
