/* ── Header ───────────────────────────────────────────────────────────────────
 * Description: Template model of the runner page: the built-in Everything and Minimal templates, conversion
 *   between the selection state and a versioned configuration object, validation of pasted
 *   configuration JSON, and saving, loading and deleting named templates under one storage key.
 * Usage: None -- a library module imported by templates-bar.js.
 * Uses: None.
 * Env: None.
 * Input: A collection, selection state or configuration text, a localStorage-like storage object and
 *   the page settings (requiredFlow, storageKey, minimalFlows, minimalStepsOff) passed to the
 *   exported functions.
 * Outputs: Exports builtIns, toConfig, fromConfig, validateConfig, matchingName, loadSaved,
 *   saveTemplate and deleteTemplate. saveTemplate and deleteTemplate write the templates to the
 *   storage object under the storageKey setting. No DOM access.
 * Returns: N/A
 * ────────────────────────────────────────────────────────────────────────── */

const VERSION = 1;
const NAME_LIMIT = 40;
const RESERVED_NAMES = ["Everything", "Minimal", "Custom", "__proto__"];

export function builtIns(collection, { minimalFlows, minimalStepsOff }) {
  return {
    Everything: { version: VERSION, flows: collection.flows.map((flow) => flow.name), stepsOff: [], overrides: {} },
    Minimal: { version: VERSION, flows: [...minimalFlows], stepsOff: [...minimalStepsOff], overrides: {} },
  };
}

export function toConfig(state, collection) {
  return {
    version: VERSION,
    flows: collection.flows.map((flow) => flow.name).filter((name) => !state.flowsOff.includes(name)),
    stepsOff: collection.items.map((item) => item.stepId).filter((id) => state.stepsOff.includes(id)),
    overrides: Object.fromEntries(Object.entries(state.overrides).sort(([a], [b]) => a.localeCompare(b))),
  };
}

export function fromConfig(config, collection) {
  return {
    flowsOff: collection.flows.map((flow) => flow.name).filter((name) => !config.flows.includes(name)),
    stepsOff: [...config.stepsOff],
    overrides: { ...config.overrides },
  };
}

export function validateConfig(text, collection, requiredFlow) {
  let parsed;
  try {
    parsed = JSON.parse(text);
  } catch (error) {
    return { error: `not valid JSON: ${error.message}` };
  }
  return checkConfig(parsed, collection, requiredFlow);
}

export function matchingName(config, saved, collection, builtin) {
  const candidates = { ...builtin, ...saved };
  const wanted = JSON.stringify(config);
  const found = Object.keys(candidates).find(
    (name) => JSON.stringify(toConfig(fromConfig(candidates[name], collection), collection)) === wanted,
  );
  return found ?? "Custom";
}

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
