#!/bin/bash
# ── Header ──────────────────────────────────────────────────────────────────
# Description: Runs INSIDE the build-and-test Docker container (JDK 25). Always builds
#   the full reactor and refreshes marketplace-app's JAR in the shared volume, regardless of who
#   called it -- Maven's own incremental compilation makes a no-op rebuild cheap either way. Also
#   optionally runs unit/integration tests -- see Env below.
# Usage: not invoked directly -- tar-piped and run via `docker run` by an external caller.
# Uses: bash, mvn, flock.
# Env:
#   Set automatically by the caller (never exported directly by a user):
#     RUN_UNIT (true/false, default false) -- also runs plain unit tests after the build.
#     RUN_INTEGRATION (true/false, default false) -- also runs Testcontainers-based integration
#       tests (needs docker.sock mounted in -- Testcontainers itself creates a sibling Postgres
#       container).
#   May be exported directly in the calling shell if needed:
#     TESTCONTAINERS_RYUK_DISABLED / INTEGRATION_TESTS_POSTGRES_FIXED_PORT -- sandbox-only
#       Testcontainers workarounds, passed through only if already set.
# Input: tar-piped repo source at /app; the shared maven-cache Docker volume at /root/.m2.
# Outputs: fresh jars for every module in the shared /root/.m2 (library modules installed;
#   marketplace-app's own JAR copied to /root/.m2/artifacts/marketplace-app.jar). RUN_UNIT/
#   RUN_INTEGRATION=true -- Surefire reports under the respective module's target/surefire-reports.
# Returns: 0 on success, non-zero on build/test failure.
# ────────────────────────────────────────────────────────────────────────────
set -e

ROOT=/app

trap '_rc=$?; echo ""; echo "=== FAILED (exit $_rc) ==="; exit $_rc' ERR

# ── Serialize concurrent mvn invocations against the shared .m2 volume ────────
# The lock file lives inside /root/.m2 itself -- the same named Docker volume mounted into
# every container instance that runs this script, so the lock is visible across all of them,
# not just within one container. A second concurrent caller blocks here until the first
# finishes; Maven's own incremental compilation then makes that second call cheap.
MVN_LOCK=/root/.m2/.build.lock
ARTIFACT_DIR=/root/.m2/artifacts
ARTIFACT="$ARTIFACT_DIR/marketplace-app.jar"

# ── Build: always the full reactor, always refreshes the shared artifact ─────
echo ""
echo "=== Building (full reactor) ==="
cd "$ROOT"
mkdir -p "$ARTIFACT_DIR"
flock "$MVN_LOCK" -c "./mvnw install -DskipTests"

JAR=$(ls "$ROOT/marketplace-app/target/"*.jar 2>/dev/null | grep -v '\.original$' | head -1)
if [ -n "$JAR" ]; then
  cp "$JAR" "$ARTIFACT"
  echo "marketplace-app.jar refreshed in the shared volume."
fi
echo ""
echo "=== Build done ==="

# ── Unit tests: plain JUnit, no Docker needed ─────────────────────────────────
if [ "$RUN_UNIT" = "true" ]; then
  echo ""
  echo "=== Running unit tests ==="
  flock "$MVN_LOCK" -c "./mvnw -pl query-lib,marketplace-app,marketplace-orchestrator test"
fi

# ── Integration tests: Testcontainers spins up its own Postgres via docker.sock ──
if [ "$RUN_INTEGRATION" = "true" ]; then
  echo ""
  echo "=== Running integration tests ==="
  flock "$MVN_LOCK" -c "./mvnw -pl integration-tests test"
fi
