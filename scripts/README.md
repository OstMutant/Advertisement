# Scripts

Developer helper scripts for building, deploying, and maintaining the project.

All scripts resolve the project root automatically — run them from any directory.

**Self-healing rule:** every script auto-starts or auto-pulls whatever it needs.
If a container is stopped — it starts it. If an image is missing — it pulls it. If nothing exists — it bootstraps from scratch.

---

## deploy.sh / deploy.bat

Full local deploy pipeline. Builds a Docker image from scratch and starts all services.

Maven dependencies are cached in Docker layer cache — if `pom.xml` files have not changed, dependencies are not re-downloaded on the next run.

```bash
bash scripts/deploy.sh                   # Linux / WSL — full output to console
bash scripts/deploy.sh --file            # filtered output + full log to /tmp/deploy.log
bash scripts/deploy.sh --no-cache        # force rebuild ignoring Docker layer cache (re-downloads all dependencies)
bash scripts/deploy.sh --reset           # wipe all containers + volumes, start fresh
bash scripts/deploy.sh --restart-infra   # restart DB + MinIO (volumes preserved), redeploy app
scripts\deploy.bat                       # Windows (calls deploy.sh via WSL)
```

### What it does (default mode)

| Step | Action |
|------|--------|
| 1 | Pull `postgres:15-alpine`, `minio/minio:latest`, `minio/mc:latest` if not present |
| 2 | Start DB + MinIO (skips already-running containers) |
| 3 | Wait for DB (`pg_isready`) and MinIO (`/minio/health/live`) to be healthy |
| 4 | Create `advertisement` MinIO bucket if it does not exist |
| 5 | Remove existing `marketplace-app` container |
| 6 | Build Docker image from `Dockerfile` (multi-stage: JDK builder → JRE runtime) |
| 7 | Start `marketplace-app` container with production env vars |
| 8 | Wait for `"Started Application"` in logs (timeout 180s) |

### Flags

| Flag | Effect |
|------|--------|
| _(none)_ | Full output to console; start stopped infra containers, skip already-running ones |
| `--file` | Filtered output to console + full log saved to `/tmp/deploy.log` |
| `--no-cache` | Force `docker build --no-cache` — ignores all cached layers, re-downloads all Maven dependencies |
| `--reset` | Stop + remove ALL containers and volumes, then start from scratch |
| `--restart-infra` | Remove and restart DB + MinIO containers, volumes preserved |

Flags can be combined: `bash scripts/deploy.sh --no-cache --file`

---

## deploy-dev.sh / deploy-dev.bat

Fast dev deploy: pipes source files into a throwaway `advertisement-build-env` container, builds the JAR, and hot-swaps it into `marketplace-app`. No Docker image rebuild — typically **3-4 min** vs 7-10 min for a full prod rebuild.

Maven dependencies are cached in a named Docker volume (`maven-cache`) — persists between runs regardless of whether the container is removed.

```bash
bash scripts/deploy-dev.sh                 # Linux / WSL — full output to console
bash scripts/deploy-dev.sh --file          # filtered output + full log to /tmp/deploy-dev.log
bash scripts/deploy-dev.sh --reset-cache   # wipe Maven cache volume before building (re-downloads all dependencies)
scripts\deploy-dev.bat                     # Windows (calls deploy-dev.sh via WSL)
```

**Self-healing:** builds `advertisement-build-env` image if missing. If `marketplace-app` is missing — runs `deploy.sh` first; if stopped — starts it.

### Maven cache

Dependencies are stored in Docker named volume `maven-cache` mounted at `/root/.m2` inside the build container. First run downloads everything; subsequent runs reuse the cache.

Use `--reset-cache` to wipe the volume and force a full re-download (e.g. after dependency conflicts or a corrupt cache).

### What it does

| Step | Action |
|------|--------|
| 1 | Build `advertisement-build-env` image if not present (JDK 25 + Docker CLI) |
| 2 | If `marketplace-app` missing → run `deploy.sh`; if stopped → start it |
| 3 | Pipe source files into `advertisement-build-env` via tar (excludes `target/`, `.git/`) |
| 4 | Build JAR with `mvn -Pproduction -DskipTests` inside the container |
| 5 | `docker cp` the JAR into the running `marketplace-app` container |
| 6 | `docker restart marketplace-app` and wait for `"Started Application"` (timeout 180s) |

### How Windows deploy works

`deploy-dev.bat` calls `wsl bash deploy-dev.sh`. The build runs inside `advertisement-build-env` container — no Java or Maven required on the developer's machine. Source files are streamed in via tar pipe; build logs are visible in Docker Desktop and in the console.

---

## run-local.bat

