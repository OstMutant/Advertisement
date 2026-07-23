# improvement-059: Local isolated, parameterized CI runner — one container, cached artifacts, configurable report output — ✅ DONE (2026-07-16)

**Type:** improvement — process/tooling. Split out of improvement-028 (GitHub Actions pipeline)
after deciding to build and prove out the pipeline logic locally first, migrate to GitHub later.
**Module:** `scripts/ci/` (`run.sh`, `entrypoint.sh`, `Dockerfile`, own `DECISIONS.md`/`README.md`),
`scripts/ci.sh` thin wrapper, no application code.
**Priority:** medium — same underlying motivation as improvement-028 (nothing currently stops a
broken build/regression from landing uncaught), but scoped to local-only execution first.
**When:** independent, no blockers. `improvement-028` (actual GitHub Actions workflow) is now
explicitly sequenced *after* this — see Related.

## Problem

No unified, isolated way to run "everything that should gate a change" (compile, unit tests,
Testcontainers integration tests, Playwright e2e, optionally SonarQube) in one command against a
clean, reproducible environment. Today's scripts (`unit-tests.sh`, `integration-tests.sh`,
`playwright.sh`) each work standalone but assume the shared, persistent dev infra
(`advertisement-db`, `advertisement-minio` — fixed container names/ports, persistent volumes) is
already running — fine for day-to-day dev iteration, wrong for a "did this change actually break
anything" gate, which wants a clean-state run, not one potentially carrying dev-session leftovers.

Explicitly **not** GitHub Actions (deferred to improvement-028) — this is local-first, both because
GitHub execution needs `gh` CLI setup + explicit push authorization not yet in place, and because
proving the pipeline logic locally first (where iteration is fast and failures are easy to
inspect) is lower-risk than debugging it for the first time on GitHub's own infrastructure.

## Design (as implemented — see DECISIONS.md ADR-005 for the full rationale, including a rejected
DinD alternative)

**One CI-runner container, Docker-outside-of-Docker.** `scripts/ci/Dockerfile` (JDK 25 + Maven +
`docker` CLI, no Node/Playwright — the e2e stage delegates entirely to the existing
`playwright/run.sh`, which manages its own `pw-runner` sibling container). `scripts/ci.sh`
builds it fresh from the current source tree and runs it once with `/var/run/docker.sock` mounted
and `--network host`, so its own sibling containers' published ports are reachable at
`localhost:PORT` exactly like a non-containerized run.

**Isolated e2e stack via env-var overrides on the existing scripts, not a new compose file.**
`scripts/deploy.sh` and `playwright/run.sh` were made parameterizable
(`NETWORK`/`DB_CONTAINER`/`MINIO_CONTAINER`/`APP_CONTAINER`/`APP_IMAGE`/`DB_PORT`/`MINIO_PORT`/
`MINIO_CONSOLE_PORT`/`APP_PORT`/`DB_VOLUME`/`MINIO_VOLUME` and
`APP_CONTAINER`/`PW_CONTAINER`/`DB_PORT`/`DB_USER`/`DB_NAME` respectively), defaulting to the exact
values already in use so normal dev usage is unchanged. `scripts/ci/entrypoint.sh` calls
both with a `ci-*`-prefixed override set (distinct network/container/volume names, ports
15432/19000/19001/18081) — zero e2e orchestration logic duplicated.

**Cached artifacts via one named volume.** `ci-m2-cache` → `/root/.m2`, mounted into the CI-runner
container on every run. (A `ci-vaadin-cache`/`ci-playwright-cache` pair was in the original design
but turned out unnecessary: the app image build already gets Maven/Vaadin caching for free from the
host's own BuildKit cache via `deploy.sh`'s `docker build`, and Playwright browser caching already
lives inside the long-lived `pw-runner`/`ci-pw-runner` sibling container, not something this
orchestrator needs to manage itself.)

**Consolidated, configurable report output.** All stage reports collected into
`scripts/ci/reports/<timestamp>/` inside the container, then copied out via `docker cp` (not a bind
mount — same constraint `playwright/CLAUDE.md` already documents for this sandbox). Output
destination configurable via `--report-dir`.

**Guaranteed cleanup, not best-effort.** `entrypoint.sh` registers `trap teardown_e2e_stack EXIT`
unconditionally, so `ci-db`/`ci-app`/`ci-minio`/the `ci-advertisement` network are removed on any
exit path (success, failure, mid-stage crash) unless `--keep-infra` was passed. `ci-pw-runner` is
deliberately excluded (stateless tooling, kept warm across runs like the dev `pw-runner` already
is).

**Parameterized stage selection (implemented as designed):**
```bash
bash scripts/ci.sh --unit --integration --e2e     # chosen stages only
bash scripts/ci.sh --all                          # unit + integration + e2e
bash scripts/ci.sh --all --sonar                   # + SonarQube analysis
bash scripts/ci.sh --all --report-dir /some/path   # configurable report destination
bash scripts/ci.sh --all --keep-infra               # don't tear down the isolated e2e stack
bash scripts/ci.sh --integration --sandbox          # this claude-dev sandbox's
                                                             # Testcontainers workaround
```

