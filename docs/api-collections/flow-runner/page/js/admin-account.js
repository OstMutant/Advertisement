/* ── Header ───────────────────────────────────────────────────────────────────
 * Description: Administrator account block of the runner page: explains what happens without an
 *   administrator and offers two fields for the e-mail and password of an existing administrator.
 *   On an empty database a run registers the default administrator, which becomes the administrator only
 *   when it is the first account; when another account was registered first, the flows that need an
 *   administrator are skipped, not failed. Empty fields keep the default.
 * Usage: None -- a library module imported by main.js.
 * Uses: ./step-view.js (element).
 * Env: None.
 * Input: The container element and the collection passed to createAdminAccount; text the user types into
 *   the two fields.
 * Outputs: Appends the explanation and the two fields to the container; no storage access, nothing is saved.
 * Returns: N/A
 * ────────────────────────────────────────────────────────────────────────── */

import { element } from "./step-view.js";

const TEXT = "Administrator account. The flows use the default administrator. On an empty database a run registers it, and it becomes the administrator only when it is the first account of the database. When another account was registered first, the flows that need an administrator (tagged below) are skipped, not failed. To run them, enter the e-mail and password of an existing administrator; empty fields keep the default.";

/**
 * Builds the administrator account block in the container and returns a reader of the values typed into it.
 * Arguments: container -- element the block is appended to; collection -- model from parseCollection (its variables.email is the e-mail placeholder).
 * Outputs: appends the explanation and the two fields to the container.
 * Returns: {values} -- values() returns an object with email (trimmed) and password, each key present only when its field is not empty.
 */
export function createAdminAccount(container, collection) {
  container.append(element("p", "admin-explain", TEXT));

  const emailInput = element("input");
  emailInput.type = "email";
  emailInput.id = "admin-email";
  emailInput.autocomplete = "off";
  emailInput.spellcheck = false;
  emailInput.setAttribute("aria-label", "Administrator e-mail");
  emailInput.placeholder = collection.variables.email;
  const emailLabel = element("label", "", "E-mail");
  emailLabel.append(emailInput);

  const passwordInput = element("input");
  passwordInput.type = "password";
  passwordInput.id = "admin-password";
  passwordInput.autocomplete = "new-password";
  passwordInput.setAttribute("aria-label", "Administrator password");
  passwordInput.placeholder = "default password";
  const passwordLabel = element("label", "", "Password");
  passwordLabel.append(passwordInput);

  const fields = element("div", "admin-fields");
  fields.append(emailLabel, passwordLabel);
  container.append(fields);

  return {
    values() {
      const values = {};
      const email = emailInput.value.trim();
      if (email !== "") {
        values.email = email;
      }
      if (passwordInput.value !== "") {
        values.password = passwordInput.value;
      }
      return values;
    },
  };
}
