# Scripts

Developer helper scripts for building, deploying, and maintaining the project.

All scripts resolve the project root automatically — run them from any directory.

---

## deploy.sh / deploy.bat

Full local deploy pipeline. Handles everything from a clean Docker environment.

```bash
bash scripts/deploy.sh                   # Linux / WSL — default
bash scripts/deploy.sh --reset           # wipe all containers + volumes, start fresh
bash scripts/deploy.sh --restart-infra   # restart DB + MinIO (volumes preserved), redeploy app
scripts\deploy.bat                       # Windows (calls deploy.sh via bash)
```

### What it does (default mode)

| Step | Action |
|------|--------|
| 1 | Pull `postgres:15-alpine`, `minio/minio:latest`, `minio/mc:latest` if not present locally |
| 2 | Start DB + MinIO via Docker Compose (running containers are not touched) |
| 3 | Wait for DB (`pg_isready`) and MinIO (`/minio/health/live`) to be healthy |
| 4 | Create `advertisement` MinIO bucket if it does not exist |
| 5 | Remove existing `marketplace-app` container |
| 6 | Build Docker image from `Dockerfile` |
| 7 | Start `marketplace-app` container with production env vars |
| 8 | Wait for `"Started Application"` in container logs (timeout 180s) |

### Flags

| Flag | Effect |
|------|--------|
| _(none)_ | Start stopped infra containers; skip already-running ones |
| `--reset` | Stop + remove ALL containers and volumes, then start from scratch |
| `--restart-infra` | Remove and restart DB + MinIO containers, volumes are preserved |

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

## reset-db.sh / reset-db.bat

Reset the local database to a clean state with 3 minimal seed users. Run before Playwright tests when you need a fresh start. Delegates to `scripts/database/reset.sh`.

```bash
bash scripts/reset-db.sh
scripts\reset-db.bat
```

---

## seed-db.sh / seed-db.bat

Insert 50 dev users (USER / MODERATOR / ADMIN mix) and sample advertisements. Safe to run multiple times — uses `ON CONFLICT DO NOTHING`. Delegates to `scripts/database/seed.sh`.

```bash
bash scripts/seed-db.sh
scripts\seed-db.bat
```

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
