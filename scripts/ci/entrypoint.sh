#!/bin/bash
# Runs *inside* the ci-runner container (scripts/ci/Dockerfile), never invoked directly -- use
# scripts/ci.sh (-> scripts/ci/run.sh) instead. Orchestrates the requested stages against an
# isolated ci-* stack, via Docker-outside-of-Docker: the host's docker.sock is mounted into this
# container, so every `docker`/`docker build`/`docker run` call here creates sibling containers on
# the *host*, not nested ones. See scripts/ci/DECISIONS.md ADR-001 for the full design rationale.
#
# Env vars (all set by scripts/ci/run.sh -- not meant to be set by hand):
#   STAGE_UNIT / STAGE_INTEGRATION / STAGE_E2E / STAGE_SONAR -- "1" to run that stage
#   SANDBOX      -- "1" to forward --sandbox to integration-tests.sh (this claude-dev sandbox only)
#   KEEP_INFRA   -- "1" to skip tearing down the isolated ci-* e2e stack after the run (debugging)
#   REPORT_ID    -- directory name used for this run's consolidated report output
#   PLAYWRIGHT_ARGS -- extra args forwarded to playwright/run.sh unchanged (e.g. "e2e --full --ux")

ROOT=/app
REPORT_DIR="$ROOT/ci-reports/${REPORT_ID:-run}"
mkdir -p "$REPORT_DIR"
OVERALL_EXIT=0

# ── Live progress file -- scripts/ci/run.sh (host side) periodically `docker cp`s the whole
#    report tree out while the container runs detached (not just this file), so completed stages'
#    reports appear on the host as soon as they're written, not only after the full run ends.
PROGRESS_FILE="$REPORT_DIR/progress.txt"
RUN_START_EPOCH=$(date +%s)
RUN_START_HUMAN=$(date '+%Y-%m-%d %H:%M:%S')
STAGES_ORDERED=()
declare -A STAGE_STATUS
declare -A STAGE_START_EPOCH
declare -A STAGE_START_HUMAN
declare -A STAGE_END_HUMAN
declare -A STAGE_ELAPSED

write_progress() {
  {
    echo "CI run: ${REPORT_ID:-run}"
    echo "Started:      $RUN_START_HUMAN"
    echo "Elapsed:      $(( $(date +%s) - RUN_START_EPOCH ))s"
    echo "Last updated: $(date '+%Y-%m-%d %H:%M:%S')"
    echo ""
    for s in "${STAGES_ORDERED[@]}"; do
      case "${STAGE_STATUS[$s]}" in
        DONE)
          printf '[x] %-12s %s -> %s  (%ss)  PASSED\n' \
            "$s" "${STAGE_START_HUMAN[$s]}" "${STAGE_END_HUMAN[$s]}" "${STAGE_ELAPSED[$s]}" ;;
        FAILED)
          printf '[x] %-12s %s -> %s  (%ss)  FAILED\n' \
            "$s" "${STAGE_START_HUMAN[$s]}" "${STAGE_END_HUMAN[$s]}" "${STAGE_ELAPSED[$s]}" ;;
        RUNNING)
          printf '[>] %-12s %s -> ...          RUNNING (%ss so far)\n' \
            "$s" "${STAGE_START_HUMAN[$s]}" "$(( $(date +%s) - STAGE_START_EPOCH[$s] ))" ;;
        PENDING)
          printf '[ ] %-12s pending\n' "$s" ;;
      esac
    done
  } > "$PROGRESS_FILE"
}

register_stage() {
  local name="$1"
  STAGES_ORDERED+=("$name")
  STAGE_STATUS[$name]="PENDING"
}

HEARTBEAT_PID=""

start_stage() {
  local name="$1"
  STAGE_STATUS[$name]="RUNNING"
  STAGE_START_EPOCH[$name]=$(date +%s)
  STAGE_START_HUMAN[$name]=$(date '+%H:%M:%S')
  write_progress
  echo ""
  echo "=== Stage: $name (started ${STAGE_START_HUMAN[$name]}) ==="
  # Background heartbeat: re-writes progress.txt every 5s while this stage's blocking command
  # runs, so "Elapsed"/"so far"/"Last updated" stay live for long stages (e2e is ~10 minutes) --
  # not just frozen at the values captured when the stage started. Safe as a forked subshell: the
  # associative arrays it inherited at fork time don't change again until this stage ends (nothing
  # else touches them mid-stage), and `date +%s` inside write_progress is computed fresh on every
  # call regardless.
  ( while true; do sleep 5; write_progress; done ) &
  HEARTBEAT_PID=$!
}

end_stage() {
  local name="$1" rc="$2"
  if [ -n "$HEARTBEAT_PID" ]; then
    kill "$HEARTBEAT_PID" 2>/dev/null
    wait "$HEARTBEAT_PID" 2>/dev/null
    HEARTBEAT_PID=""
  fi
  STAGE_END_HUMAN[$name]=$(date '+%H:%M:%S')
  STAGE_ELAPSED[$name]=$(( $(date +%s) - STAGE_START_EPOCH[$name] ))
  STAGE_STATUS[$name]=$([ "$rc" -eq 0 ] && echo DONE || echo FAILED)
  write_progress
  echo "$name exit: $rc (${STAGE_ELAPSED[$name]}s)"
}

[ "$STAGE_UNIT" = "1" ]        && register_stage unit
[ "$STAGE_INTEGRATION" = "1" ] && register_stage integration
[ "$STAGE_E2E" = "1" ]         && register_stage e2e
[ "$STAGE_SONAR" = "1" ]       && register_stage sonar
write_progress

