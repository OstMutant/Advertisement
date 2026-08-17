#!/bin/bash
# ── Header ──────────────────────────────────────────────────────────────────
# Description: Runs INSIDE the build-and-test Docker container (JDK 25). Always builds
#   the full reactor and refreshes marketplace-app's JAR in the shared volume, regardless of who
#   called it -- Maven's own incremental compilation makes a no-op rebuild cheap either way. Also
#   optionally runs unit/integration tests and an ArchUnit metrics export -- see Env below.
# Usage: not invoked directly -- tar-piped and run via `docker run` by an external caller.
# Uses: bash, mvn, flock.
# Env:
#   Set automatically by the caller (never exported directly by a user):
#     RUN_UNIT (true/false, default false) -- also runs plain unit tests after the build.
#     RUN_INTEGRATION (true/false, default false) -- also runs Testcontainers-based integration
#       tests (needs docker.sock mounted in -- Testcontainers itself creates a sibling Postgres
#       container).
#     ARCHUNIT_METRICS (true/false, default false) -- also runs marketplace-app's
#       ArchitectureMetricsExport (module-level coupling metrics for architecture-map.html
#       --with-archunit); several minutes even on a warm build, grouped sequentially with
#       RUN_UNIT (both touch marketplace-app's own target/), parallel-safe against
#       RUN_INTEGRATION only.
#     UNIT_TEST_ARG (default empty) -- narrows RUN_UNIT to one module (query-lib/marketplace-app/
#       marketplace-orchestrator) or one test class by name; empty runs all 3 modules.
#     INTEGRATION_TEST_ARG (default empty) -- narrows RUN_INTEGRATION to one scenario ("smoke") or
#       one test class by name; empty runs the whole integration-tests module.
#   May be exported directly in the calling shell if needed:
#     TESTCONTAINERS_RYUK_DISABLED / INTEGRATION_TESTS_POSTGRES_FIXED_PORT -- sandbox-only
#       Testcontainers workarounds, passed through only if already set.
# Input: tar-piped repo source at /app; the shared maven-cache Docker volume at /root/.m2.
# Outputs: fresh jars for every module in the shared /root/.m2 (library modules installed;
#   marketplace-app's own JAR copied to /root/.m2/artifacts/marketplace-app.jar); each module's own
#   target/classes also refreshed under /root/.m2/target-classes/<module>/, for scripts/sonar/run.sh
#   to mount and read directly instead of compiling locally. RUN_UNIT/
#   RUN_INTEGRATION=true -- PASSED/FAILED summary on stdout, failing test files listed; Surefire
#   reports copied to /tmp/reports/surefire/<module>/. ARCHUNIT_METRICS=true -- architecture-
#   metrics.json moved to /tmp/reports/architecture-metrics.json. Everything under /tmp/reports
#   reaches the host via run.sh's own `docker cp` after this container exits.
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

# ── Also refresh each module's own target/classes into the shared volume, for
# scripts/sonar/run.sh to mount and read directly (sonar.java.binaries needs a directory of
# .class files, not a jar) -- excludes integration-tests, which sonar-project.properties never
# scans (test-only module). ──────────────────────────────────────────────────
TARGET_CLASSES_DIR=/root/.m2/target-classes
mkdir -p "$TARGET_CLASSES_DIR"
for module in query-lib platform-commons audit-spring-boot-starter attachment-spring-boot-starter \
              user-spring-boot-starter advertisement-spring-boot-starter taxon-spring-boot-starter \
              provider-profile-spring-boot-starter marketplace-orchestrator marketplace-app; do
  if [ -d "$ROOT/$module/target/classes" ]; then
    rm -rf "$TARGET_CLASSES_DIR/$module"
    mkdir -p "$TARGET_CLASSES_DIR/$module"
    cp -r "$ROOT/$module/target/classes/." "$TARGET_CLASSES_DIR/$module/"
  fi
done

JAR=$(ls "$ROOT/marketplace-app/target/"*.jar 2>/dev/null | grep -v '\.original$' | head -1)
if [ -n "$JAR" ]; then
  cp "$JAR" "$ARTIFACT"
  echo "marketplace-app.jar refreshed in the shared volume."
fi
echo ""
echo "=== Build done ==="

