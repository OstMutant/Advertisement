#!/usr/bin/env bash
# ── Header ──────────────────────────────────────────────────────────────────
# Description: Thin trigger over a persistent Dagu server. Rebuilds the ci-runner image only when
#   scripts/ci/Dockerfile or scripts/ci/docker-entrypoint.sh changed since the image was built,
#   the image is missing, or --rebuild is passed; otherwise reuses the running ci-runner container
#   (Docker-outside-of-Docker: docker.sock mounted, so it can create/tear down its own isolated ci-* sibling
#   containers) plus its ci-runner-dagu-proxy sidecar. Every run, before firing the DAG, it
#   streams the current working tree into the container's /app via tar -- the image's own
#   `COPY . .` is not trusted as the source of truth (Docker layer-cache staleness), copies ci.yaml
#   into the Dagu DAG directory outside /app (the sync wipes /app), and then fires a DAG run
#   (scripts/ci/dagu/ci.yaml) with the requested params. Dagu replaces the orchestration/
#   UI layer only -- every DAG step calls the exact same scripts this project's other tools
#   already use (build-and-test.sh, deploy-and-run.sh, playwright/run.sh, sonar.sh).
# Usage: bash scripts/ci/run.sh [flags]
#   (no flags)               -- most extensive run: unit + integration + e2e + api_flows + sonar +
#                                archunit_metrics + lint + shellcheck + docs
#   --unit                   -- run the unit stage
#   --integration            -- run the integration stage (always applies the Testcontainers
#                                sandbox workaround internally -- see DECISIONS.md)
#   --e2e                    -- run the e2e stage
#   --api-flows              -- run the API collection flows (REST and MCP, once per role) on their own stack
#   --sonar                  -- run the sonar stage
#   --all                    -- unit + integration + e2e + api_flows (no sonar)
#   --docs-only              -- skip unit/integration/e2e/api_flows/sonar/archunit_metrics/lint/shellcheck
#                                entirely (build then straight to pipeline_metrics/docs) -- the
#                                fastest real path for testing the docs stage/sync_artifacts
#                                alone, minutes not dozens of minutes
#   --no-docs                -- skip the docs stage (regenerates architecture-model.json/
#                                architecture-map.html inside the container -- see ci.yaml)
#   --playwright-args <arg>  -- override the e2e stage's Playwright args (default
#                                "e2e --full --ux")
#   --reset-e2e-stack        -- deploy e2e's stack with a full --reset (DB/SeaweedFS volume wipe,
#                                full Liquibase replay) instead of the default --reset-only-db
#                                (fast truncate) -- only needed when the DB schema itself changed
#                                since the isolated e2e stack was last brought up
#   --reset-ci-cache         -- wipe the CI's own build caches (ci-maven-cache and ci-vaadin-cache) and remove the
#                                Sonar scanner container before the build, so the run starts with no cached
#                                dependencies, jars or Node.js (off by default; the next run downloads them again)
#   --teardown-e2e-after     -- tear down the isolated e2e stack after the run (off by default --
#                                leaving it up is the debugging-friendly default, so a failed run's
#                                containers/logs/DB state are still there to inspect without having
#                                to remember a flag ahead of time)
#   --foreground              -- block and stream this run's output instead of firing it and
#                                 returning immediately; also syncs artifacts (see Outputs)
#                                 automatically once the run finishes
#   --rebuild                  -- force an image rebuild + container recreation even when the
#                                  Dockerfile/entrypoint are unchanged (normally the image is
#                                  rebuilt only when they change; the working tree is streamed in
#                                  fresh every run regardless)
#   --no-archunit-metrics           -- skip ArchUnit's module-coupling export (on by default --
#                                      cheap compared to e2e, feeds
#                                      generate-architecture-model.sh --with-archunit)
#   --no-lint                        -- skip the ESLint stage (on by default -- delegates to
#                                        playwright/run.sh --lint, cheap, no app/DB needed)
#   --no-shellcheck                   -- skip the ShellCheck stage (on by default -- error severity
#                                         only, see ci.yaml; shellcheck itself is baked into the
#                                         ci-runner image, no other container involved)
#   --sync-artifacts                 -- pull whatever architecture-metrics.json/
#                                        pipeline-metrics.json/architecture-model.json/
#                                        architecture-map.html/Playwright report/unit+integration
#                                        Surefire+logs/Sonar report+log the run has produced onto
#                                        the host, without triggering a new run (automatic after
#                                        --foreground; needed manually after a background run, or
#                                        after triggering a run directly from Dagu's own web UI)
# Uses: bash, docker, curl, scripts/utils/agentic-output.sh
#   (emit_agentic_success_block/emit_agentic_error_block), scripts/utils/project-label.sh,
#   scripts/utils/session-id.sh, scripts/utils/sync-source.sh, scripts/utils/docker-pull-retry.sh
#   (docker_pull_missing_bases, docker_pull_missing_retry), python3
#   (--foreground only -- scripts/ci/dagu-rest-run-monitor.py polls the run and emits its own real
#   per-step markers).
# Env: None read directly -- every flag above is translated into a Dagu param (passed via
#   `dagu start ... -- key=value`).
# Input: scripts/ci/Dockerfile, scripts/ci/docker-entrypoint.sh (mtime vs the image decides
#   whether to rebuild), scripts/ci/dagu/ci.yaml, and the working tree itself -- the `git ls-files`
#   set (tracked + untracked-not-.gitignored) is streamed into the container's /app before every
#   run, so build output, node_modules, .git and report/log dirs are excluded for free.
# Outputs: (unless --sync-artifacts) three lasting named volumes (ci-m2-cache,
#   ci-dagu-home, ci-tools-cache) and two persistent containers (ci-runner, ci-runner-dagu-proxy)
#   that remain running on the host after this script exits. The ci-runner container's Dagu web
#   UI, reachable at http://localhost:8082 through the ci-runner-dagu-proxy sidecar (ci-runner
#   itself runs --network host, whose bound ports aren't reachable from a real browser in this
#   sandbox -- see DECISIONS.md). With --foreground or --sync-artifacts, also refreshes onto the
#   host: scripts/build-and-test/reports/architecture-metrics.json, scripts/ci/reports/
#   pipeline-metrics.json, (whenever the docs stage ran and regenerated them)
#   docs/architecture/data/architecture-model.json, docs/architecture/architecture-map.html and
#   .claude/nav/adr-index.md,
#   plus (whenever the matching stage ran) playwright/pw-report/, scripts/logs/playwright/,
#   scripts/build-and-test/reports/advertisement-build-only-*/,
#   scripts/logs/build-and-test/advertisement-build-only-*/,
#   integration-tests/reports/, scripts/sonar/report/report.html, and scripts/logs/sonar/.
# Returns: 0 on success; non-zero on an unrecognized flag, image-build failure, Dagu-startup
#   failure, a ci DAG run already being in progress (refused: concurrent runs collide on the
#   shared e2e ci-* stack), or (--foreground only) either a failed DAG stage or a failed
#   docker cp of architecture-model.json/architecture-map.html/adr-index.md back to the host --
#   these last two are reported distinctly ("A ci DAG stage failed" vs "Every ci DAG stage passed,
#   but ... sync ... failed") so the failure line names where it broke. A backgrounded run always
#   returns 0 once triggered, regardless of how the DAG run itself later finishes. Each run is
#   given a self-assigned id (`dagu start -r`), printed as "Dagu run id: <id>", surfaced as the
#   tree.txt header line (AGENTIC_CONTEXT), and echoed again in the PASSED/FAILED line, so it's
#   unambiguous which run any output refers to. Also
#   prints a single-line
#   AGENTIC_SUCCESS_BLOCK JSON marker on a clean finish (including a successfully-triggered
#   background run), or an AGENTIC_ERROR_BLOCK JSON marker
#   (errorCategory/isRetryable/currentStep/description/durationSeconds) on any failure path, for
#   an AI agent reading raw script output to parse machine-readable status instead of scraping
#   free text.
# ────────────────────────────────────────────────────────────────────────────

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SECONDS=0
source "$ROOT/scripts/utils/agentic-output.sh"
source "$ROOT/scripts/utils/project-label.sh"
source "$ROOT/scripts/utils/session-id.sh"
source "$ROOT/scripts/utils/docker-pull-retry.sh"
# The volume name is read from the DAG file, the single place that sets it.
CI_TEST_REPORTS_VOLUME="$(sed -n 's/^  - TEST_REPORTS_VOLUME=//p' "$ROOT/scripts/ci/dagu/ci.yaml")"
[ -n "$CI_TEST_REPORTS_VOLUME" ] || { echo "ERROR: TEST_REPORTS_VOLUME not found in scripts/ci/dagu/ci.yaml" >&2; exit 1; }
IMAGE=ci-runner
CONTAINER=ci-runner
DAGU_PORT=18080
UI_PROXY_CONTAINER=ci-runner-dagu-proxy
UI_PROXY_PORT=8082

