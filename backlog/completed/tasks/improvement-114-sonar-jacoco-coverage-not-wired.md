# improvement-114: SonarQube quality gate's `new_coverage` condition always fails — JaCoCo never wired up

**Type:** infrastructure gap — found while running Sonar after improvement-113
**Module:** `scripts/sonar/`
**Priority:** 🟡 medium — the quality gate has been silently unenforceable on its `new_coverage`
condition for the whole `PREVIOUS_VERSION` leak period; any future run that adds real code will
hit the same failure regardless of how good that code's actual tests are
**When:** independent, no blockers — worth doing before the next time `bash scripts/sonar.sh`
(without `--no-gate`) is expected to actually pass

## Problem

Running `bash scripts/sonar.sh` after improvement-113 failed the quality gate on `new_coverage`
(`0.0%`, threshold `80%`) even after the two genuinely-new `new_violations` were resolved (both
confirmed false positives, suppressed via `@SuppressWarnings`). Investigated further:
`scripts/sonar/sonar-project.properties` has **no `sonar.coverage.jacoco.xmlReportPaths`** entry,
and neither `run.sh` nor any module's `pom.xml` (unchecked further, but `grep -rn "jacoco"` across
`scripts/sonar/` returns nothing) feeds a JaCoCo execution report into the scanner. This means
Sonar has never received real coverage data for **any** module — `new_coverage` reads `0.0%`
whenever the leak period (currently `PREVIOUS_VERSION`, since 2026-06-24) contains any new lines
at all, regardless of how well-tested those lines actually are.

Confirmed via `GET /api/measures/component_tree?component=advertisement&metricKeys=new_lines_to_cover`
— the "new" period spans the accumulated work of the last month (improvement-025's four batches,
improvement-108, the earlier Sonar-fix pass, improvement-113), not just one PR's worth of change,
because the quality-gate-blocking `sonar.sh` run (vs. the informational `--no-gate` variant) simply
hadn't been exercised end-to-end against real new code since that leak period started.

**Two separable sub-problems, deliberately not conflated:**
1. Service-layer code (`AdvertisementSaveService`, `AttachmentCleanupService`, repositories, etc.)
   genuinely has JUnit coverage today (`scripts/unit-tests.sh` / `scripts/integration-tests.sh`
   pass) — that coverage is real, it's just never reported to Sonar.
2. Vaadin UI-layer code (`ui/**`) is deliberately verified via Playwright e2e, not JUnit, per this
   project's established test strategy (see `marketplace-app/CLAUDE.md`, `playwright/CLAUDE.md`).
   Wiring JaCoCo into the unit/integration test runs would raise coverage for (1) but would **not**
   move the needle for (2) — Playwright's browser-driven e2e runs don't feed a JVM-side JaCoCo
   agent without separately instrumenting the running server under test, a materially bigger lift
   than "just add the Maven plugin."

## Decided plan (2026-09-08, ready for implementation)

**Key nuance driving this shape:** `integration-tests` is this project's sole home for
Testcontainers-based repository tests of the 7 domain starters (advertisement/user/taxon/audit/
attachment/provider-profile/apikey) — the starters carry no test code of their own (see
`.claude/rules/integration-tests.md`). A plain, per-module `jacoco:report` in each starter would
therefore report `0%` for all of them regardless of real coverage, because the test code that
exercises them physically lives in a different module — `report` only attributes exec hits against
classes present in *that same module's own* `target/classes`. Confirmed via `integration-tests/pom.xml`:
it already reactor-depends on `platform-commons` + all 7 starters + `marketplace-orchestrator` +
`marketplace-rest-api` — exactly what `jacoco:report-aggregate` needs to correctly attribute
integration-tests' exec data back to those modules' own classes, with no new aggregator module
needed.

**Two separate report shapes, matching this project's existing two-container test split**
(`scripts/build-and-test/build.sh`'s `run_unit_tests` vs `run_integration_tests`, run in
separate, isolated Docker containers — so a module's own `jacoco.exec` is only visible within the
same container it was generated in):

1. **The 4 unit-tested modules** (`query-lib`, `marketplace-app`, `marketplace-orchestrator`,
   `marketplace-rest-api`) — plain `jacoco:report`, generated inside the same container that already
   runs their unit tests. Self-contained, no cross-module attribution needed.
