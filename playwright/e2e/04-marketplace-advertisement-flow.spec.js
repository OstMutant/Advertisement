const { test, expect } = require('./_test-helpers');
const { runFillLoginFormFlow, runSubmitLoginFlow, runLogoutFlow } = require('./_flows/auth.flow');
const { runCreateAdvertisementFlow, runEditAdvertisementFlow, runRestoreAdvertisementFlow } = require('./_flows/advertisement.flow');
const { TEST_USERS } = require('./_helpers');

test.describe.configure({ mode: 'serial' });

const EN_AD = {
  title: 'EN Advertisement',
  description: 'Advertisement created by English user with all media types: YouTube, image and video.',
};

const UK_AD = {
  title: 'UK Оголошення',
  description: 'Оголошення створене українським користувачем з усіма типами медіа: YouTube, зображення та відео.',
};

test.describe('Advertisement flow', () => {
  let page;

  test.beforeAll(async ({ browser }) => {
    page = await browser.newPage();
    await page.goto('/');
  });

  test.afterAll(async () => {
    await page.close();
  });

  test('userEn creates advertisement with YouTube, image and video', async () => {
    await runFillLoginFormFlow(page, TEST_USERS.userEn);
    await runSubmitLoginFlow(page, expect, TEST_USERS.userEn);
    await runCreateAdvertisementFlow(page, expect, {
      title: EN_AD.title,
      description: EN_AD.description,
      screenshotPrefix: 'adv-useren-create',
    });
    await runLogoutFlow(page, expect);
  });

  test('userUk creates advertisement with YouTube, image and video', async () => {
    await runFillLoginFormFlow(page, TEST_USERS.userUk);
    await runSubmitLoginFlow(page, expect, TEST_USERS.userUk);
    await runCreateAdvertisementFlow(page, expect, {
      title: UK_AD.title,
      description: UK_AD.description,
      screenshotPrefix: 'adv-useruk-create',
    });
    await runLogoutFlow(page, expect);
  });

  test('userEn edits advertisement — removes all media, updates title and description', async () => {
    await runFillLoginFormFlow(page, TEST_USERS.userEn);
    await runSubmitLoginFlow(page, expect, TEST_USERS.userEn);
    await runEditAdvertisementFlow(page, expect, {
      originalTitle: EN_AD.title,
      originalDescription: EN_AD.description,
      newTitle: 'EN Advertisement Updated',
      newDescription: 'Updated description for the EN advertisement.',
      screenshotPrefix: 'adv-useren-edit',
    });
    await runLogoutFlow(page, expect);
  });

  test('userUk edits advertisement — removes all media, updates title and description', async () => {
    await runFillLoginFormFlow(page, TEST_USERS.userUk);
    await runSubmitLoginFlow(page, expect, TEST_USERS.userUk);
    await runEditAdvertisementFlow(page, expect, {
      originalTitle: UK_AD.title,
      originalDescription: UK_AD.description,
      newTitle: 'UK Оголошення Оновлено',
      newDescription: 'Оновлений опис для UK оголошення.',
      screenshotPrefix: 'adv-useruk-edit',
    });
    await runLogoutFlow(page, expect);
  });

  test('userEn restores advertisement to original version', async () => {
    await runFillLoginFormFlow(page, TEST_USERS.userEn);
    await runSubmitLoginFlow(page, expect, TEST_USERS.userEn);
    await runRestoreAdvertisementFlow(page, expect, {
      currentTitle: 'EN Advertisement Updated',
      restoredTitle: EN_AD.title,
      restoredDescription: EN_AD.description,
      screenshotPrefix: 'adv-useren-restore',
    });
    await runLogoutFlow(page, expect);
  });

  test('userUk restores advertisement to original version', async () => {
    await runFillLoginFormFlow(page, TEST_USERS.userUk);
    await runSubmitLoginFlow(page, expect, TEST_USERS.userUk);
    await runRestoreAdvertisementFlow(page, expect, {
      currentTitle: 'UK Оголошення Оновлено',
      restoredTitle: UK_AD.title,
      restoredDescription: UK_AD.description,
      screenshotPrefix: 'adv-useruk-restore',
    });
    await runLogoutFlow(page, expect);
  });
});
