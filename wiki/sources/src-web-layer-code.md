---
type: source
sources: [web/src/main/java, web/html/index.html, web/build.gradle.kts, web/tools, scripts]
updated: 2026-10-01
tags: [source, code]
---

# Source: the web layer code

These are the project's own sources. The code is read to check and deepen claims from
NOTES.md, not ingested line by line. As of 2026-09-29 there are 144 files under `web/src`. The
largest are `WebTest.java` (911 lines), `SelfTest.java` (875), `TObject.java` (597, a TeaVM
fork of one class) and `webtest.py` (461).

Class-level doc comments are good and are the best place to confirm how a mechanism works. The
package layout is summarised in [[code-map]].

Things found in code that NOTES.md doesn't spell out:
- `forgeweb.teavm.Unsupported` exists and is how Classic-only methods are stubbed (throw or
  no-op): XStream serializers, Classic home screens, the bulk CDN download prompt. See
  [[classic-code-pruning]].
- `build.gradle.kts` has three entry points chosen by environment variable:
  `forge.web.WebLauncher` (game), `SELFTEST=true` gives `forgeweb.selftest.SelfTest`, and
  `WORKER=true` gives `forgeweb.worker.WfcWorker`. See [[build-pipeline]].
- The desktop app's preview config (`.claude/launch.json`, "forge-web") runs
  `scripts/serve-web` on port 8090.
