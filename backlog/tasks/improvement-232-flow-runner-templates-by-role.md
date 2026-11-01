# improvement-232: API flow runner pages: templates by role (administrator, moderator, user) instead of Everything and Minimal

**Type:** improvement
**Module:** `docs/api-collections` (`flow-runner/page/js/templates.js`, `templates-bar.js`, `main.js`, `rest-page.js`, `mcp-page.js`, `scripts/check-collection.config.json`, `scripts/run-flows.mjs`, `postman/*.postman_collection.json`), `playwright/e2e/11-api-flow-runner.spec.js`
**Priority:** high
**When:** independent. Touches the pages finished in improvement-221 Phase 2; does not depend on its Phase 3.

## Current state

- Both runner pages (REST `index.html`, MCP `mcp.html`) offer two built-in templates, `Everything` (all flows) and `Minimal` (a small fixed subset), plus templates saved in the browser. They are built by `builtIns(collection, {minimalFlows, minimalStepsOff})` in `flow-runner/page/js/templates.js`; each page passes its own lists from `rest-page.js` / `mcp-page.js`.
- The page has no notion of a role. The flow-runner design explicitly says there is no role selector and no automatic detection of roles: credentials sit in the flow settings and a step without rights returns its 403.
- A role exists only in the console run: `scripts/check-collection.config.json` has `"roles": { "user": ["Authentication", "Infrastructure", "Account"] }` and `run-flows --role user` runs only those flows. Every other REST flow (Taxons, Profile, Advertisements, Media, Contacts, Feedback, Moderation, Users) uses the administrator's key; the collection has no moderator key at all.
- The MCP collection needs no administrator: every flow runs with the key of a freshly registered user.

## Why change

`Everything` and `Minimal` say nothing about who the run is for. A developer wants to run what an administrator can do, what a moderator can do, or what an ordinary user can do, and see the flows that role covers, not a fixed "minimal" subset. The role split already exists for the console but not for the pages, and the MCP page has the same two generic templates.

## Expected benefit

One click runs the flows of one role on either page, the same split as the console run, with no administrator needed for the user template, and the list of flows per role kept in one place.

## Approach

The owner asked for the three roles administrator, moderator and user, on the REST and the MCP page, instead of `Everything` and `Minimal`. Decisions to take before work (options, not decided):

1. **Where the role lists live.** (a) In each page's profile (`rest-page.js`, `mcp-page.js`) and the console run reads the same source, so the lists exist once; (b) the page reads the console config, which is not served today. Either way `roles.user` in the config and the page template must not be two copies.
2. **What a role template is.** The set of flows (and steps) a role can run. For the REST collection this needs a moderator path: today no flow acts with a moderator key, so either flows get moderator steps or the moderator template lists the flows that only need rights a moderator has (Moderation).
3. **The MCP page.** Its flows already need no administrator. Role templates there mean running the same read tools with the key of a user promoted to moderator or administrator, which needs an administrator to promote them; decide whether the MCP page gets all three or only the one role that fits.
4. **`Everything` and `Minimal`.** Replaced by the role templates (the owner's wording) or kept alongside; `RESERVED_NAMES` and the saved-templates behaviour follow from this.
5. **Tests.** Extend the existing flow-runner spec (templates test) instead of adding a new spec; the console `--role` run is proven for each role on an empty database and a used one.

## Related

- [improvement-221](../completed/tasks/improvement-221-mcp-server-trial-run.md) — Phase 2 built the MCP page and the shared `startRunnerPage(profile)`.
- [improvement-212](../completed/tasks/improvement-212-api-flow-runner-page.md) — the page design, including "no role selector".
- [improvement-186](../completed/tasks/improvement-186-rest-hypermedia-media-subresource-and-patch-support.md) — step 13 added `--role user` and the administrator probe.
- The flow-repeatability and page-serving decisions of `docs/api-collections` (see `.claude/nav/adr-index.md`); no existing decision covers templates or roles on the page.

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
