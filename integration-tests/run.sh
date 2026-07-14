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
#
# Scenario and --sandbox can be combined in any order, e.g.:
#   bash integration-tests/run.sh --sandbox smoke
#
# Streams full Maven/Testcontainers output live. Requires a reachable Docker daemon — see
# integration-tests/CLAUDE.md.

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
REPORT_DIR="$ROOT/integration-tests/reports"
LOG_FILE="$REPORT_DIR/run.log"

SCENARIO=""
SANDBOX=""
for arg in "$@"; do
  if [ "$arg" = "--sandbox" ]; then
    SANDBOX=1
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

mkdir -p "$REPORT_DIR"
rm -f "$LOG_FILE"
rm -rf "$REPORT_DIR/surefire"

echo "Running: env ${ENV_PREFIX[*]} ./mvnw -pl integration-tests -am test $TEST_ARG"
cd "$ROOT"
env "${ENV_PREFIX[@]}" ./mvnw -pl integration-tests -am test $TEST_ARG 2>&1 | tee "$LOG_FILE"
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
