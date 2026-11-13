# improvement-251: Deleting a provider profile is tested as one unit: a failure leaves its reviews in place

**Type:** improvement — missing test coverage found by the deep verification in improvement-246
**Module:** `integration-tests` (`level2.orchestrator`), `marketplace-orchestrator` (`ProviderProfileSaveService.delete`)
**Priority:** 🟡 medium: the guarantee already holds by code reading, no test pins it
**When:** independent, no blockers

## Current state

`ProviderProfileSaveService.delete` runs inside one `TransactionTemplate`: clear the taxon assignment, delete the profile (`ProviderProfileRepository.delete`, which throws `StaleWriteException` when no row matches id and version), delete its contact info, delete its reviews, replies, ratings and rating summary (`FeedbackAccessService.deleteByEntity`), remove its promotion placements, capture the audit entry. The existing tests cover the result of `deleteByEntity` (every row of the entity removed, other entities untouched, an entity without feedback, the feedback module absent) but not the rollback of the whole unit.

## Why change

If a later step fails, or the profile delete is rejected, the reviews must still exist. Nothing proves it today; a refactor that moved a call outside the transaction would pass every test.

## Expected benefit

The all-or-nothing behaviour of the delete is pinned by a test on a real PostgreSQL.

## Approach

In `integration-tests` level 2, next to the existing `FeedbackAccessServiceTest`: create a profile with a review, a reply and a rating; call `ProviderProfileSaveService.delete` with a stale version; assert `StaleWriteException` and that the review, reply, rating, content rows and the aggregate still exist. A second case makes a later step fail (a failing `AuditPort` double) and asserts the same. A parallel-transaction test for a review written while the profile is deleted is optional and belongs to the open question in improvement-246's report (finding 3).

## Related

- [improvement-246](../completed/tasks/improvement-246-technical-debt-investigation.md): the report with the finding.
- [improvement-239](../completed/tasks/improvement-239-delete-profile-deletes-its-feedback.md): added the delete.

## Operational notes
- token_cost_review: n/a
- token_cost_research: n/a
- token_cost_verification: n/a
- review_signal_ratio: n/a
- context_loading_task_type: n/a
- context_loading_consulted: n/a
- context_loading_matched: n/a
- flows_situation: new task from the findings of the debt investigation
- flows_chosen: /new-task
- flows_matched: yes
- haiku_decomposition_used: n/a
- haiku_decomposition_dispatch_count: n/a
- haiku_decomposition_rework_needed: n/a
- adrs_referenced: none
