#!/bin/bash
# ── Header ──────────────────────────────────────────────────────────
# Description: Runs the Testcontainers-backed integration-tests module (real Postgres) with live
#   Maven output. Before testing it reinstalls only the org.ost modules whose newest .java file is
#   newer than their installed JAR in ~/.m2 (or whose JAR is missing), so it never tests against a
#   stale starter JAR and never walks the whole reactor when nothing changed; --no-check bypasses
#   that check. Standalone entry point with capabilities build-and-test.sh does not replicate.
# Usage: bash integration-tests/run.sh [--sandbox] [--no-check] [smoke | <TestClass> [<TestClass> ...]]
#   (no arguments)  every test in the module
#   smoke           PostgresContainerSmokeTest only
#   <TestClass> ... one or more test classes by name (several are joined into one -Dtest list)
#   --sandbox       apply this sandbox's Docker workarounds (Ryuk disabled, fixed Postgres port 25432)
#                   and first remove every container labelled org.testcontainers=true, host-wide;
#                   not needed on a normal developer machine
#   --no-check      skip the staleness check and test against whatever is already in ~/.m2
#   Flags can be combined in any order, e.g. --sandbox smoke.
# Uses: bash, docker (a reachable daemon), ./mvnw, java, awk, sed, find.
# Env: TESTCONTAINERS_RYUK_DISABLED and INTEGRATION_TESTS_POSTGRES_FIXED_PORT (set by --sandbox);
#   GITHUB_ACTIONS (when set, --sandbox and those two variables are refused).
# Input: integration-tests/pom.xml's org.ost <dependency> list (the modules checked for staleness
#   and aggregated into the coverage report), each module's source and installed JAR.
# Outputs: scripts/logs/integration-tests/run.log (full streamed output, replaced every run),
#   integration-tests/reports/surefire/ (one report per test class, replaced every run),
#   integration-tests/reports/jacoco/integration-tests-aggregate.xml; fresh JARs for the modules
#   that were stale; the JaCoCo CLI jar is downloaded into ~/.m2 when missing.
# Returns: 0 when every selected test passed; the failing step's exit code otherwise (starter
#   install, Maven test run); 1 when --sandbox or its variables are used under GITHUB_ACTIONS.
# ─────────────────────────────────────────────────────────────────

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
REPORT_DIR="$ROOT/integration-tests/reports"
# Separate from $REPORT_DIR -- a process log (run.log), not a test result, same logs-vs-reports
# split every other script in this repo now uses.
LOG_FILE="$ROOT/scripts/logs/integration-tests/run.log"

SCENARIO=""
SANDBOX=""
NO_CHECK=""
for arg in "$@"; do
  if [ "$arg" = "--sandbox" ]; then
    SANDBOX=1
  elif [ "$arg" = "--no-check" ]; then
    NO_CHECK=1
  else
    SCENARIO="${SCENARIO:+$SCENARIO,}$arg"
  fi
done

# CI-environment guard: --sandbox and the sandbox-only env vars it sets are
# workarounds for this specific claude-dev sandbox's Docker networking limitations (see
# scripts/CLAUDE.md "Unit / Testcontainers Tests") -- never needed, and never correct, on a real
# CI runner with normal Docker networking. Fail fast instead of letting someone copy-paste a
# --sandbox invocation into a future CI config without realizing why it's there.
if [ -n "$GITHUB_ACTIONS" ] && { [ -n "$SANDBOX" ] || [ -n "$TESTCONTAINERS_RYUK_DISABLED" ] || [ -n "$INTEGRATION_TESTS_POSTGRES_FIXED_PORT" ]; }; then
  echo "ERROR: --sandbox / TESTCONTAINERS_RYUK_DISABLED / INTEGRATION_TESTS_POSTGRES_FIXED_PORT" \
       "detected under GITHUB_ACTIONS. These are workarounds for this project's claude-dev sandbox" \
       "only -- a real CI runner has normal Docker networking and must never set them. Remove the" \
       "flag/env var from the CI config."
  exit 1
