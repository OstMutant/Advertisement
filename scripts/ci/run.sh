#!/bin/bash
# ── Header ──────────────────────────────────────────────────────────────────
# Description: Thin trigger over a persistent Dagu server. Rebuilds the ci-runner image only when
#   scripts/ci/Dockerfile or scripts/ci/docker-entrypoint.sh changed since the image was built,
#   the image is missing, or --rebuild is passed; otherwise reuses the running ci-runner container
#   (Docker-outside-of-Docker: docker.sock mounted, so it can create/tear down its own isolated ci-* sibling
#   containers) plus its ci-runner-dagu-proxy sidecar. Every run, before firing the DAG, it
#   streams the current working tree into the container's /app via tar -- the image's own
#   `COPY . .` is not trusted as the source of truth (Docker layer-cache staleness). Then fires a
#   DAG run (scripts/ci/dagu/ci.yaml) with the requested params. Dagu replaces the orchestration/
#   UI layer only -- every DAG step calls the exact same scripts this project's other tools
#   already use (build-and-test.sh, deploy-and-run.sh, playwright/run.sh, sonar.sh).
# Usage: bash scripts/ci/run.sh [flags]
#   (no flags)               -- most extensive run: unit + integration + e2e + sonar +
#                                archunit_metrics + lint + docs
#   --unit                   -- run the unit stage
#   --integration            -- run the integration stage (always applies the Testcontainers
#                                sandbox workaround internally -- see DECISIONS.md)
#   --e2e                    -- run the e2e stage
#   --sonar                  -- run the sonar stage
#   --all                    -- unit + integration + e2e (no sonar)
#   --docs-only              -- skip unit/integration/e2e/sonar/archunit_metrics/lint entirely
#                                (build then straight to pipeline_metrics/docs) -- the fastest
#                                real path for testing the docs stage/sync_artifacts alone,
#                                minutes not dozens of minutes
#   --no-docs                -- skip the docs stage (regenerates architecture-model.json/
#                                architecture-map.html inside the container -- see ci.yaml)
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
#   --rebuild                  -- force an image rebuild + container recreation even when the
#                                  Dockerfile/entrypoint are unchanged (normally the image is
#                                  rebuilt only when they change; the working tree is streamed in
#                                  fresh every run regardless)
#   --refresh-tools              -- force re-download of buildx/compose/dagu into ci-tools-cache
#                                    even if already cached (e.g. after bumping DAGU_VERSION)
#   --no-archunit-metrics           -- skip ArchUnit's module-coupling export (on by default --
#                                      cheap compared to e2e, feeds
#                                      generate-architecture-model.sh --with-archunit)
#   --no-lint                        -- skip the ESLint stage (on by default -- delegates to
#                                        playwright/run.sh --lint, cheap, no app/DB needed)
#   --sync-artifacts                 -- pull whatever architecture-metrics.json/
#                                        pipeline-metrics.json/architecture-model.json/
#                                        architecture-map.html/Playwright report/unit+integration
#                                        Surefire+logs/Sonar report+log the run has produced onto
#                                        the host, without triggering a new run (automatic after
#                                        --foreground; needed manually after a background run, or
#                                        after triggering a run directly from Dagu's own web UI)
# Uses: bash, docker, curl, scripts/utils/agentic-output.sh
#   (emit_agentic_success_block/emit_agentic_error_block), python3 (--foreground only --
#   scripts/ci/dagu-rest-run-monitor.py polls the run and emits its own real per-step markers).
# Env: None read directly -- every flag above is translated into either a container-start env var
#   (FORCE_TOOLS_REFRESH, passed via `docker run -e`) or a Dagu param (passed via
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
#   scripts/build-and-test/reports/advertisement-build-only-{unit,integration,sonar}/,
#   scripts/logs/build-and-test/advertisement-build-only-{unit,integration,sonar}/,
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
REBUILD=""
REFRESH_TOOLS="false"
ARCHUNIT_METRICS="true"
LINT="true"
SYNC_ARTIFACTS_ONLY=""
PLAYWRIGHT_ARGS="e2e --full --ux"
ANY_STAGE_FLAG=""

