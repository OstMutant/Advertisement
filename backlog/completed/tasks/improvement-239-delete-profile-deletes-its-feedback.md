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

## Decisions (2026-11-06, from the code and the project's own practice)

- **Audit:** no audit entries for the removed reviews and replies. The cascade records only its root entity: `ProviderProfileSaveService.delete` already captures one deletion for the profile, and the taxon-assignment clear and contact delete next to it add none. A per-entry `captureDeletion` exists only in `rejectFeedback`/`rejectComment`, where a moderator removes one named entry.
- **Reviews a deleted user wrote on other profiles stay.** A user is soft-deleted and purged later; `feedback.author_id` has no FK and historical authors resolve with a "deleted" mark (the user-deletion decision in `.claude/nav/adr-index.md`). Removing them would also need an aggregate recount and a promotion refresh on every other profile — a separate product decision, out of scope here.
- **Daily review limit counters:** nothing to do — an in-memory `FailureRateLimiter` keyed by author id, not by the reviewed entity.
- **Advertisements:** out of scope — no entry point writes feedback on an advertisement, and advertisements are soft-deleted.
- **Hard delete is right for profiles:** `provider_profile` is removed with a hard `DELETE`, so the reviews are truly orphaned and a hard delete of them matches.

## Plan

Direct dependency order; every step inside one transaction owned by `ProviderProfileSaveService.delete`.

1. `platform-commons` — `FeedbackPort`: add `void deleteByEntity(@NonNull EntityType entityType, @NonNull Long entityId)` with a one-line Javadoc (removes every review, its reply, rating and content, and the aggregate row of the owning entity).
2. `feedback-spring-boot-starter` — `FeedbackRepository.deleteByEntity(entityType, entityId)`, explicit SQL in FK-safe order, no FK cascade exists: `DELETE FROM feedback_comment WHERE feedback_id IN (SELECT id FROM feedback WHERE entity_type = :t AND entity_id = :id) RETURNING content_id`; `DELETE FROM feedback_rating WHERE feedback_id IN (...)`; `DELETE FROM feedback WHERE entity_type = :t AND entity_id = :id RETURNING content_id`; `DELETE FROM feedback_content WHERE id = ANY(:contentIds)` (both returned id lists); `DELETE FROM feedback_aggregate WHERE entity_type = :t AND entity_id = :id`. No empty-list guard (design by contract; `ANY` of an empty array is valid).
3. `FeedbackService.deleteByEntity` (`@Transactional`, one call to the repository) and `FeedbackPortImpl.deleteByEntity` (`@Transactional`, pure delegation).
4. `marketplace-orchestrator` — `FeedbackAccessService.deleteByEntity(entityType, entityId)`: `feedbackPortFactory.ifAvailable(port -> port.deleteByEntity(...))`, same shape as `ContactAccessService.delete`; no audit, no promotion call (the caller already does `entityRemoved`).
5. `ProviderProfileSaveService.delete`: new field `FeedbackAccessService feedbackAccessService`, one call `feedbackAccessService.deleteByEntity(EntityType.PROVIDER_PROFILE, id)` right after `contactAccessService.delete(...)`, before `promotionRefreshService.entityRemoved`. A collaborator, not a `*Port` field, so the two-port limit is untouched; no dependency cycle (`PromotionRefreshService` depends only on rules and the promotion port).
6. Tests — unit: `FeedbackAccessServiceTest` (port present delegates, port absent does nothing); `ProviderProfileSaveServiceTest` (new `@Mock FeedbackAccessService`, the existing delete test verifies the new call; the denied and non-existent cases keep their expectations). Integration: `level1/feedback/FeedbackRepositoryTest` — reviews with replies and an aggregate for two profiles, delete one, assert its five table sets are empty and the other profile's rows are intact; `level2/orchestrator` — real `ProviderProfileSaveService.delete` leaves no feedback rows.
7. Docs — `.claude/rules/feedback-spring-boot-starter.md` (the hard-delete cascades line gets the by-entity delete), `.claude/rules/marketplace-orchestrator.md` (`FeedbackAccessService` and `ProviderProfileSaveService` entries); no ADR number cited, no ticket number in code or docs.
8. Verify — `build-and-test.sh --unit --integration --sandbox` (with `--skip-vaadin`); no UI-visible change, so no Playwright run.
9. Close — move this file to `backlog/completed/tasks/`, remove its `BACKLOG.md` row, add the `✅ Done` line to `BACKLOG-ARCHIVE.md`, add 1 to `Applied` of the ADRs listed below, regenerate `.claude/nav/adr-index.md`. Commit only when asked.

Execution: steps 1-3 and 4-5 are fully specified, so each goes to a Haiku-model agent (1-3 first, then 4-6, then 7), with the diff reviewed afterwards.

## Review follow-up plan (2026-11-06, after the first implementation and a green unit + integration run)

Findings of the code review at its default level, with what is done about each:

