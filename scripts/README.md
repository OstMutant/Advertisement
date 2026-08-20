# Scripts

Developer helper scripts for building, deploying, and maintaining the project.

All scripts resolve the project root automatically — run them from any directory.

**Self-healing rule:** every script auto-starts or auto-pulls whatever it needs.
If a container is stopped, it starts it. If an image is missing, it pulls it. If nothing exists, it bootstraps from scratch.

---

## Entry points

Each entry point's own real logic lives where noted below — see that file's own header for what
it does, its flags, and its exact behavior; not restated here.

| Entry point | Real logic lives in |
|---|---|
| `deploy-and-run.sh` / `.bat` | `scripts/deploy-and-run/run.sh` |
| `run-local.bat` | self-contained (no delegation) |
| `playwright.sh` / `.bat` | `playwright/run.sh` |
| `build-and-test.sh` / `.bat` | `scripts/build-and-test/run.sh` — see `scripts/build-and-test/README.md` for the full flow |
| `integration-tests/run.sh` | self-contained — a direct alternative to `build-and-test.sh`, needs a local Java install |
| `sonar.sh` / `.bat` | `scripts/sonar/run.sh` |
| `reset.sh` / `.bat` | `scripts/deploy-and-run/reset.sh` — a faster, narrower alternative to `deploy-and-run.sh --reset`: only truncates tables (~1s, containers/volumes stay intact) vs. `--reset`'s full container+volume wipe and rebuild (~7-10 min) |
| `clean.bat` | self-contained (no delegation) |
| `collect-code.bat` | self-contained (no delegation) |
| `claude.bat` | self-contained (no delegation) |
| `run-all-tests.sh` / `.bat` | `scripts/run-all-tests/run.sh` — see `docs/ai/adr-index.md` for the unit/integration pairing's own history |
| `ci.sh` / `.bat` | `scripts/ci/run.sh` — see `scripts/ci/README.md` and `scripts/ci/DECISIONS.md` |

---

## Docker socket constraint

`scripts/build-and-test/run.sh` and `playwright/run.sh` both run builds/tests inside Docker
containers that need access to the Docker daemon. Volume mounts (`-v /host/path:/container/path`)
do not work when the caller is itself a Docker container (e.g. the Claude dev container) — Docker
resolves the host path from the host machine, not from inside the caller container, resulting in
an empty mount. Both work around this the same way in spirit, via a different mechanism (tar-pipe
vs. `docker cp`) — see each script's own header.

---

## Container reference

| Container | Image | Ports | Started by |
|-----------|-------|-------|-----------|
| `advertisement-db` | `postgres:15-alpine` | `5432` | `deploy-and-run.sh`, `docker-compose.db.yml` |
| `advertisement-minio` | `minio/minio:latest` | `9000` (API), `9001` (console) | `deploy-and-run.sh`, `docker-compose.minio.yml` |
| `marketplace-app` | built from `Dockerfile` | `8081` | `deploy-and-run.sh` |
| `advertisement-build-only` | `advertisement-build-env`, built from `scripts/build-and-test/Dockerfile` | -- | `build-and-test.sh` |
| `pw-runner` | `mcr.microsoft.com/playwright:v1.61.1-jammy` | -- | `playwright/run.sh` |
| `claude-dev` | built from `Dockerfile.ai` | -- | `claude.bat` |
| `sonarqube` | `sonarqube:community` | `9099` | `sonar.sh`, `docker-compose.sonar.yml` |
| `sonar-scanner` | `sonarsource/sonar-scanner-cli:latest` | -- | `sonar.sh` |
| `ci-runner` | built from `scripts/ci/Dockerfile` | `18080` (internal, via `--network host`) | `ci.sh` |
| `ci-runner-dagu-proxy` | `alpine/socat` | `8082` | `ci.sh` |

### Volumes

| Volume | Used by |
|--------|---------|
| `advertisement_postgres_data` | `advertisement-db`, created directly by `deploy-and-run.sh`'s own `docker run` |
| `advertisement_minio_data` | `advertisement-minio`, created directly by `deploy-and-run.sh`'s own `docker run` |
| `postgres_data` | `db` service in `docker-compose.db.yml` -- a separate volume from `advertisement_postgres_data` above despite both backing a container named `advertisement-db`, since this is the raw-compose path, not `deploy-and-run.sh`'s |
| `minio_data` | `minio` service in `docker-compose.minio.yml` -- same raw-compose distinction as `postgres_data` above |
| `maven-cache` | `advertisement-build-env` -- also holds `artifacts/marketplace-app.jar`, the shared build's own output |
| `ci-m2-cache` / `ci-dagu-home` / `ci-tools-cache` | `ci-runner` (see `scripts/ci/run.sh`'s own header) |
| `sonarqube_data` / `sonarqube_logs` / `sonarqube_extensions` / `sonarqube_temp` | `sonarqube` service in `docker-compose.sonar.yml` |

**Credentials:** DB `experiments_user`/`experiments_user_password`, database `experiments`. MinIO
`admin`/`admin12345`, bucket `advertisement`, console at `http://localhost:9001`. App at
`http://localhost:8081`.

---

## Folder structure

```
scripts/
  deploy-and-run/  -- deploy pipeline logic, Docker Compose files for local infrastructure,
                       database reset script
  build-and-test/  -- Docker build environment used by build-and-test.sh
  sonar/           -- SonarQube configuration and scanner
  ci/              -- isolated local CI runner
  run-all-tests/   -- run.sh for run-all-tests.sh
  utils/           -- shared library scripts sourced by multiple script-groups
```
