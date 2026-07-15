## Deployment

### Prod deploy (full image rebuild)
```bash
bash scripts/deploy.sh        # Linux / WSL
scripts\deploy.bat            # Windows
```
Builds a Docker image from scratch (`mvn package` inside Docker — Vaadin's production bundle is built automatically by `vaadin-maven-plugin`, no Maven profile needed; `SPRING_PROFILES_ACTIVE=prod` at runtime sets `vaadin.productionMode=true`), starts all infra + app on **port 8081** (8080 reserved for local IntelliJ dev server).
After build, Docker garbage is pruned automatically (`image prune`, `container prune`, `volume prune`).

Use `--reset` to wipe DB/MinIO volumes. Use `--restart-infra` to restart containers only. Use `--reset-db` to truncate app tables (`reset-clean.sql`) before starting the app, without touching volumes. Use `--no-cache` to force a rebuild ignoring the Docker layer cache.

**Streaming output requirement — BuildKit + buildx:**
Docker Engine must have BuildKit enabled AND the `buildx` CLI plugin must be installed at
`~/.docker/cli-plugins/docker-buildx` — without it, plain `docker build` fails outright on this
sandbox's Docker version whenever the Dockerfile uses `--mount=type=cache` (confirmed directly:
`ERROR: BuildKit is enabled but the buildx component is missing`, not just a silent legacy-builder
fallback). The `--progress=plain` flag in `deploy.sh` then enables line-by-line streaming once
BuildKit is active.

**`docker compose` CLI plugin** — needed by `scripts/database/reset.sh` (starting dev DB when no
container exists yet) and `scripts/sonar/run.sh` (starting the SonarQube stack). Not present by
default in this sandbox.

**Both plugins are installed automatically, not manually — `scripts/ensure-docker-plugins.sh`.**
`deploy.sh` (`ensure_buildx`, before its build step), `scripts/database/reset.sh` and
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
`scripts/infra/docker-compose.db.yml`/`docker-compose.app.yml`/`docker-compose.minio.yml` live in
`scripts/infra/`, not the repo root, but read `${POSTGRES_IMAGE}` (and, per
[improvement-044](../features/issues/improvement-044-shared-env-config-consolidation.md), more
values to come) from the repo-root `.env`. Compose's default project directory — where it looks
for `.env` — is the directory containing the first `-f` file, **not** the invoking shell's working
directory. Always pass `--project-directory .` (run from the repo root) or
`--project-directory "$ROOT"` (absolute path), e.g.:
```bash
docker compose --project-directory . -f scripts/infra/docker-compose.db.yml up -d
```
Omitting it silently resolves `${POSTGRES_IMAGE}` to an empty string and fails with "service db
has neither an image nor a build context specified" — confirmed by direct testing, not assumption.
This is documented, version-independent Compose behavior — the same fix applies on any machine,
not just this sandbox.

**How to run deploy.sh:**
1. First launch Monitor with `persistent: true` watching `/tmp/deploy.log`:
   - Every 10s check if file size changed
   - If 1 minute with no new output → report "process may be stuck"
   - If ERROR appears in new output → report immediately
   - If BUILD SUCCESS or Started Application → report and stop
2. Then run synchronously (user sees streaming output):
   ```
   bash scripts/deploy.sh [args] 2>&1 | tee /tmp/deploy.log
   ```
   with `timeout: 600000`

**How to run dev deploy (deploy-dev.sh):** same Monitor + tee pattern as deploy.sh, but log to `/tmp/deploy-dev.log`.

### Local run (Maven, no Docker image rebuild)
```bat
scripts\run-local.bat           REM dev profile — Vaadin dev mode, port 8080
scripts\run-local.bat --prod    REM production Vaadin build, prod profile, port 8080
```
Windows-only (native Maven + Java — no WSL). Requires DB and MinIO already running. Use when you need to compare local vs Docker behaviour.

### Dev deploy (fast JAR hot-swap)
```bash
bash scripts/deploy-dev.sh    # Linux / WSL
scripts\deploy-dev.bat        # Windows
```
Builds the JAR locally (`mvn clean package -DskipTests`), copies it into the running container via `docker cp`, and restarts the container. **No Docker image rebuild** — typically ~3-4 min vs ~7-10 min for prod.

Use `--reset-cache` to clear the Maven cache volume before building. Use `--reset-db` to truncate app tables (`reset-clean.sql`) before the hot-swap restart.

**Requires:** infra (DB, MinIO) and the `marketplace-app` container already running. Run `deploy.sh` once first.

---

## SonarQube Analysis

All config lives in `/app/scripts/sonar/`. SonarQube server runs in Docker on `localhost:9099`.

### Start server manually (if needed)
```bash
docker compose -f scripts/sonar/docker-compose.sonar.yml up -d
```

### Run analysis
```bash
bash scripts/sonar.sh        # Linux / WSL
scripts\sonar.bat            # Windows
```

The script starts SonarQube automatically if not running, copies source files into a scanner container via `docker cp`, and runs `sonar-scanner-cli`. Results: `http://localhost:9099/dashboard?id=advertisement`.

**IMPORTANT:** Same Docker socket constraint as Playwright — never use `docker run -v`. The script uses `docker cp` internally.

---

## Plain Unit Tests (no Docker)

