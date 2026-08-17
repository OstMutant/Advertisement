#!/bin/bash
# ── Header ──────────────────────────────────────────────────────────────────
# Description: Builds the whole reactor (every library module + marketplace-app's own JAR) into a
#   container-isolated Maven repository, so later test suites find everything already fresh
#   instead of triggering a redundant install mid-suite. Runs inside the build-and-test container
#   so it works even without a local JDK. Optionally also runs unit and/or integration tests
#   inside that same container (defaults from scripts/build-and-test/build-and-test.properties).
#   Image: advertisement-build-env. Container (while running): advertisement-build-only by default
#   -- attach with `docker exec -it advertisement-build-only bash` to inspect a run in progress.
#   Override via BUILD_CONTAINER_NAME (see Env below) when a caller needs to run this script
#   concurrently with another invocation of itself -- e.g. scripts/deploy-and-run/run.sh's own
#   internal call, which must not collide with a caller-level direct build-and-test.sh invocation
#   running at the same time (see scripts/DECISIONS.md).
# Usage: bash scripts/build.sh [--reset-cache] [--rebuild-image] [--unit|--no-unit]
#   [--integration|--no-integration] [--unit-test <module-or-class>]
#   [--integration-test <scenario-or-class>] [--sandbox] [--archunit-metrics]
#   --reset-cache        wipe the shared maven-cache volume before building (re-downloads everything)
#   --rebuild-image      force-rebuild the build-and-test image even if one already exists
#   --unit/--no-unit                 override build-and-test.properties' unit= default for this run
#   --integration/--no-integration   override build-and-test.properties' integration= default for this run
#   --unit-test <arg>                narrow RUN_UNIT to one module (query-lib/marketplace-app/
#                                     marketplace-orchestrator) or one test class by name
#   --integration-test <arg>         narrow RUN_INTEGRATION to one scenario (smoke) or one test
#                                     class by name
#   --sandbox                        this claude-dev sandbox's Testcontainers workarounds (Ryuk
#                                     disabled, fixed Postgres port) -- shorthand for exporting
#                                     TESTCONTAINERS_RYUK_DISABLED/INTEGRATION_TESTS_POSTGRES_FIXED_PORT
#                                     yourself; never needed on a normal developer machine
#   --archunit-metrics               off by default -- also runs marketplace-app's
#                                     ArchitectureMetricsExport (module-level coupling metrics for
#                                     architecture-map.html --with-archunit); several minutes even
#                                     on a warm build, run this occasionally, not on every call
# Uses: bash, docker, tar.
# Env: BUILD_CONTAINER_NAME (default advertisement-build-only) -- overridable so two invocations of
#   this script can run concurrently without a Docker container-name collision (Docker container
#   names must be unique; a fixed name means a second concurrent `docker run` with the same name
#   fails outright with "Conflict... name already in use", independent of and before the internal
#   flock below ever gets a chance to serialize anything). TESTCONTAINERS_RYUK_DISABLED /
#   INTEGRATION_TESTS_POSTGRES_FIXED_PORT are passed through into the container if already set in
#   the caller's own environment (sandbox-only Testcontainers workarounds, see scripts/CLAUDE.md)
#   -- same effect as passing --sandbox.
# Input: repo source; scripts/build-and-test/build-and-test.properties (unit=/integration= defaults).
# Outputs: fresh jars for the whole reactor in the shared maven-cache Docker volume (never the
#   host's own ~/.m2 -- see scripts/build-and-test/README.md); marketplace-app.jar refreshed at
#   /root/.m2/artifacts/marketplace-app.jar inside that same volume. With unit/integration enabled,
#   PASSED/FAILED summary on stdout plus Surefire reports copied to
#   scripts/build-and-test/reports/surefire/<module>/. With --archunit-metrics, also
#   scripts/build-and-test/reports/architecture-metrics.json. Prunes dangling Docker images after
#   every run.
# Returns: 0 on success, non-zero on install/test/precondition failure.
# ────────────────────────────────────────────────────────────────────────────
set -e

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
BUILD_IMAGE="advertisement-build-env"
BUILD_CONTAINER_NAME="${BUILD_CONTAINER_NAME:-advertisement-build-only}"
DOCKERFILE="$ROOT/scripts/build-and-test/Dockerfile"
PROPS_FILE="$ROOT/scripts/build-and-test/build-and-test.properties"
REPORT_DIR="$ROOT/scripts/build-and-test/reports"

# ── Defaults from build-and-test.properties ──────────────────────────────────────
RUN_UNIT="$(grep '^unit=' "$PROPS_FILE" | cut -d= -f2)"
RUN_INTEGRATION="$(grep '^integration=' "$PROPS_FILE" | cut -d= -f2)"

RESET_CACHE=false
REBUILD_IMAGE=false
SANDBOX=false
ARCHUNIT_METRICS=false
UNIT_TEST_ARG=""
INTEGRATION_TEST_ARG=""
NEXT=""
for arg in "$@"; do
  case "$NEXT" in
    unit-test)        UNIT_TEST_ARG="$arg"; NEXT=""; continue ;;
    integration-test)  INTEGRATION_TEST_ARG="$arg"; NEXT=""; continue ;;
  esac
  case "$arg" in
    --reset-cache)      RESET_CACHE=true ;;
    --rebuild-image)    REBUILD_IMAGE=true ;;
    --unit)             RUN_UNIT=true ;;
    --no-unit)          RUN_UNIT=false ;;
    --integration)      RUN_INTEGRATION=true ;;
    --no-integration)   RUN_INTEGRATION=false ;;
    --unit-test)        NEXT=unit-test ;;
    --integration-test) NEXT=integration-test ;;
    --sandbox)          SANDBOX=true ;;
    --archunit-metrics) ARCHUNIT_METRICS=true ;;
  esac
