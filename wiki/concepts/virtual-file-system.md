---
type: concept
sources: [NOTES.md#round-2, NOTES.md#round-3, NOTES.md#round-5, NOTES.md#round-7, NOTES.md#round-11, web/src/main/java/forgeweb/fs, scripts/build-webdata, scripts/record-startup, scripts/serve-web]
updated: 2026-10-01
tags: [fs, io, startup]
---

# Virtual file system

[[forge|Forge]] reads `res/` (466 MB) and writes user data through `java.io.File`, `java.nio`
and `Gdx.files.absolute`, and it does so synchronously and often on the UI thread.
[[gdx-teavm]]'s `WebFiles` doesn't support absolute paths. `forgeweb.fs` provides a browser
file system underneath all of these.

## Pieces

| Class | Role |
|---|---|
| `WebFileSystem` | the `java.io` file system. Game data is mounted **read-through** from a manifest (`path\tsize[\turl]` per line). Listings and sizes come from the manifest, and contents download on first read. Runtime writes stay in memory. Resolves relative `java.nio` paths (`./res`, Adventure's `Config.resPath`) against the user dir. |
| `WebVirtualFile`, `Node` | file and tree nodes |
| `Http` | **synchronous** GET (sync XHR, binary via the `x-user-defined` charset trick). It is used because the UI thread can't be suspended. It first takes bodies from `window.forgePrefetch` (see [[startup-and-loading]]). |
| `UserDataStore` | persists the user data folder (prefs, Adventure saves, decks) to **IndexedDB** via `window.forgeUserStore` in `index.html`. The page reads the stored files before the game starts, `restore()` puts them back, and later writes and deletes are mirrored in the background. |
| `GdxCompat` | `Gdx.files.absolute/external/getFileHandle` redirected here by [[web-layer-mechanisms\|CallRedirector]] |

## Data preparation (`scripts/build-webdata`, output in `web/webdata/`)

- **Manifest** of everything under `forge/forge-gui/res`.
- **`cardsfolder.zip`**: all card scripts in one zip (33,980 cards). Since Round 11 the entries
  are **stored, not deflated**, because inflating 34,000 entries in TeaVM-compiled JZlib took about
  10% of startup. The whole file is gzipped instead (see below), which makes it 5.8 MB on the wire
  instead of 15.8 MB.
- **Packs** (`PACK_DIRS`: `editions`, `tokenscripts`, `setlookup`, `formats`, `blockdata`):
  folders Forge reads whole, concatenated into `packs/*.pack`. The VFS downloads a pack and slices it. Since 2026-10-05 it forgets packs (and big
  read-only files such as the card zip) after 5 idle seconds and downloads them again if
  something reads them later ([[memory-budget]]). To find more candidates, count requests per folder with
  `docker logs --since 5m forge-web-serve`.
- **Startup pack** (Round 11): `packs/startup.pack` holds the other files a new game reads from
  page load to the first map (`web/startup-files.txt`, about 220 files, 17.6 MB gzipped). Fetched
  one by one, they took 144 blocking sync XHRs before the title screen alone, each a network
  round trip. `scripts/record-startup` regenerates the list. The VFS records every first read in
  `window.forgeFetched`, and the script keeps what isn't in a folder pack or the card zip.
  Rerun it after Forge updates or startup changes, then `scripts/build-webdata`.
- **Everything in `web/webdata/` is stored gzipped as `<name>.gz` only.** The page un-gzips it
  natively while downloading (`DecompressionStream`), and `Http` does the same with
  `GZIPInputStream` for anything not prefetched. Both check the gzip magic bytes, so a host that
  sends `.gz` with `Content-Encoding: gzip` also works. The game data download went from 23 MB to
  7.5 MB. That was measured before the startup pack, which only moves bytes that were fetched
  anyway.
- **Skipped**: `SKIP_SUFFIXES` (.xcf, .psd, .md, ...), `SKIP_FILES` such as `effects/demo.gif`,
  which decodes to one 11488x6480 texture and crashed the tab (Round 6), and `SKIP_DIRS`:
  `deckgendecks/` (7 MB of JDK-serialized deck-generation data that could never be read here,
  yet was downloaded at every start; Round 11).

`scripts/serve-web` serves the app at `/`, `res` at `/forge/res/` and webdata at
`/forge-data/`, with caching headers from `web/tools/serve.py`. Since Round 11 it uses HTTP/1.1
keep-alive, because a new connection for each request through Docker's port forwarding sometimes
stalled a request for a minute in headless runs.

## Zip support needed two classlib fixes
`TInflater` (TeaVM threw on `Z_BUF_ERROR` where the JDK returns 0) and `TZipFile` (no dummy
byte after raw deflate data, giving an `EOFException`). See [[bug-catalog]].

## Planned (PLAN Phase 6)
Async fetch with `@Async` suspension instead of sync XHR, so prefetching becomes optional, and a
service worker that caches app.js, the packs and res files. See [[plan-phases]].

## See also
[[saves]] · [[selftest]] (checks: cardsfolder.zip, packs, `File.list` filter, `./res` resolution)