STAGE_UNIT=""
STAGE_INTEGRATION=""
STAGE_E2E=""
STAGE_API_FLOWS=""
STAGE_SONAR=""
STAGE_DOCS=1
TEARDOWN_AFTER="false"
RESET_E2E_STACK="false"
RESET_CI_CACHE="false"
FOREGROUND=""
REBUILD=""
ARCHUNIT_METRICS="true"
LINT="true"
SHELLCHECK="true"
SYNC_ARTIFACTS_ONLY=""
PLAYWRIGHT_ARGS="e2e --full --ux"
ANY_STAGE_FLAG=""

#######################################
# Copies a container file to the host: a direct docker cp first, and only when that fails a copy to a temp file that a throwaway
# bind-mount container renames into place (a WSL2 DrvFs mount refuses an in-place overwrite with "permission denied", also for cp -f and mv;
# a rename succeeds). It runs here, on the real host, because docker cp resolves its local side in the caller's own filesystem.
# The log of every copy is appended inside $CONTAINER (/tmp/ci-sync-diag.log), the one place whoever triggered the run can read.
# Callers whose file the docs stage regenerates in place treat a non-zero return as a failed sync; metrics copies are best-effort.
# Arguments: $1 source (container:path), $2 host destination, $3 label for the log.
# Returns: 0 when the file reached the host, non-zero otherwise.
#######################################
docker_cp_diag() {
  local src="$1" dst="$2" label="$3" out rc tmp tmp_dir tmp_name dst_dir dst_name direct_out
  direct_out="$(docker cp "$src" "$dst" 2>&1 >/dev/null)"
  rc=$?
  if [ "$rc" -eq 0 ]; then
    out="$direct_out"
  else
    tmp="$(mktemp)"
    out="$(docker cp "$src" "$tmp" 2>&1 >/dev/null)"
    rc=$?
    if [ "$rc" -eq 0 ]; then
      # A single-file bind mount of a not-yet-existing path can create a directory there -- mount the temp file's directory instead.
      tmp_dir="$(dirname "$tmp")"
      tmp_name="$(basename "$tmp")"
      dst_dir="$(cd "$(dirname "$dst")" && pwd)"
      dst_name="$(basename "$dst")"
      if out="$(docker run --rm -v "$tmp_dir:/src:ro" -v "$dst_dir:/out" alpine \
          sh -c "cp -f '/src/$tmp_name' '/out/.$dst_name.ci-sync-tmp' && mv -f '/out/.$dst_name.ci-sync-tmp' '/out/$dst_name'" 2>&1)"; then
        rc=0
      else
        rc=1
      fi
    fi
    rm -f "$tmp"
    out="direct copy failed: $direct_out
$out"
  fi
  {
    echo "=== $(date -u +%FT%TZ) $label rc=$rc ==="
    [ -n "$out" ] && echo "$out"
  } | docker exec -i "$CONTAINER" sh -c 'cat >> /tmp/ci-sync-diag.log' 2>/dev/null
  return $rc
}

