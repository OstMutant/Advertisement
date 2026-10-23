import { parseSettings, settingsFor } from "./settings.js";

const AUTHORIZATION_PREFIX = 12;
const IGNORED_SETTINGS = ["host", "runId"];

export function createFlowDetail(container, collection, selection, { host, onApplyAndRun }) {
  const results = new Map();
  let flowName = null;
  let showAll = false;
  let selectedIndex = null;
  let tab = "request";

  const title = element("h2");
  const note = element("p", "note");
  const settingsBox = element("div", "settings");
  const stepList = element("ul", "steps");
  const panel = element("div", "step-panel");
  const columns = element("div", "columns");
  columns.append(stepList, panel);
  container.append(title, note, settingsBox, columns);
  container.hidden = true;
  selection.subscribe(() => {
    renderSteps();
    if (flowName !== null && !settingsBox.contains(document.activeElement)) {
      renderSettings();
    }
  });

  function open(name) {
    flowName = name;
    showAll = false;
    selectedIndex = null;
    tab = "request";
    container.hidden = false;
    title.textContent = name;
    note.textContent = collection.flows.find((flow) => flow.name === name).note;
    renderSettings();
    renderSteps();
    renderPanel();
  }

  function renderSettings() {
    settingsBox.replaceChildren();
    const allNames = Object.keys(settingsFor(collection, flowName, true, {}, host));
    if (allNames.filter((name) => !IGNORED_SETTINGS.includes(name)).length === 0) {
      settingsBox.append(element("p", "dim", "This flow has no settings."));
      return;
    }
    const shown = settingsFor(collection, flowName, showAll, selection.overrides, host);

    const toggle = element("button", "", "Show all");
    toggle.type = "button";
    toggle.setAttribute("aria-pressed", String(showAll));
    toggle.addEventListener("click", () => {
      showAll = !showAll;
      renderSettings();
    });

    const textarea = element("textarea");
    textarea.id = "flow-settings";
    textarea.spellcheck = false;
    textarea.setAttribute("aria-label", `Settings for ${flowName} as JSON`);
    textarea.value = JSON.stringify(shown, null, 2);

    const message = element("span", "msg");
    message.setAttribute("role", "status");

    const apply = element("button", "primary", "Apply and run");
    apply.id = "apply-button";
    apply.type = "button";
    apply.addEventListener("click", () => {
      const parsed = parseSettings(textarea.value, Object.keys(shown));
      if (parsed.error !== undefined) {
        message.textContent = `${parsed.error}. Previous values are still in use.`;
        message.className = "msg bad";
        return;
      }
      const values = parsed.values;
      if (values.runId === "") {
        delete values.runId;
        selection.clearValues(["runId"]);
      }
      selection.setValues(values);
      message.textContent = "";
      onApplyAndRun();
    });

    const copy = element("button", "", "Copy");
    copy.type = "button";
    copy.addEventListener("click", async () => {
      try {
        await navigator.clipboard.writeText(textarea.value);
        message.textContent = "Copied.";
      } catch {
        textarea.select();
        message.textContent = "Copy is not available here. The text is selected, press Ctrl+C.";
      }
      message.className = "msg";
    });

    const reset = element("button", "", "Reset to defaults");
    reset.type = "button";
    reset.addEventListener("click", () => {
      selection.clearValues(allNames);
      renderSettings();
    });

    const head = element("div", "settings-head");
    head.append(element("span", "label", "Settings for this flow"), toggle);
    const actions = element("div", "settings-actions");
    actions.append(apply, copy, reset, message);
    settingsBox.append(head, textarea, actions);
  }

  function renderSteps() {
    if (flowName === null) {
      return;
    }
    stepList.replaceChildren();
    collection.items.forEach((item, index) => {
      if (item.flow !== flowName) {
        return;
      }
      const locked = item.kind === "required" || item.when !== null;
      const row = element("li", index === selectedIndex ? "row sel" : "row");
      row.dataset.step = item.stepId;

      const checkbox = element("input");
      checkbox.type = "checkbox";
      checkbox.checked = locked || selection.stepOn(item.stepId);
      checkbox.disabled = locked;
      checkbox.setAttribute("aria-label", `Include step ${item.label}`);
      checkbox.addEventListener("change", () => selection.setStep(item.stepId, checkbox.checked));

      const main = element("button", "main");
      main.type = "button";
      main.append(
        element("span", `method ${item.method.toLowerCase()}`, item.method),
        element("span", "label", item.label),
        element("span", "tag", tagText(item)),
      );
      const result = results.get(index);
      if (result !== undefined && result.status !== "pending") {
        const code = result.response.code === null ? "" : ` ${result.response.code}`;
        main.append(element("span", `pill ${result.status}`, `${result.status}${code}`));
      }
      main.addEventListener("click", () => {
        selectedIndex = index;
        renderSteps();
        renderPanel();
      });
      row.append(checkbox, main);
      stepList.append(row);
    });
  }

  function renderPanel() {
    panel.replaceChildren();
    if (selectedIndex === null) {
      panel.append(element("p", "dim", "Select a step."));
      return;
    }
    const item = collection.items[selectedIndex];
    const step = results.get(selectedIndex);
    panel.append(element("h3", "", item.label), tabs(), tab === "request" ? requestView(item, step) : responseView(step));
  }

  function tabs() {
    const bar = element("div", "tabs");
    bar.setAttribute("role", "tablist");
    for (const [name, label] of [["request", "Request"], ["response", "Response"]]) {
      const button = element("button", "", label);
      button.type = "button";
      button.dataset.tab = name;
      button.setAttribute("role", "tab");
      button.setAttribute("aria-selected", String(tab === name));
      button.addEventListener("click", () => {
        tab = name;
        renderPanel();
      });
      bar.append(button);
    }
    return bar;
  }

  function requestView(item, step) {
    const sent = step !== undefined && (step.status === "passed" || step.status === "failed");
    const request = sent
      ? step.request
      : { method: item.method, url: item.url, headers: item.headers, body: item.body ?? (item.form === null ? null : "(multipart form)") };
    const view = element("div", "view");
    const line = element("div", "urlrow");
    line.append(element("span", `method ${request.method.toLowerCase()}`, request.method), element("span", "", request.url));
    view.append(line, section("Headers", headerTable(Object.entries(request.headers), true)));
    view.append(section("Body", request.body === null ? element("p", "dim", "No body") : codeBlock(request.body)));
    return view;
  }

  function responseView(step) {
    const view = element("div", "view");
    if (step === undefined || step.status === "pending") {
      view.append(element("p", "dim", "This step has not run yet."));
    } else if (step.status === "running") {
      view.append(element("p", "dim", "Waiting for the response..."));
    } else if (step.status === "skipped") {
      view.append(element("p", "dim", `Skipped: ${step.note ?? "an earlier step failed"}`));
    } else {
      const meta = element("div", "meta");
      meta.append(
        element("span", `pill ${step.status}`, step.response.code === null ? "no response" : String(step.response.code)),
        element("span", "", `${step.durationMs} ms`),
        element("span", "", `${step.response.body.length} B`),
      );
      view.append(meta, section(`Headers (${step.response.headers.length})`, headerTable(step.response.headers, false)));
      view.append(section("Body", step.response.body === "" ? element("p", "dim", "Empty body") : codeBlock(step.response.body)));
      if (step.error !== null) {
        view.append(element("p", "hint", step.error));
      }
      for (const test of step.tests) {
        if (test.passed === false) {
          view.append(element("p", "hint", `failed test: ${test.name} - ${test.error}`));
        }
      }
    }
    return view;
  }

  function update(index, step) {
    results.set(index, step);
    if (flowName !== null && collection.items[index].flow === flowName) {
      renderSteps();
      if (index === selectedIndex) {
        renderPanel();
      }
    }
  }

  function reset() {
    results.clear();
    if (flowName !== null) {
      renderSteps();
      renderPanel();
    }
  }

  return { open, update, reset };
}

function tagText(item) {
  if (item.when !== null) {
    return `runs when ${item.when.variable}=${item.when.value}`;
  }
  return item.kind === "required" ? "required" : "optional";
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

function element(tag, className, text) {
  const node = document.createElement(tag);
  if (className) {
    node.className = className;
  }
  if (text !== undefined) {
    node.textContent = text;
  }
  return node;
}
