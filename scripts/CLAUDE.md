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
container / unused volume on the machine too (see `scripts/ci/DECISIONS.md` ADR-001 for the
incident that made this explicit instead of automatic).

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

**Both plugins are installed automatically, not manually — `scripts/ensure-docker-plugins.sh`.**
`deploy-and-run.sh` (`ensure_buildx`, before its build step), `scripts/deploy-and-run/reset.sh` and
`scripts/sonar/run.sh` (`ensure_docker_compose`, before their respective `docker compose` calls)
all source this shared script and call the relevant function; each function checks `docker buildx
version` / `docker compose version` first and only downloads+installs if missing, so it's a no-op
on a normal developer machine where these already ship with Docker Desktop. Manual install (e.g.
to pre-warm a fresh sandbox, or troubleshoot outside any script) is still possible by running the
file directly — it installs both when not sourced:
```bash
bash scripts/ensure-docker-plugins.sh
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
placeholders by `application-dev.yml` (see `DECISIONS.md` ADR-009). Compose's default project
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

**How to run deploy-and-run.sh:**
1. First launch Monitor with `persistent: true` watching `/tmp/deploy.log`:
   - Every 10s check if file size changed
   - If 1 minute with no new output → report "process may be stuck"
   - If ERROR appears in new output → report immediately
   - If BUILD SUCCESS or Started Application → report and stop
2. Then run synchronously (user sees streaming output):
   ```
   bash scripts/deploy-and-run.sh [args] 2>&1 | tee /tmp/deploy.log
   ```
   with `timeout: 600000`

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
bash scripts/build-and-test.sh --sandbox --unit --integration      # both, in parallel
bash scripts/build-and-test.sh --unit --no-integration              # unit only
bash scripts/build-and-test.sh --no-unit --integration --sandbox    # integration only
bash scripts/build-and-test.sh --unit-test AccessEvaluatorTest --no-integration  # one class
bash scripts/build-and-test.sh --no-unit --integration-test AdvertisementRepositoryTest --sandbox
```

Builds the whole reactor into a container-isolated `~/.m2` first, then runs unit
(`query-lib`/`marketplace-app`/`marketplace-orchestrator`) and integration (`integration-tests`
module — Testcontainers-based repository tests, real Postgres) as parallel jobs inside that same
container. See `scripts/build-and-test/README.md` for the full flow and
`scripts/build-and-test/build.sh`'s own header for every flag. `--sandbox` applies
`TESTCONTAINERS_RYUK_DISABLED=true INTEGRATION_TESTS_POSTGRES_FIXED_PORT=25432` — **only needed in
this claude-dev sandbox**, never on a normal developer machine (see below for why).

Reports: `scripts/build-and-test/reports/surefire/<module>/`.

**How to run it (Monitor + tee pattern, same as everything else):** launch a `Monitor` watching
`scripts/build-and-test/reports/surefire/` (10s interval, catch new `.txt` files) or just the
console output itself (`PASSED|FAILED|ERROR|BUILD SUCCESS|BUILD FAILURE`), then run synchronously:
`bash scripts/build-and-test.sh --sandbox --unit --integration 2>&1 | tee /tmp/build-and-test.log`
with `timeout: 600000`.

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
- `integration-tests/reports/run.log` — full streamed output
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
`integration-tests/CLAUDE.md` and `DECISIONS.md` ADR-007 for the full rule.

Raw `mvn`, no reports folder: `mvn -pl integration-tests -am test` (or, in this sandbox only,
prefixed with `TESTCONTAINERS_RYUK_DISABLED=true INTEGRATION_TESTS_POSTGRES_FIXED_PORT=25432`).
`-am` also builds whichever starters `integration-tests` currently depends on (required — they
are not otherwise built by a scoped `-pl integration-tests` alone).

### Never run integration tests via `deploy-and-run.sh`

`deploy-and-run.sh` runs Maven with `-DskipTests` inside a `docker build` stage with no access to the
outer Docker socket — standard Docker-in-Docker isolation, no socket mount configured for the
`builder` stage. Testcontainers-based tests need a real reachable Docker daemon, which only exists
when `mvn test` is run directly, never inside this build path.

### Why this sandbox needs `INTEGRATION_TESTS_POSTGRES_FIXED_PORT` / `TESTCONTAINERS_RYUK_DISABLED`

Confirmed: Testcontainers can create a container here, but the test JVM cannot reach a
dynamically-assigned published port (only statically-published ones, e.g. `advertisement-db`'s
`5432`, are reachable) — a Docker Desktop / socket-proxy quirk specific to this sandbox, not a
code bug (same class of issue as the volume-mount limitation noted under Playwright below). Ryuk
(the container reaper) also can't connect back to the test JVM here. Neither variable is needed on
a normal developer machine — leave unset there; Testcontainers' default random-port assignment and
Ryuk cleanup both just work outside this sandbox.

