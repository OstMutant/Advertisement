/* ── Header ───────────────────────────────────────────────────────────────────
 * Description: Shared DOM builders for step display: method and status-code badges, the step heading with
 *   its status pill, the request and response views, and a generic element() helper used by the
 *   other UI modules.
 * Usage: None -- a library module imported by confirm-dialog.js, diagram.js, flow-detail.js and
 *   step-card.js.
 * Uses: ./status.js (codeClass, codeLabel); global document.
 * Env: None.
 * Input: Collection step items and step results passed to stepHead and stepBody; HTTP method, status
 *   code and tag arguments passed to the other builders.
 * Outputs: Exports element, methodLabel, codeBadge, stepHead and stepBody. Each builds and returns new
 *   DOM nodes; no changes to existing DOM and no storage access.
 * Returns: N/A
 * ────────────────────────────────────────────────────────────────────────── */

import { codeClass, codeLabel } from "./status.js";

const AUTHORIZATION_PREFIX = 12;

/**
 * Creates a badge for an HTTP method with a class per method.
 * Arguments: method -- HTTP method string such as GET.
 * Outputs: None.
 * Returns: A span element.
 */
export function methodLabel(method) {
  return element("span", `method-label method-${method.toLowerCase()}`, method);
}

/**
 * Creates a status-code badge styled by its class, showing "no response" for null.
 * Arguments: code -- HTTP status code number, or null when there is no response.
 * Outputs: None.
 * Returns: A span element with aria-label and title set from codeLabel.
 */
export function codeBadge(code) {
  if (code === null) {
    return element("span", "code code-none", "no response");
  }
  const badge = element("span", `code ${codeClass(code)}`, String(code));
  badge.setAttribute("aria-label", codeLabel(code));
  badge.title = codeLabel(code);
  return badge;
}

function hasRan(step) {
  return step !== undefined && (step.status === "passed" || step.status === "failed");
}

/**
 * Creates the step heading with its label and, when the step has a status, a status pill.
 * Arguments: item -- collection step item, step -- step result or undefined, tag -- heading tag name such as h2.
 * Outputs: None.
 * Returns: A div element containing the heading and the optional pill.
 */
export function stepHead(item, step, tag) {
  const head = element("div", "step-head");
  head.append(element(tag, "", item.label));
  if (step !== undefined && step.status !== "pending") {
    head.append(element("span", `pill ${step.status}`, step.status));
  }
  return head;
}

/**
 * Creates the request view of a step, plus its response view once it has run, with a note for pending, running or skipped steps.
 * Arguments: item -- collection step item, step -- step result or undefined.
 * Outputs: None.
 * Returns: A div element containing the request and any response sections.
 */
export function stepBody(item, step) {
  const view = element("div", "view");
  if (step === undefined || step.status === "pending") {
    view.append(element("p", "dim", "This step has not run yet."));
  } else if (step.status === "running") {
    view.append(element("p", "dim", "Waiting for the response..."));
  } else if (step.status === "skipped") {
    view.append(element("p", "dim", `Skipped: ${step.note ?? "an earlier step failed"}`));
  }
  view.append(requestView(item, step));
  if (hasRan(step)) {
    view.append(element("hr", "divider"), responseView(step));
  }
  return view;
}

/**
 * Creates a DOM element with an optional class name and text content.
 * Arguments: tag -- element tag name, className -- class string (skipped when empty), text -- text content (set only when defined).
 * Outputs: None.
 * Returns: The new element, not attached to the document.
 */
export function element(tag, className, text) {
  const node = document.createElement(tag);
  if (className) {
    node.className = className;
  }
  if (text !== undefined) {
    node.textContent = text;
  }
  return node;
}

function requestView(item, step) {
  const request = hasRan(step)
    ? step.request
    : { method: item.method, url: item.url, headers: item.headers, body: item.body ?? (item.form === null ? null : "(multipart form)") };
  const view = element("div", "view");
  view.append(element("h4", "", "Request"));
  const line = element("div", "urlrow");
  line.append(methodLabel(request.method), element("code", "url", request.url));
  view.append(line, section("Headers", headerTable(Object.entries(request.headers), true)));
  view.append(section("Body", request.body === null ? element("p", "dim", "No body") : codeBlock(request.body)));
  return view;
}

function responseView(step) {
  const view = element("div", "view");
  const body = step.response.body;
  const head = element("div", "response-head");
  head.append(element("h4", "", "Response"), codeBadge(step.response.code), element("span", "time", `${step.durationMs} ms`));
  view.append(head);
  view.append(section(`Headers (${step.response.headers.length})`, headerTable(step.response.headers, false)));
  view.append(section(`Body (${body.length} B)`, body === "" ? element("p", "dim", "Empty body") : codeBlock(body)));
  if (step.error !== null) {
    view.append(element("p", "hint", step.error));
  }
  for (const test of step.tests) {
    if (test.passed === false) {
      view.append(element("p", "hint", `failed test: ${test.name} - ${test.error}`));
    }
  }
  return view;
}

function section(label, content) {
  const block = element("div");
  block.append(element("p", "label", label), content);
  return block;
}

function headerTable(pairs, maskAuthorization) {
  const wrapper = element("div", "scroll");
  const table = element("table", "hdr");
  const body = element("tbody");
  for (const [key, value] of pairs) {
    const row = element("tr");
    const shown = maskAuthorization && key.toLowerCase() === "authorization" && value.length > AUTHORIZATION_PREFIX
      ? `${value.slice(0, AUTHORIZATION_PREFIX)}...`
      : value;
    row.append(element("td", "", key), element("td", "", shown));
    body.append(row);
  }
  table.append(body);
  wrapper.append(table);
  return wrapper;
}

function codeBlock(text) {
  return element("pre", "", pretty(text));
}

function pretty(text) {
  try {
    return JSON.stringify(JSON.parse(text), null, 2);
  } catch {
    return text;
  }
}
