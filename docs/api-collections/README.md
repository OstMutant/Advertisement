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

## Entry points

- [`api-collection-tests.sh`](api-collection-tests.sh) runs `check-collection` and then `run-flows`, one after the other.
- [`scripts/check-collection.sh`](scripts/check-collection.sh) checks the collection against the OpenAPI document of the running app.
- [`scripts/run-flows.sh`](scripts/run-flows.sh) runs the collection's flows against the running app, step by step.

Every entry point has a `.bat` twin for Windows that forwards to its `.sh` file through WSL. Usage,
options and exit codes are in each script's header.
