export async function loadCollection(url) {
  const response = await fetch(url);
  if (!response.ok) {
    throw new Error(`failed to load collection ${url}: HTTP ${response.status}`);
  }
  const collection = await response.json();
  return {
    name: collection.info.name,
    variables: Object.fromEntries((collection.variable ?? []).map((entry) => [entry.key, String(entry.value)])),
    items: collection.item.map(toItem),
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

function toItem(item) {
  const request = item.request;
  return {
    name: item.name,
    method: request.method,
    url: request.url.raw,
    headers: Object.fromEntries(
      (request.header ?? []).filter((header) => header.disabled !== true).map((header) => [header.key, header.value]),
    ),
    body: request.body?.mode === "raw" ? request.body.raw : null,
    auth: parseAuth(request.auth),
    testScript: parseTestScript(item.event),
  };
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
