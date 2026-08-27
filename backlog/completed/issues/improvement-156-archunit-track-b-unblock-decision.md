# improvement-156: ArchUnit Track B unblock — decision gate, plus a real `spi_map_json()` replacement design

**Type:** implementation — split out of `improvement-152` once that issue's Part A/D/E work was
ready to close. The underlying technical prerequisite (a working ArchUnit exporter) is done;
**unblocked (2026-08-26)** — see "Reclassified" note below.
**Module:** `marketplace-app/src/test/java/org/ost/marketplace/architecture/
ArchitectureMetricsExport.java`, `docs/architecture/scripts/generate-architecture-model.sh`
(`spi_map_json()`).
**Priority:** Top — raised to the top rank (2026-08-26).
**When:** unblocked, ready to implement.

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

**Reclassified (2026-08-26) — this issue's actual scope is Track A, not Track B; the gate above
does not apply.** Re-reading `improvement-138`'s own text: Track B's item-5 gate exists
specifically to test one hypothesis — B2, "prove the AI-token hypothesis before going further" —
whether a new L0-L5 projection layer that **Claude reads instead of source** measurably saves AI
context tokens. `improvement-138` itself already states "**Track A is not gated by this** — it
produces a human-facing visual explorer from already-[available data]." This issue's actual
deliverable (real, ArchUnit-bytecode-derived method-level caller/implementor edges, replacing
`spi_map_json()`'s regex extraction) feeds exactly one place: the existing, human-facing "SPI Map"
screen in `architecture-map.html` — the same screen Track A already ships and the same kind of
work as the already-unblocked `--archunit-metrics` module-coupling exporter. It introduces no new
AI-consumption layer, no L0-L5 projection, and is not testing the AI-token hypothesis at all —
Finding 3's original "this is exactly Track B" framing conflated "uses ArchUnit" with "is Track B,"
when Track B is actually defined by its AI-token-hypothesis purpose, not by which library produces
the data. This issue therefore never needed either of Track B's two unblock conditions in the
first place — it is unblocked now, same as Track A. `improvement-138`'s own Finding 3 record is
corrected accordingly (see that file).

## Implemented (2026-08-27)

`ArchitectureMetricsExport.java` gained a `spiEdges()` method: for every `org.ost.platform.*.spi`
interface, real implementors via `JavaClass.getAllRawInterfaces().contains(iface)` (bytecode
assignability, not a text `implements` match), and real callers via
`JavaMethod.getCallsOfSelf()` per interface method (bytecode call-site data, with the specific
method names called) — written into the same `target/architecture-metrics.json` under a new
`spiEdges` key, alongside the existing module-coupling numbers. Also added the missing
`marketplace-orchestrator` → `org.ost.orchestrator` entry to `MODULE_PACKAGES` (was absent
entirely, even though orchestrator is the primary real caller of nearly every SPI interface).

`generate-architecture-model.sh`'s `spi_map_json()` now reads this real data (via an inline
`python3 -c` call, same pattern already used for `--with-sonar`'s JSON consumption — no new
dependency) instead of the old `grep`-regex tree-walk, per interface, with a per-interface
fallback to the old regex path if `spiEdges` has no entry for it (e.g. `--archunit-metrics` was
never run this session) — never a silent full-file fallback that could hide a real per-interface
gap.

**Verified fixed, both at the data level and in the actually-rendered table (headless Chromium,
not just JSON inspection):**
- The documented false positive — `AuditAutoConfiguration` showing up as an `AuditPort` caller
  purely from a `@Bean`-wiring import — is gone.
- A previously undocumented false *negative* found in the process: `AuditActivityEnrichHook`'s
  real callers are `AuditReadService` **and** `AuditQueryService`; the old regex only ever found
  the first one (blind to whichever field-declaration shape `AuditQueryService`'s injection used).
  Both now show correctly.

**Not part of this issue's closure — tracked separately in `improvement-157`:** while verifying
this, also built a two-table (Calls / Implemented By) SPI Interface Details redesign with
rowspan-grouped Interface/Purpose cells, clickable module links, and click-a-diagram-edge-to-jump-
to-its-row linking — all verified live via headless Chromium. This diverges from `improvement-157`'s
originally specified Module → Class → Method grouping and per-method display (no Method column was
added, even though `spiEdges`' `callers[].methods` now has the data for it) — left as an open
design question for that issue, not resolved here.

## Related

- `improvement-152` — the issue this was split out of; Part C (SPI Interface Details table
  redesign) still lives there and depends on this issue's eventual `spi_map_json()` replacement for
  real method-level data.
- `improvement-138` — Track B's own issue, Finding 3 (the blocking rule, corrected 2026-08-26) and
  Findings 1/4 (fixes to apply once unblocked — not yet applicable, this issue's own work never
  touched a test-scanning `@AnalyzeClasses` config or an AI-context-token measurement).
- `improvement-135` — item 5 (the governing rule that turned out not to apply here).
- `improvement-151` — where the original SPI Map findings motivating this issue came from.
- `improvement-157` — the SPI Interface Details table redesign this issue's real data now feeds;
  the actually-shipped table shape diverges from that issue's Module → Class → Method spec (see
  "Implemented" above) — still open there.

## Operational notes
- token_cost_review: n/a
- token_cost_research: n/a
- token_cost_verification: n/a
- review_signal_ratio: n/a
- context_loading_task_type: n/a
- context_loading_consulted: no
- context_loading_matched: n/a
- flows_situation: n/a
- flows_chosen: n/a
- flows_matched: n/a

### Script/command runs
- bash scripts/build-and-test.sh --no-unit --no-integration --archunit-metrics --skip-vaadin | duration_s=~60 | mode=background | result=pass
- bash docs/architecture/scripts/generate-architecture-model.sh --with-archunit (x6, iterative verification across the exporter + spi_map_json() + UI changes) | duration_s=~90 each | mode=background | result=pass
- headless Chromium verification via `docker exec ci-pw-runner node ...` (x5, ad-hoc Playwright scripts, not a scripts/*.sh entry point) | mode=foreground | result=pass