Run the application locally via Maven without a Docker image rebuild. Requires DB and MinIO already running (start via `scripts/infra/`).

```bat
scripts\run-local.bat           REM dev profile — Vaadin dev mode, port 8080
scripts\run-local.bat --prod    REM production Vaadin build, prod profile, port 8080
```

### Profiles

| Flag | Maven profile | Spring profile | Vaadin mode | Connects to |
|------|--------------|----------------|-------------|-------------|
| _(none)_ | default | `dev` | development | `localhost:5432`, `localhost:9000` |
| `--prod` | `production` | `prod` | production (minified JS) | `localhost:5432`, `localhost:9000` |

In `--prod` mode the local infra credentials are passed as env vars — same values as the Docker deploy but pointing to `localhost` instead of container names.

---

## playwright.sh / playwright.bat

Run Playwright tests. Delegates to `playwright/run.sh`.

```bash
bash scripts/playwright.sh              # all tests
bash scripts/playwright.sh smoke        # one scenario
bash scripts/playwright.sh smoke --ux   # with screenshots
scripts\playwright.bat smoke            # Windows
```

---

## sonar.sh / sonar.bat

Run SonarQube analysis. Starts SonarQube automatically if not running. Delegates to `scripts/sonar/run.sh`.

```bash
bash scripts/sonar.sh
scripts\sonar.bat
```

Results: `http://localhost:9099/dashboard?id=advertisement`

---

## scripts/database/reset.sh / reset.bat

Truncates all application data without restarting the app or touching MinIO volumes. Use when you need a clean DB for manual testing.

```bash
bash scripts/database/reset.sh
scripts\database\reset.bat
```

**Self-healing:** if the DB container is stopped — starts it automatically.

**vs `deploy.sh --reset`:** `reset.sh` only truncates tables — containers and volumes stay intact, completes in ~1s. `deploy.sh --reset` destroys all containers and Docker volumes (DB + MinIO), then does a full rebuild (~7-10 min).

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

`deploy-dev.sh` and `playwright/run.sh` both run builds/tests inside Docker containers that need access to the Docker daemon. Volume mounts (`-v /host/path:/container/path`) do not work when the caller is itself a Docker container (e.g. the Claude dev container) — Docker resolves the host path from the **host machine**, not from inside the caller container, resulting in an empty mount.

Both scripts work around this the same way:
- **`deploy-dev.sh`** — streams source files into `advertisement-build-env` via tar pipe: `tar | docker run -i ... bash -c "tar -xzf - && build.sh"`
- **`playwright/run.sh`** — copies test files into `pw-runner` via `docker cp`

This means both scripts work correctly from any context: Windows WSL, a terminal, or the Claude dev container.

---

## Container reference

| Container | Image | Ports | Started by | Purpose |
|-----------|-------|-------|-----------|---------|
| `advertisement-db` | `postgres:15-alpine` | `5432` | `deploy.sh`, `docker-compose.db.yml` | PostgreSQL database |
| `advertisement-minio` | `minio/minio:latest` | `9000` (API), `9001` (console) | `deploy.sh`, `docker-compose.minio.yml` | S3-compatible storage (MinIO) |
| `marketplace-app` | built from `Dockerfile` | `8081` | `deploy.sh` | Spring Boot + Vaadin application |
| `advertisement-build-env` | built from `scripts/build-env/Dockerfile` | — | `deploy-dev.sh` (throwaway `--rm`, per build) | JDK 25 + Docker CLI — builds JAR, hot-swaps into marketplace-app |
| `pw-runner` | `mcr.microsoft.com/playwright:v1.52.0-jammy` | — | `playwright/run.sh` (reused across runs) | Playwright test runner |
| `claude-dev` | built from `Dockerfile.ai` | — | `scripts/claude.bat` | Claude Code dev environment |

### Volumes

| Volume | Used by | Purpose |
|--------|---------|---------|
| `advertisement_postgres_data` | `advertisement-db` | PostgreSQL data (persists across container restarts) |
| `advertisement_minio_data` | `advertisement-minio` | MinIO object storage data |
| `maven-cache` | `advertisement-build-env` | Maven `~/.m2/repository` — persists between `deploy-dev.sh` runs |

**Credentials:**
- DB: `experiments_user` / `experiments_user_password`, database `experiments`
- MinIO: `admin` / `admin12345`, bucket `advertisement`, console at `http://localhost:9001`
- App: `http://localhost:8081`

---

## Folder structure

```
scripts/
  infra/           — Docker Compose files for local infrastructure (DB, MinIO, app stack)
  build-env/       — Docker build environment for deploy-dev (JDK 25 + Docker CLI)
  database/        — SQL scripts and database helpers (reset-clean.sql)
  sonar/           — SonarQube configuration and scanner
```
