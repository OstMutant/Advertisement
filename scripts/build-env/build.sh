#!/bin/bash
# Runs INSIDE the advertisement-build-env Docker container (JDK 25 + Docker CLI).
# Called by deploy-dev.sh via: tar pipe | docker run | bash build.sh
# Output goes to docker run stdout — visible in Docker Desktop and console.
set -e

ROOT=/app
APP_CONTAINER="marketplace-app"
DB_CONTAINER="advertisement-db"

trap '_rc=$?; echo ""; echo "=== FAILED (exit $_rc) ==="; exit $_rc' ERR

# ── Step 0: Reset database (opt-in via RESET_DB=true) ────────────────────────
if [ "$RESET_DB" = "true" ]; then
  echo ""
  echo "=== Resetting database ==="
  if docker container inspect "$DB_CONTAINER" >/dev/null 2>&1; then
    docker cp "$ROOT/scripts/database/reset-clean.sql" "$DB_CONTAINER:/tmp/reset-clean.sql"
    docker exec "$DB_CONTAINER" psql -U experiments_user -d experiments -f /tmp/reset-clean.sql -q \
      && echo "Database reset."
  else
    echo "WARNING: DB container '$DB_CONTAINER' not found — skipping reset."
  fi
fi

# ── Step 1: Check marketplace-app container ──────────────────────────────────
if ! docker container inspect "$APP_CONTAINER" >/dev/null 2>&1; then
  echo "Container '$APP_CONTAINER' not found — running full deploy first..."
  bash "$ROOT/scripts/deploy.sh"
elif [ "$(docker container inspect -f '{{.State.Status}}' "$APP_CONTAINER")" != "running" ]; then
  echo "Starting $APP_CONTAINER..."
  docker start "$APP_CONTAINER"
  echo "Waiting for application to start..."
  end=$((SECONDS + 180))
  while true; do
    docker logs "$APP_CONTAINER" 2>&1 | grep -q "Started Application" && break
    [ $SECONDS -ge $end ] && echo "ERROR: startup timed out" && exit 1
    sleep 2
  done
fi

# ── Step 2: Build JAR ────────────────────────────────────────────────────────
echo ""
echo "=== Building JAR (production bundle) ==="
cd "$ROOT"
./mvnw clean package -DskipTests

# ── Step 3: Hot-swap JAR ──────────────────────────────────────────────────────
echo ""
echo "=== Deploying JAR to container ==="
JAR=$(ls "$ROOT/marketplace-app/target/"*.jar 2>/dev/null | head -1)
if [ -z "$JAR" ]; then
  echo "ERROR: No JAR found in marketplace-app/target/"
  exit 1
fi
docker cp "$JAR" "$APP_CONTAINER:/app/app.jar"

# ── Step 4: Restart and wait ──────────────────────────────────────────────────
echo ""
echo "=== Restarting container ==="
docker restart "$APP_CONTAINER"

RESTART_AT=$(date -u +%Y-%m-%dT%H:%M:%SZ)
echo "Waiting for application to start..."
end=$((SECONDS + 180))
while true; do
  if docker logs "$APP_CONTAINER" --since "$RESTART_AT" 2>&1 | grep -q "Started Application"; then
    break
  fi
  if [ $SECONDS -ge $end ]; then
    echo "ERROR: startup timed out"
    docker logs "$APP_CONTAINER" --since "$RESTART_AT" --tail=30
    exit 1
  fi
  sleep 2
done

echo ""
echo "=== Cleaning up Docker garbage ==="
docker image prune -f
docker container prune -f
docker volume prune -f

echo ""
echo "=== Application is ready at http://localhost:8081 ==="
