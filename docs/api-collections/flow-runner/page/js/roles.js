/* ── Header ───────────────────────────────────────────────────────────────────
 * Description: Derives the role of a flow from the keys its steps use and the list of flows each role can run.
 * Usage: None -- a library module imported by main.js, flow-map.js, templates.js, scripts/run-flows.mjs and scripts/check-collection.mjs.
 * Uses: None.
 * Env: None.
 * Input: A collection model from parseCollection and a role name.
 * Outputs: Exports ROLES, ADMIN_KEY, MODERATOR_KEY, ADMIN_RIGHTS, KEY_OF_ROLE, roleOfToken, flowRole, flowsForRole,
 *   flowsNeedingAdministrator, adminSkipMessage, highestRoleByOperation and roleGaps; no DOM, storage or network access.
 * Returns: N/A
 * ────────────────────────────────────────────────────────────────────────── */

export const ROLES = ["user", "moderator", "administrator"];
export const ADMIN_KEY = "adminKey";
export const MODERATOR_KEY = "moderatorKey";
export const ADMIN_RIGHTS = "adminRights";
export const KEY_OF_ROLE = { administrator: ADMIN_KEY, moderator: MODERATOR_KEY };

/**
 * Maps a bearer token template to the role whose key it references.
 * Arguments: token -- raw token template (for example {{adminKey}}), a string or undefined.
 * Outputs: None.
 * Returns: "administrator" when the token contains {{adminKey}}, "moderator" when it contains {{moderatorKey}}, otherwise "user".
 */
export function roleOfToken(token) {
  if (token?.includes(`{{${ADMIN_KEY}}}`)) {
    return "administrator";
  }
  if (token?.includes(`{{${MODERATOR_KEY}}}`)) {
    return "moderator";
  }
  return "user";
}

/**
 * Derives the highest role among a flow's non-helper steps.
 * Arguments: collection -- model from parseCollection, flow -- flow folder name.
 * Outputs: None.
 * Returns: The highest role (by position in ROLES) of the flow's non-helper items, or "user" when there is none.
 */
export function flowRole(collection, flow) {
  return collection.items
    .filter((item) => item.flow === flow && !item.helper)
    .map((item) => roleOfToken(item.auth.type === "bearer" ? item.auth.token : ""))
    .reduce((highest, role) => (ROLES.indexOf(role) > ROLES.indexOf(highest) ? role : highest), "user");
}

/**
 * Lists the flows a role is allowed to run, in collection order.
 * Arguments: collection -- model from parseCollection, role -- one of ROLES.
 * Outputs: Throws an Error when role is not in ROLES.
 * Returns: Array of flow names whose flowRole is at or below the given role.
 */
export function flowsForRole(collection, role) {
  if (!ROLES.includes(role)) {
    throw new Error(`unknown role '${role}', available: ${ROLES.join(", ")}`);
  }
  const limit = ROLES.indexOf(role);
  return collection.flows
    .map((flow) => flow.name)
    .filter((name) => ROLES.indexOf(flowRole(collection, name)) <= limit);
}

/**
 * Lists the flows whose condition requires the administrator-rights switch.
 * Arguments: collection -- model from parseCollection.
 * Outputs: None.
 * Returns: Array of flow names whose when condition is adminRights=yes, in collection order.
 */
export function flowsNeedingAdministrator(collection) {
  return collection.flows
    .filter((flow) => flow.when?.variable === ADMIN_RIGHTS && flow.when?.value === "yes")
    .map((flow) => flow.name);
}

/**
 * Builds the note shown when flows were skipped because the default account lacks administrator rights.
 * Arguments: email -- e-mail of the default account, flowNames -- array of skipped flow names.
 * Outputs: None.
 * Returns: The one-sentence note naming the account and the skipped flows.
 */
export function adminSkipMessage(email, flowNames) {
  return `the default account ${email} has no administrator rights, so these flows were skipped: ${flowNames.join(", ")}.`;
}

/**
 * Maps each non-helper item name to the highest role that exercises it.
 * Arguments: collection -- model from parseCollection.
 * Outputs: None.
 * Returns: Map from item name to the highest role (by position in ROLES) among the collection's non-helper items with that name, in order of first appearance.
 */
export function highestRoleByOperation(collection) {
  const highest = new Map();
  collection.items
    .filter((item) => !item.helper)
    .forEach((item) => {
      const role = roleOfToken(item.auth.type === "bearer" ? item.auth.token : "");
      const current = highest.get(item.name);
      if (current === undefined || ROLES.indexOf(role) > ROLES.indexOf(current)) {
        highest.set(item.name, role);
      }
    });
  return highest;
}

/**
 * Lists the operations the collection exercises at a lower role than the reference collection does.
 * Arguments: reference -- model from parseCollection, collection -- model from parseCollection.
 * Outputs: None.
 * Returns: Array of { operation, expected, actual } for each name present in both collections whose role in collection is below its role in reference (expected = the reference role, actual = the collection role), in the order the names appear in collection.
 */
export function roleGaps(reference, collection) {
  const referenceRoles = highestRoleByOperation(reference);
  const collectionRoles = highestRoleByOperation(collection);
  return [...collectionRoles.keys()]
    .filter((operation) => referenceRoles.has(operation))
    .filter((operation) => ROLES.indexOf(collectionRoles.get(operation)) < ROLES.indexOf(referenceRoles.get(operation)))
    .map((operation) => ({ operation, expected: referenceRoles.get(operation), actual: collectionRoles.get(operation) }));
}
