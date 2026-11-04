#!/usr/bin/env bash
# ── Header ──────────────────────────────────────────────────────────
# Description: Screenshots the screens of architecture-map.html through a headless Playwright browser
#   in the long-lived container arch-map-shot (left running, warm, like playwright/run.sh's
#   pw-runner), since the generated file has no automated visual test of its own, and checks what a
#   picture cannot show: every Database ERD table sits in a module frame, every ERD and Module
#   Dependencies arrow has a target row, every README flowchart block is drawn with the edge count
#   of its source, and the page raises no JavaScript error. Mirrors playwright/run.sh's container
#   conventions but is a standalone tool, not part of the e2e suite.
# Usage: bash docs/architecture/scripts/screenshot-architecture-map.sh
# Uses: bash, docker (the same pinned Playwright image and version as playwright/run.sh), node and
#   playwright inside that container.
# Env: None.
# Input: docs/architecture/architecture-map.html (must already exist and be current).
# Outputs: docs/architecture/scripts/architecture-map-screenshots/{01-system,02-diagrams-list,
#   03-diagram-module-deps,04-module-detail,04b-back-to-diagram-via-breadcrumb,05-diagram-spi,
#   06-diagram-spi-zoomed,07-pipelines,08-backlog,09-diagram-erd,09b-erd-graph,10-readme-flow-system,
#   11-readme-flow-tooling,11b-flow-block}.png (gitignored, ephemeral verification artifacts); the
#   Playwright driver /tmp/arch-map-shot.js on the host; the arch-map-shot container is left running.
# Returns: 0 when every screen was captured and every check held; 1 when the map file is missing, a
#   check failed or the page raised a JavaScript error.
# ─────────────────────────────────────────────────────────────────
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
  const depArrows = await page.evaluate(() => ({ edges: diagramCy.edges().length, withoutRow: diagramCy.edges().filter(e => !document.getElementById('dep-row-' + e.data('target'))).length }));
  console.log('Module Dependencies arrows/without table row:', JSON.stringify(depArrows));
  if (depArrows.withoutRow) jsErrors.push(depArrows.withoutRow + ' Module Dependencies arrows point at no table row');
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
  await page.waitForTimeout(1200); // cytoscape layout settle
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

  // Database ERD -- Cytoscape tables-and-relationships graph; every table must be a node.
  await goSystem(page);
  await page.locator('.special-card:has-text("Diagrams")').click();
  await page.waitForTimeout(300);
  await page.locator('.domain-group .card .card-title', { hasText: /^Entity Relationship Diagram$/ }).click();
  await page.waitForTimeout(1500);
  await shot(page, '09-diagram-erd');
  await page.locator('#diagram-cy-wrap').screenshot({ path: '/tmp/shot/09b-erd-graph.png' });
  const erd = await page.evaluate(() => ({
    nodes: diagramCy.nodes(':childless').length, frames: diagramCy.nodes(':parent').length,
    orphans: diagramCy.nodes(':childless').filter(n => n.parent().empty()).length,
    edges: diagramCy.edges().length, tables: MODEL.dbErd.tables.length,
    edgesWithoutRow: diagramCy.edges().filter(e => !document.getElementById(e.data('rowId'))).length
  }));
  console.log('ERD tables/nodes/module frames/orphans/edges:', JSON.stringify(erd));
  if (erd.edgesWithoutRow) jsErrors.push(erd.edgesWithoutRow + ' ERD arrows point at no schema row');
  if (erd.nodes !== erd.tables || erd.orphans) jsErrors.push('ERD graph has ' + erd.nodes + ' table nodes (' + erd.orphans + ' outside a module frame) for ' + erd.tables + ' tables');

  // README flow blocks on the System page (root README) and the Tooling page (INFRASTRUCTURE.md):
  // every fence must be drawn, none left as raw Mermaid text.
  for (const screen of ['System', 'Tooling']) {
    await goSystem(page);
    if (screen === 'Tooling') { await page.locator('.special-card:has-text("Tooling")').click(); await page.waitForTimeout(300); }
    const flow = await page.evaluate(() => ({
      blocks: document.querySelectorAll('.flow-block').length,
      drawn: document.querySelectorAll('.flow-block[data-drawn]').length,
      canvases: document.querySelectorAll('.flow-block canvas').length,
      rawMermaid: document.querySelectorAll('pre.mermaid').length,
      edgeMismatches: [...document.querySelectorAll('.flow-block .flow-src')].filter(p => parseFlowchart(p.textContent).edges.length !== (p.textContent.match(/-->|-\.->/g) || []).length).length
    }));
    console.log(screen + ' flow blocks:', JSON.stringify(flow));
    if (flow.blocks !== flow.drawn || flow.rawMermaid || flow.edgeMismatches) jsErrors.push(screen + ': flow blocks not all drawn ' + JSON.stringify(flow));
    await shot(page, screen === 'System' ? '10-readme-flow-system' : '11-readme-flow-tooling');
    if (screen === 'Tooling') await page.locator('.flow-block').first().screenshot({ path: '/tmp/shot/11b-flow-block.png' });
  }

  await browser.close();
  if (jsErrors.length) { console.error('JS errors during run:\n' + jsErrors.join('\n')); process.exit(1); }
  console.log('done');
})().catch(e => { console.error(e); process.exit(1); });
JS_EOF

docker cp /tmp/arch-map-shot.js "$CONTAINER:/tmp/shot/shot.js"
docker exec "$CONTAINER" sh -c "cd /tmp/shot && node shot.js"

SHOT_NAMES=(01-system 02-diagrams-list 03-diagram-module-deps 04-module-detail 04b-back-to-diagram-via-breadcrumb 05-diagram-spi 06-diagram-spi-zoomed 07-pipelines 08-backlog 09-diagram-erd 09b-erd-graph 10-readme-flow-system 11-readme-flow-tooling 11b-flow-block)
rm -f "$OUT_DIR"/*.png
for f in "${SHOT_NAMES[@]}"; do
  docker cp "$CONTAINER:/tmp/shot/$f.png" "$OUT_DIR/$f.png"
done

echo "Wrote ${#SHOT_NAMES[@]} screenshots to $OUT_DIR"
echo "Container '$CONTAINER' left running (warm, like pw-runner) -- stop with: docker rm -f $CONTAINER"
