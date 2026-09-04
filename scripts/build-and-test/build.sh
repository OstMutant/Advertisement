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
#       marketplace-orchestrator/marketplace-rest-api) or one test class by name; empty runs all 4 modules.
#     INTEGRATION_TEST_ARG (default empty) -- narrows RUN_INTEGRATION to one scenario ("smoke") or
#       one test class by name; empty runs the whole integration-tests module.
#     SKIP_VAADIN (true/false, default false) -- adds -Dvaadin.skip=true to the reactor install,
#       skipping the frontend bundle (npm/Vite) for callers that only need compiled classes. Never
#       set when the resulting jar/target-classes need to be a real, runnable build (the plain
#       cache-priming build, or anything deploy-and-run.sh/e2e relies on) -- marketplace-app's own
#       jar/target-classes refresh into the shared volume is skipped whenever this is true, so a
#       partial build never overwrites what a real one produced.
#     BUILD_CONTAINER_NAME (default advertisement-build-only) -- namespaces this run's own reports
#       under /reports/$BUILD_CONTAINER_NAME/, so two concurrent invocations (e.g. run-all-tests.sh's
#       own direct call and deploy-and-run.sh's internal one, which already uses a distinct
#       BUILD_CONTAINER_NAME to avoid a Docker container-name collision) never collide writing into
#       the same shared test-reports volume at once.
#   May be exported directly in the calling shell if needed:
#     TESTCONTAINERS_RYUK_DISABLED / INTEGRATION_TESTS_POSTGRES_FIXED_PORT -- sandbox-only
#       Testcontainers workarounds, passed through only if already set.
# Input: tar-piped repo source at /app; the shared maven-cache Docker volume at /root/.m2; the
#   shared test-reports Docker volume at /reports (RUN_UNIT/RUN_INTEGRATION/ARCHUNIT_METRICS=true
#   only -- see Outputs).
# Outputs: fresh jars for every module in the shared /root/.m2 (library modules installed;
#   marketplace-app's own JAR copied to /root/.m2/artifacts/marketplace-app.jar); each module's own
#   target/classes also refreshed under /root/.m2/target-classes/<module>/, for scripts/sonar/run.sh
#   to mount and read directly instead of compiling locally. RUN_UNIT/
#   RUN_INTEGRATION=true -- PASSED/FAILED summary on stdout, failing test files listed; Surefire
#   reports copied to $REPORTS_DIR/surefire/<module>/; the full raw console log for whichever phase
#   actually ran (only that phase's own log file exists) also copied to $REPORTS_DIR/logs/
#   (unit-tests.log/integration-tests.log/archunit-metrics.log) -- never lost to terminal
#   scrollback, unlike the stdout copy alone. ARCHUNIT_METRICS=true -- architecture-
#   metrics.json moved to $REPORTS_DIR/architecture-metrics.json. A build-info.txt (BUILD_CONTAINER_NAME,
#   timestamp, which of RUN_UNIT/RUN_INTEGRATION/ARCHUNIT_METRICS/SKIP_VAADIN were set) is always
#   written to $REPORTS_DIR/, so anyone looking at the volume can tell which invocation produced
#   what. $REPORTS_DIR (/reports/$BUILD_CONTAINER_NAME) lives on the shared test-reports named
#   Docker volume (never a WSL/Windows-drive path, so never subject to the docker-desktop-bind-mounts
#   issue documented in .claude/nav/adr-index.md), reaching the host via run.sh's own `docker cp` from
#   this container (while it's still running) after the build finishes. RUN_INTEGRATION=true also
#   mirrors run.log + surefire/ into $REPORTS_DIR/it-mirror/, copied out by run.sh's own second,
#   separate docker cp -- keeps integration-tests/reports/ fresh whenever integration tests run
#   through this script, not only via integration-tests/run.sh's own direct invocation.
# Returns: 0 on success, non-zero on build/test failure.
# ────────────────────────────────────────────────────────────────────────────
set -e

