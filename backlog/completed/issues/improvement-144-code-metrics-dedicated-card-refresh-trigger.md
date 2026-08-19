# improvement-144: Code Quality + ADRs screens, opt-in Sonar/ArchUnit flags, tooling reorg

**Type:** improvement — completed.
**Module:** `scripts/architecture/generate-architecture-model.sh`, `docs/architecture/architecture-map.html`.
**Priority:** 🔴 highest (explicit user request).
**Status:** Done — all steps below landed. The one piece of the original scope not implemented
(the companion-server refresh trigger) was split out to `improvement-146` once everything else was
done, so this file could close.

## Step 0: opt-in Sonar/ArchUnit, default off — empty sections already hide themselves — Done

**Part 1 — default must become "don't fetch".** `ensure_sonar_fresh`, `sonar_metrics_json`, and
`archunit_metrics_json` all ran on every single `generate-architecture-model.sh` invocation before
this step, with no flag to skip them — `ensure_sonar_fresh` could trigger a multi-minute SonarQube
rescan just from running the generator. Fix: flag parsing near the top of the script (same
`for arg in "$@"` shape `integration-tests/run.sh` already uses for `--sandbox`/`--no-check`):
`--with-sonar` sets `WITH_SONAR=1`, `--with-archunit` sets `WITH_ARCHUNIT=1`, neither set by
default. `ensure_sonar_fresh` and both metrics functions now only run when their flag is passed;
`sonarMetrics`/`archUnitMetrics` are `null` in the generated model otherwise.
**Consequence for the committed baseline:** `docs/architecture/architecture-model.json`/
`architecture-map.html` regenerated with the new default before committing, resetting the
committed Sonar/ArchUnit numbers to `null` — richer data stays available on demand
(`--with-sonar --with-archunit` locally) but isn't baked into what CI/the freshness gate compares
against by default.

**Part 2 — hiding empty sections: already correct, confirmed by reading the code, not assumed.**
The per-module Code Metrics rendering already returned an empty string when both metric sources
were `null`/missing for a module — no code change needed here, Part 1 alone was sufficient.

## Step 1: group architecture-generation tooling into `scripts/architecture/` — Done

Housekeeping reorg, no behavior change intended. `generate-architecture-model.sh`,
`check-architecture-model-freshness.sh`, `screenshot-architecture-map.sh`,
`liquibase-schema-to-json.js`, `md-to-decisions-json.js`, `architecture-map-screenshots/`, **and**
`DECISIONS.md` (whole file, ADR numbers unchanged — ADR-001 through ADR-021) moved into a new
`scripts/architecture/` directory, a **sibling** of `scripts/ai/`, not nested under it (an initial
attempt nested it as `scripts/ai/architecture/`, corrected mid-implementation once that still kept
the two concerns under one parent for no real reason). `scripts/ai/` keeps
`check-adr-index-freshness.sh`, `generate-adr-index.sh`, `check-flows-completeness.sh`,
`check-hardcoded-counts.sh` — the ADR-index/flows/doc-standards concern — now with no
`DECISIONS.md` of its own; it still gets a `SCRIPT_GROUP` node on the Tooling & Pipelines screen
(files-only, no ADR section).

Full rationale, every corrected mistake found along the way (`REPO_ROOT` depth, the `SCRIPT_GROUP`
evidence-file assumption, every external reference updated), and the final consequences are
recorded in `scripts/architecture/DECISIONS.md` ADR-021 — not duplicated here.

## Step 2: per-script self-documenting header, System screen's "How this page is built" block goes fully dynamic — Done

**Problem.** The System screen's "How this page is built" section hardcoded `Generation:`/
`Sources:` prose as static strings — exactly the kind of drifting documentation this tool
otherwise avoids everywhere else. **Decision:** every script in `scripts/architecture/` gets 4
fixed-prefix comment lines directly after the shebang (`# Description:`/`# Uses:`/`# Input:`/
`# Output:`, `//` for the two `.js` files). The generator gained a small extraction function
(plain `grep`/`sed`, no third parser) that walks `scripts/architecture/` **dynamically**
(`find`, not a hardcoded file list — a new script with the same header convention shows up with no
generator edit) and builds one JSON entry per file. The static `Generation:`/`Sources:` lines were
replaced by a table built from this array. **Not in scope:** applying this header convention to
`scripts/ai/`'s 4 remaining files or any other `SCRIPT_GROUP` directory.

## Step 3: System screen gains an ADRs card/screen — Done

New "📜 ADRs" card → flat, deduplicated list of every ADR across every module's `DECISIONS.md`
(`MODEL.allAdrs`, from `docs/ai/adr-index.md`), grouped by module (module-name heading links to
that module's own page for real `MODULE`-type nodes; `SCRIPT_GROUP` entries stay plain text),
clickable status filter cards (`All`/`Accepted`/`Superseded`/...), and clicking an ADR id opens the
same full-content popup the Module screen's own ADR list already used — reusing the popup DOM/
markdown rendering, not a second implementation. An "Overview" glossary section (what an ADR is,
how it's used, its boundaries) sits at the bottom of this screen. The Module page's own
"Architectural decisions" section was removed in the same step — this screen is now the single
place ADRs are ever shown, cross-linked in both directions. Full rationale and every fix found
along the way (link visibility CSS, double-scrollbar popup fix, SCRIPT_GROUP-vs-MODULE link
scoping) recorded in `scripts/architecture/DECISIONS.md` ADR-023 — not duplicated here.

## Step 4: System screen gains a Code Quality card/screen, removed from Module pages — Done

New "✅ Code Quality" card → `renderCodeQuality()`, two separate tables (SonarQube, ArchUnit —
never merged, so it's always clear which numbers came from which source), each with its own
`Source:` line and empty-state message naming the exact opt-in flag/prerequisite when null. Every
module name links back to its own page. `renderModuleCodeMetricsHtml()` (the old per-module inline
table) removed from `renderModule()` entirely. Derived ratio columns (Complexity/file, Cognitive/
file, Code smells/1k LOC, and a new Distance from Main Sequence field on the ArchUnit side) are
color-coded green/yellow/red against real thresholds; raw counts stay plain. A bottom "Overview"
section explains every field in depth (what it is, why it matters, how to read a high/low value).
Committed baseline currently carries real Sonar/ArchUnit data (explicit user choice — makes
`check-architecture-model-freshness.sh` report stale until a future no-flags regeneration; not a
silent contradiction of Step 0's "default stays null" design, a disclosed one-off exception). Full
detail in `scripts/architecture/DECISIONS.md` ADR-024.

## Not done here — split out

The companion-server on-demand refresh trigger (originally this issue's Part B) is **not**
implemented — moved to `improvement-146` once every other step above was done, since that piece's
priority is still undecided while everything else here was ready to close.

## Related

- `improvement-146` — the split-out companion-server work.
- `improvement-143` — implemented the SonarQube/ArchUnit Code Metrics section this issue built on.
- `improvement-138` — the original Architecture Control Plane plan.
- `improvement-142` — Part C (opt-in flags, tooltips, color thresholds) was originally drafted
  there before being merged into this issue.
