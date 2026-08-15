# improvement-152: `scripts/build.sh` (redundant recompile fix) + ArchUnit Track B unblock investigation + SPI Interface Details table redesign + Tooling & Pipelines regroup + repo-wide script documentation convention

**Type:** improvement + investigation + design idea — five independent topics filed together
(three split out of `improvement-151` once that issue's own real (verified) work was done and
ready to close; Parts D and E added later per explicit user request).
**Module:** `.claude/skills/doc-standards/SKILL.md`, `.claude/rules.md`, every repo script
(`scripts/**/*.sh`, `*.bat`, `docker-compose*.yml`, `*.properties`), `scripts/architecture/
generate-architecture-model.sh` (Part E); `scripts/architecture/generate-architecture-model.sh`
(Part D); `scripts/unit-tests.sh`, `scripts/unit-tests/run.sh`, `scripts/integration-tests.sh`,
`integration-tests/run.sh`, `scripts/run-all-tests.sh` (Part A); `marketplace-app/src/test/java/
org/ost/marketplace/architecture/ArchitectureMetricsExport.java`, `scripts/architecture/
generate-architecture-model.sh` (Part B and Part C).
**Priority:** Top — inherits `improvement-151`'s original "explicit user request to rank at the
very top" positioning for Part A; Part B and Part C are investigation/design-only, not yet
actioned; Parts D and E are actioned immediately, ahead of A/B/C.
**When:** Part E — done for `scripts/sonar/` (proving case) + the generalized mechanism; rolling
onto every other script is explicit future work, not started. Part D — done. Part A — independent,
no blockers, next. Part B — blocked, see below; not started. Part C — independent, not started.

## Part E — Repo-wide script documentation convention: self-contained script header, README describes flow, architecture-map surfaces both live (design done — `.claude/skills/infra-doc-standards/SKILL.md`; real application to `scripts/sonar/` still pending)

**Design shipped**, in its own dedicated skill (`.claude/skills/infra-doc-standards/SKILL.md`, split
out from `doc-standards` once the two topics — Java-app docs vs. infra/script docs — turned out to
need separate ownership; `doc-standards` keeps a one-line "out of scope, see infra-doc-standards"
pointer, no duplicate content between the two). Covers: the 7-field file-level header (`Description`/
`Usage`/`Uses`/`Env`/`Input`/`Outputs`/`Returns`), per-function headers for `source`d library files,
`.bat` delegator forwarding (`same as <file>`), Dockerfile and `.properties` field adaptations, and
the `README.md` "Flow" section (Mermaid diagrams, multi-entry-point handling, condition-tracing
rule). Grounded in the Google Shell Style Guide and ISO/ANSI flowchart notation, both confirmed via
live web search, not assumed. `scripts/architecture/DECISIONS.md` ADR-032 covers the generator-side
mechanism (`script_headers_json()`, generalized from the `scripts/architecture`-only original) and
two real bugs found and fixed while verifying it (extractor over-scanning past the header block;
header must sit immediately after the shebang, before `set -e`). `scripts/sonar/DECISIONS.md` ADR-008
covers a real, applied feature that came out of this same work: `run.sh` now auto-validates
`sonar-project.properties`'s module list against `pom.xml` before every run.

**Done, applied for real** (was "design-only so far" — now actually executed against
`scripts/sonar/`):
1. ✅ **Applied the skill to `scripts/sonar/`** — all 4 files (`docker-compose.sonar.yml`,
   `run.bat`, `run.sh`, `sonar-project.properties`) carry real, finalized headers (7-field for
   `.sh`, delimiter-framed; 6-field for `docker-compose.yml`; 3-field for `.properties`; `run.bat`'s
   `same as run.sh` forwarding template). `README.md` rewritten with the `## Flow` section (2 entry
   points converging into one Mermaid diagram, 3 decision diamonds tracing real conditional
   behavior — image recreate, DB-migration wipe, token regen, module-list auto-fix).
   Verified end to end: `bash scripts/sonar/run.sh --no-gate` still reaches `ANALYSIS SUCCESSFUL`
   after all header edits.
