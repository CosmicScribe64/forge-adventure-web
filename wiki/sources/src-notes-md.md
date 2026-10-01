---
type: source
sources: [NOTES.md]
updated: 2026-10-01
tags: [source]
---

# Source: NOTES.md

NOTES.md is the project's running lab notebook, at the project root. Coding sessions append a "Status
(round N)" or dated section after each push. It is the primary source for most of this wiki.
**Living document:** see `log.md` for how far it has been ingested.

## Structure (as ingested 2026-09-29, ~463 lines)

| Section | Summary | Main wiki pages |
|---|---|---|
| Layout / Reproduce | repo layout, Docker build commands | [[build-pipeline]], [[code-map]] |
| Findings (first compile, 2026-09-28) | 85 missing JDK APIs grouped by cause, with a fix per group | [[web-layer-mechanisms]] |
| Rounds 2-4 | compiles; browser VFS; stale current-thread bug; addSuppressed and Inflater bugs; splash draws | [[virtual-file-system]], [[bug-catalog]] |
| Round 5 | all 33,980 cards load; CountDownLatch hang; ZipFile; LDA; relative nio paths; Json reflection | [[bug-catalog]], [[single-processor]] |
| Round 6 | start menu; demo.gif texture crash; 60 s idle gap | [[startup-and-loading]] |
| Round 7 | Adventure start screen; packs; custom index.html with prefetch; ByName rules | [[startup-and-loading]], [[reflection-on-teavm]] |
| Round 8 (overnight) | the full loop from the tutorial through the overworld to a duel and its result; UiThread; saves; reflection budget; EventBus; synchronized isGameOver | [[green-threads]], [[saves]], [[reflection-budget]], [[bug-catalog]] |
| Baseline (2026-09-29) | first `scripts/measure` numbers | [[metrics]] |
| Backend decision | wasm GC spike fails in TeaVM coroutine transform | [[stay-on-js-backend]] |
| Round 9 | AsyncResult texture leak; WFC patches and workers (6.6 s); borrowed monitors; reach/reflect reports | [[memory-budget]], [[world-generation]] |
| Round 10 | loading progress; ViewLayout screen shapes; duel-start lag | [[startup-and-loading]], [[screen-layout]] |
| Review (2026-09-29) | adversarial review fixes; FreeType leak; known-unfixed list | [[bug-catalog]], [[open-issues]] |
| Round 11 | Load screen and silently skipped save hooks; time to gameplay (`[ttg]`, gzip data, StringCompat, fonts, startup pack, `--latency`); cave seams (texel inset) | [[saves]], [[startup-and-loading]], [[virtual-file-system]], [[screen-layout]], [[metrics]] |
| Debugging a stuck run, Fast checks, How the web layer plugs in, Updating Forge | procedures and reference | [[debug-stuck-run]], [[selftest]], [[web-layer-mechanisms]], [[update-forge]] |

## Reliability notes

- Numbers are mostly from headless Chromium with software GL (SwiftShader). Frame rate and
  GPU memory there don't match a real GPU. The notes say so where it matters.
- Earlier rounds are sometimes superseded by later ones (for example, world generation time went from
  35-45 s in Round 8, to 44-70 s at the baseline under load, to 6.6 s in Round 9). This wiki
  keeps the latest value and notes the history in [[metrics]].
