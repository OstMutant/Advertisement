#!/bin/bash
# Usage:
#   bash scripts/unit-tests/run.sh                    — run every plain unit test
#                                                        (query-lib + marketplace-app)
#   bash scripts/unit-tests/run.sh marketplace-app     — one module only
#   bash scripts/unit-tests/run.sh query-lib           — one module only
#   bash scripts/unit-tests/run.sh AccessEvaluatorTest — one test class by name
#
# No Docker required — these are plain JUnit 5 (+ Mockito where needed) unit tests, no
# Testcontainers, no real database, no Spring context in most cases. For Testcontainers-based
# repository tests against a real Postgres, use scripts/integration-tests.sh instead — see
# scripts/CLAUDE.md "Plain Unit Tests vs Testcontainers Tests".
#
# Streams full Maven output live.

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
REPORT_DIR="$ROOT/scripts/unit-tests/reports"
LOG_FILE="$REPORT_DIR/run.log"

MODULES="query-lib,marketplace-app"
TEST_ARG=""
ARG="$1"
if [ "$ARG" = "query-lib" ] || [ "$ARG" = "marketplace-app" ]; then
  MODULES="$ARG"
elif [ -n "$ARG" ]; then
  TEST_ARG="-Dtest=${ARG} -Dsurefire.failIfNoSpecifiedTests=false"
fi

mkdir -p "$REPORT_DIR"
rm -f "$LOG_FILE"
rm -rf "$REPORT_DIR/surefire"

echo "Running: ./mvnw -pl $MODULES -am test $TEST_ARG"
cd "$ROOT"
./mvnw -pl "$MODULES" -am test $TEST_ARG 2>&1 | tee "$LOG_FILE"
EXIT_CODE=${PIPESTATUS[0]}

mkdir -p "$REPORT_DIR/surefire"
for m in query-lib marketplace-app; do
  if [ -d "$ROOT/$m/target/surefire-reports" ]; then
    mkdir -p "$REPORT_DIR/surefire/$m"
    cp -r "$ROOT/$m"/target/surefire-reports/* "$REPORT_DIR/surefire/$m/" 2>/dev/null || true
  fi
done

echo ""
if [ "$EXIT_CODE" -eq 0 ]; then
  echo "===== PASSED ====="
else
  echo "===== FAILED (exit $EXIT_CODE) ====="
  echo "Failing tests, if any:"
  grep -rl "FAILED\|ERROR" "$REPORT_DIR"/surefire/*/*.txt 2>/dev/null | sed 's|.*/||'
fi
echo "Full log:         $LOG_FILE"
echo "Surefire reports: $REPORT_DIR/surefire/"

exit $EXIT_CODE
