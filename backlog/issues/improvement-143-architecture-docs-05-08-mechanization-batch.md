# improvement-143: Architecture Control Plane — `05`-`08` mechanization + full deletion batch (SonarQube, ArchUnit metrics/rules, `06`/`07` live pieces, `05`-`08` all deleted, folder consolidation)

**Type:** improvement — extracted from `improvement-138` into its own self-contained batch, so it
can be planned/executed independently (e.g. via `/autopilot`) without `138` growing further.
**Module:** `scripts/ai/generate-architecture-model.sh`, `docs/architecture-map.html`,
`marketplace-app/src/test/java/org/ost/marketplace/architecture/`, `docs/architecture/05-08-*.md`
**Priority:** 🔴 highest (set explicitly by user request — first to execute in the backlog)
**When:** independent, no blockers — the seven pieces below have internal ordering (see each
section), but nothing outside this issue blocks starting

## Problem

`improvement-138`'s Architecture Control Plane work (01/02/04/Bounded Contexts already live)
accumulated seven verified, planned-but-not-implemented pieces covering
`docs/architecture/05-08-*.md` — extracted here as one batch so `138` stops growing and this work
can be picked up (including via `/autopilot`) as a single, bounded unit. **Corrected decision
(2026-08-06): all four files (`05`, `06`, `07`, `08`) end up fully deleted** — `05`/`06`/`07` after
their real mechanizable pieces ship first (their live equivalent), `08` with no live equivalent at
all (pure editorial content, deleted anyway per explicit user decision, content captured in
`improvement-142` first same as the other three). Every piece below was checked against real
running systems or real file content before being written down — see each section's own
verification note.

## Planned pieces (not yet implemented, extracted 2026-08-05)

### 1. SonarQube integration on the Module page

Verified live before planning: SonarQube is running (`localhost:9099`, v26.5.0), the `advertisement`
project is already scanned, and `/api/measures/component?metricKeys=ncloc,complexity,code_smells,
duplicated_lines_density,cognitive_complexity&component=advertisement` returns real numbers
(`complexity=2372, code_smells=14, duplicated_lines_density=1.6, cognitive_complexity=792,
ncloc=15431`). Per-module breakdown is also queryable (component key = `advertisement:<module>/src/
main/java`, confirmed via `/api/components/tree`). The new SonarQube "Architecture" beta feature
(coupling/cohesion visual map) has **no public API** — confirmed directly, metric key `coupling`
does not exist — so it cannot feed this tool; only the classic, stable `/api/measures` metrics can.

**Plan:**
1. `scripts/ai/generate-architecture-model.sh` — new `ensure_sonar_fresh()`: compares SonarQube's
   last analysis date (`/api/project_analyses/search`) against the newest `.java` file's mtime
   (same staleness-check pattern `integration-tests/run.sh` already uses for starter-JAR
   reinstall-detection). If stale or the server isn't reachable, calls the existing
   `bash scripts/sonar.sh --no-gate` (reused, not reimplemented — it already handles starting the
   server, token validation, compiling, and scanning) before generating the model. `--no-gate` so a
   failing quality gate doesn't block architecture-map generation.
2. New `sonar_metrics_json()` — `curl -u "$SONAR_TOKEN:"` (token read from
   `scripts/sonar/sonar-project.properties`, not duplicated) against `/api/measures/component` once
   per module + once for the whole project. Degrades gracefully (empty/omitted, not a hard failure)
   if the server is unreachable even after `ensure_sonar_fresh()`'s attempt.
3. New `MODEL.sonarMetrics` key (added next to `boundedContexts`/`dbErd` in the same JSON-assembly
   block, ~line 869) — `{analysisDate, project: {...}, modules: {<module>: {ncloc, complexity,
   cognitiveComplexity, codeSmells, duplicatedLinesDensity, javaFileCount}}}`. `javaFileCount` is
   not a Sonar API field — it's `find $mod/src/main/java -name '*.java' | wc -l` (see piece 4's
   `07` item 1 below, replacing `07-risk-report.md`'s "Module Size Analysis" table), added into
   this same object so the Module page's "Code Metrics" section shows both in one place instead of
   two separate lookups.
