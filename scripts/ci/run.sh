#!/bin/bash
# Local, isolated, parameterized CI runner (improvement-059).
#
# Usage:
#   bash scripts/ci/run.sh                                   — default: most extensive run
#                                                                 (unit + integration + e2e + sonar,
#                                                                 e2e's Playwright pass uses
#                                                                 "e2e --full --ux"), runs in the
#                                                                 background (see below)
#   bash scripts/ci/run.sh --unit --integration --e2e         — chosen stages only
#   bash scripts/ci/run.sh --all                              — unit + integration + e2e (no sonar)
#   bash scripts/ci/run.sh --all --sonar                       — + SonarQube analysis
#   bash scripts/ci/run.sh --playwright-args "e2e --ux"        — override the e2e stage's
#                                                                  Playwright args (default when
#                                                                  unset: "e2e --full --ux")
#   bash scripts/ci/run.sh --report-dir /some/path              — configurable report destination
#   bash scripts/ci/run.sh --keep-reports 5                      — keep the last N report dirs,
#                                                                    prune older ones (default: 3;
#                                                                    0 = never prune)
#   bash scripts/ci/run.sh --keep-infra                           — don't tear down the isolated
#                                                                     e2e stack after (debugging)
#   bash scripts/ci/run.sh --sandbox                               — this claude-dev sandbox's
#                                                                      Testcontainers workaround
#   bash scripts/ci/run.sh --foreground                             — block and stream output
#                                                                       instead of the default
#                                                                       background run (this is
#                                                                       what a Monitor+tee-based
#                                                                       invocation should use)
#
# Background by default: the image build runs in the foreground (fast feedback if it fails), then
# the actual test run detaches -- the script prints the PID and report paths and returns control
# immediately. Check progress anytime with:
#   cat scripts/ci/reports/<timestamp>/progress.txt
#
# One CI-runner container (scripts/ci/Dockerfile), built fresh from the current source tree and run
# with the host's /var/run/docker.sock mounted (Docker-outside-of-Docker) -- it can then create and
# tear down its own isolated ci-* sibling containers (Postgres, MinIO, the built app, Playwright)
# without ever touching the persistent dev stack (marketplace-app/advertisement-db/...). Maven
# dependencies are cached across runs via the ci-m2-cache named volume. See scripts/ci/entrypoint.sh
# for the in-container orchestration and scripts/ci/DECISIONS.md ADR-001 for the full design.

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
IMAGE=ci-runner

STAGE_UNIT=""
STAGE_INTEGRATION=""
STAGE_E2E=""
STAGE_SONAR=""
SANDBOX=""
KEEP_INFRA=""
FOREGROUND=""
REPORT_DIR="$ROOT/scripts/ci/reports"
PLAYWRIGHT_ARGS="e2e --full --ux"
KEEP_REPORTS=3
ANY_STAGE_FLAG=""

NEXT=""
for arg in "$@"; do
  if [ -n "$NEXT" ]; then
    case "$NEXT" in
      report-dir)       REPORT_DIR="$arg" ;;
      playwright-args)  PLAYWRIGHT_ARGS="$arg" ;;
      keep-reports)     KEEP_REPORTS="$arg" ;;
    esac
    NEXT=""
    continue
  fi
  case "$arg" in
    --unit)            STAGE_UNIT=1; ANY_STAGE_FLAG=1 ;;
    --integration)      STAGE_INTEGRATION=1; ANY_STAGE_FLAG=1 ;;
    --e2e)               STAGE_E2E=1; ANY_STAGE_FLAG=1 ;;
    --sonar)              STAGE_SONAR=1; ANY_STAGE_FLAG=1 ;;
    --all)                 STAGE_UNIT=1; STAGE_INTEGRATION=1; STAGE_E2E=1; ANY_STAGE_FLAG=1 ;;
    --sandbox)             SANDBOX=1 ;;
    --keep-infra)          KEEP_INFRA=1 ;;
    --foreground)          FOREGROUND=1 ;;
    --report-dir)          NEXT=report-dir ;;
    --playwright-args)     NEXT=playwright-args ;;
    --keep-reports)        NEXT=keep-reports ;;
    *)
      echo "Unknown flag: $arg"
      echo "See usage at the top of scripts/ci/run.sh"
      exit 1
      ;;
  esac
done

# No explicit stage flag at all -> default to the most extensive run (mirrors
# `playwright.sh e2e --full --ux` being the thorough option there).
if [ -z "$ANY_STAGE_FLAG" ]; then
  STAGE_UNIT=1
  STAGE_INTEGRATION=1
  STAGE_E2E=1
  STAGE_SONAR=1
