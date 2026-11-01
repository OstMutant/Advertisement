/* ── Header ───────────────────────────────────────────────────────────────────
 * Description: Tool catalog of the MCP page, exported as createCatalog(container, {endpoint}); each tool is a fixed grid card
 *   styled like a flow card. Opens an MCP session, lists the tools; a click on a card selects it (click again to deselect) and
 *   shows that one tool in a single detail panel below the grid, with its description,
 *   parameters table, a JSON arguments editor prefilled with typed example values for the required
 *   parameters and for optional ones whose schema or description gives a default or a "for example" value, an "Optional:" hint listing the rest and a Call button that shows the
 *   result. A tool whose name appears in another tool's id description as "listed by <name>" gets
 *   one pick button per returned item; a click writes that id into those other tools' arguments.
 *   On start it creates one temporary user through the REST API (register, then issue an API key, as the collection's Setup flow does),
 *   puts the key into the key field and fills every tool's arguments with the first id of each list tool, so Call works at once.
 * Usage: None -- a library module; mcp-page.js imports it and calls createCatalog.
 * Uses: ./mcp-client.js (createMcpClient); global document, window, fetch, location (REST endpoints /api/users and /api/api-keys).
 * Env: None.
 * Input: The container element and the MCP endpoint URL; the API key typed into the catalog, held
 *   only in a JS variable; the arguments JSON typed per tool; the tool input schemas from listTools.
 * Outputs: Builds DOM inside the container (.tool-card[data-tool] with a button.open, .tool-detail panel, per-tool body nodes that keep their textarea and result while another tool is open, textarea, .tool-optional, button,
 *   .tool-status, .tool-result, .tool-error, button[data-pick-id], .tool-list); calls MCP tools; closes the session on pagehide. Posts to /api/users and /api/api-keys once per page open. Never writes
 *   storage or the URL; the key lives only in memory and the key field.
 * Returns: createCatalog resolves to {fill({apiKey, ids})}: fill puts the key into the key field and, per list tool name in ids,
 *   the id into the stored arguments of the tools whose id description says "listed by" that name; exampleArguments and optionalHint are pure helpers; an optional parameter is prefilled from schema default, examples or enum, else "default <v>" or the first "for example <v>" token of its description, typed by the schema.
 * ────────────────────────────────────────────────────────────────────────── */

import { createMcpClient } from "./mcp-client.js";

function el(tag, text, className) {
  const node = document.createElement(tag);
  if (text !== undefined) {
    node.textContent = text;
  }
  if (className) {
    node.className = className;
  }
  return node;
}

function typeOf(schema) {
  if (Array.isArray(schema.type)) {
    return schema.type.join("|");
  }
  return schema.type || (schema.enum ? "enum" : "");
}

function parametersTable(inputSchema) {
  const properties = (inputSchema && inputSchema.properties) || {};
  const required = (inputSchema && inputSchema.required) || [];
  if (Object.keys(properties).length === 0) {
    return null;
  }
  const table = el("table", undefined, "tool-params");
  const head = table.createTHead().insertRow();
  for (const title of ["Name", "Type", "Required", "Description"]) {
    head.appendChild(el("th", title));
  }
  const body = table.createTBody();
  for (const [name, schema] of Object.entries(properties)) {
    const row = body.insertRow();
    row.appendChild(el("td", name));
    row.appendChild(el("td", typeOf(schema || {})));
    row.appendChild(el("td", required.includes(name) ? "yes" : "no"));
    row.appendChild(el("td", (schema && schema.description) || ""));
  }
  return table;
}

function schemaType(schema) {
  return Array.isArray(schema.type) ? schema.type[0] : schema.type;
}

function typedToken(token, schema) {
  const text = token.replace(/[.,;:]+$/, "");
  if (text === "") {
    return undefined;
  }
  let value;
  switch (schemaType(schema)) {
    case "integer":
      value = /^-?\d+$/.test(text) ? Number(text) : undefined;
      break;
    case "number":
      value = /^-?\d+(\.\d+)?$/.test(text) ? Number(text) : undefined;
      break;
    case "boolean":
      value = text === "true" || text === "false" ? text === "true" : undefined;
      break;
    default:
      value = text;
  }
  return value;
}

function describedValue(schema) {
  const description = schema.description || "";
  const itemSchema = schemaType(schema) === "array" ? schema.items || {} : schema;
  const token = /\bdefault\s+(\S+)/i.exec(description) || /\bfor example\s+(\S+)/i.exec(description);
  if (!token) {
    return undefined;
  }
  const value = typedToken(token[1], itemSchema);
  return value !== undefined && schemaType(schema) === "array" ? [value] : value;
}

function schemaValue(schema) {
  if (schema.default !== undefined) {
    return schema.default;
  }
  if (Array.isArray(schema.examples) && schema.examples.length > 0) {
    return schema.examples[0];
  }
  if (Array.isArray(schema.enum) && schema.enum.length > 0) {
    return schema.enum[0];
  }
  return undefined;
}