fi

# Docker daemon precheck: fail with a clear message here instead of letting the
# failure surface deep inside Testcontainers' own (slower, less clear) connection probing.
if ! docker info >/dev/null 2>&1; then
  echo "ERROR: Docker daemon not reachable. integration-tests requires a running Docker daemon" \
       "(Testcontainers starts a real Postgres container). Start Docker Desktop / dockerd and retry."
  exit 1
fi

TEST_ARG=""
if [ -n "$SCENARIO" ]; then
  if [ "$SCENARIO" = "smoke" ]; then
    TEST_ARG="-Dtest=PostgresContainerSmokeTest -Dsurefire.failIfNoSpecifiedTests=false"
  else
    TEST_ARG="-Dtest=${SCENARIO} -Dsurefire.failIfNoSpecifiedTests=false"
  fi
fi

ENV_PREFIX=()
if [ -n "$SANDBOX" ]; then
  ENV_PREFIX+=("TESTCONTAINERS_RYUK_DISABLED=true" "INTEGRATION_TESTS_POSTGRES_FIXED_PORT=25432")
  echo "Applying sandbox Docker workarounds (--sandbox): Ryuk disabled, fixed Postgres port 25432."
  # Ryuk is disabled above, so remove any Testcontainers container leaked by a prior crashed run.
  docker ps -aq --filter "label=org.testcontainers=true" | xargs -r docker rm -f
fi

cd "$ROOT"

if [ -n "$NO_CHECK" ]; then
  echo "Applying --no-check: skipping the staleness check — testing against whatever is already" \
       "in ~/.m2, even if stale."
else
  # Module list derived directly from integration-tests/pom.xml's own real <dependency> entries
  # (every org.ost artifact it actually compiles against) instead of the old "no own
  # src/test/java" heuristic -- that heuristic wrongly excluded marketplace-orchestrator/
  # marketplace-rest-api (both have their own unit tests AND are real compile-scope dependencies
  # of this module's Level 3 tests), so a source change in either never triggered a reinstall here
  # and silently left ~/.m2 stale against everything else. Reading the real dependency list is
  # also self-maintaining -- any future dependency integration-tests/pom.xml adds is watched
  # automatically, no separate exception list to keep in sync.
  STARTER_MODULES=$(awk '/<groupId>org\.ost<\/groupId>/{getline; print}' "$ROOT/integration-tests/pom.xml" \
    | sed -E 's#.*<artifactId>([^<]+)</artifactId>.*#\1#' \
    | grep -vE '^(advertisement-parent|integration-tests)$' \
    | sort -u | paste -sd' ')
  NEEDS_INSTALL=""
  for m in $STARTER_MODULES; do
    JAR="$(find "$HOME/.m2/repository/org/ost/$m" -name '*.jar' 2>/dev/null | head -1)"
    if [ -z "$JAR" ]; then
      echo "No installed JAR found for $m — will install fresh starter JARs first."
      NEEDS_INSTALL=1
      break
    fi
    STALE_FILE="$(find "$ROOT/$m/src/main" -name '*.java' -newer "$JAR" 2>/dev/null | head -1)"
    if [ -n "$STALE_FILE" ]; then
      echo "$m changed since its last install ($STALE_FILE newer than $JAR) — will reinstall."
      NEEDS_INSTALL=1
      break
    fi
  done

  if [ -n "$NEEDS_INSTALL" ]; then
    echo "Installing fresh starter JARs: $STARTER_MODULES"
    ./mvnw install -pl "$(echo "$STARTER_MODULES" | tr ' ' ,)" -am -DskipTests
    INSTALL_EXIT=$?
    if [ "$INSTALL_EXIT" -ne 0 ]; then
      echo "===== FAILED (starter install exit $INSTALL_EXIT) ====="
      exit $INSTALL_EXIT
    fi
  else
    echo "All starter JARs in ~/.m2 are up to date — skipping reactor rebuild."
  fi
