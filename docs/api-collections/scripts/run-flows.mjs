/* ── Header ───────────────────────────────────────────────────────────────
 * Description: Runs the flows of the Postman collection against a running API, using the same
 *   runner as the developer-only flow runner page. Steps run in collection order; a failed step
 *   skips every later step. Creates data in the app (users, API keys), so run it against a dev
 *   database.
 * Usage: node docs/api-collections/scripts/run-flows.mjs [baseUrl] [--config <file>] [--flow <name>]...
 *     [--include <stepId>]... [--variable key=value]... [--json] [--help]
 *   [baseUrl]          API base URL; takes precedence over API_BASE_URL and the config's baseUrl
 *   --config <file>    config file (default: check-collection.config.json next to this script)
 *   --flow <name>      run only the named flow; repeat to run several; a dependent flow needs the
 *                      flow it depends on too
 *   --include <stepId> run a step that is off by default; repeat for several
 *   --variable k=v     override a collection variable; repeatable
 *   --json             print only a JSON array of step results
 *   --help             print the usage text and exit
 * Uses: node:fs, node:path, node:url, flow-runner/page/js/collection.js and runner.js (the page's
 *   own modules, reused unchanged). No dependencies.
 * Env: API_BASE_URL -- base URL used when no positional argument is given.
 * Input: the config file (collectionFile and baseUrl; relative paths resolve against the config's
 *   directory) and the Postman collection it names.
 * Outputs: one line per step with its id, name, status and HTTP code, the error and failed test
 *   lines for failed steps, then a summary line; with --json, only the JSON step array on stdout.
 *   Errors are printed on stderr.
 * Returns: exit code -- 0 when no step failed, 1 when at least one step failed, 2 for a usage,
 *   config or read error, or when no step is selected.
 * ───────────────────────────────────────────────────────────────────────── */
import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { parseCollection } from '../flow-runner/page/js/collection.js';
import { runFlow } from '../flow-runner/page/js/runner.js';

const SCRIPT_DIR = dirname(fileURLToPath(import.meta.url));
const DEFAULT_CONFIG = resolve(SCRIPT_DIR, 'check-collection.config.json');
const REQUIRED_FIELDS = ['baseUrl', 'collectionFile'];

const USAGE = `Usage: node docs/api-collections/scripts/run-flows.mjs [baseUrl] [--config <file>] [--flow <name>]...
                                              [--include <stepId>]... [--variable key=value]... [--json] [--help]

Runs the Postman collection's flows against the running API.

Options:
  [baseUrl]          API base URL; takes precedence over API_BASE_URL and the config's baseUrl
  --config <file>    config file (default: check-collection.config.json next to this script)
  --flow <name>      run only this flow; repeat for several (a dependent flow needs its
                     prerequisite flow too, e.g. --flow Authentication --flow <other>)
  --include <stepId> run a step that is off by default; repeat for several
  --variable k=v     override a collection variable; repeat for several
  --json             print only the step results as JSON
  --help             print this text and exit

Config fields:
  baseUrl, collectionFile (other fields are read by check-collection.mjs)

Note: runs create users and API keys in the app; use a dev database.

Exit codes:
  0  no step failed
  1  at least one step failed
  2  usage, config or read error, or no steps selected`;

function parseArgs(argv) {
  const options = { baseUrl: undefined, configFile: DEFAULT_CONFIG, flows: [], include: [], variables: {}, json: false, help: false };
  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i];
    if (arg === '--help') {
      options.help = true;
    } else if (arg === '--json') {
      options.json = true;
    } else if (arg === '--config') {
      if (argv[i + 1] === undefined) throw new UsageError('--config needs a file name');
      options.configFile = resolve(argv[++i]);
    } else if (arg === '--flow') {
      if (argv[i + 1] === undefined) throw new UsageError('--flow needs a flow name');
      options.flows.push(argv[++i]);
    } else if (arg === '--include') {
      if (argv[i + 1] === undefined) throw new UsageError('--include needs a step id');
      options.include.push(argv[++i]);
    } else if (arg === '--variable') {
      if (argv[i + 1] === undefined) throw new UsageError('--variable needs key=value');
      const [key, ...rest] = argv[++i].split('=');
      if (rest.length === 0 || key === '') throw new UsageError(`--variable expects key=value, got '${argv[i]}'`);
      options.variables[key] = rest.join('=');
    } else if (arg.startsWith('--')) {
      throw new UsageError(`unknown option ${arg}`);
    } else {
      options.baseUrl = arg;
    }
  }
  return options;
}

