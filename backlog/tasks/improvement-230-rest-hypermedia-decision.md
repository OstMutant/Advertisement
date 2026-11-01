# improvement-230: REST hypermedia decision — do agents and clients need `_links` and `GET /api/me`

**Type:** improvement — decision first, implementation only if the decision is yes
**Module:** `marketplace-rest-api`, `marketplace-orchestrator`, `platform-commons` (only if implemented)
**Priority:** medium — Top, right after [improvement-221](../completed/tasks/improvement-221-mcp-server-trial-run.md); a decision, not yet work
**When:** blocked on [improvement-221](../completed/tasks/improvement-221-mcp-server-trial-run.md) — its trial shows how an agent actually uses the API, which is the evidence this decision needs; do not start before it

## Current state

Carved out of [improvement-186](improvement-186-rest-hypermedia-media-subresource-and-patch-support.md) item 3 on 2026-10-28; that task now covers `PATCH` only. Facts:

- No response carries links or action hints. Every single-resource response is a flat DTO, so a caller must already know from Swagger which operations apply to a given resource for the current actor.
- `GET /api/users/me` (operationId `getMe`, the caller's own account with its id) was added by improvement-221; what is still open is a list of the caller's available actions and links to actions on resources.
- The `marketplace-app` architecture decision on list pagination chose RFC 8288 `Link` + `X-Total-Count` headers and rejected Spring HATEOAS and an envelope body. It decided pagination only; it did not rule on action discovery for a single resource (see `.claude/nav/adr-index.md`, title "External REST API list endpoints").
- The design drafted in improvement-186 item 3: hand-built HAL-shaped `_links` on single-resource responses, present per actor (`self` always; `edit`/`delete`/`media` only when `AuthorizationService.canOperate` is true), a new `GET /api/me` listing the actor's top-level actions by role, and the `swagger-hal-navigator` add-on for Swagger UI. Pagination links inside list bodies were explicitly left out because they would reverse the pagination decision.
- The root `README.md` lists "REST hypermedia (HATEOAS)" under planned directions.

## Why change

Nothing yet proves that a client needs this. The known consumers are Swagger UI, the API flow runner and the planned MCP server; the MCP server takes tool names from `operationId`, so it may not need links at all. Building `_links` for every resource DTO and keeping them in step with the authorization rules is real, permanent upkeep, so it should be decided from evidence, not assumed.

## Expected benefit

A recorded yes or no with reasons. If no: a decision record that stops the topic from coming back, and the README direction removed. If yes: an implementation scoped to what the observed clients actually use instead of the full HAL design.

## Approach

1. After improvement-221 is done, read its recorded finding on whether an agent lacked a hint about the actions available on a resource, and list any other client that would read links.
2. Decide among: (a) no hypermedia — record it with `/record-decision` and remove the README direction; (b) only `GET /api/me`, without HAL; (c) the full design from improvement-186 item 3. Revisiting list pagination inside bodies stays out of scope and needs its own separate go-ahead.
3. If (b) or (c): write the implementation plan into this file, with the verification (an API check per role: owner, other user, admin) and the Swagger UI step.

## Related

- [improvement-221](../completed/tasks/improvement-221-mcp-server-trial-run.md) — supplies the evidence
- [improvement-186](improvement-186-rest-hypermedia-media-subresource-and-patch-support.md) — the original design, now `PATCH` only
- `.claude/nav/adr-index.md` — the pagination decision and its rejected Spring HATEOAS alternative

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
