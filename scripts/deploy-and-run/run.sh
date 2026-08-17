#!/bin/bash
# ── Header ──────────────────────────────────────────────────────────────────
# Description: Full prod deploy -- reuses scripts/build-and-test.sh's shared maven-cache jar by
#   default (thin runtime-only image, no Maven build inside Docker), starts all services on port
#   8081. A Liquibase checksum mismatch (a changeset edited in place instead of a new one added,
#   e.g. a removed FK constraint, while the local dev DB still has the old version applied) is
#   detected automatically and self-heals: wipes dev DB/MinIO volumes and retries once, same as
#   --reset, no re-run needed -- dev-only, a real deployed database needs a proper migration, not a
#   wipe. See DECISIONS.md for the full reuse-jar rationale.
# Usage: bash scripts/deploy-and-run.sh [--reset] [--restart-infra] [--file] [--no-cache]
#   [--reset-only-db] [--with-tests] [--from-scratch] [--prune-all]
#   --reset          wipe DB/MinIO volumes, then rebuild
#   --restart-infra  restart infra containers only (no rebuild)
#   --file           filtered console output + full log to /tmp/deploy.log
#   --no-cache       force rebuild ignoring Docker layer cache
#   --reset-only-db  truncate app tables (reset-clean.sql) before starting the app, via reset.sh
#   --with-tests     also run unit+integration tests as part of the build-and-test.sh reuse step
#                    (default: build only, no tests, for deploy speed)
#   --from-scratch   skip the build-and-test.sh reuse step and build the full multi-stage
#                    Dockerfile in complete isolation instead (the old, pre-reuse behavior)
#   --prune-all      ALSO run `docker container prune -f` and `docker volume prune -f` after the
#                    build -- HOST-WIDE, not scoped to this app, will remove any other stopped
#                    container / unused volume on the machine. Opt-in only, never automatic -- see
#                    scripts/ci/DECISIONS.md ADR-001 for the incident that made this explicit.
# Uses: bash, docker buildx, docker compose, scripts/build-and-test.sh (unless --from-scratch),
#   scripts/deploy-and-run/reset.sh (with --reset-only-db).
# Env: NETWORK (default advertisement), DB_CONTAINER (advertisement-db), MINIO_CONTAINER
#   (advertisement-minio), APP_CONTAINER (marketplace-app), APP_IMAGE (marketplace-app), DB_PORT
#   (5432), MINIO_PORT (9000), MINIO_CONSOLE_PORT (9001), APP_PORT (8081), DB_VOLUME
#   (advertisement_postgres_data), MINIO_VOLUME (advertisement_minio_data) -- all overridable, used
#   by scripts/ci/entrypoint.sh for its isolated e2e stack. DB_NAME/DB_USER/DB_PASSWORD/
#   S3_ACCESS_KEY/S3_SECRET_KEY/S3_BUCKET/S3_REGION fall back to the repo-root .env, then a
#   hardcoded default.
# Input: repo source, .env (DB_*/S3_* fallback defaults -- NOT POSTGRES_IMAGE: this script's own
#   Postgres container always hardcodes postgres:15-alpine, unlike docker-compose.db.yml which
#   does honor POSTGRES_IMAGE), the shared maven-cache Docker volume (unless --from-scratch).
# Outputs: running marketplace-app container on APP_PORT; scripts/deploy-and-run/marketplace-app.jar
#   (extracted from maven-cache, gitignored) unless --from-scratch.
# Returns: 0 on success, non-zero on build/startup failure.
# ────────────────────────────────────────────────────────────────────────────
set -e

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
LOG=/tmp/deploy.log

# Load shared credential/port defaults from the repo-root .env (also read natively by
# scripts/deploy-and-run/docker-compose*.yml and integration-tests' Testcontainers) into ENV_*-prefixed
# vars -- NOT exported/sourced directly, so an already-exported override (e.g. DB_PORT=15432 from
# scripts/ci/entrypoint.sh's isolated e2e stack) is never clobbered. Used only as the fallback
# default below, same precedence every other var here already has.
if [ -f "$ROOT/.env" ]; then
  while IFS='=' read -r _env_key _env_value; do
    [[ "$_env_key" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]] || continue
    printf -v "ENV_$_env_key" '%s' "${_env_value%$'\r'}"
  done < <(grep -v '^\s*#' "$ROOT/.env" | grep -v '^\s*$')
fi

