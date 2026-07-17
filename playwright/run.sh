#!/bin/bash
# Usage:
#   ./playwright/run.sh                        — run all e2e spec files
#   ./playwright/run.sh e2e                    — run all e2e specs (clean DB, skips spec 05 seed)
#   ./playwright/run.sh e2e --full             — run e2e specs including spec 05 seed
#   ./playwright/run.sh 01-marketplace-empty-flow  — run a single spec file by name
#   ./playwright/run.sh --ux                   — all tests with screenshots
#   ./playwright/run.sh e2e --grep "my test"   — run only tests matching name

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
# stack (see scripts/deploy.sh's own override block) can run this same script concurrently with
# a normal dev stack — used by scripts/ci/entrypoint.sh (improvement-059).
APP_URL="${APP_URL:-http://localhost:8081}"
APP_CONTAINER="${APP_CONTAINER:-marketplace-app}"
PW_CONTAINER="${PW_CONTAINER:-pw-runner}"
DB_PORT="${DB_PORT:-5432}"
DB_USER="${DB_USER:-experiments_user}"
DB_NAME="${DB_NAME:-experiments}"

# Playwright version must match image (see playwright/CLAUDE.md) -- one place to bump both
# instead of two independent literals in the same file (improvement-044).
PLAYWRIGHT_VERSION="1.52.0"
PLAYWRIGHT_IMAGE="mcr.microsoft.com/playwright:v${PLAYWRIGHT_VERSION}-jammy"

if ! docker inspect "$APP_CONTAINER" &>/dev/null; then
  echo "ERROR: Container '$APP_CONTAINER' not found. Build and start it:"
  echo "  docker build -f Dockerfile -t marketplace-app ."
  echo "  docker-compose -f scripts/infra/docker-compose.db.yml -f scripts/infra/docker-compose.minio.yml up -d"
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
    docker cp /app/scripts/database/reset-clean.sql "$DB_CONTAINER":/tmp/pw-reset.sql 2>/dev/null
    docker exec "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" \
      -f /tmp/pw-reset.sql -q 2>/dev/null && echo "Database reset (clean)."
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
if ! docker inspect "$PW_CONTAINER" &>/dev/null; then
  docker run -d --name "$PW_CONTAINER" --network host "$PLAYWRIGHT_IMAGE" sleep 86400
else
  STATUS=$(docker inspect -f '{{.State.Status}}' "$PW_CONTAINER" 2>/dev/null)
  if [ "$STATUS" != "running" ]; then
    docker rm -f "$PW_CONTAINER"
    docker run -d --name "$PW_CONTAINER" --network host "$PLAYWRIGHT_IMAGE" sleep 86400
  fi
fi

INSTALL_CMD="if [ ! -d /tmp/node_modules ]; then cd /tmp && PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1 npm install playwright@${PLAYWRIGHT_VERSION} @playwright/test@${PLAYWRIGHT_VERSION} -q 2>&1 | grep -v '^npm notice'; fi"

# ── Clean stale artifacts in container and host ───────────────────────────────
docker exec "$PW_CONTAINER" bash -c "rm -rf /tmp/e2e /tmp/playwright.config.js /tmp/test-results /tmp/pw-report && rm -f /tmp/*.spec.js"
rm -rf /app/playwright/pw-report

# ── Sync spec files ───────────────────────────────────────────────────────────
docker exec "$PW_CONTAINER" bash -c "mkdir -p /tmp/e2e"
for f in /app/playwright/e2e/*.spec.js; do
  [ -f "$f" ] && docker cp "$f" "$PW_CONTAINER":/tmp/e2e/ 2>/dev/null
done
if [ -d /app/playwright/e2e/_flows ]; then
  docker exec "$PW_CONTAINER" bash -c "mkdir -p /tmp/e2e/_flows"
  for f in /app/playwright/e2e/_flows/*.js; do
    [ -f "$f" ] && docker cp "$f" "$PW_CONTAINER":/tmp/e2e/_flows/ 2>/dev/null
  done
fi
[ -f /app/playwright/e2e/_helpers.js ] && docker cp /app/playwright/e2e/_helpers.js "$PW_CONTAINER":/tmp/e2e/
docker cp /app/playwright/playwright.config.js "$PW_CONTAINER":/tmp/
docker cp /app/playwright/reporter.js "$PW_CONTAINER":/tmp/

# ── Build run command ─────────────────────────────────────────────────────────
# APP_URL must be forwarded explicitly -- playwright.config.js reads it from the pw-runner
# container's own env, not this script's shell (docker exec starts a fresh environment).
PW_ENV="PLAYWRIGHT_BROWSERS_PATH=/ms-playwright APP_URL=$APP_URL"
[ -n "$UX" ]   && PW_ENV="$PW_ENV PW_SCREENSHOTS=1"
[ -n "$FULL" ] && PW_ENV="$PW_ENV PW_FULL=1"

if [ -n "$SCENARIO" ]; then
  if [ "$SCENARIO" = "e2e" ]; then
    GREP_ARG=${GREP:+--grep "$GREP"}
    docker exec "$PW_CONTAINER" bash -c "$INSTALL_CMD && cd /tmp && $PW_ENV npx playwright test $SCENARIO/ $GREP_ARG --config playwright.config.js"
  else
    if [[ "$SCENARIO" == */* ]]; then
      SPEC_FILE="/app/playwright/${SCENARIO%.spec}.spec.js"
    else
      SPEC_FILE=$(find /app/playwright -name "${SCENARIO%.spec}.spec.js" -not -path "*/node_modules/*" | head -1)
    fi
    if [ ! -f "$SPEC_FILE" ]; then
      echo "Error: spec not found: $SCENARIO"
      echo "Available:"
      find /app/playwright -name "*.spec.js" -not -path "*/node_modules/*" | sort | sed "s|/app/playwright/||"
      exit 1
    fi
    SPEC_REL="${SPEC_FILE#/app/playwright/}"
    GREP_ARG=${GREP:+--grep "$GREP"}
    docker exec "$PW_CONTAINER" bash -c "$INSTALL_CMD && cd /tmp && $PW_ENV npx playwright test $SPEC_REL $GREP_ARG --config playwright.config.js"
  fi
else
  GREP_ARG=${GREP:+--grep "$GREP"}
  docker exec "$PW_CONTAINER" bash -c "$INSTALL_CMD && cd /tmp && $PW_ENV npx playwright test $GREP_ARG --config playwright.config.js"
fi

EXIT_CODE=$?

# ── Copy HTML report back ─────────────────────────────────────────────────────
mkdir -p /app/playwright/pw-report
docker cp "$PW_CONTAINER":/tmp/pw-report/. /app/playwright/pw-report/ 2>/dev/null && \
  echo "HTML report: /app/playwright/pw-report/index.html"

# ── Update test coverage ──────────────────────────────────────────────────────
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
docker cp "$PW_CONTAINER":/tmp/pw-live.log /tmp/pw-live.log 2>/dev/null || true
bash "$ROOT/scripts/update-test-coverage.sh" /tmp/pw-live.log || true

exit $EXIT_CODE
