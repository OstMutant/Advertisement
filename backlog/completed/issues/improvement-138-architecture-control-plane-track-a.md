# improvement-138: Architecture Control Plane — Track A execution history (completed)

**Status:** ✅ Track A done — this file is the archived execution log, moved out of the still-open
`backlog/issues/improvement-138-architecture-control-plane.md` on 2026-08-28 per explicit user
request to split done vs. not-done work. That file keeps the full original spec (§0-§14), the 4
verification findings, and Track B's still-gated status (blocked on `improvement-135` item 5) —
read it first for context; this file is history only, not a live plan.

---

## Execution outcome — Track A (2026-08-04)

Implemented as planned in §11 (A1-A3):

- **A1** — `scripts/ai/generate-architecture-model.sh` produces `architecture-model.json`: 10
  `MODULE` nodes (from root `pom.xml`'s reactor + each module's own `pom.xml` — `DEPENDS_ON_COMPILE`/
  `DEPENDS_ON_RUNTIME`/`DEPENDS_ON_OPTIONAL` edges), domain grouping seeded from
  `docs/architecture/03-bounded-contexts.md` (`domain_confidence: manual`), `intent[]` ADR links
  reused from `.claude/nav/adr-index.md` (never reparses `DECISIONS.md`, per §14), 12 `COMMAND` + 2
  `SKILL` nodes from `.claude/commands`/`.claude/skills`, and one `BACKLOG_SUMMARY` node
  (open/completed issue counts). **Scoping decision, not silently narrowed:** per-ADR (171) and
  per-issue (149) graph nodes were **not** built — §11 A2 explicitly commits the graph to "tens of
  nodes, not thousands," and 171+149 nodes would blow that budget by 10x+. ADRs are folded into
  each module's `intent[]` list instead (reusing the existing generated index); issues are
  represented as one aggregate count node. Documented as a scoping note directly in the script's
  own header comment, not left implicit.
  - Confirmed idempotent (byte-identical output across two consecutive runs) — required for A3's
    freshness gate to be meaningful.
  - One real bug found and fixed during implementation: this repository's working tree uses CRLF
    line endings (`core.autocrlf`), which silently broke every `$`-anchored bash regex reading
    `03-bounded-contexts.md` (domain grouping came back entirely `UNKNOWN` until the `\r` was
    stripped per line) — not caught by the JSON-validity check alone, only by inspecting actual
    output values.
- **A2** — `architecture-map.html`: a real drill-down pyramid, not a flat graph with a raw-JSON
  side panel (the first draft was exactly that and was correctly rejected — see "A2 correction"
  below). Breadcrumb-navigated screens: **System** (module cards grouped by domain, a compact
  dependency map, and two entry tiles for Tooling & Pipelines / Backlog) → **Module detail**
  (one-line description, dependencies in/out as clickable cross-links, "depended on by" reverse
  lookup, ADRs with real titles, and an explicit "Deeper levels (Track B — not built yet)" section
  listing Contracts/Implementation/Methods/Test-coverage as visible placeholders rather than
  omitting them) → **Tooling & Pipelines** (commands/skills tables). Model JSON inlined directly in
  the file (not fetched) so it opens standalone via `file://` without CORS issues. Verified via
  `node --check` (JS syntax) and a headless harness exercising every screen's render function
  against the real generated model (system/module/pipelines/backlog screens, cross-link
  navigation, unknown-id fallback) — this environment has no display for an actual browser render,
  so a full visual check still wasn't possible; flagged here rather than silently claimed.
- **A3** — `scripts/ai/check-architecture-model-freshness.sh` (same backup/regenerate/diff/restore
  pattern as `check-adr-index-freshness.sh`), wired as an unconditional stage in
  `scripts/ci/entrypoint.sh`'s `docs` gate alongside the three existing checks. One new mapping-table
  row added to `.claude/commands/sync-docs.md`'s Step 2.

**A2 correction (same day, user-flagged):** the first A2 draft was a flat Cytoscape graph plus a
side panel dumping each node's raw JSON on click — functionally present but not what the plan's
own §5 asked for ("Drill-down path: System → Module → Domain/Package → Contract → Implementation →
Method... this is the 'vision' the owner asked for"). Corrected to the actual pyramid shape
described above. Two supporting fixes landed alongside the rebuild: `description` (one line per
module, reused from root `CLAUDE.md`'s already-clean "Module Layout" ASCII tree — no second
hand-maintained copy) and `DEPENDED_ON_BY` (reverse dependency edges, computed once per module by
scanning every other module's `pom.xml`) were added to each `MODULE` node so the human layer has
enough data to be genuinely readable, and `intent[]` was changed from bare ADR-id strings to
`{id, title}` objects (titles reused from `adr-index.md`'s own Title column) so ADR references
show as real sentences, not opaque codes.

**A2 second correction (same day, user-flagged):** the user pointed out `docs/architecture/*.md`
already carries rich, curated content (per-domain Entities/Key Services/Contract bullet lists in
`03-bounded-contexts.md`, per-table module ownership in `04-database-erd.md`, and both files' own
Mermaid diagrams) that the pyramid wasn't surfacing — correctly rejecting "that's Track B" as a
scope dodge, since reading *already-written* docs content into the human layer is exactly what
Track A's own sources commit to, not new extraction risk. Added: each `MODULE` node now carries
`entities`/`keyServices`/`contracts`/`tables` arrays (parsed from `03`'s per-domain bullet lists
and `04`'s `### table` / `**Module:**` pairs, same "already-structured, non-code source" bar as
everything else in A1); the Module screen renders them as labeled sections. Two new top-level
System entries — **Database Schema** and **SPI & Contracts** — render `04`'s ERD and `02`'s SPI
graph *live* via Mermaid.js, reusing the diagrams' own Mermaid source verbatim (the `.md` files
stay the authoring source, nothing is re-derived). `json_escape_multiline()` added alongside the
existing `json_escape()` since diagram source needs its line structure preserved (`\n` escapes),
unlike every other field emitted so far. Verified via the same headless-harness pattern, extended
to check the two new screens and the new Module-screen sections against the real generated model.

**A2 third correction (same day, user-flagged, then user-directed actual visual verification):**
two more rounds:
1. User pointed out only 2 of `docs/architecture`'s 5 diagram-bearing files (`02`, `04`) were
   wired in — `01` (module dependency graph), `03` (context map), and `05` (6 documented / 9 actual
   sequence diagrams) were missing. Generalized the two hardcoded diagram extractions into
   `extract_all_mermaids_json()`, which walks any file and pairs every `` ```mermaid `` block with
   its nearest preceding heading — replaced the `diagrams: {database_erd, spi_map}` object with a
   `diagramGroups[]` array (one entry per source file, each holding all its diagrams), and replaced
   the two separate top-level System cards with one "📐 Diagrams" entry leading to a
   group-then-detail browser. Found and fixed a real bug in the extractor itself while building
   this: this environment's `awk` doesn't support `{1,3}`-style regex intervals (silently matches
   nothing, no error) — `/^#{1,3} /` never matched any heading, so every diagram's title came back
   empty until changed to the POSIX-safe `/^#+ /`.
2. User asked for zoom (+/−/reset) controls on the diagram views "it would look nicer." Added a
   CSS `transform: scale()` zoom control. **Then actually verified visually** (see below) — the
   first zoom implementation didn't fix anything, because Mermaid's own `useMaxWidth: true` default
   pre-shrinks the rendered SVG to fit its container before my zoom transform ever touches it,
   making the SPI diagram illegibly tiny at any zoom level. Fixed by setting
   `useMaxWidth: false` on `flowchart`/`er`/`sequence` in `mermaid.initialize()`, so diagrams render
   at natural size (the wrap container scrolls; zoom is now a real magnifier on top of a
   properly-sized diagram, not a scale-up of an already-shrunk one).

**Visual verification, finally done for real** (user pointed out Playwright — already in this
repo's toolchain — could screenshot a local HTML file, not just the running app; this was correct,
and the earlier "no display in this environment" framing understated what was actually available).
New `scripts/ai/screenshot-architecture-map.sh`: reuses `playwright/run.sh`'s pinned image
(`mcr.microsoft.com/playwright:v1.61.1-jammy`), keeps a warm `arch-map-shot` container across runs
(same pattern as `pw-runner`), screenshots all 7 key states (System, a Module, the Diagrams list,
the SPI diagram before/after zoom, Pipelines, Backlog) to
`scripts/ai/architecture-map-screenshots/` (gitignored). Running it and actually reading the
screenshots caught the `useMaxWidth` bug above — the headless-harness JS checks from earlier
rounds verified the code *ran without error and produced the expected DOM structure*, which is not
the same as verifying it was *legible*, and this round's failure is exactly the gap between those
two things.

**A2 fourth correction (same day, user-flagged after seeing the screenshots):** three more asks,
each verified by re-running `screenshot-architecture-map.sh` and reading the result, not just
checking the code runs:
1. System map needed the same zoom (+/−/reset) affordance as the Diagrams screen — added a
   matching `.diagram-toolbar`, wired to Cytoscape's own `cy.zoom()` API (native pan/zoom was
   already enabled by default, just undiscoverable with no visible controls).
2. Diagram views should support drag-to-pan like the System map's graph does — added
   `enableDragToPan()`, a generic mousedown/mousemove/mouseup handler driving the wrap element's
   `scrollLeft`/`scrollTop` (Mermaid's static SVG has no native per-node dragging the way Cytoscape
   does; panning the whole canvas is the equivalent affordance for a diagram viewer). Confirmed
   with a real Playwright mouse-drag simulation (`scrollLeft` 0 → 300 after a 300px drag), not just
   a code-review of the handler.
3. System map's layout was two flat rows with crossing arrows and not left-to-right like
   `01-module-dependencies.md`'s own `graph LR`. Loaded `cytoscape-dagre` (+ its `dagre`
   dependency) via CDN and switched the layout from `breadthfirst` to `dagre, rankDir: LR`.
   Dependency direction for *layout ranking* is intentionally reversed from the *visual* arrow
   direction (dagre ranks source-before-target; edges are fed to it as dependency→dependent so
   `platform-commons`/`query-lib` rank leftmost, matching the documented diagram's convention) —
   the arrowhead itself is drawn via `source-arrow-shape`, independent of the ranking direction, so
   it still visually points at the dependency, not the dependent.

**A2 fifth correction (same day, user-flagged):** "System" (Cytoscape, native drag/zoom) was liked;
the Diagrams screen (still Mermaid static SVGs at that point) wasn't — user asked directly for
draggable nodes with edges following, not just pan. Added `parseMermaidGraph()`, a minimal parser
for this repo's own Mermaid dialect (`graph LR/TD/TB`, `ID["Label"]` node declarations,
`subgraph ID["Label"] ... end` blocks, `-->`/`-.->` edges with optional `|label|`) — not a general
Mermaid grammar, just what `01`/`02`/`03` actually use. Diagrams from those 3 files now render as
real Cytoscape graphs (dagre layout, compound nodes for subgraphs) with native drag-and-drop and
`cy.zoom()`, identical interaction model to the System map. `04` (ERD) and `05` (sequence diagrams)
deliberately stay Mermaid — an ERD needs a table/column renderer Cytoscape doesn't give for free,
and sequence diagrams are temporal/lifeline diagrams where "drag this box anywhere" doesn't map
onto what the diagram represents; the UI states this explicitly rather than silently behaving
differently with no explanation. Confirmed for real, not just code-reviewed: a Playwright script
read `diagramCy.nodes()[0].position()` before and after a simulated mouse drag (`{x:1615.75,
y:50.75}` → `{x:1615.75, y:88.4}`) and the resulting screenshot visibly shows the dragged node's
both edges (incoming and outgoing) re-routed to follow it.

