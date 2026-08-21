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
# Usage: bash scripts/build-and-test/run.sh [--reset-cache] [--rebuild-image] [--unit|--no-unit]
#   [--integration|--no-integration] [--unit-test <module-or-class>]
#   [--integration-test <scenario-or-class>] [--sandbox] [--archunit-metrics] [--skip-vaadin]
#   (no flags)           build the full reactor, then run both unit and integration tests (the
#                         build-and-test.properties defaults, both true)
#   --reset-cache        wipe the shared maven-cache and vaadin-cache volumes before building
#                         (re-downloads every Maven dependency and Vaadin's own Node.js install)
#   --rebuild-image      force-rebuild the build-and-test image even if one already exists -- the
#                         image also auto-rebuilds without this flag whenever the Dockerfile's own
#                         mtime is newer than the existing image's creation time
#   --unit/--no-unit                 override build-and-test.properties' unit= default for this run
#   --integration/--no-integration   override build-and-test.properties' integration= default for this run
#   --unit-test <arg>                narrow RUN_UNIT to one module (query-lib/marketplace-app/
#                                     marketplace-orchestrator) or one test class by name
#   --integration-test <arg>         narrow RUN_INTEGRATION to one scenario (smoke) or one test
#                                     class by name
#   --no-sandbox                      disable the Docker nested-container Testcontainers
#                                     workarounds (Ryuk disabled, fixed Postgres port) that are ON
#                                     by default everywhere except GITHUB_ACTIONS -- confirmed
#                                     needed on a real Docker Desktop/WSL2 developer machine, not
#                                     just an isolated AI sandbox, whenever this container mounts
#                                     the host's docker.sock to run Testcontainers nested inside
#                                     itself; only skip it if your own setup doesn't hit this
#   --sandbox                        force the workarounds on even under GITHUB_ACTIONS (default
#                                     off there, since a real CI runner is not usually nested)
#   --archunit-metrics               off by default -- also runs marketplace-app's
#                                     ArchitectureMetricsExport (module-level coupling metrics for
#                                     architecture-map.html --with-archunit); several minutes even
#                                     on a warm build, run this occasionally, not on every call
#   --skip-vaadin                    off by default -- skips vaadin-maven-plugin's frontend bundle
#                                     (npm install + Vite, ~3-4 min) during the reactor install; use
#                                     for --unit/--integration/--archunit-metrics runs, never for a
#                                     plain cache-priming build or anything deploy-and-run.sh/e2e
#                                     relies on (see build.sh's own header for why)
# Uses: bash, docker, tar, grep, sed.
# Env: BUILD_CONTAINER_NAME (default advertisement-build-only) -- overridable so two invocations of
#   this script can run concurrently without a Docker container-name collision (Docker container
#   names must be unique; a fixed name means a second concurrent `docker run` with the same name
#   fails outright with "Conflict... name already in use", independent of and before the internal
#   flock below ever gets a chance to serialize anything). Also namespaces this run's own reports
#   under /reports/$BUILD_CONTAINER_NAME/ on the shared test-reports volume (forwarded into the
#   container as an env var too), so two concurrent invocations under different names -- e.g.
#   deploy-and-run.sh's own internal call, which already uses a distinct BUILD_CONTAINER_NAME for
#   the container-name reason above -- never collide wiping/writing each other's report data.
#   TESTCONTAINERS_RYUK_DISABLED /
#   INTEGRATION_TESTS_POSTGRES_FIXED_PORT are passed through into the container if already set in
#   the caller's own environment -- same effect as the default/--sandbox workarounds described
#   under Usage above. GITHUB_ACTIONS -- detected, never set by this script -- flips the
#   workarounds' own default from on to off (a real CI runner is not usually nested the same way a
#   local dev machine's Docker Desktop/WSL2 setup is); pass --sandbox explicitly to force them on
#   there too, if a given CI runner ever needs it.
# Input: root build files (pom.xml, mvnw, .mvn, lombok.config, .env), scripts/build-and-test/build.sh
#   itself (the container's own entry point), and each Maven module's own directory, per pom.xml's
#   <module> list -- not the whole repo (see the tar step's own comment below);
#   scripts/build-and-test/build-and-test.properties (unit=/integration= defaults, read on the host
#   side by this script, not tarred in).
# Outputs: fresh jars for the whole reactor in the shared maven-cache Docker volume (never the
#   host's own ~/.m2 -- see scripts/build-and-test/README.md); marketplace-app.jar refreshed at
#   /root/.m2/artifacts/marketplace-app.jar inside that same volume. Vaadin's own Node.js install
#   (unrelated to Maven) persists across runs in a second named volume, vaadin-cache:/root/.vaadin --
#   without it, every run without --skip-vaadin would re-download Node.js from scratch, since a
#   fresh build container has nothing outside its mounted volumes. With unit/integration enabled,
#   PASSED/FAILED summary on stdout plus Surefire reports copied to
#   scripts/build-and-test/reports/surefire/<module>/, and the full raw console log for whichever
#   phase ran copied to scripts/build-and-test/reports/logs/ (unit-tests.log/integration-tests.log)
#   -- persists the real failure detail past this run's own terminal output. Also always writes
#   scripts/build-and-test/reports/build-info.txt (BUILD_CONTAINER_NAME, timestamp, which of
#   RUN_UNIT/RUN_INTEGRATION/ARCHUNIT_METRICS/SKIP_VAADIN were set), so it's clear which invocation
#   produced a given set of reports. With --archunit-metrics, also
#   scripts/build-and-test/reports/architecture-metrics.json and
#   reports/logs/archunit-metrics.log. With --integration, also mirrors integration-tests/reports/
#   (run.log + surefire/) via its own separate `docker cp` -- keeps that directory fresh
#   regardless of whether integration tests ran through this script or integration-tests/run.sh's
#   own direct invocation. All of the above lands on the host via `docker cp` from a third named
#   volume, test-reports:/reports (wiped before every run, see the pre-run cleanup step below) --
#   build.sh writes reports directly there, never a WSL/Windows-drive path, so it's never subject
#   to the docker-desktop-bind-mounts issue documented in docs/ai/adr-index.md; the same volume can
#   also be inspected directly at any time without waiting for a full run + copy-out cycle, e.g.
#   `docker run --rm -v test-reports:/reports alpine cat /reports/logs/unit-tests.log`. Prunes
#   dangling Docker images after every run.
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
# Default on everywhere except GITHUB_ACTIONS -- confirmed needed on a real Docker Desktop/WSL2
# developer machine (not just an isolated AI sandbox) whenever this container's own nested
# Testcontainers run needs docker.sock mounted in; --sandbox/--no-sandbox below can still override
# either way.
if [ -n "$GITHUB_ACTIONS" ]; then
  SANDBOX=false
