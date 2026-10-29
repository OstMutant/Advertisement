/* ── Header ───────────────────────────────────────────────────────────────
 * Description: Checks how the flow runner treats a 429 response: a step whose passing test expects
 *   429 passes, while a 429 that no passing test expected fails the step with the Retry-After
 *   message. Replaces global fetch with a stub, so no API is needed.
 * Usage: bash docs/api-collections/scripts/run-node.sh runner-429.test.mjs
 * Uses: flow-runner/page/js/collection.js and runner.js (the page's own modules). No dependencies.
 * Env: None.
 * Input: None.
 * Outputs: one PASS or FAIL line per case, then a summary line; failures name the actual step state.
 * Returns: exit code -- 0 when all cases pass, 1 otherwise.
 * ───────────────────────────────────────────────────────────────────────── */
import { parseCollection } from '../flow-runner/page/js/collection.js';
import { runFlow } from '../flow-runner/page/js/runner.js';

function collectionWith(testLines) {
  return parseCollection({
    info: { name: 'rate limit' },
    item: [
      {
        name: 'limited',
        request: { method: 'GET', url: { raw: '{{host}}/limited' } },
        event: testLines === null ? [] : [{ listen: 'test', script: { exec: testLines } }],
      },
    ],
  });
}

const CASES = [
  {
    name: 'a step that asserts 429 passes',
    tests: ["pm.test('rate limited', () => pm.response.to.have.status(429));"],
    check: (step) => step.status === 'passed' && step.error === null,
  },
  {
    name: 'a step that asserts 200 fails with the Retry-After message',
    tests: ["pm.test('ok', () => pm.response.to.have.status(200));"],
    check: (step) => step.status === 'failed' && step.error !== null && step.error.includes('retry after 30'),
  },
  {
    name: 'a step without tests fails with the Retry-After message',
    tests: null,
    check: (step) => step.status === 'failed' && step.error !== null && step.error.includes('retry after 30'),
  },
];

globalThis.fetch = async () => new Response('slow down', { status: 429, headers: { 'Retry-After': '30' } });

let failures = 0;
for (const testCase of CASES) {
  const { steps } = await runFlow(collectionWith(testCase.tests), { host: 'http://stub' });
  const step = steps[0];
  const ok = testCase.check(step);
  failures += ok ? 0 : 1;
  console.log(`${ok ? 'PASS' : 'FAIL'} ${testCase.name}${ok ? '' : ` (status=${step.status}, error=${step.error})`}`);
}
console.log(`${CASES.length - failures}/${CASES.length} passed`);
process.exit(failures === 0 ? 0 : 1);
