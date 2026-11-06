# improvement-239: Deleting a provider profile deletes its reviews, replies and rating summary everywhere

**Type:** improvement — a gap in the delete cascade: personal text and a rating stay in the database after the profile they belong to is gone
**Module:** `platform-commons` (`feedback/spi/FeedbackPort.java`), `feedback-spring-boot-starter` (`FeedbackRepository`, `FeedbackService`, `FeedbackPortImpl`), `marketplace-orchestrator` (`FeedbackAccessService`, `ProviderProfileSaveService`), `integration-tests`
**Priority:** 🟡 high, at the top at the user's request — deleting must mean deleting everywhere, and the data left behind is user-written text
**When:** independent, no blockers; [improvement-235](improvement-235-promotion-mechanism.md) does not wait for it (its strip ignores profiles that no longer exist)

## Current state

Verified against the code on 2026-11-05:

- Every way a provider profile disappears (account overlay, catalog overlay, card delete, REST delete, the user-delete cascade) goes through `ProviderProfileSaveService.delete`, which clears the taxon assignments and the contact info inside its transaction.
- It does not touch feedback. `FeedbackPort` can hard-delete one reply and `FeedbackService` one review at a time (`FeedbackRepository.deleteFeedbackHard` removes the reply, rating and content of that one entry); there is no delete by owning entity.
- So the reviews, replies, rating rows and the `feedback_aggregate` row of a deleted profile stay. `UserDeleteService` does not remove the reviews a deleted user wrote on other profiles either.

## Why change

Deleting a profile should leave nothing of it behind. The leftover reviews are user-written text kept without any owner, and the leftover aggregate is a rating for something that no longer exists.

## Expected benefit

A deleted profile takes its reviews, replies and rating summary with it, in the same transaction, so no orphans accumulate and nothing reads a rating of a missing profile.

## Approach

1. **Delete by owning entity (recommended):** `FeedbackPort.deleteByEntity(entityType, entityId)` removing every review with its reply, rating and content, and the aggregate row; `FeedbackAccessService` wraps it; `ProviderProfileSaveService.delete` calls it inside its transaction next to the contact cascade. Generic over entity type, like the rest of the starter.
2. Rejected: a database foreign-key cascade (the starter is deliberately FK-free, resolved only through its port); a periodic sweep for orphans (leaves them in place until the next run).

Open questions for the work: audit entries for the removed reviews (one per review, or one for the cascade); whether the reviews a deleted user wrote on other profiles go too; the daily review limit counters.

## Related

- [improvement-235](improvement-235-promotion-mechanism.md) — the promotion entry removal on delete follows the same rule.
- The cascade-in-the-same-transaction decision of the orchestrator (see `.claude/nav/adr-index.md`); `.claude/rules/feedback-spring-boot-starter.md`.

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
