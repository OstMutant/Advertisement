# improvement-105: AdvertisementEnrichService — unify the mirrored Timeline/Activity enrichment branches

**Type:** improvement — simplification (parallel near-duplicate code paths). Found via
simplification review (2026-07-19).
**Module:** `marketplace-app` (`services/advertisement/AdvertisementEnrichService.java`)
**Priority:** low — no bug; two mirrored branches mean every enrichment change must be made (and
reviewed, and tested) twice
**When:** Batch N (audit-rendering simplification) — see `backlog/BACKLOG.md` "Execution
batches"; explicitly after Batch F (improvement-101 changes `idsToNames`, improvement-084
reshapes the diff layer — unify what survives, don't race them)

## Problem

The service maintains two structurally-mirrored method families for the same enrichment idea
(resolve category names, merge media changes), one per DTO type:

| Timeline (`AuditTimelineItemDto`) | Activity (`AuditActivityItemDto`) |
|---|---|
| `mergeMediaChanges()` | `enrichActivityItems()` |
| `collectTimelineCategoryIds()` | `collectActivityCategoryIds()` |
| `mergeTimelineItem()` | `mergeActivityItem()` |

The bodies differ only in (a) DTO accessors, (b) the Timeline variant's `entityType()` guard,
and (c) a slightly different media-merge condition (Activity compares `attachmentSnapshotId`
against the previous snapshot's; Timeline merges whenever media changes exist). Shared logic
(`resolveCategories`, `idsToNames`, `addCategoryIds`, `mediaChangesFor`) is already extracted —
the remaining duplication is the orchestration shell.

## Suggested fix

Introduce one generic worker parameterized over an accessor view of the item (snapshot, prev
snapshot, changes, withChanges) — a tiny internal record or lambda triple, not a new public
abstraction — and have both public entry points delegate to it. **Preserve the two deliberate
behavioral differences** (entity-type guard; media-merge condition) as explicit parameters so
they stay visible, and characterization-test both paths against current outputs before
refactoring (the Playwright activity/timeline diff assertions already pin much of this).

If the generic version comes out *less* readable than the two mirrors, close this as
considered-and-rejected with a note here — simplification that obscures loses.

## Related

- `backlog/issues/improvement-101-audit-diff-unresolved-category-ids.md` and
  [improvement-084](improvement-084-snapshot-dto-diff-field-boilerplate.md) — land first
  (Batch F).
- `backlog/issues/improvement-104-expandactivityfields-feature-envy.md` — same batch; both
  touch the audit-rendering DTO boundary.
