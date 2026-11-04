/* ── Header ───────────────────────────────────────────────────────────────────
 * Description: Opens one web page in headless Chromium and saves a full-page PNG, optionally after
 *   clicking one button by its visible name (to capture a dialog a button opens). A look-at-a-page
 *   tool for pages outside the test suite, for example the Dagu web UI; it asserts nothing.
 * Usage: run inside pw-runner by `playwright/run.sh --shot <url> [--shot-name <name>] [--shot-click <button text>]`:
 *   node shot.js <url> <output-png-path> [<button name to click first>]
 * Uses: playwright (chromium).
 * Env: None.
 * Input: the url and the optional button name given as arguments; the page itself.
 * Outputs: the PNG at the given path (viewport 1400x900, full page).
 * Returns: 0 when the PNG was written; 1 with the error message on stderr otherwise.
 * ─────────────────────────────────────────────────────────────────────────────
 */
const { chromium } = require('playwright');

const [url, outputPath, buttonName] = process.argv.slice(2);

(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1400, height: 900 } });
  await page.goto(url, { waitUntil: 'networkidle' });
  if (buttonName) {
    await page.getByRole('button', { name: buttonName }).first().click();
    await page.locator('[role="dialog"]').first().waitFor({ state: 'visible', timeout: 3000 }).catch(() => {});
    await page.waitForLoadState('networkidle');
  }
  await page.screenshot({ path: outputPath, fullPage: true });
  await browser.close();
})().catch((error) => {
  console.error(error.message);
  process.exit(1);
});
