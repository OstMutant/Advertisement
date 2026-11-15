#!/usr/bin/env bash
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
# Usage: bash playwright/run.sh [scenario] [--ux] [--full] [--lint] [--grep <pattern>] [--workers <n>] [--repeat-each <n>]
#   (no scenario)         run every *.spec.js file
#   e2e                   run all e2e specs (skips the --full blocks by default)
#   e2e --full            run e2e specs including the --full blocks
#   <spec-name>           run a single spec file by name (with or without .spec.js)
#   --ux                  also take screenshots (embedded in the HTML report)
#   --lint                run ESLint over e2e/ and the architecture-map page script instead of running any tests -- skips the
#                         app and the database entirely; ignores scenario/--ux/--full/--grep (see Input, Outputs, Returns)
#   --shot <url>          save a full-page PNG of any web page (for example the Dagu UI at http://localhost:8082)
#                         instead of running any tests -- needs neither the app nor the database; the PNG lands in
#                         scripts/logs/playwright/shots/ (see Outputs). Ignores scenario/--ux/--full/--grep
#   --shot-name <name>    file name of that PNG without extension (default: shot; letters, digits, dot, dash, underscore; exit 2 otherwise, also for --shot without a URL)
#   --shot-click <text>   click the first button with this visible name before the PNG is taken, to capture a dialog
#   --grep <pattern>      forwarded to Playwright's own --grep, run only matching tests
#   --workers <n>         forwarded to Playwright's own --workers, overrides the config's worker count
#   --repeat-each <n>     forwarded to Playwright's own --repeat-each, runs every selected test n times
# Uses: bash, docker, curl, npx playwright (inside pw-runner), npx eslint (inside pw-runner, --lint
#   only), playwright/shot.js (inside pw-runner, --shot only), scripts/deploy-and-run/reset.sh (DB reset, only when the DB actually has data),
#   scripts/utils/agentic-output.sh (emit_agentic_success_block/emit_agentic_error_block), scripts/utils/project-label.sh,
#   scripts/utils/session-id.sh (write_session_info), scripts/utils/docker-pull-retry.sh (docker_pull_missing_retry).
# Env: APP_URL (default http://localhost:8081), APP_CONTAINER (marketplace-app), PW_CONTAINER
#   (pw-runner), DB_PORT (5432), DB_USER (experiments_user), DB_NAME (experiments) -- all
#   overridable, used by scripts/ci/dagu/ci.yaml's e2e step for its isolated e2e stack.
#   TEST_REPORTS_VOLUME (default test-reports) -- the Docker volume mounted at /reports of pw-runner.
#   ADMIN_EMAIL, ADMIN_PASSWORD (default: the repo-root .env values) -- forwarded into the
#   pw-runner container for the adminEn test user.
#   PW_WORKERS (default: the repo-root .env value) -- worker count forwarded into pw-runner for
#   playwright.config.js; the --workers flag overrides it for one run.
#   PW_LINT_MAX_WARNINGS (default: the repo-root .env value) -- --lint fails when ESLint reports more
#   warnings than this, so the count of raw locators can only go down.
# Input: playwright/e2e/*.js, playwright/e2e/_flows/*.js,
#   playwright/playwright.config.js. --lint instead reads the same spec/flow/helper files plus
#   playwright/eslint.config.js and package.json (ignores playwright.config.js), and for the page
#   lint docs/architecture/page/eslint.config.js, order.txt and the fragments order.txt names.
# Outputs: HTML report (a result) at playwright/pw-report/index.html. Full raw console log (same
#   content streamed live to stdout, a process log, not a result) at scripts/logs/playwright/run.log
#   -- kept in a separate host destination from pw-report/, same logs-vs-reports split
#   run-all-tests/build-and-test already use. Written inside pw-runner to two separate volume paths
#   -- /reports/playwright/ (HTML report) and /reports/playwright-log/ (run.log, plus this script's
#   own top-level progress messages in orchestrator.log alongside it) -- kept apart because
#   Playwright's own HTML reporter clears its outputFolder when it finalizes the report after the
#   run, which would silently delete run.log if it shared that same folder (confirmed directly:
#   run.log never survived when both used /reports/playwright/, even though the HTML report itself
#   always did). Both live in the shared test-reports named Docker volume (never a WSL/Windows-drive
#   path, so never subject to the docker-desktop-bind-mounts issue documented in
#   .claude/nav/adr-index.md) -- then copied out via `docker cp`; the same volume can also be inspected
#   directly at any time, e.g. `docker exec pw-runner cat /reports/playwright-log/orchestrator.log`.
#   Restarts marketplace-app if the DB needed a reset. --lint produces no HTML report/run.log --
#   ESLint output goes straight to stdout; it installs the package.json devDependencies and leaves
#   the scratch directories /lint (e2e lint) and /lint-page (page lint, the joined page script) in pw-runner. --shot produces no HTML report/run.log either -- only
#   scripts/logs/playwright/shots/<name>.png, a look-at-a-page image, not a test result.
# Returns: 0 if the Playwright run passes; non-zero if the app container isn't found/doesn't start,
#   the DB reset fails, or any test fails. Also prints a single-line AGENTIC_SUCCESS_BLOCK JSON
#   marker on a clean finish, or an AGENTIC_ERROR_BLOCK JSON marker
#   (errorCategory/isRetryable/currentStep/description/durationSeconds) on any failure path, for
#   an AI agent reading raw script output to parse machine-readable status instead of scraping
#   free text. --lint: 0 if the e2e lint stays within PW_LINT_MAX_WARNINGS and, only then, the page
#   lint (fixed zero-warning limit) is clean, non-zero otherwise -- same two JSON markers.
#   --shot: 0 if the PNG was written, non-zero otherwise -- same two JSON markers.
# ────────────────────────────────────────────────────────────────────────────

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SECONDS=0
source "$ROOT/scripts/utils/agentic-output.sh"
source "$ROOT/scripts/utils/project-label.sh"
source "$ROOT/scripts/utils/session-id.sh"
source "$ROOT/scripts/utils/docker-pull-retry.sh"