**Testing (per this issue's own strategy above, extended after root-causing the 3 CI failures the
first pass had only flagged and not chased):**
- `bash scripts/unit-tests.sh`: 79/79 passed, `BUILD SUCCESS`.
- `bash scripts/ci.sh --all --sonar --sandbox`: **all 5 stages now genuinely run** —
  `docs` PASSED (5s), `unit` PASSED (70s), `integration` PASSED (51s, 164/164 tests),
  `e2e` PASSED (861s / 14.4min, **50/50 Playwright tests**), `sonar` completes and uploads a real
  analysis (quality gate itself fails — see below, a legitimate finding, not an infra failure).
  Three real, previously-undiagnosed bugs were found and fixed to get here, not just documented as
  "someone else's problem":
  1. **Root `Dockerfile` missing `provider-profile-spring-boot-starter` in 3 places** (the
     `COPY .../pom.xml` cache-warming step, the `COPY .../src` step, and the `mvnw install -pl`
     module list) — added when that module shipped (improvement-124 Batch B) but the Dockerfile was
     never updated. Every normal `deploy.sh` run silently reused Docker's cached layers from before
     that module existed, masking the bug completely; it only surfaced because `scripts/ci.sh`'s
     e2e stage builds a distinctly-tagged image (`marketplace-app-ci`) with no prior cache to hide
     behind. Confirmed by reading the actual Dockerfile against the real 10-module list, not
     guessed. Fixed: all 3 module lists now complete; verified by a full green `e2e` run (50/50).
  2. **`scripts/sonar/run.sh` silently corrupted its own stored token** — this repo's working tree
     uses CRLF line endings (`core.autocrlf`, the same class of bug already found once in A1's own
     domain-grouping parser); `grep "^sonar.token=" | cut -d= -f2` left a trailing `\r` on the
     extracted token, corrupting the Basic Auth header so SonarQube reported a demonstrably-valid
     token as invalid, which then also failed the `admin:admin` regeneration fallback (real
     instance no longer uses the default password). Root-caused by comparing the outer shell's own
     manual `curl` test (valid) against the script's `grep|cut`-extracted value (invalid) for the
     *same* token string, then confirming via `curl -v`'s raw `Authorization` header and `cat -A`
     on the properties file. Fixed with `tr -d '\r'` on both token-reading call sites.
  3. **`sonar-project.properties` and `run.sh`'s own copy loop only listed 5 of 9 Java-source
     modules** — missing `user-`/`advertisement-`/`taxon-`/`provider-profile-spring-boot-starter`
     entirely (45 of 314 Java files never scanned). Same "forgot to update the module list" pattern
     as bug 1. Fixed both `sonar.sources`/`sonar.java.binaries` and the copy loop; confirmed by the
     scanner's own "N source files to be analyzed" count rising from 261 to 306 after the fix.
  - **Sonar's quality gate genuinely fails**, and this is *not* something to force-pass: `new_coverage`
    0% (already tracked — `improvement-114`, JaCoCo never wired in), `new_duplicated_lines_density`
    4.97% (threshold 3%), `new_violations` 11-14 (threshold 0), all measured against a
    `PREVIOUS_VERSION` baseline from 2026-06-24 — i.e. accumulated across many commits since then,
    not something this session introduced. Left as a real, visible finding; not suppressed with
    `--no-gate` and not "fixed" by hastily patching code to satisfy an automated gate outside this
    issue's scope.

