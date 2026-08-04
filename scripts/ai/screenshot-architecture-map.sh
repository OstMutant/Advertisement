#!/usr/bin/env bash
# Screenshots architecture-map.html (Track A of the architecture control plane) via a headless
# Playwright browser, since this generated file has no automated visual test of its own and Claude
# has no display in this environment otherwise. Mirrors playwright/run.sh's container conventions (same pinned
# Playwright image/version) but is a standalone tool, not part of the e2e suite -- it screenshots
# a static generated file, not the running app.
#
# Usage:
#   bash scripts/ai/screenshot-architecture-map.sh
#
# Output: scripts/ai/architecture-map-screenshots/{01-system,02-module,03-database,04-spi,05-pipelines,06-backlog}.png
# (gitignored -- ephemeral verification artifacts, not committed).
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
HTML="$REPO_ROOT/architecture-map.html"
OUT_DIR="$REPO_ROOT/scripts/ai/architecture-map-screenshots"
CONTAINER="arch-map-shot"
PLAYWRIGHT_VERSION="1.61.1"
PLAYWRIGHT_IMAGE="mcr.microsoft.com/playwright:v${PLAYWRIGHT_VERSION}-jammy"

[ -f "$HTML" ] || { echo "ERROR: $HTML does not exist -- run generate-architecture-model.sh first."; exit 1; }
mkdir -p "$OUT_DIR"

# ── Reuse a warm container across runs (same pattern as playwright/run.sh's pw-runner) ─────────
if ! docker inspect "$CONTAINER" &>/dev/null; then
  echo "Starting $CONTAINER ($PLAYWRIGHT_IMAGE)..."
  docker run -d --name "$CONTAINER" "$PLAYWRIGHT_IMAGE" sleep infinity >/dev/null
fi
STATUS=$(docker inspect -f '{{.State.Status}}' "$CONTAINER" 2>/dev/null)
if [ "$STATUS" != "running" ]; then
  docker start "$CONTAINER" >/dev/null
fi

# Install the playwright npm package once per container lifetime, not on every run.
if ! docker exec "$CONTAINER" test -d /tmp/shot/node_modules/playwright; then
  echo "Installing playwright@$PLAYWRIGHT_VERSION in $CONTAINER (first run only)..."
  docker exec "$CONTAINER" sh -c "mkdir -p /tmp/shot && cd /tmp/shot && npm init -y >/dev/null 2>&1 && npm install playwright@$PLAYWRIGHT_VERSION --no-save >/dev/null 2>&1"
fi

docker cp "$HTML" "$CONTAINER:/tmp/shot/architecture-map.html"

cat > /tmp/arch-map-shot.js <<'JS_EOF'
const { chromium } = require('playwright');

async function shot(page, name) {
  await page.screenshot({ path: `/tmp/shot/${name}.png`, fullPage: true });
}
async function goSystem(page) {
  await page.locator('#breadcrumb a', { hasText: 'System' }).first().click();
  await page.waitForTimeout(300);
}

(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1400, height: 1000 } });
  await page.goto('file:///tmp/shot/architecture-map.html');
  await page.waitForTimeout(1500); // cytoscape layout settle
  await shot(page, '01-system');

  await page.locator('.domain-group .card').first().click();
  await page.waitForTimeout(400);
  await shot(page, '02-module');

  await goSystem(page);
  await page.locator('.special-card:has-text("Diagrams")').click();
  await page.waitForTimeout(300);
  await shot(page, '03-diagrams-list');

  // Open the SPI map diagram specifically -- it's the densest one, worth checking zoom on it.
  await page.locator('.domain-group .card', { hasText: 'SPI Dependency Graph' }).click();
  await page.waitForTimeout(1200); // mermaid render settle
  await shot(page, '04-diagram-spi');
  await page.locator('.zoom-controls button', { hasText: '+' }).click();
  await page.locator('.zoom-controls button', { hasText: '+' }).click();
  await page.waitForTimeout(200);
  await shot(page, '05-diagram-spi-zoomed');

  await goSystem(page);
  await page.locator('.special-card:has-text("Tooling")').click();
  await page.waitForTimeout(300);
  await shot(page, '06-pipelines');

  await goSystem(page);
  await page.locator('.special-card:has-text("Backlog")').click();
  await page.waitForTimeout(300);
  await shot(page, '07-backlog');

  await browser.close();
  console.log('done');
})().catch(e => { console.error(e); process.exit(1); });
JS_EOF

docker cp /tmp/arch-map-shot.js "$CONTAINER:/tmp/shot/shot.js"
docker exec "$CONTAINER" sh -c "cd /tmp/shot && node shot.js"

SHOT_NAMES=(01-system 02-module 03-diagrams-list 04-diagram-spi 05-diagram-spi-zoomed 06-pipelines 07-backlog)
rm -f "$OUT_DIR"/*.png
for f in "${SHOT_NAMES[@]}"; do
  docker cp "$CONTAINER:/tmp/shot/$f.png" "$OUT_DIR/$f.png"
done

echo "Wrote ${#SHOT_NAMES[@]} screenshots to $OUT_DIR"
echo "Container '$CONTAINER' left running (warm, like pw-runner) -- stop with: docker rm -f $CONTAINER"
