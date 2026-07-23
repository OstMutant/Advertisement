# improvement-114: SonarQube quality gate's `new_coverage` condition always fails — JaCoCo never wired up

**Type:** infrastructure gap — found while running Sonar after improvement-113
**Module:** `scripts/sonar/`
**Priority:** medium — the quality gate has been silently unenforceable on its `new_coverage`
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

## Suggested fix (investigate at implementation time, not decided here)

1. Add `jacoco-maven-plugin` to the parent POM (or each module that has JUnit/Testcontainers
   tests), producing an `exec`/`xml` report during `scripts/unit-tests.sh` and
   `scripts/integration-tests.sh` runs.
2. Merge/aggregate both reports (unit + integration) per module before the Sonar scan — check
   whether `jacoco:report-aggregate` or a manual `sonar.coverage.jacoco.xmlReportPaths` list of
   both paths is the right shape for this multi-module reactor.
3. Point `sonar.coverage.jacoco.xmlReportPaths` in `scripts/sonar/sonar-project.properties` at the
   aggregated report(s).
4. For the UI-layer gap (sub-problem 2): decide whether to carve out `sonar.coverage.exclusions`
   for `ui/**` package trees (formalizing "this code is verified by Playwright, not JaCoCo" as
   explicit config, the same reasoning already applied narrowly to
   `ui/query/elements/**` during improvement-113 — see `marketplace-app/DECISIONS.md` ADR-056) or
   pursue real server-side JaCoCo instrumentation during Playwright runs (bigger, likely not worth
   it given the project's deliberate two-test-type split).
5. Re-run `bash scripts/sonar.sh` (blocking mode) against a real, small change once wired up to
   confirm `new_coverage` reflects actual test coverage instead of a hardcoded `0.0%`.

## Verification plan

- After wiring: make a trivial change to a JUnit-covered service method, run `bash
  scripts/sonar.sh`, confirm `new_coverage` reports a non-zero, plausible percentage instead of
  `0.0%`.
- Confirm the quality gate can pass end-to-end (not just via `--no-gate`) for a change that
  genuinely has good test coverage.

## Related

- [improvement-113](../completed/issues/improvement-113-query-elements-leaf-components-plain-classes.md) —
  where this was found; `sonar.coverage.exclusions` was narrowly scoped to
  `ui/query/elements/**` there as a stopgap, not a full fix for this issue.
- `scripts/sonar/DECISIONS.md` — quality-gate-blocking rationale (why `-Dsonar.qualitygate.wait=true`
  needed more than just adding the flag).