NETWORK="${NETWORK:-advertisement}"
DB_CONTAINER="${DB_CONTAINER:-advertisement-db}"
MINIO_CONTAINER="${MINIO_CONTAINER:-advertisement-minio}"
APP_CONTAINER="${APP_CONTAINER:-marketplace-app}"
APP_IMAGE="${APP_IMAGE:-marketplace-app}"
DB_PORT="${DB_PORT:-${ENV_DB_PORT:-5432}}"
MINIO_PORT="${MINIO_PORT:-${ENV_S3_PORT:-9000}}"
MINIO_CONSOLE_PORT="${MINIO_CONSOLE_PORT:-9001}"
APP_PORT="${APP_PORT:-8081}"
DB_VOLUME="${DB_VOLUME:-advertisement_postgres_data}"
MINIO_VOLUME="${MINIO_VOLUME:-advertisement_minio_data}"
DB_NAME="${DB_NAME:-${ENV_DB_NAME:-experiments}}"
DB_USER="${DB_USER:-${ENV_DB_USER:-experiments_user}}"
DB_PASSWORD="${DB_PASSWORD:-${ENV_DB_PASSWORD:-experiments_user_password}}"
S3_ACCESS_KEY="${S3_ACCESS_KEY:-${ENV_S3_ACCESS_KEY:-admin}}"
S3_SECRET_KEY="${S3_SECRET_KEY:-${ENV_S3_SECRET_KEY:-admin12345}}"
S3_BUCKET="${S3_BUCKET:-${ENV_S3_BUCKET:-advertisement}}"
S3_REGION="${S3_REGION:-${ENV_S3_REGION:-us-east-1}}"

# ── Parse args ────────────────────────────────────────────────────────────────
MODE="default"
FILE_MODE=false
NO_CACHE=false
RESET_ONLY_DB=false
PRUNE_ALL=false
WITH_TESTS=false
FROM_SCRATCH=false
for arg in "$@"; do
  case "$arg" in
    --reset)         MODE="reset" ;;
    --restart-infra) MODE="restart-infra" ;;
    --file)          FILE_MODE=true ;;
    --no-cache)      NO_CACHE=true ;;
    --reset-only-db) RESET_ONLY_DB=true ;;
    --prune-all)     PRUNE_ALL=true ;;
    --with-tests)    WITH_TESTS=true ;;
    --from-scratch)  FROM_SCRATCH=true ;;
  esac
done

if $FILE_MODE; then
  trap '_rc=$?; echo ""; echo "=== FAILED (exit $_rc) ==="; echo "App logs:"; docker logs --tail=40 "$APP_CONTAINER" 2>/dev/null; echo "Full log: $LOG"; exit $_rc' ERR
else
  trap '_rc=$?; echo ""; echo "=== FAILED (exit $_rc) ==="; docker logs --tail=20 "$APP_CONTAINER" 2>/dev/null; exit $_rc' ERR
fi

# ── Helper: pull image if not present locally ─────────────────────────────────
pull_if_missing() {
  local image="$1"
  if ! docker image inspect "$image" >/dev/null 2>&1; then
    echo "Pulling $image..."
    docker pull "$image"
  else
    echo "Image $image already present."
  fi
}

# ── Helper: ensure container is running ──────────────────────────────────────
ensure_running() {
  local name="$1"; shift
  local run_cmd=("$@")

  if docker container inspect "$name" >/dev/null 2>&1; then
    local status
    status=$(docker container inspect -f '{{.State.Status}}' "$name")
    if [ "$status" = "running" ]; then
      echo "Container $name already running."
    else
      echo "Starting $name..."
      docker start "$name"
    fi
  else
    echo "Creating $name..."
    "${run_cmd[@]}"
  fi
}

# ── Helper: wait for DB ───────────────────────────────────────────────────────
wait_for_db() {
  echo "Waiting for DB..."
  until docker exec "$DB_CONTAINER" pg_isready -U "$DB_USER" -d "$DB_NAME" -q 2>/dev/null; do
    sleep 1
  done
  echo "DB ready."
}

# ── Helper: wait for MinIO ────────────────────────────────────────────────────
wait_for_minio() {
  echo "Waiting for MinIO..."
  until curl -s --max-time 3 "http://localhost:$MINIO_PORT/minio/health/live" >/dev/null 2>&1; do
    sleep 1
  done
  echo "MinIO ready."
}

# ── Helper: ensure MinIO bucket exists ───────────────────────────────────────
configure_minio() {
  echo "Configuring MinIO bucket..."
  docker rm -f minio-init 2>/dev/null || true
  docker run --rm --network "$NETWORK" --entrypoint /bin/sh minio/mc:latest -c "
    mc alias set local http://$MINIO_CONTAINER:9000 $S3_ACCESS_KEY $S3_SECRET_KEY
    mc mb --ignore-existing local/$S3_BUCKET
    mc anonymous set public local/$S3_BUCKET
    echo 'Bucket OK.'
  "
}

