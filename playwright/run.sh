#!/bin/bash
# ── Header ──────────────────────────────────────────────────────────────────
# Description: Runs Playwright tests inside the reused `pw-runner` Docker container -- ensures
#   marketplace-app is running (starts it and waits if stopped), resets/reseeds the DB only when it
#   actually has data (stops the app around the reset, restarts it after), syncs spec/flow/helper
#   files and playwright.config.js into the container via `docker cp` (volume mounts don't work when
#   the caller is itself a container), runs the tests, then copies the HTML report back out.
#   Reuses `pw-runner` across calls -- the `playwright`/`@playwright/test` npm packages are only
#   installed once (skipped on later runs if already present), and the pinned npm package version
#   must match the container's own pre-baked Playwright browser version (both hardcoded here as
#   PLAYWRIGHT_VERSION -- bump both together, never independently).
# Usage: bash playwright/run.sh [scenario] [--ux] [--full] [--grep <pattern>]
#   (no scenario)         run every *.spec.js file
#   e2e                   run all e2e specs (skips spec 05's seed by default)
#   e2e --full            run e2e specs including spec 05's seed
#   <spec-name>           run a single spec file by name (with or without .spec.js)
#   --ux                  also take screenshots (embedded in the HTML report)
#   --grep <pattern>      forwarded to Playwright's own --grep, run only matching tests
# Uses: bash, docker, curl, npx playwright (inside pw-runner), scripts/deploy-and-run/reset.sh (DB
#   reset, only when the DB actually has data).
# Env: APP_URL (default http://localhost:8081), APP_CONTAINER (marketplace-app), PW_CONTAINER
#   (pw-runner), DB_PORT (5432), DB_USER (experiments_user), DB_NAME (experiments) -- all
#   overridable, used by scripts/ci/dagu/ci.yaml's e2e step for its isolated e2e stack.
# Input: playwright/e2e/*.spec.js, playwright/e2e/_flows/*.js, playwright/e2e/_helpers.js,
#   playwright/playwright.config.js.
# Outputs: HTML report at playwright/pw-report/index.html; full raw console log (same content
#   streamed live to stdout) at playwright/pw-report/run.log. Written inside pw-runner to two
#   separate volume paths -- /reports/playwright/ (HTML report) and /reports/playwright-log/
#   (run.log) -- kept apart because Playwright's own HTML reporter clears its outputFolder when it
#   finalizes the report after the run, which would silently delete run.log if it shared that same
#   folder (confirmed directly: run.log never survived when both used /reports/playwright/, even
#   though the HTML report itself always did). Both live in the shared test-reports named Docker
#   volume (never a WSL/Windows-drive path, so never subject to the docker-desktop-bind-mounts
#   issue documented in docs/ai/adr-index.md) -- then copied out via `docker cp`; the same volume
#   can also be inspected directly at any time, e.g. `docker exec pw-runner cat
#   /reports/playwright-log/run.log`. Restarts marketplace-app if the DB needed a reset.
# Returns: 0 if the Playwright run passes; non-zero if the app container isn't found/doesn't start,
#   the DB reset fails, or any test fails.
# ────────────────────────────────────────────────────────────────────────────

ROOT="$(cd "$(dirname "$0")/.." && pwd)"

# ── Parse args ───────────────────────────────────────────────────────────────
UX=""
FULL=""
SCENARIO=""
GREP=""
SKIP_NEXT=""
for arg in "$@"; do
  if [ -n "$SKIP_NEXT" ]; then GREP="$arg"; SKIP_NEXT=""; continue; fi
  if [ "$arg" = "--ux" ]; then UX=1;
  elif [ "$arg" = "--full" ]; then FULL=1;
  elif [ "$arg" = "--grep" ]; then SKIP_NEXT=1;
  else SCENARIO="$arg"; fi
done

