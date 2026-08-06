# improvement-144: Code Metrics as its own top-level card + on-demand refresh trigger

**Type:** improvement — investigation + design decision, filed as its own issue per explicit user
request (highest priority, first to execute).
**Module:** `scripts/ai/generate-architecture-model.sh`, `docs/architecture/architecture-map.html`
**Priority:** 🔴 highest (set explicitly by user request — first to execute in the backlog)
**When:** independent, no hard blockers, but benefits from landing after `improvement-142`'s
sibling follow-up (tooltip/color/source-link groundwork on the existing per-module Code Metrics
section) so this card reuses that rendering instead of duplicating it — see "Related" below.

## Problem

SonarQube/ArchUnit metrics (`sonarMetrics`/`archUnitMetrics` in `architecture-model.json`,
rendered by `renderModuleCodeMetricsHtml()`) currently only show up buried inside each module's own
page — a user has to click into every module individually to see its numbers, with no way to
compare modules side by side. Separately, refreshing this data today means running a CLI command
(`bash scripts/sonar.sh` / `bash scripts/unit-tests.sh`) outside the tool entirely, then
regenerating `architecture-map.html` — there's no way to trigger a refresh from inside the tool
itself.

## Suggested fix (open design question — investigate before implementing)

**Part A — dedicated card.** Add a new top-level entry point on the System screen (alongside the
existing Diagrams / Tooling & Pipelines / Backlog / Docker cards) — a "Code Quality" screen with
one table listing every module's Sonar+ArchUnit metrics side by side (reusing whatever
tooltip/color-threshold/source-link design lands from `improvement-142`'s sibling follow-up, not a
second separate design). Per-module pages keep their own Code Metrics section too, or link out to
this new screen instead — decide during implementation which reads better, not pre-decided here.

**Part B — on-demand refresh trigger, real constraint to resolve first.**
`architecture-map.html` is a static, self-contained file, documented and used as `file://`
-compatible with no server (`docs/architecture/README.md`). A literal "button that runs the
scan" requires *something* to execute a shell command on click, which a static HTML page opened via
`file://` cannot do — browser sandboxing blocks filesystem/process access from page JS, by design,
regardless of framework. Two real options, needing a decision before implementation, not a default
to reach for silently:

1. **Copy-command button** — click copies
   `bash scripts/ai/generate-architecture-model.sh --with-sonar --with-archunit` (see
   `improvement-142`'s sibling follow-up for these flags) to the clipboard, with an instruction to
   run it in a terminal and reload the page. Keeps the tool's current serverless/`file://`-only
   property intact; not literally "one click," but no new moving parts either.
2. **Small local companion server** — a tiny script the button's `fetch()` call hits, which shells
   out to the existing `sonar.sh`/`unit-tests.sh` scripts and signals completion; the page then
   re-fetches the regenerated model. Gives a real one-click experience but drops the "no server,
   `file://` compatible" property the tool advertises today — needs its own script, its own
   lifecycle (start/stop), and documentation, and only works while that local server is running.

Investigate both before choosing; do not default to option 2 just because it matches the literal
"one click" framing — option 1 may be the better fit given the tool's existing design goals. Bring
back a recommendation with tradeoffs, not a unilateral pick, before writing any code.

## Related

- `improvement-142` — sibling follow-up filed the same session: descriptions/tooltips, color
  thresholds, source links, and the `--with-sonar`/`--with-archunit` opt-in flags for the *existing*
  per-module Code Metrics section. This issue's Part A reuses that rendering; Part B's option 1
  reuses those same flags directly.
- `improvement-143` — implemented the SonarQube/ArchUnit Code Metrics section in the first place.
- `improvement-138` — the original Architecture Control Plane plan both of the above extend.
- `docs/architecture/README.md` — documents the tool's current "no server, `file://` compatible"
  property, the constraint Part B has to either preserve or explicitly trade away.
