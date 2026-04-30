#!/bin/bash
# Usage:
#   ./playwright/run.sh                  — run all spec files
#   ./playwright/run.sh smoke            — run smoke.spec.js
#   ./playwright/run.sh --ux             — run all with local screenshots
#   ./playwright/run.sh smoke --ux       — run one with local screenshots

# ── Parse args ───────────────────────────────────────────────────────────────
SCENARIO=""
UX_FLAG=""
for arg in "$@"; do
  if [ "$arg" = "--ux" ]; then UX_FLAG="--ux";
  else SCENARIO="$arg"; fi
done

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

# ── Clean stale artifacts in container ───────────────────────────────────────
docker exec pw-runner bash -c "rm -rf /tmp/*.spec.js /tmp/_test-helpers.js /tmp/playwright.config.js /tmp/test-results /tmp/screenshots"

# ── Sync spec files ───────────────────────────────────────────────────────────
for f in /app/playwright/*.spec.js \
          /app/playwright/_test-helpers.js \
          /app/playwright/playwright.config.js; do
  docker cp "$f" pw-runner:/tmp/ 2>/dev/null
done

# ── Build run command ─────────────────────────────────────────────────────────
PW_ENV="PLAYWRIGHT_BROWSERS_PATH=/ms-playwright"
[ "$UX_FLAG" = "--ux" ] && PW_ENV="$PW_ENV PW_UX=1 SCREENSHOT_DIR=/tmp/screenshots"

if [ -n "$SCENARIO" ]; then
  SPEC="${SCENARIO%.spec}.spec.js"
  if [ ! -f "/app/playwright/$SPEC" ]; then
    echo "Error: /app/playwright/$SPEC not found"
    echo "Available: $(ls /app/playwright/*.spec.js | xargs -n1 basename | sed 's/.spec.js//' | tr '\n' ' ')"
    exit 1
  fi
  docker exec pw-runner bash -c "$INSTALL_CMD && cd /tmp && $PW_ENV npx playwright test $SPEC --config playwright.config.js"
else
  docker exec pw-runner bash -c "$INSTALL_CMD && cd /tmp && $PW_ENV npx playwright test --config playwright.config.js"
fi

EXIT_CODE=$?

# ── Copy HTML report back ─────────────────────────────────────────────────────
rm -rf /app/playwright/pw-report
mkdir -p /app/playwright/pw-report
docker cp pw-runner:/tmp/pw-report/. /app/playwright/pw-report/ 2>/dev/null && \
  echo "HTML report: /app/playwright/pw-report/index.html"

# ── Copy screenshots back (only in --ux mode) ─────────────────────────────────
if [ "$UX_FLAG" = "--ux" ]; then
  rm -rf /app/playwright/screenshots
  mkdir -p /app/playwright/screenshots
  docker cp pw-runner:/tmp/screenshots/. /app/playwright/screenshots/ 2>/dev/null
  echo "Screenshots: /app/playwright/screenshots/"
  ls /app/playwright/screenshots/ 2>/dev/null
fi

exit $EXIT_CODE