# ── Unit tests: plain JUnit, no Docker needed ─────────────────────────────────
# No flock here (unlike the install step above) -- once ~/.m2 is installed, both this and the
# integration-test block below only *read* it, never write, so they're safe to run concurrently
# against each other. They touch different target/ dirs too (query-lib/marketplace-app/
# marketplace-orchestrator vs integration-tests), so no output collision either.
run_unit_tests() {
  UNIT_MODULES="query-lib,marketplace-app,marketplace-orchestrator"
  UNIT_TEST_FLAG=""
  if [ "$UNIT_TEST_ARG" = "query-lib" ] || [ "$UNIT_TEST_ARG" = "marketplace-app" ] || [ "$UNIT_TEST_ARG" = "marketplace-orchestrator" ]; then
    UNIT_MODULES="$UNIT_TEST_ARG"
  elif [ -n "$UNIT_TEST_ARG" ]; then
    UNIT_TEST_FLAG="-Dtest=${UNIT_TEST_ARG} -Dsurefire.failIfNoSpecifiedTests=false"
  fi

  ./mvnw -pl "$UNIT_MODULES" test $UNIT_TEST_FLAG > /tmp/unit-tests.log 2>&1
  UNIT_EXIT=$?

  mkdir -p /tmp/reports/surefire
  for m in query-lib marketplace-app marketplace-orchestrator; do
    if [ -d "$ROOT/$m/target/surefire-reports" ]; then
      mkdir -p "/tmp/reports/surefire/$m"
      cp -r "$ROOT/$m"/target/surefire-reports/* "/tmp/reports/surefire/$m/" 2>/dev/null || true
    fi
  done
  return $UNIT_EXIT
}

print_unit_summary() {
  cat /tmp/unit-tests.log
  echo ""
  if [ "$UNIT_EXIT" -eq 0 ]; then
    echo "===== UNIT TESTS PASSED ====="
  else
    echo "===== UNIT TESTS FAILED (exit $UNIT_EXIT) ====="
    echo "Failing tests, if any:"
    grep -rl "FAILED\|ERROR" /tmp/reports/surefire/*/*.txt 2>/dev/null | sed 's|.*/||'
  fi
}

# ── Integration tests: Testcontainers spins up its own Postgres via docker.sock ──
# -Dsurefire.excludedGroups= (empty) overrides integration-tests/pom.xml's default
# "testcontainers" exclusion -- without it, every real @Tag("testcontainers") repository test is
# silently skipped, same override integration-tests/run.sh already applies.
run_integration_tests() {
  INTEGRATION_TEST_FLAG=""
  if [ "$INTEGRATION_TEST_ARG" = "smoke" ]; then
    INTEGRATION_TEST_FLAG="-Dtest=PostgresContainerSmokeTest -Dsurefire.failIfNoSpecifiedTests=false"
  elif [ -n "$INTEGRATION_TEST_ARG" ]; then
    INTEGRATION_TEST_FLAG="-Dtest=${INTEGRATION_TEST_ARG} -Dsurefire.failIfNoSpecifiedTests=false"
  fi

  ./mvnw -pl integration-tests test -Dsurefire.excludedGroups= $INTEGRATION_TEST_FLAG > /tmp/integration-tests.log 2>&1
  INTEGRATION_EXIT=$?

  mkdir -p /tmp/reports/surefire/integration-tests
  cp -r "$ROOT"/integration-tests/target/surefire-reports/* /tmp/reports/surefire/integration-tests/ 2>/dev/null || true
  return $INTEGRATION_EXIT
}

print_integration_summary() {
  cat /tmp/integration-tests.log
  echo ""
  if [ "$INTEGRATION_EXIT" -eq 0 ]; then
    echo "===== INTEGRATION TESTS PASSED ====="
  else
    echo "===== INTEGRATION TESTS FAILED (exit $INTEGRATION_EXIT) ====="
    echo "Failing tests, if any:"
    grep -l "FAILED\|ERROR" /tmp/reports/surefire/integration-tests/*.txt 2>/dev/null | sed 's|.*/||'
  fi
}

