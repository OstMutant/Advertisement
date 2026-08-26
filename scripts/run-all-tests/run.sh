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
#   name collision (see scripts/build-and-test/run.sh's own Env doc). See .claude/nav/adr-index.md
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
#   scripts/utils/agentic-output.sh (emit_agentic_success_block/emit_agentic_error_block).
# Env: None directly -- flags forwarded to build-and-test.sh/deploy-and-run.sh/playwright.sh carry
#   their own Env behavior, see those scripts' own headers.
# Input: None beyond CLI flags.
# Outputs: build-and-test.log, playwright.log, and this script's own top-level progress/result
#   messages (orchestrator.log) are all written during the run into the shared test-reports named
#   Docker volume (never a WSL/Windows-drive path, so never subject to the docker-desktop-bind-
#   mounts issue documented in .claude/nav/adr-index.md) via a persistent `run-all-tests-reports`
#   container (removed and recreated fresh at the start of each run -- same reused-container shape
#   playwright/run.sh already uses for pw-runner). This script itself never copies that data to a
#   host path or removes the container at the end anymore -- confirmed directly that `docker cp`'s
#   own destination-path argument cannot reliably resolve a WSL docker-desktop-bind-mounts alias
#   path. `run-all-tests.bat` performs the real host copy (into scripts/logs/run-all-tests/) and
#   the container removal as a native cmd.exe step immediately after this script returns -- no WSL
#   path translation involved there at all. A direct `bash scripts/run-all-tests.sh` invocation
#   with no `.bat` wrapper (e.g. Claude Code's own usage) never gets a host copy this way --
#   inspect the volume directly instead, e.g. `docker exec run-all-tests-reports cat
#   /reports/run-all-tests/orchestrator.log`. Surefire reports under
#   scripts/build-and-test/reports/surefire/, and with --archunit-metrics also
#   scripts/build-and-test/reports/architecture-metrics.json (produced by build-and-test.sh itself,
#   not duplicated here).
# Returns: 0 if build-and-test.sh, deploy-and-run.sh, and playwright.sh all succeed, 1 if any fail.
#   Also prints a single-line AGENTIC_SUCCESS_BLOCK JSON marker on a clean finish, or an
#   AGENTIC_ERROR_BLOCK JSON marker (errorCategory/isRetryable/currentStep/description/
#   durationSeconds) once both the build-and-test.sh and deploy-and-run.sh+playwright.sh branches
#   have finished -- this script deliberately lets both branches run to completion even if one
#   fails, so there is no earlier per-command failure point to attach the marker to.
# ────────────────────────────────────────────────────────────────────────────
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SECONDS=0
source "$ROOT/scripts/utils/agentic-output.sh"
# Display-only from here on -- this script itself never writes to a host path anymore (see the
# no-host-copy note further down); run-all-tests.bat's own native copy step is what actually
# populates this directory, after this script returns.
REPORT_DIR="$ROOT/scripts/logs/run-all-tests"

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
# documented in .claude/nav/adr-index.md) via one persistent, reused container (REPORTS_CONTAINER,
# same reuse pattern as playwright/run.sh's own pw-runner) rather than a fresh throwaway container
# per write/per flush -- simplest reliable shape: the container is always already running by the
# time any docker exec against it happens, so there's no container-startup-timing question to
# reason about at every call site. Removed and recreated fresh here, at the start of each run;
# no longer removed by this script at the end -- see the no-host-copy note further down for why
# that moved to run-all-tests.bat's own native step.
REPORTS_CONTAINER="run-all-tests-reports"
docker rm -f "$REPORTS_CONTAINER" >/dev/null 2>&1 || true
docker run -d --name "$REPORTS_CONTAINER" -v test-reports:/reports alpine sleep 86400 >/dev/null
docker exec "$REPORTS_CONTAINER" sh -c "mkdir -p /reports/run-all-tests && rm -f /reports/run-all-tests/*.log /reports/run-all-tests/orchestrator.log"

# This script's own top-level progress/result messages (unlike build-and-test.log/playwright.log,
# which capture their sub-process's full output) otherwise go only to whichever terminal invoked
# this script -- never persisted anywhere. A synchronous, foreground `docker exec -i ... cat >>`
# per checkpoint (not a background pipe/fifo -- these messages are short and infrequent, no need
# for that machinery) mirrors each one into the volume too, so it stays inspectable independent of
# terminal access.
log_orchestrator() {
  echo "$1" | docker exec -i "$REPORTS_CONTAINER" sh -c "cat >> /reports/run-all-tests/orchestrator.log" 2>/dev/null || true
}

DEPLOY_FLAGS=(--reset-only-db)
$RESET_FULL && DEPLOY_FLAGS=(--reset)

echo "Starting deploy-and-run.sh ${DEPLOY_FLAGS[*]} + playwright in background (log: $PW_LOG)..."
log_orchestrator "Starting deploy-and-run.sh ${DEPLOY_FLAGS[*]} + playwright in background (log: $PW_LOG)..."
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
log_orchestrator "Running build-and-test.sh --unit --integration..."
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
log_orchestrator "Waiting for deploy-and-run + playwright to finish..."
wait $PW_PID
PW_EXIT="$(cat "$PW_EXIT_FILE" 2>/dev/null || echo 1)"
rm -f "$PW_EXIT_FILE"

# No host copy and no container removal here anymore -- confirmed directly that `docker cp`'s own
# destination-path argument cannot reliably resolve a WSL docker-desktop-bind-mounts alias path
# (fails with "invalid output path: directory ... does not exist" even naming a directory that
# unquestionably exists), while a native cmd.exe `docker cp` (no WSL path translation involved at
# all) does not have this problem -- the same reason `clean.bat` exists as a native step instead of
# a WSL one. run-all-tests.bat now performs the real host copy (and the container removal) as a
# native step immediately after this script returns; this script's only remaining job is getting
# everything correctly into REPORTS_CONTAINER's own volume and exiting with the right code. A
# direct `bash scripts/run-all-tests.sh` invocation with no .bat wrapper (e.g. Claude Code's own
# usage) never gets the host copy this way -- inspect the volume directly instead, e.g.
# `docker exec run-all-tests-reports cat /reports/run-all-tests/playwright.log`.

RESULT_LINE="ALL PASSED"
[ "$BUILD_EXIT" -eq 0 ] && [ "$PW_EXIT" -eq 0 ] || RESULT_LINE="SOME FAILED"
SUMMARY="
===== SUMMARY =====
build-and-test log: $BUILD_LOG
Surefire reports:   scripts/build-and-test/reports/surefire/
deploy-and-run + playwright log: $PW_LOG (exit $PW_EXIT)
$RESULT_LINE"
echo "$SUMMARY"
log_orchestrator "$SUMMARY"

if [ "$BUILD_EXIT" -ne 0 ] || [ "$PW_EXIT" -ne 0 ]; then
  emit_agentic_error_block "business" "false" "run-all-tests" "run-all-tests.sh finished with a failure (build-and-test exit $BUILD_EXIT, deploy+playwright exit $PW_EXIT) -- see $BUILD_LOG / $PW_LOG, not retryable as-is."
else
  emit_agentic_success_block "run-all-tests"
fi
exit $(( BUILD_EXIT != 0 || PW_EXIT != 0 ))
