# Infrastructure

Technical infrastructure overview for the Advertisement Platform — how to run it, the scripts that
drive it, and the environment it expects. See [README.md](README.md) for what the project is.

---

## Running Locally

The project uses three separate Docker Compose files:

| File | Purpose |
|---|---|
| `scripts/deploy-and-run/docker-compose.db.yml` | PostgreSQL |
| `scripts/deploy-and-run/docker-compose.minio.yml` | MinIO (S3-compatible storage, emulates Supabase Storage) |
| `scripts/deploy-and-run/docker-compose.app.yml` | Application (production build) |

### Option 1 — Dev mode (run from IDE)

Start only the infrastructure:

```bash
docker-compose -f scripts/deploy-and-run/docker-compose.db.yml -f scripts/deploy-and-run/docker-compose.minio.yml up -d
```

Then run the application from your IDE with the `dev` Spring profile active.  
The `dev` profile connects to `localhost:5432` and `localhost:9000`.

MinIO console: http://localhost:9001 — login: `admin` / `admin12345`  
The `advertisement` bucket is created automatically on first start.

Open the app: http://localhost:8080

### Option 2 — Full Docker (local production simulation)

**Recommended path:** `bash scripts/deploy-and-run.sh` — the canonical, actively-maintained way to run
the full production build locally (BuildKit caching, automatic Docker garbage pruning, startup
detection, `--reset`/`--restart-infra`/`--reset-only-db`/`--no-cache` flags). See
[`scripts/README.md`](scripts/README.md) and [`scripts/CLAUDE.md`](scripts/CLAUDE.md) for details.
**This starts the app on port 8081**, not 8080 (8080 is reserved for Option 1's IDE dev mode).

The raw `docker-compose.app.yml` file below also exists and works, but publishes on **port 8080**
(a different port than `deploy-and-run.sh`) and has none of `deploy-and-run.sh`'s caching/pruning/flag support —
prefer `deploy-and-run.sh` unless you specifically need the bare compose file:

```bash
docker-compose -f scripts/deploy-and-run/docker-compose.db.yml -f scripts/deploy-and-run/docker-compose.minio.yml -f scripts/deploy-and-run/docker-compose.app.yml up --build
```

To stop and remove volumes:

```bash
docker-compose -f scripts/deploy-and-run/docker-compose.db.yml -f scripts/deploy-and-run/docker-compose.minio.yml -f scripts/deploy-and-run/docker-compose.app.yml down -v
```

---

## Helper Scripts

All developer scripts live in `scripts/`. See [`scripts/README.md`](scripts/README.md) for full details.

| Script | Purpose |
|---|---|
| `scripts/deploy-and-run.sh` / `scripts/deploy-and-run.bat` | Full deploy pipeline: pull images → start infra → build → run → wait for startup |
| `scripts/run-local.bat` | Run the app via Maven with no Docker image rebuild (dev or prod Vaadin mode) |
| `scripts/build-and-test.sh` / `scripts/build-and-test.bat` | Build the whole reactor, optionally run unit/integration tests in parallel — no local Java needed |
| `scripts/playwright.sh` / `scripts/playwright.bat` | Run Playwright end-to-end tests (delegates to `playwright/run.sh`) |
| `scripts/run-all-tests.sh` / `scripts/run-all-tests.bat` | `build-and-test.sh` (unit + integration in parallel) plus Playwright in parallel with that, one command |
| `scripts/sonar.sh` / `scripts/sonar.bat` | Run SonarQube static analysis (delegates to `scripts/sonar/run.sh`) |
| `scripts/ci.sh` / `scripts/ci.bat` | Isolated local CI pipeline — unit + integration + e2e + Sonar in one backgrounded pass |
| `scripts/clean.bat` | Remove Maven `target/` directories and Vaadin generated files |
| `scripts/collect-code.bat` | Collect all source files into a single `all-code.txt` for AI analysis |
| `scripts/claude.bat` | Start Claude Code container with project and auth mounts |

```bash
bash scripts/deploy-and-run.sh                  # default: skip already-running containers
bash scripts/deploy-and-run.sh --reset          # wipe everything and start from scratch
bash scripts/deploy-and-run.sh --restart-infra  # restart DB + MinIO, redeploy app
```

All scripts resolve the project root automatically — run them from any directory.

---

## AI-Assisted Development Workflow

This project is built and maintained with [Claude Code](https://claude.com/claude-code) as an
active part of the engineering process, not just autocomplete. A set of custom slash commands
wraps the day-to-day loop — build, test, document, review — into single, repeatable steps:

| Command | Purpose |
|---|---|
| `/build-and-test` | Build the reactor (+ optional tests) |
| `/deploy-and-run` | Rebuild the Docker image and start the app |
| `/playwright` | Run the Playwright end-to-end suite |
| `/sonar` | Run SonarQube static analysis |
| `/run-all-tests` | Unit tests → integration tests sequentially, Playwright in parallel |
| `/ci` | Full isolated CI pipeline — unit + integration + e2e + Sonar, backgrounded |
| `/sync-docs` | Reconcile architecture docs (`DECISIONS.md`/`CLAUDE.md`) with the actual code |
| `/record-decision` | Record a new architectural decision in the relevant module's `DECISIONS.md` |
| `/feature` | Scaffold a new tracked backlog issue, ranked into the priority list |
| `/autopilot` | Approve a scoped task once, then implement + test + document it end-to-end |

Every module keeps its own `DECISIONS.md` (an ADR log — why, not just what) and `CLAUDE.md` (the
working agreement for changes in that module). Those same documents drive the AI-assisted workflow
and double as onboarding notes for a human contributor — one source of truth either way.

### Running the AI dev container

`scripts/claude.bat` starts Claude Code itself in an isolated Docker container (built from
`Dockerfile.ai`), with the project directory and a per-account auth config mounted:

```bat
scripts\claude.bat your.email@gmail.com
```

Chat history and project context are shared across accounts (same mounted project directory);
only the auth config folder is per-account, so switching accounts on rate limits keeps the
conversation going.

Once the container is up: running the deploy script (`scripts/deploy-and-run.sh`) to build and start the
app, then the Playwright script (`scripts/playwright.sh`) against it, is already a complete,
working test environment — real Postgres, real MinIO, a real production-mode build, and a full
browser-driven end-to-end suite — with no extra setup beyond those two commands.

---

## Database Scripts

| File | Purpose |
|---|---|
| `scripts/reset.sh` / `scripts/reset.bat` | Truncates all application data without restarting the app or touching MinIO volumes (~1s) — delegates to `scripts/deploy-and-run/reset.sh`, runs `reset-clean.sql` |
| `scripts/deploy-and-run/reset-clean.sql` | Truncates all tables (no seed data). Run automatically by `playwright/run.sh` before every test run. |

---

## Environment Variables

Key variables used by the application.
* For **local Docker testing**, configure them in `scripts/deploy-and-run/docker-compose.app.yml`.
* For **production**, set them directly in the hosting provider's dashboard/secrets manager.

| Variable | Description | Example |
|---|---|---|
| `DB_HOST` | PostgreSQL host | `db` / a managed Postgres hostname |
| `DB_PORT` | PostgreSQL port | `5432` |
| `DB_NAME` | Database name | `experiments` |
| `DB_USER` | Database user | `experiments_user` |
| `DB_PASSWORD` | Database password | — |
| `DB_SSL_PARAMS` | Optional SSL query params | `?sslmode=require` |
| `S3_ENDPOINT` | S3-compatible storage endpoint | `http://minio:9000` |
| `S3_BUCKET` | Bucket name | `advertisement` |
| `S3_ACCESS_KEY` | S3 access key | — |
| `S3_SECRET_KEY` | S3 secret key | — |
| `S3_REGION` | S3 region | `us-east-1` / `auto` |
| `S3_PUBLIC_URL` | Public base URL for file access | `http://localhost:9000/advertisement` |
| `APP_PUBLIC_URL` | Public base URL of the app itself — used to build absolute deep-link/OG/sitemap URLs | `http://localhost:8080` |

---

## Running Without Docker

Requires a running PostgreSQL instance and a running MinIO instance (or any S3-compatible storage)
matching the `application-dev.yml` config.

```bash
git clone https://github.com/OstMutant/Advertisement.git
cd Advertisement
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```
