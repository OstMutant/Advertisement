# improvement-047: Keep `integration-tests` out of a plain `mvn install`/`mvn test`, add a Docker precheck, close a couple of doc/CI hygiene gaps

**Type:** improvement — build/CI safety and developer experience. Originated from an external
review of PR #20 (the `integration-tests` module); this issue is a corrected, de-duplicated version
of that review after verifying every claim against current code — see "What the original review
got wrong" below before implementing anything.
**Module:** root `pom.xml`, `integration-tests/run.sh`, `integration-tests/CLAUDE.md`/`README.md`,
possibly `.github/workflows/` (see item 6, explicitly scoped out of this issue's first pass).
**Priority:** medium — nothing here is broken today for the actual sanctioned workflow (always via
`scripts/integration-tests.sh`, always through `deploy.sh`'s `-DskipTests` build), but a plain
`mvn install`/`mvn test` run from repo root — a normal thing a new contributor or future CI would
do — currently silently requires Docker and can hang/fail with no explanation.
**When:** independent, no blockers.

## Problem

Verified directly against current code (2026-07-14): `integration-tests` is a normal `<module>` in
root `pom.xml`'s `<modules>` list, alongside every domain starter. `deploy.sh`/`deploy-dev.sh`
already build with `./mvnw install -DskipTests` (per `scripts/CLAUDE.md`), so the sanctioned deploy
path never hits this. But a plain `mvn install` or `mvn test` run from the repo root — without
`-DskipTests`, e.g. by a new contributor doing a sanity build, or a future CI pipeline that isn't
`deploy.sh` — will run Surefire on `integration-tests` too, which means:
- It requires a reachable Docker daemon (per `integration-tests/CLAUDE.md`), with no upfront check
  — if Docker isn't running, the failure surfaces deep inside Testcontainers' own connection
  probing, not as a clear "Docker required" message.
- On a real developer machine (not this session's sandbox), Testcontainers' own container-start
  cost still adds real, unexpected time to what should be a fast local build sanity-check.

## What the original review got wrong (corrected before filing this issue)

The source review (an external prompt reviewing PR #20) was accurate on the core problem but had
three issues, checked against current code before writing this issue:

1. **Missed that gating the module behind a Maven `<profile>` breaks the existing
   `integration-tests/run.sh`.** That script runs `./mvnw -pl integration-tests -am test [...]`.
   If `integration-tests` is removed from the base `<modules>` list, Maven's reactor won't discover
   it at all without the profile active — `-pl integration-tests` would fail outright. Any profile-
   based fix must update `run.sh` in the same change to pass `-Pintegration-tests` (or the
   equivalent `-Dintegration-tests=true` property activation), not just document the new command.
2. **A module-level profile is coarse — it hides Docker-free tests too.** `integration-tests`
   already contains tests with no Docker dependency at all (`AdvertisementSnapshotDtoTest`, and the
   `SharedEnvConfigTest` proposed by item 3 below) alongside Testcontainers-based ones. Gating the
   whole module behind a profile means a plain `mvn test` can no longer run even the cheap,
   Docker-free tests. The finer-grained alternative is a JUnit 5 `@Tag("testcontainers")` on
   Testcontainers-based test classes + Surefire `<excludedGroups>testcontainers</excludedGroups>`
   by default, overridable via `-Dgroups=testcontainers`. **Decision needed before implementing —
   see "Suggested fix" item 1.**
3. **Proposed `.env.example` doesn't fit this repo's actual `.env` setup.** Checked: `.env` is
   committed (not `.gitignore`d), contains exactly one line
   (`POSTGRES_IMAGE=postgres:15-alpine`), no secrets. `.env.example` earns its keep when the real
   `.env` is gitignored and holds secrets a new contributor must supply themselves — that's not
   this repo's model. A separate `.env.example` here would just duplicate `.env`'s content under a
   different name. The actual gap worth closing is a one-line doc note (in `integration-tests
   /CLAUDE.md` or root `CLAUDE.md`), not a new file — see item 4.
4. **Item "add tests for AccessEvaluator/TaxonRepository" fully duplicates
   [improvement-045](../completed/issues/improvement-045-critical-test-coverage-gaps.md), and is already partially
   done.** `TaxonRepository.findByIds()`/`findByTypeAndCode()` were fixed and covered by
   `TaxonRepositoryTest` on 2026-07-14 (commit `631644d2`) — the original review's premise (missing
   `deleted_at IS NULL` filter) is already resolved. `AccessEvaluator`'s unit test is
   `improvement-045` item 1, already tracked, not duplicated here. **Do not re-file these — track
   status in improvement-045 only.**

## Suggested fix

1. **Decide: Maven profile vs. JUnit `@Tag` for keeping Testcontainers tests out of a plain
   `mvn test`.** Recommendation: `@Tag("testcontainers")` + Surefire `excludedGroups` — it's
   per-test-class granularity (keeps `SharedEnvConfigTest`/`AdvertisementSnapshotDtoTest` runnable
   by a plain `mvn test`, only Testcontainers-based classes need the explicit opt-in flag), and
   doesn't touch the module's presence in the reactor (so `scripts/integration-tests.sh`'s existing
   `-pl integration-tests -am` keeps working unmodified — just add `-Dgroups=testcontainers` to run
   everything, or leave it default to run everything including the tag since the script's whole
   purpose is running these tests deliberately). If a full module-level profile is preferred instead
   (e.g. to also skip the module's own *compilation* on a Docker-less machine, which `@Tag` doesn't
   achieve since compiling has no Docker dependency), update `integration-tests/run.sh` in the same
   change to add `-Pintegration-tests`/`-Dintegration-tests=true` to its `mvn` invocation — do not
   ship the profile without updating the script that's currently the only sanctioned way to run
   these tests.
2. **Docker daemon precheck in `integration-tests/run.sh`.** Before invoking `mvn`, run `docker
   info` (or `docker version`); on failure, print a clear "Docker daemon not reachable — start
   Docker Desktop / dockerd and retry" message and exit 1, instead of letting Testcontainers'
   own (slower, less clear) connection probing surface the failure. Mirrors the same idea already
   used elsewhere in this project (`deploy.sh`'s health checks for DB/MinIO before proceeding).
3. **`SharedEnvConfigTest` unit test** (no Docker needed — pure file-walking logic, use JUnit
   `@TempDir`): cover (a) `.env` found when CWD is repo root, (b) `.env` found when CWD is a
   simulated module subdirectory (temp dir tree mimicking the real layout), (c) missing `.env`
   throws `IllegalStateException` with a clear message. Lives in `integration-tests/src/test/java
   /org/ost/integrationtests/SharedEnvConfigTest.java`. If tagging (item 1) is chosen, this class
   deliberately has **no** `@Tag("testcontainers")` — it should run under a plain `mvn test`.
4. **One-line doc addition**, not a new file: in `integration-tests/CLAUDE.md` (near the
   `SharedEnvConfig` description) or root `CLAUDE.md`'s `.env` mention, state explicitly that the
   repo-root `.env` is committed intentionally and must only ever hold non-secret dev-only values
   (currently just `POSTGRES_IMAGE`) — future additions to it (see
   [improvement-044](improvement-044-shared-env-config-consolidation.md)) must keep that
   invariant; production secrets belong in CI/deploy-time environment variables, never in this
   file.
5. **CI-environment guard for sandbox-only env vars, in `integration-tests/run.sh`.** If
   `GITHUB_ACTIONS` (or another recognized CI env var) is set and `--sandbox` was also passed (or
   `TESTCONTAINERS_RYUK_DISABLED`/`INTEGRATION_TESTS_POSTGRES_FIXED_PORT` are already present in
   the environment), fail fast with a message explaining these are sandbox-only workarounds that
   should never be needed on a real CI runner with normal Docker networking — prevents someone
   copy-pasting a `--sandbox` invocation into a future CI config without realizing why it's there.

## Explicitly out of scope for this issue

- **A GitHub Actions workflow for `integration-tests`.** Verified: this repo has no
  `.github/workflows/` at all today — introducing CI is a standalone decision (which provider,
  which triggers, whether it's wanted at all given deploys are currently manual via
  `scripts/deploy.sh`), not a drive-by addition bundled into a test-tagging cleanup. File a separate
  issue if/when CI is actually decided on.
- **AccessEvaluator / TaxonRepository / rate-limit test coverage.** Fully owned by
  [improvement-045](../completed/issues/improvement-045-critical-test-coverage-gaps.md) — do not duplicate tracking
  here.

## Related

- [improvement-027](improvement-027-unit-testcontainers-test-layer.md) — the `integration-tests`
  module this issue hardens.
- [improvement-045](../completed/issues/improvement-045-critical-test-coverage-gaps.md) — owns the actual new test
  content for AccessEvaluator/rate-limiting/etc.; this issue is build/CI plumbing only.
- [improvement-044](improvement-044-shared-env-config-consolidation.md) — the `.env` consolidation
  effort item 4's doc note should stay consistent with, if more values get added to `.env` later.
