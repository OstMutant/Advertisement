/* ── Header ───────────────────────────────────────────────────────────────
 * Description: Checks the role derivation of roles.js and the helper flag, flow note and flow condition that
 *   parseCollection reads. Uses a synthetic collection plus the REST and the MCP collection from the repository, so no API is needed.
 * Usage: bash docs/api-collections/scripts/run-node.sh roles.test.mjs
 * Uses: node:fs, flow-runner/page/js/collection.js and roles.js (the page's own modules), ./test-runner.mjs. No dependencies.
 * Env: None.
 * Input: the REST and the MCP Postman collection (postman/marketplace-rest-api.postman_collection.json and postman/marketplace-mcp.postman_collection.json).
 * Outputs: one PASS or FAIL line per case, then a summary line; failures name the error when one was thrown.
 * Returns: exit code -- 0 when all cases pass, 1 otherwise.
 * ───────────────────────────────────────────────────────────────────────── */
import { readFileSync } from 'node:fs';
import { parseCollection } from '../flow-runner/page/js/collection.js';
import {
  ROLES,
  roleOfToken,
  flowRole,
  flowsForRole,
  flowsNeedingAdministrator,
  adminSkipMessage,
  roleGaps,
} from '../flow-runner/page/js/roles.js';
import { runCases } from './test-runner.mjs';

function makeStep(id, auth, helper = false) {
  return {
    name: id,
    request: {
      method: 'GET',
      url: { raw: '{{host}}/x' },
      auth,
      description: `step: ${id}\nlabel: ${id}\nkind: optional${helper ? '\nhelper: yes' : ''}\n\nStep ${id}.`,
    },
  };
}

function makeFolder(name, description, item) {
  return { name, description, item };
}

const bearer = (token) => ({ type: 'bearer', bearer: [{ key: 'token', value: token }] });
const BASIC = { type: 'basic', basic: [{ key: 'username', value: 'user' }, { key: 'password', value: 'pass' }] };
const NOAUTH = { type: 'noauth' };

const collection = parseCollection({
  info: { name: 'roles' },
  item: [
    makeFolder('Plain', 'Plain work.', [
      makeStep('a.one', bearer('{{userKey}}')),
      makeStep('a.two', BASIC),
      makeStep('a.three', NOAUTH),
    ]),
    makeFolder('Staff', 'when: adminRights=yes\n\nStaff work.', [
      makeStep('s.prep', bearer('{{adminKey}}'), true),
      makeStep('s.work', bearer('{{moderatorKey}}')),
      makeStep('s.deny', bearer('{{userKey}}')),
    ]),
    makeFolder('Admin', 'when: adminRights=yes\n\nAdmin work.', [
      makeStep('d.one', bearer('{{adminKey}}')),
      makeStep('d.two', bearer('{{userKey}}')),
    ]),
    makeFolder('OnlyHelper', 'Helper only.', [makeStep('h.one', bearer('{{adminKey}}'), true)]),
  ],
});

const restCollection = parseCollection(JSON.parse(readFileSync(new URL('../postman/marketplace-rest-api.postman_collection.json', import.meta.url), 'utf8')));
const mcpCollection = parseCollection(JSON.parse(readFileSync(new URL('../postman/marketplace-mcp.postman_collection.json', import.meta.url), 'utf8')));

const same = (actual, expected) => JSON.stringify(actual) === JSON.stringify(expected);

