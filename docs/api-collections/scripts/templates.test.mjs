/* ── Header ───────────────────────────────────────────────────────────────
 * Description: Checks the built-in Administrator, Moderator and User templates against the role flow lists of
 *   the REST and MCP collections, the matching of a selection to template names, saving and loading named
 *   templates, and validation of the role templates. Uses the two real collections from the repository, so no API is needed.
 * Usage: bash docs/api-collections/scripts/run-node.sh templates.test.mjs
 * Uses: node:fs, flow-runner/page/js/collection.js and templates.js (the page's own modules), ./test-runner.mjs. No dependencies.
 * Env: None.
 * Input: None.
 * Outputs: one PASS or FAIL line per case, then a summary line; failures name the error when one was thrown.
 * Returns: exit code -- 0 when all cases pass, 1 otherwise.
 * ───────────────────────────────────────────────────────────────────────── */
import { readFileSync } from 'node:fs';
import { parseCollection } from '../flow-runner/page/js/collection.js';
import {
  builtIns,
  toConfig,
  validateConfig,
  matchingNames,
  loadSaved,
  saveTemplate,
} from '../flow-runner/page/js/templates.js';
import { runCases } from './test-runner.mjs';

const restCollection = parseCollection(JSON.parse(readFileSync(new URL('../postman/marketplace-rest-api.postman_collection.json', import.meta.url), 'utf8')));
const mcpCollection = parseCollection(JSON.parse(readFileSync(new URL('../postman/marketplace-mcp.postman_collection.json', import.meta.url), 'utf8')));

const same = (actual, expected) => JSON.stringify(actual) === JSON.stringify(expected);

const REST_USER_FLOWS = ['Authentication', 'Infrastructure', 'Profile', 'Advertisements', 'Account', 'Media', 'Contacts', 'Feedback'];
const REST_MODERATOR_FLOWS = ['Authentication', 'Taxons', 'Infrastructure', 'Profile', 'Advertisements', 'Account', 'Media', 'Contacts', 'Feedback', 'Moderation'];
const MCP_USER_FLOWS = ['Setup', 'Account', 'Advertisements', 'Media', 'Provider profiles', 'Contacts', 'Feedback', 'Users', 'API keys', 'Infrastructure', 'Close'];
const MCP_MODERATOR_FLOWS = ['Setup','Account', 'Advertisements', 'Media', 'Provider profiles', 'Contacts', 'Feedback', 'Users', 'API keys', 'Infrastructure', 'Taxons', 'Moderation', 'Close'];

const restBuiltin = builtIns(restCollection);
const mcpBuiltin = builtIns(mcpCollection);

const allFlowsOn = { flowsOff: [], stepsOff: [], overrides: {} };
const flowsOffOutside = (collection, keep) => ({
  flowsOff: collection.flows.map((flow) => flow.name).filter((name) => !keep.includes(name)),
  stepsOff: [],
  overrides: {},
});
const savedStorage = (initial) => {
  const map = new Map();
  if (initial !== undefined) {
    map.set('k', initial);
  }
  return { getItem: (key) => (map.has(key) ? map.get(key) : null), setItem: (key, value) => map.set(key, value) };
};
const settings = { storageKey: 'k', requiredFlow: 'Authentication' };

const CASES = [
  {
    name: 'REST builtIns key order is Administrator, Moderator, User',
    check: () => same(Object.keys(restBuiltin), ['Administrator', 'Moderator', 'User']),
  },
  {
    name: 'REST builtIns User flows = the User list',
    check: () => same(restBuiltin.User.flows, REST_USER_FLOWS),
  },
  {
    name: 'REST builtIns Moderator flows = the Moderator list',
    check: () => same(restBuiltin.Moderator.flows, REST_MODERATOR_FLOWS),
  },
  {
    name: 'REST builtIns Administrator flows = the Moderator list plus Users',
    check: () => same(restBuiltin.Administrator.flows, [...REST_MODERATOR_FLOWS, 'Users']),
  },
  {
    name: 'MCP builtIns User flows = the MCP User list',
    check: () => same(mcpBuiltin.User.flows, MCP_USER_FLOWS),
  },
  {
    name: 'MCP builtIns: Moderator adds Taxons and Moderation to the User flows, Administrator adds Administration',
    check: () => same(mcpBuiltin.User.flows, MCP_USER_FLOWS)
      && same(mcpBuiltin.Moderator.flows, MCP_MODERATOR_FLOWS)
      && same(mcpBuiltin.Administrator.flows, ['Setup', 'Account', 'Advertisements', 'Media', 'Provider profiles', 'Contacts', 'Feedback', 'Users', 'API keys', 'Infrastructure', 'Taxons', 'Moderation', 'Administration', 'Close']),
  },
  {
    name: 'matchingNames: REST all flows on gives Administrator',
    check: () => same(matchingNames(toConfig(allFlowsOn, restCollection), {}, restCollection, restBuiltin), ['Administrator']),
  },
  {
    name: 'matchingNames: MCP all flows on gives Administrator',
    check: () => same(matchingNames(toConfig(allFlowsOn, mcpCollection), {}, mcpCollection, mcpBuiltin), ['Administrator']),
  },
  {
    name: 'matchingNames: REST with Users off gives Moderator',
    check: () => same(
      matchingNames(toConfig({ ...allFlowsOn, flowsOff: ['Users'] }, restCollection), {}, restCollection, restBuiltin),
      ['Moderator'],
    ),
  },
  {
    name: 'matchingNames: REST with everything outside the User list plus Media off gives Custom',
    check: () => {
      const state = flowsOffOutside(restCollection, REST_USER_FLOWS.filter((name) => name !== 'Media'));
      return same(matchingNames(toConfig(state, restCollection), {}, restCollection, restBuiltin), ['Custom']);
    },
  },
  {
    name: 'matchingNames: saved template equal to User gives User, mine',
    check: () => {
      const state = flowsOffOutside(restCollection, REST_USER_FLOWS);
      return same(
        matchingNames(toConfig(state, restCollection), { mine: restBuiltin.User }, restCollection, restBuiltin),
        ['User', 'mine'],
      );
    },
  },
  {
    name: 'saveTemplate: reserved role names and Custom return an error',
    check: () => ['Administrator', 'Moderator', 'User', 'Custom'].every(
      (name) => saveTemplate(savedStorage(), restCollection, name, restBuiltin.User, settings).error !== undefined,
    ),
  },
  {
    name: 'saveTemplate: a name such as check is saved',
    check: () => saveTemplate(savedStorage(), restCollection, 'check', restBuiltin.User, settings).saved !== undefined,
  },
  {
    name: 'loadSaved: a template stored under the old name Everything loads as an ordinary template',
    check: () => {
      const stored = JSON.stringify({ version: 1, templates: { Everything: restBuiltin.Administrator } });
      const saved = loadSaved(savedStorage(stored), restCollection, settings);
      return same(Object.keys(saved), ['Everything']) && same(saved.Everything, restBuiltin.Administrator);
    },
  },
  {
    name: 'validateConfig: each REST role template validates with requiredFlow Authentication',
    check: () => ['Administrator', 'Moderator', 'User'].every(
      (name) => validateConfig(JSON.stringify(restBuiltin[name]), restCollection, 'Authentication').error === undefined,
    ),
  },
  {
    name: 'on both collections User is narrower than Moderator and Moderator than Administrator',
    check: () => [restBuiltin, mcpBuiltin].every(
      (builtin) => builtin.User.flows.length < builtin.Moderator.flows.length
        && builtin.Moderator.flows.length < builtin.Administrator.flows.length,
    ),
  },
];

await runCases(CASES);