2. **`integration-tests`** — `jacoco:report-aggregate`, generated inside the integration-tests
   container (which already does a full `-DskipTests` reactor install first, so `target/classes`
   for every module it depends on already exists there). This is what actually closes sub-problem 1
   for the 7 starter modules.

**Files to touch:**
- `pom.xml` (root) — add `jacoco-maven-plugin` to `<build><plugins>` (inherited by every module
  automatically). Two executions: `prepare-agent` (attaches the coverage agent to the JVM surefire
  forks for), `report` bound to the `test` phase (JaCoCo's own documented pattern — phase-ordering
  within `test` makes it run after surefire's own `test` goal, no `verify`-phase workaround needed).
- `integration-tests/pom.xml` — one additional `report-aggregate` execution.
- `scripts/build-and-test/build.sh` — copy each module's `target/site/jacoco/jacoco.xml` (4 direct
  reports + 1 aggregated report from `integration-tests`) into `$LOGS_DIR`/`$REPORTS_DIR`, next to
  the existing surefire-report copy loop.
- `scripts/sonar/run.sh` — carry those 5 XML files into the `sonar-scanner` container, same
  `docker cp`/volume mechanism already used for `target-classes`.
- `scripts/sonar/sonar-project.properties` — `sonar.coverage.jacoco.xmlReportPaths=` listing all 5
  paths, comma-separated. `marketplace-orchestrator`/`marketplace-rest-api` will legitimately appear
  in two of the five reports (their own unit-test report + integration-tests' aggregate touching
  their classes too) — safe: Sonar's JaCoCo XML importer merges multiple report inputs per
  file+line, not a conflict or double-count.
- Sub-problem 2 (UI layer, `ui/**`, deliberately Playwright-only) — carve out
  `sonar.coverage.exclusions` for it in the same properties file, same reasoning already applied
  narrowly to `ui/query/elements/**` in improvement-113 (`marketplace-app/DECISIONS.md` ADR-056),
  just widened to the whole `ui/**` tree.

**Verification plan (unchanged from original):**
- After wiring: make a trivial change to a JUnit-covered service method, run `bash scripts/sonar.sh`,
  confirm `new_coverage` reports a non-zero, plausible percentage instead of `0.0%`.
- Confirm the quality gate can pass end-to-end (not just via `--no-gate`) for a change that
  genuinely has good test coverage.

## Verification plan

- After wiring: make a trivial change to a JUnit-covered service method, run `bash
  scripts/sonar.sh`, confirm `new_coverage` reports a non-zero, plausible percentage instead of
  `0.0%`.
- Confirm the quality gate can pass end-to-end (not just via `--no-gate`) for a change that
  genuinely has good test coverage.

## Adjacent idea noted here (2026-08-28, not planned/implemented, unrelated to this issue's own coverage-wiring scope)

