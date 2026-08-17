# Scripts

Developer helper scripts for building, deploying, and maintaining the project.

All scripts resolve the project root automatically — run them from any directory.

**Self-healing rule:** every script auto-starts or auto-pulls whatever it needs.
If a container is stopped — it starts it. If an image is missing — it pulls it. If nothing exists — it bootstraps from scratch.

---

## deploy-and-run.sh / deploy-and-run.bat

Full local deploy pipeline. By default, no Docker image is built at all — it reuses
`scripts/build-and-test.sh`'s shared jar and runs it directly against the mounted `maven-cache`
volume. `--from-scratch` builds a real, separately tagged image instead, from the full multi-stage
root `Dockerfile`, for when an actual portable/deployable image is genuinely needed.

```bash
bash scripts/deploy-and-run.sh                   # Linux / WSL — full output to console
bash scripts/deploy-and-run.sh --file            # filtered output + full log to /tmp/deploy.log
bash scripts/deploy-and-run.sh --no-cache        # force rebuild ignoring Docker layer cache (re-downloads all dependencies)
bash scripts/deploy-and-run.sh --reset           # wipe all containers + volumes, start fresh
bash scripts/deploy-and-run.sh --restart-infra   # restart DB + MinIO (volumes preserved), redeploy app
bash scripts/deploy-and-run.sh --reset-only-db        # truncate app tables (reset-clean.sql) before starting the app
bash scripts/deploy-and-run.sh --prune-all       # deliberate whole-machine deep clean: also prunes stopped
                                          # containers + unused volumes host-wide, not scoped to
                                          # this app (see scripts/CLAUDE.md, scripts/ci/DECISIONS.md ADR-001)
scripts\deploy-and-run.bat                       # Windows (calls run.sh via WSL)
```

### What it does (default mode)

| Step | Action |
|------|--------|
| 1 | Pull `postgres:15-alpine`, `minio/minio:latest`, `minio/mc:latest` if not present |
| 2 | Start DB + MinIO (skips already-running containers) |
| 3 | Wait for DB (`pg_isready`) and MinIO (`/minio/health/live`) to be healthy |
| 4 | Create `advertisement` MinIO bucket if it does not exist |
| 5 | Run `scripts/build-and-test.sh` (refreshes `marketplace-app.jar` in the shared `maven-cache` volume) |
| 6 | Remove existing `marketplace-app` container |
| 7 | Start `marketplace-app` container directly from `eclipse-temurin:25-jre`, `maven-cache` volume mounted, running `java -jar` straight out of it — no image build |
| 8 | Wait for `"Started Application"` in logs (timeout 180s) |

### Flags

| Flag | Effect |
|------|--------|
| _(none)_ | Full output to console; start stopped infra containers, skip already-running ones |
| `--file` | Filtered output to console + full log saved to `/tmp/deploy.log` |
| `--no-cache` | Force `docker build --no-cache` — only meaningful with `--from-scratch`, ignores all cached layers, re-downloads all Maven dependencies |
| `--reset` | Stop + remove ALL containers and volumes, then start from scratch |
| `--restart-infra` | Remove and restart DB + MinIO containers, volumes preserved |
| `--reset-only-db` | Truncate app tables (`reset-clean.sql`) before starting the app — no volume wipe |
| `--with-tests` | Also run unit+integration tests as part of the `build-and-test.sh` step (default: build only, no tests, for deploy speed) |
| `--from-scratch` | Build a real, separately tagged `marketplace-app` image from the full multi-stage root `Dockerfile`, in complete isolation — the old, pre-reuse behavior, for when an actual portable/deployable image is genuinely needed |
| `--prune-all` | Also run `docker container prune -f`/`docker volume prune -f` — host-wide, not scoped to this app's own resources; opt-in only, see `scripts/CLAUDE.md` |

Flags can be combined: `bash scripts/deploy-and-run.sh --no-cache --file`

---

## run-local.bat

Run the application locally via Maven without a Docker image rebuild. Requires DB and MinIO already running (start via `scripts/deploy-and-run/`).

```bat
scripts\run-local.bat           REM dev profile — Vaadin dev mode, port 8080
scripts\run-local.bat --prod    REM production Vaadin build, prod profile, port 8080
```

### Profiles

Vaadin dev/production mode is controlled by the Spring profile (`vaadin.productionMode` in `application-{profile}.yml`), not by any Maven profile — there is no `production` Maven profile in this project.

| Flag | Spring profile | Vaadin mode | Connects to |
|------|----------------|-------------|-------------|
| _(none)_ | `dev` | development (`productionMode: false`) | `localhost:5432`, `localhost:9000` |
| `--prod` | `prod` | production (`productionMode: true`, minified JS) | `localhost:5432`, `localhost:9000` |

