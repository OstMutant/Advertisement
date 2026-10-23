import { loadCollection } from "./collection.js";
import { runFlow } from "./runner.js";
import { createDiagram } from "./diagram.js";
import { createFlowMap } from "./flow-map.js";

const COLLECTION_URL = "postman/marketplace-rest-api.postman_collection.json";

async function init() {
  const runButton = document.getElementById("run-button");
  const results = document.getElementById("results");
  const diagram = createDiagram(document.getElementById("diagram"));
  let collection;
  let map;
  try {
    collection = await loadCollection(COLLECTION_URL);
    map = createFlowMap(document.getElementById("flow-map"), collection);
  } catch (error) {
    errorElement().textContent = error.message;
    return;
  }
  runButton.addEventListener("click", () => run(runButton, results, diagram, map, collection));
  runButton.disabled = false;
}

async function run(runButton, results, diagram, map, collection) {
  runButton.disabled = true;
  results.replaceChildren();
  diagram.reset();
  map.reset();
  errorElement().textContent = "";
  try {
    await runFlow(collection, {
      host: location.origin,
      flows: map.selectedFlows(),
      onStepUpdate: (index, step) => {
        renderStep(results, index, step);
        diagram.update(index, step);
        map.update(index, step);
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
