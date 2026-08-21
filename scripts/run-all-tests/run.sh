#!/bin/bash
# ── Header ──────────────────────────────────────────────────────────────────
# Description: Daily-iteration test loop -- runs scripts/build-and-test.sh --unit --integration
#   --skip-vaadin (whole reactor installed once, skipping the Vaadin frontend bundle neither test
#   suite touches, unit+integration tests against it) in parallel with a
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
#   name collision (see scripts/build-and-test/run.sh's own Env doc). See docs/ai/adr-index.md
#   for the unit/integration pairing's own history.
# Usage: bash scripts/run-all-tests.sh [--unit-test <module-or-class>]
#   [--integration-test <scenario-or-class>] [--sandbox] [--archunit-metrics] [--reset]
#   [--playwright "<args>"]
#   (no flags)                 unit+integration tests (build-and-test.sh's own defaults, both true)
#                               in parallel with a full deploy + Playwright run against every spec
#                               (default --reset-only-db, no scenario/--ux/--full narrowing)
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
# Uses: bash, docker (alpine image, for the persistent run-all-tests-reports container),
#   scripts/build-and-test.sh, scripts/deploy-and-run.sh, scripts/playwright.sh,
#   scripts/utils/wait-for-container-files.sh (sourced).
# Env: None directly -- flags forwarded to build-and-test.sh/deploy-and-run.sh/playwright.sh carry
#   their own Env behavior, see those scripts' own headers.
# Input: None beyond CLI flags.
# Outputs: scripts/run-all-tests/reports/build-and-test.log + playwright.log (the latter now also
#   carries deploy-and-run.sh's own output, since it runs immediately before playwright in the same
#   backgrounded block). Both are written during the run into the shared test-reports named Docker
#   volume (never a WSL/Windows-drive path, so never subject to the docker-desktop-bind-mounts
#   issue documented in docs/ai/adr-index.md) via a persistent `run-all-tests-reports` container
#   (removed and recreated fresh at the start of each run, never removed at the end -- same reused-
#   container shape playwright/run.sh already uses for pw-runner), then copied out to their real
#   destination here via `docker cp` once the whole run finishes; the same volume can also be
#   inspected directly at any time, e.g. `docker exec run-all-tests-reports cat
#   /reports/run-all-tests/playwright.log`. Surefire reports under
#   scripts/build-and-test/reports/surefire/, and with --archunit-metrics also
#   scripts/build-and-test/reports/architecture-metrics.json (produced by build-and-test.sh itself,
#   not duplicated here).
# Returns: 0 if build-and-test.sh, deploy-and-run.sh, and playwright.sh all succeed, 1 if any fail.
# ────────────────────────────────────────────────────────────────────────────
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
REPORT_DIR="$ROOT/scripts/run-all-tests/reports"
# No mkdir -p here -- `docker cp` (below) creates the destination directory itself when it doesn't
# exist, so a raw bash `mkdir -p` against this WSL/Windows-drive host path was pure redundancy that
# cost every run a real filesystem write vulnerable to the docker-desktop-bind-mounts issue
# documented in docs/ai/adr-index.md (confirmed directly to fail with "Permission denied" on a
# real Docker Desktop/WSL2 machine). `docker cp` itself is daemon-mediated and not subject to it.

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

BUILD_AND_TEST_FLAGS=(--unit --integration --skip-vaadin)
[ -n "$UNIT_TEST_ARG" ] && BUILD_AND_TEST_FLAGS+=(--unit-test "$UNIT_TEST_ARG")
[ -n "$INTEGRATION_TEST_ARG" ] && BUILD_AND_TEST_FLAGS+=(--integration-test "$INTEGRATION_TEST_ARG")
$SANDBOX && BUILD_AND_TEST_FLAGS+=(--sandbox)
$ARCHUNIT_METRICS && BUILD_AND_TEST_FLAGS+=(--archunit-metrics)

PW_LOG="$REPORT_DIR/playwright.log"
BUILD_LOG="$REPORT_DIR/build-and-test.log"
PW_EXIT_FILE="/tmp/run-all-tests-pw-exit.$$"