In `--prod` mode the local infra credentials are passed as env vars — same values as the Docker deploy but pointing to `localhost` instead of container names.

---

## playwright.sh / playwright.bat

Run Playwright tests. Delegates to `playwright/run.sh`.

```bash
bash scripts/playwright.sh              # all tests
bash scripts/playwright.sh e2e          # e2e suite (specs 01–06, skips spec 05 seed)
bash scripts/playwright.sh e2e --full --ux  # full e2e suite with screenshots
scripts\playwright.bat e2e --ux         # Windows
```

---

## build-and-test.sh / build-and-test.bat — unit + integration tests

Preferred way to run unit and/or integration tests — no local Java install needed, builds the
whole reactor once, then runs both in parallel inside the same container. See
`scripts/build-and-test/README.md` for the full flow.

```bash
bash scripts/build-and-test.sh --sandbox --unit --integration      # both, in parallel
bash scripts/build-and-test.sh --unit --no-integration              # unit only
bash scripts/build-and-test.sh --no-unit --integration --sandbox    # integration only
bash scripts/build-and-test.sh --unit-test AccessEvaluatorTest --no-integration
bash scripts/build-and-test.sh --no-unit --integration-test AdvertisementRepositoryTest --sandbox
```

Reports: `scripts/build-and-test/reports/surefire/<module>/`. `--sandbox` is only needed in this
claude-dev sandbox (dynamic Testcontainers ports aren't reachable there) — omit it on a normal
developer machine.

## `integration-tests/run.sh` — direct alternative, needs a local Java install

Testcontainers-based repository tests + fixtures (module `integration-tests` — owns every such
test for every starter, so starters carry none themselves) can also run directly, without a
container, via this module's own entry point — with capabilities `build-and-test.sh` deliberately
doesn't replicate (a targeted per-starter staleness check instead of always installing the whole
reactor, `--no-check` to bypass it entirely):

```bash
bash integration-tests/run.sh                          # all integration tests
bash integration-tests/run.sh smoke                    # just PostgresContainerSmokeTest
bash integration-tests/run.sh AdvertisementRepositoryTest  # one class by name
bash integration-tests/run.sh --sandbox smoke          # + this sandbox's Docker workarounds
bash integration-tests/run.sh --no-check TaxonRepositoryTest  # skip the staleness check
```

Reports after each run: `integration-tests/reports/run.log` (full output) and
`integration-tests/reports/surefire/` (pass/fail per test class). `run.sh` auto-detects whether the
starter modules it depends on changed since their last install and only rebuilds those before
testing (~1:47-3:35 vs. 3-7 min walking the full reactor every time) — no manual flag needed.
`--no-check` skips that detection entirely, testing against whatever's already in `~/.m2`; see
`integration-tests/CLAUDE.md` for the full rule.

---

## sonar.sh / sonar.bat

Run SonarQube analysis. Starts SonarQube automatically if not running. Delegates to `scripts/sonar/run.sh`.

```bash
bash scripts/sonar.sh              # blocking: exits non-zero if the quality gate fails
scripts\sonar.bat                  # Windows — same
bash scripts/sonar.sh --no-gate    # informational only, always exits 0
```

Results: `http://localhost:9099/dashboard?id=advertisement`

---

## scripts/deploy-and-run/reset.sh / reset.bat

Truncates all application data without restarting the app or touching MinIO volumes. Use when you need a clean DB for manual testing.

```bash
bash scripts/deploy-and-run/reset.sh
scripts\deploy-and-run\reset.bat
```

**Self-healing:** if the DB container is stopped — starts it automatically.

**vs `deploy-and-run.sh --reset`:** `reset.sh` only truncates tables — containers and volumes stay intact, completes in ~1s. `deploy-and-run.sh --reset` destroys all containers and Docker volumes (DB + MinIO), then does a full rebuild (~7-10 min).

---

## clean.bat

Removes Maven `target/` directories, Vaadin generated frontend files, and Playwright artifacts.

```bat
scripts\clean.bat
```

---

## collect-code.bat

Collects all source files into a single `all-code.txt` in the project root — useful for AI analysis.

```bat
scripts\collect-code.bat
```

---

## claude.bat

Starts the Claude Code Docker container with the project directory and auth config mounted.

```bat
scripts\claude.bat your.email@gmail.com
```

---

## Docker socket constraint

