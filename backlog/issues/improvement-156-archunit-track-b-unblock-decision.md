# improvement-156: ArchUnit Track B unblock — decision gate, plus a real `spi_map_json()` replacement design

**Type:** investigation + decision material — split out of `improvement-152` once that issue's Part
A/D/E work was ready to close. The underlying technical prerequisite (a working ArchUnit exporter)
is done; the actual unblock is a decision gate, not a technical task.
**Module:** `marketplace-app/src/test/java/org/ost/marketplace/architecture/
ArchitectureMetricsExport.java`, `docs/architecture/scripts/generate-architecture-model.sh`
(`spi_map_json()`).
**Priority:** Top — explicit placement, ranked directly after `improvement-155`.
**When:** blocked on a decision — see "The real blocker" below. Not started.

## Why this is being looked at

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
three gaps: real caller/implementor edges verified by bytecode, method-level granularity,
distinguishing real calls from DI-wiring-only references, and a path to a real Test Coverage
overlay.

## What already exists — was broken, now fixed (found 2026-08-16, fixed same session)

- `ArchitectureMetricsExport` (`marketplace-app/src/test/java/org/ost/marketplace/architecture`) —
  an ArchUnit exporter meant to write `marketplace-app/target/architecture-metrics.json` (module-
  level Efferent/Afferent Coupling, Instability, Abstractness), read by
  `generate-architecture-model.sh --with-archunit`. **Originally confirmed broken**: the class name
  doesn't match Surefire's default include patterns (`*Test`/`Test*`/`*Tests`/`*TestCase` —
  "ArchitectureMetricsExport" matches none), so a plain `mvn test` never discovered or ran it, and
  forcing it directly (`-Dtest=ArchitectureMetricsExport`) failed when scoped to fewer than all
  modules (`@AnalyzeClasses(packages = "org.ost")` needs every module's classes on the classpath
  simultaneously).
  **Fixed, verified working.** `scripts/build-and-test/build.sh` gained an `--archunit-metrics`
  flag (`run_archunit_metrics()`): runs `./mvnw -pl marketplace-app test
  -Dtest=ArchitectureMetricsExport -Dsurefire.failIfNoSpecifiedTests=false` — the `-Dtest=` bypass
  fixes the naming-pattern gap, and running it after the full-reactor `mvn install` step (so every
  starter's classes are on `marketplace-app`'s own resolved classpath as real `~/.m2` JARs) fixes
  the narrow-scope failure. Output copied to `/tmp/reports/architecture-metrics.json`, extracted to
  `scripts/build-and-test/reports/architecture-metrics.json` on the host.
  `generate-architecture-model.sh`'s `archunit_metrics_json()` gained a fallback path
  (`ARCHUNIT_METRICS_FILE_FALLBACK`) reading that host location. **Confirmed real, non-placeholder
  output**: `scripts/build-and-test/reports/architecture-metrics.json` contains real per-module
  coupling numbers for all 9 modules (e.g. `platform-commons`: `efferentCoupling: 0,
  afferentCoupling: 7, instability: 0.0, abstractness: 0.3`) — not zeros or an error stub.
  Measured timing: ~4m53s cold (no prior install), ~15.9s warm (reusing `build-and-test`'s own
  install).
  **This only fixes the module-coupling metrics table** (`--with-archunit`'s Code Quality screen
  data) — it does not touch the separate, larger ask below (a real ArchUnit-based `spi_map_json()`
  replacement with method-level caller/implementor data), which remains fully unbuilt.
- The Code Quality screen still has placeholder rows anticipating that separate, larger gap
  (interface/method-level data, not just module coupling): "Contract method signatures & types",
  "Implementation classes", "Methods", "Test coverage (DIRECT/INDIRECT/E2E)" — all tagged
  `needs ArchUnit exporter`.

## The real blocker — this is a decision gate, not a technical one

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

## Not yet done

- No decision made on which of the two unblock conditions applies, or whether to wait longer.
- No design work started on what an ArchUnit-based `spi_map_json()` replacement would actually look
  like (query shape, caching/staleness relative to `bash scripts/build-and-test.sh`, how it'd feed
  the SPI Interface Details table redesign from `improvement-151`'s "Ideas" section, and from
  `improvement-152` Part C).

## Related

- `improvement-152` — the issue this was split out of; Part C (SPI Interface Details table
  redesign) still lives there and depends on this issue's eventual `spi_map_json()` replacement for
  real method-level data.
- `improvement-138` — Track B's own issue, Finding 3 (the blocking rule) and Findings 1/4 (fixes to
  apply once unblocked).
- `improvement-135` — item 5 (the governing rule blocking Track B), item 3 (the evidence Track B is
  waiting on).
- `improvement-151` — where the original SPI Map findings motivating this issue came from.
