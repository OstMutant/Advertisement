/* ── Header ───────────────────────────────────────────────────────────────
 * Description: Checks the Postman collection against the running API's OpenAPI document. Every
 *   request must name an existing operationId with a matching method and path and carry a valid
 *   step/label/kind header (and an optional `when` key, when present, valid); every operation must
 *   be in the collection or on the allow-list, and the allow-list must hold no covered or unknown
 *   operation. Prints every finding, not only the first one.
 * Usage: node docs/api-collections/scripts/check-collection.mjs [baseUrl] [--config <file>] [--help]
 *   The config defaults to check-collection.config.json next to this script.
 * Uses: node:fs, node:path, node:url, global fetch (Node >= 18). No dependencies.
 * Env: API_BASE_URL -- base URL used when no positional argument is given.
 * Input: the OpenAPI document at <baseUrl><openApiPath>, the Postman collection file and the
 *   allow-list file named in the config (relative paths resolve against the config's directory).
 * Outputs: one "- <finding>" line per finding on stdout, then a summary line with the operation,
 *   collection and allow-list counts. Errors are printed on stderr.
 * Returns: exit code -- 0 when there are no findings, 1 when there is at least one finding,
 *   2 when the spec, the config or a listed file cannot be read.
 * ───────────────────────────────────────────────────────────────────────── */
import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const SCRIPT_DIR = dirname(fileURLToPath(import.meta.url));
const DEFAULT_CONFIG = resolve(SCRIPT_DIR, 'check-collection.config.json');
const METHODS = ['get', 'post', 'put', 'delete', 'patch'];
const STRING_FIELDS = ['baseUrl', 'openApiPath', 'collectionFile', 'allowListFile', 'hostPlaceholder'];
const WHEN_PATTERN = /^[A-Za-z_][A-Za-z0-9_]*=.+$/;

const USAGE = `Usage: node docs/api-collections/scripts/check-collection.mjs [baseUrl] [--config <file>] [--help]

Checks the Postman collection against the API's OpenAPI document.

Options:
  [baseUrl]        API base URL; takes precedence over API_BASE_URL and the config's baseUrl
  --config <file>  config file (default: check-collection.config.json next to this script)
  --help           print this text and exit

Config fields:
  baseUrl, openApiPath, collectionFile, allowListFile, hostPlaceholder,
  stepMetadata.maxLabelLength, stepMetadata.kinds

Exit codes:
  0  no findings
  1  at least one finding
  2  cannot read the spec or the config`;

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
  for (const field of STRING_FIELDS) {
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
  const dir = dirname(file);
  return {
    ...config,
    collectionFile: resolve(dir, config.collectionFile),
    allowListFile: resolve(dir, config.allowListFile),
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

function collectRequests(items, hostPlaceholder) {
  return items.flatMap((item) => [
    ...(item.request
      ? [{
          name: item.name,
          method: item.request.method.toUpperCase(),
          path: normalise(requestPath(item.request.url, hostPlaceholder)),
          header: parseHeader(item.request.description),
        }]
      : []),
    ...(item.item ? collectRequests(item.item, hostPlaceholder) : []),
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

function check({ operations, requests, allowList, stepMetadata }) {
  const findings = [];
  const covered = new Set();
  const stepUsers = new Map();
  for (const { name, method, path, header } of requests) {
    const endpoints = operations.get(name);
    if (!endpoints) {
      findings.push(`request '${name}' names no operation`);
    } else {
      covered.add(name);
      if (!endpoints.some((e) => e.method === method && e.path === path)) {
        const described = endpoints.map((e) => `${e.method} ${e.path}`).join(', ');
        findings.push(`request '${name}' sends ${method} ${path} but its operation is ${described}`);
      }
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
  }
  for (const [step, count] of stepUsers) {
    if (count > 1) findings.push(`step '${step}' is used by more than one request`);
  }
  const allowed = new Set(allowList);
  for (const operationId of operations.keys()) {
    if (!covered.has(operationId) && !allowed.has(operationId)) {
      findings.push(`operation '${operationId}' is not in the collection and not allow-listed`);
    }
  }
  for (const operationId of allowed) {
    if (covered.has(operationId)) {
      findings.push(`allow-list entry '${operationId}' is covered by the collection, remove it`);
    } else if (!operations.has(operationId)) {
      findings.push(`allow-list entry '${operationId}' names no operation`);
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
  const operations = await fetchOperations(`${baseUrl}${config.openApiPath}`);
  const requests = readCollection(config.collectionFile, config.hostPlaceholder);
  const allowList = readAllowList(config.allowListFile);
  const { findings, coveredCount, allowedCount } = check({
    operations,
    requests,
    allowList,
    stepMetadata: config.stepMetadata,
  });
  findings.forEach((finding) => console.log(`- ${finding}`));
  console.log(`${operations.size} operations in the API, ${coveredCount} in the collection, ${allowedCount} allow-listed`);
  return findings.length > 0 ? 1 : 0;
}

main().then(
  (code) => process.exit(code),
  (err) => {
    console.error(err.message);
    process.exit(2);
  },
);
