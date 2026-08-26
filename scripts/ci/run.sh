#!/bin/bash
# ── Header ──────────────────────────────────────────────────────────────────
# Description: Thin trigger over a persistent Dagu server. (Re)builds the ci-runner image,
#   (re)starts the ci-runner container (Docker-outside-of-Docker: docker.sock mounted, so it can
#   create/tear down its own isolated ci-* sibling containers) plus its ci-runner-dagu-proxy
#   sidecar, then fires a DAG run (scripts/ci/dagu/ci.yaml) inside it with the requested params.
#   Dagu replaces the orchestration/UI layer only -- every DAG step calls the exact same scripts
#   this project's other tools already use (build-and-test.sh, deploy-and-run.sh, playwright/
#   run.sh, sonar.sh).
# Usage: bash scripts/ci/run.sh [flags]
#   (no flags)               -- most extensive run: unit + integration + e2e + sonar +
#                                archunit_metrics + docs
#   --unit                   -- run the unit stage
#   --integration            -- run the integration stage (always applies the Testcontainers
#                                sandbox workaround internally -- see DECISIONS.md)
#   --e2e                    -- run the e2e stage
#   --sonar                  -- run the sonar stage
#   --all                    -- unit + integration + e2e (no sonar)
#   --no-docs                -- skip the doc-freshness stage
#   --playwright-args <arg>  -- override the e2e stage's Playwright args (default
#                                "e2e --full --ux")
#   --reset-e2e-db           -- deploy e2e's stack with a full --reset (DB/MinIO volume wipe, full
#                                Liquibase replay) instead of the default --reset-only-db (fast
#                                truncate) -- only needed when the DB schema itself changed since
#                                the isolated e2e stack was last brought up
#   --no-keep-e2e-infra      -- tear down the isolated e2e stack after the run instead of leaving
#                                it up (on by default -- leaving it up is the debugging-friendly
#                                default, so a failed run's containers/logs/DB state are still
#                                there to inspect without having to remember the flag ahead of time)
#   --foreground              -- block and stream this run's output instead of firing it and
#                                 returning immediately; also syncs artifacts (see Outputs)
#                                 automatically once the run finishes
#   --no-rebuild               -- trigger a new DAG run against the already-running ci-runner
#                                  container instead of rebuilding/recreating it
#   --refresh-tools              -- force re-download of buildx/compose/dagu into ci-tools-cache
#                                    even if already cached (e.g. after bumping DAGU_VERSION)
#   --no-archunit-metrics           -- skip ArchUnit's module-coupling export (on by default --
#                                      cheap compared to e2e, feeds
#                                      generate-architecture-model.sh --with-archunit)
#   --sync-artifacts                 -- pull whatever architecture-metrics.json/
#                                        pipeline-metrics.json the container has produced onto the
#                                        host, without triggering a new run (automatic after
#                                        --foreground; needed manually after a background run, or
#                                        after triggering a run directly from Dagu's own web UI)
# Uses: bash, docker, curl, scripts/utils/agentic-output.sh
#   (emit_agentic_success_block/emit_agentic_error_block).
# Env: None read directly -- every flag above is translated into either a container-start env var
#   (FORCE_TOOLS_REFRESH, passed via `docker run -e`) or a Dagu param (passed via
#   `dagu start ... -- key=value`).
# Input: scripts/ci/Dockerfile, scripts/ci/dagu/ci.yaml.
# Outputs: (unless --no-rebuild/--sync-artifacts) three lasting named volumes (ci-m2-cache,
#   ci-dagu-home, ci-tools-cache) and two persistent containers (ci-runner, ci-runner-dagu-proxy)
#   that remain running on the host after this script exits. The ci-runner container's Dagu web
#   UI, reachable at http://localhost:8082 through the ci-runner-dagu-proxy sidecar (ci-runner
#   itself runs --network host, whose bound ports aren't reachable from a real browser in this
#   sandbox -- see DECISIONS.md). With --foreground or --sync-artifacts, also refreshes
#   scripts/build-and-test/reports/architecture-metrics.json and
#   scripts/ci/reports/pipeline-metrics.json on the host.
# Returns: 0 on success; non-zero on an unrecognized flag, image-build failure, Dagu-startup
#   failure, or (--foreground only) a failed DAG run -- a backgrounded run always returns 0 once
#   triggered, regardless of how the DAG run itself later finishes. Also prints a single-line
#   AGENTIC_SUCCESS_BLOCK JSON marker on a clean finish (including a successfully-triggered
#   background run), or an AGENTIC_ERROR_BLOCK JSON marker
#   (errorCategory/isRetryable/currentStep/description/durationSeconds) on any failure path, for
#   an AI agent reading raw script output to parse machine-readable status instead of scraping
#   free text.
# ────────────────────────────────────────────────────────────────────────────

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SECONDS=0
source "$ROOT/scripts/utils/agentic-output.sh"
IMAGE=ci-runner
CONTAINER=ci-runner
DAGU_PORT=18080
UI_PROXY_CONTAINER=ci-runner-dagu-proxy
UI_PROXY_PORT=8082

