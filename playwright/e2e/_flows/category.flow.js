/* ── Header ──────────────────────────────────────────────────────────────────
 * Description: Flow helpers for the Category reference-data domain -- switching to the
 *   Reference Data / Categories tab, creating a category through its overlay form, selecting a
 *   category in the advertisement form's shadow-DOM combo box, and asserting category
 *   names/chips (including deleted/struck-through state) appear on a card, view overlay, or
 *   activity diff.
 * Usage: None -- a library only, required by spec files (see Input).
 * Uses: ../_helpers (screenshot).
 * Env: None.
 * Input: required by 03-marketplace-promotion-flow.spec.js (selectCategoryInAdForm,
 *   assertViewOverlayHasDeletedCategory, assertActivityDiffHasStruckThroughCategory),
 *   04-marketplace-advertisement-flow.spec.js (assertViewOverlayHasCategories),
 *   05-seed-filter-sort-pagination.spec.js (runCreateCategoryFlow); also required internally by
 *   advertisement.flow.js, city.flow.js, delete.flow.js, and seed.flow.js.
 * Outputs: exports openReferenceDataTab, runCreateCategoryFlow, selectCategoryInAdForm,
 *   assertCardHasCategories, assertViewOverlayHasCategories, assertViewOverlayHasDeletedCategory,
 *   assertActivityDiffHasStruckThroughCategory.
 * Returns: N/A
 * ──────────────────────────────────────────────────────────────────────────── */
const { screenshot } = require('../_helpers');

/**
 * Navigates to the Reference Data tab, then (re-)selects the Categories sub-tab.
 * @param {import('@playwright/test').Page} page
 * @returns {Promise<void>}
 */
async function openReferenceDataTab(page) {
  await page.locator('.main-tabs vaadin-tab').filter({ hasText: /Reference Data|Довідникові дані/i }).click();
  // Sub-tabs retain their last selection across visibility toggles — reselect Categories explicitly.
  await page.locator('.reference-data-sub-tabs vaadin-tab').filter({ hasText: /Categories|Категорії/i }).click();
  await page.locator('.taxon-management-view').waitFor({ timeout: 5000 });
}

/**
 * Creates a category with EN/UK name and description through the taxon overlay form, then
 * verifies it appears in the category management list.
 * @param {import('@playwright/test').Page} page
 * @param {import('@playwright/test').Expect} expect
 * @param {object} params
 * @param {string} params.nameEn
 * @param {string} params.descriptionEn
 * @param {string} params.nameUk
 * @param {string} params.descriptionUk
 * @param {string} [params.screenshotPrefix] when set, screenshots are taken before/after save.
 * @returns {Promise<void>}
 */
async function runCreateCategoryFlow(page, expect, { nameEn, descriptionEn, nameUk, descriptionUk, screenshotPrefix }) {
  await openReferenceDataTab(page);
  await page.locator('.taxon-add-button').click();
  const overlay = page.locator('.taxon-overlay');
  await overlay.waitFor({ timeout: 5000 });

  const localeContents = overlay.locator('.taxon-locale-content');
  await localeContents.nth(0).locator('vaadin-text-field input').fill(nameEn);
  await localeContents.nth(0).locator('vaadin-text-area textarea').fill(descriptionEn);
  await localeContents.nth(1).locator('vaadin-text-field input').fill(nameUk);
  await localeContents.nth(1).locator('vaadin-text-area textarea').fill(descriptionUk);

  if (screenshotPrefix) await screenshot(page, `${screenshotPrefix}-form`);

  await overlay.locator('vaadin-button').filter({ hasText: /зберегти|save/i }).click();
  await expect(page.locator('vaadin-notification-card')).toBeVisible({ timeout: 5000 });
  await page.locator('vaadin-notification-card vaadin-button').click();
  await overlay.locator('vaadin-button')
    .filter({ has: page.locator('vaadin-icon[icon="vaadin:close"]') })
    .click();
  await overlay.waitFor({ state: 'hidden', timeout: 8000 });
  await expect(page.locator('.taxon-row-name', { hasText: nameEn })).toBeVisible({ timeout: 5000 });

  if (screenshotPrefix) await screenshot(page, `${screenshotPrefix}-created`);
}

