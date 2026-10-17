# improvement-212: API flow runner page — visualize and run API flows from `docs/api-collections`

**Type:** improvement — new developer tool (dev-only page)
**Module:** `docs/api-collections/` (new tool folder), `marketplace-rest-api` (dev-profile static-resource wiring, `config/`), `marketplace-app` (`application-dev.yml`)
**Priority:** medium-high — Top, second after [improvement-211](improvement-211-rest-api-testing-ui-and-mcp-readiness-investigation.md)
**When:** independent; benefits from 211's explicit `operationId`s and from the request set already in `docs/api-collections/postman/`

## Current state

Verified 2026-10-17. `docs/api-collections/postman/marketplace-rest-api.postman_collection.json` holds eight chained requests (register a test user, issue an API key, create an advertisement, then four reads); Swagger UI at `/swagger-ui/index.html` runs single requests only. A Postman Flow (visual canvas) cannot be exported or imported as a file, so it cannot live in the repository. The architecture map is a local file and cannot send requests: the browser blocks `file://` (`Origin: null`) calls to `localhost:8081`, and the app has no CORS configuration (a preflight `OPTIONS` probe returned 401). The locally running Docker app uses `SPRING_PROFILES_ACTIVE=prod` (`docker-compose.app.yml`, `run.sh`), so anything bound to the `dev` profile is not served by it.

Searched for a ready open-source tool (canvas of requests, flow stored as a file, no server to deploy) and found none: Bruno has no canvas, Postman Flows have no import, Node-RED/n8n/Kestra are servers without response assertions. Library options found: `postman-collection` (Apache-2.0, collection format handling) and `postman-sandbox` (runs `pm.*` scripts in Node or browser); `postman-runtime` is documented as Node-only; `dagre` (layout only, last updated 2018 per one source) and `cytoscape.js` for drawing graphs. Mermaid's handling of live status updates was not established.

## Why change

Ad-hoc requests are not enough to see and exercise a whole scenario (register → key → create → read, later with file uploads and feedback), and the only visual tool that draws it is closed and cannot store the flow in git.

## Expected benefit

One page, served by the running app, that draws a flow as a diagram, lets a developer run it step by step or whole, and colours each step live (pending / running / passed / failed with status code), with the flow definition versioned in the repository.

## Approach

1. **Where it lives (proposal, name to confirm):** a new folder `docs/api-collections/flow-runner/` with subfolders `flows/` (flow definitions), `page/` (one HTML file plus `css/` and `js/` modules, vanilla JavaScript, no build step) and a short `README.md`. Postman stays in `docs/api-collections/postman/`.
2. **How it is served (decided: same origin, behind a toggle):** same origin as the API, so no CORS.
   The page is served only when a dedicated property is on (name to confirm, for example
   `marketplace.dev-tools.enabled`), default off; the `dev` Spring profile turns it on in
   `application-dev.yml`. Because the locally running Docker app uses `SPRING_PROFILES_ACTIVE=prod`,
   the profile alone would leave it unreachable there, so the same property is also set through an
   environment variable in the local Docker stack, opt-in and off by default, switched on by a
   `deploy-and-run.sh` flag (decided; flag name to confirm, for example `--dev-tools`) that passes the
   environment variable to the container, never hard-wired on in the compose file a real production
   deployment would reuse. The flag follows the script's header, usage and README conventions. When the property is off the path is not registered at all
   (404), not merely closed by authorisation. The files reach the app through a build-time copy
   into `marketplace-rest-api` resources.
3. **Flow definition (decided 2026-10-17): read the Postman collection directly** — one source of
   truth, no second format and no generated copy. The page parses
   `docs/api-collections/postman/marketplace-rest-api.postman_collection.json`; the `pm.*` test
   scripts in it are run with `postman-sandbox` (Apache-2.0, 6.7.4, documented for Node and browser)
   or, if that proves too heavy for a browser page, a small shim for the few `pm` calls used
   (`collectionVariables.set`, `response.json()`, `response.code`). `postman-collection` (Apache-2.0,
   5.3.1, updated 2026-07) is the candidate for reading the format; plain JSON parsing is the
   fallback since v2.1 is plain JSON. Verify in the prototype that both load in a page without a
   build step.
4. **Drawing (versions checked 2026-10-17): prototype with Mermaid first.** Mermaid (MIT, 12.2.0,
   updated 2026-10-13, about 90k stars) is already the diagram syntax used across this repository's
   documentation, draws a flowchart from text, and is cheap to drive: define status classes with
   `classDef` (pending / running / passed / failed), regenerate the diagram text with the current
   `class <node> <status>` lines and re-render the element (`mermaid.run`; clear the node's processed
   flag first). Clickable nodes need `securityLevel: 'loose'`, acceptable only because the diagram
   text comes from our own collection, never from user input. Unproven and to check in the prototype:
   flicker and layout jumps on each re-render, the exact re-render recipe on the installed version,
   and a known issue in Mermaid 11.15 where `:::className` is rejected after the `@{ shape: ... }`
   syntax (use a separate `class` statement). No ready tool that shows live execution status on a
   Mermaid diagram was found. Fallback if Mermaid proves too static: Cytoscape.js (MIT, 3.34.4,
   updated 2026-10-15, about 11k stars, framework-free) with `@dagrejs/dagre` (MIT, 3.1.1, updated
   2026-08; the old `dagre` 0.8.5 dates from 2019 and is not the one to use). Not chosen:
   `@xyflow/system` (low-level core of React Flow), `elkjs` (EPL-2.0 or GPL-3.0, licence check).
