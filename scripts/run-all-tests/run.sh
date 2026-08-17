#!/bin/bash
# ── Header ──────────────────────────────────────────────────────────────────
# Description: Daily-iteration test loop -- runs scripts/build-and-test.sh --unit --integration
#   (whole reactor installed once, unit+integration tests against it) in parallel with a
#   deploy-and-run.sh + playwright.sh sequence (deploy-and-run.sh first, so playwright always tests
#   a freshly-rebuilt marketplace-app container, then playwright.sh once that succeeds). The deploy
#   call always clears app data first -- by default via `--reset-only-db` (truncate, fast: schema/
#   Liquibase history untouched, only needs a full reactor build + a live app), or via `--reset`
#   (full DB/MinIO volume wipe, slower: also re-runs every Liquibase migration from scratch) when
#   `--reset` is passed to this script -- use that only when the schema itself changed since the
#   last run; `--reset-only-db` already gives Playwright the same guaranteed-clean *data* the
#   original stale-state problem needed (see playwright/CLAUDE.md's own "Always deploy with
#   --reset" rule -- this script is what actually enforces a clean-data guarantee automatically
#   instead of relying on a human to remember, `--reset-only-db` or `--reset` either one). The two
#   build-and-test.sh invocations this produces (this script's own, and the one deploy-and-run.sh
#   triggers internally) are safe to run concurrently against the shared maven-cache volume --
#   serialized by that script's own flock, and given distinct container names to avoid a Docker
#   name collision (see scripts/build-and-test/run.sh's own Env doc). See DECISIONS.md ADR-004's
#   annotation for the unit/integration pairing's own history.
# Usage: bash scripts/run-all-tests.sh [--unit-test <module-or-class>]
#   [--integration-test <scenario-or-class>] [--sandbox] [--archunit-metrics] [--reset]
#   [--playwright "<args>"]
#   --unit-test <arg>          forwarded to build-and-test.sh's own --unit-test
#   --integration-test <arg>   forwarded to build-and-test.sh's own --integration-test
#   --sandbox                  forwarded to build-and-test.sh's own --sandbox -- this claude-dev
#                               sandbox's Testcontainers workaround only, never needed on a real
#                               developer machine
#   --archunit-metrics         forwarded to build-and-test.sh's own --archunit-metrics -- off by
#                               default, not part of the normal daily loop (several minutes even
#                               on a warm build)
#   --reset                    use deploy-and-run.sh's own --reset (full volume wipe) instead of
#                               the default --reset-only-db -- only needed when the DB schema
#                               itself changed since the last run
#   --playwright "<args>"      forwarded verbatim to playwright.sh
# Uses: bash, scripts/build-and-test.sh, scripts/deploy-and-run.sh, scripts/playwright.sh.
# Env: None directly -- flags forwarded to build-and-test.sh/deploy-and-run.sh/playwright.sh carry
#   their own Env behavior, see those scripts' own headers.
# Input: None beyond CLI flags.
# Outputs: scripts/run-all-tests/reports/build-and-test.log + playwright.log (the latter now also
#   carries deploy-and-run.sh's own output, since it runs immediately before playwright in the same
#   backgrounded block). Surefire reports under scripts/build-and-test/reports/surefire/, and with
#   --archunit-metrics also scripts/build-and-test/reports/architecture-metrics.json (produced by
#   build-and-test.sh itself, not duplicated here).
# Returns: 0 if build-and-test.sh, deploy-and-run.sh, and playwright.sh all succeed, 1 if any fail.
# ────────────────────────────────────────────────────────────────────────────
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
REPORT_DIR="$ROOT/scripts/run-all-tests/reports"
mkdir -p "$REPORT_DIR"

UNIT_TEST_ARG=""
INTEGRATION_TEST_ARG=""
SANDBOX=false
ARCHUNIT_METRICS=false
RESET_FULL=false
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
    --archunit-metrics)  ARCHUNIT_METRICS=true ;;
    --reset)              RESET_FULL=true ;;
    --playwright)        NEXT=playwright ;;
  esac
done

BUILD_AND_TEST_FLAGS=(--unit --integration)
[ -n "$UNIT_TEST_ARG" ] && BUILD_AND_TEST_FLAGS+=(--unit-test "$UNIT_TEST_ARG")
[ -n "$INTEGRATION_TEST_ARG" ] && BUILD_AND_TEST_FLAGS+=(--integration-test "$INTEGRATION_TEST_ARG")
$SANDBOX && BUILD_AND_TEST_FLAGS+=(--sandbox)
$ARCHUNIT_METRICS && BUILD_AND_TEST_FLAGS+=(--archunit-metrics)

PW_LOG="$REPORT_DIR/playwright.log"
BUILD_LOG="$REPORT_DIR/build-and-test.log"
PW_EXIT_FILE="$REPORT_DIR/.pw_exit_code"
rm -f "$PW_EXIT_FILE"

DEPLOY_FLAGS=(--reset-only-db)
$RESET_FULL && DEPLOY_FLAGS=(--reset)

echo "Starting deploy-and-run.sh ${DEPLOY_FLAGS[*]} + playwright in background (log: $PW_LOG)..."
{
  bash "$ROOT/scripts/deploy-and-run.sh" "${DEPLOY_FLAGS[@]}"
  DEPLOY_EXIT=$?
  if [ "$DEPLOY_EXIT" -eq 0 ]; then
    bash "$ROOT/scripts/playwright.sh" $PLAYWRIGHT_ARGS
    RC=$?
  else
    echo "deploy-and-run.sh failed (exit $DEPLOY_EXIT) -- skipping playwright."
    RC=$DEPLOY_EXIT
  fi
  echo "$RC" > "$PW_EXIT_FILE"
} > "$PW_LOG" 2>&1 &
PW_PID=$!

echo "Running build-and-test.sh --unit --integration..."
bash "$ROOT/scripts/build-and-test.sh" "${BUILD_AND_TEST_FLAGS[@]}" 2>&1 | tee "$BUILD_LOG"
BUILD_EXIT=${PIPESTATUS[0]}

echo "Waiting for deploy-and-run + playwright to finish..."
wait $PW_PID
PW_EXIT="$(cat "$PW_EXIT_FILE" 2>/dev/null || echo 1)"
rm -f "$PW_EXIT_FILE"

echo ""
echo "===== SUMMARY ====="
echo "build-and-test log: $BUILD_LOG"
echo "Surefire reports:   scripts/build-and-test/reports/surefire/"
echo "deploy-and-run + playwright log: $PW_LOG (exit $PW_EXIT)"
if [ "$BUILD_EXIT" -eq 0 ] && [ "$PW_EXIT" -eq 0 ]; then
  echo "ALL PASSED"
else
  echo "SOME FAILED"
fi

exit $(( BUILD_EXIT != 0 || PW_EXIT != 0 ))
