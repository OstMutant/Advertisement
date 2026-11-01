/* ── Header ───────────────────────────────────────────────────────────────
 * Description: Checks the MCP client against a stub fetch: the initialize / initialized handshake and
 *   its session header, JSON and event-stream answers, tools/list pagination, the Authorization header
 *   on callTool, one session restart after a 404, and a JSON-RPC error. No API is needed.
 * Usage: bash docs/api-collections/scripts/run-node.sh mcp-client.test.mjs
 * Uses: flow-runner/page/js/mcp-client.js (the page's own module). No dependencies.
 * Env: None.
 * Input: None.
 * Outputs: one PASS or FAIL line per case, then a summary line; failures name the error.
 * Returns: exit code -- 0 when all cases pass, 1 otherwise.
 * ───────────────────────────────────────────────────────────────────────── */
import { createMcpClient } from '../flow-runner/page/js/mcp-client.js';

const ENDPOINT = 'http://stub/mcp';

function json(body, init = {}) {
  return new Response(JSON.stringify(body), { status: 200, headers: { 'content-type': 'application/json', ...init.headers } });
}

function stream(body) {
  return new Response(`event: message\ndata: ${JSON.stringify(body)}\n\n`, { status: 200, headers: { 'content-type': 'text/event-stream' } });
}

function initResult(id) {
  return json({ jsonrpc: '2.0', id, result: { protocolVersion: '2025-11-25' } }, { headers: { 'mcp-session-id': `s-${id}` } });
}

// Records every request and answers through the handler (method -> Response).
function stub(handler) {
  const calls = [];
  const fetchImpl = async (url, init) => {
    const body = init.body ? JSON.parse(init.body) : null;
    const call = { method: init.method, headers: init.headers, body };
    calls.push(call);
    if (body && body.method === 'initialize') return initResult(body.id);
    if (body && body.method === 'notifications/initialized') return new Response(null, { status: 202 });
    return handler(body, call, calls);
  };
  return { fetchImpl, calls };
}

const CASES = [
  {
    name: 'open sends initialize without a session header, then initialized with it',
    run: async () => {
      const { fetchImpl, calls } = stub(() => new Response('', { status: 500 }));
      await createMcpClient({ endpoint: ENDPOINT, fetchImpl }).open();
      const [init, ack] = calls;
      return calls.length === 2 && init.body.method === 'initialize' && init.body.params.protocolVersion === '2025-11-25'
        && init.headers['Mcp-Session-Id'] === undefined && init.headers.Accept === 'application/json, text/event-stream'
        && ack.body.method === 'notifications/initialized' && ack.headers['Mcp-Session-Id'] === 's-1'
        && ack.headers['MCP-Protocol-Version'] === '2025-11-25';
    },
  },
  {
    name: 'a JSON answer and an event-stream answer both parse',
    run: async () => {
      const client = createMcpClient({ endpoint: ENDPOINT, fetchImpl: stub(() => null).fetchImpl });
      const a = client.parseBody('application/json', '{"a":1}');
      const b = client.parseBody('text/event-stream;charset=UTF-8', 'event: message\ndata: {"a":2}\n\n');
      return a.a === 1 && b.a === 2;
    },
  },
  {
    name: 'listTools follows two pages of nextCursor',
    run: async () => {
      const { fetchImpl, calls } = stub((body) => body.params.cursor === 'p2'
        ? stream({ jsonrpc: '2.0', id: body.id, result: { tools: [{ name: 'c' }] } })
        : json({ jsonrpc: '2.0', id: body.id, result: { tools: [{ name: 'a' }, { name: 'b' }], nextCursor: 'p2' } }));
      const client = createMcpClient({ endpoint: ENDPOINT, fetchImpl });
      await client.open();
      const tools = await client.listTools();
      return tools.map((tool) => tool.name).join() === 'a,b,c' && calls.filter((call) => call.body.method === 'tools/list').length === 2;
    },
  },
  {
    name: 'callTool sends Authorization only with a key',
    run: async () => {
      const { fetchImpl, calls } = stub((body) => json({ jsonrpc: '2.0', id: body.id, result: { isError: false } }));
      const client = createMcpClient({ endpoint: ENDPOINT, fetchImpl });
      await client.open();
      await client.callTool('t', { x: 1 }, 'secret');
      await client.callTool('t', {}, '');
      const [withKey, withoutKey] = calls.filter((call) => call.body.method === 'tools/call');
      return withKey.headers.Authorization === 'Bearer secret' && withKey.body.params.arguments.x === 1
        && withoutKey.headers.Authorization === undefined;
    },
  },
  {
    name: 'a 404 is followed by exactly one new initialize and one resend',
    run: async () => {
      let first = true;
      const { fetchImpl, calls } = stub((body) => {
        if (first) { first = false; return new Response('gone', { status: 404 }); }
        return json({ jsonrpc: '2.0', id: body.id, result: { tools: [] } });
      });
      const client = createMcpClient({ endpoint: ENDPOINT, fetchImpl });
      await client.open();
      await client.listTools();
      const methods = calls.map((call) => call.body.method);
      const resend = calls[calls.length - 1];
      return methods.filter((m) => m === 'initialize').length === 2 && methods.filter((m) => m === 'tools/list').length === 2
        && resend.headers['Mcp-Session-Id'] === 's-3';
    },
  },
  {
    name: 'a JSON-RPC error throws with its code',
    run: async () => {
      const { fetchImpl } = stub((body) => json({ jsonrpc: '2.0', id: body.id, error: { code: -32602, message: 'bad params' } }));
      const client = createMcpClient({ endpoint: ENDPOINT, fetchImpl });
      await client.open();
      try {
        await client.callTool('t', {});
      } catch (error) {
        return error.message.includes('-32602') && error.message.includes('bad params');
      }
      return false;
    },
  },
];

let failures = 0;
for (const testCase of CASES) {
  let ok = false;
  let detail = '';
  try {
    ok = await testCase.run();
  } catch (error) {
    detail = ` (${error.message})`;
  }
  failures += ok ? 0 : 1;
  console.log(`${ok ? 'PASS' : 'FAIL'} ${testCase.name}${ok ? '' : detail}`);
}
console.log(`${CASES.length - failures}/${CASES.length} passed`);
process.exit(failures === 0 ? 0 : 1);
