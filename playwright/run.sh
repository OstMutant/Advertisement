#!/bin/bash
# Usage: ./playwright/run.sh <scenario> [--ux]
# Example: ./playwright/run.sh add-advertisement
#          ./playwright/run.sh add-advertisement --ux

SCENARIO=$1
if [ -z "$SCENARIO" ]; then
  echo "Usage: ./playwright/run.sh <scenario> [--ux]"
  echo "Available: $(ls /app/playwright/*.js | grep -v _common | xargs -n1 basename | sed 's/.js//' | tr '\n' ' ')"
  exit 1
fi

SCRIPT="/app/playwright/${SCENARIO}.js"
if [ ! -f "$SCRIPT" ]; then
  echo "Error: scenario '$SCENARIO' not found at $SCRIPT"
  exit 1
fi

UX_FLAG=${2:-}
SCREENSHOT_DIR="/app/playwright/screenshots"

# For the smoke scenario — clear all screenshots before the run
if [ "$SCENARIO" = "smoke" ] && [ "$UX_FLAG" = "--ux" ]; then
  echo "Clearing old screenshots..."
  rm -f "$SCREENSHOT_DIR"/*.png
fi

# ── Reuse pw-runner if already running, otherwise start it ──────────────────
if ! docker inspect pw-runner &>/dev/null; then
  docker run -d --name pw-runner --network host mcr.microsoft.com/playwright:v1.52.0-jammy sleep 86400
else
  STATUS=$(docker inspect -f '{{.State.Status}}' pw-runner 2>/dev/null)
  if [ "$STATUS" != "running" ]; then
    docker rm -f pw-runner
    docker run -d --name pw-runner --network host mcr.microsoft.com/playwright:v1.52.0-jammy sleep 86400
  fi
fi

# Install node_modules into /tmp only once (docker cp doesn't touch subdirs)
INSTALL_CMD="if [ ! -d /tmp/node_modules ]; then cd /tmp && PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1 npm install playwright@1.52.0 @playwright/test@1.52.0 -q 2>&1 | grep -v '^npm notice'; fi"

# ── @playwright/test spec files (*.spec) ────────────────────────────────────
if [[ "$SCENARIO" == *.spec ]]; then
  SPEC_FILE="/app/playwright/${SCENARIO}.js"
  if [ ! -f "$SPEC_FILE" ]; then
    echo "Error: spec file '$SPEC_FILE' not found"
    exit 1
  fi
  docker cp "$SPEC_FILE" pw-runner:/tmp/scenario.spec.js
  docker cp /app/playwright/_common.js pw-runner:/tmp/_common.js
  docker cp /app/playwright/_test-helpers.js pw-runner:/tmp/_test-helpers.js
  docker cp /app/playwright/playwright.config.js pw-runner:/tmp/playwright.config.js

  docker exec pw-runner bash -c "$INSTALL_CMD && cd /tmp && PLAYWRIGHT_BROWSERS_PATH=/ms-playwright npx playwright test scenario.spec.js --config playwright.config.js"

  mkdir -p /app/playwright/pw-report
  docker cp pw-runner:/tmp/pw-report/. /app/playwright/pw-report/ 2>/dev/null && \
    echo "HTML report: /app/playwright/pw-report/index.html"
  exit $?
fi

# ── Legacy plain-node scenarios ──────────────────────────────────────────────
docker cp "$SCRIPT" pw-runner:/tmp/scenario.js
docker cp /app/playwright/_common.js pw-runner:/tmp/_common.js

if [ "$UX_FLAG" = "--ux" ]; then
  docker exec pw-runner bash -c "$INSTALL_CMD && mkdir -p /screenshots && cd /tmp && PLAYWRIGHT_BROWSERS_PATH=/ms-playwright SCREENSHOT_DIR=/screenshots node /tmp/scenario.js --ux"
  mkdir -p "$SCREENSHOT_DIR"
  docker cp pw-runner:/screenshots/. "$SCREENSHOT_DIR/" 2>/dev/null
  echo ""
  echo "Screenshots saved to $SCREENSHOT_DIR/"
  ls "$SCREENSHOT_DIR/"
else
  docker exec pw-runner bash -c "$INSTALL_CMD && cd /tmp && PLAYWRIGHT_BROWSERS_PATH=/ms-playwright node /tmp/scenario.js"
fi
