/* ── Header ───────────────────────────────────────────────────────────────────
 * Description: Template model of the runner page: the built-in Administrator, Moderator and User templates,
 *   conversion between the selection state and a versioned configuration object, validation of
 *   pasted configuration JSON, and saving, loading and deleting named templates under one storage key.
 * Usage: None -- a library module imported by templates-bar.js; scripts/templates.test.mjs also imports it.
 * Uses: ./roles.js (ROLES, flowsForRole).
 * Env: None.
 * Input: A collection, selection state or configuration text, a localStorage-like storage object and
 *   the page settings (requiredFlow, storageKey) passed to the exported functions.
 * Outputs: Exports builtIns, toConfig, fromConfig, validateConfig, matchingNames, loadSaved,
 *   saveTemplate and deleteTemplate. saveTemplate and deleteTemplate write the templates to the
 *   storage object under the storageKey setting. No DOM access.
 * Returns: N/A
 * ────────────────────────────────────────────────────────────────────────── */

import { ROLES, flowsForRole } from "./roles.js";

const VERSION = 1;
const NAME_LIMIT = 40;
const RESERVED_NAMES = [...ROLES.map(templateName), "Custom", "__proto__"];

function templateName(role) {
  return role[0].toUpperCase() + role.slice(1);
}

/**
 * Builds the built-in Administrator, Moderator and User templates for a collection.
 * Arguments: collection -- model from parseCollection.
 * Outputs: None.
 * Returns: Object keyed by template name in the order Administrator, Moderator, User, each with version, flows (from flowsForRole), stepsOff [] and overrides {}.
 */
export function builtIns(collection) {
  return Object.fromEntries(
    [...ROLES].reverse().map((role) => [
      templateName(role),
      { version: VERSION, flows: flowsForRole(collection, role), stepsOff: [], overrides: {} },
    ]),
  );
}

/**
 * Converts a selection state into a versioned configuration object.
 * Arguments: state -- selection state with flowsOff, stepsOff and overrides; collection -- model from parseCollection.
 * Outputs: None.
 * Returns: Object with version, flows (flow names not switched off, in collection order), stepsOff (step ids in collection order) and overrides (sorted by name).
 */
export function toConfig(state, collection) {
  return {
    version: VERSION,
    flows: collection.flows.map((flow) => flow.name).filter((name) => !state.flowsOff.includes(name)),
    stepsOff: collection.items.map((item) => item.stepId).filter((id) => state.stepsOff.includes(id)),
    overrides: Object.fromEntries(Object.entries(state.overrides).sort(([a], [b]) => a.localeCompare(b))),
  };
}

/**
 * Converts a versioned configuration object into a selection state.
 * Arguments: config -- versioned configuration object; collection -- model from parseCollection.
 * Outputs: None.
 * Returns: Object with flowsOff (flows missing from config.flows), stepsOff and overrides copied from config.
 */
export function fromConfig(config, collection) {
  return {
    flowsOff: collection.flows.map((flow) => flow.name).filter((name) => !config.flows.includes(name)),
    stepsOff: [...config.stepsOff],
    overrides: { ...config.overrides },
  };
}

/**
 * Parses configuration JSON text and checks it against the collection.
 * Arguments: text -- configuration JSON text; collection -- model from parseCollection; requiredFlow -- flow name that must stay on.
 * Outputs: None.
 * Returns: {error} with the reason when the text is not valid JSON or fails a check, otherwise {config} with the normalized configuration.
 */
export function validateConfig(text, collection, requiredFlow) {
  let parsed;
  try {
    parsed = JSON.parse(text);
  } catch (error) {
    return { error: `not valid JSON: ${error.message}` };
  }
  return checkConfig(parsed, collection, requiredFlow);
}

/**
 * Lists the template names whose configuration equals the given one.
 * Arguments: config -- versioned configuration object; saved -- saved templates object; collection -- model from parseCollection; builtin -- built-in templates object.
 * Outputs: None.
 * Returns: Array of every matching name, built-in names first, or ["Custom"] when none matches.
 */
export function matchingNames(config, saved, collection, builtin) {
  const candidates = { ...builtin, ...saved };
  const wanted = JSON.stringify(config);
  const found = Object.keys(candidates).filter(
    (name) => JSON.stringify(toConfig(fromConfig(candidates[name], collection), collection)) === wanted,
  );
  return found.length > 0 ? found : ["Custom"];
}

