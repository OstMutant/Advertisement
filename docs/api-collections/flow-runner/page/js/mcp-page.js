/* ── Header ───────────────────────────────────────────────────────────────────
 * Description: Page script of the MCP page: holds the MCP-specific runner settings, starts the shared
 *   runner page once the DOM is ready, and wires the section tabs and mounts the tool catalog in the Tools section (the catalog creates one temporary user per page open).
 * Usage: Loaded by mcp.html as a module script.
 * Uses: ./main.js (startRunnerPage); ./page-tabs.js (setupSectionTabs); ./mcp-catalog.js (createCatalog); global document, location.
 * Env: None.
 * Input: None.
 * Outputs: Calls setupSectionTabs on the section tablist, createCatalog on the element with id catalog, then startRunnerPage with the MCP collection URL, required flow,
 *   storage key, the Minimal template definition and an onRunFinished hook that fills the catalog with the run key and taxon id
 *   (only variables that exist).
 * Returns: N/A
 * ────────────────────────────────────────────────────────────────────────── */

import { startRunnerPage } from "./main.js";
import { setupSectionTabs } from "./page-tabs.js";
import { createCatalog } from "./mcp-catalog.js";

const PROFILE = {
  collectionUrl: "postman/marketplace-mcp.postman_collection.json",
  requiredFlow: "Setup",
  storageKey: "mcp-flow-runner.templates",
  minimalFlows: ["Setup", "Account", "Close"],
  minimalStepsOff: [],
};

function wholeNumber(value) {
  return typeof value === "number" || (typeof value === "string" && /^\d+$/.test(value)) ? Number(value) : null;
}

function start() {
  setupSectionTabs(document.querySelector("[aria-label='Section']"));
  const catalog = createCatalog(document.getElementById("catalog"), { endpoint: location.origin + "/mcp" });
  startRunnerPage({
    ...PROFILE,
    onRunFinished: async (variables) => {
      const taxonId = wholeNumber(variables.mcpTaxonId);
      (await catalog).fill({
        apiKey: variables.mcpKey || "",
        ids: taxonId === null ? {} : { listTaxons: taxonId },
      });
    },
  });
}

if (document.readyState === "loading") {
  document.addEventListener("DOMContentLoaded", start);
} else {
  start();
}
