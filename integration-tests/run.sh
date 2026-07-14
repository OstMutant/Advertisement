#!/bin/bash
# Usage:
#   bash integration-tests/run.sh                          — run every test in the module
#   bash integration-tests/run.sh smoke                     — run PostgresContainerSmokeTest only
#   bash integration-tests/run.sh AdvertisementRepositoryTest — run one test class by name
#   bash integration-tests/run.sh --sandbox                 — apply this sandbox's Docker
#                                                               workarounds (Ryuk disabled, fixed
#                                                               Postgres port); NOT needed on a
#                                                               normal developer machine, see
#                                                               scripts/CLAUDE.md "Unit /
#                                                               Testcontainers Tests"
#   bash integration-tests/run.sh --no-check                — skip the automatic staleness check
#                                                               below entirely and go straight to
#                                                               `mvn -pl integration-tests test`
#                                                               against whatever is already in
#                                                               ~/.m2, even if it's stale. Use only
#                                                               when you deliberately want to test
#                                                               against a specific already-built
#                                                               JAR (e.g. reproducing behavior
#                                                               against an older starter build) or
#                                                               to shave the few seconds the `find`
#                                                               check itself costs — NOT a
#                                                               substitute for the check on a normal
#                                                               edit/test loop.
#
# Flags can be combined in any order, e.g.:
#   bash integration-tests/run.sh --sandbox smoke
#
# Streams full Maven/Testcontainers output live. Requires a reachable Docker daemon — see
# integration-tests/CLAUDE.md.
#
# Automatic staleness check (default, no flag needed — see DECISIONS.md ADR-007):
# integration-tests depends on platform-commons/advertisement-/user-/taxon-spring-boot-starter as
# real compiled JARs from ~/.m2, not source. Before testing, this script compares each of those
# modules' newest .java file against its installed JAR's mtime; if any source is newer (or the JAR
# is missing entirely), it runs a targeted `mvn install -DskipTests` for just those modules first.
# Otherwise it skips straight to `mvn -pl integration-tests test` — no full 9-module reactor walk,
# no risk of silently testing against a stale JAR either way. `--no-check` above bypasses this.

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
REPORT_DIR="$ROOT/integration-tests/reports"
LOG_FILE="$REPORT_DIR/run.log"

SCENARIO=""
SANDBOX=""
NO_CHECK=""
for arg in "$@"; do
  if [ "$arg" = "--sandbox" ]; then
    SANDBOX=1
  elif [ "$arg" = "--no-check" ]; then
    NO_CHECK=1
  else
    SCENARIO="$arg"
  fi
done

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
fi

cd "$ROOT"

if [ -n "$NO_CHECK" ]; then
  echo "Applying --no-check: skipping the staleness check — testing against whatever is already" \
       "in ~/.m2, even if stale."
else
  STARTER_MODULES="platform-commons advertisement-spring-boot-starter user-spring-boot-starter taxon-spring-boot-starter"
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
rm -f "$LOG_FILE"
rm -rf "$REPORT_DIR/surefire"

echo "Running: env ${ENV_PREFIX[*]} ./mvnw -pl integration-tests test $TEST_ARG"
env "${ENV_PREFIX[@]}" ./mvnw -pl integration-tests test $TEST_ARG 2>&1 | tee "$LOG_FILE"
EXIT_CODE=${PIPESTATUS[0]}

mkdir -p "$REPORT_DIR/surefire"
cp -r "$ROOT"/integration-tests/target/surefire-reports/* "$REPORT_DIR/surefire/" 2>/dev/null || true

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
