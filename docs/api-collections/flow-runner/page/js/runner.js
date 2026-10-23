import { substitute } from "./collection.js";
import { createPm, runScript } from "./pm-shim.js";

export async function runFlow(collection, { host, variables = {}, include = [], onStepUpdate = () => {} }) {
  const runVariables = { ...collection.variables, runId: newRunId(), ...variables, host };
  const steps = collection.items.map((item, index) => ({
    index,
    id: item.stepId,
    name: item.label,
    requestName: item.name,
    kind: item.kind,
    flow: item.flow,
    status: "pending",
    durationMs: 0,
    error: null,
    note: null,
    tests: [],
    request: { method: item.method, url: item.url, headers: {}, body: null },
    response: { code: null, headers: [], body: "" },
  }));
  steps.forEach((step) => onStepUpdate(step.index, step));

  let failed = false;
  for (const [index, item] of collection.items.entries()) {
    const step = steps[index];
    if (item.defaultOff && !include.includes(item.stepId)) {
      step.status = "skipped";
      step.note = "off by default";
      onStepUpdate(index, step);
      continue;
    }
    if (failed) {
      step.status = "skipped";
      onStepUpdate(index, step);
      continue;
    }

    step.status = "running";
    onStepUpdate(index, step);
    const start = Date.now();

    const missing = new Set();
    const apply = (template) => {
      const result = substitute(template, runVariables);
      result.missing.forEach((name) => missing.add(name));
      return result.text;
    };
    const url = apply(item.url);
    const headers = Object.fromEntries(
      Object.entries(item.headers).map(([key, value]) => [key, apply(value)]),
    );
    const body = item.body === null ? null : apply(item.body);
    const auth = { ...item.auth };
    if (auth.type === "basic") {
      auth.username = apply(item.auth.username);
      auth.password = apply(item.auth.password);
    }
    if (auth.type === "bearer") {
      auth.token = apply(item.auth.token);
    }

    if (missing.size > 0) {
      step.status = "failed";
      step.error = `unresolved variable(s): ${[...missing].join(", ")}`;
      failed = true;
      step.durationMs = Date.now() - start;
      onStepUpdate(index, step);
      continue;
    }

    if (auth.type === "basic") {
      headers.Authorization = `Basic ${toBase64(`${auth.username}:${auth.password}`)}`;
    } else if (auth.type === "bearer") {
      headers.Authorization = `Bearer ${auth.token}`;
    }

    step.request = { method: item.method, url, headers, body };

    let response;
    try {
      const res = await fetch(url, { method: item.method, headers, body: body ?? undefined });
      response = { code: res.status, headers: [...res.headers.entries()], body: await res.text() };
    } catch (error) {
      step.status = "failed";
      step.error = error.message;
      failed = true;
      step.durationMs = Date.now() - start;
      onStepUpdate(index, step);
      continue;
    }
    step.response = response;
    if (response.code === 429) {
      const retryAfter = response.headers.find(([key]) => key.toLowerCase() === "retry-after")?.[1] ?? "unknown";
      step.error = `rate limited (429), retry after ${retryAfter} s`;
    }

    const { pm, tests } = createPm({ code: response.code, bodyText: response.body }, runVariables);
    try {
      runScript(item.testScript, pm);
    } catch (error) {
      step.error = error.message;
    }
    step.tests = tests;

    step.status = step.error === null && tests.every((test) => test.passed) ? "passed" : "failed";
    if (step.status === "failed") {
      failed = true;
    }
    step.durationMs = Date.now() - start;
    onStepUpdate(index, step);
  }

  return { steps, variables: runVariables };
}

function newRunId() {
  return Date.now().toString(36);
}

function toBase64(text) {
  const bytes = new TextEncoder().encode(text);
  return btoa(String.fromCharCode(...bytes));
}
