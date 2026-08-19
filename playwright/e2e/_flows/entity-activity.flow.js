const { expect } = require('@playwright/test');

// Opens the shared nested history overlay (EntityActivityOverlay) via a domain-specific icon
// button selector (e.g. '.advertisement-history-button'). Returns the .entity-activity-list
// locator scoped inside the nested overlay -- always the same DOM location regardless of domain,
// since the overlay is a single global singleton, not nested inside the domain's own overlay.
async function openEntityActivity(page, openButtonSelector) {
  await page.locator(openButtonSelector).click();
  await page.locator('.entity-activity-overlay.overlay--visible').waitFor({ timeout: 8000 });
  return page.locator('.entity-activity-overlay .entity-activity-list');
}

// Closes the nested history overlay. `via: 'parent'` (default) and `via: 'x'` both go back to the
// immediate opener (the still-open form underneath, never actually closed -- see
// docs/ai/adr-index.md). `via: 'outer'` clicks the first step in the breadcrumb
// chain -- always the domain's list link, regardless of how many steps precede the immediate
// parent -- and exits all the way out. Idempotent -- a no-op if the overlay is already closed, so
// callers don't need to track whether an earlier step already closed it.
async function closeEntityActivity(page, via = 'parent') {
  const overlay = page.locator('.entity-activity-overlay.overlay--visible');
  if (!(await overlay.isVisible().catch(() => false))) return;
  const selector = {
    x: '.entity-activity-close-button',
    parent: '.entity-activity-breadcrumb-parent',
    outer: '.entity-activity-breadcrumb-step:first-child',
  }[via];
  await page.locator(selector).click();
  await overlay.waitFor({ state: 'hidden', timeout: 8000 });
  if (via === 'outer') {
    await page.locator('.base-overlay.overlay--visible').waitFor({ state: 'hidden', timeout: 8000 });
  }
}

// Clicks the nth (default first) restore button inside the open history overlay. Restoring only
// stages the values into the parent form and closes the history overlay by itself -- callers still
// need to click Save afterward, same two-step contract as before the tab-to-overlay redesign.
async function restoreFromEntityActivity(page, index = 0) {
  await page.locator('.entity-activity-overlay .entity-activity-list .entity-activity-restore-btn').nth(index).click();
  await page.locator('.entity-activity-overlay.overlay--visible').waitFor({ state: 'hidden', timeout: 8000 });
}

module.exports = { openEntityActivity, closeEntityActivity, restoreFromEntityActivity };
