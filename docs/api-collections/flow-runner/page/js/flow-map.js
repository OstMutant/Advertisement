const REQUIRED_FLOW = "Authentication";

export function createFlowMap(container, collection, selection, { onOpen, guard }) {
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
    checkbox.checked = selection.flowOn(flow.name);
    checkbox.disabled = required;
    checkbox.setAttribute("aria-label", `Include ${flow.name}`);
    checkbox.addEventListener("change", () => {
      const on = checkbox.checked;
      guard(() => selection.setFlow(flow.name, on), () => {
        checkbox.checked = !on;
      });
    });

    const order = document.createElement("span");
    order.className = "order";
    order.textContent = `step ${position + 1} of ${collection.flows.length}`;
    const name = document.createElement("span");
    name.className = "name";
    name.textContent = flow.name;
    const note = document.createElement("span");
    note.className = "note";
    note.textContent = flow.note;
    const open = document.createElement("button");
    open.type = "button";
    open.className = "open";
    open.setAttribute("aria-pressed", "false");
    open.append(order, name, note);
    open.addEventListener("click", () => onOpen(flow.name));

    const head = document.createElement("div");
    head.className = "head";
    head.append(checkbox, open);

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

    cards.push({ flow: flow.name, steps, checkbox, open, count, pill });
  });

  function renderCount(card) {
    const selected = selection.flowOn(card.flow)
      ? card.steps.filter((step) => selection.stepOn(step.stepId)).length
      : 0;
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

  function update(index, step) {
    statuses.set(index, step.status);
    cards.forEach(renderPill);
  }

  function reset() {
    statuses.clear();
    cards.forEach(renderPill);
  }

  function setOpen(flowName) {
    cards.forEach((card) => card.open.setAttribute("aria-pressed", String(card.flow === flowName)));
  }

  selection.subscribe(() =>
    cards.forEach((card) => {
      card.checkbox.checked = selection.flowOn(card.flow);
      renderCount(card);
    }),
  );
  cards.forEach(renderCount);

  return { update, reset, setOpen };
}
