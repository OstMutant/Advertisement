const { screenshot } = require('../_helpers');

async function openReferenceDataTab(page) {
  await page.locator('.main-tabs vaadin-tab').filter({ hasText: /Reference Data|Довідникові дані/i }).click();
  await page.locator('.taxon-management-view').waitFor({ timeout: 5000 });
}

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

async function deselectCategoryInAdForm(page, overlay, categoryName) {
  const comboBox = overlay.locator('[data-testid="advertisement-overlay-field-categories"]');
  await comboBox.locator('input').click();
  await waitForVaadinIdle(page);
  await page.locator('vaadin-multi-select-combo-box-overlay').first().waitFor({ state: 'visible', timeout: 5000 });
  const result = await page.evaluate((name) => {
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
    const overlayEl = shadowFind(document, 'vaadin-multi-select-combo-box-overlay');
    if (!overlayEl) return { found: false, debug: 'overlay not found' };
    const item = findItem(overlayEl.shadowRoot, name) || findItem(overlayEl, name);
    if (!item) return { found: false, debug: 'item not found' };
    item.click();
    return { found: true };
  }, categoryName);

  if (!result.found) throw new Error(`Category "${categoryName}" not found in overlay`);
  await waitForVaadinIdle(page);
  await page.keyboard.press('Escape');
  await page.locator('vaadin-multi-select-combo-box-overlay').first().waitFor({ state: 'hidden', timeout: 5000 });
  await waitForVaadinIdle(page);
}

async function assertCardHasCategories(page, expect, card, categoryNames, screenshotName) {
  const line = card.locator('.advertisement-categories');
  await expect(line).toBeVisible({ timeout: 5000 });
  for (const name of categoryNames) {
    await expect(line).toContainText(name, { timeout: 5000 });
  }
  if (screenshotName) await screenshot(page, screenshotName);
}

async function assertViewOverlayHasCategories(page, expect, overlay, categoryNames, screenshotName) {
  const chips = overlay.locator('.advertisement-category-chip');
  await expect(chips).toHaveCount(categoryNames.length, { timeout: 5000 });
  for (const name of categoryNames) {
    await expect(overlay.locator('.advertisement-category-chip', { hasText: name })).toBeVisible({ timeout: 5000 });
  }
  if (screenshotName) await screenshot(page, screenshotName);
}

module.exports = {
  openReferenceDataTab,
  runCreateCategoryFlow,
  selectCategoryInAdForm,
  deselectCategoryInAdForm,
  assertCardHasCategories,
  assertViewOverlayHasCategories,
};