sync_artifacts() {
  docker_pull_missing_retry alpine
  mkdir -p "$ROOT/scripts/build-and-test/reports" "$ROOT/scripts/ci/reports"
  docker cp "$CONTAINER:/app/scripts/build-and-test/reports/architecture-metrics.json" \
    "$ROOT/scripts/build-and-test/reports/architecture-metrics.json" 2>/dev/null
  docker cp "$CONTAINER:/app/scripts/ci/reports/pipeline-metrics.json" \
    "$ROOT/scripts/ci/reports/pipeline-metrics.json" 2>/dev/null

  local docs_synced=0
  docker exec "$CONTAINER" sh -c ': > /tmp/ci-sync-diag.log' 2>/dev/null
  docker_cp_diag "$CONTAINER:/app/docs/architecture/data/architecture-model.json" \
    "$ROOT/docs/architecture/data/architecture-model.json" "architecture-model.json" || docs_synced=1
  docker_cp_diag "$CONTAINER:/app/docs/architecture/architecture-map.html" \
    "$ROOT/docs/architecture/architecture-map.html" "architecture-map.html" || docs_synced=1

  # adr-index.md is regenerated in-container by the docs stage; copy it back too -- docker cp's own exit code is the check, a failed copy sets docs_synced.
  docker_cp_diag "$CONTAINER:/app/.claude/nav/adr-index.md" \
    "$ROOT/.claude/nav/adr-index.md" "adr-index.md" || docs_synced=1

  # Pulls test-result artifacts out of the shared test-reports volume via a throwaway container; best-effort, errors suppressed.
  local VOL_READER="ci-artifact-reader"
  docker rm -f "$VOL_READER" >/dev/null 2>&1
  docker run -d --name "$VOL_READER" --label "$PROJECT_LABEL" -v "$CI_TEST_REPORTS_VOLUME:/reports" alpine sleep 60 >/dev/null 2>&1
  local STAGE_DIRS
  # Oldest first, so the newest stage folder is copied last and wins for the shared it-mirror target.
  STAGE_DIRS="$(docker exec "$VOL_READER" sh -c 'ls -tr /reports' 2>/dev/null | grep '^advertisement-build-only-')"
  for name in $STAGE_DIRS; do
    docker cp "$VOL_READER:/reports/$name/." "$ROOT/scripts/build-and-test/reports/$name/" 2>/dev/null
    docker cp "$VOL_READER:/reports/logs/$name/." "$ROOT/scripts/logs/build-and-test/$name/" 2>/dev/null
  done
  for name in $STAGE_DIRS; do
    docker cp "$VOL_READER:/reports/$name/it-mirror/." "$ROOT/integration-tests/reports/" 2>/dev/null
  done
  docker cp "$VOL_READER:/reports/playwright/." "$ROOT/playwright/pw-report/" 2>/dev/null
  docker cp "$VOL_READER:/reports/playwright-log/." "$ROOT/scripts/logs/playwright/" 2>/dev/null
  docker cp "$VOL_READER:/reports/sonar/." "$ROOT/scripts/logs/sonar/" 2>/dev/null
  docker rm -f "$VOL_READER" >/dev/null 2>&1

  # session-info.txt lives only in the ci-runner filesystem, not in the test-reports volume.
  for sub in playwright sonar build-and-test $(for name in $STAGE_DIRS; do echo "build-and-test/$name"; done); do
    docker cp "$CONTAINER:/app/scripts/logs/$sub/session-info.txt" "$ROOT/scripts/logs/$sub/session-info.txt" 2>/dev/null || true
  done
  docker cp "$CONTAINER:/app/scripts/logs/build-and-test/session-info.txt" "$ROOT/scripts/ci/reports/session-info.txt" 2>/dev/null || true

  # Sonar's report.html only ever exists in $CONTAINER's own filesystem, not the test-reports volume -- this leg writes to the real host, so it needs docker_cp_diag, not a plain docker cp.
  local sonar_report_failed=0
  docker_cp_diag "$CONTAINER:/app/scripts/sonar/report/report.html" \
    "$ROOT/scripts/sonar/report/report.html" "report.html" || sonar_report_failed=1
  # report.html only exists when this run's own sonar stage actually ran -- only STAGE_SONAR runs count a missing copy as a real failure.
  if [ "$sonar_report_failed" -ne 0 ] && [ -n "$STAGE_SONAR" ]; then
    docs_synced=1
  fi

  if [ "$docs_synced" -ne 0 ]; then
    echo "docker cp errors (also saved inside $CONTAINER at /tmp/ci-sync-diag.log):"
    docker exec "$CONTAINER" cat /tmp/ci-sync-diag.log 2>/dev/null
  fi

  return $docs_synced
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
    --api-flows)          STAGE_API_FLOWS=1; ANY_STAGE_FLAG=1 ;;
    --sonar)              STAGE_SONAR=1; ANY_STAGE_FLAG=1 ;;
    --docs-only)           ANY_STAGE_FLAG=1; ARCHUNIT_METRICS="false"; LINT="false"; SHELLCHECK="false" ;;
    --no-docs)             STAGE_DOCS="" ;;
    --all)                 STAGE_UNIT=1; STAGE_INTEGRATION=1; STAGE_E2E=1; STAGE_API_FLOWS=1; ANY_STAGE_FLAG=1 ;;
    --teardown-e2e-after)  TEARDOWN_AFTER="true" ;;
    --reset-e2e-stack)     RESET_E2E_STACK="true" ;;
    --reset-ci-cache)      RESET_CI_CACHE="true" ;;
    --foreground)          FOREGROUND=1 ;;
    --rebuild)             REBUILD=1 ;;
    --no-archunit-metrics) ARCHUNIT_METRICS="false" ;;
    --no-lint)             LINT="false" ;;
    --no-shellcheck)       SHELLCHECK="false" ;;
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
  if sync_artifacts; then
    echo "Synced architecture-metrics.json/pipeline-metrics.json/architecture-model.json/" \
         "architecture-map.html, plus whichever of Playwright report/unit+integration" \
         "Surefire+logs/Sonar report+log the run produced, from $CONTAINER and the" \
         "test-reports volume onto the host."
    emit_agentic_success_block "sync-artifacts"
    exit 0
  else
    echo "Synced whatever else was available, but failed to copy" \
         "architecture-model.json/architecture-map.html from $CONTAINER onto the host."
    emit_agentic_error_block "transient" "true" "sync-artifacts" "docker cp of architecture-model.json/architecture-map.html/adr-index.md failed -- is $CONTAINER running?"
    exit 1
  fi