Surfaced while discussing what other real code-quality metrics SonarQube/ArchUnit/Dagu can still
provide, alongside this issue's own coverage gap: `generate-architecture-model.sh`'s
`coupling_checks_json()` (Code Quality screen's "Architecture Checks") re-runs 3 hand-written grep
patterns every generation ("No Vaadin imports in starters", "No direct starter-to-starter internal
imports", "No UI to Repository direct imports") instead of a real bytecode check.
`ArchitectureMetricsExport.java` (`marketplace-app/src/test/java/org/ost/marketplace/architecture/`)
already loads the full `JavaClasses` graph for its module-coupling metrics — ArchUnit's own
`SlicesRuleDefinition.slices()...beFreeOfCycles()` (true cycle detection) and
`noClasses().that()...should().dependOnClassesThat()...` (real layering/import rules) could replace
these 3 greps with real, already-available bytecode checks in the same JVM run, no extra cost. Not
sized or planned in detail — noted here only because it came up in the same conversation as this
issue's own metric gap, not because it shares a root cause with JaCoCo/coverage wiring.

## Confirmed still open, real evidence from a `/ci` DAG run (2026-08-29)

Same root cause, observed again end-to-end via `bash scripts/ci.sh`'s `sonar` stage (not a local
`bash scripts/sonar.sh` run): quality gate failed on `new_coverage` (`0.0%`, threshold `80%`) while
`new_duplicated_lines_density` and `new_violations` both passed clean. Real scanner log lines:

```
No report imported, no coverage information will be imported by JaCoCo XML Report Importer
Invalid value for 'sonar.java.binaries', no files nor directories matching 'query-lib/target/classes'
```

The second line turned out to be a genuinely separate bug, root-caused and fixed the same day
(2026-08-29): `scripts/sonar/run.sh`'s copy loop (`for module in query-lib platform-commons ...`)
runs `docker exec "$SCANNER_CONTAINER" test -d "/root/.m2/target-classes/$module"` as the
container's *default* exec user (`scanner-cli`, uid 1000) — but `/root` inside `sonar-scanner`
is `dr-xr-x---` (750, owner+group only), so that check silently returns false for **every**
module regardless of whether the data exists, and the whole binaries-copy step gets skipped
across the board (not `query-lib`-specific; it just happens to be first in
`sonar.java.binaries`'s list, so it's the first one Sonar's own error names). Confirmed live
against the real running `sonar-scanner` container: `query-lib`'s compiled classes genuinely
existed there (`--user root` could see them), the non-root check just couldn't reach `/root` at
all to find out. Fixed by adding `--user root` to the three `docker exec` calls in that loop,
matching the same flag this script already uses elsewhere (`rm -rf /tmp/sonar-src`) — re-verified
live post-fix (`docker exec --user root sonar-scanner test -d
/root/.m2/target-classes/query-lib` now returns true). This fix is independent of and doesn't
replace this issue's own JaCoCo-wiring gap above — both still need to land before `new_coverage`
reports a real, non-zero number.

## Verified (2026-09-08)

Implemented the plan above (all 5 files) and ran `bash scripts/sonar.sh` in blocking mode (no
`--no-gate`). Real, confirmed results from the SonarQube API:
- Scanner log: `Importing 5 report(s)` — all 5 JaCoCo XML paths found and imported.
- `coverage` (overall project metric): **20.0%** — previously always literally `0.0%` (no data
  ever imported at all); this is the definitive proof the wiring works.
- `new_coverage` for this specific run: `0.0%`, but with only 7 new coverable lines in the current
  `PREVIOUS_VERSION` leak period (residual from an earlier commit this session) and
  `ignoredConditions: true` in the gate response — SonarQube's own standard behavior when too
  little new code exists to meaningfully evaluate a new-code percentage metric, not a wiring
  failure.
- `QUALITY GATE STATUS: PASSED` — end to end, in blocking mode, for the first time.

`sonar.coverage.exclusions` widened from the narrow `ui/query/elements/**` (improvement-113's
stopgap) to the whole `marketplace-app/.../ui/**` tree, closing sub-problem 2 from this issue's
original analysis.

See `scripts/sonar/DECISIONS.md` ADR-010 for the full design record (also amends ADR-001's
"no pom.xml changes" constraint, narrowly, for `jacoco-maven-plugin`).

## Related

- [improvement-113](../completed/tasks/improvement-113-query-elements-leaf-components-plain-classes.md) —
  where this was found; `sonar.coverage.exclusions` was narrowly scoped to
  `ui/query/elements/**` there as a stopgap, not a full fix for this issue.
- `scripts/sonar/DECISIONS.md` — quality-gate-blocking rationale (why `-Dsonar.qualitygate.wait=true`
  needed more than just adding the flag).

## Operational notes
- token_cost_review: 109204
- token_cost_research: n/a
- token_cost_verification: n/a
- review_signal_ratio: 1 / 3
- context_loading_task_type: n/a
- context_loading_consulted: n/a
- context_loading_matched: n/a
- flows_situation: implement an already-decided infra/config plan end to end via /autopilot, verify via a real blocking Sonar run
- flows_chosen: /autopilot
- flows_matched: yes

### Agent calls
- Code review of JaCoCo wiring changes | subagent_type=deep-review-orchestrator | tokens=109204 | tool_uses=40 | duration_s=460 | mode=background | batch=solo

### Script/command runs
- bash scripts/sonar.sh (blocking, no --no-gate) | duration_s=382 | mode=background | result=pass

### Review angle yield
- dry-kiss-yagni | survived=1 | total_candidates=1 | tokens=56745
- solid | survived=0 | total_candidates=0 | tokens=38897
- precedent | survived=2 | total_candidates=2 | tokens=80544
