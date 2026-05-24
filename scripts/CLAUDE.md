## Deployment

### Prod deploy (full image rebuild)
```bash
bash scripts/deploy.sh        # Linux / WSL
scripts\deploy.bat            # Windows
```
Builds a Docker image from scratch (`mvn -Pproduction` inside Docker), starts all infra + app on **port 8081** (8080 reserved for local IntelliJ dev server).
After build, Docker garbage is pruned automatically (`image prune`, `container prune`, `volume prune`).

Use `--reset` to wipe DB/MinIO volumes. Use `--restart-infra` to restart containers only.

### Dev deploy (fast JAR hot-swap)
```bash
bash scripts/deploy-dev.sh    # Linux / WSL
scripts\deploy-dev.bat        # Windows
```
Builds the JAR locally (`mvn -Pproduction -DskipTests`), copies it into the running container via `docker cp`, and restarts the container. **No Docker image rebuild** — typically ~3-4 min vs ~7-10 min for prod.

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
