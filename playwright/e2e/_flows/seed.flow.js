/* ── Header ──────────────────────────────────────────────────────────────────
 * Description: REST-based data-seeding helpers (via the external API, no browser needed) -- an API key
 *   for an account, a generic authenticated POST, and creators for users, taxons, advertisements and
 *   provider profiles, used to populate test data for the e2e specs and fixtures.
 * Usage: None -- a library only, required by spec files and ./_fixtures.js (see Input).
 * Uses: Playwright's APIRequestContext only.
 * Env: None.
 * Input: required by _fixtures.js and the specs that seed data over REST.
 * Outputs: exports issueApiKeyViaApi, postViaApi, createProviderProfileViaApi, seedUsersViaApi,
 *   seedTaxonsViaApi, seedAdvertisementsViaApi.
 * Returns: N/A
 * ──────────────────────────────────────────────────────────────────────────── */

/**
 * Issues an API key for the given account via HTTP Basic (POST /api/api-keys), returning the raw
 * key -- domain-agnostic, reusable by any future REST-based seeding helper.
 * @param {import('@playwright/test').APIRequestContext} request
 * @param {Object} credentials
 * @param {string} credentials.email
 * @param {string} credentials.password
 * @param {string} [label] optional key label, shown in the account's API keys tab.
 * @returns {Promise<string>} the raw API key
 */
async function issueApiKeyViaApi(request, { email, password }, label) {
  const auth = Buffer.from(`${email}:${password}`).toString('base64');
  const response = await request.post('/api/api-keys', {
    headers: { Authorization: `Basic ${auth}` },
    data: label ? { label } : undefined,
  });
  if (!response.ok()) throw new Error(`issueApiKeyViaApi failed: ${response.status()} ${await response.text()}`);
  const { rawKey } = await response.json();
  return rawKey;
}

/**
 * Generic authenticated POST building block -- every domain-specific REST seeder builds on this
 * instead of repeating the Authorization header and response-check boilerplate.
 * @param {import('@playwright/test').APIRequestContext} request
 * @param {string|null} apiKey pass null for a public endpoint.
 * @param {string} path
 * @param {Object} data
 * @returns {Promise<Object>} the parsed JSON response body
 */
async function postViaApi(request, apiKey, path, data) {
  const response = await request.post(path, {
    headers: apiKey ? { Authorization: `Bearer ${apiKey}` } : undefined,
    data,
  });
  if (!response.ok()) throw new Error(`POST ${path} failed: ${response.status()} ${await response.text()}`);
  return response.json();
}

/**
 * Creates one provider profile via POST /api/provider-profiles.
 * @param {import('@playwright/test').APIRequestContext} request
 * @param {string} apiKey the owner's raw API key.
 * @param {Object} body the provider profile request body.
 * @returns {Promise<Object>} the parsed JSON response body
 */
async function createProviderProfileViaApi(request, apiKey, body) {
  return postViaApi(request, apiKey, '/api/provider-profiles', body);
}

/**
 * Registers many users sequentially via POST /api/users (public, no key needed) -- sequential
 * (not parallel) so each user's created_at strictly increases in input order, matching the old
 * serial UI-based seeding's ordering guarantee that filter/sort tests rely on.
 * @param {import('@playwright/test').APIRequestContext} request
 * @param {Array<{name: string, email: string, password: string}>} users
 * @returns {Promise<void>}
 */
async function seedUsersViaApi(request, users) {
  for (const u of users) {
    await postViaApi(request, null, '/api/users', u);
  }
}

/**
 * Creates many taxons (categories or cities) via POST /api/taxons, sequentially -- returns their
 * ids in the same order as the input array.
 * @param {import('@playwright/test').APIRequestContext} request
 * @param {string} apiKey a privileged (admin/moderator) account's key.
 * @param {'CATEGORY'|'CITY'} type
 * @param {Array<{nameEn: string, descriptionEn: string, nameUk: string, descriptionUk: string}>} taxons
 * @returns {Promise<number[]>} taxon ids, one per input entry.
 */
async function seedTaxonsViaApi(request, apiKey, type, taxons) {
  const ids = [];
  for (const t of taxons) {
    const body = await postViaApi(request, apiKey, '/api/taxons', {
      type,
      translations: [
        { locale: 'en', name: t.nameEn, description: t.descriptionEn },
        { locale: 'uk', name: t.nameUk, description: t.descriptionUk },
      ],
    });
    ids.push(body.id);
  }
  return ids;
}

/**
 * Creates many advertisements sequentially via POST /api/advertisements -- sequential (not
 * parallel) so each ad's created_at strictly increases in input order, matching the old
 * serial UI-based seeding's ordering guarantee that filter/sort tests rely on.
 * @param {import('@playwright/test').APIRequestContext} request
 * @param {string} apiKey
 * @param {Array<{title: string, description: string, adKind: string, categoryId: number, cityTaxonId: number}>} ads
 * @returns {Promise<void>}
 */
async function seedAdvertisementsViaApi(request, apiKey, ads) {
  for (const ad of ads) {
    await postViaApi(request, apiKey, '/api/advertisements', {
      title: ad.title,
      description: ad.description,
      adKind: ad.adKind,
      categoryIds: [ad.categoryId],
      cityTaxonId: ad.cityTaxonId,
    });
  }
}

module.exports = {
  issueApiKeyViaApi, postViaApi, createProviderProfileViaApi, seedUsersViaApi, seedTaxonsViaApi, seedAdvertisementsViaApi,
};