ROOT=/app

# Namespaced by BUILD_CONTAINER_NAME (set by run.sh, defaults to advertisement-build-only) so two
# concurrent invocations of this script -- e.g. run-all-tests.sh's own direct call and
# deploy-and-run.sh's internal one, which already uses a distinct BUILD_CONTAINER_NAME to avoid a
# Docker container-name collision -- don't also collide writing into the same shared test-reports
# volume at once.
REPORTS_DIR="/reports/${BUILD_CONTAINER_NAME:-advertisement-build-only}"
# Separate top-level container path (not nested under REPORTS_DIR) for raw process logs
# (build-info.txt, logs/*.log) -- kept apart from REPORTS_DIR, which is real test *results* only
# (surefire/, it-mirror/, architecture-metrics.json), so run.sh's own host copy-out can send each
# to its own separate host destination (scripts\logs\build-and-test\ vs
# scripts\build-and-test\reports\) with two plain docker cp calls, no exclusion logic needed.
LOGS_DIR="/reports/logs/${BUILD_CONTAINER_NAME:-advertisement-build-only}"

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
# SKIP_VAADIN=true adds -Dvaadin.skip=true, skipping vaadin-maven-plugin's build-frontend goal
# (npm install + Vite bundle, ~3-4 min) for callers that only need compiled Java classes (unit/
# integration tests, ArchUnit metrics) -- none of them exercise Vaadin UI rendering, so the
# frontend bundle is pure unneeded cost for them. Never set for the plain "prime the cache" build
# (no RUN_UNIT/RUN_INTEGRATION/ARCHUNIT_METRICS) or anything deploy-and-run.sh/e2e relies on --
# those need a real, runnable jar with the frontend actually bundled.
echo ""
echo "=== Building (full reactor) ==="
cd "$ROOT"
mkdir -p "$ARTIFACT_DIR"
MVN_INSTALL_FLAGS="-DskipTests"
[ "$SKIP_VAADIN" = "true" ] && MVN_INSTALL_FLAGS="$MVN_INSTALL_FLAGS -Dvaadin.skip=true"
flock "$MVN_LOCK" -c "./mvnw install $MVN_INSTALL_FLAGS"

# ── Also refresh each module's own target/classes into the shared volume, for
# scripts/sonar/run.sh to mount and read directly (sonar.java.binaries needs a directory of
# .class files, not a jar) -- excludes integration-tests, which sonar-project.properties never
# scans (test-only module). marketplace-app itself is excluded from this refresh (and from the JAR
# copy below) when SKIP_VAADIN=true -- its target/classes and jar are missing the Vaadin frontend
# bundle in that case, and both this shared volume path and $ARTIFACT are read by other callers
# (sonar, deploy-and-run.sh/e2e) that need the real thing, not a partial one. ──────────
# Module list derived from root pom.xml (excluding integration-tests) instead of hand-maintained --
# a separately hardcoded copy here silently drifted out of sync before (confirmed directly:
# apikey-spring-boot-starter/marketplace-rest-api were missing, so scripts/sonar/run.sh's
# sonar.java.binaries pointed at directories that were never populated).
TARGET_CLASSES_DIR=/root/.m2/target-classes
mkdir -p "$TARGET_CLASSES_DIR"
# Pure grep/sed, not python3 -- this script runs inside the minimal (JDK-only) build container,
# unlike scripts/sonar/run.sh's own host-side module-list validator, which can use python3.
TARGET_CLASSES_MODULES=$(sed -n '/<modules>/,/<\/modules>/p' "$ROOT/pom.xml" \
  | grep -oE '<module>[^<]+</module>' | sed -E 's#</?module>##g' | grep -v '^integration-tests$')
