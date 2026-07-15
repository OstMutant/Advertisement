# improvement-051: `run-all-tests.sh` — sequential Maven suites, parallel Playwright

**Type:** improvement — developer workflow / iteration speed.
**Module:** `scripts/` (new `scripts/run-all-tests.sh`), `scripts/DECISIONS.md` (ADR-004).
**Priority:** medium — daily-iteration convenience, not correctness-critical.
**Status:** VERIFIED (2026-07-15) — `scripts/run-all-tests.sh` and `scripts/DECISIONS.md` ADR-004
written and confirmed end-to-end: `--unit "AccessEvaluatorTest" --integration "--sandbox smoke"
--playwright "01-marketplace-empty-flow --ux"` ran unit-tests (17/17 passed) then integration-tests
(smoke passed) sequentially while Playwright ran in parallel from the start; Playwright happened to
fail for an unrelated environmental reason (dev Postgres on port 5432 wasn't running in this
sandbox at the time — `ERR_CONNECTION_REFUSED` against the app), which incidentally proved the
failure-detection path too: the summary correctly reported `SOME FAILED` with a non-zero exit code.
Still uncommitted — commit only on explicit request per project rule.

## Problem

Running `scripts/unit-tests.sh`, `scripts/integration-tests.sh`, and `scripts/playwright.sh` one at
a time during daily iteration (edit code → run all three suites → repeat) is slow and repetitive.
The three scripts already exist independently and are not meant to be replaced — the goal is a
thin, opt-in orchestrator on top of them.

## Design decision and nuances (recorded before implementation started)

**Full 3-way parallelism was considered and rejected.** `unit-tests.sh` runs
`./mvnw -pl query-lib,marketplace-app -am test`, and `integration-tests.sh` conditionally runs
`./mvnw install -pl platform-commons,advertisement-/user-/taxon-spring-boot-starter -am
-DskipTests` (see `integration-tests/DECISIONS.md` ADR-007 for that staleness-check design). Both
can compile the *same* starter modules into their shared `target/` directories. Running them
concurrently right after editing one of those starters is a genuine Maven build-race risk (one
process reading/writing `target/classes` while the other recompiles it), not just a performance
concern — confirmed by reading both scripts directly, not assumed.

**`playwright.sh` has no such conflict.** It never touches the Maven reactor — it only drives an
already-built, already-running `marketplace-app` Docker container via `docker cp`/`docker exec`
(see `playwright/CLAUDE.md`). It can safely run in parallel with either or both of the Maven-based
suites.

**Chosen shape:** `unit-tests.sh` → `integration-tests.sh` sequentially in one stream;
`playwright.sh` starts in parallel from the very beginning (backgrounded, own log file under
`scripts/run-all-tests/reports/`), `wait`-ed on at the end. Each suite's own flags/scenario args
are grouped behind `--unit "..."` / `--integration "..."` / `--playwright "..."` and forwarded
unchanged to the existing scripts — no new flag vocabulary, no duplicated argument parsing.

**Side benefit of the chosen order (observed, not a design requirement):** since `unit-tests.sh`
already compiles the shared starter modules via its own `-am` reactor build moments earlier,
`integration-tests.sh`'s subsequent staleness-check/install step typically finds a warm compile
cache ("Nothing to compile") and completes faster than a cold invocation would — even though
`unit-tests.sh` never installs anything to `~/.m2` itself (`test` goal, not `install`), so the
install step still always actually runs, just faster.

**Explicitly out of scope:** true 3-way parallelism (unit-tests and integration-tests running
concurrently) would require isolating one of the two Maven-based suites into a separate git
worktree (or equivalent) so their `target/` directories never overlap. Not pursued — the
sequential-plus-parallel-Playwright shape already captures most of the available time savings
without new infrastructure. The individual scripts remain the default, single-suite entry points
for day-to-day iteration; `run-all-tests.sh` is an additional, opt-in grouping, not a replacement
(daily-iteration default stays the synchronous, one-at-a-time Monitor+tee pattern already
documented in `scripts/CLAUDE.md`).

## What's already implemented

- `scripts/run-all-tests.sh` — manual parse-loop grouping (`--unit`/`--integration`/`--playwright`,
  same style as `integration-tests/run.sh`/`playwright/run.sh`, no getopts). Runs unit→integration
  sequentially with `tee` to `scripts/run-all-tests/reports/unit-then-integration.log`; runs
  playwright backgrounded to `scripts/run-all-tests/reports/playwright.log`; prints a PASSED/FAILED
  summary combining both exit codes.
- `scripts/DECISIONS.md` ADR-004 — records the above reasoning.

## Remaining work

1. Run `bash scripts/run-all-tests.sh --unit "<fast test>" --integration "--sandbox smoke"
   --playwright "<fast spec>"` end-to-end at least once, confirm: both log files populate
   correctly, the summary line reflects real exit codes (test a deliberate failure in one suite to
   confirm it's actually detected, not just the happy path), and Playwright's background run
   doesn't get silently swallowed if it finishes before or after the sequential pair.
2. Commit once verified (per project rule: only on explicit "зроби коміт").

## Related

- [improvement-047](improvement-047-integration-tests-ci-safety.md) — `integration-tests/run.sh`
  build/CI safety, same script this orchestrator wraps.
- `integration-tests/DECISIONS.md` ADR-007 — the staleness-check/install logic this issue's "side
  benefit" section depends on understanding correctly.