# `docker cp`'s "local" side always resolves in the caller's own filesystem -- run from here (the
# real host), not from inside a DAG step (confirmed directly: a step-side `docker cp` landed the
# file inside ci-runner's own filesystem, not the host's, even with docker.sock mounted).
# architecture-metrics.json/pipeline-metrics.json are copied best-effort (errors suppressed) since
# a failed/partial run still surfaces whatever archunit_metrics/pipeline_metrics managed to produce
# before failing. architecture-model.json/architecture-map.html are different: the docs DAG step
# now regenerates them in place (see ci.yaml), so a copy failure here means the host's committed
# copies were NOT actually refreshed -- returns non-zero in that case so callers can tell.
# Whatever `docker cp` actually says on stderr is relayed into $CONTAINER itself (never just
# $ROOT, the caller's own host filesystem) -- $CONTAINER is the one piece of shared, inspectable
# state whoever triggered this run and whoever is debugging it both have access to, via
# `docker exec $CONTAINER cat /tmp/ci-sync-diag.log`, independent of whose shell/host actually ran
# run.sh (confirmed a real gap: a previous version only wrote to $ROOT, invisible cross-host).
# `docker cp` writing straight to a WSL2 Windows-drive mount (/mnt/c, /mnt/d, ...) has been seen to
# fail with "unlinkat ...: permission denied" on a real WSL2/Docker Desktop host, and a plain shell
# `cp -f`/`mv` onto the same path hits the identical wall ("cp: cannot create regular file ...:
# Permission denied", even for a brand-new file in that directory) -- this is DrvFs enforcing the
# real Windows ACL underneath, which a WSL shell process cannot route around no matter which Linux
# tool it uses. That failure is specifically an in-place-overwrite problem (the ACL blocks
# replacing a file that already exists), so the fallback below routes the final write through a
# throwaway container's own bind-mounted view of the destination directory and uses `mv` (a rename,
# not an overwrite) to land it.
#
# Try the plain, single-step `docker cp` straight to the final destination first -- it succeeds on
# some hosts (confirmed: this sandbox's own Docker Desktop VM has no such ACL restriction) and
# needs none of the machinery below. Only fall back to the temp-file + bind-mount-container dance
# when the direct copy actually fails, so a host where it works never pays for the elaborate path.
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
      # Bind-mounting a single file whose target path doesn't already exist inside the image can
      # get Docker to create a directory there instead (a real, confirmed gotcha) -- mount the
      # temp file's own directory instead and reference it by name.
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

  # Test-result artifacts (Playwright report, unit/integration Surefire+logs, Sonar's run log) are
  # written directly into the shared `test-reports` named volume by the build/pw-runner/scanner
  # containers -- pulled out here via a throwaway container mounting that volume (named-volume `-v`
  # mounts are reliable in this sandbox, unlike host-path bind mounts -- see DECISIONS.md), the
  # same established pattern scripts/run-all-tests/run.sh already uses. This does not depend on
  # $CONTAINER (ci-runner) still holding its own copy of anything -- best-effort, errors suppressed,
  # since a partial run still leaves whatever earlier steps produced worth pulling.
  local VOL_READER="ci-artifact-reader"
  docker rm -f "$VOL_READER" >/dev/null 2>&1
  docker run -d --name "$VOL_READER" -v test-reports:/reports alpine sleep 60 >/dev/null 2>&1
  for name in advertisement-build-only-unit advertisement-build-only-integration advertisement-build-only-sonar; do
    docker cp "$VOL_READER:/reports/$name/." "$ROOT/scripts/build-and-test/reports/$name/" 2>/dev/null
    docker cp "$VOL_READER:/reports/logs/$name/." "$ROOT/scripts/logs/build-and-test/$name/" 2>/dev/null
  done
  docker cp "$VOL_READER:/reports/advertisement-build-only-integration/it-mirror/." \
    "$ROOT/integration-tests/reports/" 2>/dev/null
  docker cp "$VOL_READER:/reports/playwright/." "$ROOT/playwright/pw-report/" 2>/dev/null
  docker cp "$VOL_READER:/reports/playwright-log/." "$ROOT/scripts/logs/playwright/" 2>/dev/null
  docker cp "$VOL_READER:/reports/sonar/." "$ROOT/scripts/logs/sonar/" 2>/dev/null
  docker rm -f "$VOL_READER" >/dev/null 2>&1

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
    --sonar)              STAGE_SONAR=1; ANY_STAGE_FLAG=1 ;;
    --docs-only)           ANY_STAGE_FLAG=1; ARCHUNIT_METRICS="false"; LINT="false" ;;
    --no-docs)             STAGE_DOCS="" ;;
    --all)                 STAGE_UNIT=1; STAGE_INTEGRATION=1; STAGE_E2E=1; ANY_STAGE_FLAG=1 ;;
    --no-keep-e2e-infra)   KEEP_INFRA="false" ;;
    --reset-e2e-db)        RESET_E2E_DB="true" ;;
    --foreground)          FOREGROUND=1 ;;
    --rebuild)             REBUILD=1 ;;
    --refresh-tools)       REFRESH_TOOLS="true" ;;
    --no-archunit-metrics) ARCHUNIT_METRICS="false" ;;
    --no-lint)             LINT="false" ;;
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

