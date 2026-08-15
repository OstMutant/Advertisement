#!/bin/bash
# ── Header ──────────────────────────────────────────────────────────────────
# Description: Daily-iteration test loop -- runs scripts/build-and-test.sh --unit --integration
#   (whole reactor installed once, unit+integration tests in parallel against it) and
#   scripts/playwright.sh in parallel with each other, since Playwright never touches the Maven
#   reactor and has nothing to race with the build-and-test container. See DECISIONS.md ADR-004's
#   annotation for the unit/integration pairing's own history.
# Usage: bash scripts/run-all-tests.sh [--unit-test <module-or-class>]
#   [--integration-test <scenario-or-class>] [--sandbox] [--playwright "<args>"]
#   --unit-test <arg>          forwarded to build-and-test.sh's own --unit-test
#   --integration-test <arg>   forwarded to build-and-test.sh's own --integration-test
#   --sandbox                  forwarded to build-and-test.sh's own --sandbox -- this claude-dev
#                               sandbox's Testcontainers workaround only, never needed on a real
#                               developer machine
#   --playwright "<args>"      forwarded verbatim to playwright.sh
# Uses: bash, scripts/build-and-test.sh, scripts/playwright.sh.
# Env: None directly -- flags forwarded to build-and-test.sh/playwright.sh carry their own Env
#   behavior, see those scripts' own headers.
# Input: None beyond CLI flags.
# Outputs: scripts/run-all-tests/reports/build-and-test.log + playwright.log. Surefire reports
#   under scripts/build-and-test/reports/surefire/ (produced by build-and-test.sh itself, not
#   duplicated here).
# Returns: 0 if both build-and-test.sh and playwright.sh succeed, 1 if either fails.
# ────────────────────────────────────────────────────────────────────────────
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
REPORT_DIR="$ROOT/scripts/run-all-tests/reports"
mkdir -p "$REPORT_DIR"

UNIT_TEST_ARG=""
INTEGRATION_TEST_ARG=""
SANDBOX=false
PLAYWRIGHT_ARGS=""
NEXT=""
for arg in "$@"; do
  case "$NEXT" in
    unit-test)           UNIT_TEST_ARG="$arg"; NEXT=""; continue ;;
    integration-test)    INTEGRATION_TEST_ARG="$arg"; NEXT=""; continue ;;
    playwright)          PLAYWRIGHT_ARGS="$arg"; NEXT=""; continue ;;
  esac
  case "$arg" in
    --unit-test)         NEXT=unit-test ;;
    --integration-test)  NEXT=integration-test ;;
    --sandbox)           SANDBOX=true ;;
    --playwright)        NEXT=playwright ;;
  esac
done

BUILD_AND_TEST_FLAGS=(--unit --integration)
[ -n "$UNIT_TEST_ARG" ] && BUILD_AND_TEST_FLAGS+=(--unit-test "$UNIT_TEST_ARG")
[ -n "$INTEGRATION_TEST_ARG" ] && BUILD_AND_TEST_FLAGS+=(--integration-test "$INTEGRATION_TEST_ARG")
$SANDBOX && BUILD_AND_TEST_FLAGS+=(--sandbox)

PW_LOG="$REPORT_DIR/playwright.log"
BUILD_LOG="$REPORT_DIR/build-and-test.log"

echo "Starting playwright in background (log: $PW_LOG)..."
bash "$ROOT/scripts/playwright.sh" $PLAYWRIGHT_ARGS > "$PW_LOG" 2>&1 &
PW_PID=$!

echo "Running build-and-test.sh --unit --integration..."
bash "$ROOT/scripts/build-and-test.sh" "${BUILD_AND_TEST_FLAGS[@]}" 2>&1 | tee "$BUILD_LOG"
BUILD_EXIT=${PIPESTATUS[0]}

echo "Waiting for playwright to finish..."
wait $PW_PID
PW_EXIT=$?

echo ""
echo "===== SUMMARY ====="
echo "build-and-test log: $BUILD_LOG"
echo "Surefire reports:   scripts/build-and-test/reports/surefire/"
echo "playwright log:      $PW_LOG (exit $PW_EXIT)"
if [ "$BUILD_EXIT" -eq 0 ] && [ "$PW_EXIT" -eq 0 ]; then
  echo "ALL PASSED"
else
  echo "SOME FAILED"
fi

exit $(( BUILD_EXIT != 0 || PW_EXIT != 0 ))