// Waits for Vaadin server round-trips to settle (same pattern as waitForVaadin in filter.flow.js).
async function waitForVaadinIdle(page) {
  await page.evaluate(() => new Promise(r => setTimeout(r, 0)));
  await page.waitForFunction(() => {
    if (window.Vaadin?.Flow?.clients) {
      const clients = Object.values(window.Vaadin.Flow.clients);
      if (clients.some(c => typeof c.isActive === 'function' && c.isActive())) return false;
    }
    return true;
  }, { timeout: 8000 });
}

/**
 * Selects a category in the advertisement form's multi-select combo box, piercing shadow DOM and
 * scrolling the virtual list as needed to find the item by its exact label.
 * @param {import('@playwright/test').Page} page
 * @param {import('@playwright/test').Locator} overlay the advertisement overlay.
 * @param {string} categoryName exact category label to select.
 * @returns {Promise<void>}
 */
async function selectCategoryInAdForm(page, overlay, categoryName) {
  const comboBox = overlay.locator('[data-testid="advertisement-overlay-field-categories"]');
  // Click input to open the dropdown — blurs title/description so Vaadin syncs their values
  // to the server before we interact with the combo box.
  await comboBox.locator('input').click();
  await waitForVaadinIdle(page);
  await page.locator('vaadin-multi-select-combo-box-overlay').first().waitFor({ state: 'visible', timeout: 5000 });
  // The combo-box-overlay is a child overlay appended inside the parent advertisement overlay's
  // shadow root, not in document.body — document.querySelector does not pierce shadow DOM.
  // We traverse all shadow roots to find it, then click the item by its label property.
  // Avoids keyboard typing — per-character input events trigger binder validation that can
  // mark the Set<Long> field invalid while the text "CategoryName" is still in the input.
  // For large lists Vaadin's virtual scroller only renders visible items, so we scroll the
  // dropdown to bring the target item into the DOM before searching.
  const result = await page.evaluate(async (name) => {
    function shadowFind(root, selector) {
      const el = root.querySelector(selector);
      if (el) return el;
      for (const child of root.querySelectorAll('*')) {
        if (child.shadowRoot) {
          const found = shadowFind(child.shadowRoot, selector);
          if (found) return found;
        }
      }
      return null;
    }
    function findItem(root, label) {
      if (!root) return null;
      for (const tag of ['vaadin-multi-select-combo-box-item', 'vaadin-combo-box-item']) {
        for (const el of root.querySelectorAll(tag)) {
          if (el.label === label) return el;
        }
      }
      for (const child of root.querySelectorAll('*')) {
        if (child.shadowRoot) {
          const found = findItem(child.shadowRoot, label);
          if (found) return found;
        }
      }
      return null;
    }
    // Find ALL overlay instances (there may be multiple — pick the opened/visible one).
    function shadowFindAll(root, selector, acc = []) {
      acc.push(...root.querySelectorAll(selector));
      for (const child of root.querySelectorAll('*')) {
        if (child.shadowRoot) shadowFindAll(child.shadowRoot, selector, acc);
      }
      return acc;
    }
    const allOverlays = shadowFindAll(document, 'vaadin-multi-select-combo-box-overlay');
    const overlayEl = allOverlays.find(o => o.hasAttribute('opened') || o.getBoundingClientRect().height > 0) || allOverlays[0];
    if (!overlayEl) return { found: false, debug: `overlay not found; total=${allOverlays.length}` };

    // Items may be in shadow root, light DOM, or in slot.assignedElements().
    const sr = overlayEl.shadowRoot;
    const slot = sr?.querySelector('slot');
    const assigned = slot ? [...slot.assignedElements({ flatten: true })] : [];
    const allRoots = [sr, overlayEl, ...assigned.map(e => e.shadowRoot).filter(Boolean), ...assigned];

    // First attempt — item may already be visible in the current viewport.
    let item = null;
    for (const root of allRoots) {
      item = findItem(root, name);
      if (item) break;
    }

    // If not found, scroll the virtual scroller to bring the item into the DOM.
    if (!item) {
      const scroller = assigned.find(e => e.tagName?.toLowerCase().includes('scroller'));
      if (scroller) {
        const itemHeight = 36;
        const totalCount = scroller.items?.length ?? 50;
        for (let step = 1; step <= totalCount && !item; step++) {
          scroller.scrollTop = step * itemHeight;
          await new Promise(r => requestAnimationFrame(r));
          for (const root of allRoots) {
            item = findItem(root, name);
            if (item) break;
          }
        }
      }
    }

    if (!item) {
      return { found: false, debug: JSON.stringify({
        overlayCount: allOverlays.length,
        opened: overlayEl.hasAttribute('opened'),
        srTags: sr ? [...sr.querySelectorAll('*')].map(c => c.tagName).slice(0, 30) : [],
        assignedTags: assigned.map(e => e.tagName),
        assignedLabels: assigned.map(e => e.label || e.textContent?.trim().slice(0, 20)),
      }) };
    }
    item.click();
    return { found: true };
  }, categoryName);

  if (!result.found) throw new Error(`Category "${categoryName}" not found. DOM: ${result.debug}`);
  await waitForVaadinIdle(page);
  await page.keyboard.press('Escape');
  await page.locator('vaadin-multi-select-combo-box-overlay').first().waitFor({ state: 'hidden', timeout: 5000 });
  await waitForVaadinIdle(page);
}