## Why this migrates cleanly to improvement-028 later

The actual test/build logic stays in the existing standalone scripts. A future GitHub Actions
workflow would call the same scripts as job steps, substituting GitHub's native `services:`
mechanism (sibling service containers on the runner's own daemon — the same DooD shape this design
already uses, unlike the rejected DinD alternative) for the `ci-*` env-var overrides here, and
GitHub's own dependency caching (`actions/cache`) for the `ci-m2-cache` named volume. No test logic
gets rewritten for the migration — only the outer orchestration layer changes.

## Resolution (2026-07-16)

Implemented and verified directly, each stage run standalone through `scripts/ci.sh`:
- `--unit`: 22/22 tests passed across all modules.
- `--integration --sandbox`: 83/83 Testcontainers tests passed, including the highest-risk
  DooD-inside-DooD path (Testcontainers spinning up its own sibling Postgres from *inside* the
  CI-runner container).
- `--e2e`: 35/48 passed, byte-for-byte matching the non-containerized baseline (13 pre-existing
  skips, same as always without `--full`).
- `--sonar` wraps `scripts/sonar.sh` unchanged (already manages its own container lifecycle) — not
  run standalone as part of this verification pass, lower risk since no new parameterization was
  added for it.

Surfaced and fixed three real, pre-existing issues along the way (full detail in
`scripts/DECISIONS.md` ADR-005):
1. Enforcer `dependencyConvergence` conflict in `integration-tests` (`commons-io` via
   `testcontainers` vs. via `liquibase-core`) — nothing had run `mvn -pl integration-tests test`
   since the Enforcer rule (improvement-031) was introduced. Fixed by bumping the pinned
   `liquibase-core` to 5.0.3 and `commons-io` to 2.22.0 to match (checked: `testcontainers` is
   already on its latest release, so the pin is unavoidable regardless of liquibase-core's
   version).
2. `playwright/run.sh` never forwarded `APP_URL` into the `pw-runner` container's actual test-run
   environment — invisible in normal dev use since its default already matched
   `playwright.config.js`'s own hardcoded fallback. Fixed by adding it to `PW_ENV`.
3. `scripts/deploy.sh`'s unconditional `docker container prune -f`/`docker volume prune -f` acted
   host-wide, not scoped to this app — running `deploy.sh` from inside the CI-runner (via the
   mounted socket) while the dev `marketplace-app`/`pw-runner`/`sonarqube` containers happened to
   be stopped for an unrelated diagnostic deleted all three outright (data survived, containers
   didn't — confirmed directly, not theoretical). Fixed by moving both behind a new, opt-in
   `deploy.sh --prune-all` flag with an explicit host-wide-effect warning, rather than either
   leaving the bug in place or silently dropping the deep-clean capability entirely.

Two design points changed from the original plan, both simplifications: no new
`scripts/infra/docker-compose.ci.yml` (unnecessary — `deploy.sh` never used compose files to begin
with, so env-var overrides on the existing script were the natural fit), and no
`ci-vaadin-cache`/`ci-playwright-cache` volumes (unnecessary — see Design section above). DinD was
considered and explicitly rejected in favor of DooD — see `scripts/ci/DECISIONS.md` ADR-001.

**Follow-up (2026-07-16, same day):** restructured from a flat `scripts/ci-local.sh` +
`scripts/ci-runner/entrypoint.sh` + root `Dockerfile.ci-runner` into a nested `scripts/ci/` module
(own `DECISIONS.md`/`README.md`, matching `scripts/sonar/`'s precedent — see `scripts/ci/DECISIONS.md`
ADR-001), plus four UX changes based on direct feedback: (1) runs in the background by default
(`docker run -d` + a host-side polling loop that `docker cp`s out a small `progress.txt` every 5s,
`--foreground` opts back into the old blocking behavior — ADR-002), (2) no stage flags now means the
most extensive run (`--unit --integration --e2e --sonar`, Playwright forwarded `"e2e --full --ux"`
by default — ADR-003), (3) report directories pruned to the last 3 by default
(`--keep-reports N` — ADR-004), (4) a fourth real bug found in the process: `scripts/sonar/run.sh`
called the system `mvn` binary directly, which doesn't exist inside the minimal `ci-runner` image
(only `./mvnw` is copied in) — fixed by switching to `/app/mvnw`.

## Related

- [improvement-028](improvement-028-minimal-ci-pipeline.md) — the actual GitHub Actions workflow,
  now explicitly sequenced after this issue.
- `scripts/ci/DECISIONS.md` — full design rationale (DooD vs. DinD, background execution, default
  stage selection, report retention) and all four bugs found.
- `scripts/ci/README.md` — usage reference (flags, report layout, isolation model).
- `scripts/CLAUDE.md` — the existing standalone scripts this orchestrator wraps, not replaces; also
  documents `scripts/ci.sh` itself under "Local CI Runner".
- `playwright/CLAUDE.md` — the `docker cp`-not-bind-mount constraint this design's report-output
  mechanism follows.