for module in $TARGET_CLASSES_MODULES; do
  if [ "$module" = "marketplace-app" ] && [ "$SKIP_VAADIN" = "true" ]; then
    continue
  fi
  if [ -d "$ROOT/$module/target/classes" ]; then
    rm -rf "$TARGET_CLASSES_DIR/$module"
    mkdir -p "$TARGET_CLASSES_DIR/$module"
    cp -r "$ROOT/$module/target/classes/." "$TARGET_CLASSES_DIR/$module/"
  fi
done

if [ "$SKIP_VAADIN" != "true" ]; then
  JAR=$(ls "$ROOT/marketplace-app/target/"*.jar 2>/dev/null | grep -v '\.original$' | head -1)
  if [ -n "$JAR" ]; then
    cp "$JAR" "$ARTIFACT"
    echo "marketplace-app.jar refreshed in the shared volume."
  fi
fi
echo ""
echo "=== Build done ==="

# ── build-info.txt: written unconditionally so anyone looking at $LOGS_DIR on the shared
# volume (or after it lands on the host) can tell which invocation produced it, without having to
# guess from context -- which container name, when, and with which flags. Lives in $LOGS_DIR, not
# $REPORTS_DIR -- it's process metadata about the run itself, not a test result. ────────────────
mkdir -p "$LOGS_DIR"
cat > "$LOGS_DIR/build-info.txt" <<EOF
BUILD_CONTAINER_NAME=${BUILD_CONTAINER_NAME:-advertisement-build-only}
timestamp=$(date -u +%Y-%m-%dT%H:%M:%SZ)
RUN_UNIT=${RUN_UNIT:-false}
RUN_INTEGRATION=${RUN_INTEGRATION:-false}
ARCHUNIT_METRICS=${ARCHUNIT_METRICS:-false}
SKIP_VAADIN=${SKIP_VAADIN:-false}
UNIT_TEST_ARG=${UNIT_TEST_ARG:-}
INTEGRATION_TEST_ARG=${INTEGRATION_TEST_ARG:-}
EOF

# ── From here on, test failures are captured via $? and summarized, never allowed to abort the
# script outright -- the top-level ERR trap above exists only to catch a genuinely unexpected
# failure during the mandatory reactor build above. Left active past this point, it fires on the
# very first non-zero-returning top-level command in the test-running section below (confirmed
# directly: `wait $INTEGRATION_PID` returning a failed job's real exit status was enough) and exits
# immediately -- before print_unit_summary/print_integration_summary ever run, so a real test
# failure's actual cause (which test, what error) never gets printed anywhere, console or file. ──
trap - ERR

# ── Unit tests: plain JUnit, no Docker needed ─────────────────────────────────
# No flock here (unlike the install step above) -- once ~/.m2 is installed, both this and the
# integration-test block below only *read* it, never write, so they're safe to run concurrently
# against each other. They touch different target/ dirs too (query-lib/marketplace-app/
# marketplace-orchestrator/marketplace-rest-api vs integration-tests), so no output collision either.
run_unit_tests() {
  UNIT_MODULES="query-lib,marketplace-app,marketplace-orchestrator,marketplace-rest-api"
  UNIT_TEST_FLAG=""
  if [ "$UNIT_TEST_ARG" = "query-lib" ] || [ "$UNIT_TEST_ARG" = "marketplace-app" ] || [ "$UNIT_TEST_ARG" = "marketplace-orchestrator" ] || [ "$UNIT_TEST_ARG" = "marketplace-rest-api" ]; then
    UNIT_MODULES="$UNIT_TEST_ARG"
  elif [ -n "$UNIT_TEST_ARG" ]; then
    UNIT_TEST_FLAG="-Dtest=${UNIT_TEST_ARG} -Dsurefire.failIfNoSpecifiedTests=false"
  fi

  ./mvnw -pl "$UNIT_MODULES" test $UNIT_TEST_FLAG > /tmp/unit-tests.log 2>&1
  UNIT_EXIT=$?

  mkdir -p "$LOGS_DIR"
  cp /tmp/unit-tests.log "$LOGS_DIR/unit-tests.log"

  mkdir -p "$REPORTS_DIR/surefire"
  for m in query-lib marketplace-app marketplace-orchestrator marketplace-rest-api; do
    if [ -d "$ROOT/$m/target/surefire-reports" ]; then
      mkdir -p "$REPORTS_DIR/surefire/$m"
      cp -r "$ROOT/$m"/target/surefire-reports/* "$REPORTS_DIR/surefire/$m/" 2>/dev/null || true
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
    grep -rl "FAILED\|ERROR" "$REPORTS_DIR"/surefire/*/*.txt 2>/dev/null | sed 's|.*/||'
  fi
}

