/* ── Header ───────────────────────────────────────────────────────────────────
 * Description: Minimal MCP streamable-HTTP client: opens a session (initialize plus
 *   notifications/initialized), lists tools following pagination, calls a tool with an optional
 *   Bearer key, and closes the session. Answers may be application/json or text/event-stream; both
 *   are parsed. A 404 on a request that carried a session id starts a new session once and resends
 *   that request once.
 * Usage: None -- a library module imported by the MCP page and by check-collection.mjs.
 * Uses: None (fetch, Headers, TextEncoder, btoa/atob and URL globals only).
 * Env: None.
 * Input: An endpoint URL and an optional fetch implementation passed to createMcpClient; a tool name,
 *   an arguments object and an optional API key passed to callTool.
 * Outputs: Exports createMcpClient. HTTP requests to the endpoint only; no DOM or storage access, and
 *   the API key is sent only in the Authorization header of a callTool request.
 * Returns: open resolves to nothing; listTools to the tools array; callTool to the JSON-RPC result;
 *   close to nothing; parseBody to the parsed JSON message.
 * ────────────────────────────────────────────────────────────────────────── */

const PROTOCOL_VERSION = "2025-11-25";

export function createMcpClient({ endpoint, fetchImpl = fetch }) {
  let sessionId = null;
  let protocolVersion = PROTOCOL_VERSION;
  let nextId = 1;

  function parseBody(contentType, text) {
    if ((contentType || "").startsWith("text/event-stream")) {
      const dataLines = text
        .split(/\r?\n/)
        .filter((line) => line.startsWith("data:"))
        .map((line) => line.slice(5).trim())
        .filter((payload) => payload !== "");
      return JSON.parse(dataLines[dataLines.length - 1]);
    }
    return JSON.parse(text);
  }

  async function post(message, apiKey, allowReopen = true) {
    const headers = {
      "Content-Type": "application/json",
      Accept: "application/json, text/event-stream",
    };
    const carriedSession = sessionId !== null;
    if (carriedSession) {
      headers["Mcp-Session-Id"] = sessionId;
      headers["MCP-Protocol-Version"] = protocolVersion;
    }
    if (typeof apiKey === "string" && apiKey !== "") {
      headers.Authorization = `Bearer ${apiKey}`;
    }
    const response = await fetchImpl(endpoint, { method: "POST", headers, body: JSON.stringify(message) });
    if (response.status === 404 && carriedSession && allowReopen) {
      await open();
      return post(message, apiKey, false);
    }
    return response;
  }

  async function request(method, params, apiKey) {
    const response = await post({ jsonrpc: "2.0", id: nextId++, method, params }, apiKey);
    const text = await response.text();
    if (!response.ok) {
      throw new Error(`MCP ${method} failed: HTTP ${response.status} ${text}`);
    }
    const message = parseBody(response.headers.get("content-type"), text);
    if (message.error) {
      throw new Error(`MCP ${method} error ${message.error.code}: ${message.error.message}`);
    }
    return message.result;
  }

  async function open() {
    sessionId = null;
    protocolVersion = PROTOCOL_VERSION;
    const response = await post({
      jsonrpc: "2.0",
      id: nextId++,
      method: "initialize",
      params: { protocolVersion: PROTOCOL_VERSION, capabilities: {}, clientInfo: { name: "marketplace-dev-tools", version: "1" } },
    });
    const text = await response.text();
    if (!response.ok) {
      throw new Error(`MCP initialize failed: HTTP ${response.status} ${text}`);
    }
    const message = parseBody(response.headers.get("content-type"), text);
    if (message.error) {
      throw new Error(`MCP initialize error ${message.error.code}: ${message.error.message}`);
    }
    sessionId = response.headers.get("mcp-session-id");
    protocolVersion = message.result.protocolVersion;
    const ack = await post({ jsonrpc: "2.0", method: "notifications/initialized" });
    if (ack.status !== 202) {
      throw new Error(`MCP notifications/initialized failed: HTTP ${ack.status} ${await ack.text()}`);
    }
  }

  async function listTools() {
    const tools = [];
    let cursor;
    do {
      const result = await request("tools/list", cursor === undefined ? {} : { cursor });
      tools.push(...result.tools);
      cursor = result.nextCursor;
    } while (cursor !== undefined && cursor !== null);
    return tools;
  }

  function callTool(name, args, apiKey) {
    return request("tools/call", { name, arguments: args }, apiKey);
  }

  async function close(options = {}) {
    if (sessionId === null) {
      return;
    }
    const headers = { "Mcp-Session-Id": sessionId, "MCP-Protocol-Version": protocolVersion };
    const response = await fetchImpl(endpoint, { method: "DELETE", headers, keepalive: options.keepalive === true });
    sessionId = null;
    return response.status;
  }

  return { open, listTools, callTool, close, parseBody };
}
