/* ── Header ───────────────────────────────────────────────────────────────
 * Description: Checks the Postman collection against the API's OpenAPI document (REST, the default) or
 *   against the MCP server's tool list (MCP, selected by `"source": "mcp"` in the config). Requests are
 *   matched to operations or tools, and every operation must be in the collection or on the allow-list.
 *   In MCP mode only the matching of request names to tools is limited to tools/call requests; every
 *   request gets the step-header and role checks. In MCP mode with referenceCollectionFile set, every tool also
 *   present in the reference collection must be exercised at least at the same role.
 * Usage: node docs/api-collections/scripts/check-collection.mjs [baseUrl] [--config <file>] [--help]
 *   (no flags)  check the collection named in check-collection.config.json against <API_BASE_URL or the config's baseUrl><openApiPath> (<mcpPath> in MCP mode)
 *   The config defaults to check-collection.config.json next to this script.
 * Uses: node:fs, node:path, node:url, global fetch (Node >= 18), flow-runner/page/js/collection.js,
 *   flow-runner/page/js/mcp-client.js (MCP mode only), flow-runner/page/js/roles.js. No dependencies.
 * Env: API_BASE_URL -- base URL used when no positional argument is given.
 * Input: the OpenAPI document at <baseUrl><openApiPath> (MCP mode: the tool list of the MCP server
 *   at <baseUrl><mcpPath>), the Postman collection file and the allow-list file named in the config
 *   (relative paths resolve against the config's directory).
 * Outputs: one "- <finding>" line per finding on stdout, all of them, covering: a request that names no
 *   operation (MCP mode: tool) or whose method and path differ from it (MCP mode: whose tools/call calls another
 *   tool); a missing step, label or kind header; a `when` key or flow `when` not in <variable>=<value> form; a
 *   `helper` value other than yes; a step used by more than one request; a non-helper request using the
 *   administrator or moderator key in a flow with no `when` condition; an operation or tool neither covered nor
 *   allow-listed; an allow-list entry that is covered or names nothing; in MCP mode with referenceCollectionFile, a tool
 *   exercised at a lower role than in the reference collection. Then a summary line with the operation
 *   (MCP mode: tool), collection (called) and allow-list counts. Errors are printed on stderr.
 * Returns: exit code -- 0 when there are no findings, 1 when there is at least one finding,
 *   2 on a usage error, or when the spec, the tool list, the config or a listed file cannot be read.
 * ───────────────────────────────────────────────────────────────────────── */
import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createMcpClient } from '../flow-runner/page/js/mcp-client.js';
import { parseCollection } from '../flow-runner/page/js/collection.js';
import { roleOfToken, roleGaps, KEY_OF_ROLE, ADMIN_RIGHTS } from '../flow-runner/page/js/roles.js';

const SCRIPT_DIR = dirname(fileURLToPath(import.meta.url));
const DEFAULT_CONFIG = resolve(SCRIPT_DIR, 'check-collection.config.json');
const METHODS = ['get', 'post', 'put', 'delete', 'patch'];
const COMMON_FIELDS = ['baseUrl', 'collectionFile', 'allowListFile', 'hostPlaceholder'];
const TOOL_CALL_PATTERN = /"method"\s*:\s*"tools\/call"/;
const TOOL_NAME_PATTERN = /"name"\s*:\s*"([^"]*)"/;
const WHEN_PATTERN = /^[A-Za-z_][A-Za-z0-9_]*=.+$/;

const USAGE = `Usage: node docs/api-collections/scripts/check-collection.mjs [baseUrl] [--config <file>] [--help]

Checks the Postman collection against the API's OpenAPI document.

Options:
  [baseUrl]        API base URL; takes precedence over API_BASE_URL and the config's baseUrl
  --config <file>  config file (default: check-collection.config.json next to this script)
  --help           print this text and exit

Config fields:
  source (openapi, the default, or mcp), baseUrl, openApiPath (openapi only), mcpPath (mcp only),
  collectionFile, allowListFile, hostPlaceholder, stepMetadata.maxLabelLength, stepMetadata.kinds,
  referenceCollectionFile (mcp only, optional)

Exit codes:
  0  no findings
  1  at least one finding
  2  usage error, or cannot read the spec or the config`;

function parseArgs(argv) {
  const options = { baseUrl: undefined, configFile: DEFAULT_CONFIG, help: false };
  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i];
    if (arg === '--help') {
      options.help = true;
    } else if (arg === '--config') {
      if (argv[i + 1] === undefined) throw new Error('--config needs a file name');
      options.configFile = resolve(argv[++i]);
    } else if (arg.startsWith('--')) {
      throw new Error(`unknown option ${arg}`);
    } else {
      options.baseUrl = arg;
    }
  }
  return options;
}

function readText(file) {
  try {
    return readFileSync(file, 'utf8');
  } catch (err) {
    throw new Error(`cannot read ${file}: ${err.message}`);
  }
}

function readJson(file) {
  const text = readText(file);
  try {
    return JSON.parse(text);
  } catch (err) {
    throw new Error(`cannot read ${file}: ${err.message}`);
  }
}

