/* ── Header ───────────────────────────────────────────────────────────────
 * Description: Checks how the flow runner skips steps whose flow condition or step condition is not met, and that
 *   a skipped step does not stop the run. Replaces global fetch with a stub, so no API is needed.
 * Usage: bash docs/api-collections/scripts/run-node.sh runner-when.test.mjs
 * Uses: flow-runner/page/js/collection.js and runner.js (the page's own modules), ./test-runner.mjs. No dependencies.
 * Env: None.
 * Input: None.
 * Outputs: one PASS or FAIL line per case, then a summary line; failures name the error when one was thrown.
 * Returns: exit code -- 0 when all cases pass, 1 otherwise.
 * ───────────────────────────────────────────────────────────────────────── */
import { parseCollection } from '../flow-runner/page/js/collection.js';
import { runFlow } from '../flow-runner/page/js/runner.js';
import { runCases } from './test-runner.mjs';

function makeStep(id, when = null) {
  return {
    name: id,
    request: {
      method: 'GET',
      url: { raw: '{{host}}/x' },
      description: `step: ${id}\nlabel: ${id}\nkind: optional${when === null ? '' : `\nwhen: ${when}`}\n\nStep ${id}.`,
    },
  };
}

const OPEN = { name: 'Open', description: 'Open flow.', item: [makeStep('o.one')] };
const GATED = {
  name: 'Gated',
  description: 'when: adminRights=yes\n\nGated flow.',
  item: [makeStep('g.one'), makeStep('g.two', 'other=on')],
};

const collection = parseCollection({ info: { name: 'when' }, item: [OPEN, GATED] });
const gatedFirst = parseCollection({ info: { name: 'when' }, item: [GATED, OPEN] });

globalThis.fetch = async () => new Response('{}', { status: 200 });

async function stepsById(model, variables) {
  const { steps } = await runFlow(model, { host: 'http://stub', variables });
  return Object.fromEntries(steps.map((step) => [step.id, step]));
}

const CASES = [
  {
    name: 'flow condition not met: o.one passes, both Gated steps skipped with the flow note',
    check: async () => {
      const steps = await stepsById(collection, { adminRights: 'no' });
      return steps['o.one'].status === 'passed'
        && steps['g.one'].status === 'skipped' && steps['g.one'].note === 'flow condition not met'
        && steps['g.two'].status === 'skipped' && steps['g.two'].note === 'flow condition not met';
    },
  },
  {
    name: 'step condition not met: g.one passes, g.two skipped with its own note',
    check: async () => {
      const steps = await stepsById(collection, { adminRights: 'yes' });
      return steps['g.one'].status === 'passed'
        && steps['g.two'].status === 'skipped' && steps['g.two'].note === 'condition not met';
    },
  },
  {
    name: 'all conditions met: all three steps pass',
    check: async () => {
      const steps = await stepsById(collection, { adminRights: 'yes', other: 'on' });
      return steps['o.one'].status === 'passed'
        && steps['g.one'].status === 'passed'
        && steps['g.two'].status === 'passed';
    },
  },
  {
    name: 'a skipped Gated flow does not stop the run: o.one after it still passes',
    check: async () => {
      const steps = await stepsById(gatedFirst, { adminRights: 'no' });
      return steps['g.one'].status === 'skipped'
        && steps['o.one'].status === 'passed';
    },
  },
];

await runCases(CASES);