# ── Isolated e2e stack -- unique names/ports so it never collides with the persistent dev stack,
#    forwarded as env overrides to the *existing*, unmodified scripts/deploy.sh + playwright/run.sh
#    (see their own override blocks) -- no e2e logic is reimplemented here. ────────────────────
CI_NETWORK=ci-advertisement
CI_DB_CONTAINER=ci-advertisement-db
CI_MINIO_CONTAINER=ci-advertisement-minio
CI_APP_CONTAINER=ci-marketplace-app
CI_APP_IMAGE=marketplace-app-ci
CI_DB_PORT=15432
CI_MINIO_PORT=19000
CI_MINIO_CONSOLE_PORT=19001
CI_APP_PORT=18081
CI_DB_VOLUME=ci_advertisement_postgres_data
CI_MINIO_VOLUME=ci_advertisement_minio_data
# Deliberately NOT torn down below -- a stateless tool container (just caches its own npm
# install), kept warm across runs exactly like the dev pw-runner already behaves.
CI_PW_CONTAINER=ci-pw-runner

teardown_e2e_stack() {
  if [ "$KEEP_INFRA" = "1" ]; then
    echo "KEEP_INFRA=1 -- leaving the ci-* e2e stack running (if it was started)."
    return
  fi
  docker rm -f "$CI_APP_CONTAINER" "$CI_DB_CONTAINER" "$CI_MINIO_CONTAINER" 2>/dev/null || true
  docker network rm "$CI_NETWORK" 2>/dev/null || true
}
cleanup_on_exit() {
  [ -n "$HEARTBEAT_PID" ] && kill "$HEARTBEAT_PID" 2>/dev/null
  teardown_e2e_stack
}
# Registered unconditionally (harmless no-op via `|| true` if the e2e stage never ran, or if no
# heartbeat is active) so any early exit/failure anywhere in this script still guarantees cleanup.
trap cleanup_on_exit EXIT

if [ "$STAGE_UNIT" = "1" ]; then
  start_stage unit
  bash "$ROOT/scripts/unit-tests.sh"
  RC=$?
  [ "$RC" -ne 0 ] && OVERALL_EXIT=1
  mkdir -p "$REPORT_DIR/unit-tests"
  cp -r "$ROOT/scripts/unit-tests/reports/." "$REPORT_DIR/unit-tests/" 2>/dev/null || true
  end_stage unit "$RC"
fi

if [ "$STAGE_INTEGRATION" = "1" ]; then
  start_stage integration
  SANDBOX_FLAG=""
  [ "$SANDBOX" = "1" ] && SANDBOX_FLAG="--sandbox"
  bash "$ROOT/scripts/integration-tests.sh" $SANDBOX_FLAG
  RC=$?
  [ "$RC" -ne 0 ] && OVERALL_EXIT=1
  mkdir -p "$REPORT_DIR/integration-tests"
  cp -r "$ROOT/integration-tests/reports/." "$REPORT_DIR/integration-tests/" 2>/dev/null || true
  end_stage integration "$RC"
fi

if [ "$STAGE_E2E" = "1" ]; then
  start_stage e2e

  NETWORK="$CI_NETWORK" DB_CONTAINER="$CI_DB_CONTAINER" MINIO_CONTAINER="$CI_MINIO_CONTAINER" \
  APP_CONTAINER="$CI_APP_CONTAINER" APP_IMAGE="$CI_APP_IMAGE" \
  DB_PORT="$CI_DB_PORT" MINIO_PORT="$CI_MINIO_PORT" MINIO_CONSOLE_PORT="$CI_MINIO_CONSOLE_PORT" \
  APP_PORT="$CI_APP_PORT" DB_VOLUME="$CI_DB_VOLUME" MINIO_VOLUME="$CI_MINIO_VOLUME" \
  bash "$ROOT/scripts/deploy.sh"
  DEPLOY_RC=$?

  if [ "$DEPLOY_RC" -eq 0 ]; then
    APP_URL="http://localhost:$CI_APP_PORT" APP_CONTAINER="$CI_APP_CONTAINER" \
    PW_CONTAINER="$CI_PW_CONTAINER" DB_PORT="$CI_DB_PORT" \
    bash "$ROOT/playwright/run.sh" $PLAYWRIGHT_ARGS
    RC=$?
  else
    echo "deploy.sh failed (exit $DEPLOY_RC) -- skipping playwright."
    RC=$DEPLOY_RC
  fi
  [ "$RC" -ne 0 ] && OVERALL_EXIT=1

  mkdir -p "$REPORT_DIR/playwright"
  cp -r "$ROOT/playwright/pw-report/." "$REPORT_DIR/playwright/" 2>/dev/null || true
  end_stage e2e "$RC"

  teardown_e2e_stack
fi

if [ "$STAGE_SONAR" = "1" ]; then
  start_stage sonar
  bash "$ROOT/scripts/sonar.sh"
  RC=$?
  [ "$RC" -ne 0 ] && OVERALL_EXIT=1
  mkdir -p "$REPORT_DIR/sonar"
  cp -r "$ROOT/scripts/sonar/report/." "$REPORT_DIR/sonar/" 2>/dev/null || true
  end_stage sonar "$RC"
fi

echo ""
echo "===== CI-RUNNER SUMMARY ====="
echo "Report dir (inside container): $REPORT_DIR"
if [ "$OVERALL_EXIT" -eq 0 ]; then
  echo "ALL REQUESTED STAGES PASSED"
  echo "RESULT: PASSED" >> "$PROGRESS_FILE"
else
  echo "SOME STAGES FAILED"
  echo "RESULT: FAILED" >> "$PROGRESS_FILE"
fi

exit $OVERALL_EXIT
