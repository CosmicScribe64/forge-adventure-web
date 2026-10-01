---
type: source
sources: [PLAN.md]
updated: 2026-10-01
tags: [source, plan]
---

# Source: PLAN.md

PLAN.md is the architecture plan, written on 2026-09-29 after an architecture review. It has seven phases,
each with a measurable exit criterion, plus a Status section and a "Next" list taken from a
watched play-through. It is a living document.

- Principle: root-cause fixes, and a stopgap only as a labelled stepping stone.
- The phases and their current state are tracked in [[plan-phases]].
- The "Next" items are tracked in [[open-issues]].

Main wiki pages fed by it: [[plan-phases]], [[open-issues]], [[stay-on-js-backend]],
[[classic-code-pruning]], [[world-generation]], [[memory-budget]], [[webtest-harness]].

One discrepancy worth knowing: PLAN Phase 2 says Classic entry points are stubbed "through
ForgeWebSubstitutionPolicy". In the code, it is done by a separate TeaVM transformer,
`forgeweb.teavm.Unsupported`, which replaces method bodies (see [[classic-code-pruning]]).
