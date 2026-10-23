import { parseSettings, settingsFor } from "./settings.js";
import { codeBadge, element, methodLabel, stepBody, stepHead } from "./step-view.js";

const IGNORED_SETTINGS = ["host", "runId"];

export function createFlowDetail(container, collection, selection, { host, guard }) {
  const results = new Map();
  let flowName = null;
  let showAll = false;
  let selectedIndex = null;

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

    const apply = element("button", "primary", "Apply");
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
      message.textContent = "Applied. Press Run selected to use the values.";
      message.className = "msg";
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
      checkbox.addEventListener("change", () => {
        const on = checkbox.checked;
        guard(() => selection.setStep(item.stepId, on), () => {
          checkbox.checked = !on;
        });
      });

      const main = element("button", "main");
      main.type = "button";
      main.append(methodLabel(item.method), element("span", "label", item.label), element("span", "tag", tagText(item)));
      const result = results.get(index);
      if (result !== undefined && result.status !== "pending") {
        main.append(element("span", `pill ${result.status}`, result.status));
        if (result.response.code !== null) {
          main.append(codeBadge(result.response.code));
        }
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
    panel.append(stepHead(item, step, "h3"), stepBody(item, step));
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
