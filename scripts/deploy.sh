#!/bin/bash
# Full prod deploy: builds Docker image from scratch and starts all services on port 8081.
#
# Usage:
#   bash scripts/deploy.sh                      — full output to console (default)
#   bash scripts/deploy.sh --file               — filtered output + full log to /tmp/deploy.log
#   bash scripts/deploy.sh --no-cache           — force rebuild ignoring Docker layer cache
#   bash scripts/deploy.sh --reset              — wipe DB/MinIO volumes, then rebuild
#   bash scripts/deploy.sh --restart-infra      — restart infra containers only (no rebuild)
#
# Stream full app log after deploy:
#   docker logs -f marketplace-app
set -e

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LOG=/tmp/deploy.log

NETWORK="advertisement"
DB_CONTAINER="advertisement-db"
MINIO_CONTAINER="advertisement-minio"
APP_CONTAINER="marketplace-app"

# ── Parse args ────────────────────────────────────────────────────────────────
MODE="default"
FILE_MODE=false
NO_CACHE=false
for arg in "$@"; do
  case "$arg" in
    --reset)         MODE="reset" ;;
    --restart-infra) MODE="restart-infra" ;;
    --file)          FILE_MODE=true ;;
    --no-cache)      NO_CACHE=true ;;
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
  until docker exec "$DB_CONTAINER" pg_isready -U experiments_user -d experiments -q 2>/dev/null; do
    sleep 1
  done
  echo "DB ready."
}

# ── Helper: wait for MinIO ────────────────────────────────────────────────────
wait_for_minio() {
  echo "Waiting for MinIO..."
  until curl -s --max-time 3 http://localhost:9000/minio/health/live >/dev/null 2>&1; do
    sleep 1
  done
  echo "MinIO ready."
}

# ── Helper: ensure MinIO bucket exists ───────────────────────────────────────
configure_minio() {
  echo "Configuring MinIO bucket..."
  docker rm -f minio-init 2>/dev/null || true
  docker run --rm --network "$NETWORK" --entrypoint /bin/sh minio/mc:latest -c "
    mc alias set local http://$MINIO_CONTAINER:9000 admin admin12345
    mc mb --ignore-existing local/advertisement
    mc anonymous set public local/advertisement
    echo 'Bucket OK.'
  "
}

# ── Step 1: Infra ─────────────────────────────────────────────────────────────
echo ""
echo "=== Step 1: Infrastructure ==="

if [ "$MODE" = "reset" ]; then
  echo "Resetting all containers and volumes..."
  docker rm -f "$DB_CONTAINER" "$MINIO_CONTAINER" "$APP_CONTAINER" 2>/dev/null || true
  docker volume rm advertisement_postgres_data advertisement_minio_data 2>/dev/null || true
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
    -p 5432:5432 \
    -e POSTGRES_DB=experiments \
    -e POSTGRES_USER=experiments_user \
    -e POSTGRES_PASSWORD=experiments_user_password \
    -v advertisement_postgres_data:/var/lib/postgresql/data \
    postgres:15-alpine

ensure_running "$MINIO_CONTAINER" \
  docker run -d --name "$MINIO_CONTAINER" --network "$NETWORK" \
    -p 9000:9000 -p 9001:9001 \
    -e MINIO_ROOT_USER=admin \
    -e MINIO_ROOT_PASSWORD=admin12345 \
    -v advertisement_minio_data:/data \
    minio/minio:latest server /data --console-address ":9001"

wait_for_db
wait_for_minio
configure_minio

# ── Step 2: Build ─────────────────────────────────────────────────────────────
echo ""
echo "=== Step 2: Build image ==="
docker rm -f "$APP_CONTAINER" 2>/dev/null || true

BUILD_FLAGS=""
$NO_CACHE && BUILD_FLAGS="--no-cache"

if $FILE_MODE; then
  docker build $BUILD_FLAGS -f "$ROOT/Dockerfile" -t marketplace-app "$ROOT" 2>&1 \
    | tee "$LOG" \
    | grep --line-buffered -E "^Step [0-9]+|^#[0-9]+ |Building .+\[[0-9]+/[0-9]+\]|BUILD (SUCCESS|FAILURE)|=== |ERROR|Successfully built"
else
  docker build $BUILD_FLAGS -f "$ROOT/Dockerfile" -t marketplace-app "$ROOT"
fi

docker image prune -f
docker container prune -f
docker volume prune -f

# ── Step 3: Start application ─────────────────────────────────────────────────
echo ""
echo "=== Step 3: Start application ==="
docker run -d --name "$APP_CONTAINER" --network "$NETWORK" \
  -p 8081:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e DB_HOST="$DB_CONTAINER" -e DB_PORT=5432 -e DB_NAME=experiments \
  -e DB_USER=experiments_user -e DB_PASSWORD=experiments_user_password \
  -e S3_ENDPOINT="http://$MINIO_CONTAINER:9000" -e S3_BUCKET=advertisement \
  -e S3_ACCESS_KEY=admin -e S3_SECRET_KEY=admin12345 \
  -e S3_REGION=us-east-1 \
  -e S3_PUBLIC_URL=http://localhost:9000/advertisement \
  -e JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=50.0 -XX:+UseG1GC" \
  marketplace-app

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
echo "=== Application is ready at http://localhost:8081 ==="