---

## Running Playwright Tests

**How to run playwright.sh:**
1. Kill stale processes: `docker exec pw-runner pkill -f "node.*playwright" 2>/dev/null; true`
2. Launch Monitor with `persistent: true` watching `/tmp/playwright.log`:
   - Poll every 10s for new output
   - If 2 minutes with no new output → report "process may be stuck"
   - If `failed` or `Error` appears → report immediately
   - If `passed` summary line appears → report and stop
3. Then run synchronously (user sees streaming output):
   ```
   bash scripts/playwright.sh [scenario] 2>&1 | tee /tmp/playwright.log
   ```
   with `timeout: 600000`

---

## Local CI Runner (isolated, parameterized)

Lives in `scripts/ci/` (own `DECISIONS.md`/`README.md`, matching `scripts/sonar/`'s nested-module
shape, not `playwright/`'s root-level one — this is a tool wrapping other scripts, not a separate
test-authoring ecosystem). One CI-runner container (`scripts/ci/Dockerfile`), built fresh from the
current source tree and run with the host's `/var/run/docker.sock` mounted
(Docker-outside-of-Docker) — it creates and tears down its own isolated `ci-*`-named sibling
containers, never touching the persistent dev stack. See `scripts/ci/DECISIONS.md` ADR-001 for the
full design (why DooD, not DinD) and `scripts/ci/entrypoint.sh` for the in-container orchestration.

```bash
bash scripts/ci.sh                                        # default: most extensive run
                                                            # (unit+integration+e2e+sonar, e2e uses
                                                            # "e2e --full --ux"), backgrounded
bash scripts/ci.sh --unit --integration --e2e              # chosen stages only
bash scripts/ci.sh --all --sonar                            # everything, explicit
bash scripts/ci.sh --playwright-args "e2e --ux"              # override the e2e stage's Playwright args
bash scripts/ci.sh --report-dir /some/path                    # configurable report destination
bash scripts/ci.sh --keep-reports 5                             # keep last N report dirs (default 3)
bash scripts/ci.sh --keep-infra                                  # don't tear down the isolated
                                                                   # e2e stack after (debugging)
bash scripts/ci.sh --integration --sandbox                        # this claude-dev sandbox's
                                                                    # Testcontainers workaround
bash scripts/ci.sh --foreground                                    # block and stream instead of
                                                                     # the background default (see
                                                                     # "How to run it" below)
```

**Background by default, with a live progress file.** A bare `bash scripts/ci.sh` builds the image
in the foreground (fast, fails loudly), then detaches and returns control within seconds, printing
the background PID and two paths: `scripts/ci/reports/<timestamp>/progress.txt` (a small,
continuously-rewritten status file — per-stage `PENDING`/`RUNNING`/`DONE`/`FAILED` with elapsed
seconds, updated via periodic `docker cp` while the container runs, since bind mounts don't work in
this sandbox — same constraint as `playwright/CLAUDE.md`) and `run.log` (the outer orchestrator's
own stdout — container build/run/wait bookkeeping, not any stage's command output).
Check in on a running background job anytime with `cat <path>/progress.txt` — no need to attach to
anything. Reports land in `scripts/ci/reports/<timestamp>/{build-and-test,playwright,
sonar}/` (gitignored, pruned to the last 3 runs by default — see `--keep-reports`), each with its
own `run.log` holding that stage's actual command output (see `scripts/ci/DECISIONS.md` ADR-006).
Maven
dependencies are cached across runs via the `ci-m2-cache` named volume. No stage logic is
reimplemented — `entrypoint.sh` calls the existing `build-and-test.sh`/
`deploy-and-run.sh`+`playwright/run.sh`/`sonar.sh` scripts, with `deploy-and-run.sh` and `playwright/run.sh` now
accepting env-var overrides (container/network names, ports, volume names — default to the exact
values already in use, so normal dev usage is unaffected) for the isolated e2e stack.

**How to run it (when *you*, not the user, need to verify a change to this tool itself):** unlike
every other script in this file, do NOT use the Monitor + `| tee` pattern here for a normal
end-to-end run — `scripts/ci.sh`'s own background mode plus `progress.txt` already gives you a
non-blocking way to watch it, and re-wrapping that in a blocking foreground call defeats the whole
point of this tool. Launch it plain (`bash scripts/ci.sh [flags]`, no `--foreground`, no
backgrounding wrapper needed since it returns on its own in seconds), then either poll
`progress.txt` yourself between other work, or set up a `Monitor` that periodically reads
`progress.txt` (not raw stdout) and reports on `RESULT:`. Reserve `--foreground` + Monitor+`tee` for
the rare case where you need a single blocking call with a definite end (e.g. scripted verification
inside a larger multi-step check).
