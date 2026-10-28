# docs/api-collections

The request collection of the REST API (`marketplace-rest-api`): the Postman collection, a
developer-only flow runner page the app can serve, and the scripts that test the collection against
a running app.

| Path | Contents |
|---|---|
| [`postman/`](postman/README.md) | The Postman collection (v2.1) of the REST API, with one folder per flow. |
| [`flow-runner/`](flow-runner/) | `page/`: the flow runner page, served by the app under `/dev-tools/api-flows/` only when `marketplace.dev-tools.enabled` is `true`. |
| [`scripts/`](scripts/README.md) | Node scripts that check the collection against the OpenAPI document and run its flows, plus their shell and Windows wrappers. |
| [`api-collection-tests.sh`](api-collection-tests.sh) / [`.bat`](api-collection-tests.bat) | Entry point that runs both collection tests. |

## The flow runner page

`flow-runner/page/` is a static page (plain JavaScript modules, no build step) that reads the Postman
collection and runs its flows in the browser against the app that serves it. From top to bottom:

- **Templates:** `Everything`, `Minimal` and the templates saved in the browser, shown as tabs, with the
  current run configuration as JSON that can be copied and applied back. Saved templates live in the
  browser's `localStorage`.
- **Flow map:** one card per flow with a checkbox (Authentication is required and cannot be unchecked), the
  number of selected steps and, after a run, a count of passed, skipped or failed steps. A click on a card
  opens the flow: its settings as JSON (`Apply` stores them, `Reset to defaults` clears them), its steps
  with checkboxes, and the request and response of the chosen step.
- **Run bar:** `Run selected` runs the checked flows in collection order, a failed step skips every later
  step; `Clear` returns the page to the not-run state. Changing any selection after a run asks first,
  because it clears the run.
- **Diagram:** one node per step, grey until the step runs, then coloured by status with the response code
  badge coloured by class (2xx, 3xx, 4xx, 5xx). A click on a node opens a card with the request and the
  response.

The Node scripts run the same runner modules without a browser, so the page and the headless run cannot
drift apart.

## Entry points

- [`api-collection-tests.sh`](api-collection-tests.sh) runs `check-collection` and then `run-flows`, one after the other.
- [`scripts/check-collection.sh`](scripts/check-collection.sh) checks the collection against the OpenAPI document of the running app.
- [`scripts/run-flows.sh`](scripts/run-flows.sh) runs the collection's flows against the running app, step by step.

Every entry point has a `.bat` twin for Windows that forwards to its `.sh` file through WSL. Usage,
options and exit codes are in each script's header.
