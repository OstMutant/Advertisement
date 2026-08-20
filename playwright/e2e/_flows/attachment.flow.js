/* ── Header ──────────────────────────────────────────────────────────────────
 * Description: Shadow-DOM-piercing helpers for the card lightbox (vaadin-dialog.card-lightbox) --
 *   waiting for it to open/close, reading the current iframe/video src, clicking a thumbnail by
 *   index, and checking whether the video/image element is currently visible. Every lookup is
 *   rooted at the currently *opened* dialog instance, not `document`, since closed lightboxes stay
 *   in the DOM (hidden, not removed).
 * Usage: None -- a library only, required by spec files (see Input).
 * Uses: None (plain page.evaluate/waitForFunction DOM traversal).
 * Env: None.
 * Input: required directly by 04-marketplace-advertisement-flow.spec.js, and by
 *   advertisement.flow.js (clickLightboxThumb, getVideoSrc, waitForVideoWrapperVisible), which is
 *   itself required by that same spec file.
 * Outputs: exports waitForLightboxOpen, waitForLightboxClosed, getIframeSrc, clickLightboxThumb,
 *   getVideoSrc, isVideoWrapperVisible, waitForVideoWrapperVisible, waitForMainImageVisible.
 * Returns: N/A
 * ──────────────────────────────────────────────────────────────────────────── */

function shadowFindAll(root, selector) {
  const els = [...root.querySelectorAll(selector)];
  for (const child of root.querySelectorAll('*'))
    if (child.shadowRoot) els.push(...shadowFindAll(child.shadowRoot, selector));
  return els;
}

function shadowFind(root, selector) {
  const el = root.querySelector(selector);
  if (el) return el;
  for (const child of root.querySelectorAll('*'))
    if (child.shadowRoot) { const found = shadowFind(child.shadowRoot, selector); if (found) return found; }
  return null;
}

/**
 * Waits until the card lightbox dialog is opened.
 * @param {import('@playwright/test').Page} page
 * @returns {Promise<void>}
 */
async function waitForLightboxOpen(page) {
  await page.waitForFunction(
    () => !!document.querySelector('vaadin-dialog.card-lightbox[opened]'),
    { timeout: 8000 }
  );
}

/**
 * Waits until the card lightbox dialog is closed. Never throws if the wait times out.
 * @param {import('@playwright/test').Page} page
 * @returns {Promise<void>}
 */
async function waitForLightboxClosed(page) {
  await page.waitForFunction(
    () => !document.querySelector('vaadin-dialog.card-lightbox[opened]'),
    { timeout: 8000 }
  ).catch(() => {});
}

// Every lookup below is rooted at the currently *opened* dialog, not `document` as a whole --
// Vaadin dialogs don't remove their content from the DOM on close, only hide it, so after several
// lightboxes have been opened and closed earlier in a test run, more than one `.card-lightbox__*`
// element can exist at once. An unscoped `document.querySelector` (or a recursive shadow-DOM
// search rooted at `document`) picks whichever one happens to be first in document order, which
// is not necessarily the currently open instance -- confirmed directly: this broke the "YouTube
// -> image" lightbox test once CardLightboxViewer.update() stopped taking the same shortcut
// (see docs/ai/adr-index.md for the CardLightboxViewer fix).

/**
 * Reads the `src` of the currently-open lightbox's iframe (e.g. an embedded YouTube video).
 * @param {import('@playwright/test').Page} page
 * @returns {Promise<string|undefined>} the iframe src, or undefined if not found.
 */
async function getIframeSrc(page) {
  return page.evaluate((sel) => {
    function search(root) {
      const el = root.querySelector(sel);
      if (el) return el.src;
      for (const c of root.querySelectorAll('*'))
        if (c.shadowRoot) { const r = search(c.shadowRoot); if (r !== undefined) return r; }
      return undefined;
    }
    const dialog = document.querySelector('vaadin-dialog.card-lightbox[opened]');
    return dialog ? search(dialog) : undefined;
  }, '.card-lightbox__iframe');
}

