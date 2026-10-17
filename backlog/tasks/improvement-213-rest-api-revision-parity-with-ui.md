# improvement-213: REST API revision — audit parity with the UI and list what is missing in REST

**Type:** improvement — audit that produces follow-up tasks (no code changes of its own)
**Module:** `marketplace-rest-api` (`api/*Controller.java`), `marketplace-app` (Vaadin views and form handlers under `ui/views/`), `marketplace-orchestrator` (the services both adapters call)
**Priority:** high — Top, first: its result decides the scope of [improvement-186](improvement-186-rest-hypermedia-media-subresource-and-patch-support.md), [improvement-210](improvement-210-rest-api-feedback-and-comments.md), [improvement-212](improvement-212-api-flow-runner-page.md) and the planned MCP server
**When:** independent; do before the tasks listed above change the API

## Current state

Verified against code, 2026-10-17. `marketplace-rest-api` has five controllers: `AdvertisementApiController`, `ApiKeyController`, `ProviderProfileApiController`, `TaxonApiController`, `UserApiController`. The user endpoints are `POST /api/users` (register), `GET /api/users` (admin or moderator), `GET /api/users/{id}`, and `GET` and `PATCH /api/users/me/settings`; nothing else writes users. The Vaadin UI lets an administrator edit a user's name and role (`AccountNameFormModeHandler`), and the checks live in `marketplace-orchestrator` (`AuthorizationService`), so the logic REST would need already exists. Roles are `USER`, `ADMIN`, `MODERATOR` (`Role.java`); the first registered account becomes `ADMIN` (`UserService.register`), every later one `USER`, and no REST endpoint can change a role. Feedback and comments have a domain and orchestrator services but no REST controller (see improvement-210); advertisement media has no REST endpoint (improvement-186). No task or ADR found that records a decision to keep role changes out of REST, so the gap reads as unbuilt, not rejected.

## Why change

REST is meant to mirror what the UI can do. The gaps surface one at a time (a flow runner that cannot make a moderator, an MCP server that cannot manage users) and each is currently found by accident, with no list of what is missing or why.

## Expected benefit

One reviewed list, per domain, of what the UI can do that REST cannot, each item classified, so the follow-up REST work is planned once instead of discovered piecemeal.

## Approach

1. **Inventory (read-only):** for each domain — users, advertisements, provider profiles, taxons, feedback and comments, attachments/media, contacts, audit and history/restore, settings, API keys, moderation — list the actions the Vaadin views and form handlers offer, then the matching REST endpoint or its absence.
2. **Classify each gap** as one of: belongs in REST; UI-only by design (with the reason); security-sensitive and needs its own design (for example changing a role, removing the last administrator).
3. **Output:** a table in this file plus one `/new-task` per gap worth building, ranked in `BACKLOG.md`; existing tasks (186, 210) are linked instead of duplicated.
4. **Role-change endpoint (decided 2026-10-17):** the audit starts from the known gap that only the Vaadin form changes a role. The first endpoint to build from it is an administrator-only role change, so that an administrator can make a moderator through the API (no dev-only script is needed; see [improvement-214](improvement-214-admin-credentials-from-environment.md) for the administrator). Design points to settle: the last administrator cannot lose the role, every change is written to the audit log, and the same authorisation as the UI form is reused from `marketplace-orchestrator`.
5. **Out of scope:** implementing any endpoint found missing.

## Related

- [improvement-186](improvement-186-rest-hypermedia-media-subresource-and-patch-support.md) — media, PATCH, Basic-auth gap
- [improvement-210](improvement-210-rest-api-feedback-and-comments.md) — feedback and comments
- [improvement-212](improvement-212-api-flow-runner-page.md) — needs administrator and moderator accounts for role-based flows
- [improvement-211](improvement-211-rest-api-testing-ui-and-mcp-readiness-investigation.md) — tooling and MCP readiness

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