else
  SANDBOX=true
fi
ARCHUNIT_METRICS=false
SKIP_VAADIN=false
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
    --no-sandbox)       SANDBOX=false ;;
    --archunit-metrics) ARCHUNIT_METRICS=true ;;
    --skip-vaadin)       SKIP_VAADIN=true ;;
  esac
done

if $SANDBOX; then
  TESTCONTAINERS_RYUK_DISABLED=true
  INTEGRATION_TESTS_POSTGRES_FIXED_PORT=25432
  echo "Applying Docker nested-container workarounds (Ryuk disabled, fixed Postgres port 25432)." \
       "Pass --no-sandbox if your Docker setup doesn't need this."
fi

# ── Clear build caches if requested ──────────────────────────────────────────
if $RESET_CACHE; then
  echo "=== Clearing build caches (maven-cache, vaadin-cache) ==="
  docker volume rm maven-cache 2>/dev/null || true
  docker volume rm vaadin-cache 2>/dev/null || true
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

# No mkdir -p here -- the later `docker cp` (see below) creates its destination directory itself,
# so a raw bash `mkdir -p` this early against a WSL/Windows-drive host path was pure redundancy,
# vulnerable to the docker-desktop-bind-mounts issue documented in docs/ai/adr-index.md.

# ── Pipe sources + build (+ optional unit/integration tests) ─────────────────
# Tars only what this run actually needs inside the container: build.sh itself (the entry point
# the container executes below, via bash -c), the root build files, and each module's own
# directory -- never the whole repo (docs/, backlog/, playwright/, .claude/, etc. are never
# touched by build.sh). .env is included even though `mvn install` itself never reads it --
# integration-tests' own SharedEnvConfig reads it at test-runtime (POSTGRES_IMAGE for
# Testcontainers), walking up from the JVM's working directory; missing it doesn't fail the build,
# it fails every single integration test at startup with a hard-to-read NoClassDefFoundError/
# IllegalStateException (confirmed directly -- this was left out once, and it broke exactly this
# way). An include-list instead of an ever-growing exclude-list also
# structurally avoids a real, confirmed Windows/WSL failure: tar (via WSL) has been observed unable
# to read a file that another process (e.g. a `docker cp` from an unrelated script) still holds a
# lock on (Permission denied) -- a file outside this include-list simply never gets read, whatever
# it is. Module list is derived from the root pom.xml's own <module> entries so it can never drift
# out of sync with the real reactor (matching build.sh's own per-module loop for target-classes).
MODULES=$(grep -oE '<module>[^<]+</module>' "$ROOT/pom.xml" | sed -E 's#</?module>##g')

