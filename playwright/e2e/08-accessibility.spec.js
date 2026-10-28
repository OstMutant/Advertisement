/* ── Header ──────────────────────────────────────────────────────────────────
 * Description: Accessibility smoke check -- as an administrator, walks the Advertisements, Users,
 *   Timeline and Reference Data tabs and asserts no icon-only button (vaadin-button or plain
 *   button with no visible text) is missing an aria-label. Searches recursively through shadow
 *   roots via a page.evaluate() DOM walk, since Vaadin components render their real buttons
 *   inside shadow DOM.
 * Usage: run via the Playwright test runner -- `bash /app/playwright/run.sh 08-accessibility
 *   --ux` (runs alone or with any other spec), or as part of the full e2e suite
 *   (`bash /app/playwright/run.sh e2e --ux`).
 * Uses: @playwright/test.
 * Env: None.
 * Input: ./_fixtures (test, expect, pageAs, adminAccount), ./_helpers (screenshot).
 * Outputs: Playwright HTML report entries (one per test), PNG screenshots attached to the report
 *   when PW_SCREENSHOTS is set. Creates only the worker's own administrator account.
 * Returns: exit code from the Playwright test runner -- 0 when every test in this file passes,
 *   non-zero otherwise.
 * ──────────────────────────────────────────────────────────────────────────── */
const { test, expect } = require('./_fixtures');
const { screenshot } = require('./_helpers');

async function assertNoUnlabeledIconButtons(page, screenshotName) {
  const offenders = await page.evaluate(() => {
    function findAll(root, selector, acc = []) {
      acc.push(...root.querySelectorAll(selector));
      for (const el of root.querySelectorAll('*')) {
        if (el.shadowRoot) findAll(el.shadowRoot, selector, acc);
      }
      return acc;
    }
    return findAll(document, 'vaadin-button, button')
      .filter(b => b.offsetParent !== null && !b.textContent.trim() && !b.getAttribute('aria-label'))
      .map(b => b.outerHTML.substring(0, 150));
  });
  expect(offenders, `Icon-only buttons missing aria-label: ${JSON.stringify(offenders)}`).toHaveLength(0);
  if (screenshotName) await screenshot(page, screenshotName);
}

test.describe('Accessibility — icon-only controls have accessible names', () => {
  test('administrator — no unlabeled icon-only buttons across all main tabs', async ({ pageAs, adminAccount }) => {
    const page = await pageAs(adminAccount);
    await assertNoUnlabeledIconButtons(page, 'a11y-advertisements-tab');

    await page.getByRole('tab', { name: /Users|Користувачі/i }).click();
    await assertNoUnlabeledIconButtons(page, 'a11y-users-tab');

    await page.getByRole('tab', { name: /Timeline|Таймлайн/i }).click();
    await assertNoUnlabeledIconButtons(page, 'a11y-timeline-tab');

    await page.getByRole('tab', { name: /Reference Data|Довідникові дані/i }).click();
    await assertNoUnlabeledIconButtons(page, 'a11y-reference-data-tab');
  });
});