# ── ArchUnit metrics export: needs the full reactor already installed (it scans every module's
# classes on one combined classpath), and touches marketplace-app's own target/ the same as
# run_unit_tests does -- grouped with it below, never run concurrently against it, to avoid both
# writing into that same target/ at once. Off by default -- see Env below -- since a cold run
# takes several minutes just for this one export, disproportionate to add to every build.
run_archunit_metrics() {
  ./mvnw -pl marketplace-app test -Dtest=ArchitectureMetricsExport -Dsurefire.failIfNoSpecifiedTests=false > /tmp/archunit-metrics.log 2>&1
  ARCHUNIT_METRICS_EXIT=$?

  mkdir -p /tmp/reports
  if [ -f "$ROOT/marketplace-app/target/architecture-metrics.json" ]; then
    mv "$ROOT/marketplace-app/target/architecture-metrics.json" /tmp/reports/architecture-metrics.json
  fi
  return $ARCHUNIT_METRICS_EXIT
}

print_archunit_metrics_summary() {
  cat /tmp/archunit-metrics.log
  echo ""
  if [ "$ARCHUNIT_METRICS_EXIT" -eq 0 ]; then
    echo "===== ARCHUNIT METRICS EXPORT PASSED ====="
  else
    echo "===== ARCHUNIT METRICS EXPORT FAILED (exit $ARCHUNIT_METRICS_EXIT) ====="
  fi
}

# ── Everything touching marketplace-app's own target/ (unit tests, ArchUnit metrics export) is
# grouped here and runs sequentially within the group -- integration tests are the only genuinely
# independent piece (its own, disjoint target/ dir), so they're the one thing safe to parallelize
# against this group. Printing happens inside the group itself (not after) so it still shows up
# correctly even when the group runs backgrounded -- a subshell's own variables don't survive it
# exiting, but its stdout does.
run_marketplace_app_group() {
  GROUP_EXIT=0
  if [ "$RUN_UNIT" = "true" ]; then
    run_unit_tests
    UNIT_EXIT=$?
    print_unit_summary
    [ "$UNIT_EXIT" -eq 0 ] || GROUP_EXIT=$UNIT_EXIT
  fi
  if [ "$ARCHUNIT_METRICS" = "true" ]; then
    run_archunit_metrics
    ARCHUNIT_METRICS_EXIT=$?
    print_archunit_metrics_summary
    [ "$ARCHUNIT_METRICS_EXIT" -eq 0 ] || GROUP_EXIT=$ARCHUNIT_METRICS_EXIT
  fi
  return $GROUP_EXIT
}

NEED_MARKETPLACE_APP_GROUP=false
if [ "$RUN_UNIT" = "true" ] || [ "$ARCHUNIT_METRICS" = "true" ]; then
  NEED_MARKETPLACE_APP_GROUP=true
fi

FINAL_EXIT=0
if $NEED_MARKETPLACE_APP_GROUP && [ "$RUN_INTEGRATION" = "true" ]; then
  echo ""
  echo "=== Running marketplace-app tests + integration tests in parallel ==="
  set +e
  run_marketplace_app_group & GROUP_PID=$!
  run_integration_tests & INTEGRATION_PID=$!
  wait $GROUP_PID; GROUP_EXIT=$?
  wait $INTEGRATION_PID; INTEGRATION_EXIT=$?
  set -e
  print_integration_summary
  echo "Surefire reports: scripts/build-and-test/reports/surefire/"
  [ "$GROUP_EXIT" -eq 0 ] || FINAL_EXIT=$GROUP_EXIT
  [ "$INTEGRATION_EXIT" -eq 0 ] || FINAL_EXIT=$INTEGRATION_EXIT
elif $NEED_MARKETPLACE_APP_GROUP; then
  echo ""
  echo "=== Running marketplace-app tests ==="
  set +e
  run_marketplace_app_group
  GROUP_EXIT=$?
  set -e
  echo "Surefire reports: scripts/build-and-test/reports/surefire/"
  FINAL_EXIT=$GROUP_EXIT
elif [ "$RUN_INTEGRATION" = "true" ]; then
  echo ""
  echo "=== Running integration tests ==="
  set +e
  run_integration_tests
  INTEGRATION_EXIT=$?
  set -e
  print_integration_summary
  echo "Surefire reports: scripts/build-and-test/reports/surefire/"
  FINAL_EXIT=$INTEGRATION_EXIT
fi
[ "$FINAL_EXIT" -eq 0 ] || exit $FINAL_EXIT