4. Client JS — `renderModule()` (line 1551): new `if (n.sonarMetrics) { ... }` section inserted
   between the existing "Contracts" section (ends line 1576) and "Depends on" (starts line 1578) —
   a new `<section class="block"><h3>Code Metrics (SonarQube)</h3>...</section>`, nothing existing
   removed or replaced.

**Known trade-off, accepted by the user:** when Sonar data is stale, generation stops being
"always ~50s" — a full rescan (mvn compile of 9 modules + sonar-scanner) takes several minutes.
This is a real behavior change to the generator's runtime characteristics, not a cosmetic addition.

**Explicitly does not solve:** `05-sequence-diagrams.md` (see piece 5 below — deleted, not fixed);
SonarQube's own new coupling/cohesion visualization has no public API (metric key `coupling` does
not exist), so Sonar itself cannot feed `08-scorecard.md`'s Coupling/Cohesion sections — **but see
piece 2 below, which can**; `08`'s SPI Design Quality/Database Design/Testability sections stay
editorial judgment regardless (not a code metric at all, ArchUnit included).

### 2. ArchUnit coupling/instability/abstractness metrics

Verified: `com.tngtech.archunit:archunit-junit5` is already a `marketplace-app` dependency (used by
the existing `ArchitectureRulesTest.java`, 8 rules, runs in `bash scripts/unit-tests.sh`). ArchUnit's
core artifact (no extra dependency needed) includes `ArchitectureMetrics.componentDependencyMetrics()`
— computes, from the real class-level dependency graph (byte-code verified, not grep on import
text): **Efferent Coupling (Ce)** (outgoing deps), **Afferent Coupling (Ca)** (incoming deps),
**Instability (I)** = Ce/(Ca+Ce), and **Abstractness (A)** (ratio of abstract classes) per package/
component — the classic Robert Martin component-metrics set. Also available: Lakos cumulative
dependency metrics. This runs entirely inside the existing test suite, no external server
(contrast SonarQube, which needs `localhost:9099` up).

**Plan:**
1. New class `marketplace-app/src/test/java/org/ost/marketplace/architecture/ArchitectureMetricsExport.java`
   (same package as the existing `ArchitectureRulesTest`) — a `@Test` method that builds
   `MetricsComponents.fromPackages(...)` for each of the 9 real modules' root packages
   (`org.ost.audit`, `org.ost.attachment`, `org.ost.user`, `org.ost.advertisement`, `org.ost.taxon`,
   `org.ost.provider`, `org.ost.platform`, `org.ost.marketplace`, `org.ost.query`), runs
   `ArchitectureMetrics.componentDependencyMetrics(components)`, and writes the result as JSON to a
   fixed path (`marketplace-app/target/architecture-metrics.json`) — runs automatically every time
   `bash scripts/unit-tests.sh` runs, no separate invocation needed.
2. `generate-architecture-model.sh` — new function reads that JSON file if present (graceful
   `null`/omitted if the file doesn't exist yet, e.g. before the first `unit-tests.sh` run — same
   "don't hard-fail on missing optional data" approach as piece 1 above), adds it to `MODEL` next
   to `sonarMetrics`.
3. UI: same "Code Metrics" section on the Module page (`renderModule()`, between "Contracts" and
   "Depends on" — the same insertion point already planned for Sonar's numbers) gets 4 more rows:
   Efferent Coupling, Afferent Coupling, Instability, Abstractness, per module.

**Known trade-off:** this data is only as fresh as the last `bash scripts/unit-tests.sh` run — no
auto-trigger like piece 1's `ensure_sonar_fresh()` (running the full unit-test suite just to
refresh one JSON file is a bigger cost than triggering it makes sense for inside
`generate-architecture-model.sh`); staleness is surfaced (e.g. file mtime shown), not silently
hidden, but not auto-fixed either.

### 3. New `@ArchTest` rule candidates

`marketplace-app/src/test/java/org/ost/marketplace/architecture/ArchitectureRulesTest.java`
already has 8 rules. Compared against `.claude/rules.md`/module `CLAUDE.md` text and
`06-coupling-analysis.md`'s manual grep checks, 6 real candidates found with no existing rule:

