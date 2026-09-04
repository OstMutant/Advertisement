/* ── Header ──────────────────────────────────────────────────────────────────
 * Description: Smoke checks for the external REST API's own infrastructure -- the health
 *   endpoint, and the OpenAPI/Swagger surface (springdoc). The Swagger checks confirm the
 *   generated spec actually describes this API's real dual-auth model (bearer key + HTTP basic,
 *   both declared in OpenApiConfig) rather than just that springdoc itself boots, and that
 *   Swagger UI is reachable past the security filter chain. No browser needed for any check --
 *   all are plain HTTP requests.
 * Usage: run via the Playwright test runner -- `bash /app/playwright/run.sh 09-rest-api-swagger
 *   --ux`, or as part of the full e2e suite (`bash /app/playwright/run.sh e2e --ux`).
 * Uses: @playwright/test.
 * Env: None.
 * Input: ./_helpers (test, expect).
 * Outputs: Playwright HTML report entries (one per test). No persistent data is created or
 *   modified in the app.
 * Returns: exit code from the Playwright test runner -- 0 when every test in this file passes,
 *   non-zero otherwise.
 * ──────────────────────────────────────────────────────────────────────────── */
const { test, expect } = require('./_helpers');

test.describe('REST API infrastructure', () => {

  test('health endpoint is reachable', async ({ request }) => {
    const response = await request.get('/health');
    expect(response.ok()).toBeTruthy();
    expect(await response.text()).toBe('ok');
  });

  test('OpenAPI spec describes both real auth schemes — bearerKey and basicAuth', async ({ request }) => {
    const response = await request.get('/v3/api-docs');
    expect(response.ok()).toBeTruthy();

    const spec = await response.json();
    const schemes = spec.components?.securitySchemes ?? {};

    expect(schemes.bearerKey).toMatchObject({ type: 'http', scheme: 'bearer' });
    expect(schemes.basicAuth).toMatchObject({ type: 'http', scheme: 'basic' });
  });

  test('Swagger UI is reachable', async ({ request }) => {
    const response = await request.get('/swagger-ui/index.html');
    expect(response.ok()).toBeTruthy();
  });
});