# No --rm here (unlike before) -- Surefire reports are pulled out via `docker cp` after this
# container exits, the same workaround playwright/run.sh already uses, since a host-path bind
# mount (-v host:container) resolves against the wrong filesystem when the caller is itself a
# Docker container (e.g. this claude-dev sandbox) -- confirmed directly: an earlier -v attempt
# here left the mount empty. The container is removed explicitly right after the copy instead.
# Defensive rm before create -- a prior run that was interrupted or failed before reaching its own
# cleanup below (e.g. the container-name conflict this same variable was introduced to prevent)
# would otherwise leave a stale container behind that blocks every subsequent run with the same
# name, requiring manual `docker rm` to recover.
# test-reports is a shared named Docker volume (never a WSL/Windows-drive path) that build.sh
# writes its reports into directly at /reports/$BUILD_CONTAINER_NAME -- wiped before every run so
# nothing stale from a previous run under this same container name lingers; everything under it
# gets freshly regenerated by whichever of RUN_UNIT/RUN_INTEGRATION/ARCHUNIT_METRICS is actually
# set this time, same "wipe the whole thing, simpler than tracking per-category overlap" choice
# clean.bat already makes for the host-side copies. Scoped to this run's own
# /reports/$BUILD_CONTAINER_NAME/ subfolder, never the whole volume -- a second, concurrent
# invocation under a different BUILD_CONTAINER_NAME (e.g. deploy-and-run.sh's own internal call,
# which already uses a distinct name to avoid a Docker container-name collision) writes to its own
# separate namespace in the same volume and must never wipe this one's in-progress data.
docker run --rm -v test-reports:/reports "$BUILD_IMAGE" bash -c "rm -rf /reports/$BUILD_CONTAINER_NAME" 2>/dev/null || true

docker rm -f "$BUILD_CONTAINER_NAME" 2>/dev/null || true
set +e
tar -czf - --exclude='*/target' --exclude='marketplace-app/src/main/frontend/generated' \
  -C "$ROOT" pom.xml mvnw .mvn lombok.config .env scripts/build-and-test/build.sh $MODULES \
  | docker run -i \
      --name "$BUILD_CONTAINER_NAME" \
      -v maven-cache:/root/.m2 \
      -v vaadin-cache:/root/.vaadin \
      -v test-reports:/reports \
      "${DOCKER_SOCK_MOUNT[@]}" \
      -e BUILD_CONTAINER_NAME="$BUILD_CONTAINER_NAME" \
      -e RUN_UNIT="$RUN_UNIT" \
      -e RUN_INTEGRATION="$RUN_INTEGRATION" \
      -e ARCHUNIT_METRICS="$ARCHUNIT_METRICS" \
      -e SKIP_VAADIN="$SKIP_VAADIN" \
      -e UNIT_TEST_ARG="$UNIT_TEST_ARG" \
      -e INTEGRATION_TEST_ARG="$INTEGRATION_TEST_ARG" \
      "${SANDBOX_ENV[@]}" \
      "$BUILD_IMAGE" \
      bash -c "tar -xzf - -C /app && bash /app/scripts/build-and-test/build.sh"
