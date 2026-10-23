import { loadCollection } from "./collection.js";
import { runFlow } from "./runner.js";
import { createDiagram } from "./diagram.js";

const COLLECTION_URL = "postman/marketplace-rest-api.postman_collection.json";

function init() {
  const runButton = document.getElementById("run-button");
  const results = document.getElementById("results");
  const diagram = createDiagram(document.getElementById("diagram"));
  runButton.addEventListener("click", () => run(runButton, results, diagram));
}

async function run(runButton, results, diagram) {
  runButton.disabled = true;
  results.replaceChildren();
  diagram.reset();
  errorElement().textContent = "";
  try {
    const collection = await loadCollection(COLLECTION_URL);
    await runFlow(collection, {
      host: location.origin,
      onStepUpdate: (index, step) => {
        renderStep(results, index, step);
        diagram.update(index, step);
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
