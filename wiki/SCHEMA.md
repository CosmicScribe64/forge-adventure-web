# Wiki schema

This page describes how the wiki is structured and maintained. The LLM owns every page here
except `raw/`. The human curates sources, asks questions and decides what to emphasise. Read this
page before touching the wiki.

## What the wiki is for

The wiki is a compiled, cross-linked picture of the port of Forge Adventure to the browser
(the project root is `..`). It covers how the web layer works, why it is built that way, what
broke and how it was fixed, what is measured, and what is still open. It sits between the raw
sources and the person asking questions, so answers don't have to be worked out from NOTES.md
and the code every time.

## Layers

| Layer | Where | Who writes it |
|---|---|---|
| Raw sources | `../NOTES.md`, `../PLAN.md`, `../patches/forge-web.patch`, `../web/`, `../scripts/`, `raw/` (clipped articles, upstream issues, transcripts, screenshots) | the human, or coding sessions. **Never edited during wiki work.** |
| Wiki | everything in `wiki/` except `raw/` | the LLM |
| Schema | this file | human and LLM together |

NOTES.md and PLAN.md are *living* sources, because coding sessions add to them. When ingesting,
record in `log.md` which part was read (section headings or "through Round N") so the next
ingest knows where to resume.

## Layout

```
wiki/
  SCHEMA.md      this file
  index.md       catalog of every page, by category, one line each (read first when answering)
  log.md         append-only timeline of ingests, queries, lints
  overview.md    the one-page picture; start here
  sources/       one summary page per ingested source
  concepts/      how a part of the system works (the bulk of the wiki)
  components/    our own code, by package: what lives where and which concept it serves
  entities/      external projects and services (TeaVM, gdx-teavm, Forge, ...)
  bugs/          defects found, in whose code, fix, upstream status
  decisions/     one page per decision: context, options, choice, how to revisit
  status/        plan phases, metrics over time, open issues
  howto/         procedures (update Forge, add a missing API, debug a hang)
  analyses/      answers to questions worth keeping (filed from queries)
  raw/           human-supplied sources; the LLM reads, never edits
  tools/lint     health check script
```

## Page conventions

- File names: lowercase-kebab, unique across the whole wiki, because links use the name, not
  the path.
- Links: Obsidian `[[page-name]]` / `[[page-name|label]]`. Link the first mention of any page
  in a page body; don't over-link repeats.
- Code references: backticked paths relative to the project root, e.g.
  `web/src/main/java/forgeweb/compat/UiThread.java`, and class names. (Vault links can't reach
  outside `wiki/`.)
- Every page starts with frontmatter:
  ```yaml
  ---
  type: concept | component | entity | bug | decision | status | howto | source | analysis | overview
  sources: [NOTES.md#round-8, web/.../UiThread.java]   # where claims come from
  updated: 2026-09-29
  tags: [threads, teavm]
  ---
  ```
  Bug pages add `owner: teavm | gdx-teavm | forge | ours` and
  `upstream: not-reported | reported | fixed-upstream | n/a`.
  Decision pages add `status: active | superseded | revisit`.
- Then `# Title`, a summary of 1-3 sentences, and the body. Keep pages focused, and split a page
  that grows past about 150 lines.
- State facts with their date or round when they may change ("as of 2026-09-29", "Round 9").
  Numbers always carry their conditions (headless / real GPU, seed, machine).
- Contradictions: never silently overwrite. If a new source disagrees with a page, update
  the claim and add a `> [!note] Superseded` callout with the old value, date and source.
  If the disagreement is unresolved, add `> [!warning] Conflict` and list it in
  `status/open-issues`.
- End each page with `## See also` when there are related pages not already linked.

## Workflows

### Ingest (a new source, or new sections of NOTES.md / PLAN.md)
1. Read the source. For NOTES.md, read only the sections after the last ingest point in
   `log.md`.
2. Tell the human the main points in a few lines, and ask what to emphasise if that's unclear.
3. Write or update `sources/<name>.md`.
4. Update every affected concept, component, bug, decision and status page (often 5-15 pages).
   A new bug goes in `bugs/` and a new decision in `decisions/`. New numbers go in
   `status/metrics`, PLAN status changes in `status/plan-phases`, and new "known, not fixed"
   items in `status/open-issues`.
5. Update `index.md` and `overview.md` if the big picture moved.
6. Append to `log.md`. Run `tools/lint`.

### After a coding session (the most common ingest)
When a session fixed something or learned something, fold it in the same way, using the diff
or the NOTES entry as the source. Verify against code when a page names a file or class.

### Query
1. Read `index.md`, then the relevant pages. Go to the raw sources and code only for gaps.
2. Answer with links to wiki pages (and code paths).
3. If the answer is reusable (a comparison, an explanation that took synthesis), file it as
   `analyses/<name>.md`, link it from related pages and `index.md`, and log it.

### Lint
Run `tools/lint` (broken links, orphans, pages missing from index, missing frontmatter).
Then read for contradictions between pages, claims that newer NOTES rounds or code have made
stale, concepts mentioned without a page, and open issues that were actually fixed. Report the
findings, fix the mechanical ones, and log the pass.

## Log format

`## [YYYY-MM-DD] <ingest|update|query|lint|schema> | <title>` followed by a few bullets
(what was read, pages touched). `grep "^## \[" wiki/log.md | tail -5` shows recent activity.
