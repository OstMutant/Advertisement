#!/bin/bash
set -e

ROOT="$(cd "$(dirname "$0")/.." && pwd)"

DB_CONTAINER="advertisement-db-1"
MINIO_CONTAINER="advertisement-minio-1"
APP_CONTAINER="advertisement-app"

# ── Parse args ────────────────────────────────────────────────────────────────
MODE="default"
for arg in "$@"; do
  case "$arg" in
    --reset)         MODE="reset" ;;
    --restart-infra) MODE="restart-infra" ;;
  esac
done

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

  if docker inspect "$name" >/dev/null 2>&1; then
    local status
    status=$(docker inspect -f '{{.State.Status}}' "$name")
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
  docker run --rm --network host --entrypoint /bin/sh minio/mc:latest -c "
    mc alias set local http://localhost:9000 admin admin12345
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

pull_if_missing "postgres:15-alpine"
pull_if_missing "minio/minio:latest"
pull_if_missing "minio/mc:latest"

ensure_running "$DB_CONTAINER" \
  docker run -d --name "$DB_CONTAINER" --network host \
    -e POSTGRES_DB=experiments \
    -e POSTGRES_USER=experiments_user \
    -e POSTGRES_PASSWORD=experiments_user_password \
    -v advertisement_postgres_data:/var/lib/postgresql/data \
    postgres:15-alpine

ensure_running "$MINIO_CONTAINER" \
  docker run -d --name "$MINIO_CONTAINER" --network host \
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
docker build -f "$ROOT/Dockerfile" -t advertisement-app "$ROOT"

# ── Step 3: Start application ─────────────────────────────────────────────────
echo ""
echo "=== Step 3: Start application ==="
docker run -d --name "$APP_CONTAINER" --network host \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e DB_HOST=localhost -e DB_PORT=5432 -e DB_NAME=experiments \
  -e DB_USER=experiments_user -e DB_PASSWORD=experiments_user_password \
  -e S3_ENDPOINT=http://localhost:9000 -e S3_BUCKET=advertisement \
  -e S3_ACCESS_KEY=admin -e S3_SECRET_KEY=admin12345 \
  -e S3_REGION=us-east-1 \
  -e S3_PUBLIC_URL=http://localhost:9000/advertisement \
  advertisement-app

echo "Waiting for application to start..."
end=$((SECONDS + 180))
while true; do
  if docker logs "$APP_CONTAINER" 2>&1 | grep -q "Started Application"; then
    break
  fi
  if [ $SECONDS -ge $end ]; then
    echo "ERROR: startup timed out"
    docker logs --tail=50 "$APP_CONTAINER"
    exit 1
  fi
  sleep 2
done

echo ""
echo "=== Application is ready at http://localhost:8080 ==="
