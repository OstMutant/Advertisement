const { test, expect, screenshot, TEST_USERS } = require('./_helpers');
const { runFillLoginFormFlow, runSubmitLoginFlow, runLogoutFlow } = require('./_flows/auth.flow');

test.describe.configure({ mode: 'serial' });

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
  let page;

  test.beforeAll(async ({ browser }) => {
    page = await browser.newPage();
    await page.goto('/');
  });

  test.afterAll(async () => {
    await page.close();
  });

  test('adminEn — no unlabeled icon-only buttons across all main tabs', async () => {
    await runFillLoginFormFlow(page, TEST_USERS.adminEn);
    await runSubmitLoginFlow(page, expect, TEST_USERS.adminEn);
    await assertNoUnlabeledIconButtons(page, 'a11y-advertisements-tab');

    await page.locator('vaadin-tab').filter({ hasText: /Users|Користувачі/i }).click();
    await assertNoUnlabeledIconButtons(page, 'a11y-users-tab');

    await page.locator('vaadin-tab').filter({ hasText: /Timeline|Таймлайн/i }).click();
    await assertNoUnlabeledIconButtons(page, 'a11y-timeline-tab');

    await page.locator('vaadin-tab').filter({ hasText: /Reference Data|Довідникові дані/i }).click();
    await assertNoUnlabeledIconButtons(page, 'a11y-reference-data-tab');

    await runLogoutFlow(page, expect);
  });
});