# ── Helper: wipe DB/MinIO/app containers and volumes ─────────────────────────
reset_infra() {
  echo "Resetting all containers and volumes..."
  docker rm -f "$DB_CONTAINER" "$MINIO_CONTAINER" "$APP_CONTAINER" 2>/dev/null || true
  docker volume rm "$DB_VOLUME" "$MINIO_VOLUME" 2>/dev/null || true
}

# ── Helper: bring up DB + MinIO and wait until both are ready ────────────────
start_infra() {
  docker network create "$NETWORK" 2>/dev/null || true

  pull_if_missing "postgres:15-alpine"
  pull_if_missing "minio/minio:latest"
  pull_if_missing "minio/mc:latest"

  ensure_running "$DB_CONTAINER" \
    docker run -d --name "$DB_CONTAINER" --network "$NETWORK" \
      -p "$DB_PORT":5432 \
      -e POSTGRES_DB="$DB_NAME" \
      -e POSTGRES_USER="$DB_USER" \
      -e POSTGRES_PASSWORD="$DB_PASSWORD" \
      -v "$DB_VOLUME":/var/lib/postgresql/data \
      postgres:15-alpine

  ensure_running "$MINIO_CONTAINER" \
    docker run -d --name "$MINIO_CONTAINER" --network "$NETWORK" \
      -p "$MINIO_PORT":9000 -p "$MINIO_CONSOLE_PORT":9001 \
      -e MINIO_ROOT_USER="$S3_ACCESS_KEY" \
      -e MINIO_ROOT_PASSWORD="$S3_SECRET_KEY" \
      -v "$MINIO_VOLUME":/data \
      minio/minio:latest server /data --console-address ":9001"

  wait_for_db
  wait_for_minio
  configure_minio
}

# ── Step 1: Infra ─────────────────────────────────────────────────────────────
echo ""
echo "=== Step 1: Infrastructure ==="

if [ "$MODE" = "reset" ]; then
  reset_infra
fi

if [ "$MODE" = "restart-infra" ]; then
  echo "Restarting infra containers (volumes preserved)..."
  docker rm -f "$DB_CONTAINER" "$MINIO_CONTAINER" 2>/dev/null || true
fi

start_infra

if $RESET_ONLY_DB; then
  echo "Resetting database (reset-clean.sql)..."
  DB_NAME="$DB_NAME" DB_USER="$DB_USER" bash "$ROOT/scripts/deploy-and-run/reset.sh" --container "$DB_CONTAINER"
fi

# ── Step 1.5: reuse build-and-test.sh's shared jar (skipped with --from-scratch) ──────────────
if ! $FROM_SCRATCH; then
  echo ""
  echo "=== Step 1.5: Build via build-and-test.sh (shared maven-cache jar) ==="
  BUILD_AND_TEST_FLAGS=(--no-unit --no-integration)
  $WITH_TESTS && BUILD_AND_TEST_FLAGS=(--unit --integration)
  # Distinct container name (not build-and-test.sh's own default) -- this call can run
  # concurrently with a caller-level direct build-and-test.sh invocation (e.g.
  # scripts/run-all-tests.sh runs both from one top-level command), and Docker container names
  # must be unique -- see scripts/build-and-test/run.sh's own Env doc for why.
  BUILD_CONTAINER_NAME="advertisement-build-only-deploy" bash "$ROOT/scripts/build-and-test.sh" "${BUILD_AND_TEST_FLAGS[@]}"

  echo "Extracting marketplace-app.jar from the shared maven-cache volume..."
  docker rm -f jar-extract-tmp 2>/dev/null || true
  docker run -d --name jar-extract-tmp --entrypoint sleep -v maven-cache:/root/.m2 alpine:latest infinity >/dev/null
  docker cp jar-extract-tmp:/root/.m2/artifacts/marketplace-app.jar "$ROOT/scripts/deploy-and-run/marketplace-app.jar"
  docker rm -f jar-extract-tmp >/dev/null
fi

# ── Step 2: Build ─────────────────────────────────────────────────────────────
echo ""
echo "=== Step 2: Build image ==="
source "$ROOT/scripts/ensure-docker-plugins.sh"
ensure_buildx
docker rm -f "$APP_CONTAINER" 2>/dev/null || true

BUILD_FLAGS=""
$NO_CACHE && BUILD_FLAGS="--no-cache"

