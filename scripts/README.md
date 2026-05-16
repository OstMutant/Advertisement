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
| 5 | Remove existing `advertisement-app` container |
| 6 | Build Docker image from `Dockerfile` |
| 7 | Start `advertisement-app` container with production env vars |
| 8 | Wait for `"Started Application"` in container logs (timeout 180s) |

### Flags

| Flag | Effect |
|------|--------|
| _(none)_ | Start stopped infra containers; skip already-running ones |
| `--reset` | Stop + remove ALL containers and volumes, then start from scratch |
| `--restart-infra` | Remove and restart DB + MinIO containers, volumes are preserved |

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
