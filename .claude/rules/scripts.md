---
paths: ["scripts/**"]
---

## Deployment

### Prod deploy (local dev-loop, no image build by default)
```bash
bash scripts/deploy-and-run.sh        # Linux / WSL
scripts\deploy-and-run.bat            # Windows
```
By default, no Docker image is built at all — the app container runs `java -jar` directly against
the shared `maven-cache` volume (already refreshed by an internal `scripts/build-and-test.sh` call,
Vaadin's production bundle included; `SPRING_PROFILES_ACTIVE=prod` at runtime sets
`vaadin.productionMode=true`), starts all infra + app on **port 8081** (8080 reserved for local
IntelliJ dev server). `--from-scratch` builds a real, separately tagged image instead, from the
full multi-stage root `Dockerfile` — needed when an actual portable/deployable image is genuinely
required, not just a running local container. With `--from-scratch`, dangling (untagged) Docker
images are pruned automatically after the build (`docker image prune -f` only — scoped by
definition to unreferenced images, so it can never touch another stack's resources).
`docker container prune -f`/`docker volume prune -f` are opt-in only, via `--prune-all` — both act
host-wide, not scoped to this app's own containers/volumes, so they will remove any other stopped
container / unused volume on the machine too (see `.claude/nav/adr-index.md` for the incident that
made this explicit instead of automatic).

Use `--reset` to wipe DB/MinIO volumes. Use `--restart-infra` to restart containers only. Use `--reset-only-db` to truncate app tables (`reset-clean.sql`) before starting the app, without touching volumes. Use `--no-cache` to force a rebuild ignoring the Docker layer cache (only meaningful with `--from-scratch`). Use `--prune-all` for a deliberate, whole-machine deep clean (see warning above).

**Streaming output requirement — BuildKit + buildx:**
Docker Engine must have BuildKit enabled AND the `buildx` CLI plugin must be installed at
`~/.docker/cli-plugins/docker-buildx` — without it, plain `docker build` fails outright on this
sandbox's Docker version whenever the Dockerfile uses `--mount=type=cache` (confirmed directly:
`ERROR: BuildKit is enabled but the buildx component is missing`, not just a silent legacy-builder
fallback). The `--progress=plain` flag in `deploy-and-run.sh` then enables line-by-line streaming once
BuildKit is active.

**`docker compose` CLI plugin** — needed by `scripts/deploy-and-run/reset.sh` (starting dev DB when no
container exists yet) and `scripts/sonar/run.sh` (starting the SonarQube stack). Not present by
default in this sandbox.

**Both plugins are installed automatically, not manually — `scripts/utils/ensure-docker-plugins.sh`.**
`deploy-and-run.sh` (`ensure_buildx`, before its build step), `scripts/deploy-and-run/reset.sh` and
`scripts/sonar/run.sh` (`ensure_docker_compose`, before their respective `docker compose` calls)
all source this shared script and call the relevant function; each function checks `docker buildx
version` / `docker compose version` first and only downloads+installs if missing, so it's a no-op
on a normal developer machine where these already ship with Docker Desktop. Manual install (e.g.
to pre-warm a fresh sandbox, or troubleshoot outside any script) is still possible by running the
file directly — it installs both when not sourced:
```bash
bash scripts/utils/ensure-docker-plugins.sh
```
Or individually, mirroring what each function does:
```bash
mkdir -p ~/.docker/cli-plugins
curl -Lo ~/.docker/cli-plugins/docker-buildx \
  https://github.com/docker/buildx/releases/download/v0.21.0/buildx-v0.21.0.linux-amd64
chmod +x ~/.docker/cli-plugins/docker-buildx
curl -Lo ~/.docker/cli-plugins/docker-compose \
  https://github.com/docker/compose/releases/download/v2.32.4/docker-compose-linux-x86_64
chmod +x ~/.docker/cli-plugins/docker-compose
```
Verify: `docker buildx version` / `docker compose version`.

