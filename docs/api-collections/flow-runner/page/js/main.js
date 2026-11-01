/* ── Header ───────────────────────────────────────────────────────────────────
 * Description: Shared start-up of a flow runner page, exported as startRunnerPage(profile). Loads the collection, wires the selection, diagram,
 *   flow map, flow detail, step card and templates bar together, runs the collection from the
 *   Run button, and asks for confirmation before clearing the results of a previous run.
 * Usage: None -- a library module; a page script such as rest-page.js imports it and calls
 *   startRunnerPage(profile) after the DOM is ready.
 * Uses: ./collection.js (loadCollection); ./runner.js (runFlow); ./confirm-dialog.js; ./diagram.js;
 *   ./flow-map.js; ./flow-detail.js; ./selection.js; ./step-card.js; ./templates-bar.js; global
 *   document, window.localStorage and location.origin.
 * Env: None.
 * Input: The profile {collectionUrl, requiredFlow, storageKey, minimalFlows, minimalStepsOff, onRunFinished (optional hook, called with the final run variables)};
 *   the collection JSON at profile.collectionUrl; DOM elements by id (run-button, clear-button,
 *   confirm-dialog, diagram, flow-map, flow-detail, step-card, templates, error);
 *   window.localStorage, probed for availability.
 * Outputs: Updates every view on each step update; writes an error message into the element with id
 *   error (created if missing) on load or run failure. The localStorage probe writes and removes
 *   one temporary key.
 * Returns: N/A
 * ────────────────────────────────────────────────────────────────────────── */

import { loadCollection } from "./collection.js";
import { runFlow } from "./runner.js";
import { createConfirmDialog } from "./confirm-dialog.js";
import { createDiagram } from "./diagram.js";
import { createFlowMap } from "./flow-map.js";
import { createFlowDetail } from "./flow-detail.js";
import { createSelection } from "./selection.js";
import { createStepCard } from "./step-card.js";
import { createTemplatesBar } from "./templates-bar.js";

const CLEAR_MESSAGE = "All data of the last run will be cleared. Continue?";

export async function startRunnerPage({ collectionUrl, requiredFlow, storageKey, minimalFlows, minimalStepsOff, onRunFinished }) {
  const runButton = document.getElementById("run-button");
  const clearButton = document.getElementById("clear-button");
  const selection = createSelection();
  const confirmClear = createConfirmDialog(document.getElementById("confirm-dialog"));
  let collection;
  let map;
  let detail;
  let card;
  let diagram;
  let running = false;
  let hasResults = false;

  function clearViews() {
    diagram.clear();
    map.reset();
    detail.reset();
    card.reset();
    errorElement().textContent = "";
  }

  async function guard(action, onCancel) {
    if (running) {
      onCancel();
      return;
    }
    if (!hasResults) {
      action();
      return;
    }
    if (await confirmClear(CLEAR_MESSAGE)) {
      clearViews();
      hasResults = false;
      clearButton.disabled = true;
      action();
    } else {
      onCancel();
    }
  }

  async function start() {
    if (running) {
      return;
    }
    running = true;
    hasResults = true;
    runButton.disabled = true;
    clearButton.disabled = true;
    clearViews();
    const { host = location.origin, ...variables } = selection.overrides;
    try {
      const outcome = await runFlow(collection, {
        host,
        variables,
        flows: collection.flows.map((flow) => flow.name).filter((name) => selection.flowOn(name)),
        exclude: selection.excludedSteps(),
        onStepUpdate: (index, step) => {
          diagram.update(index, step);
          map.update(index, step);
          detail.update(index, step);
          card.update(index, step);
        },
      });
      if (onRunFinished) {
        await onRunFinished(outcome.variables);
      }
    } catch (error) {
      errorElement().textContent = error.message;
    } finally {
      running = false;
      runButton.disabled = false;
      clearButton.disabled = false;
    }
  }

  try {
    collection = await loadCollection(collectionUrl);
    card = createStepCard(document.getElementById("step-card"), collection);
    diagram = createDiagram(document.getElementById("diagram"), { onOpen: (index) => card.open(index) });
    diagram.build(collection);
    detail = createFlowDetail(document.getElementById("flow-detail"), collection, selection, {
      host: location.origin,
      guard,
    });
    map = createFlowMap(document.getElementById("flow-map"), collection, selection, {
      onOpen: (name) => {
        detail.open(name);
        map.setOpen(name);
      },
      guard,
      requiredFlow,
    });
    createTemplatesBar(document.getElementById("templates"), collection, selection, openStorage(storageKey), {
      guard,
      requiredFlow,
      storageKey,
      minimalFlows,
      minimalStepsOff,
    });
    runButton.addEventListener("click", start);
    clearButton.addEventListener("click", () => guard(() => {}, () => {}));
  } catch (error) {
    errorElement().textContent = error.message;
    return;
  }
  runButton.disabled = false;
}

function openStorage(storageKey) {
  try {
    const storage = window.localStorage;
    const probeKey = `${storageKey}.probe`;
    storage.setItem(probeKey, "1");
    storage.removeItem(probeKey);
    return storage;
  } catch {
    return null;
  }
}

function errorElement() {
  let element = document.getElementById("error");
  if (element === null) {
    element = document.createElement("p");
    element.id = "error";
    document.body.append(element);
  }
  return element;
}