STAGE_UNIT=""
STAGE_INTEGRATION=""
STAGE_E2E=""
STAGE_SONAR=""
STAGE_DOCS=1
KEEP_INFRA="true"
RESET_E2E_DB="false"
FOREGROUND=""
NO_REBUILD=""
REFRESH_TOOLS="false"
ARCHUNIT_METRICS="true"
SYNC_ARTIFACTS_ONLY=""
PLAYWRIGHT_ARGS="e2e --full --ux"
ANY_STAGE_FLAG=""

# `docker cp`'s "local" side always resolves in the caller's own filesystem -- run from here (the
# real host), not from inside a DAG step (confirmed directly: a step-side `docker cp` landed the
# file inside ci-runner's own filesystem, not the host's, even with docker.sock mounted). Copies
# whatever's present regardless of exit code, so a failed/partial run still surfaces whatever
# archunit_metrics/pipeline_metrics managed to produce before failing.
sync_artifacts() {
  mkdir -p "$ROOT/scripts/build-and-test/reports" "$ROOT/scripts/ci/reports"
  docker cp "$CONTAINER:/app/scripts/build-and-test/reports/architecture-metrics.json" \
    "$ROOT/scripts/build-and-test/reports/architecture-metrics.json" 2>/dev/null
  docker cp "$CONTAINER:/app/scripts/ci/reports/pipeline-metrics.json" \
    "$ROOT/scripts/ci/reports/pipeline-metrics.json" 2>/dev/null
}

NEXT=""
for arg in "$@"; do
  if [ -n "$NEXT" ]; then
    case "$NEXT" in
      playwright-args) PLAYWRIGHT_ARGS="$arg" ;;
    esac
    NEXT=""
    continue
  fi
  case "$arg" in
    --unit)            STAGE_UNIT=1; ANY_STAGE_FLAG=1 ;;
    --integration)      STAGE_INTEGRATION=1; ANY_STAGE_FLAG=1 ;;
    --e2e)               STAGE_E2E=1; ANY_STAGE_FLAG=1 ;;
    --sonar)              STAGE_SONAR=1; ANY_STAGE_FLAG=1 ;;
    --no-docs)             STAGE_DOCS="" ;;
    --all)                 STAGE_UNIT=1; STAGE_INTEGRATION=1; STAGE_E2E=1; ANY_STAGE_FLAG=1 ;;
    --no-keep-e2e-infra)   KEEP_INFRA="false" ;;
    --reset-e2e-db)        RESET_E2E_DB="true" ;;
    --foreground)          FOREGROUND=1 ;;
    --no-rebuild)          NO_REBUILD=1 ;;
    --refresh-tools)       REFRESH_TOOLS="true" ;;
    --no-archunit-metrics) ARCHUNIT_METRICS="false" ;;
    --sync-artifacts)      SYNC_ARTIFACTS_ONLY=1 ;;
    --playwright-args)     NEXT=playwright-args ;;
    *)
      echo "Unknown flag: $arg"
      echo "See usage at the top of scripts/ci/run.sh"
      emit_agentic_error_block "validation" "true" "arg-parsing" "Unknown flag: $arg -- fix the invocation and retry."
      exit 1
      ;;
  esac
done

if [ -n "$SYNC_ARTIFACTS_ONLY" ]; then
  sync_artifacts
  echo "Synced architecture-metrics.json/pipeline-metrics.json from $CONTAINER onto the host" \
       "(whatever was present -- no-op for either file the container hasn't produced yet)."
  emit_agentic_success_block "sync-artifacts"
  exit 0
fi

# No explicit stage flag at all -> default to the most extensive run (mirrors
# `playwright.sh e2e --full --ux` being the thorough option there).
if [ -z "$ANY_STAGE_FLAG" ]; then
  STAGE_UNIT=1
  STAGE_INTEGRATION=1
  STAGE_E2E=1
  STAGE_SONAR=1
fi

bool() { [ -n "$1" ] && echo true || echo false; }