if $FROM_SCRATCH; then
  DOCKERFILE_PATH="$ROOT/Dockerfile"
  BUILD_CONTEXT="$ROOT"
else
  DOCKERFILE_PATH="$ROOT/scripts/deploy-and-run/Dockerfile"
  BUILD_CONTEXT="$ROOT/scripts/deploy-and-run"
fi

if $FILE_MODE; then
  docker build --progress=plain $BUILD_FLAGS -f "$DOCKERFILE_PATH" -t "$APP_IMAGE" "$BUILD_CONTEXT" 2>&1 \
    | tee "$LOG" \
    | grep --line-buffered -E "^Step [0-9]+|^#[0-9]+ |Building .+\[[0-9]+/[0-9]+\]|BUILD (SUCCESS|FAILURE)|=== |ERROR|Successfully built"
else
  docker build $BUILD_FLAGS -f "$DOCKERFILE_PATH" -t "$APP_IMAGE" "$BUILD_CONTEXT"
fi

# Only dangling (untagged) images are pruned automatically -- by definition unreferenced by any
# tag/container, so this can never touch another stack's active image.
docker image prune -f

if $PRUNE_ALL; then
  echo ""
  echo "--prune-all: also removing every OTHER stopped container and unused volume on this" \
       "machine, not just this app's own (confirmed directly to affect an unrelated dev stack" \
       "if it happened to be stopped at the time -- see scripts/ci/DECISIONS.md ADR-001)."
  docker container prune -f
  docker volume prune -f
fi

# ── Helper: start the app container ───────────────────────────────────────────
start_app_container() {
  docker rm -f "$APP_CONTAINER" 2>/dev/null || true
  docker run -d --name "$APP_CONTAINER" --network "$NETWORK" \
    -p "$APP_PORT":8080 \
    -e SPRING_PROFILES_ACTIVE=prod \
    -e DB_HOST="$DB_CONTAINER" -e DB_PORT=5432 -e DB_NAME="$DB_NAME" \
    -e DB_USER="$DB_USER" -e DB_PASSWORD="$DB_PASSWORD" \
    -e S3_ENDPOINT="http://$MINIO_CONTAINER:9000" -e S3_BUCKET="$S3_BUCKET" \
    -e S3_ACCESS_KEY="$S3_ACCESS_KEY" -e S3_SECRET_KEY="$S3_SECRET_KEY" \
    -e S3_REGION="$S3_REGION" \
    -e S3_PUBLIC_URL="http://localhost:$MINIO_PORT/$S3_BUCKET" \
    -e APP_PUBLIC_URL="http://localhost:$APP_PORT" \
    -e JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=50.0 -XX:+UseG1GC" \
    "$APP_IMAGE"
}

# ── Helper: wait for the app to start. Returns 0 = started, 1 = Liquibase
# checksum mismatch (stale dev DB, auto-recoverable), 2 = any other failure.
wait_for_app() {
  echo "Waiting for application to start..."
  local end=$((SECONDS + 180))
  while true; do
    local logs
    logs=$(docker logs "$APP_CONTAINER" 2>&1)
    if grep -q "Started Application" <<<"$logs"; then
      return 0
    fi
    if grep -q "changesets check sum\|ValidationFailedException" <<<"$logs"; then
      return 1
    fi
    if [ "$(docker container inspect -f '{{.State.Running}}' "$APP_CONTAINER" 2>/dev/null)" != "true" ]; then
      return 2
    fi
    if [ $SECONDS -ge $end ]; then
      return 2
    fi
    sleep 2
  done
}

# ── Step 3: Start application ─────────────────────────────────────────────────
echo ""
echo "=== Step 3: Start application ==="
start_app_container

status=0
wait_for_app && status=0 || status=$?

if [ $status -eq 1 ]; then
  echo ""
  echo "Liquibase checksum mismatch detected -- a changeset was edited in place and the local" \
       "dev DB still has the old version applied (e.g. a removed FK constraint). This is" \
       "dev-only and always safe to auto-recover from: wiping DB/MinIO volumes and retrying once."
  reset_infra
  start_infra
  start_app_container
  status=0
  wait_for_app && status=0 || status=$?
  if [ $status -ne 0 ]; then
    echo "=== FAILED: startup failed again after auto-reset ==="
    docker logs --tail=50 "$APP_CONTAINER"
    exit 1
  fi
elif [ $status -ne 0 ]; then
  echo "=== FAILED: startup timed out ==="
  docker logs --tail=50 "$APP_CONTAINER"
  exit 1
fi

echo ""
echo "=== Application is ready at http://localhost:$APP_PORT ==="
