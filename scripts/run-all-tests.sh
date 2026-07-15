#!/bin/bash
# Usage:
#   bash scripts/run-all-tests.sh
#   bash scripts/run-all-tests.sh --unit "AccessEvaluatorTest" \
#                                  --integration "--sandbox UserServiceRestoreTest" \
#                                  --playwright "e2e --ux"
#
# Groups each suite's own flags/scenario args behind --unit/--integration/--playwright, then
# forwards them unchanged to scripts/unit-tests.sh, scripts/integration-tests.sh,
# scripts/playwright.sh respectively (same flags each script already accepts standalone).
#
# unit-tests and integration-tests run sequentially, not in parallel: both can compile the same
# starter modules (platform-commons, user-/advertisement-/taxon-spring-boot-starter) into their
# shared target/ dirs -- running them concurrently risks a build race if a starter changed since
# the last run. playwright runs in parallel with that pair from the start: it never touches the
# Maven reactor, only an already-built, already-running Docker container, so it has nothing to
# race with the Maven-based suites. See DECISIONS.md ADR-001.
#
# Side benefit of the unit-tests -> integration-tests order: unit-tests.sh's `-am` reactor build
# already compiles the shared starter modules (though it never installs them to ~/.m2 -- `test`
# goal, not `install`), so integration-tests.sh's own staleness-check/install step that follows
# finds a warm compile cache ("Nothing to compile") and completes faster than a cold run would.

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
REPORT_DIR="$ROOT/scripts/run-all-tests/reports"
mkdir -p "$REPORT_DIR"

UNIT_ARGS=""
INTEGRATION_ARGS=""
PLAYWRIGHT_ARGS=""
CURRENT=""
for arg in "$@"; do
  case "$arg" in
    --unit) CURRENT="unit" ;;
    --integration) CURRENT="integration" ;;
    --playwright) CURRENT="playwright" ;;
    *)
      case "$CURRENT" in
        unit) UNIT_ARGS="$UNIT_ARGS $arg" ;;
        integration) INTEGRATION_ARGS="$INTEGRATION_ARGS $arg" ;;
        playwright) PLAYWRIGHT_ARGS="$PLAYWRIGHT_ARGS $arg" ;;
        *) echo "Ignoring arg with no --unit/--integration/--playwright group: $arg" ;;
      esac
      ;;
  esac
done

PW_LOG="$REPORT_DIR/playwright.log"
SEQ_LOG="$REPORT_DIR/unit-then-integration.log"

echo "Starting playwright in background (log: $PW_LOG)..."
bash "$ROOT/scripts/playwright.sh" $PLAYWRIGHT_ARGS > "$PW_LOG" 2>&1 &
PW_PID=$!

{
  echo "Running unit-tests..."
  bash "$ROOT/scripts/unit-tests.sh" $UNIT_ARGS
  UNIT_EXIT=$?
  echo "unit-tests exit code: $UNIT_EXIT"

  echo "Running integration-tests..."
  bash "$ROOT/scripts/integration-tests.sh" $INTEGRATION_ARGS
  INTEGRATION_EXIT=$?
  echo "integration-tests exit code: $INTEGRATION_EXIT"
} 2>&1 | tee "$SEQ_LOG"
SEQ_EXIT=${PIPESTATUS[0]}

echo "Waiting for playwright to finish..."
wait $PW_PID
PW_EXIT=$?

echo ""
echo "===== SUMMARY ====="
echo "unit-tests + integration-tests log: $SEQ_LOG"
echo "playwright log:                     $PW_LOG (exit $PW_EXIT)"
if [ "$SEQ_EXIT" -eq 0 ] && [ "$PW_EXIT" -eq 0 ]; then
  echo "ALL PASSED"
else
  echo "SOME FAILED"
fi

exit $(( SEQ_EXIT != 0 || PW_EXIT != 0 ))
