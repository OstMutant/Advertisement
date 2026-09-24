#!/usr/bin/env bash
# Description: Screenshots every screen of architecture-map.html via a headless Playwright
#   browser, since the generated file has no automated visual test of its own.
# Uses: bash, a headless Playwright browser (Docker, same pinned image/version as playwright/run.sh).
# Input: docs/architecture/architecture-map.html (must already exist and be current).
# Output: docs/architecture/scripts/architecture-map-screenshots/{01-system,02-diagrams-list,
#   03-diagram-module-deps,04-module-detail,05-diagram-spi,06-diagram-spi-zoomed,07-pipelines,
#   08-backlog}.png (gitignored -- ephemeral verification artifacts, not committed).
#
# Mirrors playwright/run.sh's container conventions but is a standalone tool, not part of the e2e
# suite -- it screenshots a static generated file, not the running app.
#
# Usage:
#   bash docs/architecture/scripts/screenshot-architecture-map.sh
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
HTML="$REPO_ROOT/docs/architecture/architecture-map.html"
OUT_DIR="$REPO_ROOT/docs/architecture/scripts/architecture-map-screenshots"
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
  const jsErrors = [];
  page.on('pageerror', e => jsErrors.push(String(e)));
  await page.goto('file:///tmp/shot/architecture-map.html');
  await page.waitForTimeout(500);
  await shot(page, '01-system'); // must show only 3 cards -- no map, no domain-grouped module list

  await page.locator('.special-card:has-text("Diagrams")').click();
  await page.waitForTimeout(300);
  await shot(page, '02-diagrams-list'); // Module Dependencies group should carry the "draggable" badge

  // Module Dependencies -- now the domain-colored, click-navigable graph (moved off the old
  // System page, one shared renderer instead of two -- see renderModuleDependencyGraph()).
  await page.locator('.domain-group .card .card-title', { hasText: /^Dependency Graph$/ }).click();
  await page.waitForTimeout(1500); // cytoscape layout settle
  await shot(page, '03-diagram-module-deps');

  // Confirm the module detail page (reached by clicking a node in the graph above) still renders
  // correctly -- driven directly via the exposed navigate() rather than a canvas-coordinate click,
  // since node positions depend on dagre's layout and aren't stable enough to hardcode a click at.
  await page.evaluate(() => navigate({ screen: 'module', id: 'platform-commons' }));
  await page.waitForTimeout(300);
  await shot(page, '04-module-detail');

  // Breadcrumb must offer a way back to the diagram (not just all the way to System) -- click the
  // "Module Dependencies — Dependency Graph" crumb segment and confirm it lands back on the graph.
  await page.locator('#breadcrumb a', { hasText: 'Module Dependencies' }).click();
  await page.waitForTimeout(800);
  await shot(page, '04b-back-to-diagram-via-breadcrumb');

  await goSystem(page);
  await page.locator('.special-card:has-text("Diagrams")').click();
  await page.waitForTimeout(300);
  // SPI map -- split one tab per subsystem (see MODEL.diagramGroups["02-spi-map"].diagrams), the
  // group heading itself ("SPI Map") renders in an <h3>, not inside any .card -- click one real
  // per-subsystem card instead. Advertisement is one of the densest subsystems, worth checking
  // zoom on it.
  await page.locator('.domain-group .card', { hasText: 'Advertisement Subsystem' }).click();
  await page.waitForTimeout(1200); // mermaid render settle
  await shot(page, '05-diagram-spi');
  await page.locator('.zoom-controls button', { hasText: '+' }).click();
  await page.locator('.zoom-controls button', { hasText: '+' }).click();
  await page.waitForTimeout(200);
  await shot(page, '06-diagram-spi-zoomed');

  await goSystem(page);
  await page.locator('.special-card:has-text("Tooling")').click();
  await page.waitForTimeout(300);
  await shot(page, '07-pipelines');

  await goSystem(page);
  await page.locator('.special-card:has-text("Backlog")').click();
  await page.waitForTimeout(300);
  await shot(page, '08-backlog');

  await browser.close();
  if (jsErrors.length) { console.error('JS errors during run:\n' + jsErrors.join('\n')); process.exit(1); }
  console.log('done');
})().catch(e => { console.error(e); process.exit(1); });
JS_EOF

docker cp /tmp/arch-map-shot.js "$CONTAINER:/tmp/shot/shot.js"
docker exec "$CONTAINER" sh -c "cd /tmp/shot && node shot.js"

SHOT_NAMES=(01-system 02-diagrams-list 03-diagram-module-deps 04-module-detail 04b-back-to-diagram-via-breadcrumb 05-diagram-spi 06-diagram-spi-zoomed 07-pipelines 08-backlog)
rm -f "$OUT_DIR"/*.png
for f in "${SHOT_NAMES[@]}"; do
  docker cp "$CONTAINER:/tmp/shot/$f.png" "$OUT_DIR/$f.png"
done

echo "Wrote ${#SHOT_NAMES[@]} screenshots to $OUT_DIR"
echo "Container '$CONTAINER' left running (warm, like pw-runner) -- stop with: docker rm -f $CONTAINER"