# ── Ensure marketplace-app is running ──────────────────────────────────────
# All names/ports below are overridable via env vars (default shown), so a second, isolated
# stack (see scripts/deploy-and-run.sh's own override block) can run this same script concurrently with
# a normal dev stack — used by scripts/ci/dagu/ci.yaml's e2e step.
APP_URL="${APP_URL:-http://localhost:8081}"
APP_CONTAINER="${APP_CONTAINER:-marketplace-app}"
PW_CONTAINER="${PW_CONTAINER:-pw-runner}"
DB_PORT="${DB_PORT:-5432}"
DB_USER="${DB_USER:-experiments_user}"
DB_NAME="${DB_NAME:-experiments}"

# Playwright version must match image (see playwright/CLAUDE.md) -- one place to bump both
# instead of two independent literals in the same file.
PLAYWRIGHT_VERSION="1.61.1"
PLAYWRIGHT_IMAGE="mcr.microsoft.com/playwright:v${PLAYWRIGHT_VERSION}-jammy"

if ! docker inspect "$APP_CONTAINER" &>/dev/null; then
  echo "ERROR: Container '$APP_CONTAINER' not found. Build and start it:"
  echo "  docker build -f Dockerfile -t marketplace-app ."
  echo "  docker-compose -f scripts/deploy-and-run/docker-compose.db.yml -f scripts/deploy-and-run/docker-compose.minio.yml up -d"
  echo "  docker run -d --name marketplace-app --network host \\"
  echo "    -e SPRING_PROFILES_ACTIVE=prod -e DB_HOST=localhost -e DB_PORT=5432 \\"
  echo "    -e DB_NAME=experiments -e DB_USER=experiments_user \\"
  echo "    -e DB_PASSWORD=experiments_user_password \\"
  echo "    -e S3_ENDPOINT=http://localhost:9000 -e S3_BUCKET=advertisement \\"
  echo "    -e S3_ACCESS_KEY=admin -e S3_SECRET_KEY=admin12345 \\"
  echo "    -e S3_REGION=us-east-1 \\"
  echo "    -e S3_PUBLIC_URL=http://localhost:9000/advertisement marketplace-app"
  exit 1
fi

STATUS=$(docker inspect -f '{{.State.Status}}' "$APP_CONTAINER")
if [ "$STATUS" != "running" ]; then
  echo "Starting $APP_CONTAINER..."
  docker start "$APP_CONTAINER"
  echo "Waiting for startup (tailing logs)..."
  end=$((SECONDS + 120))
  while true; do
    if docker logs "$APP_CONTAINER" 2>&1 | grep -q "Started Application"; then
      echo "App ready."
      break
    fi
    if [ $SECONDS -ge $end ]; then
      echo "ERROR: startup timed out"
      docker logs --tail=40 "$APP_CONTAINER"
      exit 1
    fi
    sleep 2
  done
else
  curl -s --max-time 5 "$APP_URL" >/dev/null 2>&1 \
    || { echo "ERROR: $APP_CONTAINER running but not responding"; docker logs --tail=30 "$APP_CONTAINER"; exit 1; }
fi

# ── Reset / seed database ─────────────────────────────────────────────────────
# Tables are only truncated while the app is stopped — never live, and only
# when actually needed (skip entirely if already empty).
DB_CONTAINER=$(docker ps --filter "publish=$DB_PORT" --format "{{.Names}}" | head -1)
if [ -n "$DB_CONTAINER" ]; then
  ROW_COUNT=$(docker exec "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" \
    -tAc "SELECT count(*) FROM user_information" 2>/dev/null | tr -d '[:space:]')
  if [ "$ROW_COUNT" = "0" ]; then
    echo "Database already clean — skipping reset."
  else
    echo "Database has data — stopping app, resetting, restarting..."
    docker stop "$APP_CONTAINER" >/dev/null
    DB_NAME="$DB_NAME" DB_USER="$DB_USER" bash "$ROOT/scripts/deploy-and-run/reset.sh" --container "$DB_CONTAINER"
    RESTART_AT=$(date -u +%Y-%m-%dT%H:%M:%SZ)
    docker start "$APP_CONTAINER" >/dev/null
    echo "Waiting for application to restart..."
    end=$((SECONDS + 120))
    while true; do
      if docker logs "$APP_CONTAINER" --since "$RESTART_AT" 2>&1 | grep -q "Started Application"; then
        echo "App ready."
        break
      fi
      if [ $SECONDS -ge $end ]; then
        echo "ERROR: restart timed out"
        docker logs --tail=40 "$APP_CONTAINER"
        exit 1
      fi
      sleep 2
    done
  fi
