/* ── Header ──────────────────────────────────────────────────────────────────
 * Description: End-to-end flow through the external REST API against the live stack, in five
 *   independent tests -- registration and a bearer key, reference data, a provider profile story
 *   (contact reveals, reviews and replies, flags and moderation, account deletion), an advertisement
 *   with a real media upload, and the audit timeline with settings history. Every step is a plain
 *   HTTP request through Playwright's request fixture; no browser is used.
 * Usage: run via the Playwright test runner -- `bash /app/playwright/run.sh 10-rest-api-flow --ux`
 *   (runs alone or with any other spec), or as part of the full e2e suite
 *   (`bash /app/playwright/run.sh e2e --ux`).
 * Uses: @playwright/test (via ./_fixtures and ./_helpers).
 * Env: ADMIN_EMAIL, ADMIN_PASSWORD -- optional overrides for the administrator account (read by ./_helpers).
 * Input: ./_fixtures (test, expect, adminKey, createUser, unique, runId), ./_helpers (YT_URL,
 *   TEST_USERS), ./_flows/seed.flow (issueApiKeyViaApi, seedTaxonsViaApi).
 * Outputs: Playwright HTML report entries (one per test). Creates only uniquely named users, API
 *   keys, taxons, provider profiles, an advertisement with media, reviews, replies and flags; the
 *   accounts of the profile story are deleted again by its last step.
 * Returns: exit code from the Playwright test runner -- 0 when every test in this file passes,
 *   non-zero otherwise.
 * ──────────────────────────────────────────────────────────────────────────── */
const { test, expect } = require('./_fixtures');
const { YT_URL, TEST_USERS } = require('./_helpers');
const { issueApiKeyViaApi, seedTaxonsViaApi } = require('./_flows/seed.flow');

const PASSWORD = 'password123';
const PNG_1X1_BASE64 = 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==';

const basic = (email, password) => `Basic ${Buffer.from(`${email}:${password}`).toString('base64')}`;
const bearer = key => `Bearer ${key}`;
const pngBuffer = () => Buffer.from(PNG_1X1_BASE64, 'base64');
const emailFor = (unique, prefix) => `${unique(prefix).replace(/\s+/g, '.')}@example.com`;

/** Creates one category and one city through the REST API with names unique to this test. */
async function createReferenceData(request, adminKey, unique) {
  const [categoryId] = await seedTaxonsViaApi(request, adminKey, 'CATEGORY', [
    { nameEn: unique('REST category'), descriptionEn: 'REST flow category', nameUk: unique('REST категорія'), descriptionUk: 'REST flow category' },
  ]);
  const [cityId] = await seedTaxonsViaApi(request, adminKey, 'CITY', [
    { nameEn: unique('REST city'), descriptionEn: 'REST flow city', nameUk: unique('REST місто'), descriptionUk: 'REST flow city' },
  ]);
  return { categoryId, cityId };
}

