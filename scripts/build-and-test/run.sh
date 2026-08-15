#!/bin/bash
# ── Header ──────────────────────────────────────────────────────────────────
# Description: Builds the whole reactor (every library module + marketplace-app's own JAR) into a
#   container-isolated Maven repository, so later test suites find everything already fresh
#   instead of triggering a redundant install mid-suite. Runs inside the build-and-test container
#   so it works even without a local JDK. Optionally also runs unit and/or integration tests
#   inside that same container (defaults from scripts/build-and-test/build-and-test.properties).
#   Image: advertisement-build-env. Container (while running): advertisement-build-only -- attach
#   with `docker exec -it advertisement-build-only bash` to inspect a run in progress.
# Usage: bash scripts/build-and-test.sh [--reset-cache] [--rebuild-image] [--unit|--no-unit] [--integration|--no-integration]
#   --reset-cache      wipe the shared maven-cache volume before building (re-downloads everything)
#   --rebuild-image    force-rebuild the build-and-test image even if one already exists
#   --unit/--no-unit               override build-and-test.properties' unit= default for this run
#   --integration/--no-integration override build-and-test.properties' integration= default for this run
# Uses: bash, docker, tar.
# Env: None directly; TESTCONTAINERS_RYUK_DISABLED / INTEGRATION_TESTS_POSTGRES_FIXED_PORT are
#   passed through into the container if already set in the caller's own environment (sandbox-only
#   Testcontainers workarounds, see scripts/CLAUDE.md).
# Input: repo source; scripts/build-and-test/build-and-test.properties (unit=/integration= defaults).
# Outputs: fresh jars for the whole reactor in the shared maven-cache Docker volume (never the
#   host's own ~/.m2 -- see scripts/build-and-test/README.md); marketplace-app.jar refreshed at
#   /root/.m2/artifacts/marketplace-app.jar inside that same volume. With unit/integration enabled,
#   Surefire reports under each module's own target/surefire-reports (inside the container only --
#   not copied back to the host). Prunes dangling Docker images after every run.
# Returns: 0 on success, non-zero on install/test failure.
# ────────────────────────────────────────────────────────────────────────────
set -e

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
BUILD_IMAGE="advertisement-build-env"
DOCKERFILE="$ROOT/scripts/build-and-test/Dockerfile"
PROPS_FILE="$ROOT/scripts/build-and-test/build-and-test.properties"

# ── Defaults from build-and-test.properties ──────────────────────────────────────
RUN_UNIT="$(grep '^unit=' "$PROPS_FILE" | cut -d= -f2)"
RUN_INTEGRATION="$(grep '^integration=' "$PROPS_FILE" | cut -d= -f2)"

RESET_CACHE=false
REBUILD_IMAGE=false
for arg in "$@"; do
  case "$arg" in
    --reset-cache)      RESET_CACHE=true ;;
    --rebuild-image)    REBUILD_IMAGE=true ;;
    --unit)             RUN_UNIT=true ;;
    --no-unit)          RUN_UNIT=false ;;
    --integration)      RUN_INTEGRATION=true ;;
    --no-integration)   RUN_INTEGRATION=false ;;
  esac
done

# ── Clear Maven cache if requested ───────────────────────────────────────────
if $RESET_CACHE; then
  echo "=== Clearing Maven cache ==="
  docker volume rm maven-cache 2>/dev/null || true
fi

# ── Ensure build image exists and is current (rebuilds if Dockerfile changed since last build,
# or unconditionally with --rebuild-image) ────────────────────────────────────
if $REBUILD_IMAGE || ! docker image inspect "$BUILD_IMAGE" >/dev/null 2>&1; then
  echo "Building $BUILD_IMAGE image..."
  docker build -f "$DOCKERFILE" -t "$BUILD_IMAGE" "$ROOT"
else
  IMAGE_CREATED_EPOCH=$(date -d "$(docker image inspect -f '{{.Created}}' "$BUILD_IMAGE")" +%s)
  DOCKERFILE_EPOCH=$(date -r "$DOCKERFILE" +%s)
  if [ "$DOCKERFILE_EPOCH" -gt "$IMAGE_CREATED_EPOCH" ]; then
    echo "Dockerfile changed since last build — rebuilding $BUILD_IMAGE image..."
    docker build -f "$DOCKERFILE" -t "$BUILD_IMAGE" "$ROOT"
  fi
fi

# ── docker.sock only when integration tests need Testcontainers to reach the Docker daemon --
# least privilege: never mounted for a build/unit-only run ────────────────────
DOCKER_SOCK_MOUNT=()
if [ "$RUN_INTEGRATION" = "true" ]; then
  DOCKER_SOCK_MOUNT=(-v /var/run/docker.sock:/var/run/docker.sock)
fi

# ── Sandbox-only Testcontainers workarounds, passed through only if already set ──
SANDBOX_ENV=()
[ -n "$TESTCONTAINERS_RYUK_DISABLED" ] && SANDBOX_ENV+=(-e "TESTCONTAINERS_RYUK_DISABLED=$TESTCONTAINERS_RYUK_DISABLED")
[ -n "$INTEGRATION_TESTS_POSTGRES_FIXED_PORT" ] && SANDBOX_ENV+=(-e "INTEGRATION_TESTS_POSTGRES_FIXED_PORT=$INTEGRATION_TESTS_POSTGRES_FIXED_PORT")

# ── Pipe sources + build (+ optional unit/integration tests) ─────────────────
# Excludes generated/build-artifact paths not needed for a build -- on Windows, tar (via WSL) has
# been observed unable to read some of these (Permission denied, likely a live process holding
# them open), so excluding them also avoids that failure, not just extra bytes.
tar -czf - --exclude='*/target' --exclude='.git' \
  --exclude='marketplace-app/src/main/frontend/generated' --exclude='docs/ai/adr-index.md' \
  -C "$ROOT" . \
  | docker run --rm -i \
      --name advertisement-build-only \
      -v maven-cache:/root/.m2 \
      "${DOCKER_SOCK_MOUNT[@]}" \
      -e RUN_UNIT="$RUN_UNIT" \
      -e RUN_INTEGRATION="$RUN_INTEGRATION" \
      "${SANDBOX_ENV[@]}" \
      "$BUILD_IMAGE" \
      bash -c "tar -xzf - -C /app && bash /app/scripts/build-and-test/build.sh"

# ── Keep the build environment clean: prune dangling images (never containers/volumes --
# those are host-wide operations, opt-in only, see scripts/CLAUDE.md) ─────────
docker image prune -f >/dev/null
