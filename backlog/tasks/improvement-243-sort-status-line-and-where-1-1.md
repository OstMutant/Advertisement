# improvement-243: The sort line of the list pages shows the wrong order and no test checks it; `WHERE 1=1` in two repositories

**Type:** improvement — the page shows data that does not match the real sorting, and nothing catches it
**Module:** `marketplace-app` (`ui/query/sort/SortProcessor`, `ui/query/QueryStatusBar`, the list views), `provider-profile-spring-boot-starter`, `audit-spring-boot-starter`
**Priority:** 🟡 high, at the user's request
**When:** independent, no blockers

## Current state

Checked on 2026-11-08 in the code:

- The line "Sort: ..." under the query block of a list page is built by `SortProcessor.loopSortDescriptions`, which walks `fieldsMap`, that is the sort fields in the order they are registered in the query block, and prints those that have a direction. The real order of the sorting is the `Sort` given to the `SortProcessor` in each `*QueryConfig`.
- The two orders differ in three lists. Advertisements: sorting `updatedAt`, `createdAt`; the line shows Created, Updated. Users: the same. Provider profiles: sorting `promoted`, `updatedAt`, `createdAt`; the line shows Promotion, Created, Updated. Moderation and timeline sort by `createdAt` only, no difference.
- In the SQL of the provider profile list the order is: the promoted group, `pp.updated_at DESC`, `pp.id DESC`, `pp.created_at DESC`; `pp.id` is a tiebreaker after `updated_at` (`SORT_FIELDS` in `ProviderProfileRepository`) and is unique, so the `created_at` after it never changes the order. The SQL of the advertisement and user lists was not checked.
- No test checks the text of this line: a search of all `.java` and `.js` files for `loopSortDescriptions`, `QueryStatusBar` and `query-status-bar-sort-info` finds only production code; `playwright/e2e` has no check of `sort-info` or `Sort: `.
- `WHERE 1=1` is used in two repositories only, four lines in total: `AuditLogRepository` (lines 142 and 151, there since the first audit timeline commit) and `ProviderProfileRepository` (lines 101 and 108, there since the module was created). No decision record describes it. The other repositories (`AdvertisementRepository`, `UserRepository`, `FeedbackRepository`) start from a real condition (`deleted_at IS NULL`, the moderation status) and append ` AND ...`; `provider_profile` and `audit_log` have no such condition. `SqlFilterBuilder.build(params, filter, prefix)` takes the prefix as a parameter.
- `AuditLogRepository.java:110` runs `DELETE FROM audit_log WHERE created_at < NOW() - MAKE_INTERVAL(days => :days ...`; `audit_log` has only the indexes `idx_audit_entity` and `idx_audit_changed_by`, and none of them has `created_at` as the leading column. Whether to add such an index is not decided.

## Why change

The page shows the user an order of sorting that is not the real one, and no test would notice a wrong line. `WHERE 1=1` is a placeholder condition that no decision describes and that the other repositories do not need.

## Expected benefit

The line under the query block names the sort fields in the order the list is really sorted; a test fails when it does not. The two repositories build `WHERE` only from real conditions.

## Approach

1. The line under the query block follows the order of the `Sort` of the `SortProcessor`, not the order of registration of the fields (`SortProcessor.loopSortDescriptions`), in every list that uses it.
2. A test for the text of the line for each list with its default sorting: advertisements, users, provider profiles, moderation, timeline.
3. `WHERE 1=1` step: remove `WHERE 1=1` from `AuditLogRepository` and `ProviderProfileRepository`; `WHERE` is written only when at least one condition exists.
4. `WHERE 1=1` step, decision: update ADR-003 (marketplace-app), "Inline SQL repository style — no descriptor layer", in `marketplace-app/DECISIONS.md` with the rule "no `WHERE 1=1`; the prefix of `SqlFilterBuilder.build` carries `WHERE` or `AND`", through `/record-decision`, and regenerate `.claude/nav/adr-index.md`.

5. Index step (decision, not a mechanical change): decide whether `audit_log` gets an index with `created_at` as the leading column for the purge by age; if yes, add it to the changelog of the audit starter, in place, with a `remarks`.

## Related

- [improvement-241](../completed/tasks/improvement-241-database-refactoring.md): the database refactoring in which `WHERE 1=1` was found.
- ADR-003 (marketplace-app).

## Operational notes
- token_cost_review: n/a
- token_cost_research: n/a
- token_cost_verification: n/a
- review_signal_ratio: n/a
- context_loading_task_type: n/a
- context_loading_consulted: n/a
- context_loading_matched: n/a
- flows_situation: new improvement request, not yet tracked
- flows_chosen: /new-task
- flows_matched: yes
- haiku_decomposition_used: n/a
- haiku_decomposition_dispatch_count: n/a
- haiku_decomposition_rework_needed: n/a
- adrs_referenced: none