function exampleValue(schema) {
  if (schema.default !== undefined) {
    return schema.default;
  }
  if (Array.isArray(schema.examples) && schema.examples.length > 0) {
    return schema.examples[0];
  }
  if (Array.isArray(schema.enum) && schema.enum.length > 0) {
    return schema.enum[0];
  }
  const type = Array.isArray(schema.type) ? schema.type[0] : schema.type;
  switch (type) {
    case "integer":
    case "number":
      return 1;
    case "boolean":
      return false;
    case "array":
      return [];
    case "object":
      return {};
    default:
      return "";
  }
}

export function exampleArguments(inputSchema) {
  const properties = (inputSchema && inputSchema.properties) || {};
  const required = (inputSchema && inputSchema.required) || [];
  const template = {};
  for (const [name, schema] of Object.entries(properties)) {
    if (required.includes(name)) {
      template[name] = exampleValue(schema || {});
      continue;
    }
    const known = schemaValue(schema || {});
    const value = known !== undefined ? known : describedValue(schema || {});
    if (value !== undefined) {
      template[name] = value;
    }
  }
  for (const name of required) {
    if (!(name in template)) {
      template[name] = null;
    }
  }
  return JSON.stringify(template, null, 2);
}

export function optionalHint(inputSchema) {
  const properties = (inputSchema && inputSchema.properties) || {};
  const required = (inputSchema && inputSchema.required) || [];
  const prefilled = JSON.parse(exampleArguments(inputSchema));
  const names = Object.entries(properties)
    .filter(([name]) => !required.includes(name) && !(name in prefilled))
    .map(([name, schema]) => `${name} (${typeOf(schema || {}) || "any"})`);
  return names.length > 0 ? `Optional: ${names.join(", ")}` : "";
}

function listTargets(tools) {
  const targets = new Map();
  for (const tool of tools) {
    const description = tool.inputSchema?.properties?.id?.description || "";
    const match = /listed by (\w+)/.exec(description);
    if (match) {
      targets.set(match[1], [...(targets.get(match[1]) || []), tool.name]);
    }
  }
  return targets;
}

function setId(textarea, id) {
  let current = {};
  try {
    const parsed = JSON.parse(textarea.value);
    if (parsed && typeof parsed === "object" && !Array.isArray(parsed)) {
      current = parsed;
    }
  } catch {
    // an unparsable editor content is replaced by an object holding only the id
  }
  current.id = id;
  textarea.value = JSON.stringify(current, null, 2);
}

const DEFAULT_PASSWORD = "password123";

async function issueDefaultKey() {
  const email = `mcp.page.${Math.random().toString(36).slice(2, 10)}@example.com`;
  const post = (path, body, headers) =>
    fetch(location.origin + path, {
      method: "POST",
      headers: { "Content-Type": "application/json", ...headers },
      body: JSON.stringify(body),
    });
  const registered = await post("/api/users", { name: "MCP Page User", email, password: DEFAULT_PASSWORD });
  if (registered.status !== 201) {
    throw new Error(`register answered HTTP ${registered.status}`);
  }
  const issued = await post("/api/api-keys", { label: "mcp-page" }, { Authorization: `Basic ${btoa(`${email}:${DEFAULT_PASSWORD}`)}` });
  if (issued.status !== 201) {
    throw new Error(`issue key answered HTTP ${issued.status}`);
  }
  const rawKey = (await issued.json()).rawKey;
  if (typeof rawKey !== "string" || rawKey === "") {
    throw new Error("no key in the answer");
  }
  return rawKey;
}

function showPicks(output, result, targetAreas) {
  const items = result.structuredContent?.items;
  if (result.isError || !Array.isArray(items)) {
    return;
  }
  const picks = el("div", undefined, "tool-picks");
  for (const item of items.slice(0, 10)) {
    if (!item || typeof item.id !== "number") {
      continue;
    }
    const label = item.title || item.name;
    const pick = el("button", label ? `id ${item.id} ${label}` : `id ${item.id}`, "tool-pick");
    pick.type = "button";
    pick.dataset.pickId = String(item.id);
    pick.addEventListener("click", () => targetAreas.forEach((area) => setId(area, item.id)));
    picks.appendChild(pick);
  }
  if (picks.childElementCount > 0) {
    output.appendChild(picks);
  }
}

function showResult(output, result) {
  output.replaceChildren();
  const status = el("p", result.isError ? "isError: true" : "isError: false", "tool-status");
  status.dataset.error = String(Boolean(result.isError));
  output.appendChild(status);
  let text;
  if (result.structuredContent !== undefined && result.structuredContent !== null) {
    text = JSON.stringify(result.structuredContent, null, 2);
  } else {
    text = (result.content || [])
      .filter((item) => item.type === "text")
      .map((item) => item.text)
      .join("\n");
  }
  output.appendChild(el("pre", text, "tool-result"));
}