# No explicit stage flag at all -> default to the most extensive run (mirrors
# `playwright.sh e2e --full --ux` being the thorough option there).
if [ -z "$ANY_STAGE_FLAG" ]; then
  STAGE_UNIT=1
  STAGE_INTEGRATION=1
  STAGE_E2E=1
  STAGE_SONAR=1
fi

bool() { [ -n "$1" ] && echo true || echo false; }

# ── ci-runner lifecycle: rebuild the image only on real need, reuse the running container ──────
# The ci-runner image bakes the repo in via `COPY . .`, and Docker's layer cache can silently
# serve a stale copy of that layer (a generated file caught mid-rewrite has shipped as 0 bytes
# this way -- see improvement-183). So the image is not trusted as the working-tree source: build
# it only when scripts/ci/Dockerfile or scripts/ci/docker-entrypoint.sh changed since the image
# was built (or the image is missing, or --rebuild is passed), keep the running container across
# runs, and overlay /app with the live working tree via tar before every run (further below).
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
  # Captured by ID, not just started under $CONTAINER's name -- every liveness/CPU check below
  # queries this specific ID, not the name, because the name is a mutable pointer: if this
  # container is killed and anything else creates a new one reusing the same name, a name-based
  # `docker inspect`/`docker stats` silently starts reporting on that *other* container and would
  # never notice this one died. See DECISIONS.md.
  CONTAINER_ID="$(docker run -d --name "$CONTAINER" \
    --network host \
    -e FORCE_TOOLS_REFRESH="$REFRESH_TOOLS" \
    -v /var/run/docker.sock:/var/run/docker.sock \
    -v ci-m2-cache:/root/.m2 \
    -v ci-dagu-home:/root/.dagu \
    -v ci-tools-cache:/root/.ci-tools \
    "$IMAGE")"

  echo "Waiting for Dagu's web UI to come up (first start downloads buildx/compose/dagu into" \
       "ci-tools-cache if not already cached there -- real duration depends on network speed, not" \
       "a fixed guess)..."

  # Readiness is checked via `docker exec ... curl` (through the docker socket), never a direct
  # `curl localhost:$DAGU_PORT` from this process. ci-runner runs --network host, so a direct curl
  # only works when this script itself runs directly on the host, sharing that network namespace --
  # confirmed directly to fail otherwise: Dagu was fully healthy and answering 200 from inside the
  # container while a direct outer curl from the caller's own process never reached it at all,
  # because that caller doesn't share ci-runner's network namespace (e.g. it's itself sandboxed
  # without host networking). `docker exec` always works regardless of the caller's own network
  # namespace, since it goes through the docker socket, not the network stack -- one mechanism that
  # works whether this script runs on a bare host or inside another, non-host-networked container.
  dagu_ready() {
    docker exec "$CONTAINER_ID" curl -sf "http://localhost:$DAGU_PORT/" >/dev/null 2>&1
  }

  # Typical-case wait: poll for up to TYPICAL_WAIT_SECONDS, same shape as before -- covers the
  # common case where startup finishes within the usual window.
  TYPICAL_WAIT_SECONDS=120
  UP=""
  for _ in $(seq 1 "$TYPICAL_WAIT_SECONDS"); do
    if dagu_ready; then
      UP=1
      break
    fi
    sleep 1
  done

  # Past the typical wait and still not up: rather than cutting off at a bigger, still-arbitrary
  # number, keep going as long as the container is genuinely still alive and actively doing real
  # work (real CPU activity -- the tool download/unpack this step depends on), re-checking both
  # liveness and readiness every EXTENDED_CHECK_INTERVAL_SECONDS. Only declares failure once the
  # container has actually died or gone idle without ever coming up -- a real signal, not a second
  # guessed duration.
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
else
  echo "=== Reusing the already-running ci-runner container ==="
fi
emit_agentic_success_block "start-ci-runner"