/**
 * Reads the saved templates from storage, keeping only entries that pass the configuration checks.
 * Arguments: storage -- localStorage-like object; collection -- model from parseCollection; settings -- {storageKey, requiredFlow}.
 * Outputs: None (reads the storage object only).
 * Returns: Object of saved templates by name; an empty object when the stored data is missing, unreadable or of another version.
 */
export function loadSaved(storage, collection, { storageKey, requiredFlow }) {
  try {
    const parsed = JSON.parse(storage.getItem(storageKey) ?? "null");
    if (parsed === null || parsed.version !== VERSION || typeof parsed.templates !== "object" || parsed.templates === null) {
      return {};
    }
    const saved = {};
    for (const [name, config] of Object.entries(parsed.templates)) {
      const checked = checkConfig(config, collection, requiredFlow);
      if (nameError(name) === null && checked.config !== undefined) {
        saved[name] = checked.config;
      }
    }
    return saved;
  } catch {
    return {};
  }
}

/**
 * Validates a template name and stores the configuration under it.
 * Arguments: storage -- localStorage-like object; collection -- model from parseCollection; name -- template name (trimmed before use); config -- versioned configuration object; settings -- {storageKey, requiredFlow}.
 * Outputs: writes all saved templates to the storage object under settings.storageKey.
 * Returns: {saved} with the saved templates after the write, or {error} when the name is invalid or the storage write fails.
 */
export function saveTemplate(storage, collection, name, config, settings) {
  const trimmed = name.trim();
  const error = nameError(trimmed);
  if (error !== null) {
    return { error };
  }
  const saved = loadSaved(storage, collection, settings);
  saved[trimmed] = config;
  return store(storage, saved, settings.storageKey);
}

/**
 * Removes a saved template from storage.
 * Arguments: storage -- localStorage-like object; collection -- model from parseCollection; name -- saved template name; settings -- {storageKey, requiredFlow}.
 * Outputs: writes the remaining saved templates to the storage object under settings.storageKey.
 * Returns: {saved} with the remaining templates, or {error} when the storage write fails.
 */
export function deleteTemplate(storage, collection, name, settings) {
  const saved = loadSaved(storage, collection, settings);
  delete saved[name];
  return store(storage, saved, settings.storageKey);
}

function store(storage, saved, storageKey) {
  try {
    storage.setItem(storageKey, JSON.stringify({ version: VERSION, templates: saved }));
  } catch {
    return { error: "storage is not available" };
  }
  return { saved };
}

function nameError(name) {
  if (name === "") {
    return "enter a template name";
  }
  if (name.length > NAME_LIMIT) {
    return `the name is longer than ${NAME_LIMIT} characters`;
  }
  if (RESERVED_NAMES.includes(name)) {
    return "this name is reserved";
  }
  return null;
}

function checkConfig(parsed, collection, requiredFlow) {
  if (parsed === null || typeof parsed !== "object" || Array.isArray(parsed)) {
    return { error: "configuration must be a JSON object" };
  }
  if (parsed.version !== VERSION) {
    return { error: `configuration version must be ${VERSION}` };
  }
  const flowNames = collection.flows.map((flow) => flow.name);
  if (!Array.isArray(parsed.flows) || parsed.flows.some((name) => !flowNames.includes(name))) {
    return { error: "flows must be a list of known flow names" };
  }
  if (!parsed.flows.includes(requiredFlow)) {
    return { error: `${requiredFlow} cannot be left out` };
  }
  if (!Array.isArray(parsed.stepsOff)) {
    return { error: "stepsOff must be a list of step ids" };
  }
  for (const id of parsed.stepsOff) {
    const item = collection.items.find((candidate) => candidate.stepId === id);
    if (item === undefined) {
      return { error: `unknown step: ${id}` };
    }
    if (item.kind === "required" || item.when !== null) {
      return { error: `step ${id} cannot be switched off` };
    }
  }
  const overrides = parsed.overrides ?? {};
  if (typeof overrides !== "object" || overrides === null || Array.isArray(overrides)) {
    return { error: "overrides must be an object" };
  }
  const allowed = [...Object.keys(collection.variables), "host", "runId"];
  const values = {};
  for (const [key, value] of Object.entries(overrides)) {
    if (!allowed.includes(key)) {
      return { error: `unknown setting: ${key}` };
    }
    if (typeof value !== "string" && typeof value !== "number") {
      return { error: `setting ${key} must be a string or a number` };
    }
    values[key] = String(value);
  }
  return { config: { version: VERSION, flows: parsed.flows, stepsOff: parsed.stepsOff, overrides: values } };
}