fi

# No explicit stage flag at all -> default to the most extensive run.
if [ -z "$ANY_STAGE_FLAG" ]; then
  STAGE_UNIT=1
  STAGE_INTEGRATION=1
  STAGE_E2E=1
  STAGE_API_FLOWS=1
  STAGE_SONAR=1
fi

bool() { [ -n "$1" ] && echo true || echo false; }

# ── ci-runner lifecycle: build only when the Dockerfile/entrypoint is newer than the image (its cached COPY layer can be stale) ──
IMAGE_CREATED_EPOCH="$(date -d "$(docker image inspect -f '{{.Created}}' "$IMAGE" 2>/dev/null)" +%s 2>/dev/null || echo 0)"
DOCKERFILE_MTIME="$(stat -c %Y "$ROOT/scripts/ci/Dockerfile" "$ROOT/scripts/ci/docker-entrypoint.sh" 2>/dev/null | sort -n | tail -1)"
DOCKERFILE_MTIME="${DOCKERFILE_MTIME:-0}"

NEED_BUILD=""
if [ -n "$REBUILD" ] \
   || ! docker image inspect "$IMAGE" >/dev/null 2>&1 \
   || [ "$DOCKERFILE_MTIME" -gt "$IMAGE_CREATED_EPOCH" ]; then
  NEED_BUILD=1