else
  echo "WARNING: No postgres container found on port $DB_PORT — test accounts may not exist."
fi

# ── Reuse pw-runner if already running, otherwise start it ───────────────────
# test-reports is a shared named Docker volume (never a WSL/Windows-drive path, see
# docs/ai/adr-index.md) -- mounted here so it's present the moment pw-runner is (re)created. Since
# pw-runner is normally reused across calls (not recreated every run), an already-running
# container from before this mount was added won't pick it up until it's next recreated (e.g. via
# `docker rm -f pw-runner`).
if ! docker inspect "$PW_CONTAINER" &>/dev/null; then
  docker run -d --name "$PW_CONTAINER" --network host -v test-reports:/reports "$PLAYWRIGHT_IMAGE" sleep 86400
else
  STATUS=$(docker inspect -f '{{.State.Status}}' "$PW_CONTAINER" 2>/dev/null)
  if [ "$STATUS" != "running" ]; then
    docker rm -f "$PW_CONTAINER"
    docker run -d --name "$PW_CONTAINER" --network host -v test-reports:/reports "$PLAYWRIGHT_IMAGE" sleep 86400
  fi
fi

INSTALL_CMD="if [ ! -d /tmp/node_modules ]; then cd /tmp && PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1 npm install playwright@${PLAYWRIGHT_VERSION} @playwright/test@${PLAYWRIGHT_VERSION} -q 2>&1 | grep -v '^npm notice'; fi"

# ── Clean stale artifacts in container and volume ─────────────────────────────
docker exec "$PW_CONTAINER" bash -c "rm -rf /tmp/e2e /tmp/playwright.config.js /tmp/test-results /reports/playwright && rm -f /tmp/*.spec.js"