class UsageError extends Error {}

function readJson(file) {
  let text;
  try {
    text = readFileSync(file, 'utf8');
  } catch (err) {
    throw new UsageError(`cannot read ${file}: ${err.message}`);
  }
  try {
    return JSON.parse(text);
  } catch (err) {
    throw new UsageError(`cannot read ${file}: ${err.message}`);
  }
}

function loadConfig(file) {
  const config = readJson(file);
  for (const field of REQUIRED_FIELDS) {
    if (typeof config[field] !== 'string' || config[field] === '') {
      throw new UsageError(`config: ${field} is missing`);
    }
  }
  return { ...config, collectionFile: resolve(dirname(file), config.collectionFile) };
}

function validateIncludes(collection, ids) {
  const available = collection.items.map((item) => item.stepId);
  for (const id of ids) {
    if (!available.includes(id)) {
      throw new UsageError(`unknown step '${id}', available: ${available.join(', ')}`);
    }
  }
}

function selectFlows(collection, names) {
  if (names.length === 0) return collection;
  const available = collection.flows.map((flow) => flow.name);
  for (const name of names) {
    if (!available.includes(name)) {
      throw new UsageError(`unknown flow '${name}', available: ${available.join(', ')}`);
    }
  }
  return { ...collection, items: collection.items.filter((item) => names.includes(item.flow)) };
}

function stepLine(step, index) {
  const code = step.response.code === null ? '' : ` (${step.response.code})`;
  const note = step.status === 'skipped' && step.note ? ` (${step.note})` : '';
  return `${index + 1}. ${step.id} "${step.name}" ${step.status}${code}${note}`;
}

function failureLines(step) {
  const lines = [];
  if (step.error) lines.push(`    error: ${step.error}`);
  for (const test of step.tests.filter((t) => !t.passed)) {
    lines.push(`    failed test: ${test.name} - ${test.error}`);
  }
  return lines;
}

function summaryLine(steps) {
  const count = (status) => steps.filter((step) => step.status === status).length;
  return `${steps.length} steps: ${count('passed')} passed, ${count('failed')} failed, ${count('skipped')} skipped`;
}

function jsonRows(steps) {
  return steps.map(({ id, name, status, response, durationMs, error, tests }) => ({
    id,
    name,
    status,
    code: response.code,
    durationMs,
    error,
    tests,
  }));
}

async function main() {
  const options = parseArgs(process.argv.slice(2));
  if (options.help) {
    console.log(USAGE);
    return 0;
  }
  const config = loadConfig(options.configFile);
  const baseUrl = (options.baseUrl ?? process.env.API_BASE_URL ?? config.baseUrl).replace(/\/+$/, '');
  const fullCollection = parseCollection(readJson(config.collectionFile));
  validateIncludes(fullCollection, options.include);
  const collection = selectFlows(fullCollection, options.flows);
  if (collection.items.length === 0) {
    console.error('no steps to run');
    return 2;
  }
  const { steps } = await runFlow(collection, { host: baseUrl, variables: options.variables, include: options.include });

  if (options.json) {
    console.log(JSON.stringify(jsonRows(steps), null, 2));
  } else {
    steps.forEach((step, index) => {
      console.log(stepLine(step, index));
      if (step.status === 'failed') failureLines(step).forEach((line) => console.log(line));
    });
    console.log(summaryLine(steps));
  }
  return steps.some((step) => step.status === 'failed') ? 1 : 0;
}

main().then(
  (code) => process.exit(code),
  (err) => {
    console.error(err.message);
    process.exit(2);
  },
);