**`--project-directory` is required whenever `-f` points outside the repo root.**
`scripts/deploy-and-run/docker-compose.db.yml`/`docker-compose.app.yml`/`docker-compose.minio.yml` live in
`scripts/deploy-and-run/`, not the repo root, but read `${POSTGRES_IMAGE}`/`${DB_NAME}`/`${DB_USER}`/
`${DB_PASSWORD}`/`${DB_PORT}`/`${S3_ACCESS_KEY}`/`${S3_SECRET_KEY}`/`${S3_BUCKET}`/`${S3_REGION}`/
`${S3_PORT}` from the repo-root `.env` — the single source of truth for these values, also read as
fallback defaults by `deploy-and-run.sh`/`scripts/deploy-and-run/reset.sh` and as `${VAR:default}` Spring
placeholders by `application-dev.yml` (see `.claude/nav/adr-index.md`). Compose's default project
directory — where it looks for `.env` — is the directory containing the first `-f` file, **not**
the invoking shell's working directory. Always pass `--project-directory .` (run from the repo
root) or `--project-directory "$ROOT"` (absolute path), e.g.:
```bash
docker compose --project-directory . -f scripts/deploy-and-run/docker-compose.db.yml up -d
```
Omitting it silently resolves `${POSTGRES_IMAGE}` to an empty string and fails with "service db
has neither an image nor a build context specified" — confirmed by direct testing, not assumption.
This is documented, version-independent Compose behavior — the same fix applies on any machine,
not just this sandbox.

**How to run deploy-and-run.sh:** per `.claude/rules.md`'s "Scripts" section — background
`bash scripts/deploy-and-run.sh [args] > /tmp/deploy.log 2>&1`, then attach `Monitor` with the
wait-then-tail wrapper against `/tmp/deploy.log`; stay quiet on routine progress, surface errors,
a stall well past normal build time, or `BUILD SUCCESS`/`Started Application`.

### Local run (Maven, no Docker image rebuild)
```bat
scripts\run-local.bat           REM dev profile — Vaadin dev mode, port 8080
scripts\run-local.bat --prod    REM production Vaadin build, prod profile, port 8080
```
Windows-only (native Maven + Java — no WSL). Requires DB and MinIO already running. Use when you need to compare local vs Docker behaviour.

---

## SonarQube Analysis

All config lives in `/app/scripts/sonar/`. SonarQube server runs in Docker on `localhost:9099`.

### Start server manually (if needed)
```bash
docker compose -f scripts/sonar/docker-compose.sonar.yml up -d
```

### Run analysis
```bash
bash scripts/sonar.sh              # Linux / WSL -- blocking: exits non-zero if the quality gate fails
scripts\sonar.bat                  # Windows -- same
bash scripts/sonar.sh --no-gate    # informational only, always exits 0
```

The script starts SonarQube automatically if not running, builds all modules via `scripts/build-and-test.sh` (no local Java needed), copies source files into the scanner container via `docker cp`, mounts the shared `maven-cache` volume directly into the scanner container for compiled classes, and runs `sonar-scanner-cli`. Results: `http://localhost:9099/dashboard?id=advertisement`. The scanner always waits for server-side report processing (`-Dsonar.qualitygate.wait=true`, unconditional — needed so the HTML-report step below doesn't query the issues API before the server finishes indexing); `--no-gate` only changes whether a failed gate makes the script itself exit non-zero, not whether it waits. See `scripts/sonar/DECISIONS.md` for the quality-gate-blocking history (a `tee`d exit-code bug meant the flag alone wouldn't have blocked anything).

**IMPORTANT:** Same Docker socket constraint as Playwright for host-path bind mounts (`-v /host/path:/container/path`) — never use those, the script uses `docker cp` for host-to-container file transfer instead. This does NOT apply to named-volume mounts (`-v maven-cache:/root/.m2`), which the scanner container itself now uses directly — those aren't host paths and don't hit the same bug.

---

## Unit / Integration Tests

Two ways to run these, covering the same tests either way:

### Via `build-and-test.sh` (preferred — no local Java needed, unit + integration in parallel)

```bash
bash scripts/build-and-test.sh --unit --integration      # both, in parallel
bash scripts/build-and-test.sh --unit --no-integration              # unit only
bash scripts/build-and-test.sh --no-unit --integration    # integration only
bash scripts/build-and-test.sh --unit-test AccessEvaluatorTest --no-integration  # one class
bash scripts/build-and-test.sh --no-unit --integration-test AdvertisementRepositoryTest
```

