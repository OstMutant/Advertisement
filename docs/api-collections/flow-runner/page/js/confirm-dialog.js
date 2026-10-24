/* ── Header ───────────────────────────────────────────────────────────────────
 * Description: Builds the modal confirmation dialog shown before the results of the last run are cleared.
 *   createConfirmDialog returns a function that shows a message and resolves to true only when
 *   the user confirms.
 * Usage: None -- a library module imported by main.js.
 * Uses: ./step-view.js (element); the passed-in dialog element's showModal, close and
 *   addEventListener.
 * Env: None.
 * Input: A dialog element passed to createConfirmDialog; the message text passed to the returned
 *   function.
 * Outputs: Sets the dialog's aria-label, replaces its children with the message and Cancel / Clear
 *   buttons, and opens it modally. Resolves the returned Promise when the dialog closes. No
 *   storage access.
 * Returns: N/A
 * ────────────────────────────────────────────────────────────────────────── */

import { element } from "./step-view.js";

export function createConfirmDialog(dialog) {
  dialog.setAttribute("aria-label", "Confirm clearing the last run");
  return function confirmClear(message) {
    return new Promise((resolve) => {
      let answer = false;
      const confirm = element("button", "primary", "Clear and continue");
      confirm.id = "confirm-ok";
      confirm.type = "button";
      confirm.addEventListener("click", () => {
        answer = true;
        dialog.close();
      });
      const cancel = element("button", "", "Cancel");
      cancel.id = "confirm-cancel";
      cancel.type = "button";
      cancel.addEventListener("click", () => dialog.close());
      const actions = element("div", "confirm-actions");
      actions.append(cancel, confirm);
      const body = element("div", "confirm-body");
      body.append(element("p", "", message), actions);
      dialog.replaceChildren(body);
      dialog.addEventListener("close", () => resolve(answer), { once: true });
      dialog.showModal();
    });
  };
}
