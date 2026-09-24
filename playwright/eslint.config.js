/* ── Header ──────────────────────────────────────────────────────────────────
 * Description: Flat ESLint config for the e2e test suite -- enables eslint-plugin-playwright's
 *   recommended rules, with playwright/missing-playwright-await made explicit (the plain-JS
 *   equivalent of @typescript-eslint/no-floating-promises: catches an un-awaited Playwright API
 *   call, a real silent test-failure mode this suite has no other guard against).
 *   playwright/expect-expect is reconfigured (not disabled) via assertFunctionPatterns -- this
 *   suite deliberately delegates real assertions into helper functions prefixed assert-, verify-,
 *   or suffixed -Flow (see playwright/CLAUDE.md's helper-organization rules) -- plus a third
 *   suffixed -ViaApi (seed-data helpers that throw on a non-ok HTTP response instead of calling
 *   expect() directly) -- which the rule's own default (a direct expect() call inside the test
 *   body only) cannot see into, producing false positives on nearly every test in this suite
 *   otherwise.
 *   playwright/no-conditional-in-test is disabled -- every real instance in this suite (checked
 *   individually, not assumed) branches on a caller-supplied config parameter passed into a
 *   shared flow helper (e.g. richText, cityToSet) or filters/cleans up test-infrastructure state,
 *   never on live page state read mid-test -- the actual non-determinism this rule exists to
 *   catch. playwright/no-skipped-test is disabled -- every skip in this suite is the documented
 *   `test.skip(!process.env.PW_FULL, reason)` --full feature-gate, not a forgotten/broken test.
 * Usage: npx eslint . (run from /tmp inside pw-runner, via playwright/run.sh --lint).
 * Uses: eslint, eslint-plugin-playwright.
 * Env: None.
 * Input: None.
 * Outputs: lint errors/warnings to stdout.
 * Returns: N/A -- eslint's own CLI exit code (0 = clean, 1 = lint errors found).
 * ──────────────────────────────────────────────────────────────────────────── */
const playwright = require('eslint-plugin-playwright');

const recommended = playwright.configs['flat/recommended'];

module.exports = [
  {
    ...recommended,
    files: ['**/*.js'],
    rules: {
      ...recommended.rules,
      'playwright/missing-playwright-await': 'error',
      'playwright/expect-expect': ['warn', { assertFunctionPatterns: ['^assert', '^verify', '^run.*Flow$', '.*ViaApi$'] }],
      'playwright/no-conditional-in-test': 'off',
      'playwright/no-skipped-test': 'off',
    },
  },
  {
    ignores: ['pw-report/**', 'node_modules/**'],
  },
];
