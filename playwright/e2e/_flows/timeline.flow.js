/* ── Header ──────────────────────────────────────────────────────────────────
 * Description: Shared flow helpers for the Timeline tab -- opening the tab and its filter panel,
 *   asserting activity-feed rows (single-row presence, multi-row counts with title/actor/changes
 *   text checks, uniform entity-type/action across all visible rows), the actor-picker visibility
 *   toggle, and filling the entity-type/action-type/actor-picker filter controls (including chip
 *   removal and chip-count reads).
 * Usage: None -- a library only, required by spec files (see Input).
 * Uses: ./_helpers (screenshot, assertRightAligned), ./filter.flow (openQueryPanel, waitForVaadin).
 * Env: None.
 * Input: required by 02-marketplace-authentication-flow.spec.js, 03-marketplace-promotion-flow.spec.js,
 *   05-marketplace-advertisement-flow.spec.js, 06-seed-filter-sort-pagination.spec.js.
 * Outputs: exports openTimelineTab, openTimelineFilter, closeTimelineFilter, assertFeedHasRow,
 *   assertTimelineHasRows, assertAllRowsHaveType, assertAllRowsHaveAction, assertActorPickerVisible,
 *   fillEntityType, fillActionType, fillActorPicker, removeActorChip, actorChipCount, TIMELINE_BLOCK.
 * Returns: N/A
 * ──────────────────────────────────────────────────────────────────────────── */
const { screenshot, assertRightAligned } = require('../_helpers');
const { openQueryPanel, applyFilter, clearFilter, waitForVaadin } = require('./filter.flow');

const TIMELINE_BLOCK = '.timeline-query-block';

/**
 * Opens the Timeline tab and waits for the activity feed to render its first row.
 * Arguments: page.
 * Outputs: none (waits only).
 * Returns: Promise<void>.
 */
async function openTimelineTab(page) {
  await page.locator('vaadin-tab').filter({ hasText: /timeline|таймлайн/i }).click();
  await page.locator('.activity-feed').waitFor({ timeout: 8000 });
  await page.locator('.activity-feed .activity-feed-row').first().waitFor({ timeout: 8000 });
}

/**
 * Opens the Timeline tab's own filter panel.
 * Arguments: page.
 * Outputs: none.
 * Returns: Promise<void>.
 */
async function openTimelineFilter(page) {
  await openQueryPanel(page, TIMELINE_BLOCK);
}

/**
 * Closes the Timeline filter panel by clicking the visible query-status-bar.
 * Arguments: page.
 * Outputs: none.
 * Returns: Promise<void>.
 */
async function closeTimelineFilter(page) {
  await page.locator('.query-status-bar:visible').click();
  await page.locator(TIMELINE_BLOCK).waitFor({ state: 'hidden', timeout: 5000 });
}

/**
 * Asserts at least one feed row matches the given criteria (action/entityType/editor text).
 * Arguments: page, expect, { action, entityType, editor, screenshotName }.
 * Outputs: optional screenshot if screenshotName is given.
 * Returns: Promise<void>.
 */
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

/**
 * Asserts at least minCount feed rows match the given criteria (titleText/allFieldsText check the
 * row body; actorText also verifies the editor-badge/timestamp layout is right-aligned).
 * Arguments: page, expect, { action, entityType, minCount, titleText, allFieldsText, actorText,
 *   changesText, screenshotName }.
 * Outputs: optional screenshot if screenshotName is given.
 * Returns: Promise<void>.
 */
async function assertTimelineHasRows(page, expect, { action, entityType, minCount = 1, titleText, allFieldsText, actorText, changesText, screenshotName } = {}) {
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
      rows.filter({ has: page.locator('.activity-feed-changes', { hasText: titleText }) }).first()
    ).toBeVisible({ timeout: 5000 });
  }
  if (allFieldsText) {
    const row = rows.filter({ has: page.locator('.activity-feed-changes', { hasText: allFieldsText[0] }) }).first();
    await expect(row).toBeVisible({ timeout: 5000 });
    const changes = row.locator('.activity-feed-changes');
    for (const text of allFieldsText) {
      await expect(changes, `expected the full field dump to contain "${text}"`).toContainText(text);
    }
  }
  if (actorText) {
    const row = rows.filter({ has: page.locator('.activity-feed-editor', { hasText: actorText }) }).first();
    await expect(row).toBeVisible({ timeout: 5000 });
    // Editor badge + timestamp are wrapped together and pushed flush right as one group.
    // 12px tolerance covers .activity-feed-row's own 10px right padding.
    await assertRightAligned(expect, row.locator('.activity-feed-right-group'), row, 12);
    const editorBox = await row.locator('.activity-feed-editor').boundingBox();
    const timeBox = await row.locator('.activity-feed-time').boundingBox();
    expect(editorBox.x, 'editor badge must sit to the left of the timestamp').toBeLessThan(timeBox.x);
    expect(timeBox.x - (editorBox.x + editorBox.width), 'editor and timestamp must be adjacent, not far apart').toBeLessThan(20);
  }
  if (changesText) {
    await expect(
      rows.filter({ has: page.locator('.activity-feed-changes', { hasText: changesText }) }).first()
    ).toBeVisible({ timeout: 5000 });
  }
  if (screenshotName) await screenshot(page, screenshotName);
}

