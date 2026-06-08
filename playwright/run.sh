#!/bin/bash
# Usage:
#   ./playwright/run.sh                        — run all spec files
#   ./playwright/run.sh marketplace            — run all marketplace specs
#   ./playwright/run.sh audit                  — run all audit specs
#   ./playwright/run.sh attachment             — run all attachment specs
#   ./playwright/run.sh e2e                    — run all e2e specs (clean DB)
#   ./playwright/run.sh audit/advertisement-history  — run by group/name
#   ./playwright/run.sh --ux                   — all tests with screenshots

# ── Parse args ───────────────────────────────────────────────────────────────
UX=""
SCENARIO=""
for arg in "$@"; do
  if [ "$arg" = "--ux" ]; then UX=1; else SCENARIO="$arg"; fi
done

# ── Ensure marketplace-app is running ──────────────────────────────────────
APP_URL="${APP_URL:-http://localhost:8081}"

if ! docker inspect marketplace-app &>/dev/null; then
  echo "ERROR: Container 'marketplace-app' not found. Build and start it:"
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

STATUS=$(docker inspect -f '{{.State.Status}}' marketplace-app)
if [ "$STATUS" != "running" ]; then
  echo "Starting marketplace-app..."
  docker start marketplace-app
  echo "Waiting for startup (tailing logs)..."
  end=$((SECONDS + 120))
  while true; do
    if docker logs marketplace-app 2>&1 | grep -q "Started Application"; then
      echo "App ready."
      break
    fi
    if [ $SECONDS -ge $end ]; then
      echo "ERROR: startup timed out"
      docker logs --tail=40 marketplace-app
      exit 1
    fi
    sleep 2
  done
else
  curl -s --max-time 5 "$APP_URL" >/dev/null 2>&1 \
    || { echo "ERROR: marketplace-app running but not responding"; docker logs --tail=30 marketplace-app; exit 1; }
fi

# ── Reset / seed database ─────────────────────────────────────────────────────
DB_CONTAINER=$(docker ps --filter "publish=5432" --format "{{.Names}}" | head -1)
if [ -n "$DB_CONTAINER" ]; then
  if [ "$SCENARIO" = "e2e" ]; then
    docker cp /app/scripts/database/reset-clean.sql "$DB_CONTAINER":/tmp/pw-reset.sql 2>/dev/null
    docker exec "$DB_CONTAINER" psql -U experiments_user -d experiments \
      -f /tmp/pw-reset.sql -q 2>/dev/null && echo "Database reset (clean)." || true
  else
    docker cp /app/scripts/database/reset.sql "$DB_CONTAINER":/tmp/pw-reset.sql 2>/dev/null
    docker exec "$DB_CONTAINER" psql -U experiments_user -d experiments \
      -f /tmp/pw-reset.sql -q 2>/dev/null && echo "Database reset (seeded)." || true
  fi
else
  echo "WARNING: No postgres container found on port 5432 — test accounts may not exist."
fi

# ── Reuse pw-runner if already running, otherwise start it ───────────────────
if ! docker inspect pw-runner &>/dev/null; then
  docker run -d --name pw-runner --network host mcr.microsoft.com/playwright:v1.52.0-jammy sleep 86400
else
  STATUS=$(docker inspect -f '{{.State.Status}}' pw-runner 2>/dev/null)
  if [ "$STATUS" != "running" ]; then
    docker rm -f pw-runner
    docker run -d --name pw-runner --network host mcr.microsoft.com/playwright:v1.52.0-jammy sleep 86400
  fi
fi

INSTALL_CMD="if [ ! -d /tmp/node_modules ]; then cd /tmp && PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1 npm install playwright@1.52.0 @playwright/test@1.52.0 -q 2>&1 | grep -v '^npm notice'; fi"

# ── Clean stale artifacts in container and host ───────────────────────────────
docker exec pw-runner bash -c "rm -rf /tmp/marketplace /tmp/audit /tmp/attachment /tmp/e2e /tmp/_test-helpers.js /tmp/playwright.config.js /tmp/test-results /tmp/pw-report && rm -f /tmp/*.spec.js"
rm -rf /app/playwright/pw-report

# ── Sync spec files ───────────────────────────────────────────────────────────
docker exec pw-runner bash -c "mkdir -p /tmp/marketplace /tmp/audit /tmp/attachment /tmp/e2e"
for group in marketplace audit attachment e2e; do
  for f in /app/playwright/$group/*.spec.js; do
    [ -f "$f" ] && docker cp "$f" pw-runner:/tmp/$group/ 2>/dev/null
  done
  docker cp /app/playwright/_test-helpers.js pw-runner:/tmp/$group/ 2>/dev/null
done
if [ -d /app/playwright/e2e/_flows ]; then
  docker exec pw-runner bash -c "mkdir -p /tmp/e2e/_flows"
  for f in /app/playwright/e2e/_flows/*.js; do
    [ -f "$f" ] && docker cp "$f" pw-runner:/tmp/e2e/_flows/ 2>/dev/null
  done
fi
[ -f /app/playwright/e2e/_helpers.js ] && docker cp /app/playwright/e2e/_helpers.js pw-runner:/tmp/e2e/
docker cp /app/playwright/_test-helpers.js pw-runner:/tmp/
docker cp /app/playwright/playwright.config.js pw-runner:/tmp/
docker cp /app/playwright/reporter.js pw-runner:/tmp/

# ── Build run command ─────────────────────────────────────────────────────────
PW_ENV="PLAYWRIGHT_BROWSERS_PATH=/ms-playwright"
[ -n "$UX" ] && PW_ENV="$PW_ENV PW_SCREENSHOTS=1"

if [ -n "$SCENARIO" ]; then
  if [ "$SCENARIO" = "marketplace" ] || [ "$SCENARIO" = "audit" ] || [ "$SCENARIO" = "attachment" ] || [ "$SCENARIO" = "e2e" ]; then
    docker exec pw-runner bash -c "$INSTALL_CMD && cd /tmp && $PW_ENV npx playwright test $SCENARIO/ --config playwright.config.js"
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
    docker exec pw-runner bash -c "$INSTALL_CMD && cd /tmp && $PW_ENV npx playwright test $SPEC_REL --config playwright.config.js"
  fi
else
  docker exec pw-runner bash -c "$INSTALL_CMD && cd /tmp && $PW_ENV npx playwright test --config playwright.config.js"
fi

EXIT_CODE=$?

# ── Copy HTML report back ─────────────────────────────────────────────────────
mkdir -p /app/playwright/pw-report
docker cp pw-runner:/tmp/pw-report/. /app/playwright/pw-report/ 2>/dev/null && \
  echo "HTML report: /app/playwright/pw-report/index.html"

exit $EXIT_CODE
