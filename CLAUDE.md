# Forge Adventure Web

Forge's Adventure mode (libGDX), compiled to JavaScript with gdx-teavm and TeaVM 0.15 so it runs
in a browser. `NOTES.md` is the lab notebook and `PLAN.md` is the plan. Every change to Forge is
in `patches/forge-web.patch`.

## Project wiki (`wiki/`)
`wiki/` is a maintained, cross-linked knowledge base about this project, kept as an Obsidian
vault. **Read `wiki/SCHEMA.md` before editing it.** You own the wiki, so keep it current.

- To answer a question about the project, read `wiki/index.md` first, then the relevant pages,
  then the code and NOTES.md for anything they don't cover. File answers worth reusing under
  `wiki/analyses/`.
- After a session that fixed or learned something (a bug, a decision, new numbers, a changed
  mechanism), fold it into the wiki with SCHEMA.md's ingest workflow, append an entry to
  `wiki/log.md`, and run `wiki/tools/lint`.
- NOTES.md, PLAN.md and the code are sources. Wiki work never edits them.