fi

NEED_CONTAINER=""
if [ -n "$NEED_BUILD" ] \
   || [ "$(docker inspect -f '{{.State.Running}}' "$CONTAINER" 2>/dev/null)" != "true" ] \
   || [ "$(docker inspect -f '{{.State.Running}}' "$UI_PROXY_CONTAINER" 2>/dev/null)" != "true" ]; then
  NEED_CONTAINER=1
fi

if [ -n "$NEED_BUILD" ]; then
  echo "=== Building ci-runner image ==="
  docker_pull_missing_bases "$ROOT/scripts/ci/Dockerfile"
  docker build -f "$ROOT/scripts/ci/Dockerfile" -t "$IMAGE" "$ROOT"
  BUILD_RC=$?
  if [ "$BUILD_RC" -ne 0 ]; then
    echo "===== FAILED (ci-runner image build, exit $BUILD_RC) ====="
    emit_agentic_error_block "transient" "true" "build-image" "ci-runner image build failed with exit code $BUILD_RC."
    exit $BUILD_RC
  fi
fi
emit_agentic_success_block "build-image"

if [ -n "$NEED_CONTAINER" ]; then
  docker volume create ci-m2-cache >/dev/null
  docker volume create ci-dagu-home >/dev/null
  docker volume create ci-tools-cache >/dev/null

  docker rm -f "$CONTAINER" >/dev/null 2>&1

  echo ""
  echo "=== Starting ci-runner (Dagu server) ==="
  # Addressed by ID below because a container name can be reused by another container, silently redirecting name-based inspect/stats.
  CONTAINER_ID="$(docker run -d --name "$CONTAINER" \
    --label "$PROJECT_LABEL" \
    --network host \
    -v /var/run/docker.sock:/var/run/docker.sock \
    -v ci-m2-cache:/root/.m2 \
    -v ci-dagu-home:/root/.dagu \
    -v ci-tools-cache:/root/.ci-tools \
    "$IMAGE")"

  echo "Waiting for Dagu's web UI to come up (first start downloads buildx/compose/dagu into" \
       "ci-tools-cache if not already cached there -- real duration depends on network speed, not" \
       "a fixed guess)..."

  # Readiness uses docker exec curl because a direct curl cannot reach the --network host container unless this script shares the host network.
  dagu_ready() {
    docker exec "$CONTAINER_ID" curl -sf "http://localhost:$DAGU_PORT/" >/dev/null 2>&1
  }

  TYPICAL_WAIT_SECONDS=120
  UP=""
  for _ in $(seq 1 "$TYPICAL_WAIT_SECONDS"); do
    if dagu_ready; then
      UP=1
      break
    fi
    sleep 1
  done

  # Past the typical wait, keep waiting while the container is alive and using CPU (the tool download); fail once it dies or goes idle.
  EXTENDED_CHECK_INTERVAL_SECONDS=30
  EXTENDED_WAIT_DEADLINE=$(( $(date +%s) + 600 ))
  while [ -z "$UP" ] && [ "$(date +%s)" -lt "$EXTENDED_WAIT_DEADLINE" ]; do
    if [ "$(docker inspect -f '{{.State.Running}}' "$CONTAINER_ID" 2>/dev/null)" != "true" ]; then
      echo "===== FAILED (ci-runner container exited unexpectedly during startup -- container id $CONTAINER_ID) ====="
      docker logs --tail 30 "$CONTAINER_ID" 2>&1
      emit_agentic_error_block "transient" "true" "start-ci-runner" "ci-runner container (id $CONTAINER_ID) exited during startup, before Dagu's web UI ever came up -- see the printed logs above."
      exit 1
    fi
    CPU_PCT="$(docker stats --no-stream --format '{{.CPUPerc}}' "$CONTAINER_ID" 2>/dev/null | tr -d '%')"
    if [ -z "$CPU_PCT" ] || [ "$(awk -v cpu="$CPU_PCT" 'BEGIN { print (cpu+0 >= 1) ? 1 : 0 }')" != "1" ]; then
      echo "===== FAILED (ci-runner container is alive but idle -- CPU ${CPU_PCT:-unknown}%, no longer doing real work -- container id $CONTAINER_ID) ====="
      docker logs --tail 30 "$CONTAINER_ID" 2>&1
      emit_agentic_error_block "transient" "true" "start-ci-runner" "ci-runner container (id $CONTAINER_ID) went idle (CPU ${CPU_PCT:-unknown}%) before Dagu's web UI ever came up -- see the printed logs above."
      exit 1
    fi
    echo "Still working (CPU ${CPU_PCT}%) -- waiting another ${EXTENDED_CHECK_INTERVAL_SECONDS}s..."
    sleep "$EXTENDED_CHECK_INTERVAL_SECONDS"
    dagu_ready && UP=1
  done

  if [ -z "$UP" ]; then
    echo "===== FAILED (Dagu web UI never came up on :$DAGU_PORT -- container id $CONTAINER_ID) ====="
    docker logs --tail 30 "$CONTAINER_ID" 2>&1
    emit_agentic_error_block "transient" "true" "start-ci-runner" "Dagu web UI never came up on :$DAGU_PORT within the extended wait (container id $CONTAINER_ID) -- see the printed logs above."
    exit 1
  fi

  # socat sidecar republishes the host-network $DAGU_PORT through a bridge -p publish (browser-reachable), targeting the bridge gateway IP.
  BRIDGE_GATEWAY="$(docker network inspect bridge --format '{{(index .IPAM.Config 0).Gateway}}')"
  docker rm -f "$UI_PROXY_CONTAINER" >/dev/null 2>&1
  docker_pull_missing_retry alpine/socat
  docker run -d --name "$UI_PROXY_CONTAINER" \
    --label "$PROJECT_LABEL" \
    -p "$UI_PROXY_PORT:$UI_PROXY_PORT" \
    alpine/socat "TCP-LISTEN:$UI_PROXY_PORT,fork,reuseaddr" "TCP:$BRIDGE_GATEWAY:$DAGU_PORT" >/dev/null

  echo "Dagu web UI is up: http://localhost:$UI_PROXY_PORT"