Builds the whole reactor into a container-isolated `~/.m2` first, then runs unit
(`query-lib`/`marketplace-app`/`marketplace-orchestrator`/`marketplace-rest-api`) and integration (`integration-tests`
module — Testcontainers-based repository tests, real Postgres) as parallel jobs inside that same
container. See `scripts/build-and-test/README.md` for the full flow and
`scripts/build-and-test/build.sh`'s own header for every flag. `TESTCONTAINERS_RYUK_DISABLED=true
INTEGRATION_TESTS_POSTGRES_FIXED_PORT=25432` is applied **by default** everywhere except
`GITHUB_ACTIONS` — confirmed needed on a real Docker Desktop/WSL2 developer machine too, not just
an AI sandbox, whenever `--integration` is used: this script's own build container mounts the
host's `docker.sock` so Testcontainers can run nested inside it, and that nesting is what triggers
the port-reachability/Ryuk-connectivity issue, independent of which machine hosts it. Pass
`--no-sandbox` to disable the workaround if your own setup doesn't need it.

Reports: `scripts/build-and-test/reports/surefire/<module>/` (Surefire pass/fail per test class)
and `scripts/build-and-test/reports/logs/` (the full raw console log for whichever phase ran —
`unit-tests.log`/`integration-tests.log`/`archunit-metrics.log` — persists the real failure detail
past this run's own terminal output/scrollback).

**How to run it:** per `.claude/rules.md`'s "Scripts" section — background
`bash scripts/build-and-test.sh --unit --integration > /tmp/build-and-test.log 2>&1`,
then attach `Monitor` with the wait-then-tail wrapper against that log; stay quiet on routine
progress, surface errors, a stall, or `PASSED|FAILED|BUILD SUCCESS|BUILD FAILURE`.

### Via direct Maven/module scripts (need a local Java install)

Plain unit tests, no Docker: `mvn -pl query-lib,marketplace-app,marketplace-orchestrator test`.

Integration tests (Testcontainers, real Postgres) — `integration-tests/run.sh` still exists as its
own standalone entry point, with capabilities `build-and-test.sh` deliberately doesn't replicate
(a targeted per-starter staleness check instead of always installing the whole reactor,
`--no-check` to bypass it entirely):

```bash
bash integration-tests/run.sh                          # every test
bash integration-tests/run.sh smoke                     # just PostgresContainerSmokeTest
bash integration-tests/run.sh AdvertisementRepositoryTest  # one class by name
bash integration-tests/run.sh --sandbox smoke            # + this sandbox's Docker workarounds
bash integration-tests/run.sh --no-check TaxonRepositoryTest  # skip the staleness check
```

Streams full Maven/Testcontainers output live via `tee`. After the run:
- `scripts/logs/integration-tests/run.log` — full streamed output
- `integration-tests/reports/surefire/` — one `.txt`/`.xml` pass/fail report per test class
  (copied from Maven's own `target/surefire-reports/`)

`run.sh` auto-detects whether `platform-commons`/`advertisement`/`user`/`taxon`/`audit`/`attachment`/
`provider-profile-spring-boot-starter` changed since their last `~/.m2` install (comparing each
module's newest `.java` file's mtime against its installed JAR) and only reinstalls those before
testing, instead of rebuilding all 9 non-`integration-tests` reactor modules every run (measured
~1:47-3:35 total when nothing needed
reinstalling vs. 3-7 min walking the full reactor, dominated by ~100s of "nothing to compile"
Maven overhead across those modules in this sandbox). No manual flag needed — confirmed the
detection correctly triggers a reinstall when a starter file actually changes, not just when
nothing changed. `--no-check` bypasses the detection entirely (test against whatever's in `~/.m2`
right now) — only for deliberately reproducing behavior against an older build. See
`integration-tests/CLAUDE.md` and `.claude/nav/adr-index.md` for the full rule.

Raw `mvn`, no reports folder: `mvn -pl integration-tests -am test` (or, in this sandbox only,
prefixed with `TESTCONTAINERS_RYUK_DISABLED=true INTEGRATION_TESTS_POSTGRES_FIXED_PORT=25432`).
`-am` also builds whichever starters `integration-tests` currently depends on (required — they
are not otherwise built by a scoped `-pl integration-tests` alone).

### Never run integration tests via `deploy-and-run.sh`

`deploy-and-run.sh` runs Maven with `-DskipTests` inside a `docker build` stage with no access to the
outer Docker socket — standard Docker-in-Docker isolation, no socket mount configured for the
`builder` stage. Testcontainers-based tests need a real reachable Docker daemon, which only exists
when `mvn test` is run directly, never inside this build path.

### Why `INTEGRATION_TESTS_POSTGRES_FIXED_PORT` / `TESTCONTAINERS_RYUK_DISABLED` are needed

Confirmed: Testcontainers can create a container, but the test JVM cannot always reach a
dynamically-assigned published port (only statically-published ones, e.g. `advertisement-db`'s
`5432`, are reliably reachable) — a Docker Desktop/WSL2 nested-container networking quirk, not a
code bug (same class of issue as the volume-mount limitation noted under Playwright below). Ryuk
(the container reaper) can likewise fail to connect back to the test JVM. Confirmed present both in
this AI sandbox and on a real Docker Desktop/WSL2 developer machine when `build-and-test.sh`'s own
build container mounts the host's `docker.sock` to run Testcontainers nested inside itself — the
nesting itself is what triggers it, not which machine hosts the container. `build-and-test.sh`
applies both variables by default for this reason (see above); pass `--no-sandbox` if a given
setup doesn't hit this. `integration-tests/run.sh`'s own direct (non-nested) invocation stays
opt-in via its own `--sandbox` flag — not yet confirmed needed outside this AI sandbox for that
simpler, single-level case.

---

## Running Playwright Tests

**How to run playwright.sh:** per `.claude/rules.md`'s "Scripts" section.
1. Kill stale processes: `docker exec pw-runner pkill -f "node.*playwright" 2>/dev/null; true`
2. Background `bash scripts/playwright.sh [scenario] > /tmp/playwright.log 2>&1`, then attach
   `Monitor` with the wait-then-tail wrapper against that log.
3. Stay quiet on routine per-test progress; surface a real error, a stall, or the final
   `passed`/`failed` summary line.

---

## Local CI Runner (isolated, parameterized, Dagu-backed)

Lives in `scripts/ci/` (own `DECISIONS.md`/`README.md`, matching `scripts/sonar/`'s nested-module
shape, not `playwright/`'s root-level one — this is a tool wrapping other scripts, not a separate
test-authoring ecosystem). One persistent `ci-runner` container (`scripts/ci/Dockerfile`), built
from the current source tree and run with the host's `/var/run/docker.sock` mounted
(Docker-outside-of-Docker) — it creates and tears down its own isolated `ci-*`-named sibling
containers, never touching the persistent dev stack. `ci-runner` runs Dagu
(https://github.com/dagucloud/dagu), a single-binary DAG engine with a built-in web UI, orchestrating
the stage sequence defined in `scripts/ci/dagu/ci.yaml` (`build` → `unit`/`integration`/`e2e`/
`sonar`/`archunit_metrics` in parallel → `pipeline_metrics` → `docs`) — each step calls the same
existing scripts (`build-and-test.sh`/`deploy-and-run.sh`+`playwright/run.sh`/`sonar.sh`) directly,
no stage logic reimplemented. `unit`/`integration`/`archunit_metrics` pass `--skip-vaadin` to
`build-and-test.sh` (skips the Vaadin frontend bundle none of them need — see
`.claude/nav/adr-index.md`). See `.claude/nav/adr-index.md` for the DooD (Docker-outside-of-Docker)
design, the Dagu migration, and the pipeline-metrics/ArchUnit-export follow-up.

```bash
bash scripts/ci.sh                                        # build the image, start the persistent
                                                            # container, trigger the most extensive
                                                            # run (unit+integration+e2e+sonar+
                                                            # archunit_metrics+docs)