1. **Starter-to-starter imports** — `.claude/rules.md` "Module Import Rules": *"Starters must NOT
   import from marketplace or from each other."* Currently only checked manually via grep in `06`.
   Rule: no class in `org.ost.{audit,attachment,user,advertisement,taxon,provider}..` depends on
   classes in another of those packages (only `org.ost.platform..` allowed).
2. **Marketplace not importing starter internals** — same rules.md section: *"Marketplace may
   import from starters only via platform-commons contracts... never via internal impl classes
   (util, service, repository)."* Existing rule `ui_must_not_call_repositories_directly` only
   covers `org.ost.marketplace.ui..` + `..repository..`. Wider rule: all of
   `org.ost.marketplace..` must not depend on `org.ost.{audit,attachment,user,advertisement,taxon,
   provider}.{util,service,repository}..`.
3. **`*Util` classes are non-instantiable** — `marketplace-app/CLAUDE.md`: *"`*Util` — static-only
   utility class (`@NoArgsConstructor(access = PRIVATE)`)."* Rule: classes with simple name ending
   `Util` have a private constructor.
4. **`*Config` classes are `@Configuration`** — same file, "Class suffixes". Rule: classes ending
   `Config` are annotated `@Configuration`.
5. **`MessageSource` only used in `I18nServiceImpl`** — `marketplace-app/CLAUDE.md`: *"Never use
   raw `MessageSource` directly in UI components — use `I18nService.get(I18nKey)`."* Rule: only
   `I18nServiceImpl` depends on `org.springframework.context.MessageSource`.
6. **Package-level cycle freedom** — `SlicesRuleDefinition.slices().matching(...).should()
   .beFreeOfCycles()`. Stronger than `06`'s current "No Cyclic Dependencies Detected" (which only
   checks module-level DAG shape via `pom.xml`) — this catches cycles between packages *within* a
   single module too.

Highest value / lowest risk: #1 (closes a real gap `06` currently only checks by hand) and #6
(strictly stronger than the existing module-level check). #2 is broader and needs careful exception
wording to avoid false positives. #3-5 are small and low-risk. None implemented yet — no rule
priority/order decided.

### 4. Live coupling checks on the Module Dependencies page + `07`'s 4 mechanizable pieces

Reviewed `docs/architecture/06-coupling-analysis.md` in full. `06`'s "Architecture Violations —
Current State" and "Potential Layer Violations" sections are literally written as "run this grep
command → here's the result" (the grep commands are already inline in the markdown) — directly
re-runnable.

**Plan:**
1. New bash function `coupling_checks_json()` in `generate-architecture-model.sh` — re-runs the
   real grep checks already described in `06` (no Vaadin imports in starters, no direct
   starter-to-starter internal imports, no UI→Repository direct imports), each producing a
   PASS/FAIL + the real evidence file/grep output, not hand-typed "✓ PASS" text.
2. New `MODEL.couplingChecks` key, added in the same JSON-assembly block as `boundedContexts`.
3. Client JS — `renderModuleDependencyExtrasHtml()` (line 1443): new section "Architecture Checks"
   inserted between the existing "Dependency Table" section (ends line 1471) and "Key Observations"
   (starts line 1472). Rendered under System → "📐 Diagrams" card → Module Dependencies view,
   below the existing Overview/Legend/Dependency Table sections.

**Corrected decision (2026-08-06): `06-coupling-analysis.md` is deleted, not partially kept.**
The Sonar-sourced "Module Size & Complexity" table step is dropped (that data already lives in the
per-module "Code Metrics" section from piece 1, no need for a second copy on the Module
Dependencies page). Once the live "Architecture Checks" section above ships, `06`'s three
narrative sections ("Actor-Reference Coupling", "Singleton State Isolation", "Optional Dependency
Guards" — the only content with no live equivalent) are captured in full in `improvement-142` for
future analysis, same "capture before delete" discipline already used for `bounded-contexts.md`
and `05-sequence-diagrams.md`, then `git rm docs/architecture/06-coupling-analysis.md`.

**`07-risk-report.md` — corrected plan, after a row-by-row check found an earlier draft's
"confirmed duplicate" claim wrong** (`07`'s Constructor Injection table has 4 rows, `06`'s has 3,
missing `DefaultTaxonPort`; `07`'s God-Package table has 4 rows, `06`'s has 3, missing
`org.ost.platform`; `07` splits Module Size into two separate sections, one of which — top-10
largest files by line count — `06` has no equivalent of at all). Four pieces are genuinely
mechanizable, each verified against the real current file content before planning:

1. **"Module Size Analysis"** (`07` lines 3-21, e.g. `| marketplace-app | 174 | MEDIUM — UI
   complexity, but expected |`). UI: not a new page — a new row (`Java Files: 174`) inside the
   already-planned "Code Metrics (SonarQube)" section on each module's own page (System → Diagrams
   → click a module). Mechanism: `find $mod/src/main/java -name '*.java' | wc -l`, added into
   `sonar_metrics_json()`.
