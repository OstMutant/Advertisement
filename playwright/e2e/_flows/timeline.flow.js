const { screenshot } = require('../_helpers');
const { openQueryPanel, applyFilter, clearFilter } = require('./filter.flow');

const TIMELINE_BLOCK = '.timeline-query-block';

async function openTimelineTab(page) {
  await page.locator('vaadin-tab').filter({ hasText: /timeline|таймлайн/i }).click();
  await page.locator('.activity-feed').waitFor({ timeout: 8000 });
  await page.locator('.activity-feed .activity-feed-row').first().waitFor({ timeout: 8000 });
}

async function openTimelineFilter(page) {
  await openQueryPanel(page, TIMELINE_BLOCK);
}

async function closeTimelineFilter(page) {
  await page.locator('.query-status-bar:visible').click();
  await page.locator(TIMELINE_BLOCK).waitFor({ state: 'hidden', timeout: 5000 });
}

// Asserts at least one feed row matches the given criteria
async function assertFeedHasRow(page, expect, { action, entityType, editor, screenshotName } = {}) {
  const feed = page.locator('.activity-feed');
  await feed.waitFor({ timeout: 8000 });
  let row = feed.locator('.activity-feed-row');
  if (action)     row = row.filter({ has: page.locator(`.activity-feed-action--${action}`) });
  if (entityType) row = row.filter({ has: page.locator(`.activity-feed-type--${entityType}`) });
  if (editor)     row = row.filter({ hasText: editor });
  await expect(row.first()).toBeVisible({ timeout: 8000 });
  if (screenshotName) await screenshot(page, screenshotName);
}

// Asserts at least minCount feed rows match the given criteria.
// Optional: titleText checks that at least one row's .activity-feed-name contains the text.
//           actorText checks that at least one row's .activity-feed-editor contains the text.
async function assertTimelineHasRows(page, expect, { action, entityType, minCount = 1, titleText, actorText, screenshotName } = {}) {
  const feed = page.locator('.activity-feed');
  await feed.waitFor({ timeout: 8000 });
  let rows = feed.locator('.activity-feed-row');
  if (action)     rows = rows.filter({ has: page.locator(`.activity-feed-action--${action}`) });
  if (entityType) rows = rows.filter({ has: page.locator(`.activity-feed-type--${entityType}`) });
  await expect(rows.first()).toBeVisible({ timeout: 8000 });
  const count = await rows.count();
  expect(count).toBeGreaterThanOrEqual(minCount);
  if (titleText) {
    await expect(
      rows.filter({ has: page.locator('.activity-feed-name', { hasText: titleText }) }).first()
    ).toBeVisible({ timeout: 5000 });
  }
  if (actorText) {
    await expect(
      rows.filter({ has: page.locator('.activity-feed-editor', { hasText: actorText }) }).first()
    ).toBeVisible({ timeout: 5000 });
  }
  if (screenshotName) await screenshot(page, screenshotName);
}

// Asserts all visible feed rows have the given entity type CSS class
async function assertAllRowsHaveType(page, expect, entityType, screenshotName) {
  const rows = page.locator('.activity-feed .activity-feed-row');
  // Wait for the feed to show the filtered results — first row must already have the correct type
  await expect(rows.first().locator(`.activity-feed-type--${entityType}`)).toBeVisible({ timeout: 8000 });
  const count = await rows.count();
  expect(count).toBeGreaterThan(0);
  for (let i = 0; i < count; i++) {
    await expect(rows.nth(i).locator(`.activity-feed-type--${entityType}`)).toBeVisible({ timeout: 3000 });
  }
  if (screenshotName) await screenshot(page, screenshotName);
}

// Asserts all visible feed rows have the given action CSS class
async function assertAllRowsHaveAction(page, expect, action, screenshotName) {
  const rows = page.locator('.activity-feed .activity-feed-row');
  // Wait for filter to take effect — first row must have the correct action class
  await expect(rows.first().locator(`.activity-feed-action--${action}`)).toBeVisible({ timeout: 8000 });
  const count = await rows.count();
  expect(count).toBeGreaterThan(0);
  for (let i = 0; i < count; i++) {
    await expect(rows.nth(i).locator(`.activity-feed-action--${action}`)).toBeVisible({ timeout: 3000 });
  }
  if (screenshotName) await screenshot(page, screenshotName);
}

// Requires filter panel to be open (call openTimelineFilter first)
async function assertActorPickerVisible(page, expect, visible) {
  const actorRow = page.locator(`${TIMELINE_BLOCK} .query-inline-row`)
    .filter({ has: page.locator('.query-inline-label-sort', { hasText: /actor|виконавець/i }) });
  if (visible) {
    await expect(actorRow).toBeVisible({ timeout: 5000 });
  } else {
    await expect(actorRow).not.toBeVisible({ timeout: 5000 });
  }
}

async function fillEntityType(page, value) {
  const combo = page.locator(`${TIMELINE_BLOCK} vaadin-multi-select-combo-box`).nth(0);
  await combo.locator('input').click();
  const overlay = page.locator('vaadin-multi-select-combo-box-overlay[opened]');
  await overlay.waitFor({ state: 'visible', timeout: 8000 });
  // Do NOT type to filter — typing empties the Vaadin overlay. Click the item by text directly.
  await page.locator('vaadin-multi-select-combo-box-item').filter({ hasText: value }).first().waitFor({ timeout: 5000 });
  await page.locator('vaadin-multi-select-combo-box-item').filter({ hasText: value }).first().click();
  await page.keyboard.press('Escape');
  await overlay.waitFor({ state: 'hidden', timeout: 5000 });
}

async function fillActionType(page, value) {
  const combo = page.locator(`${TIMELINE_BLOCK} vaadin-multi-select-combo-box`).nth(1);
  await combo.locator('input').click();
  const overlay = page.locator('vaadin-multi-select-combo-box-overlay[opened]');
  await overlay.waitFor({ state: 'visible', timeout: 8000 });
  await page.locator('vaadin-multi-select-combo-box-item').filter({ hasText: value }).first().waitFor({ timeout: 5000 });
  await page.locator('vaadin-multi-select-combo-box-item').filter({ hasText: value }).first().click();
  await page.keyboard.press('Escape');
  await overlay.waitFor({ state: 'hidden', timeout: 5000 });
}

async function fillActorPicker(page, userName) {
  await page.locator('.user-picker-open').click();
  const dialog = page.locator('vaadin-dialog-overlay[opened]');
  await dialog.waitFor({ state: 'visible', timeout: 5000 });
  const cell = page.locator('vaadin-grid-cell-content').filter({ hasText: new RegExp(`^${userName}$`) });
  await cell.first().waitFor({ timeout: 8000 });
  await cell.first().click();
  await dialog.waitFor({ state: 'hidden', timeout: 5000 });
}

module.exports = {
  openTimelineTab,
  openTimelineFilter,
  closeTimelineFilter,
  assertFeedHasRow,
  assertTimelineHasRows,
  assertAllRowsHaveType,
  assertAllRowsHaveAction,
  assertActorPickerVisible,
  fillEntityType,
  fillActionType,
  fillActorPicker,
  TIMELINE_BLOCK,
};
