/* ── Header ──────────────────────────────────────────────────────────────────
 * Description: Setup project that runs before every other test -- signs up adminEn, the first
 *   account on an empty database, which the app promotes to ADMIN automatically, and checks the
 *   settings, timeline and user audit activity that sign-up creates. Skips itself when the
 *   administrator already exists, so the suite also passes on a used database.
 * Usage: run by the Playwright test runner as the "setup" project, never directly -- every other
 *   project depends on it.
 * Uses: @playwright/test (test, expect) via ./_helpers.
 * Env: ADMIN_EMAIL, ADMIN_PASSWORD (through TEST_USERS.adminEn).
 * Input: ./_helpers (test, expect, TEST_USERS), ./_flows/signup.flow (runSignUpFlow),
 *   ./_flows/audit.flow (runVerifySettingsAfterSignupFlow, runVerifyUserAuditActivityFlow),
 *   ./_flows/timeline.flow, ./_flows/user-management.flow (runNavigateToUsersTabFlow).
 * Outputs: Playwright HTML report entry; creates the adminEn account.
 * Returns: Playwright's own pass/fail exit code convention.
 * ──────────────────────────────────────────────────────────────────────────── */
const { test, expect, TEST_USERS } = require('./_helpers');
const { runSignUpFlow } = require('./_flows/signup.flow');
const { runNavigateToUsersTabFlow } = require('./_flows/user-management.flow');
const { runVerifySettingsAfterSignupFlow, runVerifyUserAuditActivityFlow } = require('./_flows/audit.flow');
const { openTimelineTab, openTimelineFilter, closeTimelineFilter, assertFeedHasRow, assertActorPickerVisible } = require('./_flows/timeline.flow');

test('adminEn signs up — first user auto-promoted to ADMIN, settings open, timeline and user audit created', async ({ page, request }) => {
  const credentials = Buffer.from(`${TEST_USERS.adminEn.email}:${TEST_USERS.adminEn.password}`).toString('base64');
  const probe = await request.post('/api/api-keys', { headers: { Authorization: `Basic ${credentials}` } });
  test.skip(probe.status() === 201, 'administrator already exists');

  await page.goto('/');
  await runSignUpFlow(page, expect, TEST_USERS.adminEn, 'ADMIN', async () => {
    await runVerifySettingsAfterSignupFlow(page, expect, { screenshotName: 'admin-signup-settings', privileged: true });
    await openTimelineTab(page);
    await openTimelineFilter(page);
    await assertActorPickerVisible(page, expect, true);
    await closeTimelineFilter(page);
    await assertFeedHasRow(page, expect, { action: 'created', entityType: 'user', screenshotName: 'admin-signup-timeline' });
    await runNavigateToUsersTabFlow(page, expect);
    await runVerifyUserAuditActivityFlow(page, expect, TEST_USERS.adminEn.email, {
      screenshotName: 'admin-signup-user-audit',
      rows: [{ action: /created/i, version: 'v1', changesIncludes: [TEST_USERS.adminEn.name, TEST_USERS.adminEn.email] }],
    });
  });
});
