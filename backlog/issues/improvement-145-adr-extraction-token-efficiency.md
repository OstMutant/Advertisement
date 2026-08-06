# improvement-145: On-demand ADR full-text extraction tool — the "L3" token-efficiency gap

**Type:** improvement — new small tool, no UI change.
**Module:** `scripts/architecture/md-to-decisions-json.js`, `docs/ai/README.md`.
**Priority:** 🔴 highest (explicit user request — first to execute).
**When:** independent, no blockers.

## Problem

`scripts/architecture/DECISIONS.md` has a long-standing "Open goals" entry ("AI-layer L3
(Rule/Intent) artifact") identifying this exact gap, never implemented. Investigated directly
before filing this issue, not assumed:

- `docs/ai/adr-index.md` already exists and works well — a flat, mechanically generated list
  (id/module/status/title) so Claude never has to grep every `DECISIONS.md` blind. `docs/ai/
  context-loading.md` already prescribes "filter adr-index.md by module first" for the relevant
  task types.
- The gap is **after** that step: once Claude knows it needs, say, `ADR-042` and `ADR-057` from a
  specific module's `DECISIONS.md`, there is no cheap way to read just those two — only the whole
  file (via `Read`) or a manual grep-for-line-number-then-offset/limit dance. Real file sizes,
  confirmed by direct measurement, not estimated:
  ```
  marketplace-app/DECISIONS.md        3580 lines / 72 ADRs
  scripts/architecture/DECISIONS.md   1549 lines / 23 ADRs
  platform-commons/DECISIONS.md        804 lines / 27 ADRs
  ```
  Reading a 3580-line file to get 2 ADRs (~100-150 lines of actual relevant text) is a real,
  measurable waste, not a hypothetical one.

## Suggested fix

No new generated static file (would go stale, need its own freshness gate). Instead: extend the
already-existing `scripts/architecture/md-to-decisions-json.js` (it already parses `## ADR-NNN:
Title` blocks into `{id, title, status, body}` objects for the `--stdout` mode the generator
itself uses) with a new **on-demand extraction mode**:

```
node scripts/architecture/md-to-decisions-json.js --extract <module> <ADR-NNN>[,<ADR-NNN>...]
```

- Reads the real `<module>/DECISIONS.md` at call time (always fresh — no staleness possible, no
  freshness gate needed, unlike a generated file).
- Filters the already-parsed `.adrs` array down to just the requested id(s).
- Prints **raw markdown** (the ADR's own `## ADR-NNN: Title` heading + full body), not JSON — JSON
  would just be escaping overhead Claude has to mentally undo; the whole point is a lean read.
- Reuses 100% of the existing parsing logic in this file — zero duplicated markdown-parsing code.

## Not in scope

- No change to `adr-index.md`/`context-loading.md` — they already correctly point at this gap
  (context-loading.md's "filter adr-index.md by module first" guidance stays exactly as-is; this
  tool is the next step after that guidance, not a replacement for it).
- No new CI freshness gate — an on-demand extraction tool that reads the real file at call time
  cannot go stale by construction, unlike a generated snapshot.
- No change to the human-facing `architecture-map.html` ADR popup (`openAdrPopupForIntent`/
  `openAdrPopupForAdr`) — that already reads full ADR bodies embedded in `architecture-model.json`
  for a person clicking through the UI; this issue is specifically about Claude's own token cost
  when working in this repo, a different consumer with a different cost profile.

## Related

- `scripts/architecture/DECISIONS.md` "Open goals" — the "AI-layer L3 (Rule/Intent) artifact" entry
  this issue directly addresses (mark done there once implemented, per this project's own
  "Open goals" convention).
- `docs/ai/README.md` — document the new extraction mode there once built, in the existing
  file/why-it-exists/where-it-fits/when-to-consult/how-it-stays-fresh table format.