function loadConfig(file) {
  const config = readJson(file);
  const source = config.source ?? 'openapi';
  if (source !== 'openapi' && source !== 'mcp') {
    throw new Error(`config: source must be openapi or mcp`);
  }
  const sourceField = source === 'mcp' ? 'mcpPath' : 'openApiPath';
  for (const field of [...COMMON_FIELDS, sourceField]) {
    if (typeof config[field] !== 'string' || config[field] === '') {
      throw new Error(`config: ${field} is missing`);
    }
  }
  if (!Number.isInteger(config.stepMetadata?.maxLabelLength)) {
    throw new Error('config: stepMetadata.maxLabelLength is missing');
  }
  if (!Array.isArray(config.stepMetadata?.kinds) || config.stepMetadata.kinds.length === 0) {
    throw new Error('config: stepMetadata.kinds is missing');
  }
  if (config.referenceCollectionFile !== undefined && (typeof config.referenceCollectionFile !== 'string' || config.referenceCollectionFile === '')) {
    throw new Error('config: referenceCollectionFile must be a text');
  }
  const dir = dirname(file);
  return {
    ...config,
    source,
    collectionFile: resolve(dir, config.collectionFile),
    allowListFile: resolve(dir, config.allowListFile),
    referenceCollectionFile: config.referenceCollectionFile === undefined ? undefined : resolve(dir, config.referenceCollectionFile),
  };
}

function normalise(path) {
  return path.replace(/\{\{?[^}]*\}\}?/g, '{}');
}

async function fetchOperations(specUrl) {
  let spec;
  try {
    const response = await fetch(specUrl);
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    spec = await response.json();
  } catch (err) {
    throw new Error(`cannot read ${specUrl}: ${err.cause?.message ?? err.message}`);
  }
  const operations = new Map();
  for (const [path, item] of Object.entries(spec.paths ?? {})) {
    for (const method of METHODS) {
      const operationId = item[method]?.operationId;
      if (!operationId) continue;
      const endpoints = operations.get(operationId) ?? [];
      endpoints.push({ method: method.toUpperCase(), path: normalise(path) });
      operations.set(operationId, endpoints);
    }
  }
  return operations;
}

async function fetchTools(endpoint) {
  const client = createMcpClient({ endpoint });
  try {
    await client.open();
    const tools = await client.listTools();
    return new Map(tools.map((tool) => [tool.name, []]));
  } catch (err) {
    throw new Error(`cannot read ${endpoint}: ${err.cause?.message ?? err.message}`);
  } finally {
    await client.close().catch(() => {});
  }
}

function requestPath(url, hostPlaceholder) {
  if (Array.isArray(url.path)) return `/${url.path.join('/')}`;
  const raw = typeof url === 'string' ? url : url.raw;
  return raw.split(hostPlaceholder).join('').split('?')[0];
}

function descriptionText(description) {
  if (typeof description === 'string') return description;
  return typeof description?.content === 'string' ? description.content : '';
}

function parseHeader(description) {
  const header = {};
  for (const line of descriptionText(description).split(/\r?\n/)) {
    if (line.trim() === '') break;
    const colon = line.indexOf(':');
    if (colon < 0) continue;
    header[line.slice(0, colon).trim()] = line.slice(colon + 1).trim();
  }
  return header;
}

function flowWhen(description) {
  const firstLine = descriptionText(description).split(/\r?\n/)[0].trim();
  const match = /^when:\s*(.+)$/.exec(firstLine);
  return match ? match[1].trim() : undefined;
}

function collectRequests(items, hostPlaceholder, flow = { name: '', when: undefined }) {
  return items.flatMap((item) => [
    ...(item.request
      ? [{
          name: item.name,
          method: item.request.method.toUpperCase(),
          path: normalise(requestPath(item.request.url, hostPlaceholder)),
          header: parseHeader(item.request.description),
          body: item.request.body?.raw ?? '',
          flow: flow.name,
          flowWhen: flow.when,
          token: item.request.auth?.bearer?.find((entry) => entry.key === 'token')?.value ?? '',
        }]
      : []),
    ...(item.item
      ? collectRequests(item.item, hostPlaceholder, flow.name === '' ? { name: item.name, when: flowWhen(item.description) } : flow)
      : []),
  ]);
}

function readCollection(file, hostPlaceholder) {
  return collectRequests(readJson(file).item ?? [], hostPlaceholder);
}

function readAllowList(file) {
  return readText(file)
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter((line) => line !== '' && !line.startsWith('#'))
    .map((line) => line.split('#')[0].trim().split(/\s+/)[0]);
}

function checkRestRequest({ name, method, path }, operations, covered, findings) {
  const endpoints = operations.get(name);
  if (!endpoints) {
    findings.push(`request '${name}' names no operation`);
    return;
  }
  covered.add(name);
  if (!endpoints.some((e) => e.method === method && e.path === path)) {
    const described = endpoints.map((e) => `${e.method} ${e.path}`).join(', ');
    findings.push(`request '${name}' sends ${method} ${path} but its operation is ${described}`);
  }
}