function showError(output, message) {
  output.replaceChildren(el("p", message, "tool-error"));
}

export async function createCatalog(container, { endpoint }) {
  const client = createMcpClient({ endpoint });
  let apiKey = "";
  container.replaceChildren();
  window.addEventListener("pagehide", () => {
    try {
      client.close({ keepalive: true })?.catch?.(() => {});
    } catch {
      // ignored on page hide
    }
  });

  const keyLabel = el("label", "API key (optional) ");
  const keyInput = document.createElement("input");
  keyInput.type = "password";
  keyInput.autocomplete = "off";
  keyInput.id = "catalog-api-key";
  keyInput.addEventListener("input", () => {
    apiKey = keyInput.value;
  });
  keyLabel.appendChild(keyInput);
  container.appendChild(keyLabel);

  const keyNote = el("p", undefined, "tool-error");
  keyNote.hidden = true;
  container.appendChild(keyNote);

  const areas = new Map();
  let targets = new Map();
  function fill({ apiKey: key, ids = {} }) {
    if (key) {
      apiKey = key;
      keyInput.value = key;
    }
    for (const [listTool, id] of Object.entries(ids)) {
      (targets.get(listTool) || []).forEach((name) => areas.has(name) && setId(areas.get(name), id));
    }
  }
  const api = { fill };

  let tools;
  try {
    await client.open();
    tools = await client.listTools();
  } catch (error) {
    container.appendChild(el("p", `Could not load tools: ${error.message}`, "tool-error"));
    return api;
  }

  targets = listTargets(tools);
  const list = el("div", undefined, "tool-list");
  const detail = el("section", undefined, "tool-detail");
  detail.hidden = true;
  container.append(list, detail);
  const cards = new Map();
  const bodies = new Map();
  let openName = null;

  function select(name) {
    openName = openName === name ? null : name;
    cards.forEach((card, toolName) => card.setAttribute("aria-pressed", String(toolName === openName)));
    detail.hidden = openName === null;
    if (openName === null) {
      detail.replaceChildren();
      return;
    }
    const tool = tools.find((entry) => entry.name === openName);
    detail.replaceChildren(el("h2", tool.name), bodies.get(openName));
  }

  for (const tool of tools) {
    const card = el("div", undefined, "flow-card tool-card");
    card.dataset.tool = tool.name;
    const open = el("button", undefined, "open");
    open.type = "button";
    open.setAttribute("aria-pressed", "false");
    open.appendChild(el("span", tool.name, "name"));
    const title = (tool.annotations && tool.annotations.title) || tool.title;
    if (title) {
      open.appendChild(el("span", title, "note"));
    }
    open.addEventListener("click", () => select(tool.name));
    card.appendChild(open);
    cards.set(tool.name, open);
    list.appendChild(card);

    const body = el("div", undefined, "tool-body");
    bodies.set(tool.name, body);
    body.appendChild(el("p", tool.description || "", "tool-description"));
    const table = parametersTable(tool.inputSchema);
    if (table) {
      body.appendChild(table);
    }

    const args = document.createElement("textarea");
    args.rows = 4;
    args.value = exampleArguments(tool.inputSchema);
    areas.set(tool.name, args);
    const hint = optionalHint(tool.inputSchema);
    args.setAttribute("aria-label", `Arguments of ${tool.name}`);
    const button = el("button", "Call");
    button.type = "button";
    button.setAttribute("aria-label", `Call ${tool.name}`);
    const output = el("div", undefined, "tool-output");
    button.addEventListener("click", async () => {
      let parsed;
      try {
        parsed = JSON.parse(args.value);
      } catch (error) {
        showError(output, `Invalid JSON: ${error.message}`);
        return;
      }
      button.disabled = true;
      try {
        const result = await client.callTool(tool.name, parsed, apiKey);
        showResult(output, result);
        if (targets.has(tool.name)) {
          showPicks(output, result, targets.get(tool.name).map((name) => areas.get(name)).filter(Boolean));
        }
      } catch (error) {
        showError(output, error.message);
      } finally {
        button.disabled = false;
      }
    });
    body.appendChild(args);
    if (hint) {
      body.appendChild(el("p", hint, "tool-optional"));
    }
    body.append(button, output);
  }

  async function prepareDefaults() {
    try {
      const key = await issueDefaultKey();
      if (!apiKey) {
        fill({ apiKey: key });
      }
    } catch (error) {
      keyNote.textContent = `No default API key: ${error.message}`;
      keyNote.hidden = false;
    }
    for (const listTool of targets.keys()) {
      try {
        const result = await client.callTool(listTool, {}, apiKey);
        const first = result.isError ? null : result.structuredContent?.items?.find((item) => typeof item?.id === "number");
        if (first) {
          fill({ ids: { [listTool]: first.id } });
        }
      } catch {
        // a failed list call leaves the typed example in place
      }
    }
  }
  prepareDefaults();
  return api;
}
