// Shadow DOM helpers for card lightbox and attachment lightbox interactions.

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

async function waitForLightboxOpen(page) {
  await page.waitForFunction(
    () => !!document.querySelector('vaadin-dialog.card-lightbox[opened]'),
    { timeout: 8000 }
  );
}

async function waitForLightboxClosed(page) {
  await page.waitForFunction(
    () => !document.querySelector('vaadin-dialog.card-lightbox[opened]'),
    { timeout: 8000 }
  ).catch(() => {});
}

async function getIframeSrc(page) {
  return page.evaluate((sel) => {
    function search(root) {
      const el = root.querySelector(sel);
      if (el) return el.src;
      for (const c of root.querySelectorAll('*'))
        if (c.shadowRoot) { const r = search(c.shadowRoot); if (r !== undefined) return r; }
      return undefined;
    }
    return search(document);
  }, '.card-lightbox__iframe');
}

async function clickLightboxThumb(page, index) {
  await page.evaluate((idx) => {
    function findAll(root, sel) {
      const els = [...root.querySelectorAll(sel)];
      for (const c of root.querySelectorAll('*'))
        if (c.shadowRoot) els.push(...findAll(c.shadowRoot, sel));
      return els;
    }
    const thumbs = findAll(document, '.card-lightbox__strip .card-lightbox__thumb');
    if (thumbs[idx]) thumbs[idx].click();
  }, index);
}

async function getVideoSrc(page) {
  return page.evaluate(() => {
    function search(root) {
      const v = root.querySelector('.card-lightbox__main-video');
      if (v) return v.getAttribute('src') ?? v.src ?? '';
      for (const c of root.querySelectorAll('*'))
        if (c.shadowRoot) { const r = search(c.shadowRoot); if (r !== undefined) return r; }
      return undefined;
    }
    return search(document);
  });
}

async function isVideoWrapperVisible(page) {
  return page.evaluate(() => {
    function search(root) {
      const w = root.querySelector('.card-lightbox__main-video-wrapper');
      if (w) return !w.hasAttribute('hidden');
      for (const c of root.querySelectorAll('*'))
        if (c.shadowRoot) { const r = search(c.shadowRoot); if (r !== undefined) return r; }
      return undefined;
    }
    return search(document);
  });
}

async function waitForVideoWrapperVisible(page) {
  await page.waitForFunction(() => {
    function search(root) {
      const w = root.querySelector('.card-lightbox__main-video-wrapper');
      if (w) return !w.hasAttribute('hidden');
      for (const c of root.querySelectorAll('*'))
        if (c.shadowRoot && search(c.shadowRoot)) return true;
      return false;
    }
    return search(document);
  }, { timeout: 8000 });
}

async function waitForMainImageVisible(page) {
  await page.waitForFunction(() => {
    function search(root) {
      const img = root.querySelector('.card-lightbox__main-image');
      if (img && !img.hasAttribute('hidden')) return true;
      for (const c of root.querySelectorAll('*'))
        if (c.shadowRoot && search(c.shadowRoot)) return true;
      return false;
    }
    return search(document);
  }, { timeout: 5000 });
}

module.exports = {
  waitForLightboxOpen, waitForLightboxClosed,
  getIframeSrc, clickLightboxThumb,
  getVideoSrc, isVideoWrapperVisible,
  waitForVideoWrapperVisible, waitForMainImageVisible,
};