2. ✅ **Regenerated `architecture-model.json`/`architecture-map.html`.** Surfaced two more real
   bugs in `script_headers_json()` while doing so (`Output`→`Outputs` rename never reached the
   parser's own regex; `Env`/`Returns` were never in its field list at all — see
   `scripts/architecture/DECISIONS.md` ADR-032's third/fourth bug notes) — fixed and re-verified via
   screenshot: all 7 header columns now render correctly on the Sonar card, with each file-type's
   deliberately-omitted fields (e.g. `Returns` for Docker, 4 fields for `.properties`) rendering
   empty rather than absorbing a neighboring field's content.
3. **Still open:** remove the `scripts/sonar`-specific illustrative example content from
   `infra-doc-standards/SKILL.md` now that the real files carry the real headers (the skill's own
   copy is now a `doc-standards`-style "one fact, one canonical home" duplicate) — not done this
   pass, needs explicit confirmation before editing the skill again.

Follow-up beyond `scripts/sonar/` (also not started): roll the same convention onto every other
script in the repo (root `scripts/*.sh`/`*.bat`, `scripts/ai/`, `scripts/ci/`, `playwright/`).

### `playwright/` — two-level directory structure, design discussed, not started

`playwright/` is the first `SCRIPT_GROUP_DIRS` candidate with real nested content the current
`-maxdepth 1` file-glob never reaches: `playwright/e2e/` (7 `.spec.js` + `_helpers.js`) and
`playwright/e2e/_flows/` (14 `.flow.js`, `require()`d by 2+ spec files — the JS analogue of the
existing "per-function header for files `source`d by other scripts" convention). `playwright/e2e/`
also has its own `README.md`, a test catalog — a genuinely different document shape from the
`## Flow` call-chain section the convention defines for a script directory's `README.md`.

**Design direction agreed in chat, not yet implemented:** card-then-drill-down, one level at a
time — not stacking `playwright`/`e2e`/`_flows` as multiple sections on one page. Clicking the
"Playwright" card on the Tooling & Pipelines list shows only `playwright/`'s own top-level files
(`run.sh`/`run.bat`/`playwright.config.js`/`reporter.js`) + its own `README.md`, plus one
subdirectory card ("e2e"). Clicking that card opens its own page: `playwright/e2e/`'s own files +
its own `README.md` (the existing test catalog, unchanged), plus one further subdirectory card
("_flows"). Clicking that opens `playwright/e2e/_flows/`'s own files, no further subdirectory
cards. Each level is `SCRIPT_GROUP_DIRS`'s existing flat, `-maxdepth 1`-per-directory shape —
nothing new needed for file discovery — the new part is a drill-down screen (generalizes the same
card→detail pattern already used for the System module screen and the Diagrams groupKey screen)
that resolves child cards purely from other `SCRIPT_GROUP` node ids sharing the parent id as a
prefix (`playwright/e2e` is a child of `playwright`; `playwright/e2e/_flows` is a child of
`playwright/e2e`) — no hardcoded "e2e"/"_flows" names in the renderer.

Still open, not decided:
- Whether `.spec.js`/`.flow.js` files get a lighter-weight header-field adaptation (most fields
  `None`, `Description` + a pointer to `e2e/README.md`'s own per-test detail) instead of the full
  7-field script header — proposed in chat, not confirmed.
- Exact mechanics of the new drill-down screen/router state (a new `screen` type vs. extending the
  existing `pipelines` screen with a path array) — not designed yet, only the card-per-level UX is
  agreed.

### Problem

While fixing `scripts/sonar`'s README/comments (Part D follow-up work), a top-of-file "Usage:"
comment got trimmed down to a bare pointer at `README.md` ("see README.md 'How to run'") under the
"one line or none" code-comment rule. Rejected: a script's own top comment must be self-contained
— what it does, what parameters are valid, what result to expect — not a redirect to another file.
This surfaced a real, repo-wide gap: `scripts/architecture/*.sh`/`*.js` already have a real,
mechanically-parsed 4-field header (`Description:`/`Uses:`/`Input:`/`Output:` — see
`scripts/architecture/DECISIONS.md` ADR-022, read live by `architecture_tooling_self_docs_json()`
and shown on the "Build architecture page" card), but this convention (a) is undocumented as a
standing rule anywhere outside that one generator function's own code, (b) is hardcoded to only
scan `scripts/architecture/` (`architecture_tooling_self_docs_json()`'s `folder =
os.path.join(repo_root, 'scripts', 'architecture')`), never applied to any other script directory,
and (c) has no field for valid CLI parameters/flags — exactly the gap that triggered this.

### Research grounding (2026-08-14, web search)

Checked against real bash-scripting convention before finalizing, per explicit request: the
[Google Shell Style Guide](https://google.github.io/styleguide/shellguide.html) — the most widely
cited authoritative bash convention — requires a top-of-file header comment (brief overview) and,
for functions, a structured block: `Description`, `Globals`, `Arguments`, `Outputs`, `Returns`.
This validates both the general shape (structured header block, not prose) and specifically the
missing field this issue exists to add — Google's `Arguments` is exactly our `Input:`/`Uses:`
fields' missing sibling for "what CLI flags does invoking this take."

### Decision — three-layer documentation, one canonical home each

1. **Script's own top-of-file header — self-contained, mechanically parsed.** Every script gets a
   structured header, extending the existing 4-field convention with a 5th field:
   `Description:` / `Usage:` (valid CLI parameters/flags — the new field) / `Uses:` / `Input:` /
   `Output:`. This is a deliberate, explicit exception to the "one line or none" code-comment rule
   (a structured metadata block, not prose rationale) — the rule needs an explicit carve-out
   documenting this, not just tacit precedent in one directory.
2. **README.md (per script-group directory) — describes the *flow between* scripts, not each
   script's own behavior again.** One script's output frequently feeds another's input (e.g.
   `scripts/sonar.sh` → `scripts/sonar/run.sh`; `scripts/ci.sh` → `scripts/ci/run.sh` →
   `scripts/unit-tests.sh`/`integration-tests.sh`/`deploy.sh`+`playwright/run.sh`/`sonar.sh`).
   README's job is to document that chain — what calls what, in what order, what the end-to-end
   result is — referencing each script's own header (by name/path) instead of restating its
   Description/Usage/Output. The existing `scripts/sonar/README.md` "Key files" table's Role
   column currently *restates* a shortened version of what the header now says — needs trimming
   once headers exist, per "one fact, one canonical home."
3. **`architecture-map.html`'s Tooling & Pipelines cards — renders both live, no third copy.**
   `architecture_tooling_self_docs_json()` (renamed/generalized, no longer hardcoded to
   `scripts/architecture`) scans every `SCRIPT_GROUP_DIRS` entry's own files for this header,
   parses the 5 fields (added: `Usage`), and renders a per-file table on that group's own card —
   the exact mechanism already built for "Build architecture page," extended repo-wide. The
   README's flow/chain content renders alongside via the already-built `readme_json_for()` +
   `mdBlockToHtml()` path (Part D/ADR-031) — two distinct sections on one page, sourced from two
   distinct files, no restatement between them.

### Where the convention itself gets documented (not the case study)

Per `.claude/rules.md`'s own "state the abstract principle, not a case study" rule: the concrete
5-field spec and the "header vs. README vs. architecture-map" layering belongs in
`.claude/skills/doc-standards/SKILL.md` (already the "how do facts about this repo get organized
across files" hub, already has a "Code comment rationale" ownership-table row added this session) —
a new dedicated section, not a second skill file. `.claude/rules.md`'s "Code comments: one line or
none" rule gets one added sentence carving out this structured header as an explicit exception,
cross-referencing the doc-standards section for the exact field spec — same "one canonical home,
cross-referenced" shape as the fix already applied earlier this session for where trimmed-comment
rationale goes.

### Scope for the first pass

`scripts/sonar/` (the 4 files already touched this session: `run.sh`, `run.bat`,
`docker-compose.sonar.yml`, `sonar-project.properties`) as the proving case — including a header
adapted for YAML/properties comment syntax (`#`-prefixed, same 5 fields). Generalizing
`architecture_tooling_self_docs_json()` to scan every `SCRIPT_GROUP_DIRS` entry (not just
`scripts/architecture`) is part of this pass so the Sonar card actually shows the new headers
live — doing the convention without the live-render half would leave it exactly as
un-discoverable as the gap this issue starts from. Rolling the header onto every *other* script in
the repo (root `scripts/*.sh`/`*.bat`, `scripts/ai/`, `scripts/ci/`, `playwright/`) is explicitly
**not** in this pass — flagged as follow-up work once the convention/mechanism is proven on one
directory, not assumed to be needed here.

## Part D — Tooling & Pipelines screen: regroup script cards by tool, not by ai/scripts category (done)

**Shipped.** See `scripts/architecture/DECISIONS.md` ADR-030.

### Problem

`renderPipelines()` (`scripts/architecture/generate-architecture-model.sh`) groups all 6
`SCRIPT_GROUP_DIRS` (`scripts/ai`, `scripts/architecture`, `scripts`, `scripts/ci`,
`scripts/sonar`, `playwright`) into only two buckets via `SCRIPT_GROUP_CATEGORY`: `ai` → "AI
Tooling" heading (currently `scripts/ai` + `scripts/architecture` together, alongside the
Commands/Skills tables), `scripts` → "Other Scripts" heading (currently `scripts`, `scripts/ci`,
`scripts/sonar`, `playwright` all together). The "How this page is built" self-docs table
(`MODEL.architectureToolingSelfDocs`, describing `scripts/architecture`'s own generator scripts)
renders as a separate trailing section instead of being part of that directory's own card.

### Fix

Split into one group heading/card per tool instead of the current 2-bucket split (Docker and
Runtime sections are unaffected — stay exactly as they are today):

- **AI Tooling** — `scripts/ai` only (drop `scripts/architecture` out of this bucket)
- **Build architecture page** — `scripts/architecture` (the architecture-map generator itself),
  with the existing "How this page is built"/`architectureToolingSelfDocs` table folded into this
  card instead of a separate trailing section
- **Playwright** — `playwright`
- **Sonar** — `scripts/sonar`
- **CI** — `scripts/ci`
- **Other Scripts** — `scripts` (root-level scripts not covered by any of the above)

Implementation: replace the binary `SCRIPT_GROUP_CATEGORY` (`ai`/`scripts`) with a 1:1
per-directory group-heading label, one heading per `SCRIPT_GROUP_DIRS` entry.

## Part A — `scripts/build.sh`: unit-tests.sh → integration-tests.sh redundantly recompiles/reinstalls the same modules

Moved verbatim from `improvement-151` (never implemented there — that issue's actual code work all
went into an unrelated architecture-generator cleanup instead, bundled in per explicit user
request; see `improvement-151`'s own text for that history).

### Problem

`scripts/unit-tests/run.sh` runs `./mvnw -pl "$MODULES" -am test` — the Maven `test` goal, which
compiles the reactor but never runs `install`, so nothing lands in `~/.m2`.

`integration-tests/run.sh` has its own staleness check (`DECISIONS.md` ADR-007): it compares each
of `platform-commons`/`advertisement`/`user`/`taxon`/`audit`/`attachment`/
`provider-profile-spring-boot-starter`'s newest `.java` file against its `~/.m2`-installed JAR's
mtime, and runs `./mvnw install -pl <stale modules> -am -DskipTests` for anything stale before
testing.

Because `unit-tests.sh` never installs, running `integration-tests.sh` right after
`unit-tests.sh` — even with zero further code changes — always finds every module "stale"
relative to whatever was last installed (which could be from a much earlier session) and
re-triggers a full `mvn install` for all of them. Confirmed (originally, in `improvement-151`): a
`bash scripts/run-all-tests.sh --integration "--sandbox"` run took 16m23s for the unit-tests leg
and a further 2m07s for integration-tests immediately after — the second number is almost entirely
redundant recompilation of source `unit-tests.sh` had already compiled seconds earlier.
`scripts/DECISIONS.md` ADR-004 already notes the partial version of this: `unit-tests.sh`'s
`-am` reactor build pre-warms `target/classes` so the recompile inside that later `install` finds
"nothing to compile" — but the `mvn install` invocation itself still always runs, paying its own
Maven-startup/dependency-resolution overhead for no reason.

### Suggested fix

Introduce `scripts/build.sh` — a single `mvn install -DskipTests` for the whole reactor (or at
least the modules `integration-tests` depends on: `platform-commons` + 6 starters +
`marketplace-orchestrator`). No changes needed to `unit-tests.sh`/`integration-tests.sh`
themselves — both already degrade correctly to "nothing to compile"/"nothing stale" once this
runs first:
- `unit-tests.sh`'s `mvn test` skips recompilation via Maven's own incremental compiler check
  (unchanged `target/classes`).
- `integration-tests.sh`'s existing staleness check (ADR-007) compares `~/.m2`-installed JAR
  mtimes against source — once `build.sh` has installed fresh JARs, it finds nothing stale and
  skips its own `install` step entirely, going straight to `mvn -pl integration-tests test`.

`run-all-tests.sh` calls `build.sh` once as its first step, before launching the
unit-tests → integration-tests sequence and the parallel Playwright leg (Playwright itself never
touches Maven, so it doesn't need or benefit from this — see ADR-004).

**Explicitly out of scope, investigated and rejected in `improvement-151`:**
- Merging `deploy.sh`/`deploy-dev.sh` into one parametrized script — considered; `deploy.sh` (318
  lines, infra bootstrap, Liquibase self-heal, CI isolated-stack env-var overrides) and
  `deploy-dev.sh` (64 lines, assumes infra/app already running) are different-shaped flows, not
  parameter variations of the same one; merging would either bloat `deploy.sh` with a branch where
  most of its own flags become meaningless, or duplicate bootstrap logic. Not pursued.
- Making `deploy.sh`'s Docker image build reuse host-compiled classes — rejected on principle:
  the deploy image must build reproducibly from source in an isolated context, not from
  potentially-stale/uncommitted host artifacts. Only the external-dependency download cache
  (`--mount=type=cache,target=/root/.m2` in BuildKit) is legitimately shared.
- `ArchitectureRulesTest`'s own ~195s ArchUnit classpath scan and Vaadin's ~61s
  `prepare-frontend` class scan — both inherent to the tools themselves, not fixable via build
  artifact reuse.

## Part B — ArchUnit Track B unblock investigation (not started, background/decision material only)

### Why this is being looked at

While reviewing the SPI Map diagram's data quality (`improvement-151`'s Step 0 work), found
concrete, confirmed evidence that the current grep-regex-based extraction in
`generate-architecture-model.sh`'s `spi_map_json()` has real accuracy gaps:
- All 14 `platform-commons` `*.spi` interfaces' Javadoc `Port: marketplace → X-starter.`/
  `Hook: ... → marketplace.` first lines are stale post-BFF-migration — the real caller/implementor
  module is always `marketplace-orchestrator`, never `marketplace-app` directly, confirmed against
  `MODEL.spiMap.details[].callers`/`.implementations` for every single one of them.
- `spi_map_json()`'s `IMPL_PATTERN`/`CALLER_PATTERN` are field-declaration/`implements`-clause text
  regexes — class-level only, no method-level data, and blind to any caller that doesn't fit one of
  3 hardcoded field-declaration shapes (a method-parameter injection, a local variable, etc.).
- `AuditAutoConfiguration` shows up as an `AuditPort` "caller" purely because it imports the type
  for `@Bean` wiring — a false positive at the method-call level, found while mocking up the SPI
  Interface Details table redesign (see `improvement-151`'s "Ideas" section).

Real ArchUnit-based bytecode analysis (not text regex, not hand-written Javadoc) would close all
three gaps — see chat discussion this session for the fuller reasoning (real caller/implementor
edges verified by bytecode, method-level granularity, distinguishing real calls from DI-wiring-only
references, and a path to a real Test Coverage overlay).

### What already exists

- `ArchitectureMetricsExport` (`marketplace-app/src/test/java/org/ost/marketplace/architecture`) —
  a real ArchUnit test/exporter, already wired: writes `marketplace-app/target/architecture-metrics
  .json` every time `bash scripts/unit-tests.sh` runs. `generate-architecture-model.sh
  --with-archunit` reads it — but **only module-level metrics today** (Efferent/Afferent Coupling,
  Instability, Abstractness), no interface/method/contract-level data.
- The Code Quality screen already has placeholder rows anticipating exactly this gap: "Contract
  method signatures & types", "Implementation classes", "Methods", "Test coverage (DIRECT/INDIRECT/
  E2E)" — all tagged `needs ArchUnit exporter`.

### The real blocker — this is a decision gate, not a technical one

This is exactly `improvement-138`'s **Track B**, and it is **officially blocked**, per that issue's
own recorded Finding 3: Track B conflicts with `improvement-135` item 5's governing rule — *"No new
navigation file, no new metadata field, no expansion of `adr-index.md`'s schema... until items 2-4
show the existing layer is pulling its weight, or a specific, evidenced discovery failure
demonstrates a gap the current layer can't cover."* Track B's L0-L5 AI Context Layer is squarely
the kind of new AI-navigation content that rule was written to gate.

`improvement-138` records exactly two conditions that unblock Track B (still both unmet as of this
writing — confirmed against `improvement-135`'s own current status: item 3's mechanism is built,
but its empirical answer is still pending real accumulated data):
1. `improvement-135` item 3 produces real accumulated evidence (via `/sync-docs --full-audit`)
   showing the *existing* `context-loading.md`/`flows.md` layer is not sufficient for
   architecture/impact-analysis-shaped tasks — a genuine discovery-gap finding.
2. The user explicitly decides Track B itself *is* that evidenced-gap exception — recorded plainly
   as a deliberate decision (who decided it and why), not silently assumed.

Today's SPI Map findings (above) are **not** the same thing as condition 1 — they're evidence the
*generator's own data source* has accuracy problems, not evidence about `context-loading.md`'s
AI-context-loading efficiency specifically (`improvement-135` item 3's actual hypothesis). They
could plausibly support condition 2 (a user decision that this particular gap justifies starting
Track B early), but that's the user's call to make explicitly, not something to assume from this
issue's own existence — same framing `improvement-138`'s Finding 3 already established.

Two known implementation-detail fixes already recorded in `improvement-138`, to apply whenever B
actually starts (not blockers, just corrections to the original plan):
- Finding 1: the production-code exporter and any future test-scanning exporter need two separate
  `@AnalyzeClasses` configurations — `ArchitectureRulesTest`'s existing import uses
  `ImportOption.DoNotIncludeTests`, so it cannot be literally reused for a test-scanning exporter.
- Finding 4: any token-cost/AI-context measurement Track B adds must extend `improvement-135`'s
  existing `## Operational notes` block, not introduce a differently-named block.

### Not yet done

- No decision made on which of the two unblock conditions applies, or whether to wait longer.
- No design work started on what an ArchUnit-based `spi_map_json()` replacement would actually look
  like (query shape, caching/staleness relative to `bash scripts/unit-tests.sh`, how it'd feed the
  SPI Interface Details table redesign from `improvement-151`'s "Ideas" section).

## Part C — SPI Interface Details table redesign — split Callers/Implemented By, group by Module → Class → Method

Moved verbatim from `improvement-151`'s "Ideas — captured, not started" section once that issue's
own real work was ready to close. Design only, no code written.

Scoped first to the Audit Subsystem (`org.ost.platform.audit.spi`, `SPI Interface Details (3)`
card) as the experiment, before spreading the same shape to every other subsystem's table.

**Shape:**
1. Split the current single table (`Caller(s) | Interface | Direction | Implementation(s) |
   Purpose`) into **two separate tables** — one for Callers, one for Implemented by.
2. Inside each table, group rows: **Module** (1st column) → **Class** (2nd column) → **Method**
   (3rd column).
3. Method column format:
   - Callers table: `callerMethod() → Interface#interfaceMethod()` — the caller's own method next
     to the specific interface method it invokes, not just "this class touches this interface"
     class-level as today.
   - Implemented by table: bare `method()` per line (the row's own Class column already identifies
     which class it belongs to, so no `ClassName#` prefix needed there — only needed if a future
     variant ever combines multiple implementation classes into one cell).

A concrete mockup for the Audit Subsystem, built from real grep against the current code (not
placeholder text), was shown and approved in chat — worth re-deriving rather than restating in
full here, but the real findings that came out of building it are worth keeping:

- **`AuditAutoConfiguration` is not a real per-method caller.** It only shows up in today's
  class-level scan because it imports `AuditPort`/`DefaultAuditPort` for `@Bean` wiring — there's
  no method call to put in a Method column for it. The redesigned table needs an explicit answer
  for this shape (drop it from Callers entirely, or show it as `(DI wiring)` with no method), not
  silently reuse today's class-level "callers" list as-is.
- **Zero method-level data exists in the model today.** `spi_map_json()`'s `IMPL_PATTERN`/
  `CALLER_PATTERN` are field-declaration/`implements`-clause regexes — class-level only, no method
  parsing. Building this table for real (not a hand-typed mockup) needs new extraction work,
  larger in scope than the existing class-level scan: per interface method, grep each candidate
  caller/implementor file for an actual invocation/override of that specific method.
- **Generic interfaces need extra thought.** `AuditActivityEnrichHook<T extends AuditableSnapshot>`
  — a bare `entityType()` method name in the table says nothing about which concrete `T` a given
  implementation (e.g. one bean per `EntityType`) is registered for; may need the type argument
  shown alongside the method, not decided how.

**Natural link to Part B**: a real ArchUnit-based exporter (once Track B unblocks) would be the
mechanical data source this table redesign actually needs (method-level caller/implementor pairs) —
the two parts are independent to file but likely sequenced together in practice.

## Related

- `improvement-151` — where the original `build.sh` topic, the SPI Map findings motivating Part B,
  and Part C's own design work all came from; see that issue for the full architecture-generator
  cleanup history.
- `improvement-138` — Track B's own issue, Finding 3 (the blocking rule) and Findings 1/4 (fixes to
  apply once unblocked).
- `improvement-135` — item 5 (the governing rule blocking Track B), item 3 (the evidence Track B is
  waiting on).
- `scripts/DECISIONS.md` ADR-007 — the existing staleness-check mechanism Part A extends the
  benefit of.
- `scripts/DECISIONS.md` ADR-004 — `run-all-tests.sh`'s sequential-Maven/parallel-Playwright
  design; `build.sh` slots in as its new first step.
- `scripts/CLAUDE.md` "Plain Unit Tests" / "Unit / Testcontainers Tests" sections — the two
  scripts' documented behavior Part A would update once implemented.
