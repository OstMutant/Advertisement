# Issue Backlog — prioritized execution order

Index of all open issues in `issues/`, grouped by execution wave. Each issue file carries the
same assignment in its `**When:**` line — if they ever disagree, the issue file wins and this
index must be updated.

Waves: **Week 0** = cheap independent quick wins; **Wave 1** = prerequisites for public
shareability; **Wave 2** = quality hardening before public traffic; **Wave 3** = with the
corresponding domain work; **Deferred** = trigger-based, do not touch until the trigger fires.

---

## Week 0 — quick wins (~1 day total)

✅ Done (2026-07-04, commit 0f02b91d): toast position (improvement-012), header email overflow
(improvement-009), unused vaadin-core dependency (improvement-016), virtual threads,
DelegatingPasswordEncoder — all verified with a full reactor build, `deploy-dev.sh`, and a
green 46/46 e2e run. Moved to `completed/issues/`.

**Still open:**

| Item | What | Cost | Note |
|---|---|---|---|
| owasp-sanitizer bump | Part 1/3 of process-improvements | 1 pom line + full e2e | deliberately held back — investigate the edit-form dirty-state bug (Quill/HTML content triggers Save/Discard on open with no actual edit) before touching the sanitizer, in case they're related |
| buildx + cache mounts | Part 1/3 of process-improvements | ~1h | not started |

## Wave 1 — prerequisites for public shareability

| Order | Issue | What | Note |
|---|---|---|---|
| 1 | [improvement-005](issues/improvement-005-rich-text-excerpt-and-sanitizer-gaps.md) | Plain-text excerpt for cards | dual purpose: fixes card HTML leak AND feeds link-preview descriptions |
| 2 | [improvement-017](issues/improvement-017-sync-s3-upload-in-request-thread.md) (step 1) | Upload size caps | alongside thumbnail work; step 2 (async) deferred |
| 3 | [improvement-020](issues/improvement-020-security-baseline-before-public-endpoints.md) | Deny-by-default + rate limiting | **hard gate** — lands with the first public REST endpoints |
| 4 | [improvement-007](issues/improvement-007-taxon-findbyids-and-snapshot-captureandgetid.md) | Bulk taxon findByIds (kills list N+1) | bundle with the city/geo feature PR |
| 5 | [improvement-004](issues/improvement-004-pageLimit-and-taxon-softdelete-actor.md) | pageLimit dedup + softDelete actorId | same taxon-repo touch as #4, same PR |

## Wave 2 — quality hardening before public traffic

| Order | Issue | What | Note |
|---|---|---|---|
| 1 | [improvement-018](issues/improvement-018-settings-pagination-cross-session-bleed.md) | Cross-session settings bleed + UI-ref leak | **real multi-user bug**; needs 2-session Playwright test |
| 2 | [improvement-015](issues/improvement-015-optimistic-locking.md) | Optimistic locking (@Version on all entities) | silent last-write-wins otherwise |
| 3 | [improvement-013](issues/improvement-013-raw-field-names-in-activity-diff.md) | Localized field labels in Activity diffs | infrastructure (labelFor) already exists |
| 4 | [issue-description-length-tag-spam](issues/issue-description-length-tag-spam.md) | Tag-spam validator + raw-size cap | formatting tags survive the sanitizer |
| 5 | [improvement-006](issues/improvement-006-quill-description-counter-and-db-limit.md) | Quill counter + DB limit | strictly after #4 |
| 6 | [improvement-011](issues/improvement-011-unguarded-port-injection-in-ui-components.md) | Port-injection guards decision (Option A/C) | must precede creation of any new starter |

Plus from `process-improvements.md`: ArchUnit module + minimal CI (before the codebase grows).

## Wave 3 — with the corresponding domain work

| Issue | What | Pairs with |
|---|---|---|
| [improvement-002](issues/improvement-002-snapshot-schema-versioning.md) | Snapshot schema versioning | before the first new snapshot-bearing domain |
| [improvement-019](issues/improvement-019-findtimeline-correlated-subqueries.md) | findTimeline window-function rewrite | any audit-starter touch |

Plus: Testcontainers test layer is a hard gate before any payment code.

## Deferred — trigger-based (do not touch until the trigger fires)

| Issue | Trigger |
|---|---|
| [improvement-003](issues/improvement-003-deferred-performance.md) (items A-K) | per-item triggers inside the file |
| [improvement-021](issues/improvement-021-attachment-concurrency-and-batching.md) | concurrent gallery editing in practice; item A joins any attachment schema touch |
| [improvement-017](issues/improvement-017-sync-s3-upload-in-request-thread.md) (step 2) | bundled with the thumbnail-pipeline refactor |
| [improvement-008](issues/improvement-008-deleted-category-strikethrough.md), [improvement-010](issues/improvement-010-advertisements-view-refresh-error-notification.md), [improvement-014](issues/improvement-014-uuid-filenames-in-media-diff.md) | cosmetic — batch into any nearby UI-touching PR |
| [goal-001](issues/goal-001-activity-field-visibility-by-role.md) | user feedback |

---

## Maintenance rules

- New issue → add its `**When:**` line AND a row here, in the same change.
- Issue resolved → move the file to `completed/issues/` (per rules.md) AND remove its row
  here, in the same change.
