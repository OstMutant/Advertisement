const { chromium } = require('playwright');
const { check, screenshot, login } = require('./_common');
const https = require('https');
const fs    = require('fs');

const UX = process.argv.includes('--ux');

function downloadPng(url, dest) {
  return new Promise((resolve, reject) => {
    const file = fs.createWriteStream(dest);
    https.get(url, res => {
      if (res.statusCode !== 200) return reject(new Error(`HTTP ${res.statusCode} for ${url}`));
      res.pipe(file);
      file.on('finish', () => file.close(resolve));
    }).on('error', reject);
  });
}

// DiceBear v9 — "adventurer" style, each seed produces a unique cartoon character
const ADS = [
  { title: 'Red Sunset Villa',     desc: 'Cozy villa with stunning red sunset views',   seed: 'felix'   },
  { title: 'Green Valley Cabin',   desc: 'Peaceful cabin surrounded by green forest',   seed: 'luna'    },
  { title: 'Blue Ocean Apartment', desc: 'Modern apartment with blue ocean panorama',   seed: 'oliver'  },
  { title: 'Orange Desert House',  desc: 'Warm desert house with orange dunes outside', seed: 'mia'     },
  { title: 'Purple Mountain Lodge',desc: 'Elegant lodge high in the purple mountains',  seed: 'jasper'  },
  { title: 'Teal Lake Cottage',    desc: 'Charming cottage by the teal mountain lake',  seed: 'aurora'  },
];

const DICEBEAR = (seed) =>
  `https://api.dicebear.com/9.x/adventurer/png?seed=${seed}&size=256&backgroundColor=b6e3f4,c0aede,d1d4f9,ffd5dc,ffdfbf`;

async function createAd(page, title, desc) {
  await page.locator('.add-advertisement-button').click();
  await page.waitForTimeout(800);
  const overlay = page.locator('.advertisement-overlay');
  await overlay.locator('vaadin-text-field input').fill(title);
  await overlay.locator('vaadin-text-area textarea').fill(desc);
  await overlay.locator('vaadin-button').filter({ hasText: /зберегти|save/i }).click();
  await page.waitForTimeout(1500);
}

(async () => {
  const browser = await chromium.launch();
  const page    = await browser.newPage({ viewport: { width: 1280, height: 900 } });

  await login(page);
  await page.waitForTimeout(1000);

  // Step 1: Create 6 fresh ads
  await check('Create 6 cartoon property ads', async () => {
    for (const ad of ADS) {
      await createAd(page, ad.title, ad.desc);
    }
    console.log('      6 ads created');
  });
  await screenshot(page, 'upload-01-ads-created', UX);

  // Step 2: Download cartoon image and upload to each ad
  console.log('Downloading cartoon avatars and uploading...');
  for (const ad of ADS) {
    const imgPath = `/tmp/cartoon-${ad.seed}.png`;

    await check(`Upload cartoon to "${ad.title}"`, async () => {
      await downloadPng(DICEBEAR(ad.seed), imgPath);
      console.log(`      downloaded avatar for ${ad.seed}`);

      await page.waitForSelector('.advertisement-title', { timeout: 5000 });
      await page.locator('.advertisement-card')
        .filter({ has: page.locator('.advertisement-title', { hasText: ad.title }) })
        .first()
        .click();
      await page.waitForTimeout(800);
      await page.locator('vaadin-button').filter({ hasText: /редагувати|edit/i }).first().click();
      await page.waitForTimeout(800);
      await page.locator('vaadin-upload input[type="file"]').setInputFiles(imgPath);
      await page.waitForTimeout(2000);
      await page.locator('.base-overlay.overlay--visible vaadin-button')
        .filter({ hasText: /зберегти|save/i }).click();
      await page.waitForTimeout(1500);
      // overlay switches to view mode after save — close via breadcrumb
      await page.locator('.overlay__breadcrumb-back').click();
      await page.waitForSelector('.base-overlay.overlay--visible', { state: 'hidden', timeout: 5000 }).catch(() => {});
      await page.waitForTimeout(300);
    });

    if (fs.existsSync(imgPath)) fs.unlinkSync(imgPath);
  }

  await screenshot(page, 'upload-02-list-with-cartoons', UX);
  await browser.close();
})();