Fast JUnit 5 (+ Mockito where needed) unit tests with no Testcontainers, no real database, and
usually no Spring context — e.g. `query-lib`'s `SqlConditionTest`/`SqlOperatorTest`,
`marketplace-app`'s `AccessEvaluatorTest`/`AuthServiceTest`. Run via `scripts/unit-tests.sh`, never
raw `mvn`:

```bash
bash scripts/unit-tests.sh                       # all plain unit tests (query-lib + marketplace-app)
bash scripts/unit-tests.sh marketplace-app        # one module only
bash scripts/unit-tests.sh query-lib              # one module only
bash scripts/unit-tests.sh AccessEvaluatorTest    # one test class by name
scripts\unit-tests.bat                            # Windows
```

Delegates to `scripts/unit-tests/run.sh`. Streams full Maven output live via `tee`. After the run:
- `scripts/unit-tests/reports/run.log` — full streamed output
- `scripts/unit-tests/reports/surefire/<module>/` — one `.txt`/`.xml` report per test class, split
  by module

No Docker, no `--sandbox` flag — these tests never touch a container. If a test needs a real
Postgres, it belongs in `integration-tests` (see below), not here.

**How to run it (Monitor + tee pattern, same as everything else):** launch a `Monitor` watching
`scripts/unit-tests/reports/run.log` (10s interval, catch `PASSED|FAILED|ERROR`), then run
synchronously: `bash scripts/unit-tests.sh [scenario] 2>&1 | tee /tmp/unit-tests.log` with
`timeout: 600000`.

---

## Unit / Testcontainers Tests

All Testcontainers-based tests and their fixtures live in the `integration-tests` module (see
`integration-tests/CLAUDE.md` for why domain starters carry none of this themselves). For
Docker-free plain unit tests (`query-lib`, `marketplace-app`'s non-UI service layer), see "Plain
Unit Tests (no Docker)" above instead.

### Via script (preferred — streaming, reports folder, scenario selection)

```bash
bash scripts/integration-tests.sh                          # Linux / WSL — every test
scripts\integration-tests.bat                               # Windows

bash scripts/integration-tests.sh smoke                     # just PostgresContainerSmokeTest
bash scripts/integration-tests.sh AdvertisementRepositoryTest  # one class by name
bash scripts/integration-tests.sh --sandbox smoke            # + this sandbox's Docker workarounds
bash scripts/integration-tests.sh --no-check TaxonRepositoryTest  # skip the staleness check
```

Delegates to `integration-tests/run.sh` (same thin-wrapper shape as `scripts/playwright.sh` →
`playwright/run.sh`). Streams full Maven/Testcontainers output live via `tee`. After the run:
- `integration-tests/reports/run.log` — full streamed output
- `integration-tests/reports/surefire/` — one `.txt`/`.xml` pass/fail report per test class
  (copied from Maven's own `target/surefire-reports/`)

`--sandbox` applies `TESTCONTAINERS_RYUK_DISABLED=true INTEGRATION_TESTS_POSTGRES_FIXED_PORT=25432`
— **only needed in this claude-dev sandbox**, never on a normal developer machine (see below for
why). Omit it there; Testcontainers' defaults just work.

`run.sh` auto-detects whether `platform-commons`/`advertisement`/`user`/`taxon-spring-boot-starter`
changed since their last `~/.m2` install (comparing each module's newest `.java` file's mtime
against its installed JAR) and only reinstalls those before testing, instead of rebuilding all 7
non-`integration-tests` reactor modules every run (measured ~1:47-3:35 total when nothing needed
reinstalling vs. 3-7 min walking the full reactor, dominated by ~100s of "nothing to compile"
Maven overhead across those modules in this sandbox). No manual flag needed — confirmed the
detection correctly triggers a reinstall when a starter file actually changes, not just when
nothing changed. `--no-check` bypasses the detection entirely (test against whatever's in `~/.m2`
right now) — only for deliberately reproducing behavior against an older build. See
`integration-tests/CLAUDE.md` and `DECISIONS.md` ADR-007 for the full rule.

**How to run it (Monitor + tee pattern, same as deploy/Playwright):** launch a `Monitor` watching
`integration-tests/reports/run.log` (10s interval, catch `PASSED|FAILED|ERROR`), then run
synchronously: `bash scripts/integration-tests.sh --sandbox [scenario] 2>&1 | tee /tmp/integration-tests.log`
with `timeout: 600000`.

### Via direct command (no script, no reports folder)

```bash
mvn -pl integration-tests -am test
# or, in this sandbox only:
TESTCONTAINERS_RYUK_DISABLED=true INTEGRATION_TESTS_POSTGRES_FIXED_PORT=25432 mvn -pl integration-tests -am test
```
`-am` also builds whichever starters `integration-tests` currently depends on (required — they
are not otherwise built by a scoped `-pl integration-tests` alone).

### Never run via `deploy.sh`/`deploy-dev.sh`

Both build Maven inside a `docker build` stage (multi-stage `Dockerfile`) that already skips tests
(`./mvnw install -DskipTests`) and, even if it didn't, has no access to the outer Docker socket
(standard Docker-in-Docker isolation — no socket mount configured for the `builder` stage).
Testcontainers-based tests need a real reachable Docker daemon, which only exists when `mvn test`
is run directly, never inside the image build.

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
