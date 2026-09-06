# Architecture & Technical Decisions — scripts/architecture

---

## ADR-034: Database ERD's no-FK point relationships derived from a `remarks=` marker convention, not a hand-curated list

**Status:** Accepted

**Context:** `db_erd_conceptual_relationships_json()` (established by ADR-017) held 9 hand-curated
`{from, to, label}` entries for conceptual (no real SQL-level FK) relationships. Adding
`apikey-spring-boot-starter` surfaced that `USER_INFORMATION → API_KEY` was missing — the list was
never updated when that starter shipped. A full sweep of every `*_id`/`*_by`-shaped column across
every changelog (advertisement, api_key, attachment, attachment_snapshot, audit_log,
provider_profile, taxon, user_preferences) found the list already missing four more real
relationships even before `api_key` (`taxon.created_by`/`updated_by`/`deleted_by`,
`attachment_snapshot.changed_by_actor_id`, `audit_log.actor_id`, `user_preferences.actor_id`) —
9 entries existed where 13 real point-relationships do, plus a 4th generic entity_type/entity_id
pair (`ADVERTISEMENT → TAXON_ASSIGNMENT`, `taxon-spring-boot-starter`) also missing. A
column-name-pattern heuristic (match `actor_id`/`_by`/`_actor_id`) was considered and rejected:
column naming is inconsistent across starters (`created_by` vs. `deleted_by_actor_id` vs.
`changed_by_actor_id`), several existing `remarks=` never mention "no FK" at all (`audit_log.actor_id`
just says "User ID who made the change"), and a name-only heuristic cannot express *which* table is
the real target — defaulting to "always `user_information`" would have been wrong for
`provider_profile.city_taxon_id → taxon`, the one point-relationship in this codebase whose target
isn't the actor table.

**Decision:**
1. A new, fixed, machine-parseable `remarks=` marker: every no-FK reference column's `remarks=` now
   carries the literal substring `References <table>(<column>), no FK` — mirrors the real Liquibase
   FK syntax (`references="table(id)"`) as prose, appended to whatever business-meaning text
   root `CLAUDE.md`'s "Database Changes" guideline already requires, never a replacement for it.
   Documented as a mechanically-required convention in `.claude/skills/module-doc-standards/SKILL.md`
   ("No-FK reference columns" section + a pre-write checklist line) — the same mechanically-required
   tier as that skill's `*.spi` interface Javadoc convention, since a missing/malformed marker
   silently drops the relationship from the live diagram instead of erroring.
2. Applied to all 13 existing point-relationship columns across 7 changelog files (advertisement,
   api_key, attachment ×2, audit_log, provider_profile ×2, taxon ×3, user_preferences), each also
   gaining `<validCheckSum>ANY</validCheckSum>` where missing (advertisement, audit,
   provider-profile, taxon) so a future non-`--reset` deploy doesn't fail on the remarks-text
   checksum change.
