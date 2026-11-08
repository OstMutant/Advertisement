# Notes: optimize-table-references

Purpose: findings, the comparison of variants and the differences from the project's own practice.

## Findings

- OPT-candidate 1 (implement, medium): delete test-only AttachmentRepository.loadMediaStats(EntityType,Long) + MEDIA_STATS_ROW_MAPPER, bulk overload covers it; est -55 +6; risk: no-attachments case differs (single returns MediaStats(null,null,0), bulk omits key)
- 2 (implement, medium): FeedbackService.save lines 83-132 drop contentId/effectiveEntityType/effectiveEntityId and throwaway Feedback.builder(); est -14 +6
- 3 (implement, medium): ApiKeyRepositoryTest repeated other-user/ApiKey creation, two local helpers; est -16 +8
- 4 (implement, high): FeedbackServiceTest.concurrentSaves_sameEntity... reuse runConcurrently; est -24 +5
- 5 (implement, high): move bearer/etagOf/historyEntries/snapshotIdOf (18/16/8/5 private copies) into AbstractRestApiScenarioTest; est -280 +30, 26 files; run full Level 3
- 6 (implement, medium): three identical stub UiLabelHook implementations to one shared class; est -58 +22
- 7 (investigate, medium): buildChipRow x3 in marketplace-app handlers into ChipRowUtil; only advertisement copy adds --deleted class; Playwright selectors; est -55 +25
- 8 (investigate, medium): taxon_assignment.assigned_by always NULL (DefaultTaxonPort passes null actor); drop parameter/field/column via new changeset; est -24 +8; overlaps improvement-241 marker defect, coordinate

## Variants and comparison

- assigned_by: (a) drop column, field, parameter and test assertion; (b) fill it from the actor, needs a TaxonPort contract change or a CurrentActorHook lookup inside the starter, a behavior change the brief forbids; (c) keep as is and only add the marker in improvement-241. (a) is the only reduction; in-place edit of 001-taxon.xml (it carries validCheckSum ANY, the pre-launch pattern of ADR-070) vs a new 002 changeset with dropColumn (no auto rollback per Liquibase docs) is a decision.
- FeedbackService.save: keep the two owner locals (least change) vs switch them to one EntityRef local; chosen: keep the pair, drop the contentId local and the throwaway Feedback.builder() in the edit branch, carry a Long feedbackId instead of a Feedback.

## Differences from the project

- Brief points at backlog/tasks/improvement-225-code-reduction-findings.md but the file is in backlog/completed/tasks/; no overlap with candidates 1, 2, 8 found there or in improvement-241 beyond the assigned_by marker
- Candidates 3-7 of the earlier pass (ApiKeyRepositoryTest helpers, FeedbackServiceTest concurrency, REST scenario helpers, UiLabelHook stubs, buildChipRow) are not about tables or cross-module references; kept out of the plan steps so the goal is not widened, listed for the person to route to the findings inventory
- CONFLICT: best practice (Fowler data clump, Preserve Whole Object) says pass EntityRef wherever entityType+entityId travel together; the brief forbids changing Port contracts, and AttachmentPort/FeedbackPort/TaxonPort/ContactPort take the pair. Inside the attachment starter alone, switching AttachmentService/AttachmentRepository to EntityRef would add wrapping in DefaultAttachmentPort and save nothing. Left to the person as a separate contract-change decision.
- CONFLICT: candidate 1 (delete single-entity AttachmentRepository.loadMediaStats) contradicts ADR-035 (marketplace-app) and .claude/rules/attachment-spring-boot-starter.md, which keep it because it has its own repository tests; no production caller exists (git grep). Plan step is conditional on the person's choice and needs an ADR annotation via /record-decision.
- CONFLICT: user_preferences.actor_id is in the same module as user_information but has no FK on purpose (ADR-070, actor-reference convention), so UserService.purge must call UserPreferencesRepository.deleteByActorId; PostgreSQL docs recommend ON DELETE CASCADE for a component table. An in-module FK is allowed by the brief (no cross-module FK). Left as a decision; not in the plan by default.
- Feedback tables have in-module FKs without ON DELETE CASCADE; deleteFeedbackHard/deleteByEntity delete each table explicitly (rule file: no FK cascade). CASCADE on feedback_rating/feedback_comment to feedback would drop two of four deletes but content rows still need explicit deletes and the project prefers explicit over implicit; rejected for low net benefit, listed for the person.

## Rejected ideas

- EntityRef instead of (EntityType,Long) in Ports/DTOs: public contract change, orchestrator callers, Set-of-ids signatures, little saving
- Remove actor-reference cleanup (clearActorReferences, deleteAllForActor, deleteByActorId): exists because no cross-module FK; belongs to improvement-241
- Remove PortImpl pass-throughs, collapse REST history endpoints, merge small test helpers, drop duplicated No-FK remarks changeset: hard limits or below significance
- Switching AttachmentService/AttachmentRepository internals to EntityRef while AttachmentPort keeps the pair: DefaultAttachmentPort would have to wrap every call, more code not less; bulk methods take Set<Long> ids that EntityRef cannot carry.
- Off-topic candidates 3-7 from the earlier pass (test helpers, UiLabelHook stubs, buildChipRow): real but not about tables or references; not planned here, route to the findings inventory if wanted.

## Inspected

- 152 files in scope, 151 read in full; I18nKey.java read to line 80 only; exclusion of marketplace-orchestrator matched no file; 8 findings of 9 candidates (1 dropped by verification: feedback moderation helper)
- This run did not re-read all 152 files; it relied on the earlier pass (151 read in full, I18nKey.java to line 80) and re-verified against code every fact the plans use: FeedbackService.save, AttachmentRepository.loadMediaStats and its tests, TaxonAssignment/Repository/Service/DefaultTaxonPort and test, user_preferences schema and UserService.purge, FeedbackRepository hard deletes, ADR-035, ADR-070, the attachment and feedback rule files. Stopped because the last two sources did not change the choice and every remaining idea hits a brief limit or belongs to improvement-241.