**Track A is complete and closes its stated goals** ("visual control," "legacy visible," "pipelines
included," and now genuinely "maximum readability" per the corrected A2) independently of Track B,
which remains gated on the `improvement-135` item 5 conflict (Finding 3) and has not started. This
issue stays open in `backlog/issues/` (not moved to `completed/`) until Track B is resolved one way
or the other.

## Operational notes (Track A)
- token_cost_review: n/a (no Agent-tool review calls this run)
- token_cost_research: n/a (research done directly by the main thread, no delegation)
- token_cost_verification: n/a (unit-tests.sh/ci.sh run directly, no Agent-tool verification calls)
- context_loading_task_type: new-tooling/scripting task (generator script + CI gate), extended
  mid-session into root-causing 3 pre-existing CI infrastructure bugs after the user rejected an
  initial "flag and don't chase" call on the e2e/sonar failures
- context_loading_consulted: yes — read `scripts/ai/generate-adr-index.sh`,
  `check-adr-index-freshness.sh`, `check-flows-completeness.sh`, `.claude/commands/sync-docs.md`,
  `.claude/nav/flows.md`, `scripts/ci/entrypoint.sh`, `Dockerfile`, `scripts/sonar/run.sh`,
  `scripts/sonar/sonar-project.properties` before writing new code, to match existing conventions
  and find real root causes rather than guessing
- context_loading_matched: yes
- flows_situation: pre-approved plan (this issue's own §11), user said "давай" to proceed; later,
  user explicitly rejected the initial "these 3 failures are out of scope, flag don't fix" framing
  and required root-cause fixes before accepting the work as done
- flows_chosen: direct implementation (not /autopilot — user approved conversationally, not via the
  slash command)
- flows_matched: n/a (no single flows.md row covers "implement an approved plan without
  /autopilot" — this was a reasonable direct continuation of the approved plan, not a flow gap)

## Follow-up — System page simplification, Module Dependencies graph consolidation (done 2026-08-04)

User-requested UI change to `architecture-map.html`/`scripts/ai/generate-architecture-model.sh`.
Two problems, one fix:

1. **System (first) page is doing too much.** It currently renders: the 3 summary cards
   (Tooling & Pipelines, Backlog, Diagrams) — keep these — *plus* a hand-built, domain-colored,
   click-to-navigate Cytoscape module dependency map (`renderMap()`/`<div id="map">`) *plus* a
   domain-grouped grid of module cards (Shared Kernel / Testing / Audit Domain / ... sections).
   User wants the first page trimmed to just the 3 cards.
2. **The module graph shouldn't disappear — it duplicates the "Module Dependencies — Dependency
   Graph" diagram that already exists under Diagrams (source: `docs/architecture/
   01-module-dependencies.md`'s `graph LR` Mermaid block).** Right now that diagram is rendered
   through a *separate, generic* code path (`GRAPH_TYPE_KEYS` → `parseMermaidGraph()` →
   `renderCytoscapeDiagram()`), which re-parses the plain Mermaid text and has no domain coloring
   and no click-to-navigate-to-module-detail — a real, second implementation of "draw the module
   graph" next to `renderMap()`'s richer one. This is exactly the kind of duplication
   `doc-standards/SKILL.md`'s "one fact, one canonical home" rule exists to catch (confirmed no
   `DECISIONS.md` anywhere in the repo currently documents the System/Diagrams navigation
   structure or this graph-duplication at all — a real gap, not just this one delta, per explicit
   user instruction to note it since it wasn't noted anywhere).

**Decision:** Collapse to one graph-building implementation.
- Remove the map (`<div id="map">`, `renderMap()`/`systemCy`, zoom toolbar) and the domain-grouped
  card grid from `renderSystem()`. System page becomes exactly the 3-card grid.
- Special-case `01-module-dependencies` inside `renderDiagrams()`'s diagram-body rendering: instead
  of routing it through the generic `parseMermaidGraph()`/`renderCytoscapeDiagram()` path (still
  used as-is for `02-spi-map`/`03-bounded-contexts`, which have no equivalently rich structured
  node model), render it with the *same* domain-colored, click-navigable graph-building logic
  `renderMap()` used — reusing `moduleNodes` (the pom.xml-sourced canonical module list, already
  the single source of truth the whole model is built from) instead of re-parsing the markdown's
  own Mermaid text a second time. Clicking a node still navigates to that module's detail page
  (`renderModule()`, unchanged) — same interaction the old System map offered, now reachable via
  System → Diagrams → Module Dependencies → (click a node).
- "Friends with the markdown": the diagram still visually represents the same facts
  `01-module-dependencies.md` states (10 modules, same dependency edges) — using `moduleNodes`
  instead of re-parsing the Mermaid text is not a fork, since `moduleNodes` and the Mermaid text
  are both already generated from/checked against the same `pom.xml` reality (the model-generation
  step and `/sync-docs` both anchor to that). No second hand-maintained graph definition is
  introduced.
- Record this as a new ADR in `scripts/ai/DECISIONS.md` (would be ADR-003 — only 2 exist there
  today, and neither covers the System/Diagrams navigation structure or this consolidation) — the
  first documentation of "System page = summary only; module browsing lives under Diagrams, one
  shared graph renderer, not two."

**Verification — done:** regenerated via `bash scripts/ai/generate-architecture-model.sh`,
screenshotted via `bash scripts/ai/screenshot-architecture-map.sh` (the script itself needed
updating — its old click sequence assumed the now-removed `.domain-group .card` on the System
page; module-detail verification switched to driving `navigate()` directly via `page.evaluate()`
instead of a fragile canvas-coordinate click on a dagre-laid-out node). Confirmed visually: System
page shows only 3 cards; Diagrams → Module Dependencies → Dependency Graph shows the domain-
colored graph with working node clicks into module detail (breadcrumb: `System › Diagrams ›
Module Dependencies — Dependency Graph`, then `System › <module>` on click, same flat-breadcrumb
shape the old System map already had); SPI map / bounded contexts unchanged (still generic/non-
colored, as decided — no equivalent node model exists for them yet). Zero JS errors during the
full screenshot run. Recorded as `scripts/ai/DECISIONS.md` ADR-003. `.claude/nav/adr-index.md`
(181 entries) and `architecture-model.json`/`architecture-map.html` regenerated again after the
ADR was added; `check-adr-index-freshness.sh`/`check-architecture-model-freshness.sh`/
`check-flows-completeness.sh`/`check-hardcoded-counts.sh` all pass.

**Noted but out of scope for this follow-up:** the Diagrams list page shows sequence diagram
"8. Login and Registration Rate Limiting" twice (a pre-existing duplicate in `docs/architecture/
05-sequence-diagrams.md`'s own diagram list, unrelated to this change) — flagged for a future
pass, not fixed here.

### Addendum — breadcrumb back-navigation (done 2026-08-04)

User feedback after the above landed: forwarding into a module from the diagram worked, but there
was no way back to the diagram short of clicking all the way up to "System." User also asked how
to organize this so a single fix keeps working as the tool grows, rather than needing a matching
patch in multiple places each time.

**Decision:** Replaced the breadcrumb's hand-built per-screen `if/else` HTML with a single growing
navigation stack (`crumbStack`, pushed on drill-down, truncated on jumping back) plus one label
function (`crumbLabelFor(view)`) — `navigate()`/`renderBreadcrumb()` are the only two places that
touch it, and adding a future screen only means teaching `crumbLabelFor()` its label. Deliberately
the same shape as this project's real `BreadcrumbStep` pattern in `marketplace-app` (see root
`CLAUDE.md` "Breadcrumb Pattern") rather than a new, differently-shaped mechanism. Added a generic
`navigateBack()` (pop one level) and repointed the Diagrams detail view's "← all diagrams" button
(renamed "← back") to it — its old direct `navigate({screen:'diagrams'})` call would have grown
the stack instead of shrinking it under the new always-push `navigate()` semantics. Recorded as
`scripts/ai/DECISIONS.md` ADR-004.

**Verified** via an extended `screenshot-architecture-map.sh` run: System → Diagrams → Module
Dependencies → click node → module detail (breadcrumb: `System › Diagrams › Module Dependencies —
Dependency Graph › platform-commons`) → click the "Module Dependencies — Dependency Graph"
breadcrumb segment → lands back on the re-rendered graph, not a dead end. `.claude/nav/adr-index.md`
(182 entries) and `architecture-model.json`/`architecture-map.html` regenerated again; all 4 CI
freshness gates pass; zero JS errors across the full screenshot run.

### Addendum — Module screen: back button, ADR export, ADR links resolved to the real `DECISIONS.md` (done 2026-08-05)

User feedback: the Module screen had no `← back`, its ADR list had no links at all, and no
Markdown export (unlike the Diagrams screens). Also relocated `architecture-map.html`/
`architecture-model.json` from repo root into `docs/` (user-directed, settled after a few
iterations on exactly where).

Two implementation attempts were built and both corrected before landing, directly from user
review — full history in `scripts/ai/DECISIONS.md` ADR-006:
1. Embedded the full ADR body text into `architecture-model.json`, shown in a `<dialog>` modal.
   **Rejected**: violates `doc-standards/SKILL.md`'s own "reference by ADR number, never restate
   the reasoning inline" rule — real duplication of ADR prose across two committed files, even
   though generation kept them in sync (no drift ≠ no duplication). Found and fixed 3 real bugs
   while building this version (an `awk` `gsub` backslash-escaping gotcha, a CRLF-related trailing
   `---` leak, a `check-hardcoded-counts.sh` false positive on embedded historical ADR text) — kept
   as real fixes even though the feature they were fixing was itself reverted.
2. Replaced the body with a real link + a computed line number. **Also rejected**: a line number
   needs recalculating on every edit to any earlier ADR in the same file, fails silently (no gate
   catches a wrong line), and never bought real navigation anyway (a raw `.md` opened via `file://`
   has no heading anchors for a fragment to jump to).

**Final design:** ADR items link straight to their real `<module>/DECISIONS.md`
(`../${module}/DECISIONS.md`, relative to `docs/architecture-map.html`'s own location) — no body
text, no line number, zero ADR prose duplicated anywhere in the generated files. The ADR id itself
(already the link's visible text) is what a human searches for once the file opens — stable by
construction, nothing to keep in sync. `check-hardcoded-counts.sh`'s temporary exclusion for the
two generated files was reverted once the body text (its only reason for existing) was removed.
Verified directly: mirrored the real relative path in an isolated test directory and confirmed a
browser click actually opens and renders the real `DECISIONS.md` content, not a broken link.

### SUPERSEDED — see "Knowledge Pyramid direction" below for the reframed plan

The section below (kept for its verified technical findings, still valid) is superseded in scope
by the "Knowledge Pyramid" reframing, 2026-08-05: this was scoped as a narrow markdown→JSON format
swap; the actual goal is a layered knowledge model (L0 repo → L1 structured facts → L2 graph → L3
AI projection → L3.5 task projection → L4 human view), of which `<module>/DECISIONS.json` is just
one L1 fact file among several planned (`modules.json`, `contracts.json`, `rules.json`,
`decisions.json`, `issues.json`, `dependencies.json`). The pilot output
(`audit-spring-boot-starter/DECISIONS.json`) is not discarded — it already fits this frame as-is.

### PLANNED (awaiting go-ahead) — DECISIONS.md → DECISIONS.json migration, repo-wide

User-directed pivot, 2026-08-05: instead of any embed/dialog/HTTP-server design, convert every
module's `DECISIONS.md` to structured JSON outright, and have `architecture-map.html` load it via
a real link — no fetch, no server, no per-ADR duplication. Decisions are always written by Claude,
never hand-edited by the user directly, so a structured format is strictly easier to work with
going forward, not a authoring regression.

**Two technical facts driving the design, both verified directly against this repo's actual
Playwright/Chromium version before proposing anything:**
- `fetch()`/`XMLHttpRequest` against a sibling `file://` resource fails outright (`Failed to
  fetch`) — confirmed. Same for reading an `<iframe>`'s `contentDocument` cross-document under
  `file://` (loads, but `contentDocument` is `null`) — confirmed. Neither can move data into page
  JS without a server.
- `<script src="sibling.json">` **does** load under `file://` with zero CORS restriction —
  confirmed (this is exactly how the page already loads Cytoscape/Mermaid from a CDN today). The
  referenced file's content must be an actual JS statement that assigns to a reachable variable
  (`window.X = [...]`) — a bare top-level `{...}`/`[...]` alone doesn't error, but also isn't
  retrievable by the page afterward, since nothing holds a reference to it. Confirmed working
  end-to-end, including triggered dynamically at click-time (not just declared statically in the
  initial HTML) via `document.createElement('script')`.

**Design:**
1. Every module's `DECISIONS.md` is replaced by `<module>/DECISIONS.json`. Content: `window.
   DECISIONS_DATA = window.DECISIONS_DATA || {}; window.DECISIONS_DATA["<module>"] = [ {"id":
   "ADR-001", "title": "...", "status": "...", "context": "...", "decision": "...",
   "consequences": "..."}, ... ];` — JSON-shaped array of ADR objects, wrapped in the one-line
   assignment `<script src>` loading requires. This *is* the file Claude edits directly going
   forward (via `/decision` and any ADR-recording step) — not a generated mirror of a markdown
   original.
2. `architecture-map.html`'s Module screen dynamically injects `<script src="../<module>/
   DECISIONS.json">` the first time that module's page is opened (lazy, not all 12 modules loaded
   upfront) — confirmed this works triggered at click-time, not just declared in the static HTML.
3. The ADR list on the Module screen is built with a plain loop over `window.DECISIONS_DATA[module]`
   (already how it works today, just reading richer objects instead of `{id,title,file}`).
4. Clicking an ADR shows its `context`/`decision`/`consequences` inline (toggle, no navigation) —
   the data is already in memory, this is a pure client-side render, "on the fly" per the user's
   own phrase.
5. A "view full file" link near the "Architectural decisions (N)" heading still points at the real
   `<module>/DECISIONS.json` for anyone who wants the whole module's history in one place.

**Tooling with a hard dependency on the markdown format today, all requiring an update:**
- `scripts/ai/generate-adr-index.sh` — parses `## ADR-NNN: Title` / `**Status:**` from markdown;
  must parse the new JSON shape instead.
- `.claude/commands/decision.md` (`/decision` command) — writes new ADRs; must emit JSON entries,
  not markdown headings.
- `.claude/skills/doc-standards/SKILL.md` and `.claude/rules.md` — reference `DECISIONS.md` by
  name/extension in the standing rules text.
- Root `CLAUDE.md`'s "Architectural Decisions Log" list of 12 file paths, and every module's own
  `CLAUDE.md` cross-references (`@module/DECISIONS.md`).
- `scripts/ai/generate-architecture-model.sh`'s `adr_intent_for_module()`/`json_adr_array()` (the
  `file` field it emits per ADR would now point at `.json`, not `.md`).

**Content migration risk:** ~184 ADRs across 12 modules (per the current `adr-index.md` count),
some files large (`marketplace-app/DECISIONS.md` alone: 72 ADRs, 3568 lines) — a mechanical
markdown→JSON conversion needs a verification pass (e.g. per-ADR non-whitespace character-count
comparison against the original) to catch silent content loss, not just a visual spot-check.

**Open scoping question, not yet resolved:** pilot the full mechanism on one small module first
(`scripts/ai/DECISIONS.md`, 6 ADRs, directly relevant to this tool) to prove the round-trip and the
click-to-expand UX before converting the other 11 (much larger, historically load-bearing) files —
or convert all 12 in the same pass. Awaiting the user's call before starting either.

### DIRECTION (fixed 2026-08-05, corrected 2026-08-05) — re-affirms §1/§4/§5, does not replace them

User-proposed reframing ("Knowledge Pyramid") turned out to already be this issue's own stated
design, written in §0-§5 above before this addendum existed — §1 "one pyramid, not two systems",
§4 "AI layer: progressive, token-minimal levels L0-L5", §5 "human layer: visual control surface".
An earlier version of this addendum introduced a *second*, incompatible layer numbering (L0
repo/L1 facts/L2 graph/L3 AI projection/L3.5 task projection/L4 human) — corrected here to stop
that drift before it spread further (it had already reached `scripts/ai/DECISIONS.md` ADR-007/008,
fixed in the same pass as this correction). There is one layer scheme for this issue, §4's:

| §4 level | Content | Maps to today's work |
|---|---|---|
| L0 — System | module list + one-line purpose | `architecture-model.json`'s module nodes — built |
| L1 — Module | deps, owned domains, lifecycle | Module screen in `architecture-map.html` — built |
| L2 — Contract | `*Port`/`*Hook` signatures, no bodies | Contracts (Port/Hook) section — built (names only, Track B) |
| **L3 — Rule/Intent** | **applicable rules + relevant ADRs for the touched area** | **the ADR popup work (ADR-007/008 below) — but only as a human-clicked view (§5), not yet an AI-consumable token-cheap file (§4's actual ask for this level)** |
| L4 — Test evidence | which tests cover the touched contract | not started (needs Track B) |
| L5 — Implementation | actual method bodies, SQL | not started (needs Track B) |

The real lever for Claude's token/speed cost was misidentified mid-session as file *format* (md
vs json — measured directly at ~2.7% size difference on `audit-spring-boot-starter`, noise, not a
real lever). The actual lever, already correctly named in §4, is progressive/conditional loading —
L1-L4 are each loaded only when a task actually needs that level, not read wholesale.

**What today's work actually is, precisely:** a real, working piece of L3 content (relevant ADRs
for a module), but built for §5's human layer (a popup someone clicks) — not yet §4's AI layer (a
small file Claude reads directly, without a human or a browser involved at all). Building the
actual §4 L3 artifact (e.g. `<module>-l3.json`: touched-module's applicable rules + ADRs, sized for
direct Claude consumption) is the next concrete step, not a new "L3.5" concept — it was never
missing from the plan, only unbuilt.

**Steps taken 2026-08-05 (see `scripts/ai/DECISIONS.md` ADR-007/ADR-008 for full technical
history):**
1. Replaced the awk-based markdown parser (2 real bugs caught and fixed live) with
   `scripts/ai/md-to-decisions-json.js` (Node) — the canonical extractor for the `decisions`
   domain, generalized to any module, not just the pilot.
2. ADR content embedded directly into `architecture-model.json`'s per-module `decisions` field
   (inlined into `architecture-map.html`'s existing generation-time `<script>` block) — no runtime
   file loading, no browser-dependent behavior. Shown via a real `<dialog>` popup on click.
   Piloted on `audit-spring-boot-starter` only (25 ADRs); not yet extended to the other 11 modules.

**Not started:** an actual §4 L3 AI-consumable artifact (Claude-readable, not human-popup-only);
rollout to the other 11 modules; L4/L5.

### DONE (2026-08-05) — steps 1-2 above, with a mid-flight design correction (see `scripts/ai/DECISIONS.md` ADR-007/ADR-008)

Step 1 (Node parser) and step 2 (record direction + ADR) both landed, but the design step 2's own
ADR-007 first recorded — a separate `<module>/DECISIONS.json`, `<script src>`-loaded under
`file://` — was itself reverted one round later: it worked in direct testing (confirmed in both
Chromium and Firefox) but made the feature's correctness depend on browser-specific `file://`
security policy, which the user flagged as unacceptable regardless of today's test result.

**Final design (ADR-008):** ADR content is embedded directly into `architecture-model.json`'s
per-module `decisions` field — inlined into `architecture-map.html`'s existing `const MODEL = ...`
`<script>` block at generation time, zero runtime file loading of any kind. Clicking an ADR opens a
real `<dialog>` popup (`showModal()`), not inline expand. `md-to-decisions-json.js` gained a
`--stdout <module>` mode for this; the standalone `<module>/DECISIONS.json` file and its
`<script src>` loader were deleted. `DECISIONS.md` remains the actual canonical source — this ADR
only changed where the *parsed* data lands, not what's hand/Claude-edited.

This does reopen ADR-006's original "no ADR body text in `architecture-model.json`" ruling — a
deliberate, informed reversal after exhausting the alternatives (plain link: no popup possible;
separate JSON + `<script src>`: works today, unacceptable browser-policy dependency), recorded as
such rather than silently contradicting it. Screenshot-verified: table/bullet-list/multi-line
numbered-list content all render correctly inside the popup for `audit-spring-boot-starter`'s
25 ADRs. All 4 CI freshness gates pass.

**Rolled out to all 7 modules with their own `DECISIONS.md` (2026-08-05):**
`attachment-spring-boot-starter`, `audit-spring-boot-starter`, `integration-tests`,
`marketplace-app`, `platform-commons`, `query-lib`, `taxon-spring-boot-starter` — 186 ADRs total.
Parser validated against every file first (ADR count matched real heading count exactly
everywhere, non-whitespace character ratio landed in the expected 92-97% range — no truncation).
Screenshot-verified the largest module (`marketplace-app`, 72 ADRs, including a popup with nested
bullet lists and inline code) renders correctly with zero page errors.
`scripts/ai/check-hardcoded-counts.sh` needed the same exclusion ADR-006 once needed and reverted —
reapplied, this time permanently (see `scripts/ai/DECISIONS.md` ADR-008's Open goals entry).

**Not started:** an actual §4 L3 AI-consumable artifact (Claude-readable directly, not only via a
human-clicked popup); L4/L5.

**Cross-module ADR references (2026-08-05, see `scripts/ai/DECISIONS.md` ADR-009):** an ADR can
concern more than one module (e.g. `marketplace-app/DECISIONS.md` ADR-071 is really about
`user-spring-boot-starter`) — `adr-index.md` used to only attribute an ADR to the file it's
physically in, so `user-spring-boot-starter`'s page showed "(0)" despite real, relevant ADRs
existing. Fixed with an optional `**Also affects:** module-a, module-b` tag (additive, no
migration of the existing ~188 ADRs, no change to where a future decision gets written) — chosen
over consolidating into one central tagged file, which would have changed the authoring model for
every future decision project-wide. The 3 modules with no `DECISIONS.md` of their own
(`advertisement-spring-boot-starter`, `provider-profile-spring-boot-starter`,
`user-spring-boot-starter`) each get a generated, pointer-only `DECISIONS.md` listing whichever
ADRs cross-reference them — zero duplicated prose, confirmed idempotent across regenerations. Also
fixed live: a CRLF bug in `generate-adr-index.sh`'s module-name comparison silently dropped every
cross-referenced row (same class of issue ADR-006 hit once before, different script). Applied the
tag to 7 real ADRs as a working demonstration (5 → `advertisement-spring-boot-starter`, 2 →
`provider-profile-spring-boot-starter`), curated by reading each candidate's real content, not
just its title — both previously-empty pointer modules now show real cross-referenced ADRs.

**Tooling & Pipelines screen extended (2026-08-05):** the 5 non-Maven directories with their own
`DECISIONS.md` (`scripts`, `scripts/ai`, `scripts/ci`, `scripts/sonar`, `playwright` — previously
invisible to the tool entirely) get a new `SCRIPT_GROUP` node type, reusing the exact same
ADR-embedding/popup mechanism `MODULE` nodes already have. The screen is now split into "AI
Tooling" (Commands, Skills, `scripts/ai`) and "Other Scripts" (the remaining 4 directories), each
script group showing its own file list (as real links) and ADR list (as popups). Commands/Skills
"Source" column changed from plain text to a real clickable link, same "resolve to the file, don't
just name it" rule already applied elsewhere in this tool. Screenshot-verified: 5 script groups
render, 0 page errors, all links/popups functional.

**Backlog screen extended (2026-08-05):** same click →
short description → real file link pattern applied to the Backlog screen's 33 open + 120
completed issues (previously only 2 aggregate counts, no per-issue visibility at all). Description
is a short, truncated first paragraph (~240 chars), not the full issue body — same "don't embed
what a real link already resolves" rule as ADRs. Screenshot-verified working.

**Backlog cards made interactive (2026-08-05):** the two count cards (open/completed) now toggle
which single list renders below — click "open" shows only open issues, click "completed" shows
only completed, active card gets a highlighted border. Defaults to "open" on screen entry.
`backlog/` has no `DECISIONS.md` of its own (confirmed directly — only `BACKLOG.md`, `issues/`,
`completed/` exist), so no decisions section applies here, unlike Module/Script-Group screens.

**Priority + real `BACKLOG.md` link added (2026-08-05):** each issue's `**Priority:**` line
(emoji + level, e.g. "🟡 medium" — the reason text stays in the file, not duplicated) now shows
next to its title in the list and in the popup. The screen-desc's "see backlog/BACKLOG.md" mention
became a real clickable link (`sourceLink()`), positioned above the open/completed cards.
Screenshot-verified.

**`scripts/ai` file ordering + a real download-vs-open finding (2026-08-05):** `SCRIPT_GROUP_FILE_
ORDER` gives `scripts/ai` an explicit priority order (generators first — `generate-architecture-
model.sh`/`generate-adr-index.sh`/`md-to-decisions-json.js` — then the 4 CI gates, then the
dev-only screenshot tool) instead of alphabetical; other script groups keep alphabetical unless
given their own explicit order. Separately confirmed directly (Playwright, `context.on('download')`
event): `.sh` files always trigger a browser download when opened via `file://`, `.js`/`.md` files
open inline — a real Chrome file-type-sniffing behavior for local files, not fixable from HTML/JS
alone (no `fetch()` workaround exists under `file://`, and a `type="text/plain"` hint on the `<a>`
tag has no effect, confirmed by testing both). Stated as a genuine limitation, not silently
"fixed" with something that doesn't actually change the behavior.

**Backlog sort now matches `BACKLOG.md`'s real execution order, plus a new Docker screen
(2026-08-05):** user-flagged that `improvement-138`
wasn't sorting first despite being top priority — the previous sort re-derived tiers from each
issue's own Priority text, losing `BACKLOG.md`'s actual fine-grained order (138 before 136 before
135, all three "Top"). Fixed by parsing `BACKLOG.md`'s own "Issues (in execution order)" table
column directly into `MODEL.backlogPriorityOrder`, sorting by real position there first, falling
back to the text-tier approximation only for issues not yet triaged into that table. Verified: the
first 4 rendered open issues are now `138, 136, 135, 124`, matching the real table exactly. Also
added: a "🐳 Docker" System card/screen — every `Dockerfile`/`docker-compose.yml` in the repo, real
links, mechanically-extracted build stages/service names, linking to `scripts/CLAUDE.md` for the
actual deploy workflow prose rather than restating it.

**SPI Map rebuilt live from real Java source, same pattern as Module Dependencies (2026-08-05, see
`scripts/ai/DECISIONS.md` ADR-014):** the rendered diagram looked bad (all nodes cramped onto one
row — Mermaid-parsed compound groups weren't laying out well for this graph's shape). Rebuilt
live: grepped `public interface` under `platform-commons/*/spi/*.java` + every real `implements` of
each across starters/marketplace-app — reproduced the existing `.md`'s diagram and file locations
exactly, and caught a real staleness bug in the process (`UserIdMarker`'s claimed implementer,
`UserDto`, doesn't actually implement it anywhere in current code — the live version correctly
shows "no implementation found" instead of repeating a false claim). Purpose one-liners and the 3
Call Flow Examples (genuinely editorial, no mechanical source) carried over as static content, same
exception Module Dependencies' Key Observations already established; "Implementation Rules"
dropped entirely in favor of a real link to `platform-commons/CLAUDE.md` (actual duplication, not
an editorial exception). Both the Interface and Implementation(s) table columns are real links to
the actual `.java` files. Export as Markdown added. `docs/architecture/02-spi-map.md` is **kept**
for now — explicit user instruction, pending a side-by-side comparison before deletion.

**Two more real gaps found and fixed, same session (2026-08-05):** (1) diagram nodes weren't
clickable — the shared Cytoscape renderer had no click handler at all; fixed with a `file` field on
every node plus one shared tap handler, verified against the real relative path (clicking
`AdvertisementPort` opens the real `.java` file). (2) User reported scrollbars unresponsive when
zooming Module Dependencies/SPI Map — confirmed directly the container never actually overflows
(Cytoscape manages zoom via its own internal transform, not DOM size), so the scrollbar CSS was
dead code specifically for these diagrams; removed it, and clarified the hint text to state both
real drag behaviors (drag a node to reposition it vs. drag empty canvas to pan — confirmed panning
itself already worked, the gap was that the old hint only mentioned the first one).

**Content parity gap found and fixed (2026-08-05):** user-flagged the live SPI Map page had
genuinely less content than the retired `.md`. Diffed word-for-word — two real losses:
`AuditPort`'s Purpose dropped its 9-method list, `UserIdMarker`'s Purpose trimmed its exact
`isOwner(UserDto, UserIdMarker)` signature. Both restored verbatim. The "Overview" intro paragraph
(the `*Port`/`*Hook` direction explanation) had been dropped entirely — restored as its own static
section, and mirrored into the markdown export too. Deliberately did *not* restore a standalone
"File Locations Summary" — every interface/implementation cell in the table is now a real link to
its own file, so a flat path list would just duplicate what the table already shows.

**Second re-read, 3 more real gaps found and fixed (2026-08-05):** user pushed back again after
comparing more carefully — correct. (1) The original grouped interfaces into 7 subsystem tables,
each headed by the real Java package (`org.ost.platform.audit.spi`, etc.) — the live version had
flattened this into one undifferentiated table with no package shown. Fixed: `subsystem`/`package`
now mechanically extracted per interface (`grep "^package "`), grouped into
`renderSpiSubsystemTables()`. (2) Two subsystem-level editorial notes (Attachment's
`AttachmentMediaChangeHook` non-existence, User's 4-port-split rationale) were dropped with no
trace — restored as static content under their respective subsystem tables. (3) "Implementation
Rules" had been reduced to a bare link, losing real, unique Location + concrete-example content
(not covered by `platform-commons/CLAUDE.md`'s more abstract version) — restored as static text,
keeping the link only for the core "pure delegation" rule itself. All three also flow into
`exportSpiMapMarkdown()`.

**Stale cross-references to the retired `02-spi-map.md` cleaned up (2026-08-05):** there was never
a dedicated generator script for `01`/`02` — both were hand/Claude-updated markdown, triggered by
`/sync-docs`'s mapping table, not a deterministic script. What *was* stale: that mapping table
itself (`.claude/commands/sync-docs.md`), `doc-standards/SKILL.md`'s canonical ownership table,
`.claude/nav/context-loading.md`'s routing table, and `docs/architecture/README.md` (3 mentions) all
still pointed at `02-spi-map.md` as if it were still the live target. All updated to point at
`docs/architecture-map.html`'s SPI Map page instead, mirroring the exact pattern already applied to
`01-module-dependencies.md` earlier in this issue.

**`docs/architecture/02-spi-map.md` deleted (2026-08-05):** `git rm` after the user compared
content twice across two rounds of real gaps found and fixed (see above), then gave explicit
go-ahead. Verified both `01-module-dependencies.md` and `02-spi-map.md` are actually gone from disk
and correctly staged as deletions in `git status`. `docs/architecture/` now holds only
`bounded-contexts.md`, `04-database-erd.md`, `05-sequence-diagrams.md`, `06-coupling-
analysis.md`, `07-risk-report.md`, `08-scorecard.md`, and `README.md` — Module Dependencies and SPI
Map both live exclusively in `docs/architecture-map.html` now.

**Bounded Contexts assessed and handled differently, deliberately (2026-08-05, see
`scripts/ai/DECISIONS.md` ADR-015):** unlike 01/02, most of this file's content isn't mechanical —
Context Map edges are conceptual business relationships, domain grouping is already flagged
`domain_confidence: manual` in the model, Integration Patterns/Risks are analytical prose. Proposed
this assessment before touching anything; agreed to keep it hand-maintained rather than force a
live rebuild. Renamed `03-bounded-contexts.md` → `bounded-contexts.md` (dropping the numeric
prefix, this file only) with every reference updated across the repo. Its diagram page now shows a
real link to the file plus the one-line "why hand-maintained" reasoning. `/sync-docs`'s own Step 4
now explicitly sequences docs-first-then-regenerate (`bash scripts/ai/generate-architecture-model.sh`
runs last, since it reads `bounded-contexts.md`/`DECISIONS.md` as input) instead of leaving the
ordering only implicit in the mapping table; Step 3's stale "For 01"/"For 02" lines (both fully
automated now, no manual read-and-rewrite step) corrected.

**Bounded Contexts layout fix, click-to-navigate, Integration Patterns extras (2026-08-05):**
extended `renderCytoscapeDiagram(source, rankDir)` to accept an optional `rankDir`, passed `"LR"`
for the bounded-contexts diagram only (mirroring SPI Map's fix). First attempt still rendered as a
single cramped vertical column regardless of `rankDir` — root-caused directly (printed every node's
resolved `x`/`y` via a Playwright script): every node had an *identical* x, meaning dagre's ranking
collapsed to one column no matter the direction. Cause: the Mermaid source encodes, for each of 6
domains, a pair of edges directly between a `subgraph` group id and one of its own nested children
(e.g. `User -->|defines contract| UserPort` and `UserPort -->|implements in| User`, with `UserPort`
declared *inside* the `User` subgraph, i.e. `parent: "User"` in the parsed Cytoscape compound
model) — 12 such edges total. An edge between a compound parent and its own descendant forms a
2-cycle dagre's compound-aware ranking can't resolve normally, and collapsing that cycle onto one
rank drags the whole graph's ranking down with it. Fix: `renderCytoscapeFromGraph()` now drops any
edge where one endpoint is the immediate Cytoscape `parent` of the other before building the
layout — a general rule (any future compound Mermaid diagram with the same pattern is protected
automatically), not a bounded-contexts-only special case. These edges were also redundant to draw:
the parent/child nesting itself already visually shows "this domain owns this port". Verified fix
via direct Playwright node-position dump (`x` values now spread across the canvas, not identical)
and a full-page screenshot. Also added: `BOUNDED_CONTEXTS_MODULE_MAP` (Mermaid subgraph id → real
module id) + a `tap` handler on domain nodes that navigates to the real module page (verified via
`node.emit("tap")` — a raw `cy.emit("tap", [{target: n}])` call does NOT trigger a delegated
`cy.on("tap","node",...)` handler the same way a real user click does, a test-harness gotcha, not a
real bug, confirmed by re-testing with the node-level emit form). Added
`bounded_contexts_integration_patterns_json()` (bash) → `MODEL.boundedContextsIntegrationPatterns`
→ `renderBoundedContextsExtrasHtml()` (JS), 3 named integration-pattern walkthroughs (Entity
Lifecycle with Audit, Media Attachment, Activity Feed Enrichment) rendered below the diagram, same
shape as SPI Map's Call Flow Examples.

**Bounded Contexts diagram dropped entirely, kept as note + link only (2026-08-05, see
`scripts/ai/DECISIONS.md` ADR-016 — supersedes the layout-fix work above):** re-testing the
`rankDir: "LR"` fix after removing the 12 parent-self-child edges (a real bug, kept fixed) still
showed zero horizontal spread. Root-caused directly: this graph is genuinely cyclic at the domain
level once compound groups collapse to single nodes (`UI → Audit` and `Audit → UI` both exist via
`UI`'s edges into other domains' ports and `Audit`'s edge into `UI`'s own `HookImpls` child) —
`dagre` documents that compound-node layout doesn't reliably support cycles crossing compound
boundaries, and a whole strongly-connected component collapsing onto one rank is exactly that
failure mode. Presented this to the user with 3 options before acting; discussion also surfaced
that the diagram itself isn't a unique source of truth (Module Dependencies + SPI Map + each
domain's own `CLAUDE.md` already cover most of the same ground) — `bounded-contexts.md` is the real
source, not the picture. Decision: stop forcing this graph through the dependency-graph renderer.
The Bounded Contexts diagram page now shows only the explanatory note (kept, from ADR-015) + a real
link to `bounded-contexts.md` — no canvas, no click-to-navigate, no Integration Patterns extras.
Removed as dead weight: `BOUNDED_CONTEXTS_MODULE_MAP`, `renderBoundedContextsExtrasHtml()`,
`bounded_contexts_integration_patterns_json()` + its model field, `"bounded-contexts"` from
`GRAPH_TYPE_KEYS`, and the now-unreachable `isGraphType` branch/variable in `renderDiagrams()`. The
parent-self-edge exclusion fix in `renderCytoscapeFromGraph()` stays — a real, general correctness
fix independent of this diagram's fate, protecting any future compound Mermaid diagram from the
same pattern. Verified: all 4 CI freshness gates pass; screenshot confirms no canvas element
renders on the page.

**Database ERD (04-database-erd.md) migration — planned, not yet implemented (2026-08-05):**
asked to migrate this file fully into `architecture-map.html`, same spirit as Module
Dependencies/SPI Map (live diagram + real links + all markdown content preserved), but explicitly
keep `04-database-erd.md` on disk after migrating (not deleted yet — compare first, matching the
already-established 01/02 pattern of "migrate, verify, only then delete on explicit go-ahead").

Assessed the 9 real Liquibase changelog XML files first (one per starter's `changes/*.xml`, e.g.
`user-spring-boot-starter/.../01-user-schema.xml`) before proposing a design — they use
`<createTable>`/`<column>`/`<constraints nullable/unique/primaryKey>`/
`<addForeignKeyConstraint>`/`<createIndex>`, structured enough for mechanical extraction (table
name, column name/type/nullable/unique/PK, real FKs, indexes — same spirit as `pom.xml` for 01 and
Java `implements` for 02).

**What's mechanically extractable (live-generated):** table name, module, real changelog file path
(linkable), column name/type/constraints, real foreign keys, indexes.

**What must stay hand-preserved editorial text (same class of problem `bounded-contexts.md`'s
conceptual edges already surfaced — not a new risk, a recurring one):**
- Per-column and per-table "Notes" (business meaning, ADR cross-references) — not derivable from
  XML at all.
- The ERD's "no FK" relationships (e.g. `advertisement.created_by → user_information`,
  `provider_profile.city_taxon_id → taxon`) — deliberately *not* real DB constraints (app-level
  decoupling, see `marketplace-app/DECISIONS.md`), so nothing in the changelog XML encodes them;
  omitting them from a mechanically-generated relationship list would be a real content loss, not
  just a cosmetic gap.
- "Data Flow & Relationships" (3 narrative pseudocode flows), "Soft Delete Pattern", "Extensibility",
  "Performance Considerations" — analytical/narrative sections, same bar as SPI Map's Call Flow
  Examples / the (now-removed) Bounded Contexts Integration Patterns.

**Planned shape (mirrors SPI Map's richer pattern, not Module Dependencies' flatter one — this
file has real per-table editorial content SPI Map's subsystem tables/notes already set the
precedent for preserving):**
1. `db_erd_json()` (bash, likely delegating to a small XML-parsing script — text-block XML with
   regex/awk risks the same fragility already hit and rejected for `DECISIONS.md` parsing, session
   history favors a real parser) walks the 9 changelog files, emits table/column/FK/index data +
   module + real changelog link per table.
2. A hand-preserved static map (same shape as `SPI_PURPOSE`) carries the non-derivable content:
   per-column Notes, per-table Notes, and the "no FK" relationship list.
3. Render: generate the Mermaid `erDiagram` source string live from the merged (mechanical +
   preserved) data — keeps Mermaid's native ERD rendering (PK/FK/UK badges, crow's-foot notation)
   which the custom Cytoscape parser doesn't support for `erDiagram` syntax — plus an HTML
   table-schema section per table (columns/types/constraints/Notes, real changelog link),
   analogous to SPI Map's subsystem tables.
4. Before deleting `04-database-erd.md`: word-for-word content-parity diff against the live
   version, same discipline that caught two real content-loss rounds on SPI Map — not assumed
   correct on the first pass.

Not started — plan written here per the Approval Rule, presented to the user, awaiting
confirmation before implementation begins.

**Database ERD migrated and `04-database-erd.md` deleted (2026-08-05, see `scripts/ai/DECISIONS.md`
ADR-017):** implemented the plan above, with one mid-flight redesign — the user rejected the
originally-planned `declare -A` bash map for column/table descriptions (the same shape `SPI_PURPOSE`
already used for SPI Map) as itself a second, drifting copy of information that belongs next to the
real thing it describes. Applied the correction to both subsystems:
- **SPI Map:** all 17 `platform-commons/*.spi` interfaces now carry a real Javadoc purpose
  paragraph (11 added, 6 already had one — kept, one of which — `AuditDomainHook` — turned out more
  accurate than the `SPI_PURPOSE` entry it would have replaced, direct evidence of drift).
  `SPI_PURPOSE` deleted; `spi_javadoc_purpose_for()` extracts it live via `awk`.
- **Database ERD:** every `<column>`/`<createTable>` in the 6 real changelogs
  (`user`/`advertisement`/`attachment`/`audit`/`taxon`/`provider-profile`) got a `remarks="..."`
  attribute carrying the old markdown's "Notes" text verbatim. New
  `scripts/ai/liquibase-schema-to-json.js` (Node, real parser) extracts table/column/type/
  constraints/real-FKs/indexes/`remarks` live; a small hand-preserved list covers the 9
  relationships with no real SQL-level FK (this codebase's deliberate actor-reference decoupling).
  Diagram renders via Mermaid's native `erDiagram` (solid lines = real FK, dotted = conceptual-only)
  fed a live-built source string; an HTML table-schema section (same shape as SPI Map's subsystem
  tables) sits below it.
- Content-parity check before deleting the markdown found the table/column/index/FK data had full
  parity plus **two real staleness bugs** in the old hand-maintained file (`advertisement.ad_kind`
  and `attachment_snapshot.version` both exist in the real schema, were never documented). Four
  narrative sections (Data Flow examples, Soft Delete Pattern, Extensibility, Performance
  Considerations) had no live equivalent — each was assessed rather than reflexively copied into a
  new static array (the exact pattern just corrected): Data Flow's 3 examples duplicate SPI Map's
  existing Call Flow Examples from a different angle; the other three are already inferable from
  live table/column `remarks` and the mechanically-extracted index list. None needed a new home.
  `docs/architecture/04-database-erd.md` deleted; every repo-wide reference repointed.
- Verified directly: `bash scripts/deploy.sh --reset` (full rebuild, wiped DB) applied all 6
  modified changelogs cleanly; queried Postgres directly and confirmed `remarks` became real
  `COMMENT ON COLUMN` entries, not just changelog metadata. All 4 CI freshness gates green
  throughout. Conventions documented in `platform-commons/CLAUDE.md` (SPI Javadoc) and root
  `CLAUDE.md`'s "Database Changes" guideline (Liquibase `remarks`), so future additions don't
  regress back to a second, hand-maintained copy.
- Added an Open goal in `scripts/ai/DECISIONS.md` to apply the same "scatter into real source"
  treatment to `bounded-contexts.md` next (domain grouping is already derivable from Java
  package/module structure; some business relationships may be re-derivable from real
  `ComponentFactory<XPort>` injection points) — explicitly noted this does not by itself resolve why
  the Bounded Contexts diagram was dropped in ADR-016 (a genuine graph-cycle/`dagre` rendering
  limitation, not a staleness problem) — freshness and renderability are separate concerns.

**Database ERD UX follow-up (2026-08-05, same DECISIONS.md ADR-017):** user compared the new page
against the Cytoscape diagrams (01/02) and found 3 gaps. Fixed: (1) clicking a table in the diagram
now scrolls to its schema section below (`wireDbErdEntityClicks()`, matches rendered SVG entity ids
against real table names, wired after `mermaid.run()`'s promise resolves); (2) added a "Notation"
legend (PK/FK/UK, solid/dotted line meaning) — agreed this is fine as static content, unlike table
descriptions, since notation symbols are fixed language constants, not domain facts that can drift.
Explained rather than "fixed" the third (scroll/zoom mechanism differs from 01/02): `erDiagram`
syntax only renders via Mermaid's own engine, so it necessarily shares `05-sequence-diagrams.md`'s
CSS-scale + native-scrollbar mechanism, not Cytoscape's canvas pan/zoom — asked whether to build a
fully custom draggable Cytoscape ER renderer anyway (losing native crow's-foot notation); declined,
kept the native renderer. Also gave Module Dependencies (01) an "Overview" section matching
SPI Map's and the new ERD page's (01 didn't have one, user liked the ERD wording and asked for
parity).

**Second follow-up round, same DECISIONS.md ADR-017:** user found the first round incomplete —
ERD's own legend didn't cover symbols actually visible in the diagram (crow's-foot cardinality
markers), "Notation" read worse than "Legend", and the request to spread explanations across all
diagrams hadn't been done for 01/02. Also asked for a concise "what tech/data builds this page"
block on the System screen. Inspected the real rendered SVG (Playwright, `<marker>` ids) instead of
guessing which symbols appear — confirmed only `||--o{`/`||..o{` are ever emitted, so the Legend
explains exactly those two crow's-foot markers, not a generic reference for symbols that never
render. Renamed "Notation" → "Legend"; added a "Legend" section to 01 (line style = compile vs.
runtime, arrow direction, node color = domain) and 02 (dashed box = module, blue box = Java class,
arrow = implements). 01's domain-color legend is built **live** from the same `domainOrder`/
`domainColor()` functions the graph itself uses — not hardcoded — which is how it honestly surfaced
a real quirk (9 domains over an 8-color palette means `Shared Kernel`/`UI/Application Layer` share a
color) a static legend would have missed or gotten wrong. Added a short "How this page is built"
block to the System screen: rendering stack, generation stack, and a one-line list of where each
piece of the data actually comes from — no restated `CLAUDE.md` prose, kept deliberately brief.

**Toolbar layout fix, same session:** the ERD page's `← back`/`Export as Markdown` row looked
different from 01/02 — it rendered its own second toolbar div below the title with a
`margin-top:-8px` patch, instead of joining the shared toolbar row above the title like 01/02 do.
Fixed by adding `04-database-erd` to the existing shared conditional and deleting the separate
toolbar entirely.

**Bounded Contexts diagram — planned restoration via Mermaid's native engine (2026-08-05):** ADR-016
dropped the diagram entirely because our custom Cytoscape+dagre compound-node pipeline collapsed it
onto one column (a real graph cycle via the UI/Audit hub, a documented dagre limitation). User asked
whether Mermaid's own native rendering (the same mechanism now used for the Database ERD and
`05-sequence-diagrams.md` — `mermaid.run()`, not our `parseMermaidGraph()` + Cytoscape pipeline)
would handle this better. Verified directly before proposing anything: rendered the exact same
`bounded-contexts.md` Mermaid source through plain `mermaid.run()` in an isolated Playwright test —
produced a properly spread-out layout (8 cluster boxes at clearly different x/y positions, not
collapsed) — confirmed with a screenshot, not assumed. Mermaid's own flowchart/subgraph layout
handles this graph's cycles acceptably; our hand-rolled Cytoscape-compound-dagre pipeline did not.

**Planned fix:** restore `bounded-contexts` to `renderDiagrams()`, but through the native Mermaid
path (mirroring the Database ERD/`05` branch), not the removed Cytoscape pipeline:
1. Replace the current note-only branch (`g.key === "bounded-contexts"`, `generate-architecture-model.sh`
   ~line 2046) with one that calls `mermaid.run()` on `d.source` (bounded-contexts.md's existing
   Mermaid block, unchanged — mechanizing the *data* is the separate, larger, not-yet-started Open
   Goal in `scripts/ai/DECISIONS.md`, not required just to fix rendering) plus `enableDragToPan()`.
2. Keep the explanatory note (still true and useful: edges are conceptual, not mechanically
   derived), reworded now that a diagram renders again.
3. Add click-to-navigate (domain box → real module page), reusing the same
   SVG-entity-id-matching approach `wireDbErdEntityClicks()` already proved for the ERD page —
   needs checking Mermaid's actual node-id convention for `graph`/flowchart type first (confirmed
   different from `erDiagram`'s `entity-NAME-uuid` pattern), not assumed.
4. Verify via Playwright (real screenshot, not assumed), all 4 CI gates, document in
   `scripts/ai/DECISIONS.md` (supersedes/annotates ADR-016) and here.

Not started — plan written here per the Approval Rule, presented to the user, awaiting confirmation.

**Implemented and verified (2026-08-05, see `scripts/ai/DECISIONS.md` ADR-018):** all 4 plan steps
done as written. `wireBoundedContextsClicks()` turned out simpler than the ERD equivalent — Mermaid
gives each subgraph's SVG cluster group an id exactly equal to the subgraph id (`id="User"`,
`id="Audit"`, confirmed by inspecting the real rendered SVG), no regex name-matching needed, unlike
`erDiagram`'s `entity-NAME-<uuid>` pattern. Verified end-to-end: a real click on the `User` cluster
navigates to `user-spring-boot-starter`'s module page (breadcrumb changed); a screenshot at 30% zoom
shows all 8 domains spread out and readable. One real, pre-existing rough edge carried over from
`05`/ERD's shared zoom mechanism (not new): `zoomDiagram(0)` resets to 100% scale rather than fitting
content to the viewport the way Cytoscape's `diagramCy.fit()` does for 01/02, so this unusually wide
diagram (~4248px) needs a manual zoom-out on first open — noted, not fixed this round. All 4 CI
freshness gates green.

**Bounded Contexts data mechanization — attempted, in progress (2026-08-05):** user asked to
maximize mechanical derivation of `bounded-contexts.md`'s content (not just rendering, which
ADR-018 already did) — as strictly as possible, marking anything uncertain rather than silently
guessing, aiming to eventually delete the `.md` the same way 01/02/04 were. Investigated feasibility
first (`ComponentFactory<XPort>` grep across the repo is too noisy — 15+ files per port, mixing UI
components/config/test-support with real domain-to-domain coupling; and starters never depend on
each other directly by this project's own Module Import Rules, so cross-domain relationships like
"audited via" are always mediated through `marketplace-app` orchestration classes, not a clean
starter-to-starter signal).

**Plan:** reuse the existing `confidence`/`domain_confidence` field convention already in
`architecture-model.json` (`"extracted"` = real mechanical signal, `"manual"` = hand-set/fallback)
to mark each derived domain/relationship honestly rather than inventing a new marker scheme.
1. `bounded_contexts_json()` (bash) → `MODEL.boundedContexts.domains[]` — per domain: real
   `@Table` entities, real `*Service` classes, real Ports/Hooks it implements (from existing
   `spiMap` data), real tables owned (from existing `MODULE_TABLES`) — all `"extracted"`.
2. Relationships, one detection rule per type, each honestly confidence-tagged:
   - Shared → every domain: from `moduleNodes`' own pom.xml-derived edges (already computed for
     Module Dependencies) — `"extracted"`.
   - UI → every port: real `ComponentFactory`/`ObjectProvider<XPort>` injection anywhere in
     `marketplace-app` — `"extracted"` (broad is correct here, UI genuinely calls every port).
   - Domain ← "receives callbacks from" (Hook implementations in `marketplace-app`): from existing
     `spiMap` implementation data, filtered to `*Hook` interfaces — `"extracted"`.
   - Provider → Taxon ("category assignment via"): real `TaxonPort.replaceAssignments()` call site
     inside `provider-profile-spring-boot-starter` itself (this one path is genuinely direct, per
     that starter's own `CLAUDE.md`) — `"extracted"`.
   - Domain → Audit ("audited via") / Domain → Attachment ("can have"): `EntityType.<DOMAIN>`
     appearing as an argument to `AuditPort.capture*()`/`AttachmentPort` calls — real signal, but
     attribution to "the real orchestration class" vs. incidental UI-layer mentions needs
     judgment; marked `"heuristic"`, not `"extracted"`, with the matched file:line kept as evidence.
3. Client-side `buildBoundedContextsMermaidSource()` (mirrors `buildDbErdMermaidSource()`) generates
   the `graph TB` Mermaid text live from `MODEL.boundedContexts`, replacing the current `d.source`
   (still read verbatim from `bounded-contexts.md` since ADR-018). A legend/notes section shows
   confidence per relationship, so uncertain edges are visible in the tool itself, not just in
   `DECISIONS.md`.
4. `bounded-contexts.md` stays on disk, untouched, not auto-deleted — same established discipline
   as 01/02/04: migrate, content-parity-compare with the user, delete only on explicit go-ahead.

Not yet implemented — plan written here per the Approval Rule; implementation follows immediately
in this same session per explicit user go-ahead already given in chat.

**Implemented and verified (2026-08-05, see `scripts/ai/DECISIONS.md` ADR-019):** the naive
`ComponentFactory<XPort>` grep from the earlier investigation was too noisy to use, but a real,
precise signal turned out to exist: `AuditActivityFieldsHook`/`AuditActivityEnrichHook`
implementations in `marketplace-app/spi/*.java` each declare a specific `entityType()` — a
first-class, intentional mechanism, not an incidental mention. Combined with
`TaxonPort.replaceAssignments()`'s real call sites and the already-computed `moduleNodes` pom.xml
edges, all 19 relationships in the final graph landed at `"extracted"` confidence (real code
signal) — the `"heuristic"` tier prepared for weaker signals wasn't needed. `bounded_contexts_json()`
also derives every domain's real `@Table` entities, `*Service` classes, tables owned, and SPI ports
implemented; `Shared`/`UI` get domain-appropriate substitutes (real counts, real Hook impl names).
`buildBoundedContextsMermaidSource()` generates the diagram live from this data (mirrors
`buildDbErdMermaidSource()`); a Relationships table (Relationship/Label/Confidence/Evidence) makes
every fact traceable. Two real findings surfaced along the way: (1) the original diagram's
`Attachment -->|audited via| Audit` edge has no real `EntityType.ATTACHMENT`/Hook backing it — not
yet resolved whether the original was imprecise or a different real mechanism exists, flagged for
the content-parity pass; (2) `Advertisement -> Taxon` category-assignment coupling exists in real
code exactly like `Provider -> Taxon` but was missing from the original diagram entirely — found
and fixed in the same pass (added as a second `"category assignment via"` detection rule). Verified
directly: 0 page errors, real click-to-navigate confirmed, all 4 CI gates pass.
`bounded-contexts.md` stays on disk — not yet content-parity-compared or deleted.

**Follow-up polish (2026-08-05, see `scripts/ai/DECISIONS.md` ADR-019):** real links added for
every entity/service/table/port (grouped into labeled categories instead of one flat list), a
"What crosses" column showing the real payload type per relationship (checked against real `Port`
method signatures), a "Legend" section matching 01/02/04, an "Export as Markdown" button (was
missing), a `"(live)"` marker on the Diagrams list card, and a real layout bug fix (long evidence
text was overflowing the Relationships table — fixed generically for every table in the tool).

**Content-parity check, before any deletion (same date):** unlike SPI Map/ERD, real narrative
content was found with no live equivalent — `Domain Details`' per-domain prose, `Shared Kernel`'s
full category breakdown, `Domain Independence`, and `Risks & Future Considerations`. Confirmed
`Integration Patterns` is not a loss (duplicates SPI Map's Call Flow Examples word-for-word).
Presented to the user before deleting anything — decision pending on whether to carry the four
real sections over as hand-preserved content first. `bounded-contexts.md` not deleted yet.

**Correction (2026-08-07):** the three "stays on disk"/"not deleted yet" claims above are stale.
`docs/architecture/bounded-contexts.md` was in fact deleted 2026-08-06 as part of
`improvement-143`'s work (`git rm`, content captured in full in `improvement-142` before deletion)
— confirmed gone from disk, deletion commit `524468e0`. See `improvement-142`'s own "Status"
section, which already reflects this correctly.

