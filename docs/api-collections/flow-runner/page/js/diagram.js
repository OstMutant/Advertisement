/* ── Header ───────────────────────────────────────────────────────────────────
 * Description: Builds the step diagram in a container: one row per flow with a numbered button per step,
 *   and paints each step's pending, passed, failed or skipped state and response code badge as
 *   results arrive.
 * Usage: None -- a library module imported by main.js.
 * Uses: ./step-view.js (codeBadge); ./status.js (codeLabel); global document.
 * Env: None.
 * Input: A container element and an onOpen callback passed to createDiagram; the collection passed to
 *   build(); step updates passed to update(index, step).
 * Outputs: Appends flow rows and step buttons to the container; sets class names, text, aria-label and
 *   title on the step buttons; calls onOpen(index) when a step button is clicked. No storage
 *   access.
 * Returns: N/A
 * ────────────────────────────────────────────────────────────────────────── */

import { codeBadge } from "./step-view.js";
import { codeLabel } from "./status.js";

export function createDiagram(container, { onOpen }) {
  const nodes = [];
  const labels = [];

  function build(collection) {
    let lastFlow = null;
    let list = null;
    collection.items.forEach((item, index) => {
      if (list === null || lastFlow !== item.flow) {
        lastFlow = item.flow;
        const row = document.createElement("section");
        row.className = "flow-row";
        if (item.flow !== "") {
          const title = document.createElement("h3");
          title.className = "flow-title";
          title.textContent = item.flow;
          row.append(title);
        }
        list = document.createElement("ol");
        list.className = "flow-steps";
        row.append(list);
        container.append(row);
      }
      const entry = document.createElement("li");
      entry.className = "flow-step-item";
      const button = document.createElement("button");
      button.type = "button";
      button.dataset.step = item.stepId;
      const indexText = document.createElement("span");
      indexText.className = "index";
      indexText.textContent = String(index + 1);
      const name = document.createElement("span");
      name.className = "name";
      button.append(indexText, name);
      button.addEventListener("click", () => onOpen(index));
      entry.append(button);
      list.append(entry);
      nodes.push(button);
      labels.push(item.label);
      paint(index, item.label, "pending", null);
    });
  }

  function paint(index, name, status, code) {
    const node = nodes[index];
    node.className = `step ${status}`;
    node.querySelector(".name").textContent = name;
    node.querySelector(".code")?.remove();
    if (code !== null) {
      node.append(codeBadge(code));
    }
    const description = status === "pending"
      ? `${name}: not run`
      : `${name}: ${status}${code === null ? "" : `, ${codeLabel(code)}`}`;
    node.setAttribute("aria-label", description);
    node.title = description;
  }

  function update(index, step) {
    paint(index, step.name, step.status, step.response.code);
  }

  function clear() {
    nodes.forEach((_, index) => paint(index, labels[index], "pending", null));
  }

  return { build, update, clear };
}
