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

docker run -d --name pw-runner --network host mcr.microsoft.com/playwright:v1.52.0-jammy sleep 300
docker cp "$SCRIPT" pw-runner:/tmp/scenario.js
docker cp /app/playwright/_common.js pw-runner:/tmp/_common.js

if [ "$UX_FLAG" = "--ux" ]; then
  docker exec pw-runner bash -c "mkdir -p /screenshots && cd /tmp && PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1 npm install playwright@1.52.0 -q && PLAYWRIGHT_BROWSERS_PATH=/ms-playwright SCREENSHOT_DIR=/screenshots node /tmp/scenario.js --ux"
  mkdir -p "$SCREENSHOT_DIR"
  docker cp pw-runner:/screenshots/. "$SCREENSHOT_DIR/" 2>/dev/null
  echo ""
  echo "Screenshots saved to $SCREENSHOT_DIR/"
  ls "$SCREENSHOT_DIR/"
else
  docker exec pw-runner bash -c "cd /tmp && PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1 npm install playwright@1.52.0 -q && PLAYWRIGHT_BROWSERS_PATH=/ms-playwright node /tmp/scenario.js"
fi

docker rm -f pw-runner
