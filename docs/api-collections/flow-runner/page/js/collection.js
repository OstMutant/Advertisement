export async function loadCollection(url) {
  const response = await fetch(url);
  if (!response.ok) {
    throw new Error(`failed to load collection ${url}: HTTP ${response.status}`);
  }
  return parseCollection(await response.json());
}

export function parseCollection(collection) {
  const flows = [];
  const items = [];
  for (const entry of collection.item) {
    if (Array.isArray(entry.item)) {
      flows.push({ name: entry.name, note: descriptionText(entry.description) });
      collectItems([entry], entry.name, items);
    } else {
      collectItems([entry], "", items);
    }
  }
  return {
    name: collection.info.name,
    variables: Object.fromEntries((collection.variable ?? []).map((entry) => [entry.key, String(entry.value)])),
    flows,
    items,
  };
}

export function substitute(template, variables) {
  const missing = [];
  const text = template.replace(PLACEHOLDER, (placeholder, name) => {
    if (Object.hasOwn(variables, name)) {
      return variables[name];
    }
    if (!missing.includes(name)) {
      missing.push(name);
    }
    return placeholder;
  });
  return { text, missing };
}

const PLACEHOLDER = /\{\{\s*([^}\s]+)\s*\}\}/g;
const HEADER_LINE = /^(\w+):\s*(.*)$/;

function collectItems(entries, flow, items) {
  for (const entry of entries) {
    if (Array.isArray(entry.item)) {
      collectItems(entry.item, flow, items);
    } else {
      items.push(toItem(entry, flow));
    }
  }
}

function toItem(item, flow) {
  const request = item.request;
  const { meta, text } = parseDescription(request.description);
  return {
    name: item.name,
    method: request.method,
    url: request.url.raw,
    headers: Object.fromEntries(
      (request.header ?? []).filter((header) => header.disabled !== true).map((header) => [header.key, header.value]),
    ),
    body: request.body?.mode === "raw" ? request.body.raw : null,
    form: request.body?.mode === "formdata" ? parseFormData(request.body.formdata) : null,
    auth: parseAuth(request.auth),
    testScript: parseTestScript(item.event),
    flow,
    stepId: meta.step || item.name,
    label: meta.label || item.name,
    kind: meta.kind || "optional",
    defaultOff: meta.default === "off",
    when: parseWhen(meta.when),
    text,
  };
}

function parseFormData(formdata) {
  return (formdata ?? []).map((entry) => ({
    key: entry.key,
    type: entry.type ?? "text",
    value: entry.value,
    src: entry.src,
    fileName: entry.fileName,
  }));
}

function parseDescription(description) {
  const lines = descriptionText(description).split("\n");
  const blankIndex = lines.findIndex((line) => line.trim() === "");
  const headerLines = blankIndex === -1 ? lines : lines.slice(0, blankIndex);
  const bodyLines = blankIndex === -1 ? [] : lines.slice(blankIndex + 1);
  const meta = {};
  for (const line of headerLines) {
    const match = HEADER_LINE.exec(line.trim());
    if (match !== null) {
      meta[match[1]] = match[2].trim();
    }
  }
  return { meta, text: bodyLines.join("\n").trim() };
}

function descriptionText(description) {
  const content = typeof description === "string" ? description : description?.content ?? "";
  return content.trim();
}

function parseWhen(when) {
  if (when === undefined) {
    return null;
  }
  const separator = when.indexOf("=");
  return { variable: when.slice(0, separator), value: when.slice(separator + 1) };
}

function parseAuth(auth) {
  if (auth?.type === "basic") {
    return {
      type: "basic",
      username: findValue(auth.basic, "username"),
      password: findValue(auth.basic, "password"),
    };
  }
  if (auth?.type === "bearer") {
    return { type: "bearer", token: findValue(auth.bearer, "token") };
  }
  return { type: "noauth" };
}

function findValue(entries, key) {
  return (entries ?? []).find((entry) => entry.key === key)?.value ?? "";
}

function parseTestScript(events) {
  const testEvent = (events ?? []).find((event) => event.listen === "test");
  return (testEvent?.script?.exec ?? []).join("\n");
}