bash scripts/ci.sh --unit --integration --e2e              # chosen stages only
bash scripts/ci.sh --all --sonar                            # everything, explicit
bash scripts/ci.sh --no-docs                                 # skip the doc-freshness stage
bash scripts/ci.sh --playwright-args "e2e --ux"                # override the e2e stage's
                                                                 # Playwright args
bash scripts/ci.sh --no-keep-e2e-infra                            # tear down the isolated e2e
                                                                   # stack after (on by default,
                                                                   # left up for debugging)
bash scripts/ci.sh --reset-e2e-db                                  # full --reset (volume wipe)
                                                                    # before e2e's deploy instead
                                                                    # of the default --reset-only-db
                                                                    # -- only needed when the DB
                                                                    # schema itself changed
bash scripts/ci.sh --foreground                                     # block and stream this run's
                                                                      # output instead of firing it
                                                                      # and returning immediately
bash scripts/ci.sh --no-rebuild                                      # trigger a new run against
                                                                       # the already-running
                                                                       # container instead of
                                                                       # rebuilding/recreating it
bash scripts/ci.sh --refresh-tools                                    # force re-download of
                                                                        # buildx/compose/dagu even
                                                                        # if already cached
bash scripts/ci.sh --no-archunit-metrics                                # skip ArchUnit's
                                                                          # module-coupling export
                                                                          # (on by default)
