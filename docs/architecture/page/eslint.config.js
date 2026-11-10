/* ── Header ──────────────────────────────────────────────────────────────────
 * Description: Flat ESLint config for the architecture-map page script -- the *.js files here are
 *   fragments of one classic browser script, so the lint run checks their concatenation (page.js
 *   next to this config), never a single fragment. Rules are ESLint's recommended set. Browser
 *   globals come from the `globals` package; the page-specific globals (MODEL and the three
 *   libraries) exist only as <script> tags in the generated HTML, so they are listed here. Functions
 *   called only from inline on*="..." attributes of the generated markup look unused to ESLint, so
 *   their names are read from page.js itself and exempted from no-unused-vars.
 * Usage: npx eslint . (run from the directory holding page.js and this file; the lint stage of the CI runner does this).
 * Uses: @eslint/js, globals.
 * Env: None.
 * Input: page.js beside this file (the concatenated page script).
 * Outputs: lint errors/warnings to stdout.
 * Returns: N/A -- eslint's own CLI exit code (0 = clean, 1 = lint errors found).
 * ──────────────────────────────────────────────────────────────────────────── */
const fs = require('fs');
const path = require('path');
const js = require('@eslint/js');
const globals = require('globals');

const source = fs.readFileSync(path.join(__dirname, 'page.js'), 'utf8');
const inlineHandlerNames = new Set();
for (const [, handler] of source.matchAll(/\bon[a-z]+="([^"]*)"/g)) {
  for (const [, name] of handler.matchAll(/([A-Za-z_$][\w$]*)\s*\(/g)) inlineHandlerNames.add(name);
}

module.exports = [
  { ignores: ['eslint.config.js'] },
  js.configs.recommended,
  {
    files: ['**/*.js'],
    languageOptions: {
      sourceType: 'script',
      globals: {
        ...globals.browser,
        MODEL: 'readonly',
        cytoscape: 'readonly',
        cytoscapeDagre: 'readonly',
        MiniSearch: 'readonly',
      },
    },
    rules: {
      'no-unused-vars': ['error', { varsIgnorePattern: `^(${[...inlineHandlerNames].join('|')})$` }],
    },
  },
];
