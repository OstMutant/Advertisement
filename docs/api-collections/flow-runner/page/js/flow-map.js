const REQUIRED_FLOW = "Authentication";

export function createFlowMap(container, collection) {
  const cards = [];
  const statuses = new Map();

  collection.flows.forEach((flow, position) => {
    const steps = collection.items.filter((item) => item.flow === flow.name);
    const required = flow.name === REQUIRED_FLOW;

    const card = document.createElement("div");
    card.className = "flow-card";
    card.dataset.flow = flow.name;

    const checkbox = document.createElement("input");
    checkbox.type = "checkbox";
    checkbox.checked = true;
    checkbox.disabled = required;
    checkbox.setAttribute("aria-label", `Include ${flow.name}`);

    const order = document.createElement("div");
    order.className = "order";
    order.textContent = `step ${position + 1} of ${collection.flows.length}`;
    const name = document.createElement("div");
    name.className = "name";
    name.textContent = flow.name;
    const note = document.createElement("div");
    note.className = "note";
    note.textContent = flow.note;
    const text = document.createElement("div");
    text.append(order, name, note);

    const head = document.createElement("div");
    head.className = "head";
    head.append(checkbox, text);

    const mark = document.createElement("span");
    mark.className = "tag";
    mark.textContent = required ? "required" : "optional";
    const count = document.createElement("span");
    count.className = "tag count";
    const pill = document.createElement("span");
    pill.className = "pill";
    pill.hidden = true;

    const foot = document.createElement("div");
    foot.className = "foot";
    foot.append(mark, count, pill);
    card.append(head, foot);
    container.append(card);

    const entry = { flow: flow.name, checkbox, count, pill, steps };
    cards.push(entry);
    checkbox.addEventListener("change", () => renderCount(entry));
    renderCount(entry);
  });

  function renderCount(card) {
    const selected = card.checkbox.checked ? card.steps.filter((step) => !step.defaultOff).length : 0;
    card.count.textContent = `${selected} of ${card.steps.length} steps`;
  }

  function renderPill(card) {
    const tally = { passed: 0, failed: 0, skipped: 0 };
    for (const [index, status] of statuses) {
      if (collection.items[index].flow === card.flow && status in tally) {
        tally[status] += 1;
      }
    }
    const kind = tally.failed > 0 ? "failed" : tally.skipped > 0 ? "skipped" : tally.passed > 0 ? "passed" : null;
    card.pill.hidden = kind === null;
    if (kind !== null) {
      card.pill.className = `pill ${kind}`;
      card.pill.textContent = `${tally[kind]} ${kind}`;
    }
  }

  function selectedFlows() {
    return cards.filter((card) => card.checkbox.checked).map((card) => card.flow);
  }

  function update(index, step) {
    statuses.set(index, step.status);
    cards.forEach(renderPill);
  }

  function reset() {
    statuses.clear();
    cards.forEach(renderPill);
  }

  return { selectedFlows, update, reset };
}
