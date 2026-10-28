# improvement-210: REST API for feedback and comments

**Type:** improvement — new external API surface (missing capability)
**Module:** `marketplace-rest-api`, `marketplace-orchestrator` (`FeedbackAccessService`,
`FeedbackCommentWriteService`), `feedback-spring-boot-starter`, `platform-commons` (`FeedbackPort`,
`FeedbackDto`, `FeedbackSaveDto`, `FeedbackAggregateDto`)
**Priority:** medium-high — Top, second after improvement-186's first pass
**When:** independent; should land before [improvement-196](../../tasks/improvement-196-rest-api-rate-limiting.md) so its rate limiting covers these new write endpoints

## Current state

Verified against code, 2026-10-17. The feedback domain is complete and used by the Vaadin UI:
`FeedbackPort` (list by entity, count, aggregate, save, comment save/delete, flag, approve/
reject/confirm-violation, moderation queue), orchestrated by `FeedbackAccessService` and
`FeedbackCommentWriteService` (privileged actions go through `authorizationService`). `marketplace-
rest-api` has controllers only for Advertisement, ApiKey, ProviderProfile, Taxon and User — nothing
for feedback or comments. `FeedbackService.save` already enforces a daily per-author limit, an edit
window, optimistic locking via `version`, and HTML sanitizing, so those rules apply to any caller.
No backlog task tracks a REST surface for this.

## Why change

External API clients (the reason the REST module exists) cannot read ratings, leave a review,
reply to one, or report abuse, although the product already supports all of it in the UI.

## Expected benefit

API parity with the UI for the public review flow, with authorization and moderation rules reused
from the orchestrator rather than reimplemented.

## Approach

1. Public reads, like the other catalogs: list feedback for an entity (paged, `Link` +
   `X-Total-Count` per the existing list-endpoint convention) and the aggregate (average, count).
2. Authenticated writes: create/update a review (`If-Match` on `version`, as the other PUTs do),
   add/delete a comment, flag a review or comment with a reason.
3. Moderation (approve, reject, confirm violation, queue): UI-only for now (confirmed 2026-10-17);
   revisit only if an API client needs it.
4. Every new operation gets an explicit `operationId` (verb + entity, camelCase, see improvement-211 item 2), never the generated one.
5. Contract: request/response DTOs in `marketplace-rest-api` (never the internal DTOs directly),
   `429` mapping through the existing `TooManyAttemptsException` handler, OpenAPI annotations at the
   existing density, controller tests mirroring the other controllers.
6. Record the decision in `marketplace-rest-api/DECISIONS.md` via `/record-decision`.

## Related

- [improvement-186](../../tasks/improvement-186-rest-hypermedia-media-subresource-and-patch-support.md) (REST hypermedia, media, PATCH, Basic-auth brute-force gap) — shares the module
- [improvement-196](../../tasks/improvement-196-rest-api-rate-limiting.md) (REST write rate limiting) — should cover these endpoints once they exist
- [improvement-200](improvement-200-f06-reviews-ratings.md) (reviews/ratings, completed) — the domain this exposes
- `marketplace-orchestrator/DECISIONS.md` ADR-007 (authorization at the orchestrator boundary)

## Resolution (2026-10-20)

Built as phases 6a and 6b of [improvement-213](../../tasks/improvement-213-rest-api-revision-parity-with-ui.md), with every rule matched to the Vaadin panel instead of the first outline: provider profiles only (the UI has no feedback on advertisements); a public paged list with the owner's reply and hidden entries returned without text and rating; one review per user and profile (`409`), edit by the author within the edit window with `If-Match`; the single reply by the profile's owner; flagging only of an entry that is not hidden, never your own; a daily limit of 10 reviews and 30 replies (`429`, `Retry-After` from the limiter's window); moderation (queue, approve, confirm violation) for administrators and moderators, with no reject because the UI has none. `FeedbackWriteService` and the status checks in the services are shared by the UI and REST. Request and response records live in the REST module; the decision is recorded by extending the service-boundary authorization decision in `marketplace-orchestrator/DECISIONS.md` (ADR-007).

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
- adrs_referenced: ADR-007 (marketplace-orchestrator)