const CASES = [
  {
    name: 'roleOfToken gives user, moderator, administrator, and user for undefined and empty',
    check: () => same(
      [roleOfToken('{{userKey}}'), roleOfToken('{{moderatorKey}}'), roleOfToken('{{adminKey}}'), roleOfToken(undefined), roleOfToken('')],
      ['user', 'moderator', 'administrator', 'user', 'user'],
    ),
  },
  {
    name: 'flowRole gives user, moderator, administrator, user (helper ignored)',
    check: () => same(
      [flowRole(collection, 'Plain'), flowRole(collection, 'Staff'), flowRole(collection, 'Admin'), flowRole(collection, 'OnlyHelper')],
      ['user', 'moderator', 'administrator', 'user'],
    ),
  },
  {
    name: 'flowsForRole user = Plain, OnlyHelper',
    check: () => same(flowsForRole(collection, 'user'), ['Plain', 'OnlyHelper']),
  },
  {
    name: 'flowsForRole moderator = Plain, Staff, OnlyHelper',
    check: () => same(flowsForRole(collection, 'moderator'), ['Plain', 'Staff', 'OnlyHelper']),
  },
  {
    name: 'flowsForRole administrator = all four flows in order',
    check: () => same(flowsForRole(collection, 'administrator'), ['Plain', 'Staff', 'Admin', 'OnlyHelper']),
  },
  {
    name: 'flowsForRole throws for an unknown role and names the three roles',
    check: () => {
      try {
        flowsForRole(collection, 'owner');
        return false;
      } catch (error) {
        return ROLES.every((role) => error.message.includes(role));
      }
    },
  },
  {
    name: 'flowsNeedingAdministrator = Staff, Admin',
    check: () => same(flowsNeedingAdministrator(collection), ['Staff', 'Admin']),
  },
  {
    name: 'parseCollection keeps the folder note without the header line',
    check: () => collection.flows.find((flow) => flow.name === 'Staff').note === 'Staff work.',
  },
  {
    name: 'parseCollection sets helper true only on s.prep and h.one',
    check: () => same(collection.items.filter((item) => item.helper).map((item) => item.stepId), ['s.prep', 'h.one']),
  },
  {
    name: 'a Staff item has flowWhen adminRights=yes and a Plain item has null',
    check: () => same(collection.items.find((item) => item.flow === 'Staff').flowWhen, { variable: 'adminRights', value: 'yes' })
      && collection.items.find((item) => item.flow === 'Plain').flowWhen === null,
  },
  {
    name: 'real collection: flowsForRole user',
    check: () => same(flowsForRole(restCollection, 'user'), ['Authentication', 'Infrastructure', 'Profile', 'Advertisements', 'Account', 'Media', 'Contacts', 'Feedback']),
  },
  {
    name: 'real collection: flowsForRole moderator',
    check: () => same(flowsForRole(restCollection, 'moderator'), ['Authentication', 'Taxons', 'Infrastructure', 'Profile', 'Advertisements', 'Account', 'Media', 'Contacts', 'Feedback', 'Moderation']),
  },
  {
    name: 'real collection: flowsForRole administrator',
    check: () => same(flowsForRole(restCollection, 'administrator'), ['Authentication', 'Taxons', 'Infrastructure', 'Profile', 'Advertisements', 'Account', 'Media', 'Contacts', 'Feedback', 'Moderation', 'Users']),
  },
  {
    name: 'real collection: flowsNeedingAdministrator',
    check: () => same(flowsNeedingAdministrator(restCollection), ['Taxons', 'Profile', 'Advertisements', 'Media', 'Contacts', 'Feedback', 'Moderation', 'Users']),
  },
  {
    name: 'adminSkipMessage names the account and the flows',
    check: () => adminSkipMessage('a@b.c', ['Taxons', 'Moderation']) === 'the default account a@b.c has no administrator rights, so these flows were skipped: Taxons, Moderation.',
  },
  {
    name: 'roleGaps reports an operation exercised at a lower role than the reference',
    check: () => same(
      roleGaps(
        parseCollection({
          info: { name: 'reference' },
          item: [makeFolder('Users', 'Users work.', [
            makeStep('deleteUser', bearer('{{adminKey}}')),
            makeStep('listUsers', bearer('{{moderatorKey}}')),
            makeStep('getUser', bearer('{{userKey}}')),
          ])],
        }),
        parseCollection({
          info: { name: 'tested' },
          item: [makeFolder('Users', 'Users work.', [
            makeStep('deleteUser', bearer('{{userKey}}')),
            makeStep('listUsers', bearer('{{moderatorKey}}')),
            makeStep('getUser', bearer('{{adminKey}}'), true),
          ])],
        }),
      ),
      [{ operation: 'deleteUser', expected: 'administrator', actual: 'user' }],
    ),
  },
  {
    name: 'real collections: the MCP collection exercises every shared operation at least at the REST role',
    check: () => same(roleGaps(restCollection, mcpCollection), []),
  },
];

await runCases(CASES);
