# Issue Backlog — prioritized execution order

Index of all open issues in `issues/`, grouped by execution wave. Each issue file carries the
same assignment in its `**When:**` line — if they ever disagree, the issue file wins and this
index must be updated.

Waves: **Week 0** = cheap independent quick wins; **Wave 1** = prerequisites for public
shareability; **Wave 2** = quality hardening before public traffic; **Wave 3** = with the
corresponding domain work; **Deferred** = trigger-based, do not touch until the trigger fires.

---

## Week 0 — quick wins (~1 day total)

✅ Done (2026-07-04): toast position (improvement-012, commit 0f02b91d), header email overflow
(improvement-009, commit 0f02b91d), unused vaadin-core dependency (improvement-016, commit
0f02b91d), virtual threads (commit 0f02b91d), DelegatingPasswordEncoder (commit 0f02b91d),
owasp-sanitizer bump 20240325.1→20260313.1 (commit a9ed6d7e) — all verified with a full
reactor build, `deploy-dev.sh`, and a green 46/46 e2e run each time. Resolved issues moved to
`completed/issues/`.

Also fixed this session, not originally tracked as Week-0 items: a Quill false-dirty-state bug
and 3-layer description length validation (commit b7d64cc2 — closes
`completed/issues/issue-description-length-tag-spam.md`, unblocks improvement-006; see
`marketplace-app/DECISIONS.md` ADR-021 update and ADR-024).

**Still open:**

| Item | What | Cost | Note |
|---|---|---|---|
| buildx + cache mounts | Part 1/3 of process-improvements | ~1h | not started |

**Week 0 is otherwise complete.**

## Wave 1 — prerequisites for public shareability

✅ Done (2026-07-04): improvement-005 — plain-text card excerpt (Jsoup `.text()` instead of raw
`innerHTML`) + sanitizer allowlist merge (`<pre>` added, `mailto:`/extra tags kept as accepted
divergence). Moved to `completed/issues/`. Updated an outdated Playwright assertion
(`e2e/_flows/advertisement.flow.js`, card step) that expected rich HTML tags in the card —
full e2e 46/46 green.

**Still open:**

| Order | Issue | What | Note |
|---|---|---|---|
| 1 | [improvement-017](issues/improvement-017-sync-s3-upload-in-request-thread.md) (step 1) | Upload size caps | alongside thumbnail work; step 2 (async) deferred |
| 2 | [improvement-020](issues/improvement-020-security-baseline-before-public-endpoints.md) | Deny-by-default + rate limiting | **hard gate** — lands with the first public REST endpoints |
| 3 | [improvement-007](issues/improvement-007-taxon-findbyids-and-snapshot-captureandgetid.md) | Bulk taxon findByIds (kills list N+1) | bundle with the city/geo feature PR |
| 4 | [improvement-004](issues/improvement-004-pageLimit-and-taxon-softdelete-actor.md) | pageLimit dedup + softDelete actorId | same taxon-repo touch as #3, same PR |

## Wave 2 — quality hardening before public traffic

| Order | Issue | What | Note |
|---|---|---|---|
| 1 | [improvement-018](issues/improvement-018-settings-pagination-cross-session-bleed.md) | Cross-session settings bleed + UI-ref leak | **real multi-user bug**; needs 2-session Playwright test |
| 2 | [improvement-015](issues/improvement-015-optimistic-locking.md) | Optimistic locking (@Version on all entities) | silent last-write-wins otherwise |
| 3 | [improvement-013](issues/improvement-013-raw-field-names-in-activity-diff.md) | Localized field labels in Activity diffs | infrastructure (labelFor) already exists |

✅ Done (2026-07-04): tag-spam validator + 3-layer Jsoup-based length validation
(`issue-description-length-tag-spam` → moved to `completed/issues/`), alongside a fix for a
Quill false-dirty-state bug (edit form showed Save/Discard as active on open for rich-text
descriptions — not separately tracked as an issue, fixed directly; see
`marketplace-app/DECISIONS.md` ADR-021 update). Full e2e 46/46 green.

**Still open, no longer blocked:**

| Issue | What | Note |
|---|---|---|
| [improvement-006](issues/improvement-006-quill-description-counter-and-db-limit.md) | Quill char counter + DB VARCHAR(2000) migration | its blocker is resolved; can be picked up independently now |
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