# ── Replace /app inside the running container with the live working tree, every run ────────────
# The image's own `COPY . .` is never trusted (stale-layer risk -- see the lifecycle note above),
# so the working tree is streamed in fresh before every DAG run. The file set comes from
# `git ls-files` (tracked + untracked-not-.gitignored), not a tar tree-walk -- git already
# excludes `.git`, every `*/target`, `node_modules`, report/log dirs, sockets/FIFOs, etc. `*.md`
# is kept (unlike `.dockerignore`), since the docs stage needs DECISIONS.md / flows.md /
# adr-index.md. `/app` is wiped first, then re-extracted -- a plain `tar -x` overlay leaves behind
# files that were deleted from the working tree since the last sync (a stale copy of a
# since-removed .java then breaks the compile). Everything under /app is regenerated by the build
# (target/, node_modules, ...); the durable caches live in separate volumes (/root/.m2,
# /root/.ci-tools, /root/.dagu), untouched by this.
#
# `tar` runs WITHOUT `--ignore-failed-read` on purpose: an unreadable listed file (e.g. one a
# generator left mode 0600, so the sync user can't read it) must fail the whole sync loudly, not
# be silently dropped -- a missing file only surfaces later as a confusing downstream stage
# failure (a "stale adr-index.md" in the docs stage, say). Any non-zero tar exit is fatal here.
echo ""
echo "=== Syncing working tree into $CONTAINER ==="
source "$ROOT/scripts/utils/sync-source.sh"
if ! sync_source_into "$CONTAINER"; then
  echo "===== FAILED (working-tree sync into $CONTAINER) ====="
  emit_agentic_error_block "transient" "true" "sync-source" "Streaming the working tree into $CONTAINER failed -- see the error printed above for which stage (ls-files/tar/extract)."
  exit 1
fi
emit_agentic_success_block "sync-source"

DAGU_PARAMS=(
  "unit=$(bool "$STAGE_UNIT")"
  "integration=$(bool "$STAGE_INTEGRATION")"
  "e2e=$(bool "$STAGE_E2E")"
  "sonar=$(bool "$STAGE_SONAR")"
  "archunit_metrics=$ARCHUNIT_METRICS"
  "lint=$LINT"
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

# Refuse to start a second run while one is genuinely alive. The e2e stage's ci-* stack
# (ci-advertisement-db / ci-marketplace-app / ...) has fixed, non-per-run container names, so a
# concurrent run's e2e stage redeploys/resets it out from under the first run's Playwright tests
# -- the app vanishes mid-test and unrelated specs fail (see scripts/ci/README.md). Dagu's own
# maxActiveRuns does not gate a manual `dagu start`, so gate it here. Use `dagu ps` (the live
# process store), not the REST status: a run killed with its container is left as "running" in the
# persisted history until Dagu reconciles it, and `dagu ps` never shows that zombie.
ACTIVE_RUN="$(docker exec "$CONTAINER" dagu ps -d ci 2>/dev/null | awk '$1 == "ci" { print $2; exit }')"
if [ -n "$ACTIVE_RUN" ]; then
  echo "===== FAILED (a ci DAG run is already in progress: $ACTIVE_RUN -- wait for it to finish or stop it at http://localhost:$UI_PROXY_PORT) ====="
  emit_agentic_error_block "business" "false" "ci-run" "A ci DAG run is already in progress ($ACTIVE_RUN); refusing to start a second -- concurrent e2e stages collide on the shared ci-* stack."
  exit 1
fi

# Assign this run's id ourselves (`dagu start -r <id>`) so the monitor watches exactly this run,
# never a guess from the API. Timestamp + pid + a random tail -- unique by construction.
DAGU_RUN_ID="ci-$(date -u +%Y%m%dT%H%M%SZ)-$$-${RANDOM}${RANDOM}"
echo "Dagu run id: $DAGU_RUN_ID"

if [ -n "$FOREGROUND" ]; then
  docker exec -d "$CONTAINER" dagu start -r "$DAGU_RUN_ID" "$DAG_FILE" -- "${DAGU_PARAMS[@]}"
  # scripts/ci/dagu-rest-run-monitor.py polls Dagu's own REST API and prints a real
  # AGENTIC_SUCCESS_BLOCK/AGENTIC_ERROR_BLOCK marker per step -- this is what lets
  # scripts/activity-monitor.sh render a real per-step tree.txt for `ci.sh --foreground`, the same
  # way it already does for every other wrapped script (see .claude/rules/scripts.md's "Local CI
  # Runner" section). Its own exit code becomes this script's real exit code. DAGU_RUN_ID tells it
  # exactly which run to watch -- no guessing.
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
    # Every DAG stage passed; only pulling architecture-model.json/architecture-map.html/
    # adr-index.md, or (when the sonar stage actually ran) report.html, back to the host failed.
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
