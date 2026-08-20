# improvement-162: `docs/architecture/` directory reorganization — architecture-doc.sh relocation + data/ split

**Type:** improvement
**Module:** `docs/architecture/architecture-doc.sh`/`.bat` (moved from `scripts/`),
  `docs/architecture/*`, `docs/architecture/scripts/generate-architecture-model.sh`,
  `docs/architecture/scripts/check-architecture-model-freshness.sh`,
  `docs/ai/scripts/check-hardcoded-counts.sh`, `.claude/commands/sync-docs.md`, `scripts/README.md`
**Priority:** Top 🟡
**When:** independent, no blockers

## Background

Filed as a scope-TBD placeholder with two candidate directions. Both are now resolved:

1. ~~Root `scripts/*.sh`/`*.bat` `infra-doc-standards` rollout~~ — **done**, confirmed via repeated
   `infra-doc-standards` skill runs over `scripts/` this same session (all 20 root files' headers
   verified accurate by 3 independent fresh-agent reviews; `scripts/README.md` fully rewritten;
   real bugs found and fixed along the way — stale paths in `collect-code.bat`, hardcoded
   sandbox-only `/app/...` paths in `playwright.bat`/`architecture-doc.bat`). Not this issue's
   concern going forward.
2. The generator-refactor direction narrowed, through direct discussion, into a concrete,
   scoped structural reorganization (below) — not the originally-speculated full heredoc HTML/CSS
   rewrite of `generate-architecture-model.sh`, which stays out of scope here.

## Problem

`scripts/architecture-doc.sh`/`.bat` is a root-level `scripts/` entry point whose entire job is
delegating into `docs/architecture/scripts/generate-architecture-model.sh` — a different directory
tree, structurally unrelated to `scripts/`'s own operational (build/deploy/test) family. Unlike
`playwright.sh` (a genuine root-level entry point for an operational sibling directory),
`docs/architecture/scripts/` is already its own separate, established documentation tree (its own
`SCRIPT_GROUP_DIRS` card in the architecture-map generator, alongside `docs/ai/scripts/`) — so
keeping its entry point artificially in `scripts/` doesn't fit the same pattern. Confirmed the real,
documented invocation path (`.claude/commands/sync-docs.md`) already calls
`bash docs/architecture/scripts/generate-architecture-model.sh` directly, bypassing
`scripts/architecture-doc.sh` entirely — the root-level wrapper is not the actual canonical
entry point in practice.

Separately, `docs/architecture/` itself mixes its one real human-facing output
(`architecture-map.html`) at the top level with 4 generator-internal/input files
(`architecture-model.json`, `README.md`, `runtime-notes.md`, `arch-embed-index.md`) — no separation
between "the deliverable" and "the data that produces it."

## Fix — applied

1. **Moved** `scripts/architecture-doc.sh` → `docs/architecture/architecture-doc.sh`,
   `scripts/architecture-doc.bat` → `docs/architecture/architecture-doc.bat` — **one level above**
   `docs/architecture/scripts/`, alongside `architecture-map.html` (the two things a human directly
   interacts with: the deliverable and the "regenerate it" entry point), not inside `scripts/`
   alongside the generator's own internal logic. Internal calls to `scripts/generate-architecture-model.sh`/
   `scripts/check-architecture-model-freshness.sh`/`scripts/screenshot-architecture-map.sh` resolve
   via `dirname "$0"/scripts/...`.
2. **Created** `docs/architecture/data/`, moved `arch-embed-index.md`, `architecture-model.json`,
   `README.md`, `runtime-notes.md` into it. `architecture-map.html` and `architecture-doc.sh`/`.bat`
   are now the only two top-level entries in `docs/architecture/`; `scripts/` (the generator's own
   logic) stays at `docs/architecture/scripts/`, unmoved.
3. **Updated every real reference** to the 4 relocated data files' old paths:
   - `docs/architecture/scripts/generate-architecture-model.sh` (13 occurrences + the now-obsolete
     "architecture-doc.sh has no matching subfolder" counterexample comment's stale path + removed
     `architecture-doc.sh`/`.bat` from `SCRIPT_GROUP_FILE_ORDER["scripts"]` — they were briefly
     added to `["docs/architecture/scripts"]` during an intermediate wrong-location attempt, then
     removed again once the correct one-level-up location was clarified)
   - `docs/architecture/scripts/check-architecture-model-freshness.sh` (8 occurrences)
   - `docs/ai/scripts/check-hardcoded-counts.sh` (1 occurrence, an exclusion pattern)
   - `.claude/commands/sync-docs.md` (3 mentions)
   - `scripts/README.md` (removed the `architecture-doc.sh` / `.bat` Entry points row entirely —
     no longer a `scripts/` entry point at all, at either location)
4. **Not touched**: `docs/architecture/scripts/DECISIONS.md` (append-only historical record, old
   paths stay as written) and every `backlog/completed/` mention (same reason).
5. Regenerate `architecture-model.json`/`architecture-map.html` via the relocated
   `architecture-doc.sh` afterward, to verify the new layout end to end.

**Known gap, not fixed here (flagged, needs a design decision):** `docs/architecture/` itself is
not a `SCRIPT_GROUP_DIRS` entry in `generate-architecture-model.sh` (only
`docs/architecture/scripts` and `docs/ai/scripts` are) — so `architecture-doc.sh`/`.bat`'s own
headers, now living at `docs/architecture/` directly, render nowhere in `architecture-map.html`'s
self-documentation. Whether `docs/architecture/` should become its own tracked card (and if so,
whether `architecture-map.html`/the `data/` files belong alongside it) is a follow-up design
question, out of scope for this mechanical move.

## Related

- `improvement-155` — repo-wide `infra-doc-standards` rollout (its root-`scripts/` item, now done
  via this session's skill runs).
- `docs/architecture/scripts/DECISIONS.md` — generator mechanism ADRs.
