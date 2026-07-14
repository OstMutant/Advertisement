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
#   bash integration-tests/run.sh --fast                    — skip Maven's -am ("also-make")
#                                                               reactor rebuild; resolves
#                                                               platform-commons/advertisement-
#                                                               /user-/taxon-spring-boot-starter
#                                                               from already-installed ~/.m2 JARs
#                                                               instead of rebuilding all 7 other
#                                                               reactor modules every run (~100s
#                                                               of "nothing to compile" overhead
#                                                               even when nothing changed).
#
#     REQUIRES: at least one prior `./mvnw install -DskipTests` (or a --fast-less run) to have
#     populated ~/.m2 with those modules' JARs. Do NOT use --fast right after editing a domain
#     starter's own source (e.g. advertisement-spring-boot-starter/src/main/java/...) — --fast
#     would silently test against the STALE JAR still in ~/.m2, not your edit. Run
#     `./mvnw install -pl <that-module> -am -DskipTests` (or drop --fast for one run) first, then
#     --fast is safe again for iterating on integration-tests' own test files.
#
# Flags can be combined in any order, e.g.:
#   bash integration-tests/run.sh --sandbox --fast smoke
#
# Streams full Maven/Testcontainers output live. Requires a reachable Docker daemon — see
# integration-tests/CLAUDE.md.

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
REPORT_DIR="$ROOT/integration-tests/reports"
LOG_FILE="$REPORT_DIR/run.log"

SCENARIO=""
SANDBOX=""
FAST=""
for arg in "$@"; do
  if [ "$arg" = "--sandbox" ]; then
    SANDBOX=1
  elif [ "$arg" = "--fast" ]; then
    FAST=1
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

REACTOR_FLAG="-am"
if [ -n "$FAST" ]; then
  REACTOR_FLAG=""
  echo "Applying --fast: skipping -am reactor rebuild — resolving starter deps from ~/.m2." \
       "Do NOT use this right after editing a starter's own source; see run.sh header comment."
fi

mkdir -p "$REPORT_DIR"
rm -f "$LOG_FILE"
rm -rf "$REPORT_DIR/surefire"

echo "Running: env ${ENV_PREFIX[*]} ./mvnw -pl integration-tests $REACTOR_FLAG test $TEST_ARG"
cd "$ROOT"
env "${ENV_PREFIX[@]}" ./mvnw -pl integration-tests $REACTOR_FLAG test $TEST_ARG 2>&1 | tee "$LOG_FILE"
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
