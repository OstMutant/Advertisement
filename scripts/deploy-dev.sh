#!/bin/bash
# Fast dev deploy: builds JAR locally and hot-swaps it into the running container.
# Typically ~3-4 min vs ~7-10 min for a full prod rebuild.
#
# Usage:
#   bash scripts/deploy-dev.sh
#
# Requires: infra (DB, MinIO) and marketplace-app container already running.
# Run scripts/deploy.sh once first if the container does not exist yet.
#
# Filtered output (key milestones only):
#   bash scripts/deploy-dev.sh 2>&1 | tee /tmp/deploy-dev.log | grep -E "BUILD|ERROR|Deploying|Restarting|Started"
#
# Stream full app log after deploy:
#   docker logs -f marketplace-app
set -e

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APP_CONTAINER="marketplace-app"
LOG=/tmp/deploy-dev.log

trap '_rc=$?; echo ""; echo "=== FAILED (exit $_rc) ==="; echo "Errors:"; grep -E "^\[ERROR\]" "$LOG" 2>/dev/null | grep -v "Help 1\|After correcting\|resume the build\|full stack trace\|Re-run Maven" | head -40; echo "Full log: $LOG"; exit $_rc' ERR

# ── Step 1: Check container ───────────────────────────────────────────────────
if ! docker inspect "$APP_CONTAINER" >/dev/null 2>&1; then
  echo "Container '$APP_CONTAINER' not found. Run scripts/deploy.sh first."
  exit 1
fi

# ── Step 2: Build JAR ────────────────────────────────────────────────────────
echo ""
echo "=== Building JAR (production bundle) ==="
cd "$ROOT"
./mvnw clean package -Pproduction -DskipTests 2>&1 | tee "$LOG" | grep --line-buffered -E "^\[ERROR\]|Building marketplace|BUILD SUCCESS|BUILD FAILURE"
if [ "${PIPESTATUS[0]}" -ne 0 ]; then exit 1; fi
echo "Build done."

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
