import { loadCollection } from "./collection.js";
import { runFlow } from "./runner.js";
import { createDiagram } from "./diagram.js";
import { createFlowMap } from "./flow-map.js";
import { createFlowDetail } from "./flow-detail.js";
import { createSelection } from "./selection.js";

const COLLECTION_URL = "postman/marketplace-rest-api.postman_collection.json";

async function init() {
  const runButton = document.getElementById("run-button");
  const results = document.getElementById("results");
  const diagram = createDiagram(document.getElementById("diagram"));
  const selection = createSelection();
  let collection;
  let map;
  let detail;
  try {
    collection = await loadCollection(COLLECTION_URL);
    const start = () => {
      if (!runButton.disabled) {
        run(runButton, results, diagram, map, detail, collection, selection);
      }
    };
    detail = createFlowDetail(document.getElementById("flow-detail"), collection, selection, {
      host: location.origin,
      onApplyAndRun: start,
    });
    map = createFlowMap(document.getElementById("flow-map"), collection, selection, {
      onOpen: (name) => {
        detail.open(name);
        map.setOpen(name);
      },
    });
    runButton.addEventListener("click", start);
  } catch (error) {
    errorElement().textContent = error.message;
    return;
  }
  runButton.disabled = false;
}

async function run(runButton, results, diagram, map, detail, collection, selection) {
  runButton.disabled = true;
  results.replaceChildren();
  diagram.reset();
  map.reset();
  detail.reset();
  errorElement().textContent = "";
  const { host = location.origin, ...variables } = selection.overrides;
  try {
    await runFlow(collection, {
      host,
      variables,
      flows: collection.flows.map((flow) => flow.name).filter((name) => selection.flowOn(name)),
      exclude: selection.excludedSteps(),
      onStepUpdate: (index, step) => {
        renderStep(results, index, step);
        diagram.update(index, step);
        map.update(index, step);
        detail.update(index, step);
      },
    });
  } catch (error) {
    errorElement().textContent = error.message;
  } finally {
    runButton.disabled = false;
  }
}

function renderStep(results, index, step) {
  const li = results.children[index] ?? results.appendChild(document.createElement("li"));
  li.dataset.step = step.name;
  li.className = step.status;

  let line = `${step.name} - ${step.status}`;
  if (step.response.code !== null) {
    line += ` (${step.response.code})`;
  }
  if (step.error !== null) {
    line += ` - ${step.error}`;
  }
  if (step.note !== null) {
    line += ` - ${step.note}`;
  }
  li.textContent = line;

  for (const test of step.tests) {
    if (test.passed === false) {
      const div = document.createElement("div");
      div.textContent = `failed test: ${test.name} - ${test.error}`;
      li.append(div);
    }
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

if (document.readyState === "loading") {
  document.addEventListener("DOMContentLoaded", init);
} else {
  init();
}
