const PLACEHOLDER = /\{\{([A-Za-z_][A-Za-z0-9_]*)\}\}/g;
const COMPACT_LIMIT = 60;
const HIDDEN_IN_COMPACT = ["host", "runId"];

function referencedVariables(items) {
  const names = new Set();
  const scan = (text) => {
    for (const match of String(text ?? "").matchAll(PLACEHOLDER)) {
      names.add(match[1]);
    }
  };
  for (const item of items) {
    scan(item.url);
    Object.values(item.headers).forEach(scan);
    scan(item.body);
    (item.form ?? []).forEach((entry) => {
      scan(entry.key);
      scan(entry.value);
      scan(entry.src);
      scan(entry.fileName);
    });
    scan(item.auth.username);
    scan(item.auth.password);
    scan(item.auth.token);
  }
  return [...names];
}

export function settingsFor(collection, flowName, showAll, overrides, host) {
  const referenced = referencedVariables(collection.items.filter((item) => item.flow === flowName));
  const names = Object.keys(collection.variables).filter((name) => referenced.includes(name) && !HIDDEN_IN_COMPACT.includes(name));
  const shown = showAll ? names : names.filter((name) => collection.variables[name].length <= COMPACT_LIMIT);
  if (showAll) {
    shown.push("host", "runId");
  }
  return Object.fromEntries(shown.map((name) => [name, overrides[name] ?? defaultValue(collection, name, host)]));
}

export function parseSettings(text, allowedKeys) {
  let parsed;
  try {
    parsed = JSON.parse(text);
  } catch (error) {
    return { error: `not valid JSON: ${error.message}` };
  }
  if (parsed === null || typeof parsed !== "object" || Array.isArray(parsed)) {
    return { error: "settings must be a JSON object" };
  }
  const values = {};
  for (const [key, value] of Object.entries(parsed)) {
    if (!allowedKeys.includes(key)) {
      return { error: `unknown setting: ${key}` };
    }
    if (typeof value !== "string" && typeof value !== "number") {
      return { error: `setting ${key} must be a string or a number` };
    }
    values[key] = String(value);
  }
  return { values };
}

function defaultValue(collection, name, host) {
  if (name === "host") {
    return host;
  }
  if (name === "runId") {
    return "";
  }
  return collection.variables[name];
}