test.describe('REST API flow through the real stack', () => {

  test('owner registers and gets a key — register, issue key with Basic, list keys, read own user with ETag, read settings, change locale', async ({ request, unique }) => {
    const email = emailFor(unique, 'owner');

    const register = await request.post('/api/users', { data: { name: unique('REST Owner'), email, password: PASSWORD } });
    expect(register.status()).toBe(201);
    const ownerId = (await register.json()).id;

    const issue = await request.post('/api/api-keys', {
      headers: { Authorization: basic(email, PASSWORD) },
      data: { label: 'rest flow owner' },
    });
    expect(issue.status()).toBe(201);
    const ownerKey = (await issue.json()).rawKey;

    const keys = await request.get('/api/api-keys', { headers: { Authorization: bearer(ownerKey) } });
    expect(keys.status()).toBe(200);
    expect(await keys.json()).toHaveLength(1);

    const user = await request.get(`/api/users/${ownerId}`, { headers: { Authorization: bearer(ownerKey) } });
    expect(user.status()).toBe(200);
    expect(user.headers()['etag']).toBeTruthy();

    const settings = await request.get('/api/users/me/settings', { headers: { Authorization: bearer(ownerKey) } });
    expect(settings.status()).toBe(200);

    const locale = await request.put('/api/users/me/locale', {
      headers: { Authorization: bearer(ownerKey) },
      data: { locale: 'uk' },
    });
    expect(locale.status()).toBe(204);
  });

  test('admin reads reference data — issue admin key, own category and city in en and uk', async ({ request, unique }) => {
    const issue = await request.post('/api/api-keys', {
      headers: { Authorization: basic(TEST_USERS.adminEn.email, TEST_USERS.adminEn.password) },
      data: { label: 'rest flow admin' },
    });
    expect(issue.status()).toBe(201);
    const adminKey = (await issue.json()).rawKey;

    const { categoryId, cityId } = await createReferenceData(request, adminKey, unique);

    const categories = await request.get('/api/taxons?type=CATEGORY');
    expect(categories.status()).toBe(200);
    expect((await categories.json()).some(taxon => taxon.id === categoryId)).toBeTruthy();

    const cities = await request.get('/api/taxons?type=CITY');
    expect(cities.status()).toBe(200);
    expect((await cities.json()).some(taxon => taxon.id === cityId)).toBeTruthy();

    const ukCategories = await request.get('/api/taxons?type=CATEGORY&locale=uk');
    expect(ukCategories.status()).toBe(200);
    expect((await ukCategories.json()).some(taxon => taxon.id === categoryId)).toBeTruthy();
  });

  test('owner builds a provider profile story — profile history and If-Match, visitor contact reveals, review and reply, flags and moderation, accounts deleted with the profile', async ({ request, adminKey, createUser, unique }) => {
    const { categoryId, cityId } = await createReferenceData(request, adminKey, unique);
    const owner = await createUser();
    const ownerKey = await issueApiKeyViaApi(request, owner);
    const headers = { Authorization: bearer(ownerKey) };
    const profileBody = {
      kind: 'MASTER',
      about: unique('REST flow master'),
      categoryIds: [categoryId],
      cityTaxonId: cityId,
      phone: '+380501234567',
      telegram: 'rest_flow_master',
    };
    let profileId;

    await test.step('owner creates the profile, updates it with If-Match, restores the first snapshot', async () => {
      const create = await request.post('/api/provider-profiles', { headers, data: profileBody });
      expect(create.status()).toBe(201);
      const created = await create.json();
      profileId = created.id;
      expect(created.categoryNames.length).toBeGreaterThan(0);
      expect(created.cityName).toBeTruthy();

      const historyBefore = await request.get(`/api/provider-profiles/${profileId}/history`, { headers });
      expect(historyBefore.status()).toBe(200);
      expect((await historyBefore.json()).length).toBeGreaterThanOrEqual(1);

      const current = await request.get(`/api/provider-profiles/${profileId}`);
      expect(current.status()).toBe(200);

      const update = await request.put(`/api/provider-profiles/${profileId}`, {
        headers: { ...headers, 'If-Match': current.headers()['etag'] },
        data: { ...profileBody, about: `${profileBody.about}, updated` },
      });
      expect(update.status()).toBe(200);

      const historyAfter = await request.get(`/api/provider-profiles/${profileId}/history`, { headers });
      expect(historyAfter.status()).toBe(200);
      const entries = await historyAfter.json();
      const oldestSnapshotId = entries[entries.length - 1].snapshotId;

      const snapshot = await request.get(`/api/provider-profiles/${profileId}/history/${oldestSnapshotId}`, { headers });
      expect(snapshot.status()).toBe(200);

      const latest = await request.get(`/api/provider-profiles/${profileId}`);
      const restore = await request.post(`/api/provider-profiles/${profileId}/history/${oldestSnapshotId}/restore`, {
        headers: { ...headers, 'If-Match': latest.headers()['etag'] },
      });
      expect(restore.status()).toBe(200);

      const missingIfMatch = await request.put(`/api/provider-profiles/${profileId}`, { headers, data: profileBody });
      expect(missingIfMatch.status()).toBe(400);
    });

    const outsider = await createUser();
    const outsiderKey = await issueApiKeyViaApi(request, outsider);

    await test.step('visitor reveals contact, owner sees view counts, other user gets 403', async () => {
      const channels = await request.get(`/api/provider-profiles/${profileId}/contact`);
      expect(channels.status()).toBe(200);
      const { channels: available } = await channels.json();
      expect(available).toContain('PHONE');
      expect(available).toContain('TELEGRAM');

      const phone = await request.post(`/api/provider-profiles/${profileId}/contact/reveals`, { data: { channel: 'PHONE' } });
      expect(phone.status()).toBe(200);
      expect((await phone.json()).value).toBe('+380501234567');

      const telegram = await request.post(`/api/provider-profiles/${profileId}/contact/reveals`, { data: { channel: 'TELEGRAM' } });
      expect(telegram.status()).toBe(200);
      expect((await telegram.json()).deepLink).toMatch(/^https:\/\/t\.me\//);

      const views = await request.get(`/api/provider-profiles/${profileId}/contact/views`, { headers });
      expect(views.status()).toBe(200);
      expect((await views.json()).some(entry => entry.count >= 1)).toBeTruthy();

      const forbidden = await request.get(`/api/provider-profiles/${profileId}/contact/views`, {
        headers: { Authorization: bearer(outsiderKey) },
      });
      expect(forbidden.status()).toBe(403);

      const removeOutsider = await request.delete(`/api/users/${outsider.id}`, { headers: { Authorization: bearer(outsiderKey) } });
      expect(removeOutsider.status()).toBe(204);
    });

    const reviewer = await createUser();
    const reviewerKey = await issueApiKeyViaApi(request, reviewer);
    const flagger = await createUser();
    const flaggerKey = await issueApiKeyViaApi(request, flagger);
    let feedbackId;
    let replyId;

    await test.step('reviewer reviews, owner replies, both are edited with If-Match', async () => {
      const review = await request.post(`/api/provider-profiles/${profileId}/feedback`, {
        headers: { Authorization: bearer(reviewerKey) },
        data: { rating: 5, text: 'Great' },
      });
      expect(review.status()).toBe(201);
      const createdReview = await review.json();
      feedbackId = createdReview.id;

      const list = await request.get(`/api/provider-profiles/${profileId}/feedback`);
      expect(list.status()).toBe(200);
      expect(await list.json()).toHaveLength(1);

      const aggregate = await request.get(`/api/provider-profiles/${profileId}/feedback/aggregate`);
      expect(aggregate.status()).toBe(200);
      expect((await aggregate.json()).reviewCount).toBe(1);

      const reply = await request.post(`/api/feedback/${feedbackId}/reply`, { headers, data: { text: 'Thanks' } });
      expect(reply.status()).toBe(201);
      const createdReply = await reply.json();
      replyId = createdReply.id;

      const editReply = await request.put(`/api/feedback/${feedbackId}/reply`, {
        headers: { ...headers, 'If-Match': `"${createdReply.version}"` },
        data: { text: 'Thanks, again' },
      });
      expect(editReply.status()).toBe(200);

      const editReview = await request.put(`/api/feedback/${feedbackId}`, {
        headers: { Authorization: bearer(reviewerKey), 'If-Match': `"${createdReview.version}"` },
        data: { rating: 4, text: 'Good' },
      });
      expect(editReview.status()).toBe(200);
    });

    await test.step('flagger flags the review and the reply, admin moderates', async () => {
      const flagReview = await request.post(`/api/feedback/${feedbackId}/flags`, {
        headers: { Authorization: bearer(flaggerKey) },
        data: { reason: 'spam' },
      });
      expect(flagReview.status()).toBe(204);

      const queue = await request.get('/api/moderation/queue', { headers: { Authorization: bearer(adminKey) } });
      expect(queue.status()).toBe(200);
      expect((await queue.json()).some(entry => entry.id === feedbackId && entry.type === 'FEEDBACK')).toBeTruthy();

      const approve = await request.post(`/api/moderation/feedback/${feedbackId}/approve`, {
        headers: { Authorization: bearer(adminKey) },
      });
      expect(approve.status()).toBe(204);

      const flagReply = await request.post(`/api/feedback/${feedbackId}/reply/flags`, {
        headers: { Authorization: bearer(flaggerKey) },
        data: { reason: 'spam' },
      });
      expect(flagReply.status()).toBe(204);

      const confirm = await request.post(`/api/moderation/replies/${replyId}/confirm-violation`, {
        headers: { Authorization: bearer(adminKey) },
      });
      expect(confirm.status()).toBe(204);

      const publicList = await request.get(`/api/provider-profiles/${profileId}/feedback`);
      expect(publicList.status()).toBe(200);
      const entry = (await publicList.json()).find(item => item.id === feedbackId);
      expect(entry).toBeTruthy();
      expect(entry.reply.text).toBeNull();
    });

    await test.step('owner, reviewer and flagger delete their users, the profile goes with the owner', async () => {
      const removeReviewer = await request.delete(`/api/users/${reviewer.id}`, { headers: { Authorization: bearer(reviewerKey) } });
      expect(removeReviewer.status()).toBe(204);

      const removeFlagger = await request.delete(`/api/users/${flagger.id}`, { headers: { Authorization: bearer(flaggerKey) } });
      expect(removeFlagger.status()).toBe(204);

      const removeOwner = await request.delete(`/api/users/${owner.id}`, { headers });
      expect(removeOwner.status()).toBe(204);

      const profile = await request.get(`/api/provider-profiles/${profileId}`);
      expect(profile.status()).toBe(404);
    });
  });

  test('owner creates advertisement with a real file — upload to storage, fetch it back, history, restore, video link, delete media', async ({ request, adminKey, createUser, unique }) => {
    const { categoryId, cityId } = await createReferenceData(request, adminKey, unique);
    const owner = await createUser();
    const ownerKey = await issueApiKeyViaApi(request, owner);
    const headers = { Authorization: bearer(ownerKey) };
    const title = unique('REST flow ad');

    const create = await request.post('/api/advertisements', {
      headers,
      data: { title, description: 'REST flow description', adKind: 'OFFER', categoryIds: [categoryId], cityTaxonId: cityId },
    });
    expect(create.status()).toBe(201);
    const advertisementId = (await create.json()).id;

    const upload = await request.post(`/api/advertisements/${advertisementId}/media`, {
      headers,
      multipart: { files: { name: 'pic.png', mimeType: 'image/png', buffer: pngBuffer() } },
    });
    expect(upload.status()).toBe(201);
    const [uploaded] = await upload.json();

    const fetched = await request.get(uploaded.url);
    expect(fetched.status()).toBe(200);
    expect(fetched.headers()['content-type']).toContain('image/png');

    const historyBefore = await request.get(`/api/advertisements/${advertisementId}/history`, { headers });
    expect(historyBefore.status()).toBe(200);
    expect((await historyBefore.json()).length).toBeGreaterThanOrEqual(1);

    const current = await request.get(`/api/advertisements/${advertisementId}`);
    const update = await request.put(`/api/advertisements/${advertisementId}`, {
      headers: { ...headers, 'If-Match': current.headers()['etag'] },
      data: { title: `${title} updated`, description: 'REST flow description, updated', adKind: 'OFFER', categoryIds: [categoryId], cityTaxonId: cityId },
    });
    expect(update.status()).toBe(200);

    const historyAfter = await request.get(`/api/advertisements/${advertisementId}/history`, { headers });
    const entries = await historyAfter.json();
    const oldestSnapshotId = entries[entries.length - 1].snapshotId;

    const latest = await request.get(`/api/advertisements/${advertisementId}`);
    const restore = await request.post(`/api/advertisements/${advertisementId}/history/${oldestSnapshotId}/restore`, {
      headers: { ...headers, 'If-Match': latest.headers()['etag'] },
    });
    expect(restore.status()).toBe(200);

    const video = await request.post(`/api/advertisements/${advertisementId}/media/videos`, { headers, data: { url: YT_URL } });
    expect(video.status()).toBe(201);

    const remove = await request.delete(`/api/advertisements/${advertisementId}/media/${uploaded.id}`, { headers });
    expect(remove.status()).toBe(204);
  });

  test('admin reads audit and owner restores settings — timeline, settings save, settings history, restore', async ({ request, adminKey, createUser }) => {
    const owner = await createUser();
    const ownerKey = await issueApiKeyViaApi(request, owner);

    const timeline = await request.get('/api/audit/timeline', { headers: { Authorization: bearer(adminKey) } });
    expect(timeline.status()).toBe(200);
    expect((await timeline.json()).length).toBeGreaterThan(0);

    const currentSettings = await request.get('/api/users/me/settings', { headers: { Authorization: bearer(ownerKey) } });
    expect(currentSettings.status()).toBe(200);

    const save = await request.patch('/api/users/me/settings', {
      headers: { Authorization: bearer(ownerKey), 'If-Match': currentSettings.headers()['etag'] },
      data: { adsPageSize: 25, usersPageSize: 25, timelinePageSize: 25, providerProfilesPageSize: 25 },
    });
    expect(save.status()).toBe(200);

    const history = await request.get('/api/users/me/settings/history', { headers: { Authorization: bearer(ownerKey) } });
    expect(history.status()).toBe(200);
    const entries = await history.json();
    expect(entries.length).toBeGreaterThanOrEqual(2);
    const oldestSnapshotId = entries[entries.length - 1].snapshotId;

    const latestSettings = await request.get('/api/users/me/settings', { headers: { Authorization: bearer(ownerKey) } });
    const restore = await request.post(`/api/users/me/settings/history/${oldestSnapshotId}/restore`, {
      headers: { Authorization: bearer(ownerKey), 'If-Match': latestSettings.headers()['etag'] },
    });
    expect(restore.status()).toBe(200);
  });
});