`scripts/build-and-test/run.sh` and `playwright/run.sh` both run builds/tests inside Docker containers that need access to the Docker daemon. Volume mounts (`-v /host/path:/container/path`) do not work when the caller is itself a Docker container (e.g. the Claude dev container) — Docker resolves the host path from the **host machine**, not from inside the caller container, resulting in an empty mount.

Both scripts work around this the same way:
- **`scripts/build-and-test/run.sh`** — streams source files into `advertisement-build-env` via tar pipe: `tar | docker run -i ... bash -c "tar -xzf - && build.sh"`
- **`playwright/run.sh`** — copies test files into `pw-runner` via `docker cp`

This means both scripts work correctly from any context: Windows WSL, a terminal, or the Claude dev container.

---

## Container reference

| Container | Image | Ports | Started by | Purpose |
|-----------|-------|-------|-----------|---------|
| `advertisement-db` | `postgres:15-alpine` | `5432` | `deploy-and-run.sh`, `docker-compose.db.yml` | PostgreSQL database |
| `advertisement-minio` | `minio/minio:latest` | `9000` (API), `9001` (console) | `deploy-and-run.sh`, `docker-compose.minio.yml` | S3-compatible storage (MinIO) |
| `marketplace-app` | built from `Dockerfile` | `8081` | `deploy-and-run.sh` | Spring Boot + Vaadin application |
| `advertisement-build-only` | `advertisement-build-env`, built from `scripts/build-and-test/Dockerfile` | — | `build-and-test.sh` (throwaway `--rm`, per build) | JDK 25 — builds the reactor into the shared `~/.m2` |
| `pw-runner` | `mcr.microsoft.com/playwright:v1.61.1-jammy` | — | `playwright/run.sh` (reused across runs) | Playwright test runner |
| `claude-dev` | built from `Dockerfile.ai` | — | `scripts/claude.bat` | Claude Code dev environment |

### Volumes

| Volume | Used by | Purpose |
|--------|---------|---------|
| `advertisement_postgres_data` | `advertisement-db` | PostgreSQL data (persists across container restarts) |
| `advertisement_minio_data` | `advertisement-minio` | MinIO object storage data |
| `maven-cache` | `advertisement-build-env` | Maven `~/.m2/repository` — persists between `build-and-test.sh` runs; also holds `artifacts/marketplace-app.jar`, the shared build's own output |

**Credentials:**
- DB: `experiments_user` / `experiments_user_password`, database `experiments`
- MinIO: `admin` / `admin12345`, bucket `advertisement`, console at `http://localhost:9001`
- App: `http://localhost:8081`

---

## Folder structure

```
scripts/
  deploy-and-run/  — deploy pipeline logic, Docker Compose files for local infrastructure (DB,
                     MinIO, app stack), database reset script
  build-and-test/     — Docker build environment used by build-and-test.sh (JDK 25)
  sonar/           — SonarQube configuration and scanner
  ci/              — isolated local CI runner (Dockerfile, entrypoint.sh, own README/DECISIONS.md)
  run-all-tests/   — run.sh + reports/ output for run-all-tests.sh
```

---

## run-all-tests.sh / run-all-tests.bat

Runs `scripts/build-and-test.sh --unit --integration` (installs the whole reactor once, then runs
unit+integration tests in parallel against it) and `scripts/playwright.sh` in parallel with that
(it never touches the Maven reactor). See `scripts/DECISIONS.md` ADR-004's annotation.

```bash
bash scripts/run-all-tests.sh
bash scripts/run-all-tests.sh --unit-test AccessEvaluatorTest \
                               --integration-test TaxonRepositoryTest --sandbox \
                               --playwright "e2e --ux"
```

Reports: `scripts/run-all-tests/reports/build-and-test.log` + `playwright.log`; Surefire reports
under `scripts/build-and-test/reports/surefire/`.

---

## ci.sh / ci.bat

Isolated local CI runner: builds a dedicated CI-runner container (Docker-outside-of-Docker, own
`/var/run/docker.sock` mount) and runs unit → integration → e2e → Sonar in one pass, without
touching the persistent dev stack. Backgrounded by default, with a live `progress.txt`.

```bash
bash scripts/ci.sh                              # default: unit+integration+e2e+sonar, backgrounded
bash scripts/ci.sh --unit --integration --e2e   # chosen stages only
bash scripts/ci.sh --integration --sandbox      # this sandbox's Testcontainers workaround
bash scripts/ci.sh --foreground                 # block and stream instead of the background default
```

Reports: `scripts/ci/reports/<timestamp>/{build-and-test,playwright,sonar}/`
(pruned to the last 3 runs by default — see `--keep-reports`). Full detail: `scripts/ci/README.md`
and `scripts/ci/DECISIONS.md`.