bash scripts/ci.sh --sync-artifacts                                      # pull architecture-metrics.json/
                                                                           # pipeline-metrics.json onto
                                                                           # the host without
                                                                           # triggering a new run
                                                                           # (automatic after
                                                                           # --foreground; manual
                                                                           # after a backgrounded run)
```

**Live status, logs, and run history: `http://localhost:8082`** — Dagu's own web UI, reached
through a small `alpine/socat` proxy sidecar (`ci-runner-dagu-proxy`) rather than directly, since a
`--network host` container's bound ports (needed so `ci-runner`'s own DAG steps reach sibling
`ci-*` containers at plain `localhost:PORT`) aren't reachable from a real browser in this sandbox —
see `.claude/nav/adr-index.md`. There is no `scripts/ci/reports/` tree, `progress.txt`, or
`--report-dir`/`--keep-reports` flag anymore — Dagu's UI and run history (backed by the
`ci-dagu-home` named volume) replace all of that. Once the container is running, a DAG run can also
be triggered directly from that UI ("Start" on the `ci` DAG opens a dialog with a field per
`scripts/ci/dagu/ci.yaml` param) — `bash scripts/ci.sh` itself is only needed to build/start the
container in the first place, or to trigger a run from a script/CI context. **Triggering from the
UI never picks up source changes made since the last `bash scripts/ci.sh` rebuild** — the container
has no live view of the working tree (see `.claude/nav/adr-index.md` for why a bind mount
isn't used instead); re-run `bash scripts/ci.sh` after any code change before relying on the UI's
"Start" button again. Maven dependencies are
cached across runs via the `ci-m2-cache` named volume; buildx/compose/Dagu's own binaries are
cached via `ci-tools-cache` (downloaded once, reused across image rebuilds — see
`scripts/ci/docker-entrypoint.sh`). `deploy-and-run.sh` and `playwright/run.sh` accept env-var
overrides (container/network names, ports, volume names — default to the exact values already in
use, so normal dev usage is unaffected) for the isolated e2e stack.

**How to run it (Monitor-backed, same pattern as deploy/playwright/build-and-test):**
1. Trigger: `bash scripts/ci.sh [flags]` (no `--foreground`) — returns once the image is built, the
   container is up, and the run is triggered.
2. Launch `Monitor` with `command: "python3 -u scripts/ci/watch-run.py"` (`-u` is required, see the
   script's own header) — polls Dagu's REST API
   (through the proxy sidecar, not a log file, since a triggered run has no single streaming log)
   and emits one line per step-status transition, then a final `RUN <status>` line and exits on its
   own once the run reaches a terminal state. Unlike `deploy.log`/`playwright.log`, there's no file
   to `tail`, so this script — not a raw shell command — is what Monitor watches.

Use `--foreground` + Monitor+`tee` on `ci.sh` itself only when a single blocking call with a
definite end is actually needed (e.g. scripted verification inside a larger multi-step check) —
`--no-docs` keeps that call short when only a specific stage's pass/fail matters.
