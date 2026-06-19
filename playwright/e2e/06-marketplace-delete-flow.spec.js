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

  test('userEn: cancel delete keeps card, confirm delete removes card and list shrinks', async () => {
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

  test('adminEn: cancel delete keeps user row, confirm delete removes row and grid shrinks', async () => {
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
