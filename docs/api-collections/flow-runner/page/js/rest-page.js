/* ── Header ───────────────────────────────────────────────────────────────────
 * Description: Page script of the REST flow runner: holds the REST-specific settings and starts the shared
 *   runner page once the DOM is ready, and wires the section tabs.
 * Usage: Loaded by index.html as a module script.
 * Uses: ./main.js (startRunnerPage); ./page-tabs.js (setupSectionTabs); global document.
 * Env: None.
 * Input: None.
 * Outputs: Calls setupSectionTabs on the section tablist, then startRunnerPage with the REST collection URL, required flow, storage key and the
 *   Minimal template definition.
 * Returns: N/A
 * ────────────────────────────────────────────────────────────────────────── */

import { startRunnerPage } from "./main.js";
import { setupSectionTabs } from "./page-tabs.js";

const PROFILE = {
  collectionUrl: "postman/marketplace-rest-api.postman_collection.json",
  requiredFlow: "Authentication",
  storageKey: "api-flow-runner.templates",
  minimalFlows: ["Authentication", "Advertisements"],
  minimalStepsOff: [
    "auth.second",
    "auth.keys",
    "auth.revoke",
    "auth.revoked",
    "auth.again",
    "ads.list",
    "ads.get",
    "ads.update",
    "ads.history",
    "ads.snapshot",
    "ads.revert",
    "ads.gone",
  ],
};

function start() {
  setupSectionTabs(document.querySelector("[aria-label='Section']"));
  startRunnerPage(PROFILE);
}

if (document.readyState === "loading") {
  document.addEventListener("DOMContentLoaded", start);
} else {
  start();
}