# ── Sync spec files ───────────────────────────────────────────────────────────
docker exec "$PW_CONTAINER" bash -c "mkdir -p /tmp/e2e"
for f in "$ROOT"/playwright/e2e/*.spec.js; do
  [ -f "$f" ] && docker cp "$f" "$PW_CONTAINER":/tmp/e2e/ 2>/dev/null
done
if [ -d "$ROOT/playwright/e2e/_flows" ]; then
  docker exec "$PW_CONTAINER" bash -c "mkdir -p /tmp/e2e/_flows"
  for f in "$ROOT"/playwright/e2e/_flows/*.js; do
    [ -f "$f" ] && docker cp "$f" "$PW_CONTAINER":/tmp/e2e/_flows/ 2>/dev/null
  done
fi
[ -f "$ROOT/playwright/e2e/_helpers.js" ] && docker cp "$ROOT/playwright/e2e/_helpers.js" "$PW_CONTAINER":/tmp/e2e/
docker cp "$ROOT/playwright/playwright.config.js" "$PW_CONTAINER":/tmp/

# ── Build run command ─────────────────────────────────────────────────────────
# APP_URL must be forwarded explicitly -- playwright.config.js reads it from the pw-runner
# container's own env, not this script's shell (docker exec starts a fresh environment).
PW_ENV="PLAYWRIGHT_BROWSERS_PATH=/ms-playwright APP_URL=$APP_URL"
[ -n "$UX" ]   && PW_ENV="$PW_ENV PW_SCREENSHOTS=1"
[ -n "$FULL" ] && PW_ENV="$PW_ENV PW_FULL=1"

# Full console output also persists to a log file, not just the terminal -- otherwise the only
# record of a real failure's cause is whatever a Windows/PowerShell terminal's own scrollback
# happens to still hold, easily lost on a long run. Written inside the container to
# /reports/playwright-log/run.log -- deliberately NOT /reports/playwright/ (the HTML reporter's own
# outputFolder) -- the HTML reporter clears its outputFolder when it finalizes the report after the
# run, which silently deleted run.log every time it lived in that same folder (confirmed directly).
# Both paths are under the shared test-reports named Docker volume, never a WSL/Windows-drive path,
# so never subject to the docker-desktop-bind-mounts issue documented in docs/ai/adr-index.md --
# then copied out to $RUN_LOG via `docker cp` after the run. `tee` still prints live to stdout
# unchanged; the inner `exit ${PIPESTATUS[0]}` (escaped so it evaluates inside the container, not
# this host shell) propagates the real playwright exit code through docker exec's own $?, since
# there's no host-side pipe left to lose it in.
# No mkdir -p here -- `docker cp` (below) creates the destination directory itself when it doesn't
# exist, so a raw bash `mkdir -p` against this WSL/Windows-drive host path was pure redundancy that
# cost every run a real filesystem write vulnerable to the docker-desktop-bind-mounts issue
# documented in docs/ai/adr-index.md (confirmed directly to fail with "Permission denied" on a
# real Docker Desktop/WSL2 machine). `docker cp` itself is daemon-mediated and not subject to it.
RUN_LOG="$ROOT/playwright/pw-report/run.log"

if [ -n "$SCENARIO" ]; then
  if [ "$SCENARIO" = "e2e" ]; then
    GREP_ARG=${GREP:+--grep "$GREP"}
    docker exec "$PW_CONTAINER" bash -c "$INSTALL_CMD && mkdir -p /reports/playwright /reports/playwright-log && cd /tmp && ($PW_ENV npx playwright test $SCENARIO/ $GREP_ARG --config playwright.config.js 2>&1 | tee /reports/playwright-log/run.log; exit \${PIPESTATUS[0]})"
  else
    if [[ "$SCENARIO" == */* ]]; then
      SPEC_FILE="$ROOT/playwright/${SCENARIO%.spec}.spec.js"
    else
      SPEC_FILE=$(find "$ROOT/playwright" -name "${SCENARIO%.spec}.spec.js" -not -path "*/node_modules/*" | head -1)
    fi
    if [ ! -f "$SPEC_FILE" ]; then
      echo "Error: spec not found: $SCENARIO"
      echo "Available:"
      find "$ROOT/playwright" -name "*.spec.js" -not -path "*/node_modules/*" | sort | sed "s|$ROOT/playwright/||"
      exit 1
    fi
    SPEC_REL="${SPEC_FILE#$ROOT/playwright/}"
    GREP_ARG=${GREP:+--grep "$GREP"}
    docker exec "$PW_CONTAINER" bash -c "$INSTALL_CMD && mkdir -p /reports/playwright /reports/playwright-log && cd /tmp && ($PW_ENV npx playwright test $SPEC_REL $GREP_ARG --config playwright.config.js 2>&1 | tee /reports/playwright-log/run.log; exit \${PIPESTATUS[0]})"
  fi
else
  GREP_ARG=${GREP:+--grep "$GREP"}
  docker exec "$PW_CONTAINER" bash -c "$INSTALL_CMD && mkdir -p /reports/playwright /reports/playwright-log && cd /tmp && ($PW_ENV npx playwright test $GREP_ARG --config playwright.config.js 2>&1 | tee /reports/playwright-log/run.log; exit \${PIPESTATUS[0]})"
fi

EXIT_CODE=$?

# ── Copy HTML report + run log back from the shared test-reports volume ──────
# Two separate docker cp calls -- the HTML report and run.log live in separate volume paths (see
# header) precisely so the HTML reporter's own outputFolder-clearing can't delete run.log; both
# still land together in the same host directory.
docker cp "$PW_CONTAINER":/reports/playwright/. "$ROOT/playwright/pw-report/" 2>/dev/null && \
  echo "HTML report: $ROOT/playwright/pw-report/index.html"
docker cp "$PW_CONTAINER":/reports/playwright-log/. "$ROOT/playwright/pw-report/" 2>/dev/null

exit $EXIT_CODE