3. `db_erd_json()` (`generate-architecture-model.sh`) now derives the point-relationship half of
   `conceptualRelationships` itself: a `run_node -e` post-processing step regex-scans every parsed
   column's `remarks=` for the marker (`from` = the table named in the marker, `to` = the table the
   column lives on, `label` = the column name), merged with a much-shrunk hand-curated list
   (`db_erd_conceptual_relationships_json()`) that now holds only the 3 genuine generic
   entity_type/entity_id pairs — whose real target is a runtime data value, not a schema fact, so
   no marker can express it (unchanged from ADR-017's original reasoning for this narrower class).

**Rejected alternative — column-name pattern matching** (regex on column name alone, e.g.
`actor_id$|_by$|_actor_id$`): rejected per the Context above — inconsistent naming across starters
and remarks text not always mentioning "no FK" would keep producing silent misses, and the target
table still couldn't be determined without a further, separate hardcoded assumption.

**Consequences:**
- Verified directly: regenerated `architecture-model.json` shows all 16 real conceptual
  relationships (13 derived + 3 curated), including the two gaps this sweep found
  (`USER_INFORMATION → API_KEY`, `ADVERTISEMENT → TAXON_ASSIGNMENT`) and the four pre-existing ones
  listed in Context.
- A future new no-FK reference column is captured automatically the moment its `remarks=` carries
  the marker — no further edit to `generate-architecture-model.sh` needed, closing off this
  specific recurring class of hardcoded-list drift (see `backlog/completed/BACKLOG-ARCHIVE.md`'s
  `improvement-181` for the broader pattern this instance belongs to).
- `liquibase-schema-to-json.js` itself is unchanged — the derivation lives in `db_erd_json()`
  instead, since the parser's existing plain-array output shape has a second consumer
  (`MODULE_TABLES`'s own `run_node -e` extraction) that a shape change would have broken.

---

## ADR-033: "AI Tooling" generalized into a `.claude`-rooted tree, same mechanism as "Scripts"; README becomes the sole canonical file list, chip-row is last-resort only

**Status:** Accepted

**Context:** ADR-010 gave "AI Tooling" a flat category (`scripts/ai`, since renamed
`.claude/nav/scripts`) rendered as two hardcoded tables (Commands, Skills) plus a single flat
`SCRIPT_GROUP` card for the scripts directory. `.claude/` itself (`commands/`, `nav/`, `rules/`,
`skills/`) had no representation in the tool at all — its own file headers/READMEs (added under
improvement-170) were unreachable through the UI. A first attempt to fix this added the
Commands/Skills tables as-is plus a flat `.claude` tree, which surfaced two real duplication bugs:
`renderScriptGroupSection()`'s `chipFiles` fallback rendered a second, context-free file list
alongside a directory's own README even when that README already named and linked the same files
in context (confirmed live: `.claude/nav`, `.claude/rules`, and the `.claude` root itself all
showed this duplicate); and `mdBlockToHtml()`/`mdInlineToHtml()` never supported real markdown
`[text](url)` link syntax at all, so every `[file](file.md)` reference in an existing README
(including pre-existing ones in `.claude/nav/README.md`, unrelated to this change) rendered as
inert bracket text, not a clickable link.

**Decision:**
1. `SCRIPT_TREE_ROOTS`/`renderScriptTree()` (previously hardcoded to the "scripts"+"playwright"
   pair feeding only the "Scripts" card) generalized to a `groupId`-keyed `TREE_ROOTS` map, each
   entry naming its own primary root + sibling roots; `.claude` added as a second tree, category
   "AI Tooling", feeding the same drill-down renderer as "Scripts". `.claude/nav/scripts`
   (previously its own flat `SCRIPT_GROUP_DIRS` entry) is now reached as a nested child of the
   `.claude` tree instead. `SCRIPT_TREE_LEAF_DIRS` (new) stops recursion at `.claude/skills`
   specifically — each skill's own `SKILL.md` is already that unit's complete, self-contained
   description, so a per-skill folder-card would only ever show an empty page.
2. The hardcoded Commands/Skills tables on the "AI Tooling" root are removed — every directory
   under `.claude/` (including `commands/`, previously undocumented) now gets a real `README.md`
   as its one canonical, in-context file list, matching how every other `SCRIPT_GROUP` directory
   already works.
3. `renderScriptGroupSection()`'s chip-row (a bare, context-free file-link list) now renders
   **only** when the node has no `README.md` at all — a node with a README is assumed to already
   name and link its own files in context; showing both was the exact duplicate this decision
   exists to remove.
4. `mdInlineToHtml()`/`mdBlockToHtml()` gained real `[text](url)` markdown-link support (resolved
   against the source file's own directory, or used as-is when the target already contains `/`) —
   applies to every existing embedded-markdown render (root `README.md`/`INFRASTRUCTURE.md`, every
   `SCRIPT_GROUP` README, ADR popup body), not new for this change alone.

**Consequences:**
- Every `.claude/` subdirectory's file headers/READMEs (added under improvement-170) are now
  actually reachable through `architecture-map.html`, closing the gap ADR-010 left open.
- A future tree/`SCRIPT_GROUP` directory that wants a rich, non-file-based summary (like the
  removed Commands/Skills tables) needs a deliberate design choice — README-as-canonical is now
  the default, not an automatic table.
- Verified directly via a live Playwright run in the existing `arch-map-shot` container: 4
  folder-cards at the AI Tooling root (`commands`/`nav`/`rules`/`skills`), 0 folder-cards inside
  `skills` (recursion correctly stopped), 13 real clickable links on `commands` (was 1 before the
  markdown-link fix), 0 console/page errors, no duplicate chip-row on `nav`/`rules`/root.

---

## ADR-001: Generated ADR index over `DECISIONS.md`, mechanical fields only, no per-entry authoring

**Status:** Accepted

**Context:** An audit of whether this repo's AI-navigation surface (module `CLAUDE.md`,
`DECISIONS.md`, `docs/architecture/`, backlog, `.claude/skills/deep-review/`,
`.claude/commands/*.md`) needed an additive layer for discoverability/context-efficiency. The audit
found module `CLAUDE.md` is already unconditionally `@`-imported into every session (root
`CLAUDE.md`), so a module-level navigation index would add tokens with no discovery benefit.
`docs/architecture/03-bounded-contexts.md` and `04-database-erd.md` already cover module/SPI and
table-ownership mapping respectively, in more detail than a new file would. The one real gap: the
11 `DECISIONS.md` files (root `CLAUDE.md` names only 9 — it omits `scripts/ci/DECISIONS.md` and
`scripts/sonar/DECISIONS.md`) hold ~173 ADR entries with no index, and are not auto-loaded.

**Decision:** Build `.claude/nav/adr-index.md`, generated by `scripts/ai/generate-adr-index.sh` — a
mechanical parser (no LLM judgment) that scans the filesystem for every `DECISIONS.md` (never a
hardcoded module list, since that list has already drifted once), extracts each `## ADR-NNN:
Title` heading and its `**Status:**` line, and emits one row per entry: ADR number, owning module
(derived from the file's own path), status, title. All three fields are already 100% present in
every entry's existing text — the index adds zero new authoring burden to the ~173 existing
decisions. A free-form `Tags`/cross-module-`Scope` field was considered and rejected for this
version: it is the one genuinely subjective field (2 of 4 sampled entries during the audit —
platform-commons ADR-003, marketplace-app ADR-034 — are cross-module despite living in one file,
which pure file-path derivation gets wrong), and building it speculatively, with no evidence of
real search friction yet, would just be a second manually-maintained surface that can itself drift.
Regeneration is manual (`bash scripts/ai/generate-adr-index.sh`), mirroring `/sync-docs`'s own
"run manually, never automatically" convention — wired as a mandatory step into `/decision` (after
recording a new ADR) and into `/sync-docs`'s changed-file→doc-target mapping and `--full-audit`
freshness pass, rather than inventing a separate drift-detection mechanism.

**Rejected alternative — per-entry metadata block prepended to every `DECISIONS.md` entry**
(`Status | Scope | Modules | Tags`, one block per ADR): rejected as more than the evidenced gap
warranted. `Status` already exists on every entry; `Module`/`Title` are mechanically derivable from
the file path and heading text alone, with zero entries needing to change. Prepending a redundant
block to ~173 existing entries would be authoring work with no discovery benefit over generating
the same three fields externally.

**Rejected alternative — `.claude/nav/module-index.md` and `.claude/nav/database-ownership.md`**: both
found to substantially duplicate existing, already-more-detailed documentation
(`docs/architecture/03-bounded-contexts.md` and `04-database-erd.md` respectively). Not built.

---

## ADR-002: `check-hardcoded-counts.sh` — build-enforced backstop for stale hard-coded counts, not prose alone

**Status:** Accepted

**Context:** A documentation-cleanup pass found a stale hard-coded module count ("9 modules" /
"all 9 modules") surviving in at least six files (`docs/architecture/01-module-dependencies.md`,
`docs/architecture/README.md` in three places, `.claude/skills/deep-review/references/full-mode.md`'s
module scope list, and others) after `provider-profile-spring-boot-starter` was added as the repo's
tenth module. This project already has a stronger precedent for exactly this class of problem
(ADR-001 above: `check-adr-index-freshness.sh`) — prose discipline alone (the new
`doc-standards` skill's pre-write checklist) is the primary defense, but this repo prefers a
build-enforced backstop over prose alone wherever one is cheap to add.

**Decision:** Add `scripts/ai/check-hardcoded-counts.sh` — read-only, greps every `docs/`,
`CLAUDE.md`, and `README.md` file for the pattern `N modules`/`N module`, compares each match
against `pom.xml`'s actual `<module>` count, and fails (exit 1) on any mismatch. Excludes
`≥N modules` (a threshold rule, e.g. "used by ≥2 modules" in `platform-commons/CLAUDE.md" — not a
total-count claim) via a negative-lookbehind. Wired into `scripts/ci/entrypoint.sh`'s existing
`docs` stage alongside `check-adr-index-freshness.sh`/`check-flows-completeness.sh`. Scoped
deliberately narrow — module counts only, the one recurring pattern actually found stale — not a
general-purpose "any number might be stale" linter, which would be far noisier and harder to keep
green.

**Rejected alternative — prose-only fix (rely on the `doc-standards` skill's checklist alone)**:
rejected for the same reason ADR-001 rejected relying on the `/decision` command alone to keep the
ADR index fresh — a checklist consulted by whoever happens to be editing a file next has no
enforcement when someone (human or a differently-scoped Claude session) edits a doc without going
through that checklist. The skill's checklist remains the primary, first-line discipline; this
script is the backstop, not a replacement.

---

## ADR-003: `architecture-map.html`'s System page holds only the 3 entry-point cards; module browsing lives under Diagrams › Module Dependencies, one shared graph renderer instead of two

**Status:** Accepted

**Context:** No `DECISIONS.md` anywhere in the repo documented `architecture-map.html`'s
navigation structure before this entry — a real gap for a generated tool with real design
decisions behind it (drill-down pyramid, Cytoscape vs. Mermaid rendering choices), not just this
one delta. The System (first) page originally rendered three things at once: the 3 summary cards
(Tooling & Pipelines, Backlog, Diagrams), a hand-built domain-colored Cytoscape module dependency
map (`renderMap()`/`<div id="map">`, click a node to open its module page), and a domain-grouped
grid of module cards. Separately, the Diagrams screen's "Module Dependencies" group (source:
`docs/architecture/01-module-dependencies.md`) rendered the *same conceptual graph* through a
second, generic code path (`parseMermaidGraph()`/`renderCytoscapeDiagram()`, shared with `02-spi-
map`/`03-bounded-contexts`) — re-parsing the plain Mermaid text, with no domain coloring and no
click-to-navigate. Two implementations of "draw the module graph," one richer than the other —
exactly what `doc-standards/SKILL.md`'s "one fact, one canonical home" rule exists to catch.

**Decision:** System page trimmed to just the 3 cards. The domain-colored, click-navigable graph
(`renderMap()`, renamed `renderModuleDependencyGraph()`) moved to be the special-cased renderer for
the Diagrams screen's `01-module-dependencies` group specifically, replacing the generic Mermaid-
text-parse path for that one diagram only (`02-spi-map`/`03-bounded-contexts` keep the generic
path — no equivalently rich structured node model exists for them yet). It builds from
`moduleNodes` (the `pom.xml`-sourced canonical module list the whole model is already generated
from) rather than re-parsing `01-module-dependencies.md`'s Mermaid text a second time — not a
fork of the markdown's facts, since both are already anchored to the same `pom.xml` reality. Node
click still navigates to `renderModule()` (unchanged). The new renderer draws into the Diagrams
screen's existing `#diagram-cy` container and reuses its existing zoom toolbar
(`zoomDiagram()`/`#zoom-label`) instead of carrying its own — the old System-page-only
`systemCy`/`zoomMap()`/`#map-zoom-label` trio is deleted outright, not left dead.

**Consequences:**
- One graph-building function for the module dependency graph, not two — verified no other call
  site still references `renderMap`/`systemCy`/`zoomMap`.
- `scripts/ai/screenshot-architecture-map.sh` updated to match: the module-detail screenshot now
  drives `navigate({screen:'module',id:...})` directly via `page.evaluate()` rather than clicking a
  `.domain-group .card` on the System page (that page no longer has one) — canvas-coordinate
  clicking on a dagre-laid-out Cytoscape node was judged too fragile to hardcode a screenshot-script
  click at, direct navigation is an honest substitute for visually verifying the target screen
  renders correctly.
- `#map`'s now-orphaned CSS rule removed alongside its last consumer.

---

## ADR-004: `architecture-map.html`'s breadcrumb is a growing stack (one `navigate()`/`crumbLabelFor()` pair), not per-screen hand-built breadcrumb HTML

**Status:** Accepted

**Context:** ADR-003 moved module browsing under Diagrams › Module Dependencies, one hop deeper
than before (`System › Diagrams › Module Dependencies — Dependency Graph › <module>` instead of
the old flat `System › <module>`). The breadcrumb code at the time was a hand-built `if/else`
chain keyed on `view.screen`, with no navigation history at all — clicking a node forwarded into
the module page correctly, but there was no way back to the diagram short of clicking all the way
to "System" and re-navigating from scratch. Bolting a fix onto that `if/else` chain (e.g. hardcoding
one more special case for "module reached from a diagram") would have made the *next* screen added
to this tool need its own hand-written breadcrumb case too — the same "fix it in one place, not
scattered per-screen" question the user raised directly.

**Decision:** Replaced the hand-built breadcrumb with a single navigation stack (`crumbStack`,
never rewritten in place — only pushed when drilling into a new non-System screen, or truncated
when jumping back to an earlier point) plus one label function (`crumbLabelFor(view)`) mapping any
view object to its display text. `navigate(next)` is the only place that touches the stack;
`renderBreadcrumb()` is the only place that reads it. Same shape as this project's real
`BreadcrumbStep` pattern in `marketplace-app` (a growing stack, never rewriting existing segments —
see root `CLAUDE.md` "Breadcrumb Pattern") — deliberately reused rather than inventing a
differently-shaped mechanism for this generated tool. A generic `navigateBack()` (pop one level, or
go to System if the stack is empty) replaced the Diagrams detail view's own hardcoded
`navigate({screen:'diagrams'})` "← all diagrams" button, which would otherwise have *grown* the
stack instead of shrinking it now that plain `navigate()` always pushes.

**Consequences:**
- Every current screen (module, pipelines, backlog, diagrams-list, diagrams-detail) gets correct
  breadcrumb history and back-navigation for free — none of their own rendering code changed, only
  `crumbLabelFor()` needed one case per screen type.
- Adding a future screen means adding one `crumbLabelFor()` branch — `navigate()`/
  `renderBreadcrumb()`/`navigateBack()` need no changes, by construction.
- Verified via `scripts/ai/screenshot-architecture-map.sh`: System → Diagrams → Module
  Dependencies → click node → module detail → click the "Module Dependencies — Dependency Graph"
  breadcrumb segment lands back on the (re-rendered) graph, not a dead end.

---

## ADR-005: `docs/architecture/01-module-dependencies.md` retired entirely — its diagram, dependency table, and module-versions line render live on `architecture-map.html` instead; Key Observations carried over as static text in the generator

**Status:** Accepted

**Context:** ADR-003 stopped `architecture-map.html`'s Module Dependencies diagram from re-parsing
`01-module-dependencies.md`'s Mermaid text, rendering it from `moduleNodes` instead — but the
`.md` file itself still existed, still hand-maintained, still a second copy of facts the tool
already had. A first attempt at closing that gap spliced a generated Mermaid block back into the
`.md` between marker comments, checked by a new freshness gate — technically closing the drift
risk, but not the actual complaint: a file that still needs generating, committing, and verifying
on every `pom.xml` change is still ongoing cost for zero unique information, and it caused a real
bug during implementation (an early version of the splice function silently deleted the entire
Dependency Table / Key Observations / Module Versions sections below the marker — caught before
being committed, but confirming a hand-rolled in-place file splice is itself a maintenance
liability, not a neutral no-op).

**Decision:** Delete `docs/architecture/01-module-dependencies.md` outright. Its four sections now
live as follows, all reachable from `architecture-map.html`'s Diagrams › Module Dependencies page:
- **Dependency Graph** — the domain-colored, click-navigable graph already built from `moduleNodes`
  (ADR-003), unchanged.
- **Dependency Table** — regenerated as an HTML `<table>` on the same page, built from
  `n.edges.DEPENDS_ON_COMPILE/RUNTIME/OPTIONAL` (`moduleDepsScopeText()`) — mechanical, same source
  as the graph, not hand-typed.
- **Module Versions** — one line, sourced from the root `pom.xml`'s own `<artifactId>`/`<version>`
  (`ROOT_ARTIFACT_ID`/`ROOT_VERSION`, extracted the same way `module_deps()` reads per-module
  pom.xml files) — emitted into `architecture-model.json` as `rootArtifactId`/`rootVersion`.
- **Key Observations** — the one section that is genuinely editorial judgment, not a fact
  `pom.xml` encodes. Not mechanically regenerated: carried over verbatim into a static JS constant
  (`MODULE_DEPENDENCY_KEY_OBSERVATIONS`) in `generate-architecture-model.sh`'s HTML template. If
  these observations go stale, there is now exactly one place to fix them — no `DECISIONS.md`-style
  append-only history needed for seven sentences of interpretation, but no second, driftable copy
  either.

An "Export as Markdown" button on the same page builds a markdown snapshot of the table + key
observations + module versions client-side (`exportModuleDependenciesMarkdown()`, `Blob` + a
synthetic `<a download>` click) and triggers a browser download. Deliberately **not** a mechanism
for regenerating a tracked file: nothing it produces is committed back to the repo, so there is no
staleness risk to guard against — a considered alternative (keep the marker-spliced `.md` block,
just add an export button too) was rejected because it would still carry the original problem's
full cost (a committed, freshness-gated, hand-editable-by-accident file) for a benefit ("readable
without opening the tool") the export button already covers on demand.

**Consequences:**
- `scripts/ai/check-architecture-model-freshness.sh` reverted to checking only
  `architecture-model.json`/`architecture-map.html` — no third file to back up/restore/diff.
- `docs/architecture/README.md`'s "Files Overview" no longer lists a `01-module-dependencies.md`
  entry; every cross-reference to that file repo-wide (`.claude/commands/sync-docs.md`,
  `.claude/skills/doc-standards/SKILL.md`, `.claude/nav/context-loading.md`,
  `docs/architecture/03/06/07/08-*.md`, `integration-tests/DECISIONS.md`) repointed to
  `docs/architecture-map.html`'s Module Dependencies page.
- `docs/architecture/02-05-*.md` are unaffected — they still have no equivalent live-rendered
  replacement, so they keep being the authoring source for their own diagrams (`GRAPH_TYPE_KEYS`
  still routes `02-spi-map`/`03-bounded-contexts` through the generic Mermaid-text parser).
  Retiring those too, the same way, is a natural next step but not done here — each would need its
  own judgment call about which parts are mechanical vs. editorial, same as this one required.

**Follow-up fixes, same session:** the export button initially omitted the diagram itself
(`buildModuleDependencyMermaid()` added — same `moduleNodes` data, serialized to Mermaid text
client-side, since Cytoscape has no built-in "export back to Mermaid"). Dependency Table cells
were plain text; `moduleDepsScopeGroups()`/`moduleDepsScopeHtml()` added so each dependency name
is its own link to that module's page too (`moduleDepsScopeText()` kept, plain-text sibling for
the markdown export) — required adding `table.simple a` CSS, since a bare `<a onclick=...>` with
no `href` renders unstyled by default. The page-level toolbar (back/export) and the diagram's own
zoom controls were one shared `.diagram-toolbar` row; split apart on request — back/export now sit
directly under the `<h2>Diagrams</h2>` title (page-level nav), zoom controls moved to their own
right-aligned row directly above each diagram box (diagram-level control, not page-level).

---

## ADR-006: ADR references resolve to the real `DECISIONS.md` via `{id, title, file}` only — no embedded body text, no tracked line number

**Status:** Accepted

**Context:** A generated reference to an ADR needs to get a reader from "here's an id and a title"
to the real Context/Decision/Consequences prose without leaving the tool. Two richer designs were
tried and both rejected. **Embedding the full ADR body text** (a `"body"` field re-read from each
module's `DECISIONS.md`, shown in a modal) is exactly the duplication
`doc-standards/SKILL.md`'s canonical ownership table already rules against — "ADR rationale →
canonical home: `DECISIONS.md` → everywhere else: reference by ADR number, never restate the
reasoning inline." Embedding real prose into a second, committed, generated file is still two
copies of the same text, even when generation keeps them in sync (no *drift* risk doesn't mean no
*duplication*). **A file link plus a separately-computed line number** (`grep -n` for the ADR's own
heading, shown as `module/DECISIONS.md:42`) genuinely fixed the duplication problem, but is fragile
in a way nothing catches: add or edit *any* ADR earlier in the same file and every later line
number goes wrong until the next regeneration, silently — no drift-detection gate would notice. It
also bought no real navigation to begin with: a raw `.md` opened via `file://` renders as plain
text, not HTML, so there are no heading anchors for a `#fragment` to jump to; the line number was
always just a hint a human had to read and manually scroll/search for, no more precise than the ADR
id itself already displayed as the link's own visible text.

**Decision:** `json_adr_array()` emits `{"id","title","file"}` only — `file` is the module's real
`DECISIONS.md` path, nothing else. `adrFileLink()` builds a relative link (`../${a.file}`, relative
to `docs/architecture-map.html`'s own location, so it resolves correctly regardless of where the
repo is cloned) that opens the real file directly. The ADR id, already shown as the link's visible
text (e.g. "ADR-001 (query-lib)"), is what a human actually searches for (Ctrl+F) once the file
opens — it never goes stale, unlike a line number, because it doesn't depend on anything else in
the file staying in the same physical position. The "← back"/"Export as Markdown" toolbar
(`navigateBack()`/`exportModuleMarkdown()`) sits above the screen title, applied consistently to
every screen that has one — the export's own ADR section lists `id`/`title` plus the same file
reference, never body text.

**Consequences:**
- Zero ADR prose duplicated anywhere in `architecture-model.json`/`architecture-map.html` — every
  ADR is one canonical copy, in its own `DECISIONS.md`, exactly as `doc-standards/SKILL.md`
  requires. No line-tracking logic to keep correct.
- The Module screen itself carries no per-node "Architectural decisions" list or `adrFileLink()`
  call today — that responsibility moved entirely to the System-level flat ADRs screen (see this
  file's ADR-023); `renderModule()` shows Tables/Entities/Key services/Contracts/Depends-on only.
- Verified directly: opening the exported link resolves to the real file and shows the real ADR
  text (mirrored the relative path in an isolated directory and confirmed the browser navigates to
  and renders the actual `DECISIONS.md` content, not a 404/blocked request).

---

## ADR-007: ADR content embedded directly in `architecture-model.json`, opt-in via `--with-adr-details`, shown in a popup — Node.js replaces `awk` for markdown parsing

**Status:** Accepted

**Context:** Showing an ADR's real content without leaving the tool needed more than a plain file
link — a real click-to-view experience. A raw `.md` opened via `file://` renders as `text/plain`
(confirmed directly), so neither `#fragment` anchors nor the Text Fragments API scroll to anything;
`fetch()`/`XMLHttpRequest` against a sibling `file://` resource fails outright; `<iframe>` loads a
sibling file but its `contentDocument` is blocked from cross-document read. A separate
`<module>/DECISIONS.json`, dynamically `<script src>`-loaded per module, was tried and confirmed
working in both Chromium and Firefox — but rejected anyway: its correctness depends on
browser-specific `file://` security policy for cross-directory resource loading, which could
tighten in either browser or be locked down by an organization's policy with no code-visible
warning. A tool meant to "just work" when double-clicked should not have its core feature's
correctness depend on which browser opens it.

**Decision:**
1. **ADR content embeds directly into `architecture-model.json`**, not a separate file. For every
   module in `FULL_DECISIONS_MODULES`, the parsed `{title, adrs:[{id, title, status, body}],
   extra:[{heading, body}]}` object is embedded as a `"decisions"` field directly on that module's
   node — inlined into `architecture-map.html`'s own `const MODEL = ...` `<script>` block at
   generation time, the same mechanism every other MODEL field uses. No runtime file load of any
   kind for ADR content — zero `fetch`, zero `<script src>`, zero `iframe`, so zero browser-
   dependent behavior. `body` is kept as one raw markdown blob per ADR (not decomposed into
   separate context/decision/consequences fields), since real ADRs carry irregular structure
   (amendments, correction callouts, tables, numbered lists) a rigid schema would lose or mangle.
2. **Embedding is opt-in**, via `--with-adr-details` on `generate-architecture-model.sh` (off by
   default, same generation-time-flag pattern as `--with-sonar`/`--with-archunit`) —
   `decisions_json_for()` returns `null` for every module unless the flag is passed. ADR text
   changes rarely (only when a new decision is recorded), so a live server was considered and
   rejected as unnecessary complexity for data this stable. `MODEL.allAdrs` (the separate, always-
   lean list the ADRs screen's card grid reads, sourced from `.claude/nav/adr-index.md`) is unaffected
   either way — it always carries `id`/`title`/`status`/`module` for every ADR, so the popup always
   opens with a real title/status; only the body differs, falling back to a source-file link when
   the module's `decisions` field is absent (flag off, or module has no `DECISIONS.md`).
3. **Click opens a real popup**, not inline expand: one shared `<dialog id="adr-popup">` element in
   the page skeleton, populated and opened via `showModal()`, closed via an `×` button or the
   dialog's native `Esc`/backdrop-click behavior — a better fit than inline expand for longer ADR
   bodies (table + prose runs well past a comfortable inline-accordion height).
4. **Client-side `mdBlockToHtml()`** renders `body` (paragraphs/lists/tables) for the popup view — a
   deliberately narrow block-level markdown-to-HTML step, same "only what's needed" scope as
   `parseMermaidGraph()`, not a general markdown library.
5. **Markdown→JSON parsing is `scripts/architecture/md-to-decisions-json.js` (Node.js)**, not a
   hand-rolled `awk` state machine — `node` is already a first-class tool in this repo
   (`playwright/`). `JSON.stringify()` gives correct escaping by construction, closing off the
   escaping-bug class `awk` kept hitting live. A `--stdout <module>` mode prints one module's parsed
   object as a single line of JSON for the generator to embed.
6. **Reframed as one concrete piece of this tool's own Knowledge Pyramid design**: a generated model
   read through two projections, an AI layer with progressive token-minimal levels (L0 System → L1
   Module → L2 Contract → L3 Rule/Intent → L4 Test evidence → L5 Implementation) and a human layer
   (the visual explorer this tool is). This ADR's work is a first real piece of L3 content (relevant
   ADRs for a module), built for the human layer (the popup); the equivalent Claude-readable L3
   artifact is `md-to-decisions-json.js`'s `--extract` mode (see this file's Open goals). Format
   (markdown vs. JSON) was never the real lever for Claude's token/speed cost — measured directly:
   `DECISIONS.md` vs. the equivalent `DECISIONS.json` differ by only ~2.7% in byte size, noise at
   this scale; the real lever is whether Claude reads raw source at all for a task, versus a
   projection scoped to it.

**Consequences:**
- Rolled out to all 7 modules with their own `DECISIONS.md` (`attachment-spring-boot-starter`,
  `audit-spring-boot-starter`, `integration-tests`, `marketplace-app`, `platform-commons`,
  `query-lib`, `taxon-spring-boot-starter`; the other 3 record decisions in `marketplace-app`'s or
  `platform-commons`' `DECISIONS.md` per root `CLAUDE.md`, so have no file of their own to embed).
  Parser validated against all 7 before wiring in: ADR count matched real `## ADR-` heading count
  exactly in every file, and a non-whitespace character-count ratio (parsed body text vs. raw file
  content) landed in the expected 92-97% range everywhere.
- `generate-adr-index.sh` still parses markdown only — the "(N)" ADR count and whether the ADRs
  screen renders a module's entries at all still depend on `DECISIONS.md` staying present, even for
  a module with embedded body content.
- `exportModuleMarkdown()` is unaffected — still `id`/`title`/`file` reference only, no body text.
- `check-hardcoded-counts.sh` excludes `architecture-model.json`/`architecture-map.html` by path,
  since embedded ADR body text can legitimately contain an unrelated historical count (e.g. a past
  module-list size mentioned inside an old ADR's own prose) that would otherwise false-positive as
  a stale current-state claim — every other file under `docs/` is still checked for genuine
  staleness.
- Committed `architecture-model.json`/`architecture-map.html` default to `--with-adr-details` off
  (`"decisions": null`) — the richer embedded content stays available on demand, but isn't baked
  into what the freshness gate compares against.

---

## ADR-009: Cross-module ADR references — `**Also affects:**` tag, plus a generated pointer `DECISIONS.md` for modules with none of their own

**Status:** Accepted

**Context:** `user-spring-boot-starter` showed "Architectural decisions (0)" despite real ADRs
existing about it (`marketplace-app/DECISIONS.md` ADR-071, `platform-commons/DECISIONS.md`
ADR-026) — `adr-index.md` attributes every ADR to the single file it's physically written in, with
no way for a module merely *discussed* in another module's ADR to show up on its own page. Two
designs were compared: consolidate all decisions into one central file with module tags (rejected
— changes how every future decision gets recorded project-wide, and needs manually re-tagging
~188 existing ADRs), versus an optional tag on the existing per-file convention (chosen — additive,
zero migration, no change to where a decision gets written).

**Decision:**
1. An ADR may add `**Also affects:** module-a, module-b` directly after its `**Status:**` line.
   `generate-adr-index.sh` emits one extra table row per affected module for the same ADR (same
   file, same id) — the "Module" column gets the affected module, the id's own `(home-module)`
   parenthetical still names where the text actually lives, so the distinction is never lost.
2. `json_adr_array()` (in `generate-architecture-model.sh`) now derives each ADR's `file` field
   from that parenthetical, not from the module being queried — required, since a cross-listed
   entry's real file differs from the module its row is filed under.
3. Client-side rendering unified: every module's list now renders uniformly from `n.intent`
   (`renderAdrList()`), whether an entry is home-grown or cross-listed. Clicking resolves the
   ADR's real home module (`openAdrPopupForIntent()`) and opens the popup from *that* module's
   embedded `decisions` data if present, falling back to a real link to the source file otherwise
   (e.g. a cross-reference into a non-Maven-module `DECISIONS.md`, which has no embedded data).
   Replaces ADR-007's earlier `renderAdrJsonList()`/`openAdrPopup()`, which only ever looked at
   the currently-viewed module's own data.
4. `advertisement-spring-boot-starter`, `provider-profile-spring-boot-starter`, and
   `user-spring-boot-starter` (the 3 Maven modules with no hand-authored `DECISIONS.md`) each get a
   **generated, pointer-only** `DECISIONS.md` — a title, a "do not hand-edit" note, and a markdown
   list of whichever ADRs cross-reference them (`[ADR-071 (marketplace-app)](../marketplace-app/
   DECISIONS.md) — Title`), or "No ADRs currently cross-reference this module." if none yet. Zero
   ADR prose duplicated — every line is a real link. Regenerated by
   `generate-architecture-model.sh` (`POINTER_DECISIONS_MODULES`/`generate_pointer_decisions_md()`)
   every run; confirmed idempotent (byte-identical output across two consecutive full
   regenerations).
5. Two real ADRs tagged as a working demonstration: marketplace-app's ADR-071 and
   platform-commons' ADR-026, both `**Also affects:** user-spring-boot-starter`. The other two
   pointer-only modules currently show "no cross-references yet" — accurate, not a bug; tagging the
   rest of the ~188-ADR history is a curation task, not part of this ADR.

**Consequences:**
- CRLF bug, caught and fixed live: `generate-adr-index.sh`'s `awk` didn't strip trailing `\r`
  (this repo has CRLF line endings in some files, `marketplace-app/DECISIONS.md` and
  `platform-commons/DECISIONS.md` among them), so `"user-spring-boot-starter\r" != "user-spring-boot-starter"`
  silently dropped every cross-referenced row. Fixed with `{ sub(/\r$/, "") }` as the first rule in
  the `awk` program — the same class of CRLF issue ADR-006 already hit once, in a different script.
- `.claude/nav/adr-index.md` gained 2 new rows (188 total) for the two tagged ADRs; the 3 new pointer
  `DECISIONS.md` files are picked up by `generate-adr-index.sh`'s own file scan too, but produce no
  `## ADR-` matches — they land in "Known gaps" (harmless, correctly labeled non-standard format,
  not a real gap).
- Root `CLAUDE.md`'s "Architectural Decisions Log" note updated — it previously (incorrectly, after
  this ADR) implied these 3 modules have no `DECISIONS.md` file at all; corrected to describe the
  generated pointer file and added the third module (`provider-profile-spring-boot-starter`) the
  old note never listed.
- Verified directly: `user-spring-boot-starter`'s page shows 2 ADR items, both open the real popup
  content from their actual home module (`marketplace-app`), zero page errors.

---

## ADR-010: Non-Maven tooling directories (`scripts`, `scripts/ai`, `scripts/ci`, `scripts/sonar`, `playwright`) get a `SCRIPT_GROUP` node — same ADR mechanism as `MODULE`

**Status:** Superseded by ADR-033

**Context:** 5 directories carry their own `DECISIONS.md` (real ADRs, real history) but have no
`pom.xml`, so `MODULES` (sourced from the Maven reactor) never included them — they were entirely
invisible to `architecture-map.html`, including their own decisions, even after ADR-007/009 made
this possible for Maven modules. The Tooling & Pipelines screen also only ever showed Commands and
Skills, not the underlying scripts those commands wrap.

**Decision:** New node type `SCRIPT_GROUP`, one per directory in `SCRIPT_GROUP_DIRS`
(`scripts/ai`, `scripts`, `scripts/ci`, `scripts/sonar`, `playwright`), each carrying `files[]`
(its own `*.sh`/`*.js` at depth 1), `intent[]`, and `decisions` — identical shape and identical
generation path (`decisions_json_for()`, `FULL_DECISIONS_MODULES` extended to include these 5
directories) as `MODULE` nodes; `renderAdrList()`/`openAdrPopupForIntent()` needed zero changes to
work for them, since both already operate generically on `n.intent`/`byId[homeModule].decisions`
rather than assuming `type === "MODULE"`. The Tooling & Pipelines screen now splits into "AI
Tooling" (Commands, Skills, `scripts/ai`) and "Other Scripts" (the remaining 4 directories) via a
`category` field (`SCRIPT_GROUP_CATEGORY`). Commands/Skills `Source` column changed from plain
`<code>` text to a real link (`sourceLink()`) — same "resolve to the file, don't just print its
path" rule already applied to ADR references.

**Consequences:**
- `scripts/sonar/DECISIONS.md` shows its file list but no ADR section — it doesn't use the
  standard `## ADR-NNN:` heading format (a pre-existing, already-documented gap in
  `generate-adr-index.sh`'s own "Known gaps" output, not a new issue introduced here).
- Verified directly: 5 `SCRIPT_GROUP` nodes render, 0 page errors, file links and ADR popups both
  functional for every group that has ADRs in the standard format.

---

## ADR-014: SPI Map rebuilt live from real Java source, same pattern as Module Dependencies — `docs/architecture/02-spi-map.md` retired

**Status:** Accepted — `.md` file deleted 2026-08-05, after explicit user comparison and go-ahead

**Context:** The rendered SPI Map diagram needed the same treatment as Module Dependencies (01):
live from real source, an Export as Markdown button, then retire the `.md`. The retired
`docs/architecture/02-spi-map.md` was richer than `01-module-dependencies.md` — an Overview, the
diagram, a 7-subsystem "SPI Interface Details" table (Interface/Direction/Implementation/Purpose)
organized as one table per subsystem headed by its real Java package, subsystem-level editorial
notes (e.g. Attachment's "`AttachmentMediaChangeHook` does not exist..."), an "Implementation
Rules" section with concrete real-example bullets, 3 "Call Flow Examples", and a "File Locations
Summary." Grepping `public interface` under `platform-commons/*/spi/*.java` plus
`implements \bXName\b` across every starter + marketplace-app reproduces the diagram and File
Locations content exactly — genuinely mechanical, same bar as `module_deps()`'s own pom.xml
parsing. This same grep caught a real staleness bug in the retired `.md`: it claimed `UserIdMarker`
is "implemented by domain types (e.g. `UserDto`)" — no class in the codebase actually implements
it; the live version correctly reports "no implementation found" instead of repeating a claim
that's no longer true.

**What is *not* mechanical:** "Purpose" one-liners per interface (editorial summaries), the
subsystem-level editorial notes, "Implementation Rules"' concrete examples, and the 3 Call Flow
Examples (narrative traces) have no other source and are hand-preserved as static content in the
generator — the same exception Module Dependencies' `MODULE_DEPENDENCY_KEY_OBSERVATIONS` already
established, not a new precedent. "Implementation Rules"' *core* rule statement (not its concrete
examples) is a real link to `platform-commons/CLAUDE.md`'s "Hook and Port Implementation Rules"
instead, since restating that part would be actual duplication.

**Decision:**
1. `spi_map_json()` (`generate-architecture-model.sh`) builds `MODEL.spiMap` (`{nodes, edges,
   details}`) directly from the grep extraction above — a `SCRIPT_GROUP`-style live source, no
   Mermaid-text intermediate. `02-spi-map` gets its own synthesized `diagramGroups` entry
   (`file: "platform-commons/src (live)"`), mirroring `01-module-dependencies`'s pattern; removed
   from the markdown-extraction loop that still serves `03-05`. `details[]` carries
   `package`/`subsystem` (mechanically extracted via `grep "^package "`, subsystem = the path
   segment between `platform.` and `.spi`), grouped client-side in `renderSpiSubsystemTables()`
   using `SPI_SUBSYSTEM_LABEL` for the display headings.
2. `SPI_PURPOSE`/`SPI_SUBSYSTEM_NOTE` (bash associative arrays) and
   `spi_call_flow_examples_json()` carry the genuinely-editorial pieces (interface purposes,
   subsystem notes, call flow narratives) over as static content.
3. Both the Interface and Implementation(s) columns in the details table link to the real `.java`
   file (`spiFileLink()`, readable class name as the link text — same "short label, real link"
   pattern `adrFileLink()` uses for ADRs). Diagram nodes are clickable too — every node carries a
   `file` field in its Cytoscape data, and one shared `diagramCy.on("tap", "node[file]", ...)`
   handler opens it, so any current or future Cytoscape diagram with file-bearing nodes gets
   click-to-open for free.
4. Client-side: `renderCytoscapeDiagram(source)` (Mermaid-parsed diagrams) and `renderSpiMapGraph()`
   (live-generated nodes/edges) share one `renderCytoscapeFromGraph(nodes, edges, rankDir)`. SPI Map
   renders left-to-right (`rankDir: "LR"`) rather than the shared default top-to-bottom — reads
   better for this 2-level interface→implementation shape; other callers keep the default.
5. `docs/architecture/02-spi-map.md` is deleted (`git rm`) — same precedent
   `01-module-dependencies.md`'s removal set (ADR-005): compared content against the live version
   until every real gap was found and fixed (subsystem grouping/package headers, subsystem notes,
   Implementation Rules' concrete examples, the Overview intro paragraph, real per-cell file links),
   then removed only once parity was confirmed, not before.

**Consequences:**
- Verified directly: 44 nodes, 19 edges, 17 interface-detail rows, 0 duplicate Cytoscape ids,
  `UserIdMarker` correctly shows no implementation, all 7 subsystem tables render with real
  package-derived headings. Screenshot-confirmed the layout fix — modules render in distinct
  visible boxes, left-to-right flow, no more single-row cramping.
- `exportSpiMapMarkdown()` carries every one of the hand-preserved editorial pieces (Purpose,
  subsystem notes, Call Flow Examples, Implementation Rules' concrete examples, the Overview intro),
  not just the on-page render.
- A native-scrollbar zoom-responsiveness complaint on this and Module Dependencies turned out to be
  dead CSS, not a real interaction gap: `#diagram-cy-wrap`'s `scrollWidth`/`clientWidth` stay
  exactly equal through zoom (Cytoscape manages its own internal transform, not DOM size), so the
  shared `.diagram-wrap` class's `overflow: auto` never engaged for these diagrams. Fixed with
  `#diagram-cy-wrap { overflow: hidden; }` plus updated hint text on all Cytoscape-rendered diagrams
  stating both real drag behaviors explicitly: "drag a node to reposition it, drag empty canvas
  space to pan the view."

---

## ADR-015: Bounded Contexts stays hand-maintained (unlike Module Dependencies/SPI Map) — renamed, linked, and `/sync-docs` ordering made explicit

**Status:** Accepted — the diagram-rendering approach noted in Consequences is superseded by
ADR-016; the rename/link/ordering decisions remain current

**Context:** Asked to give `docs/architecture/03-bounded-contexts.md`'s diagram page the same
treatment as Module Dependencies (01) and SPI Map (02). Read the full file first — unlike those
two, most of its content is genuinely not mechanical: the Context Map's edges are conceptual
business relationships ("audited via", "can have", "category assignment via"), not a grep-able
`implements`/pom.xml fact; domain grouping is already flagged `domain_confidence: manual` in the
model itself; Integration Patterns, Risks, and the Cross-Domain-Dependencies prose are analytical
content, not extractable data. Proposed this assessment to the user before touching anything —
agreed: keep the file as the real, hand-maintained source, don't force a live rebuild where the
underlying facts aren't mechanical.

**Decision:**
1. Renamed `docs/architecture/03-bounded-contexts.md` → `docs/architecture/bounded-contexts.md`
   (dropping the numeric prefix, this file only — 04-08 unchanged) — every reference updated:
   `generate-architecture-model.sh` (`BOUNDED_CONTEXTS` path, the `stem`/`DIAGRAM_FILE_LABEL`/
   `GRAPH_TYPE_KEYS` keys, comments), `.claude/commands/sync-docs.md`, `.claude/nav/context-loading.md`,
   `.claude/nav/README.md`, `docs/architecture/README.md`. Left references inside historical/archival
   files (`backlog/completed/**`, `backlog/BACKLOG.md`'s dated narrative, this file's own earlier
   ADR history) untouched — they describe a past state under the name that was actually true then.
2. The Bounded Contexts diagram page in `architecture-map.html` now shows a real link to
   `bounded-contexts.md` plus a one-line explanation of *why* it stays hand-maintained (the
   conceptual-edges reasoning above) — resolve-by-reference with the reasoning stated once, not a
   silent "here's a link" with no context for why this one file didn't get the 01/02 treatment.
3. `/sync-docs`'s own workflow now states the ordering explicitly, not just implicitly via the
   mapping table: Step 4 updates `docs/architecture/*.md` files (including `bounded-contexts.md`)
   first, then runs `bash scripts/ai/generate-architecture-model.sh` as its last action — since the
   generator reads `bounded-contexts.md`/`DECISIONS.md` as input, running it first would bake in
   stale content. Step 3's "For `01`"/"For `02`" lines (now stale — neither gets a manual
   read-and-rewrite step anymore, both are fully automated) replaced with a note stating that
   directly, plus a new "For `bounded-contexts.md`" line.

**Consequences:**
- One trigger (`generate-architecture-model.sh`), run once, at a fixed point in `/sync-docs`'s own
  step order — not scattered across multiple implied trigger points the mapping table alone
  suggested. Matches the user's stated end goal ("один тригер перегенерував всю архітектуру") for
  this file; extending the same explicit-ordering treatment to `04-08` is a separate, not-yet-asked
  step, not assumed done here.
- Noted, not fixed (out of today's scope) at the time: the Bounded Contexts diagram itself still
  rendered via the generic Mermaid-parse + top-to-bottom Cytoscape path and had the same "cramped
  into one row" layout issue SPI Map had before its `rankDir: "LR"` fix. **Superseded by ADR-016**
  — investigating this surfaced a deeper, unfixable-the-same-way problem (the graph is genuinely
  cyclic, not just mis-ranked), and the diagram was dropped from the tool entirely in favor of a
  plain link.
- Verified directly: all 4 CI freshness gates pass, screenshot-confirmed the new note/link render
  correctly on the Bounded Contexts diagram page.

## ADR-016: Bounded Contexts diagram dropped from the tool entirely — real graph is cyclic, not just mis-ranked

**Status:** Accepted — the "dropped from the tool entirely" part is superseded by ADR-029; the
diagnosis below (why the Cytoscape+dagre compound pipeline specifically fails on this graph)
still stands and is exactly why ADR-029 restores the diagram through a *different* rendering path
instead of retrying the same one.

**Context:** Asked to fix the Bounded Contexts diagram's layout the same way SPI Map's was fixed
(`rankDir: "LR"`), and to add click-to-navigate + an Integration Patterns section below it,
mirroring SPI Map's Call Flow Examples — a direct follow-up to ADR-015's "noted, not fixed" bullet.

**Investigation:**
1. Passed `rankDir: "LR"` through to `renderCytoscapeDiagram()`/`renderCytoscapeFromGraph()` for
   this diagram only. Screenshot still showed every node in a single cramped column, just rotated
   (vertical instead of horizontal) — not fixed.
2. Dumped every node's resolved Cytoscape `x`/`y` via a direct Playwright script: every node had an
   *identical* `x`, confirming dagre's ranking was collapsing to one column regardless of
   `rankDir` — a structural problem, not a wrong CSS/config value.
3. Root cause #1 (real bug, fixed): the Mermaid source encodes, for 6 of the 7 domains, a pair of
   edges directly between a `subgraph` group id and one of its own nested children (e.g.
   `User -->|defines contract| UserPort` and `UserPort -->|implements in| User`, with `UserPort`
   declared *inside* the `User` subgraph — `parent: "User"` in the parsed Cytoscape compound
   model). An edge between a compound parent and its own descendant forms a 2-cycle dagre's
   compound-aware ranking can't resolve, and collapsing it drags the whole graph down with it. Fix:
   `renderCytoscapeFromGraph()` now drops any edge where one endpoint is the immediate parent of
   the other before building the layout (`layoutEdges` — kept for drawing, general rule protecting
   any future compound Mermaid diagram with the same pattern, not bounded-contexts-specific).
4. Re-tested after the fix (12 edges removed, confirmed via edge count 34 → 22): `x` spread was
   *still* zero. Compared directly against SPI Map (also a compound graph, confirmed working —
   real spread 107→415 after the same renderer) to rule out a renderer-wide regression — SPI Map
   was fine, so the problem was specific to this graph's shape, not the shared code.
5. Root cause #2 (real, structural, not a bug): traced the remaining 22 edges and found the domain
   layer forms a genuine cycle when each compound group is collapsed to one node — `UI` has edges
   into `UserPort`/`AdvPort`/etc. (children of other domains), and `Audit` has an edge into
   `HookImpls` (a child of `UI`), while `UI` also has a direct edge into `Audit`. Collapsed:
   `UI → Audit` and `Audit → UI` both exist, and every other domain sits on a path between them
   (`UI → <domain> → Audit`). This makes `{User, Advertisement, Attachment, Taxon, Audit, UI}` one
   strongly-connected component — only `Shared` (pure source, no incoming edges) sits outside it.
   `dagre`'s own documentation states compound-node layout does not reliably support cycles
   spanning compound boundaries; a whole SCC collapsing onto one rank is exactly that failure mode,
   confirmed directly rather than assumed.
6. Presented this finding to the user before doing anything further (three concrete options: scope
   a diagram-specific hub-edge exclusion to force a DAG shape; drop compound nesting for this one
   diagram in favor of flat color-coded nodes; or stop chasing the layout and accept/simplify).
   Discussion also surfaced a more basic question — does this diagram carry information genuinely
   unique versus Module Dependencies (01, real pom.xml deps) + SPI Map (02, real interface/impl
   edges) + each domain's own `CLAUDE.md`? Answer: partially (the DDD entity/table/role framing and
   labeled business relationships aren't duplicated verbatim anywhere else), but the diagram itself
   is a visual convenience over facts that already live elsewhere, not a unique source of truth —
   `bounded-contexts.md` is that source, not the picture.

**Decision:** Stop trying to force this specific graph into the tool's dependency-graph renderer.
`renderDiagrams()` gets a dedicated `g.key === "bounded-contexts"` branch: shows the explanatory
note (kept — the "why this file stays hand-maintained, not mechanical" reasoning from ADR-015) plus
a real link to `bounded-contexts.md`, with no canvas, no Cytoscape instance, no zoom controls, no
click-to-navigate, no Integration Patterns extras section. Removed as dead weight once the diagram
was dropped: `BOUNDED_CONTEXTS_MODULE_MAP`, `renderBoundedContextsExtrasHtml()`,
`bounded_contexts_integration_patterns_json()` (bash) and its `boundedContextsIntegrationPatterns`
model field, `"bounded-contexts"` from `GRAPH_TYPE_KEYS`, and the now-unreachable `isGraphType`
branch/variable in `renderDiagrams()` (both remaining `GRAPH_TYPE_KEYS` members — 01 and 02 — were
already handled by their own earlier explicit branches, so that branch had no live callers left).
The parent-self-edge fix in `renderCytoscapeFromGraph()` (step 3 above) is kept — it's a real,
general correctness fix independent of this diagram's fate.

**Consequences:**
- Bounded Contexts is no longer listed as a `draggable` diagram type on the Diagrams index card.
- No further layout engineering sunk into a graph that is genuinely cyclic at the domain level —
  the honest option given `dagre`'s documented limitation, not a workaround.
- If this diagram's visual form is revisited later, the two live options identified but not taken
  are: (a) a diagram-specific edge-exclusion-from-ranking rule (draw all edges, rank on an
  explicitly acyclic subset only), or (b) drop Cytoscape compound (`parent`) nesting for this one
  diagram — flat, color-coded-by-domain nodes instead of nested dashed boxes — since `dagre`'s
  built-in cycle-breaking is far more reliable on non-compound graphs.
- Verified directly: all 4 CI freshness gates pass; screenshot confirms the page renders only the
  note + real link, no canvas element present (`document.getElementById("diagram-cy")` is `null`).

## ADR-017: Database ERD rebuilt live from real Liquibase changelogs — descriptions moved into `remarks=`, `docs/architecture/04-database-erd.md` retired; the same "single source of truth" pattern also applied retroactively to SPI Map's purpose text

**Status:** Accepted — decision #3's hand-preserved list of actor-reference/no-FK point
relationships is superseded by ADR-034 (now derived mechanically from a `remarks=` marker
convention); the entity_type/entity_id generic-relationship portion of that same list, and every
other part of this decision, still stands.

**Context:** Asked to migrate `04-database-erd.md` fully into `architecture-map.html`, same spirit
as Module Dependencies (01)/SPI Map (02) — live diagram + real links + every piece of the
markdown's content accounted for, file kept on disk until content parity is verified (established
01/02 pattern: migrate, verify, delete only on explicit go-ahead).

While scoping where the per-column/per-table descriptive text (`04-database-erd.md`'s "Notes"
prose) should live, the user pushed back on the design that was about to be proposed — a
`declare -A` bash map inside the generator, the same shape `SPI_PURPOSE` already used for SPI Map's
interface descriptions. The objection: that shape is itself a second copy of information that
belongs next to the real thing it describes (a Java interface, a database column) — the generator
should be a pure aggregator of real source data, not a place where descriptive prose gets
hand-authored and duplicated. This reframed the task: not just migrate the ERD, but apply the same
correction retroactively to `SPI_PURPOSE`.

**Decision:**

1. **SPI Map's `SPI_PURPOSE` retired — descriptions moved into real Javadoc.** All 17
   `platform-commons/*.spi` interfaces now carry a Javadoc paragraph directly above their
   declaration (11 got one added; 6 already had one — kept as-is, since in one case
   `AuditDomainHook`'s real Javadoc was *more accurate* than the stale `SPI_PURPOSE` entry
   describing it, direct evidence the duplicated-copy design was already drifting).
   `spi_javadoc_purpose_for(file)` (new function, `generate-architecture-model.sh`) extracts the
   Javadoc block immediately preceding `interface X` live via `awk` — walks backward past
   blank/annotation lines (handles `@FunctionalInterface`) to the nearest `*/`, then to its
   matching `/**`, strips `*`/`{@code}`/`{@link}` markup. `SPI_PURPOSE` array deleted. Convention
   documented in `platform-commons/CLAUDE.md`'s "SPI Interface Naming" section (first line states
   `Port: X → Y.` / `Hook: X → Y.` matching the direction table, so both a human reading the
   `.java` file and the generator agree on the same text).
2. **Liquibase `remarks=` is the new single source for column/table meaning.** All 6 real
   changelogs with an actual `<createTable>` (`user`, `advertisement`, `attachment`, `audit`,
   `taxon`, `provider-profile`) got a `remarks="..."` attribute on every `<column>` and on each
   `<createTable>` itself, carrying over `04-database-erd.md`'s "Notes" text verbatim where it
   existed. Not a prod database — no `validCheckSum` compatibility concern, changesets edited
   directly rather than working around checksum drift. Convention documented in root `CLAUDE.md`'s
   "Database Changes" guideline (guideline 6).
3. **`scripts/ai/liquibase-schema-to-json.js`** (new, Node — same "real parser, not fragile
   awk/sed" precedent as `md-to-decisions-json.js`) parses the 6 changelogs: table name, module,
   real changelog path, per-column name/type/`remarks`/nullable/unique/primaryKey/inline FK
   (`references="table(col)"`), standalone `<addForeignKeyConstraint>`, `<createIndex>`,
   composite `<addPrimaryKey>`, and a narrow regex pass over raw `<sql>` blocks for the two
   patterns actually used (a `CHECK` constraint, a partial/GIN `CREATE INDEX`). `db_erd_json()`
   (bash) calls it and merges in one hand-preserved list — `conceptualRelationships` — for the ERD
   edges with **no real FK** (this codebase deliberately decouples actor-reference/cross-domain
   columns — `advertisement.created_by`, `audit_log.actor_id`, `provider_profile.city_taxon_id`,
   etc., see `marketplace-app/DECISIONS.md` ADR-034/ADR-035): 9 relationships, carried over
   verbatim from the retired file's ER diagram, same "genuinely editorial, no mechanical source"
   exception class as `spi_call_flow_examples_json()`. Real FKs (`taxon_translation`/
   `taxon_assignment` → `taxon`, `user_information`'s self-referential `deleted_by`) are **not**
   duplicated into this list — they come live from the parser.
4. **Render:** `buildDbErdMermaidSource()` generates a Mermaid `erDiagram` string live from
   `MODEL.dbErd` (native Mermaid ER rendering — PK/FK/UK badges, crow's-foot notation — since
   `parseMermaidGraph()`'s custom parser only understands `graph TB/LR` syntax, not `erDiagram`'s
   column-block notation; Cytoscape was never in the running here). Real FKs render as solid lines
   (Mermaid's "identifying relationship" `--`); the 9 hand-preserved conceptual ones render as
   dotted lines (`..`, "non-identifying relationship") — the notation itself carries the
   real-vs-conceptual distinction, not just prose. `renderDbErdTableSchemas()` adds an HTML
   table-schema section per table below the diagram (columns/types/constraints/`remarks`, real
   FKs, indexes, real changelog link) — same shape as SPI Map's subsystem tables.
   `exportDbErdMarkdown()` added for parity with 01/02's export buttons.
5. **Table ownership per module** (`MODULE_TABLES`, used elsewhere in the model for each module's
   "owns these tables" listing) was re-pointed from parsing `04-database-erd.md`'s `### table` /
   `**Module:**` markdown headings to calling `liquibase-schema-to-json.js` directly — one real
   source feeding two consumers, not a markdown mirror of the same fact.
6. **Content-parity check, then delete.** Diffed the full original file section-by-section against
   the live version. Table/column/constraint/index/FK data: full parity, plus two real staleness
   bugs the old markdown had already accumulated were caught and fixed in the process —
   `advertisement.ad_kind` and `attachment_snapshot.version` both exist in the real schema but were
   missing from the hand-maintained markdown entirely. Four narrative sections (Data Flow examples,
   Soft Delete Pattern, Extensibility, Performance Considerations) had no live equivalent — assessed
   each rather than reflexively re-adding a new static array (the exact pattern being corrected by
   this ADR): Data Flow's 3 examples turned out to be the *same* 3 flows SPI Map's Call Flow
   Examples already narrate, just from the SQL side instead of the call side — genuine duplication
   in the original hand-authored docs, not lost information; Extensibility is already carried by
   the `attachment`/`audit_log` table-level `remarks` ("Generic: can attach to any entity type...");
   Soft Delete Pattern and Performance Considerations are generic RDBMS-pattern commentary already
   inferable from the live `deleted_at` column remarks and the mechanically-extracted index list
   respectively — none of the four needed a new home. `docs/architecture/04-database-erd.md`
   deleted (`git rm`) on that basis, with every repo-wide reference repointed
   (`.claude/commands/sync-docs.md`, `docs/architecture/README.md`, `.claude/nav/context-loading.md`,
   `.claude/nav/README.md`) — historical mentions in `backlog/BACKLOG.md`'s dated narrative and this
   file's own ADR-001/history left untouched (describe a past state that was actually true then).
7. **Verified directly, not assumed:** `bash scripts/deploy.sh --reset` — a full rebuild against a
   wiped database — applied all 6 modified changelogs cleanly (`Liquibase: Update has been
   successful` for every changeset, app started). Queried Postgres directly
   (`col_description('user_information'::regclass, ...)`) and confirmed the `remarks` attributes
   became real `COMMENT ON COLUMN` entries in the database itself, not just changelog metadata —
   the single source of truth is genuinely queryable from either side (live tool or `psql`).

**Consequences:**
- Two generator-side static description stores (`SPI_PURPOSE`, and the design that would have
  become its ERD equivalent) are gone; the real count of "places that describe what an interface or
  column means" dropped from 2 to 1 for both subsystems.
- Any future SPI interface or database column automatically shows up correctly described in the
  live tool the moment its Javadoc/`remarks` is written — no second edit anywhere else, and no way
  for the two to drift (there is no longer a second copy to drift from).
- `docs/architecture/` now holds only `bounded-contexts.md` and `05-08-*.md` — Module Dependencies,
  SPI Map, and Database ERD all live exclusively in `docs/architecture-map.html`.
- All 4 CI freshness gates verified green after every step, not just at the end.

**Follow-up fixes, same session:** the new Database ERD page initially had three gaps versus the
Cytoscape-based diagrams (01/02) it was asked to match: the SVG entities weren't clickable, the
scroll/zoom mechanism looked inconsistent, and PK/FK/UK/line notation had no explanation. Fixed the
first and third directly:
- `wireDbErdEntityClicks()` (new) — after `mermaid.run()`'s promise resolves (the SVG doesn't exist
  synchronously before that, unlike `enableDragToPan()` which only needs the wrapper div), matches
  each rendered `entity-<NAME>-<uuid>` SVG group against the real table list
  (`t.name.toUpperCase().replace(/_/g,"")`) and wires a click → `scrollIntoView()` on that table's
  schema section (`id="db-table-<name>"`, added to `renderDbErdTableSchemas()`'s wrapper div).
- Added a "Notation" section (PK/FK/UK, solid-vs-dotted line meaning) — explicitly agreed this is
  fine as static/hardcoded content, unlike the table/column descriptions ADR-017 just moved out of
  the generator: ER notation symbols are fixed language constants, not domain facts that can drift
  from the code they describe.
- The scroll/zoom mechanism gap is not a bug: `erDiagram` syntax can only render via Mermaid's own
  engine (`parseMermaidGraph()` only understands `graph TB/LR`), so it necessarily uses the same
  CSS-transform-scale + native-scrollbar + `enableDragToPan()` mechanism `05-sequence-diagrams.md`
  already uses — Cytoscape's canvas-based pan/zoom (01/02) isn't available to a Mermaid-rendered
  SVG. Asked the user whether to invest in a fully custom draggable Cytoscape ER renderer anyway
  (accepting the loss of Mermaid's native crow's-foot notation) — declined, keep the native
  renderer.
- Also gave Module Dependencies (01) an "Overview" section (`renderModuleDependencyExtrasHtml()`)
  — SPI Map and the new Database ERD page both had one already, 01 didn't; user liked the ERD one's
  wording and asked for the same treatment. States the same fact pattern: real source
  (`pom.xml` `&lt;dependencies&gt;`), live-rendered, nothing to keep in sync elsewhere.

**Second follow-up round, same session — "Legend" everywhere, plus a "how this is built" block:**
user pointed out the first round's fixes were incomplete: the ERD's own legend was missing symbols
actually present in the diagram (the crow's-foot cardinality markers), the word "Notation" read
worse than "Legend", and only the ERD page had one at all despite the request being to add
explanations across the diagrams. Also asked for a concise "what libraries/tech build this page,
what feeds the JSON" block on the System screen.
- Inspected the real rendered SVG directly (Playwright, `<marker>` element ids) rather than
  guessing which crow's-foot symbols appear — confirmed `buildDbErdMermaidSource()` only ever
  emits `||--o{`/`||..o{` (never any other cardinality combination), so the Legend explains exactly
  those two symbols (`‖` = exactly one, `○&lt;` = zero or many), not a generic full-notation
  reference for symbols that never actually render.
- Renamed "Notation" → "Legend" (ERD), and added a "Legend" section to Module Dependencies (01) and
  SPI Map (02) too — each explaining what's actually different about *that* diagram (01: line
  style = compile vs. runtime scope, arrow direction, node color = domain; 02: dashed box = module
  boundary, blue box = Java class, arrow = "implements").
- Module Dependencies' domain-color legend is **built live** from `domainOrder`/`domainColor()` —
  the same functions the graph itself uses to color nodes — not a hardcoded color-to-domain table.
  Domain colors are assigned by first-appearance order over an 8-color palette, so a static list
  would silently go wrong the moment a 9th+ domain repeats a color (confirmed this actually happens
  today — `Shared Kernel` and `UI/Application Layer` share the same blue, an honest artifact of the
  live legend, not something a hand-written one would have caught or explained).
- Added a "How this page is built" block to the System screen (`renderSystem()`): rendering stack
  (Cytoscape.js + cytoscape-dagre for the draggable graphs, Mermaid.js for ERD/sequence), generation
  stack (this generator + the two Node.js parsers, linked via `sourceLink()`), and a one-line list of
  where each piece of `architecture-model.json` actually comes from — deliberately short, no
  restated prose from any `CLAUDE.md`, since the ask was explicitly for something concise a user
  reads in passing, not a duplicate of the governance docs.

**Toolbar layout bug, same session:** user flagged the ERD page's `← back`/`Export as Markdown` row
looked different from 01/02's. Root cause: 01/02's shared toolbar block already renders both
buttons together, above the `Diagrams` title; the ERD branch instead rendered its own *second*
toolbar div, with its own export button, *below* the title, papered over with a `margin-top:-8px`
hack. Fixed by adding `04-database-erd` to the shared toolbar's existing conditional (same pattern
as 01/02) and deleting the ERD branch's separate toolbar entirely — one shared row, no per-diagram
copy, no CSS patch needed.

## ADR-019: Bounded Contexts domain grouping and relationships now generated live from real code — no longer hand-typed in `bounded-contexts.md`

**Status:** Accepted

**Context:** User asked to mechanize `bounded-contexts.md`'s actual content (not just its
rendering, which ADR-029 already fixed), maximally, marking anything uncertain rather than
guessing, with the eventual goal of deleting the `.md` the same way 01/02/04 were. First
investigation (naive `ComponentFactory<XPort>` grep across the repo) was too noisy — 15+ files per
port, mixing UI components, config classes, and test support with real domain coupling, and this
codebase's own Module Import Rules mean starters never depend on each other directly, so
cross-domain relationships are always mediated through `marketplace-app`, not a clean
starter-to-starter signal.

**Real signal found on closer investigation:** `AuditActivityFieldsHook`/`AuditActivityEnrichHook`
implementations in `marketplace-app/spi/*.java` each declare an `entityType()` method returning a
specific `EntityType` enum value — this is a precise, first-class, intentional mechanism (not an
incidental mention), and maps directly to "this domain is audited via Audit"
(`AuditActivityFieldsHook`) or "this domain can have Attachment" (`AuditActivityEnrichHook`).
Combined with `TaxonPort.replaceAssignments()`'s real call site (inside
`provider-profile-spring-boot-starter` itself, confirmed direct per that starter's own
`CLAUDE.md`, unlike Advertisement's `marketplace-app`-mediated category writes) and the already-
computed `moduleNodes` pom.xml dependency edges (Shared → every domain), nearly every relationship
in the original diagram turned out to have a real, precise, mechanical source after all — the
initial pessimism from the naive grep was about the wrong signal, not about mechanization being
infeasible.

**Decision:**
1. `bounded_contexts_json()` (bash, `generate-architecture-model.sh`) → `MODEL.boundedContexts`:
   - `domains[]` — per domain, real `@Table` entity classes, real `*Service` classes, real tables
     owned (reusing `MODULE_TABLES`, already computed for the Database ERD), and real SPI
     interfaces it implements (same grep technique `spi_map_json()` already uses). `Shared`
     (platform-commons) and `UI` (marketplace-app) get domain-appropriate substitutes: Shared shows
     real counts (17 SPI interfaces, 25 DTOs, 9 core model classes); UI shows its real `*HookImpl`
     classes.
   - `relationships[]` — one add-and-dedupe helper (`add_rel`, merges multiple real signals for the
     same conceptual `from|to|label` triple into one edge with combined evidence, e.g. `User`'s two
     separate `EntityType` hooks — `USER` and `USER_SETTINGS` — both feed one `User -> Audit` edge
     rather than drawing a duplicate parallel line). Every relationship carries a `confidence` field
     (reusing the project's existing `extracted`/`inferred`/`manual` convention, the same one
     `MODULE_DOMAIN` already uses — not a new marker scheme) and an `evidence` string naming the
     real file/method/call site backing it. All 18 relationships found landed at `"extracted"` —
     the heuristic/uncertain tier this ADR was prepared to use for weaker signals wasn't needed in
     practice, since every relationship type had a real, named, first-class code signal once the
     right one was found.
2. `buildBoundedContextsMermaidSource()` (client JS, mirrors `buildDbErdMermaidSource()`) generates
   the `graph TB` Mermaid source live from `MODEL.boundedContexts` — real entity/service/table/port
   names as node labels, real relationship labels as edges, dashed for `Shared`'s `decouples`
   edges. Replaces `d.source` (which, at the time this ADR landed, still read verbatim from
   `bounded-contexts.md` for rendering — see ADR-029).
3. `renderBoundedContextsExtrasHtml()` adds an Overview + Domain Contents + a full Relationships
   table (Relationship / Label / Confidence / Evidence columns) below the diagram — every fact
   traceable to its real source, not just asserted.
4. `bounded-contexts.md` is **not deleted** — kept on disk as the hand-authored comparison source,
   same 01/02/04 discipline (migrate, verify content parity with the user, delete only on explicit
   go-ahead). Not yet compared word-for-word against the live version.

**Consequences:**
- One real, confirmed discrepancy already visible without a full comparison pass: the original
  diagram has `Attachment -->|audited via| Audit`, but there is no `EntityType.ATTACHMENT` value
  and no `AuditActivityFieldsHook` implementation for it — the live version correctly does not draw
  this edge. Whether the original was wrong (attachment changes aren't actually audited via the
  `EntityType` mechanism, `attachment_snapshot` is its own separate history table instead) or
  whether a real mechanism exists that this pass's signals didn't catch is not yet resolved — flag
  for the content-parity pass before any deletion decision.
- The live version surfaces a relationship the original never had at all: `Advertisement -> Taxon`
  category-assignment coupling exists in real code (`marketplace-app`'s `AdvertisementSaveService`
  calls `TaxonPort.replaceAssignments()`, exactly the same real mechanism `Provider -> Taxon` uses)
  but was never drawn in `bounded-contexts.md`. Added as a second `"category assignment via"`
  detection rule (Advertisement's real call site is in `marketplace-app`, not its own starter,
  unlike Provider's direct one) — found and fixed in the same pass, not left as a noted gap.
- Verified directly: 0 page errors, real click-to-navigate confirmed (clicking the `User` cluster
  navigates to `user-spring-boot-starter`'s module page), screenshot-confirmed all 8 domains
  render with real content and all 18 relationships render with real evidence. All 4 CI freshness
  gates pass.

**Follow-up polish, same session:** user asked for real links in Domain Contents (not just plain
text), a per-entry breakdown of *what kind* of thing each item is (class vs. interface vs. table),
visibility into *what data* actually crosses each relationship, a "Legend" section matching
01/02/04, and pointed out two real gaps versus the other three live diagrams: no "Export as
Markdown" button, and no "(live)" marker on the Diagrams list card.
- `json_named_file_array()` (bash, `generate-architecture-model.sh`) replaces the plain
  `json_str_array()` calls for entities/services/tables/ports — each item is now `{name, file}`
  with a real relative path (tables link to the real changelog, ports to the real `.spi` interface
  file, entities/services to their own real `.java` file). `bcItemLink()` (client JS) renders each
  as a real link when a file is present, plain text otherwise (Shared's count summaries have none).
- `renderBoundedContextsExtrasHtml()`'s Domain Contents now groups each domain's items into four
  labeled rows (Entities / Services / Tables / Ports) instead of one flattened comma list — a
  reader can tell what kind of thing each name is without having to already know the codebase.
- `BC_LABEL_PAYLOAD` (bash) — a small map from relationship *label* to a real "what crosses"
  description, checked directly against the real `Port` method signatures it describes
  (`AuditPort.capture*()` all take `AuditableSnapshot`; `AttachmentPort.getMediaSummaries()`
  returns `AttachmentMediaSummaryDto`; `TaxonPort.replaceAssignments()` takes a `Set<Long>` of
  taxon ids) — not guessed. Rendered as a new "What crosses" column in the Relationships table.
- Added a "Legend" section (box = bounded context, solid line = real relationship, dashed line =
  "decouples" compile-time-only dependency) — same shape 01/02/04 already have.
- `exportBoundedContextsMarkdown()` added, wired into the shared toolbar's existing conditional —
  parity with 01/02/04's export buttons, none of which this diagram had until now.
- The Diagrams list card now shows `"real code (live)"` instead of the real
  `bounded-contexts.md` path — `bounded-contexts` moved out of the markdown-mermaid-extraction loop
  entirely and into the directly-synthesized `diagram_groups_json` list alongside 01/02/04 (same
  pattern ADR-005/ADR-014/ADR-017 already established), since the diagram content genuinely no
  longer depends on that file for rendering, only for comparison.
- Real layout bug found and fixed: the Relationships table's long unbroken evidence strings (e.g.
  the merged multi-hook evidence list) pushed the table wider than its container, overflowing the
  page. Fixed generically on the shared `table.simple` rule (`table-layout: fixed` +
  `overflow-wrap: anywhere` on `td`) rather than a one-off fix — benefits every table in the tool
  that might hit the same problem with long unbroken content, not just this one.

**Content-parity investigation, same session (before any deletion decision):** unlike SPI Map/ERD,
where investigation found nothing genuinely lost, this file has real narrative/analytical content
with no live equivalent: `Domain Details`' per-domain prose (specific cross-domain call narratives,
negative facts like "User domain does not depend on other starters", architectural nuances like
`DefaultTaxonPort` being a coordination layer rather than pure delegation, and why
`TaxonAuditHook` does not exist), `Shared Kernel`'s full 5-category breakdown (SPI/DTOs/Models/API
Markers/Utility, with concrete examples like `YoutubeUtil`), a `Domain Independence` section, and a
`Risks & Future Considerations` section cross-referencing `06-coupling-analysis.md`. Confirmed
`Integration Patterns` is **not** a loss — word-for-word, it narrates the same 3 flows SPI Map's
own Call Flow Examples already do, just from the domain side instead of the call side. Presented
this finding to the user before proceeding with any deletion — awaiting a decision on whether to
carry the four genuinely-unique sections over as hand-preserved static content (same exception
class as SPI Map's Call Flow Examples) before retiring the file, or accept the loss, or keep the
file for now. `bounded-contexts.md` is **not deleted yet**.

## ADR-020: `docs/architecture/05-08-*.md` retired in full — SonarQube + ArchUnit metrics, live coupling checks, and the tool's own files relocated into `docs/architecture/`

**Status:** Accepted

**Context:** The remaining four hand-maintained docs (`05-sequence-diagrams.md`,
`06-coupling-analysis.md`, `07-risk-report.md`, `08-scorecard.md`) were investigated for the same
"migrate live, delete only after content parity" treatment already applied to 01/02/04/Bounded
Contexts. Findings, each checked against a real running system or real file content before being
acted on:
- `05` — no tool makes sequence/call-order diagrams live without real runtime instrumentation
  (checked: JetBrains SequenceDiagram plugin — IDE-only; `plantuml-generator-maven-plugin` — real
  Maven plugin but static call-hierarchy from one start method, not a curated flow, outputs
  PlantUML not Mermaid; ArchUnit — checks dependency rules, doesn't generate sequence diagrams;
  OpenTelemetry+Tracetest — the real path, but Playwright already exercises the same flows this
  file documents, reducing the remaining lift to instrumenting the app + writing a trace→Mermaid
  converter, still out of scope here).
- `06`'s violation-check sections are literally written as "run this grep → here's the result" —
  directly re-runnable.
- `07` was checked row-by-row against `06` before any dedup decision — an earlier draft's "these 3
  tables are duplicates" claim was **wrong** (different row counts, extra columns each direction);
  4 pieces (Module Size, Largest Java Files, Constructor Injection, God Packages) are genuinely
  mechanizable once verified against real current content.
- `08`'s 7-dimension 1-10 editorial scorecard has no mechanical source at all — checked whether
  SonarQube's new "Architecture" beta feature (coupling/cohesion visualization) could feed it:
  confirmed no public API (`coupling` metric key does not exist on `/api/measures`).
- SonarQube (already running at `localhost:9099`) and ArchUnit (already a `marketplace-app`
  dependency, `archunit-junit5` 1.4.2) both turned out to supply real numbers that `07`/`08` were
  trying to approximate by hand: SonarQube gives `ncloc`/`complexity`/`cognitive_complexity`/
  `code_smells`/`duplicated_lines_density` per module (via `/api/measures/component_tree`, since
  SonarQube has no aggregate component for a module's own `src/main/java` root — only real leaf
  package directories are indexed, so per-module numbers are summed from all matching leaf
  packages); ArchUnit's `ArchitectureMetrics.componentDependencyMetrics()` gives real Efferent/
  Afferent Coupling, Instability, and Abstractness per module from the actual class-dependency
  graph, no external server needed.

**Decision:**
1. `scripts/ai/generate-architecture-model.sh` gained 6 new data-producer functions
   (`sonar_metrics_json`, `archunit_metrics_json`, `coupling_checks_json`,
   `largest_java_files_json`, `constructor_injection_json`, `god_packages_json`) and
   `ensure_sonar_fresh()` (compares SonarQube's last analysis date against the newest `.java`
   file's mtime, same staleness-check pattern `integration-tests/run.sh` already uses; triggers
   `bash scripts/sonar.sh --no-gate` if stale or unreachable). All optional-data sources degrade to
   `null`/empty gracefully — the tool never hard-fails on a missing Sonar server or a not-yet-run
   `ArchitectureMetricsExport`.
2. New `marketplace-app/src/test/java/org/ost/marketplace/architecture/ArchitectureMetricsExport.java`
   — a `@ArchTest` method that computes the ArchUnit metrics above per module and writes them to
   `marketplace-app/target/architecture-metrics.json` every time `bash scripts/unit-tests.sh` runs.
3. `ArchitectureRulesTest.java` gained 6 new `@ArchTest` rules closing real gaps between
   `.claude/rules.md`/module `CLAUDE.md` text and the existing 8: starters must not import sibling
   starters' packages; marketplace must not import starter internals (`util`/`services`/
   `repository` packages, not just `repository` under `ui`); `*Util` classes have private
   constructors; top-level `*Config` classes are `@Configuration` (nested classes with the same
   name suffix, e.g. a `Parameters`-style record, are a real, unrelated naming collision and are
   excluded); `MessageSource` is only used in `I18nServiceImpl` (the `@Bean` factory method itself,
   `I18nConfig`, is excluded — it's the thing that constructs the bean, not "raw usage" of it);
   packages are free of cycles (`SlicesRuleDefinition.slices().matching("org.ost.(**)")` — stronger
   than the module-level DAG check alone, catches cycles between packages *within* one module too).
4. Client JS: a new "Code Metrics" section on the Module page (Sonar + ArchUnit numbers together),
   and a new "Architecture Checks"/"Largest Java Files"/"Constructor Injection"/"Largest Packages"
   set of sections on the Module Dependencies page.
5. `05`/`06`/`07`/`08` deleted in full via `git rm`, full content of all four captured in
   `improvement-142` first (same "capture before delete" discipline as `bounded-contexts.md`).
   `07`'s 3 "Architectural Debt" TODO items moved into `backlog/BACKLOG.md` as real tracked notes,
   not just archived.
6. `docs/architecture-map.html`/`docs/architecture-model.json` moved into `docs/architecture/`
   (`git mv`), so every architecture artifact lives under one directory. Required updating: the
   generator's own `OUTPUT`/`HTML_OUTPUT` path constants; 5 relative-link generators in the client
   JS (`adrFileLink`, `sourceLink`, `spiFileLink`, `bcItemLink`, and the Cytoscape node-click
   handler — `../` became `../../`, since the HTML now sits one directory deeper); 3 other scripts'
   path constants (`check-architecture-model-freshness.sh`, `check-hardcoded-counts.sh`'s grep
   exclusion pattern, `screenshot-architecture-map.sh`); and prose references in
   `.claude/commands/sync-docs.md`, `.claude/skills/doc-standards/SKILL.md`, `.claude/nav/README.md`,
   `.claude/nav/context-loading.md`. `docs/architecture/README.md` rewritten to describe the current
   state (a pointer to the live tool) rather than the 8 now-deleted files it used to index.

**Consequences:**
- Verified directly, not assumed: after the file move, `sourceLink()`'s generated `href` for a
  real module's `DECISIONS.md` resolves correctly (`../../marketplace-app/DECISIONS.md`, confirmed
  via a real headless-browser click-through in an isolated container mirroring the real relative
  directory structure). The new "Code Metrics" section renders real numbers with no JS errors —
  confirmed the same way, after catching and fixing a real bug where numeric fields (e.g.
  `sonar.ncloc`) were passed through the shared `esc()` HTML-escaping helper, which assumes a
  string and throws on a number (`(s || "").replace is not a function`); fixed by not
  HTML-escaping values that are already known-safe numbers, not by changing the shared `esc()`.
  `largest_java_files_json()`/`constructor_injection_json()` initially scanned the whole repo
  including `src/test/java` — caught real test files appearing in a "largest files" list meant to
  reflect production complexity; fixed by scoping both to `*/src/main/java/*` only, matching
  `god_packages_json()`'s scope (which was already correct).
- `06`'s "Architecture Checks" grep-based verification and `ArchitectureRulesTest`'s new
  `@ArchTest` rules cover overlapping ground (starter-to-starter imports, Vaadin-in-starters) via
  two different mechanisms — the grep checks are visible on the live tool's Module Dependencies
  page (human-facing), the `@ArchTest` rules are build-breaking (CI-facing). Deliberately not
  deduplicated into one mechanism: the tool's own checks need to run without a `mvn test` cycle
  (fast, always available when regenerating), while `@ArchTest` needs to fail the build, which the
  tool's own read-only HTML page cannot do.
- Generation is no longer unconditionally "~50s" — when SonarQube data is stale,
  `ensure_sonar_fresh()` triggers a full rescan (compile + `sonar-scanner`, confirmed ~30-60s in
  this run) before the model is built. Accepted trade-off, not a regression caught after the fact —
  explicitly weighed and approved before implementation.

## ADR-021: Sonar/ArchUnit fetch made opt-in (default off); architecture-generation tooling — and this file — moved into `scripts/architecture/`, a sibling of `scripts/ai/`

**Status:** Accepted

**Context:** Two related requests. First: `ensure_sonar_fresh()`/`sonar_metrics_json()`/
`archunit_metrics_json()` ran unconditionally on every generator invocation, so a plain run could
trigger a multi-minute SonarQube rescan with no way to opt out — confirmed by reading the call
sites, not assumed. `renderModuleCodeMetricsHtml()` was checked and already returns `""` when both
metric sources are `null` for a module, so no separate "hide empty sections" fix was needed once
the default flips. Second: `scripts/ai/` held two distinct concerns side by side with no folder
separation — architecture-map generation (the large, actively-growing piece) and ADR-index/flows/
doc-standards backstop tooling (a separate, smaller concern). An initial attempt moved the
generation files into a `scripts/ai/architecture/` **subfolder** (nested under `scripts/ai/`,
`DECISIONS.md` left behind at `scripts/ai/DECISIONS.md`) — corrected mid-implementation to a
**sibling** `scripts/architecture/` directory instead, once it became clear the nested shape still
mixed the two concerns under one parent for no real reason.

**Decision:**
1. Added `--with-sonar`/`--with-archunit` flags (`for arg in "$@"` parsing, same shape
   `integration-tests/run.sh` already uses for `--sandbox`/`--no-check`), both off by default.
   `ensure_sonar_fresh` and the two metrics functions now only run when their flag is passed;
   `sonarMetrics`/`archUnitMetrics` are `null` in the generated model otherwise.
2. Moved `generate-architecture-model.sh`, `check-architecture-model-freshness.sh`,
   `screenshot-architecture-map.sh`, `liquibase-schema-to-json.js`, `md-to-decisions-json.js`,
   `architecture-map-screenshots/`, **and this `DECISIONS.md` file itself** into a new
   `scripts/architecture/` directory, a sibling of `scripts/ai/`, not nested under it. ADR numbers
   in this file are unchanged (still ADR-001 through ADR-021) — only the file's own path moved,
   since those numbers are referenced by number from `.claude/rules.md`, other scripts, and this
   generator's own rendered output; renumbering was ruled out as actively dangerous. Every
   in-repo `scripts/ai/DECISIONS.md ADR-NNN` reference (this file's own comments in the generator,
   `md-to-decisions-json.js`, `generate-adr-index.sh`, `check-hardcoded-counts.sh`) was updated to
   `scripts/architecture/DECISIONS.md ADR-NNN` in the same pass — the whole file moved, so every
   pointer to it moves too; this is a live path reference, not historical prose about a past event,
   so it doesn't fall under the append-only-history exception.
3. `scripts/ai/` keeps `check-adr-index-freshness.sh`, `generate-adr-index.sh`,
   `check-flows-completeness.sh`, `check-hardcoded-counts.sh` — the ADR-index/flows/doc-standards
   concern — but now has **no `DECISIONS.md` of its own**. It still gets its own `SCRIPT_GROUP`
   node (files-only: `decisions_json_for()`/`adr_intent_for_module()` both degrade to
   empty/`null` for a module with no `DECISIONS.md` on disk and no cross-references pointing at
   it, rather than needing a special case in the render path) so its 4 files stay visible on the
   Tooling & Pipelines screen instead of disappearing.
4. `REPO_ROOT` in the three moved `.sh` files ended up unchanged in net effect
   (`scripts/architecture/<file>.sh` is the same depth below repo root as the original
   `scripts/ai/<file>.sh` was) — but this took two corrections to get right: first bumped from
   `../..` to `../../..` for the (later-reverted) nested-subfolder attempt, then back to `../..`
   once the sibling-directory shape was adopted. The kind of self-referential path bug this class
   of reorg risks at every depth change, not just once.
5. The `scripts/ai` `SCRIPT_GROUP` node's `evidence` field (used for the "view full file" link)
   is now computed conditionally — `"$d/DECISIONS.md"` only when that file actually exists,
   `"$d"` (the directory itself) otherwise — rather than assuming every `SCRIPT_GROUP_DIRS` entry
   owns a `DECISIONS.md`, which stopped being true the moment `scripts/ai` lost its own.

**Consequences:**
- `docs/architecture/architecture-model.json`/`architecture-map.html` regenerated with the new
  default — `sonarMetrics`/`archUnitMetrics` reset to `null` in the committed copy. The richer data
  stays available on demand (`--with-sonar --with-archunit` locally) but is no longer baked into
  what CI/the freshness gate compares against.
- Every external reference to the 5 moved files' old `scripts/ai/<file>` path needed updating in
  the same pass: root `CLAUDE.md` (2), `platform-commons/CLAUDE.md` (1),
  `.claude/commands/sync-docs.md` (3), `scripts/ci/entrypoint.sh` (1), plus the generator's own
  self-referencing invocations of its sibling `.js` parsers and its rendered
  `"regenerate via ..."` strings (which also flow into the 3 generated-pointer `DECISIONS.md`
  files for `advertisement`/`user`/`provider-profile-spring-boot-starter` — those regenerate
  automatically, never hand-edited). Historical ADR prose in *other* modules' `DECISIONS.md` files
  that mentions the old bare `scripts/ai/<file>` path (if any) is left as-is, per this project's
  append-only-history convention — genuinely historical prose describes what was true when
  written, distinct from the live cross-file pointers corrected in point 2 above.
- `.claude/nav/adr-index.md` regenerated (`bash scripts/ai/generate-adr-index.sh`) in the same pass —
  mandatory whenever any `DECISIONS.md` changes, including a path-only move like this one.

## ADR-023: System screen gains a flat ADRs screen — status-filterable, grouped by module, with a hand-maintained glossary at the bottom

**Status:** Accepted

**Context:** No single screen summarized every ADR across the repo; a reader had to already know
which module's `DECISIONS.md` to open. Separately, general terms/concepts (starting with a concise
explanation of what an ADR *is* in this repo — not a changelog, not a code comment, not a backlog
issue, never renumbered) had no home either.

**Decision:**
1. `all_adrs_json()` (bash) parses `.claude/nav/adr-index.md` (same source `adr_intent_for_module()`
   reads) into a flat, deduplicated `MODEL.allAdrs` array — `{id, module, status, title}` per ADR,
   keeping only each ADR's home-module row and skipping the "Also affects" cross-reference
   duplicate rows that table also carries.
2. A 5th System card opens the **ADRs** screen: status-bucketed summary cards (bucketed by the
   status text's first word only, e.g. `Accepted`/`Superseded`/`Deprecated` — this repo's
   `Status:` lines are often long free-form annotations, not bare words, so bucketing by the
   literal full string would fragment the summary into dozens of near-duplicate 1-count cards),
   each clickable (`setAdrStatusFilter(status)`, same `card-active` pattern the Backlog screen
   uses) plus an "All" card, narrowing the table below. The table itself is grouped by module —
   one `<section>` per module (first-appearance order), the module name as a clickable heading
   (`navigate({screen:'module', id:...})`, scoped to real `MODULE`-type nodes only — `SCRIPT_GROUP`
   groups stay plain text since `renderModule()` reads fields they don't carry) with its
   `sourceLink` right there. Clicking an ADR id opens the shared `#adr-popup` dialog
   (`openAdrPopupForAdr(id, module)`), rendering that module's embedded ADR body via
   `mdBlockToHtml()` when present, falling back to a real link to the source file otherwise.
3. A hand-maintained glossary (`GLOSSARY` array in the generator, `{term, body}` pairs — same
   "genuinely non-mechanical content" exception SPI Map's Call Flow Examples and Bounded Contexts'
   narrative sections already use) renders as an "Overview" section at the very bottom of the ADRs
   screen, below the last module group — not a separate System card/screen of its own. First entry:
   "ADR (Architectural Decision Record)" — what it is, how it's used in this repo (`/decision`,
   never edited to remove content, `Status: Superseded` instead of deletion), and its boundaries.
4. Every System-card-reached screen (Tooling & Pipelines, Backlog, Diagrams list, Docker, ADRs)
   gets a "← back" button via one shared `backButtonHtml()` helper, matching the toolbar the
   Module/diagram-detail screens already had.
5. The Module page's own former "Architectural decisions" section is removed entirely — every
   module's ADRs are already one click away, grouped, on the ADRs screen, so keeping a second copy
   on the Module page would be a duplicate list. `exportModuleMarkdown()`'s own
   `## Architectural decisions` section is unaffected — a standalone export has no "click through to
   another screen" option, so keeping the ADR list there is the only way that export stays
   self-contained.

**Consequences:**
- System screen's card grid grows from 4 to 5 cards.
- Adding a glossary term later means appending to the `GLOSSARY` array — no new plumbing.
- `#adr-popup` sets `overflow: hidden` on the `<dialog>` element itself (the popup body's own
  `overflow: auto; max-height: calc(80vh - 60px)` already scrolls) — without it, a sufficiently
  tall ADR body produces two independent scrollbars (dialog + inner body) instead of one.
- Firing a second `generate-architecture-model.sh` invocation before the first finishes can race on
  the shared output file and corrupt `architecture-model.json` into invalid JSON — a repeatable
  failure mode of this specific script (multi-second wall-clock runtime, writes one shared output
  file), not a one-off bug. Always let one run finish (or confirm no matching process is running)
  before starting another.

## ADR-024: SonarQube/ArchUnit metrics consolidated onto a dedicated System-level "Code Quality" screen, removed from Module pages

**Status:** Accepted

**Context:** User request (Part A of the original, much larger improvement-144 scope, revived
here in a smaller shape than originally drafted): rather than each module's own page carrying a
"Code Metrics" section (`renderModuleCodeMetricsHtml()`), gather all SonarQube + ArchUnit data
into one System-level card/screen, with a clear breakdown of which numbers came from which source,
and stop repeating it per module.

**Decision:**
1. New 7th System card, "✅ Code Quality" → new `renderCodeQuality()` screen. Two separate
   `<section>`s, one per source (never merged into one combined table) — "SonarQube" (module | Java
   files | lines of code | complexity | cognitive complexity | code smells) and "ArchUnit" (module
   | efferent coupling | afferent coupling | instability | abstractness), each with its own
   `Source:` line (analysis date for Sonar, "from the last `bash scripts/unit-tests.sh` run" for
   ArchUnit) and its own empty-state message naming the exact opt-in flag/prerequisite when that
   source's data is `null`. Every module name in both tables is a `module-link` back to that
   module's own page (verified data still round-trips through real module pages, just not
   duplicated onto them anymore).
2. `renderModuleCodeMetricsHtml()` (the old per-module inline table) and its one call site inside
   `renderModule()` are both removed.
3. Verified with real data: temporarily regenerated with `--with-sonar --with-archunit` (SonarQube
   already running, `marketplace-app/target/architecture-metrics.json` already present from an
   earlier `bash scripts/unit-tests.sh` run in this session) to confirm both tables actually
   populate correctly, then regenerated again with no flags before committing — the committed
   default stays `sonarMetrics`/`archUnitMetrics: null`, per ADR-021/Step 0's opt-in design; this
   verification pass never changed that default.

**Consequences:**
- Module pages get shorter (one less section); the "Code Quality" screen is now the single place
  either metric source is ever displayed.
- No diagram screen needed a change — confirmed by grep before starting that
  `renderModuleCodeMetricsHtml`'s only real call site was `renderModule()`; no diagram ever
  rendered Sonar/ArchUnit data directly.

**Amendment (same session, immediate follow-up): per-column Overview + green/yellow/red
thresholds added, and the committed baseline deliberately carries real Sonar/ArchUnit data (not
`null`) — a one-off, explicit exception to ADR-021's default.**
1. **Column descriptions.** New `<section class="block"><h3>Overview</h3>` at the bottom of the
   Code Quality screen — one row per field (`CODE_QUALITY_GLOSSARY` array) explaining what it
   means, reusing the exact tooltip text drafted (but never implemented) in the original,
   larger version of this issue before it was trimmed down to just the companion-server plan.
2. **Color thresholds** on derived ratios only, never on raw counts (Ce/Ca, code smells, complexity
   totals have no universal per-module threshold — only ratios do): 3 new SonarQube table columns
   (Complexity/file, Cognitive/file, Code smells/1k LOC) and 1 new ArchUnit table column (Distance
   from Main Sequence = `|A+I-1|`, not previously shown at all). `metricClass(value, green, yellow)`
   → `.metric-good`/`.metric-watch`/`.metric-critical`, new CSS custom properties (`--critical:
   #c0392b; --critical-bg: #fdecea;`) matching the existing `--active`/`--transitional` badge
   palette convention. Thresholds: Complexity/file <10/10-20/>20; Cognitive/file <10/10-25/>25;
   Code smells/1k LOC <5/5-15/>15; Distance from Main Sequence <0.3/0.3-0.6/>0.6 — same numbers
   originally drafted, verified against this repo's real current data (e.g. `marketplace-app`
   Complexity/file 46.8 → red, `query-lib`/`platform-commons` Distance from Main Sequence 0.75/0.68
   → red, most starters' Distance ~0.0-0.13 → green).
3. **Committed baseline carries real data, on explicit user instruction** — the user asked to run
   with `--with-sonar --with-archunit` and then, when told this makes
   `check-architecture-model-freshness.sh` report the committed copy as stale (that gate always
   regenerates with no flags for its comparison, per ADR-021's design), explicitly chose to keep
   the real-data version anyway and accept the gate showing red until a future no-flags
   regeneration. Not a silent contradiction of ADR-021's "default stays null" consequence — a
   deliberate, disclosed, one-off exception; ADR-021's default-off behavior itself is unchanged.
4. **Overview descriptions expanded** (immediate follow-up, same session) — the first pass's
   `CODE_QUALITY_GLOSSARY` entries were one-line definitions only; the user asked for more depth.
   Each entry now also explains *why* the field matters and how to read a high/low value (e.g.
   Instability's "0 = safe to keep unchanged, risky to modify" / "1 = safe to change freely, since
   nothing breaks downstream"; Distance from Main Sequence's Zone of Pain/Zone of Uselessness
   framing from Robert Martin's original stability/abstractness model) — still one paragraph per
   field, not a multi-section essay.

## ADR-025: Bounded Contexts domain discovery is self-describing (pom.xml property), not a hardcoded module-name pattern

**Status:** Accepted

**Context:** `marketplace-orchestrator` was added as a new module (a structural category — not a
`*-spring-boot-starter`, not `platform-commons`/`marketplace-app`) but never appeared on the
Bounded Contexts diagram. Root cause: `bounded_contexts_json()`'s domain discovery (ADR-019) only
recognized two hardcoded structural ids (`Shared` → `platform-commons`, `UI` → `marketplace-app`)
plus every module matching the `*-spring-boot-starter` name suffix — a third structural category
had no path into `BC_DOMAIN_ORDER` at all, silently. This is the same "hardcoded list goes stale"
bug class already hit twice elsewhere in this repo (root `Dockerfile`'s module `COPY`/`-pl` lists,
`scripts/sonar/`'s module lists) — a new module type requires remembering to also update this
generator, with nothing forcing that to happen.

**Decision:** Domain discovery reads a self-declared marker from each module's own `pom.xml`
instead of pattern-matching its artifact name:
```xml
<properties>
    <architecture.boundedContext>starter</architecture.boundedContext>
    <!-- or: shared / ui / orchestrator -->
</properties>
```
All 9 current bounded-context modules (`platform-commons`, `marketplace-app`,
`marketplace-orchestrator`, and the 6 `*-spring-boot-starter` modules) carry this property.
`query-lib`/`integration-tests` carry none — absence of the property means "not a bounded
context," a safe default rather than an easy-to-forget hardcoded exclusion list. The generator
loops `$MODULES` (already read from the root `pom.xml` reactor list) and reads each module's own
property via `sed`/`grep` instead of the old suffix match; `shared`/`ui`/`orchestrator` are handled
as named singletons (their JSON shape genuinely differs — no persistence, hand-picked "ports"
signal), `starter` kind reuses the existing id/label derivation from the artifact name.
`Orchestrator`'s own JSON branch: `entities`/`tables` empty (no persistence), `services` from real
`*Service.java` files (same generic signal domain modules already use), `ports` from which
`*Port` interfaces its source actually injects via `ComponentFactory<XPort>` (the real call-direction
signal — orchestrator calls Ports, it never `implements` one, so the existing starter branch's
`implements XPort` grep would find nothing).

New real relationship edges, not asserted: `Shared -> Orchestrator` (`decouples`, real pom.xml
dependency), `Orchestrator -> <starter>` (`calls`, real per-`*Port`-interface evidence: which
`ComponentFactory<XPort>` types orchestrator's source injects, matched against which starter
actually implements that interface — not an unconditional loop over every starter), `UI ->
Orchestrator` (`calls`, real evidence: count of `marketplace-app` classes importing
`org.ost.orchestrator.*`). The pre-existing `UI -> <starter>` edges (unconditional for every
starter, not gated on real evidence) are deliberately left unchanged here — fixing those requires
knowing which UI classes still hold a direct `*Port` after the true-BFF migration
(`improvement-147`) actually lands; tracked there, not fixed speculatively ahead of that work.

**Bug hit while implementing, fixed in the same change:** the new domain-discovery loop's
`bc_kind="$(sed ... | grep -o ... | sed ...)"` line was missing its closing double-quote (an
`unexpected EOF` / stray `(` syntax error surfaced 40+ lines later, at an unrelated array literal —
bash reports the *next* place parsing desynchronizes, not the actual unclosed quote, so trust
`bash -n` on the smallest reproducible snippet, not the reported line number, when debugging this
class of error). Separately, `grep -o ...` finding no match for `query-lib`/`integration-tests`
(no `architecture.boundedContext` property) exits 1; under this script's `set -o pipefail`, that
non-zero status propagates through the rest of the pipe even though the final `sed` stage exits 0,
which combined with `set -e` silently aborted the whole script with no error message — fixed with
`|| true` on every such bare `pipeline-into-assignment` where "no match" is an expected, valid
outcome, not a real failure.

**Consequences:** a future new module type (e.g. a second BFF-style module, or a reporting module)
becomes visible on this diagram from the same commit that adds its `pom.xml` property — no
separate edit to `generate-architecture-model.sh` required, closing the exact gap this ADR fixes
for `marketplace-orchestrator`. Regenerated via `bash scripts/architecture/generate-architecture-model.sh`;
`check-architecture-model-freshness.sh` confirmed green.

## ADR-028: Bounded Contexts splits into Context Map + Shared Dependencies; per-box item cap; tighter Mermaid spacing; audited-via evidence repaired

**Status:** Superseded by ADR-029 — the Mermaid-based Context Map/Shared Dependencies split was
replaced by a single Cytoscape-rendered diagram; the `audited via` evidence-repair fix (item 4)
remains current

**Context:** Three separate, real problems surfaced on the Bounded Contexts diagram in the same
sitting. (1) User reported the `showDecouplesEdges` checkbox (see this file's ADR-029) itself was unclear — mixing
"here's a toggle for a secondary fact" into the primary call-graph view was confusing regardless of
wording. (2) The diagram was "розтягнута" (stretched) well past one screen — measured cause:
`Orchestrator`'s domain box alone lists 29 items (18 services + 11 ports), dwarfing every other
domain (max 11) and dominating the page height in Mermaid's `graph TB` vertical layout; Mermaid's
default `nodeSpacing`/`rankSpacing` (50/50) compounded this further. (3) `platform-commons/
DECISIONS.md` ADR-029's third refinement (`AuditActivityFieldsHook` removed entirely) had already
flagged, as a known but unfixed consequence, that the `"$dom" -> "Audit" "audited via"` relationship
edges lost their only evidence source and vanished from the diagram — a real signal loss, not
cosmetic, confirmed here by inspecting the regenerated model directly (19 relationships, zero
`audited via` entries).

**Decision:**
1. **Checkbox removed, split into two diagrams** — same mechanism `02-spi-map`'s subsystem-tab
   split already uses (multiple entries in one `diagramGroups[].diagrams` array): "Context Map"
   (default; real business relationships only, `Shared` domain excluded entirely since it never
   participates in a real call, only `decouples`) and "Shared Dependencies" (the `decouples` edges
   only, simple domain-name boxes with no entity/service/table/port lists, since this diagram
   exists purely to show one compile-time fact). `buildBoundedContextsMermaidSource()` split into
   `buildContextMapMermaidSource()`/`buildSharedDependenciesMermaidSource()`;
   `renderBoundedContextsExtrasHtml(view)` filters Domain Contents/Relationships/Legend to match
   whichever tab is open, mirroring SPI Map's own per-subsystem filtering pattern.
2. **Per-box item cap** — `BC_MAX_ITEMS_PER_BOX = 8`; a domain with more items shows the first 8
   plus a `"+N more -- see Domain Contents below"` pseudo-node instead of every item inline. Full
   list stays one click away in the existing "Domain Contents" section.
3. **Tighter Mermaid spacing** — `flowchart.nodeSpacing`/`rankSpacing`/`padding` set to 12/25/6
   (from Mermaid's defaults of roughly 50/50/8) in the one global `mermaid.initialize()` call;
   Bounded Contexts is the sole live `graph`-type flowchart consumer, so this doesn't affect the ERD
   (its own `er` config) or SPI Map (Cytoscape-rendered, not Mermaid).
4. **`audited via` evidence repaired** — new source is `AuditTimelineRowRenderer.
   LABELED_ENTITY_TYPES`, the `Set<EntityType>` constant introduced when `AuditActivityFieldsHook`
   was removed (`platform-commons/DECISIONS.md` ADR-029's third refinement) — parsed via
   `sed -n '/LABELED_ENTITY_TYPES/,/;/p' | grep -oP 'EntityType\.\K\w+'` against that one file,
   replacing the old multi-file `implements AuditActivityFieldsHook` scan. `can have`
   (`AuditActivityEnrichHook`) evidence-gathering is unaffected — that interface still has a real
   implementation with real per-call logic.

**Consequences:** Regenerated and confirmed: `audited via` edges back (`Advertisement`/`Taxon` ->
`Audit`, `User` -> `Audit` with both `USER`/`USER_SETTINGS` folded into one edge's evidence, same
`add_rel()` merge behavior as before). Two diagram tabs confirmed present in the regenerated model.
`check-architecture-model-freshness.sh` green.

---

## ADR-029: Bounded Contexts renders via Cytoscape as 4 category-split diagrams — flat domain nodes, per-edge evidence traced to its real caller

**Status:** Accepted

**Context:** Restoring interactivity for the Bounded Contexts diagram (after ADR-016 dropped it —
see that entry for the original dagre compound-cycle diagnosis) needed to both match the polish of
this tool's other Cytoscape diagrams (native pan/zoom/drag, not Mermaid's hand-rolled
`enableDragToPan()` fallback) and avoid ADR-016's original failure mode. Checked directly rather
than assuming either way: ADR-016's collapse was caused specifically by nesting each domain's
entities/services/tables/ports as *compound-child* nodes, which created a cycle spanning compound
boundaries that dagre's compound-aware ranking cannot resolve. The current relationship set has a
2-node cycle (`Orchestrator <-> Audit`), but between two *flat, top-level, non-nested* domains — the
ordinary case dagre handles without difficulty, not the parent-child-crossing-boundary shape
ADR-016 diagnosed.

Separately, real per-edge evidence needed repair: an earlier per-folder grep labeled every Hook
implementation file found in `marketplace-app/spi`/`marketplace-orchestrator/spi` as caused by
"Audit" regardless of who actually calls it — wrong for `UiLabelHook`/`SessionActorHook` (real
caller: `marketplace-orchestrator`'s own Hook classes) and incomplete for `CurrentActorHook` (two
real callers: `audit-spring-boot-starter` and `attachment-spring-boot-starter`, not just audit).

**Decision:**
1. **Cytoscape rendering, flat nodes.** Bounded Contexts renders via `renderContextMapGraph()`,
   reusing `renderModuleDependencyGraph()`'s shape: one flat node per domain (no compound nesting,
   no item children on the canvas), colored via the same `domainColor()`/`moduleNodes` lookup
   Module Dependencies uses, native pan/zoom/click/`tap`-to-navigate — the identical interaction
   model as every other Cytoscape diagram in this tool. Entities/services/tables/ports live only in
   the "Domain Contents" table below the diagram and each domain's own module page, never on the
   canvas. `buildContextMapMermaidSource()` is kept, narrowed to Markdown-export use only (a static
   document has none of the live-interaction concerns above), emitting the same flat-node shape as
   the live graph.
2. **Per-edge evidence traced to its real caller, not its folder.** For each `*Hook.java` interface
   (scanned across `platform-commons/*/spi` and `marketplace-orchestrator/*/spi`), the generator
   finds its real implementor (decides `UI` vs `Orchestrator`) and every real caller across every
   starter plus `marketplace-orchestrator` itself, adding one edge per distinct
   (caller-domain, implementor-domain) pair — skipping self-edges where a domain's own forwarder
   calls its own SPI. Real edges today: `Audit -> UI` carries only `AuditActivityEnrichHook`;
   `Audit -> Orchestrator` carries `AuditDomainHook` + `CurrentActorHook`; `Attachment ->
   Orchestrator` carries `CurrentActorHook`'s second real caller; `Orchestrator -> UI` carries
   `UiLabelHook`/`SessionActorHook`. `UserSettingsChangedHook` is unaccounted for either way — its
   real implementor, `SettingsPaginationService`, lives outside the `spi/` package this search
   covers, a known gap.
3. **Split into 4 diagram tabs by relationship category**, since one combined canvas mixed 4
   genuinely different kinds of fact into one arrow+label visual: "Service Calls (BFF)" (real
   forward-direction Port calls — `Orchestrator -> {6 domains}`, `UI -> Orchestrator`, plus one
   documented `UI -> User` exception), "Hook Callbacks" (reverse-direction dependency-inversion
   edges — `Audit -> UI`, `Audit -> Orchestrator`, `Attachment -> Orchestrator`, `Orchestrator ->
   UI` — a different mechanism than orchestration, not a BFF violation), "Cross-Starter Exceptions"
   (`ProviderProfile -> Taxon`, one real documented starter-to-starter exception bypassing the
   orchestrator, tech debt per `provider-profile-spring-boot-starter/CLAUDE.md`), and "Derived
   Facts" (non-call classification data — `audited via`, `can have`). `BC_CATEGORY_ORDER`/
   `BC_CATEGORY_LABEL`/`BC_CATEGORY_DESC`/`BC_LABEL_CATEGORY` (bash, mirroring SPI Map's
   `SPI_SUBSYSTEM_*` per-subsystem split) drive `buildContextMapGraph(category)`/
   `renderContextMapGraph(category)`/`renderBoundedContextsExtrasHtml(category)`, all filtering to
   the active tab's edges and the domains actually touched by them. Domain Contents also filters
   per active category (computed from `involvedIds`, the same domain set the canvas itself draws);
   Overview/Legend stay unfiltered across all 4 tabs. `exportBoundedContextsMarkdown()` stays one
   unfiltered document (all relationships) with a `Category` column.
4. **Per-edge payload text, not one fixed string per label.** `BC_HOOK_PAYLOAD` (keyed by real
   interface name — `AuditDomainHook`, `AuditActivityEnrichHook`, `CurrentActorHook`, `UiLabelHook`,
   `SessionActorHook` — each mapped to its own real method signature) feeds an optional
   `payload_override` argument into `add_rel()`, since the 4 Hook Callback edges carry genuinely
   different real types and one shared label-level text was wrong for 3 of them. Every other
   relationship category still uses one fixed `BC_LABEL_PAYLOAD[label]` text, accurate at that
   granularity.
5. **Table/diagram UX:** Relationships table drops the `Confidence` column (every row was
   `"extracted"` — a constant column conveys nothing; stated once in the section header instead).
   `Label`/column headers carry a `title` tooltip explaining what each relationship kind means.
   `Evidence` text runs through `linkifyEvidence()`, wrapping just the real
   `<module>/src/main/java/.../X.java[:line]` substring in a working link. Diagram edges are
   clickable (`diagramCy.on("tap", "edge", ...)`) — `bcRelRowId(from, to, label)` produces one
   canonical id shared by both the table row and the edge, so a click scrolls to and flashes the
   matching row.
6. **The one-off "Shared Dependencies" second diagram (dashed edges from `platform-commons` to
   every domain, restating one compile-time fact) is not kept** — the fact is stated once in prose
   in the Overview section instead of drawn 7-8 times as identical parallel edges.

**Consequences:** `GRAPH_TYPE_KEYS` includes `bounded-contexts`, so it shows the "draggable" badge
like every other Cytoscape diagram. No live Mermaid `graph`-type flowchart consumer remains
anywhere in the tool (DB ERD uses `erDiagram`, the generic fallback uses `sequenceDiagram`) — the
`mermaid.initialize()` `flowchart` config tuning and its CSS overrides are dead and were removed.
SPI Map gained the symmetric caller-detection pass in the same line of work: every interface now
shows not just `interface -> implementation` but also `caller -> interface "calls"`, via the same
per-interface grep-for-real-callers technique. `platform-commons/CLAUDE.md`'s SPI naming table
gained `marketplace-orchestrator` as a legitimate `*Hook` caller (previously only "starter") — see
`platform-commons/DECISIONS.md` ADR-029.

**Trigger to revisit:** if the `Orchestrator <-> Audit` cycle ever grows into a larger
compound-boundary-crossing cycle (e.g. if item nodes are ever reintroduced as canvas children),
re-verify against ADR-016's original diagnosis before assuming dagre still handles it — the
reasoning above is specific to today's flat, no-nested-children shape, not a blanket "dagre handles
cycles fine now" conclusion.

**Found but not yet fixed:** `spi_call_flow_examples_json()` (hand-typed narrative call traces
carried over from a retired markdown file) has drifted stale in all 3 entries after the Hook-
relocation/orchestrator-extraction work — e.g. "Enrich Audit Activity" still cites
`AuditActivityFieldsHook.fields()`/`AdvertisementActivityFieldsHookImpl` (both deleted). Rewriting
all 3 accurately needs its own scoped pass (verify each full call chain from scratch).

---

## ADR-032: Every `SCRIPT_GROUP` script self-documents via a fixed 7-field header, plus a rendered `README.md` per directory — no hand-written prose describing what a script does anywhere in the tool

**Status:** Accepted
**Verified:** 2026-08-21

**Context:** Nothing in the tool described what a script in `scripts/` actually does, what it
needs, or what it produces except free-form comments a human might or might not keep current —
exactly the class of drifting documentation the rest of the tool avoids everywhere else
(Javadoc-sourced SPI descriptions, `remarks=`-sourced column docs, live ADR content). Checked
against real bash convention before finalizing (2026-08-14 web search): the
[Google Shell Style Guide](https://google.github.io/styleguide/shellguide.html) requires a
top-of-file header and, for functions, a structured `Description`/`Globals`/`Arguments`/`Outputs`/
`Returns` block — the basis for the field set below. Full three-layer design (header / README /
architecture-map) is split across two dedicated skills: `.claude/skills/infra-doc-standards/SKILL.md`
(file-level and per-function header conventions) and `.claude/skills/infra-readme-standards/SKILL.md`
(script-group `README.md`/Flow-diagram conventions) — split 2026-08-21 once the combined file grew
to 714 lines/6-8x its sibling skills, and confirmed to auto-trigger on every script/tooling file
edit regardless of whether the edit only touches a header. Both are distinct from `doc-standards`,
which covers Java-app documentation, not infra/script files — this ADR covers only the
generator-side mechanism.

**Decision:**
1. **Per-script header — 7 fixed-prefix comment lines** (`#` for `.sh`, `//` for `.js`, `REM` for
   `.bat`), directly after the shebang/`@echo off`, before any other free-form rationale prose:
   `Description` / `Usage` / `Uses` / `Env` / `Input` / `Outputs` / `Returns` — omitting a field
   (e.g. `Returns` for a `docker-compose.yml`, or `Uses`/`Input`/`Outputs`/`Returns` for a
   `.properties` file) is valid; the parser renders only non-empty fields.
2. **`script_headers_json(dir, files)`** (bash, Python3 for the parsing) takes the same file list
   already computed for a `SCRIPT_GROUP` node — never re-globs separately, so "files shown" and
   "headers parsed" can't drift from each other — reads each file's header block, and embeds a
   `headers` array directly on that node. The file glob itself also matches `*.bat`/`*.yml`/
   `*.yaml`/`*.properties`/`Dockerfile`, not just `*.sh`/`*.js` — mechanical, self-extends to any
   future file type without a generator edit.
3. **`readme_json_for()`** reads `<dir>/README.md` (if present) into a `readme` field on the same
   node — always embedded (unlike `decisions_json_for()`, gated behind `--with-adr-details`, since
   a README is orders of magnitude smaller than a full ADR history). `renderScriptGroupSection()`
   renders `headers` as a per-file "Script headers" block (`Description`/`Usage`/... as labeled
   lines, one block per file, only non-empty fields shown — a wide table reads poorly once a field
   wraps across multiple lines) followed by the `readme` content via `mdBlockToHtml()`, sourced-
   linked back to the real files — header = self-contained per-file facts, README = flow between
   files. The top-of-card file-chip row only lists files *not* already covered by a parsed header,
   so a fully-documented directory never shows the same filename twice.
4. **`mdBlockToHtml()`** (shared with the ADR-popup renderer) supports `#`-`######` headings
   (rendered 3 levels down, `##` → `<h5>`, capped at `<h6>`, so an embedded README's heading never
   collides with the surrounding screen chrome), fenced ` ``` ` code blocks, and a
   `` ```mermaid `` fence renders as a live diagram (`<pre class="mermaid">`, `mermaid.run()`)
   instead of inert source text.
5. **`SCRIPT_GROUP_FILE_ORDER`** lets a directory declare its own file display order (real entry
   points first, e.g. `run.sh`/`run.bat`, before supporting config files) — the same "start from
   what you'd actually run" order its own `README.md` uses.

**Non-obvious gotchas, worth keeping:**
- A script's header must sit **immediately** after the shebang/`@echo off` — nothing between,
  not even `set -e` — since the parser stops reading at the first non-comment line.
- The header block is **one contiguous comment run starting right after the shebang**, not "any
  field-name-prefixed line anywhere in the read window" — a file with an unrelated second `Usage:`-
  labeled block further down (a real pre-existing pattern in at least one script) must not have
  that second block absorbed into the structured header.
- A Python (or any) script embedded in a bash `"..."`-quoted string must never contain an
  unescaped `"`, including inside its own comments — bash's quote parser doesn't know or care that
  the character sits inside what Python considers a comment; this silently corrupts the string
  bash actually passes through, with no traceback since the mangled result may still parse as
  syntactically different, but invalid, Python.

**Consequences:** A new script dropped into any `SCRIPT_GROUP` directory with this header
convention appears with a fully rendered "Script headers" block automatically — no generator edit
needed. `scripts/sonar/`'s 4 files (`run.sh`/`run.bat`/`docker-compose.sonar.yml`/
`sonar-project.properties`) and `scripts/sonar/README.md` are the first full rollout under this
convention; rolling it onto every other script in the repo is a separate, not-yet-done pass.

---

## Open goals

~~Mechanize `bounded-contexts.md` the same "scatter into real source" way as ADR-017~~ — done, see
ADR-019 (domains/relationships derived live from real code) and ADR-025 (domain *discovery* itself
made self-describing via each module's own `pom.xml`, not a second hardcoded list).

~~AI-layer L3 (Rule/Intent) artifact~~ — done, see `md-to-decisions-json.js`'s `--extract` mode
(improvement-145): reads `.claude/nav/adr-index.md` (via `.claude/nav/context-loading.md`'s guidance) to
find the relevant id(s), then extracts just those from the real `DECISIONS.md` on demand, exactly
the source and shape this goal specified — not a filtered read of `architecture-model.json`.
~~Full ADR-embedding rollout to all modules with their own `DECISIONS.md`~~ — done, see ADR-007.