if [ -z "$NO_REBUILD" ]; then
  echo "=== Building ci-runner image ==="
  docker build -f "$ROOT/scripts/ci/Dockerfile" -t "$IMAGE" "$ROOT"
  BUILD_RC=$?
  if [ "$BUILD_RC" -ne 0 ]; then
    echo "===== FAILED (ci-runner image build, exit $BUILD_RC) ====="
    emit_agentic_error_block "transient" "true" "build-image" "ci-runner image build failed with exit code $BUILD_RC."
    exit $BUILD_RC
  fi

  docker volume create ci-m2-cache >/dev/null
  docker volume create ci-dagu-home >/dev/null
  docker volume create ci-tools-cache >/dev/null

  docker rm -f "$CONTAINER" >/dev/null 2>&1

  echo ""
  echo "=== Starting ci-runner (Dagu server) ==="
  docker run -d --name "$CONTAINER" \
    --network host \
    -e FORCE_TOOLS_REFRESH="$REFRESH_TOOLS" \
    -v /var/run/docker.sock:/var/run/docker.sock \
    -v ci-m2-cache:/root/.m2 \
    -v ci-dagu-home:/root/.dagu \
    -v ci-tools-cache:/root/.ci-tools \
    "$IMAGE" >/dev/null

  echo "Waiting for Dagu's web UI to come up (first start downloads buildx/compose/dagu into" \
       "ci-tools-cache if not already cached there -- can take a minute)..."
  UP=""
  for _ in $(seq 1 120); do
    if curl -sf "http://localhost:$DAGU_PORT/" >/dev/null 2>&1; then
      UP=1
      break
    fi
    sleep 1
  done
  if [ -z "$UP" ]; then
    echo "===== FAILED (Dagu web UI never came up on :$DAGU_PORT -- check: docker logs $CONTAINER) ====="
    emit_agentic_error_block "transient" "true" "start-ci-runner" "Dagu web UI never came up on :$DAGU_PORT within the startup wait -- check: docker logs $CONTAINER."
    exit 1
  fi

  # Dagu UI proxy: $CONTAINER runs with --network host (needed so its own shell steps reach
  # sibling ci-* containers at plain localhost:PORT, same reasoning as DECISIONS.md's DooD design) -- but a
  # host-network container's bound ports are not forwarded to a real browser the way an explicit
  # `docker run -p` publish is, confirmed directly by comparing against marketplace-app's own
  # (bridge + `-p`) port, which is reachable. This tiny socat sidecar republishes $DAGU_PORT via a
  # normal bridge + `-p` so the same forwarding path that already works for every other container
  # in this project also carries Dagu's UI. `host.docker.internal` was tried first (the portable,
  # documented option) but resolved to an address that refused the connection in this sandbox --
  # confirmed directly, not assumed -- so the default bridge network's own gateway IP (where a
  # --network host container's bound ports are actually reachable from a bridge-network sibling)
  # is read directly from Docker instead of hardcoding a guessed address.
  BRIDGE_GATEWAY="$(docker network inspect bridge --format '{{(index .IPAM.Config 0).Gateway}}')"
  docker rm -f "$UI_PROXY_CONTAINER" >/dev/null 2>&1
  docker run -d --name "$UI_PROXY_CONTAINER" \
    -p "$UI_PROXY_PORT:$UI_PROXY_PORT" \
    alpine/socat "TCP-LISTEN:$UI_PROXY_PORT,fork,reuseaddr" "TCP:$BRIDGE_GATEWAY:$DAGU_PORT" >/dev/null

  echo "Dagu web UI is up: http://localhost:$UI_PROXY_PORT"
fi

DAGU_PARAMS=(
  "unit=$(bool "$STAGE_UNIT")"
  "integration=$(bool "$STAGE_INTEGRATION")"
  "e2e=$(bool "$STAGE_E2E")"
  "sonar=$(bool "$STAGE_SONAR")"
  "archunit_metrics=$ARCHUNIT_METRICS"
  "docs=$(bool "$STAGE_DOCS")"
  "keep_e2e_infra=$KEEP_INFRA"
  "reset_e2e_db=$RESET_E2E_DB"
  "e2e_args=$PLAYWRIGHT_ARGS"
)

echo ""
echo "=== Triggering ci DAG run (${DAGU_PARAMS[*]}) ==="

# A bare DAG name (`dagu start ci`) resolves against $DAGU_HOME/dags, not the --dags directory
# start-all was launched with -- confirmed directly, not assumed. The file path resolves correctly
# instead, relative to the image's WORKDIR (/app).
DAG_FILE=scripts/ci/dagu/ci.yaml

if [ -n "$FOREGROUND" ]; then
  docker exec "$CONTAINER" dagu start "$DAG_FILE" -- "${DAGU_PARAMS[@]}"
  EXIT_CODE=$?
  sync_artifacts
  echo ""
  if [ "$EXIT_CODE" -eq 0 ]; then
    echo "===== PASSED ====="
    emit_agentic_success_block "ci-run"
  else
    echo "===== FAILED (exit $EXIT_CODE) ====="
    emit_agentic_error_block "business" "false" "ci-run" "CI DAG run failed (exit $EXIT_CODE) -- one or more stages did not pass, not retryable as-is."
  fi
  echo "Full history: http://localhost:$UI_PROXY_PORT"
  exit $EXIT_CODE
else
  docker exec -d "$CONTAINER" dagu start "$DAG_FILE" -- "${DAGU_PARAMS[@]}"
  echo ""
  echo "DAG run triggered in the background."
  echo "Watch live status/logs: http://localhost:$UI_PROXY_PORT"
  echo "Once it finishes, run 'bash scripts/ci/run.sh --sync-artifacts' to pull" \
       "architecture-metrics.json/pipeline-metrics.json onto the host."
  emit_agentic_success_block "trigger-background-run"
fi
