#!/bin/bash
# Fast dev deploy: builds JAR inside a Docker container and hot-swaps it into marketplace-app.
# Typically ~3-4 min vs ~7-10 min for a full prod rebuild.
# Maven cache persists in a named Docker volume (maven-cache) between runs.
#
# Usage:
#   bash scripts/deploy-dev.sh                -- full output to console (default)
#   bash scripts/deploy-dev.sh --file         -- filtered output + full log to /tmp/deploy-dev.log
#   bash scripts/deploy-dev.sh --reset-cache  -- clear Maven cache before building
#   bash scripts/deploy-dev.sh --reset-db     -- truncate app tables (reset-clean.sql) before hot-swap restart
#
# Stream full app log after deploy:
#   docker logs -f marketplace-app
set -e

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BUILD_IMAGE="advertisement-build-env"
LOG=/tmp/deploy-dev.log

FILE_MODE=false
RESET_CACHE=false
RESET_DB=false
for arg in "$@"; do
  [ "$arg" = "--file" ]         && FILE_MODE=true
  [ "$arg" = "--reset-cache" ]  && RESET_CACHE=true
  [ "$arg" = "--reset-db" ]     && RESET_DB=true
done

# ── Ensure build image exists ─────────────────────────────────────────────────
if ! docker image inspect "$BUILD_IMAGE" >/dev/null 2>&1; then
  echo "Building $BUILD_IMAGE image..."
  docker build -f "$ROOT/scripts/build-env/Dockerfile" -t "$BUILD_IMAGE" "$ROOT"
fi

# ── Clear Maven cache if requested ───────────────────────────────────────────
if $RESET_CACHE; then
  echo "=== Clearing Maven cache ==="
  docker volume rm maven-cache 2>/dev/null || true
fi

# ── Remove stale build container if exists ───────────────────────────────────
docker rm -f "$BUILD_IMAGE" 2>/dev/null || true

# ── Pipe sources + run build (output visible in Docker Desktop) ───────────────
if $FILE_MODE; then
  tar -czf - --exclude='*/target' --exclude='.git' -C "$ROOT" . \
    | docker run --rm -i \
        --name "$BUILD_IMAGE" \
        -v maven-cache:/root/.m2 \
        -v /var/run/docker.sock:/var/run/docker.sock \
        -e RESET_DB="$RESET_DB" \
        "$BUILD_IMAGE" \
        bash -c "tar -xzf - -C /app && bash /app/scripts/build-env/build.sh" \
    2>&1 | tee "$LOG" | grep --line-buffered -E "^\[ERROR\]|^=== |BUILD SUCCESS|BUILD FAILURE|Started Application"
else
  tar -czf - --exclude='*/target' --exclude='.git' -C "$ROOT" . \
    | docker run --rm -i \
        --name "$BUILD_IMAGE" \
        -v maven-cache:/root/.m2 \
        -v /var/run/docker.sock:/var/run/docker.sock \
        -e RESET_DB="$RESET_DB" \
        "$BUILD_IMAGE" \
        bash -c "tar -xzf - -C /app && bash /app/scripts/build-env/build.sh"
fi
