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

## Plan (2026-11-11)

Read from the code: `ProviderProfileSaveService.delete` runs one `TransactionTemplate`: fetch and authorize, clear the taxon assignment, `ProviderProfilePort.delete(id, version)` (stale version throws `StaleWriteException`), delete contact info, `FeedbackAccessService.deleteByEntity`, `PromotionRefreshService.entityRemoved`, and last `AuditPort.captureDeletion`. A stale version fails at the port call, before the contact and feedback deletes, so that case alone cannot show that the later steps share the transaction; a failing last step can, because by then the profile, contact and feedback are already deleted.

New class `ProviderProfileDeleteTransactionTest` in `integration-tests`, `level2.orchestrator`, extends `AbstractPostgresIntegrationTest`:

- Context: `@SpringBootTest` over the user, taxon, provider-profile, contact, feedback and promotion starters, `OrchestratorAutoConfiguration` and `RepositoryTestSupport`; `@MockitoBean` for `AuditPort` and the five shell hooks (as the other level 2 tests do); pool size and public base URL by `@TestPropertySource`.
- Fixtures as in `FeedbackAccessServiceTest`: owner and author users, a profile through its repository, a contact through `ContactAccessService.save`, a review and a reply through `FeedbackAccessService`.
- Case 1, a stale version (`version + 1`): `StaleWriteException`; the profile, its contact, the review, the reply and the aggregate (review count) are all still there.
- Case 2, a later step fails (`AuditPort.captureDeletion` throws `IllegalStateException`): the exception reaches the caller; the same five things are still there. This is the case that pins the transaction.
- Case 3, the current version: the profile, contact, review, reply and aggregate are gone; it proves the assertions of the first two cases can detect a deletion.
- Control of the test itself: temporarily run `delete` without its transaction; case 2 must fail; then restore the file.

Not covered, on purpose: the taxon assignment and the promotion placements (they need a taxon and a rule-qualifying review to exist first); the parallel-transaction case stays in the deferred bucket.

After: `/deep-review` and `/sync-docs` on the new class, the integration stage of `/ci` (a test-only change), the task closed with `Applied` +1 for the ADRs it cites, regenerated index and map.

## Related

- [improvement-246](../completed/tasks/improvement-246-technical-debt-investigation.md): the report with the finding.
- [improvement-239](../completed/tasks/improvement-239-delete-profile-deletes-its-feedback.md): added the delete.

## Result (2026-11-11)

`ProviderProfileDeleteTransactionTest` in `integration-tests`, `level2.orchestrator`: three cases pass (stale version, failing last step, current version). With the transaction taken out of `ProviderProfileSaveService.delete` for one run, the failing-last-step case failed (the profile was already gone) and the other two passed, so that case is the one that pins the transaction; the original file was restored. Not covered: the taxon assignment and the promotion placements; the parallel-transaction case stays in the deferred findings.

## Operational notes
- token_cost_review: 48647
- token_cost_research: n/a
- token_cost_verification: 96278
- review_signal_ratio: 0 / 0
- context_loading_task_type: Feature, single module
- context_loading_consulted: yes
- context_loading_matched: yes
- flows_situation: review of the diff, doc sync after code change, test-only change
- flows_chosen: /deep-review, /sync-docs
- flows_matched: yes
- haiku_decomposition_used: yes
- haiku_decomposition_dispatch_count: 1
- haiku_decomposition_rework_needed: 0 / 1
- adrs_referenced: ADR-002 (marketplace-orchestrator), ADR-009 (marketplace-orchestrator)

### Agent calls
- diff review | subagent_type=deep-review-orchestrator | tokens=48647 | tool_uses=6 | duration_s=433 | mode=background | batch=solo
- documentation audit of the new test | subagent_type=general-purpose (haiku) | tokens=96278 | tool_uses=5 | duration_s=1319 | mode=background | batch=solo

### Script/command runs
- scripts/build-and-test.sh, one test class, two runs (the first run and the control without the transaction) | duration_s=326 | mode=background | result=pass