function checkToolRequest({ name, method, path, body }, tools, mcpPath, covered, findings) {
  if (method !== 'POST' || path !== mcpPath || !TOOL_CALL_PATTERN.test(body)) return;
  if (!tools.has(name)) {
    findings.push(`request '${name}' names no tool`);
  } else {
    covered.add(name);
  }
  const called = TOOL_NAME_PATTERN.exec(body)?.[1];
  if (called !== name) {
    findings.push(`request '${name}' calls tool '${called}' but is named '${name}'`);
  }
}

function check({ operations, requests, allowList, stepMetadata, mcpPath }) {
  const findings = [];
  const covered = new Set();
  const stepUsers = new Map();
  const checkedFlows = new Set();
  const kind = mcpPath === undefined ? 'operation' : 'tool';
  for (const request of requests) {
    const { name, header, flow, flowWhen: condition, token } = request;
    if (mcpPath === undefined) {
      checkRestRequest(request, operations, covered, findings);
    } else {
      checkToolRequest(request, operations, mcpPath, covered, findings);
    }
    if (!header.step) {
      findings.push(`request '${name}': step is missing`);
    } else {
      stepUsers.set(header.step, (stepUsers.get(header.step) ?? 0) + 1);
    }
    if (!header.label || header.label.length > stepMetadata.maxLabelLength) {
      findings.push(`request '${name}': label is missing or longer than ${stepMetadata.maxLabelLength} characters`);
    }
    if (!stepMetadata.kinds.includes(header.kind)) {
      findings.push(`request '${name}': kind must be one of ${stepMetadata.kinds.join(', ')}`);
    }
    if (Object.hasOwn(header, 'when') && !WHEN_PATTERN.test(header.when)) {
      findings.push(`request '${name}': when must be <variable>=<value>`);
    }
    if (Object.hasOwn(header, 'helper') && header.helper !== 'yes') {
      findings.push(`request '${name}': helper must be yes`);
    }
    if (condition !== undefined && !checkedFlows.has(flow)) {
      checkedFlows.add(flow);
      if (!WHEN_PATTERN.test(condition)) findings.push(`flow '${flow}': when must be <variable>=<value>`);
    }
    const role = roleOfToken(token);
    if (header.helper !== 'yes' && condition === undefined && role !== 'user') {
      findings.push(`request '${name}': uses {{${KEY_OF_ROLE[role]}}} but its flow '${flow}' has no flow condition (when: ${ADMIN_RIGHTS}=yes)`);
    }
  }
  for (const [step, count] of stepUsers) {
    if (count > 1) findings.push(`step '${step}' is used by more than one request`);
  }
  const allowed = new Set(allowList);
  for (const operationId of operations.keys()) {
    if (!covered.has(operationId) && !allowed.has(operationId)) {
      findings.push(`${kind} '${operationId}' is not in the collection and not allow-listed`);
    }
  }
  for (const operationId of allowed) {
    if (covered.has(operationId)) {
      findings.push(`allow-list entry '${operationId}' is covered by the collection, remove it`);
    } else if (!operations.has(operationId)) {
      findings.push(`allow-list entry '${operationId}' names no ${kind}`);
    }
  }
  return { findings, coveredCount: covered.size, allowedCount: allowed.size };
}

async function main() {
  const options = parseArgs(process.argv.slice(2));
  if (options.help) {
    console.log(USAGE);
    return 0;
  }
  const config = loadConfig(options.configFile);
  const baseUrl = (options.baseUrl ?? process.env.API_BASE_URL ?? config.baseUrl).replace(/\/+$/, '');
  const isMcp = config.source === 'mcp';
  const requests = readCollection(config.collectionFile, config.hostPlaceholder);
  const allowList = readAllowList(config.allowListFile);
  const operations = isMcp
    ? await fetchTools(`${baseUrl}${config.mcpPath}`)
    : await fetchOperations(`${baseUrl}${config.openApiPath}`);
  const { findings, coveredCount, allowedCount } = check({
    operations,
    requests,
    allowList,
    stepMetadata: config.stepMetadata,
    mcpPath: isMcp ? config.mcpPath : undefined,
  });
  if (isMcp && config.referenceCollectionFile !== undefined) {
    const gaps = roleGaps(parseCollection(readJson(config.referenceCollectionFile)), parseCollection(readJson(config.collectionFile)));
    gaps.forEach(({ operation, expected, actual }) => findings.push(`tool '${operation}': the reference collection exercises it as ${expected}, this collection only as ${actual}`));
  }
  findings.forEach((finding) => console.log(`- ${finding}`));
  console.log(isMcp
    ? `${operations.size} tools on the server, ${coveredCount} called in the collection, ${allowedCount} allow-listed`
    : `${operations.size} operations in the API, ${coveredCount} in the collection, ${allowedCount} allow-listed`);
  return findings.length > 0 ? 1 : 0;
}

main().then(
  (code) => process.exit(code),
  (err) => {
    console.error(err.message);
    process.exit(2);
  },
);
