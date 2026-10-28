/* ── Header ──────────────────────────────────────────────────────────────────
 * Description: Delete-flow e2e coverage for both entity types the UI exposes a delete action for
 *   -- an advertisement card's delete button (a user creates then deletes their own ad, cancel
 *   keeps the card, confirm removes it and the title-filtered list goes from one ad to none) and
 *   the user grid's delete action (an administrator deletes a user created for the test, cancel
 *   keeps the row, confirm removes it, and the deleted user stays absent after clearing the grid
 *   filter and searching the e-mail again). The deleted user owns no advertisements, because a user
 *   who owns advertisements cannot be deleted due to the FK constraint from advertisement.created_by.
 *   Per test:
 *   - "user deletes advertisement -- cancel keeps card, confirm removes card and the filtered list
 *     goes to none": create ad -> filter by its title -> cancel delete -> card still visible ->
 *     confirm delete -> card gone -> filtered list empty.
 *   - "administrator deletes user -- cancel keeps row, confirm removes row, absent after clearing
 *     the filter": filter by e-mail -> cancel delete -> row still visible -> confirm delete -> row
 *     gone -> filter clear -> e-mail search finds no row.
 * Usage: run via the Playwright test runner -- `bash /app/playwright/run.sh 07-marketplace-
 *   delete-flow --ux` (runs alone or with any other spec), or as part of the full e2e suite
 *   (`bash /app/playwright/run.sh e2e --ux`).
 * Uses: @playwright/test.
 * Env: None.
 * Input: ./_fixtures (test, expect, pageAs, userAccount, adminAccount, createUser, unique),
 *   ./_helpers (screenshot), ./_flows/user-management.flow, ./_flows/delete.flow,
 *   ./_flows/advertisement-filter.flow.
 * Outputs: Playwright HTML report entries (one per test/test.step), PNG screenshots attached to
 *   the report when PW_SCREENSHOTS is set. Creates one uniquely titled advertisement and deletes
 *   it, and creates one uniquely named user and deletes it.
 * Returns: exit code from the Playwright test runner -- 0 when every test in this file passes,
 *   non-zero otherwise.
 * ──────────────────────────────────────────────────────────────────────────── */
const { test, expect } = require('./_fixtures');
const { screenshot } = require('./_helpers');
const { runNavigateToUsersTabFlow, runFilterUserByEmailFlow, clearUserFilter } = require('./_flows/user-management.flow');
const { cancelDeleteDialog, confirmDeleteDialog, runCreateSimpleAdvertisementFlow } = require('./_flows/delete.flow');
const { runOpenFilterPanelFlow, runFillTitleFilterFlow, runApplyFilterFlow } = require('./_flows/advertisement-filter.flow');

const DELETE_BUTTON = /^(Delete|Видалити)$/;

test.describe('Delete flow', () => {

  test('user deletes advertisement — cancel keeps card, confirm removes card and the filtered list goes to none', async ({ pageAs, userAccount, unique }) => {
    const page = await pageAs(userAccount);
    const title = unique('Ad To Delete');

    await runCreateSimpleAdvertisementFlow(page, {
      title,
      description: 'This advertisement will be deleted during the e2e delete flow test.',
      screenshotPrefix: 'delete-ad-create',
    });

    await runOpenFilterPanelFlow(page, expect);
    await runFillTitleFilterFlow(page, title);
    await runApplyFilterFlow(page, expect);

    const cardsWithTitle = page.locator('.advertisement-card')
      .filter({ has: page.getByRole('heading', { level: 3, name: title }) });
    await expect(cardsWithTitle).toHaveCount(1, { timeout: 8000 });
    await screenshot(page, 'delete-ad-01-card-visible');

    await test.step('cancel delete — card stays', async () => {
      await cardsWithTitle.first().getByRole('button', { name: DELETE_BUTTON }).click();
      await cancelDeleteDialog(page);
      await expect(cardsWithTitle).toHaveCount(1);
      await screenshot(page, 'delete-ad-02-cancel-card-stays');
    });

    await test.step('confirm delete — card is gone and the filtered list is empty', async () => {
      await cardsWithTitle.first().getByRole('button', { name: DELETE_BUTTON }).click();
      await confirmDeleteDialog(page);
      await expect(cardsWithTitle).toHaveCount(0, { timeout: 8000 });
      await expect(page.locator('.pagination-count')).toContainText('0–0', { timeout: 8000 });
      await screenshot(page, 'delete-ad-03-card-gone');
    });
  });

  test('administrator deletes user — cancel keeps row, confirm removes row, absent after clearing the filter', async ({ pageAs, adminAccount, createUser }) => {
    const target = await createUser();
    const page = await pageAs(adminAccount);

    await runNavigateToUsersTabFlow(page, expect);
    await screenshot(page, 'delete-user-01-grid-before');

    await runFilterUserByEmailFlow(page, target.email);
    const userNames = page.locator('.user-grid-name:visible');
    await userNames.first().waitFor({ timeout: 5000 });
    await screenshot(page, 'delete-user-02-filtered');

    const deleteBtn = () => page.locator('.user-grid-actions').getByRole('button', { name: DELETE_BUTTON }).first();

    await test.step('cancel delete — row stays', async () => {
      await deleteBtn().click();
      await cancelDeleteDialog(page);
      await expect(userNames.filter({ hasText: target.name })).toHaveCount(1);
      await screenshot(page, 'delete-user-03-cancel-stays');
    });

    await test.step('confirm delete — row disappears', async () => {
      await deleteBtn().click();
      await confirmDeleteDialog(page);
      // Vaadin Grid keeps recycled DOM nodes — use :visible to skip phantom rows
      await expect(
        userNames.filter({ hasText: target.name })
      ).toHaveCount(0, { timeout: 8000 });
      await screenshot(page, 'delete-user-04-row-gone');
    });

    await test.step('clear filter and search the e-mail again — deleted user absent', async () => {
      await clearUserFilter(page);
      await runFilterUserByEmailFlow(page, target.email);
      await expect(
        userNames.filter({ hasText: target.name })
      ).toHaveCount(0, { timeout: 5000 });
      await screenshot(page, 'delete-user-05-absent-in-full-list');
    });
  });
});
