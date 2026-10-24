/* ── Header ───────────────────────────────────────────────────────────────────
 * Description: Modal card that shows one step's details (its request, and its response once run) in a
 *   dialog. Opens for a step index and re-renders when that step's results update.
 * Usage: None -- a library module imported by main.js.
 * Uses: ./step-view.js (element, stepBody, stepHead); the passed-in dialog element's showModal and
 *   close.
 * Env: None.
 * Input: The dialog element and collection passed to createStepCard; step indexes via open(index);
 *   step results via update(index, step).
 * Outputs: Replaces the dialog's children, sets its aria-label and opens it modally; closes it on the
 *   Close button or a backdrop click. No storage access.
 * Returns: N/A
 * ────────────────────────────────────────────────────────────────────────── */

import { element, stepBody, stepHead } from "./step-view.js";

export function createStepCard(dialog, collection) {
  const results = new Map();
  let openIndex = null;

  dialog.addEventListener("close", () => {
    openIndex = null;
  });
  dialog.addEventListener("click", (event) => {
    if (event.target === dialog) {
      dialog.close();
    }
  });

  function render() {
    const item = collection.items[openIndex];
    const step = results.get(openIndex);
    dialog.setAttribute("aria-label", item.label);
    const close = element("button", "close", "Close");
    close.type = "button";
    close.addEventListener("click", () => dialog.close());
    const top = element("div", "card-head");
    top.append(stepHead(item, step, "h2"), close);
    const body = element("div", "card-body");
    body.append(top, stepBody(item, step));
    dialog.replaceChildren(body);
  }

  function open(index) {
    openIndex = index;
    render();
    if (!dialog.open) {
      dialog.showModal();
    }
  }

  function update(index, step) {
    results.set(index, step);
    if (openIndex === index && dialog.open) {
      render();
    }
  }

  function reset() {
    results.clear();
    if (openIndex !== null && dialog.open) {
      render();
    }
  }

  return { open, update, reset };
}
