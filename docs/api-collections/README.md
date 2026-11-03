# docs/api-collections

The request collections of the REST API (`marketplace-rest-api`) and of the MCP server
(`marketplace-mcp`): the two Postman collections, two developer-only pages the app can serve (a flow
runner for each), and the scripts that test the collections against a running app.

| Path | Contents |
|---|---|
| [`postman/`](postman/README.md) | The Postman collections (v2.1) of the REST API and of the MCP server, with one folder per flow. |
| [`flow-runner/`](flow-runner/) | `page/`: the REST flow runner page ([`index.html`](flow-runner/page/index.html)) and the MCP page ([`mcp.html`](flow-runner/page/mcp.html)), served by the app under `/dev-tools/api-flows/` only when `marketplace.dev-tools.enabled` is `true`. |
| [`scripts/`](scripts/README.md) | Node scripts that check a collection against the OpenAPI document (or the live MCP tool list) and run its flows, plus their shell and Windows wrappers. |
| [`api-collection-tests.sh`](api-collection-tests.sh) / [`.bat`](api-collection-tests.bat) | Entry point that runs both collection tests. |

## The flow runner page

`flow-runner/page/` is a static page (plain JavaScript modules, no build step) that reads the Postman
collection and runs its flows in the browser against the app that serves it. Both pages share the same top:
the title, a switch of two tabs (`REST API` and `MCP`; the other one is a link to the other page), and the
link to that page's Postman collection. Under it come the section tabs; only one section shows at a time.
On the REST page they are `Endpoints` (a link to the app's Swagger UI at `/swagger-ui/index.html`) and
`Flows`, the default. The `Flows` section, from top to bottom:

- **Templates:** `Administrator`, `Moderator` and `User` (the flows each role can run, see [Roles](postman/README.md#roles-and-running-without-an-administrator)) and the templates saved in the browser, shown as tabs; the label names every template that equals
  the current selection. A flow that needs an administrator carries the tag "needs an administrator", and after
  a run with an account that is not an administrator the page lists the skipped flows and where to enter the
  administrator's e-mail and password. The current run configuration is shown as JSON that can be copied and
  applied back. Saved templates live in the browser's `localStorage`.
- **Administrator account:** a note on what happens without an administrator and two fields for the e-mail
  and password of an existing administrator; empty fields keep the default. The values are used only for
  the run and are not part of the copied configuration or the saved templates.
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

## The MCP page

`flow-runner/page/mcp.html`, at `/dev-tools/api-flows/mcp.html`, is served from the same folder and
behind the same switch as the REST page. Its section tabs are `Tools` and `Flows` (the default):

- **Tools tab:** built live from the server's `tools/list`. Each tool shows its name, its description,
  a parameter table, a box for the JSON arguments, an optional API key field and a `Call` button. The field is
  empty on open and is filled with the key of the last flow run, or you paste your own; the key
  is kept only in the page's memory, never in `localStorage`.
- **Flows tab:** the same runner as on the REST page, over
  [`postman/marketplace-mcp.postman_collection.json`](postman/marketplace-mcp.postman_collection.json).
  Both pages start the shared runner modules with `startRunnerPage(profile)`; [`rest-page.js`](flow-runner/page/js/rest-page.js) and
  [`mcp-page.js`](flow-runner/page/js/mcp-page.js) hold each page's own settings.

The page opens an MCP session (`initialize`, then `notifications/initialized`), opens a new one when the
server answers 404 to a session id, and closes the session when the page is hidden. Answers may come as
JSON or as an event stream; both are read.

## Entry points

- [`api-collection-tests.sh`](api-collection-tests.sh) runs `check-collection` and then `run-flows`, one after the other.
- With `--config` [`scripts/mcp-collection.config.json`](scripts/mcp-collection.config.json) the same entry point checks and runs the MCP collection instead of the REST one; the configuration and the allow-list are described in [scripts/README.md](scripts/README.md#configuration).
- [`scripts/check-collection.sh`](scripts/check-collection.sh) checks the collection against the OpenAPI document of the running app.
- [`scripts/run-flows.sh`](scripts/run-flows.sh) runs the collection's flows against the running app, step by step.

Every entry point has a `.bat` twin for Windows that forwards to its `.sh` file through WSL. Usage,
options and exit codes are in each script's header.
