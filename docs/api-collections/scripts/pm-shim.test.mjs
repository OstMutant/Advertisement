/* ── Header ───────────────────────────────────────────────────────────────
 * Description: Checks the pm shim's response access in test scripts: pm.response.headers.get matches a
 *   header name case-insensitively, pm.response.text returns the raw body, an unknown header gives
 *   undefined, and any other headers member fails the step as an unsupported pm call. Replaces
 *   global fetch with a stub, so no API is needed.
 * Usage: bash docs/api-collections/scripts/run-node.sh pm-shim.test.mjs
 * Uses: flow-runner/page/js/collection.js and runner.js (the page's own modules), ./test-runner.mjs. No dependencies.
 * Env: None.
 * Input: None.
 * Outputs: one PASS or FAIL line per case, then a summary line; failures name the actual step state.
 * Returns: exit code -- 0 when all cases pass, 1 otherwise.
 * ───────────────────────────────────────────────────────────────────────── */
import { parseCollection } from '../flow-runner/page/js/collection.js';
import { runFlow } from '../flow-runner/page/js/runner.js';
import { runCases } from './test-runner.mjs';

const BODY = 'event: message\ndata: {"ok":true}\n\n';

function collectionWith(testLines) {
  return parseCollection({
    info: { name: 'pm shim' },
    item: [
      {
        name: 'answer',
        request: { method: 'GET', url: { raw: '{{host}}/answer' } },
        event: [{ listen: 'test', script: { exec: testLines } }],
      },
    ],
  });
}

const CASES = [
  {
    name: 'headers.get reads a header ignoring case and stores it',
    tests: ["pm.collectionVariables.set('sid', pm.response.headers.get('Mcp-Session-Id'));"],
    check: (step, variables) => step.status === 'passed' && variables.sid === 'session-1',
  },
  {
    name: 'text returns the raw event-stream body',
    tests: [`pm.test('raw', () => pm.expect(pm.response.text()).to.include('data: {"ok":true}'));`],
    check: (step) => step.status === 'passed' && step.error === null,
  },
  {
    name: 'an unknown header gives undefined without failing the step',
    tests: ["pm.test('absent', () => { if (pm.response.headers.get('X-Missing') !== undefined) throw new Error('defined'); });"],
    check: (step) => step.status === 'passed' && step.error === null,
  },
  {
    name: 'an unsupported headers member fails the step',
    tests: ["pm.response.headers.foo;"],
    check: (step) => step.status === 'failed' && step.error !== null && step.error.includes('unsupported pm call'),
  },
];

globalThis.fetch = async () =>
  new Response(BODY, { status: 200, headers: { 'content-type': 'text/event-stream', 'mcp-session-id': 'session-1' } });

await runCases(CASES, async (testCase) => {
  const { steps, variables } = await runFlow(collectionWith(testCase.tests), { host: 'http://stub' });
  const step = steps[0];
  return { ok: testCase.check(step, variables), detail: ` (status=${step.status}, error=${step.error})` };
});