# ── Parse args ───────────────────────────────────────────────────────────────
UX=""
FULL=""
LINT=""
SHOT_URL=""
SHOT_NAME=""
SHOT_CLICK=""
SHOT_REQUESTED=""
SCENARIO=""
GREP=""
WORKERS=""
REPEAT_EACH=""
NEXT_VAR=""
for arg in "$@"; do
  if [ -n "$NEXT_VAR" ]; then printf -v "$NEXT_VAR" '%s' "$arg"; NEXT_VAR=""; continue; fi
  if [ "$arg" = "--ux" ]; then UX=1;
  elif [ "$arg" = "--full" ]; then FULL=1;
  elif [ "$arg" = "--lint" ]; then LINT=1;
  elif [ "$arg" = "--shot" ]; then SHOT_REQUESTED=true; NEXT_VAR=SHOT_URL;
  elif [ "$arg" = "--shot-name" ]; then NEXT_VAR=SHOT_NAME;
  elif [ "$arg" = "--shot-click" ]; then NEXT_VAR=SHOT_CLICK;
  elif [ "$arg" = "--grep" ]; then NEXT_VAR=GREP;
  elif [ "$arg" = "--workers" ]; then NEXT_VAR=WORKERS;
  elif [ "$arg" = "--repeat-each" ]; then NEXT_VAR=REPEAT_EACH;
  else SCENARIO="$arg"; fi
done

# ── Ensure marketplace-app is running (names and ports are env-overridable so an isolated second stack can run beside the dev stack) ──
APP_URL="${APP_URL:-http://localhost:8081}"
APP_CONTAINER="${APP_CONTAINER:-marketplace-app}"
PW_CONTAINER="${PW_CONTAINER:-pw-runner}"
DB_PORT="${DB_PORT:-5432}"
DB_USER="${DB_USER:-experiments_user}"
DB_NAME="${DB_NAME:-experiments}"

# One pinned version drives both the npm packages and the image tag, which must match (see .claude/rules/playwright.md).
PLAYWRIGHT_VERSION="1.65.0"
PLAYWRIGHT_IMAGE="mcr.microsoft.com/playwright:v${PLAYWRIGHT_VERSION}-jammy"

# ── Reuse pw-runner if running, else start it, before the app/DB checks so log_orchestrator can write from the first message ──
docker_pull_missing_retry "$PLAYWRIGHT_IMAGE"
if ! docker inspect "$PW_CONTAINER" &>/dev/null; then
  docker run -d --name "$PW_CONTAINER" --label "$PROJECT_LABEL" --network host -v "${TEST_REPORTS_VOLUME:-test-reports}:/reports" "$PLAYWRIGHT_IMAGE" sleep 86400