1. **Empty `Long[]` in `feedback_content ... ANY(:contentIds)`** (a profile with no reviews is the common case, and no test reaches it): replace the two `RETURNING` reads plus the Java array with one statement, a data-modifying CTE — `WITH c AS (DELETE FROM feedback_comment WHERE feedback_id IN (SELECT id FROM feedback WHERE entity_type = :entityType AND entity_id = :entityId) RETURNING content_id), r AS (DELETE FROM feedback_rating WHERE feedback_id IN (<same subquery>)), f AS (DELETE FROM feedback WHERE entity_type = :entityType AND entity_id = :entityId RETURNING content_id) DELETE FROM feedback_content WHERE id IN (SELECT content_id FROM c UNION SELECT content_id FROM f)` — then the aggregate delete. No Java array, no `Stream` import, no empty-list case, which also covers review finding 6. A new level 1 test `deleteByEntity_entityWithoutFeedback_leavesOtherEntitiesUntouched` deletes an entity that has no feedback and asserts nothing throws and another entity's rows are intact.
2. **No promotion refresh inside `deleteByEntity`:** `ProviderProfileSaveService.delete` calls `promotionRefreshService.entityRemoved` right after it, which clears every reason for the removed profile; the contract is stated in one line of Javadoc on `FeedbackAccessService.deleteByEntity` ("removes the entity's feedback without touching its promotion placements; the caller removes those") so a later caller cannot miss it.
3. **Silent no-op when the feedback starter is absent:** no change — every delete cascade of this module (`TaxonAssignmentWriteService.clear`, `AttachmentSoftDeleteService.softDeleteAll`, `ContactAccessService.delete`, audit capture) goes through `ifAvailable` for an optional starter, and only a domain's own mandatory port and explicit user-driven writes use `get()`; failing loudly would break profile deletion in a deployment without the feedback starter.
4. **Other child tables of `feedback`:** no change — `feedback-changelog` holds one changeset with exactly the five tables the method deletes from.
5. **Unit test stubs:** in `marketplace-orchestrator` `FeedbackAccessServiceTest` remove the class-wide lenient `ifAvailable` stub from `setUp`, stub it with `doAnswer` inside `deleteByEntity_portPresent_delegatesToPort` only, and in `deleteByEntity_portAbsent_doesNothing` drop the `doNothing()` line and its comment (an unstubbed void mock already does nothing); drop the then-unused imports (`Consumer`, `lenient`, `doNothing` if unused).
6. Covered by item 1.

Playwright (extend `04-provider-profile-flow.spec.js`, test "user deletes their own provider profile from the public catalog", no new `test(...)`): after `own` is created and before the card delete, a second user reviews the profile through `reviewViaApi` and the owner replies through `replyViaApi`; the review and the reply are both flagged through `flagViaApi` with one unique reason; `GET /api/moderation/queue?reasonText=<that reason>` with the administrator key lists both entries with `profileId` equal to the profile (read as REST precondition). After the existing delete assertions the same queue request returns an empty list. The test name gets the new verifications (flagged review and reply removed from the moderation queue with the profile); the file header test list is extended. The "minimal profile" test, which deletes a profile with no reviews, runs the empty-feedback path unchanged.

Then: `/sync-docs`, then the full CI (`scripts/ci.sh --foreground`, wrapped by the activity monitor: unit, integration, e2e `--full --ux`, Sonar), then close the task.

## Out of scope, recorded elsewhere

The reviews a deleted user wrote on other profiles: an entry in [improvement-133](../../tasks/improvement-133-deferred-oversized-review-findings.md) (product decision first).

## Related

- [improvement-235](improvement-235-promotion-mechanism.md) — the promotion entry removal on delete follows the same rule.
- The cascade-in-the-same-transaction decision of the orchestrator (see `.claude/nav/adr-index.md`); `.claude/rules/feedback-spring-boot-starter.md`.

## Operational notes
- token_cost_review: 81104
- token_cost_research: n/a
- token_cost_verification: n/a
- review_signal_ratio: 6 / n/a
- context_loading_task_type: Cross-module feature
- context_loading_consulted: yes
- context_loading_matched: yes
- flows_situation: a finished change needs a review, a doc sync and a full CI pass before closing
- flows_chosen: /code-review, /sync-docs, scripts/ci.sh through the activity monitor
- flows_matched: yes
- haiku_decomposition_used: n/a
- haiku_decomposition_dispatch_count: n/a
- haiku_decomposition_rework_needed: n/a
- adrs_referenced: ADR-002 (marketplace-orchestrator), ADR-051 (marketplace-app)

### Agent calls
- port, repository and level 1 test for deleteByEntity | subagent_type=general-purpose | tokens=131020 | tool_uses=13 | duration_s=89 | mode=background | batch=solo
- orchestrator cascade call, unit and level 2 tests | subagent_type=general-purpose | tokens=131286 | tool_uses=21 | duration_s=127 | mode=background | batch=solo
- code review of the diff | subagent_type=code-review | tokens=81104 | tool_uses=2 | duration_s=51 | mode=background | batch=solo
- deleteByEntity as one CTE and the empty-entity test | subagent_type=general-purpose | tokens=108072 | tool_uses=8 | duration_s=64 | mode=background | batch=follow-up
- unit test stubs and Javadoc | subagent_type=general-purpose | tokens=89963 | tool_uses=7 | duration_s=47 | mode=background | batch=follow-up
- Playwright delete test extension | subagent_type=general-purpose | tokens=90914 | tool_uses=13 | duration_s=90 | mode=background | batch=follow-up

### Script/command runs
- scripts/build-and-test.sh --unit --integration --sandbox --skip-vaadin | duration_s=500 | mode=background | result=pass
- docs/architecture/scripts/generate-architecture-model.sh | duration_s=n/a | mode=background | result=pass
- scripts/ci.sh --foreground | duration_s=1210 | mode=background | result=fail
- scripts/ci.sh --e2e --foreground | duration_s=1290 | mode=background | result=pass

### Review angle yield
- code-review at its default level | survived=6 | total_candidates=n/a | tokens=81104
