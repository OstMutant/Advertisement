# Architecture & Technical Decisions — scripts/ci

---

## ADR-001: ci-runner container via Docker-outside-of-Docker, not Docker-in-Docker
**Status:** Accepted

**Context:** This project needed a single, isolated, parameterized way to run
unit/integration/e2e/sonar stages without colliding with the persistent dev stack
(`marketplace-app`/`advertisement-db`/`advertisement-minio`/`pw-runner`), while staying migratable
to GitHub Actions later. Two mechanisms were considered for "one container, nothing leaks out":
- **DinD** (nested `dockerd` inside the CI-runner container, `--privileged`): rejected. GitHub
  Actions itself does not run jobs this way — its `services:` mechanism spins up *sibling*
  containers on the runner VM's own daemon, not a nested one — so building on DinD now would be
  throwaway work at GitHub-migration time. It also adds a second layer of network indirection on
  top of an already-fragile one: this sandbox's Testcontainers/Ryuk already need
  `TESTCONTAINERS_RYUK_DISABLED`/a fixed port to reach dynamically-published ports at all (see
  `integration-tests/CLAUDE.md`), and nesting another daemon inside that was assessed as more
  likely to make this worse, not better.
- **DooD** (host's `/var/run/docker.sock` mounted into the one CI-runner container): accepted.
  Matches how GitHub Actions' own `services:` model actually works, so the migration path stays
  straightforward (local `ci-*` env-var overrides → GitHub `services:`, named volume →
  `actions/cache`, no rewrite of stage logic). "Nothing leaks out" is
  satisfied in the sense that matters: sibling containers created during a run are named uniquely
  (`ci-*`) so they never collide with the dev stack, and are torn down via a `trap ... EXIT` in
  `scripts/ci/entrypoint.sh` regardless of success/failure — not that zero sibling containers ever
  transiently exist.

**Decision:** `scripts/ci.sh` (thin wrapper) → `scripts/ci/run.sh` (host-side entry point) builds
`scripts/ci/Dockerfile` from the current source tree and runs it with `/var/run/docker.sock`
mounted, `--network host` (so ports its own sibling containers publish are reachable at
`localhost:PORT` exactly like a non-containerized run — no bridge-network DNS indirection to design
around), and a `ci-m2-cache` named volume at `/root/.m2`. `scripts/ci/entrypoint.sh` runs inside
that container and, per requested stage, calls the **existing, unmodified-in-substance**
`scripts/unit-tests.sh` / `scripts/integration-tests.sh --sandbox` / (`scripts/deploy-and-run.sh` +
`playwright/run.sh`, both env-overridable — see below) / `scripts/sonar.sh` — no stage logic is
reimplemented, only wrapped. (Superseded on the unit/integration point by ADR-008 below: those two
scripts were later merged into one `build_and_test` stage calling `scripts/build-and-test.sh`, and
both standalone scripts were deleted — this ADR-001 passage describes the original wiring at the
time of this decision, not current behavior for that part.)

**e2e isolation reuses `deploy-and-run.sh`'s own env-var overrides, not a new compose file.** An earlier
draft of this design planned a dedicated `scripts/infra/docker-compose.ci.yml` for isolated
Postgres/MinIO. Rejected once it became clear `deploy-and-run.sh` doesn't use `docker-compose.*.yml` at
all (those exist only for IDE-only dev-mode infra) — it already does raw `docker run` for
everything. So instead, `NETWORK`/`DB_CONTAINER`/`MINIO_CONTAINER`/`APP_CONTAINER`/`APP_IMAGE`/
`DB_PORT`/`MINIO_PORT`/`MINIO_CONSOLE_PORT`/`APP_PORT`/`DB_VOLUME`/`MINIO_VOLUME` in
`scripts/deploy-and-run.sh`, and `APP_CONTAINER`/`PW_CONTAINER`/`DB_PORT`/`DB_USER`/`DB_NAME` in
`playwright/run.sh`, were all made overridable via the already-established `"${VAR:-default}"`
idiom (`playwright/run.sh`'s own `APP_URL` already used this). `entrypoint.sh` calls both scripts
with a `ci-*`-prefixed set of overrides (distinct network/container names, host ports
15432/19000/19001/18081, distinct volume names) — zero duplicated e2e orchestration logic, and the
normal dev workflow (`bash scripts/deploy-and-run.sh`, no env vars set) is byte-for-byte unchanged, verified
directly (full `deploy-and-run.sh` + `playwright.sh e2e --ux` run, 35/48 passed, matching the pre-change
baseline exactly).

**`ci-pw-runner` is deliberately excluded from the post-run teardown.** Unlike `ci-db`/`ci-minio`/
`ci-app` (which hold run-specific state and must be torn down for isolation), the Playwright runner
container is stateless tooling — it only caches its own `npm install`. Removing it every run would
defeat the "cached artifacts, not re-fetched" goal for no isolation benefit, so it persists across
`ci/run.sh` runs exactly like the dev `pw-runner` already does.

**Consequences / verified directly:**
- `--unit`, `--integration --sandbox`, and `--e2e` were each run standalone through
  `scripts/ci.sh` and passed (unit: 22/22 across all modules; integration: 83/83 Testcontainers
  tests including the highest-risk DooD-inside-DooD path; e2e: 35/48 first pass, 48/48 with
  `--full`, matching the non-containerized baseline exactly).
- Surfaced three real, pre-existing bugs neither smoke test had ever exercised before: (1) an
  Enforcer `dependencyConvergence` conflict in `integration-tests` between `testcontainers`'s
  transitive `commons-io:2.20.0` and `liquibase-core`'s transitive `commons-io:2.21.0` — nothing
  had run `mvn -pl integration-tests test` since the Enforcer `dependencyConvergence` rule was
  introduced, since `Dockerfile` never builds that module. Fixed by bumping the root
  `dependencyManagement`-pinned `liquibase-core` to 5.0.3 (pulls `commons-io:2.22.0`, newer than
  5.0.2's 2.21.0) and pinning `commons-io` to match; upgrading `liquibase-core` alone does not
  eliminate the need for the pin — `testcontainers` is already on its latest release (2.0.5) and
  will never converge with any liquibase-core release on its own. (2) `playwright/run.sh` computed
  `APP_URL` locally but never forwarded it into the `docker exec pw-runner ...` environment
  (`PW_ENV` carried only `PLAYWRIGHT_BROWSERS_PATH`/`PW_SCREENSHOTS`/`PW_FULL`) — invisible in
  normal dev use because the script's own default (`http://localhost:8081`) already matched
  `playwright.config.js`'s hardcoded fallback, but broke outright the first time a non-default
  `APP_URL` (the isolated stack's `18081`) was actually needed. Fixed by adding `APP_URL=$APP_URL`
  to `PW_ENV`. (3) `scripts/sonar/run.sh` called the system `mvn` binary directly, which doesn't
  exist inside the minimal `ci-runner` image (only the Maven wrapper `./mvnw` is copied in) —
  invisible on a normal dev machine where `mvn` is installed system-wide. Fixed by switching to
  `/app/mvnw`, matching every other script in the project.
- Surfaced a real, unrelated latent bug in `scripts/deploy-and-run.sh` during this verification: its
  post-build `docker container prune -f` / `docker volume prune -f` act host-wide, not scoped to
  this app's own resources. Running `deploy-and-run.sh` from inside `scripts/ci/run.sh` (via the mounted
  `docker.sock`) while the dev `marketplace-app`/`pw-runner`/`sonarqube` containers happened to be
  stopped (for an unrelated diagnostic) deleted all three outright — confirmed directly, not
  theoretical. Underlying data survived (Postgres/MinIO/SonarQube state all live in named volumes
  that were never touched, since `advertisement-db`/`advertisement-minio` were never stopped), but
  the containers themselves had to be manually recreated. Fixed by making both prune calls opt-in
  via a new `deploy-and-run.sh --prune-all` flag (explicit host-wide-effect warning printed before running),
  no longer part of the automatic post-build step. Explicit, scoped cleanup of this app's own
  containers/volumes already exists via `deploy-and-run.sh --reset`; `--prune-all` is for a deliberate,
  whole-machine deep clean when that's actually wanted.

---

## ADR-002: Background by default, live progress via `docker cp`-polled `progress.txt`
**Status:** Accepted

**Context:** The initial implementation blocked the caller until the whole run finished (10+
minutes for a full `--all --sonar` run). The user wanted the opposite default: kick off the run and
get the shell back immediately, then check in on progress whenever convenient — start it, walk
away, come back and see what's done, what's running, what failed, and decide whether to wait, stop,
or restart.

**Decision:** `scripts/ci/run.sh` builds the image in the foreground (fast, fails loudly if
something's wrong with the Dockerfile itself), then detaches: `docker run -d` starts the container,
a background subshell (`&` + `disown`) polls `docker inspect .State.Running` and copies out the
whole report tree (not just `progress.txt` — see the report-tree note below) via `docker cp` every
5 seconds — bind mounts don't work from inside this sandbox (same constraint `playwright/CLAUDE.md`
documents), so periodic `docker cp` is the only way to surface live progress on the host while the
container is still running. The script itself returns control (prints the background PID, the
`progress.txt` path, and the full `run.log` path) within seconds, not minutes. `--foreground` opts
back into the old blocking, streaming behavior — this is what an AI-driven Monitor+tee-style
verification invocation should use instead, since it needs a definite, single point where the run
has finished.

`scripts/ci/entrypoint.sh` (inside the container) rewrites the whole `progress.txt` file on every
stage transition (registered as `PENDING` up front for every requested stage, flipped to
`RUNNING` when it starts, `DONE`/`FAILED` with elapsed seconds when it ends) — a full rewrite each
time, not an append, so the file is always a complete, current snapshot rather than a growing log
that needs to be read from the bottom. A background heartbeat (a forked subshell, started in
`start_stage()`, killed in `end_stage()`) also re-writes it every 5s *during* a running stage —
without this, `Elapsed`/`Last updated`/the running stage's `so far` counter would stay frozen at
whatever they were the instant the stage started, for however long that stage takes (confirmed
directly: e2e alone runs ~10 minutes, and without the heartbeat the file looked stale/dead for that
whole time even though the host-side copy loop was faithfully re-copying it every 5s — it just had
nothing new to copy).

**Incremental report collection, not just a final copy.** The host-side polling loop originally
copied only `progress.txt` out on each cycle, deferring the actual report subdirectories
(`unit-tests/`, `integration-tests/`, ...) to a single `docker cp` after the container exits — so a
completed stage's reports weren't visible on the host until the *entire* run finished, even though
that stage's own results had been ready for many minutes (confirmed directly, then corrected):
`entrypoint.sh` already writes each stage's report subdirectory into the container's
`/app/ci-reports/<id>/` the moment that stage ends, so the polling loop now `docker cp`s the whole
tree every cycle instead of just the one file — negligible extra cost (the report tree is at most a
few MB of text/XML at any point mid-run), and completed stages' reports now appear on the host
within one 5s cycle of that stage finishing, not after the whole run.

**Consequences:**
- The most-extensive default (see ADR-003) makes this default background behavior matter in
  practice — a full `--all --sonar` run is genuinely long enough (~15-20 minutes) that blocking by
  default would be the wrong choice for how this tool is actually used.
- Container names are now unique per run (`ci-runner-<timestamp>`, not a fixed `ci-runner`), so two
  runs can be started back-to-back (or even overlapping) without a name collision — a real
  consideration once backgrounding makes concurrent invocation easy to reach for by accident.

---

## ADR-003: Default stage selection is the most extensive run, not the narrowest
**Status:** Accepted

**Context:** The original design required at least one explicit stage flag (`--unit`, `--e2e`,
etc.) and errored with no flags at all ("No stage selected"). In practice, the most useful default
for "just run the CI checks" is the most thorough one, not an error — mirroring how
`playwright.sh e2e --full --ux` (not the bare, narrower default) is what actually gets reached for
when someone wants a real answer to "did I break anything."

**Decision:** No stage flag at all now means `--unit --integration --e2e --sonar`, and the e2e
stage's own Playwright invocation defaults to `"e2e --full --ux"` (overridable via
`--playwright-args "..."`) rather than Playwright's own bare default. Explicit stage flags (e.g.
`bash scripts/ci.sh --unit`) still scope the run to only what's asked, unchanged.

**Consequences:** A bare `bash scripts/ci.sh` invocation is now a genuinely long run (~15-20
minutes including the full Playwright suite and a SonarQube pass) — acceptable specifically because
of ADR-002's background-by-default behavior; this default would be a poor choice if the tool still
blocked the caller.

---

## ADR-004: Report retention — keep the last N runs, prune the rest
**Status:** Accepted

**Context:** Each run creates a full `scripts/ci/reports/<timestamp>/` tree (Playwright HTML
report with screenshots, surefire XML/txt, Sonar HTML) — left unmanaged, this grows unbounded
across repeated runs, especially now that runs are cheap to kick off (backgrounded, most-extensive
default).

**Decision:** `scripts/ci/run.sh --keep-reports N` (default `3`) prunes `scripts/ci/reports/*`
down to the N most recent directories (sorted by the timestamp-based directory name) after each
run completes. `--keep-reports 0` disables pruning entirely.

---

## ADR-005: Concurrency with normal dev work — genuine isolation, one real resource caveat
**Status:** Accepted

**Context:** With background-by-default (ADR-002) making it cheap to kick off a run and keep
working, the natural next question is whether it's actually safe to develop and run the app/tests
manually while a `scripts/ci.sh` run is in progress — not just "probably fine" but verified.

**Findings, verified directly:**
- The `ci-runner` image is built from a **frozen snapshot** of the source tree (`COPY . .` at
  `docker build` time, which happens once at the start of `scripts/ci/run.sh`). Editing files on
  the host after a run has started has zero effect on that run — `unit`/`integration` stages
  execute `./mvnw` against the container's own copied filesystem, not a live bind mount, so there
  is nothing to race with concurrent host-side edits or a concurrent local `mvn`/IDE build.
- `ci-m2-cache` (Maven dependency cache) is a Docker-managed named volume mounted only inside the
  container — a completely different filesystem location from the host's own `~/.m2`. No shared
  state between container-side and host-side Maven activity.
- The e2e stage's isolated stack (see ADR-001) never touches the persistent dev stack's container
  names, ports, network, or database.
- **The one real limit is CPU/RAM contention, not data isolation.** Running the e2e stage
  concurrently with a full dev stack (`marketplace-app`+`advertisement-db`+`advertisement-minio`+
  `pw-runner`) *and* a running SonarQube container caused genuine Playwright timeout flakiness on
  this project's own sandbox (6.7 GB RAM total) — 5 tests failed with `Timed out ... waiting for`/
  `locator.click: Timeout ... exceeded` errors that disappeared entirely on an immediate re-run
  after stopping the unrelated dev containers. Confirmed directly, not theoretical. Likely much
  less of a problem on a normal, less constrained dev machine, but a genuine resource limit, not
  something to silently paper over.
- **Two `--e2e` runs at once collide with each other.** Unlike the `ci-runner-<timestamp>`
  container itself (unique per run, see ADR-002), the isolated e2e stack's own container/network
  names (`ci-advertisement-db`, `ci-advertisement-minio`, `ci-marketplace-app`, `ci-advertisement`
  network) are fixed, not templated with the run's timestamp — a second concurrent `--e2e` run
  would fail trying to create containers/a network that already exist.

**Decision:** Document this as-is rather than engineering it away. Templating the e2e stack's
container/network names with `$REPORT_ID` (mirroring `ci-runner-<timestamp>`) would fix the
concurrent-e2e-runs limit, but wasn't done — no real use case surfaced for running two e2e stages
at once (as opposed to one e2e stage alongside normal dev work, which already works today), and it
would need a second `--playwright-args`-style mechanism to also stop `deploy.sh --restart-infra`,
`--reset`, etc. from targeting the wrong instance by accident.

**Consequences:** Safe defaults for the common case (one CI run + normal dev work), with the two
real limits (resource contention under load, no double e2e) called out explicitly in `README.md`
rather than left for someone to discover the hard way.

## ADR-006: `e2e`/`sonar` stages now write their own `run.log` — closes a real "failed with no
retrievable reason" gap

**Status:** Accepted

**Context:** Confirmed directly (a real `bash scripts/ci.sh` run, not theoretical): when the `e2e`
and `sonar` stages fail, `entrypoint.sh` only ever copied the stage's *final* artifact
(`playwright/pw-report/.` → `$REPORT_DIR/playwright/`, `scripts/sonar/report/.` →
`$REPORT_DIR/sonar/`) — never the stdout of `deploy-and-run.sh`/`playwright/run.sh`/`scripts/sonar.sh`
while they ran. Once the ephemeral `ci-runner-<timestamp>` container is `docker rm -f`'d after the
run (see ADR-002), that stdout is gone permanently — a failed `e2e`/`sonar` stage surfaces only
`FAILED` in `progress.txt`, with zero way to find out *why* after the fact. This is unlike `unit`/
`integration`, which already had real logs because the scripts they call
(`unit-tests.sh`/`integration-tests.sh`) already `tee` their own output to their own
`reports/run.log` internally — `deploy-and-run.sh` and `scripts/sonar/run.sh` also `tee` to a log file
(`/tmp/deploy.log`, `/tmp/sonar.log`), but only inside `/tmp` on whichever container runs them,
never copied into the shared report tree; `playwright/run.sh` doesn't self-log its own stdout at
all (the `/tmp/pw-live.log` reference at its tail is a best-effort `docker cp` from the *pw-runner*
container for a file nothing actually writes there — a dead reference, not a working log path).

**Decision:** Wrapped each of the `e2e` and `sonar` stage blocks in `entrypoint.sh` in a `{ ... }
2>&1 | tee "$REPORT_DIR/<stage>/run.log"` — the same manual-tee convention used everywhere else in
this repo (`deploy-and-run.sh`, `sonar/run.sh`, interactive Monitor+tee invocations), just applied at the
one place (`entrypoint.sh`) that previously had none. Exit code is captured via a temp
`.exit_code` file written *inside* the piped block and read back after, not via `$?` directly after
the pipe — `$?` after a pipe is `tee`'s own exit status (always 0), the exact "tee'd exit-code bug"
`scripts/sonar/DECISIONS.md` ADR-001 already documents for a different script; this avoids
reintroducing that same bug here.

**Consequences:**
- `scripts/ci/reports/<timestamp>/playwright/run.log` and `.../sonar/run.log` now exist after every
  run, success or failure, and get synced to the host live by the same 5s `docker cp` poll loop
  that already surfaces `unit-tests/run.log`/`integration-tests/run.log` — a failed `e2e`/`sonar`
  stage is now debuggable from the report directory alone, no need to re-run anything or attach to
  a container that's already been removed.
- `run.log` at the top level of a report run (`scripts/ci/reports/<timestamp>/run.log`, the outer
  `run.sh`'s own stdout) still does **not** contain this detail — it only ever held container
  orchestration messages (build output, `docker run`/`docker wait` bookkeeping), not stage stdout,
  for any stage. The per-stage `run.log` files (inside `unit-tests/`, `integration-tests/`,
  `playwright/`, `sonar/`) are the only ones with real command output; `scripts/CLAUDE.md`'s
  "Local CI Runner" section description of the outer `run.log` as "the full raw output" was already
  imprecise before this change — corrected in the same pass.

## ADR-007: `teardown_e2e_stack()` now also removes `CI_DB_VOLUME`/`CI_MINIO_VOLUME`

**Status:** Accepted

**Context:** ADR-006's new `playwright/run.log` capture surfaced the *actual* cause of a
recurring `e2e` failure that had no visible reason before: every `adminEn signs up` /
`findByFilter` call against `user_information` threw `BadSqlGrammarException` → `PSQLException:
column u.deleted_at does not exist`. Confirmed directly, not guessed: `\d user_information`
against the isolated CI Postgres showed no `deleted_at`/`deleted_by` columns at all, while the
persistent dev Postgres (`advertisement-db`) had both. Root cause, traced via `git log -p` on
`01-user-schema.xml`: commit `3063048d` (adding soft-delete to users) edited the
**already-applied** changeset `01-user-schema` in place to add `deleted_at`/`deleted_by`, instead
of adding a new changeset, and added `<validCheckSum>ANY</validCheckSum>` to silence the resulting
checksum-mismatch error. Liquibase never re-runs a changeset it has already recorded as executed —
`<validCheckSum>ANY</validCheckSum>` only suppresses the *warning* about the mismatch, it does not
retroactively apply the new columns. `teardown_e2e_stack()` only ever removed the `ci-*`
containers/network, never `CI_DB_VOLUME`/`CI_MINIO_VOLUME` — so the isolated e2e Postgres volume,
first created by some CI run before commit `3063048d`, has been silently reused (never
recreated) by every `e2e` run since, permanently stuck on the schema from before that commit.

**Decision:** `teardown_e2e_stack()` now also runs `docker volume rm "$CI_DB_VOLUME"
"$CI_MINIO_VOLUME"` after removing the containers/network, so every `e2e` stage starts from a
genuinely empty Postgres/MinIO and Liquibase applies the current, complete `01-user-schema`
changeset in one shot. Scoped to this project's non-prod call: the underlying Liquibase
anti-pattern (editing an applied changeset instead of appending a new one) is not fixed here — any
*real*, long-lived database (a developer's persistent local volume, or a future prod database)
that ran `01-user-schema` before commit `3063048d` would still be permanently missing these
columns, since Liquibase itself never revisits a recorded changeset. Left as-is because this
project has no production deployment yet and the persistent dev stack already has the columns
(added by some means before this was caught) — revisit with a proper forward-only, idempotent
follow-up changeset if/when a real deployed database needs to be protected against this class of
gap.

**Consequences:**
- `e2e` CI runs are no longer permanently stuck replaying a stale schema — the very next
  `bash scripts/ci.sh --e2e` after this fix starts clean.
- `CI_DB_VOLUME`/`CI_MINIO_VOLUME` no longer persist any data between CI runs at all (by design —
  this isolated stack was always meant to be ephemeral per ADR-001; the volumes existing at all
  was so Postgres/MinIO could restart cleanly *within* one run, e.g. across `deploy.sh`'s own
  container-recreate step, not so state would survive *across* separate `ci.sh` invocations).
- `01-user-schema.xml`'s `<validCheckSum>ANY</validCheckSum>` remains in place — it was never the
  actual bug (it correctly lets Liquibase accept the edited changeset's new checksum on databases
  that already ran the old version), and removing it would only reintroduce a checksum-mismatch
  failure on those same already-migrated databases without fixing the missing-columns gap either
  way.

---

## ADR-008: `unit`/`integration` stages merged into one `build_and_test` stage

**Status:** Accepted

**Context:** `entrypoint.sh` ran `unit`/`integration` as two separate, sequential stages, each
calling `scripts/unit-tests.sh`/`scripts/integration-tests.sh` directly. `scripts/build-and-test.sh`
(improvement-152) now installs the whole reactor once and runs unit and integration tests as
parallel jobs internally, reaching feature parity with both standalone scripts (module/test
selection, host-copied reports, PASSED/FAILED summaries, `--sandbox`). Keeping `entrypoint.sh` on
the two standalone scripts would mean CI duplicates a full reactor install/compile cycle that
`build-and-test.sh` already does once, and blocks retiring the standalone scripts.

**Decision:** `STAGE_UNIT`/`STAGE_INTEGRATION` (still two separate env vars, still independently
toggleable) now drive a single `build_and_test` stage that calls `bash scripts/build-and-test.sh`
with `--unit`/`--no-unit` and `--integration`/`--no-integration` set accordingly, forwarding
`--sandbox` only when integration is requested. Reports move from two sources
(`scripts/unit-tests/reports/`, `integration-tests/reports/`) to one
(`scripts/build-and-test/reports/` → `$REPORT_DIR/build-and-test/`).

**Consequences:**
- `progress.txt` shows one `build_and_test` line instead of separate `unit`/`integration` lines
  when either or both are requested — matches this repo's own CI-vocabulary grounding (a stage is
  a sequential step; what were two sequential stages are now one stage with two parallel jobs
  inside it, the same shape `build-and-test.sh` already uses standalone).
- Requesting only one of `--unit`/`--integration` still works exactly as before (the other side
  gets `--no-unit`/`--no-integration`) — no change in what a caller can select, only in how the
  single resulting Maven invocation is organized internally.
- Unblocked retiring `scripts/unit-tests.sh`/`.bat`/`scripts/unit-tests/` and
  `scripts/integration-tests.sh`/`.bat` — this was the last real code dependency on either script
  outside `run-all-tests.sh` (already migrated). **Done:** both scripts and the `scripts/unit-tests/`
  directory have since been deleted entirely.