fi

REPORT_ID="$(date +%Y%m%d-%H%M%S)"
RUN_REPORT_DIR="$REPORT_DIR/$REPORT_ID"
mkdir -p "$RUN_REPORT_DIR"
CONTAINER="ci-runner-$REPORT_ID"

echo "=== Building ci-runner image ==="
docker build -f "$ROOT/scripts/ci/Dockerfile" -t "$IMAGE" "$ROOT"
BUILD_RC=$?
if [ "$BUILD_RC" -ne 0 ]; then
  echo "===== FAILED (ci-runner image build, exit $BUILD_RC) ====="
  exit $BUILD_RC
fi

docker volume create ci-m2-cache >/dev/null

run_and_collect() {
  docker run -d --name "$CONTAINER" \
    --network host \
    -v /var/run/docker.sock:/var/run/docker.sock \
    -v ci-m2-cache:/root/.m2 \
    -e STAGE_UNIT="$STAGE_UNIT" -e STAGE_INTEGRATION="$STAGE_INTEGRATION" \
    -e STAGE_E2E="$STAGE_E2E" -e STAGE_SONAR="$STAGE_SONAR" \
    -e SANDBOX="$SANDBOX" -e KEEP_INFRA="$KEEP_INFRA" -e REPORT_ID="$REPORT_ID" \
    -e PLAYWRIGHT_ARGS="$PLAYWRIGHT_ARGS" \
    "$IMAGE" >/dev/null

  if [ -n "$FOREGROUND" ]; then
    docker logs -f "$CONTAINER"
  else
    # Poll the whole report tree out every 5s while the container runs -- not just progress.txt,
    # so a stage's reports (unit-tests/, integration-tests/, ...) show up on the host as soon as
    # that stage finishes inside the container, not only after the entire run ends. Bind mounts
    # don't work from inside this sandbox (same constraint playwright/CLAUDE.md documents), so
    # `docker cp` is the only way to surface this live.
    while [ "$(docker inspect -f '{{.State.Running}}' "$CONTAINER" 2>/dev/null)" = "true" ]; do
      docker cp "$CONTAINER:/app/ci-reports/$REPORT_ID/." "$RUN_REPORT_DIR/" 2>/dev/null
      sleep 5
    done
  fi

  EXIT_CODE=$(docker wait "$CONTAINER" 2>/dev/null)
  EXIT_CODE=${EXIT_CODE:-1}

  docker cp "$CONTAINER:/app/ci-reports/$REPORT_ID/." "$RUN_REPORT_DIR/" 2>/dev/null
  docker rm -f "$CONTAINER" >/dev/null 2>&1

  if [ "$KEEP_REPORTS" -gt 0 ] 2>/dev/null; then
    ls -1dt "$REPORT_DIR"/*/ 2>/dev/null | tail -n +$(( KEEP_REPORTS + 1 )) | xargs -r rm -rf
  fi

  {
    echo ""
    if [ "$EXIT_CODE" -eq 0 ]; then
      echo "===== PASSED ====="
    else
      echo "===== FAILED (exit $EXIT_CODE) ====="
    fi
    echo "Reports: $RUN_REPORT_DIR/"
  } >> "$RUN_REPORT_DIR/progress.txt"

  return "$EXIT_CODE"
}

echo ""
echo "=== Running ci-runner (unit=${STAGE_UNIT:-0} integration=${STAGE_INTEGRATION:-0}" \
     "e2e=${STAGE_E2E:-0} sonar=${STAGE_SONAR:-0}) ==="

if [ -n "$FOREGROUND" ]; then
  run_and_collect
  EXIT_CODE=$?
  echo ""
  if [ "$EXIT_CODE" -eq 0 ]; then
    echo "===== PASSED ====="
  else
    echo "===== FAILED (exit $EXIT_CODE) ====="
  fi
  echo "Reports: $RUN_REPORT_DIR/"
  exit $EXIT_CODE
else
  ( run_and_collect ) </dev/null >"$RUN_REPORT_DIR/run.log" 2>&1 &
  BGPID=$!
  disown
  echo "Running in background (PID $BGPID)."
  echo "Progress:  $RUN_REPORT_DIR/progress.txt"
  echo "Full log:  $RUN_REPORT_DIR/run.log"
  echo "Check anytime: cat $RUN_REPORT_DIR/progress.txt"
  exit 0
fi
