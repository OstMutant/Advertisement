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