2. **"Largest Java Files"** (`07` lines 24-44, a static top-10 snapshot dated 2026-08-04, e.g.
   `I18nKey.java | 438 | marketplace-app | MEDIUM | ...`). UI: new section titled "Largest Java
   Files" on the Module Dependencies view (System → Diagrams → Module Dependencies, below the
   diagram, alongside "Architecture Checks"), a table with columns File | Lines | Module.
   Mechanism: new `largest_java_files_json()`, the same `find ... -exec wc -l {} \; | sort -rn |
   head` command already written as text in the file, re-run every generation.
3. **"Constructor Injection Complexity"** (`07` lines 47-62, 4 hand-picked classes with a judgment
   comment per row). UI: same Module Dependencies view, new section "Constructor Injection", table
   Class | Module | Field count — every class with 4+ fields, not just the 4 currently picked, no
   judgment column. Mechanism: grep for `@RequiredArgsConstructor`, count `private final` fields,
   threshold >3.
4. **"Package God-Package Analysis"** (`07` lines 65-77, 4 hand-picked packages). UI: same Module
   Dependencies view, new section "Largest Packages", table Package | File count — every package
   above a threshold. Mechanism: `find ... -name '*.java'` grouped by directory, threshold-based.

**Corrected decision (2026-08-06): `07-risk-report.md` is deleted in full, not kept in reduced
form.** Same treatment as `05`/`06`. Once the 4 mechanizable pieces above ship (their data now
lives on the Module page / Module Dependencies page, not in this file), everything else in `07` —
"Database Schema Risks", "Dependency Chain Risks", "Code Complexity Hot Spots", "Security Risks",
"Performance Risks", "Testing Risks", "Architectural Debt", "Summary" — is captured in full in
`improvement-142` for future analysis (same "capture before delete" discipline as
`bounded-contexts.md`/`05-sequence-diagrams.md`/`06-coupling-analysis.md`), then
`git rm docs/architecture/07-risk-report.md`. "Architectural Debt" specifically also gets its 3
TODO items moved into `backlog/BACKLOG.md` proper (this project's actual convention for tracked
work items), not just archived in `142` — a small separate action alongside the deletion.

### 5. Delete `05-sequence-diagrams.md` and its wiring

Investigated whether any tool (JetBrains SequenceDiagram plugin, `plantuml-generator-maven-plugin`,
ArchUnit, SonarQube, OpenTelemetry+Tracetest) could make `05` live — see `improvement-142`'s
findings. None fit without a much larger OpenTelemetry-based effort. Decision: rather than keep
`05` listed in `architecture-map.html`'s Diagrams screen as if it were on the same footing as the
live diagrams (01/02/04/Bounded Contexts), remove it from the tool entirely — the file's full
content is preserved in `improvement-142` first, so nothing is lost, only git history would
otherwise hold it.

**Plan:**
1. Full content of `docs/architecture/05-sequence-diagrams.md` captured in `improvement-142`
   (done) — a future OpenTelemetry-based reimplementation starts from that, not from git
   archaeology.
2. `git rm docs/architecture/05-sequence-diagrams.md`.
3. `scripts/ai/generate-architecture-model.sh`:
   - Remove `[05-sequence-diagrams]="Sequence Diagrams"` from the `DIAGRAM_FILE_LABEL` array
     (line 453) — and the now-also-dead `[02-spi-map]="SPI Map"` entry in the same array (line
     452; `02` already moved to the directly-synthesized diagram-groups list per ADR-005/014, this
     array entry has had no reader since).
   - Remove the `for stem in 05-sequence-diagrams; do ... done` loop (lines 457-465) that extracted
     its Mermaid blocks into `diagram_groups_json`.
   - `renderDiagrams()` (client JS): update the Diagrams screen's description text (line 2256),
     which currently says "...the rest reused verbatim from docs/architecture/bounded-contexts.md
     and 05-sequence-diagrams.md, which stay the authoring source for those" — both files are gone
     by this point, the sentence needs rewriting to match what's actually still true.
   - Drop the "05-sequence-diagrams" example from the comment at line 2312 (prose reference only,
     no logic depends on it).