/**
 * Asserts an advertisement card's categories line contains every expected category name.
 * @param {import('@playwright/test').Page} page
 * @param {import('@playwright/test').Expect} expect
 * @param {import('@playwright/test').Locator} card
 * @param {string[]} categoryNames
 * @param {string} [screenshotName] when set, takes a screenshot after the assertion passes.
 * @returns {Promise<void>}
 */
async function assertCardHasCategories(page, expect, card, categoryNames, screenshotName) {
  const line = card.locator('.advertisement-categories');
  await expect(line).toBeVisible({ timeout: 5000 });
  for (const name of categoryNames) {
    await expect(line).toContainText(name, { timeout: 5000 });
  }
  if (screenshotName) await screenshot(page, screenshotName);
}

/**
 * Asserts the advertisement view overlay shows exactly the expected set of category chips.
 * @param {import('@playwright/test').Page} page
 * @param {import('@playwright/test').Expect} expect
 * @param {import('@playwright/test').Locator} overlay
 * @param {string[]} categoryNames
 * @param {string} [screenshotName] when set, takes a screenshot after the assertion passes.
 * @returns {Promise<void>}
 */
async function assertViewOverlayHasCategories(page, expect, overlay, categoryNames, screenshotName) {
  const chips = overlay.locator('.advertisement-category-chip');
  await expect(chips).toHaveCount(categoryNames.length, { timeout: 5000 });
  for (const name of categoryNames) {
    await expect(overlay.locator('.advertisement-category-chip', { hasText: name })).toBeVisible({ timeout: 5000 });
  }
  if (screenshotName) await screenshot(page, screenshotName);
}

/**
 * Asserts the advertisement view overlay's category chip for a soft-deleted category is visible
 * and carries the deleted (struck-through) styling class.
 * @param {import('@playwright/test').Page} page
 * @param {import('@playwright/test').Expect} expect
 * @param {import('@playwright/test').Locator} overlay
 * @param {string} categoryName
 * @param {string} [screenshotName] when set, takes a screenshot after the assertion passes.
 * @returns {Promise<void>}
 */
async function assertViewOverlayHasDeletedCategory(page, expect, overlay, categoryName, screenshotName) {
  const chip = overlay.locator('.advertisement-category-chip', { hasText: categoryName });
  await expect(chip).toBeVisible({ timeout: 5000 });
  await expect(chip).toHaveClass(/advertisement-category-chip--deleted/);
  if (screenshotName) await screenshot(page, screenshotName);
}

/**
 * Asserts an activity diff's changes element shows the category name inside a struck-through
 * (`<s>`) element.
 * @param {import('@playwright/test').Page} page
 * @param {import('@playwright/test').Expect} expect
 * @param {import('@playwright/test').Locator} changes the `.entity-activity-changes` locator.
 * @param {string} categoryName
 * @param {string} [screenshotName] when set, takes a screenshot after the assertion passes.
 * @returns {Promise<void>}
 */
async function assertActivityDiffHasStruckThroughCategory(page, expect, changes, categoryName, screenshotName) {
  await expect(changes.locator('s', { hasText: categoryName })).toBeVisible({ timeout: 5000 });
  if (screenshotName) await screenshot(page, screenshotName);
}

module.exports = {
  openReferenceDataTab,
  runCreateCategoryFlow,
  selectCategoryInAdForm,
  assertCardHasCategories,
  assertViewOverlayHasCategories,
  assertViewOverlayHasDeletedCategory,
  assertActivityDiffHasStruckThroughCategory,
};