else
  echo "=== Reusing the already-running ci-runner container ==="
fi
emit_agentic_success_block "start-ci-runner"

# ── Sync /app with the live working tree every run: wipe then extract (no stale files); tar fails loudly on an unreadable file ──
echo ""
echo "=== Syncing working tree into $CONTAINER ==="
source "$ROOT/scripts/utils/sync-source.sh"
if ! sync_source_into "$CONTAINER"; then
  echo "===== FAILED (working-tree sync into $CONTAINER) ====="
  emit_agentic_error_block "transient" "true" "sync-source" "Streaming the working tree into $CONTAINER failed -- see the error printed above for which stage (ls-files/tar/extract)."
  exit 1
fi
emit_agentic_success_block "sync-source"

# The DAG file lives outside /app, so the freshly synced copy is placed there after every sync.
DAG_DIR="$(docker exec "$CONTAINER" printenv DAGU_DAGS_DIR)"
docker exec "$CONTAINER" cp /app/scripts/ci/dagu/ci.yaml "$DAG_DIR/ci.yaml"

# Assign this run's id ourselves (dagu start -r) so the monitor watches exactly this run.
DAGU_RUN_ID="ci-$(date -u +%Y%m%dT%H%M%SZ)-$$-${RANDOM}${RANDOM}"
# The CI session ID is the Dagu run id, so every log of this run links back to the Dagu history.
export SESSION_ID="$DAGU_RUN_ID"