/**
 * Asserts all visible feed rows have the given entity-type CSS class.
 * Arguments: page, expect, entityType, screenshotName.
 * Outputs: optional screenshot if screenshotName is given.
 * Returns: Promise<void>.
 */
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

/**
 * Asserts all visible feed rows have the given action CSS class.
 * Arguments: page, expect, action, screenshotName.
 * Outputs: optional screenshot if screenshotName is given.
 * Returns: Promise<void>.
 */
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

/**
 * Asserts the actor-picker row's visibility in the filter panel. Requires the filter panel to
 * already be open (call openTimelineFilter first).
 * Arguments: page, expect, visible.
 * Outputs: none.
 * Returns: Promise<void>.
 */
async function assertActorPickerVisible(page, expect, visible) {
  const actorRow = page.locator(`${TIMELINE_BLOCK} .query-inline-row`)
    .filter({ has: page.locator('.query-inline-label-sort', { hasText: /actor|виконавець/i }) });
  if (visible) {
    await expect(actorRow).toBeVisible({ timeout: 5000 });
  } else {
    await expect(actorRow).not.toBeVisible({ timeout: 5000 });
  }
}

/**
 * Selects one value from the Timeline filter's entity-type multi-select combo.
 * Arguments: page, value.
 * Outputs: none.
 * Returns: Promise<void>.
 */
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

/**
 * Selects one value from the Timeline filter's action-type multi-select combo.
 * Arguments: page, value.
 * Outputs: none.
 * Returns: Promise<void>.
 */
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

/**
 * Opens the actor picker dialog and selects the given user by name, either by scrolling the
 * virtualized grid to find the row (default) or via the dialog's own server-side search field.
 * Arguments: page, userName, { useSearch }.
 * Outputs: none.
 * Returns: Promise<void>.
 */
async function fillActorPicker(page, userName, { useSearch = false } = {}) {
  await page.locator('.user-picker-open').click();
  const dialog = page.locator('vaadin-dialog-overlay[opened]');
  await dialog.waitFor({ state: 'visible', timeout: 5000 });
  const cell = page.locator('vaadin-grid-cell-content').filter({ hasText: new RegExp(`^${userName}$`) });

  if (useSearch) {
    // Server-side filter via the dialog's own search field narrows the grid to a handful of
    // rows, all rendered without scrolling — needed when the target name isn't guaranteed to be
    // alone in the virtualized grid (e.g. two boundary-test users sharing an identical 100-char
    // name), where a row that's only partially scrolled into view can still report as "visible"
    // to Playwright's actionability check while clipped by the grid's own overflow, silently
    // missing the click. Selected globally rather than scoped through `dialog` — same reasoning
    // as the grid below: Vaadin renders dialog content outside the overlay element's own subtree,
    // so a `dialog.locator(...)`-scoped search times out even though the field is on screen.
    // Only one `vaadin-text-field` exists on screen while the picker dialog is open (the rest of
    // the query panel behind it uses combo-boxes/date fields, not a plain text field).
    await page.locator('vaadin-text-field input').fill(userName);
    await page.locator('vaadin-text-field vaadin-button').click();
    // The search is a server-side filter round-trip -- without this, the cell lookup below can
    // grab a pre-filter row that gets detached mid-action once the filtered grid re-renders.
    await waitForVaadin(page);
  } else {
    // The grid virtualizes rows and lazy-loads beyond its first data-provider page (Vaadin's
    // default page size is 50) — a target further down the name-sorted list won't be in the DOM
    // yet. Scroll the grid's virtual scroller in increasing steps until the row appears or a
    // reasonable number of attempts is exhausted. Each scroll step waits on the grid's own
    // rendering completion (a frame + microtask flush) rather than a fixed timer.
    // Only one grid exists on screen while the picker dialog is open — select it directly rather
    // than scoping through `vaadin-dialog-overlay[opened]`: the grid is not a CSS descendant of
    // the overlay element itself (Vaadin renders dialog content outside that direct subtree),
    // confirmed by a scoped-vs-global locator count check.
    const grid = page.locator('vaadin-grid');
    for (let index = 10; index <= 200 && (await cell.count()) === 0; index += 10) {
      await grid.evaluate((el, idx) => {
        el.scrollToIndex(idx);
        return new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)));
      }, index);
    }
  }

  await cell.first().waitFor({ timeout: 8000 });
  await cell.first().scrollIntoViewIfNeeded();
  await cell.first().click();
  await dialog.waitFor({ state: 'hidden', timeout: 5000 });
}

/**
 * Removes one selected-actor chip by user name and waits for the server round-trip to settle.
 * Arguments: page, userName.
 * Outputs: none.
 * Returns: Promise<void>.
 */
async function removeActorChip(page, userName) {
  const chip = page.locator('.user-picker-chip').filter({ hasText: userName });
  await chip.locator('.user-picker-chip-remove').click();
  // Removal is a server round-trip (CustomField.setModelValue -> re-render), same as any other
  // Vaadin button click -- without this the immediately-following chip-count read can observe
  // stale DOM, unlike fillActorPicker's selection which already waits on the dialog closing.
  await waitForVaadin(page);
}

/**
 * Returns the current count of selected-actor chips.
 * Arguments: page.
 * Outputs: none.
 * Returns: Promise<number>.
 */
function actorChipCount(page) {
  return page.locator('.user-picker-chip').count();
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
  removeActorChip,
  actorChipCount,
  TIMELINE_BLOCK,
};