else
  PW_STATUS=$(docker inspect -f '{{.State.Status}}' "$PW_CONTAINER" 2>/dev/null)
  if [ "$PW_STATUS" != "running" ]; then
    docker rm -f "$PW_CONTAINER"
    docker run -d --name "$PW_CONTAINER" --label "$PROJECT_LABEL" --network host -v "${TEST_REPORTS_VOLUME:-test-reports}:/reports" "$PLAYWRIGHT_IMAGE" sleep 86400
  fi
fi
docker exec "$PW_CONTAINER" sh -c "mkdir -p /reports/playwright-log" >/dev/null 2>&1 || true

# Kills a Playwright process left running in pw-runner by an interrupted run.
docker exec "$PW_CONTAINER" pkill -f "node.*playwright" 2>/dev/null || true

#######################################
# Appends one progress message to orchestrator.log in the test-reports volume, so it outlives the terminal.
# The write is a synchronous docker exec per message, not a background pipe: messages are short and infrequent.
# Arguments: $1 the message.
# Returns: 0 always (a failed write is ignored).
#######################################
log_orchestrator() {
  echo "$1" | docker exec -i "$PW_CONTAINER" sh -c "cat >> /reports/playwright-log/orchestrator.log" 2>/dev/null || true
}

# Copies e2e/*.js and e2e/_flows/*.js into pw-runner's <dest>/e2e; the lint and test paths pass different dests to keep separate node_modules.
sync_e2e_files() {
  local dest="$1"
  docker exec "$PW_CONTAINER" bash -c "mkdir -p $dest/e2e"
  for f in "$ROOT"/playwright/e2e/*.js; do
    [ -f "$f" ] && docker cp "$f" "$PW_CONTAINER":$dest/e2e/ 2>/dev/null
  done
  if [ -d "$ROOT/playwright/e2e/_flows" ]; then
    docker exec "$PW_CONTAINER" bash -c "mkdir -p $dest/e2e/_flows"
    for f in "$ROOT"/playwright/e2e/_flows/*.js; do
      [ -f "$f" ] && docker cp "$f" "$PW_CONTAINER":$dest/e2e/_flows/ 2>/dev/null
    done
  fi
}

# ── Lint mode (no app/DB needed): own /lint directory, never /tmp, because a shared /tmp/node_modules let the lint install pass the test run's install check ──
if [ -n "$LINT" ]; then
  echo "Running ESLint..."
  docker exec "$PW_CONTAINER" bash -c "rm -rf /lint && mkdir -p /lint"
  sync_e2e_files /lint
  docker cp "$ROOT/playwright/eslint.config.js" "$PW_CONTAINER":/lint/
  docker cp "$ROOT/playwright/package.json" "$PW_CONTAINER":/lint/
  docker exec "$PW_CONTAINER" bash -c "cd /lint && npm install --no-save -q 2>&1 | grep -v '^npm notice'"
  PW_LINT_MAX_WARNINGS="${PW_LINT_MAX_WARNINGS:-$(grep -E '^PW_LINT_MAX_WARNINGS=' "$ROOT/.env" 2>/dev/null | cut -d= -f2-)}"
  docker exec "$PW_CONTAINER" bash -c "cd /lint && npx eslint . ${PW_LINT_MAX_WARNINGS:+--max-warnings $PW_LINT_MAX_WARNINGS}"
  EXIT_CODE=$?
  if [ "$EXIT_CODE" -eq 0 ]; then
    # Page script of the architecture map: fragments joined in page/order.txt order, linted with its own config and a zero warning limit
    docker exec "$PW_CONTAINER" bash -c "rm -rf /lint-page && mkdir -p /lint-page && ln -s /lint/node_modules /lint-page/node_modules"
    docker cp "$ROOT/docs/architecture/page/eslint.config.js" "$PW_CONTAINER":/lint-page/
    while IFS= read -r page_file; do cat "$ROOT/docs/architecture/page/$page_file.js"; done < "$ROOT/docs/architecture/page/order.txt" \
      | docker exec -i "$PW_CONTAINER" bash -c "cat > /lint-page/page.js"
    docker exec "$PW_CONTAINER" bash -c "cd /lint-page && npx eslint . --max-warnings 0"
    EXIT_CODE=$?
  fi
  if [ "$EXIT_CODE" -ne 0 ]; then
    emit_agentic_error_block "business" "false" "eslint-lint" "ESLint found lint errors (exit $EXIT_CODE) -- see output above."
  else
    emit_agentic_success_block "eslint-lint"
  fi
  exit $EXIT_CODE
fi

# ── Shot mode (no app/DB needed): own /shot directory with its own node_modules, same reason as /lint ──
if [ -n "$SHOT_REQUESTED" ] && [ -z "$SHOT_URL" ]; then
  echo "ERROR: --shot needs a URL" >&2
  emit_agentic_error_block "validation" "false" "arg-parsing" "--shot needs a URL -- pass it as the next argument."
  exit 2
fi

if [ -n "$SHOT_URL" ]; then
  SHOT_NAME="${SHOT_NAME:-shot}"
  case "$SHOT_NAME" in
    *[!A-Za-z0-9._-]*|.*) emit_agentic_error_block "validation" "false" "arg-parsing" "--shot-name may hold only letters, digits, dot, dash and underscore, and may not start with a dot."; exit 2 ;;
  esac
  SHOT_DIR="$ROOT/scripts/logs/playwright/shots"
  echo "Saving a screenshot of $SHOT_URL..."
  docker exec "$PW_CONTAINER" bash -c "mkdir -p /shot/out && rm -f /shot/out/$SHOT_NAME.png"
  docker cp "$ROOT/playwright/shot.js" "$PW_CONTAINER":/shot/ || { emit_agentic_error_block "transient" "true" "playwright-shot" "Could not copy shot.js into $PW_CONTAINER."; exit 1; }
  docker exec "$PW_CONTAINER" bash -c "cd /shot && { [ -d node_modules/playwright ] || npm install --no-save -q playwright@$PLAYWRIGHT_VERSION; }" || { emit_agentic_error_block "transient" "true" "playwright-shot" "npm install of playwright failed inside $PW_CONTAINER -- see output above."; exit 1; }
  docker exec -w /shot "$PW_CONTAINER" node shot.js "$SHOT_URL" "/shot/out/$SHOT_NAME.png" ${SHOT_CLICK:+"$SHOT_CLICK"}
  EXIT_CODE=$?
  if [ "$EXIT_CODE" -eq 0 ]; then
    mkdir -p "$SHOT_DIR"
    docker cp "$PW_CONTAINER":"/shot/out/$SHOT_NAME.png" "$SHOT_DIR/$SHOT_NAME.png" || EXIT_CODE=$?
  fi
  if [ "$EXIT_CODE" -ne 0 ]; then
    emit_agentic_error_block "business" "false" "playwright-shot" "Could not save a screenshot of $SHOT_URL (exit $EXIT_CODE) -- see output above."
  else
    echo "Screenshot: $SHOT_DIR/$SHOT_NAME.png"
    emit_agentic_success_block "playwright-shot"
  fi
  exit $EXIT_CODE
fi

if ! docker inspect "$APP_CONTAINER" &>/dev/null; then
  echo "ERROR: Container '$APP_CONTAINER' not found. Build and start it:"
  echo "  docker build -f Dockerfile -t marketplace-app ."
  echo "  docker-compose -f scripts/deploy-and-run/docker-compose.db.yml -f scripts/deploy-and-run/docker-compose.seaweedfs.yml up -d"
  echo "  docker run -d --name marketplace-app --label $PROJECT_LABEL --network host \\"
  echo "    -e SPRING_PROFILES_ACTIVE=prod -e DB_HOST=localhost -e DB_PORT=5432 \\"
  echo "    -e DB_NAME=experiments -e DB_USER=experiments_user \\"
  echo "    -e DB_PASSWORD=experiments_user_password \\"
  echo "    -e S3_ENDPOINT=http://localhost:9000 -e S3_BUCKET=advertisement \\"
  echo "    -e S3_ACCESS_KEY=admin -e S3_SECRET_KEY=admin12345 \\"
  echo "    -e S3_REGION=us-east-1 \\"
  echo "    -e S3_PUBLIC_URL=http://localhost:9000/advertisement marketplace-app"
  emit_agentic_error_block "validation" "false" "check-app-container" "Container '$APP_CONTAINER' not found -- build and start it first, per the commands printed above."
  exit 1
fi

STATUS=$(docker inspect -f '{{.State.Status}}' "$APP_CONTAINER")
if [ "$STATUS" != "running" ]; then
  echo "Starting $APP_CONTAINER..."
  log_orchestrator "Starting $APP_CONTAINER..."
  docker start "$APP_CONTAINER"
  echo "Waiting for startup (tailing logs)..."
  end=$((SECONDS + 120))
  while true; do
    if docker logs "$APP_CONTAINER" 2>&1 | grep -q "Started Application"; then
      echo "App ready."
      log_orchestrator "App ready."
      break
    fi
    if [ $SECONDS -ge $end ]; then
      echo "ERROR: startup timed out"
      log_orchestrator "ERROR: startup timed out"
      docker logs --tail=40 "$APP_CONTAINER"
      emit_agentic_error_block "transient" "true" "start-app" "$APP_CONTAINER did not report 'Started Application' within the startup timeout."
      exit 1
    fi
    sleep 2
  done
else
  curl -s --max-time 5 "$APP_URL" >/dev/null 2>&1 \
    || { echo "ERROR: $APP_CONTAINER running but not responding"; log_orchestrator "ERROR: $APP_CONTAINER running but not responding"; docker logs --tail=30 "$APP_CONTAINER"; emit_agentic_error_block "transient" "true" "check-app-container" "$APP_CONTAINER is running but not responding on $APP_URL."; exit 1; }
fi

# ── Reset / seed database: tables are truncated only while the app is stopped and only when the DB has data ──
DB_CONTAINER=$(docker ps --filter "publish=$DB_PORT" --format "{{.Names}}" | head -1)
if [ -n "$DB_CONTAINER" ]; then
  ROW_COUNT=$(docker exec "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" \
    -tAc "SELECT count(*) FROM user_information" 2>/dev/null | tr -d '[:space:]')
  if [ "$ROW_COUNT" = "0" ]; then
    echo "Database already clean — skipping reset."
    log_orchestrator "Database already clean — skipping reset."
  else
    echo "Database has data — stopping app, resetting, restarting..."
    log_orchestrator "Database has data — stopping app, resetting, restarting..."
    docker stop "$APP_CONTAINER" >/dev/null
    DB_NAME="$DB_NAME" DB_USER="$DB_USER" bash "$ROOT/scripts/deploy-and-run/reset.sh" --container "$DB_CONTAINER"
    RESTART_AT=$(date -u +%Y-%m-%dT%H:%M:%SZ)
    docker start "$APP_CONTAINER" >/dev/null
    echo "Waiting for application to restart..."
    end=$((SECONDS + 120))
    while true; do
      if docker logs "$APP_CONTAINER" --since "$RESTART_AT" 2>&1 | grep -q "Started Application"; then
        echo "App ready."
        log_orchestrator "App ready."
        break
      fi
      if [ $SECONDS -ge $end ]; then
        echo "ERROR: restart timed out"
        log_orchestrator "ERROR: restart timed out"
        docker logs --tail=40 "$APP_CONTAINER"
        emit_agentic_error_block "transient" "true" "restart-app" "$APP_CONTAINER did not report 'Started Application' within the restart timeout after the DB reset."
        exit 1
      fi
      sleep 2
    done
  fi
else
  echo "WARNING: No postgres container found on port $DB_PORT — test accounts may not exist."
  log_orchestrator "WARNING: No postgres container found on port $DB_PORT — test accounts may not exist."
fi

INSTALL_CMD="if [ ! -d /tmp/node_modules ]; then cd /tmp && PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1 npm install playwright@${PLAYWRIGHT_VERSION} @playwright/test@${PLAYWRIGHT_VERSION} -q 2>&1 | grep -v '^npm notice'; fi"

# ── Clean stale artifacts in container and volume ─────────────────────────────
docker exec "$PW_CONTAINER" bash -c "rm -rf /tmp/e2e /tmp/playwright.config.js /tmp/test-results /reports/playwright && rm -f /tmp/*.spec.js"

# ── Sync spec files ───────────────────────────────────────────────────────────
sync_e2e_files /tmp
docker cp "$ROOT/playwright/playwright.config.js" "$PW_CONTAINER":/tmp/

# ── Build run command (APP_URL is forwarded explicitly because playwright.config.js reads the container's env, not this shell's) ──
ADMIN_EMAIL="${ADMIN_EMAIL:-$(grep -E '^ADMIN_EMAIL=' "$ROOT/.env" 2>/dev/null | cut -d= -f2-)}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-$(grep -E '^ADMIN_PASSWORD=' "$ROOT/.env" 2>/dev/null | cut -d= -f2-)}"
PW_WORKERS="${PW_WORKERS:-$(grep -E '^PW_WORKERS=' "$ROOT/.env" 2>/dev/null | cut -d= -f2-)}"
PW_ENV="PLAYWRIGHT_BROWSERS_PATH=/ms-playwright APP_URL=$(printf '%q' "$APP_URL") ADMIN_EMAIL=$(printf '%q' "$ADMIN_EMAIL") ADMIN_PASSWORD=$(printf '%q' "$ADMIN_PASSWORD") PW_WORKERS=$(printf '%q' "$PW_WORKERS")"
[ -n "$UX" ]   && PW_ENV="$PW_ENV PW_SCREENSHOTS=1"
[ -n "$FULL" ] && PW_ENV="$PW_ENV PW_FULL=1"

# run.log is written to /reports/playwright-log, not /reports/playwright, whose folder the HTML reporter clears on finalize.
RUN_LOG="$ROOT/scripts/logs/playwright/run.log"

# In the docker exec strings below, \${PIPESTATUS[0]} is escaped to evaluate in the container and keep playwright's exit code through tee.
EXTRA_ARGS="${WORKERS:+--workers $WORKERS} ${REPEAT_EACH:+--repeat-each $REPEAT_EACH}"
# printf %q keeps a pattern with spaces or shell characters one argument inside the bash -c string below.
GREP_ARG=""
[ -n "$GREP" ] && GREP_ARG="--grep $(printf '%q' "$GREP")"
if [ -n "$SCENARIO" ]; then
  if [ "$SCENARIO" = "e2e" ]; then
    docker exec "$PW_CONTAINER" bash -c "$INSTALL_CMD && mkdir -p /reports/playwright /reports/playwright-log && cd /tmp && ($PW_ENV npx playwright test $SCENARIO/ $GREP_ARG $EXTRA_ARGS --config playwright.config.js 2>&1 | tee /reports/playwright-log/run.log; exit \${PIPESTATUS[0]})"
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
      emit_agentic_error_block "validation" "false" "arg-parsing" "Spec not found: $SCENARIO -- fix the scenario name and retry."
      exit 1
    fi
    SPEC_REL="${SPEC_FILE#$ROOT/playwright/}"
    docker exec "$PW_CONTAINER" bash -c "$INSTALL_CMD && mkdir -p /reports/playwright /reports/playwright-log && cd /tmp && ($PW_ENV npx playwright test $SPEC_REL $GREP_ARG $EXTRA_ARGS --config playwright.config.js 2>&1 | tee /reports/playwright-log/run.log; exit \${PIPESTATUS[0]})"
  fi
else
  docker exec "$PW_CONTAINER" bash -c "$INSTALL_CMD && mkdir -p /reports/playwright /reports/playwright-log && cd /tmp && ($PW_ENV npx playwright test $GREP_ARG $EXTRA_ARGS --config playwright.config.js 2>&1 | tee /reports/playwright-log/run.log; exit \${PIPESTATUS[0]})"
fi

EXIT_CODE=$?

# ── Copy HTML report and run log back from the test-reports volume to playwright/pw-report/ and scripts/logs/playwright/ ──
docker cp "$PW_CONTAINER":/reports/playwright/. "$ROOT/playwright/pw-report/" 2>/dev/null && \
  echo "HTML report: $ROOT/playwright/pw-report/index.html"
docker cp "$PW_CONTAINER":/reports/playwright-log/. "$ROOT/scripts/logs/playwright/" 2>/dev/null
write_session_info "$ROOT/scripts/logs/playwright" playwright

if [ "$EXIT_CODE" -ne 0 ]; then
  emit_agentic_error_block "business" "false" "playwright-run" "Playwright run failed (exit $EXIT_CODE) -- one or more tests did not pass, not retryable as-is."
else
  emit_agentic_success_block "playwright-run"
fi
exit $EXIT_CODE