fi

mkdir -p "$REPORT_DIR"
mkdir -p "$(dirname "$LOG_FILE")"
rm -f "$LOG_FILE"
rm -rf "$REPORT_DIR/surefire"

# -Dsurefire.excludedGroups= (empty) overrides the pom's default "testcontainers" exclusion --
# this script's whole purpose is running these Docker-backed tests deliberately.
echo "Running: env ${ENV_PREFIX[*]} ./mvnw -pl integration-tests test -Dsurefire.excludedGroups= $TEST_ARG"
env "${ENV_PREFIX[@]}" ./mvnw -pl integration-tests test -Dsurefire.excludedGroups= $TEST_ARG 2>&1 | tee "$LOG_FILE"
EXIT_CODE=${PIPESTATUS[0]}

mkdir -p "$REPORT_DIR/surefire"
cp -r "$ROOT"/integration-tests/target/surefire-reports/* "$REPORT_DIR/surefire/" 2>/dev/null || true

# Attributes coverage back to the starters this run exercised, via the standalone JaCoCo CLI jar
# reading target/classes+src/main/java straight off disk -- not jacoco:report-aggregate (the Maven
# goal), which only sees dependency modules present in the *same reactor session* and this script's
# scoped `-pl integration-tests test` above never provides that, so it silently produces an empty
# report instead (see .claude/nav/adr-index.md).
JACOCO_VERSION="$(grep -m1 '<jacoco.version>' "$ROOT/pom.xml" | sed -E 's#.*<jacoco.version>([^<]+)</jacoco.version>.*#\1#')"
JACOCO_CLI_JAR="$HOME/.m2/repository/org/jacoco/org.jacoco.cli/$JACOCO_VERSION/org.jacoco.cli-$JACOCO_VERSION-nodeps.jar"
[ -f "$JACOCO_CLI_JAR" ] || ./mvnw -q dependency:get -Dartifact=org.jacoco:org.jacoco.cli:"$JACOCO_VERSION":jar:nodeps
# Every org.ost module integration-tests/pom.xml declares as a dependency, so a new module is covered without editing this list
JACOCO_AGGREGATE_MODULES=$(awk '/<groupId>org\.ost<\/groupId>/{getline; print}' "$ROOT/integration-tests/pom.xml" \
  | sed -E 's#.*<artifactId>([^<]+)</artifactId>.*#\1#' \
  | grep -vE '^(advertisement-parent|integration-tests)$' \
  | sort -u | paste -sd' ')
CLI_REPORT_ARGS=()
for m in $JACOCO_AGGREGATE_MODULES; do
  [ -d "$ROOT/$m/target/classes" ] && CLI_REPORT_ARGS+=(--classfiles "$ROOT/$m/target/classes")
  [ -d "$ROOT/$m/src/main/java" ] && CLI_REPORT_ARGS+=(--sourcefiles "$ROOT/$m/src/main/java")
done
if [ -f "$JACOCO_CLI_JAR" ] && [ -f "$ROOT/integration-tests/target/jacoco.exec" ]; then
  mkdir -p "$REPORT_DIR/jacoco"
  java -jar "$JACOCO_CLI_JAR" report "$ROOT/integration-tests/target/jacoco.exec" \
    "${CLI_REPORT_ARGS[@]}" --xml "$REPORT_DIR/jacoco/integration-tests-aggregate.xml" \
    --name "integration-tests aggregate"
fi

echo ""
if [ "$EXIT_CODE" -eq 0 ]; then
  echo "===== PASSED ====="
else
  echo "===== FAILED (exit $EXIT_CODE) ====="
  echo "Failing tests, if any:"
  grep -l "FAILED\|ERROR" "$REPORT_DIR"/surefire/*.txt 2>/dev/null | sed 's|.*/||'
fi
echo "Full log:        $LOG_FILE"
echo "Surefire reports: $REPORT_DIR/surefire/"

exit $EXIT_CODE
