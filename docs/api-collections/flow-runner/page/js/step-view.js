import { codeClass, codeLabel } from "./status.js";

const AUTHORIZATION_PREFIX = 12;

export function methodLabel(method) {
  return element("span", `method-label method-${method.toLowerCase()}`, method);
}

export function codeBadge(code) {
  if (code === null) {
    return element("span", "code code-none", "no response");
  }
  const badge = element("span", `code ${codeClass(code)}`, String(code));
  badge.setAttribute("aria-label", codeLabel(code));
  badge.title = codeLabel(code);
  return badge;
}

export function hasRan(step) {
  return step !== undefined && (step.status === "passed" || step.status === "failed");
}

export function stepHead(item, step, tag) {
  const head = element("div", "step-head");
  head.append(element(tag, "", item.label));
  if (step !== undefined && step.status !== "pending") {
    head.append(element("span", `pill ${step.status}`, step.status));
  }
  return head;
}

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
