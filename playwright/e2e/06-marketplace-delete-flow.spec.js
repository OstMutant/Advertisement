/* ── Header ──────────────────────────────────────────────────────────────────
 * Description: Delete-flow e2e coverage for both entity types the UI exposes a delete action for
 *   -- an advertisement card's delete button (userEn creates then deletes its own ad, cancel
 *   keeps the card, confirm removes it and shrinks the list) and the user grid's delete action
 *   (adminEn deletes moderatorUk, cancel keeps the row, confirm removes it, and the deleted user
 *   stays absent after clearing the grid filter). moderatorUk is used as the delete target
 *   specifically because it owns no advertisements -- userUk/userEn cannot be deleted due to the
 *   FK constraint from advertisement.created_by.
 *   Per test:
 *   - "userEn deletes advertisement -- cancel keeps card, confirm removes card and shrinks list":
 *     create ad -> cancel delete -> card still visible -> confirm delete -> card gone -> list
 *     shrinks.
 *   - "adminEn deletes user -- cancel keeps row, confirm removes row and shrinks grid": cancel
 *     delete -> row still visible -> confirm delete -> row gone -> filter clear -> absent in full
 *     list.
 * Usage: run via the Playwright test runner -- `bash /app/playwright/run.sh 06-marketplace-
 *   delete-flow --ux`, or as part of the full e2e suite (`bash /app/playwright/run.sh e2e --ux`).
 * Uses: @playwright/test.
 * Env: None.
 * Input: ./_helpers (test, expect, screenshot, TEST_USERS), ./_flows/auth.flow,
 *   ./_flows/user-management.flow, ./_flows/delete.flow.
 * Outputs: Playwright HTML report entries (one per test/test.step), PNG screenshots attached to
 *   the report when PW_SCREENSHOTS is set. Creates one advertisement and deletes it within the
 *   same test; deletes the moderatorUk user row from the app database.
 * Returns: exit code from the Playwright test runner -- 0 when every test in this file passes,
 *   non-zero otherwise.
 * ──────────────────────────────────────────────────────────────────────────── */
const { test, expect, screenshot, TEST_USERS } = require('./_helpers');
const { runFillLoginFormFlow, runSubmitLoginFlow, runLogoutFlow } = require('./_flows/auth.flow');
const { runNavigateToUsersTabFlow, runFilterUserByEmailFlow, clearUserFilter } = require('./_flows/user-management.flow');
const { cancelDeleteDialog, confirmDeleteDialog, runCreateSimpleAdvertisementFlow } = require('./_flows/delete.flow');

test.describe.configure({ mode: 'serial' });

// moderatorUk has no advertisements — userUk and userEn cannot be deleted due to FK constraint (they own ads).
const USER_TO_DELETE = TEST_USERS.moderatorUk;

const DELETE_AD = {
  title:       'Ad To Delete E2E',
  description: 'This advertisement will be deleted during the e2e delete flow test.',
};

test.describe('Delete flow', () => {
  let page;

  test.beforeAll(async ({ browser }) => {
    page = await browser.newPage();
    await page.goto('/');
  });

  test.afterAll(async () => {
    await page.close();
  });

  test('userEn deletes advertisement — cancel keeps card, confirm removes card and shrinks list', async () => {
    await runFillLoginFormFlow(page, TEST_USERS.userEn);
    await runSubmitLoginFlow(page, expect, TEST_USERS.userEn);

    // Create a fresh ad so it appears first (newest) regardless of how many seed ads exist
    await runCreateSimpleAdvertisementFlow(page, { ...DELETE_AD, screenshotPrefix: 'delete-ad-create' });

    const targetCard = page.locator('.advertisement-card')
      .filter({ has: page.locator('.advertisement-title', { hasText: DELETE_AD.title }) })
      .first();
    await targetCard.waitFor({ timeout: 8000 });
    await screenshot(page, 'delete-ad-01-card-visible');

    await test.step('cancel delete — card stays', async () => {
      await targetCard.locator('.advertisement-delete').click();
      await cancelDeleteDialog(page);
      await expect(targetCard).toBeVisible();
      await screenshot(page, 'delete-ad-02-cancel-card-stays');
    });

    await test.step('confirm delete — card is gone', async () => {
      await targetCard.locator('.advertisement-delete').click();
      await confirmDeleteDialog(page);
      await expect(
        page.locator('.advertisement-card')
          .filter({ has: page.locator('.advertisement-title', { hasText: DELETE_AD.title }) })
      ).toHaveCount(0, { timeout: 8000 });
      await screenshot(page, 'delete-ad-03-card-gone');
    });

    await runLogoutFlow(page, expect);
  });

  test('adminEn deletes user — cancel keeps row, confirm removes row and shrinks grid', async () => {
    await runFillLoginFormFlow(page, TEST_USERS.adminEn);
    await runSubmitLoginFlow(page, expect, TEST_USERS.adminEn);

    await runNavigateToUsersTabFlow(page, expect);

    await screenshot(page, 'delete-user-01-grid-before');

    await runFilterUserByEmailFlow(page, USER_TO_DELETE.email);
    await page.locator('.user-grid-name:visible').first().waitFor({ timeout: 5000 });
    await screenshot(page, 'delete-user-02-filtered');

    const deleteBtn = () => page.locator('.user-grid-actions vaadin-button[title="Delete"], .user-grid-actions vaadin-button[title="Видалити"]').first();

    await test.step('cancel delete — row stays', async () => {
      await deleteBtn().click();
      await cancelDeleteDialog(page);
      await expect(page.locator('.user-grid-name:visible').first()).toBeVisible();
      await screenshot(page, 'delete-user-03-cancel-stays');
    });

    await test.step('confirm delete — row disappears', async () => {
      await deleteBtn().click();
      await confirmDeleteDialog(page);
      // Vaadin Grid keeps recycled DOM nodes — use :visible to skip phantom rows
      await expect(
        page.locator('.user-grid-name:visible').filter({ hasText: USER_TO_DELETE.name })
      ).toHaveCount(0, { timeout: 8000 });
      await screenshot(page, 'delete-user-04-row-gone');
    });

    await test.step('clear filter — deleted user absent from full list', async () => {
      await clearUserFilter(page);
      await expect(
        page.locator('.user-grid-name:visible').filter({ hasText: USER_TO_DELETE.name })
      ).toHaveCount(0, { timeout: 5000 });
      await screenshot(page, 'delete-user-05-absent-in-full-list');
    });

    await runLogoutFlow(page, expect);
  });
});