done

if $SANDBOX; then
  TESTCONTAINERS_RYUK_DISABLED=true
  INTEGRATION_TESTS_POSTGRES_FIXED_PORT=25432
  echo "Applying sandbox Docker workarounds (--sandbox): Ryuk disabled, fixed Postgres port 25432."
fi

# CI-environment guard: --sandbox and the sandbox-only env vars it sets are workarounds for this
# specific claude-dev sandbox's Docker networking limitations -- never needed, and never correct,
# on a real CI runner with normal Docker networking. Fail fast instead of letting someone
# copy-paste a --sandbox invocation into a future CI config without realizing why it's there.
if [ -n "$GITHUB_ACTIONS" ] && { $SANDBOX || [ -n "$TESTCONTAINERS_RYUK_DISABLED" ] || [ -n "$INTEGRATION_TESTS_POSTGRES_FIXED_PORT" ]; }; then
  echo "ERROR: --sandbox / TESTCONTAINERS_RYUK_DISABLED / INTEGRATION_TESTS_POSTGRES_FIXED_PORT" \
       "detected under GITHUB_ACTIONS. These are workarounds for this project's claude-dev sandbox" \
       "only -- a real CI runner has normal Docker networking and must never set them. Remove the" \
       "flag/env var from the CI config."
  exit 1
fi

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

  # Docker-daemon precheck: fail with a clear message here instead of letting the failure surface
  # deep inside Testcontainers' own (slower, less clear) connection probing.
  if ! docker info >/dev/null 2>&1; then
    echo "ERROR: Docker daemon not reachable. RUN_INTEGRATION requires a running Docker daemon" \
         "(Testcontainers starts a real Postgres container). Start Docker Desktop / dockerd and retry."
    exit 1
  fi
fi

# ── Sandbox-only Testcontainers workarounds, passed through only if already set ──
SANDBOX_ENV=()
[ -n "$TESTCONTAINERS_RYUK_DISABLED" ] && SANDBOX_ENV+=(-e "TESTCONTAINERS_RYUK_DISABLED=$TESTCONTAINERS_RYUK_DISABLED")
[ -n "$INTEGRATION_TESTS_POSTGRES_FIXED_PORT" ] && SANDBOX_ENV+=(-e "INTEGRATION_TESTS_POSTGRES_FIXED_PORT=$INTEGRATION_TESTS_POSTGRES_FIXED_PORT")

mkdir -p "$REPORT_DIR"

# ── Pipe sources + build (+ optional unit/integration tests) ─────────────────
# Excludes generated/build-artifact paths not needed for a build -- on Windows, tar (via WSL) has
# been observed unable to read some of these (Permission denied, likely a live process holding
# them open), so excluding them also avoids that failure, not just extra bytes.
# No --rm here (unlike before) -- Surefire reports are pulled out via `docker cp` after this
# container exits, the same workaround playwright/run.sh already uses, since a host-path bind
# mount (-v host:container) resolves against the wrong filesystem when the caller is itself a
# Docker container (e.g. this claude-dev sandbox) -- confirmed directly: an earlier -v attempt
# here left the mount empty. The container is removed explicitly right after the copy instead.
# Defensive rm before create -- a prior run that was interrupted or failed before reaching its own
# cleanup below (e.g. the container-name conflict this same variable was introduced to prevent)
# would otherwise leave a stale container behind that blocks every subsequent run with the same
# name, requiring manual `docker rm` to recover.
docker rm -f "$BUILD_CONTAINER_NAME" 2>/dev/null || true
set +e
tar -czf - --exclude='*/target' --exclude='.git' \
  --exclude='marketplace-app/src/main/frontend/generated' --exclude='docs/ai/adr-index.md' \
  -C "$ROOT" . \
  | docker run -i \
      --name "$BUILD_CONTAINER_NAME" \
      -v maven-cache:/root/.m2 \
      "${DOCKER_SOCK_MOUNT[@]}" \
      -e RUN_UNIT="$RUN_UNIT" \
      -e RUN_INTEGRATION="$RUN_INTEGRATION" \
      -e ARCHUNIT_METRICS="$ARCHUNIT_METRICS" \
      -e UNIT_TEST_ARG="$UNIT_TEST_ARG" \
      -e INTEGRATION_TEST_ARG="$INTEGRATION_TEST_ARG" \
      "${SANDBOX_ENV[@]}" \
      "$BUILD_IMAGE" \
      bash -c "tar -xzf - -C /app && bash /app/scripts/build-and-test/build.sh"
BUILD_EXIT=$?
set -e

docker cp "$BUILD_CONTAINER_NAME":/tmp/reports/. "$REPORT_DIR/" 2>/dev/null || true
docker rm "$BUILD_CONTAINER_NAME" >/dev/null 2>&1 || true

# ── Keep the build environment clean: prune dangling images (never containers/volumes --
# those are host-wide operations, opt-in only, see scripts/CLAUDE.md) ─────────
docker image prune -f >/dev/null

[ "$BUILD_EXIT" -eq 0 ] || exit $BUILD_EXIT