# ── Integration tests: Testcontainers spins up its own Postgres via docker.sock ──
# -Dsurefire.excludedGroups= (empty) overrides integration-tests/pom.xml's default
# "testcontainers" exclusion -- without it, every real @Tag("testcontainers") repository test is
# silently skipped, same override integration-tests/run.sh already applies.
run_integration_tests() {
  # Ryuk is disabled here, so remove any Testcontainers container leaked by a prior crashed run.
  if [ -n "$TESTCONTAINERS_RYUK_DISABLED" ]; then
    docker ps -aq --filter "label=org.testcontainers=true" | xargs -r docker rm -f
  fi

  INTEGRATION_TEST_FLAG=""
  if [ "$INTEGRATION_TEST_ARG" = "smoke" ]; then
    INTEGRATION_TEST_FLAG="-Dtest=PostgresContainerSmokeTest -Dsurefire.failIfNoSpecifiedTests=false"
  elif [ -n "$INTEGRATION_TEST_ARG" ]; then
    INTEGRATION_TEST_FLAG="-Dtest=${INTEGRATION_TEST_ARG} -Dsurefire.failIfNoSpecifiedTests=false"
  fi

  ./mvnw -pl integration-tests test -Dsurefire.excludedGroups= $INTEGRATION_TEST_FLAG > /tmp/integration-tests.log 2>&1
  INTEGRATION_EXIT=$?

  mkdir -p "$LOGS_DIR"
  cp /tmp/integration-tests.log "$LOGS_DIR/integration-tests.log"

  mkdir -p "$REPORTS_DIR/surefire/integration-tests"
  cp -r "$ROOT"/integration-tests/target/surefire-reports/* "$REPORTS_DIR/surefire/integration-tests/" 2>/dev/null || true

  # Mirrors integration-tests/reports/ own shape (surefire/ only -- no run.log copy here, that
  # would just duplicate $LOGS_DIR/integration-tests.log under a second name; run.sh's own
  # copy-out sends that one file to scripts/logs/integration-tests/run.log instead), copied out via
  # a second, separate docker cp in run.sh (in addition to the one that copies $REPORTS_DIR/. as a
  # whole into scripts/build-and-test/reports/) -- so integration-tests/reports/ stays fresh
  # whenever integration tests run through this script too, not only via integration-tests/run.sh's
  # own direct invocation. It also lands (harmlessly) as a nested it-mirror/ subfolder inside
  # scripts/build-and-test/reports/ via the first, whole-volume copy -- not worth avoiding, both
  # destinations reading it via `docker cp` are unaffected by that duplication.
  mkdir -p "$REPORTS_DIR/it-mirror/surefire"
  cp -r "$ROOT"/integration-tests/target/surefire-reports/* "$REPORTS_DIR/it-mirror/surefire/" 2>/dev/null || true

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
    grep -l "FAILED\|ERROR" "$REPORTS_DIR"/surefire/integration-tests/*.txt 2>/dev/null | sed 's|.*/||'
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

  mkdir -p "$LOGS_DIR"
  cp /tmp/archunit-metrics.log "$LOGS_DIR/archunit-metrics.log"

  mkdir -p "$REPORTS_DIR"
  if [ -f "$ROOT/marketplace-app/target/architecture-metrics.json" ]; then
    mv "$ROOT/marketplace-app/target/architecture-metrics.json" "$REPORTS_DIR/architecture-metrics.json"
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
