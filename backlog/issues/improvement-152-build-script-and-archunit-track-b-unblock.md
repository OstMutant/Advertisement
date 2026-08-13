# improvement-152: `scripts/build.sh` (redundant recompile fix) + ArchUnit Track B unblock investigation + SPI Interface Details table redesign

**Type:** improvement + investigation + design idea — three independent topics filed together, all
split out of `improvement-151` once that issue's own real (verified) work was done and ready to
close.
**Module:** `scripts/unit-tests.sh`, `scripts/unit-tests/run.sh`, `scripts/integration-tests.sh`,
`integration-tests/run.sh`, `scripts/run-all-tests.sh` (Part A); `marketplace-app/src/test/java/
org/ost/marketplace/architecture/ArchitectureMetricsExport.java`, `scripts/architecture/
generate-architecture-model.sh` (Part B and Part C).
**Priority:** Top — inherits `improvement-151`'s original "explicit user request to rank at the
very top" positioning for Part A; Part B and Part C are investigation/design-only, not yet
actioned.
**When:** Part A — independent, no blockers. Part B — blocked, see below; not started. Part C —
independent, not started.

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
