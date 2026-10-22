/* ── Header ──────────────────────────────────────────────────────────────────
 * Description: End-to-end flow through the external REST API against the live stack -- registers
 *   users and issues bearer keys, manages reference data, a provider profile and an advertisement
 *   with a real media upload, reveals contact channels, reviews and replies, moderates flagged
 *   content, restores history snapshots, and deletes the accounts again. Every step is a plain HTTP
 *   request through Playwright's request fixture; no browser is used.
 * Usage: run via the Playwright test runner -- `bash /app/playwright/run.sh 10-rest-api-flow --ux`,
 *   or as part of the full e2e suite (`bash /app/playwright/run.sh e2e --ux`).
 * Uses: @playwright/test (via ./_helpers).
 * Env: ADMIN_EMAIL, ADMIN_PASSWORD -- optional overrides for the seeded admin account (read by ./_helpers).
 * Input: ./_helpers (test, expect, TEST_USERS, YT_URL); the seeded admin account and the category
 *   and city taxons seeded by spec 03.
 * Outputs: Playwright HTML report entries (one per test). Creates users, API keys, a provider
 *   profile, an advertisement with media, reviews, replies and flags in the app; the three accounts
 *   created here are deleted again by the last test.
 * Returns: exit code from the Playwright test runner -- 0 when every test in this file passes,
 *   non-zero otherwise.
 * ──────────────────────────────────────────────────────────────────────────── */
const { test, expect, TEST_USERS, YT_URL } = require('./_helpers');

const PASSWORD = 'password123';
const PNG_1X1_BASE64 = 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==';

let emailCounter = 0;
const uniqueEmail = prefix => `${prefix}.${Date.now()}.${++emailCounter}@example.com`;
const basic = (email, password) => `Basic ${Buffer.from(`${email}:${password}`).toString('base64')}`;
const bearer = key => `Bearer ${key}`;
const pngBuffer = () => Buffer.from(PNG_1X1_BASE64, 'base64');