# Both orchestration-level logs are written into the shared test-reports named Docker volume
# (never a WSL/Windows-drive path, so never subject to the docker-desktop-bind-mounts issue
# documented in docs/ai/adr-index.md) via one persistent, reused container (REPORTS_CONTAINER,
# same reuse pattern as playwright/run.sh's own pw-runner) rather than a fresh throwaway container
# per write/per flush -- simplest reliable shape: the container is always already running by the
# time any docker exec/docker cp against it happens, so there's no container-startup-timing
# question to reason about at every call site. Removed and recreated fresh here, at the start of
# each run -- deliberately NOT removed at the end (see the flush step below), so a failed/crashed
# run's data in the volume stays inspectable via `docker exec run-all-tests-reports ...` until the
# next run explicitly clears it.
REPORTS_CONTAINER="run-all-tests-reports"
docker rm -f "$REPORTS_CONTAINER" >/dev/null 2>&1 || true
docker run -d --name "$REPORTS_CONTAINER" -v test-reports:/reports alpine sleep 86400 >/dev/null
docker exec "$REPORTS_CONTAINER" sh -c "mkdir -p /reports/run-all-tests && rm -f /reports/run-all-tests/*.log"

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
} 2>&1 | docker exec -i "$REPORTS_CONTAINER" sh -c "cat > /reports/run-all-tests/playwright.log" &
PW_PID=$!

echo "Running build-and-test.sh --unit --integration..."
# A named pipe (mkfifo) + an explicitly backgrounded writer with its own captured PID, not
# `tee >(...)` process substitution -- confirmed directly that bash does not reliably wait for a
# process substitution's own subprocess (a bare `wait` returned immediately, before a deliberately
# slow writer had finished, in a controlled test), which let the flush step below read from the
# volume before this write had actually finished. `wait $BUILD_LOG_WRITER_PID` blocks on that
# specific, real PID instead, so the write is guaranteed done before flushing.
BUILD_LOG_FIFO="/tmp/run-all-tests-build.$$.fifo"
mkfifo "$BUILD_LOG_FIFO"
docker exec -i "$REPORTS_CONTAINER" sh -c "cat > /reports/run-all-tests/build-and-test.log" < "$BUILD_LOG_FIFO" &
BUILD_LOG_WRITER_PID=$!
bash "$ROOT/scripts/build-and-test.sh" "${BUILD_AND_TEST_FLAGS[@]}" 2>&1 | tee "$BUILD_LOG_FIFO"
BUILD_EXIT=${PIPESTATUS[0]}
wait $BUILD_LOG_WRITER_PID
rm -f "$BUILD_LOG_FIFO"

echo "Waiting for deploy-and-run + playwright to finish..."
wait $PW_PID
PW_EXIT="$(cat "$PW_EXIT_FILE" 2>/dev/null || echo 1)"
rm -f "$PW_EXIT_FILE"

# Flush both logs from the volume to their real destination in the repo -- straight `docker cp`
# from REPORTS_CONTAINER (already running the whole time, see above), same proven pattern
# playwright/run.sh already uses against its own long-lived pw-runner.
docker cp "$REPORTS_CONTAINER":/reports/run-all-tests/. "$REPORT_DIR/" 2>/dev/null \
  || echo "WARNING: could not copy orchestration logs to $REPORT_DIR -- inspect directly: docker exec $REPORTS_CONTAINER cat /reports/run-all-tests/playwright.log"

# Confirm both files actually landed on the host (the docker cp above already ran -- this checks
# its real result as plain host paths, not container-internal ones) before removing
# REPORTS_CONTAINER -- confirmed present -> removed right away instead of waiting for the next run;
# still missing after the timeout -> left running with an ERROR message, still cleared at the start
# of the next run either way (see REPORTS_CONTAINER setup above).
source "$ROOT/scripts/utils/wait-for-container-files.sh"
wait_for_container_files_or_keep "$REPORTS_CONTAINER" 20 \
  "$REPORT_DIR/build-and-test.log" "$REPORT_DIR/playwright.log"

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
