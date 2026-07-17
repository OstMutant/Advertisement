#!/bin/bash
# Full prod deploy: builds Docker image from scratch and starts all services on port 8081.
#
# Usage:
#   bash scripts/deploy.sh                      — full output to console (default)
#   bash scripts/deploy.sh --file               — filtered output + full log to /tmp/deploy.log
#   bash scripts/deploy.sh --no-cache           — force rebuild ignoring Docker layer cache
#   bash scripts/deploy.sh --reset              — wipe DB/MinIO volumes, then rebuild
#   bash scripts/deploy.sh --restart-infra      — restart infra containers only (no rebuild)
#   bash scripts/deploy.sh --reset-db           — truncate app tables (reset-clean.sql) before starting the app
#   bash scripts/deploy.sh --prune-all          — ALSO run `docker container prune -f` and
#                                                  `docker volume prune -f` after the build. These
#                                                  act HOST-WIDE, not scoped to this app -- they
#                                                  will remove any other stopped container / unused
#                                                  volume on the machine, dev or otherwise. Opt-in
#                                                  only, never run automatically -- see
#                                                  scripts/ci/DECISIONS.md ADR-001 for the incident that made
#                                                  this explicit instead of unconditional.
#
# Stream full app log after deploy:
#   docker logs -f marketplace-app
#
# All container/network/volume names and host ports are overridable via env vars (default shown
# above each), e.g. for a second, isolated stack run alongside the normal dev one:
#   NETWORK=ci-advertisement DB_CONTAINER=ci-advertisement-db MINIO_CONTAINER=ci-advertisement-minio \
#   APP_CONTAINER=ci-marketplace-app APP_IMAGE=marketplace-app-ci \
#   DB_PORT=15432 MINIO_PORT=19000 MINIO_CONSOLE_PORT=19001 APP_PORT=18081 \
#   DB_VOLUME=ci_advertisement_postgres_data MINIO_VOLUME=ci_advertisement_minio_data \
#   bash scripts/deploy.sh
# Used by scripts/ci/entrypoint.sh (improvement-059) for an isolated e2e run.
set -e

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LOG=/tmp/deploy.log

# Load shared credential/port defaults from the repo-root .env (also read natively by
# scripts/infra/docker-compose*.yml and integration-tests' Testcontainers) into ENV_*-prefixed
# vars -- NOT exported/sourced directly, so an already-exported override (e.g. DB_PORT=15432 from
# scripts/ci/entrypoint.sh's isolated e2e stack) is never clobbered. Used only as the fallback
# default below, same precedence every other var here already has.
if [ -f "$ROOT/.env" ]; then
  while IFS='=' read -r _env_key _env_value; do
    [[ "$_env_key" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]] || continue
    printf -v "ENV_$_env_key" '%s' "$_env_value"
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
RESET_DB=false
PRUNE_ALL=false
for arg in "$@"; do
  case "$arg" in
    --reset)         MODE="reset" ;;
    --restart-infra) MODE="restart-infra" ;;
    --file)          FILE_MODE=true ;;
    --no-cache)      NO_CACHE=true ;;
    --reset-db)      RESET_DB=true ;;
    --prune-all)     PRUNE_ALL=true ;;
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

# ── Step 1: Infra ─────────────────────────────────────────────────────────────
echo ""
echo "=== Step 1: Infrastructure ==="

if [ "$MODE" = "reset" ]; then
  echo "Resetting all containers and volumes..."
  docker rm -f "$DB_CONTAINER" "$MINIO_CONTAINER" "$APP_CONTAINER" 2>/dev/null || true
  docker volume rm "$DB_VOLUME" "$MINIO_VOLUME" 2>/dev/null || true
fi

if [ "$MODE" = "restart-infra" ]; then
  echo "Restarting infra containers (volumes preserved)..."
  docker rm -f "$DB_CONTAINER" "$MINIO_CONTAINER" 2>/dev/null || true
fi

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

if $RESET_DB; then
  echo "Resetting database (reset-clean.sql)..."
  docker cp "$ROOT/scripts/database/reset-clean.sql" "$DB_CONTAINER:/tmp/reset-clean.sql"
  docker exec "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -f /tmp/reset-clean.sql -q \
    && echo "Database reset."
fi

# ── Step 2: Build ─────────────────────────────────────────────────────────────
echo ""
echo "=== Step 2: Build image ==="
source "$ROOT/scripts/ensure-docker-plugins.sh"
ensure_buildx
docker rm -f "$APP_CONTAINER" 2>/dev/null || true

BUILD_FLAGS=""
$NO_CACHE && BUILD_FLAGS="--no-cache"

if $FILE_MODE; then
  docker build --progress=plain $BUILD_FLAGS -f "$ROOT/Dockerfile" -t "$APP_IMAGE" "$ROOT" 2>&1 \
    | tee "$LOG" \
    | grep --line-buffered -E "^Step [0-9]+|^#[0-9]+ |Building .+\[[0-9]+/[0-9]+\]|BUILD (SUCCESS|FAILURE)|=== |ERROR|Successfully built"
else
  docker build $BUILD_FLAGS -f "$ROOT/Dockerfile" -t "$APP_IMAGE" "$ROOT"
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

# ── Step 3: Start application ─────────────────────────────────────────────────
echo ""
echo "=== Step 3: Start application ==="
docker run -d --name "$APP_CONTAINER" --network "$NETWORK" \
  -p "$APP_PORT":8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e DB_HOST="$DB_CONTAINER" -e DB_PORT=5432 -e DB_NAME="$DB_NAME" \
  -e DB_USER="$DB_USER" -e DB_PASSWORD="$DB_PASSWORD" \
  -e S3_ENDPOINT="http://$MINIO_CONTAINER:9000" -e S3_BUCKET="$S3_BUCKET" \
  -e S3_ACCESS_KEY="$S3_ACCESS_KEY" -e S3_SECRET_KEY="$S3_SECRET_KEY" \
  -e S3_REGION="$S3_REGION" \
  -e S3_PUBLIC_URL="http://localhost:$MINIO_PORT/$S3_BUCKET" \
  -e JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=50.0 -XX:+UseG1GC" \
  "$APP_IMAGE"

echo "Waiting for application to start..."
end=$((SECONDS + 180))
while true; do
  if docker logs "$APP_CONTAINER" 2>&1 | grep -q "Started Application"; then
    break
  fi
  if [ $SECONDS -ge $end ]; then
    echo "=== FAILED: startup timed out ==="
    docker logs --tail=50 "$APP_CONTAINER"
    exit 1
  fi
  sleep 2
done

echo ""
echo "=== Application is ready at http://localhost:$APP_PORT ==="