BUILD_EXIT=$?
set -e

# Only the default-named invocation (the one run-all-tests.sh/a plain caller triggers directly,
# which actually runs tests) lands in the shared scripts/build-and-test/reports/ -- a second,
# concurrent invocation under a different name (e.g. deploy-and-run.sh's own internal
# --no-unit --no-integration build) gets its own namespaced subfolder instead, so it never
# overwrites the real invocation's build-info.txt/reports with its own empty ones (confirmed
# real: exactly this collision happened before this fix, deploy's own no-op build-info.txt landed
# on top of the real test run's).
if [ "$BUILD_CONTAINER_NAME" = "advertisement-build-only" ]; then
  HOST_REPORT_DEST="$REPORT_DIR"
  HOST_LOGS_DEST="$ROOT/scripts/logs/build-and-test"
else
  HOST_REPORT_DEST="$REPORT_DIR/$BUILD_CONTAINER_NAME"
  HOST_LOGS_DEST="$ROOT/scripts/logs/build-and-test/$BUILD_CONTAINER_NAME"
fi
# No `mkdir -p` here -- `docker cp` creates the destination directory (and any missing
# intermediates) itself when it doesn't exist, so the raw bash `mkdir -p` was pure redundancy that
# cost every caller a real filesystem write against a WSL/Windows-drive host path -- confirmed
# directly to fail with "Permission denied" on a real Docker Desktop/WSL2 machine due to the
# docker-desktop-bind-mounts issue (see docs/ai/adr-index.md), which crashed the whole build under
# `set -e`. `docker cp` itself is daemon-mediated and not subject to that bug.
# Two separate copies -- LOGS_DIR (build-info.txt, logs/*.log -- raw process narration) and
# REPORTS_DIR (surefire/, it-mirror/, architecture-metrics.json -- actual test results) are
# separate top-level paths inside the container specifically so each can go to its own separate
# host destination here, without any exclusion logic.
docker cp "$BUILD_CONTAINER_NAME":/reports/logs/"$BUILD_CONTAINER_NAME"/. "$HOST_LOGS_DEST/" 2>/dev/null || true
docker cp "$BUILD_CONTAINER_NAME":/reports/"$BUILD_CONTAINER_NAME"/. "$HOST_REPORT_DEST/" 2>/dev/null || true
# Only attempted when integration tests actually ran -- otherwise there's nothing in the
# container's it-mirror to copy. Same reasoning as above -- no `mkdir -p` before this `docker cp`
# either. integration-tests/reports/ gets only the surefire/ result (it-mirror no longer contains a
# run.log of its own -- see build.sh); its matching log is not duplicated under a third location --
# it's already at $HOST_LOGS_DEST/integration-tests.log (copied above), no separate
# integration-tests-specific logs folder needed for one file.
if [ "$RUN_INTEGRATION" = "true" ]; then
  docker cp "$BUILD_CONTAINER_NAME":/reports/"$BUILD_CONTAINER_NAME"/it-mirror/. "$ROOT/integration-tests/reports/" 2>/dev/null || true
fi

# Always remove the container after the copies above -- no post-copy verification (removed per
# direct instruction: a timing-sensitive check was producing false failures under load even when
# the copy had genuinely succeeded, just slightly later than an arbitrary timeout allowed).
docker rm -f "$BUILD_CONTAINER_NAME" >/dev/null 2>&1 || true

# ── Keep the build environment clean: prune dangling images (never containers/volumes --
# those are host-wide operations, opt-in only, see scripts/CLAUDE.md) ─────────
docker image prune -f >/dev/null

[ "$BUILD_EXIT" -eq 0 ] || exit $BUILD_EXIT
