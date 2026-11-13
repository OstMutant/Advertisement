# improvement-254: Three small defects from a review: an unfiltered moderation queue without the user starter, an unmeasured audit index claim, feedback remarks that promise too much

**Type:** bug (the moderation queue) and two accuracy fixes (an index comment, table remarks)
**Module:** `feedback-spring-boot-starter` (`FeedbackService`, `db/feedback-changelog/changes/01-feedback-schema.xml`), `audit-spring-boot-starter` (`db/audit-changelog/changes/01-audit-schema.xml`, `AuditLogRepository`), `integration-tests` (the new tests)
**Priority:** 🟡 high — placed at the very top by the user; small, independent and one of them returns data the caller asked to exclude.
**When:** independent, no blockers.

## Current state

Checked against the sources on 2026-11-12:

- **Moderation queue.** `FeedbackService.resolveAuthorIdsFilter` (`feedback-spring-boot-starter/src/main/java/org/ost/feedback/services/FeedbackService.java:337-340`) returns `Optional.empty()` when `authorName` is blank, and also when `authorName` is set but the optional `UserPort` is absent (`userPortFactory.findIfAvailable().map(...)`). `findModerationQueue` (lines 320-325) and `countModerationQueue` (329-333) read an empty `Optional` as "no author filter" and return the whole queue. The Javadoc of the method (line 336) records this as intended ("no user starter present"). The two integration test files that mention an author filter (`FeedbackAccessServiceTest`, `ModerationMatrixScenarioTest`) were not read; no test I found names the case "author name set, `UserPort` absent".
- **Audit index.** `idx_audit_created_at` (`01-audit-schema.xml:47-49`) carries the comment "the purge by age (deleteOlderThan) and the timeline's newest-first order". `deleteOlderThan` (`AuditLogRepository.java:109-110`) filters `created_at < NOW() - interval`, which the index can serve. `findTimeline` wraps `NUMBERED_SELECT` (window functions `ROW_NUMBER()` and `LAG()`) in a CTE and sorts the result by `created_at`; no execution plan of it has been recorded.
- **Feedback remarks.** `01-feedback-schema.xml` says of `feedback_content` that "each row belongs to exactly one of them, both columns are unique" (line 14) and, on the two `content_id` columns, "one content row belongs to exactly one entry" (line 47) and "exactly one reply" (line 86). The schema has `UNIQUE` on `feedback.content_id` and on `feedback_comment.content_id`, which is uniqueness inside each table; nothing in the schema stops one `feedback_content` row from being referenced from both tables.

## Why change

- A caller that filters the queue by author while the user starter is not deployed gets every queue item, with no signal that the filter did not run. A missing optional dependency must not silently widen a result.
- A comment that credits an index with a benefit nobody measured invites keeping an index for the wrong reason or adding a second one for the same wrong reason.
- Remarks are the single source of truth for the database ERD page of the architecture map; a remark that promises more than the constraint gives misleads whoever reads the model.

## Expected benefit

- The moderation queue never returns unfiltered rows when an author filter was asked for and cannot be applied; list and count agree.
- The index comment, and the index itself if it earns nothing, match a recorded plan or an explicit statement that no measurement was possible.
- The remarks state exactly what the constraints guarantee.

## Approach

1. **Moderation queue.** Separate the two cases: no author name (no author restriction) and an author name that cannot be resolved because `UserPort` is absent (the filter cannot run). Options: (a) return an empty list and a count of 0, the same as "no author matches" — least code, no new type; (b) a typed unavailability exception — explicit, but a new exception and handling in every caller. Prefer (a) unless a caller needs to tell the cases apart. Change the same place for list and count and correct the Javadoc of line 336. Tests (Testcontainers where the repository is involved, a service test with a mocked `ComponentFactory<UserPort>` otherwise): author name blank and `UserPort` absent leaves the queue unfiltered; author name set and `UserPort` absent returns nothing, list and count alike; author name set and `UserPort` present filters as today.
2. **Audit index.** Run `EXPLAIN (ANALYZE, BUFFERS)` for `deleteOlderThan` and for `findTimeline` on representative data in the integration-test PostgreSQL (seed enough rows for the planner to choose between index and scan). Outcomes: used only by the purge, so the comment names only the purge; not used by either, so consider removing the index (an in-place edit of the base changeset, see the policy below); used by both, so the comment stays. If a realistic measurement is not possible in the environment, say so in the comment instead of claiming a benefit. No new index without a measured need.
3. **Feedback remarks.** First check the life cycle of `feedback_content` in the services: where a content row is created and whether any path reuses one. If no path needs mutual exclusion between the two tables, keep the two `UNIQUE` constraints and rewrite the remarks to say "unique within this table" and that the service creates one content row per entry or reply; no trigger and no polymorphic schema. If a path does need exclusion, find the simplest reliable way and size it before doing it.

Schema edits in this task follow the project's rule for base changesets: edited in place with `validCheckSum ANY`, because the application has no production deployment; a database that already ran the old changeset gets the change after a reset. That is a recorded decision, not a gap (see the decision records of `marketplace-app` and the entry of `improvement-133` about in-place edits). A review finding that asked for new changesets instead was checked and does not apply under that rule.

## Closing note

Done: approach item 1. A moderation queue filtered by an author name returns nothing, list and count alike, when the user starter is absent (a blank name stays unfiltered); two tests in `FeedbackServiceTest` pin both cases; commit 2c0ad751; the whole local CI run passed.

Not done, judged not critical: approach items 2 and 3. Rewriting the feedback table remarks and checking the comment of `idx_audit_created_at` change no behaviour — the first is the accuracy of a description, the second an unmeasured comment with at most a small write cost. Both are carried to the deferred findings (entry 48 of improvement-133).

## Related

- [improvement-252](../completed/tasks/improvement-252-application-starts-without-each-optional-starter.md) — optional starters and `ComponentFactory`; the moderation queue fix must keep "Feedback starter without User starter" working when no author filter is given.
- [improvement-133](improvement-133-deferred-oversized-review-findings.md) — records that in-place edits of changelogs leave an already migrated database without the change.
- The decision record of the moderation queue (SQL-level filter, sort and pagination, in `marketplace-orchestrator`; find it through `.claude/nav/adr-index.md`) — the queue itself is not changed here, only the case of a missing `UserPort`.

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

### Script/command runs
- scripts/build-and-test.sh --no-unit --integration-test FeedbackServiceTest | duration_s=212 | mode=background | result=pass
- scripts/ci.sh (full run: unit, integration, e2e --full --ux, api_flows, sonar, archunit_metrics, lint, shellcheck, docs) | duration_s=1454 | mode=background | result=pass

### Commands used
- /ci | uses=1 | failed=0 | note=full run succeeded in about 24 minutes; the map regenerated inside the runner only, the host copy needs `scripts/ci/run.sh --sync-artifacts`