test.describe.serial('REST API flow through the real stack', () => {

  let adminKey;
  let categoryId;
  let cityId;

  let ownerId;
  let ownerKey;
  let reviewerId;
  let reviewerKey;
  let flaggerId;
  let flaggerKey;

  let profileId;
  let profileOldestSnapshotId;

  let advertisementId;
  let advertisementOldestSnapshotId;
  let uploadedAttachmentId;
  let uploadedMediaUrl;

  let feedbackId;
  let feedbackVersion;
  let replyId;
  let replyVersion;

  let settingsOldestSnapshotId;

  test('owner registers and gets a key — register, issue key with Basic, list keys, read own user with ETag, read settings, change locale', async ({ request }) => {
    const email = uniqueEmail('owner');

    const register = await request.post('/api/users', { data: { name: 'REST Owner', email, password: PASSWORD } });
    expect(register.status()).toBe(201);
    ownerId = (await register.json()).id;

    const issue = await request.post('/api/api-keys', {
      headers: { Authorization: basic(email, PASSWORD) },
      data: { label: 'rest flow owner' },
    });
    expect(issue.status()).toBe(201);
    ownerKey = (await issue.json()).rawKey;

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

  test('admin reads reference data — issue admin key, categories and cities in en and uk', async ({ request }) => {
    const issue = await request.post('/api/api-keys', {
      headers: { Authorization: basic(TEST_USERS.adminEn.email, TEST_USERS.adminEn.password) },
      data: { label: 'rest flow admin' },
    });
    expect(issue.status()).toBe(201);
    adminKey = (await issue.json()).rawKey;

    const categories = await request.get('/api/taxons?type=CATEGORY');
    expect(categories.status()).toBe(200);
    const categoryList = await categories.json();
    expect(categoryList.length).toBeGreaterThan(0);
    categoryId = categoryList[0].id;

    const cities = await request.get('/api/taxons?type=CITY');
    expect(cities.status()).toBe(200);
    const cityList = await cities.json();
    expect(cityList.length).toBeGreaterThan(0);
    cityId = cityList[0].id;

    const ukCategories = await request.get('/api/taxons?type=CATEGORY&locale=uk');
    expect(ukCategories.status()).toBe(200);
  });

  test('owner creates provider profile — names resolved, history, update with If-Match, restore first snapshot, missing If-Match', async ({ request }) => {
    const headers = { Authorization: bearer(ownerKey) };
    const profileBody = {
      kind: 'MASTER',
      about: 'REST flow master',
      categoryIds: [categoryId],
      cityTaxonId: cityId,
      phone: '+380501234567',
      telegram: 'rest_flow_master',
    };

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
    const profileEtag = current.headers()['etag'];

    const update = await request.put(`/api/provider-profiles/${profileId}`, {
      headers: { ...headers, 'If-Match': profileEtag },
      data: { ...profileBody, about: 'REST flow master, updated' },
    });
    expect(update.status()).toBe(200);

    const historyAfter = await request.get(`/api/provider-profiles/${profileId}/history`, { headers });
    expect(historyAfter.status()).toBe(200);
    const entries = await historyAfter.json();
    profileOldestSnapshotId = entries[entries.length - 1].snapshotId;

    const snapshot = await request.get(`/api/provider-profiles/${profileId}/history/${profileOldestSnapshotId}`, { headers });
    expect(snapshot.status()).toBe(200);

    const latest = await request.get(`/api/provider-profiles/${profileId}`);
    const restore = await request.post(`/api/provider-profiles/${profileId}/history/${profileOldestSnapshotId}/restore`, {
      headers: { ...headers, 'If-Match': latest.headers()['etag'] },
    });
    expect(restore.status()).toBe(200);

    const missingIfMatch = await request.put(`/api/provider-profiles/${profileId}`, { headers, data: profileBody });
    expect(missingIfMatch.status()).toBe(400);
  });

  test('owner creates advertisement with a real file — upload to storage, fetch it back, history, restore, video link, delete media', async ({ request }) => {
    const headers = { Authorization: bearer(ownerKey) };

    const create = await request.post('/api/advertisements', {
      headers,
      data: {
        title: 'REST flow ad',
        description: 'REST flow description',
        adKind: 'OFFER',
        categoryIds: [categoryId],
        cityTaxonId: cityId,
      },
    });
    expect(create.status()).toBe(201);
    advertisementId = (await create.json()).id;

    const upload = await request.post(`/api/advertisements/${advertisementId}/media`, {
      headers,
      multipart: { files: { name: 'pic.png', mimeType: 'image/png', buffer: pngBuffer() } },
    });
    expect(upload.status()).toBe(201);
    const [uploaded] = await upload.json();
    uploadedAttachmentId = uploaded.id;
    uploadedMediaUrl = uploaded.url;

    const fetched = await request.get(uploadedMediaUrl);
    expect(fetched.status()).toBe(200);
    expect(fetched.headers()['content-type']).toContain('image/png');

    const historyBefore = await request.get(`/api/advertisements/${advertisementId}/history`, { headers });
    expect(historyBefore.status()).toBe(200);
    expect((await historyBefore.json()).length).toBeGreaterThanOrEqual(1);

    const current = await request.get(`/api/advertisements/${advertisementId}`);
    const update = await request.put(`/api/advertisements/${advertisementId}`, {
      headers: { ...headers, 'If-Match': current.headers()['etag'] },
      data: {
        title: 'REST flow ad updated',
        description: 'REST flow description, updated',
        adKind: 'OFFER',
        categoryIds: [categoryId],
        cityTaxonId: cityId,
      },
    });
    expect(update.status()).toBe(200);

    const historyAfter = await request.get(`/api/advertisements/${advertisementId}/history`, { headers });
    const entries = await historyAfter.json();
    advertisementOldestSnapshotId = entries[entries.length - 1].snapshotId;

    const latest = await request.get(`/api/advertisements/${advertisementId}`);
    const restore = await request.post(`/api/advertisements/${advertisementId}/history/${advertisementOldestSnapshotId}/restore`, {
      headers: { ...headers, 'If-Match': latest.headers()['etag'] },
    });
    expect(restore.status()).toBe(200);

    const video = await request.post(`/api/advertisements/${advertisementId}/media/videos`, {
      headers,
      data: { url: YT_URL },
    });
    expect(video.status()).toBe(201);

    const remove = await request.delete(`/api/advertisements/${advertisementId}/media/${uploadedAttachmentId}`, { headers });
    expect(remove.status()).toBe(204);
  });

  test('visitor reveals contact — channels, phone reveal, Telegram deep link, owner sees view counts, other user gets 403', async ({ request }) => {
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

    const views = await request.get(`/api/provider-profiles/${profileId}/contact/views`, {
      headers: { Authorization: bearer(ownerKey) },
    });
    expect(views.status()).toBe(200);
    expect((await views.json()).some(entry => entry.count >= 1)).toBeTruthy();

    const outsiderEmail = uniqueEmail('outsider');
    const outsider = await request.post('/api/users', { data: { name: 'REST Outsider', email: outsiderEmail, password: PASSWORD } });
    expect(outsider.status()).toBe(201);
    const outsiderId = (await outsider.json()).id;
    const outsiderIssue = await request.post('/api/api-keys', {
      headers: { Authorization: basic(outsiderEmail, PASSWORD) },
      data: { label: 'rest flow outsider' },
    });
    expect(outsiderIssue.status()).toBe(201);
    const outsiderKey = (await outsiderIssue.json()).rawKey;

    const forbidden = await request.get(`/api/provider-profiles/${profileId}/contact/views`, {
      headers: { Authorization: bearer(outsiderKey) },
    });
    expect(forbidden.status()).toBe(403);

    const removeOutsider = await request.delete(`/api/users/${outsiderId}`, { headers: { Authorization: bearer(outsiderKey) } });
    expect(removeOutsider.status()).toBe(204);
  });

  test('reviewer reviews and owner replies — review, aggregate, reply, edits with If-Match', async ({ request }) => {
    const reviewerEmail = uniqueEmail('reviewer');
    const register = await request.post('/api/users', { data: { name: 'REST Reviewer', email: reviewerEmail, password: PASSWORD } });
    expect(register.status()).toBe(201);
    reviewerId = (await register.json()).id;

    const issue = await request.post('/api/api-keys', {
      headers: { Authorization: basic(reviewerEmail, PASSWORD) },
      data: { label: 'rest flow reviewer' },
    });
    expect(issue.status()).toBe(201);
    reviewerKey = (await issue.json()).rawKey;

    const review = await request.post(`/api/provider-profiles/${profileId}/feedback`, {
      headers: { Authorization: bearer(reviewerKey) },
      data: { rating: 5, text: 'Great' },
    });
    expect(review.status()).toBe(201);
    const createdReview = await review.json();
    feedbackId = createdReview.id;
    feedbackVersion = createdReview.version;

    const list = await request.get(`/api/provider-profiles/${profileId}/feedback`);
    expect(list.status()).toBe(200);
    expect(await list.json()).toHaveLength(1);

    const aggregate = await request.get(`/api/provider-profiles/${profileId}/feedback/aggregate`);
    expect(aggregate.status()).toBe(200);
    expect((await aggregate.json()).reviewCount).toBe(1);

    const reply = await request.post(`/api/feedback/${feedbackId}/reply`, {
      headers: { Authorization: bearer(ownerKey) },
      data: { text: 'Thanks' },
    });
    expect(reply.status()).toBe(201);
    const createdReply = await reply.json();
    replyId = createdReply.id;
    replyVersion = createdReply.version;

    const editReply = await request.put(`/api/feedback/${feedbackId}/reply`, {
      headers: { Authorization: bearer(ownerKey), 'If-Match': `"${replyVersion}"` },
      data: { text: 'Thanks, again' },
    });
    expect(editReply.status()).toBe(200);

    const editReview = await request.put(`/api/feedback/${feedbackId}`, {
      headers: { Authorization: bearer(reviewerKey), 'If-Match': `"${feedbackVersion}"` },
      data: { rating: 4, text: 'Good' },
    });
    expect(editReview.status()).toBe(200);
  });

  test('flagger flags feedback and reply, admin moderates — queue, approve, confirm violation, hidden reply', async ({ request }) => {
    const flaggerEmail = uniqueEmail('flagger');
    const register = await request.post('/api/users', { data: { name: 'REST Flagger', email: flaggerEmail, password: PASSWORD } });
    expect(register.status()).toBe(201);
    flaggerId = (await register.json()).id;

    const issue = await request.post('/api/api-keys', {
      headers: { Authorization: basic(flaggerEmail, PASSWORD) },
      data: { label: 'rest flow flagger' },
    });
    expect(issue.status()).toBe(201);
    flaggerKey = (await issue.json()).rawKey;

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

  test('admin reads audit and owner restores settings — timeline, settings save, settings history, restore', async ({ request }) => {
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
    settingsOldestSnapshotId = entries[entries.length - 1].snapshotId;

    const latestSettings = await request.get('/api/users/me/settings', { headers: { Authorization: bearer(ownerKey) } });
    const restore = await request.post(`/api/users/me/settings/history/${settingsOldestSnapshotId}/restore`, {
      headers: { Authorization: bearer(ownerKey), 'If-Match': latestSettings.headers()['etag'] },
    });
    expect(restore.status()).toBe(200);
  });

  test('owner, reviewer and flagger delete their users — cascade removes the profile', async ({ request }) => {
    const removeReviewer = await request.delete(`/api/users/${reviewerId}`, { headers: { Authorization: bearer(reviewerKey) } });
    expect(removeReviewer.status()).toBe(204);

    const removeFlagger = await request.delete(`/api/users/${flaggerId}`, { headers: { Authorization: bearer(flaggerKey) } });
    expect(removeFlagger.status()).toBe(204);

    const removeOwner = await request.delete(`/api/users/${ownerId}`, { headers: { Authorization: bearer(ownerKey) } });
    expect(removeOwner.status()).toBe(204);

    const profile = await request.get(`/api/provider-profiles/${profileId}`);
    expect(profile.status()).toBe(404);
  });
});