DAGU_PARAMS=(
  "unit=$(bool "$STAGE_UNIT")"
  "integration=$(bool "$STAGE_INTEGRATION")"
  "e2e=$(bool "$STAGE_E2E")"
  "api_flows=$(bool "$STAGE_API_FLOWS")"
  "sonar=$(bool "$STAGE_SONAR")"
  "archunit_metrics=$ARCHUNIT_METRICS"
  "lint=$LINT"
  "shellcheck=$SHELLCHECK"
  "docs=$(bool "$STAGE_DOCS")"
  "teardown_e2e_after=$TEARDOWN_AFTER"
  "reset_e2e_stack=$RESET_E2E_STACK"
  "reset_ci_cache=$RESET_CI_CACHE"
  "e2e_args=$PLAYWRIGHT_ARGS"
)

echo ""
echo "=== Triggering ci DAG run (${DAGU_PARAMS[*]}) ==="

# A bare DAG name resolves against $DAGU_HOME/dags, not the --dags directory, so the run starts from the file path.
DAG_FILE="$DAG_DIR/ci.yaml"

# Refuse a second run while one is alive: concurrent e2e stages share fixed ci-* container names; `dagu ps` is used because REST status keeps killed runs as running.
ACTIVE_RUN="$(docker exec "$CONTAINER" dagu ps -d ci 2>/dev/null | awk '$1 == "ci" { print $2; exit }')"
if [ -n "$ACTIVE_RUN" ]; then
  echo "===== FAILED (a ci DAG run is already in progress: $ACTIVE_RUN -- wait for it to finish or stop it at http://localhost:$UI_PROXY_PORT) ====="
  emit_agentic_error_block "business" "false" "ci-run" "A ci DAG run is already in progress ($ACTIVE_RUN); refusing to start a second -- concurrent e2e stages collide on the shared ci-* stack."
  exit 1