5. **Input data per flow (decided 2026-10-17).** Two kinds of data are kept apart: *input data*
   (what a request needs that cannot come from an earlier response: names, emails, passwords,
   advertisement fields, an id when the step that creates it is not selected) and *captured data*
   (`apiKey`, `categoryId`, `advertisementId`, ...), which comes out of earlier responses and passes
   on to later steps on its own. The request bodies in the Postman collection reference variables,
   and the defaults live in the collection's `variable` list, so Postman and the page share one
   source. Every flow has its own settings text area: a compact JSON with only the values worth
   editing (for Advertisements: title, description, kind), and a **Show all** switch that reveals the
   full settings (ids, suffixes, overrides). Buttons: Apply and run, Copy, Reset to defaults; invalid
   JSON is rejected with a message and the previous values stay in force. The goal is that the user
   knows what to change in the default JSON to make a failing flow pass, and runs it again at once.
   The Authentication defaults for the administrator are the values of `ADMIN_EMAIL` and
   `ADMIN_PASSWORD` (see improvement-214). Test users and passwords are dev data only.
6. **Two levels, linear run (decided 2026-10-17).**
   - **Level 1, flow map:** one card per flow, in run order (Authentication, Taxons, Provider
     profile, Advertisements, later Feedback and Media, shown as unavailable until built). Each card
     has a checkbox, a required/optional mark, how many of its steps are selected and, after a run,
     how many passed, failed or were skipped. Run selected, or choose a preset.
   - **Level 2, flow detail:** the flow's settings text area, then its steps with checkboxes; clicking
     a step shows its request and response with headers.
   - **The run is linear.** Flows depend directly on the ones before them; when any step fails,
     everything after it, in that flow and in later flows, is skipped. Captured values pass forward
     through the whole run. There are no parallel branches.
   - **Marks:** unavailable (not built yet) is greyed out and not clickable; required is checked and
     cannot be unchecked; optional can be switched. The same for flows and steps. There is no role
     selector and no automatic detection of roles: credentials sit in the flow settings, and a step
     that lacks rights simply returns its 403, which the user reads and fixes in the settings.
   - **Defaults assume an empty database:** everything that can be created is checked, so Taxons is on
     by default. When Taxons is off, a "pick existing category and city" step (public
     `GET /api/taxons`) becomes required in the flows that need ids; while Taxons is on, it is
     optional and off. Authentication is required whenever any other flow is selected.
   - **Accounts:** the first registered account becomes the administrator (the current rule), so the
     administrator is registered first from the Authentication defaults; whether the database is
     empty is the user's responsibility (reset it with `deploy-and-run.sh --reset-only-db`), and a
     failure there is visible and fixable in the settings. A moderator is made by an administrator
     through the role-change endpoint planned in improvement-213; until it exists no flow needs one.
   - **Templates (noted 2026-10-17):** a template is a named, complete run configuration — which
     flows and optional steps are on, and each flow's settings. Built in: Everything and Minimal
     (Authentication plus Advertisements, required steps only). A configuration can be copied out and
     pasted back; editing anything switches the label to Custom. Not decided: saving user-made
     templates under a name (browser `localStorage`, or JSON files under the tool folder).
   - **Selection is not part of the settings JSON:** checkboxes and presets are separate from the
     values the flows send.
7. **Out of scope:** the architecture map, production exposure, the Postman Flow canvas. Record the dev-only serving decision with `/record-decision` once implemented.

## Related

- Design reference: [`docs/api-collections/flow-runner/mockup/flow-runner-mockup.html`](../../docs/api-collections/flow-runner/mockup/flow-runner-mockup.html) — interactive mockup of the two levels (flow map, step detail), the "run as" role, required/optional/unavailable marks and the run data document; example data only, no requests are sent.

- [improvement-211](improvement-211-rest-api-testing-ui-and-mcp-readiness-investigation.md) — tooling investigation and the Postman collection this builds on
- [improvement-186](improvement-186-rest-hypermedia-media-subresource-and-patch-support.md) — file-upload endpoints the runner will need to exercise later
- [improvement-210](improvement-210-rest-api-feedback-and-comments.md) — feedback/comment endpoints for further flows

## Operational notes
- token_cost_review: n/a
- token_cost_research: n/a
- token_cost_verification: n/a
- review_signal_ratio: n/a
- context_loading_task_type: n/a
- context_loading_consulted: n/a
- context_loading_matched: n/a
- flows_situation: n/a
- flows_chosen: n/a
- flows_matched: n/a
- haiku_decomposition_used: n/a
- haiku_decomposition_dispatch_count: n/a
- haiku_decomposition_rework_needed: n/a
- adrs_referenced: none
