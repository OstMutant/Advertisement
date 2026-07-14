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
Docker Engine must have BuildKit enabled (`"features": {"buildkit": true}` in Docker Engine JSON config) AND the `buildx` CLI plugin must be installed at `~/.docker/cli-plugins/docker-buildx`. Without the binary, Docker silently falls back to the legacy builder which buffers all output without a TTY — no streaming. Install once:
```bash
mkdir -p ~/.docker/cli-plugins
curl -Lo ~/.docker/cli-plugins/docker-buildx \
  https://github.com/docker/buildx/releases/download/v0.21.0/buildx-v0.21.0.linux-amd64
chmod +x ~/.docker/cli-plugins/docker-buildx
```
Verify: `docker buildx version`. The `--progress=plain` flag in `deploy.sh` then enables line-by-line streaming.

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

## Unit / Testcontainers Tests

```bash
mvn clean test 2>&1 | tee /tmp/test.log
```

**Never run via `deploy.sh`/`deploy-dev.sh`.** Both build Maven inside a `docker build` stage
(multi-stage `Dockerfile`) that already skips tests (`./mvnw install -DskipTests`) and, even if it
didn't, has no access to the outer Docker socket (standard Docker-in-Docker isolation — no socket
mount configured for the `builder` stage). Testcontainers-based tests (`improvement-027`) need a
real reachable Docker daemon, which only exists when `mvn test` is run directly in this
environment, never inside the image build. Confirmed reachable here: `/var/run/docker.sock`
mounted, `docker version` responds, `mvn`/`./mvnw` both present.

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