fi

echo "Dagu run id: $DAGU_RUN_ID"

if [ -n "$FOREGROUND" ]; then
  docker exec -d "$CONTAINER" dagu start -r "$DAGU_RUN_ID" "$DAG_FILE" -- "${DAGU_PARAMS[@]}"
  # The REST monitor prints an AGENTIC marker per Dagu step (for activity-monitor's tree); its exit code becomes this script's exit code.
  DAGU_UI_PORT="$UI_PROXY_PORT" DAGU_RUN_ID="$DAGU_RUN_ID" python3 -u scripts/ci/dagu-rest-run-monitor.py
  DAG_EXIT=$?
  SYNC_FAILED=""
  sync_artifacts || SYNC_FAILED=1
  echo ""
  if [ "$DAG_EXIT" -ne 0 ]; then
    # A real DAG stage failed -- the artifact sync outcome is secondary here.
    EXIT_CODE=1
    echo "===== FAILED${DAGU_RUN_ID:+ -- Dagu run $DAGU_RUN_ID} ====="
    emit_agentic_error_block "business" "false" "ci-run" "A ci DAG stage failed${DAGU_RUN_ID:+ (Dagu run $DAGU_RUN_ID)} -- see the per-step lines above; not retryable as-is."
  elif [ -n "$SYNC_FAILED" ]; then
    # Every DAG stage passed; only copying artifacts back to the host failed.
    EXIT_CODE=1
    echo "===== FAILED (artifact sync)${DAGU_RUN_ID:+ -- Dagu run $DAGU_RUN_ID} ====="
    emit_agentic_error_block "transient" "true" "ci-run" "Every ci DAG stage passed${DAGU_RUN_ID:+ (Dagu run $DAGU_RUN_ID)}, but copying architecture-model.json/architecture-map.html/adr-index.md${STAGE_SONAR:+/report.html} from $CONTAINER back to the host failed -- see the docker cp errors above; re-run 'bash scripts/ci/run.sh --sync-artifacts' to retry just that."
  else
    EXIT_CODE=0
    echo "===== PASSED${DAGU_RUN_ID:+ (Dagu run $DAGU_RUN_ID)} ====="
    emit_agentic_success_block "ci-run"
  fi
  echo "Full history: http://localhost:$UI_PROXY_PORT"
  exit $EXIT_CODE
else
  docker exec -d "$CONTAINER" dagu start -r "$DAGU_RUN_ID" "$DAG_FILE" -- "${DAGU_PARAMS[@]}"
  echo ""
  echo "DAG run triggered in the background (Dagu run $DAGU_RUN_ID)."
  echo "Watch live status/logs: http://localhost:$UI_PROXY_PORT"
  echo "Once it finishes, run 'bash scripts/ci/run.sh --sync-artifacts' to pull" \
       "architecture-metrics.json/pipeline-metrics.json onto the host."
  emit_agentic_success_block "trigger-background-run"
fi