/**
 * Clicks the thumbnail at the given index in the currently-open lightbox's thumbnail strip.
 * @param {import('@playwright/test').Page} page
 * @param {number} index
 * @returns {Promise<void>}
 */
async function clickLightboxThumb(page, index) {
  await page.evaluate((idx) => {
    function findAll(root, sel) {
      const els = [...root.querySelectorAll(sel)];
      for (const c of root.querySelectorAll('*'))
        if (c.shadowRoot) els.push(...findAll(c.shadowRoot, sel));
      return els;
    }
    const dialog = document.querySelector('vaadin-dialog.card-lightbox[opened]');
    const thumbs = dialog ? findAll(dialog, '.card-lightbox__strip .card-lightbox__thumb') : [];
    if (thumbs[idx]) thumbs[idx].click();
  }, index);
}

/**
 * Reads the `src` of the currently-open lightbox's main video element.
 * @param {import('@playwright/test').Page} page
 * @returns {Promise<string|undefined>} the video src, or undefined if not found.
 */
async function getVideoSrc(page) {
  return page.evaluate(() => {
    function search(root) {
      const v = root.querySelector('.card-lightbox__main-video');
      if (v) return v.getAttribute('src') ?? v.src ?? '';
      for (const c of root.querySelectorAll('*'))
        if (c.shadowRoot) { const r = search(c.shadowRoot); if (r !== undefined) return r; }
      return undefined;
    }
    const dialog = document.querySelector('vaadin-dialog.card-lightbox[opened]');
    return dialog ? search(dialog) : undefined;
  });
}

/**
 * Checks whether the currently-open lightbox's main video wrapper is currently visible.
 * @param {import('@playwright/test').Page} page
 * @returns {Promise<boolean|undefined>} true/false if the wrapper is found, undefined otherwise.
 */
async function isVideoWrapperVisible(page) {
  return page.evaluate(() => {
    function search(root) {
      const w = root.querySelector('.card-lightbox__main-video-wrapper');
      if (w) return !w.hasAttribute('hidden');
      for (const c of root.querySelectorAll('*'))
        if (c.shadowRoot) { const r = search(c.shadowRoot); if (r !== undefined) return r; }
      return undefined;
    }
    const dialog = document.querySelector('vaadin-dialog.card-lightbox[opened]');
    return dialog ? search(dialog) : undefined;
  });
}

/**
 * Waits until the currently-open lightbox's main video wrapper becomes visible.
 * @param {import('@playwright/test').Page} page
 * @returns {Promise<void>}
 */
async function waitForVideoWrapperVisible(page) {
  await page.waitForFunction(() => {
    function search(root) {
      const w = root.querySelector('.card-lightbox__main-video-wrapper');
      if (w) return !w.hasAttribute('hidden');
      for (const c of root.querySelectorAll('*'))
        if (c.shadowRoot && search(c.shadowRoot)) return true;
      return false;
    }
    const dialog = document.querySelector('vaadin-dialog.card-lightbox[opened]');
    return dialog ? search(dialog) : false;
  }, { timeout: 8000 });
}

/**
 * Waits until the currently-open lightbox's main image element becomes visible.
 * @param {import('@playwright/test').Page} page
 * @returns {Promise<void>}
 */
async function waitForMainImageVisible(page) {
  await page.waitForFunction(() => {
    function search(root) {
      const img = root.querySelector('.card-lightbox__main-image');
      if (img && !img.hasAttribute('hidden')) return true;
      for (const c of root.querySelectorAll('*'))
        if (c.shadowRoot && search(c.shadowRoot)) return true;
      return false;
    }
    const dialog = document.querySelector('vaadin-dialog.card-lightbox[opened]');
    return dialog ? search(dialog) : false;
  }, { timeout: 5000 });
}

module.exports = {
  waitForLightboxOpen, waitForLightboxClosed,
  getIframeSrc, clickLightboxThumb,
  getVideoSrc, isVideoWrapperVisible,
  waitForVideoWrapperVisible, waitForMainImageVisible,
};
