# improvement-216: API key management in the Vaadin UI (view and revoke own keys)

**Type:** improvement — missing UI for an existing capability
**Module:** `marketplace-app` (new Vaadin view), `marketplace-orchestrator` (`ApiKeyManagementService`, already present)
**Priority:** medium — Top, right after improvement-212
**When:** independent; no blockers

## Current state

API keys can be issued (`POST /api/api-keys`), listed (`GET /api/api-keys`) and revoked (`DELETE /api/api-keys/{id}`) only through the REST API. `marketplace-orchestrator`'s `ApiKeyManagementService` already exposes `create`, `listForActor` and `revoke` over `ApiKeyPort`, and `ApiKeySummaryDto` carries `id`, `keyPrefix`, `label`, `createdAt`, `lastUsedAt` and `revokedAt` — never the raw key or its hash. `marketplace-app` has no view that uses any of this.

## Why change

A user who issued keys through Swagger or scripts has no way to see which keys exist, which are still in use, or to remove forgotten ones without calling REST by hand. Keys are shown once at issuance and stored as hashes, so the list of metadata is the only way to recognise them later.

## Expected benefit

A user sees their own keys (label, prefix, created, last used, revoked) and revokes any of them in two clicks; stale keys no longer pile up unnoticed.

## Approach

0. Note: `ApiKeyManagementService.listForActor(userId, includeRevoked)` hides revoked keys unless the flag is true; the page decides whether to show revoked keys (the metadata includes `revokedAt`) and passes the flag accordingly.
1. **View only plus revoke (chosen scope):** a page listing the signed-in user's keys with a revoke action and a confirmation step; no key value is ever displayed. Uses `ApiKeyManagementService` from `marketplace-orchestrator`, so no new orchestration and no direct port use in `marketplace-app`.
2. **Issuing a new key in the UI is out of scope.** It needs its own decision: showing a raw key once in a dialog with copy support, and whether the UI should issue keys at all. Raise separately if wanted.
3. Open design points for the plan: where the page lives in navigation (profile/settings area versus its own menu entry), whether revoked keys stay visible, and whether an administrator may see other users' keys (default: no, own keys only).
4. Tests: a view test for list and revoke, a level 3 scenario that a user cannot revoke another user's key, and a Playwright scenario for the page.

## Related

- [improvement-213](../completed/tasks/improvement-213-rest-api-revision-parity-with-ui.md) — REST/UI parity work; key management is the same parity gap in the other direction
- [improvement-212](improvement-212-api-flow-runner-page.md) — flows depend on a reusable key
- `.claude/nav/adr-index.md` — the ADR on API-key bearer authentication and the one on Basic only on key issuance; neither covers a UI for key management, so nothing settled is reopened

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