4. Regenerate + run all 4 CI freshness gates; confirm the Diagrams screen no longer lists a
   "Sequence Diagrams" card and nothing else broke (the generic Mermaid-rendering `else` branch in
   `renderDiagrams()` has no other diagram group routed through it once `05` is gone, so nothing
   else exercises that code path — confirm it doesn't dangle).

### 6. Move `architecture-map.html`/`architecture-model.json` into `docs/architecture/`

Once pieces 1-5 above land (`05` deleted, `06`/`07` partially mechanized/deduped, `08` untouched),
move `docs/architecture-map.html` and `docs/architecture-model.json` from `docs/` into
`docs/architecture/`, so every architecture artifact — the interactive tool and the remaining
hand-maintained markdown — lives under one directory instead of the tool sitting at the `docs/`
root as a sibling of the `architecture/` subfolder.

**What this touches (not yet verified line-by-line, flagged so it isn't underestimated later):**
- `scripts/ai/generate-architecture-model.sh`'s `OUTPUT`/`HTML_OUTPUT` path constants (near the top
  of the file).
- Every relative `../` link inside the generated HTML/JS (`sourceLink()` and similar helpers) —
  currently correct on the assumption the file lives at `docs/`, one level above the repo's module
  roots; after the move it becomes `../../`.
- The 4 CI freshness gates (`scripts/ai/check-adr-index-freshness.sh`,
  `check-architecture-model-freshness.sh`, `check-flows-completeness.sh`,
  `check-hardcoded-counts.sh`) — likely hardcode the current path.
- Every doc referencing these two files by path: root `CLAUDE.md`, various module `DECISIONS.md`
  entries, `docs/architecture/README.md`.

Not started — a distinct, non-trivial move with a wide blast radius, deliberately sequenced last.

### 7. Delete `08-scorecard.md`

Confirmed by user decision (2026-08-06): deleted too, even though **nothing** in it is
mechanizable — no tool gives real numbers for its 7 dimensions' 1-10 editorial scores (checked
directly: SonarQube's coupling/cohesion visualization has no public API). This is a pure
"capture the reasoning, then delete" action, not a mechanization — full content (all 7 dimensions'
evidence/scores/improvement suggestions, the Overall Assessment/Strengths/Recommendations/
Conclusion sections) is captured in full in `improvement-142` first.

**Plan:**
1. Content already captured in `improvement-142` (done).
2. `git rm docs/architecture/08-scorecard.md`.
3. No `generate-architecture-model.sh`/`architecture-map.html` wiring exists for this file today
   (unlike `05`, it was never extracted into the Diagrams list) — nothing to remove from the tool
   itself, only the file deletion and any stray references to `08-scorecard.md` in other docs
   (`docs/architecture/README.md` at minimum — not yet checked for others).

## Suggested execution order

Pieces 3 (ArchUnit rule candidates) and 6 (folder move) have open decisions/verification not yet
resolved (rule priority not chosen for 3; blast radius not verified line-by-line for 6) — not
autopilot-ready as-is. Pieces 1, 2, 4, 5, 7 are each concretely specified (exact files, functions,
line numbers, insertion points) and could reasonably be run independently, in any order, with 6
deliberately last (depends on all others landing first).

## Related

- `improvement-138` — the original Architecture Control Plane plan this batch was extracted from.
- `improvement-142` — the running tracker for what stays hand-maintained (`05`'s full content,
  why `06`/`07`/`08`'s narrative sections don't mechanize) and other `architecture-map.html`
  follow-ups; this batch and `142` reference each other for the pieces each one owns.
- `scripts/ai/DECISIONS.md` ADR-017/ADR-018/ADR-019 — the live-migration precedent (01/02/04/Bounded
  Contexts) this batch continues.
