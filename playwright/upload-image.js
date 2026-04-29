const { check, screenshot, withBrowser, waitForOverlay, waitForOverlayClosed, downloadPng, createAd } = require('./_common');
const fs = require('fs');

const DICEBEAR = seed =>
  `https://api.dicebear.com/9.x/adventurer/png?seed=${seed}&size=256&backgroundColor=b6e3f4,c0aede,d1d4f9,ffd5dc,ffdfbf`;

const ADS = [
  { title: 'Red Sunset Villa',      desc: 'Cozy villa with stunning red sunset views',   seed: 'felix'  },
  { title: 'Green Valley Cabin',    desc: 'Peaceful cabin surrounded by green forest',   seed: 'luna'   },
  { title: 'Blue Ocean Apartment',  desc: 'Modern apartment with blue ocean panorama',   seed: 'oliver' },
  { title: 'Orange Desert House',   desc: 'Warm desert house with orange dunes outside', seed: 'mia'    },
  { title: 'Purple Mountain Lodge', desc: 'Elegant lodge high in the purple mountains',  seed: 'jasper' },
  { title: 'Teal Lake Cottage',     desc: 'Charming cottage by the teal mountain lake',  seed: 'aurora' },
];

withBrowser(async (page) => {
  await check('Create 6 cartoon property ads', async () => {
    for (const ad of ADS) {
      await createAd(page, { title: ad.title, description: ad.desc });
    }
    console.log('      6 ads created');
  });
  await screenshot(page, 'upload-01-ads-created');

  console.log('Downloading cartoon avatars and uploading...');
  for (const ad of ADS) {
    const imgPath = `/tmp/cartoon-${ad.seed}.png`;

    await check(`Upload cartoon to "${ad.title}"`, async () => {
      await downloadPng(DICEBEAR(ad.seed), imgPath);
      console.log(`      downloaded avatar for ${ad.seed}`);

      await page.locator('.advertisement-card')
        .filter({ has: page.locator('.advertisement-title', { hasText: ad.title }) })
        .first().click();
      await waitForOverlay(page);
      await page.locator('vaadin-button').filter({ hasText: /редагувати|edit/i }).first().click();
      await page.waitForSelector('.base-overlay.overlay--visible vaadin-text-field input', { timeout: 5000 });
      await page.locator('vaadin-upload input[type="file"]').setInputFiles(imgPath);
      await page.waitForSelector('.attachment-gallery__item', { timeout: 10000 });
      await page.locator('.base-overlay.overlay--visible vaadin-button')
        .filter({ hasText: /зберегти|save/i }).click();
      await page.waitForSelector('.overlay__view-title', { timeout: 5000 });
      await page.locator('.overlay__breadcrumb-back').click();
      await waitForOverlayClosed(page).catch(() => {});
    });

    if (fs.existsSync(imgPath)) fs.unlinkSync(imgPath);
  }

  await screenshot(page, 'upload-02-list-with-cartoons');
});
